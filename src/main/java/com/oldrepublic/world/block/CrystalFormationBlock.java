package com.oldrepublic.world.block;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A crystal growth attached to the face of a solid block, like vanilla amethyst clusters but
 * without waterlogging. {@code facing} points away from the supporting block.
 */
public abstract class CrystalFormationBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

	protected CrystalFormationBlock(int height, int inset, Properties properties) {
		super(properties);
		double lo = inset;
		double hi = 16 - inset;
		shapes.put(Direction.UP, Block.box(lo, 0, lo, hi, height, hi));
		shapes.put(Direction.DOWN, Block.box(lo, 16 - height, lo, hi, 16, hi));
		shapes.put(Direction.NORTH, Block.box(lo, lo, 16 - height, hi, hi, 16));
		shapes.put(Direction.SOUTH, Block.box(lo, lo, 0, hi, hi, height));
		shapes.put(Direction.EAST, Block.box(0, lo, lo, height, hi, hi));
		shapes.put(Direction.WEST, Block.box(16 - height, lo, lo, 16, hi, hi));
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
	}

	/** Unit vector of a direction. */
	public static Vec3 facingVector(Direction direction) {
		// API-CHECK: Direction#getStepX/Y/Z (standard, Direction was not dumped).
		return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.get(state.getValue(FACING));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos support = pos.relative(facing.getOpposite());
		return level.getBlockState(support).isFaceSturdy(level, support, facing);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}

		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
		return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
