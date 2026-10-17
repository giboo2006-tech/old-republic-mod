package com.oldrepublic.force.sense;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pure rules of Force Sense, shared by the server, the client (Danger Sense) and the tests. */
public final class SenseRules {
	/** Whether a fresh holocron result still earns the follow-up pulse: at most {@code followUpTicks} after the cast. */
	public static boolean followUpDue(long castTick, long now, int followUpTicks) {
		return now >= castTick && now - castTick <= followUpTicks;
	}

	/**
	 * Whether a tell at offset {@code toTell} from the player is inside the front cone of full angle
	 * {@code coneDegrees} around {@code look}: then the player sees it and Danger Sense stays quiet.
	 * Measured on the horizontal plane; a tell straight above or below counts as in front.
	 */
	public static boolean inDangerCone(Vec3 look, Vec3 toTell, float coneDegrees) {
		Vec3 flatLook = new Vec3(look.x, 0, look.z);
		Vec3 flatTell = new Vec3(toTell.x, 0, toTell.z);

		if (flatLook.lengthSqr() < 1.0e-6 || flatTell.lengthSqr() < 1.0e-6) {
			return true;
		}

		double cos = flatLook.normalize().dot(flatTell.normalize());
		return cos >= Math.cos(Math.toRadians(Mth.clamp(coneDegrees, 0f, 360f) / 2.0));
	}

	/** Closeness 0..1 of a site {@code distance} blocks away for a search of {@code radius} blocks. */
	public static float closeness(double distance, int radius) {
		return radius <= 0 ? 0f : Mth.clamp((float) (1.0 - distance / radius), 0f, 1f);
	}

	/** Unit horizontal direction from {@code from} to {@code to}, or zero when they are on the same column. */
	public static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
		Vec3 flat = new Vec3(to.x - from.x, 0, to.z - from.z);
		return flat.lengthSqr() < 1.0e-6 ? Vec3.ZERO : flat.normalize();
	}

	/** Energy cost of a cast at mastery rank {@code rank}, before the cost multipliers (never below 0). */
	public static float cost(SenseBalance b, int rank) {
		return Math.max(0f, b.cost - rank * b.masteryCostPerRank);
	}

	/** Creature range at mastery rank {@code rank}. */
	public static int creatureRange(SenseBalance b, int rank) {
		return b.creatureRange + rank * b.masteryRangePerRank;
	}

	/** Cooldown ticks with {@code archivists_road} cutting {@code roadCut} (a fraction), at least 1. */
	public static int cooldown(int baseTicks, boolean archivistsRoad, float roadCut) {
		return Math.max(1, Math.round(baseTicks * (archivistsRoad ? 1f - roadCut : 1f)));
	}

	private SenseRules() {
	}
}
