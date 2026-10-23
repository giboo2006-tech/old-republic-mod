package com.oldrepublic.vendor;

/**
 * Balance section {@code vendors} (spec MILESTONE_4.md 14): prices, the sell rate, enemy credit
 * drops, contract credits and the Milestone 3 back pay. Server side; read it through
 * {@link VendorFeature#balance()}. Pure rules take an instance, so tests use the defaults
 * ({@code new VendorBalance()}).
 */
public final class VendorBalance {
	/** Every vendor price is multiplied by this. */
	public double priceMultiplier = 1.0;
	/** Prices at Honoured with the vendor's faction (the highest tier, M3 14.1). */
	public double honouredDiscount = 0.9;
	/** A vendor buys an item back at this share of its buy price. */
	public double sellRate = 0.2;
	/** The ship's hold pays when its body is this close (informational: {@code Credits.HOLD_RANGE} is the rule). */
	public int holdPaymentRange = 32;

	/** Troopers, Mandalorians and droids drop credit chits at this chance, never natural patrols (7.7). */
	public double creditDropChance = 0.5;
	/** How many chits a drop holds: [min, max]. */
	public int[] creditDrop = {1, 6};

	/** Planet and outpost contracts pay {@code base + perLevel x level} credits at full pay. */
	public int contractCreditsBase = 25;
	public int contractCreditsPerLevel = 5;

	/** The one-time Milestone 3 back pay: per milestone held, and its cap (8.8). */
	public int backPayPerMilestone = 100;
	public int backPayMax = 300;

	/** The most of one line a single purchase may buy. */
	public int maxPurchase = 64;
	/** How far from the vendor a purchase is still accepted (blocks). */
	public double reach = 8;
	/** The hub spar (8.7): the apprentice or acolyte yields at this share of its health; its blows are scaled by this. */
	public double hubSparYield = 0.4;
	public double hubSparDamage = 0.5;
}
