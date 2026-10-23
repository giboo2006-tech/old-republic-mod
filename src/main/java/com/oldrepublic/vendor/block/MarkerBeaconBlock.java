package com.oldrepublic.vendor.block;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.quest.story.ChapterFourService;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Kaast's marker beacon (spec MILESTONE_4.md 8.6): a scrap post with a red lamp that blinks and
 * chirps now and then, left near the player as his challenge. Using it replays the challenge for the
 * player it was left for ({@link ChapterFourService#onMarkerUsed}); for anyone else it is scrap.
 */
public class MarkerBeaconBlock extends Block {
	public static final MapCodec<MarkerBeaconBlock> CODEC = simpleCodec(MarkerBeaconBlock::new);
	private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 14, 11);
	/** The lamp's colour (Kaast's red). */
	private static final int LAMP = 0xD0352B;

	public MarkerBeaconBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<MarkerBeaconBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			ChapterFourService.onMarkerUsed(serverPlayer, pos.immutable());
		}

		return InteractionResult.SUCCESS;
	}

	/** One small red blink at the lamp every second or so, and a quiet chirp now and then (client only). */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (level.getGameTime() % 20 < 2) {
			level.addParticle(new DustParticleOptions(LAMP, 0.7f), pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 0, 0, 0);
		}

		if (random.nextInt(160) == 0) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, ModSounds.SEEKER_CHIRP, SoundSource.BLOCKS, 0.35f, 0.8f, false);
		}
	}
}
