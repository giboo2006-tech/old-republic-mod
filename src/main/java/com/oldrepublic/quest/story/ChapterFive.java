package com.oldrepublic.quest.story;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.raid.RaidData;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.quest.BossOutcome;

import net.minecraft.world.entity.player.Player;

/**
 * Access to a player's Chapter Five, "The Long Hymn", for other features (spec MILESTONE_5.md 6 and
 * 17.1). Reads work on both sides (the story attachment and the identity attachment are synced to
 * their owner). Other workers report into the chapter through {@code QuestHooks}, never by writing
 * {@link StoryData}. Signatures are frozen.
 */
public final class ChapterFive {
	/** Story flag set when the player paid Varra to fly in with the clan ({@code c5:hire_clan}, 6.5). */
	public static final String CLAN_HIRED = "c5.clan_hired";
	/** Story counter of space bounties completed (5.8, 8.5); the story worker (W4) increments it. */
	public static final String BOUNTIES = "c5.bounties";
	/** Story counter of contracts completed on Ghessar or Tyrvann (7.2, 8.5); W4 increments it. */
	public static final String CONTRACTS = "c5.contracts";
	/**
	 * Story flag set when the Dark Council's writ is given for binding Ulvaine (6.7); W4 sets it, so a
	 * lost {@code ulvaines_writ} keepsake does not lose the deed (8.5).
	 */
	public static final String WRIT = "c5.writ";

	/** The three ways to the factor at Saltgate (6.3). Saved by ordinal in {@link StoryData.Chapter5}, so only append. */
	public enum Way {
		KAJIDIC,
		CLAN,
		FORCE;

		/** Lower-case id used in dialogue conditions and actions ({@code c5:way kajidic}). */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The way at this ordinal, or null for -1 (none) or an unknown value. */
		public static @Nullable Way of(int ordinal) {
			Way[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
		}

		/** The way with this id, or null when there is none. */
		public static @Nullable Way byId(String id) {
			for (Way way : values()) {
				if (way.id().equals(id)) {
					return way;
				}
			}

			return null;
		}
	}

	/**
	 * The rank 4 deeds (8.5): any two of them count.
	 *
	 * @param hymnCleared the Long Hymn cleared at least once (W8's {@link RaidData})
	 * @param echoes Ulvaine's echo defeated, all time (W8's {@link RaidData})
	 * @param bounties space bounties completed (story counter {@link #BOUNTIES})
	 * @param contracts contracts completed on Ghessar or Tyrvann (story counter {@link #CONTRACTS})
	 * @param writ Ulvaine's writ was given (story flag {@link #WRIT}); only a Sith may count it
	 */
	public record Deeds(boolean hymnCleared, int echoes, int bounties, int contracts, boolean writ) {
	}

	/** The player's Chapter Five stage (NOT_STARTED on a client that has not received the data yet). */
	public static ChapterFiveStage stage(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? ChapterFiveStage.NOT_STARTED : data.chapter5.stage();
	}

	/** Whether the player has reached {@code stage} or gone past it. */
	public static boolean atLeast(Player p, ChapterFiveStage stage) {
		return stage(p).ordinal() >= stage.ordinal();
	}

	/** The Saltgate way that finished first (6.3), or null before one has. */
	public static @Nullable Way way(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? null : data.chapter5.chosenWay();
	}

	/**
	 * Whether Clan Hask stands with the player at the blockade (6.5): the clan's way was taken, the
	 * player's clan standing is Trusted or higher, or the player hired the clan ({@link #CLAN_HIRED}).
	 */
	public static boolean clanStands(Player p) {
		StoryData data = Story.peek(p);

		if (data != null && (data.chapter5.hasWay(Way.CLAN) || data.hasFlag(CLAN_HIRED))) {
			return true;
		}

		return Identity.tier(p, Faction.CLAN_HASK).ordinal() >= ReputationTier.TRUSTED.ordinal();
	}

	/** The verse stones silenced, a bit per stone index (6.6). */
	public static int verses(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? 0 : data.chapter5.verses();
	}

	/** The owner's choice over Ulvaine (6.7), or null before it is made. */
	public static @Nullable BossOutcome ulvaineOutcome(Player p) {
		StoryData data = Story.peek(p);
		return data == null ? null : data.chapter5.ulvaineOutcome();
	}

	/** The rank 4 deeds (8.5), from the counters the story worker (W4) and the raid (W8) keep. */
	public static Deeds deeds(Player p) {
		RaidData raid = RaidData.of(p);
		StoryData data = Story.peek(p);

		if (data == null) {
			return new Deeds(raid.clears() > 0, raid.echoes(), 0, 0, false);
		}

		return new Deeds(raid.clears() > 0, raid.echoes(), data.counter(BOUNTIES), data.counter(CONTRACTS), data.hasFlag(WRIT));
	}

	private ChapterFive() {
	}
}
