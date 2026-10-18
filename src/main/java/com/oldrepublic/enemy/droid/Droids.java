package com.oldrepublic.enemy.droid;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Mk IV security droids (spec MILESTONE_4.md 10.2). Phase 0 stub; W6 writes it. */
public final class Droids {
	/**
	 * Spawns a security droid at {@code at}, owned by {@code owner} when given; an Imperial guard
	 * droid when {@code imperialGuard} (the Proving), else a hunter's escort.
	 *
	 * @return the droid, or null when it could not spawn
	 */
	public static @Nullable LivingEntity spawnSecurity(ServerLevel level, BlockPos at, @Nullable UUID owner, boolean imperialGuard) {
		return null;
	}

	private Droids() {
	}
}
