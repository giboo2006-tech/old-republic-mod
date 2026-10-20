package com.oldrepublic.client.vendor;

import com.oldrepublic.vendor.hub.HubEntities;
import com.oldrepublic.vendor.network.VendorStockPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;

/**
 * Milestone 4 client initialisation for the vendors and the hub people (spec MILESTONE_4.md 7.8):
 * the vendor screen opened and refreshed by {@code vendor_stock}, and the renderers of the five hub
 * person types (the mentors' model layer, registered by the mentor client). Owned by W2 story,
 * vendors and economy; the lead calls it from {@code OldRepublicModClient}, so the worker never
 * edits the client entry point.
 *
 * <p>Milestone 5 (3.3, 18): Vessba the Hutt is her own entity with her own model and renderer
 * ({@link VessbaModel}, {@link VessbaRenderer}); every other new hub person is a hub host or
 * vendor variant drawn by {@link HubNpcRenderer}.
 */
public final class VendorClient {
	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(VendorStockPayload.TYPE, (payload, context) -> show(context.client(), payload));

		EntityRendererRegistry.register(HubEntities.HUB_HOST, HubNpcRenderer::new);
		EntityRendererRegistry.register(HubEntities.APPRENTICE, HubNpcRenderer::new);
		EntityRendererRegistry.register(HubEntities.ACOLYTE_HUB, HubNpcRenderer::new);
		EntityRendererRegistry.register(HubEntities.PROVING_GUARD, HubNpcRenderer::new);
		EntityRendererRegistry.register(HubEntities.VENDOR, HubNpcRenderer::new);
		ModelLayerRegistry.registerModelLayer(VessbaModel.LAYER, VessbaModel::createBodyLayer);
		EntityRendererRegistry.register(HubEntities.VESSBA, VessbaRenderer::new);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HubNpcRenderer.clearCache());
	}

	/** Opens the vendor screen, or refreshes the open one of the same vendor in place. */
	private static void show(Minecraft mc, VendorStockPayload payload) {
		if (mc.gui.screen() instanceof VendorScreen screen && screen.isFor(payload)) {
			screen.show(payload);
			return;
		}

		mc.gui.setScreen(new VendorScreen(payload));
	}

	private VendorClient() {
	}
}
