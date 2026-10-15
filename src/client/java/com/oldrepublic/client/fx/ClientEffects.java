package com.oldrepublic.client.fx;

import java.util.HashMap;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEventPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Receives {@link EffectEventPayload}s and hands them to the handler registered for their type.
 * Each feature registers handlers for its own effect ids in its client init.
 */
public final class ClientEffects {
	@FunctionalInterface
	public interface Handler {
		/**
		 * @param entity the entity named by {@code payload.entityId()}, or null if unknown or not
		 *               loaded on this client
		 */
		void play(Minecraft client, ClientLevel level, EffectEventPayload payload, Entity entity);
	}

	private static final Map<Identifier, Handler> HANDLERS = new HashMap<>();

	public static void register(Identifier type, Handler handler) {
		if (HANDLERS.put(type, handler) != null) {
			OldRepublicMod.LOGGER.warn("Effect handler for {} replaced", type);
		}
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(EffectEventPayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			ClientLevel level = client.level;
			Handler handler = HANDLERS.get(payload.effect());

			if (level == null || handler == null) {
				return;
			}

			Entity entity = payload.entityId() >= 0 ? level.getEntity(payload.entityId()) : null;
			handler.play(client, level, payload, entity);
		});
	}

	private ClientEffects() {
	}
}
