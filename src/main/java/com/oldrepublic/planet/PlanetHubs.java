package com.oldrepublic.planet;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The shared hub of each planet (spec MILESTONE_4.md 3.5): placed once per server, the first time a
 * ship sets course for the planet. Phase 0 stub (W1 fills it): the hub is a flat pad at the surface
 * of (0, 0), with four bays around it.
 */
public final class PlanetHubs {
	/** The hub anchor (the pad centre) if the hub is placed; null before that, or for the home world. */
	public static @Nullable BlockPos hub(MinecraftServer server, Planets.Planet planet) {
		ServerLevel level = Planets.level(server, planet);

		if (level == null || planet == Planets.Planet.HOME) {
			return null;
		}

		return surface(level, BlockPos.ZERO);
	}

	/**
	 * Places the hub if it is not placed yet (3.5) and completes with its anchor, on the server thread.
	 * Never fails: when no ring fits, the hub stands on a raised platform at (0, 0).
	 */
	public static CompletableFuture<BlockPos> ensure(MinecraftServer server, Planets.Planet planet) {
		BlockPos hub = hub(server, planet);
		return CompletableFuture.completedFuture(hub != null ? hub : BlockPos.ZERO);
	}

	/** The pad bays a ship may land on, in order (4.5). Empty when the hub is not placed. */
	public static List<BlockPos> padBays(MinecraftServer server, Planets.Planet planet) {
		BlockPos hub = hub(server, planet);

		if (hub == null) {
			return List.of();
		}

		return List.of(hub.offset(-12, 0, -12), hub.offset(12, 0, -12), hub.offset(-12, 0, 12), hub.offset(12, 0, 12));
	}

	/** Where a player who died on this planet without a bed there respawns (12.2), or null. */
	public static @Nullable BlockPos medbay(MinecraftServer server, Planets.Planet planet) {
		BlockPos hub = hub(server, planet);
		return hub != null ? hub.offset(0, 0, 6) : null;
	}

	private static BlockPos surface(ServerLevel level, BlockPos column) {
		level.getChunk(column);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
	}

	private PlanetHubs() {
	}
}
