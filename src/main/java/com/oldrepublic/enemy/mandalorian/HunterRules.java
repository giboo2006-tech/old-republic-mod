package com.oldrepublic.enemy.mandalorian;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The hunters' pure rules (spec MILESTONE_4.md 8.5, 8.7, 10.1), kept apart so game tests can check
 * them without a fight.
 */
public final class HunterRules {
	/**
	 * Which story ambush a spawn is: the escorted pair is ambush two, any other group of two or more is
	 * ambush one, and a lone hunter without an escort is a bounty ({@code AMBUSH_NONE}).
	 */
	public static int ambushOf(int count, boolean withDroid) {
		if (withDroid) {
			return MandalorianHunter.AMBUSH_TWO;
		}

		return count >= 2 ? MandalorianHunter.AMBUSH_ONE : MandalorianHunter.AMBUSH_NONE;
	}

	/**
	 * Whether a dead hunter drops its loot: never one Kaast called into his fight; an owned hunter only
	 * when its owner struck it last or fought beside the killer (within 32 blocks), so nobody farms
	 * another player's bounty; an unowned one always.
	 */
	public static boolean dropsLoot(boolean fightSpawn, @Nullable UUID owner, @Nullable UUID lastHurtBy, boolean ownerNear) {
		if (fightSpawn) {
			return false;
		}

		return owner == null || owner.equals(lastHurtBy) || ownerNear;
	}

	/** The rifle's lead: the larger one against a Force Speed runner. */
	public static float lead(HunterBalance b, boolean targetRunning) {
		return targetRunning ? b.speedLead : b.lead;
	}

	/** Whether the jet hop is due: its interval is up, or a player closed in, and the last hop is not too recent. */
	public static boolean hopDue(HunterBalance b, int sinceHop, double targetDistance) {
		return sinceHop >= b.jetMinGapTicks && (sinceHop >= b.jetIntervalTicks || targetDistance < b.jetCloseRange);
	}

	/** Whether the wrist rocket is due: its interval is up and the target stands in its band. */
	public static boolean rocketDue(HunterBalance b, int sinceRocket, double targetDistance) {
		return sinceRocket >= b.rocketIntervalTicks && targetDistance >= b.rocketMinRange && targetDistance <= b.rocketMaxRange;
	}

	/** Whether the wrist flamer is due: the target is close and the flamer has cooled. */
	public static boolean flamerDue(HunterBalance b, int sinceFlame, double targetDistance) {
		return sinceFlame >= b.flamerCooldownTicks && targetDistance < b.flamerRange;
	}

	private HunterRules() {
	}
}
