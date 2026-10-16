package com.oldrepublic.saber.duel;

import java.util.EnumSet;
import java.util.function.Supplier;

import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberGuard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Saber duel behaviour for NPCs (spec sections 9.5 and 9.6): approach, circle the target at 2.5 to
 * 3.5 blocks, raise the guard (more often after taking hits), attack in profile-driven strings
 * (a telegraphed first wind-up, short gaps between swings, then a vulnerable recovery), use the
 * profile's Force move through {@link #tryForceMove}, and back off while the guard is broken.
 * Strikes go through {@link SaberCombat#strike} so the player's guard and parry handle them.
 */
public class DuelistCombatGoal extends Goal {
	private enum Phase {
		NEUTRAL,
		GUARD,
		WINDUP,
		GAP,
		RECOVER,
		BROKEN,
		/** A subclass's Force move owns the turn. */
		FORCE_MOVE
	}

	/** How often (ticks) a neutral duelist considers raising its guard. */
	private static final int GUARD_CHECK_TICKS = 10;
	/** Strings start from a little outside striking distance; the wind-up steps in. */
	private static final double ENGAGE_SLACK = 0.9;

	private final Mob mob;
	private final SaberDuelist duelist;
	private final Supplier<DuelistProfile> profile;
	private final DuelState state;
	private Phase phase = Phase.NEUTRAL;
	private int timer;
	private int swingsLeft;
	private int nextAttackIn;
	private int repath;
	private boolean strafeRight;
	private int strafeFlipIn;

	public <T extends Mob & SaberDuelist> DuelistCombatGoal(T mob, Supplier<DuelistProfile> profile) {
		this.mob = mob;
		this.duelist = mob;
		this.profile = profile;
		this.state = NpcGuard.register(mob, profile);
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && !target.isSpectator() && !(mob instanceof Yieldable y && y.isYielded()) && mayFight(target);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		phase = Phase.NEUTRAL;
		nextAttackIn = 15 + mob.getRandom().nextInt(15);
		strafeFlipIn = 30;
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		mob.getNavigation().stop();

		if (phase == Phase.FORCE_MOVE) {
			cancelForceMove();
		}

		phase = Phase.NEUTRAL;

		if (!state.isBroken(mob.level().getGameTime())) {
			duelist.setGuardState(GuardState.READY);
		}
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

		long now = level.getGameTime();
		DuelistProfile p = profile();
		double distance = mob.distanceTo(target);
		mob.getLookControl().setLookAt(target, 30f, 30f);

		if (state.isBroken(now)) {
			enter(Phase.BROKEN, GuardState.BROKEN);
			backOff(target);
			return;
		}

		if (phase == Phase.BROKEN) {
			enter(Phase.NEUTRAL, GuardState.READY);
			nextAttackIn = 10;
		}

		if (state.consumeInterrupt() || state.isStaggered(now)) {
			if (phase == Phase.FORCE_MOVE) {
				cancelForceMove();
			}

			enter(Phase.RECOVER, GuardState.RECOVERING);
			timer = Math.max(timer, 10);
			mob.getNavigation().stop();

			if (state.isStaggered(now)) {
				return;
			}
		}

		if (ForceStatus.isDisarmed(mob) && phase != Phase.FORCE_MOVE) {
			// No blade to swing or guard with: keep away; a Force move is still possible.
			if (phase != Phase.NEUTRAL) {
				enter(Phase.NEUTRAL, GuardState.READY);
			}

			if (tryForceMove(target, distance)) {
				enter(Phase.FORCE_MOVE, GuardState.ATTACKING);
			} else {
				backOff(target);
			}

			return;
		}

		switch (phase) {
			case FORCE_MOVE -> {
				if (!tickForceMove(target, distance)) {
					enter(Phase.RECOVER, GuardState.RECOVERING);
					timer = 10;
				}
			}
			case WINDUP, GAP -> {
				closeIn(target, distance);

				if (--timer <= 0) {
					swing(level, target, p);
				}
			}
			case RECOVER -> {
				circle(target, distance, 0.25f);

				if (--timer <= 0) {
					enter(Phase.NEUTRAL, GuardState.READY);
					nextAttackIn = 8 + mob.getRandom().nextInt(16);
				}
			}
			case GUARD -> {
				circle(target, distance, 0.25f);
				nextAttackIn--;

				if (--timer <= 0 || (nextAttackIn <= 0 && distance <= reach(target) + ENGAGE_SLACK && mob.getRandom().nextInt(8) == 0)) {
					enter(Phase.NEUTRAL, GuardState.READY);
				}
			}
			case NEUTRAL, BROKEN -> neutral(target, distance, p, now);
		}
	}

	private void neutral(LivingEntity target, double distance, DuelistProfile p, long now) {
		circle(target, distance, 0.4f);
		nextAttackIn--;

		if (tryForceMove(target, distance)) {
			enter(Phase.FORCE_MOVE, GuardState.ATTACKING);
			return;
		}

		if (nextAttackIn <= 0 && distance <= reach(target) + ENGAGE_SLACK) {
			startString(p);
			return;
		}

		if (DuelistProfiles.guards(p) && !ForceStatus.isGuardSuppressed(mob) && distance < 5.0 && mob.tickCount % GUARD_CHECK_TICKS == 0
				&& mob.getRandom().nextFloat() < guardChance(target, now)) {
			enter(Phase.GUARD, GuardState.GUARDING);
			timer = 20 + mob.getRandom().nextInt(21);
		}
	}

	/** Chance per check to raise the guard: higher after recent hits and while the target is swinging. */
	private float guardChance(LivingEntity target, long now) {
		float chance = 0.12f + 0.15f * state.recentHits(now);

		if (target.swinging) {
			chance += 0.3f;
		}

		return Math.min(0.8f, chance);
	}

	private void startString(DuelistProfile p) {
		int extra = Math.max(0, p.stringMax() - p.stringMin());
		swingsLeft = p.stringMin() + (extra > 0 ? mob.getRandom().nextInt(extra + 1) : 0);
		enter(Phase.WINDUP, GuardState.ATTACKING);
		timer = Math.max(1, p.windupTicks());
		tell(SaberEffects.WINDUP_FIRST, timer);
	}

	private void swing(ServerLevel level, LivingEntity target, DuelistProfile p) {
		mob.swing(InteractionHand.MAIN_HAND);

		if (mob.distanceTo(target) <= reach(target) + 0.4 && SaberGuard.inFront(mob, target.position(), 120f) && mob.hasLineOfSight(target)) {
			SaberCombat.strike(target, level, mob.damageSources().mobAttack(mob), p.damage(), SaberCombat.Strike.NPC_LIGHT);
		}

		swingsLeft--;

		if (swingsLeft > 0) {
			phase = Phase.GAP;
			timer = Math.max(1, p.gapTicks());
			tell(SaberEffects.WINDUP_NEXT, timer);
		} else {
			enter(Phase.RECOVER, GuardState.RECOVERING);
			timer = p.stringMax() <= 1 ? Math.max(p.recoveryTicks(), p.gapTicks()) : p.recoveryTicks();
		}
	}

	/** The attack tell: the blade drawn back (pose from the event), the glint, and the wind-up sound. */
	private void tell(int variant, int ticks) {
		Vec3 hand = mob.position().add(0, mob.getBbHeight() * 0.75, 0);
		EffectEvents.toTracking(mob, SaberEffects.NPC_WINDUP, hand, mob.getLookAngle(), mob.getId(), ticks, variant);
	}

	private void enter(Phase next, GuardState guard) {
		phase = next;
		duelist.setGuardState(guard);
	}

	/** Strafes around the target, keeping 2.5 to 3.5 blocks; walks up when farther than 4.5. */
	private void circle(LivingEntity target, double distance, float sideways) {
		if (distance > 4.5) {
			if (--repath <= 0) {
				repath = 10;
				mob.getNavigation().moveTo(target, 1.0);
			}

			return;
		}

		mob.getNavigation().stop();

		if (--strafeFlipIn <= 0 || mob.horizontalCollision) {
			strafeRight = !strafeRight;
			strafeFlipIn = 30 + mob.getRandom().nextInt(31);
		}

		float forward = distance > 3.5 ? 0.45f : distance < 2.5 ? -0.45f : 0f;
		mob.getMoveControl().strafe(forward, strafeRight ? sideways : -sideways);
	}

	/** During a string: step in until the target is within reach. */
	private void closeIn(LivingEntity target, double distance) {
		if (distance > reach(target)) {
			if (--repath <= 0) {
				repath = 5;
				mob.getNavigation().moveTo(target, 1.15);
			}
		} else {
			mob.getNavigation().stop();
			mob.getMoveControl().strafe(0f, 0f);
		}
	}

	private void backOff(LivingEntity target) {
		mob.getNavigation().stop();
		mob.getMoveControl().strafe(mob.distanceTo(target) < 5.0 ? -0.6f : 0f, 0f);
	}

	/** Striking distance to {@code target} (centre to centre). */
	protected double reach(LivingEntity target) {
		return 2.0 + (mob.getBbWidth() + target.getBbWidth()) * 0.5;
	}

	/**
	 * Called while the duelist is neutral. Start the profile's Force move and return true to take
	 * the turn; {@link #tickForceMove} then runs every tick until it returns false.
	 */
	protected boolean tryForceMove(LivingEntity target, double distance) {
		return false;
	}

	/** Runs a started Force move. Return false when it is over. */
	protected boolean tickForceMove(LivingEntity target, double distance) {
		return false;
	}

	/** The Force move was interrupted (stagger, broken guard, goal stopped). */
	protected void cancelForceMove() {
	}

	/** Whether the duelist may fight {@code target} now (a rival only fights inside a spar). */
	protected boolean mayFight(LivingEntity target) {
		return true;
	}

	protected Mob mob() {
		return mob;
	}

	protected SaberDuelist duelist() {
		return duelist;
	}

	protected DuelistProfile profile() {
		return profile.get();
	}

	/** The duelist's guard bookkeeping. */
	protected DuelState state() {
		return state;
	}
}
