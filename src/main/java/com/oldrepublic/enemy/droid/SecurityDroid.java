package com.oldrepublic.enemy.droid;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEffects;
import com.oldrepublic.enemy.FoesFeature;
import com.oldrepublic.enemy.mandalorian.HunterDamage;
import com.oldrepublic.force.choke.ChokeImmune;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Mk IV security droid (spec MILESTONE_4.md 10.2): a tall sentry with twin arm blasters and a
 * sweeping sensor eye. It shrugs off blaster bolts (-50%) and does not breathe, burn or take poison
 * (immune to Force Choke, the hunters' flamer, fire, poison and wither); Telekinetic Lift cannot
 * raise it and Push moves it half as far ({@code FoesFeature}'s answers). Shocks hurt it more and
 * its own bolts parried back hit it for half again.
 *
 * <p>Its volley is told by a 20-tick barrel spin and whir ({@code droid_tell} 0); below half health,
 * once, it raises a frontal plate for 100 ticks that stops bolts from the front (sparks where they
 * strike). Dying, it sparks for 20 ticks and bursts. Two roles: an Imperial guard (crimson bolts,
 * not hostile to Sith-path players unless provoked) or a hunter's escort (gold bolts, whose death is
 * the second ambush's clue).
 */
public class SecurityDroid extends Monster implements Aligned, ChokeImmune {
	/** Bolts this droid fires carry this tag, so a parried one back at it is known. */
	public static final String BOLT_TAG = "oldrepublic.droid_bolt";

	private static final EntityDataAccessor<Boolean> DATA_SPINNING = SynchedEntityData.defineId(SecurityDroid.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_FIRING = SynchedEntityData.defineId(SecurityDroid.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_SHIELD = SynchedEntityData.defineId(SecurityDroid.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_GUARD = SynchedEntityData.defineId(SecurityDroid.class, EntityDataSerializers.BOOLEAN);

	private @Nullable UUID group;
	private boolean escort;
	private boolean shieldUsed;
	private int shieldTicks;
	private long staggerUntil;
	private boolean interruptRequested;
	private boolean setUp;
	private boolean deathHandled;
	private int absentTicks;

	public SecurityDroid(EntityType<? extends SecurityDroid> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		DroidBalance b = new DroidBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, b.maxHealth)
				.add(Attributes.ARMOR, b.armor)
				.add(Attributes.MOVEMENT_SPEED, b.movementSpeed)
				.add(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance)
				.add(Attributes.FOLLOW_RANGE, b.followRange)
				.add(Attributes.ATTACK_DAMAGE, 3.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new DroidCombatGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// API-CHECK: TargetingConditions.Selector as (LivingEntity, ServerLevel) -> boolean.
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
				(target, level) -> target instanceof Player player && Attitudes.toward(this, player) == Attitude.HOSTILE));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SPINNING, false);
		builder.define(DATA_FIRING, false);
		builder.define(DATA_SHIELD, false);
		builder.define(DATA_GUARD, false);
	}

	// ---------------------------------------------------------------- roles

	/** An Imperial guard (the Proving, greater tombs) instead of a hunter's escort. */
	public void setImperialGuard(boolean guard) {
		this.entityData.set(DATA_GUARD, guard);
	}

	public boolean isImperialGuard() {
		return this.entityData.get(DATA_GUARD);
	}

	/** Makes this droid the escort of a hunter ambush (the second one): its death is clue 3. */
	public void joinEscort(UUID group) {
		this.group = group;
		this.escort = true;
	}

	public boolean isEscort() {
		return escort;
	}

	public @Nullable UUID group() {
		return group;
	}

	@Override
	public Allegiance allegiance() {
		// An escort fights with the hunters.
		return isImperialGuard() ? Allegiance.EMPIRE : Allegiance.HUNTERS;
	}

	// ---------------------------------------------------------------- synced state

	public boolean isSpinning() {
		return this.entityData.get(DATA_SPINNING);
	}

	void setSpinning(boolean spinning) {
		this.entityData.set(DATA_SPINNING, spinning);
	}

	public boolean isFiring() {
		return this.entityData.get(DATA_FIRING);
	}

	void setFiring(boolean firing) {
		this.entityData.set(DATA_FIRING, firing);
	}

	public boolean isShielded() {
		return this.entityData.get(DATA_SHIELD);
	}

	/** Whether the shield has come up already this life (it comes up once). */
	public boolean shieldUsed() {
		return shieldUsed;
	}

	// ---------------------------------------------------------------- interrupts

	/** A stagger or shock: the spin stops; a stagger also keeps it from firing while it lasts. */
	public void interrupt(String cause) {
		interruptRequested = true;
		MobEffectInstance slow = getEffect(MobEffects.SLOWNESS);

		if (slow != null) {
			staggerUntil = Math.max(staggerUntil, level().getGameTime() + slow.getDuration());
		}
	}

	boolean consumeInterrupt() {
		boolean value = interruptRequested;
		interruptRequested = false;
		return value;
	}

	/** Staggered (a stun grenade's 60 ticks, a pulse): no spin and no volley. */
	public boolean isStaggered() {
		return level().getGameTime() < staggerUntil;
	}

	// ---------------------------------------------------------------- ticking

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance();
		}

		if (shieldTicks > 0 && --shieldTicks <= 0) {
			this.entityData.set(DATA_SHIELD, false);
		}

		tickOwner(level);
	}

	private void applyBalance() {
		DroidBalance b = DroidBalance.get();
		setBase(Attributes.ARMOR, b.armor);
		setBase(Attributes.MOVEMENT_SPEED, b.movementSpeed);
		setBase(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance);
		setBase(Attributes.FOLLOW_RANGE, b.followRange);
		boolean full = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, b.maxHealth);

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

	/** An escort whose owner is gone powers down and leaves, as its hunters do. */
	private void tickOwner(ServerLevel level) {
		UUID owner = StoryTags.owner(this);

		if (owner == null || !escort) {
			return;
		}

		DroidBalance b = DroidBalance.get();
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		boolean present = player != null && player.level() == level && player.isAlive() && player.distanceToSqr(this) <= b.ownerLeash * b.ownerLeash;
		absentTicks = present ? 0 : absentTicks + 1;

		if (absentTicks >= b.ownerAbsentTicks) {
			discard();
		}
	}

	// ---------------------------------------------------------------- fire

	/** Where the bolts leave: the right or left arm barrel, at chest height. */
	public Vec3 barrel(boolean left) {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double side = left ? -0.45 : 0.45;
		return new Vec3(getX() + fx * 0.7 - fz * side, getY() + 1.55, getZ() + fz * 0.7 + fx * side);
	}

	/** One bolt from alternating barrels at {@code target}. Server only. */
	void fireBolt(ServerLevel level, LivingEntity target, boolean left, DroidBalance b) {
		Vec3 muzzle = barrel(left);
		Vec3 aim = target.getEyePosition().subtract(0, 0.4, 0);
		double flight = muzzle.distanceTo(aim) / Math.max(0.1, b.boltSpeed);
		aim = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(flight * 0.5));
		Vec3 dir = aim.subtract(muzzle);
		BlasterBolt.Style style = isImperialGuard() ? BlasterBolt.Style.IMPERIAL : BlasterBolt.Style.MANDALORIAN;
		BlasterBolt bolt = BlasterBolt.create(level, this, muzzle, b.boltDamage, b.boltLifetimeTicks, style, false);
		bolt.addTag(BOLT_TAG);
		bolt.shoot(dir.x, dir.y, dir.z, b.boltSpeed, b.aimInaccuracy);
		level.addFreshEntity(bolt);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.DROID_FIRE, getSoundSource(), 0.9f, 0.95f + getRandom().nextFloat() * 0.1f);
		EffectEvents.around(level, EnemyEffects.BLASTER_FIRE, muzzle, bolt.getDeltaMovement().normalize(), getId(), 1f, style.ordinal());
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(HunterDamage.WRIST_FLAMER)) {
			return false;
		}

		if (isShielded() && shieldStops(source)) {
			Vec3 at = source.getDirectEntity() != null ? source.getDirectEntity().position() : getEyePosition();
			EffectEvents.toTracking(this, DroidEffects.TELL, at, null, getId(), 1f, DroidEffects.SHIELD);
			return false;
		}

		boolean hurt = super.hurtServer(level, source, amount);
		DroidBalance b = DroidBalance.get();

		if (hurt && isAlive() && !shieldUsed && getHealth() <= getMaxHealth() * b.shieldAt) {
			shieldUsed = true;
			shieldTicks = b.shieldTicks;
			this.entityData.set(DATA_SHIELD, true);
			EffectEvents.toTracking(this, DroidEffects.TELL, getEyePosition(), getLookAngle(), getId(), b.shieldTicks, DroidEffects.SHIELD);
		}

		return hurt;
	}

	/** The frontal plate stops projectiles that arrive within {@code shieldDegrees} of the droid's facing. */
	private boolean shieldStops(DamageSource source) {
		if (!(source.getDirectEntity() instanceof Projectile projectile)) {
			return false;
		}

		Vec3 to = projectile.position().subtract(position()).multiply(1, 0, 1);
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		return to.lengthSqr() < 1.0e-4 || to.normalize().dot(facing) >= Math.cos(Math.toRadians(DroidBalance.get().shieldDegrees));
	}

	/** Fire, poison and wither do nothing to it. */
	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.WITHER) && super.canBeAffected(effect);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.DROID_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.DROID_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(ModSounds.DROID_STEP, 0.35f, 0.9f + getRandom().nextFloat() * 0.2f);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (deathHandled || !(level() instanceof ServerLevel level)) {
			return;
		}

		deathHandled = true;
		setSpinning(false);
		setFiring(false);
		this.entityData.set(DATA_SHIELD, false);
		EffectEvents.toTracking(this, DroidEffects.TELL, getEyePosition(), null, getId(), DroidBalance.get().deathTellTicks, DroidEffects.DEATH);
		ServerPlayer killer = FoesFeature.killer(this, source);

		if (killer != null) {
			QuestHooks.onDroidKilled(killer, this);
		}

		UUID owner = StoryTags.owner(this);
		ServerPlayer ownerPlayer = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);

		if (escort && ownerPlayer != null) {
			// The second ambush's escort: clue 3 when it dies, whoever destroyed it (8.5).
			ChapterFour.addClue(ownerPlayer, 3);
		}
	}

	/** The death sparks run {@code deathTellTicks} (the death animation's length), then the burst. */
	@Override
	protected void tickDeath() {
		if (level() instanceof ServerLevel level && this.deathTime == Math.max(1, DroidBalance.get().deathTellTicks) - 1) {
			burst(level);
		}

		super.tickDeath();
	}

	/** The death burst: {@code deathBurstDamage} to everything within {@code deathBurstRadius} but its own side. */
	private void burst(ServerLevel level) {
		DroidBalance b = DroidBalance.get();
		Vec3 centre = getBoundingBox().getCenter();
		DamageSource source = level.damageSources().explosion(this, this);

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(b.deathBurstRadius + 1.0),
				e -> e != this && e.isAlive() && !Allegiances.friendly(this, e))) {
			if (target.getBoundingBox().getCenter().distanceTo(centre) <= b.deathBurstRadius + target.getBbWidth() * 0.5) {
				target.hurtServer(level, source, b.deathBurstDamage);
			}
		}

		EffectEvents.toTracking(this, DroidEffects.TELL, centre, null, getId(), 0f, DroidEffects.DEATH);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
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
		output.putBoolean("DroidGuard", isImperialGuard());
		output.storeNullable("DroidGroup", UUIDUtil.CODEC, group);
		output.putBoolean("DroidEscort", escort);
		output.putBoolean("DroidShieldUsed", shieldUsed);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setImperialGuard(input.getBooleanOr("DroidGuard", false));
		this.group = input.read("DroidGroup", UUIDUtil.CODEC).orElse(null);
		this.escort = input.getBooleanOr("DroidEscort", false);
		this.shieldUsed = input.getBooleanOr("DroidShieldUsed", false);
	}
}
