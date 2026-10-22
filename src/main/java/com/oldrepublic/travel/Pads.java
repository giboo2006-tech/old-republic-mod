package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where ships land (spec MILESTONE_4.md 4.5, 4.8, 12.2): home is the owner's landing beacon, else
 * their ship cradle, else the world spawn; a hub destination is the first free of the hub's four
 * bays, else a flat spot on the overflow rings; the hunting camp's pad is a clearing 48 blocks
 * outside the camp. A pad whose surface turns out to be blocked at landing shifts to the nearest
 * clear spot ({@link #landing}).
 *
 * <p>Bay claims are not saved: a ship holds a bay while it stands within a few blocks of it, until
 * it has been unattended (no owner or crew near it) for {@code travel.bayLapseTicks}. After a
 * restart every ship counts as attended again.
 */
final class Pads {
	/**
	 * A destination's pad.
	 *
	 * @param surface the height is found at landing (the camp's clearing, the world spawn)
	 * @param hub     the hub's world for a hub pad, else null
	 * @param bay     the bay index for a hub bay, -1 otherwise (an overflow spot, or no hub)
	 * @param centre  what the overflow rings and the ramp turn around (the hub, the camp), or null
	 */
	record Pad(GlobalPos pos, float yaw, boolean surface, Planets.@Nullable Planet hub, int bay, @Nullable BlockPos centre) {
	}

	/** Another ship within this many blocks (horizontally) holds a bay. */
	static final double BAY_RADIUS = 5;
	/** A landing spot: the ship's box and a block of margin must be clear. */
	private static final int SPOT_FOOTPRINT = 9;
	private static final int SPOT_CLEARANCE = 9;
	private static final int SPOT_SLOPE = 2;
	private static final int SPOT_SEPARATION = 10;
	private static final int SPOT_ANGLES = 16;
	/** Rings around a blocked pad that is not a hub's. */
	private static final int[] BLOCKED_RINGS = {10, 14, 18, 24};
	private static final int[] BLOCKED_FALLBACK = {32, 48};
	/**
	 * A position this close to a hub's centre (blocks) belongs to it: the largest hub's half-diagonal
	 * (the Greenwatch, 95 square since docs/MILESTONE_6.md: 67) and its overflow spots beyond its walls.
	 */
	private static final double HUB_REACH = 104;
	/** Owners and crew within this distance attend their ship. */
	private static final double ATTEND_RANGE = 32;

	/** Game time each owner's ship was last attended (memory only). */
	private static final Map<UUID, Long> ATTENDED = new HashMap<>();

	// ---------------------------------------------------------------- resolving a destination

	/**
	 * The pad for {@code d} as it is known now: null when it cannot be known yet (a hub that is not
	 * placed, a camp that is not there). A hub pad is a free bay, else the overflow marker (bay -1)
	 * whose spot is searched at landing.
	 */
	static @Nullable Pad of(ServerPlayer owner, Destination d) {
		MinecraftServer server = owner.level().getServer();

		return switch (d) {
			case HOME -> home(owner);
			case HUNTING_CAMP -> camp(owner);
			// Every other destination is its planet's hub pad (GREENWATCH, PROVING, and from Milestone 5 SALTGATE and HOLDFAST; MILESTONE_5.md 3.1).
			default -> d.planet() == null ? null : hubPad(server, d.planet(), owner.getUUID());
		};
	}

	/**
	 * A recall pad at {@code pos}: a hub's pad (a free bay) when {@code pos} is at a hub, the home
	 * pad (facing away from its beacon) when it is the owner's beacon pad, else the spot itself.
	 */
	static Pad at(ServerPlayer owner, GlobalPos pos) {
		MinecraftServer server = owner.level().getServer();
		Planets.Planet planet = Planets.Planet.of(pos.dimension());
		BlockPos hub = planet == null || planet == Planets.Planet.HOME ? null : PlanetHubs.hub(server, planet);

		if (hub != null && hub.distSqr(pos.pos()) <= HUB_REACH * HUB_REACH) {
			Pad bay = hubPad(server, planet, owner.getUUID());

			if (bay != null) {
				return bay;
			}
		}

		if (TravelAttachments.get(owner).homePad().filter(pos::equals).isPresent()) {
			return home(owner);
		}

		return new Pad(pos, 0f, false, null, -1, null);
	}

	/** The pad of the hub on the planet {@code p} stands on, or home on the home world (the host's free recall, a pad terminal). */
	static @Nullable Pad hereHub(ServerPlayer p) {
		Planets.Planet planet = Planets.Planet.of(p.level().dimension());

		if (planet == null) {
			return null;
		}

		return planet == Planets.Planet.HOME ? home(p) : hubPad(p.level().getServer(), planet, p.getUUID());
	}

	/** Home: the landing beacon's pad, else the cradle, else the world spawn (12.2). */
	static Pad home(ServerPlayer owner) {
		MinecraftServer server = owner.level().getServer();
		Optional<GlobalPos> beacon = TravelAttachments.get(owner).homePad();

		if (beacon.isPresent() && server.getLevel(beacon.get().dimension()) != null) {
			Float yaw = beaconYaw(server, beacon.get());

			if (yaw != null) {
				return new Pad(beacon.get(), yaw, false, null, -1, null);
			}

			// The beacon is gone (removed without its owner breaking it): home falls back to the cradle.
			TravelAttachments.set(owner, TravelAttachments.get(owner).withHomePad(Optional.empty()));
		}

		Pad cradle = cradle(server, owner.getUUID());
		return cradle != null ? cradle : spawn(server);
	}

	/** Home for an owner who may be offline: the cradle, else the world spawn. */
	static GlobalPos homeFallback(MinecraftServer server, UUID owner) {
		ServerPlayer online = server.getPlayerList().getPlayer(owner);

		if (online != null) {
			return surfaceOf(server, home(online)).pos();
		}

		Pad cradle = cradle(server, owner);
		return surfaceOf(server, cradle != null ? cradle : spawn(server)).pos();
	}

	/** The owner's newest ship cradle, at its {@code HEART}; null when they have none. */
	static @Nullable Pad cradle(MinecraftServer server, UUID owner) {
		Pad found = null;

		for (WorldSites.SiteRecord site : WorldSites.all(server)) {
			if (site.kind() != SiteKind.SHIP_CRADLE || !owner.equals(site.owner())) {
				continue;
			}

			ServerLevel level = server.getLevel(site.dimension());
			SitePiece piece = level == null ? null : RuntimeSites.pieceOf(level, site);

			if (piece != null) {
				BlockPos heart = SiteAnchors.find(piece, SiteAnchors.Anchor.HEART);
				found = new Pad(GlobalPos.of(site.dimension(), heart != null ? heart : piece.box().getCenter()), Ships.yawOf(site.rotation()), heart == null, null, -1, null);
			}
		}

		return found;
	}

	private static Pad spawn(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		BlockPos spawn = overworld.getRespawnData().pos();
		return new Pad(GlobalPos.of(overworld.dimension(), spawn), 0f, true, null, -1, spawn);
	}

	/**
	 * The first free bay of a planet's hub, or the overflow marker when all four are held; null when
	 * the hub is not placed yet.
	 */
	static @Nullable Pad hubPad(MinecraftServer server, Planets.Planet planet, UUID owner) {
		BlockPos hub = PlanetHubs.hub(server, planet);
		List<BlockPos> bays = PlanetHubs.padBays(server, planet);

		if (hub == null || bays.isEmpty()) {
			return null;
		}

		int bay = ShipRules.firstFreeBay(bays, heldBays(server, planet, owner), BAY_RADIUS);
		BlockPos pos = bay >= 0 ? bays.get(bay) : hub;
		return new Pad(GlobalPos.of(planet.level(), pos), yawFrom(hub, pos), bay < 0, planet, bay, hub);
	}

	/**
	 * Where other ships hold a planet's bays: grounded ships not lapsed, flights on their way there,
	 * and ships landing there by hand (MILESTONE_5.md 4.5).
	 */
	private static List<BlockPos> heldBays(MinecraftServer server, Planets.Planet planet, UUID owner) {
		long now = Clocks.time(server);
		ShipBalance.Travel t = ShipBalance.travel();
		List<BlockPos> held = new ArrayList<>(Flights.targets(planet.level(), owner));
		held.addAll(Helm.targets(planet.level(), owner));

		for (ShipRecord record : ShipRegistry.snapshot(server).all()) {
			if (record.owner().equals(owner) || !record.pos().dimension().equals(planet.level()) || record.state() == ShipState.HULK) {
				continue;
			}

			long attended = ATTENDED.computeIfAbsent(record.owner(), id -> now);

			if (!ShipRules.bayLapsed(attended, now, t) || record.flight().isPresent()) {
				held.add(record.pos().pos());
			}
		}

		return held;
	}

	/** The hunting camp's clearing: {@code travel.campPadOffset} blocks from the camp, towards the hub (or the world's centre). */
	static @Nullable Pad camp(ServerPlayer owner) {
		GlobalPos camp = PlanetSites.find(owner, PlanetSites.Site.HUNTING_CAMP);

		if (camp == null) {
			return null;
		}

		MinecraftServer server = owner.level().getServer();
		Planets.Planet planet = Planets.Planet.of(camp.dimension());
		BlockPos hub = planet == null ? null : PlanetHubs.hub(server, planet);
		BlockPos toward = hub != null ? hub : BlockPos.ZERO;
		Vec3 dir = new Vec3(toward.getX() - camp.pos().getX(), 0, toward.getZ() - camp.pos().getZ());
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : dir.normalize();
		int offset = ShipBalance.travel().campPadOffset;
		BlockPos pad = BlockPos.containing(camp.pos().getX() + 0.5 + dir.x * offset, camp.pos().getY(), camp.pos().getZ() + 0.5 + dir.z * offset);
		return new Pad(GlobalPos.of(camp.dimension(), pad), yawFrom(camp.pos(), pad), true, null, -1, camp.pos());
	}

	/** A pad with its height found now (the column's surface), when it is a surface pad and its chunk is loaded. */
	static Pad surfaceOf(MinecraftServer server, Pad pad) {
		ServerLevel level = server.getLevel(pad.pos().dimension());

		if (!pad.surface() || level == null || !level.isLoaded(pad.pos().pos())) {
			return pad;
		}

		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pad.pos().pos());
		return new Pad(GlobalPos.of(pad.pos().dimension(), top), pad.yaw(), false, pad.hub(), pad.bay(), pad.centre());
	}

	// ---------------------------------------------------------------- landing (4.5)

	/**
	 * The spot a descending ship settles on, with its chunks loaded: the pad when it is clear; else
	 * (a blocked pad, or a hub with every bay held) the best flat spot on the rings around the hub
	 * or the pad, away from every other ship.
	 */
	static Pad landing(ServerLevel level, Pad pad, @Nullable Entity self) {
		Pad known = surfaceOf(level.getServer(), pad);

		if (known.bay() >= 0 || known.hub() == null) {
			if (isClear(level, known.pos().pos(), self)) {
				return known;
			}
		}

		BlockPos centre = known.hub() != null && known.centre() != null ? known.centre() : known.pos().pos();
		int[] rings = known.hub() != null ? ShipBalance.travel().overflowRings : BLOCKED_RINGS;
		MeetingSites.Rules rules = new MeetingSites.Rules(SPOT_FOOTPRINT, SPOT_CLEARANCE, SPOT_SLOPE, pos -> false,
				otherShips(level, self), SPOT_SEPARATION);
		BlockPos spot = MeetingSites.find(level, Vec3.atBottomCenterOf(centre), Vec3.atBottomCenterOf(known.pos().pos()).subtract(Vec3.atBottomCenterOf(centre)),
				rings, BLOCKED_FALLBACK, SPOT_ANGLES, rings.length > 0 ? rings[rings.length - 1] : 24, rules);
		float yaw = known.hub() != null || known.centre() != null ? yawFrom(centre, spot) : known.yaw();
		return new Pad(GlobalPos.of(level.dimension(), spot), yaw, false, known.hub(), -1, known.centre());
	}

	/**
	 * Whether a ship fits at {@code pad}: nothing solid in its landing box, and no other ship body in
	 * it. The box is the class's of {@code self} (MILESTONE_5.md 4.9: the 7 × 4 × 7 courier's, the
	 * 5 × 2.5 × 5 interceptor's), a little smaller so a grass tuft at its edge does not count; the
	 * courier's when there is no ship.
	 */
	static boolean isClear(ServerLevel level, BlockPos pad, @Nullable Entity self) {
		if (!level.isLoaded(pad)) {
			return false;
		}

		boolean interceptor = self instanceof ShipBody ship && ship.interceptor();
		double half = ShipBody.footprintHalf(interceptor);
		Vec3 base = Vec3.atBottomCenterOf(pad);
		AABB box = new AABB(base.x - half, base.y + 0.05, base.z - half, base.x + half, base.y + ShipBody.footprintHeight(interceptor), base.z + half);

		if (!level.getEntitiesOfClass(ShipBody.class, box.inflate(1.5), ship -> ship != self && !ship.isRemoved()).isEmpty()) {
			return false;
		}

		return level.noCollision(box);
	}

	private static List<BlockPos> otherShips(ServerLevel level, @Nullable Entity self) {
		List<BlockPos> others = new ArrayList<>();

		for (ShipRecord record : ShipRegistry.snapshot(level.getServer()).all()) {
			if (record.pos().dimension().equals(level.dimension()) && (self == null || !record.body().equals(self.getUUID()))) {
				others.add(record.pos().pos());
			}
		}

		return others;
	}

	// ---------------------------------------------------------------- bay lapse (4.5)

	/** A body's attendance check: the owner or a crew member within range keeps its bay claim alive. */
	static void attend(MinecraftServer server, ShipRecord record, ShipBody ship) {
		long now = Clocks.time(server);
		ATTENDED.putIfAbsent(record.owner(), now);

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level() == ship.level() && ShipRules.mayBoard(record, player.getUUID()) && player.distanceToSqr(ship) < ATTEND_RANGE * ATTEND_RANGE) {
				ATTENDED.put(record.owner(), now);
				return;
			}
		}
	}

	/** A ship just landed: its claim starts now. */
	static void landed(MinecraftServer server, UUID owner) {
		ATTENDED.put(owner, Clocks.time(server));
	}

	static void clear() {
		ATTENDED.clear();
	}

	// ---------------------------------------------------------------- facing

	/** The yaw that points the nose away from {@code from} (so the ramp faces it). */
	static float yawFrom(BlockPos from, BlockPos pad) {
		return Ships.yawAlong(pad.getX() - from.getX(), pad.getZ() - from.getZ());
	}

	/**
	 * A beacon pad's yaw: away from the beacon, which stands on the area's edge. North-facing when
	 * the pad's chunk is not loaded; null when it is loaded and no beacon marks it any more.
	 */
	private static @Nullable Float beaconYaw(MinecraftServer server, GlobalPos pad) {
		ServerLevel level = server.getLevel(pad.dimension());

		if (level == null || !level.isLoaded(pad.pos())) {
			return 0f;
		}

		boolean unknown = false;

		for (BlockPos at : LandingBeaconBlock.around(pad.pos())) {
			if (!level.isLoaded(at)) {
				unknown = true;
			} else if (level.getBlockEntity(at) instanceof LandingBeaconBlockEntity beacon && pad.equals(beacon.padPos())) {
				return yawFrom(at, pad.pos());
			}
		}

		return unknown ? 0f : null;
	}

	private Pads() {
	}
}
