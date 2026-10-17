package com.oldrepublic.world.puzzle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decor: a torn power conduit in wreck plating that still spits the odd spark (spec 3.3, 3.7).
 * Breakable; it drops itself.
 */
public class WreckConduitBlock extends Block {
	public static final MapCodec<WreckConduitBlock> CODEC = simpleCodec(WreckConduitBlock::new);

	public WreckConduitBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<WreckConduitBlock> codec() {
		return CODEC;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PuzzleDisplay.get().wreckConduit(state, level, pos, random);
	}
}
