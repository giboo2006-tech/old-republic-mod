package com.oldrepublic.world.puzzle;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.world.puzzle.PuzzleStates.Skin;
import com.oldrepublic.world.puzzle.PuzzleStates.Veil;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
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
 * Stone that hides a way on (spec 3.3). VEILED it looks exactly like the stone of its {@code skin}.
 * Force Sense reveals the connected group ({@link #reveal}): for {@code sites.veilRevealTicks} its
 * seams seep fine dust. While REVEALED, a Push on the aim or a hit with a lit saber opens the whole
 * group ({@link #open}, at most {@code sites.veilGroupMax} blocks): OPEN has no shape, no model and
 * no occlusion. An opened group goes back to VEILED after {@code sites.puzzleResetTicks} with no
 * player near, never while something stands in it.
 *
 * <p>{@code age} counts the idle steps while OPEN. While REVEALED a non-zero {@code age} is a refresh
 * from a later Sense cast: the level keeps one scheduled tick per block, so the pending tick cannot
 * be moved; when it comes, the reveal runs on for {@code age} × {@value #REFRESH_UNIT} ticks, the
 * refreshing cast's length rounded up (at most 240 ticks, Seeker's Sight's default).
 */
public class VeiledStoneBlock extends Block {
	public static final MapCodec<VeiledStoneBlock> CODEC = simpleCodec(VeiledStoneBlock::new);
	/** Ticks per {@code age} step of a pending refresh while REVEALED. */
	static final int REFRESH_UNIT = 40;

	public VeiledStoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PuzzleStates.SKIN, Skin.RUIN).setValue(PuzzleStates.VEIL, Veil.VEILED)
				.setValue(PuzzleStates.AGE, 0));
	}

	@Override
	protected MapCodec<VeiledStoneBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PuzzleStates.SKIN, PuzzleStates.VEIL, PuzzleStates.AGE);
	}

	private static boolean isOpen(BlockState state) {
		return state.getValue(PuzzleStates.VEIL) == Veil.OPEN;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return isOpen(state) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return isOpen(state) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return isOpen(state) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return isOpen(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
	}

	/**
	 * Reveals the group around {@code pos} for {@code ticks} (the Sense provider's path; the client
	 * steps use it too). Already revealed members are refreshed: they stay revealed for at least
	 * {@code ticks} more, rounded up to {@value #REFRESH_UNIT} ticks. Returns the group size, 0 when
	 * {@code pos} is not a closed veiled stone.
	 */
	public static int reveal(ServerLevel level, BlockPos pos, int ticks) {
		return revealGroup(level, pos, ticks).size();
	}

	/** {@link #reveal}, returning the revealed group itself (empty when nothing was revealed). */
	static Set<BlockPos> revealGroup(ServerLevel level, BlockPos pos, int ticks) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof VeiledStoneBlock block) || isOpen(state)) {
			return Set.of();
		}

		Set<BlockPos> group = PuzzleWorld.group(level, pos, block, s -> !isOpen(s), SiteBalance.get().veilGroupMax);

		for (BlockPos member : group) {
			BlockState memberState = level.getBlockState(member);
			boolean refresh = memberState.getValue(PuzzleStates.VEIL) == Veil.REVEALED && PuzzleWorld.tickPending(level, member, block);
			// A refresh keeps the longer of the pending refresh and this one.
			int age = refresh ? Math.max(memberState.getValue(PuzzleStates.AGE), refreshAge(ticks)) : 0;
			level.setBlock(member, memberState.setValue(PuzzleStates.VEIL, Veil.REVEALED).setValue(PuzzleStates.AGE, age), Block.UPDATE_ALL);

			if (!refresh) {
				level.scheduleTick(member, block, Math.max(1, ticks));
			}
		}

		PuzzleEffects.send(level, PuzzleEffects.VEIL_REVEAL, pos, group.size(), state.getValue(PuzzleStates.SKIN).ordinal());
		return group;
	}

	/** A refresh of {@code ticks} as an {@code age}: whole {@value #REFRESH_UNIT}-tick steps, rounded up, from 1 to {@link PuzzleStates#MAX_AGE}. */
	static int refreshAge(int ticks) {
		return Math.min(PuzzleStates.MAX_AGE, Math.max(1, Math.ceilDiv(Math.max(1, ticks), REFRESH_UNIT)));
	}

	/**
	 * Opens the REVEALED group around {@code pos} (a Push on the aim or a lit-saber hit). Returns
	 * the number of blocks opened, 0 when {@code pos} is not a revealed veiled stone.
	 */
	public static int open(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof VeiledStoneBlock block) || state.getValue(PuzzleStates.VEIL) != Veil.REVEALED) {
			return 0;
		}

		SiteBalance b = SiteBalance.get();
		// Only what Sense showed opens: the revealed members connected to the hit block.
		Set<BlockPos> group = PuzzleWorld.group(level, pos, block, s -> s.getValue(PuzzleStates.VEIL) == Veil.REVEALED, b.veilGroupMax);

		for (BlockPos member : group) {
			BlockState memberState = level.getBlockState(member);
			level.setBlock(member, memberState.setValue(PuzzleStates.VEIL, Veil.OPEN).setValue(PuzzleStates.AGE, 0), Block.UPDATE_ALL);
			PuzzleWorld.scheduleReset(level, member, block, b);
		}

		PuzzleEffects.send(level, PuzzleEffects.VEIL_OPEN, pos, group.size(), state.getValue(PuzzleStates.SKIN).ordinal());
		PuzzleEvents.solved(level, pos, PuzzleEvents.VEIL);
		return group.size();
	}

	/** A hit with a lit saber in the main hand opens a revealed group. */
	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && state.getValue(PuzzleStates.VEIL) == Veil.REVEALED
				&& SaberComponents.isUsable(player.getMainHandItem())) {
			open(server, pos, serverPlayer);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		SiteBalance b = SiteBalance.get();

		switch (state.getValue(PuzzleStates.VEIL)) {
			case REVEALED -> {
				int refresh = state.getValue(PuzzleStates.AGE);

				if (refresh > 0) {
					level.setBlock(pos, state.setValue(PuzzleStates.AGE, 0), Block.UPDATE_CLIENTS);
					level.scheduleTick(pos, this, refresh * REFRESH_UNIT);
				} else {
					level.setBlock(pos, state.setValue(PuzzleStates.VEIL, Veil.VEILED), Block.UPDATE_ALL);
				}
			}
			case OPEN -> idleStep(level, pos, PuzzleWorld.playerNear(level, pos, b.puzzleResetRange), b);
			case VEILED -> {
			}
		}
	}

	/**
	 * One idle step of the open stone at {@code pos} (its scheduled tick; a test seam): a player
	 * near keeps it open; at reset the whole open group closes, unless something stands in it, which
	 * retries after {@link PuzzleRules#OCCUPIED_RETRY_TICKS}. Returns true when the group closed.
	 */
	public static boolean idleStep(ServerLevel level, BlockPos pos, boolean playerNear, SiteBalance b) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof VeiledStoneBlock block) || !isOpen(state)) {
			return false;
		}

		PuzzleRules.ResetStep step = PuzzleRules.resetStep(state.getValue(PuzzleStates.AGE), playerNear, b);

		if (!step.reset()) {
			level.setBlock(pos, state.setValue(PuzzleStates.AGE, step.age()), Block.UPDATE_CLIENTS);
			PuzzleWorld.scheduleReset(level, pos, block, b);
			return false;
		}

		Set<BlockPos> group = PuzzleWorld.group(level, pos, block, VeiledStoneBlock::isOpen, b.veilGroupMax);

		if (PuzzleWorld.occupied(level, group)) {
			level.setBlock(pos, state.setValue(PuzzleStates.AGE, step.age()), Block.UPDATE_CLIENTS);
			level.scheduleTick(pos, block, PuzzleRules.OCCUPIED_RETRY_TICKS);
			return false;
		}

		for (BlockPos member : group) {
			BlockState memberState = level.getBlockState(member);
			level.setBlock(member, memberState.setValue(PuzzleStates.VEIL, Veil.VEILED).setValue(PuzzleStates.AGE, 0), Block.UPDATE_ALL);
		}

		return true;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PuzzleDisplay.get().veiledStone(state, level, pos, random);
	}
}
