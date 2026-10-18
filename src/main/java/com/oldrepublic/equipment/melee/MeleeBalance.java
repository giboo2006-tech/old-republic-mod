package com.oldrepublic.equipment.melee;

import com.oldrepublic.config.BalanceConfig;

/**
 * Tunable numbers of the vibroweapons and the electrostaff (spec MILESTONE_4.md 5.2, 5.3 and 14.3),
 * the {@code melee} section of {@code config/oldrepublic/balance.json}. The guard's stamina pool,
 * regeneration and arc are the M2 saber guard's ({@code saber} section), so every guard in the game
 * feels the same.
 * <p>
 * Damage, attack speed, reach and durability are item attributes and components fixed at
 * registration, so they come from the defaults of {@code new MeleeBalance()}; a changed file value
 * for them does not apply. Every other key is read live on the server.
 */
public final class MeleeBalance {
	/** Vibroblade damage (registration default only). */
	public float vibrobladeDamage = 6f;
	/** Vibroblade attacks per second (registration default only). */
	public float vibrobladeSpeed = 1.8f;
	/** Flurry: the hit in a chain that gets the bonus. */
	public int flurryHits = 3;
	/** Flurry: the most ticks between two hits of one chain. */
	public int flurryWindow = 30;
	/** Flurry: damage added to the chain's last hit. */
	public float flurryBonus = 3f;

	/** Vibrosword damage (registration default only). */
	public float vibroswordDamage = 8.5f;
	/** Vibrosword attacks per second (registration default only). */
	public float vibroswordSpeed = 1.2f;
	/** Vibrosword reach added, blocks (registration default only). */
	public float vibroswordReach = 0.5f;
	/** Cleave: extra enemies a fully charged vibrosword hit also strikes. */
	public int cleaveTargets = 2;
	/** Cleave: how far from the wielder, in front, the extra enemies may be. */
	public float cleaveRange = 2f;
	/** Cleave: damage of the extra hits as a fraction of the swing's. */
	public float cleaveMultiplier = 0.6f;
	/** Cleave: attack strength a swing needs to count as fully charged (W4 addition). */
	public float cleaveMinStrength = 0.9f;

	/** Vibroblade durability (registration default only). */
	public int vibrobladeDurability = 500;
	/** Vibrosword durability (registration default only). */
	public int vibroswordDurability = 700;

	/** Vibroblade guard: front melee damage blocked, percent. */
	public float guardBladePct = 50f;
	/** Vibrosword guard: front melee damage blocked, percent. */
	public float guardSwordPct = 65f;
	/** Electrostaff guard: front melee damage blocked, percent. */
	public float guardStaffPct = 60f;
	/** Durability a saber parry (or a guarded saber blow) takes from a vibroweapon: the blade notches. */
	public int saberNotch = 5;

	/** Electrostaff damage (registration default only). */
	public float staffDamage = 6.5f;
	/** Electrostaff attacks per second (registration default only). */
	public float staffSpeed = 1.1f;
	/** Electrostaff reach added, blocks (registration default only). */
	public float staffReach = 1.0f;
	/** Electrostaff durability (registration default only; W4 addition, between the blade and the sword). */
	public int staffDurability = 600;
	/** Electrostaff: ticks Weapon Action is held to charge the emitters. */
	public int staffChargeTicks = 40;
	/** Electrostaff: a charge is spent by a hit within this many ticks, or lost. */
	public int staffChargeWindow = 100;
	/** Electrostaff: shock length on the struck target and the arc's target, ticks. */
	public int staffShockTicks = 20;
	/** Electrostaff: how far the arc reaches to a second enemy, blocks. */
	public float staffArcRange = 3f;
	/** Electrostaff: damage of the arc to the second enemy (W4 addition). */
	public float staffArcDamage = 2f;
	/** Droids take this much more damage from shocks (the staff's hit and arc, a shock edge). */
	public float droidShockMultiplier = 1.5f;
	/** Electrostaff sweep: damage to each enemy next to the struck one (the vanilla sweep's 1). */
	public float staffSweepDamage = 1f;

	/** The live server values. Never call during mod init. */
	public static MeleeBalance get() {
		return BalanceConfig.section("melee", MeleeBalance.class, MeleeBalance::new);
	}
}
