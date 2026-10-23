package com.oldrepublic.holocron;

import java.util.UUID;

import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * The three holocron items (spec 4.1): stack to 1, uncommon, never crafted. Each stack carries the
 * {@code oldrepublic:holocron} stamp naming the holocron, its owner and the owner's issue number.
 */
public final class HolocronItems {
	/** A cube of pale stone with gold seams. */
	public static final Item JEDI_HOLOCRON = ModRegistry.item("jedi_holocron", p -> new HolocronItem(p, HolocronKind.JEDI),
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** A pyramid of black glass with crimson seams. */
	public static final Item SITH_HOLOCRON = ModRegistry.item("sith_holocron", p -> new HolocronItem(p, HolocronKind.SITH),
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** A worn brass sphere with a turning ring. */
	public static final Item WAYFARER_HOLOCRON = ModRegistry.item("wayfarer_holocron", p -> new HolocronItem(p, HolocronKind.WAYFARER),
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	/** The item of a holocron kind. */
	public static Item of(HolocronKind kind) {
		return switch (kind) {
			case JEDI -> JEDI_HOLOCRON;
			case SITH -> SITH_HOLOCRON;
			case WAYFARER -> WAYFARER_HOLOCRON;
		};
	}

	/** One stamped holocron item of {@code holocronId} for {@code owner}, issue {@code serial}. */
	public static ItemStack stack(String holocronId, UUID owner, int serial) {
		ItemStack stack = new ItemStack(of(HolocronCatalog.get(holocronId).kind()));
		stack.set(ModComponents.HOLOCRON, new HolocronStamp(holocronId, owner, serial));
		return stack;
	}

	static void init() {
	}

	private HolocronItems() {
	}
}
