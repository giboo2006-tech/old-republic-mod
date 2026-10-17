package com.oldrepublic.world.puzzle;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ledge that follows redstone (spec 3.3). It sits in the ledge's own cell, in front of its wall:
 * powered, it is a top-slab ledge with collision; unpowered, a thin plate against the wall with no
 * collision (a collision shape outside its own cell would not be checked reliably). {@code facing}
 * is the direction the ledge sticks out into the room; the wall is at {@code facing.getOpposite()}.
 */
public class StepStoneBlock extends Block {
	public static final MapCodec<StepStoneBlock> CODEC = simpleCodec(StepStoneBlock::new);
	private static final VoxelShape LEDGE = Block.box(0, 8, 0, 16, 16, 16);
	/** The retracted plate of a north-facing stone (wall to the south), rotated for the others. */
	private static final Map<Direction, VoxelShape> PLATES = new EnumMap<>(Direction.class);

	static {
		PLATES.put(Direction.NORTH, Block.box(0, 8, 14, 16, 16, 16));
		PLATES.put(Direction.SOUTH, Block.box(0, 8, 0, 16, 16, 2));
		PLATES.put(Direction.EAST, Block.box(0, 8, 0, 2, 16, 16));
		PLATES.put(Direction.WEST, Block.box(14, 8, 0, 16, 16, 16));
	}

	public StepStoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PuzzleStates.EXTENDED, false).setValue(PuzzleStates.FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<StepStoneBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PuzzleStates.EXTENDED, PuzzleStates.FACING);
	}

	/** Placed against a wall, the ledge sticks out of the clicked face; otherwise toward the player. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		// API-CHECK: UseOnContext#getHorizontalDirection (as in LightsaberWorkbenchBlock)
		Direction facing = clicked.getAxis() != Direction.Axis.Y ? clicked : context.getHorizontalDirection().getOpposite();
		// API-CHECK: Level#hasNeighborSignal(BlockPos) (SignalGetter; probe net.minecraft.world.level.SignalGetter)
		boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
		return defaultBlockState().setValue(PuzzleStates.FACING, facing).setValue(PuzzleStates.EXTENDED, powered);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(PuzzleStates.FACING, rotation.rotate(state.getValue(PuzzleStates.FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(PuzzleStates.FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(PuzzleStates.EXTENDED) ? LEDGE : PLATES.get(state.getValue(PuzzleStates.FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(PuzzleStates.EXTENDED) ? LEDGE : Shapes.empty();
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
		if (level instanceof ServerLevel server) {
			follow(server, pos, state);
		}
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server && !oldState.is(this)) {
			follow(server, pos, state);
		}
	}

	/** Extends or retracts to match the redstone power reaching this cell; one {@code step_stone} event per change. */
	private void follow(ServerLevel level, BlockPos pos, BlockState state) {
		boolean powered = level.hasNeighborSignal(pos);

		if (powered == state.getValue(PuzzleStates.EXTENDED)) {
			return;
		}

		level.setBlock(pos, state.setValue(PuzzleStates.EXTENDED, powered), Block.UPDATE_ALL);
		PuzzleEffects.send(level, PuzzleEffects.STEP_STONE, pos, 1f, powered ? PuzzleEffects.STEP_EXTEND : PuzzleEffects.STEP_RETRACT);
	}
}
