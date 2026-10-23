package com.oldrepublic.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>When a release changes a default, the old one is listed in {@link #RETIRED}: a stored value still equal to it was
 * never tuned by hand, so it gives way to the new default and the file is rewritten; a tuned value stays. Fields a release
 * removed are listed in {@link #REMOVED} and dropped from the file.
 */
public final class BalanceConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(OldRepublicMod.MOD_ID).resolve("balance.json");
	private static JsonObject root = new JsonObject();
	private static final Map<String, Object> CACHE = new LinkedHashMap<>();
	private static boolean dirty;
	/** Defaults a release replaced, by section: the field and its old default (docs/MILESTONE_6.md 3.2, the far placement). */
	private static final Map<String, List<Retired>> RETIRED = Map.ofEntries(
			Map.entry("sites", List.of(
					new Retired("outpostWorldgenRadius", 640),
					new Retired("outpostReuseRadius", 512))),
			Map.entry("missions", List.of(
					new Retired("siteSpawnDistance", 48))),
			Map.entry("planets", List.of(
					new Retired("heartstoneRing", new int[] {80, 100, 120}),
					new Retired("expeditionRings", new int[] {300, 375, 450}),
					new Retired("cacheRings", new int[] {150, 200, 250}),
					new Retired("verseRings", new int[] {250, 350, 450}),
					new Retired("debtorCampRings", new int[] {200, 300, 400}),
					new Retired("detentionRings", new int[] {120, 160, 200}),
					new Retired("ridgeHubExclusion", 48.0))),
			Map.entry("ship", List.of(
					new Retired("cradleRange", new int[] {32, 64}))),
			Map.entry("travel", List.of(
					new Retired("overflowRings", new int[] {40, 48, 56}))),
			Map.entry("flight", List.of(
					new Retired("hubBayRange", 32))),
			Map.entry("chapter_three", List.of(
					new Retired("firstEchoRadius", 768))),
			Map.entry("chapter_five", List.of(
					new Retired("matriarchRange", new int[] {150, 250}))),
			// Milestone 7 (MILESTONE_7.md 9): the arrays gained the scatter blaster and the carbine. Doubles, as a stored
			// float reads back as its shortest decimal.
			Map.entry("blasters", List.of(
					new Retired("damage", new double[] {4.0, 5.0, 2.5}),
					new Retired("rate", new int[] {8, 10, 3}),
					new Retired("speed", new double[] {2.2, 2.6, 2.4}),
					new Retired("life", new int[] {40, 60, 50}),
					new Retired("hipSpread", new double[] {2.0, 2.5, 4.0}),
					new Retired("heatPerShot", new double[] {15, 14, 7}),
					new Retired("cooling", new double[] {2.5, 2.0, 1.5}),
					new Retired("overheatLock", new int[] {50, 60, 80}),
					new Retired("ventLock", new int[] {20, 25, 30}),
					new Retired("durability", new int[] {600, 800, 900}))));
	/** Fields a release removed, by section. */
	private static final Map<String, Set<String>> REMOVED = Map.of(
			"planets", Set.of("hubFootprint"));

	private record Retired(String field, JsonElement value) {
		Retired(String field, Object value) {
			this(field, GSON.toJsonTree(value));
		}
	}

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
			retire(name, stored.getAsJsonObject().deepCopy()).entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));

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

	/** {@code stored} without the fields that still hold a retired default or that a release removed ({@link #RETIRED}). */
	private static JsonObject retire(String name, JsonObject stored) {
		for (Retired retired : RETIRED.getOrDefault(name, List.of())) {
			JsonElement value = stored.get(retired.field());

			if (value != null && numbersEqual(value, retired.value())) {
				stored.remove(retired.field());
			}
		}

		REMOVED.getOrDefault(name, Set.of()).forEach(stored::remove);
		return stored;
	}

	/** JSON equality that reads numbers by value, so a stored {@code 48} matches a retired {@code 48.0}. */
	private static boolean numbersEqual(JsonElement a, JsonElement b) {
		if (a.isJsonPrimitive() && b.isJsonPrimitive() && a.getAsJsonPrimitive().isNumber() && b.getAsJsonPrimitive().isNumber()) {
			return a.getAsDouble() == b.getAsDouble();
		}

		if (a.isJsonArray() && b.isJsonArray()) {
			if (a.getAsJsonArray().size() != b.getAsJsonArray().size()) {
				return false;
			}

			for (int i = 0; i < a.getAsJsonArray().size(); i++) {
				if (!numbersEqual(a.getAsJsonArray().get(i), b.getAsJsonArray().get(i))) {
					return false;
				}
			}

			return true;
		}

		return a.equals(b);
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
