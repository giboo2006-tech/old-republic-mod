package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.FactionM5Balance;
import com.oldrepublic.identity.Path;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.story.ChapterFourRules.Objective;

/**
 * Chapter Five's pure rules (spec MILESTONE_5.md 6): stage moves, the call's wait, the three ways'
 * bookkeeping, the counting-house's guards, the verse stones' order, the choice's ids, the daily and
 * weekly counters, and the journal's objective and sub-objectives. No world access, so the game
 * tests and the client's journal page call them directly.
 *
 * <p>Story flags and counters of the chapter all start with {@code c5.}. Steps that can happen out of
 * order are flags, not stages (6.1).
 */
public final class ChapterFiveRules {
	// ---------------------------------------------------------------- flags

	/** Waking from a real sleep while WAITING makes the call due at the next safe moment (6.1). */
	public static final String CALL_DUE = "c5.call_due";
	/** The helm lesson's three sub-objectives, each paid once (6.2). */
	public static final String HELM = "c5.helm";
	public static final String ORBIT = "c5.orbit";
	public static final String JUMP = "c5.jump";
	/** The first arrival at Saltgate was handled: both factions met, Clan Hask's first standing set (7). */
	public static final String ARRIVED = "c5.arrived";
	/** The player has talked to Varra Hask ("Hand him to the clan" needs it, 6.4). */
	public static final String MET_VARRA = "c5.met_varra";
	/** Kaast's helmet was given back to Varra (7). */
	public static final String HELMET_RETURNED = "c5.helmet_returned";
	/** The kajidic's two jobs, each paid once, and the audience with Vessba (6.3). */
	public static final String JOB_DEBT = "c5.job.debt";
	public static final String JOB_CARGO = "c5.job.cargo";
	public static final String AUDIENCE = "c5.audience";
	/** {@code c5.debt.<force|paid|ran>}: what the player did with the debtor (6.3). */
	public static final String DEBT_PREFIX = "c5.debt.";
	/** The clan's way: the matriarch hunt and the duel of honour, each paid once (6.3). */
	public static final String MATRIARCH = "c5.matriarch";
	public static final String HONOUR = "c5.honour";
	/** {@code c5.force.<trick|fear|push>}: how the player went through the counting-house door (6.3). */
	public static final String FORCE_PREFIX = "c5.force.";
	/** The counting-house was cleared and paid (6.3). */
	public static final String COUNTING_HOUSE = "c5.counting_house";
	/** {@code c5.dask.<release|clan|end>}: Dask's fate (6.4). */
	public static final String DASK_PREFIX = "c5.dask.";
	/** The story's free cannons were fitted (6.5). */
	public static final String CANNONS_PAID = "c5.cannons_paid";
	/** The blockade's rewards were paid (6.5). */
	public static final String BLOCKADE_PAID = "c5.blockade_paid";
	/** {@code c5.verse.<index>}: that stone's rewards were paid (6.6). */
	public static final String VERSE_PREFIX = "c5.verse.";
	/** The Heart's door has opened for the owner (6.10: entry {@code the_hymnal}). */
	public static final String HYMNAL_OPEN = "c5.hymnal_open";
	/** The fight's own reward was paid (6.7). */
	public static final String ULVAINE_PAID = "c5.ulvaine_paid";
	/** {@code c5.choice.<spare|bind|end>}: that choice's rewards were given (6.7). */
	public static final String CHOICE_PREFIX = "c5.choice.";
	/** The report was made (6.7). */
	public static final String REPORTED = "c5.reported";
	/** Ghessar or Tyrvann is missing on this server (3.7): the journal says the way is closed. */
	public static final String NO_GHESSAR = "c5.no_ghessar";
	public static final String NO_TYRVANN = "c5.no_tyrvann";
	/**
	 * Free flight is off on this server ({@code flight.enabled}, 14.4), kept current by the chapter's
	 * tick so the client's journal shows the kajidic's way as the debt alone (6.9).
	 */
	public static final String FLIGHT_OFF = "c5.flight_off";

	// ---------------------------------------------------------------- counters

	/** Game-day index of a helper's last Ulvaine reward (6.9). */
	public static final String HELPER_DAY = "c5.helper_day";
	/** Week index of the fence's last hymnstone shard (7.1). */
	public static final String SHARD_WEEK = "c5.shard_week";
	/** Game-day index of the fence's last cipher (7.1). */
	public static final String CIPHER_DAY = "c5.cipher_day";
	/** {@code c5.cipher.<planet>}: how often the cipher rerolled that board's bounty (salts its seed). */
	public static final String CIPHER_PREFIX = "c5.cipher.";
	/** {@code c5.bounty_day.<planet>}: game-day index of the last bounty taken at that board (5.8). */
	public static final String BOUNTY_DAY_PREFIX = "c5.bounty_day.";

	// ---------------------------------------------------------------- missions (18.1)

	public static final String DEBT = "c5_debt";
	public static final String CARGO = "c5_cargo";
	public static final String MATRIARCH_HUNT = "c5_matriarch";
	public static final String HONOUR_DUEL = "c5_honour";
	public static final String COUNTING_HOUSE_RUN = "c5_counting_house";

	/** The counting-house's guards by the first way: kajidic, clan, Mind Trick, Fear or a forced door (6.3). */
	public static final int GUARDS_KAJIDIC = 0;
	public static final int GUARDS_CLAN = 1;
	public static final int GUARDS_TRICK = 2;
	public static final int GUARDS_FORCED = 3;

	/** Chapter5.boss values. */
	public static final int BOSS_NONE = 0;
	public static final int BOSS_YIELDED = 1;
	public static final int BOSS_RESOLVED = 2;

	/** The three Force entries at the door (6.3). */
	public static final String TRICK = "trick";
	public static final String FEAR = "fear";
	public static final String PUSH = "push";

	// ---------------------------------------------------------------- stages

	/** Moves the chapter forward to {@code to}; false when it is not later than the current stage. */
	public static boolean advance(StoryData data, ChapterFiveStage to, long now) {
		if (to.ordinal() <= data.chapter5.stage().ordinal()) {
			return false;
		}

		data.chapter5 = data.chapter5.withStage(to, now);
		return true;
	}

	/** A stage by id or name (any case), or null. */
	public static @Nullable ChapterFiveStage parseStage(String name) {
		return ChapterFiveStage.byId(name.trim().toLowerCase(Locale.ROOT));
	}

	/** Whether Chapter Four is done and Chapter Five not begun: an M4 save on join, or the report just made (6.9). */
	public static boolean shouldWait(StoryData data) {
		return data.chapter5.stage() == ChapterFiveStage.NOT_STARTED && data.chapter4.stage() == ChapterFourStage.COMPLETE;
	}

	/** Whether WAITING has waited long enough, or a sleep made the call due (6.1). */
	public static boolean callDue(StoryData data, long now, ChapterFiveBalance b) {
		StoryData.Chapter5 c = data.chapter5;
		return c.stage() == ChapterFiveStage.WAITING && (data.hasFlag(CALL_DUE) || (c.waitSince() >= 0 && now - c.waitSince() >= b.waitTicks));
	}

	/** Whether {@code missionId} is one of Chapter Five's story missions. */
	public static boolean isChapterMission(String missionId) {
		return missionId.startsWith("c5_");
	}

	/** Whether the helm should be unlocked: from CALLED on (6.1, 4.2), so saves already past it get it too. */
	public static boolean helmDue(StoryData data) {
		return data.chapter5.stage().ordinal() >= ChapterFiveStage.CALLED.ordinal();
	}

	/** Whether the holdfast should be unlocked: from ARMED on (6.1). */
	public static boolean holdfastDue(StoryData data) {
		return data.chapter5.stage().ordinal() >= ChapterFiveStage.ARMED.ordinal();
	}

	// ---------------------------------------------------------------- Saltgate (6.3)

	/** Kaast's fate in Chapter Four, or null (an old save that never chose). */
	public static @Nullable KaastOutcome kaast(StoryData data) {
		return data.chapter4.duel().kaastOutcome();
	}

	/** Clan Hask's first standing from Kaast's fate (7): spared +100, stripped 0, ended -150; none chosen counts as stripped. */
	public static int clanStart(@Nullable KaastOutcome kaast, FactionM5Balance b) {
		if (kaast == null) {
			return b.clanStartStripped;
		}

		return switch (kaast) {
			case SPARED -> b.clanStartSpared;
			case ENDED -> b.clanStartEnded;
			default -> b.clanStartStripped;
		};
	}

	/** Whether Varra asks for the duel of honour before the hunt: Kaast died by the player's hand and it is not settled (6.3). */
	public static boolean honourOwed(StoryData data) {
		return kaast(data) == KaastOutcome.ENDED && !data.hasFlag(HONOUR);
	}

	/** Whether Varra asks for Kaast's helmet: he was stripped and it is not back yet (7). */
	public static boolean helmetAsked(StoryData data) {
		return kaast(data) == KaastOutcome.STRIPPED && !data.hasFlag(HELMET_RETURNED);
	}

	/** The kajidic's jobs done so far (6.3); the cargo run does not count when free flight is off (6.9). */
	public static int jobsDone(StoryData data, boolean flight) {
		int done = data.hasFlag(JOB_DEBT) ? 1 : 0;
		return flight && data.hasFlag(JOB_CARGO) ? done + 1 : done;
	}

	/** The kajidic's jobs needed: two, or the debt alone when free flight is off (6.9, 14.4). */
	public static int jobsNeeded(boolean flight) {
		return flight ? 2 : 1;
	}

	/** Whether Vessba grants her audience now: both jobs done (the debt alone without flight) and no audience yet (6.3). */
	public static boolean audienceReady(StoryData data, boolean flight) {
		return data.chapter5.stage().ordinal() >= ChapterFiveStage.SALTGATE.ordinal() && !data.hasFlag(AUDIENCE)
				&& jobsDone(data, flight) >= jobsNeeded(flight);
	}

	/** Whether the Force's way is open at the door: in SALTGATE, before any way is done, and not taken yet (6.3). */
	public static boolean doorOpen(StoryData data) {
		return data.chapter5.stage() == ChapterFiveStage.SALTGATE && data.chapter5.chosenWay() == null;
	}

	/** Which counting-house guard count applies (an index into {@code countingHouseGuards}), from the first way and how the door was taken. */
	public static int guardIndex(StoryData data) {
		ChapterFive.Way first = data.chapter5.chosenWay();

		if (first == null || first == ChapterFive.Way.KAJIDIC) {
			return GUARDS_KAJIDIC;
		}

		if (first == ChapterFive.Way.CLAN) {
			return GUARDS_CLAN;
		}

		return data.hasFlag(FORCE_PREFIX + TRICK) ? GUARDS_TRICK : GUARDS_FORCED;
	}

	/** How many enforcers guard the counting-house (6.3). */
	public static int guards(StoryData data, ChapterFiveBalance b) {
		int index = guardIndex(data);
		int[] counts = b.countingHouseGuards;
		return index < counts.length ? Math.max(0, counts[index]) : 0;
	}

	/** Whether the clan fights beside the player in the counting-house: the clan's way came first (6.3). */
	public static boolean clanAllies(StoryData data) {
		return data.chapter5.chosenWay() == ChapterFive.Way.CLAN;
	}

	/**
	 * Whether a clear mission of the chapter (the counting-house, the matriarch hunt) is done on
	 * arrival: no guards stand (the kajidic sold Dask out), or the world is on Peaceful (6.3, 6.9).
	 */
	public static boolean clearsOnArrival(int foes, boolean peaceful) {
		return foes <= 0 || peaceful;
	}

	// ---------------------------------------------------------------- the factor (6.4)

	/** Dask's fate from its action id ({@code release}, {@code clan}, {@code end}), or {@code DASK_NONE}. */
	public static int daskChoice(String id) {
		return switch (id.trim().toLowerCase(Locale.ROOT)) {
			case "release" -> StoryData.Chapter5.DASK_RELEASE;
			case "clan" -> StoryData.Chapter5.DASK_CLAN;
			case "end" -> StoryData.Chapter5.DASK_END;
			default -> StoryData.Chapter5.DASK_NONE;
		};
	}

	/** The action id of a Dask fate, or empty for none. */
	public static String daskId(int dask) {
		return switch (dask) {
			case StoryData.Chapter5.DASK_RELEASE -> "release";
			case StoryData.Chapter5.DASK_CLAN -> "clan";
			case StoryData.Chapter5.DASK_END -> "end";
			default -> "";
		};
	}

	/** Whether Dask's choice may be made now: THE_FACTOR, the counting-house cleared, no fate yet; the clan's only after meeting Varra (6.4). */
	public static boolean daskChoiceOpen(StoryData data, int choice) {
		return data.chapter5.stage() == ChapterFiveStage.THE_FACTOR && data.hasFlag(COUNTING_HOUSE) && data.chapter5.dask() == StoryData.Chapter5.DASK_NONE
				&& choice != StoryData.Chapter5.DASK_NONE && (choice != StoryData.Chapter5.DASK_CLAN || data.hasFlag(MET_VARRA));
	}

	/** The stage after Dask's choice: ARMED, or straight to BLOCKADE for a ship that already has cannons (6.1). */
	public static ChapterFiveStage afterFactor(boolean shipHasCannons) {
		return shipHasCannons ? ChapterFiveStage.BLOCKADE : ChapterFiveStage.ARMED;
	}

	// ---------------------------------------------------------------- the blockade (6.5)

	/**
	 * Whether the nav refuses Tyrvann to the owner's ship ({@code BLOCKADED}, 4.7, 6.1): before
	 * BLOCKADE. In BLOCKADE every route ends in Tyrvann's orbit and starts the battle; after it, Tyrvann
	 * is an ordinary destination. W1's {@code TravelService} reads it.
	 */
	public static boolean tyrvannBlockaded(StoryData data) {
		return data.chapter5.stage().ordinal() < ChapterFiveStage.BLOCKADE.ordinal();
	}

	/** Whether arriving in {@code system}'s orbit starts the blockade: BLOCKADE, over Tyrvann (6.5). */
	public static boolean blockadeStarts(StoryData data, Planets.Planet system) {
		return data.chapter5.stage() == ChapterFiveStage.BLOCKADE && system == Planets.Planet.TYRVANN;
	}

	/** Whether the blockade is the scripted pass, not the battle: on Peaceful, or with free flight off (6.5, 6.9). */
	public static boolean scriptedPass(boolean peaceful, boolean flight) {
		return peaceful || !flight;
	}

	/** Whether the journal sends the player to Varra for the clan's help: enough failures and no clan support (6.5). */
	public static boolean clanHelpOffered(StoryData data, boolean clanStands, ChapterFiveBalance b) {
		return data.chapter5.stage() == ChapterFiveStage.BLOCKADE && data.chapter5.blockadeFailures() >= b.clanHelpFailures && !clanStands;
	}

	// ---------------------------------------------------------------- the verse stones (6.6)

	/** The bit of verse stone {@code index} (1 to 3) in {@code Chapter5.verses}; 0 for an index out of range. */
	public static int verseBit(int index) {
		return index >= 1 && index <= 3 ? 1 << (index - 1) : 0;
	}

	/** How many stones are silenced. */
	public static int versesSilenced(int mask) {
		return Integer.bitCount(mask & 0b111);
	}

	/** VERSES passes to THE_HYMNAL with three stones silenced and the duel level (6.1). */
	public static boolean versesReady(StoryData data, int level, ChapterFiveBalance b) {
		return data.chapter5.stage() == ChapterFiveStage.VERSES && versesSilenced(data.chapter5.verses()) >= 3 && level >= b.duelLevel;
	}

	/** The entry of the {@code order}th stone silenced (1 to 3), whatever its index (6.10). */
	public static String verseEntry(int order) {
		return switch (order) {
			case 1 -> JournalEntries.VERSE_ONE;
			case 2 -> JournalEntries.VERSE_TWO;
			default -> JournalEntries.VERSE_THREE;
		};
	}

	/** The codex page the {@code order}th stone silenced opens: the second the Long Hymn, the third the First Cantor (6.6); else null. */
	public static @Nullable String verseCodex(int order) {
		return switch (order) {
			case 2 -> JournalEntries.CODEX_THE_LONG_HYMN;
			case 3 -> JournalEntries.CODEX_THE_FIRST_CANTOR;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the choice (6.7)

	/** Whether Ulvaine has yielded to this owner and waits for the choice (the {@code c5:yield} condition). */
	public static boolean choiceOpen(StoryData data) {
		StoryData.Chapter5 c = data.chapter5;
		return c.stage() == ChapterFiveStage.THE_HYMNAL && c.boss() == BOSS_YIELDED && c.ulvaine() < 0;
	}

	/** The outcome of a choice id ({@code spare}, {@code bind}, {@code end}), or null. */
	public static @Nullable BossOutcome outcomeOf(String id) {
		return switch (id.trim().toLowerCase(Locale.ROOT)) {
			case "spare" -> BossOutcome.SPARED;
			case "bind" -> BossOutcome.BOUND;
			case "end" -> BossOutcome.EXECUTED;
			default -> null;
		};
	}

	/** The choice id of an outcome; {@code spare} for anything that is not a choice. */
	public static String outcomeId(@Nullable BossOutcome outcome) {
		if (outcome == BossOutcome.BOUND) {
			return "bind";
		}

		return outcome == BossOutcome.EXECUTED ? "end" : "spare";
	}

	/** The choice's journal entry. */
	public static String outcomeEntry(BossOutcome outcome) {
		return switch (outcome) {
			case BOUND -> JournalEntries.ULVAINE_BOUND;
			case EXECUTED -> JournalEntries.ULVAINE_ENDED;
			default -> JournalEntries.ULVAINE_SPARED;
		};
	}

	/** Whether the report is open: AFTERMATH, not yet reported. */
	public static boolean reportReady(StoryData data) {
		return data.chapter5.stage() == ChapterFiveStage.AFTERMATH && !data.hasFlag(REPORTED);
	}

	/** A helper's reward, once per in-game day (6.9). */
	public static boolean helperDue(StoryData data, long day) {
		return data.counters.getOrDefault(HELPER_DAY, -1) < day;
	}

	// ---------------------------------------------------------------- the ongoing loop (6.8, 7.1, 5.8)

	/** The fence's hymnstone shard, once a week (7.1). */
	public static boolean shardDue(StoryData data, long week) {
		return data.counters.getOrDefault(SHARD_WEEK, -1) < week;
	}

	/** The fence's cipher, once a day (7.1). */
	public static boolean cipherDue(StoryData data, long day) {
		return data.counters.getOrDefault(CIPHER_DAY, -1) < day;
	}

	/** Whether a board's space bounty may still be taken today: one per board per player per day (5.8). */
	public static boolean bountyDue(StoryData data, String planet, long day) {
		return data.counters.getOrDefault(BOUNTY_DAY_PREFIX + planet, -1) < day;
	}

	// ---------------------------------------------------------------- the journal (6.10)

	private static final String OBJ = "quest.oldrepublic.c5.objective.";
	private static final String STEP = "quest.oldrepublic.c5.step.";

	/** One sub-objective line under the objective, ticked when done. */
	public record Step(Objective line, boolean done) {
	}

	/**
	 * The journal's Chapter Five objective (6.1) for this player, or null before the call. {@code level}
	 * is the player's level.
	 */
	public static @Nullable Objective objective(StoryData data, int level, ChapterFiveBalance b) {
		StoryData.Chapter5 c = data.chapter5;

		return switch (c.stage()) {
			case CALLED -> data.hasFlag(NO_GHESSAR) ? new Objective(OBJ + "closed_ghessar") : new Objective(OBJ + "called");
			case SALTGATE -> new Objective(OBJ + "saltgate");
			case THE_FACTOR -> new Objective(data.hasFlag(COUNTING_HOUSE) ? OBJ + "dask" : OBJ + "the_factor");
			case ARMED -> new Objective(OBJ + "armed");
			case BLOCKADE -> data.hasFlag(NO_TYRVANN) ? new Objective(OBJ + "closed_tyrvann") : new Objective(OBJ + "blockade");
			case TYRVANN -> new Objective(OBJ + "tyrvann");
			case VERSES -> versesSilenced(c.verses()) >= 3 && level < b.duelLevel
					? new Objective(OBJ + "not_ready", b.duelLevel)
					: new Objective(OBJ + "verses", versesSilenced(c.verses()), 3);
			case THE_HYMNAL -> new Objective(OBJ + "hymnal", b.recommendedLevel);
			case AFTERMATH -> new Objective(OBJ + "report." + pathId(data.storyPath()));
			case COMPLETE -> new Objective(OBJ + "complete");
			default -> null;
		};
	}

	/**
	 * The sub-objectives under the objective (6.2, 6.3, 6.5): the helm lesson while it is not done
	 * (ticked lines only while CALLED; afterwards the undone ones stay as hints), the three ways at
	 * Saltgate with their progress, and the clan's help after failed blockades.
	 *
	 * @param flight whether free flight is on ({@code flight.enabled}): off, the kajidic's way is the debt alone
	 * @param clanStands whether the clan already stands with the player at the blockade
	 */
	public static List<Step> steps(StoryData data, boolean flight, boolean clanStands, ChapterFiveBalance b) {
		List<Step> steps = new ArrayList<>();
		ChapterFiveStage stage = data.chapter5.stage();

		if (stage.ordinal() < ChapterFiveStage.CALLED.ordinal() || stage == ChapterFiveStage.COMPLETE) {
			return steps;
		}

		if (stage == ChapterFiveStage.SALTGATE) {
			int jobs = jobsDone(data, flight);
			int needed = jobsNeeded(flight);
			boolean audience = data.hasFlag(AUDIENCE);
			steps.add(new Step(audience ? new Objective(STEP + "kajidic_done")
					: jobs >= needed ? new Objective(STEP + "kajidic_audience") : new Objective(STEP + "kajidic", jobs, needed),
					data.chapter5.hasWay(ChapterFive.Way.KAJIDIC)));
			steps.add(new Step(honourOwed(data) ? new Objective(STEP + "clan_honour") : new Objective(STEP + "clan"),
					data.chapter5.hasWay(ChapterFive.Way.CLAN)));
			steps.add(new Step(new Objective(STEP + "force"), data.chapter5.hasWay(ChapterFive.Way.FORCE)));
		}

		if (clanHelpOffered(data, clanStands, b)) {
			steps.add(new Step(new Objective(STEP + "clan_help", b.clanHelpCredits), false));
		}

		if (flight) {
			boolean called = stage == ChapterFiveStage.CALLED;
			addHelm(steps, data, HELM, "helm", called);
			addHelm(steps, data, ORBIT, "orbit", called);
			addHelm(steps, data, JUMP, "jump", called);
		}

		return steps;
	}

	private static void addHelm(List<Step> steps, StoryData data, String flag, String key, boolean called) {
		boolean done = data.hasFlag(flag);

		if (called || !done) {
			steps.add(new Step(new Objective(STEP + key), done));
		}
	}

	/** {@code jedi}, {@code sith} or {@code independent}: who the report goes to. */
	public static String pathId(Path path) {
		return path == Path.JEDI ? "jedi" : path == Path.SITH ? "sith" : "independent";
	}

	private ChapterFiveRules() {
	}
}
