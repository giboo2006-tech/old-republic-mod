package com.oldrepublic.quest;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids owned by the quest feature. See {@code docs/design/quest.md} for the payload
 * meaning of each.
 */
public final class QuestEffects {
	/** To the dreamer only. intensity = duration ticks; variant 0 start, 1 cancel. */
	public static final Identifier AWAKENING_DREAM = OldRepublicMod.id("awakening_dream");
	/** Around the player. intensity = radius. */
	public static final Identifier AWAKENING_BURST = OldRepublicMod.id("awakening_burst");
	/** To trackers and self. intensity = duration ticks; variant 0 start, 1 break, 2 complete. */
	public static final Identifier MEDITATION = OldRepublicMod.id("meditation");
	/** To the owner. dir = horizontal direction to the cave; intensity = closeness 0..1; variant 0 found, 1 none. */
	public static final Identifier CALLING = OldRepublicMod.id("calling");
	/** To the owner. intensity = entry index; variant 0 journal entry, 1 codex entry. */
	public static final Identifier JOURNAL_ENTRY = OldRepublicMod.id("journal_entry");

	public static final int DREAM_START = 0;
	public static final int DREAM_CANCEL = 1;
	public static final int MEDITATION_START = 0;
	public static final int MEDITATION_BREAK = 1;
	public static final int MEDITATION_COMPLETE = 2;
	public static final int CALLING_FOUND = 0;
	public static final int CALLING_NONE = 1;

	private QuestEffects() {
	}
}
