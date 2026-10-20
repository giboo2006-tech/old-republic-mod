package com.oldrepublic.planet.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side ambience of the planet blocks. The blocks call {@link #get()} from
 * {@code animateTick} (which only runs on the client); {@code client.planet.PlanetClient} installs
 * the real listener with {@link #set}, keeping client classes such as {@code Vfx} out of common
 * code (the M3 {@code WorldBlockAmbience} pattern).
 */
public final class PlanetBlockAmbience {
	/** The display ticks of the planet blocks. */
	public interface Listener {
		/** A duskglass cluster: a dark glint now and then. */
		default void duskglass(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** An aurelite ore block: a rare warm glint where it meets the air. */
		default void aurelite(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** A clean waystone: its pale hum, now and then. */
		default void waystone(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** The Heartstone: slow dark mist at its base while it is corrupted. */
		default void heartstone(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** A sunstone ore block: heat shimmer where a seam meets the air, now and then. */
		default void sunstone(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** A stormglass cluster: static crawls over it now and then. */
		default void stormglass(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/**
		 * A verse stone, every client tick (from its block entity's client ticker): its motes while
		 * awake and its positional hum loop, which the listener starts and stops itself.
		 */
		default void verseStone(BlockState state, Level level, BlockPos pos) {
		}
	}

	private static final Listener NONE = new Listener() {
	};
	private static volatile Listener listener = NONE;

	public static Listener get() {
		return listener;
	}

	public static void set(Listener newListener) {
		listener = newListener == null ? NONE : newListener;
	}

	private PlanetBlockAmbience() {
	}
}
