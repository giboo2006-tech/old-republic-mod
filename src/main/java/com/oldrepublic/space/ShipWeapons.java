package com.oldrepublic.space;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Identity;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.space.network.ShipCombatStatusPayload;
import com.oldrepublic.space.network.ShipFirePayload;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A ship's weapons on the server (spec MILESTONE_5.md 5.2, 5.3): the pilot's twin cannons and
 * missile rack and the gunner's dorsal turret. Clients send only their triggers
 * ({@code ship_fire}); the server fires while a trigger is held, from the ship's server position and
 * heading (the gunner's aim from the gunner's server-side look), checks heat, the interval and the
 * hold's missiles, and ignores anyone not in the role. Every other tick it sends each pilot and
 * gunner their {@code ship_combat_status}, and once more when their role ends, which also lets go
 * of their triggers and any lock in progress.
 *
 * <p>Heat, the alternating muzzle and the lock are per ship (by owner) and live in memory. A ship's
 * velocity lives on its pilot's client, so the server measures it from its position each tick: the
 * bolts inherit it and the turrets of the gunship lead with it.
 */
public final class ShipWeapons {
	/**
	 * The turret's barrels reach this far from its pivot (blocks). The muzzles, the pivot and the
	 * rack are the ship class's own points ({@link ShipBody#cannons}, {@link ShipBody#turret},
	 * {@link ShipBody#rack}, W1), where its model draws the guns.
	 */
	private static final double TURRET_BARREL = 1.2;
	/** The turret aims from 10 degrees below the horizon to straight up (Minecraft pitch: positive is down). */
	public static final float TURRET_PITCH_DOWN = 10f;
	public static final float TURRET_PITCH_UP = -90f;
	private static final int STATUS_INTERVAL = 2;
	/** A position change faster than this (squared blocks per tick) is a teleport, not flight. */
	private static final double JUMP_SQR = 9.0;

	/** A player's held triggers and the role last told to their HUD. */
	private static final class Crew {
		boolean primary;
		boolean missile;
		int role;
	}

	/** One ship's guns, by owner. */
	private static final class Guns {
		SpaceRules.Heat cannons = SpaceRules.Heat.COLD;
		SpaceRules.Heat turret = SpaceRules.Heat.COLD;
		int muzzle;
		long lastMissile = Long.MIN_VALUE / 4;
		@Nullable UUID lockTarget;
		int lockTicks;
		boolean locked;
	}

	/** A piloted ship's last position and measured velocity, by body. */
	private static final class Track {
		Vec3 last;
		Vec3 velocity = Vec3.ZERO;
		long seen;

		Track(Vec3 last, long seen) {
			this.last = last;
			this.seen = seen;
		}
	}

	private static final Map<UUID, Crew> CREW = new HashMap<>();
	private static final Map<UUID, Guns> GUNS = new HashMap<>();
	private static final Map<UUID, Track> TRACKS = new HashMap<>();
	private static final Map<UUID, Integer> INCOMING = new HashMap<>();

	// ---------------------------------------------------------------- roles

	/** The ship {@code p} flies by hand, or null. */
	static @Nullable ShipBody piloted(Player p) {
		return p.getVehicle() instanceof ShipBody ship && ship.piloted() && ship.getControllingPassenger() == p ? ship : null;
	}

	/**
	 * The ship whose gunner seat {@code p} holds (5.2), or null: a flying ship with cannons, and
	 * {@code p} its first passenger who is not the owner (the courier's seat 1, the interceptor's
	 * second seat).
	 */
	public static @Nullable ShipBody gunned(Player p) {
		return p.getVehicle() instanceof ShipBody ship && gunnerOf(ship) == p ? ship : null;
	}

	/**
	 * The player in {@code ship}'s gunner seat, or null (no cannons, not at the helm, or nobody
	 * there): W1's seat rule ({@link ShipBody#gunner}), the one the ship's model aims its turret by.
	 */
	public static @Nullable Player gunnerOf(ShipBody ship) {
		return ship.gunner();
	}

	private static int role(ServerPlayer p) {
		ShipBody pilotOf = piloted(p);

		if (pilotOf != null && pilotOf.state() == ShipState.FLYING && pilotOf.hull() > 0) {
			return ShipCombatStatusPayload.PILOT;
		}

		ShipBody gunnerOf = gunned(p);
		return gunnerOf != null && gunnerOf.hull() > 0 ? ShipCombatStatusPayload.GUNNER : ShipCombatStatusPayload.NONE;
	}

	// ---------------------------------------------------------------- triggers

	/** A trigger pressed or released ({@code ship_fire}). Releasing the use button fires a locked missile. */
	public static void trigger(ServerPlayer p, int weapon, boolean pressed) {
		int role = role(p);

		if (role == ShipCombatStatusPayload.NONE) {
			return;
		}

		Crew crew = CREW.computeIfAbsent(p.getUUID(), id -> new Crew());

		if (weapon == ShipFirePayload.PRIMARY) {
			crew.primary = pressed;
		} else if (weapon == ShipFirePayload.MISSILE && role == ShipCombatStatusPayload.PILOT) {
			crew.missile = pressed;
			ShipBody ship = piloted(p);

			if (!pressed && ship != null && ship.level() instanceof ServerLevel level) {
				release(level, p, ship, guns(p.getUUID()), SpaceBalance.get());
			}
		}
	}

	private static Guns guns(UUID owner) {
		return GUNS.computeIfAbsent(owner, id -> new Guns());
	}

	// ---------------------------------------------------------------- ticking

	/** One server tick: velocities, the held triggers, the locks and the HUD status. */
	static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		SpaceBalance b = SpaceBalance.get();

		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ShipBody flown = piloted(p);

			if (flown != null) {
				track(flown, now);
			}
		}

		TRACKS.values().removeIf(t -> now - t.seen > 20);

		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int role = role(p);
			Crew crew = CREW.get(p.getUUID());

			if (role == ShipCombatStatusPayload.NONE) {
				if (crew != null) {
					letGo(p, crew);
				}

				continue;
			}

			if (crew == null) {
				crew = new Crew();
				CREW.put(p.getUUID(), crew);
			}

			if (role == ShipCombatStatusPayload.PILOT) {
				pilot(p, crew, b, now);
			} else {
				gunner(p, crew, b, now);
			}

			if (crew.role != role || (now + p.getId()) % STATUS_INTERVAL == 0) {
				crew.role = role;
				SpaceNetwork.send(p, status(p, role, b, now));
			}
		}
	}

	private static void track(ShipBody ship, long now) {
		Vec3 pos = ship.position();
		Track track = TRACKS.get(ship.getUUID());

		if (track == null) {
			TRACKS.put(ship.getUUID(), new Track(pos, now));
			return;
		}

		if (track.seen == now) {
			return;
		}

		Vec3 moved = pos.subtract(track.last).scale(1.0 / Math.max(1, now - track.seen));
		track.velocity = moved.lengthSqr() > JUMP_SQR ? Vec3.ZERO : moved;
		track.last = pos;
		track.seen = now;
	}

	/** An entity's velocity in blocks per tick: a piloted ship's as measured, anything else's own. */
	public static Vec3 velocityOf(Entity e) {
		if (e instanceof ShipBody ship) {
			Track track = TRACKS.get(ship.getUUID());
			return track == null ? Vec3.ZERO : track.velocity;
		}

		return e.getDeltaMovement();
	}

	/** A player's role ended (dismounted, landed, handed to the autopilot, disabled, gone): triggers and lock let go, the HUD told. */
	private static void letGo(ServerPlayer p, Crew crew) {
		CREW.remove(p.getUUID());
		Guns guns = GUNS.get(p.getUUID());

		if (guns != null && guns.lockTarget != null) {
			loseLock(p, guns);
		}

		if (crew.role != ShipCombatStatusPayload.NONE) {
			SpaceNetwork.send(p, ShipCombatStatusPayload.OFF);
		}
	}

	private static void pilot(ServerPlayer p, Crew crew, SpaceBalance b, long now) {
		ShipBody ship = piloted(p);

		if (ship == null || !(ship.level() instanceof ServerLevel level)) {
			return;
		}

		Guns guns = guns(p.getUUID());

		if (crew.primary && (ship.upgrades() & ShipRecord.UPGRADE_CANNONS) != 0) {
			cannons(level, p, ship, guns, b, now);
		}

		lock(level, p, ship, guns, crew, b);
	}

	private static void gunner(ServerPlayer p, Crew crew, SpaceBalance b, long now) {
		ShipBody ship = gunned(p);
		UUID owner = ship == null ? null : ship.ownerId();

		if (ship == null || owner == null || !crew.primary || !(ship.level() instanceof ServerLevel level)) {
			return;
		}

		Guns guns = guns(owner);

		if (!guns.turret.ready(now, b.turretInterval)) {
			return;
		}

		Vec3 aim = turretAim(p.getXRot(), p.getYRot());
		double[] turret = ship.turret();
		Vec3 pivot = ship.position().add(ShipBody.local(turret[0], turret[1], turret[2], ship.getYRot(), ship.getXRot()));
		Vec3 from = pivot.add(aim.scale(TURRET_BARREL));
		ShipBolt.fire(level, ship, p.getUUID(), ShipBolt.Team.PLAYERS, styleOf(level.getServer(), owner), from, aim.scale(b.boltSpeed).add(velocityOf(ship)),
				b.turretDamage, b.boltRange);
		guns.turret = guns.turret.fire(now, b.turretHeat, b);
		EffectEvents.toTracking(ship, SpaceEffects.SHIP_WEAPON, from, aim, ship.getId(), 1f, SpaceEffects.WEAPON_TURRET);

		if (guns.turret.locked(now)) {
			EffectEvents.toTracking(ship, SpaceEffects.SHIP_WEAPON, pivot, aim, ship.getId(), 1f, SpaceEffects.WEAPON_OVERHEAT);
		}
	}

	/** The turret's aim for a gunner looking at {@code pitch}, {@code yaw}: all the way round, from 10 degrees down to straight up. */
	public static Vec3 turretAim(float pitch, float yaw) {
		return Vec3.directionFromRotation(Mth.clamp(pitch, TURRET_PITCH_UP, TURRET_PITCH_DOWN), yaw);
	}

	/** The ship's nose (a unit vector), from its server-side rotation. */
	static Vec3 nose(ShipBody ship) {
		return ShipBody.local(0, 0, 1, ship.getYRot(), ship.getXRot()).normalize();
	}

	// ---------------------------------------------------------------- cannons

	private static void cannons(ServerLevel level, ServerPlayer pilot, ShipBody ship, Guns guns, SpaceBalance b, long now) {
		if (!guns.cannons.ready(now, b.cannonInterval)) {
			return;
		}

		Vec3 dir = nose(ship);
		double[][] muzzles = ship.cannons();
		double[] m = muzzles[guns.muzzle % muzzles.length];
		guns.muzzle ^= 1;
		Vec3 from = ship.position().add(ShipBody.local(m[0], m[1], m[2], ship.getYRot(), ship.getXRot()));
		ShipBolt.fire(level, ship, pilot.getUUID(), ShipBolt.Team.PLAYERS, styleOf(level.getServer(), pilot.getUUID()),
				from, dir.scale(b.boltSpeed).add(velocityOf(ship)), b.cannonDamage, b.boltRange);
		guns.cannons = guns.cannons.fire(now, b.cannonHeat, b);
		EffectEvents.toTracking(ship, SpaceEffects.SHIP_WEAPON, from, dir, ship.getId(), 1f, SpaceEffects.WEAPON_CANNONS);

		if (guns.cannons.locked(now)) {
			EffectEvents.toTracking(ship, SpaceEffects.SHIP_WEAPON, from, dir, ship.getId(), 1f, SpaceEffects.WEAPON_OVERHEAT);
		}
	}

	/** The colour of a ship's bolts by its owner's path (5.3): Sith red, Jedi green-white, Independent yellow; red before a path. */
	static ShipBolt.Style styleOf(MinecraftServer server, UUID owner) {
		ServerPlayer player = server.getPlayerList().getPlayer(owner);

		if (player == null) {
			return ShipBolt.Style.SITH;
		}

		return switch (Identity.path(player)) {
			case JEDI -> ShipBolt.Style.JEDI;
			case INDEPENDENT -> ShipBolt.Style.INDEPENDENT;
			default -> ShipBolt.Style.SITH;
		};
	}

	// ---------------------------------------------------------------- missiles

	/**
	 * While the use button is held on a ship with the rack: the ship target nearest the nose within
	 * {@code space.lockCone} and {@code space.lockRange} is locked after {@code space.lockTicks} ticks
	 * held in the cone (the rising tone, then the steady one).
	 */
	private static void lock(ServerLevel level, ServerPlayer pilot, ShipBody ship, Guns guns, Crew crew, SpaceBalance b) {
		if (!crew.missile || (ship.upgrades() & ShipRecord.UPGRADE_MISSILES) == 0) {
			if (guns.lockTarget != null && !crew.missile) {
				loseLock(pilot, guns);
			}

			return;
		}

		Entity current = guns.lockTarget == null ? null : level.getEntity(guns.lockTarget);
		Vec3 eye = ship.getBoundingBox().getCenter();
		Vec3 nose = nose(ship);
		Entity target = current != null && lockable(current, eye, nose, b) ? current : lockCandidate(level, eye, nose, b);

		if (target == null) {
			if (guns.lockTarget != null) {
				loseLock(pilot, guns);
			}

			return;
		}

		if (!target.getUUID().equals(guns.lockTarget)) {
			guns.lockTarget = target.getUUID();
			guns.lockTicks = 0;
			guns.locked = false;
			EffectEvents.toPlayer(pilot, SpaceEffects.MISSILE_LOCK, target.getBoundingBox().getCenter(), null, target.getId(), 0f, SpaceEffects.LOCK_LOCKING);
		}

		guns.lockTicks++;

		if (!guns.locked && SpaceRules.lockHeld(guns.lockTicks, b)) {
			guns.locked = true;
			EffectEvents.toPlayer(pilot, SpaceEffects.MISSILE_LOCK, target.getBoundingBox().getCenter(), null, target.getId(), 1f, SpaceEffects.LOCK_LOCKED);
		}
	}

	/** Whether {@code target} can be locked from {@code eye} along {@code nose}: a live ship target, not an ally, in the cone and range. */
	public static boolean lockable(Entity target, Vec3 eye, Vec3 nose, SpaceBalance b) {
		if (!target.isAlive() || !target.is(SpaceEntities.SHIP_TARGETS) || target instanceof Starfighter f && f.side() == SpaceRules.Side.ALLIED) {
			return false;
		}

		return SpaceRules.inCone(eye, nose, target.getBoundingBox().getCenter(), b.lockCone, b.lockRange);
	}

	/** The lockable ship target nearest the nose's line. */
	private static @Nullable Entity lockCandidate(ServerLevel level, Vec3 eye, Vec3 nose, SpaceBalance b) {
		AABB box = new AABB(eye, eye).inflate(b.lockRange + BlockadeGunship.REACH);
		Entity best = null;
		double bestDot = -2;

		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, e -> lockable(e, eye, nose, b))) {
			Vec3 to = mob.getBoundingBox().getCenter().subtract(eye);
			double dot = to.lengthSqr() < 1.0e-6 ? 1 : nose.dot(to.normalize());

			if (dot > bestDot) {
				best = mob;
				bestDot = dot;
			}
		}

		return best;
	}

	/** The use button released: a held lock fires a missile from the hold; anything short of a lock is lost. */
	private static void release(ServerLevel level, ServerPlayer pilot, ShipBody ship, Guns guns, SpaceBalance b) {
		Entity target = guns.lockTarget == null ? null : level.getEntity(guns.lockTarget);
		boolean fire = guns.locked && target != null && target.isAlive();
		long now = level.getGameTime();

		if (fire && now - guns.lastMissile >= b.missileInterval && launch(level, ship, pilot.getUUID(), pilot.getUUID(), target, b)) {
			guns.lastMissile = now;
			guns.lockTarget = null;
			guns.lockTicks = 0;
			guns.locked = false;
			return;
		}

		loseLock(pilot, guns);
	}

	/**
	 * Fires one concussion missile from {@code ship}'s rack at {@code target}, taking it from
	 * {@code owner}'s hold. False (nothing fired) when the hold has none. The released lock calls it;
	 * so do the game tests.
	 */
	public static boolean launch(ServerLevel level, ShipBody ship, UUID owner, @Nullable UUID shooter, Entity target, SpaceBalance b) {
		if (ShipHolds.take(level.getServer(), owner, M5Items.CONCUSSION_MISSILE, 1) < 1) {
			return false;
		}

		Vec3 nose = nose(ship);
		double[] rack = ship.rack();
		Vec3 from = ship.position().add(ShipBody.local(rack[0], rack[1], rack[2], ship.getYRot(), ship.getXRot()));
		ShipMissile.launch(level, ship, shooter, ShipBolt.Team.PLAYERS, from, nose, target, b.missileDamage, b.missileSplash, null, b);
		EffectEvents.toTracking(ship, SpaceEffects.SHIP_WEAPON, from, nose, ship.getId(), 1f, SpaceEffects.WEAPON_MISSILE);
		return true;
	}

	private static void loseLock(ServerPlayer pilot, Guns guns) {
		boolean had = guns.lockTarget != null;
		guns.lockTarget = null;
		guns.lockTicks = 0;
		guns.locked = false;

		if (had) {
			EffectEvents.toPlayer(pilot, SpaceEffects.MISSILE_LOCK, pilot.position(), null, -1, 0f, SpaceEffects.LOCK_LOST);
		}
	}

	// ---------------------------------------------------------------- incoming missiles

	/** A missile locks on {@code pilot}'s ship (a raider's lock or its missile in flight): the warning starts with the first. */
	public static void incomingStarted(ServerPlayer pilot) {
		int count = INCOMING.merge(pilot.getUUID(), 1, Integer::sum);

		if (count == 1) {
			EffectEvents.toPlayer(pilot, SpaceEffects.MISSILE_LOCK, pilot.position(), null, -1, 1f, SpaceEffects.LOCK_INCOMING);
		}
	}

	/** One lock or missile at {@code pilot}'s ship ended (fired on, hit, missed, expired, broken): the warning stops with the last. */
	public static void incomingEnded(ServerPlayer pilot) {
		Integer count = INCOMING.get(pilot.getUUID());

		if (count == null) {
			return;
		}

		if (count <= 1) {
			INCOMING.remove(pilot.getUUID());
			EffectEvents.toPlayer(pilot, SpaceEffects.MISSILE_LOCK, pilot.position(), null, -1, 0f, SpaceEffects.LOCK_INCOMING_END);
		} else {
			INCOMING.put(pilot.getUUID(), count - 1);
		}
	}

	/** Whether a missile locks on or flies at {@code pilot}'s ship. */
	public static boolean incoming(UUID pilot) {
		return INCOMING.containsKey(pilot);
	}

	// ---------------------------------------------------------------- status

	private static ShipCombatStatusPayload status(ServerPlayer p, int role, SpaceBalance b, long now) {
		ShipBody ship = role == ShipCombatStatusPayload.PILOT ? piloted(p) : gunned(p);
		UUID owner = ship == null ? null : ship.ownerId();

		if (owner == null) {
			return ShipCombatStatusPayload.OFF;
		}

		MinecraftServer server = p.level().getServer();
		ServerPlayer pilot = server.getPlayerList().getPlayer(owner);
		Guns guns = guns(owner);
		float shield = pilot == null ? 0f : ShipCombat.shieldPoints(pilot);
		float shieldMax = ShipRegistry.of(server, owner).map(r -> ShipCombat.maxShield(r, b)).orElse(0f);
		int missiles = (ship.upgrades() & ShipRecord.UPGRADE_MISSILES) == 0 ? -1 : ShipHolds.count(server, owner, M5Items.CONCUSSION_MISSILE);
		Entity target = guns.lockTarget == null || !(ship.level() instanceof ServerLevel level) ? null : level.getEntity(guns.lockTarget);
		float progress = target == null ? 0f : guns.locked ? 1f : SpaceRules.lockProgress(guns.lockTicks, b);
		return new ShipCombatStatusPayload(role, shield, shieldMax, guns.cannons.at(now, b), guns.cannons.locked(now), guns.turret.at(now, b),
				guns.turret.locked(now), missiles, target == null ? -1 : target.getId(), progress, incoming(owner), SpaceMissions.scanMeter(owner),
				(float) b.boltSpeed);
	}

	/** The player left: their triggers, warnings and their ship's guns are forgotten. */
	static void forget(UUID player) {
		CREW.remove(player);
		INCOMING.remove(player);
		GUNS.remove(player);
	}

	/** Tests: everything forgotten. */
	static void reset() {
		CREW.clear();
		GUNS.clear();
		TRACKS.clear();
		INCOMING.clear();
	}

	private ShipWeapons() {
	}
}
