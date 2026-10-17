package com.oldrepublic.world.block;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.world.WorldBalance;
import com.oldrepublic.world.WorldBlocks;
import com.oldrepublic.world.WorldDamage;
import com.oldrepublic.world.WorldEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server state machine of an unstable cluster: IDLE (counting how long a player lingers in
 * range) → CHARGING (the tell) → discharge → COOLDOWN. The state is transient on purpose: after a
 * reload the cluster simply starts idle, and a {@code charging=true} state left over in the
 * world is resumed as a fresh charge so the tell is never skipped.
 */
public class UnstableClusterBlockEntity extends BlockEntity {
	private static final int SCAN_INTERVAL = 4;

	private enum Phase { IDLE, CHARGING, COOLDOWN }

	private Phase phase = Phase.IDLE;
	private int linger;
	private int timer;

	public UnstableClusterBlockEntity(BlockPos pos, BlockState state) {
		super(WorldBlocks.UNSTABLE_CLUSTER_ENTITY, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, UnstableClusterBlockEntity cluster) {
		if (level instanceof ServerLevel server) {
			cluster.tick(server, pos, state);
		}
	}

	private void tick(ServerLevel level, BlockPos pos, BlockState state) {
		WorldBalance b = WorldBalance.get();
		boolean chargingState = state.getValue(UnstableCrystalClusterBlock.CHARGING);

		switch (phase) {
			case COOLDOWN -> {
				if (chargingState) {
					setCharging(level, pos, state, false);
				}

				if (--timer <= 0) {
					phase = Phase.IDLE;
					linger = 0;
				}
			}
			case CHARGING -> {
				if (++timer >= b.unstableChargeTicks) {
					discharge(level, pos, state, b);
				}
			}
			case IDLE -> {
				if (chargingState) {
					// Left over from before a reload: replay the whole tell.
					startCharge(level, pos, state, b);
					return;
				}

				if (level.getGameTime() % SCAN_INTERVAL != 0) {
					return;
				}

				linger = playerInRange(level, pos, b.unstableRadius) ? linger + SCAN_INTERVAL : 0;

				if (linger >= b.unstableLingerTicks) {
					startCharge(level, pos, state, b);
				}
			}
		}
	}

	private static boolean playerInRange(ServerLevel level, BlockPos pos, double radius) {
		Vec3 center = Vec3.atCenterOf(pos);

		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && !player.isCreative()
					&& player.getBoundingBox().getCenter().distanceToSqr(center) <= radius * radius) {
				return true;
			}
		}

		return false;
	}

	private void startCharge(ServerLevel level, BlockPos pos, BlockState state, WorldBalance b) {
		phase = Phase.CHARGING;
		timer = 0;
		setCharging(level, pos, state, true);
		Direction facing = state.getValue(CrystalFormationBlock.FACING);
		EffectEvents.around(level, WorldEffects.CRYSTAL_UNSTABLE_CHARGE, Vec3.atCenterOf(pos), CrystalFormationBlock.facingVector(facing),
				-1, b.unstableChargeTicks / 20f, 0);
	}

	private void discharge(ServerLevel level, BlockPos pos, BlockState state, WorldBalance b) {
		Vec3 center = Vec3.atCenterOf(pos);
		double radius = b.unstableRadius;
		DamageSource source = level.damageSources().source(WorldDamage.CRYSTAL_DISCHARGE);
		AABB box = new AABB(center, center).inflate(radius);

		for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive())) {
			Vec3 body = entity.getBoundingBox().getCenter();

			if (body.distanceToSqr(center) > radius * radius) {
				continue;
			}

			entity.hurtServer(level, source, b.unstableDamage);
			Vec3 away = body.subtract(center);
			Vec3 flat = new Vec3(away.x, 0, away.z);
			Vec3 push = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0.25, 0) : flat.normalize().scale(0.45).add(0, 0.2, 0);
			entity.setDeltaMovement(entity.getDeltaMovement().add(push));
			entity.hurtMarked = true;
		}

		EffectEvents.around(level, WorldEffects.CRYSTAL_DISCHARGE, center,
				CrystalFormationBlock.facingVector(state.getValue(CrystalFormationBlock.FACING)), -1, (float) radius, 0);
		phase = Phase.COOLDOWN;
		timer = Math.max(1, b.unstableCooldownTicks);
		linger = 0;
		setCharging(level, pos, level.getBlockState(pos), false);
	}

	private static void setCharging(ServerLevel level, BlockPos pos, BlockState state, boolean charging) {
		if (state.getBlock() instanceof UnstableCrystalClusterBlock && state.getValue(UnstableCrystalClusterBlock.CHARGING) != charging) {
			level.setBlock(pos, state.setValue(UnstableCrystalClusterBlock.CHARGING, charging), Block.UPDATE_ALL);
		}
	}
}
