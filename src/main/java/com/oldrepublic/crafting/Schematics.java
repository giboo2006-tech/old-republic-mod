package com.oldrepublic.crafting;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

/**
 * The loaded schematics (spec MILESTONE_4.md 7.3). W5 adds the reload listener and the client
 * sync; until then the set is empty. Server and client each hold their own copy.
 */
public final class Schematics {
	private static volatile Map<Identifier, Schematic> loaded = Map.of();

	public static @Nullable Schematic get(Identifier id) {
		return loaded.get(id);
	}

	/** Every schematic, in load order (read only). */
	public static Collection<Schematic> all() {
		return Collections.unmodifiableCollection(loaded.values());
	}

	/** Replaces the loaded set (the reload listener, the client sync and tests). */
	public static void replace(Collection<Schematic> schematics) {
		Map<Identifier, Schematic> next = new LinkedHashMap<>();

		for (Schematic s : schematics) {
			next.put(s.id(), s);
		}

		loaded = Collections.unmodifiableMap(next);
	}

	private Schematics() {
	}
}
