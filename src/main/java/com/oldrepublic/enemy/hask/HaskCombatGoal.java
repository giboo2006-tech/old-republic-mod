package com.oldrepublic.enemy.hask;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.enemy.cartel.StandingTargets;
import com.oldrepublic.enemy.hymn.Knock;
import com.oldrepublic.enemy.mandalorian.HunterDamage;
import com.oldrepublic.enemy.mandalorian.HunterEffects;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberToggle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Clan Hask warrior's fight (spec MILESTONE_5.md 12.1), on the pattern of the M4
 * {@code HunterCombatGoal} (package-private there, so written again here). Every heavy blow has its
 * tell, and any interrupt (a stagger, a Push, a shock) drops the tell in progress:
 * <ul>
 * <li><b>Rifle</b>: holds 8 to 18 blocks and strafes; tells-first bursts of 2 bolts of 6 (the hunter's
 * shouldered-rifle tell), a jet hop to high ground or a flank every 200 ticks or when rushed
 * ({@code hunter_tell} jet), and the wrist rocket every 300 ticks ({@code hunter_tell} rocket: the red
 * dot on the target).</li>
 * <li><b>Blade</b>: vibrosword strings of two blows behind the M2 {@code npc_windup}; every 160 ticks,
 * at 5 to 12 blocks, the <b>jet-slam</b>: the pack whines and it crouches for 20 ticks
 * ({@code hask_warrior} 0), then it jets onto the target and lands for 9 ({@code jet_slam}) and
 * knock-back within 3 blocks.</li>
 * <li><b>Champion</b>: the blade's strings (9 a blow) behind its clan-iron shield, and every 160 ticks
 * the <b>shield bash</b>: the shield drawn back for 15 ticks ({@code hask_warrior} 3), then 6,
 * knock-back 1.0 and a 20-tick stagger.</li>
 * </ul>
 */
final class HaskCombatGoal extends Goal {
	private enum Phase {
		READY,
		/** A tell is showing; the move comes when it runs out. */
		TELL,
		/** Between the bolts of one burst. */
		BURST_GAP,
		/** In the air on a jet-slam. */
		SLAM_FLIGHT,
		RECOVERY
	}

	private enum Move {
		RIFLE,
		HOP,
		ROCKET,
		CUT,
		CUT_NEXT,
		SLAM,
		BASH
	}

	private static final int SIGHT_GRACE = 6;
	private static final int RETRY_TICKS = 10;
	/** A hop is taken when the target comes this close (fraction of the rifle's minimum range). */
	private static final double RUSHED_FRACTION = 0.6;

	private final HaskWarrior mob;
	private Phase phase = Phase.READY;
	private Move move = Move.RIFLE;
	private int timer;
	private int shotsLeft;
	private int lostSight;
	private int repath;
	private int strafeFlip;
	private boolean strafeClockwise;
	private int sinceHop;
	private int sinceRocket;
	private int sinceSlam;
	private int sinceBash;
	private @Nullable Vec3 hopSpot;

	HaskCombatGoal(HaskWarrior mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && target.level() == mob.level() && mob.isFighting() && !StandingTargets.ignored(target);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse() || phase == Phase.SLAM_FLIGHT && mob.isHopping();
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		phase = Phase.READY;
		timer = RETRY_TICKS;
		HaskBalance b = HaskBalance.get();
		// The first specials come a little early, so a short fight still shows them.
		sinceHop = b.jetIntervalTicks / 2;
		sinceRocket = b.rocketIntervalTicks / 2;
		sinceSlam = b.jetSlamIntervalTicks / 2;
		sinceBash = b.bashIntervalTicks / 2;
		LivingEntity target = mob.getTarget();

		if (target != null) {
			StandingTargets.onAttack(mob, mob, target, b.engageTicks);
		}

		if (mob.getRandom().nextInt(3) == 0) {
			mob.playSound(ModSounds.HASK_BARK, 1f, 0.95f + mob.getRandom().nextFloat() * 0.1f);
		}
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		cancelTell();
		mob.getNavigation().stop();
		phase = Phase.READY;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}

		HaskBalance b = HaskBalance.get();
		long now = level.getGameTime();
		sinceHop++;
		sinceRocket++;
		sinceSlam++;
		sinceBash++;
		boolean interrupted = mob.consumeHaskInterrupt();

		if (phase == Phase.SLAM_FLIGHT) {
			if (interrupted) {
				// Knocked out of the air: the slam never lands.
				mob.tellLog().cancel();
				phase = Phase.RECOVERY;
				timer = RETRY_TICKS;
			} else if (!mob.isHopping()) {
				slamLands(level, b, now);
			}

			return;
		}

		LivingEntity target = mob.getTarget();

		if (target == null) {
			return;
		}

		mob.getLookControl().setLookAt(target, 30f, 30f);

		if (interrupted && phase == Phase.TELL) {
			cancelTell();
			phase = Phase.READY;
			timer = RETRY_TICKS;
		}

		if (mob.isHopping()) {
			return;
		}

		double distance = mob.distanceTo(target);
		boolean sees = mob.getSensing().hasLineOfSight(target);

		if (phase == Phase.TELL && move != Move.RIFLE) {
			mob.getNavigation().stop();
		} else {
			walk(target, distance, sees, b);
		}

		switch (phase) {
			case READY -> {
				if (--timer <= 0) {
					choose(level, target, distance, sees, b, now);
				}
			}
			case TELL -> {
				if (move == Move.RIFLE) {
					lostSight = sees ? 0 : lostSight + 1;

					if (lostSight > SIGHT_GRACE) {
						cancelTell();
						phase = Phase.READY;
						timer = RETRY_TICKS;
						return;
					}
				}

				if (--timer <= 0) {
					land(level, target, distance, b, now);
				}
			}
			case BURST_GAP -> {
				if (--timer <= 0) {
					shoot(level, target, b);
				}
			}
			case RECOVERY -> {
				if (--timer <= 0) {
					phase = Phase.READY;
					timer = mob.variant() == HaskWarrior.Variant.RIFLE ? 1 : Math.max(1, b.stringCooldownTicks);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- choosing

	private void choose(ServerLevel level, LivingEntity target, double distance, boolean sees, HaskBalance b, long now) {
		if (ForceStatus.isDisarmed(mob)) {
			timer = RETRY_TICKS;
			return;
		}

		switch (mob.variant()) {
			case RIFLE -> chooseRifle(level, target, distance, sees, b, now);
			case BLADE -> {
				if (sees && mob.onGround() && sinceSlam >= b.jetSlamIntervalTicks && distance >= b.jetSlamMinRange && distance <= b.jetSlamMaxRange) {
					startTell(Move.SLAM, b.jetSlamTellTicks, now);
					mob.setAction(HaskWarrior.ACTION_SLAM);
					EffectEvents.toTracking(mob, HaskEffects.EVENT, mob.position(), null, mob.getId(), b.jetSlamTellTicks, HaskEffects.SLAM_TELL);
					mob.playSound(ModSounds.HASK_SLAM_TELL, 1f, 1f);
				} else if (sees && distance <= b.swordReach) {
					startTell(Move.CUT, b.windupTicks, now);
					windup(SaberEffects.WINDUP_FIRST, b.windupTicks);
				}
			}
			case CHAMPION -> {
				if (sees && sinceBash >= b.bashIntervalTicks && distance <= b.bashReach) {
					startTell(Move.BASH, b.bashTellTicks, now);
					mob.setAction(HaskWarrior.ACTION_BASH);
					EffectEvents.toTracking(mob, HaskEffects.EVENT, shieldPoint(), mob.getLookAngle(), mob.getId(), b.bashTellTicks, HaskEffects.BASH_TELL);
					mob.playSound(ModSounds.HASK_BASH_TELL, 1f, 1f);
				} else if (sees && distance <= b.swordReach) {
					startTell(Move.CUT, b.windupTicks, now);
					windup(SaberEffects.WINDUP_FIRST, b.windupTicks);
				}
			}
		}
	}

	/** The rifle's choice: a hop when rushed or due, the rocket when due, else a burst. */
	private void chooseRifle(ServerLevel level, LivingEntity target, double distance, boolean sees, HaskBalance b, long now) {
		boolean rushed = distance < b.minRange * RUSHED_FRACTION;

		if (mob.onGround() && (sinceHop >= b.jetIntervalTicks || rushed && sinceHop >= b.jetIntervalTicks / 4)) {
			hopSpot = mob.findHopSpot(level, target, b.jetMinDistance, b.jetMaxDistance);

			if (hopSpot != null) {
				startTell(Move.HOP, b.jetTellTicks, now);
				gadgetTell(target, MandalorianHunter.GADGET_JET, b.jetTellTicks, HunterEffects.TELL_JET);
				return;
			}

			// Nowhere to land: look again a little later.
			sinceHop = b.jetIntervalTicks - 40;
		}

		if (sees && sinceRocket >= b.rocketIntervalTicks && distance >= b.minRange && distance <= b.maxRange + 2) {
			startTell(Move.ROCKET, b.rocketTellTicks, now);
			gadgetTell(target, MandalorianHunter.GADGET_ROCKET, b.rocketTellTicks, HunterEffects.TELL_ROCKET);
			return;
		}

		if (sees && distance <= b.maxRange + 4) {
			shotsLeft = Math.max(1, b.burst);
			startTell(Move.RIFLE, b.rifleTellTicks, now);
			mob.charging(true);
		}
	}

	private void startTell(Move next, int ticks, long now) {
		phase = Phase.TELL;
		move = next;
		timer = Math.max(1, ticks);
		lostSight = 0;

		if (next != Move.HOP) {
			// A hop is a move, not a blow: its tell still shows, but it lands nothing.
			mob.tellLog().tell(now, ticks);
		}
	}

	/** A gadget's tell: the hunter's pose (synced) and the M4 {@code hunter_tell} event. The rocket's red dot sits on the target. */
	private void gadgetTell(LivingEntity target, int gadget, int ticks, int variant) {
		mob.gadgetPose(gadget);
		mob.getNavigation().stop();
		int entity = variant == HunterEffects.TELL_ROCKET ? target.getId() : mob.getId();
		EffectEvents.toTracking(mob, HunterEffects.TELL, mob.wristPosition(), null, entity, ticks, variant);
	}

	/** A sword blow's tell: the blade drawn back (the pose from the event), the glint, and on the first blow the wind-up sound. */
	private void windup(int variant, int ticks) {
		Vec3 hand = mob.position().add(0, mob.getBbHeight() * 0.75, 0);
		EffectEvents.toTracking(mob, SaberEffects.NPC_WINDUP, hand, mob.getLookAngle(), mob.getId(), ticks, variant);
	}

	// ---------------------------------------------------------------- landing

	private void land(ServerLevel level, LivingEntity target, double distance, HaskBalance b, long now) {
		switch (move) {
			case RIFLE -> {
				mob.tellLog().heavy(now);
				shoot(level, target, b);
			}
			case HOP -> {
				cancelTell();
				sinceHop = 0;
				phase = Phase.RECOVERY;
				timer = RETRY_TICKS;

				if (hopSpot != null) {
					mob.startHop(level, hopSpot, b.jetFlightTicks, b.jetArcHeight);
				}

				hopSpot = null;
			}
			case ROCKET -> {
				mob.tellLog().heavy(now);
				cancelTell();
				sinceRocket = 0;
				mob.fireRocket(level, target, b.rocketSpeed, b.rocketDamage, b.rocketRadius, b.rocketLifetimeTicks);
				StandingTargets.onAttack(mob, mob, target, b.engageTicks);
				phase = Phase.RECOVERY;
				timer = RETRY_TICKS;
			}
			case CUT -> {
				mob.tellLog().heavy(now);
				cut(level, target, distance, b);
				startTell(Move.CUT_NEXT, b.gapTicks, now);
				windup(SaberEffects.WINDUP_NEXT, b.gapTicks);
			}
			case CUT_NEXT -> {
				mob.tellLog().heavy(now);
				cut(level, target, distance, b);
				phase = Phase.RECOVERY;
				timer = Math.max(1, b.recoveryTicks);
			}
			case SLAM -> launchSlam(level, target, b);
			case BASH -> bash(level, target, distance, b, now);
		}
	}

	/** One vibrosword blow, as an NPC strike (a lit saber's guard and parry apply). */
	private void cut(ServerLevel level, LivingEntity target, double distance, HaskBalance b) {
		mob.swing(InteractionHand.MAIN_HAND);

		if (distance <= b.swordReach + 0.4) {
			float damage = mob.variant() == HaskWarrior.Variant.CHAMPION ? b.championDamage : b.bladeDamage;
			SaberCombat.strike(target, level, mob.damageSources().mobAttack(mob), damage, SaberCombat.Strike.NPC_LIGHT);
			StandingTargets.onAttack(mob, mob, target, b.engageTicks);
		}
	}

	private void shoot(ServerLevel level, LivingEntity target, HaskBalance b) {
		mob.charging(false);
		mob.fireBolt(level, target, b.rifleDamage, b.boltSpeed, b.boltLifetimeTicks, b.aimInaccuracy, b.lead, b.speedLead, ModSounds.BLASTER_FIRE);
		StandingTargets.onAttack(mob, mob, target, b.engageTicks);

		if (--shotsLeft > 0) {
			phase = Phase.BURST_GAP;
			timer = Math.max(2, b.burstGapTicks);
		} else {
			phase = Phase.RECOVERY;
			timer = Math.max(1, b.burstCooldownTicks);
		}
	}

	// ---------------------------------------------------------------- the jet-slam

	/** After its tell: the jets fire and it arcs onto the target (a spot beside it, so it lands on its feet). */
	private void launchSlam(ServerLevel level, LivingEntity target, HaskBalance b) {
		mob.setAction(HaskWarrior.ACTION_NONE);
		sinceSlam = 0;
		Vec3 back = mob.position().subtract(target.position()).multiply(1, 0, 1);
		Vec3 spot = target.position().add(back.lengthSqr() < 1.0e-4 ? Vec3.ZERO : back.normalize().scale(0.9));

		if (!mob.fitsAt(spot)) {
			spot = target.position();
		}

		phase = Phase.SLAM_FLIGHT;
		mob.startHop(level, spot, b.jetSlamFlightTicks, b.jetArcHeight);
	}

	/** The landing: flame and dust, 9 ({@code jet_slam}) and knock-back to everything not of its side within the radius. */
	private void slamLands(ServerLevel level, HaskBalance b, long now) {
		mob.tellLog().heavy(now);
		Vec3 at = mob.position();
		EffectEvents.toTracking(mob, HaskEffects.EVENT, at, null, mob.getId(), b.jetSlamRadius, HaskEffects.SLAM);
		level.playSound(null, at.x, at.y, at.z, ModSounds.HASK_SLAM, mob.getSoundSource(), 1.3f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
		DamageSource source = level.damageSources().source(HunterDamage.JET_SLAM, mob, mob);
		double r = b.jetSlamRadius;

		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 1.5, r),
				e -> e != mob && e.isAlive() && !StandingTargets.ignored(e) && !Allegiances.friendly(mob, e) && hits(e))) {
			if (hit.distanceToSqr(at) > r * r + 1.0) {
				continue;
			}

			if (hit.hurtServer(level, source, b.jetSlamDamage)) {
				Knock.away(hit, at, b.jetSlamKnockback, 0.35);
			}
		}

		phase = Phase.RECOVERY;
		timer = Math.max(1, b.recoveryTicks);
	}

	/** Its area blows land on its target and on whatever fights its side; bystanders it is not fighting are spared. */
	private boolean hits(LivingEntity e) {
		LivingEntity target = mob.getTarget();
		return e == target || e instanceof Mob other && other.getTarget() == mob
				|| e instanceof Player p && StandingTargets.wantsToFight(mob, mob, p, mob.level().getGameTime(), HaskWarrior.targetSettings());
	}

	// ---------------------------------------------------------------- the shield bash

	private void bash(ServerLevel level, LivingEntity target, double distance, HaskBalance b, long now) {
		mob.tellLog().heavy(now);
		mob.setAction(HaskWarrior.ACTION_NONE);
		sinceBash = 0;
		mob.swing(InteractionHand.OFF_HAND);
		Vec3 at = shieldPoint();
		EffectEvents.toTracking(mob, HaskEffects.EVENT, at, mob.getLookAngle(), mob.getId(), 1f, HaskEffects.BASH);
		level.playSound(null, at.x, at.y, at.z, ModSounds.HASK_BASH, mob.getSoundSource(), 1.2f, 0.9f + mob.getRandom().nextFloat() * 0.2f);

		if (distance <= b.bashReach + 0.5 && SaberCombat.strike(target, level, mob.damageSources().mobAttack(mob), b.bashDamage,
				SaberCombat.Strike.npcHeavy(SaberToggle.balance().heavyGuardDrainMult))) {
			Knock.away(target, mob.position(), b.bashKnockback, 0.3);
			ControlEffects.stagger(mob, target, b.bashStaggerTicks, 1);
			StandingTargets.onAttack(mob, mob, target, b.engageTicks);
		}

		phase = Phase.RECOVERY;
		timer = Math.max(1, b.recoveryTicks);
	}

	/** In front of its chest, on the shield arm's side. */
	private Vec3 shieldPoint() {
		Vec3 look = mob.getLookAngle().multiply(1, 0, 1);
		Vec3 ahead = look.lengthSqr() < 1.0e-4 ? Vec3.ZERO : look.normalize().scale(0.55);
		return mob.position().add(ahead).add(0, mob.getBbHeight() * 0.55, 0);
	}

	// ---------------------------------------------------------------- shared

	/** Drops the tell in progress: no glow, no gadget pose, no Hask pose, and the tell log forgets it. */
	private void cancelTell() {
		if (phase == Phase.TELL && move != Move.HOP) {
			mob.tellLog().cancel();
		}

		mob.charging(false);
		mob.gadgetPose(MandalorianHunter.GADGET_NONE);
		mob.setAction(HaskWarrior.ACTION_NONE);
		hopSpot = null;
	}

	/** The rifle holds its band; the blades walk in to sword's reach. */
	private void walk(LivingEntity target, double distance, boolean sees, HaskBalance b) {
		if (mob.variant() != HaskWarrior.Variant.RIFLE) {
			if (distance > b.swordReach * 0.85 || !sees) {
				if (--repath <= 0) {
					repath = 8;
					mob.getNavigation().moveTo(target, 1.15);
				}
			} else {
				mob.getNavigation().stop();
			}

			return;
		}

		if (!sees || distance > b.maxRange) {
			if (--repath <= 0) {
				repath = 10;
				mob.getNavigation().moveTo(target, 1.0);
			}

			return;
		}

		mob.getNavigation().stop();

		if (--strafeFlip <= 0 || mob.horizontalCollision) {
			strafeClockwise = !strafeClockwise;
			strafeFlip = 20 + mob.getRandom().nextInt(25);
		}

		float forward = distance < b.minRange ? -0.6f : 0f;
		float sideways = phase == Phase.TELL ? 0.3f : 0.5f;
		mob.getMoveControl().strafe(forward, strafeClockwise ? sideways : -sideways);
	}
}
