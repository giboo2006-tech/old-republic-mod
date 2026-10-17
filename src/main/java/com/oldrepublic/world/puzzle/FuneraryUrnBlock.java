package com.oldrepublic.world.puzzle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sealed clay funerary urn along a tomb wall (spec 3.3). Breaking it gives 1–2 {@code grave_silk}
 * and sometimes a bone (its loot table). Unlike an {@link EchoUrnBlock} it plays no part in a puzzle.
 */
public class FuneraryUrnBlock extends Block {
	public static final MapCodec<FuneraryUrnBlock> CODEC = simpleCodec(FuneraryUrnBlock::new);
	private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 12, 12);

	public FuneraryUrnBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<FuneraryUrnBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
