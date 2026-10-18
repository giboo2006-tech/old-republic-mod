package com.oldrepublic.planet.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.planet.PlanetBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Heartstone (spec MILESTONE_4.md 3.4, 8.4): the great waystone the Greenwatch was built to
 * keep, its dark veins thick as roots. Unbreakable. Its {@link HeartstoneBlockEntity} remembers who
 * has cleansed it; the cleansing itself is the story's ({@code Heartstones}).
 */
public class HeartstoneBlock extends Block implements EntityBlock {
	public static final MapCodec<HeartstoneBlock> CODEC = simpleCodec(HeartstoneBlock::new);

	public HeartstoneBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<HeartstoneBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HeartstoneBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != PlanetBlocks.HEARTSTONE_ENTITY) {
			return null;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<HeartstoneBlockEntity>) HeartstoneBlockEntity::serverTick;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PlanetBlockAmbience.get().heartstone(state, level, pos, random);
	}
}
