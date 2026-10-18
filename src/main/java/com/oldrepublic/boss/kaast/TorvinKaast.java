package com.oldrepublic.boss.kaast;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.mandalorian.HunterBalance;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.barrier.BarrierPiercing;
import com.oldrepublic.force.choke.ChokedTargets;
import com.oldrepublic.force.choke.ForceChoke;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.ParryReactive;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.throwing.SaberThrow;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Torvin Kaast, the hunter (spec MILESTONE_4.md 10.5): a Mandalorian hunter with the hunter's rifle,
 * jet pack and wrist gadgets, fought in his hunting camp in three phases ({@link KaastCombatGoal})
 * under a {@link KaastSession}. Nothing kills him: his health stops at {@code yieldAt}, where he
 * kneels, takes his helmet off and yields. Only the listed interrupts touch him ({@link KaastRules}):
 * a bolt parried back (stagger), a Push or a returned heavy bolt on a nest (he falls and is staggered),
 * a heavy blow while he kneels at a mine, and a parried axe blow. In phase 3 his vibro-axe blocks
 * sabers from the front with its own guard, and the Barrier does not soften it. Never saved: the
 * session respawns him.
 */
public class TorvinKaast extends MandalorianHunter implements BarrierPiercing, ParryReactive {
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(TorvinKaast.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_AXE = SynchedEntityData.defineId(TorvinKaast.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_KNEEL = SynchedEntityData.defineId(TorvinKaast.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_KNELT = SynchedEntityData.defineId(TorvinKaast.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_STAGGERED = SynchedEntityData.defineId(TorvinKaast.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_ECHO = SynchedEntityData.defineId(TorvinKaast.class, EntityDataSerializers.BOOLEAN);
	/** The axe is drawn back for the combo's third blow (the client's pose). */
	private static final EntityDataAccessor<Boolean> DATA_WINDUP = SynchedEntityData.defineId(TorvinKaast.class, EntityDataSerializers.BOOLEAN);

	/** The axe guards this wide an arc in front of him (degrees, either side). */
	private static final double GUARD_ARC = 70;
	/** Without a session (a stray summon) he leaves after this long. */
	private static final int STRAY_TICKS = 20;

	private @Nullable KaastSession session;
	private float guard;
	private long guardBackAt = Long.MIN_VALUE;
	private int staggerTicks;
	/** Knocked off a nest: Lift may raise him {@code liftFallBlocks} until the stagger ends. */
	private boolean fallStagger;
	/** The nest he stands on, or -1. */
	private int nest = -1;
	private boolean chokeUsed;
	private @Nullable ServerPlayer choker;
	/** His scaled maximum health (above the attribute's cap, hits are scaled down instead); 0 until set. */
	private double scaledMax;
	/** The resolution's end: the only time he may die. */
	private boolean ending;
	/** The combo blow being struck now (0 to 2), or -1: what a parry reacts to. */
	private int striking = -1;

	public TorvinKaast(EntityType<? extends TorvinKaast> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		markFightSpawn();
	}

	/** Default attributes; the session sets the scaled health. */
	public static AttributeSupplier.Builder createAttributes() {
		KaastBalance b = new KaastBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, b.maxHealth)
				.add(Attributes.ARMOR, b.armor)
				.add(Attributes.MOVEMENT_SPEED, b.movementSpeed)
				.add(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.ATTACK_DAMAGE, 8.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new KaastCombatGoal(this));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, 1);
		builder.define(DATA_AXE, false);
		builder.define(DATA_KNEEL, false);
		builder.define(DATA_KNELT, false);
		builder.define(DATA_STAGGERED, false);
		builder.define(DATA_ECHO, false);
		builder.define(DATA_WINDUP, false);
	}

	// ---------------------------------------------------------------- state

	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	public boolean isAxeDrawn() {
		return this.entityData.get(DATA_AXE);
	}

	/** Kneeling to set a mine. */
	public boolean isKneeling() {
		return this.entityData.get(DATA_KNEEL);
	}

	/** Knelt in his yield, helmet off. */
	public boolean isKnelt() {
		return this.entityData.get(DATA_KNELT);
	}

	public boolean isStaggered() {
		return this.entityData.get(DATA_STAGGERED);
	}

	/** The daily rematch's hunter's echo. */
	public boolean isEcho() {
		return this.entityData.get(DATA_ECHO);
	}

	public boolean isWindingUp() {
		return this.entityData.get(DATA_WINDUP);
	}

	public @Nullable KaastSession session() {
		return session;
	}

	void attach(KaastSession session, boolean echo) {
		this.session = session;
		this.entityData.set(DATA_ECHO, echo);
	}

	/** The axe's guard left (phase 3). */
	public float guard() {
		return guard;
	}

	public int nest() {
		return nest;
	}

	public boolean isFallStaggered() {
		return fallStagger && staggerTicks > 0;
	}

	public boolean chokeUsed() {
		return chokeUsed;
	}

	/** His scaled maximum health: the max health attribute, or more when the scaling passes its 1024 cap. */
	public double scaledMaxHealth() {
		return scaledMax > 0 ? scaledMax : getMaxHealth();
	}

	void setNest(int nest) {
		this.nest = nest;
	}

	void setKneeling(boolean kneeling) {
		this.entityData.set(DATA_KNEEL, kneeling);
	}

	void setWindingUp(boolean windingUp) {
		this.entityData.set(DATA_WINDUP, windingUp);
	}

	void setStriking(int blow) {
		this.striking = blow;
	}

	/** The gadget whose tell shows (the hunters' {@code GADGET_*}). */
	void showGadget(int gadget) {
		setGadget(gadget);
	}

	/** The rifle's aim tell. */
	void aim(boolean aiming) {
		setCharging(aiming);
	}

	// ---------------------------------------------------------------- phases

	/** A phase begins (the session's call): the phase 3 helmet cracks and the axe comes out. */
	void enterPhase(ServerLevel level, int phase) {
		this.entityData.set(DATA_PHASE, phase);
		stopFlame();
		aim(false);
		showGadget(GADGET_NONE);
		nest = -1;

		if (phase == 3) {
			this.entityData.set(DATA_AXE, true);
			guard = KaastBalance.get().guard;
			EffectEvents.around(level, KaastEffects.EVENT, getEyePosition(), null, getId(), 0f, KaastEffects.HELMET);
			level.playSound(null, getX(), getY(), getZ(), ModSounds.BOSS_KAAST_HELMET_CRACK, getSoundSource(), 1.2f, 1f);
			level.playSound(null, getX(), getY(), getZ(), ModSounds.BOSS_KAAST_AXE_DRAW, getSoundSource(), 1.2f, 1f);
		}
	}

	/** Kneels, takes the helmet off and waits (the yield). */
	void kneelInYield(ServerLevel level) {
		this.entityData.set(DATA_KNELT, true);
		this.entityData.set(DATA_KNEEL, false);
		this.entityData.set(DATA_WINDUP, false);
		stopFlame();
		aim(false);
		showGadget(GADGET_NONE);
		setTarget(null);
		getNavigation().stop();
		setNoGravity(false);
		EffectEvents.around(level, KaastEffects.EVENT, position(), null, getId(), 0f, KaastEffects.YIELD);
		level.playSound(null, getX(), getY(), getZ(), ModSounds.BOSS_KAAST_YIELD, getSoundSource(), 1f, 1f);
		EffectEvents.around(level, KaastEffects.EVENT, getEyePosition(), null, getId(), 1f, KaastEffects.HELMET);
	}

	/** The resolution's end ("End him"): the one death he is allowed. */
	void end(ServerLevel level) {
		ending = true;
		kill(level);
	}

	// ---------------------------------------------------------------- stagger and interrupts

	/** One of the listed interrupts staggers him for {@code ticks} (full length: his own rule). */
	void stagger(int ticks) {
		staggerTicks = Math.max(staggerTicks, ticks);
		this.entityData.set(DATA_STAGGERED, true);
		this.entityData.set(DATA_KNEEL, false);
		this.entityData.set(DATA_WINDUP, false);
		stopFlame();
		aim(false);
		showGadget(GADGET_NONE);
		getNavigation().stop();
	}

	/** Generic interrupts (a stagger, a stun, a shock) do not stop him. */
	@Override
	public void interrupt(String cause) {
	}

	/** A hit in the air is not one of his interrupts; a Push on a nest is ({@link #onPushed}). */
	@Override
	public void onPushedInAir() {
	}

	/** A Push reached him: on a nest in phase 1 it knocks him off. */
	void onPushed() {
		if (KaastRules.interrupts(KaastRules.Interrupt.PUSH_OFF_NEST, phase(), nest >= 0 || isHopping(), false)) {
			knockOffNest();
		}
	}

	/** Off the nest: he falls and is staggered {@code nestFallStagger}, then hops back. */
	private void knockOffNest() {
		if (isHopping()) {
			// Caught mid-hop: the jet cuts out and he drops (the hunter's own rule).
			super.onPushedInAir();
		}

		nest = -1;
		fallStagger = true;
		setNoGravity(false);
		stagger(KaastBalance.get().nestFallStagger);
	}

	@Override
	public void onParried(ServerPlayer defender, SaberCombat.Strike strike) {
		if (striking < 0 || !KaastRules.interrupts(KaastRules.Interrupt.PARRIED_COMBO, phase(), false, false)) {
			return;
		}

		KaastBalance b = KaastBalance.get();

		if (striking >= 2) {
			guard = Math.max(0f, guard - b.parryGuardDamage);
			stagger(b.comboParryStagger);
		} else {
			stagger(b.comboBreakStagger);
		}

		striking = -1;
	}

	@Override
	public boolean piercesBarrier() {
		return isAxeDrawn();
	}

	// ---------------------------------------------------------------- ticking

	@Override
	protected void applyBalance() {
		KaastBalance b = KaastBalance.get();
		double max = session == null ? b.maxHealth : session.maxHealth();
		scaledMax = max;
		applyAttributes(KaastRules.attributeHealth(max), b.armor, b.movementSpeed, b.knockbackResistance, 48.0);
	}

	/**
	 * Rescales his maximum health, keeping the fraction he has left (a participant joined or left). The
	 * attribute stops at {@link KaastRules#HEALTH_CAP}; past it the scaling lives in {@link KaastRules#damageScale}.
	 */
	void rescale(double maxHealth) {
		scaledMax = maxHealth;
		AttributeInstance instance = getAttribute(Attributes.MAX_HEALTH);
		double attribute = KaastRules.attributeHealth(maxHealth);

		if (instance == null || Math.abs(instance.getBaseValue() - attribute) < 1.0e-3) {
			return;
		}

		float fraction = getMaxHealth() <= 0f ? 1f : getHealth() / getMaxHealth();
		instance.setBaseValue(attribute);
		setHealth((float) attribute * fraction);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (session == null) {
			if (tickCount > STRAY_TICKS) {
				discard();
			}

			return;
		}

		if (staggerTicks > 0 && --staggerTicks <= 0) {
			this.entityData.set(DATA_STAGGERED, false);
			fallStagger = false;
		}

		if (isAxeDrawn() && guard <= 0f && level.getGameTime() >= guardBackAt) {
			guard = KaastBalance.get().guard;
		}
	}

	/** The Choke is watched here, not in {@link #customServerAiStep}: a hold pauses his AI (MobChokeMixin). */
	@Override
	public void tick() {
		super.tick();

		if (session != null && !isRemoved() && level() instanceof ServerLevel server) {
			tickChoke(server);
		}
	}

	/** Held by a Choke (once, phase 3): when it lets go he burns it off with his flamer, staggering the caster. */
	private void tickChoke(ServerLevel level) {
		if (ChokedTargets.isChoked(this)) {
			ServerPlayer caster = ForceChoke.chokerOf(this);

			if (caster != null) {
				choker = caster;
				chokeUsed = true;
			}

			return;
		}

		ServerPlayer caster = choker;
		choker = null;

		if (caster == null || !caster.isAlive() || caster.level() != level) {
			return;
		}

		KaastBalance b = KaastBalance.get();
		HunterBalance h = HunterBalance.get();
		Vec3 toward = caster.position().subtract(position());
		EffectEvents.around(level, KaastEffects.EVENT, wristPosition(), toward.lengthSqr() < 1.0e-4 ? null : toward.normalize(), getId(), 1f,
				KaastEffects.BURN_OFF);
		startFlame(level, h.flamerDamageInterval * 2, h.flamerDamage, h.flamerDamageInterval, h.flamerConeLength, h.flamerConeDegrees, h.flamerFireSeconds);
		ControlEffects.stagger(this, caster, b.chokeBurnStagger, 2);
	}

	/** The {@code chokeTicks} hook: how long a Choke may hold him now (the hold itself marks his one choke used). */
	int allowChoke(int requested) {
		return KaastRules.chokeTicks(phase(), chokeUsed, requested, KaastBalance.get());
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!ending && (isKnelt() || session == null || session.isTransitioning())) {
			return false;
		}

		KaastBalance b = KaastBalance.get();
		Entity direct = source.getDirectEntity();
		float dealt = amount;

		if (direct instanceof BlasterBolt bolt && bolt.parried()) {
			if (bolt.heavy() && phase() == 1 && nest >= 0) {
				knockOffNest();
			} else if (KaastRules.interrupts(KaastRules.Interrupt.PARRIED_BOLT, phase(), nest >= 0, isKneeling())) {
				stagger(b.parriedBoltStagger);
			}
		}

		if (blocksWithAxe(source)) {
			guard -= dealt * b.blockDrainPerDamage;
			Vec3 at = direct != null ? direct.getBoundingBox().getCenter() : getEyePosition();
			EffectEvents.around(level, KaastEffects.EVENT, at, null, getId(), 1f, KaastEffects.CLASH);
			dealt *= b.blockDamageFraction;

			if (guard <= 0f) {
				guard = 0f;
				guardBackAt = level.getGameTime() + b.guardRegenTicks;
				stagger(b.guardBreakTicks);
			}
		}

		if (isKneeling() && dealt >= b.kneelHeavyDamage && KaastRules.interrupts(KaastRules.Interrupt.KNEEL_HEAVY, phase(), false, true)) {
			stagger(b.kneelStagger);
		}

		// Past the attribute cap the scaled health is kept by scaling each hit down (before armour).
		boolean hurt = super.hurtServer(level, source, dealt * KaastRules.damageScale(scaledMaxHealth()));

		if (hurt && session != null && !ending) {
			session.onKaastHurt(this);
		}

		return hurt;
	}

	/** A saber blow from in front while the axe is out, his guard holds and he is not staggered or winding up. */
	private boolean blocksWithAxe(DamageSource source) {
		if (!isAxeDrawn() || guard <= 0f || isStaggered() || isWindingUp()) {
			return false;
		}

		boolean saber = source.is(SaberThrow.DAMAGE)
				|| source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && SaberComponents.isUsable(attacker.getMainHandItem());
		Vec3 from = source.getSourcePosition();

		if (!saber || from == null) {
			return false;
		}

		Vec3 to = from.subtract(position()).multiply(1, 0, 1);
		Vec3 facing = Vec3.directionFromRotation(0f, this.yBodyRot);
		return to.lengthSqr() < 1.0e-4 || to.normalize().dot(facing) >= Math.cos(Math.toRadians(GUARD_ARC));
	}

	/** His health never falls below the yield floor while the fight runs: nothing kills him. */
	@Override
	public void setHealth(float health) {
		if (session != null && !ending) {
			health = Math.max(health, KaastRules.floor(getMaxHealth(), KaastBalance.get()));
		}

		super.setHealth(health);
	}

	/** Kaast's end is W2's ending, not a hunter kill. */
	@Override
	protected void onKilled(ServerPlayer killer) {
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.BOSS_KAAST_HURT;
	}

	/** His owner may talk to him while he kneels (the yield dialogue, again). */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (isKnelt() && session != null && !isEcho() && player instanceof ServerPlayer serverPlayer && session.owner().equals(player.getUUID())) {
			QuestHooks.onBossYielded(serverPlayer, KaastFight.BOSS_ID);
			return InteractionResult.SUCCESS;
		}

		return super.mobInteract(player, hand);
	}

	@Override
	public boolean canYield() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return !isKnelt() && super.isPushable();
	}
}
