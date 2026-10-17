package com.oldrepublic.world.outpost;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.SiteBalance;

/**
 * The pure rule behind a bounty terminal's 200-tick check (spec 3.3, 3.5, 7.4), so its long timers
 * are tested in a few ticks. Slot 0 is the officer, slots 1 to 4 the garrison ({@code GARRISON_1}–4).
 *
 * <ul>
 * <li><b>Loading rule.</b> Nothing is counted while the terminal's entities are not loaded.</li>
 * <li><b>Never filled.</b> A slot that never had a member spawns at once (the first check that passes
 * the loading rule fills the whole outpost).</li>
 * <li><b>Two checks.</b> A filled slot counts as missing only after {@code terminalChecksBeforeRespawn}
 * checks in a row found nobody bound to it.</li>
 * <li><b>Officer.</b> A missing officer is replaced at once.</li>
 * <li><b>Garrison.</b> One missing trooper is replaced per {@code garrisonRefillTicks}, and only while
 * no player is within {@code garrisonRefillClearRange}.</li>
 * </ul>
 */
public final class GarrisonRules {
	/** Slot index of the officer. */
	public static final int OFFICER = 0;
	/** Number of slots: the officer and four garrison troopers. */
	public static final int SLOTS = 5;
	/** Missed checks are counted up to this, so the counter never overflows. */
	private static final int FAILED_CAP = 1000;

	/**
	 * One slot's saved state.
	 *
	 * @param member the UUID the terminal lists for the slot, or null when it was never filled
	 * @param failed consecutive checks that found nobody bound to the slot
	 */
	public record Slot(@Nullable UUID member, int failed) {
	}

	/**
	 * A terminal's saved state.
	 *
	 * @param slots    the {@link #SLOTS} slots, officer first
	 * @param refillAt game time of the last garrison refill (or of the first fill)
	 */
	public record State(List<Slot> slots, long refillAt) {
		public State {
			if (slots.size() != SLOTS) {
				throw new IllegalArgumentException("A terminal has " + SLOTS + " slots, not " + slots.size());
			}

			slots = List.copyOf(slots);
		}

		/** A terminal that never spawned anyone. */
		public static State empty() {
			List<Slot> slots = new ArrayList<>(SLOTS);

			for (int i = 0; i < SLOTS; i++) {
				slots.add(new Slot(null, 0));
			}

			return new State(slots, 0L);
		}
	}

	/**
	 * What one check saw.
	 *
	 * @param loaded     whether the loading rule held (entities of the terminal's chunk and the 8 around it
	 *                   loaded, and the terminal entity ticking)
	 * @param present    bit {@code i} set when the member listed for slot {@code i} was found alive and bound
	 * @param playerNear whether a player was within {@code garrisonRefillClearRange}
	 */
	public record Observation(boolean loaded, int present, boolean playerNear) {
		/** Whether the member of {@code slot} was found. */
		public boolean present(int slot) {
			return (present & (1 << slot)) != 0;
		}
	}

	/**
	 * The outcome of a check.
	 *
	 * @param next  the state to save; a slot listed in {@code spawn} keeps its old member until the
	 *              terminal records the newly spawned one
	 * @param spawn the slots to spawn now, officer first
	 */
	public record Result(State next, List<Integer> spawn) {
		public Result {
			spawn = List.copyOf(spawn);
		}
	}

	/** One terminal check at game time {@code now}. */
	public static Result step(State state, Observation obs, long now, SiteBalance b) {
		if (!obs.loaded()) {
			return new Result(state, List.of());
		}

		int checks = Math.max(1, b.terminalChecksBeforeRespawn);
		boolean refillDue = !obs.playerNear() && now - state.refillAt() >= b.garrisonRefillTicks;
		boolean refilled = false;
		boolean firstFill = false;
		List<Slot> next = new ArrayList<>(SLOTS);
		List<Integer> spawn = new ArrayList<>();

		for (int i = 0; i < SLOTS; i++) {
			Slot slot = state.slots().get(i);

			if (obs.present(i)) {
				next.add(new Slot(slot.member(), 0));
				continue;
			}

			if (slot.member() == null) {
				spawn.add(i);
				next.add(new Slot(null, 0));
				firstFill |= i != OFFICER;
				continue;
			}

			int failed = Math.min(FAILED_CAP, slot.failed() + 1);
			boolean confirmed = failed >= checks;

			if (confirmed && i == OFFICER) {
				spawn.add(i);
				next.add(new Slot(slot.member(), 0));
			} else if (confirmed && refillDue && !refilled) {
				spawn.add(i);
				refilled = true;
				next.add(new Slot(slot.member(), 0));
			} else {
				next.add(new Slot(slot.member(), failed));
			}
		}

		long refillAt = refilled || firstFill ? now : state.refillAt();
		return new Result(new State(next, refillAt), spawn);
	}

	private GarrisonRules() {
	}
}
