package com.oldrepublic.enemy.mandalorian;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.combat.DeflectableProjectile;
import com.oldrepublic.enemy.trooper.FragGrenade;
import com.oldrepublic.force.ForceReactiveEntity;
import com.oldrepublic.force.barrier.BarrierReactive;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.GuardDrainSource;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The hunters' (and Kaast's) wrist rocket (spec MILESTONE_4.md 10.1): slow, heavy, gravity-free, and
 * bursting in {@code rocketRadius} blocks for {@code rocketDamage} (half at the edge), with no
 * terrain damage. A lightsaber cannot deflect it ({@link #saberDeflectable()} false): a guard only
 * blocks it, and it bursts at the blade. Force Push sends it back along the push, now its caster's;
 * a Force Barrier stops it with a burst at the barrier's surface ({@link BarrierReactive}). Never saved.
 *
 * <p>The client draws a small smoking rocket with an engine flare; the burst is the
 * {@code hunter_attack} rocket event.
 */
public class HunterRocket extends ThrowableProjectile implements DeflectableProjectile, ForceReactiveEntity, BarrierReactive, GuardDrainSource {
	/** True once pushed back: the client draws the flare hotter, and the burst spares nobody. */
	private static final EntityDataAccessor<Boolean> DATA_RETURNED = SynchedEntityData.defineId(HunterRocket.class, EntityDataSerializers.BOOLEAN);
	/** After a push, the caster is ignored this long so the rocket cannot burst on them at once. */
	private static final int OWNER_GRACE_TICKS = 6;
	/** A blocked rocket drains this much guard stamina. */
	private static final float GUARD_DRAIN = 30f;

	private float damage = 7f;
	private float radius = 2f;
	private int lifetime = 100;
	private int life;
	private boolean burst;

	public HunterRocket(EntityType<? extends HunterRocket> type, Level level) {
		super(type, level);
	}

	/** A rocket from {@code shooter}'s wrist at {@code pos}, flying along {@code dir}; added to the level. */
	public static HunterRocket launch(ServerLevel level, LivingEntity shooter, Vec3 pos, Vec3 dir, float speed, float damage, float radius, int lifetime) {
		HunterRocket rocket = new HunterRocket(HunterEntities.HUNTER_ROCKET, level);
		rocket.setPos(pos);
		rocket.setOwner(shooter);
		rocket.damage = damage;
		rocket.radius = radius;
		rocket.lifetime = lifetime;
		rocket.fly(dir.normalize().scale(speed));
		level.addFreshEntity(rocket);
		level.playSound(null, pos.x, pos.y, pos.z, ModSounds.MANDALORIAN_ROCKET, SoundSource.HOSTILE, 1f, 0.95f + level.getRandom().nextFloat() * 0.1f);
		return rocket;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_RETURNED, false);
	}

	public boolean returned() {
		return this.entityData.get(DATA_RETURNED);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	protected float getAirDrag() {
		return 1.0f;
	}

	@Override
	public void tick() {
		super.tick();
		life++;

		if (level() instanceof ServerLevel level && !isRemoved() && life > lifetime) {
			explode(level, position());
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		if (!super.canHitEntity(target) || target instanceof HunterRocket) {
			return false;
		}

		Entity owner = getOwner();
		return owner == null || target != owner || life > OWNER_GRACE_TICKS && returned();
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(level() instanceof ServerLevel level) || isRemoved() || burst) {
			return;
		}

		if (result.getEntity() instanceof LivingEntity living && CombatHooks.onProjectileHit(living, this) != CombatHooks.DeflectResult.NONE) {
			// A saber guard blocked it: it bursts against the blade.
			explode(level, position());
			return;
		}

		explode(level, result.getLocation());
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);

		if (level() instanceof ServerLevel level && !isRemoved() && !burst) {
			explode(level, result.getLocation());
		}
	}

	/**
	 * The burst at {@code at}: {@code damage} falling to half at {@code radius}, a shove away from the
	 * centre, no terrain damage. Before a push it spares its shooter's side, as bolts do.
	 */
	public void explode(ServerLevel level, Vec3 at) {
		if (burst) {
			return;
		}

		burst = true;
		setPos(at);
		Entity owner = getOwner();
		DamageSource source = level.damageSources().source(HunterDamage.ROCKET, this, owner);

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1.0), LivingEntity::isAlive)) {
			double distance = target.getBoundingBox().getCenter().distanceTo(at);
			float amount = FragGrenade.damageAt(damage, radius, Math.max(0.0, distance - target.getBbWidth() * 0.5));

			if (amount <= 0f || FragGrenade.spares(owner, target, returned())) {
				continue;
			}

			target.hurtServer(level, source, amount);
			Vec3 away = target.position().subtract(at).multiply(1, 0, 1);
			Vec3 push = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize().scale(0.5);
			target.push(push.x, 0.25, push.z);
			target.hurtMarked = true;
		}

		EffectEvents.around(level, HunterEffects.ATTACK, at, null, getId(), radius, HunterEffects.ATTACK_ROCKET);
		discard();
	}

	// ---------------------------------------------------------------- answers

	@Override
	public Vec3 deflectableVelocity() {
		return getDeltaMovement();
	}

	@Override
	public @Nullable Entity deflectableShooter() {
		return getOwner();
	}

	/** Never redirected by a saber: a guard only blocks it and it bursts there. */
	@Override
	public void redirect(Vec3 newVelocity, @Nullable Entity newOwner, boolean parried) {
	}

	@Override
	public boolean saberDeflectable() {
		return false;
	}

	@Override
	public float guardDrain() {
		return GUARD_DRAIN;
	}

	/** Push sends it back along the push, now the caster's, at the rocket's return speed. */
	@Override
	public boolean onForcePush(ServerPlayer caster, Vec3 dir, float strength) {
		if (burst) {
			return true;
		}

		setOwner(caster);
		life = 0;
		lifetime = Math.max(lifetime, 60);
		this.entityData.set(DATA_RETURNED, true);
		fly(dir.normalize().scale(HunterBalance.get().rocketPushSpeed));
		return true;
	}

	/** A barrier stops it with a burst at its surface. */
	@Override
	public void onBarrier(ServerPlayer caster, Vec3 surface, Vec3 outward) {
		if (level() instanceof ServerLevel level) {
			explode(level, surface.add(outward.scale(0.2)));
		}
	}

	private void fly(Vec3 velocity) {
		setDeltaMovement(velocity);
		double horizontal = velocity.horizontalDistance();
		setYRot((float) (Mth.atan2(velocity.x, velocity.z) * (180.0 / Math.PI)));
		setXRot((float) (Mth.atan2(velocity.y, horizontal) * (180.0 / Math.PI)));
		this.yRotO = getYRot();
		this.xRotO = getXRot();
		this.hurtMarked = true;
	}
}
