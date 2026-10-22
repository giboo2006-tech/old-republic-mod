package com.oldrepublic.world.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The build kit of the structures overhaul (docs/MILESTONE_6.md section 4): 64 plain building blocks
 * (plating, masonry, lights, machines, cargo, cloth and ground decals) with block items, in
 * creative-tab order. None has a block entity or behaviour of its own; they exist so the worlds'
 * structures look like the Old Republic. Metal breaks at 3.0, masonry at 1.8 (both need a pickaxe),
 * cloth at 0.8, decals at 0.1.
 */
public final class BuildBlocks {
	private static final List<Block> ALL = new ArrayList<>();

	// Durasteel and faction plating
	public static final Block DURASTEEL_PLATING = block("durasteel_plating", metal(MapColor.METAL));
	public static final StairBlock DURASTEEL_PLATING_STAIRS = stairs("durasteel_plating_stairs", DURASTEEL_PLATING, metal(MapColor.METAL));
	public static final SlabBlock DURASTEEL_PLATING_SLAB = slab("durasteel_plating_slab", metal(MapColor.METAL));
	public static final Block DURASTEEL_DECK = block("durasteel_deck", metal(MapColor.COLOR_GRAY));
	public static final SlabBlock DURASTEEL_DECK_SLAB = slab("durasteel_deck_slab", metal(MapColor.COLOR_GRAY));
	public static final TransparentBlock DURASTEEL_GRATE = register("durasteel_grate", TransparentBlock::new, metal(MapColor.COLOR_GRAY)
			.noOcclusion()
			.isValidSpawn((state, level, pos, type) -> false)
			.isRedstoneConductor((state, level, pos) -> false)
			.isSuffocating((state, level, pos) -> false)
			.isViewBlocking((state, level, pos) -> false));
	public static final RotatedPillarBlock DURASTEEL_BEAM = pillar("durasteel_beam", metal(MapColor.METAL));
	public static final RotatedPillarBlock DURASTEEL_PIPES = pillar("durasteel_pipes", metal(MapColor.METAL));
	public static final Block DURASTEEL_VENT = block("durasteel_vent", metal(MapColor.COLOR_GRAY));
	public static final Block RUSTED_PLATING = block("rusted_plating", metal(MapColor.TERRACOTTA_ORANGE));
	public static final Block REPUBLIC_PLATING = block("republic_plating", metal(MapColor.SNOW));
	public static final StairBlock REPUBLIC_PLATING_STAIRS = stairs("republic_plating_stairs", REPUBLIC_PLATING, metal(MapColor.SNOW));
	public static final SlabBlock REPUBLIC_PLATING_SLAB = slab("republic_plating_slab", metal(MapColor.SNOW));
	public static final Block IMPERIAL_PLATING = block("imperial_plating", metal(MapColor.COLOR_BLACK));
	public static final StairBlock IMPERIAL_PLATING_STAIRS = stairs("imperial_plating_stairs", IMPERIAL_PLATING, metal(MapColor.COLOR_BLACK));
	public static final SlabBlock IMPERIAL_PLATING_SLAB = slab("imperial_plating_slab", metal(MapColor.COLOR_BLACK));

	// Lights, machines and cargo
	public static final Block LIGHT_PANEL = block("light_panel", metal(MapColor.SNOW).lightLevel(state -> 14));
	public static final Block LIGHT_PANEL_AMBER = block("light_panel_amber", metal(MapColor.COLOR_ORANGE).lightLevel(state -> 13));
	public static final Block LIGHT_PANEL_RED = block("light_panel_red", metal(MapColor.COLOR_RED).lightLevel(state -> 9));
	public static final FacingBlock CONSOLE = register("console", FacingBlock::new, metal(MapColor.COLOR_GRAY).lightLevel(state -> 6));
	public static final FacingBlock HOLO_SCREEN = register("holo_screen", FacingBlock::new, metal(MapColor.COLOR_LIGHT_BLUE).lightLevel(state -> 9));
	public static final Block CARGO_CRATE = block("cargo_crate", metal(MapColor.COLOR_GRAY));
	public static final Block CARGO_CRATE_REPUBLIC = block("cargo_crate_republic", metal(MapColor.SNOW));
	public static final Block CARGO_CRATE_IMPERIAL = block("cargo_crate_imperial", metal(MapColor.COLOR_BLACK));

	// Jedi masonry
	public static final Block RUIN_BRICKS = block("ruin_bricks", masonry(MapColor.SAND, SoundType.STONE));
	public static final StairBlock RUIN_BRICK_STAIRS = stairs("ruin_brick_stairs", RUIN_BRICKS, masonry(MapColor.SAND, SoundType.STONE));
	public static final SlabBlock RUIN_BRICK_SLAB = slab("ruin_brick_slab", masonry(MapColor.SAND, SoundType.STONE));
	public static final WallBlock RUIN_BRICK_WALL = wall("ruin_brick_wall", masonry(MapColor.SAND, SoundType.STONE));
	public static final RotatedPillarBlock RUIN_PILLAR = pillar("ruin_pillar", masonry(MapColor.SAND, SoundType.STONE));

	// Sith masonry
	public static final Block TOMB_BRICKS = block("tomb_bricks", masonry(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE));
	public static final StairBlock TOMB_BRICK_STAIRS = stairs("tomb_brick_stairs", TOMB_BRICKS, masonry(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE));
	public static final SlabBlock TOMB_BRICK_SLAB = slab("tomb_brick_slab", masonry(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE));
	public static final WallBlock TOMB_BRICK_WALL = wall("tomb_brick_wall", masonry(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE));
	public static final RotatedPillarBlock TOMB_PILLAR = pillar("tomb_pillar", masonry(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE));

	// Selvaren's Watch stone
	public static final Block WATCH_STONE = block("watch_stone", masonry(MapColor.STONE, SoundType.STONE));
	public static final StairBlock WATCH_STONE_STAIRS = stairs("watch_stone_stairs", WATCH_STONE, masonry(MapColor.STONE, SoundType.STONE));
	public static final SlabBlock WATCH_STONE_SLAB = slab("watch_stone_slab", masonry(MapColor.STONE, SoundType.STONE));
	public static final WallBlock WATCH_STONE_WALL = wall("watch_stone_wall", masonry(MapColor.STONE, SoundType.STONE));

	// Varkhast's ash brick
	public static final Block ASH_BRICKS = block("ash_bricks", masonry(MapColor.COLOR_GRAY, SoundType.NETHER_BRICKS));
	public static final StairBlock ASH_BRICK_STAIRS = stairs("ash_brick_stairs", ASH_BRICKS, masonry(MapColor.COLOR_GRAY, SoundType.NETHER_BRICKS));
	public static final SlabBlock ASH_BRICK_SLAB = slab("ash_brick_slab", masonry(MapColor.COLOR_GRAY, SoundType.NETHER_BRICKS));
	public static final WallBlock ASH_BRICK_WALL = wall("ash_brick_wall", masonry(MapColor.COLOR_GRAY, SoundType.NETHER_BRICKS));

	// Ghessar's adobe and salt
	public static final Block ADOBE = block("adobe", masonry(MapColor.SAND, SoundType.MUD_BRICKS));
	public static final StairBlock ADOBE_STAIRS = stairs("adobe_stairs", ADOBE, masonry(MapColor.SAND, SoundType.MUD_BRICKS));
	public static final SlabBlock ADOBE_SLAB = slab("adobe_slab", masonry(MapColor.SAND, SoundType.MUD_BRICKS));
	public static final WallBlock ADOBE_WALL = wall("adobe_wall", masonry(MapColor.SAND, SoundType.MUD_BRICKS));
	public static final Block ADOBE_BRICKS = block("adobe_bricks", masonry(MapColor.DIRT, SoundType.MUD_BRICKS));
	public static final Block SALT_BRICKS = block("salt_bricks", masonry(MapColor.SNOW, SoundType.CALCITE));
	public static final SlabBlock SALT_BRICK_SLAB = slab("salt_brick_slab", masonry(MapColor.SNOW, SoundType.CALCITE));

	// Tyrvann's stormstone
	public static final Block STORMSTONE = block("stormstone", masonry(MapColor.DEEPSLATE, SoundType.BASALT));
	public static final Block STORMSTONE_BRICKS = block("stormstone_bricks", masonry(MapColor.DEEPSLATE, SoundType.BASALT));
	public static final StairBlock STORMSTONE_BRICK_STAIRS = stairs("stormstone_brick_stairs", STORMSTONE_BRICKS, masonry(MapColor.DEEPSLATE, SoundType.BASALT));
	public static final SlabBlock STORMSTONE_BRICK_SLAB = slab("stormstone_brick_slab", masonry(MapColor.DEEPSLATE, SoundType.BASALT));
	public static final WallBlock STORMSTONE_BRICK_WALL = wall("stormstone_brick_wall", masonry(MapColor.DEEPSLATE, SoundType.BASALT));

	// Republic interior tiles
	public static final Block PALE_TILES = block("pale_tiles", masonry(MapColor.QUARTZ, SoundType.STONE));
	public static final StairBlock PALE_TILE_STAIRS = stairs("pale_tile_stairs", PALE_TILES, masonry(MapColor.QUARTZ, SoundType.STONE));
	public static final SlabBlock PALE_TILE_SLAB = slab("pale_tile_slab", masonry(MapColor.QUARTZ, SoundType.STONE));

	// Cloth
	public static final Block CANVAS = block("canvas", cloth(MapColor.SAND));
	public static final StairBlock CANVAS_STAIRS = stairs("canvas_stairs", CANVAS, cloth(MapColor.SAND));
	public static final SlabBlock CANVAS_SLAB = slab("canvas_slab", cloth(MapColor.SAND));
	public static final Block CANVAS_NAVY = block("canvas_navy", cloth(MapColor.COLOR_BLUE));
	public static final Block CANVAS_CRIMSON = block("canvas_crimson", cloth(MapColor.COLOR_RED));

	// Ground decals
	public static final CarpetBlock SCORCH_MARK = register("scorch_mark", CarpetBlock::new, decal(MapColor.COLOR_BLACK, SoundType.WOOL));
	public static final CarpetBlock DEBRIS = register("debris", CarpetBlock::new, decal(MapColor.COLOR_GRAY, SoundType.GRAVEL));

	/** Every build-kit block, in registration order. */
	public static List<Block> all() {
		return Collections.unmodifiableList(ALL);
	}

	/** Loads the class, which registers every block and its item (called from {@code StructuresFeature}). */
	public static void init() {
	}

	private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
		T block = ModRegistry.block(name, factory, properties);
		ALL.add(block);
		return block;
	}

	private static Block block(String name, BlockBehaviour.Properties properties) {
		return register(name, Block::new, properties);
	}

	private static StairBlock stairs(String name, Block base, BlockBehaviour.Properties properties) {
		return register(name, p -> new StairBlock(base.defaultBlockState(), p), properties);
	}

	private static SlabBlock slab(String name, BlockBehaviour.Properties properties) {
		return register(name, SlabBlock::new, properties);
	}

	private static WallBlock wall(String name, BlockBehaviour.Properties properties) {
		return register(name, WallBlock::new, properties);
	}

	private static RotatedPillarBlock pillar(String name, BlockBehaviour.Properties properties) {
		return register(name, RotatedPillarBlock::new, properties);
	}

	private static BlockBehaviour.Properties metal(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(3f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL);
	}

	private static BlockBehaviour.Properties masonry(MapColor color, SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(1.8f, 6f).requiresCorrectToolForDrops().sound(sound);
	}

	private static BlockBehaviour.Properties cloth(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(0.8f).sound(SoundType.WOOL);
	}

	private static BlockBehaviour.Properties decal(MapColor color, SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(0.1f).sound(sound).noOcclusion().pushReaction(PushReaction.DESTROY);
	}

	private BuildBlocks() {
	}
}
