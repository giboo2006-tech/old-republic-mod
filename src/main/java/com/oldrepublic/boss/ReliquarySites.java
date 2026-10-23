package com.oldrepublic.boss;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.quest.story.ChapterThreeBalance;
import com.oldrepublic.quest.story.ChapterThreeService;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryBalance;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryService;
import com.oldrepublic.quest.story.StorySites;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Placing the Reliquary, the Collector's sunken hall (spec sections 3.6 and 8.2). Signatures of
 * {@link #place} and {@link #evaluate} are frozen (spec section 15.1).
 *
 * <p>The Reliquary is two templates turned by the same rotation: {@link #SURFACE}, a watchtower stump
 * set on the ground with its lowest layers (the spiral descent) underground (its site meta's
 * {@code buried}, else {@value #SURFACE_BURIED}), and {@link #HALL} directly below it: the hall's top
 * layer is the row under the surface's lowest layer, and the hall's descent column (its {@code SHAFT}
 * anchor, the shaft's foot) lies under the surface's (its {@code SHAFT} anchor, the shaft's top
 * cell), so the spiral runs on into the antechamber. Without those anchors the columns are the old
 * generator's: the hall's {@link #HALL_SHAFT} and the surface's centre column.
 *
 * <p>The story asks {@link FarSites} for the surface first ({@link #request}, docs/MILESTONE_6.md
 * 3.5): {@code chapter_three.reliquaryRange} blocks away, with the hall check below as the request's
 * spot check and a ticket that loads the hall's ground too; {@link #settle} then starts the hall
 * under it. {@link #place} is the near search (the command, and a failed far request): it searches
 * the balance rings for a surface spot that passes the M2 rules and the 3.6 checks and whose hall
 * plus its {@value #SHELL}-block shell passes {@link #evaluate}. Either way the surface is stamped
 * (and recorded in {@code WorldSites}) at once; {@link ReliquaryBuild} lays the hall's shell, then the
 * hall slab by slab, then the heart, over the next ticks, records the hall and hands it to
 * {@link BossArenas#onReliquaryPlaced}.
 */
public final class ReliquarySites {
	public static final Identifier SURFACE = OldRepublicMod.id("boss/reliquary_surface");
	public static final Identifier HALL = OldRepublicMod.id("boss/reliquary_hall");
	/** The surface template's lowest layers set into the ground (3.6) when it has no site meta: the descent; the stump stands above. */
	public static final int SURFACE_BURIED = 9;
	/** The hall's casing that must be free of worldgen pieces and built blocks, filled before the hall. */
	public static final int SHELL = 2;
	/** The hall and its shell stay this far above the world's bottom (3.6). */
	public static final int MIN_Y_MARGIN = 8;
	/** Every ring is measured from the player and capped at this many blocks (3.6, the loaded-chunk reason of 3.5). */
	public static final int MAX_RING = 144;

	/** {@link #evaluate} reasons (lang-free; tests assert them). */
	public static final String UNLOADED = "unloaded";
	public static final String TOO_DEEP = "too_deep";
	public static final String WORLDGEN_PIECE = "worldgen_piece";
	public static final String BUILT_BLOCKS = "built_blocks";

	/**
	 * The descent's column in the hall (template-local, y unused) for a sidecar without {@code SHAFT}; a
	 * surface sidecar without it uses the template's centre column.
	 */
	static final BlockPos HALL_SHAFT = new BlockPos(24, 0, 25);
	/**
	 * How far from its target far placement may move the surface's stand: its surface search and its
	 * clearing search ({@code far.searchSlack}, 24 by default) reach this far.
	 */
	// API-CHECK: J1's RuntimeSites.placeFar searches rings 0 to 24 and the clearing search within far.searchSlack (default 24) of the target; keep this in step.
	private static final int FAR_SLACK = 24;
	/** The widest ground far placement's fit may shape around the surface (its apron), beyond the template's half-width. */
	// API-CHECK: J1's SiteFit.APRON_MAX (24) and clearance margin; the ticket below covers the surface with this much around it.
	private static final int FAR_APRON = 24;
	/** The heart cell of the hall when its sidecar is missing (the generator's HEART anchor). */
	static final BlockPos HALL_HEART = new BlockPos(15, 8, 28);

	/** Side of the square of natural ground the stump needs, the square kept free of built blocks, and the slope allowed. */
	private static final int FOOTPRINT = 13;
	private static final int CLEARANCE = 19;
	private static final int MAX_SLOPE = 3;
	/** Candidates per ring (M2's {@code story.siteAngles}) and the M2 score weight of open sky. */
	private static final int ANGLES = 16;
	private static final double OPENNESS_WEIGHT = 4.0;
	/** Columns of the hall's shell sampled for cover (every this many blocks). */
	private static final int COVER_STEP = 3;

	/** A surface stand that passed every check, with the score M2 ranks spots by. */
	private record Candidate(BlockPos stand, Rotation rotation, double meanHeight, double openness) {
	}

	/** Registers the paced hall build (server tick, server start and stop). */
	public static void init() {
		ReliquaryBuild.init();
	}

	/**
	 * Asks far placement for the owner's Reliquary surface under {@code key} (docs/MILESTONE_6.md
	 * 3.2, 3.5): {@code chapter_three.reliquaryRange} blocks from the owner, kept
	 * {@code story.siteMinSeparation} from the other players' sites, with the hall check (the hall
	 * plus its shell passes {@link #evaluate} and lies under ground) as the request's spot check and a
	 * ticket wide enough to load the hall under every stand the search may take. Idempotent by key:
	 * call it again to poll (which also hands the check back after a restart). FAILED
	 * ({@code missing_template}) without asking when a template is missing. Once it is placed,
	 * {@link #settle} starts the hall.
	 */
	public static FarSites.Status request(ServerPlayer owner, String key) {
		ServerLevel level = owner.level();
		StructureTemplate surface = template(level, SURFACE);
		StructureTemplate hall = template(level, HALL);

		if (surface == null || hall == null) {
			return new FarSites.Status(FarSites.Phase.FAILED, null, null, "missing_template");
		}

		ChapterThreeBalance b = ChapterThreeService.balance();
		return FarSites.request(level, StorySites.request(key, owner.getUUID(), SURFACE, owner.blockPosition(), b.reliquaryRange)
				.avoiding(otherPlayersSites(owner), StoryService.balance().siteMinSeparation)
				// API-CHECK: Spot.templateOrigin() is where the surface's local (0,0,0) goes, as SitePiece.origin() is after the stamp (J1's placeFar).
				.check((l, spot) -> hallPassesUnder(l, surface, hall, spot.templateOrigin(), spot.rotation()))
				.loadRadius(loadRadius(surface, hall)));
	}

	/**
	 * A surface far placement placed for {@code owner}: queues the paced build of the hall under it
	 * and returns the heart position, or null when a template is missing. Safe to repeat: a hall
	 * already queued is not queued twice, and one already built (resumed at a server start before the
	 * owner asked again) is not built again.
	 */
	public static @Nullable BlockPos settle(ServerLevel level, UUID owner, SitePiece surface) {
		StructureTemplate surfaceTemplate = template(level, SURFACE);
		StructureTemplate hall = template(level, HALL);

		if (surfaceTemplate == null || hall == null) {
			return null;
		}

		BlockPos origin = hallOriginUnder(surface.origin(), surfaceTemplate, hall, surface.rotation());
		boolean built = WorldSites.all(level.getServer()).stream()
				.anyMatch(record -> record.template().equals(HALL) && record.dimension().equals(level.dimension()) && record.origin().equals(origin));
		return built ? heartOf(level, hall, surface.rotation(), origin) : startHall(level, owner, surfaceTemplate, hall, surface);
	}

	/**
	 * The far ticket's radius in chunks: the hall and its shell under any stand within
	 * {@value #FAR_SLACK} blocks of the target, in any rotation (one more block for the loaded check),
	 * or, when further, the surface itself with its search slack and the widest apron its fit may lay
	 * ({@value #FAR_APRON}); in the ticket's terms (a radius r loads r + 1 chunks around the target's
	 * chunk for placing).
	 */
	// API-CHECK: J1's ticket convention (FarJobs.radius: (reach + 15) / 16 - 1, at least 1); an explicit radius is not capped by far.maxTicketRadius.
	private static int loadRadius(StructureTemplate surface, StructureTemplate hall) {
		int reach = 0;

		for (Rotation rotation : Rotation.values()) {
			BlockPos surfaceOrigin = RuntimeSites.templateOrigin(surface, BlockPos.ZERO, rotation, surfaceBuried());
			BoundingBox shell = hall.getBoundingBox(settings(rotation), hallOriginUnder(surfaceOrigin, surface, hall, rotation)).inflatedBy(SHELL + 1);
			reach = Math.max(reach, Math.max(Math.max(-shell.minX(), shell.maxX()), Math.max(-shell.minZ(), shell.maxZ())));
		}

		int surfaceReach = Math.max(surface.getSize().getX(), surface.getSize().getZ()) / 2 + FAR_APRON + 1;
		return Math.max(1, (Math.max(reach, surfaceReach) + FAR_SLACK + 15) / 16 - 1);
	}

	/**
	 * Places the owner's Reliquary on the balance rings ahead of {@code origin} along {@code forward},
	 * adds a {@code RELIQUARY} site record with the owner and returns the heart position, or null
	 * when no spot was found (the caller retries later). The surface is placed now; the hall and the
	 * heart follow over the next few ticks.
	 */
	public static @Nullable BlockPos place(ServerPlayer owner, Vec3 origin, Vec3 forward) {
		ServerLevel level = owner.level();
		StructureTemplate surface = template(level, SURFACE);
		StructureTemplate hall = template(level, HALL);

		if (surface == null || hall == null) {
			return null;
		}

		ChapterThreeBalance rings = ChapterThreeService.balance();
		MeetingSites.Rules rules = rules(owner);
		Candidate best = bestOnRings(level, surface, hall, origin, forward, capped(rings.reliquaryRings), rules);

		if (best == null) {
			best = bestOnRings(level, surface, hall, origin, forward, capped(rings.reliquaryFallback), rules);
		}

		if (best == null) {
			OldRepublicMod.LOGGER.warn("No Reliquary site passed for {} around {}; placement will be retried", owner.getPlainTextName(),
					BlockPos.containing(origin));
			return null;
		}

		return build(level, owner.getUUID(), surface, hall, best.stand(), best.rotation());
	}

	/**
	 * Forced placement for commands and client steps: the surface stand at {@code stand}, turned by
	 * {@code rotation}, with no site checks. Returns the heart position; the hall and the heart
	 * follow over the next few ticks.
	 *
	 * @throws IllegalStateException when a template is missing or the surface, the hall or its shell reaches a chunk that is not loaded
	 */
	public static BlockPos placeAt(ServerPlayer owner, BlockPos stand, Rotation rotation) {
		ServerLevel level = owner.level();
		StructureTemplate surface = template(level, SURFACE);
		StructureTemplate hall = template(level, HALL);

		if (surface == null || hall == null) {
			throw new IllegalStateException("Missing Reliquary templates");
		}

		BoundingBox surfaceBox = surface.getBoundingBox(settings(rotation), RuntimeSites.templateOrigin(surface, stand, rotation, surfaceBuried()));
		BoundingBox shell = hallBox(surface, hall, stand, rotation).inflatedBy(SHELL);

		if (!loaded(level, surfaceBox.inflatedBy(1)) || !loaded(level, shell.inflatedBy(1))) {
			throw new IllegalStateException("The Reliquary at " + stand + " reaches a chunk that is not loaded");
		}

		return build(level, owner.getUUID(), surface, hall, stand, rotation);
	}

	/**
	 * The pure placement check of 3.6 for a candidate hall and shell: null when accepted, otherwise
	 * the reason it was refused, checked in this order: {@value #UNLOADED} (a chunk of the box is not
	 * loaded), {@value #TOO_DEEP} (the box reaches within {@value #MIN_Y_MARGIN} blocks of the
	 * world's bottom), {@value #WORLDGEN_PIECE} ({@code isWorldgenPiece} is asked once per chunk the
	 * box touches, with the box's lowest corner clipped into that chunk) and {@value #BUILT_BLOCKS}
	 * (the box holds a {@code #built_blocks} block).
	 */
	public static @Nullable String evaluate(ServerLevel l, BoundingBox hallAndShell, Predicate<BlockPos> isWorldgenPiece) {
		List<ChunkPos> chunks = hallAndShell.intersectingChunks().toList();

		for (ChunkPos chunk : chunks) {
			if (!l.hasChunk(chunk.x(), chunk.z())) {
				return UNLOADED;
			}
		}

		if (hallAndShell.minY() < l.getMinY() + MIN_Y_MARGIN) {
			return TOO_DEEP;
		}

		for (ChunkPos chunk : chunks) {
			BlockPos corner = new BlockPos(Math.max(hallAndShell.minX(), chunk.x() << 4), hallAndShell.minY(), Math.max(hallAndShell.minZ(), chunk.z() << 4));

			if (isWorldgenPiece.test(corner)) {
				return WORLDGEN_PIECE;
			}
		}

		return containsBuilt(l, hallAndShell) ? BUILT_BLOCKS : null;
	}

	/**
	 * The production worldgen predicate for {@link #evaluate}: for the chunk of the position it is
	 * given, whether a worldgen structure piece intersects that chunk's slice of {@code box}
	 * ({@link RuntimeSites#touchesWorldgenPiece}).
	 */
	public static Predicate<BlockPos> worldgenPieces(ServerLevel l, BoundingBox box) {
		return pos -> {
			ChunkPos chunk = ChunkPos.containing(pos);
			int minX = chunk.x() << 4;
			int minZ = chunk.z() << 4;
			BoundingBox slice = new BoundingBox(Math.max(box.minX(), minX), box.minY(), Math.max(box.minZ(), minZ), Math.min(box.maxX(), minX + 15),
					box.maxY(), Math.min(box.maxZ(), minZ + 15));
			return RuntimeSites.touchesWorldgenPiece(l, slice);
		};
	}

	/**
	 * Where the hall's template origin goes for a surface stand: the hall's descent column under the
	 * surface's, its top layer the row under the surface's lowest layer.
	 */
	public static BlockPos hallOrigin(StructureTemplate surface, StructureTemplate hall, BlockPos stand, Rotation rotation) {
		return hallOriginUnder(RuntimeSites.templateOrigin(surface, stand, rotation, surfaceBuried()), surface, hall, rotation);
	}

	/** The hall's template origin under a surface placed at template origin {@code surfaceOrigin}. */
	static BlockPos hallOriginUnder(BlockPos surfaceOrigin, StructureTemplate surface, StructureTemplate hall, Rotation rotation) {
		BlockPos shaft = surfaceOrigin.offset(turn(surfaceShaft(surface), rotation));
		BlockPos hallShaft = turn(hallShaft(), rotation);
		return new BlockPos(shaft.getX() - hallShaft.getX(), surfaceOrigin.getY() - hall.getSize().getY(), shaft.getZ() - hallShaft.getZ());
	}

	/** The descent's column in the surface template: its {@code SHAFT} anchor (the shaft's top cell), else its centre column. */
	static BlockPos surfaceShaft(StructureTemplate surface) {
		List<BlockPos> shaft = SiteAnchors.of(SURFACE, SiteAnchors.Anchor.SHAFT);
		return shaft.isEmpty() ? new BlockPos(surface.getSize().getX() / 2, 0, surface.getSize().getZ() / 2) : shaft.getFirst();
	}

	/** The descent's column in the hall: its {@code SHAFT} anchor (the shaft's foot), else {@link #HALL_SHAFT}. */
	static BlockPos hallShaft() {
		List<BlockPos> shaft = SiteAnchors.of(HALL, SiteAnchors.Anchor.SHAFT);
		return shaft.isEmpty() ? HALL_SHAFT : shaft.getFirst();
	}

	/** The layers the surface sets into the ground: its site meta's {@code buried}, else {@value #SURFACE_BURIED}. */
	public static int surfaceBuried() {
		return StorySites.buried(SURFACE, SURFACE_BURIED);
	}

	/** The hall's box for a surface stand. */
	public static BoundingBox hallBox(StructureTemplate surface, StructureTemplate hall, BlockPos stand, Rotation rotation) {
		return hall.getBoundingBox(settings(rotation), hallOrigin(surface, hall, stand, rotation));
	}

	/** Stamps the surface (recorded by {@link RuntimeSites#placeAt}) and starts the paced hall build; returns the heart position. */
	private static BlockPos build(ServerLevel level, UUID owner, StructureTemplate surface, StructureTemplate hall, BlockPos stand, Rotation rotation) {
		RuntimeSites.Placement placed = RuntimeSites.placeAt(level, SURFACE, stand, rotation, surfaceBuried(), owner);
		return startHall(level, owner, surface, hall, placed.piece());
	}

	/** Queues the paced build of the hall under the placed {@code surface} piece; returns the heart position. */
	private static BlockPos startHall(ServerLevel level, UUID owner, StructureTemplate surfaceTemplate, StructureTemplate hall, SitePiece surface) {
		Rotation rotation = surface.rotation();
		BlockPos origin = hallOriginUnder(surface.origin(), surfaceTemplate, hall, rotation);
		SitePiece piece = hallPiece(level, hall, rotation, origin);
		BlockPos heart = heartOf(piece);
		ReliquaryBuild.start(level, owner, piece, surface.box(), heart);
		return heart;
	}

	/** The hall's piece with its template origin at {@code origin}. */
	private static SitePiece hallPiece(ServerLevel level, StructureTemplate hall, Rotation rotation, BlockPos origin) {
		BoundingBox box = hall.getBoundingBox(settings(rotation), origin);
		return new SitePiece(SiteKind.RELIQUARY, HALL, box, rotation, origin, SitePiece.runtimeKey(level.dimension(), origin));
	}

	private static BlockPos heartOf(ServerLevel level, StructureTemplate hall, Rotation rotation, BlockPos origin) {
		return heartOf(hallPiece(level, hall, rotation, origin));
	}

	/** The hall's {@code HEART} anchor, else the generator's cell. */
	private static BlockPos heartOf(SitePiece hall) {
		BlockPos heart = SiteAnchors.find(hall, SiteAnchors.Anchor.HEART);

		if (heart == null) {
			OldRepublicMod.LOGGER.error("The Reliquary hall sidecar has no HEART anchor; using the generator's cell");
			heart = SiteAnchors.toWorld(hall, HALL_HEART);
		}

		return heart;
	}

	/**
	 * Paced builds are not saved, so a build the server stopped before it finished (its chunks were
	 * unloaded) is found again here, once the server has started: an owned Reliquary surface in
	 * {@code WorldSites} with no hall record under it gets its hall queued again from the shell (every
	 * step is safe to repeat). The hall's record is only written when its heart is set.
	 */
	static void resumeUnfinished(MinecraftServer server) {
		List<WorldSites.SiteRecord> records = WorldSites.all(server);

		for (WorldSites.SiteRecord record : records) {
			if (record.kind() != SiteKind.RELIQUARY || !record.template().equals(SURFACE) || record.owner() == null) {
				continue;
			}

			ServerLevel level = server.getLevel(record.dimension());
			StructureTemplate surfaceTemplate = level == null ? null : template(level, SURFACE);
			StructureTemplate hall = level == null ? null : template(level, HALL);
			SitePiece surface = level == null ? null : RuntimeSites.pieceOf(level, record);

			if (surfaceTemplate == null || hall == null || surface == null) {
				continue;
			}

			BlockPos origin = hallOriginUnder(record.origin(), surfaceTemplate, hall, record.rotation());
			boolean finished = records.stream()
					.anyMatch(other -> other.template().equals(HALL) && other.dimension().equals(record.dimension()) && other.origin().equals(origin));

			if (!finished) {
				OldRepublicMod.LOGGER.info("Resuming the unfinished Reliquary hall at {} for {}", origin, record.owner());
				startHall(level, record.owner(), surfaceTemplate, hall, surface);
			}
		}
	}

	private static @Nullable Candidate bestOnRings(ServerLevel level, StructureTemplate surface, StructureTemplate hall, Vec3 origin, Vec3 forward,
			int[] rings, MeetingSites.Rules rules) {
		Vec3 flat = forward.horizontal();
		double phase = flat.lengthSqr() < 1.0e-4 ? 0 : Math.atan2(flat.z, flat.x);
		Candidate best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (int ring : rings) {
			List<Candidate> passed = new ArrayList<>();

			for (int i = 0; i < ANGLES; i++) {
				double angle = phase + i * Math.PI * 2 / ANGLES;
				int cx = (int) Math.floor(origin.x + Math.cos(angle) * ring);
				int cz = (int) Math.floor(origin.z + Math.sin(angle) * ring);
				MeetingSites.Candidate spot = MeetingSites.evaluate(level, cx, cz, rules);
				Rotation rotation = spot == null ? null : facingOrigin(spot.stand(), origin, forward);

				if (spot != null && surfacePasses(level, surface, spot.stand(), rotation, rules) && hallPasses(level, surface, hall, spot.stand(), rotation)) {
					passed.add(new Candidate(spot.stand(), rotation, spot.meanHeight(), spot.openness()));
				}
			}

			double average = passed.stream().mapToDouble(Candidate::meanHeight).average().orElse(0);

			for (Candidate candidate : passed) {
				double score = candidate.meanHeight() - average + candidate.openness() * OPENNESS_WEIGHT;

				if (score > bestScore) {
					bestScore = score;
					best = candidate;
				}
			}
		}

		return best;
	}

	/** The rotation that turns the stump's door (the template's north side) toward the search origin. */
	private static Rotation facingOrigin(BlockPos stand, Vec3 origin, Vec3 forward) {
		double dx = origin.x - (stand.getX() + 0.5);
		double dz = origin.z - (stand.getZ() + 0.5);

		if (dx * dx + dz * dz < 1) {
			dx = -forward.x;
			dz = -forward.z;
		}

		Direction toOrigin = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		return RuntimeSites.facing(toOrigin);
	}

	/** The 3.6 surface checks beyond {@link MeetingSites#evaluate}, as {@code RuntimeSites} makes them for surface sites. */
	private static boolean surfacePasses(ServerLevel level, StructureTemplate surface, BlockPos stand, Rotation rotation, MeetingSites.Rules rules) {
		BoundingBox box = surface.getBoundingBox(settings(rotation), RuntimeSites.templateOrigin(surface, stand, rotation, surfaceBuried()));
		int half = rules.clearance() / 2;
		BoundingBox clearance = BoundingBox.encapsulating(box,
				new BoundingBox(stand.getX() - half, box.minY(), stand.getZ() - half, stand.getX() + half, box.maxY(), stand.getZ() + half));
		return loaded(level, clearance.inflatedBy(1)) && !RuntimeSites.touchesWorldgenPiece(level, clearance) && !containsBuilt(level, box);
	}

	/** The hall plus its shell passes {@link #evaluate} and lies under ground everywhere (no open cliff or ravine above it). */
	private static boolean hallPasses(ServerLevel level, StructureTemplate surface, StructureTemplate hall, BlockPos stand, Rotation rotation) {
		return hallPassesUnder(level, surface, hall, RuntimeSites.templateOrigin(surface, stand, rotation, surfaceBuried()), rotation);
	}

	/** {@link #hallPasses} for a surface placed at template origin {@code surfaceOrigin} (far placement's spot check). */
	private static boolean hallPassesUnder(ServerLevel level, StructureTemplate surface, StructureTemplate hall, BlockPos surfaceOrigin, Rotation rotation) {
		BoundingBox shell = hall.getBoundingBox(settings(rotation), hallOriginUnder(surfaceOrigin, surface, hall, rotation)).inflatedBy(SHELL);
		return loaded(level, shell.inflatedBy(1)) && evaluate(level, shell, worldgenPieces(level, shell)) == null && covered(level, shell);
	}

	/** Whether the ground stands above the shell over its whole footprint (sampled every few columns). */
	private static boolean covered(ServerLevel level, BoundingBox shell) {
		for (int x = shell.minX(); x <= shell.maxX(); x += COVER_STEP) {
			for (int z = shell.minZ(); z <= shell.maxZ(); z += COVER_STEP) {
				if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) <= shell.maxY() + 1) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * The live rules for the owner's stump: footprint, clearance and slope (from the surface's site
	 * meta and size when it has meta, docs/MILESTONE_6.md 3.4), spawn protection, and
	 * {@code story.siteMinSeparation} from the other players' story sites and runtime sites (3.6).
	 */
	private static MeetingSites.Rules rules(ServerPlayer owner) {
		ServerLevel level = owner.level();
		return StorySites.nearRules(level, SURFACE, FOOTPRINT, CLEARANCE, MAX_SLOPE, spawnProtection(level, StorySites.halfSide(level, SURFACE, CLEARANCE / 2)),
				otherPlayersSites(owner), StoryService.balance().siteMinSeparation);
	}

	/** Stands inside spawn protection when the story balance asks for it to be respected (the M2 rule, overworld only). */
	private static Predicate<BlockPos> spawnProtection(ServerLevel level, int half) {
		StoryBalance b = StoryService.balance();

		if (!b.respectSpawnProtection || level.dimension() != Level.OVERWORLD || b.spawnProtectionRadius <= 0) {
			return pos -> false;
		}

		BlockPos spawn = level.getRespawnData().pos();
		int reach = b.spawnProtectionRadius + Math.max(CLEARANCE / 2, half) + 1;
		return pos -> Math.abs(pos.getX() - spawn.getX()) <= reach && Math.abs(pos.getZ() - spawn.getZ()) <= reach;
	}

	/** The other online players' M2 story sites and every runtime site in this dimension someone else owns. */
	private static List<BlockPos> otherPlayersSites(ServerPlayer owner) {
		List<BlockPos> sites = new ArrayList<>();

		for (ServerPlayer other : owner.level().getServer().getPlayerList().getPlayers()) {
			if (other != owner) {
				StoryData data = Story.get(other);
				addIfPresent(sites, data.meeting);
				addIfPresent(sites, data.camp);
				addIfPresent(sites, data.encounterPos);
			}
		}

		for (WorldSites.SiteRecord record : WorldSites.all(owner.level().getServer())) {
			if (record.owner() != null && !record.owner().equals(owner.getUUID()) && record.dimension().equals(owner.level().dimension())) {
				sites.add(record.origin());
			}
		}

		return sites;
	}

	/** The rings with each one capped at {@link #MAX_RING} (a config edit cannot push them past it). */
	public static int[] capped(int[] rings) {
		return Arrays.stream(rings).map(ring -> Math.min(MAX_RING, ring)).toArray();
	}

	private static void addIfPresent(List<BlockPos> sites, @Nullable BlockPos pos) {
		if (pos != null && !sites.contains(pos)) {
			sites.add(pos);
		}
	}

	static boolean containsBuilt(ServerLevel l, BoundingBox box) {
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

	static boolean loaded(ServerLevel l, BoundingBox box) {
		return box.intersectingChunks().allMatch(chunk -> l.hasChunk(chunk.x(), chunk.z()));
	}

	/** Placement settings of both templates: the rotation about {@link BlockPos#ZERO}, no mirror, no entities (as {@code RuntimeSites}). */
	static StructurePlaceSettings settings(Rotation rotation) {
		return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(BlockPos.ZERO).setIgnoreEntities(true);
	}

	private static BlockPos turn(BlockPos local, Rotation rotation) {
		return StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO);
	}

	static @Nullable StructureTemplate template(ServerLevel level, Identifier id) {
		Optional<StructureTemplate> template = level.getStructureManager().get(id);

		if (template.isEmpty()) {
			OldRepublicMod.LOGGER.error("Missing Reliquary template {}", id);
			return null;
		}

		return template.get();
	}

	private ReliquarySites() {
	}
}
