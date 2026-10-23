package com.oldrepublic.quest.story;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.FactionM5Balance;
import com.oldrepublic.identity.IdentityRules;
import com.oldrepublic.identity.KillReputation;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;

import net.minecraft.server.level.ServerPlayer;

/**
 * The two Milestone 5 factions' standing rules that the story keeps (spec MILESTONE_5.md 7): the
 * kill reputation of an enforcer, a clan warrior or a fighter of the quiet cargo's patrol (the M3
 * rule), the contract gain of their boards, and the deed counters of their contracts and space
 * bounties ({@link ChapterFive#CONTRACTS}, {@link ChapterFive#BOUNTIES}, 8.5). The first standing with
 * Clan Hask and both factions' first meeting are set on the first arrival at Saltgate
 * ({@link SaltgateWays}); the attitudes of their people are W8's ({@code StandingRule}). Each gain and
 * loss of the chapter itself is paid where it happens.
 */
public final class FactionStanding {
	/** Whether {@code faction} is one of the two Milestone 5 factions. */
	public static boolean isNew(@Nullable Faction faction) {
		return faction == Faction.VESSBA_KAJIDIC || faction == Faction.CLAN_HASK;
	}

	/**
	 * One kill of a faction foe (W8's {@code onFoeKilled}, or a patrol fighter of the cargo run): the M3
	 * kill rule with that faction, on the story clock's day (-10 when it counted as hostile, at most 200
	 * a day; otherwise -40 and {@code KILLED_SOLDIER}).
	 */
	static void onKill(ServerPlayer killer, Faction faction, boolean wasHostile) {
		KillReputation.apply(killer, faction, wasHostile, IdentityRules.dayIndex(Clocks.time(killer.level().getServer())));
	}

	/** A board contract's standing gain with its issuer: {@code factions_m5.contractRep} for the two new factions, else {@code fallback} (the M3 value). */
	public static int contractRep(@Nullable Faction issuer, int fallback) {
		return isNew(issuer) ? FactionM5Balance.get().contractRep : fallback;
	}

	/** A contract completed on Ghessar or Tyrvann counts toward the rank 4 deed (8.5); a space bounty counts as a bounty instead. */
	public static void onContractDone(ServerPlayer p, Planets.@Nullable Planet planet) {
		if (planet == Planets.Planet.GHESSAR || planet == Planets.Planet.TYRVANN) {
			count(p, ChapterFive.CONTRACTS);
		}
	}

	/** A space bounty completed at {@code board}'s board (5.8): the deed counter, and the board's bounty is taken for the day. */
	public static void onBountyDone(ServerPlayer p, String board, long day) {
		StoryData data = Story.get(p);
		data.counters.merge(ChapterFive.BOUNTIES, 1, Integer::sum);
		data.counters.put(ChapterFiveRules.BOUNTY_DAY_PREFIX + board, (int) day);
		Story.commit(p, data);
	}

	/** Whether {@code board}'s space bounty may be taken today by this player (one per board per day, 5.8). */
	public static boolean bountyDue(ServerPlayer p, String board) {
		return ChapterFiveRules.bountyDue(Story.get(p), board, ChapterFiveService.day(p));
	}

	/** How often the fence's cipher has rerolled {@code board}'s bounty (salts the bounty's seed, 7.1). */
	public static int ciphers(ServerPlayer p, String board) {
		return Story.get(p).counter(ChapterFiveRules.CIPHER_PREFIX + board);
	}

	private static void count(ServerPlayer p, String counter) {
		StoryData data = Story.get(p);
		data.counters.merge(counter, 1, Integer::sum);
		Story.commit(p, data);
	}

	private FactionStanding() {
	}
}
