package com.oldrepublic.equipment.mod;

import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.equipment.EquipmentM4Balance.ModNumbers;
import com.oldrepublic.equipment.blaster.BlasterBalance;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.M7Items;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What each fitted mod does (spec MILESTONE_4.md 6.3), as multipliers for the callers that apply
 * them: the blaster's spread, heat, rate and bolt damage; the melee edge; and the worn armour mods
 * summed over the four armour slots. Strength comes from the mod stack's own quality (Standard,
 * Fine, Masterwork). Damage, Force cost and regeneration multipliers go through the shared
 * {@code DamageModifiers} and {@code ForceModifiers} registries, whose clamps keep stacked mods in
 * range; control multipliers are clamped by the equipment modifier ({@code controlMin}). The
 * legendary mods (MILESTONE_5.md 11.2, {@link LegendaryMods}) have fixed numbers from the
 * {@code crafting} balance section and stack per worn piece as the M4 armour mods do. The blasters'
 * material mods (MILESTONE_7.md 9.2: the aurelite barrel and the duskglass cell) have fixed numbers
 * from the {@code blasters} section; the blaster multipliers that read them come in two forms, one
 * with the live server numbers and one with numbers passed in (the client passes the defaults).
 */
public final class ModEffects {
	/** {@code 1 + pct/100} of the fitted mod's quality, or 1 when {@code mod} is not fitted. */
	private static float ratio(ItemStack gear, Item mod, float[] pct) {
		int quality = Mods.fittedQuality(gear, mod);
		return quality < 0 ? 1f : 1f + ModNumbers.of(pct, quality) / 100f;
	}

	/** {@code 1 + pct/100} when {@code mod} is fitted, whatever its quality (a material mod's fixed number), or 1. */
	private static float fixed(ItemStack gear, Item mod, float pct) {
		return Mods.has(gear, mod) ? Math.max(0f, 1f + pct / 100f) : 1f;
	}

	/** Blaster spread multiplier: {@code mod_focused_barrel} or {@code mod_aurelite_barrel}. Server: the live blaster numbers. */
	public static float spread(ItemStack gun, ModNumbers m) {
		return spread(gun, m, BlasterBalance.get());
	}

	/** {@link #spread(ItemStack, ModNumbers)} with the blaster numbers {@code b}. */
	public static float spread(ItemStack gun, ModNumbers m, BlasterBalance b) {
		return Math.max(0f, ratio(gun, M4Items.MOD_FOCUSED_BARREL, m.focusedSpreadPct) * fixed(gun, M7Items.MOD_AURELITE_BARREL, b.aureliteSpreadPct));
	}

	/**
	 * Blaster heat-per-shot multiplier: {@code mod_cooling_cell} or {@code mod_duskglass_cell}, or
	 * {@code mod_overcharged_cell}'s extra heat. Server: the live blaster numbers.
	 */
	public static float heat(ItemStack gun, ModNumbers m) {
		return heat(gun, m, BlasterBalance.get());
	}

	/** {@link #heat(ItemStack, ModNumbers)} with the blaster numbers {@code b}. */
	public static float heat(ItemStack gun, ModNumbers m, BlasterBalance b) {
		float mult = ratio(gun, M4Items.MOD_COOLING_CELL, m.coolingHeatPct) * fixed(gun, M7Items.MOD_DUSKGLASS_CELL, b.duskglassHeatPct);

		if (Mods.has(gun, M4Items.MOD_OVERCHARGED_CELL)) {
			mult *= 1f + m.overchargedHeatPct / 100f;
		}

		return Math.max(0f, mult);
	}

	/**
	 * Blaster bolt damage multiplier: {@code mod_heavy_barrel}, {@code mod_overcharged_cell} and
	 * {@code mod_aurelite_barrel}. Server: the live blaster numbers.
	 */
	public static float boltDamage(ItemStack gun, ModNumbers m) {
		return boltDamage(gun, m, BlasterBalance.get());
	}

	/** {@link #boltDamage(ItemStack, ModNumbers)} with the blaster numbers {@code b}. */
	public static float boltDamage(ItemStack gun, ModNumbers m, BlasterBalance b) {
		return ratio(gun, M4Items.MOD_HEAVY_BARREL, m.heavyDamagePct) * ratio(gun, M4Items.MOD_OVERCHARGED_CELL, m.overchargedDamagePct)
				* fixed(gun, M7Items.MOD_AURELITE_BARREL, b.aureliteDamagePct);
	}

	/** Ticks a heavy barrel adds between shots (0 without one). */
	public static int rateTicks(ItemStack gun, ModNumbers m) {
		return Mods.has(gun, M4Items.MOD_HEAVY_BARREL) ? m.heavyRateTicks : 0;
	}

	/** Melee damage multiplier: {@code mod_keen_edge}. */
	public static float meleeDamage(ItemStack weapon, ModNumbers m) {
		return ratio(weapon, M4Items.MOD_KEEN_EDGE, m.keenDamagePct);
	}

	/** Chance per hit (0 to 1) that {@code mod_shock_edge} shocks. */
	public static float shockChance(ItemStack weapon, ModNumbers m) {
		int quality = Mods.fittedQuality(weapon, M4Items.MOD_SHOCK_EDGE);
		return quality < 0 ? 0f : ModNumbers.of(m.shockChancePct, quality) / 100f;
	}

	/** Force cost multiplier of the worn {@code mod_attunement}s. */
	public static float wornCost(LivingEntity e, ModNumbers m) {
		return worn(e, M4Items.MOD_ATTUNEMENT, m.attunementCostPct);
	}

	/** Force regeneration multiplier of the worn {@code mod_conduit}s. */
	public static float wornRegen(LivingEntity e, ModNumbers m) {
		return worn(e, M4Items.MOD_CONDUIT, m.conduitRegenPct);
	}

	/** Blaster damage taken multiplier of the worn {@code mod_plating}s. */
	public static float wornBlaster(LivingEntity e, ModNumbers m) {
		return worn(e, M4Items.MOD_PLATING, m.platingBlasterPct);
	}

	/** Melee damage taken multiplier of the worn {@code mod_weave}s. */
	public static float wornMelee(LivingEntity e, ModNumbers m) {
		return worn(e, M4Items.MOD_WEAVE, m.weaveMeleePct);
	}

	/** Control duration multiplier of the worn {@code mod_stabiliser}s. */
	public static float wornControl(LivingEntity e, ModNumbers m) {
		return worn(e, M4Items.MOD_STABILISER, m.stabiliserControlPct);
	}

	/** Flat armour of the worn {@code mod_plating}s. */
	public static float wornArmour(LivingEntity e, ModNumbers m) {
		return wornCount(e, M4Items.MOD_PLATING) * m.platingArmour;
	}

	// ---------------------------------------------------------------- the legendary mods (MILESTONE_5.md 11.2)

	/** Flat armour of the worn {@code mod_clan_iron_plating}s. */
	public static float wornClanIronArmour(LivingEntity e, CraftingBalance b) {
		return wornCount(e, LegendaryMods.CLAN_IRON_PLATING) * b.clanIronArmour;
	}

	/** Blaster damage taken multiplier of the worn {@code mod_clan_iron_plating}s. */
	public static float wornClanIronBlaster(LivingEntity e, CraftingBalance b) {
		return (float) Math.pow(Math.max(0f, 1f - b.clanIronBlasterPct / 100f), wornCount(e, LegendaryMods.CLAN_IRON_PLATING));
	}

	/** Maximum Force Energy the worn {@code mod_hymnweave_lining}s add. */
	public static int wornHymnweaveEnergy(LivingEntity e, CraftingBalance b) {
		return Math.round(wornCount(e, LegendaryMods.HYMNWEAVE_LINING) * b.hymnweaveEnergy);
	}

	/** Force regeneration multiplier of the worn {@code mod_hymnweave_lining}s. */
	public static float wornHymnweaveRegen(LivingEntity e, CraftingBalance b) {
		return (float) Math.pow(1f + b.hymnweaveRegenPct / 100f, wornCount(e, LegendaryMods.HYMNWEAVE_LINING));
	}

	/** Whether {@code gun} has the {@code mod_storm_cell}: each hit arcs to one more enemy. */
	public static boolean stormCell(ItemStack gun) {
		return Mods.has(gun, LegendaryMods.STORM_CELL);
	}

	/** Melee damage multiplier of a fitted {@code mod_sunstone_edge}, or 1. */
	public static float sunstoneEdge(ItemStack weapon, CraftingBalance b) {
		return Mods.has(weapon, LegendaryMods.SUNSTONE_EDGE) ? 1f + b.sunstoneEdgePct / 100f : 1f;
	}

	/** Ticks a fitted {@code mod_sunstone_edge} sets a struck target alight for, or 0. */
	public static int sunstoneFireTicks(ItemStack weapon, CraftingBalance b) {
		return Mods.has(weapon, LegendaryMods.SUNSTONE_EDGE) ? Math.max(0, b.sunstoneEdgeFireTicks) : 0;
	}

	/** Whether the chest piece {@code e} wears has the jet pack fitted. */
	public static boolean jetPack(LivingEntity e) {
		return Mods.has(e.getItemBySlot(EquipmentSlot.CHEST), M4Items.JET_PACK);
	}

	/** How many of the four armour pieces {@code e} wears have {@code mod} fitted. */
	private static int wornCount(LivingEntity e, Item mod) {
		int count = 0;

		for (EquipmentSlot slot : ArmourSets.SLOTS) {
			count += Mods.has(e.getItemBySlot(slot), mod) ? 1 : 0;
		}

		return count;
	}

	/** The product over the four armour slots of each fitted {@code mod}'s ratio. */
	private static float worn(LivingEntity e, Item mod, float[] pct) {
		float mult = 1f;

		for (EquipmentSlot slot : ArmourSets.SLOTS) {
			mult *= ratio(e.getItemBySlot(slot), mod, pct);
		}

		return mult;
	}

	private ModEffects() {
	}
}
