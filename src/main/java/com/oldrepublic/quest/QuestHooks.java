package com.oldrepublic.quest;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.CaptiveOutcome;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.YieldOutcome;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The single place where gameplay systems tell the introductory quest that something happened.
 * Every feature calls these; the quest feature decides what they mean for the current stage.
 * Signatures are a contract between features: add new hooks, do not change existing ones.
 */
public final class QuestHooks {
	/** The Awakening should begin now. {@code reason} is {@code time}, {@code sleep} or {@code command}. */
	public static void onAwakeningDue(ServerPlayer player, String reason) {
		QuestService.onAwakeningDue(player, reason);
	}

	public static void onLevelUp(ServerPlayer player, int level) {
		QuestService.onLevelUp(player, level);
	}

	/**
	 * An ability was used successfully. {@code hostileHits} counts distinct hostile living
	 * targets it affected.
	 */
	public static void onAbilityUsed(ServerPlayer player, String abilityId, int hostileHits) {
		QuestService.onAbilityUsed(player, abilityId, hostileHits);
	}

	/** The player entered the bounds of a crystal cave structure for the first time this visit. */
	public static void onCaveEntered(ServerPlayer player) {
		QuestService.onCaveEntered(player);
	}

	public static void onTrialStarted(ServerPlayer player) {
		QuestService.onTrialStarted(player);
	}

	public static void onTrialCompleted(ServerPlayer player) {
		QuestService.onTrialCompleted(player);
	}

	public static void onTrialFailed(ServerPlayer player) {
		QuestService.onTrialFailed(player);
	}

	/** The player picked up their attuned kyber crystal. */
	public static void onCrystalObtained(ServerPlayer player) {
		QuestService.onCrystalObtained(player);
	}

	/** A Crystal Poacher was killed by this player. */
	public static void onPoacherKilled(ServerPlayer player) {
		QuestService.onPoacherKilled(player);
	}

	/** The player constructed a lightsaber at the workbench. */
	public static void onSaberConstructed(ServerPlayer player) {
		QuestService.onSaberConstructed(player);
	}

	/** A blaster bolt was deflected ({@code parried} = returned to the shooter). */
	public static void onBoltDeflected(ServerPlayer player, boolean parried) {
		QuestService.onBoltDeflected(player, parried);
	}

	/**
	 * An ability affected a target. {@code target} is null for a batch of item entities (Force Pull
	 * on loose items).
	 */
	public static void onAbilityHit(ServerPlayer p, String abilityId, @Nullable Entity target) {
		QuestService.onAbilityHit(p, abilityId, target);
	}

	/**
	 * A saber technique was performed. {@code technique} is {@code heavy}, {@code lunge},
	 * {@code finisher} or {@code parry}; {@code hit} says whether it connected.
	 */
	public static void onSaberTechnique(ServerPlayer p, String technique, @Nullable LivingEntity target, boolean hit) {
		QuestService.onSaberTechnique(p, technique, target, hit);
	}

	/** The player decided the fate of an entity that yielded. */
	public static void onYieldResolved(ServerPlayer p, LivingEntity yielded, YieldOutcome outcome) {
		QuestService.onYieldResolved(p, yielded, outcome);
	}

	/** The player resolved a group of captives. */
	public static void onCaptivesResolved(ServerPlayer p, String groupId, CaptiveOutcome outcome) {
		QuestService.onCaptivesResolved(p, groupId, outcome);
	}

	/** A skill node was bought or granted. */
	public static void onNodeLearned(ServerPlayer p, String nodeId) {
		QuestService.onNodeLearned(p, nodeId);
	}

	/** The player's path changed (after it is stored). */
	public static void onPathChanged(ServerPlayer p, Path path) {
		QuestService.onPathChanged(p, path);
	}

	private QuestHooks() {
	}
}
