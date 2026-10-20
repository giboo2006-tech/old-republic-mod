package com.oldrepublic.enemy.cartel;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code cartel_enforcer} of {@code config/oldrepublic/balance.json} (spec
 * MILESTONE_5.md 12.1, 16.3, 16.4): the Vessba kajidic's enforcers, their three kits and the
 * attitude rules. Durations in ticks, distances in blocks.
 */
public final class CartelBalance {
	// ------------------------------------------------ health and armour (12.1)
	public float gunmanHealth = 26f;
	public float gunmanArmor = 4f;
	public float bruiserHealth = 34f;
	public float bruiserArmor = 6f;
	public float captainHealth = 48f;
	public float captainArmor = 8f;
	public float speed = 0.28f;
	public float followRange = 28f;
	public float knockbackResistance = 0.1f;

	// ------------------------------------------------ kill XP (16.3; unread until a per-type kill XP hook exists)
	public int gunmanXp = 45;
	public int bruiserXp = 50;
	public int captainXp = 90;

	// ------------------------------------------------ gunman: a blaster rifle from cover
	public float rifleDamage = 5f;
	/** The muzzle glow before each burst. */
	public int rifleTellTicks = 12;
	public int rifleBurst = 2;
	public int rifleBurstGapTicks = 6;
	public int rifleCooldownTicks = 40;
	public float boltSpeed = 2.4f;
	public int boltLifetimeTicks = 40;
	public float aimInaccuracy = 2.5f;
	/** The gunman keeps between these distances. */
	public float rifleMinRange = 7f;
	public float rifleMaxRange = 18f;
	/** The M1 cover rule (PoacherCoverGoal): below this health fraction it runs to cover. */
	public float coverHealthFraction = 0.6f;
	public float coverSpeed = 1.2f;
	public int coverHoldMinTicks = 40;
	public int coverHoldMaxTicks = 80;
	public int coverCooldownTicks = 200;

	// ------------------------------------------------ bruiser: a vibrosword string and a shove
	public float swordDamage = 7f;
	public float shoveDamage = 4f;
	public float shoveKnockback = 0.9f;
	/** The wind-up of the first blow (the M2 npc_windup tell), the gap before the shove, the recovery after. */
	public int stringWindupTicks = 14;
	public int stringGapTicks = 9;
	public int stringRecoveryTicks = 30;
	public int stringCooldownTicks = 30;
	public float swordReach = 2.6f;

	// ------------------------------------------------ captain: a blaster pistol, frag grenades and the call
	public float pistolDamage = 5f;
	public int pistolTellTicks = 10;
	public int pistolCooldownTicks = 30;
	public float fragDamage = 7f;
	public float fragRadius = 3f;
	/** The M3 grenade_tell: the arm drawn back with the grenade's arming light. */
	public int fragTellTicks = 20;
	public int fragCooldownTicks = 200;
	public float fragMinRange = 5f;
	public float fragMaxRange = 16f;
	/** The captain's call brings every enforcer this close onto its target. */
	public float callRange = 24f;
	public int callCooldownTicks = 400;

	// ------------------------------------------------ attitude (7)
	/** A Distrusted player this close is warned, and fought if they draw a weapon within it. */
	public float waryRange = 8f;
	public int warnRepeatTicks = 200;
	/** How long a fight it started (engagement) or a player started (provocation) lasts after the last blow. */
	public int engageTicks = 400;
	public int provokeTicks = 400;
	/** Hub guards never start a fight this close to a pad bay or the medbay (7: Hostile). */
	public float safeRadius = 6f;
	/** A hub guard stays this close to its post. */
	public int hubLeash = 8;
	/** "The owner's party": players this close to the owner of an owned group count with the owner. */
	public float partyRange = 24f;

	public static CartelBalance get() {
		return BalanceConfig.section("cartel_enforcer", CartelBalance.class, CartelBalance::new);
	}
}
