package com.oldrepublic.force;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who is charging a push and since when, on the server. Deliberately not persisted: a charge never
 * survives death, disconnect, dimension change or a restart.
 */
final class PushCharges {
	private static final Map<UUID, Long> STARTED = new HashMap<>();
	private static long ticks;

	static long now() {
		return ticks;
	}

	static boolean isCharging(ServerPlayer player) {
		return STARTED.containsKey(player.getUUID());
	}

	static void start(ServerPlayer player) {
		STARTED.put(player.getUUID(), ticks);
	}

	/** Removes the charge and returns how long it was held in ticks, or -1 if there was none. */
	static long finish(ServerPlayer player) {
		Long start = STARTED.remove(player.getUUID());
		return start == null ? -1 : ticks - start;
	}

	/** Cancels charges that became invalid: dead, spectating, using an item, or held too long. */
	static void tick(MinecraftServer server) {
		ticks++;

		if (STARTED.isEmpty()) {
			return;
		}

		int maxHold = ForcePush.balance().maxHoldTicks;
		Iterator<Map.Entry<UUID, Long>> it = STARTED.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = null;

			for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
				if (candidate.getUUID().equals(entry.getKey())) {
					player = candidate;
					break;
				}
			}

			if (player == null) {
				it.remove();
				continue;
			}

			boolean invalid = !player.isAlive() || player.isSpectator() || player.isUsingItem() || ticks - entry.getValue() > maxHold;

			if (invalid) {
				it.remove();
				ForcePush.sendStop(player);
			}
		}
	}

	static void remove(UUID id) {
		STARTED.remove(id);
	}

	static void clear() {
		STARTED.clear();
		ticks = 0;
	}

	private PushCharges() {
	}
}
