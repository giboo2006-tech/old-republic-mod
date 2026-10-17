package com.oldrepublic.enemy;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.force.ForceReactions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The enemy feature's answers to Force powers: the push clash on a Sith Acolyte's shove wind-up,
 * and wind-up interrupts for the poachers, their Captain and the troopers. (Saber guards, including
 * the acolyte's, are answered by the saber feature.)
 *
 * <p>Troopers are not push-resistant (spec 7.1). Any interrupt (a stagger, Push, Pull, Disarming
 * Pull, shock) cancels a trooper's tell or channel: the charge before a shot, the medic's kolto
 * channel, the Rally fist, the flare and the grenade arm. Disarming Pull's 120 ticks without firing
 * come from {@code ForceStatus#disarm}, which the trooper reads every tick.
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

	@Override
	public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
		if (target instanceof TrooperEntity trooper) {
			trooper.interruptTell();
		} else {
			interruptWindup(target);
		}
	}
}
