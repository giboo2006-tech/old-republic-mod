package com.oldrepublic.boss.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A player's Long Hymn and echo record, attachment {@code oldrepublic:raid} (MILESTONE_5.md 12.2–12.4, 14.1): persistent,
 * kept on death, synced to its player only ({@link RaidAttachments}). An immutable record: changes replace it whole.
 */
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

	/** {@code p}'s record ({@link #EMPTY} when it has none); works on both sides (the attachment is synced to its player). */
	public static RaidData of(Player p) {
		return p.getAttachedOrElse(RaidAttachments.RAID, EMPTY);
	}

	/** Replaces {@code p}'s record (server side). */
	public static void set(ServerPlayer p, RaidData data) {
		p.setAttached(RaidAttachments.RAID, data);
	}

	/** Whether the rewarded clear of {@code week} is already taken. */
	public boolean clearedIn(long week) {
		return lastClearWeek == week;
	}

	/** Whether the echo of {@code day} is already rewarded. */
	public boolean echoOn(long day) {
		return echoDay == day;
	}

	/** This record after a rewarded clear in {@code week}, with the schematic pity now {@code newPity}. */
	public RaidData withClear(long week, int newPity) {
		return new RaidData(week, Math.max(0, newPity), clears + 1, echoDay, echoes);
	}

	/** This record after a rewarded echo defeat on {@code day}. */
	public RaidData withEcho(long day) {
		return new RaidData(lastClearWeek, pity, clears, day, echoes + 1);
	}
}
