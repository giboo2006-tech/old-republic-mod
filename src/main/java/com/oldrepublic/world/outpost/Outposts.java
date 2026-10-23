package com.oldrepublic.world.outpost;

import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SiteStates;
import com.oldrepublic.world.site.StructureLocator;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Republic and Imperial outposts near the player (spec 3.5): worldgen first, then a runtime outpost
 * placed through {@code RuntimeSites}. Signatures are frozen (spec 15.1).
 */
public final class Outposts {
	/** What {@link #ensureNear} does for one army. */
	public enum Decision {
		/** Use the worldgen outpost within {@code sites.outpostWorldgenRadius}. */
		WORLDGEN,
		/** Reuse the runtime outpost within {@code sites.outpostReuseRadius}. */
		REUSE,
		/** Place a new runtime outpost. */
		PLACE,
		/** No outpost is near and no new one may be placed: use the nearest existing outpost, at any distance. */
		CAP_NEAREST
	}

	/**
	 * What {@link #decide} looks at, for one army.
	 *
	 * @param player         the player's position
	 * @param worldgen       the nearest worldgen outpost the locator found, or null
	 * @param runtimeNearest the nearest runtime outpost of this army at any distance, or null
	 * @param runtimeCount   the runtime outposts in the world, both armies together
	 */
	public record OutpostQuery(BlockPos player, @Nullable BlockPos worldgen, @Nullable BlockPos runtimeNearest, int runtimeCount) {
	}

	/**
	 * The pure rule of 3.5 (a test seam): a worldgen outpost within {@code outpostWorldgenRadius},
	 * else a runtime one within {@code outpostReuseRadius}, else a new one unless runtime outposts are
	 * off or {@code maxRuntimeOutposts} exist. Distances are horizontal and inclusive.
	 */
	public static Decision decide(OutpostQuery q, SiteBalance b) {
		if (q.worldgen() != null && within(q.player(), q.worldgen(), b.outpostWorldgenRadius)) {
			return Decision.WORLDGEN;
		}

		if (q.runtimeNearest() != null && within(q.player(), q.runtimeNearest(), b.outpostReuseRadius)) {
			return Decision.REUSE;
		}

		if (!b.runtimeOutposts || q.runtimeCount() >= b.maxRuntimeOutposts) {
			return Decision.CAP_NEAREST;
		}

		return Decision.PLACE;
	}

	/**
	 * Builds the frozen query from a detached {@link WorldSites.Data} (a test seam): the nearest
	 * runtime outpost of {@code army} in {@code dimension} at any distance (its footprint centre), and
	 * the runtime outposts of both armies in every dimension.
	 *
	 * @throws IllegalArgumentException for a faction without outposts (the Jedi Order, the Frontier)
	 */
	public static OutpostQuery query(BlockPos player, @Nullable BlockPos worldgen, WorldSites.Data sites, Faction army, ResourceKey<Level> dimension) {
		SiteStates.Army outposts = armyOf(army);
		WorldSites.SiteRecord nearest = sites.nearest(OutpostPlacement.kind(outposts), dimension, player, Integer.MAX_VALUE);
		int count = sites.count(SiteKind.REPUBLIC_OUTPOST) + sites.count(SiteKind.IMPERIAL_OUTPOST);
		return new OutpostQuery(player, worldgen, nearest == null ? null : centre(nearest), count);
	}

	/**
	 * Makes sure each army has an outpost the player can reach (3.5): asks {@link #decide} per army
	 * and places a runtime outpost when it says so. Chapter Three sites are overworld only (6.9,
	 * 12.2): in any other dimension it does nothing.
	 */
	public static void ensureNear(ServerPlayer p) {
		ensureNear(p, (army, decision) -> {
		});
	}

	/**
	 * {@link #ensureNear(ServerPlayer)} that reports each army's decision once its locator search has
	 * run (for {@code /oldrepublic outpost ensure}). The worldgen search ignores runtime outposts, and
	 * each decision reads the world's sites afresh, so two calls in a row never place twice. Outside
	 * the overworld nothing is searched, placed or reported.
	 */
	public static void ensureNear(ServerPlayer p, BiConsumer<Faction, Decision> report) {
		if (p.level().dimension() != Level.OVERWORLD) {
			return;
		}

		SiteBalance b = SiteBalance.get();

		for (SiteStates.Army army : SiteStates.Army.values()) {
			StructureLocator.find(p, OutpostPlacement.kind(army), b.outpostWorldgenRadius, SitePiece::runtime,
					worldgen -> report.accept(army.faction(), decideAndAct(p, army, worldgen, b)));
		}
	}

	/**
	 * The bounty terminal of {@code army}'s nearest outpost within {@code radius} blocks
	 * (horizontally), or null: runtime outposts from {@link WorldSites}, and the worldgen outpost the
	 * locator has cached for this player. Never blocks; when nothing is cached it starts a search so
	 * a later call can answer.
	 */
	public static @Nullable BlockPos nearestTerminal(ServerPlayer p, Faction army, int radius) {
		SiteStates.Army outposts = SiteStates.Army.of(army);

		if (outposts == null) {
			return null;
		}

		ServerLevel level = p.level();
		SiteKind kind = OutpostPlacement.kind(outposts);
		BlockPos from = p.blockPosition();
		BlockPos best = null;
		long bestSqr = (long) radius * radius;

		for (WorldSites.SiteRecord record : WorldSites.all(level.getServer())) {
			if (record.kind() != kind || !record.dimension().equals(level.dimension())) {
				continue;
			}

			SitePiece piece = RuntimeSites.pieceOf(level, record);
			BlockPos terminal = piece == null ? null : SiteAnchors.find(piece, SiteAnchors.Anchor.TERMINAL);

			if (terminal != null && distSqr(from, terminal) <= bestSqr) {
				best = terminal;
				bestSqr = distSqr(from, terminal);
			}
		}

		SitePiece cached = StructureLocator.cached(p, kind);

		if (cached == null) {
			StructureLocator.find(p, kind, Math.max(radius, SiteBalance.get().outpostWorldgenRadius), null, piece -> {
			});
		} else {
			BlockPos terminal = SiteAnchors.find(cached, SiteAnchors.Anchor.TERMINAL);

			if (terminal != null && distSqr(from, terminal) <= bestSqr) {
				best = terminal;
			}
		}

		return best;
	}

	private static Decision decideAndAct(ServerPlayer p, SiteStates.Army army, @Nullable SitePiece worldgen, SiteBalance b) {
		ServerLevel level = p.level();
		BlockPos worldgenPos = worldgen == null ? null : worldgen.box().getCenter();
		OutpostQuery q = query(p.blockPosition(), worldgenPos, WorldSites.snapshot(level.getServer()), army.faction(), level.dimension());
		Decision decision = decide(q, b);

		if (decision == Decision.PLACE) {
			BlockPos terminal = OutpostPlacement.place(p, army, b);

			if (terminal == null) {
				OldRepublicMod.LOGGER.debug("No spot for a runtime {} outpost near {}; the next check tries again", army.getSerializedName(), p.getName().getString());
			}
		}

		return decision;
	}

	/** The footprint centre of a runtime outpost record (both outpost templates are {@value OutpostPlacement#FOOTPRINT} blocks square). */
	private static BlockPos centre(WorldSites.SiteRecord record) {
		int half = OutpostPlacement.FOOTPRINT / 2;
		return record.origin().offset(StructureTemplate.transform(new BlockPos(half, 0, half), Mirror.NONE, record.rotation(), BlockPos.ZERO));
	}

	private static SiteStates.Army armyOf(Faction army) {
		SiteStates.Army outposts = SiteStates.Army.of(army);

		if (outposts == null) {
			throw new IllegalArgumentException(army + " has no outposts");
		}

		return outposts;
	}

	private static long distSqr(BlockPos from, BlockPos to) {
		long dx = to.getX() - from.getX();
		long dz = to.getZ() - from.getZ();
		return dx * dx + dz * dz;
	}

	private static boolean within(BlockPos from, BlockPos to, int radius) {
		return distSqr(from, to) <= (long) radius * radius;
	}

	private Outposts() {
	}
}
