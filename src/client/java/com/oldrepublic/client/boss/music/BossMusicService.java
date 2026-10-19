package com.oldrepublic.client.boss.music;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;

/** The generic boss music service (MILESTONE_5.md 12.5); W8 owns it. Phase 0 stub. */
public final class BossMusicService {
	private static final Map<String, Entry> BOSSES = new HashMap<>();

	/** A registered boss: the entity type the watchdog looks for, and its loops (track 1 is the first). */
	private record Entry(EntityType<?> boss, List<SoundEvent> tracks) {
	}

	/**
	 * Registers {@code bossId}'s music: {@code tracks} are its loops, track 1 to n of the {@code boss_music} payload (12.5).
	 * Phase 0 stub (W8 replaces it): kept here, nothing plays.
	 */
	public static void register(String bossId, EntityType<?> boss, List<SoundEvent> tracks) {
		BOSSES.put(bossId, new Entry(boss, List.copyOf(tracks)));
	}

	private BossMusicService() {
	}
}
