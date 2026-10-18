package com.oldrepublic.planet.block;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.planet.PlanetBalance;
import com.oldrepublic.planet.PlanetDamage;
import com.oldrepublic.world.block.CrystalFormationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cluster of black duskglass (spec MILESTONE_4.md 3.3, 8.4), grown on the shaded walls of
 * Varkhast's tomb valleys and lining the duskglass rift. It cuts: a living thing inside its cell
 * takes {@code planets.duskglassDamage} ({@code oldrepublic:duskglass_cut}), limited by the
 * victim's own hurt cooldown. Mined, it gives duskglass shards (its loot table).
 */
public class DuskglassClusterBlock extends CrystalFormationBlock {
	public static final MapCodec<DuskglassClusterBlock> CODEC = simpleCodec(DuskglassClusterBlock::new);

	public DuskglassClusterBlock(Properties properties) {
		super(7, 3, properties);
	}

	@Override
	protected MapCodec<DuskglassClusterBlock> codec() {
		return CODEC;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (level instanceof ServerLevel server && entity instanceof LivingEntity living && living.isAlive()) {
			living.hurtServer(server, server.damageSources().source(PlanetDamage.DUSKGLASS_CUT), PlanetBalance.get().duskglassDamage);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PlanetBlockAmbience.get().duskglass(state, level, pos, random);
	}
}
