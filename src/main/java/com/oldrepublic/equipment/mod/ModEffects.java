package com.oldrepublic.equipment.mod;

import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.equipment.EquipmentM4Balance.ModNumbers;
import com.oldrepublic.registry.M4Items;

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
 * range; control multipliers are clamped by the equipment modifier ({@code controlMin}).
 */
public final class ModEffects {
	/** {@code 1 + pct/100} of the fitted mod's quality, or 1 when {@code mod} is not fitted. */
	private static float ratio(ItemStack gear, Item mod, float[] pct) {
		int quality = Mods.fittedQuality(gear, mod);
		return quality < 0 ? 1f : 1f + ModNumbers.of(pct, quality) / 100f;
	}

	/** Blaster spread multiplier: {@code mod_focused_barrel}. */
	public static float spread(ItemStack gun, ModNumbers m) {
		return Math.max(0f, ratio(gun, M4Items.MOD_FOCUSED_BARREL, m.focusedSpreadPct));
	}

	/** Blaster heat-per-shot multiplier: {@code mod_cooling_cell}, or {@code mod_overcharged_cell}'s extra heat. */
	public static float heat(ItemStack gun, ModNumbers m) {
		float mult = ratio(gun, M4Items.MOD_COOLING_CELL, m.coolingHeatPct);

		if (Mods.has(gun, M4Items.MOD_OVERCHARGED_CELL)) {
			mult *= 1f + m.overchargedHeatPct / 100f;
		}

		return Math.max(0f, mult);
	}

	/** Blaster bolt damage multiplier: {@code mod_heavy_barrel} and {@code mod_overcharged_cell}. */
	public static float boltDamage(ItemStack gun, ModNumbers m) {
		return ratio(gun, M4Items.MOD_HEAVY_BARREL, m.heavyDamagePct) * ratio(gun, M4Items.MOD_OVERCHARGED_CELL, m.overchargedDamagePct);
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
		int count = 0;

		for (EquipmentSlot slot : ArmourSets.SLOTS) {
			count += Mods.has(e.getItemBySlot(slot), M4Items.MOD_PLATING) ? 1 : 0;
		}

		return count * m.platingArmour;
	}

	/** Whether the chest piece {@code e} wears has the jet pack fitted. */
	public static boolean jetPack(LivingEntity e) {
		return Mods.has(e.getItemBySlot(EquipmentSlot.CHEST), M4Items.JET_PACK);
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
