package com.oldrepublic.boss.kaast;

import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.enemy.mandalorian.HunterDamage;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.enemy.trooper.FragGrenade;
import com.oldrepublic.force.ForceReactiveEntity;
import com.oldrepublic.force.sense.SenseTargets;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One of Kaast's camp mines (spec MILESTONE_4.md 10.5, phase 2): it blinks and beeps for
 * {@code mineArmTicks} (a tell), then is live, and anyone not on Kaast's side within
 * {@code mineRadius} sets it off for {@code mineDamage} and knockback. Push or Lift sets it off from
 * range (the blast then spares no one), as does a shot or a blow. Force Sense shows live mines.
 * Never saved: the session clears them on a yield or a reset.
 */
public class CampMine extends Entity implements ForceReactiveEntity {
	private static final EntityDataAccessor<Boolean> DATA_LIVE = SynchedEntityData.defineId(CampMine.class, EntityDataSerializers.BOOLEAN);

	/** The blast reaches this far beyond the trigger radius, falling off. */
	private static final float BLAST_REACH = 1.0f;
	private static final int ARMING_BEEP_TICKS = 10;
	private static final int LIVE_BEEP_TICKS = 40;

	private @Nullable UUID owner;
	private int armTicks = 40;
	private float damage = 8f;
	private float radius = 1.5f;
	private boolean burst;

	public CampMine(EntityType<? extends CampMine> type, Level level) {
		super(type, level);
	}

	/** Sets a mine at {@code at} for {@code kaast}: arming starts now. */
	public static @Nullable CampMine place(ServerLevel level, TorvinKaast kaast, BlockPos at, KaastBalance b) {
		CampMine mine = KaastEntities.CAMP_MINE.create(level, EntitySpawnReason.EVENT);

		if (mine == null) {
			return null;
		}

		mine.owner = kaast.getUUID();
		mine.armTicks = Math.max(1, b.mineArmTicks);
		mine.damage = b.mineDamage;
		mine.radius = b.mineRadius;
		mine.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);

		if (!level.addFreshEntity(mine)) {
			return null;
		}

		EffectEvents.around(level, KaastEffects.EVENT, mine.position(), null, mine.getId(), mine.armTicks, KaastEffects.MINE_ARM);
		level.playSound(null, mine.getX(), mine.getY(), mine.getZ(), ModSounds.BOSS_KAAST_MINE_ARM, SoundSource.HOSTILE, 1f, 1f);
		return mine;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_LIVE, false);
	}

	public boolean isLive() {
		return this.entityData.get(DATA_LIVE);
	}

	@Override
	public void tick() {
		super.tick();

		if (!(level() instanceof ServerLevel level) || burst) {
			return;
		}

		if (!isLive()) {
			if (tickCount % ARMING_BEEP_TICKS == 0) {
				level.playSound(null, getX(), getY(), getZ(), ModSounds.BOSS_KAAST_MINE_BEEP, SoundSource.HOSTILE, 0.8f, 1f + tickCount / (float) armTicks * 0.5f);
			}

			if (tickCount >= armTicks) {
				this.entityData.set(DATA_LIVE, true);
				EffectEvents.around(level, KaastEffects.EVENT, position(), null, getId(), 1f, KaastEffects.MINE_LIVE);
			}

			return;
		}

		if (tickCount % LIVE_BEEP_TICKS == 0) {
			level.playSound(null, getX(), getY(), getZ(), ModSounds.BOSS_KAAST_MINE_BEEP, SoundSource.HOSTILE, 0.35f, 1.6f);
		}

		Entity kaast = owner == null ? null : level.getEntity(owner);

		if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(radius), e -> triggers(kaast, e)).isEmpty()) {
			detonate(level, false);
		}
	}

	/** Anyone alive but Kaast's side (Kaast, his hunters, Rattle) and players who cannot be hurt. */
	private static boolean triggers(@Nullable Entity kaast, LivingEntity e) {
		if (!e.isAlive() || e == kaast || e instanceof MandalorianHunter || e instanceof Rattle) {
			return false;
		}

		return !(e instanceof Player player) || !player.isCreative() && !player.isSpectator();
	}

	/** The blast: {@code damage} at the centre, falling off over the radius and a little beyond, and a shove. */
	void detonate(ServerLevel level, boolean byForce) {
		if (burst) {
			return;
		}

		burst = true;
		Vec3 at = position().add(0, 0.2, 0);
		Entity kaast = owner == null ? null : level.getEntity(owner);
		DamageSource source = level.damageSources().source(HunterDamage.CAMP_MINE, this, kaast);
		float reach = radius + BLAST_REACH;

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach + 1.0), LivingEntity::isAlive)) {
			double distance = Math.max(0.0, target.getBoundingBox().getCenter().distanceTo(at) - target.getBbWidth() * 0.5);
			float amount = FragGrenade.damageAt(damage, reach, distance);

			if (amount <= 0f || FragGrenade.spares(kaast, target, byForce)) {
				continue;
			}

			target.hurtServer(level, source, amount);
			Vec3 away = target.position().subtract(at).multiply(1, 0, 1);
			Vec3 push = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize().scale(0.7);
			target.push(push.x, 0.4, push.z);
			target.hurtMarked = true;
		}

		EffectEvents.around(level, KaastEffects.EVENT, at, null, getId(), reach, KaastEffects.MINE_BURST);
		level.playSound(null, at.x, at.y, at.z, ModSounds.BOSS_KAAST_MINE_BURST, SoundSource.HOSTILE, 1.4f, 1f);
		discard();
	}

	/**
	 * Force Sense: every live mine within the block range is revealed, and dust settles on it for the
	 * caster alone (a mine still arming already blinks and beeps).
	 */
	static void collectSense(ServerPlayer caster, int blockRange, int creatureRange, Consumer<SenseTargets.Point> out) {
		ServerLevel level = caster.level();

		for (CampMine mine : level.getEntitiesOfClass(CampMine.class, caster.getBoundingBox().inflate(blockRange), CampMine::isLive)) {
			if (mine.distanceTo(caster) > blockRange) {
				continue;
			}

			Vec3 at = mine.position().add(0, 0.1, 0);
			out.accept(new SenseTargets.Point(SenseTargets.Kind.BLOCK, at, -1, Attitude.NEUTRAL));
			EffectEvents.toPlayer(caster, KaastEffects.EVENT, at, null, mine.getId(), 1f, KaastEffects.MINE_SENSED);
		}
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		detonate(level, source.getEntity() instanceof ServerPlayer);
		return true;
	}

	@Override
	public boolean onForcePush(ServerPlayer caster, Vec3 dir, float strength) {
		detonate(caster.level(), true);
		return true;
	}

	@Override
	public boolean onForceLift(ServerPlayer caster) {
		detonate(caster.level(), true);
		return true;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
