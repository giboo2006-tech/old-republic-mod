package com.oldrepublic.boss.kaast;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;

/** Torvin Kaast's hunting camp (spec MILESTONE_4.md 8.6, 10.5). Phase 0 stub; W6 writes it. */
public final class HuntingCamps {
	/**
	 * Places the owner's hunting camp on their path world through {@code PlanetSites}, once.
	 *
	 * @return the camp's anchor, or null when it is not placed yet (the caller retries)
	 */
	public static @Nullable GlobalPos place(ServerPlayer owner) {
		return null;
	}

	private HuntingCamps() {
	}
}
