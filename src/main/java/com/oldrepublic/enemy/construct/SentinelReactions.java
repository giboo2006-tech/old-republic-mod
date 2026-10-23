package com.oldrepublic.enemy.construct;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.DamageModifier;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.throwing.SaberThrow;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Ruin Sentinel's resistances (spec 7.7), as the shared hooks see them:
 * <ul>
 * <li>{@link ForceReactions.Handler}: Push knockback comes out at {@code pushMultiplier} of a normal
 * target's, the {@code #push_resistant} tag included; Lift raises it at most {@code liftHeight};
 * every wind-up interrupt cancels a slam or lance tell.</li>
 * <li>{@link DamageModifier}: Force Lightning x{@code lightningMultiplier}, blaster bolts
 * x{@code blasterMultiplier} (except its own lance parried back, which hits for exactly
 * {@code lanceReturnDamage}), sabers x{@code saberMultiplier}, and x{@code exposedMultiplier} while
 * the core is exposed.</li>
 * <li>{@link ControlEffects.DurationModifier}: shock lasts x{@code shockDurationMultiplier} (overload).</li>
 * </ul>
 * Fall, drowning, poison and wither immunity live on {@link RuinSentinel} itself.
 */
final class SentinelReactions implements ForceReactions.Handler, DamageModifier, ControlEffects.DurationModifier {
	// ---------------------------------------------------------------- Force

	@Override
	public float onPushHit(ServerPlayer caster, LivingEntity target) {
		if (!(target instanceof RuinSentinel)) {
			return 1f;
		}

		return pushFactor(target, SentinelBalance.get().pushMultiplier, ForcePush.balance().resistantKnockbackMult, ForcePush.balance().knockbackResistanceWeight);
	}

	/**
	 * The handler's share of the Push multiplier, so that the knock-back the push code applies
	 * (already x{@code resistantKnockbackMult} for the tag and x(1 - weight x knockback resistance))
	 * comes out at {@code wanted} overall. Never above 1.
	 */
	static float pushFactor(LivingEntity target, float wanted, float resistantMult, float resistanceWeight) {
		float tag = ForcePowers.isResistant(target) ? Math.max(0.01f, resistantMult) : 1f;
		float attribute = Math.max(0.01f, ForcePowers.knockbackFactor(target, resistanceWeight));
		return Math.min(1f, wanted / (tag * attribute));
	}

	@Override
	public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
		if (target instanceof RuinSentinel sentinel) {
			sentinel.interrupt();
		}
	}

	@Override
	public float liftHeight(LivingEntity target, float requested) {
		return target instanceof RuinSentinel ? Math.min(requested, SentinelBalance.get().liftHeight) : requested;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public float modify(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof RuinSentinel sentinel)) {
			return amount;
		}

		return amount * multiplier(sentinel, source, SentinelBalance.get());
	}

	/** The product of the sentinel's damage multipliers for {@code source}. */
	static float multiplier(RuinSentinel sentinel, DamageSource source, SentinelBalance b) {
		float mult = 1f;

		if (source.is(ForceDamage.LIGHTNING)) {
			mult *= b.lightningMultiplier;
		} else if (source.is(EnemyEntities.BLASTER_DAMAGE)) {
			boolean returnedLance = source.getDirectEntity() instanceof BlasterBolt bolt && bolt.heavy() && bolt.parried();
			mult *= returnedLance ? 1f : b.blasterMultiplier;
		} else if (isSaberHit(source)) {
			mult *= b.saberMultiplier;
		}

		if (sentinel.isCoreExposed()) {
			mult *= b.exposedMultiplier;
		}

		return mult;
	}

	/** A thrown saber, or a melee hit by someone holding a usable saber. */
	static boolean isSaberHit(DamageSource source) {
		if (source.is(SaberThrow.DAMAGE)) {
			return true;
		}

		return source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
				&& SaberComponents.isUsable(attacker.getMainHandItem());
	}

	// ---------------------------------------------------------------- control

	@Override
	public float multiplier(LivingEntity target, String kind) {
		return target instanceof RuinSentinel && ControlEffects.SHOCK.equals(kind) ? SentinelBalance.get().shockDurationMultiplier : 1f;
	}
}
