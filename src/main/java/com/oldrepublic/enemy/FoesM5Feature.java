package com.oldrepublic.enemy;

import com.oldrepublic.boss.mentor.MentorDuel;
import com.oldrepublic.boss.raid.BossMusicPayload;
import com.oldrepublic.boss.raid.RaidFeature;
import com.oldrepublic.boss.ulvaine.UlvaineFeature;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.cartel.CartelEnforcers;
import com.oldrepublic.enemy.cartel.CartelEntities;
import com.oldrepublic.enemy.cartel.StandingRule;
import com.oldrepublic.enemy.hask.HaskEntities;
import com.oldrepublic.enemy.hask.HaskWarriors;
import com.oldrepublic.enemy.hymn.FoeReactions;
import com.oldrepublic.enemy.hymn.HymnEntities;
import com.oldrepublic.enemy.hymn.Hymnsworns;
import com.oldrepublic.enemy.scour.ScourEntities;
import com.oldrepublic.enemy.scour.ScourParticles;
import com.oldrepublic.enemy.scour.Scours;
import com.oldrepublic.enemy.stormback.StormbackEntities;
import com.oldrepublic.enemy.stormback.StormbackRule;
import com.oldrepublic.enemy.stormback.Stormbacks;
import com.oldrepublic.force.ForceReactions;

/**
 * Milestone 5 server and common initialisation for the five Milestone 5 foes (12.1), the mentor
 * duellist, Darth Ulvaine and her echo, the Long Hymn raid and the boss music service (section 12)
 * (spec MILESTONE_5.md). Owned by W8 foes and bosses; the lead calls it from {@code OldRepublicMod}
 * after the Phase 0 contracts, so the worker never edits the mod's entry point. Registers the foes'
 * entity types, spawn eggs, natural spawns and spawner groups, their attitudes and Force reactions,
 * then the bosses: the {@code boss_music} payload, Darth Ulvaine, the raid and the mentor duels.
 */
public final class FoesM5Feature {
	public static void init() {
		// The five foes (12.1): entity types and eggs (loaded with their classes), attributes, spawn placements.
		CartelEntities.init();
		HaskEntities.init();
		HymnEntities.init();
		ScourEntities.init();
		StormbackEntities.init();
		ScourParticles.init();

		// Spawner groups (17.1: registered here, after the Phase 0 built-ins), hub roles and natural spawns.
		CartelEnforcers.init();
		HaskWarriors.init();
		Hymnsworns.init();
		Scours.init();
		Stormbacks.init();

		// Standing decides the Kajidic's and the clan's attitudes; the stormback is a neutral grazer.
		Attitudes.register(new StandingRule());
		Attitudes.register(new StormbackRule());
		// Staggers, shocks, Pushes and disarms interrupt the foes' tells (the hunter's own handler covers the Hask).
		ForceReactions.register(new FoeReactions());

		// The bosses (12.2 to 12.5): every boss's music payload (the lead's Collector and Kaast send it too),
		// Ulvaine and her echo, the Long Hymn raid and the mentor duels.
		BossMusicPayload.register();
		UlvaineFeature.init();
		RaidFeature.init();
		MentorDuel.init();
	}

	private FoesM5Feature() {
	}
}
