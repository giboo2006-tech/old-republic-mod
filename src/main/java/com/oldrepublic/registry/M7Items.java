package com.oldrepublic.registry;

import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.melee.VibroWeaponItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Milestone 7's shared blocks and items (spec MILESTONE_7.md 12.1), registered up front so every
 * worker's recipes, loot, stock, schematics and tests resolve them. The ores' drops, worldgen and art
 * are OR's; the weapons' numbers and behaviour WM's (melee) and WB's (blasters), through their
 * classes and the kinds appended for them; the mods' effects WM's (edges) and WB's (barrel, cell).
 */
public final class M7Items {
	// Ores and materials (OR, 4.1 to 4.3)
	/** Starfall iron ore in stone: an iron pickaxe, drops raw starfall. */
	public static final Block STARFALL_ORE = ModRegistry.block("starfall_ore", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.5f, 3f).requiresCorrectToolForDrops().sound(SoundType.STONE));
	/** Starfall iron ore in deepslate. */
	public static final Block DEEPSLATE_STARFALL_ORE = ModRegistry.block("deepslate_starfall_ore", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(5f, 3f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
	/** The meteorite's charred shell: any pickaxe, drops itself. */
	public static final Block STARFALL_ROCK = ModRegistry.block("starfall_rock", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.5f, 4f).requiresCorrectToolForDrops().sound(SoundType.BASALT));
	/** Tyrvann's clan iron ore, basalt with blued seams: a diamond pickaxe, drops clan iron. */
	public static final Block CLAN_IRON_ORE = ModRegistry.block("clan_iron_ore", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(4f, 4f).requiresCorrectToolForDrops().sound(SoundType.BASALT));
	public static final Item RAW_STARFALL = ModRegistry.item("raw_starfall", new Item.Properties());
	public static final Item STARFALL_INGOT = ModRegistry.item("starfall_ingot", new Item.Properties());
	/** The alloy of hymnstone, clan iron, sunstone and starfall iron (4.3). */
	public static final Item HYMNSTEEL_INGOT = ModRegistry.item("hymnsteel_ingot", new Item.Properties().rarity(Rarity.RARE));

	// Weapons (WM and WB, 9.1): each class completes the properties from its registration defaults.
	public static final VibroWeaponItem VIBRO_AXE = ModRegistry.item("vibro_axe",
			p -> new VibroWeaponItem(VibroWeaponItem.Kind.AXE, p), new Item.Properties().stacksTo(1));
	public static final VibroWeaponItem FORCE_PIKE = ModRegistry.item("force_pike",
			p -> new VibroWeaponItem(VibroWeaponItem.Kind.PIKE, p), new Item.Properties().stacksTo(1));
	public static final BlasterItem SCATTER_BLASTER = ModRegistry.item("scatter_blaster",
			p -> new BlasterItem(BlasterItem.Kind.SCATTER, p), new Item.Properties().stacksTo(1));
	public static final BlasterItem BLASTER_CARBINE = ModRegistry.item("blaster_carbine",
			p -> new BlasterItem(BlasterItem.Kind.CARBINE, p), new Item.Properties().stacksTo(1));

	// Material mods (9.2); Mods.slotOf knows them.
	public static final Item MOD_STARFALL_EDGE = mod("mod_starfall_edge");
	public static final Item MOD_CLAN_IRON_EDGE = mod("mod_clan_iron_edge");
	public static final Item MOD_AURELITE_BARREL = mod("mod_aurelite_barrel");
	public static final Item MOD_DUSKGLASS_CELL = mod("mod_duskglass_cell");

	private static Item mod(String name) {
		return ModRegistry.item(name, new Item.Properties().stacksTo(16));
	}

	/** Loads the class so the entries above register; called once from {@link M7Contracts#init}. */
	static void init() {
	}

	private M7Items() {
	}
}
