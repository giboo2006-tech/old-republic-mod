package com.oldrepublic.planet.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ashroot (spec MILESTONE_4.md 3.3): a knotted, grey-red root that pushes up in the shade of
 * Varkhast's gullies, on sand, terracotta, coarse dirt and red sandstone. A Medtech ingredient.
 */
public class AshrootBlock extends PlanetPlantBlock {
	public static final MapCodec<AshrootBlock> CODEC = simpleCodec(AshrootBlock::new);

	public AshrootBlock(Properties properties) {
		super(10, 8, properties);
	}

	@Override
	protected MapCodec<AshrootBlock> codec() {
		return CODEC;
	}

	@Override
	protected boolean mayPlaceOn(BlockState ground) {
		return ground.is(BlockTags.SAND) || ground.is(BlockTags.TERRACOTTA) || ground.is(BlockTags.DIRT) || ground.is(Blocks.RED_SANDSTONE)
				|| ground.is(Blocks.GRAVEL) || ground.is(Blocks.TUFF);
	}
}
