package com.oldrepublic.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.Ships;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Credits as items (spec MILESTONE_4.md 7.7): {@code credit_chit} is 1, {@code credit_bundle} is
 * 100. Payment reads the main inventory and hotbar and, when asked, the hold of the player's own
 * ship if its body is loaded within {@link #HOLD_RANGE} blocks.
 */
public final class Credits {
	public static final int BUNDLE = 100;
	/** How near the ship must be for its hold to pay (the {@code vendors.holdPaymentRange} default). */
	public static final double HOLD_RANGE = 32;

	/** The player's credits. */
	public static int count(ServerPlayer p, boolean includeShipHold) {
		long total = count(p.getInventory().getNonEquipmentItems());

		if (includeShipHold) {
			total += nearHold(p).map(r -> count(r.hold())).orElse(0L);
		}

		return (int) Math.min(Integer.MAX_VALUE, total);
	}

	/**
	 * Takes {@code amount} credits, all or nothing: chits first (inventory, then hold), then
	 * bundles, with exact change given back as chits and bundles.
	 *
	 * @return whether the player paid
	 */
	public static boolean take(ServerPlayer p, int amount, boolean includeShipHold) {
		if (amount <= 0) {
			return amount == 0;
		}

		if (count(p, includeShipHold) < amount) {
			return false;
		}

		Optional<ShipRecord> ship = includeShipHold ? nearHold(p) : Optional.empty();
		List<ItemStack> hold = ship.map(r -> copyAll(r.hold())).orElse(new ArrayList<>());
		List<ItemStack> inventory = p.getInventory().getNonEquipmentItems();

		int left = amount;
		left = remove(inventory, M4Items.CREDIT_CHIT, left);
		left = remove(hold, M4Items.CREDIT_CHIT, left);
		int bundles = (left + BUNDLE - 1) / BUNDLE;
		int stillBundles = remove(inventory, M4Items.CREDIT_BUNDLE, bundles);
		stillBundles = remove(hold, M4Items.CREDIT_BUNDLE, stillBundles);
		int change = bundles * BUNDLE - left;

		p.getInventory().setChanged();

		if (ship.isPresent()) {
			ShipRegistry.put(p.level().getServer(), ship.get().withHold(hold));
		}

		if (change > 0) {
			give(p, change);
		}

		return stillBundles == 0;
	}

	/** Gives {@code amount} credits as bundles, then chits; what does not fit drops for the player only. */
	public static void give(ServerPlayer p, int amount) {
		if (amount <= 0) {
			return;
		}

		int bundles = amount / BUNDLE;
		int chits = amount % BUNDLE;

		while (bundles > 0) {
			int n = Math.min(bundles, new ItemStack(M4Items.CREDIT_BUNDLE).getMaxStackSize());
			Rewards.give(p, new ItemStack(M4Items.CREDIT_BUNDLE, n));
			bundles -= n;
		}

		if (chits > 0) {
			Rewards.give(p, new ItemStack(M4Items.CREDIT_CHIT, chits));
		}
	}

	/** The credit value of one {@code stack}. */
	public static long value(ItemStack stack) {
		if (stack.is(M4Items.CREDIT_CHIT)) {
			return stack.getCount();
		}

		return stack.is(M4Items.CREDIT_BUNDLE) ? (long) stack.getCount() * BUNDLE : 0L;
	}

	private static long count(List<ItemStack> stacks) {
		long total = 0;

		for (ItemStack stack : stacks) {
			total += value(stack);
		}

		return total;
	}

	/** Removes up to {@code n} of {@code item} from {@code stacks} in place; returns how many are still owed. */
	private static int remove(List<ItemStack> stacks, Item item, int n) {
		for (int i = 0; i < stacks.size() && n > 0; i++) {
			ItemStack stack = stacks.get(i);

			if (stack.is(item)) {
				int taken = Math.min(n, stack.getCount());
				stack.shrink(taken);
				n -= taken;
			}
		}

		return n;
	}

	private static List<ItemStack> copyAll(List<ItemStack> stacks) {
		List<ItemStack> out = new ArrayList<>(stacks.size());

		for (ItemStack stack : stacks) {
			out.add(stack.copy());
		}

		return out;
	}

	/** The player's ship record when its body is loaded in their level within {@link #HOLD_RANGE}. */
	private static Optional<ShipRecord> nearHold(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		Entity body = Ships.body(server, p.getUUID());

		if (body == null || body.level() != p.level() || body.distanceToSqr(p) > HOLD_RANGE * HOLD_RANGE) {
			return Optional.empty();
		}

		return ShipRegistry.of(server, p.getUUID());
	}

	private Credits() {
	}
}
