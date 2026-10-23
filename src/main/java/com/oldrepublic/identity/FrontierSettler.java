package com.oldrepublic.identity;

import java.util.EnumSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.registry.ModSounds;

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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A Frontier Settler: a civilian homesteader in a worn work tunic. Free settlers wander near home
 * (and flee on sight from players the Frontier distrusts); captives kneel bound at a post until the
 * deciding player resolves their group through the captives dialogue; freed settlers run for home
 * and are gone once far enough. Killing or hurting one is a dark act; poachers never target them.
 */
public class FrontierSettler extends PathfinderMob {
	/** What the settler is doing. Stored by ordinal: append only. */
	public enum State {
		FREE,
		CAPTIVE,
		FLEEING;

		static State byOrdinal(int ordinal) {
			State[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FREE;
		}
	}

	/** Skins: {@code textures/entity/frontier_settler_<n>.png} (and {@code _bound} while captive). */
	public static final int VARIANTS = 3;

	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(FrontierSettler.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(FrontierSettler.class, EntityDataSerializers.INT);
	private static final int HOME_RADIUS = 16;
	private static final double FLEE_SPEED = 1.35;
	private static final double DISTRUST_RADIUS = 12;

	private @Nullable UUID owner;
	private String groupId = "";
	private @Nullable BlockPos home;
	private @Nullable Vec3 fleeOrigin;
	private int fleeTicks;
	private boolean setUp;

	public FrontierSettler(EntityType<? extends FrontierSettler> type, Level level) {
		super(type, level);
	}

	/** Default attributes. Balance values replace them on each settler's first server tick. */
	public static AttributeSupplier.Builder createAttributes() {
		IdentityBalance defaults = new IdentityBalance();
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, defaults.settlerHealth)
				.add(Attributes.MOVEMENT_SPEED, defaults.settlerSpeed * 0.5);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new FleeHomeGoal());
		this.goalSelector.addGoal(2, new AvoidDistrustedGoal());
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.8) {
			@Override
			public boolean canUse() {
				return state() == State.FREE && super.canUse();
			}
		});
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6) {
			@Override
			public boolean canUse() {
				return state() == State.FREE && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, State.FREE.ordinal());
		builder.define(DATA_VARIANT, 0);
	}

	// ---------------------------------------------------------------- state

	public State state() {
		return State.byOrdinal(this.entityData.get(DATA_STATE));
	}

	public int variant() {
		return Mth.clamp(this.entityData.get(DATA_VARIANT), 0, VARIANTS - 1);
	}

	public void setVariant(int variant) {
		this.entityData.set(DATA_VARIANT, Math.floorMod(variant, VARIANTS));
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public String groupId() {
		return groupId;
	}

	/** Binds the settler at its current position as a captive of {@code owner}'s encounter. */
	public void makeCaptive(UUID owner, String groupId, @Nullable BlockPos home) {
		this.owner = owner;
		this.groupId = groupId;
		this.home = home;
		this.entityData.set(DATA_STATE, State.CAPTIVE.ordinal());
		setPersistenceRequired();
		getNavigation().stop();
	}

	/** A free settler living around {@code home}. */
	public void makeFree(BlockPos home) {
		this.home = home;
		this.entityData.set(DATA_STATE, State.FREE.ordinal());
		setHomeTo(home, HOME_RADIUS);
	}

	/** Cut loose: runs toward home and is gone once far enough or after a while. */
	public void startFleeing() {
		this.entityData.set(DATA_STATE, State.FLEEING.ordinal());
		this.fleeOrigin = position();
		this.fleeTicks = 0;
		clearHome();
	}

	/** Where the settler's wrists are bound (rope VFX anchor). */
	public Vec3 wristPosition() {
		return position().add(0, state() == State.CAPTIVE ? 0.45 : 1.0, 0);
	}

	// ---------------------------------------------------------------- behaviour

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			applyBalance();
		}

		switch (state()) {
			case CAPTIVE -> getNavigation().stop();
			case FLEEING -> {
				IdentityBalance b = Identity.balance();
				fleeTicks++;

				if (fleeTicks > b.settlerFleeTicks || (fleeOrigin != null && position().distanceTo(fleeOrigin) > b.settlerFleeDistance)) {
					discard();
				}
			}
			case FREE -> {
			}
		}
	}

	private void applyBalance() {
		IdentityBalance b = Identity.balance();
		boolean fullHealth = getHealth() >= getMaxHealth();
		setBase(Attributes.MAX_HEALTH, b.settlerHealth);
		setBase(Attributes.MOVEMENT_SPEED, b.settlerSpeed * 0.5);

		if (fullHealth || getHealth() > getMaxHealth()) {
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
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (state() == State.CAPTIVE && source.getEntity() instanceof Player && source.is(ContextualActs.FORCE_HARM)) {
			amount *= Identity.balance().captiveAreaDamageMult;
		}

		return super.hurtServer(level, source, amount);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		State state = state();

		if (state == State.FLEEING) {
			return InteractionResult.PASS;
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}

		if (state == State.CAPTIVE) {
			if (owner != null && !owner.equals(player.getUUID())) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.oldrepublic.identity.captive_not_yours"));
			} else if (Settlers.isResolved(serverPlayer, groupId)) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.oldrepublic.identity.captive_waits"));
			} else {
				Dialogues.open(serverPlayer, Settlers.CAPTIVES_DIALOGUE, this);
			}

			return InteractionResult.SUCCESS;
		}

		ReputationTier tier = Identity.tier(serverPlayer, Faction.FRONTIER);
		String key = switch (tier) {
			case HOSTILE, DISTRUSTED -> "message.oldrepublic.identity.settler_refuses";
			case NEUTRAL -> "message.oldrepublic.identity.settler_greets_neutral";
			case TRUSTED, HONOURED -> "message.oldrepublic.identity.settler_greets_trusted";
		};
		serverPlayer.sendOverlayMessage(Component.translatable(key));
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isPushable() {
		return state() != State.CAPTIVE && super.isPushable();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.SETTLER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.SETTLER_DEATH;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("SettlerState", state().ordinal());
		output.putInt("SettlerVariant", variant());
		output.putString("SettlerGroup", groupId);
		output.storeNullable("SettlerOwner", UUIDUtil.CODEC, owner);
		output.storeNullable("SettlerHome", BlockPos.CODEC, home);
		// API-CHECK: Vec3.CODEC (not used elsewhere in the mod yet).
		output.storeNullable("SettlerFleeOrigin", Vec3.CODEC, fleeOrigin);
		output.putInt("SettlerFleeTicks", fleeTicks);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_STATE, State.byOrdinal(input.getIntOr("SettlerState", 0)).ordinal());
		setVariant(input.getIntOr("SettlerVariant", 0));
		this.groupId = input.getStringOr("SettlerGroup", "");
		this.owner = input.read("SettlerOwner", UUIDUtil.CODEC).orElse(null);
		this.home = input.read("SettlerHome", BlockPos.CODEC).orElse(null);
		this.fleeOrigin = input.read("SettlerFleeOrigin", Vec3.CODEC).orElse(null);
		this.fleeTicks = input.getIntOr("SettlerFleeTicks", 0);
	}

	// ---------------------------------------------------------------- goals

	/** Fleeing: run toward home (or away from the nearest player when there is none). */
	private final class FleeHomeGoal extends Goal {
		private int repath;

		FleeHomeGoal() {
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return state() == State.FLEEING;
		}

		@Override
		public void tick() {
			if (--repath > 0) {
				return;
			}

			repath = 20;
			Vec3 target;

			if (home != null) {
				target = DefaultRandomPos.getPosTowards(FrontierSettler.this, 16, 7, Vec3.atBottomCenterOf(home), Math.PI / 2);
			} else {
				Player nearest = level().getNearestPlayer(FrontierSettler.this, 32);
				target = nearest == null ? DefaultRandomPos.getPos(FrontierSettler.this, 16, 7)
						: DefaultRandomPos.getPosAway(FrontierSettler.this, 16, 7, nearest.position());
			}

			if (target != null) {
				getNavigation().moveTo(target.x, target.y, target.z, FLEE_SPEED);
			}
		}
	}

	/** Free settlers keep away from players the Frontier distrusts. */
	private final class AvoidDistrustedGoal extends Goal {
		private @Nullable Player threat;

		AvoidDistrustedGoal() {
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (state() != State.FREE || tickCount % 10 != 0) {
				return false;
			}

			threat = null;

			for (Player player : level().players()) {
				if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(FrontierSettler.this) < DISTRUST_RADIUS * DISTRUST_RADIUS
						&& Identity.tier(player, Faction.FRONTIER).ordinal() <= ReputationTier.DISTRUSTED.ordinal() && hasLineOfSight(player)) {
					threat = player;
					break;
				}
			}

			if (threat == null) {
				return false;
			}

			Vec3 away = DefaultRandomPos.getPosAway(FrontierSettler.this, 16, 7, threat.position());
			return away != null && getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
		}

		@Override
		public boolean canContinueToUse() {
			return state() == State.FREE && !getNavigation().isDone();
		}
	}
}
