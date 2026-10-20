package com.oldrepublic.boss.cantor;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ForceReactions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Long Hymn's Force reactions (spec MILESTONE_5.md 12.3): the First Cantor floats and has no body
 * to grip, so Choke and Lift are refused ({@code chokeTicks} and {@code liftHeight} answer 0), and a
 * Push does not move him. A Push does not move a choir echo either: it staggers the echo's song
 * ({@link CantorChoir#stagger}); Lift finds nothing to raise in an echo bound to its spot. Stasis, Fear
 * and Mind Trick follow 9.2 through the tags ({@code #c:bosses}, {@code #oldrepublic:fearless},
 * {@code #oldrepublic:force_resistant}), and the echoes refuse Drain's damage themselves.
 */
final class CantorReactions implements ForceReactions.Handler {
	@Override
	public float onPushHit(ServerPlayer caster, LivingEntity target) {
		return target instanceof FirstCantor || target instanceof CantorChoir ? 0f : 1f;
	}

	@Override
	public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
		if (target instanceof CantorChoir echo && ForceReactions.CAUSE_FORCE_PUSH.equals(cause)) {
			echo.stagger();
		}
	}

	@Override
	public int chokeTicks(LivingEntity target, int requested) {
		return target instanceof FirstCantor ? 0 : requested;
	}

	@Override
	public float liftHeight(LivingEntity target, float requested) {
		return target instanceof FirstCantor || target instanceof CantorChoir ? 0f : requested;
	}
}
