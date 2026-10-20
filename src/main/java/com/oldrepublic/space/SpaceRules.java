package com.oldrepublic.space;

import com.oldrepublic.planet.Planets;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The pure rules of space combat (spec MILESTONE_5.md 5.3 to 5.9): heat, shields, the missile lock
 * cone, patrol checks and their makeup, the scan meter, points inside an orbit and the kill rewards.
 * Every function takes the time, the draw or the balance as an argument, so the game tests check
 * each timer without waiting for it.
 */
public final class SpaceRules {
	/** Every point W2 places in an orbit lies this near (0, 0), horizontally (5.6, 22 line 50). */
	public static final double ORBIT_POINT_RADIUS = 1920;
	/** The band W2 places points in: above the arrivals' atmosphere line, below the ceiling. */
	public static final double POINT_MIN_Y = 90;
	public static final double POINT_MAX_Y = 260;

	/** What a ship target is, for its rewards: an index into {@code killXp} and {@code killCredits}. */
	public enum Kind {
		HYMN_INTERCEPTOR,
		RAIDER,
		HASK_FIGHTER,
		GUNSHIP,
		/** The gunship's shield generator: part of the gunship, no reward of its own. */
		GENERATOR
	}

	/** Which side a starfighter flies for. Synced by ordinal: append only. */
	public enum Side {
		/** Fights the players' ships and their allies. */
		HOSTILE,
		/** Fights for the players (the clan's escort at the blockade, 6.5). */
		ALLIED,
		/** Holds fire until a player's weapons hit it (a scanning patrol, 5.9). */
		NEUTRAL;

		public static Side of(int ordinal) {
			Side[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}
	}

	// ---------------------------------------------------------------- heat (5.3)

	/**
	 * A gun's heat: {@code heat} as of the last shot, when that was, and until when an overheat locks
	 * it. The heat now is a function of these and the time ({@link #at}).
	 */
	public record Heat(float heat, long lastShot, long lockedUntil) {
		/** A gun that never fired. */
		public static final Heat COLD = new Heat(0f, Long.MIN_VALUE / 4, Long.MIN_VALUE / 4);

		public boolean locked(long now) {
			return now < lockedUntil;
		}

		/**
		 * The heat at {@code now}: during a lock it falls from 100 to {@code overheatCoolTo}; otherwise it
		 * holds for {@code heatCoolDelay} ticks after the last shot (or from the end of a lock), then cools
		 * {@code heatCoolPerSecond}.
		 */
		public float at(long now, SpaceBalance b) {
			if (locked(now)) {
				float t = 1f - (lockedUntil - now) / (float) Math.max(1, b.overheatLockTicks);
				return 100f + (b.overheatCoolTo - 100f) * t;
			}

			// An overheat sets lockedUntil after its own shot; a later shot (even one on the tick the lock ends) clears this.
			boolean afterLock = lockedUntil > lastShot;
			float base = afterLock ? b.overheatCoolTo : heat;
			long coolFrom = afterLock ? lockedUntil : lastShot + b.heatCoolDelay;
			return now <= coolFrom ? base : Math.max(0f, base - (now - coolFrom) * b.heatCoolPerSecond / 20f);
		}

		/** Whether the gun may fire at {@code now}: not locked, and {@code interval} ticks since the last shot. */
		public boolean ready(long now, int interval) {
			return !locked(now) && now - lastShot >= interval;
		}

		/** The heat after a shot at {@code now}: reaching 100 locks the gun for {@code overheatLockTicks}. */
		public Heat fire(long now, float perShot, SpaceBalance b) {
			float next = at(now, b) + perShot;
			return next >= 100f ? new Heat(100f, now, now + b.overheatLockTicks) : new Heat(next, now, lockedUntil);
		}
	}

	// ---------------------------------------------------------------- shields (5.4, 11.2)

	/** A player's ship's shield points: 0 without {@code SHIELD}; the interceptor's or the courier's, plus the capacitor's. */
	public static float maxShield(boolean shield, boolean interceptor, boolean capacitor, SpaceBalance b) {
		if (!shield) {
			return 0f;
		}

		return (interceptor ? b.interceptorShield : b.courierShield) + (capacitor ? b.capacitorShield : 0f);
	}

	/** Shield points a player's ship recharges per second (the capacitor multiplies it). */
	public static float rechargePerSecond(boolean capacitor, SpaceBalance b) {
		return b.shieldRecharge * (capacitor ? b.capacitorRechargeMult : 1f);
	}

	/** The shield one tick later: unchanged until {@code delay} ticks after the last hit, then recharging toward {@code max}. */
	public static float recharge(float points, float max, long sinceHit, int delay, float perSecond) {
		if (sinceHit < delay || points >= max) {
			return Math.min(points, max);
		}

		return Math.min(max, points + perSecond / 20f);
	}

	/** How much of {@code damage} the shield {@code points} absorbs; the rest reaches the hull. */
	public static float absorbed(float points, float damage) {
		return Math.max(0f, Math.min(points, damage));
	}

	// ---------------------------------------------------------------- aiming (5.3, 5.5)

	/** Whether {@code target} lies within {@code coneDegrees} of {@code heading} (a unit vector) from {@code from}, and within {@code range}. */
	public static boolean inCone(Vec3 from, Vec3 heading, Vec3 target, float coneDegrees, double range) {
		Vec3 to = target.subtract(from);
		double distance = to.length();

		if (distance > range) {
			return false;
		}

		if (distance < 1.0E-4) {
			return true;
		}

		return heading.dot(to.scale(1 / distance)) >= Math.cos(Math.toRadians(coneDegrees));
	}

	/** Whether a missile lock held in the cone for {@code ticks} ticks is complete ({@code space.lockTicks}, 5.3). */
	public static boolean lockHeld(int ticks, SpaceBalance b) {
		return ticks >= Math.max(1, b.lockTicks);
	}

	/** A lock's progress after {@code ticks} ticks in the cone, 0 to 1 (the HUD's ring and the rising tone). */
	public static float lockProgress(int ticks, SpaceBalance b) {
		return Mth.clamp(ticks / (float) Math.max(1, b.lockTicks), 0f, 1f);
	}

	/** Where to aim to meet a target at {@code target} moving {@code velocity} per tick with a bolt of {@code speed}, from {@code from}. */
	public static Vec3 lead(Vec3 from, Vec3 target, Vec3 velocity, double speed) {
		double ticks = target.distanceTo(from) / Math.max(0.1, speed);

		// Two passes: the time to the predicted point is a better guess than the time to the target.
		Vec3 guess = target.add(velocity.scale(ticks));
		ticks = guess.distanceTo(from) / Math.max(0.1, speed);
		return target.add(velocity.scale(ticks));
	}

	/** {@code dir} turned by a random angle up to {@code degrees} in a random direction (a fighter's spread). */
	public static Vec3 spread(Vec3 dir, float degrees, RandomSource random) {
		Vec3 axis = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 a = dir.cross(axis).normalize();
		Vec3 c = dir.cross(a).normalize();
		double angle = Math.toRadians(degrees) * Math.sqrt(random.nextDouble());
		double around = random.nextDouble() * Math.PI * 2;
		Vec3 off = a.scale(Math.cos(around)).add(c.scale(Math.sin(around)));
		return dir.scale(Math.cos(angle)).add(off.scale(Math.sin(angle))).normalize();
	}

	/** A heading turned toward {@code wanted} by at most {@code maxDegrees} (both unit vectors). */
	public static Vec3 turnToward(Vec3 heading, Vec3 wanted, float maxDegrees) {
		double cos = Mth.clamp(heading.dot(wanted), -1, 1);
		double angle = Math.acos(cos);
		double max = Math.toRadians(maxDegrees);

		if (angle <= max || angle < 1.0E-5) {
			return wanted;
		}

		// Spherical step from heading toward wanted by max radians.
		Vec3 ortho = wanted.subtract(heading.scale(cos));

		if (ortho.lengthSqr() < 1.0E-8) {
			ortho = Math.abs(heading.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
			ortho = ortho.subtract(heading.scale(ortho.dot(heading)));
		}

		ortho = ortho.normalize();
		return heading.scale(Math.cos(max)).add(ortho.scale(Math.sin(max))).normalize();
	}

	/** Entity yaw (degrees, 0 toward +Z) of a direction. */
	public static float yawOf(Vec3 dir) {
		return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
	}

	/** Entity pitch (degrees, positive down) of a direction. */
	public static float pitchOf(Vec3 dir) {
		return (float) Math.toDegrees(-Math.atan2(dir.y, dir.horizontalDistance()));
	}

	// ---------------------------------------------------------------- patrols and encounters (5.6)

	/**
	 * Whether a patrol finds an owner who has flown armed in an orbit for {@code armedTicks}: on every
	 * {@code patrolCheckTicks}th tick, never within {@code patrolGapTicks} of the last encounter, with
	 * the chance {@code patrolChance} ({@code roll} 0 to 1).
	 */
	public static boolean patrolFinds(boolean armed, long armedTicks, long now, long lastEncounterEnd, float roll, SpaceBalance b) {
		return b.patrols && armed && armedTicks > 0 && armedTicks % Math.max(1, b.patrolCheckTicks) == 0
				&& now - lastEncounterEnd >= b.patrolGapTicks && roll < b.patrolChance;
	}

	/** Who patrols an orbit (5.6): Tyrvann's interceptors until the owner's blockade falls, then Hask fighters for a clan enemy. */
	public static Kind patrolKind(Planets.Planet system, boolean blockadeBroken, boolean clanHostile) {
		return switch (system) {
			case TYRVANN -> !blockadeBroken ? Kind.HYMN_INTERCEPTOR : clanHostile ? Kind.HASK_FIGHTER : Kind.RAIDER;
			default -> Kind.RAIDER;
		};
	}

	/** A patrol's size: a larger group at Ghessar for a player at Hostile kajidic standing. */
	public static int patrolSize(Planets.Planet system, boolean kajidicHostile, SpaceBalance b, RandomSource random) {
		int[] range = system == Planets.Planet.GHESSAR && kajidicHostile ? b.hostilePatrolSize : b.patrolSize;
		return between(range, random, 2);
	}

	/** A value from a {@code [min, max]} balance pair ({@code fallback} for a malformed one). */
	public static int between(int[] range, RandomSource random, int fallback) {
		if (range.length < 2) {
			return range.length == 1 ? range[0] : fallback;
		}

		int min = Math.min(range[0], range[1]);
		int max = Math.max(range[0], range[1]);
		return min + random.nextInt(max - min + 1);
	}

	/** Whether another player's ship joins an encounter (5.6): armed, and nearer the owner's ship than {@code space.coopRange}. */
	public static boolean coopJoins(boolean armed, double distanceSqr, SpaceBalance b) {
		return armed && distanceSqr < (double) b.coopRange * b.coopRange;
	}

	/** Whether a co-op pilot leaves the encounter: more than twice {@code space.coopRange} from the owner's ship (or not flying at all). */
	public static boolean coopLeaves(double distanceSqr, SpaceBalance b) {
		return distanceSqr > 4.0 * b.coopRange * b.coopRange;
	}

	/** The scan meter one tick later (5.9): filling while a patrol fighter scans, draining while none does, 0 to {@code scanTicks}. */
	public static int scanStep(int meter, boolean scanning, SpaceBalance b) {
		return Mth.clamp(meter + (scanning ? 1 : -1), 0, Math.max(1, b.scanTicks));
	}

	// ---------------------------------------------------------------- points in an orbit

	/** {@code p} moved inside the orbit's placing band: within {@link #ORBIT_POINT_RADIUS} of (0, 0) and between the band's heights. */
	public static Vec3 clampToOrbit(Vec3 p) {
		double horizontal = Math.sqrt(p.x * p.x + p.z * p.z);
		double scale = horizontal > ORBIT_POINT_RADIUS ? ORBIT_POINT_RADIUS / horizontal : 1;
		return new Vec3(p.x * scale, Mth.clamp(p.y, POINT_MIN_Y, POINT_MAX_Y), p.z * scale);
	}

	/** Whether {@code p} lies inside the placing band. */
	public static boolean insideOrbit(Vec3 p) {
		return p.x * p.x + p.z * p.z <= ORBIT_POINT_RADIUS * ORBIT_POINT_RADIUS + 1 && p.y >= POINT_MIN_Y - 1 && p.y <= POINT_MAX_Y + 1;
	}

	/**
	 * A nav buoy's point for a run starting at {@code start} (5.9): {@code buoyDistance} away in a
	 * random direction, inside the orbit. Directions that would leave the orbit are tried again; the
	 * last try is pulled inside.
	 */
	public static Vec3 buoyPoint(Vec3 start, SpaceBalance b, RandomSource random) {
		Vec3 inside = clampToOrbit(start);
		Vec3 best = inside;

		for (int i = 0; i < 16; i++) {
			double distance = between(b.buoyDistance, random, 800);
			double angle = random.nextDouble() * Math.PI * 2;
			Vec3 p = new Vec3(inside.x + Math.cos(angle) * distance, inside.y + (random.nextDouble() * 2 - 1) * 40,
					inside.z + Math.sin(angle) * distance);
			best = clampToOrbit(p);

			if (insideOrbit(p)) {
				return best;
			}
		}

		return best;
	}

	/** A point in an orbit for a {@code space_clear} group, from a seed (a bounty's day): 300 to 1,600 blocks out. */
	public static Vec3 clearPoint(long seed) {
		RandomSource random = RandomSource.create(seed);
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = 300 + random.nextDouble() * 1300;
		double y = 120 + random.nextDouble() * 100;
		return clampToOrbit(new Vec3(Math.cos(angle) * distance, y, Math.sin(angle) * distance));
	}

	// ---------------------------------------------------------------- the floor and the Spindle (5.1, 5.7)

	/**
	 * Whether the orbit floor catches a player (5.1): in an orbit, below {@code floor}, alive, not a
	 * spectator, not flying (creative flight) and riding nothing.
	 */
	public static boolean caughtByFloor(boolean orbit, double y, int floor, boolean alive, boolean spectator, boolean flying, boolean riding) {
		return orbit && y < floor && alive && !spectator && !flying && !riding;
	}

	/** Whether the Spindle's vault opens for a player who last opened it on {@code lastDay}: once per in-game day (5.7). */
	public static boolean vaultOpens(long lastDay, long today) {
		return lastDay < today;
	}

	// ---------------------------------------------------------------- rewards (5.5)

	public static int killXp(Kind kind, SpaceBalance b) {
		int i = kind.ordinal();
		return kind != Kind.GENERATOR && i < b.killXp.length ? b.killXp[i] : 0;
	}

	public static int killCredits(Kind kind, SpaceBalance b) {
		int i = kind.ordinal();
		return kind != Kind.GENERATOR && i < b.killCredits.length ? b.killCredits[i] : 0;
	}

	/** A fighter's numbers by kind (the gunship and generator have none). */
	public static SpaceBalance.Fighter fighter(Kind kind, SpaceBalance b) {
		return switch (kind) {
			case RAIDER -> b.raider;
			case HASK_FIGHTER -> b.haskFighter;
			default -> b.hymnInterceptor;
		};
	}

	/** Ticks between shots inside a burst, and the pause that keeps the average {@code rate} per second. */
	public static int[] burstTiming(float rate, int burst) {
		int shots = Math.max(1, burst);
		int cycle = Math.max(shots, Math.round(20f * shots / Math.max(0.1f, rate)));
		int spacing = shots == 1 ? cycle : Math.max(2, Math.min(4, cycle / (shots * 2)));
		return new int[] {spacing, cycle};
	}

	private SpaceRules() {
	}
}
