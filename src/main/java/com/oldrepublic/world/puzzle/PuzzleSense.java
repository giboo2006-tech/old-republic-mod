package com.oldrepublic.world.puzzle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.sense.ForceSense;
import com.oldrepublic.force.sense.SenseTargets;
import com.oldrepublic.force.sense.SenseTargets.Kind;
import com.oldrepublic.force.sense.SenseTargets.Point;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What Force Sense finds among the puzzle blocks (spec 10.1, 10.3), all through POI lookups:
 * <ul>
 * <li>veiled stone within the block range: each group is revealed ({@link VeiledStoneBlock#reveal})
 * for {@code sites.veilRevealTicks}, plus {@code force_sense.seekerBonusTicks} for a Seeker, and
 * shown as one {@code BLOCK} point at its middle;</li>
 * <li>an unsolved urn keeper within {@code sites.urnSenseRange}: it plays its order and each urn is
 * an {@code URN} point;</li>
 * <li>with {@code relic_lore}: {@code ANCHOR} points on raised counterweights, armed pull anchors
 * and the urns of unsolved keepers out of hearing;</li>
 * <li>with {@code sense_seeker}: {@code CACHE} points on holocron pedestals and on the loot anchors
 * ({@code CHEST_1}, {@code CACHE}, {@code DECK_CACHE}, {@code ANCHOR_LOCKER}) of the site pieces
 * within {@code force_sense.glintRange}.</li>
 * </ul>
 * The caster owns a node as W3's Sense decides it ({@link ForcePowers#hasNode}): an active skill
 * node, or the node id in the ability set.
 */
final class PuzzleSense implements SenseTargets.Provider {
	private static final List<SiteAnchors.Anchor> LOOT_ANCHORS = List.of(SiteAnchors.Anchor.CHEST_1, SiteAnchors.Anchor.CACHE,
			SiteAnchors.Anchor.DECK_CACHE, SiteAnchors.Anchor.ANCHOR_LOCKER);

	@Override
	public void collect(ServerPlayer caster, int blockRange, int creatureRange, Consumer<Point> out) {
		ServerLevel level = caster.level();
		BlockPos centre = caster.blockPosition();
		SiteBalance b = SiteBalance.get();
		ForceData data = Progression.get(caster);
		boolean seeker = ForcePowers.hasNode(caster, data, SkillIds.SENSE_SEEKER);

		veiledStone(level, centre, blockRange, b.veilRevealTicks + (seeker ? ForceSense.balance().seekerBonusTicks : 0), out);
		Set<BlockPos> heard = urnKeepers(level, centre, b, out);

		if (ForcePowers.hasNode(caster, data, SkillIds.RELIC_LORE)) {
			relicLore(level, centre, blockRange, heard, out);
		}

		if (seeker) {
			seeker(level, centre, out);
		}
	}

	private static void veiledStone(ServerLevel level, BlockPos centre, int range, int revealTicks, Consumer<Point> out) {
		Set<BlockPos> done = new HashSet<>();

		for (BlockPos pos : find(level, PuzzlePois.VEILED_STONE, centre, range)) {
			if (done.contains(pos)) {
				continue;
			}

			Set<BlockPos> group = VeiledStoneBlock.revealGroup(level, pos, revealTicks);

			if (group.isEmpty()) {
				continue;
			}

			done.addAll(group);
			out.accept(blockPoint(Kind.BLOCK, middle(group)));
		}
	}

	/** Plays every unsolved keeper within hearing and returns the urns it pointed at. */
	private static Set<BlockPos> urnKeepers(ServerLevel level, BlockPos centre, SiteBalance b, Consumer<Point> out) {
		Set<BlockPos> heard = new HashSet<>();

		for (BlockPos pos : find(level, PuzzlePois.URN_KEEPER, centre, b.urnSenseRange)) {
			if (!(level.getBlockEntity(pos) instanceof UrnKeeperBlockEntity keeper) || level.getBlockState(pos).getValue(PuzzleStates.SOLVED)) {
				continue;
			}

			keeper.startPlayback(level, b);

			for (BlockPos urn : keeper.urnPositions()) {
				if (heard.add(urn)) {
					out.accept(blockPoint(Kind.URN, Vec3.atCenterOf(urn)));
				}
			}
		}

		return heard;
	}

	private static void relicLore(ServerLevel level, BlockPos centre, int range, Set<BlockPos> heard, Consumer<Point> out) {
		for (BlockPos pos : find(level, PuzzlePois.COUNTERWEIGHT_STONE, centre, range)) {
			BlockState state = level.getBlockState(pos);

			if (state.is(PuzzleBlocks.COUNTERWEIGHT_STONE) && !state.getValue(PuzzleStates.SUNK)) {
				out.accept(blockPoint(Kind.ANCHOR, Vec3.atCenterOf(pos)));
			}
		}

		for (BlockPos pos : find(level, PuzzlePois.PULL_ANCHOR, centre, range)) {
			BlockState state = level.getBlockState(pos);

			if (state.is(PuzzleBlocks.PULL_ANCHOR) && state.getValue(PuzzleStates.STRAIN) == 0) {
				out.accept(blockPoint(Kind.ANCHOR, Vec3.atCenterOf(pos)));
			}
		}

		for (BlockPos pos : find(level, PuzzlePois.URN_KEEPER, centre, range)) {
			if (!(level.getBlockEntity(pos) instanceof UrnKeeperBlockEntity keeper) || level.getBlockState(pos).getValue(PuzzleStates.SOLVED)) {
				continue;
			}

			for (BlockPos urn : keeper.urnPositions()) {
				if (heard.add(urn)) {
					out.accept(blockPoint(Kind.ANCHOR, Vec3.atCenterOf(urn)));
				}
			}
		}
	}

	private static void seeker(ServerLevel level, BlockPos centre, Consumer<Point> out) {
		int range = ForceSense.balance().glintRange;
		Set<BlockPos> glints = new HashSet<>(find(level, PuzzlePois.HOLOCRON_PEDESTAL, centre, range));
		double rangeSq = (double) range * range;

		for (SitePiece piece : StructureLocator.piecesNear(level, centre, range)) {
			for (SiteAnchors.Anchor anchor : LOOT_ANCHORS) {
				for (BlockPos pos : SiteAnchors.all(piece, anchor)) {
					if (pos.distSqr(centre) <= rangeSq) {
						glints.add(pos);
					}
				}
			}
		}

		for (BlockPos pos : glints) {
			out.accept(blockPoint(Kind.CACHE, Vec3.atCenterOf(pos)));
		}
	}

	private static List<BlockPos> find(ServerLevel level, ResourceKey<PoiType> type, BlockPos centre, int range) {
		return level.getPoiManager().findAll(holder -> holder.is(type), pos -> true, centre, Math.max(1, range), PoiManager.Occupancy.ANY)
				.map(BlockPos::immutable)
				.toList();
	}

	private static Point blockPoint(Kind kind, Vec3 pos) {
		return new Point(kind, pos, -1, Attitude.NEUTRAL);
	}

	private static Vec3 middle(Set<BlockPos> group) {
		double x = 0;
		double y = 0;
		double z = 0;

		for (BlockPos pos : group) {
			x += pos.getX() + 0.5;
			y += pos.getY() + 0.5;
			z += pos.getZ() + 0.5;
		}

		return new Vec3(x / group.size(), y / group.size(), z / group.size());
	}
}
