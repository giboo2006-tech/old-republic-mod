package com.oldrepublic.quest.turning;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.vendor.KeepsakeItem;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The Watch holocron (spec MILESTONE_5.md 8.3): Keeper Ansa lets a Jedi on the way to the Fall "study" it, and the Jedi
 * carries it to Overseer Kaddor. It exists only in the hands of a player whose Fall is carrying it: a copy anywhere else
 * in a player's inventory is gone within a second, and a dropped one is gone at once ("Ansa takes it back").
 */
public class WatchHolocronItem extends KeepsakeItem {
	/** How often (ticks) a carried copy checks that its holder may still hold it. */
	private static final int CHECK_TICKS = 20;

	public WatchHolocronItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		if (entity instanceof ServerPlayer p && level.getGameTime() % CHECK_TICKS == 0 && !FallTask.carrying(p)) {
			stack.setCount(0);
		}
	}

	/** Whether {@code stack} is a Watch holocron. */
	public static boolean is(ItemStack stack) {
		return !stack.isEmpty() && stack.is(TurningItems.WATCH_HOLOCRON);
	}

	/** Whether the player has a Watch holocron in their inventory (the hands and the armour slots included). */
	public static boolean carried(ServerPlayer p) {
		Container inventory = p.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (is(inventory.getItem(i))) {
				return true;
			}
		}

		return false;
	}

	/** Removes every Watch holocron from the player's inventory and ender chest; returns how many stacks went. */
	public static int removeAll(ServerPlayer p) {
		return removeFrom(p.getInventory()) + removeFrom(p.getEnderChestInventory());
	}

	private static int removeFrom(Container container) {
		int removed = 0;

		for (int i = 0; i < container.getContainerSize(); i++) {
			if (is(container.getItem(i))) {
				container.setItem(i, ItemStack.EMPTY);
				removed++;
			}
		}

		if (removed > 0) {
			container.setChanged();
		}

		return removed;
	}

	/** A holocron dropped (thrown, or spilled by a death) or loaded with its chunk leaves the world on the next tick. */
	static void onEntityLoad(Entity entity) {
		if (entity instanceof ItemEntity item && is(item.getItem())) {
			TurningService.discardLater(item);
		}
	}
}
