package com.oldrepublic.client.world.puzzle;

import com.oldrepublic.world.puzzle.PuzzleDisplay;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Client initialisation of the puzzle kit, called from {@code WorldClient}: the effect handlers
 * ({@link PuzzleEffectHandlers}; the puzzle seal's look is reached through the M1 {@code seal_open}
 * handler), the display-tick ambience ({@link PuzzleDisplayClient}) and the tick that draws the
 * lingering looks. Block render layers come from texture transparency in 26.2, so nothing is
 * registered for them.
 */
public final class PuzzleClient {
	public static void init() {
		PuzzleEffectHandlers.init();
		PuzzleDisplay.set(PuzzleDisplayClient.INSTANCE);
		ClientTickEvents.END_CLIENT_TICK.register(PuzzleEffectHandlers::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(PuzzleEffectHandlers::reset));
	}

	private PuzzleClient() {
	}
}
