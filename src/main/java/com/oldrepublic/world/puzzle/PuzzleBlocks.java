package com.oldrepublic.world.puzzle;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModRegistry;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The puzzle kit, resource and decor blocks of the M3 sites (spec 3.3), in creative-tab order, and
 * the two puzzle block entities. Puzzle blocks are unbreakable in survival (destroy time -1,
 * explosion resistance 3,600,000, no loot); resource and decor blocks break and drop through their
 * loot tables.
 */
public final class PuzzleBlocks {
	public static final VeiledStoneBlock VEILED_STONE = ModRegistry.block("veiled_stone", VeiledStoneBlock::new,
			puzzle(MapColor.STONE, SoundType.STONE));

	public static final CounterweightStoneBlock COUNTERWEIGHT_STONE = ModRegistry.block("counterweight_stone", CounterweightStoneBlock::new,
			puzzle(MapColor.STONE, SoundType.POLISHED_DEEPSLATE));

	public static final StepStoneBlock STEP_STONE = ModRegistry.block("step_stone", StepStoneBlock::new,
			puzzle(MapColor.STONE, SoundType.STONE).noOcclusion());

	public static final EchoUrnBlock ECHO_URN = ModRegistry.block("echo_urn", EchoUrnBlock::new,
			puzzle(MapColor.TERRACOTTA_BROWN, SoundType.DECORATED_POT).noOcclusion());

	public static final UrnKeeperBlock URN_KEEPER = ModRegistry.block("urn_keeper", UrnKeeperBlock::new,
			puzzle(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE).lightLevel(state -> state.getValue(PuzzleStates.SOLVED) ? 4 : 0));

	public static final PullAnchorBlock PULL_ANCHOR = ModRegistry.block("pull_anchor", PullAnchorBlock::new,
			puzzle(MapColor.STONE, SoundType.STONE));

	public static final CollapsedRubbleBlock COLLAPSED_RUBBLE = ModRegistry.block("collapsed_rubble", CollapsedRubbleBlock::new,
			puzzle(MapColor.STONE, SoundType.TUFF));

	public static final PuzzleSealBlock PUZZLE_SEAL = ModRegistry.block("puzzle_seal", PuzzleSealBlock::new,
			puzzle(MapColor.STONE, SoundType.STONE));

	public static final HolocronPedestalBlock HOLOCRON_PEDESTAL = ModRegistry.block("holocron_pedestal", HolocronPedestalBlock::new,
			puzzle(MapColor.STONE, SoundType.POLISHED_DEEPSLATE).noOcclusion().lightLevel(state -> HolocronPedestalBlock.isReady(state) ? 3 : 0));

	public static final TombVentBlock TOMB_VENT = ModRegistry.block("tomb_vent", TombVentBlock::new,
			puzzle(MapColor.COLOR_BLACK, SoundType.BASALT));

	public static final LumenMossBlock LUMEN_MOSS = ModRegistry.block("lumen_moss", LumenMossBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.GLOW_LICHEN)
					.strength(0.1f)
					.sound(SoundType.MOSS_CARPET)
					.lightLevel(state -> 4)
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY));

	public static final FuneraryUrnBlock FUNERARY_URN = ModRegistry.block("funerary_urn", FuneraryUrnBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_BROWN)
					.strength(0.6f)
					.sound(SoundType.DECORATED_POT)
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY));

	public static final WreckSalvageBlock WRECK_SALVAGE = ModRegistry.block("wreck_salvage", WreckSalvageBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.strength(1.5f, 3f)
					.sound(SoundType.METAL)
					.noOcclusion());

	public static final Block RUIN_STONE = ModRegistry.block("ruin_stone", Block::new, stone(MapColor.SAND, SoundType.STONE));
	public static final Block RUIN_STONE_CARVED = ModRegistry.block("ruin_stone_carved", Block::new, stone(MapColor.SAND, SoundType.STONE));
	public static final Block RUIN_STONE_CRACKED = ModRegistry.block("ruin_stone_cracked", Block::new, stone(MapColor.SAND, SoundType.STONE));
	public static final Block RUIN_STONE_MOSSY = ModRegistry.block("ruin_stone_mossy", Block::new, stone(MapColor.SAND, SoundType.STONE));
	public static final Block TOMB_STONE = ModRegistry.block("tomb_stone", Block::new, stone(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE));
	public static final Block TOMB_STONE_CARVED = ModRegistry.block("tomb_stone_carved", Block::new, stone(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE));
	public static final Block TOMB_STONE_CRACKED = ModRegistry.block("tomb_stone_cracked", Block::new, stone(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE));
	public static final Block WRECK_PLATING = ModRegistry.block("wreck_plating", Block::new, metal(MapColor.METAL));
	public static final Block WRECK_PLATING_SCORCHED = ModRegistry.block("wreck_plating_scorched", Block::new, metal(MapColor.COLOR_GRAY));
	public static final WreckConduitBlock WRECK_CONDUIT = ModRegistry.block("wreck_conduit", WreckConduitBlock::new, metal(MapColor.METAL).lightLevel(state -> 2));
	public static final Block REPUBLIC_PANEL = ModRegistry.block("republic_panel", Block::new, metal(MapColor.TERRACOTTA_WHITE));
	public static final Block IMPERIAL_PANEL = ModRegistry.block("imperial_panel", Block::new, metal(MapColor.COLOR_BLACK));

	public static final BlockEntityType<UrnKeeperBlockEntity> URN_KEEPER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("urn_keeper"),
			FabricBlockEntityTypeBuilder.create(UrnKeeperBlockEntity::new, URN_KEEPER).build());

	public static final BlockEntityType<HolocronPedestalBlockEntity> HOLOCRON_PEDESTAL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("holocron_pedestal"),
			FabricBlockEntityTypeBuilder.create(HolocronPedestalBlockEntity::new, HOLOCRON_PEDESTAL).build());

	/** Unbreakable in survival, never a spawn surface, never moved by pistons, no drops. */
	private static BlockBehaviour.Properties puzzle(MapColor color, SoundType sound) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(-1f, 3600000f)
				.sound(sound)
				.noLootTable()
				.pushReaction(PushReaction.BLOCK)
				.isValidSpawn((state, level, pos, type) -> false);
	}

	/** Breakable site stone: mined with a pickaxe ({@code #minecraft:mineable/pickaxe}) to drop itself. */
	private static BlockBehaviour.Properties stone(MapColor color, SoundType sound) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(1.8f, 6f)
				.requiresCorrectToolForDrops()
				.sound(sound);
	}

	/** Breakable hull and panel metal: mined with a pickaxe to drop itself. */
	private static BlockBehaviour.Properties metal(MapColor color) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(3f, 6f)
				.requiresCorrectToolForDrops()
				.sound(SoundType.METAL);
	}

	/** Loads the class: registers every block, its item and the block entity types. */
	public static void init() {
	}

	private PuzzleBlocks() {
	}
}
