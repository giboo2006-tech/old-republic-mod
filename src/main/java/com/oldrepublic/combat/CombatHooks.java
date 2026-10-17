package com.oldrepublic.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Cross-feature combat contract. Projectiles call {@link #onProjectileHit} before applying damage
 * so a guarding target can block or deflect them. The saber feature installs the guard logic.
 */
public final class CombatHooks {
	public enum DeflectResult {
		/** Not guarded: apply the hit normally. */
		NONE,
		/** Absorbed by the guard: no damage, projectile destroyed. */
		BLOCKED,
		/** Knocked away in a random forward arc: no damage. */
		DEFLECTED,
		/** Perfectly timed: returned straight at the shooter. */
		PARRIED
	}

	@FunctionalInterface
	public interface ProjectileGuard {
		DeflectResult onHit(LivingEntity target, Projectile projectile);
	}

	private static ProjectileGuard projectileGuard = (target, projectile) -> DeflectResult.NONE;

	public static void setProjectileGuard(ProjectileGuard guard) {
		projectileGuard = guard;
	}

	/**
	 * Called by deflectable projectiles when they hit a living entity. If the result is not
	 * {@link DeflectResult#NONE} the projectile must not deal its damage (the guard has already
	 * redirected or removed it).
	 */
	public static DeflectResult onProjectileHit(LivingEntity target, Projectile projectile) {
		return projectileGuard.onHit(target, projectile);
	}

	private CombatHooks() {
	}
}
