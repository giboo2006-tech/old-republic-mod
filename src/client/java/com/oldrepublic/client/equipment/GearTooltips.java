package com.oldrepublic.client.equipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.equipment.ArmourItem;
import com.oldrepublic.equipment.EquipmentM4Balance.ModNumbers;
import com.oldrepublic.equipment.mod.LegendaryMods;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.registry.M4Items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Mod lines in tooltips (spec MILESTONE_4.md 6.3): a mod item says which slot it fits and what it
 * does at its quality; a weapon or armour piece lists its fitted mods with their effects, then its
 * open slots. The numbers are the default balance (the client never reads the balance file); the
 * legendary mods' (MILESTONE_5.md 11.2) are the {@code crafting} section's defaults.
 */
final class GearTooltips {
	private static final ModNumbers MODS = new ModNumbers();
	private static final CraftingBalance LEGENDARY = new CraftingBalance();

	static void append(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		ModSlot fits = Mods.slotOf(stack);

		if (fits != null) {
			lines.add(Component.translatable("equipment.oldrepublic.mod.fits", slotName(fits)).withStyle(ChatFormatting.GRAY));
			Component effect = effect(stack.getItem(), Crafting.quality(stack).ordinal());

			if (effect != null) {
				lines.add(effect.copy().withStyle(ChatFormatting.BLUE));
			}

			return;
		}

		List<ModSlot> slots = Mods.slotsOf(stack);

		if (slots.isEmpty()) {
			return;
		}

		Map<ModSlot, ItemStack> fitted = Mods.fitted(stack).mods();
		List<Component> open = new ArrayList<>();

		for (ModSlot slot : slots) {
			ItemStack mod = fitted.get(slot);

			if (mod == null || mod.isEmpty()) {
				open.add(slotName(slot));
				continue;
			}

			lines.add(Component.translatable("equipment.oldrepublic.mod.fitted", slotName(slot), mod.getHoverName()).withStyle(ChatFormatting.GRAY));
			Component effect = effect(mod.getItem(), Crafting.quality(mod).ordinal());

			if (effect != null) {
				lines.add(Component.literal(" ").append(effect).withStyle(ChatFormatting.BLUE));
			}
		}

		if (!open.isEmpty()) {
			MutableComponent list = Component.empty();

			for (int i = 0; i < open.size(); i++) {
				list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(open.get(i));
			}

			lines.add(Component.translatable("equipment.oldrepublic.mod.open", list).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static Component slotName(ModSlot slot) {
		return Component.translatable("equipment.oldrepublic.mod_slot." + slot.id());
	}

	/** What {@code mod} does at quality {@code q} (0 Standard to 2 Masterwork), or null for an unknown item. */
	private static @Nullable Component effect(Item mod, int q) {
		String key = "equipment.oldrepublic.mod.effect." + BuiltInRegistries.ITEM.getKey(mod).getPath();

		if (mod == M4Items.MOD_PLATING) {
			return Component.translatable(key, ArmourItem.signed(MODS.platingArmour), pct(MODS.platingBlasterPct, q));
		}

		if (mod == M4Items.MOD_WEAVE) {
			return Component.translatable(key, pct(MODS.weaveMeleePct, q));
		}

		if (mod == M4Items.MOD_ATTUNEMENT) {
			return Component.translatable(key, pct(MODS.attunementCostPct, q));
		}

		if (mod == M4Items.MOD_CONDUIT) {
			return Component.translatable(key, pct(MODS.conduitRegenPct, q));
		}

		if (mod == M4Items.MOD_STABILISER) {
			return Component.translatable(key, pct(MODS.stabiliserControlPct, q));
		}

		if (mod == M4Items.JET_PACK) {
			return Component.translatable(key, MODS.jetCharges, ArmourItem.number(MODS.jetRefillTicks / 20f));
		}

		if (mod == M4Items.MOD_FOCUSED_BARREL) {
			return Component.translatable(key, pct(MODS.focusedSpreadPct, q));
		}

		if (mod == M4Items.MOD_HEAVY_BARREL) {
			return Component.translatable(key, pct(MODS.heavyDamagePct, q), ArmourItem.signed(MODS.heavyRateTicks));
		}

		if (mod == M4Items.MOD_COOLING_CELL) {
			return Component.translatable(key, pct(MODS.coolingHeatPct, q));
		}

		if (mod == M4Items.MOD_OVERCHARGED_CELL) {
			return Component.translatable(key, pct(MODS.overchargedDamagePct, q), ArmourItem.percent(MODS.overchargedHeatPct / 100f));
		}

		if (mod == M4Items.MOD_KEEN_EDGE) {
			return Component.translatable(key, pct(MODS.keenDamagePct, q));
		}

		if (mod == M4Items.MOD_SHOCK_EDGE) {
			return Component.translatable(key, ArmourItem.number(ModNumbers.of(MODS.shockChancePct, q)), ArmourItem.number(MODS.shockTicks / 20f));
		}

		return legendaryEffect(mod, key);
	}

	/** What a legendary mod does (fixed numbers, whatever its quality), or null for any other item. */
	private static @Nullable Component legendaryEffect(Item mod, String key) {
		if (mod == LegendaryMods.CLAN_IRON_PLATING) {
			return Component.translatable(key, ArmourItem.signed(LEGENDARY.clanIronArmour), ArmourItem.percent(-LEGENDARY.clanIronBlasterPct / 100f));
		}

		if (mod == LegendaryMods.HYMNWEAVE_LINING) {
			return Component.translatable(key, ArmourItem.signed(LEGENDARY.hymnweaveEnergy), ArmourItem.percent(LEGENDARY.hymnweaveRegenPct / 100f));
		}

		if (mod == LegendaryMods.STORM_CELL) {
			return Component.translatable(key, ArmourItem.number(LEGENDARY.stormCellRange), ArmourItem.number(LEGENDARY.stormCellPct));
		}

		if (mod == LegendaryMods.SUNSTONE_EDGE) {
			return Component.translatable(key, ArmourItem.percent(LEGENDARY.sunstoneEdgePct / 100f), ArmourItem.number(LEGENDARY.sunstoneEdgeFireTicks / 20f));
		}

		return null;
	}

	private static String pct(float[] values, int q) {
		return ArmourItem.percent(ModNumbers.of(values, q) / 100f);
	}

	private GearTooltips() {
	}
}
