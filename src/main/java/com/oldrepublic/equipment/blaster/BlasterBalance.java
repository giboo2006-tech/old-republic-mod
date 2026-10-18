package com.oldrepublic.equipment.blaster;

import com.oldrepublic.config.BalanceConfig;

/**
 * Tunable numbers of the player's blasters (spec MILESTONE_4.md 5.1 and 14.3), the {@code blasters}
 * section of {@code config/oldrepublic/balance.json}. Arrays hold one value per
 * {@link BlasterItem.Kind}: pistol, rifle, repeater.
 * <p>
 * Items are registered at mod init, before any balance file is read, so {@link #durability},
 * {@link #rifleSlowPct}, {@link #repeaterSlowPct} and {@link #bashDamage} (item components and
 * attributes) come from the defaults of {@code new BlasterBalance()}; a changed file value for them
 * does not apply. The client draws the reticle and heat gauge from the defaults too, because the
 * server's file is not synced. Every other key is read live on the server.
 */
public final class BlasterBalance {
	/** Damage per bolt. */
	public float[] damage = {4.0f, 5.0f, 2.5f};
	/** Ticks between shots while the trigger is held. */
	public int[] rate = {8, 10, 3};
	/** Repeater: ticks of barrel spin before the first bolt, again after each vent or overheat. */
	public int spinUp = 12;
	/** Repeater: a spun barrel winds down after this many ticks without a shot (W4 addition). */
	public int spinHoldTicks = 40;
	/** Bolt speed, blocks per tick. */
	public float[] speed = {2.2f, 2.6f, 2.4f};
	/** Bolt life, ticks. */
	public int[] life = {40, 60, 50};
	/** Hip-fire spread: the cone half-angle in degrees. */
	public float[] hipSpread = {2.0f, 2.5f, 4.0f};
	/** Sneaking steadies aim: spread times this. */
	public float steadyMultiplier = 0.35f;
	/** Moving faster than walking: spread times this. */
	public float moveMultiplier = 2f;
	/** Airborne: spread times this. */
	public float airMultiplier = 3f;
	/** Heat added per shot (0 to 100). */
	public float[] heatPerShot = {15f, 14f, 7f};
	/** Heat lost per tick once {@link #coolDelay} ticks have passed since the last shot. */
	public float[] cooling = {2.5f, 2.0f, 1.5f};
	/** Ticks after a shot before the blaster starts to cool. */
	public int coolDelay = 16;
	/** Ticks an overheated blaster cannot fire; its heat falls to 0 over the lock. */
	public int[] overheatLock = {50, 60, 80};
	/** Ticks a vented blaster cannot fire. */
	public int[] ventLock = {20, 25, 30};
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
	/** Repeater: targets a bolt passes through before it stops. */
	public int repeaterPierce = 1;
	/** Repeater: hits on one mob within {@link #suppressWindow} ticks that suppress it. */
	public int suppressHits = 4;
	/** Repeater: the suppression window, ticks. */
	public int suppressWindow = 40;
	/** Repeater: ticks a suppressed mob is slowed ({@code ControlEffects.SLOW}). */
	public int suppressTicks = 30;
	/** Melee bash damage (registration default only). */
	public float bashDamage = 2f;
	/** Ticks a bash staggers its target. */
	public int bashStagger = 6;
	/** Pistol: the first shot after drawing it fires this many ticks sooner than the rate. */
	public int quickDrawTicks = 4;
	/** Durability (registration default only). */
	public int[] durability = {600, 800, 900};

	/** The live server values. Never call during mod init. */
	public static BlasterBalance get() {
		return BalanceConfig.section("blasters", BlasterBalance.class, BlasterBalance::new);
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
