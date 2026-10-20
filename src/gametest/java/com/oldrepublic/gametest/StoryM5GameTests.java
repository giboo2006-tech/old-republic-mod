package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.boss.ulvaine.UlvaineBalance;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.enemy.trooper.TrooperAttitudeRule;
import com.oldrepublic.equipment.EquipmentItems;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.FactionM5Balance;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityBalance;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.IdentityRules;
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
import com.oldrepublic.mission.SpaceContracts;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveBalance;
import com.oldrepublic.quest.story.ChapterFiveRules;
import com.oldrepublic.quest.story.ChapterFiveService;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.FactionStanding;
import com.oldrepublic.quest.story.KaastOutcome;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.turning.RankFour;
import com.oldrepublic.quest.turning.RankFourChecklistPayload;
import com.oldrepublic.quest.turning.TurningRules;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.space.SpaceEncounters;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.FlightBalance;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.vendor.VendorEntry;
import com.oldrepublic.vendor.VendorItems;
import com.oldrepublic.vendor.VendorRules;
import com.oldrepublic.vendor.VendorStock;
import com.oldrepublic.vendor.VendorStocks;
import com.oldrepublic.vendor.Vendors;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Chapter Five, "The Long Hymn", and the two Milestone 5 factions (spec MILESTONE_5.md 19.1, W4): the
 * stage machine and M4 saves, the call at a safe moment, the helm and its lesson, the three ways at
 * Saltgate, the counting-house and the factor, the cannons and the blockade, the verse stones, the
 * choice over Ulvaine and the report, the Vessba kajidic's and Clan Hask's standing, vendors,
 * contracts, bounties and deeds, and the Peaceful rules.
 *
 * <p>Test rules ({@code WORKER_M5.md} 4): every test declares {@code maxTicks} (20, far under 400);
 * players are listed mock players removed at the end; the default empty structure; days are explicit
 * where a rule takes one, and a player test that reads the world's day compares with {@link Clocks#day}
 * on the same tick. Everything runs synchronously in the first tick, before the story's own tick
 * touches the players. No server test touches a real planet or orbit (they do not exist on the
 * game-test server): arrivals are the hooks' calls, the blockade battle (W2's, which spawns its ships)
 * is never started, and every cross-level rule is tested as a pure rule. The ship registry is
 * snapshotted and committed back; the blockade's per-battle mark is cleared in a {@code finally}.
 *
 * <p>Every player is set up with Chapter Four complete (Kaast's fate as the test needs it, a camp far
 * away, the back pay settled) and both mentors' trust at 50, so trust moves both ways.
 */
public class StoryM5GameTests {
	private static final ChapterFiveBalance DEFAULTS = new ChapterFiveBalance();
	private static final FactionM5Balance FACTION_DEFAULTS = new FactionM5Balance();
	/** A Chapter Four camp far from every test, so nothing of Chapter Four is placed in the test level. */
	private static final GlobalPos FAR_CAMP = GlobalPos.of(Level.OVERWORLD, new BlockPos(29_000_000, 80, 29_000_000));
	/** The explicit day of the pure daily rules and of the contract pay. */
	private static final long DAY = 40L;
	private static final BlockPos TERMINAL = new BlockPos(100, 64, 100);
	private static final String MIND_TRICK = "force_mind_trick";
	private static final String FEAR = "force_fear";
	private static final String FENCE = "cartel_fence";
	private static final String ARMOURER = "hask_armourer";
	/** The shipwright's paid options ask for this (vendor_saltgate_shipwright). */
	private static final String SHIPWRIGHT_NEUTRAL = "{\"faction\":\"vessba_kajidic\",\"value\":\"neutral\"}";
	private static final int MENTOR_TRUST = 50;

	// ---------------------------------------------------------------- helpers

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("oldrepublic", path);
	}

	/** Where the {@code i}th player of a test stands: a three by two grid on the floor. */
	private static BlockPos spot(int i) {
		return new BlockPos(1 + 2 * (i % 3), 1, 1 + 2 * (i / 3));
	}

	/** Chapter Four's duel record with Kaast's fate ({@code null}: an old save that never chose). */
	private static StoryData.Chapter4.Duel duel(@Nullable KaastOutcome kaast) {
		StoryData.Chapter4.Duel duel = StoryData.Chapter4.Duel.NONE.withCamp(FAR_CAMP);
		// 2 is the duel's resolved state (KaastDuel.BOSS_RESOLVED).
		return kaast == null ? duel : duel.withBoss(2, kaast.ordinal());
	}

	/**
	 * A listed player on {@code path} whose Chapters Three and Four are complete, at Chapter Five
	 * {@code stage} (set directly: no stage entry logic runs), with both mentors' trust at 50.
	 */
	private static ServerPlayer at(GameTestHelper helper, BlockPos relative, Path path, ChapterFiveStage stage, @Nullable KaastOutcome kaast) {
		ServerPlayer p = M3TestPlayers.listed(helper, relative);
		IdentityData identity = Identity.get(p);
		identity.path = path;
		identity.trust.put("jedi", MENTOR_TRUST);
		identity.trust.put("sith", MENTOR_TRUST);
		Identity.commit(p, identity);
		StoryData data = Story.get(p);
		data.path = path.id();
		data.pathStage = (path == Path.JEDI || path == Path.SITH) ? PathStage.COMPLETE.ordinal() : data.pathStage;
		data.chapter3 = ChapterThreeRules.withStage(data.chapter3, ChapterThreeStage.COMPLETE);
		data.flags.add(ChapterFourRules.HULK_PLACED);
		data.flags.add(ChapterFourRules.CHALLENGE_PLAYED);
		data.chapter4 = data.chapter4.withStage(ChapterFourStage.COMPLETE, 0L).withBackPay().withDuel(duel(kaast));
		data.chapter5 = data.chapter5.withStage(stage, stage == ChapterFiveStage.NOT_STARTED ? -1L : 0L);
		Story.commit(p, data);
		return p;
	}

	/** A Jedi at THE_FACTOR whose kajidic way is done and the counting-house cleared: Dask waits. */
	private static ServerPlayer factor(GameTestHelper helper, BlockPos relative) {
		ServerPlayer p = at(helper, relative, Path.JEDI, ChapterFiveStage.THE_FACTOR, KaastOutcome.SPARED);
		StoryData data = Story.get(p);
		data.chapter5 = data.chapter5.withWay(ChapterFive.Way.KAJIDIC);
		data.flags.add(ChapterFiveRules.COUNTING_HOUSE);
		Story.commit(p, data);
		return p;
	}

	/** Pure story data on the Jedi path at Chapter Five {@code stage}, Chapter Four complete. */
	private static StoryData pure(ChapterFiveStage stage) {
		StoryData data = new StoryData();
		data.path = Path.JEDI.id();
		data.chapter4 = data.chapter4.withStage(ChapterFourStage.COMPLETE, 0L);
		data.chapter5 = data.chapter5.withStage(stage, 0L);
		return data;
	}

	/** Pure story data at THE_FACTOR with {@code ways} complete in this order (the first is the chosen way). */
	private static StoryData withWays(ChapterFive.Way... ways) {
		StoryData data = pure(ChapterFiveStage.THE_FACTOR);

		for (ChapterFive.Way way : ways) {
			data.chapter5 = data.chapter5.withWay(way);
		}

		return data;
	}

	private static StoryData story(ServerPlayer p) {
		return Story.get(p);
	}

	private static StoryData.Chapter5 c5(ServerPlayer p) {
		return Story.get(p).chapter5;
	}

	private static void flag(ServerPlayer p, String flag) {
		StoryData data = Story.get(p);
		data.flags.add(flag);
		Story.commit(p, data);
	}

	/** Sets the stage directly, without its entry logic. */
	private static void setStage(ServerPlayer p, ChapterFiveStage stage) {
		StoryData data = Story.get(p);
		data.chapter5 = data.chapter5.withStage(stage, 0L);
		Story.commit(p, data);
	}

	private static void failures(ServerPlayer p, int failures) {
		StoryData data = Story.get(p);
		data.chapter5 = data.chapter5.withBlockadeFailures(failures);
		Story.commit(p, data);
	}

	/** The fight ended with {@code outcome} chosen (Ulvaine resolved). */
	private static void resolved(ServerPlayer p, BossOutcome outcome) {
		StoryData data = Story.get(p);
		data.chapter5 = data.chapter5.withBoss(ChapterFiveRules.BOSS_RESOLVED, outcome.ordinal());
		Story.commit(p, data);
	}

	/**
	 * Forgets the blockade battle's counted failure (the per-battle mark the story keeps in memory):
	 * outside BLOCKADE the broken-blockade hook only clears it and pays nothing.
	 */
	private static void endBattle(ServerPlayer p) {
		setStage(p, ChapterFiveStage.TYRVANN);
		ChapterFiveService.onBlockadeBroken(p);
	}

	/** No helm and neither Milestone 5 destination unlocked. */
	private static void lockTravel(ServerPlayer p) {
		TravelAttachments.set(p, TravelAttachments.get(p).withHelm(false).withUnlocked(Destination.SALTGATE, false).withUnlocked(Destination.HOLDFAST, false));
	}

	/** Teaches a power directly (as the test command does) with a full pool. */
	private static void learn(ServerPlayer p, String ability) {
		ForceData data = Progression.get(p);
		data.abilities.add(ability);
		data.energy = data.maxEnergy;
		Progression.commit(p, data);
	}

	private static void level(ServerPlayer p, int level) {
		ForceData force = Progression.get(p);
		force.level = level;
		force.xp = 0;
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

	/** Skill points gained since {@code sp} at {@code level}, without the ones the levels gained since paid. */
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

	/** Kyber crystals attuned to the player. */
	private static int attunedCrystals(ServerPlayer p) {
		int n = 0;

		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			ModComponents.CrystalAttunement attunement = stack.get(ModComponents.CRYSTAL_ATTUNEMENT);

			if (attunement != null && attunement.owner().equals(p.getUUID())) {
				n += stack.getCount();
			}
		}

		return n;
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

	/** The key of the journal's Chapter Five objective for the player now, or null. */
	private static @Nullable String objectiveKey(ServerPlayer p) {
		ChapterFourRules.Objective objective = ChapterFiveRules.objective(Story.get(p), Progression.get(p).level, ChapterFiveService.balance());
		return objective == null ? null : objective.key();
	}

	private static boolean storyMet(RankFourChecklistPayload checklist) {
		return checklist.entries().stream().anyMatch(e -> "story".equals(e.id()) && e.met());
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

	/** Puts mission {@code id} in the player's hand, under way (as its start leaves a driven or space objective). */
	private static void give(ServerPlayer p, String id, Kind kind, Faction issuer) {
		Active active = new Active(id, "", kind, issuer.id(), (int) DAY, 0x5EEDL, TERMINAL);
		active.stage = Stage.ACTIVE;
		MissionData d = MissionService.get(p);
		d.active = active;
		MissionService.commit(p, d);
	}

	/** A grounded ship of the player's, parked in the test level (the record only: no body is spawned). */
	private static ShipRecord grounded(GameTestHelper helper, ServerPlayer owner) {
		GlobalPos pad = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 1, 5)));
		return ShipRecord.hulk(owner.getUUID(), UUID.randomUUID(), pad, 0f, "w4_test").withState(ShipState.GROUNDED);
	}

	// ---------------------------------------------------------------- stages and saves (6.1, 6.9)

	@GameTest(maxTicks = 20)
	public void stagesOnlyMoveForward(GameTestHelper helper) {
		StoryData data = new StoryData();
		helper.assertTrue(ChapterFiveRules.advance(data, ChapterFiveStage.WAITING, 5L), msg("NOT_STARTED moves on to WAITING"));
		helper.assertTrue(data.chapter5.waitSince() == 5L, msg("the stage's wait starts when it is entered"));
		helper.assertTrue(!ChapterFiveRules.advance(data, ChapterFiveStage.NOT_STARTED, 6L), msg("WAITING never goes back"));
		helper.assertTrue(!ChapterFiveRules.advance(data, ChapterFiveStage.WAITING, 7L), msg("entering the same stage again changes nothing"));
		helper.assertTrue(data.chapter5.stage() == ChapterFiveStage.WAITING && data.chapter5.waitSince() == 5L, msg("WAITING stays, its wait unchanged"));
		helper.assertTrue(ChapterFiveRules.advance(data, ChapterFiveStage.VERSES, 8L) && data.chapter5.stage() == ChapterFiveStage.VERSES,
				msg("a later stage may be jumped to (commands, old saves)"));
		helper.assertTrue(ChapterFiveRules.parseStage(" the_factor ") == ChapterFiveStage.THE_FACTOR
				&& ChapterFiveRules.parseStage("THE_HYMNAL") == ChapterFiveStage.THE_HYMNAL && ChapterFiveRules.parseStage("nowhere") == null,
				msg("stage names parse in any case; unknown names are null"));
		helper.assertTrue(ChapterFiveStage.of(99) == ChapterFiveStage.COMPLETE && ChapterFiveStage.of(-3) == ChapterFiveStage.NOT_STARTED,
				msg("a saved ordinal out of range is clamped"));

		ServerPlayer p = at(helper, spot(0), Path.JEDI, ChapterFiveStage.THE_FACTOR, KaastOutcome.SPARED);

		try {
			helper.assertTrue(!ChapterFiveService.enterStage(p, ChapterFiveStage.SALTGATE), msg("enterStage refuses an earlier stage"));
			helper.assertTrue(!ChapterFiveService.enterStage(p, ChapterFiveStage.THE_FACTOR), msg("enterStage refuses the same stage"));
			helper.assertTrue(c5(p).stage() == ChapterFiveStage.THE_FACTOR, msg("the stage stays THE_FACTOR, got " + c5(p).stage()));
			helper.assertTrue(ChapterFiveService.enterStage(p, ChapterFiveStage.COMPLETE) && ChapterFive.stage(p) == ChapterFiveStage.COMPLETE,
					msg("enterStage moves forward"));
			helper.assertTrue(!ChapterFiveService.enterStage(p, ChapterFiveStage.AFTERMATH) && c5(p).stage() == ChapterFiveStage.COMPLETE,
					msg("COMPLETE is the end"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void m4SaveAtC4CompleteEntersWaiting(GameTestHelper helper) {
		// A Milestone 4 save: Chapter Four complete with Kaast spared, no chapter5 field at all.
		StoryData m4 = new StoryData();
		m4.path = Path.SITH.id();
		m4.pathStage = PathStage.COMPLETE.ordinal();
		m4.chapter3 = ChapterThreeRules.withStage(m4.chapter3, ChapterThreeStage.COMPLETE);
		m4.chapter4 = m4.chapter4.withStage(ChapterFourStage.COMPLETE, 0L).withBackPay().withDuel(duel(KaastOutcome.SPARED));
		JsonObject saved = StoryData.CODEC.encodeStart(JsonOps.INSTANCE, m4).getOrThrow().getAsJsonObject();
		saved.remove("chapter5");
		StoryData loaded = StoryData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(loaded.chapter5.equals(StoryData.Chapter5.NEW), msg("no chapter5 field loads as NEW, got " + loaded.chapter5));
		helper.assertTrue(ChapterFiveRules.kaast(loaded) == KaastOutcome.SPARED, msg("Kaast's fate survives the save"));
		helper.assertTrue(ChapterFiveRules.shouldWait(loaded), msg("an M4 save at Chapter Four COMPLETE should begin Chapter Five"));

		StoryData full = new StoryData();
		full.chapter5 = new StoryData.Chapter5(ChapterFiveStage.VERSES, 12L, 0b101, ChapterFive.Way.CLAN.ordinal(), StoryData.Chapter5.DASK_CLAN, 2, 0b011,
				BossOutcome.BOUND.ordinal(), ChapterFiveRules.BOSS_RESOLVED);
		StoryData back = StoryData.CODEC.parse(JsonOps.INSTANCE, StoryData.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow()).getOrThrow();
		helper.assertTrue(back.chapter5.equals(full.chapter5), msg("every Chapter Five field survives the save: " + back.chapter5));

		ServerPlayer old = M3TestPlayers.listed(helper, spot(0));
		ServerPlayer midway = M3TestPlayers.listed(helper, spot(1));

		try {
			// A stale call flag (a sleep long ago): entering WAITING starts the wait afresh.
			loaded.flags.add(ChapterFiveRules.CALL_DUE);
			Story.commit(old, loaded);
			long now = helper.getLevel().getGameTime();
			helper.assertTrue(ChapterFiveService.checkWaiting(old), msg("the old save enters WAITING on its first check"));
			helper.assertTrue(c5(old).stage() == ChapterFiveStage.WAITING && c5(old).waitSince() == now,
					msg("WAITING with its wait counted from now (" + now + "), got " + c5(old)));
			helper.assertTrue(!story(old).hasFlag(ChapterFiveRules.CALL_DUE), msg("entering WAITING clears a stale call flag"));
			helper.assertTrue(!ChapterFiveService.checkWaiting(old), msg("a second check changes nothing"));

			StoryData hunted = Story.get(midway);
			hunted.chapter4 = hunted.chapter4.withStage(ChapterFourStage.HUNTED, 0L);
			Story.commit(midway, hunted);
			helper.assertTrue(!ChapterFiveService.checkWaiting(midway) && c5(midway).stage() == ChapterFiveStage.NOT_STARTED,
					msg("a player still in Chapter Four does not begin Chapter Five"));
		} finally {
			M3TestPlayers.remove(old);
			M3TestPlayers.remove(midway);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the call and the helm (6.1, 6.2)

	@GameTest(maxTicks = 20)
	public void callOnSafeMomentOnly(GameTestHelper helper) {
		ChapterFiveBalance b = ChapterFiveService.balance();
		StoryData waiting = pure(ChapterFiveStage.WAITING);
		waiting.chapter5 = waiting.chapter5.withStage(ChapterFiveStage.WAITING, 1000L);
		helper.assertTrue(!ChapterFiveRules.callDue(waiting, 1000L + b.waitTicks - 1, b), msg("WAITING waits a full day first"));
		helper.assertTrue(ChapterFiveRules.callDue(waiting, 1000L + b.waitTicks, b), msg("after the wait the call is due"));
		waiting.flags.add(ChapterFiveRules.CALL_DUE);
		helper.assertTrue(ChapterFiveRules.callDue(waiting, 1001L, b), msg("waking from a sleep makes it due at once"));
		helper.assertTrue(!ChapterFiveRules.callDue(pure(ChapterFiveStage.CALLED), Long.MAX_VALUE / 2, b), msg("only WAITING waits for the call"));

		ServerPlayer p = at(helper, spot(0), Path.JEDI, ChapterFiveStage.WAITING, KaastOutcome.SPARED);
		Mob zombie = null;

		try {
			StoryData data = Story.get(p);
			data.chapter5 = data.chapter5.withStage(ChapterFiveStage.WAITING, helper.getLevel().getGameTime());
			Story.commit(p, data);
			p.setOnGround(true);
			long xp = totalXp(p);
			helper.assertTrue(!ChapterFiveService.checkCall(p) && c5(p).stage() == ChapterFiveStage.WAITING, msg("not due yet: no call"));

			flag(p, ChapterFiveRules.CALL_DUE);
			zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
			helper.assertTrue(!StoryMentors.safeMoment(p), msg("a hostile within range is no safe moment"));
			helper.assertTrue(!ChapterFiveService.checkCall(p) && c5(p).stage() == ChapterFiveStage.WAITING, msg("due, but a hostile is near: no call"));

			zombie.discard();
			zombie = null;
			helper.assertTrue(StoryMentors.safeMoment(p), msg("on the home world, on the ground, unhurt, nothing hostile near, no dialogue: safe"));
			helper.assertTrue(ChapterFiveService.checkCall(p) && c5(p).stage() == ChapterFiveStage.CALLED,
					msg("due and safe: the call plays, got " + c5(p).stage()));
			helper.assertTrue(totalXp(p) - xp == b.callXp, msg("the call pays " + b.callXp + " XP, got " + (totalXp(p) - xp)));
			helper.assertTrue(entry(p, JournalEntries.THE_FACTOR_ASKS) && !story(p).hasFlag(ChapterFiveRules.CALL_DUE),
					msg("entry the_factor_asks; the due flag is spent"));
			DialogueSession session = Dialogues.session(p);
			helper.assertTrue(session != null && session.dialogue().equals(id("c5_call_saelen")),
					msg("a Jedi hears Saelen's call, got " + (session == null ? "no dialogue" : session.dialogue())));
			helper.assertTrue(!StoryMentors.safeMoment(p), msg("an open dialogue is no safe moment"));
			Dialogues.close(p);
			helper.assertTrue(ChapterFiveService.checkCall(p) && totalXp(p) - xp == b.callXp && Dialogues.session(p) == null, msg("the call plays once"));
		} finally {
			if (zombie != null) {
				zombie.discard();
			}

			Dialogues.close(p);
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void helmUnlockedAtCalledAndForOldSaves(GameTestHelper helper) {
		for (ChapterFiveStage stage : ChapterFiveStage.values()) {
			StoryData data = pure(stage);
			boolean called = stage.ordinal() >= ChapterFiveStage.CALLED.ordinal();
			boolean armed = stage.ordinal() >= ChapterFiveStage.ARMED.ordinal();
			helper.assertTrue(ChapterFiveRules.helmDue(data) == called && ChapterFiveRules.holdfastDue(data) == armed,
					msg(stage.id() + ": the helm from CALLED on, the holdfast from ARMED on"));
		}

		ServerPlayer fresh = at(helper, spot(0), Path.JEDI, ChapterFiveStage.WAITING, KaastOutcome.SPARED);
		ServerPlayer old = at(helper, spot(1), Path.SITH, ChapterFiveStage.VERSES, KaastOutcome.STRIPPED);
		ServerPlayer before = at(helper, spot(2), Path.INDEPENDENT, ChapterFiveStage.NOT_STARTED, KaastOutcome.SPARED);
		List<ServerPlayer> players = List.of(fresh, old, before);

		try {
			players.forEach(StoryM5GameTests::lockTravel);

			ChapterFiveService.enterStage(fresh, ChapterFiveStage.CALLED);
			helper.assertTrue(TravelAttachments.get(fresh).helm(), msg("CALLED unlocks the helm"));
			helper.assertTrue(TravelService.isUnlocked(fresh, Destination.SALTGATE) && !TravelService.isUnlocked(fresh, Destination.HOLDFAST),
					msg("CALLED unlocks Saltgate, not yet the holdfast"));

			// An old save already past those stages (or a command's jump) gets what its stage earned.
			ChapterFiveService.repairUnlocks(old);
			helper.assertTrue(TravelAttachments.get(old).helm() && TravelService.isUnlocked(old, Destination.SALTGATE)
					&& TravelService.isUnlocked(old, Destination.HOLDFAST), msg("a save at VERSES gets the helm, Saltgate and the holdfast"));

			ChapterFiveService.repairUnlocks(before);
			helper.assertTrue(!TravelAttachments.get(before).helm() && !TravelService.isUnlocked(before, Destination.SALTGATE),
					msg("before the call nothing is unlocked"));
		} finally {
			players.forEach(M3TestPlayers::remove);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void helmLessonPaysOnce(GameTestHelper helper) {
		ChapterFiveBalance b = ChapterFiveService.balance();
		ServerPlayer p = at(helper, spot(0), Path.JEDI, ChapterFiveStage.CALLED, KaastOutcome.SPARED);
		ServerPlayer early = at(helper, spot(1), Path.SITH, ChapterFiveStage.WAITING, KaastOutcome.SPARED);

		try {
			long xp = totalXp(p);
			ChapterFiveService.onHelmTaken(p);
			helper.assertTrue(totalXp(p) - xp == b.helmXp && entry(p, JournalEntries.THE_HELM) && story(p).hasFlag(ChapterFiveRules.HELM),
					msg("taking the helm pays " + b.helmXp + " XP and writes the_helm, got " + (totalXp(p) - xp)));
			ChapterFiveService.onHelmTaken(p);
			helper.assertTrue(totalXp(p) - xp == b.helmXp, msg("the helm pays once"));

			xp = totalXp(p);
			ChapterFiveService.onOrbitEntered(p, Planets.Planet.HOME, false);
			helper.assertTrue(totalXp(p) - xp == b.orbitXp && codex(p, JournalEntries.CODEX_SPACE) && !story(p).hasFlag(ChapterFiveRules.JUMP),
					msg("the first orbit pays " + b.orbitXp + " XP and opens the space page; no jump yet"));
			ChapterFiveService.onOrbitEntered(p, Planets.Planet.GHESSAR, true);
			helper.assertTrue(totalXp(p) - xp == b.orbitXp + b.jumpXp && story(p).hasFlag(ChapterFiveRules.JUMP),
					msg("the first jump pays " + b.jumpXp + " XP; the orbit is not paid again"));
			ChapterFiveService.onOrbitEntered(p, Planets.Planet.GHESSAR, true);
			helper.assertTrue(totalXp(p) - xp == b.orbitXp + b.jumpXp, msg("orbit and jump pay once each"));
			helper.assertTrue(c5(p).stage() == ChapterFiveStage.CALLED, msg("the lesson's steps are flags: the stage stays CALLED"));

			long earlyXp = totalXp(early);
			ChapterFiveService.onHelmTaken(early);
			ChapterFiveService.onOrbitEntered(early, Planets.Planet.HOME, true);
			helper.assertTrue(totalXp(early) == earlyXp && !story(early).hasFlag(ChapterFiveRules.HELM) && !story(early).hasFlag(ChapterFiveRules.ORBIT),
					msg("before the call the helm lesson pays nothing"));
		} finally {
			M3TestPlayers.remove(p);
			M3TestPlayers.remove(early);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- Saltgate's three ways (6.3)

	@GameTest(maxTicks = 20)
	public void waysProgressInParallelFirstWins(GameTestHelper helper) {
		ChapterFiveBalance b = ChapterFiveService.balance();
		ServerPlayer p = at(helper, spot(0), Path.JEDI, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);

		try {
			long xp = totalXp(p);
			int before = credits(p);
			ChapterFiveService.payMission(p, definition(helper, ChapterFiveRules.MATRIARCH_HUNT));
			helper.assertTrue(c5(p).chosenWay() == ChapterFive.Way.CLAN && c5(p).hasWay(ChapterFive.Way.CLAN), msg("the matriarch completes the clan's way first"));
			helper.assertTrue(c5(p).stage() == ChapterFiveStage.THE_FACTOR, msg("the first way moves the chapter to THE_FACTOR, got " + c5(p).stage()));
			helper.assertTrue(entry(p, JournalEntries.THE_CLANS_WAY) && Identity.reputation(p, Faction.CLAN_HASK) == b.matriarchRep,
					msg("entry the_clans_way; clan +" + b.matriarchRep));
			helper.assertTrue(totalXp(p) - xp == b.matriarchXp && credits(p) - before == b.matriarchCredits,
					msg("the hunt pays " + b.matriarchXp + " XP and " + b.matriarchCredits + " credits"));
			helper.assertTrue(condition(p, "c5:way", "clan") == null && condition(p, "c5:way", "any") == null && condition(p, "c5:way", "kajidic") != null,
					msg("c5:way names the ways done"));

			// The kajidic's way goes on as optional faction work: both jobs, then the audience.
			ChapterFiveService.payMission(p, definition(helper, ChapterFiveRules.DEBT));
			ChapterFiveService.payMission(p, definition(helper, ChapterFiveRules.CARGO));
			int kajidic = Identity.reputation(p, Faction.VESSBA_KAJIDIC);
			helper.assertTrue(kajidic == 2 * b.jobRep, msg("two jobs: kajidic +" + 2 * b.jobRep + ", got " + kajidic));
			helper.assertTrue(condition(p, "c5:audience", "") == null, msg("both jobs done: Vessba grants the audience"));
			action(p, "c5:way", "kajidic");
			helper.assertTrue(c5(p).hasWay(ChapterFive.Way.KAJIDIC) && Identity.reputation(p, Faction.VESSBA_KAJIDIC) == kajidic + b.audienceRep
					&& entry(p, JournalEntries.THE_KAJIDIC_WAY), msg("the audience completes the kajidic's way: +" + b.audienceRep));
			helper.assertTrue(c5(p).chosenWay() == ChapterFive.Way.CLAN && c5(p).stage() == ChapterFiveStage.THE_FACTOR,
					msg("a later way changes neither the first way nor the stage"));

			action(p, "c5:way", "kajidic");
			ChapterFiveService.payMission(p, definition(helper, ChapterFiveRules.MATRIARCH_HUNT));
			ChapterFiveService.payMission(p, definition(helper, ChapterFiveRules.DEBT));
			helper.assertTrue(Identity.reputation(p, Faction.VESSBA_KAJIDIC) == kajidic + b.audienceRep && Identity.reputation(p, Faction.CLAN_HASK) == b.matriarchRep,
					msg("each way's work pays once"));
			action(p, "c5:way", "force");
			helper.assertTrue(!c5(p).hasWay(ChapterFive.Way.FORCE), msg("asking for the Force's way completes nothing that was not earned"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void forceWayOffersOnlyKnownPowers(GameTestHelper helper) {
		ChapterFiveBalance b = ChapterFiveService.balance();
		ServerPlayer trickster = at(helper, spot(0), Path.JEDI, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		ServerPlayer pusher = at(helper, spot(1), Path.INDEPENDENT, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		ServerPlayer frightener = at(helper, spot(2), Path.SITH, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		List<ServerPlayer> players = List.of(trickster, pusher, frightener);

		try {
			helper.assertTrue(condition(trickster, "c5:door", "") == null && condition(trickster, "c5:no_power", "") == null,
					msg("the door is the way in; without Mind Trick or Fear, c5:no_power holds"));
			action(trickster, "c5:doorman", ChapterFiveRules.TRICK);
			helper.assertTrue(!c5(trickster).hasWay(ChapterFive.Way.FORCE) && !story(trickster).hasFlag(ChapterFiveRules.FORCE_PREFIX + ChapterFiveRules.TRICK),
					msg("no Mind Trick known: the trick is refused"));

			learn(trickster, MIND_TRICK);
			helper.assertTrue(condition(trickster, "c5:no_power", "") != null, msg("knowing Mind Trick, c5:no_power fails"));
			action(trickster, "c5:doorman", ChapterFiveRules.PUSH);
			helper.assertTrue(!c5(trickster).hasWay(ChapterFive.Way.FORCE), msg("a player who can use a door power is not offered the push"));

			long xp = totalXp(trickster);
			action(trickster, "c5:doorman", ChapterFiveRules.TRICK);
			helper.assertTrue(c5(trickster).hasWay(ChapterFive.Way.FORCE) && c5(trickster).chosenWay() == ChapterFive.Way.FORCE
					&& c5(trickster).stage() == ChapterFiveStage.THE_FACTOR, msg("the trick opens the door: the Force's way, THE_FACTOR"));
			helper.assertTrue(story(trickster).hasFlag(ChapterFiveRules.FORCE_PREFIX + ChapterFiveRules.TRICK) && entry(trickster, JournalEntries.THE_FORCES_WAY)
					&& codex(trickster, JournalEntries.CODEX_CARTEL_ENFORCERS), msg("the trick is remembered, with its entry and the enforcers' page"));
			helper.assertTrue(totalXp(trickster) - xp == b.forceWayXp && Identity.reputation(trickster, Faction.VESSBA_KAJIDIC) == b.mindTrickWayRep,
					msg(b.forceWayXp + " XP and kajidic " + b.mindTrickWayRep + " after a Mind Trick, got " + (totalXp(trickster) - xp) + " and "
							+ Identity.reputation(trickster, Faction.VESSBA_KAJIDIC)));
			helper.assertTrue(Progression.isOnCooldown(trickster, MIND_TRICK), msg("the trick spends the power as if cast"));
			helper.assertTrue(ChapterFiveRules.guardIndex(story(trickster)) == ChapterFiveRules.GUARDS_TRICK, msg("four guards wait after a trick"));
			helper.assertTrue(condition(trickster, "c5:door", "") != null, msg("once a way is done the door is no longer the way in"));

			xp = totalXp(pusher);
			action(pusher, "c5:doorman", ChapterFiveRules.PUSH);
			helper.assertTrue(c5(pusher).hasWay(ChapterFive.Way.FORCE) && story(pusher).hasFlag(ChapterFiveRules.FORCE_PREFIX + ChapterFiveRules.PUSH),
					msg("without either power the door is forced with a push"));
			helper.assertTrue(totalXp(pusher) - xp == b.forceWayXp && Identity.reputation(pusher, Faction.VESSBA_KAJIDIC) == b.forceWayRep,
					msg(b.forceWayXp + " XP and kajidic " + b.forceWayRep + " for a forced door"));
			helper.assertTrue(ChapterFiveRules.guardIndex(story(pusher)) == ChapterFiveRules.GUARDS_FORCED, msg("eight guards after a forced door"));

			learn(frightener, FEAR);
			action(frightener, "c5:doorman", ChapterFiveRules.FEAR);
			helper.assertTrue(c5(frightener).hasWay(ChapterFive.Way.FORCE) && Identity.reputation(frightener, Faction.VESSBA_KAJIDIC) == b.forceWayRep
					&& ChapterFiveRules.guardIndex(story(frightener)) == ChapterFiveRules.GUARDS_FORCED && Progression.isOnCooldown(frightener, FEAR),
					msg("Fear on the guards: kajidic " + b.forceWayRep + ", eight guards, the power spent"));
		} finally {
			players.forEach(M3TestPlayers::remove);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void countingHouseGuardsByWay(GameTestHelper helper) {
		StoryData none = pure(ChapterFiveStage.SALTGATE);
		helper.assertTrue(ChapterFiveRules.guardIndex(none) == ChapterFiveRules.GUARDS_KAJIDIC && ChapterFiveRules.guards(none, DEFAULTS) == 0,
				msg("before any way: the kajidic's count"));
		StoryData kajidic = withWays(ChapterFive.Way.KAJIDIC, ChapterFive.Way.FORCE);
		helper.assertTrue(ChapterFiveRules.guards(kajidic, DEFAULTS) == 0 && !ChapterFiveRules.clanAllies(kajidic),
				msg("the kajidic sold Dask out: no guards"));
		StoryData clan = withWays(ChapterFive.Way.CLAN, ChapterFive.Way.KAJIDIC);
		helper.assertTrue(ChapterFiveRules.guards(clan, DEFAULTS) == 6 && ChapterFiveRules.clanAllies(clan),
				msg("the clan's way first: six guards, and the clan fights beside the player"));
		StoryData trick = withWays(ChapterFive.Way.FORCE);
		trick.flags.add(ChapterFiveRules.FORCE_PREFIX + ChapterFiveRules.TRICK);
		helper.assertTrue(ChapterFiveRules.guards(trick, DEFAULTS) == 4, msg("a Mind Trick at the door: four guards"));

		for (String how : List.of(ChapterFiveRules.FEAR, ChapterFiveRules.PUSH)) {
			StoryData forced = withWays(ChapterFive.Way.FORCE);
			forced.flags.add(ChapterFiveRules.FORCE_PREFIX + how);
			helper.assertTrue(ChapterFiveRules.guards(forced, DEFAULTS) == 8, msg(how + " at the door: eight guards"));
		}

		ChapterFiveBalance shortList = new ChapterFiveBalance();
		shortList.countingHouseGuards = new int[] {0, 6};
		helper.assertTrue(ChapterFiveRules.guards(trick, shortList) == 0, msg("a count missing from the config means no guards, not a crash"));
		helper.assertTrue(ChapterFiveRules.clearsOnArrival(0, false) && !ChapterFiveRules.clearsOnArrival(6, false) && ChapterFiveRules.clearsOnArrival(8, true),
				msg("no guards, or Peaceful: the counting-house is clear on arrival"));
		helper.assertTrue(DEFAULTS.clanAllies == 3, msg("three clan warriors fight beside the player"));

		// The counting-house's pay, once; then the journal names Dask.
		ChapterFiveBalance b = ChapterFiveService.balance();
		ServerPlayer p = at(helper, spot(0), Path.JEDI, ChapterFiveStage.THE_FACTOR, KaastOutcome.SPARED);

		try {
			helper.assertTrue("quest.oldrepublic.c5.objective.the_factor".equals(objectiveKey(p)), msg("THE_FACTOR: reach the factor, got " + objectiveKey(p)));
			long xp = totalXp(p);
			int before = credits(p);
			ChapterFiveService.payMission(p, definition(helper, ChapterFiveRules.COUNTING_HOUSE_RUN));
			helper.assertTrue(story(p).hasFlag(ChapterFiveRules.COUNTING_HOUSE) && totalXp(p) - xp == b.countingHouseXp && credits(p) - before == b.countingHouseCredits,
					msg("the counting-house pays " + b.countingHouseXp + " XP and " + b.countingHouseCredits + " credits"));
			helper.assertTrue("quest.oldrepublic.c5.objective.dask".equals(objectiveKey(p)), msg("cleared: the journal names Dask, got " + objectiveKey(p)));
			ChapterFiveService.payMission(p, definition(helper, ChapterFiveRules.COUNTING_HOUSE_RUN));
			helper.assertTrue(totalXp(p) - xp == b.countingHouseXp && credits(p) - before == b.countingHouseCredits, msg("it pays once"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the factor (6.4)

	@GameTest(maxTicks = 20)
	public void daskChoicesAndShifts(GameTestHelper helper) {
		helper.assertTrue(ChapterFiveRules.daskChoice("release") == StoryData.Chapter5.DASK_RELEASE && ChapterFiveRules.daskChoice(" Clan ") == StoryData.Chapter5.DASK_CLAN
				&& ChapterFiveRules.daskChoice("end") == StoryData.Chapter5.DASK_END && ChapterFiveRules.daskChoice("spare") == StoryData.Chapter5.DASK_NONE,
				msg("Dask's three fates parse; anything else is none"));

		for (int choice = StoryData.Chapter5.DASK_RELEASE; choice <= StoryData.Chapter5.DASK_END; choice++) {
			helper.assertTrue(ChapterFiveRules.daskChoice(ChapterFiveRules.daskId(choice)) == choice, msg("fate " + choice + " round-trips through its id"));
		}

		StoryData ready = pure(ChapterFiveStage.THE_FACTOR);
		helper.assertTrue(!ChapterFiveRules.daskChoiceOpen(ready, StoryData.Chapter5.DASK_RELEASE), msg("no choice before the counting-house is cleared"));
		ready.flags.add(ChapterFiveRules.COUNTING_HOUSE);
		helper.assertTrue(ChapterFiveRules.daskChoiceOpen(ready, StoryData.Chapter5.DASK_RELEASE) && ChapterFiveRules.daskChoiceOpen(ready, StoryData.Chapter5.DASK_END)
				&& !ChapterFiveRules.daskChoiceOpen(ready, StoryData.Chapter5.DASK_CLAN), msg("cleared: he may go or die, but go to the clan only after meeting Varra"));
		ready.flags.add(ChapterFiveRules.MET_VARRA);
		helper.assertTrue(ChapterFiveRules.daskChoiceOpen(ready, StoryData.Chapter5.DASK_CLAN) && !ChapterFiveRules.daskChoiceOpen(ready, StoryData.Chapter5.DASK_NONE),
				msg("after meeting Varra the clan's choice opens; none is no choice"));

		ChapterFiveBalance b = ChapterFiveService.balance();
		ServerPlayer releaser = factor(helper, spot(0));
		ServerPlayer clanward = factor(helper, spot(1));
		ServerPlayer ender = factor(helper, spot(2));
		List<ServerPlayer> players = List.of(releaser, clanward, ender);

		try {
			// Let him go.
			long xp = totalXp(releaser);
			int align = Identity.alignment(releaser);
			helper.assertTrue(condition(releaser, "c5:dask_ready", "") == null && condition(releaser, "c5:dask_ready", "clan") != null,
					msg("Dask waits for the choice; the clan's needs Varra"));
			action(releaser, "c5:dask", "release");
			helper.assertTrue(c5(releaser).dask() == StoryData.Chapter5.DASK_RELEASE && story(releaser).hasFlag(ChapterFiveRules.DASK_PREFIX + "release"),
					msg("release is saved"));
			helper.assertTrue(Identity.reputation(releaser, Faction.VESSBA_KAJIDIC) == b.releaseRep && Identity.alignment(releaser) - align == b.releaseLight,
					msg("release: kajidic +" + b.releaseRep + ", light +" + b.releaseLight));
			helper.assertTrue(totalXp(releaser) - xp == b.daskXp && entry(releaser, JournalEntries.THE_FACTOR) && !entry(releaser, JournalEntries.DASK_ENDED),
					msg("every fate: " + b.daskXp + " XP and the_factor"));
			helper.assertTrue(c5(releaser).stage() == ChapterFiveStage.ARMED, msg("no armed ship: on to ARMED, got " + c5(releaser).stage()));
			action(releaser, "c5:dask", "end");
			helper.assertTrue(c5(releaser).dask() == StoryData.Chapter5.DASK_RELEASE && Identity.reputation(releaser, Faction.VESSBA_KAJIDIC) == b.releaseRep,
					msg("the fate is chosen once"));

			// Hand him to the clan.
			action(clanward, "c5:dask", "clan");
			helper.assertTrue(c5(clanward).dask() == StoryData.Chapter5.DASK_NONE && c5(clanward).stage() == ChapterFiveStage.THE_FACTOR,
					msg("the clan's choice is refused before meeting Varra"));
			flag(clanward, ChapterFiveRules.MET_VARRA);
			action(clanward, "c5:dask", "clan");
			helper.assertTrue(c5(clanward).dask() == StoryData.Chapter5.DASK_CLAN && Identity.reputation(clanward, Faction.CLAN_HASK) == b.clanRep
					&& Identity.reputation(clanward, Faction.VESSBA_KAJIDIC) == b.clanKajidicRep,
					msg("clan: clan +" + b.clanRep + ", kajidic " + b.clanKajidicRep));

			// End him.
			align = Identity.alignment(ender);
			action(ender, "c5:dask", "end");
			helper.assertTrue(Identity.reputation(ender, Faction.VESSBA_KAJIDIC) == b.endKajidicRep && Identity.reputation(ender, Faction.CLAN_HASK) == b.endClanRep
					&& Identity.alignment(ender) - align == b.endDark, msg("end: kajidic " + b.endKajidicRep + ", clan " + b.endClanRep + ", dark " + b.endDark));
			helper.assertTrue(entry(ender, JournalEntries.DASK_ENDED) && entry(ender, JournalEntries.THE_FACTOR), msg("ending him writes dask_ended too"));
		} finally {
			players.forEach(M3TestPlayers::remove);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- arming and the blockade (6.5)

	@GameTest(maxTicks = 20)
	public void armedSkippedWhenShipHasCannons(GameTestHelper helper) {
		helper.assertTrue(ChapterFiveRules.afterFactor(true) == ChapterFiveStage.BLOCKADE && ChapterFiveRules.afterFactor(false) == ChapterFiveStage.ARMED,
				msg("after the factor: BLOCKADE for an armed ship, else ARMED"));

		ChapterFiveBalance b = ChapterFiveService.balance();
		MinecraftServer server = helper.getLevel().getServer();
		ShipRegistry.Data ships = ShipRegistry.snapshot(server);
		ServerPlayer armed = factor(helper, spot(0));
		ServerPlayer bare = factor(helper, spot(1));

		try {
			lockTravel(armed);
			ShipRegistry.put(server, grounded(helper, armed).withUpgrades(ShipRecord.UPGRADE_CANNONS));
			action(armed, "c5:dask", "release");
			helper.assertTrue(c5(armed).stage() == ChapterFiveStage.BLOCKADE, msg("a ship with cannons skips ARMED, got " + c5(armed).stage()));
			helper.assertTrue(TravelService.isUnlocked(armed, Destination.HOLDFAST), msg("skipping ARMED still puts the holdfast in the nav"));
			helper.assertTrue(condition(armed, "c5:cannons", "") != null, msg("no free cannons are offered past ARMED"));

			action(bare, "c5:dask", "release");
			helper.assertTrue(c5(bare).stage() == ChapterFiveStage.ARMED, msg("no ship: ARMED waits for cannons"));
			helper.assertTrue(condition(bare, "c5:cannons", "") == null, msg("the shipwright offers the free cannons in ARMED"));
			long xp = totalXp(bare);
			action(bare, "c5:fit_cannons");
			helper.assertTrue(!story(bare).hasFlag(ChapterFiveRules.CANNONS_PAID) && c5(bare).stage() == ChapterFiveStage.ARMED, msg("no ship: nothing is fitted"));

			ShipRegistry.put(server, grounded(helper, bare));
			action(bare, "c5:fit_cannons");
			helper.assertTrue(ShipRegistry.of(server, bare.getUUID()).map(r -> r.has(ShipRecord.UPGRADE_CANNONS)).orElse(false), msg("the free cannons are fitted"));
			helper.assertTrue(story(bare).hasFlag(ChapterFiveRules.CANNONS_PAID) && totalXp(bare) - xp == b.cannonsXp && entry(bare, JournalEntries.TEETH),
					msg("the cannons pay " + b.cannonsXp + " XP and write teeth"));
			helper.assertTrue(c5(bare).stage() == ChapterFiveStage.BLOCKADE, msg("armed now: on to BLOCKADE, got " + c5(bare).stage()));
			action(bare, "c5:fit_cannons");
			helper.assertTrue(totalXp(bare) - xp == b.cannonsXp, msg("the free cannons are fitted once"));
		} finally {
			ShipRegistry.commit(server, ships);
			M3TestPlayers.remove(armed);
			M3TestPlayers.remove(bare);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void tyrvannRefusedBeforeBlockade(GameTestHelper helper) {
		for (ChapterFiveStage stage : ChapterFiveStage.values()) {
			helper.assertTrue(ChapterFiveRules.tyrvannBlockaded(pure(stage)) == stage.ordinal() < ChapterFiveStage.BLOCKADE.ordinal(),
					msg(stage.id() + ": Tyrvann is refused (BLOCKADED) before BLOCKADE only"));
		}

		ServerPlayer p = at(helper, spot(0), Path.JEDI, ChapterFiveStage.THE_FACTOR, KaastOutcome.SPARED);

		try {
			lockTravel(p);
			helper.assertTrue(!TravelService.isUnlocked(p, Destination.HOLDFAST), msg("before ARMED the holdfast is not in the nav"));
			ChapterFiveService.enterStage(p, ChapterFiveStage.ARMED);
			helper.assertTrue(TravelService.isUnlocked(p, Destination.HOLDFAST), msg("ARMED puts Tyrvann's coordinates in the nav"));
			helper.assertTrue(ChapterFiveRules.tyrvannBlockaded(story(p)) && !ChapterFive.atLeast(p, ChapterFiveStage.BLOCKADE),
					msg("but Tyrvann stays refused until BLOCKADE"));
			ChapterFiveService.onPlanetArrived(p, Planets.Planet.TYRVANN, true);
			helper.assertTrue(c5(p).stage() == ChapterFiveStage.ARMED, msg("a landing on Tyrvann at ARMED moves nothing, got " + c5(p).stage()));

			setStage(p, ChapterFiveStage.TYRVANN);
			helper.assertTrue(!ChapterFiveRules.tyrvannBlockaded(story(p)), msg("once the blockade is broken Tyrvann is open"));
			ChapterFiveService.onPlanetArrived(p, Planets.Planet.TYRVANN, true);
			helper.assertTrue(c5(p).stage() == ChapterFiveStage.VERSES, msg("landing at TYRVANN moves on to VERSES, got " + c5(p).stage()));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void blockadeStartsOnArrivalInOrbit(GameTestHelper helper) {
		helper.assertTrue(ChapterFiveRules.blockadeStarts(pure(ChapterFiveStage.BLOCKADE), Planets.Planet.TYRVANN), msg("BLOCKADE over Tyrvann: the battle starts"));
		helper.assertTrue(!ChapterFiveRules.blockadeStarts(pure(ChapterFiveStage.BLOCKADE), Planets.Planet.GHESSAR), msg("another orbit starts nothing"));
		helper.assertTrue(!ChapterFiveRules.blockadeStarts(pure(ChapterFiveStage.ARMED), Planets.Planet.TYRVANN)
				&& !ChapterFiveRules.blockadeStarts(pure(ChapterFiveStage.TYRVANN), Planets.Planet.TYRVANN), msg("before or after BLOCKADE, Tyrvann's orbit is quiet"));

		// The battle itself is W2's and spawns its ships, so only arrivals that start none are played here.
		ChapterFiveBalance b = ChapterFiveService.balance();
		ServerPlayer blockaded = at(helper, spot(0), Path.SITH, ChapterFiveStage.BLOCKADE, KaastOutcome.SPARED);
		ServerPlayer early = at(helper, spot(1), Path.SITH, ChapterFiveStage.ARMED, KaastOutcome.SPARED);
		ServerPlayer winner = at(helper, spot(2), Path.JEDI, ChapterFiveStage.BLOCKADE, KaastOutcome.SPARED);
		List<ServerPlayer> players = List.of(blockaded, early, winner);

		try {
			ChapterFiveService.onOrbitEntered(blockaded, Planets.Planet.GHESSAR, true);
			ChapterFiveService.onOrbitEntered(early, Planets.Planet.TYRVANN, true);
			helper.assertTrue(!SpaceEncounters.blockadeRunning(blockaded) && !SpaceEncounters.blockadeRunning(early), msg("no battle outside BLOCKADE over Tyrvann"));
			helper.assertTrue(c5(blockaded).stage() == ChapterFiveStage.BLOCKADE && c5(early).stage() == ChapterFiveStage.ARMED, msg("the stages stay"));

			// A landing on Tyrvann in BLOCKADE without the battle: the scripted pass on Peaceful or with free flight off, else nothing.
			boolean scripted = ChapterFiveRules.scriptedPass(helper.getLevel().getDifficulty() == Difficulty.PEACEFUL, FlightBalance.get().enabled);
			long xp = totalXp(blockaded);
			ChapterFiveService.onPlanetArrived(blockaded, Planets.Planet.TYRVANN, true);

			if (scripted) {
				helper.assertTrue(c5(blockaded).stage() == ChapterFiveStage.VERSES && story(blockaded).hasFlag(ChapterFiveRules.BLOCKADE_PAID),
						msg("the scripted pass breaks the blockade and the landing goes on: VERSES, got " + c5(blockaded).stage()));
			} else {
				helper.assertTrue(c5(blockaded).stage() == ChapterFiveStage.BLOCKADE && totalXp(blockaded) == xp,
						msg("with free flight on, only breaking the blockade in orbit moves the chapter"));
			}

			// Breaking it (W2's hook): paid once, on to TYRVANN.
			xp = totalXp(winner);
			int before = credits(winner);
			ChapterFiveService.onBlockadeBroken(winner);
			helper.assertTrue(c5(winner).stage() == ChapterFiveStage.TYRVANN && totalXp(winner) - xp == b.blockadeXp && credits(winner) - before == b.blockadeCredits,
					msg("the gunship down: " + b.blockadeXp + " XP, " + b.blockadeCredits + " credits, TYRVANN"));
			helper.assertTrue(entry(winner, JournalEntries.THE_BLOCKADE) && codex(winner, JournalEntries.CODEX_HYMN_INTERCEPTORS), msg("the_blockade and the interceptors' page"));
			ChapterFiveService.onBlockadeBroken(winner);
			helper.assertTrue(totalXp(winner) - xp == b.blockadeXp && c5(winner).stage() == ChapterFiveStage.TYRVANN, msg("the blockade pays once"));
		} finally {
			players.forEach(M3TestPlayers::remove);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void twoFailuresOfferTheClan(GameTestHelper helper) {
		StoryData data = pure(ChapterFiveStage.BLOCKADE);
		data.chapter5 = data.chapter5.withBlockadeFailures(DEFAULTS.clanHelpFailures - 1);
		helper.assertTrue(!ChapterFiveRules.clanHelpOffered(data, false, DEFAULTS), msg("one lost battle: no offer yet"));
		data.chapter5 = data.chapter5.withBlockadeFailures(DEFAULTS.clanHelpFailures);
		helper.assertTrue(ChapterFiveRules.clanHelpOffered(data, false, DEFAULTS), msg("two lost battles: Varra offers the clan"));
		helper.assertTrue(!ChapterFiveRules.clanHelpOffered(data, true, DEFAULTS), msg("not when the clan already stands with the player"));
		helper.assertTrue(ChapterFiveRules.steps(data, true, false, DEFAULTS).stream().anyMatch(s -> s.line().key().endsWith(".clan_help")),
				msg("the journal sends the player to Varra"));
		data.chapter5 = data.chapter5.withStage(ChapterFiveStage.TYRVANN, 0L);
		helper.assertTrue(!ChapterFiveRules.clanHelpOffered(data, false, DEFAULTS), msg("only during BLOCKADE"));

		ChapterFiveBalance b = ChapterFiveService.balance();
		ServerPlayer first = at(helper, spot(0), Path.JEDI, ChapterFiveStage.BLOCKADE, KaastOutcome.STRIPPED);
		ServerPlayer second = at(helper, spot(1), Path.SITH, ChapterFiveStage.BLOCKADE, KaastOutcome.STRIPPED);
		ServerPlayer trusted = at(helper, spot(2), Path.INDEPENDENT, ChapterFiveStage.BLOCKADE, KaastOutcome.SPARED);
		List<ServerPlayer> players = List.of(first, second, trusted);

		try {
			ChapterFiveService.onBlockadeFailed(first);
			ChapterFiveService.onBlockadeFailed(first);
			helper.assertTrue(c5(first).blockadeFailures() == 1, msg("one failure per battle, got " + c5(first).blockadeFailures()));
			helper.assertTrue(condition(first, "c5:clan_help", "") != null, msg("after one failure Varra does not offer yet"));

			// A later battle (W2 starts each in orbit; here the earlier losses are set directly).
			failures(second, b.clanHelpFailures - 1);
			ChapterFiveService.onBlockadeFailed(second);
			helper.assertTrue(c5(second).blockadeFailures() == b.clanHelpFailures, msg("the second battle's loss counts"));
			helper.assertTrue(condition(second, "c5:clan_help", "") == null, msg("after " + b.clanHelpFailures + " failures Varra offers the clan"));
			action(second, "c5:hire_clan");
			helper.assertTrue(!story(second).hasFlag(ChapterFive.CLAN_HIRED), msg("without " + b.clanHelpCredits + " credits the clan is not hired"));
			Credits.give(second, b.clanHelpCredits);
			action(second, "c5:hire_clan");
			helper.assertTrue(story(second).hasFlag(ChapterFive.CLAN_HIRED) && credits(second) == 0 && ChapterFive.clanStands(second),
					msg("hired for " + b.clanHelpCredits + " credits: the clan stands with the player"));
			helper.assertTrue(condition(second, "c5:clan_help", "") != null, msg("once hired the offer is gone"));

			Identity.setReputation(trusted, Faction.CLAN_HASK, 2 * FACTION_DEFAULTS.clanStartSpared);
			failures(trusted, b.clanHelpFailures - 1);
			ChapterFiveService.onBlockadeFailed(trusted);
			helper.assertTrue(ChapterFive.clanStands(trusted) && condition(trusted, "c5:clan_help", "") != null,
					msg("the clan stands with a player it trusts: no hiring needed"));
		} finally {
			for (ServerPlayer p : players) {
				endBattle(p);
				M3TestPlayers.remove(p);
			}
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- Tyrvann and the verse stones (6.6)

	@GameTest(maxTicks = 20)
	public void versesNeedThreeAndLevel17(GameTestHelper helper) {
		helper.assertTrue(ChapterFiveRules.verseBit(1) == 1 && ChapterFiveRules.verseBit(2) == 2 && ChapterFiveRules.verseBit(3) == 4
				&& ChapterFiveRules.verseBit(0) == 0 && ChapterFiveRules.verseBit(4) == 0, msg("a bit per stone index 1 to 3"));
		StoryData data = pure(ChapterFiveStage.VERSES);
		data.chapter5 = data.chapter5.withVerses(0b011);
		helper.assertTrue(!ChapterFiveRules.versesReady(data, DEFAULTS.duelLevel, DEFAULTS), msg("two stones are not enough"));
		data.chapter5 = data.chapter5.withVerses(0b111);
		helper.assertTrue(ChapterFiveRules.versesReady(data, DEFAULTS.duelLevel, DEFAULTS) && !ChapterFiveRules.versesReady(data, DEFAULTS.duelLevel - 1, DEFAULTS),
				msg("three stones and level " + DEFAULTS.duelLevel));
		ChapterFourRules.Objective blocked = ChapterFiveRules.objective(data, DEFAULTS.duelLevel - 1, DEFAULTS);
		helper.assertTrue(blocked != null && blocked.key().endsWith(".not_ready") && blocked.args().equals(List.of(DEFAULTS.duelLevel)),
				msg("below the level the journal says which level is needed"));
		helper.assertTrue(ChapterFiveRules.verseEntry(1).equals(JournalEntries.VERSE_ONE) && ChapterFiveRules.verseCodex(1) == null
				&& JournalEntries.CODEX_THE_LONG_HYMN.equals(ChapterFiveRules.verseCodex(2)) && JournalEntries.CODEX_THE_FIRST_CANTOR.equals(ChapterFiveRules.verseCodex(3)),
				msg("entries and pages follow the order the stones fall"));

		ChapterFiveBalance b = ChapterFiveService.balance();
		int below = b.duelLevel - 1;
		helper.assertTrue(3L * b.verseXp < Progression.xpToNext(below), msg("the stones' XP alone must not level a player from " + below));
		ServerPlayer p = at(helper, spot(0), Path.JEDI, ChapterFiveStage.VERSES, KaastOutcome.SPARED);
		ServerPlayer landed = at(helper, spot(1), Path.SITH, ChapterFiveStage.TYRVANN, KaastOutcome.SPARED);

		try {
			level(p, below);
			long xp = totalXp(p);
			int before = credits(p);
			int sp = Progression.get(p).skillPoints;
			ChapterFiveService.onVerseSilenced(p, 2);
			helper.assertTrue(c5(p).verses() == 0b010 && entry(p, JournalEntries.VERSE_ONE) && !entry(p, JournalEntries.VERSE_TWO),
					msg("stone 2 falls first: verse_one"));
			ChapterFiveService.onVerseSilenced(p, 2);
			ChapterFiveService.onVerseSilenced(p, 0);
			ChapterFiveService.onVerseSilenced(p, 4);
			helper.assertTrue(c5(p).verses() == 0b010 && totalXp(p) - xp == b.verseXp, msg("a stone pays once; other indices are ignored"));
			ChapterFiveService.onVerseSilenced(p, 3);
			helper.assertTrue(entry(p, JournalEntries.VERSE_TWO) && codex(p, JournalEntries.CODEX_THE_LONG_HYMN), msg("the second stone: verse_two and the Long Hymn"));
			ChapterFiveService.onVerseSilenced(p, 1);
			helper.assertTrue(entry(p, JournalEntries.VERSE_THREE) && codex(p, JournalEntries.CODEX_THE_FIRST_CANTOR), msg("the third: verse_three and the First Cantor"));
			helper.assertTrue(totalXp(p) - xp == 3L * b.verseXp && credits(p) - before == 3 * b.verseCredits && Progression.get(p).skillPoints - sp == b.versesSp,
					msg("three stones: their XP and credits, and the third's skill point"));
			helper.assertTrue(c5(p).stage() == ChapterFiveStage.VERSES, msg("level " + below + ": the Hymnal stays shut, got " + c5(p).stage()));
			helper.assertTrue("quest.oldrepublic.c5.objective.not_ready".equals(objectiveKey(p)), msg("the journal says the level is missing, got " + objectiveKey(p)));

			level(p, b.duelLevel);
			ChapterFiveService.onLevelUp(p, b.duelLevel);
			helper.assertTrue(c5(p).stage() == ChapterFiveStage.THE_HYMNAL, msg("level " + b.duelLevel + ": THE_HYMNAL, got " + c5(p).stage()));

			ChapterFiveService.onVerseSilenced(landed, 1);
			helper.assertTrue(c5(landed).verses() == 0 && !entry(landed, JournalEntries.VERSE_ONE), msg("before VERSES a stone counts nothing"));
		} finally {
			M3TestPlayers.remove(p);
			M3TestPlayers.remove(landed);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the choice and the report (6.7)

	@GameTest(maxTicks = 20)
	public void ulvaineChoiceRewardsOncePerFlag(GameTestHelper helper) {
		helper.assertTrue(ChapterFiveRules.outcomeOf("spare") == BossOutcome.SPARED && ChapterFiveRules.outcomeOf(" BIND ") == BossOutcome.BOUND
				&& ChapterFiveRules.outcomeOf("end") == BossOutcome.EXECUTED && ChapterFiveRules.outcomeOf("echo") == null, msg("the three choices parse"));

		for (BossOutcome outcome : List.of(BossOutcome.SPARED, BossOutcome.BOUND, BossOutcome.EXECUTED)) {
			helper.assertTrue(ChapterFiveRules.outcomeOf(ChapterFiveRules.outcomeId(outcome)) == outcome, msg(outcome + " round-trips through its id"));
		}

		StoryData yielded = pure(ChapterFiveStage.THE_HYMNAL);
		helper.assertTrue(!ChapterFiveRules.choiceOpen(yielded), msg("no choice before she yields"));
		yielded.chapter5 = yielded.chapter5.withBoss(ChapterFiveRules.BOSS_YIELDED, -1);
		helper.assertTrue(ChapterFiveRules.choiceOpen(yielded), msg("yielded: the choice is open"));
		yielded.chapter5 = yielded.chapter5.withBoss(ChapterFiveRules.BOSS_RESOLVED, BossOutcome.SPARED.ordinal());
		helper.assertTrue(!ChapterFiveRules.choiceOpen(yielded), msg("resolved: no second choice"));

		ChapterFiveBalance b = ChapterFiveService.balance();
		ServerPlayer spare = at(helper, spot(0), Path.JEDI, ChapterFiveStage.THE_HYMNAL, KaastOutcome.SPARED);
		ServerPlayer jediBind = at(helper, spot(1), Path.JEDI, ChapterFiveStage.THE_HYMNAL, KaastOutcome.SPARED);
		ServerPlayer sithBind = at(helper, spot(2), Path.SITH, ChapterFiveStage.THE_HYMNAL, KaastOutcome.SPARED);
		ServerPlayer wayfarerBind = at(helper, spot(3), Path.INDEPENDENT, ChapterFiveStage.THE_HYMNAL, KaastOutcome.SPARED);
		ServerPlayer end = at(helper, spot(4), Path.SITH, ChapterFiveStage.THE_HYMNAL, KaastOutcome.SPARED);
		ServerPlayer owner = at(helper, spot(5), Path.JEDI, ChapterFiveStage.THE_HYMNAL, KaastOutcome.SPARED);
		List<ServerPlayer> players = List.of(spare, jediBind, sithBind, wayfarerBind, end, owner);

		try {
			// Spare: light, and her red crystal attuned to the player.
			int align = Identity.alignment(spare);
			int crystals = attunedCrystals(spare);
			helper.assertTrue(ChapterFiveService.applyUlvaineChoice(spare, BossOutcome.SPARED), msg("sparing her pays"));
			helper.assertTrue(Identity.alignment(spare) - align == b.spareLight && attunedCrystals(spare) - crystals == 1 && entry(spare, JournalEntries.ULVAINE_SPARED)
					&& Identity.hasChoice(spare, "ulvaine.spare"), msg("spare: light +" + b.spareLight + ", her crystal attuned to the player, the entry, the choice"));
			helper.assertTrue(!ChapterFiveService.applyUlvaineChoice(spare, BossOutcome.SPARED) && Identity.alignment(spare) - align == b.spareLight
					&& attunedCrystals(spare) - crystals == 1, msg("spare pays once"));

			// Bind: by the path at the moment of the choice.
			align = Identity.alignment(jediBind);
			helper.assertTrue(ChapterFiveService.applyUlvaineChoice(jediBind, BossOutcome.BOUND), msg("binding her pays"));
			helper.assertTrue(Identity.alignment(jediBind) - align == b.bindJediLight && Identity.reputation(jediBind, Faction.JEDI_ORDER) == b.bindJediRep
					&& entry(jediBind, JournalEntries.ULVAINE_BOUND), msg("bound by a Jedi: light +" + b.bindJediLight + ", the Order +" + b.bindJediRep));
			int empire = Identity.reputation(sithBind, Faction.SITH_EMPIRE);
			ChapterFiveService.applyUlvaineChoice(sithBind, BossOutcome.BOUND);
			helper.assertTrue(Identity.reputation(sithBind, Faction.SITH_EMPIRE) - empire == b.bindSithRep && count(sithBind, VendorItems.ULVAINES_WRIT) == 1
					&& story(sithBind).hasFlag(ChapterFive.WRIT), msg("bound by a Sith: the Empire +" + b.bindSithRep + " and the Council's writ"));
			helper.assertTrue(!ChapterFiveService.applyUlvaineChoice(sithBind, BossOutcome.BOUND) && count(sithBind, VendorItems.ULVAINES_WRIT) == 1,
					msg("one writ"));
			int before = credits(wayfarerBind);
			ChapterFiveService.applyUlvaineChoice(wayfarerBind, BossOutcome.BOUND);
			helper.assertTrue(Identity.reputation(wayfarerBind, Faction.CLAN_HASK) == b.bindClanRep && credits(wayfarerBind) - before == b.bindClanCredits,
					msg("bound by an Independent: Clan Hask +" + b.bindClanRep + " and " + b.bindClanCredits + " credits"));

			// End: dark, the Cantor's Emitter, trust both ways.
			align = Identity.alignment(end);
			helper.assertTrue(ChapterFiveService.applyUlvaineChoice(end, BossOutcome.EXECUTED), msg("ending her pays"));
			helper.assertTrue(Identity.alignment(end) - align == b.endDarkUlvaine && Crafting.knows(end, id("emitter_cantor_legendary"))
					&& entry(end, JournalEntries.ULVAINE_ENDED), msg("end: dark " + b.endDarkUlvaine + ", the Cantor's Emitter schematic, the entry"));
			helper.assertTrue(Identity.trust(end, "jedi") == MENTOR_TRUST + b.endJediTrust && Identity.trust(end, "sith") == MENTOR_TRUST + b.endSithTrust,
					msg("end: Jedi trust " + b.endJediTrust + ", Sith trust +" + b.endSithTrust));

			// The flow: she yields, the choice dialogue opens, the choice resolves the fight once.
			ChapterFiveService.onBossYielded(owner, "ulvaine");
			DialogueSession session = Dialogues.session(owner);
			helper.assertTrue(c5(owner).boss() == ChapterFiveRules.BOSS_YIELDED && condition(owner, "c5:yield", "") == null, msg("she yields to her owner"));
			helper.assertTrue(session != null && session.dialogue().equals(id("c5_ulvaine_yield")), msg("the choice opens for the owner"));
			long xp = totalXp(owner);
			int level = Progression.get(owner).level;
			int sp = Progression.get(owner).skillPoints;
			int ownerCredits = credits(owner);
			action(owner, "ulvaine:resolve", "spare");
			helper.assertTrue(c5(owner).stage() == ChapterFiveStage.AFTERMATH && ChapterFive.ulvaineOutcome(owner) == BossOutcome.SPARED,
					msg("spared: AFTERMATH, got " + c5(owner).stage()));
			helper.assertTrue(story(owner).hasFlag(ChapterFiveRules.ULVAINE_PAID) && totalXp(owner) - xp == b.ulvaineXp && storySp(owner, sp, level) == b.ulvaineSp
					&& credits(owner) - ownerCredits == b.ulvaineCredits && codex(owner, JournalEntries.CODEX_DARTH_ULVAINE),
					msg("the fight's reward: " + b.ulvaineXp + " XP, " + b.ulvaineSp + " SP, " + b.ulvaineCredits + " credits, got " + (totalXp(owner) - xp) + " XP"));
			ChapterFiveService.onBossDefeated(owner, "ulvaine", BossOutcome.SPARED, owner.getUUID(), true);
			action(owner, "ulvaine:resolve", "end");
			helper.assertTrue(totalXp(owner) - xp == b.ulvaineXp && ChapterFive.ulvaineOutcome(owner) == BossOutcome.SPARED && !entry(owner, JournalEntries.ULVAINE_ENDED),
					msg("the fight pays once, and the choice is made once"));
		} finally {
			for (ServerPlayer p : players) {
				Dialogues.close(p);
				M3TestPlayers.remove(p);
			}
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void reportShowsRankFourChecklist(GameTestHelper helper) {
		helper.assertTrue(TurningRules.checklistVisible(Path.JEDI, ChapterFiveStage.AFTERMATH) && TurningRules.checklistVisible(Path.INDEPENDENT, ChapterFiveStage.COMPLETE)
				&& !TurningRules.checklistVisible(Path.SITH, ChapterFiveStage.THE_HYMNAL) && !TurningRules.checklistVisible(Path.NONE, ChapterFiveStage.COMPLETE),
				msg("the rank 4 checklist shows from AFTERMATH, for a path with a rank 4"));

		ChapterFiveBalance b = ChapterFiveService.balance();
		ServerPlayer p = at(helper, spot(0), Path.JEDI, ChapterFiveStage.AFTERMATH, KaastOutcome.SPARED);
		ServerPlayer wayfarer = at(helper, spot(1), Path.INDEPENDENT, ChapterFiveStage.AFTERMATH, KaastOutcome.SPARED);
		ServerPlayer early = at(helper, spot(2), Path.JEDI, ChapterFiveStage.THE_HYMNAL, KaastOutcome.SPARED);
		List<ServerPlayer> players = List.of(p, wayfarer, early);

		try {
			resolved(p, BossOutcome.SPARED);
			RankFourChecklistPayload before = RankFour.checklist(p);
			helper.assertTrue(before.visible() && before.kind() == RankFourChecklistPayload.MASTER && !storyMet(before),
					msg("AFTERMATH: the Master's checklist shows, its story line not yet met"));
			helper.assertTrue(condition(p, "c5:report_ready", "") == null && "quest.oldrepublic.c5.objective.report.jedi".equals(objectiveKey(p)),
					msg("the journal sends a Jedi to Saelen, got " + objectiveKey(p)));
			long xp = totalXp(p);
			action(p, "c5:report");
			helper.assertTrue(c5(p).stage() == ChapterFiveStage.COMPLETE && entry(p, JournalEntries.REPORT_C5) && story(p).hasFlag(ChapterFiveRules.REPORTED),
					msg("the report completes the chapter, got " + c5(p).stage()));
			helper.assertTrue(totalXp(p) - xp == b.reportXp && Identity.trust(p, "jedi") == MENTOR_TRUST + b.reportTrust
					&& Progression.get(p).flags.contains("resonance:chapter_five"), msg("the report: " + b.reportXp + " XP, trust +" + b.reportTrust + ", Resonance"));
			RankFourChecklistPayload after = RankFour.checklist(p);
			helper.assertTrue(after.visible() && storyMet(after), msg("COMPLETE: the checklist's story line is met"));
			action(p, "c5:report");
			helper.assertTrue(totalXp(p) - xp == b.reportXp, msg("the report is made once"));

			RankFourChecklistPayload wayfarers = RankFour.checklist(wayfarer);
			helper.assertTrue(wayfarers.visible() && wayfarers.kind() == RankFourChecklistPayload.WAYFARER, msg("an Independent sees the Wayfarer's checklist"));
			helper.assertTrue(!RankFour.checklist(early).visible() && condition(early, "c5:report_ready", "") != null,
					msg("before AFTERMATH: no checklist and no report"));
		} finally {
			players.forEach(M3TestPlayers::remove);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the two factions (7)

	@GameTest(maxTicks = 20)
	public void clanStartFromKaastOutcome(GameTestHelper helper) {
		helper.assertTrue(ChapterFiveRules.clanStart(KaastOutcome.SPARED, FACTION_DEFAULTS) == 100 && ChapterFiveRules.clanStart(KaastOutcome.STRIPPED, FACTION_DEFAULTS) == 0
				&& ChapterFiveRules.clanStart(KaastOutcome.ENDED, FACTION_DEFAULTS) == -150 && ChapterFiveRules.clanStart(null, FACTION_DEFAULTS) == 0,
				msg("Clan Hask's first standing: spared +100, stripped 0, ended -150; none chosen counts as stripped"));

		KaastOutcome[] fates = {KaastOutcome.SPARED, KaastOutcome.STRIPPED, KaastOutcome.ENDED, null};
		ReputationTier[] tiers = {ReputationTier.TRUSTED, ReputationTier.NEUTRAL, ReputationTier.DISTRUSTED, ReputationTier.NEUTRAL};
		List<ServerPlayer> players = new ArrayList<>();

		try {
			for (int i = 0; i < fates.length; i++) {
				ServerPlayer p = at(helper, spot(i), Path.JEDI, ChapterFiveStage.CALLED, fates[i]);
				players.add(p);
				String who = fates[i] == null ? "no fate" : fates[i].id();
				int start = ChapterFiveRules.clanStart(fates[i], FactionM5Balance.get());
				ChapterFiveService.onPlanetArrived(p, Planets.Planet.GHESSAR, true);
				helper.assertTrue(Identity.reputation(p, Faction.CLAN_HASK) == start && Identity.tier(p, Faction.CLAN_HASK) == tiers[i],
						msg(who + ": the clan starts at " + start + " (" + tiers[i] + "), got " + Identity.reputation(p, Faction.CLAN_HASK)));
				helper.assertTrue(Identity.knows(p, Faction.VESSBA_KAJIDIC) && Identity.knows(p, Faction.CLAN_HASK) && story(p).hasFlag(ChapterFiveRules.ARRIVED),
						msg(who + ": both factions are met on the first arrival"));
				helper.assertTrue(c5(p).stage() == ChapterFiveStage.SALTGATE && Identity.reputation(p, Faction.VESSBA_KAJIDIC) == 0,
						msg(who + ": CALLED moves to SALTGATE; the kajidic starts Neutral"));
				ChapterFiveService.onPlanetArrived(p, Planets.Planet.GHESSAR, false);
				helper.assertTrue(Identity.reputation(p, Faction.CLAN_HASK) == start, msg(who + ": the first standing is set once"));
			}

			// Kaast's fate in the clan's asks (6.3, 7).
			helper.assertTrue(condition(players.get(2), "c5:honour_owed", "") == null && condition(players.get(0), "c5:honour_owed", "") != null,
					msg("the duel of honour is owed only for Kaast ended"));
			helper.assertTrue(condition(players.get(1), "c5:helmet_asked", "") == null && condition(players.get(0), "c5:helmet_asked", "") != null,
					msg("the helmet is asked for only when he was stripped"));
			helper.assertTrue(condition(players.get(2), "c5:kaast", "end") == null && condition(players.get(2), "c5:kaast", "spare,strip") != null
					&& condition(players.get(3), "c5:kaast", "spare,strip,end") != null, msg("c5:kaast reads Chapter Four's outcome; none matches nothing"));
		} finally {
			players.forEach(M3TestPlayers::remove);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void helmetReturnedOnce(GameTestHelper helper) {
		StoryData stripped = pure(ChapterFiveStage.SALTGATE);
		stripped.chapter4 = stripped.chapter4.withDuel(duel(KaastOutcome.STRIPPED));
		helper.assertTrue(ChapterFiveRules.helmetAsked(stripped), msg("Varra asks for a stripped Kaast's helmet"));
		stripped.flags.add(ChapterFiveRules.HELMET_RETURNED);
		helper.assertTrue(!ChapterFiveRules.helmetAsked(stripped), msg("not once it is back"));
		StoryData spared = pure(ChapterFiveStage.SALTGATE);
		spared.chapter4 = spared.chapter4.withDuel(duel(KaastOutcome.SPARED));
		helper.assertTrue(!ChapterFiveRules.helmetAsked(spared), msg("a spared Kaast kept his helmet"));

		int gain = FactionM5Balance.get().helmetReturnRep;
		ServerPlayer p = at(helper, spot(0), Path.SITH, ChapterFiveStage.SALTGATE, KaastOutcome.STRIPPED);
		ServerPlayer keeper = at(helper, spot(1), Path.SITH, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);

		try {
			helper.assertTrue(condition(p, "c5:helmet_asked", "") == null && condition(p, "c5:helmet", "") != null, msg("asked for, but not carried: no option"));
			action(p, "c5:helmet");
			helper.assertTrue(!story(p).hasFlag(ChapterFiveRules.HELMET_RETURNED) && Identity.reputation(p, Faction.CLAN_HASK) == 0, msg("nothing to give back"));

			p.getInventory().add(new ItemStack(EquipmentItems.KAAST_HELMET));
			helper.assertTrue(condition(p, "c5:helmet", "") == null, msg("carried: the option shows"));
			action(p, "c5:helmet");
			helper.assertTrue(story(p).hasFlag(ChapterFiveRules.HELMET_RETURNED) && count(p, EquipmentItems.KAAST_HELMET) == 0
					&& Identity.reputation(p, Faction.CLAN_HASK) == gain, msg("given back: Varra keeps it, clan +" + gain));

			p.getInventory().add(new ItemStack(EquipmentItems.KAAST_HELMET));
			action(p, "c5:helmet");
			helper.assertTrue(Identity.reputation(p, Faction.CLAN_HASK) == gain && count(p, EquipmentItems.KAAST_HELMET) == 1,
					msg("once: a second helmet is neither asked for nor taken"));

			keeper.getInventory().add(new ItemStack(EquipmentItems.KAAST_HELMET));
			action(keeper, "c5:helmet");
			helper.assertTrue(count(keeper, EquipmentItems.KAAST_HELMET) == 1 && Identity.reputation(keeper, Faction.CLAN_HASK) == 0,
					msg("Kaast spared: the helmet is not asked for and stays with the player"));
		} finally {
			M3TestPlayers.remove(p);
			M3TestPlayers.remove(keeper);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void tiersGateVendorsAndAttitude(GameTestHelper helper) {
		VendorStock fence = VendorStocks.get(FENCE);
		VendorStock armourer = VendorStocks.get(ARMOURER);
		helper.assertTrue(fence != null && fence.faction().orElse(null) == Faction.VESSBA_KAJIDIC, msg("the fence sells for the kajidic"));
		helper.assertTrue(armourer != null && armourer.faction().orElse(null) == Faction.CLAN_HASK, msg("the armourer sells for the clan"));

		for (Path path : List.of(Path.JEDI, Path.SITH, Path.INDEPENDENT)) {
			helper.assertTrue(!VendorRules.refuses(Faction.VESSBA_KAJIDIC, ReputationTier.NEUTRAL, path) && !VendorRules.refuses(Faction.CLAN_HASK, ReputationTier.NEUTRAL, path),
					msg(path.id() + ": at Neutral the new factions serve any path"));
			helper.assertTrue(VendorRules.refuses(Faction.VESSBA_KAJIDIC, ReputationTier.DISTRUSTED, path) && VendorRules.refuses(Faction.CLAN_HASK, ReputationTier.HOSTILE, path),
					msg(path.id() + ": below Neutral they refuse"));
		}

		VendorEntry trusted = fence.entries().stream().filter(e -> e.tier() == ReputationTier.TRUSTED.ordinal()).findFirst().orElse(null);
		VendorEntry honoured = armourer.entries().stream().filter(e -> e.tier() == ReputationTier.HONOURED.ordinal()).findFirst().orElse(null);
		helper.assertTrue(trusted != null && honoured != null, msg("both stocks have tiered lines"));
		helper.assertTrue(VendorRules.lockReason(trusted, Faction.VESSBA_KAJIDIC, ReputationTier.NEUTRAL, 0, 0) != null
				&& VendorRules.lockReason(trusted, Faction.VESSBA_KAJIDIC, ReputationTier.TRUSTED, 0, 0) == null, msg("the fence's jet pack waits for Trusted"));
		helper.assertTrue(VendorRules.lockReason(honoured, Faction.CLAN_HASK, ReputationTier.TRUSTED, 0, 0) != null
				&& VendorRules.lockReason(honoured, Faction.CLAN_HASK, ReputationTier.HONOURED, 0, 0) == null, msg("the legendary schematics wait for Honoured"));

		// Their people's attitude: the M3 trooper table by tier (W8's StandingRule reads it), Peaceful capped at Neutral.
		helper.assertTrue(TrooperAttitudeRule.attitude(ReputationTier.HOSTILE, false) == Attitude.HOSTILE && TrooperAttitudeRule.attitude(ReputationTier.HOSTILE, true) == Attitude.NEUTRAL
				&& TrooperAttitudeRule.attitude(ReputationTier.DISTRUSTED, false) == Attitude.WARY && TrooperAttitudeRule.attitude(ReputationTier.NEUTRAL, false) == Attitude.NEUTRAL
				&& TrooperAttitudeRule.attitude(ReputationTier.TRUSTED, false) == Attitude.ALLIED, msg("Hostile attacks, Distrusted is Wary, Neutral ignores, Trusted is Allied"));

		ServerPlayer p = at(helper, spot(0), Path.JEDI, ChapterFiveStage.ARMED, KaastOutcome.SPARED);
		Mob seller = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(5, 1, 5));

		try {
			Identity.setReputation(p, Faction.VESSBA_KAJIDIC, -150);
			helper.assertTrue(Identity.tier(p, Faction.VESSBA_KAJIDIC) == ReputationTier.DISTRUSTED
					&& TrooperAttitudeRule.attitude(Identity.tier(p, Faction.VESSBA_KAJIDIC), false) == Attitude.WARY, msg("-150 with the kajidic: Wary enforcers"));
			Vendors.open(p, seller, id(FENCE));
			helper.assertTrue(Vendors.openStock(p) == null, msg("Wary: the fence turns the player away"));
			helper.assertTrue(condition(p, "reputation_min", SHIPWRIGHT_NEUTRAL) != null, msg("Wary: the shipwright's paid work is closed"));
			helper.assertTrue(condition(p, "c5:cannons", "") == null, msg("but the story's free cannons ignore standing"));

			Identity.setReputation(p, Faction.VESSBA_KAJIDIC, 0);
			Vendors.open(p, seller, id(FENCE));
			helper.assertTrue(FENCE.equals(Vendors.openStock(p)), msg("Neutral: the fence opens, got " + Vendors.openStock(p)));
			helper.assertTrue(condition(p, "reputation_min", SHIPWRIGHT_NEUTRAL) == null, msg("Neutral: the shipwright's paid work opens"));
		} finally {
			seller.discard();
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void standingRowsOnlyWhenMet(GameTestHelper helper) {
		List<Faction> first = List.of(Faction.JEDI_ORDER, Faction.SITH_EMPIRE, Faction.REPUBLIC, Faction.FRONTIER);
		helper.assertTrue(IdentityRules.standingRows(new IdentityData()).equals(first), msg("a new player sees the four factions of Milestones 1 to 4, in order"));
		ServerPlayer p = at(helper, spot(0), Path.INDEPENDENT, ChapterFiveStage.CALLED, KaastOutcome.SPARED);

		try {
			helper.assertTrue(IdentityRules.standingRows(Identity.get(p)).equals(first) && !Identity.knows(p, Faction.VESSBA_KAJIDIC) && !Identity.knows(p, Faction.CLAN_HASK),
					msg("before Saltgate: four rows"));
			ChapterFiveService.onPlanetArrived(p, Planets.Planet.GHESSAR, true);
			List<Faction> rows = IdentityRules.standingRows(Identity.get(p));
			helper.assertTrue(rows.equals(List.of(Faction.JEDI_ORDER, Faction.SITH_EMPIRE, Faction.REPUBLIC, Faction.FRONTIER, Faction.VESSBA_KAJIDIC, Faction.CLAN_HASK)),
					msg("after the first arrival both new factions follow, got " + rows));
			helper.assertTrue(Identity.hasChoice(p, IdentityRules.metChoice(Faction.VESSBA_KAJIDIC)) && Identity.hasChoice(p, IdentityRules.metChoice(Faction.CLAN_HASK)),
					msg("meeting a faction is an identity choice, so it is saved"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void contractsValidateOnNewPlanets(GameTestHelper helper) {
		UUID player = new UUID(0x1234_5678_9ABCL, 0x0FED_CBA9_8765L);

		for (Planets.Planet planet : List.of(Planets.Planet.GHESSAR, Planets.Planet.TYRVANN)) {
			boolean ghessar = planet == Planets.Planet.GHESSAR;
			helper.assertTrue(PlanetContracts.issuer(planet) == (ghessar ? Faction.VESSBA_KAJIDIC : Faction.CLAN_HASK), msg(planet.id() + "'s board is its hub's faction's"));
			List<String> templates = ghessar ? PlanetContracts.GHESSAR_TEMPLATES : PlanetContracts.TYRVANN_TEMPLATES;
			List<String> types = PlanetContracts.types(planet);
			helper.assertTrue(types.size() == 6 && PlanetContracts.templates(planet).equals(templates), msg(planet.id() + " offers six contracts"));

			for (String type : types) {
				MissionDefinition def = definition(helper, type);
				String template = PlanetContracts.template(def);
				boolean space = "run".equals(template) || "patrol".equals(template) || SpaceContracts.BOUNTY.equals(template);
				helper.assertTrue(def.isPlanetContract() && planet.id().equals(def.planet()) && def.kind() == Kind.CONTRACT && templates.contains(template)
						&& def.objective() != null, msg(type + " is a contract of " + planet.id()));
				helper.assertTrue(SpaceContracts.isSpace(def) == space && SpaceContracts.isBounty(def) == SpaceContracts.BOUNTY.equals(template),
						msg(type + (space ? " is flown in space" : " is worked on the ground")));
			}

			List<MissionRules.Offer> offers = MissionRules.offers((int) DAY, player, TERMINAL, planet.level(), types, t -> true, i -> false,
					MissionFeature.balance().offersPerBoard);
			helper.assertTrue(!offers.isEmpty() && offers.equals(MissionRules.offers((int) DAY, player, TERMINAL, planet.level(), types, t -> true, i -> false,
					MissionFeature.balance().offersPerBoard)), msg(planet.id() + ": the same day and board roll the same offers"));
			helper.assertTrue(offers.stream().allMatch(o -> types.contains(o.type())), msg(planet.id() + ": every offer is one of its own"));
		}

		int gain = FactionM5Balance.get().contractRep;
		helper.assertTrue(FactionStanding.contractRep(Faction.VESSBA_KAJIDIC, 999) == gain && FactionStanding.contractRep(Faction.CLAN_HASK, 999) == gain
				&& FactionStanding.contractRep(Faction.JEDI_ORDER, 999) == 999 && FactionStanding.contractRep(null, 7) == 7,
				msg("the new factions' contract gain is factions_m5.contractRep; the others keep theirs"));

		// The chapter's own story missions.
		for (String id : List.of(ChapterFiveRules.DEBT, ChapterFiveRules.CARGO, ChapterFiveRules.MATRIARCH_HUNT, ChapterFiveRules.HONOUR_DUEL,
				ChapterFiveRules.COUNTING_HOUSE_RUN)) {
			MissionDefinition def = definition(helper, id);
			helper.assertTrue(def.kind() == Kind.STORY && def.driven() && ChapterFiveRules.isChapterMission(id) && def.objective() != null,
					msg(id + " is a driven story mission"));
		}

		helper.assertTrue(definition(helper, ChapterFiveRules.CARGO).objective() == MissionDefinition.Objective.SPACE_RUN
				&& definition(helper, ChapterFiveRules.MATRIARCH_HUNT).objective() == MissionDefinition.Objective.CLEAR
				&& definition(helper, ChapterFiveRules.COUNTING_HOUSE_RUN).objective() == MissionDefinition.Objective.CLEAR,
				msg("the cargo is flown; the hunt and the counting-house are cleared"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void spaceObjectivesGoThroughSpaceMissions(GameTestHelper helper) {
		helper.assertTrue(SpaceContracts.isSpace(definition(helper, ChapterFiveRules.CARGO)) && SpaceContracts.isSpace(definition(helper, "ghessar_run"))
				&& SpaceContracts.isSpace(definition(helper, "tyrvann_patrol")) && !SpaceContracts.isSpace(definition(helper, "ghessar_cull"))
				&& !SpaceContracts.isSpace(null), msg("space_run and space_clear objectives are flown"));
		helper.assertTrue(SpaceContracts.isBounty(definition(helper, "ghessar_bounty")) && SpaceContracts.isBounty(definition(helper, "tyrvann_bounty"))
				&& !SpaceContracts.isBounty(definition(helper, "ghessar_run")) && !SpaceContracts.isBounty(definition(helper, ChapterFiveRules.CARGO)),
				msg("the bounties are the boards' bounty template"));

		ChapterFiveBalance b = ChapterFiveService.balance();
		ServerPlayer clean = at(helper, spot(0), Path.JEDI, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		ServerPlayer scanned = at(helper, spot(1), Path.SITH, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		ServerPlayer runner = at(helper, spot(2), Path.INDEPENDENT, ChapterFiveStage.COMPLETE, KaastOutcome.SPARED);
		ServerPlayer idle = at(helper, spot(3), Path.JEDI, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		List<ServerPlayer> players = List.of(clean, scanned, runner, idle);

		try {
			ChapterFiveService.onSpaceObjectiveDone(idle, "ghessar_run", true);
			ChapterFiveService.onSpaceObjectiveDone(idle, ChapterFiveRules.CARGO, true);
			helper.assertTrue(MissionService.activeId(idle) == null && !story(idle).hasFlag(ChapterFiveRules.JOB_CARGO), msg("a report with no mission in hand pays nothing"));

			// The quiet cargo: W2 flies it and reports the end (here the mission is put in hand as its start leaves it).
			give(clean, ChapterFiveRules.CARGO, Kind.STORY, Faction.VESSBA_KAJIDIC);
			long xp = totalXp(clean);
			ChapterFiveService.onSpaceObjectiveDone(clean, ChapterFiveRules.CARGO, true);
			helper.assertTrue(MissionService.activeId(clean) == null && MissionService.get(clean).storyDone(ChapterFiveRules.CARGO)
					&& story(clean).hasFlag(ChapterFiveRules.JOB_CARGO), msg("a clean run completes the cargo job"));
			helper.assertTrue(totalXp(clean) - xp == b.jobXp && Identity.reputation(clean, Faction.VESSBA_KAJIDIC) == b.jobRep,
					msg("the job pays " + b.jobXp + " XP and kajidic +" + b.jobRep));

			give(scanned, ChapterFiveRules.CARGO, Kind.STORY, Faction.VESSBA_KAJIDIC);
			ChapterFiveService.onSpaceObjectiveDone(scanned, ChapterFiveRules.CARGO, false);
			helper.assertTrue(MissionService.activeId(scanned) == null && !MissionService.get(scanned).storyDone(ChapterFiveRules.CARGO)
					&& !story(scanned).hasFlag(ChapterFiveRules.JOB_CARGO), msg("a scan or a lost crate drops the job, to be taken again"));

			// A board's run: paid by the contract rules.
			give(runner, "ghessar_run", Kind.CONTRACT, Faction.VESSBA_KAJIDIC);
			ChapterFiveService.onSpaceObjectiveDone(runner, "tyrvann_patrol", true);
			helper.assertTrue("ghessar_run".equals(MissionService.activeId(runner)), msg("another mission's report leaves the run in hand"));
			ChapterFiveService.onSpaceObjectiveDone(runner, "ghessar_run", true);
			helper.assertTrue(MissionService.activeId(runner) == null && Identity.reputation(runner, Faction.VESSBA_KAJIDIC) == FactionM5Balance.get().contractRep
					&& story(runner).counter(ChapterFive.CONTRACTS) == 1, msg("the run is paid as a Ghessar contract and counts as one"));
		} finally {
			for (ServerPlayer p : players) {
				MissionService.abandon(p);
				M3TestPlayers.remove(p);
			}
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void bountyOncePerBoardPerDayOutsideSoftCap(GameTestHelper helper) {
		StoryData data = new StoryData();
		helper.assertTrue(ChapterFiveRules.bountyDue(data, "ghessar", DAY), msg("a board's first bounty is due"));
		data.counters.put(ChapterFiveRules.BOUNTY_DAY_PREFIX + "ghessar", (int) DAY);
		helper.assertTrue(!ChapterFiveRules.bountyDue(data, "ghessar", DAY) && ChapterFiveRules.bountyDue(data, "ghessar", DAY + 1)
				&& ChapterFiveRules.bountyDue(data, "tyrvann", DAY), msg("one per board per day; the other board and the next day are free"));

		FactionM5Balance fb = FactionM5Balance.get();
		MissionBalance mb = MissionFeature.balance();
		ServerPlayer p = at(helper, spot(0), Path.SITH, ChapterFiveStage.COMPLETE, KaastOutcome.SPARED);

		try {
			MissionData d = MissionService.get(p);
			d.contractDay = (int) DAY;
			d.contractsToday = mb.fullPayPerDay;
			MissionService.commit(p, d);
			give(p, "ghessar_bounty", Kind.CONTRACT, Faction.VESSBA_KAJIDIC);
			long xp = totalXp(p);
			int before = credits(p);
			MissionService.complete(p, null, (int) DAY);
			helper.assertTrue(MissionService.activeId(p) == null && totalXp(p) - xp == fb.bountyXp && credits(p) - before == fb.bountyCredits,
					msg("past the day's soft cap a bounty still pays in full: " + fb.bountyXp + " XP and " + fb.bountyCredits + " credits, got "
							+ (totalXp(p) - xp) + " XP and " + (credits(p) - before)));
			helper.assertTrue(Identity.reputation(p, Faction.VESSBA_KAJIDIC) == fb.bountyRep, msg("+" + fb.bountyRep + " with the board's faction"));
			helper.assertTrue(MissionService.get(p).contractsToday == mb.fullPayPerDay, msg("a bounty does not count toward the soft cap"));
			helper.assertTrue(story(p).counter(ChapterFive.BOUNTIES) == 1 && ChapterFive.deeds(p).bounties() == 1, msg("the bounty deed counts"));
			helper.assertTrue(!FactionStanding.bountyDue(p, "ghessar") && FactionStanding.bountyDue(p, "tyrvann"),
					msg("Ghessar's bounty is taken for today; Tyrvann's is not"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void deedsCounters(GameTestHelper helper) {
		ServerPlayer p = at(helper, spot(0), Path.SITH, ChapterFiveStage.THE_HYMNAL, KaastOutcome.SPARED);

		try {
			ChapterFive.Deeds none = ChapterFive.deeds(p);
			helper.assertTrue(none.bounties() == 0 && none.contracts() == 0 && !none.writ(), msg("no deeds yet"));
			FactionStanding.onContractDone(p, Planets.Planet.GHESSAR);
			FactionStanding.onContractDone(p, Planets.Planet.TYRVANN);
			FactionStanding.onContractDone(p, Planets.Planet.SELVAREN);
			FactionStanding.onContractDone(p, Planets.Planet.HOME);
			FactionStanding.onContractDone(p, null);
			helper.assertTrue(ChapterFive.deeds(p).contracts() == 2, msg("only Ghessar's and Tyrvann's contracts count, got " + ChapterFive.deeds(p).contracts()));

			give(p, "tyrvann_hunt", Kind.CONTRACT, Faction.CLAN_HASK);
			MissionService.complete(p, null, (int) DAY);
			helper.assertTrue(ChapterFive.deeds(p).contracts() == 3 && Identity.reputation(p, Faction.CLAN_HASK) == FactionM5Balance.get().contractRep,
					msg("a finished Tyrvann contract counts, and pays clan +" + FactionM5Balance.get().contractRep));

			FactionStanding.onBountyDone(p, "tyrvann", DAY);
			helper.assertTrue(ChapterFive.deeds(p).bounties() == 1, msg("a space bounty counts"));

			ChapterFiveService.applyUlvaineChoice(p, BossOutcome.BOUND);
			helper.assertTrue(ChapterFive.deeds(p).writ() && count(p, VendorItems.ULVAINES_WRIT) == 1, msg("binding her as a Sith gives the writ"));
			removeAll(p, VendorItems.ULVAINES_WRIT);
			helper.assertTrue(ChapterFive.deeds(p).writ(), msg("the deed is a flag: a lost keepsake does not lose it"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void newFactionKillLossesCapped(GameTestHelper helper) {
		IdentityBalance ib = Identity.balance();
		ServerPlayer p = M3TestPlayers.listed(helper, spot(0));
		Mob victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(5, 1, 5));

		try {
			int republic = Identity.reputation(p, Faction.REPUBLIC);
			int kills = ib.killRepDailyCap / Math.max(1, -ib.hostileKillRep) + 5;

			for (int i = 0; i < kills; i++) {
				ChapterFiveService.onFoeKilled(p, victim, "oldrepublic:cartel_enforcer", Faction.VESSBA_KAJIDIC, true);
			}

			helper.assertTrue(Identity.reputation(p, Faction.VESSBA_KAJIDIC) == -ib.killRepDailyCap,
					msg(kills + " hostile enforcers: the day's loss stops at " + ib.killRepDailyCap + ", got " + Identity.reputation(p, Faction.VESSBA_KAJIDIC)));
			helper.assertTrue(codex(p, JournalEntries.CODEX_CARTEL_ENFORCERS), msg("an enforcer opens its codex page"));
			ChapterFiveService.onFoeKilled(p, victim, "oldrepublic:cartel_enforcer", Faction.VESSBA_KAJIDIC, false);
			helper.assertTrue(Identity.reputation(p, Faction.VESSBA_KAJIDIC) == -ib.killRepDailyCap + ib.neutralKillRep,
					msg("a non-hostile kill costs " + ib.neutralKillRep + ", beyond the cap"));
			ChapterFiveService.onFoeKilled(p, victim, "oldrepublic:hask_warrior", Faction.CLAN_HASK, true);
			helper.assertTrue(Identity.reputation(p, Faction.CLAN_HASK) == ib.hostileKillRep, msg("the clan has its own cap"));
			ChapterFiveService.onFoeKilled(p, victim, "oldrepublic:sand_scour", null, true);
			ChapterFiveService.onFoeKilled(p, victim, "oldrepublic:hymnsworn", Faction.REPUBLIC, true);
			helper.assertTrue(codex(p, JournalEntries.CODEX_SAND_SCOURS) && codex(p, JournalEntries.CODEX_HYMNSWORN) && Identity.reputation(p, Faction.REPUBLIC) == republic
					&& Identity.reputation(p, Faction.CLAN_HASK) == ib.hostileKillRep, msg("beasts and the Hymnsworn open their pages and cost no standing"));
		} finally {
			victim.discard();
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void debtChoicesPayBribeOrForce(GameTestHelper helper) {
		ChapterFiveBalance b = ChapterFiveService.balance();
		String debt = ChapterFiveRules.DEBT;
		ServerPlayer payer = at(helper, spot(0), Path.JEDI, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		ServerPlayer briber = at(helper, spot(1), Path.SITH, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		ServerPlayer trickster = at(helper, spot(2), Path.JEDI, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		ServerPlayer quitter = at(helper, spot(3), Path.INDEPENDENT, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		ServerPlayer forcer = at(helper, spot(4), Path.SITH, ChapterFiveStage.SALTGATE, KaastOutcome.SPARED);
		List<ServerPlayer> players = List.of(payer, briber, trickster, quitter, forcer);

		try {
			for (ServerPlayer p : players) {
				action(p, "c5:mission", debt);
				helper.assertTrue(debt.equals(MissionService.activeId(p)) && condition(p, "c5:debt", "choice") == null,
						msg("Teel's debt is taken; the debtor waits for the choice"));
			}

			// Pay his debt from the player's own credits.
			action(payer, "c5:debt", "pay");
			helper.assertTrue(!story(payer).hasFlag(ChapterFiveRules.DEBT_PREFIX + "paid"), msg("without " + b.debtCredits + " credits the debt cannot be paid"));
			Credits.give(payer, b.debtCredits);
			action(payer, "c5:debt", "pay");
			helper.assertTrue(story(payer).hasFlag(ChapterFiveRules.DEBT_PREFIX + "paid") && credits(payer) == 0 && condition(payer, "c5:debt", "box") == null,
					msg("paid: the box goes back to Teel"));
			action(payer, "c5:debt", "force");
			helper.assertTrue(!story(payer).hasFlag(ChapterFiveRules.DEBT_PREFIX + "force"), msg("the debtor's choice is made once"));
			action(payer, "c5:debt_claim", "bribe");
			helper.assertTrue(debt.equals(MissionService.activeId(payer)), msg("with the box in hand there is no story to bribe for"));
			long xp = totalXp(payer);
			action(payer, "c5:debt_claim", "box");
			helper.assertTrue(MissionService.activeId(payer) == null && story(payer).hasFlag(ChapterFiveRules.JOB_DEBT) && totalXp(payer) - xp == b.jobXp
					&& credits(payer) == b.jobCredits && Identity.reputation(payer, Faction.VESSBA_KAJIDIC) == b.jobRep,
					msg("the box handed over: the job pays " + b.jobXp + " XP, " + b.jobCredits + " credits, kajidic +" + b.jobRep));
			action(payer, "c5:mission", debt);
			helper.assertTrue(MissionService.activeId(payer) == null, msg("the debt is done once"));

			// Let him run, then bribe Teel.
			action(briber, "c5:debt", "run");
			helper.assertTrue(condition(briber, "c5:debt", "ran") == null && condition(briber, "c5:debt", "box") != null, msg("he ran: no box for Teel"));
			action(briber, "c5:debt_claim", "box");
			helper.assertTrue(debt.equals(MissionService.activeId(briber)), msg("there is no box to hand over"));
			action(briber, "c5:debt_claim", "bribe");
			helper.assertTrue(debt.equals(MissionService.activeId(briber)), msg("a bribe needs " + b.teelBribe + " credits"));
			Credits.give(briber, b.teelBribe);
			action(briber, "c5:debt_claim", "bribe");
			helper.assertTrue(MissionService.activeId(briber) == null && story(briber).hasFlag(ChapterFiveRules.JOB_DEBT) && credits(briber) == b.jobCredits,
					msg("Teel takes the bribe and believes the empty box"));

			// Let him run, then a Mind Trick on Teel.
			learn(trickster, MIND_TRICK);
			action(trickster, "c5:debt", "run");
			action(trickster, "c5:debt_claim", "trick");
			helper.assertTrue(MissionService.activeId(trickster) == null && story(trickster).hasFlag(ChapterFiveRules.JOB_DEBT) && Progression.isOnCooldown(trickster, MIND_TRICK),
					msg("a Mind Trick convinces Teel"));

			// Let him run, then give up: the job is dropped and may be taken again from the start.
			action(quitter, "c5:debt", "run");
			action(quitter, "c5:debt_claim", "back");
			helper.assertTrue(MissionService.activeId(quitter) == null && !story(quitter).hasFlag(ChapterFiveRules.DEBT_PREFIX + "ran")
					&& !MissionService.get(quitter).storyDone(debt), msg("dropped, not done"));
			action(quitter, "c5:mission", debt);
			helper.assertTrue(debt.equals(MissionService.activeId(quitter)) && condition(quitter, "c5:debt", "choice") == null, msg("taken again from the start"));

			// Take the box by force: his salvagers fight at the camp (on Ghessar), so here the choice is only recorded.
			action(forcer, "c5:debt", "force");
			helper.assertTrue(story(forcer).hasFlag(ChapterFiveRules.DEBT_PREFIX + "force") && debt.equals(MissionService.activeId(forcer))
					&& condition(forcer, "c5:debt", "choice") != null && condition(forcer, "c5:debt", "box") != null, msg("force: the box must be fought for"));
		} finally {
			for (ServerPlayer p : players) {
				MissionService.abandon(p);
				M3TestPlayers.remove(p);
			}
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void ulvaineHelperPaidOncePerDay(GameTestHelper helper) {
		StoryData data = new StoryData();
		helper.assertTrue(ChapterFiveRules.helperDue(data, DAY), msg("a helper's first reward is due"));
		data.counters.put(ChapterFiveRules.HELPER_DAY, (int) DAY);
		helper.assertTrue(!ChapterFiveRules.helperDue(data, DAY) && ChapterFiveRules.helperDue(data, DAY + 1), msg("once per in-game day"));

		ChapterFiveBalance b = ChapterFiveService.balance();
		int helperXp = UlvaineBalance.get().helperXp;
		long today = Clocks.day(helper.getLevel().getServer());
		ServerPlayer owner = at(helper, spot(0), Path.JEDI, ChapterFiveStage.THE_HYMNAL, KaastOutcome.SPARED);
		ServerPlayer friend = at(helper, spot(1), Path.SITH, ChapterFiveStage.COMPLETE, KaastOutcome.SPARED);
		ServerPlayer echo = at(helper, spot(2), Path.INDEPENDENT, ChapterFiveStage.COMPLETE, KaastOutcome.SPARED);
		List<ServerPlayer> players = List.of(owner, friend, echo);

		try {
			long xp = totalXp(friend);
			ChapterFiveService.onBossDefeated(friend, "ulvaine", BossOutcome.SPARED, owner.getUUID(), false);
			helper.assertTrue(totalXp(friend) - xp == helperXp && story(friend).counter(ChapterFiveRules.HELPER_DAY) == (int) today,
					msg("a helper is paid " + helperXp + " XP and today is marked"));
			helper.assertTrue(!story(friend).hasFlag(ChapterFiveRules.ULVAINE_PAID), msg("a helper never gets the owner's reward"));
			ChapterFiveService.onBossDefeated(friend, "ulvaine", BossOutcome.EXECUTED, owner.getUUID(), false);
			helper.assertTrue(totalXp(friend) - xp == helperXp, msg("a second fight the same day pays a helper nothing"));

			long echoXp = totalXp(echo);
			ChapterFiveService.onBossDefeated(echo, "ulvaine", BossOutcome.ECHO, owner.getUUID(), false);
			ChapterFiveService.onBossDefeated(echo, "kaast", BossOutcome.SPARED, owner.getUUID(), false);
			helper.assertTrue(totalXp(echo) == echoXp && !story(echo).counters.containsKey(ChapterFiveRules.HELPER_DAY),
					msg("her echo (W8 pays it) and other bosses pay nothing here"));

			long ownerXp = totalXp(owner);
			ChapterFiveService.onBossDefeated(owner, "ulvaine", BossOutcome.SPARED, owner.getUUID(), true);
			helper.assertTrue(totalXp(owner) - ownerXp == b.ulvaineXp && story(owner).hasFlag(ChapterFiveRules.ULVAINE_PAID)
					&& !story(owner).counters.containsKey(ChapterFiveRules.HELPER_DAY), msg("the owner gets the fight's own reward, not a helper's"));
			ChapterFiveService.onBossDefeated(owner, "ulvaine", BossOutcome.SPARED, owner.getUUID(), true);
			helper.assertTrue(totalXp(owner) - ownerXp == b.ulvaineXp, msg("the owner's reward is paid once"));
		} finally {
			players.forEach(M3TestPlayers::remove);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- Peaceful and free flight off (6.9)

	@GameTest(maxTicks = 20)
	public void peacefulRules(GameTestHelper helper) {
		helper.assertTrue(ChapterFiveRules.scriptedPass(true, true) && ChapterFiveRules.scriptedPass(false, false) && !ChapterFiveRules.scriptedPass(false, true),
				msg("the blockade is the scripted pass on Peaceful or with free flight off"));
		helper.assertTrue(ChapterFiveRules.clearsOnArrival(DEFAULTS.matriarchBrood, true) && !ChapterFiveRules.clearsOnArrival(DEFAULTS.matriarchBrood, false),
				msg("on Peaceful a clear mission (the matriarch, the counting-house) completes on arrival"));
		helper.assertTrue(definition(helper, ChapterFiveRules.MATRIARCH_HUNT).objective().combat() && definition(helper, ChapterFiveRules.COUNTING_HOUSE_RUN).objective().combat(),
				msg("both are combat objectives (the M3 Peaceful rule)"));
		helper.assertTrue(ChapterFiveRules.jobsNeeded(false) == 1 && ChapterFiveRules.jobsNeeded(true) == 2, msg("without free flight the debt alone is the kajidic's work"));

		StoryData jobs = pure(ChapterFiveStage.SALTGATE);
		jobs.flags.add(ChapterFiveRules.JOB_DEBT);
		helper.assertTrue(ChapterFiveRules.audienceReady(jobs, false) && !ChapterFiveRules.audienceReady(jobs, true), msg("the debt alone opens the audience only without flight"));
		jobs.flags.add(ChapterFiveRules.JOB_CARGO);
		helper.assertTrue(ChapterFiveRules.jobsDone(jobs, false) == 1 && ChapterFiveRules.jobsDone(jobs, true) == 2 && ChapterFiveRules.audienceReady(jobs, true),
				msg("a cargo run counts only with flight"));
		List<ChapterFiveRules.Step> grounded = ChapterFiveRules.steps(jobs, false, false, DEFAULTS);
		helper.assertTrue(grounded.size() == 3 && grounded.stream().noneMatch(s -> s.line().key().endsWith(".helm") || s.line().key().endsWith(".orbit")
				|| s.line().key().endsWith(".jump")), msg("without free flight the journal shows the three ways and no helm lesson"));
		helper.assertTrue(ChapterFiveRules.steps(pure(ChapterFiveStage.CALLED), true, false, DEFAULTS).size() == 3, msg("with it, CALLED lists the three helm steps"));

		// Ulvaine on Peaceful talks and kneels at once (W8 calls the yield): the choice still opens for the owner.
		ServerPlayer owner = at(helper, spot(0), Path.SITH, ChapterFiveStage.THE_HYMNAL, KaastOutcome.SPARED);

		try {
			ChapterFiveService.onBossYielded(owner, "ulvaine");
			ChapterFiveService.onBossYielded(owner, "ulvaine");
			helper.assertTrue(ChapterFiveRules.choiceOpen(story(owner)) && Dialogues.session(owner) != null, msg("a yield, however reached, opens the choice once"));
			action(owner, "ulvaine:resolve", "bind");
			helper.assertTrue(c5(owner).stage() == ChapterFiveStage.AFTERMATH && ChapterFive.ulvaineOutcome(owner) == BossOutcome.BOUND, msg("bound: AFTERMATH"));
			ChapterFiveService.onBossYielded(owner, "ulvaine");
			helper.assertTrue(!ChapterFiveRules.choiceOpen(story(owner)) && c5(owner).boss() == ChapterFiveRules.BOSS_RESOLVED, msg("a late yield reopens nothing"));
		} finally {
			Dialogues.close(owner);
			M3TestPlayers.remove(owner);
		}

		helper.succeed();
	}
}
