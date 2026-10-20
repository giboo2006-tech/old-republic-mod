package com.oldrepublic.space;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Milestone 5 server and common initialisation for space and ship combat (section 5): the five
 * orbits, ship weapons, heat and missiles, shields, the enemy starfighters, patrols and the
 * blockade, the Silent Spindle, space bounties and space objectives (spec MILESTONE_5.md). Owned by
 * W2 space; the lead calls it from {@code OldRepublicMod} after the Phase 0 contracts, so the
 * worker never edits the mod's entry point. Register this feature's own items, blocks, entities,
 * particles, payloads, balance section, events and commands from here.
 */
public final class SpaceFeature {
	public static void init() {
		SpaceEntities.init();
		SpaceParticles.init();
		SpaceData.init();
		SilentSpindle.init();
		Registry.register(BuiltInRegistries.FEATURE, OldRepublicMod.id("asteroid"), new AsteroidFeature());
		SpaceNetwork.init();

		// The guns fire before the shields recharge, then the encounters, the objectives, the floor and the station.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ShipWeapons.tick(server);
			ShipCombat.tick(server);
			SpaceEncounters.tick(server);
			SpaceMissions.tick(server);
			OrbitFloor.tick(server);
			SilentSpindle.tick(server);
		});
		ServerPlayerEvents.LEAVE.register(SpaceFeature::leave);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && !level.isClientSide() && player instanceof ServerPlayer p) {
				return SilentSpindle.onUse(p, hit.getBlockPos());
			}

			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		SpaceCommands.init();
	}

	/** A player leaving the server: their encounter ends, they leave the others, and their moment-to-moment state goes. */
	private static void leave(ServerPlayer p) {
		SpaceEncounters.onLeave(p);
		ShipWeapons.forget(p.getUUID());
		ShipCombat.forget(p.getUUID());
		SpaceMissions.forget(p.getUUID());
		OrbitFloor.forget(p.level().getServer(), p.getUUID());
	}

	/** Ends every encounter and forgets the shields, the guns, the objectives' state and the floor's moves (the reset command, tests). */
	static void reset(MinecraftServer server) {
		SpaceEncounters.reset(server);
		ShipCombat.reset();
		ShipWeapons.reset();
		SpaceMissions.reset();
		OrbitFloor.reset(server);
	}

	/** Forgets everything kept in memory (an integrated server stopped; the next world starts clean). */
	private static void clear() {
		SpaceEncounters.clear();
		ShipCombat.reset();
		ShipWeapons.reset();
		SpaceMissions.reset();
		OrbitFloor.clear();
	}

	private SpaceFeature() {
	}
}
