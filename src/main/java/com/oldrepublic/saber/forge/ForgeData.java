package com.oldrepublic.saber.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.SaberItems;

/**
 * The hilt designs a player has studied at the Forge (MILESTONE_7.md 5.1, attachment
 * {@code oldrepublic:forge}). Immutable: change it with {@link #study} and {@link ForgeAttachments#set}.
 * The designs every player knows from the start ({@link SaberItems#isKnownByDefault}) are never stored,
 * so a change to that list reaches old saves.
 *
 * @param studied the studied designs' item ids ({@code emitter_vanguard}), sorted, without repeats, at most
 *        {@link #MAX_STUDIED}
 */
public record ForgeData(List<String> studied) {
	/** Far more than there are designs: a bound on what a save or a packet can hold. */
	public static final int MAX_STUDIED = 64;

	public static final ForgeData EMPTY = new ForgeData(List.of());

	public static final Codec<ForgeData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().optionalFieldOf("studied", List.of()).forGetter(ForgeData::studied)
	).apply(i, ForgeData::new));

	public ForgeData {
		List<String> sorted = new ArrayList<>(new TreeSet<>(studied));
		studied = List.copyOf(sorted.subList(0, Math.min(sorted.size(), MAX_STUDIED)));
	}

	/** Whether the player may forge {@code design} (5.1): known from the start, or studied. */
	public boolean knows(HiltParts.Part design) {
		return SaberItems.isKnownByDefault(design) || studied.contains(design.itemId());
	}

	/** This data with {@code design} studied (unchanged when it is known already). */
	public ForgeData study(HiltParts.Part design) {
		if (knows(design)) {
			return this;
		}

		List<String> next = new ArrayList<>(studied);
		next.add(design.itemId());
		return new ForgeData(next);
	}
}
