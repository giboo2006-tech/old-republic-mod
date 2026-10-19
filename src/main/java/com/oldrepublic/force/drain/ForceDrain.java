package com.oldrepublic.force.drain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceMobEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.force.choke.ForceChoke;
import com.oldrepublic.force.pull.PullTargeting;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Force Drain ({@code force_drain}, Sith, MILESTONE_5.md 9.5): a channel at one living target within 10 blocks, in sight
 * (the M2 single-target aim). It costs 10 FE to start and 2 per pulse; a pulse every 10 ticks, at most 6. Each pulse
 * deals 2 damage ({@code oldrepublic:force_drain}, in {@code #oldrepublic:force_harm}), heals the caster 1.5 and gives
 * back 2 FE, and the target is {@code drained} (Slowness I, hidden) while it lasts. It ends when the target leaves range
 * or sight, the caster is staggered, the key is released, or the last pulse lands; the 300-tick cooldown runs from the
 * end. Droids ({@code #oldrepublic:droids}) and the starfighters ({@code #oldrepublic:ship_targets}) have no life to
 * drain: refused, nothing spent.
 *
 * <p>Drain Life ({@code drain_life}): heal ×2, damage ×0.75. Drain Essence ({@code drain_essence}): +4 FE per pulse, and
 * each pulse interrupts the target's wind-up ({@link ForceReactions#interruptWindup} with the cause {@link #CAUSE_DRAIN};
 * bosses decide through their handler); a drained player (PvP on) pays double for powers meanwhile.
 */
public final class ForceDrain implements ForceAbility {
	public static final String ID = "force_drain";
	public static final String LIFE = "drain_life";
	public static final String ESSENCE = "drain_essence";
	/** The interrupt cause of Drain Essence's pulses (MILESTONE_5.md 9.5); {@code ForceReactions} names it for the handlers. */
	public static final String CAUSE_DRAIN = "drain";
	/** Damage type of a pulse (armour applies; in {@code #oldrepublic:force_harm}). */
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("force_drain"));
	/**
	 * Effect event {@code force_drain} (15.3): toTracking the caster; entity = the caster; variant =
	 * {@link ForceEffects#packCaster}(the target's id, 0 start / 1 pulse / 2 end), so the client can draw the stream from
	 * the target as both move. Start: pos = the target's chest, intensity = the longest channel in seconds; a pulse: pos =
	 * the target's chest, dir = from the caster to the target, intensity = the distance; the end: pos = the target's chest.
	 */
	public static final Identifier EFFECT = OldRepublicMod.id("force_drain");
	public static final int VARIANT_START = 0;
	public static final int VARIANT_PULSE = 1;
	public static final int VARIANT_END = 2;
	/** The starfighters, the gunship and its generator (W2's tag): no life to drain. */
	public static final TagKey<EntityType<?>> SHIP_TARGETS = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("ship_targets"));
	/** The hidden Slowness and the {@code drained} effect outlast each pulse by this much, so they hold between pulses. */
	static final int LINGER_TICKS = 5;

	/** One running drain. */
	private static final class Channel {
		final ServerPlayer caster;
		final LivingEntity target;
		final boolean life;
		final boolean essence;
		int age;
		int pulses;

		Channel(ServerPlayer caster, LivingEntity target, boolean life, boolean essence) {
			this.caster = caster;
			this.target = target;
			this.life = life;
			this.essence = essence;
		}
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	/** Players drained by Drain Essence (PvP), to the game time their double cost lasts. */
	private static final Map<UUID, Long> ESSENCE_DRAINED = new HashMap<>();

	public static DrainBalance balance() {
		return BalanceConfig.section("force_drain", DrainBalance.class, DrainBalance::new);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public InputStyle inputStyle() {
		return InputStyle.CHANNEL;
	}

	@Override
	public void onInput(ServerPlayer p, AbilityAction action) {
		switch (action) {
			case START -> start(p);
			case RELEASE, CANCEL -> end(p, true);
		}
	}

	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
		end(p, feedback);
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return CHANNELS.containsKey(p.getUUID());
	}

	/**
	 * Keys: {@code start_cost}, {@code cost_per_s}, {@code max_channel_s}, {@code cooldown_s}, {@code range},
	 * {@code pulse_damage}, {@code pulse_heal}, {@code pulse_energy}, {@code life_heal_mult}, {@code life_damage_mult},
	 * {@code essence_energy}, {@code drained_cost_mult}, {@code mastery_drain_damage}.
	 */
	@Override
	public Map<String, Float> info() {
		DrainBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		int every = Math.max(1, b.pulseTicks);
		info.put("start_cost", b.startCost);
		info.put("cost_per_s", b.pulseCost * 20f / every);
		info.put("max_channel_s", b.maxPulses * every / 20f);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("range", b.range);
		info.put("pulse_damage", b.pulseDamage);
		info.put("pulse_heal", b.pulseHeal);
		info.put("pulse_energy", b.pulseEnergy);
		info.put("life_heal_mult", b.lifeHealMultiplier);
		info.put("life_damage_mult", b.lifeDamageMultiplier);
		info.put("essence_energy", b.essenceEnergy);
		info.put("drained_cost_mult", b.drainedCostMultiplier);
		info.put("mastery_drain_damage", b.masteryDamagePerRank);
		return info;
	}

	// ---------------------------------------------------------------- rules

	/** Whether {@code target} has life to drain: not a droid, a starfighter or an armour stand. */
	public static boolean hasLife(LivingEntity target) {
		return !target.is(ForceChoke.DROIDS) && !target.is(SHIP_TARGETS) && !(target instanceof ArmorStand);
	}

	/** One pulse's damage at mastery {@code rank}, with Drain Life's multiplier. */
	public static float pulseDamage(DrainBalance b, boolean life, int rank) {
		float base = b.pulseDamage + b.masteryDamagePerRank * Mth.clamp(rank, 0, 3);
		return life ? base * b.lifeDamageMultiplier : base;
	}

	/** One pulse's heal, with Drain Life's multiplier. */
	public static float pulseHeal(DrainBalance b, boolean life) {
		return life ? b.pulseHeal * b.lifeHealMultiplier : b.pulseHeal;
	}

	/** The Force Energy one pulse gives back, with Drain Essence's. */
	public static float pulseEnergy(DrainBalance b, boolean essence) {
		return b.pulseEnergy + (essence ? b.essenceEnergy : 0f);
	}

	/** Whether {@code player} is draining someone now. */
	public static boolean isDraining(Player player) {
		return CHANNELS.containsKey(player.getUUID());
	}

	/** The creature {@code player} is draining, or null. */
	public static @Nullable LivingEntity targetOf(Player player) {
		Channel channel = CHANNELS.get(player.getUUID());
		return channel == null ? null : channel.target;
	}

	/** The pulse damage source caused by {@code caster}. */
	public static DamageSource source(ServerLevel level, ServerPlayer caster) {
		return ForceDamage.source(level, DAMAGE, caster);
	}

	// ---------------------------------------------------------------- start

	static void start(ServerPlayer player) {
		if (CHANNELS.containsKey(player.getUUID()) || !ForcePowers.mayStart(player, ID)) {
			return;
		}

		DrainBalance b = balance();
		ServerLevel level = player.level();
		LivingEntity target = PullTargeting.find(level, player, player.getEyePosition(), player.getLookAngle(), b.range, b.aimAssistHalfAngle,
				e -> ForcePowers.canAffect(player, e, b.pvpEffects), e -> !Attitudes.shieldedFromArea(player, e));

		if (target == null) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_drain.no_target"));
			return;
		}

		if (!hasLife(target)) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_drain.no_life"));
			return;
		}

		if (!Progression.trySpendEnergy(player, b.startCost * ForcePowers.costMultiplier(player, ID))) {
			return;
		}

		ForceData data = Progression.get(player);
		boolean life = ForcePowers.hasNode(player, data, LIFE);
		boolean essence = !life && ForcePowers.hasNode(player, data, ESSENCE);
		CHANNELS.put(player.getUUID(), new Channel(player, target, life, essence));
		hold(target, player, b);
		boolean hostile = ForcePowers.isHostile(player, target);
		EffectEvents.toTracking(player, EFFECT, ForcePowers.chest(target), direction(player, target), player.getId(),
				b.maxPulses * Math.max(1, b.pulseTicks) / 20f, ForceEffects.packCaster(target.getId(), VARIANT_START));
		QuestHooks.onAbilityUsed(player, ID, hostile ? 1 : 0);
		QuestHooks.onAbilityHit(player, ID, target);
	}

	/** From the caster's eyes to the target's chest, a unit vector. */
	private static Vec3 direction(ServerPlayer caster, LivingEntity target) {
		Vec3 to = ForcePowers.chest(target).subtract(caster.getEyePosition());
		return to.lengthSqr() < 1.0e-6 ? caster.getLookAngle() : to.normalize();
	}

	// ---------------------------------------------------------------- ticking

	/** Advances every drain; called every server tick. */
	public static void tick() {
		if (CHANNELS.isEmpty()) {
			return;
		}

		DrainBalance b = balance();

		for (Channel channel : new ArrayList<>(CHANNELS.values())) {
			tickChannel(channel, b);
		}
	}

	private static void tickChannel(Channel channel, DrainBalance b) {
		ServerPlayer caster = channel.caster;
		LivingEntity target = channel.target;

		if (!caster.isAlive() || caster.isRemoved()) {
			finish(channel, false);
			return;
		}

		if (!target.isAlive() || target.isRemoved() || target.level() != caster.level()) {
			finish(channel, true);
			return;
		}

		ServerLevel level = caster.level();

		if (caster.getEyePosition().distanceTo(ForcePowers.chest(target)) > b.range + 2.0
				|| !PushTargeting.hasLineOfSight(level, caster, caster.getEyePosition(), ForcePowers.chest(target))) {
			finish(channel, true);
			return;
		}

		channel.age++;

		if (channel.age % Math.max(1, b.pulseTicks) != 0) {
			return;
		}

		if (!Progression.trySpendEnergy(caster, b.pulseCost * ForcePowers.costMultiplier(caster, ID))) {
			finish(channel, true);
			return;
		}

		pulse(channel, level, b);

		if (CHANNELS.get(caster.getUUID()) == channel && channel.pulses >= b.maxPulses) {
			finish(channel, true);
		}
	}

	private static void pulse(Channel channel, ServerLevel level, DrainBalance b) {
		ServerPlayer caster = channel.caster;
		LivingEntity target = channel.target;
		channel.pulses++;
		float damage = pulseDamage(b, channel.life, ForcePowers.masteryRank(caster, ID));
		// The pulses are 10 ticks apart, the vanilla hurt cooldown as long: clear it so a pulse is never reduced.
		target.invulnerableTime = 0;
		boolean landed = target.hurtServer(level, source(level, caster), damage);
		EffectEvents.toTracking(caster, EFFECT, ForcePowers.chest(target), direction(caster, target), caster.getId(),
				(float) caster.getEyePosition().distanceTo(ForcePowers.chest(target)), ForceEffects.packCaster(target.getId(), VARIANT_PULSE));

		if (!landed) {
			return;
		}

		caster.heal(pulseHeal(b, channel.life));
		restoreEnergy(caster, pulseEnergy(b, channel.essence));

		if (target.isAlive()) {
			hold(target, caster, b);

			if (channel.essence) {
				ForceReactions.interruptWindup(target, caster, CAUSE_DRAIN);

				if (target instanceof ServerPlayer victim) {
					ESSENCE_DRAINED.put(victim.getUUID(), victim.level().getGameTime() + Math.max(1, b.pulseTicks) + LINGER_TICKS);
				}
			}
		}

		ForcePowers.addMastery(caster, ID, 1);
	}

	/**
	 * The target is {@code drained} while the channel holds: the effect and a hidden Slowness I, each lasting until a
	 * little after the next pulse, so they lapse on their own if the drain is lost without a word.
	 */
	private static void hold(LivingEntity target, ServerPlayer caster, DrainBalance b) {
		int linger = Math.max(1, b.pulseTicks) + LINGER_TICKS;
		ForceMobEffects.apply(target, ForceMobEffects.DRAINED, linger, caster);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, linger, 0, false, false, false), caster);
	}

	/** Gives {@code amount} Force Energy back, up to the maximum. */
	static void restoreEnergy(ServerPlayer player, float amount) {
		ForceData data = Progression.get(player);
		float restored = Math.min(amount, Math.max(0f, data.maxEnergy - data.energy));

		if (restored > 0f) {
			data.energy += restored;
			Progression.commit(player, data);
		}
	}

	/** Ends {@code player}'s drain, if any. */
	public static void end(ServerPlayer player, boolean feedback) {
		Channel channel = CHANNELS.get(player.getUUID());

		if (channel != null) {
			finish(channel, feedback);
		}
	}

	private static void finish(Channel channel, boolean feedback) {
		ServerPlayer caster = channel.caster;
		LivingEntity target = channel.target;
		DrainBalance b = balance();
		CHANNELS.remove(caster.getUUID());
		Progression.setCooldown(caster, ID, ForcePowers.cooldown(caster, ID, b.cooldownTicks));

		if (target.isAlive() && !target.isRemoved()) {
			release(target, b);
		}

		if (feedback) {
			EffectEvents.toTracking(caster, EFFECT, ForcePowers.chest(target), null, caster.getId(), 0f,
					ForceEffects.packCaster(target.getId(), VARIANT_END));
		}
	}

	/** The drain lets go: {@code drained} goes, and the hidden Slowness it gave (never anyone else's). */
	private static void release(LivingEntity target, DrainBalance b) {
		if (target.hasEffect(ForceMobEffects.DRAINED)) {
			target.removeEffect(ForceMobEffects.DRAINED);
		}

		MobEffectInstance slowness = target.getEffect(MobEffects.SLOWNESS);

		if (slowness != null && slowness.getAmplifier() == 0 && !slowness.isVisible() && !slowness.showIcon()
				&& slowness.getDuration() <= Math.max(1, b.pulseTicks) + LINGER_TICKS) {
			target.removeEffect(MobEffects.SLOWNESS);
		}

		ESSENCE_DRAINED.remove(target.getUUID());
	}

	// ---------------------------------------------------------------- hooks

	/** {@code ForceReactions} hook: a stagger on a draining caster ends the drain. */
	public static void onInterrupted(LivingEntity target, String cause) {
		if (ForceReactions.CAUSE_STAGGER.equals(cause) && target instanceof ServerPlayer player && CHANNELS.containsKey(player.getUUID())) {
			end(player, true);
		}
	}

	/** {@code ForceReactions} cost hook: a player drained by Drain Essence pays {@code drainedCostMultiplier} for powers. */
	public static float costMultiplier(ServerPlayer player) {
		if (ESSENCE_DRAINED.isEmpty()) {
			return 1f;
		}

		Long until = ESSENCE_DRAINED.get(player.getUUID());

		if (until == null) {
			return 1f;
		}

		if (player.level().getGameTime() > until || !player.hasEffect(ForceMobEffects.DRAINED)) {
			ESSENCE_DRAINED.remove(player.getUUID());
			return 1f;
		}

		return Math.max(1f, balance().drainedCostMultiplier);
	}

	/** Ends the player's drain (death, disconnect, change of level) and forgets their double cost. */
	public static void forget(ServerPlayer player, boolean feedback) {
		end(player, feedback);
		ESSENCE_DRAINED.remove(player.getUUID());
	}

	public static void clear() {
		CHANNELS.clear();
		ESSENCE_DRAINED.clear();
	}
}
