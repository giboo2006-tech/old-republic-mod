package com.oldrepublic.enemy.hymn;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.FoesFeature;
import com.oldrepublic.equipment.GearItems;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

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
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * A Hymnsworn (spec MILESTONE_5.md 12.1): one of Ulvaine's cult, hooded in storm-grey with a sung
 * verse stitched down the sleeve. Three kinds by {@link Variant}: the chanter (a chant that heals its
 * allies 1 a second within 8 blocks, weak staff blows), the blade (fast vibroblade strikes) and the
 * storm-caller (it calls lightning onto a marked patch, behind a 40-tick tell, and throws short bursts
 * of Force lightning). Hostile to every player ({@code HYMN}); never spawns naturally and never in
 * Peaceful. A shade (the raid's) is a blade drawn translucent that drops nothing.
 */
public class Hymnsworn extends Monster implements Aligned, Interruptible {
	/** The three kinds. Saved and synced by ordinal: append only. */
	public enum Variant {
		CHANTER,
		BLADE,
		CALLER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Variant byOrdinal(int ordinal) {
			Variant[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}

		/** A kind by name, or null. */
		public static @Nullable Variant byId(String id) {
			for (Variant v : values()) {
				if (v.id().equals(id)) {
					return v;
				}
			}

			return null;
		}
	}

	/** What the client poses: nothing, the chant (hands together, head bowed), the call (arms raised), a lightning burst (hand out). */
	public static final int ACTION_NONE = 0;
	public static final int ACTION_CHANT = 1;
	public static final int ACTION_CALL = 2;
	public static final int ACTION_LIGHTNING = 3;

	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(Hymnsworn.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(Hymnsworn.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_SHADE = SynchedEntityData.defineId(Hymnsworn.class, EntityDataSerializers.BOOLEAN);
	/** Hymnsworn this close come to a struck brother's aid. */
	private static final double ALERT_RANGE = 16;
	/** A chant keeps going this long after the last hurt ally or fight. */
	private static final int CHANT_LINGER_TICKS = 60;

	private final TellLog tells = new TellLog();
	private boolean setUp;
	private boolean variantSet;
	private boolean deathHandled;
	private boolean interruptRequested;
	/** A boss fight's minion (Ulvaine's pylon chanters, the raid's shades): never saved, its fight respawns it. */
	private boolean summoned;
	private long chantUntil;
	private int clientActionTicks;

	public Hymnsworn(EntityType<? extends Hymnsworn> type, Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	/** Default attributes; the balance replaces them on the first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		HymnswornBalance b = new HymnswornBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, b.bladeHealth)
				.add(Attributes.ARMOR, 0.0)
				.add(Attributes.MOVEMENT_SPEED, b.speed)
				.add(Attributes.FOLLOW_RANGE, b.followRange)
				.add(Attributes.ATTACK_DAMAGE, b.bladeDamage);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new HymnswornCombatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, Variant.BLADE.ordinal());
		builder.define(DATA_ACTION, ACTION_NONE);
		builder.define(DATA_SHADE, false);
	}

	// ---------------------------------------------------------------- state

	public Variant variant() {
		return Variant.byOrdinal(this.entityData.get(DATA_VARIANT));
	}

	/** Sets the kind (its weapon and numbers). Server side. */
	public void setVariant(Variant variant) {
		this.entityData.set(DATA_VARIANT, variant.ordinal());
		variantSet = true;
		equip();

		if (setUp) {
			applyBalance();
		}
	}

	/** {@code ACTION_*}, for the client's pose. */
	public int action() {
		return this.entityData.get(DATA_ACTION);
	}

	void setAction(int action) {
		this.entityData.set(DATA_ACTION, action);
	}

	/** A raid shade: drawn translucent, drops nothing. */
	public boolean isShade() {
		return this.entityData.get(DATA_SHADE);
	}

	public void setShade(boolean shade) {
		this.entityData.set(DATA_SHADE, shade);
	}

	/** A boss fight's minion, never saved (server side, set before it joins the level). */
	public boolean isSummoned() {
		return summoned;
	}

	public void setSummoned(boolean summoned) {
		this.summoned = summoned;
	}

	/** Client only: ticks the current action pose has shown. */
	public int clientActionTicks() {
		return clientActionTicks;
	}

	/** Its tells and heavy hits (the tests read it). */
	public TellLog tellLog() {
		return tells;
	}

	/** Whether the chanter's chant is sounding now (a fight, or a hurt ally near). */
	public boolean isChanting() {
		return variant() == Variant.CHANTER && action() == ACTION_CHANT;
	}

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

	@Override
	public Allegiance allegiance() {
		return Allegiance.HYMN;
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

			if (!variantSet) {
				equip();
			}

			applyBalance();
		}

		if (tickCount % FoeTargets.INTERVAL == 0) {
			FoeTargets.keepOrFindPlayer(this, level, HymnswornBalance.get().followRange);
		}

		if (variant() == Variant.CHANTER) {
			tickChant(level);
		}
	}

	/**
	 * The chant: while it fights, or an ally within {@code chantRange} is hurt, every
	 * {@code chantIntervalTicks} it heals every other Hymnsworn there by {@code chantHeal} (12.1).
	 */
	private void tickChant(ServerLevel level) {
		HymnswornBalance b = HymnswornBalance.get();
		long now = level.getGameTime();
		int interval = Math.max(1, b.chantIntervalTicks);

		if (now % interval != 0) {
			return;
		}

		AABB area = getBoundingBox().inflate(b.chantRange);
		double r = b.chantRange * b.chantRange;
		boolean hurtAlly = false;

		for (Hymnsworn ally : level.getEntitiesOfClass(Hymnsworn.class, area, h -> h != this && h.isAlive() && h.distanceToSqr(this) <= r)) {
			hurtAlly |= ally.getHealth() < ally.getMaxHealth();
		}

		if (getTarget() != null || hurtAlly) {
			chantUntil = now + CHANT_LINGER_TICKS;
		}

		boolean chanting = now < chantUntil && action() != ACTION_CALL && action() != ACTION_LIGHTNING;
		setAction(chanting ? ACTION_CHANT : action() == ACTION_CHANT ? ACTION_NONE : action());

		if (!chanting) {
			return;
		}

		for (Hymnsworn ally : level.getEntitiesOfClass(Hymnsworn.class, area, h -> h != this && h.isAlive() && h.distanceToSqr(this) <= r)) {
			if (ally.getHealth() < ally.getMaxHealth()) {
				ally.heal(b.chantHeal);
			}
		}

		// The chant itself is the client's loop (hymnsworn.chant, while the synced action is CHANT).
		EffectEvents.toTracking(this, HymnEffects.EVENT, getEyePosition(), null, getId(), b.chantRange, HymnEffects.CHANT);
	}

	/** Applies the balance attributes of its kind (attribute defaults are fixed at start-up). */
	private void applyBalance() {
		HymnswornBalance b = HymnswornBalance.get();
		boolean full = getHealth() >= getMaxHealth();
		Variant v = variant();
		setBase(Attributes.MAX_HEALTH, v == Variant.CALLER ? b.callerHealth : v == Variant.CHANTER ? b.chanterHealth : b.bladeHealth);
		setBase(Attributes.MOVEMENT_SPEED, b.speed);
		setBase(Attributes.FOLLOW_RANGE, b.followRange);
		setBase(Attributes.ATTACK_DAMAGE, v == Variant.CHANTER ? b.staffDamage : b.bladeDamage);

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

	/** The chanter's staff, the blade's vibroblade; the storm-caller's hands are empty. Never dropped. */
	private void equip() {
		ItemStack weapon = switch (variant()) {
			case CHANTER -> new ItemStack(GearItems.ELECTROSTAFF);
			case BLADE -> new ItemStack(EnemyItems.VIBROBLADE);
			default -> ItemStack.EMPTY;
		};

		if (!ItemStack.isSameItem(getMainHandItem(), weapon)) {
			setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}

		setDropChance(EquipmentSlot.MAINHAND, 0f);
		setCanPickUpLoot(false);
	}

	// ---------------------------------------------------------------- damage, sounds, death

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && isAlive() && source.getEntity() instanceof LivingEntity attacker && attacker != this && !Allegiances.friendly(this, attacker)
				&& !FoeTargets.ignored(attacker)) {
			setTarget(attacker);

			for (Hymnsworn brother : level.getEntitiesOfClass(Hymnsworn.class, getBoundingBox().inflate(ALERT_RANGE),
					h -> h != this && h.isAlive() && h.getTarget() == null)) {
				brother.setTarget(attacker);
			}
		}

		return hurt;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.HYMNSWORN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.HYMNSWORN_DEATH;
	}

	/** Shades are sung, not born: they drop nothing. */
	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return super.shouldDropLoot(level) && !isShade();
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

		if (killer != null && !isShade()) {
			FoeKills.report(killer, this, HymnEntities.HYMNSWORN_ID, null, Attitudes.hostileSnapshot(this, killer));
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

	/** Half blades, a third chanters, the rest storm-callers. */
	static Variant randomVariant(RandomSource random) {
		float roll = random.nextFloat();
		return roll < 0.5f ? Variant.BLADE : roll < 0.83f ? Variant.CHANTER : Variant.CALLER;
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		// The kind's weapon is set with the variant.
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---------------------------------------------------------------- saving

	/** A boss fight's minion is not saved: a restart ends the fight, and the fight brings its own. */
	@Override
	public boolean shouldBeSaved() {
		return !summoned && super.shouldBeSaved();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("HymnVariant", variant().ordinal());
		output.putBoolean("HymnShade", isShade());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_VARIANT, Variant.byOrdinal(input.getIntOr("HymnVariant", Variant.BLADE.ordinal())).ordinal());
		this.entityData.set(DATA_SHADE, input.getBooleanOr("HymnShade", false));
		variantSet = true;
	}
}
