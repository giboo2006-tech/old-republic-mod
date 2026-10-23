package com.oldrepublic.boss.drops;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.saber.HiltParts;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

/**
 * A player's record of the bosses' drops (spec MILESTONE_7.md 7.3), attachment {@code oldrepublic:boss_drops}:
 * persistent, kept on death, server side only. Immutable: changes replace it whole.
 *
 * @param pity   each drop's occasions in a row without one, by counter ({@link BossDrops}: {@code collector},
 *               {@code watchman}, {@code penitent}, {@code choir}); never negative
 * @param given  every set part the drops gave, by item id ({@code emitter_watchman}): what "the part the player lacks"
 *               is read against, and the guard of the once-only emitters
 * @param rolled the last occasion (an in-game day, or the raid week) each repeating drop rolled on, by guard
 */
public record BossDropsData(Map<String, Integer> pity, Set<String> given, Map<String, Long> rolled) {
	public static final BossDropsData EMPTY = new BossDropsData(Map.of(), Set.of(), Map.of());

	private static final Codec<Set<String>> NAMES = Codec.STRING.listOf().xmap(LinkedHashSet::new, List::copyOf);

	public static final Codec<BossDropsData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("pity", Map.of()).forGetter(BossDropsData::pity),
			NAMES.optionalFieldOf("given", Set.of()).forGetter(BossDropsData::given),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("rolled", Map.of()).forGetter(BossDropsData::rolled)
	).apply(i, BossDropsData::new));

	/** Persistent and kept on death; never synced (nothing on the client reads it). */
	public static final AttachmentType<BossDropsData> BOSS_DROPS = AttachmentRegistry.create(
			OldRepublicMod.id("boss_drops"),
			builder -> builder
					.initializer(() -> EMPTY)
					.persistent(CODEC)
					.copyOnDeath()
	);

	public BossDropsData {
		pity = Map.copyOf(pity);
		given = Set.copyOf(given);
		rolled = Map.copyOf(rolled);
	}

	/** {@code p}'s record ({@link #EMPTY} when it has none). */
	public static BossDropsData get(Player p) {
		return p.getAttachedOrCreate(BOSS_DROPS);
	}

	/** Replaces {@code p}'s record. */
	public static void set(Player p, BossDropsData data) {
		p.setAttached(BOSS_DROPS, data);
	}

	/** The occasions in a row without a drop on {@code counter}. */
	public int misses(String counter) {
		return Math.max(0, pity.getOrDefault(counter, 0));
	}

	/** Whether the drops gave {@code part}. */
	public boolean gave(HiltParts.Part part) {
		return given.contains(part.itemId());
	}

	/** Whether the drop guarded by {@code guard} already rolled on {@code occasion}. */
	public boolean rolledOn(String guard, long occasion) {
		Long last = rolled.get(guard);
		return last != null && last == occasion;
	}

	/** This record with {@code counter} at {@code misses}. */
	public BossDropsData withMisses(String counter, int misses) {
		Map<String, Integer> next = new HashMap<>(pity);
		next.put(counter, Math.max(0, misses));
		return new BossDropsData(next, given, rolled);
	}

	/** This record with {@code part} given. */
	public BossDropsData withGiven(HiltParts.Part part) {
		Set<String> next = new LinkedHashSet<>(given);
		next.add(part.itemId());
		return new BossDropsData(pity, next, rolled);
	}

	/** This record with the drop guarded by {@code guard} rolled on {@code occasion}. */
	public BossDropsData withRolled(String guard, long occasion) {
		Map<String, Long> next = new HashMap<>(rolled);
		next.put(guard, occasion);
		return new BossDropsData(pity, given, next);
	}

	/** Loads the class, registering the attachment; called from {@link BossDropsFeature#init()}. */
	static void init() {
	}
}
