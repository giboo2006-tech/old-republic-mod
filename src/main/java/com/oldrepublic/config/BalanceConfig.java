package com.oldrepublic.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oldrepublic.OldRepublicMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side balance values, stored in {@code config/oldrepublic/balance.json}. Each feature owns
 * one section: a plain Java class with public fields and default values. Missing sections and
 * fields are filled in from the defaults and written back, so the file always lists every value
 * that can be tuned.
 *
 * <pre>{@code
 * public final class PushBalance { public float baseRange = 6f; }
 * PushBalance push = BalanceConfig.section("force_push", PushBalance.class, PushBalance::new);
 * }</pre>
 *
 * Values are read once per server start ({@link #reload()} is called on server starting) and are
 * authoritative; clients never read this file.
 */
public final class BalanceConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(OldRepublicMod.MOD_ID).resolve("balance.json");
	private static JsonObject root = new JsonObject();
	private static final Map<String, Object> CACHE = new LinkedHashMap<>();
	private static boolean dirty;

	public static synchronized <T> T section(String name, Class<T> type, Supplier<T> defaults) {
		Object cached = CACHE.get(name);

		if (type.isInstance(cached)) {
			return type.cast(cached);
		}

		T value;
		JsonElement stored = root.get(name);
		JsonObject defaultsJson = GSON.toJsonTree(defaults.get()).getAsJsonObject();

		if (stored != null && stored.isJsonObject()) {
			JsonObject merged = defaultsJson.deepCopy();
			stored.getAsJsonObject().entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));

			try {
				value = GSON.fromJson(merged, type);
			} catch (RuntimeException e) {
				OldRepublicMod.LOGGER.error("Invalid balance section '{}', using defaults", name, e);
				value = defaults.get();
			}

			if (!merged.equals(stored)) {
				root.add(name, merged);
				dirty = true;
			}
		} else {
			value = defaults.get();
			root.add(name, defaultsJson);
			dirty = true;
		}

		CACHE.put(name, value);
		saveIfDirty();
		return value;
	}

	public static synchronized void reload() {
		CACHE.clear();

		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				root = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
			} catch (IOException | RuntimeException e) {
				OldRepublicMod.LOGGER.error("Could not read {}, using defaults", FILE, e);
				root = new JsonObject();
			}
		} else {
			root = new JsonObject();
		}
	}

	private static void saveIfDirty() {
		if (!dirty) {
			return;
		}

		dirty = false;

		try {
			Files.createDirectories(FILE.getParent());

			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			OldRepublicMod.LOGGER.error("Could not write {}", FILE, e);
		}
	}

	private BalanceConfig() {
	}
}
