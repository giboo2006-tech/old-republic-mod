package com.oldrepublic.force.lightning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.saber.SaberInteractions;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.ForceReactiveEntity;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.force.pull.PullTargeting;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.skill.SkillNodeEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Force Lightning ({@code force_lightning}, spec 8.5): a channel. A visible charge, then arcs to
 * the creature nearest the aim axis (re-chosen every few ticks) that deal damage on an interval,
 * shock (slow and interrupt wind-ups), are absorbed by facing saber guards, and burn the caster
 * after a while. A {@link ForceReactiveEntity} on the aim ray that takes the lightning draws every
 * arc instead (spec 10.6). Chain and Consuming upgrades; {@code archivists_hunger} strikes whole
 * targets harder and {@code crackling_grasp} leaves the struck slowed after the channel. About four
 * effect events per second per caster.
 */
public final class ForceLightning implements ForceAbility {
	public static final String ID = "force_lightning";
	/** Arc slots: the primary target and up to two chained ones. */
	public static final int SLOTS = 3;

	/** One channel in progress. */
	private static final class Channel {
		final ServerPlayer caster;
		final LightningShape shape;
		final @Nullable LivingEntity[] targets = new LivingEntity[SLOTS];
		/** Shock duration this cast per target, after diminishing returns. */
		final Map<UUID, Integer> shockTicks = new HashMap<>();
		final Set<UUID> touched = new HashSet<>();
		/** The reactive entity drawing the arcs this tick, or null. */
		@Nullable Entity reactive;
		int age;
		int arcTicks = -1;
		int contactTicks;
		int mastery;

		Channel(ServerPlayer caster, LightningShape shape) {
			this.caster = caster;
			this.shape = shape;
		}

		boolean arcing() {
			return arcTicks >= 0;
		}
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

	public static LightningBalance balance() {
		return BalanceConfig.section("force_lightning", LightningBalance.class, LightningBalance::new);
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
			case RELEASE, CANCEL -> cancel(p, true);
		}
	}

	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
		Channel channel = CHANNELS.get(p.getUUID());

		if (channel != null) {
			finish(channel, feedback);
		}
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return CHANNELS.containsKey(p.getUUID());
	}

	/**
	 * Keys: {@code start_cost}, {@code cost_per_s}, {@code charge_s}, {@code max_channel_s},
	 * {@code cooldown_s}, {@code full_cooldown_s}, {@code range}, {@code cone}, {@code damage_per_s},
	 * {@code mastery_damage_pct}, {@code shock_s}, {@code burn_after_s}, {@code burn_per_s},
	 * {@code interrupt_damage}, {@code guard_damage_pct}, {@code chain_targets}, {@code chain_range},
	 * {@code chain_damage_pct}, {@code chain_cost_pct}, {@code consume_ramp_pct_per_s},
	 * {@code consume_ramp_max_pct}, {@code consume_drain_pct}.
	 */
	@Override
	public Map<String, Float> info() {
		LightningBalance b = balance();
		float perSecond = 20f / Math.max(1, b.damageIntervalTicks);
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("start_cost", b.startCost);
		info.put("cost_per_s", b.costPerTick * 20f);
		info.put("charge_s", b.chargeTicks / 20f);
		info.put("max_channel_s", b.maxChannelTicks / 20f);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("full_cooldown_s", b.fullChannelCooldownTicks / 20f);
		info.put("range", b.range);
		info.put("cone", b.halfAngle * 2f);
		info.put("damage_per_s", b.damage * perSecond);
		info.put("mastery_damage_pct", b.masteryDamagePerRank * 100f);
		info.put("shock_s", b.shockTicks / 20f);
		info.put("burn_after_s", b.burnAfterTicks / 20f);
		info.put("burn_per_s", b.burnDamage * perSecond);
		info.put("interrupt_damage", b.interruptDamage);
		info.put("guard_damage_pct", b.guardDamageMult * 100f);
		info.put("chain_targets", (float) b.chainTargets);
		info.put("chain_range", b.chainRange);
		info.put("chain_damage_pct", b.chainDamageMult * 100f);
		info.put("chain_cost_pct", (b.chainCostMult - 1f) * 100f);
		info.put("consume_ramp_pct_per_s", b.consumeRampPerSecond * 100f);
		info.put("consume_ramp_max_pct", b.consumeRampMax * 100f);
		info.put("consume_drain_pct", b.consumeDrain * 100f);
		return info;
	}

	/** The player's lightning upgrade. */
	public static LightningShape shape(ServerPlayer player) {
		ForceData data = Progression.get(player);

		for (LightningShape shape : LightningShape.values()) {
			if (shape != LightningShape.NONE && ForcePowers.hasNode(player, data, shape.nodeId())) {
				return shape;
			}
		}

		return LightningShape.NONE;
	}

	/**
	 * Damage of one damage tick on a target before armour.
	 *
	 * @param chained      a chained (not primary) arc
	 * @param contactTicks unbroken contact with this target so far (Consuming ramp)
	 */
	public static float damage(LightningBalance b, int masteryRank, LightningShape shape, boolean chained, int contactTicks,
			boolean resistant, boolean guarding) {
		float damage = b.damage * (1f + b.masteryDamagePerRank * Mth.clamp(masteryRank, 0, 3));

		if (chained) {
			damage *= b.chainDamageMult;
		}

		if (shape == LightningShape.CONSUMING) {
			damage *= 1f + Math.min(b.consumeRampMax, b.consumeRampPerSecond * contactTicks / 20f);
		}

		if (resistant) {
			damage *= b.resistantDamageMult;
		}

		return guarding ? damage * b.guardDamageMult : damage;
	}

	/** Cooldown after the arcs end; {@code fullChannel} when they ran the whole channel. */
	public static int cooldownTicks(LightningBalance b, boolean fullChannel) {
		return fullChannel ? b.fullChannelCooldownTicks : b.cooldownTicks;
	}

	private static float costMultiplier(ServerPlayer player, LightningShape shape) {
		return ForcePowers.costMultiplier(player, ID) * (shape == LightningShape.CHAIN ? balance().chainCostMult : 1f);
	}

	static void start(ServerPlayer player) {
		if (CHANNELS.containsKey(player.getUUID()) || !ForcePowers.mayStart(player, ID)) {
			return;
		}

		LightningShape shape = shape(player);

		if (!Progression.trySpendEnergy(player, balance().startCost * costMultiplier(player, shape))) {
			return;
		}

		CHANNELS.put(player.getUUID(), new Channel(player, shape));
		int rank = ForcePowers.masteryRank(player, ID);
		EffectEvents.toTracking(player, ForceEffects.LIGHTNING, player.getEyePosition(), player.getLookAngle(), player.getId(), rank,
				ForceEffects.packLightning(ForceEffects.LIGHTNING_CHARGE, shape.ordinal()));
		QuestHooks.onAbilityUsed(player, ID, 0);
	}

	private static void finish(Channel channel, boolean feedback) {
		ServerPlayer caster = channel.caster;
		LightningBalance b = balance();
		CHANNELS.remove(caster.getUUID());

		if (channel.arcing()) {
			Progression.setCooldown(caster, ID, ForcePowers.cooldown(caster, ID, cooldownTicks(b, channel.arcTicks >= b.maxChannelTicks)));
			SkillNodeEffects.crackle(caster, struck(channel));
		}

		if (feedback) {
			EffectEvents.toTracking(caster, ForceEffects.LIGHTNING, caster.getEyePosition(), caster.getLookAngle(), caster.getId(), 0f,
					ForceEffects.packLightning(ForceEffects.LIGHTNING_STOP, channel.shape.ordinal()));
		}
	}

	/** The living creatures the arcs struck during this channel that are still in the caster's level. */
	private static List<LivingEntity> struck(Channel channel) {
		List<LivingEntity> struck = new ArrayList<>();

		for (UUID id : channel.touched) {
			if (channel.caster.level().getEntity(id) instanceof LivingEntity living && living.isAlive()) {
				struck.add(living);
			}
		}

		return struck;
	}

	/** A single hit of at least {@code interruptDamage} on the caster ends the channel. */
	public static void onDamaged(LivingEntity entity, float damageTaken) {
		if (entity instanceof ServerPlayer player && damageTaken >= balance().interruptDamage) {
			Channel channel = CHANNELS.get(player.getUUID());

			if (channel != null) {
				finish(channel, true);
			}
		}
	}

	/** Advances every channel; called every server tick. */
	public static void tick() {
		if (CHANNELS.isEmpty()) {
			return;
		}

		LightningBalance b = balance();

		for (Channel channel : new ArrayList<>(CHANNELS.values())) {
			tickChannel(channel, b);
		}
	}

	private static void tickChannel(Channel channel, LightningBalance b) {
		ServerPlayer caster = channel.caster;

		if (!caster.isAlive() || caster.isRemoved()) {
			finish(channel, false);
			return;
		}

		channel.age++;

		if (!channel.arcing()) {
			if (channel.age >= b.chargeTicks) {
				channel.arcTicks = 0;
				EffectEvents.toTracking(caster, ForceEffects.LIGHTNING, caster.getEyePosition(), caster.getLookAngle(), caster.getId(),
						ForcePowers.masteryRank(caster, ID), ForceEffects.packLightning(ForceEffects.LIGHTNING_ARCS, channel.shape.ordinal()));
			} else {
				return;
			}
		}

		Entity reactive = feedReactive(channel, b);
		boolean reactiveChanged = reactive != channel.reactive;
		channel.reactive = reactive;

		if (reactiveChanged || channel.arcTicks % Math.max(1, b.retargetTicks) == 0) {
			retarget(channel, b);
		}

		if (channel.arcTicks % Math.max(1, b.damageIntervalTicks) == 0 && !damageTick(channel, b)) {
			finish(channel, true);
			return;
		}

		if (++channel.arcTicks > b.maxChannelTicks) {
			finish(channel, true);
		}
	}

	/**
	 * Offers this tick's lightning to the reactive entity on the aim ray (a pylon focus, debris).
	 * Returns it when it took the lightning, else null.
	 */
	private static @Nullable Entity feedReactive(Channel channel, LightningBalance b) {
		ServerPlayer caster = channel.caster;
		Entity entity = ForceReactiveEntity.findOnAim(caster, b.range);

		if (entity == null) {
			return null;
		}

		float perTick = damage(b, ForcePowers.masteryRank(caster, ID), channel.shape, false, 0, false, false) / Math.max(1, b.damageIntervalTicks);
		return ((ForceReactiveEntity) entity).onForceLightning(caster, perTick) ? entity : null;
	}

	/**
	 * Picks the primary target (and chained ones) and sends the slots that changed. No living
	 * targets while a reactive entity takes the arcs.
	 */
	private static void retarget(Channel channel, LightningBalance b) {
		ServerPlayer caster = channel.caster;
		List<LivingEntity> chosen = channel.reactive != null ? List.of() : pickTargets(caster, channel.shape, b);
		LivingEntity previousPrimary = channel.targets[0];

		for (int slot = 0; slot < SLOTS; slot++) {
			LivingEntity next = slot < chosen.size() ? chosen.get(slot) : null;
			LivingEntity current = channel.targets[slot];

			if (current == next) {
				continue;
			}

			if (current != null) {
				sendTarget(caster, current, slot, false);
			}

			if (next != null) {
				sendTarget(caster, next, slot, true);
			}

			channel.targets[slot] = next;
		}

		if (channel.targets[0] != previousPrimary) {
			channel.contactTicks = 0;
		}
	}

	private static void sendTarget(ServerPlayer caster, LivingEntity target, int slot, boolean on) {
		EffectEvents.around(caster.level(), ForceEffects.LIGHTNING_TARGET, ForcePowers.chest(target), null, target.getId(), on ? 1f : 0f,
				ForceEffects.packCaster(caster.getId(), slot));
	}

	/**
	 * The primary target nearest the aim axis, then (Chain) up to two more near it, nearest first.
	 * The chain skips bystanders the area rule shields ({@link Attitudes#shieldedFromArea}).
	 */
	public static List<LivingEntity> pickTargets(ServerPlayer caster, LightningShape shape, LightningBalance b) {
		ServerLevel level = caster.level();
		Vec3 eye = caster.getEyePosition();
		List<Entity> candidates = level.getEntities(caster, new AABB(eye, eye).inflate(b.range + 2.0),
				e -> ForcePowers.canAffect(caster, e, b.pvpEffects));
		LivingEntity primary = PullTargeting.nearestToAxis(level, caster, eye, caster.getLookAngle(), b.range, b.halfAngle, candidates);
		List<LivingEntity> chosen = new ArrayList<>();

		if (primary == null) {
			return chosen;
		}

		chosen.add(primary);

		if (shape != LightningShape.CHAIN) {
			return chosen;
		}

		Vec3 from = ForcePowers.chest(primary);
		candidates.stream()
				.filter(e -> e != primary && !Attitudes.shieldedFromArea(caster, e) && ForcePowers.chest(e).distanceTo(from) <= b.chainRange
						&& PushTargeting.hasLineOfSight(level, primary, from, ForcePowers.chest(e)))
				.sorted(Comparator.comparingDouble(e -> ForcePowers.chest(e).distanceToSqr(from)))
				.limit(Math.min(b.chainTargets, SLOTS - 1))
				.forEach(e -> chosen.add((LivingEntity) e));
		return chosen;
	}

	/** One damage tick: pay, damage and shock the targets, burn the caster. False when out of energy. */
	private static boolean damageTick(Channel channel, LightningBalance b) {
		ServerPlayer caster = channel.caster;
		ServerLevel level = caster.level();

		if (!Progression.trySpendEnergy(caster, b.costPerTick * b.damageIntervalTicks * costMultiplier(caster, channel.shape))) {
			return false;
		}

		int rank = ForcePowers.masteryRank(caster, ID);
		int hits = 0;
		int blocked = 0;
		Vec3 contact = null;

		for (int slot = 0; slot < SLOTS; slot++) {
			LivingEntity target = channel.targets[slot];

			if (target == null || !target.isAlive() || target.isRemoved()) {
				continue;
			}

			boolean guarding = ForceReactions.isGuarding(target, caster.getEyePosition());

			if (guarding) {
				blocked |= 1 << slot;
				SaberInteractions.onLightningBlocked(target);
			}

			strike(channel, target, slot, guarding, rank, b);
			hits++;

			if (slot == 0) {
				contact = ForcePowers.chest(target);
			}
		}

		channel.contactTicks += b.damageIntervalTicks;

		if (channel.arcTicks >= b.burnAfterTicks) {
			caster.hurtServer(level, ForceDamage.source(level, ForceDamage.LIGHTNING, null), b.burnDamage);
		}

		if (contact == null) {
			contact = channel.reactive != null ? channel.reactive.getBoundingBox().getCenter()
					: PullTargeting.rayEnd(level, caster, caster.getEyePosition(), caster.getLookAngle(), b.range);
		}

		EffectEvents.toTracking(caster, ForceEffects.LIGHTNING_HIT, contact, null, caster.getId(), hits, blocked);
		return true;
	}

	private static void strike(Channel channel, LivingEntity target, int slot, boolean guarding, int rank, LightningBalance b) {
		ServerPlayer caster = channel.caster;
		ServerLevel level = caster.level();
		boolean resistant = ForcePowers.isResistant(target);
		boolean chained = slot > 0;
		float damage = damage(b, rank, channel.shape, chained, slot == 0 ? channel.contactTicks : 0, resistant, guarding);

		if (chained) {
			damage *= ForceReactions.areaDamageMultiplier(target);
		}

		damage *= SkillNodeEffects.archivistsHunger(caster, target);

		float before = target.getHealth();
		target.hurtServer(level, ForceDamage.source(level, ForceDamage.LIGHTNING, caster), damage);
		float dealt = Math.max(0f, before - target.getHealth());

		if (guarding) {
			ForceReactions.drainGuard(target, b.guardDrainPerTick);
		} else {
			shock(channel, target, resistant, b);
		}

		if (channel.shape == LightningShape.CONSUMING && dealt > 0f) {
			caster.heal(dealt * b.consumeDrain);
		}

		if (Attitudes.countsAsHostile(caster, target) && channel.mastery < b.masteryPerCastMax) {
			channel.mastery++;
			ForcePowers.addMastery(caster, ID, 1);
		}

		if (channel.touched.add(target.getUUID())) {
			QuestHooks.onAbilityHit(caster, ID, target);
		}
	}

	/**
	 * Shock: slowness I refreshed on every damage tick and interrupted wind-ups. Push-resistant
	 * targets go through the diminishing returns once per cast: the first shock in a window lasts
	 * fully and interrupts, the second is half as long and does not interrupt, later ones do nothing.
	 * Durations go through the {@code shock} duration modifiers.
	 */
	private static void shock(Channel channel, LivingEntity target, boolean resistant, LightningBalance b) {
		int full = ControlEffects.duration(target, b.shockTicks, ControlEffects.SHOCK);
		int ticks = resistant
				? channel.shockTicks.computeIfAbsent(target.getUUID(), id -> ControlEffects.diminished(target, b.shockTicks, ControlEffects.SHOCK))
				: full;

		if (ticks <= 0) {
			return;
		}

		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 0), channel.caster);

		if (ticks >= full) {
			ForceReactions.interruptWindup(target, channel.caster, ForceReactions.CAUSE_SHOCK);
		}
	}

	/** Drops the player's state (death, disconnect, dimension change). */
	public static void forget(ServerPlayer player) {
		Channel channel = CHANNELS.get(player.getUUID());

		if (channel != null) {
			finish(channel, true);
		}
	}

	public static void clear() {
		CHANNELS.clear();
	}
}
