package com.oldrepublic.boss.ulvaine;

import com.oldrepublic.quest.BossOutcome;

import net.minecraft.server.level.ServerPlayer;

/** Darth Ulvaine's fight in the Heart of the Hymnal (MILESTONE_5.md 12.2); W8 owns it. Phase 0 stub. */
public final class UlvaineFight {
	/**
	 * The owner's choice at the yield ({@code SPARED}, {@code BOUND} or {@code EXECUTED}, from W4's {@code ulvaine:resolve}
	 * action): plays the scene and reports the defeat to every participant (12.2). Phase 0 stub (W8 replaces it): does nothing.
	 */
	public static void resolve(ServerPlayer owner, BossOutcome outcome) {
	}

	private UlvaineFight() {
	}
}
