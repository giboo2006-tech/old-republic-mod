package com.oldrepublic.enemy.creature;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The ash hound pack's hunt (spec MILESTONE_4.md 10.4). The first hound to spot a player howls,
 * which sets every hound within {@code howlRange} on that player. The hounds hunting one player sort
 * themselves by entity id: the first holds about 4 blocks in front of the player, the others circle
 * to its flanks at {@code flankRadius}. One hound at a time lunges: a {@code lungeTellTicks} crouch
 * and growl, then a leap of up to {@code lungeRange} blocks for {@code lungeDamage}. A flanker lunges
 * once it is in position; the front hound waits for its flankers. Up close a hound bites.
 */
final class AshHoundPackGoal extends Goal {
	private enum Phase {
		HOWL,
		CIRCLE,
		TELL,
		LEAP,
		RECOVER
	}

	/** Ticks the howl takes. */
	private static final int HOWL_TICKS = 24;
	/** The front hound holds this far from the player. */
	private static final double HOLD = 4;
	/** A flanker this close to its spot is in position. */
	private static final double IN_POSITION = 2;
	/** A hound that has waited this long lunges from wherever it is. */
	private static final int IMPATIENCE_TICKS = 80;
	private static final double BITE_REACH = 1.8;
	/** A leap still airborne after this long counts as landed. */
	private static final int MAX_LEAP_TICKS = 30;
	private static final int RECOVER_TICKS = 10;

	private final AshHound mob;
	private Phase phase = Phase.CIRCLE;
	private int timer;
	private int cooldown;
	private int biteCooldown;
	private int repath;
	private int waited;
	private boolean leapHit;

	AshHoundPackGoal(AshHound mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && !mob.isStaggered();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		CreatureBalance b = CreatureBalance.get();
		// Packmates' first lunges are spread out.
		cooldown = b.lungeCooldownTicks / 2 + mob.getRandom().nextInt(Math.max(1, b.lungeCooldownTicks / 2));
		waited = 0;
		repath = 0;
		LivingEntity target = mob.getTarget();

		if (target != null && mob.level() instanceof ServerLevel level && pack(level, target, b).size() <= 1) {
			howl(level, target, b);
		} else {
			phase = Phase.CIRCLE;
		}
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		mob.getNavigation().stop();

		if (!mob.isStaggered()) {
			mob.setState(AshHound.STATE_IDLE);
		}

		if (phase == Phase.TELL || phase == Phase.LEAP) {
			cooldown = CreatureBalance.get().lungeCooldownTicks;
		}

		phase = Phase.CIRCLE;
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();

		if (target == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}

		CreatureBalance b = CreatureBalance.get();
		cooldown--;
		biteCooldown--;

		switch (phase) {
			case HOWL -> {
				mob.getNavigation().stop();
				mob.getLookControl().setLookAt(target, 30f, 30f);

				if (--timer <= 0) {
					phase = Phase.CIRCLE;
					mob.setState(AshHound.STATE_IDLE);
				}
			}
			case CIRCLE -> circle(level, target, b);
			case TELL -> crouch(target, b);
			case LEAP -> leap(level, target, b);
			case RECOVER -> {
				if (--timer <= 0) {
					phase = Phase.CIRCLE;
					mob.setState(AshHound.STATE_IDLE);
				}
			}
			default -> phase = Phase.CIRCLE;
		}
	}

	/** The call that brings the pack: every hound within {@code howlRange} without a target takes this one's. */
	private void howl(ServerLevel level, LivingEntity target, CreatureBalance b) {
		phase = Phase.HOWL;
		timer = HOWL_TICKS;
		mob.setState(AshHound.STATE_HOWL);
		mob.getNavigation().stop();
		EffectEvents.around(level, BeastEffects.TELL, mob.getEyePosition(), null, mob.getId(), HOWL_TICKS, BeastEffects.HOUND_HOWL);
		mob.playSound(ModSounds.ASH_HOUND_HOWL, 2.5f, 0.9f + mob.getRandom().nextFloat() * 0.2f);

		for (AshHound other : level.getEntitiesOfClass(AshHound.class, mob.getBoundingBox().inflate(b.howlRange), h -> h != mob && h.isAlive())) {
			if (other.getTarget() == null || !other.getTarget().isAlive()) {
				other.setTarget(target);
			}
		}
	}

	/** The hounds hunting {@code target} near it, in entity id order (their roles). */
	private static List<AshHound> pack(ServerLevel level, LivingEntity target, CreatureBalance b) {
		return level.getEntitiesOfClass(AshHound.class, target.getBoundingBox().inflate(b.howlRange), h -> h.isAlive() && h.getTarget() == target)
				.stream().sorted(Comparator.comparingInt(AshHound::getId)).toList();
	}

	private void circle(ServerLevel level, LivingEntity target, CreatureBalance b) {
		List<AshHound> pack = pack(level, target, b);
		int index = Math.max(0, pack.indexOf(mob));
		CreatureRules.Role role = CreatureRules.role(index);
		AshHound front = pack.isEmpty() ? mob : pack.get(0);
		Vec3 frontDir = front.position().subtract(target.position());
		Vec3 spot = CreatureRules.spot(target.position(), frontDir, role, HOLD, b.flankRadius);
		double distance = mob.distanceTo(target);
		mob.getLookControl().setLookAt(target, 30f, 30f);
		waited++;

		if (distance <= BITE_REACH && biteCooldown <= 0) {
			biteCooldown = b.biteCooldownTicks;
			mob.swing(InteractionHand.MAIN_HAND);
			target.hurtServer(level, mob.damageSources().mobAttack(mob), b.biteDamage);
			return;
		}

		boolean inPosition = mob.position().distanceToSqr(spot) <= IN_POSITION * IN_POSITION;
		boolean flankersOut = role != CreatureRules.Role.FRONT || pack.size() <= 1 || flankersInPosition(pack, target, b);
		boolean ready = cooldown <= 0 && distance <= b.lungeRange && distance > BITE_REACH && mob.getSensing().hasLineOfSight(target)
				&& (inPosition && flankersOut || waited > IMPATIENCE_TICKS) && !packLunging(pack);

		if (ready) {
			startTell(level, target, b);
			return;
		}

		if (--repath <= 0) {
			repath = 8;
			mob.getNavigation().moveTo(spot.x, target.getY(), spot.z, inPosition ? 0.8 : 1.2);
		}
	}

	private static boolean flankersInPosition(List<AshHound> pack, LivingEntity target, CreatureBalance b) {
		Vec3 frontDir = pack.get(0).position().subtract(target.position());

		for (int i = 1; i < pack.size(); i++) {
			Vec3 spot = CreatureRules.spot(target.position(), frontDir, CreatureRules.role(i), HOLD, b.flankRadius);

			if (pack.get(i).position().distanceToSqr(spot) > IN_POSITION * IN_POSITION) {
				return false;
			}
		}

		return true;
	}

	/** One hound lunges at a time. */
	private boolean packLunging(List<AshHound> pack) {
		for (AshHound other : pack) {
			if (other != mob && other.isLunging()) {
				return true;
			}
		}

		return false;
	}

	/** The crouch and the growl. */
	private void startTell(ServerLevel level, LivingEntity target, CreatureBalance b) {
		phase = Phase.TELL;
		timer = b.lungeTellTicks;
		mob.getNavigation().stop();
		mob.setState(AshHound.STATE_CROUCH);
		Vec3 dir = target.position().subtract(mob.position());
		EffectEvents.toTracking(mob, BeastEffects.TELL, mob.position(), dir.lengthSqr() < 1.0e-6 ? null : dir.normalize(), mob.getId(), timer,
				BeastEffects.HOUND_LUNGE);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.ASH_HOUND_GROWL, mob.getSoundSource(), 1.2f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
	}

	private void crouch(LivingEntity target, CreatureBalance b) {
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(target, 30f, 30f);
		Vec3 to = new Vec3(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ());

		if (to.lengthSqr() > 1.0e-6) {
			float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
			mob.setYRot(yaw);
			mob.yBodyRot = yaw;
		}

		if (--timer > 0) {
			return;
		}

		if (to.lengthSqr() < 1.0e-6) {
			endLunge(b);
			return;
		}

		phase = Phase.LEAP;
		timer = 0;
		leapHit = false;
		mob.setState(AshHound.STATE_LEAP);
		Vec3 dir = to.normalize();
		double speed = CreatureRules.leapSpeed(to.length(), b.lungeRange);
		mob.setDeltaMovement(dir.x * speed, 0.42, dir.z * speed);
		mob.hurtMarked = true;
		EffectEvents.toTracking(mob, BeastEffects.TELL, mob.position(), dir, mob.getId(), 1f, BeastEffects.HOUND_LEAP);
		mob.playSound(ModSounds.ASH_HOUND_LUNGE, 1.2f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
	}

	private void leap(ServerLevel level, LivingEntity target, CreatureBalance b) {
		timer++;

		if (!leapHit && mob.getBoundingBox().inflate(0.3).intersects(target.getBoundingBox())) {
			leapHit = true;
			// A saber parry cancels this and turns the hound (AshHound#onParried).
			target.hurtServer(level, mob.damageSources().mobAttack(mob), b.lungeDamage);
		}

		if (timer > 2 && mob.onGround() || timer > MAX_LEAP_TICKS) {
			endLunge(b);
		}
	}

	private void endLunge(CreatureBalance b) {
		phase = Phase.RECOVER;
		timer = RECOVER_TICKS;
		cooldown = b.lungeCooldownTicks;
		waited = 0;

		if (!mob.isStaggered()) {
			mob.setState(AshHound.STATE_IDLE);
		}
	}
}
