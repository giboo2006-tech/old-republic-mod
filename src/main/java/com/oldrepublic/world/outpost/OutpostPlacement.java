package com.oldrepublic.world.outpost;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.quest.story.StoryService;
import com.oldrepublic.world.site.PlacementRules;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SiteBlocks;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SiteMeta;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SiteStates;
import com.oldrepublic.world.site.WorldSites;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Where and how a runtime outpost goes (spec 3.5; docs/MILESTONE_6.md 3.2, 3.4): far placement
 * {@code sites.outpostRange} blocks from the player on high ground, at least
 * {@code sites.outpostSeparation} from every other runtime outpost and outside spawn protection;
 * when far placement fails, the near search on the rings {@code sites.outpostRings} from the player
 * (fallback {@code sites.outpostFallback}) with the template's site meta rules (its larger side of
 * natural ground, its clearance all round, its {@code max_slope}). With {@code story.placeStructures}
 * off only the bounty terminal is placed by the near search (its block entity spawns the officer and
 * garrison), and the site is still recorded so it is reused.
 */
public final class OutpostPlacement {
	/** The outpost templates' side (both are square): the fallback when a template cannot be read, and the record centre's offset. */
	public static final int FOOTPRINT = 45;
	/** Side of the square that must hold no built block and no worldgen piece, for a template without site meta. */
	public static final int CLEARANCE = 61;
	/** Allowed height difference across the footprint, for a template without site meta. */
	public static final int MAX_SLOPE = 5;
	/** The template's lowest layer (its foundation) replaces the ground, for a template without site meta. */
	public static final int BURIED = 1;
	/** The terminal's screen side in the template frame: it faces the entrance (local north). */
	public static final Direction TERMINAL_FACING = Direction.NORTH;
	private static final long TICKS_PER_DAY = 24000L;

	/** The outpost template of an army. */
	public static Identifier template(SiteStates.Army army) {
		return OldRepublicMod.id(army == SiteStates.Army.REPUBLIC ? "outpost/republic_post" : "outpost/imperial_post");
	}

	/** The site kind of an army's outpost. */
	public static SiteKind kind(SiteStates.Army army) {
		return army == SiteStates.Army.REPUBLIC ? SiteKind.REPUBLIC_OUTPOST : SiteKind.IMPERIAL_OUTPOST;
	}

	/**
	 * The near search's rules for a new outpost of {@code army} in {@code level}: the template's site
	 * meta rules ({@link RuntimeSites#rules}; the constants above without one), and every runtime
	 * outpost of either army avoided.
	 */
	public static MeetingSites.Rules rules(ServerLevel level, SiteStates.Army army, SiteBalance b) {
		Identifier template = template(army);
		int footprint = RuntimeSites.footprint(level, template, FOOTPRINT);
		List<BlockPos> avoid = outposts(level);
		SiteMeta meta = SiteMeta.find(template);
		return meta != null ? RuntimeSites.rules(meta, footprint, PlacementRules.spawnProtection(level, footprint), avoid, b.outpostSeparation)
				: new MeetingSites.Rules(footprint, CLEARANCE, MAX_SLOPE, PlacementRules.spawnProtection(level, footprint), avoid, b.outpostSeparation);
	}

	/** The footprint centres of every runtime outpost of either army in {@code level}. */
	private static List<BlockPos> outposts(ServerLevel level) {
		List<BlockPos> centres = new ArrayList<>();

		for (WorldSites.SiteRecord record : WorldSites.all(level.getServer())) {
			if ((record.kind() == SiteKind.REPUBLIC_OUTPOST || record.kind() == SiteKind.IMPERIAL_OUTPOST) && record.dimension().equals(level.dimension())) {
				SitePiece piece = RuntimeSites.pieceOf(level, record);
				centres.add(piece != null ? piece.box().getCenter() : record.origin());
			}
		}

		return centres;
	}

	/**
	 * Places a runtime outpost of {@code army} for the player and records it; returns its terminal,
	 * or null when it is not placed (yet). Through far placement first (docs/MILESTONE_6.md 3.2):
	 * {@code sites.outpostRange} blocks from the player, on the ground its site meta prefers (high),
	 * {@code sites.outpostSeparation} from every other runtime outpost. Far placement goes on by
	 * itself once asked, so the outpost appears even if nobody asks again; a later call returns its
	 * terminal. When far placement has failed (seen by a later call), or with
	 * {@code story.placeStructures} off, the near search runs at once.
	 */
	public static @Nullable BlockPos place(ServerPlayer p, SiteStates.Army army, SiteBalance b) {
		ServerLevel level = p.level();
		Identifier template = template(army);
		Vec3 origin = p.position();
		Vec3 forward = p.getLookAngle();

		if (StoryService.balance().placeStructures) {
			String key = farKey(level, army);
			int min = b.outpostRange.length > 0 ? b.outpostRange[0] : 0;
			int max = Math.max(min, b.outpostRange.length > 1 ? b.outpostRange[1] : min);
			FarSites.Status far = FarSites.request(level, FarSites.Request.of(key, null, template, p.blockPosition(), min, max).kind(kind(army))
					.avoiding(outposts(level), b.outpostSeparation));
			RuntimeSites.Placement placed = far.placement();

			if (far.placed() && placed != null) {
				return SiteAnchors.find(placed.piece(), SiteAnchors.Anchor.TERMINAL);
			}

			if (!far.failed()) {
				return null;
			}

			// Forgotten, so the next outpost of this army tries far placement again.
			FarSites.cancel(level, key);
			OldRepublicMod.LOGGER.info("Far placement found no ground for a {} outpost near {} ({}); trying the near search", army.getSerializedName(),
					p.getName().getString(), far.detail());
			RuntimeSites.Placement placement = RuntimeSites.placeSurface(level, template, origin, forward, b.outpostRings, b.outpostFallback, rules(level, army, b),
					RuntimeSites.buriedOf(template), null);
			return placement == null ? null : SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.TERMINAL);
		}

		RuntimeSites.Spot spot = RuntimeSites.findSurface(level, template, origin, forward, b.outpostRings, b.outpostFallback, rules(level, army, b),
				RuntimeSites.buriedOf(template));
		return spot == null ? null : placeTerminalOnly(level, army, template, spot);
	}

	/**
	 * The far placement key of {@code army}'s next runtime outpost, numbered by the outposts it has:
	 * one request at a time per army, whoever asks, and a new key once it is placed.
	 */
	static String farKey(ServerLevel level, SiteStates.Army army) {
		return "outpost/" + army.getSerializedName() + "/" + WorldSites.snapshot(level.getServer()).count(kind(army));
	}

	/**
	 * The {@code story.placeStructures=false} path: only the bounty terminal, on the ground at the
	 * template's {@code TERMINAL} column, and the site's record (so later calls reuse it).
	 */
	private static BlockPos placeTerminalOnly(ServerLevel level, SiteStates.Army army, Identifier template, RuntimeSites.Spot spot) {
		SitePiece piece = new SitePiece(kind(army), template, spot.box(), spot.rotation(), spot.templateOrigin(),
				SitePiece.runtimeKey(level.dimension(), spot.templateOrigin()));
		BlockPos anchor = SiteAnchors.find(piece, SiteAnchors.Anchor.TERMINAL);
		BlockPos column = anchor != null ? anchor : spot.stand();
		BlockPos pos = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
		BlockState terminal = SiteBlocks.BOUNTY_TERMINAL.defaultBlockState()
				.setValue(SiteStates.FACTION, army)
				.setValue(SiteStates.FACING, spot.rotation().rotate(TERMINAL_FACING));
		level.setBlock(pos, terminal, Block.UPDATE_ALL);
		long day = level.getServer().overworld().getGameTime() / TICKS_PER_DAY;
		WorldSites.add(level.getServer(), new WorldSites.SiteRecord(kind(army), level.dimension(), spot.templateOrigin(), spot.rotation(), template, null, day));
		return pos;
	}

	private OutpostPlacement() {
	}
}
