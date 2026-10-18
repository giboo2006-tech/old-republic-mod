package com.oldrepublic.travel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Clocks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The launch's combat lock (spec MILESTONE_4.md 4.6): no launch while a hostile within
 * {@code travel.combatLockRange} blocks of the ship has damaged the owner or a crew member in the
 * last {@code travel.combatLockTicks}. Hits on players are remembered for a few seconds in memory
 * only; a hit by the owner or another crew member does not count.
 */
final class CombatLock {
	/** One hit: who struck and when (game time). */
	private record Hit(UUID attacker, long time) {
	}

	/** Recent hits per victim, newest last; a few per player are plenty. */
	private static final int MAX_HITS = 8;
	private static final Map<UUID, Deque<Hit>> HITS = new HashMap<>();

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer victim && source.getEntity() instanceof LivingEntity attacker && attacker != victim) {
				record(victim, attacker);
			}
		});
	}

	private static void record(ServerPlayer victim, LivingEntity attacker) {
		Deque<Hit> hits = HITS.computeIfAbsent(victim.getUUID(), id -> new ArrayDeque<>());
		hits.addLast(new Hit(attacker.getUUID(), Clocks.time(victim.level().getServer())));

		while (hits.size() > MAX_HITS) {
			hits.removeFirst();
		}
	}

	/**
	 * Whether the ship is locked: a living attacker that is not owner or crew, within range of the
	 * ship (or of the owner when the body is not loaded), hit the owner or crew lately.
	 */
	static boolean inCombat(MinecraftServer server, ShipRecord record, @Nullable CourierShuttle ship) {
		ShipBalance.Travel t = ShipBalance.travel();

		if (!t.combatLock) {
			return false;
		}

		long now = Clocks.time(server);
		Entity centre = ship != null ? ship : server.getPlayerList().getPlayer(record.owner());

		if (centre == null || !(centre.level() instanceof ServerLevel level)) {
			return false;
		}

		for (Map.Entry<UUID, Deque<Hit>> entry : HITS.entrySet()) {
			if (!ShipRules.mayBoard(record, entry.getKey())) {
				continue;
			}

			for (Iterator<Hit> it = entry.getValue().iterator(); it.hasNext();) {
				Hit hit = it.next();

				if (now - hit.time() > t.combatLockTicks) {
					it.remove();
					continue;
				}

				Entity attacker = level.getEntity(hit.attacker());

				if (attacker instanceof LivingEntity living && living.isAlive() && !ShipRules.mayBoard(record, attacker.getUUID())
						&& attacker.distanceToSqr(centre) <= (double) t.combatLockRange * t.combatLockRange) {
					return true;
				}
			}
		}

		return false;
	}

	/** Forgets a player's hits (they left). */
	static void forget(UUID player) {
		HITS.remove(player);
	}

	static void clear() {
		HITS.clear();
	}

	private CombatLock() {
	}
}
