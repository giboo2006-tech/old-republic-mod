package com.oldrepublic.world.puzzle;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A funerary urn on a plinth that sings for its keeper (spec 3.3). A Push reports to the keeper
 * found through the {@code urn_keeper} POI within {@link #KEEPER_RANGE} whose stored offsets contain
 * this urn, so it works in worldgen, runtime and test placements alike. {@code facing} and
 * {@code index} are decor (the carving it shows).
 */
public class EchoUrnBlock extends Block {
	public static final MapCodec<EchoUrnBlock> CODEC = simpleCodec(EchoUrnBlock::new);
	/** How far an urn looks for its keeper. */
	public static final int KEEPER_RANGE = 16;
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

	public EchoUrnBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PuzzleStates.FACING, Direction.NORTH).setValue(PuzzleStates.INDEX, 0));
	}

	@Override
	protected MapCodec<EchoUrnBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PuzzleStates.FACING, PuzzleStates.INDEX);
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
		return SHAPE;
	}

	/** The keeper of the urn at {@code urn}: the nearest {@code urn_keeper} within range that lists it, or null. */
	public static @Nullable UrnKeeperBlockEntity findKeeper(ServerLevel level, BlockPos urn) {
		List<BlockPos> keepers = level.getPoiManager()
				.findAll(holder -> holder.is(PuzzlePois.URN_KEEPER), pos -> true, urn, KEEPER_RANGE, PoiManager.Occupancy.ANY)
				.sorted(Comparator.comparingDouble(pos -> pos.distSqr(urn)))
				.toList();

		for (BlockPos keeperPos : keepers) {
			if (level.getBlockEntity(keeperPos) instanceof UrnKeeperBlockEntity keeper && keeper.urnPositions().contains(urn)) {
				return keeper;
			}
		}

		return null;
	}
}
