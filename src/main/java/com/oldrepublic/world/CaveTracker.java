package com.oldrepublic.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Per-player cave visits. Every {@link #INTERVAL} ticks checks which crystal cave piece the
 * player stands in. Entering any piece starts a visit and calls
 * {@link QuestHooks#onCaveEntered} once; the visit ends after {@link #EXIT_GRACE} ticks outside.
 * Being in an underground piece (descent or heart) switches the client's cave ambience on, with
 * the same grace period before it switches off.
 */
final class CaveTracker {
	static final int INTERVAL = 10;
	static final int EXIT_GRACE = 60;

	private static final class State {
		ResourceKey<Level> dimension;
		boolean visiting;
		int outsideTicks;
		boolean ambience;
		int surfaceTicks;
		/** -1 = the client does not know the flag yet (join, respawn, dimension change). */
		int sentAmbience = -1;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static int ticks;

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CaveTracker::tick);
		ServerPlayerEvents.LEAVE.register(player -> {
			STATES.remove(player.getUUID());
			CaveLocator.forget(player.getUUID());
		});
		ServerPlayerEvents.JOIN.register(player -> STATES.remove(player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			State state = STATES.get(newPlayer.getUUID());

			if (state != null) {
				state.sentAmbience = -1;
			}
		});
	}

	private static void tick(MinecraftServer server) {
		ticks++;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((ticks + (player.getId() & 0xFF)) % INTERVAL == 0) {
				update(player);
			}
		}
	}

	private static void update(ServerPlayer player) {
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		ResourceKey<Level> dimension = player.level().dimension();

		if (state.dimension != dimension) {
			state.dimension = dimension;
			state.visiting = false;
			state.ambience = false;
			state.sentAmbience = -1;
		}

		Identifier piece = player.isSpectator() ? null : CaveLocator.pieceAt(player.level(), player.blockPosition());
		boolean inside = piece != null;
		boolean underground = inside && !CaveLocator.CAMP_TEMPLATE.equals(piece);

		if (inside) {
			state.outsideTicks = 0;

			if (!state.visiting) {
				state.visiting = true;
				QuestHooks.onCaveEntered(player);
			}
		} else if (state.visiting) {
			state.outsideTicks += INTERVAL;

			if (state.outsideTicks >= EXIT_GRACE) {
				state.visiting = false;
			}
		}

		if (underground) {
			state.surfaceTicks = 0;
			state.ambience = true;
		} else if (state.ambience) {
			state.surfaceTicks += INTERVAL;

			if (state.surfaceTicks >= EXIT_GRACE) {
				state.ambience = false;
			}
		}

		int flag = state.ambience ? 1 : 0;

		if (flag != state.sentAmbience) {
			state.sentAmbience = flag;
			EffectEvents.toPlayer(player, WorldEffects.CAVE_AMBIENCE, player.position(), null, player.getId(), 1f, flag);
		}
	}

	private CaveTracker() {
	}
}
