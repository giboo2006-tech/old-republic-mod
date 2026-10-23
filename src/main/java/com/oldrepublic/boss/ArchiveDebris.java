package com.oldrepublic.boss;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.combat.DeflectableProjectile;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactiveEntity;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.saber.GuardDrainSource;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A chunk of the Archive's debris for Orren Vasch's Archive Hurl (spec 8.3, 10.6). It lives through
 * {@link State}s:
 * <ul>
 * <li>{@link State#HELD}: torn from a debris pile during the 24-tick tell, it drifts to a point above
 * his raised off-hand. A Pull rips it away ({@link #onForcePull}: it falls between them and he
 * staggers); a Lift catches it ({@link #onForceLift}).</li>
 * <li>{@link State#FLYING}: thrown at {@code hurlSpeed}. A saber guard only blocks it (drain
 * {@code hurlGuardDrain}, it crumbles): it is never deflected. A Push within {@code hurlReturnRange}
 * returns it.</li>
 * <li>{@link State#CAUGHT}: hangs in the catcher's Lift; on release it drops harmlessly, or, with the
 * {@code lift_hurl} node, flies back at him.</li>
 * <li>{@link State#RETURNED}: flies back at Vasch and hits only him, for
 * {@code hurlDamage x hurlReturnMultiplier} and a {@code hurlReturnStaggerTicks} stagger.</li>
 * <li>{@link State#FALLING}: harmless; crumbles where it lands.</li>
 * </ul>
 * Never saved ({@code noSave}). Crumbling sends {@code boss_hurl} variant 2 with intensity 0 (a dust
 * burst, no rip sound).
 */
public class ArchiveDebris extends ThrowableItemProjectile implements DeflectableProjectile, ForceReactiveEntity, GuardDrainSource {
	/** The chunk's state. Synced by ordinal; append only. */
	public enum State {
		HELD,
		FLYING,
		CAUGHT,
		FALLING,
		RETURNED;

		static State of(int ordinal) {
			State[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}
	}

	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(ArchiveDebris.class, EntityDataSerializers.INT);
	/** Gravity in flight: a nearly flat throw. */
	private static final double FLIGHT_GRAVITY = 0.012;
	private static final double FALL_GRAVITY = 0.06;
	/** How fast a held or caught chunk closes on its hold point, per tick (fraction of the gap). */
	private static final double HOLD_STEER = 0.22;
	/** The longest any state may last before the chunk crumbles on its own. */
	private static final int MAX_STATE_TICKS = 200;
	/** A Lift channel that has not touched the chunk for this many ticks has ended. */
	private static final int LIFT_GRACE_TICKS = 2;
	/** Speed of a chunk returned by Push or by a Lift hurl, as a multiple of {@code hurlSpeed}. */
	private static final double RETURN_SPEED_MULT = 1.3;

	private float damage;
	/** Vasch, who tore the chunk loose; the projectile owner changes to whoever sends it back. Server side. */
	private @Nullable LivingEntity thrower;
	private @Nullable Vec3 holdPoint;
	private int stateTicks;
	private @Nullable ServerPlayer catcher;
	private long lastLiftTick;

	public ArchiveDebris(EntityType<? extends ArchiveDebris> type, Level level) {
		super(type, level);
	}

	/**
	 * A debris chunk at {@code pos}, held (not moving) and not yet added to the level; the caller sets
	 * its hold point or velocity and adds it.
	 */
	public static ArchiveDebris create(ServerLevel level, @Nullable LivingEntity thrower, Vec3 pos, float damage) {
		ArchiveDebris debris = new ArchiveDebris(BossEntities.ARCHIVE_DEBRIS, level);
		debris.setPos(pos);
		debris.damage = damage;
		debris.holdPoint = pos;
		debris.setNoGravity(true);

		if (thrower != null) {
			debris.thrower = thrower;
			debris.setOwner(thrower);
		}

		return debris;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, State.HELD.ordinal());
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COBBLESTONE;
	}

	public State state() {
		return State.of(this.entityData.get(DATA_STATE));
	}

	private void setState(State state) {
		this.entityData.set(DATA_STATE, state.ordinal());
		stateTicks = 0;
		setNoGravity(state == State.HELD || state == State.CAUGHT);
	}

	/** The damage the chunk deals when thrown. */
	public float damage() {
		return damage;
	}

	/** Where a held chunk drifts to (above his raised hand); the Hurl updates it every tick. */
	public void setHoldPoint(Vec3 point) {
		holdPoint = point;
	}

	/** Vasch, who tore the chunk loose, while he is alive and in the level (server side). */
	public @Nullable LivingEntity thrower() {
		return thrower != null && thrower.isAlive() && !thrower.isRemoved() ? thrower : null;
	}

	/** Throws a held chunk along {@code velocity} (the end of the Hurl tell). */
	public void launch(Vec3 velocity) {
		if (state() != State.HELD) {
			return;
		}

		setState(State.FLYING);
		setDeltaMovement(velocity);
		hurtMarked = true;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		State state = state();

		switch (state) {
			case HELD, CAUGHT -> hoverTick(state);
			case FALLING -> fallTick();
			default -> super.tick();
		}

		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return;
		}

		if (++stateTicks > MAX_STATE_TICKS) {
			crumble(level);
			return;
		}

		if (state == State.HELD && thrower() == null) {
			// He is gone (a reset, a transition): the chunk drops.
			setState(State.FALLING);
		} else if (state == State.CAUGHT && level.getGameTime() - lastLiftTick > LIFT_GRACE_TICKS) {
			releaseCatch(level);
		}
	}

	/** Held or caught: no physics, it steers toward its hold point (a caught chunk stays where it was caught). */
	private void hoverTick(State state) {
		baseTick();

		if (level().isClientSide()) {
			return;
		}

		Vec3 target = state == State.HELD && holdPoint != null ? holdPoint : position();
		Vec3 step = target.subtract(position()).scale(HOLD_STEER);
		setDeltaMovement(step);
		setPos(position().add(step));
	}

	/** Falling: gravity and block collisions; crumbles on landing. */
	private void fallTick() {
		baseTick();
		Vec3 v = getDeltaMovement().add(0, -FALL_GRAVITY, 0);
		move(MoverType.SELF, v);
		setDeltaMovement(getDeltaMovement().scale(0.98));

		if (level() instanceof ServerLevel level && (onGround() || horizontalCollision)) {
			crumble(level);
		}
	}

	@Override
	protected double getDefaultGravity() {
		return FLIGHT_GRAVITY;
	}

	// ---------------------------------------------------------------- hits

	@Override
	protected boolean canHitEntity(Entity target) {
		if (!super.canHitEntity(target) || target instanceof ArchiveDebris || target instanceof PylonFocus) {
			return false;
		}

		State state = state();

		if (state == State.RETURNED) {
			// Sent back: it is aimed at him alone.
			return target == thrower();
		}

		Entity owner = getOwner();
		return state == State.FLYING && (owner == null || (target != owner && !Allegiances.friendly(owner, target)));
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(level() instanceof ServerLevel level) || isRemoved() || !(result.getEntity() instanceof LivingEntity living)) {
			return;
		}

		CollectorBalance b = CollectorBalance.get();

		if (state() == State.RETURNED) {
			Entity returner = getOwner();
			LivingEntity thrower = thrower();

			// He staggers only when the hit lands: not while invulnerable (a transition), yielded, or for a returner outside the fight.
			if (living == thrower && living instanceof OrrenVasch vasch
					&& vasch.hurtServer(level, BossDamageTypes.source(level, BossDamageTypes.ARCHIVE_DEBRIS, this, returner), b.hurlDamage * b.hurlReturnMultiplier)) {
				vasch.counterStagger(b.hurlReturnStaggerTicks);
			}

			crumble(level);
			return;
		}

		// The guard first (spec 10.7): a saber guard blocks it (drain hurlGuardDrain) and never deflects it.
		CombatHooks.DeflectResult guard = CombatHooks.onProjectileHit(living, this);

		if (guard == CombatHooks.DeflectResult.NONE) {
			living.hurtServer(level, BossDamageTypes.source(level, BossDamageTypes.ARCHIVE_DEBRIS, this, getOwner()), damage);
		}

		crumble(level);
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);

		if (level() instanceof ServerLevel level && !isRemoved()) {
			crumble(level);
		}
	}

	/** Breaks into dust where it is: {@code boss_hurl} variant 2 with intensity 0 (no rip sound). */
	public void crumble(ServerLevel level) {
		if (isRemoved()) {
			return;
		}

		EffectEvents.around(level, BossEffects.HURL, position(), getDeltaMovement(), getId(), 0f, BossEffects.HURL_RIPPED);
		discard();
	}

	// ---------------------------------------------------------------- Force

	/** Lets a held chunk fall where it is, harmlessly (a transition, a reset, a lost target); no stagger. */
	public void drop() {
		if (state() == State.HELD) {
			setState(State.FALLING);
			setDeltaMovement(Vec3.ZERO);
		}
	}

	/** Pull during the tell rips the chunk away: it falls between them, and he staggers (8.3). */
	@Override
	public boolean onForcePull(ServerPlayer caster, Vec3 dir, float strength) {
		if (!(level() instanceof ServerLevel level) || isRemoved() || state() != State.HELD) {
			return false;
		}

		rip(level, caster);
		return true;
	}

	/**
	 * Rips the held chunk out of his grip toward {@code caster}: it drops between them. Also called
	 * when a Pull reaches Vasch himself during the tell. Returns false when it is not held.
	 */
	public boolean rip(ServerLevel level, @Nullable ServerPlayer caster) {
		if (state() != State.HELD) {
			return false;
		}

		Vec3 toward = caster == null ? Vec3.ZERO : caster.position().subtract(position()).multiply(1, 0, 1);
		// A short hop toward the caster, so it lands about halfway.
		Vec3 hop = toward.lengthSqr() < 1.0e-4 ? Vec3.ZERO : toward.normalize().scale(Math.min(0.35, toward.length() * 0.06));
		setState(State.FALLING);
		setDeltaMovement(hop.add(0, 0.15, 0));
		hurtMarked = true;
		EffectEvents.around(level, BossEffects.HURL, position(), hop, getId(), 1f, BossEffects.HURL_RIPPED);

		if (thrower() instanceof OrrenVasch vasch) {
			vasch.onHurlRipped(caster);
		}

		return true;
	}

	/** Lift during the tell catches the chunk; the catcher's channel keeps it hanging. */
	@Override
	public boolean onForceLift(ServerPlayer caster) {
		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return false;
		}

		State state = state();

		if (state == State.HELD) {
			setState(State.CAUGHT);
			catcher = caster;
			lastLiftTick = level.getGameTime();
			EffectEvents.around(level, BossEffects.HURL, position(), null, getId(), 1f, BossEffects.HURL_CAUGHT);

			if (thrower() instanceof OrrenVasch vasch) {
				vasch.onHurlCaught();
			}

			return true;
		}

		if (state == State.CAUGHT && catcher == caster) {
			lastLiftTick = level.getGameTime();
			return true;
		}

		return false;
	}

	/** The catcher let go: it drops harmlessly, or, with {@code lift_hurl}, flies back at him for the Push-return damage. */
	private void releaseCatch(ServerLevel level) {
		ServerPlayer from = catcher;
		LivingEntity thrower = thrower();
		catcher = null;

		if (from != null && thrower != null && from.isAlive() && ForcePowers.hasNode(from, Progression.get(from), "lift_hurl")) {
			sendBack(level, from, thrower);
		} else {
			setState(State.FALLING);
			setDeltaMovement(Vec3.ZERO);
		}
	}

	/** A Push within {@code hurlReturnRange} of the flying chunk returns it at him (8.3). */
	@Override
	public boolean onForcePush(ServerPlayer caster, Vec3 dir, float strength) {
		if (!(level() instanceof ServerLevel level) || isRemoved() || state() != State.FLYING) {
			return false;
		}

		CollectorBalance b = CollectorBalance.get();
		LivingEntity thrower = thrower();

		if (thrower == null || caster.distanceTo(this) > b.hurlReturnRange) {
			return false;
		}

		sendBack(level, caster, thrower);
		return true;
	}

	private void sendBack(ServerLevel level, Player by, LivingEntity at) {
		Vec3 aim = at.getBoundingBox().getCenter().subtract(position());
		double speed = CollectorBalance.get().hurlSpeed * RETURN_SPEED_MULT;
		Vec3 velocity = aim.lengthSqr() < 1.0e-4 ? Vec3.ZERO : aim.normalize().scale(speed);
		// The returner owns it now, so the hit on him counts as theirs.
		setOwner(by);
		setState(State.RETURNED);
		setDeltaMovement(velocity);
		hurtMarked = true;
		EffectEvents.around(level, BossEffects.HURL, position(), velocity, getId(), 1f, BossEffects.HURL_RETURNED);
	}

	// ---------------------------------------------------------------- contracts

	@Override
	public Vec3 deflectableVelocity() {
		return getDeltaMovement();
	}

	@Override
	public @Nullable Entity deflectableShooter() {
		return getOwner();
	}

	/** Never redirected: a guard only blocks the chunk (it breaks itself on {@code BLOCKED}). */
	@Override
	public void redirect(Vec3 newVelocity, @Nullable Entity newOwner, boolean parried) {
	}

	@Override
	public boolean saberDeflectable() {
		return false;
	}

	@Override
	public float guardDrain() {
		return CollectorBalance.get().hurlGuardDrain;
	}
}
