package com.oldrepublic.enemy.stormback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.FoesFeature;
import com.oldrepublic.enemy.hymn.FoeKills;
import com.oldrepublic.enemy.hymn.FoeTargets;
import com.oldrepublic.enemy.hymn.Interruptible;
import com.oldrepublic.enemy.hymn.TellLog;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.KillXp;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A stormback of Tyrvann (spec MILESTONE_5.md 12.1): a heavy, slate-plated grazer of the basalt and
 * the stormwood. Neutral until provoked: struck, or crowded by a player within 6 blocks for 60 ticks.
 * Then it charges (the M4 hollowhorn's pattern, {@link StormbackChargeGoal}). Lightning that strikes
 * it <b>charges</b> it: 400 ticks of crackling plates, +50% damage, and its charge shocks for 20
 * ticks; it drops stormglass if it dies charged. Once per fight, below half health, it <b>roars</b>
 * a bolt down onto itself (a 30-tick tell): the bolt charges it at once and strikes everyone within
 * 3 blocks for 6 and a 20-tick shock. Never in Peaceful.
 */
public class Stormback extends PathfinderMob implements Interruptible, KillXp {
	/** The client's pose: grazing or walking, the charge tell, the charge, stunned against a wall, the roar. */
	public static final int STATE_IDLE = 0;
	public static final int STATE_TELL = 1;
	public static final int STATE_CHARGING = 2;
	public static final int STATE_STUNNED = 3;
	public static final int STATE_ROAR = 4;

	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(Stormback.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_CHARGED = SynchedEntityData.defineId(Stormback.class, EntityDataSerializers.BOOLEAN);
	/** Ticks between the crowding checks. */
	public static final int CROWD_INTERVAL = 10;

	private final TellLog tells = new TellLog();
	private final Map<UUID, Integer> crowding = new HashMap<>();
	private @Nullable UUID angerTarget;
	private long angerUntil;
	private boolean angerProvoked;
	private long chargedUntil;
	private boolean roared;
	private boolean setUp;
	private boolean deathHandled;
	private boolean interruptRequested;
	private int clientStateTicks;

	public Stormback(EntityType<? extends Stormback> type, Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	/** Default attributes; the balance replaces them on the first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		StormbackBalance b = new StormbackBalance();
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, b.health)
				.add(Attributes.ARMOR, b.armor)
				.add(Attributes.MOVEMENT_SPEED, b.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance)
				.add(Attributes.FOLLOW_RANGE, b.followRange)
				.add(Attributes.ATTACK_DAMAGE, b.buttDamage);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new StormbackChargeGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_IDLE);
		builder.define(DATA_CHARGED, false);
	}

	// ---------------------------------------------------------------- state

	/** {@code STATE_*}, for the client's pose. */
	public int state() {
		return this.entityData.get(DATA_STATE);
	}

	void setState(int state) {
		this.entityData.set(DATA_STATE, state);
	}

	/** Charged by lightning: crackling plates, +50% damage, a shocking charge. */
	public boolean isCharged() {
		return this.entityData.get(DATA_CHARGED);
	}

	/** Kill XP (16.3): more for one that dies charged. */
	@Override
	public int killXp() {
		StormbackBalance b = StormbackBalance.get();
		return isCharged() ? b.chargedXp : b.xp;
	}

	public boolean isStunned() {
		return state() == STATE_STUNNED;
	}

	/** Client only: ticks the current state has shown. */
	public int clientStateTicks() {
		return clientStateTicks;
	}

	/** Its tells and heavy hits (the tests read it). */
	public TellLog tellLog() {
		return tells;
	}

	/** Whether it is angry at {@code entity} now. */
	public boolean isAngryAt(LivingEntity entity) {
		return angerTarget != null && angerTarget.equals(entity.getUUID()) && level().getGameTime() < angerUntil;
	}

	/** Whether that anger was provoked by {@code entity} striking first (the fight then does not count as hostile, 7). */
	public boolean isProvokedBy(LivingEntity entity) {
		return isAngryAt(entity) && angerProvoked;
	}

	/** Whether its once-a-fight roar is spent. */
	public boolean hasRoared() {
		return roared;
	}

	void markRoared() {
		roared = true;
	}

	/** The damage multiplier while charged (1 otherwise). */
	public float damageMultiplier() {
		return isCharged() ? 1f + StormbackBalance.get().chargedDamageBonus : 1f;
	}

	@Override
	public void interrupt() {
		interruptRequested = true;
	}

	boolean consumeInterrupt() {
		boolean value = interruptRequested;
		interruptRequested = false;
		return value;
	}

	// ---------------------------------------------------------------- lightning

	/** Struck by real lightning (ridge lightning seeks it, 3.3): no burn and no harm, it drinks the storm. */
	@Override
	public void thunderHit(ServerLevel level, LightningBolt bolt) {
		charge(level);
	}

	/** Charges it for {@code chargedTicks} (again from now if it already was). Server side. */
	public void charge(ServerLevel level) {
		StormbackBalance b = StormbackBalance.get();
		boolean was = isCharged();
		chargedUntil = level.getGameTime() + b.chargedTicks;
		this.entityData.set(DATA_CHARGED, true);

		if (!was) {
			// The crackle is the client's loop (stormback.charged, while the synced flag holds).
			EffectEvents.toTracking(this, StormbackEffects.EVENT, position().add(0, getBbHeight() * 0.7, 0), null, getId(), b.chargedTicks, StormbackEffects.CHARGED);
		}
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			clientStateTicks = state() != STATE_IDLE ? clientStateTicks + 1 : 0;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance();
		}

		long now = level.getGameTime();

		if (isCharged() && now >= chargedUntil) {
			this.entityData.set(DATA_CHARGED, false);
			EffectEvents.toTracking(this, StormbackEffects.EVENT, position().add(0, getBbHeight() * 0.7, 0), null, getId(), 1f, StormbackEffects.SPENT);
		}

		if (tickCount % CROWD_INTERVAL == 0) {
			checkCrowding(level.players(), now);
		}

		updateTarget(level, now);
	}

	/**
	 * One crowding check of {@code players} (the level's, every {@link #CROWD_INTERVAL} ticks): a player who lingers
	 * within {@code provokeRange} for {@code provokeTicks} angers it (it started that fight). Public for the tests, whose
	 * survival players are fake players, not in the level's list.
	 */
	public void checkCrowding(List<? extends Player> players, long now) {
		StormbackBalance b = StormbackBalance.get();
		double r = b.provokeRange * b.provokeRange;
		Map<UUID, Integer> seen = new HashMap<>();

		for (Player player : players) {
			if (!player.isAlive() || FoeTargets.ignored(player) || player.distanceToSqr(this) > r) {
				continue;
			}

			int ticks = crowding.getOrDefault(player.getUUID(), 0) + CROWD_INTERVAL;
			seen.put(player.getUUID(), ticks);

			if (ticks >= b.provokeTicks && !isAngryAt(player)) {
				anger(player, false, now);
			}
		}

		crowding.clear();
		crowding.putAll(seen);
	}

	/** Its target while the anger lasts; out of anger it grazes. */
	private void updateTarget(ServerLevel level, long now) {
		if (angerTarget == null) {
			if (getTarget() != null) {
				setTarget(null);
			}

			return;
		}

		double range = getAttributeValue(Attributes.FOLLOW_RANGE);

		if (now >= angerUntil || !(level.getEntity(angerTarget) instanceof LivingEntity living) || !living.isAlive() || FoeTargets.ignored(living)
				|| living.level() != level || living.distanceToSqr(this) > range * range) {
			angerTarget = null;
			roared = false;
			setTarget(null);
			return;
		}

		if (getTarget() != living) {
			setTarget(living);
		}
	}

	private void anger(LivingEntity who, boolean provoked, long now) {
		if (angerTarget == null || !angerTarget.equals(who.getUUID())) {
			angerProvoked = provoked;
		}

		angerTarget = who.getUUID();
		angerUntil = now + StormbackBalance.get().angerTicks;
	}

	private void applyBalance() {
		StormbackBalance b = StormbackBalance.get();
		boolean full = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, b.health);
		setBase(Attributes.ARMOR, b.armor);
		setBase(Attributes.MOVEMENT_SPEED, b.speed);
		setBase(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance);
		setBase(Attributes.FOLLOW_RANGE, b.followRange);
		setBase(Attributes.ATTACK_DAMAGE, b.buttDamage);

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

	// ---------------------------------------------------------------- damage, sounds, death, drops

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && isAlive() && source.getEntity() instanceof LivingEntity attacker && attacker != this && !(attacker instanceof Stormback)
				&& !FoeTargets.ignored(attacker)) {
			anger(attacker, !isAngryAt(attacker) || angerProvoked, level.getGameTime());
		}

		return hurt;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.STORMBACK_IDLE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.STORMBACK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.STORMBACK_DEATH;
	}

	/** Dying charged: 1 to 2 stormglass shards (12.1); the leather is the loot table's. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);

		if (isCharged()) {
			StormbackBalance b = StormbackBalance.get();
			int count = b.stormglassMin + getRandom().nextInt(Math.max(1, b.stormglassMax - b.stormglassMin + 1));

			if (count > 0) {
				spawnAtLocation(level, new ItemStack(M5Items.STORMGLASS_SHARD, count));
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (deathHandled || !(level() instanceof ServerLevel)) {
			return;
		}

		deathHandled = true;
		setState(STATE_IDLE);
		ServerPlayer killer = FoesFeature.killer(this, source);

		if (killer != null) {
			FoeKills.report(killer, this, StormbackEntities.ID, null, Attitudes.hostileSnapshot(this, killer));
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("StormbackCharged", isCharged() ? (int) Math.max(0, chargedUntil - level().getGameTime()) : 0);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		int left = input.getIntOr("StormbackCharged", 0);

		if (left > 0) {
			chargedUntil = level().getGameTime() + left;
			this.entityData.set(DATA_CHARGED, true);
		}
	}
}
