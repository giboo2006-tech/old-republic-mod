package com.oldrepublic.travel;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A ship's body of any class (spec MILESTONE_4.md 4.1, MILESTONE_5.md 4.1, 4.9): the courier
 * ({@link CourierShuttle}) and the interceptor. A plain entity that reads and writes its owner's
 * {@link ShipRecord}: the record is the truth, and this body only shows it and carries riders. A body
 * whose UUID is not the registry's current one discards itself, so a ship (and its hold) never
 * exists twice.
 *
 * <p>Who moves it: on the autopilot ({@link Flights}) and while the owner takes or lands the helm
 * ({@link Helm}) the server moves it along a path, nobody controls it
 * ({@link #getControllingPassenger()} is null) and clients interpolate. Once the owner flies it by
 * hand, the synced piloted flag makes the owner aboard its controlling passenger, so the pilot's
 * client simulates it (recipe A of 4.1): each tick it turns the pilot's controls into a velocity
 * ({@link FlightModel}) and moves, and vanilla sends the new position to the server every tick,
 * where it is replayed with collisions and accepted. Everyone else, the server included, follows.
 * The server pulls the ship back only after a move faster than the speed cap, and then tells the
 * pilot. It also measures the ship's speed from the moves it accepts, and a move that ran into
 * something costs hull (4.4). Neither floating kick can fire: the body has no gravity and is a
 * flying vehicle. No portal takes it, and one that falls out of the world goes home (4.10).
 *
 * <p>It takes no damage at all ({@code /kill} removes only the body; {@link Ships#restore} brings it
 * back), is collidable like a boat on the ground (players can stand on it; its riders are excepted
 * by vanilla), and is not collidable while it flies.
 *
 * <p>Synced to clients: the state, hull, wear level, flight phase, struts, hull code, upgrades (with
 * the paint), owner, piloted flag and the class's most hull, which the renderer, the engine and
 * damage loops, the flight HUDs and the pilot's controls read.
 *
 * <p>Each class gives its seats, its engine nozzles, gun points and landing footprint in ship
 * space (right, up, forward, in blocks from the body's position; {@link #local}).
 */
public abstract class ShipBody extends Entity {
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_HULL = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_GEAR = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_CODE = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_UPGRADES = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_WEAR = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_OWNER_MOST = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Long> DATA_OWNER_LEAST = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Boolean> DATA_PILOTED = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_MAX_HULL = SynchedEntityData.defineId(ShipBody.class, EntityDataSerializers.INT);

	/** Paint schemes (MILESTONE_4.md 4.1): Watch green, Imperial grey, Vasch's rust red. */
	public static final int PAINT_WATCH = 0;
	public static final int PAINT_IMPERIAL = 1;
	public static final int PAINT_VASCH = 2;

	/** The gunner's seat (5.2): the first after the pilot's. */
	public static final int GUNNER_SEAT = 1;

	/** Ticks between two reads of the registry while nothing changes. */
	private static final int SYNC_INTERVAL = 10;
	/** The server pulls a piloted ship back after a move longer than the speed cap times this (4.1). */
	private static final double CAP_TOLERANCE = 1.1;
	/** On the pilot's client, a change of position beyond this (squared blocks) that it did not fly itself is a server correction. */
	private static final double MOVED_ELSEWHERE = 1.0e-6;
	/** How far past an accepted move the server looks for what the ship ran into (blocks, 4.4). */
	private static final double PROBE = 0.05;
	/** A crash counts at most once in this many ticks (one collision spreads over several moves). */
	private static final int IMPACT_COOLDOWN = 10;

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

	/** The pilot's client: the speed in the flight model's three channels. */
	private FlightModel.Motion motion = FlightModel.Motion.STILL;
	/** The pilot's client: the controls and flight numbers for the next tick, from the pilot's {@code rideTick}. */
	private FlightModel.@Nullable Controls controls;
	private FlightModel.@Nullable Profile profile;
	/** The pilot's client: where the last tick of flight left the ship; null when it did not fly last tick. */
	private @Nullable Vec3 flown;
	/** The pilot's client: how often the server put the ship somewhere it did not fly to (the spike's measure, 4.1). */
	private int corrections;
	/** The pilot's client: the speed the server gave after it moved the ship itself ({@code flight_motion}), for the next tick. */
	private FlightModel.@Nullable Motion imposed;
	/** Server: where the piloted ship stood at its last tick, and whether a move the pilot sent since then was faster than the cap. */
	private @Nullable Vec3 lastGood;
	private boolean tooFast;
	/** Server: the piloted ship's last accepted position and the one before it (4.4: its measured speed). */
	private @Nullable Vec3 lastAccepted;
	private @Nullable Vec3 acceptedBefore;
	/** Server: the game time before which a crash does not count again. */
	private long impactAfter;

	protected ShipBody(EntityType<? extends ShipBody> type, Level level) {
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
		builder.define(DATA_PILOTED, false);
		builder.define(DATA_MAX_HULL, 100);
	}

	/**
	 * Seats in ship space (right, up, forward), in blocks from the body's position: the pilot's
	 * first, then the passengers' in boarding order.
	 */
	protected abstract double[][] seats();

	/** Whether the body is an interceptor (4.9); false for the courier. */
	public abstract boolean interceptor();

	/** The engines' mouths in ship space: the exhaust and the trail come from them (4.11). */
	public abstract double[][] nozzles();

	/** The twin laser cannons' muzzles in ship space, fired in turn (5.3: W2 fires from them). */
	public abstract double[][] cannons();

	/** The gunner turret's pivot in ship space (5.2: drawn on the model, aimed and fired by W2). */
	public abstract double[] turret();

	/** The missile rack's mouth in ship space (5.3). */
	public abstract double[] rack();

	/** Where riders get in and out, in ship space: the courier's ramp foot, the interceptor's canopy (the ramp's and canopy's sounds). */
	public abstract double[] hatch();

	/** The landing footprint's half width for a class (4.6, 4.9): a little inside the box, so a grass tuft at its edge does not count. */
	public static double footprintHalf(boolean interceptor) {
		return interceptor ? Interceptor.FOOTPRINT_HALF : CourierShuttle.FOOTPRINT_HALF;
	}

	/** The landing footprint's height for a class. */
	public static double footprintHeight(boolean interceptor) {
		return interceptor ? Interceptor.FOOTPRINT_HEIGHT : CourierShuttle.FOOTPRINT_HEIGHT;
	}

	/** Where riders step off a ship of a class, in ship space (right, forward): beside the ramp first, then the sides and the nose. */
	static double[][] dismountSpots(boolean interceptor) {
		return interceptor ? Interceptor.DISMOUNT_SPOTS : CourierShuttle.DISMOUNT_SPOTS;
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

	/** The autopilot's phase, the helm's ({@link ShipRules#PHASE_HELM} once flown by hand), or {@link ShipRules#PHASE_GROUND}. */
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

	/** The owner's UUID as synced, or null before the body has read its record. */
	public @Nullable UUID ownerId() {
		long most = entityData.get(DATA_OWNER_MOST);
		long least = entityData.get(DATA_OWNER_LEAST);
		return most == 0L && least == 0L ? null : new UUID(most, least);
	}

	/** Whether the owner flies the body by hand (the synced {@code DATA_PILOTED} flag, 4.1, 4.2). */
	public boolean piloted() {
		return entityData.get(DATA_PILOTED);
	}

	/** The most hull the ship's class holds (4.9: 100, or the interceptor's), as the server's balance has it. */
	public int maxHull() {
		return entityData.get(DATA_MAX_HULL);
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

	/**
	 * Riders cannot leave and take no damage: from the spool to the end of the settle, and at the
	 * helm, whose states ({@code LAUNCHING}, then {@code FLYING}) are flying ones (M4 4.6, 12.2; M5
	 * 4.1: Sneak means "down" while piloting).
	 */
	public boolean locksRiders() {
		return state().flying() || phase() != ShipRules.PHASE_GROUND;
	}

	/** Copies the record into the synced data, with the phase to show and whether the owner flies it by hand (server). */
	void show(ShipRecord record, int phase, boolean piloted) {
		entityData.set(DATA_STATE, record.state().ordinal());
		entityData.set(DATA_HULL, record.hull());
		entityData.set(DATA_PHASE, phase);
		entityData.set(DATA_CODE, ShipRules.packHullCode(record.hullCode()));
		entityData.set(DATA_UPGRADES, record.upgrades());
		entityData.set(DATA_WEAR, ShipRules.wearLevel(record, ShipBalance.get()));
		entityData.set(DATA_OWNER_MOST, record.owner().getMostSignificantBits());
		entityData.set(DATA_OWNER_LEAST, record.owner().getLeastSignificantBits());
		entityData.set(DATA_PILOTED, piloted);
		entityData.set(DATA_MAX_HULL, ShipRules.maxHull(record));
	}

	void setGear(boolean down) {
		entityData.set(DATA_GEAR, down);
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();

		if (level() instanceof ServerLevel server) {
			checkPilot();

			if (tickCount % SYNC_INTERVAL == 1 || tickCount < 3) {
				Ships.tickBody(server, this);
			}

			return;
		}

		if (piloted() && isLocalInstanceAuthoritative()) {
			fly();
		} else {
			flown = null;

			if (isInterpolating()) {
				// Like boats, a plain entity steps its own InterpolationHandler in tick.
				interpolation.interpolate();
			}
		}

		animate();
	}

	/**
	 * The pilot's client (4.1, 4.3): one tick of flight with the controls the pilot's last
	 * {@code rideTick} gave; the pilot's own tick then sends the new position to the server. The
	 * first tick starts where the server last put the ship. A position changed by anything else since
	 * the last tick is the server's correction (vanilla's {@code handleMoveVehicle} snapped the ship):
	 * it is counted and the ship stops there. With no flight numbers yet the ship holds.
	 */
	private void fly() {
		if (imposed != null) {
			// The server moved the ship itself (a transition, the orbit's edge): fly on from there at its speed.
			motion = imposed;
			imposed = null;
		} else if (flown == null) {
			if (isInterpolating()) {
				snapTo(interpolation.position(), interpolation.yRot(), interpolation.xRot());
			}

			motion = FlightModel.Motion.STILL;
		} else if (position().distanceToSqr(flown) > MOVED_ELSEWHERE) {
			corrections++;
			motion = FlightModel.Motion.STILL;
		}

		if (isInterpolating()) {
			interpolation.cancel();
		}

		FlightModel.Profile numbers = profile;

		if (numbers != null) {
			FlightModel.Controls c = controls != null ? controls : FlightModel.Controls.idle(getYRot(), getXRot());
			FlightModel.Pose pose = FlightModel.turn(new FlightModel.Pose(getYRot(), getXRot()), c, numbers);
			setYRot(pose.yaw());
			setXRot(pose.pitch());
			motion = FlightModel.accelerate(motion, c, numbers);
			Vec3 from = position();
			move(MoverType.SELF, FlightModel.velocity(motion, pose, numbers.cap()));

			if (horizontalCollision || verticalCollision) {
				// Only the speed the ship actually made survives a bump.
				motion = FlightModel.fromDisplacement(position().subtract(from), pose);
			}
		}

		controls = null;
		flown = position();
	}

	/**
	 * The pilot's client: the controls and flight numbers for the next tick, read from the pilot's
	 * input and look at the end of their {@code rideTick} (4.1, 4.3).
	 */
	public void steer(FlightModel.Controls controls, FlightModel.Profile profile) {
		this.controls = controls;
		this.profile = profile;
	}

	/** The pilot's client: how many times the server has corrected this ship's position. */
	public int corrections() {
		return corrections;
	}

	/**
	 * The pilot's client: the server moved the ship itself (a sky or orbit transition, the orbit's
	 * edge, 4.7) and says how it flies on ({@code flight_motion}): the heading is set now, and the
	 * next tick of flight starts from where the ship stands at {@code velocity}, without counting the
	 * move as a correction.
	 */
	public void impose(Vec3 velocity, float yaw) {
		absSnapRotationTo(yaw, getXRot());
		imposed = FlightModel.fromDisplacement(velocity, new FlightModel.Pose(yaw, getXRot()));
		flown = position();
	}

	/**
	 * Server: a piloted ship that moved faster than the cap since its last tick goes back to where it
	 * stood then, and the pilot's client is told (4.1). Vanilla replays each of the pilot's moves
	 * with {@link MoverType#PLAYER} ({@link #move}).
	 */
	private void checkPilot() {
		if (!piloted()) {
			lastGood = null;
			tooFast = false;
			lastAccepted = null;
			acceptedBefore = null;
			return;
		}

		if (tooFast && lastGood != null && getControllingPassenger() instanceof ServerPlayer pilot) {
			absSnapTo(lastGood.x, lastGood.y, lastGood.z, getYRot(), getXRot());
			pilot.connection.send(ClientboundMoveVehiclePacket.fromEntity(this));
			resetMeasure();
		}

		tooFast = false;
		lastGood = position();
	}

	/**
	 * Server: one of the pilot's moves, replayed by vanilla from the last accepted position. A move
	 * longer than the cap is noted for {@link #checkPilot}; the position before it is kept for the
	 * measured speed; after it, a crash is looked for ({@link #checkImpact}).
	 */
	@Override
	public void move(MoverType type, Vec3 movement) {
		if (type != MoverType.PLAYER || level().isClientSide() || !piloted()) {
			super.move(type, movement);
			return;
		}

		double cap = FlightBalance.get().maxSpeedCap * CAP_TOLERANCE;

		if (movement.lengthSqr() > cap * cap) {
			tooFast = true;
		}

		acceptedBefore = lastAccepted;
		lastAccepted = position();
		super.move(type, movement);
		checkImpact();
	}

	/**
	 * Server, after the replay of one of the pilot's moves (4.4): the pilot's client already stopped
	 * the ship at whatever it ran into, so the replay itself seldom collides. The server looks a
	 * little further along each axis the ship moved on before this move: a blocked axis is a crash,
	 * and the speed lost on the blocked axes goes to the helm, beyond {@code flight.impactFloor}
	 * only and at most once in {@link #IMPACT_COOLDOWN} ticks.
	 */
	private void checkImpact() {
		if (acceptedBefore == null || lastAccepted == null || !(level() instanceof ServerLevel server) || server.getGameTime() < impactAfter) {
			return;
		}

		Vec3 before = lastAccepted.subtract(acceptedBefore);
		AABB box = getBoundingBox();
		boolean x = Math.abs(before.x) > PROBE && !server.noCollision(this, box.move(Math.signum(before.x) * PROBE, 0, 0));
		boolean y = before.y < -PROBE && !server.noCollision(this, box.move(0, -PROBE, 0));
		boolean z = Math.abs(before.z) > PROBE && !server.noCollision(this, box.move(0, 0, Math.signum(before.z) * PROBE));
		double lost = FlightRules.speedLost(before, x, y, z);

		if (lost > FlightBalance.get().impactFloor) {
			impactAfter = server.getGameTime() + IMPACT_COOLDOWN;
			Vec3 centre = box.getCenter();
			Vec3 point = new Vec3(x ? before.x > 0 ? box.maxX : box.minX : centre.x, y ? box.minY : centre.y, z ? before.z > 0 ? box.maxZ : box.minZ : centre.z);
			Helm.impact(server, this, lost, point);
		}
	}

	/** Server: the piloted ship's velocity as measured from the last move the server accepted (4.4, 4.6, 4.7); zero when unknown. */
	Vec3 measuredVelocity() {
		return lastAccepted == null ? Vec3.ZERO : position().subtract(lastAccepted);
	}

	/** Server: the server moved the ship itself (a transition, the orbit's edge, a pull-back): the measure starts again. */
	void resetMeasure() {
		lastAccepted = null;
		acceptedBefore = null;
		impactAfter = level().getGameTime() + IMPACT_COOLDOWN;
	}

	/** Client: counts the ticks since the phase, state and struts last changed. */
	private void animate() {
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

	/** Moves the body along a path the server flies (server). */
	void flyTo(Vec3 pos, float yaw, float pitch) {
		setPos(pos);
		setYRot(yaw);
		setXRot(pitch);
	}

	/** A flying vehicle: vanilla never counts its pilot as floating (4.1). */
	@Override
	public boolean isFlyingVehicle() {
		return true;
	}

	/** No portal takes a ship: it flies only in its planets' skies and orbits (4.4). Overrides {@code Entity#canUsePortal(boolean)}. */
	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	/** A ship never falls out of the world (4.10): it goes back to its last landing spot with its riders. */
	@Override
	protected void onBelowWorld() {
		if (level() instanceof ServerLevel server && Ships.belowWorld(server, this)) {
			return;
		}

		super.onBelowWorld();
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

	/** No fall hurts the ship or, through it, its riders: a hand-flown ship can come down hard (4.4). */
	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
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

	/**
	 * The owner aboard while the piloted flag is set (4.1): derived from the synced owner and flag,
	 * never from the order of the passengers. Null otherwise, so nobody's client moves the body.
	 */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		UUID owner = piloted() ? ownerId() : null;

		if (owner != null) {
			for (Entity passenger : getPassengers()) {
				if (passenger instanceof Player player && owner.equals(player.getUUID())) {
					return player;
				}
			}
		}

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

	/**
	 * The player in the gunner seat (5.2), or null: on a flying ship with cannons, the first rider
	 * who is not the owner (the courier's seat 1, the interceptor's second seat).
	 */
	public @Nullable Player gunner() {
		if ((upgrades() & ShipRecord.UPGRADE_CANNONS) == 0 || state() != ShipState.FLYING) {
			return null;
		}

		for (Entity passenger : getPassengers()) {
			if (passenger instanceof Player player && seatOf(player) == GUNNER_SEAT) {
				return player;
			}
		}

		return null;
	}

	/** The seat of {@code passenger}: the owner flies, the others fill the passenger seats in boarding order. */
	int seatOf(Entity passenger) {
		UUID owner = ownerId();

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

		return Math.min(seat, seats().length - 1);
	}

	/** The seat in ship space, turned with the hull's yaw and pitch. */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		double[] seat = seats()[seatOf(passenger)];
		return local(seat[0], seat[1], seat[2], getYRot(), getXRot());
	}

	/** Beside the ramp, where riders step off (M4 4.6). */
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

	/** {@link #local(double, double, double, float)} for a hull pitched by {@code pitch} (positive: nose down, as Minecraft's pitch). */
	public static Vec3 local(double right, double up, double forward, float yaw, float pitch) {
		double rad = Math.toRadians(pitch);
		double sin = Math.sin(rad);
		double cos = Math.cos(rad);
		// Nose down: points ahead of the centre drop, and the hull's up leans forward.
		return local(right, up * cos - forward * sin, forward * cos + up * sin, yaw);
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
