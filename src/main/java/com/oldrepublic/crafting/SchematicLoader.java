package com.oldrepublic.crafting;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Loads every {@code data/<namespace>/schematic/*.json} on server start and {@code /reload} (spec
 * MILESTONE_4.md 7.3) into {@link Schematics}, and keeps the compact JSON of each one for the
 * {@code schematic_sync} payload. A file that names an item that is not registered (another
 * worker's item not merged yet, or a removed one) is skipped with a warning, so the rest still
 * load; a file that fails to parse is logged and skipped the same way.
 */
public final class SchematicLoader implements ResourceManagerReloadListener {
	public static final Identifier RELOADER_ID = OldRepublicMod.id("schematic");
	private static final FileToIdConverter FILES = FileToIdConverter.json("schematic");

	private static volatile Map<Identifier, String> serverJson = Map.of();

	static void init() {
		DataResourceLoader.get().registerReloadListener(RELOADER_ID, new SchematicLoader());
	}

	/** Compact JSON of every loaded schematic, by id, for the sync payload. */
	public static Map<Identifier, String> serverJson() {
		return serverJson;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<Identifier, String> texts = new LinkedHashMap<>();

		for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(manager).entrySet()) {
			Identifier id = FILES.fileToId(entry.getKey());

			try (Reader reader = entry.getValue().openAsReader()) {
				texts.put(id, JsonParser.parseReader(reader).toString());
			} catch (IOException | JsonParseException | IllegalStateException e) {
				OldRepublicMod.LOGGER.warn("Skipping schematic {}: {}", id, e.getMessage());
			}
		}

		Map<Identifier, String> kept = new LinkedHashMap<>();
		List<Schematic> parsed = parseAll(texts, kept);
		Schematics.replace(parsed);
		serverJson = Collections.unmodifiableMap(kept);
		OldRepublicMod.LOGGER.info("Loaded {} schematics ({} skipped)", parsed.size(), texts.size() - parsed.size());
	}

	/**
	 * Parses schematic texts (the server's files, or the sync payload on the client). Entries that
	 * fail are logged and left out; {@code kept} (when not null) receives the text of every entry
	 * that parsed.
	 */
	public static List<Schematic> parseAll(Map<Identifier, String> texts, @Nullable Map<Identifier, String> kept) {
		List<Schematic> parsed = new ArrayList<>();

		for (Map.Entry<Identifier, String> entry : texts.entrySet()) {
			Schematic schematic;

			try {
				schematic = parse(entry.getKey(), JsonParser.parseString(entry.getValue()));
			} catch (JsonParseException e) {
				OldRepublicMod.LOGGER.warn("Skipping schematic {}: {}", entry.getKey(), e.getMessage());
				continue;
			}

			if (schematic != null) {
				parsed.add(schematic);

				if (kept != null) {
					kept.put(entry.getKey(), entry.getValue());
				}
			}
		}

		return parsed;
	}

	/**
	 * One schematic from its JSON, or null (with a warning in the log) when it names an unknown
	 * item or does not parse.
	 */
	public static @Nullable Schematic parse(Identifier id, JsonElement json) {
		Optional<Identifier> unknown = unknownItem(json);

		if (unknown.isPresent()) {
			OldRepublicMod.LOGGER.warn("Skipping schematic {}: item {} is not registered", id, unknown.get());
			return null;
		}

		DataResult<Schematic> result = Schematic.fileCodec(id).parse(JsonOps.INSTANCE, json);
		Optional<Schematic> parsed = result.result();

		if (parsed.isEmpty()) {
			// Never the partial result: a list decode drops a bad input and keeps the rest, which would load a cheaper craft.
			result.resultOrPartial(error -> OldRepublicMod.LOGGER.warn("Skipping schematic {}: {}", id, error));
			return null;
		}

		return parsed.get();
	}

	/** The first {@code item} id in the file (an input or the output) that is not registered. */
	public static Optional<Identifier> unknownItem(JsonElement json) {
		if (!json.isJsonObject()) {
			return Optional.empty();
		}

		JsonObject object = json.getAsJsonObject();
		List<JsonElement> entries = new ArrayList<>();

		if (object.has("inputs") && object.get("inputs").isJsonArray()) {
			object.getAsJsonArray("inputs").forEach(entries::add);
		}

		if (object.has("output")) {
			entries.add(object.get("output"));
		}

		for (JsonElement entry : entries) {
			if (!entry.isJsonObject() || !entry.getAsJsonObject().has("item")) {
				continue;
			}

			Identifier item = Identifier.tryParse(entry.getAsJsonObject().get("item").getAsString());

			if (item != null && !BuiltInRegistries.ITEM.containsKey(item)) {
				return Optional.of(item);
			}
		}

		return Optional.empty();
	}
}
