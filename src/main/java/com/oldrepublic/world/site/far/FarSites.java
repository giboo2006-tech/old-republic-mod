package com.oldrepublic.world.site.far;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteKind;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * Far placement (docs/MILESTONE_6.md section 3): puts a runtime site hundreds of blocks away in
 * terrain that suits it, without needing the chunks there to be loaded when it is asked for.
 *
 * <ol>
 * <li><b>Choose</b> a target in the request's distance band from the world generator's own noise
 * heights and biomes (nothing is loaded), time-sliced and deterministic by key.</li>
 * <li><b>Load</b> the area with an {@code oldrepublic:far_site} ticket.</li>
 * <li><b>Place</b> precisely once loaded (the surface search around the target, then a clearing
 * search, then a forced placement), facing {@code from}, recorded in {@code WorldSites}.</li>
 * <li><b>Linger</b>, then release the ticket.</li>
 * </ol>
 *
 * Requests are idempotent by key, and the chosen target survives a restart (global attachment
 * {@code oldrepublic:far_sites}). Callers poll every so often (their existing retry loops) and use
 * {@link Status#pointer()} for guidance meanwhile; on {@link Phase#FAILED} they fall back to their
 * near search.
 *
 * <p>Phase 0 contract (7.1): the signatures are frozen. The Phase 0 body below is a stub that
 * answers {@link Phase#FAILED} ("not_implemented"); J1 replaces it.
 */
public final class FarSites {
	/** Where a request stands. */
	public enum Phase {
		/** Scoring candidates from noise heights; {@link Status#target()} is null until one is chosen. */
		CHOOSING,
		/** A target is chosen and its chunks are loading; the site is placed when they are. */
		LOADING,
		/** Placed and recorded; {@link Status#placement()} is set. */
		PLACED,
		/** Nothing could be chosen or placed; the caller falls back to its near search. */
		FAILED
	}

	/** The kind of ground a request prefers. */
	public enum Terrain {
		ANY,
		/** Flat ground: the smallest spread of heights under the footprint. */
		FLAT,
		/** Ridges and hilltops: higher than the ground around, with a flat enough top. */
		HIGH,
		/** Valleys and low shores: lower than the ground around. */
		LOW
	}

	/**
	 * A request's state.
	 *
	 * @param target    the chosen ground point (null while choosing, and on some failures)
	 * @param placement the placed site (only when {@link Phase#PLACED})
	 * @param detail    a short machine-readable reason or note ("chosen", "loading", "no_dry_ground",
	 *                  "unknown", "not_implemented", ...), for logs and tests
	 */
	public record Status(Phase phase, @Nullable BlockPos target, RuntimeSites.@Nullable Placement placement, String detail) {
		public boolean placed() {
			return phase == Phase.PLACED;
		}

		public boolean failed() {
			return phase == Phase.FAILED;
		}

		/**
		 * Where guidance should point: the placed site's {@code HEART} anchor (its stand when it has none)
		 * once placed, else the chosen target, else null.
		 */
		public @Nullable BlockPos pointer() {
			if (placement != null) {
				return placement.origin();
			}

			return target;
		}
	}

	/** An extra veto on the precise spot once the area is loaded (the Reliquary's hall check). */
	@FunctionalInterface
	public interface SpotCheck {
		boolean accept(ServerLevel level, RuntimeSites.Spot spot);
	}

	/** What to place and where to look; built with the static factories and the chained options. */
	public static final class Request {
		final String key;
		final @Nullable UUID owner;
		final @Nullable Identifier template;
		final BlockPos from;
		final int minDistance;
		final int maxDistance;
		final int footprint;
		final @Nullable Rotation fixedRotation;
		@Nullable Vec3 bearing;
		float spreadDegrees = 360f;
		@Nullable TagKey<Biome> biomes;
		final List<BlockPos> avoid = new ArrayList<>();
		int separation;
		@Nullable Terrain terrain;
		@Nullable SpotCheck check;
		int loadRadius = -1;
		@Nullable SiteKind kind;
		boolean kindSet;

		private Request(String key, @Nullable UUID owner, @Nullable Identifier template, BlockPos from, int minDistance, int maxDistance,
				int footprint, @Nullable Rotation fixedRotation) {
			this.key = Objects.requireNonNull(key, "key");
			this.owner = owner;
			this.template = template;
			this.from = from.immutable();
			this.minDistance = Math.max(0, minDistance);
			this.maxDistance = Math.max(this.minDistance, maxDistance);
			this.footprint = footprint;
			this.fixedRotation = fixedRotation;
		}

		/**
		 * A template placed {@code minDistance} to {@code maxDistance} blocks from {@code from}, its
		 * entrance (local north) facing {@code from}.
		 *
		 * @param key a stable key, unique per site and owner ("story/&lt;uuid&gt;/crossing"); the same key
		 *            always means the same site
		 */
		public static Request of(String key, @Nullable UUID owner, Identifier template, BlockPos from, int minDistance, int maxDistance) {
			return new Request(key, owner, template, from, minDistance, maxDistance, 0, null);
		}

		/** A ground point with no template (a hunting ground, a cull): PLACED once its chunks are loaded. */
		public static Request point(String key, @Nullable UUID owner, BlockPos from, int minDistance, int maxDistance, int footprint) {
			return new Request(key, owner, null, from, minDistance, maxDistance, footprint, null);
		}

		/** A fixed stamp: {@code template} at {@code origin} turned by {@code rotation}, once its chunks are loaded. */
		public static Request at(String key, @Nullable UUID owner, Identifier template, BlockPos origin, Rotation rotation) {
			return new Request(key, owner, template, origin, 0, 0, 0, Objects.requireNonNull(rotation, "rotation"));
		}

		/** Prefer bearings (from {@code from}) within {@code spreadDegrees} of {@code direction}. */
		public Request facing(Vec3 direction, float spreadDegrees) {
			this.bearing = direction;
			this.spreadDegrees = spreadDegrees;
			return this;
		}

		/** Keep the target in these biomes. */
		public Request inBiomes(TagKey<Biome> biomes) {
			this.biomes = biomes;
			return this;
		}

		/** Keep the target at least {@code separation} blocks from each of {@code others}. */
		public Request avoiding(Collection<BlockPos> others, int separation) {
			this.avoid.addAll(others);
			this.separation = Math.max(this.separation, separation);
			return this;
		}

		/** The ground to prefer (default: the template's site meta, else {@link Terrain#ANY}). */
		public Request preferring(Terrain terrain) {
			this.terrain = terrain;
			return this;
		}

		/** An extra veto on the precise spot. */
		public Request check(SpotCheck check) {
			this.check = check;
			return this;
		}

		/** The ticket radius in chunks, instead of the one computed from the template. */
		public Request loadRadius(int chunks) {
			this.loadRadius = chunks;
			return this;
		}

		/** The {@code WorldSites} record kind (default: {@link SiteKind#ofTemplate}). */
		public Request kind(@Nullable SiteKind kind) {
			this.kind = kind;
			this.kindSet = true;
			return this;
		}

		public String key() {
			return key;
		}
	}

	/**
	 * Starts (or continues) a request and returns its state. Idempotent by key: asking again with the
	 * same key returns the same request's state and never places a second site.
	 */
	public static Status request(ServerLevel level, Request request) {
		// Phase 0 stub (J1 implements): callers take their near-search fallback.
		return new Status(Phase.FAILED, null, null, "not_implemented");
	}

	/** The state of the request with this key; {@link Phase#FAILED} with detail "unknown" when there is none. */
	public static Status poll(ServerLevel level, String key) {
		return new Status(Phase.FAILED, null, null, "unknown");
	}

	/** Forgets a request and releases its ticket (a placed site stays where it is). */
	public static void cancel(ServerLevel level, String key) {
	}

	/**
	 * "about 550 blocks to the north-east": the distance rounded to 50 and one of eight compass
	 * directions; "close by" under 100 blocks.
	 */
	public static Component describe(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);

		if (distance < 100) {
			return Component.translatable("message.oldrepublic.far.close");
		}

		long rounded = Math.round(distance / 50.0) * 50L;
		return Component.translatable("message.oldrepublic.far.about", rounded, Component.translatable("message.oldrepublic.far.dir." + compass(dx, dz)));
	}

	/** One of n, ne, e, se, s, sw, w, nw for a step of (dx, dz) (north is -z). */
	public static String compass(double dx, double dz) {
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = (int) Math.floorMod(Math.round(degrees / 45.0), 8);
		return switch (sector) {
			case 0 -> "n";
			case 1 -> "ne";
			case 2 -> "e";
			case 3 -> "se";
			case 4 -> "s";
			case 5 -> "sw";
			case 6 -> "w";
			default -> "nw";
		};
	}

	private FarSites() {
	}
}
