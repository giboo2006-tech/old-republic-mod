package com.oldrepublic.planet.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sunstone ore in Ghessar's canyon walls (spec MILESTONE_5.md 3.2), between y 40 and 120: terracotta
 * and red sandstone shot with seams of a hot amber stone. Needs an iron pickaxe; drops 1 to 2
 * {@code sunstone}, and Fortune applies (its loot table). Where a seam meets the air it shimmers
 * now and then, like heat over rock.
 */
public class SunstoneOreBlock extends Block {
	public static final MapCodec<SunstoneOreBlock> CODEC = simpleCodec(SunstoneOreBlock::new);

	public SunstoneOreBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<SunstoneOreBlock> codec() {
		return CODEC;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PlanetBlockAmbience.get().sunstone(state, level, pos, random);
	}
}
