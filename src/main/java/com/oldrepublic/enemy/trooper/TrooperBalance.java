package com.oldrepublic.enemy.trooper;

import com.oldrepublic.config.BalanceConfig;

/**
 * Tunable numbers for Republic and Imperial troopers (spec 7 and 14), stored in the {@code trooper}
 * section of {@code config/oldrepublic/balance.json}. Read on the server only, never during mod
 * init. Ticks are game ticks, ranges are blocks, damage and health are half hearts.
 */
public final class TrooperBalance {
	// ---------------------------------------------------------------- roles: {health, armour}

	public float[] republicRifleman = {24, 4};
	public float[] republicMedic = {20, 2};
	public float[] republicSergeant = {30, 6};
	public float[] imperialRifleman = {24, 5};
	public float[] imperialGrenadier = {26, 5};
	public float[] imperialSergeant = {30, 7};

	// ---------------------------------------------------------------- movement and shooting

	/** Movement speed attribute. */
	public float speed = 0.27f;
	/** The charge tell before every shot or burst (muzzle glow and whine). */
	public int chargeTicks = 12;
	/** The distance band a rifleman keeps to its target. */
	public int bandMin = 10;
	public int bandMax = 18;
	/** Republic rifle: bolts per burst, ticks between them and ticks between bursts. */
	public float republicBoltDamage = 4;
	public int burstSize = 3;
	public int burstGap = 4;
	public int burstCooldown = 50;
	/** After this many bursts the Republic rifle vents and cannot fire for {@link #ventTicks}. */
	public int ventAfterBursts = 3;
	public int ventTicks = 40;
	/** Imperial rifle: one strong, normal (not heavy) bolt per cooldown. */
	public float imperialBoltDamage = 7;
	public int imperialCooldown = 34;
	/** The Imperial flanker circles 90 degrees off the squad's line at this distance from the target. */
	public int[] flankerRange = {12, 16};
	/** Medic and grenadier sidearm. */
	public float pistolDamage = 4;
	public int pistolCooldown = 40;

	// ---------------------------------------------------------------- role specials

	/** Field medic: heals an ally at or below {@link #medicThreshold} of its health after a channel. */
	public float medicHeal = 8;
	public float medicThreshold = 0.5f;
	public int medicChannelTicks = 20;
	public int medicCooldown = 160;
	/** A single hit of at least this much interrupts the medic's channel. */
	public float interruptDamage = 4;
	/** Republic sergeant's Rally: tell, buff duration, cooldown and the burst gap reduction. */
	public int rallyTellTicks = 30;
	public int rallyTicks = 200;
	public int rallyCooldown = 600;
	public float rallyFireRate = 0.25f;
	/** Grenadier: one grenade per interval; the frag grenade (part B) reads the fuse and burst values. */
	public int grenadeInterval = 200;
	public int grenadeTellTicks = 20;
	public int grenadeFuseTicks = 40;
	public float grenadeDamage = 8;
	public float grenadeRadius = 3;
	public float grenadeKnockback = 0.5f;
	public int[] grenadeRange = {6, 14};
	/** A pushed grenade flies on at this speed (blocks per tick). */
	public float grenadePushSpeed = 1.2f;
	/** Imperial sergeant's flare: tell, delay until the reinforcements arrive, how many and from how far. */
	public int reinforceTellTicks = 30;
	public int reinforceDelayTicks = 200;
	public int reinforceCount = 2;
	public int[] reinforceDistance = {24, 32};
	/** A rifleman with {@link #deflectCount} bolts deflected within the window holds fire, then sidesteps. */
	public int deflectWindowTicks = 100;
	public int deflectCount = 2;
	public int holdFireTicks = 40;
	public int[] repositionRange = {4, 8};

	// ---------------------------------------------------------------- drops

	/** Chance of one durasteel plate when a player kills the trooper. */
	public float durasteelChance = 0.2f;

	// ---------------------------------------------------------------- attitudes (spec 5.2)

	/** A HOSTILE tier player is attacked on sight within this range. */
	public int hostileRange = 24;
	/** The Wary leader warns a player within this range, at most once per {@link #warnRepeatTicks}. */
	public int warnRange = 16;
	/** After the warning, a player staying within this range for {@link #waryTicks} (or lighting a saber) is engaged. */
	public int waryRange = 8;
	public int waryTicks = 120;
	public int warnRepeatTicks = 600;
	/** Allied troopers defend their player from attackers within this range. */
	public int allyDefendRange = 16;
	public int provokedTicks = 1200;
	public int engagedTicks = 1200;
	/** How long an incident started by an ally defence lasts. */
	public int incidentTicks = 1200;

	// ---------------------------------------------------------------- cold peace (spec 7.5)

	/** Untagged patrols of both armies within this range halt and face each other. */
	public int standoffRange = 16;
	/** They keep at least this far apart. */
	public int standoffDistance = 12;
	/** They walk on after this long. */
	public int standoffTicks = 200;

	// ---------------------------------------------------------------- barks

	/** Barks are shown to players within this range. */
	public int barkRange = 24;
	/** At most one bark per trooper per this many ticks. */
	public int barkTrooperTicks = 120;
	/** At most one bark per squad per this many ticks. */
	public int barkSquadTicks = 40;

	// ---------------------------------------------------------------- squads

	/** One extra rifleman when the nearest player is at least this level. */
	public int extraRiflemanLevel = 11;
	/** With the sergeant dead and half the squad down, the rest fall back this far for this long. */
	public int moraleRetreatTicks = 200;
	public int moraleRetreatDistance = 24;
	/** Republic and Imperial troopers never fight each other without an incident. */
	public boolean coldPeace = true;

	// ---------------------------------------------------------------- W4 additions (not in spec 14)

	/** Bolt speeds in blocks per tick: the Imperial bolt is the slow, hard one (spec 7.3). */
	public float boltSpeed = 1.8f;
	public float imperialBoltSpeed = 1.35f;
	public int boltLifetimeTicks = 60;
	/** Vanilla projectile inaccuracy units at Normal difficulty (x1.5 on Easy, x0.6 on Hard). */
	public float aimInaccuracy = 4;
	/** How far troopers notice and track targets. */
	public float followRange = 32;
	/** Allies within this range of the sergeant are rallied; a Rally needs at least {@link #rallyMinAlive} alive. */
	public int rallyRadius = 12;
	public int rallyMinAlive = 2;
	/** How far the medic goes to reach a wounded ally. */
	public int medicRange = 16;
	/** Squad members keep this far from each other while following the leader. */
	public int[] squadSpacing = {3, 5};
	/** Riflemen below this fraction of their health look for cover (spec 7.1), then hold it a while. */
	public float coverHealthFraction = 0.5f;
	public float coverSpeed = 1.3f;
	public int[] coverHoldTicks = {40, 60};
	public int coverCooldownTicks = 200;
	/** An Allied squad salutes a player within this range; the Neutral "move along" needs a lit saber this close. */
	public int saluteRange = 8;
	public int moveAlongRange = 4;
	/** How far a stood-up downed trooper looks for its army's outpost terminal to walk home to. */
	public int homeRange = 384;
	/**
	 * A squad's encounter ends after this many ticks in which no member fought (spec 7.3, "once per
	 * encounter"): the fallen member and the used flare are forgotten, and flare arrivals that are
	 * not on patrol walk off.
	 */
	public int encounterEndTicks = 600;

	public static TrooperBalance get() {
		return BalanceConfig.section("trooper", TrooperBalance.class, TrooperBalance::new);
	}
}
