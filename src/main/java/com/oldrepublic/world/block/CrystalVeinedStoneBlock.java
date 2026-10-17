package com.oldrepublic.world.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Stone threaded with faint kyber veins. Glints now and then, which marks breakable cave walls. */
public class CrystalVeinedStoneBlock extends Block {
	public static final MapCodec<CrystalVeinedStoneBlock> CODEC = simpleCodec(CrystalVeinedStoneBlock::new);

	public CrystalVeinedStoneBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<CrystalVeinedStoneBlock> codec() {
		return CODEC;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		WorldBlockAmbience.get().vein(state, level, pos, random);
	}
}
