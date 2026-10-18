package com.oldrepublic.force.barrier;

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
import com.oldrepublic.ability.EnergyRefunds;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.combat.DeflectableProjectile;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.mandalorian.HunterDamage;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Force Barrier ({@code force_barrier}, spec MILESTONE_4.md 9.2): a channel. A sphere of bent air
 * of radius 2 around the caster's chest (3.5 with Shelter) stops every projectile whose next step
 * enters it: bolts stop and drop for 3 Force Energy each (heavy 10), or fly back at their shooter
 * with Return; arrows and thrown things fall short; rockets burst at the surface. Melee and flame
 * damage to the protected are halved and blasts from outside keep 30%. The caster cannot attack or
 * use items, and sprints only at walking pace. A single hit of 10 or more breaks it; releasing after
 * 40 ticks sends a pulse that throws enemies back.
 *
 * <p>Projectiles are checked once per server tick after every entity has moved, against the segment
 * each will travel next tick, so a bolt never reaches the caster between two checks.
 */
public final class ForceBarrier implements ForceAbility {
	public static final String ID = "force_barrier";
	public static final String SHELTER = "barrier_shelter";
	public static final String RETURN = "barrier_return";
	public static final String GUARDIANS_PATIENCE = "guardians_patience";
	/** Effect event {@code force_barrier}: toTracking; intensity = radius on start; variants below. */
	public static final Identifier EFFECT = OldRepublicMod.id("force_barrier");
	public static final int VARIANT_START = 0;
	public static final int VARIANT_END = 1;
	public static final int VARIANT_PULSE = 2;
	public static final int VARIANT_BREAK = 3;
	/** Effect event {@code force_barrier_impact}: around the point of impact, dir = outward; variants below; intensity 1 heavy. */
	public static final Identifier IMPACT = OldRepublicMod.id("force_barrier_impact");
	public static final int IMPACT_BOLT = 0;
	public static final int IMPACT_RETURN = 1;
	public static final int IMPACT_PROJECTILE = 2;
	public static final int IMPACT_BLAST = 3;
	public static final int IMPACT_MELEE = 4;
	/** Transient MOVEMENT_SPEED modifier while sprinting behind the barrier. */
	public static final Identifier SLOW_MODIFIER = OldRepublicMod.id("force_barrier_slow");
	/** Energy is paid every this many ticks. */
	private static final int PAY_INTERVAL = 10;
	/** How far beyond the sphere projectiles are looked for (the fastest bolt's step and some). */
	private static final double SEARCH_MARGIN = 6.0;

	/** One raised barrier. */
	private static final class Channel {
		final ServerPlayer caster;
		final boolean shelter;
		final boolean returns;
		final float radius;
		final int maxTicks;
		final Set<UUID> handled = new HashSet<>();
		int age;
		int stopped;
		float absorbed;
		boolean breakPending;

		Channel(ServerPlayer caster, boolean shelter, boolean returns, float radius, int maxTicks) {
			this.caster = caster;
			this.shelter = shelter;
			this.returns = returns;
			this.radius = radius;
			this.maxTicks = maxTicks;
		}

		Vec3 center() {
			return ForcePowers.chest(caster);
		}
	}

	/** How a barrier ends. */
	enum End {
		/** Released or run out: the pulse when it held long enough. */
		RELEASED,
		/** Cancelled (slot, screen, another power, out of energy): no pulse. */
		CANCELLED,
		/** Broken by a big hit: the caster staggers. */
		BROKEN,
		/** Gone without feedback (death, disconnect). */
		SILENT
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	/** {@code guardians_patience}: the game time until which a caster's parries restore energy. */
	private static final Map<UUID, Long> PATIENCE = new HashMap<>();

	public static BarrierBalance balance() {
		return BalanceConfig.section("force_barrier", BarrierBalance.class, BarrierBalance::new);
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
			case CANCEL -> end(p, End.CANCELLED);
		}
	}

	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
		end(p, feedback ? End.CANCELLED : End.SILENT);
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return CHANNELS.containsKey(p.getUUID());
	}

	/** Whether {@code player} holds a barrier now (no attacks or item use). */
	public static boolean isChannelling(Player player) {
		return CHANNELS.containsKey(player.getUUID());
	}

	/**
	 * Keys: {@code start_cost}, {@code cost_per_s}, {@code max_channel_s}, {@code cooldown_s},
	 * {@code barrier_radius}, {@code bolt_cost}, {@code heavy_bolt_cost}, {@code melee_pct},
	 * {@code flame_pct}, {@code break_damage}, {@code pulse_min_s}, {@code pulse_radius},
	 * {@code shelter_radius}, {@code return_damage_pct}, {@code patience_energy},
	 * {@code patience_window_s}, {@code mastery_cost_per_s}, {@code mastery_duration_s}.
	 */
	@Override
	public Map<String, Float> info() {
		BarrierBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("start_cost", b.startCost);
		info.put("cost_per_s", b.channelCost);
		info.put("max_channel_s", b.maxTicks / 20f);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("barrier_radius", b.radius);
		info.put("bolt_cost", b.boltCost);
		info.put("heavy_bolt_cost", b.heavyBoltCost);
		info.put("melee_pct", b.meleePct);
		info.put("flame_pct", b.flamePct);
		info.put("break_damage", b.breakDamage);
		info.put("pulse_min_s", b.pulseMinTicks / 20f);
		info.put("pulse_radius", b.pulseRadius);
		info.put("shelter_radius", b.shelterRadius);
		info.put("return_damage_pct", b.returnDamageMult * 100f);
		info.put("patience_energy", b.guardiansPatienceFe);
		info.put("patience_window_s", b.guardiansPatienceTicks / 20f);
		info.put("mastery_cost_per_s", b.masteryCostPerRank);
		info.put("mastery_duration_s", b.masteryTicksPerRank / 20f);
		return info;
	}

	// ---------------------------------------------------------------- rules

	/** Force Energy per second at mastery {@code rank}. */
	public static float costPerSecond(BarrierBalance b, int rank) {
		return Math.max(0f, b.channelCost - b.masteryCostPerRank * Mth.clamp(rank, 0, 3));
	}

	/** The longest hold at mastery {@code rank}. */
	public static int maxTicks(BarrierBalance b, int rank) {
		return b.maxTicks + b.masteryTicksPerRank * Mth.clamp(rank, 0, 3);
	}

	/** Force Energy a stopped bolt costs. */
	public static float boltCost(BarrierBalance b, boolean heavy) {
		return heavy ? b.heavyBoltCost : b.boltCost;
	}

	/** Whether a release after {@code age} ticks sends the pulse. */
	public static boolean pulses(BarrierBalance b, int age) {
		return age >= b.pulseMinTicks;
	}

	/**
	 * Where the path from {@code pos} along {@code v} (one tick) enters the sphere at {@code center}
	 * of radius {@code r}: the entry point, {@code pos} itself when it is already inside and moving
	 * inward, or null when it does not enter during this step.
	 */
	public static @Nullable Vec3 entry(Vec3 pos, Vec3 v, Vec3 center, double r) {
		Vec3 rel = pos.subtract(center);
		double c = rel.lengthSqr() - r * r;

		if (c <= 0.0) {
			return v.dot(rel) < 0.0 ? pos : null;
		}

		double a = v.lengthSqr();

		if (a < 1.0e-8) {
			return null;
		}

		double half = v.dot(rel);
		double disc = half * half - a * c;

		if (half >= 0.0 || disc < 0.0) {
			return null;
		}

		double t = (-half - Math.sqrt(disc)) / a;
		return t >= 0.0 && t <= 1.0 ? pos.add(v.scale(t)) : null;
	}

	/** The damage multiplier the barrier gives a protected creature against {@code source}. */
	public static float damageMultiplier(BarrierBalance b, DamageSource source, Vec3 center, double radius) {
		if (isBlast(source)) {
			Vec3 at = source.getSourcePosition();
			return at == null || at.distanceTo(center) >= radius - 0.25 ? b.blastMult : 1f;
		}

		if (isFlame(source)) {
			return 1f - b.flamePct / 100f;
		}

		return isMelee(source) ? 1f - b.meleePct / 100f : 1f;
	}

	/** Blasts: the explosion tag (which holds the hunters' rockets and Kaast's mines), and those two by key as well. */
	static boolean isBlast(DamageSource source) {
		return source.is(DamageTypeTags.IS_EXPLOSION) || source.is(HunterDamage.ROCKET) || source.is(HunterDamage.CAMP_MINE);
	}

	/** Flame: the fire tag, which the wrist flamer's damage type joins. */
	static boolean isFlame(DamageSource source) {
		return source.is(DamageTypeTags.IS_FIRE) || source.is(HunterDamage.WRIST_FLAMER);
	}

	/** A blow struck by a living attacker in reach (players and mobs), except one that pierces the barrier ({@link BarrierPiercing}). */
	static boolean isMelee(DamageSource source) {
		return source.isDirect() && source.getEntity() instanceof LivingEntity
				&& !(source.getEntity() instanceof BarrierPiercing piercing && piercing.piercesBarrier())
				&& (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO));
	}

	// ---------------------------------------------------------------- start and end

	static void start(ServerPlayer player) {
		if (CHANNELS.containsKey(player.getUUID()) || !ForcePowers.mayStart(player, ID)) {
			return;
		}

		BarrierBalance b = balance();

		if (!Progression.trySpendEnergy(player, b.startCost * ForcePowers.costMultiplier(player, ID))) {
			return;
		}

		ForceData data = Progression.get(player);
		boolean shelter = ForcePowers.hasNode(player, data, SHELTER);
		boolean returns = !shelter && ForcePowers.hasNode(player, data, RETURN);
		float radius = shelter ? b.shelterRadius : b.radius;
		Channel channel = new Channel(player, shelter, returns, radius, maxTicks(b, ForcePowers.masteryRank(player, ID)));
		CHANNELS.put(player.getUUID(), channel);
		EffectEvents.toTracking(player, EFFECT, channel.center(), null, player.getId(), radius, VARIANT_START);
		QuestHooks.onAbilityUsed(player, ID, 0);
	}

	/** Ends {@code player}'s barrier, if any, the given way. */
	public static void end(ServerPlayer player, End how) {
		Channel channel = CHANNELS.get(player.getUUID());

		if (channel != null) {
			finish(channel, how);
		}
	}

	/** Drops the barrier with its end feedback (a net, a hold): no pulse. */
	public static void drop(ServerPlayer player) {
		end(player, End.CANCELLED);
	}

	private static void finish(Channel channel, End how) {
		ServerPlayer caster = channel.caster;
		BarrierBalance b = balance();
		CHANNELS.remove(caster.getUUID());
		setSlowed(caster, false, b);
		Progression.setCooldown(caster, ID, ForcePowers.cooldown(caster, ID, b.cooldownTicks));

		if (ForcePowers.hasNode(caster, Progression.get(caster), GUARDIANS_PATIENCE)) {
			PATIENCE.put(caster.getUUID(), caster.level().getGameTime() + b.guardiansPatienceTicks);
		}

		switch (how) {
			case RELEASED -> {
				if (pulses(b, channel.age)) {
					pulse(channel, b);
					EffectEvents.toTracking(caster, EFFECT, channel.center(), null, caster.getId(), channel.radius, VARIANT_PULSE);
				} else {
					EffectEvents.toTracking(caster, EFFECT, channel.center(), null, caster.getId(), channel.radius, VARIANT_END);
				}
			}
			case CANCELLED -> EffectEvents.toTracking(caster, EFFECT, channel.center(), null, caster.getId(), channel.radius, VARIANT_END);
			case BROKEN -> {
				ControlEffects.stagger(null, caster, b.breakStaggerTicks, 1);
				EffectEvents.toTracking(caster, EFFECT, channel.center(), null, caster.getId(), channel.radius, VARIANT_BREAK);
			}
			default -> {
			}
		}
	}

	/** The pulse: enemies within {@code pulseRadius} are thrown back and staggered. */
	private static void pulse(Channel channel, BarrierBalance b) {
		ServerPlayer caster = channel.caster;
		ServerLevel level = caster.level();
		Vec3 center = channel.center();
		double r = b.pulseRadius;

		for (Entity entity : level.getEntities(caster, new AABB(center, center).inflate(r + 1.0),
				e -> ForcePowers.canAffect(caster, e, b.pvpEffects) && ForcePowers.chest(e).distanceTo(center) <= r + e.getBbWidth() * 0.5)) {
			LivingEntity target = (LivingEntity) entity;

			if (!(target instanceof Player) && !ForcePowers.isHostile(caster, target)) {
				continue;
			}

			Vec3 away = target.position().subtract(caster.position());
			Vec3 flat = new Vec3(away.x, 0, away.z);
			Vec3 dir = flat.lengthSqr() < 1.0e-4 ? caster.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
			float knock = b.pulseKnockback * ForcePowers.knockbackFactor(target, 1f);
			target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(knock)).add(0, 0.25 * knock, 0));
			target.hurtMarked = true;
			ControlEffects.stagger(caster, target, b.pulseStaggerTicks, 1);
			QuestHooks.onAbilityHit(caster, ID, target);
		}
	}

	// ---------------------------------------------------------------- ticking

	/** Advances every barrier; called every server tick, after every entity moved. */
	public static void tick() {
		if (CHANNELS.isEmpty()) {
			return;
		}

		BarrierBalance b = balance();

		for (Channel channel : new ArrayList<>(CHANNELS.values())) {
			tickChannel(channel, b);
		}
	}

	private static void tickChannel(Channel channel, BarrierBalance b) {
		ServerPlayer caster = channel.caster;

		if (!caster.isAlive() || caster.isRemoved()) {
			finish(channel, End.SILENT);
			return;
		}

		channel.age++;

		if (channel.age % PAY_INTERVAL == 0) {
			float cost = costPerSecond(b, ForcePowers.masteryRank(caster, ID)) * PAY_INTERVAL / 20f * ForcePowers.costMultiplier(caster, ID);

			if (!Progression.trySpendEnergy(caster, cost)) {
				finish(channel, End.CANCELLED);
				return;
			}
		}

		if (channel.age >= channel.maxTicks) {
			finish(channel, End.RELEASED);
			return;
		}

		setSlowed(caster, caster.isSprinting(), b);
		intercept(channel, b);
	}

	private static void setSlowed(ServerPlayer player, boolean slowed, BarrierBalance b) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed == null) {
			return;
		}

		if (slowed) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_MODIFIER, b.sprintMoveMult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			speed.removeModifier(SLOW_MODIFIER);
		}
	}

	/** Stops every projectile whose next step enters the sphere; one thrown from inside it never enters and flies on. */
	private static void intercept(Channel channel, BarrierBalance b) {
		ServerPlayer caster = channel.caster;
		Vec3 center = channel.center();
		double r = channel.radius;

		for (Projectile projectile : caster.level().getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(r + SEARCH_MARGIN),
				p -> p.isAlive() && !p.isRemoved())) {
			if (channel.handled.contains(projectile.getUUID())) {
				continue;
			}

			Entity owner = projectile.getOwner();

			if (owner != null && protects(channel, owner)) {
				continue;
			}

			if (owner != null && projectile.position().distanceToSqr(center) <= r * r && owner.getBoundingBox().getCenter().distanceToSqr(center) <= r * r) {
				// Thrown from inside the sphere: it never crosses the surface, and only what enters is stopped (9.2).
				continue;
			}

			Vec3 point = entry(projectile.position(), projectile.getDeltaMovement(), center, r);

			if (point == null) {
				continue;
			}

			channel.handled.add(projectile.getUUID());

			if (!stop(channel, projectile, point, center, b)) {
				return;
			}
		}

		if (channel.handled.size() > 256) {
			channel.handled.clear();
		}
	}

	/** Stops one projectile at {@code point}. False when the barrier failed (out of energy) and ended. */
	private static boolean stop(Channel channel, Projectile projectile, Vec3 point, Vec3 center, BarrierBalance b) {
		ServerPlayer caster = channel.caster;
		ServerLevel level = caster.level();
		Vec3 outward = point.subtract(center);
		outward = outward.lengthSqr() < 1.0e-6 ? caster.getLookAngle() : outward.normalize();

		if (projectile instanceof BarrierReactive reactive) {
			reactive.onBarrier(caster, point, outward);
			EffectEvents.around(level, IMPACT, point, outward, caster.getId(), 1f, IMPACT_BLAST);
			countStopped(channel, b);
			return true;
		}

		if (projectile instanceof DeflectableProjectile deflectable && deflectable.saberDeflectable()) {
			boolean heavy = projectile instanceof BlasterBolt bolt && bolt.heavy();

			if (!Progression.trySpendEnergy(caster, boltCost(b, heavy) * ForcePowers.costMultiplier(caster, ID))) {
				// Drained: the barrier falls and this bolt goes on.
				finish(channel, End.CANCELLED);
				return false;
			}

			Entity shooter = deflectable.deflectableShooter();

			if (channel.returns && !heavy && shooter instanceof LivingEntity living && living.isAlive() && shooter.level() == level) {
				Vec3 back = ForcePowers.chest(living).subtract(point);
				double speed = Math.max(1.0, deflectable.deflectableVelocity().length());
				projectile.setPos(point);

				if (projectile instanceof BlasterBolt bolt) {
					bolt.redirect(back.normalize().scale(speed), caster, false, b.returnDamageMult);
				} else {
					deflectable.redirect(back.normalize().scale(speed), caster, false);
				}

				EffectEvents.around(level, IMPACT, point, outward, caster.getId(), 0.5f, IMPACT_RETURN);
			} else {
				projectile.discard();
				EffectEvents.around(level, IMPACT, point, outward, caster.getId(), heavy ? 1f : 0.5f, IMPACT_BOLT);
			}

			countStopped(channel, b);
			return true;
		}

		if (projectile instanceof AbstractHurtingProjectile) {
			// Fireballs and the like: smothered at the surface.
			projectile.discard();
		} else {
			// Arrows and thrown things lose their way and fall just outside.
			projectile.setPos(point.add(outward.scale(0.15)));
			projectile.setDeltaMovement(outward.scale(0.12).add(0, -0.05, 0));
			projectile.hurtMarked = true;
		}

		EffectEvents.around(level, IMPACT, point, outward, caster.getId(), 0.5f, IMPACT_PROJECTILE);
		countStopped(channel, b);
		return true;
	}

	private static void countStopped(Channel channel, BarrierBalance b) {
		channel.stopped++;

		if (b.masteryProjectiles > 0 && channel.stopped % b.masteryProjectiles == 0) {
			ForcePowers.addMastery(channel.caster, ID, 1);
		}
	}

	/** Whether {@code channel} shields {@code entity}: its caster, or (Shelter) an ally or non-hostile creature inside. */
	private static boolean protects(Channel channel, Entity entity) {
		if (entity == channel.caster) {
			return true;
		}

		if (!channel.shelter || !(entity instanceof LivingEntity living) || !living.isAlive()
				|| ForcePowers.chest(living).distanceTo(channel.center()) > channel.radius) {
			return false;
		}

		if (living instanceof Player other) {
			return !channel.caster.level().isPvpAllowed() || channel.caster.isAlliedTo(other);
		}

		return !ForcePowers.isHostile(channel.caster, living);
	}

	private static @Nullable Channel protecting(LivingEntity victim) {
		Channel own = CHANNELS.get(victim.getUUID());

		if (own != null && own.caster == victim) {
			return own;
		}

		for (Channel channel : CHANNELS.values()) {
			if (channel.shelter && channel.caster.level() == victim.level() && protects(channel, victim)) {
				return channel;
			}
		}

		return null;
	}

	/**
	 * The barrier's {@code DamageModifier}: melee and flame halved and outside blasts cut for the
	 * protected; notes a big hit on the caster so the barrier breaks once the hit lands.
	 */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (CHANNELS.isEmpty()) {
			return amount;
		}

		Channel channel = protecting(victim);

		if (channel == null) {
			return amount;
		}

		BarrierBalance b = balance();

		if (victim == channel.caster && amount >= b.breakDamage) {
			channel.breakPending = true;
		}

		float mult = damageMultiplier(b, source, channel.center(), channel.radius);

		if (mult < 1f && isMelee(source)) {
			absorbMelee(channel, amount * (1f - mult), b);
			EffectEvents.around(channel.caster.level(), IMPACT, ForcePowers.chest(victim), null, channel.caster.getId(), 0.5f, IMPACT_MELEE);
		}

		return amount * mult;
	}

	private static void absorbMelee(Channel channel, float absorbed, BarrierBalance b) {
		float before = channel.absorbed;
		channel.absorbed += absorbed;

		if (b.masteryMeleeAbsorbed > 0f) {
			int points = (int) (channel.absorbed / b.masteryMeleeAbsorbed) - (int) (before / b.masteryMeleeAbsorbed);
			ForcePowers.addMastery(channel.caster, ID, points);
		}
	}

	/** Fabric {@code AFTER_DAMAGE}: a big hit noted by {@link #modifyDamage} breaks the caster's barrier. */
	public static void onDamaged(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		Channel channel = CHANNELS.get(player.getUUID());

		if (channel != null && channel.breakPending) {
			finish(channel, End.BROKEN);
		}
	}

	/** {@code guardians_patience}: a parry soon after a barrier ended restores energy. */
	public static void onParry(ServerPlayer defender) {
		Long until = PATIENCE.get(defender.getUUID());

		if (until == null) {
			return;
		}

		long now = defender.level().getGameTime();

		if (now > until) {
			PATIENCE.remove(defender.getUUID());
			return;
		}

		EnergyRefunds.forParry(defender, now, balance().guardiansPatienceFe);
	}

	// ---------------------------------------------------------------- lifecycle

	/** Drops the player's barrier and patience window (death, disconnect, change of level). */
	public static void forget(ServerPlayer player, boolean feedback) {
		end(player, feedback ? End.CANCELLED : End.SILENT);
		PATIENCE.remove(player.getUUID());
		setSlowed(player, false, balance());
	}

	public static void clear() {
		CHANNELS.clear();
		PATIENCE.clear();
	}
}
