package com.oldrepublic.world.puzzle;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A rusted iron ring set in stone (spec 3.3). A Pull makes it strain ({@code strain} 1) for
 * {@code sites.anchorStrainTicks}, then it releases ({@code strain} 2) and outputs redstone 15 on
 * every side for {@code sites.anchorOpenTicks}, then it rearms by itself. {@code age} is part of the
 * frozen state set and stays 0 on the anchor: its timeline is the strain alone.
 */
public class PullAnchorBlock extends Block {
	public static final MapCodec<PullAnchorBlock> CODEC = simpleCodec(PullAnchorBlock::new);

	public PullAnchorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PuzzleStates.STRAIN, 0).setValue(PuzzleStates.AGE, 0));
	}

	@Override
	protected MapCodec<PullAnchorBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PuzzleStates.STRAIN, PuzzleStates.AGE);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(PuzzleStates.STRAIN) == 2 ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return getSignal(state, level, pos, direction);
	}

	/** A Pull on the anchor. Returns true when it started to strain; false while it is straining or released. */
	public static boolean pull(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof PullAnchorBlock block)) {
			return false;
		}

		PuzzleRules.AnchorStep step = PuzzleRules.anchorPull(state.getValue(PuzzleStates.STRAIN), SiteBalance.get());

		if (step == null) {
			return false;
		}

		block.apply(level, pos, state, step);
		return true;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		PuzzleRules.AnchorStep step = PuzzleRules.anchorTick(state.getValue(PuzzleStates.STRAIN), SiteBalance.get());

		if (step != null) {
			apply(level, pos, state, step);
		}
	}

	private void apply(ServerLevel level, BlockPos pos, BlockState state, PuzzleRules.AnchorStep step) {
		boolean wasOn = state.getValue(PuzzleStates.STRAIN) == 2;
		level.setBlock(pos, state.setValue(PuzzleStates.STRAIN, step.strain()), Block.UPDATE_ALL);

		if (wasOn != (step.strain() == 2)) {
			level.updateNeighborsAt(pos, this);
		}

		if (step.delay() > 0) {
			level.scheduleTick(pos, this, step.delay());
		}

		if (step.effect() >= 0) {
			PuzzleEffects.send(level, PuzzleEffects.ANCHOR, pos, 1f, step.effect());
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (state.getValue(PuzzleStates.STRAIN) == 2) {
			level.updateNeighborsAt(pos, this);
		}
	}
}
