package com.oldrepublic.client.force;

import java.util.List;

import com.oldrepublic.force.network.SenseRevealPayload;
import com.oldrepublic.force.sense.SenseTargets;

/**
 * The local player's current Force Sense reveal ({@code sense_reveal}): the points the server sent
 * and how long they stay. {@link SenseFx} draws from it and the client screenshot steps read it.
 * Cleared on a level change or disconnect. Client side.
 */
public final class SenseReveal {
	private static List<SenseTargets.Point> points = List.of();
	private static long castTick = Long.MIN_VALUE;
	private static long until = Long.MIN_VALUE;

	/** Stores a new reveal; it replaces the previous one. */
	static void set(SenseRevealPayload payload) {
		points = payload.points();
		castTick = PowerAnimations.ticks();
		until = castTick + Math.max(1, payload.durationTicks());
	}

	/** The revealed points while the reveal lasts, nearest first; empty once it has faded. */
	public static List<SenseTargets.Point> points() {
		return active() ? points : List.of();
	}

	/** Whether a reveal is showing now. */
	public static boolean active() {
		return PowerAnimations.ticks() < until;
	}

	/** Client ticks since the last cast, or -1 when no reveal is showing. */
	static long age() {
		return active() ? PowerAnimations.ticks() - castTick : -1;
	}

	/** Client ticks the current reveal has left, or 0. */
	static long remaining() {
		return Math.max(0, until - PowerAnimations.ticks());
	}

	static void clear() {
		points = List.of();
		castTick = Long.MIN_VALUE;
		until = Long.MIN_VALUE;
	}

	private SenseReveal() {
	}
}
