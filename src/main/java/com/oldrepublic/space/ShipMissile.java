package com.oldrepublic.space;

import java.util.UUID;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.travel.ShipBody;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A concussion missile (spec MILESTONE_5.md 5.3, cut order 8): the pilot's, fired at a locked target
 * from a {@code concussion_missile} in the hold, or a kajidic raider's at a player's ship (5.5). It
 * homes on its target, turning at most {@code space.missileTurn} degrees a tick at
 * {@code space.missileSpeed}, and falls apart after {@code space.missileLife} ticks. It hits what a
 * bolt of its {@link ShipBolt.Team} may hit; a player's missile also hurts the other ship targets
 * within {@code space.missileSplashRadius} of the blast. A missile at a player's ship keeps that
 * pilot's incoming warning on until it ends, whatever ends it.
 */
public class ShipMissile extends AbstractHurtingProjectile {
	private static final EntityDataAccessor<Integer> DATA_TEAM = SynchedEntityData.defineId(ShipMissile.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SHIP = SynchedEntityData.defineId(ShipMissile.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3fc> DATA_VELOCITY = SynchedEntityData.defineId(ShipMissile.class, EntityDataSerializers.VECTOR3);
	private static final double RENDER_DISTANCE = 160;

	private float damage;
	private float splash;
	private double splashRadius;
	private double speed;
	private float turn;
	private int life;
	private @Nullable UUID shooter;
	private @Nullable UUID target;
	/** The pilot warned of this missile (a fighter's missile at their ship), told when it ends. */
	private @Nullable UUID warned;

	public ShipMissile(EntityType<? extends ShipMissile> type, Level level) {
		super(type, level);
		this.accelerationPower = 0.0;
	}

	/**
	 * Launches a missile from {@code from} along {@code heading} at {@code target}.
	 *
	 * @param ship    the ship or fighter that fires it (never hit, nor its riders)
	 * @param shooter the player credited with a kill, or null
	 * @param warned  the pilot whose incoming warning this missile keeps on, or null
	 */
	public static ShipMissile launch(ServerLevel level, Entity ship, @Nullable UUID shooter, ShipBolt.Team team, Vec3 from, Vec3 heading,
			Entity target, float damage, float splash, @Nullable UUID warned, SpaceBalance b) {
		ShipMissile missile = new ShipMissile(SpaceEntities.SHIP_MISSILE, level);
		missile.setPos(from);
		missile.entityData.set(DATA_TEAM, team.ordinal());
		missile.entityData.set(DATA_SHIP, ship.getId());
		missile.damage = damage;
		missile.splash = splash;
		missile.splashRadius = b.missileSplashRadius;
		missile.speed = b.missileSpeed;
		missile.turn = b.missileTurn;
		missile.life = b.missileLife;
		missile.shooter = shooter;
		missile.target = target.getUUID();
		missile.warned = warned;
		missile.steer(heading.normalize().scale(b.missileSpeed));
		level.addFreshEntity(missile);
		return missile;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TEAM, ShipBolt.Team.PLAYERS.ordinal());
		builder.define(DATA_SHIP, -1);
		builder.define(DATA_VELOCITY, new Vector3f());
	}

	public ShipBolt.Team team() {
		return ShipBolt.Team.of(entityData.get(DATA_TEAM));
	}

	public int shipId() {
		return entityData.get(DATA_SHIP);
	}

	/** The synced velocity (blocks per tick), which the renderer faces along. */
	public Vec3 velocity() {
		Vector3fc v = entityData.get(DATA_VELOCITY);
		return new Vec3(v.x(), v.y(), v.z());
	}

	/** The player credited with what this missile destroys, or null. Server side. */
	public @Nullable UUID shooter() {
		return shooter;
	}

	/** The entity it homes on (server side), or null. */
	public @Nullable UUID target() {
		return target;
	}

	private void steer(Vec3 velocity) {
		setDeltaMovement(velocity);
		entityData.set(DATA_VELOCITY, new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z));
		setYRot((float) (Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG));
		setXRot((float) (Mth.atan2(velocity.y, velocity.horizontalDistance()) * Mth.RAD_TO_DEG));
	}

	@Override
	public void tick() {
		if (level().isClientSide()) {
			setDeltaMovement(velocity());
		} else if (level() instanceof ServerLevel level) {
			if (--life < 0) {
				end(level);
				return;
			}

			home(level);

			if (strikeHull(level)) {
				return;
			}
		}

		super.tick();
	}

	/** Turns toward the target at the missile's turn rate; a lost target leaves it flying straight. */
	private void home(ServerLevel level) {
		Entity aim = target == null ? null : level.getEntity(target);
		Vec3 velocity = getDeltaMovement();

		if (aim == null || !aim.isAlive() || velocity.lengthSqr() < 1.0e-6) {
			return;
		}

		Vec3 heading = velocity.normalize();
		Vec3 wanted = aim.getBoundingBox().getCenter().subtract(position());

		if (wanted.lengthSqr() < 1.0e-6) {
			return;
		}

		steer(SpaceRules.turnToward(heading, wanted.normalize(), turn).scale(speed));
	}

	/** As a bolt: the gunship's turned hull before the sweep. */
	private boolean strikeHull(ServerLevel level) {
		if (!ShipBolt.mayHitGunship(team())) {
			return false;
		}

		Vec3 from = position();
		Vec3 to = from.add(getDeltaMovement());
		int ship = shipId();
		BlockadeGunship.HullHit hit = BlockadeGunship.firstHullHit(level, from, to, gunship -> gunship.getId() != ship);

		if (hit == null) {
			return false;
		}

		HitResult other = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, getClipType());

		if (other.getType() != HitResult.Type.MISS && from.distanceToSqr(other.getLocation()) < from.distanceToSqr(hit.at())) {
			return false;
		}

		setPos(hit.at());
		blast(level, hit.gunship(), hit.at());
		return true;
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		return ShipBolt.mayHit(target, team(), shipId());
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (level() instanceof ServerLevel level && !isRemoved()) {
			blast(level, result.getEntity(), result.getLocation());
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		if (level() instanceof ServerLevel level && !isRemoved()) {
			Direction face = result.getDirection();
			Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
			EffectEvents.around(level, SpaceEffects.SHIP_HIT, result.getLocation(), normal, -1, damage, SpaceEffects.HIT_BLOCK);
			end(level);
		}
	}

	/**
	 * The warhead goes off on {@code hit} at {@code at}: its damage there, the splash around (players'
	 * and allies' missiles); as for bolts, only what {@link ShipBolt#lands} allows takes damage.
	 */
	private void blast(ServerLevel level, Entity hit, Vec3 at) {
		// A ship outside this missile's battle takes nothing, but the warhead is spent on it all the same.
		boolean lands = ShipBolt.lands(level, hit, team(), shipId());

		if (lands && hit instanceof ShipBody body) {
			UUID owner = body.ownerId();
			ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);

			if (player != null) {
				ShipCombat.hitShip(player, damage, this);
			}
		} else if (lands) {
			hit.hurtServer(level, source(level), damage);
		}

		if (splash > 0f && team() != ShipBolt.Team.HOSTILE) {
			AABB area = new AABB(at, at).inflate(splashRadius + BlockadeGunship.REACH);

			for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != hit && e.is(SpaceEntities.SHIP_TARGETS))) {
				boolean near = other instanceof BlockadeGunship gunship ? gunship.hullDistance(at) <= splashRadius
						: other.getBoundingBox().distanceToSqr(at) <= splashRadius * splashRadius;

				if (near && (other instanceof BlockadeGunship || canHitEntity(other)) && ShipBolt.lands(level, other, team(), shipId())) {
					other.hurtServer(level, source(level), splash);
				}
			}
		}

		end(level);
	}

	private DamageSource source(ServerLevel level) {
		return ShipBolt.damageSource(level, this, shipId());
	}

	/** Goes out without a blast (the fight that fired it is over); a warned pilot's warning stops. */
	public void fizzle(ServerLevel level) {
		end(level);
	}

	/** The missile is gone: a warned pilot's warning stops. */
	private void end(ServerLevel level) {
		if (warned != null) {
			ServerPlayer pilot = level.getServer().getPlayerList().getPlayer(warned);

			if (pilot != null) {
				ShipWeapons.incomingEnded(pilot);
			}

			warned = null;
		}

		discard();
	}

	@Override
	public void remove(RemovalReason reason) {
		// Unloaded with its chunk or killed by a command: the warning must still stop.
		if (warned != null && level() instanceof ServerLevel level) {
			ServerPlayer pilot = level.getServer().getPlayerList().getPlayer(warned);
			warned = null;

			if (pilot != null) {
				ShipWeapons.incomingEnded(pilot);
			}
		}

		super.remove(reason);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected boolean shouldBurn() {
		return false;
	}

	/** The smoke trail is spawned by the client through its particle budget (15.1), not here. */
	@Override
	protected @Nullable ParticleOptions getTrailParticle() {
		// Probed (tools/api-probe/m5_w2.txt): AbstractHurtingProjectile#tick ends with createParticleTrail, which skips a null particle.
		return null;
	}

	@Override
	protected float getInertia() {
		return 1f;
	}

	@Override
	protected float getLiquidInertia() {
		return 1f;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < RENDER_DISTANCE * RENDER_DISTANCE;
	}
}
