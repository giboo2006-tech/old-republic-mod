package com.oldrepublic.quest.turning;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.planet.Planets;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * What the Milestone 5 quest hooks mean for the Fall, the Return and rank 4 (spec MILESTONE_5.md 8).
 * Owned by W5 paths and ranks. Phase 0 stub: {@code QuestHooks} forwards every Milestone 5 hook here,
 * so the bodies are empty until W5 fills them; W5 keeps these signatures and may add methods.
 */
public final class TurningService {
	/** W1: the owner flew their ship by hand. */
	public static void onHelmTaken(ServerPlayer owner) {
		// Phase 0 stub: W5 fills it.
	}

	/** W1: the owner's ship entered {@code system}'s orbit; {@code byJump} for a hyperspace jump. */
	public static void onOrbitEntered(ServerPlayer owner, Planets.Planet system, boolean byJump) {
		// Phase 0 stub: W5 fills it.
	}

	/** W2: the owner's blockade over Tyrvann is broken. */
	public static void onBlockadeBroken(ServerPlayer owner) {
		// Phase 0 stub: W5 fills it.
	}

	/** W2: a {@code space_run} or {@code space_clear} objective of mission {@code missionId} ended. */
	public static void onSpaceObjectiveDone(ServerPlayer p, String missionId, boolean success) {
		// Phase 0 stub: W5 fills it.
	}

	/** W2: the player shot down a starfighter; {@code encounterOwner} is the encounter's owner, if any. */
	public static void onShipKill(ServerPlayer shooter, Entity fighter, @Nullable UUID encounterOwner) {
		// Phase 0 stub: W5 fills it.
	}

	/** W3: the owner silenced verse stone {@code index}. */
	public static void onVerseSilenced(ServerPlayer owner, int index) {
		// Phase 0 stub: W5 fills it.
	}

	/** W8: the player took part in a clear of the Long Hymn; {@code firstClear} for their first (8.5's deeds). */
	public static void onRaidCleared(ServerPlayer p, boolean firstClear) {
		// Phase 0 stub: W5 fills it.
	}

	/**
	 * W1: the player touched down on {@code planet}; {@code first} marks their first landing there
	 * (the Return's prisoners are put down at a Greenwatch pad bay, 8.3).
	 */
	public static void onPlanetArrived(ServerPlayer p, Planets.Planet planet, boolean first) {
		// Phase 0 stub: W5 fills it.
	}

	/** W8: the player killed one of the five Milestone 5 foes. */
	public static void onFoeKilled(ServerPlayer killer, LivingEntity victim, String kind, @Nullable Faction faction, boolean wasHostile) {
		// Phase 0 stub: W5 fills it.
	}

	private TurningService() {
	}
}
