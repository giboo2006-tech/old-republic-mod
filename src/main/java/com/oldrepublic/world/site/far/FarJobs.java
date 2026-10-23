package com.oldrepublic.world.site.far;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.world.site.PlacementRules;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SiteGround;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SiteMeta;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The running side of far placement (docs/MILESTONE_6.md 3.1): turns requests into saved entries
 * ({@link FarSiteData}) and, every server tick, chooses targets within the shared sample budget
 * ({@code far.samplesPerTick}, oldest request first), holds the {@code oldrepublic:far_site} tickets,
 * places one loaded request, lets lingering tickets go and fails what waited too long.
 *
 * <p>Only the entries are saved. The choosers, the callers' spot checks and the tickets live in
 * memory: after a restart a choosing request starts its chooser over (deterministic, it comes to the
 * same target for the same world), a loading one takes its ticket again, and one that was given a
 * spot check waits for its caller to ask again (which hands the check back) until it times out.
 *
 * <p>A target whose surroundings refuse the site (another site, a worldgen piece, a built block,
 * spawn protection or the caller's check) is kept away from and another is chosen, up to
 * {@value #MAX_TARGETS} targets in all.
 */
final class FarJobs {
	/** Targets a request tries before it fails. */
	static final int MAX_TARGETS = 3;
	/** Ticks between refreshes of a held ticket. */
	static final long REFRESH_TICKS = 200L;
	/** The ticket type's timeout: a ticket nobody refreshes any more goes by itself. */
	static final long TICKET_TIMEOUT = 3 * REFRESH_TICKS;
	/** The footprint assumed for a template that cannot be read (its request fails when it is placed). */
	private static final int FALLBACK_FOOTPRINT = 16;
	/** Blocks around a ground point's footprint that are loaded before it counts as placed. */
	private static final int POINT_MARGIN = 2;

	/** A choosing request's chooser and the ground it reads. */
	private record Job(FarChooser chooser, FarChooser.Ground ground) {
	}

	/** A ticket held for a request; {@code releaseAt} is {@link Long#MAX_VALUE} while the request needs it. */
	private record Held(ResourceKey<Level> dimension, ChunkPos chunk, int radius, long refreshedAt, long releaseAt) {
		boolean sameTicket(Held other) {
			return dimension.equals(other.dimension) && chunk.equals(other.chunk) && radius == other.radius;
		}
	}

	private static final Map<String, Job> JOBS = new HashMap<>();
	private static final Map<String, FarSites.SpotCheck> CHECKS = new HashMap<>();
	private static final Map<String, Held> HELD = new HashMap<>();
	/** When each loading request began to wait in this run (a restart starts the wait again). */
	private static final Map<String, Long> WAITING = new HashMap<>();

	/** {@link FarSites#request}: the stored request's state, or a new one's. */
	static FarSites.Status request(ServerLevel level, FarSites.Request request) {
		MinecraftServer server = level.getServer();
		long now = Clocks.time(server);
		FarSiteData.Entry entry = FarSiteData.get(server, request.key);

		if (entry == null) {
			entry = create(level, request, now);
			FarSiteData.put(server, entry);

			if (entry.phase() == FarSites.Phase.LOADING) {
				hold(level, entry, now);
			} else if (entry.phase() == FarSites.Phase.FAILED) {
				logEnd(entry);
			}
		}

		if (request.check != null && entry.active()) {
			CHECKS.put(request.key, request.check);
		}

		return entry.status();
	}

	/** {@link FarSites#poll}. */
	static FarSites.Status poll(ServerLevel level, String key) {
		FarSiteData.Entry entry = FarSiteData.get(level.getServer(), key);
		return entry != null ? entry.status() : new FarSites.Status(FarSites.Phase.FAILED, null, null, "unknown");
	}

	/** {@link FarSites#cancel}: forgets the request and lets its ticket go at once. */
	static void cancel(ServerLevel level, String key) {
		MinecraftServer server = level.getServer();
		FarSiteData.remove(server, key);
		JOBS.remove(key);
		CHECKS.remove(key);
		WAITING.remove(key);
		Held held = HELD.remove(key);

		if (held != null) {
			unticket(server, held);
		}
	}

	/** The server tick: choose, load, place, release (see the class comment). */
	static void tick(MinecraftServer server) {
		List<FarSiteData.Entry> active = new ArrayList<>();

		for (FarSiteData.Entry entry : FarSiteData.all(server)) {
			if (entry.active()) {
				active.add(entry);
			}
		}

		long now = Clocks.time(server);
		SiteBalance.Far far = SiteBalance.get().far;
		int budget = Math.max(1, far.samplesPerTick);
		boolean mayPlace = true;

		for (FarSiteData.Entry entry : active) {
			ServerLevel level = server.getLevel(entry.dimension());

			try {
				if (level == null) {
					finish(server, entry.failed("no_level", now), now);
				} else if (entry.phase() == FarSites.Phase.CHOOSING) {
					if (budget > 0) {
						budget -= choose(level, entry, budget, far, now);
					}
				} else if (load(level, entry, far, now, mayPlace)) {
					mayPlace = false;
				}
			} catch (RuntimeException e) {
				OldRepublicMod.LOGGER.error("Far placement {} failed", entry.key(), e);
				finish(server, entry.failed("error", now), now);
			}
		}

		refreshAndRelease(server, now);
	}

	/** Forgets everything held in memory (the server stopped; its tickets went with it). */
	static void clear() {
		JOBS.clear();
		CHECKS.clear();
		HELD.clear();
		WAITING.clear();
	}

	/**
	 * The request radius in chunks whose loaded area, one chunk inside its border, reaches
	 * {@code reach} blocks from the target all round: the readiness check ({@link #ready}) waits for
	 * the chunks up to r + 1 away, and the ticket is taken that wide ({@link #ticketed}).
	 */
	static int radius(int reach, int maxRadius) {
		return Math.clamp((reach + 15) / 16 - 1, 1, Math.max(1, maxRadius));
	}

	/**
	 * The radius a request of {@code radius} takes its ticket with: one chunk wider, since a ticket of
	 * radius r makes chunks full only up to r away and {@link #ready} waits for those up to r + 1.
	 */
	static int ticketed(int radius) {
		return radius + 1;
	}

	/** Whether every chunk within {@code radius} + 1 of the target's chunk is loaded. */
	static boolean ready(ServerLevel level, BlockPos target, int radius) {
		ChunkPos centre = ChunkPos.containing(target);
		int reach = radius + 1;

		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (!level.hasChunk(centre.x() + dx, centre.z() + dz)) {
					return false;
				}
			}
		}

		return true;
	}

	/** A new request's entry: choosing, or loading at once for a fixed stamp, or failed when its template cannot be placed. */
	private static FarSiteData.Entry create(ServerLevel level, FarSites.Request request, long now) {
		FarSiteData.Shape shape = new FarSiteData.Shape(request.template, request.from, request.minDistance, request.maxDistance, request.footprint,
				request.fixedRotation);
		FarSiteData.Prefs prefs = new FarSiteData.Prefs(request.bearing, request.spreadDegrees, request.biomes, request.biome, request.avoid, request.separation,
				request.terrain, request.loadRadius, request.kind, request.check != null);
		FarSiteData.Entry entry = FarSiteData.Entry.choosing(request.key, level.dimension(), request.owner, shape, prefs, now);
		Identifier template = request.template;

		if (template == null) {
			return entry;
		} else if (request.kind == null && SiteKind.ofTemplate(template) == null) {
			return entry.failed("not_a_site_template", now);
		} else if (request.fixedRotation != null) {
			BoundingBox box = RuntimeSites.boxOf(level, template, request.from, request.fixedRotation);

			if (box == null) {
				return entry.failed("missing_template", now);
			}

			BlockPos target = new BlockPos(box.getCenter().getX(), box.minY() + RuntimeSites.buriedOf(template), box.getCenter().getZ());
			int reach = Math.max(box.getXSpan(), box.getZSpan()) / 2 + 2;
			int radius = request.loadRadius >= 0 ? request.loadRadius : radius(reach, SiteBalance.get().far.maxTicketRadius);
			return entry.loading(target, radius, "loading", now);
		}

		return level.getStructureManager().get(template).isPresent() ? entry : entry.failed("missing_template", now);
	}

	/** Works on a choosing request within {@code budget} samples; returns the samples spent. */
	private static int choose(ServerLevel level, FarSiteData.Entry entry, int budget, SiteBalance.Far far, long now) {
		Job job = JOBS.get(entry.key());

		if (job == null) {
			job = job(level, entry, far);
			JOBS.put(entry.key(), job);
		}

		int used = job.chooser().step(job.ground(), budget);

		if (!job.chooser().done()) {
			return used;
		}

		JOBS.remove(entry.key());
		FarChooser.Choice choice = job.chooser().choice();

		if (choice == null) {
			finish(level.getServer(), entry.failed("no_dry_ground", now), now);
			return used;
		}

		Identifier template = entry.shape().template();
		int reach = template != null ? RuntimeSites.farReach(level, template, far.searchSlack) : Math.max(1, entry.shape().footprint()) / 2 + POINT_MARGIN;
		int radius = entry.prefs().loadRadius() >= 0 ? entry.prefs().loadRadius() : radius(reach, far.maxTicketRadius);
		FarSiteData.Entry loading = entry.loading(choice.target(), radius, "loading", now);
		FarSiteData.put(level.getServer(), loading);
		hold(level, loading, now);
		return used;
	}

	/** A chooser for a request: its band, its footprint, the ground it prefers and the points it keeps away from. */
	private static Job job(ServerLevel level, FarSiteData.Entry entry, SiteBalance.Far far) {
		FarSiteData.Shape shape = entry.shape();
		FarSiteData.Prefs prefs = entry.prefs();
		Identifier template = shape.template();
		SiteMeta meta = template != null ? SiteMeta.of(template) : SiteMeta.DEFAULT;
		int footprint = template != null ? RuntimeSites.footprint(level, template, FALLBACK_FOOTPRINT) : Math.max(1, shape.footprint());
		FarSites.Terrain terrain = prefs.terrain() != null ? prefs.terrain() : template != null ? meta.terrain() : FarSites.Terrain.ANY;
		TagKey<Biome> biomes = prefs.biomes() != null ? prefs.biomes() : template != null ? meta.biomes() : null;
		FarChooser.Spec spec = new FarChooser.Spec(shape.from(), shape.minDistance(), shape.maxDistance(), footprint, terrain, prefs.bearing(), prefs.spread(),
				keep(level, entry, far), FarChooser.seed(entry.key(), level.getSeed()), far.anglesPerRing, far.ringsPerBand);
		return new Job(new FarChooser(spec), new NoiseGround(level, biomes, prefs.biome(), far.structureRange));
	}

	/**
	 * The points a request's target keeps away from: the request's own ({@link FarSites.Request#avoiding}),
	 * its refused targets, every recorded site of the dimension (from the edge of its box), the other
	 * requests' targets and world spawn, the last four by {@code far.separation}.
	 */
	private static List<FarChooser.Keep> keep(ServerLevel level, FarSiteData.Entry entry, SiteBalance.Far far) {
		List<FarChooser.Keep> keep = new ArrayList<>();
		MinecraftServer server = level.getServer();

		for (BlockPos pos : entry.prefs().avoid()) {
			keep.add(new FarChooser.Keep(pos, entry.prefs().separation()));
		}

		for (BlockPos pos : entry.refused()) {
			keep.add(new FarChooser.Keep(pos, far.separation));
		}

		for (WorldSites.SiteRecord record : WorldSites.all(server)) {
			if (record.dimension().equals(level.dimension())) {
				SitePiece piece = RuntimeSites.pieceOf(level, record);
				BoundingBox box = piece != null ? piece.box() : new BoundingBox(record.origin());
				keep.add(new FarChooser.Keep(box.getCenter(), far.separation + Math.max(box.getXSpan(), box.getZSpan()) / 2));
			}
		}

		for (FarSiteData.Entry other : FarSiteData.all(server)) {
			if (other.phase() == FarSites.Phase.LOADING && other.target() != null && other.dimension().equals(level.dimension()) && !other.key().equals(entry.key())) {
				keep.add(new FarChooser.Keep(other.target(), far.separation));
			}
		}

		if (level.dimension() == Level.OVERWORLD) {
			keep.add(new FarChooser.Keep(level.getRespawnData().pos(), far.separation));
		}

		return keep;
	}

	/**
	 * Holds a loading request's ticket and, once its area is loaded, places it when {@code mayPlace}
	 * (one placement per tick); fails it once it has waited {@code far.loadTimeoutTicks}. Returns
	 * whether it placed or tried to.
	 */
	private static boolean load(ServerLevel level, FarSiteData.Entry entry, SiteBalance.Far far, long now, boolean mayPlace) {
		BlockPos target = entry.target();

		if (target == null) {
			finish(level.getServer(), entry.failed("no_target", now), now);
			return false;
		}

		hold(level, entry, now);
		long waitingSince = WAITING.computeIfAbsent(entry.key(), key -> now);
		boolean checkLost = entry.prefs().hasCheck() && !CHECKS.containsKey(entry.key());

		if (mayPlace && !checkLost && ready(level, target, entry.ticketRadius())) {
			place(level, entry, target, far, now);
			return true;
		}

		if (now - waitingSince > far.loadTimeoutTicks) {
			finish(level.getServer(), entry.failed(checkLost ? "check_lost" : "load_timeout", now), now);
		}

		return false;
	}

	/** The precise step: a ground point's ground, a fixed stamp, or a template's search around its target. */
	private static void place(ServerLevel level, FarSiteData.Entry entry, BlockPos target, SiteBalance.Far far, long now) {
		MinecraftServer server = level.getServer();
		FarSiteData.Shape shape = entry.shape();
		Identifier template = shape.template();

		if (template == null) {
			BlockPos below = SiteGround.belowGrowth(level, target.getX(), target.getZ());
			int y = below != null ? below.getY() + 1 : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
			finish(server, entry.placed(null, target.atY(y), "point", now), now);
			return;
		}

		FarSites.SpotCheck check = CHECKS.get(entry.key());
		Predicate<RuntimeSites.Spot> veto = check == null ? spot -> true : spot -> check.accept(level, spot);
		Rotation fixed = shape.fixedRotation();
		RuntimeSites.Outcome outcome;

		if (fixed != null) {
			outcome = RuntimeSites.placeExact(level, template, entry.prefs().kind(), shape.from(), fixed, veto, entry.owner());
		} else {
			SiteMeta meta = SiteMeta.of(template);
			FarSites.Terrain terrain = entry.prefs().terrain() != null ? entry.prefs().terrain() : meta.terrain();
			int footprint = RuntimeSites.footprint(level, template, FALLBACK_FOOTPRINT);
			outcome = RuntimeSites.placeFar(level, template, entry.prefs().kind(), target, Vec3.atBottomCenterOf(shape.from()), meta, terrain, far.searchSlack,
					PlacementRules.spawnProtection(level, footprint), veto, entry.owner());
		}

		RuntimeSites.Placement placement = outcome.placement();

		if (placement != null) {
			finish(server, entry.placed(FarSiteData.Placed.of(placement), placement.origin(), outcome.detail(), now), now);
		} else if (fixed != null || "missing_template".equals(outcome.detail()) || entry.refused().size() + 1 >= MAX_TARGETS) {
			finish(server, entry.failed(outcome.detail(), now), now);
		} else {
			OldRepublicMod.LOGGER.info("Far placement {}: target {} refused ({}), choosing another", entry.key(), target, outcome.detail());
			finish(server, entry.retry(outcome.detail(), now), now);
		}
	}

	/**
	 * Stores a request's new state (placed, failed, or choosing again) and drops what it no longer
	 * needs: its chooser and its wait, its check once it has ended, and its ticket (at once, or after
	 * {@code far.lingerTicks} once placed).
	 */
	private static void finish(MinecraftServer server, FarSiteData.Entry entry, long now) {
		String key = entry.key();
		FarSiteData.put(server, entry);
		JOBS.remove(key);
		WAITING.remove(key);
		logEnd(entry);

		if (!entry.active()) {
			CHECKS.remove(key);
		}

		Held held = HELD.get(key);
		int linger = SiteBalance.get().far.lingerTicks;

		if (held == null) {
			return;
		} else if (entry.phase() == FarSites.Phase.PLACED && linger > 0) {
			HELD.put(key, new Held(held.dimension(), held.chunk(), held.radius(), held.refreshedAt(), now + linger));
		} else {
			HELD.remove(key);
			unticket(server, held);
		}
	}

	/** One log line when a request ends: where it was placed and how, or why it failed (its caller falls back to its near search). */
	private static void logEnd(FarSiteData.Entry entry) {
		if (entry.phase() == FarSites.Phase.PLACED) {
			OldRepublicMod.LOGGER.info("Far placement {}: placed at {} in {} ({})", entry.key(), entry.target(), entry.dimension().identifier(), entry.detail());
		} else if (entry.phase() == FarSites.Phase.FAILED) {
			OldRepublicMod.LOGGER.info("Far placement {}: failed ({}){}", entry.key(), entry.detail(), entry.target() == null ? "" : " at " + entry.target());
		}
	}

	/** Takes the ticket of a loading request, or takes it again where its target or radius changed. */
	private static void hold(ServerLevel level, FarSiteData.Entry entry, long now) {
		BlockPos target = entry.target();

		if (target == null) {
			return;
		}

		Held wanted = new Held(entry.dimension(), ChunkPos.containing(target), entry.ticketRadius(), now, Long.MAX_VALUE);
		Held held = HELD.get(entry.key());

		if (held != null && held.sameTicket(wanted) && held.releaseAt() == Long.MAX_VALUE) {
			return;
		}

		if (held != null) {
			HELD.remove(entry.key());
			unticket(level.getServer(), held);
		}

		level.getChunkSource().addTicketWithRadius(FarSitesFeature.TICKET, wanted.chunk(), ticketed(wanted.radius()));
		HELD.put(entry.key(), wanted);
	}

	/** Refreshes held tickets before the type's timeout, and lets lingering ones go once their time has come. */
	private static void refreshAndRelease(MinecraftServer server, long now) {
		List<Held> released = new ArrayList<>();

		for (Iterator<Map.Entry<String, Held>> it = HELD.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Held> slot = it.next();
			Held held = slot.getValue();
			ServerLevel level = server.getLevel(held.dimension());

			if (level == null) {
				it.remove();
			} else if (now >= held.releaseAt()) {
				it.remove();
				released.add(held);
			} else if (now - held.refreshedAt() >= REFRESH_TICKS) {
				level.getChunkSource().addTicketWithRadius(FarSitesFeature.TICKET, held.chunk(), ticketed(held.radius()));
				slot.setValue(new Held(held.dimension(), held.chunk(), held.radius(), now, held.releaseAt()));
			}
		}

		for (Held held : released) {
			unticket(server, held);
		}
	}

	/** Removes a ticket no request holds any more (two requests can share one: same chunk and radius). */
	private static void unticket(MinecraftServer server, Held held) {
		ServerLevel level = server.getLevel(held.dimension());

		if (level == null) {
			return;
		}

		for (Held other : HELD.values()) {
			if (other.sameTicket(held)) {
				return;
			}
		}

		level.getChunkSource().removeTicketWithRadius(FarSitesFeature.TICKET, held.chunk(), ticketed(held.radius()));
	}

	private FarJobs() {
	}
}
