package com.oldrepublic.force;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.oldrepublic.combat.DeflectableProjectile;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
 * Applies a push to its targets: impulses (scaled by the other features' {@link ForceReactions},
 * for example a saber guard or the acolyte's shove clash), Repulse stagger (with the common
 * diminishing returns, {@link ControlEffects}), projectile pushes and reflections, and the Focused
 * Blast wall slam. Never modifies blocks.
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

	private static final List<SlamWatch> SLAMS = new ArrayList<>();
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
				strength = pushLiving(caster, living, direction, strength * ForceReactions.onPushHit(caster, living), charge, branch, b);

				if (living instanceof Enemy) {
					hostile++;
				}

				QuestHooks.onAbilityHit(caster, ForcePush.ABILITY_ID, living);

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
			ControlEffects.stagger(caster, living, b.repulseStaggerTicks, b.repulseStaggerAmplifier);
		}

		if (branch == PushBranch.FOCUSED_BLAST) {
			float damage = b.slamDamageBase + b.slamDamagePerCharge * charge;
			SLAMS.removeIf(w -> w.target == living);
			SLAMS.add(new SlamWatch(living, caster.getUUID(), direction, damage, ticks + b.slamWindowTicks, impulse.horizontalDistance()));
		}

		return s;
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

	/** Watches Focused Blast targets for wall impacts. */
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

		DamageSource source = ForceDamage.source(level, ForceDamage.IMPACT, caster);
		target.hurtServer(level, source, watch.damage);
		Vec3 horizontal = new Vec3(watch.direction.x, 0, watch.direction.z);
		Vec3 along = horizontal.lengthSqr() > 1.0e-4 ? horizontal.normalize() : Vec3.ZERO;
		Vec3 contact = target.position().add(0, target.getBbHeight() * 0.5, 0).add(along.scale(target.getBbWidth() * 0.5 + 0.05));
		EffectEvents.around(level, ForceEffects.PUSH_SLAM, contact, along.scale(-1), target.getId(), watch.damage, 0);
	}

	/** Drops all tracked state (server stopping). */
	static void clear() {
		SLAMS.clear();
		ticks = 0;
	}

	private PushEffects() {
	}
}
