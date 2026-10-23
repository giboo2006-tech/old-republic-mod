package com.oldrepublic.boss.drops;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.oldrepublic.saber.HiltParts;

/**
 * The pure rules of Milestone 7's drops (spec MILESTONE_7.md 7.3, 7.4), apart from the players and the world so the
 * tests can check them alone: the pity, which set parts a player still lacks, and how many of a material a drop holds.
 */
public final class BossDropRules {
	/**
	 * Whether a drop comes this time: {@code draw} (0 to 1) falls under {@code chance}, or this is the {@code pity}-th
	 * occasion in a row without one ({@code misses} came before it). The Collector's emitter rule
	 * ({@code ChapterThreeRules.emitterDue}): with a pity of 4 the fourth clear without one is sure. A pity of 0 or less
	 * never forces it.
	 */
	public static boolean due(int misses, float draw, float chance, int pity) {
		return draw < chance || pity > 0 && misses + 1 >= pity;
	}

	/**
	 * The parts of {@code parts} the drops have not given yet, in the order given ({@code given} holds item ids,
	 * {@code grip_watchman}). What the drops gave is the record, not what the player carries: a part stored in a chest,
	 * worn on a saber or traded away still counts as had.
	 */
	public static List<HiltParts.Part> lacking(Set<String> given, List<? extends HiltParts.Part> parts) {
		List<HiltParts.Part> out = new ArrayList<>();

		for (HiltParts.Part part : parts) {
			if (!given.contains(part.itemId())) {
				out.add(part);
			}
		}

		return out;
	}

	/** How many items a drop of {@code range} ({min, max}) holds for a roll in [0, 1): uniformly within it, never below 0. */
	public static int count(double roll, int[] range) {
		int min = range.length > 0 ? Math.max(0, range[0]) : 1;
		int max = range.length > 1 ? Math.max(min, range[1]) : min;
		return min + (int) Math.floor(Math.max(0, Math.min(0.999999, roll)) * (max - min + 1));
	}

	private BossDropRules() {
	}
}
