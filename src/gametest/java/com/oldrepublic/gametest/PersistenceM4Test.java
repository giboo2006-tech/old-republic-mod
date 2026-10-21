package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingData;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.equipment.mod.FittedMods;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.gametest.visuals.Visuals;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelData;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Milestone 4 persistence client test (spec MILESTONE_4.md 17.2, lead). Writes the new saved state
 * in a fresh world, closes it, reopens the same save and asserts every part came back:
 * <ul>
 * <li>the ship registry record: hull, fuel, upgrades (a paint bit added), state, and a hold with a
 * modded gear stack, an empty slot and a stack of ingots; the ship body is back in the world;</li>
 * <li>the travel attachment: an unlocked destination, the last pad and the recall and tow days;</li>
 * <li>the crafting attachment: a discipline at level 3 with XP and a Masterwork pity of 7;</li>
 * <li>the story attachment: Chapter Four at the DUEL stage with Kaast yielded and the choice not yet
 * made, the hunting camp on Varkhast (far from the player, so the fight never starts);</li>
 * <li>a blaster rifle of Fine quality with a focused barrel and a Masterwork cooling cell, in the
 * player's inventory and in the hold.</li>
 * </ul>
 *
 * <p>Screenshot {@code oldrepublic_m4_76_persistence_reopened}: the reopened world with the ship.
 * Shortcut: the story stage is written directly, as {@code /oldrepublic c4 stage} would.
 */
public class PersistenceM4Test implements FabricClientGameTest {
	/** A main-inventory slot, so the stack is not held while it is checked. */
	private static final int GEAR_SLOT = 9;
	private static final int HULL = 73;
	private static final int FUEL = 5;
	/** The first paint bit of the upgrades mask (spec 12.1: paint bits 16 and 17). */
	private static final int PAINT_BIT = 1 << 16;
	private static final int RECALL_DAY = 3;
	private static final int TOW_DAY = 4;
	private static final int LEVEL = 3;
	private static final int XP = 500;
	private static final int PITY = 7;
	private static final GlobalPos LAST_PAD = GlobalPos.of(Level.OVERWORLD, new BlockPos(24, -60, 24));
	private static final long DUEL_SINCE = 1234L;
	/** {@code StoryData.Chapter4.Duel#boss}: 1 is yielded. */
	private static final int BOSS_YIELDED = 1;
	private static final GlobalPos CAMP = GlobalPos.of(Planets.Planet.VARKHAST.level(), new BlockPos(640, 90, -320));

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Visuals.only()) {
			return; // A visuals-only run: VisualsClientTest takes its close-up pictures alone.
		}

		TestSingleplayerContext created = context.worldBuilder().create();
		var save = created.getWorldSave();
		ShipRecord expected;

		try (created) {
			expected = write(context, created);
		}

		try (TestSingleplayerContext world = save.open()) {
			world.getConnection().waitForChunksRender();
			context.waitTicks(40);
			TestServerContext server = world.getServer();
			String problem = server.computeOnServer(s -> problem(s, expected));

			if (problem != null) {
				throw new AssertionError("After reopening the world: " + problem);
			}

			context.takeScreenshot("oldrepublic_m4_76_persistence_reopened");
			OldRepublicMod.LOGGER.info("Persistence M4 test: ship, travel, crafting, Chapter Four and gear came back after a reopen");
		}
	}

	/** Writes every part into {@code world}; returns the ship record as written. */
	private static ShipRecord write(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		world.getConnection().waitForChunksRender();
		server.runCommand("oldrepublic ship give @p");
		context.waitTicks(20);

		String setup = server.computeOnServer(s -> {
			ServerPlayer p = player(s);

			if (ShipRegistry.of(s, p.getUUID()).isEmpty()) {
				return "/oldrepublic ship give made no ship record";
			}

			ItemStack gear = gear();
			String built = gearProblem(gear);

			if (built != null) {
				return "the modded rifle could not be built: " + built;
			}

			ShipBalance b = ShipBalance.get();
			Ships.update(s, p.getUUID(), r -> {
				ShipRecord changed = r.withHull(HULL).withFuel(FUEL).withUpgrades(r.upgrades() | PAINT_BIT).withHold(hold());
				return changed.withState(ShipRules.restingState(changed, b));
			});
			TravelAttachments.set(p, TravelAttachments.get(p).withUnlocked(Destination.GREENWATCH, true).withLastPad(Optional.of(LAST_PAD))
					.withRecallDay(RECALL_DAY).withTowDay(TOW_DAY));
			CraftingAttachments.set(p, CraftingAttachments.get(p).withProgress(new CraftingData.Progress(Discipline.ARMSTECH, LEVEL, XP, PITY)));
			p.getInventory().setItem(GEAR_SLOT, gear);
			StoryData story = Story.get(p);
			story.chapter4 = story.chapter4.withStage(ChapterFourStage.DUEL, DUEL_SINCE)
					.withDuel(StoryData.Chapter4.Duel.NONE.withCamp(CAMP).withBoss(BOSS_YIELDED, -1));
			Story.commit(p, story);
			return null;
		});

		if (setup != null) {
			throw new AssertionError("Writing the M4 state: " + setup);
		}

		context.waitTicks(20);
		return server.computeOnServer(s -> ShipRegistry.of(s, player(s).getUUID()).orElseThrow());
	}

	/** Null when everything {@link #write} wrote is back; {@code expected} is the ship record as written. */
	private static @Nullable String problem(MinecraftServer server, ShipRecord expected) {
		ServerPlayer p = player(server);
		ShipRecord r = ShipRegistry.of(server, p.getUUID()).orElse(null);

		if (r == null) {
			return "the ship registry has no record for the player";
		}

		if (r.hull() != HULL || r.fuel() != FUEL || r.upgrades() != expected.upgrades() || (r.upgrades() & PAINT_BIT) == 0 || r.state() != expected.state()
				|| !r.hullCode().equals(expected.hullCode()) || !r.pos().equals(expected.pos())) {
			return "the ship record is " + describe(r) + ", written as " + describe(expected);
		}

		String hold = holdProblem(r.hold());

		if (hold != null) {
			return "the hold: " + hold;
		}

		if (Ships.shuttle(server, p.getUUID()) == null) {
			return "the ship body is not in the world";
		}

		TravelData travel = TravelAttachments.get(p);

		if (!travel.isUnlocked(Destination.GREENWATCH) || !travel.lastPad().equals(Optional.of(LAST_PAD)) || travel.recallDay() != RECALL_DAY
				|| travel.towDay() != TOW_DAY) {
			return "the travel data is " + travel;
		}

		CraftingData.Progress armstech = CraftingAttachments.get(p).progress(Discipline.ARMSTECH).orElse(null);

		if (armstech == null || armstech.level() != LEVEL || armstech.xp() != XP || armstech.pity() != PITY) {
			return "the Armstech progress is " + armstech + ", not level " + LEVEL + ", " + XP + " XP, pity " + PITY;
		}

		StoryData.Chapter4 c4 = Story.get(p).chapter4;
		StoryData.Chapter4.Duel duel = c4.duel();

		if (c4.stage() != ChapterFourStage.DUEL || c4.waitSince() != DUEL_SINCE || duel.boss() != BOSS_YIELDED || duel.outcome() != -1
				|| !CAMP.equals(duel.camp())) {
			return "Chapter Four is " + c4.stage() + " since " + c4.waitSince() + " with " + duel + ", not DUEL since " + DUEL_SINCE
					+ " with Kaast yielded at " + CAMP;
		}

		String gear = gearProblem(p.getInventory().getItem(GEAR_SLOT));
		return gear == null ? null : "the inventory rifle: " + gear;
	}

	/** A Fine blaster rifle with a focused barrel and a Masterwork cooling cell. */
	private static ItemStack gear() {
		ItemStack rifle = new ItemStack(EnemyItems.BLASTER_RIFLE);
		rifle.set(M4Components.QUALITY, Quality.FINE.ordinal());
		Mods.fit(rifle, ModSlot.BARREL, new ItemStack(M4Items.MOD_FOCUSED_BARREL));
		ItemStack cell = new ItemStack(M4Items.MOD_COOLING_CELL);
		cell.set(M4Components.QUALITY, Quality.MASTERWORK.ordinal());
		Mods.fit(rifle, ModSlot.CELL, cell);
		return rifle;
	}

	/** The rifle, an empty slot that keeps its place, and 12 iron ingots. */
	private static List<ItemStack> hold() {
		List<ItemStack> hold = new ArrayList<>();
		hold.add(gear());
		hold.add(ItemStack.EMPTY);
		hold.add(new ItemStack(Items.IRON_INGOT, 12));
		return hold;
	}

	private static @Nullable String holdProblem(List<ItemStack> hold) {
		if (hold.size() < 3) {
			return hold.size() + " slots, not 3";
		}

		String rifle = gearProblem(hold.get(0));

		if (rifle != null) {
			return "slot 0: " + rifle;
		}

		if (!hold.get(1).isEmpty()) {
			return "slot 1 holds " + hold.get(1) + ", not nothing";
		}

		if (!hold.get(2).is(Items.IRON_INGOT) || hold.get(2).getCount() != 12) {
			return "slot 2 holds " + hold.get(2) + ", not 12 iron ingots";
		}

		return null;
	}

	/** Null when {@code stack} is the Fine rifle with both mods, the cell Masterwork. */
	private static @Nullable String gearProblem(ItemStack stack) {
		if (!stack.is(EnemyItems.BLASTER_RIFLE)) {
			return stack + " is not a blaster rifle";
		}

		Integer quality = stack.get(M4Components.QUALITY);

		if (quality == null || quality != Quality.FINE.ordinal()) {
			return "quality " + quality + ", not Fine";
		}

		FittedMods mods = Mods.fitted(stack);
		ItemStack barrel = mods.get(ModSlot.BARREL);
		ItemStack cell = mods.get(ModSlot.CELL);

		if (!barrel.is(M4Items.MOD_FOCUSED_BARREL) || !cell.is(M4Items.MOD_COOLING_CELL)) {
			return "mods " + mods.mods() + ", not a focused barrel and a cooling cell";
		}

		Integer cellQuality = cell.get(M4Components.QUALITY);
		return cellQuality != null && cellQuality == Quality.MASTERWORK.ordinal() ? null : "the cell's quality is " + cellQuality + ", not Masterwork";
	}

	private static String describe(ShipRecord r) {
		return r.state() + " hull " + r.hull() + " fuel " + r.fuel() + " upgrades " + Integer.toHexString(r.upgrades()) + " code " + r.hullCode() + " at "
				+ r.pos();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
