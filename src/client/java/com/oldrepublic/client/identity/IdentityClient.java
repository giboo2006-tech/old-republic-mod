package com.oldrepublic.client.identity;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.dialogue.DialogueClient;
import com.oldrepublic.identity.IdentityEntities;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Client initialisation for the identity feature (dialogue screen, alignment and reputation toasts,
 * the Standing panel, settler rendering). Called from OldRepublicModClient.
 */
public final class IdentityClient {
	public static void init() {
		ModelLayerRegistry.registerModelLayer(SettlerModel.LAYER, SettlerModel::createBodyLayer);
		EntityRendererRegistry.register(IdentityEntities.FRONTIER_SETTLER, SettlerRenderer::new);

		IdentityFx.init();
		DialogueClient.init();

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OldRepublicMod.id("identity_toasts"), IdentityToasts::extract);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> IdentityToasts.reset());
	}

	private IdentityClient() {
	}
}
