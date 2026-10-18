package com.oldrepublic.enemy.trooper;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.force.ForceReactiveEntity;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Imperial grenadier's frag grenade (spec 7.3). It is lobbed in a ballistic arc, lands, and
 * bursts {@link #fuseLength()} ticks after it first touched down: {@code grenadeDamage} within
 * {@code grenadeRadius} (linear falloff to half at the edge), {@code grenadeKnockback}, and never any
 * terrain damage. Until Push or Pull redirects it, it spares its own side the way blaster bolts do;
 * after a redirect it hits everyone, its thrower included. A player's {@code frag_grenade} item
 * (MILESTONE_4.md 7.6) throws the same entity with its own damage and radius.
 *
 * <p>Force Push sends it on along the push at {@code grenadePushSpeed} (the fuse keeps running);
 * Force Pull flings it back in an arc to land at the thrower's feet. Not saved.
 *
 * <p>Effects: {@code grenade_fuse} on the first landing (the fuse tell, entity-bound),
 * {@code grenade_burst} at the burst, {@code grenade_return} on every redirect. The server plays no
 * sound; the client handlers do.
 */
public class FragGrenade extends ThrowableProjectile implements ForceReactiveEntity {
	/** The burst's damage type: {@code oldrepublic:frag_grenade} (in {@code #minecraft:is_explosion}). */
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("frag_grenade"));

	/** Gravity per tick, heavier than a snowball so the lob reads as a weighted grenade. */
	public static final double GRAVITY = 0.04;
	/** Air drag per tick (vanilla throwables use 0.99). */
	public static final float DRAG = 0.99f;
	/** A thrower's aim lands within this many blocks of the target (a square spread, centred). */
	public static final double SPREAD = 1.2;
	/** A pushed grenade climbs at least this much per unit of speed, so a push along the floor sends it flying instead of skidding. */
	public static final double PUSH_MIN_RISE = 0.25;
	/** A grenade still in the air this long starts its fuse anyway (thrown into a ravine, or the void). */
	public static final int MAX_FLIGHT_TICKS = 100;

	/** Bounces off walls and ceilings before it settles wherever it is. */
	private static final int MAX_BOUNCES = 4;
	/** Slower than this after a wall bounce, it simply drops and settles. */
	private static final double SETTLE_SPEED = 0.12;
	/** Ticks during which the entity it just bounced off cannot catch it again. */
	private static final int BOUNCE_GRACE_TICKS = 10;

	/** Resting on the ground (true) or in flight (false). */
	private static final EntityDataAccessor<Boolean> DATA_LANDED = SynchedEntityData.defineId(FragGrenade.class, EntityDataSerializers.BOOLEAN);
	/** The fuse length in ticks, copied from {@code TrooperBalance.grenadeFuseTicks} when thrown. */
	private static final EntityDataAccessor<Integer> DATA_FUSE_LENGTH = SynchedEntityData.defineId(FragGrenade.class, EntityDataSerializers.INT);
	/** Game time of the first landing, or -1 while the fuse has not started. */
	private static final EntityDataAccessor<Long> DATA_FUSE_START = SynchedEntityData.defineId(FragGrenade.class, EntityDataSerializers.LONG);
	/** Pushed or pulled at least once: it now hits everyone. */
	private static final EntityDataAccessor<Boolean> DATA_REDIRECTED = SynchedEntityData.defineId(FragGrenade.class, EntityDataSerializers.BOOLEAN);

	/** Who threw it (server side). Pull returns the grenade to this entity's feet. */
	private @Nullable Entity thrower;
	/** Whether the fuse length was read from the balance (server side, once). */
	private boolean configured;
	/** Ticks in the air since the throw (server side). */
	private int flightTicks;
	/** Wall and ceiling bounces since the last throw or redirect. */
	private int bounces;
	/** The entity it last bounced off, ignored for {@link #BOUNCE_GRACE_TICKS}. */
	private @Nullable Entity bouncedOff;
	private int bounceGrace;
	/** The burst's damage and radius for a player's grenade; negative reads the trooper balance (saved). */
	private float damage = -1f;
	private float radius = -1f;

	public FragGrenade(EntityType<? extends FragGrenade> type, Level level) {
		super(type, level);
	}

	/**
	 * Lobs a grenade from {@code thrower}'s right hand so it lands near {@code target} (within
	 * {@link #SPREAD}), adds it to the level and returns it. Plays nothing: the tell was
	 * {@code grenade_tell}.
	 */
	public static FragGrenade throwAt(ServerLevel level, LivingEntity thrower, Vec3 target) {
		return throwAt(level, thrower, target, -1f, -1f);
	}

	/**
	 * {@link #throwAt(ServerLevel, LivingEntity, Vec3)} with the burst's own {@code damage} and
	 * {@code radius} (a player's grenade, MILESTONE_4.md 7.6); a negative value keeps the trooper's.
	 */
	public static FragGrenade throwAt(ServerLevel level, LivingEntity thrower, Vec3 target, float damage, float radius) {
		FragGrenade grenade = new FragGrenade(EnemyEntities.FRAG_GRENADE, level);
		Vec3 hand = handPosition(thrower);
		RandomSource random = thrower.getRandom();
		Vec3 aim = target.add((random.nextDouble() - 0.5) * SPREAD, 0, (random.nextDouble() - 0.5) * SPREAD);

		grenade.setPos(hand);
		grenade.setOwner(thrower);
		grenade.thrower = thrower;
		grenade.damage = damage;
		grenade.radius = radius;
		grenade.configure();
		Vec3 velocity = lob(hand, aim, lobTicks(horizontal(hand, aim)));
		grenade.setDeltaMovement(velocity);
		grenade.faceAlong(velocity);
		level.addFreshEntity(grenade);
		return grenade;
	}

	/** The thrower's right hand, a little below the eyes. */
	static Vec3 handPosition(LivingEntity thrower) {
		float yaw = thrower.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		return thrower.getEyePosition().add(right.scale(0.35)).add(0, -0.25, 0);
	}

	// ---------------------------------------------------------------- ballistics (pure)

	/** Flight time of a thrown lob over {@code distance} horizontal blocks: 10 to 26 ticks. */
	public static int lobTicks(double distance) {
		return Mth.clamp((int) Math.round(8 + distance), 10, 26);
	}

	/** Flight time of a pulled grenade flung back over {@code distance} horizontal blocks: 6 to 22 ticks. */
	public static int pullTicks(double distance) {
		return Mth.clamp((int) Math.round(5 + 0.9 * distance), 6, 22);
	}

	/**
	 * The launch velocity that carries a grenade from {@code from} to {@code to} in exactly
	 * {@code ticks} ticks of free flight (gravity, then drag, then the move, as
	 * {@link ThrowableProjectile} steps it).
	 */
	public static Vec3 lob(Vec3 from, Vec3 to, int ticks) {
		int t = Math.max(1, ticks);
		double sum = dragSum(t);
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double fall = GRAVITY * DRAG / (1.0 - DRAG) * (t - sum);
		return new Vec3(dx / sum, (dy + fall) / sum, dz / sum);
	}

	/** Where an unobstructed grenade launched from {@code from} at {@code velocity} is after {@code ticks} ticks. */
	public static Vec3 flight(Vec3 from, Vec3 velocity, int ticks) {
		Vec3 pos = from;
		Vec3 v = velocity;

		for (int i = 0; i < ticks; i++) {
			v = v.add(0, -GRAVITY, 0).scale(DRAG);
			pos = pos.add(v);
		}

		return pos;
	}

	/** d + d^2 + ... + d^t for the drag d. */
	private static double dragSum(int t) {
		return DRAG * (1.0 - Math.pow(DRAG, t)) / (1.0 - DRAG);
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = b.x - a.x;
		double dz = b.z - a.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Burst damage at {@code distance} from the centre: full at the centre, half at the edge, none beyond. */
	public static float damageAt(float damage, float radius, double distance) {
		if (radius <= 0f || distance > radius) {
			return 0f;
		}

		return damage * (1f - 0.5f * (float) (distance / radius));
	}

	/**
	 * Whether a grenade thrown by {@code thrower} spares {@code target}: before any redirect it
	 * spares the thrower, its side ({@link Allegiances#friendly}) and players the thrower is
	 * Allied with, as blaster bolts do. A redirected grenade spares nobody. Server side.
	 */
	public static boolean spares(@Nullable Entity thrower, Entity target, boolean redirected) {
		if (redirected || thrower == null) {
			return false;
		}

		if (target == thrower || Allegiances.friendly(thrower, target)) {
			return true;
		}

		return !target.level().isClientSide() && target instanceof Player player && thrower instanceof LivingEntity living
				&& Attitudes.toward(living, player) == Attitude.ALLIED;
	}

	// ---------------------------------------------------------------- state

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_LANDED, false);
		builder.define(DATA_FUSE_LENGTH, 40);
		builder.define(DATA_FUSE_START, -1L);
		builder.define(DATA_REDIRECTED, false);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("BurstDamage", damage);
		output.putFloat("BurstRadius", radius);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.damage = input.getFloatOr("BurstDamage", -1f);
		this.radius = input.getFloatOr("BurstRadius", -1f);
	}

	/** Resting on the ground. Synced. */
	public boolean landed() {
		return this.entityData.get(DATA_LANDED);
	}

	/** The fuse length in ticks, from {@code TrooperBalance.grenadeFuseTicks}. Synced. */
	public int fuseLength() {
		return this.entityData.get(DATA_FUSE_LENGTH);
	}

	/** Ticks since the first landing (0 before it), on both sides. */
	public int fuseTicks() {
		long start = this.entityData.get(DATA_FUSE_START);
		return start < 0 ? 0 : (int) Math.max(0L, level().getGameTime() - start);
	}

	/** Whether the fuse is burning (it has landed at least once). Synced. */
	public boolean fuseStarted() {
		return this.entityData.get(DATA_FUSE_START) >= 0;
	}

	/** Pushed or pulled at least once: it now hits everyone. Synced. */
	public boolean redirected() {
		return this.entityData.get(DATA_REDIRECTED);
	}

	/** Who threw it, while it is alive (server side). */
	public @Nullable Entity thrower() {
		return thrower != null && !thrower.isRemoved() ? thrower : null;
	}

	private void configure() {
		if (!configured) {
			this.entityData.set(DATA_FUSE_LENGTH, Math.max(1, TrooperBalance.get().grenadeFuseTicks));
			configured = true;
		}
	}

	private void setLanded(boolean landed) {
		this.entityData.set(DATA_LANDED, landed);
	}

	@Override
	protected double getDefaultGravity() {
		return GRAVITY;
	}

	@Override
	protected float getAirDrag() {
		return DRAG;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			configure();
		}

		if (landed()) {
			restTick();
		} else {
			super.tick();
		}

		if (bounceGrace > 0 && --bounceGrace == 0) {
			bouncedOff = null;
		}

		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return;
		}

		if (!fuseStarted()) {
			if (++flightTicks > MAX_FLIGHT_TICKS) {
				startFuse(level);
			}

			return;
		}

		if (fuseTicks() >= fuseLength()) {
			burst(level);
		}
	}

	/** Resting: it settles under gravity with block collisions and ground friction, so it rolls off ledges and stops. */
	private void restTick() {
		baseTick();
		Vec3 v = getDeltaMovement();

		if (!isNoGravity()) {
			v = v.add(0, -GRAVITY, 0);
		}

		// API-CHECK: MoverType.SELF (the MoverType enum constants were not dumped; Entity#move is).
		move(MoverType.SELF, v);
		v = getDeltaMovement();
		setDeltaMovement(onGround() ? v.multiply(0.55, 1.0, 0.55) : v.scale(0.98));
	}

	private void startFuse(ServerLevel level) {
		if (fuseStarted()) {
			return;
		}

		this.entityData.set(DATA_FUSE_START, level.getGameTime());
		EffectEvents.toTracking(this, TrooperEffects.GRENADE_FUSE, position(), null, getId(), fuseLength(), 0);
	}

	// ---------------------------------------------------------------- hits

	@Override
	protected boolean canHitEntity(Entity target) {
		if (!super.canHitEntity(target) || target instanceof FragGrenade || target instanceof BlasterBolt || target == bouncedOff) {
			return false;
		}

		return !spares(thrower(), target, redirected());
	}

	/** It knocks against a body and drops at its feet. */
	@Override
	protected void onHitEntity(EntityHitResult result) {
		Vec3 v = getDeltaMovement();
		setDeltaMovement(-v.x * 0.15, Math.min(v.y, 0.0) * 0.3, -v.z * 0.15);
		bouncedOff = result.getEntity();
		bounceGrace = BOUNCE_GRACE_TICKS;
		this.hurtMarked = true;
	}

	/** It lands on a top face; it bounces off walls and ceilings, and settles after a few bounces. */
	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		Direction face = result.getDirection();
		Vec3 v = getDeltaMovement();
		Vec3 at = result.getLocation();

		if (face == Direction.UP) {
			setPos(at.x, at.y + 0.01, at.z);
			land(new Vec3(v.x * 0.25, 0, v.z * 0.25));
			return;
		}

		// API-CHECK: Direction#getStepX/Y/Z (standard, Direction was not dumped).
		Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
		double half = getBbWidth() * 0.5 + 0.02;
		setPos(at.x + normal.x * half, at.y + (face == Direction.DOWN ? -(getBbHeight() + 0.02) : 0), at.z + normal.z * half);
		// Reflect the part of the velocity going into the face, damped; the rest slows.
		double into = v.dot(normal);
		Vec3 bounced = v.subtract(normal.scale(2 * into)).multiply(
				normal.x != 0 ? 0.35 : 0.6, normal.y != 0 ? 0.35 : 0.8, normal.z != 0 ? 0.35 : 0.6);
		bounces++;

		if (bounces >= MAX_BOUNCES || bounced.length() < SETTLE_SPEED) {
			land(bounced.multiply(0.5, 0, 0.5));
		} else {
			setDeltaMovement(bounced);
			this.hurtMarked = true;
		}
	}

	private void land(Vec3 velocity) {
		setDeltaMovement(velocity);
		setLanded(true);
		this.hurtMarked = true;

		if (level() instanceof ServerLevel level) {
			startFuse(level);
		}
	}

	// ---------------------------------------------------------------- burst

	/**
	 * Bursts: damage with linear falloff and knockback to every living body in the radius that it
	 * can see from the centre (and does not spare), then {@code grenade_burst}. Never touches blocks.
	 */
	private void burst(ServerLevel level) {
		TrooperBalance b = TrooperBalance.get();
		float radius = this.radius < 0f ? b.grenadeRadius : this.radius;
		float damage = this.damage < 0f ? b.grenadeDamage : this.damage;
		Vec3 center = position().add(0, 0.2, 0);
		DamageSource source = level.damageSources().source(DAMAGE, this, getOwner());
		Entity spareFor = thrower();
		boolean everyone = redirected();

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius + 1.0),
				e -> e.isAlive() && !e.isSpectator())) {
			AABB box = target.getBoundingBox();
			Vec3 nearest = new Vec3(Mth.clamp(center.x, box.minX, box.maxX), Mth.clamp(center.y, box.minY, box.maxY), Mth.clamp(center.z, box.minZ, box.maxZ));
			double distance = nearest.distanceTo(center);
			float amount = damageAt(damage, radius, distance);

			if (amount <= 0f || spares(spareFor, target, everyone) || !exposed(level, center, target)) {
				continue;
			}

			if (target.hurtServer(level, source, amount)) {
				knockAway(target, center, b.grenadeKnockback);
			}
		}

		EffectEvents.around(level, TrooperEffects.GRENADE_BURST, center, null, -1, radius, 0);
		discard();
	}

	/** Whether the burst centre sees the target's middle or its eyes (walls and floors shield). */
	private boolean exposed(ServerLevel level, Vec3 center, LivingEntity target) {
		Vec3 middle = target.getBoundingBox().getCenter();

		if (level.clip(new ClipContext(center, middle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) {
			return true;
		}

		return level.clip(new ClipContext(center, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
	}

	/** Knocks the target away from the centre, reduced by its knockback resistance, with a small hop. */
	private void knockAway(LivingEntity target, Vec3 center, float strength) {
		double resistance = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
		double s = strength * (1.0 - resistance);

		if (s <= 0.0) {
			return;
		}

		Vec3 away = new Vec3(target.getX() - center.x, 0, target.getZ() - center.z);

		if (away.lengthSqr() < 1.0e-4) {
			float angle = getRandom().nextFloat() * Mth.TWO_PI;
			away = new Vec3(Mth.cos(angle), 0, Mth.sin(angle));
		}

		Vec3 impulse = away.normalize().scale(s).add(0, s * 0.6, 0);
		target.setDeltaMovement(target.getDeltaMovement().multiply(0.5, 0.5, 0.5).add(impulse));
		target.hurtMarked = true;
	}

	// ---------------------------------------------------------------- Force

	/**
	 * Push sends it on along the push at {@code grenadePushSpeed}, lofted to climb at least
	 * {@link #PUSH_MIN_RISE} per unit of speed; it is in flight again and the fuse keeps running.
	 */
	@Override
	public boolean onForcePush(ServerPlayer caster, Vec3 dir, float strength) {
		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return false;
		}

		Vec3 velocity = pushVelocity(dir, TrooperBalance.get().grenadePushSpeed);
		redirect(caster, velocity);
		EffectEvents.around(level, TrooperEffects.GRENADE_RETURN, position(), velocity, getId(), 1f, TrooperEffects.RETURN_PUSH);
		return true;
	}

	/** Pull flings it back in an arc to land at its thrower's feet, or at the caster's if the thrower is gone. */
	@Override
	public boolean onForcePull(ServerPlayer caster, Vec3 dir, float strength) {
		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return false;
		}

		Entity back = thrower();

		if (back == null || !back.isAlive() || back.level() != level) {
			back = caster;
		}

		Vec3 from = position().add(0, 0.05, 0);
		Vec3 to = back.position();
		setPos(from);
		Vec3 velocity = lob(from, to, pullTicks(horizontal(from, to)));
		redirect(caster, velocity);
		EffectEvents.around(level, TrooperEffects.GRENADE_RETURN, position(), velocity, getId(), 1f, TrooperEffects.RETURN_PULL);
		return true;
	}

	/** The pushed velocity: along {@code dir}, lofted to at least {@link #PUSH_MIN_RISE}, at {@code speed}. */
	public static Vec3 pushVelocity(Vec3 dir, float speed) {
		Vec3 unit = dir.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : dir.normalize();

		if (unit.y < PUSH_MIN_RISE) {
			Vec3 flat = new Vec3(unit.x, 0, unit.z);
			double flatLength = flat.length();

			if (flatLength < 1.0e-4) {
				unit = new Vec3(0, 1, 0);
			} else {
				double rise = PUSH_MIN_RISE;
				unit = flat.scale(Math.sqrt(1.0 - rise * rise) / flatLength).add(0, rise, 0);
			}
		}

		return unit.scale(speed);
	}

	private void redirect(ServerPlayer caster, Vec3 velocity) {
		setLanded(false);
		this.entityData.set(DATA_REDIRECTED, true);
		setOwner(caster);
		bounces = 0;
		bouncedOff = null;
		bounceGrace = 0;
		setDeltaMovement(velocity);
		faceAlong(velocity);
		this.needsSync = true;
		this.hurtMarked = true;
	}

	private void faceAlong(Vec3 velocity) {
		setYRot((float) (Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG));
		setXRot((float) (Mth.atan2(velocity.y, velocity.horizontalDistance()) * Mth.RAD_TO_DEG));
		this.yRotO = getYRot();
		this.xRotO = getXRot();
	}
}
