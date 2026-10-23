package com.oldrepublic.world.puzzle;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.puzzle.PuzzleStates.VentPhase;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/**
 * The puzzle kit's timers as pure steps (spec 3.3): no world access, so the game tests can drive
 * every timeline with {@code new SiteBalance()}. Each block keeps exactly one scheduled tick (the
 * level keeps only one pending tick per position and block), so every timeline below is a chain of
 * single steps.
 */
public final class PuzzleRules {
	/** Lift dispatches further apart than this break a rubble channel. */
	public static final int RUBBLE_MAX_GAP = 4;
	/** How long a vent stays in PUFF (the cloud lingers on the client for 30 ticks either way). */
	public static final int VENT_PUFF_TICKS = 10;
	/** A reset that found an entity in the way tries again after this long. */
	public static final int OCCUPIED_RETRY_TICKS = 20;

	/** One idle step of a resettable block: its new {@code age}, and whether it resets now. */
	public record ResetStep(int age, boolean reset) {
	}

	/**
	 * One idle step: a player near keeps the block as it is (age 0); otherwise the age grows (capped
	 * at {@link PuzzleStates#MAX_AGE}) and the block resets once {@code age * interval} reaches
	 * {@code sites.puzzleResetTicks}.
	 */
	public static ResetStep resetStep(int age, boolean playerNear, SiteBalance b) {
		if (playerNear) {
			return new ResetStep(0, false);
		}

		int next = Math.min(PuzzleStates.MAX_AGE, Math.max(0, age) + 1);
		return new ResetStep(next, (long) next * resetInterval(b) >= b.puzzleResetTicks);
	}

	/**
	 * Ticks between idle steps: {@code sites.puzzleTickInterval}, stretched when a configured reset
	 * time would need more than {@link PuzzleStates#MAX_AGE} steps, so a reset always happens.
	 */
	public static int resetInterval(SiteBalance b) {
		int needed = Math.ceilDiv(Math.max(1, b.puzzleResetTicks), PuzzleStates.MAX_AGE);
		return Math.max(1, Math.max(b.puzzleTickInterval, needed));
	}

	/**
	 * The urn keeper's order: which urn (0–3, {@code URN_1}–{@code URN_4}) is pushed first, second,
	 * third and fourth. A permutation seeded from the keeper's position, so it is stable for one
	 * keeper and differs between keepers.
	 */
	public static int[] urnOrder(BlockPos keeper) {
		RandomSource random = RandomSource.create(keeper.asLong());
		int[] order = {0, 1, 2, 3};

		for (int i = order.length - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int swap = order[i];
			order[i] = order[j];
			order[j] = swap;
		}

		return order;
	}

	/**
	 * One step of a tomb vent.
	 *
	 * @param next  the phase after this step
	 * @param delay ticks to the next step, 0 = the vent rests until something kicks it
	 * @param puff  whether this step releases the puff (damage, slowness, the cloud)
	 */
	public record VentStep(VentPhase next, int delay, boolean puff) {
	}

	/**
	 * A vent's cycle while a player is within {@code sites.ventActiveRange}: IDLE waits
	 * {@code ventMinTicks}..{@code ventMaxTicks}, TELL lasts {@code ventTellTicks}, PUFF lasts
	 * {@link #VENT_PUFF_TICKS}. Without a player near, IDLE rests; a tell already shown always
	 * finishes with its puff, so a tell is never a lie.
	 */
	public static VentStep ventStep(VentPhase phase, boolean playerNear, RandomSource random, SiteBalance b) {
		return switch (phase) {
			case IDLE -> playerNear ? new VentStep(VentPhase.TELL, Math.max(1, b.ventTellTicks), false) : new VentStep(VentPhase.IDLE, 0, false);
			case TELL -> new VentStep(VentPhase.PUFF, VENT_PUFF_TICKS, true);
			case PUFF -> new VentStep(VentPhase.IDLE, playerNear ? ventWait(random, b) : 0, false);
		};
	}

	/** A resting vent's wait before its next tell. */
	public static int ventWait(RandomSource random, SiteBalance b) {
		int min = Math.max(1, b.ventMinTicks);
		int max = Math.max(min, b.ventMaxTicks);
		return min + random.nextInt(max - min + 1);
	}

	/**
	 * Whether collapsed rubble clears on a Lift dispatch at {@code now}: the dispatches since
	 * {@code channelStart} have run for {@code sites.rubbleTicks} ticks with no gap over
	 * {@link #RUBBLE_MAX_GAP} (the previous one was at {@code lastLift}).
	 */
	public static boolean rubbleClears(long channelStart, long lastLift, long now, SiteBalance b) {
		return now - lastLift <= RUBBLE_MAX_GAP && now >= lastLift && now - channelStart + 1 >= Math.max(1, b.rubbleTicks);
	}

	/**
	 * The channel a Lift dispatch at {@code now} belongs to: the running one when the last dispatch
	 * was at most {@link #RUBBLE_MAX_GAP} ago, else a new one starting now.
	 */
	public static RubbleChannel rubbleLift(@Nullable RubbleChannel running, long now) {
		if (running == null || now - running.last() > RUBBLE_MAX_GAP || now < running.last()) {
			return new RubbleChannel(now, now);
		}

		return new RubbleChannel(running.start(), now);
	}

	/** A Lift channel on one rubble block: the first and the latest dispatch. */
	public record RubbleChannel(long start, long last) {
	}

	/**
	 * One step of a counterweight's timeline.
	 *
	 * @param sunk   whether it is down after the step
	 * @param age    its warning marker after the step (1 once the warning played)
	 * @param delay  ticks to its next step, 0 = none
	 * @param effect the {@code counterweight} variant to play ({@link PuzzleEffects#COUNTERWEIGHT_SINK} and so on), -1 none
	 */
	public record CounterweightStep(boolean sunk, int age, int delay, int effect) {
	}

	/** A Push on a counterweight: a raised one sinks; a sunk one ignores it (its timer is not refreshed). */
	public static @Nullable CounterweightStep counterweightPush(boolean sunk, SiteBalance b) {
		if (sunk) {
			return null;
		}

		int down = Math.max(1, b.counterweightTicks);
		int warn = Math.clamp(b.counterweightWarnTicks, 0, down - 1);
		return new CounterweightStep(true, 0, down - warn, PuzzleEffects.COUNTERWEIGHT_SINK);
	}

	/** The scheduled step of a sunk counterweight: first the warning, then it rises. */
	public static @Nullable CounterweightStep counterweightTick(boolean sunk, int age, SiteBalance b) {
		if (!sunk) {
			return null;
		}

		int down = Math.max(1, b.counterweightTicks);
		int warn = Math.clamp(b.counterweightWarnTicks, 0, down - 1);

		if (age == 0 && warn > 0) {
			return new CounterweightStep(true, 1, warn, PuzzleEffects.COUNTERWEIGHT_WARN);
		}

		return new CounterweightStep(false, 0, 0, PuzzleEffects.COUNTERWEIGHT_RISE);
	}

	/**
	 * One step of a pull anchor.
	 *
	 * @param strain its strain after the step (0 armed, 1 straining, 2 released)
	 * @param delay  ticks to its next step, 0 = none
	 * @param effect the {@code anchor} variant to play ({@link PuzzleEffects#ANCHOR_STRAIN} or {@link PuzzleEffects#ANCHOR_RELEASE}), -1 none
	 */
	public record AnchorStep(int strain, int delay, int effect) {
	}

	/** A Pull on an anchor: an armed one starts to strain; a straining or released one ignores it. */
	public static @Nullable AnchorStep anchorPull(int strain, SiteBalance b) {
		return strain == 0 ? new AnchorStep(1, Math.max(1, b.anchorStrainTicks), PuzzleEffects.ANCHOR_STRAIN) : null;
	}

	/** The scheduled step of an anchor: straining → released (redstone on), released → armed again. */
	public static @Nullable AnchorStep anchorTick(int strain, SiteBalance b) {
		return switch (strain) {
			case 1 -> new AnchorStep(2, Math.max(1, b.anchorOpenTicks), PuzzleEffects.ANCHOR_RELEASE);
			case 2 -> new AnchorStep(0, 0, -1);
			default -> null;
		};
	}

	private PuzzleRules() {
	}
}
