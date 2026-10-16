package com.oldrepublic.enemy;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Spawning Crystal Poacher crews for other features (spec section 7.8): mission camps, the
 * Courier's Fall scavengers and Vasch's counter-hunt all go through here.
 */
public final class Poachers {
	/**
	 * Spawns a crew of {@code count} poachers around {@code at}, owned by {@code owner} when not
	 * null, and returns them. Phase 0 stub: W4 fills this in.
	 */
	public static List<LivingEntity> spawnCrew(ServerLevel l, BlockPos at, int count, @Nullable UUID owner) {
		return List.of();
	}

	private Poachers() {
	}
}
