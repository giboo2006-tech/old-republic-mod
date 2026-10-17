package com.oldrepublic.world.site;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.world.CaveLocator;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/**
 * Finds sites for players (spec 3.8): the nearest site of a kind, asynchronously and per player;
 * the site piece at a position; and an anchor anywhere in a piece's site.
 *
 * <p>Searches go through one server-wide {@link LocatorQueue} worked at the end of every server
 * tick, so a search costs at most {@code sites.locatorCellsPerTick} cell checks per tick across all
 * players; the {@link WorldgenLookup} decides which placement cells to check and what they hold.
 * {@link #piecesNear} and {@link #sitePieces} are synchronous reads for nearby pieces and whole sites.
 */
public final class StructureLocator {
	/** The structure tags of the M3 worldgen kinds ({@code data/oldrepublic/tags/worldgen/structure/}); crystal caves use {@link CaveLocator#CRYSTAL_CAVES}. */
	public static final TagKey<Structure> JEDI_RUINS = tag("jedi_ruins");
	public static final TagKey<Structure> SITH_TOMBS = tag("sith_tombs");
	public static final TagKey<Structure> WRECK_FIELDS = tag("wreck_fields");
	public static final TagKey<Structure> REPUBLIC_OUTPOSTS = tag("republic_outposts");
	public static final TagKey<Structure> IMPERIAL_OUTPOSTS = tag("imperial_outposts");

	/** Runtime pieces of one site lie within this many blocks of each other (record origins); also the pieceAt prefilter. */
	private static final int RUNTIME_SITE_SPAN = 64;

	private static @Nullable MinecraftServer server;
	private static LocatorQueue queue = newQueue(new SiteBalance());

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(started -> {
			server = started;
			queue = newQueue(SiteBalance.get());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			queue.clear();
			server = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(ticking -> queue.tick(ticking.overworld().getGameTime()));
		ServerPlayerEvents.LEAVE.register(player -> queue.forget(player.getUUID()));
	}

	/**
	 * Queues a search for the site of {@code kind} nearest to the player, within {@code radiusBlocks}
	 * (horizontal blocks, converted to placement cells internally) and not excluded by
	 * {@code exclude}. The callback gets the site's start piece, or null when there is none; it runs
	 * at once on a fresh cached result, otherwise on a later server tick, and never if the player
	 * leaves first.
	 */
	public static void find(ServerPlayer p, SiteKind kind, int radiusBlocks, @Nullable Predicate<SitePiece> exclude, Consumer<@Nullable SitePiece> callback) {
		LocatorQueue.Search search = new LocatorQueue.Search(p.level(), p.getUUID(), kind, p.blockPosition(), radiusBlocks, exclude);
		queue.submit(search, now(p), callback);
	}

	/** The player's cached result for {@code kind}, read synchronously; null when there is none or it went stale. Never queues. */
	public static @Nullable SitePiece cached(ServerPlayer p, SiteKind kind) {
		return queue.cached(p.getUUID(), kind, p.level().dimension(), p.blockPosition(), now(p));
	}

	/**
	 * The site piece containing {@code pos}: a piece of a worldgen site (through the kinds' structure
	 * tags), else a runtime placement recorded in {@link WorldSites}; null when there is none.
	 */
	public static @Nullable SitePiece pieceAt(ServerLevel level, BlockPos pos) {
		for (SiteKind kind : SiteKind.values()) {
			TagKey<Structure> tag = structureTag(kind);

			if (tag == null) {
				continue;
			}

			StructureStart start = level.structureManager().getStructureWithPieceAt(pos, tag);

			if (start == null || !start.isValid()) {
				continue;
			}

			for (StructurePiece piece : start.getPieces()) {
				SitePiece site = piece.getBoundingBox().isInside(pos) ? worldgenPiece(level.dimension(), kind, start, piece) : null;

				if (site != null) {
					return site;
				}
			}
		}

		for (WorldSites.SiteRecord record : WorldSites.all(level.getServer())) {
			if (record.dimension().equals(level.dimension()) && near(record.origin(), pos, RUNTIME_SITE_SPAN)) {
				SitePiece site = RuntimeSites.pieceOf(level, record);

				if (site != null && site.box().isInside(pos)) {
					return site;
				}
			}
		}

		return null;
	}

	/**
	 * The first cell of {@code anchor} in the whole site of {@code anyPiece}, in world space: the
	 * piece itself first, then every other piece of the same structure start (worldgen) or the other
	 * records of the same kind and owner within 64 blocks (runtime). Null when the site has none.
	 */
	public static @Nullable BlockPos anchor(SitePiece anyPiece, SiteAnchors.Anchor anchor) {
		BlockPos own = SiteAnchors.find(anyPiece, anchor);

		if (own != null) {
			return own;
		}

		MinecraftServer current = server;
		ResourceKey<Level> dimension = anyPiece.dimension();
		ServerLevel level = current == null || dimension == null ? null : current.getLevel(dimension);

		if (level == null) {
			return null;
		}

		for (SitePiece piece : anyPiece.runtime() ? runtimeSiblings(level, anyPiece) : worldgenSiblings(level, anyPiece)) {
			BlockPos found = SiteAnchors.find(piece, anchor);

			if (found != null) {
				return found;
			}
		}

		return null;
	}

	/**
	 * Every site piece whose box comes within {@code radius} blocks of {@code pos} horizontally,
	 * nearest first (4.6): pieces of worldgen sites of every kind with a structure tag, and runtime
	 * placements recorded in {@link WorldSites}. Synchronous and never queued; only the loaded chunks
	 * of the square are read (the starts they reference), so it never generates a chunk.
	 */
	public static List<SitePiece> piecesNear(ServerLevel level, BlockPos pos, int radius) {
		int reach = Math.max(0, radius);
		Map<SitePiece, Long> found = new LinkedHashMap<>();
		Map<SiteKind, Set<Structure>> kinds = new EnumMap<>(SiteKind.class);

		for (SiteKind kind : SiteKind.values()) {
			TagKey<Structure> tag = structureTag(kind);

			if (tag != null) {
				kinds.put(kind, structures(level, tag));
			}
		}

		int minChunkX = (pos.getX() - reach) >> 4;
		int maxChunkX = (pos.getX() + reach) >> 4;
		int minChunkZ = (pos.getZ() - reach) >> 4;
		int maxChunkZ = (pos.getZ() + reach) >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ)) {
					continue;
				}

				for (Map.Entry<SiteKind, Set<Structure>> kind : kinds.entrySet()) {
					Set<Structure> structures = kind.getValue();

					for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(chunkX, chunkZ), structures::contains)) {
						if (!start.isValid()) {
							continue;
						}

						for (StructurePiece piece : start.getPieces()) {
							long distSqr = boxDistSqr(pos, piece.getBoundingBox());
							SitePiece site = distSqr <= (long) reach * reach ? worldgenPiece(level.dimension(), kind.getKey(), start, piece) : null;

							if (site != null) {
								found.putIfAbsent(site, distSqr);
							}
						}
					}
				}
			}
		}

		for (WorldSites.SiteRecord record : WorldSites.all(level.getServer())) {
			if (record.dimension().equals(level.dimension()) && near(record.origin(), pos, RUNTIME_SITE_SPAN + reach)) {
				SitePiece site = RuntimeSites.pieceOf(level, record);
				long distSqr = site == null ? Long.MAX_VALUE : boxDistSqr(pos, site.box());

				if (site != null && distSqr <= (long) reach * reach) {
					found.putIfAbsent(site, distSqr);
				}
			}
		}

		List<SitePiece> pieces = new ArrayList<>(found.keySet());
		pieces.sort(Comparator.comparingLong(found::get));
		return pieces;
	}

	/**
	 * Every piece of the site {@code anyPiece} belongs to, {@code anyPiece} first (4.6): the other
	 * pieces of its worldgen start in the start's order, or the other runtime records of the same
	 * kind and owner within 64 blocks, nearest first. Just {@code anyPiece} when its level is gone.
	 */
	public static List<SitePiece> sitePieces(SitePiece anyPiece) {
		MinecraftServer current = server;
		ResourceKey<Level> dimension = anyPiece.dimension();
		ServerLevel level = current == null || dimension == null ? null : current.getLevel(dimension);
		List<SitePiece> pieces = new ArrayList<>();
		pieces.add(anyPiece);

		if (level != null) {
			pieces.addAll(anyPiece.runtime() ? runtimeSiblings(level, anyPiece) : worldgenSiblings(level, anyPiece));
		}

		return pieces;
	}

	/** The structure tag that finds a worldgen kind (3.8), or null for kinds that only exist at runtime. */
	public static @Nullable TagKey<Structure> structureTag(SiteKind kind) {
		return switch (kind) {
			case CRYSTAL_CAVE -> CaveLocator.CRYSTAL_CAVES;
			case JEDI_RUIN -> JEDI_RUINS;
			case SITH_TOMB -> SITH_TOMBS;
			case WRECK_FIELD -> WRECK_FIELDS;
			case REPUBLIC_OUTPOST -> REPUBLIC_OUTPOSTS;
			case IMPERIAL_OUTPOST -> IMPERIAL_OUTPOSTS;
			case ECHO_SHRINE, COURIERS_FALL, MISSION_SITE, RELIQUARY -> null;
		};
	}

	/**
	 * A worldgen piece as a {@link SitePiece}: its template, box, rotation and placement position,
	 * keyed by the start's chunk. Null for pieces that are not single templates.
	 */
	static @Nullable SitePiece worldgenPiece(ResourceKey<Level> dimension, SiteKind kind, StructureStart start, StructurePiece piece) {
		if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single) {
			Identifier template = single.getTemplateLocation();
			// API-CHECK: jigsaw pieces place their template at getPosition() with rotation pivot BlockPos.ZERO
			// (SinglePoolElement#getSettings sets no pivot), as CaveLocator.heartOf assumes.
			return new SitePiece(kind, template, piece.getBoundingBox(), pool.getRotation(), pool.getPosition(), SitePiece.worldgenKey(dimension, start.getChunkPos()));
		}

		return null;
	}

	/** Every other piece of the worldgen start that holds {@code piece}, in the start's piece order. */
	private static List<SitePiece> worldgenSiblings(ServerLevel level, SitePiece piece) {
		ChunkPos startChunk = piece.startChunk();

		if (startChunk == null) {
			return List.of();
		}

		ChunkAccess chunk = level.getChunk(startChunk.x(), startChunk.z(), ChunkStatus.STRUCTURE_STARTS, true);

		if (chunk == null) {
			return List.of();
		}

		for (StructureStart start : chunk.getAllStarts().values()) {
			if (start == null || !start.isValid() || !holds(start, piece)) {
				continue;
			}

			List<SitePiece> siblings = new ArrayList<>();

			for (StructurePiece other : start.getPieces()) {
				SitePiece sibling = worldgenPiece(level.dimension(), piece.kind(), start, other);

				if (sibling != null && !sibling.equals(piece)) {
					siblings.add(sibling);
				}
			}

			return siblings;
		}

		return List.of();
	}

	private static boolean holds(StructureStart start, SitePiece piece) {
		for (StructurePiece other : start.getPieces()) {
			if (other.getBoundingBox().equals(piece.box()) && other instanceof PoolElementStructurePiece pool && pool.getPosition().equals(piece.origin())) {
				return true;
			}
		}

		return false;
	}

	/** The other records of the same kind and owner within 64 blocks of the piece's record, nearest first. */
	private static List<SitePiece> runtimeSiblings(ServerLevel level, SitePiece piece) {
		List<WorldSites.SiteRecord> records = WorldSites.all(level.getServer());
		WorldSites.SiteRecord own = null;

		for (WorldSites.SiteRecord record : records) {
			if (record.dimension().equals(level.dimension()) && record.origin().equals(piece.origin()) && record.template().equals(piece.template())) {
				own = record;
				break;
			}
		}

		if (own == null) {
			return List.of();
		}

		List<WorldSites.SiteRecord> others = new ArrayList<>();

		for (WorldSites.SiteRecord record : records) {
			if (record != own && record.kind() == own.kind() && record.dimension().equals(own.dimension())
					&& Objects.equals(record.owner(), own.owner()) && near(record.origin(), own.origin(), RUNTIME_SITE_SPAN)) {
				others.add(record);
			}
		}

		BlockPos from = own.origin();
		others.sort(Comparator.comparingDouble(record -> record.origin().distSqr(from)));
		List<SitePiece> siblings = new ArrayList<>(others.size());

		for (WorldSites.SiteRecord record : others) {
			SitePiece sibling = RuntimeSites.pieceOf(level, record);

			if (sibling != null) {
				siblings.add(sibling);
			}
		}

		return siblings;
	}

	/** The structures of a structure tag in this level's registry. */
	private static Set<Structure> structures(ServerLevel level, TagKey<Structure> tag) {
		Set<Structure> structures = Collections.newSetFromMap(new IdentityHashMap<>());

		for (Holder<Structure> holder : structureRegistry(level).getTagOrEmpty(tag)) {
			structures.add(holder.value());
		}

		return structures;
	}

	private static Registry<Structure> structureRegistry(ServerLevel level) {
		// API-CHECK: RegistryAccess#lookupOrThrow(ResourceKey) returns the Registry (as Fabric's TranslationConventionLogWarnings uses it).
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
	}

	/** Horizontal distance squared from {@code pos} to the nearest column of {@code box} (0 inside). */
	private static long boxDistSqr(BlockPos pos, BoundingBox box) {
		long dx = Math.max(0, Math.max(box.minX() - pos.getX(), pos.getX() - box.maxX()));
		long dz = Math.max(0, Math.max(box.minZ() - pos.getZ(), pos.getZ() - box.maxZ()));
		return dx * dx + dz * dz;
	}

	private static boolean near(BlockPos a, BlockPos b, int span) {
		return Math.abs(a.getX() - b.getX()) <= span && Math.abs(a.getZ() - b.getZ()) <= span;
	}

	private static long now(ServerPlayer p) {
		return p.level().getServer().overworld().getGameTime();
	}

	private static LocatorQueue newQueue(SiteBalance balance) {
		return new LocatorQueue(new WorldgenLookup(), balance.locatorCellsPerTick, balance.locatorCacheTicks, balance.locatorCacheMove);
	}

	private static TagKey<Structure> tag(String path) {
		return TagKey.create(Registries.STRUCTURE, OldRepublicMod.id(path));
	}

	/**
	 * The worldgen lookup of 3.8. A search's cells are the regions of the random-spread placement of
	 * the kind's structures (the first random-spread placement the dimension's generator holds for a
	 * structure of the kind's tag), in rings around the player's region
	 * ({@link LocatorQueue#rings}, {@link LocatorQueue#ringCells}, capped at
	 * {@code sites.locatorMaxRings}). A cell's check reads the region's potential start chunk at
	 * {@code STRUCTURE_STARTS} and keeps the starts of the kind's tag, as their start piece; it also
	 * returns the runtime records ({@link WorldSites}) of the kind whose origin lies in that region,
	 * so runtime and worldgen sites compete ring by ring. A kind without a placement (the runtime
	 * kinds, or a dimension that does not generate it) gets one cell holding all its records in the
	 * dimension. The story's own positions (the player's shrines, Courier's Fall, the Reliquary) are
	 * found through these records: every story placement goes through {@link RuntimeSites}, which
	 * records it.
	 */
	private static final class WorldgenLookup implements LocatorQueue.Lookup {
		@Override
		public List<LocatorQueue.Cell> plan(LocatorQueue.Search search) {
			RandomSpreadStructurePlacement placement = placement(search.level(), search.kind());

			if (placement == null) {
				return List.of(new LocatorQueue.Cell(0, 0, 0));
			}

			int spacing = Math.max(1, placement.spacing());
			int rings = LocatorQueue.rings(search.radiusBlocks(), spacing, SiteBalance.get().locatorMaxRings);
			return LocatorQueue.ringCells(Math.floorDiv(search.from().getX() >> 4, spacing), Math.floorDiv(search.from().getZ() >> 4, spacing), rings);
		}

		@Override
		public List<SitePiece> check(LocatorQueue.Search search, LocatorQueue.Cell cell) {
			ServerLevel level = search.level();
			SiteKind kind = search.kind();
			RandomSpreadStructurePlacement placement = placement(level, kind);
			List<SitePiece> found = new ArrayList<>();

			if (placement == null) {
				for (WorldSites.SiteRecord record : WorldSites.all(level.getServer())) {
					addRuntime(level, kind, record, found);
				}

				return found;
			}

			int spacing = Math.max(1, placement.spacing());
			TagKey<Structure> tag = structureTag(kind);

			if (tag != null) {
				// API-CHECK: ChunkGeneratorStructureState#getLevelSeed and RandomSpreadStructurePlacement#getPotentialStructureChunk
				// (chunk coordinates in, the region's start chunk out), as ChunkGenerator#findNearestMapStructure uses them.
				long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
				ChunkPos potential = placement.getPotentialStructureChunk(seed, cell.x() * spacing, cell.z() * spacing);
				ChunkAccess chunk = level.getChunk(potential.x(), potential.z(), ChunkStatus.STRUCTURE_STARTS, true);
				Set<Structure> structures = structures(level, tag);

				if (chunk != null) {
					for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
						StructureStart start = entry.getValue();

						if (start != null && start.isValid() && structures.contains(entry.getKey()) && !start.getPieces().isEmpty()) {
							SitePiece piece = worldgenPiece(level.dimension(), kind, start, start.getPieces().getFirst());

							if (piece != null) {
								found.add(piece);
							}
						}
					}
				}
			}

			for (WorldSites.SiteRecord record : WorldSites.all(level.getServer())) {
				if (Math.floorDiv(record.origin().getX() >> 4, spacing) == cell.x() && Math.floorDiv(record.origin().getZ() >> 4, spacing) == cell.z()) {
					addRuntime(level, kind, record, found);
				}
			}

			return found;
		}

		private static void addRuntime(ServerLevel level, SiteKind kind, WorldSites.SiteRecord record, List<SitePiece> found) {
			if (record.kind() == kind && record.dimension().equals(level.dimension())) {
				SitePiece piece = RuntimeSites.pieceOf(level, record);

				if (piece != null) {
					found.add(piece);
				}
			}
		}

		/** The random-spread placement of the kind's structures in this level's generator, or null. */
		private static @Nullable RandomSpreadStructurePlacement placement(ServerLevel level, SiteKind kind) {
			TagKey<Structure> tag = structureTag(kind);

			if (tag == null) {
				return null;
			}

			// API-CHECK: ChunkGeneratorStructureState#getPlacementsForStructure(Holder<Structure>) (called by
			// ChunkGenerator#findNearestMapStructure in the world dump).
			for (Holder<Structure> structure : structureRegistry(level).getTagOrEmpty(tag)) {
				for (StructurePlacement placement : level.getChunkSource().getGeneratorState().getPlacementsForStructure(structure)) {
					if (placement instanceof RandomSpreadStructurePlacement spread) {
						return spread;
					}
				}
			}

			return null;
		}
	}

	private StructureLocator() {
	}
}
