package com.oldrepublic.vendor;

import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.vendor.network.VendorBuyPayload;
import com.oldrepublic.vendor.network.VendorStockPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Milestone 4 server and common initialisation for vendors, their stock loader and the vendor
 * screen's payloads (spec MILESTONE_4.md 7.7, 7.8), the story's items and blocks
 * ({@link VendorItems}), the hub people ({@link HubNpcs}) and enemy credit drops
 * ({@link CreditDrops}). Owned by W2; the lead calls it from {@code OldRepublicMod}.
 */
public final class VendorFeature {
	/** Balance section {@code vendors} (14). */
	public static VendorBalance balance() {
		return BalanceConfig.section("vendors", VendorBalance.class, VendorBalance::new);
	}

	public static void init() {
		VendorItems.init();
		HubNpcs.init();
		VendorStocks.init();

		PayloadTypeRegistry.clientboundPlay().register(VendorStockPayload.TYPE, VendorStockPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VendorBuyPayload.TYPE, VendorBuyPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(VendorBuyPayload.TYPE, (payload, context) -> Vendors.handle(context.player(), payload));

		ServerLivingEntityEvents.AFTER_DEATH.register(CreditDrops::onDeath);
		ServerPlayerEvents.LEAVE.register(player -> Vendors.forget(player.getUUID()));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> Vendors.forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Vendors.clear());
	}

	private VendorFeature() {
	}
}
