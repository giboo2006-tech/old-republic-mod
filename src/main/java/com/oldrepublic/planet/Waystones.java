package com.oldrepublic.planet;

import java.util.function.Consumer;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.force.sense.SenseTargets;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.block.HeartstoneBlockEntity;
import com.oldrepublic.planet.block.WaystoneBlock;
import com.oldrepublic.planet.block.WaystoneBlockEntity;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SitePois;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * The Watch waystones (spec MILESTONE_4.md 3.4, 8.4, 12.3).
 *
 * <p><b>Corruption.</b> Worldgen places the clean {@code planet/waystone} only; its stone's block
 * entity carries {@code pending} and its template-local position. The first time the stone ticks it
 * decides, seeded by its position ({@link #corrupted}), whether it is one of the corrupted quarter;
 * if so, {@code planet/waystone_corrupted} is stamped over the same cells with the same rotation
 * (the stone's facing gives it). So the decision is the same on every server with the same seed and
 * needs no structure processor.
 *
 * <p><b>Cleansing</b> is per player: a Force Sense cast within {@code planets.waystoneSenseRange}
 * of a corrupted stone cleanses it for the caster only (the stone stays corrupted for everyone
 * else), plays the cleanse, and reports it ({@link QuestHooks#onWaystoneCleansed}). The corrupted
 * ambience goes only to players near the stone it is not cleansed for.
 */
public final class Waystones {
	public static final Identifier CLEAN_TEMPLATE = OldRepublicMod.id("planet/waystone");
	public static final Identifier CORRUPTED_TEMPLATE = OldRepublicMod.id("planet/waystone_corrupted");
	/** Layers both waystone templates set into the ground (their feet layer). */
	public static final int BURIED = 2;
	/** Seconds a client keeps the corrupt loop alive after one refresh; refreshes come every 2 seconds. */
	public static final float AMBIENCE_KEEP_ALIVE = 3f;
	private static final long SALT = 0x57415953544f4e45L;

	/** Whether the stone at {@code stone} is corrupted at {@code chance}: seeded by its position only. */
	public static boolean corrupted(BlockPos stone, double chance) {
		return RandomSource.create(stone.asLong() ^ SALT).nextDouble() < chance;
	}

	/** {@link #corrupted} at the configured {@code planets.waystoneCorruptedChance}. */
	public static boolean corruptedAt(BlockPos stone) {
		return corrupted(stone, PlanetBalance.get().waystoneCorruptedChance);
	}

	/**
	 * Decides a pending stone once. A clean result clears {@code pending}. A corrupted result stamps
	 * the corrupted template over the stone's own template (or, for a stone without a template
	 * position, swaps the block); if the stamp cannot run yet (a neighbouring chunk is not loaded),
	 * the stone stays pending and tries again on its next interval.
	 */
	public static void decide(ServerLevel level, WaystoneBlockEntity stone) {
		BlockPos pos = stone.getBlockPos();
		BlockState state = stone.getBlockState();

		if (stone.corrupted() || !corruptedAt(pos)) {
			stone.setPending(false);
			return;
		}

		BlockPos local = stone.local();

		if (local == null) {
			level.setBlock(pos, PlanetBlocks.WAYSTONE_CORRUPTED.defaultBlockState().setValue(WaystoneBlock.FACING, state.getValue(WaystoneBlock.FACING)),
					Block.UPDATE_ALL);
			return;
		}

		Rotation rotation = RuntimeSites.facing(state.getValue(WaystoneBlock.FACING));
		BlockPos origin = pos.subtract(StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO));
		SitePiece piece = RuntimeSites.stamp(level, SiteKind.WAYSTONE, CORRUPTED_TEMPLATE, origin, rotation);

		if (piece == null) {
			OldRepublicMod.LOGGER.debug("Waystone at {} waits for its neighbours to load before turning corrupted", pos);
		} else if (level.getBlockEntity(pos) instanceof WaystoneBlockEntity placed) {
			placed.setPending(false);
		}
	}

	/**
	 * Sends the corrupted loop's refresh (toPlayer, every 40 ticks) to every player within
	 * {@code planets.waystoneAmbienceRange} the stone is not cleansed for.
	 */
	public static void sendAmbience(ServerLevel level, WaystoneBlockEntity stone) {
		Vec3 centre = Vec3.atCenterOf(stone.getBlockPos());
		double range = PlanetBalance.get().waystoneAmbienceRange;

		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && player.position().distanceToSqr(centre) <= range * range && !stone.cleansedFor(player.getUUID())) {
				EffectEvents.toPlayer(player, PlanetEffects.WAYSTONE, centre, null, -1, AMBIENCE_KEEP_ALIVE, PlanetEffects.WAYSTONE_CORRUPT);
			}
		}
	}

	/**
	 * Cleanses the corrupted stone at {@code pos} for {@code player}: records it, plays the cleanse
	 * (around, with the player's entity id so only their own loop stops) and reports it. False when
	 * there is no corrupted stone there or it was already cleansed for them.
	 */
	public static boolean cleanse(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();

		if (!(level.getBlockEntity(pos) instanceof WaystoneBlockEntity stone) || !stone.corrupted() || !stone.addCleansed(player.getUUID())) {
			return false;
		}

		EffectEvents.around(level, PlanetEffects.WAYSTONE, Vec3.atCenterOf(pos), null, player.getId(), 1f, PlanetEffects.WAYSTONE_CLEANSE);
		QuestHooks.onWaystoneCleansed(player, pos.immutable(), false);
		return true;
	}

	/**
	 * Whether a corrupted waystone stands within {@code radius} blocks of {@code pos}, from the POI
	 * index and loaded chunks only (W6: hollowhorns avoid them, 3.4).
	 */
	public static boolean corruptedNear(ServerLevel level, BlockPos pos, int radius) {
		for (BlockPos stone : SitePois.find(level, PlanetBlocks.WAYSTONE_POI, pos, radius)) {
			if (loaded(level, stone) && level.getBlockState(stone).getBlock() instanceof WaystoneBlock block && block.corrupted()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * The Force Sense provider (registered by {@link PlanetFeature}): a cast cleanses every
	 * corrupted waystone within {@code planets.waystoneSenseRange} of the caster that is not
	 * cleansed for them yet, and reveals the corrupted stones and the Heartstone within the block
	 * range that are still corrupted for the caster.
	 */
	static void collectSense(ServerPlayer caster, int blockRange, int creatureRange, Consumer<SenseTargets.Point> out) {
		ServerLevel level = caster.level();

		if (!Planets.isStoryLevel(level)) {
			return;
		}

		double senseRange = PlanetBalance.get().waystoneSenseRange;
		int radius = (int) Math.ceil(Math.max(blockRange, senseRange));
		Vec3 at = caster.position();

		for (BlockPos pos : SitePois.find(level, PlanetBlocks.WAYSTONE_POI, caster.blockPosition(), radius)) {
			if (!loaded(level, pos) || !(level.getBlockEntity(pos) instanceof WaystoneBlockEntity stone) || !stone.corrupted()
					|| stone.cleansedFor(caster.getUUID())) {
				continue;
			}

			double distance = Vec3.atCenterOf(pos).distanceTo(at);

			if (distance <= senseRange) {
				cleanse(caster, pos);
			} else if (distance <= blockRange) {
				out.accept(new SenseTargets.Point(SenseTargets.Kind.BLOCK, Vec3.atCenterOf(pos), -1, Attitude.NEUTRAL));
			}
		}

		for (BlockPos pos : SitePois.find(level, PlanetBlocks.HEARTSTONE_POI, caster.blockPosition(), blockRange)) {
			BlockEntity entity = loaded(level, pos) ? level.getBlockEntity(pos) : null;

			if (entity instanceof HeartstoneBlockEntity heart && !heart.cleansedFor(caster.getUUID())) {
				out.accept(new SenseTargets.Point(SenseTargets.Kind.BLOCK, Vec3.atCenterOf(pos), -1, Attitude.NEUTRAL));
			}
		}
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
	}

	private Waystones() {
	}
}
