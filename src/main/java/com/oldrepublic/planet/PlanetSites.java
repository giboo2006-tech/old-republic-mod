package com.oldrepublic.planet;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
 */
public final class PlanetSites {
	/** Stored by ordinal: append only. */
	public enum Site {
		SINGING_HOLLOW,
		DUSKGLASS_RIFT,
		WAYFARER_CACHE,
		HUNTING_CAMP,
		AMBUSH_ONE,
		AMBUSH_TWO,
		HEARTSTONE,
		SEPULCHRE
	}

	/** One template of a site, with the layers it sets into the ground. */
	public record Piece(Identifier template, int buried) {
	}

	public static final Piece HOLLOW_SURFACE = new Piece(OldRepublicMod.id("expedition/singing_hollow_surface"), 3);
	public static final Piece HOLLOW_GROTTO = new Piece(OldRepublicMod.id("expedition/singing_hollow_grotto"), 0);
	public static final Piece RIFT_SURFACE = new Piece(OldRepublicMod.id("expedition/duskglass_rift_surface"), 2);
	public static final Piece RIFT = new Piece(OldRepublicMod.id("expedition/duskglass_rift_rift"), 0);
	public static final Piece CACHE = new Piece(OldRepublicMod.id("planet/wayfarer_cache"), 4);
	public static final Piece CAMP = new Piece(OldRepublicMod.id("boss/hunting_camp"), 4);
	public static final Piece AMBUSH = new Piece(OldRepublicMod.id("mission/ambush_rocks"), 1);
	public static final Piece HEARTSTONE_PIECE = new Piece(Heartstones.TEMPLATE, 3);

	/** Owners' sites whose next placement attempt waits: owner, site, game time. */
	private static final Map<UUID, Map<Site, Long>> NEXT_TRY = new HashMap<>();
	/** Owners' sites whose failed placement has been logged with its reasons. */
	private static final Map<UUID, Set<Site>> EXPLAINED = new HashMap<>();
	private static final long SALT = 0x5349544552494e47L;
	/** An existing ambush stays in use while its owner is this close to it. */
	private static final int AMBUSH_REUSE = 64;

	/**
	 * The site's position, placing it first when it is not placed yet; null when it cannot be placed
	 * now (its chunks are not loaded, or no ring fits). Callers retry later and point the Calling at
	 * the ring meanwhile.
	 */
	public static @Nullable GlobalPos ensure(ServerPlayer owner, Site site, Planets.Planet planet) {
		GlobalPos found = find(owner, site);

		if (found != null && (!isAmbush(site) || reusable(owner, found))) {
			return found;
		}

		MinecraftServer server = owner.level().getServer();
		ServerLevel level = Planets.level(server, planet);

		if (level == null || !due(owner.getUUID(), site, Clocks.time(server))) {
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

		if (!owner.level().dimension().equals(level.dimension())) {
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

		if (shared(site)) {
			data.putShared(site, placed);
		} else {
			data.putOwned(new PlanetSiteData.Owned(owner.getUUID(), site, placed, false));
		}

		PlanetSiteData.commit(server, data);
		return placed;
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
	 * Where a site that is not placed yet will be looked for: the middle of its rings around the hub,
	 * in its seeded direction. Null for the ambushes (they follow the player), for the Sepulchre
	 * (under the Proving) and while the hub is not placed.
	 */
	public static @Nullable GlobalPos ringPoint(MinecraftServer server, UUID owner, Site site, Planets.Planet planet) {
		BlockPos hub = PlanetHubs.hub(server, planet);
		int[] rings = rings(site);

		if (hub == null || rings.length == 0 || isAmbush(site) || site == Site.SEPULCHRE) {
			return null;
		}

		double angle = angle(owner, site, hub);
		int ring = rings[rings.length / 2];
		BlockPos point = new BlockPos(hub.getX() + (int) Math.round(Math.cos(angle) * ring), hub.getY(), hub.getZ() + (int) Math.round(Math.sin(angle) * ring));
		return GlobalPos.of(planet.level(), point);
	}

	/** Whether {@code site} is server-wide (the Heartstone, the Sepulchre). */
	public static boolean shared(Site site) {
		return site == Site.HEARTSTONE || site == Site.SEPULCHRE;
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

		if (template.equals(PlanetHubs.GREENWATCH) || template.equals(PlanetHubs.PROVING)) {
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
			default -> List.of();
		};
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
	}

	/** The game time before which {@code owner}'s next attempt at {@code site} waits, or null when none waits ({@link #due}). */
	static @Nullable Long nextTry(UUID owner, Site site) {
		Map<Site, Long> next = NEXT_TRY.get(owner);
		return next == null ? null : next.get(site);
	}

	/**
	 * The surface rules of a site's ring search: its surface piece's {@code footprint} square of story
	 * ground within 3 blocks of slope (4 for the hunting camp), no built block 4 blocks around it,
	 * and no stand within {@code footprint} of a recorded site's origin.
	 */
	static MeetingSites.Rules rules(ServerLevel level, Site site, int footprint) {
		return new MeetingSites.Rules(footprint, footprint + 8, site == Site.HUNTING_CAMP ? 4 : 3, farFromSites(level, footprint), List.of(), 0);
	}

	private static @Nullable GlobalPos place(ServerLevel level, ServerPlayer owner, Site site, Planets.Planet planet, BlockPos hub) {
		GlobalPos ring = ringPoint(level.getServer(), owner.getUUID(), site, planet);
		List<Piece> pieces = pieces(site);

		if (ring == null || pieces.isEmpty()) {
			return null;
		}

		double range = PlanetBalance.get().siteEnsureRange;

		if (owner.position().distanceToSqr(Vec3.atCenterOf(ring.pos()).with(Direction.Axis.Y, owner.getY())) > range * range) {
			return null;
		}

		Piece surface = pieces.getFirst();
		int footprint = footprint(level, surface.template());
		MeetingSites.Rules rules = rules(level, site, footprint);
		Vec3 origin = Vec3.atBottomCenterOf(hub);
		Vec3 forward = Vec3.atCenterOf(ring.pos()).subtract(Vec3.atCenterOf(hub)).horizontal();
		UUID ownerId = shared(site) ? null : owner.getUUID();
		RuntimeSites.Placement placement = RuntimeSites.placeSurface(level, surface.template(), origin, forward, rings(site), new int[0], rules, surface.buried(), ownerId);
		String ringRefusals = null;

		if (placement == null) {
			ringRefusals = PlanetSiteSearch.ringReport(level, site, hub, ring.pos());
			placement = PlanetSiteSearch.placeClearing(level, site, surface, footprint, hub, ring.pos(), rules.forbidden(), ownerId);
		}

		if (placement == null) {
			explainOnce(owner, site, planet);
			return null;
		}

		if (pieces.size() > 1) {
			sink(level, placement, pieces.get(1), surface.buried(), ownerId);
		}

		BlockPos heart = SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.HEART);
		OldRepublicMod.LOGGER.info("Placed {} for {} at {} in {}", site, owner.getName().getString(), placement.origin(), level.dimension().identifier());

		if (ringRefusals != null) {
			OldRepublicMod.LOGGER.info("No ring candidate fitted {}, so a stand near the ring point was cleared for it. The ring search:\n{}", site, ringRefusals);
		}

		return GlobalPos.of(level.dimension(), heart != null ? heart : placement.origin());
	}

	/** Logs why {@code site} found no spot, the first time it fails for {@code owner}. */
	private static void explainOnce(ServerPlayer owner, Site site, Planets.Planet planet) {
		if (EXPLAINED.computeIfAbsent(owner.getUUID(), id -> EnumSet.noneOf(Site.class)).add(site)) {
			OldRepublicMod.LOGGER.warn("No spot for {} yet; retrying every {} ticks while its owner is near the ring point:\n{}", site,
					PlanetBalance.get().siteRetryTicks, PlanetSiteSearch.explain(owner, site, planet));
		}
	}

	/** The underground piece of an expedition, right under its surface piece and turned the same way; stamped anyway when the sunken checks refuse. */
	private static void sink(ServerLevel level, RuntimeSites.Placement surface, Piece deep, int buried, @Nullable UUID owner) {
		BlockPos column = surface.origin();
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
		SitePiece piece = RuntimeSites.stamp(level, SiteKind.EXPEDITION, deep.template(), templateOrigin, surface.rotation());

		if (piece != null) {
			WorldSites.add(level.getServer(), new WorldSites.SiteRecord(SiteKind.EXPEDITION, level.dimension(), piece.origin(), piece.rotation(), deep.template(),
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
				rules, AMBUSH.buried(), owner.getUUID());

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
