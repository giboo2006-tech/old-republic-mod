package com.oldrepublic.force.lift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.AbilityInfoKeys;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceAct;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForceDispatch;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.ForceReactiveEntity;
import com.oldrepublic.force.heal.ForceHeal;
import com.oldrepublic.force.pull.PullTargeting;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Telekinetic Lift ({@code force_lift}, spec 10.2): a channel that raises one target and holds it in
 * the air, then slams it down (or, with Hurl, throws it) on release or at the end of the hold.
 * Order of what it reaches (10.6): a reactive entity on the aim ray (every tick), a living target
 * (its reaction handlers cap the height; 0 refuses), a reactive block on the aim (dispatched every
 * tick; rubble counts its own ticks). Creatures are held through {@link LiftedTargets} (transient
 * gravity, paused AI), players through {@link Holds} (PvP option only). A hit of 6 or more on the
 * caster ends the channel; every end path lets the target go.
 */
public final class ForceLift implements ForceAbility {
	public static final String ID = "force_lift";
	/** Hold source for {@link Holds} and the control kind's records. */
	public static final String HOLD_SOURCE = "force_lift";
	/** The channel pays its per-second cost in steps of this many ticks. */
	private static final int PAY_INTERVAL = 10;
	private static final int STAGGER_AMPLIFIER = 2;
	/** A hostile held this long earns the cast's mastery point. */
	private static final int MASTERY_HOLD_TICKS = 20;
	/** A slammed or thrown target that has not landed after this many ticks lands now. */
	private static final int FALL_TIMEOUT = 30;
	private static final double SLAM_SPEED = 1.0;
	private static final double HURL_LIFT = 0.25;
	private static final Vec3 UP = new Vec3(0, 1, 0);

	/**
	 * What a cast reaches.
	 *
	 * @param refused   nothing to lift because the target refused (the reason was already shown)
	 * @param height    blocks a living target rises
	 * @param holdTicks the longest hold (after diminishing returns for a living target)
	 */
	record Choice(ForceDispatch.Step step, @Nullable Entity reactive, @Nullable LivingEntity living, @Nullable BlockPos block, float height,
			int holdTicks, boolean boss, boolean refused) {
		static Choice none(boolean refused) {
			return new Choice(ForceDispatch.Step.NONE, null, null, null, 0f, 0, false, refused);
		}
	}

	/** One channel in progress. */
	private static final class Channel {
		final ServerPlayer caster;
		final ForceDispatch.Finders finders;
		final Choice choice;
		final LiftShape shape;
		final boolean hostile;
		int age;
		boolean masteryGiven;

		Channel(ServerPlayer caster, ForceDispatch.Finders finders, Choice choice, LiftShape shape, boolean hostile) {
			this.caster = caster;
			this.finders = finders;
			this.choice = choice;
			this.shape = shape;
			this.hostile = hostile;
		}
	}

	/** Why a channel ended. */
	private enum End {
		/** The key was released: slam (or hurl). */
		RELEASED,
		/** The hold ran out, or the energy did: slam (or hurl). */
		EXPIRED,
		/** A heavy hit on the caster: the target just drops. */
		INTERRUPTED,
		/** The target died, unloaded, moved away or broke free. */
		LOST,
		/** Death, disconnect, dimension change, a slot or screen change, a stop. */
		CANCELLED
	}

	/** A slammed or thrown target on its way down. */
	private static final class Fall {
		final ServerPlayer caster;
		final LivingEntity target;
		final boolean hurl;
		final boolean boss;
		int age;
		boolean hitOther;

		Fall(ServerPlayer caster, LivingEntity target, boolean hurl, boolean boss) {
			this.caster = caster;
			this.target = target;
			this.hurl = hurl;
			this.boss = boss;
		}
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	private static final List<Fall> FALLS = new ArrayList<>();

	public static LiftBalance balance() {
		return BalanceConfig.section("force_lift", LiftBalance.class, LiftBalance::new);
	}

	/**
	 * Registers the Suspend damage bonus and the cleanup paths: a target that unloads, and every
	 * target let go while the server is stopping (before the levels save). Called from
	 * {@code ForceFeature}, which also routes ticks, damage and {@link #forget}.
	 */
	public static void init() {
		DamageModifiers.register(ForceLift::suspendBonus);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> onUnload(entity));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
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
			case RELEASE -> end(p, End.RELEASED);
			case CANCEL -> cancel(p, true);
		}
	}

	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
		Channel channel = CHANNELS.get(p.getUUID());

		if (channel != null) {
			finish(channel, End.CANCELLED, feedback);
		}
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return CHANNELS.containsKey(p.getUUID());
	}

	/**
	 * Keys: {@code start_cost}, {@code cost_per_s}, {@code max_channel_s}, {@code cooldown_s},
	 * {@code range}, {@code lift_height}, {@code slam_damage}, {@code slam_stagger_s},
	 * {@code interrupt_damage}, {@code mastery_lift_range}, {@code mastery_cost_pct},
	 * {@code suspend_hold_s}, {@code suspend_ally_pct}, {@code hurl_distance}, {@code hurl_damage},
	 * {@code hurl_stagger_s}.
	 */
	@Override
	public Map<String, Float> info() {
		LiftBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put(AbilityInfoKeys.START_COST, b.startCost);
		info.put(AbilityInfoKeys.COST_PER_S, b.costPerSecond);
		info.put(AbilityInfoKeys.MAX_CHANNEL, b.maxHoldTicks / 20f);
		info.put(AbilityInfoKeys.COOLDOWN, b.cooldownTicks / 20f);
		info.put(AbilityInfoKeys.RANGE, b.range);
		info.put("lift_height", b.height);
		info.put("slam_damage", b.slamDamage);
		info.put("slam_stagger_s", b.slamStaggerTicks / 20f);
		info.put("interrupt_damage", b.interruptDamage);
		info.put("mastery_lift_range", b.masteryRangePerRank);
		info.put("mastery_cost_pct", b.masteryCostPerRank * 100f);
		info.put("suspend_hold_s", b.suspendHoldTicks / 20f);
		info.put("suspend_ally_pct", b.suspendAllyBonus * 100f);
		info.put("hurl_distance", b.hurlDistance);
		info.put("hurl_damage", b.hurlDamage);
		info.put("hurl_stagger_s", b.hurlStaggerTicks / 20f);
		return info;
	}

	/** The player's lift upgrade. */
	public static LiftShape shape(ServerPlayer player) {
		ForceData data = Progression.get(player);

		for (LiftShape shape : LiftShape.values()) {
			if (shape != LiftShape.NONE && ForcePowers.hasNode(player, data, shape.nodeId())) {
				return shape;
			}
		}

		return LiftShape.NONE;
	}

	/** {@code base} energy for {@code player}: mastery and {@code archivists_road}, then the shared multipliers. */
	private static float cost(ServerPlayer player, float base) {
		return LiftRules.cost(base, ForcePowers.masteryRank(player, ID), Skills.active(player, SkillIds.ARCHIVISTS_ROAD), balance(), SkillService.balance())
				* ForcePowers.costMultiplier(player, ID);
	}

	/** Press: validate, find the target (nothing is spent when there is none), pay the start and lift. */
	static void start(ServerPlayer player) {
		if (CHANNELS.containsKey(player.getUUID()) || !ForcePowers.mayStart(player, ID)) {
			return;
		}

		float startCost = cost(player, balance().startCost);

		if (!ForcePowers.canAfford(player, startCost)) {
			return;
		}

		Choice choice = choose(player, ForceDispatch.WORLD);

		if (choice.step() == ForceDispatch.Step.NONE) {
			if (!choice.refused()) {
				player.sendOverlayMessage(Component.translatable("message.oldrepublic.force.lift_no_target"));
			}

			return;
		}

		if (!Progression.trySpendEnergy(player, startCost)) {
			return;
		}

		begin(player, ForceDispatch.WORLD, choice);
	}

	/**
	 * Test seam: picks what a lift by {@code caster} would reach with {@code finders} and starts
	 * holding it, without cost or the start checks. Returns what it reached.
	 */
	public static ForceDispatch.Step liftTarget(ServerPlayer caster, ForceDispatch.Finders finders) {
		Choice choice = choose(caster, finders);

		if (choice.step() != ForceDispatch.Step.NONE) {
			begin(caster, finders, choice);
		}

		return choice.step();
	}

	/** The 10.6 order: reactive entity, living target, reactive block. Shows the reason when a target refuses. */
	static Choice choose(ServerPlayer caster, ForceDispatch.Finders finders) {
		LiftBalance b = balance();
		float range = LiftRules.range(ForcePowers.masteryRank(caster, ID), b);
		int holdTicks = LiftRules.holdTicks(shape(caster) == LiftShape.SUSPEND, b);
		Entity reactive = finders.reactiveEntity(caster, range);

		if (reactive instanceof ForceReactiveEntity answers && answers.onForceLift(caster)) {
			return new Choice(ForceDispatch.Step.ENTITY, reactive, null, null, 0f, holdTicks, false, false);
		}

		ServerLevel level = caster.level();
		LivingEntity living = PullTargeting.find(level, caster, caster.getEyePosition(), caster.getLookAngle(), range, b.coneDegrees / 2f,
				e -> ForcePowers.canAffect(caster, e, b.pvpEffects) && !LiftedTargets.isLifted(e) && !(e instanceof Player p && Holds.isHeld(p)),
				e -> !Attitudes.shieldedFromArea(caster, e));

		if (living != null) {
			return chooseLiving(caster, living, holdTicks, b);
		}

		BlockHitResult hit = finders.reactiveBlock(caster, range);

		if (hit != null && ForceDispatch.block(caster, hit, ForceAct.LIFT, UP, 1f, ID)) {
			return new Choice(ForceDispatch.Step.BLOCK, null, null, hit.getBlockPos(), 0f, holdTicks, false, false);
		}

		return Choice.none(false);
	}

	private static Choice chooseLiving(ServerPlayer caster, LivingEntity target, int holdTicks, LiftBalance b) {
		if (!LiftRules.liftable(target.getBbHeight(), b)) {
			caster.sendOverlayMessage(Component.translatable("message.oldrepublic.force.lift_too_heavy"));
			return Choice.none(true);
		}

		// Entity#is(TagKey) checks the c:bosses convention tag (as SaberTechniques does).
		boolean boss = target.is(ConventionalEntityTypeTags.BOSSES);
		boolean resistant = ForcePowers.isResistant(target);
		float height = LiftRules.height(resistant, boss, ForceReactions.liftHeight(target, LiftRules.requested(resistant, boss, b)), b);
		int hold = height > 0f ? ControlEffects.diminished(target, holdTicks, ControlEffects.HOLD) : 0;

		if (hold <= 0) {
			caster.sendOverlayMessage(Component.translatable("message.oldrepublic.force.lift_refused"));
			return Choice.none(true);
		}

		return new Choice(ForceDispatch.Step.LIVING, null, target, null, height, hold, boss, false);
	}

	private static void begin(ServerPlayer caster, ForceDispatch.Finders finders, Choice choice) {
		LiftBalance b = balance();
		LiftShape shape = shape(caster);
		LivingEntity living = choice.living();
		boolean hostile = living != null && ForcePowers.isHostile(caster, living);
		CHANNELS.put(caster.getUUID(), new Channel(caster, finders, choice, shape, hostile));
		int targetId = -1;
		Vec3 pos;

		if (living != null) {
			targetId = living.getId();
			pos = living.getBoundingBox().getCenter();

			if (living instanceof ServerPlayer held) {
				Holds.hold(held, choice.holdTicks(), HOLD_SOURCE, held.position().add(0, choice.height(), 0), true);
			} else {
				LiftedTargets.lift(living, caster.getUUID(), choice.height(), b.riseTicks, shape == LiftShape.SUSPEND);

				if (hostile) {
					living.setLastHurtByMob(caster);
				}
			}

			ForceReactions.interruptWindup(living, caster, ForceReactions.CAUSE_STAGGER);
			EffectEvents.toTracking(living, ForceEffects.LIFT_TARGET, living.position(), null, living.getId(), choice.holdTicks() / 20f, 0);
			QuestHooks.onAbilityHit(caster, ID, living);
		} else if (choice.reactive() != null) {
			targetId = choice.reactive().getId();
			pos = choice.reactive().getBoundingBox().getCenter();
		} else {
			pos = Vec3.atCenterOf(choice.block() == null ? caster.blockPosition() : choice.block());
		}

		EffectEvents.toTracking(caster, ForceEffects.LIFT, pos, caster.getLookAngle(), targetId, choice.holdTicks() / 20f,
				ForceEffects.packCaster(caster.getId(), ForceEffects.LIFT_START));
		QuestHooks.onAbilityUsed(caster, ID, hostile ? 1 : 0);
	}

	/** Advances channels, held creatures and falls; called every server tick. */
	public static void tick() {
		LiftedTargets.tick();

		if (!CHANNELS.isEmpty()) {
			LiftBalance b = balance();

			for (Channel channel : new ArrayList<>(CHANNELS.values())) {
				tickChannel(channel, b);
			}
		}

		if (!FALLS.isEmpty()) {
			tickFalls(balance());
		}
	}

	private static void tickChannel(Channel channel, LiftBalance b) {
		ServerPlayer caster = channel.caster;

		if (!caster.isAlive() || caster.isRemoved()) {
			finish(channel, End.CANCELLED, false);
			return;
		}

		channel.age++;

		if (!holding(channel, b)) {
			finish(channel, End.LOST, true);
			return;
		}

		if (channel.age % PAY_INTERVAL == 0 && !Progression.trySpendEnergy(caster, cost(caster, b.costPerSecond * PAY_INTERVAL / 20f))) {
			finish(channel, End.EXPIRED, true);
			return;
		}

		if (channel.hostile && !channel.masteryGiven && channel.age >= MASTERY_HOLD_TICKS) {
			channel.masteryGiven = true;
			ForcePowers.addMastery(caster, ID, 1);
		}

		if (channel.age >= channel.choice.holdTicks()) {
			finish(channel, End.EXPIRED, true);
		}
	}

	/** Whether the channel still has its target: alive and held, or still on the aim and still answering. */
	private static boolean holding(Channel channel, LiftBalance b) {
		ServerPlayer caster = channel.caster;
		Choice choice = channel.choice;
		float range = LiftRules.range(ForcePowers.masteryRank(caster, ID), b);

		return switch (choice.step()) {
			case LIVING -> {
				LivingEntity target = choice.living();
				yield target != null && target.isAlive() && !target.isRemoved() && target.level() == caster.level()
						&& (target instanceof ServerPlayer held ? heldByLift(held) : LiftedTargets.isLifted(target));
			}
			case ENTITY -> {
				Entity found = channel.finders.reactiveEntity(caster, range);
				yield found != null && found == choice.reactive() && ((ForceReactiveEntity) found).onForceLift(caster);
			}
			case BLOCK -> {
				BlockHitResult hit = channel.finders.reactiveBlock(caster, range);
				yield hit != null && hit.getBlockPos().equals(choice.block()) && ForceDispatch.block(caster, hit, ForceAct.LIFT, UP, 1f, ID);
			}
			case ITEMS, NONE -> false;
		};
	}

	private static boolean heldByLift(ServerPlayer player) {
		Holds.HeldState state = player.getAttached(Holds.HELD);
		return state != null && HOLD_SOURCE.equals(state.source());
	}

	/** Releases the key: the target is slammed (or thrown). */
	private static void end(ServerPlayer player, End end) {
		Channel channel = CHANNELS.get(player.getUUID());

		if (channel != null) {
			finish(channel, end, true);
		}
	}

	private static void finish(Channel channel, End end, boolean feedback) {
		ServerPlayer caster = channel.caster;
		LiftBalance b = balance();

		if (!CHANNELS.remove(caster.getUUID(), channel)) {
			return;
		}

		Progression.setCooldown(caster, ID, ForcePowers.cooldown(caster, ID, b.cooldownTicks));
		Choice choice = channel.choice;
		LivingEntity target = choice.living();
		int targetId = -1;
		Vec3 pos = caster.getEyePosition();

		if (target != null) {
			targetId = target.getId();
			pos = target.getBoundingBox().getCenter();
			let(target, end == End.RELEASED || end == End.EXPIRED ? "release" : "break");
			EffectEvents.toTracking(target, ForceEffects.LIFT_TARGET, target.position(), null, target.getId(), 0f, 0);

			if ((end == End.RELEASED || end == End.EXPIRED) && target.isAlive() && !target.isRemoved()) {
				drop(channel, target, b);
			}
		} else if (choice.reactive() != null) {
			targetId = choice.reactive().getId();
			pos = choice.reactive().getBoundingBox().getCenter();
		} else if (choice.block() != null) {
			pos = Vec3.atCenterOf(choice.block());
		}

		if (feedback) {
			EffectEvents.toTracking(caster, ForceEffects.LIFT, pos, caster.getLookAngle(), targetId, 0f,
					ForceEffects.packCaster(caster.getId(), ForceEffects.LIFT_END));
		}
	}

	/** Lets go of a living target: a player's hold ends, a creature's gravity and AI come back. */
	private static void let(LivingEntity target, String reason) {
		if (target instanceof ServerPlayer held) {
			if (heldByLift(held)) {
				Holds.release(held, reason);
			}
		} else {
			LiftedTargets.release(target, reason);
		}
	}

	/** The end of a hold: slam the target down, or with Hurl throw it along the aim (never a player). */
	private static void drop(Channel channel, LivingEntity target, LiftBalance b) {
		ServerPlayer caster = channel.caster;
		boolean hurl = channel.shape == LiftShape.HURL && !(target instanceof Player);

		if (hurl) {
			Vec3 look = caster.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			Vec3 dir = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
			double speed = LiftRules.hurlSpeed(b.hurlDistance);
			target.setDeltaMovement(dir.x * speed, HURL_LIFT, dir.z * speed);
			EffectEvents.toTracking(caster, ForceEffects.LIFT, target.getBoundingBox().getCenter(), dir, target.getId(), b.hurlDistance,
					ForceEffects.packCaster(caster.getId(), ForceEffects.LIFT_HURL));
		} else {
			target.setDeltaMovement(0, -SLAM_SPEED, 0);
		}

		target.hurtMarked = true;
		FALLS.removeIf(f -> f.target == target);
		FALLS.add(new Fall(caster, target, hurl, channel.choice.boss()));
	}

	private static void tickFalls(LiftBalance b) {
		Iterator<Fall> it = FALLS.iterator();

		while (it.hasNext()) {
			Fall fall = it.next();
			LivingEntity target = fall.target;

			if (!target.isAlive() || target.isRemoved() || fall.caster.isRemoved() || target.level() != fall.caster.level()) {
				it.remove();
				continue;
			}

			fall.age++;

			if (fall.hurl) {
				strikeOther(fall, b);

				if (target.horizontalCollision || target.onGround() && fall.age > 2 || fall.age >= FALL_TIMEOUT) {
					it.remove();
					impact(fall, b.hurlDamage, b.hurlStaggerTicks);
				}
			} else if (target.onGround() && fall.age > 1 || fall.age >= FALL_TIMEOUT) {
				it.remove();
				impact(fall, b.slamDamage, fall.boss ? b.bossStaggerTicks : b.slamStaggerTicks);
			}
		}
	}

	/** The landing: damage, a stagger, and the slam burst at the target's feet. */
	private static void impact(Fall fall, float damage, int staggerTicks) {
		LivingEntity target = fall.target;
		ServerPlayer caster = fall.caster;
		ServerLevel level = caster.level();
		target.hurtServer(level, ForceDamage.source(level, ForceDamage.IMPACT, caster), damage);
		ControlEffects.stagger(caster, target, staggerTicks, STAGGER_AMPLIFIER, ControlEffects.STAGGER);
		target.resetFallDistance();
		EffectEvents.toTracking(caster, ForceEffects.LIFT, target.position(), UP, target.getId(), damage,
				ForceEffects.packCaster(caster.getId(), ForceEffects.LIFT_SLAM));
	}

	/** Hurl: the first creature the thrown target meets takes the same blow. */
	private static void strikeOther(Fall fall, LiftBalance b) {
		if (fall.hitOther) {
			return;
		}

		LivingEntity target = fall.target;
		ServerPlayer caster = fall.caster;
		ServerLevel level = caster.level();

		for (Entity entity : level.getEntities(target, target.getBoundingBox().inflate(0.3), e -> e != caster && e instanceof LivingEntity
				&& ForcePowers.canAffect(caster, e, b.pvpEffects) && !Attitudes.shieldedFromArea(caster, e))) {
			LivingEntity other = (LivingEntity) entity;
			fall.hitOther = true;
			other.hurtServer(level, ForceDamage.source(level, ForceDamage.IMPACT, caster), b.hurlDamage);
			ControlEffects.stagger(caster, other, b.hurlStaggerTicks, STAGGER_AMPLIFIER, ControlEffects.STAGGER);
			QuestHooks.onAbilityHit(caster, ID, other);
			return;
		}
	}

	/** One hit of at least {@code interruptDamage} on the caster ends the channel; the target drops. */
	public static void onDamaged(LivingEntity entity, float damageTaken) {
		if (entity instanceof ServerPlayer player && damageTaken >= balance().interruptDamage) {
			Channel channel = CHANNELS.get(player.getUUID());

			if (channel != null) {
				finish(channel, End.INTERRUPTED, true);
			}
		}
	}

	/**
	 * Suspend (spec 10.2): the caster's allies deal {@code suspendAllyBonus} more to a creature the
	 * caster holds with {@code lift_suspend}. Allies are the caster, players the caster is not fighting
	 * (no damage between the two within {@code force_heal.combatExcludeTicks}, the Shared Light rule),
	 * and creatures {@link Attitudes} reports as allied to the caster.
	 */
	static float suspendBonus(LivingEntity victim, DamageSource source, float amount) {
		UUID casterId = LiftedTargets.suspendedBy(victim);
		Entity attacker = source.getEntity();

		if (casterId == null || attacker == null || attacker == victim || !(victim.level() instanceof ServerLevel level)) {
			return amount;
		}

		ServerPlayer caster = level.getServer().getPlayerList().getPlayer(casterId);
		boolean ally = attacker == caster || caster != null && (attacker instanceof Player other
				? !ForceHeal.inCombat(caster, other, ForceHeal.balance().combatExcludeTicks)
				: attacker instanceof LivingEntity living && Attitudes.toward(living, caster) == Attitude.ALLIED);
		return ally ? amount * (1f + balance().suspendAllyBonus) : amount;
	}

	/** A target or reactive entity left the level (unloaded, discarded): its channel ends and it is let go. */
	private static void onUnload(Entity entity) {
		for (Channel channel : new ArrayList<>(CHANNELS.values())) {
			if (channel.choice.living() == entity || channel.choice.reactive() == entity) {
				finish(channel, End.LOST, true);
			}
		}

		if (entity instanceof LivingEntity living) {
			LiftedTargets.release(living, "unload");
		}

		FALLS.removeIf(f -> f.target == entity);
	}

	/** Drops the player's state (death, disconnect, dimension change): their channel ends, a fall of theirs is forgotten. */
	public static void forget(ServerPlayer player) {
		Channel channel = CHANNELS.get(player.getUUID());

		if (channel != null) {
			finish(channel, End.CANCELLED, true);
		}

		FALLS.removeIf(f -> f.caster == player || f.target == player);
	}

	/** Lets every target go (server stopping). */
	public static void clear() {
		for (Channel channel : new ArrayList<>(CHANNELS.values())) {
			finish(channel, End.CANCELLED, false);
		}

		CHANNELS.clear();
		FALLS.clear();
		LiftedTargets.clear();
	}
}
