package com.oldrepublic.crafting.bench;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.registry.M4Components;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Finding and taking a schematic's inputs (7.1): the bench's parts tray first, then the player's
 * main inventory and hotbar. Worn, modded and named items never count, so a craft cannot eat a
 * fitted rifle or a named drop. Taking is all or nothing: {@link #plan} reserves every input on
 * the live stacks before anything is removed. Works on either side (the screen shows have/need).
 */
public final class Ingredients {
	/** The live stacks that may feed a craft, tray first. */
	public static List<ItemStack> sources(@Nullable Container tray, Player player) {
		List<ItemStack> out = new ArrayList<>();

		if (tray != null) {
			for (int i = 0; i < tray.getContainerSize(); i++) {
				add(out, tray.getItem(i));
			}
		}

		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			add(out, stack);
		}

		return out;
	}

	private static void add(List<ItemStack> out, ItemStack stack) {
		if (!stack.isEmpty() && !stack.isDamaged() && !stack.has(M4Components.MODS) && !stack.has(M4Components.NAMED)) {
			out.add(stack);
		}
	}

	/** How many items in {@code sources} match {@code input}. */
	public static int count(List<ItemStack> sources, Schematic.Input input) {
		int n = 0;

		for (ItemStack stack : sources) {
			if (input.matches(stack)) {
				n += stack.getCount();
			}
		}

		return n;
	}

	/**
	 * Reserves every input in order (an item already reserved for an earlier input is not counted
	 * again). Returns how many to take from each stack, or null when an input is short.
	 */
	public static @Nullable Map<ItemStack, Integer> plan(List<ItemStack> sources, List<Schematic.Input> inputs) {
		Map<ItemStack, Integer> taken = new IdentityHashMap<>();

		for (Schematic.Input input : inputs) {
			int left = input.count();

			for (ItemStack stack : sources) {
				if (left <= 0) {
					break;
				}

				if (!input.matches(stack)) {
					continue;
				}

				int already = taken.getOrDefault(stack, 0);
				int n = Math.min(left, stack.getCount() - already);

				if (n > 0) {
					taken.put(stack, already + n);
					left -= n;
				}
			}

			if (left > 0) {
				return null;
			}
		}

		return taken;
	}

	/** Whether every input is available. */
	public static boolean has(List<ItemStack> sources, List<Schematic.Input> inputs) {
		return plan(sources, inputs) != null;
	}

	/**
	 * Takes every input (all or nothing) from the tray and the player's inventory.
	 *
	 * @return whether the inputs were taken
	 */
	public static boolean take(@Nullable Container tray, Player player, List<Schematic.Input> inputs) {
		Map<ItemStack, Integer> plan = plan(sources(tray, player), inputs);

		if (plan == null) {
			return false;
		}

		plan.forEach(ItemStack::shrink);

		if (tray != null) {
			tray.setChanged();
		}

		player.getInventory().setChanged();
		return true;
	}

	private Ingredients() {
	}
}
