package com.oldrepublic.space;

import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.planet.Planets;

/**
 * Balance section {@code space} (spec MILESTONE_5.md 16.4): patrols, ship weapons and heat,
 * missiles and the lock, shields, the enemy starfighters and the gunship, kill rewards, the
 * blockade, space objectives and bounties, the Spindle and its capacitor, asteroids and the orbit
 * floor. Server side; the rules take an instance, so tests pass the defaults
 * ({@code new SpaceBalance()}) without touching the config file. Angles are in degrees, speeds in
 * blocks per tick, times in ticks unless a name says seconds.
 */
public final class SpaceBalance {
	/** The live {@code space} values. Never call during mod init. */
	public static SpaceBalance get() {
		return BalanceConfig.section("space", SpaceBalance.class, SpaceBalance::new);
	}

	// ---------------------------------------------------------------- patrols (5.6)

	/** Random patrols in orbit (14.4). */
	public boolean patrols = true;
	/** Ticks of armed flight in an orbit between two patrol checks, and the chance of each check. */
	public int patrolCheckTicks = 2400;
	public float patrolChance = 0.25f;
	/** No patrol within this many ticks of the owner's last encounter. */
	public int patrolGapTicks = 1200;
	/** Fighters in a patrol (min, max), and at Ghessar for a player at Hostile kajidic standing. */
	public int[] patrolSize = {2, 4};
	public int[] hostilePatrolSize = {3, 4};
	/** Where a patrol arrives (blocks from the ship), and the distance from all its fighters that ends it. */
	public int patrolDistance = 120;
	public int patrolLeaveRange = 200;
	/** XP for destroying a whole patrol. */
	public int patrolBonusXp = 300;
	/** Reserved: players' ships never damage each other in Milestone 5 (5.4); the value is not read. */
	public boolean pvpShipDamage = false;

	// ---------------------------------------------------------------- weapons (5.3)

	/** The pilot's twin cannons: ticks between shots, damage per bolt, heat per shot. */
	public int cannonInterval = 5;
	public float cannonDamage = 6f;
	public float cannonHeat = 7f;
	/** Every ship bolt's speed and range. */
	public double boltSpeed = 4.0;
	public int boltRange = 96;
	/** The gunner's dorsal turret (5.2). */
	public int turretInterval = 7;
	public float turretDamage = 5f;
	public float turretHeat = 7f;
	/** Heat (0 to 100): cooling per second after {@link #heatCoolDelay} ticks without a shot; at 100 the guns lock, then cool to {@link #overheatCoolTo}. */
	public float heatCoolPerSecond = 20f;
	public int heatCoolDelay = 10;
	public int overheatLockTicks = 60;
	public float overheatCoolTo = 40f;
	/** Concussion missiles: ticks between launches, damage, splash on ship targets within the radius, flight. */
	public int missileInterval = 20;
	public float missileDamage = 30f;
	public float missileSplash = 10f;
	public double missileSplashRadius = 3.0;
	public double missileSpeed = 1.6;
	public float missileTurn = 6f;
	public int missileLife = 100;
	/** The lock: a target within the cone of the nose and the range, held for the ticks. */
	public float lockCone = 20f;
	public double lockRange = 80.0;
	public int lockTicks = 30;

	// ---------------------------------------------------------------- shields (5.4, 11.2)

	/** Shield points with the {@code SHIELD} upgrade, per class; recharge per second after the delay without a hit. */
	public float courierShield = 40f;
	public float interceptorShield = 60f;
	public float shieldRecharge = 4f;
	public int shieldDelay = 60;
	/** A ship hit within this many ticks is under fire (the {@code UNDER_FIRE} refusal, 4.7). */
	public int underFireTicks = 200;
	/** The Spindle Capacitor: shield points added and the recharge multiplier (needs {@code SHIELD}). */
	public float capacitorShield = 20f;
	public float capacitorRechargeMult = 1.5f;

	// ---------------------------------------------------------------- starfighters (5.5)

	/** Fighters fire only with the target inside this cone of the nose; their bolts spread this much. */
	public float fighterAimCone = 10f;
	public float fighterSpread = 2f;
	/** A fighter's shield recharge per second after the delay without a hit. */
	public float fighterShieldRecharge = 5f;
	public int fighterShieldDelay = 60;
	public Fighter hymnInterceptor = Fighter.of(1.6, 10, 20, 4, 3f, 3, 0, 0f);
	public Fighter raider = Fighter.of(1.2, 20, 40, 5, 2f, 2, 200, 15f);
	public Fighter haskFighter = Fighter.of(1.4, 15, 30, 5, 2.5f, 2, 0, 0f);
	public Gunship gunship = new Gunship();
	/** Kill XP (M1 fatigue applies) and credits, by {@link SpaceRules.Kind}: Hymn interceptor, raider, Hask fighter, gunship. */
	public int[] killXp = {60, 80, 70, 600};
	public int[] killCredits = {10, 15, 10, 10};

	// ---------------------------------------------------------------- the blockade (5.6, 6.5)

	/** The gunship's distance ahead of the arrival; the two waves; the clan's escort; the Peaceful pass. */
	public int blockadeDistance = 150;
	public int[] blockadeWaves = {3, 4};
	public int clanFighters = 3;
	public int peacefulPassTicks = 200;
	/** Other players' armed ships within this many blocks join an encounter (5.6). */
	public int coopRange = 128;

	// ---------------------------------------------------------------- objectives and bounties (5.8, 5.9)

	/** The scan: a patrol fighter within range with line of sight for the ticks completes it. */
	public int scanRange = 48;
	public int scanTicks = 60;
	/** The nav buoy's distance from the start of a run (min, max). */
	public int[] buoyDistance = {600, 1000};
	/** A bounty's ace: shield and hull multiplier (its guns fire this much faster too), and its wingmen. */
	public float aceMultiplier = 2f;
	public int aceWingmen = 2;
	/** Fighters in a {@code space_clear} group without an ace (min, max). */
	public int[] clearGroupSize = {3, 6};

	// ---------------------------------------------------------------- the Silent Spindle (5.7)

	/** The station's place in the home system's orbit, the distance at which it is placed, and the vault's capacitor roll. */
	public int[] spindlePos = {420, 150, -380};
	public int spindlePlaceRange = 400;
	public float vaultSchematicChance = 0.1f;
	public int vaultSchematicPity = 8;
	/** The droid posts restock once per day when a player comes this near. */
	public int spindleDroidRange = 64;

	// ---------------------------------------------------------------- the orbits (5.1)

	/** Chunks per asteroid cluster, by {@link Planets.Planet} ordinal: home, Selvaren, Varkhast, Ghessar, Tyrvann. */
	public int[] asteroidDensity = {3, 4, 2, 3, 3};
	/** A player below this height in an orbit is put back aboard (5.1). */
	public int orbitCatchY = -56;

	/** Chunks per asteroid cluster in {@code planet}'s orbit (3 for a world without a row). */
	public int asteroidDensity(Planets.Planet planet) {
		int i = planet.ordinal();
		return i < asteroidDensity.length ? Math.max(1, asteroidDensity[i]) : 3;
	}

	/**
	 * One starfighter's numbers (5.5).
	 */
	public static final class Fighter {
		/** Top speed. */
		public double speed;
		/** Recharging shield points, and hull (the mob's health). */
		public float shield;
		public float hull;
		/** Damage per bolt; shots per second on average; shots per burst (1: a steady stream). */
		public float damage;
		public float rate;
		public int burst;
		/** Ticks between missiles (0: none) and a missile's damage. */
		public int missileInterval;
		public float missileDamage;

		public Fighter() {
		}

		static Fighter of(double speed, float shield, float hull, float damage, float rate, int burst, int missileInterval, float missileDamage) {
			Fighter f = new Fighter();
			f.speed = speed;
			f.shield = shield;
			f.hull = hull;
			f.damage = damage;
			f.rate = rate;
			f.burst = burst;
			f.missileInterval = missileInterval;
			f.missileDamage = missileDamage;
			return f;
		}
	}

	/**
	 * The blockade gunship's numbers (5.5).
	 */
	public static final class Gunship {
		public double speed = 0.3;
		public float hull = 400f;
		public float generatorHull = 80f;
		/** Each of the four turrets: damage per bolt, shots per second, range, and its bolts' spread in degrees. */
		public float turretDamage = 3f;
		public float turretRate = 2f;
		public int turretRange = 64;
		public float turretSpread = 3f;
		/** While the generator stands: interceptors launched every interval, how many, and at most how many alive. */
		public int launchInterval = 600;
		public int launchCount = 2;
		public int maxAlive = 6;
		/** The radius of its slow orbit around its post. */
		public int patrolRadius = 40;
	}
}
