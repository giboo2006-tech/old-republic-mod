package com.oldrepublic.combat;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A projectile that lightsaber guards and Force abilities can send back. Implemented by the
 * blaster bolt.
 */
public interface DeflectableProjectile {
	/** Current velocity in blocks per tick. */
	Vec3 deflectableVelocity();

	/** Who fired it, or null. */
	@Nullable Entity deflectableShooter();

	/**
	 * Sends the projectile off in a new direction, now owned by {@code newOwner} so it can hurt its
	 * original shooter. {@code parried} marks a perfectly timed return.
	 */
	void redirect(Vec3 newVelocity, @Nullable Entity newOwner, boolean parried);
}
