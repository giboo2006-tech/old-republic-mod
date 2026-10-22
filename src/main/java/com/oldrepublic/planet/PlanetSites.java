package com.oldrepublic.planet;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Pair;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.block.VerseStoneBlockEntity;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SiteMeta;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;
import com.oldrepublic.world.site.far.FarRequests;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Per-player runtime sites on the planets (spec MILESTONE_4.md 3.4), and the two shared ones.
 *
 * <p>Each site has a <b>ring point</b>: a direction from the hub seeded by the owner and the site
 * (the Heartstone's by the hub alone), at the middle of the site's rings. A site is placed by
 * {@link #ensure} once its owner stands in the planet's level within {@code planets.siteEnsureRange}
 * of that point (ambush rocks: near the owner, anywhere far enough from the hub and other sites),
 * so the ground there is loaded; until then {@link #ensure} returns null, callers retry (at most
 * once per {@code planets.siteRetryTicks} per owner and site) and the Calling points at
 * {@link #ringPoint}. Placement uses {@link RuntimeSites#placeSurface} around the hub with the first
 * candidate toward the ring point, then, when no ring candidate is bare and level enough, the
 * clearing search around the ring point ({@link PlanetSiteSearch}); the expeditions add their
 * underground piece below the surface piece with {@link RuntimeSites#placeSunken}. A placement by
 * the clearing search logs why each ring candidate was refused; the first attempt that finds no
 * spot at all logs why ({@link PlanetSiteSearch#explain}), once per owner and site. Positions are
 * kept in {@link PlanetSiteData}.
 *
 * <p>The position returned is the site's {@code HEART} anchor: the expeditions' surface entrance,
 * the cache's vault, the Heartstone's ring, the Sepulchre's hall, the camp's pit, the rocks' middle.
 *
 * <p>Milestone 5 (spec MILESTONE_5.md 3.2 to 3.4) appends the verse stones and the Hymnal on
 * Tyrvann, the debtor camp on Ghessar and the detention post on Varkhast. A site with a biome of its
 * own ({@link #biomeOf}: the three verse stones, the Hymnal, the debtor camp) has its ring point
 * moved to the nearest point of that biome ({@link ServerLevel#findClosestBiome3d}, which reads the
 * biome source and loads no chunk), and both searches keep to that biome; only when neither finds a
 * spot there does a last clearing search take any ground near the ring point (logged). A placed verse
 * stone is given its owner, its index and its guards' spot. The Hymnal's halls are sunk under its
 * arch by {@link Hymnal#sinkHalls}, and its position is the arch's {@code STAIR_MOUTH}. A missing
 * template still means "not yet": {@link #ensure} returns null and logs it once.
 *
 * <p>Milestone 6 (docs/MILESTONE_6.md 3.2): a site no longer waits for its owner to come near. The
 * first {@link #ensure} asks far placement ({@link FarSites}) for its surface piece anywhere between
 * its first and last ring from the hub, toward its ring point ({@code planets.siteBearingSpread}),
 * in its own biome when it has one; later calls poll it, and the one that finds it placed finishes it
 * (the underground piece, the Hymnal's halls through {@link Hymnal#requestHalls}, a verse stone's
 * set-up) and records it. Meanwhile {@link #ringPoint} is far placement's chosen target. Only when far
 * placement fails does the search above run, near the owner as before.
 */
public final class PlanetSites {
	/** Stored by ordinal: append only. A {@code switch} over sites needs a {@code default} branch. */
	public enum Site {
		SINGING_HOLLOW,
		DUSKGLASS_RIFT,
		WAYFARER_CACHE,
		HUNTING_CAMP,
		AMBUSH_ONE,
		AMBUSH_TWO,
		HEARTSTONE,
		SEPULCHRE,
		/** The three verse stones of each player on Tyrvann: stormwood, mire, basalt (MILESTONE_5.md 3.3.2). */
		VERSE_STONE_1,
		VERSE_STONE_2,
		VERSE_STONE_3,
		/** The Hymnal on Tyrvann, one per server (3.3.3). */
		HYMNAL,
		/** A player's debtor camp in Ghessar's canyons (3.2). */
		DEBTOR_CAMP,
		/** A player's Imperial detention post on Varkhast (3.4). */
		DETENTION_POST
	}

	/** One template of a site, with the layers it sets into the ground when it has no site meta. */
	public record Piece(Identifier template, int buried) {
		/** The layers it sets into the ground: its site meta's {@code buried}, else {@link #buried} (docs/MILESTONE_6.md 3.4). */
		public int layers() {
			return SiteMeta.buried(template, buried);
		}
	}

	public static final Piece HOLLOW_SURFACE = new Piece(OldRepublicMod.id("expedition/singing_hollow_surface"), 3);
	public static final Piece HOLLOW_GROTTO = new Piece(OldRepublicMod.id("expedition/singing_hollow_grotto"), 0);
	public static final Piece RIFT_SURFACE = new Piece(OldRepublicMod.id("expedition/duskglass_rift_surface"), 2);
	public static final Piece RIFT = new Piece(OldRepublicMod.id("expedition/duskglass_rift_rift"), 0);
	public static final Piece CACHE = new Piece(OldRepublicMod.id("planet/wayfarer_cache"), 4);
	public static final Piece CAMP = new Piece(OldRepublicMod.id("boss/hunting_camp"), 4);
	public static final Piece AMBUSH = new Piece(OldRepublicMod.id("mission/ambush_rocks"), 1);
	public static final Piece HEARTSTONE_PIECE = new Piece(Heartstones.TEMPLATE, 3);
	// Milestone 5 (18.1): W3's templates (tools/assets/gen_structures_m5.py), with the layers each sets into the ground.
	public static final Piece VERSE_STONE = new Piece(OldRepublicMod.id("site/verse_stone"), 2);
	/** The Hymnal's surface piece: the arch over the cleft, its stair running down 17 layers into the ridge (3.3.3). */
	public static final Piece HYMNAL_ARCH = new Piece(OldRepublicMod.id("site/hymnal_arch"), 17);
	/** The Hymnal's halls, stamped under the arch at {@link Hymnal#HALLS_OFFSET} ({@link Hymnal#sinkHalls}). */
	public static final Piece HYMNAL_HALLS = new Piece(OldRepublicMod.id("site/hymnal"), 0);
	public static final Piece DEBTOR_CAMP_PIECE = new Piece(OldRepublicMod.id("site/debtor_camp"), 2);
	public static final Piece DETENTION_POST_PIECE = new Piece(OldRepublicMod.id("site/detention_post"), 2);

	/** Owners' sites whose next placement attempt waits: owner, site, game time. */
	private static final Map<UUID, Map<Site, Long>> NEXT_TRY = new HashMap<>();
	/** Owners' sites whose failed placement has been logged with its reasons. */
	private static final Map<UUID, Set<Site>> EXPLAINED = new HashMap<>();
	/** Sites whose missing template has been logged (once per server run). */
	private static final Set<Site> MISSING = EnumSet.noneOf(Site.class);
	/** Ring points moved into their site's biome, by the seeded point they were searched from (deterministic, so kept for the run). */
	private static final Map<BlockPos, BlockPos> BIOME_POINTS = new HashMap<>();
	/** How far from its seeded point a ring point may move to reach its site's biome. */
	private static final int BIOME_SEARCH_RADIUS = 192;
	/** Grid step of that search (one biome sample per step; the planets' biomes do not change with height). */
	private static final int BIOME_SEARCH_STEP = 16;
	private static final long SALT = 0x5349544552494e47L;
	/** An existing ambush stays in use while its owner is this close to it. */
	private static final int AMBUSH_REUSE = 64;
	/** Far placement keys whose failure has been logged (once per server run). */
	private static final Set<String> FAR_FAILED = new HashSet<>();

	/**
	 * The site's position, placing it first when it is not placed yet; null when it cannot be placed
	 * now (far placement is still choosing or loading, or it failed and the owner is not near the ring
	 * point, or no ring fits). Callers retry later and point the Calling at the ring meanwhile
	 * ({@link #ringPoint}).
	 */
	public static @Nullable GlobalPos ensure(ServerPlayer owner, Site site, Planets.Planet planet) {
		GlobalPos found = find(owner, site);

		if (found != null && (!isAmbush(site) || reusable(owner, found))) {
			return found;
		}

		MinecraftServer server = owner.level().getServer();
		ServerLevel level = Planets.level(server, planet);

		if (level == null || !due(owner.getUUID(), site, Clocks.time(server)) || !templatesReady(level, site)) {
			return null;
		}

		if (site == Site.SEPULCHRE) {
			PlanetHubs.ensure(server, Planets.Planet.VARKHAST);
			return null;
		}

		BlockPos hub = isAmbush(site) ? null : PlanetHubs.hub(server, planet);

		if (!isAmbush(site) && hub == null) {
			PlanetHubs.ensure(server, planet);
			return null;
		}

		// Far placement needs nobody there; the ambushes (and far placement's near fallback) follow the owner.
		if (isAmbush(site) && !owner.level().dimension().equals(level.dimension())) {
			return null;
		}

		GlobalPos placed;

		try {
			placed = isAmbush(site) ? placeAmbush(level, owner) : place(level, owner, site, planet, hub);
		} catch (RuntimeException e) {
			OldRepublicMod.LOGGER.error("Placing {} for {} failed", site, owner.getName().getString(), e);
			return null;
		}

		if (placed == null) {
			return null;
		}

		PlanetSiteData.Data data = PlanetSiteData.snapshot(server);
		GlobalPos recorded = placed;

		if (shared(site)) {
			recorded = ensureShared(data, site, () -> placed);
		} else {
			data.putOwned(new PlanetSiteData.Owned(owner.getUUID(), site, placed, false));
		}

		PlanetSiteData.commit(server, data);
		return recorded;
	}

	/**
	 * The shared {@code site} in {@code data}, recording the one {@code placer} gives only when there
	 * is none yet (a test seam: one Heartstone, Sepulchre and Hymnal per server, whoever asks first).
	 */
	public static GlobalPos ensureShared(PlanetSiteData.Data data, Site site, Supplier<GlobalPos> placer) {
		GlobalPos pos = data.shared(site);

		if (pos == null) {
			pos = placer.get();
			data.putShared(site, pos);
		}

		return pos;
	}

	/** The site's position if it is placed for this owner, else null. Never places. */
	public static @Nullable GlobalPos find(ServerPlayer owner, Site site) {
		MinecraftServer server = owner.level().getServer();

		if (shared(site)) {
			return PlanetSiteData.shared(server, site);
		}

		PlanetSiteData.Owned entry = PlanetSiteData.owned(server, owner.getUUID(), site);
		return entry != null ? entry.pos() : null;
	}

	/**
	 * Where a site that is not placed yet will be: far placement's chosen target (docs/MILESTONE_6.md
	 * 3.1) once it has one and has not failed, else the {@linkplain #seededPoint seeded ring point}.
	 * Null for the ambushes (they follow the player), for the Sepulchre (under the Proving) and while
	 * the hub is not placed.
	 */
	public static @Nullable GlobalPos ringPoint(MinecraftServer server, UUID owner, Site site, Planets.Planet planet) {
		GlobalPos seeded = seededPoint(server, owner, site, planet);
		ServerLevel level = seeded == null ? null : Planets.level(server, planet);

		if (level == null) {
			return seeded;
		}

		FarSites.Status far = FarSites.poll(level, farKey(site, owner, planet));
		BlockPos target = far.failed() ? null : far.pointer();
		return target != null ? GlobalPos.of(planet.level(), target) : seeded;
	}

	/**
	 * A site's seeded ring point: the middle of its rings around the hub, in its seeded direction,
	 * moved to the nearest point of its own biome when it has one ({@link #biomeOf}; MILESTONE_5.md
	 * 3.3.2) and that point lies within its rings. Far placement prefers bearings toward it, and its
	 * near fallback searches around it. Null as {@link #ringPoint}.
	 */
	public static @Nullable GlobalPos seededPoint(MinecraftServer server, UUID owner, Site site, Planets.Planet planet) {
		BlockPos hub = PlanetHubs.hub(server, planet);
		int[] rings = rings(site);

		if (hub == null || rings.length == 0 || isAmbush(site) || site == Site.SEPULCHRE) {
			return null;
		}

		double angle = angle(owner, site, hub);
		int ring = rings[rings.length / 2];
		BlockPos point = new BlockPos(hub.getX() + (int) Math.round(Math.cos(angle) * ring), hub.getY(), hub.getZ() + (int) Math.round(Math.sin(angle) * ring));
		ResourceKey<Biome> biome = biomeOf(site);
		ServerLevel level = biome == null ? null : Planets.level(server, planet);

		if (level != null) {
			point = inBiome(level, biome, point, hub, rings);
		}

		return GlobalPos.of(planet.level(), point);
	}

	/**
	 * The biome a site keeps to (MILESTONE_5.md 3.2, 3.3.2, 3.3.3): the verse stones' stormwood, mire
	 * and basalt, the Hymnal's basalt ridges and the debtor camp's canyons; null for any other site.
	 */
	public static @Nullable ResourceKey<Biome> biomeOf(Site site) {
		return switch (site) {
			case VERSE_STONE_1 -> PlanetWorldgen.TYRVANN_STORMWOOD;
			case VERSE_STONE_2 -> PlanetWorldgen.TYRVANN_MIRE;
			case VERSE_STONE_3, HYMNAL -> PlanetWorldgen.TYRVANN_BASALT;
			case DEBTOR_CAMP -> PlanetWorldgen.GHESSAR_CANYONS;
			default -> null;
		};
	}

	/**
	 * The point of {@code biome} nearest {@code seeded} (the biome source's answer, no chunk loaded),
	 * when it lies within {@code rings} around {@code hub} (one ring step of slack either way); else
	 * {@code seeded}. Kept for the server run: the answer never changes.
	 */
	static BlockPos inBiome(ServerLevel level, ResourceKey<Biome> biome, BlockPos seeded, BlockPos hub, int[] rings) {
		BlockPos cached = BIOME_POINTS.get(seeded);

		if (cached != null) {
			return cached;
		}

		int slack = rings.length > 1 ? Math.abs(rings[1] - rings[0]) : BIOME_SEARCH_STEP;
		int inner = Math.max(0, rings[0] - slack);
		int outer = rings[rings.length - 1] + slack;
		BlockPos from = seeded.atY(level.getSeaLevel());
		// One sample a column: the vertical step is taller than the world.
		Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(biome), from, BIOME_SEARCH_RADIUS, BIOME_SEARCH_STEP,
				level.getHeight() + 1);
		BlockPos point = seeded;

		if (found != null) {
			BlockPos at = found.getFirst();
			double fromHub = Math.sqrt(Math.pow(at.getX() - hub.getX(), 2) + Math.pow(at.getZ() - hub.getZ(), 2));

			if (fromHub >= inner && fromHub <= outer) {
				point = new BlockPos(at.getX(), seeded.getY(), at.getZ());
			}
		}

		BIOME_POINTS.put(seeded.immutable(), point);
		return point;
	}

	/** Whether {@code site} is server-wide (the Heartstone, the Sepulchre, the Hymnal). */
	public static boolean shared(Site site) {
		return site == Site.HEARTSTONE || site == Site.SEPULCHRE || site == Site.HYMNAL;
	}

	/**
	 * The world {@code site} always stands on (MILESTONE_5.md 3.1 to 3.4), or null when that depends
	 * on the player: the Wayfarer cache, the hunting camp (the path's world) and the ambushes (wherever
	 * the hunters catch up).
	 */
	public static Planets.@Nullable Planet planetOf(Site site) {
		return switch (site) {
			case SINGING_HOLLOW, HEARTSTONE -> Planets.Planet.SELVAREN;
			case DUSKGLASS_RIFT, SEPULCHRE, DETENTION_POST -> Planets.Planet.VARKHAST;
			case DEBTOR_CAMP -> Planets.Planet.GHESSAR;
			case VERSE_STONE_1, VERSE_STONE_2, VERSE_STONE_3, HYMNAL -> Planets.Planet.TYRVANN;
			default -> null;
		};
	}

	/** Records that {@code owner} finished their {@code site} (an expedition, the cache); false when it is not placed or was already done. */
	public static boolean markDone(MinecraftServer server, UUID owner, Site site) {
		PlanetSiteData.Data data = PlanetSiteData.snapshot(server);
		PlanetSiteData.Owned entry = data.owned(owner, site);

		if (entry == null || entry.done()) {
			return false;
		}

		data.putOwned(new PlanetSiteData.Owned(owner, site, entry.pos(), true));
		PlanetSiteData.commit(server, data);
		return true;
	}

	/**
	 * Layers W1's planet template {@code template} sets into the ground when placed by itself (a
	 * command, a client test): a site piece's, a hub's or a waystone's; -1 for any other template.
	 */
	public static int buried(Identifier template) {
		for (Site site : Site.values()) {
			for (Piece piece : pieces(site)) {
				if (piece.template().equals(template)) {
					return piece.buried();
				}
			}
		}

		if (template.equals(PlanetHubs.GREENWATCH) || template.equals(PlanetHubs.PROVING) || template.equals(PlanetHubs.SALTGATE)
				|| template.equals(PlanetHubs.HOLDFAST)) {
			return PlanetHubs.BURIED;
		} else if (template.equals(Waystones.CLEAN_TEMPLATE) || template.equals(Waystones.CORRUPTED_TEMPLATE)) {
			return Waystones.BURIED;
		}

		return template.equals(PlanetHubs.SEPULCHRE) ? 0 : -1;
	}

	/** The site's pieces, surface first. */
	public static List<Piece> pieces(Site site) {
		return switch (site) {
			case SINGING_HOLLOW -> List.of(HOLLOW_SURFACE, HOLLOW_GROTTO);
			case DUSKGLASS_RIFT -> List.of(RIFT_SURFACE, RIFT);
			case WAYFARER_CACHE -> List.of(CACHE);
			case HUNTING_CAMP -> List.of(CAMP);
			case AMBUSH_ONE, AMBUSH_TWO -> List.of(AMBUSH);
			case HEARTSTONE -> List.of(HEARTSTONE_PIECE);
			case VERSE_STONE_1, VERSE_STONE_2, VERSE_STONE_3 -> List.of(VERSE_STONE);
			case HYMNAL -> List.of(HYMNAL_ARCH, HYMNAL_HALLS);
			case DEBTOR_CAMP -> List.of(DEBTOR_CAMP_PIECE);
			case DETENTION_POST -> List.of(DETENTION_POST_PIECE);
			default -> List.of();
		};
	}

	/**
	 * Whether every piece of {@code site} has its template, so placing it can begin; a missing one is
	 * logged once per site and server run (the Milestone 5 templates until W3 writes them). A site
	 * without pieces (the Sepulchre, placed with its hub) is ready.
	 */
	private static boolean templatesReady(ServerLevel level, Site site) {
		for (Piece piece : pieces(site)) {
			if (level.getStructureManager().get(piece.template()).isEmpty()) {
				if (MISSING.add(site)) {
					OldRepublicMod.LOGGER.warn("{} cannot be placed yet: its template {} is missing", site, piece.template());
				}

				return false;
			}
		}

		return true;
	}

	/**
	 * Whether a placement attempt for {@code owner}'s {@code site} may run at {@code now}; when it
	 * may, the next one waits {@code planets.siteRetryTicks}. Pure enough for the retry test.
	 */
	public static boolean due(UUID owner, Site site, long now) {
		Map<Site, Long> next = NEXT_TRY.computeIfAbsent(owner, id -> new HashMap<>());
		Long at = next.get(site);

		if (at != null && now < at) {
			return false;
		}

		next.put(site, now + Math.max(1, PlanetBalance.get().siteRetryTicks));
		return true;
	}

	/** Forgets an owner's retry times and logged failures (logout, and the retry test's clean-up). */
	public static void forget(UUID owner) {
		NEXT_TRY.remove(owner);
		EXPLAINED.remove(owner);
	}

	static void clear() {
		NEXT_TRY.clear();
		EXPLAINED.clear();
		MISSING.clear();
		BIOME_POINTS.clear();
		FAR_FAILED.clear();
	}

	/** The game time before which {@code owner}'s next attempt at {@code site} waits, or null when none waits ({@link #due}). */
	static @Nullable Long nextTry(UUID owner, Site site) {
		Map<Site, Long> next = NEXT_TRY.get(owner);
		return next == null ? null : next.get(site);
	}

	/**
	 * The surface rules of a site's ring search: its surface piece's {@code footprint} square of story
	 * ground within 3 blocks of slope (4 for the hunting camp), no built block 4 blocks around it,
	 * no stand within {@code footprint} of a recorded site's origin, and none outside the site's own
	 * biome ({@link #biomeOf}).
	 */
	static MeetingSites.Rules rules(ServerLevel level, Site site, int footprint) {
		return new MeetingSites.Rules(footprint, footprint + 8, site == Site.HUNTING_CAMP ? 4 : 3, farFromSites(level, footprint).or(outsideBiome(level, site)),
				List.of(), 0);
	}

	/** Stands outside {@code site}'s own biome; none for a site without one. */
	private static Predicate<BlockPos> outsideBiome(ServerLevel level, Site site) {
		ResourceKey<Biome> biome = biomeOf(site);
		return biome == null ? pos -> false : pos -> !level.getBiome(pos).is(biome);
	}

	/**
	 * Places {@code site}: through far placement first ({@link #requestFar}; null while it chooses and
	 * loads), and once that has failed, by the ring and clearing searches around the hub as before
	 * Milestone 6, while the owner is within {@code planets.siteEnsureRange} of the seeded ring point.
	 */
	private static @Nullable GlobalPos place(ServerLevel level, ServerPlayer owner, Site site, Planets.Planet planet, BlockPos hub) {
		GlobalPos ring = seededPoint(level.getServer(), owner.getUUID(), site, planet);
		List<Piece> pieces = pieces(site);

		if (ring == null || pieces.isEmpty()) {
			return null;
		}

		Piece surface = pieces.getFirst();
		UUID ownerId = shared(site) ? null : owner.getUUID();
		FarSites.Status far = requestFar(level, owner.getUUID(), site, planet, hub, ring.pos(), surface);
		RuntimeSites.Placement farPlacement = far.placement();

		if (far.placed() && farPlacement != null) {
			return finish(level, owner, site, farPlacement, ownerId, true);
		}

		if (!far.failed()) {
			return null;
		}

		double range = PlanetBalance.get().siteEnsureRange;

		if (FAR_FAILED.add(farKey(site, owner.getUUID(), planet))) {
			OldRepublicMod.LOGGER.info("Far placement found no ground for {} ({}); it is placed by the near search once its owner comes within {} blocks of {}",
					site, far.detail(), Math.round(range), ring.pos());
		}

		if (!owner.level().dimension().equals(level.dimension())
				|| owner.position().distanceToSqr(Vec3.atCenterOf(ring.pos()).with(Direction.Axis.Y, owner.getY())) > range * range) {
			return null;
		}

		int footprint = footprint(level, surface.template());
		MeetingSites.Rules rules = rules(level, site, footprint);
		Vec3 origin = Vec3.atBottomCenterOf(hub);
		Vec3 forward = Vec3.atCenterOf(ring.pos()).subtract(Vec3.atCenterOf(hub)).horizontal();
		RuntimeSites.Placement placement = RuntimeSites.placeSurface(level, surface.template(), origin, forward, rings(site), new int[0], rules, surface.layers(), ownerId);
		String ringRefusals = null;

		if (placement == null) {
			ringRefusals = PlanetSiteSearch.ringReport(level, site, hub, ring.pos());
			placement = PlanetSiteSearch.placeClearing(level, site, surface, footprint, hub, ring.pos(), rules.forbidden(), ownerId);
		}

		if (placement == null && biomeOf(site) != null) {
			placement = PlanetSiteSearch.placeClearing(level, site, surface, footprint, hub, ring.pos(), farFromSites(level, footprint), ownerId);

			if (placement != null) {
				OldRepublicMod.LOGGER.warn("No stand in {}'s own biome {} fitted near its ring point, so it took other ground at {}", site,
						biomeOf(site).identifier(), placement.origin());
			}
		}

		if (placement == null) {
			explainOnce(owner, site, planet);
			return null;
		}

		GlobalPos placed = finish(level, owner, site, placement, ownerId, false);

		if (ringRefusals != null) {
			OldRepublicMod.LOGGER.info("No ring candidate fitted {}, so a stand near the ring point was cleared for it. The ring search:\n{}", site, ringRefusals);
		}

		return placed;
	}

	/**
	 * Asks far placement for {@code site}'s surface piece (docs/MILESTONE_6.md 3.2): between its first
	 * and last ring from the hub, preferring bearings within {@code planets.siteBearingSpread} of its
	 * seeded ring point {@code ring}, in its own biome when it has one, its entrance toward the hub, on
	 * the ground its site meta prefers. Idempotent by {@link #farKey}.
	 */
	static FarSites.Status requestFar(ServerLevel level, UUID owner, Site site, Planets.Planet planet, BlockPos hub, BlockPos ring, Piece surface) {
		int[] rings = rings(site);
		Vec3 bearing = Vec3.atCenterOf(ring).subtract(Vec3.atCenterOf(hub)).horizontal();
		FarSites.Request request = FarSites.Request.of(farKey(site, owner, planet), shared(site) ? null : owner, surface.template(), hub, rings[0],
				rings[rings.length - 1]);

		if (bearing.lengthSqr() > 1.0e-4) {
			request.facing(bearing, PlanetBalance.get().siteBearingSpread);
		}

		ResourceKey<Biome> biome = biomeOf(site);
		return FarSites.request(level, biome == null ? request : FarRequests.inBiome(request, biome));
	}

	/** The far placement key of {@code owner}'s {@code site} on {@code planet}; a shared site's is the same whoever asks. */
	static String farKey(Site site, UUID owner, Planets.Planet planet) {
		String key = "planet/" + planet.name().toLowerCase(Locale.ROOT) + "/" + site.name().toLowerCase(Locale.ROOT);
		return shared(site) ? key : key + "/" + owner;
	}

	/**
	 * Finishes a placed surface piece: the underground piece below it (the expeditions), the Hymnal's
	 * halls ({@link Hymnal#requestHalls} after far placement, else {@link Hymnal#sinkHalls}), a verse
	 * stone's set-up; returns its {@code HEART} ({@code STAIR_MOUTH} for the Hymnal), or null while the
	 * Hymnal's halls are still loading.
	 */
	private static @Nullable GlobalPos finish(ServerLevel level, ServerPlayer owner, Site site, RuntimeSites.Placement placement, @Nullable UUID ownerId,
			boolean far) {
		List<Piece> pieces = pieces(site);

		if (site == Site.HYMNAL) {
			FarSites.Status halls = far ? Hymnal.requestHalls(level, placement) : null;

			if (halls != null && !halls.placed() && !halls.failed()) {
				return null;
			}

			if (halls == null || halls.failed()) {
				if (halls != null) {
					OldRepublicMod.LOGGER.info("Far placement could not stamp the Hymnal's halls ({}); they are sunk at once", halls.detail());
				}

				Hymnal.sinkHalls(level, placement);
			}
		} else if (pieces.size() > 1) {
			sink(level, placement, pieces.get(1), pieces.getFirst().layers(), ownerId);
		}

		int index = verseIndex(site);

		if (index > 0) {
			BlockPos stone = SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.STONE);

			if (stone != null) {
				// A far placement may have been released before this poll: load the stone's chunk for its block entity.
				level.getChunk(stone.getX() >> 4, stone.getZ() >> 4);
			}

			setUpVerseStone(level, placement.piece(), owner.getUUID(), index);
		}

		BlockPos heart = SiteAnchors.find(placement.piece(), site == Site.HYMNAL ? SiteAnchors.Anchor.STAIR_MOUTH : SiteAnchors.Anchor.HEART);
		OldRepublicMod.LOGGER.info("Placed {} for {} at {} in {}{}", site, owner.getName().getString(), placement.origin(), level.dimension().identifier(),
				far ? " (far placement)" : "");
		return GlobalPos.of(level.dimension(), heart != null ? heart : placement.origin());
	}

	/** The index (1 to 3) of a verse stone site, or 0 for any other site. */
	public static int verseIndex(Site site) {
		return switch (site) {
			case VERSE_STONE_1 -> 1;
			case VERSE_STONE_2 -> 2;
			case VERSE_STONE_3 -> 3;
			default -> 0;
		};
	}

	/** Gives a freshly placed verse stone its owner, its index and its guards' spot (the {@code STONE} and {@code SPAWNER} anchors). */
	static void setUpVerseStone(ServerLevel level, SitePiece piece, UUID owner, int index) {
		BlockPos stone = SiteAnchors.find(piece, SiteAnchors.Anchor.STONE);

		if (stone != null && level.getBlockEntity(stone) instanceof VerseStoneBlockEntity entity) {
			entity.setup(owner, index, SiteAnchors.find(piece, SiteAnchors.Anchor.SPAWNER));
		} else {
			OldRepublicMod.LOGGER.error("The verse stone site {} at {} has no verse stone at its STONE anchor", piece.template(), piece.origin());
		}
	}

	/** Logs why {@code site} found no spot, the first time it fails for {@code owner}. */
	private static void explainOnce(ServerPlayer owner, Site site, Planets.Planet planet) {
		if (EXPLAINED.computeIfAbsent(owner.getUUID(), id -> EnumSet.noneOf(Site.class)).add(site)) {
			OldRepublicMod.LOGGER.warn("No spot for {} yet; retrying every {} ticks while its owner is near the ring point:\n{}", site,
					PlanetBalance.get().siteRetryTicks, PlanetSiteSearch.explain(owner, site, planet));
		}
	}

	/** The underground piece of a site (the expeditions), right under its surface piece and turned the same way; stamped anyway when the sunken checks refuse. */
	private static void sink(ServerLevel level, RuntimeSites.Placement surface, Piece deep, int buried, @Nullable UUID owner) {
		BlockPos column = surface.origin();
		// The surface stood in loaded ground, but the deep piece and its shell can reach further.
		RuntimeSites.loadForSunken(level, deep.template(), column, buried + 1, surface.rotation());
		RuntimeSites.Placement placed = RuntimeSites.placeSunken(level, deep.template(), column, buried + 1, surface.rotation(), owner);

		if (placed != null) {
			return;
		}

		StructureTemplate loaded = level.getStructureManager().get(deep.template()).orElse(null);

		if (loaded == null) {
			return;
		}

		BlockPos centred = RuntimeSites.templateOrigin(loaded, column, surface.rotation(), 0);
		BlockPos templateOrigin = centred.atY(column.getY() - (buried + 1) - (loaded.getSize().getY() - 1));
		SiteKind kind = SiteKind.ofTemplate(deep.template());
		SitePiece piece = kind == null ? null : RuntimeSites.stamp(level, kind, deep.template(), templateOrigin, surface.rotation());

		if (piece != null) {
			WorldSites.add(level.getServer(), new WorldSites.SiteRecord(kind, level.dimension(), piece.origin(), piece.rotation(), deep.template(),
					owner, Clocks.day(level.getServer())));
			OldRepublicMod.LOGGER.info("Placed {} at {} without the sunken checks (a cave or another piece was in the way)", deep.template(), templateOrigin);
		}
	}

	/** Hunter ambush rocks on a ring 24 to 32 blocks around the owner, far from the hub and every recorded site (8.5). */
	private static @Nullable GlobalPos placeAmbush(ServerLevel level, ServerPlayer owner) {
		PlanetBalance b = PlanetBalance.get();
		int footprint = footprint(level, AMBUSH.template());
		Planets.Planet planet = Planets.Planet.of(level.dimension());
		BlockPos hub = planet == null || planet == Planets.Planet.HOME ? null : PlanetHubs.hub(level.getServer(), planet);
		double clearance = b.ambushClearance;

		if (hub != null && owner.position().distanceToSqr(Vec3.atCenterOf(hub)) < clearance * clearance) {
			return null;
		}

		Predicate<BlockPos> forbidden = farFromSites(level, (int) clearance).or(pos -> hub != null && pos.distSqr(hub) < clearance * clearance);
		MeetingSites.Rules rules = new MeetingSites.Rules(footprint, footprint + 4, 3, forbidden, List.of(), 0);
		RuntimeSites.Placement placement = RuntimeSites.placeSurface(level, AMBUSH.template(), owner.position(), owner.getLookAngle(), b.ambushRings, new int[0],
				rules, AMBUSH.layers(), owner.getUUID());

		if (placement == null) {
			return null;
		}

		BlockPos heart = SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.HEART);
		return GlobalPos.of(level.dimension(), heart != null ? heart : placement.origin());
	}

	/** A candidate is refused within {@code distance} of any recorded site's origin in the level. */
	private static Predicate<BlockPos> farFromSites(ServerLevel level, int distance) {
		List<WorldSites.SiteRecord> records = WorldSites.all(level.getServer()).stream().filter(r -> r.dimension().equals(level.dimension())).toList();
		long limit = (long) distance * distance;
		return pos -> {
			for (WorldSites.SiteRecord record : records) {
				long dx = record.origin().getX() - pos.getX();
				long dz = record.origin().getZ() - pos.getZ();

				if (dx * dx + dz * dz < limit) {
					return true;
				}
			}

			return false;
		};
	}

	/** The side of the square a template stands on: its longer horizontal side (15 when missing). */
	static int footprint(ServerLevel level, Identifier template) {
		return level.getStructureManager().get(template).map(t -> Math.max(t.getSize().getX(), t.getSize().getZ())).orElse(15);
	}

	/** The ring radii around the hub a site is searched on. */
	static int[] rings(Site site) {
		PlanetBalance b = PlanetBalance.get();
		return switch (site) {
			case SINGING_HOLLOW, DUSKGLASS_RIFT -> b.expeditionRings;
			case WAYFARER_CACHE -> b.cacheRings;
			case HUNTING_CAMP -> b.campRings;
			case HEARTSTONE -> b.heartstoneRing;
			case AMBUSH_ONE, AMBUSH_TWO -> b.ambushRings;
			case VERSE_STONE_1, VERSE_STONE_2, VERSE_STONE_3 -> b.verseRings;
			case HYMNAL -> b.hymnalRings;
			case DEBTOR_CAMP -> b.debtorCampRings;
			case DETENTION_POST -> b.detentionRings;
			default -> new int[0];
		};
	}

	/** The seeded direction of a site's ring point: by the hub for a shared site, by owner and site otherwise. */
	private static double angle(UUID owner, Site site, BlockPos hub) {
		long seed = shared(site) ? hub.asLong() ^ site.ordinal() : owner.getMostSignificantBits() ^ owner.getLeastSignificantBits() ^ ((long) site.ordinal() << 32);
		return RandomSource.create(seed ^ SALT).nextDouble() * Math.PI * 2;
	}

	private static boolean isAmbush(Site site) {
		return site == Site.AMBUSH_ONE || site == Site.AMBUSH_TWO;
	}

	private static boolean reusable(ServerPlayer owner, GlobalPos at) {
		return owner.level().dimension().equals(at.dimension()) && owner.position().distanceToSqr(Vec3.atCenterOf(at.pos())) <= AMBUSH_REUSE * AMBUSH_REUSE;
	}

	private PlanetSites() {
	}
}
