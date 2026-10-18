package com.oldrepublic.planet.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Aurelite ore in stone or deepslate (spec MILESTONE_4.md 3.2): warm gold veins, exposed in
 * Selvaren's tarn cliffs and buried elsewhere. Mined with a pickaxe it gives aurelite shards (its
 * loot table). A rare glint plays where a vein meets the air.
 */
public class AureliteOreBlock extends Block {
	public static final MapCodec<AureliteOreBlock> CODEC = simpleCodec(AureliteOreBlock::new);

	public AureliteOreBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<AureliteOreBlock> codec() {
		return CODEC;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PlanetBlockAmbience.get().aurelite(state, level, pos, random);
	}
}
