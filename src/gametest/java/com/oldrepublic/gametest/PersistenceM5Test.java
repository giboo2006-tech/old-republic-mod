package com.oldrepublic.gametest;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.raid.RaidData;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.equipment.mod.LegendaryMods;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.gametest.visuals.Visuals;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveRules;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.quest.turning.TurningRules;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.CrystalTuning;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.SaberTunings;
import com.oldrepublic.travel.FlightBalance;
import com.oldrepublic.travel.FlightRules;
import com.oldrepublic.travel.Interceptor;
import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 persistence client test (spec MILESTONE_5.md 19.2, lead). Writes the new saved state in a fresh world,
 * closes it, reopens the same save and asserts every part came back:
 * <ul>
 * <li>the story attachment: Chapter Five at VERSES with two stones silenced (the first and the third), the kajidic's way
 * finished first, Dask released and one blockade failure; and the Fall at its task;</li>
 * <li>the travel attachment's helm;</li>
 * <li>the raid attachment: a rewarded clear with the schematic pity at 1;</li>
 * <li>in the inventory, a long-bladed saber around a Keen crystal, and a Jedi hood with a Legendary hymnweave
 * lining;</li>
 * <li>the ship: an interceptor with every Milestone 5 upgrade, landed in the field ahead of the player.</li>
 * </ul>
 * Then the ship is put in the air in {@code FLYING} with nobody at the helm, the world closes, and on the third opening
 * the ship stands on its last landing spot again (4.10), still an interceptor with every upgrade.
 *
 * <p>Screenshot {@code oldrepublic_m5_090_persistence_reopened}: the second opening. Shortcuts: the records are written
 * directly, as the chapter's and the raid's commands would; the refit is the shipwright's rule applied to the record,
 * and the ship's flight is its state set to {@code FLYING} with the body lifted, as a restart finds it.
 */
public class PersistenceM5Test implements FabricClientGameTest {
	/** Main-inventory slots, so neither stack is held or worn while it is checked. */
	private static final int SABER_SLOT = 9;
	private static final int HOOD_SLOT = 10;
	private static final long VERSES_SINCE = 4321L;
	private static final int FAILURES = 1;
	private static final long BAND_SINCE = 2L;
	private static final int REFUSALS = 1;
	private static final long CLEAR_WEEK = 3L;
	private static final int RAID_PITY = 1;
	private static final CrystalTuning TUNING = CrystalTuning.KEEN;
	/** Every Milestone 5 upgrade (4.8) and both legendary ship parts (11.2). */
	private static final int M5_UPGRADES = ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_SHIELD | ShipRecord.UPGRADE_THRUSTERS
			| ShipRecord.UPGRADE_MISSILES | ShipRecord.PART_SPINDLE_CAPACITOR | ShipRecord.PART_HYMN_DRIVE;
	/** Under the interceptor's most (80, 4.9), so the refit keeps it. */
	private static final int SHIP_HULL = 70;
	private static final int SHIP_FUEL = 3;
	/** How high the ship is lifted while it is saved in flight. */
	private static final double FLIGHT_HEIGHT = 6;
	/** Long enough for the restart recovery, which runs every 200 ticks. */
	private static final int SETTLE_TICKS = 300;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Visuals.only()) {
			return; // A visuals-only run: VisualsClientTest takes its close-up pictures alone.
		}

		TestSingleplayerContext created = context.worldBuilder().create();
		var save = created.getWorldSave();
		GlobalPos landing;

		try (created) {
			landing = write(context, created);
		}

		try (TestSingleplayerContext world = save.open()) {
			world.getConnection().waitForChunksRender();
			context.waitTicks(40);
			TestServerContext server = world.getServer();
			String problem = server.computeOnServer(s -> problem(s, landing));

			if (problem != null) {
				throw new AssertionError("After reopening the world: " + problem);
			}

			context.takeScreenshot("oldrepublic_m5_090_persistence_reopened");
			String flying = server.computeOnServer(PersistenceM5Test::takeOff);

			if (flying != null) {
				throw new AssertionError("Putting the ship in the air: " + flying);
			}
		}

		try (TestSingleplayerContext world = save.open()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			String problem = "not checked";

			for (int waited = 0; waited < SETTLE_TICKS && problem != null; waited += 10) {
				context.waitTicks(10);
				problem = server.computeOnServer(s -> shipProblem(s, player(s), landing));
			}

			if (problem != null) {
				throw new AssertionError("After reopening the world with the ship saved in flight: " + problem);
			}

			OldRepublicMod.LOGGER.info("Persistence M5 test: Chapter Five, the turning, the helm, the raid record, the saber, the legendary mod "
					+ "and the interceptor came back after a reopen, and the ship saved in flight stood on its last landing spot");
		}
	}

	/** Writes every part into {@code world}; returns the ship's field landing spot. */
	private static GlobalPos write(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		world.getConnection().waitForChunksRender();
		String setup = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ItemStack saber = saber(p.getUUID());
			ItemStack hood = hood();
			String built = saberProblem(saber, p.getUUID());

			if (built == null) {
				built = hoodProblem(hood);
			}

			if (built != null) {
				return "the gear could not be built: " + built;
			}

			StoryData story = Story.get(p);
			// Chapter Four done, as it is for anyone in Chapter Five.
			story.chapter4 = story.chapter4.withStage(ChapterFourStage.COMPLETE, -1L);
			story.chapter5 = expectedChapter5();
			story.turning = expectedTurning();
			Story.commit(p, story);
			TravelAttachments.set(p, TravelAttachments.get(p).withHelm(true));
			RaidData.set(p, expectedRaid());
			p.getInventory().setItem(SABER_SLOT, saber);
			p.getInventory().setItem(HOOD_SLOT, hood);
			// The ship lands in the field ahead of the player, then takes the shipwright's refit and every upgrade.
			Ships.give(p);
			return Ships.update(s, p.getUUID(), r -> FlightRules.refitted(r.withUpgrades(r.upgrades() | M5_UPGRADES).withHull(SHIP_HULL)
					.withFuel(SHIP_FUEL), true, FlightBalance.get(), ShipBalance.get())).isPresent() ? null : "no ship was given";
		});

		if (setup != null) {
			throw new AssertionError("Writing the M5 state: " + setup);
		}

		// The body becomes an interceptor on its next tick (Ships.tickBody).
		context.waitTicks(20);
		GlobalPos landing = server.computeOnServer(s -> ShipRegistry.of(s, player(s).getUUID()).map(ShipRecord::pos).orElse(null));
		String ship = landing == null ? "the ship's record is gone" : server.computeOnServer(s -> shipProblem(s, player(s), landing));

		if (ship != null) {
			throw new AssertionError("Writing the M5 state: the ship: " + ship);
		}

		return landing;
	}

	/** Null when everything {@link #write} wrote is back. */
	private static @Nullable String problem(MinecraftServer server, GlobalPos landing) {
		ServerPlayer p = player(server);
		StoryData story = Story.get(p);
		StoryData.Chapter5 c5 = story.chapter5;

		if (!c5.equals(expectedChapter5())) {
			return "Chapter Five is " + c5 + ", not " + expectedChapter5();
		}

		if (ChapterFiveRules.versesSilenced(c5.verses()) != 2 || ChapterFive.stage(p) != ChapterFiveStage.VERSES) {
			return "Chapter Five reads " + ChapterFive.stage(p) + " with " + ChapterFiveRules.versesSilenced(c5.verses()) + " stones silenced";
		}

		if (!story.turning.equals(expectedTurning())) {
			return "the turning is " + story.turning + ", not " + expectedTurning();
		}

		if (!TravelAttachments.get(p).helm()) {
			return "the helm is off";
		}

		RaidData raid = RaidData.of(p);

		if (!raid.equals(expectedRaid())) {
			return "the raid record is " + raid + ", not " + expectedRaid();
		}

		String saber = saberProblem(p.getInventory().getItem(SABER_SLOT), p.getUUID());

		if (saber != null) {
			return "the saber: " + saber;
		}

		String hood = hoodProblem(p.getInventory().getItem(HOOD_SLOT));

		if (hood != null) {
			return "the hood: " + hood;
		}

		String ship = shipProblem(server, p, landing);
		return ship == null ? null : "the ship: " + ship;
	}

	/** Puts the ship in the air in {@code FLYING} with nobody at the helm, as a restart in flight finds it. */
	private static @Nullable String takeOff(MinecraftServer server) {
		UUID owner = player(server).getUUID();
		ShipBody body = Ships.ship(server, owner);

		if (body == null) {
			return "its body is not loaded";
		}

		Ships.update(server, owner, r -> r.withState(ShipState.FLYING));
		body.snapTo(body.getX(), body.getY() + FLIGHT_HEIGHT, body.getZ());
		ShipRecord record = ShipRegistry.of(server, owner).orElse(null);
		return record != null && record.state() == ShipState.FLYING ? null : "its record is " + record + ", not FLYING";
	}

	/** Null when {@code p}'s ship is an interceptor with every M5 upgrade, settled on {@code landing}. */
	private static @Nullable String shipProblem(MinecraftServer server, ServerPlayer p, GlobalPos landing) {
		ShipRecord record = ShipRegistry.of(server, p.getUUID()).orElse(null);

		if (record == null) {
			return "there is no ship record";
		}

		if (record.state().flying() || record.flight().isPresent()) {
			return "it is " + record.state() + (record.flight().isPresent() ? " on a flight" : "") + ", not settled";
		}

		if (!record.pos().equals(landing)) {
			return "its record is at " + record.pos() + ", not its landing spot " + landing;
		}

		if (!Ships.interceptor(record) || (record.upgrades() & M5_UPGRADES) != M5_UPGRADES) {
			return "its upgrades are " + Integer.toBinaryString(record.upgrades()) + ", not an interceptor's with every M5 upgrade";
		}

		if (record.hull() != SHIP_HULL || record.fuel() != SHIP_FUEL) {
			return "hull " + record.hull() + " and fuel " + record.fuel() + ", not " + SHIP_HULL + " and " + SHIP_FUEL;
		}

		ShipBody body = Ships.ship(server, p.getUUID());

		if (!(body instanceof Interceptor)) {
			return "its body is " + body + ", not an interceptor";
		}

		Vec3 spot = Vec3.atBottomCenterOf(landing.pos());

		if (!body.level().dimension().equals(landing.dimension()) || body.position().distanceTo(spot) > 0.5) {
			return "its body is at " + body.position() + " in " + body.level().dimension() + ", not on its landing spot " + spot;
		}

		return null;
	}

	/** VERSES with the first and third stones silenced, the kajidic's way first, Dask released, one blockade failure. */
	private static StoryData.Chapter5 expectedChapter5() {
		return StoryData.Chapter5.NEW.withStage(ChapterFiveStage.VERSES, VERSES_SINCE).withWay(ChapterFive.Way.KAJIDIC)
				.withDask(StoryData.Chapter5.DASK_RELEASE).withBlockadeFailures(FAILURES)
				.withVerses(ChapterFiveRules.verseBit(1) | ChapterFiveRules.verseBit(3));
	}

	/** The Fall at its task, the holocron still with Ansa, after one refused offer. */
	private static StoryData.Turning expectedTurning() {
		return StoryData.Turning.NEW.withStage(TurningKind.FALL, TurningStage.TASK).withTask(TurningRules.FALL_TO_ANSA)
				.withOffer(BAND_SINCE, REFUSALS, -1L);
	}

	/** One rewarded clear, the schematic pity at 1. */
	private static RaidData expectedRaid() {
		return RaidData.EMPTY.withClear(CLEAR_WEEK, RAID_PITY);
	}

	/** A retracted Guardian saber with a long blade, around the player's Keen crystal. */
	private static ItemStack saber(UUID owner) {
		CrystalAttunement crystal = new CrystalAttunement(owner, "Tester", BladeColor.BLUE.rgb(), TUNING.id());
		ItemStack stack = SaberItems.buildSaber(crystal, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
		stack.set(SaberComponents.SABER_CONFIG, SaberComponents.config(stack).withLength(SaberConfig.LENGTH_LONG));
		SaberToggle.refreshModifiers(stack, false);
		return stack;
	}

	private static @Nullable String saberProblem(ItemStack stack, UUID owner) {
		if (stack.isEmpty() || !stack.has(SaberComponents.SABER_CONFIG)) {
			return stack + " is not a built saber";
		}

		SaberConfig config = SaberComponents.config(stack);

		if (config.length() != SaberConfig.LENGTH_LONG) {
			return "blade length " + config.length() + ", not long";
		}

		if (!config.crystal().owner().equals(owner) || !SaberTunings.carries(stack, TUNING)) {
			return "the crystal is " + config.crystal() + ", not the player's " + TUNING.id() + " crystal";
		}

		return null;
	}

	/** A Jedi robe hood with a Legendary hymnweave lining in its armour slot. */
	private static ItemStack hood() {
		ItemStack lining = Crafting.withQuality(new ItemStack(LegendaryMods.HYMNWEAVE_LINING), Quality.LEGENDARY);
		return Mods.fit(ArmourSets.piece(ArmourSet.JEDI_ROBES, EquipmentSlot.HEAD), ModSlot.ARMOUR, lining);
	}

	private static @Nullable String hoodProblem(ItemStack stack) {
		ItemStack expected = ArmourSets.piece(ArmourSet.JEDI_ROBES, EquipmentSlot.HEAD);

		if (!stack.is(expected.getItem())) {
			return stack + " is not the Jedi robes' hood";
		}

		ItemStack lining = Mods.fitted(stack).get(ModSlot.ARMOUR);

		if (!lining.is(LegendaryMods.HYMNWEAVE_LINING)) {
			return "its armour mod is " + lining + ", not a hymnweave lining";
		}

		Quality quality = Crafting.quality(lining);
		return quality == Quality.LEGENDARY ? null : "the lining's quality is " + quality + ", not Legendary";
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
