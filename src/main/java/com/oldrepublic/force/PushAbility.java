package com.oldrepublic.force;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;

import net.minecraft.server.level.ServerPlayer;

/**
 * Force Push on the ability framework: hold to charge, release to push. The behaviour and numbers
 * are Chapter One's, implemented in {@link ForcePush}.
 */
public final class PushAbility implements ForceAbility {
	@Override
	public String id() {
		return ForcePush.ABILITY_ID;
	}

	@Override
	public InputStyle inputStyle() {
		return InputStyle.HOLD_RELEASE;
	}

	@Override
	public void onInput(ServerPlayer p, AbilityAction action) {
		ForcePush.onInput(p, action);
	}

	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
		ForcePush.cancel(p, feedback);
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return PushCharges.isCharging(p);
	}

	/**
	 * Keys: {@code cost_min}, {@code cost_max}, {@code cooldown_s}, {@code range}, {@code cone},
	 * {@code knockback_min}, {@code knockback_max}, {@code min_charge_s}, {@code full_charge_s},
	 * {@code mastery_range_pct}, {@code mastery_cost_pct}, {@code repulse_cone}, {@code repulse_range},
	 * {@code repulse_knock_mult}, {@code stagger_s}, {@code focused_cone}, {@code focused_range},
	 * {@code focused_knock_mult}, {@code slam_damage_min}, {@code slam_damage_max},
	 * {@code deflect_radius}, {@code deflect_knock_mult}, {@code deflect_cooldown_s}.
	 */
	@Override
	public Map<String, Float> info() {
		PushBalance b = ForcePush.balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("cost_min", b.costMin);
		info.put("cost_max", b.costMax);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("range", b.baseRange);
		info.put("cone", b.baseHalfAngle * 2f);
		info.put("knockback_min", b.knockbackMin);
		info.put("knockback_max", b.knockbackMax);
		info.put("min_charge_s", b.minChargeTicks / 20f);
		info.put("full_charge_s", b.fullChargeTicks / 20f);
		info.put("mastery_range_pct", b.masteryRangePerRank * 100f);
		info.put("mastery_cost_pct", b.masteryCostPerRank * 100f);
		info.put("repulse_cone", b.repulseHalfAngle * 2f);
		info.put("repulse_range", b.repulseRange);
		info.put("repulse_knock_mult", b.repulseKnockback);
		info.put("stagger_s", b.repulseStaggerTicks / 20f);
		info.put("focused_cone", b.focusedHalfAngle * 2f);
		info.put("focused_range", b.focusedRange);
		info.put("focused_knock_mult", b.focusedKnockback);
		info.put("slam_damage_min", b.slamDamageBase);
		info.put("slam_damage_max", b.slamDamageBase + b.slamDamagePerCharge);
		info.put("deflect_radius", b.deflectRadius);
		info.put("deflect_knock_mult", b.deflectKnockback);
		info.put("deflect_cooldown_s", Math.round(b.cooldownTicks * b.deflectCooldownMult) / 20f);
		return info;
	}
}
