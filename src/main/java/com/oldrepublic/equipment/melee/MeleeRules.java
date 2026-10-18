package com.oldrepublic.equipment.melee;

/**
 * The pure arithmetic of the vibroweapons and the electrostaff (spec MILESTONE_4.md 5.2 and 5.3):
 * the flurry chain, the guard's reduction and drain, cleave damage and the staff's charge window.
 * Nothing here reads the world, so game tests check each rule with explicit ticks.
 */
public final class MeleeRules {
	/**
	 * The flurry chain after a hit at {@code now}: the hit continues the chain when it lands within
	 * {@code flurryWindow} ticks of the last one and the chain has not just paid out, else it starts a
	 * new chain at 1. A result of {@code flurryHits} is the flurry hit.
	 *
	 * @param chain the chain count after the previous hit (0 for none)
	 * @param lastHit the tick of the previous hit
	 */
	public static int nextChain(int chain, long lastHit, long now, MeleeBalance b) {
		boolean continues = chain > 0 && chain < b.flurryHits && now - lastHit <= b.flurryWindow && now >= lastHit;
		return continues ? chain + 1 : 1;
	}

	/** Whether a hit that brings the chain to {@code chain} is the flurry hit (+bonus, no cooldown reset). */
	public static boolean isFlurry(int chain, MeleeBalance b) {
		return b.flurryHits > 0 && chain >= b.flurryHits;
	}

	/** The fraction of a front melee hit a raised guard lets through, by weapon. */
	public static float guardThrough(VibroWeaponItem.Kind kind, MeleeBalance b) {
		float pct = switch (kind) {
			case BLADE -> b.guardBladePct;
			case SWORD -> b.guardSwordPct;
			default -> b.guardStaffPct;
		};
		return Math.max(0f, Math.min(1f, 1f - pct / 100f));
	}

	/** Guard stamina a blocked hit of {@code damage} drains: the M2 guard's base plus per-damage cost. */
	public static float guardDrain(float damage, float drainBase, float drainPerDamage) {
		return Math.max(0f, drainBase + drainPerDamage * damage);
	}

	/** Damage of each extra cleave hit, from the swing's damage. */
	public static float cleaveDamage(float swingDamage, MeleeBalance b) {
		return swingDamage * b.cleaveMultiplier;
	}

	/** Whether a swing with {@code strength} (0 to 1, the attack charge) is fully charged for a cleave. */
	public static boolean fullyCharged(float strength, MeleeBalance b) {
		return strength >= b.cleaveMinStrength;
	}

	/** Whether a staff charge begun at {@code chargeStart} is loaded at {@code now}. */
	public static boolean staffCharged(long chargeStart, long now, MeleeBalance b) {
		return chargeStart >= 0 && now - chargeStart >= b.staffChargeTicks;
	}

	/** Whether a staff charge loaded at {@code loadedAt} is still live at {@code now}. */
	public static boolean staffChargeLive(long loadedAt, long now, MeleeBalance b) {
		return loadedAt >= 0 && now - loadedAt <= b.staffChargeWindow;
	}

	/** Shock damage on a droid is multiplied; on anything else it is unchanged. */
	public static float shockDamage(float damage, boolean droid, MeleeBalance b) {
		return droid ? damage * b.droidShockMultiplier : damage;
	}

	private MeleeRules() {
	}
}
