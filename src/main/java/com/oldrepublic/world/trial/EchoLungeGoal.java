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
 * The echo's telegraphed lunge: from mid range it crouches for {@code echoLungeWindupTicks}
 * (synced action + effect event with the lunge sound and wisp trail), then dashes along the
 * direction locked at the end of the tell. It hits at most once. Side-stepping during the tell
 * dodges it.
 */
final class EchoLungeGoal extends Goal {
	private static final double MIN_RANGE = 3.5;
	private static final double MAX_RANGE = 9;
	private static final int DASH_TICKS = 8;

	private enum Phase { WINDUP, DASH, DONE }

	private final ShadowEchoEntity echo;
	private Phase phase = Phase.DONE;
	private int timer;
	private int nextUse = -1;
	private boolean hit;
	private Vec3 direction = Vec3.ZERO;

	EchoLungeGoal(ShadowEchoEntity echo) {
		this.echo = echo;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (nextUse < 0) {
			nextUse = echo.tickCount + 60;
		}

		LivingEntity target = echo.getTarget();

		if (target == null || !target.isAlive() || echo.tickCount < nextUse || !echo.onGround()) {
			return false;
		}

		double distance = echo.distanceTo(target);
		return distance >= MIN_RANGE && distance <= MAX_RANGE && echo.hasLineOfSight(target);
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
		hit = false;
		echo.getNavigation().stop();
		echo.setAction(ShadowEchoEntity.ACTION_LUNGE_WINDUP);
		LivingEntity target = echo.getTarget();
		Vec3 toTarget = target == null ? Vec3.ZERO : target.position().subtract(echo.position());
		EffectEvents.toTracking(echo, WorldEffects.TRIAL_ECHO_LUNGE, echo.position(), flat(toTarget), echo.getId(), b.echoLungeWindupTicks / 20f, 0);
	}

	@Override
	public void tick() {
		LivingEntity target = echo.getTarget();

		if (target == null) {
			phase = Phase.DONE;
			return;
		}

		WorldBalance b = WorldBalance.get();
		timer++;

		if (phase == Phase.WINDUP) {
			echo.getNavigation().stop();
			echo.getLookControl().setLookAt(target, 30f, 30f);

			if (timer >= b.echoLungeWindupTicks) {
				direction = flat(target.position().subtract(echo.position()));
				phase = Phase.DASH;
				timer = 0;
				echo.setAction(ShadowEchoEntity.ACTION_LUNGING);
				echo.setDeltaMovement(direction.scale(b.echoLungeSpeed).add(0, 0.12, 0));
				EffectEvents.toTracking(echo, WorldEffects.TRIAL_ECHO_LUNGE, echo.position(), direction, echo.getId(), 0f, 1);
			}

			return;
		}

		if (phase == Phase.DASH) {
			Vec3 motion = echo.getDeltaMovement();
			echo.setDeltaMovement(direction.x * b.echoLungeSpeed, motion.y, direction.z * b.echoLungeSpeed);

			if (!hit && echo.getBoundingBox().inflate(0.35).intersects(target.getBoundingBox()) && echo.level() instanceof ServerLevel level) {
				hit = true;
				target.hurtServer(level, level.damageSources().mobAttack(echo), b.echoLungeDamage);
				target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(0.5)).add(0, 0.15, 0));
				target.hurtMarked = true;
				echo.setDeltaMovement(Vec3.ZERO);
			}

			if (timer >= DASH_TICKS || hit || echo.horizontalCollision) {
				phase = Phase.DONE;
			}
		}
	}

	@Override
	public void stop() {
		phase = Phase.DONE;
		echo.setAction(ShadowEchoEntity.ACTION_IDLE);
		nextUse = echo.tickCount + WorldBalance.get().echoLungeCooldownTicks;
	}

	private static Vec3 flat(Vec3 v) {
		Vec3 horizontal = new Vec3(v.x, 0, v.z);
		return horizontal.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : horizontal.normalize();
	}
}
