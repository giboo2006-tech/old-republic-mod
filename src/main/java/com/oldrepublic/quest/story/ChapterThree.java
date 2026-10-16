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

	/** The player's Chapter Three stage. Phase 0 stub: W2 fills this in. */
	public static ChapterThreeStage stage(Player p) {
		return ChapterThreeStage.NOT_STARTED;
	}

	/** Whether the player has reached {@code stage} or gone past it. */
	public static boolean atLeast(Player p, ChapterThreeStage stage) {
		return stage(p).ordinal() >= stage.ordinal();
	}

	/** Where the story placed {@code site} for the player, or null. Phase 0 stub: W2 fills this in. */
	public static @Nullable BlockPos site(Player p, Site site) {
		return null;
	}

	/** The Echo Shrines placed for the player (at most four). Phase 0 stub: W2 fills this in. */
	public static List<BlockPos> shrines(Player p) {
		return List.of();
	}

	private ChapterThree() {
	}
}
