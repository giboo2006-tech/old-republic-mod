package com.oldrepublic.equipment;

import com.oldrepublic.identity.AlignmentBand;
import com.oldrepublic.identity.Path;

/**
 * The pure arithmetic of the era armour traits and set bonuses (spec section 9.1). Callers pass what
 * is worn, who wears it and the balance; nothing here reads the world, so game tests check the
 * worked example and every bonus directly. Per-piece percentages multiply per piece (four pieces at
 * +5% cost give ×1.05⁴ = ×1.216); a set bonus multiplies on top. The cross-source clamps are
 * {@code ForceModifiers}' and {@code DamageModifiers}' job, not these rules'.
 */
public final class SetBonusRules {
	/** Ability id of Force Heal (Calm of the Watch). */
	public static final String HEAL = "force_heal";
	/** Ability id of Force Lightning (Hunger of the Tomb). */
	public static final String LIGHTNING = "force_lightning";
	/** Ability id of Telekinetic Lift, the Independent's substitute for both. */
	public static final String LIFT = "force_lift";
	/** Pieces in a full set. */
	public static final int FULL = 4;

	/** How many pieces of each set an entity wears (rank 3 robes count as their set's robe). */
	public record Worn(int republicTrooper, int sithTrooper, int jediRobes, int sithRobes) {
		/** Nothing worn. */
		public static final Worn NONE = new Worn(0, 0, 0, 0);

		/** Pieces of {@code set}. */
		public int count(ArmourSet set) {
			return switch (set) {
				case REPUBLIC_TROOPER -> republicTrooper;
				case SITH_TROOPER -> sithTrooper;
				case JEDI_ROBES -> jediRobes;
				case SITH_ROBES -> sithRobes;
			};
		}

		/** Whether all four pieces of {@code set} are worn. */
		public boolean full(ArmourSet set) {
			return count(set) >= FULL;
		}

		/** This with one more piece of {@code set}. */
		public Worn plus(ArmourSet set) {
			return new Worn(republicTrooper + (set == ArmourSet.REPUBLIC_TROOPER ? 1 : 0), sithTrooper + (set == ArmourSet.SITH_TROOPER ? 1 : 0),
					jediRobes + (set == ArmourSet.JEDI_ROBES ? 1 : 0), sithRobes + (set == ArmourSet.SITH_ROBES ? 1 : 0));
		}
	}

	/** The wearer's identity as far as the robe gates care; a mob is {@link #NOBODY}. */
	public record Wearer(int alignment, AlignmentBand band, Path path) {
		/** Not a player: no robe set bonus applies. */
		public static final Wearer NOBODY = new Wearer(0, AlignmentBand.BALANCED, Path.NONE);

		/** Whether this is an Independent in the Balanced band, who may use either robe bonus. */
		public boolean balancedIndependent() {
			return path == Path.INDEPENDENT && band == AlignmentBand.BALANCED;
		}
	}

	/** {@code (1 + pct/100)^pieces}: the per-piece multiplier of {@code pieces} pieces. */
	public static float perPiece(int pieces, float pct) {
		return (float) Math.pow(1f + pct / 100f, pieces);
	}

	/** {@code 1 + pct/100}. */
	public static float ratio(float pct) {
		return 1f + pct / 100f;
	}

	/** Calm of the Watch's gate: alignment at least +{@code setAlignment}, or an Independent in the Balanced band. */
	public static boolean jediGate(Wearer w, EquipmentBalance b) {
		return w.alignment() >= b.setAlignment || w.balancedIndependent();
	}

	/** Hunger of the Tomb's gate: alignment at most -{@code setAlignment}, or an Independent in the Balanced band. */
	public static boolean sithGate(Wearer w, EquipmentBalance b) {
		return w.alignment() <= -b.setAlignment || w.balancedIndependent();
	}

	/** Whether the set bonus of {@code set} is active: 4/4 worn and, for the robes, the alignment gate passed. */
	public static boolean active(ArmourSet set, Worn worn, Wearer w, EquipmentBalance b) {
		if (!worn.full(set)) {
			return false;
		}

		return switch (set) {
			case REPUBLIC_TROOPER, SITH_TROOPER -> true;
			case JEDI_ROBES -> jediGate(w, b);
			case SITH_ROBES -> sithGate(w, b);
		};
	}

	/** The power Calm of the Watch makes cheaper: Force Heal, or Lift for an Independent. */
	public static String calmAbility(Path path) {
		return path == Path.INDEPENDENT ? LIFT : HEAL;
	}

	/** The power Hunger of the Tomb makes cheaper: Force Lightning, or Lift for an Independent. */
	public static String hungerAbility(Path path) {
		return path == Path.INDEPENDENT ? LIFT : LIGHTNING;
	}

	/** Force Energy cost multiplier of {@code abilityId}: every piece's cost trait, then the robe bonus for that power. */
	public static float costMultiplier(Worn worn, Wearer w, String abilityId, EquipmentBalance b) {
		float mult = perPiece(worn.republicTrooper() + worn.sithTrooper(), b.trooperCostPct)
				* perPiece(worn.jediRobes(), b.jediCostPct)
				* perPiece(worn.sithRobes(), b.sithCostPct);

		if (active(ArmourSet.JEDI_ROBES, worn, w, b) && calmAbility(w.path()).equals(abilityId)) {
			mult *= ratio(b.jediSetHealCost);
		}

		if (active(ArmourSet.SITH_ROBES, worn, w, b) && hungerAbility(w.path()).equals(abilityId)) {
			mult *= ratio(b.sithSetLightningCost);
		}

		return mult;
	}

	/** Force Energy regeneration multiplier: trooper pieces slow it, Jedi robe pieces speed it. */
	public static float regenMultiplier(Worn worn, EquipmentBalance b) {
		return perPiece(worn.republicTrooper() + worn.sithTrooper(), b.trooperRegenPct) * perPiece(worn.jediRobes(), b.jediRegenPct);
	}

	/** Flat max Force Energy: {@code sithMaxEnergy} per Sith robe piece. */
	public static int maxEnergyBonus(Worn worn, EquipmentBalance b) {
		return worn.sithRobes() * b.sithMaxEnergy;
	}

	/** Multiplier on blaster damage taken: Republic pieces, then Shoulder to Shoulder at 4/4. */
	public static float blasterMultiplier(Worn worn, EquipmentBalance b) {
		float mult = perPiece(worn.republicTrooper(), b.republicBlasterPct);
		return worn.full(ArmourSet.REPUBLIC_TROOPER) ? mult * ratio(b.republicSetBlasterPct) : mult;
	}

	/** Multiplier on melee damage taken: Sith trooper pieces. */
	public static float meleeMultiplier(Worn worn, EquipmentBalance b) {
		return perPiece(worn.sithTrooper(), b.sithMeleePct);
	}

	/** Iron Discipline: multiplier on every control duration at 4/4 Sith trooper armour, else 1. */
	public static float controlMultiplier(Worn worn, EquipmentBalance b) {
		return worn.full(ArmourSet.SITH_TROOPER) ? Math.max(0f, ratio(b.sithSetControlPct)) : 1f;
	}

	/** Shoulder to Shoulder: multiplier on the guard drain of bolts at 4/4 Republic trooper armour, else 1. */
	public static float boltDrainMultiplier(Worn worn, EquipmentBalance b) {
		return worn.full(ArmourSet.REPUBLIC_TROOPER) ? Math.max(0f, ratio(b.republicSetBoltDrainPct)) : 1f;
	}

	/** Iron Discipline's knockback resistance while 4/4 Sith trooper armour is worn, else 0. */
	public static float setKnockback(Worn worn, EquipmentBalance b) {
		return worn.full(ArmourSet.SITH_TROOPER) ? b.sithSetKnockbackResist : 0f;
	}

	/** Hunger of the Tomb: whether a kill at {@code killTick} counts after a power hit at {@code hitTick}. */
	public static boolean hungerWindow(long hitTick, long killTick, EquipmentBalance b) {
		long since = killTick - hitTick;
		return since >= 0 && since <= b.sithSetWindowTicks;
	}

	private SetBonusRules() {
	}
}
