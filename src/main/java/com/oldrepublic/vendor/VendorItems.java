package com.oldrepublic.vendor;

import com.oldrepublic.registry.ModRegistry;
import com.oldrepublic.vendor.block.ContractTerminalBlock;
import com.oldrepublic.vendor.block.MarkerBeaconBlock;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Items and blocks of the story and economy (spec MILESTONE_4.md 16.1): Kaast's armour pattern,
 * the tomb relic, the sealed contract keepsake, Kaast's marker beacon and the hubs' contract
 * terminal; Milestone 5 adds Ulvaine's writ (MILESTONE_5.md 18.1). ({@code credit_chit} and
 * {@code credit_bundle} are the lead's, in {@code M4Items}.)
 */
public final class VendorItems {
	/** Kaast's armour pattern (spared, 8.6): counts as a Mandalorian armour schematic for a trainer's commission (7.8). */
	public static final Item KAAST_PATTERN = ModRegistry.item("kaast_pattern", KeepsakeItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	/** The relic the Proving and Both Doors send the player into a tomb for (8.4). */
	public static final Item TOMB_RELIC = ModRegistry.item("tomb_relic", KeepsakeItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** The contract on the player's head, taken from Kaast's droid (8.5): a keepsake, never needed by a step. */
	public static final Item SEALED_CONTRACT = ModRegistry.item("sealed_contract", KeepsakeItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/**
	 * The Dark Council's writ for binding Darth Ulvaine as a Sith (spec MILESTONE_5.md 6.7): a keepsake
	 * that counts as a deed for the Darth ceremony (8.5). The deed is the story flag {@code c5.writ}, so
	 * losing the writ loses nothing.
	 */
	public static final Item ULVAINES_WRIT = ModRegistry.item("ulvaines_writ", KeepsakeItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	/** Kaast's challenge (8.6): a scrap post with a blinking lamp, placed near the player. */
	public static final MarkerBeaconBlock MARKER_BEACON = ModRegistry.block("marker_beacon", MarkerBeaconBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6f).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 3));
	/** A hub's planet contract board (3.5, 8.7). Unbreakable in survival, like the M3 terminals' site. */
	public static final ContractTerminalBlock CONTRACT_TERMINAL = ModRegistry.block("contract_terminal", ContractTerminalBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1f, 3600000f).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 5));

	/** Loads the class, registering everything above. */
	static void init() {
	}

	private VendorItems() {
	}
}
