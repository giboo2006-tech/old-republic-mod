package com.oldrepublic.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.enemy.PoacherCaptain;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Yielding;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.mission.MissionDefinition.Objective;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.world.site.SiteAnchors.Anchor;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The seven objective types (spec 5.7) and what reaching a mission site does (3.6, 6.9).
 *
 * <p>The counting is pure: {@link #advance} moves an objective on one trigger and
 * {@link #resolveOnArrival} decides what arriving does, Peaceful included, so the game tests check
 * every rule without a world. The rest spawns each objective's people at the site's anchors when
 * the owner first comes within {@code missions.siteSpawnDistance}.
 *
 * <ul>
 * <li>{@code clear}: defeat the crew ({@code clear_camp}).</li>
 * <li>{@code recover}: deal with the chief, take the item, bring it back (R2, I1).</li>
 * <li>{@code rescue}: defeat the bound guards, then decide the wounded (R1).</li>
 * <li>{@code survey}: cast Sense near the site's heart.</li>
 * <li>{@code secure}: defeat the guards at the site's surface.</li>
 * <li>{@code intercept}: defeat 3 troopers and decide their sergeant ({@code patrol_intercept}).</li>
 * <li>{@code yield}: fight the squad until the sergeant yields and decide him (I2).</li>
 * </ul>
 */
public final class Objectives {
	/** What happened that may move an objective. */
	public enum Trigger {
		/** A fighter of the mission died. */
		KILL,
		/** A downed trooper of the mission was tended and stood. */
		TEND,
		/** The mission item was taken. */
		ITEM,
		/** Sense was cast within {@code surveyHeartRange} of the heart. */
		SENSE,
		/** The sergeant's fate was decided, or he died undecided. */
		SERGEANT
	}

	/**
	 * An objective after one trigger.
	 *
	 * @param complete the mission is done and pays now (a choice, if any, already made or none owed)
	 */
	public record Step(Stage stage, int progress, int extra, boolean complete) {
	}

	/**
	 * What reaching the site does.
	 *
	 * @param progress      the counter after arriving
	 * @param spawnFighters whether the fighters spawn (never on Peaceful, except troopers, who stand down)
	 * @param yieldAtOnce   whether the sergeant yields at once (Peaceful incident squads)
	 * @param guardsDone    whether the chief or fence is out of the way already (Peaceful)
	 * @param complete      whether the mission is done on arrival
	 */
	public record Arrival(Stage stage, int progress, boolean spawnFighters, boolean yieldAtOnce, boolean guardsDone, boolean complete) {
	}

	/** Troopers a squad objective spawns before the level-based extra rifleman (spec 3.6). */
	private static final List<TrooperRole> SQUAD = List.of(TrooperRole.RIFLEMAN, TrooperRole.RIFLEMAN, TrooperRole.MEDIC, TrooperRole.SERGEANT);
	private static final List<Anchor> CREW_ANCHORS = List.of(Anchor.CREW_1, Anchor.CREW_2, Anchor.CREW_3, Anchor.CREW_4);
	private static final List<Anchor> SQUAD_ANCHORS = List.of(Anchor.SQUAD_1, Anchor.SQUAD_2, Anchor.SQUAD_3, Anchor.SQUAD_4, Anchor.SQUAD_5);
	private static final List<Anchor> SENTINEL_ANCHORS = List.of(Anchor.SENTINEL_1, Anchor.SENTINEL_2, Anchor.SENTINEL_3);
	private static final List<Anchor> DOWNED_ANCHORS = List.of(Anchor.DOWNED_1, Anchor.DOWNED_2, Anchor.DOWNED_3);
	/** The extra rifleman of a squad at level {@code squad_extra_level} and above (spec 3.6). */
	private static final int SQUAD_EXTRA_LEVEL = 11;
	/** The fourth crew member of {@code clear_camp} at level {@code crew_high_level} and above (spec 3.6). */
	private static final int CREW_HIGH_LEVEL = 9;
	/** Name of I2's Republic recon sergeant. */
	static final String BORDER_SERGEANT = "npc.oldrepublic.aldo_brannick";

	// ---------------------------------------------------------------- pure rules

	/**
	 * Moves an objective on one trigger. Triggers that do not belong to the objective change nothing.
	 *
	 * @param target        the main counter's goal (fighters, the item, the heart)
	 * @param extra         the second counter (R1: wounded tended)
	 * @param extraTarget   its goal (R1: the downed troopers), 0 when unused
	 * @param sergeantDone  whether the sergeant's fate is already decided (intercept, yield)
	 */
	public static Step advance(Objective objective, Trigger trigger, Stage stage, int progress, int target, int extra, int extraTarget,
			boolean sergeantDone) {
		Step unchanged = new Step(stage, progress, extra, false);

		if (stage == Stage.PLACING || stage == Stage.TRAVEL || stage == Stage.RETURN) {
			return unchanged;
		}

		return switch (objective) {
			case CLEAR, SECURE -> trigger == Trigger.KILL ? counted(stage, progress + 1, target, extra) : unchanged;
			case RESCUE -> switch (trigger) {
				case KILL -> {
					int killed = Math.min(target, progress + 1);
					Stage next = killed >= target ? Stage.DECIDE : stage;
					yield new Step(next, killed, extra, next == Stage.DECIDE && extraTarget > 0 && extra >= extraTarget);
				}
				case TEND -> {
					int tended = Math.min(Math.max(1, extraTarget), extra + 1);
					yield new Step(stage, progress, tended, stage == Stage.DECIDE && tended >= extraTarget);
				}
				default -> unchanged;
			};
			case RECOVER -> trigger == Trigger.ITEM ? new Step(Stage.RETURN, Math.max(1, target), extra, false) : unchanged;
			case SURVEY -> trigger == Trigger.SENSE ? new Step(stage, Math.max(1, target), extra, true) : unchanged;
			case INTERCEPT -> switch (trigger) {
				case KILL -> {
					int killed = Math.min(target, progress + 1);
					yield new Step(stage, killed, extra, killed >= target && sergeantDone);
				}
				case SERGEANT -> new Step(stage, progress, extra, progress >= target);
				default -> unchanged;
			};
			case YIELD -> switch (trigger) {
				case KILL -> new Step(stage, Math.min(target, progress + 1), extra, false);
				case SERGEANT -> new Step(stage, progress, extra, true);
				default -> unchanged;
			};
		};
	}

	private static Step counted(Stage stage, int progress, int target, int extra) {
		int capped = Math.min(target, progress);
		return new Step(stage, capped, extra, capped >= target);
	}

	/**
	 * What reaching the site does (6.9). Elsewhere than Peaceful everyone spawns and the objective
	 * starts counting. On Peaceful, combat resolves on arrival: bound guards crumble and crews flee
	 * ({@code clear} and {@code secure} complete at once, R1 goes straight to the wounded, the chief or
	 * fence is gone and the item can be taken), and incident squads stand down with their sergeant
	 * yielding at once. A survey is never combat.
	 */
	public static Arrival resolveOnArrival(Objective objective, int target, boolean peaceful) {
		if (!peaceful || !objective.combat()) {
			return new Arrival(Stage.ACTIVE, 0, objective != Objective.SURVEY, false, false, false);
		}

		return switch (objective) {
			case CLEAR, SECURE -> new Arrival(Stage.ACTIVE, target, false, false, true, true);
			case RESCUE -> new Arrival(Stage.DECIDE, target, false, false, true, false);
			case RECOVER -> new Arrival(Stage.ACTIVE, 0, false, false, true, false);
			case INTERCEPT, YIELD -> new Arrival(Stage.ACTIVE, target, true, true, false, false);
			case SURVEY -> new Arrival(Stage.ACTIVE, 0, false, false, false, false);
		};
	}

	/**
	 * The main counter's goal at accept time: crew size ({@code crew}, or {@code crew_high} at
	 * {@code crew_high_level}), guards by site kind for {@code secure} ({@code sentinels} at a ruin or
	 * tomb, {@code scavengers} at a wreck), the bound guards for {@code rescue}, {@code kills} for
	 * {@code intercept}, the squad without its sergeant for {@code yield}, and 1 for the item or heart.
	 */
	public static int target(MissionDefinition def, Objective objective, int level, @Nullable SiteKind siteKind) {
		return switch (objective) {
			case CLEAR -> level >= def.count("crew_high_level", CREW_HIGH_LEVEL) ? def.count("crew_high", 4) : def.count("crew", 3);
			case SECURE -> siteKind == SiteKind.WRECK_FIELD ? def.count("scavengers", 4) : def.count("sentinels", 3);
			case RESCUE -> def.count("sentinels", 3);
			case INTERCEPT -> def.count("kills", 3);
			case YIELD -> squadRoles(def, level).size() - 1;
			case RECOVER, SURVEY -> 1;
		};
	}

	/** The second counter's goal: the wounded of {@code rescue}; 0 for the others. */
	public static int extraTarget(MissionDefinition def, Objective objective) {
		return objective == Objective.RESCUE ? def.count("downed", 3) : 0;
	}

	/** The squad of a trooper objective: two riflemen, a medic and the sergeant, plus a rifleman at {@code squad_extra_level}. */
	static List<TrooperRole> squadRoles(MissionDefinition def, int level) {
		List<TrooperRole> roles = new ArrayList<>(SQUAD);

		if (level >= def.count("squad_extra_level", SQUAD_EXTRA_LEVEL)) {
			roles.add(TrooperRole.RIFLEMAN);
		}

		return roles;
	}

	/** Lang key of the counter label on the action bar and tracker ("Sentinels 2/3"). */
	public static String counterKey(Objective objective, @Nullable SiteKind siteKind) {
		String suffix = objective == Objective.SECURE && siteKind == SiteKind.WRECK_FIELD ? ".wreck" : "";
		return "mission.oldrepublic.counter." + objective.id() + suffix;
	}

	// ---------------------------------------------------------------- spawning at the site

	/**
	 * Spawns the objective's people for the owner at the site and returns how many fighters count
	 * toward the main counter (the caller lowers an over-high target to it, so a refused spawn never
	 * locks the mission). {@code piece} is the runtime camp, or null without one (anchors then fall
	 * back to a ring around the site).
	 */
	static int spawn(ServerPlayer owner, MissionDefinition def, MissionData.Active active, Objective objective, Arrival arrival, @Nullable SitePiece piece) {
		ServerLevel level = owner.level();
		BlockPos site = active.site;

		if (site == null) {
			return 0;
		}

		UUID id = owner.getUUID();
		String missionId = active.id;
		int fighters = 0;

		switch (objective) {
			case CLEAR -> {
				if (arrival.spawnFighters()) {
					fighters = MissionSites.spawnCrew(level, id, missionId, MissionSites.spots(level, piece, site, CREW_ANCHORS), active.target).size();
				} else {
					owner.sendSystemMessage(Component.translatable("message.oldrepublic.mission.crew_fled"));
				}
			}
			case SECURE -> fighters = spawnGuards(owner, active, arrival.spawnFighters());
			case RESCUE -> {
				if (arrival.spawnFighters()) {
					fighters = MissionSites.spawnSentinels(level, id, missionId, MissionSites.spots(level, piece, site, SENTINEL_ANCHORS), active.target,
							SentinelVariant.WATCHER).size();
				} else {
					owner.sendSystemMessage(Component.translatable("message.oldrepublic.mission.binding_failed"));
				}

				List<BlockPos> downed = MissionSites.spots(level, piece, site, DOWNED_ANCHORS).subList(0, Math.min(DOWNED_ANCHORS.size(),
						Math.max(0, extraTarget(def, objective))));
				int spawned = MissionSites.spawnDowned(level, id, missionId, downed, Faction.REPUBLIC).size();

				if (spawned < extraTarget(def, objective)) {
					OldRepublicMod.LOGGER.warn("Mission {}: only {} downed troopers spawned", missionId, spawned);
				}
			}
			case RECOVER -> {
				if (arrival.spawnFighters()) {
					boolean fence = MissionItems.byId(def.item()) == MissionItems.FENCE_BOOK;

					// Hessa's people do not fight: she yields at once (Cameos).
					if (fence || !Cameos.hessaRunsCache(owner)) {
						MissionSites.spawnCrew(level, id, missionId, MissionSites.spots(level, piece, site, CREW_ANCHORS), def.count("crew", 4));
					}

					spawnChief(owner, def, active, piece);
				} else {
					owner.sendSystemMessage(Component.translatable("message.oldrepublic.mission.crew_fled"));
				}
			}
			case INTERCEPT -> fighters = spawnSquad(owner, def, active, objective, arrival, piece);
			case YIELD -> {
				fighters = spawnSquad(owner, def, active, objective, arrival, piece);
				// I2: the survey post's own man lies downed at the WOUNDED anchor; tending him is worth Sith Empire standing.
				if (def.count("downed", 1) > 0) {
					MissionSites.spawnDowned(level, id, missionId, List.of(MissionSites.spot(level, piece, site, Anchor.WOUNDED)), Faction.SITH_EMPIRE);
				}
			}
			case SURVEY -> {
			}
		}

		return fighters;
	}

	/** A secure contract's guards at the site's surface: Watchers at a ruin, Tomb sentinels at a tomb, Vasch's scavengers at a wreck. */
	private static int spawnGuards(ServerPlayer owner, MissionData.Active active, boolean spawn) {
		SiteKind kind = SiteKind.byId(active.siteKind);
		boolean wreck = kind == SiteKind.WRECK_FIELD;

		if (!spawn) {
			owner.sendSystemMessage(Component.translatable(wreck ? "message.oldrepublic.mission.crew_fled" : "message.oldrepublic.mission.binding_failed"));
			return 0;
		}

		ServerLevel level = owner.level();
		List<Anchor> ring = new ArrayList<>();

		for (int i = 0; i < active.target; i++) {
			ring.add(Anchor.SURFACE_GUARD);
		}

		// No piece: the guards stand on a ring around the guard point instead of all on one block.
		List<BlockPos> spots = MissionSites.spots(level, null, active.site, ring);

		if (wreck) {
			return MissionSites.spawnCrew(level, owner.getUUID(), active.id, spots, active.target).size();
		}

		SentinelVariant variant = kind == SiteKind.SITH_TOMB ? SentinelVariant.TOMB : SentinelVariant.WATCHER;
		return MissionSites.spawnSentinels(level, owner.getUUID(), active.id, spots, active.target, variant).size();
	}

	/** R2's chief (by Hessa Dray's fate, {@link Cameos}) or I1's fence Dovan Lisk. */
	private static void spawnChief(ServerPlayer owner, MissionDefinition def, MissionData.Active active, @Nullable SitePiece piece) {
		ServerLevel level = owner.level();

		if (MissionItems.byId(def.item()) == MissionItems.FENCE_BOOK) {
			BlockPos at = MissionSites.spot(level, piece, active.site, Anchor.FENCE);
			PoacherCaptain fence = MissionSites.spawnCaptain(level, owner.getUUID(), active.id, at, "npc.oldrepublic.dovan_lisk",
					MissionFeature.balance().fenceYield);

			if (fence == null) {
				OldRepublicMod.LOGGER.warn("Mission {}: the fence did not spawn; the book is free to take", active.id);
				active.set(MissionData.FLAG_CHIEF_DONE);
			}

			return;
		}

		Cameos.spawnR2Chief(owner, active, MissionSites.spot(level, piece, active.site, Anchor.CHIEF));
	}

	/**
	 * A trooper squad: the other army for an intercept (Republic for the Imperial board and the other
	 * way round), the Republic recon squad for I2. Incident-tagged {@code ARMY_AND_OWNER}, spread over
	 * the {@code SQUAD_n} anchors, the sergeant set to yield into the mission's dialogue.
	 */
	private static int spawnSquad(ServerPlayer owner, MissionDefinition def, MissionData.Active active, Objective objective, Arrival arrival,
			@Nullable SitePiece piece) {
		ServerLevel level = owner.level();
		MissionBalance b = MissionFeature.balance();
		boolean border = objective == Objective.YIELD;
		Faction army = border ? Faction.REPUBLIC : otherArmy(Faction.byId(active.issuer));
		List<TrooperRole> roles = squadRoles(def, Progression.get(owner).level);
		List<BlockPos> spots = MissionSites.spots(level, piece, active.site, SQUAD_ANCHORS.subList(0, Math.min(SQUAD_ANCHORS.size(), roles.size())));
		List<LivingEntity> squad = MissionSites.spawnSquad(level, owner.getUUID(), active.id, spots.getFirst(), army, roles, IncidentMode.ARMY_AND_OWNER);
		int fighters = 0;

		for (int i = 0; i < squad.size(); i++) {
			LivingEntity trooper = squad.get(i);
			BlockPos spot = MissionSites.stand(level, spots.get(Math.min(i, spots.size() - 1)));
			trooper.snapTo(Vec3.atBottomCenterOf(spot), trooper.getYRot(), 0f);

			if (MissionSites.hasRole(trooper, MissionSites.ROLE_SERGEANT)) {
				Identifier dialogue = OldRepublicMod.id(border ? "yield_border" : "yield_intercept");
				Troopers.setYield(trooper, (float) (border ? b.borderYield : b.interceptYield), dialogue);

				if (border) {
					// I2's sergeant is named in the brief and in yield_border (spec 5.4).
					trooper.setCustomName(Component.translatable(BORDER_SERGEANT));
				}

				if (arrival.yieldAtOnce()) {
					Yielding.forceYield(trooper);
				}
			} else {
				fighters++;
			}
		}

		if (arrival.yieldAtOnce()) {
			owner.sendSystemMessage(Component.translatable("message.oldrepublic.mission.stand_down"));
		}

		return fighters;
	}

	/** The army an intercept squad belongs to: the other one. */
	static Faction otherArmy(@Nullable Faction issuer) {
		return issuer == Faction.SITH_EMPIRE ? Faction.REPUBLIC : Faction.SITH_EMPIRE;
	}

	private Objectives() {
	}
}
