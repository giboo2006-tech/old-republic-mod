package com.oldrepublic.mission;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.LegendaryDrops;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.FactionM5Balance;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionDefinition.Objective;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.quest.story.ChapterFiveRules;
import com.oldrepublic.quest.story.FactionStanding;
import com.oldrepublic.space.SpaceMissions;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.FlightBalance;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.TravelService;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;

/**
 * The missions flown in space (spec MILESTONE_5.md 5.8, 5.9, 7.2): the two space objectives
 * ({@code space_run}, {@code space_clear}) of the quiet cargo ({@code c5_cargo}) and of Saltgate's and
 * the holdfast's boards ({@code ghessar_run}, {@code tyrvann_patrol}) and the boards' daily space
 * bounties ({@code ghessar_bounty}, {@code tyrvann_bounty}).
 *
 * <p>The mission service starts each objective here through W2's {@link SpaceMissions} (no ground
 * site: the objective is ACTIVE at once), and W2 reports its end through
 * {@code QuestHooks.onSpaceObjectiveDone}, which completes or drops the mission. A bounty pays its own
 * table in place of the contract formula, outside the daily soft cap: 600 XP, 400 credits, +15 with the
 * board's faction and the Hymn-Tuned Drive's schematic roll (W2 rolls its loot), and it counts for the
 * rank 4 deed. One bounty per board per player per day.
 */
public final class SpaceContracts {
	/** The bounty template of a board ({@code <planet>_bounty}). */
	public static final String BOUNTY = "bounty";
	/** The Hymn-Tuned Drive's legendary schematic (11.2), rolled on each bounty (11.4). */
	static final Identifier DRIVE_SCHEMATIC = OldRepublicMod.id("hymn_tuned_drive_legendary");

	// ---------------------------------------------------------------- which missions

	/** Whether {@code def}'s objective is flown in space. */
	public static boolean isSpace(@Nullable MissionDefinition def) {
		return def != null && def.objective() != null && def.objective().space();
	}

	/** Whether {@code def} is a board's space bounty ({@code <planet>_bounty}). */
	public static boolean isBounty(@Nullable MissionDefinition def) {
		return def != null && def.isPlanetContract() && BOUNTY.equals(PlanetContracts.template(def));
	}

	/**
	 * Whether a board may offer {@code def} now: a space objective needs free flight ({@code flight.enabled},
	 * 14.4); on Peaceful no {@code space_clear} contract or bounty is offered (7.2); a bounty once per
	 * board per player per day (5.8).
	 */
	static boolean offerable(ServerPlayer p, @Nullable MissionDefinition def) {
		if (def == null || !isSpace(def)) {
			return def != null;
		}

		if (!FlightBalance.get().enabled) {
			return false;
		}

		if (def.objective() == Objective.SPACE_CLEAR && p.level().getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}

		return !isBounty(def) || def.planet() == null || FactionStanding.bountyDue(p, def.planet());
	}

	// ---------------------------------------------------------------- starting

	/**
	 * Starts {@code def}'s space objective for {@code p} through W2 (before the mission is saved as
	 * active); false, with a message, when it is refused now (no ship, a full hold, flight off).
	 */
	static boolean onStarted(ServerPlayer p, MissionDefinition def) {
		MinecraftServer server = p.level().getServer();
		Objective objective = def.objective();

		if (objective == null || !objective.space()) {
			return true;
		}

		if (!FlightBalance.get().enabled) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.space.no_flight"));
			return false;
		}

		if (ShipRegistry.of(server, p.getUUID()).isEmpty()) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.space.no_ship"));
			return false;
		}

		Planets.Planet system = system(p, def);

		if (system == null || !SpaceMissions.start(p, def.id(), objective, system, isBounty(def))) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.space.refused"));
			return false;
		}

		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.space." + objective.id(),
				Component.translatable("mission.oldrepublic.space.orbit." + system.id())));
		return true;
	}

	/**
	 * The orbit the objective is flown in: the quiet cargo from Ghessar's (6.3), a board's run or patrol
	 * over its own world, and a bounty in one of the player's unlocked orbits, seeded by the day and by
	 * the fence's ciphers at that board (5.8, 7.1). Null when no orbit is there.
	 */
	static Planets.@Nullable Planet system(ServerPlayer p, MissionDefinition def) {
		MinecraftServer server = p.level().getServer();

		if (ChapterFiveRules.CARGO.equals(def.id())) {
			return orbitOrNull(server, Planets.Planet.GHESSAR);
		}

		Planets.Planet board = PlanetContracts.planetOf(def);

		if (!isBounty(def) || board == null) {
			return board == null ? null : orbitOrNull(server, board);
		}

		List<Planets.Planet> orbits = new ArrayList<>();

		for (Destination d : Destination.values()) {
			Planets.Planet planet = d.planet();

			if (planet != null && !orbits.contains(planet) && Planets.orbitLevel(server, planet) != null && TravelService.isUnlocked(p, d)) {
				orbits.add(planet);
			}
		}

		if (orbits.isEmpty()) {
			return orbitOrNull(server, board);
		}

		long salt = Clocks.day(server) * 31L + FactionStanding.ciphers(p, board.id());
		RandomSource random = RandomSource.create(Rewards.seed(p, "bounty:" + board.id(), salt));
		return orbits.get(random.nextInt(orbits.size()));
	}

	private static Planets.@Nullable Planet orbitOrNull(MinecraftServer server, Planets.Planet planet) {
		return Planets.orbitLevel(server, planet) != null ? planet : null;
	}

	// ---------------------------------------------------------------- ending

	/**
	 * W2: the space objective of mission {@code missionId} ended (through Chapter Five's hook, which
	 * keeps the quiet cargo for itself). Success completes and pays the active contract; a scan, a lost
	 * crate or a fled fight drops it, to be taken again from the board tomorrow.
	 */
	public static void onSpaceObjectiveDone(ServerPlayer p, String missionId, boolean success) {
		Active active = MissionService.get(p).active;

		if (active == null || !active.id.equals(missionId)) {
			// A run of a mission no longer in hand (abandoned meanwhile): nothing to pay.
			return;
		}

		if (success) {
			MissionService.complete(p, null);
		} else {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.space.failed"));
			MissionService.abandon(p);
		}
	}

	/** The mission ended any way: W2's objective goes with it (it has already gone when W2 reported the end). */
	static void onEnded(ServerPlayer p, Active active) {
		if (isSpace(MissionLoader.get(active.id))) {
			SpaceMissions.cancel(p, active.id);
		}
	}

	/**
	 * A board's space bounty, in place of the contract pay (5.8): {@code factions_m5.bountyXp} (600),
	 * {@code bountyCredits} (400), {@code bountyRep} (+15) with the board's faction, the drive schematic's
	 * roll with its guarantee, and the deed counter; the board's bounty is taken for the day.
	 */
	static void payBounty(ServerPlayer p, MissionDefinition def, Active active) {
		FactionM5Balance b = FactionM5Balance.get();
		Progression.addXp(p, b.bountyXp, "mission");
		Credits.give(p, b.bountyCredits);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.credits", b.bountyCredits));
		Faction issuer = Faction.byId(active.issuer);

		if (issuer != null) {
			Identity.changeReputation(p, issuer, b.bountyRep, "mission." + active.id, false);
		}

		LegendaryDrops.roll(p, DRIVE_SCHEMATIC, b.driveSchematicChance, b.driveSchematicPity);
		String board = def.planet() != null ? def.planet() : "";
		FactionStanding.onBountyDone(p, board, Clocks.day(p.level().getServer()));
		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.space.bounty_paid"));
	}

	private SpaceContracts() {
	}
}
