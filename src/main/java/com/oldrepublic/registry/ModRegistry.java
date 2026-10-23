package com.oldrepublic.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registration helpers shared by all features, plus the mod's creative tab. Features register in
 * their own classes (e.g. {@code saber.SaberItems}) through these helpers; items are shown in the
 * creative tab in registration order.
 */
public final class ModRegistry {
	public static final ResourceKey<net.minecraft.world.item.CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, OldRepublicMod.id("main"));
	private static final List<Supplier<ItemStack>> TAB_ENTRIES = new ArrayList<>();
	private static Supplier<ItemStack> tabIcon = () -> new ItemStack(Items.AMETHYST_SHARD);

	public static <T extends Item> T item(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, OldRepublicMod.id(name));
		T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		TAB_ENTRIES.add(() -> new ItemStack(item));
		return item;
	}

	public static Item item(String name, Item.Properties properties) {
		return item(name, Item::new, properties);
	}

	/** Registers a block and a matching block item. */
	public static <T extends Block> T block(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
		T block = blockOnly(name, factory, properties);
		item(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	/** Registers a block without an item. */
	public static <T extends Block> T blockOnly(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, OldRepublicMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	/** Adds an extra stack (e.g. a pre-built lightsaber) to the creative tab. */
	public static void tabEntry(Supplier<ItemStack> stack) {
		TAB_ENTRIES.add(stack);
	}

	public static void tabIcon(Supplier<ItemStack> icon) {
		tabIcon = icon;
	}

	/** Called last in mod init, after every feature has registered. */
	public static void registerTab() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.oldrepublic.main"))
				.icon(() -> tabIcon.get())
				.displayItems((context, entries) -> TAB_ENTRIES.forEach(s -> entries.accept(s.get())))
				.build());
	}

	private ModRegistry() {
	}
}
