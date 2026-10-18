package com.oldrepublic.equipment.mod;

import java.util.List;

import com.oldrepublic.registry.M4Components;

import net.minecraft.world.item.ItemStack;

/**
 * Which gear has which mod slots, and fitting (spec MILESTONE_4.md 6.3). Signatures are frozen;
 * W4 writes {@link #slotsOf} and {@link #fits}. Phase 0's stub gives every stack one armour slot
 * and lets any non-empty stack fit it, so the bench's Modify tab can be built in parallel.
 */
public final class Mods {
	/** The slots {@code gear} has, in display order; empty when it takes no mods. */
	public static List<ModSlot> slotsOf(ItemStack gear) {
		return gear.isEmpty() ? List.of() : List.of(ModSlot.ARMOUR);
	}

	/** Whether {@code mod} may go into {@code slot} of {@code gear} (the slot may be taken). */
	public static boolean fits(ItemStack gear, ModSlot slot, ItemStack mod) {
		return !mod.isEmpty() && slotsOf(gear).contains(slot);
	}

	/** The mods fitted to {@code gear}. */
	public static FittedMods fitted(ItemStack gear) {
		return gear.getOrDefault(M4Components.MODS, FittedMods.NONE);
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
