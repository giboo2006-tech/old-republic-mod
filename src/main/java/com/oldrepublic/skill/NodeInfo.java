package com.oldrepublic.skill;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.oldrepublic.holocron.HolocronBalance;

/**
 * Extra sources of live numbers for node tooltips, beside the abilities' own {@code info()} maps.
 * A node names its source with {@code "info_source"} in the tree JSON; the server sends every
 * source in the {@code ability_info} payload. The skills feature registers {@code skills} (its own
 * passives); the saber feature registers {@code saber} for its passives, forms and techniques.
 */
public final class NodeInfo {
	private static final Map<String, Supplier<Map<String, Float>>> SOURCES = new LinkedHashMap<>();

	/** Registers a source. Registering the same id twice is a programming error. */
	public static void register(String sourceId, Supplier<Map<String, Float>> values) {
		if (SOURCES.putIfAbsent(sourceId, values) != null) {
			throw new IllegalStateException("Node info source " + sourceId + " is already registered");
		}
	}

	/** Current values of every source (read on the server when the payload is built). */
	public static Map<String, Map<String, Float>> snapshot() {
		Map<String, Map<String, Float>> out = new LinkedHashMap<>();
		SOURCES.forEach((id, supplier) -> out.put(id, Map.copyOf(supplier.get())));
		return out;
	}

	/** The skills feature's own values, keyed as the trees' {@code info} lists use them. */
	static Map<String, Float> skillValues() {
		SkillBalance b = SkillService.balance();
		Map<String, Float> values = new LinkedHashMap<>();
		values.put("reserves_energy", (float) b.deepReservesPerRank);
		values.put("flow_regen_pct", (float) b.quickenedRegenPctPerRank);
		values.put("flow_delay_s", b.quickenedDelayPerRank / 20f);
		values.put("unbound_cost_pct", Math.round((1f - b.unboundCostMult) * 100f) * 1f);
		values.put("unbound_cooldown_pct", Math.round((1f - b.unboundCooldownMult) * 100f) * 1f);
		values.put("centered_parry_ticks", (float) b.centeredParryTicksPerRank);
		values.put("calm_delay_s", b.calmFocusDelay / 20f);
		values.put("calm_energy_pct", (float) b.calmFocusMaxEnergyPct);
		values.put("guardians_reduction_pct", b.guardiansResolveReduction * 100f);
		values.put("guardians_radius", b.guardiansResolveRadius);
		values.put("fury_pct", b.furyPerRank * 100f);
		values.put("fury_health_pct", b.furyHealthFraction * 100f);
		values.put("hungering_energy", (float) b.hungeringEnergy);
		values.put("hungering_cooldown_s", b.hungeringCooldownTicks / 20f);
		values.put("dread_seconds", b.dreadFleeTicks / 20f);
		values.put("opposed_cost_mult", b.opposedCostMult);
		values.put("unbound_cost_mult", b.unboundCostMult);
		values.put("unbound_cooldown_mult", b.unboundCooldownMult);
		values.put("switch_penalty", (float) b.switchPenalty);
		values.put("respec_loss_pct", b.respecLoss * 100f);
		values.put("respec_cooldown_ticks", (float) b.respecCooldownTicks);
		values.put("respec_radius", (float) b.respecAnchorRadius);
		explorationValues(b, values);
		return values;
	}

	/** The Milestone 3 nodes: Exploration passives and the granted holocron nodes. */
	private static void explorationValues(SkillBalance b, Map<String, Float> values) {
		values.put("trailwise_fall_pct", SkillAttributes.rankValue(b.trailwiseFall, 1) * 100f);
		values.put("trailwise_fall_max_pct", SkillAttributes.rankValue(b.trailwiseFall, 2) * 100f);
		values.put("trailwise_hunger_pct", SkillAttributes.rankValue(b.trailwiseHunger, 1) * 100f);
		values.put("trailwise_hunger_max_pct", SkillAttributes.rankValue(b.trailwiseHunger, 2) * 100f);
		values.put("relic_xp_pct", HolocronBalance.get().relicLoreXpBonus * 100f);
		values.put("hardy_health_1", (float) (b.hardyHealth.length > 0 ? b.hardyHealth[0] : 0));
		values.put("hardy_health_2", (float) (b.hardyHealth.length > 1 ? b.hardyHealth[1] : 0));
		values.put("scavenger_pct", b.scavengerChance * 100f);
		values.put("quiet_wake_range", (float) b.quietStepWake);
		values.put("quiet_warn_range", (float) b.quietStepWarn);
		values.put("quiet_wary_s", b.quietStepWaryTicks / 20f);
		values.put("road_sense_cooldown_pct", b.archivistsRoadSenseCooldown * 100f);
		values.put("road_lift_cost_pct", b.archivistsRoadLiftCost * 100f);
		values.put("pathfinder_speed_pct", b.pathfinderSpeed * 100f);
		values.put("pathfinder_fall_pct", b.pathfinderFall * 100f);
		values.put("fall_cap_pct", b.fallReductionCap * 100f);
		values.put("insight_heal_cost_pct", b.watchersInsightHealCost * 100f);
		values.put("tomb_energy", (float) b.tombHungerFe);
		values.put("tomb_window_s", b.tombHungerWindowTicks / 20f);
		values.put("crackling_slow_pct", b.cracklingSlow * 100f);
		values.put("crackling_s", b.cracklingTicks / 20f);
		values.put("hunger_bonus_pct", b.archivistsHungerBonus * 100f);
		values.put("hunger_health_pct", b.archivistsHungerThreshold * 100f);
	}

	private NodeInfo() {
	}
}
