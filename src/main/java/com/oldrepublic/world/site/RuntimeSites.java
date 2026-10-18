package com.oldrepublic.world.site;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.TerrainPalette;
import com.oldrepublic.quest.story.MeetingSites;

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

		adaptTerrain(l, spot.box(), spot.stand().getY() - 1);
		SitePiece piece = stamp(l, kind, template, spot.templateOrigin(), spot.rotation());
		return piece == null ? null : record(l, piece, spot.stand(), owner);
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

		adaptTerrain(l, box, origin.getY() - 1);
		SitePiece piece = stamp(l, kind, template, templateOrigin, rotation);

		if (piece == null) {
			throw new IllegalStateException("Site template " + template + " could not be placed at " + origin);
		}

		return record(l, piece, origin, owner);
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

				for (int y = groundY; y >= groundY - SUPPORT_DEPTH; y--) {
					if (!needsSupport(l.getBlockState(pos.set(x, y, z))) || touchesBuilt(l, pos)) {
						break;
					}

					BlockState fill = y == groundY ? layers.top() : y >= groundY - 2 ? layers.fill() : layers.base();
					l.setBlock(pos, fill, Block.UPDATE_CLIENTS);
				}

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

		if (!loaded(l, box.inflatedBy(1)) || touchesWorldgenPiece(l, box) || containsBuilt(l, box)) {
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
		double dx = origin.x - (stand.getX() + 0.5);
		double dz = origin.z - (stand.getZ() + 0.5);

		if (dx * dx + dz * dz < 1) {
			dx = -forward.x;
			dz = -forward.z;
		}

		Direction toOrigin = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		Rotation rotation = facing(toOrigin);
		BlockPos templateOrigin = templateOrigin(template, stand, rotation, buried);
		BoundingBox box = template.getBoundingBox(settings(rotation), templateOrigin);
		int half = rules.clearance() / 2;
		BoundingBox clearance = BoundingBox.encapsulating(box,
				new BoundingBox(stand.getX() - half, box.minY(), stand.getZ() - half, stand.getX() + half, box.maxY(), stand.getZ() + half));

		if (!loaded(l, clearance.inflatedBy(1)) || touchesWorldgenPiece(l, clearance) || containsBuilt(l, box)) {
			return null;
		}

		return new Spot(stand, rotation, templateOrigin, box);
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

	private static boolean touchesBuilt(ServerLevel l, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (l.getBlockState(pos.relative(direction)).is(MeetingSites.BUILT_BLOCKS)) {
				return true;
			}
		}

		return false;
	}

	private static boolean needsSupport(BlockState state) {
		return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES);
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
