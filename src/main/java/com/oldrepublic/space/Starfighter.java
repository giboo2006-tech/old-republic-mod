package com.oldrepublic.space;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Planets;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An enemy (or allied) starfighter (spec MILESTONE_5.md 5.5): a gravity-free {@link Mob} flown the
 * Phantom way, steering its velocity toward a point rather than walking a path. It circles its
 * target, makes an attack run on it and breaks away close in, then circles again; it fires only
 * while its nose is within {@code space.fighterAimCone} of where the target will be, and its bolts
 * spread {@code space.fighterSpread}. Each kind sets its own runs ({@link Flight}): the Hymn
 * interceptor dives from above, the kajidic raider strafes from the side, the Hask fighter keeps its
 * trio in formation.
 *
 * <p>Health is split into a recharging shield (synced, for the combat HUD's readout) and the hull,
 * which is the mob's health. Only {@code ship_weapon} damage hurts it, without the vanilla hurt
 * cooldown, so two cannons and a turret all land. It is never saved: its encounter rebuilds it, and
 * the encounter decides when it vanishes ({@link SpaceEncounters}). A destroyed fighter is gone at
 * once; the client plays its explosion from the {@code ship_destroyed} event, and
 * {@link SpaceRewards} pays the player whose bolt or missile finished it. Nothing drops.
 *
 * <p>Its side ({@link SpaceRules.Side}) is synced: hostile fighters attack the players' armed ships
 * (only those of their encounter's owner and co-op crews when they have one) and allied fighters;
 * allied fighters attack hostile ship targets; neutral fighters (a scanning patrol) shadow their
 * encounter's ship and turn hostile when a player's weapon hits them. A fighter of an encounter only
 * ever fights the ships of that encounter ({@link ShipBolt#lands}).
 */
public abstract class Starfighter extends Mob {
	private static final EntityDataAccessor<Integer> DATA_SIDE = SynchedEntityData.defineId(Starfighter.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_SHIELD = SynchedEntityData.defineId(Starfighter.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_SHIELD_MAX = SynchedEntityData.defineId(Starfighter.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_ACE = SynchedEntityData.defineId(Starfighter.class, EntityDataSerializers.BOOLEAN);

	/**
	 * How a kind flies (5.5): the circle it holds around its target (radius and height above it), how
	 * long it circles before a run (ticks), how close a run goes before breaking away, its turn rate
	 * (degrees per tick) and whether wingmen hold formation on their leader while circling.
	 */
	protected record Flight(double radius, double height, int circleMin, int circleMax, double breakDistance, float turn, boolean formation) {
	}

	/** What the fighter is doing. */
	public enum Phase {
		CIRCLE,
		RUN,
		BREAK
	}

	/** Attack runs give up after this long. */
	private static final int RUN_MAX_TICKS = 140;
	private static final int BREAK_TICKS_MIN = 22;
	private static final int BREAK_TICKS_MAX = 36;
	/** Targets farther than this are dropped; a new one is looked for within {@link #SEEK_RANGE}. */
	private static final double LOSE_RANGE = 220;
	private static final double SEEK_RANGE = 160;
	private static final double ALLY_RANGE = 96;
	/** A target is kept at least this long before a nearer one may take its place. */
	private static final int TARGET_HOLD_TICKS = 100;
	/** In an orbit, fighters stay between these heights (the atmosphere line is at 64, the ceiling at 320). */
	private static final double BAND_LOW = 76;
	private static final double BAND_HIGH = 306;
	/** Blocks looked ahead for an asteroid, at least, and in ticks of flight. */
	private static final double LOOK_AHEAD_MIN = 8;
	private static final double LOOK_AHEAD_TICKS = 7;
	private static final double ACCELERATION = 0.06;
	private static final double DECELERATION = 0.08;
	/** An idle fighter (no target) circles its post this wide at this share of its speed. */
	private static final double IDLE_RADIUS = 40;
	private static final double IDLE_SPEED = 0.6;
	/** Its guns sit this far out on each side and ahead of its centre (blocks). */
	private static final double MUZZLE_SIDE = 1.1;
	private static final double MUZZLE_AHEAD = 1.6;

	private boolean setUp;
	private SpaceBalance.Fighter numbers = new SpaceBalance.Fighter();
	private float shield;
	private long lastHit = Long.MIN_VALUE / 4;
	/** An ace's guns fire this much faster (5.8). */
	private float fireRate = 1f;

	private Phase phase = Phase.CIRCLE;
	/** Its circles and runs at this share of their size (1; game tests fly them smaller, in a small sky). */
	private double flightScale = 1.0;
	private int phaseTicks;
	private int phaseLength = 60;
	private Vec3 heading = new Vec3(0, 0, 1);
	private double speed;
	private double orbitAngle;
	private int orbitDirection = 1;
	private Vec3 breakDirection = new Vec3(0, 1, 0);
	private @Nullable Vec3 post;

	private @Nullable UUID targetId;
	private int targetTicks;
	/** A target set from outside (an encounter's first target, or a test), kept while it is valid. */
	private @Nullable UUID engaged;
	private @Nullable Vec3 lastTargetPos;
	private Vec3 targetVelocity = Vec3.ZERO;

	private @Nullable UUID leader;
	private int wingSlot;
	private boolean departing;

	private long nextShot;
	private int burstLeft;
	private int muzzle;
	private long nextMissile;
	private int lockTicks;
	private @Nullable UUID warnedPilot;

	/** Client: the bank angle drawn (degrees), from how fast the fighter turns. */
	private float bank;
	private float bankO;

	protected Starfighter(EntityType<? extends Starfighter> type, Level level) {
		super(type, level);
		setNoGravity(true);
		this.xpReward = 0;
		this.lookControl = new FixedLook(this);
	}

	/**
	 * A look control that leaves the fighter's rotation alone: the vanilla one levels the pitch every
	 * tick, and the fighter faces its own heading (as the Phantom's control does).
	 */
	static final class FixedLook extends LookControl {
		FixedLook(Mob mob) {
			super(mob);
		}

		@Override
		public void tick() {
		}
	}

	/** Default attributes; {@link #setup} sets the hull from the balance. */
	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.FOLLOW_RANGE, SEEK_RANGE)
				.add(Attributes.FLYING_SPEED, 1.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	/** Its reward row and its numbers. */
	public abstract SpaceRules.Kind kind();

	/** The colour of its bolts. */
	protected abstract ShipBolt.Style boltStyle();

	/** How it flies its runs. */
	protected abstract Flight flight();

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SIDE, SpaceRules.Side.HOSTILE.ordinal());
		builder.define(DATA_SHIELD, 0f);
		builder.define(DATA_SHIELD_MAX, 0f);
		builder.define(DATA_ACE, false);
	}

	// ---------------------------------------------------------------- setting up

	/**
	 * Gives the fighter its numbers (5.5): its kind's shield and hull, both multiplied for an ace
	 * (whose guns also fire faster, 5.8), its side, and full health. Called before it is added to the
	 * level; a fighter from a spawn egg sets itself up hostile on its first tick.
	 */
	public void setup(SpaceRules.Side side, boolean ace, SpaceBalance b) {
		setUp = true;
		numbers = SpaceRules.fighter(kind(), b);
		float multiplier = ace ? Math.max(1f, b.aceMultiplier) : 1f;
		fireRate = multiplier;
		AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);

		if (health != null) {
			health.setBaseValue(Math.max(1f, numbers.hull * multiplier));
		}

		setHealth(getMaxHealth());
		shield = numbers.shield * multiplier;
		entityData.set(DATA_SHIELD_MAX, shield);
		entityData.set(DATA_SHIELD, shield);
		entityData.set(DATA_ACE, ace);
		setSide(side);
		speed = numbers.speed * 0.5;
		orbitDirection = getRandom().nextBoolean() ? 1 : -1;
		phaseLength = circleLength();
	}

	/** Points the fighter along {@code direction} (a unit vector) at {@code fraction} of its speed: a launch or an arrival. */
	public void launchAlong(Vec3 direction, double fraction) {
		heading = direction.lengthSqr() > 1.0e-6 ? direction.normalize() : heading;
		speed = numbers.speed * Mth.clamp(fraction, 0, 1);
		face(heading);
		setDeltaMovement(heading.scale(speed));
	}

	/** Wingman {@code slot} (1 or 2) of {@code leader}'s formation while circling. */
	public void follow(@Nullable UUID leader, int slot) {
		this.leader = leader;
		this.wingSlot = slot;
	}

	/** Attacks {@code target} first (an encounter's ship, a test's mark); null clears it. */
	public void engage(@Nullable Entity target) {
		engaged = target == null ? null : target.getUUID();
		targetId = engaged;
		targetTicks = 0;
	}

	/** Where an idle fighter circles; by default where it first had no target. */
	public void setPost(@Nullable Vec3 post) {
		this.post = post;
	}

	/** Breaks off for good and flies to {@code point} (a pass turning away, a broken blockade's pilots fleeing); it fires no more. */
	public void depart(Vec3 point) {
		departing = true;
		post = point;
		engaged = null;
		targetId = null;
		phase = Phase.CIRCLE;
	}

	/** Whether it has broken off for good. */
	public boolean departing() {
		return departing;
	}

	public SpaceRules.Side side() {
		return SpaceRules.Side.of(entityData.get(DATA_SIDE));
	}

	public void setSide(SpaceRules.Side side) {
		entityData.set(DATA_SIDE, side.ordinal());
	}

	/** The shield's points now (synced). */
	public float shield() {
		return entityData.get(DATA_SHIELD);
	}

	public float shieldMax() {
		return entityData.get(DATA_SHIELD_MAX);
	}

	/** A bounty's ace (synced: its name tag and the HUD). */
	public boolean ace() {
		return entityData.get(DATA_ACE);
	}

	/** Whether it is attacking, breaking or circling (tests). */
	public Phase phase() {
		return phase;
	}

	/**
	 * Game tests: its circle, the height it holds above the target and the distance at which a run
	 * breaks off, at {@code scale} of its kind's (0.05 to 1), so a whole run fits a test's sky. The
	 * timing and the turn rate stay its kind's.
	 */
	public void scaleFlight(double scale) {
		flightScale = Mth.clamp(scale, 0.05, 1.0);
	}

	/** Its kind's flight at its scale. */
	private Flight flown() {
		Flight f = flight();
		return flightScale >= 1.0 ? f
				: new Flight(f.radius() * flightScale, f.height() * flightScale, f.circleMin(), f.circleMax(), f.breakDistance() * flightScale, f.turn(),
						f.formation());
	}

	/** Its target now, or null. Server side. */
	public @Nullable UUID targetId() {
		return targetId;
	}

	/** Client: the bank to draw at {@code partialTick} (degrees, positive to the right). */
	public float bank(float partialTick) {
		return Mth.lerp(partialTick, bankO, bank);
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			bankO = bank;
			float turned = Mth.wrapDegrees(getYRot() - yRotO);
			bank += (Mth.clamp(turned * 6f, -55f, 55f) - bank) * 0.25f;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		SpaceBalance b = SpaceBalance.get();

		if (!setUp) {
			setup(SpaceRules.Side.HOSTILE, false, b);
		}

		rechargeShield(level, b);
		Entity target = target(level);
		track(target);
		steer(level, target, b);
		shoot(level, target, b);
	}

	/** Flies the velocity the last tick's steering set: no gravity, no drag (space). */
	@Override
	public void travel(Vec3 input) {
		move(MoverType.SELF, getDeltaMovement());
	}

	private void rechargeShield(ServerLevel level, SpaceBalance b) {
		float max = shieldMax();
		float next = SpaceRules.recharge(shield, max, level.getGameTime() - lastHit, b.fighterShieldDelay, b.fighterShieldRecharge);

		if (next != shield) {
			shield = next;
			entityData.set(DATA_SHIELD, shield);
		}
	}

	// ---------------------------------------------------------------- targets

	/** The target now: the engaged one while valid, else the current one for a while, else the nearest; none once departing. */
	private @Nullable Entity target(ServerLevel level) {
		if (departing) {
			return null;
		}

		Entity current = targetId == null ? null : level.getEntity(targetId);
		boolean keep = current != null && valid(current) && (current.getUUID().equals(engaged) || targetTicks++ < TARGET_HOLD_TICKS);

		if (keep) {
			return current;
		}

		if (engaged != null && (current == null || !current.getUUID().equals(engaged))) {
			Entity mark = level.getEntity(engaged);

			if (mark != null && valid(mark)) {
				targetId = engaged;
				return mark;
			}

			engaged = null;
		}

		Entity found = seek(level);
		targetId = found == null ? null : found.getUUID();
		targetTicks = 0;
		return found;
	}

	private boolean valid(Entity target) {
		return target.isAlive() && !target.isRemoved() && target.level() == level() && target.distanceToSqr(this) < LOSE_RANGE * LOSE_RANGE;
	}

	/** The nearest enemy: a player's armed ship (of this fighter's encounter, if it has one) or an allied fighter; for allies, a hostile ship target. */
	private @Nullable Entity seek(ServerLevel level) {
		SpaceRules.Side side = side();

		if (side == SpaceRules.Side.ALLIED) {
			return nearestShipTarget(level, SpaceRules.Side.HOSTILE, SEEK_RANGE);
		}

		Set<UUID> pilots = SpaceEncounters.participants(this);
		Entity best = null;
		double bestDistance = SEEK_RANGE * SEEK_RANGE;

		for (ServerPlayer player : level.players()) {
			if (pilots != null && !pilots.contains(player.getUUID())) {
				continue;
			}

			if (!(player.getVehicle() instanceof ShipBody ship) || !ship.piloted() || ship.getControllingPassenger() != player) {
				continue;
			}

			// An unarmed courier is never attacked (5.6); an encounter's own pilots are armed by its rules.
			if (pilots == null && (ship.upgrades() & ShipRecord.UPGRADE_CANNONS) == 0) {
				continue;
			}

			double distance = ship.distanceToSqr(this);

			if (distance < bestDistance) {
				best = ship;
				bestDistance = distance;
			}
		}

		if (side == SpaceRules.Side.HOSTILE) {
			Entity ally = nearestShipTarget(level, SpaceRules.Side.ALLIED, ALLY_RANGE);

			if (ally != null && ally.distanceToSqr(this) < bestDistance) {
				return ally;
			}
		}

		return best;
	}

	/**
	 * The nearest ship target of {@code side} within {@code range}: fighters, and for hostile ones the
	 * gunship and its generator; only those of this fighter's own encounter (or of none, without one).
	 */
	private @Nullable Entity nearestShipTarget(ServerLevel level, SpaceRules.Side side, double range) {
		AABB box = getBoundingBox().inflate(range);
		UUID battle = SpaceEncounters.ownerOf(this);
		Entity best = null;
		double bestDistance = range * range;

		for (Mob mob : level.getEntitiesOfClass(Mob.class, box,
				e -> e != this && e.isAlive() && e.is(SpaceEntities.SHIP_TARGETS) && Objects.equals(SpaceEncounters.ownerOf(e), battle))) {
			boolean ofSide = mob instanceof Starfighter fighter ? fighter.side() == side : side == SpaceRules.Side.HOSTILE;
			double distance = mob.distanceToSqr(this);

			if (ofSide && distance < bestDistance) {
				best = mob;
				bestDistance = distance;
			}
		}

		return best;
	}

	/** The target's velocity, measured from its position each tick (a piloted ship's velocity lives on its pilot's client). */
	private void track(@Nullable Entity target) {
		if (target == null) {
			lastTargetPos = null;
			targetVelocity = Vec3.ZERO;
			return;
		}

		Vec3 now = target.position();
		targetVelocity = lastTargetPos == null ? target.getDeltaMovement() : now.subtract(lastTargetPos);

		if (targetVelocity.lengthSqr() > 9) {
			// A teleport or a correction, not flight.
			targetVelocity = Vec3.ZERO;
		}

		lastTargetPos = now;
	}

	// ---------------------------------------------------------------- flight

	private void steer(ServerLevel level, @Nullable Entity target, SpaceBalance b) {
		Flight f = flown();
		Vec3 pos = position();
		double top = numbers.speed;
		Vec3 wanted;

		if (target == null) {
			if (post == null) {
				post = pos;
			}

			if (departing) {
				wanted = direction(post.subtract(pos));
			} else {
				orbitAngle += orbitDirection * top * IDLE_SPEED / IDLE_RADIUS;
				Vec3 point = post.add(Math.cos(orbitAngle) * IDLE_RADIUS, 0, Math.sin(orbitAngle) * IDLE_RADIUS);
				wanted = direction(point.subtract(pos));
				top *= IDLE_SPEED;
			}

			phase = Phase.CIRCLE;
		} else {
			wanted = attack(level, target, pos, top, f, b);
		}

		wanted = avoid(level, pos, wanted);
		heading = SpaceRules.turnToward(heading, wanted, f.turn());
		double off = Math.acos(Mth.clamp(heading.dot(wanted), -1, 1));
		double goal = off > 0.8 ? top * 0.75 : top;
		speed += Mth.clamp(goal - speed, -DECELERATION, ACCELERATION);

		if (horizontalCollision || verticalCollision) {
			// Struck something: climb out and come round again.
			heading = direction(heading.scale(-0.4).add(0, 1, 0));
			startBreak(heading);
		}

		setDeltaMovement(heading.scale(speed));
		face(heading);
	}

	/** The wanted direction while it has a target: circling, the run (aimed at the lead point) or the break. */
	private Vec3 attack(ServerLevel level, Entity target, Vec3 pos, double top, Flight f, SpaceBalance b) {
		Vec3 aimAt = target.getBoundingBox().getCenter();
		double distance = aimAt.distanceTo(pos);
		phaseTicks++;

		switch (phase) {
			case RUN -> {
				Vec3 lead = SpaceRules.lead(pos, aimAt, targetVelocity, b.boltSpeed);

				if (distance < f.breakDistance() || phaseTicks > RUN_MAX_TICKS) {
					Vec3 side = heading.cross(new Vec3(0, 1, 0));
					side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
					startBreak(direction(heading.add(side.scale(getRandom().nextBoolean() ? 0.9 : -0.9)).add(0, 0.55, 0)));
				}

				return direction(lead.subtract(pos));
			}
			case BREAK -> {
				if (phaseTicks > phaseLength) {
					startCircle();
				}

				return breakDirection;
			}
			default -> {
				Starfighter lead = formationLeader(level);

				if (lead != null) {
					// Hold the wing slot: beside and behind the leader, matching its heading.
					Vec3 right = lead.heading.cross(new Vec3(0, 1, 0));
					right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
					Vec3 slot = lead.position().add(right.scale(wingSlot == 1 ? 7 : -7)).subtract(lead.heading.scale(6)).add(0, wingSlot == 1 ? 1.5 : -1.5, 0);
					Vec3 toSlot = slot.subtract(pos);
					phase = lead.phase == Phase.RUN ? Phase.RUN : Phase.CIRCLE;
					phaseTicks = 0;
					return toSlot.lengthSqr() < 16 ? lead.heading : direction(toSlot.scale(0.25).add(lead.heading));
				}

				orbitAngle += orbitDirection * top / Math.max(8, f.radius());
				double radius = side() == SpaceRules.Side.NEUTRAL ? Math.min(30, f.radius()) : f.radius();
				Vec3 point = aimAt.add(Math.cos(orbitAngle) * radius, side() == SpaceRules.Side.NEUTRAL ? 6 : f.height(), Math.sin(orbitAngle) * radius);

				if (phaseTicks > phaseLength && side() != SpaceRules.Side.NEUTRAL) {
					phase = Phase.RUN;
					phaseTicks = 0;
				}

				return direction(point.subtract(pos));
			}
		}
	}

	/** The leader this wingman holds formation on while circling, when it flies in formation and the leader is alive. */
	private @Nullable Starfighter formationLeader(ServerLevel level) {
		if (!flown().formation() || leader == null) {
			return null;
		}

		Entity lead = level.getEntity(leader);
		return lead instanceof Starfighter fighter && fighter.isAlive() && fighter != this && fighter.distanceToSqr(this) < 80 * 80 ? fighter : null;
	}

	private void startBreak(Vec3 direction) {
		phase = Phase.BREAK;
		phaseTicks = 0;
		phaseLength = BREAK_TICKS_MIN + getRandom().nextInt(BREAK_TICKS_MAX - BREAK_TICKS_MIN + 1);
		breakDirection = direction;
	}

	private void startCircle() {
		phase = Phase.CIRCLE;
		phaseTicks = 0;
		phaseLength = circleLength();
		orbitAngle = Math.atan2(getZ() - (lastTargetPos == null ? getZ() : lastTargetPos.z), getX() - (lastTargetPos == null ? getX() : lastTargetPos.x));

		if (getRandom().nextInt(3) == 0) {
			orbitDirection = -orbitDirection;
		}
	}

	private int circleLength() {
		Flight f = flown();
		return f.circleMin() + getRandom().nextInt(Math.max(1, f.circleMax() - f.circleMin() + 1));
	}

	/** {@code wanted} bent away from a block ahead and back into the orbit's band. */
	private Vec3 avoid(ServerLevel level, Vec3 pos, Vec3 wanted) {
		Vec3 ahead = pos.add(heading.scale(Math.max(LOOK_AHEAD_MIN, speed * LOOK_AHEAD_TICKS)));
		BlockHitResult hit = level.clip(new ClipContext(pos, ahead, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 result = wanted;

		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
			result = direction(result.add(normal.scale(1.6)).add(0, 0.6, 0));
		}

		// The gunship is no block, so it is looked for on its own: climb over it, away from its middle.
		BlockadeGunship.HullHit hull = BlockadeGunship.firstHullHit(level, pos, ahead, gunship -> true);

		if (hull != null) {
			Vec3 away = pos.subtract(hull.gunship().position());
			result = direction(result.add(direction(new Vec3(away.x, 0, away.z)).scale(0.8)).add(0, 1.4, 0));
		}

		if (Planets.isOrbit(level)) {
			if (pos.y < BAND_LOW) {
				result = direction(result.add(0, 1.2, 0));
			} else if (pos.y > BAND_HIGH) {
				result = direction(result.add(0, -1.2, 0));
			}
		}

		return result;
	}

	private void face(Vec3 dir) {
		float yaw = SpaceRules.yawOf(dir);
		setYRot(yaw);
		setXRot(SpaceRules.pitchOf(dir));
		setYHeadRot(yaw);
		setYBodyRot(yaw);
	}

	private static Vec3 direction(Vec3 v) {
		return v.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1) : v.normalize();
	}

	/** Its heading (a unit vector). */
	public Vec3 heading() {
		return heading;
	}

	// ---------------------------------------------------------------- guns

	private void shoot(ServerLevel level, @Nullable Entity target, SpaceBalance b) {
		if (target == null || side() == SpaceRules.Side.NEUTRAL) {
			endLock(level);
			return;
		}

		long now = level.getGameTime();
		Vec3 aimAt = target.getBoundingBox().getCenter();
		Vec3 lead = SpaceRules.lead(position(), aimAt, targetVelocity, b.boltSpeed);
		boolean inCone = SpaceRules.inCone(position(), heading, lead, b.fighterAimCone, b.boltRange);

		if (inCone && now >= nextShot) {
			int[] timing = SpaceRules.burstTiming(numbers.rate * fireRate, numbers.burst);

			if (burstLeft <= 0) {
				burstLeft = Math.max(1, numbers.burst);
			}

			fireBolt(level, lead, b);
			burstLeft--;
			nextShot = now + (burstLeft > 0 ? timing[0] : Math.max(timing[0], timing[1] - timing[0] * (Math.max(1, numbers.burst) - 1)));
		} else if (!inCone && burstLeft > 0 && now >= nextShot) {
			// The burst is broken off when the nose leaves the target.
			burstLeft = 0;
		}

		missile(level, target, b, now);
	}

	private void fireBolt(ServerLevel level, Vec3 aimAt, SpaceBalance b) {
		Vec3 right = heading.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		muzzle ^= 1;
		Vec3 from = getBoundingBox().getCenter().add(heading.scale(MUZZLE_AHEAD)).add(right.scale(muzzle == 0 ? MUZZLE_SIDE : -MUZZLE_SIDE));
		Vec3 dir = SpaceRules.spread(direction(aimAt.subtract(from)), b.fighterSpread, getRandom());
		ShipBolt.Team team = side() == SpaceRules.Side.ALLIED ? ShipBolt.Team.ALLIED : ShipBolt.Team.HOSTILE;
		ShipBolt.fire(level, this, null, team, boltStyle(), from, dir.scale(b.boltSpeed).add(getDeltaMovement()), numbers.damage, b.boltRange);
		EffectEvents.toTracking(this, SpaceEffects.SHIP_WEAPON, from, dir, getId(), 1f, SpaceEffects.WEAPON_FIGHTER_LASERS);
	}

	/**
	 * A kind with missiles (the kajidic raider, 5.5) locks a player's ship that stays in its lock cone
	 * for {@code space.lockTicks} (the pilot hears the warning from the start of the lock), then fires
	 * one; then waits {@code missileInterval} ticks.
	 */
	private void missile(ServerLevel level, Entity target, SpaceBalance b, long now) {
		if (numbers.missileInterval <= 0 || now < nextMissile || !(target instanceof ShipBody ship) || side() != SpaceRules.Side.HOSTILE) {
			if (!(target instanceof ShipBody)) {
				endLock(level);
			}

			return;
		}

		ServerPlayer pilot = ship.getControllingPassenger() instanceof ServerPlayer p ? p : null;
		boolean locking = pilot != null && SpaceRules.inCone(position(), heading, ship.getBoundingBox().getCenter(), b.lockCone, b.lockRange);

		if (!locking) {
			endLock(level);
			return;
		}

		if (lockTicks++ == 0) {
			warnedPilot = pilot.getUUID();
			ShipWeapons.incomingStarted(pilot);
		}

		if (!SpaceRules.lockHeld(lockTicks, b)) {
			return;
		}

		Vec3 from = getBoundingBox().getCenter().add(heading.scale(MUZZLE_AHEAD)).add(0, -0.4, 0);
		ShipMissile.launch(level, this, null, ShipBolt.Team.HOSTILE, from, heading, ship, numbers.missileDamage, 0f, pilot.getUUID(), b);
		EffectEvents.toTracking(this, SpaceEffects.SHIP_WEAPON, from, heading, getId(), 1f, SpaceEffects.WEAPON_FIGHTER_MISSILE);
		// The missile keeps the warning on now: this lock hands it over without ending it.
		warnedPilot = null;
		lockTicks = 0;
		nextMissile = now + numbers.missileInterval;
	}

	/** A lock broken before the missile flew: the pilot's warning stops. */
	private void endLock(ServerLevel level) {
		lockTicks = 0;

		if (warnedPilot != null) {
			ServerPlayer pilot = level.getServer().getPlayerList().getPlayer(warnedPilot);
			warnedPilot = null;

			if (pilot != null) {
				ShipWeapons.incomingEnded(pilot);
			}
		}
	}

	// ---------------------------------------------------------------- damage

	/**
	 * Only ship weapons hurt it (and {@code /kill}): the shield takes the hit first, the rest goes to
	 * the hull, with no hurt cooldown. A neutral fighter shot by a player turns hostile.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}

		if (!source.is(SpaceEntities.SHIP_WEAPON) || isDeadOrDying()) {
			return false;
		}

		lastHit = level.getGameTime();
		Entity direct = source.getDirectEntity();
		Vec3 at = direct != null ? direct.position() : getBoundingBox().getCenter();
		UUID shooter = SpaceRewards.shooterOf(source);

		if (side() == SpaceRules.Side.NEUTRAL && shooter != null) {
			setSide(SpaceRules.Side.HOSTILE);
			SpaceEncounters.onProvoked(this);
		}

		float absorbed = SpaceRules.absorbed(shield, amount);
		shield -= absorbed;
		entityData.set(DATA_SHIELD, shield);
		float rest = amount - absorbed;
		Vec3 normal = at.subtract(getBoundingBox().getCenter());
		EffectEvents.around(level, SpaceEffects.SHIP_HIT, at, normal.lengthSqr() > 1.0e-6 ? normal.normalize() : null, getId(), amount,
				rest > 0f ? SpaceEffects.HIT_HULL : SpaceEffects.HIT_SHIELD);

		if (rest <= 0f) {
			return true;
		}

		boolean hurt = super.hurtServer(level, source, rest);
		this.invulnerableTime = 0;
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		boolean first = !this.dead;
		super.die(source);

		if (first && level() instanceof ServerLevel level) {
			endLock(level);
			EffectEvents.around(level, SpaceEffects.SHIP_DESTROYED, getBoundingBox().getCenter(), heading, getId(), 1f, SpaceEffects.DESTROYED_FIGHTER);
			SpaceRewards.onDestroyed(level, this, kind(), source);
		}
	}

	/** Gone at once: the explosion is the client's (the {@code ship_destroyed} event). */
	@Override
	protected void tickDeath() {
		if (!level().isClientSide() && !isRemoved()) {
			discard();
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel level) {
			endLock(level);
		}

		super.remove(reason);
	}

	/** Nothing drops in space (items would fall forever, 5.5). */
	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	/** Its encounter decides when it leaves. */
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

	/** Its engine is a client loop and its hits are the {@code ship_hit} event: no vanilla mob sounds. */
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
}
