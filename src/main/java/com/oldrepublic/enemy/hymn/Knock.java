package com.oldrepublic.enemy.hymn;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Knock-back for the Milestone 5 foes' shoves, slams and charges (the Collector's rule): along the
 * horizontal line away from the source, less by the target's knock-back resistance, with a small
 * lift. Server side; marks the target so the client gets the new motion.
 */
public final class Knock {
	/** Knocks {@code target} away from {@code from} with {@code strength} (blocks per tick) and {@code lift}. */
	public static void away(LivingEntity target, Vec3 from, double strength, double lift) {
		Vec3 away = target.position().subtract(from).multiply(1, 0, 1);
		along(target, away, strength, lift);
	}

	/** Knocks {@code target} along {@code dir} (flattened); a zero direction only lifts it. */
	public static void along(LivingEntity target, Vec3 dir, double strength, double lift) {
		double resist = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
		double s = strength * (1.0 - resist);

		if (s <= 0.0) {
			return;
		}

		Vec3 flat = dir.multiply(1, 0, 1);
		Vec3 unit = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(unit.x * s, lift * (1.0 - resist), unit.z * s));
		target.hurtMarked = true;
	}

	private Knock() {
	}
}
