package com.oldrepublic.mentor;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.duel.DuelistCombatGoal;
import com.oldrepublic.saber.duel.DuelistProfile;
import com.oldrepublic.saber.duel.DuelistProfiles;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.SaberDuelist;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A Chapter Two mentor: Master Ilyra Saelen or Overseer Teyvar Sorn. Owned by one player, persistent,
 * leashed to a camp anchor, and untouchable outside a spar (only {@code /kill} and the void get
 * through). Holds a lightsaber built with the saber feature's assembly helpers: lit at the Crossing,
 * in demonstrations and in spars, otherwise retracted. Talking to it is routed to the story feature.
 *
 * <p>Fights only inside a spar, through the saber feature's {@link DuelistCombatGoal} with the
 * profile the story sets ({@code jedi_master} or {@code training}).
 */
public abstract class MentorEntity extends PathfinderMob implements SaberDuelist {
	private static final EntityDataAccessor<Integer> DATA_GUARD = SynchedEntityData.defineId(MentorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_MEDITATING = SynchedEntityData.defineId(MentorEntity.class, EntityDataSerializers.BOOLEAN);
	private static final int DUPLICATE_CHECK_INTERVAL = 40;

	private @Nullable UUID owner;
	private @Nullable BlockPos anchor;
	/** Walking away from the Crossing: discarded once far enough or after a timeout. */
	private boolean departing;
	private @Nullable Vec3 departFrom;
	private int departTicks;
	/** The player this mentor is sparring with right now (sessions are in memory only). */
	private @Nullable UUID sparPartner;
	private String profileId = "jedi_master";
	private long lastAttackLine = Long.MIN_VALUE;
	private boolean setUp;

	protected MentorEntity(EntityType<? extends MentorEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 60)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 24)
				.add(Attributes.ATTACK_DAMAGE, 5)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
	}

	public abstract MentorRole role();

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new DuelistCombatGoal(this, () -> DuelistProfiles.get(profileId)));
		this.goalSelector.addGoal(2, new MentorGoals.Depart(this));
		this.goalSelector.addGoal(3, new MentorGoals.FaceOwner(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.9));
		this.goalSelector.addGoal(5, new MentorGoals.Meditate(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.55));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_GUARD, GuardState.READY.ordinal());
		builder.define(DATA_MEDITATING, false);
	}

	// --- SaberDuelist

	@Override
	public GuardState guardState() {
		GuardState[] values = GuardState.values();
		int index = this.entityData.get(DATA_GUARD);
		return values[Math.max(0, Math.min(values.length - 1, index))];
	}

	/** Set by the duel goal; synced for the guard pose. */
	@Override
	public void setGuardState(GuardState state) {
		this.entityData.set(DATA_GUARD, state.ordinal());
	}

	@Override
	public ItemStack duelSaber() {
		return getMainHandItem();
	}

	/** The duel profile the story chose for the current spar. */
	public DuelistProfile profile() {
		return DuelistProfiles.get(profileId);
	}

	public void setProfile(String id) {
		this.profileId = id;
	}

	// --- Ownership, anchor and story state

	public @Nullable UUID owner() {
		return owner;
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
	}

	public boolean isOwnedBy(Player player) {
		return player.getUUID().equals(owner);
	}

	/** The owner if they are online, in any level. */
	public @Nullable ServerPlayer ownerPlayer() {
		if (owner == null || !(level() instanceof ServerLevel serverLevel)) {
			return null;
		}

		return serverLevel.getServer().getPlayerList().getPlayer(owner);
	}

	public @Nullable BlockPos anchor() {
		return anchor;
	}

	/** Moves the camp anchor and the leash with it. */
	public void setAnchor(BlockPos anchor, int leash) {
		this.anchor = anchor;
		setHomeTo(anchor, leash);
	}

	public boolean isMeditating() {
		return this.entityData.get(DATA_MEDITATING);
	}

	void setMeditating(boolean meditating) {
		this.entityData.set(DATA_MEDITATING, meditating);
	}

	public boolean isDeparting() {
		return departing;
	}

	/** Leaves the scene: walks away from {@code from} and is removed out there. Not reversible. */
	public void depart(Vec3 from) {
		departing = true;
		departFrom = from;
		departTicks = 0;
		clearHome();
		setMeditating(false);
	}

	@Nullable Vec3 departFrom() {
		return departFrom;
	}

	public boolean isSparring() {
		return sparPartner != null;
	}

	public boolean isSparringWith(Player player) {
		return player.getUUID().equals(sparPartner);
	}

	/** Starts or ends sparring: the partner may then strike this mentor (the spar keeps it safe). */
	public void setSparPartner(@Nullable ServerPlayer partner) {
		this.sparPartner = partner == null ? null : partner.getUUID();
		setTarget(partner);
		setMeditating(false);
	}

	// --- Saber

	/** Ignites or retracts the held saber (with the ignition sound), building it first if missing. */
	public void setBladeLit(boolean lit) {
		ItemStack saber = ensureSaber();

		if (SaberComponents.isIgnited(saber) != lit) {
			SaberToggle.setIgnited(saber, lit, level().getGameTime());
			setItemSlot(EquipmentSlot.MAINHAND, saber);
			SaberToggle.playToggleSound(level(), this, lit);
		}
	}

	private ItemStack ensureSaber() {
		ItemStack held = getMainHandItem();

		if (held.is(SaberItems.LIGHTSABER)) {
			return held;
		}

		MentorRole role = role();
		CrystalAttunement crystal = new CrystalAttunement(new UUID(0L, 0L), "", role.blade().rgb());
		ItemStack saber = SaberItems.buildSaber(crystal, role.blade(), role.emitter(), role.grip(), role.pommel(), SaberToggle.balance());
		setItemSlot(EquipmentSlot.MAINHAND, saber);
		setDropChance(EquipmentSlot.MAINHAND, 0f);
		return saber;
	}

	// --- Lifecycle

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!setUp) {
			setUp = true;
			setPersistenceRequired();
			setCanPickUpLoot(false);
			ensureSaber();

			if (anchor != null && !departing) {
				setHomeTo(anchor, StoryMentors.leash(this));
			}
		}

		if (departing) {
			departTicks++;

			if (departFrom == null || position().distanceTo(departFrom) > StoryMentors.departDistance() || departTicks > StoryMentors.departTimeout()) {
				discard();
			}

			return;
		}

		if (tickCount % DUPLICATE_CHECK_INTERVAL == 0 && !StoryMentors.stillWanted(this)) {
			discard();
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer serverPlayer && !departing) {
			StoryMentors.onUsed(serverPlayer, this);
		}

		return InteractionResult.SUCCESS;
	}

	/**
	 * Untouchable outside a spar with its partner; only damage that bypasses invulnerability
	 * ({@code /kill}, the void) gets through. A player's blow earns a line and costs a little trust.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}

		if (source.getEntity() instanceof ServerPlayer player) {
			if (isSparringWith(player)) {
				return super.hurtServer(level, source, amount);
			}

			long now = level.getGameTime();

			if (now - lastAttackLine >= StoryMentors.attackCooldown()) {
				lastAttackLine = now;
				StoryMentors.onAttacked(player, this);
			}
		}

		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("Owner", UUIDUtil.CODEC, owner);
		output.storeNullable("Anchor", BlockPos.CODEC, anchor);
		output.putBoolean("Departing", departing);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		anchor = input.read("Anchor", BlockPos.CODEC).orElse(null);

		// A mentor saved mid-departure has already said goodbye.
		if (input.getBooleanOr("Departing", false)) {
			departing = true;
			departTicks = Integer.MAX_VALUE / 2;
		}
	}
}
