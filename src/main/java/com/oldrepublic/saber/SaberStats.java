package com.oldrepublic.saber;

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

	public static SaberStats of(Emitter emitter, Grip grip, Pommel pommel, SaberBalance b) {
		double damage = b.baseDamage;
		double speed = b.baseAttackSpeed;
		double reach = 0;
		float stamina = b.guardStamina;
		float parry = b.parryWindowTicks;

		switch (emitter) {
			case GUARDIAN -> {
				reach += b.guardianReach;
				speed += b.guardianSpeed;
			}
			case DUELIST -> {
				speed += b.duelistSpeed;
				damage += b.duelistDamage;
			}
		}

		switch (grip) {
			case WRAPPED -> stamina *= b.wrappedStaminaMultiplier;
			case RIDGED -> speed += b.ridgedSpeed;
		}

		switch (pommel) {
			case WEIGHTED -> {
				damage += b.weightedDamage;
				speed += b.weightedSpeed;
			}
			case BALANCED -> parry *= b.balancedParryMultiplier;
		}

		return new SaberStats(damage, Math.max(0.1, speed), reach, stamina, parry);
	}

	public static SaberStats of(SaberConfig config, SaberBalance b) {
		return of(config.emitter(), config.grip(), config.pommel(), b);
	}

	/** The attribute modifiers a saber carries while ignited or retracted (main hand only). */
	public ItemAttributeModifiers modifiers(boolean ignited, SaberBalance b) {
		double attackDamage = ignited ? damage : b.retractedDamage;
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage - PLAYER_BASE_DAMAGE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed - PLAYER_BASE_ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);

		if (ignited && reachBonus > 0) {
			// API-CHECK: 26.2 also has an AttackRange item component; this assumes melee reach still
			// follows the entity_interaction_range attribute for items without that component.
			builder.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_MODIFIER_ID, reachBonus, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		}

		return builder.build();
	}
}
