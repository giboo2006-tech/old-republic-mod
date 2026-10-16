package com.oldrepublic.force.pull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.duel.SaberDuelist;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Force Pull ({@code force_pull}, spec 8.1): a tap that reaches for one creature (or loose items)
 * and yanks it to the caster. Server side: targeting at the press, a short visible reach, then the
 * pull, a brake near the caster so the target lands at the caster's feet, and the Disarming and
 * Reeling upgrades.
 */
public final class ForcePull implements ForceAbility {
	public static final String ID = "force_pull";
	private static final int STAGGER_AMPLIFIER = 2;

	/** A pull between the press and the end of the reach. Nothing is spent until it lands. */
	private static final class Reach {
		final ServerPlayer caster;
		final @Nullable LivingEntity target;
		final Vec3 itemPoint;
		final PullShape shape;
		int age;

		Reach(ServerPlayer caster, @Nullable LivingEntity target, Vec3 itemPoint, PullShape shape) {
			this.caster = caster;
			this.target = target;
			this.itemPoint = itemPoint;
			this.shape = shape;
		}
	}

	/** A pulled creature on its way to the caster. */
	private static final class Drag {
		final ServerPlayer caster;
		final LivingEntity target;
		final PullShape shape;
		int age;
		boolean arrived;

		Drag(ServerPlayer caster, LivingEntity target, PullShape shape) {
			this.caster = caster;
			this.target = target;
			this.shape = shape;
		}
	}

	private static final Map<UUID, Reach> REACHING = new HashMap<>();
	private static final List<Drag> DRAGS = new ArrayList<>();
	/** Reeling Pull: caster UUID to the game time until which the next saber hit is empowered. */
	private static final Map<UUID, Long> REEL_BONUS = new HashMap<>();

	public static PullBalance balance() {
		return BalanceConfig.section("force_pull", PullBalance.class, PullBalance::new);
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
			start(p);
		} else if (action == AbilityAction.CANCEL) {
			cancel(p, true);
		}
	}

	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
		REACHING.remove(p.getUUID());
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return REACHING.containsKey(p.getUUID());
	}

	/**
	 * Keys: {@code cost}, {@code cooldown_s}, {@code range}, {@code aim_assist}, {@code speed_max},
	 * {@code mastery_range_pct}, {@code mastery_cost_pct}, {@code knock_mult} (Disarming),
	 * {@code disarm_seconds}, {@code no_guard_seconds}, {@code reel_mult}, {@code reel_stagger_s},
	 * {@code reel_bonus_pct}, {@code reel_window_s}.
	 */
	@Override
	public Map<String, Float> info() {
		PullBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("cost", b.cost);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("range", b.range);
		info.put("aim_assist", b.aimAssistHalfAngle);
		info.put("speed_max", b.speedMax);
		info.put("mastery_range_pct", b.masteryRangePerRank * 100f);
		info.put("mastery_cost_pct", b.masteryCostPerRank * 100f);
		info.put("knock_mult", b.disarmKnockMult);
		info.put("disarm_seconds", b.disarmTicks / 20f);
		info.put("no_guard_seconds", b.acolyteNoGuardTicks / 20f);
		info.put("reel_mult", b.reelKnockMult);
		info.put("reel_stagger_s", b.reelStaggerTicks / 20f);
		info.put("reel_bonus_pct", b.reelBonus * 100f);
		info.put("reel_window_s", b.reelWindowTicks / 20f);
		return info;
	}

	/** The player's pull upgrade. */
	public static PullShape shape(ServerPlayer player) {
		ForceData data = Progression.get(player);

		for (PullShape shape : PullShape.values()) {
			if (shape != PullShape.NONE && ForcePowers.hasNode(player, data, shape.nodeId())) {
				return shape;
			}
		}

		return PullShape.NONE;
	}

	/** Reach in blocks after mastery. */
	public static float range(PullBalance b, int masteryRank) {
		return b.range * (1f + b.masteryRangePerRank * Mth.clamp(masteryRank, 0, 3));
	}

	/** Force Energy cost after mastery (before skill, alignment and form multipliers). */
	public static float baseCost(PullBalance b, int masteryRank) {
		return b.cost * Math.max(0f, 1f - b.masteryCostPerRank * Mth.clamp(masteryRank, 0, 3));
	}

	private static float cost(ServerPlayer player) {
		return baseCost(balance(), ForcePowers.masteryRank(player, ID)) * ForcePowers.costMultiplier(player, ID);
	}

	/** Press: pick the target now (no cost if there is none), then reach for {@code windupTicks}. */
	static void start(ServerPlayer player) {
		if (REACHING.containsKey(player.getUUID()) || !ForcePowers.mayStart(player, ID)) {
			return;
		}

		PullBalance b = balance();
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		float range = range(b, ForcePowers.masteryRank(player, ID));
		LivingEntity target = PullTargeting.find(level, player, eye, look, range, b.aimAssistHalfAngle,
				e -> ForcePowers.canAffect(player, e, b.pvpEffects));
		Vec3 end = PullTargeting.rayEnd(level, player, eye, look, range);

		if (target == null && PullTargeting.items(level, end, b.itemRadius).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_pull.no_target"));
			return;
		}

		if (!ForcePowers.canAfford(player, cost(player))) {
			return;
		}

		PullShape shape = shape(player);
		REACHING.put(player.getUUID(), new Reach(player, target, end, shape));
		EffectEvents.toTracking(player, ForceEffects.PULL, hand(player), look, player.getId(), 1f, shape.ordinal());
	}

	/** Approximate position of the reaching hand (for effect anchors). */
	static Vec3 hand(ServerPlayer player) {
		return player.getEyePosition().add(player.getLookAngle().scale(0.6)).subtract(0, 0.35, 0);
	}

	/** End of the reach: spend, set the cooldown and pull. Returns false if it fizzled (nothing spent). */
	private static boolean land(Reach reach) {
		ServerPlayer caster = reach.caster;
		PullBalance b = balance();
		float range = range(b, ForcePowers.masteryRank(caster, ID));
		LivingEntity target = reach.target;
		boolean valid = target != null && target.isAlive() && !target.isRemoved() && target.level() == caster.level()
				&& target.distanceTo(caster) <= range + 2.0
				&& PushTargeting.hasLineOfSight(caster.level(), caster, caster.getEyePosition(), PullTargeting.aimPoint(target));
		List<ItemEntity> items = target == null ? PullTargeting.items(caster.level(), reach.itemPoint, b.itemRadius) : List.of();

		if (!valid && items.isEmpty() || !caster.isAlive() || !Progression.trySpendEnergy(caster, cost(caster))) {
			return false;
		}

		Progression.setCooldown(caster, ID, ForcePowers.cooldown(caster, ID, b.cooldownTicks));
		int hostile = 0;

		if (valid) {
			pull(caster, target, reach.shape);
			hostile = ForcePowers.isHostile(target) ? 1 : 0;
			QuestHooks.onAbilityHit(caster, ID, target);
		} else {
			pullItems(caster, items, reach.itemPoint, b);
			QuestHooks.onAbilityHit(caster, ID, null);
		}

		ForcePowers.addMastery(caster, ID, hostile);
		QuestHooks.onAbilityUsed(caster, ID, hostile);
		return true;
	}

	/**
	 * Speed multiplier for {@code target}: the shape, push resistance (the common
	 * {@code force_push.resistantKnockbackMult}, x0.4), knockback resistance (weighted 0.6) and a
	 * saber guard facing the caster (x0.5).
	 */
	public static float multiplier(PullBalance b, LivingEntity target, PullShape shape, boolean guarding) {
		float mult = shape.speedMult(b) * ForcePowers.knockbackFactor(target, b.knockbackResistanceWeight);

		if (ForcePowers.isResistant(target)) {
			mult *= ForcePush.balance().resistantKnockbackMult;
		}

		return guarding ? mult * b.guardingMult : mult;
	}

	/**
	 * The velocity that pulls a creature at {@code targetPos} toward a caster at {@code casterPos}:
	 * horizontal speed {@code min(speedMax, speedBase + speedPerBlock * distance)} plus a lift that
	 * grows with how far the target is below the caster, all times {@code mult}.
	 */
	public static Vec3 velocity(PullBalance b, Vec3 casterPos, Vec3 targetPos, float mult) {
		Vec3 offset = casterPos.subtract(targetPos);
		Vec3 flat = new Vec3(offset.x, 0, offset.z);
		double distance = offset.length();
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		double speed = Math.min(b.speedMax, b.speedBase + b.speedPerBlock * distance);
		double lift = b.lift + b.liftPerBlockAbove * Math.max(0.0, offset.y);
		return dir.scale(speed * mult).add(0, lift * mult, 0);
	}

	/**
	 * Pulls {@code target} toward {@code caster} now with {@code shape} (the landing of a reach; no
	 * cost or cooldown here). Public for the game tests.
	 */
	public static void pull(ServerPlayer caster, LivingEntity target, PullShape shape) {
		PullBalance b = balance();
		boolean guarding = ForceReactions.isGuarding(target, caster.getEyePosition());
		Vec3 velocity = velocity(b, caster.position(), target.position(), multiplier(b, target, shape, guarding));
		target.setDeltaMovement(velocity);
		target.hurtMarked = true;

		if (target instanceof Enemy && !(target instanceof Player)) {
			target.setLastHurtByMob(caster);
		}

		DRAGS.removeIf(d -> d.target == target);
		DRAGS.add(new Drag(caster, target, shape));
		Vec3 toCaster = caster.position().subtract(target.position());
		EffectEvents.around(caster.level(), ForceEffects.PULL_HIT, PullTargeting.aimPoint(target),
				toCaster.lengthSqr() > 1.0e-4 ? toCaster.normalize() : Vec3.ZERO, target.getId(), (float) velocity.length(), ForceEffects.PULL_LAUNCHED);

		if (shape == PullShape.DISARM) {
			disarm(caster, target, b);
		}
	}

	/** Disarming Pull: weapons out of line, guards knocked open, a short item cooldown for players. */
	private static void disarm(ServerPlayer caster, LivingEntity target, PullBalance b) {
		int ticks = ControlEffects.diminished(target, b.disarmTicks);

		if (ticks <= 0) {
			return;
		}

		if (target instanceof Player player) {
			ItemStack held = player.getMainHandItem();

			if (!held.isEmpty()) {
				player.getCooldowns().addCooldown(held, b.pvpItemCooldownTicks);
			}

			player.stopUsingItem();
		} else {
			ForceStatus.disarm(target, ticks);
		}

		if (target instanceof SaberDuelist) {
			ForceReactions.drainGuard(target, b.acolyteGuardDrain);
			ForceStatus.suppressGuard(target, b.acolyteNoGuardTicks);
		}

		ForceReactions.interruptWindup(target);
		EffectEvents.around(caster.level(), ForceEffects.PULL_DISARM, weaponHand(target), target.getLookAngle(), target.getId(), 1f, 0);
	}

	/** Approximate position of a creature's main hand. */
	private static Vec3 weaponHand(LivingEntity entity) {
		double yaw = Math.toRadians(entity.yBodyRot);
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return entity.position().add(0, entity.getBbHeight() * 0.55, 0).add(right.scale(entity.getBbWidth() * 0.6));
	}

	private static void pullItems(ServerPlayer caster, List<ItemEntity> items, Vec3 point, PullBalance b) {
		Vec3 hand = hand(caster);

		for (ItemEntity item : items) {
			Vec3 velocity = velocity(b, caster.position(), item.position(), 1f);
			item.setDeltaMovement(velocity);
			item.hurtMarked = true;
		}

		EffectEvents.around(caster.level(), ForceEffects.PULL_HIT, point, hand.subtract(point), caster.getId(), items.size(), ForceEffects.PULL_ITEMS);
	}

	/** Advances reaches and drags; called every server tick. */
	public static void tick() {
		Iterator<Reach> reaches = REACHING.values().iterator();
		List<Reach> landing = new ArrayList<>();

		while (reaches.hasNext()) {
			Reach reach = reaches.next();

			if (!reach.caster.isAlive() || reach.caster.isRemoved()) {
				reaches.remove();
			} else if (++reach.age >= balance().windupTicks) {
				reaches.remove();
				landing.add(reach);
			}
		}

		for (Reach reach : landing) {
			land(reach);
		}

		tickDrags();
	}

	private static void tickDrags() {
		PullBalance b = balance();
		Iterator<Drag> it = DRAGS.iterator();

		while (it.hasNext()) {
			Drag drag = it.next();
			LivingEntity target = drag.target;

			if (!target.isAlive() || target.isRemoved() || drag.caster.isRemoved() || target.level() != drag.caster.level()
					|| ++drag.age > b.brakeWindowTicks) {
				it.remove();
				continue;
			}

			if (target.distanceTo(drag.caster) > b.brakeDistance) {
				continue;
			}

			// Close to the caster: brake so the target lands at the caster's feet instead of flying past.
			target.setDeltaMovement(braked(b, target.getDeltaMovement()));
			target.hurtMarked = true;

			if (!drag.arrived) {
				drag.arrived = true;
				arrive(drag, b);
			}
		}
	}

	/** A dragged target's velocity once it is within the brake distance: horizontal speed x0.3. */
	public static Vec3 braked(PullBalance b, Vec3 velocity) {
		return new Vec3(velocity.x * b.brakeFactor, velocity.y, velocity.z * b.brakeFactor);
	}

	private static void arrive(Drag drag, PullBalance b) {
		ServerPlayer caster = drag.caster;
		EffectEvents.around(caster.level(), ForceEffects.PULL_HIT, caster.position(), Vec3.ZERO, drag.target.getId(), 0f, ForceEffects.PULL_ARRIVED);

		if (drag.shape == PullShape.REEL) {
			ControlEffects.stagger(caster, drag.target, b.reelStaggerTicks, STAGGER_AMPLIFIER);
			REEL_BONUS.put(caster.getUUID(), caster.level().getGameTime() + b.reelWindowTicks);
		}
	}

	/**
	 * Reeling Pull's empowered strike: the caster's next melee hit with an ignited saber inside the
	 * window deals {@code 1 + reelBonus} times the damage. Called from the damage mixin.
	 */
	public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
		if (REEL_BONUS.isEmpty() || !(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker) {
			return amount;
		}

		Long until = REEL_BONUS.get(attacker.getUUID());

		if (until == null || target == attacker || !SaberComponents.isIgnited(attacker.getMainHandItem())) {
			return amount;
		}

		REEL_BONUS.remove(attacker.getUUID());
		return attacker.level().getGameTime() <= until ? amount * (1f + balance().reelBonus) : amount;
	}

	/** Drops the player's state (death, disconnect, dimension change). */
	public static void forget(ServerPlayer player) {
		REACHING.remove(player.getUUID());
		REEL_BONUS.remove(player.getUUID());
		DRAGS.removeIf(d -> d.caster == player || d.target == player);
	}

	public static void clear() {
		REACHING.clear();
		DRAGS.clear();
		REEL_BONUS.clear();
	}

	/** Whether {@code target} is being dragged toward a caster right now. */
	public static boolean isDragged(LivingEntity target) {
		for (Drag drag : DRAGS) {
			if (drag.target == target) {
				return true;
			}
		}

		return false;
	}
}
