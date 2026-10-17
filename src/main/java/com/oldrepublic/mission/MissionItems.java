package com.oldrepublic.mission;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.registry.ModRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The two story mission items (spec section 5.4): the Watch Archive record recovered in "Credits
 * from the Core" and the fence's book of names taken in "Names". Both are ordinary single items;
 * a lost one is issued again at its mission site while the mission is active (12.2).
 */
public final class MissionItems {
	/** A sealed Watch Archive ledger cylinder, stolen with the Archive (R2). */
	public static final Item ARCHIVE_RECORD = ModRegistry.item("archive_record", new Item.Properties().stacksTo(1));
	/** Dovan Lisk's book of everyone who handled the crystals (I1). */
	public static final Item FENCE_BOOK = ModRegistry.item("fence_book", new Item.Properties().stacksTo(1));

	/** The item a mission definition names, or null for none or an unknown id. */
	public static @Nullable Item byId(@Nullable Identifier id) {
		if (id == null) {
			return null;
		}

		// API-CHECK: BuiltInRegistries.ITEM.getValue(Identifier) returns the default (air) for a missing id (as gametest/PlaythroughTest).
		Item item = BuiltInRegistries.ITEM.getValue(id);
		return item == Items.AIR ? null : item;
	}

	/** Whether the player carries at least one {@code item}. */
	public static boolean carries(ServerPlayer p, Item item) {
		return p.getInventory().contains(stack -> !stack.isEmpty() && stack.is(item));
	}

	/** Removes one {@code item} from the player's inventory; false when they carry none. */
	public static boolean take(ServerPlayer p, Item item) {
		Inventory inventory = p.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty() && stack.is(item)) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}

		return false;
	}

	/** Loads the class so the items register; called from {@link MissionFeature#init()}. */
	static void init() {
	}

	private MissionItems() {
	}
}
