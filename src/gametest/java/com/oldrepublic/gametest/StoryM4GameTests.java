package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingRules;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.crafting.bench.BenchResult;
import com.oldrepublic.crafting.bench.BenchService;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.enemy.droid.SeekerDroid;
import com.oldrepublic.holocron.HolocronData;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.mission.MissionBalance;
import com.oldrepublic.mission.MissionData;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.mission.MissionDefinition.Kind;
import com.oldrepublic.mission.MissionFeature;
import com.oldrepublic.mission.MissionLoader;
import com.oldrepublic.mission.MissionRules;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.mission.PlanetContracts;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.ChapterFour;
import com.oldrepublic.quest.story.ChapterFourBalance;
import com.oldrepublic.quest.story.ChapterFourRepairs;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourService;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.KaastDuel;
import com.oldrepublic.quest.story.KaastOutcome;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.SeekerScene;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.TheHunt;
import com.oldrepublic.quest.story.WorldArcs;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.vendor.VendorBalance;
import com.oldrepublic.vendor.VendorEntry;
import com.oldrepublic.vendor.VendorFeature;
import com.oldrepublic.vendor.VendorItems;
import com.oldrepublic.vendor.VendorRules;
import com.oldrepublic.vendor.VendorStock;
import com.oldrepublic.vendor.VendorStocks;
import com.oldrepublic.vendor.Vendors;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Chapter Four, vendors and the economy (spec MILESTONE_4.md 17.1, W2): the stage machine and old
 * saves, the seeker's safe moment, the briefing and the motivator's routes, the first landing, the
 * world arc and its powers, the hunt and its clues, the duel's choice and the report, the back pay,
 * vendors, commissions and contract credits.
 *
 * <p>Test rules (M3 15.1, {@code WORKER_M4.md}): every test declares {@code maxTicks} (at most
 * 400); players are listed mock players removed at the end; the default empty structure; days are
 * explicit (pure rules take the day, and player tests that read the world's day compare with
 * {@link Clocks#day} on the same tick); nothing writes world, global or server state except the
 * sabotage test's ship record, which is keyed by its mock player and removed at the end. Everything
 * runs synchronously in the first tick, before the story's own tick touches the players. No server
 * test touches a real planet (the planets do not exist on the game-test server): planet arrivals are
 * the hooks' calls, and the trial rules are tested as rules.
 *
 * <p>Every player is set up with the chapter's world placements already marked done (the hulk, the
 * challenge's beacon, a far-away camp) and the Milestone 3 back pay settled, so nothing is placed in
 * the test level and no credits arrive behind a test's back.
 */
public class StoryM4GameTests {
	private static final ChapterFourBalance DEFAULTS = new ChapterFourBalance();
	private static final VendorBalance VENDOR_DEFAULTS = new VendorBalance();
	/** A camp far from every test, so CHALLENGED never places one and the ring is never entered by walking. */
	private static final GlobalPos FAR_CAMP = GlobalPos.of(Level.OVERWORLD, new BlockPos(29_000_000, 80, 29_000_000));
	/** The explicit day of the pure daily rules. */
	private static final long DAY = 40L;
	private static final BlockPos TERMINAL = new BlockPos(100, 64, 100);

	// ---------------------------------------------------------------- helpers

	private static Component msg(String text) {
		return Component.literal(text);
	}

	/**
	 * A listed player on {@code path} whose Chapter Three is complete, at Chapter Four {@code stage}
	 * (set directly: no stage entry logic runs), with the placements marked done and the back pay settled.
	 */
	private static ServerPlayer at(GameTestHelper helper, BlockPos relative, Path path, ChapterFourStage stage) {
		ServerPlayer p = M3TestPlayers.listed(helper, relative);
		IdentityData identity = Identity.get(p);
		identity.path = path;
		Identity.commit(p, identity);
		StoryData data = Story.get(p);
		data.path = path.id();
		data.pathStage = (path == Path.JEDI || path == Path.SITH) ? PathStage.COMPLETE.ordinal() : data.pathStage;
		data.chapter3 = ChapterThreeRules.withStage(data.chapter3, ChapterThreeStage.COMPLETE);
		data.flags.add(ChapterFourRules.HULK_PLACED);
		data.flags.add(ChapterFourRules.CHALLENGE_PLAYED);
		data.chapter4 = data.chapter4.withStage(stage, 0L).withBackPay().withDuel(StoryData.Chapter4.Duel.NONE.withCamp(FAR_CAMP));
		Story.commit(p, data);
		return p;
	}

	private static ServerPlayer at(GameTestHelper helper, Path path, ChapterFourStage stage) {
		return at(helper, new BlockPos(1, 1, 1), path, stage);
	}

	private static StoryData story(ServerPlayer p) {
		return Story.get(p);
	}

	private static StoryData.Chapter4 c4(ServerPlayer p) {
		return Story.get(p).chapter4;
	}

	private static void flag(ServerPlayer p, String flag) {
		StoryData data = Story.get(p);
		data.flags.add(flag);
		Story.commit(p, data);
	}

	private static void arcSteps(ServerPlayer p, int steps) {
		StoryData data = Story.get(p);
		data.chapter4 = data.chapter4.withArc(data.chapter4.arc().withMissionFlags(data.chapter4.arc().missionFlags() | steps));
		Story.commit(p, data);
	}

	private static void level(ServerPlayer p, int level) {
		ForceData force = Progression.get(p);
		force.level = level;
		Progression.commit(p, force);
	}

	/** All XP the player has earned: the levels behind them plus the XP into the current one. */
	private static long totalXp(ServerPlayer p) {
		ForceData data = Progression.get(p);
		long total = data.xp;

		for (int level = 1; level < data.level; level++) {
			total += Progression.xpToNext(level);
		}

		return total;
	}

	/**
	 * Skill points gained since {@code sp} at {@code level}, without the ones the levels gained since
	 * paid: a story reward's XP can level a fresh test player, and each level pays its own point.
	 */
	private static int storySp(ServerPlayer p, int sp, int level) {
		ForceData data = Progression.get(p);
		return data.skillPoints - sp - (data.level - level) * Progression.balance().skillPointsPerLevel;
	}

	private static int count(ServerPlayer p, Item item) {
		Inventory inventory = p.getInventory();
		int n = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				n += inventory.getItem(i).getCount();
			}
		}

		return n;
	}

	private static void removeAll(ServerPlayer p, Item item) {
		Inventory inventory = p.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	private static boolean entry(ServerPlayer p, String id) {
		return Progression.get(p).flags.contains(JournalEntries.entryFlag(id));
	}

	private static boolean codex(ServerPlayer p, String id) {
		return Progression.get(p).flags.contains(JournalEntries.codexFlag(id));
	}

	private static int credits(ServerPlayer p) {
		return Credits.count(p, false);
	}

	private static void action(ServerPlayer p, String id) {
		DialogueActions.Action a = DialogueActions.get(id);

		if (a == null) {
			throw new IllegalStateException("No dialogue action " + id);
		}

		a.run(p, null);
	}

	private static void action(ServerPlayer p, String id, String arg) {
		DialogueActions.ArgAction a = DialogueActions.getArg(id);

		if (a == null) {
			throw new IllegalStateException("No dialogue argument action " + id);
		}

		a.run(p, null, arg);
	}

	private static @Nullable String condition(ServerPlayer p, String id, String arg) {
		DialogueConditions.Condition c = DialogueConditions.get(id);

		if (c == null) {
			throw new IllegalStateException("No dialogue condition " + id);
		}

		return c.failReason(p, null, arg);
	}

	/** A loaded mission definition; fails the test when the data pack did not load it. */
	private static MissionDefinition definition(GameTestHelper helper, String id) {
		MissionDefinition def = MissionLoader.get(id);
		helper.assertTrue(def != null, msg("the mission definition " + id + " must be loaded from data/oldrepublic/mission/"));
		return def;
	}

	/** Gives the player a skill node directly (owned, bought for {@code spent} points), as a purchase would. */
	private static void own(ServerPlayer p, String node, int spent) {
		SkillData data = SkillService.data(p);
		data.nodes.put(node, 1);

		if (spent > 0) {
			data.spent.put(node, spent);
		}

		SkillService.commit(p, data);
	}

	/** Fills every open ability slot so a granted power finds none free. */
	private static void fillSlots(ServerPlayer p) {
		SkillData data = SkillService.data(p);

		for (int i = 0; i < Math.min(data.openSlots, data.slots.size()); i++) {
			if (data.slot(i).isEmpty()) {
				data.slots.set(i, "w2_test_filler_" + i);
			}
		}

		SkillService.commit(p, data);
	}

	private static void freeSlots(ServerPlayer p) {
		SkillData data = SkillService.data(p);

		for (int i = 0; i < data.slots.size(); i++) {
			if (data.slot(i).startsWith("w2_test_filler_")) {
				data.slots.set(i, "");
			}
		}

		SkillService.commit(p, data);
	}

	private static void discardDropsNear(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4)).forEach(Entity::discard);
	}

	private static void removeShip(MinecraftServer server, UUID owner) {
		ShipRegistry.Data ships = ShipRegistry.snapshot(server);
		ships.remove(owner);
		ShipRegistry.commit(server, ships);
	}

	private static StoryData pureAt(Path path, ChapterFourStage stage) {
		StoryData data = new StoryData();
		data.path = path.id();
		data.chapter4 = data.chapter4.withStage(stage, 0L);
		return data;
	}

	// ---------------------------------------------------------------- stages and saves

	@GameTest(maxTicks = 20)
	public void stagesOnlyMoveForward(GameTestHelper helper) {
		StoryData data = new StoryData();
		helper.assertTrue(ChapterFourRules.advance(data, ChapterFourStage.MARKED, 5L), msg("NOT_STARTED moves on to MARKED"));
		helper.assertTrue(data.chapter4.waitSince() == 5L, msg("the stage's wait starts when it is entered"));
		helper.assertTrue(!ChapterFourRules.advance(data, ChapterFourStage.NOT_STARTED, 6L), msg("MARKED never goes back"));
		helper.assertTrue(!ChapterFourRules.advance(data, ChapterFourStage.MARKED, 7L), msg("entering the same stage again changes nothing"));
		helper.assertTrue(data.chapter4.stage() == ChapterFourStage.MARKED && data.chapter4.waitSince() == 5L, msg("MARKED stays, its wait unchanged"));
		helper.assertTrue(ChapterFourRules.parseStage(" world_arc ") == ChapterFourStage.WORLD_ARC && ChapterFourRules.parseStage("nowhere") == null,
				msg("stage names parse in any case; unknown names are null"));
		helper.assertTrue(ChapterFourStage.of(99) == ChapterFourStage.COMPLETE && ChapterFourStage.of(-3) == ChapterFourStage.NOT_STARTED,
				msg("a saved ordinal out of range is clamped"));

		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.DUEL);

		try {
			helper.assertTrue(!ChapterFourService.enterStage(p, ChapterFourStage.REPAIRS), msg("enterStage refuses an earlier stage"));
			helper.assertTrue(c4(p).stage() == ChapterFourStage.DUEL, msg("the stage stays DUEL, got " + c4(p).stage()));
			helper.assertTrue(ChapterFourService.enterStage(p, ChapterFourStage.COMPLETE) && c4(p).stage() == ChapterFourStage.COMPLETE,
					msg("enterStage moves forward"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void oldSaveAtC3CompleteEntersMarked(GameTestHelper helper) {
		// An M3 save: Chapter Three complete, no chapter4 field at all.
		StoryData m3 = new StoryData();
		m3.path = Path.SITH.id();
		m3.pathStage = PathStage.COMPLETE.ordinal();
		m3.chapter3 = ChapterThreeRules.withStage(m3.chapter3, ChapterThreeStage.COMPLETE);
		JsonObject saved = StoryData.CODEC.encodeStart(JsonOps.INSTANCE, m3).getOrThrow().getAsJsonObject();
		saved.remove("chapter4");
		StoryData loaded = StoryData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(loaded.chapter4.equals(StoryData.Chapter4.NEW), msg("no chapter4 field loads as NEW"));
		helper.assertTrue(ChapterFourRules.shouldMark(loaded), msg("an M3 save at Chapter Three COMPLETE should begin Chapter Four"));

		StoryData full = new StoryData();
		full.chapter4 = new StoryData.Chapter4(ChapterFourStage.DUEL, 12L, new StoryData.Chapter4.Arc("varkhast", 0b101, 2),
				new StoryData.Chapter4.Hunt(0b111, true, 9L, 2), new StoryData.Chapter4.Duel(FAR_CAMP, 1, 1),
				StoryData.Chapter4.Sites.NONE.with(ChapterFourRules.SITE_BEACON, FAR_CAMP), true);
		StoryData back = StoryData.CODEC.parse(JsonOps.INSTANCE, StoryData.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow()).getOrThrow();
		helper.assertTrue(back.chapter4.equals(full.chapter4), msg("every Chapter Four field survives the save: " + back.chapter4));

		ServerPlayer old = M3TestPlayers.listed(helper);
		ServerPlayer midway = M3TestPlayers.listed(helper, new BlockPos(3, 1, 1));

		try {
			Story.commit(old, loaded);
			helper.assertTrue(ChapterFourService.checkMarked(old), msg("the old save enters MARKED on its first check"));
			helper.assertTrue(c4(old).stage() == ChapterFourStage.MARKED && c4(old).waitSince() >= 0, msg("MARKED with its wait counted from now"));
			helper.assertTrue(!ChapterFourService.checkMarked(old), msg("a second check changes nothing"));

			StoryData leads = Story.get(midway);
			leads.chapter3 = ChapterThreeRules.withStage(leads.chapter3, ChapterThreeStage.LEADS);
			Story.commit(midway, leads);
			helper.assertTrue(!ChapterFourService.checkMarked(midway) && c4(midway).stage() == ChapterFourStage.NOT_STARTED,
					msg("a player still in Chapter Three is not marked"));
		} finally {
			M3TestPlayers.remove(old);
			M3TestPlayers.remove(midway);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the seeker

	@GameTest(maxTicks = 20)
	public void seekerSceneOnSafeMomentOnly(GameTestHelper helper) {
		ChapterFourBalance b = ChapterFourService.balance();
		StoryData waiting = pureAt(Path.JEDI, ChapterFourStage.MARKED);
		waiting.chapter4 = waiting.chapter4.withStage(ChapterFourStage.MARKED, 1000L);
		helper.assertTrue(!ChapterFourRules.seekerDue(waiting, 1000L + b.waitTicks - 1, b), msg("MARKED waits a full day first"));
		helper.assertTrue(ChapterFourRules.seekerDue(waiting, 1000L + b.waitTicks, b), msg("after the wait the seeker is due"));
		waiting.flags.add(ChapterFourRules.SEEKER_DUE);
		helper.assertTrue(ChapterFourRules.seekerDue(waiting, 1001L, b), msg("waking from a sleep makes it due at once"));
		helper.assertTrue(!ChapterFourRules.seekerDue(pureAt(Path.JEDI, ChapterFourStage.SEEKER), Long.MAX_VALUE / 2, b),
				msg("only MARKED waits for the seeker"));

		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.MARKED);
		Mob zombie = null;

		try {
			StoryData data = Story.get(p);
			data.chapter4 = data.chapter4.withStage(ChapterFourStage.MARKED, helper.getLevel().getGameTime());
			Story.commit(p, data);
			p.setOnGround(true);
			SeekerScene.check(p);
			helper.assertTrue(!SeekerScene.playing(p), msg("not due yet: no scene"));

			flag(p, ChapterFourRules.SEEKER_DUE);
			zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
			helper.assertTrue(!SeekerScene.isSafe(p, b), msg("a hostile within range is no safe moment"));
			SeekerScene.check(p);
			helper.assertTrue(!SeekerScene.playing(p), msg("due, but a hostile is near: no scene"));

			zombie.discard();
			zombie = null;
			helper.assertTrue(SeekerScene.isSafe(p, b), msg("on the ground, unhurt, nothing hostile near: safe"));
			SeekerScene.check(p);
			helper.assertTrue(SeekerScene.playing(p), msg("due and safe: the scene plays"));
			helper.assertTrue(!helper.getLevel().getEntitiesOfClass(SeekerDroid.class, p.getBoundingBox().inflate(32), Entity::isAlive).isEmpty(),
					msg("its droid stays in the level (the scene's tag goes on after the spawn, so the load rule keeps it)"));
			SeekerScene.forget(p);
			helper.assertTrue(!SeekerScene.playing(p) && c4(p).stage() == ChapterFourStage.MARKED && story(p).hasFlag(ChapterFourRules.SEEKER_DUE),
					msg("a cut-off scene leaves the mark due for the next safe moment"));
		} finally {
			if (zombie != null) {
				zombie.discard();
			}

			SeekerScene.forget(p);
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the briefing and repairs

	@GameTest(maxTicks = 20)
	public void briefingPerPathGivesHulkBenchAndDiscipline(GameTestHelper helper) {
		ChapterFourBalance b = ChapterFourService.balance();
		Item bench = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("oldrepublic", "engineering_bench")).orElse(null);
		Path[] paths = {Path.JEDI, Path.SITH, Path.INDEPENDENT};
		String[] entries = {JournalEntries.THE_PATIENT_STAR, JournalEntries.A_WRITTEN_OFF_COURIER, JournalEntries.VASCHS_COURIER};
		List<ServerPlayer> players = new ArrayList<>();

		try {
			for (int i = 0; i < paths.length; i++) {
				ServerPlayer p = at(helper, new BlockPos(1 + 2 * i, 1, 1), paths[i], ChapterFourStage.SEEKER);
				players.add(p);
				long xp = totalXp(p);
				action(p, "c4:brief");
				String who = paths[i].id();
				helper.assertTrue(c4(p).stage() == ChapterFourStage.REPAIRS, msg(who + ": the briefing moves to REPAIRS, got " + c4(p).stage()));
				helper.assertTrue(totalXp(p) - xp == b.briefXp, msg(who + ": the briefing pays " + b.briefXp + " XP, got " + (totalXp(p) - xp)));
				helper.assertTrue(entry(p, entries[i]) && codex(p, JournalEntries.CODEX_STARSHIPS), msg(who + ": entry " + entries[i] + " and the starships codex"));
				helper.assertTrue(story(p).hasFlag(ChapterFourRules.BRIEFED) && story(p).hasFlag(ChapterFourRules.HULK_PLACED),
						msg(who + ": briefed, and the hulk is placed (marked here; the placement is W3's Ships.placeHulk)"));
				helper.assertTrue(bench == null || count(p, bench) == 1, msg(who + ": one Engineering Bench as an item"));
				boolean green = TravelService.isUnlocked(p, Destination.GREENWATCH);
				boolean proving = TravelService.isUnlocked(p, Destination.PROVING);
				helper.assertTrue(paths[i] == Path.JEDI ? green && !proving : paths[i] == Path.SITH ? proving && !green : green && proving,
						msg(who + ": the path's coordinates (both worlds for an Independent), got greenwatch " + green + ", proving " + proving));

				action(p, "c4:brief");
				helper.assertTrue(totalXp(p) - xp == b.briefXp && (bench == null || count(p, bench) == 1), msg(who + ": the briefing is given once"));

				QuestHooks.onDisciplineLevel(p, "artifice", 1);
				helper.assertTrue(entry(p, JournalEntries.A_TRADE) && codex(p, JournalEntries.CODEX_DISCIPLINES),
						msg(who + ": choosing the first discipline at the bench writes a_trade"));
			}

			if (bench == null) {
				helper.assertTrue(false, msg("oldrepublic:engineering_bench (W5) is not registered; the briefing gives no bench"));
			}
		} finally {
			players.forEach(M3TestPlayers::remove);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void motivatorThreeRoutes(GameTestHelper helper) {
		ChapterFourBalance b = ChapterFourService.balance();
		ServerPlayer salvager = at(helper, new BlockPos(1, 1, 1), Path.JEDI, ChapterFourStage.REPAIRS);
		ServerPlayer smuggled = at(helper, new BlockPos(3, 1, 1), Path.SITH, ChapterFourStage.REPAIRS);
		ServerPlayer buyer = at(helper, new BlockPos(5, 1, 1), Path.INDEPENDENT, ChapterFourStage.REPAIRS);

		try {
			// Salvage: Sense at a Wreck Field's HEART (the anchor search is the M3 survey check; here the find is called directly).
			helper.assertTrue(ChapterFourRepairs.salvage(salvager), msg("the first salvage gives a motivator"));
			helper.assertTrue(story(salvager).hasFlag(ChapterFourRules.SALVAGED) && story(salvager).hasFlag(ChapterFourRules.MOTIVATOR_ROUTE_PREFIX + "salvage"),
					msg("the salvage route is recorded"));
			helper.assertTrue(!ChapterFourRepairs.salvage(salvager), msg("the same day gives no second motivator"));

			// "A Part Off the Books": its pay gives the motivator and 100 credits.
			int before = credits(smuggled);
			WorldArcs.payMission(smuggled, definition(helper, ChapterFourRules.OFF_THE_BOOKS));
			helper.assertTrue(count(smuggled, M4Items.HYPERDRIVE_MOTIVATOR) == 1, msg("the poachers' cache holds the motivator"));
			helper.assertTrue(credits(smuggled) - before == b.offTheBooksCredits, msg("the mission pays " + b.offTheBooksCredits + " credits"));
			helper.assertTrue(story(smuggled).hasFlag(ChapterFourRules.MOTIVATOR_ROUTE_PREFIX + "smuggler")
					&& entry(smuggled, JournalEntries.A_PART_OFF_THE_BOOKS), msg("the smuggler route and its entry"));

			// Requisition: a bought motivator marks its route.
			QuestHooks.onPurchase(buyer, "requisition_republic", new ItemStack(M4Items.HYPERDRIVE_MOTIVATOR), 400);
			helper.assertTrue(story(buyer).hasFlag(ChapterFourRules.MOTIVATOR_ROUTE_PREFIX + "bought"), msg("the bought route is recorded"));
			VendorStock requisition = VendorStocks.get("requisition_republic");
			helper.assertTrue(requisition != null && requisition.entries().stream().anyMatch(e -> e.item().is(M4Items.HYPERDRIVE_MOTIVATOR)),
					msg("the Republic requisition stock sells a motivator"));
		} finally {
			discardDropsNear(helper, salvager);
			M3TestPlayers.remove(salvager);
			M3TestPlayers.remove(smuggled);
			M3TestPlayers.remove(buyer);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void partsXpOnce(GameTestHelper helper) {
		ChapterFourBalance b = ChapterFourService.balance();
		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.REPAIRS);

		try {
			long xp = totalXp(p);
			QuestHooks.onShipPartFitted(p, 0);
			helper.assertTrue(totalXp(p) - xp == b.partXp, msg("a fitted part pays " + b.partXp + " XP"));
			QuestHooks.onShipPartFitted(p, 0);
			QuestHooks.onShipPartFitted(p, -1);
			QuestHooks.onShipPartFitted(p, ChapterFourRules.PARTS);
			helper.assertTrue(totalXp(p) - xp == b.partXp && ChapterFourRules.partsFitted(story(p)) == 1,
					msg("the same socket pays once; unknown sockets pay nothing"));

			for (int socket = 1; socket < ChapterFourRules.PARTS; socket++) {
				QuestHooks.onShipPartFitted(p, socket);
			}

			helper.assertTrue(totalXp(p) - xp == (long) b.partXp * ChapterFourRules.PARTS && ChapterFourRules.partsFitted(story(p)) == ChapterFourRules.PARTS,
					msg("four parts pay four times"));
			helper.assertTrue(c4(p).stage() == ChapterFourStage.REPAIRS, msg("the parts alone do not lift off: the ship must stand READY"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- first flight and the arc

	@GameTest(maxTicks = 20)
	public void firstFlightEndsOnAnyLandingOnPathWorld(GameTestHelper helper) {
		helper.assertTrue(ChapterFourRules.endsFirstFlight(Path.JEDI, Planets.Planet.SELVAREN) && !ChapterFourRules.endsFirstFlight(Path.JEDI, Planets.Planet.VARKHAST),
				msg("a Jedi's first flight ends on Selvaren only"));
		helper.assertTrue(ChapterFourRules.endsFirstFlight(Path.SITH, Planets.Planet.VARKHAST) && !ChapterFourRules.endsFirstFlight(Path.SITH, Planets.Planet.SELVAREN),
				msg("a Sith's first flight ends on Varkhast only"));
		helper.assertTrue(ChapterFourRules.endsFirstFlight(Path.INDEPENDENT, Planets.Planet.SELVAREN)
				&& ChapterFourRules.endsFirstFlight(Path.INDEPENDENT, Planets.Planet.VARKHAST)
				&& !ChapterFourRules.endsFirstFlight(Path.INDEPENDENT, Planets.Planet.HOME), msg("an Independent's ends on either planet"));

		ServerPlayer jedi = at(helper, new BlockPos(1, 1, 1), Path.JEDI, ChapterFourStage.FIRST_FLIGHT);
		ServerPlayer wanderer = at(helper, new BlockPos(3, 1, 1), Path.INDEPENDENT, ChapterFourStage.FIRST_FLIGHT);

		try {
			QuestHooks.onPlanetArrived(jedi, Planets.Planet.HOME, false);
			QuestHooks.onPlanetArrived(jedi, Planets.Planet.VARKHAST, true);
			helper.assertTrue(c4(jedi).stage() == ChapterFourStage.FIRST_FLIGHT, msg("home and the other world do not end a Jedi's first flight"));
			helper.assertTrue(entry(jedi, JournalEntries.VARKHAST), msg("the landing on Varkhast still writes its entry"));
			QuestHooks.onPlanetArrived(jedi, Planets.Planet.SELVAREN, true);
			helper.assertTrue(c4(jedi).stage() == ChapterFourStage.WORLD_ARC && Planets.Planet.SELVAREN.equals(c4(jedi).arcPlanet()),
					msg("landing on Selvaren (any pad or fallback) begins the arc there, got " + c4(jedi).stage()));

			long xp = totalXp(jedi);
			QuestHooks.onPlanetArrived(jedi, Planets.Planet.SELVAREN, false);
			helper.assertTrue(totalXp(jedi) == xp, msg("the planet's discovery XP is paid once"));

			QuestHooks.onPlanetArrived(wanderer, Planets.Planet.VARKHAST, true);
			helper.assertTrue(c4(wanderer).stage() == ChapterFourStage.WORLD_ARC && Planets.Planet.VARKHAST.equals(c4(wanderer).arcPlanet()),
					msg("an Independent's first landing on either world begins the arc"));
		} finally {
			MissionService.abandon(wanderer);
			M3TestPlayers.remove(jedi);
			M3TestPlayers.remove(wanderer);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void arcOrderFreeAndPowerOwnedCompletesStep(GameTestHelper helper) {
		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.JEDI, ChapterFourStage.WORLD_ARC);
		ServerPlayer owner = at(helper, new BlockPos(3, 1, 1), Path.SITH, ChapterFourStage.FIRST_FLIGHT);

		try {
			WorldArcs.payMission(p, definition(helper, ChapterFourRules.HEARTSTONE));
			StoryData.Chapter4.Arc arc = c4(p).arc();
			helper.assertTrue(arc.has(ChapterFourRules.STEP_SECOND) && !arc.has(ChapterFourRules.STEP_FIRST),
					msg("the Heartstone can come before the Greenwatch"));
			helper.assertTrue(!arc.has(ChapterFourRules.STEP_POWER), msg("the mission alone is not the power: the trial's grant is"));
			WorldArcs.payMission(p, definition(helper, ChapterFourRules.GREENWATCH));
			helper.assertTrue(c4(p).arc().has(ChapterFourRules.STEP_FIRST | ChapterFourRules.STEP_SECOND), msg("both steps, in either order"));
			helper.assertTrue(c4(p).stage() == ChapterFourStage.WORLD_ARC, msg("without the power and an expedition the arc goes on"));

			own(owner, ChapterFourRules.arcPower(Path.SITH), 0);
			QuestHooks.onPlanetArrived(owner, Planets.Planet.VARKHAST, true);
			helper.assertTrue(c4(owner).stage() == ChapterFourStage.WORLD_ARC && c4(owner).arc().has(ChapterFourRules.STEP_POWER),
					msg("Choke already owned when the arc begins completes the power step"));
			helper.assertTrue(ChapterFourRules.arcChecklist(c4(owner), Path.SITH).size() == 4, msg("the journal lists four arc steps"));
		} finally {
			M3TestPlayers.remove(p);
			M3TestPlayers.remove(owner);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void grantedPowerWaitsForFreeSlot(GameTestHelper helper) {
		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.WORLD_ARC);

		try {
			fillSlots(p);
			helper.assertTrue(!WorldArcs.grantPower(p, "force_barrier"), msg("no free slot: the grant waits"));
			helper.assertTrue(SkillService.data(p).rank("force_barrier") == 0 && !c4(p).arc().has(ChapterFourRules.STEP_POWER),
					msg("nothing is granted while every slot is full"));
			freeSlots(p);
			helper.assertTrue(WorldArcs.grantPower(p, "force_barrier"), msg("a freed slot takes the power (force_barrier must be a W6 skill node)"));
			helper.assertTrue(SkillService.data(p).owns("force_barrier") && story(p).hasFlag(ChapterFourRules.POWER_GRANTED)
					&& c4(p).arc().has(ChapterFourRules.STEP_POWER), msg("granted, slotted, and the arc's power step is done"));
			helper.assertTrue(WorldArcs.grantPower(p, "force_barrier"), msg("an owned power counts at once"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void heartstoneCompletesOnlyWithBarrierSinceWave3(GameTestHelper helper) {
		ChapterFourBalance b = DEFAULTS;
		int lastWave = b.heartstoneWaves[b.heartstoneWaves.length - 1];
		helper.assertTrue(!ChapterFourRules.barrierCounts(lastWave - 1, b) && ChapterFourRules.barrierCounts(lastWave, b),
				msg("a Barrier raised before the third wave does not count; from it on it does"));
		helper.assertTrue(!ChapterFourRules.heartstoneDone(b.heartstoneTicks, true, false, b), msg("time up without a raise since wave 3: the stone waits"));
		helper.assertTrue(!ChapterFourRules.heartstoneDone(b.heartstoneTicks - 1, true, true, b), msg("raised, but before the 90 seconds are up"));
		helper.assertTrue(!ChapterFourRules.heartstoneDone(b.heartstoneTicks, false, true, b), msg("never granted: not done"));
		helper.assertTrue(ChapterFourRules.heartstoneDone(b.heartstoneTicks, true, true, b), msg("time up, granted and raised since wave 3: done"));
		helper.assertTrue(ChapterFourRules.heartstoneDone(b.heartstoneTicks + 4000, true, true, b), msg("a late raise ends it whenever it comes"));
		helper.assertTrue(b.heartstoneGrantTicks > b.heartstoneWaves[1] && b.heartstoneGrantTicks < lastWave,
				msg("the grant comes between the second and third waves"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void sepulchreGripReleasesAtGrant(GameTestHelper helper) {
		ChapterFourBalance b = DEFAULTS;
		long gripAt = 500L;
		helper.assertTrue(!ChapterFourRules.gripEnds(gripAt + b.sepulchreGripTicks - 1, gripAt, true, b), msg("the grip holds its full time"));
		helper.assertTrue(ChapterFourRules.gripEnds(gripAt + b.sepulchreGripTicks, gripAt, true, b), msg("the grip ends at its time (Choke is granted then)"));
		helper.assertTrue(ChapterFourRules.gripEnds(gripAt + 3, gripAt, false, b), msg("freed early by a Push, the grant comes at once"));
		helper.assertTrue(!ChapterFourRules.gripEnds(gripAt + 1000, -1L, false, b), msg("no grip yet: nothing ends"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void speedRefundForBoughtNode(GameTestHelper helper) {
		ServerPlayer p = at(helper, Path.INDEPENDENT, ChapterFourStage.WORLD_ARC);

		try {
			own(p, "force_speed", 3);
			int sp = Progression.get(p).skillPoints;
			WorldArcs.teachSpeed(p);
			helper.assertTrue(Progression.get(p).skillPoints - sp == 3, msg("Tessaly returns the 3 points spent on Force Speed, got "
					+ (Progression.get(p).skillPoints - sp)));
			SkillData skills = SkillService.data(p);
			helper.assertTrue(skills.owns("force_speed") && skills.isGranted("force_speed") && !skills.spent.containsKey("force_speed"),
					msg("the bought node is now a granted one"));
			helper.assertTrue(c4(p).arc().has(ChapterFourRules.STEP_POWER) && story(p).hasFlag(ChapterFourRules.SPEED_REFUND), msg("the power step and the refund flag"));
			helper.assertTrue(skills.owns("speed_wayfarer"), msg("the Wayfarer branch is granted with it (a W6 node)"));

			own(p, "force_speed", 3);
			int again = Progression.get(p).skillPoints;
			WorldArcs.teachSpeed(p);
			helper.assertTrue(Progression.get(p).skillPoints == again, msg("the refund is paid once"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void arcMissionsPay150(GameTestHelper helper) {
		ChapterFourBalance b = ChapterFourService.balance();
		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.WORLD_ARC);

		try {
			int jedi = Identity.reputation(p, Faction.JEDI_ORDER);
			int before = credits(p);
			long xp = totalXp(p);
			WorldArcs.payMission(p, definition(helper, ChapterFourRules.GREENWATCH));
			helper.assertTrue(credits(p) - before == b.arcMissionCredits, msg("an arc mission pays " + b.arcMissionCredits + " credits"));
			helper.assertTrue(totalXp(p) - xp == b.arcMissionXp, msg("and " + b.arcMissionXp + " XP"));
			helper.assertTrue(Identity.reputation(p, Faction.JEDI_ORDER) - jedi == b.arcRep && entry(p, JournalEntries.THE_GREENWATCH),
					msg("the Jedi Order's standing and the entry"));

			before = credits(p);
			xp = totalXp(p);
			WorldArcs.payMission(p, definition(helper, ChapterFourRules.HEARTSTONE));
			helper.assertTrue(credits(p) - before == b.arcMissionCredits && totalXp(p) - xp == b.arcPowerMissionXp,
					msg("the power mission pays the same credits and " + b.arcPowerMissionXp + " XP"));
			helper.assertTrue(codex(p, JournalEntries.CODEX_FORCE_BARRIER), msg("and the power's codex"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void expeditionSpOncePerExpedition(GameTestHelper helper) {
		ChapterFourBalance b = ChapterFourService.balance();
		ServerPlayer p = at(helper, Path.SITH, ChapterFourStage.WORLD_ARC);

		try {
			// The expedition's XP may level the player up; those levels' own points are not the expedition's.
			int sp = Progression.get(p).skillPoints;
			int level = Progression.get(p).level;
			QuestHooks.onExpeditionDone(p, PlanetSites.Site.DUSKGLASS_RIFT);
			helper.assertTrue(storySp(p, sp, level) == b.expeditionSp && entry(p, JournalEntries.THE_RIFT),
					msg("the rift pays " + b.expeditionSp + " SP and writes its entry, got " + storySp(p, sp, level)
							+ " SP, entry " + entry(p, JournalEntries.THE_RIFT)));
			helper.assertTrue(c4(p).arc().expedition() == ChapterFourRules.EXPEDITION_DONE, msg("the arc's expedition is done"));
			long xp = totalXp(p);
			QuestHooks.onExpeditionDone(p, PlanetSites.Site.DUSKGLASS_RIFT);
			helper.assertTrue(storySp(p, sp, level) == b.expeditionSp && totalXp(p) == xp, msg("the same expedition pays once"));

			QuestHooks.onExpeditionDone(p, PlanetSites.Site.SINGING_HOLLOW);
			helper.assertTrue(storySp(p, sp, level) == 2 * b.expeditionSp, msg("another expedition pays its own SP, got " + storySp(p, sp, level)));
			helper.assertTrue(condition(p, "c4:crystal", "") == null, msg("the Hollow's crystal waits for its hue"));
			action(p, "c4:crystal", "green");
			helper.assertTrue(condition(p, "c4:crystal", "") != null, msg("chosen once, the crystal is given"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void skipExpeditionKeepsJournalEntry(GameTestHelper helper) {
		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.WORLD_ARC);

		try {
			helper.assertTrue(condition(p, "c4:not_now", "") != null, msg("\"Not now\" waits for the power"));
			arcSteps(p, ChapterFourRules.STEP_POWER);
			helper.assertTrue(condition(p, "c4:not_now", "") == null, msg("with the power learned, the expedition may wait"));
			action(p, "c4:not_now");
			helper.assertTrue(c4(p).arc().expedition() == ChapterFourRules.EXPEDITION_SKIPPED && c4(p).stage() == ChapterFourStage.HUNTED,
					msg("skipped, the arc ends and the hunt begins, got " + c4(p).stage()));

			int sp = Progression.get(p).skillPoints;
			QuestHooks.onExpeditionDone(p, PlanetSites.Site.SINGING_HOLLOW);
			helper.assertTrue(entry(p, JournalEntries.THE_SINGING_HOLLOW) && Progression.get(p).skillPoints > sp,
					msg("a skipped expedition done later still writes its entry and pays"));
			helper.assertTrue(c4(p).stage() == ChapterFourStage.HUNTED && c4(p).arc().expedition() == ChapterFourRules.EXPEDITION_DONE,
					msg("the stage stays HUNTED; the expedition counts as done"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the hunt

	@GameTest(maxTicks = 20)
	public void huntEventsRecordedAsFlagsBeforeHunted(GameTestHelper helper) {
		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.WORLD_ARC);

		try {
			level(p, ChapterFourService.balance().duelLevel);

			for (int clue = 1; clue <= ChapterFour.MAX_CLUE; clue++) {
				ChapterFour.addClue(p, clue);
			}

			helper.assertTrue(ChapterFour.clues(p) == ChapterFour.MAX_CLUE && c4(p).stage() == ChapterFourStage.WORLD_ARC,
					msg("clues found during the arc are recorded; the stage waits for the arc"));
			helper.assertTrue(entry(p, JournalEntries.SABOTAGE) && entry(p, JournalEntries.THE_BUYER), msg("their entries are written as they happen"));

			arcSteps(p, ChapterFourRules.STEP_POWER);
			action(p, "c4:not_now");
			helper.assertTrue(c4(p).stage() == ChapterFourStage.CHALLENGED,
					msg("the arc's end counts the clues already held: all three and the level go straight on, got " + c4(p).stage()));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void huntedOnlyAfterWorldArc(GameTestHelper helper) {
		helper.assertTrue(!ChapterFourRules.huntRunning(pureAt(Path.JEDI, ChapterFourStage.REPAIRS).chapter4)
				&& ChapterFourRules.huntRunning(pureAt(Path.JEDI, ChapterFourStage.FIRST_FLIGHT).chapter4), msg("hunt events run from FIRST_FLIGHT on"));
		ServerPlayer p = at(helper, Path.SITH, ChapterFourStage.WORLD_ARC);

		try {
			level(p, 20);
			ChapterFour.addClue(p, 1);
			ChapterFour.addClue(p, 2);
			ChapterFour.addClue(p, 3);
			QuestHooks.onLevelUp(p, 20);
			helper.assertTrue(c4(p).stage() == ChapterFourStage.WORLD_ARC, msg("clues and level never skip the arc, got " + c4(p).stage()));
			arcSteps(p, ChapterFourRules.STEP_FIRST | ChapterFourRules.STEP_SECOND);
			QuestHooks.onLevelUp(p, 20);
			helper.assertTrue(c4(p).stage() == ChapterFourStage.WORLD_ARC, msg("both missions without the power and an expedition: still the arc"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void sabotageOnceWhenShipLandsHome(GameTestHelper helper) {
		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.WORLD_ARC);
		MinecraftServer server = helper.getLevel().getServer();

		try {
			GlobalPos home = GlobalPos.of(Level.OVERWORLD, helper.absolutePos(new BlockPos(2, 1, 2)));
			ShipRegistry.put(server, ShipRecord.hulk(p.getUUID(), UUID.randomUUID(), home, 0f, "w2_test").withState(ShipState.GROUNDED).withHull(60));
			QuestHooks.onShipStateChanged(p, ShipState.LANDING, ShipState.GROUNDED);
			helper.assertTrue(!c4(p).hunt().sabotageDone(), msg("no sabotage before the ship has been to a planet"));

			flag(p, ChapterFourRules.LANDED_PREFIX + Planets.Planet.SELVAREN.id());
			QuestHooks.onShipStateChanged(p, ShipState.READY, ShipState.LAUNCHING);
			helper.assertTrue(!c4(p).hunt().sabotageDone(), msg("only a landing settles the ship"));
			QuestHooks.onShipStateChanged(p, ShipState.LANDING, ShipState.GROUNDED);
			ShipRecord ship = ShipRegistry.of(server, p.getUUID()).orElseThrow();
			helper.assertTrue(c4(p).hunt().sabotageDone() && ChapterFour.hasClue(p, 1), msg("the first landing home blows the charge: clue 1"));
			helper.assertTrue(ship.hull() <= 10 && ship.state() == ShipState.DISABLED, msg("hull 10 and DISABLED, got " + ship.hull() + " " + ship.state()));

			ShipRegistry.put(server, ship.withHull(80).withState(ShipState.READY));
			QuestHooks.onShipStateChanged(p, ShipState.LANDING, ShipState.READY);
			helper.assertTrue(ShipRegistry.of(server, p.getUUID()).orElseThrow().hull() == 80, msg("the sabotage happens once"));
		} finally {
			removeShip(server, p.getUUID());
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void ambushRetryAfterFleeing(GameTestHelper helper) {
		ChapterFourBalance b = DEFAULTS;
		StoryData data = pureAt(Path.JEDI, ChapterFourStage.HUNTED);
		helper.assertTrue(!ChapterFourRules.ambushOneDue(data, DAY), msg("the first ambush waits for the sabotage"));
		data.chapter4 = data.chapter4.withHunt(data.chapter4.hunt().withSabotage());
		helper.assertTrue(ChapterFourRules.ambushOneDue(data, DAY), msg("after the sabotage the first ambush is due"));

		// Fleeing sets the retry day a day later (TheHunt): until then nothing comes.
		data.counters.put(ChapterFourRules.AMBUSH_RETRY_DAY, (int) (DAY + b.ambushRetryDays));
		helper.assertTrue(!ChapterFourRules.ambushOneDue(data, DAY), msg("a fled ambush does not come again the same day"));
		helper.assertTrue(ChapterFourRules.ambushOneDue(data, DAY + b.ambushRetryDays), msg("it comes again on the retry day"));

		data.chapter4 = data.chapter4.withHunt(data.chapter4.hunt().withAmbush(DAY, 1));
		data.flags.add(ChapterFourRules.AMBUSH_WORLD_PREFIX + "selvaren");
		data.counters.remove(ChapterFourRules.AMBUSH_RETRY_DAY);
		helper.assertTrue(!ChapterFourRules.ambushTwoDue(data, DAY + b.ambushGapDays - 1, "home", b), msg("the second ambush waits its days"));
		helper.assertTrue(!ChapterFourRules.ambushTwoDue(data, DAY + b.ambushGapDays, "selvaren", b), msg("and comes on another world than the first"));
		helper.assertTrue(ChapterFourRules.ambushTwoDue(data, DAY + b.ambushGapDays, "home", b), msg("home, two days later: due"));
		data.counters.put(ChapterFourRules.AMBUSH_RETRY_DAY, (int) (DAY + b.ambushGapDays + 1));
		helper.assertTrue(!ChapterFourRules.ambushTwoDue(data, DAY + b.ambushGapDays, "home", b), msg("a fled second ambush waits for its retry day too"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void clue3CountsOnDroidDeath(GameTestHelper helper) {
		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.HUNTED);

		try {
			ChapterFour.addClue(p, 3);
			helper.assertTrue(ChapterFour.hasClue(p, 3) && ChapterFour.clues(p) == 1, msg("the droid's death is clue 3"));
			helper.assertTrue(count(p, VendorItems.SEALED_CONTRACT) == 1 && entry(p, JournalEntries.THE_BUYER) && codex(p, JournalEntries.CODEX_THE_BUYER),
					msg("the sealed contract, entry and codex the_buyer"));
			helper.assertTrue(c4(p).hunt().ambushes() >= 2, msg("the second ambush counts as beaten"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void cluesCountOnce(GameTestHelper helper) {
		ChapterFourBalance b = ChapterFourService.balance();
		ServerPlayer p = at(helper, Path.SITH, ChapterFourStage.HUNTED);

		try {
			long xp = totalXp(p);
			ChapterFour.addClue(p, 1);
			ChapterFour.addClue(p, 1);
			ChapterFour.addClue(p, 0);
			ChapterFour.addClue(p, 4);
			helper.assertTrue(ChapterFour.clues(p) == 1 && totalXp(p) - xp == b.sabotageXp, msg("a clue counts and pays once; unknown clues are ignored"));
			ChapterFour.addClue(p, 2);
			ChapterFour.addClue(p, 2);
			helper.assertTrue(ChapterFour.clues(p) == 2 && totalXp(p) - xp == (long) b.sabotageXp + b.ambushXp, msg("the second clue too"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void challengedNeedsArcCluesAndLevel14(GameTestHelper helper) {
		ChapterFourBalance b = DEFAULTS;
		StoryData data = pureAt(Path.JEDI, ChapterFourStage.HUNTED);
		data.chapter4 = data.chapter4.withHunt(data.chapter4.hunt().withClues(0b011));
		helper.assertTrue(!ChapterFourRules.challengeReady(data.chapter4, b.duelLevel, b), msg("two clues are not enough"));
		data.chapter4 = data.chapter4.withHunt(data.chapter4.hunt().withClues(0b111));
		helper.assertTrue(!ChapterFourRules.challengeReady(data.chapter4, b.duelLevel - 1, b), msg("three clues below the duel level wait"));
		helper.assertTrue(ChapterFourRules.challengeReady(data.chapter4, b.duelLevel, b), msg("three clues at level " + b.duelLevel + ": ready"));
		data.chapter4 = data.chapter4.withStage(ChapterFourStage.WORLD_ARC, 0L);
		helper.assertTrue(!ChapterFourRules.challengeReady(data.chapter4, 99, b), msg("never before the arc is over"));
		helper.assertTrue("quest.oldrepublic.c4.objective.not_ready".equals(ChapterFourRules.objective(withStage(data, ChapterFourStage.HUNTED), 0,
				b.duelLevel - 1, b).key()), msg("the journal says why it waits"));

		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.HUNTED);
		int duelLevel = ChapterFourService.balance().duelLevel;

		try {
			level(p, duelLevel - 1);
			ChapterFour.addClue(p, 1);
			ChapterFour.addClue(p, 2);
			ChapterFour.addClue(p, 3);
			helper.assertTrue(c4(p).stage() == ChapterFourStage.HUNTED, msg("below the level the hunt waits"));
			level(p, duelLevel);
			QuestHooks.onLevelUp(p, duelLevel);
			helper.assertTrue(c4(p).stage() == ChapterFourStage.CHALLENGED, msg("the level-up opens the challenge, got " + c4(p).stage()));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	private static StoryData withStage(StoryData data, ChapterFourStage stage) {
		StoryData copy = data.copy();
		copy.chapter4 = copy.chapter4.withStage(stage, 0L);
		return copy;
	}

	@GameTest(maxTicks = 20)
	public void oneTimeItemsReissuedOncePerDay(GameTestHelper helper) {
		StoryData data = new StoryData();
		helper.assertTrue(ChapterFourRules.reissueDue(data, "motivator", DAY), msg("never issued: due"));
		data.counters.put(ChapterFourRules.REISSUE_PREFIX + "motivator", (int) DAY);
		helper.assertTrue(!ChapterFourRules.reissueDue(data, "motivator", DAY) && ChapterFourRules.reissueDue(data, "motivator", DAY + 1),
				msg("once per day"));
		helper.assertTrue(ChapterFourRules.reissueDue(data, "relic", DAY), msg("each item keeps its own day"));

		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.REPAIRS);

		try {
			flag(p, ChapterFourRules.MOTIVATOR_ROUTE_PREFIX + "smuggler");
			helper.assertTrue(condition(p, "c4:smuggler", "") == null, msg("a lost smuggler's motivator may be re-issued"));
			action(p, "c4:reissue");
			helper.assertTrue(count(p, M4Items.HYPERDRIVE_MOTIVATOR) == 1, msg("the smuggler hands over a second one"));
			helper.assertTrue(condition(p, "c4:smuggler", "") != null, msg("not while carrying one"));
			removeAll(p, M4Items.HYPERDRIVE_MOTIVATOR);
			helper.assertTrue(condition(p, "c4:smuggler", "") != null, msg("lost again the same day: no third"));

			int today = (int) Clocks.day(helper.getLevel().getServer());
			StoryData yesterday = Story.get(p);
			yesterday.counters.put(ChapterFourRules.REISSUE_PREFIX + "motivator", today - 1);
			Story.commit(p, yesterday);
			helper.assertTrue(condition(p, "c4:smuggler", "") == null, msg("the next day it is re-issued again"));
			QuestHooks.onShipPartFitted(p, 2);
			helper.assertTrue(condition(p, "c4:smuggler", "") != null, msg("once the motivator socket is fitted nothing more is re-issued"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the duel and the report

	@GameTest(maxTicks = 20)
	public void kaastChoiceRewardsOncePerFlag(GameTestHelper helper) {
		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.CHALLENGED);

		try {
			KaastDuel.enterRing(p, true);
			helper.assertTrue(c4(p).stage() == ChapterFourStage.DUEL && condition(p, "c4:yield", "") == null, msg("the yield opens the choice"));
			int sp = Progression.get(p).skillPoints;
			action(p, "kaast:resolve", "spare");
			helper.assertTrue(c4(p).stage() == ChapterFourStage.AFTERMATH && c4(p).duel().kaastOutcome() == KaastOutcome.SPARED,
					msg("the choice is recorded and the chapter moves to AFTERMATH"));
			helper.assertTrue(count(p, VendorItems.KAAST_PATTERN) == 1 && entry(p, JournalEntries.KAAST_SPARED), msg("Spare: the pattern and the entry"));
			helper.assertTrue(story(p).hasFlag(ChapterFourRules.KAAST_PAID) && story(p).hasFlag(ChapterFourRules.STASH_OPENED)
					&& Progression.get(p).skillPoints > sp, msg("the first clear's rewards and the stash"));
			int alloy = count(p, M4Items.ALLOY_PLATE);

			action(p, "kaast:resolve", "end");
			helper.assertTrue(c4(p).duel().kaastOutcome() == KaastOutcome.SPARED, msg("the choice is made once"));
			helper.assertTrue(!KaastDuel.applyChoice(p, KaastOutcome.SPARED) && count(p, VendorItems.KAAST_PATTERN) == 1,
					msg("the same ending's rewards are given once per flag"));
			QuestHooks.onBossDefeated(p, "kaast", com.oldrepublic.quest.BossOutcome.SPARED, p.getUUID(), true);
			helper.assertTrue(count(p, M4Items.ALLOY_PLATE) == alloy && Progression.get(p).skillPoints > sp, msg("the fight's own hook pays nothing twice"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void reportUnlocksAllDestinations(GameTestHelper helper) {
		ChapterFourBalance b = ChapterFourService.balance();
		ServerPlayer p = at(helper, Path.SITH, ChapterFourStage.DUEL);

		try {
			action(p, "c4:report");
			helper.assertTrue(c4(p).stage() == ChapterFourStage.DUEL, msg("no report before AFTERMATH"));
			ChapterFourService.enterStage(p, ChapterFourStage.AFTERMATH);
			helper.assertTrue(condition(p, "c4:report_ready", "") == null, msg("the report is open in AFTERMATH"));
			long xp = totalXp(p);
			action(p, "c4:report");
			helper.assertTrue(c4(p).stage() == ChapterFourStage.COMPLETE && entry(p, JournalEntries.REPORT_C4), msg("the report completes the chapter"));
			helper.assertTrue(totalXp(p) - xp == b.reportXp, msg("and pays " + b.reportXp + " XP"));

			for (Destination d : Destination.values()) {
				// The Milestone 4 four; Saltgate and the holdfast wait for Chapter Five (MILESTONE_5.md 6.1).
				helper.assertTrue(TravelService.isUnlocked(p, d) == (d.milestone() <= 4),
						msg(d.milestone() <= 4 ? "every Milestone 4 destination unlocks: " + d : "a Milestone 5 destination stays locked: " + d));
			}

			action(p, "c4:report");
			helper.assertTrue(totalXp(p) - xp == b.reportXp, msg("the report is made once"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void backPayOnceCapped300(GameTestHelper helper) {
		VendorBalance vb = VENDOR_DEFAULTS;
		helper.assertTrue(ChapterFourRules.backPay(2, vb.backPayPerMilestone, vb.backPayMax) == 2 * vb.backPayPerMilestone
				&& ChapterFourRules.backPay(5, vb.backPayPerMilestone, vb.backPayMax) == vb.backPayMax && VendorRules.backPay(9, vb) == vb.backPayMax
				&& VendorRules.backPay(0, vb) == 0, msg("100 per milestone, at most 300"));

		ServerPlayer veteran = M3TestPlayers.listed(helper);
		ServerPlayer fresh = M3TestPlayers.listed(helper, new BlockPos(3, 1, 1));

		try {
			StoryData data = Story.get(veteran);
			data.flags.add(ChapterThreeRules.COLLECTOR_SP);
			data.flags.add(ChapterThreeRules.RANK3_DONE);
			Story.commit(veteran, data);
			HolocronData holocrons = HolocronService.data(veteran);
			holocrons.attuned.add("jedi_morrane");
			holocrons.attuned.add("sith_zarvos");
			HolocronService.commit(veteran, holocrons);

			int before = credits(veteran);
			int paid = ChapterFourService.settleBackPay(veteran);
			VendorBalance live = VendorFeature.balance();
			int expected = Math.min(live.backPayMax, live.backPayPerMilestone * (2 + HolocronService.attunedCount(veteran, null)));
			helper.assertTrue(paid == expected && credits(veteran) - before == paid, msg("the back pay is " + expected + ", got " + paid));
			helper.assertTrue(ChapterFourService.settleBackPay(veteran) == 0 && credits(veteran) - before == paid && c4(veteran).backPay(),
					msg("it is paid once"));

			helper.assertTrue(ChapterFourService.settleBackPay(fresh) == 0 && c4(fresh).backPay(), msg("nothing owed: settled with nothing paid"));
		} finally {
			M3TestPlayers.remove(veteran);
			M3TestPlayers.remove(fresh);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void peacefulCluesByJournal(GameTestHelper helper) {
		ServerPlayer p = at(helper, Path.JEDI, ChapterFourStage.HUNTED);

		try {
			TheHunt.ambush(p, 1, true);
			helper.assertTrue(ChapterFour.hasClue(p, 2) && !TheHunt.ambushRunning(p), msg("on Peaceful no hunter comes: clue 2 arrives by journal"));
			TheHunt.ambush(p, 2, true);
			helper.assertTrue(ChapterFour.hasClue(p, 3) && count(p, VendorItems.SEALED_CONTRACT) == 1, msg("and clue 3 with its contract"));

			ChapterFourService.enterStage(p, ChapterFourStage.CHALLENGED);
			KaastDuel.enterRing(p, true);
			helper.assertTrue(c4(p).stage() == ChapterFourStage.DUEL && condition(p, "c4:yield", "") == null,
					msg("the duel becomes a talk: Kaast yields at once and the choice opens"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- vendors and credits

	@GameTest(maxTicks = 20)
	public void vendorTierGatesAndRefusals(GameTestHelper helper) {
		VendorBalance b = VENDOR_DEFAULTS;
		helper.assertTrue(VendorRules.refuses(Faction.JEDI_ORDER, ReputationTier.NEUTRAL, Path.SITH)
				&& VendorRules.refuses(Faction.SITH_EMPIRE, ReputationTier.HONOURED, Path.JEDI), msg("the other side's hub refuses"));
		helper.assertTrue(!VendorRules.refuses(Faction.JEDI_ORDER, ReputationTier.NEUTRAL, Path.INDEPENDENT)
				&& VendorRules.refuses(Faction.JEDI_ORDER, ReputationTier.DISTRUSTED, Path.INDEPENDENT), msg("below Neutral refuses anyone"));
		helper.assertTrue(!VendorRules.refuses(null, ReputationTier.HOSTILE, Path.SITH), msg("trainers and spacers serve everyone"));
		helper.assertTrue(VendorRules.price(100, true, b) == 90 && VendorRules.price(100, false, b) == 100 && VendorRules.sellPrice(100, b) == 20,
				msg("Honoured pays 90%; selling returns 20%"));

		VendorEntry plate = new VendorEntry(new ItemStack(Items.IRON_CHESTPLATE), 40, Optional.empty(), ReputationTier.TRUSTED.ordinal(), Optional.empty(), 0, 0);
		String lock = VendorRules.lockReason(plate, Faction.JEDI_ORDER, ReputationTier.NEUTRAL, 0, 0);
		helper.assertTrue(lock != null && lock.startsWith(VendorRules.REASON + "tier|"), msg("a Trusted line is locked at Neutral, got " + lock));
		helper.assertTrue(VendorRules.lockReason(plate, Faction.JEDI_ORDER, ReputationTier.TRUSTED, 0, 0) == null, msg("and open at Trusted"));

		VendorStock stock = new VendorStock("w2_test_quartermaster", Optional.of(Faction.JEDI_ORDER), List.of(plate), Map.of());
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			Identity.setReputation(p, Faction.JEDI_ORDER, 0);
			Credits.give(p, 200);
			int before = credits(p);
			helper.assertTrue(Vendors.buy(p, stock, 0, 1) == 0 && credits(p) == before && count(p, Items.IRON_CHESTPLATE) == 0,
					msg("a locked line sells nothing and takes nothing"));
			Identity.setReputation(p, Faction.JEDI_ORDER, 150);
			helper.assertTrue(Vendors.buy(p, stock, 0, 1) == 1 && before - credits(p) == 40 && count(p, Items.IRON_CHESTPLATE) == 1,
					msg("at Trusted it is bought for its price"));
			Identity.setReputation(p, Faction.JEDI_ORDER, 600);
			before = credits(p);
			helper.assertTrue(Vendors.buy(p, stock, 0, 1) == 1 && before - credits(p) == VendorRules.price(40, true, VendorFeature.balance()),
					msg("Honoured pays the discounted price"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void vendorDailyLimitPerPlayer(GameTestHelper helper) {
		VendorBalance b = VENDOR_DEFAULTS;
		VendorEntry pattern = new VendorEntry(new ItemStack(Items.PAPER), 10, Optional.empty(), 0, Optional.empty(), 0, 2);
		helper.assertTrue(VendorRules.dailyLeft(pattern, 0) == 2 && VendorRules.dailyLeft(pattern, 2) == 0, msg("two a day"));
		helper.assertTrue(VendorRules.allowedCount(pattern, 5, 0, b) == 2 && VendorRules.allowedCount(pattern, 5, 1, b) == 1, msg("a purchase stops at the limit"));
		helper.assertTrue(VendorRules.lockReason(pattern, null, ReputationTier.NEUTRAL, 0, 2) != null, msg("sold out for today"));

		VendorStock stock = new VendorStock("w2_test_trainer", Optional.empty(), List.of(pattern), Map.of());
		ServerPlayer first = M3TestPlayers.listed(helper);
		ServerPlayer second = M3TestPlayers.listed(helper, new BlockPos(3, 1, 1));

		try {
			Credits.give(first, 100);
			Credits.give(second, 100);
			helper.assertTrue(Vendors.buy(first, stock, 0, 5) == 2, msg("the first player buys the day's two"));
			helper.assertTrue(Vendors.buy(first, stock, 0, 1) == 0 && count(first, Items.PAPER) == 2, msg("and no more today"));
			helper.assertTrue(Vendors.boughtToday(first, stock.id(), 0) == 2, msg("the day's purchases are counted on the player"));
			helper.assertTrue(Vendors.buy(second, stock, 0, 1) == 1, msg("another player has their own limit"));
		} finally {
			M3TestPlayers.remove(first);
			M3TestPlayers.remove(second);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void commissionChargesFeeAndStandardQuality(GameTestHelper helper) {
		Schematic chosen = null;

		for (Schematic s : Schematics.all()) {
			boolean plain = s.discipline().isPresent() && s.quality() && !Crafting.isMasterworkOnly(s) && !s.inputs().isEmpty()
					&& s.inputs().stream().allMatch(i -> i.what().left().isPresent() && !i.what().left().get().equals(s.output().getItem())
							&& !i.what().left().get().equals(M4Items.CREDIT_CHIT) && !i.what().left().get().equals(M4Items.CREDIT_BUNDLE));

			if (plain) {
				chosen = s;
				break;
			}
		}

		helper.assertTrue(chosen != null, msg("a quality discipline schematic with item inputs must be loaded (W5's data)"));
		Schematic s = chosen;
		int fee = CraftingRules.commissionFee(s.level(), CraftingBalance.get());
		helper.assertTrue(fee == CraftingBalance.get().commissionPerLevel * Math.max(1, s.level()), msg("the fee is per schematic level"));
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			Crafting.teach(p, s.id());

			for (Schematic.Input input : s.inputs()) {
				p.getInventory().placeItemBackInInventory(new ItemStack(input.what().left().orElseThrow(), input.count()));
			}

			helper.assertTrue(BenchService.commission(p, s.id(), null) == BenchResult.NO_CREDITS, msg("no credits: no commission"));
			Credits.give(p, fee + 7);
			int before = credits(p);
			int had = count(p, s.output().getItem());
			helper.assertTrue(BenchService.commission(p, s.id(), s.discipline().get()) == BenchResult.COMMISSIONED, msg("the trainer makes it"));
			helper.assertTrue(before - credits(p) == fee, msg("the fee " + fee + " is charged, got " + (before - credits(p))));
			helper.assertTrue(count(p, s.output().getItem()) - had == s.output().getCount(), msg("the item is handed over"));
			Inventory inventory = p.getInventory();
			Quality quality = null;

			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (inventory.getItem(i).is(s.output().getItem())) {
					quality = Crafting.quality(inventory.getItem(i));
				}
			}

			helper.assertTrue(quality == Quality.STANDARD, msg("a commission is always Standard, got " + quality));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void contractCreditsOnlyAtFullPay(GameTestHelper helper) {
		VendorBalance vb = VENDOR_DEFAULTS;
		helper.assertTrue(VendorRules.contractCredits(0, vb) == vb.contractCreditsBase
				&& VendorRules.contractCredits(10, vb) == vb.contractCreditsBase + 10 * vb.contractCreditsPerLevel, msg("25 + 5 x level"));

		MissionBalance b = MissionFeature.balance();
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			for (int i = 0; i <= b.fullPayPerDay; i++) {
				boolean full = i < b.fullPayPerDay;
				int level = Progression.get(p).level;
				int before = credits(p);
				Active active = new Active("clear_camp", "", Kind.CONTRACT, Faction.FRONTIER.id(), (int) DAY, 0x5EEDL, TERMINAL);
				active.stage = Stage.ACTIVE;
				MissionData d = MissionService.get(p);
				d.active = active;
				MissionService.commit(p, d);
				MissionService.complete(p, null, (int) DAY);
				int expected = full ? VendorRules.contractCredits(level, VendorFeature.balance()) : 0;
				// The Frontier loot may hold no chits; any credits beyond the contract's would come from it, so only "none past the cap" is exact.
				helper.assertTrue(full ? credits(p) - before >= expected : credits(p) == before,
						msg("contract " + (i + 1) + (full ? " pays at least " + expected + " credits" : " pays no credits past the cap") + ", got " + (credits(p) - before)));
			}
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void requisitionRefusedAtWary(GameTestHelper helper) {
		helper.assertTrue(VendorRules.refuses(Faction.REPUBLIC, ReputationTier.DISTRUSTED, Path.JEDI), msg("Wary with the Republic: refused"));
		helper.assertTrue(!VendorRules.refuses(Faction.REPUBLIC, ReputationTier.NEUTRAL, Path.SITH), msg("the requisition serves any path above Wary"));
		VendorStock republic = VendorStocks.get("requisition_republic");
		VendorStock imperial = VendorStocks.get("requisition_imperial");
		helper.assertTrue(republic != null && republic.faction().orElse(null) == Faction.REPUBLIC, msg("the Republic requisition is the Republic's"));
		helper.assertTrue(imperial != null && imperial.faction().orElse(null) == Faction.SITH_EMPIRE, msg("the Imperial requisition is the Empire's"));

		ServerPlayer p = M3TestPlayers.listed(helper);
		Mob officer = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(2, 1, 1));

		try {
			Identity.setReputation(p, Faction.REPUBLIC, -200);
			Vendors.open(p, officer, Identifier.fromNamespaceAndPath("oldrepublic", "requisition_republic"));
			helper.assertTrue(Vendors.openStock(p) == null, msg("at Wary the requisition does not open"));
			Identity.setReputation(p, Faction.REPUBLIC, 0);
			Vendors.open(p, officer, Identifier.fromNamespaceAndPath("oldrepublic", "requisition_republic"));
			helper.assertTrue("requisition_republic".equals(Vendors.openStock(p)), msg("at Neutral it opens, got " + Vendors.openStock(p)));
		} finally {
			officer.discard();
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void planetContractsSeededPerDimension(GameTestHelper helper) {
		UUID player = new UUID(0x1234_5678_9ABCL, 0x0FED_CBA9_8765L);
		long home = MissionRules.boardSeed((int) DAY, player, TERMINAL, Level.OVERWORLD);
		long selvaren = MissionRules.boardSeed((int) DAY, player, TERMINAL, Planets.SELVAREN);
		long varkhast = MissionRules.boardSeed((int) DAY, player, TERMINAL, Planets.VARKHAST);
		helper.assertTrue(home == MissionRules.boardSeed((int) DAY, player, TERMINAL), msg("the overworld keeps the M3 seed"));
		helper.assertTrue(home != selvaren && selvaren != varkhast, msg("each planet's board has its own seed"));

		MissionData d = new MissionData();
		helper.assertTrue(MissionRules.useOffer(d, TERMINAL, Planets.SELVAREN, 0, 99), msg("an offer is used on Selvaren"));
		helper.assertTrue(MissionRules.offerUsed(d, TERMINAL, Planets.SELVAREN, 0) && !MissionRules.offerUsed(d, TERMINAL, Planets.VARKHAST, 0)
				&& !MissionRules.offerUsed(d, TERMINAL, 0), msg("a terminal at the same coordinates elsewhere is another board"));

		for (Planets.Planet planet : List.of(Planets.Planet.SELVAREN, Planets.Planet.VARKHAST)) {
			helper.assertTrue(PlanetContracts.issuer(planet) == (planet == Planets.Planet.SELVAREN ? Faction.JEDI_ORDER : Faction.SITH_EMPIRE),
					msg(planet.id() + "'s board is issued by its hub's faction"));
			List<String> types = PlanetContracts.types(planet);
			helper.assertTrue(types.size() == PlanetContracts.TEMPLATES.size(), msg(planet.id() + " offers six contracts"));

			for (String type : types) {
				MissionDefinition def = definition(helper, type);
				helper.assertTrue(def.isPlanetContract() && planet.id().equals(def.planet()) && def.kind() == Kind.CONTRACT,
						msg(type + " is a contract of " + planet.id()));
			}

			List<MissionRules.Offer> offers = MissionRules.offers((int) DAY, player, TERMINAL, planet.level(), types, t -> true, i -> false,
					MissionFeature.balance().offersPerBoard);
			helper.assertTrue(offers.equals(MissionRules.offers((int) DAY, player, TERMINAL, planet.level(), types, t -> true, i -> false,
					MissionFeature.balance().offersPerBoard)), msg("the same day and board roll the same offers"));
			helper.assertTrue(offers.stream().allMatch(o -> types.contains(o.type())), msg("every offer is one of the planet's own"));
		}

		helper.assertTrue(PlanetContracts.issuer(Planets.Planet.HOME) == null, msg("home has no planet board"));
		helper.succeed();
	}
}
