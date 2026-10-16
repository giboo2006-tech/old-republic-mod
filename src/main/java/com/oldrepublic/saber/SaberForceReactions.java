package com.oldrepublic.saber;

import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.saber.duel.NpcGuard;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The saber feature's answers to Force powers: guards (with the form's arc for players), guard
 * drain and restore, a Push on a guard (knock-back x{@code pushOnGuardKnockMult}, drain
 * {@code pushOnGuardDrain}), wind-up interrupts of saber NPCs, and Ataru's Force Jump cost.
 */
final class SaberForceReactions implements ForceReactions.Handler {
	private static final String FORCE_JUMP = "force_jump";

	@Override
	public boolean isGuarding(LivingEntity target, Vec3 from) {
		return SaberInteractions.isGuardingAgainst(target, from);
	}

	@Override
	public boolean drainGuard(LivingEntity target, float amount) {
		return SaberInteractions.drainGuard(target, amount);
	}

	@Override
	public void restoreGuard(LivingEntity target, float amount) {
		SaberInteractions.restoreGuard(target, amount);
	}

	@Override
	public float onPushHit(ServerPlayer caster, LivingEntity target) {
		if (!SaberInteractions.isGuardingAgainst(target, caster.position())) {
			return 1f;
		}

		SaberBalance b = SaberToggle.balance();
		SaberInteractions.drainGuard(target, b.pushOnGuardDrain);
		return b.pushOnGuardKnockMult;
	}

	@Override
	public void interruptWindup(LivingEntity target) {
		if (target instanceof Mob mob) {
			NpcGuard.interruptWindup(mob);
		}
	}

	@Override
	public float costMultiplier(ServerPlayer player, String abilityId) {
		return FORCE_JUMP.equals(abilityId) && SaberPassives.form(player) == SaberForm.ATARU ? SaberToggle.balance().ataruJumpCostMult : 1f;
	}
}
