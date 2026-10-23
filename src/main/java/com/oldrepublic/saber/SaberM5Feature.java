package com.oldrepublic.saber;

import com.oldrepublic.crafting.LegendaryItems;
import com.oldrepublic.equipment.mod.LegendaryMods;
import com.oldrepublic.saber.network.OffhandStrikePayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Milestone 5 server and common initialisation for saber forms, dual-wielding, blade length,
 * crystal tuning and hum profiles (section 10), the Legendary tier and its ten components (section
 * 11), the robes and the mask (spec MILESTONE_5.md). Owned by W7 sabers, crafting and gear; the
 * lead calls it from {@code OldRepublicMod} after the Phase 0 contracts, so the worker never edits
 * the mod's entry point. Register this feature's own items, blocks, entities, particles, payloads,
 * balance section, events and commands from here.
 *
 * <p>Here: the legendary items ({@link LegendaryMods}, {@link LegendaryItems}; the saber parts and
 * the lens register with the M2 saber items), the {@code offhand_strike} payload and its server side
 * ({@link SaberDual}), the stance attachment ({@link SaberStances}) and the Attuned tuning's Force
 * modifier ({@link SaberTunings}). The forms' numbers, blade length and the other tunings live in the
 * M2 to M4 saber classes that already read them ({@link SaberForm}, {@link SaberStats}); the
 * Legendary tier in the M4 crafting classes ({@code Crafting}, {@code BenchService}, {@code Salvage},
 * {@code LegendaryDrops}).
 */
public final class SaberM5Feature {
	public static void init() {
		LegendaryMods.init();
		LegendaryItems.init();
		SaberStances.init();
		SaberTunings.init();

		PayloadTypeRegistry.serverboundPlay().register(OffhandStrikePayload.TYPE, OffhandStrikePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OffhandStrikePayload.TYPE, (payload, context) -> SaberDual.strike(context.player(), payload.targetId()));

		ServerPlayerEvents.LEAVE.register(SaberDual::forget);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			SaberDual.forget(oldPlayer);
			SaberDual.forget(newPlayer);
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> SaberDual.forget(player));
	}

	private SaberM5Feature() {
	}
}
