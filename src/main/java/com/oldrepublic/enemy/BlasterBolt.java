package com.oldrepublic.enemy;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.combat.DeflectableProjectile;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Blaster bolt: a fast, gravity-free, glowing projectile. Living targets are first offered to
 * {@link CombatHooks#onProjectileHit} so lightsaber guards can block, deflect or parry it. A
 * redirected bolt belongs to its new owner and can hit the poacher who fired it.
 */
public class BlasterBolt extends ThrowableProjectile implements DeflectableProjectile {
	/** Set once the bolt has been deflected; the client draws it a little hotter. */
	private static final EntityDataAccessor<Boolean> DATA_DEFLECTED = SynchedEntityData.defineId(BlasterBolt.class, EntityDataSerializers.BOOLEAN);
	/** After a redirect the new owner is ignored this long, so the bolt cannot hit its deflector. */
	private static final int OWNER_GRACE_TICKS = 4;

	private float damage = 5;
	private int lifetime = 60;
	/** Ticks since firing or the last redirect. */
	private int life;

	public BlasterBolt(EntityType<? extends BlasterBolt> type, Level level) {
		super(type, level);
	}

	/** Creates a bolt at {@code pos} owned by {@code shooter}; call {@code shoot} and add it to the level. */
	public static BlasterBolt create(Level level, @Nullable Entity shooter, Vec3 pos, float damage, int lifetime) {
		BlasterBolt bolt = new BlasterBolt(EnemyEntities.BLASTER_BOLT, level);
		bolt.setPos(pos);
		bolt.setOwner(shooter);
		bolt.damage = damage;
		bolt.lifetime = lifetime;
		return bolt;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_DEFLECTED, false);
	}

	public boolean isDeflected() {
		return this.entityData.get(DATA_DEFLECTED);
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

		if (!level().isClientSide() && life > lifetime && !isRemoved()) {
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		if (!super.canHitEntity(target) || target instanceof BlasterBolt) {
			return false;
		}

		Entity owner = getOwner();

		if (owner != null && target == owner && life <= OWNER_GRACE_TICKS) {
			return false;
		}

		// No friendly fire inside the camp while a poacher owns the bolt.
		return !(owner instanceof CrystalPoacher && target instanceof CrystalPoacher);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return;
		}

		Entity target = result.getEntity();

		if (target instanceof LivingEntity living) {
			CombatHooks.DeflectResult guard = CombatHooks.onProjectileHit(living, this);

			if (guard != CombatHooks.DeflectResult.NONE) {
				// The guard redirected the bolt (deflect, parry) or absorbed it (block).
				if (guard == CombatHooks.DeflectResult.BLOCKED && !isRemoved()) {
					discard();
				}

				return;
			}
		}

		DamageSource source = level.damageSources().source(EnemyEntities.BLASTER_DAMAGE, this, getOwner());
		target.hurtServer(level, source, damage);

		Vec3 velocity = getDeltaMovement();
		Vec3 normal = velocity.lengthSqr() > 1.0e-6 ? velocity.normalize().reverse() : Vec3.ZERO;
		EffectEvents.around(level, EnemyEffects.BOLT_IMPACT, result.getLocation(), normal, -1, 1f, EnemyEffects.IMPACT_ENTITY);
		discard();
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);

		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return;
		}

		Direction face = result.getDirection();
		// API-CHECK: Direction#getStepX/Y/Z (standard, Direction was not dumped).
		Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
		EffectEvents.around(level, EnemyEffects.BOLT_IMPACT, result.getLocation(), normal, -1, 1f, EnemyEffects.IMPACT_BLOCK);
		discard();
	}

	@Override
	public Vec3 deflectableVelocity() {
		return getDeltaMovement();
	}

	@Override
	public @Nullable Entity deflectableShooter() {
		return getOwner();
	}

	@Override
	public void redirect(Vec3 newVelocity, @Nullable Entity newOwner, boolean parried) {
		setDeltaMovement(newVelocity);
		setOwner(newOwner);
		life = 0;

		double horizontal = newVelocity.horizontalDistance();
		setYRot((float) (Mth.atan2(newVelocity.x, newVelocity.z) * (180.0 / Math.PI)));
		setXRot((float) (Mth.atan2(newVelocity.y, horizontal) * (180.0 / Math.PI)));
		this.yRotO = getYRot();
		this.xRotO = getXRot();

		this.entityData.set(DATA_DEFLECTED, true);
		this.needsSync = true;
		this.hurtMarked = true;
	}

	/** Damage this bolt deals on a clean hit. */
	public float damage() {
		return damage;
	}
}
