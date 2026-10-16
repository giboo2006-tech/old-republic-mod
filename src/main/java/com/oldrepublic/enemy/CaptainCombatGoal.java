package com.oldrepublic.enemy;

import java.util.EnumSet;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberGuard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The Poacher Captain's fight (spec section 9.7.2): she presses into melee with telegraphed
 * vibroblade swings ({@code swingTellTicks}) and, off cooldown, an overhead heavy
 * ({@code heavyWindupTicks}, draining a guard {@code heavyGuardDrainMult} times as much; a parry
 * staggers her for {@code parryStaggerTicks}). Between {@code pistolMinRange} and
 * {@code pistolMaxRange} she fires single, telegraphed pistol bolts unless disarmed. Every attack
 * has a tell; a stagger, shock or disarm cancels it.
 */
final class CaptainCombatGoal extends Goal {
	private enum Phase {
		NEUTRAL,
		SWING,
		HEAVY,
		PISTOL,
		RECOVER
	}

	private static final double MELEE_REACH = 2.9;
	private static final float HEAVY_CHANCE = 0.35f;

	private final PoacherCaptain mob;
	private Phase phase = Phase.NEUTRAL;
	private int timer;
	private long heavyReadyAt;
	private long pistolReadyAt;
	private int repath;
	private boolean strafeRight;
	private int strafeFlipIn;

	CaptainCombatGoal(PoacherCaptain mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && !target.isSpectator() && !mob.isYielded();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		phase = Phase.NEUTRAL;
		timer = 10;
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		mob.getNavigation().stop();
		mob.setAction(PoacherCaptain.Action.IDLE);
		phase = Phase.NEUTRAL;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();

		if (target == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}

		CaptainBalance b = CaptainBalance.get();
		long now = level.getGameTime();
		double distance = mob.distanceTo(target);
		boolean sees = mob.getSensing().hasLineOfSight(target);
		mob.getLookControl().setLookAt(target, 30f, 30f);

		boolean interrupted = mob.consumeInterrupt() || (phase == Phase.PISTOL && mob.isDisarmed());

		if (mob.isStaggered() || interrupted) {
			mob.setAction(mob.isStaggered() ? PoacherCaptain.Action.STAGGERED : PoacherCaptain.Action.IDLE);
			mob.getNavigation().stop();

			if (phase != Phase.RECOVER && phase != Phase.NEUTRAL) {
				phase = Phase.RECOVER;
				timer = 10;
			}

			if (mob.isStaggered()) {
				return;
			}
		}

		switch (phase) {
			case NEUTRAL -> neutral(level, target, distance, sees, now, b);
			case SWING, HEAVY -> {
				closeIn(target, distance);

				if (--timer <= 0) {
					strike(level, target, phase == Phase.HEAVY, b);
				}
			}
			case PISTOL -> {
				mob.getNavigation().stop();

				if (!sees) {
					recover(10);
				} else if (--timer <= 0) {
					mob.firePistol(level, target);
					pistolReadyAt = now + b.pistolCooldownTicks;
					recover(10);
				}
			}
			case RECOVER -> {
				strafe(distance, 0.3f);

				if (--timer <= 0) {
					phase = Phase.NEUTRAL;
					mob.setAction(PoacherCaptain.Action.IDLE);
				}
			}
		}
	}

	private void neutral(ServerLevel level, LivingEntity target, double distance, boolean sees, long now, CaptainBalance b) {
		if (distance <= MELEE_REACH + 0.8 && sees) {
			if (now >= heavyReadyAt && mob.getRandom().nextFloat() < HEAVY_CHANCE) {
				phase = Phase.HEAVY;
				timer = b.heavyWindupTicks;
				heavyReadyAt = now + b.heavyCooldownTicks;
				mob.setAction(PoacherCaptain.Action.HEAVY_WINDUP);
				EffectEvents.toTracking(mob, EnemyEffects.CAPTAIN_HEAVY, mob.getEyePosition(), mob.getLookAngle(), mob.getId(), timer, EnemyEffects.HEAVY_WINDUP);
			} else {
				phase = Phase.SWING;
				timer = b.swingTellTicks;
				mob.setAction(PoacherCaptain.Action.SWING_TELL);
			}

			return;
		}

		if (sees && !mob.isDisarmed() && now >= pistolReadyAt && distance >= b.pistolMinRange && distance <= b.pistolMaxRange
				&& mob.getRandom().nextInt(4) == 0) {
			phase = Phase.PISTOL;
			timer = b.pistolTellTicks;
			mob.setAction(PoacherCaptain.Action.PISTOL_TELL);
			return;
		}

		if (--repath <= 0) {
			repath = 10;
			mob.getNavigation().moveTo(target, 1.1);
		}
	}

	private void strike(ServerLevel level, LivingEntity target, boolean heavy, CaptainBalance b) {
		mob.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, mob.getX(), mob.getY() + 1.2, mob.getZ(), ModSounds.VIBROBLADE_SWING, mob.getSoundSource(), 1f, heavy ? 0.85f : 1f);

		if (heavy) {
			EffectEvents.toTracking(mob, EnemyEffects.CAPTAIN_HEAVY, mob.getEyePosition(), mob.getLookAngle(), mob.getId(), 1f, EnemyEffects.HEAVY_STRIKE);
		}

		if (mob.distanceTo(target) <= MELEE_REACH + 0.4 && SaberGuard.inFront(mob, target.position(), 120f) && mob.hasLineOfSight(target)) {
			SaberCombat.Strike strike = heavy ? SaberCombat.Strike.npcHeavy(b.heavyGuardDrainMult) : SaberCombat.Strike.NPC_LIGHT;
			boolean hurt = SaberCombat.strike(target, level, mob.damageSources().mobAttack(mob), heavy ? b.heavyDamage : b.swingDamage, strike);

			if (hurt) {
				level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), ModSounds.VIBROBLADE_HIT, mob.getSoundSource(), 1f, 1f);
			}
		}

		recover(heavy ? 30 : 16);
	}

	private void recover(int ticks) {
		phase = Phase.RECOVER;
		timer = ticks;
		mob.setAction(PoacherCaptain.Action.IDLE);
	}

	private void closeIn(LivingEntity target, double distance) {
		if (distance > MELEE_REACH) {
			if (--repath <= 0) {
				repath = 5;
				mob.getNavigation().moveTo(target, 1.15);
			}
		} else {
			mob.getNavigation().stop();
		}
	}

	private void strafe(double distance, float sideways) {
		mob.getNavigation().stop();

		if (--strafeFlipIn <= 0 || mob.horizontalCollision) {
			strafeRight = !strafeRight;
			strafeFlipIn = 25 + mob.getRandom().nextInt(25);
		}

		mob.getMoveControl().strafe(distance < 2.0 ? -0.4f : 0f, strafeRight ? sideways : -sideways);
	}
}
