package com.oldrepublic.enemy.creature;

import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.Waystones;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The beasts' natural spawns (spec MILESTONE_4.md 3.1, 3.3, 3.4, 10.3, 10.4), added with Fabric's
 * {@code BiomeModifications} on W1's planet biome tags so the planets load before these types exist.
 * Hollowhorn herds graze the Selvaren meadows (rarely the highwood); ash hound packs hunt the
 * Varkhast redrock and, more often, the ashfall, at dusk and night (always in the ashfall), never
 * within {@code houndHubExclusion} blocks of the Proving.
 */
public final class CreatureSpawns {
	/** Natural hollowhorns keep this far from a corrupted waystone (3.4). */
	private static final int WAYSTONE_AVOID = 24;
	/** The highwood sees hollowhorns at this fraction of the meadow's weight ("rarely"). */
	private static final int HIGHWOOD_DIVISOR = 5;

	/** Adds the spawn entries. Called from {@code FoesFeature} after the balance is loaded. */
	public static void init() {
		CreatureBalance b = CreatureBalance.get();
		BiomeModifications.addSpawn(BiomeSelectors.tag(PlanetWorldgen.IS_SELVAREN).and(BiomeSelectors.includeByKey(PlanetWorldgen.SELVAREN_MEADOW)),
				MobCategory.CREATURE, CreatureEntities.HOLLOWHORN, b.hollowhornWeight, b.herdMin, b.herdMax);
		BiomeModifications.addSpawn(BiomeSelectors.tag(PlanetWorldgen.IS_SELVAREN).and(BiomeSelectors.includeByKey(PlanetWorldgen.SELVAREN_HIGHWOOD)),
				MobCategory.CREATURE, CreatureEntities.HOLLOWHORN, Math.max(1, b.hollowhornWeight / HIGHWOOD_DIVISOR), b.herdMin, b.herdMax);
		BiomeModifications.addSpawn(BiomeSelectors.tag(PlanetWorldgen.IS_VARKHAST).and(BiomeSelectors.includeByKey(PlanetWorldgen.VARKHAST_REDROCK)),
				MobCategory.MONSTER, CreatureEntities.ASH_HOUND, b.houndWeight, b.packMin, b.packMax);
		BiomeModifications.addSpawn(BiomeSelectors.tag(PlanetWorldgen.IS_VARKHAST).and(BiomeSelectors.includeByKey(PlanetWorldgen.VARKHAST_ASHFALL)),
				MobCategory.MONSTER, CreatureEntities.ASH_HOUND, b.houndWeight * 3 / 2, b.packMin, b.packMax);
	}

	/** Solid ground; a natural herd also keeps away from corrupted waystones. */
	public static boolean checkHollowhornSpawnRules(EntityType<Hollowhorn> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (!Mob.checkMobSpawnRules(type, level, reason, pos, random)) {
			return false;
		}

		// The waystone lookup reads the POI index, so only live spawns make it, never chunk generation.
		return reason != EntitySpawnReason.NATURAL || !Waystones.corruptedNear(level.getLevel(), pos, WAYSTONE_AVOID);
	}

	/** Any light (the ashfall is dim by day), dusk and night outside the ashfall, and never near the Proving. */
	public static boolean checkAshHoundSpawnRules(EntityType<AshHound> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (!Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) {
			return false;
		}

		if (reason != EntitySpawnReason.NATURAL) {
			return true;
		}

		ServerLevel server = level.getLevel();
		// The hub of the world it spawns in (MILESTONE_5.md 3.1): the Proving on Varkhast, the hounds' only home.
		Planets.Planet planet = Planets.Planet.of(server.dimension());
		BlockPos hub = planet == null || planet == Planets.Planet.HOME ? null : PlanetHubs.hub(server.getServer(), planet);
		double hubDistance = hub == null ? -1 : hub.distSqr(pos);
		boolean ashfall = level.getBiome(pos).is(PlanetWorldgen.VARKHAST_ASHFALL);
		return CreatureRules.houndMaySpawn(server.isDarkOutside(), ashfall, hubDistance, CreatureBalance.get().houndHubExclusion);
	}

	private CreatureSpawns() {
	}
}
