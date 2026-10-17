package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.holocron.HolocronData;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Guidance;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.story.ChapterThreeBalance;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeService;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.RankThree;
import com.oldrepublic.quest.story.SendWord;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryAttachments;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryRules;
import com.oldrepublic.registry.M3Items;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

/**
 * Chapter Three game tests (spec 17.1, W2): the stage machine and its saved form, leads and the
 * level gate, the first echo and its fallbacks, the late join, co-op stances, rank 3 and the
 * Pathfinder, the report, and the Collector's rewards. The eight contract and kill-reputation cases
 * live in {@code MissionGameTests} and are declared here as delegations (W2 plan 4.9).
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks} (at most 400); players are listed mock
 * players, removed at the end; the default empty structure (no fixture needed); the daily rules are
 * pure functions given the day; nothing writes world, global or server state: all state lives on
 * the mock players. Everything runs synchronously in the first tick, before the story's own tick
 * can touch the players.
 */
public class StoryM3GameTests {
	private static final ChapterThreeBalance B = new ChapterThreeBalance();
	private static final String COLLECTOR = "oldrepublic:orren_vasch";
	private static final String RANK3_REQ = "message.oldrepublic.story.rank3.";

	// ---------------------------------------------------------------- helpers

	private static Component msg(String text) {
		return Component.literal(text);
	}

	/** A listed player on {@code path} (story and identity) at Chapter Three {@code stage}. */
	private static ServerPlayer at(GameTestHelper helper, BlockPos relative, Path path, ChapterThreeStage stage) {
		ServerPlayer p = M3TestPlayers.listed(helper, relative);
		IdentityData identity = Identity.get(p);
		identity.path = path;
		Identity.commit(p, identity);
		StoryData data = Story.get(p);
		data.path = path.id();
		data.pathStage = (path == Path.JEDI || path == Path.SITH) ? PathStage.COMPLETE.ordinal() : data.pathStage;
		data.chapter3 = ChapterThreeRules.withStage(data.chapter3, stage);
		Story.commit(p, data);
		return p;
	}

	private static ServerPlayer at(GameTestHelper helper, Path path, ChapterThreeStage stage) {
		return at(helper, new BlockPos(1, 1, 1), path, stage);
	}

	private static StoryData story(ServerPlayer p) {
		return Story.get(p);
	}

	private static void flag(ServerPlayer p, String flag) {
		StoryData data = Story.get(p);
		data.flags.add(flag);
		Story.commit(p, data);
	}

	private static void attune(ServerPlayer p, String... ids) {
		HolocronData data = HolocronService.data(p);

		for (String id : ids) {
			data.attuned.add(id);
		}

		HolocronService.commit(p, data);
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

	private static String condition(ServerPlayer p, String id, String arg) {
		DialogueConditions.Condition c = DialogueConditions.get(id);

		if (c == null) {
			throw new IllegalStateException("No dialogue condition " + id);
		}

		return c.failReason(p, null, arg);
	}

	/** A rank 3 standing: everything met for {@code path}, as a base the matrix changes one value of. */
	private static ChapterThreeRules.Standing ready(Path path) {
		int alignment = path == Path.SITH ? -B.rank3Alignment : B.rank3Alignment;
		return new ChapterThreeRules.Standing(path, ChapterThreeStage.COMPLETE, B.rank3MinLevel, B.rank3MinTrust, B.rank3MinRep, alignment,
				B.rank3Holocrons, Math.max(B.rank3Holocrons, B.pathfinderHolocrons), B.pathfinderTier, Math.max(B.rank3Contracts, B.pathfinderContracts));
	}

	private static ChapterThreeRules.Standing with(ChapterThreeRules.Standing s, ChapterThreeStage stage, int level, int trust, int ownRep, int alignment,
			int ownHolocrons, int anyHolocrons, int bestRep, int contracts) {
		return new ChapterThreeRules.Standing(s.path(), stage, level, trust, ownRep, alignment, ownHolocrons, anyHolocrons, bestRep, contracts);
	}

	// ---------------------------------------------------------------- stages and saves

	@GameTest(maxTicks = 20)
	public void stagesOnlyMoveForward(GameTestHelper helper) {
		StoryData data = new StoryData();
		helper.assertTrue(ChapterThreeRules.advance(data, ChapterThreeStage.LEADS), msg("NOT_STARTED moves on to LEADS"));
		helper.assertTrue(!ChapterThreeRules.advance(data, ChapterThreeStage.LISTENING), msg("LEADS never goes back to LISTENING"));
		helper.assertTrue(!ChapterThreeRules.advance(data, ChapterThreeStage.LEADS), msg("entering the same stage again changes nothing"));
		helper.assertTrue(data.chapter3.stage() == ChapterThreeStage.LEADS, msg("the stage stays LEADS, got " + data.chapter3.stage()));
		helper.assertTrue(ChapterThreeRules.parseStage(" leads ") == ChapterThreeStage.LEADS && ChapterThreeRules.parseStage("nowhere") == null,
				msg("stage names parse case-insensitively; unknown names are null"));

		ServerPlayer p = at(helper, Path.JEDI, ChapterThreeStage.CONFRONTATION);

		try {
			ChapterThreeService.enterStage(p, ChapterThreeStage.AFTERMATH);
			helper.assertTrue(story(p).chapter3.stage() == ChapterThreeStage.AFTERMATH, msg("enterStage moves forward"));
			ChapterThreeService.enterStage(p, ChapterThreeStage.LEADS);
			ChapterThreeService.enterStage(p, ChapterThreeStage.CONFRONTATION);
			helper.assertTrue(story(p).chapter3.stage() == ChapterThreeStage.AFTERMATH, msg("enterStage never moves back, got " + story(p).chapter3.stage()));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void oldSaveAtCompleteEntersWaiting(GameTestHelper helper) {
		// An M2 save: the Jedi path complete, no chapter3 field at all.
		StoryData m2 = new StoryData();
		m2.path = Path.JEDI.id();
		m2.pathStage = PathStage.COMPLETE.ordinal();
		JsonObject saved = StoryData.CODEC.encodeStart(JsonOps.INSTANCE, m2).getOrThrow().getAsJsonObject();
		saved.remove("chapter3");
		StoryData loaded = StoryData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(loaded.chapter3.stage() == ChapterThreeStage.NOT_STARTED && ChapterThreeRules.shouldWait(loaded),
				msg("an M2 save at COMPLETE loads at NOT_STARTED and should wait"));

		ServerPlayer jedi = M3TestPlayers.listed(helper);
		ServerPlayer independent = M3TestPlayers.listed(helper, new BlockPos(3, 1, 1));
		ServerPlayer midway = M3TestPlayers.listed(helper, new BlockPos(5, 1, 1));

		try {
			Story.commit(jedi, loaded);
			helper.assertTrue(ChapterThreeService.checkWaiting(jedi), msg("the old save starts waiting"));
			StoryData after = story(jedi);
			helper.assertTrue(after.chapter3.stage() == ChapterThreeStage.WAITING && after.chapter3.waitSince() >= 0,
					msg("WAITING with the wait counted from now, got " + after.chapter3));
			helper.assertTrue(!ChapterThreeService.checkWaiting(jedi), msg("a second check changes nothing"));

			StoryData noMaster = Story.get(independent);
			noMaster.path = Path.INDEPENDENT.id();
			noMaster.flags.add(Story.NO_MASTER);
			Story.commit(independent, noMaster);
			helper.assertTrue(ChapterThreeService.checkWaiting(independent) && story(independent).chapter3.stage() == ChapterThreeStage.WAITING,
					msg("an Independent who walked away from both masters waits too"));

			StoryData lesson = Story.get(midway);
			lesson.path = Path.SITH.id();
			lesson.pathStage = PathStage.LESSON_ONE.ordinal();
			Story.commit(midway, lesson);
			helper.assertTrue(!ChapterThreeService.checkWaiting(midway) && story(midway).chapter3.stage() == ChapterThreeStage.NOT_STARTED,
					msg("a Sith still in Chapter Two does not wait"));
		} finally {
			M3TestPlayers.remove(jedi);
			M3TestPlayers.remove(independent);
			M3TestPlayers.remove(midway);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void chapter3CodecRoundTripAndCopyOnDeath(GameTestHelper helper) {
		Set<String> leads = new LinkedHashSet<>(List.of(ChapterThreeRules.LEAD_LEDGER, "holo:jedi_morrane", "mission:r1_silent_patrol"));
		StoryData.Chapter3 full = new StoryData.Chapter3(ChapterThreeStage.AFTERMATH, 1234L, leads,
				List.of(new BlockPos(1, 64, 2), new BlockPos(-3, 70, 9), new BlockPos(8, 66, -4)), 0b101, new BlockPos(100, 70, -20),
				List.of(new BlockPos(300, 60, 300)), new BlockPos(-500, 20, 40), 2, BossOutcome.BOUND.ordinal(), BossOutcome.SPARED.ordinal(), 17, 3);
		Tag nbt = StoryData.Chapter3.CODEC.encodeStart(NbtOps.INSTANCE, full).getOrThrow();
		StoryData.Chapter3 back = StoryData.Chapter3.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
		helper.assertTrue(back.equals(full), msg("every Chapter Three field survives NBT: " + back));
		helper.assertTrue(List.copyOf(back.leads()).equals(List.copyOf(leads)), msg("leads keep their order"));

		StoryData data = new StoryData();
		data.chapter3 = full;
		JsonElement json = StoryData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		helper.assertTrue(StoryData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().chapter3.equals(full), msg("the chapter rides in the story save"));

		helper.assertTrue(StoryAttachments.STORY.copyOnDeath(), msg("the story attachment is copied on death"));
		helper.assertTrue(data.copy().chapter3.equals(full), msg("the copy kept on death carries Chapter Three"));

		ServerPlayer p = at(helper, Path.INDEPENDENT, ChapterThreeStage.LEADS);

		try {
			StoryData mine = Story.get(p);
			mine.chapter3 = full;
			Story.commit(p, mine);
			helper.assertTrue(Story.get(p).chapter3.equals(full) && Story.peek(p) != null && Story.peek(p).chapter3.equals(full),
					msg("commit and read back on the player"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void saveWithoutChapter3Loads(GameTestHelper helper) {
		StoryData m2 = new StoryData();
		m2.path = Path.SITH.id();
		m2.pathStage = PathStage.LESSON_TWO.ordinal();
		m2.flags.add(StoryRules.LEDGER_READ);
		m2.counters.put(StoryRules.PULL_TARGETS, 4);
		m2.chapter3 = ChapterThreeRules.withStage(m2.chapter3, ChapterThreeStage.LEADS);
		JsonObject saved = StoryData.CODEC.encodeStart(JsonOps.INSTANCE, m2).getOrThrow().getAsJsonObject();
		helper.assertTrue(saved.has("chapter3"), msg("a chapter in progress is saved"));
		saved.remove("chapter3");
		StoryData loaded = StoryData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(loaded.chapter3.equals(StoryData.Chapter3.NEW), msg("no chapter3 field loads as NEW"));
		helper.assertTrue(loaded.storyPath() == Path.SITH && loaded.stage() == PathStage.LESSON_TWO && loaded.hasFlag(StoryRules.LEDGER_READ)
				&& loaded.counter(StoryRules.PULL_TARGETS) == 4, msg("everything else of the M2 save is kept"));

		JsonObject partial = new JsonObject();
		partial.addProperty("stage", ChapterThreeStage.LISTENING.ordinal());
		StoryData.Chapter3 sparse = StoryData.Chapter3.CODEC.parse(JsonOps.INSTANCE, partial).getOrThrow();
		helper.assertTrue(sparse.stage() == ChapterThreeStage.LISTENING && sparse.leads().isEmpty() && sparse.fall() == null && sparse.outcome() == -1
				&& sparse.stance() == -1, msg("a sparse chapter3 fills its defaults: " + sparse));
		helper.succeed();
	}

	// ---------------------------------------------------------------- leads and the gate

	@GameTest(maxTicks = 20)
	public void leadsCountOnceAndBeforeLeads(GameTestHelper helper) {
		StoryData capped = new StoryData();

		for (int i = 0; i < StoryData.Chapter3.MAX_LEADS + 4; i++) {
			ChapterThreeRules.addLead(capped, "mission:test_" + i);
		}

		helper.assertTrue(capped.chapter3.leads().size() == StoryData.Chapter3.MAX_LEADS, msg("at most 16 leads are kept"));
		helper.assertTrue(!ChapterThreeRules.addLead(new StoryData(), " "), msg("a blank lead is refused"));

		ServerPlayer p = at(helper, Path.JEDI, ChapterThreeStage.LISTENING);

		try {
			helper.assertTrue(ChapterThreeService.addLead(p, "holo:jedi_morrane"), msg("a lead found before LEADS is kept"));
			helper.assertTrue(!ChapterThreeService.addLead(p, "holo:jedi_morrane"), msg("the same lead counts once"));
			ChapterThreeService.onContractCompleted(p);
			StoryData before = story(p);
			helper.assertTrue(before.chapter3.stage() == ChapterThreeStage.LISTENING && before.chapter3.leads().size() == 1,
					msg("a lead never moves the stage; a contract before LEADS is no lead, got " + before.chapter3.leads()));

			ChapterThreeService.enterStage(p, ChapterThreeStage.LEADS);
			helper.assertTrue(story(p).chapter3.leads().contains("holo:jedi_morrane"), msg("the early lead counts once LEADS begins"));
			ChapterThreeService.onContractCompleted(p);
			ChapterThreeService.onContractCompleted(p);
			StoryData after = story(p);
			helper.assertTrue(after.chapter3.leads().size() == 2 && after.chapter3.leads().contains(ChapterThreeRules.LEAD_CONTRACT),
					msg("contracts give one lead at LEADS, however many are done, got " + after.chapter3.leads()));
			helper.assertTrue(after.chapter3.stage() == ChapterThreeStage.LEADS, msg("two of four leads leave the trail open"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void levelGateKeepsCallingOnAChamberOrBoard(GameTestHelper helper) {
		int needed = B.leadsNeeded;
		int low = B.reliquaryMinLevel - 1;
		helper.assertTrue(ChapterThreeRules.levelGated(needed, low, B) && !ChapterThreeRules.leadsReady(needed, low, B),
				msg("a full trail below the level waits"));
		helper.assertTrue(ChapterThreeRules.leadsReady(needed, B.reliquaryMinLevel, B) && !ChapterThreeRules.levelGated(needed, B.reliquaryMinLevel, B),
				msg("the level reached, the Reliquary opens"));
		helper.assertTrue(!ChapterThreeRules.levelGated(needed - 1, low, B) && !ChapterThreeRules.leadsReady(needed - 1, 30, B),
				msg("an open trail is no gate, at any level"));

		BlockPos chamber = new BlockPos(40, 60, -10);
		BlockPos board = new BlockPos(-90, 70, 15);
		ChapterThreeRules.GateTarget toChamber = ChapterThreeRules.gatedTarget(chamber, HolocronKind.SITH, board, Faction.REPUBLIC.ordinal());
		helper.assertTrue(toChamber != null && toChamber.pos().equals(chamber)
				&& toChamber.callingVariant() == Guidance.variant(QuestEffects.CALLING_HOLOCRON, HolocronKind.SITH.ordinal()),
				msg("the gate's Calling points at an unused chamber first, tinted by its kind"));
		ChapterThreeRules.GateTarget toBoard = ChapterThreeRules.gatedTarget(null, HolocronKind.JEDI, board, Faction.REPUBLIC.ordinal());
		helper.assertTrue(toBoard != null && toBoard.pos().equals(board)
				&& toBoard.callingVariant() == Guidance.variant(QuestEffects.CALLING_OUTPOST, Faction.REPUBLIC.ordinal()),
				msg("with no chamber it points at an outpost board, by army"));
		helper.assertTrue(ChapterThreeRules.gatedTarget(null, HolocronKind.JEDI, null, 0) == null, msg("with neither it points nowhere"));

		StoryData data = new StoryData();
		data.path = Path.JEDI.id();
		data.chapter3 = ChapterThreeRules.withStage(data.chapter3, ChapterThreeStage.LEADS);

		for (int i = 0; i < needed; i++) {
			ChapterThreeRules.addLead(data, "mission:gate_" + i);
		}

		helper.assertTrue("quest.oldrepublic.c3.objective.leads.gated".equals(ChapterThreeRules.objectiveKey(data, low, B)),
				msg("the journal names the gate"));
		helper.assertTrue("quest.oldrepublic.c3.objective.leads".equals(ChapterThreeRules.objectiveKey(data, B.reliquaryMinLevel, B)),
				msg("and the trail once the level is reached"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- first echo and shrines

	@GameTest(maxTicks = 20)
	public void firstEchoFallsBackToShrine(GameTestHelper helper) {
		BlockPos ruin = new BlockPos(200, 60, 200);
		BlockPos shrine = new BlockPos(-120, 64, 80);
		StoryData data = new StoryData();
		int[] placed = {0};

		helper.assertTrue(ruin.equals(ChapterThreeRules.firstEchoTarget(data, ruin, () -> {
			placed[0]++;
			return shrine;
		})) && placed[0] == 0, msg("a found pedestal is the target; no shrine is placed"));

		// The stub locator finds nothing: an Echo Shrine is placed and remembered.
		helper.assertTrue(shrine.equals(ChapterThreeRules.firstEchoTarget(data, null, () -> {
			placed[0]++;
			return shrine;
		})) && placed[0] == 1 && data.chapter3.shrines().equals(List.of(shrine)), msg("nothing found: the shrine is the target and is recorded"));

		StoryData failed = new StoryData();
		helper.assertTrue(ChapterThreeRules.firstEchoTarget(failed, null, () -> null) == null && failed.chapter3.shrines().isEmpty(),
				msg("a failed placement leaves no target (the search retries)"));

		StoryData full = new StoryData();

		for (int i = 0; i < StoryData.Chapter3.MAX_SHRINES; i++) {
			full.chapter3 = ChapterThreeRules.withShrine(full.chapter3, new BlockPos(i * 100, 64, 0));
		}

		BlockPos last = full.chapter3.shrines().getLast();
		helper.assertTrue(last.equals(ChapterThreeRules.firstEchoTarget(full, null, () -> {
			throw new IllegalStateException("no fifth shrine");
		})), msg("with four shrines the last one is reused"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void firstEchoAlreadyMetAdvancesAtOnce(GameTestHelper helper) {
		helper.assertTrue("sith_zarvos".equals(ChapterThreeRules.alreadyMet(Path.SITH, "sith_zarvos"::equals)), msg("a Sith holocron already met"));
		helper.assertTrue(ChapterThreeRules.alreadyMet(Path.JEDI, "sith_zarvos"::equals) == null, msg("another kind is no first echo"));
		helper.assertTrue(ChapterThreeRules.alreadyMet(Path.INDEPENDENT, ChapterThreeRules.WAYFARERS_COMPASS::equals) == null,
				msg("the Compass never counts as the Independent's first echo"));
		helper.assertTrue(ChapterThreeRules.endsFirstEcho(Path.INDEPENDENT, ChapterThreeRules.WAYFARER_PELL)
				&& !ChapterThreeRules.endsFirstEcho(Path.JEDI, "archive_watch"), msg("Pell ends it for an Independent; an Archive holocron never does"));

		ServerPlayer p = at(helper, Path.SITH, ChapterThreeStage.LISTENING);

		try {
			attune(p, "sith_zarvos");
			ChapterThreeService.enterStage(p, ChapterThreeStage.FIRST_ECHO);
			StoryData data = story(p);
			helper.assertTrue(data.chapter3.stage() == ChapterThreeStage.LEADS, msg("FIRST_ECHO ends at once, got " + data.chapter3.stage()));
			helper.assertTrue(data.chapter3.leads().contains(ChapterThreeRules.LEAD_HOLOCRON_PREFIX + "sith_zarvos"), msg("the holocron is a lead"));
			helper.assertTrue(ChapterThreeRules.notes(data, JournalEntries.FIRST_ECHO).contains("quest.oldrepublic.c3.note.careful_man"),
					msg("the careful man goes under the first echo's entry"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void extraShrineOncePerKind(GameTestHelper helper) {
		StoryData data = new StoryData();
		helper.assertTrue(!ChapterThreeRules.extraShrineNeeded(data, HolocronKind.JEDI, 0, 2, B), msg("not before LEADS"));
		data.chapter3 = ChapterThreeRules.withStage(data.chapter3, ChapterThreeStage.LEADS);
		helper.assertTrue(ChapterThreeRules.extraShrineNeeded(data, HolocronKind.JEDI, 1, 2, B), msg("short of the rank 3 holocrons at LEADS"));
		helper.assertTrue(!ChapterThreeRules.extraShrineNeeded(data, HolocronKind.JEDI, 2, 2, B), msg("not once the requirement is met"));

		ChapterThreeRules.recordExtraShrine(data, HolocronKind.JEDI, new BlockPos(10, 64, 10));
		helper.assertTrue(!ChapterThreeRules.extraShrineNeeded(data, HolocronKind.JEDI, 1, 2, B), msg("one extra shrine per kind"));
		helper.assertTrue(ChapterThreeRules.extraShrineNeeded(data, HolocronKind.WAYFARER, 1, 3, B), msg("another kind may still get its own"));
		helper.assertTrue(data.chapter3.shrines().size() == 1, msg("the shrine is remembered"));

		ChapterThreeRules.recordExtraShrine(data, HolocronKind.WAYFARER, new BlockPos(20, 64, 20));
		ChapterThreeRules.recordExtraShrine(data, HolocronKind.SITH, new BlockPos(30, 64, 30));
		data.chapter3 = ChapterThreeRules.withShrine(data.chapter3, new BlockPos(40, 64, 40));
		StoryData other = new StoryData();
		other.chapter3 = ChapterThreeRules.withStage(data.chapter3, ChapterThreeStage.COMPLETE);
		helper.assertTrue(!ChapterThreeRules.extraShrineNeeded(other, HolocronKind.JEDI, 0, 2, B), msg("never beyond four shrines"));

		ChapterThreeBalance none = new ChapterThreeBalance();
		none.extraShrinesPerKind = 0;
		StoryData fresh = new StoryData();
		fresh.chapter3 = ChapterThreeRules.withStage(fresh.chapter3, ChapterThreeStage.RELIQUARY);
		helper.assertTrue(!ChapterThreeRules.extraShrineNeeded(fresh, HolocronKind.SITH, 0, 2, none), msg("the balance can turn the guarantee off"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void lateJoinRefusedFromSummons(GameTestHelper helper) {
		for (ChapterThreeStage stage : ChapterThreeStage.values()) {
			StoryData data = new StoryData();
			data.path = Path.INDEPENDENT.id();
			data.chapter3 = ChapterThreeRules.withStage(data.chapter3, stage);
			boolean refused = stage.ordinal() >= ChapterThreeStage.SUMMONS.ordinal();
			String blocker = StoryRules.joinBlocker(data, Path.JEDI, 0, 0);
			helper.assertTrue(refused == StoryRules.ROAD_CHOSEN.equals(blocker), msg(stage + ": the road-chosen refusal, got " + blocker));
			helper.assertTrue(StoryRules.lateJoin(data, Path.SITH) != refused, msg(stage + ": the late join " + (refused ? "is refused" : "still works")));
			helper.assertTrue(ChapterThreeRules.lateJoinRefused(data) == refused, msg(stage + ": the stage rule agrees"));
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the Collector, co-op and rewards

	@GameTest(maxTicks = 40)
	public void coopHelperJumpsToAftermathWithOwnStance(GameTestHelper helper) {
		ServerPlayer owner = at(helper, new BlockPos(1, 1, 1), Path.JEDI, ChapterThreeStage.CONFRONTATION);
		ServerPlayer helperPlayer = at(helper, new BlockPos(3, 1, 1), Path.SITH, ChapterThreeStage.RELIQUARY);
		ServerPlayer jedi = at(helper, new BlockPos(5, 1, 1), Path.JEDI, ChapterThreeStage.CONFRONTATION);
		ServerPlayer late = at(helper, new BlockPos(7, 1, 1), Path.SITH, ChapterThreeStage.CONFRONTATION);

		try {
			action(jedi, "c3:stance", "bind");
			helper.assertTrue(story(jedi).chapter3.stance() < 0 && story(jedi).chapter3.stage() == ChapterThreeStage.CONFRONTATION,
					msg("a Jedi cannot bind him: no stance, no jump"));

			action(helperPlayer, "c3:stance", "execute");
			StoryData stanced = story(helperPlayer);
			helper.assertTrue(stanced.chapter3.stage() == ChapterThreeStage.AFTERMATH, msg("the helper's chapter jumps to AFTERMATH"));
			helper.assertTrue(stanced.chapter3.stance() == BossOutcome.EXECUTED.ordinal() && Identity.hasChoice(helperPlayer, "collector.execute"),
					msg("their own stance is kept and recorded as their choice"));
			action(helperPlayer, "c3:stance", "spare");
			helper.assertTrue(story(helperPlayer).chapter3.stance() == BossOutcome.EXECUTED.ordinal(), msg("a stance is taken once"));

			// The owner spares him: the world follows the owner, the helper keeps their stance.
			ChapterThreeService.onBossDefeated(owner, COLLECTOR, BossOutcome.SPARED, owner.getUUID(), true);
			ChapterThreeService.onBossDefeated(helperPlayer, COLLECTOR, BossOutcome.SPARED, owner.getUUID(), true);
			StoryData ownerData = story(owner);
			StoryData helperData = story(helperPlayer);
			helper.assertTrue(ownerData.chapter3.stage() == ChapterThreeStage.AFTERMATH && ownerData.chapter3.outcome() == BossOutcome.SPARED.ordinal(),
					msg("the owner's outcome is the world's"));
			helper.assertTrue(helperData.chapter3.outcome() == BossOutcome.SPARED.ordinal() && helperData.chapter3.stance() == BossOutcome.EXECUTED.ordinal(),
					msg("the helper notes the world outcome beside their own stance"));
			helper.assertTrue(ChapterThreeRules.clashes(helperData.chapter3.stance(), helperData.chapter3.outcome()),
					msg("an execution stance clashes with a spared Collector (the report's extra line)"));
			helper.assertTrue(helperData.hasFlag(ChapterThreeRules.COLLECTOR_SP) && ownerData.hasFlag(ChapterThreeRules.COLLECTOR_SP),
					msg("both earn the first-clear rewards"));
			helper.assertTrue(ChapterThreeRules.notes(helperData, JournalEntries.THE_COLLECTOR).contains("quest.oldrepublic.c3.note.fought_beside"),
					msg("the helper's journal says whom they fought beside"));
			helper.assertTrue(ChapterThreeRules.coopStage(ChapterThreeStage.RELIQUARY) && !ChapterThreeRules.coopStage(ChapterThreeStage.LEADS),
					msg("only helpers at RELIQUARY or CONFRONTATION take a stance"));

			// A helper still choosing when the owner resolves: credited in the same tick, their stance stays open once.
			ChapterThreeService.onBossDefeated(late, COLLECTOR, BossOutcome.SPARED, owner.getUUID(), true);
			helper.assertTrue(story(late).chapter3.stage() == ChapterThreeStage.AFTERMATH && story(late).hasFlag(ChapterThreeRules.COOP_HELPER),
					msg("the late helper is credited and moves to AFTERMATH"));
			action(late, "c3:stance", "bind");
			StoryData lateData = story(late);
			helper.assertTrue(lateData.chapter3.stance() == BossOutcome.BOUND.ordinal() && Identity.hasChoice(late, "collector.bind"),
					msg("a stance picked after the owner's choice is still stored and recorded"));
			helper.assertTrue(lateData.hasFlag(ChapterThreeRules.COLLECTOR_SP), msg("and the first-clear rewards were paid once"));
			action(late, "c3:stance", "execute");
			helper.assertTrue(story(late).chapter3.stance() == BossOutcome.BOUND.ordinal() && !Identity.hasChoice(late, "collector.execute"),
					msg("the late stance is still taken once"));
			helper.assertTrue(!ChapterThreeRules.stanceOpen(story(owner)), msg("the owner never takes a stance in AFTERMATH"));
		} finally {
			M3TestPlayers.remove(owner);
			M3TestPlayers.remove(helperPlayer);
			M3TestPlayers.remove(jedi);
			M3TestPlayers.remove(late);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void collectorRewardsOncePerFlag(GameTestHelper helper) {
		ServerPlayer p = at(helper, Path.JEDI, ChapterThreeStage.CONFRONTATION);

		try {
			int pointsBefore = Progression.get(p).skillPoints;
			ChapterThreeService.onBossDefeated(p, COLLECTOR, BossOutcome.SPARED, p.getUUID(), true);
			int pointsAfter = Progression.get(p).skillPoints;
			helper.assertTrue(pointsAfter > pointsBefore && story(p).hasFlag(ChapterThreeRules.COLLECTOR_SP), msg("the first clear pays skill points"));
			helper.assertTrue(count(p, M3Items.SINGING_CRYSTAL) == 1, msg("and one singing crystal"));

			ChapterThreeService.onBossDefeated(p, COLLECTOR, BossOutcome.SPARED, p.getUUID(), true);
			helper.assertTrue(Progression.get(p).skillPoints == pointsAfter && count(p, M3Items.SINGING_CRYSTAL) == 1,
					msg("a second defeat report pays nothing more (flag c3.collector_sp)"));

			// Rematches: once per in-game day.
			ChapterThreeService.onBossDefeated(p, COLLECTOR, BossOutcome.ECHO, p.getUUID(), false);
			int shards = count(p, M3Items.HOLOCRON_SHARD);
			helper.assertTrue(story(p).chapter3.echoClears() == 1 && shards >= 1, msg("the first rematch of the day pays"));
			ChapterThreeService.onBossDefeated(p, COLLECTOR, BossOutcome.ECHO, p.getUUID(), false);
			helper.assertTrue(story(p).chapter3.echoClears() == 1 && count(p, M3Items.HOLOCRON_SHARD) == shards, msg("a second rematch that day pays nothing"));
		} finally {
			M3TestPlayers.remove(p);
		}

		StoryData.Chapter3 cleared = ChapterThreeRules.withEchoClear(StoryData.Chapter3.NEW, 7);
		helper.assertTrue(!ChapterThreeRules.echoRewardDue(cleared, 7) && ChapterThreeRules.echoRewardDue(cleared, 8), msg("the next day pays again"));

		// The emitter: on a roll, or on the 4th clear in a row without one.
		int pityAt = 4;
		int pity = 0;
		List<Boolean> given = new ArrayList<>();

		for (int clear = 1; clear <= 5; clear++) {
			boolean emitter = ChapterThreeRules.emitterDue(pity, false, pityAt);
			given.add(emitter);
			pity = emitter ? 0 : pity + 1;
		}

		helper.assertTrue(given.equals(List.of(false, false, false, true, false)), msg("pity on the 4th clear without one, then it starts over: " + given));
		helper.assertTrue(ChapterThreeRules.emitterDue(0, true, pityAt), msg("a lucky roll gives it at once"));
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void otherHelperRewardOncePerDay(GameTestHelper helper) {
		ServerPlayer owner = at(helper, new BlockPos(1, 1, 1), Path.JEDI, ChapterThreeStage.CONFRONTATION);
		ServerPlayer other = at(helper, new BlockPos(3, 1, 1), Path.INDEPENDENT, ChapterThreeStage.LEADS);

		try {
			helper.assertTrue(!ChapterThreeRules.helperGetsFullRewards(story(other)), msg("a helper still on the trail is an 'other helper'"));
			ChapterThreeService.onBossDefeated(other, COLLECTOR, BossOutcome.SPARED, owner.getUUID(), true);
			int shards = count(other, M3Items.HOLOCRON_SHARD);
			int day = story(other).counter(ChapterThreeRules.HELPER_DAY);
			helper.assertTrue(shards >= 1 && story(other).counters.containsKey(ChapterThreeRules.HELPER_DAY), msg("the other helper's reward is paid"));
			helper.assertTrue(story(other).chapter3.stage() == ChapterThreeStage.LEADS && !story(other).hasFlag(ChapterThreeRules.COLLECTOR_SP),
					msg("their own chapter does not move and they get no first clear"));

			ChapterThreeService.onBossDefeated(other, COLLECTOR, BossOutcome.SPARED, owner.getUUID(), true);
			helper.assertTrue(count(other, M3Items.HOLOCRON_SHARD) == shards && story(other).counter(ChapterThreeRules.HELPER_DAY) == day,
					msg("once per in-game day"));
		} finally {
			M3TestPlayers.remove(owner);
			M3TestPlayers.remove(other);
		}

		helper.assertTrue(ChapterThreeRules.helperRewardDue(-1, 0) && !ChapterThreeRules.helperRewardDue(4, 4) && ChapterThreeRules.helperRewardDue(4, 5),
				msg("never paid, the same day, the next day"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- rank 3

	@GameTest(maxTicks = 20)
	public void rank3MatrixAndFailReasons(GameTestHelper helper) {
		for (Path path : new Path[] {Path.JEDI, Path.SITH}) {
			ChapterThreeRules.Standing s = ready(path);
			int lean = path == Path.SITH ? -1 : 1;
			helper.assertTrue(ChapterThreeRules.rank3Blocker(s, B) == null, msg(path + ": everything met"));
			helper.assertTrue(ChapterThreeRules.requirements(s, B).size() == 7, msg(path + ": seven requirements"));

			// The trial is the confrontation won: the chapter COMPLETE, and it is named first.
			String trial = ChapterThreeRules.rank3Blocker(with(s, ChapterThreeStage.CONFRONTATION, 1, 0, 0, 0, 0, 0, 0, 0), B);
			helper.assertTrue((RANK3_REQ + "trial|1").equals(trial), msg(path + ": at CONFRONTATION the trial blocks first, got " + trial));
			helper.assertTrue(ChapterThreeRules.rank3Blocker(with(s, ChapterThreeStage.AFTERMATH, s.level(), s.trust(), s.ownRep(), s.alignment(),
					s.ownHolocrons(), s.anyHolocrons(), s.bestRep(), s.contracts()), B) != null, msg(path + ": AFTERMATH is not yet the trial"));

			String[][] cases = {
					{"level", RANK3_REQ + "level|" + B.rank3MinLevel},
					{"trust", RANK3_REQ + "trust|" + B.rank3MinTrust},
					{"reputation", RANK3_REQ + "reputation|" + B.rank3MinRep},
					{"alignment", RANK3_REQ + "alignment|" + B.rank3Alignment},
					{"holocrons", RANK3_REQ + "holocrons|" + B.rank3Holocrons},
					{"contracts", RANK3_REQ + "contracts|" + B.rank3Contracts}};

			for (String[] c : cases) {
				ChapterThreeRules.Standing one = switch (c[0]) {
					case "level" -> with(s, s.stage(), B.rank3MinLevel - 1, s.trust(), s.ownRep(), s.alignment(), s.ownHolocrons(), s.anyHolocrons(),
							s.bestRep(), s.contracts());
					case "trust" -> with(s, s.stage(), s.level(), B.rank3MinTrust - 1, s.ownRep(), s.alignment(), s.ownHolocrons(), s.anyHolocrons(),
							s.bestRep(), s.contracts());
					case "reputation" -> with(s, s.stage(), s.level(), s.trust(), B.rank3MinRep - 1, s.alignment(), s.ownHolocrons(), s.anyHolocrons(),
							s.bestRep(), s.contracts());
					case "alignment" -> with(s, s.stage(), s.level(), s.trust(), s.ownRep(), lean * (B.rank3Alignment - 1), s.ownHolocrons(),
							s.anyHolocrons(), s.bestRep(), s.contracts());
					case "holocrons" -> with(s, s.stage(), s.level(), s.trust(), s.ownRep(), s.alignment(), B.rank3Holocrons - 1, s.anyHolocrons(),
							s.bestRep(), s.contracts());
					default -> with(s, s.stage(), s.level(), s.trust(), s.ownRep(), s.alignment(), s.ownHolocrons(), s.anyHolocrons(), s.bestRep(),
							B.rank3Contracts - 1);
				};
				String reason = ChapterThreeRules.rank3Blocker(one, B);
				helper.assertTrue(c[1].equals(reason), msg(path + " missing " + c[0] + ": expected " + c[1] + ", got " + reason));
			}

			// The other path's alignment fails.
			ChapterThreeRules.Standing wrongSide = with(s, s.stage(), s.level(), s.trust(), s.ownRep(), -s.alignment(), s.ownHolocrons(),
					s.anyHolocrons(), s.bestRep(), s.contracts());
			helper.assertTrue((RANK3_REQ + "alignment|" + B.rank3Alignment).equals(ChapterThreeRules.rank3Blocker(wrongSide, B)),
					msg(path + ": the other side's alignment fails"));
		}

		ChapterThreeRules.Standing none = new ChapterThreeRules.Standing(Path.NONE, ChapterThreeStage.COMPLETE, 30, 100, 1000, 0, 4, 12, 1000, 9);
		helper.assertTrue((RANK3_REQ + "no_path").equals(ChapterThreeRules.rank3Blocker(none, B)), msg("no path, no rank 3"));
		helper.assertTrue(new ChapterThreeRules.Requirement("level", false, 3, 11).failReason().equals(RANK3_REQ + "level|11"),
				msg("a fail reason is the lang key and its need"));

		ServerPlayer p = at(helper, Path.JEDI, ChapterThreeStage.CONFRONTATION);

		try {
			String reason = condition(p, "rank3:ready", "");
			helper.assertTrue((RANK3_REQ + "trial|1").equals(reason), msg("the dialogue names the trial first, got " + reason));
			List<ChapterThreeRules.Requirement> list = RankThree.requirements(p, 1);
			helper.assertTrue(!list.isEmpty() && "trial".equals(list.getFirst().id()) && !list.getFirst().met(),
					msg("the Standing checklist reads the same requirements"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void pathfinderNeedsTrustedAndThreeHolocrons(GameTestHelper helper) {
		ChapterThreeRules.Standing s = ready(Path.INDEPENDENT);
		helper.assertTrue(ChapterThreeRules.rank3Blocker(s, B) == null, msg("everything met: the title is ready"));
		helper.assertTrue(ChapterThreeRules.requirements(s, B).stream().map(ChapterThreeRules.Requirement::id).toList()
				.equals(List.of("trial", "level", "holocrons", "trusted", "contracts")), msg("the Pathfinder's five requirements, in order"));

		ChapterThreeRules.Standing twoHolocrons = with(s, s.stage(), s.level(), 0, -900, 0, 0, B.pathfinderHolocrons - 1, s.bestRep(), s.contracts());
		helper.assertTrue((RANK3_REQ + "holocrons|" + B.pathfinderHolocrons).equals(ChapterThreeRules.rank3Blocker(twoHolocrons, B)),
				msg("three holocrons of any kind"));
		ChapterThreeRules.Standing wary = with(s, s.stage(), s.level(), 0, -900, 0, 0, s.anyHolocrons(), B.pathfinderTier - 1, s.contracts());
		helper.assertTrue((RANK3_REQ + "trusted|" + B.pathfinderTier).equals(ChapterThreeRules.rank3Blocker(wary, B)),
				msg("Trusted with at least one faction"));
		helper.assertTrue(ChapterThreeRules.rank3Blocker(with(s, s.stage(), s.level(), 0, -900, -100, 0, s.anyHolocrons(), s.bestRep(), s.contracts()), B) == null,
				msg("no mentor trust, own-order standing or alignment is asked of an Independent"));

		ServerPlayer p = at(helper, Path.INDEPENDENT, ChapterThreeStage.COMPLETE);

		try {
			attune(p, ChapterThreeRules.WAYFARERS_COMPASS, "jedi_morrane", "sith_zarvos", "archive_watch");
			Identity.setReputation(p, Faction.FRONTIER, B.pathfinderTier);
			ChapterThreeRules.Standing read = RankThree.standing(p, B.rank3MinLevel);
			helper.assertTrue(read.anyHolocrons() == 3, msg("three world holocrons count (the Archive does not), got " + read.anyHolocrons()));
			helper.assertTrue(read.bestRep() >= B.pathfinderTier && Identity.tier(p, Faction.FRONTIER) == ReputationTier.TRUSTED,
					msg("Trusted with the Frontier is the best standing"));
			List<ChapterThreeRules.Requirement> list = ChapterThreeRules.requirements(read, B);
			helper.assertTrue(list.stream().filter(r -> !"contracts".equals(r.id())).allMatch(ChapterThreeRules.Requirement::met),
					msg("all but the contracts are met: " + list));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the report

	@GameTest(maxTicks = 20)
	public void independentReportAtWary(GameTestHelper helper) {
		helper.assertTrue(ChapterThreeRules.officerTakesReport(ReputationTier.DISTRUSTED) && !ChapterThreeRules.officerTakesReport(ReputationTier.HOSTILE),
				msg("an officer above Hostile takes the report"));
		ServerPlayer wary = at(helper, new BlockPos(1, 1, 1), Path.INDEPENDENT, ChapterThreeStage.AFTERMATH);
		ServerPlayer hostile = at(helper, new BlockPos(3, 1, 1), Path.INDEPENDENT, ChapterThreeStage.AFTERMATH);

		try {
			helper.assertTrue(condition(wary, "c3:report_ready", "officer") != null, msg("no report before the hoard is decided"));
			flag(wary, ChapterThreeRules.HOARD_DECIDED);
			flag(hostile, ChapterThreeRules.HOARD_DECIDED);
			helper.assertTrue(condition(wary, "c3:report_ready", "officer") == null && condition(wary, "c3:report_ready", "") != null,
					msg("an Independent reports at an officer, not a mentor"));

			Identity.setReputation(wary, Faction.REPUBLIC, -200);
			helper.assertTrue(Identity.tier(wary, Faction.REPUBLIC) == ReputationTier.DISTRUSTED, msg("Republic standing is Wary"));
			action(wary, "c3:report", "republic");
			StoryData done = story(wary);
			helper.assertTrue(done.chapter3.stage() == ChapterThreeStage.COMPLETE && done.hasFlag(ChapterThreeRules.REPORT_DONE),
					msg("a Wary officer takes the report: COMPLETE"));

			Identity.setReputation(hostile, Faction.REPUBLIC, -600);
			action(hostile, "c3:report", "republic");
			helper.assertTrue(story(hostile).chapter3.stage() == ChapterThreeStage.AFTERMATH && !story(hostile).hasFlag(ChapterThreeRules.REPORT_DONE),
					msg("a Hostile army's officer refuses it"));
		} finally {
			M3TestPlayers.remove(wary);
			M3TestPlayers.remove(hostile);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void sendWordWhenBothArmiesHostile(GameTestHelper helper) {
		StoryData ready = new StoryData();
		ready.chapter3 = ChapterThreeRules.withStage(ready.chapter3, ChapterThreeStage.AFTERMATH);
		ready.flags.add(ChapterThreeRules.HOARD_DECIDED);
		helper.assertTrue(ChapterThreeRules.sendWordEligible(ready, Path.INDEPENDENT, ReputationTier.HOSTILE, ReputationTier.HOSTILE),
				msg("both armies Hostile: the journal can carry the report"));
		helper.assertTrue(!ChapterThreeRules.sendWordEligible(ready, Path.INDEPENDENT, ReputationTier.HOSTILE, ReputationTier.DISTRUSTED),
				msg("one army still listening: go to its officer"));
		helper.assertTrue(!ChapterThreeRules.sendWordEligible(ready, Path.JEDI, ReputationTier.HOSTILE, ReputationTier.HOSTILE),
				msg("a Jedi reports to the mentor"));

		ServerPlayer p = at(helper, Path.INDEPENDENT, ChapterThreeStage.AFTERMATH);

		try {
			flag(p, ChapterThreeRules.HOARD_DECIDED);
			Identity.setReputation(p, Faction.REPUBLIC, -600);
			Identity.setReputation(p, Faction.SITH_EMPIRE, -200);
			helper.assertTrue(!SendWord.eligible(p), msg("the Empire is only Wary"));
			action(p, "c3:send_word");
			helper.assertTrue(story(p).chapter3.stage() == ChapterThreeStage.AFTERMATH, msg("sending word is refused then"));

			Identity.setReputation(p, Faction.SITH_EMPIRE, -600);
			helper.assertTrue(SendWord.eligible(p), msg("both Hostile"));
			action(p, "c3:send_word");
			helper.assertTrue(story(p).chapter3.stage() == ChapterThreeStage.COMPLETE && story(p).hasFlag(ChapterThreeRules.REPORT_DONE),
					msg("the word is sent: COMPLETE"));
			helper.assertTrue(!SendWord.eligible(p), msg("and only once"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- delegations to MissionGameTests (W2 plan 4.9)

	@GameTest(maxTicks = 40)
	public void peacefulResolvesCombatObjectivesOnArrival(GameTestHelper helper) {
		MissionGameTests.casePeacefulResolvesCombatObjectivesOnArrival(helper);
	}

	@GameTest(maxTicks = 40)
	public void hostileKillCostsTenAndPaysXp(GameTestHelper helper) {
		MissionGameTests.caseHostileKillCostsTenAndPaysXp(helper);
	}

	@GameTest(maxTicks = 40)
	public void nonHostileKillCostsFortyAndKilledSoldier(GameTestHelper helper) {
		MissionGameTests.caseNonHostileKillCostsFortyAndKilledSoldier(helper);
	}

	@GameTest(maxTicks = 40)
	public void provokeThenKillCountsAsNotHostile(GameTestHelper helper) {
		MissionGameTests.caseProvokeThenKillCountsAsNotHostile(helper);
	}

	@GameTest(maxTicks = 40)
	public void waryEngagedKillIsSelfDefence(GameTestHelper helper) {
		MissionGameTests.caseWaryEngagedKillIsSelfDefence(helper);
	}

	@GameTest(maxTicks = 40)
	public void dailyCapCoversOnlyTenLosses(GameTestHelper helper) {
		MissionGameTests.caseDailyCapCoversOnlyTenLosses(helper);
	}

	@GameTest(maxTicks = 40)
	public void killsNeverGainReputation(GameTestHelper helper) {
		MissionGameTests.caseKillsNeverGainReputation(helper);
	}

	@GameTest(maxTicks = 40)
	public void cameoFlagsChooseR2Chief(GameTestHelper helper) {
		MissionGameTests.caseCameoFlagsChooseR2Chief(helper);
	}
}
