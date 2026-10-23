package com.oldrepublic.world.puzzle;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The carved block behind a burial dais that remembers which urns sing and in what order (spec
 * 3.3). Force Sense within {@code sites.urnSenseRange} plays the order ({@link PuzzleSense}); the
 * urns report Pushes to it ({@link EchoUrnBlock}). Solved, it outputs redstone 15 on every side and
 * latches until it resets after {@code sites.puzzleResetTicks} with no player near. {@code facing}
 * north is the template frame of its {@code urns} offsets. State lives in {@link UrnKeeperBlockEntity}.
 */
public class UrnKeeperBlock extends Block implements EntityBlock {
	public static final MapCodec<UrnKeeperBlock> CODEC = simpleCodec(UrnKeeperBlock::new);

	public UrnKeeperBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PuzzleStates.FACING, Direction.NORTH).setValue(PuzzleStates.SOLVED, false));
	}

	@Override
	protected MapCodec<UrnKeeperBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PuzzleStates.FACING, PuzzleStates.SOLVED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// API-CHECK: UseOnContext#getHorizontalDirection (as in LightsaberWorkbenchBlock)
		return defaultBlockState().setValue(PuzzleStates.FACING, context.getHorizontalDirection().getOpposite());
	}

	/** Rotating the keeper rotates its urn frame with it, exactly as structure placement rotates the urns. */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(PuzzleStates.FACING, rotation.rotate(state.getValue(PuzzleStates.FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(PuzzleStates.FACING)));
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(PuzzleStates.SOLVED) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return getSignal(state, level, pos, direction);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new UrnKeeperBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != PuzzleBlocks.URN_KEEPER_ENTITY) {
			return null;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<UrnKeeperBlockEntity>) UrnKeeperBlockEntity::serverTick;
	}

	/** The idle step of a solved keeper: resets (unpowering its line) after the idle time with no player near. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(PuzzleStates.SOLVED) || !(level.getBlockEntity(pos) instanceof UrnKeeperBlockEntity keeper)) {
			return;
		}

		SiteBalance b = SiteBalance.get();

		if (!keeper.idleStep(PuzzleWorld.playerNear(level, pos, b.puzzleResetRange), b)) {
			PuzzleWorld.scheduleReset(level, pos, this, b);
			return;
		}

		level.setBlock(pos, state.setValue(PuzzleStates.SOLVED, false), Block.UPDATE_ALL);
		level.updateNeighborsAt(pos, this);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (state.getValue(PuzzleStates.SOLVED)) {
			level.updateNeighborsAt(pos, this);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PuzzleDisplay.get().urnKeeper(state, level, pos, random);
	}
}
