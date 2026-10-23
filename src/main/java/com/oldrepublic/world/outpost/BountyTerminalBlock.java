package com.oldrepublic.world.outpost;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.mission.Missions;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.world.site.SiteBlocks;
import com.oldrepublic.world.site.SiteEffects;
import com.oldrepublic.world.site.SiteStates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An outpost's bounty terminal (spec 3.3, 3.5): a full-cube console whose {@code facing} is the
 * screen side and whose {@code faction} is the outpost's army. Using it opens that army's mission
 * board ({@code Missions.openBoard}) with the {@code terminal_use} effect. Its block entity anchors
 * the outpost's officer and garrison ({@link BountyTerminalBlockEntity}).
 */
public class BountyTerminalBlock extends Block implements EntityBlock {
	public static final MapCodec<BountyTerminalBlock> CODEC = simpleCodec(BountyTerminalBlock::new);

	public BountyTerminalBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(SiteStates.FACTION, SiteStates.Army.REPUBLIC)
				.setValue(SiteStates.FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<BountyTerminalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SiteStates.FACTION, SiteStates.FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		// API-CHECK: UseOnContext#getHorizontalDirection (long-standing; used the same way by LightsaberWorkbenchBlock).
		return defaultBlockState().setValue(SiteStates.FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(SiteStates.FACING, rotation.rotate(state.getValue(SiteStates.FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(SiteStates.FACING)));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
			SiteStates.Army army = state.getValue(SiteStates.FACTION);
			Direction facing = state.getValue(SiteStates.FACING);
			Vec3 screen = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
			EffectEvents.around(server, SiteEffects.TERMINAL_USE, Vec3.atCenterOf(pos), screen, -1, 1f, army.ordinal());
			Missions.openBoard(serverPlayer, pos.immutable(), army.faction());
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BountyTerminalBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != SiteBlocks.BOUNTY_TERMINAL_ENTITY) {
			return null;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<BountyTerminalBlockEntity>) BountyTerminalBlockEntity::serverTick;
	}
}
