package com.oldrepublic.world.site;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One placed template of a site: a worldgen jigsaw piece or a runtime placement (spec 15.1).
 * {@code origin} and {@code rotation} are exactly what the template was placed with (rotation
 * pivot {@link BlockPos#ZERO}, no mirror), so {@link SiteAnchors#toWorld} maps template-local
 * positions the way {@code StructureTemplate} placement does. The box is a copy; do not change it.
 *
 * @param siteKey names the whole site: {@link #worldgenKey} (dimension and start chunk) for worldgen
 *                pieces, {@link #runtimeKey} (dimension and record origin) for runtime ones.
 *                {@code HolocronData.attuned_at} stores it.
 */
public record SitePiece(SiteKind kind, Identifier template, BoundingBox box, Rotation rotation, BlockPos origin, String siteKey) {
	private static final String WORLDGEN = "chunk";
	private static final String RUNTIME = "site";
	private static final String SEPARATOR = "|";

	public SitePiece {
		box = new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	/** The key of a worldgen site: {@code <dimension>|chunk|<x>|<z>} of its structure start chunk. */
	public static String worldgenKey(ResourceKey<Level> dimension, ChunkPos start) {
		return dimension.identifier() + SEPARATOR + WORLDGEN + SEPARATOR + start.x() + SEPARATOR + start.z();
	}

	/** The key of a runtime site: {@code <dimension>|site|<x>|<y>|<z>} of its {@link WorldSites.SiteRecord} origin. */
	public static String runtimeKey(ResourceKey<Level> dimension, BlockPos recordOrigin) {
		return dimension.identifier() + SEPARATOR + RUNTIME + SEPARATOR + recordOrigin.getX() + SEPARATOR + recordOrigin.getY() + SEPARATOR
				+ recordOrigin.getZ();
	}

	/** Whether this piece was placed at runtime (it has a {@link WorldSites} record) rather than by worldgen. */
	public boolean runtime() {
		String[] parts = siteKey.split("\\" + SEPARATOR);
		return parts.length == 5 && RUNTIME.equals(parts[1]);
	}

	/** The dimension named by {@link #siteKey}, or null when the key cannot be read. */
	public @Nullable ResourceKey<Level> dimension() {
		String[] parts = siteKey.split("\\" + SEPARATOR);
		Identifier id = parts.length >= 1 ? Identifier.tryParse(parts[0]) : null;
		return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
	}

	/** The structure start chunk of a worldgen piece, or null for a runtime piece or an unreadable key. */
	public @Nullable ChunkPos startChunk() {
		String[] parts = siteKey.split("\\" + SEPARATOR);

		if (parts.length != 4 || !WORLDGEN.equals(parts[1])) {
			return null;
		}

		try {
			return new ChunkPos(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
