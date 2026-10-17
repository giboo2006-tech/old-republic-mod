package com.oldrepublic.equipment;

import java.util.EnumMap;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * The armour materials of the era sets (spec section 9.1 and 9.4): one per set plus the two rank 3
 * robes, which share their set's stats and differ only in their equipment asset. Durability and
 * enchantability are the {@code new EquipmentBalance()} defaults, because materials are built at mod
 * init before the balance file is read.
 */
public final class EquipmentMaterials {
	/** Repair tag of both trooper sets ({@code durasteel_plate}). */
	public static final TagKey<Item> REPAIRS_TROOPER = TagKey.create(Registries.ITEM, OldRepublicMod.id("repairs/trooper_armour"));
	/** Repair tag of the Jedi robes ({@code lumen_thread}). */
	public static final TagKey<Item> REPAIRS_JEDI = TagKey.create(Registries.ITEM, OldRepublicMod.id("repairs/jedi_robes"));
	/** Repair tag of the Sith robes ({@code grave_silk}). */
	public static final TagKey<Item> REPAIRS_SITH = TagKey.create(Registries.ITEM, OldRepublicMod.id("repairs/sith_robes"));
	/** Cloth of the Jedi robe recipes (white, light grey or brown wool). */
	public static final TagKey<Item> JEDI_ROBE_CLOTH = TagKey.create(Registries.ITEM, OldRepublicMod.id("jedi_robe_cloth"));
	/** Cloth of the Sith robe recipes (black, grey or red wool). */
	public static final TagKey<Item> SITH_ROBE_CLOTH = TagKey.create(Registries.ITEM, OldRepublicMod.id("sith_robe_cloth"));

	public static final ResourceKey<EquipmentAsset> REPUBLIC_TROOPER_ASSET = asset("republic_trooper");
	public static final ResourceKey<EquipmentAsset> SITH_TROOPER_ASSET = asset("sith_trooper");
	public static final ResourceKey<EquipmentAsset> JEDI_ROBES_ASSET = asset("jedi_robes");
	public static final ResourceKey<EquipmentAsset> SITH_ROBES_ASSET = asset("sith_robes");
	public static final ResourceKey<EquipmentAsset> JEDI_KNIGHT_ASSET = asset("jedi_knight");
	public static final ResourceKey<EquipmentAsset> SITH_LORD_ASSET = asset("sith_lord");

	private static final EquipmentBalance DEFAULTS = new EquipmentBalance();
	/** Armour per piece of both trooper sets: helmet, chestplate, leggings, boots (15 in all). */
	private static final int[] TROOPER_ARMOUR = {2, 6, 5, 2};
	/** Armour per piece of both robe sets (7 in all). */
	private static final int[] ROBE_ARMOUR = {1, 3, 2, 1};

	public static final ArmorMaterial REPUBLIC_TROOPER = trooper(0.5f, 0f, REPUBLIC_TROOPER_ASSET);
	public static final ArmorMaterial SITH_TROOPER = trooper(1.0f, 0.05f, SITH_TROOPER_ASSET);
	public static final ArmorMaterial JEDI_ROBES = robe(REPAIRS_JEDI, JEDI_ROBES_ASSET);
	public static final ArmorMaterial SITH_ROBES = robe(REPAIRS_SITH, SITH_ROBES_ASSET);
	/** {@code jedi_knight_robe}: the Jedi robe stats with the Knight's asset. */
	public static final ArmorMaterial JEDI_KNIGHT = robe(REPAIRS_JEDI, JEDI_KNIGHT_ASSET);
	/** {@code sith_lord_robe}: the Sith robe stats with the Lord's asset. */
	public static final ArmorMaterial SITH_LORD = robe(REPAIRS_SITH, SITH_LORD_ASSET);

	/** The equipment asset key {@code oldrepublic:<name>} (never {@code EquipmentAssets.createId}, which is minecraft:). */
	public static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, OldRepublicMod.id(name));
	}

	private static ArmorMaterial trooper(float toughness, float knockback, ResourceKey<EquipmentAsset> asset) {
		return new ArmorMaterial(DEFAULTS.trooperDurability, defense(TROOPER_ARMOUR), DEFAULTS.trooperEnchant,
				ModSounds.holder(ModSounds.ARMOUR_EQUIP_TROOPER), toughness, knockback, REPAIRS_TROOPER, asset);
	}

	private static ArmorMaterial robe(TagKey<Item> repair, ResourceKey<EquipmentAsset> asset) {
		return new ArmorMaterial(DEFAULTS.robeDurability, defense(ROBE_ARMOUR), DEFAULTS.robeEnchant,
				ModSounds.holder(ModSounds.ARMOUR_EQUIP_ROBE), 0f, 0f, repair, asset);
	}

	/** An explicit defense map (helmet, chestplate, leggings, boots), so no vanilla helper's argument order matters. */
	private static Map<ArmorType, Integer> defense(int[] values) {
		Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
		map.put(ArmorType.HELMET, values[0]);
		map.put(ArmorType.CHESTPLATE, values[1]);
		map.put(ArmorType.LEGGINGS, values[2]);
		map.put(ArmorType.BOOTS, values[3]);
		return map;
	}

	private EquipmentMaterials() {
	}
}
