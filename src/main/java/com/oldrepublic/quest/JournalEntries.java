package com.oldrepublic.quest;

import java.util.List;
import java.util.stream.Stream;

/**
 * The canonical order of the journal and codex entries of "Echoes in the Stone" (Chapter One),
 * "Two Voices" (Chapter Two) and "The Ones That Sing" (Chapter Three). Unlocked entries
 * are stored as {@code journal:<id>} and {@code codex:<id>} flags in
 * {@link com.oldrepublic.progression.ForceData#flags}; their text lives in the language file under
 * {@code journal.oldrepublic.entry.<id>.title/.body} and {@code journal.oldrepublic.codex.<id>.title/.body}.
 */
public final class JournalEntries {
	public static final String THE_DREAM = "the_dream";
	public static final String STILLNESS = "stillness";
	public static final String THE_PULL = "the_pull";
	public static final String VOICES_IN_THE_ROCK = "voices_in_the_rock";
	public static final String CUTTERS = "cutters";
	public static final String RETURNED_FIRE = "returned_fire";
	public static final String WHAT_WAITS_BELOW = "what_waits_below";
	public static final String REFLECTION = "reflection";
	public static final String THE_CRYSTAL = "the_crystal";
	public static final String A_BLADE_OF_MY_OWN = "a_blade_of_my_own";

	// Chapter Two: Two Voices
	public static final String TWO_VOICES = "two_voices";
	public static final String THE_CROSSING = "the_crossing";
	public static final String THE_WATCHWOMAN = "the_watchwoman";
	public static final String THE_OVERSEER = "the_overseer";
	public static final String MY_OWN_WAY = "my_own_way";
	public static final String THE_REACH = "the_reach";
	public static final String WHAT_IS_OWED = "what_is_owed";
	public static final String THE_STILL_BLADE = "the_still_blade";
	public static final String RELENTLESS = "relentless";
	public static final String SMOKE_ON_THE_RIDGE = "smoke_on_the_ridge";
	public static final String A_LIFE_SPARED = "a_life_spared";
	public static final String A_DEBT_COLLECTED = "a_debt_collected";
	public static final String SHE_RAN = "she_ran";
	public static final String SHE_FELL = "she_fell";
	public static final String CUT_LOOSE = "cut_loose";
	public static final String THE_PRICE_OF_FREEDOM = "the_price_of_freedom";
	public static final String LEFT_BOUND = "left_bound";
	public static final String THE_LEDGER = "the_ledger";
	public static final String THE_WATCHWOMAN_LISTENS = "the_watchwoman_listens";
	public static final String THE_OVERSEER_WEIGHS = "the_overseer_weighs";
	public static final String THE_DARK_CLOUDS = "the_dark_clouds";
	public static final String STILL_TOO_SOFT = "still_too_soft";
	public static final String PADAWAN_OF_THE_WATCH = "padawan_of_the_watch";
	public static final String APPRENTICE = "apprentice";
	public static final String NO_MASTER = "no_master";

	// Chapter Three: The Ones That Sing
	public static final String THE_ONES_THAT_SING = "the_ones_that_sing";
	public static final String TWO_FLAGS = "two_flags";
	public static final String LISTEN = "listen";
	public static final String THE_HUNT = "the_hunt";
	public static final String COURIERS_FALL = "couriers_fall";
	public static final String WHAT_THE_FORCE_HIDES = "what_the_force_hides";
	public static final String FIRST_ECHO = "first_echo";
	public static final String SILENT_PATROL = "silent_patrol";
	public static final String CREDITS_FROM_THE_CORE = "credits_from_the_core";
	public static final String NAMES = "names";
	public static final String BORDER_INCIDENT = "border_incident";
	public static final String THE_RELIQUARY = "the_reliquary";
	public static final String THE_COLLECTOR = "the_collector";
	public static final String COLLECTOR_SPARED = "collector_spared";
	public static final String COLLECTOR_EXECUTED = "collector_executed";
	public static final String COLLECTOR_BOUND = "collector_bound";
	public static final String COLLECTOR_RELEASED = "collector_released";
	public static final String ARCHIVE_RETURNED = "archive_returned";
	public static final String ARCHIVE_KEPT = "archive_kept";
	public static final String ARCHIVE_UNMADE = "archive_unmade";
	public static final String REPORT_WATCH = "report_watch";
	public static final String REPORT_OVERSEER = "report_overseer";
	public static final String REPORT_OFFICER = "report_officer";
	public static final String KNIGHT_OF_THE_WATCH = "knight_of_the_watch";
	public static final String NAMED_LORD = "named_lord";
	public static final String PATHFINDER = "pathfinder";

	public static final String CODEX_THE_FORCE = "the_force";
	public static final String CODEX_COLD_PEACE = "cold_peace";
	public static final String CODEX_KYBER = "kyber";
	public static final String CODEX_POACHERS = "poachers";
	public static final String CODEX_THE_TRIAL = "the_trial";
	public static final String CODEX_LIGHTSABER = "lightsaber";
	public static final String CODEX_JEDI_ORDER = "jedi_order";
	public static final String CODEX_SITH_EMPIRE = "sith_empire";
	public static final String CODEX_JEDI_WATCH = "jedi_watch";
	public static final String CODEX_SITH_OVERSEERS = "sith_overseers";
	public static final String CODEX_THE_FRONTIER = "the_frontier";
	public static final String CODEX_LIGHT_AND_DARK = "light_and_dark";
	public static final String CODEX_SABER_FORMS = "saber_forms";
	public static final String CODEX_FORCE_HEAL = "force_heal";
	public static final String CODEX_FORCE_LIGHTNING = "force_lightning";
	public static final String CODEX_TWO_FLAGS = "two_flags";
	public static final String CODEX_THE_TREATY_LINE = "the_treaty_line";
	public static final String CODEX_FORCE_SENSE = "force_sense";
	public static final String CODEX_TELEKINETIC_LIFT = "telekinetic_lift";
	public static final String CODEX_HOLOCRONS = "holocrons";
	public static final String CODEX_THE_COLLECTORS_TRAIL = "the_collectors_trail";
	public static final String CODEX_VASCH_RECORDS = "vasch_records";
	public static final String CODEX_DOCTORED_LIST = "doctored_list";
	public static final String CODEX_REPUBLIC_TROOPERS = "republic_troopers";
	public static final String CODEX_IMPERIAL_TROOPERS = "imperial_troopers";
	public static final String CODEX_RUIN_SENTINELS = "ruin_sentinels";
	public static final String CODEX_THE_COLLECTOR = "the_collector";
	public static final String CODEX_THE_COLLECTOR_BOUND = "the_collector_bound";
	public static final String CODEX_ARCHIVE_UNMADE = "archive_unmade";
	public static final String CODEX_ERA_ARMOUR = "era_armour";

	/**
	 * The 15 holocrons in the order of spec section 4.2 (Wayfarer, Jedi, Sith, then the Archive);
	 * each has the codex entry {@code holocron_<id>} ({@link #holocronCodex}).
	 */
	public static final List<String> HOLOCRON_IDS = List.of("wayfarers_compass", "wayfarer_pell", "wayfarer_osk", "wayfarer_maelis",
			"jedi_morrane", "jedi_sarrow", "jedi_ketterin", "jedi_vanterre", "sith_zarvos", "sith_ysolde", "sith_thrane", "sith_torr",
			"archive_watch", "archive_codex", "archive_unbound");

	/**
	 * Journal entries in reading order. Indices travel in the journal-entry effect, so new entries
	 * are only ever appended.
	 */
	public static final List<String> ENTRIES = List.of(THE_DREAM, STILLNESS, THE_PULL, VOICES_IN_THE_ROCK, CUTTERS,
			RETURNED_FIRE, WHAT_WAITS_BELOW, REFLECTION, THE_CRYSTAL, A_BLADE_OF_MY_OWN,
			TWO_VOICES, THE_CROSSING, THE_WATCHWOMAN, THE_OVERSEER, MY_OWN_WAY, THE_REACH, WHAT_IS_OWED, THE_STILL_BLADE,
			RELENTLESS, SMOKE_ON_THE_RIDGE, A_LIFE_SPARED, A_DEBT_COLLECTED, SHE_RAN, SHE_FELL, CUT_LOOSE, THE_PRICE_OF_FREEDOM,
			LEFT_BOUND, THE_LEDGER, THE_WATCHWOMAN_LISTENS, THE_OVERSEER_WEIGHS, THE_DARK_CLOUDS, STILL_TOO_SOFT,
			PADAWAN_OF_THE_WATCH, APPRENTICE, NO_MASTER,
			THE_ONES_THAT_SING, TWO_FLAGS, LISTEN, THE_HUNT, COURIERS_FALL, WHAT_THE_FORCE_HIDES, FIRST_ECHO, SILENT_PATROL,
			CREDITS_FROM_THE_CORE, NAMES, BORDER_INCIDENT, THE_RELIQUARY, THE_COLLECTOR, COLLECTOR_SPARED, COLLECTOR_EXECUTED,
			COLLECTOR_BOUND, COLLECTOR_RELEASED, ARCHIVE_RETURNED, ARCHIVE_KEPT, ARCHIVE_UNMADE, REPORT_WATCH, REPORT_OVERSEER,
			REPORT_OFFICER, KNIGHT_OF_THE_WATCH, NAMED_LORD, PATHFINDER);

	/** The first entry of Chapter Two; the journal puts the chapter header above it. */
	public static final String CHAPTER_TWO_FIRST = TWO_VOICES;
	/** The first entry of Chapter Three; the journal puts the chapter header above it. */
	public static final String CHAPTER_THREE_FIRST = THE_ONES_THAT_SING;
	/**
	 * The first entry of each chapter in reading order: chapter {@code n} starts at element
	 * {@code n - 1}. The journal draws a chapter header above each.
	 */
	public static final List<String> CHAPTER_FIRSTS = List.of(THE_DREAM, CHAPTER_TWO_FIRST, CHAPTER_THREE_FIRST);

	/**
	 * Codex entries in reading order (append only, as for {@link #ENTRIES}). Chapter Three's holocron
	 * entries sit in the {@link #HOLOCRON_IDS} order.
	 */
	public static final List<String> CODEX = Stream.of(
			Stream.of(CODEX_THE_FORCE, CODEX_COLD_PEACE, CODEX_KYBER, CODEX_POACHERS, CODEX_THE_TRIAL, CODEX_LIGHTSABER, CODEX_JEDI_ORDER,
					CODEX_SITH_EMPIRE, CODEX_LIGHT_AND_DARK, CODEX_JEDI_WATCH, CODEX_SITH_OVERSEERS, CODEX_THE_FRONTIER, CODEX_SABER_FORMS,
					CODEX_FORCE_HEAL, CODEX_FORCE_LIGHTNING,
					CODEX_TWO_FLAGS, CODEX_THE_TREATY_LINE, CODEX_FORCE_SENSE, CODEX_TELEKINETIC_LIFT, CODEX_HOLOCRONS),
			HOLOCRON_IDS.stream().map(JournalEntries::holocronCodex),
			Stream.of(CODEX_THE_COLLECTORS_TRAIL, CODEX_VASCH_RECORDS, CODEX_DOCTORED_LIST, CODEX_REPUBLIC_TROOPERS, CODEX_IMPERIAL_TROOPERS,
					CODEX_RUIN_SENTINELS, CODEX_THE_COLLECTOR, CODEX_THE_COLLECTOR_BOUND, CODEX_ARCHIVE_UNMADE, CODEX_ERA_ARMOUR)
	).flatMap(ids -> ids).toList();

	public static String entryFlag(String id) {
		return "journal:" + id;
	}

	public static String codexFlag(String id) {
		return "codex:" + id;
	}

	/** The codex entry id of a holocron: {@code holocron_<id>}. */
	public static String holocronCodex(String holocronId) {
		return "holocron_" + holocronId;
	}

	private JournalEntries() {
	}
}
