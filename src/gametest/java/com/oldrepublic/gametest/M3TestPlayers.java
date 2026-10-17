package com.oldrepublic.gametest;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Listed mock players for Milestone 3 game tests (spec 15.1): unlike M2's {@code FakePlayer}, a
 * listed player is in {@code level.players()} and the server's player list, so proximity rules,
 * level ticks and join and leave events see it. Built on
 * {@link GameTestHelper#makeMockServerPlayerInLevel}.
 *
 * <p>Tests remove their players with {@link #remove} when they finish (a removal is a logout: it
 * fires the leave event). A player a failed test leaves behind is removed once the longest test
 * (400 ticks) is surely over.
 */
public final class M3TestPlayers {
	/** Server ticks after which a forgotten player is removed: the 400-tick test limit plus a margin. */
	private static final int LIFETIME_TICKS = 440;

	private static final Map<ServerPlayer, Integer> LISTED = new HashMap<>();
	private static boolean sweeping;

	/** A listed player standing at relative (1, 1, 1) of the test (the floor of a fixture is y = 0). */
	public static ServerPlayer listed(GameTestHelper helper) {
		return listed(helper, new BlockPos(1, 1, 1));
	}

	/** A listed player with their feet at the bottom centre of {@code relative}. */
	public static ServerPlayer listed(GameTestHelper helper, BlockPos relative) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(relative));
		player.snapTo(feet.x, feet.y, feet.z);
		MinecraftServer server = helper.getLevel().getServer();
		startSweeping();
		LISTED.put(player, server.getTickCount() + LIFETIME_TICKS);
		return player;
	}

	/** Logs a listed player out (the leave event fires); safe to call twice. */
	public static void remove(ServerPlayer player) {
		if (LISTED.remove(player) != null && !player.hasDisconnected()) {
			player.level().getServer().getPlayerList().remove(player);
		}
	}

	private static void startSweeping() {
		if (sweeping) {
			return;
		}

		sweeping = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (LISTED.isEmpty()) {
				return;
			}

			int now = server.getTickCount();
			LISTED.entrySet().stream().filter(entry -> entry.getValue() <= now).map(Map.Entry::getKey).toList().forEach(M3TestPlayers::remove);
		});
	}

	private M3TestPlayers() {
	}
}
