package com.oldrepublic.enemy.scour;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.hymn.FoeSpawns;
import com.oldrepublic.planet.PlanetWeather;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;
import com.oldrepublic.world.site.SpawnerGroups;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;

/**
 * Where sand scours come from (spec MILESTONE_5.md 3.2, 12.1): the natural spawn in Ghessar's dunes
 * and canyons (weight 10, twice that in a sandstorm), the spawner group {@code scour_brood} (contract
 * culls; with {@code bound} set, a matriarch leads it: the matriarch hunt), and the matriarch's own
 * brood calls.
 */
public final class Scours {
	/** The spawner group id. */
	public static final String GROUP = "scour_brood";
	/** A group spawner whose data says no count spawns this many. */
	public static final int DEFAULT_SIZE = 3;
	public static final ResourceKey<Biome> GHESSAR_DUNES = ResourceKey.create(Registries.BIOME, OldRepublicMod.id("ghessar_dunes"));
	public static final ResourceKey<Biome> GHESSAR_CANYONS = ResourceKey.create(Registries.BIOME, OldRepublicMod.id("ghessar_canyons"));

	/** Registers the group and the natural spawns. Called from {@code FoesM5Feature} after the balance is loaded. */
	public static void init() {
		SpawnerGroups.register(GROUP, new SpawnerGroups.Group() {
			@Override
			public int size(int count, BlockPos pos) {
				return count > 0 ? count : DEFAULT_SIZE;
			}

			@Override
			public List<LivingEntity> spawn(ServerLevel level, BlockPos at, int size, boolean bound, @Nullable UUID owner) {
				return new ArrayList<>(spawnBrood(level, at, size, bound, owner));
			}
		});

		ScourBalance b = ScourBalance.get();
		// Registered at the storm weight; outside a sandstorm the spawn rule turns away all but 1 in stormMultiplier.
		int weight = Math.max(1, b.weight * Math.max(1, b.stormMultiplier));
		BiomeModifications.addSpawn(BiomeSelectors.tag(PlanetWorldgen.IS_GHESSAR).and(BiomeSelectors.includeByKey(GHESSAR_DUNES, GHESSAR_CANYONS)),
				MobCategory.MONSTER, ScourEntities.SAND_SCOUR, weight, b.groupMin, Math.max(b.groupMin, b.groupMax));
	}

	/**
	 * Any light (the dunes glare by day), the vanilla monster rules otherwise; outside a sandstorm only
	 * 1 natural attempt in {@code stormMultiplier} is taken (the storm doubles the scours).
	 */
	public static boolean checkSpawnRules(EntityType<SandScour> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) {
			return false;
		}

		if (reason != EntitySpawnReason.NATURAL) {
			return true;
		}

		int multiplier = Math.max(1, ScourBalance.get().stormMultiplier);
		return PlanetWeather.kind(level.getLevel()) == PlanetWeather.Kind.SANDSTORM || random.nextInt(multiplier) == 0;
	}

	/** A brood of {@code size} around {@code at}, led by a matriarch when {@code matriarch} (she counts as one of them). */
	public static List<SandScour> spawnBrood(ServerLevel level, BlockPos at, int size, boolean matriarch, @Nullable UUID owner) {
		List<SandScour> out = new ArrayList<>(size);

		for (int i = 0; i < size; i++) {
			boolean leader = matriarch && i == 0;
			SandScour scour = FoeSpawns.spawn(level, ScourEntities.SAND_SCOUR, at, i, size, owner, s -> s.setMatriarch(leader));

			if (scour != null) {
				out.add(scour);
			}
		}

		return out;
	}

	/** One burrowed scour (a matriarch when {@code matriarch}) at {@code at}; null when the level refused it. */
	public static @Nullable SandScour spawn(ServerLevel level, BlockPos at, boolean matriarch, @Nullable UUID owner) {
		return FoeSpawns.spawn(level, ScourEntities.SAND_SCOUR, at, 0, 1, owner, s -> s.setMatriarch(matriarch));
	}

	private Scours() {
	}
}
