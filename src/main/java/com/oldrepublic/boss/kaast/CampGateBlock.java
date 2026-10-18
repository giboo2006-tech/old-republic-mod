package com.oldrepublic.boss.kaast;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The hunting camp's gate (spec MILESTONE_4.md 10.5, 10.6): a sliding scrap door in the ring's wall.
 * Open, only the top row's header shows (walk through; the owner uses it to call the daily hunter's echo after
 * the chapter). Closed during a fight (the M3 seal rules): a listed participant who uses it is let
 * through, anyone else is refused. The fight's session opens and closes it; whose camp it is comes
 * from the owner's chapter record of the camp ({@code StoryData.Chapter4.Duel}), not from a block entity.
 */
public class CampGateBlock extends Block {
	public static final MapCodec<CampGateBlock> CODEC = simpleCodec(CampGateBlock::new);
	public static final BooleanProperty OPEN = BooleanProperty.create("open");
	/** Whether this is the opening's top row (no gate block above), where the open door's header shows. */
	public static final BooleanProperty TOP = BooleanProperty.create("top");
	/** The retracted door: a header under the top of the opening. */
	private static final VoxelShape HEADER = Block.box(0, 12, 0, 16, 16, 16);

	public CampGateBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(OPEN, true).setValue(TOP, true));
	}

	@Override
	protected MapCodec<CampGateBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OPEN, TOP);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (!state.getValue(OPEN)) {
			return Shapes.block();
		}

		return state.getValue(TOP) ? HEADER : Shapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
	}

	/** Keeps {@link #TOP} in step with the block above. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (direction == Direction.UP) {
			return state.setValue(TOP, !neighborState.is(this));
		}

		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
		return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
			KaastFight.onGateUsed(server, pos, serverPlayer, state.getValue(OPEN));
		}

		return InteractionResult.SUCCESS;
	}
}
