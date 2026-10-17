package com.oldrepublic.enemy;

import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.trooper.PatrolSpawner;
import com.oldrepublic.enemy.trooper.TrooperAttitudeRule;
import com.oldrepublic.enemy.trooper.TrooperCommands;
import com.oldrepublic.enemy.trooper.TrooperEvents;
import com.oldrepublic.force.ForceReactions;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.entity.MobCategory;

/** Server and common initialisation for the enemy feature. Called from OldRepublicMod. */
public final class EnemyFeature {
	public static void init() {
		EnemyEntities.init();
		EnemyItems.init();
		EnemyParticles.init();

		// The trooper rule must be the first rule any W4 code registers (spec 5.2).
		Attitudes.register(new TrooperAttitudeRule());
		ForceReactions.register(new EnemyForceReactions());

		TrooperEvents.init();
		EnemyCommands.init();
		TrooperCommands.init();
		PatrolSpawner.init();

		// Rare underground spawns outside camps. Always registered (biome modifiers are applied before
		// the balance file is read) and gated by EnemyBalance#naturalSpawns in the spawn predicate.
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, EnemyEntities.CRYSTAL_POACHER, 2, 1, 2);
	}

	private EnemyFeature() {
	}
}
