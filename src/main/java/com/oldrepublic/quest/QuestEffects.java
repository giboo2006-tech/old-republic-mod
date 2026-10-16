package com.oldrepublic.quest;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids owned by the quest feature. See {@code docs/design/quest.md} for the payload
 * meaning of each.
 */
public final class QuestEffects {
	/** To the dreamer only. intensity = duration ticks; variant 0 start, 1 cancel, 2 Two Voices start. */
	public static final Identifier AWAKENING_DREAM = OldRepublicMod.id("awakening_dream");
	/** Around the player. intensity = radius. */
	public static final Identifier AWAKENING_BURST = OldRepublicMod.id("awakening_burst");
	/** To trackers and self. intensity = duration ticks; variant 0 start, 1 break, 2 complete. */
	public static final Identifier MEDITATION = OldRepublicMod.id("meditation");
	/**
	 * To the owner. dir = horizontal direction to the cave (or story site); intensity = closeness
	 * 0..1; variant 0 found, 1 none, 2 the two voices (meeting point), 3 smoke (raider camp).
	 */
	public static final Identifier CALLING = OldRepublicMod.id("calling");
	/** To the owner. intensity = entry index; variant 0 journal entry, 1 codex entry. */
	public static final Identifier JOURNAL_ENTRY = OldRepublicMod.id("journal_entry");
	/**
	 * To the owner. A Chapter Two milestone: variant 0 lesson one, 1 lesson two, 2 Smoke on the
	 * Ridge, 3 chapter complete; intensity = path ordinal (1 Jedi, 2 Sith, 3 Independent).
	 */
	public static final Identifier LESSON_COMPLETE = OldRepublicMod.id("lesson_complete");
	/** Around the mentor. entity = the mentor leaving the Crossing; variant 0 Jedi, 1 Sith. */
	public static final Identifier MENTOR_DEPART = OldRepublicMod.id("mentor_depart");

	public static final int DREAM_START = 0;
	public static final int DREAM_CANCEL = 1;
	public static final int DREAM_TWO_VOICES = 2;
	public static final int MEDITATION_START = 0;
	public static final int MEDITATION_BREAK = 1;
	public static final int MEDITATION_COMPLETE = 2;
	public static final int CALLING_FOUND = 0;
	public static final int CALLING_NONE = 1;
	public static final int CALLING_VOICES = 2;
	public static final int CALLING_SMOKE = 3;
	public static final int LESSON_ONE = 0;
	public static final int LESSON_TWO = 1;
	public static final int LESSON_RECKONING = 2;
	public static final int LESSON_CHAPTER = 3;

	private QuestEffects() {
	}
}
