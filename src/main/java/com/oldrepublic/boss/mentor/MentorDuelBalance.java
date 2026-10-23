package com.oldrepublic.boss.mentor;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code mentor_duel} of {@code config/oldrepublic/balance.json} (spec MILESTONE_5.md
 * 12.1, 16.4): the mentor at full strength in the Fall and the Return (profile {@code mentor_true}).
 * Keys marked "addition" hold numbers the spec states in prose or leaves open.
 */
public final class MentorDuelBalance {
	public float damage = 9f;
	public float guard = 120f;
	public float parry = 0.35f;
	public int[] strings = {3, 5};
	public float health = 300f;
	/** The mentor stays within this many blocks of where the duel began. */
	public float leash = 24f;
	public float yieldAt = 0.10f;
	/** The duelling player dead, or away from the duel's ground, this long: the duel resets. */
	public int leaveTicks = 200;
	/** Addition: the profile's tell timings (the M2 duellist's shape). */
	public int windupTicks = 12;
	public int gapTicks = 7;
	public int recoveryTicks = 22;
	/** Addition: how often the mentor uses a power (the first after {@code firstPowerTicks}), and the powers' numbers. */
	public int powerInterval = 160;
	public int firstPowerTicks = 100;
	/** Saelen's Push: a {@code pushTellTicks} tell, then a throw within {@code pushRange}. */
	public float pushRange = 6f;
	public float pushDamage = 4f;
	public float pushStrength = 1.3f;
	public int pushTellTicks = 12;
	/** Saelen's Barrier: for {@code barrierTicks} the duelling player's blows are turned. */
	public int barrierTicks = 60;
	public float barrierRadius = 1.8f;
	/** Sorn's Lightning: a {@code lightningChargeTicks} charge (the tell), then the channel. */
	public int lightningChargeTicks = 10;
	public int lightningChannel = 30;
	public float lightningDamage = 2.5f;
	public int lightningInterval = 10;
	public float lightningRange = 10f;
	/** Sorn's Choke: a {@code chokeTellTicks} tell, then the M3 hold within {@code chokeRange}, {@code chokeDamage} a second. */
	public int chokeTicks = 40;
	public float chokeDamage = 1f;
	public int chokeTellTicks = 15;
	public float chokeRange = 8f;

	public static MentorDuelBalance get() {
		return BalanceConfig.section("mentor_duel", MentorDuelBalance.class, MentorDuelBalance::new);
	}
}
