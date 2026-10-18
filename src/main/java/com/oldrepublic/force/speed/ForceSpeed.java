package com.oldrepublic.force.speed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.equipment.SetBonuses;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Force Speed ({@code force_speed}, spec MILESTONE_4.md 9.1): a tap that starts a run of
 * {@code durationTicks} with movement speed +40% (a transient {@code ADD_MULTIPLIED_BASE} modifier
 * {@code oldrepublic:force_speed}), +0.5 step height and halved sprinting hunger. Sprint jumps carry
 * further on the client ({@code client.force.speed.SpeedFx}), which owns the local player's movement.
 *
 * <p>The run is a state, not a channel: {@link #isActive} stays false, so other powers stay usable
 * while it lasts. It ends on a stagger, slow or shock (all apply slowness), a hold, a net, water,
 * riding, a second cast (no refund), death, disconnect and a change of level; the cooldown starts when
 * it ends. Burst dashes along the look for the first ticks; Long Road runs longer for more energy;
 * Wayfarer turns some projectiles aside and removes short-fall damage.
 */
public final class ForceSpeed implements ForceAbility {
	public static final String ID = "force_speed";
	public static final String BURST = "speed_burst";
	public static final String LONG_ROAD = "speed_long_road";
	public static final String WAYFARER = "speed_wayfarer";
	/** Effect event {@code force_speed} (spec 13.3): toTracking, variants below, intensity = run seconds on start. */
	public static final Identifier EFFECT = OldRepublicMod.id("force_speed");
	public static final int VARIANT_START = 0;
	public static final int VARIANT_END = 1;
	public static final int VARIANT_BURST = 2;
	/** Transient attribute modifiers of a run. */
	public static final Identifier SPEED_MODIFIER = OldRepublicMod.id("force_speed");
	public static final Identifier STEP_MODIFIER = OldRepublicMod.id("force_speed_step");
	/** Vanilla sprinting exhaustion per block (Player#checkMovementStatistics). */
	private static final float SPRINT_EXHAUSTION_PER_BLOCK = 0.1f;
	/** How far ahead (ticks) a projectile's path is checked against a Wayfarer runner. */
	private static final int WAYFARER_LOOKAHEAD = 10;
	/** Closest approach (blocks from the chest) a turned projectile is steered to. */
	private static final double WAYFARER_CLEARANCE = 0.85;
	private static final RandomSource RANDOM = RandomSource.create();

	/** One run in progress. */
	private static final class Run {
		final ServerPlayer player;
		final long start;
		final long end;
		final boolean burst;
		final boolean wayfarer;
		final int burstTicks;
		Vec3 dash = Vec3.ZERO;
		int dashTicks;
		double lastX;
		double lastZ;
		double travelled;
		boolean bursting;

		Run(ServerPlayer player, long start, long end, boolean burst, boolean wayfarer, int burstTicks) {
			this.player = player;
			this.start = start;
			this.end = end;
			this.burst = burst;
			this.wayfarer = wayfarer;
			this.burstTicks = burstTicks;
			this.lastX = player.getX();
			this.lastZ = player.getZ();
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	/** Projectiles a Wayfarer runner already rolled against (once each). */
	private static final Set<UUID> ROLLED = new HashSet<>();

	public static SpeedBalance balance() {
		return BalanceConfig.section("force_speed", SpeedBalance.class, SpeedBalance::new);
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

	/** A run is a state, never a channel: nothing to cancel on a slot or screen change. */
	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return false;
	}

	/**
	 * Keys: {@code cost}, {@code cooldown_s}, {@code duration_s}, {@code speed_pct},
	 * {@code burst_speed_pct}, {@code burst_distance}, {@code long_road_s}, {@code long_road_cost},
	 * {@code wayfarer_miss_pct}, {@code wayfarer_fall}, {@code mastery_speed_cost},
	 * {@code mastery_duration_s}.
	 */
	@Override
	public Map<String, Float> info() {
		SpeedBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("cost", b.cost);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("duration_s", b.durationTicks / 20f);
		info.put("speed_pct", b.speedPct);
		info.put("burst_speed_pct", b.burstSpeedPct);
		info.put("burst_distance", b.burstDistance);
		info.put("long_road_s", b.longRoadTicks / 20f);
		info.put("long_road_cost", b.longRoadExtraCost);
		info.put("wayfarer_miss_pct", b.wayfarerMissChance * 100f);
		info.put("wayfarer_fall", b.wayfarerSafeFall);
		info.put("mastery_speed_cost", b.masteryCostPerRank);
		info.put("mastery_duration_s", b.masteryTicksPerRank / 20f);
		return info;
	}

	// ---------------------------------------------------------------- rules

	/** Force Energy of a cast at mastery {@code rank}, before the cost multipliers. */
	public static float cost(SpeedBalance b, int rank, boolean longRoad) {
		return Math.max(0f, b.cost + (longRoad ? b.longRoadExtraCost : 0f) - b.masteryCostPerRank * Mth.clamp(rank, 0, 3));
	}

	/** Ticks a run lasts at mastery {@code rank}. */
	public static int duration(SpeedBalance b, int rank, boolean longRoad) {
		return (longRoad ? b.longRoadTicks : b.durationTicks) + b.masteryTicksPerRank * Mth.clamp(rank, 0, 3);
	}

	/** The speed bonus (a fraction) {@code age} ticks into a run. */
	public static float speedBonus(SpeedBalance b, boolean burst, int age) {
		return (burst && age < b.burstTicks ? b.burstSpeedPct : b.speedPct) / 100f;
	}

	/** Whether {@code player}'s run is up. */
	public static boolean isRunning(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		return run != null && run.player == player;
	}

	/** Whether {@code entity} is a player running with the Wayfarer branch. */
	public static boolean isWayfaring(LivingEntity entity) {
		Run run = RUNS.get(entity.getUUID());
		return run != null && run.player == entity && run.wayfarer;
	}

	// ---------------------------------------------------------------- cast and end

	static void cast(ServerPlayer player) {
		Run running = RUNS.get(player.getUUID());

		if (running != null) {
			// A second cast ends the run early; nothing is refunded.
			end(running, true);
			return;
		}

		if (!ForcePowers.mayStart(player, ID)) {
			return;
		}

		if (blocked(player)) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_speed.cannot_run"));
			return;
		}

		SpeedBalance b = balance();
		ForceData data = Progression.get(player);
		boolean longRoad = ForcePowers.hasNode(player, data, LONG_ROAD);
		boolean burst = !longRoad && ForcePowers.hasNode(player, data, BURST);
		boolean wayfarer = ForcePowers.hasNode(player, data, WAYFARER);
		int rank = ForcePowers.masteryRank(player, ID);

		if (!Progression.trySpendEnergy(player, cost(b, rank, longRoad) * ForcePowers.costMultiplier(player, ID))) {
			return;
		}

		long now = player.level().getGameTime();
		// Light on Your Feet (the spacer set, W4) lengthens the run.
		int ticks = Math.round(duration(b, rank, longRoad) * SetBonuses.forceSpeedDurationMultiplier(player));
		Run run = new Run(player, now, now + ticks, burst, wayfarer, b.burstTicks);
		RUNS.put(player.getUUID(), run);
		applyModifiers(player, speedBonus(b, burst, 0), b);
		EffectEvents.toTracking(player, EFFECT, player.position(), player.getLookAngle(), player.getId(), ticks / 20f, VARIANT_START);

		if (burst) {
			startDash(run, b);
		}

		QuestHooks.onAbilityUsed(player, ID, 0);
	}

	/** The burst dash: up to {@code burstDistance} along the horizontal look, short of the first wall. */
	private static void startDash(Run run, SpeedBalance b) {
		ServerPlayer player = run.player;
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);

		if (flat.lengthSqr() < 1.0e-4) {
			return;
		}

		Vec3 dir = flat.normalize();
		double distance = dashDistance(player, dir, b);

		if (distance < 0.5) {
			return;
		}

		run.dashTicks = Math.max(1, b.burstDashTicks);
		run.dash = dir.scale(distance / run.dashTicks);
		EffectEvents.toTracking(player, EFFECT, player.position(), dir, player.getId(), (float) distance, VARIANT_BURST);
	}

	/**
	 * How far a burst dash from {@code player} along the horizontal unit vector {@code dir} goes: the
	 * full {@code burstDistance}, or half a block short of the first wall in the way.
	 */
	public static double dashDistance(LivingEntity player, Vec3 dir, SpeedBalance b) {
		Vec3 from = player.position().add(0, 0.5, 0);
		HitResult hit = player.level().clip(new ClipContext(from, from.add(dir.scale(b.burstDistance)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? b.burstDistance : Math.max(0.0, from.distanceTo(hit.getLocation()) - 0.5);
	}

	/** Ends {@code player}'s run, if any (a net, a hold or another feature's rule). */
	public static void end(ServerPlayer player, boolean feedback) {
		Run run = RUNS.get(player.getUUID());

		if (run != null) {
			end(run, feedback);
		}
	}

	private static void end(Run run, boolean feedback) {
		ServerPlayer player = run.player;
		RUNS.remove(player.getUUID());
		removeModifiers(player);
		Progression.setCooldown(player, ID, ForcePowers.cooldown(player, ID, balance().cooldownTicks));

		if (feedback) {
			EffectEvents.toTracking(player, EFFECT, player.position(), player.getLookAngle(), player.getId(), 0f, VARIANT_END);
		}
	}

	/** Things that end a run (and refuse a cast): slowed, staggered or shocked (slowness), held, in water, riding. */
	static boolean blocked(ServerPlayer player) {
		return player.hasEffect(MobEffects.SLOWNESS) || Holds.isHeld(player) || player.isInWater() || player.isPassenger();
	}

	// ---------------------------------------------------------------- ticking

	/** Advances every run; called every server tick. */
	public static void tick() {
		if (RUNS.isEmpty()) {
			if (!ROLLED.isEmpty()) {
				ROLLED.clear();
			}

			return;
		}

		SpeedBalance b = balance();

		for (Run run : new ArrayList<>(RUNS.values())) {
			tickRun(run, b);
		}
	}

	private static void tickRun(Run run, SpeedBalance b) {
		ServerPlayer player = run.player;

		if (!player.isAlive() || player.isRemoved()) {
			end(run, false);
			return;
		}

		long now = player.level().getGameTime();

		if (now >= run.end || blocked(player)) {
			end(run, true);
			return;
		}

		int age = (int) (now - run.start);
		boolean bursting = run.burst && age < run.burstTicks;

		if (bursting != run.bursting || age == 0) {
			run.bursting = bursting;
			applyModifiers(player, speedBonus(b, run.burst, age), b);
		}

		if (run.dashTicks > 0) {
			run.dashTicks--;
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(run.dash.x, Math.min(v.y, 0.1), run.dash.z);
			player.hurtMarked = true;

			if (run.dashTicks == 0) {
				Vec3 after = player.getDeltaMovement();
				player.setDeltaMovement(after.x * 0.4, after.y, after.z * 0.4);
			}
		}

		double dx = player.getX() - run.lastX;
		double dz = player.getZ() - run.lastZ;
		run.lastX = player.getX();
		run.lastZ = player.getZ();
		double moved = Math.sqrt(dx * dx + dz * dz);

		// A teleport is not running.
		if (moved < 4.0) {
			countDistance(run, moved, b);

			if (player.isSprinting() && moved > 0.0) {
				// API-CHECK: Player#causeFoodExhaustion with a negative amount lowers exhaustion (FoodData#addExhaustion adds it unclamped below).
				player.causeFoodExhaustion((float) (-SPRINT_EXHAUSTION_PER_BLOCK * (1.0 - b.sprintHungerMult) * moved));
			}
		}

		if (run.wayfarer) {
			wayfarer(run, b);
		}
	}

	private static void countDistance(Run run, double moved, SpeedBalance b) {
		run.travelled += moved;

		if (b.masteryBlocks > 0f && run.travelled >= b.masteryBlocks) {
			run.travelled -= b.masteryBlocks;
			ForcePowers.addMastery(run.player, ID, 1);
		}
	}

	/** Mastery points a run of {@code blocks} earns. */
	public static int masteryFor(double blocks, SpeedBalance b) {
		return b.masteryBlocks <= 0f ? 0 : (int) Math.floor(blocks / b.masteryBlocks);
	}

	private static void applyModifiers(ServerPlayer player, float bonus, SpeedBalance b) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_MODIFIER, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}

		AttributeInstance step = player.getAttribute(Attributes.STEP_HEIGHT);

		if (step != null) {
			step.addOrUpdateTransientModifier(new AttributeModifier(STEP_MODIFIER, b.stepHeight, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Removes the run's modifiers; safe when there are none. */
	public static void removeModifiers(LivingEntity player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed != null) {
			speed.removeModifier(SPEED_MODIFIER);
		}

		AttributeInstance step = player.getAttribute(Attributes.STEP_HEIGHT);

		if (step != null) {
			step.removeModifier(STEP_MODIFIER);
		}
	}

	// ---------------------------------------------------------------- Wayfarer

	/**
	 * Turns aside some projectiles about to hit a Wayfarer runner: each projectile rolls once; on a
	 * miss its path bends by at most {@code wayfarerMaxDegrees}, and only if the new path is as clear
	 * of walls as the old one.
	 */
	private static void wayfarer(Run run, SpeedBalance b) {
		ServerPlayer player = run.player;
		ServerLevel level = player.level();
		Vec3 chest = ForcePowers.chest(player);

		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(24.0),
				p -> p.isAlive() && p.getOwner() != player)) {
			Vec3 v = projectile.getDeltaMovement();

			if (v.lengthSqr() < 0.04 || ROLLED.contains(projectile.getUUID())) {
				continue;
			}

			Vec3 bent = wayfarerPath(projectile.position(), v, chest, b, RANDOM.nextFloat());

			if (bent == null) {
				continue;
			}

			ROLLED.add(projectile.getUUID());

			if (bent.equals(v) || !clearAsBefore(level, projectile, v, bent)) {
				continue;
			}

			projectile.setDeltaMovement(bent);
			projectile.hurtMarked = true;
		}

		if (ROLLED.size() > 512) {
			ROLLED.clear();
		}
	}

	/**
	 * The Wayfarer rule for one projectile at {@code pos} moving {@code v} per tick toward a runner
	 * whose chest is at {@code chest}: null when it is not on course to hit (no roll), {@code v}
	 * itself when the roll {@code roll} keeps it on course, else the bent velocity (same speed,
	 * at most {@code wayfarerMaxDegrees} off).
	 */
	public static @Nullable Vec3 wayfarerPath(Vec3 pos, Vec3 v, Vec3 chest, SpeedBalance b, float roll) {
		double speedSqr = v.lengthSqr();
		Vec3 toChest = chest.subtract(pos);
		double t = toChest.dot(v) / speedSqr;

		if (t < 0.0 || t > WAYFARER_LOOKAHEAD) {
			return null;
		}

		Vec3 closest = pos.add(v.scale(t));
		Vec3 offset = closest.subtract(chest);
		double miss = offset.length();

		if (miss > 0.9) {
			return null;
		}

		if (roll >= b.wayfarerMissChance) {
			return v;
		}

		double speed = Math.sqrt(speedSqr);
		Vec3 dir = v.scale(1.0 / speed);
		Vec3 side = offset.subtract(dir.scale(offset.dot(dir)));

		if (side.lengthSqr() < 1.0e-6) {
			side = dir.cross(new Vec3(0, 1, 0));

			if (side.lengthSqr() < 1.0e-6) {
				side = new Vec3(1, 0, 0);
			}
		}

		side = side.normalize();
		double distance = Math.max(0.5, toChest.length());
		double wanted = Math.atan2(Math.max(0.0, WAYFARER_CLEARANCE - miss), distance);
		double angle = Math.min(wanted, Math.toRadians(b.wayfarerMaxDegrees));
		return dir.scale(Math.cos(angle)).add(side.scale(Math.sin(angle))).scale(speed);
	}

	/** Whether the bent path over the next ticks hits a wall no sooner than the old one did. */
	public static boolean clearAsBefore(ServerLevel level, Projectile projectile, Vec3 before, Vec3 after) {
		Vec3 from = projectile.position();
		double oldDistance = wallDistance(level, projectile, from, before);
		double newDistance = wallDistance(level, projectile, from, after);
		return newDistance + 1.0e-3 >= oldDistance;
	}

	private static double wallDistance(ServerLevel level, Projectile projectile, Vec3 from, Vec3 v) {
		Vec3 to = from.add(v.scale(WAYFARER_LOOKAHEAD));
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
		return hit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : from.distanceTo(hit.getLocation());
	}

	/**
	 * Fabric {@code ALLOW_DAMAGE}: a Wayfarer runner takes no damage from a fall shorter than
	 * {@code wayfarerSafeFall}. The fall distance is still set while the fall damage is dealt.
	 */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		return !(source.is(DamageTypeTags.IS_FALL) && isWayfaring(entity) && entity.fallDistance < balance().wayfarerSafeFall);
	}

	// ---------------------------------------------------------------- lifecycle

	/** Ends the player's run (death, disconnect, change of level) and drops its modifiers. */
	public static void forget(ServerPlayer player, boolean feedback) {
		Run run = RUNS.get(player.getUUID());

		if (run != null) {
			end(run, feedback);
		}

		removeModifiers(player);
	}

	public static void clear() {
		RUNS.clear();
		ROLLED.clear();
	}
}
