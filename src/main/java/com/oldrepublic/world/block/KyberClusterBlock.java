package com.oldrepublic.world.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decorative kyber formation (light 6). Not the quest crystal: it drops only itself with Silk
 * Touch and nothing otherwise, so caves cannot be farmed. Its client ambience (motes, glints,
 * the resonance loop of large formations) is driven from {@link #animateTick}.
 */
public class KyberClusterBlock extends CrystalFormationBlock {
	public static final MapCodec<KyberClusterBlock> CODEC = simpleCodec(KyberClusterBlock::new);

	public KyberClusterBlock(Properties properties) {
		super(7, 3, properties);
	}

	@Override
	protected MapCodec<KyberClusterBlock> codec() {
		return CODEC;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		WorldBlockAmbience.get().cluster(state, level, pos, random, false);
	}
}
