package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Guidance;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.world.site.SiteKind;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Pure rules of Chapter Three, "The Ones That Sing" (spec section 6), kept apart from the server
 * so game tests and the client can check them directly. Methods that change data only ever move a
 * stage forward and leave committing to the caller; every reward elsewhere is guarded by a stage
 * change or one of the one-shot flags named here.
 */
public final class ChapterThreeRules {
	/** One-shot flags in {@link StoryData#flags} (spec 4.4 of the W2 plan). */
	public static final String COLLECTOR_SP = "c3.collector_sp";
	public static final String VASCH_MESSAGE = "c3.vasch_message";
	public static final String AMBUSH = "c3.ambush";
	public static final String VASCH_OFFER = "c3.vasch_offer";
	/** "Give them back." was said: its small light shift applies once. */
	public static final String GIVE_BACK = "c3.give_back";
	public static final String LESSON_ACCEPTED = "c3.lesson_accepted";
	public static final String LISTENING_PAID = "c3.listening_paid";
	public static final String FALL_CREW = "c3.fall_crew";
	public static final String HOARD_DECIDED = "c3.hoard_decided";
	public static final String REPORT_DONE = "c3.report_done";
	public static final String RANK3_DONE = "c3.rank3_done";
	public static final String PATHFINDER_DONE = "c3.pathfinder_done";
	public static final String BRENN_MET = "c3.brenn_met";
	/** Credited as a co-op helper of another owner's fight (8.9): a stance not yet taken stays open in AFTERMATH. */
	public static final String COOP_HELPER = "c3.coop_helper";
	/** Waking from a long sleep made the dream due (kept, so a relog still plays it). */
	public static final String DREAM_DUE = "c3.dream_due";
	/** Followed by a {@link HolocronKind} id: that kind's extra Echo Shrine was placed. */
	public static final String EXTRA_SHRINE_PREFIX = "c3.extra_shrine.";
	/** Journal notes: {@code note.<entryId>|<langKey>} (only through {@link ChapterThreeService#addJournalNote}). */
	public static final String NOTE_PREFIX = "note.";

	/** Counters in {@link StoryData#counters}. */
	public static final String ECHO_PITY = "c3.echo_pity";
	/** Game-day index of the last "other helper" reward; absent before the first. */
	public static final String HELPER_DAY = "c3.helper_day";
	public static final String PLACE_FAILURES = "c3.place_failures";

	/** Lead ids (spec 6.4). */
	public static final String LEAD_LEDGER = "ledger";
	public static final String LEAD_CONTRACT = "contract";
	public static final String LEAD_HOLOCRON_PREFIX = "holo:";
	public static final String LEAD_MISSION_PREFIX = "mission:";

	/** Holocron ids the chapter itself refers to. */
	public static final String WAYFARERS_COMPASS = "wayfarers_compass";
	public static final String WAYFARER_PELL = "wayfarer_pell";

	private static final String RANK3_REQ = "message.oldrepublic.story.rank3.";

	// ---------------------------------------------------------------- Chapter3 copies

	/** The chapter with a new stage (no checks; see {@link #advance}). */
	public static StoryData.Chapter3 withStage(StoryData.Chapter3 c, ChapterThreeStage stage) {
		return new StoryData.Chapter3(stage, c.waitSince(), c.leads(), c.cairns(), c.cairnsTouched(), c.fall(), c.shrines(), c.reliquary(),
				c.boss(), c.outcome(), c.stance(), c.echoDay(), c.echoClears());
	}

	public static StoryData.Chapter3 withWaitSince(StoryData.Chapter3 c, long waitSince) {
		return new StoryData.Chapter3(c.stage(), waitSince, c.leads(), c.cairns(), c.cairnsTouched(), c.fall(), c.shrines(), c.reliquary(),
				c.boss(), c.outcome(), c.stance(), c.echoDay(), c.echoClears());
	}

	public static StoryData.Chapter3 withLeads(StoryData.Chapter3 c, Set<String> leads) {
		return new StoryData.Chapter3(c.stage(), c.waitSince(), leads, c.cairns(), c.cairnsTouched(), c.fall(), c.shrines(), c.reliquary(),
				c.boss(), c.outcome(), c.stance(), c.echoDay(), c.echoClears());
	}

	/** New cairns or caches; the touched mask starts again. */
	public static StoryData.Chapter3 withCairns(StoryData.Chapter3 c, List<BlockPos> cairns) {
		return new StoryData.Chapter3(c.stage(), c.waitSince(), c.leads(), cairns, 0, c.fall(), c.shrines(), c.reliquary(), c.boss(),
				c.outcome(), c.stance(), c.echoDay(), c.echoClears());
	}

	public static StoryData.Chapter3 withTouched(StoryData.Chapter3 c, int mask) {
		return new StoryData.Chapter3(c.stage(), c.waitSince(), c.leads(), c.cairns(), mask, c.fall(), c.shrines(), c.reliquary(), c.boss(),
				c.outcome(), c.stance(), c.echoDay(), c.echoClears());
	}

	public static StoryData.Chapter3 withFall(StoryData.Chapter3 c, @Nullable BlockPos fall) {
		return new StoryData.Chapter3(c.stage(), c.waitSince(), c.leads(), c.cairns(), c.cairnsTouched(), fall, c.shrines(), c.reliquary(),
				c.boss(), c.outcome(), c.stance(), c.echoDay(), c.echoClears());
	}

	/** The chapter with one more Echo Shrine, or unchanged when four are placed already. */
	public static StoryData.Chapter3 withShrine(StoryData.Chapter3 c, BlockPos shrine) {
		if (c.shrines().size() >= StoryData.Chapter3.MAX_SHRINES) {
			return c;
		}

		List<BlockPos> shrines = new ArrayList<>(c.shrines());
		shrines.add(shrine);
		return new StoryData.Chapter3(c.stage(), c.waitSince(), c.leads(), c.cairns(), c.cairnsTouched(), c.fall(), shrines, c.reliquary(),
				c.boss(), c.outcome(), c.stance(), c.echoDay(), c.echoClears());
	}

	public static StoryData.Chapter3 withReliquary(StoryData.Chapter3 c, @Nullable BlockPos heart) {
		return new StoryData.Chapter3(c.stage(), c.waitSince(), c.leads(), c.cairns(), c.cairnsTouched(), c.fall(), c.shrines(), heart,
				c.boss(), c.outcome(), c.stance(), c.echoDay(), c.echoClears());
	}

	/** The boss state: 0 none, 1 yielded, 2 resolved; {@code outcome} is a {@link BossOutcome} ordinal or -1. */
	public static StoryData.Chapter3 withBoss(StoryData.Chapter3 c, int boss, int outcome) {
		return new StoryData.Chapter3(c.stage(), c.waitSince(), c.leads(), c.cairns(), c.cairnsTouched(), c.fall(), c.shrines(), c.reliquary(),
				boss, outcome, c.stance(), c.echoDay(), c.echoClears());
	}

	public static StoryData.Chapter3 withStance(StoryData.Chapter3 c, int stance) {
		return new StoryData.Chapter3(c.stage(), c.waitSince(), c.leads(), c.cairns(), c.cairnsTouched(), c.fall(), c.shrines(), c.reliquary(),
				c.boss(), c.outcome(), stance, c.echoDay(), c.echoClears());
	}

	/** A rewarded rematch on {@code day}: the echo day is set and the clears go up by one. */
	public static StoryData.Chapter3 withEchoClear(StoryData.Chapter3 c, int day) {
		return new StoryData.Chapter3(c.stage(), c.waitSince(), c.leads(), c.cairns(), c.cairnsTouched(), c.fall(), c.shrines(), c.reliquary(),
				c.boss(), c.outcome(), c.stance(), day, c.echoClears() + 1);
	}

	// ---------------------------------------------------------------- stages

	/**
	 * Moves the chapter to {@code to} if that is later than its stage now.
	 *
	 * @return whether the stage changed
	 */
	public static boolean advance(StoryData data, ChapterThreeStage to) {
		if (to.ordinal() <= data.chapter3.stage().ordinal()) {
			return false;
		}

		data.chapter3 = withStage(data.chapter3, to);
		return true;
	}

	/** Whether Chapter Two is finished, so the chapter should start waiting (6.1, 6.9). */
	public static boolean shouldWait(StoryData data) {
		if (data.chapter3.stage() != ChapterThreeStage.NOT_STARTED) {
			return false;
		}

		Path path = data.storyPath();
		boolean mentorDone = (path == Path.JEDI || path == Path.SITH) && data.stage() == PathStage.COMPLETE;
		return mentorDone || data.hasFlag(Story.NO_MASTER);
	}

	/** Whether the dream is due: the wait ran out, or a long sleep made it due. */
	public static boolean dreamDue(StoryData data, long gameTime, int waitTicks) {
		StoryData.Chapter3 c = data.chapter3;

		if (c.stage() != ChapterThreeStage.WAITING) {
			return false;
		}

		return data.hasFlag(DREAM_DUE) || (c.waitSince() >= 0 && gameTime - c.waitSince() >= waitTicks);
	}

	/** The stage with this name (case-insensitive, as dialogue conditions and commands write it), or null. */
	public static @Nullable ChapterThreeStage parseStage(String name) {
		for (ChapterThreeStage stage : ChapterThreeStage.values()) {
			if (stage.name().equalsIgnoreCase(name.trim())) {
				return stage;
			}
		}

		return null;
	}

	// ---------------------------------------------------------------- leads

	/**
	 * Adds a lead (kept from any stage; at most {@link StoryData.Chapter3#MAX_LEADS}).
	 *
	 * @return whether it was new and stored
	 */
	public static boolean addLead(StoryData data, String lead) {
		Set<String> leads = data.chapter3.leads();

		if (lead.isBlank() || leads.contains(lead) || leads.size() >= StoryData.Chapter3.MAX_LEADS) {
			return false;
		}

		Set<String> next = new LinkedHashSet<>(leads);
		next.add(lead);
		data.chapter3 = withLeads(data.chapter3, next);
		return true;
	}

	/** Lang key of a lead's journal line: {@code quest.oldrepublic.c3.lead.<lead with ':' as '.'>}. */
	public static String leadKey(String lead) {
		return "quest.oldrepublic.c3.lead." + lead.replace(':', '.');
	}

	/** Whether LEADS may end: enough leads and a high enough level. */
	public static boolean leadsReady(int leads, int level, ChapterThreeBalance b) {
		return leads >= b.leadsNeeded && level >= b.reliquaryMinLevel;
	}

	/** Whether the trail is clear but the level is not: the Reliquary waits (6.4). */
	public static boolean levelGated(int leads, int level, ChapterThreeBalance b) {
		return leads >= b.leadsNeeded && level < b.reliquaryMinLevel;
	}

	/**
	 * Where the Calling points while the level gate holds.
	 *
	 * @param callingVariant packed with its data ({@link Guidance#variant})
	 */
	public record GateTarget(BlockPos pos, int callingVariant) {
	}

	/**
	 * The level gate's Calling target: the nearest unused holocron chamber when there is one, else
	 * the nearest outpost board, else null.
	 *
	 * @param chamberKind the {@link HolocronKind} the chamber holds
	 * @param boardArmy   the {@code Faction} ordinal of the board's army
	 */
	public static @Nullable GateTarget gatedTarget(@Nullable BlockPos chamber, HolocronKind chamberKind, @Nullable BlockPos board, int boardArmy) {
		if (chamber != null) {
			return new GateTarget(chamber, Guidance.variant(QuestEffects.CALLING_HOLOCRON, chamberKind.ordinal()));
		}

		return board == null ? null : new GateTarget(board, Guidance.variant(QuestEffects.CALLING_OUTPOST, boardArmy));
	}

	// ---------------------------------------------------------------- cairns

	/** The touched mask with cairn {@code index} set; unchanged for an index outside the cairns. */
	public static int touch(int mask, int index, int cairns) {
		return index < 0 || index >= cairns ? mask : mask | (1 << index);
	}

	/** Cairns touched so far. */
	public static int touched(int mask) {
		return Integer.bitCount(mask);
	}

	// ---------------------------------------------------------------- holocrons

	/** The kind of a holocron id from its frozen prefix, or null for an Archive or unknown id. */
	public static @Nullable HolocronKind kindOf(String holocronId) {
		if (holocronId.startsWith("jedi_")) {
			return HolocronKind.JEDI;
		} else if (holocronId.startsWith("sith_")) {
			return HolocronKind.SITH;
		} else if (holocronId.startsWith("wayfarer")) {
			return HolocronKind.WAYFARER;
		}

		return null;
	}

	/** Whether a holocron id is one of the twelve world holocrons (not the Archive). */
	public static boolean isWorldHolocron(String holocronId) {
		return kindOf(holocronId) != null && JournalEntries.HOLOCRON_IDS.contains(holocronId);
	}

	/** The holocron kind a path's first echo looks for. */
	public static HolocronKind echoKind(Path path) {
		return switch (path) {
			case JEDI -> HolocronKind.JEDI;
			case SITH -> HolocronKind.SITH;
			default -> HolocronKind.WAYFARER;
		};
	}

	/** The worldgen site kinds that hold a pedestal of {@code kind} worth searching first. */
	public static List<SiteKind> echoSites(Path path) {
		return switch (path) {
			case JEDI -> List.of(SiteKind.JEDI_RUIN);
			case SITH -> List.of(SiteKind.SITH_TOMB);
			default -> List.of(SiteKind.WRECK_FIELD, SiteKind.JEDI_RUIN, SiteKind.SITH_TOMB);
		};
	}

	/**
	 * Whether attuning {@code holocronId} ends FIRST_ECHO for a player of {@code path}: a world
	 * holocron of the path's kind; for an Independent a Wayfarer holocron other than the Compass.
	 */
	public static boolean endsFirstEcho(Path path, String holocronId) {
		HolocronKind kind = kindOf(holocronId);
		return isWorldHolocron(holocronId) && kind == echoKind(path) && !WAYFARERS_COMPASS.equals(holocronId);
	}

	/**
	 * The first world holocron that already ends the first echo on entry (6.3: a Sith who found a
	 * tomb early), in the 4.2 order, or null.
	 */
	public static @Nullable String alreadyMet(Path path, Predicate<String> hasAttuned) {
		for (String id : JournalEntries.HOLOCRON_IDS) {
			if (endsFirstEcho(path, id) && hasAttuned.test(id)) {
				return id;
			}
		}

		return null;
	}

	/**
	 * The first-echo target (6.3): the pedestal the locator found, else an Echo Shrine placed now and
	 * appended to {@link StoryData.Chapter3#shrines}; null when neither worked (retry later). The
	 * caller commits.
	 */
	public static @Nullable BlockPos firstEchoTarget(StoryData data, @Nullable BlockPos found, Supplier<@Nullable BlockPos> placeShrine) {
		if (found != null) {
			return found;
		}

		if (data.chapter3.shrines().size() >= StoryData.Chapter3.MAX_SHRINES) {
			return data.chapter3.shrines().getLast();
		}

		BlockPos shrine = placeShrine.get();

		if (shrine != null) {
			data.chapter3 = withShrine(data.chapter3, shrine);
		}

		return shrine;
	}

	/**
	 * The old-world guarantee (4.3): at LEADS or later, with the holocron requirement of rank 3 (or
	 * the Pathfinder) still unmet, one extra Echo Shrine of the needed kind may be placed, once per
	 * kind ({@code extraShrinesPerKind}) and only while fewer than four shrines exist.
	 */
	public static boolean extraShrineNeeded(StoryData data, HolocronKind kind, int have, int need, ChapterThreeBalance b) {
		StoryData.Chapter3 c = data.chapter3;
		return c.stage().ordinal() >= ChapterThreeStage.LEADS.ordinal() && have < need && b.extraShrinesPerKind > 0
				&& !data.hasFlag(EXTRA_SHRINE_PREFIX + kind.name().toLowerCase(Locale.ROOT))
				&& c.shrines().size() < StoryData.Chapter3.MAX_SHRINES;
	}

	/** Records an extra Echo Shrine of {@code kind}: its flag and its position. The caller commits. */
	public static void recordExtraShrine(StoryData data, HolocronKind kind, BlockPos shrine) {
		data.flags.add(EXTRA_SHRINE_PREFIX + kind.name().toLowerCase(Locale.ROOT));
		data.chapter3 = withShrine(data.chapter3, shrine);
	}

	// ---------------------------------------------------------------- Collector

	/** Whether a co-op helper at this stage earns the owner's rewards and a stance of their own (8.9). */
	public static boolean coopStage(ChapterThreeStage stage) {
		return stage == ChapterThreeStage.RELIQUARY || stage == ChapterThreeStage.CONFRONTATION;
	}

	/**
	 * Whether a helper gets the full owner rewards from a defeat: still at RELIQUARY or
	 * CONFRONTATION, or already moved on by their own stance and not yet paid.
	 */
	public static boolean helperGetsFullRewards(StoryData data) {
		StoryData.Chapter3 c = data.chapter3;
		boolean stanceTaken = c.stage() == ChapterThreeStage.AFTERMATH && c.stance() >= 0;
		return !data.hasFlag(COLLECTOR_SP) && (coopStage(c.stage()) || stanceTaken);
	}

	/**
	 * Whether this player may still take their own stance (8.9), once: at RELIQUARY or CONFRONTATION,
	 * or in AFTERMATH when the owner's choice credited them before they picked (W5 reports every
	 * participant in the tick of the owner's choice, while their stance dialogue may still be open).
	 */
	public static boolean stanceOpen(StoryData data) {
		StoryData.Chapter3 c = data.chapter3;
		return c.stance() < 0 && (coopStage(c.stage()) || c.stage() == ChapterThreeStage.AFTERMATH && data.hasFlag(COOP_HELPER));
	}

	/** Whether the owner may end the fight this way on this path (8.7). */
	public static boolean resolveAllowed(Path path, BossOutcome outcome) {
		return switch (outcome) {
			case SPARED -> path == Path.JEDI || path == Path.SITH;
			case EXECUTED -> path != Path.NONE;
			case BOUND -> path == Path.SITH;
			case RELEASED, BROUGHT_TO_REPUBLIC, BROUGHT_TO_EMPIRE -> path == Path.INDEPENDENT;
			case ECHO -> false;
		};
	}

	/** Whether a co-op helper of this path may take this stance (8.9: spare, execute, bind, release). */
	public static boolean stanceAllowed(Path path, BossOutcome stance) {
		return switch (stance) {
			case SPARED, EXECUTED, BOUND -> resolveAllowed(path, stance);
			case RELEASED -> path == Path.INDEPENDENT;
			default -> false;
		};
	}

	/** The outcome named by a dialogue argument ({@code spare}, {@code bring_in_republic}...), or null. */
	public static @Nullable BossOutcome outcomeOf(String arg) {
		return switch (arg.trim().toLowerCase(Locale.ROOT)) {
			case "spare" -> BossOutcome.SPARED;
			case "execute" -> BossOutcome.EXECUTED;
			case "bind" -> BossOutcome.BOUND;
			case "release" -> BossOutcome.RELEASED;
			case "bring_in_republic" -> BossOutcome.BROUGHT_TO_REPUBLIC;
			case "bring_in_imperial" -> BossOutcome.BROUGHT_TO_EMPIRE;
			default -> null;
		};
	}

	/** The identity choice id an outcome records: {@code collector.spare|execute|bind|release|bring_in}. */
	public static String choiceId(BossOutcome outcome) {
		return "collector." + switch (outcome) {
			case SPARED -> "spare";
			case EXECUTED -> "execute";
			case BOUND -> "bind";
			case RELEASED -> "release";
			case BROUGHT_TO_REPUBLIC, BROUGHT_TO_EMPIRE -> "bring_in";
			case ECHO -> "echo";
		};
	}

	/** The journal entry an outcome unlocks (a bring-in is custody: spared to the Republic, bound to the Empire). */
	public static String outcomeEntry(BossOutcome outcome) {
		return switch (outcome) {
			case SPARED, BROUGHT_TO_REPUBLIC -> JournalEntries.COLLECTOR_SPARED;
			case EXECUTED -> JournalEntries.COLLECTOR_EXECUTED;
			case BOUND, BROUGHT_TO_EMPIRE -> JournalEntries.COLLECTOR_BOUND;
			case RELEASED, ECHO -> JournalEntries.COLLECTOR_RELEASED;
		};
	}

	/** Whether a helper's own stance clashes with the owner's world outcome (the report's extra line). */
	public static boolean clashes(int stance, int outcome) {
		BossOutcome own = BossOutcome.of(stance);
		BossOutcome world = BossOutcome.of(outcome);

		if (own == null || world == null) {
			return false;
		}

		return clashGroup(own) != clashGroup(world);
	}

	/** 0 he lives free, 1 he is in someone's custody, 2 he is dead. */
	private static int clashGroup(BossOutcome outcome) {
		return switch (outcome) {
			case EXECUTED -> 2;
			case BOUND, BROUGHT_TO_REPUBLIC, BROUGHT_TO_EMPIRE -> 1;
			case SPARED, RELEASED, ECHO -> 0;
		};
	}

	/** Whether the "other helper" reward is open today ({@code lastDay} -1 when never paid). */
	public static boolean helperRewardDue(int lastDay, int today) {
		return lastDay != today;
	}

	/** Whether a rematch pays today. */
	public static boolean echoRewardDue(StoryData.Chapter3 c, int today) {
		return c.echoDay() != today;
	}

	/**
	 * Whether a rewarded rematch gives the emitter: it rolled, or this is the {@code pityAt}-th clear
	 * in a row without one (8.9). The pity counter resets to 0 when it does, else goes up by one.
	 */
	public static boolean emitterDue(int pityBefore, boolean rolled, int pityAt) {
		return rolled || pityBefore + 1 >= pityAt;
	}

	// ---------------------------------------------------------------- report

	/**
	 * Whether the report is open: AFTERMATH, the hoard decided, not yet reported, and the right
	 * reader ({@code mentor}: Jedi and Sith; otherwise Independents at an officer).
	 */
	public static boolean reportReady(StoryData data, Path path, boolean mentor) {
		if (data.chapter3.stage() != ChapterThreeStage.AFTERMATH || !data.hasFlag(HOARD_DECIDED) || data.hasFlag(REPORT_DONE)) {
			return false;
		}

		return mentor ? path == Path.JEDI || path == Path.SITH : path == Path.INDEPENDENT;
	}

	/** Whether an officer of an army at this tier takes the Independent's report (any tier above Hostile, 5.3). */
	public static boolean officerTakesReport(ReputationTier tier) {
		return tier != ReputationTier.HOSTILE;
	}

	/** "Send word" from the journal: an Independent whose report is open while both armies are Hostile (5.3). */
	public static boolean sendWordEligible(StoryData data, Path path, ReputationTier republic, ReputationTier empire) {
		return reportReady(data, path, false) && republic == ReputationTier.HOSTILE && empire == ReputationTier.HOSTILE;
	}

	/** A late join is refused from Chapter Three SUMMONS on (6.9). */
	public static boolean lateJoinRefused(StoryData data) {
		return data.chapter3.stage().ordinal() >= ChapterThreeStage.SUMMONS.ordinal();
	}

	// ---------------------------------------------------------------- rank 3

	/**
	 * One rank 3 requirement as the Standing checklist shows it and the dialogue names it.
	 *
	 * @param id   {@code trial}, {@code level}, {@code trust}, {@code reputation}, {@code alignment},
	 *             {@code holocrons}, {@code trusted} or {@code contracts}
	 * @param have the player's value (for alignment, its distance past zero in the path's direction)
	 */
	public record Requirement(String id, boolean met, int have, int need) {
		/** The dialogue fail reason: a lang key with its arguments ({@code key|need}). */
		public String failReason() {
			return RANK3_REQ + id + "|" + need;
		}
	}

	/** What a player's rank 3 checks read, gathered on either side. */
	public record Standing(Path path, ChapterThreeStage stage, int level, int trust, int ownRep, int alignment, int ownHolocrons,
			int anyHolocrons, int bestRep, int contracts) {
	}

	/**
	 * Every requirement of the player's rank 3 (Knight or Lord) or Pathfinder title, in the order the
	 * dialogue names a failure (5.8). Empty for a player with no path.
	 */
	public static List<Requirement> requirements(Standing s, ChapterThreeBalance b) {
		List<Requirement> out = new ArrayList<>();
		boolean trial = s.stage() == ChapterThreeStage.COMPLETE;

		switch (s.path()) {
			case JEDI, SITH -> {
				int lean = s.path() == Path.JEDI ? s.alignment() : -s.alignment();
				out.add(new Requirement("trial", trial, trial ? 1 : 0, 1));
				out.add(new Requirement("level", s.level() >= b.rank3MinLevel, s.level(), b.rank3MinLevel));
				out.add(new Requirement("trust", s.trust() >= b.rank3MinTrust, s.trust(), b.rank3MinTrust));
				out.add(new Requirement("reputation", s.ownRep() >= b.rank3MinRep, s.ownRep(), b.rank3MinRep));
				out.add(new Requirement("alignment", lean >= b.rank3Alignment, lean, b.rank3Alignment));
				out.add(new Requirement("holocrons", s.ownHolocrons() >= b.rank3Holocrons, s.ownHolocrons(), b.rank3Holocrons));
				out.add(new Requirement("contracts", s.contracts() >= b.rank3Contracts, s.contracts(), b.rank3Contracts));
			}
			case INDEPENDENT -> {
				out.add(new Requirement("trial", trial, trial ? 1 : 0, 1));
				out.add(new Requirement("level", s.level() >= b.rank3MinLevel, s.level(), b.rank3MinLevel));
				out.add(new Requirement("holocrons", s.anyHolocrons() >= b.pathfinderHolocrons, s.anyHolocrons(), b.pathfinderHolocrons));
				out.add(new Requirement("trusted", s.bestRep() >= b.pathfinderTier, s.bestRep(), b.pathfinderTier));
				out.add(new Requirement("contracts", s.contracts() >= b.pathfinderContracts, s.contracts(), b.pathfinderContracts));
			}
			case NONE -> {
			}
		}

		return out;
	}

	/** The first unmet requirement's fail reason, or null when every one is met (none for no path). */
	public static @Nullable String rank3Blocker(Standing s, ChapterThreeBalance b) {
		List<Requirement> requirements = requirements(s, b);

		if (requirements.isEmpty()) {
			return RANK3_REQ + "no_path";
		}

		for (Requirement requirement : requirements) {
			if (!requirement.met()) {
				return requirement.failReason();
			}
		}

		return null;
	}

	// ---------------------------------------------------------------- journal

	/**
	 * Lang key of the Chapter Three objective for the journal's left page and the Standing tab, or
	 * null when nothing is shown (NOT_STARTED, WAITING).
	 */
	public static @Nullable String objectiveKey(StoryData data, int level, ChapterThreeBalance b) {
		StoryData.Chapter3 c = data.chapter3;
		Path path = data.storyPath();
		String base = "quest.oldrepublic.c3.objective.";
		String who = path == Path.INDEPENDENT ? "independent" : path == Path.SITH ? "sith" : "jedi";

		return switch (c.stage()) {
			case NOT_STARTED, WAITING -> null;
			case SUMMONS -> base + "summons." + who;
			case LISTENING -> base + "listening";
			case FIRST_ECHO -> base + "first_echo." + who;
			case LEADS -> levelGated(c.leads().size(), level, b) ? base + "leads.gated" : base + "leads";
			case RELIQUARY -> base + "reliquary";
			case CONFRONTATION -> base + "confrontation";
			case AFTERMATH -> data.hasFlag(HOARD_DECIDED) ? base + "report." + who : base + "hoard";
			case COMPLETE -> base + "complete";
		};
	}

	/**
	 * The Chapter Three objective with its counts filled in (cairns found, leads, the level the gate
	 * waits for, the recommended level), or null when nothing is shown ({@link #objectiveKey}).
	 */
	public static @Nullable Component objective(StoryData data, int level, ChapterThreeBalance b) {
		String key = objectiveKey(data, level, b);

		if (key == null) {
			return null;
		}

		StoryData.Chapter3 c = data.chapter3;
		int leads = c.leads().size();

		return switch (c.stage()) {
			case LISTENING -> Component.translatable(key, touched(c.cairnsTouched()), c.cairns().isEmpty() ? b.cairnCount : c.cairns().size());
			case LEADS -> levelGated(leads, level, b) ? Component.translatable(key, b.reliquaryMinLevel)
					: Component.translatable(key, Math.min(leads, b.leadsNeeded), b.leadsNeeded);
			case RELIQUARY -> Component.translatable(key, b.recommendedLevel);
			default -> Component.translatable(key);
		};
	}

	/** The journal notes stored for {@code entryId}, as lang keys sorted by key (4.4). */
	public static List<String> notes(StoryData data, String entryId) {
		String prefix = NOTE_PREFIX + entryId + "|";
		return data.flags.stream().filter(flag -> flag.startsWith(prefix)).map(flag -> flag.substring(prefix.length())).sorted().toList();
	}

	private ChapterThreeRules() {
	}
}
