package com.oldrepublic.saber;

/**
 * Tunable lightsaber numbers, section {@code "saber"} of {@code config/oldrepublic/balance.json}.
 * Server side only; clients see the results through item attribute modifiers and menu data.
 */
public final class SaberBalance {
	/** Ignited damage before components (the player's base 1 is included). */
	public float baseDamage = 7.0f;
	/** Ignited attacks per second before components. */
	public float baseAttackSpeed = 1.6f;
	/** Damage of a retracted hilt. */
	public float retractedDamage = 1.0f;

	public float guardianReach = 0.25f;
	public float guardianSpeed = -0.1f;
	public float duelistSpeed = 0.15f;
	public float duelistDamage = -0.5f;
	public float wrappedStaminaMultiplier = 1.2f;
	public float ridgedSpeed = 0.1f;
	public float weightedDamage = 1.0f;
	public float weightedSpeed = -0.15f;
	public float balancedParryMultiplier = 1.1f;

	/** Guard stamina before the grip bonus. */
	public float guardStamina = 100f;
	public float guardRegenPerSecond = 15f;
	/** Ticks without a drain before stamina regenerates. */
	public int guardRegenDelayTicks = 20;
	/** Parry window at the start of a guard, in ticks, before the pommel bonus. */
	public float parryWindowTicks = 6f;
	/** Total width of the guarded arc in front of the player, in degrees. */
	public float guardArcDegrees = 100f;
	/** Fraction of melee damage removed by a successful (non-parry) block. */
	public float meleeReduction = 0.70f;
	public float meleeDrainBase = 6f;
	public float meleeDrainPerDamage = 3f;
	public float boltDrain = 10f;
	/** Half-width of the random forward arc a deflected bolt leaves in, in degrees. */
	public float deflectArcDegrees = 50f;
	/** Ticks the guard is unusable after it breaks (also the slowness on the defender). */
	public int guardBreakTicks = 20;
	/** Stagger applied to a parried melee attacker, in ticks. */
	public int staggerTicks = 20;
	public float parryKnockback = 0.6f;

	// ---- Milestone 2: shared saber stamina, combo, heavy attack, lunge, forms, passives, NPC guard, spars

	/** Light combo: a hit at this attack strength or more within the window continues the string. */
	public int comboWindowTicks = 16;
	public float comboMinStrength = 0.9f;
	public float finisherDamageMult = 1.3f;
	/** Extra knockback of the overhead finisher (blocks per tick, horizontal). */
	public float finisherKnockback = 0.45f;
	public float finisherGuardDrainMult = 2f;

	/** Heavy attack (sneak + attack). */
	public int heavyWindupTicks = 10;
	public float heavyDamageMult = 1.75f;
	public float heavyArcDegrees = 90f;
	public float heavyReachBonus = 0.5f;
	public int heavyMaxTargets = 3;
	public float heavyStaminaCost = 15f;
	public int heavyCooldownTicks = 24;
	public float heavyGuardDrainMult = 3f;
	public int heavyStaggerTicks = 20;
	/** A hit of at least this much damage during the wind-up cancels the heavy attack. */
	public float heavyInterruptDamage = 4f;
	public float heavyMoveMult = 0.5f;

	/** Lunge (sprint + attack at a target out of reach). */
	public float lungeMinRange = 3.5f;
	public float lungeMaxRange = 7f;
	/** Aim assist half-angle in degrees when picking the lunge target. */
	public float lungeAimAssist = 6f;
	public float lungeSpeed = 1.1f;
	public int lungeMaxTicks = 8;
	public float lungeHitDistance = 2.5f;
	public float lungeDamageMult = 1.2f;
	public float lungeStaminaCost = 20f;
	public int lungeCooldownTicks = 60;

	/** Forms. */
	public int formSwitchTicks = 20;
	public int formCombatLockTicks = 60;
	public float soresuParryMult = 1.5f;
	public float soresuGuardArc = 150f;
	public float soresuDrainMult = 0.7f;
	public float soresuRegenMult = 1.2f;
	/** Soresu sends unparried bolts toward the crosshair within this many degrees. */
	public float soresuDeflectAim = 15f;
	public float soresuDamageMult = 0.85f;
	public int soresuHeavyWindupExtra = 4;
	/** Ataru: attacks per second added while the form is active and a lit saber is held. */
	public float ataruSpeed = 0.15f;
	public float ataruStaminaMult = 0.75f;
	/** Ataru parry window change in ticks (negative shortens it). */
	public float ataruParryTicks = -2f;
	public float ataruLungeCost = 12f;
	public float ataruLungeRange = 9f;
	public int ataruLungeCooldown = 40;
	public int ataruComboWindow = 20;
	public float ataruAerialMult = 1.4f;
	/** Read by the force feature for Force Jump while Ataru is active. */
	public float ataruJumpCostMult = 0.75f;
	/** Falling at least this far makes the next attack an Ataru aerial strike. */
	public float ataruMinFall = 1.5f;

	/** Lightsaber passives (skill tree nodes read live through {@code Skills.active}). */
	public int riposteWindowTicks = 20;
	public float riposteDamageMult = 1.4f;
	public float deflectionControlDegrees = 20f;
	public float steadyGuardPerRank = 0.10f;

	/** Guard and parry of saber NPCs ({@code SaberDuelist}). */
	public float npcGuardArc = 120f;
	public float npcBlockDamageMult = 0.15f;
	public float npcDrainBase = 6f;
	public float npcDrainPerDamage = 3f;
	public int npcBrokenTicks = 40;
	public float npcBrokenDamageMult = 1.25f;
	/** NPCs only parry while their stamina is at least this fraction of the maximum. */
	public float npcParryMinStamina = 0.7f;
	public int npcParryStaggerTicks = 15;

	/** Ticks between {@code saber.lightning_block} sounds for one guarding entity. */
	public int lightningBlockSoundInterval = 10;
	public float pushOnGuardKnockMult = 0.5f;
	public float pushOnGuardDrain = 20f;

	/** Spars. */
	public float sparArenaRadius = 16f;
	public float sparPlayerLoseFraction = 0.2f;
}
