package com.oldrepublic.travel;

import java.util.Optional;

import com.oldrepublic.ability.AbilityLocks;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The "not while flying" power lock (spec MILESTONE_4.md 9): no Force power while riding a ship
 * whose state is not {@link ShipState#READY}. Reads the registry, so it holds whatever the ship's
 * body does.
 */
public final class TravelLocks {
	static final String NOT_WHILE_FLYING = "message.oldrepublic.ability.not_while_flying";

	/** The ship state of the vehicle {@code p} rides, if it is a ship body. */
	public static Optional<ShipState> riddenShipState(ServerPlayer p) {
		Entity vehicle = p.getRootVehicle();

		if (vehicle == p) {
			return Optional.empty();
		}

		return ShipRegistry.byBody(p.level().getServer(), vehicle.getUUID()).map(ShipRecord::state);
	}

	static void init() {
		AbilityLocks.register((p, abilityId) -> riddenShipState(p).filter(state -> state != ShipState.READY).isPresent()
				? Component.translatable(NOT_WHILE_FLYING)
				: null);
	}

	private TravelLocks() {
	}
}
