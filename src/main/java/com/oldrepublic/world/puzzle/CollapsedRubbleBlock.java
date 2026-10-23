package com.oldrepublic.world.puzzle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A jammed heap of fallen stone closing a doorway (spec 3.3). Only Lift moves it: it shudders while
 * a Lift channel holds it on the aim and clears after {@code sites.rubbleTicks} ticks of channel (no
 * shape, no model, a debris burst). Force Lift dispatches every tick of its channel; a gap of more
 * than {@link PuzzleRules#RUBBLE_MAX_GAP} ticks starts the count over. A cleared doorway re-forms
 * after {@code sites.puzzleResetTicks} with no player near, never on an occupant.
 */
public class CollapsedRubbleBlock extends Block {
	public static final MapCodec<CollapsedRubbleBlock> CODEC = simpleCodec(CollapsedRubbleBlock::new);
	/** Channels idle this long are forgotten. */
	private static final int CHANNEL_FORGET_TICKS = 200;

	private record ChannelKey(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static final Map<ChannelKey, PuzzleRules.RubbleChannel> CHANNELS = new HashMap<>();

	public CollapsedRubbleBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PuzzleStates.CLEARED, false).setValue(PuzzleStates.AGE, 0));
	}

	@Override
	protected MapCodec<CollapsedRubbleBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PuzzleStates.CLEARED, PuzzleStates.AGE);
	}

	private static boolean cleared(BlockState state) {
		return state.getValue(PuzzleStates.CLEARED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return cleared(state) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return cleared(state) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return cleared(state) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return cleared(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
	}

	/**
	 * One Lift dispatch on the rubble at {@code pos}. The first dispatch of a channel makes it
	 * shudder; once the channel has lasted {@code sites.rubbleTicks} it clears. Returns true while the
	 * rubble takes the act, false when there is none (or it is already cleared).
	 */
	public static boolean lift(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof CollapsedRubbleBlock block) || cleared(state)) {
			return false;
		}

		long now = level.getGameTime();
		ChannelKey key = new ChannelKey(level.dimension(), pos.immutable());
		forgetStale(level.dimension(), now);
		PuzzleRules.RubbleChannel previous = CHANNELS.get(key);
		PuzzleRules.RubbleChannel channel = PuzzleRules.rubbleLift(previous, now);
		boolean started = previous == null || previous.start() != channel.start();
		SiteBalance b = SiteBalance.get();

		if (started) {
			PuzzleEffects.send(level, PuzzleEffects.RUBBLE_CRUMBLE, pos, Math.max(1, b.rubbleTicks), PuzzleEffects.RUBBLE_SHUDDER);
		}

		if (!PuzzleRules.rubbleClears(channel.start(), started ? now : previous.last(), now, b)) {
			CHANNELS.put(key, channel);
			return true;
		}

		CHANNELS.remove(key);
		level.setBlock(pos, state.setValue(PuzzleStates.CLEARED, true).setValue(PuzzleStates.AGE, 0), Block.UPDATE_ALL);
		PuzzleWorld.scheduleReset(level, pos, block, b);
		PuzzleEffects.send(level, PuzzleEffects.RUBBLE_CRUMBLE, pos, 1f, PuzzleEffects.RUBBLE_COLLAPSE);
		PuzzleEvents.solved(level, pos, PuzzleEvents.RUBBLE);
		return true;
	}

	private static void forgetStale(ResourceKey<Level> dimension, long now) {
		if (CHANNELS.isEmpty()) {
			return;
		}

		List<ChannelKey> stale = CHANNELS.entrySet().stream()
				.filter(e -> e.getKey().dimension().equals(dimension) && Math.abs(now - e.getValue().last()) > CHANNEL_FORGET_TICKS)
				.map(Map.Entry::getKey)
				.toList();
		stale.forEach(CHANNELS::remove);
	}

	/** The idle step of a cleared doorway (see {@link #idleStep}). */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		SiteBalance b = SiteBalance.get();
		idleStep(level, pos, PuzzleWorld.playerNear(level, pos, b.puzzleResetRange), b);
	}

	/**
	 * One idle step of the cleared rubble at {@code pos} (its scheduled tick; a test seam): it
	 * re-forms after the idle time, never on an occupant (which retries after
	 * {@link PuzzleRules#OCCUPIED_RETRY_TICKS}). Returns true when it re-formed.
	 */
	public static boolean idleStep(ServerLevel level, BlockPos pos, boolean playerNear, SiteBalance b) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof CollapsedRubbleBlock block) || !cleared(state)) {
			return false;
		}

		PuzzleRules.ResetStep step = PuzzleRules.resetStep(state.getValue(PuzzleStates.AGE), playerNear, b);

		if (!step.reset()) {
			level.setBlock(pos, state.setValue(PuzzleStates.AGE, step.age()), Block.UPDATE_CLIENTS);
			PuzzleWorld.scheduleReset(level, pos, block, b);
			return false;
		}

		if (PuzzleWorld.occupied(level, List.of(pos))) {
			level.setBlock(pos, state.setValue(PuzzleStates.AGE, step.age()), Block.UPDATE_CLIENTS);
			level.scheduleTick(pos, block, PuzzleRules.OCCUPIED_RETRY_TICKS);
			return false;
		}

		level.setBlock(pos, state.setValue(PuzzleStates.CLEARED, false).setValue(PuzzleStates.AGE, 0), Block.UPDATE_ALL);
		return true;
	}
}
