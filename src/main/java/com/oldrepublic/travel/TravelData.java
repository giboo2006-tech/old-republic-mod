package com.oldrepublic.travel;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

/**
 * A player's travel state (spec MILESTONE_4.md 12.1, attachment {@code oldrepublic:travel}).
 * Immutable: change it with the {@code with*} methods and {@link TravelAttachments#set}.
 *
 * @param unlocked destinations this player may fly to ({@code HOME} is always allowed)
 * @param homePad the player's landing beacon, if placed
 * @param lastPad where the player's ship last landed
 * @param recallDay the world day of the last free recall (4.8), or -1
 * @param towDay the world day of the last free tow, or -1
 * @param crewOf the owner whose crew this player is, if any
 * @param helm whether the rebuilt nav computer lets this player fly their ship by hand
 *        (MILESTONE_5.md 4.2, 14.1): set by the story at {@code CALLED}, by
 *        {@code /oldrepublic ship helm} and for saves already past it; false in a Milestone 4 save
 */
public record TravelData(Set<Destination> unlocked, Optional<GlobalPos> homePad, Optional<GlobalPos> lastPad, long recallDay, long towDay, Optional<UUID> crewOf,
		boolean helm) {
	public static final TravelData EMPTY = new TravelData(Set.of(), Optional.empty(), Optional.empty(), -1L, -1L, Optional.empty(), false);

	private static final Codec<Destination> DESTINATION_CODEC = Codec.STRING.xmap(id -> {
		Destination d = Destination.byId(id);
		return d != null ? d : Destination.HOME;
	}, Destination::id);

	public static final Codec<TravelData> CODEC = RecordCodecBuilder.create(i -> i.group(
			DESTINATION_CODEC.listOf().optionalFieldOf("unlocked", List.of()).forGetter(d -> List.copyOf(d.unlocked)),
			GlobalPos.CODEC.optionalFieldOf("home_pad").forGetter(TravelData::homePad),
			GlobalPos.CODEC.optionalFieldOf("last_pad").forGetter(TravelData::lastPad),
			Codec.LONG.optionalFieldOf("recall_day", -1L).forGetter(TravelData::recallDay),
			Codec.LONG.optionalFieldOf("tow_day", -1L).forGetter(TravelData::towDay),
			UUIDUtil.CODEC.optionalFieldOf("crew_of").forGetter(TravelData::crewOf),
			Codec.BOOL.optionalFieldOf("helm", false).forGetter(TravelData::helm)
	).apply(i, (unlocked, home, last, recall, tow, crewOf, helm) -> new TravelData(Set.copyOf(unlocked), home, last, recall, tow, crewOf, helm)));

	public TravelData {
		EnumSet<Destination> set = EnumSet.noneOf(Destination.class);
		set.addAll(unlocked);
		unlocked = Set.copyOf(set);
	}

	/** The Milestone 4 shape: no helm. */
	public TravelData(Set<Destination> unlocked, Optional<GlobalPos> homePad, Optional<GlobalPos> lastPad, long recallDay, long towDay, Optional<UUID> crewOf) {
		this(unlocked, homePad, lastPad, recallDay, towDay, crewOf, false);
	}

	public boolean isUnlocked(Destination d) {
		return d == Destination.HOME || unlocked.contains(d);
	}

	public TravelData withUnlocked(Destination d, boolean on) {
		EnumSet<Destination> set = EnumSet.noneOf(Destination.class);
		set.addAll(unlocked);

		if (on) {
			set.add(d);
		} else {
			set.remove(d);
		}

		return new TravelData(set, homePad, lastPad, recallDay, towDay, crewOf, helm);
	}

	public TravelData withHomePad(Optional<GlobalPos> pad) {
		return new TravelData(unlocked, pad, lastPad, recallDay, towDay, crewOf, helm);
	}

	public TravelData withLastPad(Optional<GlobalPos> pad) {
		return new TravelData(unlocked, homePad, pad, recallDay, towDay, crewOf, helm);
	}

	public TravelData withRecallDay(long day) {
		return new TravelData(unlocked, homePad, lastPad, day, towDay, crewOf, helm);
	}

	public TravelData withTowDay(long day) {
		return new TravelData(unlocked, homePad, lastPad, recallDay, day, crewOf, helm);
	}

	public TravelData withCrewOf(Optional<UUID> owner) {
		return new TravelData(unlocked, homePad, lastPad, recallDay, towDay, owner, helm);
	}

	/** The helm unlocked or not (MILESTONE_5.md 4.2). */
	public TravelData withHelm(boolean on) {
		return new TravelData(unlocked, homePad, lastPad, recallDay, towDay, crewOf, on);
	}
}
