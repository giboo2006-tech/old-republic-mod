package com.oldrepublic.client.boss.music;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.BossEffects;
import com.oldrepublic.boss.BossEntities;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;

/**
 * The Collector's fight music (spec 8.5): three streamed loops in {@code SoundSource.MUSIC},
 * started and crossfaded over {@value #FADE_TICKS} ticks by the {@code boss_phase} events the arena
 * session sends each participant (variant 0 {@code music.collector.archivist}, 1
 * {@code music.collector.singing_wall}, 2 {@code music.collector.unkept}; 3 yield, 4 reset,
 * 5 victory and 6 music stop fade it out). A sync-only resend (intensity 0) starts the current loop
 * too, and a loop already playing is never restarted.
 *
 * <p>Every stop path: the next loop crossfading in, variants 3 to 6, the local player's death, a
 * level or dimension change and disconnect (at once), the client toggle {@code ClientConfig.bossMusic}
 * (fades out; turning it back on mid-fight resumes the current loop), and a watchdog that fades the
 * music when no Orren Vasch has been near for {@value #WATCHDOG_TICKS} ticks, in case a stop event
 * was lost. While a loop plays or fades, vanilla background music is held off
 * ({@code MusicManagerMixin}).
 */
public final class BossMusic {
	/** Crossfade length in ticks. */
	static final int FADE_TICKS = 40;
	/** How far the watchdog looks for Orren Vasch. */
	private static final double WATCHDOG_RANGE = 64.0;
	/** Ticks without Orren Vasch nearby before the watchdog fades the music. */
	private static final int WATCHDOG_TICKS = 200;
	/** How often the watchdog looks, in ticks. */
	private static final int WATCHDOG_INTERVAL = 20;
	/** Ticks a freshly started loop has to become active before it counts as stopped by the engine. */
	private static final int START_GRACE = 20;

	/** The loop the fight asks for, or null. */
	private static @Nullable SoundEvent wanted;
	/** The loop fading in or playing. */
	private static @Nullable BossMusicSound playing;
	private static int playingAge;
	/** Loops fading out. */
	private static final List<BossMusicSound> FADING = new ArrayList<>();
	private static @Nullable ClientLevel lastLevel;
	private static int watchdogTicks;
	private static int unseenTicks;

	public static void init() {
		ClientEffects.addListener(BossMusic::onEvent);
		ClientTickEvents.END_CLIENT_TICK.register(BossMusic::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopNow());
	}

	/** True while a boss loop plays or fades out: vanilla background music is held off. */
	public static boolean holdsVanillaMusic() {
		return playing != null || !FADING.isEmpty();
	}

	/** Called on the client thread for every effect event. */
	private static void onEvent(EffectEventPayload payload) {
		if (!BossEffects.PHASE.equals(payload.effect())) {
			return;
		}

		wanted = switch (payload.variant()) {
			case BossEffects.PHASE_START -> ModSounds.MUSIC_COLLECTOR_ARCHIVIST;
			case BossEffects.PHASE_TRANSITION_ONE -> ModSounds.MUSIC_COLLECTOR_SINGING_WALL;
			case BossEffects.PHASE_TRANSITION_TWO -> ModSounds.MUSIC_COLLECTOR_UNKEPT;
			// Yield (its drone takes over), reset, victory and the music stop.
			default -> null;
		};
		unseenTicks = 0;
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// Join, dimension change or leaving the world: the fight's music never follows.
			lastLevel = mc.level;
			stopNow();
		}

		if (mc.level == null || mc.player == null) {
			return;
		}

		if (wanted != null && (!mc.player.isAlive() || bossLost(mc))) {
			wanted = null;
		}

		SoundEvent target = ClientConfig.get().bossMusic ? wanted : null;

		if (playing != null && (target == null || playing.track() != target)) {
			playing.fadeOut();
			FADING.add(playing);
			playing = null;
		}

		if (target != null && playing == null) {
			start(mc, target);
		}

		for (Iterator<BossMusicSound> it = FADING.iterator(); it.hasNext();) {
			if (it.next().isStopped()) {
				it.remove();
			}
		}

		if (playing != null && ++playingAge > START_GRACE && !mc.getSoundManager().isActive(playing)) {
			// Stopped from outside (a sound reload or /stopsound): the music ends here.
			playing = null;
			wanted = null;
		}
	}

	/** Starts {@code track}, or brings it back if it is still fading out. */
	private static void start(Minecraft mc, SoundEvent track) {
		for (Iterator<BossMusicSound> it = FADING.iterator(); it.hasNext();) {
			BossMusicSound sound = it.next();

			if (sound.track() == track && !sound.isStopped()) {
				it.remove();
				sound.fadeIn();
				playing = sound;
				return;
			}
		}

		if (!holdsVanillaMusic()) {
			// The first boss loop: stop the vanilla track once; MusicManagerMixin keeps the next one away.
			mc.getMusicManager().stopPlaying();
		}

		playing = new BossMusicSound(track);
		playingAge = 0;
		mc.getSoundManager().play(playing);
	}

	/**
	 * The watchdog: true once no Orren Vasch has been within {@value #WATCHDOG_RANGE} blocks for
	 * {@value #WATCHDOG_TICKS} ticks (the session ended without reaching this client).
	 */
	private static boolean bossLost(Minecraft mc) {
		if (++watchdogTicks < WATCHDOG_INTERVAL) {
			return false;
		}

		watchdogTicks = 0;
		boolean near = !mc.level.getEntitiesOfClass(Entity.class, mc.player.getBoundingBox().inflate(WATCHDOG_RANGE),
				e -> e.getType() == BossEntities.ORREN_VASCH && e.isAlive()).isEmpty();
		unseenTicks = near ? 0 : unseenTicks + WATCHDOG_INTERVAL;
		return unseenTicks >= WATCHDOG_TICKS;
	}

	/** Stops every loop at once and forgets the fight (disconnect, level change). */
	private static void stopNow() {
		wanted = null;

		if (playing != null) {
			playing.end();
			playing = null;
		}

		FADING.forEach(BossMusicSound::end);
		FADING.clear();
		unseenTicks = 0;
	}

	private BossMusic() {
	}
}
