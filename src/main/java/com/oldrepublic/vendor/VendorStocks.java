package com.oldrepublic.vendor;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
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
 * Loads every {@code data/oldrepublic/vendor/*.json} on server start and {@code /reload} (spec
 * MILESTONE_4.md 7.8, 12.4: stock loads on the server only and reaches a client when its vendor
 * screen opens).
 *
 * <p>The reload keeps only each file's JSON: in 26.2 a data reload runs before item components are
 * bound at server start, so no {@code ItemStack} may be built there. A stock is parsed into
 * {@link VendorStock} on its first use after that (memoised, forgotten on the next reload). A file
 * that is not JSON is logged and skipped at the reload; a stock that fails to parse is logged once,
 * at its first use, and stays missing until the next reload.
 */
public final class VendorStocks implements ResourceManagerReloadListener {
	private static final FileToIdConverter FILES = FileToIdConverter.json("vendor");
	/** Each stock's JSON by id, as last loaded. */
	private static volatile Map<String, JsonElement> sources = Map.of();
	/** Stocks parsed since the last reload (empty when the stock failed to parse). */
	private static final Map<String, Optional<VendorStock>> PARSED = new ConcurrentHashMap<>();

	static void init() {
		DataResourceLoader.get().registerReloadListener(OldRepublicMod.id("vendor"), new VendorStocks());
	}

	/** The stock with this id ({@code watch_quartermaster} or {@code oldrepublic:watch_quartermaster}), or null. */
	public static @Nullable VendorStock get(String id) {
		Identifier key = Identifier.tryParse(id.contains(":") ? id : OldRepublicMod.MOD_ID + ":" + id);
		return key == null ? null : parsed(key.getPath());
	}

	/** Every stock that parses, in id order. */
	public static Collection<VendorStock> all() {
		List<VendorStock> out = new ArrayList<>();

		for (String id : sources.keySet()) {
			VendorStock stock = parsed(id);

			if (stock != null) {
				out.add(stock);
			}
		}

		return out;
	}

	/** The stock {@code id}, parsed on first use (never during a reload: item components may not be bound yet). */
	private static @Nullable VendorStock parsed(String id) {
		Optional<VendorStock> cached = PARSED.get(id);

		if (cached != null) {
			return cached.orElse(null);
		}

		JsonElement json = sources.get(id);

		if (json == null) {
			return null;
		}

		VendorStock stock = null;

		try {
			stock = VendorStock.parse(id, json);
		} catch (JsonParseException | IllegalStateException | UnsupportedOperationException | NumberFormatException e) {
			OldRepublicMod.LOGGER.error("Vendor stock {} does not parse: {}", id, e.getMessage());
		}

		PARSED.put(id, Optional.ofNullable(stock));
		return stock;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<String, JsonElement> loaded = new TreeMap<>();

		for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(manager).entrySet()) {
			Identifier id = FILES.fileToId(entry.getKey());

			if (!id.getNamespace().equals(OldRepublicMod.MOD_ID)) {
				continue;
			}

			try (Reader reader = entry.getValue().openAsReader()) {
				loaded.put(id.getPath(), JsonParser.parseReader(reader));
			} catch (IOException | JsonParseException e) {
				OldRepublicMod.LOGGER.error("Skipping vendor stock {} ({}): {}", id, entry.getKey(), e.getMessage());
			}
		}

		sources = Collections.unmodifiableMap(loaded);
		PARSED.clear();
		OldRepublicMod.LOGGER.info("Loaded {} vendor stocks (parsed on first use)", loaded.size());
	}
}
