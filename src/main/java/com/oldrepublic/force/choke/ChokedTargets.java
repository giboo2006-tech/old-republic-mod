package com.oldrepublic.force.choke;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * Creatures Force Choke holds off the ground right now (never players: they go through
 * {@code Holds}). Nothing is saved: gravity is switched off by the transient modifier
 * {@link #GRAVITY_MODIFIER}, and {@code mixin.foes.MobChokeMixin} pauses the AI while
 * {@link #isChoked} is true, so an unloaded target or a stopped server leaves nothing behind.
 * Server side.
 */
public final class ChokedTargets {
	/** Transient GRAVITY modifier (-1.0, multiplied total): gravity 0 while held. */
	public static final Identifier GRAVITY_MODIFIER = OldRepublicMod.id("choke_gravity");
	/** How strongly the hold pulls the target back to its spot each tick. */
	private static final double HOLD_PULL = 0.4;
	private static final double MAX_RISE_SPEED = 0.2;
	private static final int RISE_TICKS = 8;

	/** One held creature. */
	private static final class Held {
		final LivingEntity target;
		final Vec3 anchor;
		final double height;
		int age;

		Held(LivingEntity target, double height) {
			this.target = target;
			this.anchor = target.position();
			this.height = height;
		}
	}

	/** By target UUID; read by the AI mixin on every mob tick, so lookups stay cheap. */
	private static final Map<UUID, Held> HELD = new ConcurrentHashMap<>();

	/** Whether {@code e} is held by a Choke right now (its AI is paused). */
	public static boolean isChoked(Entity e) {
		if (HELD.isEmpty()) {
			return false;
		}

		Held held = HELD.get(e.getUUID());
		return held != null && held.target == e;
	}

	/** Lifts {@code target} by {@code height} and holds it there: gravity off, movement and AI stopped. */
	static void hold(LivingEntity target, double height) {
		AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);

		if (gravity != null) {
			gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_MODIFIER, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}

		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}

		stopMoving(target);
		target.resetFallDistance();
		HELD.put(target.getUUID(), new Held(target, height));
	}

	/** Lets go of {@code e}: the gravity modifier goes and the AI runs again. Safe when it is not held. */
	static void release(LivingEntity e) {
		AttributeInstance gravity = e.getAttribute(Attributes.GRAVITY);

		if (gravity != null) {
			gravity.removeModifier(GRAVITY_MODIFIER);
		}

		if (HELD.remove(e.getUUID()) != null) {
			e.resetFallDistance();
		}
	}

	/** Keeps every held creature at its spot in the air, feet hanging; drops the ones that went away. */
	static void tick() {
		if (HELD.isEmpty()) {
			return;
		}

		for (Held held : new ArrayList<>(HELD.values())) {
			LivingEntity target = held.target;

			if (!target.isAlive() || target.isRemoved()) {
				release(target);
				continue;
			}

			held.age++;
			Vec3 pos = target.position();
			double rise = Math.min(1.0, held.age / (double) RISE_TICKS);
			double goalY = held.anchor.y + held.height * rise;
			double vy = Mth.clamp((goalY - pos.y) * 0.5, -MAX_RISE_SPEED, MAX_RISE_SPEED);
			target.setDeltaMovement((held.anchor.x - pos.x) * HOLD_PULL, vy, (held.anchor.z - pos.z) * HOLD_PULL);
			target.hurtMarked = true;
			stopMoving(target);
			target.resetFallDistance();
		}
	}

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
		for (Held held : new ArrayList<>(HELD.values())) {
			release(held.target);
		}

		HELD.clear();
	}

	private ChokedTargets() {
	}
}
