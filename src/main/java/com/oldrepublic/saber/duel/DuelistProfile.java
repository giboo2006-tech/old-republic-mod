package com.oldrepublic.saber.duel;

/**
 * How a saber duelist fights.
 *
 * <p>The component order is frozen (M3): the M2 fields, then the three M3 fields. The M2 field list
 * stays available as a constructor so older call sites keep compiling.
 *
 * @param yieldFraction   health fraction at which it yields in a spar or encounter; 0 = never
 * @param deflects        whether it sends blaster bolts back while it guards (spec 7.8, 10.7)
 * @param heavyDamage     damage of the heavy last swing of a string of 2 or more; 0 = no heavy swing
 *                        (every swing deals {@code damage})
 * @param parryMinStamina guard stamina fraction it needs to parry a light attack; 0 = the
 *                        {@code saber.npcParryMinStamina} balance value
 */
public record DuelistProfile(String id, float damage, float guardStamina, float parryChance, int stringMin, int stringMax,
		int windupTicks, int gapTicks, int recoveryTicks, boolean shove, float yieldFraction,
		boolean deflects, float heavyDamage, float parryMinStamina) {
	/** The M2 field list: deflects false, heavyDamage 0 (= damage), parryMinStamina 0 (= saber.npcParryMinStamina). */
	public DuelistProfile(String id, float damage, float guardStamina, float parryChance, int stringMin, int stringMax,
			int windupTicks, int gapTicks, int recoveryTicks, boolean shove, float yieldFraction) {
		this(id, damage, guardStamina, parryChance, stringMin, stringMax, windupTicks, gapTicks, recoveryTicks, shove, yieldFraction,
				false, 0f, 0f);
	}

	/** Whether the last swing of a string of 2 or more is a heavy swing. */
	public boolean hasHeavySwing() {
		return heavyDamage > 0f;
	}
}
