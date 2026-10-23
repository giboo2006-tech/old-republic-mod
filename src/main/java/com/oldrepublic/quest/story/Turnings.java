package com.oldrepublic.quest.story;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Identity;

import net.minecraft.world.entity.player.Player;

/**
 * Access to a player's Fall or Return for other features (spec MILESTONE_5.md 8 and 17.1). Reads
 * work on both sides (the story and identity attachments are synced to their owner); the paths
 * worker (W5) owns the questlines and the only writes. Signatures are frozen.
 */
public final class Turnings {
	/** Identity choice recorded at the Fall's rite: a player can Fall once (8.1, 14.1). */
	public static final String FALL_DONE = "turn.fall.done";
	/** Identity choice recorded at the Return's rite: a player can Return once (8.1, 14.1). */
	public static final String RETURN_DONE = "turn.return.done";

	/** The current or last questline's step (NONE on a client that has not received the data yet). */
	public static TurningStage stage(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? TurningStage.NONE : data.turning.stage();
	}

	/** The questline under way, or the last one finished (NONE when none was ever offered). */
	public static TurningKind kind(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? TurningKind.NONE : data.turning.kind();
	}

	/** Whether the player has completed {@code kind}'s rite (the identity choice, kept for good). */
	public static boolean done(Player p, TurningKind kind) {
		String choice = doneChoice(kind);
		return choice != null && Identity.hasChoice(p, choice);
	}

	/** The identity choice recorded when {@code kind} is complete, or null for NONE. */
	public static @Nullable String doneChoice(TurningKind kind) {
		return switch (kind) {
			case FALL -> FALL_DONE;
			case RETURN -> RETURN_DONE;
			default -> null;
		};
	}

	private Turnings() {
	}
}
