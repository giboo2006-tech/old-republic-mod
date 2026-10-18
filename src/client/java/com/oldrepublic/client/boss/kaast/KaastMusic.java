package com.oldrepublic.client.boss.kaast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.kaast.KaastEffects;
import com.oldrepublic.boss.kaast.KaastEntities;
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
 * The Kaast fight's music (spec MILESTONE_4.md 10.5, 13.4): {@code music.kaast.hunt},
 * {@code music.kaast.trap} and {@code music.kaast.last_stand}, one per phase, crossfaded over
 * {@value #FADE_TICKS} ticks like the M3 boss. The session sends each participant a {@code kaast}
 * event, variant {@code MUSIC}, with the phase as intensity (0 stops it).
 *
 * <p>Every stop path: the next phase crossfading in; the stop the session sends on the yield, a
 * victory, a reset and a participant leaving the ring; the local player's death; a level or
 * dimension change and disconnect (at once); the client toggle {@code ClientConfig.bossMusic}; and a
 * watchdog that fades the music when no Torvin Kaast has been near for {@value #WATCHDOG_TICKS}
 * ticks, in case a stop was lost. While a loop plays or fades, vanilla background music is held
 * off ({@code MusicManagerKaastMixin}).
 */
public final class KaastMusic {
	/** Crossfade length in ticks. */
	static final int FADE_TICKS = 40;
	private static final double WATCHDOG_RANGE = 64.0;
	private static final int WATCHDOG_TICKS = 200;
	private static final int WATCHDOG_INTERVAL = 20;
	/** Ticks a fresh loop has to become active before it counts as stopped by the engine. */
	private static final int START_GRACE = 20;

	private static @Nullable SoundEvent wanted;
	private static @Nullable KaastMusicSound playing;
	private static int playingAge;
	private static final List<KaastMusicSound> FADING = new ArrayList<>();
	private static @Nullable ClientLevel lastLevel;
	private static int watchdogTicks;
	private static int unseenTicks;

	public static void init() {
		ClientEffects.addListener(KaastMusic::onEvent);
		ClientTickEvents.END_CLIENT_TICK.register(KaastMusic::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopNow());
	}

	/** True while a Kaast loop plays or fades out: vanilla background music is held off. */
	public static boolean holdsVanillaMusic() {
		return playing != null || !FADING.isEmpty();
	}

	private static void onEvent(EffectEventPayload payload) {
		if (!KaastEffects.EVENT.equals(payload.effect()) || payload.variant() != KaastEffects.MUSIC) {
			return;
		}

		wanted = switch (Math.round(payload.intensity())) {
			case 1 -> ModSounds.MUSIC_KAAST_HUNT;
			case 2 -> ModSounds.MUSIC_KAAST_TRAP;
			case 3 -> ModSounds.MUSIC_KAAST_LAST_STAND;
			default -> null;
		};
		unseenTicks = 0;
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			stopNow();
		}

		if (mc.level == null || mc.player == null) {
			return;
		}

		if (wanted != null && (!mc.player.isAlive() || kaastLost(mc))) {
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

		FADING.removeIf(KaastMusicSound::isStopped);

		if (playing != null && ++playingAge > START_GRACE && !mc.getSoundManager().isActive(playing)) {
			// Stopped from outside (a sound reload or /stopsound): the music ends here.
			playing = null;
			wanted = null;
		}
	}

	private static void start(Minecraft mc, SoundEvent track) {
		for (Iterator<KaastMusicSound> it = FADING.iterator(); it.hasNext();) {
			KaastMusicSound sound = it.next();

			if (sound.track() == track && !sound.isStopped()) {
				it.remove();
				sound.fadeIn();
				playing = sound;
				return;
			}
		}

		if (!holdsVanillaMusic()) {
			// The first loop: stop the vanilla track once; the mixin keeps the next one away.
			mc.getMusicManager().stopPlaying();
		}

		playing = new KaastMusicSound(track);
		playingAge = 0;
		mc.getSoundManager().play(playing);
	}

	/** The watchdog: true once no Torvin Kaast has been within range for {@value #WATCHDOG_TICKS} ticks. */
	private static boolean kaastLost(Minecraft mc) {
		if (++watchdogTicks < WATCHDOG_INTERVAL) {
			return false;
		}

		watchdogTicks = 0;
		boolean near = !mc.level.getEntitiesOfClass(Entity.class, mc.player.getBoundingBox().inflate(WATCHDOG_RANGE),
				e -> e.getType() == KaastEntities.TORVIN_KAAST && e.isAlive()).isEmpty();
		unseenTicks = near ? 0 : unseenTicks + WATCHDOG_INTERVAL;
		return unseenTicks >= WATCHDOG_TICKS;
	}

	private static void stopNow() {
		wanted = null;

		if (playing != null) {
			playing.end();
			playing = null;
		}

		FADING.forEach(KaastMusicSound::end);
		FADING.clear();
		unseenTicks = 0;
	}

	private KaastMusic() {
	}
}
