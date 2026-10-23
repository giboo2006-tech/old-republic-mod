package com.oldrepublic.client.equipment;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/**
 * The Milestone 7 blasters' client side (docs/MILESTONE_7.md 9.1 and 9.3): the scatter blaster's and the
 * carbine's shots and anything else they need registered on the client. A Phase 0 stub that WB fills; called
 * once from {@link EquipmentClient#init}.
 */
public final class BlasterM7Client {
	static void init() {
	}

	/**
	 * The tooltip line of a Milestone 7 mod (the Aurelite Barrel and the Duskglass Cell): the translation {@code key} with
	 * its numbers, or null for any other item. Called by {@link GearTooltips} for a mod and for a fitted one.
	 */
	static @Nullable Component modEffect(Item mod, String key) {
		return null;
	}

	private BlasterM7Client() {
	}
}
