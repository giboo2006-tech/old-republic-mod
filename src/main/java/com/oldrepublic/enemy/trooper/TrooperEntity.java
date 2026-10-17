package com.oldrepublic.enemy.trooper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEffects;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.PoacherCoverGoal;
import com.oldrepublic.enemy.trooper.goal.TrooperEscortGoal;
import com.oldrepublic.enemy.trooper.goal.TrooperFollowGoal;
import com.oldrepublic.enemy.trooper.goal.TrooperHoldGoal;
import com.oldrepublic.enemy.trooper.goal.TrooperLeaveGoal;
import com.oldrepublic.enemy.trooper.goal.TrooperMoraleGoal;
import com.oldrepublic.enemy.trooper.goal.TrooperRouteGoal;
import com.oldrepublic.enemy.trooper.goal.TrooperShootGoal;
import com.oldrepublic.enemy.trooper.goal.TrooperSpecialGoal;
import com.oldrepublic.enemy.trooper.goal.TrooperStandoffGoal;
import com.oldrepublic.enemy.trooper.goal.TrooperTargetGoal;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.FrontierSettler;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.outpost.Outposts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Republic or Imperial trooper (spec 7.1 to 7.5): one class registered as {@code republic_trooper}
 * and {@code sith_trooper}. Not a vanilla {@code Monster} or {@code Enemy}: hostility comes from
 * {@link Attitudes} only ({@link TrooperAttitudeRule}), so an Allied garrison never stops a player
 * sleeping and officers survive Peaceful.
 *
 * <p>The role, action, downed, disarmed and yielded flags are synced, so the client poses the model
 * from synced data alone. The AI lives in {@code enemy.trooper.goal}: target choice, the shooting
 * cycle and band, cover, the role specials, morale, standoffs, the patrol route, following the
 * leader, escorts and walking home. The squad-wide social rules (Wary warning, "move along",
 * salutes, the standoff) run on the squad leader through {@link TrooperSocial}.
 */
public class TrooperEntity extends PathfinderMob implements Aligned, Yieldable {
	/** The {@code Role} ordinal. Synced: append only. */
	private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(TrooperEntity.class, EntityDataSerializers.INT);
	/** The {@link TrooperAction} ordinal. Synced: append only. */
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(TrooperEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_DOWNED = SynchedEntityData.defineId(TrooperEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_DISARMED = SynchedEntityData.defineId(TrooperEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_YIELDED = SynchedEntityData.defineId(TrooperEntity.class, EntityDataSerializers.BOOLEAN);

	/** Entity tag W2 puts on the I2 sergeant: it yields to {@code yield_border} at {@code borderYield}. */
	public static final String TAG_BORDER_INCIDENT = "oldrepublic.mission.i2_border_incident";
	public static final Identifier YIELD_BORDER = OldRepublicMod.id("yield_border");
	public static final Identifier YIELD_INTERCEPT = OldRepublicMod.id("yield_intercept");
	/** How long a salute pose is held (the {@code trooper_salute} intensity). */
	public static final int SALUTE_TICKS = 30;
	/** {@code BleedAt} value of a downed trooper that never bleeds out. */
	private static final long NO_BLEED = -1;
	/** A stood-up or spared trooper walking off goes this far from where it was when no home is known. */
	private static final int LEAVE_DISTANCE = 32;
	/** A kneel session ends when the kneeling player is farther than this. */
	private static final double KNEEL_REACH = 3.0;
	/** Gap after which a kneel is a new one and tending starts again from zero. */
	private static final int TEND_GAP_TICKS = 10;
	/** How often a bound trooper re-checks its post for a duplicate. */
	private static final int POST_CHECK_INTERVAL = 100;
	private static final int MORALE_INTERVAL = 10;
	/** A prisoner counts as escorted this long after the escort last led it. */
	private static final int ESCORT_GRACE_TICKS = 20;
	/** A mob that hurt the trooper stays a target this long. */
	private static final int HURT_BY_MEMORY = 200;

	/** The weapon a role fires: sets damage, cadence and bolt speed in {@link TrooperShootGoal}. */
	public enum Weapon {
		/** Republic rifle: bursts, then a vent. */
		REPUBLIC_RIFLE,
		/** Imperial rifle: one strong bolt per cooldown. */
		IMPERIAL_RIFLE,
		/** Medic and grenadier sidearm. */
		PISTOL,
		/** Officers. */
		NONE
	}

	// ---------------------------------------------------------------- saved state

	private @Nullable UUID squad;
	private @Nullable UUID owner;
	private IncidentMode incident = IncidentMode.NONE;
	private long incidentUntil;
	private final PlayerExpiries provoked = new PlayerExpiries();
	private final PlayerExpiries engaged = new PlayerExpiries();
	private long bleedAt = NO_BLEED;
	private @Nullable BlockPos terminal;
	private int terminalSlot = -1;
	/** Explicit yield threshold set by {@link Troopers#setYield}; negative when unset. */
	private float yieldFraction = -1f;
	private @Nullable Identifier yieldDialogueId;
	private int nameSeed;
	/** How many troopers the squad started with (morale). */
	private int squadSize = 1;
	/** The Imperial squad's flanker. */
	private boolean flanker;
	/** A Role was read from saved data (STRUCTURE spawns keep it), or a spawner set it. */
	private boolean roleFixed;
	/** A handed-over prisoner waiting for its escort. */
	private boolean bound;
	/** The yield was decided; the sergeant never yields again. */
	private boolean yieldResolved;

	// ---------------------------------------------------------------- transient server state

	private boolean setUp;
	private boolean deathHandled;
	private boolean patrol;
	/** Arrived on a flare outside a patrol: never saved, and walks off when the encounter ends. */
	private boolean reinforcement;
	private @Nullable List<BlockPos> route;
	private int routeIndex;
	private boolean escort;
	private @Nullable LivingEntity escortPrisoner;
	private @Nullable BlockPos escortExit;
	private int escortSide;
	private long escortSince;
	private long boundSince;
	/** Last game time an escort walked this prisoner (it then does not stand still or walk off alone). */
	private long escortedAt = TrooperRules.NEVER;
	private boolean leaving;
	private @Nullable Vec3 leaveTo;
	private long leaveSince;
	private long lastBark = TrooperRules.NEVER;
	private final List<Long> deflectedAt = new ArrayList<>(4);
	private long holdFireUntil;
	private @Nullable Vec3 repositionTo;
	private long ventUntil;
	private long rallyUntil;
	private boolean moraleUsed;
	private long retreatUntil;
	private @Nullable Vec3 retreatTo;
	private int tendProgress;
	private long lastTendTick = TrooperRules.NEVER;
	private @Nullable UUID kneeler;
	private @Nullable UUID defending;
	private long defendingUntil;
	/** Set by a Force interrupt or a heavy hit during a tell; the running goal drops the tell. */
	private boolean pendingInterrupt;

	// ---------------------------------------------------------------- both sides

	/** Ticks since {@link #action()} last changed. */
	private int actionTicks;

	// Assigned in registerGoals (called from the Mob constructor), so never initialised here.
	private TrooperSpecialGoal specialGoal;

	public TrooperEntity(EntityType<? extends TrooperEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	/** Default attributes. Each trooper applies its role's balance values on its first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		TrooperBalance d = new TrooperBalance();
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, d.republicRifleman[0])
				.add(Attributes.ARMOR, d.republicRifleman[1])
				.add(Attributes.MOVEMENT_SPEED, d.speed)
				.add(Attributes.FOLLOW_RANGE, d.followRange);
	}

	@Override
	protected void registerGoals() {
		this.specialGoal = new TrooperSpecialGoal(this);
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new TrooperHoldGoal(this));
		this.goalSelector.addGoal(1, new TrooperEscortGoal(this));
		this.goalSelector.addGoal(1, new TrooperLeaveGoal(this));
		this.goalSelector.addGoal(2, new TrooperMoraleGoal(this));
		this.goalSelector.addGoal(3, specialGoal);
		this.goalSelector.addGoal(4, new PoacherCoverGoal(this, TrooperEntity::coverSettings,
				() -> role() == TrooperRole.RIFLEMAN && canFight(), this::dropTell));
		this.goalSelector.addGoal(5, new TrooperShootGoal(this));
		this.goalSelector.addGoal(6, new TrooperStandoffGoal(this));
		this.goalSelector.addGoal(7, new TrooperRouteGoal(this));
		this.goalSelector.addGoal(8, new TrooperFollowGoal(this));
		this.goalSelector.addGoal(9, new MoveTowardsRestrictionGoal(this, 0.8));
		this.goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal(this, 0.6) {
			@Override
			public boolean canUse() {
				return strolls() && super.canUse();
			}
		});
		this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8f));
		this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new TrooperTargetGoal(this));
	}

	private static PoacherCoverGoal.Settings coverSettings() {
		TrooperBalance b = TrooperBalance.get();
		return new PoacherCoverGoal.Settings(b.coverHealthFraction, b.coverSpeed, b.coverHoldTicks[0],
				b.coverHoldTicks[b.coverHoldTicks.length - 1], b.coverCooldownTicks);
	}

	/** Free roaming only for lone troopers: squads follow their leader, patrols their route, officers stay put. */
	private boolean strolls() {
		return canFight() && !patrol && squad == null && getTarget() == null && !isOfficer();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, TrooperRole.RIFLEMAN.ordinal());
		builder.define(DATA_ACTION, TrooperAction.IDLE.ordinal());
		builder.define(DATA_DOWNED, false);
		builder.define(DATA_DISARMED, false);
		builder.define(DATA_YIELDED, false);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);

		if (DATA_ACTION.equals(accessor)) {
			actionTicks = 0;
		}
	}

	// ---------------------------------------------------------------- frozen API (client-safe members)

	/** The trooper's role. Synced. */
	public TrooperRole role() {
		TrooperRole[] values = TrooperRole.values();
		int ordinal = this.entityData.get(DATA_ROLE);
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : TrooperRole.RIFLEMAN;
	}

	/** {@link Faction#SITH_EMPIRE} for {@code sith_trooper}, {@link Faction#REPUBLIC} otherwise. */
	public Faction army() {
		return getType() == EnemyEntities.SITH_TROOPER ? Faction.SITH_EMPIRE : Faction.REPUBLIC;
	}

	public boolean isRepublic() {
		return army() == Faction.REPUBLIC;
	}

	/** {@code republic} or {@code imperial}: the lang and texture key. */
	public String armyKey() {
		return isRepublic() ? "republic" : "imperial";
	}

	/** The colour of this trooper's bolts. */
	public BlasterBolt.Style boltStyle() {
		return isRepublic() ? BlasterBolt.Style.REPUBLIC : BlasterBolt.Style.IMPERIAL;
	}

	/** What the trooper is visibly doing. Synced. */
	public TrooperAction action() {
		return TrooperAction.byOrdinal(this.entityData.get(DATA_ACTION));
	}

	/** Ticks since {@link #action()} last changed, on both sides. */
	public int actionTicks() {
		return actionTicks;
	}

	/** Downed and waiting to be tended. Synced. */
	public boolean isDowned() {
		return this.entityData.get(DATA_DOWNED);
	}

	/** Its weapon is pulled out of line by Disarming Pull: no firing, weapon hidden. Synced. */
	public boolean isDisarmed() {
		return this.entityData.get(DATA_DISARMED);
	}

	public boolean isOfficer() {
		return role() == TrooperRole.OFFICER;
	}

	@Override
	public boolean isYielded() {
		return this.entityData.get(DATA_YIELDED);
	}

	/**
	 * Where bolts leave the weapon: in front of the shooting shoulder at chest height (the same maths
	 * as {@code CrystalPoacher#muzzlePosition}).
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

	/** The squad UUID, fresh per {@link Troopers#spawnSquad}; null for a lone trooper. Server. */
	public @Nullable UUID squad() {
		return squad;
	}

	@Override
	public Allegiance allegiance() {
		return isRepublic() ? Allegiance.REPUBLIC : Allegiance.EMPIRE;
	}

	// ---------------------------------------------------------------- role, weapon, setup

	/** Sets the role (normalised for the army) and marks it fixed, so no spawn rolls another. */
	public void setRole(TrooperRole role) {
		this.entityData.set(DATA_ROLE, TrooperRules.normalise(army(), role).ordinal());
		this.roleFixed = true;

		if (setUp) {
			applyBalance();
			equipWeapon();
		}
	}

	/** The weapon this role fires. */
	public Weapon weapon() {
		return switch (role()) {
			case RIFLEMAN, SERGEANT -> isRepublic() ? Weapon.REPUBLIC_RIFLE : Weapon.IMPERIAL_RIFLE;
			case MEDIC, GRENADIER -> Weapon.PISTOL;
			case OFFICER -> Weapon.NONE;
		};
	}

	/** Applies the role's balance values and weapon. Runs on the first server tick, or at spawn. */
	void setUp() {
		setUp = true;
		this.entityData.set(DATA_ROLE, TrooperRules.normalise(army(), role()).ordinal());
		applyBalance();
		equipWeapon();
		setCanPickUpLoot(false);

		if (isOfficer()) {
			nameOfficer();
		}
	}

	private void applyBalance() {
		TrooperBalance b = TrooperBalance.get();
		float[] stats = TrooperRules.stats(army(), role(), b);
		setBase(Attributes.ARMOR, stats[1]);
		setBase(Attributes.MOVEMENT_SPEED, b.speed);
		setBase(Attributes.FOLLOW_RANGE, b.followRange);

		boolean fullHealth = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, stats[0]);

		if (fullHealth || getHealth() > getMaxHealth()) {
			setHealth(getMaxHealth());
		}
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);

		if (instance != null) {
			// API-CHECK: AttributeInstance#setBaseValue (standard, also used by CrystalPoacher).
			instance.setBaseValue(value);
		}
	}

	private void equipWeapon() {
		ItemStack weapon = switch (weapon()) {
			case REPUBLIC_RIFLE, IMPERIAL_RIFLE -> new ItemStack(EnemyItems.BLASTER_RIFLE);
			case PISTOL -> new ItemStack(EnemyItems.BLASTER_PISTOL);
			case NONE -> ItemStack.EMPTY;
		};

		if (getMainHandItem().getItem() != weapon.getItem()) {
			setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}

		setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		equipWeapon();
	}

	/**
	 * Spawn eggs, commands and structures. A Role that was read from saved data, or set by a spawner,
	 * is never re-rolled (spec 7.1: a STRUCTURE spawn keeps its Role); otherwise the role is drawn from
	 * the army's patrol composition.
	 */
	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);

		if (!roleFixed) {
			List<TrooperRole> roles = TrooperRules.defaultSquad(army(), 0, TrooperBalance.get());
			setRole(roles.get(getRandom().nextInt(roles.size())));
		}

		equipWeapon();

		if (reason == EntitySpawnReason.STRUCTURE || reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.COMMAND) {
			setPersistenceRequired();
		}

		return result;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		actionTicks++;

		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return;
		}

		long now = level.getGameTime();

		// Downed troopers have no AI (customServerAiStep does not run), so their clock is here.
		if (isDowned()) {
			if (bleedAt != NO_BLEED && now >= bleedAt) {
				discard();
				return;
			}

			tickKneel(level, now);
		}

		TrooperAction action = action();

		if (action == TrooperAction.SALUTE && actionTicks >= SALUTE_TICKS
				|| action == TrooperAction.VENTING && now >= ventUntil) {
			setAction(TrooperAction.IDLE);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp();
		}

		long now = level.getGameTime();
		this.entityData.set(DATA_DISARMED, ForceStatus.isDisarmed(this));

		if (rallyUntil != 0 && now >= rallyUntil) {
			rallyUntil = 0;
		}

		if (terminal != null && (tickCount == 1 || tickCount % POST_CHECK_INTERVAL == 0) && TrooperEvents.duplicateAtPost(level, this)) {
			discard();
			return;
		}

		if (bound && !isEscorted(now) && now - boundSince > TrooperBalance.get().escortDespawnTicks) {
			// Handed over, but no escort came: it walks off on its own.
			setBound(false);
			leave(null, position());
		}

		if (tickCount % MORALE_INTERVAL == 0) {
			checkMorale(level, now);
		}

		if (tickCount % TrooperSocial.INTERVAL == 0 && canFight() && Squads.leader(this) == this) {
			TrooperSocial.tick(level, this, now);
		}
	}

	// ---------------------------------------------------------------- actions and tells

	/** Shows {@code action} (synced). Starting a tell clears any interrupt that arrived before it. */
	public void setAction(TrooperAction action) {
		if (TrooperRules.interruptible(action)) {
			pendingInterrupt = false;
		}

		if (action() != action) {
			this.entityData.set(DATA_ACTION, action.ordinal());
			actionTicks = 0;
		}
	}

	/** A Force interrupt (stagger, Push, Pull, Disarming Pull, shock): cancels the current tell or channel. */
	public void interruptTell() {
		if (TrooperRules.interruptible(action())) {
			pendingInterrupt = true;
		}
	}

	/** True once after the current tell was interrupted; the goal running it drops it. */
	public boolean consumeInterrupt() {
		boolean value = pendingInterrupt;
		pendingInterrupt = false;
		return value;
	}

	/** Drops whatever tell is showing (cover, retreat). */
	public void dropTell() {
		if (TrooperRules.interruptible(action())) {
			setAction(TrooperAction.IDLE);
		}
	}

	/** Starts the Republic rifle's vent: no firing for {@code ventTicks}, steam, a reload bark. */
	public void startVent() {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}

		TrooperBalance b = TrooperBalance.get();
		ventUntil = level.getGameTime() + b.ventTicks;
		setAction(TrooperAction.VENTING);
		EffectEvents.toTracking(this, TrooperEffects.TROOPER_VENT, muzzlePosition(), null, getId(), b.ventTicks, 0);
		Barks.say(this, BarkCategory.RELOAD);
	}

	public boolean isVenting() {
		return level().getGameTime() < ventUntil;
	}

	/** Salutes {@code toward} (or straight ahead): the pose for {@link #SALUTE_TICKS}, the event and a bark. */
	public void salute(@Nullable Player toward) {
		if (toward != null) {
			getLookControl().setLookAt(toward, 30f, 30f);
		}

		setAction(TrooperAction.SALUTE);
		EffectEvents.toTracking(this, TrooperEffects.TROOPER_SALUTE, position(), getLookAngle(), getId(), SALUTE_TICKS, 0);
		Barks.say(this, BarkCategory.SALUTE);
	}

	/**
	 * Fires one bolt at {@code target} with this trooper's style and sends {@code blaster_fire}
	 * (intensity 1 while the squad has fire sounds left in this 4-tick window, else 0). Server only.
	 */
	public void fireAt(ServerLevel level, LivingEntity target, float damage, float speed) {
		TrooperBalance b = TrooperBalance.get();
		Vec3 muzzle = muzzlePosition();
		Vec3 aim = target.getEyePosition().subtract(0, 0.35, 0);

		// A target just below a ledge can be seen eye to eye while the chest line is blocked.
		if (blocked(level, muzzle, aim) && !blocked(level, muzzle, target.getEyePosition())) {
			aim = target.getEyePosition();
		}

		double flightTicks = muzzle.distanceTo(aim) / Math.max(0.1, speed);
		aim = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(flightTicks * 0.5));
		Vec3 dir = aim.subtract(muzzle);

		float inaccuracy = b.aimInaccuracy * switch (level.getDifficulty()) {
			case EASY -> 1.5f;
			case HARD -> 0.6f;
			default -> 1f;
		};

		BlasterBolt bolt = BlasterBolt.create(level, this, muzzle, damage, b.boltLifetimeTicks, boltStyle(), false);
		bolt.shoot(dir.x, dir.y, dir.z, speed, inaccuracy);
		level.addFreshEntity(bolt);

		Vec3 velocity = bolt.getDeltaMovement();
		Vec3 flight = velocity.lengthSqr() > 1.0e-6 ? velocity.normalize() : getLookAngle();
		EffectEvents.around(level, EnemyEffects.BLASTER_FIRE, muzzle, flight, getId(), Squads.claimFireSound(this) ? 1f : 0f, boltStyle().ordinal());
	}

	private boolean blocked(ServerLevel level, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
	}

	/**
	 * Makes this trooper perform {@code action} now with its full tell (the debug command):
	 * {@code grenade} (at {@code at}, default 8 blocks ahead), {@code flare}, {@code rally},
	 * {@code vent}, {@code heal} or {@code salute}.
	 *
	 * @return false when this trooper cannot do it (wrong role or army, or not standing)
	 */
	public boolean perform(String action, @Nullable Vec3 at) {
		if (!canPerform(action)) {
			return false;
		}

		switch (action) {
			case "vent" -> startVent();
			case "salute" -> salute(level().getNearestPlayer(this, 16));
			case "grenade" -> specialGoal.request(TrooperSpecialGoal.Kind.GRENADE, at != null ? at : position().add(getLookAngle().multiply(1, 0, 1).normalize().scale(8)));
			case "flare" -> specialGoal.request(TrooperSpecialGoal.Kind.FLARE, null);
			case "rally" -> specialGoal.request(TrooperSpecialGoal.Kind.RALLY, null);
			case "heal" -> specialGoal.request(TrooperSpecialGoal.Kind.HEAL, null);
			default -> {
				return false;
			}
		}

		return true;
	}

	/** Whether {@link #perform} would accept {@code action}. */
	public boolean canPerform(String action) {
		if (!canFight()) {
			return action.equals("salute") && !isDowned() && !isYielded() && isAlive();
		}

		return switch (action) {
			case "vent" -> weapon() == Weapon.REPUBLIC_RIFLE;
			case "salute" -> true;
			case "grenade" -> role() == TrooperRole.GRENADIER;
			case "flare" -> role() == TrooperRole.SERGEANT && !isRepublic();
			case "rally" -> role() == TrooperRole.SERGEANT && isRepublic();
			case "heal" -> role() == TrooperRole.MEDIC;
			default -> false;
		};
	}

	// ---------------------------------------------------------------- fighting state

	/** Standing and able to fight: not downed, yielded, bound, an escort, walking off or an officer. */
	public boolean canFight() {
		return isAlive() && !isDowned() && !isYielded() && !yieldResolved && !bound && !escort && !leaving && !isOfficer();
	}

	/** Whether {@code e} is something this trooper fights (spec 5.2, 7.5, 7.8). Server. */
	public boolean wants(LivingEntity e) {
		if (e == this || !e.isAlive() || e.isRemoved() || e instanceof FrontierSettler || !e.canBeSeenAsEnemy()
				|| Allegiances.friendly(this, e)) {
			return false;
		}

		long now = level().getGameTime();

		if (e instanceof Player player) {
			// Creative and spectator players already failed canBeSeenAsEnemy() above.
			if (TrooperAttitudeRule.peaceful(this)) {
				return false;
			}

			UUID id = player.getUUID();
			return isEngagedWith(id, now) || isProvokedBy(id, now) || incidentIncludes(id, now) || isDefending(e, now)
					|| Attitudes.toward(this, player) == Attitude.HOSTILE;
		}

		if (isDefending(e, now) || e == getLastHurtByMob() && tickCount - getLastHurtByMobTimestamp() < HURT_BY_MEMORY) {
			return true;
		}

		if (e instanceof TrooperEntity other) {
			return ColdPeace.shouldEngage(incidentMode(), other.incidentMode(), TrooperBalance.get().coldPeace);
		}

		Allegiance side = Allegiances.of(e);
		return side == Allegiance.CREW || (side == Allegiance.ANCIENT || side == Allegiance.ARCHIVE) && Troopers.fightsOnSight(e);
	}

	/**
	 * Whether {@code e} must be seen within {@code hostileRange} before this trooper picks it (on-sight
	 * targets), rather than being fought wherever it is (provoked, engaged, defended against, hurt by).
	 */
	public boolean onSightOnly(LivingEntity e) {
		long now = level().getGameTime();

		if (isDefending(e, now) || e == getLastHurtByMob() && tickCount - getLastHurtByMobTimestamp() < HURT_BY_MEMORY) {
			return false;
		}

		if (e instanceof Player player) {
			UUID id = player.getUUID();
			return !isEngagedWith(id, now) && !isProvokedBy(id, now) && !incidentIncludes(id, now);
		}

		return true;
	}

	/** Frontier settlers are never targets. */
	@Override
	public boolean canAttack(LivingEntity target) {
		return !(target instanceof FrontierSettler) && !Allegiances.friendly(this, target) && super.canAttack(target);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target instanceof FrontierSettler || target != null && !canFight() ? null : target);
	}

	/** Fights {@code attacker} because it hurt a player this trooper is Allied with (spec 5.2). */
	public void defend(LivingEntity attacker) {
		defending = attacker.getUUID();
		defendingUntil = level().getGameTime() + TrooperBalance.get().engagedTicks;

		if (canFight()) {
			setTarget(attacker);
		}
	}

	private boolean isDefending(Entity e, long now) {
		return defending != null && now < defendingUntil && defending.equals(e.getUUID());
	}

	public boolean isProvokedBy(UUID player, long now) {
		return provoked.active(player, now);
	}

	public boolean isEngagedWith(UUID player, long now) {
		return engaged.active(player, now);
	}

	/** {@code player} struck first: this trooper fights them for {@code provokedTicks} (not hostile). */
	public void provoke(UUID player, long now) {
		provoked.prune(now);
		provoked.put(player, TrooperRules.expiry(now, TrooperBalance.get().provokedTicks));
	}

	/** This trooper fired first at {@code player}: hostile for {@code engagedTicks}. */
	public void engage(UUID player, long now) {
		engaged.prune(now);
		engaged.put(player, TrooperRules.expiry(now, TrooperBalance.get().engagedTicks));
	}

	/** The incident mode in force now. */
	public IncidentMode incidentMode() {
		return TrooperRules.incidentAt(incident, incidentUntil, level().getGameTime());
	}

	/** The saved incident expiry (game time). */
	public long incidentUntil() {
		return incidentUntil;
	}

	/** Whether the incident makes this trooper engaged with {@code player} at {@code now}. */
	public boolean incidentIncludes(UUID player, long now) {
		return TrooperRules.incidentIncludes(incident, incidentUntil, now, owner, player);
	}

	/** Replaces the incident (spawners: {@code until} is {@link Long#MAX_VALUE} for one that never ends). */
	public void setIncident(IncidentMode mode, long until) {
		this.incident = mode;
		this.incidentUntil = mode == IncidentMode.NONE ? 0 : until;
	}

	/** Adds an incident to the current one: the stronger mode and the later expiry win. */
	public void addIncident(IncidentMode mode, long until) {
		long[] merged = TrooperRules.mergeIncident(incident, incidentUntil, mode, until, level().getGameTime());
		setIncident(IncidentMode.values()[(int) merged[0]], merged[1]);
	}

	/** The mission or contract owner, if any. */
	public @Nullable UUID owner() {
		return owner;
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
	}

	void setSquad(@Nullable UUID squad, int size) {
		this.squad = squad;
		this.squadSize = Math.max(1, size);
	}

	public int squadSize() {
		return squadSize;
	}

	public boolean isFlanker() {
		return flanker;
	}

	void setFlanker(boolean flanker) {
		this.flanker = flanker;
	}

	public boolean isRallied() {
		return rallyUntil != 0 && level().getGameTime() < rallyUntil;
	}

	/** Rally: a shorter gap between bursts until {@code until}. Resistance is applied by the sergeant. */
	public void setRallied(long until) {
		rallyUntil = Math.max(rallyUntil, until);
	}

	// ---------------------------------------------------------------- deflection answer

	/**
	 * One of this trooper's bolts was deflected (called by {@link BlasterBolt#redirect}). A rifleman with
	 * {@code deflectCount} deflected within {@code deflectWindowTicks} holds fire for
	 * {@code holdFireTicks} and repositions 4 to 8 blocks sideways, barking {@code flank}.
	 */
	public void onBoltDeflected() {
		if (role() != TrooperRole.RIFLEMAN || !canFight()) {
			return;
		}

		TrooperBalance b = TrooperBalance.get();
		long now = level().getGameTime();
		deflectedAt.removeIf(at -> now - at >= b.deflectWindowTicks);
		deflectedAt.add(now);

		if (!TrooperRules.answersDeflection(deflectedAt, now, b)) {
			return;
		}

		deflectedAt.clear();
		holdFireUntil = now + b.holdFireTicks;
		repositionTo = sidestep(b);
		dropTell();
		Barks.say(this, BarkCategory.FLANK);
	}

	private @Nullable Vec3 sidestep(TrooperBalance b) {
		LivingEntity target = getTarget();
		Vec3 facing = target != null ? target.position().subtract(position()) : getLookAngle();
		Vec3 flat = new Vec3(facing.x, 0, facing.z);

		if (flat.lengthSqr() < 1.0e-4) {
			flat = new Vec3(1, 0, 0);
		}

		Vec3 side = new Vec3(-flat.z, 0, flat.x).normalize().scale(getRandom().nextBoolean() ? 1 : -1);
		int min = b.repositionRange[0];
		int max = b.repositionRange[b.repositionRange.length - 1];
		double distance = min + getRandom().nextDouble() * Math.max(0, max - min);
		return position().add(side.scale(distance));
	}

	/** Holding fire after an answered deflection. */
	public boolean isHoldingFire() {
		return level().getGameTime() < holdFireUntil;
	}

	/** Where to sidestep after an answered deflection, or null. */
	public @Nullable Vec3 repositionTarget() {
		return repositionTo;
	}

	public void clearReposition() {
		repositionTo = null;
	}

	// ---------------------------------------------------------------- morale

	private void checkMorale(ServerLevel level, long now) {
		if (moraleUsed || squad == null || !canFight() || getTarget() == null) {
			return;
		}

		if (!TrooperRules.moraleBreaks(Squads.sergeantStanding(this), Squads.fighters(this).size(), squadSize)) {
			return;
		}

		TrooperBalance b = TrooperBalance.get();
		moraleUsed = true;
		retreatUntil = now + b.moraleRetreatTicks;
		Vec3 away = position().subtract(getTarget().position()).multiply(1, 0, 1);
		away = away.lengthSqr() < 1.0e-4 ? getLookAngle().reverse().multiply(1, 0, 1) : away;
		retreatTo = position().add(away.normalize().scale(b.moraleRetreatDistance));
		dropTell();
		Barks.say(this, BarkCategory.RETREAT);
	}

	/** Falling back after the squad broke (spec 7.5). */
	public boolean isRetreating() {
		return retreatTo != null && level().getGameTime() < retreatUntil;
	}

	public @Nullable Vec3 retreatTarget() {
		return retreatTo;
	}

	// ---------------------------------------------------------------- damage, death, drops

	/**
	 * Officers and downed troopers take only damage that bypasses invulnerability ({@code /kill}, the
	 * void). A hit of {@code interruptDamage} or more breaks the medic's channel.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if ((isOfficer() || isDowned()) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}

		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && TrooperRules.hitInterrupts(action(), amount, TrooperBalance.get())) {
			pendingInterrupt = true;
		}

		return hurt;
	}

	/** Downed troopers and officers are never targets (mobs, area powers, aim assist). */
	@Override
	public boolean canBeSeenAsEnemy() {
		return !isDowned() && !isOfficer() && super.canBeSeenAsEnemy();
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.HOSTILE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.TROOPER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.TROOPER_DEATH;
	}

	/**
	 * Reports the kill to the quest feature with {@code wasHostile} from the hostility snapshot (spec
	 * 5.2), marks the squad as having lost a member, and makes the squad fight a player who killed a
	 * member without being hostile (a kill is a hit).
	 */
	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (deathHandled || !(level() instanceof ServerLevel level)) {
			return;
		}

		deathHandled = true;
		setAction(TrooperAction.IDLE);

		// Only a fall in a fight readies the Imperial flare (spec 7.3): a living killer, or a squad
		// that is fighting. A trooper lost to a fall or a fire outside any fight does not count.
		if (source.getEntity() instanceof LivingEntity || getTarget() != null || Squads.fighting(this)) {
			Squads.memberFell(this);
		}

		ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player
				: getKillCredit() instanceof ServerPlayer credited ? credited : null;

		if (killer == null) {
			return;
		}

		boolean wasHostile = Attitudes.hostileSnapshot(this, killer);
		QuestHooks.onTrooperKilled(killer, this, army(), role(), wasHostile);

		if (!wasHostile && !TrooperAttitudeRule.peaceful(this)) {
			TrooperEvents.provokeSquad(this, killer, level.getGameTime());
		}
	}

	/** Only a player's kill drops anything: one durasteel plate at {@code durasteelChance} (spec 7.1). */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean causedByPlayer) {
		super.dropCustomDeathLoot(level, source, causedByPlayer);

		for (ItemStack stack : TrooperRules.deathDrops(getRandom(), causedByPlayer, TrooperBalance.get())) {
			spawnAtLocation(level, stack);
		}
	}

	/** Patrols despawn as whole squads (spec 7.6) and garrisons stay, so vanilla never removes one. */
	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	/** Patrol members, flare arrivals, escorts and troopers walking off are never saved. */
	@Override
	public boolean shouldBeSaved() {
		return !patrol && !reinforcement && !escort && !leaving && super.shouldBeSaved();
	}

	// ---------------------------------------------------------------- use: officers and downed

	/** Officers open their dialogue; sneaking at a downed trooper starts tending it (kneel). */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (isOfficer()) {
			if (player instanceof ServerPlayer serverPlayer) {
				Dialogues.open(serverPlayer, OldRepublicMod.id("officer_" + armyKey()), this);
			}

			return InteractionResult.SUCCESS;
		}

		if (isDowned() && player.isShiftKeyDown()) {
			if (player instanceof ServerPlayer serverPlayer) {
				kneeler = serverPlayer.getUUID();
				tend(serverPlayer, false);
			}

			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	// ---------------------------------------------------------------- downed

	/** Makes this a downed trooper (no AI, never targeted) owned by {@code owner}. */
	void setDowned(@Nullable UUID owner) {
		this.entityData.set(DATA_DOWNED, true);
		this.owner = owner;
		setNoAi(true);
		setTarget(null);
		setAction(TrooperAction.IDLE);
	}

	/** Game time at which this downed trooper is discarded; negative when it never bleeds out. */
	public long bleedAt() {
		return bleedAt;
	}

	void setBleedAt(long gameTime) {
		this.bleedAt = gameTime;
	}

	/** The W4 kneel driver: while the player who sneak-used it stays crouched beside it, it is tended each tick. */
	private void tickKneel(ServerLevel level, long now) {
		if (kneeler == null) {
			return;
		}

		if (level.getEntity(kneeler) instanceof ServerPlayer player && player.isAlive() && player.isShiftKeyDown()
				&& player.distanceTo(this) <= KNEEL_REACH) {
			tend(player, false);
		} else {
			kneeler = null;
		}
	}

	/**
	 * One kneel tick of tending, or an instant Force Heal revive. Kneel ticks count once per game tick
	 * however many callers drive them, and a gap of more than {@value #TEND_GAP_TICKS} ticks starts the
	 * count again. Sends {@code trooper_tend} every 20 kneel ticks and when it stands.
	 *
	 * @return true when the trooper stood up
	 */
	public boolean tend(ServerPlayer player, boolean instant) {
		if (!isDowned() || !isAlive() || !(level() instanceof ServerLevel level)) {
			return false;
		}

		if (!instant) {
			long now = level.getGameTime();

			if (now == lastTendTick) {
				return false;
			}

			boolean fresh = lastTendTick == TrooperRules.NEVER || now - lastTendTick > TEND_GAP_TICKS;
			tendProgress = fresh ? 1 : tendProgress + 1;
			lastTendTick = now;

			if (tendProgress % 20 == 1) {
				EffectEvents.toTracking(this, TrooperEffects.TROOPER_TEND, position(), null, getId(), 1f, TrooperEffects.TEND_TENDING);
			}

			if (tendProgress < TrooperBalance.get().tendTicks) {
				return false;
			}
		}

		standUp(player);
		return true;
	}

	/** Kneel ticks counted toward standing up. */
	public int tendProgress() {
		return tendProgress;
	}

	private void standUp(ServerPlayer tender) {
		this.entityData.set(DATA_DOWNED, false);
		setNoAi(false);
		bleedAt = NO_BLEED;
		kneeler = null;
		tendProgress = 0;
		setHealth(Math.max(getHealth(), getMaxHealth() * 0.5f));
		EffectEvents.toTracking(this, TrooperEffects.TROOPER_TEND, position(), null, getId(), 1f, TrooperEffects.TEND_STANDS);
		QuestHooks.onTrooperTended(tender, this, army());
		BlockPos home = Outposts.nearestTerminal(tender, army(), TrooperBalance.get().homeRange);
		leave(home != null ? Vec3.atBottomCenterOf(home) : null, tender.position());
	}

	// ---------------------------------------------------------------- leaving, escorts, patrols

	/**
	 * Walks off and is removed once out of sight: to {@code destination}, or {@value #LEAVE_DISTANCE}
	 * blocks away from {@code awayFrom}. Never saved from now on.
	 */
	public void leave(@Nullable Vec3 destination, Vec3 awayFrom) {
		leaving = true;
		setTarget(null);
		clearHome();
		setAction(TrooperAction.IDLE);
		leaveSince = level().getGameTime();

		if (destination != null) {
			leaveTo = destination;
		} else {
			Vec3 away = position().subtract(awayFrom).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0e-4 ? getLookAngle().multiply(1, 0, 1) : away;
			leaveTo = position().add(away.normalize().scale(LEAVE_DISTANCE));
		}
	}

	public boolean isLeaving() {
		return leaving;
	}

	public @Nullable Vec3 leaveTarget() {
		return leaveTo;
	}

	public long leaveSince() {
		return leaveSince;
	}

	/** Makes this an escort walking {@code prisoner} to {@code exit} on side {@code side} (+1 or -1). */
	void setEscort(LivingEntity prisoner, BlockPos exit, int side) {
		escort = true;
		escortPrisoner = prisoner;
		escortExit = exit;
		escortSide = side;
		escortSince = level().getGameTime();
		setTarget(null);
		clearHome();
	}

	public boolean isEscort() {
		return escort;
	}

	public @Nullable LivingEntity escortPrisonerOf() {
		return escortPrisoner;
	}

	public @Nullable BlockPos escortExit() {
		return escortExit;
	}

	public int escortSide() {
		return escortSide;
	}

	public long escortSince() {
		return escortSince;
	}

	/** A prisoner: hands behind the back, no fighting. */
	public void setBound(boolean bound) {
		this.bound = bound;

		if (bound) {
			boundSince = level().getGameTime();
			setTarget(null);
			setAction(TrooperAction.BOUND);
		} else if (action() == TrooperAction.BOUND) {
			setAction(TrooperAction.IDLE);
		}
	}

	public boolean isBound() {
		return bound;
	}

	/** An escort is walking this prisoner now (called by the escort every tick it leads the prisoner). */
	public void markEscorted(long now) {
		escortedAt = now;
	}

	/** Whether an escort walked this prisoner within the last few ticks. */
	public boolean isEscorted(long now) {
		return escortedAt != TrooperRules.NEVER && now - escortedAt <= ESCORT_GRACE_TICKS;
	}

	void setPatrol(boolean patrol) {
		this.patrol = patrol;
	}

	public boolean isPatrol() {
		return patrol;
	}

	void setReinforcement(boolean reinforcement) {
		this.reinforcement = reinforcement;
	}

	/** A flare arrival that is not on patrol (spec 7.3): it walks off once the squad's encounter ends. */
	public boolean isReinforcement() {
		return reinforcement;
	}

	void setRoute(@Nullable List<BlockPos> route) {
		this.route = route == null ? null : List.copyOf(route);
		this.routeIndex = 0;
	}

	/** The next route point of a patrol leader, or null when it has none or has finished. */
	public @Nullable BlockPos routePoint() {
		return route != null && routeIndex < route.size() ? route.get(routeIndex) : null;
	}

	public void advanceRoute() {
		routeIndex++;
	}

	// ---------------------------------------------------------------- terminal, officers, yield setup

	void bindTerminal(BlockPos pos, int slot, int leash) {
		terminal = pos.immutable();
		terminalSlot = slot;
		setHomeTo(terminal, leash);
		setPersistenceRequired();
	}

	public @Nullable BlockPos terminal() {
		return terminal;
	}

	public int terminalSlot() {
		return terminal == null ? -1 : terminalSlot;
	}

	/** Sets an officer's name seed and its custom name {@code npc.oldrepublic.officer.<army>.<seed mod 6>}. */
	void setNameSeed(int nameSeed) {
		this.nameSeed = nameSeed;

		if (isOfficer()) {
			nameOfficer();
		}
	}

	private void nameOfficer() {
		setCustomName(Component.translatable("npc.oldrepublic.officer." + armyKey() + "." + Math.floorMod(nameSeed, 6)));
	}

	public int nameSeed() {
		return nameSeed;
	}

	void setYield(float fraction, Identifier dialogue) {
		this.yieldFraction = fraction;
		this.yieldDialogueId = dialogue;
	}

	long lastBark() {
		return lastBark;
	}

	void setLastBark(long gameTime) {
		this.lastBark = gameTime;
	}

	// ---------------------------------------------------------------- Yieldable

	/**
	 * Only incident-tagged sergeants yield (spec 7.1): one W2 gave a threshold through
	 * {@link Troopers#setYield}, or the sergeant of an {@code ARMY_AND_OWNER} squad by default.
	 */
	@Override
	public boolean canYield() {
		return role() == TrooperRole.SERGEANT && !yieldResolved && !isDowned()
				&& (yieldFraction >= 0 || incidentMode() == IncidentMode.ARMY_AND_OWNER);
	}

	@Override
	public float yieldHealthFraction() {
		if (yieldFraction >= 0) {
			return yieldFraction;
		}

		TrooperBalance b = TrooperBalance.get();
		return entityTags().contains(TAG_BORDER_INCIDENT) ? b.borderYield : b.interceptYield;
	}

	/** The mission or contract owner decides; anyone may when there is none. */
	@Override
	public @Nullable UUID yieldDecider() {
		return owner;
	}

	@Override
	public void setYielded(boolean yielded) {
		this.entityData.set(DATA_YIELDED, yielded);

		if (yielded) {
			setTarget(null);
			getNavigation().stop();
			setAction(TrooperAction.IDLE);
			Barks.say(this, BarkCategory.YIELD);
		}
	}

	/**
	 * Spared, interrogated or fled: walks off. Handed over: bound, waiting for the escort W2 spawns
	 * ({@link Troopers#spawnEscort}). Executed: the identity feature deals the blow.
	 */
	@Override
	public void onYieldResolved(YieldOutcome outcome) {
		yieldResolved = true;
		this.entityData.set(DATA_YIELDED, false);

		switch (outcome) {
			case HANDED_OVER -> setBound(true);
			case SPARED, INTERROGATED, FLED -> {
				Player nearest = level().getNearestPlayer(this, 32);
				leave(null, nearest != null ? nearest.position() : position().add(getLookAngle()));
			}
			case EXECUTED -> {
			}
		}
	}

	/** {@code yield_border} for the I2 sergeant, {@code yield_intercept} otherwise, unless W2 chose one. */
	@Override
	public Identifier yieldDialogue() {
		if (yieldDialogueId != null) {
			return yieldDialogueId;
		}

		return entityTags().contains(TAG_BORDER_INCIDENT) ? YIELD_BORDER : YIELD_INTERCEPT;
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Role", role().name());
		output.storeNullable("Squad", UUIDUtil.CODEC, squad);
		output.storeNullable("Owner", UUIDUtil.CODEC, owner);
		output.putInt("SquadSize", squadSize);
		output.putBoolean("Flanker", flanker);

		if (incident != IncidentMode.NONE) {
			ValueOutput tag = output.child("Incident");
			tag.putInt("Mode", incident.ordinal());
			tag.putLong("Until", incidentUntil);
		}

		if (!provoked.isEmpty()) {
			output.store("Provoked", PlayerExpiries.CODEC, provoked);
		}

		if (!engaged.isEmpty()) {
			output.store("Engaged", PlayerExpiries.CODEC, engaged);
		}

		if (bleedAt != NO_BLEED) {
			output.putLong("BleedAt", bleedAt);
		}

		if (terminal != null) {
			ValueOutput tag = output.child("Terminal");
			tag.store("Pos", BlockPos.CODEC, terminal);
			tag.putInt("Slot", terminalSlot);
		}

		if (yieldFraction >= 0 && yieldDialogueId != null) {
			ValueOutput tag = output.child("Yield");
			tag.putFloat("Fraction", yieldFraction);
			tag.putString("Dialogue", yieldDialogueId.toString());
		}

		output.putBoolean("Downed", isDowned());
		// The yield survives a relog or restart (spec 12.2): a yielded sergeant still waits for its
		// decider, and a handed-over prisoner stays bound for its escort.
		output.putBoolean("Yielded", isYielded());
		output.putBoolean("YieldResolved", yieldResolved);
		output.putBoolean("Bound", bound);

		if (isOfficer()) {
			output.putInt("NameSeed", nameSeed);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.getString("Role").ifPresent(name -> {
			for (TrooperRole role : TrooperRole.values()) {
				if (role.name().equals(name)) {
					setRole(role);
				}
			}
		});

		squad = input.read("Squad", UUIDUtil.CODEC).orElse(null);
		owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		squadSize = Math.max(1, input.getIntOr("SquadSize", 1));
		flanker = input.getBooleanOr("Flanker", false);

		incident = IncidentMode.NONE;
		incidentUntil = 0;
		input.child("Incident").ifPresent(tag -> {
			int mode = tag.getIntOr("Mode", 0);
			IncidentMode[] modes = IncidentMode.values();
			setIncident(mode >= 0 && mode < modes.length ? modes[mode] : IncidentMode.NONE, tag.getLongOr("Until", 0));
		});

		provoked.clear();
		engaged.clear();
		input.read("Provoked", PlayerExpiries.CODEC).ifPresent(read -> read.entries().forEach(e -> provoked.put(e.player(), e.until())));
		input.read("Engaged", PlayerExpiries.CODEC).ifPresent(read -> read.entries().forEach(e -> engaged.put(e.player(), e.until())));
		bleedAt = input.getLongOr("BleedAt", NO_BLEED);

		terminal = null;
		terminalSlot = -1;
		input.child("Terminal").ifPresent(tag -> tag.read("Pos", BlockPos.CODEC).ifPresent(pos -> {
			terminal = pos;
			terminalSlot = tag.getIntOr("Slot", 0);
		}));

		yieldFraction = -1f;
		yieldDialogueId = null;
		input.child("Yield").ifPresent(tag -> {
			Identifier dialogue = Identifier.tryParse(tag.getStringOr("Dialogue", ""));

			if (dialogue != null) {
				yieldFraction = tag.getFloatOr("Fraction", -1f);
				yieldDialogueId = dialogue;
			}
		});

		boolean downed = input.getBooleanOr("Downed", false);
		this.entityData.set(DATA_DOWNED, downed);

		if (downed) {
			setNoAi(true);
		}

		// Yielding.onEntityLoad tracks a yielded sergeant again; a bound prisoner restarts its escort wait.
		this.entityData.set(DATA_YIELDED, input.getBooleanOr("Yielded", false));
		yieldResolved = input.getBooleanOr("YieldResolved", false);

		if (input.getBooleanOr("Bound", false)) {
			setBound(true);
		}

		nameSeed = input.getIntOr("NameSeed", 0);
	}
}
