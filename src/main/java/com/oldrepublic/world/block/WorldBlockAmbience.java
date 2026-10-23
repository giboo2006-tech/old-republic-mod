package com.oldrepublic.world.block;

import com.oldrepublic.world.trial.ShadowEchoEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side ambience of the world blocks and the Shadow Echo. The blocks call {@link #get()} from
 * {@code animateTick} (which only runs on the client); the client feature installs the real
 * implementation with {@link #set}. Keeping it behind this hook keeps client classes such as
 * {@code Vfx} out of common code.
 */
public final class WorldBlockAmbience {
	public interface Listener {
		/** A kyber or unstable cluster was ticked for display. */
		default void cluster(BlockState state, Level level, BlockPos pos, RandomSource random, boolean unstable) {
		}

		/** A crystal-veined stone was ticked for display. */
		default void vein(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** A resonant seal was ticked for display. */
		default void seal(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** A meditation stone was ticked for display. */
		default void meditationStone(BlockState state, Level level, BlockPos pos, RandomSource random) {
		}

		/** A Shadow Echo ticked on the client (wisps, tells). */
		default void echoTick(ShadowEchoEntity echo) {
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

	private WorldBlockAmbience() {
	}
}
