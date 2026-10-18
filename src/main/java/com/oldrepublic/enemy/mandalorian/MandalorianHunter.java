package com.oldrepublic.enemy.mandalorian;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEffects;
import com.oldrepublic.enemy.FoesFeature;
import com.oldrepublic.enemy.PoacherCoverGoal;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.speed.ForceSpeed;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.ChapterFour;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Mandalorian hunter (spec MILESTONE_4.md 10.1): a bounty hunter in worn plate with a jet pack,
 * a gold-bolt rifle and wrist gadgets. Each gadget has its own tell before it acts: the jet hop (a
 * rising whine and flame flicker at the pack), the wrist flamer (wrist raised, a pilot flame and a
 * hiss) and the wrist rocket (a targeting beep and a small red dot on the target). The fight itself
 * is {@link HunterCombatGoal}; this class holds the synced state the client draws, the gadgets'
 * mechanics (also used by Torvin Kaast, who extends it), saving, the yield and the death hooks.
 *
 * <p>Story hunters belong to a player ({@code StoryTags} owner) and a group (one ambush): the last
 * hunter of a story ambush yields at {@code yieldAt} (the M2 {@link Yieldable} flow, dialogue
 * {@code c4_hunter_yield}); the first ambush's yield is clue 2 (8.5). Owned hunters whose owner is
 * gone for {@code ownerAbsentTicks} jet away.
 */
public class MandalorianHunter extends Monster implements Yieldable, Aligned {
	/** The dialogue a story ambush's last hunter opens when it yields (W2's). */
	public static final Identifier YIELD_DIALOGUE = OldRepublicMod.id("c4_hunter_yield");
	/** Painted plate variants (the client's textures {@code mandalorian_hunter_<n>}). */
	public static final int PAINTS = 4;

	/** Which gadget's tell is showing (the client's pose): none, jet, flamer, rocket, net. */
	public static final int GADGET_NONE = 0;
	public static final int GADGET_JET = 1;
	public static final int GADGET_FLAMER = 2;
	public static final int GADGET_ROCKET = 3;
	public static final int GADGET_NET = 4;

	/** No ambush: a bounty hunter, a hunter called into Kaast's fight, or a spawn egg. */
	public static final int AMBUSH_NONE = 0;
	public static final int AMBUSH_ONE = 1;
	public static final int AMBUSH_TWO = 2;

	private static final EntityDataAccessor<Integer> DATA_PAINT = SynchedEntityData.defineId(MandalorianHunter.class, EntityDataSerializers.INT);
	/** The rifle's aim tell before a burst. */
	private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(MandalorianHunter.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_GADGET = SynchedEntityData.defineId(MandalorianHunter.class, EntityDataSerializers.INT);
	/** The wrist flamer's cone is burning (the client's cone particles and the flamer loop follow it). */
	private static final EntityDataAccessor<Boolean> DATA_FLAMING = SynchedEntityData.defineId(MandalorianHunter.class, EntityDataSerializers.BOOLEAN);
	/** In a jet hop or a jet descent (the exhaust). */
	private static final EntityDataAccessor<Boolean> DATA_JETTING = SynchedEntityData.defineId(MandalorianHunter.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_YIELDED = SynchedEntityData.defineId(MandalorianHunter.class, EntityDataSerializers.BOOLEAN);

	/** How far group members are looked for when deciding who is the last one standing. */
	private static final double GROUP_RANGE = 96;
	/** A spared hunter waits this long, then jets away. */
	private static final int DEPART_TICKS = 30;
	/** Speed of a jet descent (an ambush arrival). */
	private static final double DESCENT_SPEED = 0.42;

	private @Nullable UUID group;
	private int ambush = AMBUSH_NONE;
	/** Called into Kaast's camp fight: never drops anything and never yields. */
	private boolean fightSpawn;
	private boolean setUp;
	private boolean deathHandled;
	private boolean interruptRequested;
	private int absentTicks;
	private int departTicks = -1;

	// Jet hop state (server).
	private @Nullable Vec3 hopFrom;
	private @Nullable Vec3 hopTo;
	private int hopTick;
	private int hopTicks;
	private double hopArc;
	private boolean descending;
	private boolean staggerOnLanding;

	// Flamer state (server).
	private int flameTicks;
	private int flameAge;
	private float flameDamage;
	private int flameInterval;
	private double flameLength;
	private float flameDegrees;
	private float flameFireSeconds;

	/** Client only: ticks the current rifle tell or gadget tell has shown. */
	private int clientTellTicks;

	public MandalorianHunter(EntityType<? extends MandalorianHunter> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	/** Default attributes; the balance replaces them on the hunter's first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		HunterBalance b = new HunterBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, b.maxHealth)
				.add(Attributes.ARMOR, b.armor)
				.add(Attributes.MOVEMENT_SPEED, b.movementSpeed)
				.add(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance)
				.add(Attributes.FOLLOW_RANGE, b.followRange)
				.add(Attributes.ATTACK_DAMAGE, 4.0);
	}

	@Override
	protected void registerGoals() {
		HunterBalance defaults = HunterBalance.get();
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PoacherCoverGoal(this, MandalorianHunter::coverSettings, this::mayTakeCover, () -> setCharging(false)));
		this.goalSelector.addGoal(2, new HunterCombatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, (float) defaults.maxRange));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return isFighting() && super.canUse();
			}
		}.setAlertOthers(MandalorianHunter.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
			@Override
			public boolean canUse() {
				return isFighting() && super.canUse();
			}
		});
	}

	private static PoacherCoverGoal.Settings coverSettings() {
		HunterBalance b = HunterBalance.get();
		return new PoacherCoverGoal.Settings(b.coverHealthFraction, b.coverSpeed, b.coverHoldMinTicks, b.coverHoldMaxTicks, b.coverCooldownTicks);
	}

	/** Cover is the hunter's own retreat; never in the middle of a gadget, a hop or a yield. */
	protected boolean mayTakeCover() {
		return isFighting() && gadget() == GADGET_NONE && !isFlaming() && !isJetting();
	}

	/** Whether the hunter fights at all now (not yielded, not leaving). */
	public boolean isFighting() {
		return !isYielded() && departTicks < 0;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PAINT, 0);
		builder.define(DATA_CHARGING, false);
		builder.define(DATA_GADGET, GADGET_NONE);
		builder.define(DATA_FLAMING, false);
		builder.define(DATA_JETTING, false);
		builder.define(DATA_YIELDED, false);
	}

	// ---------------------------------------------------------------- synced state

	/** The painted plate variant, 0 to {@link #PAINTS} - 1 (seeded at spawn). */
	public int paint() {
		return Mth.clamp(this.entityData.get(DATA_PAINT), 0, PAINTS - 1);
	}

	public boolean isCharging() {
		return this.entityData.get(DATA_CHARGING);
	}

	protected void setCharging(boolean charging) {
		this.entityData.set(DATA_CHARGING, charging);
	}

	/** The gadget whose tell is showing ({@code GADGET_*}). */
	public int gadget() {
		return this.entityData.get(DATA_GADGET);
	}

	protected void setGadget(int gadget) {
		this.entityData.set(DATA_GADGET, gadget);
	}

	public boolean isFlaming() {
		return this.entityData.get(DATA_FLAMING);
	}

	public boolean isJetting() {
		return this.entityData.get(DATA_JETTING);
	}

	/** Client only: ticks the current tell has been showing. */
	public int clientTellTicks() {
		return clientTellTicks;
	}

	// ---------------------------------------------------------------- story state

	/**
	 * Makes this hunter part of a story ambush (8.5): {@code group} ties it to its fellow hunters,
	 * {@code ambush} is 1 or 2 (0 for a bounty hunter).
	 */
	public void joinAmbush(@Nullable UUID group, int ambush) {
		this.group = group;
		this.ambush = ambush;
	}

	/** Marks a hunter Kaast called into his fight: no drops, no yield. */
	public void markFightSpawn() {
		this.fightSpawn = true;
	}

	public @Nullable UUID group() {
		return group;
	}

	public int ambush() {
		return ambush;
	}

	public boolean isFightSpawn() {
		return fightSpawn;
	}

	/** The player this hunter is hunting (its {@code StoryTags} owner), or null. */
	public @Nullable UUID owner() {
		return StoryTags.owner(this);
	}

	@Override
	public Allegiance allegiance() {
		// Hunters, their escorts and Kaast never shoot each other.
		return Allegiance.HUNTERS;
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			clientTellTicks = isCharging() || gadget() != GADGET_NONE ? clientTellTicks + 1 : 0;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance();
		}

		tickHop(level);
		tickFlame(level);

		if (isYielded()) {
			getNavigation().stop();
			setTarget(null);
		}

		if (departTicks >= 0 && --departTicks <= 0) {
			leave(level);
			return;
		}

		tickOwner(level);
	}

	/** Applies the balance attributes once per loaded hunter (attribute defaults are fixed at startup). */
	protected void applyBalance() {
		HunterBalance b = HunterBalance.get();
		applyAttributes(b.maxHealth, b.armor, b.movementSpeed, b.knockbackResistance, b.followRange);
	}

	/** Sets the base attributes, keeping a fresh hunter at full health. */
	protected final void applyAttributes(double maxHealth, double armor, double speed, double knockbackResistance, double followRange) {
		setBase(Attributes.ARMOR, armor);
		setBase(Attributes.MOVEMENT_SPEED, speed);
		setBase(Attributes.KNOCKBACK_RESISTANCE, knockbackResistance);
		setBase(Attributes.FOLLOW_RANGE, followRange);
		boolean full = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, maxHealth);

		if (full || getHealth() > getMaxHealth()) {
			setHealth(getMaxHealth());
		}
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);

		if (instance != null) {
			// API-CHECK: AttributeInstance#setBaseValue (as CrystalPoacher).
			instance.setBaseValue(value);
		}
	}

	/** An owned hunter whose owner is gone (offline, another level, too far) for {@code ownerAbsentTicks} jets away. */
	private void tickOwner(ServerLevel level) {
		UUID owner = owner();

		if (owner == null || fightSpawn) {
			return;
		}

		HunterBalance b = HunterBalance.get();
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		boolean present = player != null && player.level() == level && player.isAlive() && player.distanceToSqr(this) <= b.ownerLeash * b.ownerLeash;
		absentTicks = present ? 0 : absentTicks + 1;

		if (absentTicks >= b.ownerAbsentTicks) {
			leave(level);
		}
	}

	/** Jets away for good: a hop of flame straight up, then gone (no drops, no death). */
	public void leave(ServerLevel level) {
		stopFlame();
		EffectEvents.around(level, HunterEffects.ATTACK, position(), new Vec3(0, 1, 0), getId(), 10f, HunterEffects.ATTACK_JET);
		discard();
	}

	// ---------------------------------------------------------------- interrupts

	/** A stagger, Push, shock or other interrupt: the current tell and the flame stop. Kaast narrows this. */
	public void interrupt(String cause) {
		interruptRequested = true;
		stopFlame();
	}

	/** True once after {@link #interrupt}. */
	boolean consumeInterrupt() {
		boolean value = interruptRequested;
		interruptRequested = false;
		return value;
	}

	/** Pushed while in a hop: the hop ends, it falls and is staggered on landing (10.1). */
	public void onPushedInAir() {
		if (isJetting() && !descending) {
			endHop();
			staggerOnLanding = true;
		}
	}

	// ---------------------------------------------------------------- jet

	/** A descent from above on the jet pack (an ambush arrival). */
	public void startDescent() {
		descending = true;
		setNoGravity(true);
		this.entityData.set(DATA_JETTING, true);
	}

	/**
	 * Starts a hop to {@code to} over {@code ticks}, arcing {@code arc} blocks above the straight line
	 * (more when climbing). Sends the jet event.
	 */
	public void startHop(ServerLevel level, Vec3 to, int ticks, double arc) {
		hopFrom = position();
		hopTo = to;
		hopTick = 0;
		hopTicks = Math.max(4, ticks);
		hopArc = arc + Math.max(0.0, to.y - hopFrom.y);
		descending = false;
		setNoGravity(true);
		getNavigation().stop();
		this.entityData.set(DATA_JETTING, true);
		Vec3 dir = to.subtract(hopFrom);
		EffectEvents.around(level, HunterEffects.ATTACK, position(), dir.lengthSqr() < 1.0e-4 ? null : dir.normalize(), getId(), hopTicks, HunterEffects.ATTACK_JET);
	}

	private void tickHop(ServerLevel level) {
		if (descending) {
			resetFallDistance();
			setDeltaMovement(getDeltaMovement().multiply(0.5, 0, 0.5).add(0, -DESCENT_SPEED, 0));

			if (onGround()) {
				descending = false;
				setNoGravity(false);
				this.entityData.set(DATA_JETTING, false);
			}

			return;
		}

		if (hopTo != null && hopFrom != null) {
			resetFallDistance();
			hopTick++;
			double p = Math.min(1.0, hopTick / (double) hopTicks);
			Vec3 next = hopFrom.lerp(hopTo, p).add(0, hopArc * 4 * p * (1 - p), 0);
			setDeltaMovement(next.subtract(position()));
			this.hurtMarked = true;

			if (p >= 1.0 || hopTick > 2 && horizontalCollision) {
				endHop();
			}

			return;
		}

		if (staggerOnLanding && onGround()) {
			staggerOnLanding = false;
			ControlEffects.stagger(null, this, staggerTicks(), 2);
		}
	}

	/** Ticks of the stagger after being pushed out of a hop. */
	protected int staggerTicks() {
		return HunterBalance.get().jetStaggerTicks;
	}

	private void endHop() {
		hopFrom = null;
		hopTo = null;
		setNoGravity(false);
		this.entityData.set(DATA_JETTING, false);
	}

	public boolean isHopping() {
		return hopTo != null || descending;
	}

	/**
	 * A spot {@code min} to {@code max} blocks away to hop to: higher ground and a flank on
	 * {@code target} score best, a clear line to it helps. Null when none fits.
	 */
	public @Nullable Vec3 findHopSpot(ServerLevel level, @Nullable LivingEntity target, double min, double max) {
		Vec3 best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		Vec3 toTarget = target == null ? null : target.position().subtract(position()).multiply(1, 0, 1);

		for (int i = 0; i < 16; i++) {
			double angle = getRandom().nextDouble() * Math.PI * 2;
			double distance = min + getRandom().nextDouble() * (max - min);
			int x = Mth.floor(getX() + Math.cos(angle) * distance);
			int z = Mth.floor(getZ() + Math.sin(angle) * distance);

			if (!level.hasChunkAt(new BlockPos(x, getBlockY(), z))) {
				continue;
			}

			// API-CHECK: Level#getHeight(Heightmap.Types, int, int) (as TheHunt).
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

			Vec3 spot = new Vec3(x + 0.5, y, z + 0.5);

			if (Math.abs(y - getBlockY()) > 6 || !level.noCollision(this, makeBoundingBox(spot))) {
				continue;
			}

			double score = (y - getY()) * 1.5;

			if (target != null && toTarget != null && toTarget.lengthSqr() > 1.0e-4) {
				Vec3 move = spot.subtract(position()).multiply(1, 0, 1).normalize();
				score += (1.0 - Math.abs(move.dot(toTarget.normalize()))) * 3.0;
				double d = spot.distanceTo(target.position());
				score -= d < HunterBalance.get().minRange ? 4.0 : 0.0;
				score += clearLine(level, spot.add(0, getEyeHeight(), 0), target.getEyePosition()) ? 2.0 : 0.0;
			}

			if (score > bestScore) {
				bestScore = score;
				best = spot;
			}
		}

		return best;
	}

	private boolean clearLine(ServerLevel level, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
	}

	// ---------------------------------------------------------------- rifle

	/** Where the rifle's bolts leave: in front of the shooting shoulder at chest height. */
	public Vec3 muzzlePosition() {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		return new Vec3(getX() + fx * 0.9 - fz * 0.3, getY() + 1.38, getZ() + fz * 0.9 + fx * 0.3);
	}

	/** The wrist the gadgets fire from (left forearm). */
	public Vec3 wristPosition() {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		return new Vec3(getX() + fx * 0.6 + fz * 0.35, getY() + 1.3, getZ() + fz * 0.6 - fx * 0.35);
	}

	/**
	 * One gold bolt at {@code target}, aimed ahead of its motion by {@code lead} of the bolt's flight
	 * ({@code speedLead} for a Force Speed runner). Server only.
	 */
	public BlasterBolt fireBolt(ServerLevel level, LivingEntity target, float damage, float speed, int lifetime, float inaccuracy, float lead,
			float speedLead, SoundEvent sound) {
		Vec3 muzzle = muzzlePosition();
		Vec3 aim = target.getEyePosition().subtract(0, 0.35, 0);
		double flight = muzzle.distanceTo(aim) / Math.max(0.1, speed);
		float factor = target instanceof ServerPlayer player && ForceSpeed.isRunning(player) ? speedLead : lead;
		aim = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(flight * factor));
		Vec3 dir = aim.subtract(muzzle);
		BlasterBolt bolt = BlasterBolt.create(level, this, muzzle, damage, lifetime, BlasterBolt.Style.MANDALORIAN, false);
		bolt.shoot(dir.x, dir.y, dir.z, speed, inaccuracy);
		level.addFreshEntity(bolt);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, sound, getSoundSource(), 1f, 0.9f + getRandom().nextFloat() * 0.1f);
		EffectEvents.around(level, EnemyEffects.BLASTER_FIRE, muzzle, bolt.getDeltaMovement().normalize(), getId(), 1f, BlasterBolt.Style.MANDALORIAN.ordinal());
		return bolt;
	}

	// ---------------------------------------------------------------- rocket

	/** The rocket at {@code target}'s chest. Server only. */
	public HunterRocket fireRocket(ServerLevel level, LivingEntity target, float speed, float damage, float radius, int lifetime) {
		Vec3 wrist = wristPosition();
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
		return HunterRocket.launch(level, this, wrist, aim.subtract(wrist), speed, damage, radius, lifetime);
	}

	// ---------------------------------------------------------------- flamer

	/** Lights the flamer's cone for {@code ticks}: {@code damage} every {@code interval} ticks within {@code length} and {@code degrees}. */
	public void startFlame(ServerLevel level, int ticks, float damage, int interval, double length, float degrees, float fireSeconds) {
		flameTicks = ticks;
		flameAge = 0;
		flameDamage = damage;
		flameInterval = Math.max(1, interval);
		flameLength = length;
		flameDegrees = degrees;
		flameFireSeconds = fireSeconds;
		this.entityData.set(DATA_FLAMING, true);
		EffectEvents.around(level, HunterEffects.ATTACK, wristPosition(), getLookAngle(), getId(), ticks, HunterEffects.ATTACK_FLAME);
	}

	/** Puts the flame out (its time is up, a stagger, death, a yield). */
	public void stopFlame() {
		flameTicks = 0;
		this.entityData.set(DATA_FLAMING, false);
	}

	private void tickFlame(ServerLevel level) {
		if (flameTicks <= 0) {
			return;
		}

		if (--flameTicks <= 0 || !isAlive()) {
			stopFlame();
			return;
		}

		if (flameAge++ % flameInterval == 0) {
			burn(level);
		}
	}

	/** One pulse of the cone: everyone in it but the hunter's own side is burned and set alight. */
	private void burn(ServerLevel level) {
		Vec3 wrist = wristPosition();
		Vec3 look = getLookAngle();
		double cos = Math.cos(Math.toRadians(flameDegrees));
		// API-CHECK: DamageSources#source(key, direct, causing) with a null direct entity: no source position, so a saber guard ignores the flame (10.1).
		DamageSource source = level.damageSources().source(HunterDamage.WRIST_FLAMER, null, this);
		List<LivingEntity> inCone = level.getEntitiesOfClass(LivingEntity.class, new AABB(wrist, wrist).inflate(flameLength + 1.0),
				e -> e != this && e.isAlive() && !Allegiances.friendly(this, e));

		for (LivingEntity target : inCone) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(wrist);
			double distance = to.length();

			if (distance > flameLength + target.getBbWidth() * 0.5 || distance > 1.0e-3 && to.normalize().dot(look) < cos
					|| !clearLine(level, wrist, target.getBoundingBox().getCenter())) {
				continue;
			}

			if (target.hurtServer(level, source, flameDamage) && !target.fireImmune()) {
				target.igniteForSeconds(flameFireSeconds);
			}
		}
	}

	// ---------------------------------------------------------------- Yieldable

	@Override
	public boolean canYield() {
		return ambush != AMBUSH_NONE && !fightSpawn && departTicks < 0 && isLastOfGroup();
	}

	/** No other living hunter of this hunter's ambush is near. */
	public boolean isLastOfGroup() {
		if (group == null) {
			return true;
		}

		return level().getEntitiesOfClass(MandalorianHunter.class, getBoundingBox().inflate(GROUP_RANGE),
				h -> h != this && h.isAlive() && group.equals(h.group) && !h.isYielded()).isEmpty();
	}

	@Override
	public float yieldHealthFraction() {
		return HunterBalance.get().yieldAt;
	}

	@Override
	public @Nullable UUID yieldDecider() {
		return owner();
	}

	@Override
	public boolean isYielded() {
		return this.entityData.get(DATA_YIELDED);
	}

	/** Yielded: it kneels and waits. The first ambush's yield brings clue 2 and opens the talk for the owner. */
	@Override
	public void setYielded(boolean yielded) {
		this.entityData.set(DATA_YIELDED, yielded);

		if (!yielded) {
			return;
		}

		setTarget(null);
		getNavigation().stop();
		setCharging(false);
		setGadget(GADGET_NONE);
		stopFlame();

		if (ambush == AMBUSH_ONE && level() instanceof ServerLevel level) {
			UUID owner = owner();
			ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);

			if (player != null) {
				if (player.level() == level && player.distanceToSqr(this) <= 24 * 24) {
					Dialogues.open(player, YIELD_DIALOGUE, this);
				}

				ChapterFour.addClue(player, 2);
			}
		}
	}

	/** Spared, questioned or fled: it gets up and jets away. Executed: the identity feature kills it. */
	@Override
	public void onYieldResolved(YieldOutcome outcome) {
		this.entityData.set(DATA_YIELDED, false);

		if (outcome != YieldOutcome.EXECUTED) {
			departTicks = DEPART_TICKS;
			setTarget(null);
		}
	}

	@Override
	public @Nullable Identifier yieldDialogue() {
		return YIELD_DIALOGUE;
	}

	// ---------------------------------------------------------------- damage, sounds, death

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && isJetting() && !descending && amount >= 4f) {
			// A heavy hit in the air knocks the hunter out of its hop.
			onPushedInAir();
		}

		return hurt;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.MANDALORIAN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.MANDALORIAN_DEATH;
	}

	/** Owned hunters drop only for their owner's fights; hunters Kaast called never drop (no farming the camp). */
	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return super.shouldDropLoot(level) && mayDropLoot(level);
	}

	/**
	 * The anti-farm rule ({@link HunterRules#dropsLoot}) for this hunter now, from who hurt it last:
	 * whether its death may drop anything. Other drops on a hunter's death (W2's credit chits) can ask it too.
	 */
	public boolean mayDropLoot(ServerLevel level) {
		return HunterRules.dropsLoot(fightSpawn, owner(), getLastHurtByPlayer() == null ? null : getLastHurtByPlayer().getUUID(), ownerNear(level));
	}

	private boolean ownerNear(ServerLevel level) {
		UUID owner = owner();
		ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
		return player != null && player.level() == level && player.distanceToSqr(this) <= 32 * 32;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (deathHandled || !(level() instanceof ServerLevel level)) {
			return;
		}

		deathHandled = true;
		stopFlame();
		setCharging(false);
		setGadget(GADGET_NONE);
		ServerPlayer killer = FoesFeature.killer(this, source);

		if (killer != null) {
			onKilled(killer);
		}
	}

	/** The death's quest hook: a hunter killed, by its owner or someone else. Kaast's end is W2's, not a hunter kill. */
	protected void onKilled(ServerPlayer killer) {
		UUID owner = owner();
		QuestHooks.onHunterKilled(killer, this, owner != null && owner.equals(killer.getUUID()));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
		this.entityData.set(DATA_PAINT, getRandom().nextInt(PAINTS));
		setPersistenceRequired();
		setCanPickUpLoot(false);
		return result;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("HunterPaint", paint());
		output.storeNullable("HunterGroup", UUIDUtil.CODEC, group);
		output.putInt("HunterAmbush", ambush);
		output.putBoolean("HunterFightSpawn", fightSpawn);
		output.putBoolean("HunterYielded", isYielded());
		output.putInt("HunterDepart", departTicks);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_PAINT, input.getIntOr("HunterPaint", 0));
		this.group = input.read("HunterGroup", UUIDUtil.CODEC).orElse(null);
		this.ambush = input.getIntOr("HunterAmbush", AMBUSH_NONE);
		this.fightSpawn = input.getBooleanOr("HunterFightSpawn", false);
		this.entityData.set(DATA_YIELDED, input.getBooleanOr("HunterYielded", false));
		this.departTicks = input.getIntOr("HunterDepart", -1);
	}
}
