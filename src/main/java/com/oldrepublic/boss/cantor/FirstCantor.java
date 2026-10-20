package com.oldrepublic.boss.cantor;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.raid.RaidBalance;
import com.oldrepublic.boss.raid.RaidRules;
import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The First Cantor (spec MILESTONE_5.md 12.3): the Sith who wrote the Hymn, bound into the song-engine
 * under the Heart. A towering spectral figure (2.6 blocks) in robes of light and shadow who floats
 * where the song holds him and never walks; he lives only inside a {@link CantorSession}, which
 * drives every move he makes (the verse beam, the slam, the chain lightning, the verses, the engine).
 *
 * <p>Damage gates, in {@link #hurtServer}: refused outside the fight, while he rises, during the phase
 * transitions and while he unravels, from anyone who is not a listed participant, and while any
 * choir echo sings (the shield). Each hit is scaled by the M4 health-cap trick and doubled while a
 * silenced point's window is open. The phase thresholds are floors ({@link #setHealth}): no hit
 * carries him past the next one, and at the last floor (1 health) he unravels into song. He takes
 * no knock-back (Push does not move him), and his reactions refuse Choke and Lift
 * ({@link CantorReactions}).
 */
public class FirstCantor extends Monster implements Aligned {
	/** What his body does, for the client's poses. Synced by ordinal; append only. */
	public enum Action {
		NONE,
		/** Rising out of the engine, arms at his sides. */
		RISE,
		/** One arm swept out along the verse beam. */
		BEAM,
		/** Both fists raised over his head: the slam's tell and the blow. */
		SLAM,
		/** A hand flung out, lightning leaping from it. */
		CHAIN,
		/** Head back, singing a verse (the transitions, a verse, the shades' call). */
		VERSE,
		/** The engine's hymn: arms spread wide, the song pouring out of him. */
		HYMN,
		/** Coming apart into drifting light. */
		UNRAVEL;

		public static Action of(int ordinal) {
			Action[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}
	}

	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(FirstCantor.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(FirstCantor.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(FirstCantor.class, EntityDataSerializers.BYTE);
	private static final int FLAG_SHIELDED = 1;
	private static final int FLAG_WINDOW = 2;
	/** A stray copy without a session leaves after this long. */
	private static final int STRAY_TICKS = 20;

	private @Nullable CantorSession session;
	/** Where the song holds him (the session moves it while he rises); null until bound. */
	private @Nullable Vec3 hover;
	/** Set while the session itself changes his health (scaling, a phase jump): no floor then. */
	private boolean healthUnlocked;
	/** His scaled maximum health (above the attribute's cap, hits are scaled down instead); 0 until set. */
	private double scaledMax;
	/** Client: the client tick when {@link #action()} last changed (pose timing). */
	private int actionStartTick;

	public FirstCantor(EntityType<? extends FirstCantor> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.noPhysics = true;
		setNoGravity(true);
	}

	/** Default attributes; the session sets the scaled maximum when he rises. */
	public static AttributeSupplier.Builder createAttributes() {
		RaidBalance b = new RaidBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, RaidRules.attributeHealth(b.maxHealth))
				.add(Attributes.ARMOR, b.armor)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ATTACK_DAMAGE, b.slamDamage);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24f) {
			@Override
			public boolean canUse() {
				return action() != Action.UNRAVEL && super.canUse();
			}
		});
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, 0);
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

	/** Joins him to the session that raised him (server side; never saved). */
	void bind(CantorSession session, Vec3 hover) {
		this.session = session;
		this.hover = hover;
	}

	public @Nullable CantorSession session() {
		return session;
	}

	/** Where the song holds him now. */
	void setHover(Vec3 hover) {
		this.hover = hover;
	}

	/** The fight's phase (0 while he rises, then 1 to 3). */
	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	void setPhase(int phase) {
		this.entityData.set(DATA_PHASE, Mth.clamp(phase, 0, RaidRules.PHASE_HYMN));
	}

	public Action action() {
		return Action.of(this.entityData.get(DATA_ACTION));
	}

	/** Server: what his body does now (the client poses it). */
	void setAction(Action action) {
		if (action() != action) {
			this.entityData.set(DATA_ACTION, action.ordinal());
		}
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
		byte next = (byte) (on ? flags | bit : flags & ~bit);

		if (next != flags) {
			this.entityData.set(DATA_FLAGS, next);
		}
	}

	/** The choir's song shields him (the renderer draws the pale motes settling on his robes). */
	public boolean isShielded() {
		return flag(FLAG_SHIELDED);
	}

	void setShielded(boolean shielded) {
		setFlag(FLAG_SHIELDED, shielded);
	}

	/** A silenced point's window is open: he takes double damage (the renderer dims his light). */
	public boolean isWindowOpen() {
		return flag(FLAG_WINDOW);
	}

	void setWindowOpen(boolean open) {
		setFlag(FLAG_WINDOW, open);
	}

	/** His scaled maximum health: the attribute, or more when the scaling passes its 1024 cap. */
	public double scaledMaxHealth() {
		return scaledMax > 0 ? scaledMax : getMaxHealth();
	}

	/**
	 * Rescales his maximum health, keeping the fraction he has left (a participant joined in the first
	 * {@code joinTicks}). The attribute stops at the cap; past it the scaling lives in
	 * {@link RaidRules#damageScale}.
	 */
	void rescale(double maxHealth) {
		scaledMax = maxHealth;
		AttributeInstance instance = getAttribute(Attributes.MAX_HEALTH);
		double attribute = RaidRules.attributeHealth(maxHealth);

		if (instance == null || Math.abs(instance.getBaseValue() - attribute) < 1.0e-3) {
			return;
		}

		float fraction = getMaxHealth() <= 0f ? 1f : getHealth() / getMaxHealth();
		instance.setBaseValue(attribute);
		setHealthUnlocked((float) attribute * fraction);
	}

	/** The session changes his health directly: no floor. */
	void setHealthUnlocked(float health) {
		healthUnlocked = true;

		try {
			setHealth(health);
		} finally {
			healthUnlocked = false;
		}
	}

	/** His chest, where the beam and the lightning leave from. */
	public Vec3 chest() {
		return position().add(0, getBbHeight() * 0.62, 0);
	}

	/** The Hymn's own: the Hymnsworn shades are his allies, and his hazards pass them by. */
	@Override
	public Allegiance allegiance() {
		return Allegiance.HYMN;
	}

	// ---------------------------------------------------------------- behaviour

	@Override
	public void tick() {
		this.noPhysics = true;
		super.tick();

		if (level().isClientSide()) {
			return;
		}

		CantorSession current = session;

		if (current == null || current.cantor() != this) {
			// Orphaned (his raid ended, or a stray summon): he only ever exists inside a session.
			if (tickCount > STRAY_TICKS || current != null) {
				discard();
			}

			return;
		}

		// The song holds him: nothing moves him (Push, knock-back, a shove).
		setDeltaMovement(Vec3.ZERO);
		Vec3 at = hover;

		if (at != null && position().distanceToSqr(at) > 1.0e-4) {
			setPos(at);
		}
	}

	/** He floats where the song holds him: no walking, falling or drifting. */
	@Override
	public void travel(Vec3 input) {
	}

	// ---------------------------------------------------------------- damage (12.3)

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}

		CantorSession current = session;

		if (current == null || !current.acceptsCantorDamage(this, source)) {
			return false;
		}

		boolean hurt = super.hurtServer(level, source, amount * current.damageMultiplier());

		if (hurt) {
			current.onCantorHurt(this);
		}

		return hurt;
	}

	/** Song has no lungs or skin: falls, drowning, fire, walls and wither do nothing. */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FIRE)
				|| source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.WITHER) || super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		Holder<?> type = effect.getEffect();
		return !type.equals(MobEffects.POISON) && !type.equals(MobEffects.WITHER) && !type.equals(MobEffects.LEVITATION)
				&& super.canBeAffected(effect);
	}

	/**
	 * The phase floors (12.3, the M3 rule): while his session fights, no hit takes him below the
	 * current phase's threshold, nor below 1 in the last; reaching it is reported to the session
	 * (the next phase, or the unravelling).
	 */
	@Override
	public void setHealth(float health) {
		CantorSession current = session;

		if (current != null && !healthUnlocked && !level().isClientSide()) {
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
			session.onCantorLost(this);
		}
	}

	/** Only while his raid runs: the undercroft is no place to sleep then. */
	@Override
	public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
		return session != null && session.isFighting();
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

	/** His session owns his lifetime. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	/** The rewards are the raid's ({@code RaidRewards}): he drops nothing himself. */
	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.BOSS_CANTOR_HURT;
	}

	/** His end is the session's unravelling. */
	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}
}
