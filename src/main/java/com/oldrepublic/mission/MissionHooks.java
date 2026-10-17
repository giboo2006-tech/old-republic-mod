package com.oldrepublic.mission;

import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.identity.ContextAct;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.KillReputation;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionDefinition.Objective;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The mission feature's entry points that the quest feature's hooks route to (W2 plan 4.2). Nothing
 * else of the mission feature is called from outside. Objective kills are counted from the death
 * event of the mission's own entities ({@link MissionFeature}), whoever lands the blow; these hooks
 * add what belongs to the killer: kill reputation, the codex, and the decisions.
 */
public final class MissionHooks {
	/** Discovery, arriving at a mission camp, and the freed settler at the Republic outpost. */
	public static void onSiteEntered(ServerPlayer p, SitePiece piece) {
		Discovery.onSiteEntered(p, piece);

		if (piece.kind() == SiteKind.MISSION_SITE) {
			MissionService.checkArrival(p);
		} else if (piece.kind() == SiteKind.REPUBLIC_OUTPOST) {
			Cameos.onOutpostEntered(p, piece);
		}
	}

	/** Sense was cast: a survey completes when the caster stands within {@code surveyHeartRange} of the site's heart. */
	public static void onForceSense(ServerPlayer p, int revealed) {
		MissionData d = MissionService.get(p);
		Active active = d.active;
		MissionDefinition base = active == null ? null : MissionService.baseDefinition(active);

		if (active == null || base == null || base.objective() != Objective.SURVEY || active.site == null) {
			return;
		}

		double range = MissionFeature.balance().surveyHeartRange;

		if (p.position().distanceToSqr(Vec3.atCenterOf(active.site)) > range * range) {
			return;
		}

		d.markSurveyed(active.site);

		if (active.stage == MissionData.Stage.TRAVEL) {
			active.stage = MissionData.Stage.ACTIVE;
		}

		MissionService.commit(p, d);
		MissionService.trigger(p, Objectives.Trigger.SENSE, null);
	}

	/**
	 * A player killed a trooper. All kill reputation is applied here (5.2): -10 with its army when
	 * it counted as hostile (capped at 200 a day per army), -40 and {@code KILLED_SOLDIER} when it did
	 * not; never a gain. The army's codex page opens on the first kill.
	 */
	public static void onTrooperKilled(ServerPlayer p, LivingEntity trooper, Faction army, TrooperRole role, boolean wasHostile) {
		KillReputation.apply(p, army, wasHostile, MissionService.day(p));
		Discovery.onTrooperMet(p, army);
	}

	/**
	 * A downed trooper the player tended stood up: R1 counts its wounded, I2 pays for the downed
	 * Imperial once. The army's codex page opens on the first tend.
	 */
	public static void onTrooperTended(ServerPlayer p, LivingEntity trooper, Faction army) {
		Discovery.onTrooperMet(p, army);
		Active active = MissionService.get(p).active;

		if (active == null || !MissionSites.isMember(trooper) || !active.id.equals(MissionSites.missionOf(trooper))
				|| !MissionSites.hasRole(trooper, MissionSites.ROLE_DOWNED)) {
			return;
		}

		MissionDefinition base = MissionService.baseDefinition(active);

		if (base != null && base.objective() == Objective.YIELD) {
			StoryMissions.onWoundedTended(p, active);
		} else {
			MissionService.trigger(p, Objectives.Trigger.TEND, StoryMissions.TEND);
		}
	}

	/** A player destroyed a construct: the Ruin Sentinels codex page. */
	public static void onConstructKilled(ServerPlayer p, LivingEntity construct, SentinelVariant variant) {
		Discovery.onSentinelMet(p);
	}

	/**
	 * A yielded mission enemy's fate was decided by its owner. Others' yields are ignored.
	 *
	 * <ul>
	 * <li>R2's chief or I1's fence: dealt with, the crate or book can be taken. The M2 yield rules
	 * apply (5.4): a confirmed execution by the decider moves no alignment.</li>
	 * <li>The intercept sergeant: Spare changes nothing, Execute is {@code KILLED_SOLDIER},
	 * Interrogate is {@code CRUELTY} and starts the pull toward an unused chamber.</li>
	 * <li>The I2 sergeant: Spare, Hand over (the escort comes) or Execute is the mission's choice;
	 * fleeing ends it without one.</li>
	 * </ul>
	 */
	public static void onYieldResolved(ServerPlayer p, LivingEntity yielded, YieldOutcome outcome) {
		Active active = MissionService.get(p).active;

		if (active == null || !MissionSites.isMember(yielded) || !active.id.equals(MissionSites.missionOf(yielded))) {
			return;
		}

		MissionDefinition base = MissionService.baseDefinition(active);
		Objective objective = base == null ? null : base.objective();

		if (MissionSites.hasRole(yielded, MissionSites.ROLE_CHIEF)) {
			MissionService.markChiefDone(p);
			return;
		}

		if (!MissionSites.hasRole(yielded, MissionSites.ROLE_SERGEANT) || objective == null) {
			return;
		}

		MissionService.markChiefDone(p);

		if (objective == Objective.YIELD) {
			String choice = StoryMissions.yieldChoice(outcome);

			if (outcome == YieldOutcome.HANDED_OVER) {
				StoryMissions.handOver(p, yielded, active.site);
			} else if (outcome != YieldOutcome.EXECUTED) {
				yielded.addTag(MissionSites.ROLE_PREFIX + MissionSites.ROLE_ESCORT);
			}

			MissionService.trigger(p, Objectives.Trigger.SERGEANT, choice);
			return;
		}

		if (outcome == YieldOutcome.EXECUTED) {
			Identity.contextual(p, ContextAct.KILLED_SOLDIER, yielded);
		} else {
			if (outcome == YieldOutcome.INTERROGATED) {
				Identity.contextual(p, ContextAct.CRUELTY, yielded);
				MissionGuidance.startPull(p);
			}

			// He walks off on his own (W4); the site cleanup leaves him be.
			yielded.addTag(MissionSites.ROLE_PREFIX + MissionSites.ROLE_ESCORT);
		}

		MissionService.trigger(p, Objectives.Trigger.SERGEANT, null);
	}

	private MissionHooks() {
	}
}
