package com.oldrepublic.vendor.hub;

import java.util.EnumSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.mission.PlanetContracts;
import com.oldrepublic.saber.duel.DuelistCombatGoal;
import com.oldrepublic.saber.duel.DuelistProfiles;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.SaberDuelist;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.duel.SparResult;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A hub person (spec MILESTONE_4.md 3.5, 7.8): a host, an apprentice or acolyte, a Proving guard
 * or a vendor. Persistent, leashed to the hub spawner that made it, and untouchable by players and
 * mobs (only {@code /kill} and the void get through): the M3 terminal rule, Peaceful-safe. The
 * exceptions are the two jobs it can be given:
 * <ul>
 * <li>a <b>spar</b> partner (apprentices and acolytes): it fights only its spar partner, through
 * the saber feature's {@link Spar} rules, which never let anyone die;</li>
 * <li>an <b>escort</b> (planet contracts, 8.7): it follows its owner to the contract's site, can be
 * hurt by mobs on the way (an escort contract fails when it dies), never by players, and is not
 * saved (a restart respawns it beside the owner).</li>
 * </ul>
 * An <b>echo</b> (Zarvos in his Sepulchre, 8.4) stands still, takes no damage, is not saved and
 * is broken by the story ({@link HubNpcs#breakEcho}). Talking to it goes through {@link HubNpcs#onUsed}.
 */
public class HubNpc extends PathfinderMob implements SaberDuelist {
	private static final EntityDataAccessor<String> DATA_VARIANT = SynchedEntityData.defineId(HubNpc.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_GUARD = SynchedEntityData.defineId(HubNpc.class, EntityDataSerializers.INT);
	/** Escorts keep this close to their owner, and stop following at this distance. */
	private static final double ESCORT_FOLLOW = 3.5;
	private static final double ESCORT_STOP = 2.0;

	private final HubRole role;
	private @Nullable BlockPos anchor;
	private int leash = 6;
	private @Nullable UUID escortOwner;
	private boolean echo;
	private boolean setUp;
	private long lastBark = Long.MIN_VALUE;

	public HubNpc(EntityType<? extends HubNpc> type, Level level, HubRole role) {
		super(type, level);
		this.role = role;
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 24)
				.add(Attributes.ATTACK_DAMAGE, 3)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new SparOnlyCombatGoal(this));
		this.goalSelector.addGoal(2, new FollowEscortOwnerGoal(this));
		this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6f));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.8));
		this.goalSelector.addGoal(5, new StrollGoal(this));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, "");
		builder.define(DATA_GUARD, GuardState.READY.ordinal());
	}

	// ---------------------------------------------------------------- identity

	public HubRole role() {
		return role;
	}

	/** Who this is: {@code ansa}, {@code kaddor}, {@code zarvos}, {@code apprentice_1}, a vendor's stock id, ... */
	public String variant() {
		return this.entityData.get(DATA_VARIANT);
	}

	/** Sets the variant and the name shown when looked at ({@code npc.oldrepublic.<variant>.name}). */
	public void setVariant(String variant) {
		this.entityData.set(DATA_VARIANT, variant);
		setCustomName(Component.translatable(HubNpcs.nameKey(role, variant)));
	}

	/** The hub spawner position this person stays near. */
	public @Nullable BlockPos anchor() {
		return anchor;
	}

	public void setAnchor(BlockPos anchor, int leash) {
		this.anchor = anchor.immutable();
		this.leash = leash;
		setHomeTo(this.anchor, leash);
	}

	// ---------------------------------------------------------------- escort and echo

	public @Nullable UUID escortOwner() {
		return escortOwner;
	}

	/** Starts (or, with null, ends) following {@code owner} as a contract escort. */
	public void setEscortOwner(@Nullable UUID owner) {
		this.escortOwner = owner;

		if (owner != null) {
			clearHome();
		} else if (anchor != null) {
			setHomeTo(anchor, leash);
		}
	}

	public boolean isEscort() {
		return escortOwner != null;
	}

	public boolean isEcho() {
		return echo;
	}

	/** Makes this an echo: still, untouchable, not saved. */
	public void setEcho(boolean echo) {
		this.echo = echo;
		setNoAi(echo);
	}

	// ---------------------------------------------------------------- SaberDuelist

	@Override
	public GuardState guardState() {
		GuardState[] values = GuardState.values();
		int index = this.entityData.get(DATA_GUARD);
		return values[Math.max(0, Math.min(values.length - 1, index))];
	}

	@Override
	public void setGuardState(GuardState state) {
		this.entityData.set(DATA_GUARD, state.ordinal());
	}

	@Override
	public ItemStack duelSaber() {
		return getMainHandItem();
	}

	@Override
	public void onSparEnd(ServerPlayer player, SparResult result) {
		setTarget(null);
		setGuardState(GuardState.READY);
	}

	/** The duel profile: an acolyte fights like the M3 acolytes, an apprentice like a training partner. */
	String profileId() {
		return role == HubRole.ACOLYTE ? DuelistProfiles.ACOLYTE : DuelistProfiles.TRAINING;
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			setPersistenceRequired();
			setCanPickUpLoot(false);
			HubNpcs.equip(this);

			if (anchor != null && escortOwner == null) {
				setHomeTo(anchor, leash);
			}
		}

		// A partner whose spar ended without our callback (a restart, a lost session) stands down.
		if (getTarget() != null && !Spar.isPartner(this)) {
			setTarget(null);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return !echo && escortOwner == null && super.shouldBeSaved();
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer serverPlayer && !echo) {
			HubNpcs.onUsed(serverPlayer, this);
		}

		return InteractionResult.SUCCESS;
	}

	/**
	 * Untouchable, except: damage that bypasses invulnerability; the spar partner's blows (the spar
	 * rules decide the rest); and, for an escort, blows from anything but a player. A player's blow
	 * earns a bark now and then.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}

		if (echo) {
			return false;
		}

		if (source.getEntity() instanceof ServerPlayer player) {
			if (Spar.partner(player) == this) {
				return super.hurtServer(level, source, amount);
			}

			long now = level.getGameTime();

			if (now - lastBark >= 60) {
				lastBark = now;
				HubNpcs.onStruck(player, this);
			}

			return false;
		}

		return escortOwner != null && super.hurtServer(level, source, amount);
	}

	/** An escort's death fails its contract (8.7). */
	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (escortOwner != null && level() instanceof ServerLevel server) {
			PlanetContracts.escortDied(server, escortOwner);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Variant", variant());
		output.storeNullable("Anchor", BlockPos.CODEC, anchor);
		output.putInt("Leash", leash);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_VARIANT, input.getStringOr("Variant", ""));
		anchor = input.read("Anchor", BlockPos.CODEC).orElse(null);
		leash = input.getIntOr("Leash", 6);
	}

	// ---------------------------------------------------------------- goals

	/** The saber duel goal, but only against the current spar partner. */
	private static final class SparOnlyCombatGoal extends DuelistCombatGoal {
		private final HubNpc npc;

		SparOnlyCombatGoal(HubNpc npc) {
			super(npc, () -> DuelistProfiles.get(npc.profileId()));
			this.npc = npc;
		}

		@Override
		protected boolean mayFight(LivingEntity target) {
			return Spar.isPartner(npc);
		}
	}

	/** An escort walks after its owner. */
	private static final class FollowEscortOwnerGoal extends Goal {
		private final HubNpc npc;
		private int repath;

		FollowEscortOwnerGoal(HubNpc npc) {
			this.npc = npc;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private @Nullable Player owner() {
			return npc.escortOwner == null ? null : npc.level().getPlayerByUUID(npc.escortOwner);
		}

		@Override
		public boolean canUse() {
			Player owner = owner();
			return owner != null && owner.isAlive() && npc.distanceToSqr(owner) > ESCORT_FOLLOW * ESCORT_FOLLOW;
		}

		@Override
		public boolean canContinueToUse() {
			Player owner = owner();
			return owner != null && owner.isAlive() && npc.distanceToSqr(owner) > ESCORT_STOP * ESCORT_STOP;
		}

		@Override
		public void tick() {
			Player owner = owner();

			if (owner == null) {
				return;
			}

			npc.getLookControl().setLookAt(owner, 10f, npc.getMaxHeadXRot());

			if (--repath <= 0) {
				repath = 10;
				npc.getNavigation().moveTo(owner, 1.0);
			}
		}

		@Override
		public void stop() {
			npc.getNavigation().stop();
		}
	}

	/** Apprentices, acolytes and guards wander a little around their spot; hosts and vendors keep to it. */
	private static final class StrollGoal extends WaterAvoidingRandomStrollGoal {
		private final HubNpc npc;

		StrollGoal(HubNpc npc) {
			super(npc, 0.5);
			this.npc = npc;
		}

		@Override
		public boolean canUse() {
			boolean wanders = npc.role == HubRole.APPRENTICE || npc.role == HubRole.ACOLYTE || npc.role == HubRole.GUARD;
			return wanders && npc.escortOwner == null && super.canUse();
		}
	}
}
