package com.oldrepublic.enemy.trooper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * Player UUID to expiry game time, at most {@link #MAX} entries (spec 5.2): a trooper's
 * {@code Provoked} and {@code Engaged} lists. Adding a fifth player drops the entry that expires
 * soonest. Saved as a list of {@code {Player, Until}} compounds. Pure: the caller passes the time.
 */
public final class PlayerExpiries {
	/** Entries kept per list. */
	public static final int MAX = 4;

	/** One saved entry. */
	public record Entry(UUID player, long until) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("Player").forGetter(Entry::player),
				Codec.LONG.fieldOf("Until").forGetter(Entry::until)
		).apply(i, Entry::new));
	}

	/** The saved form: a list of {@link Entry}. */
	public static final Codec<PlayerExpiries> CODEC = Entry.CODEC.listOf().xmap(PlayerExpiries::of, PlayerExpiries::entries);

	private final Map<UUID, Long> until = new LinkedHashMap<>();

	/** Builds a list from saved entries (the last {@link #MAX} that would survive {@link #put}). */
	public static PlayerExpiries of(List<Entry> entries) {
		PlayerExpiries expiries = new PlayerExpiries();

		for (Entry entry : entries) {
			expiries.put(entry.player(), entry.until());
		}

		return expiries;
	}

	/**
	 * Sets {@code player}'s expiry, keeping the later one if it already has an entry. With {@link #MAX}
	 * other players listed, the one that expires soonest makes room.
	 */
	public void put(UUID player, long expiresAt) {
		Long current = until.get(player);

		if (current != null) {
			until.put(player, Math.max(current, expiresAt));
			return;
		}

		if (until.size() >= MAX) {
			UUID soonest = null;
			long soonestUntil = Long.MAX_VALUE;

			for (Map.Entry<UUID, Long> entry : until.entrySet()) {
				if (entry.getValue() < soonestUntil) {
					soonest = entry.getKey();
					soonestUntil = entry.getValue();
				}
			}

			until.remove(soonest);
		}

		until.put(player, expiresAt);
	}

	/** Whether {@code player} has an entry that has not expired at game time {@code now}. */
	public boolean active(UUID player, long now) {
		Long expiresAt = until.get(player);
		return expiresAt != null && now < expiresAt;
	}

	/** Removes entries that expired before {@code now}. */
	public void prune(long now) {
		until.values().removeIf(expiresAt -> now >= expiresAt);
	}

	public void remove(UUID player) {
		until.remove(player);
	}

	public void clear() {
		until.clear();
	}

	public boolean isEmpty() {
		return until.isEmpty();
	}

	public int size() {
		return until.size();
	}

	/** The players with an entry active at {@code now}. */
	public List<UUID> activePlayers(long now) {
		List<UUID> players = new ArrayList<>();

		for (Map.Entry<UUID, Long> entry : until.entrySet()) {
			if (now < entry.getValue()) {
				players.add(entry.getKey());
			}
		}

		return players;
	}

	/** Every entry, in insertion order (the saved form). */
	public List<Entry> entries() {
		List<Entry> entries = new ArrayList<>(until.size());
		until.forEach((player, expiresAt) -> entries.add(new Entry(player, expiresAt)));
		return entries;
	}
}
