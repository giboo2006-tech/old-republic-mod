package com.oldrepublic.enemy.cartel;

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
 * Where cartel enforcers come from (spec MILESTONE_5.md 3.2.1, 12.1): the spawner group
 * {@code cartel_enforcers} (kajidic posts, the counting-house, contracts; with an owner they stand
 * against the owner's party) and the hub role {@code enforcer} (Saltgate's wall posts: a hub guard
 * whose attitude follows kajidic standing, leashed to its post, never despawning, dropping nothing).
 */
public final class CartelEnforcers {
	/** The spawner group id. */
	public static final String GROUP = "cartel_enforcers";
	/** The hub role id (Saltgate's {@code ENFORCER_1}-{@code 6} posts). */
	public static final String HUB_ROLE = "enforcer";
	/** A group spawner whose data says no count spawns this many. */
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
				return new ArrayList<>(spawnSquad(level, at, size, owner));
			}
		});
		HubSpawners.register(HUB_ROLE, (level, at, planet, role) -> spawnHubGuard(level, at));
	}

	/**
	 * The kits of a squad of {@code size}: a captain leads a squad of three or more, then gunmen and
	 * bruisers by turns (a gunman first).
	 */
	public static List<CartelEnforcer.Variant> squad(int size) {
		List<CartelEnforcer.Variant> kits = new ArrayList<>();
		int rest = size;

		if (size >= 3) {
			kits.add(CartelEnforcer.Variant.CAPTAIN);
			rest--;
		}

		for (int i = 0; i < rest; i++) {
			kits.add(i % 2 == 0 ? CartelEnforcer.Variant.GUNMAN : CartelEnforcer.Variant.BRUISER);
		}

		return kits;
	}

	/** Spawns a squad of {@code size} ({@link #squad}) around {@code at}; with an {@code owner} it guards against them. */
	public static List<CartelEnforcer> spawnSquad(ServerLevel level, BlockPos at, int size, @Nullable UUID owner) {
		List<CartelEnforcer.Variant> kits = squad(Math.max(0, size));
		List<CartelEnforcer> out = new ArrayList<>(kits.size());

		for (int i = 0; i < kits.size(); i++) {
			CartelEnforcer.Variant kit = kits.get(i);
			CartelEnforcer enforcer = FoeSpawns.spawn(level, CartelEntities.CARTEL_ENFORCER, at, i, kits.size(), owner, e -> e.setVariant(kit));

			if (enforcer != null) {
				out.add(enforcer);
			}
		}

		return out;
	}

	/** One enforcer of {@code kit} at {@code at}, optionally owned; null when the level refused it. */
	public static @Nullable CartelEnforcer spawn(ServerLevel level, BlockPos at, CartelEnforcer.Variant kit, @Nullable UUID owner) {
		return FoeSpawns.spawn(level, CartelEntities.CARTEL_ENFORCER, at, 0, 1, owner, e -> e.setVariant(kit));
	}

	/** A wall guard for a Saltgate post: a gunman mostly, seeded by the post so it is the same one each restore. */
	public static @Nullable CartelEnforcer spawnHubGuard(ServerLevel level, BlockPos at) {
		float roll = RandomSource.create(at.asLong()).nextFloat();
		CartelEnforcer.Variant kit = roll < 0.6f ? CartelEnforcer.Variant.GUNMAN : roll < 0.85f ? CartelEnforcer.Variant.BRUISER : CartelEnforcer.Variant.CAPTAIN;
		return FoeSpawns.spawn(level, CartelEntities.CARTEL_ENFORCER, at, 0, 1, null, e -> {
			e.setVariant(kit);
			e.addTag(HubNpcs.HUB_TAG);
			e.setHomeTo(at, CartelBalance.get().hubLeash);
		});
	}

	private CartelEnforcers() {
	}
}
