package com.oldrepublic.boss.kaast;

import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The camp's block (spec MILESTONE_4.md 16.1): {@code camp_gate}, the sliding scrap door of the ring. */
public final class KaastBlocks {
	/** Unbreakable in survival (12.2): an operator removes it with commands. */
	public static final Block CAMP_GATE = ModRegistry.block("camp_gate", CampGateBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.strength(-1f, 3600000f)
					.sound(SoundType.METAL)
					.noOcclusion()
					.noLootTable()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false)
					.isRedstoneConductor((state, level, pos) -> false)
					.isSuffocating((state, level, pos) -> false)
					.isViewBlocking((state, level, pos) -> false));

	/** Loads the class, registering the block and its item. Called from {@code FoesFeature}. */
	public static void init() {
	}

	private KaastBlocks() {
	}
}
