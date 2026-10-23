package com.oldrepublic.world.site;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

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
 * The spawn point of a site's garrison (spec 3.3): invisible, without collision or occlusion, and
 * unbreakable. Its {@link SiteSpawnerBlockEntity} spawns the group once, the first time a player
 * comes near with the entities loaded, then turns the cell into its replace-with block.
 */
public class SiteSpawnerBlock extends Block implements EntityBlock {
	public static final MapCodec<SiteSpawnerBlock> CODEC = simpleCodec(SiteSpawnerBlock::new);

	public SiteSpawnerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<SiteSpawnerBlock> codec() {
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
		return new SiteSpawnerBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != SiteBlocks.SITE_SPAWNER_ENTITY) {
			return null;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<SiteSpawnerBlockEntity>) SiteSpawnerBlockEntity::serverTick;
	}
}
