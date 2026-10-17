package com.oldrepublic.mission;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.mission.MissionData.Active;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;

/**
 * The kneel over a mission's wounded (spec 5.4): sneak-use a downed trooper of your active mission
 * and stay crouched beside it. Each tick of the kneel goes through {@code Troopers.tend(p, e, false)}
 * (W4 counts kneel ticks once per game tick however many drivers call it, so the trooper's own
 * sneak-use kneel never doubles up), and the action bar shows how far along it is against
 * {@code missions.tendTicks}. Force Heal revives at once through W3's heal (within
 * {@code missions.healTendRange}). The trooper standing fires {@code onTrooperTended}.
 */
final class Tending {
	/** The kneel ends when the player is farther than this from the trooper. */
	private static final double KNEEL_REACH = 2.5;

	/** The trooper each player kneels over, in memory only. */
	private static final Map<UUID, UUID> KNEELING = new HashMap<>();

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && !level.isClientSide() && player instanceof ServerPlayer p && p.isShiftKeyDown()
					&& isMissionWounded(p, entity)) {
				KNEELING.put(p.getUUID(), entity.getUUID());
			}

			// The trooper's own use runs too and starts its kneel; the ticks are counted once.
			return InteractionResult.PASS;
		});
	}

	/** Whether {@code entity} is a downed trooper of the player's active mission. */
	static boolean isMissionWounded(ServerPlayer p, Entity entity) {
		Active active = MissionService.get(p).active;
		return active != null && Troopers.isDowned(entity) && MissionSites.hasRole(entity, MissionSites.ROLE_DOWNED)
				&& active.id.equals(MissionSites.missionOf(entity));
	}

	/** One tick of a player's kneel: tend, show the progress, end it when they stand up, move off or it rises. */
	static void tick(ServerPlayer p) {
		UUID id = KNEELING.get(p.getUUID());

		if (id == null) {
			return;
		}

		Entity wounded = p.level().getEntity(id);

		if (wounded == null || !Troopers.isDowned(wounded) || !p.isAlive() || !p.isShiftKeyDown() || p.distanceTo(wounded) > KNEEL_REACH) {
			KNEELING.remove(p.getUUID());
			return;
		}

		if (Troopers.tend(p, wounded, false)) {
			KNEELING.remove(p.getUUID());
			return;
		}

		int ticks = wounded instanceof TrooperEntity trooper ? trooper.tendProgress() : 0;
		int percent = Math.min(99, ticks * 100 / Math.max(1, MissionFeature.balance().tendTicks));
		p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.tending", percent));
	}

	/** Forgets a player's kneel (leave, respawn, level change). */
	static void forget(UUID player) {
		KNEELING.remove(player);
	}

	/** Forgets all in-memory state (server stopped). */
	static void clear() {
		KNEELING.clear();
	}

	private Tending() {
	}
}
