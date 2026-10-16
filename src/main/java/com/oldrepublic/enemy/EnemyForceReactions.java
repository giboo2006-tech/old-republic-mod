package com.oldrepublic.enemy;

import com.oldrepublic.force.ForceReactions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The enemy feature's answers to Force powers: the push clash on a Sith Acolyte's shove wind-up,
 * and wind-up interrupts for the poachers and their Captain. (Saber guards, including the
 * acolyte's, are answered by the saber feature.)
 */
final class EnemyForceReactions implements ForceReactions.Handler {
	@Override
	public float onPushHit(ServerPlayer caster, LivingEntity target) {
		if (target instanceof SithAcolyte acolyte) {
			acolyte.onPushed();
		}

		return 1f;
	}

	@Override
	public void interruptWindup(LivingEntity target) {
		if (target instanceof CrystalPoacher poacher) {
			poacher.interruptCharge();
		} else if (target instanceof PoacherCaptain captain) {
			captain.interrupt();
		}
	}
}
