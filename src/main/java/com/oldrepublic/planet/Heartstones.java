package com.oldrepublic.planet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.block.HeartstoneBlockEntity;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Heartstone (spec MILESTONE_4.md 3.4, 8.4, 12.3): the shared great waystone beside the
 * Greenwatch. The cleansing trial is the story's (W2); this class gives it the stone's look and
 * sound and records the result. The stone shows the state of whoever stands closest: while that
 * player has not cleansed it, everyone within the ambience range gets the corrupted loop.
 */
public final class Heartstones {
	public static final Identifier TEMPLATE = OldRepublicMod.id("planet/heartstone");

	/**
	 * The Heartstone block of the placed Heartstone whose {@code HEART} anchor (the position
	 * {@link PlanetSites#ensure} returns) is {@code heart}; null when no Heartstone is recorded there.
	 * For the story's {@link #wave} and {@link #complete} calls.
	 */
	public static @Nullable BlockPos stone(ServerLevel level, BlockPos heart) {
		SitePiece piece = StructureLocator.runtimePieceAt(level, heart, SiteKind.HEARTSTONE);
		return piece == null ? null : SiteAnchors.find(piece, SiteAnchors.Anchor.STONE);
	}

	/** A wave of the cleansing begins (W2 calls it): dark mist wells at the stone's base. {@code wave} is 1 to 3. */
	public static void wave(ServerLevel level, BlockPos stone, int wave) {
		EffectEvents.around(level, PlanetEffects.HEARTSTONE, Vec3.atBottomCenterOf(stone), null, -1, wave, PlanetEffects.HEARTSTONE_WAVE);
	}

	/**
	 * The cleansing is complete for {@code player} (W2 calls it): records it on the stone, plays the
	 * slow exhale of pale motes and reports it ({@code heartstone} = true). False when the stone is
	 * not at {@code stone} or was already cleansed for them.
	 */
	public static boolean complete(ServerPlayer player, BlockPos stone) {
		ServerLevel level = player.level();

		if (!(level.getBlockEntity(stone) instanceof HeartstoneBlockEntity heart) || !heart.addCleansed(player.getUUID())) {
			return false;
		}

		EffectEvents.around(level, PlanetEffects.HEARTSTONE, Vec3.atCenterOf(stone), null, player.getId(), 1f, PlanetEffects.HEARTSTONE_COMPLETE);
		QuestHooks.onWaystoneCleansed(player, stone.immutable(), true);
		return true;
	}

	/**
	 * The corrupted loop (the waystone event's variant 0) for every player within the ambience range,
	 * while the nearest of them has not cleansed the stone.
	 */
	public static void sendAmbience(ServerLevel level, HeartstoneBlockEntity heart) {
		Vec3 centre = Vec3.atCenterOf(heart.getBlockPos());
		double range = PlanetBalance.get().waystoneAmbienceRange;
		ServerPlayer nearest = null;

		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && player.position().distanceToSqr(centre) <= range * range
					&& (nearest == null || player.position().distanceToSqr(centre) < nearest.position().distanceToSqr(centre))) {
				nearest = player;
			}
		}

		if (nearest == null || heart.cleansedFor(nearest.getUUID())) {
			return;
		}

		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && player.position().distanceToSqr(centre) <= range * range) {
				EffectEvents.toPlayer(player, PlanetEffects.WAYSTONE, centre, null, -1, Waystones.AMBIENCE_KEEP_ALIVE, PlanetEffects.WAYSTONE_CORRUPT);
			}
		}
	}

	private Heartstones() {
	}
}
