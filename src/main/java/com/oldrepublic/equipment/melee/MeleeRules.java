package com.oldrepublic.equipment.melee;

/**
 * The pure arithmetic of the vibroweapons and the electrostaff (spec MILESTONE_4.md 5.2 and 5.3):
 * the flurry chain, the guard's reduction and drain, cleave damage and the staff's charge window;
 * and of the vibro-axe's Sunder, the force pike's stun thrust and the material edges (MILESTONE_7.md
 * 9.1 and 9.2). Nothing here reads the world, so game tests check each rule with explicit ticks.
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
			case AXE -> b.guardAxePct;
			case PIKE -> b.guardPikePct;
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

	// ---------------------------------------------------------------- Milestone 7 (MILESTONE_7.md 9.1, 9.2)

	/**
	 * The damage of a player's swing before criticals and enchantments: vanilla's attack-strength
	 * scaling, {@code 0.2 + 0.8 x strength^2} of the attack damage.
	 */
	public static float swingDamage(float attackDamage, float strength) {
		float s = Math.max(0f, Math.min(1f, strength));
		return attackDamage * (0.2f + 0.8f * s * s);
	}

	/** Whether a swing of {@code kind} with {@code strength} is a Sunder: a full swing of the vibro-axe. */
	public static boolean sunders(VibroWeaponItem.Kind kind, float strength, MeleeBalance b) {
		return kind == VibroWeaponItem.Kind.AXE && strength >= b.sunderMinStrength;
	}

	/** A guard's drain under a Sunder: {@code sunderDrainMultiplier} times the ordinary drain. */
	public static float sunderDrain(float drain, MeleeBalance b) {
		return drain * Math.max(1f, b.sunderDrainMultiplier);
	}

	/**
	 * The drain a Sunder adds on top of a guard's own drain of {@code drain}, so the two together are
	 * {@link #sunderDrain} (for the saber and NPC guards, which drain the blow themselves).
	 */
	public static float sunderExtra(float drain, MeleeBalance b) {
		return sunderDrain(drain, b) - drain;
	}

	/** The guard drain with a Clan Iron Edge fitted ({@code clanIronEdgeDrainPct} less), or unchanged. */
	public static float edgeDrain(float drain, boolean clanIron, MeleeBalance b) {
		return clanIron ? drain * Math.max(0f, 1f - b.clanIronEdgeDrainPct / 100f) : drain;
	}

	/** The Starfall Edge's ratio on a melee blow: {@code 1 + starfallEdgeDamagePct/100} on a droid or construct, else 1. */
	public static float starfallRatio(boolean droidOrConstruct, MeleeBalance b) {
		return droidOrConstruct ? 1f + b.starfallEdgeDamagePct / 100f : 1f;
	}

	/**
	 * Durability a notch takes from a weapon: {@code notch}, or with a Starfall Edge fitted
	 * {@code notch x 100 / (100 + starfallEdgeDurabilityPct)}, rounded (5 becomes 4).
	 */
	public static int notchWear(int notch, boolean starfall, MeleeBalance b) {
		return starfall ? Math.round(notch * 100f / (100f + Math.max(0f, b.starfallEdgeDurabilityPct))) : notch;
	}

	/**
	 * The Starfall Edge's wear credit after one more hit: every hit earns {@code starfallEdgeDurabilityPct};
	 * at {@code 100 + pct} one point of wear is given back ({@link #refunds}), so a weapon lasts
	 * {@code 1 + pct/100} as many hits (25: one point back every fifth hit).
	 */
	public static float edgeCredit(float credit, MeleeBalance b) {
		return credit + Math.max(0f, b.starfallEdgeDurabilityPct);
	}

	/** Whether {@code credit} pays back one point of wear. */
	public static boolean refunds(float credit, MeleeBalance b) {
		return b.starfallEdgeDurabilityPct > 0f && credit >= 100f + b.starfallEdgeDurabilityPct;
	}

	/** The credit left after one point of wear was paid back. */
	public static float afterRefund(float credit, MeleeBalance b) {
		return credit - (100f + b.starfallEdgeDurabilityPct);
	}

	/** Whether a pike charge begun at {@code chargeStart} is loaded at {@code now}. */
	public static boolean pikeCharged(long chargeStart, long now, MeleeBalance b) {
		return chargeStart >= 0 && now - chargeStart >= b.pikeChargeTicks;
	}

	/** Whether a pike charge loaded at {@code loadedAt} is still live at {@code now}. */
	public static boolean pikeChargeLive(long loadedAt, long now, MeleeBalance b) {
		return loadedAt >= 0 && now - loadedAt <= b.pikeChargeWindow;
	}

	/** Whether the charge of {@code kind} (the staff or the pike) begun at {@code chargeStart} is loaded at {@code now}. */
	public static boolean chargeLoaded(VibroWeaponItem.Kind kind, long chargeStart, long now, MeleeBalance b) {
		return kind == VibroWeaponItem.Kind.PIKE ? pikeCharged(chargeStart, now, b) : staffCharged(chargeStart, now, b);
	}

	/** Whether the charge of {@code kind} loaded at {@code loadedAt} is still live at {@code now}. */
	public static boolean chargeLive(VibroWeaponItem.Kind kind, long loadedAt, long now, MeleeBalance b) {
		return kind == VibroWeaponItem.Kind.PIKE ? pikeChargeLive(loadedAt, now, b) : staffChargeLive(loadedAt, now, b);
	}

	/** A charged thrust's damage: multiplied on a droid (which is not stunned), unchanged on anything else. */
	public static float thrustDamage(float damage, boolean droid, MeleeBalance b) {
		return droid ? damage * b.pikeDroidMultiplier : damage;
	}

	private MeleeRules() {
	}
}
