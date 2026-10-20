package com.oldrepublic.enemy.scour;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.FoesFeature;
import com.oldrepublic.enemy.hymn.FoeKills;
import com.oldrepublic.enemy.hymn.Interruptible;
import com.oldrepublic.enemy.hymn.TellLog;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A sand scour of Ghessar (spec MILESTONE_5.md 12.1): a long, plated burrower the colour of the dunes.
 * <b>Burrowed</b> it is hidden in the sand (drawn as nothing; no collision, nothing can strike or aim at
 * it; a faint ripple and a hiss within 6 blocks), creeping toward the nearest player. A player within
 * 4 blocks makes it <b>burst</b> out behind a 15-tick tell (the sand boils) and bite: 6 and Slowness
 * II for 40 ticks. It fights above ground for 120 ticks, then burrows again for at least 30, and it
 * takes damage only above ground. Below 30% it flees under the sand for a while. The
 * <b>matriarch</b> (1.6 times the size, 120 health) never flees; every 200 ticks she sweeps her tail
 * behind and beside her (a 20-tick tell: the tail rises and coils; 8 and knock-back within 4 blocks),
 * and every 400 ticks she calls 2 brood up from the sand. The fight itself is {@link ScourGoal}.
 */
public class SandScour extends Monster implements Interruptible {
	/** The client's pose: nothing, the sand boiling over it, the matriarch's tail raised. */
	public static final int ACTION_NONE = 0;
	public static final int ACTION_BURST = 1;
	public static final int ACTION_SWEEP = 2;

	private static final EntityDataAccessor<Boolean> DATA_MATRIARCH = SynchedEntityData.defineId(SandScour.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_BURROWED = SynchedEntityData.defineId(SandScour.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(SandScour.class, EntityDataSerializers.INT);

	private final TellLog tells = new TellLog();
	private boolean setUp;
	private boolean deathHandled;
	private boolean interruptRequested;
	private int clientActionTicks;

	public SandScour(EntityType<? extends SandScour> type, Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	/** Default attributes; the balance replaces them on the first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		ScourBalance b = new ScourBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, b.scourHealth)
				.add(Attributes.MOVEMENT_SPEED, b.speed)
				.add(Attributes.FOLLOW_RANGE, b.followRange)
				.add(Attributes.ATTACK_DAMAGE, b.biteDamage)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new ScourGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MATRIARCH, false);
		builder.define(DATA_BURROWED, true);
		builder.define(DATA_ACTION, ACTION_NONE);
	}

	// ---------------------------------------------------------------- state

	public boolean isMatriarch() {
		return this.entityData.get(DATA_MATRIARCH);
	}

	/** Makes this a matriarch (bigger, sweeps, calls brood, never flees) or a plain scour. Server side. */
	public void setMatriarch(boolean matriarch) {
		this.entityData.set(DATA_MATRIARCH, matriarch);

		if (setUp) {
			applyBalance();
		}
	}

	/** Hidden in the sand. */
	public boolean isBurrowed() {
		return this.entityData.get(DATA_BURROWED);
	}

	void setBurrowed(boolean burrowed) {
		this.entityData.set(DATA_BURROWED, burrowed);
	}

	/** {@code ACTION_*}, for the client's pose. */
	public int action() {
		return this.entityData.get(DATA_ACTION);
	}

	void setAction(int action) {
		this.entityData.set(DATA_ACTION, action);
	}

	/** Client only: ticks the current action has shown. */
	public int clientActionTicks() {
		return clientActionTicks;
	}

	/** Its tells and heavy hits (the tests read it). */
	public TellLog tellLog() {
		return tells;
	}

	/** A stagger, a Push, a shock: the matriarch's sweep in its tell stops. Under the sand nothing reaches it. */
	@Override
	public void interrupt() {
		interruptRequested = true;
	}

	boolean consumeInterrupt() {
		boolean value = interruptRequested;
		interruptRequested = false;
		return value;
	}

	// ---------------------------------------------------------------- hidden in the sand

	@Override
	public boolean isPickable() {
		return !isBurrowed() && super.isPickable();
	}

	@Override
	public boolean isPushable() {
		return !isBurrowed() && super.isPushable();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity entity) {
		return !isBurrowed() && super.canBeCollidedWith(entity);
	}

	@Override
	public boolean canBeHitByProjectile() {
		return !isBurrowed() && super.canBeHitByProjectile();
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return !isBurrowed() && super.canBeSeenAsEnemy();
	}

	@Override
	public boolean isAttackable() {
		return !isBurrowed() && super.isAttackable();
	}

	@Override
	protected void doPush(Entity entity) {
		if (!isBurrowed()) {
			super.doPush(entity);
		}
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			clientActionTicks = action() != ACTION_NONE ? clientActionTicks + 1 : 0;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance();
		}
	}

	private void applyBalance() {
		ScourBalance b = ScourBalance.get();
		boolean full = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, isMatriarch() ? b.matriarchHealth : b.scourHealth);
		setBase(Attributes.MOVEMENT_SPEED, b.speed);
		setBase(Attributes.FOLLOW_RANGE, b.followRange);
		setBase(Attributes.ATTACK_DAMAGE, b.biteDamage);
		setBase(Attributes.SCALE, isMatriarch() ? b.matriarchScale : 1.0);

		if (full || getHealth() > getMaxHealth()) {
			setHealth(getMaxHealth());
		}
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);

		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	/** The faint ripple over it, every {@code rippleIntervalTicks} while burrowed (the client shows it within 6 blocks). */
	void ripple(ServerLevel level) {
		EffectEvents.toTracking(this, ScourEffects.EVENT, position(), null, getId(), ScourBalance.get().rippleRange, ScourEffects.RIPPLE);
	}

	// ---------------------------------------------------------------- damage, sounds, death

	/** Under the sand nothing reaches it ({@code /kill} and the void still do). */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (isBurrowed() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}

		return super.hurtServer(level, source, amount);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.SCOUR_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.SCOUR_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return isMatriarch() ? super.getVoicePitch() * 0.7f : super.getVoicePitch();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (deathHandled || !(level() instanceof ServerLevel)) {
			return;
		}

		deathHandled = true;
		setAction(ACTION_NONE);
		ServerPlayer killer = FoesFeature.killer(this, source);

		if (killer != null) {
			FoeKills.report(killer, this, ScourEntities.ID, null, Attitudes.hostileSnapshot(this, killer));
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
		setBurrowed(true);
		return result;
	}

	/** Natural scours come and go with the dunes; placed ones (broods, the matriarch's hunt) stay. */
	@Override
	public boolean removeWhenFarAway(double distance) {
		return !isPersistenceRequired() && !isMatriarch() && super.removeWhenFarAway(distance);
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("ScourMatriarch", isMatriarch());
		output.putBoolean("ScourBurrowed", isBurrowed());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_MATRIARCH, input.getBooleanOr("ScourMatriarch", false));
		this.entityData.set(DATA_BURROWED, input.getBooleanOr("ScourBurrowed", true));
	}
}
