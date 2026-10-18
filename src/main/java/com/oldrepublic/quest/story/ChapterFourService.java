package com.oldrepublic.quest.story;

import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.travel.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Chapter Four's story rules (spec MILESTONE_4.md 8). Phase 0 stub: the M4 {@code QuestHooks}
 * land here with empty bodies. W2 owns and fills it.
 */
public final class ChapterFourService {
	/** A clue was recorded by {@link ChapterFour#addClue} (8.5): XP, journal and the stage change. */
	static void onClue(ServerPlayer p, int clue) {
	}

	public static void onPlanetArrived(ServerPlayer p, Planets.Planet planet, boolean first) {
	}

	public static void onShipStateChanged(ServerPlayer owner, ShipState from, ShipState to) {
	}

	public static void onShipPartFitted(ServerPlayer owner, int socket) {
	}

	public static void onCrafted(ServerPlayer p, Identifier schematic, int quality, boolean first) {
	}

	public static void onDisciplineLevel(ServerPlayer p, String discipline, int level) {
	}

	public static void onPurchase(ServerPlayer p, String vendor, ItemStack bought, int price) {
	}

	public static void onWaystoneCleansed(ServerPlayer p, BlockPos pos, boolean heartstone) {
	}

	public static void onHunterKilled(ServerPlayer p, LivingEntity hunter, boolean owned) {
	}

	public static void onDroidKilled(ServerPlayer p, LivingEntity droid) {
	}

	public static void onBeastKilled(ServerPlayer p, LivingEntity beast, String kind) {
	}

	public static void onExpeditionDone(ServerPlayer p, PlanetSites.Site site) {
	}

	/** Loads the class during mod init. */
	public static void init() {
	}

	private ChapterFourService() {
	}
}
