package com.oldrepublic.enemy.cartel;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.hymn.Knock;
import com.oldrepublic.enemy.trooper.FragGrenade;
import com.oldrepublic.enemy.trooper.TrooperEffects;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberToggle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A cartel enforcer's fight (spec MILESTONE_5.md 12.1), by kit. Every heavy blow has its tell, and
 * any interrupt (a stagger, a Push, a shock) drops the tell in progress:
 * <ul>
 * <li><b>Gunman</b>: holds 7 to 18 blocks, strafes, and fires tells-first bursts of 2 rifle bolts
 * (the muzzle glow, synced) with a 40-tick cooldown; the M1 cover goal takes it to cover when hurt.</li>
 * <li><b>Bruiser</b>: walks in and swings a two-blow string, each blow behind the M2 {@code npc_windup}
 * (the full tell, then the glint only): a vibrosword cut, then a shove that knocks the target back.</li>
 * <li><b>Captain</b>: a blaster pistol (one tells-first shot), a frag grenade every 200 ticks at 5 to 16
 * blocks behind the M3 {@code grenade_tell}, and once per 400 ticks the call that brings every
 * enforcer within 24 blocks onto its target.</li>
 * </ul>
 */
final class CartelCombatGoal extends Goal {
	private enum Phase {
		READY,
		/** A tell is showing; the blow or shot comes when it runs out. */
		TELL,
		/** Between the shots of one burst. */
		BURST_GAP,
		RECOVERY
	}

	private enum Move {
		SHOT,
		SWORD,
		SHOVE,
		FRAG
	}

	/** Ticks of lost sight a shot's tell survives. */
	private static final int SIGHT_GRACE = 6;
	/** Retry delay after a cancelled tell. */
	private static final int RETRY_TICKS = 10;
	/** A cancelled frag may come again this much later. */
	private static final int FRAG_RETRY_TICKS = 60;

	private final CartelEnforcer mob;
	private Phase phase = Phase.READY;
	private Move move = Move.SHOT;
	private int timer;
	private int shotsLeft;
	private int lostSight;
	private int repath;
	private int strafeFlip;
	private boolean strafeClockwise;
	private long nextFrag;
	private long nextCall;
	private @Nullable Vec3 fragAt;

	CartelCombatGoal(CartelEnforcer mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && target.level() == mob.level() && !StandingTargets.ignored(target);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		phase = Phase.READY;
		timer = RETRY_TICKS;
		shotsLeft = 0;
		strafeFlip = 20;
		long now = mob.level().getGameTime();
		CartelBalance b = CartelBalance.get();
		// The first frag comes a little early, so a short fight still shows it.
		nextFrag = Math.max(nextFrag, now + b.fragCooldownTicks / 3);
		LivingEntity target = mob.getTarget();

		if (target != null) {
			StandingTargets.onAttack(mob, mob, target, b.engageTicks);

			if (mob.variant() == CartelEnforcer.Variant.CAPTAIN && now >= nextCall && mob.level() instanceof ServerLevel level) {
				nextCall = now + b.callCooldownTicks;
				call(level, target, b, now);
			}
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
		LivingEntity target = mob.getTarget();

		if (target == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}

		CartelBalance b = CartelBalance.get();
		long now = level.getGameTime();
		double distance = mob.distanceTo(target);
		boolean sees = mob.getSensing().hasLineOfSight(target);
		mob.getLookControl().setLookAt(target, 30f, 30f);

		if (mob.consumeInterrupt() && phase == Phase.TELL) {
			if (move == Move.FRAG) {
				nextFrag = now + FRAG_RETRY_TICKS;
			}

			cancelTell();
			phase = Phase.READY;
			timer = RETRY_TICKS;
		}

		if (phase == Phase.TELL && (move == Move.FRAG || move == Move.SWORD || move == Move.SHOVE)) {
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
				if (move == Move.SHOT) {
					lostSight = sees ? 0 : lostSight + 1;

					if (lostSight > SIGHT_GRACE || ForceStatus.isDisarmed(mob)) {
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
					timer = mob.variant() == CartelEnforcer.Variant.BRUISER ? Math.max(1, b.stringCooldownTicks) : 1;
				}
			}
		}
	}

	// ---------------------------------------------------------------- choosing and landing

	private void choose(ServerLevel level, LivingEntity target, double distance, boolean sees, CartelBalance b, long now) {
		if (ForceStatus.isDisarmed(mob)) {
			timer = RETRY_TICKS;
			return;
		}

		switch (mob.variant()) {
			case BRUISER -> {
				if (sees && distance <= b.swordReach) {
					startTell(Move.SWORD, b.stringWindupTicks, now);
					windup(SaberEffects.WINDUP_FIRST, b.stringWindupTicks);
				}
			}
			case CAPTAIN -> {
				if (sees && now >= nextFrag && distance >= b.fragMinRange && distance <= b.fragMaxRange) {
					fragAt = target.position();
					startTell(Move.FRAG, b.fragTellTicks, now);
					mob.setAction(CartelEnforcer.ACTION_GRENADE);
					Vec3 toward = fragAt.subtract(mob.position());
					EffectEvents.toTracking(mob, TrooperEffects.GRENADE_TELL, handPosition(), toward.lengthSqr() > 1.0e-4 ? toward.normalize() : mob.getLookAngle(),
							mob.getId(), b.fragTellTicks, 0);
				} else if (sees && distance <= b.fragMaxRange + 4) {
					shotsLeft = 1;
					startTell(Move.SHOT, b.pistolTellTicks, now);
					mob.setCharging(true);
				}
			}
			default -> {
				if (sees && distance <= b.rifleMaxRange + 4) {
					shotsLeft = Math.max(1, b.rifleBurst);
					startTell(Move.SHOT, b.rifleTellTicks, now);
					mob.setCharging(true);
				}
			}
		}
	}

	private void startTell(Move next, int ticks, long now) {
		phase = Phase.TELL;
		move = next;
		timer = Math.max(1, ticks);
		lostSight = 0;
		mob.tellLog().tell(now, ticks);
	}

	/** The bruiser's tell: the blade drawn back (the pose from the event), the glint, and on the first blow the wind-up sound. */
	private void windup(int variant, int ticks) {
		Vec3 hand = mob.position().add(0, mob.getBbHeight() * 0.75, 0);
		EffectEvents.toTracking(mob, SaberEffects.NPC_WINDUP, hand, mob.getLookAngle(), mob.getId(), ticks, variant);
	}

	private void land(ServerLevel level, LivingEntity target, double distance, CartelBalance b, long now) {
		switch (move) {
			case SHOT -> {
				mob.tellLog().heavy(now);
				shoot(level, target, b);
			}
			case SWORD -> {
				mob.tellLog().heavy(now);
				mob.swing(InteractionHand.MAIN_HAND);

				if (distance <= b.swordReach + 0.4) {
					strike(level, target, b.swordDamage, SaberCombat.Strike.NPC_LIGHT);
				}

				// The shove follows behind the glint-only tell.
				startTell(Move.SHOVE, b.stringGapTicks, now);
				windup(SaberEffects.WINDUP_NEXT, b.stringGapTicks);
			}
			case SHOVE -> {
				mob.tellLog().heavy(now);
				mob.swing(InteractionHand.OFF_HAND);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.CARTEL_SHOVE, mob.getSoundSource(), 1f, 0.95f + mob.getRandom().nextFloat() * 0.1f);

				if (distance <= b.swordReach + 0.4
						&& strike(level, target, b.shoveDamage, SaberCombat.Strike.npcHeavy(SaberToggle.balance().heavyGuardDrainMult))) {
					Knock.away(target, mob.position(), b.shoveKnockback, 0.3);
				}

				phase = Phase.RECOVERY;
				timer = Math.max(1, b.stringRecoveryTicks);
			}
			case FRAG -> {
				mob.tellLog().heavy(now);
				mob.setAction(CartelEnforcer.ACTION_NONE);
				mob.swing(InteractionHand.MAIN_HAND);
				Vec3 aim = target.isAlive() && fragAt != null && target.position().distanceToSqr(fragAt) < 64 ? target.position()
						: fragAt != null ? fragAt : target.position();
				FragGrenade.throwAt(level, mob, aim, b.fragDamage, b.fragRadius);
				nextFrag = now + b.fragCooldownTicks;
				fragAt = null;
				phase = Phase.RECOVERY;
				timer = RETRY_TICKS;
			}
		}
	}

	/** Deals one blow as an NPC strike (a lit saber's guard and parry apply to it). True when it went through. */
	private boolean strike(ServerLevel level, LivingEntity target, float damage, SaberCombat.Strike strike) {
		DamageSource source = mob.damageSources().mobAttack(mob);
		boolean hurt = SaberCombat.strike(target, level, source, damage, strike);
		StandingTargets.onAttack(mob, mob, target, CartelBalance.get().engageTicks);
		return hurt;
	}

	private void shoot(ServerLevel level, LivingEntity target, CartelBalance b) {
		mob.setCharging(false);
		boolean captain = mob.variant() == CartelEnforcer.Variant.CAPTAIN;
		mob.fireBolt(level, target, captain ? b.pistolDamage : b.rifleDamage);
		StandingTargets.onAttack(mob, mob, target, b.engageTicks);

		if (--shotsLeft > 0) {
			phase = Phase.BURST_GAP;
			timer = Math.max(2, b.rifleBurstGapTicks);
		} else {
			phase = Phase.RECOVERY;
			timer = Math.max(1, captain ? b.pistolCooldownTicks : b.rifleCooldownTicks);
		}
	}

	/** Drops the tell in progress: no glow, no drawn-back arm, and the tell log forgets it. */
	private void cancelTell() {
		if (phase == Phase.TELL) {
			mob.tellLog().cancel();
		}

		mob.setCharging(false);
		mob.setAction(CartelEnforcer.ACTION_NONE);
		fragAt = null;
	}

	/** The captain's call (the kajidic's squad bark): every enforcer within {@code callRange} takes up its fight. */
	private void call(ServerLevel level, LivingEntity target, CartelBalance b, long now) {
		mob.playSound(ModSounds.CARTEL_BARK, 1.4f, 0.85f);
		AABB box = mob.getBoundingBox().inflate(b.callRange);
		StandingTargets.Settings s = CartelEnforcer.targetSettings();

		for (CartelEnforcer other : level.getEntitiesOfClass(CartelEnforcer.class, box, e -> e != mob && e.isAlive())) {
			if (other.distanceToSqr(mob) > b.callRange * b.callRange || other.getTarget() != null && other.getTarget().isAlive()) {
				continue;
			}

			if (target instanceof Player player) {
				StandingTargets.share(mob, other, player, now, s);

				if (StandingTargets.wantsToFight(other, other, player, now, s)) {
					other.setTarget(player);
				}
			} else {
				other.setTarget(target);
			}
		}
	}

	// ---------------------------------------------------------------- moving

	/**
	 * The bruiser walks in to sword's reach; the gunman holds 7 to 18 blocks and the captain 5 to 16,
	 * closing in when far or blind, strafing in the band and backing off when too close.
	 */
	private void walk(LivingEntity target, double distance, boolean sees, CartelBalance b) {
		if (mob.variant() == CartelEnforcer.Variant.BRUISER) {
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

		boolean captain = mob.variant() == CartelEnforcer.Variant.CAPTAIN;
		double min = captain ? b.fragMinRange : b.rifleMinRange;
		double max = captain ? b.fragMaxRange : b.rifleMaxRange;

		if (!sees || distance > max) {
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

		float forward = distance < min ? -0.6f : 0f;
		float sideways = phase == Phase.TELL ? 0.3f : 0.5f;
		mob.getMoveControl().strafe(forward, strafeClockwise ? sideways : -sideways);
	}

	/** The captain's right hand, a little below the eyes (where the grenade's arming light shows). */
	private Vec3 handPosition() {
		float yaw = mob.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		return mob.getEyePosition().add(right.scale(0.35)).add(0, -0.25, 0);
	}
}
