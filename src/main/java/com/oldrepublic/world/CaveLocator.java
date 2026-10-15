package com.oldrepublic.world;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Finds crystal caves for "The Calling". PLACEHOLDER: the world feature implements this.
 */
public final class CaveLocator {
	/**
	 * Nearest crystal cave heart (the trial chamber) to the player in their current dimension,
	 * within a search radius, or null. Results may be cached; cheap enough to call every few
	 * seconds per player.
	 */
	public static @Nullable BlockPos findNearestCave(ServerPlayer player) {
		return null;
	}

	/** Whether the player is currently inside a crystal cave structure. */
	public static boolean isInsideCave(ServerPlayer player) {
		return false;
	}

	private CaveLocator() {
	}
}
