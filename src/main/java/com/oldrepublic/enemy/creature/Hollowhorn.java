package com.oldrepublic.enemy.creature;

import java.util.EnumSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.FoesFeature;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The hollowhorn (spec MILESTONE_4.md 10.3): a shaggy, elk-like grazer of the Selvaren meadows,
 * living in herds of 3 to 5 with a bull and sometimes calves. It is neutral: hit one, or come within
 * {@code calfGuardRange} of a calf, and the herd is hostile for {@code angerTicks}. Its attack is the
 * telegraphed straight charge ({@link HollowhornChargeGoal}); a charge into a wall stuns it, and a
 * stunned hollowhorn takes {@code stunDamageMult} damage. Calves flee and never charge.
 *
 * <p>The corrupted variant is the same entity with the {@code oldrepublic.corrupted} tag, set by the
 * Heartstone waves and corrupted waystones (W1, W2): always hostile, never a calf, no drops.
 */
public class Hollowhorn extends PathfinderMob {
	/** The entity tag W1's and W2's wave spawners set on corrupted beasts. */
	public static final String CORRUPTED_TAG = "oldrepublic.corrupted";

	/** The client's pose: grazing or walking, the charge tell, the charge, stunned against a wall. */
	public static final int STATE_IDLE = 0;
	public static final int STATE_TELL = 1;
	public static final int STATE_CHARGING = 2;
	public static final int STATE_STUNNED = 3;

	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(Hollowhorn.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_CALF = SynchedEntityData.defineId(Hollowhorn.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_BULL = SynchedEntityData.defineId(Hollowhorn.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_CORRUPTED = SynchedEntityData.defineId(Hollowhorn.class, EntityDataSerializers.BOOLEAN);

	/** A corrupted hollowhorn looks for players this close. */
	private static final double CORRUPTED_SIGHT = 24;
	/** Ticks between a calf's checks for players too near it. */
	private static final int CALF_CHECK_TICKS = 10;

	/** Spawn group data: the index of the next member of a herd (its bull first). */
	static final class HerdData implements SpawnGroupData {
		private int count;

		int next() {
			return count++;
		}
	}

	private @Nullable UUID angerTarget;
	private long angerUntil;
	/** The anger came from the target striking the herd (provoked), not from a calf being approached. */
	private boolean angerProvoked;
	private boolean setUp;
	private boolean deathHandled;
	private int calfCheck;

	public Hollowhorn(EntityType<? extends Hollowhorn> type, Level level) {
		super(type, level);
		this.xpReward = 3;
	}

	/** Default attributes; the balance replaces them on the first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		CreatureBalance b = new CreatureBalance();
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, b.hollowhornHealth)
				.add(Attributes.MOVEMENT_SPEED, b.hollowhornSpeed)
				.add(Attributes.KNOCKBACK_RESISTANCE, b.hollowhornKnockbackResistance)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.ATTACK_DAMAGE, b.buttDamage);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 12f, 1.0, 1.5, e -> isCalf() && isAngryAt(e)));
		this.goalSelector.addGoal(2, new HollowhornChargeGoal(this));
		this.goalSelector.addGoal(4, new HerdGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_IDLE);
		builder.define(DATA_CALF, false);
		builder.define(DATA_BULL, false);
		builder.define(DATA_CORRUPTED, false);
	}

	// ---------------------------------------------------------------- state

	/** {@code STATE_*}, for the client's pose. */
	public int state() {
		return this.entityData.get(DATA_STATE);
	}

	void setState(int state) {
		this.entityData.set(DATA_STATE, state);
	}

	public boolean isCalf() {
		return this.entityData.get(DATA_CALF);
	}

	public boolean isBull() {
		return this.entityData.get(DATA_BULL);
	}

	public boolean isCorrupted() {
		return this.entityData.get(DATA_CORRUPTED);
	}

	public boolean isStunned() {
		return state() == STATE_STUNNED;
	}

	/** Makes this a calf (smaller, flees, never charges) or a grown animal. Server only. */
	public void setCalf(boolean calf) {
		this.entityData.set(DATA_CALF, calf);
		setBase(Attributes.SCALE, calf ? CreatureBalance.get().calfScale : 1.0);
	}

	/** Whether this hollowhorn is angry at {@code entity} now (hit by it, or guarding a calf from it). */
	public boolean isAngryAt(Entity entity) {
		return angerTarget != null && angerTarget.equals(entity.getUUID()) && level().getGameTime() < angerUntil;
	}

	/** Whether the anger at {@code entity} was provoked by it striking first. */
	public boolean isProvokedBy(Entity entity) {
		return isAngryAt(entity) && angerProvoked;
	}

	// ---------------------------------------------------------------- ticking

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance();
		}

		if (!isCorrupted() && entityTags().contains(CORRUPTED_TAG)) {
			becomeCorrupted();
		}

		updateTarget(level);

		if (isCalf() && ++calfCheck >= CALF_CHECK_TICKS) {
			calfCheck = 0;
			guardCalf(level);
		}
	}

	private void applyBalance() {
		CreatureBalance b = CreatureBalance.get();
		setBase(Attributes.MOVEMENT_SPEED, b.hollowhornSpeed);
		setBase(Attributes.KNOCKBACK_RESISTANCE, b.hollowhornKnockbackResistance);
		setBase(Attributes.ATTACK_DAMAGE, b.buttDamage);
		boolean full = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, isBull() ? b.bullHealth : b.hollowhornHealth);

		if (full || getHealth() > getMaxHealth()) {
			setHealth(getMaxHealth());
		}

		if (isCalf()) {
			setBase(Attributes.SCALE, b.calfScale);
		}
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);

		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private void becomeCorrupted() {
		this.entityData.set(DATA_CORRUPTED, true);
		setCalf(false);
		angerTarget = null;
		setPersistenceRequired();
	}

	/** Corrupted: the nearest player. Otherwise the anger target while the anger lasts. */
	private void updateTarget(ServerLevel level) {
		if (isCorrupted()) {
			LivingEntity target = getTarget();

			if (target == null || !target.isAlive() || ignored(target) || target.distanceToSqr(this) > CORRUPTED_SIGHT * CORRUPTED_SIGHT * 2) {
				setTarget(nearestPlayer(level, CORRUPTED_SIGHT));
			}

			return;
		}

		if (angerTarget == null) {
			return;
		}

		double range = getAttributeValue(Attributes.FOLLOW_RANGE);

		if (level.getGameTime() >= angerUntil || !(level.getEntity(angerTarget) instanceof LivingEntity living) || !living.isAlive() || ignored(living)
				|| living.level() != level || living.distanceToSqr(this) > range * range) {
			angerTarget = null;
			setTarget(null);
			return;
		}

		if (!isCalf() && getTarget() != living) {
			setTarget(living);
		}
	}

	private @Nullable Player nearestPlayer(ServerLevel level, double range) {
		Player best = null;
		double bestDistance = range * range;

		for (ServerPlayer player : level.players()) {
			double distance = player.distanceToSqr(this);

			if (distance < bestDistance && player.isAlive() && !ignored(player)) {
				best = player;
				bestDistance = distance;
			}
		}

		return best;
	}

	/** Creative and spectator players. The game mode, not {@code isCreative()}: a game test's mock player always answers true there. */
	private static boolean ignored(LivingEntity entity) {
		return entity instanceof Player player && (player.gameMode() == GameType.CREATIVE || player.isSpectator());
	}

	/** A calf with a player too near angers its herd at that player. */
	private void guardCalf(ServerLevel level) {
		CreatureBalance b = CreatureBalance.get();

		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !ignored(player) && player.distanceToSqr(this) <= b.calfGuardRange * b.calfGuardRange && !isAngryAt(player)) {
				angerHerd(level, player, false);
				return;
			}
		}
	}

	/** This hollowhorn and every grown, uncorrupted hollowhorn within {@code herdRange} turn on {@code who}. */
	public void angerHerd(ServerLevel level, LivingEntity who, boolean provoked) {
		CreatureBalance b = CreatureBalance.get();
		anger(level, who, provoked, b.angerTicks);

		for (Hollowhorn other : level.getEntitiesOfClass(Hollowhorn.class, getBoundingBox().inflate(b.herdRange), h -> h != this && h.isAlive() && !h.isCorrupted())) {
			other.anger(level, who, provoked, b.angerTicks);
		}
	}

	private void anger(ServerLevel level, LivingEntity who, boolean provoked, int ticks) {
		if (isCorrupted()) {
			return;
		}

		angerTarget = who.getUUID();
		angerUntil = level.getGameTime() + ticks;
		angerProvoked = provoked;
	}

	// ---------------------------------------------------------------- damage, sounds, death

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, CreatureRules.hollowhornDamage(CreatureBalance.get(), amount, isStunned()));

		if (hurt && !isCorrupted() && source.getEntity() instanceof LivingEntity attacker && attacker != this && !(attacker instanceof Hollowhorn)
				&& !ignored(attacker)) {
			angerHerd(level, attacker, true);
		}

		return hurt;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.HOLLOWHORN_IDLE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.HOLLOWHORN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.HOLLOWHORN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return isCalf() ? super.getVoicePitch() * 1.3f : super.getVoicePitch();
	}

	/** Calves and corrupted hollowhorns drop nothing. */
	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return super.shouldDropLoot(level) && !isCalf() && !isCorrupted();
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
			QuestHooks.onBeastKilled(killer, this, "hollowhorn");
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		HerdData herd = groupData instanceof HerdData data ? data : new HerdData();
		int index = herd.next();
		boolean natural = reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
		this.entityData.set(DATA_BULL, CreatureRules.isBull(index, natural, getRandom().nextFloat()));
		setCalf(CreatureRules.isCalf(index, getRandom().nextFloat(), CreatureBalance.get().calfChance, isCorrupted()));
		super.finalizeSpawn(level, difficulty, reason, herd);
		return herd;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("HollowhornBull", isBull());
		output.putBoolean("HollowhornCalf", isCalf());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_BULL, input.getBooleanOr("HollowhornBull", false));
		this.entityData.set(DATA_CALF, input.getBooleanOr("HollowhornCalf", false));
	}

	// ---------------------------------------------------------------- herd

	/** Out of a fight, a hollowhorn that strays more than 10 blocks from its herd walks back to the nearest grown one. */
	private static final class HerdGoal extends Goal {
		private static final double STRAY = 10;
		private static final double CLOSE = 4;

		private final Hollowhorn mob;
		private @Nullable Hollowhorn leader;
		private int repath;

		HerdGoal(Hollowhorn mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (mob.getTarget() != null || mob.isCorrupted() || mob.getRandom().nextInt(40) != 0) {
				return false;
			}

			double range = CreatureBalance.get().herdRange;
			Hollowhorn nearest = null;
			double best = range * range;

			for (Hollowhorn other : mob.level().getEntitiesOfClass(Hollowhorn.class, mob.getBoundingBox().inflate(range),
					h -> h != mob && h.isAlive() && !h.isCalf() && !h.isCorrupted())) {
				double distance = other.distanceToSqr(mob) - (other.isBull() ? 16 : 0);

				if (distance < best) {
					best = distance;
					nearest = other;
				}
			}

			if (nearest == null || nearest.distanceToSqr(mob) < STRAY * STRAY) {
				return false;
			}

			leader = nearest;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return leader != null && leader.isAlive() && mob.getTarget() == null && leader.distanceToSqr(mob) > CLOSE * CLOSE;
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void tick() {
			if (leader != null && --repath <= 0) {
				repath = 10;
				mob.getNavigation().moveTo(leader, 1.0);
			}
		}

		@Override
		public void stop() {
			leader = null;
			mob.getNavigation().stop();
		}
	}
}
