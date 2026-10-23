package com.oldrepublic.enemy;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.duel.DuelistCombatGoal;
import com.oldrepublic.saber.duel.NpcGuard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Sith Acolyte's fight: the saber duel goal plus the Force Shove. The shove is telegraphed for
 * {@code shoveWindupTicks} (hand thrust forward, dust and dark ripples at the palm, the wind-up
 * sound), then throws everything in a {@code shoveConeDegrees} cone within {@code shoveRange} back
 * and up for {@code shoveDamage}. A Force Push during the wind-up cancels it (the push clash, in
 * {@link EnemyForceReactions}); so does any stagger or shock.
 */
final class AcolyteCombatGoal extends DuelistCombatGoal {
	private final SithAcolyte acolyte;
	private int shoveTimer;
	private long shoveReadyAt;

	AcolyteCombatGoal(SithAcolyte acolyte) {
		super(acolyte, acolyte::profile);
		this.acolyte = acolyte;
	}

	@Override
	protected boolean mayFight(LivingEntity target) {
		return acolyte.mayFight(target);
	}

	@Override
	protected boolean tryForceMove(LivingEntity target, double distance) {
		AcolyteBalance b = AcolyteBalance.get();
		long now = acolyte.level().getGameTime();

		if (!profile().shove() || now < shoveReadyAt || distance < 2.0 || distance > b.shoveRange - 0.5
				|| !acolyte.hasLineOfSight(target) || acolyte.getRandom().nextInt(15) != 0) {
			return false;
		}

		beginShove(now);
		return true;
	}

	/**
	 * Starts the shove wind-up now, ignoring the range, cooldown and chance checks (scripted scenes
	 * and tests). The release still needs the goal to be running; the push clash works either way.
	 */
	void beginShove(long now) {
		AcolyteBalance b = AcolyteBalance.get();
		shoveTimer = b.shoveWindupTicks;
		shoveReadyAt = now + b.shoveCooldownTicks;
		acolyte.setShoving(true);
		acolyte.getNavigation().stop();
		EffectEvents.toTracking(acolyte, EnemyEffects.ACOLYTE_SHOVE, palm(), acolyte.getLookAngle(), acolyte.getId(), b.shoveWindupTicks, EnemyEffects.SHOVE_WINDUP);
	}

	@Override
	protected boolean tickForceMove(LivingEntity target, double distance) {
		acolyte.getNavigation().stop();
		acolyte.getMoveControl().strafe(0f, 0f);

		if (--shoveTimer > 0) {
			return true;
		}

		acolyte.setShoving(false);

		if (acolyte.level() instanceof ServerLevel level) {
			release(level);
		}

		return false;
	}

	@Override
	protected void cancelForceMove() {
		shoveTimer = 0;
		acolyte.setShoving(false);
	}

	/** True during the shove wind-up (the push clash window). */
	boolean isShoveWindingUp() {
		return shoveTimer > 0 && acolyte.isShoving();
	}

	/** The push clash: the shove is cancelled and the acolyte staggered. */
	void clash() {
		cancelForceMove();
		NpcGuard.stagger(acolyte, AcolyteBalance.get().shoveClashStaggerTicks);
		EffectEvents.toTracking(acolyte, EnemyEffects.ACOLYTE_SHOVE_CLASH, palm(), acolyte.getLookAngle(), acolyte.getId(), 1f, 0);
	}

	private void release(ServerLevel level) {
		AcolyteBalance b = AcolyteBalance.get();
		Vec3 origin = acolyte.getEyePosition();
		Vec3 look = acolyte.getLookAngle();
		double cos = Math.cos(Math.toRadians(b.shoveConeDegrees / 2f));
		AABB box = acolyte.getBoundingBox().inflate(b.shoveRange);

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != acolyte && e.isAlive() && !e.isSpectator() && !(e instanceof SithAcolyte))) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(origin);
			double distance = to.length();

			if (distance > b.shoveRange || distance < 1.0e-3 || to.normalize().dot(look) < cos || !acolyte.hasLineOfSight(target)) {
				continue;
			}

			double resist = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
			Vec3 push = to.horizontal().normalize().scale(b.shoveStrength * (1.0 - 0.6 * resist));
			target.setDeltaMovement(target.getDeltaMovement().scale(0.2).add(push.x, b.shoveLift, push.z));
			target.hurtMarked = true;
			target.hurtServer(level, acolyte.damageSources().indirectMagic(acolyte, acolyte), b.shoveDamage);
		}

		EffectEvents.toTracking(acolyte, EnemyEffects.ACOLYTE_SHOVE, palm(), look, acolyte.getId(), 1f, EnemyEffects.SHOVE_RELEASE);
	}

	/** The palm of the thrust hand, for the effects. */
	private Vec3 palm() {
		return acolyte.getEyePosition().subtract(0, 0.35, 0).add(acolyte.getLookAngle().scale(0.7));
	}
}
