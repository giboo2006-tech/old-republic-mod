package com.oldrepublic.enemy.mandalorian;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ForceReactions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Mandalorian hunter as the shared Force hooks see it (spec MILESTONE_4.md 10.1): a Push that
 * lands while it is in a jet hop knocks it out of the air (it falls and is staggered on landing),
 * and any interrupt drops the tell in progress and puts the flamer out. Torvin Kaast, a hunter
 * himself, narrows both in his own class.
 */
public final class HunterReactions implements ForceReactions.Handler {
	@Override
	public float onPushHit(ServerPlayer caster, LivingEntity target) {
		if (target instanceof MandalorianHunter hunter) {
			hunter.onPushedInAir();
		}

		return 1f;
	}

	@Override
	public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
		if (target instanceof MandalorianHunter hunter) {
			hunter.interrupt(cause);
		}
	}
}
