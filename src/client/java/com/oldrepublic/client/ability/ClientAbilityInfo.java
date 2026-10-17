package com.oldrepublic.client.ability;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.AbilityCosts;
import com.oldrepublic.ability.AbilityInfoKeys;
import com.oldrepublic.ability.AbilityRuntime;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.ability.network.AbilityInfoPayload;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.skill.SkillBalance;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.Skills;

import net.minecraft.world.entity.player.Player;

/**
 * The server's live ability numbers ({@code ability_info}), kept for the Force screen's detail
 * panel and the HUD sockets. Empty until the server has sent them; readers fall back to "no data".
 */
public final class ClientAbilityInfo {
	private static Map<String, AbilityInfoPayload.Entry> entries = Map.of();

	public static void set(AbilityInfoPayload payload) {
		entries = Map.copyOf(payload.entries());
	}

	public static void reset() {
		entries = Map.of();
	}

	/** True once the server has sent its numbers. */
	public static boolean received() {
		return !entries.isEmpty();
	}

	/** The values of an ability or info source, or an empty map. */
	public static Map<String, Float> values(@Nullable String sourceId) {
		AbilityInfoPayload.Entry entry = sourceId == null ? null : entries.get(sourceId);
		return entry == null ? Map.of() : entry.values();
	}

	/** One value, or null when the source or key is unknown. */
	public static @Nullable Float value(@Nullable String sourceId, String key) {
		return values(sourceId).get(key);
	}

	/** How the ability reads its key; TAP when unknown. */
	public static InputStyle inputStyle(String abilityId) {
		AbilityInfoPayload.Entry entry = entries.get(abilityId);
		InputStyle[] styles = InputStyle.values();
		return entry == null || entry.inputStyle() < 0 || entry.inputStyle() >= styles.length ? InputStyle.TAP : styles[entry.inputStyle()];
	}

	/** Energy to start the ability before modifiers, or 0 when unknown. */
	public static float startCost(String abilityId) {
		return AbilityInfoKeys.startCost(values(abilityId));
	}

	/** Ticks of hold for a full charge (hold-and-release), or 0 when unknown. */
	public static int fullChargeTicks(String abilityId) {
		Float seconds = value(abilityId, AbilityInfoKeys.FULL_CHARGE);
		return seconds == null ? 0 : Math.round(seconds * 20f);
	}

	/** Ticks of hold before a release fires (hold-and-release), or 0 when unknown. */
	public static int minChargeTicks(String abilityId) {
		Float seconds = value(abilityId, AbilityInfoKeys.MIN_CHARGE);
		return seconds == null ? 0 : Math.round(seconds * 20f);
	}

	/** Longest channel in ticks, or 0 when unknown. */
	public static int maxChannelTicks(String abilityId) {
		Float seconds = value(abilityId, AbilityInfoKeys.MAX_CHANNEL);
		return seconds == null ? 0 : Math.round(seconds * 20f);
	}

	/**
	 * The cost multiplier the server applies for this player: {@code unbound} and opposed alignment,
	 * using the server's numbers from the {@code skills} info source, times the other features'
	 * multipliers the server sends in the player's own {@link AbilityRuntime#COST_MODIFIERS} entry
	 * (armour traits, carried holocrons, {@code watchers_insight}, forms), 1 when not sent.
	 */
	public static float costMultiplier(Player player, String abilityId) {
		SkillBalance b = new SkillBalance();
		Map<String, Float> skills = values("skills");
		b.unboundCostMult = skills.getOrDefault("unbound_cost_mult", b.unboundCostMult);
		b.opposedCostMult = skills.getOrDefault("opposed_cost_mult", b.opposedCostMult);
		float others = values(AbilityRuntime.COST_MODIFIERS).getOrDefault(abilityId, 1f);
		return AbilityCosts.costMultiplier(Skills.active(player, SkillIds.UNBOUND), AbilityCosts.treeOf(player, abilityId), Identity.alignment(player), b)
				* others;
	}

	/** True when the player's alignment refuses this power (drawn cross-hatched on the HUD). */
	public static boolean refused(Player player, String abilityId) {
		return AbilityCosts.opposition(AbilityCosts.treeOf(player, abilityId), Identity.alignment(player)) == AbilityCosts.Opposition.REFUSED;
	}

	private ClientAbilityInfo() {
	}
}
