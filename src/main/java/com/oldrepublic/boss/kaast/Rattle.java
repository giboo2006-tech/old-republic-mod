package com.oldrepublic.boss.kaast;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.choke.ChokeImmune;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Rattle ({@code kaast_droid}, spec MILESTONE_4.md 10.5, 10.7): Kaast's seeker droid, the seeker's
 * model in his paint. It circles above his target and sweeps it with a scan line; while the line is
 * on a player Kaast's bolts deal {@code markBonus} more. 20 health, flying, untouchable by Choke.
 * Destroyed, the mark ends for the fight; it leaves for phase 2 and comes back for phase 3 unless it
 * was destroyed. Never saved.
 */
public class Rattle extends PathfinderMob implements ChokeImmune {
	/** The entity id its scan line is on, or -1 (the client draws the line). */
	private static final EntityDataAccessor<Integer> DATA_SCAN = SynchedEntityData.defineId(Rattle.class, EntityDataSerializers.INT);

	private static final double ORBIT_RADIUS = 5;
	private static final double ORBIT_HEIGHT = 6;
	private static final double MAX_SPEED = 0.4;
	/** Without a session (a stray summon) it leaves after this long. */
	private static final int STRAY_TICKS = 20;

	private @Nullable KaastSession session;
	private double orbit;
	private int cycle;

	public Rattle(EntityType<? extends Rattle> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, new KaastBalance().rattleHealth)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SCAN, -1);
	}

	void attach(KaastSession session) {
		this.session = session;
		this.orbit = getRandom().nextDouble() * Math.PI * 2;
	}

	/** The entity id the scan line is on, or -1. */
	public int scanTarget() {
		return this.entityData.get(DATA_SCAN);
	}

	/** Whether the scan line is on {@code target} now (Kaast's bolts deal more). */
	public boolean isScanning(LivingEntity target) {
		return isAlive() && scanTarget() == target.getId();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (session == null) {
			if (tickCount > STRAY_TICKS) {
				discard();
			}

			return;
		}

		KaastBalance b = KaastBalance.get();
		LivingEntity target = session.kaastTarget();
		Vec3 centre = target != null ? target.position() : Vec3.atBottomCenterOf(session.layout().heart());
		orbit += 0.035;
		Vec3 point = centre.add(Math.cos(orbit) * ORBIT_RADIUS, ORBIT_HEIGHT, Math.sin(orbit) * ORBIT_RADIUS);
		Vec3 move = point.subtract(position()).scale(0.12);

		if (move.length() > MAX_SPEED) {
			move = move.normalize().scale(MAX_SPEED);
		}

		setDeltaMovement(move);
		resetFallDistance();
		int period = Math.max(1, b.rattleScanTicks + b.rattleRestTicks);
		boolean scanning = cycle++ % period < b.rattleScanTicks;
		int scan = -1;

		if (target != null) {
			Vec3 to = target.getEyePosition().subtract(getEyePosition());
			float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
			setYRot(yaw);
			this.yBodyRot = yaw;
			this.yHeadRot = yaw;

			if (scanning && to.length() <= b.rattleScanRange && getSensing().hasLineOfSight(target)) {
				scan = target.getId();
			}
		}

		if (scan != scanTarget()) {
			this.entityData.set(DATA_SCAN, scan);

			if (scan >= 0) {
				playSound(ModSounds.SEEKER_SCAN, 1f, 1.2f);
			}
		}
	}

	/** Flies away: the fight's second phase, a yield, a reset. */
	void leave(ServerLevel level) {
		EffectEvents.around(level, KaastEffects.EVENT, position(), new Vec3(0, 1, 0), getId(), 0f, KaastEffects.ECHO);
		discard();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (session != null && level() instanceof ServerLevel) {
			session.onRattleDestroyed();
		}
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.SEEKER_FLY;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.DROID_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.DROID_DEATH;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}
}
