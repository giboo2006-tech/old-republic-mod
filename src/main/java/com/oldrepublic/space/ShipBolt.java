package com.oldrepublic.space;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A ship's laser bolt (spec MILESTONE_5.md 5.3, 5.5): the pilot's cannons, the gunner's turret, the
 * starfighters' lasers and the gunship's turrets. It flies straight at its speed for its range, and
 * {@link AbstractHurtingProjectile} sweeps its whole move every tick, so a bolt never passes through
 * a fast target. It never hits the ship or fighter that fired it, nor anyone riding it, for its whole
 * life; it never hurts players, other mobs or blocks (a block stops it with sparks).
 *
 * <p>What it may hit depends on its {@link Team}: the players' bolts and the allied fighters' hit
 * ship targets ({@code #oldrepublic:ship_targets}) of the other side, and the hostile fighters' hit
 * players' ships (through {@link ShipCombat#hitShip}) and allied fighters. Enemy and allied fire
 * counts only inside its own encounter ({@link #lands}): one player's battle never hurts another
 * player's ship or fighters. The gunship's box is as wide as its length, so its hull is tested on its
 * own turned box ({@link BlockadeGunship#hullHit}) before the sweep, never by the sweep itself.
 *
 * <p>The damage source is {@code ship_weapon} caused by the firing ship or fighter, never a player,
 * so the Milestone 1 kill XP is not paid twice: {@link SpaceRewards} pays the shooter recorded here.
 * The velocity is synced exactly ({@link #DATA_VELOCITY}), so a bolt faster than the add-entity
 * packet carries still flies at its speed on every client.
 */
public class ShipBolt extends AbstractHurtingProjectile {
	/** Who fired the bolt, which sets what it may hit. Synced by ordinal: append only. */
	public enum Team {
		/** A player's ship: the cannons or the turret. */
		PLAYERS,
		/** A hostile starfighter or the gunship. */
		HOSTILE,
		/** A fighter on the players' side (the clan's escort, 6.5). */
		ALLIED;

		public static Team of(int ordinal) {
			Team[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}
	}

	/** The bolt's colour. Synced by ordinal: append only. */
	public enum Style {
		/** A Sith player's ship: red. */
		SITH,
		/** A Jedi player's ship: green-white. */
		JEDI,
		/** An Independent player's ship (or one without a path): yellow. */
		INDEPENDENT,
		/** Ulvaine's interceptors: violet. */
		HYMN,
		/** The kajidic's raiders: orange. */
		RAIDER,
		/** Clan Hask: amber. */
		HASK,
		/** The blockade gunship's turrets: violet, heavier. */
		GUNSHIP;

		public static Style of(int ordinal) {
			Style[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}
	}

	private static final EntityDataAccessor<Integer> DATA_TEAM = SynchedEntityData.defineId(ShipBolt.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STYLE = SynchedEntityData.defineId(ShipBolt.class, EntityDataSerializers.INT);
	/** The network id of the ship or fighter that fired it (-1: none), so clients skip it too. */
	private static final EntityDataAccessor<Integer> DATA_SHIP = SynchedEntityData.defineId(ShipBolt.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3fc> DATA_VELOCITY = SynchedEntityData.defineId(ShipBolt.class, EntityDataSerializers.VECTOR3);
	/** Beyond this the bolt is not drawn (blocks); the encounter's fighters fight within it. */
	private static final double RENDER_DISTANCE = 160;

	private float damage;
	/** Ticks of flight left (server). */
	private int life;
	/** The player credited with what it destroys (null for a fighter's bolt). */
	private @Nullable UUID shooter;

	public ShipBolt(EntityType<? extends ShipBolt> type, Level level) {
		super(type, level);
		this.accelerationPower = 0.0;
	}

	/**
	 * Fires a bolt from {@code from} with {@code velocity} (blocks per tick) for {@code range} blocks.
	 *
	 * @param ship    the ship or fighter that fires it: never hit, nor its riders
	 * @param shooter the player credited with a kill (the pilot or the gunner), or null
	 */
	public static ShipBolt fire(ServerLevel level, Entity ship, @Nullable UUID shooter, Team team, Style style, Vec3 from, Vec3 velocity,
			float damage, double range) {
		ShipBolt bolt = new ShipBolt(SpaceEntities.SHIP_BOLT, level);
		bolt.setPos(from);
		bolt.launch(velocity);
		bolt.entityData.set(DATA_TEAM, team.ordinal());
		bolt.entityData.set(DATA_STYLE, style.ordinal());
		bolt.entityData.set(DATA_SHIP, ship.getId());
		bolt.damage = damage;
		bolt.shooter = shooter;
		bolt.life = Mth.ceil(range / Math.max(0.1, velocity.length()));
		level.addFreshEntity(bolt);
		return bolt;
	}

	/** Sets the velocity, its synced copy and the facing (the projectile convention: yaw from +Z toward +X). */
	void launch(Vec3 velocity) {
		setDeltaMovement(velocity);
		entityData.set(DATA_VELOCITY, new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z));
		setYRot((float) (Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG));
		setXRot((float) (Mth.atan2(velocity.y, velocity.horizontalDistance()) * Mth.RAD_TO_DEG));
		this.yRotO = getYRot();
		this.xRotO = getXRot();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TEAM, Team.PLAYERS.ordinal());
		builder.define(DATA_STYLE, Style.INDEPENDENT.ordinal());
		builder.define(DATA_SHIP, -1);
		builder.define(DATA_VELOCITY, new Vector3f());
	}

	public Team team() {
		return Team.of(entityData.get(DATA_TEAM));
	}

	public Style style() {
		return Style.of(entityData.get(DATA_STYLE));
	}

	/** The network id of the firing ship or fighter, -1 for none. */
	public int shipId() {
		return entityData.get(DATA_SHIP);
	}

	/** The player credited with what this bolt destroys, or null. Server side. */
	public @Nullable UUID shooter() {
		return shooter;
	}

	public float damage() {
		return damage;
	}

	@Override
	public void tick() {
		if (level().isClientSide()) {
			// The add-entity packet may carry a clipped velocity: fly at the synced one.
			Vector3fc v = entityData.get(DATA_VELOCITY);
			setDeltaMovement(new Vec3(v.x(), v.y(), v.z()));
		} else if (level() instanceof ServerLevel level) {
			if (--life < 0) {
				discard();
				return;
			}

			if (strikeHull(level)) {
				return;
			}
		}

		super.tick();
	}

	/**
	 * The gunship's turned hull, tested before the sweep: when this tick's move enters it before
	 * anything the sweep would hit, the bolt strikes it there. True when the bolt struck.
	 */
	private boolean strikeHull(ServerLevel level) {
		if (!mayHitGunship(team())) {
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
		strike(level, hit.gunship(), hit.at());
		discard();
		return true;
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		return mayHit(target, team(), shipId());
	}

	/**
	 * Whether a ship weapon of {@code team} fired by the entity {@code ship} (network id) may hit
	 * {@code target}: never its own ship or that ship's riders, never a player or a projectile, and
	 * the gunship only through its turned hull. Uses synced state only, so both sides agree.
	 */
	public static boolean mayHit(Entity target, Team team, int ship) {
		if (!target.isAlive() || target instanceof Player || target instanceof Projectile || target instanceof BlockadeGunship) {
			return false;
		}

		if (target.getId() == ship || target.getRootVehicle().getId() == ship) {
			return false;
		}

		return switch (team) {
			case HOSTILE -> target instanceof ShipBody || target instanceof Starfighter fighter && fighter.side() == SpaceRules.Side.ALLIED;
			case ALLIED -> target.is(SpaceEntities.SHIP_TARGETS) && !(target instanceof Starfighter fighter && fighter.side() != SpaceRules.Side.HOSTILE);
			default -> target.is(SpaceEntities.SHIP_TARGETS) && !(target instanceof Starfighter fighter && fighter.side() == SpaceRules.Side.ALLIED);
		};
	}

	/** Whether a weapon of {@code team} may hit the gunship (its hull or its generator): the players and their allies. */
	public static boolean mayHitGunship(Team team) {
		return team != Team.HOSTILE;
	}

	/**
	 * Server: whether a hit that {@link #mayHit} allows counts on {@code target}. The players' own fire
	 * counts on every ship target. Enemy and allied fire counts only inside its own encounter: on a
	 * fighter or gunship of the same encounter, and on a player's ship when its owner flies in that
	 * encounter; fire from a fighter without an encounter (a spawn egg's) reaches only armed ships, as
	 * its targeting does (5.6). So one player's battle never hurts another player's ship or fighters.
	 * A hit that does not count still stops the bolt.
	 */
	public static boolean lands(ServerLevel level, Entity target, Team team, int ship) {
		if (team == Team.PLAYERS) {
			return true;
		}

		Entity firing = ship >= 0 ? level.getEntity(ship) : null;

		if (target instanceof ShipBody body) {
			UUID owner = body.ownerId();
			Set<UUID> pilots = firing == null ? null : SpaceEncounters.participants(firing);

			if (owner == null) {
				return false;
			}

			return pilots != null ? pilots.contains(owner) : (body.upgrades() & ShipRecord.UPGRADE_CANNONS) != 0;
		}

		return firing != null && Objects.equals(SpaceEncounters.ownerOf(firing), SpaceEncounters.ownerOf(target));
	}

	/**
	 * A {@code ship_weapon} hit as a bolt fired by {@code ship} for {@code shooter} deals it, with no
	 * bolt in flight (game tests): {@link SpaceRewards} credits the shooter, as for a real bolt.
	 */
	public static DamageSource strikeOf(ServerLevel level, Entity ship, @Nullable UUID shooter) {
		ShipBolt bolt = new ShipBolt(SpaceEntities.SHIP_BOLT, level);
		bolt.setPos(ship.getBoundingBox().getCenter());
		bolt.entityData.set(DATA_SHIP, ship.getId());
		bolt.shooter = shooter;
		return damageSource(level, bolt, ship.getId());
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (level() instanceof ServerLevel level && !isRemoved()) {
			strike(level, result.getEntity(), result.getLocation());
			discard();
		}
	}

	/** Applies the bolt to {@code target} at {@code at}: a player's ship through its shield and hull, a ship target through its own. */
	private void strike(ServerLevel level, Entity target, Vec3 at) {
		if (!lands(level, target, team(), shipId())) {
			return;
		}

		if (target instanceof ShipBody body) {
			UUID owner = body.ownerId();
			ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);

			if (player != null) {
				ShipCombat.hitShip(player, damage, this);
			}

			return;
		}

		target.hurtServer(level, damageSource(level, this, shipId()), damage);
	}

	/** {@code ship_weapon} from {@code direct}, caused by the firing ship or fighter when it is still in the level. */
	static DamageSource damageSource(ServerLevel level, Entity direct, int ship) {
		Entity firing = ship >= 0 ? level.getEntity(ship) : null;
		return level.damageSources().source(SpaceEntities.SHIP_WEAPON, direct, firing);
	}

	/** A block stops the bolt with a few sparks; nothing in it reacts (ship weapons never break or trigger blocks). */
	@Override
	protected void onHitBlock(BlockHitResult result) {
		if (level() instanceof ServerLevel level && !isRemoved()) {
			Direction face = result.getDirection();
			Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
			EffectEvents.around(level, SpaceEffects.SHIP_HIT, result.getLocation(), normal, -1, damage, SpaceEffects.HIT_BLOCK);
			discard();
		}
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

	/** No trail particles (15.1): the renderer draws the streak. */
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
