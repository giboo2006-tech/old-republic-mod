package com.oldrepublic.saber;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;

/**
 * Saber forms (stances). A player may own several form nodes in the Lightsaber tree; the active one
 * is stored by the skills feature ({@code Skills.activeForm}) and changes the numbers below.
 */
public enum SaberForm {
	UNFORMED(""),
	/** Form III: defence. */
	SORESU("form_soresu"),
	/** Form IV: aggression and mobility. */
	ATARU("form_ataru");

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

	/** What this form changes, from the balance numbers. Pure. */
	public Modifiers modifiers(SaberBalance b) {
		return switch (this) {
			case UNFORMED -> new Modifiers(1f, 0f, b.guardArcDegrees, 1f, 1f, 1f, -1f, 1f, 0f, b.heavyWindupTicks,
					true, b.lungeStaminaCost, b.lungeMaxRange, b.lungeCooldownTicks, b.comboWindowTicks, false);
			case SORESU -> new Modifiers(b.soresuParryMult, 0f, b.soresuGuardArc, b.soresuDrainMult, 1f, b.soresuRegenMult, b.soresuDeflectAim,
					b.soresuDamageMult, 0f, b.heavyWindupTicks + b.soresuHeavyWindupExtra,
					false, b.lungeStaminaCost, b.lungeMaxRange, b.lungeCooldownTicks, b.comboWindowTicks, false);
			case ATARU -> new Modifiers(1f, b.ataruParryTicks, b.guardArcDegrees, 1f, b.ataruStaminaMult, 1f, -1f, 1f, b.ataruSpeed, b.heavyWindupTicks,
					true, b.ataruLungeCost, b.ataruLungeRange, b.ataruLungeCooldown, b.ataruComboWindow, true);
		};
	}

	/**
	 * The numbers a form changes (spec section 9.4).
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
	 */
	public record Modifiers(float parryMult, float parryTicks, float guardArc, float drainMult, float maxStaminaMult, float regenMult,
			float deflectAim, float damageMult, float attackSpeedBonus, int heavyWindupTicks, boolean lungeAllowed, float lungeCost,
			float lungeRange, int lungeCooldown, int comboWindow, boolean aerialStrike) {
	}
}
