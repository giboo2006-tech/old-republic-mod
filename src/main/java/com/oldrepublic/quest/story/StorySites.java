package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteMeta;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * The story's far sites (docs/MILESTONE_6.md 3.5): the {@link FarSites} request keys and bands, what
 * a placed request stands on, the template-driven rules of the near search that takes over when far
 * placement fails, the raider stronghold's anchors with the old camp's layout as the fallback, the
 * encampment's stamp rule and its stamp that keeps what players put in the Crossing, and the
 * journal's note of the way. Server side.
 */
public final class StorySites {
	/** Prefix of the lang keys of the journal's notes of the way. */
	private static final String NOTE_PREFIX = "quest.oldrepublic.far.note.";
	/** Notes give the distance in hundreds of blocks up to this many; further is "far". */
	private static final int NOTE_HUNDREDS = 10;
	/** A site record's box widened by this much still holds the position it is looked up by. */
	private static final int PIECE_SLACK = 2;
	private static final List<SiteAnchors.Anchor> CREW = List.of(SiteAnchors.Anchor.CREW_1, SiteAnchors.Anchor.CREW_2, SiteAnchors.Anchor.CREW_3,
			SiteAnchors.Anchor.CREW_4, SiteAnchors.Anchor.CREW_5, SiteAnchors.Anchor.CREW_6, SiteAnchors.Anchor.CREW_7);
	private static final List<SiteAnchors.Anchor> CAPTIVES = List.of(SiteAnchors.Anchor.CAPTIVE_1, SiteAnchors.Anchor.CAPTIVE_2, SiteAnchors.Anchor.CAPTIVE_3);

	/**
	 * Where the raider stronghold's people stand: the crew in spawn order (four poachers, then the
	 * extras), the hired acolyte, the Captain and the captives.
	 */
	public record RaiderSpots(List<BlockPos> crew, BlockPos acolyte, BlockPos captain, List<BlockPos> captives) {
	}

	// ---------------------------------------------------------------- request keys

	/** A far request key: {@code <part>/<owner>/<site>}, one site per owner and key. */
	public static String key(String part, UUID owner, String site) {
		return part + "/" + owner + "/" + site;
	}

	static String crossingKey(UUID owner) {
		return key("story", owner, "crossing");
	}

	static String encampmentKey(UUID owner) {
		return key("story", owner, "encampment");
	}

	static String strongholdKey(UUID owner) {
		return key("story", owner, "raider_camp");
	}

	static String fallKey(UUID owner) {
		return key("c3", owner, "couriers_fall");
	}

	static String reliquaryKey(UUID owner) {
		return key("c3", owner, "reliquary");
	}

	/** The first echo's shrine ({@code extra} false) or the old-world guarantee's shrine of {@code kind}. */
	static String shrineKey(UUID owner, HolocronKind kind, boolean extra) {
		return key("c3", owner, (extra ? "extra_shrine_" : "first_echo_") + kind.name().toLowerCase(Locale.ROOT));
	}

	static String matriarchKey(UUID owner) {
		return key("c5", owner, "matriarch");
	}

	/** The hulk's cradle ({@code Ships.placeHulk}). */
	public static String cradleKey(UUID owner) {
		return key("ship", owner, "cradle");
	}

	/** Every far request key the story may have asked under for {@code owner} (a story reset cancels them all). */
	static List<String> storyKeys(UUID owner) {
		List<String> keys = new ArrayList<>(List.of(crossingKey(owner), encampmentKey(owner), strongholdKey(owner), fallKey(owner), reliquaryKey(owner),
				matriarchKey(owner), cradleKey(owner)));

		for (HolocronKind kind : HolocronKind.values()) {
			keys.add(shrineKey(owner, kind, false));
			keys.add(shrineKey(owner, kind, true));
		}

		return keys;
	}

	// ---------------------------------------------------------------- requests and their answers

	/** A template request in {@code band} ([min, max] blocks) from {@code from}, its entrance facing {@code from}. */
	public static FarSites.Request request(String key, @Nullable UUID owner, Identifier template, BlockPos from, int[] band) {
		return FarSites.Request.of(key, owner, template, from, low(band), high(band));
	}

	/** A ground point (no template) of {@code footprint} in {@code band} ([min, max] blocks) from {@code from}. */
	public static FarSites.Request point(String key, @Nullable UUID owner, BlockPos from, int[] band, int footprint) {
		return FarSites.Request.point(key, owner, from, low(band), high(band), footprint);
	}

	private static int low(int[] band) {
		return band.length > 0 ? band[0] : 0;
	}

	private static int high(int[] band) {
		return band.length > 1 ? Math.max(band[0], band[1]) : low(band);
	}

	/** Whether a request is still under way (choosing a target, or loading it). */
	public static boolean pending(FarSites.Status status) {
		return status.phase() == FarSites.Phase.CHOOSING || status.phase() == FarSites.Phase.LOADING;
	}

	/** Where guidance points while the request with {@code key} is under way; null once it is placed or failed, or before it is chosen. */
	// API-CHECK: FarSites.poll finds a request by key whatever level it is given (J1 keeps them per server); callers may poll from another dimension.
	public static @Nullable BlockPos pendingPointer(ServerLevel level, String key) {
		FarSites.Status status = FarSites.poll(level, key);
		return pending(status) ? status.pointer() : null;
	}

	/** A placed site's {@code HEART} anchor, else the stand it was placed on. */
	public static BlockPos heart(RuntimeSites.Placement placement) {
		BlockPos heart = SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.HEART);
		return heart != null ? heart : placement.origin();
	}

	/** Where a placed request stands: its template's {@link #heart}, or a ground point's target; null when it is not placed. */
	// API-CHECK: a placed ground point (Request.point) answers PLACED with its target set and no placement (J1's FarJobs; not run here).
	public static @Nullable BlockPos placedAt(FarSites.Status status) {
		if (!status.placed()) {
			return null;
		}

		RuntimeSites.Placement placement = status.placement();
		return placement != null ? heart(placement) : status.target();
	}

	// ---------------------------------------------------------------- templates and the near search

	/** The template's site meta, or null when it has none ({@link SiteMeta#of} gives the defaults then). */
	private static @Nullable SiteMeta meta(Identifier template) {
		SiteMeta meta = SiteMeta.of(template);
		return meta.equals(SiteMeta.DEFAULT) ? null : meta;
	}

	/** The layers a template sets into the ground: its site meta's {@code buried}, else {@code fallback} (the constant from before site meta). */
	public static int buried(Identifier template, int fallback) {
		SiteMeta meta = meta(template);
		return meta != null ? meta.buried() : fallback;
	}

	/** Half the template's larger horizontal side, or {@code fallback} when the template is missing. */
	public static int halfSide(ServerLevel level, Identifier template, int fallback) {
		return level.getStructureManager().get(template).map(t -> side(t) / 2).orElse(fallback);
	}

	/**
	 * The near search's rules for a template (docs/MILESTONE_6.md 3.4): with site meta, the template's
	 * larger side as the footprint, that side plus the meta's clearance on each side, and the meta's
	 * slope; without meta (or without the template), the caller's constants.
	 */
	public static MeetingSites.Rules nearRules(ServerLevel level, Identifier template, int footprint, int clearance, int maxSlope,
			Predicate<BlockPos> forbidden, List<BlockPos> avoid, int separation) {
		SiteMeta meta = meta(template);
		int side = level.getStructureManager().get(template).map(StorySites::side).orElse(-1);

		if (meta == null || side < 0) {
			return new MeetingSites.Rules(footprint, clearance, maxSlope, forbidden, avoid, separation);
		}

		return new MeetingSites.Rules(side, side + 2 * meta.clearance(), meta.maxSlope(), forbidden, avoid, separation);
	}

	private static int side(StructureTemplate template) {
		return Math.max(template.getSize().getX(), template.getSize().getZ());
	}

	/** The turn that faces a template's entrance (its local north) from {@code stand} toward {@code toward}. */
	public static Rotation facingToward(BlockPos stand, Vec3 toward) {
		double dx = toward.x - (stand.getX() + 0.5);
		double dz = toward.z - (stand.getZ() + 0.5);
		Direction entrance = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		return RuntimeSites.facing(entrance);
	}

	/**
	 * The newest site record of {@code template} in the level's dimension owned by {@code owner} whose
	 * box (widened by two blocks) holds the column of {@code near}, as a piece; null when there is none
	 * (an old save's camp, or a site the near search found without placing blocks).
	 */
	public static @Nullable SitePiece ownedPiece(ServerLevel level, UUID owner, Identifier template, BlockPos near) {
		List<WorldSites.SiteRecord> records = WorldSites.all(level.getServer());

		for (int i = records.size() - 1; i >= 0; i--) {
			WorldSites.SiteRecord record = records.get(i);

			if (!record.template().equals(template) || !owner.equals(record.owner()) || !record.dimension().equals(level.dimension())) {
				continue;
			}

			SitePiece piece = RuntimeSites.pieceOf(level, record);

			if (piece != null && holds(piece.box(), near)) {
				return piece;
			}
		}

		return null;
	}

	private static boolean holds(BoundingBox box, BlockPos pos) {
		return pos.getX() >= box.minX() - PIECE_SLACK && pos.getX() <= box.maxX() + PIECE_SLACK && pos.getZ() >= box.minZ() - PIECE_SLACK
				&& pos.getZ() <= box.maxZ() + PIECE_SLACK;
	}

	/** An anchor of a placed piece in world space, or null without a piece or without that anchor. */
	public static @Nullable BlockPos anchor(@Nullable SitePiece piece, SiteAnchors.Anchor anchor) {
		return piece == null ? null : SiteAnchors.find(piece, anchor);
	}

	// ---------------------------------------------------------------- the raider stronghold and the encampment

	/**
	 * Where the people of the raider stronghold {@code camp} stand: its anchors {@code CREW_1..7},
	 * {@code ACOLYTE}, {@code CAPTAIN} and {@code CAPTIVE_1..3}; each anchor the sidecar lacks (every
	 * one, without a piece) comes from the old camp's layout centred on {@code site}
	 * ({@link StoryStructures#crewSpots} and the rest).
	 */
	public static RaiderSpots raiderSpots(Level level, @Nullable SitePiece camp, BlockPos site) {
		List<BlockPos> crew = withAnchors(StoryStructures.crewSpots(level, site), camp, CREW);
		List<BlockPos> captives = withAnchors(StoryStructures.captiveSpots(level, site), camp, CAPTIVES);
		BlockPos acolyte = anchor(camp, SiteAnchors.Anchor.ACOLYTE);
		BlockPos captain = anchor(camp, SiteAnchors.Anchor.CAPTAIN);
		return new RaiderSpots(crew, acolyte != null ? acolyte : StoryStructures.acolyteSpot(level, site),
				captain != null ? captain : StoryStructures.captainSpot(level, site), captives);
	}

	/** {@code fallback} with every position the piece has an anchor for replaced by that anchor (same index). */
	private static List<BlockPos> withAnchors(List<BlockPos> fallback, @Nullable SitePiece piece, List<SiteAnchors.Anchor> anchors) {
		List<BlockPos> out = new ArrayList<>(fallback);

		for (int i = 0; i < Math.min(out.size(), anchors.size()); i++) {
			BlockPos at = anchor(piece, anchors.get(i));

			if (at != null) {
				out.set(i, at);
			}
		}

		return out;
	}

	/** The encampment's stamp rule (docs/MILESTONE_6.md 3.5): the owner at least {@code distance} blocks (horizontally) from the Crossing. */
	public static boolean stampDue(BlockPos owner, BlockPos crossing, double distance) {
		double dx = owner.getX() - crossing.getX();
		double dz = owner.getZ() - crossing.getZ();
		return dx * dx + dz * dz >= distance * distance;
	}

	/**
	 * Stamps {@code overlay} over the placed {@code base} at the base's origin and rotation, as
	 * {@link RuntimeSites#stamp} places a template (pivot at the origin, no mirror, no entities, no
	 * record), except in the cells {@link #keptCells} names, which stay as they are: a chest the
	 * player filled keeps its contents, a wall they built still stands. With cells to keep, the
	 * template goes in box by box around them ({@link #without}) with its own block shapes, as
	 * worldgen places a structure piece chunk by chunk, so no bed, door or torch is lost at a cut.
	 * False, with nothing placed, when the overlay's template is missing or its box (plus one block)
	 * reaches a chunk that is not loaded.
	 */
	public static boolean stampOver(ServerLevel level, Identifier overlay, SitePiece base) {
		StructureTemplate template = level.getStructureManager().get(overlay).orElse(null);

		if (template == null) {
			return false;
		}

		BoundingBox box = template.getBoundingBox(settings(base.rotation()), base.origin());

		if (!loaded(level, box.inflatedBy(1))) {
			return false;
		}

		List<BoundingBox> parts = without(box, keptCells(level, box, base));
		boolean cut = parts.size() > 1;

		for (BoundingBox part : parts) {
			// API-CHECK: StructurePlaceSettings#setKnownShape(boolean) (vanilla's jigsaw pieces set it to place a template in parts without shape updates at the cuts).
			template.placeInWorld(level, base.origin(), base.origin(), settings(base.rotation()).setBoundingBox(part).setKnownShape(cut),
					RandomSource.create(base.origin().asLong()), Block.UPDATE_CLIENTS);
		}

		return true;
	}

	/**
	 * The cells of {@code box} a stamp over {@code base} must leave alone, so nothing a player put
	 * there is lost: every block entity the base's template did not put there (a player's chest, bed
	 * or sign, with what it holds), every container even where the base did put it (a player may have
	 * filled it), and every {@code #oldrepublic:built_blocks} block the base's template did not put
	 * there (a block a player built). With the base's template missing, every block entity and built
	 * block is kept. Natural ground and the base's own blocks (its campfire and banners too) may be
	 * stamped over.
	 */
	public static List<BlockPos> keptCells(ServerLevel level, BoundingBox box, SitePiece base) {
		StructureTemplate template = level.getStructureManager().get(base.template()).orElse(null);
		StructurePlaceSettings settings = settings(base.rotation());
		Map<Block, Set<BlockPos>> baseCells = new HashMap<>();
		List<BlockPos> kept = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));

					if (state.isAir()) {
						continue;
					}

					BlockEntity entity = level.getBlockEntity(pos);

					if (entity instanceof Container || ((entity != null || state.is(MeetingSites.BUILT_BLOCKS)) && (template == null
							|| !baseCells.computeIfAbsent(state.getBlock(), block -> cellsOf(template, settings, base.origin(), block)).contains(pos)))) {
						kept.add(pos.immutable());
					}
				}
			}
		}

		return kept;
	}

	/** Where {@code template}, placed at {@code origin} with {@code settings}, puts {@code block}: world positions. */
	private static Set<BlockPos> cellsOf(StructureTemplate template, StructurePlaceSettings settings, BlockPos origin, Block block) {
		Set<BlockPos> cells = new HashSet<>();

		// filterBlocks gives positions offset by the origin and turned by the settings (as the gametests read templates with it).
		for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, block)) {
			cells.add(info.pos());
		}

		return cells;
	}

	/**
	 * {@code box} cut into boxes that together hold each of its cells once, except {@code cells}: each
	 * cell splits the box holding it into the parts below and above it along x, then within its x
	 * slice along z, then within its column along y (at most five more boxes per cell).
	 */
	public static List<BoundingBox> without(BoundingBox box, List<BlockPos> cells) {
		List<BoundingBox> parts = new ArrayList<>(List.of(box));

		for (BlockPos cell : cells) {
			List<BoundingBox> next = new ArrayList<>(parts.size() + 5);
			int x = cell.getX();
			int y = cell.getY();
			int z = cell.getZ();

			for (BoundingBox part : parts) {
				if (!part.isInside(cell)) {
					next.add(part);
					continue;
				}

				if (x > part.minX()) {
					next.add(new BoundingBox(part.minX(), part.minY(), part.minZ(), x - 1, part.maxY(), part.maxZ()));
				}

				if (x < part.maxX()) {
					next.add(new BoundingBox(x + 1, part.minY(), part.minZ(), part.maxX(), part.maxY(), part.maxZ()));
				}

				if (z > part.minZ()) {
					next.add(new BoundingBox(x, part.minY(), part.minZ(), x, part.maxY(), z - 1));
				}

				if (z < part.maxZ()) {
					next.add(new BoundingBox(x, part.minY(), z + 1, x, part.maxY(), part.maxZ()));
				}

				if (y > part.minY()) {
					next.add(new BoundingBox(x, part.minY(), z, x, y - 1, z));
				}

				if (y < part.maxY()) {
					next.add(new BoundingBox(x, y + 1, z, x, part.maxY(), z));
				}
			}

			parts = next;
		}

		return parts;
	}

	/** Placement settings of runtime sites: the rotation about {@link BlockPos#ZERO}, no mirror, no entities (as {@code RuntimeSites}). */
	private static StructurePlaceSettings settings(Rotation rotation) {
		return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(BlockPos.ZERO).setIgnoreEntities(true);
	}

	private static boolean loaded(ServerLevel level, BoundingBox box) {
		return box.intersectingChunks().allMatch(chunk -> level.hasChunk(chunk.x(), chunk.z()));
	}

	// ---------------------------------------------------------------- the journal's note of the way

	/**
	 * Notes the way to a far site under the journal entry {@code entryId} (docs/MILESTONE_6.md 3.3):
	 * its direction and its distance from where the player stands, replacing an earlier note of the
	 * way under that entry.
	 */
	public static void noteWay(ServerPlayer player, String entryId, BlockPos to) {
		StoryData data = Story.get(player);
		String earlier = ChapterThreeRules.NOTE_PREFIX + entryId + "|" + NOTE_PREFIX;

		if (data.flags.removeIf(flag -> flag.startsWith(earlier))) {
			Story.commit(player, data);
		}

		BlockPos from = player.blockPosition();
		ChapterThreeService.addJournalNote(player, entryId, directionNote(from, to));
		ChapterThreeService.addJournalNote(player, entryId, distanceNote(from, to));
	}

	/** The lang key of the note naming the direction from {@code from} to {@code to}: one of eight. */
	public static String directionNote(BlockPos from, BlockPos to) {
		return NOTE_PREFIX + "dir." + FarSites.compass(to.getX() - from.getX(), to.getZ() - from.getZ());
	}

	/**
	 * The lang key of the note naming the distance from {@code from} to {@code to}: "close" under 100
	 * blocks, then the nearest hundred up to 1000 ("1" to "10"), then "far".
	 */
	public static String distanceNote(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);

		if (distance < 100) {
			return NOTE_PREFIX + "dist.close";
		}

		long hundreds = Math.round(distance / 100.0);
		return NOTE_PREFIX + "dist." + (hundreds > NOTE_HUNDREDS ? "far" : Long.toString(hundreds));
	}

	// ---------------------------------------------------------------- test seams (docs/MILESTONE_6.md 8.2)

	/** Takes {@code status} as the answer of the player's Crossing request (VOICES, no meeting point yet). */
	public static void settleCrossing(ServerPlayer player, FarSites.Status status) {
		Crossing.settle(player, status);
	}

	/** Takes {@code status} as the answer of the player's stronghold request (Smoke on the Ridge not placed yet). */
	public static void settleStronghold(ServerPlayer player, FarSites.Status status) {
		Reckoning.settle(player, status);
	}

	/**
	 * Builds the chosen mentor's pending camp now, whatever the player's distance from the Crossing
	 * (the {@code story camp} command): true once it stands. See {@code Crossing.buildCampNow}.
	 */
	public static boolean buildCampNow(ServerPlayer player) {
		return Crossing.buildCampNow(player);
	}

	private StorySites() {
	}
}
