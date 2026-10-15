package com.oldrepublic.world;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Finds crystal caves for "The Calling" and tells whether a player is inside one.
 *
 * <p>Searches use the vanilla structure locator on the {@code #oldrepublic:crystal_caves} tag and
 * are cached per player. A player without a usable cache gets an immediate search (at most one
 * per server tick); a stale cache is returned at once and refreshed in the background queue
 * (also one search per tick server-wide). Once the cave is within {@code caveHeartRefineBlocks},
 * the result is refined from the cave's entrance to its meditation stone.
 */
public final class CaveLocator {
	public static final TagKey<Structure> CRYSTAL_CAVES = TagKey.create(Registries.STRUCTURE, OldRepublicMod.id("crystal_caves"));
	public static final Identifier CAMP_TEMPLATE = OldRepublicMod.id("crystal_cave/camp");
	public static final Identifier DESCENT_TEMPLATE = OldRepublicMod.id("crystal_cave/descent");
	public static final Identifier HEART_TEMPLATE = OldRepublicMod.id("crystal_cave/heart");
	/** The meditation stone inside the heart template (see {@code tools/assets/gen_structures.py}). */
	public static final BlockPos HEART_STONE_LOCAL = new BlockPos(22, 1, 38);

	private record Cached(ResourceKey<Level> dimension, BlockPos query, @Nullable BlockPos result, long time) {
	}

	private static final Map<UUID, Cached> CACHE = new HashMap<>();
	private static final ArrayDeque<UUID> QUEUE = new ArrayDeque<>();
	private static final Set<UUID> QUEUED = new HashSet<>();
	private static final Map<UUID, ServerPlayer> QUEUED_PLAYERS = new HashMap<>();
	private static boolean searchedThisTick;

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CaveLocator::tick);
	}

	/**
	 * Nearest crystal cave heart (the trial chamber) to the player in their current dimension,
	 * within a search radius, or null. Results may be cached; cheap enough to call every few
	 * seconds per player.
	 */
	public static @Nullable BlockPos findNearestCave(ServerPlayer player) {
		ServerLevel level = player.level();
		Cached cached = CACHE.get(player.getUUID());

		if (cached != null && cached.dimension() == level.dimension()) {
			if (!isFresh(cached, player, level)) {
				enqueue(player);
			}

			return cached.result();
		}

		if (!searchedThisTick) {
			searchedThisTick = true;
			return search(player).result();
		}

		enqueue(player);
		return null;
	}

	/** Searches now, ignoring the cache (used by {@code /oldrepublic locatecave}). */
	public static @Nullable BlockPos findNearestCaveNow(ServerPlayer player) {
		return search(player).result();
	}

	/** Whether the player is currently inside a crystal cave structure. */
	public static boolean isInsideCave(ServerPlayer player) {
		return pieceAt(player.level(), player.blockPosition()) != null;
	}

	/**
	 * The template id of the crystal cave piece whose bounds contain {@code pos}
	 * ({@link #CAMP_TEMPLATE}, {@link #DESCENT_TEMPLATE} or {@link #HEART_TEMPLATE}), or null.
	 */
	public static @Nullable Identifier pieceAt(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, CRYSTAL_CAVES);

		if (start == null || !start.isValid()) {
			return null;
		}

		for (StructurePiece piece : start.getPieces()) {
			if (piece.getBoundingBox().isInside(pos)) {
				Identifier template = templateOf(piece);
				return template != null ? template : HEART_TEMPLATE;
			}
		}

		return null;
	}

	public static void forget(UUID player) {
		CACHE.remove(player);

		if (QUEUED.remove(player)) {
			QUEUE.remove(player);
			QUEUED_PLAYERS.remove(player);
		}
	}

	private static boolean isFresh(Cached cached, ServerPlayer player, ServerLevel level) {
		WorldBalance b = WorldBalance.get();
		long age = level.getGameTime() - cached.time();
		double move = b.caveCacheMoveBlocks;
		return age >= 0 && age < b.caveCacheSeconds * 20L && cached.query().distSqr(player.blockPosition()) < move * move;
	}

	private static void enqueue(ServerPlayer player) {
		if (QUEUED.add(player.getUUID())) {
			QUEUE.add(player.getUUID());
		}

		QUEUED_PLAYERS.put(player.getUUID(), player);
	}

	private static void tick(MinecraftServer server) {
		boolean budgetUsed = searchedThisTick;
		searchedThisTick = false;

		if (budgetUsed || QUEUE.isEmpty()) {
			return;
		}

		UUID id = QUEUE.poll();
		QUEUED.remove(id);
		ServerPlayer player = QUEUED_PLAYERS.remove(id);

		if (player != null && !player.isRemoved() && !player.hasDisconnected()) {
			search(player);
		}
	}

	private static Cached search(ServerPlayer player) {
		WorldBalance b = WorldBalance.get();
		ServerLevel level = player.level();
		BlockPos origin = player.blockPosition();
		BlockPos result = null;

		try {
			BlockPos start = level.findNearestMapStructure(CRYSTAL_CAVES, origin, Math.max(1, b.caveSearchRadius), false);

			if (start != null) {
				result = start;
				double dx = start.getX() - origin.getX();
				double dz = start.getZ() - origin.getZ();

				if (dx * dx + dz * dz <= (double) b.caveHeartRefineBlocks * b.caveHeartRefineBlocks) {
					BlockPos heart = heartOf(level, start);

					if (heart != null) {
						result = heart;
					}
				}
			}
		} catch (RuntimeException e) {
			OldRepublicMod.LOGGER.warn("Crystal cave search failed near {}", origin, e);
		}

		Cached cached = new Cached(level.dimension(), origin, result, level.getGameTime());
		CACHE.put(player.getUUID(), cached);
		return cached;
	}

	/** The meditation stone of the cave whose start lies in the chunk of {@code start}, or null. */
	static @Nullable BlockPos heartOf(ServerLevel level, BlockPos start) {
		ChunkAccess chunk = level.getChunk(start.getX() >> 4, start.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true);

		if (chunk == null) {
			return null;
		}

		for (StructureStart structureStart : chunk.getAllStarts().values()) {
			if (structureStart == null || !structureStart.isValid()) {
				continue;
			}

			for (StructurePiece piece : structureStart.getPieces()) {
				if (piece instanceof PoolElementStructurePiece poolPiece && HEART_TEMPLATE.equals(templateOf(piece))) {
					// API-CHECK: jigsaw pieces place their template with rotation pivot BlockPos.ZERO
					// (SinglePoolElement#getSettings does not set a pivot), so this matches placement.
					return poolPiece.getPosition().offset(StructureTemplate.transform(HEART_STONE_LOCAL, Mirror.NONE, poolPiece.getRotation(), BlockPos.ZERO));
				}
			}
		}

		return null;
	}

	private static @Nullable Identifier templateOf(StructurePiece piece) {
		if (piece instanceof PoolElementStructurePiece poolPiece && poolPiece.getElement() instanceof SinglePoolElement single) {
			return single.getTemplateLocation();
		}

		return null;
	}

	private CaveLocator() {
	}
}
