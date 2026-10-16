package com.oldrepublic.enemy;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.identity.FrontierSettler;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.ParryReactive;
import com.oldrepublic.saber.SaberCombat;

import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Poacher Captain (spec section 9.7.2): the raider camp's elite, a melee pressure fighter with a
 * vibroblade (telegraphed swings and an overhead heavy) and a blaster pistol for the middle
 * distance. The fight lives in {@link CaptainCombatGoal}. She yields at {@code yieldFraction} of
 * her health ({@link Yieldable}; the identity feature runs the Spare / Execute decision).
 */
public class PoacherCaptain extends Monster implements Yieldable, ParryReactive, PoacherCrew {
	/** What her weapons are doing, synced for the pose and tells. Stored by ordinal: append only. */
	public enum Action {
		IDLE,
		SWING_TELL,
		HEAVY_WINDUP,
		PISTOL_TELL,
		STAGGERED;

		static Action byOrdinal(int ordinal) {
			Action[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}
	}

	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(PoacherCaptain.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_YIELDED = SynchedEntityData.defineId(PoacherCaptain.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_DISARMED = SynchedEntityData.defineId(PoacherCaptain.class, EntityDataSerializers.BOOLEAN);
	private static final int FLEE_TICKS = 200;

	private @Nullable UUID owner;
	private String encounter = "";
	private boolean setUp;
	private long staggeredUntil;
	private boolean interruptRequested;
	private int fleeTicks = -1;
	/** Client only: ticks the current action has been showing. */
	private int clientActionTicks;

	public PoacherCaptain(EntityType<? extends PoacherCaptain> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	/** Default attributes. Balance values replace them on her first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		CaptainBalance d = new CaptainBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, d.health)
				.add(Attributes.ARMOR, d.armor)
				.add(Attributes.MOVEMENT_SPEED, d.speed)
				.add(Attributes.FOLLOW_RANGE, d.followRange)
				.add(Attributes.KNOCKBACK_RESISTANCE, d.knockbackResistance)
				.add(Attributes.ATTACK_DAMAGE, d.swingDamage);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new CaptainCombatGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
			/** Frontier settlers are never targets (raids take them captive; they are not fought). */
	@Override
	public boolean canAttack(LivingEntity target) {
		return !(target instanceof FrontierSettler) && super.canAttack(target);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target instanceof FrontierSettler ? null : target);
	}

	@Override
			public boolean canUse() {
				return !isYielded() && fleeTicks < 0 && super.canUse();
			}
		});
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return !isYielded() && fleeTicks < 0 && super.canUse();
			}
		}.setAlertOthers(CrystalPoacher.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
			@Override
			public boolean canUse() {
				return !isYielded() && fleeTicks < 0 && super.canUse();
			}
		});
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, Action.IDLE.ordinal());
		builder.define(DATA_YIELDED, false);
		builder.define(DATA_DISARMED, false);
	}

	// ---------------------------------------------------------------- state

	public Action action() {
		return Action.byOrdinal(this.entityData.get(DATA_ACTION));
	}

	void setAction(Action action) {
		this.entityData.set(DATA_ACTION, action.ordinal());
	}

	/** Client only: ticks the current action has been showing. */
	public int clientActionTicks() {
		return clientActionTicks;
	}

	/** The pistol is pulled out of line (Disarming Pull): she cannot fire. Synced. */
	public boolean isDisarmed() {
		return this.entityData.get(DATA_DISARMED);
	}

	public @Nullable UUID owner() {
		return owner;
	}

	/** The encounter's owner, who decides her fate when she yields (null: any player). */
	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
	}

	public String encounter() {
		return encounter;
	}

	/** Tags her as part of an encounter; also makes her persistent. */
	public void setEncounter(String encounter) {
		this.encounter = encounter;
		setPersistenceRequired();
	}

	boolean isStaggered() {
		return level().getGameTime() < staggeredUntil;
	}

	/** Staggers her for {@code ticks}: no attacks, and the current tell is dropped. */
	public void stagger(int ticks) {
		staggeredUntil = Math.max(staggeredUntil, level().getGameTime() + ticks);
		interruptRequested = true;
	}

	/** Cancels the current tell or wind-up (a stagger, shock or disarm). */
	public void interrupt() {
		interruptRequested = true;
	}

	boolean consumeInterrupt() {
		boolean value = interruptRequested;
		interruptRequested = false;
		return value;
	}

	@Override
	public void onParried(ServerPlayer defender, SaberCombat.Strike strike) {
		CaptainBalance b = CaptainBalance.get();
		stagger(strike.technique() == SaberCombat.Technique.NPC_HEAVY ? b.parryStaggerTicks : b.swingTellTicks);
	}

	/** Where pistol bolts leave the off-hand pistol. */
	public Vec3 pistolMuzzle() {
		float yaw = this.yBodyRot * ((float) Math.PI / 180f);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double side = getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
		return new Vec3(getX() + fx * 0.9 - fz * side * 0.35, getY() + 1.4, getZ() + fz * 0.9 + fx * side * 0.35);
	}

	// ---------------------------------------------------------------- Yieldable

	@Override
	public boolean canYield() {
		return fleeTicks < 0;
	}

	@Override
	public float yieldHealthFraction() {
		return CaptainBalance.get().yieldFraction;
	}

	@Override
	public @Nullable UUID yieldDecider() {
		return owner;
	}

	@Override
	public boolean isYielded() {
		return this.entityData.get(DATA_YIELDED);
	}

	@Override
	public void setYielded(boolean yielded) {
		this.entityData.set(DATA_YIELDED, yielded);

		if (yielded) {
			setTarget(null);
			getNavigation().stop();
			setAction(Action.IDLE);
		}
	}

	/** Spared or fled: she gets up and leaves the area for good. Executed: the identity feature kills her. */
	@Override
	public void onYieldResolved(YieldOutcome outcome) {
		setYielded(false);

		if (outcome != YieldOutcome.EXECUTED) {
			fleeTicks = FLEE_TICKS;
			setTarget(null);
		}
	}

	// ---------------------------------------------------------------- behaviour

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			Action action = action();
			clientActionTicks = action == Action.IDLE ? 0 : clientActionTicks + 1;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance();
			equipWeapons();
		}

		this.entityData.set(DATA_DISARMED, ForceStatus.isDisarmed(this));

		if (isYielded()) {
			getNavigation().stop();
		}

		if (fleeTicks >= 0) {
			tickFlee(level);
		}
	}

	private void tickFlee(ServerLevel level) {
		if (--fleeTicks <= 0) {
			discard();
			return;
		}

		setTarget(null);

		if (getNavigation().isDone()) {
			Player nearest = level.getNearestPlayer(this, 32);
			Vec3 away = nearest == null ? DefaultRandomPos.getPos(this, 16, 7) : DefaultRandomPos.getPosAway(this, 16, 7, nearest.position());

			if (away != null) {
				getNavigation().moveTo(away.x, away.y, away.z, 1.3);
			}
		}
	}

	/** Fires one pistol bolt at the target. Server only. */
	void firePistol(ServerLevel level, LivingEntity target) {
		CaptainBalance b = CaptainBalance.get();
		EnemyBalance e = EnemyBalance.get();
		Vec3 muzzle = pistolMuzzle();
		Vec3 dir = target.getEyePosition().subtract(0, 0.35, 0).subtract(muzzle);
		BlasterBolt bolt = BlasterBolt.create(level, this, muzzle, b.pistolDamage, e.boltLifetimeTicks);
		bolt.shoot(dir.x, dir.y, dir.z, e.boltSpeed, e.aimInaccuracy);
		level.addFreshEntity(bolt);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.BLASTER_PISTOL_FIRE, getSoundSource(), 1f, 0.95f + getRandom().nextFloat() * 0.1f);
		EffectEvents.around(level, EnemyEffects.BLASTER_FIRE, muzzle, bolt.getDeltaMovement().normalize(), getId(), 1f, 0);
	}

	private void applyBalance() {
		CaptainBalance b = CaptainBalance.get();
		boolean fullHealth = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, b.health);
		setBase(Attributes.ARMOR, b.armor);
		setBase(Attributes.MOVEMENT_SPEED, b.speed);
		setBase(Attributes.FOLLOW_RANGE, b.followRange);
		setBase(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance);
		setBase(Attributes.ATTACK_DAMAGE, b.swingDamage);

		if (fullHealth || getHealth() > getMaxHealth()) {
			setHealth(getMaxHealth());
		}
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);

		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private void equipWeapons() {
		if (!getMainHandItem().is(EnemyItems.VIBROBLADE)) {
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(EnemyItems.VIBROBLADE));
		}

		if (!getOffhandItem().is(EnemyItems.BLASTER_PISTOL)) {
			setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(EnemyItems.BLASTER_PISTOL));
		}

		setDropChance(EquipmentSlot.MAINHAND, 0f);
		setDropChance(EquipmentSlot.OFFHAND, 0f);
		setCanPickUpLoot(false);
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		equipWeapons();
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
		equipWeapons();

		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.STRUCTURE) {
			setPersistenceRequired();
		}

		return result;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return owner == null && encounter.isEmpty() && super.removeWhenFarAway(distanceSqr);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.CAPTAIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.CAPTAIN_DEATH;
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("CaptainYielded", isYielded());
		output.putString("CaptainEncounter", encounter);
		output.storeNullable("CaptainOwner", UUIDUtil.CODEC, owner);
		output.putInt("CaptainFleeTicks", fleeTicks);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_YIELDED, input.getBooleanOr("CaptainYielded", false));
		this.encounter = input.getStringOr("CaptainEncounter", "");
		this.owner = input.read("CaptainOwner", UUIDUtil.CODEC).orElse(null);
		this.fleeTicks = input.getIntOr("CaptainFleeTicks", -1);
	}
}
