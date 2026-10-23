package com.oldrepublic.planet.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.planet.PlanetBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The carved heart of a Watch waystone (spec MILESTONE_4.md 3.4): {@code waystone} hums pale and
 * clean, {@code waystone_corrupted} carries dark veins. Unbreakable in survival. {@code facing} is
 * the stone's carved face; templates write it as north, so a placed stone's facing also tells the
 * rotation of its template ({@link WaystoneBlockEntity}).
 */
public class WaystoneBlock extends Block implements EntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final MapCodec<WaystoneBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.BOOL.fieldOf("corrupted").forGetter(WaystoneBlock::corrupted),
			propertiesCodec()
	).apply(i, WaystoneBlock::new));

	private final boolean corrupted;

	public WaystoneBlock(boolean corrupted, Properties properties) {
		super(properties);
		this.corrupted = corrupted;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	/** Whether this is the corrupted stone. */
	public boolean corrupted() {
		return corrupted;
	}

	@Override
	protected MapCodec<WaystoneBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WaystoneBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != PlanetBlocks.WAYSTONE_ENTITY) {
			return null;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<WaystoneBlockEntity>) WaystoneBlockEntity::serverTick;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!corrupted) {
			PlanetBlockAmbience.get().waystone(state, level, pos, random);
		}
	}
}
