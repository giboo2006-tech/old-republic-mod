package com.oldrepublic.quest.story;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.quest.BossOutcome;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

/**
 * Everything the story remembers about one player's Chapters Two to Five and their turning (the Fall
 * or the Return, spec MILESTONE_5.md 8). Lives in the
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

	/**
	 * Chapter Four progress (spec MILESTONE_4.md 8, 12.1). Immutable, like {@link Chapter3}: replace
	 * it and commit. Phase 0 built {@code stage}, {@code wait_since}, {@code arc} and {@code hunt}
	 * (what the frozen {@link ChapterFour} accessor reads); the quest worker added {@code duel},
	 * {@code sites} and {@code back_pay}. Nested records keep every codec under 16 fields, and every
	 * field is optional, so a Milestone 3 save decodes to {@link #NEW}.
	 *
	 * @param waitSince game time the current stage began waiting, or -1
	 * @param duel the hunting camp and the duel's state (8.6)
	 * @param sites story places the chapter itself remembers (the marker beacon, the ambush spots)
	 * @param backPay whether the Milestone 3 back pay (8.8) was settled
	 */
	public record Chapter4(ChapterFourStage stage, long waitSince, Arc arc, Hunt hunt, Duel duel, Sites sites, boolean backPay) {
		/** A player who has not begun Chapter Four. */
		public static final Chapter4 NEW = new Chapter4(ChapterFourStage.NOT_STARTED, -1L, Arc.NONE, Hunt.NONE, Duel.NONE, Sites.NONE, false);

		/** The Phase 0 shape: no duel, no sites, back pay not settled. */
		public Chapter4(ChapterFourStage stage, long waitSince, Arc arc, Hunt hunt) {
			this(stage, waitSince, arc, hunt, Duel.NONE, Sites.NONE, false);
		}

		/**
		 * The world arc (8.4).
		 *
		 * @param planet the {@link Planets.Planet} id, or empty before the first landing
		 * @param missionFlags the arc's finished steps, a bit each ({@link ChapterFourRules})
		 * @param expedition 0 not done, 1 done, 2 skipped
		 */
		public record Arc(String planet, int missionFlags, int expedition) {
			public static final Arc NONE = new Arc("", 0, 0);
			public static final Codec<Arc> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.STRING.optionalFieldOf("planet", "").forGetter(Arc::planet),
					Codec.INT.optionalFieldOf("mission_flags", 0).forGetter(Arc::missionFlags),
					Codec.INT.optionalFieldOf("expedition", 0).forGetter(Arc::expedition)
			).apply(i, Arc::new));

			public Arc withPlanet(String newPlanet) {
				return new Arc(newPlanet, missionFlags, expedition);
			}

			public Arc withMissionFlags(int newFlags) {
				return new Arc(planet, newFlags, expedition);
			}

			public Arc withExpedition(int newExpedition) {
				return new Arc(planet, missionFlags, newExpedition);
			}

			/** Whether every bit of {@code mask} is set. */
			public boolean has(int mask) {
				return (missionFlags & mask) == mask;
			}
		}

		/**
		 * The hunt (8.5).
		 *
		 * @param clues the clues held, a bit per clue (bit 0 is clue 1)
		 * @param ambushDay game-day index of the first ambush, or -1
		 * @param ambushes ambushes beaten
		 */
		public record Hunt(int clues, boolean sabotageDone, long ambushDay, int ambushes) {
			public static final Hunt NONE = new Hunt(0, false, -1L, 0);
			public static final Codec<Hunt> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.INT.optionalFieldOf("clues", 0).forGetter(Hunt::clues),
					Codec.BOOL.optionalFieldOf("sabotage_done", false).forGetter(Hunt::sabotageDone),
					Codec.LONG.optionalFieldOf("ambush_day", -1L).forGetter(Hunt::ambushDay),
					Codec.INT.optionalFieldOf("ambushes", 0).forGetter(Hunt::ambushes)
			).apply(i, Hunt::new));

			public Hunt withClues(int newClues) {
				return new Hunt(newClues, sabotageDone, ambushDay, ambushes);
			}

			public Hunt withSabotage() {
				return new Hunt(clues, true, ambushDay, ambushes);
			}

			public Hunt withAmbush(long day, int beaten) {
				return new Hunt(clues, sabotageDone, day, beaten);
			}
		}

		/**
		 * The duel (8.6, 10.6).
		 *
		 * @param camp the hunting camp's anchor, or null before it is placed
		 * @param outcome the owner's {@link KaastOutcome} ordinal, or -1
		 * @param boss 0 not yielded, 1 yielded, 2 resolved
		 */
		public record Duel(@Nullable GlobalPos camp, int outcome, int boss) {
			public static final Duel NONE = new Duel(null, -1, 0);
			public static final Codec<Duel> CODEC = RecordCodecBuilder.create(i -> i.group(
					GlobalPos.CODEC.optionalFieldOf("camp").forGetter(d -> Optional.ofNullable(d.camp())),
					Codec.INT.optionalFieldOf("outcome", -1).forGetter(Duel::outcome),
					Codec.INT.optionalFieldOf("boss", 0).forGetter(Duel::boss)
			).apply(i, (camp, outcome, boss) -> new Duel(camp.orElse(null), outcome, boss)));

			public Duel withCamp(@Nullable GlobalPos newCamp) {
				return new Duel(newCamp, outcome, boss);
			}

			public Duel withBoss(int newBoss, int newOutcome) {
				return new Duel(camp, newOutcome, newBoss);
			}

			/** The owner's choice, or null before it is made. */
			public @Nullable KaastOutcome kaastOutcome() {
				KaastOutcome[] values = KaastOutcome.values();
				return outcome >= 0 && outcome < values.length ? values[outcome] : null;
			}
		}

		/**
		 * Story places Chapter Four remembers itself, by name ({@code beacon}, {@code ambush_one},
		 * {@code ambush_two}); at most {@link #MAX}. Sites placed by other features are found through
		 * {@code PlanetSites.find} and are not copied here.
		 */
		public record Sites(Map<String, GlobalPos> places) {
			public static final int MAX = 8;
			public static final Sites NONE = new Sites(Map.of());
			public static final Codec<Sites> CODEC = Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC).xmap(Sites::new, Sites::places);

			/** Copies the map, keeping at most {@link #MAX} entries. */
			public Sites {
				Map<String, GlobalPos> copy = new LinkedHashMap<>();

				for (Map.Entry<String, GlobalPos> entry : places.entrySet()) {
					if (copy.size() < MAX) {
						copy.put(entry.getKey(), entry.getValue());
					}
				}

				places = Collections.unmodifiableMap(copy);
			}

			public @Nullable GlobalPos get(String name) {
				return places.get(name);
			}

			public Sites with(String name, @Nullable GlobalPos pos) {
				Map<String, GlobalPos> copy = new LinkedHashMap<>(places);

				if (pos == null) {
					copy.remove(name);
				} else {
					copy.remove(name);
					copy.put(name, pos);
				}

				return new Sites(copy);
			}
		}

		private static final Codec<ChapterFourStage> STAGE_CODEC = Codec.INT.xmap(ChapterFourStage::of, ChapterFourStage::ordinal);

		public static final Codec<Chapter4> CODEC = RecordCodecBuilder.create(i -> i.group(
				STAGE_CODEC.optionalFieldOf("stage", ChapterFourStage.NOT_STARTED).forGetter(Chapter4::stage),
				Codec.LONG.optionalFieldOf("wait_since", -1L).forGetter(Chapter4::waitSince),
				Arc.CODEC.optionalFieldOf("arc", Arc.NONE).forGetter(Chapter4::arc),
				Hunt.CODEC.optionalFieldOf("hunt", Hunt.NONE).forGetter(Chapter4::hunt),
				Duel.CODEC.optionalFieldOf("duel", Duel.NONE).forGetter(Chapter4::duel),
				Sites.CODEC.optionalFieldOf("sites", Sites.NONE).forGetter(Chapter4::sites),
				Codec.BOOL.optionalFieldOf("back_pay", false).forGetter(Chapter4::backPay)
		).apply(i, Chapter4::new));

		/** The arc's planet, or null. */
		public Planets.@Nullable Planet arcPlanet() {
			return arc.planet().isEmpty() ? null : Planets.Planet.byId(arc.planet());
		}

		public Chapter4 withStage(ChapterFourStage newStage, long newWaitSince) {
			return new Chapter4(newStage, newWaitSince, arc, hunt, duel, sites, backPay);
		}

		public Chapter4 withArc(Arc newArc) {
			return new Chapter4(stage, waitSince, newArc, hunt, duel, sites, backPay);
		}

		public Chapter4 withHunt(Hunt newHunt) {
			return new Chapter4(stage, waitSince, arc, newHunt, duel, sites, backPay);
		}

		public Chapter4 withDuel(Duel newDuel) {
			return new Chapter4(stage, waitSince, arc, hunt, newDuel, sites, backPay);
		}

		public Chapter4 withSites(Sites newSites) {
			return new Chapter4(stage, waitSince, arc, hunt, duel, newSites, backPay);
		}

		public Chapter4 withBackPay() {
			return new Chapter4(stage, waitSince, arc, hunt, duel, sites, true);
		}
	}

	/**
	 * Chapter Five, "The Long Hymn" (spec MILESTONE_5.md 6, 14.1). Immutable, like {@link Chapter4}:
	 * replace it and commit. Phase 0 built the whole record so the frozen {@link ChapterFive}
	 * accessor can read it; the story worker (W4) fills it. Every field is optional, so a Milestone 4
	 * save decodes to {@link #NEW}.
	 *
	 * @param stage the chapter's stage, saved by ordinal
	 * @param waitSince game time the current stage began waiting, or -1
	 * @param ways the Saltgate ways completed, a bit per {@link ChapterFive.Way} ordinal (6.3)
	 * @param way the {@link ChapterFive.Way} ordinal of the way that finished first (the counting-house
	 *     follows it), or -1
	 * @param dask Dask's fate (6.4): {@link #DASK_NONE}, {@link #DASK_RELEASE}, {@link #DASK_CLAN} or
	 *     {@link #DASK_END}
	 * @param blockadeFailures how often the owner's ship was disabled in the blockade (6.5)
	 * @param verses the verse stones silenced, a bit per stone index (6.6)
	 * @param ulvaine the owner's {@link BossOutcome} ordinal over Ulvaine, or -1 (6.7)
	 * @param boss 0 not yielded, 1 yielded, 2 resolved
	 */
	public record Chapter5(ChapterFiveStage stage, long waitSince, int ways, int way, int dask, int blockadeFailures, int verses, int ulvaine,
			int boss) {
		/** Dask's fate is not chosen yet. */
		public static final int DASK_NONE = -1;
		/** "Let him go" ({@code c5:dask release}). */
		public static final int DASK_RELEASE = 0;
		/** "Hand him to the clan" ({@code c5:dask clan}). */
		public static final int DASK_CLAN = 1;
		/** "End him" ({@code c5:dask end}). */
		public static final int DASK_END = 2;
		/** A player who has not begun Chapter Five. */
		public static final Chapter5 NEW = new Chapter5(ChapterFiveStage.NOT_STARTED, -1L, 0, -1, DASK_NONE, 0, 0, -1, 0);

		private static final Codec<ChapterFiveStage> STAGE_CODEC = Codec.INT.xmap(ChapterFiveStage::of, ChapterFiveStage::ordinal);

		public static final Codec<Chapter5> CODEC = RecordCodecBuilder.create(i -> i.group(
				STAGE_CODEC.optionalFieldOf("stage", ChapterFiveStage.NOT_STARTED).forGetter(Chapter5::stage),
				Codec.LONG.optionalFieldOf("wait_since", -1L).forGetter(Chapter5::waitSince),
				Codec.INT.optionalFieldOf("ways", 0).forGetter(Chapter5::ways),
				Codec.INT.optionalFieldOf("way", -1).forGetter(Chapter5::way),
				Codec.INT.optionalFieldOf("dask", DASK_NONE).forGetter(Chapter5::dask),
				Codec.INT.optionalFieldOf("blockade_failures", 0).forGetter(Chapter5::blockadeFailures),
				Codec.INT.optionalFieldOf("verses", 0).forGetter(Chapter5::verses),
				Codec.INT.optionalFieldOf("ulvaine", -1).forGetter(Chapter5::ulvaine),
				Codec.INT.optionalFieldOf("boss", 0).forGetter(Chapter5::boss)
		).apply(i, Chapter5::new));

		/** The way that finished first, or null before one has. */
		public ChapterFive.@Nullable Way chosenWay() {
			return ChapterFive.Way.of(way);
		}

		/** Whether way {@code w} is complete. */
		public boolean hasWay(ChapterFive.Way w) {
			return (ways & (1 << w.ordinal())) != 0;
		}

		/** The owner's choice over Ulvaine, or null before it is made. */
		public @Nullable BossOutcome ulvaineOutcome() {
			return BossOutcome.of(ulvaine);
		}

		public Chapter5 withStage(ChapterFiveStage newStage, long newWaitSince) {
			return new Chapter5(newStage, newWaitSince, ways, way, dask, blockadeFailures, verses, ulvaine, boss);
		}

		/** Marks way {@code w} complete; the first way completed also becomes {@link #way} (6.3). */
		public Chapter5 withWay(ChapterFive.Way w) {
			return new Chapter5(stage, waitSince, ways | (1 << w.ordinal()), way < 0 ? w.ordinal() : way, dask, blockadeFailures, verses, ulvaine,
					boss);
		}

		public Chapter5 withDask(int newDask) {
			return new Chapter5(stage, waitSince, ways, way, newDask, blockadeFailures, verses, ulvaine, boss);
		}

		public Chapter5 withBlockadeFailures(int newFailures) {
			return new Chapter5(stage, waitSince, ways, way, dask, newFailures, verses, ulvaine, boss);
		}

		public Chapter5 withVerses(int newVerses) {
			return new Chapter5(stage, waitSince, ways, way, dask, blockadeFailures, newVerses, ulvaine, boss);
		}

		public Chapter5 withBoss(int newBoss, int newUlvaine) {
			return new Chapter5(stage, waitSince, ways, way, dask, blockadeFailures, verses, newUlvaine, newBoss);
		}
	}

	/**
	 * The Fall or the Return (spec MILESTONE_5.md 8, 14.1). Immutable, like {@link Chapter4}: replace
	 * it and commit. Phase 0 built the record so the frozen {@link Turnings} accessor can read it; the
	 * paths worker (W5) fills it. Whether a player has ever Fallen or Returned is the identity choice
	 * ({@link Turnings#done}), not this record. Every field is optional, so a Milestone 4 save decodes
	 * to {@link #NEW}.
	 *
	 * @param kind the questline under way, or the last one finished, saved by ordinal
	 * @param stage that questline's step, saved by ordinal
	 * @param bandSince game-day index the alignment was first seen in the offer's band, or -1 (8.2)
	 * @param refusals offers declined while the band held (8.2)
	 * @param reofferDay game-day index on which a declined offer may come back, or -1 (8.2)
	 * @param task the task step's progress (8.3), as the paths worker defines it
	 * @param turnedDay game-day index of the last rite, or -1 (the Standing panel's "Turned" line, 8.4)
	 */
	public record Turning(TurningKind kind, TurningStage stage, long bandSince, int refusals, long reofferDay, int task, long turnedDay) {
		/** A player who has never been offered a turning. */
		public static final Turning NEW = new Turning(TurningKind.NONE, TurningStage.NONE, -1L, 0, -1L, 0, -1L);

		private static final Codec<TurningKind> KIND_CODEC = Codec.INT.xmap(TurningKind::of, TurningKind::ordinal);
		private static final Codec<TurningStage> STAGE_CODEC = Codec.INT.xmap(TurningStage::of, TurningStage::ordinal);

		public static final Codec<Turning> CODEC = RecordCodecBuilder.create(i -> i.group(
				KIND_CODEC.optionalFieldOf("kind", TurningKind.NONE).forGetter(Turning::kind),
				STAGE_CODEC.optionalFieldOf("stage", TurningStage.NONE).forGetter(Turning::stage),
				Codec.LONG.optionalFieldOf("band_since", -1L).forGetter(Turning::bandSince),
				Codec.INT.optionalFieldOf("refusals", 0).forGetter(Turning::refusals),
				Codec.LONG.optionalFieldOf("reoffer_day", -1L).forGetter(Turning::reofferDay),
				Codec.INT.optionalFieldOf("task", 0).forGetter(Turning::task),
				Codec.LONG.optionalFieldOf("turned_day", -1L).forGetter(Turning::turnedDay)
		).apply(i, Turning::new));

		public Turning withStage(TurningKind newKind, TurningStage newStage) {
			return new Turning(newKind, newStage, bandSince, refusals, reofferDay, task, turnedDay);
		}

		public Turning withOffer(long newBandSince, int newRefusals, long newReofferDay) {
			return new Turning(kind, stage, newBandSince, newRefusals, newReofferDay, task, turnedDay);
		}

		public Turning withTask(int newTask) {
			return new Turning(kind, stage, bandSince, refusals, reofferDay, newTask, turnedDay);
		}

		public Turning withTurnedDay(long newTurnedDay) {
			return new Turning(kind, stage, bandSince, refusals, reofferDay, task, newTurnedDay);
		}
	}

	public static final Codec<StoryData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Stages.CODEC.fieldOf("stages").forGetter(StoryData::stages),
			Places.CODEC.optionalFieldOf("places", new Places("", Optional.empty(), Optional.empty())).forGetter(StoryData::places),
			Npcs.CODEC.optionalFieldOf("npcs", new Npcs(Optional.empty(), Optional.empty(), Optional.empty())).forGetter(StoryData::npcs),
			Encounter.CODEC.optionalFieldOf("encounter", new Encounter(Optional.empty(), 0, 0, 0, Optional.empty(), -1L)).forGetter(StoryData::encounter),
			ForceData.INT_MAP.optionalFieldOf("counters", Map.of()).forGetter(d -> d.counters),
			ForceData.STRING_SET.optionalFieldOf("flags", Set.of()).forGetter(d -> d.flags),
			Chapter3.CODEC.optionalFieldOf("chapter3", Chapter3.NEW).forGetter(d -> d.chapter3),
			Chapter4.CODEC.optionalFieldOf("chapter4", Chapter4.NEW).forGetter(d -> d.chapter4),
			Chapter5.CODEC.optionalFieldOf("chapter5", Chapter5.NEW).forGetter(d -> d.chapter5),
			Turning.CODEC.optionalFieldOf("turning", Turning.NEW).forGetter(d -> d.turning)
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

	/** Chapter Four progress; immutable, so replace it and commit (see {@link Chapter4}). */
	public Chapter4 chapter4 = Chapter4.NEW;

	/** Chapter Five progress; immutable, so replace it and commit (see {@link Chapter5}). */
	public Chapter5 chapter5 = Chapter5.NEW;

	/** The Fall or the Return; immutable, so replace it and commit (see {@link Turning}). */
	public Turning turning = Turning.NEW;

	/** Frozen copy for syncing (see {@link #syncView()}); never copied, persisted or mutated. */
	private volatile @Nullable StoryData syncView;

	public StoryData() {
	}

	private StoryData(Stages stages, Places places, Npcs npcs, Encounter encounter, Map<String, Integer> counters, Set<String> flags,
			Chapter3 chapter3, Chapter4 chapter4, Chapter5 chapter5, Turning turning) {
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
		this.chapter4 = chapter4;
		this.chapter5 = chapter5;
		this.turning = turning;
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
		return new StoryData(stages(), places(), npcs(), encounter(), counters, flags, chapter3, chapter4, chapter5, turning);
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
