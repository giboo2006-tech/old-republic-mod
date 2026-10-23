package com.oldrepublic.space;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.Ships;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Space's reads and writes of a ship's hold (spec MILESTONE_5.md 5.3, 5.5, 5.9): the missile rack
 * fires from it, the kajidic's sealed cargo rides in it, and kill credits that do not fit the
 * pilot's pockets go into it (nothing may drop in space). Writes go through {@code Ships.update}, so
 * an open ship screen reloads them. The list helpers work on copies and never change a record's own
 * stacks.
 */
final class ShipHolds {
	/** How many of {@code item} {@code hold} holds. */
	static int count(List<ItemStack> hold, Item item) {
		int total = 0;

		for (ItemStack stack : hold) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	/** A copy of {@code hold} with up to {@code n} of {@code item} taken out, from the last stacks first. */
	static List<ItemStack> without(List<ItemStack> hold, Item item, int n) {
		List<ItemStack> out = copy(hold);

		for (int i = out.size() - 1; i >= 0 && n > 0; i--) {
			ItemStack stack = out.get(i);

			if (stack.is(item)) {
				int taken = Math.min(n, stack.getCount());
				stack.shrink(taken);
				n -= taken;
			}
		}

		return out;
	}

	/**
	 * Puts as much of {@code stack} as fits into {@code hold} (a mutable list): onto matching stacks
	 * first, then into empty slots below {@code size}. Returns what did not fit; {@code stack} is not
	 * changed.
	 */
	static ItemStack add(List<ItemStack> hold, ItemStack stack, int size) {
		ItemStack rest = stack.copy();

		for (int i = 0; i < hold.size() && i < size && !rest.isEmpty(); i++) {
			ItemStack slot = hold.get(i);

			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest)) {
				int moved = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());

				if (moved > 0) {
					slot.grow(moved);
					rest.shrink(moved);
				}
			}
		}

		for (int i = 0; i < size && !rest.isEmpty(); i++) {
			while (hold.size() <= i) {
				hold.add(ItemStack.EMPTY);
			}

			if (hold.get(i).isEmpty()) {
				int moved = Math.min(rest.getCount(), rest.getMaxStackSize());
				hold.set(i, rest.copyWithCount(moved));
				rest.shrink(moved);
			}
		}

		return rest;
	}

	/** A mutable deep copy. */
	static List<ItemStack> copy(List<ItemStack> hold) {
		List<ItemStack> out = new ArrayList<>(hold.size());

		for (ItemStack stack : hold) {
			out.add(stack.copy());
		}

		return out;
	}

	/** Puts {@code stack} into {@code owner}'s hold; returns what did not fit (all of it without a ship). */
	static ItemStack stow(MinecraftServer server, UUID owner, ItemStack stack) {
		ShipRecord record = ShipRegistry.of(server, owner).orElse(null);

		if (record == null || stack.isEmpty()) {
			return stack.copy();
		}

		List<ItemStack> hold = copy(record.hold());
		ItemStack rest = add(hold, stack, ShipRules.holdSize(record, ShipBalance.get()));

		if (rest.getCount() < stack.getCount()) {
			Ships.update(server, owner, r -> r.withHold(hold));
		}

		return rest;
	}

	/** Takes up to {@code n} of {@code item} out of {@code owner}'s hold; returns how many were taken. */
	static int take(MinecraftServer server, UUID owner, Item item, int n) {
		ShipRecord record = ShipRegistry.of(server, owner).orElse(null);

		if (record == null || n <= 0) {
			return 0;
		}

		int taken = Math.min(n, count(record.hold(), item));

		if (taken > 0) {
			Ships.update(server, owner, r -> r.withHold(without(r.hold(), item, taken)));
		}

		return taken;
	}

	/** How many of {@code item} {@code owner}'s hold holds (0 without a ship). */
	static int count(MinecraftServer server, UUID owner, Item item) {
		return ShipRegistry.of(server, owner).map(r -> count(r.hold(), item)).orElse(0);
	}

	private ShipHolds() {
	}
}
