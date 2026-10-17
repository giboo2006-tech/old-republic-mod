package com.oldrepublic.enemy.construct;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.identity.FrontierSettler;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.skill.SkillPassives;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Ruin Sentinel (spec 7.7): a 2.3-block stone and bronze construct with a chest lumen, in two
 * {@link SentinelVariant variants}. It stands dormant as a statue in an alcove and checks for waking
 * every 10 ticks: a player within {@code wakeRange} (or W3's {@code skill.quietStepWake} against
 * {@code quiet_step}, {@link SkillPassives#sentinelWakeRadius}) wakes it, a hit wakes it, and {@link Sentinels#wakeNear} wakes it. Watchers
 * sleep on (and bow their heads) for players at alignment {@code >= dormantAlignment}, Tomb
 * sentinels for {@code <= -dormantAlignment}; a {@link #isBound() bound} sentinel sleeps for no one.
 * In Peaceful dormant sentinels never wake for players, and a bound sentinel with an owner crumbles
 * when its owner arrives ("The binding has failed.").
 *
 * <p>Awake, it fights with two telegraphed attacks run by its own small state machine
 * ({@link Action}, synced for the pose): the {@link #beginSlam() slam} (a 90 degree arc in front,
 * then the core is exposed) and the {@link #beginLance lance} (one heavy {@code SENTINEL} bolt that
 * only a parry returns). {@link SentinelSlamGoal} and {@link SentinelLanceGoal} decide when to start
 * them. A stagger or any wind-up interrupt cancels a tell. Out of a fight for a while, an unbound or
 * worldgen sentinel walks back to its post and sleeps again; boss sentinels never do.
 *
 * <p>NBT: {@code Variant} (lowercase name; an ordinal is read too), {@code Bound}, {@code Owner},
 * {@code Dormant}, {@code Post}, {@code PostYaw}. Boss sentinels ({@link Allegiance#ARCHIVE}) are
 * never saved.
 */
public class RuinSentinel extends Monster implements Aligned {
	/** What the sentinel is doing. Synced by ordinal for the client pose: append only. */
	public enum Action {
		IDLE,
		/** The wake tell: lumen igniting, stone grinding, dust shedding. */
		WAKING,
		/** Arms raised before the slam. */
		SLAM_TELL,
		/** Chest lumen drawing motes before the lance. */
		LANCE_TELL,
		/** After a slam: chest plates parted, damage taken x{@code exposedMultiplier}. */
		EXPOSED,
		/** A short pause after an attack or a cancelled tell. */
		RECOVER,
		/** Staggered (a returned lance). */
		STAGGERED;

		static Action byOrdinal(int ordinal) {
			Action[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
		}
	}

	/** The pause after an attack or a cancelled tell. */
	static final int RECOVER_TICKS = 10;
	/** Fixed: a stone construct barely moves when struck (Push has its own multiplier, see {@link SentinelReactions}). */
	static final double KNOCKBACK_RESISTANCE = 0.6;
	/** Out of a fight this long, a sentinel heads back to its post. */
	private static final int RESLEEP_TICKS = 200;
	/** In Peaceful, a bound sentinel crumbles once its owner comes this close. */
	private static final double PEACEFUL_CRUMBLE_RANGE = 16.0;
	/** Where its slam starts: centre to centre, a little beyond the slam reach (the tell gives time to step out). */
	static final double SLAM_START_RANGE = 3.0;
	/** Degrees the sentinel may turn per tick while tracking during a tell. */
	private static final float TRACK_DEGREES = 12f;

	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(RuinSentinel.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(RuinSentinel.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_BOWING = SynchedEntityData.defineId(RuinSentinel.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(RuinSentinel.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_ARCHIVE = SynchedEntityData.defineId(RuinSentinel.class, EntityDataSerializers.BOOLEAN);

	private boolean bound;
	private @Nullable UUID owner;
	private @Nullable BlockPos post;
	private float postYaw;
	private boolean setUp;
	private int actionTicks;
	/** Ticks of the current action at its start (client and server), for the pose. */
	private int actionLength;
	/** Client: the tick the current action began, for the pose. Server: the same, for tells. */
	private int actionStartTick;
	/** The tick the bow last began or ended (both sides), for the pose. */
	private int bowStartTick;
	private long slamReadyAt;
	private long lanceReadyAt;
	private @Nullable LivingEntity lanceTarget;
	private @Nullable UUID wakeCause;
	private boolean wakeEffectPending;
	private int idleTicks;
	/** The player credited when the binding fails in Peaceful. */
	private @Nullable ServerPlayer crumbleCredit;
	private boolean deathHandled;

	public RuinSentinel(EntityType<? extends RuinSentinel> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	/** Default attributes (Watcher values). The balance replaces them per variant on the first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		SentinelBalance d = new SentinelBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, d.watcherHealth)
				.add(Attributes.ARMOR, d.armor)
				.add(Attributes.MOVEMENT_SPEED, d.watcherSpeed)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
				.add(Attributes.ATTACK_DAMAGE, d.slamDamage);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SentinelLanceGoal(this));
		this.goalSelector.addGoal(3, new SentinelSlamGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return canFight() && super.canUse();
			}
		});
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
			@Override
			public boolean canUse() {
				return canFight() && super.canUse();
			}
		});
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, SentinelVariant.WATCHER.ordinal());
		builder.define(DATA_DORMANT, true);
		builder.define(DATA_BOWING, false);
		builder.define(DATA_ACTION, Action.IDLE.ordinal());
		builder.define(DATA_ARCHIVE, false);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);

		if (DATA_ACTION.equals(key)) {
			actionStartTick = this.tickCount;
		} else if (DATA_BOWING.equals(key)) {
			bowStartTick = this.tickCount;
		}
	}

	// ---------------------------------------------------------------- state

	public SentinelVariant variant() {
		SentinelVariant[] values = SentinelVariant.values();
		return values[Mth.clamp(this.entityData.get(DATA_VARIANT), 0, values.length - 1)];
	}

	/** Sets the variant and applies its health and speed (at full health). */
	public void setVariant(SentinelVariant variant) {
		this.entityData.set(DATA_VARIANT, variant.ordinal());

		if (!level().isClientSide()) {
			applyBalance(true);
		}
	}

	/** Re-bound to guard someone's trail: always hostile, sleeps for no side. */
	public boolean isBound() {
		return bound;
	}

	public void setBound(boolean bound) {
		this.bound = bound;
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
	}

	public boolean isDormant() {
		return this.entityData.get(DATA_DORMANT);
	}

	/** A dormant Watcher with its head lowered for a passing player of its side. Synced. */
	public boolean isBowing() {
		return this.entityData.get(DATA_BOWING);
	}

	/** Ticks since the bow began or ended (client: since the synced change arrived), for the pose. */
	public int bowAge() {
		return Math.max(0, this.tickCount - bowStartTick);
	}

	public Action action() {
		return Action.byOrdinal(this.entityData.get(DATA_ACTION));
	}

	/** Ticks since the current action began (client: since the synced change arrived). */
	public int actionAge() {
		return Math.max(0, this.tickCount - actionStartTick);
	}

	/** One of Vasch's bound sentinels in the Reliquary: allegiance {@link Allegiance#ARCHIVE}, never saved. */
	public boolean isArchive() {
		return this.entityData.get(DATA_ARCHIVE);
	}

	void setArchive(boolean archive) {
		this.entityData.set(DATA_ARCHIVE, archive);
	}

	@Override
	public Allegiance allegiance() {
		return isArchive() ? Allegiance.ARCHIVE : Allegiance.ANCIENT;
	}

	/** Its post: where it sleeps, and the way it faces there. */
	public void setPost(BlockPos pos, float yaw) {
		this.post = pos.immutable();
		this.postYaw = yaw;
	}

	public @Nullable BlockPos post() {
		return post;
	}

	/** After a slam, while its core is exposed (damage taken x{@code exposedMultiplier}). */
	public boolean isCoreExposed() {
		return action() == Action.EXPOSED;
	}

	/** Whether it is awake and free to start an attack. */
	public boolean isReady() {
		return !isDormant() && action() == Action.IDLE && !isDeadOrDying();
	}

	boolean canFight() {
		return !isDormant() && action() != Action.WAKING && !isDeadOrDying();
	}

	long slamReadyAt() {
		return slamReadyAt;
	}

	long lanceReadyAt() {
		return lanceReadyAt;
	}

	/**
	 * Whether this dormant sentinel sleeps on for {@code player}: never when bound; a Watcher for
	 * alignment {@code >= dormantAlignment}, a Tomb sentinel for {@code <= -dormantAlignment}.
	 */
	public boolean isDormantFor(Player player) {
		return isDormant() && sleepsFor(player);
	}

	/** The side rule of {@link #isDormantFor}, whether or not it is dormant now. */
	public boolean sleepsFor(Player player) {
		if (bound) {
			return false;
		}

		int threshold = SentinelBalance.get().dormantAlignment;
		int alignment = Identity.alignment(player);
		return variant() == SentinelVariant.TOMB ? alignment <= -threshold : alignment >= threshold;
	}

	private boolean peaceful() {
		return level().getDifficulty() == Difficulty.PEACEFUL;
	}

	// ---------------------------------------------------------------- waking

	/**
	 * Wakes the sentinel with its tell ({@code wakeTicks}: the lumen ignites, stone grinds, dust
	 * sheds). {@code cause} becomes its target once the tell is over. Does nothing if it is awake.
	 */
	public void wake(@Nullable Player cause) {
		if (!isDormant() || level().isClientSide()) {
			return;
		}

		this.entityData.set(DATA_DORMANT, false);
		this.entityData.set(DATA_BOWING, false);
		wakeCause = cause == null ? null : cause.getUUID();
		idleTicks = 0;
		setAction(Action.WAKING, SentinelBalance.get().wakeTicks);
		// Sent on the next tick, so a sentinel woken in the tick it was added is already tracked.
		wakeEffectPending = true;
	}

	/** Sleeps again at its post (the statue pose); cancels any action and the target. */
	void sleep() {
		this.entityData.set(DATA_DORMANT, true);
		setAction(Action.IDLE, 0);
		setTarget(null);
		getNavigation().stop();
		lanceTarget = null;
		wakeCause = null;
	}

	// ---------------------------------------------------------------- attacks

	/** Starts the slam tell now, ignoring the cooldown (scripted scenes and tests). Server side. */
	public void beginSlam() {
		if (level().isClientSide() || isDeadOrDying()) {
			return;
		}

		wakeQuietly();
		SentinelBalance b = SentinelBalance.get();
		setAction(Action.SLAM_TELL, b.slamTellTicks);
		EffectEvents.toTracking(this, SentinelEffects.SLAM, armsPosition(), facing(), getId(), b.slamTellTicks, SentinelEffects.SLAM_TELL);
	}

	/** Starts the lance tell at {@code target} now, ignoring the cooldown and range (scenes and tests). Server side. */
	public void beginLance(LivingEntity target) {
		if (level().isClientSide() || isDeadOrDying()) {
			return;
		}

		wakeQuietly();
		SentinelBalance b = SentinelBalance.get();
		lanceTarget = target;
		setTarget(target);
		setAction(Action.LANCE_TELL, b.lanceTellTicks);
		EffectEvents.toTracking(this, SentinelEffects.LANCE, lumenPosition(), facing(), getId(), b.lanceTellTicks, SentinelEffects.LANCE_CHARGE);
	}

	/** A scripted attack on a dormant sentinel wakes it without the wake tell. */
	private void wakeQuietly() {
		if (isDormant()) {
			this.entityData.set(DATA_DORMANT, false);
			this.entityData.set(DATA_BOWING, false);
		}
	}

	/**
	 * A wind-up interrupt ({@code ForceReactions} cause): a slam or lance tell is cancelled and the
	 * sentinel recovers briefly. Wake tells, exposure and staggers run on. Server side.
	 */
	public void interrupt() {
		Action action = action();

		if (action == Action.SLAM_TELL || action == Action.LANCE_TELL) {
			lanceTarget = null;
			setAction(Action.RECOVER, RECOVER_TICKS);
		}
	}

	/** Staggers it for {@code ticks}: no attacks, any tell cancelled. Server side. */
	public void stagger(int ticks) {
		if (ticks <= 0 || isDormant()) {
			return;
		}

		lanceTarget = null;
		getNavigation().stop();
		setAction(Action.STAGGERED, Math.max(ticks, action() == Action.STAGGERED ? actionTicks : 0));
	}

	private void setAction(Action action, int ticks) {
		this.entityData.set(DATA_ACTION, action.ordinal());
		this.actionTicks = ticks;
		this.actionLength = ticks;
		this.actionStartTick = this.tickCount;
	}

	/** Ticks of the current action in total. */
	int actionLength() {
		return actionLength;
	}

	// ---------------------------------------------------------------- ticking

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance(false);

			if (post == null) {
				setPost(blockPosition(), getYRot());
			}
		}

		if (wakeEffectPending) {
			wakeEffectPending = false;
			EffectEvents.toTracking(this, SentinelEffects.WAKE, lumenPosition(), facing(), getId(), SentinelBalance.get().wakeTicks, SentinelEffects.WAKE_RISE);
		}

		if (isDormant()) {
			tickDormant(level);
			return;
		}

		tickAction(level);
	}

	/** The statue: holds still and checks for waking every 10 ticks. */
	private void tickDormant(ServerLevel level) {
		getNavigation().stop();

		if (getTarget() != null) {
			setTarget(null);
		}

		if ((this.tickCount + getId()) % 10 != 0) {
			return;
		}

		SentinelBalance b = SentinelBalance.get();
		boolean peaceful = peaceful();

		if (peaceful && bound && owner != null) {
			ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);

			if (ownerPlayer != null && ownerPlayer.level() == level && ownerPlayer.distanceToSqr(this) <= PEACEFUL_CRUMBLE_RANGE * PEACEFUL_CRUMBLE_RANGE) {
				crumble(level, ownerPlayer);
				return;
			}
		}

		boolean bow = false;
		Player waker = null;

		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}

			double distance = player.distanceTo(this);

			if (distance > b.wakeRange) {
				continue;
			}

			if (sleepsFor(player)) {
				bow |= variant() == SentinelVariant.WATCHER;
				continue;
			}

			// quiet_step (W3's rule and number, skill.quietStepWake): the radius shrinks to 4 for that player.
			double range = Math.min(b.wakeRange, SkillPassives.sentinelWakeRadius(player, Mth.ceil(b.wakeRange)));

			if (!peaceful && distance <= range && waker == null) {
				waker = player;
			}
		}

		if (waker != null) {
			wake(waker);
			return;
		}

		if (bow != isBowing()) {
			this.entityData.set(DATA_BOWING, bow);

			if (bow) {
				EffectEvents.toTracking(this, SentinelEffects.WAKE, headPosition(), facing(), getId(), 0f, SentinelEffects.WAKE_BOW);
			}
		}
	}

	private void tickAction(ServerLevel level) {
		Action action = action();

		if (action != Action.IDLE) {
			getNavigation().stop();
		}

		switch (action) {
			case WAKING -> {
				if (--actionTicks <= 0) {
					setAction(Action.IDLE, 0);
					Player cause = wakeCause == null ? null : level.getServer().getPlayerList().getPlayer(wakeCause);
					wakeCause = null;

					if (cause != null && cause.isAlive() && canAttack(cause)) {
						setTarget(cause);
					}
				}
			}
			case SLAM_TELL -> {
				LivingEntity target = getTarget();

				// Tracks the target in the first half of the tell, then commits to its facing.
				if (target != null && actionTicks > actionLength / 2) {
					turnToward(target.position(), TRACK_DEGREES);
				}

				if (--actionTicks <= 0) {
					slamImpact(level);
				}
			}
			case LANCE_TELL -> {
				LivingEntity target = lanceTarget;

				if (target == null || !target.isAlive() || target.level() != level) {
					setAction(Action.RECOVER, RECOVER_TICKS);
					lanceTarget = null;
					break;
				}

				turnToward(target.position(), TRACK_DEGREES);
				getLookControl().setLookAt(target, 30f, 30f);

				if (--actionTicks <= 0) {
					fireLance(level, target);
				}
			}
			case EXPOSED, RECOVER, STAGGERED -> {
				if (--actionTicks <= 0) {
					setAction(Action.IDLE, 0);
				}
			}
			case IDLE -> tickIdle(level);
		}
	}

	/** Out of a fight: back to the post and to sleep (never for boss sentinels). */
	private void tickIdle(ServerLevel level) {
		if (getTarget() != null || isArchive() || post == null) {
			idleTicks = 0;
			return;
		}

		if (++idleTicks < RESLEEP_TICKS) {
			return;
		}

		Vec3 home = Vec3.atBottomCenterOf(post);

		if (position().distanceToSqr(home) <= 1.5 * 1.5) {
			snapTo(home.x, getY(), home.z, postYaw, 0f);
			setYHeadRot(postYaw);
			setYBodyRot(postYaw);
			sleep();
			idleTicks = 0;
		} else if (getNavigation().isDone()) {
			getNavigation().moveTo(home.x, home.y, home.z, 1.0);

			if (idleTicks > RESLEEP_TICKS * 4) {
				// Cannot path home (the alcove was walled up): sleep where it stands.
				setPost(blockPosition(), getYRot());
			}
		}
	}

	/** The slam lands: the arc in front, then the core is exposed. */
	private void slamImpact(ServerLevel level) {
		SentinelBalance b = SentinelBalance.get();
		Vec3 facing = facing();
		double reach = b.slamRadius + getBbWidth() * 0.5;

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach + 1.0, 1.0, reach + 1.0))) {
			if (victim == this || !victim.isAlive() || Allegiances.friendly(this, victim) || victim instanceof Player p && p.isSpectator()) {
				continue;
			}

			Vec3 to = victim.position().subtract(position()).horizontal();
			double distance = to.length() - victim.getBbWidth() * 0.5;

			if (distance > reach || !SaberGuard.inFront(this, victim.position(), b.slamArcDegrees)
					|| victim.getY() > getY() + 2.0 || victim.getY() + victim.getBbHeight() < getY() - 1.0) {
				continue;
			}

			SaberCombat.Strike strike = SaberCombat.Strike.npcHeavy(SaberToggle.balance().heavyGuardDrainMult);

			if (SaberCombat.strike(victim, level, damageSources().mobAttack(this), b.slamDamage, strike)) {
				Vec3 away = to.lengthSqr() < 1.0e-4 ? facing : to.normalize();
				float kb = b.slamKnockback * ForcePowers.knockbackFactor(victim, 1f);
				victim.setDeltaMovement(victim.getDeltaMovement().add(away.x * kb, 0.25 * kb, away.z * kb));
				victim.hurtMarked = true;
			}
		}

		long now = level.getGameTime();
		slamReadyAt = now + b.slamCooldown;
		Vec3 centre = position().add(facing.scale(b.slamRadius * 0.55 + getBbWidth() * 0.5));
		EffectEvents.toTracking(this, SentinelEffects.SLAM, centre, facing, getId(), b.slamRadius, SentinelEffects.SLAM_IMPACT);
		setAction(Action.EXPOSED, b.exposedTicks);
		EffectEvents.toTracking(this, SentinelEffects.CORE, lumenPosition(), facing, getId(), b.exposedTicks, 0);
	}

	/** The lance: one heavy amber-white bolt at the target's chest. */
	private void fireLance(ServerLevel level, LivingEntity target) {
		SentinelBalance b = SentinelBalance.get();
		lanceTarget = null;
		lanceReadyAt = level.getGameTime() + b.lanceCooldown;
		setAction(Action.RECOVER, RECOVER_TICKS);

		if (!hasLineOfSight(target)) {
			return;
		}

		Vec3 muzzle = lumenPosition().add(facing().scale(0.35));
		Vec3 aim = target.getBoundingBox().getCenter().subtract(muzzle);
		int life = Mth.ceil(b.lanceMax() / Math.max(0.1f, b.lanceSpeed)) + 10;
		BlasterBolt bolt = BlasterBolt.create(level, this, muzzle, b.lanceDamage, life, BlasterBolt.Style.SENTINEL, true);
		bolt.shoot(aim.x, aim.y, aim.z, b.lanceSpeed, 0f);
		level.addFreshEntity(bolt);
		EffectEvents.toTracking(this, SentinelEffects.LANCE, muzzle, aim.normalize(), getId(), 1f, SentinelEffects.LANCE_FIRE);
	}

	/** Turns body and head toward {@code pos} by at most {@code maxDegrees}. */
	private void turnToward(Vec3 pos, float maxDegrees) {
		Vec3 to = pos.subtract(position());

		if (to.horizontalDistanceSqr() < 1.0e-4) {
			return;
		}

		float wanted = (float) (Mth.atan2(to.z, to.x) * (180.0 / Math.PI)) - 90f;
		float yaw = Mth.approachDegrees(getYRot(), wanted, maxDegrees);
		setYRot(yaw);
		setYBodyRot(yaw);
		setYHeadRot(yaw);
	}

	/** Horizontal facing. */
	public Vec3 facing() {
		return Vec3.directionFromRotation(0f, getYRot());
	}

	/** The chest lumen (where the lance leaves and the core opens). */
	public Vec3 lumenPosition() {
		return position().add(0, getBbHeight() * 0.62, 0).add(facing().scale(getBbWidth() * 0.45));
	}

	private Vec3 armsPosition() {
		return position().add(0, getBbHeight() * 0.9, 0);
	}

	private Vec3 headPosition() {
		return position().add(0, getBbHeight() * 0.88, 0).add(facing().scale(0.2));
	}

	// ---------------------------------------------------------------- damage and death

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Entity attacker = source.getEntity();

		if (isDormant() && attacker instanceof LivingEntity && attacker != this) {
			// Attacked: it wakes, whatever its side (but never for a player in Peaceful).
			if (!(attacker instanceof Player player)) {
				wake(null);
			} else if (!peaceful()) {
				wake(player);
			}
		}

		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && source.getDirectEntity() instanceof BlasterBolt bolt && bolt.heavy() && bolt.parried()) {
			// Its own lance, parried back: a stagger (diminishing returns apply, as to every control).
			int ticks = ControlEffects.stagger(attacker, this, SentinelBalance.get().lanceReturnStaggerTicks, 2, ControlEffects.STAGGER);
			stagger(ticks);
		}

		return hurt;
	}

	/** Immune to falling, drowning and wither damage (poison and wither effects never take hold). */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.WITHER)
				|| super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		Holder<?> type = effect.getEffect();
		return !type.equals(MobEffects.POISON) && !type.equals(MobEffects.WITHER) && super.canBeAffected(effect);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !(target instanceof FrontierSettler) && !Allegiances.friendly(this, target) && super.canAttack(target);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target instanceof FrontierSettler ? null : target);
	}

	/** A statue does not get shoved around. */
	@Override
	public boolean isPushable() {
		return !isDormant() && super.isPushable();
	}

	/** A dormant sentinel never keeps a player from sleeping (a bed near a ruin works). */
	@Override
	public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
		return !isDormant() && super.isPreventingPlayerRest(level, player);
	}

	/** Garrisons stay; boss sentinels are not saved at all. */
	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return !isArchive() && super.shouldBeSaved();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (!(level() instanceof ServerLevel level) || deathHandled || !isDeadOrDying()) {
			return;
		}

		deathHandled = true;
		setAction(Action.IDLE, 0);
		EffectEvents.toTracking(this, SentinelEffects.DEATH, lumenPosition(), facing(), getId(), 1f, variant().ordinal());
		ServerPlayer credited = source.getEntity() instanceof ServerPlayer p ? p
				: getKillCredit() instanceof ServerPlayer p ? p : null;

		if (credited != null) {
			QuestHooks.onConstructKilled(credited, this, variant());
		}
	}

	/**
	 * Peaceful: the binding fails when its owner arrives; it crumbles with no drops, and the owner is
	 * credited so a combat objective completes.
	 */
	private void crumble(ServerLevel level, ServerPlayer ownerPlayer) {
		if (deathHandled) {
			return;
		}

		deathHandled = true;
		crumbleCredit = ownerPlayer;
		EffectEvents.toTracking(this, SentinelEffects.DEATH, lumenPosition(), facing(), getId(), 1f, variant().ordinal());
		ownerPlayer.sendOverlayMessage(Component.translatable("boss.oldrepublic.sentinel.binding_failed"));
		QuestHooks.onConstructKilled(ownerPlayer, this, variant());
		discard();
	}

	/** The player last credited for a Peaceful crumble (tests). */
	@Nullable ServerPlayer crumbleCredit() {
		return crumbleCredit;
	}

	/** The plates (loot table {@code entities/ruin_sentinel}) drop only with {@code durasteelChance}. */
	@Override
	protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKill) {
		if (getRandom().nextFloat() < SentinelBalance.get().durasteelChance) {
			super.dropFromLootTable(level, source, playerKill);
		}
	}

	/** Lumen thread (Watcher) or grave silk (Tomb), with {@code threadChance}. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean playerKill) {
		super.dropCustomDeathLoot(level, source, playerKill);

		if (getRandom().nextFloat() < SentinelBalance.get().threadChance) {
			spawnAtLocation(level, new ItemStack(variant() == SentinelVariant.TOMB ? M3Items.GRAVE_SILK : M3Items.LUMEN_THREAD));
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.SENTINEL_HURT;
	}

	/** {@code sentinel.death} plays from the {@code sentinel_death} effect handler, with the crumble. */
	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}

	@Override
	protected float getSoundVolume() {
		return 1.2f;
	}

	// ---------------------------------------------------------------- balance and saving

	private void applyBalance(boolean heal) {
		SentinelBalance b = SentinelBalance.get();
		SentinelVariant v = variant();
		boolean full = heal || getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, b.health(v));
		setBase(Attributes.ARMOR, b.armor);
		setBase(Attributes.MOVEMENT_SPEED, b.speed(v));

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

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Variant", variant().name().toLowerCase(Locale.ROOT));
		output.putBoolean("Bound", bound);
		output.storeNullable("Owner", UUIDUtil.CODEC, owner);
		output.putBoolean("Dormant", isDormant());
		output.storeNullable("Post", BlockPos.CODEC, post);
		output.putFloat("PostYaw", postYaw);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_VARIANT, readVariant(input).ordinal());
		this.bound = input.getBooleanOr("Bound", false);
		this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		this.entityData.set(DATA_DORMANT, input.getBooleanOr("Dormant", true));
		this.post = input.read("Post", BlockPos.CODEC).orElse(null);
		this.postYaw = input.getFloatOr("PostYaw", getYRot());
	}

	/** {@code Variant}: the lowercase variant name ({@code watcher}, {@code tomb}), read tolerantly: a name, else an ordinal, else Watcher. */
	static SentinelVariant readVariant(ValueInput input) {
		SentinelVariant[] values = SentinelVariant.values();
		String name = input.getStringOr("Variant", "");

		for (SentinelVariant v : values) {
			if (v.name().equalsIgnoreCase(name)) {
				return v;
			}
		}

		int ordinal = input.getInt("Variant").orElse(-1);
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SentinelVariant.WATCHER;
	}
}
