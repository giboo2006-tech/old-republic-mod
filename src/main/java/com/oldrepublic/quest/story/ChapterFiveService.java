package com.oldrepublic.quest.story;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.BossOutcome;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * What the Milestone 5 quest hooks mean for Chapter Five, "The Long Hymn", and the two new factions
 * (spec MILESTONE_5.md 6 and 7). Owned by W4 story and factions. Phase 0 stub: {@code QuestHooks}
 * forwards every Milestone 5 hook here and {@code QuestService} sends Darth Ulvaine's boss hooks
 * here (12.6), so the bodies are empty until W4 fills them; W4 keeps these signatures and may add
 * methods.
 */
public final class ChapterFiveService {
	/** W1: the owner flew their ship by hand (6.2, "Take the helm"). */
	public static void onHelmTaken(ServerPlayer owner) {
		// Phase 0 stub: W4 fills it.
	}

	/** W1: the owner's ship entered {@code system}'s orbit; {@code byJump} for a hyperspace jump (6.2, 6.5). */
	public static void onOrbitEntered(ServerPlayer owner, Planets.Planet system, boolean byJump) {
		// Phase 0 stub: W4 fills it.
	}

	/** W2: the owner's blockade over Tyrvann is broken (6.5). */
	public static void onBlockadeBroken(ServerPlayer owner) {
		// Phase 0 stub: W4 fills it.
	}

	/** W2: a {@code space_run} or {@code space_clear} objective of mission {@code missionId} ended (5.9). */
	public static void onSpaceObjectiveDone(ServerPlayer p, String missionId, boolean success) {
		// Phase 0 stub: W4 fills it.
	}

	/** W2: the player shot down a starfighter; {@code encounterOwner} is the encounter's owner, if any (5.6, 6.10). */
	public static void onShipKill(ServerPlayer shooter, Entity fighter, @Nullable UUID encounterOwner) {
		// Phase 0 stub: W4 fills it.
	}

	/** W3: the owner silenced verse stone {@code index} (6.6). */
	public static void onVerseSilenced(ServerPlayer owner, int index) {
		// Phase 0 stub: W4 fills it.
	}

	/** W8: the player took part in a clear of the Long Hymn; {@code firstClear} for their first (12.3). */
	public static void onRaidCleared(ServerPlayer p, boolean firstClear) {
		// Phase 0 stub: W4 fills it.
	}

	/** W1: the player touched down on {@code planet}; {@code first} marks their first landing there (6.1). */
	public static void onPlanetArrived(ServerPlayer p, Planets.Planet planet, boolean first) {
		// Phase 0 stub: W4 fills it.
	}

	/** W8: the player killed one of the five Milestone 5 foes (7, 6.10). */
	public static void onFoeKilled(ServerPlayer killer, LivingEntity victim, String kind, @Nullable Faction faction, boolean wasHostile) {
		// Phase 0 stub: W4 fills it.
	}

	/** Darth Ulvaine's fight entered {@code phase} (12.6). */
	public static void onBossPhase(ServerPlayer player, String bossId, int phase) {
		// Phase 0 stub: W4 fills it.
	}

	/** Darth Ulvaine yielded and waits for the owner's choice (6.7, 12.6). */
	public static void onBossYielded(ServerPlayer owner, String bossId) {
		// Phase 0 stub: W4 fills it.
	}

	/** Darth Ulvaine's fight ended with {@code outcome}; called once per participant (6.7, 6.9, 12.6). */
	public static void onBossDefeated(ServerPlayer player, String bossId, BossOutcome outcome, UUID owner, boolean firstClear) {
		// Phase 0 stub: W4 fills it.
	}

	private ChapterFiveService() {
	}
}
