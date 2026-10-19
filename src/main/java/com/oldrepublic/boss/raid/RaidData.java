package com.oldrepublic.boss.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.entity.player.Player;

/** A player's Long Hymn and echo record, attachment {@code oldrepublic:raid} (MILESTONE_5.md 12.2–12.4, 14.1); W8 owns it. Phase 0 stub. */
public record RaidData(long lastClearWeek, int pity, int clears, long echoDay, int echoes) {
	/** Never cleared and no echo yet: the week and the day are -1, since week 0 and day 0 are real values. */
	public static final RaidData EMPTY = new RaidData(-1L, 0, 0, -1L, 0);

	public static final Codec<RaidData> CODEC = RecordCodecBuilder.create(i -> i.group(
			// Clocks.day(server) / raid.weekDays at the last rewarded clear (12.3).
			Codec.LONG.optionalFieldOf("last_clear_week", -1L).forGetter(RaidData::lastClearWeek),
			// Weekly clears in a row without a legendary schematic (11.4).
			Codec.INT.optionalFieldOf("pity", 0).forGetter(RaidData::pity),
			// Clears, all time (the rank 4 deed, 8.5).
			Codec.INT.optionalFieldOf("clears", 0).forGetter(RaidData::clears),
			// The day of the last rewarded echo (once a day, 12.2).
			Codec.LONG.optionalFieldOf("echo_day", -1L).forGetter(RaidData::echoDay),
			// Echo defeats, all time (the rank 4 deed).
			Codec.INT.optionalFieldOf("echoes", 0).forGetter(RaidData::echoes)
	).apply(i, RaidData::new));

	/** {@code p}'s record. Phase 0 stub (W8 replaces it): {@link #EMPTY} until W8 registers the attachment. */
	public static RaidData of(Player p) {
		return EMPTY;
	}
}
