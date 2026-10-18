package com.oldrepublic.equipment;

import java.util.Arrays;
import java.util.List;

import com.oldrepublic.identity.AlignmentBand;
import com.oldrepublic.identity.Path;

/**
 * The pure arithmetic of the era armour traits and set bonuses (spec section 9.1, and the Milestone 4
 * sets of MILESTONE_4.md 6.2 at the end). Callers pass what is worn, who wears it and the balance;
 * nothing here reads the world, so game tests check the worked example and every bonus directly. Per-piece percentages multiply per piece (four pieces at
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

	/**
	 * How many pieces of each set an entity wears (rank 3 robes count as their set's robe): one count
	 * per {@link ArmourSet} ordinal, so appending a set never breaks it (spec MILESTONE_4.md 6.1). The
	 * four M3 accessors and the four-count constructor stay, so M3 code reads it as before.
	 */
	public static final class Worn {
		/** Nothing worn. */
		public static final Worn NONE = new Worn(new int[ArmourSet.values().length]);

		private final int[] counts;

		/** The four M3 counts; every M4 set at zero. */
		public Worn(int republicTrooper, int sithTrooper, int jediRobes, int sithRobes) {
			this(fromM3(republicTrooper, sithTrooper, jediRobes, sithRobes));
		}

		private Worn(int[] counts) {
			this.counts = counts;
		}

		private static int[] fromM3(int republicTrooper, int sithTrooper, int jediRobes, int sithRobes) {
			int[] counts = new int[ArmourSet.values().length];
			counts[ArmourSet.REPUBLIC_TROOPER.ordinal()] = republicTrooper;
			counts[ArmourSet.SITH_TROOPER.ordinal()] = sithTrooper;
			counts[ArmourSet.JEDI_ROBES.ordinal()] = jediRobes;
			counts[ArmourSet.SITH_ROBES.ordinal()] = sithRobes;
			return counts;
		}

		public int republicTrooper() {
			return count(ArmourSet.REPUBLIC_TROOPER);
		}

		public int sithTrooper() {
			return count(ArmourSet.SITH_TROOPER);
		}

		public int jediRobes() {
			return count(ArmourSet.JEDI_ROBES);
		}

		public int sithRobes() {
			return count(ArmourSet.SITH_ROBES);
		}

		/** Pieces of {@code set}. */
		public int count(ArmourSet set) {
			return counts[set.ordinal()];
		}

		/** Whether all four pieces of {@code set} are worn. */
		public boolean full(ArmourSet set) {
			return count(set) >= FULL;
		}

		/** This with one more piece of {@code set}. */
		public Worn plus(ArmourSet set) {
			int[] next = counts.clone();
			next[set.ordinal()]++;
			return new Worn(next);
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Worn worn && Arrays.equals(counts, worn.counts);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(counts);
		}

		@Override
		public String toString() {
			return "Worn" + Arrays.toString(counts);
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

	/**
	 * Whether the set bonus of {@code set} is active: 4/4 worn and, for the robes, the Watch battle
	 * armour and the Sith war armour, the alignment gate passed.
	 */
	public static boolean active(ArmourSet set, Worn worn, Wearer w, EquipmentBalance b) {
		if (!worn.full(set)) {
			return false;
		}

		return switch (set) {
			case JEDI_ROBES, WATCH_BATTLE -> jediGate(w, b);
			case SITH_ROBES, SITH_WAR -> sithGate(w, b);
			default -> true;
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

	// ---- Milestone 4 sets (spec MILESTONE_4.md 6.2), W4

	/** Ability id of Force Barrier (Warden's Resolve). */
	public static final String BARRIER = "force_barrier";
	/** Ability id of Force Choke (Wrath Unbound). */
	public static final String CHOKE = "force_choke";
	/** Ability id of Force Speed (Hunter's Tenacity, Light on Your Feet). */
	public static final String SPEED = "force_speed";

	/**
	 * The per-piece traits of one set, whole percent in the balance convention (a negative "taken"
	 * percentage is resistance): all zero for the M3 sets, whose traits the M3 rules above count.
	 */
	public record PieceNumbers(float costPct, float regenPct, int maxEnergy, float blasterPct, float meleePct, float fallPct) {
		/** No traits. */
		public static final PieceNumbers ZERO = new PieceNumbers(0f, 0f, 0, 0f, 0f, 0f);
	}

	/** One worn piece of an M4 set with its quality's armour multiplier ({@code Quality.armour()}, 1 for Standard). */
	public record Piece(ArmourSet set, float quality) {
	}

	/** What the worn M4 pieces do together: multipliers (1 for none) and flat max energy. */
	public record GearTotals(float cost, float regen, int maxEnergy, float blasterTaken, float meleeTaken, float fallTaken) {
		/** Nothing worn. */
		public static final GearTotals NONE = new GearTotals(1f, 1f, 0, 1f, 1f, 1f);
	}

	/** The per-piece traits of {@code set} (6.2). */
	public static PieceNumbers numbers(ArmourSet set, EquipmentM4Balance m) {
		return switch (set) {
			case WATCH_BATTLE -> new PieceNumbers(m.watchCostPct, 0f, 0, 0f, m.watchMeleePct, 0f);
			case SITH_WAR -> new PieceNumbers(0f, m.sithWarRegenPct, m.sithWarMaxEnergy, 0f, m.sithWarMeleePct, 0f);
			case MANDALORIAN -> new PieceNumbers(m.mandoCostPct, 0f, 0, m.mandoBlasterPct, 0f, 0f);
			case SPACER -> new PieceNumbers(0f, m.spacerRegenPct, 0, 0f, 0f, m.spacerFallPct);
			default -> PieceNumbers.ZERO;
		};
	}

	/**
	 * One piece's ratio for a trait of {@code pct}: quality (7.4) strengthens the trait when it helps
	 * the wearer and leaves a drawback as it is, so a Masterwork piece is never worse than a Standard
	 * one. {@code lowerIsBetter} for costs and damage taken.
	 */
	public static float pieceRatio(float pct, float quality, boolean lowerIsBetter) {
		boolean helps = lowerIsBetter ? pct < 0f : pct > 0f;
		return ratio(helps ? pct * quality : pct);
	}

	/** The worn M4 pieces' traits together: ratios multiply per piece, max energy adds (rounded, scaled by quality). */
	public static GearTotals totals(List<Piece> pieces, EquipmentM4Balance m) {
		float cost = 1f;
		float regen = 1f;
		int maxEnergy = 0;
		float blaster = 1f;
		float melee = 1f;
		float fall = 1f;

		for (Piece piece : pieces) {
			PieceNumbers n = numbers(piece.set(), m);
			float q = piece.quality();
			cost *= pieceRatio(n.costPct(), q, true);
			regen *= pieceRatio(n.regenPct(), q, false);
			maxEnergy += n.maxEnergy() > 0 ? Math.round(n.maxEnergy() * q) : n.maxEnergy();
			blaster *= pieceRatio(n.blasterPct(), q, true);
			melee *= pieceRatio(n.meleePct(), q, true);
			fall *= pieceRatio(n.fallPct(), q, true);
		}

		return new GearTotals(cost, regen, maxEnergy, blaster, melee, fall);
	}

	/** Warden's Resolve: active (4/4 Watch battle armour, the light gate) and a parry at {@code parryTick} at most {@code watchSetTicks} ago. */
	public static boolean warden(Worn worn, Wearer w, long parryTick, long now, EquipmentBalance b, EquipmentM4Balance m) {
		long since = now - parryTick;
		return since >= 0 && since <= m.watchSetTicks && active(ArmourSet.WATCH_BATTLE, worn, w, b);
	}

	/** Wrath Unbound: active (4/4 Sith war armour, the dark gate) and health below {@code sithWarSetHealthFraction} of max. */
	public static boolean wrath(Worn worn, Wearer w, float health, float maxHealth, EquipmentBalance b, EquipmentM4Balance m) {
		return health < maxHealth * m.sithWarSetHealthFraction && active(ArmourSet.SITH_WAR, worn, w, b);
	}

	/** Whether {@code abilityId} is one of Wrath Unbound's powers (Force Choke, Force Lightning). */
	public static boolean wrathPower(String abilityId) {
		return CHOKE.equals(abilityId) || LIGHTNING.equals(abilityId);
	}

	/**
	 * The M4 set bonuses' Force cost multiplier of {@code abilityId}: Barrier during Warden's Resolve,
	 * Choke and Lightning during Wrath Unbound, Force Speed under Hunter's Tenacity (dearer).
	 */
	public static float setCostMultiplier(Worn worn, String abilityId, boolean warden, boolean wrath, EquipmentM4Balance m) {
		float mult = 1f;

		if (warden && BARRIER.equals(abilityId)) {
			mult *= ratio(m.watchSetBarrierCostPct);
		}

		if (wrath && wrathPower(abilityId)) {
			mult *= ratio(m.sithWarSetCostPct);
		}

		if (worn.full(ArmourSet.MANDALORIAN) && SPEED.equals(abilityId)) {
			mult *= ratio(m.mandoSetSpeedCostPct);
		}

		return Math.max(0f, mult);
	}

	/** Warden's Resolve's melee damage taken multiplier (1 outside its window). */
	public static float wardenMeleeTaken(boolean warden, EquipmentM4Balance m) {
		return warden ? Math.max(0f, ratio(m.watchSetParryResistPct)) : 1f;
	}

	/** Wrath Unbound's multiplier on Choke and Lightning damage dealt (1 when not in wrath). */
	public static float wrathDamage(boolean wrath, EquipmentM4Balance m) {
		return wrath ? ratio(m.sithWarSetDamagePct) : 1f;
	}

	/** Hunter's Tenacity: control durations on a wearer of 4/4 Mandalorian armour, else 1. */
	public static float hunterControl(Worn worn, EquipmentM4Balance m) {
		return worn.full(ArmourSet.MANDALORIAN) ? Math.max(0f, ratio(m.mandoSetControlPct)) : 1f;
	}

	/** Jet pack charges: the pack's own, plus Hunter's Tenacity's. */
	public static int jetCharges(Worn worn, EquipmentM4Balance m, int packCharges) {
		return packCharges + (worn.full(ArmourSet.MANDALORIAN) ? m.mandoSetJetCharges : 0);
	}

	/** Light on Your Feet: the further fall damage taken multiplier at 4/4 spacer's gear, else 1. */
	public static float spacerFall(Worn worn, EquipmentM4Balance m) {
		return worn.full(ArmourSet.SPACER) ? Math.max(0f, ratio(m.spacerSetFallPct)) : 1f;
	}

	/** Light on Your Feet: the Force Speed duration multiplier at 4/4 spacer's gear, else 1. */
	public static float speedDuration(Worn worn, EquipmentM4Balance m) {
		return worn.full(ArmourSet.SPACER) ? Math.max(0f, ratio(m.spacerSetSpeedDurationPct)) : 1f;
	}

	/** Light on Your Feet: the movement efficiency that keeps soul sand and ash from slowing (0 without the set). */
	public static float terrainEfficiency(Worn worn, EquipmentM4Balance m) {
		return worn.full(ArmourSet.SPACER) ? Math.max(0f, Math.min(1f, m.spacerSetTerrainEfficiency)) : 0f;
	}

	/**
	 * Every control multiplier on the wearer together (Iron Discipline, Hunter's Tenacity, stabiliser
	 * mods, {@code Extra.controlPct} of the pieces), never below {@code controlMin}; 1 stays 1.
	 */
	public static float controlTotal(float product, EquipmentM4Balance m) {
		return product >= 1f ? product : Math.max(m.controlMin, product);
	}

	private SetBonusRules() {
	}
}
