package com.oldrepublic.client.mission;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.mission.network.TrackerHintPayload;

/**
 * What the client last heard about the player's missions that is not in the synced attachment:
 * the latest tracker hint (direction, band and counter, spec 5.7) and the moment of the last
 * {@code mission_update}, which makes the tracker flash. Everything is dropped on a level change or
 * a disconnect. Client thread only.
 */
final class ClientMissionState {
	/** A hint older than this is stale: the mission ended, or the player left its dimension. */
	static final int HINT_STALE_TICKS = 60;
	/** Length of the tracker's flash after a mission update. */
	static final int FLASH_TICKS = 24;

	private static @Nullable TrackerHintPayload hint;
	private static int hintTick = Integer.MIN_VALUE;
	private static int flashTick = Integer.MIN_VALUE;
	private static boolean flashAccept;

	/** Stores a tracker hint from the server. */
	static void hint(TrackerHintPayload payload) {
		hint = payload;
		hintTick = QuestClient.ticks();
	}

	/** The latest hint, or null when none arrived in the last {@value #HINT_STALE_TICKS} ticks. */
	static @Nullable TrackerHintPayload freshHint() {
		return hint != null && QuestClient.ticks() - hintTick <= HINT_STALE_TICKS ? hint : null;
	}

	/** Starts the tracker's flash: a strong one for a newly accepted mission, a soft one for progress. */
	static void flash(boolean accept) {
		flashTick = QuestClient.ticks();
		flashAccept = accept;
	}

	/** The flash's strength at {@code now} (client ticks plus the partial tick), 1 at the update falling to 0. */
	static float flash(float now) {
		float age = now - flashTick;

		if (age < 0f || age > FLASH_TICKS) {
			return 0f;
		}

		float strength = flashAccept ? 1f : 0.6f;
		return strength * (1f - age / FLASH_TICKS);
	}

	/** Ticks since the last mission update, or a large number when none. */
	static float sinceUpdate(float now) {
		return flashTick == Integer.MIN_VALUE ? Float.MAX_VALUE : now - flashTick;
	}

	/** Whether the last update was an accept. */
	static boolean lastWasAccept() {
		return flashAccept;
	}

	static void reset() {
		hint = null;
		hintTick = Integer.MIN_VALUE;
		flashTick = Integer.MIN_VALUE;
		flashAccept = false;
	}

	private ClientMissionState() {
	}
}
