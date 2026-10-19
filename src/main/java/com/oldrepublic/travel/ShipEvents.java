package com.oldrepublic.travel;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The ship's server events (spec MILESTONE_4.md 4.6, 4.9, 12.2; MILESTONE_5.md 4.2, 4.10): riders
 * are put off before they are saved, never log in aboard, take no damage while the sequence or the
 * helm locks them in; flights, the helm, queued bodies and chunk tickets advance every tick; a
 * restart settles interrupted flights and ships left flying with nobody at the helm.
 */
final class ShipEvents {
	/** The camera sweep runs this often (ticks). */
	private static final int SWEEP_INTERVAL = 20;

	static void init() {
		// LEAVE fires at the head of PlayerList#remove, before the player is saved and before vanilla
		// removes a vehicle whose only player passenger is leaving: the ship is never saved with a rider.
		ServerPlayerEvents.LEAVE.register(ShipEvents::leaving);
		ServerPlayerEvents.JOIN.register(ShipEvents::joined);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ShipCamera.detach(newPlayer));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ShipEvents::allowDamage);
		ServerLifecycleEvents.SERVER_STARTED.register(Flights::recover);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		ServerTickEvents.END_SERVER_TICK.register(ShipEvents::tick);
	}

	/** A player leaving the server: off the ship and down beside its pad (4.9, MILESTONE_5.md 4.10). */
	private static void leaving(ServerPlayer player) {
		if (player.getVehicle() instanceof ShipBody ship) {
			ShipRecord record = ShipRegistry.byBody(player.level().getServer(), ship.getUUID()).orElse(null);

			if (record != null && Helm.holds(record.owner())) {
				Helm.riderLeaving(player, record);
			} else {
				Flights.riderLeaving(player, ship);
			}
		}

		ShipCamera.detach(player);
		CombatLock.forget(player.getUUID());
		ShipActions.forget(player.getUUID());
		TravelDialogue.forget(player.getUUID());
	}

	/**
	 * A player joining: riders log in on the ground beside the ship (4.9). Only a crash can save a
	 * player aboard; vanilla then brings the body from their file, which is put off at once and
	 * left to the body's own check (a stale copy discards itself).
	 */
	private static void joined(ServerPlayer player) {
		ShipActions.remember(player);

		if (player.getVehicle() instanceof ShipBody ship) {
			ShipRecord record = ShipRegistry.byBody(player.level().getServer(), ship.getUUID()).orElse(null);
			player.stopRiding();

			if (record != null) {
				Ships.placeBesidePad(player, record, false);
			}
		}

		ShipCamera.detach(player);
	}

	/** Riders take no damage from the Spool to the end of the Settle, and at the helm, except {@code /kill} (12.2). */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		return !(entity instanceof Player && entity.getVehicle() instanceof ShipBody ship && ship.locksRiders()
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
	}

	private static void tick(MinecraftServer server) {
		Flights.tick(server);
		Helm.tick(server);
		Ships.tickPending(server);
		TravelTickets.tick(server);

		if (server.getTickCount() % SWEEP_INTERVAL == 0) {
			ShipCamera.sweep(server);
		}
	}

	/** Forgets everything kept in memory (an integrated server stopped; the next world starts clean). */
	private static void clear() {
		Flights.clear();
		Helm.clear();
		Ships.clear();
		Pads.clear();
		TravelTickets.clear();
		CombatLock.clear();
		ShipActions.clear();
		TravelDialogue.clear();
	}

	private ShipEvents() {
	}
}
