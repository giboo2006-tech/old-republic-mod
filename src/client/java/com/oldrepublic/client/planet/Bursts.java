package com.oldrepublic.client.planet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.Vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

/**
 * Particle bursts spread over a few ticks (spec MILESTONE_5.md 15.2): a site door's 16 dust over 20
 * ticks, a silenced verse stone's 30 falling motes over 20. Each tick spends its share through the
 * {@link Vfx} budget. Every burst is dropped when the client level changes and on disconnect, so
 * none outlives the level it was started in.
 */
final class Bursts {
	private static final List<Burst> ACTIVE = new ArrayList<>();
	private static @Nullable Object lastLevel;

	/** {@code total} samples of {@code spawn} over {@code duration} ticks; {@code age} counts the ticks run. */
	private record Burst(int total, int duration, Consumer<RandomSource> spawn, int[] age) {
	}

	/** Spreads {@code total} calls of {@code spawn} (Standard count) evenly over {@code duration} ticks, starting now. */
	static void add(ClientLevel level, int total, int duration, Consumer<RandomSource> spawn) {
		if (level != lastLevel) {
			ACTIVE.clear();
			lastLevel = level;
		}

		Burst burst = new Burst(total, Math.max(1, duration), spawn, new int[1]);
		ACTIVE.add(burst);
		step(burst, level.getRandom());
	}

	static void tick(Minecraft client) {
		if (client.level != lastLevel) {
			ACTIVE.clear();
			lastLevel = client.level;
		}

		if (client.level == null) {
			return;
		}

		RandomSource random = client.level.getRandom();
		Iterator<Burst> it = ACTIVE.iterator();

		while (it.hasNext()) {
			Burst burst = it.next();

			if (burst.age()[0] >= burst.duration()) {
				it.remove();
			} else {
				step(burst, random);
			}
		}
	}

	/** Disconnect: every burst stops. */
	static void clear() {
		ACTIVE.clear();
		lastLevel = null;
	}

	private static void step(Burst burst, RandomSource random) {
		int age = burst.age()[0]++;
		int due = burst.total() * (age + 1) / burst.duration() - burst.total() * age / burst.duration();

		if (due <= 0) {
			return;
		}

		for (int i = 0; i < Vfx.count(due); i++) {
			burst.spawn().accept(random);
		}
	}

	private Bursts() {
	}
}
