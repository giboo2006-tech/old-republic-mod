package com.oldrepublic.crafting;

import java.util.List;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.item.FusionCutterItem;
import com.oldrepublic.crafting.item.GrenadeItem;
import com.oldrepublic.crafting.item.HealItem;
import com.oldrepublic.crafting.item.RepairKitItem;
import com.oldrepublic.crafting.item.ScannerItem;
import com.oldrepublic.crafting.item.SchematicItem;
import com.oldrepublic.crafting.item.StimItem;
import com.oldrepublic.crafting.item.StunGrenade;
import com.oldrepublic.crafting.item.Consumables.Stim;
import com.oldrepublic.registry.ModRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Items and entity types of the crafting feature (spec MILESTONE_4.md 7.6, 16.1), in creative tab
 * order. {@code alloy_plate} and {@code masterwork_fragment} are the lead's {@code M4Items}.
 */
public final class CraftingItems {
	public static final SchematicItem SCHEMATIC = ModRegistry.item("schematic", SchematicItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final HealItem MEDPAC = ModRegistry.item("medpac", p -> new HealItem(p, false), new Item.Properties().stacksTo(16));
	public static final HealItem KOLTO_INJECTOR = ModRegistry.item("kolto_injector", p -> new HealItem(p, true),
			new Item.Properties().stacksTo(8).rarity(Rarity.UNCOMMON));
	public static final StimItem COMBAT_STIM = ModRegistry.item("combat_stim", p -> new StimItem(p, Stim.COMBAT), new Item.Properties().stacksTo(8));
	public static final StimItem FOCUS_STIM = ModRegistry.item("focus_stim", p -> new StimItem(p, Stim.FOCUS), new Item.Properties().stacksTo(8));
	public static final FusionCutterItem FUSION_CUTTER = ModRegistry.item("fusion_cutter", FusionCutterItem::new,
			new Item.Properties().durability(CraftingBalance.get().cutterDurability));
	public static final ScannerItem SCANNER = ModRegistry.item("scanner", ScannerItem::new, new Item.Properties().stacksTo(1));
	public static final GrenadeItem FRAG_GRENADE = ModRegistry.item("frag_grenade", p -> new GrenadeItem(p, false), new Item.Properties().stacksTo(4));
	public static final GrenadeItem STUN_GRENADE = ModRegistry.item("stun_grenade", p -> new GrenadeItem(p, true), new Item.Properties().stacksTo(4));
	public static final RepairKitItem REPAIR_KIT = ModRegistry.item("repair_kit", RepairKitItem::new,
			new Item.Properties().durability(CraftingBalance.get().repairKitUses));

	private static final ResourceKey<EntityType<?>> STUN_GRENADE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("stun_grenade"));

	/** A thrown stun grenade (never saved). */
	public static final EntityType<StunGrenade> STUN_GRENADE_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, STUN_GRENADE_KEY,
			EntityType.Builder.<StunGrenade>of(StunGrenade::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(4)
					.updateInterval(10)
					.noSave()
					.build(STUN_GRENADE_KEY));

	/** The schematics taught by items (7.3); the creative tab shows one schematic item each. */
	private static final List<String> ITEM_SCHEMATICS = List.of(
			"watch_battle_helmet", "watch_battle_chest", "watch_battle_legs", "watch_battle_boots",
			"sith_war_helmet", "sith_war_chest", "sith_war_legs", "sith_war_boots",
			"mandalorian_helmet", "mandalorian_chest", "mandalorian_legs", "mandalorian_boots");

	static void init() {
		for (String id : ITEM_SCHEMATICS) {
			ModRegistry.tabEntry(() -> SchematicItem.of(OldRepublicMod.id(id)));
		}
	}

	private CraftingItems() {
	}
}
