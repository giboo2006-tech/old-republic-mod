#!/usr/bin/env python3
"""Generates W1's Milestone 4 world data: the two planets' worldgen JSON, the planet structure templates
(gzipped structure NBT) and their anchor sidecars, the waystone and greater tomb structures, sets, pools
and tags. See docs/design/planets.md.

Deterministic: the same script always writes byte-identical files (fixed seeds, the NBT writer and Template
class of gen_structures.py, the Site helpers of gen_structures_m3.py, fixed gzip mtime). Writes under
src/main/resources/data/oldrepublic/:

  Planet data (spec 3.1 to 3.3)
    dimension_type/{selvaren,varkhast}.json       the overworld type with the planet's attributes
    dimension/{selvaren,varkhast}.json            noise generator, multi_noise source of the three biomes
    worldgen/noise_settings/{selvaren,varkhast}   the vanilla overworld noise router (copied verbatim from the
                                                  26.2 dump once, kept from the committed file afterwards),
                                                  the planet's sea level, aquifers and surface rule
    worldgen/biome/<six biomes>.json              colours, spawners (vanilla mobs only), carvers, features
    worldgen/configured_feature, placed_feature   planet trees, ores and the W1 Java decoration features
    tags/worldgen/biome/{is_selvaren,is_varkhast,is_planet,has_structure/waystone,has_structure/greater_tomb}

  Templates (spec 3.4, 3.5, 16.1): see M4_TEMPLATES below
    structure/<template>.nbt, site_anchors/<template>.json   planet/{waystone,waystone_corrupted,heartstone,wayfarer_cache},
                                                  greater_tomb/{gate,descent,urn_vault,sealed_crypt},
                                                  hub/{greenwatch,proving,sepulchre}, expedition/{singing_hollow_surface,
                                                  singing_hollow_grotto,duskglass_rift_surface,duskglass_rift_rift},
                                                  boss/hunting_camp, mission/ambush_rocks
    worldgen/{structure,template_pool,structure_set}  the waystone (one piece) and the greater tomb (gate, descent, urn
                                                  vault, sealed crypt) with their random-spread sets
    tags/worldgen/structure/{waystones,greater_tombs}  StructureLocator's tags

  Every template is checked before it is written: its size, its exact sidecar anchors (each a SiteAnchors.Anchor,
  read from the Java enum), the block each anchor holds, two cells of air on a block at each feet anchor, room above
  spawners and terminals; the greater tomb is assembled with every rotation as vanilla's jigsaw placement would.

Rules: those of gen_structures_m3.py (template-local x east, y up, z south; `buried` B puts layer B on the
stand; anchors hold their real blocks; no floor circles, sigils or glowing runes), plus: biome feature lists
are cut from one master order per planet, so the feature sorter never sees a cycle.

Usage: python3 tools/assets/gen_structures_m4.py [--router <vanilla overworld noise_settings JSON>]
"""

import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_structures_m3 as m3  # noqa: E402
from gen_structures import AIR, CAVE_AIR, Byte, value_noise  # noqa: E402
from gen_structures_m3 import (  # noqa: E402
    BOTTOM, CHEST, COUNTERWEIGHT, LANTERN_HUNG, LEAVES, MOD, SPAWNER, Element, Site, Structure,
    assemble, campfire, clear_above, log, noise, sidecar, stairs, write_json,
)

DATA = m3.DATA


# =============================================================================================
# Planet worldgen data (spec 3.1 to 3.3)
# =============================================================================================
def block(name, props=None):
    state = {"Name": name}
    if props:
        state["Properties"] = props
    return state


def rule_block(name, props=None):
    return {"type": "minecraft:block", "result_state": block(name, props)}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def cond(condition, then):
    return {"type": "minecraft:condition", "if_true": condition, "then_run": then}


def biome_is(*ids):
    return {"type": "minecraft:biome", "biome_is": MOD + ids[0] if len(ids) == 1 else [MOD + b for b in ids]}


def stone_depth(surface_type="floor", add_surface_depth=False, offset=0, secondary=0):
    return {"type": "minecraft:stone_depth", "add_surface_depth": add_surface_depth, "offset": offset,
            "secondary_depth_range": secondary, "surface_type": surface_type}


def y_above(y, add_stone_depth=False, multiplier=0):
    return {"type": "minecraft:y_above", "add_stone_depth": add_stone_depth, "anchor": {"absolute": y},
            "surface_depth_multiplier": multiplier}


def surface_noise(lo, hi):
    return {"type": "minecraft:noise_threshold", "max_threshold": hi, "min_threshold": lo, "noise": "minecraft:surface"}


def water(offset, add_stone_depth=False, multiplier=0):
    return {"type": "minecraft:water", "add_stone_depth": add_stone_depth, "offset": offset, "surface_depth_multiplier": multiplier}


def negate(condition):
    return {"type": "minecraft:not", "invert": condition}


BEDROCK_FLOOR = cond({"type": "minecraft:vertical_gradient", "false_at_and_above": {"above_bottom": 5},
                      "random_name": "minecraft:bedrock_floor", "true_at_and_below": {"above_bottom": 0}},
                     rule_block("minecraft:bedrock"))
DEEPSLATE = cond({"type": "minecraft:vertical_gradient", "false_at_and_above": {"absolute": 8},
                  "random_name": "minecraft:deepslate", "true_at_and_below": {"absolute": 0}},
                 rule_block("minecraft:deepslate", {"axis": "y"}))
ABOVE_SURFACE = {"type": "minecraft:above_preliminary_surface"}


def selvaren_surface():
    """Grass and dirt over stone; podzol patches in the highwood, coarse dirt in the meadow, gravel shores and
    clay beds in the tarns (sea level 58)."""
    highwood_top = seq(
        cond(surface_noise(-0.95, -0.55), rule_block("minecraft:podzol", {"snowy": "false"})),
        cond(surface_noise(0.6, 0.72), rule_block("minecraft:moss_block")),
        cond(water(-1), rule_block("minecraft:grass_block", {"snowy": "false"})),
        rule_block("minecraft:dirt"),
    )
    meadow_top = seq(
        cond(surface_noise(-0.18, 0.18), rule_block("minecraft:coarse_dirt")),
        cond(water(-1), rule_block("minecraft:grass_block", {"snowy": "false"})),
        rule_block("minecraft:gravel"),
    )
    tarn_top = seq(
        # Shores: gravel and clay a few blocks either side of the water line, grass above.
        cond(negate(y_above(61)), seq(
            cond(surface_noise(0.3, 1.0e9), rule_block("minecraft:clay")),
            rule_block("minecraft:gravel"))),
        cond(surface_noise(0.55, 1.0e9), rule_block("minecraft:stone")),
        cond(water(-1), rule_block("minecraft:grass_block", {"snowy": "false"})),
        rule_block("minecraft:gravel"),
    )
    return seq(
        BEDROCK_FLOOR,
        cond(ABOVE_SURFACE, seq(
            cond(stone_depth(), seq(
                cond(biome_is("selvaren_highwood"), highwood_top),
                cond(biome_is("selvaren_meadow"), meadow_top),
                cond(biome_is("selvaren_tarn"), tarn_top),
            )),
            cond(stone_depth(add_surface_depth=True), seq(
                cond(biome_is("selvaren_tarn"), cond(negate(y_above(61)), rule_block("minecraft:gravel"))),
                rule_block("minecraft:dirt"),
            )),
        )),
        DEEPSLATE,
    )


def varkhast_surface():
    """Banded red rock (vanilla bandlands) in the redrock and the tomb valleys, red sand floors, grey coarse
    dirt, gravel and tuff in the ashfall. No sea (sea level -64), so every water check passes."""
    redrock_top = seq(
        cond(y_above(74, add_stone_depth=True, multiplier=1), seq(
            cond(surface_noise(-0.909, -0.5454), rule_block("minecraft:terracotta")),
            cond(surface_noise(-0.1818, 0.1818), rule_block("minecraft:terracotta")),
            cond(surface_noise(0.5454, 0.909), rule_block("minecraft:terracotta")),
            {"type": "minecraft:bandlands"})),
        cond(stone_depth("ceiling"), rule_block("minecraft:red_sandstone")),
        rule_block("minecraft:red_sand"),
    )
    ashfall_top = seq(
        cond(surface_noise(-0.909, -0.5454), rule_block("minecraft:gravel")),
        cond(surface_noise(0.5454, 0.909), rule_block("minecraft:tuff")),
        cond(surface_noise(0.2, 0.3), rule_block("minecraft:smooth_basalt")),
        rule_block("minecraft:coarse_dirt"),
    )
    valley_top = seq(
        cond(surface_noise(-0.3, 0.1), rule_block("minecraft:smooth_sandstone")),
        cond(surface_noise(0.6, 1.0e9), rule_block("minecraft:gravel")),
        cond(stone_depth("ceiling"), rule_block("minecraft:red_sandstone")),
        rule_block("minecraft:red_sand"),
    )
    return seq(
        BEDROCK_FLOOR,
        cond(ABOVE_SURFACE, seq(
            cond(stone_depth(), seq(
                cond(biome_is("varkhast_redrock"), redrock_top),
                cond(biome_is("varkhast_ashfall"), ashfall_top),
                cond(biome_is("varkhast_tomb_valley"), valley_top),
            )),
            cond(stone_depth(add_surface_depth=True), seq(
                cond(biome_is("varkhast_ashfall"), seq(
                    cond(surface_noise(0.0, 1.0e9), rule_block("minecraft:tuff")),
                    rule_block("minecraft:coarse_dirt"))),
                cond(y_above(64, add_stone_depth=True, multiplier=1), {"type": "minecraft:bandlands"}),
                rule_block("minecraft:red_sandstone"),
            )),
            # Cliffs and mesa faces above the preliminary surface show the bands, as in vanilla badlands.
            cond(biome_is("varkhast_redrock", "varkhast_tomb_valley"),
                 cond(y_above(50, add_stone_depth=True, multiplier=-1), {"type": "minecraft:bandlands"})),
        )),
        DEEPSLATE,
    )


PLANETS = {
    "selvaren": {
        "sea_level": 58,
        "aquifers": True,
        "surface": selvaren_surface,
        "sky": "#8fc6c9", "fog": "#b7d3d0", "cloud": "#e6ffffff", "ambient_light": "#0a120c",
        "music": MOD + "music.selvaren",
    },
    "varkhast": {
        "sea_level": -64,
        "aquifers": False,
        "surface": varkhast_surface,
        # No clouds: fully transparent (ARGB), so the dull rust sky stays open.
        "sky": "#c27a4e", "fog": "#8a5a44", "cloud": "#00f0d0b8", "ambient_light": "#140a06",
        "music": MOD + "music.varkhast",
    },
}


def dimension_type_json(planet):
    p = PLANETS[planet]
    return {
        "ambient_light": 0.0,
        "attributes": {
            "minecraft:audio/ambient_sounds": {"mood": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave",
                                                        "tick_delay": 6000}},
            "minecraft:audio/background_music": {
                "creative": {"max_delay": 24000, "min_delay": 12000, "sound": p["music"]},
                "default": {"max_delay": 24000, "min_delay": 9000, "sound": p["music"]},
            },
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "when_dark",
                                            "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
            "minecraft:gameplay/nether_portal_spawns_piglin": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:visual/ambient_light_color": p["ambient_light"],
            "minecraft:visual/cloud_color": p["cloud"],
            "minecraft:visual/cloud_height": 192.33,
            "minecraft:visual/fog_color": p["fog"],
            "minecraft:visual/sky_color": p["sky"],
        },
        "coordinate_scale": 1.0,
        "default_clock": "minecraft:overworld",
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_skylight": True,
        "height": 384,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 384,
        "min_y": -64,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0},
        "timelines": "#minecraft:in_overworld",
    }


FULL = [-1.0, 1.0]


def climate(biome, continentalness=FULL, weirdness=FULL, temperature=FULL, humidity=FULL, erosion=FULL):
    return {"biome": MOD + biome, "parameters": {
        "continentalness": continentalness, "depth": 0.0, "erosion": erosion, "humidity": humidity, "offset": 0.0,
        "temperature": temperature, "weirdness": weirdness}}


# Non-overlapping boxes: seas and river valleys are tarns, the rest splits by humidity (Selvaren); dry sea
# basins and cold uplands are the ashfall, the valley band is the tomb valleys, the rest the redrock (Varkhast).
INLAND = [-0.16, 1.0]
SEA = [-1.2, -0.16]
VALLEY = [-0.05, 0.05]
TOMB_BAND = [-0.1, 0.1]
CLIMATE = {
    "selvaren": [
        climate("selvaren_tarn", SEA),
        climate("selvaren_tarn", INLAND, VALLEY),
        climate("selvaren_highwood", INLAND, [-1.0, -0.05], humidity=[0.0, 1.0]),
        climate("selvaren_highwood", INLAND, [0.05, 1.0], humidity=[0.0, 1.0]),
        climate("selvaren_meadow", INLAND, [-1.0, -0.05], humidity=[-1.0, 0.0]),
        climate("selvaren_meadow", INLAND, [0.05, 1.0], humidity=[-1.0, 0.0]),
    ],
    "varkhast": [
        climate("varkhast_ashfall", SEA),
        climate("varkhast_tomb_valley", INLAND, TOMB_BAND),
        climate("varkhast_redrock", INLAND, [-1.0, -0.1], temperature=[-0.3, 1.0]),
        climate("varkhast_redrock", INLAND, [0.1, 1.0], temperature=[-0.3, 1.0]),
        climate("varkhast_ashfall", INLAND, [-1.0, -0.1], temperature=[-1.0, -0.3]),
        climate("varkhast_ashfall", INLAND, [0.1, 1.0], temperature=[-1.0, -0.3]),
    ],
}


def dimension_json(planet):
    return {"type": MOD + planet, "generator": {
        "type": "minecraft:noise",
        "biome_source": {"type": "minecraft:multi_noise", "biomes": CLIMATE[planet]},
        "settings": MOD + planet,
    }}


def noise_settings_json(planet, router_source):
    p = PLANETS[planet]
    base = dict(router_source)
    base["aquifers_enabled"] = p["aquifers"]
    base["default_block"] = block("minecraft:stone")
    base["default_fluid"] = block("minecraft:water", {"level": "0"})
    base["disable_mob_generation"] = False
    base["legacy_random_source"] = False
    base["ore_veins_enabled"] = True
    base["sea_level"] = p["sea_level"]
    base["surface_rule"] = p["surface"]()
    return base


# ---------------------------------------------------------------------------------------------
# Features
# ---------------------------------------------------------------------------------------------
ORES = [
    "ore_dirt", "ore_gravel", "ore_granite_upper", "ore_granite_lower", "ore_diorite_upper", "ore_diorite_lower",
    "ore_andesite_upper", "ore_andesite_lower", "ore_tuff", "ore_coal_upper", "ore_coal_lower", "ore_iron_upper",
    "ore_iron_middle", "ore_iron_small", "ore_gold", "ore_gold_lower", "ore_redstone", "ore_redstone_lower", "ore_diamond",
    "ore_diamond_medium", "ore_diamond_large", "ore_diamond_buried", "ore_lapis", "ore_lapis_buried", "ore_copper",
]


def vanilla(*names):
    return ["minecraft:" + n for n in names]


def mod(*names):
    return [MOD + n for n in names]


# One master order per planet and step; each biome takes a subsequence (FeatureSorter needs one order).
MASTER = {
    "selvaren": [
        [],
        vanilla("lake_lava_underground"),
        vanilla("amethyst_geode", "forest_rock") + mod("meadow_stones"),
        vanilla("monster_room", "monster_room_deep"),
        [], [],
        vanilla(*ORES) + vanilla("underwater_magma") + mod("ore_aurelite") + vanilla("disk_sand", "disk_clay", "disk_gravel"),
        [],
        vanilla("spring_water", "spring_lava"),
        vanilla("glow_lichen") + mod("highwood_trees", "highwood_saplings", "highwood_spruce", "meadow_trees", "highwood_floor",
                                     "silverleaf_patch", "tarn_shore", "aurelite_cliff")
        + vanilla("patch_large_fern", "patch_grass_taiga", "patch_tall_grass_2", "patch_grass_plain", "patch_berry_common",
                  "brown_mushroom_old_growth", "red_mushroom_old_growth", "patch_sugar_cane", "patch_firefly_bush_near_water"),
        vanilla("freeze_top_layer"),
    ],
    "varkhast": [
        [],
        vanilla("lake_lava_underground"),
        vanilla("amethyst_geode") + mod("hoodoo", "basalt_spire", "obelisk", "carved_wall"),
        vanilla("monster_room", "monster_room_deep"),
        [], [],
        vanilla(*ORES) + vanilla("ore_gold_extra"),
        [],
        vanilla("spring_lava"),
        mod("dead_tree", "ashroot_patch", "duskglass_wall") + vanilla("patch_dead_bush_badlands", "patch_dry_grass_badlands"),
        [],
    ],
}

COMMON_SELVAREN = {2: vanilla("amethyst_geode"), 1: vanilla("lake_lava_underground"), 3: vanilla("monster_room", "monster_room_deep"),
                   6: vanilla(*ORES) + mod("ore_aurelite") + vanilla("disk_sand", "disk_clay", "disk_gravel"),
                   8: vanilla("spring_water", "spring_lava"), 10: vanilla("freeze_top_layer")}
COMMON_VARKHAST = {1: vanilla("lake_lava_underground"), 2: vanilla("amethyst_geode"), 3: vanilla("monster_room", "monster_room_deep"),
                   6: vanilla(*ORES) + vanilla("ore_gold_extra"), 8: vanilla("spring_lava")}


def features(planet, extra):
    """The biome's 11 steps: the planet's common features plus `extra` (step -> ids), in master order."""
    common = COMMON_SELVAREN if planet == "selvaren" else COMMON_VARKHAST
    out = []
    for step, master in enumerate(MASTER[planet]):
        wanted = set(common.get(step, [])) | set(extra.get(step, []))
        unknown = wanted - set(master)
        if unknown:
            raise SystemExit(f"{planet}: features {sorted(unknown)} are not in the master order of step {step}")
        out.append([f for f in master if f in wanted])
    return out


def spawn(entity, weight, lo, hi):
    return {"type": "minecraft:" + entity, "maxCount": hi, "minCount": lo, "weight": weight}


# Vanilla monsters at half the vanilla weight and no creepers (spec 3.2); W6 adds the mod creatures by tag.
SELVAREN_MONSTERS = [spawn("spider", 50, 4, 4), spawn("zombie", 50, 4, 4), spawn("skeleton", 50, 4, 4), spawn("enderman", 5, 1, 4)]


def spawners(creature=(), monster=(), water_ambient=(), water_creature=(), ambient=True):
    return {
        "ambient": [spawn("bat", 10, 8, 8)] if ambient else [],
        "axolotls": [],
        "creature": list(creature),
        "misc": [],
        "monster": list(monster),
        "underground_water_creature": [spawn("glow_squid", 10, 4, 6)],
        "water_ambient": list(water_ambient),
        "water_creature": list(water_creature),
    }


def biome_json(planet, sky, fog, water_color, water_fog, grass, foliage, temperature, downfall, rain, spawn_lists, extra,
               creature_probability=None):
    out = {
        "attributes": {
            "minecraft:visual/fog_color": fog,
            "minecraft:visual/sky_color": sky,
            "minecraft:visual/water_fog_color": water_fog,
        },
        "carvers": vanilla("cave", "cave_extra_underground", "canyon"),
        "downfall": downfall,
        "effects": {"foliage_color": foliage, "grass_color": grass, "water_color": water_color},
        "features": features(planet, extra),
        "has_precipitation": rain,
        "spawn_costs": {},
        "spawners": spawn_lists,
        "temperature": temperature,
    }
    if creature_probability is not None:
        out["creature_spawn_probability"] = creature_probability
    return out


BIOMES = {
    "selvaren_highwood": lambda: biome_json(
        "selvaren", "#8fc6c9", "#b7d3d0", "#4a7f8c", "#2f4f55", "#6f9a6a", "#a9c2a4", 0.6, 0.8, True,
        spawners(creature=[spawn("fox", 8, 2, 4), spawn("rabbit", 4, 2, 3)], monster=SELVAREN_MONSTERS),
        {2: vanilla("forest_rock"),
         9: mod("highwood_trees", "highwood_saplings", "highwood_spruce", "highwood_floor", "silverleaf_patch")
         + vanilla("glow_lichen", "patch_large_fern", "patch_grass_taiga", "patch_berry_common", "brown_mushroom_old_growth",
                   "red_mushroom_old_growth")}),
    "selvaren_meadow": lambda: biome_json(
        "selvaren", "#96ccd2", "#bfd8d4", "#4a7f8c", "#2f4f55", "#9c8796", "#8fae86", 0.5, 0.6, True,
        spawners(creature=[spawn("rabbit", 6, 2, 3), spawn("bee", 2, 2, 3)], monster=SELVAREN_MONSTERS),
        {2: mod("meadow_stones"),
         9: mod("meadow_trees", "silverleaf_patch") + vanilla("glow_lichen", "patch_tall_grass_2", "patch_grass_plain")}),
    "selvaren_tarn": lambda: biome_json(
        "selvaren", "#8cbfc4", "#c9dcd8", "#3d6a73", "#2c4a50", "#6e9474", "#93b39a", 0.5, 0.8, True,
        spawners(creature=[spawn("frog", 8, 2, 5)], monster=SELVAREN_MONSTERS, water_ambient=[spawn("salmon", 6, 1, 5)],
                 water_creature=[spawn("squid", 2, 1, 4)]),
        {6: vanilla("underwater_magma"),
         9: mod("tarn_shore", "aurelite_cliff") + vanilla("glow_lichen", "patch_grass_taiga", "patch_sugar_cane",
                                                         "patch_firefly_bush_near_water")}),
    "varkhast_redrock": lambda: biome_json(
        "varkhast", "#c27a4e", "#8a5a44", "#6d5a4c", "#4a3a30", "#9e7a4d", "#8e7349", 2.0, 0.0, False,
        spawners(monster=[spawn("husk", 80, 4, 4), spawn("spider", 50, 4, 4), spawn("skeleton", 50, 4, 4), spawn("enderman", 5, 1, 4)]),
        {2: mod("hoodoo"), 9: mod("dead_tree", "ashroot_patch") + vanilla("patch_dead_bush_badlands", "patch_dry_grass_badlands")},
        creature_probability=0.03),
    "varkhast_ashfall": lambda: biome_json(
        "varkhast", "#a88874", "#77706b", "#5f5a55", "#3c3835", "#7d766c", "#6f6a62", 2.0, 0.0, False,
        spawners(monster=[spawn("skeleton", 80, 4, 4), spawn("spider", 50, 4, 4), spawn("husk", 30, 2, 4), spawn("enderman", 5, 1, 4)]),
        {2: mod("basalt_spire"), 9: mod("dead_tree") + vanilla("patch_dead_bush_badlands")},
        creature_probability=0.03),
    "varkhast_tomb_valley": lambda: biome_json(
        "varkhast", "#b8704a", "#7a4a3a", "#6d5a4c", "#4a3a30", "#8f6d48", "#7f6646", 2.0, 0.0, False,
        spawners(monster=[spawn("skeleton", 30, 1, 3), spawn("husk", 20, 1, 3), spawn("spider", 20, 1, 2)], ambient=False),
        {2: mod("obelisk", "carved_wall"), 9: mod("duskglass_wall", "ashroot_patch") + vanilla("patch_dead_bush_badlands")},
        creature_probability=0.01),
}


def tree(trunk, leaves, base, rand_a, rand_b, radius, height):
    return {"type": "minecraft:tree", "config": {
        "below_trunk_provider": {"type": "minecraft:rule_based_state_provider", "rules": [{
            "if_true": {"type": "minecraft:not", "predicate": {"type": "minecraft:matching_block_tag",
                                                               "tag": "minecraft:cannot_replace_below_tree_trunk"}},
            "then": {"type": "minecraft:simple_state_provider", "state": block("minecraft:dirt")}}]},
        "decorators": [],
        "foliage_placer": {"type": "minecraft:blob_foliage_placer", "height": height, "offset": 0, "radius": radius},
        "foliage_provider": {"type": "minecraft:simple_state_provider",
                             "state": block(leaves, {"distance": "7", "persistent": "false", "waterlogged": "false"})},
        "ignore_vines": True,
        "minimum_size": {"type": "minecraft:two_layers_feature_size"},
        "trunk_placer": {"type": "minecraft:straight_trunk_placer", "base_height": base, "height_rand_a": rand_a, "height_rand_b": rand_b},
        "trunk_provider": {"type": "minecraft:simple_state_provider", "state": block(trunk, {"axis": "y"})},
    }}


def java_feature(name):
    """A W1 Java feature (planet.PlanetDecorFeature) with no configuration."""
    return {"type": MOD + name, "config": {}}


CONFIGURED = {
    # Silver-barked highwood trees: birch trunks under oak leaves, which take the biome's pale foliage tint.
    "silver_tree": tree("minecraft:birch_log", "minecraft:oak_leaves", 9, 4, 2, 2, 4),
    "silver_tree_young": tree("minecraft:birch_log", "minecraft:oak_leaves", 5, 2, 0, 2, 3),
    "ore_aurelite": {"type": "minecraft:ore", "config": {"discard_chance_on_air_exposure": 0.0, "size": 5, "targets": [
        {"state": block(MOD + "aurelite_ore"), "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
        {"state": block(MOD + "deepslate_aurelite_ore"),
         "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}},
    ]}},
}
JAVA_FEATURES = ["highwood_floor", "meadow_stones", "silverleaf_patch", "tarn_shore", "aurelite_cliff", "hoodoo", "basalt_spire",
                 "obelisk", "carved_wall", "dead_tree", "ashroot_patch", "duskglass_wall"]
for _name in JAVA_FEATURES:
    CONFIGURED[_name] = java_feature(_name)


def count(n):
    return {"type": "minecraft:count", "count": n}


def rarity(chance):
    return {"type": "minecraft:rarity_filter", "chance": chance}


def heightmap(kind):
    return {"type": "minecraft:heightmap", "heightmap": kind}


def height_range(lo, hi):
    return {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "max_inclusive": {"absolute": hi},
                                                          "min_inclusive": {"absolute": lo}}}


IN_SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}
DRY = {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0}


def surface(*extra, kind="MOTION_BLOCKING_NO_LEAVES"):
    return list(extra) + [IN_SQUARE, heightmap(kind), BIOME]


PLACED = {
    "highwood_trees": ("silver_tree", [count(5), IN_SQUARE, DRY, heightmap("OCEAN_FLOOR"), BIOME]),
    "highwood_saplings": ("silver_tree_young", [count(3), IN_SQUARE, DRY, heightmap("OCEAN_FLOOR"), BIOME]),
    "highwood_spruce": ("minecraft:spruce", [rarity(3), IN_SQUARE, DRY, heightmap("OCEAN_FLOOR"), BIOME]),
    "meadow_trees": ("silver_tree", [rarity(9), IN_SQUARE, DRY, heightmap("OCEAN_FLOOR"), BIOME]),
    "ore_aurelite": ("ore_aurelite", [count(5), IN_SQUARE, height_range(-40, 112), BIOME]),
    "highwood_floor": ("highwood_floor", surface(count(2))),
    "meadow_stones": ("meadow_stones", surface(rarity(5))),
    "silverleaf_patch": ("silverleaf_patch", surface(rarity(2))),
    "tarn_shore": ("tarn_shore", surface(count(3), kind="WORLD_SURFACE_WG")),
    "aurelite_cliff": ("aurelite_cliff", [count(6), IN_SQUARE, height_range(50, 150), BIOME]),
    "hoodoo": ("hoodoo", surface(rarity(3))),
    "basalt_spire": ("basalt_spire", surface(rarity(2))),
    "obelisk": ("obelisk", surface(rarity(10))),
    "carved_wall": ("carved_wall", [count(3), IN_SQUARE, height_range(40, 140), BIOME]),
    "dead_tree": ("dead_tree", surface(rarity(3))),
    "ashroot_patch": ("ashroot_patch", surface(count(2))),
    "duskglass_wall": ("duskglass_wall", [count(4), IN_SQUARE, height_range(30, 130), BIOME]),
}


def write_planets(router_path):
    for planet in PLANETS:
        settings_path = os.path.join(DATA, "worldgen", "noise_settings", planet + ".json")
        if router_path:
            with open(router_path, encoding="utf-8") as source:
                vanilla_settings = json.load(source)
        elif os.path.exists(settings_path):
            with open(settings_path, encoding="utf-8") as source:
                vanilla_settings = json.load(source)
        else:
            raise SystemExit(f"{settings_path} is missing: pass --router with the vanilla overworld noise settings (26.2 dump)")
        kept = {k: vanilla_settings[k] for k in ("noise", "noise_router", "spawn_target")}
        write_json(settings_path, noise_settings_json(planet, kept))
        write_json(os.path.join(DATA, "dimension_type", planet + ".json"), dimension_type_json(planet))
        write_json(os.path.join(DATA, "dimension", planet + ".json"), dimension_json(planet))
    for name, make in BIOMES.items():
        write_json(os.path.join(DATA, "worldgen", "biome", name + ".json"), make())
    for name, value in CONFIGURED.items():
        write_json(os.path.join(DATA, "worldgen", "configured_feature", name + ".json"), value)
    for name, (feature, placement) in PLACED.items():
        write_json(os.path.join(DATA, "worldgen", "placed_feature", name + ".json"),
                   {"feature": feature if ":" in feature else MOD + feature, "placement": placement})
    used = {f for planet in MASTER for step in MASTER[planet] for f in step if f.startswith(MOD)}
    missing = {f for f in used if f[len(MOD):] not in PLACED}
    if missing:
        raise SystemExit(f"placed features named in a biome but not written: {sorted(missing)}")
    selvaren = [MOD + b for b in BIOMES if b.startswith("selvaren")]
    varkhast = [MOD + b for b in BIOMES if b.startswith("varkhast")]
    tags = os.path.join(DATA, "tags", "worldgen", "biome")
    write_json(os.path.join(tags, "is_selvaren.json"), {"values": selvaren})
    write_json(os.path.join(tags, "is_varkhast.json"), {"values": varkhast})
    # Every planet's biomes (gen_structures_m5.py adds Ghessar and Tyrvann and writes the same list).
    write_json(os.path.join(tags, "is_planet.json"), {"values": ["#oldrepublic:is_selvaren", "#oldrepublic:is_varkhast",
                                                                 "#oldrepublic:is_ghessar", "#oldrepublic:is_tyrvann"]})
    write_json(os.path.join(tags, "has_structure", "waystone.json"), {"values": ["#oldrepublic:is_selvaren"]})
    write_json(os.path.join(tags, "has_structure", "greater_tomb.json"),
               {"values": [MOD + "varkhast_redrock", MOD + "varkhast_tomb_valley"]})
    print(f"planets: {len(PLANETS)} dimensions, {len(BIOMES)} biomes, {len(CONFIGURED)} configured and {len(PLACED)} placed features")


# =============================================================================================
# Templates (spec 3.4, 3.5, 8.4, 8.5, 10.5)
# =============================================================================================
# Template-local x east, y up, z south. A template with `buried` B stands its feet layer B on the
# ground. Runtime pieces are centred on their stand; an underground piece sits right under its surface
# piece, centred on the same column (PlanetSites.sink), so the shafts below line up at the centre.
RUIN, RUIN_CARVED, RUIN_CRACKED, RUIN_MOSSY = m3.RUIN, m3.RUIN_CARVED, m3.RUIN_CRACKED, m3.RUIN_MOSSY
TOMB, TOMB_CARVED, TOMB_CRACKED = m3.TOMB, m3.TOMB_CARVED, m3.TOMB_CRACKED
TOMB_STAIRS, TOMB_SLAB, SOUL_LANTERN = m3.TOMB_STAIRS, m3.TOMB_SLAB, m3.SOUL_LANTERN
PLATE, SCORCHED, CONDUIT, IMP_PANEL = m3.PLATE, m3.SCORCHED, m3.CONDUIT, m3.IMP_PANEL
SEAL, KEEPER, ECHO_URN, FUNERARY, VEILED, LUMEN = m3.SEAL, m3.KEEPER, m3.ECHO_URN, m3.FUNERARY, m3.VEILED, m3.MOSS
GRASS, DIRT, COARSE, STONE = m3.GRASS, m3.DIRT, m3.COARSE, m3.STONE

WAYSTONE = MOD + "waystone"
WAYSTONE_CORRUPTED = MOD + "waystone_corrupted"
HEARTSTONE = MOD + "heartstone"
SEALED = MOD + "sealed_plating"
HUB_SPAWNER = MOD + "hub_spawner"
PAD_TERMINAL = MOD + "pad_terminal"
CONTRACT_TERMINAL = MOD + "contract_terminal"
CAMP_GATE = MOD + "camp_gate"
KYBER = MOD + "kyber_cluster"
AURELITE = MOD + "aurelite_ore"
DUSKGLASS = MOD + "duskglass_cluster"
BUDDING = MOD + "budding_duskglass"
SILVERLEAF = MOD + "silverleaf"
ASHROOT = MOD + "ashroot"

RED_SAND = "minecraft:red_sand"
RED_SANDSTONE = "minecraft:red_sandstone"
SMOOTH_RED = "minecraft:smooth_red_sandstone"
BLACKSTONE = "minecraft:blackstone"
BLACK_BRICKS = "minecraft:polished_blackstone_bricks"
BLACK_BRICKS_CRACKED = "minecraft:cracked_polished_blackstone_bricks"
MOSSY_SLAB = "minecraft:mossy_stone_brick_slab"
LADDER = "minecraft:ladder"
CHAIN = "minecraft:iron_chain"
SCAFFOLD = "minecraft:scaffolding"
TINTED = "minecraft:tinted_glass"

TOMB_W = MOD + "sith_tomb_weathering"


def slab(half="bottom"):
    return {"type": half, "waterlogged": "false"}


def ladder(facing):
    """A ladder facing `facing`: it hangs on the block on its other side."""
    return {"facing": facing, "waterlogged": "false"}


def wall_post():
    return {"up": "true", "north": "none", "east": "none", "south": "none", "west": "none", "waterlogged": "false"}


def fence(**sides):
    props = {"north": "false", "east": "false", "south": "false", "west": "false", "waterlogged": "false"}
    for side, on in sides.items():
        props[side] = "true" if on else "false"
    return props


def chain_y():
    return {"axis": "y", "waterlogged": "false"}


def cluster(facing="up"):
    return {"facing": facing}


def pick(seed, x, y, z, options):
    """One of `options` [(weight, block), ...] chosen by the position's noise."""
    r = noise(seed, x, y, z) * sum(w for w, _ in options)
    for w, block in options:
        if r < w:
            return block
        r -= w
    return options[-1][1]


def hub_spawner(t, anchor, x, y, z, role):
    """A hub person's spawn point (HubSpawnerBlockEntity: `role`); the cell above stays clear."""
    t.set(x, y, z, HUB_SPAWNER, None, {"id": HUB_SPAWNER, "role": role})
    t.set(x, y + 1, z, AIR)
    t.mark(anchor, x, y, z)


def terminal(t, anchor, x, y, z, block, facing):
    """A hub terminal (W2's contract terminal, W3's pad terminal; `facing` is ignored by a block without it)."""
    t.set(x, y, z, block, {"facing": facing})
    t.set(x, y + 1, z, AIR)
    t.mark(anchor, x, y, z)


def tent_at(t, base, x0, z0, x1, z1, cloth, trim, door="west"):
    """A ridge tent (ridge along x, 5 wide in z) with a plank floor on layer `base` and its door in the `door` end."""
    zc = (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, base, z, "minecraft:spruce_planks")
            h = 3 - abs(z - zc)
            for y in range(1, h + 1):
                if y == h:
                    t.set(x, base + y, z, cloth if x not in (x0, x1) else trim)
                elif x in (x0, x1):
                    t.set(x, base + y, z, trim)
                else:
                    t.set(x, base + y, z, AIR)
    dx = x0 if door == "west" else x1
    t.set(dx, base + 1, zc, AIR)
    t.set(dx, base + 2, zc, AIR)


def lamp_post(t, x, y, z, height=3, block="minecraft:spruce_fence", soul=False):
    """A post with a lantern on top."""
    for dy in range(height):
        t.set(x, y + dy, z, block, fence() if block.endswith("fence") else wall_post())
    m3.lantern(t, x, y + height, z, soul=soul)


def shell_below(t, thickness, rock_fn, max_y):
    """Rock around every air cell (within `thickness`) on unset cells up to layer `max_y`."""
    open_cells = [p for p, c in t.cells.items() if c[0] in (AIR, CAVE_AIR)]
    for (x, y, z) in open_cells:
        for dx in range(-thickness, thickness + 1):
            for dy in range(-thickness, thickness + 1):
                for dz in range(-thickness, thickness + 1):
                    p = (x + dx, y + dy, z + dz)
                    if p not in t.cells and t.inside(*p) and p[1] <= max_y:
                        t.set(*p, rock_fn(*p))


def fill_unset(t, fn, y0=0, y1=None):
    sx, sy, sz = t.size
    for x in range(sx):
        for z in range(sz):
            for y in range(y0, sy if y1 is None else y1 + 1):
                if (x, y, z) not in t.cells:
                    t.set(x, y, z, fn(x, y, z))


def air_above_unset(t, y0, keep=None):
    """Air on every unset cell from layer y0 up, except columns `keep(x, z)` (left to the natural ground)."""
    sx, sy, sz = t.size
    for x in range(sx):
        for z in range(sz):
            if keep and keep(x, z):
                continue
            for y in range(y0, sy):
                t.keep(x, y, z, AIR)


SOIL = {GRASS, DIRT, COARSE, "minecraft:podzol", "minecraft:moss_block", RED_SAND, "minecraft:mud"}


def plants(t, y, seed, options, cells):
    """Plants on layer y over `cells` where the layer is free and the block below is soil."""
    for (x, z) in cells:
        if (x, y, z) in t.cells or t.get(x, y - 1, z) not in SOIL:
            continue
        block = pick(seed, x, y, z, options)
        if block is not None:
            t.set(x, y, z, block)


# ---------------------------------------------------------------------------------------------
# Watch waystone (worldgen, Selvaren; 3.4): the clean template, and the corrupted one Waystones.decide
# stamps over a stone that rolls corruption (same size, same stone cell, same layout).
# ---------------------------------------------------------------------------------------------
WAYSTONE_SIZE = (11, 9, 11)
WAYSTONE_STONE = (5, 3, 5)


def build_waystone(corrupted):
    t = Site("planet/waystone_corrupted" if corrupted else "planet/waystone", WAYSTONE_SIZE, 2)

    def stone(x, y, z, block):
        """Ring and monolith stone; on the corrupted waystone dark veins run through a third of it."""
        if corrupted:
            r = noise(117, x, y, z)
            if r < 0.2:
                return BLACKSTONE
            if r < 0.34:
                return BLACK_BRICKS_CRACKED
        return block

    top_slab = "minecraft:blackstone_slab" if corrupted else MOSSY_SLAB
    # The standing stone: a carved base, the waystone as its heart (its carved face north), two stones above, a
    # capstone; a second course behind it gives the stone its depth.
    sx, sy, sz = WAYSTONE_STONE
    t.set(sx, 2, sz, stone(sx, 2, sz, RUIN_CARVED))
    t.set(sx, sy, sz, WAYSTONE_CORRUPTED if corrupted else WAYSTONE, {"facing": "north"},
          {"id": MOD + "waystone", "pending": Byte(0 if corrupted else 1), "local": m3.IntArray([sx, sy, sz])})
    t.mark("STONE", sx, sy, sz)
    t.set(sx, 4, sz, stone(sx, 4, sz, RUIN))
    t.set(sx, 5, sz, stone(sx, 5, sz, RUIN_MOSSY))
    t.set(sx, 6, sz, top_slab, slab())
    for y in (2, 3, 4):
        t.set(sx, y, sz + 1, stone(sx, y, sz + 1, RUIN if y < 4 else RUIN_MOSSY))
    t.set(sx, 5, sz + 1, top_slab, slab())
    for x, z in ((sx - 1, sz), (sx + 1, sz), (sx - 1, sz + 1), (sx + 1, sz + 1), (sx, sz + 2)):
        t.set(x, 2, z, top_slab, slab())
    # Old flagstones around it, half grown over.
    for x in range(2, 9):
        for z in range(2, 9):
            d = math.hypot(x - sx, (z - sz - 0.5) * 1.1)
            if d <= 2.6 and noise(103, x, 1, z) > 0.3:
                t.set(x, 1, z, stone(x, 1, z, pick(105, x, 1, z, [(5, RUIN), (2, RUIN_MOSSY), (2, RUIN_CRACKED)])))
    # The ring of fallen stones.
    for x, z, h in ((5, 1, 2), (9, 5, 3), (2, 8, 2), (2, 2, 1)):
        for y in range(2, 2 + h):
            t.set(x, y, z, stone(x, y, z, pick(107, x, y, z, [(3, RUIN), (2, RUIN_MOSSY), (1, RUIN_CRACKED)])))
    for x, z in ((8, 2), (9, 2), (4, 9), (5, 9), (6, 9), (1, 4), (1, 5)):
        t.set(x, 2, z, stone(x, 2, z, pick(109, x, 2, z, [(2, RUIN_MOSSY), (2, RUIN_CRACKED), (1, RUIN)])))
    t.set(6, 1, 9, stone(6, 1, 9, RUIN_CRACKED))
    t.set(1, 3, 5, top_slab, slab())
    # The cache cairn (south-east): a chest in a mound of stones, its north face a veiled stone Sense reveals.
    t.chest("CACHE", 8, 2, 8, "north", "force_cache")
    for x, z in ((9, 8), (7, 8), (8, 9), (9, 9), (7, 9)):
        t.set(x, 2, z, stone(x, 2, z, RUIN if (x + z) % 2 else RUIN_MOSSY))
    t.set(8, 3, 8, stone(8, 3, 8, RUIN_MOSSY))
    t.set(8, 3, 9, top_slab, slab())
    t.set(9, 3, 8, top_slab, slab())
    t.veiled(8, 2, 7, "ruin")
    t.set(8, 3, 7, top_slab, slab())
    t.spot("HEART", 5, 2, 3)
    if corrupted:
        # The twisted hollowhorns it wakes (SpawnerGroups: `bound` marks them corrupted).
        t.spawner("GUARD", 3, 2, 7, "hollowhorns", count=2, bound=True)

    # Ground: grass with moss beds; a corrupted stone has wilted it to coarse dirt and podzol.
    def top(x, z):
        r = noise(111, x, 1, z)
        if corrupted:
            return COARSE if r < 0.4 else ("minecraft:podzol" if r < 0.7 else GRASS)
        return "minecraft:moss_block" if r < 0.12 else GRASS

    m3.ground_layers(t, 1, 113, top_block=top, under=DIRT, deep=STONE)
    cells = [(x, z) for x in range(11) for z in range(11)]
    if corrupted:
        plants(t, 2, 115, [(6, "minecraft:short_dry_grass"), (3, "minecraft:dead_bush"), (11, None)], cells)
    else:
        plants(t, 2, 115, [(4, "minecraft:short_grass"), (1, "minecraft:fern"), (1, "minecraft:azure_bluet"), (0.4, SILVERLEAF),
                           (0.6, "minecraft:moss_carpet"), (13, None)], cells)
    clear_above(t, 2)
    return t


# ---------------------------------------------------------------------------------------------
# Greater tomb (worldgen, Varkhast; 3.4): gate (start, the Imperial dig) -> descent -> urn vault ->
# sealed crypt. The vault's south door is sealed plating, so the crypt (W2: the relic piece) is
# reached only through the fusion cutter or a Push.
# ---------------------------------------------------------------------------------------------
def red_ground(seed):
    def top(x, z):
        return pick(seed, x, 0, z, [(7, RED_SAND), (2, SMOOTH_RED), (1, COARSE)])
    return top


def build_tomb_gate():
    t = Site("greater_tomb/gate", (21, 14, 21), 3)
    stone = m3.tomb_stone(201)
    # The cut stair: the mouth at z 8 (stand 3), down to stand 1 at z 10, a walled trench to the south edge.
    for z in range(8, 21):
        s = {8: 3, 9: 2}.get(z, 1)
        for x in range(8, 13):
            for y in range(s, 14):
                t.set(x, y, z, AIR)
            for y in range(0, s - 1):
                t.set(x, y, z, stone(x, y, z))
            if z in (9, 10):
                t.set(x, s - 1, z, TOMB_STAIRS, stairs("north"))
            else:
                t.set(x, s - 1, z, stone(x, s - 1, z) if z == 8 or (x + z) % 5 else TOMB_CARVED)
        if z >= 9:
            for x in (7, 13):
                for y in range(0, 3):
                    t.set(x, y, z, TOMB_CARVED if y == 2 and z % 3 == 0 else stone(x, y, z))
                t.set(x, 3, z, TOMB_SLAB, BOTTOM)
    t.mark("STAIR_MOUTH", 10, 3, 8)
    t.jig(10, 1, 20, "south_up", "tomb_down", "descent_top", "greater_tomb/descent")
    # The obelisk gateway over the mouth: two 2x2 pillars from deep in the ground, a lintel, tapering tips.
    for x0 in (6, 13):
        for x in (x0, x0 + 1):
            for z in (6, 7):
                for y in range(0, 10):
                    t.set(x, y, z, TOMB_CARVED if y in (4, 8) else stone(x, y, z))
    for x in range(6, 15):
        for z in (6, 7):
            t.set(x, 10, z, TOMB_CARVED)
    for x in range(8, 13):
        for z in (6, 7):
            for y in range(3, 10):
                t.set(x, y, z, AIR)
            t.set(x, 2, z, TOMB)
    for y, block in ((11, TOMB), (12, TOMB_CARVED)):
        t.set(6, y, 6, block)
        t.set(14, y, 7, block)
    t.set(6, 13, 6, TOMB_SLAB, BOTTOM)
    t.set(14, 13, 7, TOMB_SLAB, BOTTOM)
    # A toppled obelisk east of the gate.
    for x in range(16, 20):
        t.set(x, 3, 8, TOMB_CARVED if x == 16 else stone(x, 3, 8))
    t.set(17, 4, 8, TOMB_SLAB, BOTTOM)
    # Paving under drifted red sand around the gate.
    for x in range(3, 18):
        for z in range(1, 9):
            if (x, 2, z) not in t.cells and noise(203, x, 2, z) > 0.4:
                t.set(x, 2, z, stone(x, 2, z))
    # The Imperial dig: two tents west of the trench, the excavation east, crates, lamps, the droids' post.
    tent_at(t, 2, 1, 10, 6, 14, "minecraft:gray_wool", "minecraft:red_wool")
    tent_at(t, 2, 1, 15, 6, 19, "minecraft:gray_wool", "minecraft:red_wool")
    t.chest("CHEST_1", 2, 3, 11, "south", "sith_tomb")
    t.set(2, 3, 13, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(4, 3, 16, "minecraft:red_bed", {"facing": "south", "occupied": "false", "part": "foot"})
    t.set(4, 3, 17, "minecraft:red_bed", {"facing": "south", "occupied": "false", "part": "head"})
    for x in range(15, 20):
        for z in range(11, 19):
            t.set(x, 2, z, "minecraft:gravel" if noise(205, x, 2, z) > 0.4 else COARSE)
    for x in range(16, 19):
        for z in range(13, 17):
            t.set(x, 2, z, AIR)
            t.set(x, 1, z, AIR if (x, z) != (17, 15) else TOMB_CARVED)
    t.set(16, 0, 14, TOMB_CARVED)
    for x, z in ((19, 12), (15, 18), (19, 17)):
        t.set(x, 3, z, TOMB_CRACKED)
    for x, y, z in ((15, 3, 10), (16, 3, 10), (15, 4, 10), (19, 3, 19), (18, 3, 19)):
        t.set(x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(17, 3, 4, "minecraft:campfire", campfire())
    t.set(16, 3, 3, "minecraft:spruce_slab", BOTTOM)
    t.set(18, 3, 5, "minecraft:spruce_slab", BOTTOM)
    for x, z in ((4, 4), (16, 7), (4, 20)):
        lamp_post(t, x, 3, z)
    t.spawner("SQUAD", 15, 3, 3, "dig_team")
    t.spawner("GUARD", 17, 3, 9, "security_droids", count=2)
    t.spot("SURFACE_GUARD", 10, 3, 3)
    for x, z in ((1, 2), (19, 1), (12, 1), (0, 8)):
        t.set(x, 3, z, "minecraft:dead_bush")
    m3.ground_layers(t, 2, 207, top_block=red_ground(209), under=RED_SANDSTONE, deep=RED_SANDSTONE)
    clear_above(t, 3)
    return t


DESCENT_FLAT = 14


def descent_stand(z):
    return 15 if z <= 1 else max(2, 15 - (z - 1))


def build_tomb_descent():
    t = Site("greater_tomb/descent", (11, 18, 27), 0)
    stone = m3.tomb_stone(211)
    for z in range(27):
        s = descent_stand(z)
        open_top = s >= 13
        ceiling = 17 if open_top else (min(16, s + 4) if z <= DESCENT_FLAT else 7)
        for x in range(3, 8):
            for y in range(s, ceiling + (1 if open_top else 0)):
                t.set(x, y, z, AIR)
            if 2 <= z <= DESCENT_FLAT:
                t.set(x, s - 1, z, TOMB_STAIRS, stairs("north"))
            else:
                t.set(x, s - 1, z, TOMB_CARVED if (x + z) % 4 == 0 else stone(x, s - 1, z))
            if not open_top:
                t.set(x, ceiling, z, TOMB_CARVED if z % 4 == 0 else stone(x, ceiling, z))
        for x in (2, 8):
            top = 16 if open_top else ceiling
            for y in range(s - 1, top + 1):
                t.set(x, y, z, TOMB_CARVED if (z % 4 == 0 and y < top) else stone(x, y, z))
            if open_top:
                t.set(x, 17, z, TOMB_SLAB, BOTTOM)
    # The lintel where the trench dives under the ground.
    for x in range(2, 9):
        t.set(x, 16, 4, TOMB_CARVED)
    # The lower gallery: alcoves with funerary urns, a sentinel, soul lanterns.
    for z0 in (17, 22):
        for z in range(z0, z0 + 3):
            for x in (1, 2, 8, 9):
                for y in range(2, 5):
                    t.set(x, y, z, AIR)
                t.set(x, 1, z, stone(x, 1, z))
            for x in (0, 10):
                for y in range(1, 6):
                    t.set(x, y, z, stone(x, y, z))
    for x, z in ((1, 18), (9, 18), (1, 23), (9, 24)):
        t.set(x, 2, z, FUNERARY)
    t.spawner("SENTINEL_1", 5, 2, 21, "tomb")
    for z in (8, 12):
        t.set(5, min(16, descent_stand(z) + 4) - 1, z, SOUL_LANTERN, LANTERN_HUNG)
    for z in (17, 24):
        t.set(5, 6, z, SOUL_LANTERN, LANTERN_HUNG)
    t.jig(5, 15, 0, "north_up", "descent_top", "tomb_down", "minecraft:empty")
    for x in range(4, 7):
        for y in range(2, 5):
            t.set(x, y, 26, AIR)
    t.jig(5, 2, 26, "south_up", "descent_down", "vault_entry", "greater_tomb/vault")
    shell_below(t, 2, stone, 16)
    return t


VAULT_KEEPER = (5, 2, 14)
VAULT_URNS = [(4, 3, 4), (16, 3, 4), (16, 3, 11), (11, 3, 8)]
VAULT_SEAL = [(6, 2, 14), (6, 3, 14)]


def build_urn_vault():
    t = Site("greater_tomb/urn_vault", (21, 12, 21), 0)
    stone = m3.tomb_stone(221)
    t.box(0, 0, 0, 20, 11, 20, stone)
    t.air(2, 2, 2, 18, 8, 18)
    # A stepped vault: a carved cornice, the ceiling rising toward the middle.
    for x in range(2, 19):
        for z in range(2, 19):
            edge = min(x - 2, 18 - x, z - 2, 18 - z)
            if edge == 0:
                t.set(x, 8, z, TOMB_CARVED)
            elif edge >= 3:
                t.set(x, 9, z, AIR)
            if edge >= 6:
                t.set(x, 10, z, AIR)
            if x % 4 == 2 and z % 4 == 2:
                t.set(x, 1, z, TOMB_CARVED)
    for x, z in ((6, 6), (14, 6), (14, 14)):
        for y in range(2, 9):
            t.set(x, y, z, TOMB_CARVED if y in (2, 8) else TOMB)
    # Entry from the descent.
    for x in range(9, 12):
        for y in range(2, 5):
            t.set(x, y, 0, AIR)
            t.set(x, y, 1, AIR)
    t.jig(10, 2, 0, "north_up", "vault_entry", "descent_down", "minecraft:empty")
    # The treasury (south-west), behind the keeper's wall: the keeper's seal opens it.
    t.box(2, 1, 14, 7, 6, 18, TOMB)
    t.air(3, 2, 15, 6, 5, 17)
    kx, ky, kz = VAULT_KEEPER
    offsets = []
    for (ux, uy, uz) in VAULT_URNS:
        offsets += [ux - kx, uy - ky, uz - kz]
    t.set(kx, ky, kz, KEEPER, {"facing": "north", "solved": "false"}, {"id": KEEPER, "urns": m3.IntArray(offsets)})
    t.mark("URN_KEEPER", kx, ky, kz)
    t.set(kx, ky + 1, kz, TOMB_CARVED)
    for (x, y, z) in VAULT_SEAL:
        t.seal(x, y, z, "tomb")
    t.chest("CHEST_1", 4, 2, 17, "north", "greater_tomb")
    t.set(3, 2, 17, FUNERARY)
    t.set(6, 2, 17, FUNERARY)
    t.set(4, 4, 16, SOUL_LANTERN, LANTERN_HUNG)
    # Four echo urns on plinths.
    for i, (x, y, z) in enumerate(VAULT_URNS):
        t.set(x, y - 1, z, TOMB_CARVED)
        t.set(x, y, z, ECHO_URN, {"facing": "north", "index": str(i)})
        t.mark(f"URN_{i + 1}", x, y, z)
    t.spawner("SENTINEL_1", 8, 2, 11, "tomb")
    t.spawner("SENTINEL_2", 13, 2, 15, "tomb")
    for x, z in ((3, 3), (18, 3), (18, 18), (13, 3)):
        t.set(x, 2, z, FUNERARY)
    for x, z in ((10, 6), (10, 13), (5, 10), (15, 10)):
        t.set(x, 9, z, SOUL_LANTERN, LANTERN_HUNG)
    # The crypt door: a sheet of sealed plating, then the way on to the crypt.
    for x in range(9, 12):
        for y in range(2, 5):
            t.set(x, y, 19, SEALED)
            t.set(x, y, 20, AIR)
    t.mark("DOOR_SEAL", 10, 2, 19)
    t.jig(10, 2, 20, "south_up", "vault_exit", "crypt_entry", "greater_tomb/crypt")
    m3.check_redstone(t, [((), ()), ((VAULT_KEEPER,), tuple(VAULT_SEAL))])
    return t


def build_sealed_crypt():
    t = Site("greater_tomb/sealed_crypt", (21, 12, 21), 0)
    stone = m3.tomb_stone(231)
    t.box(0, 0, 0, 20, 11, 20, stone)
    for x in range(9, 12):
        for y in range(2, 5):
            for z in range(0, 3):
                t.set(x, y, z, AIR)
    t.jig(10, 2, 0, "north_up", "crypt_entry", "vault_exit", "minecraft:empty")
    t.air(2, 2, 3, 18, 9, 18)
    for x in range(2, 19):
        for z in range(3, 19):
            if min(x - 2, 18 - x) == 0 or min(z - 3, 18 - z) == 0:
                t.set(x, 9, z, TOMB_CARVED)
    # Ribs across the ceiling and niches of urns in the side walls.
    for z in range(4, 18, 3):
        for x in range(3, 18):
            t.set(x, 9, z, TOMB_CARVED)
        for x in (1, 19):
            for y in (2, 3):
                t.set(x, y, z, AIR)
            t.set(x, 2, z, FUNERARY)
    # The dais of the tomb's lord: steps, the sarcophagus, the empty place the relic lay.
    for x in range(6, 15):
        for z in range(14, 19):
            t.set(x, 2, z, TOMB_CARVED if x in (6, 14) or z == 14 else TOMB)
    for x in range(7, 14):
        t.set(x, 2, 13, TOMB_STAIRS, stairs("south"))
    for x in range(8, 13):
        for z in (16, 17):
            t.set(x, 3, z, TOMB_CARVED if x in (8, 12) else TOMB)
            t.set(x, 4, z, TOMB_SLAB, BOTTOM)
    t.set(10, 3, 15, "minecraft:candle", {"candles": "4", "lit": "true", "waterlogged": "false"})
    t.mark("RELIC", 10, 3, 15)
    t.spot("HEART", 10, 2, 10)
    t.spawner("SENTINEL_1", 5, 2, 11, "tomb")
    t.spawner("SENTINEL_2", 15, 2, 11, "tomb")
    t.chest("CHEST_1", 16, 3, 16, "west", "greater_tomb")
    t.set(16, 2, 16, TOMB_CARVED)
    for x, z in ((6, 7), (14, 7), (10, 10)):
        t.set(x, 8, z, SOUL_LANTERN, LANTERN_HUNG)
    return t




# ---------------------------------------------------------------------------------------------
# Hubs (3.5): placed once per server by PlanetHubs (buried 3). HEART is the pad's centre, the four
# PAD_BAY spots are the ships' landing spots, MEDBAY the respawn spot; the people stand on hub_spawner
# blocks whose roles W2 (and W6 for the droid) register in HubSpawners.
# ---------------------------------------------------------------------------------------------
HUB_SIZE_GREENWATCH = (63, 24, 63)
HUB_SIZE_PROVING = (63, 28, 63)


def birch_tree(t, x, y, z, height, seed):
    """A pale highwood tree: a birch trunk and a ragged crown of persistent leaves."""
    for dy in range(height):
        t.set(x, y + dy, z, "minecraft:birch_log", log("y"))
    top = y + height - 1
    for dy in range(-2, 2):
        r = 2 if dy < 1 else 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if (dx, dz) == (0, 0) and dy < 1:
                    continue
                if abs(dx) == r and abs(dz) == r and noise(seed, dx, dy, dz) < 0.6:
                    continue
                t.keep(x + dx, top + dy, z + dz, "minecraft:birch_leaves", LEAVES)


def path_block(seed):
    def fn(x, y, z):
        return pick(seed, x, y, z, [(4, "minecraft:gravel"), (3, COARSE), (3, RUIN_MOSSY), (3, "minecraft:dirt_path")])
    return fn


def build_greenwatch():
    t = Site("hub/greenwatch", HUB_SIZE_GREENWATCH, 3)
    ruin = lambda x, y, z: pick(301, x, y, z, [(10, RUIN), (3, RUIN_CRACKED), (4, RUIN_MOSSY)])  # noqa: E731

    # --- The landing pad: moss-cracked stone, four bays split by carved walkways, low walls with lamps.
    for x in range(17, 46):
        for z in range(33, 60):
            if x == 31 or z == 46:
                t.set(x, 2, z, RUIN_CARVED)
            else:
                t.set(x, 2, z, pick(303, x, 2, z, [(10, RUIN), (3, RUIN_CRACKED), (3, RUIN_MOSSY), (1.5, "minecraft:moss_block"),
                                                   (1, "minecraft:mossy_cobblestone")]))
            edge = x in (17, 45) or z in (33, 59)
            if edge and (x + z) % 4 == 0 and not (29 <= x <= 33 or 44 <= z <= 48):
                t.set(x, 3, z, "minecraft:mossy_stone_brick_wall", wall_post())
                if (x + z) % 8 == 0:
                    m3.lantern(t, x, 4, z)
    t.spot("HEART", 31, 3, 46)
    for i, (x, z) in enumerate(((24, 39), (38, 39), (24, 53), (38, 53))):
        t.spot(f"PAD_BAY_{i + 1}", x, 3, z)
    terminal(t, "PAD_TERMINAL", 44, 3, 47, PAD_TERMINAL, "west")

    # --- The hall: a half-ruined stone hall under a broken dome.
    for x in range(20, 43):
        for z in range(4, 27):
            t.set(x, 2, z, RUIN_CARVED if x in (21, 41) or z in (5, 25) else ruin(x, 2, z))
            wall = x in (20, 42) or z in (4, 26)
            if wall:
                loss = int(noise(305, x // 3, 0, z // 3) * 5) if noise(307, x // 3, 0, z // 3) > 0.55 else 0
                for y in range(3, 11 - loss):
                    t.set(x, y, z, RUIN_CARVED if y == 3 else ruin(x, y, z))
            else:
                for y in range(3, 11):
                    t.set(x, y, z, AIR)
    for x, z in ((20, 9), (20, 15), (20, 21), (42, 9), (42, 15), (42, 21)):
        for y in range(5, 8):
            t.set(x, y, z, AIR)
        t.set(x, 4, z, RUIN_CARVED)
    for x in range(29, 34):
        for y in range(3, 8):
            t.set(x, y, 26, AIR)
    for y in range(3, 9):
        t.set(28, y, 26, RUIN_CARVED)
        t.set(34, y, 26, RUIN_CARVED)
    for x in range(28, 35):
        t.set(x, 8, 26, RUIN_CARVED)
    lamps = ((24, 6), (38, 6), (24, 24), (38, 24))
    for x in range(21, 42):
        for z in range(5, 26):
            r = math.hypot(x - 31, z - 15)
            if r < 10.6:
                continue
            if (x, z) in lamps or noise(309, x // 2, 11, z // 2) > 0.25:
                t.set(x, 11, z, MOSSY_SLAB, slab())
    for x in range(20, 43):
        for z in range(4, 27):
            r = math.hypot(x - 31, z - 15)
            if 10.0 <= r < 11.2:
                t.set(x, 11, z, RUIN_CARVED)
            for y in range(12, 22):
                d = math.sqrt((x - 31) ** 2 + (y - 11) ** 2 + (z - 15) ** 2)
                if 10.0 <= d < 11.0 and noise(311, x, y, z) > 0.3 and not (x >= 35 and y >= 15):
                    t.set(x, y, z, ruin(x, y, z))
    for x, z in lamps:
        t.set(x, 10, z, "minecraft:lantern", LANTERN_HUNG)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        px, pz = round(31 + 7.5 * math.cos(a)), round(15 + 7.5 * math.sin(a))
        for y in range(3, 11):
            t.set(px, y, pz, RUIN_CARVED if y in (3, 10) else ruin(px, y, pz))
    for x in range(28, 35):
        t.set(x, 3, 5, RUIN_CARVED)
    m3.lantern(t, 28, 4, 5)
    m3.lantern(t, 34, 4, 5)
    hub_spawner(t, "HOST", 31, 3, 7, "host")
    hub_spawner(t, "VENDOR_1", 22, 3, 10, "vendor:watch_quartermaster")
    hub_spawner(t, "VENDOR_2", 40, 3, 10, "vendor:trainer_medtech")
    hub_spawner(t, "VENDOR_3", 40, 3, 20, "vendor:trainer_artifice")
    for x, y, z in ((21, 3, 8), (21, 3, 12), (21, 4, 8)):
        t.set(x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(41, 3, 8, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
    t.set(41, 3, 12, "minecraft:cauldron")
    t.set(41, 3, 18, "minecraft:smithing_table")
    t.set(41, 3, 22, "minecraft:crafting_table")
    terminal(t, "CONTRACT_TERMINAL", 27, 3, 24, CONTRACT_TERMINAL, "north")
    for x in range(21, 42):
        for z in range(5, 26):
            if (x, 3, z) not in t.cells or t.get(x, 3, z) == AIR:
                if (x in (21, 41) or z in (5, 25)) and noise(313, x, 3, z) > 0.7 and (x, 2, z) in t.cells:
                    t.set(x, 3, z, "minecraft:moss_carpet" if noise(315, x, 3, z) > 0.4 else "minecraft:fern")
    for x in range(20, 43):
        for z in range(4, 27):
            if (x in (20, 42) or z in (4, 26)) and noise(317, x, 0, z) > 0.75:
                y = 3
                while (x, y, z) in t.cells and t.get(x, y, z) not in (AIR,):
                    y += 1
                if y > 4:
                    t.set(x, y, z, "minecraft:birch_leaves", LEAVES)

    # --- The cloister and its garden (west).
    for x in range(2, 17):
        for z in range(4, 31):
            walk = x <= 3 or x >= 15 or z <= 5 or z >= 29
            if walk:
                t.set(x, 2, z, ruin(x, 2, z))
                roofed = noise(319, x // 2, 7, z // 2) > 0.22
                if roofed and (x <= 4 or x >= 14 or z <= 6 or z >= 28):
                    t.set(x, 7, z, MOSSY_SLAB, slab())
                    if noise(321, x, 8, z) > 0.72:
                        t.set(x, 8, z, "minecraft:birch_leaves", LEAVES)
            if x in (4, 14) and 6 <= z <= 28 and z % 3 == 0 or z in (6, 28) and 4 <= x <= 14 and x % 3 == 1:
                for y in range(3, 6):
                    t.set(x, y, z, ruin(x, y, z))
                t.set(x, 6, z, RUIN_CARVED)
                t.set(x, 7, z, MOSSY_SLAB, slab())
    for z in range(3, 32):
        if noise(323, 1, 0, z) > 0.3:
            t.set(1, 3, z, ruin(1, 3, z))
    for x in range(1, 17):
        for z in (3, 31):
            if noise(325, x, 0, z) > 0.3:
                t.set(x, 3, z, ruin(x, 3, z))
    for x in range(8, 11):
        for z in range(15, 19):
            t.set(x, 1, z, RUIN)
            t.set(x, 2, z, "minecraft:water", {"level": "0"})
            if noise(327, x, 3, z) > 0.6:
                t.set(x, 3, z, "minecraft:lily_pad")
    for x in range(7, 12):
        for z in range(14, 20):
            if (x, 2, z) not in t.cells:
                t.set(x, 2, z, RUIN_MOSSY if (x + z) % 2 else RUIN)
    birch_tree(t, 11, 3, 10, 7, 329)
    birch_tree(t, 7, 3, 24, 6, 331)
    garden = [(x, z) for x in range(5, 14) for z in range(7, 28)]

    # --- The medbay (west of the pad): a small stone house with beds; the respawn spot inside.
    for x in range(4, 14):
        for z in range(35, 46):
            t.set(x, 2, z, ruin(x, 2, z))
            wall = x in (4, 13) or z in (35, 45)
            for y in range(3, 7):
                t.set(x, y, z, (RUIN_CARVED if y == 3 else ruin(x, y, z)) if wall else AIR)
            t.set(x, 7, z, MOSSY_SLAB, slab())
    for z in range(39, 42):
        for y in range(3, 6):
            t.set(13, y, z, AIR)
    for x in (4,):
        for z in (38, 42):
            t.set(x, 5, z, AIR)
    for z in (37, 43):
        t.set(5, 3, z, "minecraft:white_bed", {"facing": "west", "occupied": "false", "part": "head"})
        t.set(6, 3, z, "minecraft:white_bed", {"facing": "west", "occupied": "false", "part": "foot"})
    t.set(5, 3, 40, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
    t.set(5, 3, 41, "minecraft:cauldron")
    t.set(9, 6, 40, "minecraft:lantern", LANTERN_HUNG)
    t.spot("MEDBAY", 9, 3, 40)

    # --- The apprentices' tents, their fire and a sparring yard (east).
    tent_at(t, 2, 48, 6, 53, 10, "minecraft:white_wool", "minecraft:green_wool")
    tent_at(t, 2, 48, 13, 53, 17, "minecraft:white_wool", "minecraft:green_wool")
    tent_at(t, 2, 55, 6, 60, 10, "minecraft:white_wool", "minecraft:green_wool")
    t.set(57, 3, 13, "minecraft:campfire", campfire())
    t.set(57, 3, 15, "minecraft:birch_log", log("x"))
    t.set(59, 3, 13, "minecraft:birch_log", log("z"))
    hub_spawner(t, "RESIDENT_1", 50, 3, 12, "apprentice")
    hub_spawner(t, "RESIDENT_2", 55, 3, 14, "apprentice")
    hub_spawner(t, "RESIDENT_3", 54, 3, 24, "apprentice")
    for x in range(50, 60):
        for z in range(20, 30):
            if x in (50, 59) or z in (20, 29):
                if not (x == 50 and 23 <= z <= 25):
                    ns = x in (50, 59)
                    t.set(x, 3, z, "minecraft:spruce_fence", fence(north=ns and z > 20, south=ns and z < 29,
                                                                   east=not ns and x < 59, west=not ns and x > 50))
            else:
                t.set(x, 2, z, COARSE if noise(333, x, 2, z) > 0.5 else "minecraft:dirt_path")
    for x, z in ((57, 22), (57, 27)):
        t.set(x, 3, z, "minecraft:spruce_fence", fence())
        t.set(x, 4, z, "minecraft:hay_block", {"axis": "y"})

    # --- The spacer's stall (east of the pad).
    for x in range(48, 57):
        for z in range(38, 47):
            t.set(x, 2, z, "minecraft:gravel" if noise(335, x, 2, z) > 0.5 else COARSE)
            t.set(x, 6, z, "minecraft:brown_wool" if (x // 2) % 2 else "minecraft:light_gray_wool")
    for x, z in ((48, 38), (56, 38), (48, 46), (56, 46)):
        for y in range(3, 6):
            t.set(x, y, z, "minecraft:spruce_fence", fence())
    for x, y, z in ((50, 3, 39), (51, 3, 39), (50, 4, 39), (54, 3, 45), (55, 3, 45), (55, 3, 44)):
        t.set(x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(53, 5, 42, "minecraft:lantern", LANTERN_HUNG)
    hub_spawner(t, "VENDOR_4", 52, 3, 42, "vendor:spacer")

    # --- Paths and lamps.
    path = path_block(337)
    for x in range(29, 34):
        for z in range(27, 33):
            t.set(x, 2, z, path(x, 2, z))
    for x in range(14, 17):
        for z in range(39, 42):
            t.set(x, 2, z, path(x, 2, z))
    for x in range(46, 48):
        for z in range(41, 44):
            t.set(x, 2, z, path(x, 2, z))
    for x in range(17, 29):
        for z in range(27, 30):
            t.set(x, 2, z, path(x, 2, z))
    for x in range(43, 48):
        for z in range(11, 14):
            t.set(x, 2, z, path(x, 2, z))
    for x, z in ((28, 30), (34, 30), (16, 38), (47, 40), (18, 26), (44, 10)):
        lamp_post(t, x, 3, z, 3, "minecraft:mossy_stone_brick_wall")

    # --- Trees at the clearing's edge, the ground, the plants.
    for i, (x, z) in enumerate(((4, 52), (12, 58), (57, 55), (60, 34), (60, 3), (2, 1), (47, 60), (17, 61))):
        birch_tree(t, x, 3, z, 6 + i % 3, 339 + i)

    def top(x, z):
        r = noise(341, x, 2, z)
        return "minecraft:podzol" if r < 0.06 else ("minecraft:moss_block" if r < 0.12 else GRASS)
    m3.ground_layers(t, 2, 343, top_block=top, under=DIRT, deep=STONE)
    open_ground = [(x, z) for x in range(63) for z in range(63) if not (17 <= x <= 45 and 33 <= z <= 59)]
    plants(t, 3, 345, [(5, "minecraft:short_grass"), (1.5, "minecraft:fern"), (0.5, "minecraft:azure_bluet"),
                       (0.5, "minecraft:lily_of_the_valley"), (0.4, SILVERLEAF), (20, None)], open_ground)
    plants(t, 3, 347, [(3, "minecraft:short_grass"), (2, "minecraft:azure_bluet"), (2, "minecraft:oxeye_daisy"), (2, SILVERLEAF),
                       (1, "minecraft:fern"), (6, None)], garden)
    clear_above(t, 3)
    return t


# The Proving's stairwell (PlanetHubs.STAIR_LOCAL x 29..33, z 12..18, y 0..2) continues into the Sepulchre,
# whose origin is the Proving's (15, -16, 11) (PlanetHubs.SEPULCHRE_OFFSET): Sepulchre local = Proving local
# - (15, -16, 11). The stair is a switchback around a central pillar (x 31 / 16): flight 1 down the west lane
# going south, a landing, flight 2 down the east lane going north, a landing, flight 3 down the west lane, the
# bottom landing and the door south. Stands (feet heights) in the Proving's frame.
STAIR_FLIGHTS = (
    # (lane xs (Proving), [(z, stand), ...], stair facing)
    ((29, 30), [(13, 2), (14, 1), (15, 0), (16, -1), (17, -2)], "north"),
    ((32, 33), [(17, -4), (16, -5), (15, -6), (14, -7), (13, -8)], "south"),
    ((29, 30), [(13, -10), (14, -11), (15, -12), (16, -13), (17, -14)], "north"),
)
STAIR_LANDINGS = ((18, -3), (12, -9), (18, -15))  # (z, stand) across x 29..33
SEPULCHRE_OFFSET = (15, -16, 11)


def stairwell(t, frame):
    """The part of the switchback stair inside template `t`; `frame` (dx, dy, dz) maps Proving-local to local."""
    dx, dy, dz = frame
    for xs, steps, facing in STAIR_FLIGHTS:
        for z, s in steps:
            for x in xs:
                t.set(x - dx, s - 1 - dy, z - dz, TOMB_STAIRS, stairs(facing))
    for z, s in STAIR_LANDINGS:
        for x in range(29, 34):
            t.set(x - dx, s - 1 - dy, z - dz, BLACK_BRICKS)


def build_proving():
    t = Site("hub/proving", HUB_SIZE_PROVING, 3)
    bands = [RED_SANDSTONE, "minecraft:terracotta", "minecraft:red_terracotta", SMOOTH_RED, "minecraft:orange_terracotta",
             RED_SANDSTONE, "minecraft:brown_terracotta"]

    def rock(x, y, z):
        return bands[((y + int(noise(401, x // 4, 0, z // 4) * 2)) // 2) % len(bands)]

    # --- The cliff along the north edge, banded red rock, with the carved arch over the stair's head.
    for x in range(63):
        for z in range(10):
            top = int(3 + (9.5 - z) * 2.3 + (noise(403, x // 2, 0, z) - 0.5) * 3)
            for y in range(0, min(27, top) + 1):
                t.set(x, y, z, rock(x, y, z))
    for x in range(29, 34):
        for z in range(6, 12):
            for y in range(3, 10):
                t.set(x, y, z, AIR)
            t.set(x, 2, z, "minecraft:polished_blackstone")
    for x in range(29, 34):
        for y in range(3, 10):
            t.set(x, y, 5, "minecraft:polished_blackstone")
    for y in range(2, 11):
        for x in (28, 34):
            for z in (9, 10, 11):
                if y <= 10:
                    t.set(x, y, z, BLACK_BRICKS)
    for x in range(28, 35):
        for z in (9, 10, 11):
            t.set(x, 10, z, BLACK_BRICKS if x != 31 else "minecraft:chiseled_polished_blackstone")
    for x in (27, 35):
        t.set(x, 7, 10, "minecraft:black_wall_banner", {"facing": "south"})
        t.set(x, 6, 9, rock(x, 6, 9))
        t.set(x, 7, 9, rock(x, 7, 9))

    # --- The stairwell (STAIR_LOCAL) and its railing; the Sepulchre takes the stair on below y 0.
    for x in range(28, 35):
        for z in range(11, 20):
            for y in range(0, 3):
                if x in (28, 34) or z in (11, 19):
                    t.set(x, y, z, BLACK_BRICKS)
                elif z == 12:
                    t.set(x, y, z, "minecraft:polished_blackstone" if y == 2 else BLACK_BRICKS)
                elif x == 31 and 13 <= z <= 17:
                    t.set(x, y, z, BLACK_BRICKS)
                else:
                    t.set(x, y, z, AIR)
    stairwell(t, (0, 0, 0))
    for y in range(0, 3):
        t.set(31, y, 12, LADDER, ladder("south"))
    for z in range(13, 20):
        for x in (28, 34):
            t.set(x, 3, z, "minecraft:polished_blackstone_wall", wall_post())
    for x in range(29, 34):
        t.set(x, 3, 19, "minecraft:polished_blackstone_wall", wall_post())
    for x in (32, 33):
        t.set(x, 3, 12, "minecraft:polished_blackstone_wall", wall_post())
    for z in range(13, 18):
        t.set(31, 3, z, "minecraft:polished_blackstone_wall", wall_post())
    t.spot("STAIR_DOWN", 30, 3, 12)
    hub_spawner(t, "HOST", 25, 3, 12, "host")
    # The lamps flank the arch, clear of the lane (x 27, z 12) that leads to the stair's head.
    lamp_post(t, 27, 3, 11, 3, "minecraft:polished_blackstone_wall")
    lamp_post(t, 35, 3, 11, 3, "minecraft:polished_blackstone_wall")

    # --- The drill yard, the acolytes and the patrol droid.
    for x in range(18, 45):
        for z in range(21, 32):
            border = x in (18, 44) or z in (21, 31)
            t.set(x, 2, z, "minecraft:cut_red_sandstone" if border else SMOOTH_RED)
    for x, z in ((19, 23), (19, 29), (43, 23), (43, 29)):
        t.set(x, 3, z, "minecraft:target")
    for i, (x, z) in enumerate(((22, 24), (26, 28), (36, 24), (40, 28))):
        hub_spawner(t, f"RESIDENT_{i + 1}", x, 3, z, "acolyte")
    hub_spawner(t, "RESIDENT_7", 31, 3, 29, "droid")

    # --- Two prefab barracks; the east one holds the medbay.
    def barracks(x0, x1, door_x):
        for x in range(x0, x1 + 1):
            for z in range(14, 31):
                t.set(x, 2, z, "minecraft:smooth_stone")
                wall = x in (x0, x1) or z in (14, 30)
                for y in range(3, 7):
                    t.set(x, y, z, (PLATE if y == 3 else IMP_PANEL) if wall else AIR)
                t.set(x, 7, z, PLATE)
        for z in range(21, 24):
            for y in range(3, 6):
                t.set(door_x, y, z, AIR)
        for z in (17, 27):
            t.set((x0 + x1) // 2, 6, z, "minecraft:lantern", LANTERN_HUNG)
        out = door_x + (1 if door_x == x1 else -1)
        facing = "east" if door_x == x1 else "west"
        for z in (17, 27):
            t.set(out, 5, z, "minecraft:red_wall_banner", {"facing": facing})
    barracks(3, 15, 15)
    barracks(47, 59, 47)
    for z in (16, 18, 20, 25, 27):
        t.set(4, 3, z, "minecraft:red_bed", {"facing": "west", "occupied": "false", "part": "head"})
        t.set(5, 3, z, "minecraft:red_bed", {"facing": "west", "occupied": "false", "part": "foot"})
        t.set(14, 3, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    for z in (16, 18, 20):
        t.set(58, 3, z, "minecraft:red_bed", {"facing": "east", "occupied": "false", "part": "head"})
        t.set(57, 3, z, "minecraft:red_bed", {"facing": "east", "occupied": "false", "part": "foot"})
    for z in (25, 28):
        t.set(58, 3, z, "minecraft:white_bed", {"facing": "east", "occupied": "false", "part": "head"})
        t.set(57, 3, z, "minecraft:white_bed", {"facing": "east", "occupied": "false", "part": "foot"})
    t.set(48, 3, 28, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
    t.set(48, 3, 29, "minecraft:cauldron")
    t.spot("MEDBAY", 53, 3, 26)

    # --- The requisition store, the armourcraft tent, the spacer.
    for x in range(3, 16):
        for z in range(33, 42):
            t.set(x, 2, z, "minecraft:smooth_stone")
            wall = x == 3 or z in (33, 41)
            for y in range(3, 7):
                t.set(x, y, z, (PLATE if y == 3 else IMP_PANEL) if wall else AIR)
            t.set(x, 7, z, PLATE)
    for z in range(34, 41):
        t.set(15, 3, z, AIR)
    for x in range(10, 13):
        t.set(x, 3, 35, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(10, 3, 39, "minecraft:crafting_table")
    t.set(11, 3, 39, "minecraft:smithing_table")
    t.set(9, 6, 37, "minecraft:lantern", LANTERN_HUNG)
    hub_spawner(t, "VENDOR_1", 8, 3, 36, "vendor:imperial_quartermaster")
    hub_spawner(t, "VENDOR_2", 8, 3, 39, "vendor:trainer_armstech")
    tent_at(t, 2, 3, 44, 9, 48, "minecraft:gray_wool", "minecraft:red_wool", door="east")
    t.set(4, 3, 45, "minecraft:smithing_table")
    t.set(4, 3, 47, "minecraft:barrel", {"facing": "up", "open": "false"})
    hub_spawner(t, "VENDOR_3", 6, 3, 46, "vendor:trainer_armourcraft")
    for x, y, z in ((51, 3, 45), (52, 3, 45), (51, 4, 45), (52, 3, 49), (51, 3, 49)):
        t.set(x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    hub_spawner(t, "VENDOR_4", 50, 3, 47, "vendor:spacer")
    lamp_post(t, 53, 3, 47, 3, "minecraft:polished_blackstone_wall")

    # --- The pad: plating and stone inside blast walls, four bays, searchlights on the corners.
    for x in range(17, 46):
        for z in range(35, 61):
            if x == 31 or z == 47:
                t.set(x, 2, z, BLACK_BRICKS)
            else:
                t.set(x, 2, z, pick(405, x, 2, z, [(5, "minecraft:smooth_stone"), (2, "minecraft:polished_andesite"), (1, PLATE)]))
    for z in range(37, 61):
        if not 45 <= z <= 49:
            for x in (16, 46):
                for y in range(2, 6):
                    t.set(x, y, z, SCORCHED if noise(407, x, y, z) < 0.25 else PLATE)
    for x in range(16, 47):
        if not 29 <= x <= 33:
            for y in range(2, 6):
                t.set(x, y, 61, SCORCHED if noise(409, x, y, 61) < 0.25 else PLATE)
    for z in (40, 55):
        t.set(17, 4, z, "minecraft:red_wall_banner", {"facing": "east"})
        t.set(45, 4, z, "minecraft:red_wall_banner", {"facing": "west"})
    for x, z in ((17, 35), (45, 35), (17, 60), (45, 60)):
        for y in range(3, 7):
            t.set(x, y, z, "minecraft:polished_blackstone_wall", wall_post())
        t.set(x, 7, z, "minecraft:sea_lantern")
    t.spot("HEART", 31, 3, 47)
    for i, (x, z) in enumerate(((24, 41), (38, 41), (24, 54), (38, 54))):
        t.spot(f"PAD_BAY_{i + 1}", x, 3, z)
    terminal(t, "PAD_TERMINAL", 44, 3, 44, PAD_TERMINAL, "west")
    hub_spawner(t, "RESIDENT_5", 27, 3, 34, "guard")
    hub_spawner(t, "RESIDENT_6", 35, 3, 34, "guard")
    terminal(t, "CONTRACT_TERMINAL", 31, 3, 33, CONTRACT_TERMINAL, "north")
    for x, z in ((18, 19), (44, 19), (18, 33), (44, 33), (31, 20)):
        lamp_post(t, x, 3, z, 3, "minecraft:polished_blackstone_wall")

    def top(x, z):
        return pick(411, x, 2, z, [(7, RED_SAND), (2, COARSE), (1, "minecraft:gravel")])
    m3.ground_layers(t, 2, 413, top_block=top, under=RED_SANDSTONE, deep=RED_SANDSTONE)
    plants(t, 3, 415, [(1, "minecraft:dead_bush"), (1, "minecraft:short_dry_grass"), (0.6, ASHROOT), (40, None)],
           [(x, z) for x in range(63) for z in range(10, 63)])
    clear_above(t, 3)
    return t


def build_sepulchre():
    t = Site("hub/sepulchre", (33, 16, 41), 0)
    stone = m3.tomb_stone(421)

    def deep(x, y, z):
        return pick(423, x, y, z, [(6, TOMB), (1, TOMB_CRACKED), (2, BLACKSTONE)])

    # --- The stairwell under the Proving's (Sepulchre x 13..19, z 0..8): walls, pillar, the flights, the ladder.
    ox, oy, oz = SEPULCHRE_OFFSET
    for x in range(13, 20):
        for z in range(0, 9):
            for y in range(0, 16):
                if x in (13, 19) or z in (0, 8) or y == 0:
                    t.set(x, y, z, BLACK_BRICKS)
                elif x == 16 and 2 <= z <= 6:
                    t.set(x, y, z, BLACK_BRICKS)
                else:
                    t.set(x, y, z, AIR)
    stairwell(t, (ox, oy, oz))
    for y in range(7, 16):
        t.set(16, y, 1, LADDER, ladder("south"))
    for x in range(15, 18):
        for y in range(1, 4):
            t.set(x, y, 8, AIR)

    # --- The antechamber.
    for x in range(10, 23):
        for z in range(9, 15):
            t.set(x, 0, z, TOMB_CARVED if (x + z) % 4 == 0 else TOMB)
            for y in range(1, 7):
                t.set(x, y, z, AIR)
    for x, z in ((12, 10), (20, 10), (12, 13), (20, 13)):
        for y in range(1, 7):
            t.set(x, y, z, TOMB_CARVED if y in (1, 6) else TOMB)
    for x, z in ((10, 9), (22, 9), (10, 14), (22, 14)):
        t.set(x, 1, z, FUNERARY)
    t.set(16, 6, 11, SOUL_LANTERN, LANTERN_HUNG)
    t.set(16, 7, 11, TOMB_CARVED)

    # --- The hall: an open floor of radius 9 around its heart, pillars, three alcoves, Zarvos's dais.
    for x in range(4, 29):
        for z in range(16, 40):
            t.set(x, 0, z, TOMB_CARVED if (x - 16) % 6 == 0 and (z - 27) % 6 == 0 else stone(x, 0, z))
            for y in range(1, 14):
                t.set(x, y, z, AIR)
    for x in range(14, 19):
        for y in range(1, 6):
            t.set(x, y, 15, AIR)
    for z in range(18, 40, 4):
        for x in (6, 26):
            for y in range(1, 14):
                t.set(x, y, z, TOMB_CARVED if y in (1, 13) else TOMB)
        for x in range(4, 29):
            t.set(x, 13, z, TOMB_CARVED)
        t.set(16, 12, z, SOUL_LANTERN, LANTERN_HUNG)
    for x0, x1 in ((1, 3), (29, 31)):
        for x in range(x0, x1 + 1):
            for z in range(25, 30):
                t.set(x, 0, z, TOMB)
                for y in range(1, 6):
                    t.set(x, y, z, AIR)
        t.set(x0 if x0 == 1 else x1, 1, 25, FUNERARY)
        t.set(x0 if x0 == 1 else x1, 1, 29, FUNERARY)
    t.spot("ROOM_1", 2, 1, 27)
    t.spot("ROOM_2", 30, 1, 27)
    t.spot("ROOM_3", 16, 1, 19)
    t.spot("HEART", 16, 1, 27)
    for x in range(12, 21):
        for z in range(35, 40):
            t.set(x, 1, z, TOMB_CARVED if x in (12, 20) or z == 35 else TOMB)
    for x in range(13, 20):
        t.set(x, 1, 34, TOMB_STAIRS, stairs("south"))
    for x in range(14, 19):
        for z in (37, 38):
            t.set(x, 2, z, TOMB_CARVED if x in (14, 18) else TOMB)
            t.set(x, 3, z, TOMB_CARVED)
            t.set(x, 4, z, TOMB_SLAB, BOTTOM)
    for x in (12, 20):
        for y in range(2, 5):
            t.set(x, y, 38, TOMB_CARVED if y < 4 else "minecraft:polished_blackstone_wall")
    for x, z in ((13, 36), (19, 36)):
        t.set(x, 2, z, "minecraft:candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    t.spot("ECHO", 16, 2, 35)
    fill_unset(t, deep)
    return t




# ---------------------------------------------------------------------------------------------
# The Heartstone (runtime, shared, Selvaren; 3.4, 8.4): the corrupted great waystone in its ring.
# ---------------------------------------------------------------------------------------------
def build_heartstone():
    t = Site("planet/heartstone", (19, 12, 19), 3)
    cx, cz = 9, 9.5

    def stone(x, y, z, block):
        r = noise(501, x, y, z)
        if r < 0.24:
            return BLACKSTONE
        if r < 0.36:
            return BLACK_BRICKS_CRACKED
        return block

    # The great stone: three wide, two deep, a narrower course and a capstone; the heartstone is its carved heart.
    for x in range(8, 11):
        for z in (9, 10):
            for y in range(3, 8):
                t.set(x, y, z, stone(x, y, z, pick(503, x, y, z, [(4, RUIN), (2, RUIN_MOSSY), (1, RUIN_CRACKED)])))
            t.set(x, 3, z, stone(x, 3, z, RUIN_CARVED))
    for z in (9, 10):
        t.set(9, 8, z, stone(9, 8, z, RUIN_CARVED))
    t.set(9, 9, 10, stone(9, 9, 10, RUIN))
    t.set(9, 9, 9, "minecraft:blackstone_slab", slab())
    t.set(9, 5, 9, HEARTSTONE, None, {"id": HEARTSTONE})
    t.mark("STONE", 9, 5, 9)
    for x in range(7, 12):
        for z in range(8, 12):
            if not (8 <= x <= 10 and 9 <= z <= 10):
                t.set(x, 3, z, "minecraft:blackstone_slab" if noise(505, x, 3, z) < 0.45 else MOSSY_SLAB, slab())
    # The ring of standing stones the cleansing is held in.
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        x, z = round(cx + 7 * math.cos(a)), round(cz + 7 * math.sin(a))
        h = 2 + int(noise(507, k, 0, 0) * 3)
        for y in range(3, 3 + h):
            t.set(x, y, z, stone(x, y, z, pick(509, x, y, z, [(3, RUIN), (2, RUIN_MOSSY), (1, RUIN_CRACKED)])))
        if noise(511, k, 1, 0) > 0.5:
            t.set(x, 3 + h, z, MOSSY_SLAB, slab())
    t.spot("HEART", 9, 3, 6)

    def top(x, z):
        r = math.hypot(x - cx, z - cz)
        a = math.atan2(z - cz, x - cx)
        if r < 6.5 and abs(math.sin(3 * a + r * 0.35)) < 0.18:
            return BLACKSTONE  # the dark veins that run out from the stone
        if r < 5:
            return pick(513, x, 2, z, [(3, COARSE), (2, "minecraft:podzol"), (1, GRASS)])
        return "minecraft:moss_block" if noise(515, x, 2, z) < 0.1 else GRASS

    m3.ground_layers(t, 2, 517, top_block=top, under=DIRT, deep=STONE)
    near = [(x, z) for x in range(19) for z in range(19) if math.hypot(x - cx, z - cz) < 5.5]
    far = [(x, z) for x in range(19) for z in range(19) if math.hypot(x - cx, z - cz) >= 6]
    plants(t, 3, 519, [(2, "minecraft:dead_bush"), (3, "minecraft:short_dry_grass"), (8, None)], near)
    plants(t, 3, 521, [(4, "minecraft:short_grass"), (1.5, "minecraft:fern"), (0.4, SILVERLEAF), (12, None)], far)
    clear_above(t, 3)
    return t


# ---------------------------------------------------------------------------------------------
# The Singing Hollow (runtime, per player, Selvaren; 8.4): a mossy mound over a sinkhole, a ladder down a
# rock column into the grotto, six hanging stones under six humming clusters, a sealed relic alcove.
# ---------------------------------------------------------------------------------------------
def build_hollow_surface():
    t = Site("expedition/singing_hollow_surface", (13, 10, 13), 3)
    for x in range(13):
        for z in range(13):
            d = math.hypot(x - 6, z - 6.5)
            h = int(7.2 - d * 1.15 + (noise(531, x, 0, z) - 0.5) * 1.6)
            for y in range(3, h + 1):
                t.set(x, y, z, pick(533, x, y, z, [(5, "minecraft:mossy_cobblestone"), (4, STONE), (2, "minecraft:andesite"),
                                                   (2, "minecraft:moss_block"), (0.5, AURELITE)]))
    # The mouth (north), the sinkhole at the centre and its ladder (the grotto's ladder continues it below).
    for z in range(2, 6):
        for y in (3, 4):
            t.set(6, y, z, AIR)
    for y in range(0, 10):
        t.set(6, y, 6, AIR)
    for y in range(0, 4):
        t.set(6, y, 6, LADDER, ladder("north"))
        t.keep(6, y, 7, STONE)
    for x in (5, 7):
        for y in range(0, 3):
            t.keep(x, y, 6, STONE)
    t.spot("HEART", 6, 3, 1)
    t.spawner("GUARD", 2, 3, 3, "hollowhorns", count=2)

    def top(x, z):
        return "minecraft:moss_block" if noise(535, x, 2, z) < 0.2 else GRASS
    m3.ground_layers(t, 2, 537, top_block=top, under=DIRT, deep=STONE)
    cells = [(x, z) for x in range(13) for z in range(13)]
    plants(t, 3, 539, [(4, "minecraft:short_grass"), (2, "minecraft:fern"), (1, "minecraft:moss_carpet"), (0.6, SILVERLEAF), (8, None)], cells)
    for x in range(13):
        for z in range(13):
            for y in range(4, 10):
                if t.get(x, y - 1, z) == "minecraft:moss_block" and (x, y, z) not in t.cells and noise(541, x, y, z) > 0.6:
                    t.set(x, y, z, "minecraft:moss_carpet")
    clear_above(t, 3)
    return t


GROTTO_STONES = [((23, 13), "west"), ((19, 18), "north"), ((11, 18), "north"), ((7, 13), "east"), ((11, 8), "south"), ((19, 8), "south")]
GROTTO_CHIMES = [(26, 13), (20, 20), (10, 20), (4, 13), (10, 6), (20, 6)]
GROTTO_GATE = [(x, y, 25) for x in (14, 15, 16) for y in (2, 3, 4)]


def grotto_air(x, y, z):
    if y < 2 or y > 13:
        return False
    e = ((x - 15) / 13.0) ** 2 + ((z - 13) / 10.5) ** 2 + (value_noise(x, y, z, 4.0, 551) - 0.5) * 0.3
    if y > 6:
        e += ((y - 6) / 7.5) ** 2
    return e < 1.0


def build_hollow_grotto():
    t = Site("expedition/singing_hollow_grotto", (31, 16, 31), 0)

    def rock(x, y, z):
        return pick(553, x, y, z, [(6, STONE), (2, "minecraft:andesite"), (2, "minecraft:tuff"), (1, "minecraft:mossy_cobblestone")])

    for x in range(31):
        for z in range(31):
            for y in range(16):
                if grotto_air(x, y, z):
                    t.set(x, y, z, AIR)
    # The rock column under the sinkhole: the ladder inside, its door north.
    for x in range(14, 17):
        for z in range(14, 17):
            for y in range(1, 16):
                t.set(x, y, z, rock(x, y, z))
    for y in range(2, 16):
        t.set(15, y, 15, LADDER, ladder("north"))
    t.set(15, 2, 14, AIR)
    t.set(15, 3, 14, AIR)
    # The way to the relic: a tunnel south, the seal (GATE), the alcove with the crystal that answers.
    for x in range(14, 17):
        for z in range(21, 25):
            for y in range(2, 5):
                t.set(x, y, z, AIR)
            t.set(x, 1, z, rock(x, 1, z))
    for (x, y, z) in GROTTO_GATE:
        t.seal(x, y, z, "ruin")
        t.mark("GATE", x, y, z)
    for x in range(12, 19):
        for z in range(26, 30):
            for y in range(2, 6):
                t.set(x, y, z, AIR)
            t.set(x, 1, z, RUIN_MOSSY if (x + z) % 3 else RUIN)
    t.set(15, 2, 28, RUIN_CARVED)
    t.set(15, 3, 28, KYBER, cluster("up"))
    t.mark("RELIC", 15, 3, 28)
    t.set(13, 2, 27, LUMEN)
    t.set(17, 2, 29, LUMEN)
    # Six hanging stones (counterweights on chains), each under the humming cluster it answers to.
    for i, ((x, z), facing) in enumerate(GROTTO_STONES):
        for y in range(2, 5):
            t.set(x, y, z, AIR)
        t.set(x, 1, z, RUIN_CARVED)
        t.counterweight(f"WEIGHT_{i + 1}", x, 3, z, facing, "ruin")
        y = 4
        while y < 15 and grotto_air(x, y, z):
            t.set(x, y, z, CHAIN, chain_y())
            y += 1
        t.set(x, y, z, rock(x, y, z))
        cx, cz = GROTTO_CHIMES[i]
        for y in range(2, 4):
            t.set(cx, y, cz, AIR)
        t.set(cx, 1, cz, AURELITE)
        t.set(cx, 2, cz, KYBER, cluster("up"))
        t.mark(f"CHIME_{i + 1}", cx, 2, cz)
    t.spot("HEART", 15, 2, 10)
    # Floor and walls: moss beds, lumen moss, aurelite veins in the rock.
    for x in range(31):
        for z in range(31):
            if t.get(x, 2, z) == AIR and (x, 1, z) not in t.cells and (x, z) not in ((15, 10), (15, 14)) and z < 21:
                t.set(x, 1, z, "minecraft:moss_block" if noise(555, x, 1, z) < 0.3 else rock(x, 1, z))
                r = noise(557, x, 2, z)
                if r < 0.03:
                    t.set(x, 2, z, LUMEN)
                elif r < 0.12 and t.get(x, 1, z) == "minecraft:moss_block":
                    t.set(x, 2, z, "minecraft:moss_carpet")
    t.shell(2, lambda x, y, z: AURELITE if noise(559, x, y, z) < 0.07 else rock(x, y, z))
    stones = [(x, 3, z) for (x, z), _f in GROTTO_STONES]
    m3.check_redstone(t, [((), ())] + [((s,), ()) for s in stones])
    return t


# ---------------------------------------------------------------------------------------------
# The duskglass rift (runtime, per player, Varkhast; 8.4): a crack in the ash plain, a rope ladder into a
# chasm of black-glass ledges with ladders up every drop, hound packs, and the crystal at the bottom.
# ---------------------------------------------------------------------------------------------
def build_rift_surface():
    t = Site("expedition/duskglass_rift_surface", (15, 8, 15), 2)
    crack = set()
    for z in range(3, 12):
        for x in range(6, 9):
            crack.add((x, z))
        for x in (5, 9):
            if 4 <= z <= 10 and noise(601, x, 0, z) > 0.6:
                crack.add((x, z))
    for (x, z) in crack:
        for y in (0, 1):
            t.set(x, y, z, AIR)
    for (x, z) in sorted(crack):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, z + dz)
            if p not in crack and 0 <= p[0] < 15 and 0 <= p[1] < 15 and noise(603, p[0], 2, p[1]) > 0.45 and p != (7, 2):
                t.set(p[0], 2, p[1], pick(605, p[0], 2, p[1], [(3, BLACKSTONE), (2, "minecraft:basalt"), (2, "minecraft:tuff")]))
    for x, z in ((5, 3), (9, 8), (5, 11)):
        if (x, z) not in crack:
            t.set(x, 2, z, BLACKSTONE)
            t.set(x, 3, z, DUSKGLASS, cluster("up"))
    for y in (0, 1):
        t.set(7, y, 3, LADDER, ladder("south"))
    t.spot("HEART", 7, 2, 1)

    def top(x, z):
        return pick(607, x, 1, z, [(4, COARSE), (2, "minecraft:gravel"), (2, "minecraft:tuff"), (1, RED_SAND)])
    m3.ground_layers(t, 1, 609, top_block=top, under="minecraft:tuff", deep="minecraft:tuff")
    plants(t, 2, 611, [(1, "minecraft:dead_bush"), (12, None)], [(x, z) for x in range(15) for z in range(15)])
    clear_above(t, 2)
    return t


# Ledges: (x0, x1, z0, z1, floor), solid three thick under the floor; ladders: (x, z, y0, y1, facing).
RIFT_LEDGES = [
    (4, 13, 9, 11, 21), (14, 18, 9, 16, 18), (8, 18, 17, 18, 15), (4, 7, 12, 18, 12),
    (5, 14, 10, 11, 9), (15, 18, 10, 22, 6), (6, 18, 23, 24, 3),
]
RIFT_LADDERS = [
    (11, 11, 23, 27, "south"), (14, 10, 19, 21, "east"), (17, 17, 16, 18, "south"), (7, 17, 13, 15, "west"),
    (5, 11, 10, 12, "north"), (15, 10, 7, 9, "east"), (16, 23, 4, 6, "south"), (5, 24, 2, 3, "west"),
]
RIFT_RELIC = (11, 2, 16)


def build_rift():
    t = Site("expedition/duskglass_rift_rift", (23, 28, 31), 0)

    def rock(x, y, z):
        return pick(621, x, y, z, [(4, BLACKSTONE), (3, "minecraft:smooth_basalt"), (2, "minecraft:tuff"), (2, "minecraft:basalt"),
                                   (0.3, BUDDING)])

    # The chasm (y 2..23) under the crack (y 24..27, the surface crack's columns), jagged by noise.
    for x in range(23):
        for z in range(31):
            for y in range(2, 28):
                if y <= 23:
                    inside = 4 <= x <= 18 and 5 <= z <= 25
                    edge = x in (4, 18) or z in (5, 25)
                    if inside and not (edge and value_noise(x, y, z, 3.0, 623) > 0.62):
                        t.set(x, y, z, AIR)
                elif 10 <= x <= 12 and 11 <= z <= 19:
                    t.set(x, y, z, AIR)
    for x in range(4, 19):
        for z in range(5, 26):
            t.set(x, 1, z, pick(625, x, 1, z, [(3, BLACKSTONE), (2, "minecraft:tuff"), (1, "minecraft:gravel")]))
    for (x0, x1, z0, z1, floor) in RIFT_LEDGES:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(floor - 2, floor):
                    t.set(x, y, z, pick(631, x, y, z, [(3, BLACKSTONE), (2, "minecraft:smooth_basalt"), (1, "minecraft:tuff")]))
                t.set(x, floor, z, pick(627, x, floor, z, [(3, TINTED), (3, BLACKSTONE), (2, "minecraft:polished_blackstone")]))
    t.set(11, 23, 10, rock(11, 23, 10))  # the top ladder's backing where it leaves the crack
    for (x, z, y0, y1, facing) in RIFT_LADDERS:
        for y in range(y0, y1 + 1):
            t.set(x, y, z, LADDER, ladder(facing))
    # Hanging lanterns under the ledges (explorers' lights), the hound packs, the crystal and its plinth.
    for x, y, z in ((8, 18, 10), (16, 15, 12), (12, 12, 17), (6, 9, 15)):
        t.set(x, y, z, "minecraft:lantern", LANTERN_HUNG)
    t.spawner("SQUAD_1", 5, 13, 15, "ash_hounds")
    t.spawner("SQUAD_2", 14, 2, 20, "ash_hounds")
    rx, ry, rz = RIFT_RELIC
    t.set(rx, ry - 1, rz, BUDDING)
    t.set(rx, ry, rz, KYBER, cluster("up"))
    t.mark("RELIC", rx, ry, rz)
    t.spot("HEART", 11, 2, 13)
    t.shell(2, rock)
    # Duskglass on the walls: clusters that cut, beside the ledges and the ladders' way down.
    keep_clear = set()
    for (x, z, y0, y1, _f) in RIFT_LADDERS:
        for y in range(y0 - 1, y1 + 2):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    keep_clear.add((x + dx, y, z + dz))
    for cell in ((5, 13, 15), (5, 14, 15), (14, 2, 20), (14, 3, 20), (11, 2, 13), (11, 3, 13), (rx, ry, rz), (rx, ry + 1, rz)):
        keep_clear.add(cell)
    sides = {"east": (-1, 0), "west": (1, 0), "south": (0, -1), "north": (0, 1)}  # facing -> offset to the wall it grows on
    for (x, y, z), cell in sorted(t.cells.items()):
        if cell[0] != AIR or (x, y, z) in keep_clear or y < 2 or y > 23:
            continue
        for facing, (dx, dz) in sides.items():
            wall = t.get(x + dx, y, z + dz)
            if wall not in (None, AIR, LADDER, TINTED) and noise(629, x, y, z) < 0.035:
                t.set(x, y, z, DUSKGLASS, cluster(facing))
                break
    return t


# ---------------------------------------------------------------------------------------------
# The Wayfarer cache (runtime, per player; 8.4): a brass vault half in the ground, three plates over its pit.
# ---------------------------------------------------------------------------------------------
CACHE_PLATFORMS = [[(x, 3, z) for x in (5, 6, 7)] for z in (4, 6, 8)]
BRASS = "minecraft:waxed_cut_copper"


def build_wayfarer_cache():
    t = Site("planet/wayfarer_cache", (13, 9, 13), 4)

    def shell_block(x, y, z):
        return pick(641, x, y, z, [(6, "minecraft:waxed_weathered_cut_copper"), (3, "minecraft:waxed_exposed_cut_copper"), (2, RUIN_MOSSY)])

    inner, outer = 5.2, 6.3
    for x in range(13):
        for z in range(13):
            r = math.hypot(x - 6, z - 6)
            for y in range(9):
                d = r if y <= 3 else math.sqrt(r * r + (y - 3) ** 2)
                if inner <= d < outer:
                    t.set(x, y, z, shell_block(x, y, z))
                elif d < inner:
                    t.set(x, y, z, AIR if y > 0 else shell_block(x, y, z))
    # The entrance ledge (north), the dais (south), the pit between; three plates over the pit.
    for x in range(13):
        for z in range(13):
            if math.hypot(x - 6, z - 6) < inner and (z <= 2 or z >= 10):
                for y in range(0, 4):
                    t.set(x, y, z, shell_block(x, y, z) if y < 3 else ("minecraft:waxed_exposed_cut_copper" if (x + z) % 2 else BRASS))
    for i, cells in enumerate(CACHE_PLATFORMS):
        for (x, y, z) in cells:
            t.set(x, y, z, BRASS)
            t.mark(f"PLATFORM_{i + 1}", x, y, z)
        for x in (5, 7):
            z = cells[0][2]
            y = 4
            while y < 8 and t.get(x, y, z) == AIR:
                t.set(x, y, z, CHAIN, chain_y())
                y += 1
    for x in range(4, 9):
        if t.get(x, 8, 6) == AIR:
            t.set(x, 8, 6, BRASS)  # the crossbar over the oculus the middle plate hangs from
    for y in range(1, 4):
        t.set(6, y, 3, LADDER, ladder("south"))
    for x in range(5, 8):
        for y in (4, 5):
            t.set(x, y, 0, AIR)
            t.set(x, y, 1, AIR)
    t.spot("HEART", 6, 4, 10)
    t.chest("CACHE", 6, 4, 11, "north", "wayfarer_cache_m4")
    for x, z in ((4, 2), (8, 2), (4, 10), (8, 10)):
        m3.lantern(t, x, 4, z)
    # The pilgrim's mark: a carved stone with a lamp by the door.
    t.set(1, 4, 1, RUIN_CARVED)
    t.set(1, 5, 1, RUIN_MOSSY)
    m3.lantern(t, 1, 6, 1)
    air_above_unset(t, 4)
    return t


# ---------------------------------------------------------------------------------------------
# The hunting camp (runtime, per player; 10.5): Kaast's arena. The fight is W6's.
# ---------------------------------------------------------------------------------------------
CAMP_C = (20, 20)
CAMP_NESTS = [((20, 6), "south", "north"), ((34, 20), "west", "east"), ((13, 32), "north", "south"), ((6, 20), "east", "west")]
CAMP_MINES = [(30, 26), (20, 32), (10, 26), (10, 14), (20, 8), (30, 14)]
CAMP_GATE_CELLS = [(x, y, 38) for x in (19, 20, 21) for y in (4, 5, 6)]
CAMP_LAMPS = [(12, 12), (12, 28), (28, 28), (31, 17)]
DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def build_hunting_camp():
    t = Site("boss/hunting_camp", (41, 14, 41), 4)
    cx, cz = CAMP_C

    def r(x, z):
        return math.hypot(x - cx, z - cz)

    def scrap(x, y, z):
        return pick(661, x, y, z, [(5, PLATE), (3, SCORCHED), (1, CONDUIT), (1, "minecraft:spruce_planks"), (1, "minecraft:dark_oak_planks")])

    # The ring wall: four high on a stone footing, its gate in the south.
    for x in range(41):
        for z in range(41):
            if 17.5 <= r(x, z) < 18.6:
                for y in range(0, 4):
                    t.set(x, y, z, STONE if y < 3 else "minecraft:cobblestone")
                for y in range(4, 8):
                    if y < 7 or noise(663, x, y, z) > 0.2:
                        t.set(x, y, z, scrap(x, y, z))
    # W6's gate starts open; `top` marks the opening's top row (CampGateBlock keeps it in step only on updates).
    top = max(y for (_, y, _) in CAMP_GATE_CELLS)
    for (x, y, z) in CAMP_GATE_CELLS:
        t.set(x, y, z, CAMP_GATE, {"open": "true", "top": "true" if y == top else "false"})
        t.mark("GATE", x, y, z)
    for x in (19, 20, 21):
        t.set(x, 7, 38, PLATE)
    # The pit: fifteen across, three deep, four ramps.
    for x in range(41):
        for z in range(41):
            if r(x, z) <= 7.5:
                t.set(x, 0, z, pick(665, x, 0, z, [(3, COARSE), (2, "minecraft:gravel"), (1, "minecraft:packed_mud")]))
                for y in range(1, 4):
                    t.set(x, y, z, AIR)
    for a in range(-1, 2):
        for (x, z, dirn, step) in ((cx - 7 + 0, cz + a, "west", 0), (cx - 6, cz + a, "west", 1), (cx + 7, cz + a, "east", 0),
                                   (cx + 6, cz + a, "east", 1), (cx + a, cz - 7, "north", 0), (cx + a, cz - 6, "north", 1),
                                   (cx + a, cz + 7, "south", 0), (cx + a, cz + 6, "south", 1)):
            tread = 2 - step
            for y in range(0, tread):
                t.set(x, y, z, "minecraft:packed_mud")
            t.set(x, tread, z, "minecraft:spruce_stairs", stairs(dirn))
    # The four gun nests on scaffold towers, a ladder on the inward face.
    for i, ((nx, nz), inward, outward) in enumerate(CAMP_NESTS):
        for y in range(4, 9):
            t.set(nx, y, nz, "minecraft:spruce_log", log("y"))
            for dx in (-1, 1):
                for dz in (-1, 1):
                    t.set(nx + dx, y, nz + dz, SCAFFOLD, {"bottom": "false", "distance": "0", "waterlogged": "false"})
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                t.set(nx + dx, 9, nz + dz, "minecraft:spruce_planks")
        ix, iz = DIRS[inward]
        for y in range(4, 10):
            t.set(nx + ix, y, nz + iz, LADDER, ladder(inward))
        ox, oz = DIRS[outward]
        for k in (-1, 0, 1):
            px, pz = (nx + ox + k * abs(oz), nz + oz + k * abs(ox))
            t.set(px, 10, pz, SCORCHED if k == 0 else PLATE)
        t.spot(f"NEST_{i + 1}", nx, 10, nz)
    # Cover: wreck plates and crates scattered between the pit and the wall.
    taken = set()
    for x in range(23, 31):
        for z in range(8, 15):
            taken.add((x, z))
    for (lx, lz) in CAMP_LAMPS:
        taken.add((lx, lz))
    for (nx, nz), _i, _o in CAMP_NESTS:
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                taken.add((nx + dx, nz + dz))
    for (mx, mz) in CAMP_MINES:
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                taken.add((mx + dx, mz + dz))
    for x in range(17, 24):
        for z in range(26, 38):
            taken.add((x, z))
    k = 0
    for x in range(2, 39):
        for z in range(2, 39):
            if not 9.5 <= r(x, z) <= 15.5 or (x, z) in taken or noise(667, x, 0, z) > 0.035:
                continue
            k += 1
            kind = k % 3
            cells = [(x, z), (x + 1, z)] if kind != 2 else [(x, z), (x, z + 1)]
            if any(c in taken or not 9 <= r(*c) <= 16 for c in cells):
                continue
            for (px, pz) in cells:
                for y in range(4, 6):
                    if kind == 0:
                        t.set(px, y, pz, "minecraft:barrel", {"facing": "up", "open": "false"})
                    else:
                        t.set(px, y, pz, scrap(px, y, pz))
                taken.add((px, pz))
    for i, (x, z) in enumerate(CAMP_MINES):
        t.spot(f"MINE_{i + 1}", x, 4, z)
    t.spot("HEART", cx, 1, cz)
    # Kaast's own corner: a tent, a fire, lamps.
    tent_at(t, 3, 24, 9, 29, 13, "minecraft:gray_wool", "minecraft:black_wool")
    t.set(31, 4, 9, "minecraft:campfire", campfire())
    for x, z in CAMP_LAMPS:
        lamp_post(t, x, 4, z)

    def top(x, z):
        return pick(669, x, 3, z, [(4, COARSE), (3, "minecraft:gravel"), (2, "minecraft:packed_mud"), (1, DIRT)])
    m3.ground_layers(t, 3, 671, top_block=top, under=DIRT, deep=STONE, skip=lambda x, z: r(x, z) >= 18.6)
    air_above_unset(t, 4, keep=lambda x, z: r(x, z) >= 18.6)
    return t


# ---------------------------------------------------------------------------------------------
# Ambush rocks (runtime, per player; 8.5): boulders for cover on the natural ground (layer 0 is left
# to the ground under the template, so the piece fits either planet and the home world).
# ---------------------------------------------------------------------------------------------
def build_ambush_rocks():
    t = Site("mission/ambush_rocks", (13, 6, 13), 1)
    for (bx, bz, br) in ((3, 3, 2.2), (9.5, 2.5, 1.8), (10, 9.5, 2.0), (2.5, 10, 1.6)):
        for x in range(13):
            for z in range(13):
                for y in range(0, 5):
                    d = math.sqrt((x - bx) ** 2 + ((y - 0.5) * 1.3) ** 2 + (z - bz) ** 2)
                    if d < br + (noise(681, x, y, z) - 0.5) * 0.6:
                        t.set(x, y, z, pick(683, x, y, z, [(4, STONE), (2, "minecraft:andesite"), (2, "minecraft:cobblestone"),
                                                           (1, "minecraft:tuff"), (1, "minecraft:mossy_cobblestone")]))
    t.spot("HEART", 6, 1, 6)
    air_above_unset(t, 1)
    return t




# ---------------------------------------------------------------------------------------------
# Checks: sizes, sidecar anchors (each a name of SiteAnchors.Anchor), anchor blocks and feet cells
# ---------------------------------------------------------------------------------------------
SITE_ANCHORS_JAVA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "java", "com", "oldrepublic",
                                 "world", "site", "SiteAnchors.java")


def java_anchor_names():
    """The constants of SiteAnchors.Anchor, read from the Java source: a sidecar name the enum lacks is dropped at load."""
    with open(SITE_ANCHORS_JAVA, encoding="utf-8") as source:
        text = re.sub(r"//[^\n]*", "", source.read())
    start = text.index("public enum Anchor {") + len("public enum Anchor {")
    return set(re.findall(r"[A-Z][A-Z0-9_]*", text[start:text.index(";", start)]))


M4_ANCHOR_BLOCKS = {
    "SQUAD": SPAWNER, "SQUAD_1": SPAWNER, "SQUAD_2": SPAWNER, "SENTINEL_1": SPAWNER, "SENTINEL_2": SPAWNER, "GUARD": SPAWNER,
    "CHEST_1": CHEST, "CACHE": CHEST, "URN_KEEPER": KEEPER, "DOOR_SEAL": SEALED, "HOST": HUB_SPAWNER,
    "PAD_TERMINAL": PAD_TERMINAL, "CONTRACT_TERMINAL": CONTRACT_TERMINAL,
}
for _i in range(1, 8):
    M4_ANCHOR_BLOCKS[f"RESIDENT_{_i}"] = HUB_SPAWNER
for _i in range(1, 7):
    M4_ANCHOR_BLOCKS[f"CHIME_{_i}"] = KYBER
    M4_ANCHOR_BLOCKS[f"WEIGHT_{_i}"] = COUNTERWEIGHT
for _i in range(1, 5):
    M4_ANCHOR_BLOCKS[f"URN_{_i}"] = ECHO_URN
    M4_ANCHOR_BLOCKS[f"VENDOR_{_i}"] = HUB_SPAWNER
for _i in range(1, 4):
    M4_ANCHOR_BLOCKS[f"PLATFORM_{_i}"] = BRASS
M4_FEET = {"HEART", "SURFACE_GUARD", "MEDBAY", "STAIR_DOWN", "ECHO"} | {f"PAD_BAY_{i}" for i in range(1, 5)} \
    | {f"ROOM_{i}" for i in range(1, 4)} | {f"NEST_{i}" for i in range(1, 5)} | {f"MINE_{i}" for i in range(1, 7)}
ROOM_ABOVE = (SPAWNER, HUB_SPAWNER, PAD_TERMINAL, CONTRACT_TERMINAL)


def check_m4(t, size, expected, blocks, enum_names):
    """The M3 template checks with the M4 anchors: size, the exact anchor set, every anchor a SiteAnchors.Anchor
    inside the template holding its block, feet anchors two cells of air on a block, spawners and terminals with
    room above, jigsaws facing out of their template."""
    if t.size != size:
        raise SystemExit(f"{t.name}: size {t.size}, expected {size}")
    if sorted(t.anchors) != sorted(expected):
        raise SystemExit(f"{t.name}: anchors {sorted(t.anchors)}, expected {sorted(expected)}")
    unknown = [a for a in expected if a not in enum_names]
    if unknown:
        raise SystemExit(f"{t.name}: anchors {unknown} are not in SiteAnchors.Anchor")
    for anchor, cells in t.anchors.items():
        block = blocks.get(anchor, M4_ANCHOR_BLOCKS.get(anchor))
        for cell in cells:
            if not t.inside(*cell):
                raise SystemExit(f"{t.name}: {anchor} {cell} is outside the template")
            if block is not None and t.get(*cell) != block:
                raise SystemExit(f"{t.name}: {anchor} {cell} holds {t.get(*cell)}, expected {block}")
            if anchor in M4_FEET:
                if t.get(*cell) not in (AIR, CAVE_AIR) or t.get(cell[0], cell[1] + 1, cell[2]) not in (AIR, CAVE_AIR):
                    raise SystemExit(f"{t.name}: {anchor} {cell} is not two cells of air")
                below = t.get(cell[0], cell[1] - 1, cell[2])
                natural = below is None and t.natural_below
                if cell[1] > 0 and not natural and (below is None or not m3.is_conductor_name(below)):
                    raise SystemExit(f"{t.name}: {anchor} {cell} does not stand on a block ({below})")
    for pos, cell in t.cells.items():
        if cell[0] in ROOM_ABOVE and t.get(pos[0], pos[1] + 1, pos[2]) not in (AIR, CAVE_AIR):
            raise SystemExit(f"{t.name}: the {cell[0]} at {pos} has no room above it")
        if cell[0] == "minecraft:jigsaw" and not any(j[0] == pos for j in t.jigsaws):
            raise SystemExit(f"{t.name}: stray jigsaw at {pos}")
    for (jpos, orientation, *_rest) in t.jigsaws:
        front = orientation.split("_")[0]
        if t.inside(*m3.step_of(jpos, front)):
            raise SystemExit(f"{t.name}: the jigsaw at {jpos} faces into its own template")


def natural(builder):
    """Marks a builder's template as standing on the ground under it (its feet anchors may have unset cells below)."""
    def build():
        t = builder()
        t.natural_below = True
        return t
    return build


ANCHORS_HUB = ["HEART", "PAD_BAY_1", "PAD_BAY_2", "PAD_BAY_3", "PAD_BAY_4", "MEDBAY", "HOST", "VENDOR_1", "VENDOR_2", "VENDOR_3",
               "VENDOR_4", "RESIDENT_1", "RESIDENT_2", "RESIDENT_3", "PAD_TERMINAL", "CONTRACT_TERMINAL"]

# (builder, size, anchors written to the sidecar, anchor blocks particular to the template)
M4_TEMPLATES = [
    (lambda: build_waystone(False), (11, 9, 11), ["STONE", "CACHE", "HEART"], {"STONE": WAYSTONE}),
    (lambda: build_waystone(True), (11, 9, 11), ["STONE", "CACHE", "HEART", "GUARD"], {"STONE": WAYSTONE_CORRUPTED}),
    (build_tomb_gate, (21, 14, 21), ["STAIR_MOUTH", "SQUAD", "GUARD", "CHEST_1", "SURFACE_GUARD"], {}),
    (build_tomb_descent, (11, 18, 27), ["SENTINEL_1"], {}),
    (build_urn_vault, (21, 12, 21), ["URN_KEEPER", "URN_1", "URN_2", "URN_3", "URN_4", "SENTINEL_1", "SENTINEL_2", "CHEST_1", "DOOR_SEAL"], {}),
    (build_sealed_crypt, (21, 12, 21), ["HEART", "RELIC", "SENTINEL_1", "SENTINEL_2", "CHEST_1"], {"RELIC": "minecraft:candle"}),
    (build_greenwatch, HUB_SIZE_GREENWATCH, ANCHORS_HUB, {}),
    (build_proving, HUB_SIZE_PROVING, ANCHORS_HUB + ["RESIDENT_4", "RESIDENT_5", "RESIDENT_6", "RESIDENT_7", "STAIR_DOWN"], {}),
    (build_sepulchre, (33, 16, 41), ["HEART", "ROOM_1", "ROOM_2", "ROOM_3", "ECHO"], {}),
    (build_heartstone, (19, 12, 19), ["HEART", "STONE"], {"STONE": HEARTSTONE}),
    (build_hollow_surface, (13, 10, 13), ["HEART", "GUARD"], {}),
    (build_hollow_grotto, (31, 16, 31), ["HEART", "CHIME_1", "CHIME_2", "CHIME_3", "CHIME_4", "CHIME_5", "CHIME_6", "WEIGHT_1", "WEIGHT_2",
                                         "WEIGHT_3", "WEIGHT_4", "WEIGHT_5", "WEIGHT_6", "GATE", "RELIC"], {"GATE": SEAL, "RELIC": KYBER}),
    (build_rift_surface, (15, 8, 15), ["HEART"], {}),
    (build_rift, (23, 28, 31), ["HEART", "RELIC", "SQUAD_1", "SQUAD_2"], {"RELIC": KYBER}),
    (build_wayfarer_cache, (13, 9, 13), ["HEART", "CACHE", "PLATFORM_1", "PLATFORM_2", "PLATFORM_3"], {}),
    (build_hunting_camp, (41, 14, 41), ["HEART", "NEST_1", "NEST_2", "NEST_3", "NEST_4", "GATE", "MINE_1", "MINE_2", "MINE_3", "MINE_4",
                                        "MINE_5", "MINE_6"], {"GATE": CAMP_GATE}),
    (natural(build_ambush_rocks), (13, 6, 13), ["HEART"], {}),
]

M4_POOLS = {
    "waystone/start": [Element("planet/waystone", m3.RIGID, "minecraft:empty", m3.adapted(2, 9, 3, 1))],
    "greater_tomb/start": [Element("greater_tomb/gate", m3.RIGID, TOMB_W, m3.adapted(3, 14, 8, 2))],
    "greater_tomb/descent": [Element("greater_tomb/descent", m3.RIGID, TOMB_W)],
    "greater_tomb/vault": [Element("greater_tomb/urn_vault", m3.RIGID, TOMB_W)],
    "greater_tomb/crypt": [Element("greater_tomb/sealed_crypt", m3.RIGID, TOMB_W)],
}

M4_STRUCTURES = [
    Structure("waystone", "waystone/start", 1, 16, {"planet/waystone": 1}, {WAYSTONE: (1, 1)}),
    Structure("greater_tomb", "greater_tomb/start", 4, 96,
              {"greater_tomb/gate": 1, "greater_tomb/descent": 1, "greater_tomb/urn_vault": 1, "greater_tomb/sealed_crypt": 1},
              {SEALED: (3, 9), KEEPER: (1, 1), ECHO_URN: (4, 4)}),
]

# Structure sets (random spread; the salts are fixed so every world seed lays them out the same way).
M4_SETS = {
    "waystones": ("waystone", 20, 8, 905117),
    "greater_tombs": ("greater_tomb", 36, 14, 905123),
}


def m4_structure_json(structure, templates):
    start = templates[M4_POOLS[structure.start_pool][0].template]
    return {
        "type": "minecraft:jigsaw",
        "biomes": "#oldrepublic:has_structure/" + structure.name,
        "liquid_settings": "ignore_waterlogging",
        "max_distance_from_center": structure.max_distance,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": structure.size,
        "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": []}},
        # As in gen_structures_m3.py: layer `buried` (the feet layer) of the start lands on the first free block.
        "start_height": {"absolute": 1 - start.buried},
        "start_pool": MOD + structure.start_pool,
        "step": structure.step,
        "terrain_adaptation": "none",
        "use_expansion_hack": False,
    }


def write_templates():
    enum_names = java_anchor_names()
    templates = {}
    for builder, size, expected, blocks in M4_TEMPLATES:
        t = builder()
        if not hasattr(t, "natural_below"):
            t.natural_below = False
        check_m4(t, size, expected, blocks, enum_names)
        t.expected = expected
        templates[t.name] = t
    for name, elements in M4_POOLS.items():
        for e in elements:
            if e.template not in templates:
                raise SystemExit(f"pool {name} names an unknown template {e.template}")
    pools = dict(M4_POOLS)
    for structure in M4_STRUCTURES:
        pieces = assemble(structure, pools, templates)
        print(f"{structure.name}: " + ", ".join(f"{p[0]} at {p[1]} {p[2]} (depth {p[3]})" for p in pieces))
    for name, t in templates.items():
        n, p = t.save(os.path.join(DATA, "structure", name + ".nbt"))
        sidecar(os.path.join(DATA, "site_anchors", name + ".json"), {a: t.anchors[a] for a in t.expected})
        print(f"{name}: size {t.size}, {n} blocks, {p} palette entries, anchors {', '.join(t.expected)}")
    for name, elements in M4_POOLS.items():
        write_json(os.path.join(DATA, "worldgen", "template_pool", name + ".json"), m3.pool_json(elements))
    for structure in M4_STRUCTURES:
        write_json(os.path.join(DATA, "worldgen", "structure", structure.name + ".json"), m4_structure_json(structure, templates))
    for set_name, (structure, spacing, separation, salt) in M4_SETS.items():
        write_json(os.path.join(DATA, "worldgen", "structure_set", set_name + ".json"), {
            "placement": {"type": "minecraft:random_spread", "salt": salt, "separation": separation, "spacing": spacing},
            "structures": [{"structure": MOD + structure, "weight": 1}],
        })
        write_json(os.path.join(DATA, "tags", "worldgen", "structure", set_name + ".json"),
                   {"values": [{"id": MOD + structure, "required": False}]})


def main():
    router = None
    if "--router" in sys.argv:
        router = sys.argv[sys.argv.index("--router") + 1]
    write_planets(router)
    write_templates()


if __name__ == "__main__":
    main()
