package com.oldrepublic.saber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The light combo string of each player (spec section 9.2): a hit with attack strength of at least
 * {@code comboMinStrength} within the combo window of the previous combo hit continues the string,
 * up to the step 3 finisher. In memory only; the pure step rule is {@link #nextStep}.
 */
public final class ComboTracker {
	/** The finisher's step. */
	public static final int FINISHER = 3;

	private record State(int step, long tick) {
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	/**
	 * The step a swing reaches.
	 *
	 * @param previousStep step of the previous combo hit (0 = none)
	 * @param previousTick game time of the previous combo hit
	 * @return 1 to 3, or 0 when the swing was too weak to count (which also resets the string)
	 */
	public static int nextStep(int previousStep, long previousTick, float strength, long now, int window, float minStrength) {
		if (strength < minStrength) {
			return 0;
		}

		boolean continues = previousStep > 0 && previousStep < FINISHER && now - previousTick <= window;
		return continues ? previousStep + 1 : 1;
	}

	/** Records a swing at {@code now} and returns its step (0 for a weak swing). */
	static int onSwing(UUID player, float strength, long now, int window, float minStrength) {
		State previous = STATES.get(player);
		int step = previous == null ? nextStep(0, 0, strength, now, window, minStrength)
				: nextStep(previous.step, previous.tick, strength, now, window, minStrength);

		if (step == 0) {
			STATES.remove(player);
		} else {
			STATES.put(player, new State(step, now));
		}

		return step;
	}

	/** The current step (0 when no string is running), for tests and the debug command. */
	public static int step(UUID player, long now, int window) {
		State state = STATES.get(player);
		return state == null || now - state.tick > window ? 0 : state.step;
	}

	/** Ends the string (the player was hit). */
	static void reset(UUID player) {
		STATES.remove(player);
	}

	private ComboTracker() {
	}
}
