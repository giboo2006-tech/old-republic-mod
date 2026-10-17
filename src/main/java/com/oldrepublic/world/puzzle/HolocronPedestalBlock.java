package com.oldrepublic.world.puzzle;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.world.puzzle.PuzzleStates.PedestalKind;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The stone pedestal a holocron rests on (spec 3.3, 4.3). It is READY once it has been powered
 * (latched), or always where {@code always_ready} is set. Using a ready pedestal hands over to
 * {@link HolocronService#usePedestal} (W3); a pedestal that is not ready answers with a silent-stone
 * message. A pedestal never wakes sentinels. A latched {@code ready} clears again after
 * {@code sites.puzzleResetTicks} with no player near, once its power is gone.
 */
public class HolocronPedestalBlock extends Block implements EntityBlock {
	public static final MapCodec<HolocronPedestalBlock> CODEC = simpleCodec(HolocronPedestalBlock::new);
	/** Shown to a player who uses a pedestal that is not ready. */
	public static final String SILENT_KEY = "message.oldrepublic.world.pedestal.silent";
	private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(4, 3, 4, 12, 12, 12), Block.box(2, 12, 2, 14, 15, 14));

	public HolocronPedestalBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PuzzleStates.KIND, PedestalKind.JEDI).setValue(PuzzleStates.READY, false)
				.setValue(PuzzleStates.ALWAYS_READY, false));
	}

	@Override
	protected MapCodec<HolocronPedestalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PuzzleStates.KIND, PuzzleStates.READY, PuzzleStates.ALWAYS_READY);
	}

	/** Whether the pedestal gives its holocron now. */
	public static boolean isReady(BlockState state) {
		return state.getValue(PuzzleStates.READY) || state.getValue(PuzzleStates.ALWAYS_READY);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HolocronPedestalBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		if (isReady(state)) {
			HolocronService.usePedestal(serverPlayer, pos, state.getValue(PuzzleStates.KIND).holocron());
		} else {
			serverPlayer.sendOverlayMessage(Component.translatable(SILENT_KEY));
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
		if (level instanceof ServerLevel server) {
			latch(server, pos, state);
		}
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server && !oldState.is(this)) {
			latch(server, pos, state);
		}
	}

	/** The first power latches READY: light in the seams, motes from the cradle, the resolving chord. */
	private void latch(ServerLevel level, BlockPos pos, BlockState state) {
		if (isReady(state) || !level.hasNeighborSignal(pos)) {
			return;
		}

		level.setBlock(pos, state.setValue(PuzzleStates.READY, true), Block.UPDATE_ALL);
		PuzzleWorld.scheduleReset(level, pos, this, SiteBalance.get());
		PuzzleEffects.send(level, PuzzleEffects.PEDESTAL_READY, pos, 1f, state.getValue(PuzzleStates.KIND).ordinal());
		PuzzleEvents.solved(level, pos, PuzzleEvents.PEDESTAL);
	}

	/** The idle step of a latched pedestal: {@code ready} clears after the idle time, once unpowered. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(PuzzleStates.READY) || state.getValue(PuzzleStates.ALWAYS_READY)
				|| !(level.getBlockEntity(pos) instanceof HolocronPedestalBlockEntity pedestal)) {
			return;
		}

		SiteBalance b = SiteBalance.get();

		if (!pedestal.idleStep(PuzzleWorld.playerNear(level, pos, b.puzzleResetRange), b) || level.hasNeighborSignal(pos)) {
			PuzzleWorld.scheduleReset(level, pos, this, b);
			return;
		}

		level.setBlock(pos, state.setValue(PuzzleStates.READY, false), Block.UPDATE_ALL);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PuzzleDisplay.get().pedestal(state, level, pos, random);
	}
}
