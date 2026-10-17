package com.oldrepublic.client.boss.sentinel;

import com.oldrepublic.enemy.construct.SentinelEntities;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Client initialisation for the Ruin Sentinel: its model layer and renderer, the
 * {@code lumen_spark} provider and the handlers of the five sentinel effect events. Called first
 * from {@code BossClient.init()}.
 */
public final class SentinelClient {
	private static ClientLevel lastLevel;

	public static void init() {
		ModelLayerRegistry.registerModelLayer(RuinSentinelModel.LAYER, RuinSentinelModel::createBodyLayer);
		EntityRendererRegistry.register(SentinelEntities.RUIN_SENTINEL, RuinSentinelRenderer::new);
		LumenSparkProvider.init();
		SentinelFx.init();
		ClientTickEvents.END_CLIENT_TICK.register(SentinelClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SentinelFx.clear());
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// Join or dimension change: entity ids from the old level are stale.
			lastLevel = mc.level;
			SentinelFx.clear();
		}

		SentinelFx.tick(mc);
	}

	private SentinelClient() {
	}
}
