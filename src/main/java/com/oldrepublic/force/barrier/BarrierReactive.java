package com.oldrepublic.force.barrier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A projectile with its own answer to a Force Barrier (spec MILESTONE_4.md 9.2): the hunter's rocket
 * bursts at the surface, a thrown net falls short. Projectiles without it are stopped by the
 * barrier's general rules. Server side.
 */
public interface BarrierReactive {
	/**
	 * {@code caster}'s barrier stops this projectile at {@code surface}, where its path meets the
	 * sphere; {@code outward} points from the barrier's centre through that point (unit vector).
	 * The projectile is gone or harmless afterwards.
	 */
	void onBarrier(ServerPlayer caster, Vec3 surface, Vec3 outward);
}
