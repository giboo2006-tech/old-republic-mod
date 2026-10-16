package com.oldrepublic.identity;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Spawning Frontier Settlers for story encounters. */
public final class Settlers {
	/**
	 * Spawns one captive settler bound at each post, owned by {@code owner} and tagged with
	 * {@code groupId} so the captives can be resolved together.
	 *
	 * @return the spawned settlers (Phase 0 stub: none)
	 */
	public static List<Entity> spawnCaptives(ServerLevel l, List<BlockPos> posts, UUID owner, String groupId) {
		// Phase 0 stub; the identity feature implements the Frontier Settler.
		return List.of();
	}

	private Settlers() {
	}
}
