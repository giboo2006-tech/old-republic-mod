package com.oldrepublic.space;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ulvaine's blockade gunship over Tyrvann (spec MILESTONE_5.md 5.5, 5.6, 6.5): a slab-sided hull 24
 * blocks long that holds a slow orbit around its post. Four turrets on its back fire at the players'
 * ships and their allies within {@code space.gunship.turretRange}; while the shield generator on its
 * spine ({@link GunshipGenerator}) stands, it takes no damage and launches Hymn interceptors from
 * its belly bay. When it dies, three blasts run along its spine over 40 ticks, then it breaks in two
 * and is gone.
 *
 * <p>Its entity box is a square as wide as it is long, so ship weapons test the hull's own turned
 * box ({@link #hullHit}): bolts and missiles never use the vanilla sweep on it. With
 * {@link #setPassive} (the Peaceful scripted pass, 6.5) it neither fires nor launches.
 */
public class BlockadeGunship extends Mob {
	private static final EntityDataAccessor<Boolean> DATA_SHIELDED = SynchedEntityData.defineId(BlockadeGunship.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_BAY_OPEN = SynchedEntityData.defineId(BlockadeGunship.class, EntityDataSerializers.BOOLEAN);

	/** The hull in its own frame: half its width (right), its height (up from its position), half its length (forward). */
	public static final double HALF_WIDTH = 4.5;
	public static final double HEIGHT = 7.0;
	public static final double HALF_LENGTH = 12.0;
	/**
	 * How far around a point an entity query must reach to find a gunship by its hull: entities are
	 * filed by their position, and the hull's corners reach 12.8 blocks from it.
	 */
	public static final double REACH = 15.0;
	/** The four turrets on its back and the shield generator's seat on its spine (right, up, forward). */
	static final double[][] TURRETS = {{3.2, 7.4, 8.0}, {-3.2, 7.4, 8.0}, {3.2, 7.4, -7.5}, {-3.2, 7.4, -7.5}};
	static final double[] GENERATOR_SEAT = {0, HEIGHT, -2.0};
	/** The launch bay in its belly. */
	private static final double[] BAY = {0, -0.6, 3.0};
	/** The bay stands open this long before the fighters drop out. */
	private static final int BAY_OPEN_TICKS = 30;
	/** The first launch comes this long after it appears; a full sky delays the next one this long. */
	private static final int FIRST_LAUNCH_DELAY = 240;
	private static final int FULL_SKY_RETRY = 100;
	/** Its turn rate in degrees per tick. */
	private static final float TURN = 1.2f;
	/** The death: blasts at these ticks along the spine (forward offsets), the end at {@link #END_TICK}, gone at {@link #GONE_TICK}. */
	private static final int[] BLAST_TICKS = {1, 14, 27};
	private static final double[] BLAST_FORWARD = {7.0, -6.0, 1.0};
	public static final int END_TICK = 40;
	public static final int GONE_TICK = 80;

	private boolean setUp;
	private boolean passive;
	private @Nullable Vec3 post;
	private double orbitAngle;
	private int orbitDirection = 1;
	private Vec3 heading = new Vec3(0, 0, 1);
	private @Nullable UUID generator;
	/** Its generator's health last tick (a generator lost with its chunk comes back with it); -1 before the first. */
	private float generatorHealth = -1f;
	private boolean generatorDestroyed;
	private long nextLaunch = Long.MIN_VALUE;
	private long bayOpenedAt = Long.MIN_VALUE;
	private final long[] nextTurretShot = new long[TURRETS.length];
	/** Client: how far the bay doors stand open (0 to 1), easing toward the synced flag. */
	private float bay;
	private float bayO;

	public BlockadeGunship(EntityType<? extends BlockadeGunship> type, Level level) {
		super(type, level);
		setNoGravity(true);
		this.noPhysics = true;
		this.xpReward = 0;
		this.lookControl = new Starfighter.FixedLook(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 400.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SHIELDED, true);
		builder.define(DATA_BAY_OPEN, false);
	}

	// ---------------------------------------------------------------- setting up

	/** Its hull from the balance, at full health. Called before it is added; a gunship from a command sets itself up on its first tick. */
	public void setup(SpaceBalance b) {
		setUp = true;
		AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);

		if (health != null) {
			health.setBaseValue(Math.max(1f, b.gunship.hull));
		}

		setHealth(getMaxHealth());
	}

	/** The point its slow orbit circles; it joins the circle where it is now. */
	public void setPost(Vec3 centre) {
		this.post = centre;
		this.orbitAngle = Math.atan2(getZ() - centre.z, getX() - centre.x);
	}

	/** A passive gunship (the Peaceful pass) holds its fire and its fighters. */
	public void setPassive(boolean passive) {
		this.passive = passive;
	}

	public boolean passive() {
		return passive;
	}

	/** Whether its shield generator still stands (synced, for the client). */
	public boolean shielded() {
		return entityData.get(DATA_SHIELDED);
	}

	/** Whether its launch bay stands open (synced, for the doors). */
	public boolean bayOpen() {
		return entityData.get(DATA_BAY_OPEN);
	}

	/** Its shield generator, if it stands. Server side. */
	public @Nullable GunshipGenerator generator(ServerLevel level) {
		return generator != null && level.getEntity(generator) instanceof GunshipGenerator g && g.isAlive() ? g : null;
	}

	/** Its heading (a unit vector, level). */
	public Vec3 heading() {
		return heading;
	}

	// ---------------------------------------------------------------- the hull

	/** {@code p} in the hull's frame: (right, up, forward) from its position. */
	Vec3 toLocal(Vec3 p) {
		double rad = Math.toRadians(getYRot());
		double sin = Math.sin(rad);
		double cos = Math.cos(rad);
		Vec3 d = p.subtract(position());
		// The hull faces (-sin, 0, cos) and its right-hand side is (-cos, 0, -sin), as ShipBody.local.
		return new Vec3(-d.x * cos - d.z * sin, d.y, -d.x * sin + d.z * cos);
	}

	/** A point of the hull's frame (right, up, forward) in the world. */
	public Vec3 toWorld(double right, double up, double forward) {
		return position().add(ShipBody.local(right, up, forward, getYRot()));
	}

	/** Where the segment from {@code from} to {@code to} first enters the hull, or null when it misses (or starts inside). */
	public @Nullable Vec3 hullHit(Vec3 from, Vec3 to) {
		Vec3 a = toLocal(from);
		Vec3 b = toLocal(to);
		return AABB.clip(-HALF_WIDTH, 0, -HALF_LENGTH, HALF_WIDTH, HEIGHT, HALF_LENGTH, a, b).map(l -> toWorld(l.x, l.y, l.z)).orElse(null);
	}

	/** How far {@code p} lies from the hull (0 inside it). */
	public double hullDistance(Vec3 p) {
		Vec3 l = toLocal(p);
		double dx = Math.max(0, Math.abs(l.x) - HALF_WIDTH);
		double dy = l.y < 0 ? -l.y : Math.max(0, l.y - HEIGHT);
		double dz = Math.max(0, Math.abs(l.z) - HALF_LENGTH);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** A gunship's hull struck at a point. */
	public record HullHit(BlockadeGunship gunship, Vec3 at) {
	}

	/** The nearest gunship hull the segment from {@code from} to {@code to} enters, among those {@code filter} accepts; null when none. */
	public static @Nullable HullHit firstHullHit(ServerLevel level, Vec3 from, Vec3 to, Predicate<BlockadeGunship> filter) {
		AABB sweep = new AABB(from, to).inflate(REACH);
		HullHit best = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockadeGunship gunship : level.getEntitiesOfClass(BlockadeGunship.class, sweep, g -> g.isAlive() && filter.test(g))) {
			Vec3 at = gunship.hullHit(from, to);

			if (at != null && from.distanceToSqr(at) < bestDistance) {
				best = new HullHit(gunship, at);
				bestDistance = from.distanceToSqr(at);
			}
		}

		return best;
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			bayO = bay;
			bay = Mth.approach(bay, bayOpen() ? 1f : 0f, 0.08f);
		}
	}

	/** Client: how far the bay doors stand open at {@code partialTick}, 0 to 1. */
	public float bay(float partialTick) {
		return Mth.lerp(partialTick, bayO, bay);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		SpaceBalance b = SpaceBalance.get();
		long now = level.getGameTime();

		if (!setUp) {
			setup(b);
		}

		if (nextLaunch == Long.MIN_VALUE) {
			nextLaunch = now + FIRST_LAUNCH_DELAY;

			for (int i = 0; i < nextTurretShot.length; i++) {
				// The four turrets take turns, so their fire comes as a steady patter.
				nextTurretShot[i] = now + 20 + i * turretInterval(b) / TURRETS.length;
			}
		}

		keepGenerator(level, b);
		fly(b);

		if (!passive) {
			turrets(level, b, now);
			launches(level, b, now, !generatorDestroyed);
		}
	}

	/**
	 * Its generator, placed on the spine: spawned the first time, and again with the health it had if
	 * it went with its chunk; null once it is destroyed.
	 */
	private @Nullable GunshipGenerator keepGenerator(ServerLevel level, SpaceBalance b) {
		if (generatorDestroyed) {
			return null;
		}

		GunshipGenerator g = generator(level);

		if (g == null) {
			g = SpaceEntities.GUNSHIP_GENERATOR.create(level, EntitySpawnReason.EVENT);

			if (g == null) {
				return null;
			}

			g.attach(this, b.gunship.generatorHull, generatorHealth);
			placeGenerator(g);
			level.addFreshEntity(g);
			generator = g.getUUID();
			// A part of the blockade: its fall moves the battle on, and it goes when the battle ends.
			SpaceEncounters.adopt(this, g);
		}

		placeGenerator(g);
		generatorHealth = g.getHealth();
		return g;
	}

	/** Its generator was destroyed (by {@link GunshipGenerator#die}): the shield is down for good. */
	void onGeneratorDestroyed(ServerLevel level) {
		if (generatorDestroyed) {
			return;
		}

		generatorDestroyed = true;
		entityData.set(DATA_SHIELDED, false);
		EffectEvents.toTracking(this, SpaceEffects.SHIP_SHIELD, toWorld(0, HEIGHT / 2, 0), heading, getId(), 1f, SpaceEffects.SHIELD_DOWN);
	}

	private void placeGenerator(GunshipGenerator g) {
		Vec3 seat = toWorld(GENERATOR_SEAT[0], GENERATOR_SEAT[1], GENERATOR_SEAT[2]);
		g.setPos(seat.x, seat.y, seat.z);
		g.setYRot(getYRot());
		g.setYHeadRot(getYRot());
		g.setYBodyRot(getYRot());
		g.setDeltaMovement(getDeltaMovement());
	}

	/** Its slow orbit around the post, level, turning gently. */
	private void fly(SpaceBalance b) {
		if (post == null) {
			setPost(position().add(-b.gunship.patrolRadius, 0, 0));
		}

		double radius = Math.max(8, b.gunship.patrolRadius);
		orbitAngle += orbitDirection * b.gunship.speed / radius;
		Vec3 point = post.add(Math.cos(orbitAngle) * radius, 0, Math.sin(orbitAngle) * radius);
		Vec3 to = point.subtract(position());
		Vec3 wanted = new Vec3(to.x, 0, to.z);

		if (wanted.lengthSqr() > 1.0e-6) {
			heading = SpaceRules.turnToward(heading, wanted.normalize(), TURN);
		}

		// Hold the post's height.
		double climb = Math.max(-0.05, Math.min(0.05, (post.y - getY()) * 0.02));
		setDeltaMovement(heading.scale(b.gunship.speed).add(0, climb, 0));
		float yaw = SpaceRules.yawOf(heading);
		setYRot(yaw);
		setXRot(0f);
		setYHeadRot(yaw);
		setYBodyRot(yaw);
	}

	/** Flies the velocity its orbit set, through anything (it is too large to steer round asteroids). */
	@Override
	public void travel(Vec3 input) {
		move(MoverType.SELF, getDeltaMovement());
	}

	// ---------------------------------------------------------------- turrets

	private static int turretInterval(SpaceBalance b) {
		return Math.max(2, Math.round(20f / Math.max(0.1f, b.gunship.turretRate)));
	}

	private void turrets(ServerLevel level, SpaceBalance b, long now) {
		for (int i = 0; i < TURRETS.length; i++) {
			if (now < nextTurretShot[i]) {
				continue;
			}

			double[] t = TURRETS[i];
			Vec3 from = toWorld(t[0], t[1], t[2]);
			Entity target = turretTarget(level, from, b.gunship.turretRange);

			if (target == null) {
				nextTurretShot[i] = now + 5;
				continue;
			}

			Vec3 aimAt = SpaceRules.lead(from, target.getBoundingBox().getCenter(), ShipWeapons.velocityOf(target), b.boltSpeed);
			Vec3 to = aimAt.subtract(from);

			if (to.lengthSqr() < 1.0e-6) {
				continue;
			}

			Vec3 dir = SpaceRules.spread(to.normalize(), b.gunship.turretSpread, getRandom());
			ShipBolt.fire(level, this, null, ShipBolt.Team.HOSTILE, ShipBolt.Style.GUNSHIP, from, dir.scale(b.boltSpeed).add(getDeltaMovement()),
					b.gunship.turretDamage, b.boltRange);
			EffectEvents.toTracking(this, SpaceEffects.SHIP_WEAPON, from, dir, getId(), 1f, SpaceEffects.WEAPON_GUNSHIP_TURRET);
			nextTurretShot[i] = now + turretInterval(b);
		}
	}

	/**
	 * The nearest enemy of a turret: a player's ship of its encounter (or any armed one without an
	 * encounter), or an allied fighter of its encounter.
	 */
	private @Nullable Entity turretTarget(ServerLevel level, Vec3 from, double range) {
		Set<UUID> pilots = SpaceEncounters.participants(this);
		UUID battle = SpaceEncounters.ownerOf(this);
		Entity best = null;
		double bestDistance = range * range;

		for (ServerPlayer player : level.players()) {
			if (pilots != null && !pilots.contains(player.getUUID())) {
				continue;
			}

			if (!(player.getVehicle() instanceof ShipBody ship) || !ship.piloted() || ship.getControllingPassenger() != player) {
				continue;
			}

			if (pilots == null && (ship.upgrades() & ShipRecord.UPGRADE_CANNONS) == 0) {
				continue;
			}

			double distance = ship.getBoundingBox().getCenter().distanceToSqr(from);

			if (distance < bestDistance) {
				best = ship;
				bestDistance = distance;
			}
		}

		AABB box = new AABB(from, from).inflate(range);

		for (Starfighter ally : level.getEntitiesOfClass(Starfighter.class, box,
				f -> f.isAlive() && f.side() == SpaceRules.Side.ALLIED && Objects.equals(SpaceEncounters.ownerOf(f), battle))) {
			double distance = ally.getBoundingBox().getCenter().distanceToSqr(from);

			if (distance < bestDistance) {
				best = ally;
				bestDistance = distance;
			}
		}

		return best;
	}

	// ---------------------------------------------------------------- launches

	/**
	 * While the generator stands: every {@code launchInterval} ticks the bay opens, and
	 * {@link #BAY_OPEN_TICKS} later {@code launchCount} Hymn interceptors drop out, as long as fewer
	 * than {@code maxAlive} fly near it.
	 */
	private void launches(ServerLevel level, SpaceBalance b, long now, boolean shielded) {
		if (bayOpenedAt != Long.MIN_VALUE && now >= bayOpenedAt + BAY_OPEN_TICKS) {
			bayOpenedAt = Long.MIN_VALUE;
			entityData.set(DATA_BAY_OPEN, false);
			int room = b.gunship.maxAlive - interceptorsNear(level);
			int count = Math.min(b.gunship.launchCount, room);

			if (shielded && count > 0) {
				launch(level, b, count);
			}

			return;
		}

		if (!shielded || bayOpenedAt != Long.MIN_VALUE || now < nextLaunch) {
			return;
		}

		if (interceptorsNear(level) >= b.gunship.maxAlive) {
			nextLaunch = now + FULL_SKY_RETRY;
			return;
		}

		bayOpenedAt = now;
		nextLaunch = now + Math.max(BAY_OPEN_TICKS + 1, b.gunship.launchInterval);
		entityData.set(DATA_BAY_OPEN, true);
		EffectEvents.toTracking(this, SpaceEffects.GUNSHIP, toWorld(BAY[0], BAY[1], BAY[2]), heading, getId(), 1f, SpaceEffects.GUNSHIP_BAY_OPENS);
	}

	/** The hostile Hymn interceptors flying within 160 blocks (the waves and its own launches). */
	private int interceptorsNear(ServerLevel level) {
		return level.getEntitiesOfClass(HymnInterceptor.class, getBoundingBox().inflate(160), f -> f.isAlive() && f.side() == SpaceRules.Side.HOSTILE).size();
	}

	private void launch(ServerLevel level, SpaceBalance b, int count) {
		Vec3 bay = toWorld(BAY[0], BAY[1], BAY[2]);
		Vec3 right = ShipBody.local(1, 0, 0, getYRot());

		for (int i = 0; i < count; i++) {
			HymnInterceptor fighter = SpaceEntities.HYMN_INTERCEPTOR.create(level, EntitySpawnReason.EVENT);

			if (fighter == null) {
				continue;
			}

			double side = count == 1 ? 0 : (i % 2 == 0 ? 2.5 : -2.5);
			Vec3 at = bay.add(right.scale(side)).add(0, -1.5, 0);
			fighter.snapTo(at.x, at.y, at.z, getYRot(), 0f);
			fighter.setup(SpaceRules.Side.HOSTILE, false, b);
			fighter.launchAlong(heading.add(right.scale(side * 0.15)).add(0, -0.7, 0), 0.6);
			level.addFreshEntity(fighter);
			SpaceEncounters.adopt(this, fighter);
		}

		EffectEvents.toTracking(this, SpaceEffects.GUNSHIP, bay, heading, getId(), count, SpaceEffects.GUNSHIP_LAUNCH);
	}

	// ---------------------------------------------------------------- damage

	/**
	 * Only ship weapons (and {@code /kill}) reach it, and only once its generator is down: until then
	 * every hit is a ripple on its shield.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}

		if (!source.is(SpaceEntities.SHIP_WEAPON) || isDeadOrDying()) {
			return false;
		}

		Entity direct = source.getDirectEntity();
		Vec3 at = direct != null ? direct.position() : getBoundingBox().getCenter();
		Vec3 normal = at.subtract(toWorld(0, HEIGHT / 2, 0));
		Vec3 dir = normal.lengthSqr() > 1.0e-6 ? normal.normalize() : null;

		if (!generatorDestroyed) {
			EffectEvents.around(level, SpaceEffects.SHIP_HIT, at, dir, getId(), amount, SpaceEffects.HIT_SHIELD);
			return false;
		}

		EffectEvents.around(level, SpaceEffects.SHIP_HIT, at, dir, getId(), amount, SpaceEffects.HIT_HULL);
		boolean hurt = super.hurtServer(level, source, amount);
		this.invulnerableTime = 0;
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		boolean first = !this.dead;
		super.die(source);

		if (first && level() instanceof ServerLevel level) {
			entityData.set(DATA_BAY_OPEN, false);
			GunshipGenerator g = generator(level);

			if (g != null) {
				g.discard();
			}

			SpaceRewards.onDestroyed(level, this, SpaceRules.Kind.GUNSHIP, source);
		}
	}

	/**
	 * The death (5.5, 15.1): three fighter-sized blasts along the spine over 40 ticks, then the end
	 * (it breaks in two, which the client draws from this count), and it is gone at {@link #GONE_TICK}.
	 * Counted on both sides, so the client knows how far it has come apart.
	 */
	@Override
	protected void tickDeath() {
		this.deathTime++;

		if (!(level() instanceof ServerLevel level)) {
			return;
		}

		for (int i = 0; i < BLAST_TICKS.length; i++) {
			if (this.deathTime == BLAST_TICKS[i]) {
				Vec3 at = toWorld(0, HEIGHT * 0.6, BLAST_FORWARD[i]);
				EffectEvents.around(level, SpaceEffects.SHIP_DESTROYED, at, heading, getId(), 1f, SpaceEffects.DESTROYED_GUNSHIP_BLAST);
			}
		}

		if (this.deathTime == END_TICK) {
			EffectEvents.around(level, SpaceEffects.SHIP_DESTROYED, toWorld(0, HEIGHT / 2, 0), heading, getId(), 1f, SpaceEffects.DESTROYED_GUNSHIP_END);
		}

		if (this.deathTime >= GONE_TICK && !isRemoved()) {
			discard();
		}
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	/** Its hum is a client loop; its hits are the {@code ship_hit} event. */
	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return null;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 320 * 320;
	}
}
