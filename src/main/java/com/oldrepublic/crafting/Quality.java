package com.oldrepublic.crafting;

/**
 * A crafted item's quality (spec MILESTONE_4.md 7.4), stored by ordinal in the
 * {@code oldrepublic:quality} component; append only. The multipliers start at the 14.3 defaults;
 * the crafting feature's balance replaces them on load ({@link #configure}).
 */
public enum Quality {
	STANDARD,
	FINE,
	MASTERWORK,
	/** The fourth tier (MILESTONE_5.md 11.1): never rolled; a legendary schematic always makes it. */
	LEGENDARY;

	/** The built-in multipliers by ordinal (MILESTONE_4.md 14.3, MILESTONE_5.md 11.1). */
	private static final float[] DEFAULT_WEAPON = {1.0f, 1.06f, 1.12f, 1.18f};
	private static final float[] DEFAULT_ARMOUR = {1.0f, 1.15f, 1.30f, 1.40f};
	private static final float[] DEFAULT_DURABILITY = {1.0f, 1.25f, 1.5f, 1.75f};

	private static volatile float[] weapon = DEFAULT_WEAPON.clone();
	private static volatile float[] armour = DEFAULT_ARMOUR.clone();
	private static volatile float[] durability = DEFAULT_DURABILITY.clone();

	/** Weapon damage multiplier. */
	public float weapon() {
		return weapon[ordinal()];
	}

	/** Armour trait and defence multiplier (defence rounds down). */
	public float armour() {
		return armour[ordinal()];
	}

	/** Durability multiplier. */
	public float durability() {
		return durability[ordinal()];
	}

	/** The quality at this ordinal, clamped into range (the saved form). */
	public static Quality of(int ordinal) {
		Quality[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}

	/**
	 * Replaces the multipliers, by ordinal. Four values set every tier; three (a Milestone 4 balance file) set Standard, Fine
	 * and Masterwork and keep Legendary's built-in value (MILESTONE_5.md 11.1, 16); arrays of another length are ignored.
	 */
	public static void configure(float[] weaponMultipliers, float[] armourMultipliers, float[] durabilityMultipliers) {
		weapon = table(weaponMultipliers, DEFAULT_WEAPON, weapon);
		armour = table(armourMultipliers, DEFAULT_ARMOUR, armour);
		durability = table(durabilityMultipliers, DEFAULT_DURABILITY, durability);
	}

	/** {@code given} over {@code defaults} when it has one value per tier or one fewer; otherwise {@code current}. */
	private static float[] table(float[] given, float[] defaults, float[] current) {
		if (given.length != defaults.length && given.length != defaults.length - 1) {
			return current;
		}

		float[] table = defaults.clone();
		System.arraycopy(given, 0, table, 0, given.length);
		return table;
	}
}
