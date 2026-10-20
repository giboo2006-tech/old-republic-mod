package com.oldrepublic.enemy.cartel;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEffects;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.FoesFeature;
import com.oldrepublic.enemy.PoacherCoverGoal;
import com.oldrepublic.enemy.hymn.FoeKills;
import com.oldrepublic.enemy.hymn.Interruptible;
import com.oldrepublic.enemy.hymn.TellLog;
import com.oldrepublic.equipment.GearItems;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.KillXp;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.vendor.hub.HubNpcs;

import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A Vessba kajidic cartel enforcer (spec MILESTONE_5.md 12.1): the port's hired muscle in sand-worn
 * jackets and kajidic colours. Three kits by {@link Variant}: the gunman fights from cover with a
 * blaster rifle (the M1 cover rule), the bruiser swings a vibrosword in a two-blow string ending in a
 * shove, the captain shoots a pistol, lobs frag grenades and calls the others in. Every heavy blow
 * has its tell (the rifle's muzzle glow, the M2 {@code npc_windup}, the M3 {@code grenade_tell}).
 *
 * <p>Its attitude follows the player's kajidic standing ({@link StandingRule}); a mission's
 * enforcers (spawned with an owner) stand against their owner's party. Hub guards on Saltgate's
 * walls never despawn and never drop anything (the M4 hub rule); others drop credits through the
 * M4 credit drops. Never spawns naturally.
 */
public class CartelEnforcer extends PathfinderMob implements Aligned, FactionFoe, Interruptible, KillXp {
	/** The three kits. Saved and synced by ordinal: append only. */
	public enum Variant {
		GUNMAN,
		BRUISER,
		CAPTAIN;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Variant byOrdinal(int ordinal) {
			Variant[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}

		/** A variant by name, or null. */
		public static @Nullable Variant byId(String id) {
			for (Variant v : values()) {
				if (v.id().equals(id)) {
					return v;
				}
			}

			return null;
		}
	}

	/** What the client poses: nothing, the frag grenade's arm drawn back. */
	public static final int ACTION_NONE = 0;
	public static final int ACTION_GRENADE = 1;

	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(CartelEnforcer.class, EntityDataSerializers.INT);
	/** The rifle's or pistol's muzzle glow before a shot. */
	private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(CartelEnforcer.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(CartelEnforcer.class, EntityDataSerializers.INT);

	private final StandingState standing = new StandingState();
	private final TellLog tells = new TellLog();
	private boolean setUp;
	private boolean variantSet;
	private boolean deathHandled;
	private boolean interruptRequested;
	/** Client only: ticks the current tell has shown. */
	private int clientTellTicks;

	public CartelEnforcer(EntityType<? extends CartelEnforcer> type, Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	/** Default attributes; the balance replaces them on the enforcer's first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		CartelBalance b = new CartelBalance();
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, b.gunmanHealth)
				.add(Attributes.ARMOR, b.gunmanArmor)
				.add(Attributes.MOVEMENT_SPEED, b.speed)
				.add(Attributes.FOLLOW_RANGE, b.followRange)
				.add(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance)
				.add(Attributes.ATTACK_DAMAGE, b.swordDamage);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PoacherCoverGoal(this, CartelEnforcer::coverSettings, this::mayTakeCover, () -> setCharging(false)));
		this.goalSelector.addGoal(2, new CartelCombatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	private static PoacherCoverGoal.Settings coverSettings() {
		CartelBalance b = CartelBalance.get();
		return new PoacherCoverGoal.Settings(b.coverHealthFraction, b.coverSpeed, b.coverHoldMinTicks, b.coverHoldMaxTicks, b.coverCooldownTicks);
	}

	/** The gunman and the captain fall back to cover (the M1 rule); the bruiser presses in. */
	private boolean mayTakeCover() {
		return variant() != Variant.BRUISER && getTarget() != null && action() == ACTION_NONE;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, Variant.GUNMAN.ordinal());
		builder.define(DATA_CHARGING, false);
		builder.define(DATA_ACTION, ACTION_NONE);
	}

	// ---------------------------------------------------------------- state

	public Variant variant() {
		return Variant.byOrdinal(this.entityData.get(DATA_VARIANT));
	}

	/** Kill XP by kit (16.3). */
	@Override
	public int killXp() {
		CartelBalance b = CartelBalance.get();
		return switch (variant()) {
			case GUNMAN -> b.gunmanXp;
			case BRUISER -> b.bruiserXp;
			case CAPTAIN -> b.captainXp;
		};
	}

	/** Sets the kit (and its weapon and numbers). Server side. */
	public void setVariant(Variant variant) {
		this.entityData.set(DATA_VARIANT, variant.ordinal());
		variantSet = true;
		equip();

		if (setUp) {
			applyBalance();
		}
	}

	public boolean isCharging() {
		return this.entityData.get(DATA_CHARGING);
	}

	void setCharging(boolean charging) {
		this.entityData.set(DATA_CHARGING, charging);
	}

	/** {@code ACTION_*}, for the client's pose. */
	public int action() {
		return this.entityData.get(DATA_ACTION);
	}

	void setAction(int action) {
		this.entityData.set(DATA_ACTION, action);
	}

	/** Client only: ticks the current tell has been showing. */
	public int clientTellTicks() {
		return clientTellTicks;
	}

	/** Its tells and heavy hits (the tests read it). */
	public TellLog tellLog() {
		return tells;
	}

	/** A stagger, a Push, a shock: the tell in progress stops (the captain's frag, the bruiser's string, a shot). */
	@Override
	public void interrupt() {
		interruptRequested = true;
	}

	/** True once after {@link #interrupt}. */
	boolean consumeInterrupt() {
		boolean value = interruptRequested;
		interruptRequested = false;
		return value;
	}

	// ---------------------------------------------------------------- FactionFoe, Aligned

	@Override
	public Faction faction() {
		return Faction.VESSBA_KAJIDIC;
	}

	@Override
	public Attitude ownedAttitude() {
		return Attitude.HOSTILE;
	}

	@Override
	public StandingState standing() {
		return standing;
	}

	@Override
	public boolean isHubGuard() {
		return entityTags().contains(HubNpcs.HUB_TAG);
	}

	@Override
	public double partyRange() {
		return CartelBalance.get().partyRange;
	}

	@Override
	public Allegiance allegiance() {
		return Allegiance.KAJIDIC;
	}

	/** The faction numbers the target checks read. */
	public static StandingTargets.Settings targetSettings() {
		CartelBalance b = CartelBalance.get();
		return new StandingTargets.Settings(b.followRange, b.waryRange, b.warnRepeatTicks, b.engageTicks, b.provokeTicks, b.safeRadius,
				ModSounds.CARTEL_BARK, "entity.oldrepublic.cartel_enforcer.warn");
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			clientTellTicks = isCharging() || action() != ACTION_NONE ? clientTellTicks + 1 : 0;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;

			if (!variantSet) {
				equip();
			}

			applyBalance();
		}

		if (tickCount % StandingTargets.INTERVAL == 0) {
			StandingTargets.update(this, this, level, targetSettings());
		}
	}

	/** Applies the balance attributes of its kit (attribute defaults are fixed at start-up). */
	private void applyBalance() {
		CartelBalance b = CartelBalance.get();
		boolean full = getHealth() >= getMaxHealth();
		Variant v = variant();
		setBase(Attributes.MAX_HEALTH, v == Variant.CAPTAIN ? b.captainHealth : v == Variant.BRUISER ? b.bruiserHealth : b.gunmanHealth);
		setBase(Attributes.ARMOR, v == Variant.CAPTAIN ? b.captainArmor : v == Variant.BRUISER ? b.bruiserArmor : b.gunmanArmor);
		setBase(Attributes.MOVEMENT_SPEED, b.speed);
		setBase(Attributes.FOLLOW_RANGE, b.followRange);
		setBase(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance);
		setBase(Attributes.ATTACK_DAMAGE, b.swordDamage);

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

	/** The kit's weapon: a blaster rifle, a vibrosword or a blaster pistol. Never dropped. */
	private void equip() {
		ItemStack weapon = switch (variant()) {
			case BRUISER -> new ItemStack(GearItems.VIBROSWORD);
			case CAPTAIN -> new ItemStack(EnemyItems.BLASTER_PISTOL);
			default -> new ItemStack(EnemyItems.BLASTER_RIFLE);
		};

		if (!ItemStack.isSameItem(getMainHandItem(), weapon)) {
			setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}

		setDropChance(EquipmentSlot.MAINHAND, 0f);
		setCanPickUpLoot(false);
	}

	// ---------------------------------------------------------------- shooting

	/**
	 * Where bolts leave the rifle or pistol: in front of the shooting shoulder at chest height,
	 * matching the raised-weapon pose the client renders while it fights.
	 */
	public Vec3 muzzlePosition() {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double side = getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		// Right of an entity facing (fx, fz) is (-fz, fx).
		double rx = -fz * side;
		double rz = fx * side;
		double reach = variant() == Variant.CAPTAIN ? 0.7 : 0.95;
		return new Vec3(getX() + fx * reach + rx * 0.3, getY() + 1.38, getZ() + fz * reach + rz * 0.3);
	}

	/** Fires one bolt of {@code damage} at {@code target}, with the M1 lead and the difficulty's spread. Server only. */
	void fireBolt(ServerLevel level, LivingEntity target, float damage) {
		CartelBalance b = CartelBalance.get();
		Vec3 muzzle = muzzlePosition();
		Vec3 aim = target.getEyePosition().subtract(0, 0.35, 0);
		double flightTicks = muzzle.distanceTo(aim) / Math.max(0.1, b.boltSpeed);
		aim = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(flightTicks * 0.5));
		Vec3 dir = aim.subtract(muzzle);

		float inaccuracy = b.aimInaccuracy * switch (level.getDifficulty()) {
			case EASY -> 1.5f;
			case HARD -> 0.6f;
			default -> 1f;
		};

		BlasterBolt bolt = BlasterBolt.create(level, this, muzzle, damage, b.boltLifetimeTicks, BlasterBolt.Style.POACHER, false);
		bolt.shoot(dir.x, dir.y, dir.z, b.boltSpeed, inaccuracy);
		level.addFreshEntity(bolt);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.BLASTER_FIRE, getSoundSource(), 1.0f, 0.95f + getRandom().nextFloat() * 0.1f);
		EffectEvents.around(level, EnemyEffects.BLASTER_FIRE, muzzle, bolt.getDeltaMovement().normalize(), getId(), 1f, BlasterBolt.Style.POACHER.ordinal());
	}

	// ---------------------------------------------------------------- damage, sounds, death

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && isAlive()) {
			StandingTargets.onHurtBy(this, this, source.getEntity() instanceof LivingEntity living ? living : null,
					targetSettings());
		}

		return hurt;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.CARTEL_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.CARTEL_DEATH;
	}

	/** Hub guards never drop anything (the M4 hub rule). */
	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return super.shouldDropLoot(level) && !isHubGuard();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (deathHandled || !(level() instanceof ServerLevel)) {
			return;
		}

		deathHandled = true;
		setCharging(false);
		setAction(ACTION_NONE);
		ServerPlayer killer = FoesFeature.killer(this, source);

		if (killer != null) {
			FoeKills.report(killer, this, CartelEntities.ID, Faction.VESSBA_KAJIDIC, Attitudes.hostileSnapshot(this, killer));
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);

		if (!variantSet) {
			setVariant(randomVariant(getRandom()));
		}

		setPersistenceRequired();
		return result;
	}

	/** Half gunmen, a third bruisers, the rest captains. */
	static Variant randomVariant(RandomSource random) {
		float roll = random.nextFloat();
		return roll < 0.5f ? Variant.GUNMAN : roll < 0.85f ? Variant.BRUISER : Variant.CAPTAIN;
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		// The kit's weapon is set with the variant.
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("CartelVariant", variant().ordinal());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_VARIANT, Variant.byOrdinal(input.getIntOr("CartelVariant", Variant.GUNMAN.ordinal())).ordinal());
		variantSet = true;
	}
}
