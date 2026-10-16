package com.oldrepublic.enemy;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Crystal Poacher's fight: keep 8 to 14 blocks from the target, strafe while in that band,
 * back-pedal when too close, sprint away when rushed, move to regain line of sight, and run the
 * shot cycle (0.6 s tell before every shot, bursts of 2 to 3, then a recovery window).
 */
final class PoacherCombatGoal extends Goal {
	private enum Phase {
		/** Waiting for a clear shot. */
		READY,
		/** Tell showing; fires when the timer runs out. */
		CHARGING,
		/** Short pause between the shots of one burst. */
		BURST_GAP,
		/** Pause after a burst. */
		RECOVERY
	}

	/** Ticks of lost sight tolerated during a tell before the shot is cancelled. */
	private static final int CHARGE_SIGHT_GRACE = 6;
	/** Ticks without sight before the poacher moves to find a firing position. */
	private static final int REPOSITION_AFTER = 20;
	private static final double BASH_RANGE = 2.2;
	private static final int BASH_COOLDOWN = 30;

	private final CrystalPoacher mob;
	private Phase phase = Phase.READY;
	private int timer;
	private int shotsLeft;
	private int lostSightTicks;
	private int seeTime;
	private boolean strafeClockwise;
	private int strafeFlipIn;
	private boolean retreating;
	private int retreatRecheck;
	private int repositionCooldown;
	private int chaseRepath;
	private int bashCooldown;
	/** A stagger or shock arrived this tick. */
	private boolean interrupted;

	PoacherCombatGoal(CrystalPoacher mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		phase = Phase.READY;
		timer = 10;
		shotsLeft = 0;
		seeTime = 0;
		retreating = false;
		strafeFlipIn = 20;
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		mob.setCharging(false);
		mob.getNavigation().stop();
		phase = Phase.READY;
		retreating = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();

		if (target == null) {
			return;
		}

		EnemyBalance b = EnemyBalance.get();
		double distance = mob.distanceTo(target);
		boolean sees = mob.getSensing().hasLineOfSight(target);

		if (sees != seeTime > 0) {
			seeTime = 0;
		}

		seeTime += sees ? 1 : -1;
		bashCooldown--;
		repositionCooldown--;
		chaseRepath--;
		mob.getLookControl().setLookAt(target, 30f, 30f);

		interrupted = mob.consumeInterrupt();
		move(target, distance, sees, b);
		shoot(target, distance, sees, b);
	}

	private void move(LivingEntity target, double distance, boolean sees, EnemyBalance b) {
		if (distance < b.retreatRange || retreating) {
			if (retreat(target, distance, b)) {
				return;
			}
		}

		if (!sees) {
			if (-seeTime >= REPOSITION_AFTER && repositionCooldown <= 0) {
				repositionCooldown = 30;
				findFiringPosition(target, b);
			}

			return;
		}

		if (distance > b.maxRange) {
			if (chaseRepath <= 0) {
				chaseRepath = 10;
				mob.getNavigation().moveTo(target, 1.0);
			}

			return;
		}

		// In the band with a clear line: hold ground and strafe, back-pedalling when too close.
		mob.getNavigation().stop();

		if (--strafeFlipIn <= 0 || mob.horizontalCollision) {
			strafeClockwise = !strafeClockwise;
			strafeFlipIn = 20 + mob.getRandom().nextInt(21);
		}

		float forward = distance < b.minRange ? -0.6f : 0f;
		float sideways = phase == Phase.CHARGING ? 0.3f : 0.5f;
		mob.getMoveControl().strafe(forward, strafeClockwise ? sideways : -sideways);
	}

	/** Sprints away from a close target. Returns false when no escape is possible or needed. */
	private boolean retreat(LivingEntity target, double distance, EnemyBalance b) {
		if (retreating && (distance >= b.minRange || mob.getNavigation().isDone())) {
			retreating = false;
		}

		if (distance < b.retreatRange && --retreatRecheck <= 0) {
			retreatRecheck = 10;
			Vec3 away = DefaultRandomPos.getPosAway(mob, 10, 5, target.position());

			if (away != null && target.distanceToSqr(away) > target.distanceToSqr(mob)) {
				retreating = mob.getNavigation().moveTo(away.x, away.y, away.z, b.retreatSpeed);
			} else {
				retreating = false;
			}
		}

		if (!retreating && distance <= BASH_RANGE && bashCooldown <= 0 && mob.level() instanceof ServerLevel level) {
			// Cornered: rifle-butt bash.
			bashCooldown = BASH_COOLDOWN;
			mob.swing(InteractionHand.MAIN_HAND);
			mob.doHurtTarget(level, target);
		}

		return retreating;
	}

	/** Walks to a nearby spot with a clear line to the target, preferring the distance band. */
	private void findFiringPosition(LivingEntity target, EnemyBalance b) {
		Vec3 targetEye = target.getEyePosition();
		Vec3 fallback = null;

		for (int i = 0; i < 8; i++) {
			Vec3 candidate = LandRandomPos.getPosTowards(mob, 10, 4, target.position());

			if (candidate == null) {
				continue;
			}

			Vec3 eye = candidate.add(0, mob.getEyeHeight(), 0);
			HitResult hit = mob.level().clip(new ClipContext(eye, targetEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));

			if (hit.getType() != HitResult.Type.MISS) {
				continue;
			}

			double d = Math.sqrt(candidate.distanceToSqr(target.position()));

			if (d >= b.minRange * 0.75 && d <= b.maxRange) {
				mob.getNavigation().moveTo(candidate.x, candidate.y, candidate.z, 1.0);
				return;
			}

			if (fallback == null) {
				fallback = candidate;
			}
		}

		if (fallback != null) {
			mob.getNavigation().moveTo(fallback.x, fallback.y, fallback.z, 1.0);
		} else {
			mob.getNavigation().moveTo(target, 1.0);
		}
	}

	private void shoot(LivingEntity target, double distance, boolean sees, EnemyBalance b) {
		switch (phase) {
			case READY -> {
				if (timer > 0) {
					timer--;
				} else if (sees && !retreating && distance <= b.maxRange + 4 && !mob.isDisarmed()) {
					if (shotsLeft <= 0) {
						shotsLeft = b.burstMin + mob.getRandom().nextInt(Math.max(1, b.burstMax - b.burstMin + 1));
					}

					startCharge(b);
				}
			}
			case CHARGING -> {
				lostSightTicks = sees ? 0 : lostSightTicks + 1;

				if (retreating || lostSightTicks > CHARGE_SIGHT_GRACE || mob.isDisarmed() || interrupted) {
					// Shot abandoned: the tell ends without a bolt; the burst resumes later.
					mob.setCharging(false);
					phase = Phase.READY;
					timer = 10;
				} else if (--timer <= 0) {
					fire(target, b);
				}
			}
			case BURST_GAP -> {
				if (--timer <= 0) {
					startCharge(b);
				}
			}
			case RECOVERY -> {
				if (--timer <= 0) {
					phase = Phase.READY;
				}
			}
		}
	}

	private void startCharge(EnemyBalance b) {
		phase = Phase.CHARGING;
		timer = Math.max(1, b.chargeTicks);
		lostSightTicks = 0;
		mob.setCharging(true);
	}

	private void fire(LivingEntity target, EnemyBalance b) {
		mob.setCharging(false);

		if (mob.level() instanceof ServerLevel level) {
			mob.fireBolt(level, target);
		}

		shotsLeft--;

		if (shotsLeft > 0) {
			// A gap keeps the synced tell flag false for a few ticks, so every shot gets its own tell.
			phase = Phase.BURST_GAP;
			timer = Math.max(2, b.burstGapTicks);
		} else {
			phase = Phase.RECOVERY;
			timer = b.recoveryMinTicks + mob.getRandom().nextInt(Math.max(1, b.recoveryMaxTicks - b.recoveryMinTicks + 1));
		}
	}
}
