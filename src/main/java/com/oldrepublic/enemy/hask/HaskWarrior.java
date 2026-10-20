package com.oldrepublic.enemy.hask;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.PoacherCoverGoal;
import com.oldrepublic.enemy.cartel.FactionFoe;
import com.oldrepublic.enemy.cartel.StandingRule;
import com.oldrepublic.enemy.cartel.StandingState;
import com.oldrepublic.enemy.cartel.StandingTargets;
import com.oldrepublic.enemy.hymn.FoeKills;
import com.oldrepublic.enemy.hymn.TellLog;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.equipment.GearItems;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.vendor.hub.HubNpcs;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Clan Hask warrior (spec MILESTONE_5.md 12.1): a Mandalorian of the Tyrvann clan in clan-iron
 * plate. It is the M4 hunter underneath (jets, wrist rocket, rifle bolts, saving), with its own fight
 * ({@link HaskCombatGoal}) in three kits by {@link Variant}: the rifle (bolts of 6, the wrist rocket,
 * jet hops), the blade (vibrosword strings and the jet-slam) and the champion (vibrosword and a
 * clan-iron shield that turns frontal bolts, with the shield bash).
 *
 * <p>Its attitude follows the player's clan standing ({@link StandingRule}); a group spawned with an
 * owner (the clan's way, 6.3) fights beside its owner's party whatever the standing, taking on what
 * threatens them. Hub warriors on the holdfast's posts never despawn and drop nothing. It never
 * yields (no ambush) and never spawns naturally.
 */
public class HaskWarrior extends MandalorianHunter implements FactionFoe {
	/** The three kits. Saved and synced by ordinal: append only. */
	public enum Variant {
		RIFLE,
		BLADE,
		CHAMPION;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Variant byOrdinal(int ordinal) {
			Variant[] values = values();
			return values[Mth.clamp(ordinal, 0, values.length - 1)];
		}

		/** A kit by name, or null. */
		public static @Nullable Variant byId(String id) {
			for (Variant v : values()) {
				if (v.id().equals(id)) {
					return v;
				}
			}

			return null;
		}
	}

	/** What the client poses: nothing, the jet-slam crouch, the shield drawn back. */
	public static final int ACTION_NONE = 0;
	public static final int ACTION_SLAM = 1;
	public static final int ACTION_BASH = 2;

	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(HaskWarrior.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(HaskWarrior.class, EntityDataSerializers.INT);
	/** How far an allied warrior looks for what threatens its owner's party. */
	private static final double ASSIST_RANGE = 24;
	/** The owner's own blow marks a foe for this long. */
	private static final int ASSIST_MEMORY_TICKS = 100;

	private final StandingState standing = new StandingState();
	private final TellLog tells = new TellLog();
	private boolean haskSetUp;
	private boolean variantSet;
	private boolean haskDeathHandled;
	private boolean haskInterrupt;
	private int clientActionTicks;

	public HaskWarrior(EntityType<? extends HaskWarrior> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	/** Default attributes; the balance replaces them on the warrior's first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		HaskBalance b = new HaskBalance();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, b.rifleHealth)
				.add(Attributes.ARMOR, b.rifleArmor)
				.add(Attributes.MOVEMENT_SPEED, b.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance)
				.add(Attributes.FOLLOW_RANGE, b.followRange)
				.add(Attributes.ATTACK_DAMAGE, b.bladeDamage);
	}

	/** Its own fight and its own target picking (standing, not the hunter's "every player"). */
	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PoacherCoverGoal(this, HaskWarrior::coverSettings, this::mayTakeCover, () -> charging(false)));
		this.goalSelector.addGoal(2, new HaskCombatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	private static PoacherCoverGoal.Settings coverSettings() {
		// The M4 hunter's cover numbers: a Hask rifle fights like a hunter.
		return new PoacherCoverGoal.Settings(0.5f, 1.2f, 40, 80, 240);
	}

	/** Only the rifle takes cover; blades and champions press in. */
	@Override
	protected boolean mayTakeCover() {
		return variant() == Variant.RIFLE && super.mayTakeCover() && action() == ACTION_NONE;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, Variant.RIFLE.ordinal());
		builder.define(DATA_ACTION, ACTION_NONE);
	}

	// ---------------------------------------------------------------- state

	public Variant variant() {
		return Variant.byOrdinal(this.entityData.get(DATA_VARIANT));
	}

	/** Sets the kit (its weapon and numbers). Server side. */
	public void setVariant(Variant variant) {
		this.entityData.set(DATA_VARIANT, variant.ordinal());
		variantSet = true;
		equip();

		if (haskSetUp) {
			applyBalance();
		}
	}

	/** {@code ACTION_*}, for the client's pose. */
	public int action() {
		return this.entityData.get(DATA_ACTION);
	}

	void setAction(int action) {
		this.entityData.set(DATA_ACTION, action);
	}

	/** Client only: ticks the current action pose has shown. */
	public int clientActionTicks() {
		return clientActionTicks;
	}

	/** The rifle's aim tell (the hunter's synced flag), for the goal in this package. */
	void charging(boolean charging) {
		setCharging(charging);
	}

	/** A gadget's tell pose (the hunter's synced gadget), for the goal in this package. */
	void gadgetPose(int gadget) {
		setGadget(gadget);
	}

	/** Its tells and heavy hits (the tests read it). */
	public TellLog tellLog() {
		return tells;
	}

	/** Every interrupt (a stagger, a Push, a shock) also drops the Hask tell in progress and a jet-slam in flight. */
	@Override
	public void interrupt(String cause) {
		super.interrupt(cause);
		haskInterrupt = true;
	}

	/** Knocked out of a hop: a jet-slam in flight never lands. */
	@Override
	public void onPushedInAir() {
		if (isJetting()) {
			haskInterrupt = true;
		}

		super.onPushedInAir();
	}

	/** True once after an interrupt. */
	boolean consumeHaskInterrupt() {
		boolean value = haskInterrupt;
		haskInterrupt = false;
		return value;
	}

	// ---------------------------------------------------------------- FactionFoe, Aligned

	@Override
	public Faction faction() {
		return Faction.CLAN_HASK;
	}

	@Override
	public Attitude ownedAttitude() {
		return Attitude.ALLIED;
	}

	@Override
	public StandingState standing() {
		return standing;
	}

	@Override
	public boolean isHubGuard() {
		return entityTags().contains(HubNpcs.HUB_TAG);
	}

	@Override
	public double partyRange() {
		return HaskBalance.get().partyRange;
	}

	@Override
	public Allegiance allegiance() {
		return Allegiance.CLAN;
	}

	/** The clan's numbers the target checks read. */
	static StandingTargets.Settings targetSettings() {
		HaskBalance b = HaskBalance.get();
		return new StandingTargets.Settings(b.followRange, b.waryRange, b.warnRepeatTicks, b.engageTicks, b.provokeTicks, b.safeRadius,
				ModSounds.HASK_BARK, "entity.oldrepublic.hask_warrior.warn");
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			clientActionTicks = action() != ACTION_NONE ? clientActionTicks + 1 : 0;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (!haskSetUp) {
			haskSetUp = true;

			if (!variantSet) {
				equip();
			}
		}

		super.customServerAiStep(level);

		if (isRemoved() || isYielded()) {
			return;
		}

		if (tickCount % StandingTargets.INTERVAL == 0) {
			StandingTargets.update(this, this, level, targetSettings());
			assist(level);
		}
	}

	/**
	 * An allied warrior (spawned with an owner) takes on what threatens its owner's party: the foe the
	 * owner just struck, else the nearest mob that targets one of them.
	 */
	private void assist(ServerLevel level) {
		UUID owner = owner();
		LivingEntity current = getTarget();

		if (owner == null || current != null && current.isAlive()) {
			return;
		}

		ServerPlayer leader = level.getServer().getPlayerList().getPlayer(owner);

		if (leader == null || leader.level() != level || StandingRule.attitude(this, this, leader) != Attitude.ALLIED) {
			return;
		}

		LivingEntity struck = leader.getLastHurtMob();

		if (struck != null && struck.isAlive() && struck != this && !Allegiances.friendly(this, struck) && !(struck instanceof Player)
				&& leader.tickCount - leader.getLastHurtMobTimestamp() <= ASSIST_MEMORY_TICKS && struck.distanceToSqr(this) <= ASSIST_RANGE * ASSIST_RANGE) {
			setTarget(struck);
			return;
		}

		double range = partyRange();
		Mob best = null;
		double bestDistance = ASSIST_RANGE * ASSIST_RANGE;

		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(blockPosition()).inflate(ASSIST_RANGE), m -> m != this && m.isAlive())) {
			if (!(mob.getTarget() instanceof Player victim) || !StandingRule.inParty(this, owner, victim, range) || Allegiances.friendly(this, mob)) {
				continue;
			}

			double d = mob.distanceToSqr(this);

			if (d < bestDistance) {
				best = mob;
				bestDistance = d;
			}
		}

		if (best != null) {
			setTarget(best);
		}
	}

	/** The balance numbers of its kit. */
	@Override
	protected void applyBalance() {
		HaskBalance b = HaskBalance.get();

		switch (variant()) {
			case CHAMPION -> applyAttributes(b.championHealth, b.championArmor, b.speed, b.knockbackResistance, b.followRange);
			case BLADE -> applyAttributes(b.bladeHealth, b.bladeArmor, b.speed, b.knockbackResistance, b.followRange);
			default -> applyAttributes(b.rifleHealth, b.rifleArmor, b.speed, b.knockbackResistance, b.followRange);
		}
	}

	/** The rifle is part of the model; the blade and the champion carry a vibrosword. Never dropped. */
	private void equip() {
		ItemStack weapon = variant() == Variant.RIFLE ? ItemStack.EMPTY : new ItemStack(GearItems.VIBROSWORD);

		if (!ItemStack.isSameItem(getMainHandItem(), weapon)) {
			setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}

		setDropChance(EquipmentSlot.MAINHAND, 0f);
		setCanPickUpLoot(false);
	}

	/** Whether its body fits standing at {@code spot} (a jet-slam's landing). */
	boolean fitsAt(Vec3 spot) {
		return level().noCollision(this, makeBoundingBox(spot));
	}

	// ---------------------------------------------------------------- the shield

	/**
	 * Whether the champion's clan-iron shield covers a hit coming from {@code from}: within
	 * {@code shieldArcDegrees} of its facing, either side, and never while it bashes or is staggered
	 * out of a hop.
	 */
	public boolean shieldCovers(Vec3 from) {
		if (variant() != Variant.CHAMPION || isJetting() || action() == ACTION_BASH) {
			return false;
		}

		Vec3 to = from.subtract(position()).multiply(1, 0, 1);

		if (to.lengthSqr() < 1.0e-4) {
			return false;
		}

		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 facing = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		double cos = Math.cos(Math.toRadians(HaskBalance.get().shieldArcDegrees));
		return to.normalize().dot(facing) >= cos;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getDirectEntity() instanceof BlasterBolt bolt && shieldCovers(bolt.position())) {
			// The bolt spends itself on the clan iron.
			Vec3 at = position().add(bolt.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.45)).add(0, getBbHeight() * 0.55, 0);
			EffectEvents.toTracking(this, HaskEffects.EVENT, at, null, getId(), 1f, HaskEffects.SHIELD_BLOCK);
			level.playSound(null, at.x, at.y, at.z, ModSounds.HASK_SHIELD_BLOCK, getSoundSource(), 1f, 0.9f + getRandom().nextFloat() * 0.2f);
			StandingTargets.onHurtBy(this, this, source.getEntity() instanceof LivingEntity living ? living : null, targetSettings());
			return false;
		}

		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && isAlive()) {
			StandingTargets.onHurtBy(this, this, source.getEntity() instanceof LivingEntity living ? living : null, targetSettings());
		}

		return hurt;
	}

	// ---------------------------------------------------------------- sounds, death, drops

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.HASK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.HASK_DEATH;
	}

	/** Hub warriors never drop anything (the M4 hub rule); otherwise the hunter's anti-farm rule. */
	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return super.shouldDropLoot(level) && !isHubGuard();
	}

	/** A piece of clan iron on a {@code clanIronChance} roll when a player brought it down (16.4). */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);

		if (recentlyHit && getRandom().nextFloat() < HaskBalance.get().clanIronChance) {
			spawnAtLocation(level, M5Items.CLAN_IRON);
		}
	}

	@Override
	public void die(DamageSource source) {
		if (!haskDeathHandled && level() instanceof ServerLevel) {
			setAction(ACTION_NONE);
		}

		super.die(source);
	}

	/** The kill is a Clan Hask foe's (12.1, 7), never a hunter's: no hunter schematic roll, no bounty. */
	@Override
	protected void onKilled(ServerPlayer killer) {
		if (haskDeathHandled) {
			return;
		}

		haskDeathHandled = true;
		FoeKills.report(killer, this, HaskEntities.ID, Faction.CLAN_HASK, Attitudes.hostileSnapshot(this, killer));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);

		if (!variantSet) {
			setVariant(randomVariant(getRandom()));
		}

		return result;
	}

	/** Two rifles in five, two blades, one champion. */
	static Variant randomVariant(RandomSource random) {
		float roll = random.nextFloat();
		return roll < 0.4f ? Variant.RIFLE : roll < 0.8f ? Variant.BLADE : Variant.CHAMPION;
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		// The kit's weapon is set with the variant.
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("HaskVariant", variant().ordinal());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_VARIANT, Variant.byOrdinal(input.getIntOr("HaskVariant", Variant.RIFLE.ordinal())).ordinal());
		variantSet = true;
	}
}
