package com.oldrepublic.boss;

import com.oldrepublic.quest.BossOutcome;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The boss arenas' entry points for other features (spec section 8): who is inside a fight, and
 * resolving the Collector's fate. Signatures are frozen (spec section 15.1).
 */
public final class BossArenas {
	/** Whether the entity is inside an active boss arena. Phase 0 stub: W5 fills this in. */
	public static boolean isInArena(Entity e) {
		return false;
	}

	/**
	 * Resolves the owner's yielded boss with {@code outcome} and plays its scene (8.7). Phase 0 stub:
	 * W5 fills this in.
	 */
	public static void resolve(ServerPlayer owner, BossOutcome outcome) {
	}

	/** Whether the owner's boss has been resolved. Phase 0 stub: W5 fills this in. */
	public static boolean isResolved(ServerPlayer owner) {
		return false;
	}

	private BossArenas() {
	}
}
