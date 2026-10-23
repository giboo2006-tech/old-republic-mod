package com.oldrepublic.mission;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.world.site.SiteKind;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The mission feature's entry points for other features (spec section 5): the bounty board, the
 * active mission and the own-side contract count toward rank 3. The first three signatures are
 * frozen (spec section 15.1); the reads work on both sides, from the synced attachment.
 */
public final class Missions {
	private static final String CLEAR_CAMP = "clear_camp";
	private static final int DEFAULT_CREW = 3;

	/**
	 * Opens the bounty board of the terminal at {@code terminal}, issued by {@code issuer}
	 * ({@link Faction#REPUBLIC} or {@link Faction#SITH_EMPIRE}).
	 */
	public static void openBoard(ServerPlayer p, BlockPos terminal, Faction issuer) {
		MissionBoard.open(p, terminal, issuer);
	}

	/**
	 * Opens the planet contract board of a hub's contract terminal (spec MILESTONE_4.md 8.7): the six
	 * contracts of {@code planet}, issued by the Jedi Order on Selvaren and the Sith Empire on Varkhast.
	 */
	public static void openPlanetBoard(ServerPlayer p, BlockPos terminal, Planets.Planet planet) {
		MissionBoard.openPlanet(p, terminal, planet);
	}

	/** The id of the player's active mission, or null for none. */
	public static @Nullable String activeId(Player p) {
		MissionData d = MissionService.view(p);
		return d == null || d.active == null ? null : d.active.id;
	}

	/** Contracts completed for the player's own side, toward rank 3 (5.5, 5.8). */
	public static int ownSideContracts(Player p) {
		MissionData d = MissionService.view(p);
		return d == null ? 0 : MissionRules.ownSideCount(Identity.path(p), d.ownSide);
	}

	/** Whether the player has completed this story mission. */
	public static boolean storyDone(Player p, String missionId) {
		MissionData d = MissionService.view(p);
		return d != null && d.storyDone(missionId);
	}

	/** Whether the player has discovered a site of this kind. */
	public static boolean discovered(Player p, SiteKind kind) {
		MissionData d = MissionService.view(p);
		return d != null && d.discovered(kind.id());
	}

	/**
	 * The size of a {@code raider_camp_small} crew at this player level (the {@code clear_camp}
	 * definition's {@code crew} counts, 3 or 4 by level; spec 3.6); Vasch's counter-hunt uses it
	 * (6.4). Server side, where the definitions are loaded; 3 if {@code clear_camp} is missing.
	 */
	public static int campCrew(int level) {
		MissionDefinition def = MissionLoader.get(CLEAR_CAMP);
		return def == null ? DEFAULT_CREW : Objectives.target(def, MissionDefinition.Objective.CLEAR, level, null);
	}

	/** Where the active mission points now (its site, or its officer for a turn-in), or null (used by the client steps). */
	public static @Nullable Vec3 target(ServerPlayer p) {
		MissionData d = MissionService.get(p);
		return d.active == null ? null : MissionService.target(p, d.active);
	}

	private Missions() {
	}
}
