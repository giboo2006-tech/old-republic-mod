package com.oldrepublic.equipment.blaster;

import java.util.Arrays;

import com.oldrepublic.config.BalanceConfig;

/**
 * Tunable numbers of the player's blasters (spec MILESTONE_4.md 5.1 and 14.3; the scatter blaster,
 * the carbine and the material mods of MILESTONE_7.md 9.1 and 9.2), the {@code blasters} section of
 * {@code config/oldrepublic/balance.json}. Arrays hold one value per {@link BlasterItem.Kind}:
 * pistol, rifle, repeater, scatter, carbine.
 * <p>
 * Items are registered at mod init, before any balance file is read, so {@link #durability},
 * {@link #rifleSlowPct}, {@link #repeaterSlowPct}, {@link #scatterSlowPct} and {@link #bashDamage}
 * (item components and attributes) come from the defaults of {@code new BlasterBalance()}; a changed
 * file value for them does not apply. The client draws the reticle, the heat gauge and the mods'
 * tooltips from the defaults too, because the server's file is not synced. Every other key is read
 * live on the server.
 * <p>
 * A file written before Milestone 7 lists three kinds; {@link #get()} gives the scatter blaster and
 * the carbine their own defaults rather than the repeater's numbers.
 */
public final class BlasterBalance {
	/** The kinds a file written before Milestone 7 lists (pistol, rifle, repeater). */
	private static final int LEGACY_KINDS = 3;
	/** The defaults, for padding a pre-Milestone 7 file; never handed out. */
	private static final BlasterBalance DEFAULTS = new BlasterBalance();

	/** Damage per bolt: the scatter's per pellet, the carbine's per bolt of a burst. */
	public float[] damage = {4.0f, 5.0f, 2.5f, 1.6f, 2.6f};
	/** Ticks between shots while the trigger is held; the carbine's from one burst to the next. */
	public int[] rate = {8, 10, 3, 18, 14};
	/** Repeater: ticks of barrel spin before the first bolt, again after each vent or overheat. */
	public int spinUp = 12;
	/** Repeater: a spun barrel winds down after this many ticks without a shot (W4 addition). */
	public int spinHoldTicks = 40;
	/** Bolt speed, blocks per tick. */
	public float[] speed = {2.2f, 2.6f, 2.4f, 1.8f, 2.5f};
	/** Bolt life, ticks (the scatter's pellets die young: its reach is short). */
	public int[] life = {40, 60, 50, 8, 50};
	/** Hip-fire spread: the cone half-angle in degrees (the scatter's pellets spread over it). */
	public float[] hipSpread = {2.0f, 2.5f, 4.0f, 8.0f, 1.2f};
	/** Sneaking steadies aim: spread times this. */
	public float steadyMultiplier = 0.35f;
	/** Moving faster than walking: spread times this. */
	public float moveMultiplier = 2f;
	/** Airborne: spread times this. */
	public float airMultiplier = 3f;
	/** Heat added per shot (0 to 100): the scatter's per blast, the carbine's per burst, shared by its bolts. */
	public float[] heatPerShot = {15f, 14f, 7f, 28f, 20f};
	/** Heat lost per tick once {@link #coolDelay} ticks have passed since the last shot. */
	public float[] cooling = {2.5f, 2.0f, 1.5f, 2.5f, 2.2f};
	/** Ticks after a shot before the blaster starts to cool. */
	public int coolDelay = 16;
	/** Ticks an overheated blaster cannot fire; its heat falls to 0 over the lock. */
	public int[] overheatLock = {50, 60, 80, 70, 70};
	/** Ticks a vented blaster cannot fire. */
	public int[] ventLock = {20, 25, 30, 25, 25};
	/** Venting below this heat does nothing. */
	public float ventMin = 30f;
	/** A vent with heat inside this band (inclusive) is a perfect vent: half the lock, then free shots. */
	public float[] perfectVentBand = {70f, 90f};
	/** Shots after a perfect vent that add no heat. */
	public int perfectVentFreeShots = 3;
	/** Rifle: ticks Weapon Action is held to load the charged shot. */
	public int chargedTicks = 30;
	/** Rifle: damage of the charged (heavy) bolt. */
	public float chargedDamage = 12f;
	/** Rifle: heat of the charged shot. */
	public float chargedHeat = 35f;
	/** Repeater: movement slow while firing, percent (registration default only). */
	public float repeaterSlowPct = 30f;
	/** Rifle: movement slow while firing, percent (registration default only). */
	public float rifleSlowPct = 10f;
	/** Scatter: movement slow while firing, percent (registration default only). */
	public float scatterSlowPct = 15f;
	/** Repeater: targets a bolt passes through before it stops. */
	public int repeaterPierce = 1;
	/** Repeater: hits on one mob within {@link #suppressWindow} ticks that suppress it. */
	public int suppressHits = 4;
	/** Repeater: the suppression window, ticks. */
	public int suppressWindow = 40;
	/** Repeater: ticks a suppressed mob is slowed ({@code ControlEffects.SLOW}). */
	public int suppressTicks = 30;
	/**
	 * Scatter: pellets per blast, each a bolt of {@link #damage} inside the spread. Pellets of one blast
	 * that strike the same target all land (the hurt cooldown does not swallow them).
	 */
	public int scatterPellets = 6;
	/** Carbine: bolts per burst, each of {@link #damage}; the burst's heat is shared out between them. */
	public int burstSize = 3;
	/** Carbine: ticks between the bolts of a burst. */
	public int burstGap = 2;
	/** {@code mod_aurelite_barrel}: change of spread, percent. */
	public float aureliteSpreadPct = -30f;
	/** {@code mod_aurelite_barrel}: change of bolt damage, percent. */
	public float aureliteDamagePct = 5f;
	/** {@code mod_duskglass_cell}: change of heat per shot, percent. */
	public float duskglassHeatPct = -20f;
	/** Melee bash damage (registration default only). */
	public float bashDamage = 2f;
	/** Ticks a bash staggers its target. */
	public int bashStagger = 6;
	/** Pistol: the first shot after drawing it fires this many ticks sooner than the rate. */
	public int quickDrawTicks = 4;
	/** Durability (registration default only). */
	public int[] durability = {600, 800, 900, 700, 750};

	/** The live server values. Never call during mod init. */
	public static BlasterBalance get() {
		BlasterBalance b = BalanceConfig.section("blasters", BlasterBalance.class, BlasterBalance::new);
		b.fillNewKinds();
		return b;
	}

	/**
	 * Gives the kinds a pre-Milestone 7 file does not list their defaults: an array with at least the
	 * three old entries but fewer than the kinds is padded from {@code new BlasterBalance()}. Values in
	 * the file stay; a shorter array keeps the old rule of {@link #of} (the last value). Idempotent;
	 * {@link #get()} calls it, tests call it on their own instances.
	 */
	public void fillNewKinds() {
		damage = padded(damage, DEFAULTS.damage);
		rate = padded(rate, DEFAULTS.rate);
		speed = padded(speed, DEFAULTS.speed);
		life = padded(life, DEFAULTS.life);
		hipSpread = padded(hipSpread, DEFAULTS.hipSpread);
		heatPerShot = padded(heatPerShot, DEFAULTS.heatPerShot);
		cooling = padded(cooling, DEFAULTS.cooling);
		overheatLock = padded(overheatLock, DEFAULTS.overheatLock);
		ventLock = padded(ventLock, DEFAULTS.ventLock);
		durability = padded(durability, DEFAULTS.durability);
	}

	private static float[] padded(float[] values, float[] defaults) {
		if (values.length < LEGACY_KINDS || values.length >= defaults.length) {
			return values;
		}

		float[] out = Arrays.copyOf(defaults, defaults.length);
		System.arraycopy(values, 0, out, 0, values.length);
		return out;
	}

	private static int[] padded(int[] values, int[] defaults) {
		if (values.length < LEGACY_KINDS || values.length >= defaults.length) {
			return values;
		}

		int[] out = Arrays.copyOf(defaults, defaults.length);
		System.arraycopy(values, 0, out, 0, values.length);
		return out;
	}

	/** {@code values[kind]}, or the last value when the array is short (a hand-edited file). */
	public static float of(float[] values, BlasterItem.Kind kind) {
		return values.length == 0 ? 0f : values[Math.min(values.length - 1, kind.ordinal())];
	}

	/** {@code values[kind]}, or the last value when the array is short (a hand-edited file). */
	public static int of(int[] values, BlasterItem.Kind kind) {
		return values.length == 0 ? 0 : values[Math.min(values.length - 1, kind.ordinal())];
	}

	/** The lower bound of the perfect vent band. */
	public float perfectLow() {
		return perfectVentBand.length > 0 ? perfectVentBand[0] : 70f;
	}

	/** The upper bound of the perfect vent band. */
	public float perfectHigh() {
		return perfectVentBand.length > 1 ? perfectVentBand[1] : 90f;
	}
}
