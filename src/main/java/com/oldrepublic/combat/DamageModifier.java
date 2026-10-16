package com.oldrepublic.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** A source of incoming damage scaling (armour traits, boss wards). Register it with {@link DamageModifiers#register}. */
@FunctionalInterface
public interface DamageModifier {
	/**
	 * What this modifier makes of {@code amount}, the unmodified damage {@code victim} is taking from
	 * {@code source}; return {@code amount} to leave it alone. Server side.
	 */
	float modify(LivingEntity victim, DamageSource source, float amount);
}
