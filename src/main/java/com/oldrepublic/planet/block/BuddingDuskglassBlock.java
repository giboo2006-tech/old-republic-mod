package com.oldrepublic.planet.block;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.planet.PlanetBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Budding duskglass (spec MILESTONE_4.md 16.1): the dark rock duskglass grows from, like budding
 * amethyst. On a random tick (1 in 5) it grows a cluster on one face whose neighbour is air. It
 * drops nothing; a site's walls are lined with it so harvested clusters come back slowly.
 */
public class BuddingDuskglassBlock extends Block {
	public static final MapCodec<BuddingDuskglassBlock> CODEC = simpleCodec(BuddingDuskglassBlock::new);
	private static final int GROW_CHANCE = 5;

	public BuddingDuskglassBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<BuddingDuskglassBlock> codec() {
		return CODEC;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(GROW_CHANCE) != 0) {
			return;
		}

		Direction face = Direction.values()[random.nextInt(Direction.values().length)];
		BlockPos target = pos.relative(face);

		if (level.getBlockState(target).isAir()) {
			level.setBlock(target, PlanetBlocks.DUSKGLASS_CLUSTER.defaultBlockState().setValue(DuskglassClusterBlock.FACING, face), Block.UPDATE_ALL);
		}
	}
}
