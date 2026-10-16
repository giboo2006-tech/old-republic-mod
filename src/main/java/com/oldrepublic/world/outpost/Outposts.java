package com.oldrepublic.world.outpost;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Republic and Imperial outposts near the player (spec 3.5): worldgen first, then a runtime outpost
 * placed through {@code RuntimeSites}. Signatures are frozen (spec 15.1).
 */
public final class Outposts {
	/** What {@link #ensureNear} does for one army. */
	public enum Decision {
		/** Use the worldgen outpost within {@code sites.outpostWorldgenRadius}. */
		WORLDGEN,
		/** Reuse the runtime outpost within {@code sites.outpostReuseRadius}. */
		REUSE,
		/** Place a new runtime outpost. */
		PLACE,
		/** No outpost is near and no new one may be placed: use the nearest existing outpost, at any distance. */
		CAP_NEAREST
	}

	/**
	 * What {@link #decide} looks at, for one army.
	 *
	 * @param player         the player's position
	 * @param worldgen       the nearest worldgen outpost the locator found, or null
	 * @param runtimeNearest the nearest runtime outpost of this army at any distance, or null
	 * @param runtimeCount   the runtime outposts in the world, both armies together
	 */
	public record OutpostQuery(BlockPos player, @Nullable BlockPos worldgen, @Nullable BlockPos runtimeNearest, int runtimeCount) {
	}

	/**
	 * The pure rule of 3.5 (a test seam): a worldgen outpost within {@code outpostWorldgenRadius},
	 * else a runtime one within {@code outpostReuseRadius}, else a new one unless runtime outposts are
	 * off or {@code maxRuntimeOutposts} exist. Distances are horizontal and inclusive.
	 */
	public static Decision decide(OutpostQuery q, SiteBalance b) {
		if (q.worldgen() != null && within(q.player(), q.worldgen(), b.outpostWorldgenRadius)) {
			return Decision.WORLDGEN;
		}

		if (q.runtimeNearest() != null && within(q.player(), q.runtimeNearest(), b.outpostReuseRadius)) {
			return Decision.REUSE;
		}

		if (!b.runtimeOutposts || q.runtimeCount() >= b.maxRuntimeOutposts) {
			return Decision.CAP_NEAREST;
		}

		return Decision.PLACE;
	}

	/**
	 * Makes sure each army has an outpost the player can reach (3.5): asks {@link #decide} per army
	 * and places a runtime outpost when it says so. Phase 0 stub: W1 fills this in.
	 */
	public static void ensureNear(ServerPlayer p) {
	}

	/**
	 * The bounty terminal of {@code army}'s nearest outpost within {@code radius} blocks, or null.
	 * Phase 0 stub: W1 fills this in.
	 */
	public static @Nullable BlockPos nearestTerminal(ServerPlayer p, Faction army, int radius) {
		return null;
	}

	private static boolean within(BlockPos from, BlockPos to, int radius) {
		long dx = to.getX() - from.getX();
		long dz = to.getZ() - from.getZ();
		return dx * dx + dz * dz <= (long) radius * radius;
	}

	private Outposts() {
	}
}
