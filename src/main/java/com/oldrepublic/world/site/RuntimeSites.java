package com.oldrepublic.world.site;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.TerrainPalette;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.EchoShrines;
import com.oldrepublic.world.outpost.OutpostPlacement;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Places site templates into an existing world (spec 3.6): Echo Shrines, Courier's Fall, mission
 * sites, runtime outposts and the Reliquary. It generalises the M2 pair {@code MeetingSites.find}
 * and {@code StoryStructures.place} / {@code adaptTerrain} with rotation, buried and sunken
 * placement, stricter checks and a {@link WorldSites} record for every placement.
 *
 * <p>Conventions (Phase 0 notes):
 * <ul>
 * <li>A <b>stand</b> is a feet position on the ground: the block below it is the ground. A template
 * is centred horizontally on its stand, and its layer {@code buried} sits at the stand's height, so
 * its lowest {@code buried} layers are underground (M2's camps are {@code buried} = 1: the floor
 * layer replaces the ground).</li>
 * <li>A template's entrance is on its local north side (z = 0). {@link #placeSurface} turns it to
 * face the search origin ({@link #facing}).</li>
 * <li>Templates are placed with rotation pivot {@link BlockPos#ZERO} and no mirror, like worldgen
 * pieces, so {@link SiteAnchors#toWorld} applies to both.</li>
 * <li>Nothing is placed into, and no check reads, a chunk that is not loaded.</li>
 * </ul>
 *
 * <p>Milestone 6 (docs/MILESTONE_6.md 3.1, 3.4): a template with a site meta file
 * ({@link SiteMeta#find}) is fitted by its meta ({@link SiteFit}: trees and terrain cleared
 * {@code clear_above} over its top, holes filled {@code support_depth} deep with the biome's ground,
 * footings, {@code ramp_rings} and an apron); a template without one keeps the M3 fitting
 * ({@link #adaptTerrain}). {@link #placeFar} is far placement's precise step, {@link #placeExact}
 * its fixed stamp, and {@link #placeFitted} the hubs' last search (no stilts).
 */
public final class RuntimeSites {
	/**
	 * A placement that happened.
	 *
	 * @param origin the stand the template was centred on ({@link #placeSurface}, {@link #placeAt}),
	 *               or the surface origin it was sunk below ({@link #placeSunken}); the template's own
	 *               origin is {@code piece.origin()}
	 */
	public record Placement(SitePiece piece, BlockPos origin, Rotation rotation) {
	}

	/**
	 * A surface spot that passed every check, before anything is placed.
	 *
	 * @param templateOrigin where the template's local (0, 0, 0) goes before rotation
	 * @param box            the placed template's box
	 */
	public record Spot(BlockPos stand, Rotation rotation, BlockPos templateOrigin, BoundingBox box) {
	}

	/**
	 * What a far placement step did.
	 *
	 * @param placement the placement, or null when nothing was placed
	 * @param detail    how it was placed ("surface", "clearing", "forced", "stamped") or why not
	 */
	public record Outcome(@Nullable Placement placement, String detail) {
	}

	/** Candidates per ring, as M2's {@code story.siteAngles}. */
	private static final int ANGLES = 16;
	/** Score weight of a footprint that sees the sky everywhere, against one block of height (M2). */
	private static final double OPENNESS_WEIGHT = 4.0;
	/** Holes under a surface footprint are filled at most this deep (M2). */
	private static final int SUPPORT_DEPTH = 8;
	/** Natural clutter is cleared this far above a template's top. */
	private static final int CLEAR_ABOVE = 2;
	/** The shell around a sunken template that must be free of worldgen pieces and built blocks. */
	private static final int SUNKEN_SHELL = 2;
	/** A sunken template's shell stays this far above the world's bottom. */
	private static final int SUNKEN_MIN_Y_MARGIN = 8;
	private static final long TICKS_PER_DAY = 24000L;
	/** Rings around a far target the precise surface search walks (docs/MILESTONE_6.md 3.1). */
	private static final int[] FAR_RINGS = {0, 8, 16, 24};
	/** Grid step of the clearing search around a far target. */
	private static final int CLEARING_STEP = 8;
	/** Columns sampled per side when judging a clearing's ground (every other column). */
	private static final int SAMPLE_STEP = 2;
	/** Score cost of one block between a precise candidate and its far target. */
	private static final double DISTANCE_WEIGHT = 1.0 / 8;
	/** The Reliquary surface's layers in the ground before site meta (W5's {@code ReliquarySites}). */
	private static final Identifier RELIQUARY_SURFACE = OldRepublicMod.id("boss/reliquary_surface");
	private static final int RELIQUARY_SURFACE_BURIED = 9;

	/**
	 * Searches {@code rings} around {@code origin} (then {@code fallback}) for a stand that passes the
	 * M2 rules and the 3.6 checks, adapts the terrain, places the template facing the origin and
	 * records it. Null when no spot passed (the caller retries later) or the template is missing.
	 *
	 * @param rings    ring radii in blocks from {@code origin}, searched together (best score wins)
	 * @param fallback ring radii searched together only when {@code rings} found nothing
	 * @param buried   the template's lowest layers set into the ground (Echo Shrine 9, Reliquary surface 9)
	 * @throws IllegalArgumentException when {@code template} is not a site template ({@link SiteKind#ofTemplate})
	 */
	public static @Nullable Placement placeSurface(ServerLevel l, Identifier template, Vec3 origin, Vec3 forward, int[] rings, int[] fallback,
			MeetingSites.Rules rules, int buried, @Nullable UUID owner) {
		SiteKind kind = kindOf(template);
		Spot spot = findSurface(l, template, origin, forward, rings, fallback, rules, buried);

		if (spot == null) {
			return null;
		}

		return fitAndStamp(l, kind, template, spot, owner);
	}

	/**
	 * Places a template whole below {@code surfaceOrigin}: centred on its column, its top layer
	 * {@code depth} blocks below it, turned by {@code rotation}, and records it. Null when the
	 * template plus its 2-block shell would reach an unloaded chunk, come within 8 blocks of the
	 * world's bottom, touch a worldgen piece or hold a built block. No terrain is adapted; the
	 * Reliquary's shell and slab pacing are W5's.
	 *
	 * @throws IllegalArgumentException when {@code template} is not a site template ({@link SiteKind#ofTemplate})
	 */
	public static @Nullable Placement placeSunken(ServerLevel l, Identifier template, BlockPos surfaceOrigin, int depth, Rotation rotation, @Nullable UUID owner) {
		SiteKind kind = kindOf(template);
		StructureTemplate loaded = template(l, template);

		if (loaded == null) {
			return null;
		}

		BlockPos centred = templateOrigin(loaded, surfaceOrigin, rotation, 0);
		BlockPos templateOrigin = centred.atY(surfaceOrigin.getY() - depth - (loaded.getSize().getY() - 1));
		BoundingBox shell = loaded.getBoundingBox(settings(rotation), templateOrigin).inflatedBy(SUNKEN_SHELL);

		if (!loaded(l, shell.inflatedBy(1)) || shell.minY() < l.getMinY() + SUNKEN_MIN_Y_MARGIN
				|| touchesWorldgenPiece(l, shell) || containsBuilt(l, shell)) {
			return null;
		}

		SitePiece piece = stamp(l, kind, template, templateOrigin, rotation);
		return piece == null ? null : record(l, piece, surfaceOrigin, owner);
	}

	/**
	 * Loads, generating where needed, every chunk {@link #placeSunken} looks at for the same
	 * arguments: a deep piece under a surface piece just placed is then never refused for chunks the
	 * player has not loaded yet (a fresh arrival near the ring point).
	 */
	public static void loadForSunken(ServerLevel l, Identifier template, BlockPos surfaceOrigin, int depth, Rotation rotation) {
		StructureTemplate loaded = template(l, template);

		if (loaded == null) {
			return;
		}

		BlockPos centred = templateOrigin(loaded, surfaceOrigin, rotation, 0);
		BlockPos templateOrigin = centred.atY(surfaceOrigin.getY() - depth - (loaded.getSize().getY() - 1));
		loaded.getBoundingBox(settings(rotation), templateOrigin).inflatedBy(SUNKEN_SHELL + 1).intersectingChunks().forEach(chunk -> l.getChunk(chunk.x(), chunk.z()));
	}

	/**
	 * Places a template centred on {@code origin} (a stand) with no search and no site checks, adapts
	 * the terrain and records it: for commands and client tests.
	 *
	 * @throws IllegalArgumentException when the template is missing or is not a site template
	 * @throws IllegalStateException    when the template would reach a chunk that is not loaded, or could not be placed
	 */
	public static Placement placeAt(ServerLevel l, Identifier template, BlockPos origin, Rotation rotation, int buried, @Nullable UUID owner) {
		SiteKind kind = kindOf(template);
		StructureTemplate loaded = template(l, template);

		if (loaded == null) {
			throw new IllegalArgumentException("Missing site template " + template);
		}

		BlockPos templateOrigin = templateOrigin(loaded, origin, rotation, buried);
		BoundingBox box = loaded.getBoundingBox(settings(rotation), templateOrigin);

		if (!loaded(l, box.inflatedBy(1))) {
			throw new IllegalStateException("Site template " + template + " at " + origin + " reaches a chunk that is not loaded");
		}

		Placement placement = fitAndStamp(l, kind, template, new Spot(origin.immutable(), rotation, templateOrigin, box), owner);

		if (placement == null) {
			throw new IllegalStateException("Site template " + template + " could not be placed at " + origin);
		}

		return placement;
	}

	/**
	 * The surface search of {@link #placeSurface} without placing anything (a test seam): the best
	 * spot on {@code rings}, else on {@code fallback}, or null. A candidate passes
	 * {@link MeetingSites#evaluate}, then: every chunk of the template's box (plus one block) is
	 * loaded; no worldgen piece intersects the clearance box (the template box widened to the rules'
	 * clearance square); and the template box holds no {@code #built_blocks}. The first candidate of
	 * each ring lies straight along {@code forward}; the best score is M2's (height above the ring's
	 * average plus openness).
	 */
	public static @Nullable Spot findSurface(ServerLevel l, Identifier template, Vec3 origin, Vec3 forward, int[] rings, int[] fallback,
			MeetingSites.Rules rules, int buried) {
		StructureTemplate loaded = template(l, template);

		if (loaded == null) {
			return null;
		}

		Spot spot = bestOnRings(l, loaded, origin, forward, rings, rules, buried);
		return spot != null ? spot : bestOnRings(l, loaded, origin, forward, fallback, rules, buried);
	}

	/**
	 * Far placement's precise step (docs/MILESTONE_6.md 3.1, step 3), once the chunks around
	 * {@code target} are loaded. In order: the surface search on rings 0, 8, 16 and 24 around the
	 * target with the template's own rules ({@link #rules}; the best candidate sees the most sky, stands
	 * nearest the target and, for {@code terrain} HIGH or LOW, highest or lowest); then a clearing
	 * search with trees allowed, every 8 blocks within {@code slack} of the target (the flattest
	 * ground under the footprint, at most {@code support_depth} of spread, then the nearest); then a
	 * forced placement on the target itself, refused only by a built block, another site or a worldgen
	 * piece in its box, by {@code forbidden} or by {@code veto}. Every spot faces {@code faceToward},
	 * is set {@link #buriedOf} layers deep, is fitted by the meta ({@link SiteFit}) and is recorded.
	 *
	 * @param forbidden stands that may not be used (spawn protection)
	 * @param veto      the caller's extra check on a spot (true: allowed)
	 * @throws IllegalArgumentException when {@code template} is not a site template and {@code kind} is null
	 */
	public static Outcome placeFar(ServerLevel l, Identifier template, @Nullable SiteKind kind, BlockPos target, Vec3 faceToward, SiteMeta meta,
			FarSites.Terrain terrain, int slack, Predicate<BlockPos> forbidden, Predicate<Spot> veto, @Nullable UUID owner) {
		SiteKind recordKind = kind != null ? kind : kindOf(template);
		StructureTemplate loaded = template(l, template);

		if (loaded == null) {
			return new Outcome(null, "missing_template");
		}

		MeetingSites.Rules rules = rules(meta, footprint(loaded), forbidden, List.of(), 0);
		int buried = buriedOf(template);
		Vec3 centre = Vec3.atBottomCenterOf(target);
		Vec3 away = centre.subtract(faceToward).horizontal();
		Spot spot = bestNear(l, loaded, centre, faceToward, away, rules, buried, terrain, veto);
		String how = "surface";

		if (spot == null) {
			spot = clearingNear(l, loaded, target, faceToward, away, rules, buried, slack, meta.supportDepth(), veto);
			how = "clearing";
		}

		if (spot == null) {
			if (!l.hasChunkAt(target.getX(), target.getZ())) {
				return new Outcome(null, "not_loaded");
			}

			BlockPos below = SiteGround.belowGrowth(l, target.getX(), target.getZ());
			BlockPos stand = new BlockPos(target.getX(), below != null ? below.getY() + 1 : target.getY(), target.getZ());
			spot = spotAt(loaded, stand, faceToward, away, buried);
			String refusal = refusal(l, spot, rules, veto);

			if (refusal != null) {
				return new Outcome(null, refusal);
			}

			how = "forced";
		}

		Placement placement = fitAndStamp(l, recordKind, template, spot, meta, meta.supportDepth(), Math.min(SiteFit.APRON_MAX, meta.supportDepth()), owner);
		return placement == null ? new Outcome(null, "stamp_failed") : new Outcome(placement, how);
	}

	/**
	 * Far placement's fixed stamp ({@link FarSites.Request#at}): {@code template} with its local
	 * (0, 0, 0) at {@code templateOrigin}, turned by {@code rotation}, with no search and no fitting,
	 * and recorded. The placement's stand is the box's middle column at its {@link #buriedOf} layer.
	 * Refused when its box (plus one block) is not loaded or {@code veto} refuses the spot.
	 *
	 * @throws IllegalArgumentException when {@code template} is not a site template and {@code kind} is null
	 */
	public static Outcome placeExact(ServerLevel l, Identifier template, @Nullable SiteKind kind, BlockPos templateOrigin, Rotation rotation,
			Predicate<Spot> veto, @Nullable UUID owner) {
		SiteKind recordKind = kind != null ? kind : kindOf(template);
		StructureTemplate loaded = template(l, template);

		if (loaded == null) {
			return new Outcome(null, "missing_template");
		}

		BoundingBox box = loaded.getBoundingBox(settings(rotation), templateOrigin);
		BlockPos stand = new BlockPos(box.getCenter().getX(), box.minY() + buriedOf(template), box.getCenter().getZ());
		Spot spot = new Spot(stand, rotation, templateOrigin.immutable(), box);

		if (!loaded(l, box.inflatedBy(1))) {
			return new Outcome(null, "not_loaded");
		} else if (!veto.test(spot)) {
			return new Outcome(null, "check_refused");
		}

		SitePiece piece = stamp(l, recordKind, template, spot.templateOrigin(), rotation);
		return piece == null ? new Outcome(null, "stamp_failed") : new Outcome(record(l, piece, stand, owner), "stamped");
	}

	/**
	 * Places a template on {@code stand} however rough the ground there is (the hubs' last search,
	 * instead of stilts): fitted by its site meta ({@link SiteMeta#of}) with the fill reaching the
	 * ground ({@link SiteFit#DEEP_FILL}) and the widest apron ({@link SiteFit#APRON_MAX}), so it stands
	 * on a mound of the biome's own ground. No site checks; null when the template is missing or its
	 * box and clearing margin are not loaded.
	 *
	 * @throws IllegalArgumentException when {@code template} is not a site template
	 */
	public static @Nullable Placement placeFitted(ServerLevel l, Identifier template, BlockPos stand, Rotation rotation, int buried, @Nullable UUID owner) {
		SiteKind kind = kindOf(template);
		StructureTemplate loaded = template(l, template);

		if (loaded == null) {
			return null;
		}

		BlockPos templateOrigin = templateOrigin(loaded, stand, rotation, buried);
		BoundingBox box = loaded.getBoundingBox(settings(rotation), templateOrigin);

		if (!loaded(l, box.inflatedBy(SiteFit.CLEARING_MARGIN + 1))) {
			return null;
		}

		return fitAndStamp(l, kind, template, new Spot(stand.immutable(), rotation, templateOrigin, box), SiteMeta.of(template), SiteFit.DEEP_FILL,
				SiteFit.APRON_MAX, owner);
	}

	/**
	 * The search rules of a template's site meta: a {@code footprint} square within {@code max_slope},
	 * and {@code clearance} blocks kept clear all round it.
	 */
	public static MeetingSites.Rules rules(SiteMeta meta, int footprint, Predicate<BlockPos> forbidden, List<BlockPos> avoid, int separation) {
		return new MeetingSites.Rules(footprint, footprint + 2 * meta.clearance(), meta.maxSlope(), forbidden, avoid, separation);
	}

	/** The side of the square a template stands on (its longer horizontal side), or {@code fallback} when it is missing. */
	public static int footprint(ServerLevel l, Identifier template, int fallback) {
		return l.getStructureManager().get(template).map(RuntimeSites::footprint).orElse(fallback);
	}

	/**
	 * How far from its target {@link #placeFar} may read or change blocks with this {@code slack}: its
	 * farthest stand (the outer ring or the clearing search), half the template, and then its
	 * clearance box with the fit's margin or its apron, whichever reaches further. Far placement loads
	 * this much before it places.
	 */
	public static int farReach(ServerLevel l, Identifier template, int slack) {
		SiteMeta meta = SiteMeta.of(template);
		int around = Math.max(meta.clearance() + SiteFit.CLEARING_MARGIN + 1, Math.min(SiteFit.APRON_MAX, meta.supportDepth()));
		return Math.max(FAR_RINGS[FAR_RINGS.length - 1], slack) + footprint(l, template, 0) / 2 + around + 1;
	}

	/** The box of {@code template} with its local (0, 0, 0) at {@code templateOrigin}, turned by {@code rotation}; null when it is missing. */
	public static @Nullable BoundingBox boxOf(ServerLevel l, Identifier template, BlockPos templateOrigin, Rotation rotation) {
		return l.getStructureManager().get(template).map(loaded -> loaded.getBoundingBox(settings(rotation), templateOrigin)).orElse(null);
	}

	/**
	 * The layers a template sets into the ground: its site meta's {@code buried} when its file gives
	 * one, else the constant its placer used before site meta (the planet sites' table, the hubs', the
	 * Echo Shrines', the outposts', the Reliquary surface's), else 1 (a camp's floor layer replaces
	 * the ground, as M2's camps).
	 */
	public static int buriedOf(Identifier template) {
		return SiteMeta.buried(template, legacyBuried(template));
	}

	/**
	 * {min, max, mean} of the ground stands under a {@code footprint} square centred on
	 * ({@code cx}, {@code cz}), every other column, below trees and plants; null when a column is not
	 * loaded, is built on, or is not natural ground. With {@code wet}, a fluid's surface counts as its
	 * column's ground (the fit fills it), so only built blocks and unloaded columns refuse.
	 */
	public static int @Nullable [] groundSpread(ServerLevel l, int cx, int cz, int footprint, boolean wet) {
		int half = footprint / 2;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		long sum = 0;
		int columns = 0;

		for (int dx = -half; dx <= half; dx += SAMPLE_STEP) {
			for (int dz = -half; dz <= half; dz += SAMPLE_STEP) {
				int x = cx + dx;
				int z = cz + dz;

				if (!l.hasChunkAt(x, z)) {
					return null;
				}

				int stand = wet ? wetStand(l, x, z) : SiteGround.groundStand(l, x, z);

				if (stand == Integer.MIN_VALUE) {
					return null;
				}

				min = Math.min(min, stand);
				max = Math.max(max, stand);
				sum += stand;
				columns++;
			}
		}

		return columns == 0 ? null : new int[] {min, max, (int) Math.round(sum / (double) columns)};
	}

	/**
	 * Places a template at {@code templateOrigin} turned by {@code rotation} (pivot
	 * {@link BlockPos#ZERO}, no mirror, no entities) without any check or record (a test seam, and
	 * the last step of every placement). Null when the template is missing or reaches a chunk that
	 * is not loaded.
	 */
	public static @Nullable SitePiece stamp(ServerLevel l, SiteKind kind, Identifier template, BlockPos templateOrigin, Rotation rotation) {
		StructureTemplate loaded = template(l, template);

		if (loaded == null) {
			return null;
		}

		StructurePlaceSettings settings = settings(rotation);
		BoundingBox box = loaded.getBoundingBox(settings, templateOrigin);

		if (!loaded(l, box.inflatedBy(1))) {
			return null;
		}

		loaded.placeInWorld(l, templateOrigin, templateOrigin, settings, RandomSource.create(templateOrigin.asLong()), Block.UPDATE_CLIENTS);
		return new SitePiece(kind, template, box, rotation, templateOrigin, SitePiece.runtimeKey(l.dimension(), templateOrigin));
	}

	/** The piece of a runtime record, rebuilt from its template's size; null when the template is missing. */
	public static @Nullable SitePiece pieceOf(ServerLevel l, WorldSites.SiteRecord record) {
		StructureTemplate loaded = l.getStructureManager().get(record.template()).orElse(null);

		if (loaded == null) {
			return null;
		}

		BoundingBox box = loaded.getBoundingBox(settings(record.rotation()), record.origin());
		return new SitePiece(record.kind(), record.template(), box, record.rotation(), record.origin(), SitePiece.runtimeKey(record.dimension(), record.origin()));
	}

	/**
	 * Fits the ground to a surface template's footprint (M2's {@code adaptTerrain}, tightened): in
	 * every column of {@code box}, holes from {@code groundY} down (at most 8 deep) are filled with
	 * the biome's top, fill and base blocks ({@link TerrainPalette}: grass, dirt and stone on the home
	 * world), and natural clutter from {@code groundY + 1} to 2 above the box is
	 * cleared. A cell touching a {@code #built_blocks} block is never filled, and filling stops in
	 * that column, so a player's room or a neighbouring site is never filled in.
	 */
	public static void adaptTerrain(ServerLevel l, BoundingBox box, int groundY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				TerrainPalette.Layers layers = TerrainPalette.at(l, pos.set(x, groundY, z));
				SiteFit.fillDown(l, pos, x, z, groundY, groundY - SUPPORT_DEPTH, layers);
				boolean cleared = false;

				for (int y = groundY + 1; y <= box.maxY() + CLEAR_ABOVE; y++) {
					BlockState state = l.getBlockState(pos.set(x, y, z));

					if (!state.isAir() && isNaturalClutter(state)) {
						l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						cleared = true;
					}
				}

				// A lowered bump shows its fill; give it its top again.
				if (cleared && l.getBlockState(pos.set(x, groundY, z)).is(layers.fill().getBlock())) {
					l.setBlock(pos, layers.top(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/**
	 * Whether a template centred on {@code stand} and turned by {@code rotation} would stand clear of
	 * every other site: its box (plus one block) loaded, no worldgen piece and no recorded runtime site
	 * touching it, and no built block in it. For a placement that skips the ring search (a mission's
	 * forward fallback), so it never stamps over an outpost or another camp.
	 */
	public static boolean fits(ServerLevel l, Identifier template, BlockPos stand, Rotation rotation, int buried) {
		StructureTemplate loaded = template(l, template);

		if (loaded == null) {
			return false;
		}

		BoundingBox box = loaded.getBoundingBox(settings(rotation), templateOrigin(loaded, stand, rotation, buried));
		return loaded(l, box.inflatedBy(1)) && !containsBuilt(l, box) && clearOfSites(l, box);
	}

	/**
	 * Whether no worldgen structure piece and no recorded runtime site touches {@code box}; a chunk
	 * that is not loaded counts as touching.
	 */
	public static boolean clearOfSites(ServerLevel l, BoundingBox box) {
		if (touchesWorldgenPiece(l, box)) {
			return false;
		}

		for (WorldSites.SiteRecord record : WorldSites.all(l.getServer())) {
			SitePiece other = record.dimension().equals(l.dimension()) ? pieceOf(l, record) : null;

			if (other != null && other.box().intersects(box)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Whether any worldgen structure piece intersects {@code box}: for every chunk the box touches,
	 * the starts that chunk references, then each start's piece boxes (3.6; corner samples miss a
	 * mineshaft corridor). A chunk that is not loaded counts as touching.
	 */
	public static boolean touchesWorldgenPiece(ServerLevel l, BoundingBox box) {
		for (ChunkPos chunk : box.intersectingChunks().toList()) {
			if (!l.hasChunk(chunk.x(), chunk.z())) {
				return true;
			}

			for (StructureStart start : l.structureManager().startsForStructure(chunk, structure -> true)) {
				for (StructurePiece piece : start.getPieces()) {
					if (piece.getBoundingBox().intersects(box)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * The rotation that turns a template's entrance (its local north side) to face {@code entrance}:
	 * north is {@link Rotation#NONE}, east {@link Rotation#CLOCKWISE_90}, south
	 * {@link Rotation#CLOCKWISE_180}, west {@link Rotation#COUNTERCLOCKWISE_90}.
	 */
	public static Rotation facing(Direction entrance) {
		return switch (entrance) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/**
	 * Where a template's local (0, 0, 0) goes so that, turned by {@code rotation}, it is centred
	 * horizontally on {@code stand} with its layer {@code buried} at the stand's height.
	 */
	public static BlockPos templateOrigin(StructureTemplate template, BlockPos stand, Rotation rotation, int buried) {
		BoundingBox atZero = template.getBoundingBox(settings(rotation), BlockPos.ZERO);
		return new BlockPos(stand.getX() - atZero.getXSpan() / 2 - atZero.minX(), stand.getY() - buried, stand.getZ() - atZero.getZSpan() / 2 - atZero.minZ());
	}

	private static @Nullable Spot bestOnRings(ServerLevel l, StructureTemplate template, Vec3 origin, Vec3 forward, int[] rings, MeetingSites.Rules rules, int buried) {
		Vec3 flat = forward.horizontal();
		double phase = flat.lengthSqr() < 1.0e-4 ? 0 : Math.atan2(flat.z, flat.x);
		Spot best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (int ring : rings) {
			List<Spot> passed = new ArrayList<>();
			List<MeetingSites.Candidate> candidates = new ArrayList<>();

			for (int i = 0; i < ANGLES; i++) {
				double angle = phase + i * Math.PI * 2 / ANGLES;
				int cx = (int) Math.floor(origin.x + Math.cos(angle) * ring);
				int cz = (int) Math.floor(origin.z + Math.sin(angle) * ring);
				MeetingSites.Candidate candidate = MeetingSites.evaluate(l, cx, cz, rules);
				Spot spot = candidate == null ? null : check(l, template, candidate.stand(), origin, forward, rules, buried);

				if (spot != null) {
					passed.add(spot);
					candidates.add(candidate);
				}
			}

			double average = candidates.stream().mapToDouble(MeetingSites.Candidate::meanHeight).average().orElse(0);

			for (int i = 0; i < passed.size(); i++) {
				MeetingSites.Candidate candidate = candidates.get(i);
				double score = candidate.meanHeight() - average + candidate.openness() * OPENNESS_WEIGHT;

				if (score > bestScore) {
					bestScore = score;
					best = passed.get(i);
				}
			}
		}

		return best;
	}

	/** The 3.6 checks beyond {@link MeetingSites#evaluate} for a template on {@code stand} facing the origin. */
	private static @Nullable Spot check(ServerLevel l, StructureTemplate template, BlockPos stand, Vec3 origin, Vec3 forward, MeetingSites.Rules rules, int buried) {
		Rotation rotation = facing(entranceToward(stand, origin, forward));
		BlockPos templateOrigin = templateOrigin(template, stand, rotation, buried);
		BoundingBox box = template.getBoundingBox(settings(rotation), templateOrigin);
		BoundingBox clearance = clearanceBox(box, stand, rules);

		if (!loaded(l, clearance.inflatedBy(1)) || touchesWorldgenPiece(l, clearance) || containsBuilt(l, box)) {
			return null;
		}

		return new Spot(stand, rotation, templateOrigin, box);
	}

	/**
	 * Fits the ground (by the template's site meta when it has a file, else the M3 way), stamps the
	 * template on {@code spot} and records it; null when it cannot be stamped.
	 */
	private static @Nullable Placement fitAndStamp(ServerLevel l, SiteKind kind, Identifier template, Spot spot, @Nullable UUID owner) {
		SiteMeta meta = SiteMeta.find(template);

		if (meta != null) {
			return fitAndStamp(l, kind, template, spot, meta, meta.supportDepth(), Math.min(SiteFit.APRON_MAX, meta.supportDepth()), owner);
		}

		adaptTerrain(l, spot.box(), spot.stand().getY() - 1);
		SitePiece piece = stamp(l, kind, template, spot.templateOrigin(), spot.rotation());
		return piece == null ? null : record(l, piece, spot.stand(), owner);
	}

	/** {@link SiteFit#before}, the stamp, {@link SiteFit#after} and the record. */
	private static @Nullable Placement fitAndStamp(ServerLevel l, SiteKind kind, Identifier template, Spot spot, SiteMeta meta, int fillDepth, int apron,
			@Nullable UUID owner) {
		int standY = spot.stand().getY();
		SiteFit.before(l, spot.box(), standY, meta, fillDepth);
		SitePiece piece = stamp(l, kind, template, spot.templateOrigin(), spot.rotation());

		if (piece == null) {
			return null;
		}

		SiteFit.after(l, spot.box(), standY, meta, apron);
		return record(l, piece, spot.stand(), owner);
	}

	/** The precise surface search around a far target ({@link #placeFar}'s first step). */
	private static @Nullable Spot bestNear(ServerLevel l, StructureTemplate template, Vec3 centre, Vec3 faceToward, Vec3 away, MeetingSites.Rules rules,
			int buried, FarSites.Terrain terrain, Predicate<Spot> veto) {
		List<Spot> passed = new ArrayList<>();
		List<MeetingSites.Candidate> candidates = new ArrayList<>();

		for (int ring : FAR_RINGS) {
			int angles = ring == 0 ? 1 : ANGLES;

			for (int i = 0; i < angles; i++) {
				double angle = i * Math.PI * 2 / angles;
				int cx = (int) Math.floor(centre.x + Math.cos(angle) * ring);
				int cz = (int) Math.floor(centre.z + Math.sin(angle) * ring);
				MeetingSites.Candidate candidate = MeetingSites.evaluate(l, cx, cz, rules);
				Spot spot = candidate == null ? null : check(l, template, candidate.stand(), faceToward, away, rules, buried);

				if (spot != null && veto.test(spot)) {
					passed.add(spot);
					candidates.add(candidate);
				}
			}
		}

		double average = candidates.stream().mapToDouble(MeetingSites.Candidate::meanHeight).average().orElse(0);
		Spot best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (int i = 0; i < passed.size(); i++) {
			MeetingSites.Candidate candidate = candidates.get(i);
			double relief = terrain == FarSites.Terrain.HIGH ? candidate.meanHeight() - average : terrain == FarSites.Terrain.LOW ? average - candidate.meanHeight() : 0;
			double distance = Math.sqrt(Math.pow(candidate.stand().getX() + 0.5 - centre.x, 2) + Math.pow(candidate.stand().getZ() + 0.5 - centre.z, 2));
			double score = candidate.openness() * OPENNESS_WEIGHT + relief - distance * DISTANCE_WEIGHT;

			if (score > bestScore) {
				bestScore = score;
				best = passed.get(i);
			}
		}

		return best;
	}

	/**
	 * The clearing search around a far target ({@link #placeFar}'s second step): stands every
	 * {@value #CLEARING_STEP} blocks within {@code reach}, trees allowed, ground spread at most
	 * {@code maxSpread}; the flattest, then the nearest, that passes {@link #refusal}.
	 */
	private static @Nullable Spot clearingNear(ServerLevel l, StructureTemplate template, BlockPos target, Vec3 faceToward, Vec3 away, MeetingSites.Rules rules,
			int buried, int reach, int maxSpread, Predicate<Spot> veto) {
		record Flat(BlockPos stand, int spread, double distance) {
		}

		List<Flat> flats = new ArrayList<>();

		for (int dx = -reach; dx <= reach; dx += CLEARING_STEP) {
			for (int dz = -reach; dz <= reach; dz += CLEARING_STEP) {
				if (dx * dx + dz * dz > reach * reach) {
					continue;
				}

				int[] spread = groundSpread(l, target.getX() + dx, target.getZ() + dz, rules.footprint(), false);

				if (spread != null && spread[1] - spread[0] <= maxSpread) {
					flats.add(new Flat(new BlockPos(target.getX() + dx, spread[2], target.getZ() + dz), spread[1] - spread[0], Math.sqrt(dx * dx + dz * dz)));
				}
			}
		}

		flats.sort(Comparator.comparingInt(Flat::spread).thenComparingDouble(Flat::distance));

		for (Flat flat : flats) {
			Spot spot = spotAt(template, flat.stand(), faceToward, away, buried);

			if (refusal(l, spot, rules, veto) == null) {
				return spot;
			}
		}

		return null;
	}

	/** A template on {@code stand} facing {@code faceToward} (or back along {@code away} on top of it), unchecked. */
	private static Spot spotAt(StructureTemplate template, BlockPos stand, Vec3 faceToward, Vec3 away, int buried) {
		Rotation rotation = facing(entranceToward(stand, faceToward, away));
		BlockPos templateOrigin = templateOrigin(template, stand, rotation, buried);
		return new Spot(stand.immutable(), rotation, templateOrigin, template.getBoundingBox(settings(rotation), templateOrigin));
	}

	/**
	 * Why a spot chosen without the surface rules may not take a template, or null when it may: its
	 * stand forbidden, its clearance box (and the fit's margin) not loaded, a worldgen piece in the
	 * clearance box, a built block or another recorded site in the box, or the caller's veto.
	 */
	private static @Nullable String refusal(ServerLevel l, Spot spot, MeetingSites.Rules rules, Predicate<Spot> veto) {
		BoundingBox clearance = clearanceBox(spot.box(), spot.stand(), rules);

		if (rules.forbidden().test(spot.stand())) {
			return "forbidden";
		} else if (!loaded(l, clearance.inflatedBy(SiteFit.CLEARING_MARGIN + 1))) {
			return "not_loaded";
		} else if (touchesWorldgenPiece(l, clearance)) {
			return "worldgen_piece";
		} else if (containsBuilt(l, spot.box())) {
			return "built";
		} else if (!clearOfSites(l, spot.box())) {
			return "other_site";
		}

		return veto.test(spot) ? null : "check_refused";
	}

	/** The feet height over a column's ground below trees, or over the fluid on it; {@link Integer#MIN_VALUE} for a built block or no ground near. */
	private static int wetStand(ServerLevel l, int x, int z) {
		BlockPos below = SiteGround.belowGrowth(l, x, z);
		return below == null || l.getBlockState(below).is(MeetingSites.BUILT_BLOCKS) ? Integer.MIN_VALUE : below.getY() + 1;
	}

	/** The buried values from before site meta (docs/MILESTONE_6.md 3.4 keeps them as the fallback). */
	private static int legacyBuried(Identifier template) {
		int planet = PlanetSites.buried(template);
		SiteKind kind = SiteKind.ofTemplate(template);

		if (planet >= 0) {
			return planet;
		} else if (kind == SiteKind.ECHO_SHRINE) {
			return EchoShrines.BURIED;
		} else if (kind == SiteKind.REPUBLIC_OUTPOST || kind == SiteKind.IMPERIAL_OUTPOST) {
			return OutpostPlacement.BURIED;
		}

		return RELIQUARY_SURFACE.equals(template) ? RELIQUARY_SURFACE_BURIED : SiteMeta.DEFAULT.buried();
	}

	private static int footprint(StructureTemplate template) {
		return Math.max(template.getSize().getX(), template.getSize().getZ());
	}

	/** The template box widened to the rules' clearance square around the stand. */
	private static BoundingBox clearanceBox(BoundingBox box, BlockPos stand, MeetingSites.Rules rules) {
		int half = rules.clearance() / 2;
		return BoundingBox.encapsulating(box, new BoundingBox(stand.getX() - half, box.minY(), stand.getZ() - half, stand.getX() + half, box.maxY(), stand.getZ() + half));
	}

	/** The side of a template at {@code stand} that faces {@code toward}, or back along {@code away} when the stand is on top of it. */
	private static Direction entranceToward(BlockPos stand, Vec3 toward, Vec3 away) {
		double dx = toward.x - (stand.getX() + 0.5);
		double dz = toward.z - (stand.getZ() + 0.5);

		if (dx * dx + dz * dz < 1) {
			dx = -away.x;
			dz = -away.z;
		}

		return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	private static Placement record(ServerLevel l, SitePiece piece, BlockPos origin, @Nullable UUID owner) {
		long day = l.getServer().overworld().getGameTime() / TICKS_PER_DAY;
		WorldSites.add(l.getServer(), new WorldSites.SiteRecord(piece.kind(), l.dimension(), piece.origin(), piece.rotation(), piece.template(), owner, day));
		return new Placement(piece, origin.immutable(), piece.rotation());
	}

	private static SiteKind kindOf(Identifier template) {
		SiteKind kind = SiteKind.ofTemplate(template);

		if (kind == null) {
			throw new IllegalArgumentException(template + " is not a site template");
		}

		return kind;
	}

	/** The template, or null (logged) when it is missing. */
	private static @Nullable StructureTemplate template(ServerLevel l, Identifier id) {
		Optional<StructureTemplate> template = l.getStructureManager().get(id);

		if (template.isEmpty()) {
			OldRepublicMod.LOGGER.error("Missing site template {}", id);
			return null;
		}

		return template.get();
	}

	private static StructurePlaceSettings settings(Rotation rotation) {
		return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(BlockPos.ZERO).setIgnoreEntities(true);
	}

	private static boolean loaded(ServerLevel l, BoundingBox box) {
		return box.intersectingChunks().allMatch(chunk -> l.hasChunk(chunk.x(), chunk.z()));
	}

	private static boolean containsBuilt(ServerLevel l, BoundingBox box) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					if (l.getBlockState(pos.set(x, y, z)).is(MeetingSites.BUILT_BLOCKS)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/** Natural things that may be cleared above a site: plants, snow, leaves and natural ground (M2). */
	private static boolean isNaturalClutter(BlockState state) {
		if (state.is(MeetingSites.BUILT_BLOCKS)) {
			return false;
		}

		return (state.canBeReplaced() && state.getFluidState().isEmpty()) || state.is(BlockTags.LEAVES) || state.is(MeetingSites.STORY_GROUND);
	}

	private RuntimeSites() {
	}
}
