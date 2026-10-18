package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.holocron.ArchiveChoice;
import com.oldrepublic.holocron.HolocronData;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.Path;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Chapter Four from the briefing to the first landing (spec MILESTONE_4.md 8.2, 8.3): the briefing
 * per path, the hulk and its retries, the four parts' XP, the motivator's Wreck Field route and its
 * daily re-issue, lift-off, and what a landing on a planet means (the banner, discovery XP, the
 * host's welcome, the end of FIRST_FLIGHT).
 */
public final class ChapterFourRepairs {
	/** The Engineering Bench (W5): one is given at the briefing, and its recipe is unlocked. */
	static final net.minecraft.resources.Identifier BENCH = OldRepublicMod.id("engineering_bench");
	/** An Independent's briefing plays within this many blocks of Courier's Fall, journal in hand. */
	private static final double FALL_REACH = 12;
	/** Within this many blocks of a planet's hub the hub's discovery XP is paid. */
	private static final double HUB_REACH = 48;
	/** Socket of the hyperdrive motivator (4.2). */
	static final int MOTIVATOR_SOCKET = 2;

	/** Failed hulk placements so far, per player (memory only: a restart simply tries again). */
	private static final Map<UUID, Integer> HULK_FAILURES = new HashMap<>();

	// ---------------------------------------------------------------- the briefing

	/**
	 * {@code c4:brief}: the mentor's briefing (an Independent's plays at Courier's Fall). Once, at
	 * SEEKER: 300 XP, the path's entry, the bench and its recipe, the coordinates of the path's world
	 * (both worlds for Independents), then REPAIRS places the hulk.
	 */
	static void brief(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.chapter4.stage() != ChapterFourStage.SEEKER || !ChapterFourService.once(p, ChapterFourRules.BRIEFED)) {
			return;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		Path path = data.storyPath();
		ChapterFourService.xp(p, b.briefXp);
		Journal.unlockEntry(p, ChapterFourRules.briefingEntry(path));
		Journal.unlockCodex(p, JournalEntries.CODEX_STARSHIPS);
		giveBench(p);

		switch (path) {
			case JEDI -> {
				TravelService.unlock(p, Destination.GREENWATCH);
				ChapterFourService.mentorLine(p, archiveKept(p) ? "brief.memory" : "brief.archive");
			}
			case SITH -> {
				TravelService.unlock(p, Destination.PROVING);
				ChapterFourService.mentorLine(p, "brief");
			}
			default -> {
				TravelService.unlock(p, Destination.GREENWATCH);
				TravelService.unlock(p, Destination.PROVING);
				p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.nav_log"));
				ChapterFourService.mentorLine(p, "leave");
			}
		}

		ChapterFourService.enterStage(p, ChapterFourStage.REPAIRS);
	}

	/** Whether Saelen reads the coordinates from memory: the Archive was kept or unmade (8.2). */
	private static boolean archiveKept(ServerPlayer p) {
		HolocronData holocrons = HolocronService.view(p);
		ArchiveChoice choice = holocrons == null ? null : holocrons.archiveChoice();
		return choice == ArchiveChoice.KEEP || choice == ArchiveChoice.UNMAKE;
	}

	/** One Engineering Bench as an item, and its recipe in the recipe book (7.1). */
	private static void giveBench(ServerPlayer p) {
		Item bench = BuiltInRegistries.ITEM.getOptional(BENCH).orElse(null);

		if (bench != null) {
			Rewards.give(p, new ItemStack(bench));
		} else {
			OldRepublicMod.LOGGER.warn("{} is not registered; {} gets no bench from the briefing", BENCH, p.getName().getString());
		}

		// API-CHECK: ServerPlayer#awardRecipesByKey(List<ResourceKey<Recipe<?>>>) (in the 26.2 javap dump); an unknown key is skipped by vanilla.
		p.awardRecipesByKey(List.of(ResourceKey.create(Registries.RECIPE, BENCH)));
	}

	/** SEEKER's wait for an Independent: the journal read at Courier's Fall plays the briefing. */
	static void tickSeeker(ServerPlayer p) {
		StoryData data = Story.get(p);
		BlockPos fall = data.chapter3.fall();

		if (data.storyPath() != Path.INDEPENDENT || fall == null || !Planets.isHomeWorld(p.level()) || !QuestItems.holdsJournal(p)
				|| p.position().distanceToSqr(Vec3.atCenterOf(fall)) > FALL_REACH * FALL_REACH) {
			return;
		}

		brief(p);
	}

	// ---------------------------------------------------------------- the hulk

	/** REPAIRS begins: the hulk and its cradle are placed, and the objective is shown. */
	static void onRepairs(ServerPlayer p) {
		placeHulk(p);
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c4.objective.repairs", partsFitted(Story.get(p)), ChapterFourRules.PARTS));
	}

	/**
	 * Places the hulk near the giver (W3's {@link Ships#placeHulk}): the mentor's camp, or for an
	 * Independent Courier's Fall. A failure retries every {@code retryTicks}; after
	 * {@code forwardFallbackAfter} failures it is placed {@code forwardFallback} blocks ahead of the
	 * player (8.8). Home world only.
	 *
	 * @return whether the hulk is placed now
	 */
	static boolean placeHulk(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.hasFlag(ChapterFourRules.HULK_PLACED)) {
			return true;
		}

		if (!Planets.isHomeWorld(p.level())) {
			return false;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		int failures = HULK_FAILURES.getOrDefault(p.getUUID(), 0);
		BlockPos near;

		if (failures >= b.forwardFallbackAfter) {
			Vec3 ahead = p.getLookAngle().horizontal();
			Vec3 at = p.position().add((ahead.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : ahead.normalize()).scale(b.forwardFallback));
			near = BlockPos.containing(at);
		} else {
			near = giverPos(p, data);
		}

		ShipRecord record = Ships.placeHulk(p, near);

		if (record == null) {
			HULK_FAILURES.put(p.getUUID(), failures + 1);
			ChapterFourService.retryLater(p, "hulk", b.retryTicks);
			OldRepublicMod.LOGGER.warn("Could not place the hulk for {} near {}; retrying in {} ticks", p.getName().getString(), near, b.retryTicks);
			return false;
		}

		HULK_FAILURES.remove(p.getUUID());
		StoryData fresh = Story.get(p);
		fresh.flags.add(ChapterFourRules.HULK_PLACED);
		fresh.chapter4 = fresh.chapter4.withSites(fresh.chapter4.sites().with(ChapterFourRules.SITE_HULK, record.pos()));
		Story.commit(p, fresh);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.hulk"));
		return true;
	}

	/** Where the giver is: the mentor's camp, Courier's Fall, or the player. */
	private static BlockPos giverPos(ServerPlayer p, StoryData data) {
		BlockPos at = data.storyPath() == Path.INDEPENDENT ? data.chapter3.fall() : data.camp;
		return at != null ? at : p.blockPosition();
	}

	/** REPAIRS' slow checks: the hulk's retries, and a ship already READY (a hook missed while offline). */
	static void tickRepairs(ServerPlayer p) {
		if (!Story.get(p).hasFlag(ChapterFourRules.HULK_PLACED) && ChapterFourService.due(p, "hulk")) {
			placeHulk(p);
		}

		ShipRecord ship = ShipRegistry.of(p.level().getServer(), p.getUUID()).orElse(null);

		if (ship != null && ship.state() == ShipState.READY) {
			ChapterFourService.enterStage(p, ChapterFourStage.FIRST_FLIGHT);
		}
	}

	/** The ship's state changed: READY during REPAIRS is lift-off. */
	static void onShipStateChanged(ServerPlayer owner, ShipState from, ShipState to) {
		if (to == ShipState.READY && Story.get(owner).chapter4.stage() == ChapterFourStage.REPAIRS) {
			ChapterFourService.enterStage(owner, ChapterFourStage.FIRST_FLIGHT);
		}
	}

	/** FIRST_FLIGHT begins: the ship stands up (once). */
	static void onLiftOff(ServerPlayer p) {
		if (!ChapterFourService.once(p, ChapterFourRules.LIFT_OFF)) {
			return;
		}

		Journal.unlockEntry(p, JournalEntries.THE_SHIP_FLIES);
		ChapterFourService.mentorLine(p, "lift_off");
		Path path = Story.get(p).storyPath();
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c4.objective." + switch (path) {
			case JEDI -> "fly_selvaren";
			case SITH -> "fly_varkhast";
			default -> "choose_world";
		}));
	}

	// ---------------------------------------------------------------- the parts

	/** Parts fitted so far, from the paid-XP flags. */
	static int partsFitted(StoryData data) {
		return ChapterFourRules.partsFitted(data);
	}

	/** A repair socket was completed: 300 XP once per socket, and the count. */
	static void onPartFitted(ServerPlayer owner, int socket) {
		if (socket < 0 || socket >= ChapterFourRules.PARTS || !ChapterFourService.once(owner, ChapterFourRules.PART_PREFIX + socket)) {
			return;
		}

		ChapterFourService.xp(owner, ChapterFourService.balance().partXp);
		owner.sendOverlayMessage(Component.translatable("quest.oldrepublic.c4.objective.repairs", partsFitted(Story.get(owner)), ChapterFourRules.PARTS));
	}

	// ---------------------------------------------------------------- the motivator (8.2, 12.2)

	/**
	 * Sense near a Wreck Field's {@code HEART} during REPAIRS: a motivator still in its cradle, once
	 * per player; a lost one is found again once a day while the socket is still empty.
	 */
	static void onForceSense(ServerPlayer p) {
		if (salvageDue(p) && wreckHeartNear(p, ChapterFourService.balance().salvageRange) != null) {
			salvage(p);
		}
	}

	/** Whether Sense at a Wreck Field would give a motivator now: REPAIRS, home, the socket empty, first time or the daily re-issue. */
	static boolean salvageDue(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.chapter4.stage() != ChapterFourStage.REPAIRS || data.hasFlag(ChapterFourRules.PART_PREFIX + MOTIVATOR_SOCKET)
				|| !Planets.isHomeWorld(p.level())) {
			return false;
		}

		boolean reissue = data.hasFlag(ChapterFourRules.MOTIVATOR_ROUTE_PREFIX + "salvage") && !carriesMotivator(p)
				&& ChapterFourRules.reissueDue(data, "motivator", ChapterFourService.day(p));
		return !data.hasFlag(ChapterFourRules.SALVAGED) || reissue;
	}

	/**
	 * The Wreck Field's motivator, found by Sense at its {@code HEART} (the caller found the anchor):
	 * dropped at the player's feet when {@link #salvageDue}. Public for the game tests, which have no
	 * Wreck Field to stand in.
	 *
	 * @return whether a motivator was given
	 */
	public static boolean salvage(ServerPlayer p) {
		if (!salvageDue(p)) {
			return false;
		}

		boolean first = !Story.get(p).hasFlag(ChapterFourRules.SALVAGED);
		long day = ChapterFourService.day(p);
		StoryData fresh = Story.get(p);
		fresh.flags.add(ChapterFourRules.SALVAGED);
		fresh.flags.add(ChapterFourRules.MOTIVATOR_ROUTE_PREFIX + "salvage");
		fresh.counters.put(ChapterFourRules.REISSUE_PREFIX + "motivator", (int) day);
		Story.commit(p, fresh);
		dropAtFeet(p, new ItemStack(M4Items.HYPERDRIVE_MOTIVATOR));
		p.sendSystemMessage(Component.translatable(first ? "message.oldrepublic.story.c4.salvage" : "message.oldrepublic.story.c4.salvage_again"));
		return true;
	}

	/** The {@code HEART} anchor of a Wreck Field within {@code range} blocks of the player, or null. */
	static @Nullable BlockPos wreckHeartNear(ServerPlayer p, int range) {
		for (SitePiece piece : StructureLocator.piecesNear(p.level(), p.blockPosition(), range + 64)) {
			if (piece.kind() != SiteKind.WRECK_FIELD) {
				continue;
			}

			BlockPos heart = StructureLocator.anchor(piece, SiteAnchors.Anchor.HEART);

			if (heart != null && p.position().distanceToSqr(Vec3.atCenterOf(heart)) <= (double) range * range) {
				return heart;
			}
		}

		return null;
	}

	static boolean carriesMotivator(ServerPlayer p) {
		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (stack.is(M4Items.HYPERDRIVE_MOTIVATOR)) {
				return true;
			}
		}

		return false;
	}

	/** Whether the smuggler would re-issue a lost motivator now (the {@code c4:smuggler} condition). */
	static boolean smugglerReissueDue(ServerPlayer p) {
		StoryData data = Story.get(p);
		return data.chapter4.stage() == ChapterFourStage.REPAIRS && !data.hasFlag(ChapterFourRules.PART_PREFIX + MOTIVATOR_SOCKET)
				&& data.hasFlag(ChapterFourRules.MOTIVATOR_ROUTE_PREFIX + "smuggler") && !carriesMotivator(p)
				&& ChapterFourRules.reissueDue(data, "motivator", ChapterFourService.day(p));
	}

	/** The smuggler's re-issue (12.2): a lost motivator from "A Part Off the Books", once a day while the socket is empty. */
	static boolean reissueSmugglerMotivator(ServerPlayer p) {
		if (!smugglerReissueDue(p)) {
			return false;
		}

		StoryData data = Story.get(p);
		long day = ChapterFourService.day(p);
		data.counters.put(ChapterFourRules.REISSUE_PREFIX + "motivator", (int) day);
		Story.commit(p, data);
		Rewards.give(p, new ItemStack(M4Items.HYPERDRIVE_MOTIVATOR));
		return true;
	}

	/** A bought motivator marks the route (a lost one is simply bought again). */
	static void onPurchase(ServerPlayer p, String vendor, ItemStack bought, int price) {
		if (bought.is(M4Items.HYPERDRIVE_MOTIVATOR)) {
			StoryData data = Story.get(p);

			if (data.flags.add(ChapterFourRules.MOTIVATOR_ROUTE_PREFIX + "bought")) {
				Story.commit(p, data);
			}
		}
	}

	/** Drops {@code stack} at the player's feet, for them alone (the M2 owned-drop rule). */
	static void dropAtFeet(ServerPlayer p, ItemStack stack) {
		ItemEntity entity = new ItemEntity(p.level(), p.getX(), p.getY() + 0.2, p.getZ(), stack, 0, 0.15, 0);
		entity.setTarget(p.getUUID());
		entity.setPickUpDelay(10);
		p.level().addFreshEntity(entity);
	}

	// ---------------------------------------------------------------- landing (8.3)

	/**
	 * A flight settled on {@code planet}: the first landing's banner, XP and entries, the host's
	 * welcome, and the end of FIRST_FLIGHT on the path's world (any landing counts: a bay, an
	 * overflow spot or a hyperspace fallback).
	 */
	static void onPlanetArrived(ServerPlayer p, Planets.Planet planet, boolean first) {
		if (planet == Planets.Planet.HOME) {
			return;
		}

		ChapterFourBalance b = ChapterFourService.balance();

		if (ChapterFourService.once(p, ChapterFourRules.LANDED_PREFIX + planet.id())) {
			ChapterFourService.banner(p, QuestEffects.LESSON_PLANET, planet.ordinal());
			ChapterFourService.xp(p, b.planetXp);
			Journal.unlockEntry(p, planet == Planets.Planet.SELVAREN ? JournalEntries.SELVAREN : JournalEntries.VARKHAST);
			Journal.unlockCodex(p, planet == Planets.Planet.SELVAREN ? JournalEntries.CODEX_SELVAREN : JournalEntries.CODEX_VARKHAST);
		}

		checkHub(p);
		StoryData data = Story.get(p);

		if (data.chapter4.stage().ordinal() >= ChapterFourStage.FIRST_FLIGHT.ordinal()
				&& ChapterFourService.once(p, ChapterFourRules.MET_PREFIX + planet.id())) {
			HubNpcs.greet(p, planet);
		}

		data = Story.get(p);

		if (data.chapter4.stage() == ChapterFourStage.FIRST_FLIGHT && ChapterFourRules.endsFirstFlight(data.storyPath(), planet)) {
			data.chapter4 = data.chapter4.withArc(data.chapter4.arc().withPlanet(planet.id()));
			Story.commit(p, data);
			ChapterFourService.enterStage(p, ChapterFourStage.WORLD_ARC);
		}
	}

	/** The hub's discovery XP, once per planet, when the player is near its hub (8.3). */
	static void checkHub(ServerPlayer p) {
		Planets.Planet planet = Planets.Planet.of(p.level().dimension());

		if (planet == null || planet == Planets.Planet.HOME || Story.get(p).hasFlag(ChapterFourRules.HUB_PREFIX + planet.id())) {
			return;
		}

		BlockPos hub = PlanetHubs.hub(p.level().getServer(), planet);

		if (hub != null && p.position().distanceToSqr(Vec3.atCenterOf(hub)) <= HUB_REACH * HUB_REACH
				&& ChapterFourService.once(p, ChapterFourRules.HUB_PREFIX + planet.id())) {
			ChapterFourService.xp(p, ChapterFourService.balance().hubXp);
			Journal.unlockCodex(p, planet == Planets.Planet.SELVAREN ? JournalEntries.CODEX_THE_WATCH_ON_SELVAREN : JournalEntries.CODEX_THE_PROVING_CODEX);
		}
	}

	/** The ship's home pad, for the hunt (the sabotage happens where the ship lands at home). */
	static @Nullable GlobalPos hulkPos(StoryData data) {
		return data.chapter4.sites().get(ChapterFourRules.SITE_HULK);
	}

	static void forget(ServerPlayer p) {
		HULK_FAILURES.remove(p.getUUID());
	}

	static void clear() {
		HULK_FAILURES.clear();
	}

	private ChapterFourRepairs() {
	}
}
