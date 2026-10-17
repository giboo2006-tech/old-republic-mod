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
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SiteStates;
import com.oldrepublic.world.site.WorldSites;

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
 * Where and how a runtime outpost goes (spec 3.5): the rings {@code sites.outpostRings} from the
 * player (fallback {@code sites.outpostFallback}), a 23-block footprint of natural ground with a
 * 27-block clearance and a slope of at most 3, at least {@code sites.outpostSeparation} from every
 * other runtime outpost and outside spawn protection. With {@code story.placeStructures} off only
 * the bounty terminal is placed (its block entity spawns the officer and garrison), and the site is
 * still recorded so it is reused.
 */
public final class OutpostPlacement {
	/** Side of the square of natural ground the outpost needs. */
	public static final int FOOTPRINT = 23;
	/** Side of the square that must hold no built block and no worldgen piece. */
	public static final int CLEARANCE = 27;
	/** Allowed height difference across the footprint. */
	public static final int MAX_SLOPE = 3;
	/** The template's lowest layer (its foundation) replaces the ground. */
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

	/** The placement rules for a new outpost in {@code level}: every runtime outpost of either army is avoided. */
	public static MeetingSites.Rules rules(ServerLevel level, SiteBalance b) {
		List<BlockPos> avoid = new ArrayList<>();

		for (WorldSites.SiteRecord record : WorldSites.all(level.getServer())) {
			if ((record.kind() == SiteKind.REPUBLIC_OUTPOST || record.kind() == SiteKind.IMPERIAL_OUTPOST) && record.dimension().equals(level.dimension())) {
				SitePiece piece = RuntimeSites.pieceOf(level, record);
				avoid.add(piece != null ? piece.box().getCenter() : record.origin());
			}
		}

		return new MeetingSites.Rules(FOOTPRINT, CLEARANCE, MAX_SLOPE, PlacementRules.spawnProtection(level, FOOTPRINT), avoid, b.outpostSeparation);
	}

	/**
	 * Places a runtime outpost of {@code army} near the player and records it; returns its terminal,
	 * or null when no spot passed (the next {@code ensureNear} tries again).
	 */
	public static @Nullable BlockPos place(ServerPlayer p, SiteStates.Army army, SiteBalance b) {
		ServerLevel level = p.level();
		Identifier template = template(army);
		MeetingSites.Rules rules = rules(level, b);
		Vec3 origin = p.position();
		Vec3 forward = p.getLookAngle();

		if (StoryService.balance().placeStructures) {
			RuntimeSites.Placement placement = RuntimeSites.placeSurface(level, template, origin, forward, b.outpostRings, b.outpostFallback, rules, BURIED, null);
			return placement == null ? null : SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.TERMINAL);
		}

		RuntimeSites.Spot spot = RuntimeSites.findSurface(level, template, origin, forward, b.outpostRings, b.outpostFallback, rules, BURIED);
		return spot == null ? null : placeTerminalOnly(level, army, template, spot);
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
