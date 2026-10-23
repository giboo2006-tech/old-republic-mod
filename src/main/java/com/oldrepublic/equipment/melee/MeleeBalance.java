package com.oldrepublic.equipment.melee;

import com.oldrepublic.config.BalanceConfig;

/**
 * Tunable numbers of the vibroweapons and the electrostaff (spec MILESTONE_4.md 5.2, 5.3 and 14.3),
 * the vibro-axe, the force pike and the Starfall and Clan Iron Edges (MILESTONE_7.md 9.1 and 9.2),
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

	// Milestone 7 (MILESTONE_7.md 9.1 and 9.2): the vibro-axe, the force pike and the material edges.

	/** Vibro-axe damage (registration default only). */
	public float axeDamage = 10f;
	/** Vibro-axe attacks per second (registration default only). */
	public float axeSpeed = 0.85f;
	/** Vibro-axe reach added, blocks (registration default only). */
	public float axeReach = 0.25f;
	/** Vibro-axe durability (registration default only). */
	public int axeDurability = 800;
	/** Vibro-axe guard: front melee damage blocked, percent. */
	public float guardAxePct = 40f;
	/** Sunder: attack strength a vibro-axe swing needs (a full swing). */
	public float sunderMinStrength = 0.9f;
	/** Sunder: how many times as fast a full axe swing drains a guard (a saber, NPC or vibro guard). */
	public float sunderDrainMultiplier = 3f;
	/** Sunder: stagger of a foe whose guard the swing broke, ticks. */
	public int sunderStaggerTicks = 15;
	/** Sunder: the stagger's Slowness amplifier (2 is Slowness III, as an NPC's parry stagger). */
	public int sunderStaggerAmplifier = 2;

	/** Force pike damage (registration default only). */
	public float pikeDamage = 7f;
	/** Force pike attacks per second (registration default only). */
	public float pikeSpeed = 1.0f;
	/** Force pike reach added, blocks (registration default only). */
	public float pikeReach = 1.5f;
	/** Force pike durability (registration default only). */
	public int pikeDurability = 650;
	/** Force pike guard: front melee damage blocked, percent. */
	public float guardPikePct = 50f;
	/** Stun thrust: ticks the Weapon Action takes to charge the pike's head. */
	public int pikeChargeTicks = 30;
	/** Stun thrust: a charge is spent by a hit within this many ticks, or lost. */
	public int pikeChargeWindow = 100;
	/** Stun thrust: the stun's length, ticks (a {@code shock} control, so repeats diminish). */
	public int pikeStunTicks = 30;
	/** Stun thrust: the stun's Slowness amplifier (3 is Slowness IV). */
	public int pikeStunSlowness = 3;
	/** Stun thrust: the stun's Weakness amplifier (0 is Weakness I). */
	public int pikeStunWeakness = 0;
	/** Stun thrust: how hard the struck foe is pushed back (as vanilla knockback strength). */
	public float pikeStunKnockback = 0.6f;
	/** Stun thrust: a droid is not stunned; the thrust's damage is multiplied by this instead. */
	public float pikeDroidMultiplier = 1.3f;

	/** Starfall Edge: melee damage to droids and constructs, percent more. */
	public float starfallEdgeDamagePct = 12f;
	/** Starfall Edge: durability, percent more (wear is refunded at this rate). */
	public float starfallEdgeDurabilityPct = 25f;
	/** Clan Iron Edge: guard drain, percent less (it also holds a saber blow without a notch). */
	public float clanIronEdgeDrainPct = 15f;

	/** The live server values. Never call during mod init. */
	public static MeleeBalance get() {
		return BalanceConfig.section("melee", MeleeBalance.class, MeleeBalance::new);
	}
}
