package com.oldrepublic.registry;

import com.mojang.serialization.Codec;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.equipment.blaster.BlasterHeat;
import com.oldrepublic.equipment.mod.FittedMods;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/**
 * Milestone 4 item components that more than one feature reads or writes (spec MILESTONE_4.md
 * 12.1, 15.1). Components only one feature uses ({@code nav_data}) live with that feature.
 */
public final class M4Components {
	/** A blaster's heat (5.1); W4 reads and writes it. */
	public static final DataComponentType<BlasterHeat> BLASTER_HEAT = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("blaster_heat"),
			DataComponentType.<BlasterHeat>builder().persistent(BlasterHeat.CODEC).networkSynchronized(BlasterHeat.STREAM_CODEC).build());

	/** Fitted mods (6.3); W4 owns what they do, W5's bench fits them. */
	public static final DataComponentType<FittedMods> MODS = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("mods"),
			DataComponentType.<FittedMods>builder().persistent(FittedMods.CODEC).networkSynchronized(FittedMods.STREAM_CODEC).build());

	/**
	 * A crafted item's {@code Quality} ordinal (7.4: 0 Standard, 1 Fine, 2 Masterwork, 3 Legendary, MILESTONE_5.md 11.1); W5
	 * writes it, W4 reads it through {@code Crafting.quality}.
	 */
	public static final DataComponentType<Integer> QUALITY = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("quality"),
			DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 3)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/** The schematic a {@code schematic} item teaches (7.3); W5's item, W2's rewards and W6's loot set it. */
	public static final DataComponentType<Identifier> SCHEMATIC = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("schematic"),
			DataComponentType.<Identifier>builder().persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build());

	/** A named drop's id, such as {@code kaast_longshot} (8.6); W2 sets it, W4 reads it for stats and tooltips. */
	public static final DataComponentType<String> NAMED = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("named"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	static void init() {
	}

	private M4Components() {
	}
}
