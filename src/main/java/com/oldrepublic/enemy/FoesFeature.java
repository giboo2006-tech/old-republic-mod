package com.oldrepublic.enemy;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.kaast.KaastFeature;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.enemy.creature.CreatureEntities;
import com.oldrepublic.enemy.creature.CreatureItems;
import com.oldrepublic.enemy.creature.CreatureReactions;
import com.oldrepublic.enemy.creature.CreatureSpawns;
import com.oldrepublic.enemy.droid.DroidEntities;
import com.oldrepublic.enemy.droid.DroidReactions;
import com.oldrepublic.enemy.droid.Droids;
import com.oldrepublic.enemy.mandalorian.HunterEntities;
import com.oldrepublic.enemy.mandalorian.HunterReactions;
import com.oldrepublic.enemy.mandalorian.Netting;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.planet.HubSpawners;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Milestone 4 server and common initialisation for the Mandalorian hunter, the security droid, the
 * two planet beasts, the seeker droid and Torvin Kaast (spec MILESTONE_4.md section 10). Owned by W6
 * Force and foes; the lead calls it from {@code OldRepublicMod} after the Phase 0 contracts.
 * Registers the entity types and items, the natural spawns, the foes' reactions to the Force, their
 * damage resistances and attitudes, the Proving's patrol droid, the hunter's net, and the Kaast fight.
 */
public final class FoesFeature {
	public static void init() {
		HunterEntities.init();
		DroidEntities.init();
		CreatureEntities.init();
		CreatureItems.init();
		CreatureSpawns.init();

		ForceReactions.register(new HunterReactions());

		DroidReactions droids = new DroidReactions();
		ForceReactions.register(droids);
		DamageModifiers.register(droids);
		Attitudes.register(droids);
		// The Proving's patrol droid; replaces W1's placeholder factory (this init runs after PlanetFeature's).
		HubSpawners.register("droid", (level, at, planet, role) -> Droids.spawnHubPatrol(level, at));

		CreatureReactions creatures = new CreatureReactions();
		ForceReactions.register(creatures);
		Attitudes.register(creatures);

		Netting.init();
		ServerTickEvents.END_SERVER_TICK.register(Netting::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Netting.clear());

		KaastFeature.init();
	}

	/** The player credited with killing {@code victim}: the damage source's owner, else the last player who hurt it. */
	public static @Nullable ServerPlayer killer(LivingEntity victim, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player) {
			return player;
		}

		return victim.getLastHurtByPlayer() instanceof ServerPlayer player ? player : null;
	}

	private FoesFeature() {
	}
}
