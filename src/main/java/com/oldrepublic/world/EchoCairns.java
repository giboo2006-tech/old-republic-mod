package com.oldrepublic.world;

import java.util.List;
import java.util.UUID;

import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Echo cairns (spec 3.3): veiled stone cairns or half-buried satchels that only their owner's Sense
 * reveals and only their owner can touch. Signatures are frozen (spec 15.1).
 */
public final class EchoCairns {
	/**
	 * Places {@code count} veiled cairns (or caches) owned by {@code owner} on natural ground between
	 * {@code minRadius} and {@code maxRadius} blocks of {@code centre}, at least {@code minSpacing}
	 * apart, and returns their positions (possibly fewer when the ground does not allow them).
	 * Phase 0 stub: W1 fills this in.
	 */
	public static List<BlockPos> place(ServerPlayer owner, BlockPos centre, int minRadius, int maxRadius, int minSpacing, int count, boolean cache) {
		return List.of();
	}

	/**
	 * Reveals the owner's cairn at {@code pos} for {@code ticks}; W2's Sense provider calls it.
	 * Phase 0 stub: W1 fills this in.
	 */
	public static void reveal(ServerLevel l, BlockPos pos, UUID owner, int ticks) {
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

	private EchoCairns() {
	}
}
