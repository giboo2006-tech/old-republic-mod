package com.oldrepublic.equipment;

import com.oldrepublic.config.BalanceConfig;

/**
 * Tunable numbers of the era armour sets (spec sections 9 and 14). Stored in the {@code equipment}
 * section of {@code config/oldrepublic/balance.json}; read on the server through {@link #get()}.
 * Percentages are whole percent ({@code -6} = 6% less).
 * <p>
 * Items are registered at mod init, before any balance file is loaded, so {@link #trooperDurability},
 * {@link #robeDurability}, {@link #trooperEnchant}, {@link #robeEnchant} and the speed attribute of
 * {@link #trooperSpeedPct} come from the defaults of {@code new EquipmentBalance()}; a changed file
 * value for them does not apply. Every other key is read live.
 */
public final class EquipmentBalance {
	/** Trooper sets, per piece: Force cost change (percent). */
	public float trooperCostPct = 5f;
	/** Trooper sets, per piece: Force regeneration change (percent). */
	public float trooperRegenPct = -5f;
	/** Trooper sets, per piece: movement speed change (percent; registration default only). */
	public float trooperSpeedPct = -1.5f;

	/** Republic trooper armour, per piece: blaster damage taken (percent). */
	public float republicBlasterPct = -6f;
	/** Shoulder to Shoulder: a further blaster damage change (percent). */
	public float republicSetBlasterPct = -10f;
	/** Shoulder to Shoulder: guard stamina drain from bolts (percent). */
	public float republicSetBoltDrainPct = -25f;

	/** Sith trooper armour, per piece: melee damage taken (percent). */
	public float sithMeleePct = -4f;
	/** Iron Discipline: control durations on the wearer (percent). */
	public float sithSetControlPct = -30f;
	/** Iron Discipline: knockback resistance added while 4/4 is worn. */
	public float sithSetKnockbackResist = 0.2f;

	/** Jedi robes, per piece: Force regeneration change (percent). */
	public float jediRegenPct = 6f;
	/** Jedi robes, per piece: Force cost change (percent). */
	public float jediCostPct = -2f;
	/** Calm of the Watch: Force Energy restored per successful parry. */
	public float jediSetParryFe = 5f;
	/** Calm of the Watch: Force Heal (Lift for Independents) cost change (percent). */
	public float jediSetHealCost = -15f;

	/** Sith robes, per piece: flat max Force Energy. */
	public int sithMaxEnergy = 5;
	/** Sith robes, per piece: Force cost change (percent). */
	public float sithCostPct = -2f;
	/** Hunger of the Tomb: Force Energy restored per qualifying kill. */
	public float sithSetKillFe = 8f;
	/** Hunger of the Tomb: a kill counts within this many ticks of a power hit on the target. */
	public int sithSetWindowTicks = 160;
	/** Hunger of the Tomb: Force Lightning (Lift for Independents) cost change (percent). */
	public float sithSetLightningCost = -15f;

	/** Robe set bonuses need alignment at least +this (Jedi) or at most -this (Sith). */
	public int setAlignment = 15;

	/** Durability multiplier of the trooper sets (registration default only). */
	public int trooperDurability = 18;
	/** Durability multiplier of the robes (registration default only). */
	public int robeDurability = 10;
	/** Enchantability of the trooper sets (registration default only). */
	public int trooperEnchant = 9;
	/** Enchantability of the robes (registration default only). */
	public int robeEnchant = 15;

	/** Unused: the uniform disguise (9.5) is cut. Kept so the key stays documented. */
	public int disguiseBreakTicks = 2400;
	/** Unused: the uniform disguise (9.5) is cut. Kept so the key stays documented. */
	public int disguiseBreakRange = 24;

	/** The live server values. Never call during mod init. */
	public static EquipmentBalance get() {
		return BalanceConfig.section("equipment", EquipmentBalance.class, EquipmentBalance::new);
	}
}
