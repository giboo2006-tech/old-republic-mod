package com.oldrepublic.force;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.oldrepublic.combat.Attitudes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Finds what a push affects: living entities, loose items and projectiles inside the cone (or
 * sphere) with a clear line of sight from the caster's eyes, skipping creatures the area rule
 * shields ({@link Attitudes#shieldedFromArea}). Never looks at blocks other than for line of
 * sight.
 */
public final class PushTargeting {
	/**
	 * One affected entity.
	 *
	 * @param entity   the target
	 * @param aimPoint the point that passed the cone and line-of-sight tests
	 * @param distance distance from the caster's eyes to {@code aimPoint}
	 */
	public record Target(Entity entity, Vec3 aimPoint, double distance) {
	}

	/** Targets of a cast, nearest first. */
	public static List<Target> find(ServerPlayer caster, PushShape shape, PushBalance balance) {
		ServerLevel level = caster.level();
		Vec3 eye = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		AABB box = new AABB(eye, eye).inflate(shape.range() + 2.0);
		boolean pvp = balance.pvpEffects && level.isPvpAllowed();
		List<Target> targets = new ArrayList<>();

		for (Entity entity : level.getEntities(caster, box, e -> isPushable(caster, e, pvp))) {
			Vec3 center = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
			Vec3[] candidates = {center, entity.getEyePosition(), entity.position().add(0, 0.1, 0)};

			for (Vec3 point : candidates) {
				double slack = entity.getBbWidth() * 0.5;

				if (inShape(eye, look, point, shape.range() + slack, shape.halfAngle(), shape.spherical())
						&& hasLineOfSight(level, caster, eye, point)) {
					targets.add(new Target(entity, point, eye.distanceTo(point)));
					break;
				}
			}
		}

		targets.sort(Comparator.comparingDouble(Target::distance));
		return targets;
	}

	/** Living entities, items and projectiles; other players only when PvP effects are on; never shielded bystanders. */
	static boolean isPushable(ServerPlayer caster, Entity entity, boolean pvp) {
		if (entity == caster || !entity.isAlive() || entity.isSpectator() || Attitudes.shieldedFromArea(caster, entity)) {
			return false;
		}

		if (entity instanceof Player player) {
			return pvp && !player.isCreative() && !caster.isAlliedTo(player);
		}

		return entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof Projectile;
	}

	/**
	 * Pure geometry: whether {@code point} is within {@code range} of {@code origin} and, unless
	 * {@code spherical}, inside the cone of {@code halfAngleDeg} around {@code look} (unit vector).
	 * Points almost at the origin always count.
	 */
	public static boolean inShape(Vec3 origin, Vec3 look, Vec3 point, double range, double halfAngleDeg, boolean spherical) {
		Vec3 offset = point.subtract(origin);
		double distance = offset.length();

		if (distance > range) {
			return false;
		}

		if (spherical || distance < 0.6) {
			return true;
		}

		return offset.scale(1.0 / distance).dot(look) >= Math.cos(Math.toRadians(halfAngleDeg));
	}

	/** True when no collidable block lies between the eyes and {@code point}. */
	public static boolean hasLineOfSight(ServerLevel level, Entity caster, Vec3 eye, Vec3 point) {
		BlockHitResult hit = level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eye) >= point.distanceToSqr(eye) - 1.0e-3;
	}

	private PushTargeting() {
	}
}
