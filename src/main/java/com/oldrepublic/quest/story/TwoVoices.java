package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestStage;

import net.minecraft.server.level.ServerPlayer;

/**
 * The Two Voices dream that opens Chapter Two. Chapter One's completion starts the wait; a day later
 * (or on waking from a real sleep) the dream plays at a safe moment in the overworld. When it ends,
 * or the dreamer is hurt, the Crossing's meeting point is chosen and the voices start to pull.
 *
 * <p>The "due" mark is saved, the dream timer is not: a player who leaves mid-dream sees it again.
 */
final class TwoVoices {
	/** Remaining dream ticks per dreaming player. */
	private static final Map<UUID, Integer> DREAMING = new HashMap<>();
	/** {@code tickCount} at which each sleeping player lay down. */
	private static final Map<UUID, Integer> SLEEP_START = new HashMap<>();
	private static final int CHECK_INTERVAL = 20;

	static void tick(ServerPlayer player, StoryData data) {
		Integer left = DREAMING.get(player.getUUID());

		if (left != null) {
			if (left <= 1) {
				DREAMING.remove(player.getUUID());
				finish(player);
			} else {
				DREAMING.put(player.getUUID(), left - 1);
			}

			return;
		}

		if (player.tickCount % CHECK_INTERVAL != 0) {
			return;
		}

		if (data.crossing() == CrossingStage.NOT_STARTED && Progression.get(player).questStage >= QuestStage.COMPLETE.ordinal()) {
			StoryRules.advanceCrossing(data, CrossingStage.WAITING, CrossingStage.NOT_STARTED);
			data.waitSince = StoryService.now(player);
			Story.commit(player, data);
			return;
		}

		if (data.crossing() != CrossingStage.WAITING) {
			return;
		}

		StoryBalance b = StoryService.balance();
		boolean due = data.hasFlag(StoryRules.VOICES_DUE) || StoryService.now(player) - data.waitSince >= b.crossingDelayTicks;

		if (due && StoryService.inOverworld(player) && isSafe(player)) {
			start(player);
		}
	}

	/** Plays the dream now (also used by {@code /oldrepublic story dream}). */
	static void start(ServerPlayer player) {
		int ticks = StoryService.balance().dreamTicks;
		DREAMING.put(player.getUUID(), ticks);
		EffectEvents.toPlayer(player, QuestEffects.AWAKENING_DREAM, player.position(), null, player.getId(), ticks, QuestEffects.DREAM_TWO_VOICES);
	}

	/** Same rule as the Awakening: not hurt by a mob lately, on the ground, not in lava, not asleep. */
	private static boolean isSafe(ServerPlayer player) {
		int lastHurt = player.getLastHurtByMobTimestamp();
		boolean recentlyHurt = lastHurt > 0 && player.tickCount - lastHurt < StoryService.balance().crossingSafeTicks;
		boolean grounded = player.onGround() || player.isPassenger();
		return !recentlyHurt && grounded && !player.isInLava() && !player.isSleeping();
	}

	/** The dream ended (or was cut short): the voices begin. */
	private static void finish(ServerPlayer player) {
		StoryData data = Story.get(player);

		if (!StoryRules.advanceCrossing(data, CrossingStage.VOICES, CrossingStage.WAITING)) {
			return;
		}

		data.flags.remove(StoryRules.VOICES_DUE);
		Story.commit(player, data);
		Journal.unlockEntry(player, JournalEntries.TWO_VOICES);
		Crossing.chooseMeeting(player, data);
	}

	/** Taking damage wakes the dreamer: the overlay is cancelled and the stage still advances. */
	static void onDamaged(ServerPlayer player) {
		if (DREAMING.remove(player.getUUID()) != null) {
			EffectEvents.toPlayer(player, QuestEffects.AWAKENING_DREAM, player.position(), null, player.getId(), 0, QuestEffects.DREAM_CANCEL);

			if (player.isAlive()) {
				finish(player);
			}
		}
	}

	static void onStartSleeping(ServerPlayer player) {
		SLEEP_START.put(player.getUUID(), player.tickCount);
	}

	/** Waking from a long enough sleep while waiting makes the dream due at the next safe moment. */
	static void onStopSleeping(ServerPlayer player) {
		Integer start = SLEEP_START.remove(player.getUUID());

		if (start == null || player.tickCount - start < StoryService.balance().crossingMinSleepTicks) {
			return;
		}

		StoryData data = Story.get(player);

		if (data.crossing() == CrossingStage.WAITING && data.flags.add(StoryRules.VOICES_DUE)) {
			Story.commit(player, data);
		}
	}

	static void forget(ServerPlayer player) {
		DREAMING.remove(player.getUUID());
		SLEEP_START.remove(player.getUUID());
	}

	static void clear() {
		DREAMING.clear();
		SLEEP_START.clear();
	}

	private TwoVoices() {
	}
}
