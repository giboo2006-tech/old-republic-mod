package com.oldrepublic.mission;

import java.util.UUID;

import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.network.MissionNetworking;
import com.oldrepublic.quest.story.StoryTags;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server and common initialisation for the mission feature (spec section 5): the attachment, the
 * items, the definitions, the payloads, dialogue conditions and actions, the Calling providers,
 * the camp props, the kneel, the commands, and the events the missions listen to. Called from
 * OldRepublicMod.
 */
public final class MissionFeature {
	/** The {@code missions} section of {@code balance.json}; server side only. */
	public static MissionBalance balance() {
		return BalanceConfig.section("missions", MissionBalance.class, MissionBalance::new);
	}

	public static void init() {
		MissionAttachments.init();
		MissionItems.init();
		MissionLoader.init();
		MissionNetworking.init();
		MissionDialogue.register();
		MissionGuidance.register();
		MissionInteractions.register();
		Tending.register();
		MissionCommands.register();

		ServerTickEvents.END_SERVER_TICK.register(MissionFeature::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(MissionFeature::onDeath);
		ServerEntityEvents.ENTITY_LOAD.register(MissionService::onEntityLoad);
		ServerPlayerEvents.LEAVE.register(player -> forget(player, player.level()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> forget(oldPlayer, oldPlayer.level()));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> forget(player, origin));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MissionService.clear();
			MissionBoard.clear();
			MissionGuidance.clear();
			StoryMissions.clear();
			Cameos.clear();
			Tending.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		MissionService.tick(server);

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Tending.tick(player);
		}
	}

	/**
	 * A mission entity died, whoever killed it: a fighter counts toward its owner's objective, a
	 * chief's death clears the way to the item, and a sergeant dying undecided ends the fight
	 * without a choice. The owner must be online; the safety recount covers the rest.
	 */
	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!MissionSites.isMember(entity) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		UUID ownerId = StoryTags.owner(entity);
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		Active active = owner == null ? null : MissionService.get(owner).active;

		if (active == null || !active.id.equals(MissionSites.missionOf(entity))) {
			return;
		}

		if (MissionSites.hasRole(entity, MissionSites.ROLE_ENEMY)) {
			MissionService.trigger(owner, Objectives.Trigger.KILL, null);
		} else if (MissionSites.hasRole(entity, MissionSites.ROLE_CHIEF)) {
			MissionService.markChiefDone(owner);
		} else if (MissionSites.hasRole(entity, MissionSites.ROLE_SERGEANT) && !active.has(MissionData.FLAG_CHIEF_DONE)) {
			MissionService.markChiefDone(owner);
			MissionService.trigger(owner, Objectives.Trigger.SERGEANT, null);
		}
	}

	/** Forgets a player's in-memory mission state; their cameo settler leaves {@code level}. */
	private static void forget(ServerPlayer player, ServerLevel level) {
		UUID id = player.getUUID();
		MissionService.forget(id);
		MissionBoard.forget(id);
		MissionGuidance.forget(id);
		Tending.forget(id);
		Cameos.forget(player, level);
	}

	private MissionFeature() {
	}
}
