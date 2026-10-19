package com.oldrepublic.saber;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents.SaberConfig;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The numbers a hilt configuration produces. Pure computation from the parts and
 * {@link SaberBalance}; the blade colour never enters here.
 *
 * @param damage      ignited attack damage (including the player's base 1)
 * @param attackSpeed attacks per second
 * @param reachBonus  extra entity interaction range in blocks
 * @param maxStamina  guard stamina
 * @param parryTicks  parry window length in ticks (guard ticks below this value parry)
 */
public record SaberStats(double damage, double attackSpeed, double reachBonus, float maxStamina, float parryTicks) {
	/** Modifier id for the Guardian emitter's reach. */
	public static final Identifier REACH_MODIFIER_ID = OldRepublicMod.id("saber_reach");
	private static final double PLAYER_BASE_DAMAGE = 1.0;
	private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;

	/** A saber of these parts; a null {@code pommel} (the saberstaff, which has none) adds nothing. */
	public static SaberStats of(Emitter emitter, Grip grip, @Nullable Pommel pommel, SaberBalance b) {
		double damage = b.baseDamage;
		double speed = b.baseAttackSpeed;
		double reach = 0;
		float stamina = b.guardStamina;
		float parry = b.parryWindowTicks;

		// Phase 0 placeholders (MILESTONE_5.md 11.2): each legendary part counts as the part its model falls back to (Cantor as
		// Guardian, Sunforged as Wrapped, Hask as Weighted) until W7 writes its own numbers.
		switch (emitter) {
			case GUARDIAN, CANTOR -> {
				reach += b.guardianReach;
				speed += b.guardianSpeed;
			}
			case DUELIST -> {
				speed += b.duelistSpeed;
				damage += b.duelistDamage;
			}
			default -> {
			}
		}

		switch (grip) {
			case WRAPPED, SUNFORGED -> stamina *= b.wrappedStaminaMultiplier;
			case RIDGED -> speed += b.ridgedSpeed;
			// The staff grip's numbers are the staff's own (staff()).
			default -> {
			}
		}

		switch (pommel) {
			case null -> {
			}
			case WEIGHTED, HASK -> {
				damage += b.weightedDamage;
				speed += b.weightedSpeed;
			}
			case BALANCED -> parry *= b.balancedParryMultiplier;
			case ARCHIVIST -> {
				// A flat bonus: added before the form multiplier (withForm), like centered_mind.
				parry += b.archivistParryTicks;
				speed += b.archivistAttackSpeed;
			}
			default -> {
			}
		}

		// Emitter.RELIQUARY changes no number here: its aimed deflections and parry bonus are SaberGuard's.

		return new SaberStats(damage, Math.max(0.1, speed), reach, stamina, parry);
	}

	public static SaberStats of(SaberConfig config, SaberBalance b) {
		return of(config.emitter(), config.grip(), config.pommel(), b);
	}

	/**
	 * The stats of {@code stack} itself (spec MILESTONE_4.md 5.5, 5.6): a lightsaber's by its
	 * parts; a training saber's with its fixed damage; a saberstaff's by {@link #staff}, with its
	 * second blade lit or not. Lens effects are not here: they depend on the wielder's
	 * {@code lens_mastery} and are applied where they act ({@code SaberLens}).
	 */
	public static SaberStats of(ItemStack stack, SaberBalance b) {
		SaberConfig config = SaberComponents.config(stack);

		if (stack.getItem() instanceof TrainingSaberItem) {
			SaberStats s = of(config, b);
			return new SaberStats(b.trainingSaberDamage, s.attackSpeed, s.reachBonus, s.maxStamina, s.parryTicks);
		}

		if (stack.getItem() instanceof SaberStaffItem) {
			return staff(config.emitter(), config.grip(), !SaberComponents.staffMode(stack).single(), b);
		}

		return of(config, b);
	}

	/**
	 * A saberstaff of these parts (5.6): the hilt with no pommel, then with both blades lit damage
	 * {@code staffDamagePct}, attack speed {@code staffSpeedBonus}, reach {@code staffReach} and the
	 * parry window {@code staffParryTicks}. One blade gives a normal saber's damage and parry window
	 * and keeps the staff's speed and reach.
	 */
	public static SaberStats staff(Emitter emitter, Grip grip, boolean bothBlades, SaberBalance b) {
		SaberStats s = of(emitter, grip, null, b);
		double speed = s.attackSpeed + b.staffSpeedBonus;
		double reach = s.reachBonus + b.staffReach;

		if (!bothBlades) {
			return new SaberStats(s.damage, speed, reach, s.maxStamina, s.parryTicks);
		}

		return new SaberStats(s.damage * (1.0 + b.staffDamagePct / 100.0), speed, reach, s.maxStamina, Math.max(0f, s.parryTicks + b.staffParryTicks));
	}

	/**
	 * These stats in a form, with the {@code steady_guard} passive at {@code steadyGuardRank} and
	 * {@code extraParryTicks} from other passives ({@code centered_mind}, {@code archivists_ward}).
	 * Only stamina and the parry window change here; the form's other numbers are read from
	 * {@link SaberForm.Modifiers}.
	 *
	 * <p>Parry window order (spec 10.4): the hilt's window (base, Balanced pommel, the Archivist
	 * pommel's flat ticks) plus the flat passive bonuses, then the form multiplier (Soresu), then the
	 * form's own flat ticks (Ataru), then the cap {@code parryWindowCap}.
	 */
	public SaberStats withForm(SaberForm.Modifiers form, int steadyGuardRank, float extraParryTicks, SaberBalance b) {
		float stamina = maxStamina * (1f + b.steadyGuardPerRank * Math.max(0, steadyGuardRank)) * form.maxStaminaMult();
		float parry = parryWindow(parryTicks, extraParryTicks, form.parryMult(), form.parryTicks(), b.parryWindowCap);
		return new SaberStats(damage, attackSpeed, reachBonus, stamina, parry);
	}

	/**
	 * The parry window: {@code (hilt + flat) x formMult + formTicks}, never below 0 and never above
	 * {@code cap} (a cap of 0 or less means none).
	 */
	public static float parryWindow(float hilt, float flat, float formMult, float formTicks, float cap) {
		float window = Math.max(0f, (hilt + flat) * formMult + formTicks);
		return cap > 0f ? Math.min(window, cap) : window;
	}

	/** The attribute modifiers a saber carries while ignited or retracted (main hand only). */
	public ItemAttributeModifiers modifiers(boolean ignited, SaberBalance b) {
		double attackDamage = ignited ? damage : b.retractedDamage;
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage - PLAYER_BASE_DAMAGE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed - PLAYER_BASE_ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);

		if (ignited && reachBonus > 0) {
			// Melee reach reads ENTITY_INTERACTION_RANGE when the item has no attack_range component (AttackRange.defaultFor).
			builder.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_MODIFIER_ID, reachBonus, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		}

		return builder.build();
	}
}
