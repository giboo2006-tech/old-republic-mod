package com.oldrepublic.enemy.stormback;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.hymn.FoeTargets;
import com.oldrepublic.enemy.hymn.Knock;
import com.oldrepublic.enemy.hymn.StormCall;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The stormback's fight (spec MILESTONE_5.md 12.1), the M4 hollowhorn's pattern: from 4 to 14 blocks
 * with a clear view it lowers its head and paws grit for {@code chargeTellTicks} (the tell), then
 * charges in a straight line fixed at the tell's end, {@code chargeSpeed} blocks a tick for up to
 * {@code chargeRange} blocks. The first body it meets takes {@code chargeDamage} ({@code
 * chargedChargeDamage} while charged, and a {@code shock}) and strong knock-back; a solid block stops
 * it stunned for {@code stunTicks}. Up close it butts. Once per fight, below {@code roarBelow} of its
 * health, it rears and roars for {@code roarTellTicks} (the tell), then a bolt comes down on it: it is
 * charged at once, and players within {@code roarRadius} take {@code roarDamage} and a {@code shock}.
 * An interrupt (a stagger, a Push, a shock) drops either tell; a dropped roar comes again a little
 * later. Charges and stuns always run out, even when the target is gone.
 */
final class StormbackChargeGoal extends Goal {
	private enum Phase {
		APPROACH,
		TELL,
		CHARGE,
		STUNNED,
		ROAR
	}

	/** Reach of the head butt (a wide animal). */
	private static final double BUTT_REACH = 2.8;
	/** A charge that has run this long without reaching its distance stops anyway. */
	private static final int MAX_CHARGE_TICKS = 40;
	/** A roar dropped by an interrupt comes again after this long. */
	private static final int ROAR_RETRY_TICKS = 60;

	private final Stormback mob;
	private Phase phase = Phase.APPROACH;
	private int timer;
	private int cooldown;
	private int buttCooldown;
	private int roarRetry;
	private int repath;
	private @Nullable Vec3 chargeDir;
	private @Nullable Vec3 chargeFrom;

	StormbackChargeGoal(Stormback mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && target.level() == mob.level() && !FoeTargets.ignored(target);
	}

	@Override
	public boolean canContinueToUse() {
		return phase == Phase.CHARGE || phase == Phase.STUNNED || canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		phase = Phase.APPROACH;
		repath = 0;
	}

	@Override
	public void stop() {
		if (phase == Phase.TELL || phase == Phase.ROAR) {
			mob.tellLog().cancel();
		}

		mob.setAggressive(false);
		mob.setState(Stormback.STATE_IDLE);
		mob.getNavigation().stop();
		phase = Phase.APPROACH;
		chargeDir = null;
		chargeFrom = null;
	}

	@Override
	public void tick() {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}

		StormbackBalance b = StormbackBalance.get();
		long now = level.getGameTime();
		cooldown--;
		buttCooldown--;
		roarRetry--;

		if (mob.consumeInterrupt() && (phase == Phase.TELL || phase == Phase.ROAR)) {
			if (phase == Phase.ROAR) {
				roarRetry = ROAR_RETRY_TICKS;
			} else {
				cooldown = b.chargeCooldownTicks / 2;
			}

			mob.tellLog().cancel();
			toApproach();
			return;
		}

		switch (phase) {
			case APPROACH -> approach(level, b, now);
			case TELL -> tell(b);
			case CHARGE -> charge(level, b, now);
			case STUNNED -> stunned(b);
			case ROAR -> roar(level, b, now);
			default -> phase = Phase.APPROACH;
		}
	}

	// ---------------------------------------------------------------- approach, butt

	private void approach(ServerLevel level, StormbackBalance b, long now) {
		LivingEntity target = mob.getTarget();

		if (target == null) {
			return;
		}

		mob.getLookControl().setLookAt(target, 30f, 30f);

		if (!mob.hasRoared() && roarRetry <= 0 && mob.getHealth() < mob.getMaxHealth() * b.roarBelow) {
			startRoar(level, b, now);
			return;
		}

		double distance = horizontal(target.position().subtract(mob.position())).length();
		boolean sees = mob.getSensing().hasLineOfSight(target);

		if (cooldown <= 0 && sees && distance >= b.chargeMinRange && distance <= b.chargeRange) {
			startTell(target, b, now);
			return;
		}

		if (distance <= BUTT_REACH && buttCooldown <= 0) {
			butt(level, target, b);
			return;
		}

		if (--repath <= 0) {
			repath = 10;
			mob.getNavigation().moveTo(target, 1.2);
		}
	}

	private void butt(ServerLevel level, LivingEntity target, StormbackBalance b) {
		buttCooldown = Math.max(1, b.buttCooldownTicks);
		mob.swing(InteractionHand.MAIN_HAND);
		target.hurtServer(level, mob.damageSources().mobAttack(mob), b.buttDamage * mob.damageMultiplier());
	}

	// ---------------------------------------------------------------- the charge

	/** Head down, grit pawed up, a low bellow. */
	private void startTell(LivingEntity target, StormbackBalance b, long now) {
		phase = Phase.TELL;
		timer = Math.max(1, b.chargeTellTicks);
		mob.getNavigation().stop();
		mob.setState(Stormback.STATE_TELL);
		mob.tellLog().tell(now, b.chargeTellTicks);
		Vec3 dir = horizontal(target.position().subtract(mob.position()));
		EffectEvents.toTracking(mob, StormbackEffects.EVENT, mob.position(), dir.lengthSqr() < 1.0e-6 ? null : dir.normalize(), mob.getId(), b.chargeTellTicks,
				StormbackEffects.CHARGE_TELL);
		mob.playSound(ModSounds.STORMBACK_CHARGE_TELL, 1.3f, mob.getVoicePitch());
	}

	private void tell(StormbackBalance b) {
		LivingEntity target = mob.getTarget();
		mob.getNavigation().stop();

		if (target != null) {
			mob.getLookControl().setLookAt(target, 30f, 30f);
			face(horizontal(target.position().subtract(mob.position())));
		}

		if (--timer > 0) {
			return;
		}

		Vec3 dir = horizontal(target == null ? mob.getLookAngle() : target.position().subtract(mob.position()));

		if (dir.lengthSqr() < 1.0e-6) {
			mob.tellLog().cancel();
			endCharge(b);
			return;
		}

		phase = Phase.CHARGE;
		timer = 0;
		chargeDir = dir.normalize();
		chargeFrom = mob.position();
		mob.setState(Stormback.STATE_CHARGING);
	}

	private void charge(ServerLevel level, StormbackBalance b, long now) {
		Vec3 dir = chargeDir;
		Vec3 from = chargeFrom;

		if (dir == null || from == null) {
			mob.tellLog().cancel();
			endCharge(b);
			return;
		}

		timer++;
		face(dir);
		Vec3 move = dir.scale(b.chargeSpeed);
		mob.setDeltaMovement(move.x, mob.getDeltaMovement().y, move.z);

		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(0.4), this::canHit)) {
			boolean charged = mob.isCharged();
			mob.tellLog().heavy(now);
			hit.hurtServer(level, mob.damageSources().mobAttack(mob), charged ? b.chargedChargeDamage : b.chargeDamage);
			Knock.along(hit, dir, b.chargeKnockback, 0.35 * b.chargeKnockback);

			if (charged) {
				StormCall.shock(mob, hit, b.chargedShockTicks);
			}

			impact(level, hit.position().add(0, hit.getBbHeight() * 0.5, 0), dir);
			endCharge(b);
			return;
		}

		if (timer > 1 && mob.horizontalCollision) {
			// A wall, not a body: the tell covered no hit.
			mob.tellLog().cancel();
			impact(level, mob.position().add(dir.scale(mob.getBbWidth() * 0.6)).add(0, mob.getBbHeight() * 0.5, 0), dir);
			phase = Phase.STUNNED;
			timer = Math.max(1, b.stunTicks);
			mob.setState(Stormback.STATE_STUNNED);
			mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
			return;
		}

		if (horizontal(mob.position().subtract(from)).length() >= b.chargeRange || timer > MAX_CHARGE_TICKS) {
			mob.tellLog().cancel();
			endCharge(b);
		}
	}

	/** Not itself or its kind; creative and spectator players by game mode (a test's mock player answers {@code isCreative()} true). */
	private boolean canHit(LivingEntity e) {
		return e != mob && e.isAlive() && !(e instanceof Stormback) && !FoeTargets.ignored(e);
	}

	private void impact(ServerLevel level, Vec3 at, Vec3 dir) {
		EffectEvents.toTracking(mob, StormbackEffects.EVENT, at, dir, mob.getId(), 1f, StormbackEffects.IMPACT);
		level.playSound(null, at.x, at.y, at.z, ModSounds.STORMBACK_IMPACT, mob.getSoundSource(), 1.4f, 0.85f + mob.getRandom().nextFloat() * 0.2f);
	}

	private void stunned(StormbackBalance b) {
		mob.getNavigation().stop();

		if (--timer <= 0) {
			endCharge(b);
		}
	}

	private void endCharge(StormbackBalance b) {
		cooldown = b.chargeCooldownTicks;
		toApproach();
	}

	// ---------------------------------------------------------------- the roar

	/** It rears and roars to the sky; static climbs its plates. */
	private void startRoar(ServerLevel level, StormbackBalance b, long now) {
		phase = Phase.ROAR;
		timer = Math.max(1, b.roarTellTicks);
		mob.getNavigation().stop();
		mob.setState(Stormback.STATE_ROAR);
		mob.tellLog().tell(now, b.roarTellTicks);
		EffectEvents.toTracking(mob, StormbackEffects.EVENT, mob.position().add(0, mob.getBbHeight(), 0), null, mob.getId(), b.roarTellTicks,
				StormbackEffects.ROAR_TELL);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.STORMBACK_ROAR, mob.getSoundSource(), 1.6f, mob.getVoicePitch());
	}

	private void roar(ServerLevel level, StormbackBalance b, long now) {
		mob.getNavigation().stop();

		if (--timer > 0) {
			return;
		}

		mob.markRoared();
		mob.tellLog().heavy(now);
		StormCall.strike(level, mob.position(), b.roarRadius, b.roarDamage, b.roarShockTicks, mob, e -> e instanceof Player);
		mob.charge(level);
		cooldown = Math.max(cooldown, 20);
		toApproach();
	}

	// ---------------------------------------------------------------- helpers

	private void toApproach() {
		phase = Phase.APPROACH;
		chargeDir = null;
		chargeFrom = null;
		repath = 0;
		mob.setState(Stormback.STATE_IDLE);
	}

	private void face(Vec3 dir) {
		if (dir.lengthSqr() < 1.0e-6) {
			return;
		}

		float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
		mob.setYRot(yaw);
		mob.yBodyRot = yaw;
		mob.yHeadRot = yaw;
	}

	private static Vec3 horizontal(Vec3 v) {
		return new Vec3(v.x, 0, v.z);
	}
}
