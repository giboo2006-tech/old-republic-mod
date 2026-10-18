package com.oldrepublic.enemy.creature;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The hollowhorn's fight (spec MILESTONE_4.md 10.3): from 4 to 14 blocks with a clear view it lowers
 * its head, snorts and scrapes a hoof for {@code chargeTellTicks}, then charges in a straight line
 * fixed at the tell's end: {@code chargeSpeed} blocks a tick for up to {@code chargeRange} blocks.
 * The first body it meets takes {@code chargeDamage} and strong knockback; a solid block stops it
 * stunned for {@code stunTicks}. Up close it butts with its horns instead. Sidestep and punish.
 */
final class HollowhornChargeGoal extends Goal {
	private enum Phase {
		APPROACH,
		TELL,
		CHARGE,
		STUNNED
	}

	/** Reach of the horn butt. */
	private static final double BUTT_REACH = 2.4;
	/** A charge that has run this long without reaching its distance stops anyway. */
	private static final int MAX_CHARGE_TICKS = 40;

	private final Hollowhorn mob;
	private Phase phase = Phase.APPROACH;
	private int timer;
	private int cooldown;
	private int buttCooldown;
	private int repath;
	private @Nullable Vec3 chargeDir;
	private @Nullable Vec3 chargeFrom;

	HollowhornChargeGoal(Hollowhorn mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && !mob.isCalf();
	}

	@Override
	public boolean canContinueToUse() {
		// A charge or a stun always runs out, even when the target is gone.
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
		mob.setAggressive(false);
		mob.setState(Hollowhorn.STATE_IDLE);
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

		CreatureBalance b = CreatureBalance.get();
		cooldown--;
		buttCooldown--;

		switch (phase) {
			case APPROACH -> approach(level, b);
			case TELL -> tell(b);
			case CHARGE -> charge(level, b);
			case STUNNED -> stunned(b);
			default -> phase = Phase.APPROACH;
		}
	}

	private void approach(ServerLevel level, CreatureBalance b) {
		LivingEntity target = mob.getTarget();

		if (target == null) {
			return;
		}

		mob.getLookControl().setLookAt(target, 30f, 30f);
		double distance = horizontal(target.position().subtract(mob.position())).length();

		if (CreatureRules.chargeDue(b, distance, cooldown, mob.getSensing().hasLineOfSight(target), mob.isCalf())) {
			startTell(target, b);
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

	/** Head down, a snort and a hoof scrape kicking dirt. */
	private void startTell(LivingEntity target, CreatureBalance b) {
		phase = Phase.TELL;
		timer = b.chargeTellTicks;
		mob.getNavigation().stop();
		mob.setState(Hollowhorn.STATE_TELL);
		Vec3 dir = horizontal(target.position().subtract(mob.position()));
		EffectEvents.toTracking(mob, BeastEffects.TELL, mob.getEyePosition(), dir.lengthSqr() < 1.0e-6 ? null : dir.normalize(), mob.getId(), timer,
				BeastEffects.HOLLOWHORN_CHARGE);
		mob.playSound(ModSounds.HOLLOWHORN_SNORT, 1.2f, mob.getVoicePitch());
	}

	private void tell(CreatureBalance b) {
		LivingEntity target = mob.getTarget();
		mob.getNavigation().stop();

		if (target != null) {
			mob.getLookControl().setLookAt(target, 30f, 30f);
			face(horizontal(target.position().subtract(mob.position())));
		}

		if (--timer > 0) {
			return;
		}

		Vec3 dir = target == null ? mob.getLookAngle() : target.position().subtract(mob.position());
		dir = horizontal(dir);

		if (dir.lengthSqr() < 1.0e-6) {
			endCharge(b);
			return;
		}

		phase = Phase.CHARGE;
		timer = 0;
		chargeDir = dir.normalize();
		chargeFrom = mob.position();
		mob.setState(Hollowhorn.STATE_CHARGING);
		EffectEvents.toTracking(mob, BeastEffects.TELL, mob.position(), chargeDir, mob.getId(), (float) b.chargeRange, BeastEffects.HOLLOWHORN_RUN);
		mob.playSound(ModSounds.HOLLOWHORN_CHARGE, 1.4f, mob.getVoicePitch());
	}

	private void charge(ServerLevel level, CreatureBalance b) {
		if (chargeDir == null || chargeFrom == null) {
			endCharge(b);
			return;
		}

		timer++;
		face(chargeDir);
		Vec3 move = chargeDir.scale(b.chargeSpeed);
		mob.setDeltaMovement(move.x, mob.getDeltaMovement().y, move.z);

		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(0.4), this::canHit)) {
			hit.hurtServer(level, mob.damageSources().mobAttack(mob), b.chargeDamage);
			shove(hit, chargeDir, b.chargeKnockback);
			impact(level, hit.position().add(0, hit.getBbHeight() * 0.5, 0));
			endCharge(b);
			return;
		}

		if (timer > 1 && mob.horizontalCollision) {
			impact(level, mob.position().add(chargeDir.scale(mob.getBbWidth() * 0.6)).add(0, mob.getBbHeight() * 0.5, 0));
			phase = Phase.STUNNED;
			timer = b.stunTicks;
			mob.setState(Hollowhorn.STATE_STUNNED);
			mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
			return;
		}

		if (horizontal(mob.position().subtract(chargeFrom)).length() >= b.chargeRange || timer > MAX_CHARGE_TICKS) {
			endCharge(b);
		}
	}

	/** Strong knockback along the charge, less by the victim's knockback resistance. */
	private static void shove(LivingEntity hit, Vec3 dir, double strength) {
		double scale = Math.max(0.0, 1.0 - hit.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) * strength;

		if (scale > 0.0) {
			hit.push(dir.x * scale, 0.35 * scale, dir.z * scale);
			hit.hurtMarked = true;
		}
	}

	/** Not creative or spectator players, by game mode: a game test's mock player always answers {@code isCreative()} true. */
	private boolean canHit(LivingEntity e) {
		return e != mob && e.isAlive() && !(e instanceof Hollowhorn) && !(e instanceof Player player && (player.gameMode() == GameType.CREATIVE || player.isSpectator()));
	}

	private void impact(ServerLevel level, Vec3 at) {
		EffectEvents.toTracking(mob, BeastEffects.TELL, at, chargeDir, mob.getId(), 1f, BeastEffects.HOLLOWHORN_IMPACT);
		level.playSound(null, at.x, at.y, at.z, ModSounds.HOLLOWHORN_IMPACT, mob.getSoundSource(), 1.3f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
	}

	private void stunned(CreatureBalance b) {
		mob.getNavigation().stop();

		if (--timer <= 0) {
			endCharge(b);
		}
	}

	private void endCharge(CreatureBalance b) {
		phase = Phase.APPROACH;
		cooldown = b.chargeCooldownTicks;
		chargeDir = null;
		chargeFrom = null;
		repath = 0;
		mob.setState(Hollowhorn.STATE_IDLE);
	}

	private void butt(ServerLevel level, LivingEntity target, CreatureBalance b) {
		buttCooldown = b.buttCooldownTicks;
		mob.swing(InteractionHand.MAIN_HAND);
		target.hurtServer(level, mob.damageSources().mobAttack(mob), b.buttDamage);
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
