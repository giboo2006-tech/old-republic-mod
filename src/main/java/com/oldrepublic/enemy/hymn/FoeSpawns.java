package com.oldrepublic.enemy.hymn;

import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.story.StoryTags;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Places the Milestone 5 foes for the spawner groups, the hubs and the bosses (the M1 {@code Poachers}
 * crew pattern): member {@code index} of {@code count} stands on a small ring around the spot (on the
 * spot itself if the ring cell is blocked), is set up by the caller before {@code finalizeSpawn} (its
 * kit), never despawns, and carries the owner's tag when it has an owner.
 */
public final class FoeSpawns {
	/** Members of a group stand this far from its centre. */
	public static final double RING = 1.5;

	/**
	 * Spawns one {@code type} for member {@code index} of {@code count} around {@code at}; null when the
	 * type refused or the level did not take it.
	 *
	 * @param setup runs before {@code finalizeSpawn} (a variant, a tag)
	 */
	public static <T extends Mob> @Nullable T spawn(ServerLevel level, EntityType<T> type, BlockPos at, int index, int count, @Nullable UUID owner,
			Consumer<T> setup) {
		T mob = type.create(level, EntitySpawnReason.EVENT);

		if (mob == null) {
			return null;
		}

		Vec3 centre = Vec3.atBottomCenterOf(at);
		double angle = 2 * Math.PI * index / Math.max(1, count);
		double ring = count > 1 ? RING : 0;
		mob.snapTo(centre.x + Math.cos(angle) * ring, centre.y, centre.z + Math.sin(angle) * ring, level.getRandom().nextFloat() * 360f, 0f);

		if (!level.noCollision(mob, mob.getBoundingBox())) {
			mob.snapTo(centre.x, centre.y, centre.z, mob.getYRot(), 0f);
		}

		setup.accept(mob);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		mob.setPersistenceRequired();

		if (owner != null) {
			StoryTags.setOwner(mob, owner);
		}

		return level.addFreshEntity(mob) ? mob : null;
	}

	private FoeSpawns() {
	}
}
