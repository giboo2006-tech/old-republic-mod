package com.oldrepublic.equipment;

import com.oldrepublic.crafting.Quality;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModRegistry;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;

/**
 * The era armour items: the 18 of Milestone 3 (spec sections 9.1 and 16: four pieces of each set
 * plus the rank 3 {@code jedi_knight_robe} and {@code sith_lord_robe}) and the 18 of Milestone 4
 * (MILESTONE_4.md 6.2 and 16.1: four pieces of each new set plus Kaast's named Masterwork helmet and
 * chest). Each is built with {@code humanoidArmor(material, type)}, then {@code attributes(merged)}
 * (which replaces the material's attributes) and the {@code equipment_traits} component. The traits
 * hold the registration defaults as fractions; the live numbers are applied by
 * {@link EquipmentModifiers}.
 *
 * <p>Milestone 5 adds the rank 4 pieces (MILESTONE_5.md 8.5), each a named chest piece that counts as
 * its set's chest: {@code jedi_master_robe} (Jedi robes), {@code sith_darth_robe} (Sith robes) and
 * {@code wayfarer_mantle} (the spacer's jacket), with one more armour than the piece they stand for;
 * and the {@code cantors_mask} (12.3), a cosmetic head piece with no armour. None is crafted or
 * salvaged (the {@code named} component refuses both, and no schematic or recipe makes them); W5's
 * ceremonies give the robes and W8's raid the mask.
 */
public final class EquipmentItems {
	private static final EquipmentBalance DEFAULTS = new EquipmentBalance();
	private static final EquipmentM4Balance M4_DEFAULTS = new EquipmentM4Balance();

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

	public static final ArmourItem WATCH_BATTLE_HELMET = plate("watch_battle_helmet", ArmourSet.WATCH_BATTLE, ArmorType.HELMET);
	public static final ArmourItem WATCH_BATTLE_CHEST = plate("watch_battle_chest", ArmourSet.WATCH_BATTLE, ArmorType.CHESTPLATE);
	public static final ArmourItem WATCH_BATTLE_LEGS = plate("watch_battle_legs", ArmourSet.WATCH_BATTLE, ArmorType.LEGGINGS);
	public static final ArmourItem WATCH_BATTLE_BOOTS = plate("watch_battle_boots", ArmourSet.WATCH_BATTLE, ArmorType.BOOTS);

	public static final ArmourItem SITH_WAR_HELMET = plate("sith_war_helmet", ArmourSet.SITH_WAR, ArmorType.HELMET);
	public static final ArmourItem SITH_WAR_CHEST = plate("sith_war_chest", ArmourSet.SITH_WAR, ArmorType.CHESTPLATE);
	public static final ArmourItem SITH_WAR_LEGS = plate("sith_war_legs", ArmourSet.SITH_WAR, ArmorType.LEGGINGS);
	public static final ArmourItem SITH_WAR_BOOTS = plate("sith_war_boots", ArmourSet.SITH_WAR, ArmorType.BOOTS);

	public static final ArmourItem MANDALORIAN_HELMET = plate("mandalorian_helmet", ArmourSet.MANDALORIAN, ArmorType.HELMET);
	public static final ArmourItem MANDALORIAN_CHEST = plate("mandalorian_chest", ArmourSet.MANDALORIAN, ArmorType.CHESTPLATE);
	public static final ArmourItem MANDALORIAN_LEGS = plate("mandalorian_legs", ArmourSet.MANDALORIAN, ArmorType.LEGGINGS);
	public static final ArmourItem MANDALORIAN_BOOTS = plate("mandalorian_boots", ArmourSet.MANDALORIAN, ArmorType.BOOTS);

	public static final ArmourItem SPACER_CAP = plate("spacer_cap", ArmourSet.SPACER, ArmorType.HELMET);
	public static final ArmourItem SPACER_JACKET = plate("spacer_jacket", ArmourSet.SPACER, ArmorType.CHESTPLATE);
	public static final ArmourItem SPACER_TROUSERS = plate("spacer_trousers", ArmourSet.SPACER, ArmorType.LEGGINGS);
	public static final ArmourItem SPACER_BOOTS = plate("spacer_boots", ArmourSet.SPACER, ArmorType.BOOTS);

	/** Kaast's helmet (8.6 "Take his armour"): a named Masterwork Mandalorian helmet. */
	public static final ArmourItem KAAST_HELMET = kaast("kaast_helmet", ArmorType.HELMET);
	/** Kaast's chest plate: a named Masterwork Mandalorian chest. */
	public static final ArmourItem KAAST_CHEST = kaast("kaast_chest", ArmorType.CHESTPLATE);

	/** The Jedi Master's robe (MILESTONE_5.md 8.5): counts as the Jedi robe, 4 armour, a long cloak. */
	public static final ArmourItem JEDI_MASTER_ROBE = rankFour("jedi_master_robe", ArmourSet.JEDI_ROBES, EquipmentMaterials.JEDI_MASTER);
	/** The Darth's robe: counts as the Sith robe, 4 armour, a high-collared mantle. */
	public static final ArmourItem SITH_DARTH_ROBE = rankFour("sith_darth_robe", ArmourSet.SITH_ROBES, EquipmentMaterials.SITH_DARTH);
	/** Tessaly Arn's mantle, given to a Wayfarer: counts as the spacer's jacket, 5 armour. */
	public static final ArmourItem WAYFARER_MANTLE = rankFour("wayfarer_mantle", ArmourSet.SPACER, EquipmentMaterials.WAYFARER);
	/**
	 * The Cantor's Mask (MILESTONE_5.md 12.3, the First Cantor's first clear): worn on the head, no
	 * armour, never damaged (no durability), named so it is never salvaged or sold.
	 */
	public static final Item CANTORS_MASK = ModRegistry.item("cantors_mask", CantorsMaskItem::new, new Item.Properties()
			.stacksTo(1)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
					.setEquipSound(ModSounds.holder(ModSounds.ARMOUR_EQUIP_ROBE))
					.setAsset(EquipmentMaterials.CANTORS_MASK_ASSET)
					.build())
			.component(M4Components.NAMED, "cantors_mask")
			.rarity(Rarity.EPIC));

	/** Every Milestone 3 era armour item, in registration order. */
	public static final ArmourItem[] ALL = {
			REPUBLIC_TROOPER_HELMET, REPUBLIC_TROOPER_CHESTPLATE, REPUBLIC_TROOPER_LEGGINGS, REPUBLIC_TROOPER_BOOTS,
			SITH_TROOPER_HELMET, SITH_TROOPER_CHESTPLATE, SITH_TROOPER_LEGGINGS, SITH_TROOPER_BOOTS,
			JEDI_HOOD, JEDI_ROBE, JEDI_KNIGHT_ROBE, JEDI_TROUSERS, JEDI_BOOTS,
			SITH_HOOD, SITH_ROBE, SITH_LORD_ROBE, SITH_TROUSERS, SITH_BOOTS};
	/** Every Milestone 4 armour item, in registration order. */
	public static final ArmourItem[] M4_ARMOUR = {
			WATCH_BATTLE_HELMET, WATCH_BATTLE_CHEST, WATCH_BATTLE_LEGS, WATCH_BATTLE_BOOTS,
			SITH_WAR_HELMET, SITH_WAR_CHEST, SITH_WAR_LEGS, SITH_WAR_BOOTS,
			MANDALORIAN_HELMET, MANDALORIAN_CHEST, MANDALORIAN_LEGS, MANDALORIAN_BOOTS,
			SPACER_CAP, SPACER_JACKET, SPACER_TROUSERS, SPACER_BOOTS,
			KAAST_HELMET, KAAST_CHEST};
	/** The three rank 4 pieces (MILESTONE_5.md 8.5), in registration order; drawn by the robe renderer with the mask. */
	public static final ArmourItem[] M5_ARMOUR = {JEDI_MASTER_ROBE, SITH_DARTH_ROBE, WAYFARER_MANTLE};
	/** The chest pieces drawn with a cloth overlay on the robe model: the Watch tabard and the Sith war cape (6.1). */
	public static final ArmourItem[] CLOTHED_PLATE = {WATCH_BATTLE_CHEST, SITH_WAR_CHEST};
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
			default -> m4Traits(set);
		};
	}

	/** An M4 piece's registration-default traits: {@link SetBonusRules#numbers} as fractions, speed and fall in {@link EquipmentTraits.Extra}. */
	private static EquipmentTraits m4Traits(ArmourSet set) {
		SetBonusRules.PieceNumbers n = SetBonusRules.numbers(set, M4_DEFAULTS);
		return new EquipmentTraits(set, n.costPct() / 100f, n.regenPct() / 100f, n.maxEnergy(), -n.blasterPct() / 100f, -n.meleePct() / 100f,
				new EquipmentTraits.Extra(speedPct(set) / 100f, 0f, n.fallPct() / 100f));
	}

	/** An M4 set's per-piece movement speed change, whole percent (an attribute, so fixed at registration). */
	private static float speedPct(ArmourSet set) {
		return switch (set) {
			case MANDALORIAN -> M4_DEFAULTS.mandoSpeedPct;
			case SPACER -> M4_DEFAULTS.spacerSpeedPct;
			default -> 0f;
		};
	}

	/** The material of an M4 set. */
	private static ArmorMaterial m4Material(ArmourSet set) {
		return switch (set) {
			case WATCH_BATTLE -> EquipmentMaterials.WATCH_BATTLE;
			case SITH_WAR -> EquipmentMaterials.SITH_WAR;
			case MANDALORIAN -> EquipmentMaterials.MANDALORIAN;
			case SPACER -> EquipmentMaterials.SPACER;
			default -> throw new IllegalArgumentException("Not a Milestone 4 set: " + set);
		};
	}

	private static ArmourItem trooper(String name, ArmourSet set, ArmorType type) {
		ArmorMaterial material = set == ArmourSet.REPUBLIC_TROOPER ? EquipmentMaterials.REPUBLIC_TROOPER : EquipmentMaterials.SITH_TROOPER;
		return register(name, set, material, type, DEFAULTS.trooperSpeedPct, false);
	}

	private static ArmourItem robe(String name, ArmourSet set, ArmorMaterial material, ArmorType type, boolean rankRobe) {
		return register(name, set, material, type, 0f, rankRobe);
	}

	private static ArmourItem plate(String name, ArmourSet set, ArmorType type) {
		return register(name, set, m4Material(set), type, speedPct(set), false);
	}

	/**
	 * One of Kaast's pieces: the Mandalorian set at Masterwork, built in (defence, toughness and
	 * durability scaled as a Masterwork craft scales them, the {@code quality} and {@code named}
	 * components set), so a stack handed out by the story needs nothing more.
	 */
	private static ArmourItem kaast(String name, ArmorType type) {
		Quality q = Quality.MASTERWORK;
		ArmorMaterial material = EquipmentMaterials.KAAST;
		Item.Properties properties = new Item.Properties()
				.humanoidArmor(material, type)
				.durability(Math.round(type.getDurability(material.durability()) * q.durability()))
				.attributes(ArmourAttributes.build(material, type, speedPct(ArmourSet.MANDALORIAN), q.armour()))
				.component(ModComponents.EQUIPMENT_TRAITS, defaultTraits(ArmourSet.MANDALORIAN))
				.component(M4Components.QUALITY, q.ordinal())
				.component(M4Components.NAMED, name)
				.rarity(Rarity.EPIC);
		return ModRegistry.item(name, p -> new ArmourItem(p, ArmourSet.MANDALORIAN, type, false), properties);
	}

	/**
	 * A rank 4 chest piece (MILESTONE_5.md 8.5): {@code set}'s chest traits and attributes (the spacer's
	 * speed for the mantle) on {@code material}, named and epic.
	 */
	private static ArmourItem rankFour(String name, ArmourSet set, ArmorMaterial material) {
		Item.Properties properties = new Item.Properties()
				.humanoidArmor(material, ArmorType.CHESTPLATE)
				.attributes(ArmourAttributes.build(material, ArmorType.CHESTPLATE, speedPct(set)))
				.component(ModComponents.EQUIPMENT_TRAITS, defaultTraits(set))
				.component(M4Components.NAMED, name)
				.rarity(Rarity.EPIC);
		return ModRegistry.item(name, p -> new ArmourItem(p, set, ArmorType.CHESTPLATE, 4), properties);
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
