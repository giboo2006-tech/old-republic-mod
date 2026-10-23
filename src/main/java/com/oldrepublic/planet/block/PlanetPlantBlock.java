package com.oldrepublic.planet.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small planet herb (spec MILESTONE_4.md 3.2, 3.3): no collision, replaceable, gone when its
 * ground goes. Each kind says what it grows on; its loot table gives the herb itself.
 */
public abstract class PlanetPlantBlock extends Block {
	private final VoxelShape shape;

	protected PlanetPlantBlock(int width, int height, Properties properties) {
		super(properties);
		double inset = (16 - width) / 2.0;
		this.shape = Block.box(inset, 0, inset, 16 - inset, height, 16 - inset);
	}

	/** Whether the plant may stand on {@code ground}. */
	protected abstract boolean mayPlaceOn(BlockState ground);

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return mayPlaceOn(level.getBlockState(pos.below()));
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
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}
}
