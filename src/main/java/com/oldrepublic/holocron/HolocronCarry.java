package com.oldrepublic.holocron;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.progression.ForceModifier;
import com.oldrepublic.progression.Progression;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The carried-holocron regen bonus (spec 4.6): +{@code carriedRegenPct}% Force regen per distinct,
 * owned, current-serial holocron in the main inventory, up to {@code carriedMax}, of the kinds the
 * player's path draws on. Fabric has no inventory-change event, so the count is cached per player
 * and refreshed from an inventory hash every {@code carryRefreshTicks}; a change re-syncs the
 * player's Force data through {@link Progression#refreshModifiers}.
 */
public final class HolocronCarry implements ForceModifier {
	/** The registered modifier. */
	public static final HolocronCarry INSTANCE = new HolocronCarry();

	private record Cached(int hash, int count) {
	}

	private static final Map<UUID, Cached> CACHE = new HashMap<>();

	private HolocronCarry() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(HolocronCarry::tick);
		ServerPlayerEvents.LEAVE.register(player -> CACHE.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> CACHE.clear());
	}

	@Override
	public float regenMultiplier(Player p) {
		if (!(p instanceof ServerPlayer player)) {
			return 1f;
		}

		return 1f + count(player) * HolocronBalance.get().carriedRegenPct / 100f;
	}

	/** The cached carry count, computed now when the player has none yet. */
	public static int count(ServerPlayer p) {
		Cached cached = CACHE.get(p.getUUID());
		return cached != null ? cached.count() : refresh(p);
	}

	/**
	 * Recomputes the player's carry count from the inventory now and caches it; re-syncs the Force
	 * data when it changed. Returns the new count.
	 */
	public static int refresh(ServerPlayer p) {
		HolocronBalance b = HolocronBalance.get();
		HolocronData d = HolocronService.data(p);
		Path path = Identity.path(p);
		List<HolocronStamp> stamps = HolocronRewards.mainInventoryStamps(p.getInventory());
		int hash = Objects.hash(stamps, d.serials, path, b.carriedMax);
		int count = HolocronRules.carryCount(stamps, p.getUUID(), d.serials, path, b.carriedMax);
		Cached before = CACHE.put(p.getUUID(), new Cached(hash, count));

		if (before != null && before.count() != count) {
			Progression.refreshModifiers(p);
		}

		return count;
	}

	private static void tick(MinecraftServer server) {
		int every = Math.max(1, HolocronBalance.get().carryRefreshTicks);

		if (server.getTickCount() % every != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Cached cached = CACHE.get(player.getUUID());

			if (cached == null || cached.hash() != hash(player)) {
				refresh(player);
			}
		}
	}

	private static int hash(ServerPlayer p) {
		HolocronData d = HolocronService.view(p);
		return Objects.hash(HolocronRewards.mainInventoryStamps(p.getInventory()), d == null ? Map.of() : d.serials, Identity.path(p),
				HolocronBalance.get().carriedMax);
	}
}
