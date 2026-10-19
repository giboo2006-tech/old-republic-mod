package com.oldrepublic.boss.mentor;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** The mentor duels of the Fall and the Return (MILESTONE_5.md 12.1, 8.3); W8 owns it. Phase 0 stub. */
public final class MentorDuel {
	/** Whose duel: Saelen (Push and Barrier) or Sorn (Lightning and Choke). */
	public enum Kind {
		SAELEN,
		SORN
	}

	/** How a duel ended: the mentor yielded at 10%, or it reset (the player died or left for 200 ticks). */
	public enum Result {
		YIELDED,
		RESET
	}

	/**
	 * Starts {@code kind}'s duel between {@code p} and {@code mentor}; {@code onEnd} hears how it ended, once (12.1). Phase 0
	 * stub (W8 replaces it): there is no duel yet and the mentor yields at once, so the Turning's rites (W5) run end to end.
	 */
	public static void start(ServerPlayer p, Entity mentor, Kind kind, Consumer<Result> onEnd) {
		onEnd.accept(Result.YIELDED);
	}

	private MentorDuel() {
	}
}
