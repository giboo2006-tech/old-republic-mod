package com.oldrepublic.skill;

import net.minecraft.world.entity.player.Player;

/**
 * The numbers of the skill passives whose effects other features apply in their own code (saber
 * and force), so the balance stays in one section ({@code skills}). Call them on the server (the
 * numbers come from the server balance); each returns the neutral value when the node is not
 * active (not owned, or dormant).
 * The skills feature applies {@code deep_reserves}, {@code quickened_flow}, {@code calm_focus}
 * (energy), {@code hungering_dark} and the Milestone 3 node effects ({@link SkillAttributes},
 * {@link SkillNodeEffects}) itself; {@code quiet_step} is read here by the sentinels and troopers.
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

	/**
	 * {@code trailwise}: the food exhaustion {@code player} takes, scaled down by 15% / 30% while
	 * sprinting (called by the exhaustion mixin for every amount). The client never changes it.
	 */
	public static float sprintExhaustion(Player player, float amount) {
		if (amount <= 0f || player.level().isClientSide() || !player.isSprinting() || Skills.rank(player, SkillIds.TRAILWISE) <= 0) {
			return amount;
		}

		float cut = SkillAttributes.rankValue(SkillService.balance().trailwiseHunger, Skills.activeRank(player, SkillIds.TRAILWISE));
		return amount * Math.max(0f, 1f - cut);
	}

	/**
	 * {@code quiet_step} (for the dormant sentinels): the distance at which {@code player} wakes a
	 * sentinel whose own wake radius is {@code base}, at most {@code quietStepWake} (4) with the node.
	 */
	public static int sentinelWakeRadius(Player player, int base) {
		return Skills.active(player, SkillIds.QUIET_STEP) ? Math.min(base, SkillService.balance().quietStepWake) : base;
	}

	/** {@code quiet_step} (for wary troopers): the distance at which they warn {@code player}, at most 10 with the node. */
	public static int waryWarnRange(Player player, int base) {
		return Skills.active(player, SkillIds.QUIET_STEP) ? Math.min(base, SkillService.balance().quietStepWarn) : base;
	}

	/** {@code quiet_step} (for wary troopers): ticks before they open fire on {@code player}, at least 200 with the node. */
	public static int waryAttackTicks(Player player, int base) {
		return Skills.active(player, SkillIds.QUIET_STEP) ? Math.max(base, SkillService.balance().quietStepWaryTicks) : base;
	}

	private SkillPassives() {
	}
}
