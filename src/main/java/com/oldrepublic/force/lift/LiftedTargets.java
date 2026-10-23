package com.oldrepublic.force.lift;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Creatures Telekinetic Lift holds in the air right now (never players: they go through
 * {@code Holds}). Nothing here is saved: gravity is switched off by the transient modifier
 * {@link #GRAVITY_MODIFIER} and the AI is paused by {@code MobLiftMixin} reading {@link #isLifted},
 * so a target that unloads, or a server that stops, leaves nothing behind. Server side.
 */
public final class LiftedTargets {
	/** Transient GRAVITY modifier (-1.0, multiplied total): gravity 0 while lifted. */
	public static final Identifier GRAVITY_MODIFIER = OldRepublicMod.id("lift_gravity");
	/** Degrees per tick a lifted creature slowly turns. */
	private static final float TURN_PER_TICK = 2.5f;
	/** How strongly the hold pulls a target back to its column and height each tick. */
	private static final double HOLD_PULL = 0.35;
	private static final double MAX_RISE_SPEED = 0.35;

	/** One held creature. */
	private static final class Lifted {
		final LivingEntity target;
		final UUID caster;
		final Vec3 anchor;
		final double height;
		final int riseTicks;
		final boolean suspend;
		int age;

		Lifted(LivingEntity target, UUID caster, Vec3 anchor, double height, int riseTicks, boolean suspend) {
			this.target = target;
			this.caster = caster;
			this.anchor = anchor;
			this.height = height;
			this.riseTicks = riseTicks;
			this.suspend = suspend;
		}
	}

	/** By target UUID. Read by the AI mixin on every mob tick, so lookups stay cheap. */
	private static final Map<UUID, Lifted> LIFTED = new ConcurrentHashMap<>();

	/** Whether {@code e} is held up by a Lift right now (its AI is paused). */
	public static boolean isLifted(Entity e) {
		if (LIFTED.isEmpty()) {
			return false;
		}

		Lifted lifted = LIFTED.get(e.getUUID());
		return lifted != null && lifted.target == e;
	}

	/** The caster holding {@code e} with Suspend ({@code lift_suspend}), or null. */
	public static @Nullable UUID suspendedBy(Entity e) {
		Lifted lifted = LIFTED.isEmpty() ? null : LIFTED.get(e.getUUID());
		return lifted != null && lifted.target == e && lifted.suspend ? lifted.caster : null;
	}

	/**
	 * Lifts {@code target} by {@code height} blocks over {@code riseTicks} and holds it there: gravity
	 * off, movement and AI stopped. Replaces an earlier lift of the same target.
	 */
	static void lift(LivingEntity target, UUID caster, float height, int riseTicks, boolean suspend) {
		AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);

		if (gravity != null) {
			gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_MODIFIER, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}

		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}

		stopMoving(target);
		target.resetFallDistance();
		LIFTED.put(target.getUUID(), new Lifted(target, caster, target.position(), height, Math.max(1, riseTicks), suspend));
	}

	/** Lets go of {@code e}: the gravity modifier goes and the AI runs again. Safe when it is not lifted. */
	public static void release(LivingEntity e, String reason) {
		AttributeInstance gravity = e.getAttribute(Attributes.GRAVITY);

		if (gravity != null) {
			gravity.removeModifier(GRAVITY_MODIFIER);
		}

		Lifted lifted = LIFTED.remove(e.getUUID());

		if (lifted != null) {
			e.resetFallDistance();
			OldRepublicMod.LOGGER.debug("Lift released {} ({})", e.getName().getString(), reason);
		}
	}

	/** Moves every held creature toward its spot in the air; drops the ones that died or went away. */
	static void tick() {
		if (LIFTED.isEmpty()) {
			return;
		}

		for (Lifted lifted : new ArrayList<>(LIFTED.values())) {
			LivingEntity target = lifted.target;

			if (!target.isAlive() || target.isRemoved()) {
				release(target, "gone");
				continue;
			}

			lifted.age++;
			Vec3 pos = target.position();
			double rise = Math.min(1.0, lifted.age / (double) lifted.riseTicks);
			double goalY = lifted.anchor.y + lifted.height * rise;
			double vy = Mth.clamp((goalY - pos.y) * 0.5, -MAX_RISE_SPEED, MAX_RISE_SPEED);
			target.setDeltaMovement((lifted.anchor.x - pos.x) * HOLD_PULL, vy, (lifted.anchor.z - pos.z) * HOLD_PULL);
			target.hurtMarked = true;
			float yaw = target.getYRot() + TURN_PER_TICK;
			target.setYRot(yaw);
			target.setYBodyRot(yaw);
			target.setYHeadRot(yaw);
			stopMoving(target);
			target.resetFallDistance();
		}
	}

	/** Clears the movement input a paused AI would otherwise keep applying. */
	private static void stopMoving(LivingEntity target) {
		if (target instanceof Mob mob) {
			mob.setZza(0f);
			mob.setXxa(0f);
			mob.setYya(0f);
		}

		target.setJumping(false);
	}

	/** Releases everything (server stopping). */
	static void clear() {
		for (Lifted lifted : new ArrayList<>(LIFTED.values())) {
			release(lifted.target, "stopping");
		}

		LIFTED.clear();
	}

	private LiftedTargets() {
	}
}
