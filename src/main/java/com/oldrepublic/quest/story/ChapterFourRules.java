package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Path;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.JournalEntries;

import net.minecraft.network.chat.Component;

/**
 * Chapter Four's pure rules (spec MILESTONE_4.md 8): stage moves, the arc's steps, the hunt's
 * gates, the daily re-issues and the journal's objective line. No world access, so the game tests
 * and the client's journal page call them directly.
 *
 * <p>Story flags of the chapter all start with {@code c4.}; counters likewise.
 */
public final class ChapterFourRules {
	// ---------------------------------------------------------------- flags

	/** Waking from a real sleep while MARKED makes the seeker due at the next safe moment. */
	public static final String SEEKER_DUE = "c4.seeker_due";
	/** The briefing was given (300 XP, the hulk, the bench). */
	public static final String BRIEFED = "c4.briefed";
	/** The hulk and its cradle are placed. */
	public static final String HULK_PLACED = "c4.hulk_placed";
	/** {@code c4.part.<socket>}: the part's 300 XP was paid. */
	public static final String PART_PREFIX = "c4.part.";
	/** The Wreck Field salvage gave its motivator. */
	public static final String SALVAGED = "c4.salvaged";
	/** The ship stood up for the first time ({@code the_ship_flies}). */
	public static final String LIFT_OFF = "c4.lift_off";
	/** {@code c4.landed.<planet>}: the first landing's banner and XP; {@code c4.hub.<planet>}: the hub's XP; {@code c4.met.<planet>}: the host's welcome. */
	public static final String LANDED_PREFIX = "c4.landed.";
	public static final String HUB_PREFIX = "c4.hub.";
	public static final String MET_PREFIX = "c4.met.";
	/** The arc's power was granted by the story (Barrier, Choke or Speed and its branch). */
	public static final String POWER_GRANTED = "c4.power_granted";
	/** Tessaly's refund was paid (once, even if the node is bought and refunded again). */
	public static final String SPEED_REFUND = "c4.speed_refund";
	/** {@code c4.expedition.<site>}: that expedition's XP and SP were paid. */
	public static final String EXPEDITION_PREFIX = "c4.expedition.";
	/** {@code c4.crystal.<site>}: a second crystal waits for the player's hue choice at the host or the journal. */
	public static final String CRYSTAL_PENDING_PREFIX = "c4.crystal_pending.";
	/** Both Doors: which door the player took ({@code c4.both_doors.greenwatch} or {@code .proving}). */
	public static final String BOTH_DOORS_PREFIX = "c4.both_doors.";
	/** Hunt events that happened before HUNTED and were only recorded (8.1): their journal lines are shown later. */
	public static final String SABOTAGE_NOTED = "c4.sabotage_noted";
	/** The challenge scene played (the beacon landed). */
	public static final String CHALLENGE_PLAYED = "c4.challenge_played";
	/** {@code c4.choice.<outcome>}: that ending's rewards were given. */
	public static final String CHOICE_PREFIX = "c4.choice.";
	/** The owner's first-clear rewards (10.6). */
	public static final String KAAST_PAID = "c4.kaast_paid";
	/** The camp's stash was given (10.6). */
	public static final String STASH_OPENED = "c4.stash_opened";
	/** The report was made (8.6). */
	public static final String REPORTED = "c4.reported";
	/** The rest of {@code c4.<route>} names the route that gave the motivator: {@code salvage}, {@code smuggler} or {@code bought}. */
	public static final String MOTIVATOR_ROUTE_PREFIX = "c4.motivator.";

	// ---------------------------------------------------------------- counters

	public static final String HUNTER_DAY = "c4.hunter_day";
	public static final String HUNTER_PITY = "c4.hunter_pity";
	public static final String HELPER_DAY = "c4.helper_day";
	public static final String REMATCH_DAY = "c4.rematch_day";
	public static final String REMATCH_PITY = "c4.rematch_pity";
	/** Game-day index before which a fled ambush does not come again. */
	public static final String AMBUSH_RETRY_DAY = "c4.ambush_retry_day";
	/** Game-day index of the last re-issue of a lost one-time item: {@code c4.reissue.<item>}. */
	public static final String REISSUE_PREFIX = "c4.reissue.";

	// ---------------------------------------------------------------- arc steps (Arc.missionFlags)

	/** The arc's first mission: The Greenwatch, The Proving or Tessaly's Road. */
	public static final int STEP_FIRST = 1;
	/** The arc's second mission: The Heartstone or Zarvos's Sepulchre (an Independent's Both Doors). */
	public static final int STEP_SECOND = 1 << 1;
	/** The arc's power is owned (granted, bought earlier, or already known when the step began). */
	public static final int STEP_POWER = 1 << 2;
	/** Both Doors (Independents; optional). */
	public static final int STEP_BOTH_DOORS = 1 << 3;

	/** Arc.expedition values. */
	public static final int EXPEDITION_NONE = 0;
	public static final int EXPEDITION_DONE = 1;
	public static final int EXPEDITION_SKIPPED = 2;

	/** Story sites the chapter remembers itself ({@link StoryData.Chapter4.Sites}). */
	public static final String SITE_BEACON = "beacon";
	public static final String SITE_AMBUSH_ONE = "ambush_one";
	public static final String SITE_AMBUSH_TWO = "ambush_two";
	public static final String SITE_HULK = "hulk";

	/** The ship's four repair sockets (4.2). */
	public static final int PARTS = 4;

	/** Mission ids (16.1). */
	public static final String OFF_THE_BOOKS = "c4_off_the_books";
	public static final String GREENWATCH = "c4_greenwatch";
	public static final String HEARTSTONE = "c4_heartstone";
	public static final String PROVING = "c4_proving";
	public static final String SEPULCHRE = "c4_sepulchre";
	public static final String TESSALYS_ROAD = "c4_tessalys_road";
	public static final String BOTH_DOORS = "c4_both_doors";

	// ---------------------------------------------------------------- stages

	/** Moves the chapter forward to {@code to}; false when it is not later than the current stage. */
	public static boolean advance(StoryData data, ChapterFourStage to, long now) {
		if (to.ordinal() <= data.chapter4.stage().ordinal()) {
			return false;
		}

		data.chapter4 = data.chapter4.withStage(to, now);
		return true;
	}

	/** A stage by name (any case), or null. */
	public static @Nullable ChapterFourStage parseStage(String name) {
		for (ChapterFourStage stage : ChapterFourStage.values()) {
			if (stage.name().equalsIgnoreCase(name.trim())) {
				return stage;
			}
		}

		return null;
	}

	/** Whether an old save (or a player finishing Chapter Three) should begin Chapter Four now. */
	public static boolean shouldMark(StoryData data) {
		return data.chapter4.stage() == ChapterFourStage.NOT_STARTED && data.chapter3.stage() == ChapterThreeStage.COMPLETE;
	}

	/** Whether MARKED has waited long enough, or a sleep made the seeker due. */
	public static boolean seekerDue(StoryData data, long now, ChapterFourBalance b) {
		StoryData.Chapter4 c = data.chapter4;
		return c.stage() == ChapterFourStage.MARKED
				&& (data.hasFlag(SEEKER_DUE) || (c.waitSince() >= 0 && now - c.waitSince() >= b.waitTicks));
	}

	// ---------------------------------------------------------------- paths and worlds

	/** The path's world: Selvaren for Jedi, Varkhast for Sith; null for Independents (either). */
	public static Planets.@Nullable Planet pathWorld(Path path) {
		return switch (path) {
			case JEDI -> Planets.Planet.SELVAREN;
			case SITH -> Planets.Planet.VARKHAST;
			default -> null;
		};
	}

	/** Whether landing on {@code planet} ends FIRST_FLIGHT for {@code path} (8.1): the path world, or either for Independents. */
	public static boolean endsFirstFlight(Path path, Planets.Planet planet) {
		Planets.Planet world = pathWorld(path);
		return planet != Planets.Planet.HOME && (world == null || world == planet);
	}

	/** The arc's power: Barrier, Choke or Speed. */
	public static String arcPower(Path path) {
		return switch (path) {
			case JEDI -> "force_barrier";
			case SITH -> "force_choke";
			default -> "force_speed";
		};
	}

	/** The first arc mission for the path on {@code planet}. */
	public static String firstMission(Path path) {
		return switch (path) {
			case JEDI -> GREENWATCH;
			case SITH -> PROVING;
			default -> TESSALYS_ROAD;
		};
	}

	/** The second arc mission for the path (Independents: Both Doors). */
	public static String secondMission(Path path) {
		return switch (path) {
			case JEDI -> HEARTSTONE;
			case SITH -> SEPULCHRE;
			default -> BOTH_DOORS;
		};
	}

	/** Whether {@code missionId} is one of Chapter Four's story missions. */
	public static boolean isChapterMission(String missionId) {
		return missionId.startsWith("c4_");
	}

	/** The arc step a finished mission sets. */
	public static int stepOf(String missionId) {
		return switch (missionId) {
			case GREENWATCH, PROVING, TESSALYS_ROAD -> STEP_FIRST;
			case HEARTSTONE, SEPULCHRE -> STEP_SECOND;
			case BOTH_DOORS -> STEP_BOTH_DOORS;
			default -> 0;
		};
	}

	/** Whether the mission is one that grants the arc's power (pays {@code arcPowerMissionXp}). */
	public static boolean isPowerMission(String missionId) {
		return HEARTSTONE.equals(missionId) || SEPULCHRE.equals(missionId) || TESSALYS_ROAD.equals(missionId);
	}

	/** The world arc ends when its power is owned and the expedition is done or skipped (8.1). */
	public static boolean arcDone(StoryData.Chapter4.Arc arc) {
		return arc.has(STEP_POWER) && arc.expedition() != EXPEDITION_NONE;
	}

	/** "Not now" is open after the power is learned and before the expedition is done. */
	public static boolean mayDeclineExpedition(StoryData.Chapter4 c) {
		return c.stage() == ChapterFourStage.WORLD_ARC && c.arc().has(STEP_POWER) && c.arc().expedition() == EXPEDITION_NONE;
	}

	/** The expedition entry for a site. */
	public static @Nullable String expeditionEntry(String siteName) {
		return switch (siteName) {
			case "singing_hollow" -> JournalEntries.THE_SINGING_HOLLOW;
			case "duskglass_rift" -> JournalEntries.THE_RIFT;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the trials (8.4)

	/** Whether a Barrier raised {@code elapsed} ticks into the Heartstone's cleansing counts: from the last wave on. */
	public static boolean barrierCounts(long elapsed, ChapterFourBalance b) {
		int[] waves = b.heartstoneWaves;
		return elapsed >= (waves.length == 0 ? 0 : waves[waves.length - 1]);
	}

	/**
	 * Whether the cleansing is over: its time is up, Barrier was granted, and it was raised (for any
	 * length) since the last wave began. Otherwise the stone waits and the next raise ends it.
	 */
	public static boolean heartstoneDone(long elapsed, boolean granted, boolean raisedSinceLastWave, ChapterFourBalance b) {
		return elapsed >= b.heartstoneTicks && granted && raisedSinceLastWave;
	}

	/**
	 * Whether Zarvos's grip ends now: its {@code sepulchreGripTicks} are up, or the player was freed
	 * early (a Push, the M3 hold rule). Choke is granted as it ends.
	 */
	public static boolean gripEnds(long now, long gripAt, boolean stillHeld, ChapterFourBalance b) {
		return gripAt >= 0 && (now - gripAt >= b.sepulchreGripTicks || !stillHeld);
	}

	// ---------------------------------------------------------------- the hunt

	/** HUNTED passes to CHALLENGED with all three clues and the duel level (8.1). */
	public static boolean challengeReady(StoryData.Chapter4 c, int level, ChapterFourBalance b) {
		return c.stage() == ChapterFourStage.HUNTED && Integer.bitCount(c.hunt().clues()) >= ChapterFour.MAX_CLUE && level >= b.duelLevel;
	}

	/** Hunt events run from FIRST_FLIGHT on, whatever the stage (8.1). */
	public static boolean huntRunning(StoryData.Chapter4 c) {
		return c.stage().ordinal() >= ChapterFourStage.FIRST_FLIGHT.ordinal();
	}

	/** The first ambush waits for the sabotage and has not been beaten; a fled one waits for its retry day. */
	public static boolean ambushOneDue(StoryData data, long day) {
		StoryData.Chapter4.Hunt h = data.chapter4.hunt();
		return huntRunning(data.chapter4) && h.sabotageDone() && h.ambushes() == 0 && day >= data.counter(AMBUSH_RETRY_DAY);
	}

	/** The second ambush comes at least {@code ambushGapDays} after the first, on another world than the first. */
	public static boolean ambushTwoDue(StoryData data, long day, String world, ChapterFourBalance b) {
		StoryData.Chapter4.Hunt h = data.chapter4.hunt();
		String first = data.flags.stream().filter(f -> f.startsWith(AMBUSH_WORLD_PREFIX)).map(f -> f.substring(AMBUSH_WORLD_PREFIX.length()))
				.findFirst().orElse("");
		return huntRunning(data.chapter4) && h.ambushes() == 1 && h.ambushDay() >= 0 && day - h.ambushDay() >= b.ambushGapDays
				&& !world.equals(first) && day >= data.counter(AMBUSH_RETRY_DAY);
	}

	/** {@code c4.ambush_world.<planet>}: the world of the first ambush. */
	public static final String AMBUSH_WORLD_PREFIX = "c4.ambush_world.";

	/** How many hunters drop in for the first ambush: 3, or 4 from the duel level (8.5). */
	public static int ambushSize(int level, ChapterFourBalance b) {
		int[] sizes = b.ambushSize;
		return level >= b.duelLevel && sizes.length > 1 ? sizes[1] : sizes.length > 0 ? sizes[0] : 3;
	}

	/** Hunter bounties begin with HUNTED and continue after the chapter, at most once a day (8.7). */
	public static boolean hunterBountyOpen(StoryData data, long day, ChapterFourBalance b) {
		return b.hunterBounties && data.chapter4.stage().ordinal() >= ChapterFourStage.HUNTED.ordinal()
				&& data.counters.getOrDefault(HUNTER_DAY, -1) < day;
	}

	// ---------------------------------------------------------------- rewards

	/** M3 back pay (8.8): 100 per milestone, at most 300. */
	public static int backPay(int milestones, int perMilestone, int max) {
		return Math.max(0, Math.min(max, milestones * perMilestone));
	}

	/** A one-time chapter item may be re-issued once per in-game day (12.2). */
	public static boolean reissueDue(StoryData data, String item, long day) {
		return data.counters.getOrDefault(REISSUE_PREFIX + item, -1) < day;
	}

	/** The rematch pays once per in-game day (10.6). */
	public static boolean rematchDue(StoryData data, long day) {
		return data.counters.getOrDefault(REMATCH_DAY, -1) < day;
	}

	/** A helper's duel reward, once per in-game day (8.8). */
	public static boolean helperDue(StoryData data, long day) {
		return data.counters.getOrDefault(HELPER_DAY, -1) < day;
	}

	/** Bad-luck protection: a drop is due when it rolled, or after {@code pity} misses in a row. */
	public static boolean pityDue(int misses, boolean rolled, int pity) {
		return rolled || (pity > 0 && misses + 1 >= pity);
	}

	/** The ending's journal entry. */
	public static String outcomeEntry(KaastOutcome outcome) {
		return switch (outcome) {
			case SPARED -> JournalEntries.KAAST_SPARED;
			case STRIPPED -> JournalEntries.KAAST_STRIPPED;
			case ENDED -> JournalEntries.KAAST_ENDED;
		};
	}

	/** The briefing's journal entry for a path. */
	public static String briefingEntry(Path path) {
		return switch (path) {
			case JEDI -> JournalEntries.THE_PATIENT_STAR;
			case SITH -> JournalEntries.A_WRITTEN_OFF_COURIER;
			default -> JournalEntries.VASCHS_COURIER;
		};
	}

	// ---------------------------------------------------------------- the journal

	/** One objective line: a lang key and its arguments (numbers or nested keys prefixed with {@code @}). */
	public record Objective(String key, List<Object> args) {
		public Objective(String key, Object... args) {
			this(key, List.of(args));
		}

		/** The line as text: string arguments starting with {@code @} are lang keys themselves. */
		public Component text() {
			Object[] out = new Object[args.size()];

			for (int i = 0; i < out.length; i++) {
				Object arg = args.get(i);
				out[i] = arg instanceof String s && s.startsWith("@") ? Component.translatable(s.substring(1)) : arg;
			}

			return Component.translatable(key, out);
		}
	}

	private static final String OBJ = "quest.oldrepublic.c4.objective.";

	/**
	 * The journal's Chapter Four objective (8.1) for this player, or null before the seeker scene.
	 * {@code parts} is the number of repair parts fitted; {@code level} the player's level.
	 */
	public static @Nullable Objective objective(StoryData data, int parts, int level, ChapterFourBalance b) {
		StoryData.Chapter4 c = data.chapter4;
		Path path = data.storyPath();

		return switch (c.stage()) {
			case NOT_STARTED, MARKED -> null;
			case SEEKER -> new Objective(OBJ + "seeker");
			case REPAIRS -> new Objective(OBJ + "repairs", Math.min(parts, PARTS), PARTS);
			case FIRST_FLIGHT -> switch (path) {
				case JEDI -> new Objective(OBJ + "fly_selvaren");
				case SITH -> new Objective(OBJ + "fly_varkhast");
				default -> new Objective(OBJ + "choose_world");
			};
			case WORLD_ARC -> arcObjective(c, path);
			case HUNTED -> Integer.bitCount(c.hunt().clues()) >= ChapterFour.MAX_CLUE && level < b.duelLevel
					? new Objective(OBJ + "not_ready", b.duelLevel)
					: new Objective(OBJ + "hunted", Integer.bitCount(c.hunt().clues()), ChapterFour.MAX_CLUE);
			case CHALLENGED -> new Objective(OBJ + "challenged", b.recommendedLevel);
			case DUEL -> new Objective(OBJ + "duel");
			case AFTERMATH -> new Objective(OBJ + "report." + switch (path) {
				case JEDI -> "jedi";
				case SITH -> "sith";
				default -> "independent";
			});
			case COMPLETE -> new Objective(OBJ + "complete");
		};
	}

	private static Objective arcObjective(StoryData.Chapter4 c, Path path) {
		StoryData.Chapter4.Arc arc = c.arc();
		String p = path == Path.JEDI ? "jedi" : path == Path.SITH ? "sith" : "independent";

		if (!arc.has(STEP_FIRST)) {
			return new Objective(OBJ + "arc." + p + ".first");
		}

		if (!arc.has(STEP_POWER)) {
			return new Objective(OBJ + "arc." + p + ".second");
		}

		return new Objective(OBJ + "arc.expedition");
	}

	/** The checklist under the objective while the arc runs: each step and whether it is done (journal page). */
	public static List<Objective> arcChecklist(StoryData.Chapter4 c, Path path) {
		List<Objective> out = new ArrayList<>();

		if (c.stage().ordinal() < ChapterFourStage.WORLD_ARC.ordinal()) {
			return out;
		}

		String p = path == Path.JEDI ? "jedi" : path == Path.SITH ? "sith" : "independent";
		StoryData.Chapter4.Arc arc = c.arc();
		out.add(new Objective(OBJ + "step." + p + ".first", arc.has(STEP_FIRST) ? 1 : 0));
		out.add(new Objective(OBJ + "step." + p + ".second", arc.has(path == Path.INDEPENDENT ? STEP_BOTH_DOORS : STEP_SECOND) ? 1 : 0));
		out.add(new Objective(OBJ + "step.power." + arcPower(path), arc.has(STEP_POWER) ? 1 : 0));
		out.add(new Objective(OBJ + "step.expedition", arc.expedition()));
		return out;
	}

	/** Repair parts fitted so far, from the paid-XP flags (synced, so the journal reads it too). */
	public static int partsFitted(StoryData data) {
		int count = 0;

		for (int socket = 0; socket < PARTS; socket++) {
			if (data.hasFlag(PART_PREFIX + socket)) {
				count++;
			}
		}

		return count;
	}

	/** The id used in commands and lang keys for a stage. */
	public static String stageId(ChapterFourStage stage) {
		return stage.name().toLowerCase(Locale.ROOT);
	}

	private ChapterFourRules() {
	}
}
