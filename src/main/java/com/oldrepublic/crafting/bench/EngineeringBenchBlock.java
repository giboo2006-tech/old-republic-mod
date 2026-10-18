package com.oldrepublic.crafting.bench;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Engineering Bench (spec MILESTONE_4.md 7.1): a worktop with a vice, a welding arm and a
 * parts rack. Its block entity keeps the 9-slot parts tray (like a chest, shared by everyone who
 * uses the bench); using it opens {@link EngineeringBenchMenu}.
 */
public class EngineeringBenchBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<EngineeringBenchBlock> CODEC = simpleCodec(EngineeringBenchBlock::new);
	private static final Component TITLE = Component.translatable("block.oldrepublic.engineering_bench");

	public EngineeringBenchBlock(Properties properties) {
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
		// API-CHECK: UseOnContext#getHorizontalDirection (used the same way by LightsaberWorkbenchBlock).
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.openMenu(state.getMenuProvider(level, pos));
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof EngineeringBenchBlockEntity bench)) {
			return null;
		}

		return new SimpleMenuProvider((id, inventory, player) -> new EngineeringBenchMenu(id, inventory, bench.tray(), ContainerLevelAccess.create(level, pos), null), TITLE);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new EngineeringBenchBlockEntity(pos, state);
	}
}
