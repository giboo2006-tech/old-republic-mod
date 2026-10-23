package com.oldrepublic.boss.cantor;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.raid.RaidBalance;
import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A choir echo of the First Cantor (spec MILESTONE_5.md 12.3): a spectral singer standing at one of the
 * undercroft's {@code CHOIR_1..4} spots in the first phase, with {@code choirHealth} health scaled like
 * the Cantor's. While any echo sings, he is shielded. An echo never moves; a Push staggers its song
 * for {@code choirStaggerTicks} (the M2 diminishing returns on the push-resistant echoes) and a Stasis
 * holds it silent, without moving it. Force Drain finds no life in it (its damage is refused). Only the
 * raid's participants can break it; its session spawns it, brings the choir back and clears it.
 */
public class CantorChoir extends Mob implements Aligned {
	/** W6's Force Drain damage type, by id (this file needs no W6 class): the echoes have no life to drain. */
	private static final ResourceKey<DamageType> FORCE_DRAIN = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("force_drain"));
	/** W6's Stasis status effect, by id. */
	private static final Identifier STASIS = OldRepublicMod.id("stasis");
	private static final EntityDataAccessor<Boolean> DATA_SILENT = SynchedEntityData.defineId(CantorChoir.class, EntityDataSerializers.BOOLEAN);
	/** A stray copy without a session leaves after this long. */
	private static final int STRAY_TICKS = 20;

	private @Nullable CantorSession session;
	/** Which of the four spots it stands on. */
	private int spot;
	private @Nullable Vec3 home;
	/** Game time until which a stagger keeps it silent. */
	private long silentUntil;

	public CantorChoir(EntityType<? extends CantorChoir> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, new RaidBalance().choirHealth)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SILENT, false);
	}

	/** Joins it to the raid that raised it, standing on spot {@code spot} at {@code home} (server side; never saved). */
	void bind(CantorSession session, int spot, Vec3 home) {
		this.session = session;
		this.spot = spot;
		this.home = home;
	}

	public int spot() {
		return spot;
	}

	/** Whether its song has broken off (a stagger or a Stasis): the renderer dims it and its voice drops out. Synced. */
	public boolean isSilent() {
		return this.entityData.get(DATA_SILENT);
	}

	/** Whether it sings now, shielding the Cantor: alive, not staggered and not held in Stasis. Server side. */
	public boolean sings() {
		return isAlive() && !isRemoved() && level().getGameTime() >= silentUntil && !inStasis();
	}

	private boolean inStasis() {
		Optional<Holder.Reference<MobEffect>> stasis = BuiltInRegistries.MOB_EFFECT.get(STASIS);
		return stasis.isPresent() && hasEffect(stasis.get());
	}

	/**
	 * A Push staggers its song: silent for the diminished {@code choirStaggerTicks} (the second within the
	 * push window half as long, the third not at all). Returns the ticks it falls silent.
	 */
	public int stagger() {
		int ticks = ControlEffects.diminished(this, Math.max(0, RaidBalance.get().choirStaggerTicks), ControlEffects.STAGGER);

		if (ticks > 0) {
			silentUntil = Math.max(silentUntil, level().getGameTime() + ticks);
			this.entityData.set(DATA_SILENT, true);
		}

		return ticks;
	}

	@Override
	public Allegiance allegiance() {
		return Allegiance.HYMN;
	}

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			return;
		}

		CantorSession current = session;

		if (current == null || !current.ownsChoir(this)) {
			if (current != null || tickCount > STRAY_TICKS) {
				discard();
			}

			return;
		}

		// It never moves from its spot: a Push staggers its song instead.
		setDeltaMovement(Vec3.ZERO);
		Vec3 at = home;

		if (at != null && position().distanceToSqr(at) > 1.0e-4) {
			setPos(at);
		}

		boolean silent = !sings();

		if (silent != isSilent()) {
			this.entityData.set(DATA_SILENT, silent);
		}
	}

	/** It stands where it sings. */
	@Override
	public void travel(Vec3 input) {
	}

	/** Only the participants' blows (and {@code /kill}) reach it; Force Drain finds nothing to take. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}

		if (source.is(FORCE_DRAIN)) {
			return false;
		}

		return session != null && session.acceptsChoirDamage(source) && super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FIRE)
				|| source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(level, source);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (session != null && !level().isClientSide()) {
			session.onChoirFallen(this);
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	/** Its raid owns its lifetime. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	/** A struck echo cries out in his voice, higher. */
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.BOSS_CANTOR_HURT;
	}

	@Override
	public float getVoicePitch() {
		return 1.45f + (getRandom().nextFloat() - 0.5f) * 0.1f;
	}

	/** Its fall is the session's (the song thins). */
	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}
}
