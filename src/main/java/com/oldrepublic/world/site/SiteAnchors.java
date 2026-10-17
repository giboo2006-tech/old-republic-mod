package com.oldrepublic.world.site;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Named cells of the site templates (spec section 3: "anchors are not blocks"). Each template's
 * generator writes the real block in every anchor cell and a sidecar
 * {@code data/<namespace>/site_anchors/<template path>.json} of the form
 * {@code {"HEART": [[x, y, z], ...], ...}} in template-local coordinates. The sidecars load on the
 * server (start and {@code /reload}); the template id is the sidecar's id, so
 * {@code data/oldrepublic/site_anchors/jedi_ruin/listening_hall.json} describes
 * {@code oldrepublic:jedi_ruin/listening_hall}.
 */
public final class SiteAnchors {
	/**
	 * Every anchor name of the frozen template table (spec 15.1), in the order the table first names
	 * them. Sidecars refer to anchors by these names.
	 */
	public enum Anchor {
		STAIR_MOUTH, SURFACE_GUARD, DIGGERS, CHEST_1,
		HEART, PEDESTAL_JEDI, COUNTERWEIGHT_W, COUNTERWEIGHT_E, COUNTERWEIGHT_S, SENTINEL_1, SENTINEL_2,
		PEDESTAL_WAYFARER, CACHE, SQUAD,
		VENT_1, VENT_2, VENT_3, VENT_4, NICHE,
		URN_KEEPER, URN_1, URN_2, URN_3, URN_4, PEDESTAL_SITH,
		HATCH, COUNTERWEIGHT, DECK_CACHE, ANCHOR_LOCKER, SCAVENGER_1, SCAVENGER_2,
		TERMINAL, OFFICER, GARRISON_1, GARRISON_2, GARRISON_3, GARRISON_4,
		PEDESTAL, GUARD,
		STRONGBOX,
		DOWNED_1, DOWNED_2, DOWNED_3, SENTINEL_3, CRATE,
		CREW_1, CREW_2, CREW_3, CREW_4, CHIEF, FENCE, BOOK, FIRE,
		SQUAD_1, SQUAD_2, SQUAD_3, SQUAD_4, SQUAD_5, WOUNDED,
		DESCENT_TOP,
		BED, THRESHOLD, DOOR_SEAL, LECTERN, DAIS, PYLON_1, PYLON_2, PYLON_3, PYLON_4, GALLERY_W, GALLERY_E, SENTINEL_4,
		COLUMN_1, COLUMN_2, COLUMN_3, COLUMN_4, COLUMN_5, COLUMN_6, DEBRIS_1, DEBRIS_2, DEBRIS_3, DEBRIS_4, PLINTH, VAULT_SEAL;

		/** The anchor with this sidecar name, or null. */
		public static @Nullable Anchor byName(String name) {
			for (Anchor anchor : values()) {
				if (anchor.name().equals(name)) {
					return anchor;
				}
			}

			return null;
		}
	}

	private static final FileToIdConverter FILES = FileToIdConverter.json("site_anchors");
	private static volatile Map<Identifier, Map<Anchor, List<BlockPos>>> sidecars = Map.of();

	static void init() {
		DataResourceLoader.get().registerReloadListener(OldRepublicMod.id("site_anchors"), (ResourceManagerReloadListener) SiteAnchors::load);
	}

	/** Every position of {@code anchor} in {@code template}, template-local and in sidecar order; empty when there is none. */
	public static List<BlockPos> of(Identifier template, Anchor anchor) {
		Map<Anchor, List<BlockPos>> anchors = sidecars.get(template);
		return anchors == null ? List.of() : anchors.getOrDefault(anchor, List.of());
	}

	/** The first position of {@code anchor} in this piece, in world space, or null when its template has none. */
	public static @Nullable BlockPos find(SitePiece piece, Anchor anchor) {
		List<BlockPos> local = of(piece.template(), anchor);
		return local.isEmpty() ? null : toWorld(piece, local.getFirst());
	}

	/** Every position of {@code anchor} in this piece, in world space and in sidecar order. */
	public static List<BlockPos> all(SitePiece piece, Anchor anchor) {
		List<BlockPos> local = of(piece.template(), anchor);
		List<BlockPos> out = new ArrayList<>(local.size());

		for (BlockPos pos : local) {
			out.add(toWorld(piece, pos));
		}

		return out;
	}

	/**
	 * A template-local position in world space: the piece's rotation about {@link BlockPos#ZERO}, no
	 * mirror, then its origin. This is what {@code StructureTemplate} placement does with the
	 * settings worldgen pieces and {@link RuntimeSites} use.
	 */
	public static BlockPos toWorld(SitePiece piece, BlockPos local) {
		return piece.origin().offset(StructureTemplate.transform(local, Mirror.NONE, piece.rotation(), BlockPos.ZERO));
	}

	/** Every template that has a sidecar (for the template checks in the world tests). */
	public static Set<Identifier> templates() {
		return sidecars.keySet();
	}

	private static void load(ResourceManager manager) {
		Map<Identifier, Map<Anchor, List<BlockPos>>> loaded = new HashMap<>();

		for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(manager).entrySet()) {
			Identifier template = FILES.fileToId(entry.getKey());

			try (Reader reader = entry.getValue().openAsReader()) {
				loaded.put(template, parse(template, JsonParser.parseReader(reader)));
			} catch (IOException | JsonParseException | IllegalStateException | NumberFormatException | UnsupportedOperationException e) {
				OldRepublicMod.LOGGER.error("Skipping site anchor sidecar {} ({}): {}", template, entry.getKey(), e.getMessage());
			}
		}

		sidecars = Map.copyOf(loaded);
		OldRepublicMod.LOGGER.info("Loaded site anchors for {} templates", loaded.size());
	}

	/**
	 * Reads one sidecar. Unknown anchor names are logged and skipped; a malformed position fails the
	 * whole file.
	 */
	static Map<Anchor, List<BlockPos>> parse(Identifier template, JsonElement json) {
		Map<Anchor, List<BlockPos>> anchors = new EnumMap<>(Anchor.class);

		for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
			Anchor anchor = Anchor.byName(entry.getKey());

			if (anchor == null) {
				OldRepublicMod.LOGGER.warn("Site anchor sidecar {} names an unknown anchor {}", template, entry.getKey());
				continue;
			}

			List<BlockPos> positions = new ArrayList<>();

			for (JsonElement element : entry.getValue().getAsJsonArray()) {
				JsonArray xyz = element.getAsJsonArray();

				if (xyz.size() != 3) {
					throw new JsonParseException("anchor " + anchor + " has a position without three coordinates");
				}

				positions.add(new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
			}

			anchors.put(anchor, List.copyOf(positions));
		}

		return anchors;
	}

	private SiteAnchors() {
	}
}
