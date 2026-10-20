package com.oldrepublic.enemy.stormback;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.hymn.FoeSpawns;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;
import com.oldrepublic.world.site.SpawnerGroups;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;

/**
 * Where stormbacks come from (spec MILESTONE_5.md 3.3, 12.1): the natural spawn on Tyrvann's basalt
 * ridges and in the stormwood (weight 8, 1 to 2), and the spawner group {@code stormbacks} (the
 * Hymnal's pit, the {@code tyrvann_hunt} contract).
 */
public final class Stormbacks {
	/** The spawner group id. */
	public static final String GROUP = "stormbacks";
	/** A group spawner whose data says no count spawns this many. */
	public static final int DEFAULT_SIZE = 1;
	public static final ResourceKey<Biome> TYRVANN_BASALT = ResourceKey.create(Registries.BIOME, OldRepublicMod.id("tyrvann_basalt"));
	public static final ResourceKey<Biome> TYRVANN_STORMWOOD = ResourceKey.create(Registries.BIOME, OldRepublicMod.id("tyrvann_stormwood"));

	/** Registers the group and the natural spawns. Called from {@code FoesM5Feature} after the balance is loaded. */
	public static void init() {
		SpawnerGroups.register(GROUP, new SpawnerGroups.Group() {
			@Override
			public int size(int count, BlockPos pos) {
				return count > 0 ? count : DEFAULT_SIZE;
			}

			@Override
			public List<LivingEntity> spawn(ServerLevel level, BlockPos at, int size, boolean bound, @Nullable UUID owner) {
				return new ArrayList<>(spawnHerd(level, at, size, owner));
			}
		});

		StormbackBalance b = StormbackBalance.get();

		if (b.weight > 0) {
			BiomeModifications.addSpawn(BiomeSelectors.tag(PlanetWorldgen.IS_TYRVANN).and(BiomeSelectors.includeByKey(TYRVANN_BASALT, TYRVANN_STORMWOOD)),
					MobCategory.CREATURE, StormbackEntities.STORMBACK, b.weight, Math.max(1, b.groupMin), Math.max(Math.max(1, b.groupMin), b.groupMax));
		}
	}

	/** The vanilla ground rule (any light: the storm keeps the ridges dark by day); none in Peaceful. */
	public static boolean checkSpawnRules(EntityType<Stormback> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return level.getDifficulty() != Difficulty.PEACEFUL && Mob.checkMobSpawnRules(type, level, reason, pos, random);
	}

	/** {@code size} grazing stormbacks around {@code at}. */
	public static List<Stormback> spawnHerd(ServerLevel level, BlockPos at, int size, @Nullable UUID owner) {
		List<Stormback> out = new ArrayList<>(size);

		for (int i = 0; i < size; i++) {
			Stormback stormback = FoeSpawns.spawn(level, StormbackEntities.STORMBACK, at, i, size, owner, s -> {
			});

			if (stormback != null) {
				out.add(stormback);
			}
		}

		return out;
	}

	/** One grazing stormback at {@code at}; null when the level refused it. */
	public static @Nullable Stormback spawn(ServerLevel level, BlockPos at, @Nullable UUID owner) {
		return FoeSpawns.spawn(level, StormbackEntities.STORMBACK, at, 0, 1, owner, s -> {
		});
	}

	private Stormbacks() {
	}
}
