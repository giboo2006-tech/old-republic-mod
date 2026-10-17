package com.oldrepublic.world.site;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The structure locator's work queue (spec 3.8): searches wait in one queue, and the queue checks
 * at most {@code cellsPerTick} placement cells per {@link #tick} across all of them, so a search that
 * finds nothing costs a few ticks of small work and never a stall. Results are cached per player,
 * kind and filter for {@code cacheTicks}, or until the player moves {@code cacheMove} blocks.
 *
 * <p>What a cell is and what it holds is the {@link Lookup}'s business: the server's queue uses the
 * worldgen lookup of {@link StructureLocator}; tests build their own queue with a fake lookup and
 * never touch the server's. A search walks its cells in the order the lookup plans them (nearest
 * ring first); once a ring holds a result, the rest of that ring is checked and the nearest result
 * wins, as vanilla's random-spread search does. Results farther than the radius, and results the
 * search's filter excludes, are dropped.
 */
public final class LocatorQueue {
	/** One placement cell to check, in the lookup's own coordinates; {@code ring} counts outward from 0. */
	public record Cell(int x, int z, int ring) {
	}

	/**
	 * One search: a player's request for the nearest site of {@code kind} within {@code radiusBlocks}
	 * (horizontal) of {@code from}.
	 *
	 * @param exclude true for pieces the caller does not want (for example sites whose holocron the player already took)
	 */
	public record Search(ServerLevel level, UUID player, SiteKind kind, BlockPos from, int radiusBlocks, @Nullable Predicate<SitePiece> exclude) {
	}

	/** Where cells come from and what is in them. */
	public interface Lookup {
		/** The cells to check for a search, nearest ring first; empty when there is nothing to search (the search ends with null). */
		List<Cell> plan(Search search);

		/** The start pieces of the sites in one cell, at any distance and unfiltered (the queue filters). */
		List<SitePiece> check(Search search, Cell cell);
	}

	private record CacheKey(UUID player, SiteKind kind, boolean filtered) {
	}

	private record CacheEntry(ResourceKey<Level> dimension, BlockPos from, @Nullable SitePiece result, long time) {
	}

	private static final class Pending {
		final Search search;
		final List<Consumer<@Nullable SitePiece>> callbacks = new ArrayList<>(1);
		@Nullable List<Cell> plan;
		int next;
		int hitRing = -1;
		@Nullable SitePiece best;
		double bestDistSqr = Double.MAX_VALUE;

		Pending(Search search) {
			this.search = search;
		}
	}

	/** Default cache lifetime in ticks ({@code sites.locatorCacheTicks}). */
	public static final int DEFAULT_CACHE_TICKS = 600;
	/** Default move in blocks that makes a cached result stale ({@code sites.locatorCacheMove}). */
	public static final int DEFAULT_CACHE_MOVE = 48;

	private final Lookup lookup;
	private final int cellsPerTick;
	private final int cacheTicks;
	private final int cacheMove;
	private final ArrayDeque<Pending> queue = new ArrayDeque<>();
	private final Map<CacheKey, Pending> pendingByKey = new HashMap<>();
	private final Map<CacheKey, CacheEntry> cache = new HashMap<>();

	/** A queue checking at most {@code cellsPerTick} cells per tick, with the default cache. */
	public LocatorQueue(Lookup lookup, int cellsPerTick) {
		this(lookup, cellsPerTick, DEFAULT_CACHE_TICKS, DEFAULT_CACHE_MOVE);
	}

	/** A queue checking at most {@code cellsPerTick} cells per tick, keeping results {@code cacheTicks} or until a {@code cacheMove}-block move. */
	public LocatorQueue(Lookup lookup, int cellsPerTick, int cacheTicks, int cacheMove) {
		this.lookup = lookup;
		this.cellsPerTick = Math.max(1, cellsPerTick);
		this.cacheTicks = cacheTicks;
		this.cacheMove = cacheMove;
	}

	/**
	 * Asks for a search. A fresh cached result for the same player, kind and filter (and, with a
	 * filter, one the filter still accepts) is handed to {@code callback} before this returns;
	 * otherwise the callback runs during a later {@link #tick}, with null when nothing was found. A
	 * second request while the same search is waiting joins it.
	 */
	public void submit(Search search, long now, Consumer<@Nullable SitePiece> callback) {
		CacheKey key = new CacheKey(search.player(), search.kind(), search.exclude() != null);
		CacheEntry cached = cache.get(key);

		if (cached != null && fresh(cached, search.level().dimension(), search.from(), now)
				&& (cached.result() == null || search.exclude() == null || !search.exclude().test(cached.result()))) {
			deliver(callback, cached.result());
			return;
		}

		Pending pending = pendingByKey.get(key);

		if (pending == null) {
			pending = new Pending(search);
			pendingByKey.put(key, pending);
			queue.add(pending);
		}

		pending.callbacks.add(callback);
	}

	/**
	 * The freshest cached result of this player's searches for {@code kind} (filtered or not), read
	 * synchronously; null when there is none or it went stale. Never queues a search.
	 */
	public @Nullable SitePiece cached(UUID player, SiteKind kind, ResourceKey<Level> dimension, BlockPos at, long now) {
		CacheEntry plain = cache.get(new CacheKey(player, kind, false));
		CacheEntry filtered = cache.get(new CacheKey(player, kind, true));
		CacheEntry best = null;

		for (CacheEntry entry : new CacheEntry[] {plain, filtered}) {
			if (entry != null && fresh(entry, dimension, at, now) && (best == null || entry.time() > best.time())) {
				best = entry;
			}
		}

		return best == null ? null : best.result();
	}

	/**
	 * Works the queue: checks at most {@code cellsPerTick} cells in all, finishing every search whose
	 * plan is done (a search with nothing to check finishes without using the budget). Returns the
	 * number of cells checked.
	 */
	public int tick(long now) {
		int checked = 0;

		while (!queue.isEmpty()) {
			Pending pending = queue.peek();

			if (pending.plan == null) {
				pending.plan = plan(pending.search);
			}

			while (checked < cellsPerTick && !done(pending)) {
				check(pending, pending.plan.get(pending.next++));
				checked++;
			}

			if (!done(pending)) {
				break;
			}

			queue.poll();
			finish(pending, now);
		}

		return checked;
	}

	/** Whether no search is waiting. */
	public boolean idle() {
		return queue.isEmpty();
	}

	/** Drops a player's cache and waiting searches (their callbacks never run). */
	public void forget(UUID player) {
		cache.keySet().removeIf(key -> key.player().equals(player));
		queue.removeIf(pending -> pending.search.player().equals(player));
		pendingByKey.keySet().removeIf(key -> key.player().equals(player));
	}

	/** Drops everything (server stop). */
	public void clear() {
		cache.clear();
		queue.clear();
		pendingByKey.clear();
	}

	/**
	 * Rings of placement cells to search for {@code radiusBlocks} with a structure set spacing of
	 * {@code spacingChunks}: {@code ceil(radius / (16 × spacing)) + 1}, at most {@code maxRings} (3.8).
	 */
	public static int rings(int radiusBlocks, int spacingChunks, int maxRings) {
		int cells = (int) Math.ceil(Math.max(0, radiusBlocks) / (16.0 * Math.max(1, spacingChunks))) + 1;
		return Math.max(1, Math.min(maxRings, cells));
	}

	/**
	 * The cells of rings 0 to {@code rings - 1} around cell ({@code centreX}, {@code centreZ}),
	 * ring by ring: ring 0 is the centre, ring {@code r} the {@code 8r} cells at Chebyshev distance
	 * {@code r}.
	 */
	public static List<Cell> ringCells(int centreX, int centreZ, int rings) {
		List<Cell> cells = new ArrayList<>();

		for (int r = 0; r < rings; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
						cells.add(new Cell(centreX + dx, centreZ + dz, r));
					}
				}
			}
		}

		return cells;
	}

	/** Horizontal distance squared from {@code from} to the centre of the piece's box. */
	public static double distSqr(BlockPos from, SitePiece piece) {
		double dx = (piece.box().minX() + piece.box().maxX()) / 2.0 - from.getX();
		double dz = (piece.box().minZ() + piece.box().maxZ()) / 2.0 - from.getZ();
		return dx * dx + dz * dz;
	}

	private List<Cell> plan(Search search) {
		try {
			return List.copyOf(lookup.plan(search));
		} catch (RuntimeException e) {
			OldRepublicMod.LOGGER.warn("Site search plan failed for {}", search.kind(), e);
			return List.of();
		}
	}

	/** True once every cell is checked, or the next cell lies beyond the ring that held a result. */
	private static boolean done(Pending pending) {
		List<Cell> plan = pending.plan;
		return plan == null || pending.next >= plan.size() || (pending.hitRing >= 0 && plan.get(pending.next).ring() > pending.hitRing);
	}

	private void check(Pending pending, Cell cell) {
		List<SitePiece> found;

		try {
			found = lookup.check(pending.search, cell);
		} catch (RuntimeException e) {
			OldRepublicMod.LOGGER.warn("Site search failed in cell {}", cell, e);
			return;
		}

		Search search = pending.search;
		double radiusSqr = (double) search.radiusBlocks() * search.radiusBlocks();

		for (SitePiece piece : found) {
			double distSqr = distSqr(search.from(), piece);

			if (distSqr > radiusSqr || (search.exclude() != null && search.exclude().test(piece))) {
				continue;
			}

			if (distSqr < pending.bestDistSqr) {
				pending.best = piece;
				pending.bestDistSqr = distSqr;
			}

			if (pending.hitRing < 0) {
				pending.hitRing = cell.ring();
			}
		}
	}

	private void finish(Pending pending, long now) {
		Search search = pending.search;
		CacheKey key = new CacheKey(search.player(), search.kind(), search.exclude() != null);
		pendingByKey.remove(key);
		cache.put(key, new CacheEntry(search.level().dimension(), search.from(), pending.best, now));

		for (Consumer<@Nullable SitePiece> callback : pending.callbacks) {
			deliver(callback, pending.best);
		}
	}

	private boolean fresh(CacheEntry entry, ResourceKey<Level> dimension, BlockPos at, long now) {
		long age = now - entry.time();
		return entry.dimension().equals(dimension) && age >= 0 && age < cacheTicks && entry.from().distSqr(at) < (double) cacheMove * cacheMove;
	}

	private static void deliver(Consumer<@Nullable SitePiece> callback, @Nullable SitePiece result) {
		try {
			callback.accept(result);
		} catch (RuntimeException e) {
			OldRepublicMod.LOGGER.error("Site search callback failed", e);
		}
	}
}
