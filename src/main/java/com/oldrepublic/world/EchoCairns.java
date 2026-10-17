package com.oldrepublic.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.site.EchoCairnBlockEntity;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SiteBlocks;
import com.oldrepublic.world.site.SiteStates;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Echo cairns (spec 3.3): veiled stone cairns or half-buried satchels that only their owner's Sense
 * reveals and only their owner can touch. Signatures are frozen (spec 15.1).
 */
public final class EchoCairns {
	/** Random spots tried per cairn before {@link #place} settles for fewer. */
	private static final int ATTEMPTS_PER_CAIRN = 32;

	/**
	 * Places {@code count} veiled cairns (or caches) owned by {@code owner} on natural ground between
	 * {@code minRadius} and {@code maxRadius} blocks of {@code centre}, at least {@code minSpacing}
	 * apart, and returns their positions (possibly fewer when the ground does not allow them). Only
	 * loaded chunks are used. Index {@code i} of the result is the cairn's index for
	 * {@code QuestHooks.onCairnTouched}.
	 */
	public static List<BlockPos> place(ServerPlayer owner, BlockPos centre, int minRadius, int maxRadius, int minSpacing, int count, boolean cache) {
		ServerLevel level = owner.level();
		List<BlockPos> placed = new ArrayList<>();
		int inner = Math.max(0, Math.min(minRadius, maxRadius));
		int outer = Math.max(inner, Math.max(minRadius, maxRadius));
		RandomSource random = RandomSource.create(owner.getUUID().getLeastSignificantBits() ^ centre.asLong() ^ level.getGameTime());
		BlockState state = SiteBlocks.ECHO_CAIRN.defaultBlockState()
				.setValue(SiteStates.STYLE, cache ? SiteStates.CairnStyle.CACHE : SiteStates.CairnStyle.CAIRN);

		for (int attempt = 0; attempt < count * ATTEMPTS_PER_CAIRN && placed.size() < count; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = inner + random.nextDouble() * (outer - inner);
			int x = (int) Math.floor(centre.getX() + 0.5 + Math.cos(angle) * radius);
			int z = (int) Math.floor(centre.getZ() + 0.5 + Math.sin(angle) * radius);
			BlockPos spot = groundSpot(level, x, z);

			if (spot == null || tooClose(spot, placed, minSpacing)) {
				continue;
			}

			level.setBlock(spot, state, Block.UPDATE_ALL);

			if (level.getBlockEntity(spot) instanceof EchoCairnBlockEntity cairn) {
				cairn.setOwner(owner.getUUID(), placed.size());
				placed.add(spot);
			}
		}

		return placed;
	}

	/**
	 * Reveals the owner's cairn at {@code pos} for {@code ticks} (0 or less: {@code sites.cairnRevealTicks});
	 * W2's Sense provider calls it. Nothing happens for another owner, another block or an unloaded position.
	 */
	public static void reveal(ServerLevel l, BlockPos pos, UUID owner, int ticks) {
		if (!l.isLoaded(pos) || !(l.getBlockEntity(pos) instanceof EchoCairnBlockEntity cairn)) {
			return;
		}

		cairn.reveal(l, owner, ticks > 0 ? ticks : SiteBalance.get().cairnRevealTicks);
	}

	/**
	 * Lists the owner in {@link WorldSites}' expired cairn owners; their cairns remove themselves
	 * lazily on their own tick (3.3), so nothing writes into unloaded chunks.
	 */
	public static void expire(ServerPlayer owner) {
		MinecraftServer server = owner.level().getServer();
		WorldSites.Data data = WorldSites.snapshot(server);
		data.expireCairnOwner(owner.getUUID());
		WorldSites.commit(server, data);
	}

	/**
	 * The feet position of a cairn in column ({@code x}, {@code z}): natural, dry, sturdy ground
	 * ({@code #oldrepublic:story_ground}) under an air or plant cell that holds no built block. Null
	 * for an unloaded column or any other ground.
	 */
	private static @Nullable BlockPos groundSpot(ServerLevel level, int x, int z) {
		if (!level.hasChunkAt(x, z)) {
			return null;
		}

		BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		BlockPos below = pos.below();
		BlockState ground = level.getBlockState(below);

		if (!ground.is(MeetingSites.STORY_GROUND) || !ground.getFluidState().isEmpty() || !ground.isFaceSturdy(level, below, Direction.UP)) {
			return null;
		}

		BlockState here = level.getBlockState(pos);
		boolean free = (here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty() && !here.is(MeetingSites.BUILT_BLOCKS);
		return free ? pos : null;
	}

	private static boolean tooClose(BlockPos spot, List<BlockPos> placed, int minSpacing) {
		for (BlockPos other : placed) {
			double dx = other.getX() - spot.getX();
			double dz = other.getZ() - spot.getZ();

			if (dx * dx + dz * dz < (double) minSpacing * minSpacing) {
				return true;
			}
		}

		return false;
	}

	private EchoCairns() {
	}
}
