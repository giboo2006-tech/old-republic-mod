package com.oldrepublic.equipment;

import com.oldrepublic.combat.DamageModifier;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.progression.ForceModifier;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Applies the worn era armour traits through the shared modifier hooks (spec section 9.2): Force
 * cost, regeneration and max energy ({@link ForceModifier}), blaster and melee resistance
 * ({@link DamageModifier}) and Iron Discipline ({@link ControlEffects.DurationModifier}). The set of
 * each piece comes from its component; the numbers are the live {@link EquipmentBalance}. Server side.
 */
public final class EquipmentModifiers implements ForceModifier, DamageModifier, ControlEffects.DurationModifier {
	@Override
	public float costMultiplier(Player p, String abilityId) {
		SetBonusRules.Worn worn = ArmourSets.worn(p);
		return worn.equals(SetBonusRules.Worn.NONE) ? 1f : SetBonusRules.costMultiplier(worn, ArmourSets.wearer(p), abilityId, EquipmentBalance.get());
	}

	@Override
	public float regenMultiplier(Player p) {
		SetBonusRules.Worn worn = ArmourSets.worn(p);
		return worn.equals(SetBonusRules.Worn.NONE) ? 1f : SetBonusRules.regenMultiplier(worn, EquipmentBalance.get());
	}

	@Override
	public int maxEnergyBonus(Player p) {
		SetBonusRules.Worn worn = ArmourSets.worn(p);
		return worn.sithRobes() == 0 ? 0 : SetBonusRules.maxEnergyBonus(worn, EquipmentBalance.get());
	}

	/** Blaster damage (direct entity a {@link BlasterBolt}) and melee damage (a direct living attacker). */
	@Override
	public float modify(LivingEntity victim, DamageSource source, float amount) {
		SetBonusRules.Worn worn = ArmourSets.worn(victim);

		if (source.getDirectEntity() instanceof BlasterBolt && worn.republicTrooper() > 0) {
			return amount * SetBonusRules.blasterMultiplier(worn, EquipmentBalance.get());
		}

		if (source.getDirectEntity() instanceof LivingEntity && worn.sithTrooper() > 0) {
			return amount * SetBonusRules.meleeMultiplier(worn, EquipmentBalance.get());
		}

		return amount;
	}

	/** Iron Discipline: every control kind on a wearer of the full Sith trooper set. */
	@Override
	public float multiplier(LivingEntity target, String kind) {
		SetBonusRules.Worn worn = ArmourSets.worn(target);
		return worn.full(ArmourSet.SITH_TROOPER) ? SetBonusRules.controlMultiplier(worn, EquipmentBalance.get()) : 1f;
	}
}
