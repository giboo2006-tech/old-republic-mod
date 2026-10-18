package com.oldrepublic.travel;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The courier shuttle's body (spec MILESTONE_4.md 4.1): a plain entity that reads and writes its
 * owner's {@link ShipRecord}. The record is the truth; this body only shows it, carries riders and
 * is moved along the flight path by {@link Flights}. A body whose UUID is not the registry's
 * current one discards itself, so a ship (and its hold) never exists twice.
 *
 * <p>Nobody steers it: {@link #getControllingPassenger()} is null, so no client sends vehicle
 * movement and both floating kicks skip its riders. It takes no damage at all ({@code /kill}
 * removes only the body; {@link Ships#restore} brings it back), is collidable like a boat on the
 * ground (players can stand on it; its riders are excepted by vanilla), and is not collidable
 * while it flies.
 *
 * <p>Synced to clients: the state, hull, wear level, flight phase, struts, hull code, upgrades (with
 * the paint) and owner, which the renderer, the engine and damage loops and the flight HUD read.
 */
public class CourierShuttle extends Entity {
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(CourierShuttle.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_HULL = SynchedEntityData.defineId(CourierShuttle.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(CourierShuttle.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_GEAR = SynchedEntityData.defineId(CourierShuttle.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_CODE = SynchedEntityData.defineId(CourierShuttle.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_UPGRADES = SynchedEntityData.defineId(CourierShuttle.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_WEAR = SynchedEntityData.defineId(CourierShuttle.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_OWNER_MOST = SynchedEntityData.defineId(CourierShuttle.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Long> DATA_OWNER_LEAST = SynchedEntityData.defineId(CourierShuttle.class, EntityDataSerializers.LONG);

	/** Paint schemes (4.1): Watch green, Imperial grey, Vasch's rust red. */
	public static final int PAINT_WATCH = 0;
	public static final int PAINT_IMPERIAL = 1;
	public static final int PAINT_VASCH = 2;

	/** Ticks between two reads of the registry while nothing changes. */
	private static final int SYNC_INTERVAL = 10;

	/**
	 * Seats in ship space (right, up, forward), in blocks from the body's position: the pilot in the
	 * cockpit, then the passenger bench behind it and a jump seat.
	 */
	private static final double[][] SEATS = {
			{0.0, 1.05, 3.1},
			{-0.85, 0.95, 1.3},
			{0.85, 0.95, 1.3},
			{0.0, 0.95, -0.6}
	};

	private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
	/** Client: game ticks since the synced phase, state and gear last changed, for the ramp, tarp and struts. */
	private int phaseAge;
	private int lastPhase = ShipRules.PHASE_GROUND;
	private int lastState = -1;
	/** Client: the state before the last change (-1 when the body was first seen in its current state). */
	private int previousState = -1;
	private int stateAge;
	private boolean lastGear = true;
	private int gearAge = Integer.MAX_VALUE / 2;

	public CourierShuttle(EntityType<? extends CourierShuttle> type, Level level) {
		super(type, level);
		setNoGravity(true);
		this.blocksBuilding = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_STATE, ShipState.HULK.ordinal());
		builder.define(DATA_HULL, 0);
		builder.define(DATA_PHASE, ShipRules.PHASE_GROUND);
		builder.define(DATA_GEAR, true);
		builder.define(DATA_CODE, 0);
		builder.define(DATA_UPGRADES, 0);
		builder.define(DATA_WEAR, ShipRules.WEAR_NONE);
		builder.define(DATA_OWNER_MOST, 0L);
		builder.define(DATA_OWNER_LEAST, 0L);
	}

	// ---------------------------------------------------------------- synced view

	public ShipState state() {
		int ordinal = entityData.get(DATA_STATE);
		ShipState[] states = ShipState.values();
		return ordinal >= 0 && ordinal < states.length ? states[ordinal] : ShipState.HULK;
	}

	public int hull() {
		return entityData.get(DATA_HULL);
	}

	/** The 4.6 phase index while flying, or {@link ShipRules#PHASE_GROUND}. */
	public int phase() {
		return entityData.get(DATA_PHASE);
	}

	/** The paint scheme, {@link #PAINT_WATCH} to {@link #PAINT_VASCH} (kept in the upgrade mask, {@link ShipRules#paint}). */
	public int paint() {
		return ShipRules.paint(upgrades());
	}

	/** Whether the landing struts are down (on the ground, and in the last blocks of a descent). */
	public boolean gearDown() {
		return entityData.get(DATA_GEAR);
	}

	public String hullCode() {
		return ShipRules.unpackHullCode(entityData.get(DATA_CODE));
	}

	public int upgrades() {
		return entityData.get(DATA_UPGRADES);
	}

	/** How worn the hull looks and sounds, {@link ShipRules#WEAR_NONE} to {@link ShipRules#WEAR_FAILING} (decided by the server's balance). */
	public int wearLevel() {
		return entityData.get(DATA_WEAR);
	}

	/** The owner's UUID as synced (null before the first sync). */
	public @Nullable UUID owner() {
		long most = entityData.get(DATA_OWNER_MOST);
		long least = entityData.get(DATA_OWNER_LEAST);
		return most == 0L && least == 0L ? null : new UUID(most, least);
	}

	/** Client: ticks since the phase last changed. */
	public int phaseAge() {
		return phaseAge;
	}

	/** Client: ticks since the state last changed (the hulk standing up). */
	public int stateAge() {
		return stateAge;
	}

	/** Client: the {@code ShipState} ordinal before the last change, or -1 (the hulk's stand-up plays only after a change seen here). */
	public int previousState() {
		return previousState;
	}

	/** Client: ticks since the struts last went up or down. */
	public int gearAge() {
		return gearAge;
	}

	/** Riders cannot leave and take no damage: from the spool to the end of the settle (4.6, 12.2). */
	public boolean locksRiders() {
		return state().flying() || phase() != ShipRules.PHASE_GROUND;
	}

	/** Copies the record into the synced data (server). */
	void show(ShipRecord record, int phase) {
		entityData.set(DATA_STATE, record.state().ordinal());
		entityData.set(DATA_HULL, record.hull());
		entityData.set(DATA_PHASE, phase);
		entityData.set(DATA_CODE, ShipRules.packHullCode(record.hullCode()));
		entityData.set(DATA_UPGRADES, record.upgrades());
		entityData.set(DATA_WEAR, ShipRules.wearLevel(record, ShipBalance.get()));
		entityData.set(DATA_OWNER_MOST, record.owner().getMostSignificantBits());
		entityData.set(DATA_OWNER_LEAST, record.owner().getLeastSignificantBits());
	}

	void setGear(boolean down) {
		entityData.set(DATA_GEAR, down);
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();

		if (level() instanceof ServerLevel server) {
			if (tickCount % SYNC_INTERVAL == 1 || tickCount < 3) {
				Ships.tickBody(server, this);
			}

			return;
		}

		if (isInterpolating()) {
			// Like boats, a plain entity steps its own InterpolationHandler in tick.
			interpolation.interpolate();
		}

		int phase = phase();
		phaseAge = phase == lastPhase ? phaseAge + 1 : 0;
		lastPhase = phase;
		int state = entityData.get(DATA_STATE);

		if (state != lastState) {
			previousState = lastState;
			stateAge = 0;
		} else {
			stateAge++;
		}

		lastState = state;
		boolean gear = gearDown();
		gearAge = gear == lastGear ? gearAge + 1 : 0;
		lastGear = gear;
	}

	@Override
	public InterpolationHandler getInterpolation() {
		return interpolation;
	}

	/** Moves the body along its flight path (server). */
	void flyTo(Vec3 pos, float yaw, float pitch) {
		setPos(pos);
		setYRot(yaw);
		setXRot(pitch);
	}

	// ---------------------------------------------------------------- damage and collision

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	/** Riders inside the hull can still use it (the ship screen). */
	@Override
	public boolean canBePickedFromInside() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	/** Solid like a boat on the ground (riders are excepted by vanilla); not while it flies. */
	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return isAlive() && !state().flying() && phase() == ShipRules.PHASE_GROUND;
	}

	// ---------------------------------------------------------------- use and riding

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player instanceof ServerPlayer serverPlayer && level() instanceof ServerLevel) {
			return ShipActions.use(serverPlayer, this, hand);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && level() instanceof ServerLevel server && Ships.hasSeatFor(server, this);
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return null;
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);

		if (passenger instanceof ServerPlayer player) {
			ShipCamera.attach(player);
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);

		if (passenger instanceof ServerPlayer player) {
			ShipCamera.detach(player);
			player.resetFallDistance();
		}
	}

	/** The seat of {@code passenger}: the owner flies, the others fill the bench in boarding order. */
	int seatOf(Entity passenger) {
		UUID owner = owner();

		if (owner != null && owner.equals(passenger.getUUID())) {
			return 0;
		}

		int seat = 1;

		for (Entity other : getPassengers()) {
			if (other == passenger) {
				break;
			}

			if (owner == null || !owner.equals(other.getUUID())) {
				seat++;
			}
		}

		return Math.min(seat, SEATS.length - 1);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		double[] seat = SEATS[seatOf(passenger)];
		return local(seat[0], seat[1], seat[2], getYRot());
	}

	/** Beside the ramp, where riders step off (4.6). */
	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return level() instanceof ServerLevel server ? Ships.besideRamp(server, this, passenger) : super.getDismountLocationForPassenger(passenger);
	}

	/** A point in ship space (right, up, forward, in blocks) turned into a world offset for {@code yaw}. */
	public static Vec3 local(double right, double up, double forward, float yaw) {
		double rad = Math.toRadians(yaw);
		double sin = Math.sin(rad);
		double cos = Math.cos(rad);
		// Facing (-sin, 0, cos); the right-hand side is (-cos, 0, -sin).
		return new Vec3(-sin * forward - cos * right, up, cos * forward - sin * right);
	}

	// ---------------------------------------------------------------- saving

	/** Nothing of its own: everything the body shows is copied from the registry on its first tick. */
	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
