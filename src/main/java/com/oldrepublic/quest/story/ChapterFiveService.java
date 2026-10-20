package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.mission.SpaceContracts;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.travel.FlightBalance;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Chapter Five, "The Long Hymn" (spec MILESTONE_5.md 6), and the two Milestone 5 factions' story
 * side (7): the stage machine and the entry point of every Milestone 5 {@code QuestHooks} call. Each
 * part of the chapter lives in its own class ({@link TheCall}, {@link SaltgateWays},
 * {@link CountingHouse}, {@link TyrvannArc}, {@link UlvaineChoice}); this class moves the stage, runs
 * each stage's entry logic, ticks the chapter and holds the small helpers the parts share. Stages
 * only move forward, and every reward is guarded by a stage change or a one-time flag
 * ({@link ChapterFiveRules}). Server only. Owned by W4 story and factions; the hook signatures are
 * Phase 0's and stay as they are.
 */
public final class ChapterFiveService {
	/** Game time before which a failed placement, search or spawn is not tried again, per player and task. */
	private static final Map<String, Long> NEXT_TRY = new HashMap<>();
	/** Ulvaine's boss id (12.2); her echo's id starts with it but pays through W8. */
	static final String ULVAINE = "ulvaine";

	public static ChapterFiveBalance balance() {
		return BalanceConfig.section("chapter_five", ChapterFiveBalance.class, ChapterFiveBalance::new);
	}

	// ---------------------------------------------------------------- the stage machine

	/**
	 * Moves the chapter to {@code stage} if it is later than the current one and runs that stage's
	 * entry logic (the story uses it for every step; commands and tests use it to jump).
	 *
	 * @return whether the stage changed
	 */
	public static boolean enterStage(ServerPlayer p, ChapterFiveStage stage) {
		StoryData data = Story.get(p);

		if (!ChapterFiveRules.advance(data, stage, StoryService.now(p))) {
			return false;
		}

		Story.commit(p, data);
		OldRepublicMod.LOGGER.debug("Chapter Five of {} entered {}", p.getName().getString(), stage);
		onEnter(p, stage);
		return true;
	}

	private static void onEnter(ServerPlayer p, ChapterFiveStage stage) {
		switch (stage) {
			case WAITING -> {
				StoryData data = Story.get(p);
				data.flags.remove(ChapterFiveRules.CALL_DUE);
				Story.commit(p, data);
			}
			case CALLED -> TheCall.onCalled(p);
			case SALTGATE -> SaltgateWays.onSaltgate(p);
			case THE_FACTOR -> CountingHouse.onTheFactor(p);
			case ARMED -> TyrvannArc.onArmed(p);
			case BLOCKADE -> TyrvannArc.onBlockade(p);
			case TYRVANN -> TyrvannArc.onTyrvann(p);
			case VERSES -> TyrvannArc.onVerses(p);
			case THE_HYMNAL -> TyrvannArc.onHymnal(p);
			case AFTERMATH -> UlvaineChoice.onAftermath(p);
			default -> {
			}
		}
	}

	/**
	 * Begins Chapter Five once Chapter Four is done: also how M4 saves at Chapter Four COMPLETE enter
	 * WAITING on their first tick after joining (6.9). Public for tests.
	 *
	 * @return whether the chapter began
	 */
	public static boolean checkWaiting(ServerPlayer p) {
		return ChapterFiveRules.shouldWait(Story.get(p)) && enterStage(p, ChapterFiveStage.WAITING);
	}

	/**
	 * WAITING's check, once, now (the slow tick runs it every second): the call plays when it is due
	 * and the moment is safe (6.1, {@link StoryMentors#safeMoment}). Public for tests.
	 *
	 * @return whether the chapter is at CALLED or later
	 */
	public static boolean checkCall(ServerPlayer p) {
		if (Story.get(p).chapter5.stage() == ChapterFiveStage.WAITING) {
			TheCall.check(p);
		}

		return ChapterFive.atLeast(p, ChapterFiveStage.CALLED);
	}

	/**
	 * Gives a save the unlocks its stage has earned (6.1, 6.9): the helm and Saltgate from CALLED, the
	 * holdfast from ARMED. The slow tick runs it for every player in the chapter; public for tests.
	 */
	public static void repairUnlocks(ServerPlayer p) {
		TheCall.repair(p);
	}

	// ---------------------------------------------------------------- ticking

	/** Every tick for each living player (from {@link StoryService#tick}): the chapter's checks run every {@link StoryService#SLOW_TICK}. */
	static void tick(ServerPlayer p) {
		if ((p.tickCount + p.getId()) % StoryService.SLOW_TICK == 0) {
			slowTick(p);
		}
	}

	/** The chapter's waits, its repairs for old saves, its places and its fights. */
	private static void slowTick(ServerPlayer p) {
		checkWaiting(p);
		StoryData data = Story.get(p);
		ChapterFiveStage stage = data.chapter5.stage();

		if (stage == ChapterFiveStage.NOT_STARTED) {
			return;
		}

		if (stage == ChapterFiveStage.WAITING) {
			TheCall.check(p);
			return;
		}

		TheCall.repair(p);
		TyrvannArc.checkWorlds(p);
		SaltgateWays.tick(p);

		switch (stage) {
			case CALLED -> SaltgateWays.checkArrival(p);
			case THE_FACTOR -> CountingHouse.tick(p);
			case ARMED -> TyrvannArc.checkCannons(p);
			case TYRVANN -> TyrvannArc.checkLanded(p);
			case VERSES -> TyrvannArc.checkVerses(p);
			case AFTERMATH -> UlvaineChoice.tickAftermath(p);
			default -> {
			}
		}

		if (stage.ordinal() >= ChapterFiveStage.VERSES.ordinal()) {
			TyrvannArc.placeSites(p);
		}
	}

	/** Whether a task that failed earlier may be tried again now. */
	static boolean due(ServerPlayer p, String task) {
		Long next = NEXT_TRY.get(key(p, task));
		return next == null || StoryService.now(p) >= next;
	}

	/** Holds a task off for {@code ticks}. */
	static void retryLater(ServerPlayer p, String task, long ticks) {
		NEXT_TRY.put(key(p, task), StoryService.now(p) + ticks);
	}

	private static String key(ServerPlayer p, String task) {
		return p.getUUID() + ":" + task;
	}

	/** The world day (the overworld clock, so planets count the same days; MILESTONE_4.md 3.6). */
	static long day(ServerPlayer p) {
		return Clocks.day(p.level().getServer());
	}

	static boolean peaceful(ServerPlayer p) {
		return p.level().getDifficulty() == Difficulty.PEACEFUL;
	}

	/** Whether free flight is on ({@code flight.enabled}, 14.4): off, the blockade is the scripted pass and the kajidic's way is the debt alone. */
	static boolean flight() {
		return FlightBalance.get().enabled;
	}

	// ---------------------------------------------------------------- shared helpers

	/** Chapter Five XP (source {@code chapter_five}). */
	static void xp(ServerPlayer p, int amount) {
		if (amount > 0) {
			Progression.addXp(p, amount, "chapter_five");
		}
	}

	/** Sets the one-time flag; false when it was set before (the reward must not be given again). */
	static boolean once(ServerPlayer p, String flag) {
		StoryData data = Story.get(p);

		if (!data.flags.add(flag)) {
			return false;
		}

		Story.commit(p, data);
		return true;
	}

	/**
	 * The path's mentor speaks a Chapter Five line ({@code npc.oldrepublic.<jedi|sith>.c5.<line>}); an
	 * Independent hears Tessaly Arn's echo through the Compass ({@code npc.oldrepublic.tessaly.c5.<line>}).
	 * The path is read now, so a player who Fell or Returned hears their new mentor (6.9).
	 */
	static void mentorLine(ServerPlayer p, String line) {
		Path path = Story.get(p).storyPath();

		if (path == Path.JEDI || path == Path.SITH) {
			StoryService.mentorSays(p, MentorRole.of(path), "c5." + line);
		} else {
			StoryService.say(p, Component.translatable("npc.oldrepublic.tessaly.name"), "npc.oldrepublic.tessaly.c5." + line);
		}
	}

	/** A hub person or story character speaks a line in chat ("Name: line"). */
	static void npcLine(ServerPlayer p, String npc, String line) {
		StoryService.say(p, Component.translatable("npc.oldrepublic." + npc + ".name"), "npc.oldrepublic." + npc + ".c5." + line);
	}

	/** Sends a Chapter Five milestone banner ({@code lesson_complete} 11, 6.10) with the path as its intensity. */
	static void banner(ServerPlayer p, int variant) {
		EffectEvents.toPlayer(p, QuestEffects.LESSON_COMPLETE, p.position(), null, p.getId(), Story.get(p).storyPath().ordinal(), variant);
	}

	/** Whether a story-tagged entity belongs to the given player. */
	static boolean ownedBy(Entity entity, ServerPlayer p) {
		UUID owner = StoryTags.owner(entity);
		return owner != null && owner.equals(p.getUUID());
	}

	/** The planet the player stands on, or null (the home world counts as {@code HOME}, an orbit as null). */
	static Planets.@Nullable Planet planet(ServerPlayer p) {
		return Planets.Planet.of(p.level().dimension());
	}

	// ---------------------------------------------------------------- QuestHooks (Milestone 5, 17.1)

	/** W1: the owner flew their ship by hand (6.2, "Take the helm"). */
	public static void onHelmTaken(ServerPlayer owner) {
		TheCall.onHelmTaken(owner);
	}

	/** W1: the owner's ship entered {@code system}'s orbit; {@code byJump} for a hyperspace jump (6.2, 6.5). */
	public static void onOrbitEntered(ServerPlayer owner, Planets.Planet system, boolean byJump) {
		TheCall.onOrbitEntered(owner, byJump);
		TyrvannArc.onOrbitEntered(owner, system, byJump);
	}

	/** W2: the owner's blockade over Tyrvann is broken (6.5). */
	public static void onBlockadeBroken(ServerPlayer owner) {
		TyrvannArc.onBlockadeBroken(owner);
	}

	/**
	 * The owner's blockade battle was lost: their ship was disabled in it (6.5). Called from the ship's
	 * state change; W2 may also call it when its battle resets. Counted once per battle.
	 */
	public static void onBlockadeFailed(ServerPlayer owner) {
		TyrvannArc.onBlockadeFailed(owner);
	}

	/** W2: a {@code space_run} or {@code space_clear} objective of mission {@code missionId} ended (5.9). */
	public static void onSpaceObjectiveDone(ServerPlayer p, String missionId, boolean success) {
		if (ChapterFiveRules.CARGO.equals(missionId)) {
			SaltgateWays.onCargoDone(p, success);
		} else {
			SpaceContracts.onSpaceObjectiveDone(p, missionId, success);
		}
	}

	/** W2: the player shot down a starfighter; {@code encounterOwner} is the encounter's owner, if any (5.6, 6.10). */
	public static void onShipKill(ServerPlayer shooter, Entity fighter, @Nullable UUID encounterOwner) {
		Journal.unlockCodex(shooter, JournalEntries.CODEX_STARFIGHTERS);
		SaltgateWays.onShipKill(shooter, fighter);
	}

	/** W3: the owner silenced verse stone {@code index} (6.6). */
	public static void onVerseSilenced(ServerPlayer owner, int index) {
		TyrvannArc.onVerseSilenced(owner, index);
	}

	/**
	 * W8: the player took part in a clear of the Long Hymn; {@code firstClear} for their first (12.3).
	 * W8 pays it and the deed reads {@code RaidData}; the journal gets its entry and the First Cantor's
	 * page (both are kept once, so W8 unlocking them too changes nothing).
	 */
	public static void onRaidCleared(ServerPlayer p, boolean firstClear) {
		if (firstClear) {
			Journal.unlockEntry(p, JournalEntries.THE_FIRST_CANTOR_FALLS);
			Journal.unlockCodex(p, JournalEntries.CODEX_THE_FIRST_CANTOR);
		}
	}

	/** W1: the player touched down on {@code planet}; {@code first} marks their first landing there (6.1). */
	public static void onPlanetArrived(ServerPlayer p, Planets.Planet planet, boolean first) {
		switch (planet) {
			case GHESSAR -> SaltgateWays.onArrived(p);
			case TYRVANN -> TyrvannArc.onLanded(p);
			default -> {
			}
		}
	}

	/**
	 * W8: the player killed one of the five Milestone 5 foes (7, 6.10): the codex page of its kind on
	 * the first kill, and for an enforcer or a clan warrior the M3 kill reputation with its faction.
	 */
	public static void onFoeKilled(ServerPlayer killer, LivingEntity victim, String kind, @Nullable Faction faction, boolean wasHostile) {
		String id = kind.contains(":") ? kind.substring(kind.indexOf(':') + 1) : kind;
		String codex = switch (id.toLowerCase(Locale.ROOT)) {
			case "cartel_enforcer" -> JournalEntries.CODEX_CARTEL_ENFORCERS;
			case "hymnsworn" -> JournalEntries.CODEX_HYMNSWORN;
			case "sand_scour" -> JournalEntries.CODEX_SAND_SCOURS;
			case "stormback" -> JournalEntries.CODEX_STORMBACKS;
			default -> null;
		};

		if (codex != null) {
			Journal.unlockCodex(killer, codex);
		}

		if (faction == Faction.VESSBA_KAJIDIC || faction == Faction.CLAN_HASK) {
			FactionStanding.onKill(killer, faction, wasHostile);
		}
	}

	/** Darth Ulvaine's fight entered {@code phase} (12.6): nothing in the story waits on a phase. */
	public static void onBossPhase(ServerPlayer player, String bossId, int phase) {
		// The fight's phases are W8's; the chapter moves only on the yield and the choice.
	}

	/** Darth Ulvaine yielded and waits for the owner's choice (6.7, 12.6). */
	public static void onBossYielded(ServerPlayer owner, String bossId) {
		if (ULVAINE.equals(bossId)) {
			UlvaineChoice.onYielded(owner);
		}
	}

	/**
	 * One choice's rewards over Ulvaine, once per choice (6.7; the {@code ulvaine:resolve} action pays
	 * them after W8's scene). Public for tests.
	 *
	 * @return whether the rewards were given now
	 */
	public static boolean applyUlvaineChoice(ServerPlayer p, BossOutcome outcome) {
		return UlvaineChoice.applyChoice(p, outcome);
	}

	/** Darth Ulvaine's fight ended with {@code outcome}; called once per participant (6.7, 6.9, 12.6). Her echo is W8's to pay. */
	public static void onBossDefeated(ServerPlayer player, String bossId, BossOutcome outcome, UUID owner, boolean firstClear) {
		if (ULVAINE.equals(bossId) && outcome != BossOutcome.ECHO) {
			UlvaineChoice.onDefeated(player, owner);
		}
	}

	// ---------------------------------------------------------------- missions and people (from the mission and hub services)

	/**
	 * Pays a finished Chapter Five story mission (from {@code MissionService.complete}, in place of the
	 * M3 story pay): the Saltgate jobs, the hunt and the duel by {@link SaltgateWays}, the counting-house
	 * by {@link CountingHouse}. Each is paid once by its own flag.
	 */
	public static void payMission(ServerPlayer p, MissionDefinition def) {
		if (ChapterFiveRules.COUNTING_HOUSE_RUN.equals(def.id())) {
			CountingHouse.pay(p);
		} else {
			SaltgateWays.pay(p, def);
		}
	}

	/**
	 * A hub host was used (from {@code HubNpcs}): the story takes the use when it has something for
	 * that person, and returns true; false lets the host's dialogue open. Clan Hask's champion starts
	 * the duel of honour; meeting Varra is remembered (6.4: only then may Dask go to the clan).
	 */
	public static boolean onPersonUsed(ServerPlayer p, HubNpc npc) {
		String variant = npc.variant();

		if (HubNpcs.CHAMPION.equals(variant)) {
			SaltgateWays.championUsed(p, npc);
			return true;
		}

		if ("varra".equals(variant) && atLeastSaltgate(p)) {
			once(p, ChapterFiveRules.MET_VARRA);
		}

		return false;
	}

	private static boolean atLeastSaltgate(ServerPlayer p) {
		return Story.get(p).chapter5.stage().ordinal() >= ChapterFiveStage.SALTGATE.ordinal();
	}

	// ---------------------------------------------------------------- hooks routed from the M1 to M4 services

	/** A ship's state changed (from {@code ChapterFourService}): a ship disabled in the blockade counts a failure (6.5). */
	public static void onShipStateChanged(ServerPlayer owner, ShipState from, ShipState to) {
		if (to == ShipState.DISABLED && from != ShipState.DISABLED) {
			TyrvannArc.onShipDisabled(owner);
		}
	}

	/** The player entered a site piece: the Heart's door has opened for them (6.10, entry {@code the_hymnal}). */
	public static void onSiteEntered(ServerPlayer p, SitePiece piece) {
		TyrvannArc.onSiteEntered(p, piece);
	}

	/** A skill node was learned: each Milestone 5 power's codex page (6.10). */
	public static void onNodeLearned(ServerPlayer p, String nodeId) {
		String codex = switch (nodeId) {
			case "force_stasis" -> JournalEntries.CODEX_FORCE_STASIS;
			case "force_valor" -> JournalEntries.CODEX_FORCE_VALOR;
			case "force_drain" -> JournalEntries.CODEX_FORCE_DRAIN;
			case "force_fear" -> JournalEntries.CODEX_FORCE_FEAR;
			case "force_rage" -> JournalEntries.CODEX_FORCE_RAGE;
			case "force_mind_trick" -> JournalEntries.CODEX_MIND_TRICK;
			default -> null;
		};

		if (codex != null) {
			Journal.unlockCodex(p, codex);
		}
	}

	/** A level-up may open the Hymnal (6.1: three stones and the duel level). */
	public static void onLevelUp(ServerPlayer p, int level) {
		TyrvannArc.checkVerses(p);
	}

	// ---------------------------------------------------------------- lifecycle

	static void onStartSleeping(ServerPlayer p) {
		TheCall.onStartSleeping(p);
	}

	static void onStopSleeping(ServerPlayer p) {
		TheCall.onStopSleeping(p);
	}

	/** Logout, respawn or a dimension change: in-memory state for the player goes. */
	static void forget(ServerPlayer p) {
		TheCall.forget(p);
		TyrvannArc.forget(p);
		StoryNpcs.forget(p);
		NEXT_TRY.keySet().removeIf(k -> k.startsWith(p.getUUID() + ":"));
	}

	static void clear() {
		TheCall.clear();
		TyrvannArc.clear();
		StoryNpcs.clear();
		NEXT_TRY.clear();
	}

	/** Whether a loaded entity is a Chapter Five leftover no one tracks after a restart (a guard ally, a story person). */
	static boolean isLeftover(Entity entity) {
		return entity.entityTags().contains(StoryNpcs.STORY_TAG);
	}

	/** Loads the chapter during mod init: its dialogue conditions and actions, and its commands. */
	static void init() {
		ChapterFiveDialogue.init();
		ChapterFiveCommands.register();
	}

	private ChapterFiveService() {
	}
}
