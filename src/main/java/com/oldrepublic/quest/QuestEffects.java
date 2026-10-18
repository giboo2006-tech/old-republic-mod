package com.oldrepublic.quest;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids owned by the quest feature. See {@code docs/design/quest.md} for the payload
 * meaning of each.
 */
public final class QuestEffects {
	/**
	 * To the dreamer only. intensity = duration ticks; variant 0 start, 1 cancel, 2 Two Voices start,
	 * 3 The Ones That Sing start.
	 */
	public static final Identifier AWAKENING_DREAM = OldRepublicMod.id("awakening_dream");
	/** Around the player. intensity = radius. */
	public static final Identifier AWAKENING_BURST = OldRepublicMod.id("awakening_burst");
	/** To trackers and self. intensity = duration ticks; variant 0 start, 1 break, 2 complete. */
	public static final Identifier MEDITATION = OldRepublicMod.id("meditation");
	/**
	 * To the owner. dir = horizontal direction to the cave (or story site); intensity = closeness
	 * 0..1; variant 0 found, 1 none, 2 the two voices (meeting point), 3 smoke (raider camp), and the
	 * Chapter Three variants 4 to 8 sent through {@link Guidance} (entity id = the variant's data).
	 */
	public static final Identifier CALLING = OldRepublicMod.id("calling");
	/** To the owner. intensity = entry index; variant 0 journal entry, 1 codex entry. */
	public static final Identifier JOURNAL_ENTRY = OldRepublicMod.id("journal_entry");
	/**
	 * To the owner. A story milestone: variant 0 lesson one, 1 lesson two, 2 Smoke on the Ridge,
	 * 3 chapter complete, 4 mission complete, 5 Chapter Three report, 6 the Collector defeated,
	 * 7 rank 3; intensity = path ordinal (1 Jedi, 2 Sith, 3 Independent).
	 */
	public static final Identifier LESSON_COMPLETE = OldRepublicMod.id("lesson_complete");
	/** Around the mentor. entity = the mentor leaving the Crossing; variant 0 Jedi, 1 Sith. */
	public static final Identifier MENTOR_DEPART = OldRepublicMod.id("mentor_depart");
	/**
	 * Around a touched cairn or cache (spec 13.3): it settles into dust and one mote rises toward the
	 * toucher. pos = the cairn; dir = toward the toucher's eyes; entity = the toucher; variant 0 cairn,
	 * 1 cache.
	 */
	public static final Identifier CAIRN_TOUCH = OldRepublicMod.id("cairn_touch");
	/**
	 * Around the player at a rank 3 ceremony (spec 5.8): variant {@link #RANK_KNIGHTING},
	 * {@link #RANK_NAMING} or {@link #RANK_PATHFINDER}; entity = the player. Knighting and Naming:
	 * pos = the player's feet, dir = from the mentor toward the player (zero when the mentor is not
	 * known). Pathfinder: pos = the top of the Courier's Fall pedestal, no dir.
	 */
	public static final Identifier RANK_CEREMONY = OldRepublicMod.id("rank_ceremony");

	public static final int DREAM_START = 0;
	public static final int DREAM_CANCEL = 1;
	public static final int DREAM_TWO_VOICES = 2;
	public static final int DREAM_ONES_THAT_SING = 3;
	public static final int MEDITATION_START = 0;
	public static final int MEDITATION_BREAK = 1;
	public static final int MEDITATION_COMPLETE = 2;
	public static final int CALLING_FOUND = 0;
	public static final int CALLING_NONE = 1;
	public static final int CALLING_VOICES = 2;
	public static final int CALLING_SMOKE = 3;
	/** Toward a holocron chamber; data = the {@code HolocronKind} ordinal. */
	public static final int CALLING_HOLOCRON = 4;
	/** Toward the active contract's site. */
	public static final int CALLING_CONTRACT = 5;
	/** Toward the Reliquary. */
	public static final int CALLING_RELIQUARY = 6;
	/** Toward an unvisited outpost; data = the army's {@code Faction} ordinal. */
	public static final int CALLING_OUTPOST = 7;
	/** Toward Courier's Fall. */
	public static final int CALLING_FALL = 8;
	/** The target is on another world: toward the player's ship or home pad; data = the {@code Planets.Planet} ordinal (spec MILESTONE_4.md 3.6). */
	public static final int CALLING_OFFWORLD = 9;
	public static final int LESSON_ONE = 0;
	public static final int LESSON_TWO = 1;
	public static final int LESSON_RECKONING = 2;
	public static final int LESSON_CHAPTER = 3;
	public static final int LESSON_MISSION = 4;
	public static final int LESSON_CHAPTER_THREE = 5;
	public static final int LESSON_BOSS = 6;
	public static final int LESSON_RANK_THREE = 7;
	public static final int CAIRN_STONE = 0;
	public static final int CAIRN_CACHE = 1;
	/** Saelen's blade touches each shoulder. */
	public static final int RANK_KNIGHTING = 0;
	/** Sorn's writ is read and the old-name tablet breaks. */
	public static final int RANK_NAMING = 1;
	/** Tessaly Arn's figure projects from the Compass and nods. */
	public static final int RANK_PATHFINDER = 2;

	private QuestEffects() {
	}
}
