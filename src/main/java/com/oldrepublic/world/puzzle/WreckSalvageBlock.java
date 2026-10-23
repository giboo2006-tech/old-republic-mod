package com.oldrepublic.world.puzzle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A low heap of torn hull plates in a wreck field (spec 3.3). Breaking it gives 1–2
 * {@code durasteel_plate} (its loot table).
 */
public class WreckSalvageBlock extends Block {
	public static final MapCodec<WreckSalvageBlock> CODEC = simpleCodec(WreckSalvageBlock::new);
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 7, 15);

	public WreckSalvageBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<WreckSalvageBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
