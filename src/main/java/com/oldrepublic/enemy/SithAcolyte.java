package com.oldrepublic.enemy;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.FrontierSettler;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.duel.DuelistProfile;
import com.oldrepublic.saber.duel.DuelistProfiles;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.SaberDuelist;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.duel.SparResult;

import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
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
 * Sith Acolyte (spec section 9.7.1): a duelist in dark Imperial acolyte robes with a red blade.
 * Fights through {@link AcolyteCombatGoal} (the saber duel goal plus the Force Shove). In
 * {@link Mode#HOSTILE} it attacks players; in {@link Mode#RIVAL} (Brenn) it is neutral, owned, and
 * fights only inside a spar, and it can yield to its owner. Encounters and commands spawn it; it
 * never spawns naturally.
 */
public class SithAcolyte extends Monster implements SaberDuelist, Yieldable {
	/** Stored by ordinal: append only. */
	public enum Mode {
		HOSTILE,
		RIVAL;

		static Mode byOrdinal(int ordinal) {
			return ordinal == RIVAL.ordinal() ? RIVAL : HOSTILE;
		}
	}

	/** Skins: {@code textures/entity/sith_acolyte_<id>.png}. Stored by ordinal: append only. */
	public enum Skin {
		ACOLYTE,
		BRENN,
		DESERTER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Skin byOrdinal(int ordinal) {
			Skin[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}
	}

	private static final EntityDataAccessor<Integer> DATA_GUARD = SynchedEntityData.defineId(SithAcolyte.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(SithAcolyte.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(SithAcolyte.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_YIELDED = SynchedEntityData.defineId(SithAcolyte.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_KNEELING = SynchedEntityData.defineId(SithAcolyte.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_SHOVING = SynchedEntityData.defineId(SithAcolyte.class, EntityDataSerializers.BOOLEAN);
	/** How long the acolyte kneels after losing a spar. */
	private static final int SPAR_KNEEL_TICKS = 80;
	/** How long it runs before it is gone, once its yield is resolved (spared or fled). */
	private static final int FLEE_TICKS = 200;

	private @Nullable UUID owner;
	private String encounter = "";
	private boolean setUp;
	private int kneelTicks;
	private int fleeTicks = -1;
	private @Nullable AcolyteCombatGoal combat;

	public SithAcolyte(EntityType<? extends SithAcolyte> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	/** Default attributes. Balance values replace them on each acolyte's first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		AcolyteBalance d = new AcolyteBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, d.health)
				.add(Attributes.ARMOR, d.armor)
				.add(Attributes.MOVEMENT_SPEED, d.speed)
				.add(Attributes.FOLLOW_RANGE, d.followRange)
				.add(Attributes.KNOCKBACK_RESISTANCE, d.knockbackResistance)
				.add(Attributes.ATTACK_DAMAGE, d.damage);
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
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.combat = new AcolyteCombatGoal(this);
		this.goalSelector.addGoal(2, combat);
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
			@Override
			public boolean canUse() {
				return !isKneeling() && super.canUse();
			}
		});
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return mode() == Mode.HOSTILE && !isYielded() && super.canUse();
			}
		});
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
			@Override
			public boolean canUse() {
				return mode() == Mode.HOSTILE && !isYielded() && super.canUse();
			}
		});
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_GUARD, GuardState.READY.ordinal());
		builder.define(DATA_MODE, Mode.HOSTILE.ordinal());
		builder.define(DATA_SKIN, Skin.ACOLYTE.ordinal());
		builder.define(DATA_YIELDED, false);
		builder.define(DATA_KNEELING, false);
		builder.define(DATA_SHOVING, false);
	}

	// ---------------------------------------------------------------- state

	public Mode mode() {
		return Mode.byOrdinal(this.entityData.get(DATA_MODE));
	}

	/** {@link Mode#RIVAL} also makes it persistent. */
	public void setMode(Mode mode) {
		this.entityData.set(DATA_MODE, mode.ordinal());

		if (mode == Mode.RIVAL) {
			setPersistenceRequired();
			setTarget(null);
		}
	}

	public Skin skin() {
		return Skin.byOrdinal(this.entityData.get(DATA_SKIN));
	}

	public void setSkin(Skin skin) {
		this.entityData.set(DATA_SKIN, skin.ordinal());
	}

	public @Nullable UUID owner() {
		return owner;
	}

	/** The owning player (a rival's story owner, or the encounter's owner). */
	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
	}

	public String encounter() {
		return encounter;
	}

	/** Tags the acolyte as part of an encounter; also makes it persistent. */
	public void setEncounter(String encounter) {
		this.encounter = encounter;
		setPersistenceRequired();
	}

	/** The duel profile: {@code rival} in rival mode, otherwise {@code acolyte}. */
	public DuelistProfile profile() {
		return DuelistProfiles.get(mode() == Mode.RIVAL ? DuelistProfiles.RIVAL : DuelistProfiles.ACOLYTE);
	}

	/** Kneeling: yielded, or just lost a spar. Synced for the pose. */
	public boolean isKneeling() {
		return this.entityData.get(DATA_KNEELING);
	}

	/** The Force Shove wind-up is showing (hand thrust forward). Synced for the pose. */
	public boolean isShoving() {
		return this.entityData.get(DATA_SHOVING);
	}

	void setShoving(boolean shoving) {
		this.entityData.set(DATA_SHOVING, shoving);
	}

	/**
	 * A Force Push hit the acolyte. During the shove wind-up this is the push clash: the shove is
	 * cancelled and the acolyte staggered. Returns true if it clashed. Server side.
	 */
	public boolean onPushed() {
		if (combat == null || !combat.isShoveWindingUp()) {
			return false;
		}

		combat.clash();
		return true;
	}

	/**
	 * Starts the Force Shove wind-up now (scripted scenes and tests); see
	 * {@link AcolyteCombatGoal#beginShove}. Returns false before the goals exist. Server side.
	 */
	public boolean startShove() {
		if (combat == null || isKneeling()) {
			return false;
		}

		combat.beginShove(level().getGameTime());
		return true;
	}

	/** Whether it may fight {@code target}: hostile acolytes always, a rival only its spar partner. */
	boolean mayFight(LivingEntity target) {
		return !isKneeling() && (mode() == Mode.HOSTILE || Spar.isPartner(this));
	}

	// ---------------------------------------------------------------- SaberDuelist

	@Override
	public GuardState guardState() {
		GuardState[] values = GuardState.values();
		return values[Mth.clamp(this.entityData.get(DATA_GUARD), 0, values.length - 1)];
	}

	@Override
	public void setGuardState(GuardState state) {
		this.entityData.set(DATA_GUARD, state.ordinal());
	}

	@Override
	public ItemStack duelSaber() {
		return getMainHandItem();
	}

	@Override
	public void onSparEnd(ServerPlayer player, SparResult result) {
		if (result == SparResult.WON) {
			kneelTicks = SPAR_KNEEL_TICKS;
			this.entityData.set(DATA_KNEELING, true);
			getNavigation().stop();
			player.sendSystemMessage(Component.translatable("message.oldrepublic.saber.acolyte_concedes", getDisplayName()));
		}
	}

	// ---------------------------------------------------------------- Yieldable

	@Override
	public boolean canYield() {
		return mode() == Mode.RIVAL && !Spar.isPartner(this);
	}

	@Override
	public float yieldHealthFraction() {
		return AcolyteBalance.get().rivalTrialYield;
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
		this.entityData.set(DATA_KNEELING, yielded || kneelTicks > 0);

		if (yielded) {
			setTarget(null);
			getNavigation().stop();
			setGuardState(GuardState.READY);
		}
	}

	/** Spared or fled: it gets up and leaves for good. Executed: the identity feature kills it. */
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
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance();
			equipSaber();
		}

		if (kneelTicks > 0 && --kneelTicks == 0) {
			this.entityData.set(DATA_KNEELING, isYielded());
		}

		if (isKneeling()) {
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

		if (getNavigation().isDone()) {
			Player nearest = level.getNearestPlayer(this, 32);
			Vec3 away = nearest == null ? DefaultRandomPos.getPos(this, 16, 7) : DefaultRandomPos.getPosAway(this, 16, 7, nearest.position());

			if (away != null) {
				getNavigation().moveTo(away.x, away.y, away.z, 1.3);
			}
		}
	}

	/** Applies balance attributes once per loaded acolyte (attribute defaults are fixed at startup). */
	private void applyBalance() {
		AcolyteBalance b = AcolyteBalance.get();
		boolean fullHealth = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, b.health);
		setBase(Attributes.ARMOR, b.armor);
		setBase(Attributes.MOVEMENT_SPEED, b.speed);
		setBase(Attributes.FOLLOW_RANGE, b.followRange);
		setBase(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance);
		setBase(Attributes.ATTACK_DAMAGE, b.damage);

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

	/** A red Duelist-emitter saber, lit. Never dropped. */
	private void equipSaber() {
		ItemStack held = getMainHandItem();

		if (!(SaberComponents.isIgnited(held))) {
			ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(new UUID(0L, 0L), "", BladeColor.RED.rgb()), BladeColor.RED,
					Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, SaberToggle.balance());
			SaberToggle.setIgnited(saber, true, level().getGameTime());
			setItemSlot(EquipmentSlot.MAINHAND, saber);
		}

		setDropChance(EquipmentSlot.MAINHAND, 0f);
		setCanPickUpLoot(false);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
		equipSaber();

		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.STRUCTURE) {
			setPersistenceRequired();
		}

		return result;
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		// The saber is built in equipSaber (it needs the balance and the game time).
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// A rival outside a spar does not answer blows; it still takes them (and can yield).
		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && mode() == Mode.RIVAL && !Spar.isPartner(this)) {
			setTarget(null);
		}

		return hurt;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return owner == null && encounter.isEmpty() && mode() == Mode.HOSTILE && super.removeWhenFarAway(distanceSqr);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.ACOLYTE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.ACOLYTE_DEATH;
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("AcolyteMode", mode().ordinal());
		output.putInt("AcolyteSkin", skin().ordinal());
		output.putBoolean("AcolyteYielded", isYielded());
		output.putString("AcolyteEncounter", encounter);
		output.storeNullable("AcolyteOwner", UUIDUtil.CODEC, owner);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_MODE, Mode.byOrdinal(input.getIntOr("AcolyteMode", 0)).ordinal());
		setSkin(Skin.byOrdinal(input.getIntOr("AcolyteSkin", 0)));
		boolean yielded = input.getBooleanOr("AcolyteYielded", false);
		this.entityData.set(DATA_YIELDED, yielded);
		this.entityData.set(DATA_KNEELING, yielded);
		this.encounter = input.getStringOr("AcolyteEncounter", "");
		this.owner = input.read("AcolyteOwner", UUIDUtil.CODEC).orElse(null);
	}
}
