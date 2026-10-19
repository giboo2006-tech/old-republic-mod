package com.oldrepublic.force.valor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceMobEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.heal.ForceHeal;
import com.oldrepublic.force.heal.HealBalance;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.skill.CombatTracker;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Force Valor ({@code force_valor}, Jedi Master, granted at the Master ceremony, MILESTONE_5.md 9.4): a tap that gives the
 * caster and, within 8 blocks, every player Shared Light counts as an ally ({@link ForceHeal#isShareTarget}) the
 * {@code valor} effect for 200 ticks: +20% attack damage, +4 armour and +0.5 knockback resistance. Rallying Light
 * ({@code valor_shared}) reaches 16 blocks and each ally regains 4 health at the start; Steadfast ({@code valor_steadfast})
 * is for the caster alone, with +30% attack damage and no {@code stagger} while it lasts.
 *
 * <p>The numbers are transient attribute modifiers with fixed ids, kept in step with the effect by {@link #sync}, which
 * runs for every player every server tick: they go when the effect ends for any reason (expiry, death, milk,
 * {@code /effect clear}) and come back for a player who logs in with it.
 */
public final class ForceValor implements ForceAbility {
	public static final String ID = "force_valor";
	public static final String SHARED = "valor_shared";
	public static final String STEADFAST = "valor_steadfast";
	/**
	 * Effect event {@code force_valor} (15.3): toTracking; variant 0 cast (entity = the caster), 1 an ally touched
	 * (entity = the ally), 2 the end on one player (entity = that player); intensity = seconds (variants 0 and 1).
	 */
	public static final Identifier EFFECT = OldRepublicMod.id("force_valor");
	public static final int VARIANT_CAST = 0;
	public static final int VARIANT_ALLY = 1;
	public static final int VARIANT_END = 2;
	/** The transient modifiers' fixed ids. */
	public static final Identifier DAMAGE_ID = OldRepublicMod.id("valor_damage");
	public static final Identifier ARMOR_ID = OldRepublicMod.id("valor_armor");
	public static final Identifier KNOCKBACK_ID = OldRepublicMod.id("valor_knockback");
	/**
	 * Players whose Valor now is Steadfast's (the stronger damage, no stagger). In memory: a player who comes back after a
	 * restart with the effect is judged by {@link #guessSteadfast}.
	 */
	private static final Set<UUID> STEADFAST_NOW = ConcurrentHashMap.newKeySet();

	public static ValorBalance balance() {
		return BalanceConfig.section("force_valor", ValorBalance.class, ValorBalance::new);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public InputStyle inputStyle() {
		return InputStyle.TAP;
	}

	@Override
	public void onInput(ServerPlayer p, AbilityAction action) {
		if (action == AbilityAction.START) {
			cast(p);
		}
	}

	/** A tap: nothing to cancel (the effect runs its course). */
	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return false;
	}

	/**
	 * Keys: {@code cost}, {@code cooldown_s}, {@code duration_s}, {@code radius}, {@code damage_pct}, {@code armor},
	 * {@code knockback_resistance}, {@code shared_radius}, {@code shared_heal}, {@code steadfast_damage_pct},
	 * {@code mastery_valor_s}.
	 */
	@Override
	public Map<String, Float> info() {
		ValorBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("cost", b.cost);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("duration_s", b.durationTicks / 20f);
		info.put("radius", b.radius);
		info.put("damage_pct", b.attackDamagePct);
		info.put("armor", b.armor);
		info.put("knockback_resistance", b.knockbackResistance);
		info.put("shared_radius", b.sharedRadius);
		info.put("shared_heal", b.sharedHeal);
		info.put("steadfast_damage_pct", b.steadfastDamagePct);
		info.put("mastery_valor_s", b.masteryTicksPerRank / 20f);
		return info;
	}

	// ---------------------------------------------------------------- rules

	/** Valor's length at mastery {@code rank}. */
	public static int ticks(ValorBalance b, int rank) {
		return b.durationTicks + b.masteryTicksPerRank * Mth.clamp(rank, 0, 3);
	}

	/** The attack damage bonus as a fraction (0.2 for 20%): Steadfast's, or the plain one. */
	public static double damageFraction(ValorBalance b, boolean steadfast) {
		return (steadfast ? b.steadfastDamagePct : b.attackDamagePct) / 100.0;
	}

	/** Whether {@code player}'s Valor is Steadfast's now (no stagger, the stronger damage). */
	public static boolean isSteadfast(LivingEntity entity) {
		return entity instanceof Player && !STEADFAST_NOW.isEmpty() && STEADFAST_NOW.contains(entity.getUUID()) && entity.hasEffect(ForceMobEffects.VALOR);
	}

	/**
	 * The Steadfast {@code DurationModifier}: a {@code stagger} on a player under Steadfast lasts nothing (so it neither
	 * slows nor interrupts).
	 */
	public static float durationMultiplier(LivingEntity target, String kind) {
		return ControlEffects.STAGGER.equals(kind) && isSteadfast(target) ? 0f : 1f;
	}

	/** Allies Valor touches: other players Shared Light counts as allies, within {@code radius}, alive. */
	static List<ServerPlayer> allies(ServerPlayer caster, double radius) {
		HealBalance heal = ForceHeal.balance();
		List<ServerPlayer> out = new ArrayList<>();
		AABB box = caster.getBoundingBox().inflate(radius);

		for (Player other : caster.level().getEntitiesOfClass(Player.class, box,
				p -> p != caster && p.distanceToSqr(caster) <= radius * radius && ForceHeal.isShareTarget(caster, p, heal))) {
			if (other instanceof ServerPlayer ally) {
				out.add(ally);
			}
		}

		return out;
	}

	// ---------------------------------------------------------------- cast

	static void cast(ServerPlayer player) {
		if (!ForcePowers.mayStart(player, ID)) {
			return;
		}

		ValorBalance b = balance();

		if (!Progression.trySpendEnergy(player, b.cost * ForcePowers.costMultiplier(player, ID))) {
			return;
		}

		Progression.setCooldown(player, ID, ForcePowers.cooldown(player, ID, b.cooldownTicks));
		ForceData data = Progression.get(player);
		boolean shared = ForcePowers.hasNode(player, data, SHARED);
		boolean steadfast = !shared && ForcePowers.hasNode(player, data, STEADFAST);
		int ticks = ticks(b, ForcePowers.masteryRank(player, ID));
		List<ServerPlayer> allies = steadfast ? List.of() : allies(player, shared ? b.sharedRadius : b.radius);
		grant(player, ticks, steadfast, b);
		EffectEvents.toTracking(player, EFFECT, player.position(), null, player.getId(), ticks / 20f, VARIANT_CAST);

		for (ServerPlayer ally : allies) {
			grant(ally, ticks, false, b);

			if (shared && b.sharedHeal > 0f) {
				ally.heal(b.sharedHeal);
			}

			EffectEvents.toTracking(ally, EFFECT, ally.position(), null, ally.getId(), ticks / 20f, VARIANT_ALLY);
			QuestHooks.onAbilityHit(player, ID, ally);
		}

		if (!allies.isEmpty() || CombatTracker.inCombat(player)) {
			ForcePowers.addMastery(player, ID, 1);
		}

		QuestHooks.onAbilityUsed(player, ID, 0);
	}

	/** Gives {@code target} Valor for {@code ticks} and its modifiers now. Steadfast never weakens a Steadfast Valor. */
	static void grant(ServerPlayer target, int ticks, boolean steadfast, ValorBalance b) {
		if (steadfast) {
			STEADFAST_NOW.add(target.getUUID());
		} else if (!isSteadfast(target)) {
			STEADFAST_NOW.remove(target.getUUID());
		}

		ForceMobEffects.apply(target, ForceMobEffects.VALOR, ticks, target);
		applyModifiers(target, isSteadfast(target) || steadfast, b);
	}

	// ---------------------------------------------------------------- the modifiers

	/**
	 * Keeps {@code player}'s modifiers in step with the effect; called for every player every server tick. When the
	 * effect has gone they go too (with the end event); when it is there without them (a login) they come back.
	 */
	public static void sync(ServerPlayer player) {
		boolean on = player.isAlive() && player.hasEffect(ForceMobEffects.VALOR);
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		boolean has = damage != null && damage.hasModifier(DAMAGE_ID);

		if (on && !has) {
			if (!STEADFAST_NOW.contains(player.getUUID()) && guessSteadfast(player)) {
				STEADFAST_NOW.add(player.getUUID());
			}

			applyModifiers(player, isSteadfast(player), balance());
		} else if (!on && has) {
			removeModifiers(player);
			STEADFAST_NOW.remove(player.getUUID());
			EffectEvents.toTracking(player, EFFECT, player.position(), null, player.getId(), 0f, VARIANT_END);
		} else if (!on) {
			STEADFAST_NOW.remove(player.getUUID());
		}
	}

	/** After a restart: a Steadfast owner with Valor whose own Valor is still cooling down cast it themselves. */
	private static boolean guessSteadfast(ServerPlayer player) {
		return ForcePowers.hasNode(player, Progression.get(player), STEADFAST) && Progression.isOnCooldown(player, ID);
	}

	static void applyModifiers(ServerPlayer player, boolean steadfast, ValorBalance b) {
		put(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, damageFraction(b, steadfast), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		put(player.getAttribute(Attributes.ARMOR), ARMOR_ID, b.armor, AttributeModifier.Operation.ADD_VALUE);
		put(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK_ID, b.knockbackResistance, AttributeModifier.Operation.ADD_VALUE);
	}

	/** Takes the three modifiers off (they are transient, so a save never holds them). */
	public static void removeModifiers(ServerPlayer player) {
		remove(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID);
		remove(player.getAttribute(Attributes.ARMOR), ARMOR_ID);
		remove(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK_ID);
	}

	/** Adds or updates one transient modifier ({@code amount} 0 removes it). */
	public static void put(@Nullable AttributeInstance attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		if (attribute == null) {
			return;
		}

		if (amount == 0.0) {
			remove(attribute, id);
			return;
		}

		AttributeModifier current = attribute.getModifier(id);

		if (current == null || current.amount() != amount || current.operation() != operation) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	public static void remove(@Nullable AttributeInstance attribute, Identifier id) {
		if (attribute != null && attribute.hasModifier(id)) {
			attribute.removeModifier(id);
		}
	}

	/** Whether {@code player} carries the modifier {@code id} on {@code attribute} now (tests). */
	public static boolean hasModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		return instance != null && instance.hasModifier(id);
	}

	/** Forgets whether {@code player}'s Valor was Steadfast's (they left the server; a login judges it again). */
	public static void forget(ServerPlayer player) {
		STEADFAST_NOW.remove(player.getUUID());
	}

	public static void clear() {
		STEADFAST_NOW.clear();
	}
}
