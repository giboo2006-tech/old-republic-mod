package com.oldrepublic.world.puzzle;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side display ambience of the puzzle kit: the blocks call {@link #get()} from
 * {@code animateTick} (which only runs on the client) and the client feature installs the real
 * implementation with {@link #set}. The same pattern as {@code world.block.WorldBlockAmbience}; it
 * keeps client classes out of common code.
 */
public final class PuzzleDisplay {
	/** What the client draws for each block on its display tick. Every method does nothing by default. */
	public interface Listener {
		/** Lumen moss: faint warm motes. */
		default void lumenMoss(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** Veiled stone: REVEALED seeps fine dust from its seams. */
		default void veiledStone(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** Wreck conduit: an occasional spark. */
		default void wreckConduit(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** Holocron pedestal: slow motes from the cradle while ready. */
		default void pedestal(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** Tomb vent: a thin wisp while idle. */
		default void tombVent(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** Urn keeper: a faint warm glow in the seams once solved. */
		default void urnKeeper(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}
	}

	private static final Listener NONE = new Listener() {
	};
	private static volatile Listener listener = NONE;

	/** The installed listener (a no-op on a dedicated server). */
	public static Listener get() {
		return listener;
	}

	/** Installs the client's listener; null restores the no-op. */
	public static void set(Listener newListener) {
		listener = newListener == null ? NONE : newListener;
	}

	private PuzzleDisplay() {
	}
}
