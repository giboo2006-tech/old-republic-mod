package com.oldrepublic.force.fear;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceMobEffects;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.choke.ForceChoke;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code fear} control (MILESTONE_5.md 9.2): the runtime behind {@link ControlEffects#FEAR}, shared by Force Fear,
 * Choke's Dread and the saber's Dread Presence ({@link ForceFear#apply}). A frightened creature has the
 * {@code oldrepublic:fear} effect (icon and timer), and:
 * <ul>
 * <li>a {@link PathfinderMob} gets a priority-0 flee goal (move and look) that runs to a spot away from what frightened it
 * ({@code DefaultRandomPos.getPosAway}, run speed {@code fleeSpeed}), and drops its target every second;</li>
 * <li>a creature without goals, and a player (PvP), gets Slowness II (hidden); a player also cannot sprint (the client
 * half, {@code mixin.force.LocalPlayerFearMixin}, refuses to start a sprint while the local player is afraid).</li>
 * </ul>
 * Bosses ({@code #c:bosses}), droids ({@code #oldrepublic:droids}) and {@code #oldrepublic:fearless} creatures are never
 * frightened. The effect is the state: when it ends for any reason the goal goes on the next tick. A creature that loads
 * with the effect (a reloaded chunk) gets its goal back. Nightmare's frightened target takes more damage while afraid.
 * Server side.
 */
public final class Frightened {
	/** One frightened creature: what it runs from, who frightened it, whether it is Nightmare's target. */
	private static final class Fear {
		final LivingEntity target;
		Vec3 from;
		final @Nullable UUID source;
		boolean nightmare;
		@Nullable FleeGoal goal;

		Fear(LivingEntity target, Vec3 from, @Nullable UUID source, boolean nightmare) {
			this.target = target;
			this.from = from;
			this.source = source;
			this.nightmare = nightmare;
		}
	}

	/** A frightened creature drops its target this often. */
	private static final int DROP_TARGET_INTERVAL = 20;
	private static final Map<UUID, Fear> FEARS = new HashMap<>();

	/**
	 * Frightens {@code target} for {@code baseTicks} before the control rules (the length comes from
	 * {@link ControlEffects#diminished(LivingEntity, int, String)} alone). {@code from} is what it runs from: the given
	 * point, else the source's position, else its own target's. Returns the ticks applied, 0 when it is immune or the
	 * diminishing returns used it up.
	 */
	public static int apply(LivingEntity target, @Nullable ServerPlayer source, int baseTicks, @Nullable Vec3 from, boolean nightmare) {
		if (!target.isAlive() || target.isRemoved() || baseTicks <= 0 || isImmune(target)) {
			return 0;
		}

		int ticks = ControlEffects.diminished(target, baseTicks, ControlEffects.FEAR);

		if (ticks <= 0) {
			return 0;
		}

		ForceMobEffects.apply(target, ForceMobEffects.FEAR, ticks, source);
		Vec3 point = from != null ? from : source != null ? source.position() : threatOf(target);
		Fear fear = FEARS.get(target.getUUID());

		if (fear == null || fear.target != target) {
			fear = new Fear(target, point, source == null ? null : source.getUUID(), nightmare);
			FEARS.put(target.getUUID(), fear);
		} else {
			fear.from = point;
			fear.nightmare |= nightmare;
		}

		FearBalance b = ForceFear.balance();

		if (target instanceof PathfinderMob mob) {
			addGoal(mob, fear, b);
			mob.setTarget(null);
		} else {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, b.slownessAmplifier, false, false, false), source);

			if (target instanceof Player player) {
				player.setSprinting(false);
			}
		}

		ForceReactions.interruptWindup(target, source, ForceReactions.CAUSE_HEAVY_FEAR);
		return ticks;
	}

	/** Whether nothing frightens {@code target}: a boss, a droid or a fearless creature. */
	public static boolean isImmune(LivingEntity target) {
		return target.is(ConventionalEntityTypeTags.BOSSES) || target.is(ForceChoke.DROIDS) || target.is(ForceFear.FEARLESS);
	}

	/** Whether {@code entity} is afraid now (any side: it reads the effect). */
	public static boolean isAfraid(LivingEntity entity) {
		return entity.hasEffect(ForceMobEffects.FEAR);
	}

	/** What a creature frightened by no one runs from: whatever it was after, else nothing in particular. */
	private static Vec3 threatOf(LivingEntity target) {
		LivingEntity threat = target instanceof PathfinderMob mob ? mob.getTarget() : null;

		if (threat == null) {
			threat = target.getLastHurtByMob();
		}

		return threat != null ? threat.position() : target.position().add(target.getLookAngle());
	}

	private static void addGoal(PathfinderMob mob, Fear fear, FearBalance b) {
		if (fear.goal != null) {
			return;
		}

		fear.goal = new FleeGoal(mob, b);
		mob.getGoalSelector().addGoal(0, fear.goal);
	}

	/** Advances every fear; called every server tick. */
	public static void tick() {
		if (FEARS.isEmpty()) {
			return;
		}

		for (Fear fear : new ArrayList<>(FEARS.values())) {
			LivingEntity target = fear.target;

			if (!target.isAlive() || target.isRemoved() || !target.hasEffect(ForceMobEffects.FEAR)) {
				end(fear);
				continue;
			}

			if (target instanceof PathfinderMob mob) {
				if ((mob.tickCount + mob.getId()) % DROP_TARGET_INTERVAL == 0) {
					mob.setTarget(null);
				}
			} else if (target instanceof Player player && player.isSprinting()) {
				player.setSprinting(false);
			}
		}
	}

	private static void end(Fear fear) {
		FEARS.remove(fear.target.getUUID());

		if (fear.goal != null && fear.target instanceof PathfinderMob mob) {
			mob.getGoalSelector().removeGoal(fear.goal);
			fear.goal = null;
		}
	}

	/**
	 * Nightmare's {@code DamageModifier}: a creature frightened by {@code fear_dread} takes {@code nightmareDamagePct} more
	 * damage while the fear lasts.
	 */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (FEARS.isEmpty()) {
			return amount;
		}

		Fear fear = FEARS.get(victim.getUUID());

		if (fear == null || !fear.nightmare || fear.target != victim || !victim.hasEffect(ForceMobEffects.FEAR)) {
			return amount;
		}

		return amount * (1f + ForceFear.balance().nightmareDamagePct / 100f);
	}

	/** Fabric {@code ENTITY_LOAD}: a creature that comes back with the effect (a reloaded chunk or save) gets its goal back. */
	public static void onLoad(Entity entity) {
		if (entity instanceof PathfinderMob mob && mob.hasEffect(ForceMobEffects.FEAR) && !FEARS.containsKey(mob.getUUID())) {
			Fear fear = new Fear(mob, threatOf(mob), null, false);
			FEARS.put(mob.getUUID(), fear);
			addGoal(mob, fear, ForceFear.balance());
		}
	}

	/** Fabric {@code ENTITY_UNLOAD}: forgets a creature leaving the world (its goal goes with the object). */
	public static void onUnload(Entity entity) {
		Fear fear = FEARS.get(entity.getUUID());

		if (fear != null && fear.target == entity) {
			end(fear);
		}
	}

	public static void clear() {
		FEARS.clear();
	}

	/**
	 * The flee: runs to a spot up to {@code fleeHorizontal} blocks away from the threat (and {@code fleeVertical} up or
	 * down), and picks a new one whenever it arrives, until the fear ends. Move and look, priority 0, so the creature's
	 * attack goals cannot run meanwhile.
	 */
	static final class FleeGoal extends Goal {
		private final PathfinderMob mob;
		private final FearBalance balance;

		FleeGoal(PathfinderMob mob, FearBalance balance) {
			this.mob = mob;
			this.balance = balance;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return mob.hasEffect(ForceMobEffects.FEAR);
		}

		@Override
		public boolean canContinueToUse() {
			return mob.hasEffect(ForceMobEffects.FEAR);
		}

		@Override
		public void start() {
			run();
		}

		@Override
		public void tick() {
			if (mob.getNavigation().isDone()) {
				run();
			}
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		private void run() {
			Fear fear = FEARS.get(mob.getUUID());
			Vec3 from = fear != null ? fear.from : mob.position();
			Vec3 away = DefaultRandomPos.getPosAway(mob, balance.fleeHorizontal, balance.fleeVertical, from);

			if (away != null) {
				mob.getNavigation().moveTo(away.x, away.y, away.z, balance.fleeSpeed);
			}
		}
	}

	private Frightened() {
	}
}
