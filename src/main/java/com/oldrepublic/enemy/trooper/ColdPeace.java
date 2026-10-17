package com.oldrepublic.enemy.trooper;

import com.oldrepublic.combat.IncidentMode;

/**
 * The cold peace between the armies (spec 7.5) as pure functions. Republic and Imperial troopers
 * never fight unless either side carries an incident; untagged patrols that meet halt facing each
 * other, their leaders bark, and after {@code standoffTicks} both walk on.
 */
public final class ColdPeace {
	/** Where a squad is in a standoff with a rival patrol. */
	public enum Phase {
		/** No rival near. */
		NONE,
		/** Halted, rifles lowered, facing the rival. */
		HALTED,
		/** Walking on again, keeping {@code standoffDistance} from the rival. */
		WALKING_ON
	}

	/** A squad's standoff phase and the game time it entered it. */
	public record Standoff(Phase phase, long since) {
		public static final Standoff NONE = new Standoff(Phase.NONE, 0);
	}

	/**
	 * Whether troopers of the two armies with these incident modes fight each other: always without
	 * the cold peace, otherwise only when either side carries an incident.
	 */
	public static boolean shouldEngage(IncidentMode a, IncidentMode b, boolean coldPeace) {
		return !coldPeace || a != IncidentMode.NONE || b != IncidentMode.NONE;
	}

	/**
	 * One step of the standoff: a rival within {@code standoffRange} halts the squad; after
	 * {@code standoffTicks} it walks on; once the rival is gone it is back to {@link Phase#NONE}.
	 */
	public static Standoff standoffStep(Standoff state, boolean rivalNear, long now, TrooperBalance b) {
		return switch (state.phase()) {
			case NONE -> rivalNear ? new Standoff(Phase.HALTED, now) : state;
			case HALTED -> {
				if (!rivalNear) {
					yield new Standoff(Phase.NONE, now);
				}

				yield now - state.since() >= b.standoffTicks ? new Standoff(Phase.WALKING_ON, now) : state;
			}
			case WALKING_ON -> rivalNear ? state : new Standoff(Phase.NONE, now);
		};
	}

	private ColdPeace() {
	}
}
