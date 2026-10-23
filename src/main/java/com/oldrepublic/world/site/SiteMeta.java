package com.oldrepublic.world.site;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.world.site.far.FarSites;

import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * How the runtime placer sits a template in the ground (docs/MILESTONE_6.md 3.4,
 * docs/STRUCTURES.md 7): read from {@code data/<namespace>/site_meta/<template path>.json}, written by
 * the template's generator. Every field is optional; a template without a file gets {@link #DEFAULT}.
 *
 * @param buried       layers below the ground the player walks on (the feet layer)
 * @param maxSlope     the most the ground may vary under the footprint
 * @param clearance    blocks kept clear of other sites and built blocks around the footprint
 * @param supportDepth footings filled down to solid ground, at most this deep
 * @param rampRings    outer rings stepped down toward lower ground
 * @param clearAbove   trees and terrain removed above the template's top, this many blocks
 * @param terrain      the ground far placement prefers
 * @param biomes       a biome tag far placement keeps to, or null
 * @param spawnRadius  how close the owner comes before the site's people spawn
 * @param links        companion templates placed with this one and their origin offsets (unrotated)
 *
 * <p>Phase 0 contract (7.1): the record and the signatures of {@link #DEFAULT}, {@link #of} and
 * {@link #init} are frozen. The files load like the anchor sidecars ({@link SiteAnchors}): on server
 * start and on {@code /reload}, the template id being the file's id, so
 * {@code data/oldrepublic/site_meta/mission/fence_camp.json} describes {@code oldrepublic:mission/fence_camp}.
 */
public record SiteMeta(int buried, int maxSlope, int clearance, int supportDepth, int rampRings, int clearAbove,
		FarSites.Terrain terrain, @Nullable TagKey<Biome> biomes, int spawnRadius, Map<Identifier, BlockPos> links) {
	public static final SiteMeta DEFAULT = new SiteMeta(1, 4, 8, 8, 2, 6, FarSites.Terrain.ANY, null, 64, Map.of());

	/** The largest value an integer field may take (the generators' check uses the same bound). */
	private static final int MAX_FIELD = 256;
	private static final FileToIdConverter FILES = FileToIdConverter.json("site_meta");
	private static volatile Map<Identifier, SiteMeta> files = Map.of();
	/** Templates whose file gives {@code buried} itself (the others take the default, which a caller may override). */
	private static volatile Set<Identifier> buriedGiven = Set.of();

	public SiteMeta {
		links = Map.copyOf(links);
	}

	/** The meta of a template: its file's values over the defaults, or {@link #DEFAULT} when it has no file. */
	public static SiteMeta of(Identifier template) {
		SiteMeta meta = files.get(template);
		return meta != null ? meta : DEFAULT;
	}

	/** The meta of a template's file, or null when it has none (so a caller can keep its own numbers). */
	public static @Nullable SiteMeta find(Identifier template) {
		return files.get(template);
	}

	/**
	 * The layers a template sets into the ground: its file's {@code buried} when the file gives one,
	 * else {@code fallback} (the caller's constant from before site meta).
	 */
	public static int buried(Identifier template, int fallback) {
		SiteMeta meta = files.get(template);
		return meta != null && buriedGiven.contains(template) ? meta.buried() : fallback;
	}

	/** Registers the loader (server start and {@code /reload}); called from {@code StructuresFeature}. */
	public static void init() {
		DataResourceLoader.get().registerReloadListener(OldRepublicMod.id("site_meta"), (ResourceManagerReloadListener) SiteMeta::load);
	}

	/** The link offset to {@code companion} (unrotated, in this template's frame), or null when the file names none. */
	public @Nullable BlockPos link(Identifier companion) {
		return links.get(companion);
	}

	private static void load(ResourceManager manager) {
		Map<Identifier, SiteMeta> loaded = new HashMap<>();
		Set<Identifier> withBuried = new HashSet<>();

		for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(manager).entrySet()) {
			Identifier template = FILES.fileToId(entry.getKey());

			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				loaded.put(template, parse(template, json));

				if (json.getAsJsonObject().has("buried")) {
					withBuried.add(template);
				}
			} catch (IOException | JsonParseException | IllegalStateException | NumberFormatException | UnsupportedOperationException e) {
				OldRepublicMod.LOGGER.error("Skipping site meta {} ({}): {}", template, entry.getKey(), e.getMessage());
			}
		}

		files = Map.copyOf(loaded);
		buriedGiven = Set.copyOf(withBuried);
		OldRepublicMod.LOGGER.info("Loaded site meta for {} templates", loaded.size());
	}

	/**
	 * Reads one file (the loader, and the tests): each field present replaces the default, unknown
	 * fields are logged and skipped, and a malformed value (a number out of 0 to 256, an unknown
	 * terrain, a biome that is not a {@code #tag}, a link without three whole numbers) fails the file.
	 */
	public static SiteMeta parse(Identifier template, JsonElement json) {
		JsonObject object = json.getAsJsonObject();
		int buried = DEFAULT.buried();
		int maxSlope = DEFAULT.maxSlope();
		int clearance = DEFAULT.clearance();
		int supportDepth = DEFAULT.supportDepth();
		int rampRings = DEFAULT.rampRings();
		int clearAbove = DEFAULT.clearAbove();
		FarSites.Terrain terrain = DEFAULT.terrain();
		TagKey<Biome> biomes = DEFAULT.biomes();
		int spawnRadius = DEFAULT.spawnRadius();
		Map<Identifier, BlockPos> links = new HashMap<>();

		for (Map.Entry<String, JsonElement> field : object.entrySet()) {
			JsonElement value = field.getValue();

			switch (field.getKey()) {
				case "buried" -> buried = whole(field.getKey(), value);
				case "max_slope" -> maxSlope = whole(field.getKey(), value);
				case "clearance" -> clearance = whole(field.getKey(), value);
				case "support_depth" -> supportDepth = whole(field.getKey(), value);
				case "ramp_rings" -> rampRings = whole(field.getKey(), value);
				case "clear_above" -> clearAbove = whole(field.getKey(), value);
				case "spawn_radius" -> spawnRadius = whole(field.getKey(), value);
				case "terrain" -> terrain = terrain(value.getAsString());
				case "biomes" -> biomes = biomeTag(value.getAsString());
				case "links" -> {
					for (Map.Entry<String, JsonElement> link : value.getAsJsonObject().entrySet()) {
						JsonArray xyz = link.getValue().getAsJsonArray();

						if (xyz.size() != 3) {
							throw new JsonParseException("link " + link.getKey() + " needs three coordinates");
						}

						links.put(id(link.getKey()), new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
					}
				}
				default -> OldRepublicMod.LOGGER.warn("Site meta {} has an unknown field {}", template, field.getKey());
			}
		}

		return new SiteMeta(buried, maxSlope, clearance, supportDepth, rampRings, clearAbove, terrain, biomes, spawnRadius, links);
	}

	private static int whole(String name, JsonElement value) {
		int number = value.getAsInt();

		if (number < 0 || number > MAX_FIELD || value.getAsDouble() != number) {
			throw new JsonParseException(name + " must be a whole number from 0 to " + MAX_FIELD + ", not " + value);
		}

		return number;
	}

	private static FarSites.Terrain terrain(String name) {
		for (FarSites.Terrain terrain : FarSites.Terrain.values()) {
			if (terrain.name().toLowerCase(Locale.ROOT).equals(name)) {
				return terrain;
			}
		}

		throw new JsonParseException("unknown terrain " + name);
	}

	private static Identifier id(String text) {
		Identifier id = Identifier.tryParse(text);

		if (id == null) {
			throw new JsonParseException("not an id: " + text);
		}

		return id;
	}

	private static TagKey<Biome> biomeTag(String text) {
		if (!text.startsWith("#")) {
			throw new JsonParseException("biomes must be a biome tag (\"#namespace:path\"), not " + text);
		}

		return TagKey.create(Registries.BIOME, id(text.substring(1)));
	}
}
