package com.oldrepublic.planet.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.force.ForceAct;
import com.oldrepublic.force.ForceBlockContext;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.HymnalDoors;
import com.oldrepublic.world.WorldEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sealed plating (spec MILESTONE_4.md 3.4, 7.6): the old alloy that closes a greater tomb's sealed
 * crypt. It is unbreakable in survival; only the fusion cutter ({@link #cut}, after 60 ticks of use, W5's
 * tool) or a Force Push ({@link #push}, a {@code force_reactive} handler) opens it, and either
 * opens the whole connected sheet (at most {@link #MAX_SHEET} blocks) with the M3 seal-open look
 * and sound.
 * <p>
 * The Hymnal's two doors are plating too (MILESTONE_5.md 3.3.3): there a use opens the door for a
 * player the story lets through ({@link HymnalDoors}), and neither the cutter nor a push opens it.
 */
public class SealedPlatingBlock extends Block {
	public static final MapCodec<SealedPlatingBlock> CODEC = simpleCodec(SealedPlatingBlock::new);
	/** The largest sheet one cut or push opens. */
	public static final int MAX_SHEET = 32;

	public SealedPlatingBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<SealedPlatingBlock> codec() {
		return CODEC;
	}

	/**
	 * A use on the plating: a Hymnal door opens for a player the story lets through, or says why it
	 * stays shut ({@link HymnalDoors#use}). Elsewhere a use does nothing.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		return serverPlayer.isSpectator() ? InteractionResult.PASS : HymnalDoors.use(server, pos, serverPlayer);
	}

	/**
	 * The fusion cutter's path (W5): opens the sheet {@code pos} belongs to. Returns the blocks opened
	 * (0 when {@code pos} is not plating, or is the Hymnal's, which no cutter opens).
	 */
	public static int cut(ServerLevel level, BlockPos pos) {
		return HymnalDoors.inHymnal(level, pos) ? 0 : open(level, pos);
	}

	/** The Force Push handler: a push on plating opens its sheet (never the Hymnal's). */
	public static boolean push(ForceBlockContext ctx) {
		return ctx.act() == ForceAct.PUSH && !HymnalDoors.inHymnal(ctx.level(), ctx.pos()) && open(ctx.level(), ctx.pos()) > 0;
	}

	/** The connected plating cells from {@code pos} (6-neighbours), at most {@link #MAX_SHEET}; empty when {@code pos} is not plating. */
	public static List<BlockPos> sheet(ServerLevel level, BlockPos pos) {
		List<BlockPos> out = new ArrayList<>();

		if (!(level.getBlockState(pos).getBlock() instanceof SealedPlatingBlock)) {
			return out;
		}

		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(pos.immutable());
		seen.add(pos.immutable());

		while (!queue.isEmpty() && out.size() < MAX_SHEET) {
			BlockPos next = queue.poll();
			out.add(next);

			for (Direction direction : Direction.values()) {
				BlockPos side = next.relative(direction);

				if (seen.add(side) && level.getBlockState(side).getBlock() instanceof SealedPlatingBlock) {
					queue.add(side);
				}
			}
		}

		return out;
	}

	private static int open(ServerLevel level, BlockPos pos) {
		List<BlockPos> sheet = sheet(level, pos);

		if (sheet.isEmpty()) {
			return 0;
		}

		Vec3 centre = Vec3.ZERO;

		for (BlockPos cell : sheet) {
			level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			centre = centre.add(Vec3.atCenterOf(cell));
		}

		EffectEvents.around(level, WorldEffects.SEAL_OPEN, centre.scale(1.0 / sheet.size()), null, -1, sheet.size(), 0);
		return sheet.size();
	}
}
