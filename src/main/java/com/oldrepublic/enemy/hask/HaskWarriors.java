package com.oldrepublic.enemy.hask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.hymn.FoeSpawns;
import com.oldrepublic.planet.HubSpawners;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.world.site.SpawnerGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Where Clan Hask warriors come from (spec MILESTONE_5.md 3.3.1, 6.3, 12.1): the spawner group
 * {@code hask_warriors} (hunts, contracts, the duel of honour; with an owner they fight beside the
 * owner's party, the clan's way) and the hub role {@code warrior} (the holdfast's posts: a hub guard
 * whose attitude follows clan standing, leashed to its post, never despawning, dropping nothing).
 */
public final class HaskWarriors {
	/** The spawner group id. */
	public static final String GROUP = "hask_warriors";
	/** The hub role id (the holdfast's {@code WARRIOR_1}-{@code 6} posts). */
	public static final String HUB_ROLE = "warrior";
	/** A group spawner whose data says no count spawns this many (the clan's way brings 3). */
	public static final int DEFAULT_SIZE = 3;

	/** Registers the group and the hub role. Called from {@code FoesM5Feature}. */
	public static void init() {
		SpawnerGroups.register(GROUP, new SpawnerGroups.Group() {
			@Override
			public int size(int count, BlockPos pos) {
				return count > 0 ? count : DEFAULT_SIZE;
			}

			@Override
			public List<LivingEntity> spawn(ServerLevel level, BlockPos at, int size, boolean bound, @Nullable UUID owner) {
				return new ArrayList<>(spawnGroup(level, at, size, owner));
			}
		});
		HubSpawners.register(HUB_ROLE, (level, at, planet, role) -> spawnHubGuard(level, at));
	}

	/** The kits of a group of {@code size}: a champion leads a group of three or more, then rifles and blades by turns (a rifle first). */
	public static List<HaskWarrior.Variant> group(int size) {
		List<HaskWarrior.Variant> kits = new ArrayList<>();
		int rest = size;

		if (size >= 3) {
			kits.add(HaskWarrior.Variant.CHAMPION);
			rest--;
		}

		for (int i = 0; i < rest; i++) {
			kits.add(i % 2 == 0 ? HaskWarrior.Variant.RIFLE : HaskWarrior.Variant.BLADE);
		}

		return kits;
	}

	/** Spawns a group of {@code size} ({@link #group}) around {@code at}; with an {@code owner} it is allied to the owner's party. */
	public static List<HaskWarrior> spawnGroup(ServerLevel level, BlockPos at, int size, @Nullable UUID owner) {
		List<HaskWarrior.Variant> kits = group(Math.max(0, size));
		List<HaskWarrior> out = new ArrayList<>(kits.size());

		for (int i = 0; i < kits.size(); i++) {
			HaskWarrior.Variant kit = kits.get(i);
			HaskWarrior warrior = FoeSpawns.spawn(level, HaskEntities.HASK_WARRIOR, at, i, kits.size(), owner, w -> w.setVariant(kit));

			if (warrior != null) {
				out.add(warrior);
			}
		}

		return out;
	}

	/** One warrior of {@code kit} at {@code at}, optionally owned (the duel of honour's champion); null when the level refused it. */
	public static @Nullable HaskWarrior spawn(ServerLevel level, BlockPos at, HaskWarrior.Variant kit, @Nullable UUID owner) {
		return FoeSpawns.spawn(level, HaskEntities.HASK_WARRIOR, at, 0, 1, owner, w -> w.setVariant(kit));
	}

	/** A post warrior for the holdfast: seeded by the post, so the same one comes back each restore. */
	public static @Nullable HaskWarrior spawnHubGuard(ServerLevel level, BlockPos at) {
		float roll = RandomSource.create(at.asLong()).nextFloat();
		HaskWarrior.Variant kit = roll < 0.5f ? HaskWarrior.Variant.RIFLE : roll < 0.8f ? HaskWarrior.Variant.BLADE : HaskWarrior.Variant.CHAMPION;
		return FoeSpawns.spawn(level, HaskEntities.HASK_WARRIOR, at, 0, 1, null, w -> {
			w.setVariant(kit);
			w.addTag(HubNpcs.HUB_TAG);
			w.setHomeTo(at, HaskBalance.get().hubLeash);
		});
	}

	private HaskWarriors() {
	}
}
