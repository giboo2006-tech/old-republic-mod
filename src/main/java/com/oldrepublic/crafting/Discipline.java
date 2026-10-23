package com.oldrepublic.crafting;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** The four crafting disciplines (spec MILESTONE_4.md 7.2). Stored by id; append only. */
public enum Discipline {
	ARMSTECH,
	ARMOURCRAFT,
	ARTIFICE,
	MEDTECH;

	/** By {@link #id()}. */
	public static final Codec<Discipline> CODEC = Codec.STRING.comapFlatMap(s -> {
		Discipline d = byId(s);
		return d != null ? DataResult.success(d) : DataResult.error(() -> "Unknown discipline " + s);
	}, Discipline::id);

	/** The saved and data-file id: {@code armstech}, {@code armourcraft}, {@code artifice}, {@code medtech}. */
	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** The discipline with this id, or null (schematic files use {@code common} for none). */
	public static @Nullable Discipline byId(String id) {
		for (Discipline d : values()) {
			if (d.id().equals(id)) {
				return d;
			}
		}

		return null;
	}
}
