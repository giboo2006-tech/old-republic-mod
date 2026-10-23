package com.oldrepublic.world.trial;

import java.util.EnumSet;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.world.WorldBalance;
import com.oldrepublic.world.WorldEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The echo mirrors the player's own Force Push: a visible arm draw for
 * {@code echoPushWindupTicks} (synced action + dark ripple tell), then a cone release that
 * shoves the player back and deals light damage. Stepping out of the cone or behind cover during
 * the tell avoids it.
 */
final class EchoPushGoal extends Goal {
	private static final int RECOVER_TICKS = 8;
	private static final int AIM_LOCK_TICKS = 4;

	private enum Phase { WINDUP, RECOVER, DONE }

	private final ShadowEchoEntity echo;
	private Phase phase = Phase.DONE;
	private int timer;
	private int nextUse = -1;
	/** Aim direction, frozen {@link #AIM_LOCK_TICKS} before release so a late side-step dodges. */
	private Vec3 aim = new Vec3(0, 0, 1);

	EchoPushGoal(ShadowEchoEntity echo) {
		this.echo = echo;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (nextUse < 0) {
			nextUse = echo.tickCount + 120;
		}

		LivingEntity target = echo.getTarget();

		if (target == null || !target.isAlive() || echo.tickCount < nextUse) {
			return false;
		}

		double range = WorldBalance.get().echoPushRange;
		return echo.distanceTo(target) <= range - 1 && echo.hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = echo.getTarget();
		return phase != Phase.DONE && target != null && target.isAlive();
	}

	@Override
	public boolean isInterruptable() {
		return false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		WorldBalance b = WorldBalance.get();
		phase = Phase.WINDUP;
		timer = 0;
		echo.getNavigation().stop();
		echo.setAction(ShadowEchoEntity.ACTION_PUSH_WINDUP);
		LivingEntity target = echo.getTarget();
		aim = target == null ? aim : aimAt(target);
		EffectEvents.toTracking(echo, WorldEffects.TRIAL_ECHO_PUSH, echo.getEyePosition(), aim, echo.getId(), b.echoPushWindupTicks / 20f, 0);
	}

	@Override
	public void tick() {
		LivingEntity target = echo.getTarget();

		if (target == null) {
			phase = Phase.DONE;
			return;
		}

		timer++;
		echo.getNavigation().stop();

		if (phase == Phase.WINDUP) {
			int windup = WorldBalance.get().echoPushWindupTicks;

			if (timer < windup - AIM_LOCK_TICKS) {
				echo.getLookControl().setLookAt(target, 30f, 30f);
				aim = aimAt(target);
			}

			if (timer >= windup) {
				release(target);
				phase = Phase.RECOVER;
				timer = 0;
			}
		} else if (phase == Phase.RECOVER && timer >= RECOVER_TICKS) {
			phase = Phase.DONE;
		}
	}

	private void release(LivingEntity target) {
		WorldBalance b = WorldBalance.get();
		echo.setAction(ShadowEchoEntity.ACTION_PUSH_RELEASE);
		Vec3 eye = echo.getEyePosition();
		Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
		Vec3 dir = toTarget.lengthSqr() < 1.0e-6 ? aim : toTarget.normalize();
		EffectEvents.toTracking(echo, WorldEffects.TRIAL_ECHO_PUSH, eye, aim, echo.getId(), (float) b.echoPushRange, 1);

		double halfCone = Math.toRadians(b.echoPushConeDegrees / 2);
		boolean inCone = aim.dot(dir) >= Math.cos(halfCone) || toTarget.length() < 1.5;

		if (toTarget.length() > b.echoPushRange || !inCone || !echo.hasLineOfSight(target) || !(echo.level() instanceof ServerLevel level)) {
			return;
		}

		target.hurtServer(level, level.damageSources().mobAttack(echo), b.echoPushDamage);
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		Vec3 shove = (flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 0) : flat.normalize().scale(b.echoPushStrength)).add(0, 0.35, 0);
		target.setDeltaMovement(target.getDeltaMovement().add(shove));
		target.hurtMarked = true;
	}

	private Vec3 aimAt(LivingEntity target) {
		Vec3 to = target.getBoundingBox().getCenter().subtract(echo.getEyePosition());
		return to.lengthSqr() < 1.0e-6 ? aim : to.normalize();
	}

	@Override
	public void stop() {
		phase = Phase.DONE;
		echo.setAction(ShadowEchoEntity.ACTION_IDLE);
		nextUse = echo.tickCount + WorldBalance.get().echoPushCooldownTicks;
	}
}
