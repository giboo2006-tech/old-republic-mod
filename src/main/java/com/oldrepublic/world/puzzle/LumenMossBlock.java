package com.oldrepublic.world.puzzle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin carpet of pale moss that glows faintly (light 4) under the ruin ledges (spec 3.3). Faint
 * warm motes drift up from it. It needs a sturdy floor, never regrows, and breaking it gives
 * 1–2 {@code lumen_thread} (its loot table).
 */
public class LumenMossBlock extends Block {
	public static final MapCodec<LumenMossBlock> CODEC = simpleCodec(LumenMossBlock::new);
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

	public LumenMossBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<LumenMossBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}

		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PuzzleDisplay.get().lumenMoss(state, level, pos, random);
	}
}
