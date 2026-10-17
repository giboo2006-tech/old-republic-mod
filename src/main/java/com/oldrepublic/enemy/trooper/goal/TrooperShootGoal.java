package com.oldrepublic.enemy.trooper.goal;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.trooper.Squads;
import com.oldrepublic.enemy.trooper.TrooperAction;
import com.oldrepublic.enemy.trooper.TrooperBalance;
import com.oldrepublic.enemy.trooper.TrooperEffects;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.TrooperRules;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A trooper's gunfight (spec 7.1 to 7.3): where it stands and when it fires.
 *
 * <p><b>Position.</b> Riflemen and sergeants hold the {@code bandMin}-{@code bandMax} band (10 to
 * 18 blocks), strafing while they have a line, closing in when too far, backing off when rushed and
 * moving to regain a line of fire. The Imperial flanker circles 90 degrees off the squad's line at
 * {@code flankerRange} (12 to 16) from the target. The medic stays behind the squad leader, away
 * from the target. The grenadier keeps to its grenade range. After an answered deflection the
 * rifleman sidesteps 4 to 8 blocks while it holds fire.
 *
 * <p><b>Fire.</b> Every shot or burst starts with the 12-tick charge tell ({@code trooper_charge},
 * the CHARGING pose). The Republic rifle then fires a burst of {@code burstSize} bolts
 * {@code burstGap} ticks apart, waits {@code burstCooldown} (shortened by a Rally) and vents for
 * {@code ventTicks} after every {@code ventAfterBursts} bursts, on top of the gap. The Imperial
 * rifle fires one slow, hard bolt per {@code imperialCooldown}; the pistol one bolt per
 * {@code pistolCooldown}. A Force interrupt, a Disarming Pull, holding fire or losing sight during
 * the tell cancels the shot; a Disarming Pull also stops a burst at once.
 */
public final class TrooperShootGoal extends Goal {
	private enum Phase {
		/** Waiting for a clear shot. */
		READY,
		/** Tell showing; fires when the timer runs out. */
		CHARGING,
		/** Between the bolts of a Republic burst. */
		BURST,
		/** After a shot, a burst or a vent. */
		COOLDOWN
	}

	/** Ticks of lost sight tolerated during a tell before the shot is cancelled. */
	private static final int CHARGE_SIGHT_GRACE = 6;
	/** Ticks without sight before the trooper moves to find a firing position. */
	private static final int REPOSITION_AFTER = 20;
	private static final int REPATH_TICKS = 10;
	/** How long a back-off run is left alone before the band logic takes over again. */
	private static final int BACK_OFF_TICKS = 20;
	/** A rifle still fires this far beyond its band. */
	private static final double REACH_BEYOND_BAND = 6;
	/** Blocks from the wanted spot that count as being there. */
	private static final double SPOT_TOLERANCE = 2.5;
	/** The medic keeps this far behind the leader, away from the target. */
	private static final double MEDIC_BEHIND = 4;
	private static final double MOVE_SPEED = 1.0;
	private static final double SIDESTEP_SPEED = 1.25;
	private static final double BACK_OFF_SPEED = 1.2;
	/** A sidestep that takes longer than this is abandoned. */
	private static final int SIDESTEP_TIMEOUT = 60;
	/** The leader is looked up again this often. */
	private static final int LEADER_REFRESH = 20;

	private final TrooperEntity trooper;
	private Phase phase = Phase.READY;
	private int timer;
	private int shotsLeft;
	private int burstsSinceVent;
	private int lostSightTicks;
	private int seeTime;
	private boolean strafeClockwise;
	private int strafeFlipIn;
	private int repath;
	private int backOff;
	private int findLineCooldown;
	private int sidestepTicks;
	private @Nullable TrooperEntity leader;
	private int leaderAge = LEADER_REFRESH;

	public TrooperShootGoal(TrooperEntity trooper) {
		this.trooper = trooper;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = trooper.getTarget();
		return target != null && target.isAlive() && trooper.canFight() && trooper.weapon() != TrooperEntity.Weapon.NONE;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		trooper.setAggressive(true);
		phase = Phase.READY;
		// A short, varied delay so a squad does not open fire on the same tick.
		timer = 8 + trooper.getRandom().nextInt(12);
		shotsLeft = 0;
		seeTime = 0;
		strafeFlipIn = 20;
		repath = 0;
		backOff = 0;
		sidestepTicks = 0;
		leaderAge = LEADER_REFRESH;
	}

	@Override
	public void stop() {
		trooper.setAggressive(false);

		if (trooper.action() == TrooperAction.CHARGING) {
			trooper.setAction(TrooperAction.IDLE);
		}

		trooper.getNavigation().stop();
		phase = Phase.READY;
		shotsLeft = 0;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = trooper.getTarget();

		if (target == null || !(trooper.level() instanceof ServerLevel level)) {
			return;
		}

		TrooperBalance b = TrooperBalance.get();
		double distance = trooper.distanceTo(target);
		boolean sees = trooper.getSensing().hasLineOfSight(target);

		if (sees != seeTime > 0) {
			seeTime = 0;
		}

		seeTime += sees ? 1 : -1;
		repath--;
		findLineCooldown--;

		if (++leaderAge >= LEADER_REFRESH) {
			leaderAge = 0;
			leader = Squads.leader(trooper);
		}

		trooper.getLookControl().setLookAt(target, 30f, 30f);
		boolean interrupted = trooper.consumeInterrupt();
		move(target, distance, sees, b);
		shoot(level, target, distance, sees, interrupted, b);
	}

	// ---------------------------------------------------------------- movement

	private void move(LivingEntity target, double distance, boolean sees, TrooperBalance b) {
		Vec3 sidestep = trooper.repositionTarget();

		if (sidestep != null) {
			if (sidestepTicks++ == 0) {
				trooper.getNavigation().moveTo(sidestep.x, sidestep.y, sidestep.z, SIDESTEP_SPEED);
			}

			if (trooper.position().distanceToSqr(sidestep) < 1.5 * 1.5 || sidestepTicks > 5 && trooper.getNavigation().isDone()
					|| sidestepTicks > SIDESTEP_TIMEOUT) {
				trooper.clearReposition();
				sidestepTicks = 0;
			}

			return;
		}

		if (backOff > 0) {
			backOff--;
			return;
		}

		Vec3 post = post(target, b);

		if (post != null) {
			if (trooper.position().distanceToSqr(post) > SPOT_TOLERANCE * SPOT_TOLERANCE) {
				if (repath <= 0) {
					repath = REPATH_TICKS;
					trooper.getNavigation().moveTo(post.x, post.y, post.z, MOVE_SPEED);
				}

				return;
			}

			trooper.getNavigation().stop();
			strafe(0f, b);
			return;
		}

		double[] band = band(b);

		if (!sees) {
			if (-seeTime >= REPOSITION_AFTER && findLineCooldown <= 0) {
				findLineCooldown = 30;
				findFiringPosition(target, band);
			}

			return;
		}

		if (distance > band[1]) {
			if (repath <= 0) {
				repath = REPATH_TICKS;
				trooper.getNavigation().moveTo(target, MOVE_SPEED);
			}

			return;
		}

		if (distance < band[0] * 0.5) {
			Vec3 away = DefaultRandomPos.getPosAway(trooper, 10, 5, target.position());

			if (away != null && target.distanceToSqr(away) > target.distanceToSqr(trooper)) {
				trooper.getNavigation().moveTo(away.x, away.y, away.z, BACK_OFF_SPEED);
				backOff = BACK_OFF_TICKS;
				return;
			}
		}

		// In the band with a clear line: hold ground and strafe, backing up when too close.
		trooper.getNavigation().stop();
		strafe(distance < band[0] ? -0.5f : 0f, b);
	}

	private void strafe(float forward, TrooperBalance b) {
		if (--strafeFlipIn <= 0 || trooper.horizontalCollision) {
			strafeClockwise = !strafeClockwise;
			strafeFlipIn = 20 + trooper.getRandom().nextInt(21);
		}

		float sideways = phase == Phase.CHARGING ? 0.25f : 0.45f;
		trooper.getMoveControl().strafe(forward, strafeClockwise ? sideways : -sideways);
	}

	/**
	 * The spot this trooper's role wants, or null to hold the distance band: the flanker's post 90
	 * degrees off the squad's line, or the medic's place behind the leader.
	 */
	private @Nullable Vec3 post(LivingEntity target, TrooperBalance b) {
		TrooperEntity lead = leader != null && leader.isAlive() ? leader : trooper;

		if (trooper.isFlanker() && trooper.role() == TrooperRole.RIFLEMAN) {
			Vec3 line = target.position().subtract(lead.position()).multiply(1, 0, 1);

			if (lead == trooper || line.lengthSqr() < 1.0e-4) {
				line = target.position().subtract(trooper.position()).multiply(1, 0, 1);
			}

			if (line.lengthSqr() < 1.0e-4) {
				return null;
			}

			Vec3 side = new Vec3(-line.z, 0, line.x).normalize().scale(flankSide());
			return target.position().add(side.scale(flankDistance(b)));
		}

		if (trooper.role() == TrooperRole.MEDIC && lead != trooper) {
			Vec3 away = lead.position().subtract(target.position()).multiply(1, 0, 1);

			if (away.lengthSqr() < 1.0e-4) {
				return null;
			}

			return lead.position().add(away.normalize().scale(MEDIC_BEHIND));
		}

		return null;
	}

	/** Which side of the line the flanker takes: fixed per trooper, so it does not swap sides. */
	private int flankSide() {
		return (trooper.getUUID().getLeastSignificantBits() & 1L) == 0 ? 1 : -1;
	}

	/** The flanker's distance from the target, fixed per trooper within {@code flankerRange}. */
	private double flankDistance(TrooperBalance b) {
		int min = b.flankerRange[0];
		int max = b.flankerRange[b.flankerRange.length - 1];
		return min + Math.floorMod(trooper.getUUID().getMostSignificantBits(), Math.max(1, max - min + 1));
	}

	/** {min, max} distance this role keeps when it has no post. */
	private double[] band(TrooperBalance b) {
		return switch (trooper.role()) {
			case GRENADIER -> new double[] {b.grenadeRange[0] + 2, b.grenadeRange[b.grenadeRange.length - 1]};
			case MEDIC -> new double[] {b.bandMax, b.bandMax + 4};
			default -> new double[] {b.bandMin, b.bandMax};
		};
	}

	/** Walks to a nearby spot with a clear line to the target, preferring the band. */
	private void findFiringPosition(LivingEntity target, double[] band) {
		Vec3 targetEye = target.getEyePosition();
		Vec3 fallback = null;

		for (int i = 0; i < 8; i++) {
			Vec3 candidate = LandRandomPos.getPosTowards(trooper, 10, 4, target.position());

			if (candidate == null) {
				continue;
			}

			Vec3 eye = candidate.add(0, trooper.getEyeHeight(), 0);
			HitResult hit = trooper.level().clip(new ClipContext(eye, targetEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, trooper));

			if (hit.getType() != HitResult.Type.MISS) {
				continue;
			}

			double d = Math.sqrt(candidate.distanceToSqr(target.position()));

			if (d >= band[0] * 0.75 && d <= band[1]) {
				trooper.getNavigation().moveTo(candidate.x, candidate.y, candidate.z, MOVE_SPEED);
				return;
			}

			if (fallback == null) {
				fallback = candidate;
			}
		}

		if (fallback != null) {
			trooper.getNavigation().moveTo(fallback.x, fallback.y, fallback.z, MOVE_SPEED);
		} else {
			trooper.getNavigation().moveTo(target, MOVE_SPEED);
		}
	}

	// ---------------------------------------------------------------- firing

	private void shoot(ServerLevel level, LivingEntity target, double distance, boolean sees, boolean interrupted, TrooperBalance b) {
		switch (phase) {
			case READY -> {
				if (timer > 0) {
					timer--;
				} else if (sees && distance <= maxRange(b) && mayFire()) {
					startCharge(b);
				}
			}
			case CHARGING -> {
				lostSightTicks = sees ? 0 : lostSightTicks + 1;

				if (interrupted || lostSightTicks > CHARGE_SIGHT_GRACE || !mayFire()) {
					// Shot abandoned: the tell ends without a bolt.
					trooper.setAction(TrooperAction.IDLE);
					phase = Phase.READY;
					timer = 10;
				} else if (--timer <= 0) {
					trooper.setAction(TrooperAction.IDLE);
					openFire(level, target, b);
				}
			}
			case BURST -> {
				if (ForceStatus.isDisarmed(trooper)) {
					endBurst(b);
				} else if (--timer <= 0) {
					trooper.fireAt(level, target, b.republicBoltDamage, b.boltSpeed);

					if (--shotsLeft > 0) {
						timer = Math.max(1, b.burstGap);
					} else {
						endBurst(b);
					}
				}
			}
			case COOLDOWN -> {
				if (--timer <= 0) {
					phase = Phase.READY;
					timer = 0;
				}
			}
		}
	}

	/** Whether nothing stops this trooper firing: not disarmed, holding fire, venting or sidestepping. */
	private boolean mayFire() {
		return !ForceStatus.isDisarmed(trooper) && !trooper.isHoldingFire() && !trooper.isVenting() && trooper.repositionTarget() == null;
	}

	private double maxRange(TrooperBalance b) {
		double far = trooper.isFlanker() ? b.flankerRange[b.flankerRange.length - 1] : band(b)[1];
		return far + REACH_BEYOND_BAND;
	}

	/** The charge tell: muzzle glow and whine for {@code chargeTicks}, seen by everyone tracking the trooper. */
	private void startCharge(TrooperBalance b) {
		phase = Phase.CHARGING;
		timer = Math.max(1, b.chargeTicks);
		lostSightTicks = 0;
		trooper.setAction(TrooperAction.CHARGING);
		EffectEvents.toTracking(trooper, TrooperEffects.TROOPER_CHARGE, trooper.muzzlePosition(), trooper.getLookAngle(), trooper.getId(),
				b.chargeTicks, trooper.boltStyle().ordinal());
	}

	/** The tell is over: the first bolt of a burst, the Imperial bolt or the pistol shot. */
	private void openFire(ServerLevel level, LivingEntity target, TrooperBalance b) {
		boolean rallied = trooper.isRallied();

		switch (trooper.weapon()) {
			case REPUBLIC_RIFLE -> {
				shotsLeft = Math.max(1, b.burstSize);
				trooper.fireAt(level, target, b.republicBoltDamage, b.boltSpeed);

				if (--shotsLeft > 0) {
					phase = Phase.BURST;
					timer = Math.max(1, b.burstGap);
				} else {
					endBurst(b);
				}
			}
			case IMPERIAL_RIFLE -> {
				trooper.fireAt(level, target, b.imperialBoltDamage, b.imperialBoltSpeed);
				phase = Phase.COOLDOWN;
				timer = TrooperRules.shotCooldown(b.imperialCooldown, rallied, b);
			}
			case PISTOL -> {
				trooper.fireAt(level, target, b.pistolDamage, b.boltSpeed);
				phase = Phase.COOLDOWN;
				timer = TrooperRules.shotCooldown(b.pistolCooldown, rallied, b);
			}
			case NONE -> phase = Phase.READY;
		}
	}

	/** A Republic burst ended: the burst gap, with the vent on top after every {@code ventAfterBursts} bursts. */
	private void endBurst(TrooperBalance b) {
		shotsLeft = 0;
		phase = Phase.COOLDOWN;
		timer = TrooperRules.shotCooldown(b.burstCooldown, trooper.isRallied(), b);

		if (++burstsSinceVent >= Math.max(1, b.ventAfterBursts)) {
			burstsSinceVent = 0;
			trooper.startVent();
			timer += b.ventTicks;
		}
	}
}
