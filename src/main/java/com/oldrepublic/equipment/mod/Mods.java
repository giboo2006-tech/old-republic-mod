package com.oldrepublic.equipment.mod;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.equipment.ArmourItem;
import com.oldrepublic.equipment.GearItems;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M4Items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Which gear has which mod slots, and fitting (spec MILESTONE_4.md 6.3). Every era armour piece (M3
 * and M4) has one {@link ModSlot#ARMOUR} slot, and chest pieces also the {@link ModSlot#PACK} slot
 * that only the jet pack fits; blasters have {@link ModSlot#BARREL} and {@link ModSlot#CELL}
 * (Kaast's Longshot only the cell: its heavy barrel is fixed); vibroweapons and the electrostaff
 * one {@link ModSlot#EDGE}. Each mod item fits exactly one slot ({@link #slotOf}), the legendary
 * mods of Milestone 5 ({@link LegendaryMods}) too. Fitted mods live in the {@code oldrepublic:mods}
 * component; a mod's own quality component sets its strength. Signatures of the Phase 0 methods are
 * frozen.
 */
public final class Mods {
	private static final List<ModSlot> ARMOUR = List.of(ModSlot.ARMOUR);
	private static final List<ModSlot> CHEST = List.of(ModSlot.ARMOUR, ModSlot.PACK);
	private static final List<ModSlot> BLASTER = List.of(ModSlot.BARREL, ModSlot.CELL);
	private static final List<ModSlot> FIXED_BARREL_BLASTER = List.of(ModSlot.CELL);
	private static final List<ModSlot> MELEE = List.of(ModSlot.EDGE);

	/** The slots {@code gear} has, in display order; empty when it takes no mods. */
	public static List<ModSlot> slotsOf(ItemStack gear) {
		if (gear.isEmpty()) {
			return List.of();
		}

		Item item = gear.getItem();

		if (item instanceof ArmourItem armour) {
			return armour.type() == ArmorType.CHESTPLATE ? CHEST : ARMOUR;
		}

		if (item instanceof BlasterItem) {
			return item == GearItems.KAAST_LONGSHOT ? FIXED_BARREL_BLASTER : BLASTER;
		}

		if (item instanceof VibroWeaponItem) {
			return MELEE;
		}

		return List.of();
	}

	/** The slot a mod stack fits, or null when {@code mod} is not a mod. */
	public static @Nullable ModSlot slotOf(ItemStack mod) {
		return slotOf(mod.getItem());
	}

	/** The slot a mod item fits, or null when {@code item} is not a mod. */
	public static @Nullable ModSlot slotOf(Item item) {
		if (item == M4Items.MOD_PLATING || item == M4Items.MOD_WEAVE || item == M4Items.MOD_ATTUNEMENT
				|| item == M4Items.MOD_CONDUIT || item == M4Items.MOD_STABILISER
				|| item == LegendaryMods.CLAN_IRON_PLATING || item == LegendaryMods.HYMNWEAVE_LINING) {
			return ModSlot.ARMOUR;
		}

		if (item == M4Items.JET_PACK) {
			return ModSlot.PACK;
		}

		if (item == M4Items.MOD_FOCUSED_BARREL || item == M4Items.MOD_HEAVY_BARREL) {
			return ModSlot.BARREL;
		}

		if (item == M4Items.MOD_COOLING_CELL || item == M4Items.MOD_OVERCHARGED_CELL || item == LegendaryMods.STORM_CELL) {
			return ModSlot.CELL;
		}

		if (item == M4Items.MOD_KEEN_EDGE || item == M4Items.MOD_SHOCK_EDGE || item == LegendaryMods.SUNSTONE_EDGE) {
			return ModSlot.EDGE;
		}

		return null;
	}

	/** Whether {@code mod} may go into {@code slot} of {@code gear} (the slot may be taken). */
	public static boolean fits(ItemStack gear, ModSlot slot, ItemStack mod) {
		return !mod.isEmpty() && slotOf(mod) == slot && slotsOf(gear).contains(slot);
	}

	/** The mods fitted to {@code gear}. */
	public static FittedMods fitted(ItemStack gear) {
		return gear.getOrDefault(M4Components.MODS, FittedMods.NONE);
	}

	/**
	 * The quality ordinal (0 Standard, 1 Fine, 2 Masterwork, 3 Legendary) of the {@code mod} fitted to
	 * {@code gear}, or -1 when it is not fitted. Kaast's Longshot counts its fixed heavy barrel at the
	 * rifle's own quality.
	 */
	public static int fittedQuality(ItemStack gear, Item mod) {
		if (mod == M4Items.MOD_HEAVY_BARREL && gear.getItem() == GearItems.KAAST_LONGSHOT) {
			return Crafting.quality(gear).ordinal();
		}

		ModSlot slot = slotOf(mod);
		FittedMods fitted = gear.get(M4Components.MODS);

		if (slot == null || fitted == null) {
			return -1;
		}

		ItemStack stack = fitted.mods().get(slot);
		return stack != null && stack.is(mod) ? Crafting.quality(stack).ordinal() : -1;
	}

	/** Whether {@code gear} has {@code mod} fitted (or built in). */
	public static boolean has(ItemStack gear, Item mod) {
		return fittedQuality(gear, mod) >= 0;
	}

	/**
	 * Fits one of {@code mod} into {@code slot} of {@code gear} when {@link #fits} allows it,
	 * replacing nothing: a taken slot refuses. Shrinks {@code mod} by one on success.
	 *
	 * @return {@code gear}, changed or not
	 */
	public static ItemStack fit(ItemStack gear, ModSlot slot, ItemStack mod) {
		FittedMods fitted = fitted(gear);

		if (!fits(gear, slot, mod) || !fitted.get(slot).isEmpty()) {
			return gear;
		}

		gear.set(M4Components.MODS, fitted.with(slot, mod.copyWithCount(1)));
		mod.shrink(1);
		return gear;
	}

	/** Takes the mod out of {@code slot} of {@code gear}; returns it, or an empty stack. */
	public static ItemStack remove(ItemStack gear, ModSlot slot) {
		FittedMods fitted = fitted(gear);
		ItemStack removed = fitted.get(slot);

		if (!removed.isEmpty()) {
			FittedMods next = fitted.with(slot, ItemStack.EMPTY);

			if (next.mods().isEmpty()) {
				gear.remove(M4Components.MODS);
			} else {
				gear.set(M4Components.MODS, next);
			}
		}

		return removed;
	}

	private Mods() {
	}
}
