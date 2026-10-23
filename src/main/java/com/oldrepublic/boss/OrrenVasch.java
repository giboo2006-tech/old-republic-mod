package com.oldrepublic.boss;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltMaterials;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.NpcGuard;
import com.oldrepublic.saber.duel.OwnsInterrupts;
import com.oldrepublic.saber.duel.SaberDuelist;

import net.minecraft.core.Holder;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
 * Orren Vasch, the Collector (spec 8.1-8.4): a saber duelist of the {@code ARCHIVE} side who lives
 * only inside an {@link ArenaSession}. Like the M2 {@code SithAcolyte} he is a {@link SaberDuelist}
 * (so the saber, parry and deflection code needs no new branch) and a {@link Yieldable}, but his yield
 * belongs to the session: {@link #canYield()} is false, so the identity feature's generic yield flow
 * never tracks him, and {@link #mobInteract} reopens his yield dialogue. He {@link OwnsInterrupts}:
 * {@link CollectorReactions} decides which causes cancel which tell.
 *
 * <p>Damage gates (8.4), in {@link #hurtServer}: refused outside a running fight, during the
 * transitions and the yield, from non-participants and from anyone outside the hall box; immune to
 * falls, drowning, fire, poison and wither. The phase thresholds are floors: {@link #setHealth}
 * never lets a hit carry him past the next threshold, which the session turns into the transition,
 * the yield, or the echo's dissolving.
 *
 * <p>Synced for the renderer: the guard state, the phase (hood and holocron in phase 3), the current
 * {@link Action} (poses) and flags (kneeling, echo, fallen, forming).
 */
public class OrrenVasch extends Monster implements SaberDuelist, Aligned, Yieldable, OwnsInterrupts {
	/** What his body is doing, for the client's poses. Synced by ordinal; append only. */
	public enum Action {
		NONE,
		/** Off-hand raised, a chunk tearing loose. */
		HURL,
		/** Hand drawn back. */
		PALM,
		/** Blade tip dragged along the floor. */
		LASH,
		/** Crouched, blade low. */
		LUNGE,
		/** Holocron raised, the cloak lifting and streaming out on the inrushing air. */
		SURGE,
		/** Sagging after the Surge. */
		SPENT,
		/** Fingers curled toward the target, then the channel. */
		GRIP,
		/** The leap to the dais and the crystal lifted (transition one). */
		LEAP,
		/** Taking up the holocron as the pylons shatter into him (transition two). */
		UNKEPT,
		/** Reading at the lectern. */
		READING,
		/** Wrists bound, walked out by an escort (scenes 0, 3, 5). */
		CUFFED;

		public static Action of(int ordinal) {
			Action[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}
	}

	private static final EntityDataAccessor<Integer> DATA_GUARD = SynchedEntityData.defineId(OrrenVasch.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(OrrenVasch.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(OrrenVasch.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(OrrenVasch.class, EntityDataSerializers.BYTE);
	private static final int FLAG_KNEELING = 1;
	private static final int FLAG_ECHO = 2;
	private static final int FLAG_FALLEN = 4;
	private static final int FLAG_FORMING = 8;
	/** The yield dialogue W2 writes; he opens it himself (the session decides when). */
	public static final Identifier YIELD_DIALOGUE = OldRepublicMod.id("c3_collector_yield");
	/** Default walking speed (no {@code collector} key: like the acolyte's). */
	private static final double SPEED = 0.30;

	private @Nullable ArenaSession session;
	private @Nullable CollectorCombatGoal combat;
	private boolean setUp;
	private long spentUntil;
	/** Set while one of his own counterplay staggers asks for its duration: no duration modifier then. */
	private boolean exactControl;
	/** Set while the session itself changes his health (scaling, reset): the phase floor does not apply. */
	private boolean healthUnlocked;
	/** Client: the client tick when {@link #action()} last changed (pose timing). */
	private int actionStartTick;

	public OrrenVasch(EntityType<? extends OrrenVasch> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	/** Default attributes; the session sets the scaled maximum and the balance armour on spawn. */
	public static AttributeSupplier.Builder createAttributes() {
		CollectorBalance d = new CollectorBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, d.maxHealth)
				.add(Attributes.ARMOR, d.armor)
				.add(Attributes.MOVEMENT_SPEED, SPEED)
				.add(Attributes.FOLLOW_RANGE, d.readyRange)
				.add(Attributes.KNOCKBACK_RESISTANCE, d.knockbackResistance)
				.add(Attributes.ATTACK_DAMAGE, d.p1LightDamage);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.combat = new CollectorCombatGoal(this);
		this.goalSelector.addGoal(2, combat);
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12f) {
			@Override
			public boolean canUse() {
				return !isFallen() && super.canUse();
			}
		});
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return !isKneeling() && !isFallen() && action() != Action.READING && super.canUse();
			}
		});
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_GUARD, GuardState.READY.ordinal());
		builder.define(DATA_PHASE, CollectorRules.PHASE_ONE);
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

	/** Joins him to the session that spawned him (server side; never saved). */
	void bind(ArenaSession session) {
		this.session = session;
	}

	public @Nullable ArenaSession session() {
		return session;
	}

	/** His fight goal (moves, tells, interrupts); null only before his goals are registered. */
	public @Nullable CollectorCombatGoal combat() {
		return combat;
	}

	/** The fight phase (1-3); phase 3 shows the hood and the holocron. */
	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	void setPhase(int phase) {
		this.entityData.set(DATA_PHASE, Mth.clamp(phase, CollectorRules.PHASE_ONE, CollectorRules.PHASE_THREE));
	}

	public Action action() {
		return Action.of(this.entityData.get(DATA_ACTION));
	}

	/** Server: what his body does now (the client poses it). */
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

	public boolean isKneeling() {
		return flag(FLAG_KNEELING);
	}

	/** The rematch's echo: desaturated and smoky (renderer), never yields. */
	public boolean isEcho() {
		return flag(FLAG_ECHO);
	}

	void setEcho(boolean echo) {
		setFlag(FLAG_ECHO, echo);
	}

	/** Lying where the execution's stroke left him (scene 2). */
	public boolean isFallen() {
		return flag(FLAG_FALLEN);
	}

	void setFallen(boolean fallen) {
		setFlag(FLAG_FALLEN, fallen);

		if (fallen) {
			setFlag(FLAG_KNEELING, false);
			getNavigation().stop();
		}
	}

	/** The echo is still gathering out of smoke (renderer fades him in). */
	public boolean isForming() {
		return flag(FLAG_FORMING);
	}

	void setForming(boolean forming) {
		setFlag(FLAG_FORMING, forming);
	}

	/** Spent after the Surge: guard broken, x{@code spentMultiplier} taken, full controls, liftable. */
	public boolean isSpent() {
		return level().getGameTime() < spentUntil;
	}

	void setSpent(int ticks) {
		spentUntil = level().getGameTime() + ticks;
		NpcGuard.breakGuard(this, ticks);
		setAction(Action.SPENT);
	}

	void clearSpent() {
		spentUntil = 0;
	}

	/** Whether his counterplay stagger is asking for its duration (the control modifier then stays out). */
	boolean exactControl() {
		return exactControl;
	}

	/**
	 * One of the fight's own counterplay staggers (a ripped Hurl, a returned chunk, a palm clash, a
	 * broken Grip): exactly {@code baseTicks}, with diminishing returns but without the outside-Spent
	 * control reduction. Cancels his current move. Returns the ticks applied.
	 */
	public int counterStagger(int baseTicks) {
		exactControl = true;
		int ticks;

		try {
			ticks = ControlEffects.diminished(this, baseTicks, ControlEffects.STAGGER);
		} finally {
			exactControl = false;
		}

		if (ticks > 0) {
			NpcGuard.stagger(this, ticks);

			if (combat != null) {
				combat.abortMove(false);
			}
		}

		return ticks;
	}

	/** A Pull ripped the Hurl chunk out of his grip: he staggers ({@code hurlRipStaggerTicks}). */
	void onHurlRipped(@Nullable ServerPlayer caster) {
		counterStagger(CollectorBalance.get().hurlRipStaggerTicks);
	}

	/** A Lift caught the Hurl chunk: the throw is lost, nothing else. */
	void onHurlCaught() {
		if (combat != null) {
			combat.abortMove(false);
		}
	}

	/** The session changes his health directly: no phase floor. */
	void setHealthUnlocked(float health) {
		healthUnlocked = true;

		try {
			setHealth(health);
		} finally {
			healthUnlocked = false;
		}
	}

	/** Sets his maximum health and keeps (or fills) his current health. */
	void setMaxHealth(float max, boolean keepFraction) {
		float oldMax = getMaxHealth();
		float fraction = oldMax > 0f ? getHealth() / oldMax : 1f;
		setBase(Attributes.MAX_HEALTH, max);
		setHealthUnlocked(keepFraction ? CollectorRules.keepFraction(fraction * oldMax, oldMax, max) : max);
	}

	/** Lights or retracts his amber blade (lit to fight, unlit at the lectern and when he kneels). */
	void setBladeLit(boolean lit) {
		ItemStack saber = getMainHandItem();

		if (SaberComponents.isIgnited(saber) != lit && !saber.isEmpty()) {
			SaberToggle.setIgnited(saber, lit, level().getGameTime());
		}
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
	public float deflectConeDegrees() {
		return CollectorBalance.get().deflectConeDegrees;
	}

	@Override
	public float deflectSpreadDegrees() {
		return CollectorBalance.get().deflectSpreadDegrees;
	}

	@Override
	public Allegiance allegiance() {
		return Allegiance.ARCHIVE;
	}

	// ---------------------------------------------------------------- Yieldable (the session owns his yield)

	/** Never through the generic yield flow: the session yields him at 10% (8.3). */
	@Override
	public boolean canYield() {
		return false;
	}

	@Override
	public float yieldHealthFraction() {
		return CollectorBalance.get().yieldAt;
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

	/** Resolved through {@link BossArenas#resolve}, never through the generic yield outcomes. */
	@Override
	public void onYieldResolved(YieldOutcome outcome) {
	}

	@Override
	public @Nullable Identifier yieldDialogue() {
		return YIELD_DIALOGUE;
	}

	/** The owner reopens the yield dialogue by using him; a credited helper their stance dialogue. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer && session != null
				&& session.onVaschUsed(serverPlayer)) {
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
			applyBalance();
			equipSaber();
		}

		if (session == null || session.vasch() != this) {
			// Orphaned (his session ended, or a stray copy): he only ever exists inside a session.
			discard();
			return;
		}

		if (spentUntil > 0 && !isSpent()) {
			spentUntil = 0;

			if (action() == Action.SPENT) {
				setAction(Action.NONE);
			}
		}

		if (isKneeling() || isFallen()) {
			getNavigation().stop();
		}
	}

	/** Armour and knockback resistance from the balance (the session sets his maximum health). */
	private void applyBalance() {
		CollectorBalance b = CollectorBalance.get();
		setBase(Attributes.ARMOR, b.armor);
		setBase(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance);
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);

		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	/**
	 * An amber blade from a squared archival hilt (8.1): the Collector set (MILESTONE_7.md 7.1, 7.5), the Reliquary
	 * emitter, the Reliquary grip in starfall iron and the Archivist pommel. Never dropped.
	 */
	private void equipSaber() {
		if (getMainHandItem().isEmpty()) {
			ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(new UUID(0L, 0L), "", BladeColor.AMBER.rgb()), BladeColor.AMBER,
					Emitter.RELIQUARY, Grip.RELIQUARY, Pommel.ARCHIVIST, new HiltMaterials(HiltMaterial.STEEL, HiltMaterial.STARFALL, HiltMaterial.STEEL),
					SaberToggle.balance());
			setItemSlot(EquipmentSlot.MAINHAND, saber);
		}

		setDropChance(EquipmentSlot.MAINHAND, 0f);
		setCanPickUpLoot(false);
		setBladeLit(session != null && session.isFighting());
	}

	// ---------------------------------------------------------------- damage (8.4)

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}

		ArenaSession current = session;

		if (current == null || !current.acceptsDamage(this, source)) {
			return false;
		}

		float before = getHealth();
		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt) {
			current.onVaschHurt(source, Math.max(0f, before - getHealth()));
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
	 * The phase floors (8.3): while his session fights, no hit takes him below the current phase's
	 * threshold; reaching it is reported to the session (transition, yield, or the echo dissolving).
	 */
	@Override
	public void setHealth(float health) {
		ArenaSession current = session;

		if (current != null && !healthUnlocked && !level().isClientSide()) {
			float floor = current.healthFloor();

			if (floor > 0f && health <= floor) {
				super.setHealth(floor);
				current.onFloorReached();
				return;
			}
		}

		super.setHealth(health);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (session != null && !level().isClientSide()) {
			session.onVaschLost();
		}
	}

	/** Only while his session fights (8.1): the antechamber bed works between attempts and while he kneels. */
	@Override
	public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
		return session != null && session.isFighting();
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	/**
	 * His session owns his lifetime, on Peaceful too (6.9: there he talks, then kneels), so vanilla
	 * despawning is off; without this a Monster is discarded every tick on Peaceful.
	 */
	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.BOSS_COLLECTOR_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}

	/** Where his raised off-hand is (the Hurl chunk, the Grip, the holocron). */
	public Vec3 offHandPoint() {
		Vec3 look = getLookAngle().multiply(1, 0, 1);
		Vec3 forward = look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : look.normalize();
		Vec3 left = new Vec3(forward.z, 0, -forward.x);
		return position().add(forward.scale(0.45)).add(left.scale(0.45)).add(0, getBbHeight() * 0.95, 0);
	}

	/** His chest, where the Surge rays start. */
	public Vec3 chestPoint() {
		return position().add(0, getBbHeight() * 0.7, 0);
	}
}
