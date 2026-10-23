package com.oldrepublic.enemy;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.combat.DeflectableProjectile;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.equipment.blaster.PlayerBolts;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Blaster bolt: a fast, gravity-free, glowing projectile. Living targets are first offered to
 * {@link CombatHooks#onProjectileHit} so lightsaber guards can block, deflect or parry it. A
 * redirected bolt belongs to its new owner and can hit the one who fired it. A bolt never hits
 * anything on its owner's side ({@link Allegiances#friendly}) or a player its owner is Allied with.
 */
public class BlasterBolt extends ThrowableProjectile implements DeflectableProjectile {
	/** Who fired the bolt, which sets its colour (spec 7.1). Synced by ordinal: append only. */
	public enum Style {
		/** Orange "scavenged cells": poacher crews. */
		POACHER,
		/** Blue-white: Republic troopers. */
		REPUBLIC,
		/** Crimson: Imperial troopers. */
		IMPERIAL,
		/** Amber-white: the Ruin Sentinel's lance. */
		SENTINEL,
		/** Green-white: the player's blasters (spec MILESTONE_4.md 5.1). */
		PLAYER,
		/** Gold: Mandalorian hunters and Torvin Kaast. */
		MANDALORIAN;

		static Style byOrdinal(int ordinal) {
			Style[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : POACHER;
		}
	}

	/** Set once the bolt has been deflected; the client draws it a little hotter. */
	private static final EntityDataAccessor<Boolean> DATA_DEFLECTED = SynchedEntityData.defineId(BlasterBolt.class, EntityDataSerializers.BOOLEAN);
	/** {@link Style} ordinal, synced so the first client frame already has the right colour. */
	private static final EntityDataAccessor<Integer> DATA_STYLE = SynchedEntityData.defineId(BlasterBolt.class, EntityDataSerializers.INT);
	/** A heavy bolt (the sentinel lance), synced so the client can draw it heavier. */
	private static final EntityDataAccessor<Boolean> DATA_HEAVY = SynchedEntityData.defineId(BlasterBolt.class, EntityDataSerializers.BOOLEAN);
	/** After a redirect the new owner is ignored this long, so the bolt cannot hit its deflector. */
	private static final int OWNER_GRACE_TICKS = 4;

	private float damage = 5;
	private int lifetime = 60;
	/** Ticks since firing or the last redirect. */
	private int life;
	/** Whether the last redirect was a parry (server side). */
	private boolean parried;

	public BlasterBolt(EntityType<? extends BlasterBolt> type, Level level) {
		super(type, level);
	}

	/** Creates a {@link Style#POACHER} bolt at {@code pos} owned by {@code shooter}; call {@code shoot} and add it to the level. */
	public static BlasterBolt create(Level level, @Nullable Entity shooter, Vec3 pos, float damage, int lifetime) {
		return create(level, shooter, pos, damage, lifetime, Style.POACHER, false);
	}

	/**
	 * Creates a bolt at {@code pos} (the muzzle) owned by {@code owner}; the caller then calls
	 * {@code shoot} and adds it to the level, exactly as with the five-argument form.
	 *
	 * @param heavy a guard cannot deflect it (it drains 35 stamina instead) and only a parry returns it
	 */
	public static BlasterBolt create(Level level, @Nullable Entity owner, Vec3 pos, float damage, int lifeTicks, Style style, boolean heavy) {
		BlasterBolt bolt = new BlasterBolt(EnemyEntities.BLASTER_BOLT, level);
		bolt.setPos(pos);
		bolt.setOwner(owner);
		bolt.damage = damage;
		bolt.lifetime = lifeTicks;
		bolt.entityData.set(DATA_STYLE, style.ordinal());
		bolt.entityData.set(DATA_HEAVY, heavy);
		return bolt;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_DEFLECTED, false);
		builder.define(DATA_STYLE, Style.POACHER.ordinal());
		builder.define(DATA_HEAVY, false);
	}

	public boolean isDeflected() {
		return this.entityData.get(DATA_DEFLECTED);
	}

	/** Who fired it: the bolt's colour. Synced. */
	public Style style() {
		return Style.byOrdinal(this.entityData.get(DATA_STYLE));
	}

	/** A heavy bolt: guard drain 35, no deflection, only a parry returns it. Synced. */
	public boolean heavy() {
		return this.entityData.get(DATA_HEAVY);
	}

	/** Whether the last redirect was a parry. Server side. */
	public boolean parried() {
		return parried;
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

		if (owner == null) {
			return true;
		}

		if (target == owner && life <= OWNER_GRACE_TICKS) {
			return false;
		}

		// No friendly fire within a side (a poacher crew, a trooper army, the Archive), the shooter included.
		if (Allegiances.friendly(owner, target)) {
			return false;
		}

		// A player's bolt passes through the shooter's allies, pets and team (MILESTONE_4.md 5.1). Server state.
		if (!level().isClientSide() && style() == Style.PLAYER && owner instanceof Player shooter
				&& PlayerBolts.passesThrough(shooter, target)) {
			return false;
		}

		// An Allied trooper's bolts pass through the player it defends. Attitudes are server state,
		// so the client never predicts this.
		boolean alliedPlayer = !level().isClientSide() && target instanceof Player player
				&& owner instanceof LivingEntity shooter && Attitudes.toward(shooter, player) == Attitude.ALLIED;
		return !alliedPlayer;
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
		redirect(newVelocity, newOwner, parried, 1f);
	}

	/**
	 * Sends the bolt off with {@code velocity}, now owned by {@code newOwner} so it can hurt its
	 * original shooter, with its damage scaled by {@code damageMultiplier} (a returned sentinel lance
	 * hits harder). {@code parried} marks a perfectly timed return ({@link #parried}). A trooper whose
	 * bolt this was is told, so a rifleman can answer repeated deflections (spec 7.1).
	 */
	public void redirect(Vec3 velocity, @Nullable Entity newOwner, boolean parried, float damageMultiplier) {
		if (!level().isClientSide() && getOwner() instanceof TrooperEntity shooter && shooter != newOwner) {
			shooter.onBoltDeflected();
		}

		setDeltaMovement(velocity);
		setOwner(newOwner);
		life = 0;
		this.parried = parried;
		damage *= damageMultiplier;

		double horizontal = velocity.horizontalDistance();
		setYRot((float) (Mth.atan2(velocity.x, velocity.z) * (180.0 / Math.PI)));
		setXRot((float) (Mth.atan2(velocity.y, horizontal) * (180.0 / Math.PI)));
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
