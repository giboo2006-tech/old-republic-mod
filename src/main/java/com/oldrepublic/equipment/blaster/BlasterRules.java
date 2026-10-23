package com.oldrepublic.equipment.blaster;

import org.jspecify.annotations.Nullable;

/**
 * The pure arithmetic of the player's blasters (spec MILESTONE_4.md 5.1): heat, cooling, overheat,
 * venting, aim spread, the rifle's charge, the repeater's spin-up and suppression, the draw delay,
 * and the scatter's pellets and the carbine's burst (MILESTONE_7.md 9.1). Nothing here reads the
 * world, so game tests drive every rule with explicit ticks, and the client gauge uses the same
 * functions on the synced component.
 * <p>
 * {@link BlasterHeat} stores the heat as it was at {@code lastShot}. While a lock runs
 * ({@code now < lockedUntil}) the heat falls in a straight line to 0 at the lock's end (an overheat
 * stores 100, a vent stores 0); a finished lock leaves the blaster cold; otherwise the heat falls by
 * the cooling rate once {@code coolDelay} ticks have passed since the last shot.
 */
public final class BlasterRules {
	/** Heat at which a blaster overheats. */
	public static final float MAX_HEAT = 100f;
	/**
	 * Heat this close below {@link #MAX_HEAT} counts as full: the carbine's bolts each add a third of
	 * the burst's heat, and fifteen thirds of 20 sum to 99.99999 in floats.
	 */
	private static final float HEAT_EPSILON = 1.0e-3f;

	/** The outcome of pressing Weapon Action to vent. */
	public enum VentResult {
		/** Below {@code ventMin}, or locked: nothing happens. */
		NONE,
		/** Vented to 0 with the vent lock. */
		VENT,
		/** Vented inside the perfect band: half the lock and free shots. */
		PERFECT
	}

	/**
	 * The heat of {@code h} at {@code now}.
	 *
	 * @param cooling heat lost per tick once cooling has started
	 * @param coolDelay ticks after the last shot before cooling starts
	 */
	public static float heatAt(BlasterHeat h, long now, float cooling, int coolDelay) {
		if (now < h.lockedUntil()) {
			long span = h.lockedUntil() - h.lastShot();
			return span <= 0 ? 0f : Math.max(0f, h.heat() * (h.lockedUntil() - now) / (float) span);
		}

		if (h.lockedUntil() > h.lastShot()) {
			// The lock has run out: an overheat has bled off, a vent was already at 0.
			return 0f;
		}

		long cooled = Math.max(0L, now - h.lastShot() - coolDelay);
		return Math.max(0f, h.heat() - cooling * cooled);
	}

	/** {@link #heatAt(BlasterHeat, long, float, int)} with the kind's numbers from {@code b}. */
	public static float heatAt(BlasterHeat h, long now, BlasterItem.Kind kind, BlasterBalance b) {
		return heatAt(h, now, BlasterBalance.of(b.cooling, kind), b.coolDelay);
	}

	/** Whether a lock (overheat or vent) keeps the blaster from firing at {@code now}. */
	public static boolean locked(BlasterHeat h, long now) {
		return now < h.lockedUntil();
	}

	/** Whether the blaster is in an overheat lock (a lock that started at full heat). */
	public static boolean overheated(BlasterHeat h, long now) {
		return locked(h, now) && h.heat() >= MAX_HEAT;
	}

	/**
	 * The heat after one shot fired at {@code now} that adds {@code heatPerShot}. A shot after a
	 * perfect vent spends a free shot and adds nothing. Reaching {@link #MAX_HEAT} overheats: full
	 * heat, locked for {@code overheatLock} ticks. (A carbine bolt is one shot here: each bolt of a
	 * burst adds {@link #heatPerBolt} and may spend a free shot.)
	 */
	public static BlasterHeat afterShot(BlasterHeat h, long now, float heatPerShot, float cooling, int coolDelay, int overheatLock) {
		float current = heatAt(h, now, cooling, coolDelay);

		if (h.freeShots() > 0) {
			return new BlasterHeat(current, now, 0L, h.freeShots() - 1);
		}

		float next = current + heatPerShot;

		if (next >= MAX_HEAT - HEAT_EPSILON) {
			return new BlasterHeat(MAX_HEAT, now, now + overheatLock, 0);
		}

		return new BlasterHeat(next, now, 0L, 0);
	}

	/** {@link #afterShot(BlasterHeat, long, float, float, int, int)} with the kind's numbers from {@code b}. */
	public static BlasterHeat afterShot(BlasterHeat h, long now, float heatPerShot, BlasterItem.Kind kind, BlasterBalance b) {
		return afterShot(h, now, heatPerShot, BlasterBalance.of(b.cooling, kind), b.coolDelay, BlasterBalance.of(b.overheatLock, kind));
	}

	/** What a vent at {@code now} would be. */
	public static VentResult ventResult(BlasterHeat h, long now, BlasterItem.Kind kind, BlasterBalance b) {
		if (locked(h, now)) {
			return VentResult.NONE;
		}

		float heat = heatAt(h, now, kind, b);

		if (heat < b.ventMin) {
			return VentResult.NONE;
		}

		return heat >= b.perfectLow() && heat <= b.perfectHigh() ? VentResult.PERFECT : VentResult.VENT;
	}

	/**
	 * The heat after venting at {@code now}, or null when the vent does nothing (below
	 * {@code ventMin} or locked). A perfect vent halves the lock and grants the free shots.
	 */
	public static @Nullable BlasterHeat vent(BlasterHeat h, long now, BlasterItem.Kind kind, BlasterBalance b) {
		VentResult result = ventResult(h, now, kind, b);

		if (result == VentResult.NONE) {
			return null;
		}

		int lock = BlasterBalance.of(b.ventLock, kind);
		boolean perfect = result == VentResult.PERFECT;
		return new BlasterHeat(0f, now, now + (perfect ? lock / 2 : lock), perfect ? b.perfectVentFreeShots : 0);
	}

	/**
	 * The aim spread (cone half-angle, degrees): the hip spread, times {@code steadyMultiplier} while
	 * sneaking, {@code moveMultiplier} when moving faster than walking, {@code airMultiplier} when
	 * airborne, and {@code modMultiplier} (a focused barrel).
	 */
	public static float spread(float hipSpread, boolean steadied, boolean fast, boolean airborne, float modMultiplier, BlasterBalance b) {
		float spread = hipSpread * modMultiplier;

		if (steadied) {
			spread *= b.steadyMultiplier;
		}

		if (fast) {
			spread *= b.moveMultiplier;
		}

		if (airborne) {
			spread *= b.airMultiplier;
		}

		return Math.max(0f, spread);
	}

	/** Whether a shot may leave at {@code now}, given the last shot and the rate (ticks between shots). */
	public static boolean rateReady(long lastFire, long now, int rate) {
		return now - lastFire >= rate;
	}

	/**
	 * Ticks after drawing a blaster before its first shot: the rate, less {@code quickDrawTicks} for the
	 * pistol's quick draw.
	 */
	public static int drawDelay(BlasterItem.Kind kind, int rate, BlasterBalance b) {
		return kind == BlasterItem.Kind.PISTOL ? Math.max(0, rate - b.quickDrawTicks) : rate;
	}

	/** Whether a rifle charge held since {@code chargeStart} has loaded the heavy bolt at {@code now}. */
	public static boolean chargeLoaded(long chargeStart, long now, BlasterBalance b) {
		return chargeStart >= 0 && now - chargeStart >= b.chargedTicks;
	}

	/** Whether a repeater whose spin began at {@code spinStart} is up to speed at {@code now}. */
	public static boolean spunUp(long spinStart, long now, BlasterBalance b) {
		return spinStart >= 0 && now - spinStart >= b.spinUp;
	}

	/**
	 * Suppression: the hits on one mob, oldest first, within the window ending at {@code now}. Returns
	 * whether this many recent hits suppress it.
	 */
	public static boolean suppressed(long[] hitTicks, long now, BlasterBalance b) {
		int recent = 0;

		for (long tick : hitTicks) {
			if (tick <= now && now - tick < b.suppressWindow) {
				recent++;
			}
		}

		return recent >= b.suppressHits;
	}

	/** Bolt damage of a standard shot: the kind's damage times the weapon quality. */
	public static float boltDamage(BlasterItem.Kind kind, float qualityMultiplier, BlasterBalance b) {
		return BlasterBalance.of(b.damage, kind) * qualityMultiplier;
	}

	/** Bolts one blast sends out together: the scatter's pellets, one for every other kind. */
	public static int pellets(BlasterItem.Kind kind, BlasterBalance b) {
		return kind == BlasterItem.Kind.SCATTER ? Math.max(1, b.scatterPellets) : 1;
	}

	/** Bolts one pull of the trigger fires one after another: the carbine's burst, one for every other kind. */
	public static int burst(BlasterItem.Kind kind, BlasterBalance b) {
		return kind == BlasterItem.Kind.CARBINE ? Math.max(1, b.burstSize) : 1;
	}

	/**
	 * Heat one bolt adds: the kind's heat per shot, shared out between the bolts of a burst, so a
	 * carbine's whole burst adds its {@code heatPerShot}.
	 */
	public static float heatPerBolt(BlasterItem.Kind kind, BlasterBalance b) {
		return BlasterBalance.of(b.heatPerShot, kind) / burst(kind, b);
	}

	/**
	 * Damage of everything one pull of the trigger sends: all of a scatter's pellets (what lands at
	 * point blank), all of a carbine's burst, one bolt of any other kind.
	 */
	public static float pullDamage(BlasterItem.Kind kind, float qualityMultiplier, BlasterBalance b) {
		return boltDamage(kind, qualityMultiplier, b) * pellets(kind, b) * burst(kind, b);
	}

	/**
	 * Whether the next bolt of a burst is due at {@code now}: bolts are {@code burstGap} ticks apart,
	 * counted from the burst's first bolt at {@code burstStart}; {@code fired} bolts have left.
	 */
	public static boolean burstBoltDue(long burstStart, int fired, long now, BlasterBalance b) {
		return now - burstStart >= (long) fired * Math.max(1, b.burstGap);
	}

	private BlasterRules() {
	}
}
