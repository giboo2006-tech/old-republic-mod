package com.oldrepublic.mission;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityRules;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.mission.MissionDefinition.Kind;
import com.oldrepublic.mission.MissionDefinition.Objective;
import com.oldrepublic.mission.network.MissionNetworking;
import com.oldrepublic.mission.network.TrackerHintPayload;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Waystones;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.quest.story.ChapterFiveRules;
import com.oldrepublic.quest.story.ChapterFiveService;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterThreeService;
import com.oldrepublic.quest.story.FactionStanding;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.quest.story.WorldArcs;
import com.oldrepublic.vendor.VendorFeature;
import com.oldrepublic.vendor.VendorRules;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteChecks;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The life of a mission (spec 5.4 to 5.7): accepting, placing its site, arriving, counting the
 * objective, the pending decision or turn-in, completing with its rewards, and abandoning. Every
 * change goes through {@link #get} and {@link #commit} (a sealed copy), and nothing here is trusted
 * from the client.
 *
 * <p>Retry timers, searches and recount strikes live in memory only: after a restart a placing
 * mission simply tries again at once.
 *
 * <p>Milestone 4: a mission's site and board carry their level ({@code Active.siteDimension},
 * {@code boardDimension}), so a planet contract works on its planet; Chapter Four's arc missions
 * are <em>driven</em> ({@link MissionDefinition#driven()}): the story moves their objective through
 * {@link #drive}, {@link #driveReturn} and {@link #completeDriven}, and they pay through
 * {@code WorldArcs.payMission}.
 *
 * <p>Milestone 5: Chapter Five's missions are driven the same way and pay through
 * {@code ChapterFiveService.payMission}; the space objectives ({@code space_run}, {@code space_clear})
 * have no ground site and are flown through {@link SpaceContracts}; a board's space bounty pays its
 * own table outside the daily soft cap.
 */
public final class MissionService {
	/** Ticks between the per-player site checks (placing, arrival, the recount, items). */
	private static final int SITE_CHECK_INTERVAL = 10;
	/** Ticks between safety recounts of a site's fighters. */
	private static final int RECOUNT_INTERVAL = 40;
	/** The owner must be this close to the site for a recount, so the fighters around it are loaded. */
	private static final double RECOUNT_OWNER_RANGE = 32;
	/** Fighters are counted this far around the site (they chase the owner). */
	private static final double RECOUNT_RADIUS = 96;
	/** Recounts in a row that must find fighters missing before they count as gone. */
	private static final int RECOUNT_STRIKES = 2;
	/** Without a camp the mission item is handed over within this many blocks of the site. */
	private static final double ITEM_HAND_RANGE = 8;

	private static final Map<UUID, Placing> PLACING = new HashMap<>();
	private static final Map<UUID, Integer> RECOUNT_STRIKES_BY_PLAYER = new HashMap<>();

	/** In-memory placement state of a player's PLACING mission. */
	private static final class Placing {
		long nextAttempt;
		int failures;
		boolean searching;
	}

	// ---------------------------------------------------------------- state

	/** The live server data. Change it, then {@link #commit} and call this again before any further change. */
	public static MissionData get(ServerPlayer p) {
		return p.getAttachedOrCreate(MissionAttachments.MISSIONS);
	}

	/** Stores a sealed copy and syncs it to the owning client. */
	public static void commit(ServerPlayer p, MissionData d) {
		p.setAttached(MissionAttachments.MISSIONS, d.copy().seal());
	}

	/** The synced data: works on both sides; null on a client that has not received it yet. */
	public static @Nullable MissionData view(Player p) {
		return p.getAttached(MissionAttachments.MISSIONS);
	}

	/** Today's day index, the M2 convention for every daily rule. */
	static int day(ServerPlayer p) {
		return IdentityRules.dayIndex(p.level().getGameTime());
	}

	/** The definition of the active mission's objective: the base contract for a duty, else the mission itself. */
	static @Nullable MissionDefinition baseDefinition(Active active) {
		return MissionLoader.get(active.base);
	}

	// ---------------------------------------------------------------- accepting and abandoning

	/**
	 * Starts a mission. A runtime camp is placed on the following ticks; a worldgen site already
	 * found ({@code found}, for survey and secure offers) is used at once; a duty or a command-started
	 * survey or secure searches first.
	 *
	 * @param issuer the issuing faction's id or {@link MissionDefinition#MENTOR}
	 * @param board  where it was taken (terminal or officer), where a turn-in points back to
	 * @param base   the contract type for a duty, else null
	 * @return false when the player already has an active mission or the definition is unusable
	 */
	static boolean start(ServerPlayer p, MissionDefinition def, String issuer, @Nullable BlockPos board, @Nullable String base, @Nullable SitePiece found) {
		MissionData d = get(p);

		if (d.active != null) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.already_active"));
			return false;
		}

		String baseId = base != null ? base : def.id();
		MissionDefinition baseDef = MissionLoader.get(baseId);

		if (baseDef == null || baseDef.objective() == null) {
			OldRepublicMod.LOGGER.warn("Mission {} has no usable objective ({})", def.id(), baseId);
			return false;
		}

		int day = day(p);
		long seed = Rewards.seed(p, "mission." + def.id(), day);
		Active active = new Active(def.id(), baseId, def.kind(), issuer, day, seed, board);
		active.boardDimension = p.level().dimension();
		active.siteDimension = p.level().dimension();
		SiteKind siteKind = found != null ? found.kind() : null;
		active.target = Objectives.target(baseDef, baseDef.objective(), Progression.get(p).level, siteKind);

		if (def.driven()) {
			// The story moves a driven mission: no site is placed, the objective counts at once.
			active.stage = Stage.ACTIVE;
			active.target = Math.max(1, def.count("target", 1));
		} else if (baseDef.objective().space()) {
			// Milestone 5 (5.9): a space objective has no ground site; it is flown in its orbit.
			active.stage = Stage.ACTIVE;
			active.target = 1;
		}

		if (found != null) {
			setWorldgenSite(p, active, baseDef.objective(), found);
		}

		// W2 starts the flight first, so a refused one (no ship, a full hold) leaves no mission behind.
		if (baseDef.objective().space() && !SpaceContracts.onStarted(p, baseDef)) {
			return false;
		}

		if (def.kind() == Kind.DUTY) {
			d.dutiesToday = d.dutyDay == day ? MissionRules.dutiesOn(d, day) + 1 : 1;
			d.dutyDay = day;
		}

		d.active = active;
		commit(p, d);
		PLACING.remove(p.getUUID());
		RECOUNT_STRIKES_BY_PLAYER.remove(p.getUUID());
		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.accepted", title(active)));
		MissionEffects.update(p, MissionEffects.UPDATE_ACCEPT, active.progress, active.target);
		return true;
	}

	/** Points a survey at the site's heart and a secure contract at its surface guard point. */
	private static void setWorldgenSite(ServerPlayer p, Active active, Objective objective, SitePiece piece) {
		active.site = objective == Objective.SURVEY ? MissionSites.heart(piece) : MissionSites.surfaceGuard(p.level(), piece);
		active.siteKind = piece.kind().id();
		active.stage = Stage.TRAVEL;
	}

	/**
	 * Abandons the active mission: nothing is paid, its people leave the site, a used board offer
	 * stays used for the day, and a story mission can be taken again later.
	 */
	public static boolean abandon(ServerPlayer p) {
		MissionData d = get(p);
		Active active = d.active;

		if (active == null) {
			return false;
		}

		d.active = null;
		commit(p, d);
		forget(p.getUUID());
		cleanupSite(p, active);
		SpaceContracts.onEnded(p, active);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.abandoned", title(active)));
		return true;
	}

	// ---------------------------------------------------------------- objective progress

	/** One objective trigger for the player's active mission (see {@link Objectives#advance}). */
	static void trigger(ServerPlayer p, Objectives.Trigger trigger, @Nullable String choice) {
		MissionData d = get(p);
		Active active = d.active;
		MissionDefinition base = active == null ? null : baseDefinition(active);

		if (active == null || base == null || base.objective() == null || base.driven()) {
			return;
		}

		Objective objective = base.objective();
		Objectives.Step step = Objectives.advance(objective, trigger, active.stage, active.progress, active.target, active.extra,
				Objectives.extraTarget(base, objective), active.has(MissionData.FLAG_CHIEF_DONE));
		boolean changed = step.stage() != active.stage || step.progress() != active.progress || step.extra() != active.extra;

		if (!changed && !step.complete()) {
			return;
		}

		active.stage = step.stage();
		active.progress = step.progress();
		active.extra = step.extra();
		commit(p, d);

		if (step.complete()) {
			complete(p, objective == Objective.RESCUE && choice == null ? StoryMissions.TEND : choice);
		} else {
			feedback(p, active, base, objective);
		}
	}

	/** Marks the chief, fence or sergeant as dealt with (the crate unlocks; a decided sergeant ends the fight). */
	static void markChiefDone(ServerPlayer p) {
		MissionData d = get(p);

		if (d.active != null && !d.active.has(MissionData.FLAG_CHIEF_DONE)) {
			d.active.set(MissionData.FLAG_CHIEF_DONE);
			commit(p, d);
		}
	}

	/** Action bar progress ("Sentinels 2/3") and the {@code mission_update} chime. */
	private static void feedback(ServerPlayer p, Active active, MissionDefinition base, Objective objective) {
		Component line = Component.translatable(MissionRules.objectiveKey(active.base, active.stage));

		if (active.target > 1 && active.stage != Stage.RETURN) {
			int shown = objective == Objective.RESCUE && active.stage == Stage.DECIDE ? active.extra : active.progress;
			int goal = objective == Objective.RESCUE && active.stage == Stage.DECIDE ? Objectives.extraTarget(base, objective) : active.target;
			String key = objective == Objective.RESCUE && active.stage == Stage.DECIDE ? "mission.oldrepublic.counter.wounded"
					: Objectives.counterKey(objective, SiteKind.byId(active.siteKind));
			line = Component.translatable("message.oldrepublic.mission.progress", Component.translatable(key), shown, goal);
		}

		p.sendOverlayMessage(line);
		MissionEffects.update(p, MissionEffects.UPDATE_PROGRESS, active.progress, active.target);
	}

	// ---------------------------------------------------------------- completion

	/**
	 * Completes the active mission and pays it: a story mission through {@link StoryMissions} with
	 * {@code choice} (null when none was owed or none was made), a contract or duty by the contract
	 * rules (5.5). Then the site is cleaned up, the banner shows and Chapter Three hears of it.
	 */
	static void complete(ServerPlayer p, @Nullable String choice) {
		complete(p, choice, day(p));
	}

	/**
	 * {@link #complete(ServerPlayer, String)} with the day the contract counts toward given
	 * explicitly (the game tests pass it, so no daily rule reads world time there).
	 *
	 * @param day the in-game day index ({@code IdentityRules.dayIndex(level.getGameTime())})
	 */
	public static void complete(ServerPlayer p, @Nullable String choice, int day) {
		MissionData d = get(p);
		Active active = d.active;

		if (active == null) {
			return;
		}

		MissionDefinition def = MissionLoader.get(active.id);
		MissionBalance b = MissionFeature.balance();
		d.active = null;
		boolean fullPay = true;
		boolean bounty = SpaceContracts.isBounty(def);

		if (active.kind == Kind.STORY) {
			d.markStoryDone(active.id);
		} else if (!bounty) {
			// A space bounty is outside the daily soft cap (MILESTONE_5.md 5.8).
			MissionRules.rollDay(d, day);
			fullPay = MissionRules.fullPay(d, b);
			d.contractsToday++;
			String key = MissionRules.ownSideKey(active.kind, active.issuer);

			if (d.ownSide.containsKey(key) || d.ownSide.size() < MissionData.MAX_OWN_SIDE) {
				d.ownSide.merge(key, 1, Integer::sum);
			}
		}

		commit(p, d);
		forget(p.getUUID());
		cleanupSite(p, active);
		SpaceContracts.onEnded(p, active);

		if (def == null) {
			OldRepublicMod.LOGGER.warn("Mission {} of {} completed without a definition; nothing paid", active.id, p.getName().getString());
			return;
		}

		if (active.kind == Kind.STORY && ChapterFourRules.isChapterMission(def.id())) {
			// Chapter Four's missions pay by its own table (8.2, 8.4) and move the arc.
			WorldArcs.payMission(p, def);
		} else if (active.kind == Kind.STORY && ChapterFiveRules.isChapterMission(def.id())) {
			// Chapter Five's missions pay by its own table (MILESTONE_5.md 6.3, 6.4) and move the chapter.
			ChapterFiveService.payMission(p, def);
		} else if (active.kind == Kind.STORY) {
			StoryMissions.pay(p, def, active, choice);
			ChapterThreeService.addLead(p, "mission:" + def.id());
		} else if (bounty) {
			SpaceContracts.payBounty(p, def, active);
		} else {
			payContract(p, def, active, fullPay);
			FactionStanding.onContractDone(p, PlanetContracts.planetOf(def));
			ChapterThreeService.onContractCompleted(p);
		}

		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.complete", title(active)));
		EffectEvents.toPlayer(p, QuestEffects.LESSON_COMPLETE, p.position(), null, p.getId(), Identity.path(p).ordinal(), QuestEffects.LESSON_MISSION);
	}

	/**
	 * Contract and duty pay (5.5): XP min(900, 250 + 40 x level), times 0.25 past the day's full-pay
	 * count with nothing else; in full, the issuer's +25 (and a Jedi's Order share of Republic gains)
	 * with one commendation or the Frontier loot, or for a duty mentor trust and own-faction standing.
	 */
	private static void payContract(ServerPlayer p, MissionDefinition def, Active active, boolean fullPay) {
		MissionBalance b = MissionFeature.balance();
		int level = Progression.get(p).level;
		int xp = MissionRules.paidXp(MissionRules.contractXp(level, b), fullPay, b);
		Progression.addXp(p, xp, "mission");

		if (!fullPay) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.over_cap", b.fullPayPerDay));
			return;
		}

		String source = "mission." + active.id;
		// Milestone 4 (7.7): contracts pay credits at full pay; capped contracts pay none (above).
		int credits = active.kind == Kind.CONTRACT ? VendorRules.contractCredits(level, VendorFeature.balance()) : 0;

		if (credits > 0) {
			Credits.give(p, credits);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.credits", credits));
		}

		if (def.isPlanetContract()) {
			PlanetContracts.pay(p, def, active, source);
			return;
		}

		if (active.kind == Kind.DUTY) {
			Path path = Identity.path(p);
			String mentor = IdentityRules.mentorOf(path);
			Faction own = IdentityRules.factionOf(path);

			if (mentor != null) {
				Identity.addTrust(p, mentor, b.dutyTrust, "mission.oldrepublic.reason.duty");
			}

			if (own != null) {
				Identity.changeReputation(p, own, b.dutyRep, source, false);
			}

			return;
		}

		Faction issuer = Faction.byId(active.issuer);

		if (issuer == null) {
			return;
		}

		Identity.changeReputation(p, issuer, b.contractRep, source, false);
		payJediShare(p, issuer, b.contractRep, source);
		MissionDefinition base = baseDefinition(active);
		Identifier loot = base != null ? base.loot() : def.loot();

		if (issuer == Faction.FRONTIER && loot != null) {
			Rewards.giveLoot(p, ResourceKey.create(Registries.LOOT_TABLE, loot), active.seed);
		} else {
			Rewards.give(p, Commendations.of(issuer, b.contractCommendations));
		}
	}

	/** For a Jedi, a Republic gain from Republic work also pays half as much Jedi Order standing (5.4). */
	static void payJediShare(ServerPlayer p, Faction faction, int gain, String source) {
		if (faction != Faction.REPUBLIC) {
			return;
		}

		int share = MissionRules.jediShare(Identity.path(p), gain, MissionFeature.balance());

		if (share > 0) {
			Identity.changeReputation(p, Faction.JEDI_ORDER, share, source + ".jedi_share", false);
		}
	}

	/** Its people leave the site (when it is loaded) and untended wounded start to bleed out. */
	private static void cleanupSite(ServerPlayer p, Active active) {
		if (active.site == null) {
			return;
		}

		ServerLevel level = p.level().getServer().getLevel(active.siteDimension);

		if (level == null) {
			return;
		}

		long bleedAt = level.getGameTime() + MissionFeature.balance().downedBleedTicks;
		MissionSites.cleanup(level, p.getUUID(), active.id, active.site, bleedAt);
		PlanetContracts.cleanup(p, active);
	}

	// ---------------------------------------------------------------- the tick

	/** Every server tick: each player's active mission, the tracker, escorts and cameos. */
	static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			MissionData d = view(p);
			Active active = d == null ? null : d.active;

			if (active != null) {
				tickActive(p, active);
			}

			Cameos.tick(p);
		}

		StoryMissions.tickEscorts(server);
		MissionBoard.tick(server);
		PlanetContracts.tick(server);
	}

	private static void tickActive(ServerPlayer p, Active active) {
		long now = p.level().getGameTime();
		MissionBalance b = MissionFeature.balance();
		long phase = now + Math.floorMod(p.getUUID().hashCode(), SITE_CHECK_INTERVAL);

		if (now % Math.max(1, b.trackerHintTicks) == 0) {
			MissionNetworking.sendHint(p, hint(p, active));
		}

		if (phase % SITE_CHECK_INTERVAL != 0) {
			return;
		}

		MissionDefinition def = MissionLoader.get(active.id);
		MissionDefinition base = baseDefinition(active);

		if (def == null || base == null || base.objective() == null) {
			if (!MissionLoader.all().isEmpty()) {
				p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.gone", active.id));
				abandon(p);
			}

			return;
		}

		if (def.driven() || base.objective().space()) {
			// The story ticks its own missions (Chapter Four's WorldArcs, Chapter Five's service); a space
			// objective is flown in its orbit and reported by W2 (MILESTONE_5.md 5.9).
			return;
		}

		switch (active.stage) {
			case PLACING -> place(p, def, base, now);
			case TRAVEL -> checkArrival(p);
			case ACTIVE, DECIDE, RETURN -> {
				if (phase % RECOUNT_INTERVAL == 0) {
					recount(p, active, base);
				}

				handItemWithoutCamp(p, base);
				Cameos.tickChief(p);
			}
		}
	}

	// ---------------------------------------------------------------- placing

	private static void place(ServerPlayer p, MissionDefinition def, MissionDefinition base, long now) {
		boolean planet = base.isPlanetContract();
		Active placing = get(p).active;
		// A planet contract is placed on its planet (its board's level); every other mission on the home world.
		ResourceKey<Level> where = planet && placing != null ? placing.boardDimension : Level.OVERWORLD;

		if (!p.level().dimension().equals(where)) {
			return;
		}

		Placing state = PLACING.computeIfAbsent(p.getUUID(), uuid -> new Placing());

		if (state.searching || now < state.nextAttempt) {
			return;
		}

		MissionBalance b = MissionFeature.balance();

		if (planet) {
			state.searching = true;
			PlanetContracts.place(p, base, site -> onPlanetSite(p, def, site));
			return;
		}

		if (base.site() != null) {
			BlockPos stand = MissionSites.place(p, base, p.getUUID());

			if (stand == null && ++state.failures >= b.siteForwardFallbackAfter) {
				stand = MissionSites.placeForward(p, base, p.getUUID());
			}

			if (stand == null) {
				state.nextAttempt = now + b.siteRetryTicks;
				return;
			}

			setRuntimeSite(p, stand);
			return;
		}

		state.searching = true;
		Objective objective = base.objective();
		int radius = objective == Objective.SURVEY ? b.surveyRadius : b.secureRadius;
		MissionData d = get(p);
		List<BlockPos> surveyed = List.copyOf(d.surveyed);
		MissionSites.nearest(p, MissionRules.WORLDGEN_KINDS, radius,
				objective == Objective.SURVEY ? piece -> surveyed.contains(MissionSites.heart(piece)) : null,
				piece -> onSiteFound(p, def, piece));
	}

	/** A worldgen site search came back for a placing mission: use it, try the duty's next type, or give up. */
	private static void onSiteFound(ServerPlayer p, MissionDefinition def, @Nullable SitePiece piece) {
		Placing state = PLACING.get(p.getUUID());

		if (state != null) {
			state.searching = false;
		}

		MissionData d = get(p);
		Active active = d.active;

		if (active == null || active.stage != Stage.PLACING || !active.id.equals(def.id())) {
			return;
		}

		MissionDefinition base = baseDefinition(active);

		if (piece != null && base != null && base.objective() != null) {
			active.target = Objectives.target(base, base.objective(), Progression.get(p).level, piece.kind());
			setWorldgenSite(p, active, base.objective(), piece);
			commit(p, d);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.site_found"));
			MissionEffects.update(p, MissionEffects.UPDATE_PROGRESS, active.progress, active.target);
			return;
		}

		String next = def.kind() == Kind.DUTY ? MissionRules.nextDutyType(def.base(), MissionRules.dutyOrder(active.startedDay, p.getUUID()), active.base) : null;
		MissionDefinition nextBase = next == null ? null : MissionLoader.get(next);

		if (nextBase != null && nextBase.objective() != null) {
			active.base = next;
			active.target = Objectives.target(nextBase, nextBase.objective(), Progression.get(p).level, null);
			commit(p, d);
			return;
		}

		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.no_site"));
		abandon(p);
	}

	private static void setRuntimeSite(ServerPlayer p, BlockPos stand) {
		MissionData d = get(p);

		if (d.active == null) {
			return;
		}

		d.active.site = stand.immutable();
		d.active.siteKind = SiteKind.MISSION_SITE.id();
		d.active.stage = Stage.TRAVEL;
		commit(p, d);
		PLACING.remove(p.getUUID());
		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.site_found"));
	}

	/** A planet contract's site search came back: travel to it, or give up when there is none near. */
	private static void onPlanetSite(ServerPlayer p, MissionDefinition def, PlanetContracts.@Nullable Site site) {
		Placing state = PLACING.get(p.getUUID());

		if (state != null) {
			state.searching = false;
		}

		MissionData d = get(p);
		Active active = d.active;

		if (active == null || active.stage != Stage.PLACING || !active.id.equals(def.id())) {
			return;
		}

		if (site == null) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.no_site"));
			abandon(p);
			return;
		}

		setSite(active, site.pos());
		active.siteKind = site.kind() == null ? "" : site.kind().id();
		active.stage = Stage.TRAVEL;
		commit(p, d);
		PLACING.remove(p.getUUID());
		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.site_found"));
		PlanetContracts.onSiteSet(p, def);
	}

	/** Sets the active mission's site and its level (not committed). */
	static void setSite(Active active, GlobalPos site) {
		active.site = site.pos().immutable();
		active.siteDimension = site.dimension();
	}

	// ---------------------------------------------------------------- arriving

	/** Arrives when the owner is within {@code siteSpawnDistance} of a travelling mission's site and its entities are loaded. */
	static void checkArrival(ServerPlayer p) {
		MissionData d = get(p);
		Active active = d.active;
		MissionDefinition base = active == null ? null : baseDefinition(active);

		if (active == null || base == null || base.objective() == null || active.stage != Stage.TRAVEL || active.site == null
				|| !p.level().dimension().equals(active.siteDimension) || base.driven()) {
			return;
		}

		ServerLevel level = p.level();
		double reach = MissionFeature.balance().siteSpawnDistance;

		if (p.position().distanceToSqr(Vec3.atCenterOf(active.site)) > reach * reach || !SiteChecks.entitiesReady(level, active.site, 1)) {
			return;
		}

		Objective objective = base.objective();
		boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
		Objectives.Arrival arrival = Objectives.resolveOnArrival(objective, active.target, peaceful);
		active.stage = arrival.stage();
		active.progress = arrival.progress();
		active.set(MissionData.FLAG_SPAWNED);

		if (peaceful) {
			active.set(MissionData.FLAG_PEACEFUL);
		}

		if (arrival.guardsDone()) {
			active.set(MissionData.FLAG_CHIEF_DONE);
		}

		SitePiece piece = SiteKind.MISSION_SITE.id().equals(active.siteKind) ? MissionSites.piece(level, active.site) : null;
		int fighters = Objectives.spawn(p, base, active, objective, arrival, piece);
		boolean counted = arrival.spawnFighters() && objective != Objective.RECOVER && objective != Objective.SURVEY;

		if (counted && fighters < active.target) {
			if (objective != Objective.YIELD) {
				OldRepublicMod.LOGGER.warn("Mission {}: {} of {} fighters spawned; the objective counts those", active.id, fighters, active.target);
			}

			active.target = fighters;
			active.progress = Math.min(active.progress, fighters);
		}

		if (objective == Objective.RESCUE && active.progress >= active.target) {
			active.stage = Stage.DECIDE;
		}

		commit(p, d);
		p.sendSystemMessage(Component.translatable(MissionRules.objectiveKey(active.base, active.stage)));
		MissionEffects.update(p, MissionEffects.UPDATE_PROGRESS, active.progress, active.target);

		boolean nothingToFight = counted && fighters == 0 && (objective == Objective.CLEAR || objective == Objective.SECURE);

		if (arrival.complete() || nothingToFight) {
			complete(p, null);
		}
	}

	// ---------------------------------------------------------------- safety recount

	/**
	 * Fighters that left the world without a death event (Peaceful removing a crew mid-fight, a
	 * despawn) still count: while the owner stands near the loaded site, fighters missing from two
	 * recounts in a row are counted as defeated, and a chief or sergeant missing is dealt with.
	 */
	private static void recount(ServerPlayer p, Active active, MissionDefinition base) {
		Objective objective = base.objective();

		if (objective == null || !objective.combat() || !active.has(MissionData.FLAG_SPAWNED) || active.site == null
				|| !p.level().dimension().equals(active.siteDimension) || p.position().distanceToSqr(Vec3.atCenterOf(active.site)) > RECOUNT_OWNER_RANGE * RECOUNT_OWNER_RANGE
				|| !SiteChecks.entitiesReady(p.level(), active.site, 2)) {
			RECOUNT_STRIKES_BY_PLAYER.remove(p.getUUID());
			return;
		}

		ServerLevel level = p.level();
		UUID owner = p.getUUID();
		int enemies = MissionSites.members(level, owner, active.id, active.site, RECOUNT_RADIUS,
				e -> MissionSites.hasRole(e, MissionSites.ROLE_ENEMY) && e.isAlive()).size();
		boolean leaderAlive = !MissionSites.members(level, owner, active.id, active.site, RECOUNT_RADIUS,
				e -> (MissionSites.hasRole(e, MissionSites.ROLE_CHIEF) || MissionSites.hasRole(e, MissionSites.ROLE_SERGEANT)) && e.isAlive()).isEmpty();
		boolean counting = active.stage == Stage.ACTIVE && objective != Objective.RECOVER;
		int missing = counting ? Math.max(0, active.target - active.progress - enemies) : 0;
		boolean leaderMissing = !leaderAlive && !active.has(MissionData.FLAG_CHIEF_DONE) && !Cameos.chiefWaitsAtPost(p, base)
				&& (objective == Objective.RECOVER || objective == Objective.INTERCEPT || objective == Objective.YIELD);

		if (missing == 0 && !leaderMissing) {
			RECOUNT_STRIKES_BY_PLAYER.remove(owner);
			return;
		}

		if (RECOUNT_STRIKES_BY_PLAYER.merge(owner, 1, Integer::sum) < RECOUNT_STRIKES) {
			return;
		}

		RECOUNT_STRIKES_BY_PLAYER.remove(owner);
		OldRepublicMod.LOGGER.debug("Mission {} of {}: {} fighters missing, leader missing {}", active.id, p.getName().getString(), missing, leaderMissing);

		for (int i = 0; i < missing; i++) {
			trigger(p, Objectives.Trigger.KILL, null);
		}

		if (leaderMissing) {
			markChiefDone(p);

			if (objective != Objective.RECOVER) {
				trigger(p, Objectives.Trigger.SERGEANT, null);
			}
		}
	}

	// ---------------------------------------------------------------- the mission item

	/**
	 * Without a camp (structures off, or a camp that could not be stamped) there is no crate or
	 * book to use: the item is handed over at the site once its chief or fence is dealt with, and
	 * handed again if it was lost (12.2).
	 */
	private static void handItemWithoutCamp(ServerPlayer p, MissionDefinition base) {
		MissionData d = get(p);
		Active active = d.active;

		if (active == null || base.objective() != Objective.RECOVER || active.site == null || !active.has(MissionData.FLAG_SPAWNED)
				|| !p.level().dimension().equals(active.siteDimension)
				|| p.position().distanceToSqr(Vec3.atCenterOf(active.site)) > ITEM_HAND_RANGE * ITEM_HAND_RANGE
				|| MissionSites.piece(p.level(), active.site) != null) {
			return;
		}

		issueItem(p, base);
	}

	/**
	 * Gives the mission item when it is due: the chief or fence dealt with while the objective is
	 * active (the objective moves on to the turn-in), or a lost one again on the way back.
	 *
	 * @return whether an item was given
	 */
	static boolean issueItem(ServerPlayer p, MissionDefinition base) {
		MissionData d = get(p);
		Active active = d.active;
		Item item = MissionItems.byId(base.item());

		if (active == null || item == null) {
			return false;
		}

		if (active.stage == Stage.ACTIVE && active.has(MissionData.FLAG_CHIEF_DONE)) {
			Rewards.give(p, new ItemStack(item));
			active.set(MissionData.FLAG_ITEM_ISSUED);
			commit(p, d);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.item_taken", item.getName(new ItemStack(item))));
			trigger(p, Objectives.Trigger.ITEM, null);
			return true;
		}

		if (active.stage == Stage.RETURN && !MissionItems.carries(p, item)) {
			Rewards.give(p, new ItemStack(item));
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.item_reissued", item.getName(new ItemStack(item))));
			return true;
		}

		return false;
	}

	// ---------------------------------------------------------------- tracker

	/** Where the tracker and the Calling point: the site while travelling or there, the board for a turn-in; null while placing or elsewhere. */
	public static @Nullable Vec3 target(ServerPlayer p, Active active) {
		GlobalPos pos = targetGlobal(active);
		return pos == null || !pos.dimension().equals(p.level().dimension()) ? null : Vec3.atCenterOf(pos.pos());
	}

	/** Where the active mission points, with its level (the Calling's off-world cue uses it), or null while placing. */
	public static @Nullable GlobalPos targetGlobal(Active active) {
		return switch (active.stage) {
			case PLACING -> null;
			case TRAVEL, ACTIVE, DECIDE -> active.siteGlobal();
			case RETURN -> active.boardGlobal();
		};
	}

	/** The tracker hint for the active mission: direction, band and the counter shown (R1's wounded while deciding). */
	static TrackerHintPayload hint(ServerPlayer p, Active active) {
		Vec3 target = target(p, active);
		MissionDefinition base = baseDefinition(active);
		boolean wounded = base != null && base.objective() == Objective.RESCUE && active.stage == Stage.DECIDE;
		int progress = wounded ? active.extra : active.progress;
		int goal = wounded ? Objectives.extraTarget(base, Objective.RESCUE) : active.target;

		if (target == null) {
			return new TrackerHintPayload(0f, MissionRules.Band.NONE.ordinal(), progress, goal);
		}

		double distance = Math.sqrt(Math.pow(target.x - p.getX(), 2) + Math.pow(target.z - p.getZ(), 2));
		return new TrackerHintPayload(MissionRules.yaw(p.position(), target), MissionRules.band(distance, MissionFeature.balance()).ordinal(), progress, goal);
	}

	/** The mission's title component. */
	static Component title(Active active) {
		return Component.translatable("mission.oldrepublic." + active.id + ".title");
	}

	// ---------------------------------------------------------------- entities

	/**
	 * A mission entity loaded from disk whose mission is over for its (online) owner falls back or
	 * goes, so finished camps empty out even when they were unloaded at the end. Cameos never stay.
	 */
	static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!MissionSites.isMember(entity) || MissionSites.hasRole(entity, MissionSites.ROLE_ESCORT) || MissionSites.hasRole(entity, MissionSites.ROLE_DOWNED)) {
			return;
		}

		if (MissionSites.hasRole(entity, MissionSites.ROLE_CAMEO)) {
			entity.discard();
			return;
		}

		UUID ownerId = StoryTags.owner(entity);
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);

		if (owner == null) {
			return;
		}

		MissionData d = view(owner);
		String missionId = MissionSites.missionOf(entity);

		if (d != null && (d.active == null || !d.active.id.equals(missionId))) {
			if (entity instanceof TrooperEntity trooper) {
				MissionSites.walkOff(trooper, owner.position());
			} else {
				entity.discard();
			}
		}
	}

	/** Forgets a player's in-memory mission state (leave, respawn, finished mission). */
	static void forget(UUID player) {
		PLACING.remove(player);
		RECOUNT_STRIKES_BY_PLAYER.remove(player);
		PlanetContracts.forget(player);
	}

	/** Forgets all in-memory state (server stopped). */
	static void clear() {
		PLACING.clear();
		RECOUNT_STRIKES_BY_PLAYER.clear();
		PlanetContracts.clear();
	}

	// ---------------------------------------------------------------- driven story missions (Chapter Four)

	/** The active mission's id, or null. */
	public static @Nullable String activeId(ServerPlayer p) {
		Active active = get(p).active;
		return active == null ? null : active.id;
	}

	/**
	 * Starts a story mission from a story beat (a hub host's dialogue, the Compass), checked by the
	 * same rules as a giver's start ({@link StoryMissions#blocker}).
	 *
	 * @return whether it started
	 */
	public static boolean startStory(ServerPlayer p, String id, @Nullable Entity giver) {
		MissionDefinition def = MissionLoader.get(id);

		if (def == null) {
			OldRepublicMod.LOGGER.warn("Story mission {} is not loaded; {} cannot start it", id, p.getName().getString());
			return false;
		}

		return StoryMissions.start(p, def, giver);
	}

	/** The active driven mission's site, or null (none set yet, or no driven mission). */
	public static @Nullable GlobalPos drivenSite(ServerPlayer p) {
		Active active = activeDriven(p, null);
		return active == null ? null : active.siteGlobal();
	}

	/** The active driven mission's progress, 0 without one. */
	public static int drivenProgress(ServerPlayer p) {
		Active active = activeDriven(p, null);
		return active == null ? 0 : active.progress;
	}

	/**
	 * Moves the active driven mission {@code id}: its site (null clears it, so the story can point at
	 * the next one), its progress and its goal. The tracker and the Calling follow the site.
	 */
	public static void drive(ServerPlayer p, String id, @Nullable GlobalPos site, int progress, int target) {
		MissionData d = get(p);
		Active active = d.active;

		if (active == null || !active.id.equals(id) || !isDriven(active)) {
			return;
		}

		if (site != null) {
			setSite(active, site);
		} else {
			active.site = null;
		}

		active.target = Math.max(1, target);
		active.progress = Math.max(0, Math.min(active.target, progress));

		if (active.stage != Stage.RETURN) {
			active.stage = Stage.ACTIVE;
		}

		commit(p, d);
		MissionEffects.update(p, MissionEffects.UPDATE_PROGRESS, active.progress, active.target);
	}

	/** The driven mission's objective is done and it waits for its turn-in at the giver (the tracker points at the board). */
	public static void driveReturn(ServerPlayer p, String id) {
		MissionData d = get(p);
		Active active = d.active;

		if (active == null || !active.id.equals(id) || !isDriven(active) || active.stage == Stage.RETURN) {
			return;
		}

		active.stage = Stage.RETURN;
		active.progress = Math.max(1, active.target);
		commit(p, d);
		p.sendSystemMessage(Component.translatable(MissionRules.objectiveKey(active.base, Stage.RETURN)));
		MissionEffects.update(p, MissionEffects.UPDATE_PROGRESS, active.progress, active.target);
	}

	/** Completes the active driven mission {@code id} and pays it. */
	public static void completeDriven(ServerPlayer p, String id) {
		Active active = get(p).active;

		if (active != null && active.id.equals(id) && isDriven(active)) {
			complete(p, null);
		}
	}

	/**
	 * Points the active driven mission {@code id} at the nearest site of {@code kinds} in the
	 * player's level that holds no surveyed position (a cleansed waystone) and is not a clean
	 * waystone (nothing to cleanse there). Does nothing while a site is set; the search answers later.
	 */
	public static void pointAtNearest(ServerPlayer p, String id, List<SiteKind> kinds, int radius) {
		Active active = activeDriven(p, id);

		if (active == null || active.site != null) {
			return;
		}

		List<BlockPos> surveyed = List.copyOf(get(p).surveyed);
		MissionSites.nearest(p, kinds, radius, piece -> cleanWaystone(piece) || surveyed.stream().anyMatch(piece.box().inflatedBy(4)::isInside), piece -> {
			Active still = activeDriven(p, id);

			if (piece != null && still != null && still.site == null) {
				drive(p, id, GlobalPos.of(p.level().dimension(), MissionSites.heart(piece)), still.progress, still.target);
			}
		});
	}

	/** A waystone whose stone is clean (decided by its position, as worldgen does): there is nothing to cleanse. */
	private static boolean cleanWaystone(SitePiece piece) {
		if (piece.kind() != SiteKind.WAYSTONE) {
			return false;
		}

		BlockPos stone = SiteAnchors.find(piece, SiteAnchors.Anchor.STONE);
		return stone == null || !Waystones.corruptedAt(stone);
	}

	/** Records a surveyed (cleansed) position, so searches skip its site. */
	public static void markSurveyed(ServerPlayer p, BlockPos pos) {
		MissionData d = get(p);
		d.markSurveyed(pos);
		commit(p, d);
	}

	private static @Nullable Active activeDriven(ServerPlayer p, @Nullable String id) {
		Active active = get(p).active;
		return active != null && (id == null || active.id.equals(id)) && isDriven(active) ? active : null;
	}

	private static boolean isDriven(Active active) {
		MissionDefinition def = MissionLoader.get(active.id);
		return def != null && def.driven();
	}

	private MissionService() {
	}
}
