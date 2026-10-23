package com.oldrepublic.enemy.mandalorian;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.droid.Droids;
import com.oldrepublic.enemy.droid.SecurityDroid;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.story.StoryTags;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Mandalorian hunter encounters (spec MILESTONE_4.md 8.5, 8.7, 10.1): ambushes, bounties and Kaast's call. */
public final class Hunters {
	/**
	 * Drops {@code count} hunters in around {@code at} (jet pack arrival), owned by {@code owner}
	 * when given, with a security droid escort when {@code withDroid}.
	 *
	 * <p>Two or more hunters, or any escorted group, form a story ambush ({@link HunterRules#ambushOf}):
	 * they share a group, and the last of them yields. A lone unescorted hunter is a bounty hunter. Each
	 * hunter starts {@code dropHeight} blocks up and descends on its jets, already hunting the owner.
	 *
	 * @return what spawned, the droid included (empty when nothing could)
	 */
	public static List<LivingEntity> spawnAmbush(ServerLevel level, BlockPos at, int count, @Nullable UUID owner, boolean withDroid) {
		List<LivingEntity> spawned = new ArrayList<>();
		UUID group = UUID.randomUUID();
		int ambush = HunterRules.ambushOf(count, withDroid);
		HunterBalance b = HunterBalance.get();
		LivingEntity target = owner == null ? null : level.getPlayerByUUID(owner);

		for (int i = 0; i < count; i++) {
			BlockPos ground = spot(level, at, i, count, b.ambushSpread);

			if (ground == null) {
				continue;
			}

			MandalorianHunter hunter = spawnOne(level, ground, owner, b.dropHeight);

			if (hunter == null) {
				continue;
			}

			hunter.joinAmbush(group, ambush);

			if (target != null) {
				hunter.setTarget(target);
			}

			spawned.add(hunter);
		}

		if (withDroid && !spawned.isEmpty()) {
			BlockPos ground = spot(level, at, count, count + 1, b.ambushSpread);
			LivingEntity droid = ground == null ? null : Droids.spawnSecurity(level, ground, owner, false);

			if (droid instanceof SecurityDroid escort) {
				escort.joinEscort(group);

				if (target != null) {
					escort.setTarget(target);
				}

				spawned.add(escort);
			}
		}

		return spawned;
	}

	/**
	 * Hunters Kaast calls into his fight (phase 2): dropped in like an ambush, owned by the camp's
	 * owner, but never yielding and never dropping anything.
	 */
	public static List<MandalorianHunter> spawnForFight(ServerLevel level, BlockPos at, int count, UUID owner) {
		List<MandalorianHunter> spawned = new ArrayList<>();
		HunterBalance b = HunterBalance.get();

		for (int i = 0; i < count; i++) {
			BlockPos ground = spot(level, at, i, count, b.ambushSpread);
			MandalorianHunter hunter = ground == null ? null : spawnOne(level, ground, owner, b.dropHeight / 2);

			if (hunter != null) {
				hunter.markFightSpawn();
				spawned.add(hunter);
			}
		}

		return spawned;
	}

	/** One hunter above {@code ground}, descending on its jets; null when it could not be added. */
	private static @Nullable MandalorianHunter spawnOne(ServerLevel level, BlockPos ground, @Nullable UUID owner, int height) {
		MandalorianHunter hunter = HunterEntities.MANDALORIAN_HUNTER.create(level, EntitySpawnReason.EVENT);

		if (hunter == null) {
			return null;
		}

		// Never inside a ceiling: as high as the open air above the landing spot allows.
		int up = 0;

		while (up < height && level.getBlockState(ground.above(up + 2)).isAir()) {
			up++;
		}

		hunter.snapTo(ground.getX() + 0.5, ground.getY() + up, ground.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
		hunter.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), EntitySpawnReason.EVENT, null);

		if (owner != null) {
			StoryTags.setOwner(hunter, owner);
		}

		if (up > 1) {
			hunter.startDescent();
		}

		if (!level.addFreshEntity(hunter)) {
			return null;
		}

		EffectEvents.around(level, HunterEffects.ATTACK, hunter.position(), new Vec3(0, -1, 0), hunter.getId(), up, HunterEffects.ATTACK_JET);
		return hunter;
	}

	/** The ground under the {@code index}-th of {@code of} spots on a ring {@code spread} blocks around {@code at}; null when unloaded. */
	private static @Nullable BlockPos spot(ServerLevel level, BlockPos at, int index, int of, double spread) {
		double angle = index * (Math.PI * 2 / Math.max(1, of)) + level.getRandom().nextDouble() * 0.6;
		double radius = of <= 1 ? 0.0 : spread;
		int x = Mth.floor(at.getX() + 0.5 + Math.cos(angle) * radius);
		int z = Mth.floor(at.getZ() + 0.5 + Math.sin(angle) * radius);

		if (!level.hasChunkAt(new BlockPos(x, at.getY(), z))) {
			return null;
		}

		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new BlockPos(x, Math.abs(y - at.getY()) > 12 ? at.getY() : y, z);
	}

	private Hunters() {
	}
}
