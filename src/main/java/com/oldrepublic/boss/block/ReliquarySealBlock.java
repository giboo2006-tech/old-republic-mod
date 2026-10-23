package com.oldrepublic.boss.block;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.boss.BossArenas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Reliquary seal (spec 8.2): the doorway seal between the antechamber and the hall, and the vault
 * seal in front of the hoard ({@link #VAULT}). The heart's session opens and closes them; an open seal
 * has no shape and no model. Unbreakable in survival, no loot.
 */
public class ReliquarySealBlock extends Block {
	public static final MapCodec<ReliquarySealBlock> CODEC = simpleCodec(ReliquarySealBlock::new);
	public static final BooleanProperty OPEN = BooleanProperty.create("open");
	public static final BooleanProperty VAULT = BooleanProperty.create("vault");

	public ReliquarySealBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(OPEN, true).setValue(VAULT, false));
	}

	@Override
	protected MapCodec<ReliquarySealBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OPEN, VAULT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
		return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
	}

	/** A closed seal tells the player why; a listed participant at the doorway seal is let back in. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(OPEN)) {
			return InteractionResult.PASS;
		}

		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
				&& (state.getValue(VAULT) || !BossArenas.letParticipantThrough(server, pos, serverPlayer))) {
			serverPlayer.sendOverlayMessage(Component.translatable(state.getValue(VAULT)
					? "boss.oldrepublic.heart.vault_sealed" : "boss.oldrepublic.heart.door_sealed"));
		}

		return InteractionResult.SUCCESS;
	}
}
