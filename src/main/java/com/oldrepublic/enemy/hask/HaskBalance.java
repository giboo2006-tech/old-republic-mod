package com.oldrepublic.enemy.hask;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code hask_warrior} of {@code config/oldrepublic/balance.json} (spec
 * MILESTONE_5.md 12.1, 16.3, 16.4): Clan Hask's warriors, their three kits, the shield and the
 * jet-slam. Durations in ticks, distances in blocks.
 */
public final class HaskBalance {
	// ------------------------------------------------ health and armour (12.1)
	public float rifleHealth = 40f;
	public float rifleArmor = 10f;
	public float bladeHealth = 40f;
	public float bladeArmor = 10f;
	public float championHealth = 80f;
	public float championArmor = 12f;
	public float speed = 0.27f;
	public float followRange = 32f;
	public float knockbackResistance = 0.3f;

	// ------------------------------------------------ kill XP (16.3, paid through progression.KillXp) and drops
	public int rifleXp = 70;
	public int bladeXp = 70;
	public int championXp = 150;
	/** The chance of one {@code clan_iron} on a warrior's death (16.4). */
	public float clanIronChance = 0.1f;

	// ------------------------------------------------ rifle: bolts and the wrist rocket (the M4 hunter's tells)
	public float rifleDamage = 6f;
	public int rifleTellTicks = 12;
	public int burst = 2;
	public int burstGapTicks = 6;
	public int burstCooldownTicks = 40;
	public float boltSpeed = 2.6f;
	public int boltLifetimeTicks = 40;
	public float aimInaccuracy = 2f;
	public float lead = 0.5f;
	public float speedLead = 0.8f;
	public float minRange = 8f;
	public float maxRange = 18f;
	public float rocketDamage = 7f;
	public float rocketSpeed = 0.9f;
	public float rocketRadius = 2.5f;
	public int rocketLifetimeTicks = 60;
	public int rocketTellTicks = 20;
	public int rocketIntervalTicks = 300;
	/** Rifle warriors hop to high ground or a flank on their jets. */
	public int jetIntervalTicks = 200;
	public int jetTellTicks = 12;
	public int jetFlightTicks = 16;
	public float jetArcHeight = 2.5f;
	public float jetMinDistance = 6f;
	public float jetMaxDistance = 12f;

	// ------------------------------------------------ blade: vibrosword strings and the jet-slam
	public float bladeDamage = 8f;
	public float championDamage = 9f;
	public int windupTicks = 12;
	public int gapTicks = 8;
	public int recoveryTicks = 28;
	public int stringCooldownTicks = 30;
	public float swordReach = 2.7f;
	public float jetSlamDamage = 9f;
	public int jetSlamTellTicks = 20;
	public int jetSlamIntervalTicks = 160;
	public int jetSlamFlightTicks = 14;
	public float jetSlamRadius = 3f;
	public float jetSlamKnockback = 0.8f;
	public float jetSlamMinRange = 5f;
	public float jetSlamMaxRange = 12f;

	// ------------------------------------------------ champion: the clan-iron shield and the bash
	/** Bolts from within this many degrees of its facing hit the shield (either side). */
	public float shieldArcDegrees = 70f;
	public int bashIntervalTicks = 160;
	public int bashTellTicks = 15;
	public float bashDamage = 6f;
	public float bashKnockback = 1f;
	public int bashStaggerTicks = 20;
	public float bashReach = 3f;

	// ------------------------------------------------ attitude (7)
	public float waryRange = 8f;
	public int warnRepeatTicks = 200;
	public int engageTicks = 400;
	public int provokeTicks = 400;
	public float safeRadius = 6f;
	public int hubLeash = 8;
	/** Players this close to the owner of an owned (allied) warrior count as the owner's party. */
	public float partyRange = 24f;

	public static HaskBalance get() {
		return BalanceConfig.section("hask_warrior", HaskBalance.class, HaskBalance::new);
	}
}
