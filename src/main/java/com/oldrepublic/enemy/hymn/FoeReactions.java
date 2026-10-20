package com.oldrepublic.enemy.hymn;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ForceReactions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Milestone 5 foes as the shared Force hooks see them (spec MILESTONE_5.md 12.1): any interrupt
 * (a stagger, a Push, a shock) drops the tell in progress of an {@link Interruptible} foe. The Hask
 * warrior is a Mandalorian hunter, so the M4 {@code HunterReactions} reaches it; the bosses have
 * their own handlers.
 */
public final class FoeReactions implements ForceReactions.Handler {
	@Override
	public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
		if (target instanceof Interruptible foe) {
			foe.interrupt();
		}
	}
}
