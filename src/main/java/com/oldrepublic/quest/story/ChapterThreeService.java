package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.ReliquarySites;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.holocron.ArchiveChoice;
import com.oldrepublic.holocron.HolocronChoice;
import com.oldrepublic.identity.IdentityRules;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.RepublicSeed;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.world.EchoCairns;
import com.oldrepublic.world.outpost.Outposts;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Chapter Three, "The Ones That Sing" (spec section 6): the stage machine and the entry points the
 * quest hooks, other features and the commands call. Each part of the chapter lives in its own
 * class ({@link OnesThatSing}, {@link ChapterThreeOpenings}, {@link FirstEcho}, {@link VaschBeats},
 * {@link CollectorRewards}, {@link RankThree}, {@link BrennCameo}); this class moves the stage and
 * runs each stage's entry logic. Stages only move forward; every reward is guarded by a stage change
 * or a one-shot flag ({@link ChapterThreeRules}). Server only.
 */
public final class ChapterThreeService {
	/** The Reliquary's hall template: crossing into it starts the confrontation. */
	static final Identifier RELIQUARY_HALL = OldRepublicMod.id("boss/reliquary_hall");
	/** The owner is "in the hall" when the heart lies inside the entered piece widened by this much. */
	private static final int HALL_SLACK = 8;

	/** Game time before which a failed placement or search is not tried again, per player and task. */
	private static final Map<String, Long> NEXT_TRY = new HashMap<>();

	public static ChapterThreeBalance balance() {
		return BalanceConfig.section("chapter_three", ChapterThreeBalance.class, ChapterThreeBalance::new);
	}

	// ---------------------------------------------------------------- facade (W2 plan 4.1)

	/**
	 * Adds a lead ({@code mission:<id>}, {@code holo:<id>}, {@code ledger}, {@code contract}). Leads
	 * are kept from any stage and count once LEADS begins; at most 16.
	 *
	 * @return whether the lead was new
	 */
	public static boolean addLead(ServerPlayer p, String lead) {
		StoryData data = Story.get(p);

		if (!ChapterThreeRules.addLead(data, lead)) {
			return false;
		}

		Story.commit(p, data);
		StoryData fresh = Story.get(p);

		if (fresh.chapter3.stage() == ChapterThreeStage.LEADS) {
			ChapterThreeBalance b = balance();
			int count = fresh.chapter3.leads().size();
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c3.lead", Component.translatable(ChapterThreeRules.leadKey(lead)),
					Math.min(count, b.leadsNeeded), b.leadsNeeded));
			checkLeads(p);
		}

		return true;
	}

	/** A contract was completed: the {@code contract} lead, once, only while the chapter is at LEADS. */
	public static void onContractCompleted(ServerPlayer p) {
		if (Story.get(p).chapter3.stage() == ChapterThreeStage.LEADS) {
			addLead(p, ChapterThreeRules.LEAD_CONTRACT);
		}
	}

	/**
	 * Adds a paragraph under a journal entry: the translated {@code langKey} is shown below
	 * {@code entryId}'s text (notes sorted by key). Stored as the story flag
	 * {@code note.<entryId>|<langKey>}, so each note is added once.
	 */
	public static void addJournalNote(ServerPlayer p, String entryId, String langKey) {
		StoryData data = Story.get(p);

		if (data.flags.add(ChapterThreeRules.NOTE_PREFIX + entryId + "|" + langKey)) {
			Story.commit(p, data);
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c3.journal_note"));
		}
	}

	/**
	 * Moves the chapter to {@code stage} if it is later than the current one and runs that stage's
	 * entry logic (commands and tests use it to jump; the story uses it for every step).
	 */
	public static void enterStage(ServerPlayer p, ChapterThreeStage stage) {
		StoryData data = Story.get(p);
		ChapterThreeStage before = data.chapter3.stage();

		if (!ChapterThreeRules.advance(data, stage)) {
			return;
		}

		Story.commit(p, data);
		OldRepublicMod.LOGGER.debug("Chapter Three of {} entered {}", p.getName().getString(), stage);

		if (before == ChapterThreeStage.LISTENING) {
			// The hidden cairns are done with: they go even if their owner logs off before one ticks.
			EchoCairns.expire(p);
		}

		onEnter(p, stage);
	}

	/**
	 * Starts the wait once Chapter Two is finished: also how M2 saves at Chapter Two COMPLETE enter
	 * WAITING (6.9), with the wait counted from now. Called from the tick; public for tests.
	 *
	 * @return whether the chapter started waiting
	 */
	public static boolean checkWaiting(ServerPlayer p) {
		if (!ChapterThreeRules.shouldWait(Story.get(p))) {
			return false;
		}

		enterStage(p, ChapterThreeStage.WAITING);
		return true;
	}

	// ---------------------------------------------------------------- stage entry

	private static void onEnter(ServerPlayer p, ChapterThreeStage stage) {
		switch (stage) {
			case WAITING -> {
				StoryData data = Story.get(p);
				data.chapter3 = ChapterThreeRules.withWaitSince(data.chapter3, StoryService.now(p));
				data.flags.remove(ChapterThreeRules.DREAM_DUE);
				Story.commit(p, data);
			}
			case SUMMONS -> enterSummons(p);
			case LISTENING -> ChapterThreeOpenings.placeHidden(p);
			case FIRST_ECHO -> FirstEcho.begin(p);
			case LEADS -> enterLeads(p);
			case RELIQUARY -> placeReliquary(p, false);
			case NOT_STARTED, CONFRONTATION, AFTERMATH, COMPLETE -> {
			}
		}
	}

	/** The dream has ended: the two flags, the Republic seed, outposts, and the opening for the path. */
	private static void enterSummons(ServerPlayer p) {
		Journal.unlockEntry(p, JournalEntries.THE_ONES_THAT_SING);
		Journal.unlockEntry(p, JournalEntries.TWO_FLAGS);
		Journal.unlockCodex(p, JournalEntries.CODEX_TWO_FLAGS);
		RepublicSeed.apply(p);
		Outposts.ensureNear(p);
		Path path = Story.get(p).storyPath();

		if (path == Path.INDEPENDENT) {
			ChapterThreeOpenings.placeFall(p);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.summons.independent"));
		} else if (path == Path.JEDI || path == Path.SITH) {
			StoryService.mentorSays(p, MentorRole.of(path), "c3.summons");
		}
	}

	/** LEADS begins: the Ledger counts if Chapter Two found it, and earlier leads count now. */
	private static void enterLeads(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.hasFlag(StoryRules.LEDGER_READ) || data.hasFlag(StoryRules.LEDGER_DELIVERED)) {
			addLead(p, ChapterThreeRules.LEAD_LEDGER);
		}

		checkLeads(p);
	}

	/** Leaves LEADS once the trail is clear and the level allows; tells the player when the level holds it. */
	static void checkLeads(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.chapter3.stage() != ChapterThreeStage.LEADS) {
			return;
		}

		ChapterThreeBalance b = balance();
		int leads = data.chapter3.leads().size();
		int level = Progression.get(p).level;

		if (ChapterThreeRules.leadsReady(leads, level, b)) {
			enterStage(p, ChapterThreeStage.RELIQUARY);
		} else if (ChapterThreeRules.levelGated(leads, level, b) && due(p, "gate_line")) {
			retryLater(p, "gate_line", b.retryTicks * 10L);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.level_gate", b.reliquaryMinLevel));
		}
	}

	/**
	 * Places the owner's Reliquary (6.5). The story only does so while {@code collector.placeReliquary}
	 * is on; the command always does. A failure is logged and retried every {@code retryTicks}; there
	 * is no forward fallback.
	 *
	 * @return whether the Reliquary is placed now
	 */
	static boolean placeReliquary(ServerPlayer p, boolean command) {
		StoryData data = Story.get(p);

		if (data.chapter3.reliquary() != null) {
			return true;
		}

		if ((!command && !CollectorRewards.placeReliquary()) || !StoryService.inOverworld(p)) {
			return false;
		}

		BlockPos heart = ReliquarySites.place(p, p.position(), p.getLookAngle());

		if (heart == null) {
			retryLater(p, "reliquary", balance().retryTicks);
			OldRepublicMod.LOGGER.warn("Could not place the Reliquary for {} near {}; retrying in {} ticks", p.getName().getString(),
					p.blockPosition(), balance().retryTicks);
			return false;
		}

		StoryData fresh = Story.get(p);
		fresh.chapter3 = ChapterThreeRules.withReliquary(fresh.chapter3, heart);
		Story.commit(p, fresh);
		Journal.unlockEntry(p, JournalEntries.THE_RELIQUARY);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.reliquary", balance().recommendedLevel));
		return true;
	}

	// ---------------------------------------------------------------- ticking

	/** Every tick for each living player (from {@link StoryService#tick}). */
	static void tick(ServerPlayer p) {
		OnesThatSing.tick(p);
		StoryGuidance.journalTick(p);

		if ((p.tickCount + p.getId()) % StoryService.SLOW_TICK == 0) {
			slowTick(p);
		}
	}

	/** The heavier checks: placement retries, spawns on approach, Vasch's beats, the cameo. */
	private static void slowTick(ServerPlayer p) {
		StoryData data = Story.get(p);
		StoryData.Chapter3 c = data.chapter3;

		switch (c.stage()) {
			case SUMMONS -> {
				if (data.storyPath() == Path.INDEPENDENT && c.fall() == null && due(p, "fall")) {
					ChapterThreeOpenings.placeFall(p);
				}
			}
			case LISTENING -> {
				if (c.cairns().isEmpty() && due(p, "cairns")) {
					ChapterThreeOpenings.placeHidden(p);
				}
			}
			case FIRST_ECHO -> FirstEcho.tick(p);
			case LEADS -> checkLeads(p);
			case RELIQUARY -> {
				if (c.reliquary() == null && due(p, "reliquary")) {
					placeReliquary(p, false);
				}
			}
			default -> {
			}
		}

		ChapterThreeOpenings.tickFall(p);
		VaschBeats.tick(p);
		BrennCameo.tick(p);
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

	/** Lets a task run at once again (after a success, or on a command). */
	static void clearRetry(ServerPlayer p, String task) {
		NEXT_TRY.remove(key(p, task));
	}

	private static String key(ServerPlayer p, String task) {
		return p.getUUID() + ":" + task;
	}

	/** The game-day index of the player's level (the M2 daily convention). */
	static int day(ServerPlayer p) {
		return IdentityRules.dayIndex(p.level().getGameTime());
	}

	// ---------------------------------------------------------------- hooks (from QuestService)

	/** The player entered a site piece: the Reliquary hall starts the confrontation; an Imperial outpost may bring Brenn. */
	public static void onSiteEntered(ServerPlayer p, SitePiece piece) {
		StoryData data = Story.get(p);
		BlockPos heart = data.chapter3.reliquary();

		if (piece.template().equals(RELIQUARY_HALL) && data.chapter3.stage() == ChapterThreeStage.RELIQUARY && heart != null
				&& piece.box().inflatedBy(HALL_SLACK).isInside(heart)) {
			enterStage(p, ChapterThreeStage.CONFRONTATION);
		}

		if (piece.kind() == SiteKind.IMPERIAL_OUTPOST) {
			BrennCameo.onImperialOutpost(p, piece);
		}
	}

	/** A boss fight the player takes part in changed phase: the owner is in their own hall. */
	public static void onBossPhase(ServerPlayer p, String bossId, int phase) {
		StoryData data = Story.get(p);
		BlockPos heart = data.chapter3.reliquary();

		if (data.chapter3.stage() == ChapterThreeStage.RELIQUARY && heart != null
				&& p.blockPosition().distSqr(heart) <= (double) CollectorRewards.HALL_REACH * CollectorRewards.HALL_REACH) {
			enterStage(p, ChapterThreeStage.CONFRONTATION);
		}
	}

	/** The player touched their cairn or cache. */
	public static void onCairnTouched(ServerPlayer p, BlockPos pos, int index, boolean cache) {
		ChapterThreeOpenings.onTouched(p, pos, index, cache);
	}

	/**
	 * A holocron was attuned: the Compass opens the Independent's LISTENING; world holocrons give
	 * codex entries, leads from FIRST_ECHO on, Vasch's message, and end the first echo.
	 */
	public static void onHolocronAttuned(ServerPlayer p, String holocronId, HolocronChoice choice, boolean first) {
		StoryData data = Story.get(p);
		Path path = data.storyPath();
		ChapterThreeStage stage = data.chapter3.stage();
		boolean world = ChapterThreeRules.isWorldHolocron(holocronId);

		if (world) {
			Journal.unlockCodex(p, JournalEntries.CODEX_HOLOCRONS);
		}

		if (ChapterThreeRules.WAYFARER_PELL.equals(holocronId) && path == Path.INDEPENDENT) {
			Journal.unlockCodex(p, JournalEntries.CODEX_TELEKINETIC_LIFT);
		}

		if (ChapterThreeRules.WAYFARERS_COMPASS.equals(holocronId)) {
			Journal.unlockCodex(p, JournalEntries.CODEX_FORCE_SENSE);

			if (path == Path.INDEPENDENT && stage == ChapterThreeStage.SUMMONS) {
				ChapterThreeOpenings.onCompassAttuned(p);
			}

			return;
		}

		if (!world || !first || stage.ordinal() < ChapterThreeStage.FIRST_ECHO.ordinal()) {
			return;
		}

		VaschBeats.onHolocronAttuned(p, data.chapter3.leads().size());
		boolean endsEcho = stage == ChapterThreeStage.FIRST_ECHO && ChapterThreeRules.endsFirstEcho(path, holocronId);
		addLead(p, ChapterThreeRules.LEAD_HOLOCRON_PREFIX + holocronId);

		if (endsEcho) {
			FirstEcho.leave(p);
		}
	}

	/** The Archive's fate was decided: its entry, and the report may follow. */
	public static void onArchiveResolved(ServerPlayer p, ArchiveChoice choice) {
		CollectorRewards.onArchiveResolved(p, choice);
	}

	/** The first era armour piece put on: the codex entry. */
	public static void onArmourEquipped(ServerPlayer p, ArmourSet set) {
		Journal.unlockCodex(p, JournalEntries.CODEX_ERA_ARMOUR);
	}

	/** The owner's boss yielded: the owner's ending dialogue and the helpers' stance dialogues. */
	public static void onBossYielded(ServerPlayer owner, String bossId) {
		CollectorRewards.onBossYielded(owner, bossId);
	}

	/** A boss fight ended for this participant: every Collector reward (8.8, 8.9). */
	public static void onBossDefeated(ServerPlayer p, String bossId, BossOutcome outcome, UUID owner, boolean firstClear) {
		CollectorRewards.onBossDefeated(p, bossId, outcome, owner, firstClear);
	}

	/** A level-up may open the Reliquary. */
	public static void onLevelUp(ServerPlayer p, int level) {
		checkLeads(p);
	}

	// ---------------------------------------------------------------- lifecycle

	static void onStartSleeping(ServerPlayer p) {
		OnesThatSing.onStartSleeping(p);
	}

	static void onStopSleeping(ServerPlayer p) {
		OnesThatSing.onStopSleeping(p);
	}

	static void onDamaged(ServerPlayer p) {
		OnesThatSing.onDamaged(p);
	}

	/** Logout, respawn or a dimension change: in-memory state for the player goes. */
	static void forget(ServerPlayer p) {
		OnesThatSing.forget(p);
		FirstEcho.forget(p);
		VaschBeats.forget(p);
		BrennCameo.forget(p);
		NEXT_TRY.keySet().removeIf(k -> k.startsWith(p.getUUID() + ":"));
	}

	static void clear() {
		OnesThatSing.clear();
		FirstEcho.clear();
		VaschBeats.clear();
		BrennCameo.clear();
		NEXT_TRY.clear();
	}

	/** Whether a loaded entity is a Chapter Three leftover (a counter-hunt or a cameo no one tracks after a restart). */
	static boolean isLeftover(Entity entity) {
		return VaschBeats.isLeftover(entity) || BrennCameo.isLeftover(entity);
	}

	/** Whether the player follows a mentor (Jedi or Sith) in the story. */
	static boolean mentorPath(Path path) {
		return path == Path.JEDI || path == Path.SITH;
	}

	private ChapterThreeService() {
	}
}
