package com.oldrepublic.network;

import java.util.Collection;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server-side helpers to broadcast {@link EffectEventPayload}s to the right players. */
public final class EffectEvents {
	/** Default distance within which players receive positional effects. */
	public static final double DEFAULT_RANGE = 96;

	/** Sends to every player near {@code pos} (within {@link #DEFAULT_RANGE}). */
	public static void around(ServerLevel level, Identifier type, Vec3 pos, @Nullable Vec3 dir, int entityId, float intensity, int variant) {
		send(PlayerLookup.around(level, pos, DEFAULT_RANGE), new EffectEventPayload(type, pos, dir == null ? Vec3.ZERO : dir, entityId, intensity, variant));
	}

	/** Sends to the players tracking {@code entity} and, if it is a player, to that player too. */
	public static void toTracking(Entity entity, Identifier type, Vec3 pos, @Nullable Vec3 dir, int entityId, float intensity, int variant) {
		EffectEventPayload payload = new EffectEventPayload(type, pos, dir == null ? Vec3.ZERO : dir, entityId, intensity, variant);
		send(PlayerLookup.tracking(entity), payload);

		if (entity instanceof ServerPlayer self) {
			ServerPlayNetworking.send(self, payload);
		}
	}

	/** Sends only to one player (personal feedback such as level-ups or the dream). */
	public static void toPlayer(ServerPlayer player, Identifier type, Vec3 pos, @Nullable Vec3 dir, int entityId, float intensity, int variant) {
		ServerPlayNetworking.send(player, new EffectEventPayload(type, pos, dir == null ? Vec3.ZERO : dir, entityId, intensity, variant));
	}

	private static void send(Collection<ServerPlayer> players, EffectEventPayload payload) {
		for (ServerPlayer player : players) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private EffectEvents() {
	}
}
