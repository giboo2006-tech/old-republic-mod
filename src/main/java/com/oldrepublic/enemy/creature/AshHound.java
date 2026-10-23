package com.oldrepublic.enemy.creature;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.FoesFeature;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.ParryReactive;
import com.oldrepublic.saber.SaberCombat;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The ash hound (spec MILESTONE_4.md 10.4): a lean, grey, hairless hound that hunts Varkhast in
 * packs of 3 or 4 at dusk, at night and in the ashfall. Fire immune. The first to spot a player howls
 * and brings the pack; one holds in front while the others circle to the flanks, and each lunges
 * after a crouch and a growl ({@link AshHoundPackGoal}). A parried lunge, or one caught by a Barrier
 * pulse or any other interrupt, turns the hound aside and staggers it for {@code lungeStaggerTicks}.
 */
public class AshHound extends Monster implements ParryReactive {
	/** The client's pose: prowling, the lunge crouch, the leap, staggered, the howl. */
	public static final int STATE_IDLE = 0;
	public static final int STATE_CROUCH = 1;
	public static final int STATE_LEAP = 2;
	public static final int STATE_STAGGERED = 3;
	public static final int STATE_HOWL = 4;

	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(AshHound.class, EntityDataSerializers.INT);

	/** The sideways shove of a turned lunge. */
	private static final double TURN_PUSH = 0.35;

	private boolean setUp;
	private boolean deathHandled;
	private int staggerTicks;

	public AshHound(EntityType<? extends AshHound> type, Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	/** Default attributes; the balance replaces them on the first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		CreatureBalance b = new CreatureBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, b.houndHealth)
				.add(Attributes.MOVEMENT_SPEED, b.houndSpeed)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.ATTACK_DAMAGE, b.biteDamage);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new AshHoundPackGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(AshHound.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_IDLE);
	}

	/** {@code STATE_*}, for the client's pose. */
	public int state() {
		return this.entityData.get(DATA_STATE);
	}

	void setState(int state) {
		this.entityData.set(DATA_STATE, state);
	}

	public boolean isStaggered() {
		return staggerTicks > 0;
	}

	/** Whether a lunge is coming or under way (the crouch or the leap). */
	public boolean isLunging() {
		int state = state();
		return state == STATE_CROUCH || state == STATE_LEAP;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance();
		}

		if (staggerTicks > 0) {
			getNavigation().stop();

			if (--staggerTicks <= 0) {
				setState(STATE_IDLE);
			}
		}
	}

	private void applyBalance() {
		CreatureBalance b = CreatureBalance.get();
		setBase(Attributes.MOVEMENT_SPEED, b.houndSpeed);
		setBase(Attributes.ATTACK_DAMAGE, b.biteDamage);
		boolean full = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, b.houndHealth);

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

	// ---------------------------------------------------------------- interrupts

	/** A saber parried the hound's bite or lunge: it is turned aside and staggered. */
	@Override
	public void onParried(ServerPlayer defender, SaberCombat.Strike strike) {
		stagger(CreatureBalance.get().lungeStaggerTicks, defender.position());
	}

	/**
	 * An interrupt ({@code ForceReactions}: a Barrier pulse, a Push, a stun): a lunge in its crouch or
	 * in the air is turned aside and the hound staggered. Anything else only gets the usual slow.
	 */
	public void interrupt(@Nullable LivingEntity by) {
		if (!isLunging()) {
			return;
		}

		LivingEntity from = by != null ? by : getTarget();
		stagger(CreatureBalance.get().lungeStaggerTicks, from == null ? null : from.position());
	}

	/** Staggers the hound for {@code ticks}, turned away from {@code from} with a small shove. */
	public void stagger(int ticks, @Nullable Vec3 from) {
		staggerTicks = Math.max(staggerTicks, ticks);
		setState(STATE_STAGGERED);
		getNavigation().stop();

		if (from == null) {
			return;
		}

		Vec3 away = new Vec3(getX() - from.x, 0, getZ() - from.z);

		if (away.lengthSqr() < 1.0e-6) {
			return;
		}

		away = away.normalize();
		// Turned aside: thrown off to one side of the line it came in on.
		Vec3 side = new Vec3(-away.z, 0, away.x).scale(getRandom().nextBoolean() ? 1 : -1);
		Vec3 shove = away.add(side).normalize().scale(TURN_PUSH);
		setDeltaMovement(shove.x, Math.max(getDeltaMovement().y, 0.15), shove.z);
		this.hurtMarked = true;
		float yaw = (float) (Mth.atan2(side.z, side.x) * Mth.RAD_TO_DEG) - 90f;
		setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	// ---------------------------------------------------------------- sounds, death

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.ASH_HOUND_IDLE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.ASH_HOUND_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.ASH_HOUND_DEATH;
	}

	/** Corrupted trial hounds (W2's waves) drop nothing. */
	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return super.shouldDropLoot(level) && !entityTags().contains(Hollowhorn.CORRUPTED_TAG);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (deathHandled || !(level() instanceof ServerLevel)) {
			return;
		}

		deathHandled = true;
		ServerPlayer killer = FoesFeature.killer(this, source);

		if (killer != null) {
			QuestHooks.onBeastKilled(killer, this, "ash_hound");
		}
	}

	/** Natural packs come and go like any monster; hounds a story wave or a site placed stay. */
	@Override
	public boolean removeWhenFarAway(double distance) {
		return StoryTags.owner(this) == null && !entityTags().contains(Hollowhorn.CORRUPTED_TAG) && super.removeWhenFarAway(distance);
	}
}
