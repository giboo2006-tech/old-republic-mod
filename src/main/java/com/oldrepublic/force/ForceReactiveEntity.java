package com.oldrepublic.force;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.pull.PullTargeting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A non-living entity that answers Force powers and the thrown saber (spec 10.6): the frag
 * grenade, the Archive's debris, a pylon focus. Each method returns true when the entity consumed
 * the act, so the power skips its normal effect on it. Server side.
 */
public interface ForceReactiveEntity {
	/** A Force Push reached it along {@code dir} (unit vector) with the push's knockback {@code strength}. */
	default boolean onForcePush(ServerPlayer caster, Vec3 dir, float strength) {
		return false;
	}

	/** A Force Pull reached it; {@code dir} points toward the caster. */
	default boolean onForcePull(ServerPlayer caster, Vec3 dir, float strength) {
		return false;
	}

	/** Called each tick of a Telekinetic Lift channel while the caster aims at it. */
	default boolean onForceLift(ServerPlayer caster) {
		return false;
	}

	/** Called each tick of a Force Lightning channel while it is on the aim ray, with the lightning's damage per tick. */
	default boolean onForceLightning(ServerPlayer caster, float perTick) {
		return false;
	}

	/** A thrown saber touched it, carrying {@code damage}. */
	default boolean onSaberThrowHit(ServerPlayer caster, float damage) {
		return false;
	}

	/**
	 * The nearest reactive entity whose box the caster's eye ray crosses within {@code range}
	 * blocks, before the first collidable block; null if none. The result implements this interface.
	 */
	static @Nullable Entity findOnAim(ServerPlayer caster, double range) {
		ServerLevel level = caster.level();
		Vec3 eye = caster.getEyePosition();
		Vec3 end = PullTargeting.rayEnd(level, caster, eye, caster.getLookAngle(), range);
		Entity nearest = null;
		double nearestSqr = Double.MAX_VALUE;

		for (Entity entity : level.getEntities(caster, new AABB(eye, end).inflate(1.0),
				e -> e instanceof ForceReactiveEntity && e.isAlive() && !e.isSpectator())) {
			Optional<Vec3> hit = entity.getBoundingBox().inflate(0.1).clip(eye, end);

			if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearestSqr) {
				nearestSqr = eye.distanceToSqr(hit.get());
				nearest = entity;
			}
		}

		return nearest;
	}
}
