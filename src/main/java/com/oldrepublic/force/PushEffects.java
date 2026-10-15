package com.oldrepublic.force;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.combat.DeflectableProjectile;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.Vec3;

/**
 * Applies a push to its targets: impulses, Repulse stagger (with diminishing returns for
 * push-resistant targets), projectile pushes and reflections, and the Focused Blast wall slam.
 * Never modifies blocks.
 */
public final class PushEffects {
	/** A Focused Blast target that may still hit a wall. */
	private static final class SlamWatch {
		final LivingEntity target;
		final UUID casterId;
		final Vec3 direction;
		final float damage;
		final long expiresAt;
		double lastSpeed;

		SlamWatch(LivingEntity target, UUID casterId, Vec3 direction, float damage, long expiresAt, double speed) {
			this.target = target;
			this.casterId = casterId;
			this.direction = direction;
			this.damage = damage;
			this.expiresAt = expiresAt;
			this.lastSpeed = speed;
		}
	}

	/** Stagger history of one push-resistant target. */
	private record StaggerRecord(long windowStart, int count) {
	}

	private static final List<SlamWatch> SLAMS = new ArrayList<>();
	private static final Map<UUID, StaggerRecord> STAGGERS = new HashMap<>();
	private static long ticks;

	/**
	 * Pushes every target and sends the impact events.
	 *
	 * @return the number of distinct hostile living targets affected (for mastery and the quest)
	 */
	static int apply(ServerPlayer caster, PushShape shape, PushBranch branch, float charge, List<PushTargeting.Target> targets) {
		PushBalance b = ForcePush.balance();
		ServerLevel level = caster.level();
		Vec3 eye = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		int hostile = 0;
		int index = 0;

		for (PushTargeting.Target target : targets) {
			Entity entity = target.entity();
			Vec3 direction = pushDirection(branch, eye, look, target.aimPoint());
			float falloff = 1f - b.distanceFalloff * Mth.clamp((float) (target.distance() / Math.max(0.1f, shape.range())), 0f, 1f);
			float strength = shape.knockback() * falloff;

			if (entity instanceof LivingEntity living) {
				strength = pushLiving(caster, living, direction, strength, charge, branch, b);

				if (living instanceof Enemy) {
					hostile++;
				}

				EffectEvents.around(level, ForceEffects.PUSH_IMPACT, facingPoint(living, eye), direction, living.getId(), strength, index++);
			} else if (entity instanceof ItemEntity item) {
				Vec3 impulse = direction.scale(strength * b.itemKnockbackMult).add(0, 0.2, 0);
				item.setDeltaMovement(item.getDeltaMovement().add(impulse));
				item.hurtMarked = true;
			} else if (entity instanceof Projectile projectile) {
				pushProjectile(caster, projectile, direction, branch, b);
				EffectEvents.around(level, ForceEffects.PUSH_IMPACT, projectile.position(), direction, projectile.getId(), strength, ForceEffects.IMPACT_PROJECTILE);
			}
		}

		return hostile;
	}

	/** Cones push mostly along the aim, slightly outward; the pulse pushes radially. */
	static Vec3 pushDirection(PushBranch branch, Vec3 eye, Vec3 look, Vec3 point) {
		Vec3 radial = point.subtract(eye);

		if (radial.lengthSqr() < 1.0e-4) {
			radial = look;
		}

		radial = radial.normalize();

		return switch (branch) {
			case DEFLECTING_PULSE -> new Vec3(radial.x, Math.max(0.0, radial.y), radial.z).normalize();
			case FOCUSED_BLAST -> look;
			default -> look.scale(0.6).add(radial.scale(0.4)).normalize();
		};
	}

	private static float pushLiving(ServerPlayer caster, LivingEntity living, Vec3 direction, float strength, float charge, PushBranch branch, PushBalance b) {
		boolean resistant = living.is(ForceTags.PUSH_RESISTANT); // API-CHECK: Entity#is(TagKey) via TypedInstance
		float s = strength;

		if (resistant) {
			s *= b.resistantKnockbackMult;
		}

		double resistance = Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
		s *= (float) (1.0 - b.knockbackResistanceWeight * resistance);
		float lift = (b.lift + b.liftPerCharge * charge) * (resistant ? b.resistantKnockbackMult : 1f);
		// Never drive a living target into the ground: aiming slightly down at a mob's chest would
		// cancel the lift and leave it skidding a couple of blocks on ground friction.
		Vec3 flat = new Vec3(direction.x, Math.max(0.0, direction.y), direction.z);
		Vec3 along = flat.lengthSqr() < 1.0e-4 ? direction : flat.normalize();
		Vec3 impulse = along.scale(s).add(0, lift, 0);
		living.setDeltaMovement(living.getDeltaMovement().scale(0.3).add(impulse));
		living.hurtMarked = true;

		if (living instanceof Enemy && !(living instanceof Player)) {
			living.setLastHurtByMob(caster);
		}

		if (branch == PushBranch.REPULSE_WAVE) {
			int duration = resistant ? diminishedStagger(living.getUUID(), b.repulseStaggerTicks, b) : b.repulseStaggerTicks;

			if (duration > 0) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, b.repulseStaggerAmplifier), caster);
			}
		}

		if (branch == PushBranch.FOCUSED_BLAST) {
			float damage = b.slamDamageBase + b.slamDamagePerCharge * charge;
			SLAMS.removeIf(w -> w.target == living);
			SLAMS.add(new SlamWatch(living, caster.getUUID(), direction, damage, ticks + b.slamWindowTicks, impulse.horizontalDistance()));
		}

		return s;
	}

	/** Each stagger on a resistant target within the window lasts half as long; the third is ignored. */
	private static int diminishedStagger(UUID id, int baseTicks, PushBalance b) {
		StaggerRecord record = STAGGERS.get(id);

		if (record == null || ticks - record.windowStart() > b.staggerDrWindowTicks) {
			record = new StaggerRecord(ticks, 0);
		}

		int count = record.count();
		STAGGERS.put(id, new StaggerRecord(record.windowStart(), count + 1));
		return count >= 2 ? 0 : baseTicks >> count;
	}

	private static void pushProjectile(ServerPlayer caster, Projectile projectile, Vec3 direction, PushBranch branch, PushBalance b) {
		if (projectile instanceof DeflectableProjectile deflectable) {
			Vec3 velocity = deflectable.deflectableVelocity();
			double speed = Math.max(velocity.length(), b.projectilePushSpeed);
			Vec3 newDirection = direction;

			if (branch == PushBranch.DEFLECTING_PULSE) {
				Entity shooter = deflectable.deflectableShooter();

				if (shooter != null && shooter.isAlive() && shooter != caster) {
					newDirection = shooter.getEyePosition().subtract(projectile.position()).normalize();
				} else if (velocity.lengthSqr() > 1.0e-6) {
					newDirection = velocity.normalize().scale(-1);
				}
			}

			deflectable.redirect(newDirection.scale(speed), caster, false);
			return;
		}

		if (branch == PushBranch.DEFLECTING_PULSE) {
			// API-CHECK: EntityReference.of(Entity) generic inference for Projectile#deflect
			projectile.deflect(ProjectileDeflection.REVERSE, caster, EntityReference.of((Entity) caster), true);
			return;
		}

		double speed = Math.max(projectile.getDeltaMovement().length(), b.projectilePushSpeed);
		projectile.setDeltaMovement(direction.scale(speed));
		projectile.setOwner(caster);
		projectile.hurtMarked = true;
	}

	/** The point on the target's body facing the caster, for the impact burst. */
	private static Vec3 facingPoint(LivingEntity target, Vec3 eye) {
		Vec3 center = target.position().add(0, target.getBbHeight() * 0.55, 0);
		Vec3 toCaster = new Vec3(eye.x - center.x, 0, eye.z - center.z);

		if (toCaster.lengthSqr() < 1.0e-4) {
			return center;
		}

		return center.add(toCaster.normalize().scale(target.getBbWidth() * 0.5 + 0.05));
	}

	/** Watches Focused Blast targets for wall impacts and expires stagger history. */
	static void tick(MinecraftServer server) {
		ticks++;
		Iterator<SlamWatch> it = SLAMS.iterator();

		while (it.hasNext()) {
			SlamWatch watch = it.next();
			LivingEntity target = watch.target;

			if (!target.isAlive() || target.isRemoved() || ticks > watch.expiresAt) {
				it.remove();
				continue;
			}

			if (target.horizontalCollision && watch.lastSpeed >= ForcePush.balance().slamMinSpeed) {
				it.remove();
				slam(server, watch);
				continue;
			}

			watch.lastSpeed = target.getDeltaMovement().horizontalDistance();
		}

		if (ticks % 200 == 0 && !STAGGERS.isEmpty()) {
			int window = ForcePush.balance().staggerDrWindowTicks;
			STAGGERS.values().removeIf(r -> ticks - r.windowStart() > window);
		}
	}

	private static void slam(MinecraftServer server, SlamWatch watch) {
		LivingEntity target = watch.target;

		if (!(target.level() instanceof ServerLevel level)) {
			return;
		}

		ServerPlayer caster = null;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getUUID().equals(watch.casterId)) {
				caster = player;
			}
		}

		DamageSource source = caster != null ? caster.damageSources().playerAttack(caster) : level.damageSources().flyIntoWall();
		target.hurtServer(level, source, watch.damage);
		Vec3 horizontal = new Vec3(watch.direction.x, 0, watch.direction.z);
		Vec3 along = horizontal.lengthSqr() > 1.0e-4 ? horizontal.normalize() : Vec3.ZERO;
		Vec3 contact = target.position().add(0, target.getBbHeight() * 0.5, 0).add(along.scale(target.getBbWidth() * 0.5 + 0.05));
		EffectEvents.around(level, ForceEffects.PUSH_SLAM, contact, along.scale(-1), target.getId(), watch.damage, 0);
	}

	/** Drops all tracked state (server stopping). */
	static void clear() {
		SLAMS.clear();
		STAGGERS.clear();
		ticks = 0;
	}

	private PushEffects() {
	}
}
