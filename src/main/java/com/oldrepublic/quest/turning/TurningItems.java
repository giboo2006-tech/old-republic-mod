package com.oldrepublic.quest.turning;

import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** The turning's one item (spec MILESTONE_5.md 8.3, 18): the Watch holocron Ansa lends a Jedi on the way to the Fall. */
public final class TurningItems {
	/** {@code watch_holocron}: a keepsake that only the Fall's delivery may hold. */
	public static final WatchHolocronItem WATCH_HOLOCRON = ModRegistry.item("watch_holocron", WatchHolocronItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());

	/** Loads this class, registering the item. Called from {@link TurningFeature#init()}. */
	static void init() {
	}

	private TurningItems() {
	}
}
