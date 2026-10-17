package com.oldrepublic.holocron;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * Where a pedestal stands (spec 4.3): its dimension and position, the site piece it belongs to, the
 * site key recorded in {@code attuned_at}, and the holocron the pedestal is fixed to, if any.
 *
 * @param site    the site piece, or null outside any known site
 * @param siteKey the piece's site key, or {@code <dim>|pedestal|x|y|z} outside any site
 * @param fixedId the only holocron this pedestal gives ({@code wayfarers_compass} at Courier's Fall), or null
 */
public record PedestalSite(ResourceKey<Level> dim, BlockPos pos, @Nullable SitePiece site, String siteKey, @Nullable String fixedId) {
	/** The pedestal block entity's saved key for its fixed holocron (spec 12.1). */
	private static final String FIXED_ID_KEY = "fixed_id";

	public PedestalSite {
		pos = pos.immutable();
	}

	/**
	 * The pedestal at {@code pos}: its site piece through {@link StructureLocator#pieceAt}, and the
	 * holocron it is fixed to. That is the {@code fixed_id} its block entity carries (spec 4.3, 12.1);
	 * a pedestal without one inside Courier's Fall still gives only the Compass.
	 */
	public static PedestalSite resolve(ServerLevel level, BlockPos pos) {
		SitePiece piece = StructureLocator.pieceAt(level, pos);
		String fixed = savedFixedId(level, pos);

		if (fixed == null && piece != null && piece.kind() == SiteKind.COURIERS_FALL) {
			fixed = HolocronCatalog.WAYFARERS_COMPASS;
		}

		if (piece == null) {
			return new PedestalSite(level.dimension(), pos, null, looseKey(level.dimension(), pos), fixed);
		}

		return new PedestalSite(level.dimension(), pos, piece, piece.siteKey(), fixed);
	}

	/**
	 * The world holocron named by the {@code fixed_id} the pedestal's block entity saves, or null. It
	 * is read from the block entity's saved data by its spec name, so the holocron feature needs none
	 * of the world feature's classes (W1's pedestal merges after this feature).
	 */
	private static @Nullable String savedFixedId(ServerLevel level, BlockPos pos) {
		BlockEntity entity = level.getBlockEntity(pos);

		if (entity == null) {
			return null;
		}

		ValueInput saved = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), entity.saveCustomOnly(level.registryAccess()));
		String id = saved.getStringOr(FIXED_ID_KEY, "");
		return HolocronCatalog.isWorld(id) ? id : null;
	}

	/** A pedestal with no site piece (test seam). */
	public static PedestalSite of(ResourceKey<Level> dim, BlockPos pos, String siteKey, @Nullable String fixedId) {
		return new PedestalSite(dim, pos, null, siteKey, fixedId);
	}

	/** The site key of a pedestal outside any site: {@code <dim>|pedestal|x|y|z}. */
	public static String looseKey(ResourceKey<Level> dim, BlockPos pos) {
		return dim.identifier() + "|pedestal|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
	}

	/** The dimension id as saved in {@code attuned_at} and {@code echoes}. */
	public String dimId() {
		return dim.identifier().toString();
	}

	/** The kind of site the pedestal stands in: the piece's kind, Courier's Fall for the Compass pedestal, or null. */
	public @Nullable SiteKind siteKind() {
		if (site != null) {
			return site.kind();
		}

		return HolocronCatalog.WAYFARERS_COMPASS.equals(fixedId) ? SiteKind.COURIERS_FALL : null;
	}

	/** The top centre of the pedestal, where the holocron rests and the effects play. */
	public Vec3 top() {
		return new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
	}
}
