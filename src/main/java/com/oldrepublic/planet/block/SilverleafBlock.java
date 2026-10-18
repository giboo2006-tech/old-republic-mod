package com.oldrepublic.planet.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Silverleaf (spec MILESTONE_4.md 3.2): a low, pale-silver herb of the Selvaren highwood that grows
 * in the shade under the trees, on grass, dirt, podzol and moss. A Medtech ingredient.
 */
public class SilverleafBlock extends PlanetPlantBlock {
	public static final MapCodec<SilverleafBlock> CODEC = simpleCodec(SilverleafBlock::new);

	public SilverleafBlock(Properties properties) {
		super(10, 9, properties);
	}

	@Override
	protected MapCodec<SilverleafBlock> codec() {
		return CODEC;
	}

	@Override
	protected boolean mayPlaceOn(BlockState ground) {
		return ground.is(BlockTags.DIRT);
	}
}
