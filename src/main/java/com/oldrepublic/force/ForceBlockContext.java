package com.oldrepublic.force;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One Force act on a reactive block, as the power that found it describes it.
 *
 * @param caster    the player using the power
 * @param level     the caster's level
 * @param pos       the block
 * @param state     the block's state when the act was dispatched
 * @param act       push, pull or lift
 * @param direction unit vector of the act (away from the caster for a push, toward it for a pull)
 * @param strength  the power's strength (its knockback, or 1 for powers without one)
 * @param abilityId the power's id, for example {@code force_push}
 */
public record ForceBlockContext(ServerPlayer caster, ServerLevel level, BlockPos pos, BlockState state, ForceAct act, Vec3 direction, float strength,
		String abilityId) {
}
