package com.oldrepublic.crafting;

import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.saber.SaberStats;

/**
 * Tunable crafting numbers, section {@code "crafting"} of {@code config/oldrepublic/balance.json}
 * (spec MILESTONE_4.md 14.3). Server side only; the client sees results through menu data and the
 * synced {@code crafting} attachment. The quality multipliers are handed to {@link Quality} on
 * server start ({@link #applyQuality}).
 */
public final class CraftingBalance {
	/** Discipline XP needed to reach levels 1 to 5. */
	public int[] levelXp = {0, 120, 360, 800, 1600};
	/** The second discipline opens at this level in the first, or at this player level. */
	public int secondDisciplineLevel = 2;
	public int secondDisciplinePlayerLevel = 13;
	/** XP multiplier for schematics two or more levels below the crafter's discipline level. */
	public float lowLevelXpMultiplier = 0.25f;
	/** Player XP for the first craft of a schematic, per schematic level. */
	public int firstCraftXpPerLevel = 30;

	public float fineBase = 0.18f;
	public float masterworkBase = 0.03f;
	public float finePerLevel = 0.06f;
	public float masterworkPerLevel = 0.03f;
	/** Quality crafts below Masterwork before the next is a guaranteed Masterwork. */
	public int pityCrafts = 10;

	/**
	 * Quality multipliers by tier: Standard, Fine, Masterwork, Legendary (MILESTONE_5.md 11.1). A
	 * stored Milestone 4 file with three values keeps Legendary's built-in value ({@link Quality#configure}).
	 */
	public float[] qualityWeaponDamage = {1.0f, 1.06f, 1.12f, 1.18f};
	public float[] qualityArmour = {1.0f, 1.15f, 1.30f, 1.40f};
	public float[] qualityDurability = {1.0f, 1.25f, 1.5f, 1.75f};

	/** Walking further than this from the bench cancels a craft (no loss). */
	public double craftCancelRange = 6;

	public float salvageReturn = 0.5f;
	public float salvageXp = 0.25f;

	public int unlearnCost = 500;
	public int commissionPerLevel = 50;

	public int medpacUseTicks = 16;
	public float medpacHeal = 8f;
	public int medpacTicks = 40;
	public float medpacInterruptDamage = 4f;
	/** Shared cooldown of every healing item after a medpac. */
	public int healCooldown = 200;

	public int koltoUseTicks = 10;
	public float koltoHeal = 6f;
	public int koltoCooldown = 300;

	public int stimTicks = 1200;
	public float combatStimPct = 8f;
	public float focusStimPct = 20f;
	public int crashTicks = 200;

	public double scannerRange = 48;
	public int scannerTicks = 200;
	public int scannerCooldown = 400;

	public float playerGrenadeDamage = 7f;
	public float playerGrenadeRadius = 3f;
	public double stunRadius = 3;
	public int stunTicks = 30;
	public int stunDroidTicks = 60;
	public int grenadeCooldown = 60;

	public int cutterSealTicks = 60;
	public int cutterDurability = 250;
	public float repairKitPct = 25f;
	public int repairKitUses = 3;

	/** Lens effects by quality (Standard, Fine, Masterwork; Legendary uses the last value): damage %, parry ticks, Force damage %. */
	public float[] lensHoned = {6f, 9f, 12f};
	public float[] lensSteady = {1f, 1f, 2f};
	public float[] lensResonant = {5f, 6.5f, 8f};

	// ---- Milestone 5 (W7): the Legendary tier and its ten components (MILESTONE_5.md 11, 16.4)

	/** A legendary schematic needs its discipline at this level. */
	public int legendaryLevel = 5;
	/** The Cantor's Emitter: damage in percent more. */
	public float cantorDamagePct = 8f;
	/** The Hask Pommel: guard stamina added, parry window ticks added. */
	public float haskGuard = 15f;
	public float haskParryTicks = 1f;
	/** The Sunforged Grip: attack speed in percent more, guard stamina added. */
	public float sunforgedSpeedPct = 8f;
	public float sunforgedGuard = 5f;
	/** The Stormglass Lens: a finisher shocks its target this many ticks; damage against droids in percent more. */
	public int stormglassShockTicks = 20;
	public float stormglassDroidPct = 10f;
	/** Clan-Iron Plating: armour added; blaster damage taken in percent less. */
	public float clanIronArmour = 3f;
	public float clanIronBlasterPct = 10f;
	/** Hymnweave Lining: maximum Force Energy added; Force Energy regeneration in percent more. */
	public float hymnweaveEnergy = 6f;
	public float hymnweaveRegenPct = 10f;
	/** Storm Cell: each blaster hit arcs to one more enemy within this many blocks, for this percent of the damage. */
	public float stormCellRange = 4f;
	public float stormCellPct = 30f;
	/** Sunstone Edge: vibroweapon damage in percent more; the target burns this many ticks. */
	public float sunstoneEdgePct = 12f;
	public int sunstoneEdgeFireTicks = 40;

	/** The live section. */
	public static CraftingBalance get() {
		return BalanceConfig.section("crafting", CraftingBalance.class, CraftingBalance::new);
	}

	/**
	 * Hands the quality multipliers to {@link Quality} and the legendary hilt parts' numbers to
	 * {@code SaberStats} (server start, 7.4; MILESTONE_5.md 11.2), which both sides read.
	 */
	public static void applyQuality() {
		CraftingBalance b = get();
		Quality.configure(b.qualityWeaponDamage, b.qualityArmour, b.qualityDurability);
		SaberStats.configureLegendary(SaberStats.Legendary.of(b));
	}

	/** Index {@code q} of a per-quality array, clamped; 0 when the array is empty. */
	public static float byQuality(float[] values, Quality q) {
		if (values.length == 0) {
			return 0f;
		}

		return values[Math.min(values.length - 1, q.ordinal())];
	}
}
