package com.oldrepublic.world.site;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An echo cairn (spec 3.3, 6.2): a small stacked-stone cairn ({@link SiteStates.CairnStyle#CAIRN}) or
 * a half-buried satchel ({@link SiteStates.CairnStyle#CACHE}) that belongs to one player. VEILED it has
 * no model, collision or occlusion; the owner's Sense reveals it through {@code EchoCairns.reveal}.
 * The owner's use while REVEALED calls {@code QuestHooks.onCairnTouched} and the cairn is gone.
 */
public class EchoCairnBlock extends Block implements EntityBlock {
	public static final MapCodec<EchoCairnBlock> CODEC = simpleCodec(EchoCairnBlock::new);
	/** Outline and collision of a revealed cairn or cache. */
	private static final VoxelShape REVEALED_SHAPE = Block.box(4, 0, 4, 12, 8, 12);

	public EchoCairnBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(SiteStates.STYLE, SiteStates.CairnStyle.CAIRN)
				.setValue(SiteStates.VEIL, SiteStates.CairnVeil.VEILED));
	}

	@Override
	protected MapCodec<EchoCairnBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SiteStates.STYLE, SiteStates.VEIL);
	}

	/** Whether the cairn shows (its veil is lifted). */
	public static boolean revealed(BlockState state) {
		return state.getValue(SiteStates.VEIL) == SiteStates.CairnVeil.REVEALED;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return revealed(state) ? REVEALED_SHAPE : Shapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return revealed(state) ? REVEALED_SHAPE : Shapes.empty();
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return revealed(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!revealed(state)) {
			return InteractionResult.PASS;
		}

		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
				&& server.getBlockEntity(pos) instanceof EchoCairnBlockEntity cairn) {
			cairn.touch(server, serverPlayer);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new EchoCairnBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != SiteBlocks.ECHO_CAIRN_ENTITY) {
			return null;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<EchoCairnBlockEntity>) EchoCairnBlockEntity::serverTick;
	}
}
