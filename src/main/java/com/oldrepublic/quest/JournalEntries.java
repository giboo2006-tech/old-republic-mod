package com.oldrepublic.quest;

import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Planets;

/**
 * The canonical order of the journal and codex entries of "Echoes in the Stone" (Chapter One),
 * "Two Voices" (Chapter Two), "The Ones That Sing" (Chapter Three), "Stars to Steer By" (Chapter
 * Four) and "The Long Hymn" (Chapter Five, with the Fall, the Return and rank 4). Unlocked entries
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
	// Chapter Four (spec MILESTONE_4.md 8.9).
	public static final String A_PRICE = "a_price";
	public static final String THE_PATIENT_STAR = "the_patient_star";
	public static final String A_WRITTEN_OFF_COURIER = "a_written_off_courier";
	public static final String VASCHS_COURIER = "vaschs_courier";
	public static final String A_TRADE = "a_trade";
	public static final String A_PART_OFF_THE_BOOKS = "a_part_off_the_books";
	public static final String THE_SHIP_FLIES = "the_ship_flies";
	public static final String SELVAREN = "selvaren";
	public static final String VARKHAST = "varkhast";
	public static final String THE_GREENWATCH = "the_greenwatch";
	public static final String THE_HEARTSTONE = "the_heartstone";
	public static final String THE_SINGING_HOLLOW = "the_singing_hollow";
	public static final String THE_PROVING = "the_proving";
	public static final String ZARVOS_SEPULCHRE = "zarvos_sepulchre";
	public static final String THE_RIFT = "the_rift";
	public static final String TESSALYS_ROAD = "tessalys_road";
	public static final String BOTH_DOORS = "both_doors";
	public static final String TWO_BLADES = "two_blades";
	public static final String SABOTAGE = "sabotage";
	public static final String FIRST_AMBUSH = "first_ambush";
	public static final String THE_BUYER = "the_buyer";
	public static final String THE_CHALLENGE = "the_challenge";
	public static final String KAAST_SPARED = "kaast_spared";
	public static final String KAAST_STRIPPED = "kaast_stripped";
	public static final String KAAST_ENDED = "kaast_ended";
	public static final String REPORT_C4 = "report_c4";
	// Chapter Five (spec MILESTONE_5.md 6.10).
	public static final String THE_FACTOR_ASKS = "the_factor_asks";
	public static final String THE_HELM = "the_helm";
	public static final String SALTGATE = "saltgate";
	public static final String THE_KAJIDIC_WAY = "the_kajidic_way";
	public static final String THE_CLANS_WAY = "the_clans_way";
	public static final String THE_FORCES_WAY = "the_forces_way";
	public static final String THE_FACTOR = "the_factor";
	public static final String THE_BUYERS_FACE = "the_buyers_face";
	public static final String DASK_ENDED = "dask_ended";
	public static final String TEETH = "teeth";
	public static final String THE_BLOCKADE = "the_blockade";
	public static final String TYRVANN = "tyrvann";
	public static final String VERSE_ONE = "verse_one";
	public static final String VERSE_TWO = "verse_two";
	public static final String VERSE_THREE = "verse_three";
	public static final String THE_HYMNAL = "the_hymnal";
	public static final String ULVAINE_SPARED = "ulvaine_spared";
	public static final String ULVAINE_BOUND = "ulvaine_bound";
	public static final String ULVAINE_ENDED = "ulvaine_ended";
	public static final String REPORT_C5 = "report_c5";
	// The Fall, the Return and rank 4 (spec MILESTONE_5.md 8).
	public static final String THE_FALL_OFFERED = "the_fall_offered";
	public static final String A_GIFT_FOR_THE_EMPIRE = "a_gift_for_the_empire";
	public static final String THE_LAST_LESSON = "the_last_lesson";
	public static final String KNEEL = "kneel";
	public static final String THE_RETURN_OFFERED = "the_return_offered";
	public static final String AMENDS = "amends";
	public static final String THE_OVERSEERS_DUE = "the_overseers_due";
	public static final String WELCOMED = "welcomed";
	public static final String MASTER = "master";
	public static final String DARTH = "darth";
	public static final String WAYFARER = "wayfarer";
	// The Long Hymn (spec MILESTONE_5.md 12).
	public static final String THE_FIRST_CANTOR_FALLS = "the_first_cantor_falls";

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
	// Chapter Four (spec MILESTONE_4.md 8.9).
	public static final String CODEX_BOUNTY_HUNTERS = "bounty_hunters";
	public static final String CODEX_SELVAREN = "selvaren";
	public static final String CODEX_VARKHAST = "varkhast";
	public static final String CODEX_THE_WATCH_ON_SELVAREN = "the_watch_on_selvaren";
	public static final String CODEX_THE_PROVING_CODEX = "the_proving_codex";
	public static final String CODEX_FORCE_SPEED = "force_speed";
	public static final String CODEX_FORCE_BARRIER = "force_barrier";
	public static final String CODEX_FORCE_CHOKE = "force_choke";
	public static final String CODEX_MANDALORIANS = "mandalorians";
	public static final String CODEX_SECURITY_DROIDS = "security_droids";
	public static final String CODEX_HOLLOWHORNS = "hollowhorns";
	public static final String CODEX_ASH_HOUNDS = "ash_hounds";
	public static final String CODEX_DISCIPLINES = "disciplines";
	public static final String CODEX_CREDITS = "credits";
	public static final String CODEX_STARSHIPS = "starships";
	public static final String CODEX_THE_BUYER = "the_buyer";
	public static final String CODEX_TORVIN_KAAST = "torvin_kaast";
	// Chapter Five (spec MILESTONE_5.md 6.10).
	public static final String CODEX_SPACE = "space";
	public static final String CODEX_GHESSAR = "ghessar";
	public static final String CODEX_TYRVANN = "tyrvann";
	public static final String CODEX_THE_VESSBA_KAJIDIC = "the_vessba_kajidic";
	public static final String CODEX_CLAN_HASK = "clan_hask";
	public static final String CODEX_DARTH_ULVAINE = "darth_ulvaine";
	public static final String CODEX_THE_LONG_HYMN = "the_long_hymn";
	public static final String CODEX_THE_FIRST_CANTOR = "the_first_cantor";
	public static final String CODEX_HYMN_INTERCEPTORS = "hymn_interceptors";
	public static final String CODEX_HYMNSWORN = "hymnsworn";
	public static final String CODEX_SAND_SCOURS = "sand_scours";
	public static final String CODEX_STORMBACKS = "stormbacks";
	public static final String CODEX_CARTEL_ENFORCERS = "cartel_enforcers";
	public static final String CODEX_STARFIGHTERS = "starfighters";
	public static final String CODEX_FORCE_STASIS = "force_stasis";
	public static final String CODEX_FORCE_VALOR = "force_valor";
	public static final String CODEX_FORCE_DRAIN = "force_drain";
	public static final String CODEX_FORCE_FEAR = "force_fear";
	public static final String CODEX_FORCE_RAGE = "force_rage";
	public static final String CODEX_MIND_TRICK = "mind_trick";
	public static final String CODEX_DUAL_BLADES = "dual_blades";
	public static final String CODEX_LEGENDARY_COMPONENTS = "legendary_components";
	public static final String CODEX_THE_SPINDLE = "the_spindle";

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
			REPORT_OFFICER, KNIGHT_OF_THE_WATCH, NAMED_LORD, PATHFINDER,
			A_PRICE, THE_PATIENT_STAR, A_WRITTEN_OFF_COURIER, VASCHS_COURIER, A_TRADE, A_PART_OFF_THE_BOOKS, THE_SHIP_FLIES, SELVAREN, VARKHAST,
			THE_GREENWATCH, THE_HEARTSTONE, THE_SINGING_HOLLOW, THE_PROVING, ZARVOS_SEPULCHRE, THE_RIFT, TESSALYS_ROAD, BOTH_DOORS, TWO_BLADES,
			SABOTAGE, FIRST_AMBUSH, THE_BUYER, THE_CHALLENGE, KAAST_SPARED, KAAST_STRIPPED, KAAST_ENDED, REPORT_C4,
			THE_FACTOR_ASKS, THE_HELM, SALTGATE, THE_KAJIDIC_WAY, THE_CLANS_WAY, THE_FORCES_WAY, THE_FACTOR, THE_BUYERS_FACE, DASK_ENDED, TEETH,
			THE_BLOCKADE, TYRVANN, VERSE_ONE, VERSE_TWO, VERSE_THREE, THE_HYMNAL, ULVAINE_SPARED, ULVAINE_BOUND, ULVAINE_ENDED, REPORT_C5,
			THE_FALL_OFFERED, A_GIFT_FOR_THE_EMPIRE, THE_LAST_LESSON, KNEEL, THE_RETURN_OFFERED, AMENDS, THE_OVERSEERS_DUE, WELCOMED, MASTER, DARTH,
			WAYFARER, THE_FIRST_CANTOR_FALLS);

	/** The first entry of Chapter Two; the journal puts the chapter header above it. */
	public static final String CHAPTER_TWO_FIRST = TWO_VOICES;
	/** The first entry of Chapter Three; the journal puts the chapter header above it. */
	public static final String CHAPTER_THREE_FIRST = THE_ONES_THAT_SING;
	/** The first entry of Chapter Four; the journal puts the chapter header above it. */
	public static final String CHAPTER_FOUR_FIRST = A_PRICE;
	/**
	 * The first entry of Chapter Five; the journal puts the chapter header above it
	 * ({@code journal.oldrepublic.chapter_header.5}). The entries of the Fall, the Return, rank 4 and
	 * the Long Hymn come after Chapter Five's in {@link #ENTRIES}, so they sit under its header.
	 */
	public static final String CHAPTER_FIVE_FIRST = THE_FACTOR_ASKS;
	/**
	 * The first entry of each chapter in reading order: chapter {@code n} starts at element
	 * {@code n - 1}. The journal draws a chapter header above each.
	 */
	public static final List<String> CHAPTER_FIRSTS = List.of(THE_DREAM, CHAPTER_TWO_FIRST, CHAPTER_THREE_FIRST, CHAPTER_FOUR_FIRST, CHAPTER_FIVE_FIRST);

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
					CODEX_RUIN_SENTINELS, CODEX_THE_COLLECTOR, CODEX_THE_COLLECTOR_BOUND, CODEX_ARCHIVE_UNMADE, CODEX_ERA_ARMOUR),
			Stream.of(CODEX_BOUNTY_HUNTERS, CODEX_SELVAREN, CODEX_VARKHAST, CODEX_THE_WATCH_ON_SELVAREN, CODEX_THE_PROVING_CODEX, CODEX_FORCE_SPEED,
					CODEX_FORCE_BARRIER, CODEX_FORCE_CHOKE, CODEX_MANDALORIANS, CODEX_SECURITY_DROIDS, CODEX_HOLLOWHORNS, CODEX_ASH_HOUNDS,
					CODEX_DISCIPLINES, CODEX_CREDITS, CODEX_STARSHIPS, CODEX_THE_BUYER, CODEX_TORVIN_KAAST),
			Stream.of(CODEX_SPACE, CODEX_GHESSAR, CODEX_TYRVANN, CODEX_THE_VESSBA_KAJIDIC, CODEX_CLAN_HASK, CODEX_DARTH_ULVAINE, CODEX_THE_LONG_HYMN,
					CODEX_THE_FIRST_CANTOR, CODEX_HYMN_INTERCEPTORS, CODEX_HYMNSWORN, CODEX_SAND_SCOURS, CODEX_STORMBACKS, CODEX_CARTEL_ENFORCERS,
					CODEX_STARFIGHTERS, CODEX_FORCE_STASIS, CODEX_FORCE_VALOR, CODEX_FORCE_DRAIN, CODEX_FORCE_FEAR, CODEX_FORCE_RAGE, CODEX_MIND_TRICK,
					CODEX_DUAL_BLADES, CODEX_LEGENDARY_COMPONENTS, CODEX_THE_SPINDLE)
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

	// Per-planet tables (spec MILESTONE_5.md 3.1): the first landing's entry and codex page, and the
	// hub's codex page. Selvaren and Varkhast keep their Milestone 4 rows; a world without a row
	// (the home world, or a planet added later) unlocks nothing.

	/** The journal entry a first landing on {@code planet} writes (MILESTONE_4.md 8.3, MILESTONE_5.md 6.3, 6.6), or null. */
	public static @Nullable String arrivalEntry(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN -> SELVAREN;
			case VARKHAST -> VARKHAST;
			case GHESSAR -> SALTGATE;
			case TYRVANN -> TYRVANN;
			default -> null;
		};
	}

	/** The codex page a first landing on {@code planet} opens, or null. */
	public static @Nullable String arrivalCodex(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN -> CODEX_SELVAREN;
			case VARKHAST -> CODEX_VARKHAST;
			case GHESSAR -> CODEX_GHESSAR;
			case TYRVANN -> CODEX_TYRVANN;
			default -> null;
		};
	}

	/** The codex page {@code planet}'s hub opens the first time the player comes near it, or null. */
	public static @Nullable String hubCodex(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN -> CODEX_THE_WATCH_ON_SELVAREN;
			case VARKHAST -> CODEX_THE_PROVING_CODEX;
			case GHESSAR -> CODEX_THE_VESSBA_KAJIDIC;
			case TYRVANN -> CODEX_CLAN_HASK;
			default -> null;
		};
	}

	private JournalEntries() {
	}
}
