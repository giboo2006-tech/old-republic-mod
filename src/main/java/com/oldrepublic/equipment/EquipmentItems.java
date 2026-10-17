package com.oldrepublic.equipment;

import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * The 18 era armour items (spec sections 9.1 and 16): four pieces of each set plus the rank 3
 * {@code jedi_knight_robe} and {@code sith_lord_robe}. Each is built with
 * {@code humanoidArmor(material, type)}, then {@code attributes(merged)} (which replaces the
 * material's attributes) and the {@code equipment_traits} component. The traits hold the
 * registration defaults as fractions; the live numbers are applied by {@link EquipmentModifiers}.
 */
public final class EquipmentItems {
	private static final EquipmentBalance DEFAULTS = new EquipmentBalance();

	public static final ArmourItem REPUBLIC_TROOPER_HELMET = trooper("republic_trooper_helmet", ArmourSet.REPUBLIC_TROOPER, ArmorType.HELMET);
	public static final ArmourItem REPUBLIC_TROOPER_CHESTPLATE = trooper("republic_trooper_chestplate", ArmourSet.REPUBLIC_TROOPER, ArmorType.CHESTPLATE);
	public static final ArmourItem REPUBLIC_TROOPER_LEGGINGS = trooper("republic_trooper_leggings", ArmourSet.REPUBLIC_TROOPER, ArmorType.LEGGINGS);
	public static final ArmourItem REPUBLIC_TROOPER_BOOTS = trooper("republic_trooper_boots", ArmourSet.REPUBLIC_TROOPER, ArmorType.BOOTS);

	public static final ArmourItem SITH_TROOPER_HELMET = trooper("sith_trooper_helmet", ArmourSet.SITH_TROOPER, ArmorType.HELMET);
	public static final ArmourItem SITH_TROOPER_CHESTPLATE = trooper("sith_trooper_chestplate", ArmourSet.SITH_TROOPER, ArmorType.CHESTPLATE);
	public static final ArmourItem SITH_TROOPER_LEGGINGS = trooper("sith_trooper_leggings", ArmourSet.SITH_TROOPER, ArmorType.LEGGINGS);
	public static final ArmourItem SITH_TROOPER_BOOTS = trooper("sith_trooper_boots", ArmourSet.SITH_TROOPER, ArmorType.BOOTS);

	public static final ArmourItem JEDI_HOOD = robe("jedi_hood", ArmourSet.JEDI_ROBES, EquipmentMaterials.JEDI_ROBES, ArmorType.HELMET, false);
	public static final ArmourItem JEDI_ROBE = robe("jedi_robe", ArmourSet.JEDI_ROBES, EquipmentMaterials.JEDI_ROBES, ArmorType.CHESTPLATE, false);
	public static final ArmourItem JEDI_KNIGHT_ROBE = robe("jedi_knight_robe", ArmourSet.JEDI_ROBES, EquipmentMaterials.JEDI_KNIGHT, ArmorType.CHESTPLATE, true);
	public static final ArmourItem JEDI_TROUSERS = robe("jedi_trousers", ArmourSet.JEDI_ROBES, EquipmentMaterials.JEDI_ROBES, ArmorType.LEGGINGS, false);
	public static final ArmourItem JEDI_BOOTS = robe("jedi_boots", ArmourSet.JEDI_ROBES, EquipmentMaterials.JEDI_ROBES, ArmorType.BOOTS, false);

	public static final ArmourItem SITH_HOOD = robe("sith_hood", ArmourSet.SITH_ROBES, EquipmentMaterials.SITH_ROBES, ArmorType.HELMET, false);
	public static final ArmourItem SITH_ROBE = robe("sith_robe", ArmourSet.SITH_ROBES, EquipmentMaterials.SITH_ROBES, ArmorType.CHESTPLATE, false);
	public static final ArmourItem SITH_LORD_ROBE = robe("sith_lord_robe", ArmourSet.SITH_ROBES, EquipmentMaterials.SITH_LORD, ArmorType.CHESTPLATE, true);
	public static final ArmourItem SITH_TROUSERS = robe("sith_trousers", ArmourSet.SITH_ROBES, EquipmentMaterials.SITH_ROBES, ArmorType.LEGGINGS, false);
	public static final ArmourItem SITH_BOOTS = robe("sith_boots", ArmourSet.SITH_ROBES, EquipmentMaterials.SITH_ROBES, ArmorType.BOOTS, false);

	/** Every era armour item, in registration order. */
	public static final ArmourItem[] ALL = {
			REPUBLIC_TROOPER_HELMET, REPUBLIC_TROOPER_CHESTPLATE, REPUBLIC_TROOPER_LEGGINGS, REPUBLIC_TROOPER_BOOTS,
			SITH_TROOPER_HELMET, SITH_TROOPER_CHESTPLATE, SITH_TROOPER_LEGGINGS, SITH_TROOPER_BOOTS,
			JEDI_HOOD, JEDI_ROBE, JEDI_KNIGHT_ROBE, JEDI_TROUSERS, JEDI_BOOTS,
			SITH_HOOD, SITH_ROBE, SITH_LORD_ROBE, SITH_TROUSERS, SITH_BOOTS};
	/** The ten robe items (the Fabric robe renderer's list). */
	public static final ArmourItem[] ROBES = {
			JEDI_HOOD, JEDI_ROBE, JEDI_KNIGHT_ROBE, JEDI_TROUSERS, JEDI_BOOTS,
			SITH_HOOD, SITH_ROBE, SITH_LORD_ROBE, SITH_TROUSERS, SITH_BOOTS};

	/** The registration-default traits of one piece of {@code set}, as fractions (spec 9.1). */
	public static EquipmentTraits defaultTraits(ArmourSet set) {
		EquipmentBalance b = DEFAULTS;
		float trooperCost = b.trooperCostPct / 100f;
		float trooperRegen = b.trooperRegenPct / 100f;
		return switch (set) {
			case REPUBLIC_TROOPER -> new EquipmentTraits(set, trooperCost, trooperRegen, 0, -b.republicBlasterPct / 100f, 0f);
			case SITH_TROOPER -> new EquipmentTraits(set, trooperCost, trooperRegen, 0, 0f, -b.sithMeleePct / 100f);
			case JEDI_ROBES -> new EquipmentTraits(set, b.jediCostPct / 100f, b.jediRegenPct / 100f, 0, 0f, 0f);
			case SITH_ROBES -> new EquipmentTraits(set, b.sithCostPct / 100f, 0f, b.sithMaxEnergy, 0f, 0f);
		};
	}

	private static ArmourItem trooper(String name, ArmourSet set, ArmorType type) {
		ArmorMaterial material = set == ArmourSet.REPUBLIC_TROOPER ? EquipmentMaterials.REPUBLIC_TROOPER : EquipmentMaterials.SITH_TROOPER;
		return register(name, set, material, type, DEFAULTS.trooperSpeedPct, false);
	}

	private static ArmourItem robe(String name, ArmourSet set, ArmorMaterial material, ArmorType type, boolean rankRobe) {
		return register(name, set, material, type, 0f, rankRobe);
	}

	private static ArmourItem register(String name, ArmourSet set, ArmorMaterial material, ArmorType type, float speedPct, boolean rankRobe) {
		Item.Properties properties = new Item.Properties()
				.humanoidArmor(material, type)
				.attributes(ArmourAttributes.build(material, type, speedPct))
				.component(ModComponents.EQUIPMENT_TRAITS, defaultTraits(set));
		return ModRegistry.item(name, p -> new ArmourItem(p, set, type, rankRobe), properties);
	}

	/** Loads the class, registering every item. */
	public static void init() {
	}

	private EquipmentItems() {
	}
}
