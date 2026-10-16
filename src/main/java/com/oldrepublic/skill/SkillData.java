package com.oldrepublic.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.progression.ForceData;

/**
 * One player's skill trees: owned nodes and ranks, what was paid for them, which were granted, the
 * four ability slots, the active saber form and the respec timer. Lives in the persistent,
 * copy-on-death {@link SkillAttachments#SKILLS} attachment, synced to its owner only.
 *
 * <p>Mutable on the server; after changing it call {@link SkillService#commit}. Clients only read.
 */
public final class SkillData {
	/** Slots stored (the fourth opens at the first rank). */
	public static final int SLOT_COUNT = 4;
	/** {@link #lastRespec} value for "never". */
	public static final long NEVER = -1L;

	public static final Codec<SkillData> CODEC = RecordCodecBuilder.create(i -> i.group(
			ForceData.INT_MAP.optionalFieldOf("nodes", Map.of()).forGetter(d -> d.nodes),
			ForceData.INT_MAP.optionalFieldOf("spent", Map.of()).forGetter(d -> d.spent),
			ForceData.STRING_SET.optionalFieldOf("granted", Set.of()).forGetter(d -> d.granted),
			Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(d -> d.slots),
			Codec.INT.optionalFieldOf("open_slots", 3).forGetter(d -> d.openSlots),
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
	/** Open slots (3, or 4 after the first rank); written by the server. */
	public int openSlots = 3;
	/** Node id of the active saber form, {@code ""} for Unformed. */
	public String activeForm = "";
	/** Game time of the last full respec, or {@link #NEVER}. */
	public long lastRespec = NEVER;
	/** Set once the Milestone 1 Force Push branch has been moved into the skill tree. */
	public boolean migrated;

	public SkillData() {
		normaliseSlots();
	}

	private SkillData(Map<String, Integer> nodes, Map<String, Integer> spent, Set<String> granted, List<String> slots, int openSlots,
			String activeForm, long lastRespec, boolean migrated) {
		this.nodes.putAll(nodes);
		this.spent.putAll(spent);
		this.granted.addAll(granted);
		this.slots.addAll(slots);
		this.openSlots = openSlots;
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
		return new SkillData(nodes, spent, granted, slots, openSlots, activeForm, lastRespec, migrated);
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
