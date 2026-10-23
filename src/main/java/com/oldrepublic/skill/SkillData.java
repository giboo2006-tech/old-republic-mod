package com.oldrepublic.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.progression.ForceData;

/**
 * One player's skill trees: owned nodes and ranks, what was paid for them, which were granted, the
 * eight ability slots (two bars of four, MILESTONE_5.md 9.1) and how many of each bar are open, the
 * active saber form and the respec timer. Lives in the persistent, copy-on-death
 * {@link SkillAttachments#SKILLS} attachment, synced to its owner only.
 *
 * <p>Mutable on the server; after changing it call {@link SkillService#commit}. Clients only read.
 */
public final class SkillData {
	/**
	 * Slots stored: the first bar (0 to 3; the fourth opens at the first rank) and the second bar (4 to 7,
	 * MILESTONE_5.md 9.1). Loading pads or truncates to it, so a four-slot save gains four empty slots.
	 */
	public static final int SLOT_COUNT = 8;
	/** Slots in one bar: the first bar is slots 0 to 3, the second bar slots 4 to 7. */
	public static final int BAR_SLOTS = 4;
	/** {@link #lastRespec} value for "never". */
	public static final long NEVER = -1L;

	public static final Codec<SkillData> CODEC = RecordCodecBuilder.create(i -> i.group(
			ForceData.INT_MAP.optionalFieldOf("nodes", Map.of()).forGetter(d -> d.nodes),
			ForceData.INT_MAP.optionalFieldOf("spent", Map.of()).forGetter(d -> d.spent),
			ForceData.STRING_SET.optionalFieldOf("granted", Set.of()).forGetter(d -> d.granted),
			Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(d -> d.slots),
			Codec.INT.optionalFieldOf("open_slots", 3).forGetter(d -> d.openSlots),
			Codec.INT.optionalFieldOf("open_slots_2", 0).forGetter(d -> d.openSlots2),
			Codec.STRING.optionalFieldOf("active_form", "").forGetter(d -> d.activeForm),
			Codec.LONG.optionalFieldOf("last_respec", NEVER).forGetter(d -> d.lastRespec),
			Codec.BOOL.optionalFieldOf("migrated", false).forGetter(d -> d.migrated)
	).apply(i, SkillData::new));

	/** Node id to owned rank (absent = not owned). */
	public final Map<String, Integer> nodes = new HashMap<>();
	/** Node id to skill points paid for its ranks (never the switch penalty; 0 or absent for grants). */
	public final Map<String, Integer> spent = new HashMap<>();
	/** Nodes given by quests or lessons: never refunded, kept by a respec. */
	public final Set<String> granted = new HashSet<>();
	/** Ability id per slot ({@code ""} = empty), always {@link #SLOT_COUNT} entries. */
	public final List<String> slots = new ArrayList<>();
	/** Open slots of the first bar (3, or 4 after the first rank); written by the server. Counts slots 0 to 3 only. */
	public int openSlots = 3;
	/**
	 * Open slots of the second bar: 0, 2 from level 15, 4 at rank 4 or as a Wayfarer (MILESTONE_5.md 9.1, 8.5); written by
	 * the server (W6's rule). Counts slots 4 to 7 only.
	 */
	public int openSlots2;
	/** Node id of the active saber form, {@code ""} for Unformed. */
	public String activeForm = "";
	/** Game time of the last full respec, or {@link #NEVER}. */
	public long lastRespec = NEVER;
	/** Set once the Milestone 1 Force Push branch has been moved into the skill tree. */
	public boolean migrated;

	/** Frozen copy for syncing (see {@link #syncView()}); never copied, persisted or mutated. */
	private volatile @Nullable SkillData syncView;

	public SkillData() {
		normaliseSlots();
	}

	private SkillData(Map<String, Integer> nodes, Map<String, Integer> spent, Set<String> granted, List<String> slots, int openSlots,
			int openSlots2, String activeForm, long lastRespec, boolean migrated) {
		this.nodes.putAll(nodes);
		this.spent.putAll(spent);
		this.granted.addAll(granted);
		this.slots.addAll(slots);
		this.openSlots = openSlots;
		this.openSlots2 = openSlots2;
		this.activeForm = activeForm;
		this.lastRespec = lastRespec;
		this.migrated = migrated;
		normaliseSlots();
	}

	private void normaliseSlots() {
		while (slots.size() < SLOT_COUNT) {
			slots.add("");
		}

		while (slots.size() > SLOT_COUNT) {
			slots.remove(slots.size() - 1);
		}
	}

	public SkillData copy() {
		return new SkillData(nodes, spent, granted, slots, openSlots, openSlots2, activeForm, lastRespec, migrated);
	}

	/**
	 * Takes the frozen copy that syncing sends ({@link #syncView()}) and returns this. Server
	 * thread only; {@link SkillService#commit}, the attachment initializer and loading call it.
	 */
	SkillData seal() {
		this.syncView = copy();
		return this;
	}

	/**
	 * What the network sends for this object: the copy taken by the last {@link #seal()}, which
	 * nothing ever mutates. Fabric encodes attachment syncs later, on the network thread, so it must
	 * never iterate the live maps and sets the server thread keeps changing in place.
	 */
	SkillData syncView() {
		SkillData view = syncView;
		return view != null ? view : this;
	}

	/** Owned rank, 0 if not owned. */
	public int rank(String nodeId) {
		return nodes.getOrDefault(nodeId, 0);
	}

	public boolean owns(String nodeId) {
		return rank(nodeId) > 0;
	}

	public boolean isGranted(String nodeId) {
		return granted.contains(nodeId);
	}

	/** Ability id in slot {@code index} (0-based), or {@code ""}. */
	public String slot(int index) {
		return index >= 0 && index < slots.size() ? slots.get(index) : "";
	}

	/**
	 * Whether slot {@code index} (0-based) is open (MILESTONE_5.md 9.1): a first-bar slot (0 to 3) while
	 * {@code index < openSlots}, a second-bar slot (4 to 7) while {@code index - 4 < openSlots2}. Each bar's count opens
	 * only its own slots. {@link Skills#isOpen} reads it for a player.
	 */
	public boolean isOpen(int index) {
		if (index < 0 || index >= SLOT_COUNT) {
			return false;
		}

		return index < BAR_SLOTS ? index < openSlots : index - BAR_SLOTS < openSlots2;
	}

	/** Slot index holding {@code abilityId}, or -1. */
	public int slotOf(String abilityId) {
		return abilityId.isEmpty() ? -1 : slots.indexOf(abilityId);
	}

	/** Every skill point paid for owned nodes (what a respec can refund). */
	public int spentTotal() {
		int total = 0;

		for (int value : spent.values()) {
			total += value;
		}

		return total;
	}
}
