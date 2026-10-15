package com.oldrepublic.quest;

import java.util.List;

/**
 * The canonical order of the journal and codex entries of "Echoes in the Stone". Unlocked entries
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

	public static final String CODEX_THE_FORCE = "the_force";
	public static final String CODEX_COLD_PEACE = "cold_peace";
	public static final String CODEX_KYBER = "kyber";
	public static final String CODEX_POACHERS = "poachers";
	public static final String CODEX_THE_TRIAL = "the_trial";
	public static final String CODEX_LIGHTSABER = "lightsaber";

	/** Journal entries in reading order. */
	public static final List<String> ENTRIES = List.of(THE_DREAM, STILLNESS, THE_PULL, VOICES_IN_THE_ROCK, CUTTERS,
			RETURNED_FIRE, WHAT_WAITS_BELOW, REFLECTION, THE_CRYSTAL, A_BLADE_OF_MY_OWN);

	/** Codex entries in reading order. */
	public static final List<String> CODEX = List.of(CODEX_THE_FORCE, CODEX_COLD_PEACE, CODEX_KYBER, CODEX_POACHERS,
			CODEX_THE_TRIAL, CODEX_LIGHTSABER);

	public static String entryFlag(String id) {
		return "journal:" + id;
	}

	public static String codexFlag(String id) {
		return "codex:" + id;
	}

	private JournalEntries() {
	}
}
