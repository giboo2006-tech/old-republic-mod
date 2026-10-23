package com.oldrepublic.world.puzzle;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.world.puzzle.PuzzleStates.Skin;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A heavy block in a floor alcove (spec 3.3). A Push sinks it for {@code sites.counterweightTicks};
 * while it is down it outputs redstone 15 from its back face only, into the cell
 * {@code pos.relative(facing.getOpposite())}, where the site hides its wiring. A grinding warning
 * plays {@code sites.counterweightWarnTicks} before it rises by itself. {@code facing} is its front,
 * toward the room; {@code age} marks that the warning has played.
 */
public class CounterweightStoneBlock extends Block {
	public static final MapCodec<CounterweightStoneBlock> CODEC = simpleCodec(CounterweightStoneBlock::new);
	private static final VoxelShape SUNK_SHAPE = Block.box(0, 0, 0, 16, 10, 16);

	public CounterweightStoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PuzzleStates.SUNK, false).setValue(PuzzleStates.FACING, Direction.NORTH)
				.setValue(PuzzleStates.SKIN, Skin.RUIN).setValue(PuzzleStates.AGE, 0));
	}

	@Override
	protected MapCodec<CounterweightStoneBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PuzzleStates.SUNK, PuzzleStates.FACING, PuzzleStates.SKIN, PuzzleStates.AGE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// API-CHECK: UseOnContext#getHorizontalDirection (as in LightsaberWorkbenchBlock)
		return defaultBlockState().setValue(PuzzleStates.FACING, context.getHorizontalDirection().getOpposite());
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
		return state.getValue(PuzzleStates.SUNK) ? SUNK_SHAPE : Shapes.block();
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return state.getValue(PuzzleStates.SUNK) ? SUNK_SHAPE : Shapes.block();
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	/**
	 * Power only toward the back cell. The asking neighbour passes the direction from itself toward
	 * this block, so the back neighbour asks with {@code facing} (the {@code ObserverBlock} convention).
	 */
	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		// API-CHECK: direction convention of getSignal (probe body:net.minecraft.world.level.block.ObserverBlock#getSignal); covered by counterweightPowersStepStonesBackFaceOnly
		return state.getValue(PuzzleStates.SUNK) && direction == state.getValue(PuzzleStates.FACING) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return getSignal(state, level, pos, direction);
	}

	/**
	 * Sinks the counterweight (the Push path; the client steps use it too). Returns true when it
	 * sank, false when it is already down or {@code pos} is not a counterweight.
	 */
	public static boolean sink(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof CounterweightStoneBlock block)) {
			return false;
		}

		PuzzleRules.CounterweightStep step = PuzzleRules.counterweightPush(state.getValue(PuzzleStates.SUNK), SiteBalance.get());

		if (step == null) {
			return false;
		}

		block.apply(level, pos, state, step);
		return true;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		PuzzleRules.CounterweightStep step = PuzzleRules.counterweightTick(state.getValue(PuzzleStates.SUNK), state.getValue(PuzzleStates.AGE), SiteBalance.get());

		if (step != null) {
			apply(level, pos, state, step);
		}
	}

	private void apply(ServerLevel level, BlockPos pos, BlockState state, PuzzleRules.CounterweightStep step) {
		boolean changed = state.getValue(PuzzleStates.SUNK) != step.sunk();
		BlockState next = state.setValue(PuzzleStates.SUNK, step.sunk()).setValue(PuzzleStates.AGE, step.age());
		level.setBlock(pos, next, Block.UPDATE_ALL);

		if (changed) {
			updateOutput(level, pos, next);
		}

		if (step.delay() > 0) {
			level.scheduleTick(pos, this, step.delay());
		}

		if (step.effect() >= 0) {
			PuzzleEffects.send(level, PuzzleEffects.COUNTERWEIGHT, pos, 1f, step.effect());
		}
	}

	/** Tells this cell's neighbours and the back cell's neighbours (strong power passes through the back block). */
	private void updateOutput(Level level, BlockPos pos, BlockState state) {
		level.updateNeighborsAt(pos, this);
		level.updateNeighborsAt(pos.relative(state.getValue(PuzzleStates.FACING).getOpposite()), this);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (state.getValue(PuzzleStates.SUNK)) {
			updateOutput(level, pos, state);
		}
	}
}
