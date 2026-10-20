package com.oldrepublic.boss.ulvaine;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
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
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.SaberDuelist;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Darth Ulvaine (spec MILESTONE_5.md 12.2): a tall Sith duellist of the {@code HYMN} who lives only
 * inside an {@link UlvaineSession}. Like the Collector she is a {@link SaberDuelist} (so the saber,
 * parry and deflection code treat her blade like any duellist's) and a {@link Yieldable} whose yield
 * belongs to the session ({@link #canYield()} is false). Her fight is {@link UlvaineCombatGoal}.
 *
 * <p>Damage gates, in {@link #hurtServer}: refused outside a running fight, during the phase
 * transitions and while she kneels, and from anyone who is not a participant; each hit is scaled by
 * the M4 health-cap trick and, while a pylon chanter lives, cut to {@code pylonDamagePct}. The phase
 * thresholds are floors ({@link #setHealth}): no hit carries her past the next one, and reaching it
 * is the session's transition, her yield, or the echo's fading. Stasis holds her at most
 * {@code stasisMaxTicks}; Choke and Lift follow {@link UlvaineReactions}.
 *
 * <p>Synced for the renderer: the guard, the phase, what her body does ({@link Action}) and flags
 * (kneeling, the echo, the echo forming).
 */
public class DarthUlvaine extends Monster implements SaberDuelist, Aligned, Yieldable {
	/** What her body is doing, for the client's poses. Synced by ordinal; append only. */
	public enum Action {
		NONE,
		/** Off-hand outstretched, Force lightning pouring from the fingers. */
		LIGHTNING,
		/** The Hymn step's tell: drawn in on herself, the air shimmering violet. */
		STEP,
		/** Palm thrust forward: the Push's wind-up. */
		PUSH,
		/** Arms raised to the storm: the second phase's transition, a rod's call. */
		STORM,
		/** Singing, head back: the third phase's transition, a pulse, the Crescendo. */
		SING,
		/** Wrists bound before her (the "bind her" scene). */
		BOUND;

		public static Action of(int ordinal) {
			Action[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}
	}

	private static final EntityDataAccessor<Integer> DATA_GUARD = SynchedEntityData.defineId(DarthUlvaine.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(DarthUlvaine.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(DarthUlvaine.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(DarthUlvaine.class, EntityDataSerializers.BYTE);
	private static final int FLAG_KNEELING = 1;
	private static final int FLAG_ECHO = 2;
	private static final int FLAG_FORMING = 4;
	/** A stray copy without a session leaves after this long. */
	private static final int STRAY_TICKS = 20;
	/** The status effect W6 registers for Stasis (looked up by id, so this file needs no W6 class). */
	private static final Identifier STASIS = OldRepublicMod.id("stasis");

	private @Nullable UlvaineSession session;
	private @Nullable UlvaineCombatGoal combat;
	private boolean setUp;
	/** Set while the session itself changes her health (scaling, a phase jump): no floor then. */
	private boolean healthUnlocked;
	/** Her scaled maximum health (above the attribute's cap, hits are scaled down instead); 0 until set. */
	private double scaledMax;
	/** The resolution's end: the only time she may die. */
	private boolean ending;
	/** Ticks the current Stasis has held her. */
	private int stasisTicks;
	/** Client: the client tick when {@link #action()} last changed (pose timing). */
	private int actionStartTick;

	public DarthUlvaine(EntityType<? extends DarthUlvaine> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	/** Default attributes; the session sets the scaled maximum on spawn. */
	public static AttributeSupplier.Builder createAttributes() {
		UlvaineBalance b = new UlvaineBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, b.maxHealth)
				.add(Attributes.ARMOR, b.armor)
				.add(Attributes.MOVEMENT_SPEED, b.movementSpeed)
				.add(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ATTACK_DAMAGE, b.damage);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.combat = new UlvaineCombatGoal(this);
		this.goalSelector.addGoal(1, combat);
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16f) {
			@Override
			public boolean canUse() {
				return !isKneeling() && super.canUse();
			}
		});
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return !isKneeling() && super.canUse();
			}
		});
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_GUARD, GuardState.READY.ordinal());
		builder.define(DATA_PHASE, UlvaineRules.PHASE_DUEL);
		builder.define(DATA_ACTION, Action.NONE.ordinal());
		builder.define(DATA_FLAGS, (byte) 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);

		if (DATA_ACTION.equals(key)) {
			actionStartTick = tickCount;
		}
	}

	// ---------------------------------------------------------------- session and state

	/** Joins her to the session that spawned her (server side; never saved). */
	void bind(UlvaineSession session, boolean echo) {
		this.session = session;
		setFlag(FLAG_ECHO, echo);
	}

	public @Nullable UlvaineSession session() {
		return session;
	}

	/** Her fight goal; null only before her goals are registered. */
	public @Nullable UlvaineCombatGoal combat() {
		return combat;
	}

	/** The fight phase (1 to 3). */
	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	void setPhase(int phase) {
		this.entityData.set(DATA_PHASE, Mth.clamp(phase, UlvaineRules.PHASE_DUEL, UlvaineRules.PHASE_HYMN));
	}

	public Action action() {
		return Action.of(this.entityData.get(DATA_ACTION));
	}

	/** Server: what her body does now (the client poses it). */
	void setAction(Action action) {
		this.entityData.set(DATA_ACTION, action.ordinal());
	}

	/** Client: ticks since {@link #action()} last changed. */
	public int actionAge() {
		return tickCount - actionStartTick;
	}

	private boolean flag(int bit) {
		return (this.entityData.get(DATA_FLAGS) & bit) != 0;
	}

	private void setFlag(int bit, boolean on) {
		byte flags = this.entityData.get(DATA_FLAGS);
		this.entityData.set(DATA_FLAGS, (byte) (on ? flags | bit : flags & ~bit));
	}

	/** Kneeling in her yield. */
	public boolean isKneeling() {
		return flag(FLAG_KNEELING);
	}

	/** Her daily echo: drawn translucent, never yields. */
	public boolean isEcho() {
		return flag(FLAG_ECHO);
	}

	/** The echo is still gathering out of the Hymn (the renderer fades her in). */
	public boolean isForming() {
		return flag(FLAG_FORMING);
	}

	void setForming(boolean forming) {
		setFlag(FLAG_FORMING, forming);
	}

	/** Her scaled maximum health: the attribute, or more when the scaling passes its 1024 cap. */
	public double scaledMaxHealth() {
		return scaledMax > 0 ? scaledMax : getMaxHealth();
	}

	/**
	 * Rescales her maximum health, keeping the fraction she has left (a participant joined or left).
	 * The attribute stops at {@code KaastRules.HEALTH_CAP}; past it the scaling lives in
	 * {@link UlvaineRules#damageScale}.
	 */
	void rescale(double maxHealth) {
		scaledMax = maxHealth;
		AttributeInstance instance = getAttribute(Attributes.MAX_HEALTH);
		double attribute = UlvaineRules.attributeHealth(maxHealth);

		if (instance == null || Math.abs(instance.getBaseValue() - attribute) < 1.0e-3) {
			return;
		}

		float fraction = getMaxHealth() <= 0f ? 1f : getHealth() / getMaxHealth();
		instance.setBaseValue(attribute);
		setHealthUnlocked((float) attribute * fraction);
	}

	/** The session changes her health directly: no floor. */
	void setHealthUnlocked(float health) {
		healthUnlocked = true;

		try {
			setHealth(health);
		} finally {
			healthUnlocked = false;
		}
	}

	/** Lights or retracts her blade (lit to fight, unlit while she kneels). */
	void setBladeLit(boolean lit) {
		ItemStack saber = getMainHandItem();

		if (!saber.isEmpty() && SaberComponents.isIgnited(saber) != lit) {
			SaberToggle.setIgnited(saber, lit, level().getGameTime());
		}
	}

	/** The resolution's end ("end her"): the one death she is allowed. */
	void end(ServerLevel level) {
		ending = true;
		kill(level);
	}

	// ---------------------------------------------------------------- SaberDuelist, Aligned

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
	public Allegiance allegiance() {
		return Allegiance.HYMN;
	}

	// ---------------------------------------------------------------- Yieldable (the session owns her yield)

	/** Never through the generic yield flow: the session yields her at 10% (12.2). */
	@Override
	public boolean canYield() {
		return false;
	}

	@Override
	public float yieldHealthFraction() {
		return UlvaineBalance.get().yieldAt;
	}

	@Override
	public @Nullable UUID yieldDecider() {
		return session == null ? null : session.owner();
	}

	@Override
	public boolean isYielded() {
		return isKneeling();
	}

	@Override
	public void setYielded(boolean yielded) {
		setFlag(FLAG_KNEELING, yielded);

		if (yielded) {
			setTarget(null);
			getNavigation().stop();
			setGuardState(GuardState.READY);
			setAction(Action.NONE);
			setBladeLit(false);
		}
	}

	/** Resolved through {@link UlvaineFight#resolve}, never through the generic yield outcomes. */
	@Override
	public void onYieldResolved(YieldOutcome outcome) {
	}

	/** The owner reopens the yield's choice by using her while she kneels (the M3 rule). */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer && session != null && session.onUsed(serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		return super.mobInteract(player, hand);
	}

	// ---------------------------------------------------------------- behaviour

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			equipSaber();
		}

		if (session == null || session.ulvaine() != this) {
			// Orphaned (her fight ended, or a stray summon): she only ever exists inside a session.
			if (tickCount > STRAY_TICKS || session != null) {
				discard();
			}

			return;
		}

		if (isKneeling()) {
			getNavigation().stop();
		}
	}

	/** Stasis is watched here, not in {@link #customServerAiStep}: a held mob's AI does not run. */
	@Override
	public void tick() {
		super.tick();

		if (!level().isClientSide() && session != null && !isRemoved()) {
			tickStasis();
		}
	}

	/** She breaks a Stasis after {@code stasisMaxTicks} (9.2, 12.2). */
	private void tickStasis() {
		Optional<Holder.Reference<MobEffect>> stasis = BuiltInRegistries.MOB_EFFECT.get(STASIS);

		if (stasis.isEmpty() || !hasEffect(stasis.get())) {
			stasisTicks = 0;
			return;
		}

		if (++stasisTicks >= Math.max(1, UlvaineBalance.get().stasisMaxTicks)) {
			removeEffect(stasis.get());
			stasisTicks = 0;
		}
	}

	/** Ticks the current Stasis has held her (the tests). */
	public int stasisTicks() {
		return stasisTicks;
	}

	/** A violet-red blade from a curved duellist's hilt (12.2). Never dropped. */
	private void equipSaber() {
		if (getMainHandItem().isEmpty()) {
			ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(new UUID(0L, 0L), "", BladeColor.RED.rgb()), BladeColor.RED, Emitter.DUELIST,
					Grip.RIDGED, Pommel.BALANCED, SaberToggle.balance());
			setItemSlot(EquipmentSlot.MAINHAND, saber);
		}

		setDropChance(EquipmentSlot.MAINHAND, 0f);
		setCanPickUpLoot(false);
		setBladeLit(!isKneeling());
	}

	// ---------------------------------------------------------------- damage (12.2)

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}

		UlvaineSession current = session;

		if (ending) {
			return super.hurtServer(level, source, amount);
		}

		if (current == null || !current.acceptsDamage(this, source)) {
			return false;
		}

		boolean hurt = super.hurtServer(level, source, amount * current.damageMultiplier());

		if (hurt) {
			current.onHurt(this);
		}

		return hurt;
	}

	/** Immune to falls, drowning, fire and wither damage (poison and wither never take hold). */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FIRE)
				|| source.is(DamageTypes.WITHER) || super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		Holder<?> type = effect.getEffect();
		return !type.equals(MobEffects.POISON) && !type.equals(MobEffects.WITHER) && super.canBeAffected(effect);
	}

	/**
	 * The phase floors (12.2, the M3 rule): while her session fights, no hit takes her below the
	 * current phase's threshold; reaching it is reported to the session.
	 */
	@Override
	public void setHealth(float health) {
		UlvaineSession current = session;

		if (current != null && !healthUnlocked && !ending && !level().isClientSide()) {
			float floor = current.healthFloor(getMaxHealth());

			if (floor > 0f && health <= floor) {
				super.setHealth(floor);
				current.onFloorReached(this);
				return;
			}
		}

		super.setHealth(health);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (session != null && !level().isClientSide()) {
			session.onUlvaineLost(this);
		}
	}

	/** Only while her fight runs: the Heart is no place to sleep then. */
	@Override
	public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
		return session != null && session.isFighting();
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	/** Her session owns her lifetime, on Peaceful too (she talks, then kneels), so vanilla despawning is off. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return !isKneeling() && super.isPushable();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.BOSS_ULVAINE_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}

	/** Her raised off-hand, where the lightning and the Push leave from. */
	public Vec3 handPoint() {
		Vec3 look = getLookAngle().multiply(1, 0, 1);
		Vec3 forward = look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : look.normalize();
		Vec3 left = new Vec3(forward.z, 0, -forward.x);
		return position().add(forward.scale(0.5)).add(left.scale(0.35)).add(0, getBbHeight() * 0.72, 0);
	}
}
