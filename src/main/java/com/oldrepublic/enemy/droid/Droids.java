package com.oldrepublic.enemy.droid;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.vendor.hub.HubNpcs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;

/** Mk IV security droids (spec MILESTONE_4.md 10.2). */
public final class Droids {
	/** How far the Proving's patrol droid strolls from its spawner. */
	private static final int PATROL_RADIUS = 12;

	/**
	 * Spawns a security droid at {@code at}, owned by {@code owner} when given; an Imperial guard
	 * droid when {@code imperialGuard} (the Proving), else a hunter's escort.
	 *
	 * @return the droid, or null when it could not spawn
	 */
	public static @Nullable LivingEntity spawnSecurity(ServerLevel level, BlockPos at, @Nullable UUID owner, boolean imperialGuard) {
		if (!level.hasChunkAt(at)) {
			return null;
		}

		SecurityDroid droid = DroidEntities.SECURITY_DROID.create(level, EntitySpawnReason.EVENT);

		if (droid == null) {
			return null;
		}

		droid.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
		droid.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		droid.setImperialGuard(imperialGuard);

		if (owner != null) {
			StoryTags.setOwner(droid, owner);
		}

		return level.addFreshEntity(droid) ? droid : null;
	}

	/**
	 * The Proving's patrol droid (the hub spawner role {@code droid}, spec MILESTONE_4.md 3.5): an
	 * Imperial guard droid, so never hostile to a Sith ({@code DroidReactions}), persistent, tagged as
	 * hub staff like the hub's people, and kept to its beat around the spawner.
	 *
	 * @return the droid, or null when it could not spawn
	 */
	public static @Nullable LivingEntity spawnHubPatrol(ServerLevel level, BlockPos at) {
		LivingEntity droid = spawnSecurity(level, at, null, true);

		if (droid instanceof SecurityDroid patrol) {
			patrol.addTag(HubNpcs.HUB_TAG);
			// API-CHECK: Mob#setHomeTo(BlockPos, int) is verified (enemy.txt); whether the home is saved is not (the spawner respawns it daily).
			patrol.setHomeTo(at, PATROL_RADIUS);
		}

		return droid;
	}

	private Droids() {
	}
}
