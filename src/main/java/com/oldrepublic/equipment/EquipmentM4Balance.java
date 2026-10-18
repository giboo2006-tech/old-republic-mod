package com.oldrepublic.equipment;

import com.oldrepublic.config.BalanceConfig;

/**
 * Tunable numbers of the Milestone 4 armour sets and of the gear mods (spec MILESTONE_4.md 6.2,
 * 6.3, 6.4 and 14.3): the {@code armour_m4} section (this class) and the {@code mods} section
 * ({@link ModNumbers}) of {@code config/oldrepublic/balance.json}. Percentages are whole percent and
 * follow the M3 {@link EquipmentBalance} convention: a cost or regeneration percentage is the change
 * ({@code -3} = 3% cheaper or slower), and a "damage taken" percentage is negative for resistance
 * ({@code -4} = 4% less melee damage taken).
 * <p>
 * Base defence, toughness, durability and enchantability are fixed at registration (the M3 rule) and
 * live in {@link EquipmentMaterials}; the movement speed traits are registration defaults too
 * (attribute modifiers). Quality scales a crafted piece's traits (7.4) when they are read.
 */
public final class EquipmentM4Balance {
	/** Watch battle armour, per piece: Force cost change. */
	public float watchCostPct = -3f;
	/** Watch battle armour, per piece: melee damage taken change. */
	public float watchMeleePct = -4f;
	/** Warden's Resolve: melee damage taken change for {@link #watchSetTicks} after a parry. */
	public float watchSetParryResistPct = -15f;
	/** Warden's Resolve: Force Barrier cost change for {@link #watchSetTicks} after a parry. */
	public float watchSetBarrierCostPct = -20f;
	/** Warden's Resolve: how long the bonus lasts after a parry, ticks. */
	public int watchSetTicks = 80;

	/** Sith war armour, per piece: flat max Force Energy. */
	public int sithWarMaxEnergy = 4;
	/** Sith war armour, per piece: melee damage taken change. */
	public float sithWarMeleePct = -5f;
	/** Sith war armour, per piece: Force regeneration change. */
	public float sithWarRegenPct = -3f;
	/** Wrath Unbound applies below this fraction of max health. */
	public float sithWarSetHealthFraction = 0.5f;
	/** Wrath Unbound: Force Choke and Force Lightning cost change. */
	public float sithWarSetCostPct = -20f;
	/** Wrath Unbound: Force Choke and Force Lightning damage dealt change. */
	public float sithWarSetDamagePct = 10f;

	/** Mandalorian armour, per piece: blaster damage taken change. */
	public float mandoBlasterPct = -6f;
	/** Mandalorian armour, per piece: Force cost change. */
	public float mandoCostPct = 5f;
	/** Mandalorian armour, per piece: movement speed change (registration default only). */
	public float mandoSpeedPct = -3f;
	/** Hunter's Tenacity: control durations on the wearer change. */
	public float mandoSetControlPct = -30f;
	/** Hunter's Tenacity: extra jet pack charges. */
	public int mandoSetJetCharges = 1;
	/** Hunter's Tenacity: Force Speed cost change. */
	public float mandoSetSpeedCostPct = 10f;

	/** Spacer's gear, per piece: movement speed change (registration default only). */
	public float spacerSpeedPct = 2f;
	/** Spacer's gear, per piece: Force regeneration change. */
	public float spacerRegenPct = 4f;
	/** Spacer's gear, per piece: fall damage taken change. */
	public float spacerFallPct = -10f;
	/** Light on Your Feet: Force Speed duration change (read by the Force Speed power). */
	public float spacerSetSpeedDurationPct = 30f;
	/** Light on Your Feet: a further fall damage taken change. */
	public float spacerSetFallPct = -50f;
	/** Light on Your Feet: movement efficiency added, so soul sand and ash do not slow the wearer (0 to 1). */
	public float spacerSetTerrainEfficiency = 1f;

	/** The smallest multiplier all gear together may put on a control duration. */
	public float controlMin = 0.4f;

	/** The live {@code armour_m4} values. Never call during mod init. */
	public static EquipmentM4Balance get() {
		return BalanceConfig.section("armour_m4", EquipmentM4Balance.class, EquipmentM4Balance::new);
	}

	/** The live {@code mods} values. Never call during mod init. */
	public static ModNumbers mods() {
		return BalanceConfig.section("mods", ModNumbers.class, ModNumbers::new);
	}

	/**
	 * The {@code mods} section (spec 6.3 and 6.4). Arrays hold one value per quality: Standard, Fine,
	 * Masterwork. Damage, cost and regeneration go through the shared modifier registries and their
	 * clamps, so mods never stack beyond them.
	 */
	public static final class ModNumbers {
		/** {@code mod_plating}: flat armour added. */
		public float platingArmour = 1f;
		/** {@code mod_plating}: blaster damage taken change. */
		public float[] platingBlasterPct = {-2f, -3f, -4f};
		/** {@code mod_weave}: melee damage taken change. */
		public float[] weaveMeleePct = {-2f, -3f, -4f};
		/** {@code mod_attunement}: Force cost change. */
		public float[] attunementCostPct = {-2f, -3f, -4f};
		/** {@code mod_conduit}: Force regeneration change. */
		public float[] conduitRegenPct = {3f, 4.5f, 6f};
		/** {@code mod_stabiliser}: control durations on the wearer change. */
		public float[] stabiliserControlPct = {-8f, -12f, -16f};
		/** {@code mod_focused_barrel}: spread change. */
		public float[] focusedSpreadPct = {-20f, -30f, -40f};
		/** {@code mod_heavy_barrel}: bolt damage change. */
		public float[] heavyDamagePct = {6f, 9f, 12f};
		/** {@code mod_heavy_barrel}: ticks added between shots. */
		public int heavyRateTicks = 1;
		/** {@code mod_cooling_cell}: heat per shot change. */
		public float[] coolingHeatPct = {-15f, -22f, -30f};
		/** {@code mod_overcharged_cell}: bolt damage change. */
		public float[] overchargedDamagePct = {4f, 6f, 8f};
		/** {@code mod_overcharged_cell}: heat per shot change. */
		public float overchargedHeatPct = 10f;
		/** {@code mod_keen_edge}: melee damage change. */
		public float[] keenDamagePct = {5f, 7.5f, 10f};
		/** {@code mod_shock_edge}: chance per hit to shock, percent. */
		public float[] shockChancePct = {8f, 12f, 16f};
		/** {@code mod_shock_edge}: shock length, ticks. */
		public int shockTicks = 20;

		/** Jet pack charges. */
		public int jetCharges = 2;
		/** Ticks on the ground that refill the jet pack. */
		public int jetRefillTicks = 100;
		/** Upward velocity of a burst. */
		public float jetUp = 0.85f;
		/** Velocity along the horizontal look direction of a burst. */
		public float jetForward = 0.3f;
		/** Fall damage taken change after a burst shortly before landing. */
		public float jetFallPct = -50f;
		/** A burst this many ticks or less before landing counts for {@link #jetFallPct}. */
		public int jetLandWindow = 20;
		/** Ticks between two bursts (W4 addition: a held key cannot fire every charge in one tick). */
		public int jetBurstGapTicks = 4;

		/** {@code values[quality]}, or the last value when the array is short (a hand-edited file). */
		public static float of(float[] values, int quality) {
			return values.length == 0 ? 0f : values[Math.max(0, Math.min(values.length - 1, quality))];
		}
	}
}
