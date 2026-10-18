package com.oldrepublic.crafting;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.bench.EngineeringBenchBlock;
import com.oldrepublic.crafting.bench.EngineeringBenchBlockEntity;
import com.oldrepublic.registry.ModRegistry;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Blocks and block entities of the crafting feature (spec MILESTONE_4.md 7.1). */
public final class CraftingBlocks {
	// Drops with any tool until the lead adds it to minecraft:mineable/pickaxe (a request).
	public static final EngineeringBenchBlock ENGINEERING_BENCH = ModRegistry.block("engineering_bench", EngineeringBenchBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).noOcclusion());

	public static final BlockEntityType<EngineeringBenchBlockEntity> ENGINEERING_BENCH_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("engineering_bench"),
			FabricBlockEntityTypeBuilder.create(EngineeringBenchBlockEntity::new, ENGINEERING_BENCH).build());

	/** Loads the class, registering the bench. */
	static void init() {
	}

	private CraftingBlocks() {
	}
}
