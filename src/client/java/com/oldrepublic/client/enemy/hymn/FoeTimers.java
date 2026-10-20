package com.oldrepublic.client.enemy.hymn;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * Spreads a look over time on the client tick (spec MILESTONE_5.md 15.1: "N particles over the tell"):
 * a {@link #spread} hands its spawner the share of {@code total} due on each tick, for {@code ticks}
 * ticks, so the whole count lands evenly over the tell. One that follows an entity stops early when
 * the entity dies or is removed (the tell was cut short with it); everything stops on a level change
 * and on disconnect.
 */
public final class FoeTimers {
	private static final int MAX_TIMERS = 64;

	/** Spawns {@code count} pieces of a look on one tick ({@code age} 0 to ticks - 1). */
	@FunctionalInterface
	public interface Spawner {
		void spawn(ClientLevel level, int count, int age);
	}

	private static final class Timer {
		final int total;
		final int ticks;
		final @Nullable Entity follow;
		final Spawner spawner;
		int age;
		float due;

		Timer(int total, int ticks, @Nullable Entity follow, Spawner spawner) {
			this.total = total;
			this.ticks = Math.max(1, ticks);
			this.follow = follow;
			this.spawner = spawner;
		}
	}

	private static final List<Timer> TIMERS = new ArrayList<>();
	private static @Nullable ClientLevel lastLevel;
	private static boolean initialised;

	/** Hooks the client tick and the disconnect. Called once from {@code FoesM5Client}. */
	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		ClientTickEvents.END_CLIENT_TICK.register(FoeTimers::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TIMERS.clear());
	}

	/**
	 * Spreads {@code total} pieces over {@code ticks} ticks; the spawner draws each tick's share. With
	 * {@code follow}, it stops when that entity is gone.
	 */
	public static void spread(int total, int ticks, @Nullable Entity follow, Spawner spawner) {
		if (total <= 0) {
			return;
		}

		if (TIMERS.size() >= MAX_TIMERS) {
			TIMERS.remove(0);
		}

		TIMERS.add(new Timer(total, ticks, follow, spawner));
	}

	/** Timers running now (the client tests read it). */
	public static int active() {
		return TIMERS.size();
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level != lastLevel) {
			lastLevel = level;
			TIMERS.clear();
		}

		if (level == null || mc.isPaused()) {
			return;
		}

		List<Timer> running = new ArrayList<>(TIMERS);

		for (Timer timer : running) {
			if (timer.follow != null && (timer.follow.isRemoved() || !timer.follow.isAlive())) {
				timer.age = timer.ticks;
				continue;
			}

			timer.due += (float) timer.total / timer.ticks;
			int count = (int) timer.due;
			timer.due -= count;

			if (count > 0) {
				timer.spawner.spawn(level, count, timer.age);
			}

			timer.age++;
		}

		TIMERS.removeIf(timer -> timer.age >= timer.ticks);
	}

	private FoeTimers() {
	}
}
