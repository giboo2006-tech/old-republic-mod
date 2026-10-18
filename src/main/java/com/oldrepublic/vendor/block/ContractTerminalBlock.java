package com.oldrepublic.vendor.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.mission.Missions;
import com.oldrepublic.planet.Planets;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A hub's contract terminal (spec MILESTONE_4.md 3.5, 8.7): a console facing {@code facing}. Using
 * it opens the planet's contract board ({@code Missions.openPlanetBoard}) for the level it stands
 * in; anywhere but Selvaren or Varkhast it has no contracts.
 */
public class ContractTerminalBlock extends Block {
	public static final MapCodec<ContractTerminalBlock> CODEC = simpleCodec(ContractTerminalBlock::new);

	public ContractTerminalBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<ContractTerminalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.HORIZONTAL_FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			Planets.Planet planet = Planets.Planet.of(level.dimension());

			if (planet == null || planet == Planets.Planet.HOME) {
				serverPlayer.sendOverlayMessage(Component.translatable("vendor.oldrepublic.terminal.offline").withStyle(ChatFormatting.GRAY));
			} else {
				Missions.openPlanetBoard(serverPlayer, pos.immutable(), planet);
			}
		}

		return InteractionResult.SUCCESS;
	}
}
