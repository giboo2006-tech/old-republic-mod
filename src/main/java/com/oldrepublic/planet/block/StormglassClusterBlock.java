package com.oldrepublic.planet.block;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.world.block.CrystalFormationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cluster of stormglass on Tyrvann's basalt ridge tops (spec MILESTONE_5.md 3.3): smoky glass
 * fused by lightning, still holding a little of the strike. Worldgen grows it on exposed tops, and
 * half of the real strikes on exposed basalt grow a new one ({@code planets.stormglassChance},
 * {@code planet.RidgeLightning}). Mined it drops 1 to 2 {@code stormglass_shard}; with Silk Touch,
 * itself (its loot table). Now and then static crawls over it.
 */
public class StormglassClusterBlock extends CrystalFormationBlock {
	public static final MapCodec<StormglassClusterBlock> CODEC = simpleCodec(StormglassClusterBlock::new);

	public StormglassClusterBlock(Properties properties) {
		super(7, 3, properties);
	}

	@Override
	protected MapCodec<StormglassClusterBlock> codec() {
		return CODEC;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PlanetBlockAmbience.get().stormglass(state, level, pos, random);
	}
}
