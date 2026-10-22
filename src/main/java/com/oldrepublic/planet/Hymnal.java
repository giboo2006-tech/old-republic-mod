package com.oldrepublic.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.hymn.FoeSpawns;
import com.oldrepublic.enemy.hymn.HymnEntities;
import com.oldrepublic.enemy.hymn.Hymnsworn;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SiteMeta;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SpawnerGroups;
import com.oldrepublic.world.site.WorldSites;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * The Hymnal on Tyrvann (spec MILESTONE_5.md 3.3.3, 6.7): one per server, in two pieces recorded as
 * {@link SiteKind#HYMNAL}. {@link PlanetSites} places the arch ({@code site/hymnal_arch}) with its
 * ring and clearing searches; {@link #sinkHalls} then stamps the halls ({@code site/hymnal}) under it
 * at {@link #HALLS_OFFSET} in the arch's frame, turned the same way, where the processional's first
 * stair meets the arch's last flight {@value #TEMPLATE_DEPTH} blocks below the arch's stand
 * ({@code planets.hymnalDepth}).
 *
 * <p>Milestone 6 (docs/MILESTONE_6.md 3.2, 3.4): the arch is placed by far placement, and so are the
 * halls ({@link #requestHalls}: a fixed stamp at the arch's site meta link to them, else
 * {@link #HALLS_OFFSET}, sunk by the generator's own heights so nothing is loaded before the ticket
 * does it); {@link #sinkHalls} stays the fallback. The keystone is the arch's {@code ARCH_TOP} anchor,
 * else {@link #ARCH_TOP}.
 *
 * <p>The halls run 79 blocks away from the arch. Where the ridge falls away above them, they sink
 * further, so their top stays {@value #COVER} blocks under the lowest ground over them (and at least
 * {@code planets.hymnalDepth} below the stand): a walled ladder shaft then joins the arch's foot to
 * the processional. The halls stay {@value #BOTTOM_MARGIN} blocks above the world's bottom and sink
 * at most {@value #MAX_SHAFT} further.
 *
 * <p>Cosmetic lightning ({@code setVisualOnly}) strikes the arch's keystone every
 * {@code planets.hymnalArchInterval} ticks while a player in Tyrvann is within
 * {@value #ARCH_AUDIENCE} blocks of it.
 *
 * <p>The processional's two storm-caller posts are group spawners of {@link #STORM_CALLERS}, which
 * this class registers: one of W8's Hymnsworn storm-callers per post.
 */
public final class Hymnal {
	/** The spawner group of the processional's storm-caller posts (one {@code CALLER} Hymnsworn each by default). */
	public static final String STORM_CALLERS = "storm_callers";
	/** The halls' template origin in the arch's frame: right under the arch's box, the first stair under its last flight. */
	public static final BlockPos HALLS_OFFSET = new BlockPos(-11, -28, 9);
	/** The arch's keystone in its own frame, where the cosmetic bolts strike. */
	public static final BlockPos ARCH_TOP = new BlockPos(7, 30, 5);
	/** The halls' top layer lies this far below the arch's stand when no shaft is needed (the arch's 17 buried layers and one). */
	public static final int TEMPLATE_DEPTH = 18;
	/** Natural ground kept over the halls' top layer. */
	public static final int COVER = 3;
	/** The furthest the halls sink below {@link #TEMPLATE_DEPTH}. */
	public static final int MAX_SHAFT = 64;
	/** The halls stay this far above the world's bottom. */
	public static final int BOTTOM_MARGIN = 8;
	/** The cosmetic bolts fall only while a player is this close to the keystone. */
	public static final double ARCH_AUDIENCE = 128;
	/** Columns sampled for the ground over the halls (every fourth). */
	private static final int SAMPLE_STEP = 4;
	/** Chunks around the halls generated before the ground is read: their shell and the shaft. */
	private static final int LOAD_MARGIN = 3;
	/** While the arch is not found, it is looked for again after this many ticks. */
	private static final int LOOKUP_TICKS = 600;
	/** The halls' far placement key: one Hymnal per server. */
	static final String HALLS_KEY = "planet/tyrvann/hymnal/halls";

	/** The keystone's world position once the arch is found, and when the next bolt may fall. */
	private static @Nullable BlockPos archTop;
	private static long nextLookup;
	private static long nextBolt;

	/** Registers {@link #STORM_CALLERS} (from {@code PlanetM5Feature}). */
	static void init() {
		SpawnerGroups.register(STORM_CALLERS, new SpawnerGroups.Group() {
			@Override
			public int size(int count, BlockPos pos) {
				return count > 0 ? count : 1;
			}

			@Override
			public List<LivingEntity> spawn(ServerLevel level, BlockPos at, int size, boolean bound, @Nullable UUID owner) {
				List<LivingEntity> out = new ArrayList<>(size);

				for (int i = 0; i < size; i++) {
					Hymnsworn caller = FoeSpawns.spawn(level, HymnEntities.HYMNSWORN, at, i, size, owner, h -> h.setVariant(Hymnsworn.Variant.CALLER));

					if (caller != null) {
						out.add(caller);
					}
				}

				return out;
			}
		});
	}

	/**
	 * Asks far placement for the halls under a placed arch (docs/MILESTONE_6.md 3.2): a fixed stamp
	 * ({@link FarSites.Request#at}) at the arch's link to them ({@link #hallsOrigin}), turned as the
	 * arch, sunk by {@link #gapFor} from the generator's own heights over them (nothing is loaded
	 * before the request's ticket does it). The first call that finds them stamped digs the ladder
	 * shaft when they sank. Idempotent; on FAILED the caller falls back to {@link #sinkHalls}.
	 */
	public static FarSites.Status requestHalls(ServerLevel level, RuntimeSites.Placement arch) {
		SitePiece archPiece = arch.piece();
		BlockPos origin = hallsOrigin(archPiece);
		FarSites.Status status = FarSites.poll(level, HALLS_KEY);

		if (status.failed() && "unknown".equals(status.detail())) {
			StructureTemplate halls = level.getStructureManager().get(PlanetSites.HYMNAL_HALLS.template()).orElse(null);

			if (halls == null) {
				return new FarSites.Status(FarSites.Phase.FAILED, null, null, "missing_template");
			}

			BoundingBox box = halls.getBoundingBox(new StructurePlaceSettings().setRotation(archPiece.rotation()), origin);
			int gap = gapFor(box.maxY(), box.minY(), lowestNoiseGround(level, box), PlanetBalance.get().hymnalDepth, level.getMinY());
			status = FarSites.request(level, FarSites.Request.at(HALLS_KEY, null, PlanetSites.HYMNAL_HALLS.template(), origin.below(gap), archPiece.rotation()));
		}

		RuntimeSites.Placement placed = status.placement();

		if (status.placed() && placed != null) {
			int gap = origin.getY() - placed.piece().origin().getY();

			if (gap > 0) {
				shaft(level, archPiece, gap);
			}

			OldRepublicMod.LOGGER.info("Placed the Hymnal's halls at {} in {}{}", placed.piece().origin(), level.dimension().identifier(),
					gap > 0 ? ", " + gap + " blocks lower than the arch's stair, joined by a ladder shaft" : "");
			archTop = null;
			nextLookup = 0;
		}

		return status;
	}

	/** Where the halls' template origin goes before they sink: the arch's site meta link to them, else {@link #HALLS_OFFSET}, in the arch's frame. */
	static BlockPos hallsOrigin(SitePiece arch) {
		BlockPos link = SiteMeta.of(arch.template()).link(PlanetSites.HYMNAL_HALLS.template());
		return SiteAnchors.toWorld(arch, link != null ? link : HALLS_OFFSET);
	}

	/**
	 * Stamps the halls under a freshly placed arch and records them, sinking them and digging the
	 * ladder shaft when the ground above them is too thin ({@link #gapFor}). Returns the halls piece,
	 * or null (logged) when the template is missing or cannot be stamped. Loads (generating where
	 * needed) every chunk around the halls first: far placement's fallback ({@link #requestHalls}).
	 */
	public static @Nullable SitePiece sinkHalls(ServerLevel level, RuntimeSites.Placement arch) {
		StructureTemplate halls = level.getStructureManager().get(PlanetSites.HYMNAL_HALLS.template()).orElse(null);

		if (halls == null) {
			OldRepublicMod.LOGGER.error("Missing template {}; the Hymnal has no halls", PlanetSites.HYMNAL_HALLS.template());
			return null;
		}

		SitePiece archPiece = arch.piece();
		Rotation rotation = archPiece.rotation();
		BlockPos origin = hallsOrigin(archPiece);
		BoundingBox box = halls.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), origin);
		// The halls reach well past the chunks the arch's owner has loaded: generate them before reading the ground.
		box.inflatedBy(LOAD_MARGIN).intersectingChunks().forEach(chunk -> level.getChunk(chunk.x(), chunk.z()));
		int gap = gapFor(box.maxY(), box.minY(), lowestGround(level, box), PlanetBalance.get().hymnalDepth, level.getMinY());
		SitePiece piece = RuntimeSites.stamp(level, SiteKind.HYMNAL, PlanetSites.HYMNAL_HALLS.template(), origin.below(gap), rotation);

		if (piece == null) {
			OldRepublicMod.LOGGER.error("Could not stamp {} at {}", PlanetSites.HYMNAL_HALLS.template(), origin.below(gap));
			return null;
		}

		WorldSites.add(level.getServer(), new WorldSites.SiteRecord(SiteKind.HYMNAL, level.dimension(), piece.origin(), rotation,
				PlanetSites.HYMNAL_HALLS.template(), null, Clocks.day(level.getServer())));

		if (gap > 0) {
			shaft(level, archPiece, gap);
		}

		OldRepublicMod.LOGGER.info("Placed the Hymnal's halls at {} in {}{}", piece.origin(), level.dimension().identifier(),
				gap > 0 ? ", " + gap + " blocks lower than the arch's stair, joined by a ladder shaft" : "");
		archTop = null;
		nextLookup = 0;
		return piece;
	}

	/**
	 * How many blocks further down than {@link #TEMPLATE_DEPTH} the halls go (pure, for the tests):
	 * enough to keep {@link #COVER} blocks of ground over their top layer {@code top} where the lowest
	 * ground above them is {@code lowestFree} (the first free block over it), and at least
	 * {@code depth} - {@link #TEMPLATE_DEPTH}; at most {@link #MAX_SHAFT}, and never closer than
	 * {@link #BOTTOM_MARGIN} to the world's bottom {@code minY}.
	 */
	public static int gapFor(int top, int bottom, int lowestFree, int depth, int minY) {
		int wanted = Math.max(Math.max(0, depth - TEMPLATE_DEPTH), top + COVER + 1 - lowestFree);
		int room = bottom - (minY + BOTTOM_MARGIN);
		return Math.max(0, Math.min(wanted, Math.min(MAX_SHAFT, room)));
	}

	/** The lowest first-free height ({@code OCEAN_FLOOR}) over the columns of {@code box}, every fourth column and its edges. */
	private static int lowestGround(ServerLevel level, BoundingBox box) {
		return lowest(box, (x, z) -> level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z));
	}

	/** {@link #lowestGround} as the world generator would make it ({@code OCEAN_FLOOR_WG} noise heights): reads no chunk. */
	private static int lowestNoiseGround(ServerLevel level, BoundingBox box) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		return lowest(box, (x, z) -> generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random));
	}

	/** The lowest {@code height} over the columns of {@code box}, every fourth column and its edges. */
	private static int lowest(BoundingBox box, ColumnHeight height) {
		int lowest = Integer.MAX_VALUE;

		for (int x = box.minX(); ; x = Math.min(x + SAMPLE_STEP, box.maxX())) {
			for (int z = box.minZ(); ; z = Math.min(z + SAMPLE_STEP, box.maxZ())) {
				lowest = Math.min(lowest, height.at(x, z));

				if (z == box.maxZ()) {
					break;
				}
			}

			if (x == box.maxX()) {
				break;
			}
		}

		return lowest;
	}

	/** A column's height. */
	@FunctionalInterface
	private interface ColumnHeight {
		int at(int x, int z);
	}

	/**
	 * The walled shaft from the arch's foot down to halls sunk {@code gap} blocks (arch frame): a ladder
	 * column along the shaft's north wall, a two-high landing at its foot that opens onto the
	 * processional's first flight, black brick around it all, and the flight's old headroom in the arch
	 * filled.
	 */
	private static void shaft(ServerLevel level, SitePiece arch, int gap) {
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, arch.rotation().rotate(Direction.SOUTH));

		for (int ly = -gap; ly <= -1; ly++) {
			boolean low = ly <= -gap + 1;

			for (int lx = 4; lx <= 10; lx++) {
				for (int lz = 9; lz <= 15; lz++) {
					boolean column = lx >= 6 && lx <= 8 && lz == 10;
					boolean landing = low && lx >= 6 && lx <= 8 && (lz == 11 || lz == 12);
					boolean head = lx >= 5 && lx <= 9 && (low && lz == 13 || ly == -gap && lz == 14);
					set(level, arch, lx, ly, lz, column ? ladder : landing || head ? air : masonry(lx, ly, lz));
				}
			}
		}

		for (int lx = 5; lx <= 9; lx++) {
			set(level, arch, lx, 1, 13, masonry(lx, 1, 13));
			set(level, arch, lx, 0, 14, masonry(lx, 0, 14));

			if (gap > 1) {
				set(level, arch, lx, 0, 13, masonry(lx, 0, 13));
			}
		}
	}

	private static void set(ServerLevel level, SitePiece arch, int lx, int ly, int lz, BlockState state) {
		level.setBlock(SiteAnchors.toWorld(arch, new BlockPos(lx, ly, lz)), state, Block.UPDATE_CLIENTS);
	}

	/** The halls' black brick, cracked here and there by position. */
	private static BlockState masonry(int x, int y, int z) {
		return Math.floorMod(x * 7 + y * 13 + z * 5, 5) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
				: Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	}

	/** One server tick: a cosmetic bolt on the arch when it is due and someone is near to see it. */
	static void tick(MinecraftServer server) {
		ServerLevel level = Planets.level(server, Planets.Planet.TYRVANN);

		if (level == null) {
			return;
		}

		long now = level.getGameTime();

		if (now < nextBolt) {
			return;
		}

		BlockPos top = archTop(server, level, now);

		if (top == null) {
			return;
		}

		nextBolt = now + between(level.getRandom(), PlanetBalance.get().hymnalArchInterval);

		if (level.hasChunkAt(top.getX(), top.getZ()) && audience(level, top)) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);

			if (bolt != null) {
				bolt.snapTo(Vec3.atBottomCenterOf(top.above()));
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
		}
	}

	/** Forgets the arch and the bolt clock (server stop). */
	static void clear() {
		archTop = null;
		nextLookup = 0;
		nextBolt = 0;
	}

	/** The keystone of the recorded arch in {@code level}, looked up at most every {@link #LOOKUP_TICKS} while not found. */
	private static @Nullable BlockPos archTop(MinecraftServer server, ServerLevel level, long now) {
		if (archTop != null || now < nextLookup) {
			return archTop;
		}

		nextLookup = now + LOOKUP_TICKS;

		for (WorldSites.SiteRecord record : WorldSites.all(server)) {
			if (record.dimension().equals(level.dimension()) && record.template().equals(PlanetSites.HYMNAL_ARCH.template())) {
				SitePiece piece = RuntimeSites.pieceOf(level, record);

				if (piece != null) {
					BlockPos anchor = SiteAnchors.find(piece, SiteAnchors.Anchor.ARCH_TOP);
					archTop = anchor != null ? anchor : SiteAnchors.toWorld(piece, ARCH_TOP);
				}
			}
		}

		return archTop;
	}

	private static boolean audience(ServerLevel level, BlockPos top) {
		Vec3 centre = Vec3.atCenterOf(top);

		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.position().distanceToSqr(centre) <= ARCH_AUDIENCE * ARCH_AUDIENCE) {
				return true;
			}
		}

		return false;
	}

	/** A tick count in the inclusive range {@code range} ({@code [min, max]}; one value or none falls back sensibly). */
	static int between(RandomSource random, int[] range) {
		if (range.length == 0) {
			return 600;
		}

		int min = Math.max(1, range[0]);
		int max = Math.max(min, range[range.length - 1]);
		return min + random.nextInt(max - min + 1);
	}

	private Hymnal() {
	}
}
