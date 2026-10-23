package com.oldrepublic.planet.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.planet.VerseStones;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The singing heart of a verse stone (spec MILESTONE_5.md 3.3.2, 6.6): a dark monolith block with
 * faintly lit seams while its verse is {@link #AWAKE}. Unbreakable. Its
 * {@link VerseStoneBlockEntity} holds the owner, the stone's index, the guards and the channel;
 * {@link VerseStones} runs the rules. Holding the use key on it channels the silence: the client
 * repeats the use every 4 ticks while the key is held, and each use keeps the channel alive.
 * A silenced stone goes dark and stays so.
 */
public class VerseStoneBlock extends Block implements EntityBlock {
	public static final MapCodec<VerseStoneBlock> CODEC = simpleCodec(VerseStoneBlock::new);
	/** The verse still sings: the seams glow, the stone hums. False once silenced. */
	public static final BooleanProperty AWAKE = BooleanProperty.create("awake");

	public VerseStoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AWAKE, true));
	}

	@Override
	protected MapCodec<VerseStoneBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AWAKE);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!state.getValue(AWAKE)) {
			return InteractionResult.PASS;
		}

		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			// CONSUME, not SUCCESS: the held key repeats the use every 4 ticks, and the arm should not swing each time.
			return InteractionResult.CONSUME;
		}

		VerseStones.use(server, pos, serverPlayer);
		return InteractionResult.CONSUME;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VerseStoneBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != PlanetBlocks.VERSE_STONE_ENTITY) {
			return null;
		}

		if (level.isClientSide()) {
			return (BlockEntityTicker<T>) (BlockEntityTicker<VerseStoneBlockEntity>) VerseStoneBlockEntity::clientTick;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<VerseStoneBlockEntity>) VerseStoneBlockEntity::serverTick;
	}
}
