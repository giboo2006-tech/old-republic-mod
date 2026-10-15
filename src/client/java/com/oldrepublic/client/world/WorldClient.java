package com.oldrepublic.client.world;

import com.oldrepublic.world.WorldEntities;
import com.oldrepublic.world.block.WorldBlockAmbience;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client initialisation for the world feature. Called from OldRepublicModClient.
 *
 * <p>No render-layer registration is needed: in 26.2 block models pick their chunk layer from
 * texture transparency, so the cluster cutouts and the translucent seal work as-is.
 */
public final class WorldClient {
	public static void init() {
		WorldParticleProviders.init();
		WorldEffectHandlers.init();
		EntityRendererRegistry.register(WorldEntities.SHADOW_ECHO, ShadowEchoRenderer::new);
		WorldBlockAmbience.set(WorldAmbience.INSTANCE);
		ClientTickEvents.END_CLIENT_TICK.register(WorldAmbience.INSTANCE::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(WorldAmbience.INSTANCE::reset));
	}

	private WorldClient() {
	}
}
