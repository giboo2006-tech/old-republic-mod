package com.oldrepublic.boss.block;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.world.WorldParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A resonance pylon of the Reliquary (spec 8.2, 8.3): a stone and bronze base with a singing crystal
 * cluster on top. The fight sets {@link #CRYSTAL}; the crystal itself is hit through the
 * {@code pylon_focus} entity that sits on it while it sings or regrows. Unbreakable in survival, no
 * loot. A singing crystal sheds a slow, steady glint (never a flicker).
 */
public class ResonancePylonBlock extends Block {
	public static final MapCodec<ResonancePylonBlock> CODEC = simpleCodec(ResonancePylonBlock::new);
	public static final EnumProperty<PylonCrystal> CRYSTAL = EnumProperty.create("crystal", PylonCrystal.class);

	public ResonancePylonBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(CRYSTAL, PylonCrystal.DORMANT));
	}

	@Override
	protected MapCodec<ResonancePylonBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CRYSTAL);
	}

	/** Block light of each crystal state: the static hall light is 7, a singing crystal adds its own. */
	public static int lightLevel(BlockState state) {
		return switch (state.getValue(CRYSTAL)) {
			case SINGING -> 9;
			case REGROWING -> 5;
			case DORMANT -> 3;
			default -> 0;
		};
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PylonCrystal crystal = state.getValue(CRYSTAL);

		if (crystal != PylonCrystal.SINGING && crystal != PylonCrystal.REGROWING) {
			return;
		}

		if (random.nextInt(crystal == PylonCrystal.SINGING ? 3 : 6) == 0) {
			double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			double y = pos.getY() + 1.05 + random.nextDouble() * 0.45;
			double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			level.addParticle(WorldParticles.CRYSTAL_GLINT, x, y, z, 0, 0.004, 0);
		}
	}
}
