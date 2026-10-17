package com.oldrepublic.enemy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.story.StoryTags;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Spawning Crystal Poacher crews for other features (spec section 7.8): mission camps, the
 * Courier's Fall scavengers and Vasch's counter-hunt all go through here.
 */
public final class Poachers {
	/** Crew members stand this far around the spawn point. */
	private static final double CREW_RING = 1.5;

	/**
	 * Spawns a crew of {@code count} poachers around {@code at}, owned by {@code owner} when not
	 * null, and returns them. They are persistent, marked {@link StoryTags#CREW}, and carry the owner
	 * as a {@link StoryTags} owner tag. Callers tag, target or leash them further as they need. In
	 * Peaceful the level removes them again, like any monster.
	 */
	public static List<LivingEntity> spawnCrew(ServerLevel l, BlockPos at, int count, @Nullable UUID owner) {
		List<LivingEntity> crew = new ArrayList<>(Math.max(0, count));
		Vec3 centre = Vec3.atBottomCenterOf(at);

		for (int i = 0; i < count; i++) {
			CrystalPoacher poacher = EnemyEntities.CRYSTAL_POACHER.create(l, EntitySpawnReason.EVENT);

			if (poacher == null) {
				continue;
			}

			double angle = 2 * Math.PI * i / Math.max(1, count);
			double ring = count > 1 ? CREW_RING : 0;
			poacher.snapTo(centre.x + Math.cos(angle) * ring, centre.y, centre.z + Math.sin(angle) * ring, l.getRandom().nextFloat() * 360f, 0f);

			if (!l.noCollision(poacher, poacher.getBoundingBox())) {
				poacher.snapTo(centre.x, centre.y, centre.z, poacher.getYRot(), 0f);
			}

			poacher.finalizeSpawn(l, l.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			poacher.setPersistenceRequired();
			poacher.addTag(StoryTags.CREW);

			if (owner != null) {
				StoryTags.setOwner(poacher, owner);
			}

			if (l.addFreshEntity(poacher)) {
				crew.add(poacher);
			}
		}

		return crew;
	}

	private Poachers() {
	}
}
