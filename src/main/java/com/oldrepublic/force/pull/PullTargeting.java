package com.oldrepublic.force.pull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.PushTargeting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Force Pull targeting: the creature on the crosshair ray, else the one nearest the aim axis inside
 * the aim-assist cone, always with line of sight from the eyes. Shared with Force Lightning's cone
 * pick. Pure enough to test with any entity list.
 */
public final class PullTargeting {
	/** Where the crosshair ray stops: the first collidable block within {@code range}, or the end of the range. */
	public static Vec3 rayEnd(ServerLevel level, Entity caster, Vec3 eye, Vec3 look, double range) {
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/**
	 * The living target of a pull from {@code eye} along {@code look} (unit vector), or null.
	 *
	 * @param allowed filter for who may be affected (the PvP rule)
	 */
	public static @Nullable LivingEntity find(ServerLevel level, Entity caster, Vec3 eye, Vec3 look, double range, double halfAngle,
			Predicate<Entity> allowed) {
		Vec3 end = rayEnd(level, caster, eye, look, range);
		AABB box = new AABB(eye, eye).inflate(range + 2.0);
		List<Entity> candidates = level.getEntities(caster, box, e -> e instanceof LivingEntity && allowed.test(e));
		LivingEntity onRay = null;
		double rayDistance = Double.MAX_VALUE;

		for (Entity entity : candidates) {
			Optional<Vec3> hit = entity.getBoundingBox().inflate(0.1).clip(eye, end);

			if (hit.isPresent() && eye.distanceTo(hit.get()) < rayDistance) {
				rayDistance = eye.distanceTo(hit.get());
				onRay = (LivingEntity) entity;
			}
		}

		if (onRay != null) {
			return onRay;
		}

		return nearestToAxis(level, caster, eye, look, range, halfAngle, candidates);
	}

	/** The creature among {@code candidates} closest to the aim axis within the cone, with line of sight. */
	public static @Nullable LivingEntity nearestToAxis(ServerLevel level, Entity caster, Vec3 eye, Vec3 look, double range, double halfAngle,
			List<Entity> candidates) {
		double minCos = Math.cos(Math.toRadians(halfAngle));
		LivingEntity best = null;
		double bestCos = -2;

		for (Entity entity : candidates) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}

			Vec3 point = aimPoint(living);
			Vec3 offset = point.subtract(eye);
			double distance = offset.length();

			if (distance < 1.0e-3 || distance > range + living.getBbWidth() * 0.5) {
				continue;
			}

			double cos = offset.scale(1.0 / distance).dot(look);

			if (cos >= minCos && cos > bestCos && PushTargeting.hasLineOfSight(level, caster, eye, point)) {
				best = living;
				bestCos = cos;
			}
		}

		return best;
	}

	/** Loose items within {@code radius} of {@code point}. */
	public static List<ItemEntity> items(ServerLevel level, Vec3 point, double radius) {
		List<ItemEntity> items = new ArrayList<>();

		for (Entity entity : level.getEntities((Entity) null, new AABB(point, point).inflate(radius),
				e -> e instanceof ItemEntity && e.isAlive() && e.position().distanceToSqr(point) <= radius * radius)) {
			items.add((ItemEntity) entity);
		}

		return items;
	}

	/** Torso point used for the cone test and line of sight. */
	public static Vec3 aimPoint(LivingEntity entity) {
		return entity.position().add(0, entity.getBbHeight() * 0.6, 0);
	}

	private PullTargeting() {
	}
}
