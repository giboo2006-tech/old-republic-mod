package com.oldrepublic.force.heal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.identity.ContextAct;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.mission.MissionFeature;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.Skills;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Force Heal ({@code force_heal}, spec 8.4): a short, interruptible channel. After a start-up it
 * heals the caster every few ticks while the key is held, slows the caster, forbids attacking and
 * guarding, and breaks on a solid hit or a stagger. Shared Light and Serene Ward upgrades. Each
 * heal tick also stands up downed troopers nearby ({@link HealRevive}) and, with Mending Breath
 * ({@code mending_breath}), draws Slowness, Weakness and Poison out of everyone it heals.
 */
public final class ForceHeal implements ForceAbility {
	public static final String ID = "force_heal";
	private static final Identifier SLOW_ID = OldRepublicMod.id("force_heal_slow");
	private static final Identifier SETTLER = OldRepublicMod.id("frontier_settler");

	/** One channel in progress. */
	private static final class Channel {
		final ServerPlayer caster;
		final HealShape shape;
		final Set<UUID> allies = new HashSet<>();
		final Set<UUID> helped = new HashSet<>();
		int age;
		int heals;
		float masteryHp;

		Channel(ServerPlayer caster, HealShape shape) {
			this.caster = caster;
			this.shape = shape;
		}
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	/** Player UUID to (other player UUID to game time) of the last damage between the two. */
	private static final Map<UUID, Map<UUID, Long>> COMBAT = new HashMap<>();

	public static HealBalance balance() {
		return BalanceConfig.section("force_heal", HealBalance.class, HealBalance::new);
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
			case RELEASE -> end(p, false);
			case CANCEL -> cancel(p, true);
		}
	}

	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
		Channel channel = CHANNELS.get(p.getUUID());

		if (channel != null) {
			finish(channel, false, feedback);
		}
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return CHANNELS.containsKey(p.getUUID());
	}

	/**
	 * Keys: {@code start_cost}, {@code cost_per_heal}, {@code cost_per_s}, {@code heal_per_s},
	 * {@code max_channel_s}, {@code startup_s}, {@code cooldown_s}, {@code interrupted_cooldown_s},
	 * {@code interrupt_damage}, {@code move_pct}, {@code mastery_heal_pct}, {@code shared_radius},
	 * {@code shared_rate_pct}, {@code shared_cost_pct}, {@code ward_min_s}, {@code ward_seconds},
	 * {@code ward_stamina}.
	 */
	@Override
	public Map<String, Float> info() {
		HealBalance b = balance();
		float perSecond = 20f / Math.max(1, b.intervalTicks);
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("start_cost", b.startCost);
		info.put("cost_per_heal", b.costPerHeal);
		info.put("cost_per_s", b.costPerHeal * perSecond);
		info.put("heal_per_s", b.healPerTick * perSecond);
		info.put("max_channel_s", b.maxChannelTicks / 20f);
		info.put("charge_s", b.startupTicks / 20f);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("interrupted_cooldown_s", b.cooldownTicks * b.interruptedCooldownMult / 20f);
		info.put("interrupt_damage", b.interruptDamage);
		info.put("move_pct", b.moveMult * 100f);
		info.put("mastery_heal_pct", b.masteryHealPerRank * 100f);
		info.put("shared_radius", b.sharedRadius);
		info.put("shared_rate_pct", b.sharedRate * 100f);
		info.put("shared_cost_pct", (b.sharedCostMult - 1f) * 100f);
		info.put("ward_min_s", b.wardMinChannelTicks / 20f);
		info.put("ward_seconds", b.wardAbsorptionTicks / 20f);
		info.put("ward_stamina", b.wardStamina);
		return info;
	}

	/** The player's heal upgrade. */
	public static HealShape shape(ServerPlayer player) {
		ForceData data = Progression.get(player);

		for (HealShape shape : HealShape.values()) {
			if (shape != HealShape.NONE && ForcePowers.hasNode(player, data, shape.nodeId())) {
				return shape;
			}
		}

		return HealShape.NONE;
	}

	/** Health restored to the caster per heal tick at {@code masteryRank}. */
	public static float healAmount(HealBalance b, int masteryRank) {
		return b.healPerTick * (1f + b.masteryHealPerRank * Math.max(0, Math.min(3, masteryRank)));
	}

	/** Whether a single hit of {@code damageTaken} (after armour) breaks the channel. */
	public static boolean interrupts(HealBalance b, float damageTaken) {
		return damageTaken >= b.interruptDamage;
	}

	/** Whether the channel of {@code player} is running (attacks are refused meanwhile). */
	public static boolean isChannelling(Player player) {
		return CHANNELS.containsKey(player.getUUID());
	}

	private static float costMultiplier(ServerPlayer player, HealShape shape) {
		return ForcePowers.costMultiplier(player, ID) * (shape == HealShape.SHARED ? balance().sharedCostMult : 1f);
	}

	static void start(ServerPlayer player) {
		if (CHANNELS.containsKey(player.getUUID()) || !ForcePowers.mayStart(player, ID)) {
			return;
		}

		if (player.isUsingItem() || player.isSprinting()) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_heal.not_still"));
			return;
		}

		HealShape shape = shape(player);
		HealBalance b = balance();

		if (!Progression.trySpendEnergy(player, b.startCost * costMultiplier(player, shape))) {
			return;
		}

		CHANNELS.put(player.getUUID(), new Channel(player, shape));
		setSlowed(player, true, b);
		EffectEvents.toTracking(player, ForceEffects.HEAL, player.position(), null, player.getId(), shape.ordinal(), ForceEffects.CHANNEL_START);
		QuestHooks.onAbilityUsed(player, ID, 0);
	}

	private static void setSlowed(ServerPlayer player, boolean slowed, HealBalance b) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed == null) {
			return;
		}

		// API-CHECK: AttributeInstance#removeModifier(Identifier) and #addTransientModifier(AttributeModifier)
		speed.removeModifier(SLOW_ID);

		if (slowed) {
			speed.addTransientModifier(new AttributeModifier(SLOW_ID, b.moveMult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Ends the channel of {@code player}, normally or {@code interrupted}. */
	static void end(ServerPlayer player, boolean interrupted) {
		Channel channel = CHANNELS.get(player.getUUID());

		if (channel != null) {
			finish(channel, interrupted, true);
		}
	}

	private static void finish(Channel channel, boolean interrupted, boolean feedback) {
		ServerPlayer caster = channel.caster;
		HealBalance b = balance();
		CHANNELS.remove(caster.getUUID());
		setSlowed(caster, false, b);
		// A channel that never healed counts as interrupted: the short cooldown.
		boolean shortCooldown = interrupted || channel.heals == 0;
		int cooldown = shortCooldown ? Math.round(b.cooldownTicks * b.interruptedCooldownMult) : b.cooldownTicks;
		Progression.setCooldown(caster, ID, ForcePowers.cooldown(caster, ID, cooldown));

		if (feedback) {
			EffectEvents.toTracking(caster, ForceEffects.HEAL, caster.position(), null, caster.getId(), channel.shape.ordinal(),
					interrupted ? ForceEffects.CHANNEL_INTERRUPTED : ForceEffects.CHANNEL_END);
		}

		if (!interrupted && channel.shape == HealShape.WARD && healPhase(channel, b) >= b.wardMinChannelTicks && caster.isAlive()) {
			caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, b.wardAbsorptionTicks, b.wardAbsorptionAmplifier, false, false, true));
			ForceReactions.restoreGuard(caster, b.wardStamina);
			EffectEvents.toTracking(caster, ForceEffects.HEAL_WARD, caster.position(), null, caster.getId(), b.wardAbsorptionTicks, 0);
		}
	}

	private static int healPhase(Channel channel, HealBalance b) {
		return Math.max(0, channel.age - b.startupTicks);
	}

	/** Damage taken by any living entity (after armour): a solid hit breaks a heal channel. */
	public static void onDamaged(LivingEntity entity, Entity attacker, float damageTaken) {
		if (entity instanceof ServerPlayer victim && attacker instanceof ServerPlayer other && victim != other) {
			long now = victim.level().getGameTime();
			COMBAT.computeIfAbsent(victim.getUUID(), id -> new HashMap<>()).put(other.getUUID(), now);
			COMBAT.computeIfAbsent(other.getUUID(), id -> new HashMap<>()).put(victim.getUUID(), now);
		}

		if (entity instanceof ServerPlayer player && interrupts(balance(), damageTaken)) {
			end(player, true);
		}
	}

	/** Whether {@code a} and {@code b} damaged each other within {@code window} ticks. */
	public static boolean inCombat(Player a, Player b, int window) {
		Map<UUID, Long> fights = COMBAT.get(a.getUUID());
		Long last = fights == null ? null : fights.get(b.getUUID());
		return last != null && a.level().getGameTime() - last <= window;
	}

	/**
	 * Whether Shared Light heals {@code entity}: other players the caster is not fighting, Frontier
	 * Settlers, villagers, wandering traders and tamed animals. PvP settings never matter here.
	 */
	public static boolean isShareTarget(ServerPlayer caster, Entity entity, HealBalance b) {
		if (entity == caster || !entity.isAlive() || entity.isSpectator() || !(entity instanceof LivingEntity)) {
			return false;
		}

		if (entity instanceof Player other) {
			return !inCombat(caster, other, b.combatExcludeTicks);
		}

		EntityType<?> type = entity.getType();
		// API-CHECK: net.minecraft.world.entity.TamableAnimal#isTame() (class location in 26.2)
		return type == EntityTypes.VILLAGER || type == EntityTypes.WANDERING_TRADER || entity instanceof TamableAnimal tame && tame.isTame()
				|| SETTLER.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}

	/** Advances every channel; called every server tick. */
	public static void tick() {
		if (CHANNELS.isEmpty()) {
			return;
		}

		HealBalance b = balance();

		for (Channel channel : new ArrayList<>(CHANNELS.values())) {
			tickChannel(channel, b);
		}
	}

	private static void tickChannel(Channel channel, HealBalance b) {
		ServerPlayer caster = channel.caster;

		if (!caster.isAlive() || caster.isRemoved()) {
			finish(channel, false, false);
			return;
		}

		if (staggered(caster, b)) {
			finish(channel, true, true);
			return;
		}

		if (caster.isSprinting() || caster.isUsingItem()) {
			finish(channel, false, true);
			return;
		}

		channel.age++;
		int phase = healPhase(channel, b);

		if (phase > 0 && phase % Math.max(1, b.intervalTicks) == 0 && !healTick(channel, b)) {
			finish(channel, false, true);
			return;
		}

		if (phase >= b.maxChannelTicks) {
			finish(channel, false, true);
		}
	}

	private static boolean staggered(ServerPlayer caster, HealBalance b) {
		MobEffectInstance slow = caster.getEffect(MobEffects.SLOWNESS);
		return slow != null && slow.getAmplifier() >= b.staggerAmplifier;
	}

	/** One heal tick: pay, heal the caster (and allies with Shared Light). False when out of energy. */
	private static boolean healTick(Channel channel, HealBalance b) {
		ServerPlayer caster = channel.caster;

		if (!Progression.trySpendEnergy(caster, b.costPerHeal * costMultiplier(caster, channel.shape))) {
			return false;
		}

		channel.heals++;
		boolean mending = Skills.active(caster, SkillIds.MENDING_BREATH);
		float amount = healAmount(b, ForcePowers.masteryRank(caster, ID));
		float healed = heal(caster, amount);

		if (mending) {
			cleanse(caster);
		}

		if (channel.shape == HealShape.SHARED) {
			healed += healAllies(channel, amount * b.sharedRate, b, mending);
		}

		HealRevive.revive(caster, MissionFeature.balance().healTendRange, HealRevive.Downed.TROOPERS);

		if (healed > 0f && ForcePowers.hostileNear(caster, b.masteryHostileRadius)) {
			channel.masteryHp += healed;
			int points = (int) (channel.masteryHp / Math.max(0.1f, b.masteryHpPerPoint));

			if (points > 0) {
				channel.masteryHp -= points * b.masteryHpPerPoint;
				ForcePowers.addMastery(caster, ID, points);
			}
		}

		return true;
	}

	/** Heals {@code entity} and returns how much health it actually regained. */
	private static float heal(LivingEntity entity, float amount) {
		float before = entity.getHealth();
		entity.heal(amount);
		return Math.max(0f, entity.getHealth() - before);
	}

	/**
	 * Mending Breath: removes Slowness, Weakness and Poison from {@code entity}, with the cleanse wisps
	 * when anything was removed. Returns whether anything was.
	 */
	public static boolean cleanse(LivingEntity entity) {
		boolean slowness = entity.removeEffect(MobEffects.SLOWNESS);
		boolean weakness = entity.removeEffect(MobEffects.WEAKNESS);
		boolean poison = entity.removeEffect(MobEffects.POISON);

		if (!slowness && !weakness && !poison) {
			return false;
		}

		if (entity.level() instanceof ServerLevel level) {
			EffectEvents.around(level, ForceEffects.HEAL, entity.position(), null, entity.getId(), 0f, ForceEffects.HEAL_CLEANSED);
		}

		return true;
	}

	private static float healAllies(Channel channel, float amount, HealBalance b, boolean mending) {
		ServerPlayer caster = channel.caster;
		double r = b.sharedRadius;
		List<Entity> near = caster.level().getEntities(caster, new AABB(caster.position(), caster.position()).inflate(r),
				e -> e.distanceToSqr(caster) <= r * r && isShareTarget(caster, e, b));
		Set<UUID> present = new HashSet<>();
		float total = 0f;

		for (Entity entity : near) {
			LivingEntity ally = (LivingEntity) entity;
			present.add(ally.getUUID());

			if (channel.allies.add(ally.getUUID())) {
				EffectEvents.around(caster.level(), ForceEffects.HEAL_ALLY, ally.position(), null, ally.getId(), 1f,
						ForceEffects.packCaster(caster.getId(), ForceEffects.CHANNEL_START));
			}

			float healed = heal(ally, amount);
			total += healed;

			if (mending) {
				cleanse(ally);
			}

			if (healed > 0f && channel.helped.add(ally.getUUID())) {
				Identity.contextual(caster, ContextAct.HELPED_OTHER, ally);
			}
		}

		Iterator<UUID> it = channel.allies.iterator();

		while (it.hasNext()) {
			UUID id = it.next();

			if (!present.contains(id)) {
				it.remove();
				Entity gone = caster.level().getEntity(id);

				if (gone != null) {
					EffectEvents.around(caster.level(), ForceEffects.HEAL_ALLY, gone.position(), null, gone.getId(), 0f,
							ForceEffects.packCaster(caster.getId(), ForceEffects.CHANNEL_END));
				}
			}
		}

		return total;
	}

	/** Drops the player's state (death, disconnect, dimension change). */
	public static void forget(ServerPlayer player) {
		Channel channel = CHANNELS.get(player.getUUID());

		if (channel != null) {
			finish(channel, false, true);
		}

		COMBAT.remove(player.getUUID());
	}

	/** Forgets old fights. */
	public static void prune(long now) {
		int window = balance().combatExcludeTicks;

		for (Map<UUID, Long> fights : COMBAT.values()) {
			fights.values().removeIf(t -> now - t > window);
		}

		COMBAT.values().removeIf(Map::isEmpty);
	}

	public static void clear() {
		CHANNELS.clear();
		COMBAT.clear();
	}
}
