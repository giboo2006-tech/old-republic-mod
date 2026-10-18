package com.oldrepublic.mixin.equipment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.LivingEntity;

/**
 * The attack strength ticker, so the vibroblade's flurry hit can leave the attack cooldown charged
 * (spec MILESTONE_4.md 5.2): the server restores it after the hit, and the wielder's client does the
 * same when the flurry's effect arrives.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAttackStrengthAccessor {
	// LivingEntity#attackStrengthTicker is a protected int in 26.2 (probe core.txt).
	@Accessor("attackStrengthTicker")
	int oldrepublic$getAttackStrengthTicker();

	@Accessor("attackStrengthTicker")
	void oldrepublic$setAttackStrengthTicker(int ticks);
}
