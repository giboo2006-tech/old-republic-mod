package com.oldrepublic.equipment.mod;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The mods fitted to a piece of gear (spec MILESTONE_4.md 6.3), the item component
 * {@code oldrepublic:mods}: slot to mod stack, at most {@link #MAX} entries. Immutable; the stacks
 * are copied in and out.
 */
public record FittedMods(Map<ModSlot, ItemStack> mods) {
	public static final int MAX = 3;
	public static final FittedMods NONE = new FittedMods(Map.of());

	public static final Codec<FittedMods> CODEC = Codec.unboundedMap(ModSlot.CODEC, ItemStack.CODEC).xmap(FittedMods::new, FittedMods::mods);

	public static final StreamCodec<RegistryFriendlyByteBuf, FittedMods> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public FittedMods {
		Map<ModSlot, ItemStack> copied = new EnumMap<>(ModSlot.class);

		for (Map.Entry<ModSlot, ItemStack> e : mods.entrySet()) {
			if (copied.size() < MAX && !e.getValue().isEmpty()) {
				copied.put(e.getKey(), e.getValue().copy());
			}
		}

		mods = java.util.Collections.unmodifiableMap(copied);
	}

	/** The mod in {@code slot}, copied, or {@link ItemStack#EMPTY}. */
	public ItemStack get(ModSlot slot) {
		ItemStack stack = mods.get(slot);
		return stack == null ? ItemStack.EMPTY : stack.copy();
	}

	/** This with {@code mod} in {@code slot} (an empty stack clears it). */
	public FittedMods with(ModSlot slot, ItemStack mod) {
		Map<ModSlot, ItemStack> next = new EnumMap<>(ModSlot.class);
		next.putAll(mods);

		if (mod.isEmpty()) {
			next.remove(slot);
		} else {
			next.put(slot, mod);
		}

		return new FittedMods(next);
	}
}
