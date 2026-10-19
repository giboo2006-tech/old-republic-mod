package com.oldrepublic.force.rage;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ForceMobEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.valor.ForceValor;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.throwing.SaberThrow;
import com.oldrepublic.skill.SkillService;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/**
 * Force Rage ({@code force_rage}, Sith Darth, granted at the Darth ceremony, MILESTONE_5.md 9.7): a tap that gives the
 * caster {@code rage} for 200 ticks: +30% melee damage, +15% attack speed and +10% movement speed, at a price: the caster
 * takes 10% more damage and regenerates no Force Energy while it lasts ({@code SkillService} sets the regeneration
 * modifier to -100% while {@link #isRaging}). Bloodthirst ({@code rage_unbound}): each saber hit heals 1. Cold Fury
 * ({@code rage_cold}): no damage-taken penalty, but +20% melee damage instead of +30%.
 *
 * <p>As Valor's, the numbers are transient attribute modifiers with fixed ids kept in step with the effect by
 * {@link #sync}, every player every server tick.
 */
public final class ForceRage implements ForceAbility {
	public static final String ID = "force_rage";
	public static final String UNBOUND = "rage_unbound";
	public static final String COLD = "rage_cold";
	/** Effect event {@code force_rage} (15.3): toTracking; entity = the caster; variant 0 start (intensity = seconds), 1 end. */
	public static final Identifier EFFECT = OldRepublicMod.id("force_rage");
	public static final int VARIANT_START = 0;
	public static final int VARIANT_END = 1;
	/** The transient modifiers' fixed ids. */
	public static final Identifier DAMAGE_ID = OldRepublicMod.id("rage_damage");
	public static final Identifier ATTACK_SPEED_ID = OldRepublicMod.id("rage_attack_speed");
	public static final Identifier MOVE_SPEED_ID = OldRepublicMod.id("rage_move_speed");

	public static RageBalance balance() {
		return BalanceConfig.section("force_rage", RageBalance.class, RageBalance::new);
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
	 * Keys: {@code cost}, {@code cooldown_s}, {@code duration_s}, {@code melee_damage_pct}, {@code attack_speed_pct},
	 * {@code move_speed_pct}, {@code damage_taken_pct}, {@code unbound_heal}, {@code cold_damage_pct}, {@code mastery_rage_s}.
	 */
	@Override
	public Map<String, Float> info() {
		RageBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("cost", b.cost);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("duration_s", b.durationTicks / 20f);
		info.put("melee_damage_pct", b.meleeDamagePct);
		info.put("attack_speed_pct", b.attackSpeedPct);
		info.put("move_speed_pct", b.moveSpeedPct);
		info.put("damage_taken_pct", b.damageTakenPct);
		info.put("unbound_heal", b.unboundHeal);
		info.put("cold_damage_pct", b.coldDamagePct);
		info.put("mastery_rage_s", b.masteryTicksPerRank / 20f);
		return info;
	}

	// ---------------------------------------------------------------- rules

	/** Rage's length at mastery {@code rank}. */
	public static int ticks(RageBalance b, int rank) {
		return b.durationTicks + b.masteryTicksPerRank * Mth.clamp(rank, 0, 3);
	}

	/** The melee damage bonus as a fraction: Cold Fury's, or the plain one. */
	public static double damageFraction(RageBalance b, boolean cold) {
		return (cold ? b.coldDamagePct : b.meleeDamagePct) / 100.0;
	}

	/** Whether {@code entity} is raging now (any side: it reads the effect). */
	public static boolean isRaging(LivingEntity entity) {
		return entity.hasEffect(ForceMobEffects.RAGE);
	}

	private static boolean cold(ServerPlayer player) {
		return ForcePowers.hasNode(player, Progression.get(player), COLD);
	}

	// ---------------------------------------------------------------- cast

	static void cast(ServerPlayer player) {
		if (!ForcePowers.mayStart(player, ID)) {
			return;
		}

		RageBalance b = balance();

		if (!Progression.trySpendEnergy(player, b.cost * ForcePowers.costMultiplier(player, ID))) {
			return;
		}

		Progression.setCooldown(player, ID, ForcePowers.cooldown(player, ID, b.cooldownTicks));
		int ticks = ticks(b, ForcePowers.masteryRank(player, ID));
		ForceMobEffects.apply(player, ForceMobEffects.RAGE, ticks, player);
		applyModifiers(player, cold(player), b);
		// The regeneration stops now, not at the next periodic refresh.
		SkillService.refresh(player);
		EffectEvents.toTracking(player, EFFECT, player.position(), null, player.getId(), ticks / 20f, VARIANT_START);
		QuestHooks.onAbilityUsed(player, ID, 0);
	}

	// ---------------------------------------------------------------- the modifiers and hooks

	/**
	 * Keeps {@code player}'s modifiers and regeneration in step with the effect; called for every player every server
	 * tick. When the effect has gone they go (with the end event and a refresh that brings the regeneration back); when
	 * it is there without them (a login) they come back.
	 */
	public static void sync(ServerPlayer player) {
		boolean on = player.isAlive() && isRaging(player);
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		boolean has = damage != null && damage.hasModifier(DAMAGE_ID);

		if (on && !has) {
			applyModifiers(player, cold(player), balance());
			SkillService.refresh(player);
		} else if (!on && has) {
			removeModifiers(player);
			SkillService.refresh(player);
			EffectEvents.toTracking(player, EFFECT, player.position(), null, player.getId(), 0f, VARIANT_END);
		}
	}

	static void applyModifiers(ServerPlayer player, boolean cold, RageBalance b) {
		ForceValor.put(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, damageFraction(b, cold), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ForceValor.put(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_ID, b.attackSpeedPct / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ForceValor.put(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVE_SPEED_ID, b.moveSpeedPct / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	public static void removeModifiers(ServerPlayer player) {
		ForceValor.remove(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID);
		ForceValor.remove(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_ID);
		ForceValor.remove(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVE_SPEED_ID);
	}

	/** The price ({@code DamageModifier}): a raging player takes {@code damageTakenPct} more, unless Cold Fury. */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof ServerPlayer player) || !isRaging(player)) {
			return amount;
		}

		RageBalance b = balance();
		return cold(player) ? amount : amount * (1f + b.damageTakenPct / 100f);
	}

	/**
	 * Fabric {@code AFTER_DAMAGE}: a hit a raging player landed. Each one counts for mastery (on a foe or another player);
	 * with Bloodthirst a saber hit (the lit blade in hand, or the thrown saber) heals {@code unboundHeal}.
	 */
	public static void onDamaged(LivingEntity victim, DamageSource source, float damageTaken) {
		if (damageTaken <= 0f || !(source.getEntity() instanceof ServerPlayer player) || player == victim || !isRaging(player)
				|| victim instanceof ArmorStand) {
			return;
		}

		boolean melee = source.getDirectEntity() == player;
		boolean saber = melee && (SaberComponents.isUsable(player.getMainHandItem()) || SaberComponents.isUsable(player.getOffhandItem()))
				|| source.is(SaberThrow.DAMAGE);

		if (!melee && !saber) {
			return;
		}

		if (victim instanceof Player || ForcePowers.isHostile(player, victim)) {
			ForcePowers.addMastery(player, ID, 1);
		}

		ForceData data = Progression.get(player);

		if (saber && ForcePowers.hasNode(player, data, UNBOUND)) {
			player.heal(balance().unboundHeal);
		}
	}
}
