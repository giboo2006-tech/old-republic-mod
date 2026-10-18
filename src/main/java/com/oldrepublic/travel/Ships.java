package com.oldrepublic.travel;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Placing and finding a player's ship body (spec MILESTONE_4.md 4.2, 4.9). Phase 0 stubs with
 * frozen signatures; W3 writes the bodies.
 */
public final class Ships {
	/**
	 * Places the Chapter Four hulk and its ship cradle near {@code near} (4.2) and records it in
	 * {@link ShipRegistry}. An owner who already has a ship gets their existing record.
	 *
	 * @return the record, or null when no spot was found yet (the caller retries later)
	 */
	public static @Nullable ShipRecord placeHulk(ServerPlayer owner, BlockPos near) {
		return ShipRegistry.of(owner.level().getServer(), owner.getUUID()).orElse(null);
	}

	/**
	 * The command and test path (4.11): a repaired ship with a full tank beside the owner. Replaces
	 * any existing body; the hold is kept.
	 */
	public static ShipRecord give(ServerPlayer owner) {
		MinecraftServer server = owner.level().getServer();
		return ShipRegistry.of(server, owner.getUUID()).orElseGet(() -> {
			ShipRecord record = ShipRecord.hulk(owner.getUUID(), UUID.randomUUID(),
					GlobalPos.of(owner.level().dimension(), owner.blockPosition()), owner.getYRot(), "")
					.withHull(100).withFuel(4).withState(ShipState.READY);
			ShipRegistry.put(server, record);
			return record;
		});
	}

	/** Whether {@code p} is riding a ship body. */
	public static boolean isAboard(Player p) {
		return false;
	}

	/** The owner's ship body if it is loaded, else null. */
	public static @Nullable Entity body(MinecraftServer server, UUID owner) {
		return null;
	}

	private Ships() {
	}
}
