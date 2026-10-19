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

	// ---- Milestone 3: NPC deflection, heavy and non-deflectable projectiles, the parry cap, the
	// Archivist pommel and Reliquary emitter, and the saber nodes of the Jedi and Sith trees (spec 10.4, 10.7)

	/** A guarding saber NPC that deflects sends bolts back from shooters at most this many degrees off its facing (either side). */
	public float npcDeflectConeDegrees = 70f;
	/** How far an NPC-deflected bolt may stray from the shooter's chest, in degrees. */
	public float npcDeflectSpreadDegrees = 15f;
	/** Stamina a guard loses blocking a heavy bolt (the sentinel lance), which it cannot deflect. */
	public float heavyBoltGuardDrain = 35f;
	/** Stamina a guard loses blocking a projectile it cannot deflect (Archive debris), when the projectile names none. */
	public float nonDeflectableGuardDrain = 30f;
	/** The parry window never exceeds this many ticks, after the flat bonuses and the form multiplier. */
	public float parryWindowCap = 11f;
	/** {@code pommel_archivist}: parry window bonus in ticks. */
	public float archivistParryTicks = 2f;
	/** {@code pommel_archivist}: attacks per second added (negative slows). */
	public float archivistAttackSpeed = -0.05f;
	/** {@code emitter_reliquary}: deflections go within this many degrees of the crosshair. */
	public float reliquaryDeflectDegrees = 12f;
	/** {@code emitter_reliquary}: parried bolts deal this much more damage (0.15 = +15%). */
	public float reliquaryParryBonus = 0.15f;
	/** {@code watchers_insight}: Force Energy restored by a parry (capped by {@code progression.parryRefundCap}). */
	public float watchersInsightFe = 3f;
	/** {@code archivists_ward}: parry window bonus in ticks. */
	public float archivistsWardTicks = 2f;
	/** {@code knights_vigil}: allies within this many blocks of the guarding Knight take less damage. */
	public float knightsVigilRange = 6f;
	public float knightsVigilReduction = 0.1f;
	/** {@code lords_dominion}: after the Lord's heavy attack, non-boss enemies within this range deal less damage. */
	public float lordsDominionRange = 8f;
	public float lordsDominionReduction = 0.1f;
	public int lordsDominionTicks = 100;

	// ---- Milestone 4 (W5): the training saber, the saberstaff, staff_whirl and lens_mastery (spec MILESTONE_4.md 5.5-5.7, 14)

	/** {@code training_saber}: its ignited damage (it never kills a player). */
	public float trainingSaberDamage = 2.5f;
	/** Saberstaff with both blades lit, against a saber of the same parts: damage per hit, in percent (negative is less). */
	public float staffDamagePct = -10f;
	/** Saberstaff: attacks per second added. */
	public float staffSpeedBonus = 0.2f;
	/** Saberstaff: reach added, in blocks. */
	public float staffReach = 0.5f;
	/** Saberstaff: guard stamina drained per block, in percent more. */
	public float staffGuardDrainPct = 20f;
	/** Saberstaff: parry window change in ticks (negative is shorter). */
	public float staffParryTicks = -1f;
	/** Saberstaff: degrees added to the guard and deflection arc (the back blade). */
	public float staffConeDegrees = 15f;
	/** Saberstaff throw: living targets hit at most. */
	public int staffThrowTargets = 3;
	/** Saberstaff throw: speed multiplier (a slower arc). */
	public float staffThrowSpeedMult = 0.8f;
	/** Saberstaff throw: extra hit-box inflation in blocks (a wider arc). */
	public float staffThrowWidth = 0.6f;
	/** Whirl (the staff's heavy attack): radius in blocks, damage in percent of the ignited damage, wind-up and recovery ticks. */
	public float whirlRange = 2.5f;
	public float whirlPct = 75f;
	public int whirlWindup = 14;
	public int whirlRecovery = 20;
	/** {@code staff_whirl}: the whirl also staggers each target hit for this many ticks. */
	public int staffWhirlStaggerTicks = 10;
	/** {@code lens_mastery}: lens effects, in percent more. */
	public float lensMasteryPct = 50f;

	// ---- Milestone 5 (W7): the three new forms, dual-wielding, blade length and crystal tuning (spec MILESTONE_5.md 10, 16.4)

	/** Makashi: parry window change in ticks (after the multiplier, before the cap). */
	public float makashiParryTicks = 2f;
	/** Makashi: its own riposte window after a parry, in ticks. */
	public int makashiRiposteWindow = 20;
	/** Makashi with the {@code riposte} passive as well: the window instead (the two never stack). */
	public int makashiPassiveWindow = 30;
	/** Makashi: the riposte's damage bonus, in percent. */
	public float makashiRipostePct = 40f;
	/** Makashi: damage against saber users ({@code SaberDuelist}), in percent more. */
	public float makashiDuelistPct = 15f;
	/** Makashi: guard stamina drained by blaster bolts, in percent more. */
	public float makashiBoltGuardPct = 20f;
	/** Djem So: heavy attack damage, in percent more. */
	public float djemSoHeavyPct = 20f;
	/** Djem So: ticks taken off the heavy wind-up. */
	public int djemSoWindupCut = 2;
	/** Djem So: a parried melee attacker is also staggered this many ticks. */
	public int djemSoStaggerTicks = 10;
	/** Djem So: chance that a deflected (not parried) bolt flies back at its shooter. */
	public float djemSoReturnChance = 0.6f;
	/** Djem So: attack speed change, in percent. */
	public float djemSoSpeedPct = -5f;
	/** Jar'Kai (only while dual-wielding): attack speed change in percent, guard stamina added, parry window change in ticks. */
	public float jarKaiSpeedPct = 10f;
	public float jarKaiGuard = 15f;
	public float jarKaiParryTicks = -1f;
	/** Dual-wielding: an off-hand strike deals this fraction of the off saber's damage. */
	public float offhandDamage = 0.7f;
	/** Dual-wielding: an off-hand strike recovers this fraction slower (0.2 = 20%). */
	public float offhandSpeed = 0.2f;
	/** Dual-wielding: parry window ticks added while guarding with two lit blades (a flat bonus, under the cap). */
	public float dualParryTicks = 1f;
	/** Blade length (10.3): short and long blades' reach in blocks, attack speed and damage in percent. */
	public float shortReach = -0.5f;
	public float shortSpeedPct = 10f;
	public float shortDamagePct = -10f;
	public float longReach = 0.5f;
	public float longSpeedPct = -10f;
	public float longDamagePct = 5f;
	/** Crystal tuning (10.4): the credits each tuning costs. */
	public int tuneCredits = 100;
	/** Keen: damage in percent more. */
	public float keenPct = 5f;
	/** Swift: attack speed in percent more. */
	public float swiftPct = 6f;
	/** Poised ({@code steady}): parry window ticks added (a flat bonus, under the cap). */
	public float steadyTicks = 1f;
	/** Warding: guard stamina added. */
	public float wardingGuard = 15f;
	/** Attuned ({@code resonant}): Force power costs in percent less while the saber is lit. */
	public float resonantPct = 5f;
	/** Materials a tuning takes: sunstone (keen, swift), stormglass shards (steady, warding), hymnstone shards (resonant). */
	public int tuneSunstone = 2;
	public int tuneStormglass = 2;
	public int tuneHymnstone = 1;
}
