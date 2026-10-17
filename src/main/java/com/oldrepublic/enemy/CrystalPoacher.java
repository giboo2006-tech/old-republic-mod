package com.oldrepublic.enemy;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.identity.FrontierSettler;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Crystal Poacher: a mercenary with a blaster rifle who keeps its distance, strafes, falls back
 * when rushed and fires telegraphed bursts of orange {@code POACHER} bolts. Hostile to players and,
 * on sight, to Republic and Imperial troopers. The combat behaviour lives in
 * {@link PoacherCombatGoal} and {@link PoacherCoverGoal}; this class holds the synced tell and
 * disarm flags, equipment, firing, sounds and death handling. A Disarming Pull
 * ({@code ForceStatus#isDisarmed}) stops it firing and lowers the rifle; a stagger or shock cancels
 * the current tell ({@link #interruptCharge}).
 */
public class CrystalPoacher extends Monster implements PoacherCrew {
	/** True during the 0.6 s tell before each shot. Drives the muzzle glow, particles and whine. */
	private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(CrystalPoacher.class, EntityDataSerializers.BOOLEAN);
	/** True while a Disarming Pull keeps the rifle out of line: no firing, rifle lowered. */
	private static final EntityDataAccessor<Boolean> DATA_DISARMED = SynchedEntityData.defineId(CrystalPoacher.class, EntityDataSerializers.BOOLEAN);
	private static final TagKey<Structure> CRYSTAL_CAVES = TagKey.create(Registries.STRUCTURE, OldRepublicMod.id("crystal_caves"));
	/** How far camp poachers wander from where the camp placed them. */
	private static final int CAMP_HOME_RADIUS = 12;
	/**
	 * How far above or below their spawn height camp poachers may go. Keeps them on the camp floor
	 * instead of climbing the hillside around a camp, where a melee player cannot reach them.
	 */
	private static final int CAMP_HOME_HEIGHT = 4;

	private boolean setUp;
	private boolean deathHandled;
	/** Set by a stagger or shock; the combat goal drops the current tell. */
	private boolean interruptRequested;
	/** Client only: ticks the current tell has been showing, for the glow ramp. */
	private int clientChargeTicks;

	public CrystalPoacher(EntityType<? extends CrystalPoacher> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	/** Default attributes. Balance values replace them on each poacher's first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		EnemyBalance defaults = new EnemyBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, defaults.maxHealth)
				.add(Attributes.ARMOR, defaults.armor)
				.add(Attributes.MOVEMENT_SPEED, defaults.movementSpeed)
				.add(Attributes.FOLLOW_RANGE, defaults.followRange)
				.add(Attributes.ATTACK_DAMAGE, defaults.meleeDamage);
	}

	/**
	 * Spawn rule. Inside a crystal cave (camp spawn overrides) any light level is fine. Elsewhere a
	 * natural spawn needs the {@code naturalSpawns} balance switch, darkness and to be deep
	 * underground; eggs, commands and spawners follow the normal monster rules.
	 */
	public static boolean checkPoacherSpawnRules(EntityType<CrystalPoacher> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (reason == EntitySpawnReason.NATURAL && isInCrystalCave(level, pos)) {
			return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random);
		}

		if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) {
			return false;
		}

		if (reason != EntitySpawnReason.NATURAL) {
			return true;
		}

		return EnemyBalance.get().naturalSpawns
				&& pos.getY() < level.getSeaLevel() - 8
				&& pos.getY() < level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 12;
	}

	private static boolean isInCrystalCave(ServerLevelAccessor level, BlockPos pos) {
		// API-CHECK: ServerLevelAccessor#getLevel and StructureStart#isValid (standard, not in the probe logs).
		return level.getLevel().structureManager().getStructureWithPieceAt(pos, CRYSTAL_CAVES).isValid();
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PoacherCoverGoal(this));
		this.goalSelector.addGoal(2, new PoacherCombatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(CrystalPoacher.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		// Poachers and troopers are hostile on sight (spec 7.8); downed troopers and officers cannot be seen as enemies.
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, TrooperEntity.class, true));
	}

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
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CHARGING, false);
		builder.define(DATA_DISARMED, false);
	}

	/** The rifle is pulled out of line (Disarming Pull). Synced. */
	public boolean isDisarmed() {
		return this.entityData.get(DATA_DISARMED);
	}

	/** Cancels the current attack tell (a stagger, shock or disarm). */
	public void interruptCharge() {
		interruptRequested = true;
	}

	/** True once after {@link #interruptCharge}. */
	boolean consumeInterrupt() {
		boolean value = interruptRequested;
		interruptRequested = false;
		return value;
	}

	public boolean isCharging() {
		return this.entityData.get(DATA_CHARGING);
	}

	void setCharging(boolean charging) {
		this.entityData.set(DATA_CHARGING, charging);
	}

	/** Client only: how many ticks the current tell has been showing (0 when not charging). */
	public int clientChargeTicks() {
		return clientChargeTicks;
	}

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			clientChargeTicks = isCharging() ? clientChargeTicks + 1 : 0;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance();
			equipRifle();
		}

		this.entityData.set(DATA_DISARMED, ForceStatus.isDisarmed(this));
	}

	/** Applies balance attributes. Runs once per loaded poacher because attribute defaults are fixed at startup. */
	private void applyBalance() {
		EnemyBalance b = EnemyBalance.get();
		setBase(Attributes.ARMOR, b.armor);
		setBase(Attributes.MOVEMENT_SPEED, b.movementSpeed);
		setBase(Attributes.FOLLOW_RANGE, b.followRange);
		setBase(Attributes.ATTACK_DAMAGE, b.meleeDamage);

		boolean fullHealth = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, b.maxHealth);

		if (fullHealth || getHealth() > getMaxHealth()) {
			setHealth(getMaxHealth());
		}
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);

		if (instance != null) {
			// API-CHECK: AttributeInstance#setBaseValue (standard, AttributeInstance was not dumped).
			instance.setBaseValue(value);
		}
	}

	/** Camp poachers (the only ones with a home) also stay near the camp floor's height. */
	@Override
	public boolean isWithinHome(BlockPos pos) {
		return super.isWithinHome(pos) && (!hasHome() || Math.abs(pos.getY() - getHomePosition().getY()) <= CAMP_HOME_HEIGHT);
	}

	@Override
	public boolean isWithinHome(Vec3 pos) {
		return super.isWithinHome(pos) && (!hasHome() || Math.abs(pos.y - getHomePosition().getY()) <= CAMP_HOME_HEIGHT);
	}

	/** Poachers loaded from structure NBT may skip finalizeSpawn, so the rifle is also given here. */
	private void equipRifle() {
		if (getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(EnemyItems.BLASTER_RIFLE));
		}

		setDropChance(EquipmentSlot.MAINHAND, 0f);
		setCanPickUpLoot(false);
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(EnemyItems.BLASTER_RIFLE));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
		populateDefaultEquipmentSlots(getRandom(), difficulty);
		equipRifle();

		if (reason == EntitySpawnReason.STRUCTURE || reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.COMMAND) {
			setPersistenceRequired();
		}

		if (reason == EntitySpawnReason.STRUCTURE) {
			// Camp poachers stay around their camp instead of strolling off across the hills.
			setHomeTo(blockPosition(), CAMP_HOME_RADIUS);
		}

		return result;
	}

	/**
	 * Where bolts leave the rifle: in front of the shooting shoulder at chest height, matching
	 * the raised-rifle pose the client renders while the poacher is aggressive.
	 */
	public Vec3 muzzlePosition() {
		float yaw = this.yBodyRot * ((float) Math.PI / 180f);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double side = getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		// Right of an entity facing (fx, fz) is (-fz, fx).
		double rx = -fz * side;
		double rz = fx * side;
		return new Vec3(getX() + fx * 0.95 + rx * 0.3, getY() + 1.38, getZ() + fz * 0.95 + rz * 0.3);
	}

	/** Fires one bolt at the target. Server only. */
	void fireBolt(ServerLevel level, LivingEntity target) {
		EnemyBalance b = EnemyBalance.get();
		Vec3 muzzle = muzzlePosition();
		Vec3 aim = target.getEyePosition().subtract(0, 0.35, 0);

		// A target just below a ledge can be seen (eye to eye) while the chest line is blocked by
		// the ledge's edge: aim at the head then, instead of shooting the ground in front of it.
		if (blocked(level, muzzle, aim) && !blocked(level, muzzle, target.getEyePosition())) {
			aim = target.getEyePosition();
		}

		double flightTicks = muzzle.distanceTo(aim) / Math.max(0.1, b.boltSpeed);
		aim = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(flightTicks * 0.5));
		Vec3 dir = aim.subtract(muzzle);

		float inaccuracy = b.aimInaccuracy * switch (level.getDifficulty()) {
			case EASY -> 1.5f;
			case HARD -> 0.6f;
			default -> 1f;
		};

		BlasterBolt bolt = BlasterBolt.create(level, this, muzzle, b.boltDamage, b.boltLifetimeTicks, BlasterBolt.Style.POACHER, false);
		bolt.shoot(dir.x, dir.y, dir.z, b.boltSpeed, inaccuracy);
		level.addFreshEntity(bolt);

		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.BLASTER_FIRE, getSoundSource(), 1.0f, 0.95f + getRandom().nextFloat() * 0.1f);
		EffectEvents.around(level, EnemyEffects.BLASTER_FIRE, muzzle, bolt.getDeltaMovement().normalize(), getId(), 1f, BlasterBolt.Style.POACHER.ordinal());
	}

	private boolean blocked(ServerLevel level, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.POACHER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.POACHER_DEATH;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (!deathHandled && level() instanceof ServerLevel serverLevel) {
			deathHandled = true;
			setCharging(false);
			PoacherLoot.onKilled(this, serverLevel, source, shouldDropLoot(serverLevel));
		}
	}
}
