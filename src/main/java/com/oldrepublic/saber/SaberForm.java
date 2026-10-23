package com.oldrepublic.saber;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;

/**
 * Saber forms (stances). A player may own several form nodes in the Lightsaber tree; the active one
 * is stored by the skills feature ({@code Skills.activeForm}) and changes the numbers below. Stored
 * by node id and sent by ordinal (the form-change effect's variant): append only.
 */
public enum SaberForm {
	UNFORMED(""),
	/** Form III: defence. */
	SORESU("form_soresu"),
	/** Form IV: aggression and mobility. */
	ATARU("form_ataru"),
	/** Form II: the duellist (MILESTONE_5.md 10.1). */
	MAKASHI("form_makashi"),
	/** Form V: power (MILESTONE_5.md 10.1). */
	DJEM_SO("form_djem_so"),
	/** Two blades, only while dual-wielding (MILESTONE_5.md 10.1, 10.2). */
	JAR_KAI("form_jar_kai");

	private final String nodeId;

	SaberForm(String nodeId) {
		this.nodeId = nodeId;
	}

	/** Skill node id of the form, {@code ""} for Unformed. */
	public String nodeId() {
		return nodeId;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component displayName() {
		return Component.translatable("form.oldrepublic." + id());
	}

	/** The form with this node id; unknown or empty ids are Unformed. */
	public static SaberForm byNode(String nodeId) {
		for (SaberForm form : values()) {
			if (form.nodeId.equals(nodeId)) {
				return form;
			}
		}

		return UNFORMED;
	}

	/** By {@link #id()}, or null. */
	public static @Nullable SaberForm byId(String id) {
		for (SaberForm form : values()) {
			if (form.id().equals(id)) {
				return form;
			}
		}

		return null;
	}

	public static SaberForm byIndex(int index) {
		SaberForm[] values = values();
		return values[Math.floorMod(index, values.length)];
	}

	/**
	 * What this form changes, from the balance numbers. Pure. Jar'Kai's numbers hold only while the
	 * player dual-wields; otherwise {@code SaberPassives.form} reports Unformed (MILESTONE_5.md 10.1).
	 */
	public Modifiers modifiers(SaberBalance b) {
		return switch (this) {
			case SORESU -> new Modifiers(b.soresuParryMult, 0f, b.soresuGuardArc, b.soresuDrainMult, 1f, b.soresuRegenMult, b.soresuDeflectAim,
					b.soresuDamageMult, 0f, b.heavyWindupTicks + b.soresuHeavyWindupExtra,
					false, b.lungeStaminaCost, b.lungeMaxRange, b.lungeCooldownTicks, b.comboWindowTicks, false);
			case ATARU -> new Modifiers(1f, b.ataruParryTicks, b.guardArcDegrees, 1f, b.ataruStaminaMult, 1f, -1f, 1f, b.ataruSpeed, b.heavyWindupTicks,
					true, b.ataruLungeCost, b.ataruLungeRange, b.ataruLungeCooldown, b.ataruComboWindow, true);
			case MAKASHI -> unformed(b, b.makashiParryTicks, b.heavyWindupTicks, new Traits(0f, 1f, 0, 0f, 1f + b.makashiBoltGuardPct / 100f,
					b.makashiRiposteWindow, b.makashiPassiveWindow, 1f + b.makashiRipostePct / 100f, 1f + b.makashiDuelistPct / 100f, 0f, true));
			case DJEM_SO -> unformed(b, 0f, Math.max(1, b.heavyWindupTicks - b.djemSoWindupCut), new Traits(b.djemSoSpeedPct, 1f + b.djemSoHeavyPct / 100f,
					b.djemSoStaggerTicks, b.djemSoReturnChance, 1f, 0, 0, 1f, 1f, 0f, true));
			case JAR_KAI -> unformed(b, b.jarKaiParryTicks, b.heavyWindupTicks, new Traits(b.jarKaiSpeedPct, 1f, 0, 0f, 1f, 0, 0, 1f, 1f, b.jarKaiGuard, false));
			default -> unformed(b, 0f, b.heavyWindupTicks, Traits.NONE);
		};
	}

	/** Unformed's numbers with a flat parry change, a heavy wind-up and the Milestone 5 traits. */
	private static Modifiers unformed(SaberBalance b, float parryTicks, int heavyWindupTicks, Traits traits) {
		return new Modifiers(1f, parryTicks, b.guardArcDegrees, 1f, 1f, 1f, -1f, 1f, 0f, heavyWindupTicks,
				true, b.lungeStaminaCost, b.lungeMaxRange, b.lungeCooldownTicks, b.comboWindowTicks, false, traits);
	}

	/**
	 * The numbers a form changes (spec section 9.4; the Milestone 5 forms' own in {@link Traits}).
	 *
	 * @param parryMult         multiplier on the parry window
	 * @param parryTicks        ticks added to the parry window after the multiplier
	 * @param guardArc          total guarded arc in degrees
	 * @param drainMult         multiplier on stamina drained while guarding
	 * @param maxStaminaMult    multiplier on maximum saber stamina
	 * @param regenMult         multiplier on stamina regeneration
	 * @param deflectAim        unparried bolts go within this many degrees of the crosshair; negative = random arc
	 * @param damageMult        multiplier on melee saber damage
	 * @param attackSpeedBonus  attacks per second added
	 * @param heavyWindupTicks  heavy attack wind-up
	 * @param lungeAllowed      whether the lunge can be used
	 * @param lungeCost         stamina cost of a lunge
	 * @param lungeRange        maximum lunge distance
	 * @param lungeCooldown     lunge cooldown in ticks
	 * @param comboWindow       ticks allowed between combo hits
	 * @param aerialStrike      whether the first airborne attack is an aerial strike
	 * @param traits            the Milestone 5 forms' numbers ({@link Traits#NONE} for the others)
	 */
	public record Modifiers(float parryMult, float parryTicks, float guardArc, float drainMult, float maxStaminaMult, float regenMult,
			float deflectAim, float damageMult, float attackSpeedBonus, int heavyWindupTicks, boolean lungeAllowed, float lungeCost,
			float lungeRange, int lungeCooldown, int comboWindow, boolean aerialStrike, Traits traits) {
		/** The Milestone 2 forms' numbers, with no Milestone 5 traits. */
		public Modifiers(float parryMult, float parryTicks, float guardArc, float drainMult, float maxStaminaMult, float regenMult,
				float deflectAim, float damageMult, float attackSpeedBonus, int heavyWindupTicks, boolean lungeAllowed, float lungeCost,
				float lungeRange, int lungeCooldown, int comboWindow, boolean aerialStrike) {
			this(parryMult, parryTicks, guardArc, drainMult, maxStaminaMult, regenMult, deflectAim, damageMult, attackSpeedBonus, heavyWindupTicks,
					lungeAllowed, lungeCost, lungeRange, lungeCooldown, comboWindow, aerialStrike, Traits.NONE);
		}
	}

	/**
	 * What the Milestone 5 forms change (MILESTONE_5.md 10.1).
	 *
	 * @param speedPct                attack speed change in percent while a lit saber is held (Djem So, Jar'Kai)
	 * @param heavyDamageMult         multiplier on heavy attack damage (Djem So)
	 * @param parryStaggerTicks       a parried melee attacker is also staggered this many ticks, 0 for none (Djem So)
	 * @param boltReturnChance        chance a deflected, unparried bolt flies back at its shooter (Djem So)
	 * @param boltDrainMult           multiplier on the guard stamina blaster bolts drain (Makashi)
	 * @param riposteTicks            the form's own riposte window after a parry, 0 for none (Makashi)
	 * @param riposteTicksWithPassive the window when the {@code riposte} passive is owned too: the two never stack (Makashi)
	 * @param riposteMult             the form's riposte damage multiplier (Makashi)
	 * @param duelistDamageMult       multiplier on damage against saber users, {@code SaberDuelist} (Makashi)
	 * @param staminaBonus            guard stamina added after the multipliers (Jar'Kai)
	 * @param offhandPenalty          whether off-hand strikes take the dual-wielding penalty (not under Jar'Kai)
	 */
	public record Traits(float speedPct, float heavyDamageMult, int parryStaggerTicks, float boltReturnChance, float boltDrainMult,
			int riposteTicks, int riposteTicksWithPassive, float riposteMult, float duelistDamageMult, float staminaBonus, boolean offhandPenalty) {
		/** No change: the Milestone 2 forms and Unformed. */
		public static final Traits NONE = new Traits(0f, 1f, 0, 0f, 1f, 0, 0, 1f, 1f, 0f, true);
	}
}
