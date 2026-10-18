package com.oldrepublic.force.choke;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.enemy.construct.RuinSentinel;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.force.pull.PullTargeting;
import com.oldrepublic.identity.AlignmentShift;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Force Choke ({@code force_choke}, spec MILESTONE_4.md 9.3): a channel on one living, breathing
 * target picked by the crosshair ray, then a 6° cone, within 12 blocks and in sight. The target is
 * lifted 0.6 blocks and held ({@code hold} control, with diminishing returns), takes 2 damage every
 * 10 ticks and cannot act; on the release it drops and staggers. Bosses decide the hold through
 * {@link ForceReactions#chokeTicks}; players are held through {@code Holds}, only with PvP. A hit of
 * 6 or more on the caster, a Push on the caster, or losing sight ends it. Crush adds damage after a
 * long hold, Dread frightens the target's allies, {@code cruel_focus} sharpens the caster's Lightning
 * on the released target.
 */
public final class ForceChoke implements ForceAbility {
	public static final String ID = "force_choke";
	public static final String CRUSH = "choke_crush";
	public static final String DREAD = "choke_dread";
	public static final String CRUEL_FOCUS = "cruel_focus";
	/** Effect event {@code force_choke}: toTracking the caster; pos = the target's chest; variants below. */
	public static final Identifier EFFECT = OldRepublicMod.id("force_choke");
	public static final int VARIANT_START = 0;
	public static final int VARIANT_END = 1;
	public static final int VARIANT_CRUSH = 2;
	/** Effect event {@code force_choke_target}: toTracking the target; intensity 1 held, 0 dropped. */
	public static final Identifier TARGET_EFFECT = OldRepublicMod.id("force_choke_target");
	/** The force-harm damage type {@code oldrepublic:force_choke}. Armour applies. */
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("force_choke"));
	/** Entity types Choke never holds (constructs, spirits); the lead's tag. */
	public static final TagKey<EntityType<?>> CHOKE_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("choke_immune"));
	/** Droids: immune to Choke, and too heavy for Lift; the lead's tag. */
	public static final TagKey<EntityType<?>> DROIDS = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("droids"));
	/** {@code Holds} source of a PvP choke. */
	public static final String HOLD_SOURCE = "force_choke";
	/** Energy is paid every this many ticks. */
	private static final int PAY_INTERVAL = 10;

	/** One channel in progress. */
	private static final class Channel {
		final ServerPlayer caster;
		final LivingEntity target;
		final int holdTicks;
		final boolean crush;
		int age;

		Channel(ServerPlayer caster, LivingEntity target, int holdTicks, boolean crush) {
			this.caster = caster;
			this.target = target;
			this.holdTicks = holdTicks;
			this.crush = crush;
		}
	}

	/** A target recently let go: who held it and when, for {@code cruel_focus}. */
	private record Released(UUID caster, long at) {
	}

	/** A frightened creature: where it runs from and until when. */
	private record Fear(Vec3 from, long until) {
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	private static final Map<UUID, Released> RELEASED = new HashMap<>();
	/** "caster|target" to the day a non-hostile choke last shifted alignment. */
	private static final Map<String, Long> SHIFTED = new HashMap<>();
	private static final Map<PathfinderMob, Fear> FEAR = new WeakHashMap<>();

	public static ChokeBalance balance() {
		return BalanceConfig.section("force_choke", ChokeBalance.class, ChokeBalance::new);
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
	 * Keys: {@code start_cost}, {@code cost_per_s}, {@code max_channel_s}, {@code cooldown_s},
	 * {@code range}, {@code cone}, {@code damage_per_s}, {@code release_stagger_s},
	 * {@code interrupt_damage}, {@code crush_hold_s}, {@code crush_damage}, {@code dread_radius},
	 * {@code dread_s}, {@code cruel_focus_pct}, {@code cruel_focus_window_s},
	 * {@code mastery_choke_cost}, {@code mastery_choke_range}.
	 */
	@Override
	public Map<String, Float> info() {
		ChokeBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("start_cost", b.startCost);
		info.put("cost_per_s", b.channelCost);
		info.put("max_channel_s", b.maxTicks / 20f);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("range", b.range);
		info.put("cone", b.coneDegrees);
		info.put("damage_per_s", b.damage * 20f / Math.max(1, b.damageInterval));
		info.put("release_stagger_s", b.releaseStaggerTicks / 20f);
		info.put("interrupt_damage", b.interruptDamage);
		info.put("crush_hold_s", b.crushMinTicks / 20f);
		info.put("crush_damage", b.crushDamage);
		info.put("dread_radius", b.dreadRadius);
		info.put("dread_s", b.dreadTicks / 20f);
		info.put("cruel_focus_pct", b.cruelFocusPct);
		info.put("cruel_focus_window_s", b.cruelFocusWindow / 20f);
		info.put("mastery_choke_cost", b.masteryCostPerRank);
		info.put("mastery_choke_range", b.masteryRangePerRank);
		return info;
	}

	// ---------------------------------------------------------------- rules

	/** Start cost at mastery {@code rank}, before the cost multipliers. */
	public static float startCost(ChokeBalance b, int rank) {
		return Math.max(0f, b.startCost - b.masteryCostPerRank * Mth.clamp(rank, 0, 3));
	}

	/** Reach at mastery {@code rank}. */
	public static float range(ChokeBalance b, int rank) {
		return b.range + b.masteryRangePerRank * Mth.clamp(rank, 0, 3);
	}

	/** The player whose choke holds {@code target} now, or null (a boss that answers the choke, 10.5). Server only. */
	public static @Nullable ServerPlayer chokerOf(LivingEntity target) {
		for (Channel channel : CHANNELS.values()) {
			if (channel.target == target) {
				return channel.caster;
			}
		}

		return null;
	}

	/** Whether {@code target} breathes and can be choked: not a droid, a construct, an armour stand, undead or water-breathing. */
	public static boolean breathes(LivingEntity target) {
		return !(target instanceof ChokeImmune) && !(target instanceof RuinSentinel) && !(target instanceof ArmorStand)
				&& !target.is(CHOKE_IMMUNE) && !target.is(DROIDS) && !target.canBreatheUnderwater();
	}

	/** Damage dealt when a hold of {@code heldTicks} ends: Crush adds its damage after a long hold. */
	public static float releaseDamage(ChokeBalance b, boolean crush, int heldTicks) {
		return crush && heldTicks >= b.crushMinTicks ? b.crushDamage : 0f;
	}

	/** Whether a choke on this target shifts alignment: non-hostile or yielded (passive animals answer to the force-harm tag instead). */
	public static boolean shifts(boolean hostile, boolean yielded, boolean passiveAnimal) {
		return (yielded || !hostile) && !passiveAnimal;
	}

	/** {@code cruel_focus}: whether a Lightning hit {@code ticksSinceRelease} after the release is sharpened. */
	public static boolean cruelFocusApplies(ChokeBalance b, long ticksSinceRelease) {
		return ticksSinceRelease >= 0 && ticksSinceRelease <= b.cruelFocusWindow;
	}

	/** Whether {@code player} is choking someone now. */
	public static boolean isChoking(Player player) {
		return CHANNELS.containsKey(player.getUUID());
	}

	// ---------------------------------------------------------------- start

	static void start(ServerPlayer player) {
		if (CHANNELS.containsKey(player.getUUID()) || !ForcePowers.mayStart(player, ID)) {
			return;
		}

		ChokeBalance b = balance();
		int rank = ForcePowers.masteryRank(player, ID);
		ServerLevel level = player.level();
		LivingEntity target = PullTargeting.find(level, player, player.getEyePosition(), player.getLookAngle(), range(b, rank), b.coneDegrees / 2f,
				e -> ForcePowers.canAffect(player, e, b.pvpEffects) && !isHeldByAnother(e),
				e -> !Attitudes.shieldedFromArea(player, e));

		if (target == null) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_choke.no_target"));
			return;
		}

		if (!breathes(target)) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_choke.no_breath"));
			return;
		}

		int requested = b.maxTicks;

		if (target.is(ConventionalEntityTypeTags.BOSSES)) {
			requested = Math.min(requested, b.bossMaxTicks);
		}

		int allowed = ForceReactions.chokeTicks(target, requested);
		int hold = allowed > 0 ? ControlEffects.diminished(target, allowed, ControlEffects.HOLD) : 0;

		if (hold <= 0) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_choke.refused"));
			return;
		}

		if (!Progression.trySpendEnergy(player, startCost(b, rank) * ForcePowers.costMultiplier(player, ID))) {
			return;
		}

		ForceData data = Progression.get(player);
		Channel channel = new Channel(player, target, hold, ForcePowers.hasNode(player, data, CRUSH));
		CHANNELS.put(player.getUUID(), channel);
		boolean hostile = ForcePowers.isHostile(player, target);

		if (target instanceof ServerPlayer victim) {
			Holds.hold(victim, hold + 1, HOLD_SOURCE, victim.position().add(0, b.liftHeight, 0), true);
		} else {
			ChokedTargets.hold(target, b.liftHeight);

			if (hostile) {
				target.setLastHurtByMob(player);
			}
		}

		ForceReactions.interruptWindup(target, player, ForceReactions.CAUSE_STAGGER);
		alignment(player, target, hostile, b);

		if (ForcePowers.hasNode(player, data, DREAD)) {
			dread(player, target, b);
		}

		EffectEvents.toTracking(player, EFFECT, ForcePowers.chest(target), null, player.getId(), hold / 20f, VARIANT_START);
		EffectEvents.toTracking(target, TARGET_EFFECT, ForcePowers.chest(target), null, target.getId(), 1f, 0);
		QuestHooks.onAbilityUsed(player, ID, hostile ? 1 : 0);
		QuestHooks.onAbilityHit(player, ID, target);
	}

	private static boolean isHeldByAnother(Entity e) {
		return ChokedTargets.isChoked(e) || e instanceof Player p && Holds.isHeld(p);
	}

	/** A choke on a non-hostile or yielded target: a minor dark shift, once per target per in-game day. */
	private static void alignment(ServerPlayer caster, LivingEntity target, boolean hostile, ChokeBalance b) {
		boolean yielded = target instanceof Yieldable yieldable && yieldable.isYielded();
		boolean passiveAnimal = !(target instanceof Player) && target.getType().getCategory() == MobCategory.CREATURE && !yielded;

		if (b.nonHostileShift == 0 || !shifts(hostile, yielded, passiveAnimal)) {
			return;
		}

		long day = Clocks.day(caster.level().getServer());
		String key = caster.getUUID() + "|" + target.getUUID();
		Long last = SHIFTED.get(key);

		if (last != null && last == day) {
			return;
		}

		SHIFTED.put(key, day);
		Identity.shift(caster, new AlignmentShift("force_choke." + target.getUUID() + "." + day, b.nonHostileShift, AlignmentShift.Kind.MINOR,
				"ability.oldrepublic.force_choke.shift_reason"));
	}

	/** Dread: non-boss enemies of the caster near the target drop their target and back away. */
	private static void dread(ServerPlayer caster, LivingEntity target, ChokeBalance b) {
		long until = caster.level().getGameTime() + b.dreadTicks;
		AABB box = target.getBoundingBox().inflate(b.dreadRadius);

		for (PathfinderMob mob : caster.level().getEntitiesOfClass(PathfinderMob.class, box,
				m -> m != target && m.isAlive() && m.distanceTo(target) <= b.dreadRadius && !m.is(ConventionalEntityTypeTags.BOSSES)
						&& ForcePowers.isHostile(caster, m))) {
			FEAR.put(mob, new Fear(target.position(), until));
			ForceReactions.interruptWindup(mob, caster, ForceReactions.CAUSE_HEAVY_FEAR);
		}
	}

	// ---------------------------------------------------------------- ticking

	/** Advances every channel, the held creatures and the frightened ones; called every server tick. */
	public static void tick() {
		ChokedTargets.tick();
		tickFear();

		if (CHANNELS.isEmpty()) {
			return;
		}

		ChokeBalance b = balance();

		for (Channel channel : new ArrayList<>(CHANNELS.values())) {
			tickChannel(channel, b);
		}
	}

	private static void tickChannel(Channel channel, ChokeBalance b) {
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

		channel.age++;
		ServerLevel level = caster.level();
		float range = range(b, ForcePowers.masteryRank(caster, ID));

		if (caster.getEyePosition().distanceTo(ForcePowers.chest(target)) > range + 2.0
				|| !PushTargeting.hasLineOfSight(level, caster, caster.getEyePosition(), ForcePowers.chest(target))) {
			finish(channel, true);
			return;
		}

		if (target instanceof ServerPlayer victim && !Holds.isHeld(victim)) {
			// Pushed free or released by another rule.
			finish(channel, true);
			return;
		}

		if (channel.age % PAY_INTERVAL == 0
				&& !Progression.trySpendEnergy(caster, b.channelCost * PAY_INTERVAL / 20f * ForcePowers.costMultiplier(caster, ID))) {
			finish(channel, true);
			return;
		}

		if (channel.age % Math.max(1, b.damageInterval) == 0) {
			target.hurtServer(level, source(level, caster), b.damage);
		}

		if (channel.age == b.masteryHoldTicks && ForcePowers.isHostile(caster, target)) {
			ForcePowers.addMastery(caster, ID, 1);
		}

		if (channel.age >= channel.holdTicks) {
			finish(channel, true);
		}
	}

	/** The choke damage source caused by {@code caster}. */
	public static DamageSource source(ServerLevel level, ServerPlayer caster) {
		return ForceDamage.source(level, DAMAGE, caster);
	}

	/** Ends {@code player}'s channel, if any. */
	public static void end(ServerPlayer player, boolean feedback) {
		Channel channel = CHANNELS.get(player.getUUID());

		if (channel != null) {
			finish(channel, feedback);
		}
	}

	private static void finish(Channel channel, boolean feedback) {
		ServerPlayer caster = channel.caster;
		LivingEntity target = channel.target;
		ChokeBalance b = balance();
		CHANNELS.remove(caster.getUUID());
		Progression.setCooldown(caster, ID, ForcePowers.cooldown(caster, ID, b.cooldownTicks));

		if (target instanceof ServerPlayer victim) {
			Holds.release(victim, "release");
		} else {
			ChokedTargets.release(target);
		}

		long now = caster.level().getGameTime();
		boolean crushed = false;

		if (target.isAlive() && !target.isRemoved()) {
			float extra = releaseDamage(b, channel.crush, channel.age);

			if (extra > 0f && target.level() instanceof ServerLevel level) {
				// The Crush lands within ten ticks of the hold's last periodic hit; clear the vanilla
				// hurt cooldown so it is not reduced to the difference between the two hits.
				target.invulnerableTime = 0;
				target.hurtServer(level, source(level, caster), extra);
				crushed = true;
			}

			if (target.isAlive()) {
				ControlEffects.stagger(caster, target, b.releaseStaggerTicks, 1);
			}

			RELEASED.put(target.getUUID(), new Released(caster.getUUID(), now));
			EffectEvents.toTracking(target, TARGET_EFFECT, ForcePowers.chest(target), null, target.getId(), 0f, crushed ? 1 : 0);
		}

		if (feedback) {
			EffectEvents.toTracking(caster, EFFECT, ForcePowers.chest(target), null, caster.getId(), 0f, crushed ? VARIANT_CRUSH : VARIANT_END);
		}
	}

	private static void tickFear() {
		if (FEAR.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<PathfinderMob, Fear>> it = FEAR.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<PathfinderMob, Fear> entry = it.next();
			PathfinderMob mob = entry.getKey();

			if (!mob.isAlive() || mob.level().getGameTime() >= entry.getValue().until()) {
				it.remove();
				continue;
			}

			mob.setTarget(null);

			if (mob.getNavigation().isDone()) {
				Vec3 away = DefaultRandomPos.getPosAway(mob, 8, 4, entry.getValue().from());

				if (away != null) {
					mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
				}
			}
		}
	}

	// ---------------------------------------------------------------- hooks

	/** Fabric {@code AFTER_DAMAGE}: a hit of {@code interruptDamage} or more on the caster ends the channel. */
	public static void onDamaged(LivingEntity entity, float damageTaken) {
		if (entity instanceof ServerPlayer player && damageTaken >= balance().interruptDamage) {
			end(player, true);
		}
	}

	/** A Force Push reached {@code target}: a pushed caster loses their grip. */
	public static void onPushed(LivingEntity target) {
		if (target instanceof ServerPlayer player) {
			end(player, true);
		}
	}

	/**
	 * The {@code cruel_focus} {@code DamageModifier}: the caster's Force Lightning on a target they
	 * released from Choke within the window deals {@code cruelFocusPct} more.
	 */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (RELEASED.isEmpty() || !source.is(ForceDamage.LIGHTNING) || !(source.getEntity() instanceof ServerPlayer caster)) {
			return amount;
		}

		Released released = RELEASED.get(victim.getUUID());
		ChokeBalance b = balance();

		if (released == null || !released.caster().equals(caster.getUUID())
				|| !cruelFocusApplies(b, victim.level().getGameTime() - released.at())
				|| !ForcePowers.hasNode(caster, Progression.get(caster), CRUEL_FOCUS)) {
			return amount;
		}

		return amount * (1f + b.cruelFocusPct / 100f);
	}

	/** Drops old release records; called now and then. */
	public static void prune(long now) {
		int window = balance().cruelFocusWindow;
		RELEASED.values().removeIf(r -> now - r.at() > window || r.at() > now);

		if (SHIFTED.size() > 1024) {
			SHIFTED.clear();
		}
	}

	/** Ends the player's channel (death, disconnect, change of level). */
	public static void forget(ServerPlayer player, boolean feedback) {
		end(player, feedback);
	}

	public static void clear() {
		CHANNELS.clear();
		RELEASED.clear();
		SHIFTED.clear();
		FEAR.clear();
		ChokedTargets.clear();
	}
}
