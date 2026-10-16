package com.oldrepublic.quest.story;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.identity.Path;
import com.oldrepublic.progression.ForceData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * Everything the story remembers about one player's Chapter Two and Chapter Three. Lives in the
 * persistent, copy-on-death {@link StoryAttachments#STORY} attachment, synced to the owning client
 * only (the journal and the Standing tab read it).
 *
 * <p>Mutable on the server; call {@link Story#commit} after changing it. Stages only move forward
 * (see {@link StoryRules}).
 */
public final class StoryData {
	private record Stages(int crossing, int path, String pathId, long waitSince, long decidedAt, int sessionDay, int atoneDay,
			long trialRetryAt, long summonAt, String summonRole) {
		static final Codec<Stages> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("crossing_stage", 0).forGetter(Stages::crossing),
				Codec.INT.optionalFieldOf("path_stage", 0).forGetter(Stages::path),
				Codec.STRING.optionalFieldOf("path", "none").forGetter(Stages::pathId),
				Codec.LONG.optionalFieldOf("wait_since", -1L).forGetter(Stages::waitSince),
				Codec.LONG.optionalFieldOf("decided_at", -1L).forGetter(Stages::decidedAt),
				Codec.INT.optionalFieldOf("session_day", -1).forGetter(Stages::sessionDay),
				Codec.INT.optionalFieldOf("atone_day", -1).forGetter(Stages::atoneDay),
				Codec.LONG.optionalFieldOf("trial_retry_at", 0L).forGetter(Stages::trialRetryAt),
				Codec.LONG.optionalFieldOf("summon_at", -1L).forGetter(Stages::summonAt),
				Codec.STRING.optionalFieldOf("summon_role", "").forGetter(Stages::summonRole)
		).apply(i, Stages::new));
	}

	private record Places(String meetingDim, Optional<BlockPos> meeting, Optional<BlockPos> camp) {
		static final Codec<Places> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("meeting_dim", "").forGetter(Places::meetingDim),
				BlockPos.CODEC.optionalFieldOf("meeting").forGetter(Places::meeting),
				BlockPos.CODEC.optionalFieldOf("camp").forGetter(Places::camp)
		).apply(i, Places::new));
	}

	private record Npcs(Optional<UUID> jedi, Optional<UUID> sith, Optional<UUID> rival) {
		static final Codec<Npcs> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.optionalFieldOf("jedi_mentor").forGetter(Npcs::jedi),
				UUIDUtil.CODEC.optionalFieldOf("sith_mentor").forGetter(Npcs::sith),
				UUIDUtil.CODEC.optionalFieldOf("rival").forGetter(Npcs::rival)
		).apply(i, Npcs::new));
	}

	private record Encounter(Optional<BlockPos> pos, int state, int captain, int captives, Optional<UUID> captainId, long spawnedAt) {
		static final Codec<Encounter> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.optionalFieldOf("pos").forGetter(Encounter::pos),
				Codec.INT.optionalFieldOf("state", 0).forGetter(Encounter::state),
				Codec.INT.optionalFieldOf("captain", 0).forGetter(Encounter::captain),
				Codec.INT.optionalFieldOf("captives", 0).forGetter(Encounter::captives),
				UUIDUtil.CODEC.optionalFieldOf("captain_id").forGetter(Encounter::captainId),
				Codec.LONG.optionalFieldOf("spawned_at", -1L).forGetter(Encounter::spawnedAt)
		).apply(i, Encounter::new));
	}

	/**
	 * Chapter Three, "The Ones That Sing" (spec sections 6 and 12.1). Immutable: change it by storing
	 * a new value in {@link #chapter3} and committing, so a sealed copy can share it safely. Other
	 * features read it only through {@link ChapterThree}. Every field is optional in the codec, so
	 * Milestone 2 saves load with {@link #NEW}.
	 *
	 * @param stage the chapter's stage, saved by ordinal
	 * @param waitSince game time WAITING began, or -1
	 * @param leads lead ids ({@code ledger}, {@code holo:<id>}, {@code mission:<id>}, {@code contract}),
	 *     in the order found; at most {@link #MAX_LEADS}
	 * @param cairns the player's cairns or caches; at most {@link #MAX_CAIRNS}
	 * @param cairnsTouched bit {@code i} set once cairn {@code i} has been touched
	 * @param fall Courier's Fall (Independents), or null
	 * @param shrines Echo Shrines placed for the player; at most {@link #MAX_SHRINES}
	 * @param reliquary the Reliquary's heart, or null
	 * @param boss 0 none, 1 yielded, 2 resolved
	 * @param outcome the owner's {@link com.oldrepublic.quest.BossOutcome} ordinal, or -1
	 * @param stance a co-op helper's own {@link com.oldrepublic.quest.BossOutcome} ordinal, or -1
	 * @param echoDay game-day index of the last rematch against the echo, or -1
	 * @param echoClears rematches won
	 */
	public record Chapter3(ChapterThreeStage stage, long waitSince, Set<String> leads, List<BlockPos> cairns, int cairnsTouched,
			@Nullable BlockPos fall, List<BlockPos> shrines, @Nullable BlockPos reliquary, int boss, int outcome, int stance,
			int echoDay, int echoClears) {
		public static final int MAX_LEADS = 16;
		public static final int MAX_CAIRNS = 3;
		public static final int MAX_SHRINES = 4;
		/** A player who has not begun Chapter Three. */
		public static final Chapter3 NEW = new Chapter3(ChapterThreeStage.NOT_STARTED, -1L, Set.of(), List.of(), 0, null, List.of(), null, 0, -1, -1, -1, 0);

		private static final Codec<ChapterThreeStage> STAGE_CODEC = Codec.INT.xmap(ChapterThreeStage::of, ChapterThreeStage::ordinal);
		private static final Codec<Set<String>> LEADS_CODEC = Codec.STRING.listOf().xmap(LinkedHashSet::new, List::copyOf);

		// API-CHECK: RecordCodecBuilder.Instance#group with 13 fields (DataFixerUpper goes to 16; the mod's largest so far is 11).
		public static final Codec<Chapter3> CODEC = RecordCodecBuilder.create(i -> i.group(
				STAGE_CODEC.optionalFieldOf("stage", ChapterThreeStage.NOT_STARTED).forGetter(Chapter3::stage),
				Codec.LONG.optionalFieldOf("wait_since", -1L).forGetter(Chapter3::waitSince),
				LEADS_CODEC.optionalFieldOf("leads", Set.of()).forGetter(Chapter3::leads),
				BlockPos.CODEC.listOf().optionalFieldOf("cairns", List.of()).forGetter(Chapter3::cairns),
				Codec.INT.optionalFieldOf("cairns_touched", 0).forGetter(Chapter3::cairnsTouched),
				BlockPos.CODEC.optionalFieldOf("fall").forGetter(c -> Optional.ofNullable(c.fall())),
				BlockPos.CODEC.listOf().optionalFieldOf("shrines", List.of()).forGetter(Chapter3::shrines),
				BlockPos.CODEC.optionalFieldOf("reliquary").forGetter(c -> Optional.ofNullable(c.reliquary())),
				Codec.INT.optionalFieldOf("boss", 0).forGetter(Chapter3::boss),
				Codec.INT.optionalFieldOf("outcome", -1).forGetter(Chapter3::outcome),
				Codec.INT.optionalFieldOf("stance", -1).forGetter(Chapter3::stance),
				Codec.INT.optionalFieldOf("echo_day", -1).forGetter(Chapter3::echoDay),
				Codec.INT.optionalFieldOf("echo_clears", 0).forGetter(Chapter3::echoClears)
		).apply(i, Chapter3::decode));

		/** Copies the collections into unmodifiable ones and drops entries beyond their limits. */
		public Chapter3 {
			leads = Collections.unmodifiableSet(new LinkedHashSet<>(leads.stream().limit(MAX_LEADS).toList()));
			cairns = List.copyOf(cairns.subList(0, Math.min(cairns.size(), MAX_CAIRNS)));
			shrines = List.copyOf(shrines.subList(0, Math.min(shrines.size(), MAX_SHRINES)));
		}

		private static Chapter3 decode(ChapterThreeStage stage, long waitSince, Set<String> leads, List<BlockPos> cairns, int cairnsTouched,
				Optional<BlockPos> fall, List<BlockPos> shrines, Optional<BlockPos> reliquary, int boss, int outcome, int stance,
				int echoDay, int echoClears) {
			return new Chapter3(stage, waitSince, leads, cairns, cairnsTouched, fall.orElse(null), shrines, reliquary.orElse(null), boss, outcome,
					stance, echoDay, echoClears);
		}
	}

	public static final Codec<StoryData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Stages.CODEC.fieldOf("stages").forGetter(StoryData::stages),
			Places.CODEC.optionalFieldOf("places", new Places("", Optional.empty(), Optional.empty())).forGetter(StoryData::places),
			Npcs.CODEC.optionalFieldOf("npcs", new Npcs(Optional.empty(), Optional.empty(), Optional.empty())).forGetter(StoryData::npcs),
			Encounter.CODEC.optionalFieldOf("encounter", new Encounter(Optional.empty(), 0, 0, 0, Optional.empty(), -1L)).forGetter(StoryData::encounter),
			ForceData.INT_MAP.optionalFieldOf("counters", Map.of()).forGetter(d -> d.counters),
			ForceData.STRING_SET.optionalFieldOf("flags", Set.of()).forGetter(d -> d.flags),
			Chapter3.CODEC.optionalFieldOf("chapter3", Chapter3.NEW).forGetter(d -> d.chapter3)
	).apply(i, StoryData::new));

	/** {@link CrossingStage} ordinal. */
	public int crossingStage;
	/** {@link PathStage} ordinal. */
	public int pathStage;
	/**
	 * The path the story follows ({@link Path#id()}): set by the Crossing choice or a late join,
	 * and kept in step with the identity path through {@code QuestHooks.onPathChanged}.
	 */
	public String path = Path.NONE.id();
	/** Game time WAITING began, or -1. */
	public long waitSince = -1;
	/** Game time the Crossing choice was made, or -1. */
	public long decidedAt = -1;
	/** Game-day index of the last daily mentor session, or -1. */
	public int sessionDay = -1;
	/** Game-day index of the last atonement or embrace session, or -1. */
	public int atoneDay = -1;
	/** Game time before which a lost trial may not be retried. */
	public long trialRetryAt;
	/** Game time a summoned mentor arrives, or -1 when none is on the way. */
	public long summonAt = -1;
	/** Mentor role ({@code jedi} or {@code sith}) of the pending summon, or empty. */
	public String summonRole = "";

	/** Dimension id of the meeting point (overworld in practice), or empty. */
	public String meetingDim = "";
	public @Nullable BlockPos meeting;
	/** The mentor's camp anchor, or null before a path is chosen. */
	public @Nullable BlockPos camp;

	public @Nullable UUID jediMentor;
	public @Nullable UUID sithMentor;
	/** Brenn Ostrak, the Sith path's rival acolyte. */
	public @Nullable UUID rival;

	/** Smoke on the Ridge: where the raider camp is. */
	public @Nullable BlockPos encounterPos;
	/** {@link EncounterState} ordinal. */
	public int encounterState;
	/** {@link CaptainFate} ordinal. */
	public int captain;
	/** {@link CaptivesFate} ordinal. */
	public int captives;
	public @Nullable UUID captainId;
	/** Game time the encounter's entities spawned, or -1. */
	public long encounterSpawnedAt = -1;

	/** Named progress counters, such as lesson progress. */
	public final Map<String, Integer> counters = new HashMap<>();
	/** One-shot story facts (lesson begun, debrief heard, Brenn spared...). */
	public final Set<String> flags = new HashSet<>();

	/** Chapter Three progress; immutable, so replace it and commit (see {@link Chapter3}). */
	public Chapter3 chapter3 = Chapter3.NEW;

	/** Frozen copy for syncing (see {@link #syncView()}); never copied, persisted or mutated. */
	private volatile @Nullable StoryData syncView;

	public StoryData() {
	}

	private StoryData(Stages stages, Places places, Npcs npcs, Encounter encounter, Map<String, Integer> counters, Set<String> flags,
			Chapter3 chapter3) {
		this.crossingStage = stages.crossing();
		this.pathStage = stages.path();
		this.path = stages.pathId();
		this.waitSince = stages.waitSince();
		this.decidedAt = stages.decidedAt();
		this.sessionDay = stages.sessionDay();
		this.atoneDay = stages.atoneDay();
		this.trialRetryAt = stages.trialRetryAt();
		this.summonAt = stages.summonAt();
		this.summonRole = stages.summonRole();
		this.meetingDim = places.meetingDim();
		this.meeting = places.meeting().orElse(null);
		this.camp = places.camp().orElse(null);
		this.jediMentor = npcs.jedi().orElse(null);
		this.sithMentor = npcs.sith().orElse(null);
		this.rival = npcs.rival().orElse(null);
		this.encounterPos = encounter.pos().orElse(null);
		this.encounterState = encounter.state();
		this.captain = encounter.captain();
		this.captives = encounter.captives();
		this.captainId = encounter.captainId().orElse(null);
		this.encounterSpawnedAt = encounter.spawnedAt();
		this.counters.putAll(counters);
		this.flags.addAll(flags);
		this.chapter3 = chapter3;
	}

	private Stages stages() {
		return new Stages(crossingStage, pathStage, path, waitSince, decidedAt, sessionDay, atoneDay, trialRetryAt, summonAt, summonRole);
	}

	private Places places() {
		return new Places(meetingDim, Optional.ofNullable(meeting), Optional.ofNullable(camp));
	}

	private Npcs npcs() {
		return new Npcs(Optional.ofNullable(jediMentor), Optional.ofNullable(sithMentor), Optional.ofNullable(rival));
	}

	private Encounter encounter() {
		return new Encounter(Optional.ofNullable(encounterPos), encounterState, captain, captives, Optional.ofNullable(captainId), encounterSpawnedAt);
	}

	public StoryData copy() {
		return new StoryData(stages(), places(), npcs(), encounter(), counters, flags, chapter3);
	}

	/**
	 * Takes the frozen copy that syncing sends ({@link #syncView()}) and returns this. Server
	 * thread only; {@link Story#commit}, the attachment initializer and loading call it.
	 */
	StoryData seal() {
		this.syncView = copy();
		return this;
	}

	/**
	 * What the network sends for this object: the copy taken by the last {@link #seal()}, which
	 * nothing ever mutates. Fabric encodes attachment syncs later, on the network thread, so it must
	 * never iterate the live maps and sets the server thread keeps changing in place.
	 */
	StoryData syncView() {
		StoryData view = syncView;
		return view != null ? view : this;
	}

	public CrossingStage crossing() {
		return CrossingStage.of(crossingStage);
	}

	public PathStage stage() {
		return PathStage.of(pathStage);
	}

	public Path storyPath() {
		return Path.byId(path);
	}

	public EncounterState encounterState() {
		return EncounterState.of(encounterState);
	}

	public CaptainFate captainFate() {
		return CaptainFate.of(captain);
	}

	public CaptivesFate captivesFate() {
		return CaptivesFate.of(captives);
	}

	/** The UUID of this player's mentor for {@code role} ({@code jedi} or {@code sith}). */
	public @Nullable UUID mentor(String role) {
		return "sith".equals(role) ? sithMentor : jediMentor;
	}

	public void setMentor(String role, @Nullable UUID id) {
		if ("sith".equals(role)) {
			sithMentor = id;
		} else {
			jediMentor = id;
		}
	}

	public int counter(String id) {
		return counters.getOrDefault(id, 0);
	}

	public boolean hasFlag(String flag) {
		return flags.contains(flag);
	}
}
