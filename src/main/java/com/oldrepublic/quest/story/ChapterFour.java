package com.oldrepublic.quest.story;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Planets;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Access to a player's Chapter Four for other features (spec MILESTONE_4.md 15.1). Reads work on
 * both sides (the story attachment is synced to its owner); {@link #addClue} is the one write other
 * workers make. Signatures are frozen.
 */
public final class ChapterFour {
	/** Clues are 1 to 3 (8.5). */
	public static final int MAX_CLUE = 3;

	/** The player's Chapter Four stage (NOT_STARTED on a client that has not received the data yet). */
	public static ChapterFourStage stage(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? ChapterFourStage.NOT_STARTED : data.chapter4.stage();
	}

	/** Whether the player has reached {@code stage} or gone past it. */
	public static boolean atLeast(Player p, ChapterFourStage stage) {
		return stage(p).ordinal() >= stage.ordinal();
	}

	/** The planet of the player's world arc (8.4), or null before their first landing. */
	public static Planets.@Nullable Planet arcPlanet(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? null : data.chapter4.arcPlanet();
	}

	/** How many of the three hunt clues the player has (8.5). */
	public static int clues(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? 0 : Integer.bitCount(data.chapter4.hunt().clues());
	}

	/** Whether the player has clue {@code clue} (1 to 3). */
	public static boolean hasClue(Player p, int clue) {
		StoryData data = Story.peek(p);
		return data != null && clue >= 1 && clue <= MAX_CLUE && (data.chapter4.hunt().clues() & (1 << (clue - 1))) != 0;
	}

	/**
	 * Gives the player clue {@code clue} (1 to 3); a clue already held is ignored. W6 calls this from
	 * the ambushes' yields and the droid's drop; the story reacts in {@link ChapterFourService#onClue}.
	 */
	public static void addClue(ServerPlayer p, int clue) {
		if (clue < 1 || clue > MAX_CLUE || hasClue(p, clue)) {
			return;
		}

		StoryData data = Story.get(p);
		data.chapter4 = data.chapter4.withHunt(data.chapter4.hunt().withClues(data.chapter4.hunt().clues() | (1 << (clue - 1))));
		Story.commit(p, data);
		ChapterFourService.onClue(p, clue);
	}

	private ChapterFour() {
	}
}
