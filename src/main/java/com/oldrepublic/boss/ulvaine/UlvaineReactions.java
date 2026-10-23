package com.oldrepublic.boss.ulvaine;

import com.oldrepublic.force.ForceReactions;

import net.minecraft.world.entity.LivingEntity;

/**
 * Darth Ulvaine's narrowed Force reactions (spec MILESTONE_5.md 12.2): a Choke holds her at most
 * {@code chokeMaxTicks}, and Lift cannot raise her. Push, staggers and shocks interrupt her moves
 * through the duellist's own guard (the saber feature's handler); her push resistance, Stasis
 * limit and immunity to Fear and Mind Trick come from her tags and her own tick.
 */
final class UlvaineReactions implements ForceReactions.Handler {
	@Override
	public int chokeTicks(LivingEntity target, int requested) {
		return target instanceof DarthUlvaine ? Math.min(requested, Math.max(0, UlvaineBalance.get().chokeMaxTicks)) : requested;
	}

	@Override
	public float liftHeight(LivingEntity target, float requested) {
		return target instanceof DarthUlvaine || target instanceof HymnResonator ? 0f : requested;
	}
}
