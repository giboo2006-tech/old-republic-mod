package com.oldrepublic.client.planet;

import com.oldrepublic.planet.block.PlanetBlockAmbience;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Milestone 4 client initialisation of the planets (W1 worlds), called by the lead from
 * {@code OldRepublicModClient}: the planet particles, the planet effect events, the block display
 * ticks, and the client tick and disconnect reset of {@link PlanetAmbience} (biome beds, ashfall,
 * corrupted stones). Hub beds go through {@code client.world.site.SiteAmbience}.
 *
 * <p>No render-layer registration is needed: in 26.2 block models pick their chunk layer from
 * texture transparency. Selvaren's second moon is cut (spec MILESTONE_4.md 1, cut item 1).
 */
public final class PlanetClient {
	public static void init() {
		PlanetParticleProviders.init();
		PlanetEffectHandlers.init();
		PlanetBlockAmbience.set(PlanetAmbience.INSTANCE);
		ClientTickEvents.END_CLIENT_TICK.register(PlanetAmbience.INSTANCE::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(PlanetAmbience.INSTANCE::reset));
	}

	private PlanetClient() {
	}
}
