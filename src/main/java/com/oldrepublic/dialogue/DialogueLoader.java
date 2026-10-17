package com.oldrepublic.dialogue;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
 * Loads every {@code data/<namespace>/dialogue/*.json} on server start and on {@code /reload}. A
 * file that fails to parse or validate is logged and skipped; the others still load.
 */
public final class DialogueLoader implements ResourceManagerReloadListener {
	private static final FileToIdConverter FILES = FileToIdConverter.json("dialogue");
	private static volatile Map<Identifier, DialogueDefinition> dialogues = Map.of();

	static void init() {
		DataResourceLoader.get().registerReloadListener(OldRepublicMod.id("dialogue"), new DialogueLoader());
	}

	/** The dialogue with this id, or null. */
	public static @Nullable DialogueDefinition get(Identifier id) {
		return dialogues.get(id);
	}

	/** Every loaded dialogue id. */
	public static Collection<Identifier> ids() {
		return dialogues.keySet();
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<Identifier, DialogueDefinition> loaded = new HashMap<>();

		for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(manager).entrySet()) {
			Identifier id = FILES.fileToId(entry.getKey());

			try (Reader reader = entry.getValue().openAsReader()) {
				loaded.put(id, DialogueDefinition.parse(id, JsonParser.parseReader(reader)));
			} catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException e) {
				OldRepublicMod.LOGGER.error("Skipping dialogue {} ({}): {}", id, entry.getKey(), e.getMessage());
			}
		}

		dialogues = Map.copyOf(loaded);
		OldRepublicMod.LOGGER.info("Loaded {} dialogues", loaded.size());
	}
}
