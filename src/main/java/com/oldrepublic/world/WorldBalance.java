package com.oldrepublic.world;

/** Balance section {@code world}: crystal caves, the unstable cluster hazard and the Trial of Reflection. */
public final class WorldBalance {
	public static WorldBalance get() {
		return com.oldrepublic.config.BalanceConfig.section("world", WorldBalance.class, WorldBalance::new);
	}

	/**
	 * Search radius passed to {@code ServerLevel#findNearestMapStructure}. For the random-spread
	 * placement this counts placement cells (28 chunks each), so 6 reaches roughly 2,700 blocks.
	 */
	public int caveSearchRadius = 6;
	/** A cached search result is reused while the player stays within this many blocks of where it was made. */
	public int caveCacheMoveBlocks = 48;
	/** A cached search result is refreshed after this many seconds. */
	public int caveCacheSeconds = 30;
	/** The heart (trial chamber) position is refined once the cave start is within this many blocks. */
	public int caveHeartRefineBlocks = 192;

	public double unstableRadius = 2.5;
	/** How long a player must stay within the radius before the cluster starts charging. */
	public int unstableLingerTicks = 10;
	/** Length of the tell (charge) before the discharge. */
	public int unstableChargeTicks = 24;
	public float unstableDamage = 4f;
	public int unstableCooldownTicks = 60;

	/** How long an opened resonant seal stays open (it waits while someone stands in the doorway). */
	public int sealOpenTicks = 200;

	/** Horizontal distance from the meditation stone that still counts as inside the chamber. */
	public double chamberRadius = 9.5;
	/** Vertical distance from the meditation stone that still counts as inside the chamber. */
	public double chamberHeight = 6;
	/** Chamber dims and the echo gathers for this long before it attacks. */
	public int trialFormTicks = 40;
	/** A trial that lasts longer than this fails (the echo fades). */
	public int trialMaxTicks = 6000;
	/** Delay between the echo's defeat and the crystal forming on the stone. */
	public int crystalFormDelayTicks = 30;

	public double echoHealth = 32;
	public double echoDamage = 4;
	public double echoSpeed = 0.31;
	public double echoArmor = 2;
	public int echoLungeCooldownTicks = 100;
	public int echoLungeWindupTicks = 14;
	public float echoLungeDamage = 5f;
	public double echoLungeSpeed = 0.9;
	public int echoPushCooldownTicks = 160;
	public int echoPushWindupTicks = 16;
	/** Release reaches this far, in a cone of {@link #echoPushConeDegrees} (full angle). */
	public double echoPushRange = 6;
	public double echoPushConeDegrees = 50;
	public double echoPushStrength = 1.2;
	public float echoPushDamage = 2f;
}
