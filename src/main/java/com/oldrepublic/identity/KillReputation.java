package com.oldrepublic.identity;

import net.minecraft.server.level.ServerPlayer;

/**
 * What killing a Republic or Imperial trooper costs (spec section 5.2). A trooper that counted as
 * hostile toward the killer when the killing damage landed (the Hostile tier, an engaged squad or an
 * incident aimed at them) costs {@code hostileKillRep} with its army, at most
 * {@code killRepDailyCap} per army and in-game day, and its kill XP is paid by the lead's
 * progression hook. Any other trooper (Neutral, Wary, Allied, and a squad the killer provoked) costs
 * {@code neutralKillRep}, never capped, plus {@link ContextAct#KILLED_SOLDIER}, and pays no XP. No kill
 * ever gains reputation.
 */
public final class KillReputation {
	/**
	 * One kill's outcome.
	 *
	 * @param repDelta      the reputation change with the trooper's army (never positive)
	 * @param killedSoldier whether {@link ContextAct#KILLED_SOLDIER} applies
	 * @param paysXp        whether the kill pays XP (the lead's progression hook decides it the same way)
	 * @param cappedToday   the capped losses with that army today after this kill (positive)
	 */
	public record Result(int repDelta, boolean killedSoldier, boolean paysXp, int cappedToday) {
	}

	/**
	 * The pure rule.
	 *
	 * @param cappedSoFarToday the capped (hostile) losses with this army already taken today, positive
	 */
	public static Result decide(boolean wasHostile, int cappedSoFarToday, IdentityBalance b) {
		int already = Math.max(0, cappedSoFarToday);

		if (!wasHostile) {
			return new Result(Math.min(0, b.neutralKillRep), true, false, already);
		}

		int loss = Math.max(0, -b.hostileKillRep);
		int allowed = Math.max(0, b.killRepDailyCap - already);
		int applied = Math.min(loss, allowed);
		return new Result(-applied, false, true, already + applied);
	}

	/**
	 * Applies one kill of a trooper of {@code army} by {@code p}: today's capped total in
	 * {@link IdentityData#killRepToday}, the reputation change and, for a non-hostile kill,
	 * {@link ContextAct#KILLED_SOLDIER} (a minor dark shift inside the M2 minor cap).
	 *
	 * @param day the in-game day index ({@code IdentityRules.dayIndex(level.getGameTime())})
	 */
	public static Result apply(ServerPlayer p, Faction army, boolean wasHostile, int day) {
		IdentityData data = Identity.get(p);

		if (data.killRepDay != day) {
			data.killRepDay = day;
			data.killRepToday.clear();
		}

		Result result = decide(wasHostile, data.killRepToday.getOrDefault(army.id(), 0), Identity.balance());

		if (wasHostile) {
			data.killRepToday.put(army.id(), result.cappedToday());
		}

		Identity.commit(p, data);

		if (result.repDelta() != 0) {
			Identity.changeReputation(p, army, result.repDelta(), wasHostile ? "kill.hostile" : "kill.soldier", false);
		}

		if (result.killedSoldier()) {
			Identity.contextual(p, ContextAct.KILLED_SOLDIER, null);
		}

		return result;
	}

	private KillReputation() {
	}
}
