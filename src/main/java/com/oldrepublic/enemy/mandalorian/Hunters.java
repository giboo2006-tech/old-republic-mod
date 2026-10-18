package com.oldrepublic.enemy.mandalorian;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Mandalorian hunter encounters (spec MILESTONE_4.md 8.5, 10.1). Phase 0 stub; W6 writes it. */
public final class Hunters {
	/**
	 * Drops {@code count} hunters in around {@code at} (jet pack arrival), owned by {@code owner}
	 * when given, with a security droid escort when {@code withDroid}.
	 *
	 * @return what spawned (empty when nothing could)
	 */
	public static List<LivingEntity> spawnAmbush(ServerLevel level, BlockPos at, int count, @Nullable UUID owner, boolean withDroid) {
		return List.of();
	}

	private Hunters() {
	}
}
