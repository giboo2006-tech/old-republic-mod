package com.oldrepublic.crafting;

/**
 * The pure crafting rules (spec MILESTONE_4.md 7.2, 7.4, 7.5), free of players and worlds so the
 * game tests can check them with fixed numbers.
 */
public final class CraftingRules {
	/** The discipline level (1 to 5) reached with {@code xp} total discipline XP. */
	public static int levelFor(int xp, int[] levelXp) {
		int level = 1;

		for (int i = 1; i < levelXp.length && i < 5; i++) {
			if (xp >= levelXp[i]) {
				level = i + 1;
			}
		}

		return level;
	}

	/** Discipline XP still needed for the next level, or 0 at level 5. */
	public static int xpToNext(int xp, int[] levelXp) {
		int level = levelFor(xp, levelXp);
		return level >= 5 || level >= levelXp.length ? 0 : Math.max(0, levelXp[level] - xp);
	}

	/** Fine chance for a crafter {@code disciplineLevel} making a level {@code schematicLevel} schematic. */
	public static float fineChance(int disciplineLevel, int schematicLevel, CraftingBalance b) {
		return b.fineBase + b.finePerLevel * Math.max(0, disciplineLevel - schematicLevel);
	}

	/** Masterwork chance, as {@link #fineChance}. */
	public static float masterworkChance(int disciplineLevel, int schematicLevel, CraftingBalance b) {
		return b.masterworkBase + b.masterworkPerLevel * Math.max(0, disciplineLevel - schematicLevel);
	}

	/**
	 * The quality for one uniform draw {@code r} in [0, 1): Masterwork below {@code masterwork},
	 * Fine below {@code masterwork + fine}, Standard otherwise.
	 */
	public static Quality pick(float fine, float masterwork, float r) {
		if (r < masterwork) {
			return Quality.MASTERWORK;
		}

		return r < masterwork + fine ? Quality.FINE : Quality.STANDARD;
	}

	/** Whether a crafter with this pity count gets a guaranteed Masterwork next. */
	public static boolean pityDue(int pity, CraftingBalance b) {
		return b.pityCrafts > 0 && pity >= b.pityCrafts;
	}

	/** The pity count after a quality craft of quality {@code q}. */
	public static int pityAfter(int pity, Quality q) {
		return q == Quality.MASTERWORK ? 0 : pity + 1;
	}

	/** Quality crafts left until a guaranteed Masterwork (the Craft tab's line); 1 means the next. */
	public static int craftsToGuarantee(int pity, CraftingBalance b) {
		return Math.max(1, b.pityCrafts + 1 - pity);
	}

	/** Discipline XP for one craft: the schematic's, a quarter when it is two or more levels below the crafter. */
	public static int craftXp(int schematicXp, int disciplineLevel, int schematicLevel, CraftingBalance b) {
		if (disciplineLevel - schematicLevel >= 2) {
			return Math.round(schematicXp * b.lowLevelXpMultiplier);
		}

		return schematicXp;
	}

	/** Player XP for the first craft of a schematic of this level. */
	public static int firstCraftXp(int schematicLevel, CraftingBalance b) {
		return b.firstCraftXpPerLevel * Math.max(1, schematicLevel);
	}

	/** Whether the second discipline is open: level 2 in the first, or player level 13. */
	public static boolean secondOpen(int firstDisciplineLevel, int playerLevel, CraftingBalance b) {
		return firstDisciplineLevel >= b.secondDisciplineLevel || playerLevel >= b.secondDisciplinePlayerLevel;
	}

	/** How many of an input of {@code count} salvage returns; the main input returns at least one. */
	public static int salvageCount(int count, boolean mainInput, float ratio) {
		int back = (int) Math.floor(count * ratio);
		return mainInput ? Math.max(1, back) : back;
	}

	/** The commission fee for a schematic of this level. */
	public static int commissionFee(int schematicLevel, CraftingBalance b) {
		return b.commissionPerLevel * Math.max(1, schematicLevel);
	}

	private CraftingRules() {
	}
}
