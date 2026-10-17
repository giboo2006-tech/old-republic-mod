package com.oldrepublic.quest.story;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * Read access to a player's Chapter Three for other features, on both sides (the story attachment
 * is synced to its owner). This is the only way other workers read the chapter; the quest feature
 * owns every write. Signatures are frozen (spec section 15.1).
 */
public final class ChapterThree {
	/** A Chapter Three site the story placed for the player. */
	public enum Site {
		/** Courier's Fall (Independents). */
		FALL,
		/** The Reliquary's heart. */
		RELIQUARY
	}

	/** The player's Chapter Three stage (NOT_STARTED on a client that has not received the data yet). */
	public static ChapterThreeStage stage(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? ChapterThreeStage.NOT_STARTED : data.chapter3.stage();
	}

	/** Whether the player has reached {@code stage} or gone past it. */
	public static boolean atLeast(Player p, ChapterThreeStage stage) {
		return stage(p).ordinal() >= stage.ordinal();
	}

	/**
	 * Where the story placed {@code site} for the player, or null: for {@link Site#FALL} the Courier's
	 * Fall pedestal, for {@link Site#RELIQUARY} the Reliquary's heart.
	 */
	public static @Nullable BlockPos site(Player p, Site site) {
		StoryData data = Story.peek(p);

		if (data == null) {
			return null;
		}

		return switch (site) {
			case FALL -> data.chapter3.fall();
			case RELIQUARY -> data.chapter3.reliquary();
		};
	}

	/** The Echo Shrines placed for the player (at most four), in the order they were placed. */
	public static List<BlockPos> shrines(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? List.of() : data.chapter3.shrines();
	}

	private ChapterThree() {
	}
}
