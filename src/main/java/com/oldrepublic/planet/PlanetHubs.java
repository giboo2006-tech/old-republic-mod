package com.oldrepublic.planet;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The shared hub of each planet (spec MILESTONE_4.md 3.5): placed once per server, the first time a
 * ship sets course for the planet ({@link #ensure}).
 *
 * <p>{@link #ensure} holds a temporary {@code PORTAL} chunk ticket of {@code planets.hubTicketRadius}
 * chunks around (0, 0) and waits, tick by tick, until all those chunks are loaded; then it searches
 * the rings {@code planets.hubRings} for a stand and places the hub, in this order:
 * <ol>
 * <li>the M3 search ({@link RuntimeSites#placeSurface} with a {@code hubFootprint} square of
 * story ground and at most {@code hubMaxSlope} of slope);</li>
 * <li>if no ring passes (Selvaren's highwood is rarely bare enough), the same rings with trees
 * allowed: the trees and natural ground standing in the hub's box are cleared first;</li>
 * <li>if still nothing fits, a raised platform at (0, 0) on stilts to the ground, logged.</li>
 * </ol>
 * The Proving's placement also places Zarvos's Sepulchre under it, joined to the Proving's carved
 * stair. Positions are kept in {@link PlanetSiteData} and the templates in {@link WorldSites}.
 *
 * <p>A hub keeps no wild creatures (3.4): placing it removes the animals and monsters worldgen left in
 * its box, and natural spawns never happen in a hub's or the Sepulchre's box ({@link #allowLoad}).
 */
public final class PlanetHubs {
	public static final Identifier GREENWATCH = OldRepublicMod.id("hub/greenwatch");
	public static final Identifier PROVING = OldRepublicMod.id("hub/proving");
	public static final Identifier SEPULCHRE = OldRepublicMod.id("hub/sepulchre");
	/** Template layers of a hub below its stand (its foundation and the pad's base). */
	public static final int BURIED = 3;
	/** Where the Sepulchre's template origin sits in the Proving's local frame: right under its carved stair. */
	public static final BlockPos SEPULCHRE_OFFSET = new BlockPos(15, -16, 11);
	/** Horizontal distance from the hub anchor inside which {@link #allowLoad} looks for the hub's and the Sepulchre's boxes. */
	private static final int SPAWN_GUARD = 64;
	/** Columns of the Proving's carved stair, local: the shaft joining a raised Proving to its Sepulchre runs here. */
	private static final BoundingBox STAIR_LOCAL = new BoundingBox(29, 0, 12, 33, 2, 18);
	/** Spacing of the fallback platform's stilts. */
	private static final int STILT_SPACING = 8;
	/** The deepest a stilt reaches looking for ground. */
	private static final int STILT_DEPTH = 96;
	/** Columns sampled per side when judging a hub's ground (every other column). */
	private static final int SAMPLE_STEP = 2;
	/** Ticks between ticket refreshes (the {@code PORTAL} ticket lives about 300 ticks). */
	private static final int TICKET_REFRESH = 200;
	/** How far around a template's box {@link #placeClearing} clears trees and bumps. */
	static final int CLEARING_MARGIN = 2;

	private static final ChunkPos HUB_CHUNK = new ChunkPos(0, 0);

	private record Job(CompletableFuture<BlockPos> future, int radius) {
	}

	private static final Map<Planets.Planet, Job> JOBS = new EnumMap<>(Planets.Planet.class);

	/** The hub anchor (the pad centre) if the hub is placed; null before that, or for the home world. */
	public static @Nullable BlockPos hub(MinecraftServer server, Planets.Planet planet) {
		PlanetSiteData.Hub hub = planet == Planets.Planet.HOME ? null : PlanetSiteData.hub(server, planet);
		return hub != null ? hub.centre() : null;
	}

	/**
	 * Places the hub if it is not placed yet (3.5) and completes with its anchor, on the server thread.
	 * Never fails: when no ring fits, the hub stands on a raised platform at (0, 0). The home world and
	 * a missing planet complete at once with {@link BlockPos#ZERO} (they have no hub).
	 */
	public static CompletableFuture<BlockPos> ensure(MinecraftServer server, Planets.Planet planet) {
		BlockPos hub = hub(server, planet);

		if (hub != null) {
			return CompletableFuture.completedFuture(hub);
		}

		ServerLevel level = Planets.level(server, planet);

		if (level == null || planet == Planets.Planet.HOME) {
			return CompletableFuture.completedFuture(BlockPos.ZERO);
		}

		Job job = JOBS.get(planet);

		if (job == null) {
			int radius = Math.max(1, PlanetBalance.get().hubTicketRadius);
			job = new Job(new CompletableFuture<>(), radius);
			JOBS.put(planet, job);
			level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, HUB_CHUNK, radius);
		}

		return job.future();
	}

	/** The pad bays a ship may land on, in order (4.5). Empty when the hub is not placed. */
	public static List<BlockPos> padBays(MinecraftServer server, Planets.Planet planet) {
		PlanetSiteData.Hub hub = planet == Planets.Planet.HOME ? null : PlanetSiteData.hub(server, planet);
		return hub != null ? hub.padBays() : List.of();
	}

	/** Where a player who died on this planet without a bed there respawns (12.2), or null. */
	public static @Nullable BlockPos medbay(MinecraftServer server, Planets.Planet planet) {
		PlanetSiteData.Hub hub = planet == Planets.Planet.HOME ? null : PlanetSiteData.hub(server, planet);
		return hub != null ? hub.medbay() : null;
	}

	/** The hub template of a planet. */
	public static Identifier template(Planets.Planet planet) {
		return planet == Planets.Planet.VARKHAST ? PROVING : GREENWATCH;
	}

	/**
	 * The hub of {@code planet} in {@code data}, placing it with {@code placer} only when there is
	 * none yet (a test seam: one placement per server, whatever calls ensure).
	 */
	public static PlanetSiteData.Hub ensureIn(PlanetSiteData.Data data, Planets.Planet planet, Supplier<PlanetSiteData.Hub> placer) {
		PlanetSiteData.Hub hub = data.hub(planet);

		if (hub == null) {
			hub = placer.get();
			data.putHub(planet, hub);
		}

		return hub;
	}

	/** Drives the pending placements: refreshes their tickets and places each hub once its chunks are loaded. */
	static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}

		long now = Clocks.time(server);

		for (Iterator<Map.Entry<Planets.Planet, Job>> it = JOBS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Planets.Planet, Job> entry = it.next();
			Planets.Planet planet = entry.getKey();
			Job job = entry.getValue();
			ServerLevel level = Planets.level(server, planet);

			if (level == null) {
				job.future().complete(BlockPos.ZERO);
				it.remove();
				continue;
			}

			if (now % TICKET_REFRESH == 0) {
				level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, HUB_CHUNK, job.radius());
			}

			if (!loaded(level, job.radius())) {
				continue;
			}

			PlanetSiteData.Data data = PlanetSiteData.snapshot(server);
			PlanetSiteData.Hub hub = ensureIn(data, planet, () -> place(level, planet, data));
			PlanetSiteData.commit(server, data);
			level.getChunkSource().removeTicketWithRadius(TicketType.PORTAL, HUB_CHUNK, job.radius());
			it.remove();
			job.future().complete(hub.centre());
		}
	}

	/** Forgets pending placements (server stop); their futures are left to the stopped server. */
	static void clear() {
		JOBS.clear();
	}

	/**
	 * Places {@code planet}'s hub around (0, 0) of {@code level}, whose chunks must be loaded, and
	 * returns where it is. Records the template (and for the Proving, the Sepulchre) in
	 * {@link WorldSites}, and the Sepulchre's shared position in {@code data}.
	 */
	public static PlanetSiteData.Hub place(ServerLevel level, Planets.Planet planet, PlanetSiteData.Data data) {
		PlanetBalance b = PlanetBalance.get();
		Identifier template = template(planet);
		Vec3 origin = new Vec3(0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0.5);
		MeetingSites.Rules rules = new MeetingSites.Rules(b.hubFootprint, b.hubFootprint, b.hubMaxSlope, pos -> false, List.of(), 0);
		RuntimeSites.Placement placement = RuntimeSites.placeSurface(level, template, origin, new Vec3(0, 0, 1), b.hubRings, new int[0], rules, BURIED, null);
		boolean raised = false;

		if (placement == null) {
			BlockPos stand = treeTolerantStand(level, b.hubRings, b.hubFootprint, b.hubMaxSlope);

			if (stand != null) {
				placement = placeClearing(level, template, stand, Rotation.NONE, BURIED, null);
			}
		}

		if (placement == null) {
			OldRepublicMod.LOGGER.warn("No ring around (0, 0) of {} fits the hub {}; it stands on a raised platform", level.dimension().identifier(), template);
			placement = placeRaised(level, template, b.hubFootprint);
			raised = true;
		}

		SitePiece piece = placement.piece();
		BlockPos centre = anchorOr(piece, SiteAnchors.Anchor.HEART, placement.origin());
		List<BlockPos> bays = new ArrayList<>();

		for (SiteAnchors.Anchor bay : new SiteAnchors.Anchor[] {SiteAnchors.Anchor.PAD_BAY_1, SiteAnchors.Anchor.PAD_BAY_2, SiteAnchors.Anchor.PAD_BAY_3,
				SiteAnchors.Anchor.PAD_BAY_4}) {
			BlockPos at = SiteAnchors.find(piece, bay);

			if (at != null) {
				bays.add(at);
			}
		}

		BlockPos medbay = anchorOr(piece, SiteAnchors.Anchor.MEDBAY, centre);
		clearCreatures(level, piece.box());

		if (planet == Planets.Planet.VARKHAST) {
			placeSepulchre(level, piece, data);
		}

		OldRepublicMod.LOGGER.info("Placed {} at {} in {}{}", template, centre, level.dimension().identifier(), raised ? " (raised platform)" : "");
		return new PlanetSiteData.Hub(centre, bays, medbay, raised);
	}

	/**
	 * The {@code ALLOW_LOAD} rule (3.4): a mob spawned naturally or by chunk generation is refused in
	 * the box of a planet's hub or of the Sepulchre. Loads from disk and every other spawn reason
	 * (spawn eggs, commands, the hub's own spawners) pass.
	 */
	static boolean allowLoad(Entity entity, ServerLevel level, @Nullable EntitySpawnReason reason, boolean fromDisk) {
		if (fromDisk || !(entity instanceof Mob) || reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return true;
		}

		Planets.Planet planet = Planets.Planet.of(level.dimension());
		BlockPos hub = planet == null ? null : hub(level.getServer(), planet);
		BlockPos pos = entity.blockPosition();

		if (hub == null || Math.abs(pos.getX() - hub.getX()) > SPAWN_GUARD || Math.abs(pos.getZ() - hub.getZ()) > SPAWN_GUARD) {
			return true;
		}

		return StructureLocator.runtimePieceAt(level, pos, SiteKind.PLANET_HUB) == null && StructureLocator.runtimePieceAt(level, pos, SiteKind.SEPULCHRE) == null;
	}

	/** Removes the wild mobs inside a freshly placed hub's box: worldgen's animals and anything that spawned before it. */
	private static void clearCreatures(ServerLevel level, BoundingBox box) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, AABB.of(box), mob -> !mob.isPersistenceRequired() && !mob.hasCustomName())) {
			mob.discard();
		}
	}

	/**
	 * Searches {@code rings} around (0, 0) for a stand whose {@code footprint} square is natural
	 * ground with trees allowed: each column's ground is found below logs, leaves and plants, and
	 * the spread of those heights must stay within {@code maxSlope}. The flattest candidate wins.
	 * Null when none passes or a column is not loaded.
	 */
	public static @Nullable BlockPos treeTolerantStand(ServerLevel level, int[] rings, int footprint, int maxSlope) {
		BlockPos best = null;
		int bestSpread = Integer.MAX_VALUE;

		for (int ring : rings) {
			int angles = ring == 0 ? 1 : 16;

			for (int i = 0; i < angles; i++) {
				double angle = i * Math.PI * 2 / angles;
				int cx = (int) Math.floor(Math.cos(angle) * ring);
				int cz = (int) Math.floor(Math.sin(angle) * ring);
				int[] spread = groundSpread(level, cx, cz, footprint);

				if (spread != null && spread[1] - spread[0] <= maxSlope && spread[1] - spread[0] < bestSpread) {
					bestSpread = spread[1] - spread[0];
					best = new BlockPos(cx, spread[2], cz);
				}
			}
		}

		return best;
	}

	/** {min, max, mean} of the natural ground stands of a square, or null when a column is not natural ground, is wet or is not loaded. */
	private static int @Nullable [] groundSpread(ServerLevel level, int cx, int cz, int footprint) {
		int half = footprint / 2;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		long sum = 0;
		int columns = 0;

		for (int dx = -half; dx <= half; dx += SAMPLE_STEP) {
			for (int dz = -half; dz <= half; dz += SAMPLE_STEP) {
				int x = cx + dx;
				int z = cz + dz;

				if (!level.hasChunkAt(x, z)) {
					return null;
				}

				int stand = groundStand(level, x, z);

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

	/** The feet height on a column's natural ground below trees and plants, or {@link Integer#MIN_VALUE} for water or anything built. */
	static int groundStand(ServerLevel level, int x, int z) {
		BlockPos below = belowGrowth(level, x, z);

		if (below == null) {
			return Integer.MIN_VALUE;
		}

		BlockState state = level.getBlockState(below);
		return state.getFluidState().isEmpty() && !state.is(MeetingSites.BUILT_BLOCKS) && naturalGround(state) ? below.getY() + 1 : Integer.MIN_VALUE;
	}

	/**
	 * The first block of a column, from its top down, that is not a tree or plant a site may clear: a
	 * fluid, a built block or the ground. Null when there is none within 40 blocks.
	 */
	static @Nullable BlockPos belowGrowth(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);

		for (int i = 0; i < 40 && pos.getY() > level.getMinY(); i++, pos.move(Direction.DOWN)) {
			BlockState state = level.getBlockState(pos);

			if (!state.getFluidState().isEmpty() || state.is(MeetingSites.BUILT_BLOCKS) || !removable(state)) {
				return pos.immutable();
			}
		}

		return null;
	}

	/** Trees and plants a hub may clear. */
	private static boolean removable(BlockState state) {
		return state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || (state.canBeReplaced() && state.getFluidState().isEmpty())
				|| state.is(Blocks.MOSS_CARPET) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK);
	}

	/** The planets' natural ground (wider than {@code #story_ground}: it also takes moss, tuff, basalt and clay). */
	static boolean naturalGround(BlockState state) {
		return state.is(MeetingSites.STORY_GROUND) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
				|| state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SMOOTH_BASALT)
				|| state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_SANDSTONE) || state.is(Blocks.RED_SANDSTONE);
	}

	/**
	 * Clears trees and natural ground standing in the template's box (widened by {@link #CLEARING_MARGIN})
	 * above its stand, then places it there turned by {@code rotation} and records it
	 * ({@link RuntimeSites#placeAt}). The hub's second search and the planet sites' clearing search
	 * ({@link PlanetSiteSearch}); the caller has checked that the widened box is loaded.
	 */
	static RuntimeSites.@Nullable Placement placeClearing(ServerLevel level, Identifier template, BlockPos stand, Rotation rotation, int buried,
			@Nullable UUID owner) {
		StructureTemplate loaded = level.getStructureManager().get(template).orElse(null);

		if (loaded == null) {
			return null;
		}

		BlockPos templateOrigin = RuntimeSites.templateOrigin(loaded, stand, rotation, buried);
		BoundingBox box = loaded.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), templateOrigin);
		clearAbove(level, box.inflatedBy(CLEARING_MARGIN), stand.getY(), box.maxY() + 12);

		try {
			return RuntimeSites.placeAt(level, template, stand, rotation, buried, owner);
		} catch (IllegalArgumentException | IllegalStateException e) {
			OldRepublicMod.LOGGER.warn("Could not place {} at {}: {}", template, stand, e.getMessage());
			return null;
		}
	}

	/** Removes trees, plants and natural ground from {@code fromY} to {@code toY} in every column of {@code box}. */
	private static void clearAbove(ServerLevel level, BoundingBox box, int fromY, int toY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = fromY; y <= toY; y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));

					if (!state.isAir() && !state.is(MeetingSites.BUILT_BLOCKS) && (removable(state) || naturalGround(state)) && state.getFluidState().isEmpty()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/**
	 * The fallback (3.5): the hub at (0, 0) with its stand at the highest ground under its
	 * footprint, trees and ground above it cleared, on stilts down to the ground with a ladder.
	 */
	private static RuntimeSites.Placement placeRaised(ServerLevel level, Identifier template, int footprint) {
		int half = footprint / 2;
		int top = level.getMinY() + 1;

		for (int dx = -half; dx <= half; dx += SAMPLE_STEP * 2) {
			for (int dz = -half; dz <= half; dz += SAMPLE_STEP * 2) {
				top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz));
			}
		}

		BlockPos stand = new BlockPos(0, Math.max(top, level.getSeaLevel() + 1), 0);
		StructureTemplate loaded = level.getStructureManager().get(template).orElseThrow(() -> new IllegalStateException("Missing hub template " + template));
		BlockPos templateOrigin = RuntimeSites.templateOrigin(loaded, stand, Rotation.NONE, BURIED);
		BoundingBox box = loaded.getBoundingBox(new StructurePlaceSettings(), templateOrigin);
		clearAbove(level, box.inflatedBy(2), box.minY(), box.maxY() + 12);
		SitePiece piece = RuntimeSites.stamp(level, SiteKind.PLANET_HUB, template, templateOrigin, Rotation.NONE);

		if (piece == null) {
			throw new IllegalStateException("Hub template " + template + " could not be placed at " + stand);
		}

		stilts(level, box, stiltBlock(level), STILT_SPACING);
		long day = Clocks.day(level.getServer());
		WorldSites.add(level.getServer(), new WorldSites.SiteRecord(SiteKind.PLANET_HUB, level.dimension(), piece.origin(), piece.rotation(), template, null, day));
		return new RuntimeSites.Placement(piece, stand, Rotation.NONE);
	}

	private static BlockState stiltBlock(ServerLevel level) {
		return Planets.Planet.of(level.dimension()) == Planets.Planet.VARKHAST ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
				: Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
	}

	/**
	 * Stilts under {@code box}: a column every {@code spacing} blocks along each axis (and at the
	 * corners) from below the box down to the first ground that is not air, plants or fluid, and a
	 * ladder up the north face of the stilt nearest the middle of the north edge. Public for the
	 * fallback test, which builds them under a small box.
	 */
	public static void stilts(ServerLevel level, BoundingBox box, BlockState pillar, int spacing) {
		List<Integer> xs = steps(box.minX(), box.maxX(), spacing);
		List<Integer> zs = steps(box.minZ(), box.maxZ(), spacing);
		int ladderX = xs.get(xs.size() / 2);

		for (int x : xs) {
			for (int z : zs) {
				int bottom = column(level, x, box.minY() - 1, z, pillar);

				if (x == ladderX && z == box.minZ()) {
					BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);

					for (int y = bottom; y < box.minY(); y++) {
						BlockPos at = new BlockPos(x, y, z - 1);

						if (level.getBlockState(at).canBeReplaced()) {
							level.setBlock(at, ladder, Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
	}

	/** Fills a column of {@code pillar} from {@code fromY} down to the ground; returns the lowest filled height. */
	private static int column(ServerLevel level, int x, int fromY, int z, BlockState pillar) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, fromY, z);
		int lowest = fromY + 1;

		for (int i = 0; i < STILT_DEPTH && pos.getY() > level.getMinY(); i++, pos.move(Direction.DOWN)) {
			BlockState state = level.getBlockState(pos);

			if (!state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES)) {
				break;
			}

			level.setBlock(pos, pillar, Block.UPDATE_CLIENTS);
			lowest = pos.getY();
		}

		return lowest;
	}

	private static List<Integer> steps(int min, int max, int spacing) {
		List<Integer> out = new ArrayList<>();

		for (int v = min; v < max; v += spacing) {
			out.add(v);
		}

		out.add(max);
		return out;
	}

	/**
	 * Places Zarvos's Sepulchre under the Proving piece: at {@link #SEPULCHRE_OFFSET} in the
	 * Proving's frame, lowered under the ground when the Proving is raised, with a shaft joining its
	 * entrance to the Proving's stair. Records it and its shared position.
	 */
	private static void placeSepulchre(ServerLevel level, SitePiece proving, PlanetSiteData.Data data) {
		StructureTemplate loaded = level.getStructureManager().get(SEPULCHRE).orElse(null);

		if (loaded == null) {
			OldRepublicMod.LOGGER.error("Missing template {}; the Proving has no Sepulchre", SEPULCHRE);
			return;
		}

		Rotation rotation = proving.rotation();
		BlockPos stairBottom = SiteAnchors.toWorld(proving, new BlockPos(STAIR_LOCAL.minX(), STAIR_LOCAL.minY(), STAIR_LOCAL.minZ()));
		BlockPos origin = SiteAnchors.toWorld(proving, SEPULCHRE_OFFSET);
		int gap = gapBelow(level, stairBottom.below());
		int sink = gap == 0 ? 0 : gap + 3;
		BlockPos sunk = origin.below(sink);
		SitePiece piece = RuntimeSites.stamp(level, SiteKind.SEPULCHRE, SEPULCHRE, sunk, rotation);

		if (piece == null) {
			OldRepublicMod.LOGGER.error("Could not place {} at {}", SEPULCHRE, sunk);
			return;
		}

		if (sink > 0) {
			shaft(level, proving, sink);
		}

		long day = Clocks.day(level.getServer());
		WorldSites.add(level.getServer(), new WorldSites.SiteRecord(SiteKind.SEPULCHRE, level.dimension(), piece.origin(), rotation, SEPULCHRE, null, day));
		BlockPos heart = anchorOr(piece, SiteAnchors.Anchor.HEART, piece.box().getCenter());
		data.putShared(PlanetSites.Site.SEPULCHRE, GlobalPos.of(level.dimension(), heart));
	}

	/** How many cells of air, plants or fluid lie below {@code from} before the ground (0 when {@code from} is ground). */
	private static int gapBelow(ServerLevel level, BlockPos from) {
		BlockPos.MutableBlockPos pos = from.mutable();
		int gap = 0;

		while (gap < STILT_DEPTH && pos.getY() > level.getMinY()) {
			BlockState state = level.getBlockState(pos);

			if (!state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)) {
				break;
			}

			gap++;
			pos.move(Direction.DOWN);
		}

		return gap;
	}

	/** Extends the Proving's stair down {@code depth} blocks as a walled ladder shaft to a lowered Sepulchre. */
	private static void shaft(ServerLevel level, SitePiece proving, int depth) {
		BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

		for (int lx = STAIR_LOCAL.minX() - 1; lx <= STAIR_LOCAL.maxX() + 1; lx++) {
			for (int lz = STAIR_LOCAL.minZ() - 1; lz <= STAIR_LOCAL.maxZ() + 1; lz++) {
				boolean edge = lx < STAIR_LOCAL.minX() || lx > STAIR_LOCAL.maxX() || lz < STAIR_LOCAL.minZ() || lz > STAIR_LOCAL.maxZ();

				for (int ly = -1; ly >= -depth; ly--) {
					BlockPos at = proving.origin().offset(StructureTemplate.transform(new BlockPos(lx, ly, lz), Mirror.NONE, proving.rotation(), BlockPos.ZERO));
					level.setBlock(at, edge ? wall : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}

		Direction north = proving.rotation().rotate(Direction.NORTH);
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, north.getOpposite());

		for (int ly = -1; ly >= -depth; ly--) {
			BlockPos at = proving.origin().offset(StructureTemplate.transform(new BlockPos(STAIR_LOCAL.minX() + 2, ly, STAIR_LOCAL.minZ()), Mirror.NONE,
					proving.rotation(), BlockPos.ZERO));
			level.setBlock(at, ladder, Block.UPDATE_CLIENTS);
		}
	}

	private static BlockPos anchorOr(SitePiece piece, SiteAnchors.Anchor anchor, BlockPos fallback) {
		BlockPos at = SiteAnchors.find(piece, anchor);

		if (at == null) {
			OldRepublicMod.LOGGER.warn("Template {} has no {} anchor", piece.template(), anchor);
			return fallback.immutable();
		}

		return at;
	}

	private static boolean loaded(ServerLevel level, int radius) {
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				if (!level.hasChunk(x, z)) {
					return false;
				}
			}
		}

		return true;
	}

	private PlanetHubs() {
	}
}
