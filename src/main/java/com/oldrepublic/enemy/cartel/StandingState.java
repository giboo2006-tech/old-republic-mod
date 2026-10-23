package com.oldrepublic.enemy.cartel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One faction foe's per-player fight state (the M3 trooper rules, 7): who it engaged (it struck
 * first, or a Wary player drew a weapon on it), who provoked it (struck it first while not hostile),
 * and when it last warned whom. In memory only: a reload forgets a fight, as the troopers do.
 */
public final class StandingState {
	private final Map<UUID, Long> engagedUntil = new HashMap<>();
	private final Map<UUID, Long> provokedUntil = new HashMap<>();
	private final Map<UUID, Long> warnedAt = new HashMap<>();

	/** It engages {@code player} until {@code until}: the fight counts as hostile. */
	public void engage(UUID player, long until) {
		engagedUntil.merge(player, until, Math::max);
	}

	/** {@code player} struck first; it fights back until {@code until}, and the fight counts as not hostile. */
	public void provoke(UUID player, long until) {
		provokedUntil.merge(player, until, Math::max);
	}

	public boolean engaged(UUID player, long now) {
		Long until = engagedUntil.get(player);
		return until != null && now < until;
	}

	public boolean provoked(UUID player, long now) {
		Long until = provokedUntil.get(player);
		return until != null && now < until;
	}

	/** Whether {@code player} may be warned again at {@code now} ({@code repeat} ticks since the last warning). */
	public boolean warnDue(UUID player, long now, int repeat) {
		Long last = warnedAt.get(player);
		return last == null || now - last >= repeat;
	}

	public void warned(UUID player, long now) {
		warnedAt.put(player, now);
	}

	/** Whether it has warned {@code player} within the last {@code window} ticks. */
	public boolean warnedWithin(UUID player, long now, int window) {
		Long last = warnedAt.get(player);
		return last != null && now - last < window;
	}

	/** Forgets fights and warnings that ran out. */
	public void prune(long now, int warnWindow) {
		engagedUntil.values().removeIf(until -> until <= now);
		provokedUntil.values().removeIf(until -> until <= now);
		warnedAt.values().removeIf(at -> now - at >= warnWindow);
	}

	/** Forgets everything (a yield, a reset). */
	public void clear() {
		engagedUntil.clear();
		provokedUntil.clear();
		warnedAt.clear();
	}
}
