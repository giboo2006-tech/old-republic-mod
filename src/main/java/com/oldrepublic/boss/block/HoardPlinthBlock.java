package com.oldrepublic.boss.block;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.boss.BossArenas;
import com.oldrepublic.holocron.HolocronService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The hoard plinth in the Reliquary's vault (spec 4.8, 8.2). Once the Collector is resolved and the
 * vault seal is open, using it opens the Archive choice ({@link HolocronService#openArchive}, W3).
 * Unbreakable in survival, no loot.
 */
public class HoardPlinthBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<HoardPlinthBlock> CODEC = simpleCodec(HoardPlinthBlock::new);

	public HoardPlinthBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
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
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
			if (BossArenas.isVaultOpen(server, pos)) {
				HolocronService.openArchive(serverPlayer, pos);
			} else {
				serverPlayer.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.vault_sealed"));
			}
		}

		return InteractionResult.SUCCESS;
	}
}
