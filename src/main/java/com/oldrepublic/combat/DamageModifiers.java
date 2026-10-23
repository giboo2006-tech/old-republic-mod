package com.oldrepublic.combat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.oldrepublic.progression.Progression;
import com.oldrepublic.progression.ProgressionBalance;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Every registered {@link DamageModifier}, combined. Each modifier sees the same unmodified amount;
 * their ratios multiply, so the order of registration does not matter, and the product is clamped
 * to the {@code progression} balance range ({@code damageModifierMin}/{@code Max}, 0.25 to 2.0).
 * Applied once per hit at the end of {@code SaberCombat.modifyIncomingDamage}.
 */
public final class DamageModifiers {
	private static final List<DamageModifier> MODIFIERS = new CopyOnWriteArrayList<>();

	/** Adds a modifier. Call during mod init. */
	public static void register(DamageModifier m) {
		MODIFIERS.add(m);
	}

	/** {@code amount} times the product of every modifier's ratio, the product clamped to the balance range. */
	public static float apply(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0f || MODIFIERS.isEmpty()) {
			return amount;
		}

		float product = 1f;

		for (DamageModifier modifier : MODIFIERS) {
			product *= modifier.modify(victim, source, amount) / amount;
		}

		ProgressionBalance b = Progression.balance();
		return amount * Mth.clamp(product, b.damageModifierMin, b.damageModifierMax);
	}

	private DamageModifiers() {
	}
}
