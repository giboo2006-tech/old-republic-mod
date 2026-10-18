package com.oldrepublic.enemy.mandalorian;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ForceReactiveEntity;
import com.oldrepublic.force.barrier.BarrierReactive;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.SaberComponents;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Kaast's thrown net (spec MILESTONE_4.md 10.5, phase 2). In flight it is a weighted throw; a player
 * it hits is caught ({@link Netting}): rooted for the throw's root ticks, with Speed, Barrier and
 * jumping ended. The net then stays on them, and breaks when its time is up, when the caught player
 * casts Force Push, when it takes {@code netSaberHits} hits from a lit saber (the caught player's
 * own or a friend's), or at once when a friend uses it (a teammate cuts them free). A Force Barrier
 * makes a thrown net fall short. Never saved.
 */
public class HunterNet extends ThrowableProjectile implements BarrierReactive, ForceReactiveEntity {
	/** The caught entity's id, or -1 in flight (the client wraps the cords around it). */
	private static final EntityDataAccessor<Integer> DATA_CAUGHT = SynchedEntityData.defineId(HunterNet.class, EntityDataSerializers.INT);
	private static final double GRAVITY = 0.03;
	/** A net that has not caught anyone is gone after this long. */
	private static final int FLIGHT_LIFETIME = 60;
	/** A net that fell short lies on the ground this long. */
	private static final int SPENT_LIFETIME = 30;

	private int rootTicks = 60;
	private int saberHits = 3;
	private int hitsTaken;
	private int life;
	private boolean spent;
	private @Nullable ServerPlayer caught;

	public HunterNet(EntityType<? extends HunterNet> type, Level level) {
		super(type, level);
	}

	/** Throws a net from {@code thrower}'s hand toward {@code target}, rooting for {@code rootTicks}, breaking after {@code saberHits} saber hits. */
	public static HunterNet throwAt(ServerLevel level, LivingEntity thrower, LivingEntity target, int rootTicks, int saberHits) {
		HunterNet net = new HunterNet(HunterEntities.HUNTER_NET, level);
		Vec3 from = thrower.getEyePosition().subtract(0, 0.3, 0);
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
		double distance = from.distanceTo(to);
		int ticks = Math.max(6, (int) Math.ceil(distance / 1.1));
		// A lob that arrives in `ticks`: straight line plus the fall the gravity takes back.
		Vec3 velocity = to.subtract(from).scale(1.0 / ticks).add(0, GRAVITY * ticks * 0.5, 0);
		net.setPos(from);
		net.setOwner(thrower);
		net.rootTicks = rootTicks;
		net.saberHits = saberHits;
		net.setDeltaMovement(velocity);
		level.addFreshEntity(net);
		return net;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_CAUGHT, -1);
	}

	/** The caught entity's id on the client, or -1 while it flies. */
	public int caughtId() {
		return this.entityData.get(DATA_CAUGHT);
	}

	public boolean hasCaught() {
		return caughtId() >= 0;
	}

	@Override
	protected double getDefaultGravity() {
		return GRAVITY;
	}

	@Override
	public void tick() {
		life++;

		if (level().isClientSide()) {
			if (!hasCaught()) {
				super.tick();
			}

			return;
		}

		if (caught != null) {
			if (!caught.isAlive() || caught.isRemoved() || caught.level() != level() || !Netting.isNetBy(caught, this)) {
				discard();
				return;
			}

			setPos(caught.getX(), caught.getY(), caught.getZ());
			setDeltaMovement(Vec3.ZERO);
			return;
		}

		super.tick();

		if (!isRemoved() && life > (spent ? SPENT_LIFETIME + FLIGHT_LIFETIME : FLIGHT_LIFETIME)) {
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		return !spent && caught == null && target instanceof LivingEntity && target != getOwner() && super.canHitEntity(target);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(level() instanceof ServerLevel level) || spent || caught != null) {
			return;
		}

		if (result.getEntity() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
			caught = player;
			this.entityData.set(DATA_CAUGHT, player.getId());
			setNoGravity(true);
			Netting.snare(player, this, rootTicks);
			EffectEvents.around(level, HunterEffects.ATTACK, player.position().add(0, 1, 0), null, player.getId(), rootTicks, HunterEffects.ATTACK_NET);
		} else {
			fallShort();
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);

		if (!level().isClientSide() && caught == null) {
			spent = true;
			setDeltaMovement(Vec3.ZERO);
			setNoGravity(true);
		}
	}

	/** The net drops out of the air, harmless. */
	private void fallShort() {
		spent = true;
		setDeltaMovement(getDeltaMovement().multiply(0.1, 0, 0.1).add(0, -0.3, 0));
	}

	/** A Force Barrier: a thrown net falls short. */
	@Override
	public void onBarrier(ServerPlayer caster, Vec3 surface, Vec3 outward) {
		if (caught == null) {
			fallShort();
		}
	}

	/** A Push swats a flying net aside (the caught player's own Push is handled by {@link Netting}). */
	@Override
	public boolean onForcePush(ServerPlayer caster, Vec3 dir, float strength) {
		if (caught == null) {
			fallShort();
			return true;
		}

		return false;
	}

	/** A caught net can be struck and used; a flying one cannot be caught by the crosshair. */
	@Override
	public boolean isPickable() {
		return hasCaught();
	}

	/** Lit-saber hits cut the net: {@code saberHits} of them free the player. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (caught == null || !(source.getEntity() instanceof Player attacker) || !SaberComponents.isUsable(attacker.getMainHandItem())) {
			return false;
		}

		if (++hitsTaken >= saberHits) {
			Netting.free(caught, Netting.FREED_SABER);
		}

		return true;
	}

	/** A friend using the net cuts the caught player free at once. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (caught == null || player == caught) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer) {
			Netting.free(caught, Netting.FREED_TEAMMATE);
		}

		return InteractionResult.SUCCESS;
	}
}
