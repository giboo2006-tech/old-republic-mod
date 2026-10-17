package com.oldrepublic.boss.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.boss.BossArenas;
import com.oldrepublic.boss.BossBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Reliquary heart (spec 8.2): the block in the antechamber that anchors the Collector's session.
 * Its {@link ReliquaryHeartBlockEntity} keeps the owner and the state; the server ticker drives the
 * fight through {@link BossArenas}. Using it opens the {@code reliquary_heart} dialogue (the rematch)
 * once the Collector is resolved. B's build sets it last, with {@link #FACING} turned like the hall.
 */
public class ReliquaryHeartBlock extends Block implements EntityBlock {
	public static final MapCodec<ReliquaryHeartBlock> CODEC = simpleCodec(ReliquaryHeartBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public ReliquaryHeartBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<ReliquaryHeartBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ReliquaryHeartBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != BossBlocks.RELIQUARY_HEART_ENTITY) {
			return null;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<ReliquaryHeartBlockEntity>) ReliquaryHeartBlockEntity::serverTick;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
			BossArenas.onHeartUsed(server, pos, serverPlayer);
		}

		return InteractionResult.SUCCESS;
	}

	/** A faint, steady warmth over the heart: a slow mote now and then, never a flicker. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(8) == 0) {
			level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
		}
	}
}
