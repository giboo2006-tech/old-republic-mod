#!/usr/bin/env python3
"""Generates W3's Milestone 5 world data: Ghessar's and Tyrvann's worldgen JSON, the M5 structure templates
(gzipped structure NBT) and their anchor sidecars, the four worldgen structures with their pools, sets and
tags. See docs/design/planets_m5.md.

Deterministic: the same script always writes byte-identical files (fixed seeds, the NBT writer and Template
class of gen_structures.py, the Site helpers of gen_structures_m3.py, the JSON helpers of gen_structures_m4.py,
fixed gzip mtime). Writes under src/main/resources/data/oldrepublic/:

  Planet data (spec MILESTONE_5.md 3.2, 3.3)
    dimension_type/{ghessar,tyrvann}.json         the overworld type with the planet's sky, fog, clouds, music,
                                                  cave mood and the M4 bed rule
    dimension/{ghessar,tyrvann}.json              noise generator, multi_noise source of the planet's three biomes
    worldgen/noise_settings/{ghessar,tyrvann}     the vanilla overworld router (read from M4's verbatim copy in
                                                  selvaren.json) with the terrain remapped: the planet's own
                                                  offset, factor, depth and sloped cheese (below) replace the
                                                  vanilla ones in final_density, preliminary_surface_level and
                                                  the depth slot; sea level, aquifers and the surface rule
    worldgen/density_function/{ghessar,tyrvann}/  offset (the vanilla offset remapped piecewise-linearly),
                                                  factor, depth and sloped_cheese
    worldgen/biome/<six biomes>.json              colours, precipitation, spawners (vanilla mobs only), features
    worldgen/configured_feature, placed_feature   stormwood trees, sunstone ore and the W3 Java decorations; and
                                                  Tyrvann's clan iron ore (Milestone 7, docs/MILESTONE_7.md 4.2)
    tags/worldgen/biome/{is_ghessar,is_tyrvann,is_planet}

  Templates (spec 3.2.1, 3.3.1 to 3.4): see M5_TEMPLATES below
    structure/<template>.nbt, site_anchors/<template>.json

  Worldgen structures (spec 3.2, 3.3): planet/{crashed_freighter,kajidic_post,hymn_shrine,mire_grave}, one piece
    each, with their pools, random-spread sets, has_structure biome tags and StructureLocator's structure tags
    (#oldrepublic:{crashed_freighters,kajidic_posts,hymn_shrines,mire_graves}).

Terrain (spec 3.2 "flattened continentalness and a shifted erosion range", 3.3 "raised jaggedness on the ridges"):
the vanilla terrain splines read the registered continentalness and erosion functions, not the router's climate
slots, so the planets reshape the spline's output instead. `offset` (the surface height: y = 63.5 + 128 * (offset +
0.50375)) is remapped through the knots below with clamped linear ramps (only density function types that appear
in the vanilla router are used: add, mul, clamp, max, cache_2d, quarter_negative, y_clamped_gradient, noise):

  Ghessar: the old ocean basins become dry salt pans at y 48 to 58, the lowlands a dune sea at y 62 to 76, and
           every higher ground is terraced into mesa steps with short steep risers (canyon country to y ~131);
           the factor is floored at 3.5 so the plateaus stay flat; no jaggedness.
  Tyrvann: the seas become shallow black water at y 54 to 62, the lowlands a jungle floor at y 63 to 71, and the
           mountains rise steeply into ridges (y 86 to 153) with the vanilla jaggedness raised 1.3x and a slide
           above y 196 that keeps the crests near y 160 to 210.

The climate slots stay vanilla; CLIMATE picks the biomes from them so the salt pans and the mire follow the old
seas and the canyons and the basalt follow the mountains.

Rules: those of gen_structures_m4.py (template-local x east, y up, z south; `buried` B puts layer B on the
stand; anchors hold their real blocks; no floor circles, sigils or glowing runes; biome feature lists cut
from one master order per planet).

Usage: python3 tools/assets/gen_structures_m5.py [--router <vanilla overworld noise_settings JSON>] [--planets [--out DIR]]

  --planets   writes only the planet data above (no templates, worldgen structures or loot): the way to change a
              planet's biomes or features without rewriting files that later milestones took over (Milestone 7
              added Tyrvann's clan iron so).
  --out DIR   with --planets: writes the files under DIR (the same paths below data/oldrepublic/) instead of the
              repository, to diff a change before it is made (the router is still read from the repository).
"""

import copy
import functools
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_structures_m3 as m3  # noqa: E402
import gen_structures_m4 as m4  # noqa: E402
from gen_structures import AIR, CAVE_AIR  # noqa: E402
from gen_structures_m3 import (  # noqa: E402
    CHEST, CONDUIT, IMP_PANEL, LANTERN_HUNG, MOD, PLATE, SCORCHED, SPAWNER, Element, Site, Structure, campfire, clear_above, log, noise,
    stairs, write_json,
)
from gen_structures_m4 import (  # noqa: E402
    ABOVE_SURFACE, BEDROCK_FLOOR, BIOME, BLACK_BRICKS, BLACK_BRICKS_CRACKED, BLACKSTONE, CHAIN, COARSE, CONTRACT_TERMINAL, DEEPSLATE, DRY,
    FULL, HUB_SPAWNER, IN_SQUARE, LADDER, ORES, PAD_TERMINAL, RED_SAND, RED_SANDSTONE, SEALED, SMOOTH_RED, biome_is, block, chain_y,
    climate, cluster, cond, count, fence, heightmap, height_range, hub_spawner, java_feature, ladder, lamp_post, mod, negate, pick,
    rarity, rule_block, seq, shell_below, slab, spawn, stone_depth, surface, surface_noise, terminal, vanilla, wall_post, y_above,
)

DATA = m3.DATA


# =============================================================================================
# Terrain: the remapped offset, factor, depth and sloped cheese (density functions)
# =============================================================================================
OFFSET_BASE = -0.50375  # the constant the vanilla offset adds to its spline

# (vanilla height r, planet height r'), both as offset + 0.50375; r runs from about -0.2222 (deep ocean) to 1.0.
TERRAIN_KNOTS = {
    "ghessar": [
        (-0.23, -0.115), (-0.12, -0.08), (-0.05, -0.045), (0.0, -0.008), (0.10, 0.035), (0.22, 0.08), (0.28, 0.10),
        # Mesa steps: a flat tread, then a riser of about 11 blocks over 0.015 of offset.
        (0.30, 0.105), (0.315, 0.195), (0.42, 0.205), (0.435, 0.30), (0.56, 0.31), (0.575, 0.40), (0.74, 0.415),
        (0.755, 0.50), (1.10, 0.53),
    ],
    "tyrvann": [
        (-0.23, -0.07), (-0.12, -0.05), (-0.05, -0.022), (0.0, -0.006), (0.10, 0.012), (0.25, 0.035), (0.33, 0.06),
        # The ridges rise steeply out of the jungle floor.
        (0.42, 0.18), (0.58, 0.42), (0.78, 0.60), (1.10, 0.70),
    ],
}
GHESSAR_FACTOR_FLOOR = 3.5
TYRVANN_JAGGEDNESS = 1.3
TYRVANN_SLIDE = (196, 240, -1.8)  # from_y, to_y, density at to_y


def df(name):
    return MOD + name


def add(a, b):
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def clamp(value, lo, hi):
    return {"type": "minecraft:clamp", "input": value, "max": hi, "min": lo}


def gradient(from_y, to_y, from_value, to_value):
    return {"type": "minecraft:y_clamped_gradient", "from_value": from_value, "from_y": from_y, "to_value": to_value,
            "to_y": to_y}


def piecewise(source, knots):
    """A piecewise-linear map of `source` through `knots` [(x, y)]: y0 plus one clamped ramp per segment."""
    out = knots[0][1]
    for (x0, y0), (x1, y1) in zip(knots, knots[1:]):
        if x1 <= x0:
            raise SystemExit(f"knots must rise: {x0} then {x1}")
        ramp = clamp(mul(1.0 / (x1 - x0), add(source, -x0)), 0.0, 1.0)
        out = add(out, mul(round(y1 - y0, 6), ramp))
    return out


def evaluate(knots, x):
    """The piecewise map in Python, for the checks below."""
    if x <= knots[0][0]:
        return knots[0][1]
    for (x0, y0), (x1, y1) in zip(knots, knots[1:]):
        if x <= x1:
            return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    return knots[-1][1]


def density_functions(planet):
    """The planet's registered density functions (path -> JSON)."""
    knots = [(x + OFFSET_BASE, y + OFFSET_BASE) for x, y in TERRAIN_KNOTS[planet]]
    offset = {"type": "minecraft:cache_2d", "argument": piecewise("minecraft:overworld/offset", knots)}
    out = {f"{planet}/offset": offset}
    if planet == "ghessar":
        factor = df("ghessar/factor")
        out["ghessar/factor"] = {"type": "minecraft:cache_2d",
                                 "argument": {"type": "minecraft:max", "argument1": "minecraft:overworld/factor",
                                              "argument2": GHESSAR_FACTOR_FLOOR}}
    else:
        factor = "minecraft:overworld/factor"
    depth = add(gradient(-64, 320, 1.5, -1.5), df(f"{planet}/offset"))
    out[f"{planet}/depth"] = depth
    shaped = df(f"{planet}/depth")
    if planet == "tyrvann":
        # The jagged noise as vanilla samples it for its peaks (a column value), and half_negative(x) = max(x, x / 2).
        out["tyrvann/jagged"] = {"type": "minecraft:cache_2d",
                                 "argument": {"type": "minecraft:noise", "noise": "minecraft:jagged", "xz_scale": 1500.0, "y_scale": 0.0}}
        jagged = df("tyrvann/jagged")
        half_negative = {"type": "minecraft:max", "argument1": jagged, "argument2": mul(0.5, jagged)}
        shaped = add(shaped, mul(mul(TYRVANN_JAGGEDNESS, "minecraft:overworld/jaggedness"), half_negative))
    cheese = add(mul(4.0, {"type": "minecraft:quarter_negative", "argument": mul(shaped, {"type": "minecraft:cache_2d", "argument": factor})}),
                 "minecraft:overworld/base_3d_noise")
    if planet == "tyrvann":
        cheese = add(cheese, gradient(TYRVANN_SLIDE[0], TYRVANN_SLIDE[1], 0.0, TYRVANN_SLIDE[2]))
    out[f"{planet}/sloped_cheese"] = cheese
    return out


def substitute(node, names):
    """A copy of a router tree with the named density function references replaced."""
    if isinstance(node, str):
        return names.get(node, node)
    if isinstance(node, dict):
        return {k: substitute(v, names) for k, v in node.items()}
    if isinstance(node, list):
        return [substitute(v, names) for v in node]
    return node


def count_refs(node, name):
    if isinstance(node, str):
        return 1 if node == name else 0
    if isinstance(node, dict):
        return sum(count_refs(v, name) for v in node.values())
    if isinstance(node, list):
        return sum(count_refs(v, name) for v in node)
    return 0


def planet_router(planet, vanilla_router):
    names = {"minecraft:overworld/sloped_cheese": df(f"{planet}/sloped_cheese"),
             "minecraft:overworld/offset": df(f"{planet}/offset"),
             "minecraft:overworld/depth": df(f"{planet}/depth")}
    if planet == "ghessar":
        names["minecraft:overworld/factor"] = df("ghessar/factor")
    for name, slot in (("minecraft:overworld/sloped_cheese", "final_density"), ("minecraft:overworld/offset", "preliminary_surface_level"),
                       ("minecraft:overworld/factor", "preliminary_surface_level"), ("minecraft:overworld/depth", "depth")):
        if count_refs(vanilla_router[slot], name) == 0:
            raise SystemExit(f"the source router's {slot} does not read {name}: pass the vanilla overworld noise settings")
    router = substitute(copy.deepcopy(vanilla_router), names)
    for name in names:
        if count_refs(router, name):
            raise SystemExit(f"{planet}: {name} is still referenced after the substitution")
    return router


def check_terrain():
    """The knots keep the spec's heights: Ghessar's pans under its dunes and its mesas under y 135; Tyrvann's ridges
    from the jungle floor to about y 153 before jaggedness; both maps rise monotonically."""
    def y(planet, r):
        return 63.52 + 128 * evaluate(TERRAIN_KNOTS[planet], r)
    for planet, knots in TERRAIN_KNOTS.items():
        values = [v for _, v in knots]
        if values != sorted(values):
            raise SystemExit(f"{planet}: the terrain map must not fall")
    checks = [
        ("ghessar", -0.2222, 45, 52), ("ghessar", -0.12, 50, 57), ("ghessar", 0.05, 62, 70), ("ghessar", 0.2, 68, 76),
        ("ghessar", 1.0, 125, 135), ("tyrvann", -0.12, 54, 60), ("tyrvann", 0.15, 63, 70), ("tyrvann", 0.6, 110, 125),
        ("tyrvann", 1.0, 145, 155),
    ]
    for planet, r, lo, hi in checks:
        if not lo <= y(planet, r) <= hi:
            raise SystemExit(f"{planet}: offset {r} lands at y {y(planet, r):.1f}, expected {lo}-{hi}")


# =============================================================================================
# Surface rules
# =============================================================================================
SALT_CRUST = MOD + "salt_crust"
PEAT = MOD + "peat"


def ghessar_surface():
    """Sand over sandstone in the dunes (sandstone under overhangs, so nothing falls); salt crust over sandstone on
    the pans with pale calcite and sand where the crust has cracked; red sand floors and banded terracotta walls in
    the canyons, with coarse dirt and terracotta on the high mesa tops. No sea (sea level -64)."""
    sand_lined = seq(cond(stone_depth("ceiling"), rule_block("minecraft:sandstone")), rule_block("minecraft:sand"))
    salt_top = seq(
        cond(surface_noise(-0.08, 0.02), rule_block("minecraft:calcite")),
        cond(surface_noise(0.55, 0.6), rule_block("minecraft:sand")),
        rule_block(SALT_CRUST),
    )
    canyon_top = seq(
        cond(y_above(97, add_stone_depth=True, multiplier=1), seq(
            cond(surface_noise(-0.909, -0.5454), rule_block("minecraft:coarse_dirt")),
            cond(surface_noise(0.5454, 0.909), rule_block("minecraft:terracotta")),
            {"type": "minecraft:bandlands"})),
        cond(stone_depth("ceiling"), rule_block("minecraft:red_sandstone")),
        rule_block("minecraft:red_sand"),
    )
    return seq(
        BEDROCK_FLOOR,
        cond(ABOVE_SURFACE, seq(
            cond(stone_depth(), seq(
                cond(biome_is("ghessar_dunes"), sand_lined),
                cond(biome_is("ghessar_saltflat"), salt_top),
                cond(biome_is("ghessar_canyons"), canyon_top),
            )),
            cond(stone_depth(add_surface_depth=True), seq(
                cond(biome_is("ghessar_dunes"), sand_lined),
                cond(biome_is("ghessar_saltflat"), rule_block("minecraft:sandstone")),
                cond(biome_is("ghessar_canyons"), seq(
                    cond(y_above(64, add_stone_depth=True, multiplier=1), {"type": "minecraft:bandlands"}),
                    rule_block("minecraft:red_sandstone"))),
            )),
            # A sandstone bed a few blocks under the dunes and the pans, as in vanilla deserts.
            cond(stone_depth(add_surface_depth=True, secondary=6), seq(
                cond(biome_is("ghessar_dunes", "ghessar_saltflat"), rule_block("minecraft:sandstone")))),
            # Canyon walls and mesa faces show the bands, as on Varkhast's redrock.
            cond(biome_is("ghessar_canyons"), cond(y_above(50, add_stone_depth=True, multiplier=-1), {"type": "minecraft:bandlands"})),
        )),
        DEEPSLATE,
    )


def tyrvann_surface():
    """Grass, moss and podzol over dirt in the stormwood (mud where it sinks under the water line); mud over peat in
    the mire, with peat breaking the surface in patches; basalt, smooth basalt and blackstone on the ridges, whose
    faces are black all the way up (sea level 62)."""
    stormwood_top = seq(
        cond(negate(y_above(63)), rule_block("minecraft:mud")),
        cond(surface_noise(-0.95, -0.55), rule_block("minecraft:podzol", {"snowy": "false"})),
        cond(surface_noise(0.55, 0.75), rule_block("minecraft:moss_block")),
        cond(surface_noise(-0.1, 0.05), rule_block("minecraft:coarse_dirt")),
        rule_block("minecraft:grass_block", {"snowy": "false"}),
    )
    mire_top = seq(
        cond(surface_noise(-0.3, 0.05), rule_block(PEAT)),
        cond(y_above(66), cond(surface_noise(0.4, 1.0e9), rule_block("minecraft:grass_block", {"snowy": "false"}))),
        rule_block("minecraft:mud"),
    )
    basalt_rock = seq(
        cond(surface_noise(-0.2, 0.15), rule_block("minecraft:smooth_basalt")),
        cond(surface_noise(0.5, 1.0e9), rule_block("minecraft:blackstone")),
        rule_block("minecraft:basalt", {"axis": "y"}),
    )
    basalt_top = seq(
        cond(negate(y_above(80)), cond(surface_noise(-1.0e9, -0.4), rule_block("minecraft:moss_block"))),
        basalt_rock,
    )
    return seq(
        BEDROCK_FLOOR,
        cond(ABOVE_SURFACE, seq(
            cond(stone_depth(), seq(
                cond(biome_is("tyrvann_stormwood"), stormwood_top),
                cond(biome_is("tyrvann_mire"), mire_top),
                cond(biome_is("tyrvann_basalt"), basalt_top),
            )),
            cond(stone_depth(add_surface_depth=True), seq(
                cond(biome_is("tyrvann_stormwood"), seq(cond(negate(y_above(63)), rule_block("minecraft:mud")), rule_block("minecraft:dirt"))),
                cond(biome_is("tyrvann_mire"), seq(cond(surface_noise(0.3, 1.0e9), rule_block("minecraft:mud")), rule_block(PEAT))),
                cond(biome_is("tyrvann_basalt"), basalt_rock),
            )),
            cond(stone_depth(add_surface_depth=True, secondary=4), seq(
                cond(biome_is("tyrvann_mire"), rule_block("minecraft:clay")))),
            # The ridges are black rock through and through above the surface estimate.
            cond(biome_is("tyrvann_basalt"), seq(
                cond(surface_noise(0.3, 1.0e9), rule_block("minecraft:blackstone")),
                rule_block("minecraft:basalt", {"axis": "y"}))),
        )),
        DEEPSLATE,
    )


# =============================================================================================
# Planets, dimension types and climate
# =============================================================================================
PLANETS = {
    "ghessar": {
        "sea_level": -64,
        "aquifers": False,
        "surface": ghessar_surface,
        # No clouds: fully transparent (ARGB), so the pale amber sky stays open.
        "sky": "#e9c89a", "fog": "#d8b27a", "cloud": "#00e9dcc4", "cloud_height": 192.33, "ambient_light": "#14100a",
        "music": MOD + "music.ghessar",
    },
    "tyrvann": {
        "sea_level": 62,
        "aquifers": True,
        "surface": tyrvann_surface,
        # Low, heavy, opaque clouds.
        "sky": "#4a5a52", "fog": "#2f3a35", "cloud": "#ff3c4640", "cloud_height": 150.0, "ambient_light": "#0a100c",
        "music": MOD + "music.tyrvann",
    },
}


def dimension_type_json(planet):
    """The M4 planet type (gen_structures_m4.dimension_type_json) with the planet's colours, clouds and music."""
    p = PLANETS[planet]
    out = m4.dimension_type_json("selvaren")
    attributes = out["attributes"]
    attributes["minecraft:audio/background_music"] = {
        "creative": {"max_delay": 24000, "min_delay": 12000, "sound": p["music"]},
        "default": {"max_delay": 24000, "min_delay": 9000, "sound": p["music"]},
    }
    attributes["minecraft:visual/ambient_light_color"] = p["ambient_light"]
    attributes["minecraft:visual/cloud_color"] = p["cloud"]
    attributes["minecraft:visual/cloud_height"] = p["cloud_height"]
    attributes["minecraft:visual/fog_color"] = p["fog"]
    attributes["minecraft:visual/sky_color"] = p["sky"]
    return out


# Vanilla climate bands (TerrainProvider): continentalness ocean < -0.19 < coast < -0.11 < near inland < 0.03 < mid and far
# inland; erosion E0-E1 < -0.375 < E2 < -0.2225 < E3 and up.
SEA = [-1.2, -0.16]
COAST_TO_NEAR = [-0.16, 0.03]
MID_FAR = [0.03, 1.0]
RUGGED = [-1.0, -0.2225]
GENTLE = [-0.2225, 1.0]
STEEP = [-1.0, -0.375]
NOT_STEEP = [-0.375, 1.0]
VALLEY = [-0.05, 0.05]
CLIMATE = {
    # The pans follow the old seas, the canyons the rugged inland, the dunes everything between.
    "ghessar": [
        climate("ghessar_saltflat", SEA),
        climate("ghessar_dunes", COAST_TO_NEAR),
        climate("ghessar_dunes", MID_FAR, erosion=GENTLE),
        climate("ghessar_canyons", MID_FAR, erosion=RUGGED),
    ],
    # The mire follows the old seas and the lowland valleys, the basalt the mountains, the stormwood the rest.
    "tyrvann": [
        climate("tyrvann_mire", SEA),
        climate("tyrvann_mire", [-0.16, 1.0], VALLEY, erosion=NOT_STEEP),
        climate("tyrvann_stormwood", COAST_TO_NEAR, [-1.0, -0.05]),
        climate("tyrvann_stormwood", COAST_TO_NEAR, [0.05, 1.0]),
        climate("tyrvann_stormwood", MID_FAR, [-1.0, -0.05], erosion=NOT_STEEP),
        climate("tyrvann_stormwood", MID_FAR, [0.05, 1.0], erosion=NOT_STEEP),
        climate("tyrvann_basalt", MID_FAR, erosion=STEEP),
    ],
}


def dimension_json(planet):
    return {"type": MOD + planet, "generator": {
        "type": "minecraft:noise",
        "biome_source": {"type": "minecraft:multi_noise", "biomes": CLIMATE[planet]},
        "settings": MOD + planet,
    }}


def noise_settings_json(planet, kept):
    p = PLANETS[planet]
    base = dict(kept)
    base["noise_router"] = planet_router(planet, kept["noise_router"])
    base["aquifers_enabled"] = p["aquifers"]
    base["default_block"] = block("minecraft:stone")
    base["default_fluid"] = block("minecraft:water", {"level": "0"})
    base["disable_mob_generation"] = False
    base["legacy_random_source"] = False
    base["ore_veins_enabled"] = True
    base["sea_level"] = p["sea_level"]
    base["surface_rule"] = p["surface"]()
    return base


# =============================================================================================
# Features
# =============================================================================================
# One master order per planet and step; each biome takes a subsequence (FeatureSorter needs one order).
MASTER = {
    "ghessar": [
        [],
        vanilla("lake_lava_underground"),
        vanilla("amethyst_geode") + mod("bleached_ribs", "salt_pillar"),
        vanilla("monster_room", "monster_room_deep"),
        [], [],
        vanilla(*ORES) + vanilla("ore_gold_extra") + mod("ore_sunstone"),
        [],
        vanilla("spring_lava"),
        vanilla("patch_dead_bush_badlands", "patch_dry_grass_badlands"),
        [],
    ],
    "tyrvann": [
        [],
        vanilla("lake_lava_underground"),
        vanilla("amethyst_geode") + mod("mire_pool"),
        vanilla("monster_room", "monster_room_deep"),
        [], [],
        vanilla(*ORES) + vanilla("disk_clay", "disk_gravel") + mod("ore_clan_iron"),
        [],
        vanilla("spring_water", "spring_lava"),
        mod("stormwood_giant", "stormwood_trees", "mire_trees", "mire_roots", "basalt_spire", "stormglass_tops", "hanging_moss")
        + vanilla("glow_lichen", "patch_large_fern", "patch_grass_taiga", "brown_mushroom_old_growth", "red_mushroom_old_growth",
                  "patch_sugar_cane", "patch_firefly_bush_near_water"),
        [],
    ],
}

COMMON = {
    "ghessar": {1: vanilla("lake_lava_underground"), 2: vanilla("amethyst_geode"), 3: vanilla("monster_room", "monster_room_deep"),
                6: vanilla(*ORES), 8: vanilla("spring_lava")},
    "tyrvann": {1: vanilla("lake_lava_underground"), 2: vanilla("amethyst_geode"), 3: vanilla("monster_room", "monster_room_deep"),
                6: vanilla(*ORES), 8: vanilla("spring_water", "spring_lava")},
}


def features(planet, extra):
    """The biome's 11 steps: the planet's common features plus `extra` (step -> ids), in master order."""
    out = []
    for step, master in enumerate(MASTER[planet]):
        wanted = set(COMMON[planet].get(step, [])) | set(extra.get(step, []))
        unknown = wanted - set(master)
        if unknown:
            raise SystemExit(f"{planet}: features {sorted(unknown)} are not in the master order of step {step}")
        out.append([f for f in master if f in wanted])
    return out


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


def biome_json(planet, colours, temperature, downfall, rain, spawn_lists, extra, creature_probability=None, particles=None):
    sky, fog, water, water_fog, grass, foliage = colours
    attributes = {
        "minecraft:visual/fog_color": fog,
        "minecraft:visual/sky_color": sky,
        "minecraft:visual/water_fog_color": water_fog,
    }
    if particles:
        attributes["minecraft:visual/ambient_particles"] = particles
    out = {
        "attributes": attributes,
        "carvers": vanilla("cave", "cave_extra_underground", "canyon"),
        "downfall": downfall,
        "effects": {"foliage_color": foliage, "grass_color": grass, "water_color": water},
        "features": features(planet, extra),
        "has_precipitation": rain,
        "spawn_costs": {},
        "spawners": spawn_lists,
        "temperature": temperature,
    }
    if creature_probability is not None:
        out["creature_spawn_probability"] = creature_probability
    return out


GHESSAR_MONSTERS = [spawn("husk", 80, 4, 4), spawn("spider", 40, 4, 4), spawn("skeleton", 40, 4, 4), spawn("enderman", 5, 1, 4)]
TYRVANN_MONSTERS = [spawn("zombie", 60, 4, 4), spawn("spider", 60, 4, 4), spawn("skeleton", 40, 4, 4), spawn("enderman", 5, 1, 4)]
# Salt dust drifting over the pans (the white haze); the basalt deltas use the same particle at 0.118.
SALT_HAZE = [{"particle": {"type": "minecraft:white_ash"}, "probability": 0.004}]

BIOMES = {
    # (sky, fog, water, water fog, grass, foliage)
    "ghessar_dunes": lambda: biome_json(
        "ghessar", ("#e9c89a", "#d8b27a", "#5d7f7a", "#3a4f4b", "#b3a065", "#a39361"), 2.0, 0.0, False,
        spawners(creature=[spawn("rabbit", 4, 2, 3)], monster=GHESSAR_MONSTERS),
        {2: mod("bleached_ribs"), 9: vanilla("patch_dead_bush_badlands", "patch_dry_grass_badlands")},
        creature_probability=0.02),
    "ghessar_saltflat": lambda: biome_json(
        "ghessar", ("#ecd2a8", "#e4d6bc", "#6a8a84", "#40524e", "#c2b27e", "#b0a072"), 2.0, 0.0, False,
        spawners(monster=GHESSAR_MONSTERS, ambient=False),
        {2: mod("salt_pillar")},
        creature_probability=0.01, particles=SALT_HAZE),
    "ghessar_canyons": lambda: biome_json(
        "ghessar", ("#e6c290", "#cf9e6a", "#5d7f7a", "#3a4f4b", "#a8905a", "#9a8450"), 2.0, 0.0, False,
        spawners(creature=[spawn("rabbit", 2, 1, 2)], monster=GHESSAR_MONSTERS),
        {6: vanilla("ore_gold_extra") + mod("ore_sunstone"), 9: vanilla("patch_dead_bush_badlands", "patch_dry_grass_badlands")},
        creature_probability=0.02),
    "tyrvann_stormwood": lambda: biome_json(
        "tyrvann", ("#4a5a52", "#2f3a35", "#26332f", "#101814", "#3f6b3c", "#2f5a2e"), 0.9, 0.9, True,
        spawners(creature=[spawn("parrot", 10, 1, 2)], monster=TYRVANN_MONSTERS),
        {9: mod("stormwood_giant", "stormwood_trees", "hanging_moss")
         + vanilla("glow_lichen", "patch_large_fern", "patch_grass_taiga", "brown_mushroom_old_growth", "red_mushroom_old_growth")},
        creature_probability=0.04),
    "tyrvann_mire": lambda: biome_json(
        "tyrvann", ("#47564e", "#29332e", "#141b18", "#0b100e", "#4a5e3a", "#3d5236"), 0.8, 0.9, True,
        spawners(creature=[spawn("frog", 10, 2, 5)], monster=TYRVANN_MONSTERS + [spawn("witch", 3, 1, 1)]),
        {2: mod("mire_pool"), 6: vanilla("disk_clay", "disk_gravel"),
         9: mod("mire_trees", "mire_roots", "hanging_moss")
         + vanilla("glow_lichen", "patch_grass_taiga", "patch_sugar_cane", "patch_firefly_bush_near_water")},
        creature_probability=0.04),
    "tyrvann_basalt": lambda: biome_json(
        "tyrvann", ("#4d5b56", "#30363a", "#1f2626", "#0e1212", "#55684a", "#4d5c45"), 0.7, 0.9, True,
        spawners(monster=TYRVANN_MONSTERS),
        {6: mod("ore_clan_iron"), 9: mod("basalt_spire", "stormglass_tops") + vanilla("patch_grass_taiga")},
        creature_probability=0.01),
}


def tree(trunk, leaves, base, rand_a, rand_b, radius, height, vines):
    """The vanilla tree format of jungle_tree (m4_worldgen digest 18): a straight trunk under a blob crown, with
    trunk vines and hanging leaf vines."""
    out = m4.tree(trunk, leaves, base, rand_a, rand_b, radius, height)
    out["config"]["decorators"] = [{"type": "minecraft:trunk_vine"}, {"type": "minecraft:leave_vine", "probability": vines}]
    return out


SUNSTONE_ORE = MOD + "sunstone_ore"
CLAN_IRON_ORE = MOD + "clan_iron_ore"
CLAN_IRON_RANGE = (64, 200)  # the ridges' basalt, from their feet above the black water to the crests under the slide
CONFIGURED = {
    # Stormwood: dark oak bark under jungle crowns, hung with vines (spec 3.3); the giants are Java (stormwood_giant).
    "stormwood_tree": tree("minecraft:dark_oak_log", "minecraft:jungle_leaves", 10, 5, 3, 3, 4, 0.35),
    "stormwood_tree_low": tree("minecraft:dark_oak_log", "minecraft:jungle_leaves", 6, 3, 1, 2, 3, 0.5),
    # Sunstone in the canyon walls, whether the wall is stone, terracotta or red sandstone (spec 3.2: y 40 to 120).
    "ore_sunstone": {"type": "minecraft:ore", "config": {"discard_chance_on_air_exposure": 0.0, "size": 6, "targets": [
        {"state": block(SUNSTONE_ORE), "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
        {"state": block(SUNSTONE_ORE), "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:terracotta"}},
        {"state": block(SUNSTONE_ORE), "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:red_sandstone"}},
    ]}},
    # Clan iron in the ridges' black rock (docs/MILESTONE_7.md 4.2): only basalt is replaced, so a vein never shows
    # in the stone under the ridges, the blackstone patches or the other biomes.
    "ore_clan_iron": {"type": "minecraft:ore", "config": {"discard_chance_on_air_exposure": 0.0, "size": 5, "targets": [
        {"state": block(CLAN_IRON_ORE), "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:basalt"}},
    ]}},
}
# W3's M5 Java decorations (planet.worldgen.PlanetDecorFeature.Kind); M4's basalt_spire is reused on Tyrvann's ridges.
JAVA_FEATURES = ["bleached_ribs", "salt_pillar", "stormwood_giant", "hanging_moss", "mire_roots", "mire_pool", "stormglass_tops"]
for _name in JAVA_FEATURES:
    CONFIGURED[_name] = java_feature(_name)

# Trees only where a sapling could take root, as vanilla's trees are placed, and in the mire on its peat too: a tree
# never stands on a structure's stone or floor (docs/MILESTONE_6.md, the Tyrvann structures).
SAPLING_GROUND = {"type": "minecraft:would_survive", "state": {"Name": "minecraft:dark_oak_sapling", "Properties": {"stage": "0"}}}
ROOTED = {"type": "minecraft:block_predicate_filter", "predicate": SAPLING_GROUND}
ROOTED_OR_PEAT = {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:any_of", "predicates": [
    SAPLING_GROUND, {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": PEAT}]}}

PLACED = {
    "bleached_ribs": ("bleached_ribs", surface(rarity(12))),
    "salt_pillar": ("salt_pillar", surface(rarity(6))),
    "ore_sunstone": ("ore_sunstone", [count(9), IN_SQUARE, height_range(40, 120), BIOME]),
    "ore_clan_iron": ("ore_clan_iron", [count(8), IN_SQUARE, height_range(*CLAN_IRON_RANGE), BIOME]),
    "stormwood_giant": ("stormwood_giant", surface(rarity(2))),
    "stormwood_trees": ("stormwood_tree", [count(7), IN_SQUARE, DRY, heightmap("OCEAN_FLOOR"), ROOTED, BIOME]),
    "mire_trees": ("stormwood_tree_low", [count(2), IN_SQUARE, DRY, heightmap("OCEAN_FLOOR"), ROOTED_OR_PEAT, BIOME]),
    "mire_roots": ("mire_roots", surface(count(2), kind="WORLD_SURFACE_WG")),
    "mire_pool": ("mire_pool", surface(rarity(2))),
    "hanging_moss": ("hanging_moss", surface(count(3))),
    "stormglass_tops": ("stormglass_tops", surface(count(2))),
}
# Placed features of M4 that Tyrvann reads (written by gen_structures_m4.py).
M4_PLACED = {"basalt_spire"}


def write_planets(router_path, out=None):
    """Writes the planet data under DATA, or under `out` (the same relative paths) for a dry run."""
    check_terrain()
    root = out or DATA
    source_path = router_path or os.path.join(DATA, "worldgen", "noise_settings", "selvaren.json")
    with open(source_path, encoding="utf-8") as source:
        vanilla_settings = json.load(source)
    kept = {k: vanilla_settings[k] for k in ("noise", "noise_router", "spawn_target")}
    for planet in PLANETS:
        for path, value in density_functions(planet).items():
            write_json(os.path.join(root, "worldgen", "density_function", path + ".json"), value)
        write_json(os.path.join(root, "worldgen", "noise_settings", planet + ".json"), noise_settings_json(planet, kept))
        write_json(os.path.join(root, "dimension_type", planet + ".json"), dimension_type_json(planet))
        write_json(os.path.join(root, "dimension", planet + ".json"), dimension_json(planet))
    for name, make in BIOMES.items():
        write_json(os.path.join(root, "worldgen", "biome", name + ".json"), make())
    for name, value in CONFIGURED.items():
        write_json(os.path.join(root, "worldgen", "configured_feature", name + ".json"), value)
    for name, (feature, placement) in PLACED.items():
        write_json(os.path.join(root, "worldgen", "placed_feature", name + ".json"),
                   {"feature": feature if ":" in feature else MOD + feature, "placement": placement})
    used = {f for planet in MASTER for step in MASTER[planet] for f in step if f.startswith(MOD)}
    missing = {f for f in used if f[len(MOD):] not in PLACED and f[len(MOD):] not in M4_PLACED}
    if missing:
        raise SystemExit(f"placed features named in a biome but not written: {sorted(missing)}")
    for name in M4_PLACED:
        if not os.path.exists(os.path.join(DATA, "worldgen", "placed_feature", name + ".json")):
            raise SystemExit(f"the M4 placed feature {name} is missing: run gen_structures_m4.py first")
    for planet in PLANETS:
        biomes = [b for b in BIOMES if b.startswith(planet)]
        named = sorted({c["biome"][len(MOD):] for c in CLIMATE[planet]})
        if named != sorted(biomes):
            raise SystemExit(f"{planet}: the climate names {named}, the biomes are {sorted(biomes)}")
    tags = os.path.join(root, "tags", "worldgen", "biome")
    write_json(os.path.join(tags, "is_ghessar.json"), {"values": [MOD + b for b in BIOMES if b.startswith("ghessar")]})
    write_json(os.path.join(tags, "is_tyrvann.json"), {"values": [MOD + b for b in BIOMES if b.startswith("tyrvann")]})
    write_json(os.path.join(tags, "is_planet.json"), {"values": IS_PLANET})
    print(f"planets: {len(PLANETS)} dimensions, {len(BIOMES)} biomes, {len(CONFIGURED)} configured and {len(PLACED)} placed features")


# The four planets' biome tags (gen_structures_m4.py writes the same list).
IS_PLANET = ["#oldrepublic:is_selvaren", "#oldrepublic:is_varkhast", "#oldrepublic:is_ghessar", "#oldrepublic:is_tyrvann"]


# =============================================================================================
# Templates (spec 3.2 to 3.4): the runtime sites, the Hymnal, the hubs and the worldgen pieces
# =============================================================================================
VERSE_STONE = MOD + "verse_stone"
HOLDFAST_GATE = MOD + "holdfast_gate"
STORMGLASS = MOD + "stormglass_cluster"
BASALT = "minecraft:basalt"
POLISHED_BASALT = "minecraft:polished_basalt"
SMOOTH_BASALT = "minecraft:smooth_basalt"
POLISHED_BLACK = "minecraft:polished_blackstone"
CHISELED_BLACK = "minecraft:chiseled_polished_blackstone"
BRICK_STAIRS = "minecraft:polished_blackstone_brick_stairs"
BRICK_SLAB = "minecraft:polished_blackstone_brick_slab"
BRICK_WALL = "minecraft:polished_blackstone_brick_wall"
SOUL_LANTERN = "minecraft:soul_lantern"
LANTERN_BLOCK = "minecraft:lantern"
LIGHTNING_ROD = "minecraft:lightning_rod"
LECTERN = "minecraft:lectern"
IRON_BARS = "minecraft:iron_bars"
IRON_DOOR = "minecraft:iron_door"
LEVER = "minecraft:lever"
BARREL = "minecraft:barrel"
SAND = "minecraft:sand"
SANDSTONE = "minecraft:sandstone"
CUT_SANDSTONE = "minecraft:cut_sandstone"
SMOOTH_SANDSTONE = "minecraft:smooth_sandstone"
CHISELED_SANDSTONE = "minecraft:chiseled_sandstone"
CUT_RED = "minecraft:cut_red_sandstone"
DEEPSLATE_TILES = "minecraft:deepslate_tiles"
POLISHED_DEEPSLATE = "minecraft:polished_deepslate"
GRAVEL = "minecraft:gravel"
MUD = "minecraft:mud"
MUD_BRICKS = "minecraft:mud_bricks"
PACKED_MUD = "minecraft:packed_mud"
MANGROVE_LOG = "minecraft:mangrove_log"
MANGROVE_PLANKS = "minecraft:mangrove_planks"
DARK_OAK_LOG = "minecraft:dark_oak_log"
DARK_OAK_PLANKS = "minecraft:dark_oak_planks"
SPRUCE_PLANKS = "minecraft:spruce_planks"
SPRUCE_FENCE = "minecraft:spruce_fence"


# Spawner groups: W8 registers the first four (12.1), planet.PlanetM5Feature the storm-callers' posts.
HYMNSWORN = "hymnsworn"
STORMBACKS = "stormbacks"
CARTEL_ENFORCERS = "cartel_enforcers"
HASK_WARRIORS = "hask_warriors"
STORM_CALLERS = "storm_callers"


def rod(facing="up"):
    return {"facing": facing, "powered": "false", "waterlogged": "false"}


def barrel(facing="up"):
    return {"facing": facing, "open": "false"}


def door(facing, half, hinge="left"):
    return {"facing": facing, "half": half, "hinge": hinge, "open": "false", "powered": "false"}


def lever(facing):
    """A wall lever pointing `facing`: it hangs on the block on its other side."""
    return {"face": "wall", "facing": facing, "powered": "false"}


def lectern(facing):
    return {"facing": facing, "has_book": "false", "powered": "false"}


def trapdoor(facing, half="top", open_=False):
    return {"facing": facing, "half": half, "open": "true" if open_ else "false", "powered": "false", "waterlogged": "false"}


def ring_cell(cx, cz, r, degrees):
    """The cell at `r` blocks from (cx, cz) toward `degrees` (0 east, 90 south), rounded half up."""
    a = math.radians(degrees)
    return int(math.floor(cx + r * math.cos(a) + 0.5)), int(math.floor(cz + r * math.sin(a) + 0.5))


SIDES = (("north", 0, -1), ("south", 0, 1), ("east", 1, 0), ("west", -1, 0))


def joins(t, x, y, z, cells, dx, dz):
    """Whether a pane, fence or wall at (x, y, z) joins toward (dx, dz): a member of `cells` or a full block there."""
    n = (x + dx, y, z + dz)
    return n in cells or m3.is_conductor_name(t.get(*n))


def connect_bars(t, cells, block=IRON_BARS):
    """Iron bars on `cells`, each joined to the others and to the full blocks beside it (set the blocks first)."""
    cells = set(cells)
    for (x, y, z) in sorted(cells):
        props = {"waterlogged": "false"}
        for side, dx, dz in SIDES:
            props[side] = "true" if joins(t, x, y, z, cells, dx, dz) else "false"
        t.set(x, y, z, block, props)


def connect_fences(t, cells, block=SPRUCE_FENCE):
    cells = set(cells)
    for (x, y, z) in sorted(cells):
        t.set(x, y, z, block, fence(**{side: joins(t, x, y, z, cells, dx, dz) for side, dx, dz in SIDES}))


def connect_walls(t, cells, block):
    """Wall blocks on `cells`: low sides toward their neighbours, a post where they do not run straight through."""
    cells = set(cells)
    for (x, y, z) in sorted(cells):
        props = {"waterlogged": "false"}
        for side, dx, dz in SIDES:
            props[side] = "low" if joins(t, x, y, z, cells, dx, dz) else "none"
        straight = (props["north"] != "none" and props["south"] != "none" and props["east"] == "none" and props["west"] == "none") \
            or (props["east"] != "none" and props["west"] != "none" and props["north"] == "none" and props["south"] == "none")
        props["up"] = "false" if straight and t.get(x, y + 1, z) in (None, AIR, CAVE_AIR) else "true"
        t.set(x, y, z, block, props)


def basalt_rock(seed):
    """Tyrvann's ridge rock (names only: shell and fill helpers set plain names, so basalt keeps its default axis)."""
    def fn(x, y, z):
        return pick(seed, x, y, z, [(5, BASALT), (3, SMOOTH_BASALT), (2, BLACKSTONE)])
    return fn


def hymn_masonry(seed):
    def fn(x, y, z):
        return pick(seed, x, y, z, [(7, BLACK_BRICKS), (2, BLACK_BRICKS_CRACKED), (1, POLISHED_BLACK)])
    return fn


# ---------------------------------------------------------------------------------------------
# Checks: the M4 rules with the Milestone 5 anchors
# ---------------------------------------------------------------------------------------------
BARREL_NAME = BARREL
M5_ANCHOR_BLOCKS = {
    "SQUAD": SPAWNER, "SQUAD_1": SPAWNER, "SQUAD_2": SPAWNER, "SQUAD_3": SPAWNER, "SENTINEL_1": SPAWNER, "SENTINEL_2": SPAWNER,
    "GUARD": SPAWNER, "CHEST_1": CHEST, "STRONGBOX": CHEST, "CRATE": BARREL_NAME, "STONE": VERSE_STONE, "HOLDFAST_GATE": HOLDFAST_GATE,
    "HEART_DOOR": SEALED, "UNDERCROFT_DOOR": SEALED, "KEYSTONE": CHISELED_BLACK, "LECTERN": LECTERN,
    "PAD_TERMINAL": PAD_TERMINAL, "CONTRACT_TERMINAL": CONTRACT_TERMINAL,
}
for _name in ["HOST", "CANTINA", "VENDOR_FENCE", "NPC_VARRA", "COUNTING_HOUSE_DOOR", "NPC_VESSBA", "NPC_TEEL", "VENDOR_SHIPWRIGHT",
              "VENDOR_SPACER", "NPC_DORRUN", "VENDOR_ARMOURER"] + [f"ENFORCER_{i}" for i in range(1, 7)] + [f"WARRIOR_{i}" for i in range(1, 7)]:
    M5_ANCHOR_BLOCKS[_name] = HUB_SPAWNER
for _i in range(1, 5):
    M5_ANCHOR_BLOCKS[f"ROD_{_i}"] = LIGHTNING_ROD
M5_FEET = {"HEART", "SURFACE_GUARD", "STAIR_MOUTH", "MEDBAY", "THRESHOLD", "DAIS", "CANTOR", "NPC_DEBTOR", "NPC_DASK", "WARDEN", "SPAWNER"} \
    | {f"PAD_BAY_{i}" for i in range(1, 5)} | {f"COUNTING_HOUSE_{i}" for i in range(1, 9)} | {f"CREW_{i}" for i in range(1, 5)} \
    | {f"PRISONER_{i}" for i in range(1, 4)} | {f"GUARD_{i}" for i in range(1, 5)} | {f"PYLON_{i}" for i in range(1, 3)} \
    | {f"RESONATOR_{i}" for i in range(1, 5)} | {f"CHOIR_{i}" for i in range(1, 5)} | {f"SILENCE_{i}" for i in range(1, 4)} \
    | {f"RING_{i}" for i in range(1, 4)}
ROOM_ABOVE_M5 = (SPAWNER, HUB_SPAWNER, PAD_TERMINAL, CONTRACT_TERMINAL)


def stands_on_block(t, cell):
    below = t.get(cell[0], cell[1] - 1, cell[2])
    return cell[1] == 0 or (below is None and t.natural_below) or (below is not None and m3.is_conductor_name(below))


def check_m5(t, size, expected, blocks, enum_names):
    """The M4 template checks with the Milestone 5 anchors: the size, the exact anchor set (each a SiteAnchors.Anchor),
    every anchor inside the template and holding its block, feet anchors two cells of air on a full block, hub spawners
    with room above and a full block below, spawners and terminals with room above, jigsaws facing
    out of their template."""
    if t.size != size:
        raise SystemExit(f"{t.name}: size {t.size}, expected {size}")
    if sorted(t.anchors) != sorted(expected):
        raise SystemExit(f"{t.name}: anchors {sorted(t.anchors)}, expected {sorted(expected)}")
    unknown = [a for a in expected if a not in enum_names]
    if unknown:
        raise SystemExit(f"{t.name}: anchors {unknown} are not in SiteAnchors.Anchor")
    for anchor, cells in t.anchors.items():
        block = blocks.get(anchor, M5_ANCHOR_BLOCKS.get(anchor))
        for cell in cells:
            if not t.inside(*cell):
                raise SystemExit(f"{t.name}: {anchor} {cell} is outside the template")
            if block is not None and t.get(*cell) != block:
                raise SystemExit(f"{t.name}: {anchor} {cell} holds {t.get(*cell)}, expected {block}")
            if anchor in M5_FEET:
                if t.get(*cell) not in (AIR, CAVE_AIR) or t.get(cell[0], cell[1] + 1, cell[2]) not in (AIR, CAVE_AIR):
                    raise SystemExit(f"{t.name}: {anchor} {cell} is not two cells of air")
                if not stands_on_block(t, cell):
                    raise SystemExit(f"{t.name}: {anchor} {cell} does not stand on a block ({t.get(cell[0], cell[1] - 1, cell[2])})")
            if block == HUB_SPAWNER and not stands_on_block(t, cell):
                raise SystemExit(f"{t.name}: the hub spawner {anchor} {cell} does not stand on a block")
    for pos, cell in t.cells.items():
        if cell[0] in ROOM_ABOVE_M5 and t.get(pos[0], pos[1] + 1, pos[2]) not in (AIR, CAVE_AIR):
            raise SystemExit(f"{t.name}: the {cell[0]} at {pos} has no room above it")
        if cell[0] == "minecraft:jigsaw" and not any(j[0] == pos for j in t.jigsaws):
            raise SystemExit(f"{t.name}: stray jigsaw at {pos}")
    for (jpos, orientation, *_rest) in t.jigsaws:
        if t.inside(*m3.step_of(jpos, orientation.split("_")[0])):
            raise SystemExit(f"{t.name}: the jigsaw at {jpos} faces into its own template")


# ---------------------------------------------------------------------------------------------
# Verse stone (runtime, per player, Tyrvann; 3.3.2): a ring of ten black stones 11 across around a 5-block
# monolith with the verse_stone block at its heart (eye height from the stand). The ground is left to the
# biome (stormwood, mire or basalt) except the old flagstones around the monolith.
# ---------------------------------------------------------------------------------------------
VERSE_SIZE = (15, 10, 15)
VERSE_CENTRE = 7


def build_verse_stone():
    t = Site("site/verse_stone", VERSE_SIZE, 2)
    c = VERSE_CENTRE

    def flag(x, y, z):
        return pick(503, x, y, z, [(5, POLISHED_BLACK), (3, BLACK_BRICKS), (2, BLACK_BRICKS_CRACKED)])

    for x in range(15):
        for z in range(15):
            d = math.hypot(x - c, z - c)
            if d <= 1.5 or d <= 3.6 and noise(501, x, 1, z) > 0.18:
                t.set(x, 1, z, flag(x, 1, z))
                t.set(x, 0, z, BLACKSTONE)
    # The foot: steps rising to the monolith on its four sides, slabs at the corners.
    for dx, dz, facing in ((0, -1, "south"), (0, 1, "north"), (-1, 0, "east"), (1, 0, "west")):
        t.set(c + dx, 2, c + dz, BRICK_STAIRS, stairs(facing))
    for dx in (-1, 1):
        for dz in (-1, 1):
            t.set(c + dx, 2, c + dz, BRICK_SLAB, slab())
    # The monolith: carved base, the singing stone, three courses of basalt, a capstone.
    t.set(c, 2, c, CHISELED_BLACK)
    t.set(c, 3, c, VERSE_STONE, {"awake": "true"}, {"id": VERSE_STONE})
    t.mark("STONE", c, 3, c)
    t.set(c, 4, c, POLISHED_BASALT, {"axis": "y"})
    t.set(c, 5, c, BASALT, {"axis": "y"})
    t.set(c, 6, c, BASALT, {"axis": "y"})
    t.set(c, 7, c, BRICK_SLAB, slab())
    # Two leaning shoulder stones east and west, so the stone shows its face north and south.
    for dx in (-1, 1):
        t.set(c + dx, 3, c, BASALT, {"axis": "y"})
        t.set(c + dx, 4, c, BRICK_SLAB, slab())
    # The ring: ten stones of one to three courses at radius 5 (gaps north and south, where the paths come in).
    rock = hymn_masonry(507)
    for k in range(10):
        x, z = ring_cell(c, c, 5, 36 * k)
        h = 1 + int(noise(509, k, 0, 0) * 3)
        t.set(x, 0, z, BLACKSTONE)
        t.set(x, 1, z, BLACKSTONE)
        for y in range(2, 2 + h):
            t.set(x, y, z, rock(x, y, z) if y > 2 else BLACKSTONE)
        top = 2 + h
        if k in (2, 6):
            t.set(x, top, z, STORMGLASS, cluster("up"))
        elif noise(511, k, 1, 0) > 0.5:
            t.set(x, top, z, BRICK_SLAB, slab())
    t.set(c, 1, 3, flag(c, 1, 3))
    t.set(c, 1, 11, flag(c, 1, 11))
    t.spot("HEART", c, 2, 3)
    t.spot("SPAWNER", c, 2, 11)
    clear_above(t, 2)
    return t


# ---------------------------------------------------------------------------------------------
# Debtor camp (runtime, per player, Ghessar's canyons; 3.2, 6.3): desperate salvagers behind a scrap barricade
# around a fire; the strongbox in the east lean-to. NPC_DEBTOR and SPAWNER are feet cells: W4 spawns the debtor
# and, after force, the salvagers (the scavengers group) there.
# ---------------------------------------------------------------------------------------------
DEBTOR_SIZE = (23, 10, 23)


def build_debtor_camp():
    t = Site("site/debtor_camp", DEBTOR_SIZE, 2)
    c = 11
    scrap = lambda x, y, z: pick(515, x, y, z, [(5, PLATE), (3, SCORCHED), (1, CUT_RED)])  # noqa: E731
    # Trampled ground in the middle; the canyon floor around is left as it is.
    for x in range(23):
        for z in range(23):
            if math.hypot(x - c, z - c) <= 8.5 and noise(511, x, 1, z) > 0.3:
                t.set(x, 1, z, pick(513, x, 1, z, [(5, RED_SAND), (3, COARSE), (2, GRAVEL)]))
    # The barricade: scrap plates, barrels and posts at radius 9 to 10, open to the north-west and south-east.
    barricade = []
    for x in range(23):
        for z in range(23):
            d = math.hypot(x - c, z - c)
            a = math.degrees(math.atan2(z - c, x - c)) % 360
            if 9.0 <= d < 10.2 and not (205 <= a <= 245 or 25 <= a <= 65):
                barricade.append((x, z))
    for x, z in barricade:
        t.set(x, 1, z, RED_SANDSTONE)
        t.set(x, 0, z, RED_SANDSTONE)
        r = noise(517, x, 2, z)
        if r < 0.18:
            t.set(x, 2, z, BARREL, barrel("up"))
            if r < 0.07:
                t.set(x, 3, z, BARREL, barrel("up"))
        else:
            t.set(x, 2, z, scrap(x, 2, z))
            if noise(519, x, 3, z) > 0.45:
                t.set(x, 3, z, scrap(x, 3, z))
    # Two lean-tos: the debtor's (north-east, the strongbox under it) and the crew's (south-west).
    for (x0, x1, zb, zf) in ((14, 18, 4, 7), (4, 8, 18, 15)):
        step = 1 if zf > zb else -1
        for x in range(x0, x1 + 1):
            for y in range(2, 5):
                t.set(x, y, zb, scrap(x, y, zb))
            for i, z in enumerate(range(zb, zf + step, step)):
                t.set(x, 5 if i < 2 else 4, z, "minecraft:brown_wool" if (x + i) % 3 else "minecraft:light_gray_wool")
                t.set(x, 1, z, COARSE)
                if i > 0:
                    for y in range(2, 5 if i < 2 else 4):
                        t.keep(x, y, z, AIR)
        for x in (x0, x1):
            for y in range(2, 4):
                t.set(x, y, zf, SPRUCE_FENCE, fence())
        m3.lantern(t, x0, 4, zf)
    t.set(16, 2, 5, CHEST, {"facing": "south", "type": "single", "waterlogged": "false"}, {"id": CHEST})
    t.mark("STRONGBOX", 16, 2, 5)
    t.set(17, 2, 5, BARREL, barrel("up"))
    t.set(15, 2, 6, "minecraft:brown_carpet")
    t.set(5, 2, 17, "minecraft:gray_carpet")
    t.set(6, 2, 17, "minecraft:gray_carpet")
    t.set(8, 2, 16, BARREL, barrel("up"))
    t.set(7, 2, 17, "minecraft:crafting_table")
    # The fire and its benches; the debtor waits north of it.
    t.set(c, 2, c, "minecraft:campfire", campfire())
    t.set(c - 2, 2, c, "minecraft:spruce_log", log("z"))
    t.set(c + 2, 2, c, "minecraft:spruce_log", log("z"))
    for x, z in ((c, 8), (c, 14), (6, 14)):
        t.set(x, 1, z, COARSE)
    t.spot("NPC_DEBTOR", c, 2, 8)
    t.spot("HEART", c, 2, 14)
    t.spot("SPAWNER", 6, 2, 14)
    # Salvage heaped by the east barricade: plates, a conduit and a broken speeder fin.
    for x, y, z in ((17, 2, 14), (18, 2, 14), (18, 2, 15), (17, 2, 15), (18, 3, 15), (16, 2, 16)):
        t.set(x, y, z, scrap(x, y, z))
    t.set(17, 3, 14, CONDUIT)
    t.set(16, 2, 15, "minecraft:iron_bars", m3.bars(east=True))
    lamp_post(t, 13, 2, 9, 2)
    clear_above(t, 2)
    return t


# ---------------------------------------------------------------------------------------------
# Detention post (runtime, per player, Varkhast; 3.4, 8.3): a fenced yard with its gate north, a guard tower in
# the north-east corner and the cell block south with three barred cells. Each cell's iron door opens with the
# lever beside it in the corridor (the prisoners walk out once W5 frees them; W5 also brings them along when they
# fall behind). Every anchor is a feet cell: W5 spawns the warden, the prisoners and the four guards.
# ---------------------------------------------------------------------------------------------
DETENTION_SIZE = (31, 16, 31)
DETENTION_CELLS = ((5, 10, 8), (13, 17, 15), (20, 25, 22))  # (x0, x1, door x): the lever sits on the pillar east of the door


def build_detention_post():
    t = Site("site/detention_post", DETENTION_SIZE, 2)

    def yard(x, y, z):
        return pick(521, x, y, z, [(6, "minecraft:polished_andesite"), (3, GRAVEL), (2, "minecraft:andesite")])

    for x in range(2, 29):
        for z in range(2, 29):
            t.set(x, 1, z, yard(x, 1, z))
            t.set(x, 0, z, "minecraft:stone")
    # The fence: panel posts every four blocks with bars between, the gate north (x 14 to 16).
    bar_cells = []
    for x in range(1, 30):
        for z in range(1, 30):
            if x not in (1, 29) and z not in (1, 29):
                continue
            t.set(x, 0, z, "minecraft:stone")
            if z == 1 and 14 <= x <= 16:
                t.set(x, 1, z, yard(x, 1, z))
                continue
            t.set(x, 1, z, IMP_PANEL)
            post = x in (1, 29) and (z - 1) % 4 == 0 or z in (1, 29) and (x - 1) % 4 == 0
            if post:
                for y in range(2, 6):
                    t.set(x, y, z, IMP_PANEL)
            else:
                bar_cells += [(x, y, z) for y in range(2, 5)]
    for x in (13, 17):
        m3.lantern(t, x, 6, 1)

    # The guard tower (x 22 to 26, z 3 to 7): a panel shaft with a ladder, an open platform with a railing and a canopy.
    for x in range(22, 27):
        for z in range(3, 8):
            t.set(x, 1, z, POLISHED_DEEPSLATE)
            wall = x in (22, 26) or z in (3, 7)
            for y in range(2, 11):
                t.set(x, y, z, IMP_PANEL if wall else AIR)
    for x in range(21, 28):
        for z in range(2, 9):
            t.set(x, 11, z, POLISHED_DEEPSLATE)
    t.set(22, 2, 5, AIR)
    t.set(22, 3, 5, AIR)
    for y in range(2, 12):
        t.set(25, y, 5, LADDER, ladder("west"))
    rail = [(x, 12, z) for x in range(21, 28) for z in range(2, 9) if x in (21, 27) or z in (2, 8)]
    for x, z in ((21, 2), (27, 2), (21, 8), (27, 8)):
        rail.remove((x, 12, z))
        for y in (12, 13):
            t.set(x, y, z, "minecraft:polished_deepslate_wall", wall_post())
    connect_bars(t, rail)
    for x in range(21, 28):
        for z in range(2, 9):
            t.set(x, 14, z, "minecraft:polished_deepslate_slab", slab())
    t.set(24, 13, 5, LANTERN_BLOCK, LANTERN_HUNG)
    for x in range(22, 27):
        for z in range(3, 8):
            if not (x in (22, 26) or z in (3, 7)) and (x, 12, z) not in t.cells:
                t.set(x, 12, z, AIR)
                t.set(x, 13, z, AIR)

    # The cell block (x 4 to 26, z 17 to 27): the corridor z 18 to 21, three cells z 23 to 26 behind barred fronts.
    for x in range(4, 27):
        for z in range(17, 28):
            t.set(x, 1, z, POLISHED_DEEPSLATE)
            t.set(x, 0, z, "minecraft:stone")
            wall = x in (4, 26) or z in (17, 27)
            for y in range(2, 7):
                t.set(x, y, z, IMP_PANEL if wall else AIR)
            t.set(x, 7, z, POLISHED_DEEPSLATE)
            if wall and (x + z) % 2 == 0:
                t.set(x, 8, z, IMP_PANEL)
    for x in range(14, 17):
        for y in range(2, 5):
            t.set(x, y, 17, AIR)
    t.set(13, 5, 17, "minecraft:polished_deepslate")
    t.set(17, 5, 17, "minecraft:polished_deepslate")
    for x0, x1, dx in DETENTION_CELLS:
        for x in range(x0 - 1, x1 + 2):
            if x0 <= x <= x1:
                continue
            for z in range(22, 27):
                for y in range(2, 7):
                    t.set(x, y, z, IMP_PANEL)
        for x in range(x0, x1 + 1):
            for y in (5, 6):
                t.set(x, y, 22, IMP_PANEL)
        for y in range(2, 5):
            t.set(dx + 1, y, 22, IMP_PANEL)
        t.set(dx, 2, 22, IRON_DOOR, door("north", "lower"))
        t.set(dx, 3, 22, IRON_DOOR, door("north", "upper"))
        t.set(dx, 4, 22, IMP_PANEL)
        t.set(dx + 1, 3, 21, LEVER, lever("north"))
        t.set(x1, 2, 26, "minecraft:smooth_stone_slab", slab())
        t.set(x1, 2, 25, "minecraft:smooth_stone_slab", slab())
        t.set(x0, 2, 26, "minecraft:cauldron")
        t.set((x0 + x1) // 2, 6, 25, LANTERN_BLOCK, LANTERN_HUNG)
    fronts = [(x, y, 22) for x0, x1, dx in DETENTION_CELLS for x in range(x0, x1 + 1) if x not in (dx, dx + 1) for y in range(2, 5)]
    connect_bars(t, fronts)
    for i, (x0, x1, dx) in enumerate(DETENTION_CELLS):
        t.spot(f"PRISONER_{i + 1}", dx - 1, 2, 25)
    # The warden's desk at the corridor's west end; lamps along the corridor.
    t.set(7, 2, 18, POLISHED_DEEPSLATE)
    t.set(7, 2, 19, "minecraft:polished_deepslate_slab", slab("top"))
    t.set(7, 3, 18, LANTERN_BLOCK, m3.LANTERN)
    t.set(5, 2, 18, BARREL, barrel("up"))
    t.spot("WARDEN", 6, 2, 20)
    for x in (9, 15, 21):
        t.set(x, 6, 19, LANTERN_BLOCK, LANTERN_HUNG)
    t.spot("GUARD_3", 11, 2, 20)
    t.spot("GUARD_4", 19, 2, 20)

    # The yard: supply crates, two flood posts, the gate guards.
    for x, y, z in ((4, 2, 4), (5, 2, 4), (4, 2, 5), (4, 3, 4), (8, 2, 3), (9, 2, 3)):
        t.set(x, y, z, BARREL, barrel("up"))
    for x, z in ((5, 13), (25, 13)):
        lamp_post(t, x, 2, z, 3, "minecraft:polished_deepslate_wall")
    t.spot("GUARD_1", 12, 2, 3)
    t.spot("GUARD_2", 18, 2, 3)
    t.spot("HEART", 15, 2, 10)
    connect_bars(t, bar_cells)
    clear_above(t, 2)
    return t


# ---------------------------------------------------------------------------------------------
# The Hymnal (Tyrvann, runtime, one per server; spec 3.3.3): site/hymnal_arch, the arch over the cleft with the
# head of the processional, and site/hymnal, the halls right under it: the rest of the processional, the Heart
# and the engine's undercroft. Both are built in one frame, the arch's template coordinates ("A"): the halls'
# template origin lies at HYMNAL_HALLS_OFFSET in it, so the halls' top layer is the layer under the arch's
# layer 0, hymnalDepth (18) layers below the arch's stand (layer 17), and the Java side (planet.Hymnal) stamps
# the halls at the arch's origin plus that offset, turned the same way.
#
#   Arch (y 0 to 30):   the mouth (STAIR_MOUTH, z 2 on the surface); flight A down the open cleft (x 6..8, z 4..9)
#                       under the arch's span (piers x 1..3 and 11..13, z 4..6); landing A (z 10..12); flight B
#                       back north (x 10..12); landing B (z 1..4); flight C south (x 6..8, z 5..8) to layer 0
#   Halls (y -1 to -28): the junction (z 9..12), flight 1, landing 1 (SQUAD_1, the storm-caller post SENTINEL_1
#                       in its west alcove), flight 2, landing 2 (SQUAD_2, SENTINEL_2 in the east alcove, the pit
#                       west of it with the stormback GUARD), flight 3, landing 3 (SQUAD_3), the Heart's door
#                       (HEART_DOOR, z 53) and, east of landing 3, the undercroft's door (UNDERCROFT_DOOR, x 11)
#                       with its stair down to the undercroft
#   The Heart:          a round hall 31 across (floor y -19) under a dome, centred on HYMNAL_HEART: twelve
#                       organ pipes of basalt and stormglass on a ring of 6 (the dormant song-engine), the rods,
#                       pylons and resonator spots of the fight, the dais with the echo's lectern to the south
#   The undercroft:     a lower round hall (floor y -27) under the Heart: the keystone on its base, the Cantor's
#                       rise beside it, the choir, silence and ring spots
# ---------------------------------------------------------------------------------------------
HYMNAL_ARCH_SIZE = (15, 31, 15)
HYMNAL_ARCH_BURIED = 17
HYMNAL_HALLS_SIZE = (37, 28, 79)
HYMNAL_HALLS_OFFSET = (-11, -28, 9)
HYMNAL_ARCH_TOP = (7, 30, 5)  # the keystone of the arch, where the cosmetic bolts strike (planet.Hymnal)
HYMNAL_HEART = (7, 69)  # the Heart's and the undercroft's centre column in the arch's frame
HEART_FLOOR = -19
UNDER_FLOOR = -27
HYMNAL_SEED = 905301


class _FrameCells:
    """`(x, y, z) in frame.cells`: whether the cell of the arch's frame is set in its template."""

    def __init__(self, frame):
        self.frame = frame

    def __contains__(self, pos):
        t, p = self.frame.locate(*pos)
        return p in t.cells


class HymnalFrame:
    """The Hymnal's two templates in the arch's frame: cells at layer 0 and above belong to the arch, cells
    below it to the halls (halls-local = frame - HYMNAL_HALLS_OFFSET). Setting a cell outside its template
    is a design error; reading one gives None (the natural ground)."""

    def __init__(self, arch, halls):
        self.arch = arch
        self.halls = halls
        self.cells = _FrameCells(self)

    def locate(self, x, y, z):
        if y >= 0:
            return self.arch, (x, y, z)
        ox, oy, oz = HYMNAL_HALLS_OFFSET
        return self.halls, (x - ox, y - oy, z - oz)

    def _inside(self, x, y, z):
        t, p = self.locate(x, y, z)
        if not t.inside(*p):
            raise SystemExit(f"{t.name}: ({x}, {y}, {z}) in the arch's frame lies outside the template")
        return t, p

    def get(self, x, y, z):
        t, p = self.locate(x, y, z)
        return t.get(*p) if t.inside(*p) else None

    def set(self, x, y, z, name, props=None, nbt=None):
        t, p = self._inside(x, y, z)
        t.set(*p, name, props, nbt)

    def put(self, x, y, z, block, props=None):
        if callable(block):
            block = block(x, y, z)
        if isinstance(block, tuple):
            self.set(x, y, z, block[0], block[1])
        elif block is not None:
            self.set(x, y, z, block, props)

    def keep(self, x, y, z, block, props=None):
        if (x, y, z) not in self.cells:
            self.put(x, y, z, block, props)

    def box(self, x0, y0, z0, x1, y1, z1, block, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, block, props)

    def air(self, x0, y0, z0, x1, y1, z1):
        self.box(x0, y0, z0, x1, y1, z1, AIR)

    def mark(self, anchor, x, y, z):
        t, p = self._inside(x, y, z)
        t.mark(anchor, *p)

    def spot(self, anchor, x, y, z):
        """A feet anchor: air at the feet and head cells."""
        self.set(x, y, z, AIR)
        self.set(x, y + 1, z, AIR)
        self.mark(anchor, x, y, z)

    def spawner(self, anchor, x, y, z, group, count=0):
        t, p = self._inside(x, y, z)
        t.spawner(anchor, *p, group, count)
        self.set(x, y + 1, z, AIR)


def hall_floor(seed):
    def fn(x, y, z):
        return pick(seed, x, y, z, [(6, BLACK_BRICKS), (2, POLISHED_BLACK), (1, BLACK_BRICKS_CRACKED)])
    return fn


def heart_floor(seed):
    def fn(x, y, z):
        return pick(seed, x, y, z, [(5, POLISHED_BLACK), (3, POLISHED_DEEPSLATE), (2, DEEPSLATE_TILES)])
    return fn


def hymnal_flight(f, x0, x1, zs, feet_of, facing, top=None):
    """A flight of brick stairs: for each z in `zs` the treads under feet_of(z), air from the feet three cells up
    (to `top` at most, where the ceiling must stay in the halls)."""
    for z in zs:
        feet = feet_of(z)
        for x in range(x0, x1 + 1):
            f.set(x, feet - 1, z, BRICK_STAIRS, m3.stairs(facing))
            for y in range(feet, feet + 3 if top is None else min(feet + 3, top(z) + 1)):
                f.set(x, y, z, AIR)


def hymnal_landing(f, x0, x1, z0, z1, floor, height, seed):
    f.box(x0, floor, z0, x1, floor, z1, hall_floor(seed))
    f.air(x0, floor + 1, z0, x1, floor + height, z1)


def wall_lamp(f, x, y, z, height=2):
    """A brick post of `height` with a soul lantern on it."""
    for dy in range(height):
        f.set(x, y + dy, z, BRICK_WALL, wall_post())
    f.set(x, y + height, z, SOUL_LANTERN, m3.LANTERN)


def hanging_lamp(f, x, z, y_lantern, y_top):
    """A soul lantern hung at y_lantern on a chain up to y_top (the cell above y_top must be the ceiling)."""
    for y in range(y_lantern + 1, y_top + 1):
        f.set(x, y, z, CHAIN, chain_y())
    f.set(x, y_lantern, z, SOUL_LANTERN, m3.LANTERN_HUNG)


def hymnal_arch_head(f):
    """The arch's part of the processional: mouth, flight A in the open cleft, landing A, flight B, landing B,
    flight C, the junction's headroom and flight 1's."""
    arch = f.arch
    hymnal_flight(f, 6, 8, range(4, 10), lambda z: 20 - z, "north")
    for z in range(4, 10):
        arch.air(6, 20 - z, z, 8, 30, z)  # the cleft is open to the sky
    hymnal_landing(f, 6, 12, 10, 12, 10, 3, HYMNAL_SEED + 1)  # landing A, feet 11
    hymnal_flight(f, 10, 12, range(9, 3, -1), lambda z: z + 1, "south")  # flight B, back north
    hymnal_landing(f, 6, 12, 1, 3, 4, 3, HYMNAL_SEED + 2)  # landing B, feet 5
    hymnal_landing(f, 6, 8, 4, 4, 4, 3, HYMNAL_SEED + 2)
    hymnal_flight(f, 6, 8, range(5, 9), lambda z: 9 - z, "north")  # flight C to layer 0
    arch.air(6, 0, 9, 8, 2, 12)  # the junction (its floor is the halls' top layer)
    arch.air(5, 0, 13, 9, 1, 13)  # flight 1's headroom
    arch.air(5, 0, 14, 9, 0, 14)
    wall_lamp(f, 12, 11, 12)
    wall_lamp(f, 6, 5, 1)
    arch.spot("STAIR_MOUTH", 7, 17, 2)


def hymnal_arch_above(arch, masonry, rock):
    """The ground around the mouth, and the arch: two piers rooted in the rock and a round span with the keystone
    on top, stormglass grown on the piers' outer faces."""
    c = 7
    for x in range(15):
        for z in range(15):
            r = math.hypot(x - c, z - c)
            if r <= 7.0:
                for y in range(12, 16):
                    arch.keep(x, y, z, rock)
            if r <= 7.5:
                paved = 5 <= x <= 9 and z <= 3
                arch.keep(x, 16, z, hall_floor(HYMNAL_SEED + 3) if paved else rock)
    for x0 in (1, 11):
        for x in range(x0, x0 + 3):
            for z in range(4, 7):
                for y in range(12, 25):
                    corner = x in (x0, x0 + 2) and z in (4, 6)
                    arch.set(x, y, z, POLISHED_BASALT if corner else masonry(x, y, z))
    for x in range(15):
        for y in range(24, 31):
            d = math.hypot(x - c, y - 24)
            if 4.5 <= d <= 6.5:
                for z in range(4, 7):
                    arch.set(x, y, z, masonry(x, y, z))
    arch.set(*HYMNAL_ARCH_TOP, CHISELED_BLACK)
    arch.set(0, 20, 5, STORMGLASS, cluster("west"))
    arch.set(14, 22, 5, STORMGLASS, cluster("east"))
    arch.set(0, 17, 4, STORMGLASS, cluster("west"))


def hymnal_processional(f):
    """The halls' part of the processional: the junction, three flights and three landings with their spawners,
    the storm-caller alcoves, the pit, the doorway into the Heart and the way down to the undercroft."""
    def below_arch(z):
        return 1 if z <= 14 else -2  # past the arch's box the ceiling (layer -1) stays in the halls

    for x in range(6, 9):
        f.set(x, -1, 9, BRICK_STAIRS, m3.stairs("north"))
    f.box(6, -1, 10, 8, -1, 12, hall_floor(HYMNAL_SEED + 4))
    hymnal_flight(f, 5, 9, range(13, 19), lambda z: 12 - z, "north", top=below_arch)  # flight 1, feet -1..-6
    # Landing 1: the first squad, the storm-caller's alcove to the west.
    hymnal_landing(f, 4, 10, 19, 25, -7, 5, HYMNAL_SEED + 5)
    hymnal_landing(f, 1, 3, 21, 23, -7, 4, HYMNAL_SEED + 5)
    f.spawner("SQUAD_1", 7, -6, 22, HYMNSWORN, 3)
    f.spawner("SENTINEL_1", 2, -6, 22, STORM_CALLERS, 1)
    wall_lamp(f, 4, -6, 19)
    wall_lamp(f, 10, -6, 25)
    hymnal_flight(f, 5, 9, range(26, 32), lambda z: 19 - z, "north")  # flight 2, feet -7..-12
    # Landing 2: the second squad, the east alcove's storm-caller, the pit with the stormback to the west.
    hymnal_landing(f, 4, 10, 32, 38, -13, 5, HYMNAL_SEED + 6)
    hymnal_landing(f, 11, 13, 34, 36, -13, 4, HYMNAL_SEED + 6)
    f.spawner("SQUAD_2", 7, -12, 35, HYMNSWORN, 3)
    f.spawner("SENTINEL_2", 12, -12, 35, STORM_CALLERS, 1)
    f.box(-4, -16, 32, 2, -16, 38, hall_floor(HYMNAL_SEED + 7))
    f.air(-4, -15, 32, 2, -8, 38)
    f.box(3, -15, 32, 3, -13, 38, BLACK_BRICKS)  # the rim between the landing and the pit
    f.air(3, -11, 32, 3, -8, 38)
    connect_walls(f, [(3, -12, z) for z in range(32, 39) if z != 33], BRICK_WALL)
    f.set(3, -12, 33, AIR)
    for y in range(-15, -12):
        f.set(2, y, 33, LADDER, ladder("west"))
    f.spawner("GUARD", -1, -15, 35, STORMBACKS, 1)
    wall_lamp(f, 10, -12, 38)
    hymnal_flight(f, 5, 9, range(39, 45), lambda z: 26 - z, "north")  # flight 3, feet -13..-18
    # Landing 3: the last squad; the Heart's door to the south, the undercroft's door to the east.
    hymnal_landing(f, 4, 10, 45, 51, -19, 5, HYMNAL_SEED + 8)
    f.spawner("SQUAD_3", 7, -18, 48, HYMNSWORN, 2)
    wall_lamp(f, 4, -18, 45)
    wall_lamp(f, 4, -18, 51)
    f.air(6, -18, 52, 8, -16, 52)
    for x in range(6, 9):
        for y in range(-18, -15):
            f.set(x, y, 53, SEALED)
            f.mark("HEART_DOOR", x, y, 53)
    for z in range(46, 49):
        for y in range(-18, -15):
            f.set(11, y, z, SEALED)
            f.mark("UNDERCROFT_DOOR", 11, y, z)
    hymnal_landing(f, 12, 14, 46, 48, -19, 3, HYMNAL_SEED + 9)  # the vestibule behind the undercroft's door
    hymnal_flight(f, 12, 14, range(49, 57), lambda z: 30 - z, "north")  # down to the undercroft, feet -19..-26
    hymnal_landing(f, 12, 14, 57, 60, UNDER_FLOOR, 3, HYMNAL_SEED + 10)


def hymnal_heart(f, masonry):
    """The Heart: the round hall, its dome, the song-engine's pipes, the fight's spots and the dais."""
    cx, cz = HYMNAL_HEART
    floor = HEART_FLOOR
    tiles = heart_floor(HYMNAL_SEED + 11)
    for x in range(cx - 17, cx + 18):
        for z in range(cz - 17, cz + 18):
            r = math.hypot(x - cx, z - cz)
            if r <= 15:
                f.set(x, floor, z, tiles(x, floor, z))
                f.air(x, floor + 1, z, x, floor + 7, z)
                for y in range(floor + 8, -1):
                    if math.hypot(r, (y + 12) * 15 / 10.5) <= 15:
                        f.set(x, y, z, AIR)
            elif r < 17.5:
                for y in range(floor, floor + 8):
                    f.set(x, y, z, masonry(x, y, z))
    for k in range(12):  # the organ pipes
        px, pz = ring_cell(cx, cz, 6, 30 * k)
        height = 6 + (k * 5) % 6
        for y in range(floor + 1, floor + 1 + height):
            f.set(px, y, pz, POLISHED_BASALT)
        f.set(px, floor + 1 + height, pz, STORMGLASS, cluster("up"))
    for i, angle in enumerate((45, 135, 225, 315)):  # the rods on their brick columns
        rx, rz = ring_cell(cx, cz, 12.5, angle)
        f.box(rx, floor + 1, rz, rx, floor + 3, rz, BLACK_BRICKS)
        f.set(rx, floor + 4, rz, LIGHTNING_ROD, rod("up"))
        f.mark(f"ROD_{i + 1}", rx, floor + 4, rz)
    for i, angle in enumerate((0, 180)):  # the pylons: a chanter's spot before a basalt pillar
        px, pz = ring_cell(cx, cz, 10, angle)
        f.spot(f"PYLON_{i + 1}", px, floor + 1, pz)
        qx, qz = ring_cell(cx, cz, 11.5, angle)
        f.box(qx, floor + 1, qz, qx, floor + 6, qz, POLISHED_BASALT)
        f.set(qx, floor + 7, qz, STORMGLASS, cluster("up"))
    for i, angle in enumerate((30, 150, 210, 330)):
        rx, rz = ring_cell(cx, cz, 9.5, angle)
        f.spot(f"RESONATOR_{i + 1}", rx, floor + 1, rz)
    for k in range(8):
        lx, lz = ring_cell(cx, cz, 14, 22.5 + 45 * k)
        wall_lamp(f, lx, floor + 1, lz)
    for angle in (0, 90, 180, 270):
        hx, hz = ring_cell(cx, cz, 9, angle)
        top = max(y for y in range(floor + 8, -1) if f.get(hx, y, hz) == AIR)
        hanging_lamp(f, hx, hz, floor + 9, top)
    # The dais to the south, the echo's lectern on it, a carved screen behind.
    f.box(5, floor + 1, 79, 9, floor + 1, 82, BLACK_BRICKS)
    f.set(7, floor + 1, 81, CHISELED_BLACK)
    for x in range(5, 10):
        f.set(x, floor + 1, 78, BRICK_STAIRS, m3.stairs("south"))
    f.spot("DAIS", 7, floor + 2, 81)
    f.set(9, floor + 2, 81, LECTERN, lectern("north"))
    f.mark("LECTERN", 9, floor + 2, 81)
    for y in range(floor + 1, floor + 8):
        f.set(5, y, 84, POLISHED_BASALT)
        f.set(9, y, 84, POLISHED_BASALT)
    f.box(6, floor + 1, 84, 8, floor + 6, 84, BLACK_BRICKS)
    f.set(7, floor + 4, 84, CHISELED_BLACK)
    f.set(7, floor + 6, 83, STORMGLASS, cluster("north"))
    f.spot("THRESHOLD", 7, floor + 1, 55)
    f.spot("HEART", cx, floor + 1, cz)


def hymnal_undercroft(f, masonry):
    """The engine's undercroft: a lower round hall with the keystone on its base, the Cantor's rise beside it and
    the choir, silence and ring spots; its box (UNDERCROFT_MIN, UNDERCROFT_MAX) bounds the raid."""
    cx, cz = HYMNAL_HEART
    floor = UNDER_FLOOR
    tiles = heart_floor(HYMNAL_SEED + 12)
    for x in range(cx - 14, cx + 15):
        for z in range(cz - 14, cz + 15):
            r = math.hypot(x - cx, z - cz)
            if r <= 12.9:
                f.set(x, floor, z, tiles(x, floor, z))
                f.air(x, floor + 1, z, x, floor + 6, z)
            if r <= 13.5:
                f.set(x, floor + 7, z, masonry(x, floor + 7, z))
            if r <= 2.2:
                f.set(x, floor + 1, z, POLISHED_BASALT if r > 1.5 else CHISELED_BLACK)
    f.set(cx, floor + 2, cz, CHISELED_BLACK)
    f.mark("KEYSTONE", cx, floor + 2, cz)
    f.spot("CANTOR", cx, floor + 2, cz - 2)
    for i, angle in enumerate((0, 120, 240)):
        rx, rz = ring_cell(cx, cz, 8.5, angle)
        f.spot(f"RING_{i + 1}", rx, floor + 1, rz)
    for i, angle in enumerate((60, 180, 300)):
        sx, sz = ring_cell(cx, cz, 8.5, angle)
        f.spot(f"SILENCE_{i + 1}", sx, floor + 1, sz)
    for i, angle in enumerate((30, 150, 210, 330)):
        qx, qz = ring_cell(cx, cz, 11.5, angle)
        f.spot(f"CHOIR_{i + 1}", qx, floor + 1, qz)
    for k in range(12):  # the pipes' roots through the ceiling
        px, pz = ring_cell(cx, cz, 6, 30 * k)
        f.set(px, floor + 6, pz, POLISHED_BASALT)
    for k in range(6):
        lx, lz = ring_cell(cx, cz, 10.5, 60 * k + 30)
        hanging_lamp(f, lx, lz, floor + 5, floor + 6)
    f.mark("UNDERCROFT_MIN", cx - 13, floor + 1, cz - 13)
    f.mark("UNDERCROFT_MAX", cx + 13, floor + 6, cz + 13)


@functools.lru_cache(maxsize=None)
def build_hymnal():
    """Both Hymnal templates, built together in the arch's frame: (arch, halls, the frame)."""
    arch = Site("site/hymnal_arch", HYMNAL_ARCH_SIZE, HYMNAL_ARCH_BURIED)
    halls = Site("site/hymnal", HYMNAL_HALLS_SIZE, 0)
    f = HymnalFrame(arch, halls)
    masonry = hymn_masonry(HYMNAL_SEED + 13)
    rock = basalt_rock(HYMNAL_SEED + 14)
    hymnal_arch_head(f)
    hymnal_heart(f, masonry)
    hymnal_undercroft(f, masonry)
    hymnal_processional(f)  # last: its doorway and doors cut through the Heart's wall
    shell_below(arch, 2, masonry, 16)
    hymnal_arch_above(arch, masonry, rock)
    clear_above(arch, 17)
    shell_below(halls, 1, masonry, HYMNAL_HALLS_SIZE[1] - 1)
    shell_below(halls, 2, rock, HYMNAL_HALLS_SIZE[1] - 1)
    return arch, halls, f


# ---------------------------------------------------------------------------------------------
# Hub helpers
# ---------------------------------------------------------------------------------------------
SANDSTONE_WALL = "minecraft:sandstone_wall"
SANDSTONE_SLAB = "minecraft:smooth_sandstone_slab"
CHISELED_RED = "minecraft:chiseled_red_sandstone"
RED_TERRACOTTA = "minecraft:red_terracotta"
WHITE_TERRACOTTA = "minecraft:white_terracotta"
ORANGE_TERRACOTTA = "minecraft:orange_terracotta"
BROWN_TERRACOTTA = "minecraft:brown_terracotta"
RED_CARPET = "minecraft:red_carpet"
DEAD_BUSH = "minecraft:dead_bush"
SPRUCE_PLATE = "minecraft:spruce_pressure_plate"
SPRUCE_STAIRS = "minecraft:spruce_stairs"
DARK_OAK_STAIRS = "minecraft:dark_oak_stairs"
DARK_OAK_SLAB = "minecraft:dark_oak_slab"
DARK_OAK_FENCE = "minecraft:dark_oak_fence"
MANGROVE_FENCE = "minecraft:mangrove_fence"
BROWN_WOOL = "minecraft:brown_wool"
HAY = "minecraft:hay_block"


def fill_ground(t, top_fn, under_fn, top=2):
    """Layers 0..top of ground in every cell not set yet: top_fn(x, z) on the top layer, under_fn(x, y, z) below."""
    sx, _, sz = t.size
    for x in range(sx):
        for z in range(sz):
            for y in range(top + 1):
                t.keep(x, y, z, top_fn(x, z) if y == top else under_fn(x, y, z))


def building(t, x0, z0, x1, z1, y0, y1, wall, floor=None, roof=None):
    """A box room: `floor` on layer y0 - 1, walls on its rim from y0 to y1, air inside, `roof` on layer y1 + 1
    (each a name, a function (x, y, z) or None to leave the layer alone)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            rim = x in (x0, x1) or z in (z0, z1)
            if floor is not None:
                t.put(x, y0 - 1, z, floor)
            for y in range(y0, y1 + 1):
                t.put(x, y, z, wall if rim else AIR)
            if roof is not None:
                t.put(x, y1 + 1, z, roof)


def table(t, x, y, z, legs=SPRUCE_FENCE, top=SPRUCE_PLATE):
    """A small table: a fence post with a pressure plate on it."""
    t.set(x, y, z, legs, fence())
    t.set(x, y + 1, z, top, {"powered": "false"})


def chair(t, x, y, z, facing, block=SPRUCE_STAIRS):
    """A seat whose back is on its `facing` side: put it on a table's `facing` side to sit facing the table."""
    t.set(x, y, z, block, m3.stairs(facing))


def awning(t, x0, z0, x1, z1, y, cloth, post=SPRUCE_FENCE):
    """A cloth canopy on layer y over posts at its four corners (from y - 3)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, y, z, cloth(x, z) if callable(cloth) else cloth)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for dy in range(1, 4):
            t.set(x, y - dy, z, post, fence())


def crates(t, cells, facing="up"):
    for (x, y, z) in cells:
        t.set(x, y, z, BARREL, barrel(facing))


# ---------------------------------------------------------------------------------------------
# Saltgate (Ghessar's hub, 3.2.1): a walled port of sand-scoured prefab and old freighter hulls. North is the
# main gate; the town fills the north half (the cantina and the counting-house behind it to the west, the market
# street, Vessba's audience hall to the east), the pads lie behind their own gate in the south half between the
# medbay and the hangar. Every person stands on a hub_spawner with a role; the counting-house posts, Dask's spot
# and the medbay are plain feet anchors.
# ---------------------------------------------------------------------------------------------
SALTGATE_SIZE = (63, 24, 63)
SALTGATE_SEED = 905401


def prefab(seed):
    """Sand-scoured prefab panels: smooth and cut sandstone with old plating patched in."""
    def fn(x, y, z):
        return pick(seed, x // 2, y, z // 2, [(6, SMOOTH_SANDSTONE), (2, CUT_SANDSTONE), (1, PLATE), (1, WHITE_TERRACOTTA)])
    return fn


def street(seed):
    def fn(x, y, z):
        return pick(seed, x, y, z, [(5, SANDSTONE), (3, SMOOTH_SANDSTONE), (2, SAND), (1, GRAVEL)])
    return fn


def saltgate_walls(t, seed):
    """The outer wall: two thick, walked at feet 9 behind merlons, lamps on its corners, the north gate and a
    ladder up beside it."""
    stone = prefab(seed)
    for x in range(63):
        for z in range(63):
            ring = min(x, z, 62 - x, 62 - z)
            if ring <= 1:
                for y in range(3, 9):
                    t.set(x, y, z, stone(x, y, z))
                if ring == 0 and (x + z) % 2 == 0:
                    t.set(x, 9, z, CUT_SANDSTONE)
    for x, z in ((1, 1), (61, 1), (1, 61), (61, 61)):
        lamp_post(t, x, 9, z, 1, SANDSTONE_WALL)
    for x in range(29, 34):
        for z in range(0, 2):
            t.air(x, 3, z, x, 6, z)
    for z in range(0, 2):
        for y in range(3, 9):
            t.set(28, y, z, CHISELED_SANDSTONE if y in (3, 8) else CUT_SANDSTONE)
            t.set(34, y, z, CHISELED_SANDSTONE if y in (3, 8) else CUT_SANDSTONE)
    m3.lantern(t, 28, 9, 1)
    m3.lantern(t, 34, 9, 1)
    for y in range(3, 9):
        t.set(36, y, 2, LADDER, ladder("south"))
    for i, (x, z) in enumerate(((20, 1), (42, 1), (1, 20), (1, 45), (61, 20), (61, 45))):
        hub_spawner(t, f"ENFORCER_{i + 1}", x, 9, z, "enforcer")


def saltgate_cantina(t, seed):
    """The cantina (north-west): the bar with the bartender behind it, the fence's booth, Varra's table and the
    contract terminal."""
    building(t, 3, 3, 19, 17, 3, 8, prefab(seed), floor=SMOOTH_SANDSTONE, roof=SMOOTH_SANDSTONE)
    t.air(19, 3, 9, 19, 5, 11)  # the door, onto the market street
    for x in range(5, 18, 4):
        for z in (3, 17):
            t.set(x, 5, z, AIR)
    connect_bars(t, [(x, 5, z) for x in range(5, 18, 4) for z in (3, 17)])
    for z in range(4, 14):
        t.set(10, 3, z, CUT_SANDSTONE)  # the bar counter
    for x in range(4, 10):
        t.set(x, 3, 13, CUT_SANDSTONE)
    crates(t, [(4, y, z) for z in range(5, 12) for y in (3, 4)], "east")
    hub_spawner(t, "CANTINA", 8, 3, 9, "bartender")
    for (x, z) in ((13, 6), (16, 9), (13, 12)):
        table(t, x, 3, z)
        chair(t, x - 1, 3, z, "west")
        chair(t, x + 1, 3, z, "east")
    table(t, 14, 3, 15)
    chair(t, 14, 3, 16, "south")
    hub_spawner(t, "NPC_VARRA", 12, 3, 15, "varra")
    crates(t, [(18, 3, 16), (18, 3, 15), (17, 3, 16), (18, 4, 16)])
    t.set(16, 3, 16, RED_CARPET)
    hub_spawner(t, "VENDOR_FENCE", 17, 3, 14, "vendor:cartel_fence")
    terminal(t, "CONTRACT_TERMINAL", 15, 3, 5, CONTRACT_TERMINAL, "south")
    for (x, z) in ((7, 7), (14, 7), (7, 15), (14, 12)):
        m3.lantern(t, x, 8, z, hanging=True)


def saltgate_counting_house(t, seed):
    """The counting-house (west, behind the cantina): a two-floor strongroom of red stone; the doorman outside its
    door, four guard posts on each floor, Dask's desk upstairs."""
    def red(x, y, z):
        if y in (3, 7, 12):
            return CUT_RED
        return pick(seed, x, y, z, [(5, SMOOTH_RED), (2, RED_TERRACOTTA), (1, CUT_RED)])
    building(t, 3, 20, 17, 30, 3, 11, red, floor=SMOOTH_SANDSTONE, roof=CUT_RED)
    t.box(4, 7, 21, 16, 7, 29, CUT_RED)  # the upper floor
    t.air(17, 3, 24, 17, 5, 26)  # the door
    t.air(4, 7, 22, 5, 7, 25)  # the stairwell
    for i, z in enumerate(range(22, 26)):
        for x in (4, 5):
            t.set(x, 3 + i, z, "minecraft:red_sandstone_stairs", m3.stairs("south"))
    connect_fences(t, [(6, 8, z) for z in range(22, 26)], DARK_OAK_FENCE)
    windows = [(x, y, z) for x in (7, 13) for z in (20, 30) for y in (5, 9)] + [(3, y, z) for z in (23, 27) for y in (5, 9)]
    for cell in windows:
        t.set(*cell, AIR)
    connect_bars(t, windows)
    hub_spawner(t, "COUNTING_HOUSE_DOOR", 19, 3, 25, "doorman")
    for i, (x, y, z) in enumerate(((7, 3, 22), (14, 3, 22), (7, 3, 28), (14, 3, 28),
                                   (9, 8, 22), (15, 8, 22), (9, 8, 28), (15, 8, 28))):
        t.spot(f"COUNTING_HOUSE_{i + 1}", x, y, z)
    crates(t, [(x, y, 29) for x in range(9, 13) for y in (3, 4)] + [(16, 3, 21), (16, 4, 21), (16, 3, 22)])
    crates(t, [(16, 3, 29)])
    for x in range(11, 14):
        t.set(x, 8, 25, CUT_RED)  # Dask's desk
    t.spot("NPC_DASK", 12, 8, 26)
    t.set(12, 8, 27, "minecraft:red_sandstone_stairs", m3.stairs("south"))
    for (x, y, z) in ((10, 6, 25), (10, 11, 22), (10, 11, 28)):
        m3.lantern(t, x, y, z, hanging=True)


def saltgate_audience_hall(t, seed):
    """Vessba's audience hall (north-east): a tall hall of red-banded stone, a carpet from the door to the dais,
    Vessba on her dais (room for her bulk around the spawner) and Ommo Teel beside its steps."""
    def hall(x, y, z):
        if y in (3, 7, 11):
            return CUT_RED
        return pick(seed, x // 2, y, z // 2, [(5, SMOOTH_SANDSTONE), (2, CUT_SANDSTONE), (1, ORANGE_TERRACOTTA)])
    building(t, 40, 3, 59, 20, 3, 11, hall, floor=SMOOTH_SANDSTONE, roof=SMOOTH_SANDSTONE)
    t.air(40, 3, 10, 40, 6, 12)
    for x in range(41, 51):
        t.set(x, 3, 11, RED_CARPET)
    for x in (43, 47):
        for z in (6, 16):
            for y in range(3, 12):
                t.set(x, y, z, CUT_SANDSTONE if y not in (3, 11) else CHISELED_SANDSTONE)
    t.box(52, 3, 8, 58, 3, 16, CUT_RED)  # the dais
    for z in range(8, 17):
        t.set(51, 3, z, "minecraft:red_sandstone_stairs", m3.stairs("east"))
    t.set(58, 4, 12, CHISELED_RED)
    for z in (9, 15):
        lamp_post(t, 57, 4, z, 2, SANDSTONE_WALL)
    hub_spawner(t, "NPC_VESSBA", 54, 4, 12, "vessba")
    hub_spawner(t, "NPC_TEEL", 50, 3, 14, "teel")
    for z in range(5, 20, 2):
        t.set(59, 6, z, RED_TERRACOTTA)
    for (x, z) in ((45, 11), (49, 7), (49, 15)):
        m3.lantern(t, x, 11, z, hanging=True)
    for x in (44, 52):
        for z in (3, 20):
            t.set(x, 7, z, AIR)
    connect_bars(t, [(x, 7, z) for x in (44, 52) for z in (3, 20)])


def saltgate_hangar(t, seed):
    """The hangar (south-east): an old freighter's hull halved into a long shed, open to the north, with the
    shipwright's bench, the spacer's stall and an engine on its cradle."""
    hull = m3.plating(seed)
    for z in range(30, 60):
        rib = (z - 30) % 5 == 0
        for x in range(47, 61):
            for y in range(3, 12):
                d = math.hypot(x - 53.5, (y - 2.5) * 1.12)
                if d < 5.6:
                    t.set(x, y, z, hull(x, y, z) if z == 59 else AIR)
                elif d < 6.75:
                    t.set(x, y, z, CONDUIT if rib else hull(x, y, z))
            t.set(x, 2, z, pick(seed, x, 2, z, [(4, SMOOTH_SANDSTONE), (2, GRAVEL), (1, PLATE)]))
    for x in range(47, 61):
        for y in range(3, 12):
            if math.hypot(x - 53.5, (y - 2.5) * 1.12) < 5.6 and not (50 <= x <= 57 and y <= 7):
                t.set(x, y, 30, hull(x, y, 30))
    t.box(52, 3, 52, 55, 5, 55, PLATE)
    t.box(53, 4, 51, 54, 4, 51, CONDUIT)
    t.set(53, 6, 53, IMP_PANEL)
    hub_spawner(t, "VENDOR_SHIPWRIGHT", 51, 3, 38, "vendor:saltgate_shipwright")
    t.set(49, 3, 37, "minecraft:smithing_table")
    t.set(49, 3, 38, "minecraft:anvil", {"facing": "north"})
    t.set(49, 3, 39, "minecraft:crafting_table")
    hub_spawner(t, "VENDOR_SPACER", 56, 3, 44, "vendor:spacer")
    crates(t, [(58, 3, z) for z in range(42, 47)] + [(58, 4, 43), (58, 4, 45), (57, 3, 46)], "west")
    for z in (34, 41, 48, 55):
        m3.lantern(t, 53, 7, z, hanging=True)


def saltgate_medbay(t, seed):
    building(t, 3, 35, 13, 46, 3, 6, prefab(seed), floor=SMOOTH_SANDSTONE, roof=SMOOTH_SANDSTONE)
    t.air(13, 3, 39, 13, 5, 41)
    for z in (37, 44):
        t.set(4, 3, z, "minecraft:white_bed", {"facing": "west", "occupied": "false", "part": "head"})
        t.set(5, 3, z, "minecraft:white_bed", {"facing": "west", "occupied": "false", "part": "foot"})
    t.set(4, 3, 40, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
    t.set(4, 3, 41, "minecraft:cauldron")
    m3.lantern(t, 8, 6, 40, hanging=True)
    t.spot("MEDBAY", 8, 3, 40)
    for z in (38, 43):
        t.set(3, 5, z, AIR)
    connect_bars(t, [(3, 5, 38), (3, 5, 43)])


def saltgate_pads(t, seed):
    """The pads (south): four bays split by walkways, behind a low wall whose gate the harbourmaster keeps."""
    for x in range(17, 46):
        for z in range(33, 60):
            if x == 31 or z == 46:
                t.set(x, 2, z, CUT_SANDSTONE)
            else:
                t.set(x, 2, z, pick(seed, x, 2, z, [(8, SMOOTH_SANDSTONE), (2, SANDSTONE), (1, CUT_SANDSTONE)]))
    for x in range(16, 47):
        for z in range(32, 61):
            rim = x in (16, 46) or z in (32, 60)
            if not rim or (z == 32 and 29 <= x <= 33):
                continue
            t.set(x, 2, z, CUT_SANDSTONE)
            t.set(x, 3, z, CUT_SANDSTONE)
            if (x + z) % 6 == 0:
                t.set(x, 4, z, SANDSTONE_WALL, wall_post())
                m3.lantern(t, x, 5, z)
    for x in range(29, 34):
        t.set(x, 2, 32, CUT_SANDSTONE)
    for x in (28, 34):
        lamp_post(t, x, 4, 32, 1, SANDSTONE_WALL)
    t.spot("HEART", 31, 3, 46)
    for i, (x, z) in enumerate(((24, 39), (38, 39), (24, 53), (38, 53))):
        t.spot(f"PAD_BAY_{i + 1}", x, 3, z)
    terminal(t, "PAD_TERMINAL", 44, 3, 47, PAD_TERMINAL, "west")
    hub_spawner(t, "HOST", 31, 3, 34, "host")


def saltgate_town(t, seed):
    """Streets, the market stalls, the caravan yard and the storage yard; lamp posts along the main street."""
    path = street(seed)
    for x in range(28, 35):
        for z in range(2, 32):
            t.set(x, 2, z, path(x, 2, z))
    for x in range(20, 28):
        for z in range(2, 32):
            t.set(x, 2, z, path(x, 2, z))
    for x in range(35, 61):
        for z in range(21, 30):
            t.set(x, 2, z, path(x, 2, z))
    stripes = lambda x, z: BROWN_WOOL if (x + z) % 2 else "minecraft:white_wool"  # noqa: E731
    for z0 in (4, 12):
        awning(t, 21, z0, 25, z0 + 4, 6, stripes)
        crates(t, [(22, 3, z0 + 1), (24, 3, z0 + 1), (22, 3, z0 + 3)])
        t.set(23, 3, z0 + 3, "minecraft:cauldron")
    awning(t, 21, 27, 25, 30, 6, lambda x, z: ORANGE_TERRACOTTA if x % 2 else "minecraft:orange_wool")
    crates(t, [(25, 3, 28), (25, 3, 29), (25, 4, 29)])
    for z in range(5, 31, 6):
        lamp_post(t, 27, 3, z, 3, SANDSTONE_WALL)
        lamp_post(t, 35, 3, z, 3, SANDSTONE_WALL)
    for x in range(41, 46):
        t.set(x, 3, 23, SPRUCE_FENCE, fence(east=x < 45, west=x > 41))
    t.set(41, 3, 26, HAY, {"axis": "y"})
    t.set(42, 3, 26, HAY, {"axis": "x"})
    crates(t, [(38, 3, 24), (38, 3, 25), (38, 4, 24), (37, 3, 27)])
    for x in range(3, 15):
        for z in range(49, 60):
            if noise(seed + 1, x // 2, 0, z // 2) > 0.62:
                crates(t, [(x, 3, z)])
                if noise(seed + 2, x, 0, z) > 0.6:
                    crates(t, [(x, 4, z)])
    for x in range(4, 13):  # a hull section left to the sand
        for y in range(3, 8):
            if 3.2 <= math.hypot(x - 8, (y - 2.5) * 1.3) <= 4.3:
                t.set(x, y, 57, m3.plating(seed + 3)(x, y, 57))
                t.set(x, y, 58, m3.plating(seed + 3)(x, y, 58))


def build_saltgate():
    t = Site("hub/saltgate", SALTGATE_SIZE, 3)
    saltgate_walls(t, SALTGATE_SEED)
    saltgate_cantina(t, SALTGATE_SEED + 10)
    saltgate_counting_house(t, SALTGATE_SEED + 20)
    saltgate_audience_hall(t, SALTGATE_SEED + 30)
    saltgate_hangar(t, SALTGATE_SEED + 40)
    saltgate_medbay(t, SALTGATE_SEED + 50)
    saltgate_pads(t, SALTGATE_SEED + 60)
    saltgate_town(t, SALTGATE_SEED + 70)
    fill_ground(t, lambda x, z: SAND if noise(SALTGATE_SEED + 80, x, 2, z) > 0.18 else SANDSTONE,
                lambda x, y, z: SANDSTONE)
    for x in range(63):
        for z in range(63):
            if (x, 3, z) not in t.cells and t.get(x, 2, z) == SAND and noise(SALTGATE_SEED + 81, x, 3, z) > 0.97:
                t.set(x, 3, z, DEAD_BUSH)
    clear_above(t, 3)
    return t


# ---------------------------------------------------------------------------------------------
# The holdfast (Tyrvann's hub, 3.3.1): Clan Hask's camp on a deck on stilts above the mire edge, ringed by a wall
# of clan iron (plating on dark oak frames) with the clan gate in the north wall, reached by a stair from the mud.
# Dorrun's longhouse and the forge flank the fire yard; the medbay, the spacer's stall, the barracks and the
# stores ring the pads. Lightning rods on every roof draw the storm away from the deck.
# ---------------------------------------------------------------------------------------------
HOLDFAST_SIZE = (57, 26, 57)
HOLDFAST_SEED = 905501
HOLDFAST_DECK = 7  # the deck's layer; the camp's feet layer is 8
HOLDFAST_GATE_CELLS = [(x, y, 10) for x in (27, 28, 29) for y in (8, 9, 10)]


def deck_planks(seed):
    def fn(x, y, z):
        return pick(seed, x, y, z // 2, [(6, DARK_OAK_PLANKS), (3, SPRUCE_PLANKS), (1, MANGROVE_PLANKS)])
    return fn


def clan_iron(seed):
    def fn(x, y, z):
        return pick(seed, x, y, z, [(5, PLATE), (2, SCORCHED), (1, IMP_PANEL)])
    return fn


def hut(t, x0, z0, x1, z1, seed, door, rod_at=None):
    """A prefab shelter on the deck: dark oak frame posts, plating panels, a plank roof with a raised middle and,
    on top, a lightning rod. `door` is (x0, z0, x1, z1) of the doorway's cells on the rim."""
    y0, y1 = HOLDFAST_DECK + 1, HOLDFAST_DECK + 4
    iron = clan_iron(seed)

    def wall(x, y, z):
        if x in (x0, x1) and z in (z0, z1) or (x in (x0, x1) and (z - z0) % 4 == 0) or (z in (z0, z1) and (x - x0) % 4 == 0):
            return DARK_OAK_LOG
        return iron(x, y, z)
    building(t, x0, z0, x1, z1, y0, y1, wall, roof=deck_planks(seed + 1))
    dx0, dz0, dx1, dz1 = door
    t.air(dx0, y0, dz0, dx1, y0 + 2, dz1)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if min(x - x0, x1 - x, z - z0, z1 - z) >= 2:
                t.set(x, y1 + 2, z, BROWN_WOOL if (x + z) % 3 else DARK_OAK_PLANKS)
    if rod_at is not None:
        rx, rz = rod_at
        t.set(rx, y1 + 3, rz, DARK_OAK_PLANKS)
        t.set(rx, y1 + 4, rz, LIGHTNING_ROD, rod("up"))


def holdfast_deck(t, seed):
    """The deck on its stilts, the wall of clan iron round it with the gate, and the stair up from the mud."""
    planks = deck_planks(seed)
    iron = clan_iron(seed + 1)
    deck = HOLDFAST_DECK
    for x in range(4, 53):
        for z in range(10, 53):
            t.set(x, deck, z, planks(x, deck, z))
            if (x - 4) % 6 == 0 and (z - 10) % 6 == 0 or x in (4, 52) and z in (10, 52):
                for y in range(3, deck):
                    t.set(x, y, z, MANGROVE_LOG, log("y"))
            rim = x in (4, 52) or z in (10, 52)
            if rim:
                post = (x - 4) % 4 == 0 if z in (10, 52) else (z - 10) % 4 == 0
                for y in range(deck + 1, deck + 5):
                    t.set(x, y, z, DARK_OAK_LOG if post else iron(x, y, z))
                if post:
                    t.set(x, deck + 5, z, DARK_OAK_LOG)
    for x, y, z in HOLDFAST_GATE_CELLS:
        t.set(x, y, z, HOLDFAST_GATE, {"facing": "north", "open": "false"})
        t.mark("HOLDFAST_GATE", x, y, z)
    for x in (26, 30):
        for y in range(deck + 1, deck + 6):
            t.set(x, y, 10, DARK_OAK_LOG)
        m3.lantern(t, x, deck + 6, 10)
    for x in range(27, 30):
        t.set(x, deck + 4, 10, DARK_OAK_LOG, log("x"))
    # The stair up from the mud: treads at z 5..8, the landing before the gate at z 9 (feet 8).
    for x in range(26, 31):
        t.set(x, deck, 9, planks(x, deck, 9))
    for i, z in enumerate(range(5, 9)):
        for x in range(27, 30):
            t.set(x, 3 + i, z, DARK_OAK_STAIRS, m3.stairs("south"))
            for y in range(3, 3 + i):
                t.set(x, y, z, DARK_OAK_PLANKS)
        for x in (26, 30):
            for y in range(3, 3 + i + 1):
                t.set(x, y, z, DARK_OAK_PLANKS)
            t.set(x, 4 + i, z, DARK_OAK_FENCE, fence(north=z > 5, south=True))
    for x in (26, 30):
        t.set(x, deck + 1, 9, DARK_OAK_FENCE, fence(north=True))
        for y in range(3, deck):
            t.set(x, y, 9, MANGROVE_LOG, log("y"))


def holdfast_camp(t, seed):
    deck = HOLDFAST_DECK
    feet = deck + 1
    # Dorrun's longhouse (north-west) and the forge (north-east), doors onto the fire yard.
    hut(t, 6, 12, 20, 24, seed, (12, 24, 14, 24), rod_at=(13, 18))
    for x in range(10, 17):
        t.set(x, feet, 13, DARK_OAK_PLANKS)
    t.set(13, feet + 1, 13, DARK_OAK_STAIRS, m3.stairs("north"))
    hub_spawner(t, "NPC_DORRUN", 13, feet, 15, "dorrun")
    for z in (18, 20):
        for x in range(9, 18, 2):
            table(t, x, feet, z, DARK_OAK_FENCE, "minecraft:dark_oak_pressure_plate")
    for (x, z) in ((9, 15), (17, 15), (13, 21)):
        m3.lantern(t, x, feet + 3, z, hanging=True)
    hut(t, 36, 12, 50, 24, seed + 2, (42, 24, 44, 24), rod_at=(43, 18))
    hub_spawner(t, "VENDOR_ARMOURER", 43, feet, 18, "vendor:hask_armourer")
    t.set(40, feet, 14, "minecraft:blast_furnace", {"facing": "south", "lit": "false"})
    t.set(41, feet, 14, "minecraft:anvil", {"facing": "east"})
    t.set(45, feet, 14, "minecraft:smithing_table")
    t.set(46, feet, 14, "minecraft:grindstone", {"face": "floor", "facing": "south"})
    crates(t, [(49, feet, z) for z in range(15, 22)], "west")
    for (x, z) in ((39, 19), (47, 19)):
        m3.lantern(t, x, feet + 3, z, hanging=True)
    # The fire yard between them: the fire and its log benches, Varra by it, the contract terminal under hides.
    t.set(28, feet, 19, "minecraft:campfire", campfire())
    for x in (25, 31):
        t.set(x, feet, 19, MANGROVE_LOG, log("z"))
    t.set(28, feet, 16, MANGROVE_LOG, log("x"))
    t.set(28, feet, 22, MANGROVE_LOG, log("x"))
    hub_spawner(t, "NPC_VARRA", 24, feet, 21, "varra")
    awning(t, 32, 13, 35, 15, feet + 3, BROWN_WOOL, DARK_OAK_FENCE)
    terminal(t, "CONTRACT_TERMINAL", 34, feet, 14, CONTRACT_TERMINAL, "south")
    # The medbay (west), the spacer's stall (east), the barracks (south-west), the stores (south-east).
    hut(t, 5, 28, 13, 38, seed + 4, (13, 32, 13, 34), rod_at=(9, 33))
    for z in (30, 36):
        t.set(6, feet, z, "minecraft:red_bed", {"facing": "west", "occupied": "false", "part": "head"})
        t.set(7, feet, z, "minecraft:red_bed", {"facing": "west", "occupied": "false", "part": "foot"})
    t.set(6, feet, 33, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
    t.spot("MEDBAY", 9, feet, 33)
    m3.lantern(t, 9, feet + 3, 33, hanging=True)
    awning(t, 45, 29, 50, 35, feet + 3, lambda x, z: BROWN_WOOL if (x + z) % 2 else "minecraft:green_wool", DARK_OAK_FENCE)
    hub_spawner(t, "VENDOR_SPACER", 48, feet, 32, "vendor:spacer")
    crates(t, [(50, feet, 31), (50, feet, 32), (50, feet + 1, 31), (46, feet, 34)])
    hut(t, 5, 42, 13, 51, seed + 6, (13, 45, 13, 47), rod_at=(9, 46))
    for z in (43, 45, 47, 49):
        t.set(6, feet, z, "minecraft:brown_bed", {"facing": "west", "occupied": "false", "part": "head"})
        t.set(7, feet, z, "minecraft:brown_bed", {"facing": "west", "occupied": "false", "part": "foot"})
    m3.lantern(t, 10, feet + 3, 46, hanging=True)
    hut(t, 44, 42, 51, 51, seed + 8, (44, 45, 44, 47), rod_at=(48, 46))
    crates(t, [(x, y, z) for x in (49, 50) for z in range(43, 51) for y in (feet, feet + 1) if (x + z + y) % 3])
    m3.lantern(t, 47, feet + 3, 46, hanging=True)
    # The pads.
    for x in range(15, 42):
        for z in range(28, 52):
            t.set(x, deck, z, PACKED_MUD if x == 28 or z == 40 else MUD_BRICKS)
    t.spot("HEART", 28, feet, 40)
    for i, (x, z) in enumerate(((21, 34), (35, 34), (21, 46), (35, 46))):
        t.spot(f"PAD_BAY_{i + 1}", x, feet, z)
    terminal(t, "PAD_TERMINAL", 41, feet, 40, PAD_TERMINAL, "west")
    for x, z in ((15, 28), (41, 28), (15, 51), (41, 51)):
        lamp_post(t, x, feet, z, 2, DARK_OAK_FENCE)
    # Warriors' posts: inside the gate, and in the gaps of the ring.
    for i, (x, z) in enumerate(((25, 12), (31, 12), (5, 26), (51, 26), (5, 40), (51, 40))):
        hub_spawner(t, f"WARRIOR_{i + 1}", x, feet, z, "warrior")
    for x, z in ((22, 12), (34, 26), (22, 26)):
        lamp_post(t, x, feet, z, 2, DARK_OAK_FENCE)


def build_holdfast():
    t = Site("hub/holdfast", HOLDFAST_SIZE, 3)
    holdfast_deck(t, HOLDFAST_SEED)
    holdfast_camp(t, HOLDFAST_SEED + 10)

    def top(x, z):
        r = noise(HOLDFAST_SEED + 20, x // 2, 2, z // 2)
        if r > 0.9 and 6 <= x <= 50 and 12 <= z <= 50:
            return ("minecraft:water", {"level": "0"})  # black pools under the deck
        return PEAT if r < 0.2 else MUD
    fill_ground(t, top, lambda x, y, z: PACKED_MUD if y == 1 else "minecraft:clay")
    clear_above(t, 3)
    return t


# ---------------------------------------------------------------------------------------------
# Worldgen pieces (3.2, 3.3): one piece each, adapted to the terrain (AdaptedPoolElement).
# ---------------------------------------------------------------------------------------------
FREIGHTER_SIZE = (33, 14, 17)
FREIGHTER_BURIED = 4


def freighter_axis(x):
    """The hull's centre height at x: the nose (east) has ploughed deeper into the dune than the engines."""
    return 4.6 - (x - 3) * 0.07


def build_crashed_freighter():
    """An old freighter half-buried in the dune sea (3.2): a long plated hull along x, engines west, the cockpit east,
    a torn breach in the north side over a sand spill into the hold. The hold keeps its chest and the cargo crate
    (CRATE, the M3 recover prop); the contract crews stand around the wreck (CREW_1 to 4)."""
    t = Site("planet/crashed_freighter", FREIGHTER_SIZE, FREIGHTER_BURIED)
    zc = 8
    plate = lambda x, y, z: pick(531, x, y, z, [(6, PLATE), (3, SCORCHED), (1, "minecraft:weathered_cut_copper")])  # noqa: E731
    hull = set()
    hold = set()
    for x in range(3, 30):
        yc = freighter_axis(x)
        taper = 1.0 if x <= 24 else max(0.35, 1.0 - (x - 24) * 0.13)
        ry, rz = 5.0 * taper, 6.0 * taper
        for y in range(0, 12):
            for z in range(17):
                e = ((y - yc) / ry) ** 2 + ((z - zc) / rz) ** 2
                inner = ((y - yc) / (ry - 1.2)) ** 2 + ((z - zc) / (rz - 1.2)) ** 2 if ry > 1.6 else 9
                if e <= 1.0 and inner > 1.0:
                    hull.add((x, y, z))
                elif e <= 1.0 and 6 <= x <= 25:
                    hold.add((x, y, z))
    for (x, y, z) in hull:
        # Corrosion holes above the sand line, never on the hold's floor.
        if y >= FREIGHTER_BURIED + 1 and noise(533, x // 2, y, z // 2) > 0.86:
            continue
        t.set(x, y, z, plate(x, y, z))
    for (x, y, z) in hold:
        t.set(x, y, z, AIR)
    # The hold's deck: plates across the lowest open rows (feet one block below the dune's surface).
    for x in range(6, 26):
        for z in range(4, 13):
            if (x, 3, z) in hold:
                t.set(x, 2, z, PLATE if (x + z) % 5 else CONDUIT)
                for y in range(0, 2):
                    t.keep(x, y, z, SANDSTONE)
    # A torn bulkhead halfway along, open in the middle.
    for z in range(4, 13):
        for y in range(3, 9):
            if (16, y, z) in hold and not (6 <= z <= 10 and y <= 5):
                t.set(16, y, z, SCORCHED if noise(535, 16, y, z) > 0.5 else PLATE)
    # The breach: the north side torn open from x 12 to 16, sand spilled in down to the deck.
    for x in range(12, 17):
        for y in range(3, 9):
            for z in range(1, 6):
                if (x, y, z) in hull or (x, y, z) in hold:
                    t.set(x, y, z, AIR)
        t.set(x, 3, 3, SAND)
        t.set(x, 3, 4, SAND)
        t.set(x, 2, 3, SANDSTONE)
        t.set(x, 2, 4, SANDSTONE)
        t.set(x, 2, 5, PLATE)
    # Engines (west): two nozzles with a dead conduit glow inside.
    for zn in (5, 11):
        for x in range(0, 4):
            for y in range(3, 9):
                for z in range(zn - 2, zn + 3):
                    d = math.hypot(y - 5.5, z - zn)
                    if d <= 2.4:
                        t.set(x, y, z, AIR if d < 1.3 and x < 3 else PLATE if (x + y) % 3 else SCORCHED)
        t.set(2, 5, zn, CONDUIT)
    # Cockpit (east): a smashed canopy of glass panes over the nose.
    for x in range(26, 31):
        for z in range(6, 11):
            y = int(round(freighter_axis(x) + 2.6 - (x - 26) * 0.5))
            if 0 <= y < 14:
                t.set(x, y, z, "minecraft:glass_pane" if noise(537, x, y, z) > 0.45 else AIR)
    for x, y, z in ((27, 3, 7), (27, 3, 9)):
        t.set(x, y, z, "minecraft:spruce_trapdoor", trapdoor("north"))
    # A dorsal fin and an antenna mast, bent.
    for y in range(9, 12):
        t.set(9, y, 8, PLATE)
        t.set(10, y, 8, SCORCHED if y < 11 else PLATE)
    for y in range(9, 13):
        t.set(21, y, 8 if y < 11 else 9, CHAIN, {"axis": "y", "waterlogged": "false"})
    # The hold: cargo, the chest and the crate; the heart by the bulkhead.
    t.chest("CHEST_1", 21, 3, 6, "south", "crashed_freighter")
    t.set(11, 3, 10, BARREL, barrel("up"))
    t.mark("CRATE", 11, 3, 10)
    for x, y, z in ((10, 3, 10), (10, 3, 9), (10, 4, 10), (22, 3, 11), (23, 3, 11), (23, 4, 11), (19, 3, 11)):
        t.set(x, y, z, BARREL, barrel("up"))
    t.set(8, 3, 6, "minecraft:iron_bars", m3.bars(north=True, south=True))
    t.spot("HEART", 14, 3, 8)
    # Ground: dune sand over sandstone, drifts banked against the hull's south side and the nose.
    m3.ground_layers(t, FREIGHTER_BURIED - 1, 539, top_block=SAND, under=SAND, deep=SANDSTONE)
    crews = {(6, 1), (26, 1), (8, 15), (24, 15), (14, 0)}
    for x in range(33):
        for z in range(17):
            if (x, z) in crews:
                continue
            near = any((x + dx, y, z + dz) in hull for dx in (-1, 0, 1) for dz in (-2, -1, 0, 1, 2) for y in (4, 5))
            if near and (z > 9 or x > 24):
                drift = 1 + int(noise(541, x // 2, 0, z // 2) * 2.2)
                for y in range(FREIGHTER_BURIED, FREIGHTER_BURIED + drift):
                    t.keep(x, y, z, SAND)
    for i, (x, z) in enumerate(((6, 1), (26, 1), (8, 15), (24, 15))):
        t.spot(f"CREW_{i + 1}", x, FREIGHTER_BURIED, z)
    t.spot("SURFACE_GUARD", 14, FREIGHTER_BURIED, 0)
    clear_above(t, FREIGHTER_BURIED)
    return t


KAJIDIC_SIZE = (21, 14, 21)


def build_kajidic_post():
    """A fortified kajidic waystation in the canyons (3.2): a walled yard of red sandstone with corner turrets and a
    south gate, a two-storey tower with a roof terrace, a striped awning over the trade stall. Cartel enforcers hold
    it (SQUAD, W8's cartel_enforcers); the escort contract ends at its heart."""
    t = Site("planet/kajidic_post", KAJIDIC_SIZE, 1)
    stone = lambda x, y, z: pick(551, x, y, z, [(5, CUT_RED), (3, SMOOTH_RED), (2, RED_SANDSTONE), (1, "minecraft:orange_terracotta")])  # noqa: E731
    for x in range(21):
        for z in range(21):
            inside = 2 <= x <= 18 and 2 <= z <= 18
            t.set(x, 0, z, pick(553, x, 0, z, [(5, SMOOTH_RED), (3, RED_SAND), (2, CUT_RED)]) if inside else
                  pick(555, x, 0, z, [(6, RED_SAND), (2, COARSE), (1, "minecraft:terracotta")]))
    # The wall with its crenellation; the gate south (x 9 to 11).
    crenels = []
    for x in range(2, 19):
        for z in range(2, 19):
            if x in (2, 18) or z in (2, 18):
                for y in range(1, 5):
                    t.set(x, y, z, stone(x, y, z))
                if (x + z) % 2 == 0:
                    crenels.append((x, 5, z))
    for x in range(9, 12):
        for y in range(1, 4):
            t.set(x, y, 18, AIR)
    for x in range(8, 13):
        t.set(x, 4, 18, CUT_RED)
    # Corner turrets.
    for cx, cz in ((2, 2), (18, 2), (2, 18), (18, 18)):
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                for y in range(0, 7):
                    t.set(x, y, z, stone(x, y, z) if y > 0 else CUT_RED)
                if (x + z) % 2 == 0:
                    crenels.append((x, 7, z))
        crenels = [c for c in crenels if not (cx - 1 <= c[0] <= cx + 1 and cz - 1 <= c[2] <= cz + 1 and c[1] == 5)]
    connect_walls(t, crenels, "minecraft:red_sandstone_wall")
    # The tower (x 8 to 12, z 7 to 11): two storeys, a ladder in the north-west corner, a roof terrace.
    for x in range(8, 13):
        for z in range(7, 12):
            wall = x in (8, 12) or z in (7, 11)
            for y in range(1, 10):
                t.set(x, y, z, stone(x, y, z) if wall else AIR)
            t.set(x, 5, z, SMOOTH_RED if wall else "minecraft:spruce_planks")
            t.set(x, 10, z, SMOOTH_RED)
    terrace = [(x, 11, z) for x in range(8, 13) for z in range(7, 12) if (x in (8, 12) or z in (7, 11)) and (x + z) % 2 == 0]
    connect_walls(t, terrace, "minecraft:red_sandstone_wall")
    for y in range(1, 11):
        t.set(9, y, 8, LADDER, ladder("south"))
    t.set(10, 1, 11, AIR)
    t.set(10, 2, 11, AIR)
    for x, z in ((8, 9), (12, 9), (10, 7)):
        t.set(x, 3, z, "minecraft:iron_bars", m3.bars(north=x != 10, south=x != 10, east=x == 10, west=x == 10))
        t.set(x, 7, z, "minecraft:iron_bars", m3.bars(north=x != 10, south=x != 10, east=x == 10, west=x == 10))
    t.chest("CHEST_1", 11, 1, 8, "west", "kajidic_post")
    t.set(11, 1, 10, BARREL, barrel("up"))
    t.set(10, 4, 9, LANTERN_BLOCK, LANTERN_HUNG)
    t.set(11, 6, 10, "minecraft:crafting_table")
    # The trade stall under a striped awning (x 13 to 16, z 13 to 16).
    for x in range(13, 17):
        for z in range(13, 17):
            t.set(x, 4, z, "minecraft:orange_wool" if (x + z) % 2 else "minecraft:white_wool")
    for x, z in ((13, 13), (16, 13), (13, 16), (16, 16)):
        for y in range(1, 4):
            t.set(x, y, z, SPRUCE_FENCE, fence())
    for x, y, z in ((14, 1, 14), (15, 1, 14), (14, 2, 14), (15, 1, 15)):
        t.set(x, y, z, BARREL, barrel("up"))
    t.set(5, 1, 5, "minecraft:water_cauldron", {"level": "3"})
    t.set(4, 1, 5, "minecraft:hay_block", {"axis": "y"})
    for x in (8, 12):
        m3.lantern(t, x, 5, 19)
        t.set(x, 4, 19, SPRUCE_FENCE, fence())
        for y in range(1, 4):
            t.set(x, y, 19, SPRUCE_FENCE, fence())
    t.spawner("SQUAD", 5, 1, 13, CARTEL_ENFORCERS, count=3)
    t.spot("HEART", 10, 1, 14)
    t.spot("SURFACE_GUARD", 10, 1, 20)
    clear_above(t, 1)
    return t


SHRINE_SIZE = (17, 10, 17)


def build_hymn_shrine():
    """A small ring of carved stones with a Hymnsworn camp (3.3): eight carved stones around a basalt cairn crowned
    with stormglass, two hide shelters and a fire. The ground is left to the stormwood or the mire."""
    t = Site("planet/hymn_shrine", SHRINE_SIZE, 1)
    c = 8
    carved = hymn_masonry(561)
    for x in range(17):
        for z in range(17):
            d = math.hypot(x - c, z - c)
            if d <= 2.6 or 5.5 <= d <= 6.6 and noise(563, x, 0, z) > 0.35:
                t.set(x, 0, z, pick(565, x, 0, z, [(4, POLISHED_BLACK), (3, BLACK_BRICKS_CRACKED), (3, BLACKSTONE)]))
    for k in range(8):
        x, z = ring_cell(c, c, 6, 45 * k + 22.5)
        h = 2 + (k * 3) % 3
        t.set(x, 0, z, BLACKSTONE)
        for y in range(1, 1 + h):
            t.set(x, y, z, CHISELED_BLACK if y == 1 + h - 1 and k % 2 == 0 else carved(x, y, z))
    # The cairn: a stepped basalt mound with a crown of stormglass.
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            t.set(x, 1, z, BASALT, {"axis": "y"})
    t.set(c, 2, c, POLISHED_BASALT, {"axis": "y"})
    t.set(c, 3, c, STORMGLASS, cluster("up"))
    for dx, dz in ((-1, -1), (1, 1)):
        t.set(c + dx, 2, c + dz, BRICK_SLAB, slab())
    t.chest("CHEST_1", c, 1, c - 2, "north", "hymn_shrine")
    # The camp: two hide shelters, a fire with bedrolls.
    for x0, z0 in ((1, 11), (12, 1)):
        for x in range(x0, x0 + 4):
            for z in range(z0, z0 + 4):
                edge = x in (x0, x0 + 3) or z in (z0, z0 + 3)
                t.set(x, 0, z, PACKED_MUD)
                if edge:
                    t.set(x, 1, z, DARK_OAK_LOG if (x, z) in ((x0, z0), (x0 + 3, z0), (x0, z0 + 3), (x0 + 3, z0 + 3)) else "minecraft:black_wool")
                    t.set(x, 2, z, "minecraft:black_wool" if (x + z) % 2 else "minecraft:gray_wool")
                t.set(x, 3, z, "minecraft:gray_wool" if edge else "minecraft:black_wool")
        door_x, door_z = (x0 + 3, z0 + 1) if x0 < 8 else (x0, z0 + 2)
        t.set(door_x, 1, door_z, AIR)
        t.set(door_x, 2, door_z, AIR)
        t.set(x0 + 1, 1, z0 + 1, "minecraft:gray_carpet")
        t.set(x0 + 2, 1, z0 + 2, "minecraft:black_carpet")
    t.set(12, 1, 12, "minecraft:campfire", campfire())
    t.set(12, 0, 12, BLACKSTONE)
    t.set(10, 1, 12, DARK_OAK_LOG, log("z"))
    t.spawner("SQUAD", 13, 1, 9, HYMNSWORN, count=3)
    t.set(13, 0, 9, BLACKSTONE)
    t.set(c, 0, 12, POLISHED_BLACK)
    t.set(c, 0, 15, BLACKSTONE)
    t.spot("HEART", c, 1, 12)
    t.spot("SURFACE_GUARD", c, 1, 15)
    clear_above(t, 1)
    t.natural_below = True
    return t


GRAVE_SIZE = (13, 9, 13)
GRAVE_BURIED = 3


def build_mire_grave():
    """A fallen warrior's cairn sunk in the mud (3.3): a ring of mossy stones around a shallow pit with the chest, a
    blade driven into the cairn's head; puddles and peat around it."""
    t = Site("planet/mire_grave", GRAVE_SIZE, GRAVE_BURIED)
    c = 6
    for x in range(13):
        for z in range(13):
            d = math.hypot(x - c, z - c)
            if d > 5.8:
                continue
            for y in range(0, GRAVE_BURIED):
                t.set(x, y, z, pick(571, x, y, z, [(5, MUD), (2, PEAT), (1, "minecraft:clay")]) if y < GRAVE_BURIED - 1 else
                      pick(573, x, y, z, [(6, MUD), (2, PEAT), (1, "minecraft:moss_block")]))
    # The pit (one below the mud) with the chest; the cairn ring around it.
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            t.set(x, GRAVE_BURIED - 1, z, AIR)
            t.set(x, GRAVE_BURIED - 2, z, PACKED_MUD)
    t.chest("CHEST_1", c, GRAVE_BURIED - 1, c, "north", "mire_grave")
    stones = lambda x, y, z: pick(575, x, y, z, [(5, "minecraft:mossy_cobblestone"), (3, "minecraft:cobblestone"), (2, MUD_BRICKS)])  # noqa: E731
    for x in range(13):
        for z in range(13):
            d = math.hypot(x - c, z - c)
            if 1.9 <= d <= 2.9:
                t.set(x, GRAVE_BURIED - 1, z, stones(x, GRAVE_BURIED - 1, z))
                if noise(577, x, 0, z) > 0.4:
                    t.set(x, GRAVE_BURIED, z, stones(x, GRAVE_BURIED, z))
    # The blade at the head of the grave: an iron post with a crossguard of chain, sunk in the cairn.
    t.set(c, GRAVE_BURIED, c - 3, "minecraft:mossy_cobblestone")
    t.set(c, GRAVE_BURIED + 1, c - 3, "minecraft:iron_bars", m3.bars())
    t.set(c, GRAVE_BURIED + 2, c - 3, "minecraft:iron_bars", m3.bars(east=True, west=True))
    t.set(c - 1, GRAVE_BURIED + 2, c - 3, CHAIN, {"axis": "x", "waterlogged": "false"})
    t.set(c + 1, GRAVE_BURIED + 2, c - 3, CHAIN, {"axis": "x", "waterlogged": "false"})
    # Puddles and hanging moss on a dead snag.
    for x, z in ((2, 9), (3, 9), (9, 3), (10, 4)):
        t.set(x, GRAVE_BURIED - 1, z, "minecraft:water", {"level": "0"})
    for y in range(GRAVE_BURIED, GRAVE_BURIED + 4):
        t.set(10, y, 9, MANGROVE_LOG, log("y"))
    t.set(10, GRAVE_BURIED + 4, 9, MANGROVE_LOG, log("x"))
    t.set(11, GRAVE_BURIED + 4, 9, MANGROVE_LOG, log("x"))
    t.set(11, GRAVE_BURIED + 3, 9, "minecraft:pale_hanging_moss", {"tip": "true"})
    t.set(c, GRAVE_BURIED - 1, 9, MUD)
    t.set(c, GRAVE_BURIED - 1, 11, MUD)
    t.spot("HEART", c, GRAVE_BURIED, 9)
    t.spot("SURFACE_GUARD", c, GRAVE_BURIED, 11)
    clear_above(t, GRAVE_BURIED)
    t.natural_below = True
    return t


# ---------------------------------------------------------------------------------------------
# Walking check: every anchor a player has to reach can be walked to from the template's way in
# ---------------------------------------------------------------------------------------------
WALK_THROUGH = {AIR, CAVE_AIR, LADDER, HUB_SPAWNER, SPAWNER, SEALED, HOLDFAST_GATE, IRON_DOOR, "minecraft:water"}


def walk_passable(name):
    """Whether a player's feet or head fit in a cell holding `name` (doors and seals count: they open)."""
    if name in WALK_THROUGH:
        return True
    if name is None:
        return False
    plain = name.split(":", 1)[1]
    return plain.endswith("_carpet") or plain.endswith("pressure_plate") or plain in ("dead_bush", "short_grass", "fern", "lever")


def walk_check(name, get, inside, start, targets, reach=2, rise=3):
    """Breadth-first walk from `start` over cells a player can stand in (feet and head free, a block or the natural
    ground below, or a ladder), stepping one up or down, climbing ladders. Each of `targets` {anchor: [cells]} must
    lie within `reach` across and `rise` up or down of a cell walked to. `get` reads a cell (None: the ground the
    template leaves), `inside` says whether a cell belongs to the template(s)."""
    def standable(x, y, z):
        if not inside(x, y, z) or not walk_passable(get(x, y, z)) or not walk_passable(get(x, y + 1, z)):
            return False
        if get(x, y, z) == LADDER or get(x, y - 1, z) == LADDER:
            return True
        below = get(x, y - 1, z)
        return below is None or not walk_passable(below) or below == "minecraft:water"

    if not standable(*start):
        raise SystemExit(f"{name}: the way in {start} is not a place to stand")
    seen = {start}
    queue = [start]
    while queue:
        x, y, z = queue.pop()
        steps = [(x + dx, y + dy, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) for dy in (-1, 0, 1)]
        if get(x, y, z) == LADDER:
            steps.append((x, y + 1, z))
        if get(x, y - 1, z) == LADDER:
            steps.append((x, y - 1, z))
        for step in steps:
            if step not in seen and standable(*step):
                if step[1] > y and not walk_passable(get(x, y + 2, z)) and get(x, y, z) != LADDER:
                    continue  # no room to step up
                seen.add(step)
                queue.append(step)
    missing = []
    for anchor, cells in targets.items():
        for (tx, ty, tz) in cells:
            if not any(abs(tx - x) <= reach and abs(tz - z) <= reach and abs(ty - y) <= rise for (x, y, z) in seen):
                missing.append(f"{anchor} {tx, ty, tz}")
    if missing:
        raise SystemExit(f"{name}: no way to walk from {start} to " + ", ".join(missing))
    return len(seen)


def template_walk(t, start_anchor, skip=()):
    """The walking check on one template from its anchor `start_anchor`, to every anchor but `skip`."""
    start = t.anchors[start_anchor][0]
    targets = {a: cells for a, cells in t.anchors.items() if a not in skip}
    return walk_check(t.name, t.get, t.inside, start, targets)


def hymnal_walk(frame):
    """The walking check across both Hymnal templates, from the mouth to every anchor of the halls."""
    ox, oy, oz = HYMNAL_HALLS_OFFSET
    targets = {a: [(x + ox, y + oy, z + oz) for (x, y, z) in cells] for a, cells in frame.halls.anchors.items()
               if a not in ("UNDERCROFT_MIN", "UNDERCROFT_MAX")}

    def inside(x, y, z):
        t, p = frame.locate(x, y, z)
        return t.inside(*p)
    return walk_check("site/hymnal", frame.get, inside, frame.arch.anchors["STAIR_MOUTH"][0], targets)


# =============================================================================================
# The M5 templates, the worldgen structures, their sets and tags, and the loot tables
# =============================================================================================
SALTGATE_ANCHORS = ["HEART", "PAD_BAY_1", "PAD_BAY_2", "PAD_BAY_3", "PAD_BAY_4", "PAD_TERMINAL", "HOST", "CONTRACT_TERMINAL", "MEDBAY",
                    "CANTINA", "VENDOR_FENCE", "NPC_VARRA", "COUNTING_HOUSE_DOOR"] + [f"COUNTING_HOUSE_{i}" for i in range(1, 9)] + \
                   ["NPC_DASK", "VENDOR_SHIPWRIGHT", "VENDOR_SPACER", "NPC_VESSBA", "NPC_TEEL"] + [f"ENFORCER_{i}" for i in range(1, 7)]
HOLDFAST_ANCHORS = ["HEART", "PAD_BAY_1", "PAD_BAY_2", "PAD_BAY_3", "PAD_BAY_4", "PAD_TERMINAL", "NPC_DORRUN", "NPC_VARRA", "VENDOR_ARMOURER",
                    "VENDOR_SPACER", "CONTRACT_TERMINAL", "MEDBAY"] + [f"WARRIOR_{i}" for i in range(1, 7)] + ["HOLDFAST_GATE"]
HYMNAL_HALLS_ANCHORS = ["HEART", "THRESHOLD", "DAIS", "LECTERN", "ROD_1", "ROD_2", "ROD_3", "ROD_4", "PYLON_1", "PYLON_2", "RESONATOR_1",
                        "RESONATOR_2", "RESONATOR_3", "RESONATOR_4", "HEART_DOOR", "UNDERCROFT_DOOR", "CHOIR_1", "CHOIR_2", "CHOIR_3",
                        "CHOIR_4", "SILENCE_1", "SILENCE_2", "SILENCE_3", "RING_1", "RING_2", "RING_3", "KEYSTONE", "CANTOR",
                        "UNDERCROFT_MIN", "UNDERCROFT_MAX", "SQUAD_1", "SQUAD_2", "SQUAD_3", "SENTINEL_1", "SENTINEL_2", "GUARD"]

# (builder, size, anchors written to the sidecar, anchor blocks particular to the template, walking check from)
M5_TEMPLATES = [
    (build_saltgate, SALTGATE_SIZE, SALTGATE_ANCHORS, {}, "HEART"),
    (build_holdfast, HOLDFAST_SIZE, HOLDFAST_ANCHORS, {}, "HEART"),
    (build_verse_stone, VERSE_SIZE, ["STONE", "HEART", "SPAWNER"], {}, "HEART"),
    (lambda: build_hymnal()[0], HYMNAL_ARCH_SIZE, ["STAIR_MOUTH"], {}, None),
    (lambda: build_hymnal()[1], HYMNAL_HALLS_SIZE, HYMNAL_HALLS_ANCHORS, {}, None),
    (build_debtor_camp, DEBTOR_SIZE, ["NPC_DEBTOR", "STRONGBOX", "SPAWNER", "HEART"], {}, "HEART"),
    (build_detention_post, DETENTION_SIZE, ["HEART", "WARDEN", "PRISONER_1", "PRISONER_2", "PRISONER_3", "GUARD_1", "GUARD_2", "GUARD_3",
                                            "GUARD_4"], {}, "HEART"),
    (build_crashed_freighter, FREIGHTER_SIZE, ["HEART", "SURFACE_GUARD", "CHEST_1", "CRATE", "CREW_1", "CREW_2", "CREW_3", "CREW_4"], {},
     "SURFACE_GUARD"),
    (build_kajidic_post, KAJIDIC_SIZE, ["HEART", "SURFACE_GUARD", "CHEST_1", "SQUAD"], {}, "SURFACE_GUARD"),
    (build_hymn_shrine, SHRINE_SIZE, ["HEART", "SURFACE_GUARD", "CHEST_1", "SQUAD"], {}, "SURFACE_GUARD"),
    (build_mire_grave, GRAVE_SIZE, ["HEART", "SURFACE_GUARD", "CHEST_1"], {}, "SURFACE_GUARD"),
]

M5_POOLS = {
    "crashed_freighter/start": [Element("planet/crashed_freighter", m3.RIGID, "minecraft:empty", m3.adapted(FREIGHTER_BURIED, 14, 4, 2))],
    "kajidic_post/start": [Element("planet/kajidic_post", m3.RIGID, "minecraft:empty", m3.adapted(1, 14, 3, 2))],
    "hymn_shrine/start": [Element("planet/hymn_shrine", m3.RIGID, "minecraft:empty", m3.adapted(1, 10, 3, 1))],
    "mire_grave/start": [Element("planet/mire_grave", m3.RIGID, "minecraft:empty", m3.adapted(GRAVE_BURIED, 9, 3, 1))],
}

M5_STRUCTURES = [
    Structure("crashed_freighter", "crashed_freighter/start", 1, 24, {"planet/crashed_freighter": 1}, {}),
    Structure("kajidic_post", "kajidic_post/start", 1, 16, {"planet/kajidic_post": 1}, {}),
    Structure("hymn_shrine", "hymn_shrine/start", 1, 16, {"planet/hymn_shrine": 1}, {}),
    Structure("mire_grave", "mire_grave/start", 1, 16, {"planet/mire_grave": 1}, {}),
]

# Structure sets (random spread, 3.2 and 3.3; fixed salts) and the biomes each structure starts in.
M5_SETS = {
    "crashed_freighters": ("crashed_freighter", 28, 10, 905201),
    "kajidic_posts": ("kajidic_post", 32, 12, 905203),
    "hymn_shrines": ("hymn_shrine", 30, 10, 905207),
    "mire_graves": ("mire_grave", 34, 12, 905209),
}
M5_STRUCTURE_BIOMES = {
    "crashed_freighter": ["oldrepublic:ghessar_dunes"],
    "kajidic_post": ["oldrepublic:ghessar_canyons"],
    "hymn_shrine": ["oldrepublic:tyrvann_stormwood", "oldrepublic:tyrvann_mire"],
    "mire_grave": ["oldrepublic:tyrvann_mire"],
}


def m5_structure_json(structure, templates):
    start = templates[M5_POOLS[structure.start_pool][0].template]
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


def write_templates_m5():
    enum_names = m4.java_anchor_names()
    templates = {}
    for builder, size, expected, blocks, walk_from in M5_TEMPLATES:
        t = builder()
        if not hasattr(t, "natural_below"):
            t.natural_below = False
        check_m5(t, size, expected, blocks, enum_names)
        if walk_from is not None:
            template_walk(t, walk_from)
        t.expected = expected
        templates[t.name] = t
    walked = hymnal_walk(build_hymnal()[2])
    print(f"site/hymnal: {walked} cells walkable from the mouth")
    for name, elements in M5_POOLS.items():
        for e in elements:
            if e.template not in templates:
                raise SystemExit(f"pool {name} names an unknown template {e.template}")
    for structure in M5_STRUCTURES:
        pieces = m3.assemble(structure, M5_POOLS, templates)
        print(f"{structure.name}: " + ", ".join(f"{p[0]} at {p[1]} {p[2]} (depth {p[3]})" for p in pieces))
    for name, t in templates.items():
        n, p = t.save(os.path.join(DATA, "structure", name + ".nbt"))
        m3.sidecar(os.path.join(DATA, "site_anchors", name + ".json"), {a: t.anchors[a] for a in t.expected})
        print(f"{name}: size {t.size}, {n} blocks, {p} palette entries, anchors {', '.join(t.expected)}")
    for name, elements in M5_POOLS.items():
        write_json(os.path.join(DATA, "worldgen", "template_pool", name + ".json"), m3.pool_json(elements))
    for structure in M5_STRUCTURES:
        write_json(os.path.join(DATA, "worldgen", "structure", structure.name + ".json"), m5_structure_json(structure, templates))
        write_json(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure", structure.name + ".json"),
                   {"values": M5_STRUCTURE_BIOMES[structure.name]})
    for set_name, (structure, spacing, separation, salt) in M5_SETS.items():
        write_json(os.path.join(DATA, "worldgen", "structure_set", set_name + ".json"), {
            "placement": {"type": "minecraft:random_spread", "salt": salt, "separation": separation, "spacing": spacing},
            "structures": [{"structure": MOD + structure, "weight": 1}],
        })
        write_json(os.path.join(DATA, "tags", "worldgen", "structure", set_name + ".json"),
                   {"values": [{"id": MOD + structure, "required": False}]})


# ---------------------------------------------------------------------------------------------
# Loot tables: the four blocks that drop something, and the four worldgen chests (3.2, 3.3)
# ---------------------------------------------------------------------------------------------
def set_count(lo, hi):
    return {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}, "add": False}


SILK_TOUCH = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}


def self_drop(block):
    return {
        "type": "minecraft:block",
        "pools": [{"bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": MOD + block}], "rolls": 1.0}],
        "random_sequence": MOD + "blocks/" + block,
    }


def no_drop(block):
    """An unbreakable site block's table: nothing drops (creative breaking, a command)."""
    return {"type": "minecraft:block", "pools": [], "random_sequence": MOD + "blocks/" + block}


def silk_or(block, drop, lo, hi, fortune):
    functions = [set_count(lo, hi)]
    if fortune:
        functions.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"})
    functions.append({"function": "minecraft:explosion_decay"})
    return {
        "type": "minecraft:block",
        "pools": [{"bonus_rolls": 0.0, "rolls": 1.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": MOD + block, "conditions": [SILK_TOUCH]},
            {"type": "minecraft:item", "name": MOD + drop, "functions": functions},
        ]}]}],
        "random_sequence": MOD + "blocks/" + block,
    }


def entry(name, weight, lo=1, hi=1, extra=()):
    e = {"type": "minecraft:item", "name": name if ":" in name else "minecraft:" + name, "weight": weight}
    functions = ([set_count(lo, hi)] if (lo, hi) != (1, 1) else []) + list(extra)
    if functions:
        e["functions"] = functions
    return e


def chest(name, pools):
    return {"type": "minecraft:chest", "pools": pools, "random_sequence": MOD + "chests/" + name}


def rolls(lo, hi):
    return float(lo) if lo == hi else {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}


LOOT_M5 = {
    "blocks/salt_crust": self_drop("salt_crust"),
    "blocks/peat": self_drop("peat"),
    "blocks/sunstone_ore": silk_or("sunstone_ore", "sunstone", 1, 2, True),
    "blocks/stormglass_cluster": silk_or("stormglass_cluster", "stormglass_shard", 1, 2, False),
    "blocks/verse_stone": no_drop("verse_stone"),
    "blocks/holdfast_gate": no_drop("holdfast_gate"),
    # A freighter's hold (the Sunstone Edge schematic is rolled by planet.PlanetM5Feature on top of this table).
    "chests/crashed_freighter": chest("crashed_freighter", [{"rolls": rolls(3, 5), "entries": [
        entry(MOD + "durasteel_plate", 8, 1, 2), entry(MOD + "alloy_plate", 4), entry("iron_ingot", 6, 1, 3),
        entry("copper_ingot", 6, 2, 4), entry(MOD + "sunstone", 4, 1, 2), entry("redstone", 4, 2, 4), entry(MOD + "medpac", 3),
        entry("gunpowder", 3, 1, 3)]}]),
    "chests/kajidic_post": chest("kajidic_post", [{"rolls": rolls(2, 4), "entries": [
        entry("gold_nugget", 8, 3, 8), entry(MOD + "sunstone", 5, 1, 3), entry(MOD + "durasteel_plate", 4, 1, 2), entry(MOD + "medpac", 3),
        entry("bread", 4, 1, 3), entry(MOD + "alloy_plate", 2), entry("iron_ingot", 4, 1, 2)]}]),
    "chests/hymn_shrine": chest("hymn_shrine", [{"rolls": rolls(2, 3), "entries": [
        entry(MOD + "stormglass_shard", 6, 1, 2), entry("candle", 4, 1, 3), entry("bone", 5, 1, 3), entry(MOD + "peat", 4, 2, 4),
        entry(MOD + "lumen_thread", 2), entry("echo_shard", 1)]}]),
    # A fallen clan warrior's cairn: the helmet, worn, always; a little of what they carried.
    "chests/mire_grave": chest("mire_grave", [
        {"rolls": 1.0, "entries": [entry(MOD + "mandalorian_helmet", 1, extra=[
            {"function": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": 0.25, "max": 0.6}}])]},
        {"rolls": rolls(1, 3), "entries": [entry("bone", 6, 1, 3), entry("iron_nugget", 5, 2, 6), entry(MOD + "peat", 4, 1, 3),
                                           entry(MOD + "clan_iron", 2)]}]),
}


def write_loot():
    for path, table in LOOT_M5.items():
        write_json(os.path.join(DATA, "loot_table", path + ".json"), table)


def main():
    router = None
    if "--router" in sys.argv:
        router = sys.argv[sys.argv.index("--router") + 1]
    out = None
    if "--out" in sys.argv:
        if "--planets" not in sys.argv:
            raise SystemExit("--out only goes with --planets")
        out = sys.argv[sys.argv.index("--out") + 1]
    write_planets(router, out)
    if "--planets" in sys.argv:
        return
    write_templates_m5()
    write_loot()


if __name__ == "__main__":
    main()
