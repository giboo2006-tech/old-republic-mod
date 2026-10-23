package com.oldrepublic.planet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.travel.TravelService;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Respawn on a planet (spec MILESTONE_4.md 12.2): a player who died on a planet respawns at their
 * bed when it is on that planet (vanilla); otherwise, with {@code planets.respawnAtHub}, they are
 * moved to that planet's hub medbay right after vanilla respawned them elsewhere. The move goes
 * through {@link TravelService#moveAcross} a tick later, with a chunk ticket held on the medbay
 * first (WORKER_M4 2).
 */
public final class PlanetRespawn {
	/** Ticks the medbay's ticket is given before the move. */
	private static final int SETTLE_TICKS = 2;

	private record Pending(ResourceKey<Level> planet, BlockPos medbay, long at) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	/** The respawn hook: queues the move when the player died on a planet and came back somewhere else. */
	static void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (alive || !Planets.isPlanet(oldPlayer.level()) || !PlanetBalance.get().respawnAtHub) {
			return;
		}

		ServerLevel died = oldPlayer.level();

		if (newPlayer.level().dimension().equals(died.dimension())) {
			return; // their bed is on this planet
		}

		Planets.Planet planet = Planets.Planet.of(died.dimension());
		BlockPos medbay = planet == null ? null : PlanetHubs.medbay(died.getServer(), planet);

		if (medbay == null) {
			return;
		}

		died.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(medbay), 1);
		PENDING.put(newPlayer.getUUID(), new Pending(died.dimension(), medbay, Clocks.time(died.getServer()) + SETTLE_TICKS));
	}

	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}

		long now = Clocks.time(server);

		for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Pending> entry = it.next();
			Pending pending = entry.getValue();

			if (now < pending.at()) {
				continue;
			}

			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			ServerLevel level = server.getLevel(pending.planet());

			if (player != null && level != null && player.isAlive()) {
				TravelService.moveAcross(player, level, Vec3.atBottomCenterOf(pending.medbay()), player.getYRot());
			}
		}
	}

	static void forget(UUID player) {
		PENDING.remove(player);
	}

	static void clear() {
		PENDING.clear();
	}

	private PlanetRespawn() {
	}
}
