package com.oldrepublic.enemy.mandalorian;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The Mandalorian hunter's fight (spec MILESTONE_4.md 10.1): hold the rifle band (8 to 18 blocks),
 * strafe, and fire tells-first bursts of 2 gold bolts with a 40-tick cooldown. Between bursts it
 * reaches for a gadget, each with its tell: the wrist flamer when the target is under 4 blocks, the
 * jet hop every 200 ticks or when rushed (to high ground or a flank), the wrist rocket every 300
 * ticks at 8 to 20 blocks. Any interrupt (stagger, Push, shock) drops the tell in progress.
 */
final class HunterCombatGoal extends Goal {
	private enum Phase {
		READY,
		RIFLE_TELL,
		BURST_GAP,
		GADGET_TELL,
		RECOVERY
	}

	private static final int SIGHT_GRACE = 6;
	private static final int BARK_CHANCE_IN = 3;

	private final MandalorianHunter mob;
	private Phase phase = Phase.READY;
	private int timer;
	private int shotsLeft;
	private int lostSight;
	private int sinceHop;
	private int sinceRocket;
	private int sinceFlame;
	private int repath;
	private boolean strafeClockwise;
	private int strafeFlip;
	private int gadget = MandalorianHunter.GADGET_NONE;
	private @Nullable Vec3 hopSpot;

	HunterCombatGoal(MandalorianHunter mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && mob.isFighting();
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
		HunterBalance b = HunterBalance.get();
		// The first gadgets come a little early, so a short fight still shows them.
		sinceHop = b.jetIntervalTicks / 2;
		sinceRocket = b.rocketIntervalTicks / 2;
		sinceFlame = b.flamerCooldownTicks;

		if (mob.getRandom().nextInt(BARK_CHANCE_IN) == 0) {
			mob.playSound(ModSounds.MANDALORIAN_BARK, 1f, 0.95f + mob.getRandom().nextFloat() * 0.1f);
		}
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		cancelTells();
		mob.stopFlame();
		mob.getNavigation().stop();
		phase = Phase.READY;
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

		HunterBalance b = HunterBalance.get();
		sinceHop++;
		sinceRocket++;
		sinceFlame++;
		mob.getLookControl().setLookAt(target, 30f, 30f);

		if (mob.consumeInterrupt() && (phase == Phase.RIFLE_TELL || phase == Phase.GADGET_TELL)) {
			cancelTells();
			phase = Phase.READY;
			timer = 10;
		}

		if (mob.isHopping()) {
			return;
		}

		double distance = mob.distanceTo(target);
		boolean sees = mob.getSensing().hasLineOfSight(target);

		if (mob.isFlaming()) {
			// Walks the cone slowly onto the target.
			mob.getNavigation().stop();
			return;
		}

		if (phase != Phase.GADGET_TELL) {
			move(target, distance, sees, b);
		} else {
			mob.getNavigation().stop();
		}

		switch (phase) {
			case READY -> {
				if (--timer <= 0) {
					choose(level, target, distance, sees, b);
				}
			}
			case RIFLE_TELL -> {
				lostSight = sees ? 0 : lostSight + 1;

				if (lostSight > SIGHT_GRACE) {
					cancelTells();
					phase = Phase.READY;
					timer = 10;
				} else if (--timer <= 0) {
					shoot(level, target, b);
				}
			}
			case BURST_GAP -> {
				if (--timer <= 0) {
					shoot(level, target, b);
				}
			}
			case GADGET_TELL -> {
				if (--timer <= 0) {
					useGadget(level, target, distance, b);
				}
			}
			case RECOVERY -> {
				if (--timer <= 0) {
					phase = Phase.READY;
				}
			}
		}
	}

	/** Picks the next action: flamer, hop, rocket, then the rifle. */
	private void choose(ServerLevel level, LivingEntity target, double distance, boolean sees, HunterBalance b) {
		if (sees && HunterRules.flamerDue(b, sinceFlame, distance)) {
			tell(target, MandalorianHunter.GADGET_FLAMER, b.flamerTellTicks, HunterEffects.TELL_FLAMER);
			return;
		}

		if (mob.onGround() && HunterRules.hopDue(b, sinceHop, distance)) {
			hopSpot = mob.findHopSpot(level, target, b.jetMinDistance, b.jetMaxDistance);

			if (hopSpot != null) {
				tell(target, MandalorianHunter.GADGET_JET, b.jetTellTicks, HunterEffects.TELL_JET);
				return;
			}

			// Nowhere to land: look again a little later.
			sinceHop = b.jetIntervalTicks - 40;
		}

		if (sees && HunterRules.rocketDue(b, sinceRocket, distance)) {
			tell(target, MandalorianHunter.GADGET_ROCKET, b.rocketTellTicks, HunterEffects.TELL_ROCKET);
			return;
		}

		if (sees && distance <= b.maxRange + 4) {
			shotsLeft = Math.max(1, b.burst);
			phase = Phase.RIFLE_TELL;
			timer = Math.max(1, b.rifleTellTicks);
			lostSight = 0;
			mob.setCharging(true);
		}
	}

	/** A gadget's tell: the pose (synced) and the {@code hunter_tell} event. The rocket's red dot sits on the target. */
	private void tell(LivingEntity target, int which, int ticks, int variant) {
		phase = Phase.GADGET_TELL;
		gadget = which;
		timer = Math.max(1, ticks);
		mob.setGadget(which);
		mob.getNavigation().stop();
		int entity = variant == HunterEffects.TELL_ROCKET ? target.getId() : mob.getId();
		EffectEvents.toTracking(mob, HunterEffects.TELL, mob.wristPosition(), null, entity, ticks, variant);
	}

	private void useGadget(ServerLevel level, LivingEntity target, double distance, HunterBalance b) {
		int which = gadget;
		cancelTells();
		phase = Phase.RECOVERY;
		timer = 10;

		switch (which) {
			case MandalorianHunter.GADGET_FLAMER -> {
				sinceFlame = 0;

				if (distance < b.flamerRange + 1.5) {
					mob.startFlame(level, b.flamerTicks, b.flamerDamage, b.flamerDamageInterval, b.flamerConeLength, b.flamerConeDegrees, b.flamerFireSeconds);
				}
			}
			case MandalorianHunter.GADGET_JET -> {
				sinceHop = 0;

				if (hopSpot != null) {
					mob.startHop(level, hopSpot, b.jetFlightTicks, b.jetArcHeight);
				}
			}
			case MandalorianHunter.GADGET_ROCKET -> {
				sinceRocket = 0;
				mob.fireRocket(level, target, b.rocketSpeed, b.rocketDamage, b.rocketRadius, b.rocketLifetimeTicks);
			}
			default -> {
			}
		}
	}

	private void shoot(ServerLevel level, LivingEntity target, HunterBalance b) {
		mob.setCharging(false);
		mob.fireBolt(level, target, b.rifleDamage, b.boltSpeed, b.boltLifetimeTicks, b.aimInaccuracy, b.lead, b.speedLead, ModSounds.BLASTER_FIRE);

		if (--shotsLeft > 0) {
			phase = Phase.BURST_GAP;
			timer = Math.max(2, b.burstGapTicks);
		} else {
			phase = Phase.RECOVERY;
			timer = Math.max(1, b.burstCooldownTicks);
		}
	}

	private void cancelTells() {
		mob.setCharging(false);
		mob.setGadget(MandalorianHunter.GADGET_NONE);
		gadget = MandalorianHunter.GADGET_NONE;
		hopSpot = null;
	}

	/** Holds the band: closes in when far or blind, strafes in it, backs off when too close. */
	private void move(LivingEntity target, double distance, boolean sees, HunterBalance b) {
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
		float sideways = phase == Phase.RIFLE_TELL ? 0.3f : 0.5f;
		mob.getMoveControl().strafe(forward, strafeClockwise ? sideways : -sideways);
	}
}
