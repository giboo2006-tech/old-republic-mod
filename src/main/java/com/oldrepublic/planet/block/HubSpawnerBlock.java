package com.oldrepublic.planet.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.planet.PlanetBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A hub's spawn point for one of its people (spec MILESTONE_4.md 3.5), the {@code site_spawner}
 * variant that never goes away: invisible, without collision, unbreakable. Its
 * {@link HubSpawnerBlockEntity} keeps its person alive through {@code HubSpawners}.
 */
public class HubSpawnerBlock extends Block implements EntityBlock {
	public static final MapCodec<HubSpawnerBlock> CODEC = simpleCodec(HubSpawnerBlock::new);

	public HubSpawnerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<HubSpawnerBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HubSpawnerBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != PlanetBlocks.HUB_SPAWNER_ENTITY) {
			return null;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<HubSpawnerBlockEntity>) HubSpawnerBlockEntity::serverTick;
	}
}
