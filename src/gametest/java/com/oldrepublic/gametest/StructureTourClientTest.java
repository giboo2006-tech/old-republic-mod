package com.oldrepublic.gametest;

import java.util.List;

import com.oldrepublic.gametest.m6.StructureTour;
import com.oldrepublic.gametest.m6.StructureTour.Stop;
import com.oldrepublic.gametest.visuals.Visuals;
import com.oldrepublic.planet.Planets;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The structure tour of the structures overhaul (docs/MILESTONE_6.md 9): every structure on its own world's terrain,
 * photographed from the air and from the ground ({@link StructureTour}), in the playthrough's seeded world. It takes long,
 * so only a visuals run that names it runs it ({@code visuals} workflow, looks {@code tour}); the build workflow's full
 * client run leaves it out.
 */
public class StructureTourClientTest implements FabricClientGameTest {
	/** The playthrough seed, so the tour's worlds are the ones a player of the seed sees. */
	private static final String SEED = "echoes-of-the-old-republic";

	/** Every structure as the game had it before the overhaul, by world. */
	private static final List<Stop> STOPS = List.of(
			Stop.worldgen(Level.OVERWORLD, "jedi_ruin", "jedi_ruin"),
			Stop.worldgen(Level.OVERWORLD, "sith_tomb", "sith_tomb"),
			Stop.worldgen(Level.OVERWORLD, "wreck_field", "wreck_field"),
			Stop.worldgen(Level.OVERWORLD, "republic_outpost", "republic_outpost"),
			Stop.worldgen(Level.OVERWORLD, "imperial_outpost", "imperial_outpost"),
			Stop.template(Level.OVERWORLD, "story/jedi_camp", "jedi_camp", -1),
			Stop.template(Level.OVERWORLD, "story/sith_camp", "sith_camp", -1),
			Stop.template(Level.OVERWORLD, "story/raider_camp", "raider_camp", -1),
			Stop.template(Level.OVERWORLD, "story/couriers_fall", "couriers_fall", -1),
			Stop.template(Level.OVERWORLD, "site/echo_shrine_jedi", "echo_shrine_jedi", -1),
			Stop.template(Level.OVERWORLD, "mission/raider_camp_small", "mission_raider_camp", -1),
			Stop.template(Level.OVERWORLD, "mission/fence_camp", "mission_fence_camp", -1),
			Stop.template(Level.OVERWORLD, "mission/survey_post", "mission_survey_post", -1),
			Stop.template(Level.OVERWORLD, "mission/skirmish_line", "mission_skirmish_line", -1),
			Stop.template(Level.OVERWORLD, "mission/patrol_ambush", "mission_patrol_ambush", -1),
			Stop.template(Level.OVERWORLD, "mission/smuggler_cache", "mission_smuggler_cache", -1),
			Stop.hub(Planets.Planet.SELVAREN, "greenwatch"),
			Stop.worldgen(Planets.SELVAREN, "waystone", "waystone"),
			Stop.template(Planets.SELVAREN, "planet/heartstone", "heartstone", -1),
			Stop.template(Planets.SELVAREN, "planet/wayfarer_cache", "wayfarer_cache", -1),
			Stop.template(Planets.SELVAREN, "expedition/singing_hollow_surface", "singing_hollow", -1),
			Stop.template(Planets.SELVAREN, "boss/hunting_camp", "hunting_camp", -1),
			Stop.hub(Planets.Planet.VARKHAST, "proving"),
			Stop.worldgen(Planets.VARKHAST, "greater_tomb", "greater_tomb"),
			Stop.template(Planets.VARKHAST, "site/detention_post", "detention_post", -1),
			Stop.template(Planets.VARKHAST, "expedition/duskglass_rift_surface", "duskglass_rift", -1),
			Stop.template(Planets.VARKHAST, "boss/reliquary_surface", "reliquary", -1),
			Stop.hub(Planets.Planet.GHESSAR, "saltgate"),
			Stop.worldgen(Planets.GHESSAR, "crashed_freighter", "crashed_freighter"),
			Stop.worldgen(Planets.GHESSAR, "kajidic_post", "kajidic_post"),
			Stop.template(Planets.GHESSAR, "site/debtor_camp", "debtor_camp", -1),
			Stop.hub(Planets.Planet.TYRVANN, "holdfast"),
			Stop.worldgen(Planets.TYRVANN, "hymn_shrine", "hymn_shrine"),
			Stop.worldgen(Planets.TYRVANN, "mire_grave", "mire_grave"),
			Stop.template(Planets.TYRVANN, "site/verse_stone", "verse_stone", -1),
			Stop.template(Planets.TYRVANN, "site/hymnal_arch", "hymnal", -1));

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Visuals.named("tour")) {
			return; // Not a tour run.
		}

		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(creator -> {
					creator.setSeed(SEED);
					creator.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
				})
				.create()) {
			world.getConnection().waitForChunksRender();
			StructureTour.run(context, world, STOPS);
		}
	}
}
