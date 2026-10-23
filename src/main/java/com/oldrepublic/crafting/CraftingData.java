package com.oldrepublic.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A player's crafting state (spec MILESTONE_4.md 12.1, attachment {@code oldrepublic:crafting}).
 * Immutable: change it with the {@code with*} methods and {@link CraftingAttachments#set}.
 *
 * @param disciplines the chosen disciplines, at most {@link #MAX_DISCIPLINES}, in the order chosen
 * @param unlearned disciplines given up at a trainer, at most {@link #MAX_UNLEARNED}; their
 *        schematics stay in {@code known} but are locked until chosen again
 * @param known learned schematic ids, at most {@link #MAX_KNOWN}
 * @param firstCrafts schematics crafted at least once (the first-craft XP), at most {@link #MAX_KNOWN}
 * @param chosenAt game-day index of the first discipline choice, or -1
 * @param legendaryMisses misses so far per legendary schematic id (MILESTONE_5.md 11.4), at most {@link #MAX_KNOWN} entries,
 *        never negative; W7's {@code LegendaryDrops} keeps it
 */
public record CraftingData(List<Progress> disciplines, List<Discipline> unlearned, Set<Identifier> known, Set<Identifier> firstCrafts, long chosenAt,
		Map<String, Integer> legendaryMisses) {
	public static final int MAX_DISCIPLINES = 2;
	public static final int MAX_UNLEARNED = 4;
	public static final int MAX_KNOWN = 256;

	/** One chosen discipline: its level (1 to 5), XP and Masterwork pity count. */
	public record Progress(Discipline discipline, int level, int xp, int pity) {
		public static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
				Discipline.CODEC.fieldOf("id").forGetter(Progress::discipline),
				Codec.INT.optionalFieldOf("level", 1).forGetter(Progress::level),
				Codec.INT.optionalFieldOf("xp", 0).forGetter(Progress::xp),
				Codec.INT.optionalFieldOf("pity", 0).forGetter(Progress::pity)
		).apply(i, Progress::new));

		public Progress {
			level = Math.max(1, Math.min(5, level));
			xp = Math.max(0, xp);
			pity = Math.max(0, pity);
		}
	}

	private static final Codec<Set<Identifier>> ID_SET = Identifier.CODEC.listOf().xmap(LinkedHashSet::new, List::copyOf);
	private static final Codec<Map<String, Integer>> MISSES = Codec.unboundedMap(Codec.STRING, Codec.INT);

	public static final CraftingData EMPTY = new CraftingData(List.of(), List.of(), Set.of(), Set.of(), -1L);

	public static final Codec<CraftingData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Progress.CODEC.listOf().optionalFieldOf("disciplines", List.of()).forGetter(CraftingData::disciplines),
			Discipline.CODEC.listOf().optionalFieldOf("unlearned", List.of()).forGetter(CraftingData::unlearned),
			ID_SET.optionalFieldOf("known", Set.of()).forGetter(CraftingData::known),
			ID_SET.optionalFieldOf("first_crafts", Set.of()).forGetter(CraftingData::firstCrafts),
			Codec.LONG.optionalFieldOf("chosen_at", -1L).forGetter(CraftingData::chosenAt),
			// Absent in M4 saves, so they load with no misses (MILESTONE_5.md 11.4, 14.1).
			MISSES.optionalFieldOf("legendary_misses", Map.of()).forGetter(CraftingData::legendaryMisses)
	).apply(i, CraftingData::new));

	public CraftingData {
		disciplines = List.copyOf(disciplines.subList(0, Math.min(disciplines.size(), MAX_DISCIPLINES)));
		unlearned = List.copyOf(unlearned.subList(0, Math.min(unlearned.size(), MAX_UNLEARNED)));
		known = limit(known);
		firstCrafts = limit(firstCrafts);
		legendaryMisses = limitMisses(legendaryMisses);
	}

	/** Data with no legendary misses (every M4 caller). */
	public CraftingData(List<Progress> disciplines, List<Discipline> unlearned, Set<Identifier> known, Set<Identifier> firstCrafts, long chosenAt) {
		this(disciplines, unlearned, known, firstCrafts, chosenAt, Map.of());
	}

	/** The player's progress in {@code d}, if they have it. */
	public Optional<Progress> progress(Discipline d) {
		for (Progress p : disciplines) {
			if (p.discipline() == d) {
				return Optional.of(p);
			}
		}

		return Optional.empty();
	}

	public boolean has(Discipline d) {
		return progress(d).isPresent();
	}

	/** Adds or replaces the progress of its discipline; past {@link #MAX_DISCIPLINES} the change is refused. */
	public CraftingData withProgress(Progress progress) {
		List<Progress> next = new ArrayList<>(disciplines);
		next.removeIf(p -> p.discipline() == progress.discipline());

		if (next.size() >= MAX_DISCIPLINES) {
			return this;
		}

		int at = indexOf(progress.discipline());
		next.add(at < 0 ? next.size() : at, progress);
		return new CraftingData(next, unlearned, known, firstCrafts, chosenAt, legendaryMisses);
	}

	/** Drops {@code d} and lists it as unlearned (oldest dropped past {@link #MAX_UNLEARNED}). */
	public CraftingData withoutDiscipline(Discipline d) {
		List<Progress> next = new ArrayList<>(disciplines);
		next.removeIf(p -> p.discipline() == d);
		List<Discipline> gone = new ArrayList<>(unlearned);
		gone.remove(d);
		gone.add(d);

		while (gone.size() > MAX_UNLEARNED) {
			gone.remove(0);
		}

		return new CraftingData(next, gone, known, firstCrafts, chosenAt, legendaryMisses);
	}

	public CraftingData withKnown(Identifier schematic) {
		Set<Identifier> next = new LinkedHashSet<>(known);
		next.add(schematic);
		return new CraftingData(disciplines, unlearned, next, firstCrafts, chosenAt, legendaryMisses);
	}

	public CraftingData withFirstCraft(Identifier schematic) {
		Set<Identifier> next = new LinkedHashSet<>(firstCrafts);
		next.add(schematic);
		return new CraftingData(disciplines, unlearned, known, next, chosenAt, legendaryMisses);
	}

	public CraftingData withChosenAt(long day) {
		return new CraftingData(disciplines, unlearned, known, firstCrafts, day, legendaryMisses);
	}

	/** Misses so far toward the legendary schematic {@code schematic} (11.4); 0 when none. */
	public int misses(String schematic) {
		return legendaryMisses.getOrDefault(schematic, 0);
	}

	/** {@code schematic}'s legendary misses set to {@code count}; 0 or less removes the entry (11.4). */
	public CraftingData withMisses(String schematic, int count) {
		Map<String, Integer> next = new LinkedHashMap<>(legendaryMisses);

		if (count > 0) {
			next.put(schematic, count);
		} else {
			next.remove(schematic);
		}

		return new CraftingData(disciplines, unlearned, known, firstCrafts, chosenAt, next);
	}

	private int indexOf(Discipline d) {
		for (int i = 0; i < disciplines.size(); i++) {
			if (disciplines.get(i).discipline() == d) {
				return i;
			}
		}

		return -1;
	}

	private static Set<Identifier> limit(Set<Identifier> ids) {
		Set<Identifier> out = new LinkedHashSet<>();

		for (Identifier id : ids) {
			if (out.size() >= MAX_KNOWN) {
				break;
			}

			out.add(id);
		}

		return java.util.Collections.unmodifiableSet(out);
	}

	private static Map<String, Integer> limitMisses(Map<String, Integer> misses) {
		Map<String, Integer> out = new LinkedHashMap<>();

		for (Map.Entry<String, Integer> e : misses.entrySet()) {
			if (out.size() >= MAX_KNOWN) {
				break;
			}

			out.put(e.getKey(), Math.max(0, e.getValue()));
		}

		return java.util.Collections.unmodifiableMap(out);
	}
}
