package com.oldrepublic.client.boss.music;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.raid.BossMusicPayload;
import com.oldrepublic.client.config.ClientConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * The generic boss music service (spec MILESTONE_5.md 12.5): one registry of bosses and their loops,
 * driven by the {@code boss_music(bossId, track)} payload the bosses' sessions send their
 * participants. Track 1 to n crossfades to that boss's n-th loop over {@value #FADE_TICKS} ticks; 0
 * fades out. A loop already playing is never restarted, and a loop still fading out comes back
 * instead of starting again.
 *
 * <p>Every stop path: track 0; another loop crossfading in; the local player's death; a level or
 * dimension change and disconnect (at once); the client toggle {@code ClientConfig.bossMusic} (fades
 * out; turning it back on mid-fight resumes the loop); the engine stopping the sound (a reload,
 * {@code /stopsound}); and a watchdog that fades the music when no entity of the boss's registered
 * type has been within {@value #WATCHDOG_RANGE} blocks for {@value #WATCHDOG_TICKS} ticks, in case a
 * stop was lost. While a loop plays or fades, vanilla background music is held off
 * ({@code mixin.boss.MusicManagerBossMusicMixin}). It replaces the M3 {@code BossMusic} and the M4
 * {@code KaastMusic}: the Collector and Kaast keep their three loops each.
 */
public final class BossMusicService {
	/** Crossfade length in ticks. */
	static final int FADE_TICKS = 40;
	/** How far the watchdog looks for the boss. */
	private static final double WATCHDOG_RANGE = 64.0;
	/** Ticks without the boss nearby before the watchdog fades the music. */
	private static final int WATCHDOG_TICKS = 200;
	/** How often the watchdog looks, in ticks. */
	private static final int WATCHDOG_INTERVAL = 20;
	/** Ticks a freshly started loop has to become active before it counts as stopped by the engine. */
	private static final int START_GRACE = 20;

	private static final Map<String, Entry> BOSSES = new HashMap<>();

	/** A registered boss: the entity type the watchdog looks for, and its loops (track 1 is the first). */
	private record Entry(EntityType<?> boss, List<SoundEvent> tracks) {
	}

	/** The boss whose music is wanted, and the loop, or null. */
	private static @Nullable String wantedBoss;
	private static @Nullable SoundEvent wanted;
	/** The loop fading in or playing. */
	private static @Nullable BossMusicSound playing;
	private static int playingAge;
	/** Loops fading out. */
	private static final List<BossMusicSound> FADING = new ArrayList<>();
	private static @Nullable ClientLevel lastLevel;
	private static int watchdogTicks;
	private static int unseenTicks;
	private static boolean initialised;

	/**
	 * Registers {@code bossId}'s music: {@code tracks} are its loops, track 1 to n of the {@code boss_music}
	 * payload; the watchdog looks for entities of {@code boss}. Registering an id again replaces it.
	 */
	public static void register(String bossId, EntityType<?> boss, List<SoundEvent> tracks) {
		BOSSES.put(bossId, new Entry(boss, List.copyOf(tracks)));
	}

	/** Whether {@code bossId} has music registered. */
	public static boolean isRegistered(String bossId) {
		return BOSSES.containsKey(bossId);
	}

	/** {@code bossId}'s loops in track order (empty when it is not registered). */
	public static List<SoundEvent> tracks(String bossId) {
		Entry entry = BOSSES.get(bossId);
		return entry == null ? List.of() : entry.tracks();
	}

	/** The entity type the watchdog looks for when {@code bossId} plays, or null when it is not registered. */
	public static @Nullable EntityType<?> bossType(String bossId) {
		Entry entry = BOSSES.get(bossId);
		return entry == null ? null : entry.boss();
	}

	/** The loop the fights ask for now (for the client tests), or null. */
	public static @Nullable SoundEvent wanted() {
		return wanted;
	}

	/** Hooks the payload, the client tick and the disconnect. Called once from {@code FoesM5Client}. */
	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(BossMusicPayload.TYPE, (payload, context) -> onMusic(payload.bossId(), payload.track()));
		ClientTickEvents.END_CLIENT_TICK.register(BossMusicService::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopNow());
	}

	/** True while a boss loop plays or fades out: vanilla background music is held off. */
	public static boolean holdsVanillaMusic() {
		return playing != null || !FADING.isEmpty();
	}

	/**
	 * A {@code boss_music} arrived (client thread): {@code track} 1 to n of {@code bossId}'s loops, or 0
	 * to stop. A stop for another boss than the one playing is ignored (two fights' messages crossing).
	 */
	static void onMusic(String bossId, int track) {
		Entry entry = BOSSES.get(bossId);

		if (track <= 0) {
			if (wantedBoss == null || wantedBoss.equals(bossId)) {
				wanted = null;
				wantedBoss = null;
			}

			return;
		}

		if (entry == null || entry.tracks().isEmpty()) {
			return;
		}

		wanted = entry.tracks().get(Math.min(track, entry.tracks().size()) - 1);
		wantedBoss = bossId;
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
			wantedBoss = null;
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
			wantedBoss = null;
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
			// The first boss loop: stop the vanilla track once; the mixin keeps the next one away.
			mc.getMusicManager().stopPlaying();
		}

		playing = new BossMusicSound(track);
		playingAge = 0;
		mc.getSoundManager().play(playing);
	}

	/**
	 * The watchdog: true once no entity of the wanted boss's type has been within
	 * {@value #WATCHDOG_RANGE} blocks for {@value #WATCHDOG_TICKS} ticks (the fight ended without the
	 * stop reaching this client).
	 */
	private static boolean bossLost(Minecraft mc) {
		if (++watchdogTicks < WATCHDOG_INTERVAL) {
			return false;
		}

		watchdogTicks = 0;
		Entry entry = wantedBoss == null ? null : BOSSES.get(wantedBoss);

		if (entry == null || mc.level == null || mc.player == null) {
			return true;
		}

		EntityType<?> type = entry.boss();
		boolean near = !mc.level.getEntitiesOfClass(Entity.class, mc.player.getBoundingBox().inflate(WATCHDOG_RANGE),
				e -> e.getType() == type && e.isAlive()).isEmpty();
		unseenTicks = near ? 0 : unseenTicks + WATCHDOG_INTERVAL;
		return unseenTicks >= WATCHDOG_TICKS;
	}

	/** Stops every loop at once and forgets the fight (disconnect, level change). */
	private static void stopNow() {
		wanted = null;
		wantedBoss = null;

		if (playing != null) {
			playing.end();
			playing = null;
		}

		FADING.forEach(BossMusicSound::end);
		FADING.clear();
		unseenTicks = 0;
	}

	private BossMusicService() {
	}
}
