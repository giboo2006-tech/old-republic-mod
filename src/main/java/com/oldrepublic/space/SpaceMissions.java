package com.oldrepublic.space;

import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.planet.Planets;

import net.minecraft.server.level.ServerPlayer;

/**
 * The runtime of the space objectives {@code space_run} and {@code space_clear} (spec MILESTONE_5.md
 * 5.8, 5.9): W4's mission service starts each here, and W2 reports its end through
 * {@code QuestHooks.onSpaceObjectiveDone}. Owned by W2 space. Phase 0 stub: every start is refused
 * until W2 fills it; W2 keeps these signatures and may add methods.
 */
public final class SpaceMissions {
	/**
	 * Starts mission {@code missionId}'s space objective of kind {@code kind} for {@code p} in the
	 * orbit of {@code system}, with a named ace when {@code ace} (a space bounty, 5.8). Returns false
	 * when it is refused now.
	 */
	public static boolean start(ServerPlayer p, String missionId, MissionDefinition.Objective kind, Planets.Planet system, boolean ace) {
		// Phase 0 stub: W2 fills it.
		return false;
	}

	/** Ends mission {@code missionId}'s space objective for {@code p} without reporting it (the mission was abandoned or failed). */
	public static void cancel(ServerPlayer p, String missionId) {
		// Phase 0 stub: W2 fills it.
	}

	private SpaceMissions() {
	}
}
