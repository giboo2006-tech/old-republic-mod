package com.oldrepublic.crafting;

/**
 * A crafted item's quality (spec MILESTONE_4.md 7.4), stored by ordinal in the
 * {@code oldrepublic:quality} component; append only. The multipliers start at the 14.3 defaults;
 * the crafting feature's balance replaces them on load ({@link #configure}).
 */
public enum Quality {
	STANDARD,
	FINE,
	MASTERWORK;

	private static volatile float[] weapon = {1.0f, 1.06f, 1.12f};
	private static volatile float[] armour = {1.0f, 1.15f, 1.30f};
	private static volatile float[] durability = {1.0f, 1.25f, 1.5f};

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

	/** Replaces the multipliers (three values each, by ordinal); arrays of another length are ignored. */
	public static void configure(float[] weaponMultipliers, float[] armourMultipliers, float[] durabilityMultipliers) {
		int n = values().length;

		if (weaponMultipliers.length == n) {
			weapon = weaponMultipliers.clone();
		}

		if (armourMultipliers.length == n) {
			armour = armourMultipliers.clone();
		}

		if (durabilityMultipliers.length == n) {
			durability = durabilityMultipliers.clone();
		}
	}
}
