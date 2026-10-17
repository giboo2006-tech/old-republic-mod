package com.oldrepublic.mission;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Loads every {@code data/oldrepublic/mission/*.json} on server start and on {@code /reload} (spec
 * 5.7). A file that fails to parse or validate is logged and skipped; the others still load. The
 * mission id is the file's path ({@code r1_silent_patrol}); only the {@code oldrepublic} namespace
 * is read, so a datapack can override a mission but not add a second namespace's copy.
 */
public final class MissionLoader implements ResourceManagerReloadListener {
	private static final FileToIdConverter FILES = FileToIdConverter.json("mission");
	private static volatile Map<String, MissionDefinition> missions = Map.of();

	static void init() {
		DataResourceLoader.get().registerReloadListener(OldRepublicMod.id("mission"), new MissionLoader());
	}

	/** The mission with this id, or null. */
	public static @Nullable MissionDefinition get(String id) {
		return missions.get(id);
	}

	/** Every loaded mission, in id order. */
	public static Collection<MissionDefinition> all() {
		return missions.values();
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<String, MissionDefinition> loaded = new TreeMap<>();

		for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(manager).entrySet()) {
			Identifier id = FILES.fileToId(entry.getKey());

			if (!id.getNamespace().equals(OldRepublicMod.MOD_ID)) {
				continue;
			}

			try (Reader reader = entry.getValue().openAsReader()) {
				loaded.put(id.getPath(), MissionDefinition.parse(id.getPath(), JsonParser.parseReader(reader)));
			} catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException | NumberFormatException e) {
				OldRepublicMod.LOGGER.error("Skipping mission {} ({}): {}", id, entry.getKey(), e.getMessage());
			}
		}

		missions = Collections.unmodifiableMap(loaded);
		OldRepublicMod.LOGGER.info("Loaded {} missions", loaded.size());
	}
}
