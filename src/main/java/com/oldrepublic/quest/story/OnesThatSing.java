package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestEffects;

import net.minecraft.server.level.ServerPlayer;

/**
 * "The Ones That Sing", the dream that opens Chapter Three (spec 6.1). Chapter Two's end starts the
 * wait (old saves too); a day later, or on waking from a real sleep, the dream plays at a safe moment
 * in the overworld. When it ends, or the dreamer is hurt, the chapter moves to SUMMONS. The wait
 * counts in any dimension; only the dream waits for the overworld.
 *
 * <p>The "due" mark is saved, the dream timer is not: a player who leaves mid-dream sees it again.
 */
final class OnesThatSing {
	/** Remaining dream ticks per dreaming player. */
	private static final Map<UUID, Integer> DREAMING = new HashMap<>();
	/** {@code tickCount} at which each sleeping player lay down. */
	private static final Map<UUID, Integer> SLEEP_START = new HashMap<>();
	private static final int CHECK_INTERVAL = 20;

	static void tick(ServerPlayer player) {
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

		if (ChapterThreeService.checkWaiting(player)) {
			return;
		}

		StoryData data = Story.get(player);

		if (ChapterThreeRules.dreamDue(data, StoryService.now(player), ChapterThreeService.balance().waitTicks)
				&& StoryService.inOverworld(player) && isSafe(player)) {
			start(player);
		}
	}

	/** Plays the dream now; the chapter moves to SUMMONS when it ends. */
	static void start(ServerPlayer player) {
		int ticks = Math.max(1, ChapterThreeService.balance().dreamTicks);
		DREAMING.put(player.getUUID(), ticks);
		EffectEvents.toPlayer(player, QuestEffects.AWAKENING_DREAM, player.position(), null, player.getId(), ticks, QuestEffects.DREAM_ONES_THAT_SING);
	}

	/** The M2 safe moment: not hurt by a mob lately, on the ground, not in lava, not asleep. */
	private static boolean isSafe(ServerPlayer player) {
		int lastHurt = player.getLastHurtByMobTimestamp();
		boolean recentlyHurt = lastHurt > 0 && player.tickCount - lastHurt < StoryService.balance().crossingSafeTicks;
		boolean grounded = player.onGround() || player.isPassenger();
		return !recentlyHurt && grounded && !player.isInLava() && !player.isSleeping();
	}

	/** The dream ended (or was cut short): the summons. */
	private static void finish(ServerPlayer player) {
		StoryData data = Story.get(player);

		if (data.chapter3.stage() != ChapterThreeStage.WAITING) {
			return;
		}

		data.flags.remove(ChapterThreeRules.DREAM_DUE);
		Story.commit(player, data);
		ChapterThreeService.enterStage(player, ChapterThreeStage.SUMMONS);
	}

	/**
	 * Taking damage wakes the dreamer: the overlay and its bed are cancelled and the stage still
	 * advances (6.1), even on a lethal hit, so the dream never replays.
	 */
	static void onDamaged(ServerPlayer player) {
		if (DREAMING.remove(player.getUUID()) != null) {
			EffectEvents.toPlayer(player, QuestEffects.AWAKENING_DREAM, player.position(), null, player.getId(), 0, QuestEffects.DREAM_CANCEL);
			finish(player);
		}
	}

	static void onStartSleeping(ServerPlayer player) {
		SLEEP_START.put(player.getUUID(), player.tickCount);
	}

	/** Waking from a long enough sleep while waiting makes the dream due at the next safe moment. */
	static void onStopSleeping(ServerPlayer player) {
		Integer start = SLEEP_START.remove(player.getUUID());

		if (start == null || player.tickCount - start < ChapterThreeService.balance().minSleepTicks) {
			return;
		}

		StoryData data = Story.get(player);

		if (data.chapter3.stage() == ChapterThreeStage.WAITING && data.flags.add(ChapterThreeRules.DREAM_DUE)) {
			Story.commit(player, data);
		}
	}

	/** Whether the player is dreaming now (the dream's other effects wait for it). */
	static boolean dreaming(ServerPlayer player) {
		return DREAMING.containsKey(player.getUUID());
	}

	static void forget(ServerPlayer player) {
		DREAMING.remove(player.getUUID());
		SLEEP_START.remove(player.getUUID());
	}

	static void clear() {
		DREAMING.clear();
		SLEEP_START.clear();
	}

	private OnesThatSing() {
	}
}
