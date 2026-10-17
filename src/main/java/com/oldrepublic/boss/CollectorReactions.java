package com.oldrepublic.boss;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.duel.DuelState;
import com.oldrepublic.saber.duel.NpcGuard;
import com.oldrepublic.saber.throwing.SaberThrow;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Orren Vasch's answers to the combat hooks (spec 8.3, 8.4):
 *
 * <ul>
 * <li><b>Interrupts</b> ({@link ForceReactions.Handler#interruptWindup(LivingEntity, ServerPlayer, String)}):
 * passed to his combat goal, which allows only the causes of 8.4 for the tell in progress; the
 * one-argument form ({@code unknown}) is ignored, and nothing interrupts the Surge or a transition.</li>
 * <li><b>Push clash</b> ({@code onPushHit}): a Push during the Palm tell.</li>
 * <li><b>Lift</b> ({@code liftHeight}): 0 (refused) unless he is Spent; then at most
 * {@code spentLiftHeight}. W3's Lift gives the slam's stagger ({@code force_lift.bossStaggerTicks}, 10)
 * with full duration while he is Spent.</li>
 * <li><b>Damage taken</b> ({@link DamageModifiers}): the Resonance Ward, Unsung, Spent, the Grip
 * channel, Lightning x0.6 and Saber Throw x0.7 outside Spent. Unsung and Spent break his guard, which
 * already multiplies by the saber pipeline's {@code npcBrokenDamageMult}, so only the remainder is added
 * here and the totals are the 8.3 values.</li>
 * <li><b>Control durations</b> ({@link ControlEffects.DurationModifier}): {@code controlMultiplier}
 * outside Spent, full durations while Spent and for his own counterplay staggers; diminishing returns
 * always apply (he is push-resistant).</li>
 * </ul>
 */
final class CollectorReactions implements ForceReactions.Handler {
	static void init() {
		ForceReactions.register(new CollectorReactions());
		DamageModifiers.register(CollectorReactions::modifyDamage);
		ControlEffects.registerDurationModifier(CollectorReactions::controlMultiplier);
	}

	/** The old one-argument form is cause {@code unknown}: never an interrupt for him. */
	@Override
	public void interruptWindup(LivingEntity target) {
	}

	@Override
	public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
		CollectorCombatGoal combat = fightingCombat(target);

		if (combat != null) {
			combat.onInterrupt(caster, cause);
		}
	}

	@Override
	public float onPushHit(ServerPlayer caster, LivingEntity target) {
		CollectorCombatGoal combat = fightingCombat(target);
		return combat == null ? 1f : combat.onPushHit(caster);
	}

	@Override
	public float liftHeight(LivingEntity target, float requested) {
		if (!(target instanceof OrrenVasch vasch)) {
			return requested;
		}

		return vasch.isSpent() ? Math.min(requested, CollectorBalance.get().spentLiftHeight) : 0f;
	}

	/** His combat goal while his session fights outside a transition, else null. */
	private static @Nullable CollectorCombatGoal fightingCombat(LivingEntity target) {
		if (!(target instanceof OrrenVasch vasch)) {
			return null;
		}

		ArenaSession session = vasch.session();

		if (session == null || !session.isFighting() || session.isTransitioning()) {
			return null;
		}

		return vasch.combat();
	}

	/** Damage taken (8.3, 8.4); see the class comment. */
	static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof OrrenVasch vasch)) {
			return amount;
		}

		CollectorBalance b = CollectorBalance.get();
		ArenaSession session = vasch.session();
		boolean spent = vasch.isSpent();
		DuelState guard = NpcGuard.state(vasch);
		boolean broken = guard != null && guard.isBroken(vasch.level().getGameTime());
		float brokenMult = SaberToggle.balance().npcBrokenDamageMult;
		float mult = 1f;

		if (session != null) {
			mult *= session.wardFactor();

			if (session.isUnsung()) {
				mult *= CollectorRules.remainderOverBrokenGuard(b.unsungMultiplier, broken, brokenMult);
			}
		}

		if (spent) {
			mult *= CollectorRules.remainderOverBrokenGuard(b.spentMultiplier, broken, brokenMult);
		} else {
			if (source.is(ForceDamage.LIGHTNING)) {
				mult *= b.lightningMultiplier;
			}

			if (source.is(SaberThrow.DAMAGE)) {
				mult *= b.saberThrowMultiplier;
			}
		}

		CollectorCombatGoal combat = vasch.combat();

		if (combat != null && combat.isChannellingGrip()) {
			mult *= b.gripTakenMultiplier;
		}

		return amount * mult;
	}

	/** Control durations on him: full while Spent and for his own counterplay staggers, else {@code controlMultiplier}. */
	static float controlMultiplier(LivingEntity target, String kind) {
		if (!(target instanceof OrrenVasch vasch) || vasch.isSpent() || vasch.exactControl()) {
			return 1f;
		}

		return CollectorBalance.get().controlMultiplier;
	}
}
