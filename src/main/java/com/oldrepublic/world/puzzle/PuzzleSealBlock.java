package com.oldrepublic.world.puzzle;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.world.puzzle.PuzzleStates.Skin;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sealed wall face (spec 3.3). Redstone power opens it with a dust burst and it latches open (no
 * shape, no model); the opening spreads to the connected seal blocks (at most
 * {@code sites.veilGroupMax}), so a doorway of several cells needs power at one of them. It closes
 * again only on reset ({@code sites.puzzleResetTicks} with no player near), only while unpowered and
 * never while an entity is inside any of its cells.
 */
public class PuzzleSealBlock extends Block {
	public static final MapCodec<PuzzleSealBlock> CODEC = simpleCodec(PuzzleSealBlock::new);

	public PuzzleSealBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PuzzleStates.OPEN, false).setValue(PuzzleStates.SKIN, Skin.RUIN).setValue(PuzzleStates.AGE, 0));
	}

	@Override
	protected MapCodec<PuzzleSealBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PuzzleStates.OPEN, PuzzleStates.SKIN, PuzzleStates.AGE);
	}

	private static boolean isOpen(BlockState state) {
		return state.getValue(PuzzleStates.OPEN);
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

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
		if (level instanceof ServerLevel server && !isOpen(state) && server.hasNeighborSignal(pos)) {
			open(server, pos);
		}
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server && !oldState.is(this) && !isOpen(state) && server.hasNeighborSignal(pos)) {
			open(server, pos);
		}
	}

	/** Opens the closed seal group around {@code pos} and latches it. Returns the number of blocks opened. */
	public static int open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof PuzzleSealBlock block) || isOpen(state)) {
			return 0;
		}

		SiteBalance b = SiteBalance.get();
		Set<BlockPos> group = PuzzleWorld.group(level, pos, block, s -> !isOpen(s), b.veilGroupMax);

		// The whole group opens before any neighbour hears of it, so a second powered cell never opens it twice.
		for (BlockPos member : group) {
			BlockState memberState = level.getBlockState(member);
			level.setBlock(member, memberState.setValue(PuzzleStates.OPEN, true).setValue(PuzzleStates.AGE, 0), Block.UPDATE_CLIENTS);
			PuzzleWorld.scheduleReset(level, member, block, b);
		}

		for (BlockPos member : group) {
			level.updateNeighborsAt(member, block);
		}

		PuzzleEffects.send(level, PuzzleEffects.SEAL_OPEN, pos, group.size(), PuzzleEffects.SEAL_PUZZLE);
		PuzzleEvents.solved(level, pos, PuzzleEvents.SEAL);
		return group.size();
	}

	/** The idle step of an open seal (see {@link #idleStep}). */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		SiteBalance b = SiteBalance.get();
		idleStep(level, pos, PuzzleWorld.playerNear(level, pos, b.puzzleResetRange), b);
	}

	/**
	 * One idle step of the open seal at {@code pos} (its scheduled tick; a test seam): a player near
	 * keeps it open; at reset the group closes if it is unpowered and nothing stands in it (an
	 * occupant retries after {@link PuzzleRules#OCCUPIED_RETRY_TICKS}). Returns true when it closed.
	 */
	public static boolean idleStep(ServerLevel level, BlockPos pos, boolean playerNear, SiteBalance b) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof PuzzleSealBlock block) || !isOpen(state)) {
			return false;
		}

		PuzzleRules.ResetStep step = PuzzleRules.resetStep(state.getValue(PuzzleStates.AGE), playerNear, b);

		if (!step.reset()) {
			level.setBlock(pos, state.setValue(PuzzleStates.AGE, step.age()), Block.UPDATE_CLIENTS);
			PuzzleWorld.scheduleReset(level, pos, block, b);
			return false;
		}

		Set<BlockPos> group = PuzzleWorld.group(level, pos, block, PuzzleSealBlock::isOpen, b.veilGroupMax);

		if (PuzzleWorld.powered(level, group)) {
			level.setBlock(pos, state.setValue(PuzzleStates.AGE, step.age()), Block.UPDATE_CLIENTS);
			PuzzleWorld.scheduleReset(level, pos, block, b);
			return false;
		}

		if (PuzzleWorld.occupied(level, group)) {
			level.setBlock(pos, state.setValue(PuzzleStates.AGE, step.age()), Block.UPDATE_CLIENTS);
			level.scheduleTick(pos, block, PuzzleRules.OCCUPIED_RETRY_TICKS);
			return false;
		}

		for (BlockPos member : group) {
			BlockState memberState = level.getBlockState(member);
			level.setBlock(member, memberState.setValue(PuzzleStates.OPEN, false).setValue(PuzzleStates.AGE, 0), Block.UPDATE_ALL);
		}

		return true;
	}
}
