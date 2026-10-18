package com.oldrepublic.planet;

import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.enemy.droid.Droids;
import com.oldrepublic.force.ForceBlockInteractions;
import com.oldrepublic.force.sense.SenseTargets;
import com.oldrepublic.planet.block.SealedPlatingBlock;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Milestone 4 server and common initialisation for the planets' blocks, worldgen hooks, hubs and planet sites (spec MILESTONE_4.md section 3). Owned by W1 worlds; the lead calls it
 * from {@code OldRepublicMod} after the Phase 0 contracts, so the worker never edits the mod's
 * entry point. Register this feature's own items, blocks, entities, particles, payloads, balance
 * section, events and commands from here.
 */
public final class PlanetFeature {
	public static void init() {
		PlanetBlocks.init();
		PlanetParticles.init();
		PlanetSiteData.init();
		PlanetWorldgen.init();

		SenseTargets.register(Waystones::collectSense);
		SenseTargets.register(Expeditions::collectSense);
		ForceBlockInteractions.register(PlanetBlocks.SEALED_PLATING, SealedPlatingBlock::push);
		// The Proving's patrol droid; W6 may register its own factory for the role, which replaces this one.
		HubSpawners.register("droid", (level, at, planet, role) -> Droids.spawnSecurity(level, at, null, true));
		DialogueConditions.register("planet:in", (player, speaker, arg) -> inPlanet(player, arg) ? null : "planet.oldrepublic.condition.in");

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			PlanetHubs.tick(server);
			Expeditions.tick(server);
			PlanetRespawn.tick(server);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register(PlanetRespawn::onRespawn);
		ServerEntityEvents.ALLOW_LOAD.register(PlanetHubs::allowLoad);
		ServerPlayerEvents.LEAVE.register(player -> {
			Expeditions.forget(player);
			PlanetSites.forget(player.getUUID());
			PlanetRespawn.forget(player.getUUID());
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && !level.isClientSide() && player instanceof ServerPlayer p) {
				return Expeditions.onUse(p, hit.getBlockPos());
			}

			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PlanetHubs.clear();
			PlanetSites.clear();
			Expeditions.clear();
			PlanetRespawn.clear();
		});
		PlanetCommands.init();
	}

	/** The {@code planet:in} dialogue condition (8.10): the player stands on {@code selvaren}, {@code varkhast} or {@code home}. */
	static boolean inPlanet(ServerPlayer player, String arg) {
		Planets.Planet planet = Planets.Planet.of(player.level().dimension());
		return planet != null && planet.id().equals(arg.trim());
	}

	private PlanetFeature() {
	}
}
