package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.mission.MissionHooks;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.vendor.VendorBalance;
import com.oldrepublic.vendor.VendorFeature;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Chapter Four, "Stars to Steer By" (spec MILESTONE_4.md 8): the stage machine and the entry point
 * of every Milestone 4 {@code QuestHooks} call. Each part of the chapter lives in its own class
 * ({@link SeekerScene}, {@link ChapterFourRepairs}, {@link WorldArcs}, {@link TheHunt},
 * {@link KaastDuel}); this class moves the stage, runs each stage's entry logic, ticks the chapter
 * and holds the small helpers the parts share. Stages only move forward, and every reward is
 * guarded by a stage change or a one-time flag ({@link ChapterFourRules}). Server only.
 */
public final class ChapterFourService {
	/** Game time before which a failed placement or search is not tried again, per player and task. */
	private static final Map<String, Long> NEXT_TRY = new HashMap<>();

	public static ChapterFourBalance balance() {
		return BalanceConfig.section("chapter_four", ChapterFourBalance.class, ChapterFourBalance::new);
	}

	// ---------------------------------------------------------------- the stage machine

	/**
	 * Moves the chapter to {@code stage} if it is later than the current one and runs that stage's
	 * entry logic (the story uses it for every step; commands and tests use it to jump).
	 *
	 * @return whether the stage changed
	 */
	public static boolean enterStage(ServerPlayer p, ChapterFourStage stage) {
		StoryData data = Story.get(p);

		if (!ChapterFourRules.advance(data, stage, StoryService.now(p))) {
			return false;
		}

		Story.commit(p, data);
		OldRepublicMod.LOGGER.debug("Chapter Four of {} entered {}", p.getName().getString(), stage);
		onEnter(p, stage);
		return true;
	}

	private static void onEnter(ServerPlayer p, ChapterFourStage stage) {
		switch (stage) {
			case MARKED -> {
				StoryData data = Story.get(p);
				data.flags.remove(ChapterFourRules.SEEKER_DUE);
				Story.commit(p, data);
			}
			case SEEKER -> SeekerScene.onSeekerStage(p);
			case REPAIRS -> ChapterFourRepairs.onRepairs(p);
			case FIRST_FLIGHT -> ChapterFourRepairs.onLiftOff(p);
			case WORLD_ARC -> WorldArcs.onArcBegins(p);
			case HUNTED -> TheHunt.onHunted(p);
			case CHALLENGED -> KaastDuel.onChallenged(p);
			case AFTERMATH -> KaastDuel.onAftermath(p);
			case NOT_STARTED, DUEL, COMPLETE -> {
			}
		}
	}

	/**
	 * Begins Chapter Four once Chapter Three is done: also how M3 saves at Chapter Three COMPLETE
	 * enter MARKED on their first tick after joining (8.8). Public for tests.
	 *
	 * @return whether the chapter began
	 */
	public static boolean checkMarked(ServerPlayer p) {
		return ChapterFourRules.shouldMark(Story.get(p)) && enterStage(p, ChapterFourStage.MARKED);
	}

	// ---------------------------------------------------------------- ticking

	/** Every tick for each living player (from {@link StoryService#tick}). */
	static void tick(ServerPlayer p) {
		SeekerScene.tick(p);
		WorldArcs.tick(p);

		if ((p.tickCount + p.getId()) % StoryService.SLOW_TICK == 0) {
			slowTick(p);
		}
	}

	/** The heavier checks: back pay, the stage's waits and retries, and the hunt. */
	private static void slowTick(ServerPlayer p) {
		settleBackPay(p);
		checkMarked(p);
		StoryData data = Story.get(p);

		ChapterFourStage stage = data.chapter4.stage();

		switch (stage) {
			case MARKED -> SeekerScene.check(p);
			case SEEKER -> ChapterFourRepairs.tickSeeker(p);
			case REPAIRS -> ChapterFourRepairs.tickRepairs(p);
			case HUNTED -> TheHunt.checkChallenge(p);
			case CHALLENGED -> KaastDuel.tickChallenged(p);
			case AFTERMATH -> KaastDuel.tickAftermath(p);
			default -> {
			}
		}

		// The arc's missions, the expeditions and Both Doors stay open after WORLD_ARC (8.4, 8.7).
		if (stage.ordinal() >= ChapterFourStage.WORLD_ARC.ordinal()) {
			WorldArcs.slowTick(p);
		}

		if (stage.ordinal() >= ChapterFourStage.FIRST_FLIGHT.ordinal() && Planets.isPlanet(p.level())) {
			ChapterFourRepairs.checkHub(p);
		}

		if (ChapterFourRules.huntRunning(Story.get(p).chapter4)) {
			TheHunt.tick(p);
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

	/** Lets a task run at once again. */
	static void clearRetry(ServerPlayer p, String task) {
		NEXT_TRY.remove(key(p, task));
	}

	private static String key(ServerPlayer p, String task) {
		return p.getUUID() + ":" + task;
	}

	/** The world day (the overworld clock, so planets count the same days; 3.6). */
	static long day(ServerPlayer p) {
		return Clocks.day(p.level().getServer());
	}

	static boolean peaceful(ServerPlayer p) {
		return p.level().getDifficulty() == Difficulty.PEACEFUL;
	}

	// ---------------------------------------------------------------- back pay (8.8)

	/**
	 * The one-time Milestone 3 back pay, on the player's first tick in Milestone 4: 100 credits per
	 * M3 milestone held (the Collector cleared, rank 3 or the Pathfinder's title, each attuned world
	 * holocron), at most 300. A player with none settles it at once with nothing paid.
	 *
	 * @return the credits paid now (0 when it was settled before or nothing was owed)
	 */
	public static int settleBackPay(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.chapter4.backPay()) {
			return 0;
		}

		VendorBalance vb = VendorFeature.balance();
		int milestones = backPayMilestones(p, data);
		int pay = ChapterFourRules.backPay(milestones, vb.backPayPerMilestone, vb.backPayMax);
		data.chapter4 = data.chapter4.withBackPay();
		Story.commit(p, data);

		if (pay > 0) {
			Credits.give(p, pay);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.back_pay", pay));
			Journal.unlockCodex(p, JournalEntries.CODEX_CREDITS);
		}

		return pay;
	}

	/** The M3 milestones the back pay counts. */
	static int backPayMilestones(ServerPlayer p, StoryData data) {
		int milestones = HolocronService.attunedCount(p, null);

		if (data.hasFlag(ChapterThreeRules.COLLECTOR_SP)) {
			milestones++;
		}

		if (data.hasFlag(ChapterThreeRules.RANK3_DONE) || data.hasFlag(ChapterThreeRules.PATHFINDER_DONE)) {
			milestones++;
		}

		return milestones;
	}

	// ---------------------------------------------------------------- shared helpers

	/** Chapter Four XP (source {@code chapter_four}). */
	static void xp(ServerPlayer p, int amount) {
		if (amount > 0) {
			Progression.addXp(p, amount, "chapter_four");
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

	/** The path's mentor speaks a Chapter Four line ({@code npc.oldrepublic.<role>.c4.<line>}); Independents hear Tessaly. */
	static void mentorLine(ServerPlayer p, String line) {
		Path path = Story.get(p).storyPath();

		if (path == Path.JEDI || path == Path.SITH) {
			StoryService.mentorSays(p, MentorRole.of(path), "c4." + line);
		} else {
			StoryService.say(p, Component.translatable("npc.oldrepublic.tessaly.name"), "npc.oldrepublic.tessaly.c4." + line);
		}
	}

	/** Sends a Chapter Four milestone banner ({@code lesson_complete} 8 to 10). */
	static void banner(ServerPlayer p, int variant, int intensity) {
		EffectEvents.toPlayer(p, QuestEffects.LESSON_COMPLETE, p.position(), null, p.getId(), intensity, variant);
	}

	/** The owner of a story-tagged entity, when it is the given player. */
	static boolean ownedBy(Entity entity, ServerPlayer p) {
		UUID owner = StoryTags.owner(entity);
		return owner != null && owner.equals(p.getUUID());
	}

	// ---------------------------------------------------------------- QuestHooks (M4)

	/** A clue was recorded by {@link ChapterFour#addClue} (8.5): XP, journal and the stage change. */
	static void onClue(ServerPlayer p, int clue) {
		TheHunt.onClue(p, clue);
	}

	public static void onPlanetArrived(ServerPlayer p, Planets.Planet planet, boolean first) {
		ChapterFourRepairs.onPlanetArrived(p, planet, first);
		TheHunt.onPlanetArrived(p, planet);
	}

	public static void onShipStateChanged(ServerPlayer owner, ShipState from, ShipState to) {
		ChapterFourRepairs.onShipStateChanged(owner, from, to);
		TheHunt.onShipStateChanged(owner, from, to);
	}

	public static void onShipPartFitted(ServerPlayer owner, int socket) {
		ChapterFourRepairs.onPartFitted(owner, socket);
	}

	public static void onCrafted(ServerPlayer p, Identifier schematic, int quality, boolean first) {
		WorldArcs.onCrafted(p, schematic, quality, first);
	}

	public static void onDisciplineLevel(ServerPlayer p, String discipline, int level) {
		if (level == 1) {
			Journal.unlockEntry(p, JournalEntries.A_TRADE);
			Journal.unlockCodex(p, JournalEntries.CODEX_DISCIPLINES);
		}
	}

	public static void onPurchase(ServerPlayer p, String vendor, ItemStack bought, int price) {
		ChapterFourRepairs.onPurchase(p, vendor, bought, price);
	}

	public static void onWaystoneCleansed(ServerPlayer p, BlockPos pos, boolean heartstone) {
		WorldArcs.onWaystoneCleansed(p, pos, heartstone);

		if (!heartstone) {
			// A cleanse contract working at that stone counts it (8.7).
			MissionHooks.onWaystoneCleansed(p, pos);
		}
	}

	/** The player used a marker beacon block at {@code pos} (VendorItems' block): their own replays the challenge. */
	public static void onMarkerUsed(ServerPlayer p, BlockPos pos) {
		KaastDuel.onMarkerUsed(p, pos);
	}

	public static void onHunterKilled(ServerPlayer p, LivingEntity hunter, boolean owned) {
		Journal.unlockCodex(p, JournalEntries.CODEX_MANDALORIANS);
		TheHunt.onHunterKilled(p, hunter, owned);
	}

	public static void onDroidKilled(ServerPlayer p, LivingEntity droid) {
		Journal.unlockCodex(p, JournalEntries.CODEX_SECURITY_DROIDS);
	}

	public static void onBeastKilled(ServerPlayer p, LivingEntity beast, String kind) {
		Journal.unlockCodex(p, "ash_hound".equals(kind) ? JournalEntries.CODEX_ASH_HOUNDS : JournalEntries.CODEX_HOLLOWHORNS);
	}

	public static void onExpeditionDone(ServerPlayer p, PlanetSites.Site site) {
		WorldArcs.onExpeditionDone(p, site);
	}

	// ---------------------------------------------------------------- hooks routed from QuestService (M1 to M3 hooks)

	/** A Force power was used: Barrier raised during the Heartstone, Speed on Tessaly's platforms. */
	public static void onAbilityUsed(ServerPlayer p, String abilityId, int hostileHits) {
		WorldArcs.onAbilityUsed(p, abilityId);
	}

	/** A Force power landed: a Choke on Zarvos's echo ends the Sepulchre. */
	public static void onAbilityHit(ServerPlayer p, String abilityId, @Nullable Entity target) {
		WorldArcs.onAbilityHit(p, abilityId, target);
	}

	/** Force Sense was cast: the Wreck Field motivator (8.2). */
	public static void onForceSense(ServerPlayer p, int revealed) {
		ChapterFourRepairs.onForceSense(p);
	}

	/** The player entered a site piece: a tomb or stone of the arc, or the hunting camp's ring, which starts the duel. */
	public static void onSiteEntered(ServerPlayer p, SitePiece piece) {
		WorldArcs.onSiteEntered(p, piece);
		KaastDuel.onSiteEntered(p, piece);
	}

	/** A level-up may open the challenge. */
	public static void onLevelUp(ServerPlayer p, int level) {
		TheHunt.checkChallenge(p);
	}

	/** Kaast yielded to his owner: the choice dialogue. */
	public static void onBossYielded(ServerPlayer owner, String bossId) {
		KaastDuel.onYielded(owner);
	}

	/** A Kaast fight ended for this participant: the owner's, a helper's or a rematch's rewards (10.6). */
	public static void onBossDefeated(ServerPlayer p, String bossId, BossOutcome outcome, UUID owner, boolean firstClear) {
		KaastDuel.onDefeated(p, outcome, owner, firstClear);
	}

	/** Whether a boss id is Torvin Kaast's (the duel or its echo). */
	public static boolean isKaast(String bossId) {
		return bossId.startsWith("kaast");
	}

	// ---------------------------------------------------------------- lifecycle

	static void onStartSleeping(ServerPlayer p) {
		SeekerScene.onStartSleeping(p);
	}

	static void onStopSleeping(ServerPlayer p) {
		SeekerScene.onStopSleeping(p);
	}

	/** Logout, respawn or a dimension change: in-memory state for the player goes. */
	static void forget(ServerPlayer p) {
		SeekerScene.forget(p);
		ChapterFourRepairs.forget(p);
		WorldArcs.forget(p);
		TheHunt.forget(p);
		NEXT_TRY.keySet().removeIf(k -> k.startsWith(p.getUUID() + ":"));
	}

	static void clear() {
		SeekerScene.clear();
		ChapterFourRepairs.clear();
		WorldArcs.clear();
		TheHunt.clear();
		NEXT_TRY.clear();
	}

	/** Whether a loaded entity is a Chapter Four leftover no one tracks after a restart (a seeker, a trial's wave). */
	static boolean isLeftover(Entity entity) {
		return SeekerScene.isLeftover(entity) || WorldArcs.isLeftover(entity);
	}

	/** Loads the class during mod init: dialogue, commands and the chapter's events. */
	public static void init() {
		ChapterFourDialogue.init();
		ChapterFourCommands.register();
	}

	private ChapterFourService() {
	}
}
