package com.oldrepublic.equipment;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * The merged vanilla attributes of one era armour piece (spec section 9.2): armour, toughness,
 * knockback resistance and, on trooper pieces, movement speed, all in one
 * {@link ItemAttributeModifiers}, because {@code Item.Properties#attributes} replaces what
 * {@code humanoidArmor} set. Every modifier id is per attribute and slot
 * ({@code oldrepublic:armour.<slot>.<attribute>}), so four pieces add four modifiers instead of
 * collapsing into one.
 */
public final class ArmourAttributes {
	/** The transient Iron Discipline modifier while the full Sith trooper set is worn. */
	public static final Identifier SET_KNOCKBACK = OldRepublicMod.id("armour.set.knockback");

	/** {@code oldrepublic:armour.<head|chest|legs|feet>.<attribute>}. */
	public static Identifier id(EquipmentSlot slot, String attribute) {
		return OldRepublicMod.id("armour." + slot.getName() + "." + attribute);
	}

	/**
	 * The merged modifiers of the {@code type} piece of {@code material}. Zero toughness and knockback
	 * entries are left out; {@code speedPct} (whole percent, 0 for none) becomes an
	 * {@code ADD_MULTIPLIED_BASE} movement speed modifier.
	 */
	public static ItemAttributeModifiers build(ArmorMaterial material, ArmorType type, float speedPct) {
		EquipmentSlot slot = type.getSlot();
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		int armour = material.defense().getOrDefault(type, 0);
		builder.add(Attributes.ARMOR, new AttributeModifier(id(slot, "armor"), armour, AttributeModifier.Operation.ADD_VALUE), group);

		if (material.toughness() != 0f) {
			builder.add(Attributes.ARMOR_TOUGHNESS,
					new AttributeModifier(id(slot, "toughness"), material.toughness(), AttributeModifier.Operation.ADD_VALUE), group);
		}

		if (material.knockbackResistance() != 0f) {
			builder.add(Attributes.KNOCKBACK_RESISTANCE,
					new AttributeModifier(id(slot, "knockback"), material.knockbackResistance(), AttributeModifier.Operation.ADD_VALUE), group);
		}

		if (speedPct != 0f) {
			builder.add(Attributes.MOVEMENT_SPEED,
					new AttributeModifier(id(slot, "speed"), speedPct / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
		}

		return builder.build();
	}

	private ArmourAttributes() {
	}
}
