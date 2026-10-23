package com.oldrepublic.client.enemy.hymn;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Looks over W8's foes and bosses near the player at a fixed interval (client tick) and hands each
 * live one to its watcher: the synced-state loops start there ({@link FoeLoops}), and the lasting
 * looks that follow a synced state (the charged plates' sparks, the Cantor's and his choir's wisps,
 * the resonators' light) are drawn there, so nothing depends on an event that may be lost.
 */
public final class FoeWatch {
	/** How far the watchers look around the player. */
	private static final double RANGE = 48.0;

	/** Looks at one live foe of its type near the player. */
	@FunctionalInterface
	public interface Watcher<T extends Entity> {
		void see(Minecraft mc, ClientLevel level, T entity);
	}

	private record Watch<T extends Entity>(Class<T> type, int interval, Watcher<T> watcher) {
		void run(Minecraft mc, ClientLevel level, LocalPlayer player) {
			for (T entity : level.getEntitiesOfClass(type, player.getBoundingBox().inflate(RANGE), Entity::isAlive)) {
				watcher.see(mc, level, entity);
			}
		}
	}

	private static final List<Watch<?>> WATCHES = new ArrayList<>();
	private static boolean initialised;
	private static long ticks;

	/** Hooks the client tick. Called once from {@code FoesM5Client}. */
	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		ClientTickEvents.END_CLIENT_TICK.register(FoeWatch::tick);
	}

	/** Adds a watcher for live foes of {@code type} near the player, run every {@code interval} ticks (client init). */
	public static <T extends Entity> void watch(Class<T> type, int interval, Watcher<T> watcher) {
		WATCHES.add(new Watch<>(type, Math.max(1, interval), watcher));
	}

	private static void tick(Minecraft mc) {
		ticks++;
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;

		if (level == null || player == null || !player.isAlive() || mc.isPaused()) {
			return;
		}

		for (Watch<?> watch : WATCHES) {
			if (ticks % watch.interval() == 0) {
				watch.run(mc, level, player);
			}
		}
	}

	private FoeWatch() {
	}
}
