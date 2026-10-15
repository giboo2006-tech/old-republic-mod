package com.oldrepublic.enemy;

import com.oldrepublic.config.BalanceConfig;

/**
 * Tunable numbers for the Crystal Poacher and its blaster, stored in the {@code enemy} section of
 * {@code config/oldrepublic/balance.json}. Read on the server only.
 */
public final class EnemyBalance {
	/** Poacher maximum health (half hearts). */
	public double maxHealth = 24;
	public double armor = 2;
	public double movementSpeed = 0.27;
	public double followRange = 32;
	/** Damage of the rifle-butt bash used when a target is right on top of the poacher. */
	public double meleeDamage = 3;
	/** Preferred distance band to the target, in blocks. */
	public double minRange = 8;
	public double maxRange = 14;
	/** Below this distance the poacher sprints away instead of strafing. */
	public double retreatRange = 5;
	public double retreatSpeed = 1.25;
	/** Attack tell before every shot: muzzle glow and charge whine (12 ticks = 0.6 s). */
	public int chargeTicks = 12;
	/** Pause between the shots of one burst, after a bolt leaves and before the next tell. */
	public int burstGapTicks = 4;
	public int burstMin = 2;
	public int burstMax = 3;
	/** Pause after a burst. */
	public int recoveryMinTicks = 40;
	public int recoveryMaxTicks = 60;
	public float boltDamage = 5;
	/** Blocks per tick. */
	public float boltSpeed = 1.8f;
	public int boltLifetimeTicks = 60;
	/** Vanilla projectile inaccuracy units at Normal difficulty (x1.5 on Easy, x0.6 on Hard). */
	public float aimInaccuracy = 5;
	/** Below this fraction of max health a recently hurt poacher looks for cover. */
	public double coverHealthFraction = 0.4;
	public double coverSpeed = 1.3;
	public int coverHoldMinTicks = 40;
	public int coverHoldMaxTicks = 60;
	public int coverCooldownTicks = 200;
	/** The poacher kill on which an emitter drop is guaranteed if none has dropped yet. */
	public int emitterGuaranteeKill = 4;
	/** Chance per kill, before the guarantee, that an emitter drops. */
	public float emitterChance = 0.12f;
	/** Rare natural spawns deep underground, outside crystal caves. Camp spawns are unaffected. */
	public boolean naturalSpawns = false;

	public static EnemyBalance get() {
		return BalanceConfig.section("enemy", EnemyBalance.class, EnemyBalance::new);
	}
}
