#!/usr/bin/env python3
"""Generates Chapter Three's runtime site templates (gzipped structure NBT) and their anchor sidecars.

Deterministic: the same script always writes byte-identical files. Uses the NBT writer and Template
class of gen_structures.py. Every template is placed per player at runtime by RuntimeSites (spec
3.6); none is a worldgen piece. Writes under src/main/resources/data/oldrepublic/:

  structure/story/couriers_fall.nbt       15 x 9 x 11  Orren Vasch's crashed courier shuttle: a scorched
                                                       furrow, the hull dug in on its keel, the cargo bay
                                                       torn open to the north with its hatch flat on the
                                                       ground, the Wayfarer pedestal (always ready, fixed
                                                       to wayfarers_compass) in the bay, his strongbox,
                                                       a smouldering engine, a torn cockpit, debris
  structure/mission/patrol_ambush.nbt     13 x 7 x 13  R1: an old ruin road, a Republic speeder-sled on
                                                       its back, blaster scorches, a supply crate
  structure/mission/smuggler_cache.nbt    13 x 8 x 13  R2: a camouflaged earth dugout (turf and leaf
                                                       roof) holding the locked crate, a cook fire
  structure/mission/fence_camp.nbt        15 x 8 x 15  I1: the fence's canvas tent with his ledger on a
                                                       lectern, the camp fire ringed by log seats, goods
  structure/mission/survey_post.nbt       15 x 8 x 15  I2: a blasted Imperial survey post, its mast down
  structure/mission/raider_camp_small.nbt 11 x 7 x 11  clear_camp: a lean-to, a fire, stolen crates
  structure/mission/skirmish_line.nbt     17 x 6 x 9   patrol_intercept: a sandbag line and crates
  site_anchors/story/couriers_fall.json and site_anchors/mission/<name>.json
                                          {"<ANCHOR>": [[x, y, z], ...]}, template-local (15.1)

Rules (frozen in docs/MILESTONE_3.md 3.6 and 15.1; keep in step with the placing code):
  * `buried`: couriers_fall is placed with buried = 2 (ChapterThreeOpenings.FALL_BURIED): layer 1 is
    the ground, layer 0 the keel and the furrow floor. Every mission template is placed with
    buried = 1 (M2's camps): layer 0 is the ground, layer 1 the feet layer.
  * The entrance is the local north side (z = 0); RuntimeSites turns it to face the player.
  * Cells that are not set are left as they are (RuntimeSites clears natural clutter first). Air is
    written where something must stay open: interiors, the furrow, the approach and every spot.
  * Anchors are sidecars, not blocks. A spot anchor (SCAVENGER_n, DOWNED_n, SENTINEL_n, CREW_n,
    CHIEF, FENCE, SQUAD_n, WOUNDED) is an air feet cell with air above and a solid block below;
    nothing spawns from the template (no site_spawner, no entities): the mission and chapter code
    spawn at these cells when the owner comes near. Block anchors hold the real block:
      PEDESTAL_WAYFARER  oldrepublic:holocron_pedestal[kind=wayfarer, always_ready=true], block
                         entity fixed_id = "wayfarers_compass"
      STRONGBOX          a chest with loot table oldrepublic:gameplay/wayfarer_cache (his payments)
      CRATE              a barrel (patrol_ambush: the supply crate; smuggler_cache: the locked crate);
                         empty, the mission code hands out its contents on use
      BOOK               a lectern holding a book (the fence's ledger), facing the tent's mouth
      FIRE               a lit campfire
    No other barrel, chest or lectern stands in a mission template, so a player never mistakes decor
    for the crate.

Usage: python3 tools/assets/gen_structures_story_m3.py
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_structures import AIR, Template, hash01, rebuilt_in_m6, value_noise  # noqa: E402

DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "data", "oldrepublic")

GRASS = "minecraft:grass_block"
COARSE = "minecraft:coarse_dirt"
DIRT = "minecraft:dirt"
PATH = "minecraft:dirt_path"
GRAVEL = "minecraft:gravel"
PLATING = "oldrepublic:wreck_plating"
SCORCHED = "oldrepublic:wreck_plating_scorched"
CONDUIT = "oldrepublic:wreck_conduit"
REPUBLIC = "oldrepublic:republic_panel"
IMPERIAL = "oldrepublic:imperial_panel"
RUIN = "oldrepublic:ruin_stone"
RUIN_CRACKED = "oldrepublic:ruin_stone_cracked"
RUIN_MOSSY = "oldrepublic:ruin_stone_mossy"
RUIN_CARVED = "oldrepublic:ruin_stone_carved"

BARS = {"east": "false", "west": "false", "north": "false", "south": "false", "waterlogged": "false"}
FENCE = BARS
LANTERN = {"hanging": "false", "waterlogged": "false"}
LANTERN_HUNG = {"hanging": "true", "waterlogged": "false"}
BOTTOM_SLAB = {"type": "bottom", "waterlogged": "false"}
LEAVES = {"distance": "7", "persistent": "true", "waterlogged": "false"}


def campfire(facing="north", lit=True, signal=False):
    return {"lit": "true" if lit else "false", "facing": facing, "signal_fire": "true" if signal else "false", "waterlogged": "false"}


def stairs(facing, half="bottom"):
    return {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"}


def ground(t, y, seed, sx, sz, pick):
    """Writes layer `y` as ground; `pick(x, z, n)` names the block for a noise value n in 0..1."""
    for x in range(sx):
        for z in range(sz):
            name = pick(x, z, value_noise(x, 0, z, 2.5, seed))
            if name is not None:
                t.set(x, y, z, name, {"snowy": "false"} if name == GRASS else None)


def spot(t, anchors, name, x, y, z):
    """A spot anchor: air at the feet and head cells."""
    t.set(x, y, z, AIR)
    t.set(x, y + 1, z, AIR)
    anchors.setdefault(name, []).append((x, y, z))


def mark(anchors, name, x, y, z):
    anchors.setdefault(name, []).append((x, y, z))


def crate_stack(t, x, y, z, height, seed):
    """A stack of plank crates topped with a slab: decor that is not a container."""
    for i in range(height):
        t.set(x, y + i, z, "minecraft:spruce_planks" if hash01(seed, x, z, i) < 0.6 else "minecraft:stripped_spruce_log",
              None if hash01(seed, x, z, i) < 0.6 else {"axis": "x"})
    t.set(x, y + height, z, "minecraft:spruce_slab", BOTTOM_SLAB)


def seat(t, x, y, z, axis):
    t.set(x, y, z, "minecraft:stripped_spruce_log", {"axis": axis})


# =============================================================================================
# story/couriers_fall (15 x 9 x 11), buried = 2
# =============================================================================================
def build_couriers_fall():
    t = Template(15, 9, 11)
    anchors = {}
    seed = 31
    G = 1  # the ground layer
    F = 2  # the feet layer

    # The hull's outline (outer shell): x 4..11, z 3..8; the cockpit x 12..13; the nose x 14.
    def near_wreck(x, z):
        dx = max(0, 4 - x, x - 13)
        dz = max(0, 3 - z, z - 8)
        return max(dx, dz)

    furrow = {(0, 5), (0, 6)} | {(x, z) for x in (1, 2) for z in range(4, 8)}

    # Scorched ground: soot and fused earth close to the wreck and the furrow, grass further out.
    def pick(x, z, n):
        d = near_wreck(x, z)
        h = hash01(seed, x, z)
        if d <= 1 or (x <= 3 and 3 <= z <= 8):
            return "minecraft:blackstone" if h < 0.3 else ("minecraft:tuff" if h < 0.5 else COARSE)
        if d <= 3:
            return COARSE if n > 0.45 else (GRAVEL if h < 0.15 else GRASS)
        return COARSE if n > 0.7 else GRASS

    ground(t, G, seed, 15, 11, pick)

    # The furrow it ploughed coming in from the west: open at the ground layer, a scorched floor
    # below and the pushed-up soil heaped along both lips.
    for (x, z) in furrow:
        t.set(x, G, z, AIR)
        t.set(x, F, z, AIR)
        h = hash01(seed + 1, x, z)
        t.set(x, 0, z, "minecraft:blackstone" if h < 0.35 else (COARSE if h < 0.75 else "minecraft:tuff"))
    for x in range(0, 4):
        for z in ((3, 8) if x >= 1 else (4, 7)):
            t.set(x, F, z, COARSE if hash01(seed + 2, x, z) < 0.6 else "minecraft:rooted_dirt")
    t.set(1, F + 1, 3, COARSE)
    t.set(2, F + 1, 8, COARSE)

    def hull(x, y, z):
        """Plating, scorched about a third of the time."""
        t.set(x, y, z, SCORCHED if hash01(seed + 3, x, y, z) < 0.33 else PLATING)

    # Keel and deck, dug in.
    for x in range(5, 11):
        for z in (5, 6):
            t.set(x, 0, z, SCORCHED)
    for x in range(4, 12):
        for z in range(4, 8):
            hull(x, G, z)

    # Walls: north z = 3 (the cargo side), south z = 8 (lower, the hull rolled onto it), the tail
    # bulkhead x = 4 and the cockpit bulkhead x = 11. The roof steps down to the south.
    for x in range(4, 12):
        for y in range(G, 5):
            hull(x, y, 3)
        for y in range(G, 4):
            hull(x, y, 8)
    for z in range(4, 8):
        for y in range(G, 5 if z <= 6 else 4):
            hull(4, y, z)
            hull(11, y, z)
    for x in range(4, 12):
        for z in range(3, 7):
            hull(x, 5, z)
        for z in (7, 8):
            hull(x, 4, z)

    # The interior: x 5..10, z 4..7 (3 high, 2 high under the low side).
    for x in range(5, 11):
        for z in range(4, 8):
            for y in range(F, 5 if z <= 6 else 4):
                t.set(x, y, z, AIR)

    # The cargo bay torn open to the north, its hatch flung flat on the ground in front.
    for x in range(5, 10):
        for y in range(F, 5):
            t.set(x, y, 3, AIR)
    t.set(6, 4, 3, "minecraft:iron_bars", BARS)
    t.set(9, 4, 3, SCORCHED)
    for x in range(5, 10):
        for z in (1, 2):
            t.set(x, G, z, PLATING if hash01(seed + 4, x, z) < 0.7 else SCORCHED)
    # Keep the approach open.
    for x in range(4, 11):
        for z in range(0, 3):
            for y in (F, F + 1):
                t.set(x, y, z, AIR)

    # A burst in the roof (ribs showing) and in the low side.
    t.set(8, 5, 5, AIR)
    t.set(8, 5, 6, "minecraft:iron_bars", BARS)
    t.set(9, 5, 5, "minecraft:iron_bars", BARS)
    t.set(9, 4, 8, AIR)
    t.set(9, 3, 8, "minecraft:iron_bars", BARS)
    # Sparking conduits in the tail bulkhead and the south wall.
    t.set(4, 3, 6, CONDUIT)
    t.set(6, 3, 8, CONDUIT)

    # The cargo bay: the Wayfarer pedestal at the back, a hanging lamp, the strongbox and spilled
    # crates. PEDESTAL_WAYFARER and STRONGBOX are the blocks themselves.
    t.set(7, F, 6, "oldrepublic:holocron_pedestal", {"kind": "wayfarer", "ready": "false", "always_ready": "true"},
          {"id": "oldrepublic:holocron_pedestal", "fixed_id": "wayfarers_compass"})
    mark(anchors, "PEDESTAL_WAYFARER", 7, F, 6)
    t.set(7, 4, 5, "minecraft:lantern", LANTERN_HUNG)
    t.set(9, F, 7, "minecraft:chest", {"facing": "north", "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": "oldrepublic:gameplay/wayfarer_cache"})
    mark(anchors, "STRONGBOX", 9, F, 7)
    t.set(5, F, 7, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(5, F + 1, 7, "minecraft:barrel", {"facing": "west", "open": "false"})
    t.set(6, F, 7, "minecraft:spruce_slab", BOTTOM_SLAB)
    t.set(10, F, 4, "minecraft:barrel", {"facing": "north", "open": "false"})
    t.set(10, F, 7, "minecraft:spruce_planks")

    # The engine: two scorched nozzle rims around a smouldering core (smoke rises from the wreck).
    for z in (4, 7):
        for y in (F, F + 1):
            t.set(3, y, z, SCORCHED)
    t.set(3, G, 5, "minecraft:blackstone")
    t.set(3, G, 6, "minecraft:blackstone")
    t.set(3, F, 5, "minecraft:campfire", campfire("west"))
    t.set(3, F, 6, "minecraft:blackstone")
    t.set(3, F + 1, 5, AIR)
    t.set(3, F + 1, 6, AIR)
    t.set(3, F + 2, 6, SCORCHED)

    # A snapped dorsal fin and a bent antenna.
    for x in (5, 6, 7):
        t.set(x, 6, 5, SCORCHED)
    t.set(6, 7, 5, SCORCHED)
    for y in (6, 7, 8):
        t.set(10, y, 4, "minecraft:iron_bars", BARS)

    # The cockpit, torn open: the bulkhead burst, two seats, a sparking console, a cracked canopy.
    for z in (5, 6):
        for y in (F, F + 1):
            t.set(11, y, z, AIR)
    for x in (12, 13):
        for z in range(4, 8):
            hull(x, G, z)
        for z in (4, 7):
            hull(x, F, z)
            if hash01(seed + 5, x, z) < 0.6:
                t.set(x, F + 1, z, "minecraft:gray_stained_glass_pane", BARS)
            else:
                t.set(x, F + 1, z, AIR)
        for z in (5, 6):
            for y in (F + 1, F + 2):
                t.set(x, y, z, AIR)
    t.set(12, F + 2, 4, "minecraft:gray_stained_glass_pane", BARS)
    t.set(12, F, 5, "minecraft:polished_blackstone_stairs", stairs("west"))
    t.set(12, F, 6, "minecraft:polished_blackstone_stairs", stairs("west"))
    t.set(13, F, 5, CONDUIT)
    t.set(13, F, 6, SCORCHED)
    # The nose, crumpled into a mound of earth.
    t.set(14, G, 5, SCORCHED)
    t.set(14, G, 6, SCORCHED)
    for z in (4, 5, 6, 7):
        t.set(14, F, z, COARSE if z in (5, 6) else DIRT)
    t.set(14, F + 1, 5, COARSE)

    # Debris thrown clear.
    t.set(0, F, 9, SCORCHED)
    t.set(1, F, 10, "minecraft:iron_bars", BARS)
    t.set(12, F, 9, SCORCHED)
    t.set(13, F, 10, "minecraft:spruce_slab", BOTTOM_SLAB)
    t.set(0, F, 2, SCORCHED)
    t.set(13, F, 0, "minecraft:iron_bars", BARS)

    # The two crystal poachers already picking at it: one by the furrow, one by the nose.
    spot(t, anchors, "SCAVENGER_1", 2, F, 1)
    spot(t, anchors, "SCAVENGER_2", 12, F, 2)
    return t, anchors


# =============================================================================================
# mission/patrol_ambush (13 x 7 x 13), buried = 1: R1 "Silent Patrol"
# =============================================================================================
def build_patrol_ambush():
    t = Template(13, 7, 13)
    anchors = {}
    seed = 41

    # An old ruin road runs north to south through the middle, paving half lost under the grass.
    def pick(x, z, n):
        h = hash01(seed, x, z)
        if 4 <= x <= 8:
            if h < 0.45:
                return RUIN if h < 0.25 else (RUIN_MOSSY if h < 0.37 else RUIN_CRACKED)
            return PATH if n > 0.4 else COARSE
        return COARSE if n > 0.68 else GRASS

    ground(t, 0, seed, 13, 13, pick)
    # Blaster scorches where the patrol made its stand.
    for (x, z) in ((6, 7), (5, 4), (9, 9), (3, 6), (7, 10)):
        t.set(x, 0, z, "minecraft:blackstone")

    # Broken kerbs and a toppled ruin pillar along the road.
    for (x, z, h) in ((3, 1, 1), (3, 4, 1), (9, 2, 1), (9, 11, 1), (3, 9, 2)):
        for y in range(1, 1 + h):
            t.set(x, y, z, RUIN_CRACKED if hash01(seed + 1, x, y, z) < 0.5 else RUIN)
    for y in range(1, 4):
        t.set(1, y, 1, RUIN_CARVED if y == 3 else RUIN)
    for x in (10, 11, 12):
        t.set(x, 1, 1, RUIN_MOSSY if x == 12 else RUIN)
    t.set(11, 1, 0, RUIN_CRACKED)
    for y in range(1, 3):
        t.set(11, y, 12, RUIN)
    t.set(0, 1, 7, RUIN_MOSSY)
    t.set(0, 1, 8, RUIN_CRACKED)

    # The speeder-sled, flipped onto its back: white hull, skid struts in the air, the engine pod
    # torn off and scorched, a handlebar bent out.
    for x in (8, 9, 10):
        for z in (5, 6):
            t.set(x, 1, z, REPUBLIC)
    t.set(9, 2, 5, REPUBLIC)
    t.set(9, 2, 6, REPUBLIC)
    t.set(8, 2, 6, "minecraft:iron_bars", BARS)
    t.set(10, 2, 6, "minecraft:iron_bars", BARS)
    t.set(10, 2, 5, "minecraft:iron_bars", BARS)
    t.set(7, 1, 5, SCORCHED)
    t.set(11, 1, 6, "minecraft:iron_trapdoor", {"facing": "west", "half": "bottom", "open": "false", "powered": "false", "waterlogged": "false"})

    # The supply crate, thrown upright beside the sled.
    t.set(10, 1, 8, "minecraft:barrel", {"facing": "up", "open": "false"})
    mark(anchors, "CRATE", 10, 1, 8)

    # Three downed troopers where they fell, three bound sentinels among the stones.
    spot(t, anchors, "DOWNED_1", 5, 1, 6)
    spot(t, anchors, "DOWNED_2", 6, 1, 9)
    spot(t, anchors, "DOWNED_3", 8, 1, 3)
    spot(t, anchors, "SENTINEL_1", 2, 1, 3)
    spot(t, anchors, "SENTINEL_2", 10, 1, 10)
    spot(t, anchors, "SENTINEL_3", 2, 1, 10)
    return t, anchors


# =============================================================================================
# mission/smuggler_cache (13 x 8 x 13), buried = 1: R2 "Credits from the Core"
# =============================================================================================
def build_smuggler_cache():
    t = Template(13, 8, 13)
    anchors = {}
    seed = 43

    def pick(x, z, n):
        if 3 <= x <= 9 and 5 <= z <= 10:
            return None  # the dugout writes its own floor
        return COARSE if n > 0.6 else (PATH if n > 0.52 else GRASS)

    ground(t, 0, seed, 13, 13, pick)

    # The dugout: an earth mound over a timber frame, x 3..9, z 5..10, its mouth on the north.
    for x in range(3, 10):
        for z in range(5, 11):
            t.set(x, 0, z, "minecraft:packed_mud" if hash01(seed, x, z) < 0.5 else COARSE)
    for x in range(3, 10):
        for z in range(5, 11):
            edge = x in (3, 9) or z in (5, 10)
            for y in (1, 2):
                t.set(x, y, z, (DIRT if hash01(seed + 1, x, y, z) < 0.5 else "minecraft:rooted_dirt") if edge else AIR)
            roof = GRASS if edge or hash01(seed + 2, x, z) < 0.85 else "minecraft:moss_block"
            t.set(x, 3, z, roof, {"snowy": "false"} if roof == GRASS else None)
    # Timber posts at the corners of the room and over the mouth.
    for (x, z) in ((4, 6), (8, 6), (4, 9), (8, 9)):
        t.set(x, 1, z, "minecraft:stripped_spruce_log", {"axis": "y"})
        t.set(x, 2, z, "minecraft:stripped_spruce_log", {"axis": "y"})
    for x in range(4, 9):
        t.set(x, 3, 7, "minecraft:spruce_log", {"axis": "x"})
    # The mouth: two wide, one step in, hung with a leaf screen at the sides.
    for x in (6, 7):
        t.set(x, 1, 5, AIR)
        t.set(x, 2, 5, AIR)
        t.set(x, 0, 5, "minecraft:spruce_planks")
    # Camouflage: turf, moss and bushes over the roof.
    for x in range(3, 10):
        for z in range(5, 11):
            h = hash01(seed + 3, x, z)
            if h < 0.45:
                t.set(x, 4, z, "minecraft:oak_leaves" if h < 0.3 else "minecraft:moss_carpet", LEAVES if h < 0.3 else None)
    t.set(5, 1, 4, "minecraft:oak_leaves", LEAVES)
    t.set(8, 1, 4, "minecraft:oak_leaves", LEAVES)
    t.set(5, 2, 4, "minecraft:oak_leaves", LEAVES)
    # Inside: the locked crate at the back, a lamp, a bedroll.
    t.set(7, 1, 9, "minecraft:barrel", {"facing": "up", "open": "false"})
    mark(anchors, "CRATE", 7, 1, 9)
    t.set(5, 1, 9, "minecraft:brown_carpet")
    t.set(5, 1, 8, "minecraft:brown_carpet")
    t.set(4, 2, 8, "minecraft:lantern", LANTERN_HUNG)

    # Outside: a cook fire with log seats, stacked plank crates under a tarp.
    t.set(2, 1, 1, "minecraft:campfire", campfire("south"))
    seat(t, 1, 1, 2, "z")
    seat(t, 3, 1, 1, "x")
    crate_stack(t, 11, 1, 6, 2, seed)
    crate_stack(t, 11, 1, 7, 1, seed)
    for z in (5, 8):
        for y in (1, 2, 3):
            t.set(12, y, z, "minecraft:spruce_fence", FENCE)
    for x in (11, 12):
        for z in (5, 6, 7, 8):
            t.set(x, 4, z, "minecraft:green_wool")

    # The chief in front of the mouth, four poachers round the camp.
    spot(t, anchors, "CHIEF", 6, 1, 3)
    spot(t, anchors, "CREW_1", 3, 1, 3)
    spot(t, anchors, "CREW_2", 10, 1, 3)
    spot(t, anchors, "CREW_3", 1, 1, 9)
    spot(t, anchors, "CREW_4", 11, 1, 10)
    return t, anchors


# =============================================================================================
# mission/fence_camp (15 x 8 x 15), buried = 1: I1 "Names"
# =============================================================================================
def build_fence_camp():
    t = Template(15, 8, 15)
    anchors = {}
    seed = 47

    def pick(x, z, n):
        d = abs(x - 7) + abs(z - 5)
        if d <= 2:
            return PATH if n > 0.3 else COARSE
        return COARSE if n > 0.66 else GRASS

    ground(t, 0, seed, 15, 15, pick)

    # The fence's tent: an A-frame of canvas, x 8..12, z 8..12, its mouth on the north.
    for z in range(8, 13):
        t.set(8, 1, z, "minecraft:white_wool")
        t.set(12, 1, z, "minecraft:white_wool")
        t.set(9, 2, z, "minecraft:white_wool")
        t.set(11, 2, z, "minecraft:white_wool")
        t.set(10, 3, z, "minecraft:brown_wool")
        for (x, y) in ((9, 1), (10, 1), (11, 1), (10, 2)):
            t.set(x, y, z, AIR)
        t.set(9, 0, z, "minecraft:spruce_planks")
        t.set(10, 0, z, "minecraft:spruce_planks")
        t.set(11, 0, z, "minecraft:spruce_planks")
    for x in (9, 10, 11):
        t.set(x, 1, 12, "minecraft:white_wool")
    t.set(10, 2, 12, "minecraft:white_wool")
    # Inside: his ledger on a lectern facing the mouth, a bedroll, a lamp.
    t.set(10, 1, 11, "minecraft:lectern", {"facing": "north", "has_book": "true", "powered": "false"})
    mark(anchors, "BOOK", 10, 1, 11)
    t.set(9, 1, 10, "minecraft:red_carpet")
    t.set(9, 1, 11, "minecraft:red_carpet")
    t.set(11, 1, 10, "minecraft:lantern", LANTERN)
    # Guy ropes (fence posts) at the corners.
    for (x, z) in ((7, 8), (13, 8), (7, 12), (13, 12)):
        t.set(x, 1, z, "minecraft:spruce_fence", FENCE)

    # The camp fire, ringed by log seats; a cooking spit across it.
    t.set(7, 1, 5, "minecraft:campfire", campfire("north"))
    mark(anchors, "FIRE", 7, 1, 5)
    seat(t, 5, 1, 5, "z")
    seat(t, 9, 1, 5, "z")
    seat(t, 7, 1, 3, "x")

    # Goods for sale: plank crates, a rack of hides, lanterns on posts.
    crate_stack(t, 2, 1, 10, 2, seed)
    crate_stack(t, 3, 1, 10, 1, seed)
    crate_stack(t, 2, 1, 11, 1, seed)
    for z in (11, 12, 13):
        t.set(5, 2, z, "minecraft:brown_carpet" if z != 12 else "minecraft:white_carpet")
        t.set(5, 1, z, "minecraft:spruce_fence", FENCE)
    for (x, z) in ((1, 1), (13, 1)):
        t.set(x, 1, z, "minecraft:spruce_fence", FENCE)
        t.set(x, 2, z, "minecraft:spruce_fence", FENCE)
        t.set(x, 3, z, "minecraft:lantern", LANTERN)

    # The fence by his tent, four poachers round the camp.
    spot(t, anchors, "FENCE", 10, 1, 6)
    spot(t, anchors, "CREW_1", 3, 1, 3)
    spot(t, anchors, "CREW_2", 12, 1, 3)
    spot(t, anchors, "CREW_3", 3, 1, 7)
    spot(t, anchors, "CREW_4", 6, 1, 10)
    return t, anchors


# =============================================================================================
# mission/survey_post (15 x 8 x 15), buried = 1: I2 "Border Incident"
# =============================================================================================
def build_survey_post():
    t = Template(15, 8, 15)
    anchors = {}
    seed = 53

    def pick(x, z, n):
        if 4 <= x <= 10 and 4 <= z <= 10:
            return "minecraft:gray_concrete" if hash01(seed, x, z) < 0.7 else "minecraft:light_gray_concrete"
        return COARSE if n > 0.64 else GRASS

    ground(t, 0, seed, 15, 15, pick)
    for (x, z) in ((6, 6), (8, 9), (5, 8), (3, 3), (11, 5), (9, 12)):
        t.set(x, 0, z, "minecraft:blackstone")

    # The post's low walls, x 4..10, z 4..10, blasted through in places; the gate on the north.
    gaps = {(6, 4), (7, 4), (8, 4), (10, 7), (4, 6), (5, 10)}
    for x in range(4, 11):
        for z in range(4, 11):
            if x in (4, 10) or z in (4, 10):
                if (x, z) in gaps:
                    continue
                t.set(x, 1, z, IMPERIAL)
                if hash01(seed + 1, x, z) < 0.55:
                    t.set(x, 2, z, IMPERIAL)
    # A lean-to roof over the south half, half collapsed.
    for x in range(5, 10):
        for z in (8, 9):
            if hash01(seed + 2, x, z) < 0.65:
                t.set(x, 3, z, IMPERIAL)
    for (x, z) in ((5, 9), (9, 9)):
        t.set(x, 1, z, "minecraft:polished_deepslate_wall", {"east": "none", "west": "none", "north": "none", "south": "none", "up": "true", "waterlogged": "false"})
        t.set(x, 2, z, "minecraft:polished_deepslate_wall", {"east": "none", "west": "none", "north": "none", "south": "none", "up": "true", "waterlogged": "false"})
    # Inside: a sparking survey console, a fallen sensor dish.
    t.set(6, 1, 9, CONDUIT)
    t.set(8, 1, 6, "minecraft:polished_deepslate_slab", BOTTOM_SLAB)
    t.set(9, 1, 6, "minecraft:polished_deepslate_slab", BOTTOM_SLAB)
    # The survey mast, shot through: its stub stands, the rest lies across the grass.
    t.set(11, 1, 11, IMPERIAL)
    t.set(11, 2, 11, "minecraft:iron_bars", BARS)
    t.set(11, 3, 11, "minecraft:iron_bars", BARS)
    for x in (12, 13, 14):
        t.set(x, 1, 11, "minecraft:lightning_rod", {"facing": "east", "powered": "false", "waterlogged": "false"})
    # The Imperial pennant on its pole.
    for y in (1, 2, 3, 4):
        t.set(2, y, 12, "minecraft:dark_oak_fence", FENCE)
    t.set(2, 4, 11, "minecraft:red_wool")
    t.set(2, 3, 11, "minecraft:black_wool")

    # The downed Imperial inside, the Republic recon squad holding the post.
    spot(t, anchors, "WOUNDED", 7, 1, 8)
    spot(t, anchors, "SQUAD_1", 7, 1, 3)
    spot(t, anchors, "SQUAD_2", 3, 1, 5)
    spot(t, anchors, "SQUAD_3", 11, 1, 4)
    spot(t, anchors, "SQUAD_4", 5, 1, 6)
    spot(t, anchors, "SQUAD_5", 9, 1, 7)
    return t, anchors


# =============================================================================================
# mission/raider_camp_small (11 x 7 x 11), buried = 1: clear_camp
# =============================================================================================
def build_raider_camp_small():
    t = Template(11, 7, 11)
    anchors = {}
    seed = 59

    def pick(x, z, n):
        return COARSE if n > 0.6 else (PATH if n > 0.5 else GRASS)

    ground(t, 0, seed, 11, 11, pick)

    # A lean-to against the south edge.
    for x in range(2, 7):
        t.set(x, 3, 9, "minecraft:brown_wool")
        t.set(x, 2, 8, "minecraft:brown_wool")
    for x in (2, 6):
        t.set(x, 1, 9, "minecraft:spruce_fence", FENCE)
        t.set(x, 2, 9, "minecraft:spruce_fence", FENCE)
    t.set(3, 1, 9, "minecraft:brown_carpet")
    t.set(4, 1, 9, "minecraft:brown_carpet")
    # The fire, a log seat, stolen crates, a crystal-cutter's bench.
    t.set(5, 1, 5, "minecraft:campfire", campfire("north"))
    seat(t, 5, 1, 7, "x")
    crate_stack(t, 9, 1, 2, 2, seed)
    crate_stack(t, 9, 1, 3, 1, seed)
    t.set(1, 1, 1, "minecraft:stonecutter", {"facing": "south"})
    t.set(2, 1, 1, "minecraft:spruce_planks")
    t.set(2, 2, 1, "minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"})

    spot(t, anchors, "CREW_1", 3, 1, 3)
    spot(t, anchors, "CREW_2", 7, 1, 3)
    spot(t, anchors, "CREW_3", 3, 1, 6)
    spot(t, anchors, "CREW_4", 8, 1, 7)
    return t, anchors


# =============================================================================================
# mission/skirmish_line (17 x 6 x 9), buried = 1: patrol_intercept
# =============================================================================================
def build_skirmish_line():
    t = Template(17, 6, 9)
    anchors = {}
    seed = 61

    def pick(x, z, n):
        if z == 4 or z == 5:
            return COARSE if n > 0.35 else DIRT
        return COARSE if n > 0.62 else GRASS

    ground(t, 0, seed, 17, 9, pick)
    for (x, z) in ((4, 2), (11, 1), (8, 3), (14, 3)):
        t.set(x, 0, z, "minecraft:blackstone")

    # The sandbag line facing north: packed mud with mud-brick caps, two firing gaps.
    for x in range(1, 16):
        if x in (5, 11):
            continue
        t.set(x, 1, 4, "minecraft:packed_mud")
        if hash01(seed, x) < 0.6:
            t.set(x, 2, 4, "minecraft:mud_brick_slab", BOTTOM_SLAB)
    for x in (0, 16):
        t.set(x, 1, 5, "minecraft:packed_mud")
    # Ammunition and ration crates behind the line, a tarp over the middle.
    crate_stack(t, 2, 1, 7, 1, seed)
    crate_stack(t, 3, 1, 7, 2, seed)
    crate_stack(t, 13, 1, 7, 1, seed)
    for x in range(7, 10):
        t.set(x, 3, 8, "minecraft:gray_wool")
        t.set(x, 3, 7, "minecraft:gray_wool")
    for x in (7, 9):
        t.set(x, 1, 8, "minecraft:spruce_fence", FENCE)
        t.set(x, 2, 8, "minecraft:spruce_fence", FENCE)

    # The squad behind its line.
    spot(t, anchors, "SQUAD_1", 4, 1, 5)
    spot(t, anchors, "SQUAD_2", 8, 1, 5)
    spot(t, anchors, "SQUAD_3", 12, 1, 5)
    spot(t, anchors, "SQUAD_4", 6, 1, 6)
    spot(t, anchors, "SQUAD_5", 10, 1, 6)
    return t, anchors


# =============================================================================================
TEMPLATES = (
    ("story/couriers_fall", (15, 9, 11), 2, ["PEDESTAL_WAYFARER", "STRONGBOX", "SCAVENGER_1", "SCAVENGER_2"], build_couriers_fall),
    ("mission/patrol_ambush", (13, 7, 13), 1, ["DOWNED_1", "DOWNED_2", "DOWNED_3", "SENTINEL_1", "SENTINEL_2", "SENTINEL_3", "CRATE"], build_patrol_ambush),
    ("mission/smuggler_cache", (13, 8, 13), 1, ["CREW_1", "CREW_2", "CREW_3", "CREW_4", "CHIEF", "CRATE"], build_smuggler_cache),
    ("mission/fence_camp", (15, 8, 15), 1, ["CREW_1", "CREW_2", "CREW_3", "CREW_4", "FENCE", "BOOK", "FIRE"], build_fence_camp),
    ("mission/survey_post", (15, 8, 15), 1, ["SQUAD_1", "SQUAD_2", "SQUAD_3", "SQUAD_4", "SQUAD_5", "WOUNDED"], build_survey_post),
    ("mission/raider_camp_small", (11, 7, 11), 1, ["CREW_1", "CREW_2", "CREW_3", "CREW_4"], build_raider_camp_small),
    ("mission/skirmish_line", (17, 6, 9), 1, ["SQUAD_1", "SQUAD_2", "SQUAD_3", "SQUAD_4", "SQUAD_5"], build_skirmish_line),
)

SPOTS = {"SCAVENGER_1", "SCAVENGER_2", "DOWNED_1", "DOWNED_2", "DOWNED_3", "SENTINEL_1", "SENTINEL_2", "SENTINEL_3",
         "CREW_1", "CREW_2", "CREW_3", "CREW_4", "CHIEF", "FENCE", "SQUAD_1", "SQUAD_2", "SQUAD_3", "SQUAD_4", "SQUAD_5", "WOUNDED"}
BLOCKS = {"PEDESTAL_WAYFARER": "oldrepublic:holocron_pedestal", "STRONGBOX": "minecraft:chest", "CRATE": "minecraft:barrel",
          "BOOK": "minecraft:lectern", "FIRE": "minecraft:campfire"}
CONTAINERS = {"minecraft:barrel", "minecraft:chest", "minecraft:lectern"}


def check(name, size, buried, expected, t, anchors):
    """The frozen size and anchor names, every anchor cell holding the right thing, and no decoy containers."""
    if t.size != size:
        raise SystemExit(f"{name}: size {t.size}, expected {size}")
    if list(anchors) != expected and sorted(anchors) != sorted(expected):
        raise SystemExit(f"{name}: anchors {sorted(anchors)}, expected {sorted(expected)}")
    feet = buried
    for anchor, cells in anchors.items():
        for (x, y, z) in cells:
            if not t.inside(x, y, z):
                raise SystemExit(f"{name}: anchor {anchor} at {(x, y, z)} is outside {t.size}")
            if anchor in SPOTS:
                below = t.get(x, y - 1, z)
                if t.get(x, y, z) != AIR or t.get(x, y + 1, z) != AIR:
                    raise SystemExit(f"{name}: spot {anchor} at {(x, y, z)} is not open")
                if y != feet or (below is not None and below == AIR):
                    raise SystemExit(f"{name}: spot {anchor} at {(x, y, z)} does not stand on the ground layer")
            elif t.get(x, y, z) != BLOCKS[anchor]:
                raise SystemExit(f"{name}: anchor {anchor} at {(x, y, z)} holds {t.get(x, y, z)}")
    if name.startswith("mission/"):
        marked = {cell for cells in anchors.values() for cell in cells}
        for pos, cell in t.cells.items():
            if cell[0] in CONTAINERS and pos not in marked:
                raise SystemExit(f"{name}: decoy container {cell[0]} at {pos}")


def sidecar(path, anchors):
    if rebuilt_in_m6(path):
        return
    text = "{\n" + ",\n".join(
        f'  "{anchor}": [' + ", ".join(f"[{x}, {y}, {z}]" for (x, y, z) in cells) + "]"
        for anchor, cells in anchors.items()) + "\n}\n"
    json.loads(text)  # well-formed
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write(text)


def main():
    for name, size, buried, expected, builder in TEMPLATES:
        t, anchors = builder()
        anchors = {anchor: anchors[anchor] for anchor in expected if anchor in anchors}
        check(name, size, buried, expected, t, anchors)
        n, p = t.save(os.path.join(DATA, "structure", name + ".nbt"))
        sidecar(os.path.join(DATA, "site_anchors", name + ".json"), anchors)
        print(f"{name}: size {t.size}, buried {buried}, {n} blocks, {p} palette entries, anchors {', '.join(anchors)}")


if __name__ == "__main__":
    main()
