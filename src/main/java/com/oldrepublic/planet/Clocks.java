package com.oldrepublic.planet;

import net.minecraft.server.MinecraftServer;

/**
 * The story clock (spec MILESTONE_4.md 3.6). Planets share the overworld clock, so every day
 * counter reads the overworld's game time, wherever the player stands.
 */
public final class Clocks {
	public static final long TICKS_PER_DAY = 24000L;

	/** The overworld game time. */
	public static long time(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** The world day ({@link #time} / 24000). */
	public static long day(MinecraftServer server) {
		return time(server) / TICKS_PER_DAY;
	}

	private Clocks() {
	}
}
