package com.oldrepublic.force.mindtrick;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceMobEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.network.EffectEvents;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * The {@code pacify} control (MILESTONE_5.md 9.2, 9.8): the runtime behind {@link ControlEffects#PACIFY}. A pacified mob
 * has the {@code oldrepublic:pacified} effect; while it does it drops its target every tick and
 * {@code mixin.force.MobPacifyMixin} refuses any new one ({@code Mob#setTarget}), so it wanders. Any damage from a player
 * ends it. Bosses are never pacified.
 *
 * <p>Confusion (Mind Trick's {@code mind_trick_confuse}): the mob turns on its nearest ally (a mob of its own side, else of
 * its own kind) instead of wandering; the cancel lets that one target through. While it lasts
 * {@link Allegiances#friendly} is false for it and for whatever it meets ({@link #isConfused}, installed as the confused
 * test), so it may hurt its own side and they answer. Server side; the confused set is safe to read from any thread.
 */
public final class Pacified {
	/** One pacified mob, and for a confused one the ally it is turned on. */
	private static final class Calm {
		final Mob mob;
		final boolean confused;
		@Nullable LivingEntity victim;

		Calm(Mob mob, boolean confused) {
			this.mob = mob;
			this.confused = confused;
		}
	}

	/** A confused mob looks for a new victim this often. */
	private static final int RETARGET_INTERVAL = 20;
	private static final Map<UUID, Calm> CALM = new ConcurrentHashMap<>();
	/** The confused mobs, read by {@link Allegiances#friendly} wherever it runs. */
	private static final Set<UUID> CONFUSED = ConcurrentHashMap.newKeySet();

	/**
	 * Pacifies {@code target} for {@code baseTicks} before the control rules (the length comes from
	 * {@link ControlEffects#diminished(LivingEntity, int, String)} alone); with {@code confuse} it turns on its own side
	 * instead of wandering. Returns the ticks applied, 0 when nothing took (a boss, or the diminishing returns).
	 */
	public static int apply(Mob target, @Nullable ServerPlayer source, int baseTicks, boolean confuse) {
		if (!target.isAlive() || target.isRemoved() || baseTicks <= 0 || target.is(ConventionalEntityTypeTags.BOSSES)) {
			return 0;
		}

		int ticks = ControlEffects.diminished(target, baseTicks, ControlEffects.PACIFY);

		if (ticks <= 0) {
			return 0;
		}

		ForceMobEffects.apply(target, ForceMobEffects.PACIFIED, ticks, source);
		Calm calm = new Calm(target, confuse);
		CALM.put(target.getUUID(), calm);

		if (confuse) {
			CONFUSED.add(target.getUUID());
			calm.victim = nearestAlly(target, MindTrick.balance().confuseSearchRadius);
		} else {
			CONFUSED.remove(target.getUUID());
		}

		target.setTarget(calm.victim);
		ForceReactions.interruptWindup(target, source, ForceReactions.CAUSE_UNKNOWN);
		return ticks;
	}

	/** Whether {@code entity} is under Confusion now: the confused test of {@link Allegiances}. Cheap; any side. */
	public static boolean isConfused(Entity entity) {
		return !CONFUSED.isEmpty() && CONFUSED.contains(entity.getUUID());
	}

	/** Whether {@code mob} is pacified now (server). */
	public static boolean isPacified(Mob mob) {
		return CALM.containsKey(mob.getUUID());
	}

	/**
	 * The {@code Mob#setTarget} cancel: true when {@code mob} is pacified and {@code target} is a new target it may not
	 * take. Clearing the target is always allowed, and so is the ally a confused mob is turned on.
	 */
	public static boolean blocksTarget(Mob mob, @Nullable LivingEntity target) {
		if (target == null || CALM.isEmpty() || mob.level().isClientSide()) {
			return false;
		}

		Calm calm = CALM.get(mob.getUUID());
		return calm != null && calm.mob == mob && target != calm.victim;
	}

	/** Advances every pacified mob; called every server tick. */
	public static void tick() {
		if (CALM.isEmpty()) {
			return;
		}

		float radius = MindTrick.balance().confuseSearchRadius;

		for (Calm calm : new ArrayList<>(CALM.values())) {
			Mob mob = calm.mob;

			if (!mob.isAlive() || mob.isRemoved() || !mob.hasEffect(ForceMobEffects.PACIFIED)) {
				end(calm);
				continue;
			}

			if (!calm.confused) {
				if (mob.getTarget() != null) {
					mob.setTarget(null);
				}

				continue;
			}

			LivingEntity victim = calm.victim;
			boolean lost = victim == null || !victim.isAlive() || victim.isRemoved() || victim.level() != mob.level()
					|| victim.distanceToSqr(mob) > radius * radius * 4;

			if (lost || (mob.tickCount + mob.getId()) % RETARGET_INTERVAL == 0) {
				calm.victim = nearestAlly(mob, radius);
			}

			if (mob.getTarget() != calm.victim) {
				mob.setTarget(calm.victim);
			}
		}
	}

	/** The nearest living creature of {@code mob}'s side (else of its kind) within {@code radius}; never a player. */
	static @Nullable LivingEntity nearestAlly(Mob mob, float radius) {
		Allegiance side = Allegiances.of(mob);
		AABB box = mob.getBoundingBox().inflate(radius);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (LivingEntity other : mob.level().getEntitiesOfClass(LivingEntity.class, box,
				e -> e != mob && e.isAlive() && !(e instanceof Player) && (side != Allegiance.NONE ? Allegiances.of(e) == side : e.getType() == mob.getType()))) {
			double distance = other.distanceToSqr(mob);

			if (distance <= radius * radius && distance < bestDistance) {
				best = other;
				bestDistance = distance;
			}
		}

		return best;
	}

	/** Fabric {@code AFTER_DAMAGE}: any damage from a player ends the calm. */
	public static void onDamaged(LivingEntity entity, DamageSource source) {
		if (CALM.isEmpty() || !(entity instanceof Mob mob) || !(source.getEntity() instanceof Player)) {
			return;
		}

		Calm calm = CALM.get(mob.getUUID());

		if (calm != null && calm.mob == mob) {
			end(calm);
		}
	}

	/** Ends the calm on {@code mob}, if any. */
	public static void release(Mob mob) {
		Calm calm = CALM.get(mob.getUUID());

		if (calm != null) {
			end(calm);
		}
	}

	private static void end(Calm calm) {
		Mob mob = calm.mob;
		CALM.remove(mob.getUUID());
		CONFUSED.remove(mob.getUUID());

		if (mob.hasEffect(ForceMobEffects.PACIFIED)) {
			mob.removeEffect(ForceMobEffects.PACIFIED);
		}

		if (calm.victim != null && mob.getTarget() == calm.victim) {
			mob.setTarget(null);
		}

		if (mob.isAlive() && !mob.isRemoved()) {
			// The client lets the creature raise its guard again (a hit can end the calm long before its time).
			EffectEvents.toTracking(mob, MindTrick.EFFECT, ForcePowers.chest(mob), null, mob.getId(), 0f, MindTrick.VARIANT_ENDED);
		}
	}

	/** Fabric {@code ENTITY_UNLOAD}: forgets a mob leaving the world (it keeps the effect, and wanders when it comes back). */
	public static void onUnload(Entity entity) {
		Calm calm = CALM.get(entity.getUUID());

		if (calm != null && calm.mob == entity) {
			CALM.remove(entity.getUUID());
			CONFUSED.remove(entity.getUUID());
		}
	}

	/** Fabric {@code ENTITY_LOAD}: a mob that comes back with the effect is pacified again (not confused). */
	public static void onLoad(Entity entity) {
		if (entity instanceof Mob mob && mob.hasEffect(ForceMobEffects.PACIFIED) && !CALM.containsKey(mob.getUUID())) {
			CALM.put(mob.getUUID(), new Calm(mob, false));
		}
	}

	public static void clear() {
		CALM.clear();
		CONFUSED.clear();
	}

	private Pacified() {
	}
}
