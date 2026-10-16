package com.oldrepublic.skill;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

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
		return values;
	}

	private NodeInfo() {
	}
}
