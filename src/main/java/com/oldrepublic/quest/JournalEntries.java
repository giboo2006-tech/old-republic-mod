package com.oldrepublic.quest;

import java.util.List;

/**
 * The canonical order of the journal and codex entries of "Echoes in the Stone" (Chapter One) and
 * "Two Voices" (Chapter Two). Unlocked entries
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

	/**
	 * Journal entries in reading order. Indices travel in the journal-entry effect, so new entries
	 * are only ever appended.
	 */
	public static final List<String> ENTRIES = List.of(THE_DREAM, STILLNESS, THE_PULL, VOICES_IN_THE_ROCK, CUTTERS,
			RETURNED_FIRE, WHAT_WAITS_BELOW, REFLECTION, THE_CRYSTAL, A_BLADE_OF_MY_OWN,
			TWO_VOICES, THE_CROSSING, THE_WATCHWOMAN, THE_OVERSEER, MY_OWN_WAY, THE_REACH, WHAT_IS_OWED, THE_STILL_BLADE,
			RELENTLESS, SMOKE_ON_THE_RIDGE, A_LIFE_SPARED, A_DEBT_COLLECTED, SHE_RAN, SHE_FELL, CUT_LOOSE, THE_PRICE_OF_FREEDOM,
			LEFT_BOUND, THE_LEDGER, THE_WATCHWOMAN_LISTENS, THE_OVERSEER_WEIGHS, THE_DARK_CLOUDS, STILL_TOO_SOFT,
			PADAWAN_OF_THE_WATCH, APPRENTICE, NO_MASTER);

	/** The first entry of Chapter Two; the journal puts the chapter header above it. */
	public static final String CHAPTER_TWO_FIRST = TWO_VOICES;

	/** Codex entries in reading order (append only, as for {@link #ENTRIES}). */
	public static final List<String> CODEX = List.of(CODEX_THE_FORCE, CODEX_COLD_PEACE, CODEX_KYBER, CODEX_POACHERS,
			CODEX_THE_TRIAL, CODEX_LIGHTSABER, CODEX_JEDI_ORDER, CODEX_SITH_EMPIRE, CODEX_LIGHT_AND_DARK, CODEX_JEDI_WATCH,
			CODEX_SITH_OVERSEERS, CODEX_THE_FRONTIER, CODEX_SABER_FORMS, CODEX_FORCE_HEAL, CODEX_FORCE_LIGHTNING);

	public static String entryFlag(String id) {
		return "journal:" + id;
	}

	public static String codexFlag(String id) {
		return "codex:" + id;
	}

	private JournalEntries() {
	}
}
