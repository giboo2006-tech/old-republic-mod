package com.oldrepublic.gametest;

import static com.oldrepublic.gametest.m6.StructureTour.close;

import java.util.ArrayList;
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
 * photographed from the air, from the ground and from its chosen views ({@link StructureTour}), in the playthrough's
 * seeded world. It takes long, so only a visuals run that names it runs it ({@code visuals} workflow): looks
 * {@code tour} for every group, or {@code tour_<group>} for one ({@code tour_story,tour_wilds} for two); the build
 * workflow's full client run leaves it out. The before pictures were taken by this test as it stood at 0bcfbb8, with
 * the structures as they were before the overhaul.
 */
public class StructureTourClientTest implements FabricClientGameTest {
	/** The playthrough seed, so the tour's worlds are the ones a player of the seed sees. */
	private static final String SEED = "echoes-of-the-old-republic";

	/** A group of stops, run by the look {@code tour_<name>}. */
	private record Group(String name, List<Stop> stops) {
	}

	private static final List<Group> GROUPS = List.of(
			new Group("landmarks", List.of(
					Stop.variants(Level.OVERWORLD, "jedi_ruin", "jedi_ruin", 4, close("jedi_ruin/courtyard", 29, 5, 9, 26, 6, 38, false),
							close("jedi_ruin/courtyard_2", 25, 5, 9, 25, 6, 38, false)),
					Stop.variants(Level.OVERWORLD, "sith_tomb", "sith_tomb", 6, close("sith_tomb/gate", 20, 7, 9, 20, 3, 34, true),
							close("sith_tomb/gate_2", 20, 7, 6, 20, 3, 23, true)),
					Stop.variants(Level.OVERWORLD, "wreck_field", "wreck_field", 4, close("wreck_field/hull", 25, 5, 14, 38, 9, 11, false),
							close("wreck_field/hull_2", 25, 5, 14, 38, 9, 11, false)),
					Stop.variants(Level.OVERWORLD, "crystal_cave", "crystal_cave", 5, close("crystal_cave/camp", 5, 11, 5, 24, 6, 22, true),
							close("crystal_cave/camp_2", 31, 11, 32, 18, 3, 18, true), close("crystal_cave/heart", 22, 2, 5, 22, 2, 29, false)),
					Stop.worldgen(Level.OVERWORLD, "republic_outpost", "republic_outpost", close("outpost/republic_post", 22, 2, 3, 22, 15, 25, false)),
					Stop.worldgen(Level.OVERWORLD, "imperial_outpost", "imperial_outpost", close("outpost/imperial_post", 22, 2, 1, 22, 10, 25, true)))),
			new Group("story", List.of(
					Stop.template(Level.OVERWORLD, "story/crossing", "crossing", -1, close("story/crossing", 17, 2, 5, 17, 4, 17, false)),
					Stop.overlay(Level.OVERWORLD, "story/jedi_encampment", "story/crossing", "jedi_encampment",
							close("story/jedi_encampment", 22, 2, 22, 14, 5, 8, false)),
					Stop.overlay(Level.OVERWORLD, "story/sith_encampment", "story/crossing", "sith_encampment",
							close("story/sith_encampment", 6, 2, 13, 4, 1, 8, true)),
					Stop.template(Level.OVERWORLD, "story/jedi_camp", "jedi_camp", -1, close("story/jedi_camp", 4, 2, 6, 1, 1, 1, true)),
					Stop.template(Level.OVERWORLD, "story/sith_camp", "sith_camp", -1, close("story/sith_camp", 4, 2, 6, 1, 1, 1, true)),
					Stop.template(Level.OVERWORLD, "story/raider_camp", "raider_camp", -1, close("story/raider_camp", 25, 2, 11, 9, 20, 38, false)),
					Stop.template(Level.OVERWORLD, "story/couriers_fall", "couriers_fall", -1, close("story/couriers_fall", 2, 4, 15, 31, 4, 15, false)),
					Stop.template(Level.OVERWORLD, "site/echo_shrine_jedi", "echo_shrine_jedi", -1, close("site/echo_shrine_jedi", 13, 2, 14, 13, 4, 23, true)),
					Stop.template(Level.OVERWORLD, "site/echo_shrine_sith", "echo_shrine_sith", -1, close("site/echo_shrine_sith", 13, 2, 14, 13, 3, 23, true)),
					Stop.template(Level.OVERWORLD, "site/echo_shrine_wayfarer", "echo_shrine_wayfarer", -1,
							close("site/echo_shrine_wayfarer", 19, 6, 6, 9, 6, 10, true)),
					Stop.template(Level.OVERWORLD, "ship/cradle", "ship_cradle", -1, close("ship/cradle", 8, 7, 14, 20, 4, 20, false)),
					Stop.template(Level.OVERWORLD, "ship/beacon_pad", "beacon_pad", 1, close("ship/beacon_pad", 5, 2, 10, 5, 0, 4, false)))),
			new Group("missions", List.of(
					Stop.template(Level.OVERWORLD, "mission/raider_camp_small", "mission_raider_camp", -1,
							close("mission/raider_camp_small", 13, 2, 3, 20, 11, 14, true)),
					Stop.template(Level.OVERWORLD, "mission/raider_camp_small_2", "mission_raider_camp_2", -1,
							close("mission/raider_camp_small_2", 14, 2, 4, 13, 2, 13, true)),
					Stop.template(Level.OVERWORLD, "mission/fence_camp", "mission_fence_camp", -1, close("mission/fence_camp", 15, 2, 15, 15, 2, 22, true)),
					Stop.template(Level.OVERWORLD, "mission/fence_camp_2", "mission_fence_camp_2", -1, close("mission/fence_camp_2", 15, 2, 2, 15, 3, 19, true)),
					Stop.template(Level.OVERWORLD, "mission/survey_post", "mission_survey_post", -1, close("mission/survey_post", 13, 2, 4, 22, 14, 13, true)),
					Stop.template(Level.OVERWORLD, "mission/survey_post_2", "mission_survey_post_2", -1,
							close("mission/survey_post_2", 12, 2, 9, 18, 9, 17, false)),
					Stop.template(Level.OVERWORLD, "mission/skirmish_line", "mission_skirmish_line", -1,
							close("mission/skirmish_line", 20, 2, 11, 20, 7, 3, false)),
					Stop.template(Level.OVERWORLD, "mission/skirmish_line_2", "mission_skirmish_line_2", -1,
							close("mission/skirmish_line_2", 19, 5, 15, 19, 4, 3, false)),
					Stop.template(Level.OVERWORLD, "mission/patrol_ambush", "mission_patrol_ambush", -1,
							close("mission/patrol_ambush", 13, 2, 0, 17, 1, 14, false)),
					Stop.template(Level.OVERWORLD, "mission/patrol_ambush_2", "mission_patrol_ambush_2", -1,
							close("mission/patrol_ambush_2", 14, 2, 2, 15, 2, 14, false)),
					Stop.template(Level.OVERWORLD, "mission/smuggler_cache", "mission_smuggler_cache", -1,
							close("mission/smuggler_cache", 12, 2, 13, 10, 1, 22, false)),
					Stop.template(Level.OVERWORLD, "mission/smuggler_cache_2", "mission_smuggler_cache_2", -1,
							close("mission/smuggler_cache_2", 11, 2, 9, 11, 1, 20, false)))),
			new Group("wilds", List.of(
					Stop.variants(Level.OVERWORLD, "wilds/homestead", "homestead", 6, close("wilds/homestead/vessk", 14, 2, 20, 8, 2, 19, false),
							close("wilds/homestead/orrin", 24, 3, 21, 34, 2, 19, false)),
					Stop.variants(Level.OVERWORLD, "wilds/watch_hermitage", "watch_hermitage", 2, close("wilds/watch_hermitage/spire", 11, 11, 9, 14, 11, 15, false),
							close("wilds/watch_hermitage/ruin", 12, 29, 9, 3, 2, 3, false)),
					Stop.variants(Level.OVERWORLD, "wilds/war_scar", "war_scar", 4, close("wilds/war_scar/walker_line", 32, 4, 46, 33, 7, 37, false),
							close("wilds/war_scar/gunship_battery", 37, 5, 26, 24, 5, 24, false)),
					Stop.variants(Level.OVERWORLD, "wilds/downed_fighter", "downed_fighter", 3,
							close("wilds/downed_fighter/republic_lean_to", 7, 4, 15, 12, 4, 10, false),
							close("wilds/downed_fighter/imperial_grave", 2, 4, 14, 10, 4, 10, false)),
					Stop.variants(Level.OVERWORLD, "wilds/jungle_shrine", "jungle_shrine", 3, close("wilds/jungle_shrine/pool_court", 18, 4, 4, 18, 8, 31, false),
							close("wilds/jungle_shrine/sunken_dome", 18, 4, 12, 19, 5, 19, false)),
					// The coast, the swamp and the sea: their other variant would stand on dry land, so only the generated one.
					Stop.worldgen(Level.OVERWORLD, "wilds/smuggler_cove", "smuggler_cove", close("wilds/smuggler_cove/cove", 13, 10, 8, 17, 8, 22, false),
							close("wilds/smuggler_cove/arch", 9, 9, 9, 13, 8, 26, false)),
					Stop.worldgen(Level.OVERWORLD, "wilds/stilt_hamlet", "stilt_hamlet", close("wilds/stilt_hamlet/reedholm", 26, 9, 28, 3, 9, 26, true),
							close("wilds/stilt_hamlet/oakhollow", 22, 9, 27, 20, 9, 29, true)),
					Stop.variants(Level.OVERWORLD, "wilds/poacher_dig", "poacher_dig", 12, close("wilds/poacher_dig/cut", 17, 13, 21, 20, 6, 3, true),
							close("wilds/poacher_dig/shaft", 8, 3, 13, 30, 2, 13, false)),
					Stop.variants(Level.OVERWORLD, "wilds/starfall_crater", "starfall_crater", 14, close("wilds/starfall_crater/fresh", 28, 17, 31, 28, 11, 19, false),
							close("wilds/starfall_crater/old", 27, 16, 33, 27, 11, 20, false), close("wilds/starfall_crater/deep", 22, 18, 6, 22, 9, 16, false)),
					Stop.variants(Level.OVERWORLD, "wilds/treaty_stone", "treaty_stone", 2, close("wilds/treaty_stone/court", 20, 3, 35, 20, 9, 19, false),
							close("wilds/treaty_stone/avenue", 16, 3, 39, 16, 9, 7, false)),
					Stop.worldgen(Level.OVERWORLD, "wilds/sunken_freighter", "sunken_freighter",
							close("wilds/sunken_freighter/broken_back", 13, 5, 12, 24, 5, 12, false),
							close("wilds/sunken_freighter/nose_down", 40, 6, 1, 16, 16, 4, false)))),
			new Group("selvaren", List.of(
					Stop.hub(Planets.Planet.SELVAREN, "greenwatch", close("hub/greenwatch", 47, 10, 28, 47, 13, 21, true)),
					Stop.worldgen(Planets.SELVAREN, "waystone", "waystone", close("planet/waystone", 10, 3, 4, 10, 4, 10, false)),
					Stop.overlay(Planets.SELVAREN, "planet/waystone_corrupted", "planet/waystone", "waystone_corrupted",
							close("planet/waystone_corrupted", 7, 3, 4, 10, 5, 11, false)),
					Stop.template(Planets.SELVAREN, "planet/heartstone", "heartstone", -1, close("planet/heartstone", 17, 4, 1, 17, 9, 18, false)),
					Stop.template(Planets.SELVAREN, "planet/wayfarer_cache", "wayfarer_cache", -1, close("planet/wayfarer_cache", 13, 9, 8, 13, 9, 22, false)),
					Stop.template(Planets.SELVAREN, "expedition/singing_hollow_surface", "singing_hollow", -1,
							close("expedition/singing_hollow_surface", 13, 5, 1, 13, 6, 12, false)),
					Stop.template(Planets.SELVAREN, "expedition/singing_hollow_grotto", "singing_hollow_grotto", 16,
							close("expedition/singing_hollow_grotto", 12, 5, 12, 18, 3, 12, false)),
					Stop.template(Planets.SELVAREN, "boss/hunting_camp", "hunting_camp", -1, close("boss/hunting_camp", 28, 6, 43, 28, 2, 28, true)),
					Stop.variants(Planets.SELVAREN, "selvaren/watch_cloister", "watch_cloister", 3,
							close("selvaren/watch_cloister/bell", 31, 4, 10, 17, 7, 8, false),
							close("selvaren/watch_cloister/fallen", 14, 4, 37, 48, 9, 36, false)),
					Stop.variants(Planets.SELVAREN, "selvaren/tarn_causeway", "tarn_causeway", 5,
							close("selvaren/tarn_causeway/isle", 10, 7, 12, 45, 8, 12, false),
							close("selvaren/tarn_causeway/broken", 12, 7, 26, 36, 7, 10, false)),
					Stop.variants(Planets.SELVAREN, "selvaren/ridge_tower", "ridge_tower", 3, close("selvaren/ridge_tower/kept", 17, 4, 13, 9, 28, 5, false),
							close("selvaren/ridge_tower/cold", 8, 19, 8, 12, 30, 6, false)),
					Stop.variants(Planets.SELVAREN, "selvaren/hollowhorn_fold", "hollowhorn_fold", 3,
							close("selvaren/hollowhorn_fold/fold", 21, 4, 25, 14, 6, 6, false),
							close("selvaren/hollowhorn_fold/steading", 8, 4, 23, 20, 8, 6, false)),
					Stop.variants(Planets.SELVAREN, "selvaren/aurelite_dig", "aurelite_dig", 3, close("selvaren/aurelite_dig/adit", 14, 4, 21, 14, 5, 5, false),
							close("selvaren/aurelite_dig/face", 21, 4, 27, 10, 8, 10, false)))),
			new Group("varkhast", List.of(
					Stop.hub(Planets.Planet.VARKHAST, "proving", close("hub/proving", 47, 9, 40, 47, 22, 26, false),
							close("hub/sepulchre", 15, 5, 27, 34, 3, 27, false)),
					Stop.worldgen(Planets.VARKHAST, "greater_tomb", "greater_tomb", close("greater_tomb/gate", 17, 8, 10, 17, 2, 31, false)),
					Stop.template(Planets.VARKHAST, "site/detention_post", "detention_post", -1, close("site/detention_post", 11, 3, 37, 38, 3, 37, false)),
					Stop.template(Planets.VARKHAST, "expedition/duskglass_rift_surface", "duskglass_rift", -1,
							close("expedition/duskglass_rift_surface", 13, 4, 5, 13, 5, 13, false)),
					Stop.template(Planets.VARKHAST, "expedition/duskglass_rift_rift", "duskglass_rift_rift", 28,
							close("expedition/duskglass_rift_rift", 10, 23, 12, 11, 2, 16, false)),
					Stop.template(Planets.VARKHAST, "boss/reliquary_surface", "reliquary", -1, close("boss/reliquary_surface", 13, 11, 4, 14, 9, 12, false)),
					Stop.template(Planets.VARKHAST, "boss/reliquary_hall", "reliquary_hall", 18, close("boss/reliquary_hall", 15, 10, 19, 15, 7, 3, false)),
					Stop.variants(Planets.VARKHAST, "varkhast/cliff_mausoleum", "cliff_mausoleum", 2,
							close("varkhast/cliff_mausoleum/sealed", 11, 4, 28, 24, 3, 28, false),
							close("varkhast/cliff_mausoleum/broken", 11, 4, 28, 24, 3, 28, false)),
					Stop.variants(Planets.VARKHAST, "varkhast/obelisk_way", "obelisk_way", 7, close("varkhast/obelisk_way/gate", 13, 8, 1, 13, 9, 60, false),
							close("varkhast/obelisk_way/fallen", 13, 8, 1, 13, 9, 60, false)),
					Stop.variants(Planets.VARKHAST, "varkhast/imperial_dig", "imperial_dig", 13, close("varkhast/imperial_dig/door", 31, 10, 33, 31, 8, 13, true),
							close("varkhast/imperial_dig/head", 31, 14, 39, 31, 6, 20, true)),
					Stop.variants(Planets.VARKHAST, "varkhast/ash_village", "ash_village", 7, close("varkhast/ash_village/bell", 26, 4, 34, 26, 13, 15, false),
							close("varkhast/ash_village/ember", 1, 9, 30, 30, 7, 30, true)),
					Stop.variants(Planets.VARKHAST, "varkhast/hound_den", "hound_den", 3, close("varkhast/hound_den/mouth", 12, 5, 13, 17, 3, 7, false),
							close("varkhast/hound_den/cleft", 25, 4, 11, 13, 3, 9, false)))),
			new Group("ghessar", List.of(
					Stop.hub(Planets.Planet.GHESSAR, "saltgate", close("hub/saltgate", 44, 5, 8, 44, 4, 28, false)),
					Stop.worldgen(Planets.GHESSAR, "crashed_freighter", "crashed_freighter", close("planet/crashed_freighter", 27, 6, 26, 28, 6, 16, false)),
					Stop.worldgen(Planets.GHESSAR, "kajidic_post", "kajidic_post", close("planet/kajidic_post", 21, 4, 37, 21, 5, 10, false)),
					Stop.template(Planets.GHESSAR, "site/debtor_camp", "debtor_camp", -1, close("site/debtor_camp", 17, 3, 4, 17, 3, 27, true)),
					Stop.template(Planets.GHESSAR, "mission/ambush_rocks", "ambush_rocks", -1, close("mission/ambush_rocks", 11, 2, 1, 20, 11, 18, true)),
					Stop.worldgen(Planets.GHESSAR, "ghessar/freighter_graveyard", "freighter_graveyard",
							close("ghessar/freighter_graveyard/yard_a", 7, 6, 25, 16, 13, 14, false),
							close("ghessar/freighter_graveyard/yard_b", 28, 5, 17, 17, 5, 19, false)),
					Stop.variants(Planets.GHESSAR, "ghessar/caravan_waystation", "caravan_waystation", 2,
							close("ghessar/caravan_waystation/kept", 23, 3, 4, 23, 4, 20, false),
							close("ghessar/caravan_waystation/ruined", 23, 3, 4, 23, 4, 20, false)),
					Stop.variants(Planets.GHESSAR, "ghessar/salt_works", "salt_works", 2, close("ghessar/salt_works/working", 20, 3, 30, 10, 9, 8, false),
							close("ghessar/salt_works/abandoned", 20, 3, 30, 15, 4, 8, false)),
					Stop.variants(Planets.GHESSAR, "ghessar/mesa_lookout", "mesa_lookout", 3, close("ghessar/mesa_lookout/lift", 10, 38, 16, 8, 38, 6, false),
							close("ghessar/mesa_lookout/stair", 6, 38, 16, 8, 38, 6, false)),
					Stop.variants(Planets.GHESSAR, "ghessar/salvager_squat", "salvager_squat", 2,
							close("ghessar/salvager_squat/market", 21, 3, 30, 21, 7, 21, false),
							close("ghessar/salvager_squat/walled", 22, 3, 39, 21, 4, 11, false)),
					Stop.variants(Planets.GHESSAR, "ghessar/escape_pod", "escape_pod", 4, close("ghessar/escape_pod/republic", 16, 5, 11, 7, 7, 10, false),
							close("ghessar/escape_pod/imperial", 16, 5, 11, 7, 7, 10, false)))),
			new Group("tyrvann", List.of(
					Stop.hub(Planets.Planet.TYRVANN, "holdfast", close("hub/holdfast", 41, 5, 6, 41, 4, 19, true)),
					Stop.worldgen(Planets.TYRVANN, "hymn_shrine", "hymn_shrine", close("planet/hymn_shrine", 14, 6, 7, 14, 13, 13, true)),
					Stop.worldgen(Planets.TYRVANN, "mire_grave", "mire_grave", close("planet/mire_grave", 19, 4, 12, 10, 5, 15, false)),
					Stop.template(Planets.TYRVANN, "site/verse_stone", "verse_stone", -1, close("site/verse_stone", 9, 3, 4, 11, 7, 11, false)),
					Stop.template(Planets.TYRVANN, "site/hymnal_arch", "hymnal", -1, close("site/hymnal_arch", 7, 15, 7, 7, 27, 4, true)),
					Stop.template(Planets.TYRVANN, "site/hymnal", "hymnal_halls", 28, close("site/hymnal", 16, 12, 47, 18, 11, 64, false)),
					// A jigsaw town: its lots are shot as they generated.
					Stop.worldgen(Planets.TYRVANN, "tyrvann/silenced_city", "silenced_city", close("tyrvann/silenced_city/plaza_a", 20, 3, 4, 20, 13, 20, false),
							close("tyrvann/silenced_city/plaza_b", 17, 3, 3, 30, 4, 20, false),
							close("tyrvann/silenced_city/song_hall", 15, 3, 7, 15, 7, 25, true),
							close("tyrvann/silenced_city/bell_tower", 15, 3, 2, 15, 22, 10, false),
							close("tyrvann/silenced_city/quay", 5, 4, 22, 20, 4, 17, false),
							close("tyrvann/silenced_city/courtyard_house", 15, 3, 6, 15, 4, 18, false)),
					Stop.variants(Planets.TYRVANN, "tyrvann/choir_ruin", "choir_ruin", 2, close("tyrvann/choir_ruin/hall_a", 18, 5, 9, 16, 15, 24, true),
							close("tyrvann/choir_ruin/hall_b", 18, 5, 9, 18, 11, 34, true)),
					Stop.variants(Planets.TYRVANN, "tyrvann/hask_camp", "hask_camp", 2, close("tyrvann/hask_camp/camp_a", 17, 3, 2, 17, 8, 12, true),
							close("tyrvann/hask_camp/camp_b", 17, 5, 3, 17, 9, 14, true)),
					Stop.variants(Planets.TYRVANN, "tyrvann/mire_causeway", "mire_causeway", 2,
							close("tyrvann/mire_causeway/causeway_a", 10, 3, 11, 9, 8, 30, false),
							close("tyrvann/mire_causeway/causeway_b", 10, 3, 6, 10, 5, 26, false)),
					Stop.variants(Planets.TYRVANN, "tyrvann/downed_interceptor", "downed_interceptor", 2,
							close("tyrvann/downed_interceptor/wreck_a", 11, 3, 29, 14, 5, 12, false),
							close("tyrvann/downed_interceptor/wreck_b", 11, 3, 29, 14, 6, 16, false)))));

	@Override
	public void runTest(ClientGameTestContext context) {
		List<Stop> stops = new ArrayList<>();

		for (Group group : GROUPS) {
			if (Visuals.named("tour") || Visuals.named("tour_" + group.name())) {
				stops.addAll(group.stops());
			}
		}

		if (stops.isEmpty()) {
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
			StructureTour.run(context, world, stops);
		}
	}
}
