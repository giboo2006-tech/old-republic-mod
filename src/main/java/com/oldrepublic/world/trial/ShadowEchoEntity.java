package com.oldrepublic.world.trial;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.WorldBalance;
import com.oldrepublic.world.block.WorldBlockAmbience;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Shadow Echo: a dark reflection of the player that fights them in the Trial of Reflection.
 * It presses with melee, a telegraphed lunge ({@link EchoLungeGoal}) and a telegraphed mirrored
 * push ({@link EchoPushGoal}). It only exists while its trial session runs: it is never saved
 * ({@code noSave} entity type) and discards itself whenever {@link TrialManager} no longer claims
 * it. Only its owner (or commands) can hurt it.
 */
public class ShadowEchoEntity extends Monster {
	public static final byte ACTION_IDLE = 0;
	public static final byte ACTION_LUNGE_WINDUP = 1;
	public static final byte ACTION_LUNGING = 2;
	public static final byte ACTION_PUSH_WINDUP = 3;
	public static final byte ACTION_PUSH_RELEASE = 4;

	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER =
			SynchedEntityData.defineId(ShadowEchoEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(ShadowEchoEntity.class, EntityDataSerializers.BYTE);
	private static final int LIVENESS_CHECK_INTERVAL = 10;

	/** Client only: ticks the current action has been showing. */
	private int clientActionTicks;
	private byte clientLastAction;

	public ShadowEchoEntity(EntityType<? extends ShadowEchoEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		WorldBalance defaults = new WorldBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, defaults.echoHealth)
				.add(Attributes.ATTACK_DAMAGE, defaults.echoDamage)
				.add(Attributes.MOVEMENT_SPEED, defaults.echoSpeed)
				.add(Attributes.ARMOR, defaults.echoArmor)
				.add(Attributes.FOLLOW_RANGE, 24)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new EchoPushGoal(this));
		this.goalSelector.addGoal(2, new EchoLungeGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true));
		// No target goals: the trial sets the owner as the only target.
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OWNER, Optional.empty());
		builder.define(DATA_ACTION, ACTION_IDLE);
	}

	/** Applies the balance section to this echo. Called once by the trial right after creation. */
	void applyBalance(WorldBalance b) {
		setBase(Attributes.MAX_HEALTH, b.echoHealth);
		setBase(Attributes.ATTACK_DAMAGE, b.echoDamage);
		setBase(Attributes.MOVEMENT_SPEED, b.echoSpeed);
		setBase(Attributes.ARMOR, b.echoArmor);
		setHealth(getMaxHealth());
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);

		if (instance != null) {
			// API-CHECK: AttributeInstance#setBaseValue (standard, AttributeInstance was not dumped).
			instance.setBaseValue(value);
		}
	}

	public @Nullable UUID ownerId() {
		return this.entityData.get(DATA_OWNER).map(EntityReference::getUUID).orElse(null);
	}

	void setOwner(UUID owner) {
		this.entityData.set(DATA_OWNER, Optional.of(EntityReference.<LivingEntity>of(owner)));
	}

	public byte action() {
		return this.entityData.get(DATA_ACTION);
	}

	void setAction(byte action) {
		this.entityData.set(DATA_ACTION, action);
	}

	/** Client only: ticks since the current action started (for the render pose ramps). */
	public int clientActionTicks() {
		return clientActionTicks;
	}

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			byte action = action();
			clientActionTicks = action == clientLastAction ? clientActionTicks + 1 : 0;
			clientLastAction = action;
			WorldBlockAmbience.get().echoTick(this);
		} else if (tickCount % LIVENESS_CHECK_INTERVAL == 0 && !TrialManager.isLiveEcho(this)) {
			discard();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		UUID owner = ownerId();
		boolean byOwner = owner != null && source.getEntity() != null && owner.equals(source.getEntity().getUUID());

		if (!byOwner && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}

		return super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		boolean wasDead = this.dead;
		super.die(source);

		if (!wasDead && level() instanceof ServerLevel) {
			TrialManager.onEchoDefeated(this);
		}
	}

	/** The echo dissolves (client wisps) instead of tipping over for a second. */
	@Override
	protected void tickDeath() {
		this.deathTime++;

		if (this.deathTime >= 3 && !level().isClientSide() && !isRemoved()) {
			discard();
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

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

	/** The trial owns this echo's lifetime (also on Peaceful), so vanilla despawning is off. */
	@Override
	public void checkDespawn() {
	}
}
