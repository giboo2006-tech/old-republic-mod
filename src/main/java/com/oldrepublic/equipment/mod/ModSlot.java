package com.oldrepublic.equipment.mod;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** A gear mod slot (spec MILESTONE_4.md 6.3). Stored by {@link #id()}; append only. */
public enum ModSlot {
	ARMOUR,
	/** The chest piece's second slot; only the jet pack fits it. */
	PACK,
	BARREL,
	CELL,
	EDGE;

	public static final Codec<ModSlot> CODEC = Codec.STRING.comapFlatMap(s -> {
		ModSlot slot = byId(s);
		return slot != null ? DataResult.success(slot) : DataResult.error(() -> "Unknown mod slot " + s);
	}, ModSlot::id);

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable ModSlot byId(String id) {
		for (ModSlot slot : values()) {
			if (slot.id().equals(id)) {
				return slot;
			}
		}

		return null;
	}
}
