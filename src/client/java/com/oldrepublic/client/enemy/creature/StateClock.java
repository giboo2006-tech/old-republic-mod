package com.oldrepublic.client.enemy.creature;

import java.util.HashMap;
import java.util.Map;

/**
 * How long each beast has been in its current synced state, measured on the client from the
 * entity's age, so a tell's pose can ease in. Forgets everything when it grows past a few hundred
 * entries (the next frame starts each beast's clock again).
 */
final class StateClock {
	private static final int MAX_ENTRIES = 512;

	private record Entry(int state, float since) {
	}

	private final Map<Integer, Entry> entries = new HashMap<>();

	/** Ticks entity {@code id} has been in {@code state} as of age {@code age} (ticks with partial tick). */
	float ticksIn(int id, int state, float age) {
		Entry entry = entries.get(id);

		if (entry == null || entry.state() != state || age < entry.since()) {
			if (entries.size() >= MAX_ENTRIES) {
				entries.clear();
			}

			entry = new Entry(state, age);
			entries.put(id, entry);
		}

		return age - entry.since();
	}
}
