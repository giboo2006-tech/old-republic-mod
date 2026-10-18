package com.oldrepublic.saber;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oldrepublic.skill.SkillPassives;
import com.oldrepublic.skill.Skills;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The skill nodes the saber feature reads live: the active form and the Lightsaber-tree passives
 * that change saber combat. Node ownership comes from {@link Skills} (works on both sides); the
 * Jedi and Sith passives' numbers come from {@link SkillPassives} (server).
 */
public final class SaberPassives {
	public static final String LUNGE = "saber_lunge";
	public static final String STEADY_GUARD = "steady_guard";
	public static final String DEFLECTION_CONTROL = "deflection_control";
	public static final String RIPOSTE = "riposte";
	public static final String SABER_THROW = "saber_throw";
	public static final String THROW_RICOCHET = "throw_ricochet";
	public static final String THROW_RENDING = "throw_rending_arc";

	/** Owned and not dormant. */
	public static boolean has(Player player, String nodeId) {
		return Skills.active(player, nodeId);
	}

	/** Owned rank of an active node, 0 when not owned or dormant. */
	public static int rank(Player player, String nodeId) {
		return Skills.activeRank(player, nodeId);
	}

	/** The active form; a form node that is no longer active counts as Unformed. */
	public static SaberForm form(Player player) {
		SaberForm form = SaberForm.byNode(Skills.activeForm(player));
		return form == SaberForm.UNFORMED || has(player, form.nodeId()) ? form : SaberForm.UNFORMED;
	}

	/**
	 * {@code saber}'s stats for this player with the form and {@code steady_guard} applied, plus
	 * {@code extraParryTicks} and a steady lens's ticks (M4: the stack's own stats, so a saberstaff
	 * or training saber counts as one).
	 */
	public static SaberStats stats(Player player, ItemStack saber, SaberBalance b, float extraParryTicks) {
		return SaberStats.of(saber, b)
				.withForm(form(player).modifiers(b), rank(player, STEADY_GUARD), extraParryTicks + SaberLens.parryTicks(player, saber, b), b);
	}

	/**
	 * Server: the same, with the flat parry-window bonuses of {@code centered_mind} and
	 * {@code archivists_ward} (added before the form multiplier and the cap, spec 10.4).
	 */
	public static SaberStats serverStats(Player player, ItemStack saber) {
		SaberBalance b = SaberToggle.balance();
		return stats(player, saber, b, SkillPassives.centeredParryTicks(player) + SaberNodes.wardTicks(player, b));
	}

	/**
	 * The saber tree's tooltip values for the skills feature's {@code NodeInfo} source
	 * {@code "saber"}. Percentages are the multiplier in percent (a damage multiplier of 0.85 is
	 * 85), except {@code riposte_damage_pct} and {@code steady_guard_pct}, which are bonuses.
	 */
	public static Map<String, Float> nodeInfo() {
		SaberBalance b = SaberToggle.balance();
		Map<String, Float> values = new LinkedHashMap<>();
		values.put("lunge_range_min", b.lungeMinRange);
		values.put("lunge_range_max", b.lungeMaxRange);
		values.put("lunge_damage_pct", b.lungeDamageMult * 100f);
		values.put("lunge_stamina", b.lungeStaminaCost);
		values.put("lunge_cooldown_s", b.lungeCooldownTicks / 20f);
		values.put("steady_guard_pct", b.steadyGuardPerRank * 100f);
		values.put("riposte_window_s", b.riposteWindowTicks / 20f);
		values.put("riposte_damage_pct", (b.riposteDamageMult - 1f) * 100f);
		values.put("deflection_degrees", b.deflectionControlDegrees);
		values.put("soresu_parry_pct", b.soresuParryMult * 100f);
		values.put("soresu_guard_arc", b.soresuGuardArc);
		values.put("soresu_drain_pct", b.soresuDrainMult * 100f);
		values.put("soresu_damage_pct", b.soresuDamageMult * 100f);
		values.put("ataru_speed", b.ataruSpeed);
		values.put("ataru_stamina_pct", b.ataruStaminaMult * 100f);
		values.put("ataru_aerial_pct", b.ataruAerialMult * 100f);
		values.put("ataru_jump_cost_pct", b.ataruJumpCostMult * 100f);
		values.put("form_switch_s", b.formSwitchTicks / 20f);
		return values;
	}

	private SaberPassives() {
	}
}
