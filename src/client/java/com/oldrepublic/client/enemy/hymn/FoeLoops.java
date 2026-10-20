package com.oldrepublic.client.enemy.hymn;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The positional loops of W8's foes and bosses (spec MILESTONE_5.md 15.4): {@code hymnsworn.chant},
 * {@code scour.hiss}, {@code stormback.charged}, {@code boss.ulvaine.lightning},
 * {@code boss.cantor.{beam,ring,choir,engine}} and {@code story.hologram}. Each loop has a key (one
 * loop per key) and a condition read every tick, and it fades out and stops on every path (the
 * 15.4 stop rules):
 *
 * <ul>
 * <li>its condition fails: the foe stops chanting, burrowing or being charged, the attack or the
 * phase ends, the fight is won, reset or wiped (the synced state or the event's timer);</li>
 * <li>the entity it follows dies or is removed (at once);</li>
 * <li>the listener moves out of its range (leaving the Heart, the undercroft or the area);</li>
 * <li>the local player's death, a level or dimension change, and disconnect (at once).</li>
 * </ul>
 * Synced-state loops come back on their own: a {@link FoeWatch} watcher looks over the foes near the
 * player every few ticks and starts what should be playing.
 */
public final class FoeLoops {
	/** Ticks a loop takes to fade in or out. */
	private static final int FADE_TICKS = 6;

	private static final Map<String, Loop> LOOPS = new HashMap<>();
	private static @Nullable ClientLevel lastLevel;
	private static boolean initialised;

	/** Hooks the client tick and the disconnect. Called once from {@code FoesM5Client}. */
	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		ClientTickEvents.END_CLIENT_TICK.register(FoeLoops::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopAll());
	}

	/**
	 * Starts {@code sound} under {@code key} unless a loop with that key still plays. It follows
	 * {@code entity} when one is given (else it stays at {@code pos}) and keeps playing while
	 * {@code keep} holds and the listener is within {@code range} blocks.
	 */
	public static void start(String key, SoundEvent sound, SoundSource source, @Nullable Entity entity, Vec3 pos, float volume, float pitch,
			double range, BooleanSupplier keep) {
		Loop playing = LOOPS.get(key);

		if (playing != null && !playing.isStopped()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || !mc.player.isAlive() || !keep.getAsBoolean()) {
			return;
		}

		Loop loop = new Loop(sound, source, entity, pos, volume, pitch, range, keep);

		if (!loop.inRange(mc.player)) {
			return;
		}

		LOOPS.put(key, loop);
		mc.getSoundManager().play(loop);
	}

	/** Fades out the loop under {@code key}, if any. */
	public static void stop(String key) {
		Loop loop = LOOPS.get(key);

		if (loop != null) {
			loop.fadeOut();
		}
	}

	/** Whether a loop under {@code key} plays (or still fades out). */
	public static boolean isPlaying(String key) {
		Loop loop = LOOPS.get(key);
		return loop != null && !loop.isStopped();
	}

	/** Loops playing now (the client tests read it). */
	public static int count() {
		int count = 0;

		for (Loop loop : LOOPS.values()) {
			if (!loop.isStopped()) {
				count++;
			}
		}

		return count;
	}

	/** Stops every loop at once (death, a level change, disconnect). */
	public static void stopAll() {
		for (Loop loop : LOOPS.values()) {
			loop.end();
		}

		LOOPS.clear();
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// Join, respawn or dimension change: nothing follows the player into the new level.
			lastLevel = mc.level;
			stopAll();
		}

		LocalPlayer player = mc.player;

		if (mc.level == null || player == null) {
			return;
		}

		if (!player.isAlive()) {
			stopAll();
			return;
		}

		LOOPS.values().removeIf(Loop::isStopped);
	}

	/** One positional loop: follows its entity or stays put, fades in, and fades out when it should stop. */
	private static final class Loop extends AbstractTickableSoundInstance {
		private final @Nullable Entity entity;
		private final float baseVolume;
		private final double range;
		private final BooleanSupplier keep;
		private float level;
		private boolean ending;

		Loop(SoundEvent sound, SoundSource source, @Nullable Entity entity, Vec3 pos, float volume, float pitch, double range, BooleanSupplier keep) {
			super(sound, source, SoundInstance.createUnseededRandom());
			this.entity = entity;
			this.baseVolume = volume;
			this.range = range;
			this.keep = keep;
			this.looping = true;
			this.delay = 0;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.pitch = pitch;
			this.volume = 0f;
			place(pos);
		}

		/** It starts silent and fades in, so the engine must not skip it for its zero volume. */
		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			if (entity != null) {
				if (entity.isRemoved() || !entity.isAlive()) {
					stop();
					return;
				}

				place(entity.position().add(0, entity.getBbHeight() * 0.5, 0));
			}

			LocalPlayer player = Minecraft.getInstance().player;
			boolean wanted = !ending && player != null && player.isAlive() && inRange(player) && keep.getAsBoolean();
			float step = 1f / FADE_TICKS;
			level = wanted ? Math.min(1f, level + step) : Math.max(0f, level - step);
			this.volume = baseVolume * level * ClientConfig.get().modSoundVolume;

			if (!wanted && level <= 0f) {
				stop();
			}
		}

		boolean inRange(LocalPlayer player) {
			return player.position().distanceToSqr(this.x, this.y, this.z) <= range * range;
		}

		private void place(Vec3 pos) {
			this.x = pos.x;
			this.y = pos.y;
			this.z = pos.z;
		}

		void fadeOut() {
			ending = true;
		}

		void end() {
			stop();
		}
	}

	private FoeLoops() {
	}
}
