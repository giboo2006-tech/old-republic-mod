package com.oldrepublic.world.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.world.WorldBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Hazard crystal. A player who lingers next to it makes it charge (a clear 1.2 s tell) and then
 * discharge, hurting every living thing in range. All logic is server-side in
 * {@link UnstableClusterBlockEntity}; {@link #CHARGING} drives the brighter texture and light.
 */
public class UnstableCrystalClusterBlock extends CrystalFormationBlock implements EntityBlock {
	public static final MapCodec<UnstableCrystalClusterBlock> CODEC = simpleCodec(UnstableCrystalClusterBlock::new);
	public static final BooleanProperty CHARGING = BooleanProperty.create("charging");

	public UnstableCrystalClusterBlock(Properties properties) {
		super(7, 3, properties);
		registerDefaultState(defaultBlockState().setValue(CHARGING, false));
	}

	@Override
	protected MapCodec<UnstableCrystalClusterBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(CHARGING);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new UnstableClusterBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != WorldBlocks.UNSTABLE_CLUSTER_ENTITY) {
			return null;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<UnstableClusterBlockEntity>) UnstableClusterBlockEntity::serverTick;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		WorldBlockAmbience.get().cluster(state, level, pos, random, true);
	}
}
