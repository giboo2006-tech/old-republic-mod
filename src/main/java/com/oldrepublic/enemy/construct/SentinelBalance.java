package com.oldrepublic.enemy.construct;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code sentinel} of {@code config/oldrepublic/balance.json}: the Ruin Sentinel
 * (spec 7.7 and 14). Durations in ticks, distances in blocks, multipliers on damage or duration.
 * Field names are the section 14 keys verbatim; {@link #liftHeight} is an added key (documented in
 * the W5 report). The {@code quiet_step} wake range is W3's ({@code skill.quietStepWake}).
 */
public final class SentinelBalance {
	/** Health, armour and movement speed per variant (Watcher, Tomb). */
	public float watcherHealth = 48f;
	public float tombHealth = 60f;
	public float armor = 8f;
	public float watcherSpeed = 0.18f;
	public float tombSpeed = 0.16f;

	/** A dormant sentinel wakes when a player comes this close, and its wake tell lasts this long. */
	public float wakeRange = 6f;
	public int wakeTicks = 20;
	/** Watchers stay dormant for players at alignment at least this, Tomb sentinels at or below its negative. */
	public int dormantAlignment = 15;

	/** Slam: tell, damage, the arc in front (radius, full angle), knockback and cooldown. */
	public int slamTellTicks = 20;
	public float slamDamage = 10f;
	public float slamRadius = 2.5f;
	public float slamArcDegrees = 90f;
	public float slamKnockback = 0.6f;
	public int slamCooldown = 60;
	/** After a slam the core is exposed this long, and takes this much more damage. */
	public int exposedTicks = 40;
	public float exposedMultiplier = 1.5f;

	/** Lance: tell, damage, bolt speed (blocks per tick), the range band {min, max} and cooldown. */
	public int lanceTellTicks = 24;
	public float lanceDamage = 7f;
	public float lanceSpeed = 1.6f;
	public float[] lanceRange = {6f, 20f};
	public int lanceCooldown = 80;
	/** Saber stamina a guard loses blocking the lance (heavy bolts at a guarding NPC use this too). */
	public float lanceGuardDrain = 35f;
	/** A parried lance hits its sentinel for this much and staggers it this long. */
	public float lanceReturnDamage = 10f;
	public int lanceReturnStaggerTicks = 20;

	/** Resistances: Push knockback (overall, tag included), Force Lightning damage, shock duration, blasters, sabers. */
	public float pushMultiplier = 0.2f;
	public float lightningMultiplier = 1.5f;
	public float shockDurationMultiplier = 2f;
	public float blasterMultiplier = 0.5f;
	public float saberMultiplier = 0.85f;
	/** Added: the highest Telekinetic Lift raises a sentinel (the push-resistant height). */
	public float liftHeight = 1.2f;

	/** Drops: the chance to roll the loot table (durasteel plates), and the thread or silk chance. */
	public float durasteelChance = 0.4f;
	public float threadChance = 0.2f;

	public float health(SentinelVariant variant) {
		return variant == SentinelVariant.TOMB ? tombHealth : watcherHealth;
	}

	public float speed(SentinelVariant variant) {
		return variant == SentinelVariant.TOMB ? tombSpeed : watcherSpeed;
	}

	/** The lower end of the lance band (never below 0). */
	public float lanceMin() {
		return lanceRange != null && lanceRange.length > 0 ? Math.max(0f, lanceRange[0]) : 6f;
	}

	/** The upper end of the lance band (never below the lower end). */
	public float lanceMax() {
		return lanceRange != null && lanceRange.length > 1 ? Math.max(lanceMin(), lanceRange[1]) : Math.max(lanceMin(), 20f);
	}

	public static SentinelBalance get() {
		return BalanceConfig.section("sentinel", SentinelBalance.class, SentinelBalance::new);
	}
}
