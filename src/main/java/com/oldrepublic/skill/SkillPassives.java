package com.oldrepublic.skill;

import net.minecraft.world.entity.player.Player;

/**
 * The numbers of the skill passives whose effects other features apply in their own code (saber
 * and force), so the balance stays in one section ({@code skills}). Call them on the server (the
 * numbers come from the server balance); each returns the neutral value when the node is not
 * active (not owned, or dormant).
 * The skills feature applies {@code deep_reserves}, {@code quickened_flow}, {@code calm_focus}
 * (energy) and {@code hungering_dark} itself.
 */
public final class SkillPassives {
	/** Extra parry-window ticks from {@code centered_mind}. */
	public static int centeredParryTicks(Player player) {
		return Skills.activeRank(player, SkillIds.CENTERED_MIND) * SkillService.balance().centeredParryTicksPerRank;
	}

	/**
	 * Damage multiplier for saber and Force damage dealt by {@code player} from {@code fury}: +6% per
	 * rank while below half health, otherwise 1.
	 */
	public static float furyMultiplier(Player player) {
		int rank = Skills.activeRank(player, SkillIds.FURY);
		SkillBalance b = SkillService.balance();

		if (rank <= 0 || player.getHealth() >= player.getMaxHealth() * b.furyHealthFraction) {
			return 1f;
		}

		return 1f + rank * b.furyPerRank;
	}

	/**
	 * Damage multiplier for another player standing near a guarding player who has
	 * {@code guardians_resolve} (the saber feature knows who is guarding and calls this).
	 *
	 * @param guardian a player who is guarding with a lit saber
	 * @param victim   the player about to take damage
	 */
	public static float guardiansResolveMultiplier(Player guardian, Player victim) {
		SkillBalance b = SkillService.balance();

		if (guardian == victim || !Skills.active(guardian, SkillIds.GUARDIANS_RESOLVE)
				|| guardian.distanceToSqr(victim) > b.guardiansResolveRadius * b.guardiansResolveRadius) {
			return 1f;
		}

		return 1f - b.guardiansResolveReduction;
	}

	/** Radius within which {@code guardians_resolve} protects other players. */
	public static float guardiansResolveRadius() {
		return SkillService.balance().guardiansResolveRadius;
	}

	/** Fear ticks a heavy attack applies to non-boss hostiles with {@code dread_presence}, else 0. */
	public static int dreadFleeTicks(Player player) {
		return Skills.active(player, SkillIds.DREAD_PRESENCE) ? SkillService.balance().dreadFleeTicks : 0;
	}

	private SkillPassives() {
	}
}
