package com.oldrepublic.enemy.hymn;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberCombat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A Hymnsworn's fight (spec MILESTONE_5.md 12.1), by kind:
 * <ul>
 * <li><b>Chanter</b>: keeps about 6 blocks back while it chants (the heal is the entity's own tick)
 * and swings its staff for 3 at anyone who closes in.</li>
 * <li><b>Blade</b>: walks in and cuts fast: 5 a blow every 14 ticks (light blows, no tell).</li>
 * <li><b>Storm-caller</b>: keeps about 9 blocks back. Every 160 ticks it <b>calls lightning</b> onto the
 * patch where its target stands (a 40-tick tell: static crawls over the patch and grit lifts from it;
 * the patch does not follow the target), then 8 and a 20-tick {@code shock} within 2.5 blocks. Every
 * 200 ticks a 20-tick <b>burst of Force lightning</b> at its target within 12 blocks, 1.5 every 5 ticks.
 * An interrupt (a stagger, a Push, a shock) drops the call's tell and ends a burst, and so does losing
 * sight of the target for a burst.</li>
 * </ul>
 */
final class HymnswornCombatGoal extends Goal {
	private enum Phase {
		READY,
		CALL_TELL,
		LIGHTNING,
		RECOVERY
	}

	private static final double STAFF_REACH = 2.4;
	private static final int RECOVERY_TICKS = 20;

	private final Hymnsworn mob;
	private Phase phase = Phase.READY;
	private int timer;
	private int cooldown;
	private int sinceCall;
	private int sinceLightning;
	private int repath;
	private int strafeFlip;
	private boolean strafeClockwise;
	private int pulse;
	private @Nullable Vec3 patch;

	HymnswornCombatGoal(Hymnsworn mob) {
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
		return canUse();
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		phase = Phase.READY;
		timer = 0;
		HymnswornBalance b = HymnswornBalance.get();
		// The first call and burst come a little early, so a short fight still shows them.
		sinceCall = b.callIntervalTicks / 2;
		sinceLightning = b.lightningIntervalTicks / 2;
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		cancel();
		mob.getNavigation().stop();
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

		HymnswornBalance b = HymnswornBalance.get();
		long now = level.getGameTime();
		double distance = mob.distanceTo(target);
		boolean sees = mob.getSensing().hasLineOfSight(target);
		cooldown--;
		sinceCall++;
		sinceLightning++;
		mob.getLookControl().setLookAt(target, 30f, 30f);

		if (mob.consumeInterrupt() && (phase == Phase.CALL_TELL || phase == Phase.LIGHTNING)) {
			if (phase == Phase.LIGHTNING) {
				sinceLightning = 0;
			}

			cancel();
		}

		walk(target, distance, sees, b);

		switch (mob.variant()) {
			case BLADE -> {
				if (distance <= b.bladeReach && sees && cooldown <= 0 && !ForceStatus.isDisarmed(mob)) {
					cooldown = Math.max(1, b.bladeCooldownTicks);
					blow(level, target, b.bladeDamage);
				}
			}
			case CHANTER -> {
				if (distance <= STAFF_REACH && sees && cooldown <= 0 && !ForceStatus.isDisarmed(mob)) {
					cooldown = Math.max(1, b.staffCooldownTicks);
					blow(level, target, b.staffDamage);
				}
			}
			case CALLER -> caller(level, target, distance, sees, b, now);
		}
	}

	private void blow(ServerLevel level, LivingEntity target, float damage) {
		mob.swing(InteractionHand.MAIN_HAND);
		SaberCombat.strike(target, level, mob.damageSources().mobAttack(mob), damage, SaberCombat.Strike.NPC_LIGHT);
	}

	// ---------------------------------------------------------------- the storm-caller

	private void caller(ServerLevel level, LivingEntity target, double distance, boolean sees, HymnswornBalance b, long now) {
		switch (phase) {
			case READY -> {
				if (sees && distance <= b.callRange && sinceCall >= b.callIntervalTicks) {
					startCall(level, target, b, now);
				} else if (sees && distance <= b.lightningRange && sinceLightning >= b.lightningIntervalTicks) {
					phase = Phase.LIGHTNING;
					timer = Math.max(1, b.lightningBurstTicks);
					pulse = 0;
					mob.setAction(Hymnsworn.ACTION_LIGHTNING);
				}
			}
			case CALL_TELL -> {
				if (--timer <= 0) {
					Vec3 at = patch;

					if (at != null) {
						strike(level, at, b, now);
					} else {
						cancel();
					}
				}
			}
			case LIGHTNING -> {
				if (!sees || distance > b.lightningRange + 2) {
					sinceLightning = 0;
					cancel();
					return;
				}

				if (pulse++ % Math.max(1, b.lightningDamageInterval) == 0) {
					zap(level, target, b);
				}

				if (--timer <= 0) {
					sinceLightning = 0;
					mob.setAction(Hymnsworn.ACTION_NONE);
					phase = Phase.RECOVERY;
					timer = RECOVERY_TICKS;
				}
			}
			case RECOVERY -> {
				if (--timer <= 0) {
					phase = Phase.READY;
				}
			}
		}
	}

	/** The tell: the patch under the target is marked (it does not follow them), the caller's arms go up. */
	private void startCall(ServerLevel level, LivingEntity target, HymnswornBalance b, long now) {
		patch = target.position();
		phase = Phase.CALL_TELL;
		timer = Math.max(1, b.callTellTicks);
		mob.setAction(Hymnsworn.ACTION_CALL);
		mob.tellLog().tell(now, b.callTellTicks);
		EffectEvents.around(level, HymnEffects.EVENT, patch, null, mob.getId(), b.callTellTicks, HymnEffects.CALL_TELL);
		level.playSound(null, patch.x, patch.y, patch.z, ModSounds.HYMNSWORN_CALL_TELL, mob.getSoundSource(), 1f, 1f);
	}

	/** The strike on the marked patch: 8 and a shock to all but the Hymn's own. */
	private void strike(ServerLevel level, Vec3 at, HymnswornBalance b, long now) {
		mob.tellLog().heavy(now);
		StormCall.strike(level, at, b.callRadius, b.callDamage, b.callShockTicks, mob, e -> e != mob && !Allegiances.friendly(mob, e));
		EffectEvents.around(level, HymnEffects.EVENT, at, null, mob.getId(), b.callRadius, HymnEffects.CALL_STRIKE);
		level.playSound(null, at.x, at.y, at.z, ModSounds.HYMNSWORN_CALL_STRIKE, mob.getSoundSource(), 1.2f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
		sinceCall = 0;
		patch = null;
		mob.setAction(Hymnsworn.ACTION_NONE);
		phase = Phase.RECOVERY;
		timer = RECOVERY_TICKS;
	}

	/** One pulse of the burst: the arc to the target's chest and its damage. */
	private void zap(ServerLevel level, LivingEntity target, HymnswornBalance b) {
		Vec3 hand = mob.getEyePosition().subtract(0, 0.4, 0);
		Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
		Vec3 dir = chest.subtract(hand);
		EffectEvents.toTracking(mob, HymnEffects.EVENT, chest, dir.lengthSqr() < 1.0e-4 ? null : dir.normalize(), mob.getId(), b.lightningBurstTicks,
				HymnEffects.LIGHTNING);
		target.hurtServer(level, level.damageSources().source(ForceDamage.LIGHTNING, mob, mob), b.lightningDamage);
	}

	/** Ends a tell or a burst without its strike: no raised arms, and the tell log forgets the tell. */
	private void cancel() {
		if (phase == Phase.CALL_TELL) {
			mob.tellLog().cancel();
		}

		if (mob.action() == Hymnsworn.ACTION_CALL || mob.action() == Hymnsworn.ACTION_LIGHTNING) {
			mob.setAction(Hymnsworn.ACTION_NONE);
		}

		patch = null;
		phase = Phase.READY;
	}

	// ---------------------------------------------------------------- moving

	/** The blade walks in; the chanter and the storm-caller keep their distance, strafing, and back off when rushed. */
	private void walk(LivingEntity target, double distance, boolean sees, HymnswornBalance b) {
		if (mob.variant() == Hymnsworn.Variant.BLADE) {
			if (distance > b.bladeReach * 0.85 || !sees) {
				if (--repath <= 0) {
					repath = 8;
					mob.getNavigation().moveTo(target, 1.2);
				}
			} else {
				mob.getNavigation().stop();
			}

			return;
		}

		double keep = mob.variant() == Hymnsworn.Variant.CALLER ? b.callerDistance : b.chantDistance;

		if (!sees || distance > keep + 3) {
			if (--repath <= 0) {
				repath = 10;
				mob.getNavigation().moveTo(target, 1.0);
			}

			return;
		}

		mob.getNavigation().stop();

		if (--strafeFlip <= 0 || mob.horizontalCollision) {
			strafeClockwise = !strafeClockwise;
			strafeFlip = 25 + mob.getRandom().nextInt(25);
		}

		boolean busy = phase == Phase.CALL_TELL || phase == Phase.LIGHTNING;
		float forward = distance < keep - 2 && distance > STAFF_REACH ? -0.5f : 0f;
		float sideways = busy ? 0.15f : 0.4f;
		mob.getMoveControl().strafe(forward, strafeClockwise ? sideways : -sideways);
	}
}
