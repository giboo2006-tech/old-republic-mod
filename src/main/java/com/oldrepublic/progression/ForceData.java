package com.oldrepublic.progression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Everything the mod remembers about one player's Force journey. Lives in a persistent,
 * copy-on-death player attachment ({@link ForceAttachments#FORCE}) and is synced to the owning
 * client only.
 *
 * <p>The object is mutable on the server. After changing it, call
 * {@link Progression#commit(net.minecraft.server.level.ServerPlayer, ForceData)} so the change is
 * synced; persistence happens on save regardless.
 */
public final class ForceData {
	public static final Codec<Map<String, Integer>> INT_MAP = Codec.unboundedMap(Codec.STRING, Codec.INT);
	public static final Codec<Map<String, Float>> FLOAT_MAP = Codec.unboundedMap(Codec.STRING, Codec.FLOAT);
	public static final Codec<Set<String>> STRING_SET = Codec.STRING.listOf().xmap(HashSet::new, list -> list.stream().sorted().toList());

	private static final Codec<Stats> STATS_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("level", 1).forGetter(Stats::level),
			Codec.LONG.optionalFieldOf("xp", 0L).forGetter(Stats::xp),
			Codec.LONG.optionalFieldOf("xp_to_next", 120L).forGetter(Stats::xpToNext),
			Codec.INT.optionalFieldOf("resonance", 0).forGetter(Stats::resonance),
			Codec.INT.optionalFieldOf("skill_points", 0).forGetter(Stats::skillPoints),
			Codec.FLOAT.optionalFieldOf("energy", 0f).forGetter(Stats::energy),
			Codec.FLOAT.optionalFieldOf("max_energy", 50f).forGetter(Stats::maxEnergy)
	).apply(i, Stats::new));

	public static final Codec<ForceData> CODEC = RecordCodecBuilder.create(i -> i.group(
			STATS_CODEC.fieldOf("stats").forGetter(ForceData::stats),
			STRING_SET.optionalFieldOf("abilities", Set.of()).forGetter(d -> d.abilities),
			Codec.STRING.optionalFieldOf("push_branch", "").forGetter(d -> d.pushBranch),
			INT_MAP.optionalFieldOf("mastery", Map.of()).forGetter(d -> d.mastery),
			Codec.INT.optionalFieldOf("quest_stage", 0).forGetter(d -> d.questStage),
			STRING_SET.optionalFieldOf("flags", Set.of()).forGetter(d -> d.flags),
			INT_MAP.optionalFieldOf("counters", Map.of()).forGetter(d -> d.counters),
			FLOAT_MAP.optionalFieldOf("kill_fatigue", Map.of()).forGetter(d -> d.killFatigue),
			INT_MAP.optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldowns),
			Codec.INT.optionalFieldOf("stir_ticks", 0).forGetter(d -> d.stirTicks),
			Codec.INT.optionalFieldOf("idle_ticks", 0).forGetter(d -> d.energyIdleTicks)
	).apply(i, ForceData::new));

	/** Persisted numeric state, grouped to keep the codec small. */
	public record Stats(int level, long xp, long xpToNext, int resonance, int skillPoints, float energy, float maxEnergy) {
	}

	public int level = 1;
	public long xp;
	/** Cached from the balance curve when committed, so the client can draw the XP bar. */
	public long xpToNext = 120;
	/** Midi-chlorian Resonance. 0 until first initialised by {@link Progression}. */
	public int resonance;
	public int skillPoints;
	public float energy;
	/** Cached from balance and resonance when committed, so the client can draw the meter. */
	public float maxEnergy = 50;
	/** Ability ids the player has learned, e.g. {@code force_push}. */
	public final Set<String> abilities = new HashSet<>();
	/** Chosen Force Push branch id, or empty. See {@code force.PushBranch}. */
	public String pushBranch = "";
	/** Ability id to mastery points. */
	public final Map<String, Integer> mastery = new HashMap<>();
	/** Introductory quest stage, see {@link com.oldrepublic.quest.QuestStage}. */
	public int questStage;
	/** One-shot facts: discoveries, codex entries, milestone rewards already granted. */
	public final Set<String> flags = new HashSet<>();
	/** Named progress counters, e.g. {@code push_practice} or {@code poacher_kills}. */
	public final Map<String, Integer> counters = new HashMap<>();
	/** Entity type id to anti-grind fatigue (decays over time). */
	public final Map<String, Float> killFatigue = new HashMap<>();
	/** Ability or action id to remaining cooldown ticks. */
	public final Map<String, Integer> cooldowns = new HashMap<>();
	/** Ticks spent alive before the Awakening. */
	public int stirTicks;
	/** Ticks since energy was last spent (regen delay). */
	public int energyIdleTicks;

	/** Frozen copy for syncing (see {@link #syncView()}); never copied, persisted or mutated. */
	private volatile @Nullable ForceData syncView;

	public ForceData() {
	}

	private ForceData(Stats stats, Set<String> abilities, String pushBranch, Map<String, Integer> mastery, int questStage,
			Set<String> flags, Map<String, Integer> counters, Map<String, Float> killFatigue, Map<String, Integer> cooldowns,
			int stirTicks, int energyIdleTicks) {
		this.level = stats.level();
		this.xp = stats.xp();
		this.xpToNext = stats.xpToNext();
		this.resonance = stats.resonance();
		this.skillPoints = stats.skillPoints();
		this.energy = stats.energy();
		this.maxEnergy = stats.maxEnergy();
		this.abilities.addAll(abilities);
		this.pushBranch = pushBranch;
		this.mastery.putAll(mastery);
		this.questStage = questStage;
		this.flags.addAll(flags);
		this.counters.putAll(counters);
		this.killFatigue.putAll(killFatigue);
		this.cooldowns.putAll(cooldowns);
		this.stirTicks = stirTicks;
		this.energyIdleTicks = energyIdleTicks;
	}

	public Stats stats() {
		return new Stats(level, xp, xpToNext, resonance, skillPoints, energy, maxEnergy);
	}

	public ForceData copy() {
		return new ForceData(stats(), abilities, pushBranch, mastery, questStage, flags, counters, killFatigue, cooldowns, stirTicks, energyIdleTicks);
	}

	/**
	 * Takes the frozen copy that syncing sends ({@link #syncView()}) and returns this. Server
	 * thread only; {@link Progression#commit}, the attachment initializer and loading call it.
	 */
	ForceData seal() {
		this.syncView = copy();
		return this;
	}

	/**
	 * What the network sends for this object: the copy taken by the last {@link #seal()}, which
	 * nothing ever mutates. Fabric encodes attachment syncs later, on the network thread, so it must
	 * never iterate the live maps and sets the server thread keeps changing in place.
	 */
	ForceData syncView() {
		ForceData view = syncView;
		return view != null ? view : this;
	}

	public boolean isAwakened() {
		return questStage >= com.oldrepublic.quest.QuestStage.FOCUS.ordinal();
	}

	public boolean hasAbility(String id) {
		return abilities.contains(id);
	}

	public int mastery(String id) {
		return mastery.getOrDefault(id, 0);
	}

	public int counter(String id) {
		return counters.getOrDefault(id, 0);
	}

	public int cooldown(String id) {
		return cooldowns.getOrDefault(id, 0);
	}

	public boolean hasFlag(String flag) {
		return flags.contains(flag);
	}
}
