package com.oldrepublic.world.block;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.world.trial.TrialManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The altar at the heart of the trial chamber. Using it begins the Trial of Reflection. */
public class MeditationStoneBlock extends Block {
	public static final MapCodec<MeditationStoneBlock> CODEC = simpleCodec(MeditationStoneBlock::new);
	/** Height of the altar top in blocks. The crystal forms just above it. */
	public static final double TOP = 10 / 16.0;
	private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(2, 4, 2, 14, 10, 14));

	public MeditationStoneBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<MeditationStoneBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			TrialManager.tryStart(serverPlayer, pos);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		WorldBlockAmbience.get().meditationStone(state, level, pos, random);
	}
}
