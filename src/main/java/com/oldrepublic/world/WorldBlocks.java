package com.oldrepublic.world;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModRegistry;
import com.oldrepublic.world.block.CrystalVeinedStoneBlock;
import com.oldrepublic.world.block.KyberClusterBlock;
import com.oldrepublic.world.block.MeditationStoneBlock;
import com.oldrepublic.world.block.ResonantSealBlock;
import com.oldrepublic.world.block.UnstableClusterBlockEntity;
import com.oldrepublic.world.block.UnstableCrystalClusterBlock;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Blocks and block entities of the world feature, in creative-tab order. */
public final class WorldBlocks {
	public static final KyberClusterBlock KYBER_CLUSTER = ModRegistry.block("kyber_cluster", KyberClusterBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_BLUE)
					.strength(1.5f)
					.sound(SoundType.AMETHYST_CLUSTER)
					.lightLevel(state -> 6)
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY));

	public static final CrystalVeinedStoneBlock CRYSTAL_VEINED_STONE = ModRegistry.block("crystal_veined_stone", CrystalVeinedStoneBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.strength(1.8f, 6f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.STONE)
					.lightLevel(state -> 2));

	public static final UnstableCrystalClusterBlock UNSTABLE_CRYSTAL_CLUSTER = ModRegistry.block("unstable_crystal_cluster", UnstableCrystalClusterBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_MAGENTA)
					.strength(1.5f)
					.sound(SoundType.AMETHYST_CLUSTER)
					.lightLevel(state -> state.getValue(UnstableCrystalClusterBlock.CHARGING) ? 11 : 4)
					.noOcclusion()
					.noLootTable()
					.pushReaction(PushReaction.DESTROY));

	/** Unbreakable in survival: it guards the trial chamber. */
	public static final ResonantSealBlock RESONANT_SEAL = ModRegistry.block("resonant_seal", ResonantSealBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(-1f, 3600000f)
					.sound(SoundType.GLASS)
					.lightLevel(state -> state.getValue(ResonantSealBlock.OPEN) ? 0 : 3)
					.noOcclusion()
					.noLootTable()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false)
					.isRedstoneConductor((state, level, pos) -> false)
					.isSuffocating((state, level, pos) -> false)
					.isViewBlocking((state, level, pos) -> false));

	/** Unbreakable in survival so a cave's trial can never be lost; creative builders can still place and remove it. */
	public static final MeditationStoneBlock MEDITATION_STONE = ModRegistry.block("meditation_stone", MeditationStoneBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.DEEPSLATE)
					.strength(-1f, 3600000f)
					.sound(SoundType.POLISHED_DEEPSLATE)
					.lightLevel(state -> 3)
					.noOcclusion()
					.pushReaction(PushReaction.BLOCK));

	public static final BlockEntityType<UnstableClusterBlockEntity> UNSTABLE_CLUSTER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("unstable_crystal_cluster"),
			FabricBlockEntityTypeBuilder.create(UnstableClusterBlockEntity::new, UNSTABLE_CRYSTAL_CLUSTER).build());

	static void init() {
	}

	private WorldBlocks() {
	}
}
