package com.oldrepublic.crafting;

import com.oldrepublic.crafting.bench.BenchService;
import com.oldrepublic.crafting.bench.EngineeringBenchMenu;
import com.oldrepublic.crafting.item.Consumables;
import com.oldrepublic.crafting.network.BenchActionPayload;
import com.oldrepublic.crafting.network.SchematicSyncPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Milestone 4 server and common initialisation for the Engineering Bench, disciplines, the
 * schematic loader, salvage, consumables and tools (spec MILESTONE_4.md 7). Called by the lead from
 * {@code OldRepublicMod} after the Phase 0 contracts.
 */
public final class CraftingFeature {
	public static void init() {
		CraftingBlocks.init();
		CraftingItems.init();
		EngineeringBenchMenu.init();
		SchematicLoader.init();
		Consumables.init();
		CraftingDialogue.init();
		CraftingCommands.init();

		PayloadTypeRegistry.serverboundPlay().register(BenchActionPayload.TYPE, BenchActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SchematicSyncPayload.TYPE, SchematicSyncPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BenchActionPayload.TYPE, (payload, context) -> BenchService.handle(context.player(), payload));

		// Join and /reload: every client gets the server's schematics.
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) ->
				ServerPlayNetworking.send(player, new SchematicSyncPayload(SchematicLoader.serverJson())));
		// After BalanceConfig.reload (registered first, in OldRepublicMod).
		ServerLifecycleEvents.SERVER_STARTING.register(server -> CraftingBalance.applyQuality());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BenchService.clear();
			Consumables.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			BenchService.tick(server);
			Consumables.tick(server);
		});
		ServerPlayerEvents.LEAVE.register(BenchService::forget);
	}

	private CraftingFeature() {
	}
}
