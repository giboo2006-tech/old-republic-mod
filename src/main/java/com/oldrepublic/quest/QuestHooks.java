package com.oldrepublic.quest;

import net.minecraft.server.level.ServerPlayer;

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

	private QuestHooks() {
	}
}
