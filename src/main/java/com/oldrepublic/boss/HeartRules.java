package com.oldrepublic.boss;

/**
 * The Reliquary heart's pure state machine and timers (spec 8.2, 8.3, 8.6). {@link #step} answers
 * what a {@link HeartState} becomes on an {@link Event}; the predicates answer the heart's timers.
 * {@code ArenaSession} feeds them from the world; the game tests check them directly.
 *
 * <pre>
 * DORMANT  --OWNER_NEAR-->  READY  --FIGHT_START-->  ACTIVE  --YIELD-->  YIELDED  --RESOLVE-->  RESOLVED
 *          <--OWNER_AWAY--         <-----RESET------
 * </pre>
 *
 * YIELDED and RESOLVED persist: no event but RESOLVE leaves YIELDED, and nothing leaves RESOLVED (the
 * rematch runs while the heart stays RESOLVED). Peaceful sends YIELD from READY: he talks, then kneels.
 */
public final class HeartRules {
	/** What happened at the heart this tick. */
	public enum Event {
		/** The owner is within {@code readyRange}. */
		OWNER_NEAR,
		/** The owner is away and nobody awakened is in the hall. */
		OWNER_AWAY,
		/** A fight starts (meeting closed or timed out, the dais or the lectern, a command). */
		FIGHT_START,
		/** He yields at 10% (or kneels at once in Peaceful). */
		YIELD,
		/** No participant was alive inside for {@code resetTicks}, or the session was dropped. */
		RESET,
		/** The owner chose his fate. */
		RESOLVE
	}

	/** The heart's state after {@code event}; unchanged when the event does not apply. */
	public static HeartState step(HeartState state, Event event) {
		return switch (state) {
			case DORMANT -> event == Event.OWNER_NEAR ? HeartState.READY : state;
			case READY -> switch (event) {
				case OWNER_AWAY -> HeartState.DORMANT;
				case FIGHT_START -> HeartState.ACTIVE;
				case YIELD -> HeartState.YIELDED;
				default -> state;
			};
			case ACTIVE -> switch (event) {
				case RESET -> HeartState.READY;
				case YIELD -> HeartState.YIELDED;
				default -> state;
			};
			case YIELDED -> event == Event.RESOLVE ? HeartState.RESOLVED : state;
			case RESOLVED -> state;
		};
	}

	/** Whether the owner at {@code distanceSqr} from the heart keeps it READY (within {@code readyRange}). */
	public static boolean ownerNear(double distanceSqr, CollectorBalance b) {
		return distanceSqr <= (double) b.readyRange * b.readyRange;
	}

	/** The doorway seal closes {@code sealCloseTicks} after the threshold was crossed, once nobody stands in it. */
	public static boolean sealCloses(long ticksSinceCrossed, boolean doorwayOccupied, CollectorBalance b) {
		return ticksSinceCrossed >= b.sealCloseTicks && !doorwayOccupied;
	}

	/** The meeting dialogue gives way to the fight after {@code meetDialogueTicks}. */
	public static boolean meetingTimedOut(long ticksSinceMeeting, CollectorBalance b) {
		return ticksSinceMeeting >= b.meetDialogueTicks;
	}

	/** The fight resets after {@code resetTicks} with no participant alive inside the hall. */
	public static boolean resetDue(long ticksWithNobodyInside, CollectorBalance b) {
		return ticksWithNobodyInside >= b.resetTicks;
	}

	/** A participant who left alive, changed dimension or disconnected leaves the bar after {@code participantLeaveTicks}. */
	public static boolean participantDropped(long ticksAway, CollectorBalance b) {
		return ticksAway >= b.participantLeaveTicks;
	}

	/** A later attempt starts when a participant comes within {@code daisStartRange} of the dais. */
	public static boolean daisStart(double distanceSqrToDais, CollectorBalance b) {
		return distanceSqrToDais <= (double) b.daisStartRange * b.daisStartRange;
	}

	private HeartRules() {
	}
}
