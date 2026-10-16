package com.oldrepublic.identity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Everything the mod remembers about who a player has become: alignment and its recent history,
 * the daily caps of contextual shifts, faction reputation, path, rank, mentor trust and the
 * one-time choices already made. Lives in the persistent, copy-on-death player attachment
 * {@link IdentityAttachments#IDENTITY}, synced to the owning client only.
 *
 * <p>Mutable on the server; change it only through {@link Identity} (which validates, caps, syncs
 * and gives feedback) or {@link IdentityRules} (the pure rules, used by Identity and the tests).
 */
public final class IdentityData {
	/** The Standing tab shows this many recent alignment shifts. */
	public static final int LOG_SIZE = 8;

	/** One remembered alignment shift. */
	public record LogEntry(int amount, String reason, int day) {
		static final Codec<LogEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("amount").forGetter(LogEntry::amount),
				Codec.STRING.fieldOf("reason").forGetter(LogEntry::reason),
				Codec.INT.fieldOf("day").forGetter(LogEntry::day)
		).apply(i, LogEntry::new));
	}

	/** Today's totals of capped shifts, grouped to keep the main codec small. */
	private record Caps(int day, int light, int dark, int severe) {
		static final Codec<Caps> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("day", -1).forGetter(Caps::day),
				Codec.INT.optionalFieldOf("light", 0).forGetter(Caps::light),
				Codec.INT.optionalFieldOf("dark", 0).forGetter(Caps::dark),
				Codec.INT.optionalFieldOf("severe", 0).forGetter(Caps::severe)
		).apply(i, Caps::new));
	}

	private static final Codec<Map<String, Integer>> INT_MAP = Codec.unboundedMap(Codec.STRING, Codec.INT);
	private static final Codec<Set<String>> STRING_SET = Codec.STRING.listOf().xmap(HashSet::new, set -> set.stream().sorted().toList());

	public static final Codec<IdentityData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("alignment", 0).forGetter(d -> d.alignment),
			LogEntry.CODEC.listOf().optionalFieldOf("alignment_log", List.of()).forGetter(d -> d.alignmentLog),
			Caps.CODEC.optionalFieldOf("caps", new Caps(-1, 0, 0, 0)).forGetter(d -> new Caps(d.capDay, d.minorLight, d.minorDark, d.severeDark)),
			INT_MAP.optionalFieldOf("reputation", Map.of()).forGetter(d -> d.reputation),
			Codec.STRING.optionalFieldOf("path", Path.NONE.id()).forGetter(d -> d.path.id()),
			Codec.INT.optionalFieldOf("rank", 0).forGetter(d -> d.rank),
			INT_MAP.optionalFieldOf("trust", Map.of()).forGetter(d -> d.trust),
			STRING_SET.optionalFieldOf("choices", Set.of()).forGetter(d -> d.choices)
	).apply(i, IdentityData::new));

	/** -100 (dark) to +100 (light). */
	public int alignment;
	/** The last {@link #LOG_SIZE} shifts, oldest first. */
	public final List<LogEntry> alignmentLog = new ArrayList<>();
	/** Game-day index the daily totals below belong to, or -1. */
	public int capDay = -1;
	/** Contextual light gained today. */
	public int minorLight;
	/** Contextual darkness gained today (positive number). */
	public int minorDark;
	/** Darkness from killing innocents today (positive number). */
	public int severeDark;
	/** Faction id to reputation, -1000 to +1000. Absent means 0. */
	public final Map<String, Integer> reputation = new HashMap<>();
	public Path path = Path.NONE;
	/** 0 = none, 1 = Initiate / Acolyte, 2 = Padawan / Apprentice. */
	public int rank;
	/** Mentor id ({@code jedi}, {@code sith}) to trust, 0 to 100. Absent means 0. */
	public final Map<String, Integer> trust = new HashMap<>();
	/**
	 * Recorded one-time facts: major alignment source ids (such as {@code reckoning.captain}),
	 * one-shot reputation and trust sources ({@code rep:<faction>:<source>}, {@code trust:<source>})
	 * and choice outcomes recorded by other features.
	 */
	public final Set<String> choices = new HashSet<>();

	public IdentityData() {
	}

	private IdentityData(int alignment, List<LogEntry> log, Caps caps, Map<String, Integer> reputation, String path, int rank,
			Map<String, Integer> trust, Set<String> choices) {
		this.alignment = alignment;
		this.alignmentLog.addAll(log);
		this.capDay = caps.day();
		this.minorLight = caps.light();
		this.minorDark = caps.dark();
		this.severeDark = caps.severe();
		this.reputation.putAll(reputation);
		this.path = Path.byId(path);
		this.rank = rank;
		this.trust.putAll(trust);
		this.choices.addAll(choices);
	}

	public IdentityData copy() {
		return new IdentityData(alignment, alignmentLog, new Caps(capDay, minorLight, minorDark, severeDark), reputation, path.id(), rank, trust, choices);
	}

	public int reputation(Faction faction) {
		return reputation.getOrDefault(faction.id(), 0);
	}

	public int trust(String mentorId) {
		return trust.getOrDefault(mentorId, 0);
	}
}
