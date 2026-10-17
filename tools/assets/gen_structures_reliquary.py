#!/usr/bin/env python3
"""Generates the Reliquary templates (gzipped structure NBT) and their anchor sidecars (W5).

Deterministic: the same script always writes byte-identical files. Uses the NBT writer, Template,
hash01 and value_noise of gen_structures.py (imported, never edited). Writes:

  src/main/resources/data/oldrepublic/structure/boss/reliquary_surface.nbt   17 x 16 x 17
  src/main/resources/data/oldrepublic/structure/boss/reliquary_hall.nbt      31 x 18 x 31
  src/main/resources/data/oldrepublic/site_anchors/boss/<same names>.json    {"<ANCHOR>": [[x, y, z], ...]}

Template-local coordinates: x east, y up, z south. Both templates are placed with the same rotation
(pivot ZERO, no mirror) by com.oldrepublic.boss.ReliquarySites, which keeps these rules in step:

  * reliquary_surface is a surface template with buried = 9: layers 0-8 are underground (layer 8 is
    the ground under the stand), layers 9-15 the watchtower stump. Its entrance is the local north
    side (z = 0), turned to face the player. Its centre column (8, 8) is the descent's axis.
  * reliquary_hall goes directly below the surface: its top layer (y = 17) is the row under the
    surface's layer 0 (stand height - 10, so its top face is at stand height - 9), and its column
    SHAFT_HALL lies under the surface's centre column, so the spiral descent runs on through both
    templates into the antechamber. ReliquarySites.HALL_SHAFT and SURFACE_SHAFT hold these columns.
  * Every cell of the hall is written (rock, masonry or air). Surface cells that are not written keep
    the terrain (the game adapts the ground around the stump first).
  * The heart cell (anchor HEART) is air: the build sets reliquary_heart there last, facing the
    template's north turned by the placement rotation.

The hall (spec 8.2, with the sizes that fit 31 x 31; see docs/design/boss.md "Reliquary placement"):

    z  0      north wall
    z  1-5    the raised platform (+3): wings x 6-9 (P1) and x 21-24 (P2), dais x 10-20 with the lectern,
              shelves and Vasch's spot; the hoard vault is sunk inside the dais (x 11-19, z 1-4,
              interior 9 x 4 x 3), its vault seal in the dais front (x 14-16, z 5)
    z  6-19   the hall floor: sunken floor 15 x 14 (x 8-22, -1) with six broken 2 x 2 columns and four
              debris piles; wing stairs at z 6-8; open undercrofts under the galleries
    x  1-5 /
       25-29  galleries (+5) from z 1 to 16, two sentinel alcoves each; the south balcony (+5, z 17-19)
              joins them; gallery stairs x 6-7 / 23-24 at z 12-16 climb south to the balcony
    z 20      threshold wall: the door seal (x 14-16, +5) opens from the balcony into the antechamber
    z 21-29   antechamber 9 x 9 (x 11-19, +5): vanilla bed, the heart, a rack of emptied cradles; the
              spiral descent arrives through its east wall from the shaft at x 21-27, z 22-28

Art rule: no floor circles, sigils or runes. Light comes from crystal sconces: amethyst clusters on
brackets with a vanilla light block (level 7) beside each, so nothing flickers.

Usage: python3 tools/assets/gen_structures_reliquary.py
"""

import json
import math
import os
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_structures import AIR, Int, Template, hash01, value_noise  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "..", "..", "src", "main", "resources", "data", "oldrepublic")
STRUCTURES = os.path.join(DATA, "structure", "boss")
SIDECARS = os.path.join(DATA, "site_anchors", "boss")

SURFACE_SIZE = (17, 16, 17)
HALL_SIZE = (31, 18, 31)
SURFACE_BURIED = 9
# The descent axis: the surface's centre column and the hall column under it (ReliquarySites keeps the same).
SHAFT_SURFACE = (8, 8)
SHAFT_HALL = (24, 25)
# Hall-local y = surface-local y + HALL_DY (the hall's top layer is the row under the surface's layer 0).
HALL_DY = HALL_SIZE[1]

# Hall levels (block y of the floor; feet stand one higher).
FLOOR_Y = 2
SUNK_Y = 1
DAIS_Y = 5
GAL_Y = 7
CEIL_Y = 16

# ---------------------------------------------------------------------------------------------
# Blocks
# ---------------------------------------------------------------------------------------------
WF = {"waterlogged": "false"}
LIGHT7 = ("minecraft:light", {"level": "7", "waterlogged": "false"})
PYLON = ("oldrepublic:resonance_pylon", {"crystal": "dormant"})
DOOR_SEAL = ("oldrepublic:reliquary_seal", {"open": "true", "vault": "false"})
VAULT_SEAL = ("oldrepublic:reliquary_seal", {"open": "false", "vault": "true"})
PLINTH = ("oldrepublic:hoard_plinth", {"facing": "south"})

NON_SOLID = {
    AIR, "minecraft:cave_air", "minecraft:light", "minecraft:amethyst_cluster", "minecraft:large_amethyst_bud",
    "minecraft:medium_amethyst_bud", "minecraft:small_amethyst_bud", "minecraft:iron_chain", "minecraft:cobweb",
    "minecraft:moss_carpet", "minecraft:candle", "oldrepublic:reliquary_seal_open",
}
# Blocks a mob or player can stand on top of (full or stepped tops).
STEPPED = ("_stairs", "_slab")


def stairs(name, facing):
    return (name, {"facing": facing, "half": "bottom", "shape": "straight", "waterlogged": "false"})


def slab(name, kind="bottom"):
    return (name, {"type": kind, "waterlogged": "false"})


def cluster(facing, name="minecraft:amethyst_cluster"):
    return (name, {"facing": facing, "waterlogged": "false"})


def empty_shelf(facing):
    props = {"facing": facing}
    for i in range(6):
        props[f"slot_{i}_occupied"] = "false"
    return ("minecraft:chiseled_bookshelf", props)


def put(t, x, y, z, block, nbt=None):
    name, props = block if isinstance(block, tuple) else (block, None)
    t.set(x, y, z, name, props, nbt)


def natural_rock(seed):
    """The rock the hall is cut into: deepslate with tuff veins, as deep ground looks."""
    def fn(x, y, z):
        n = value_noise(x, y, z, 5.0, seed)
        if n > 0.68:
            return "minecraft:tuff"
        if hash01(seed, x, y, z) < 0.03:
            return "minecraft:cobbled_deepslate"
        return "minecraft:deepslate"
    return fn


def masonry(seed):
    """Dressed walls of the hall: deepslate bricks, weathered here and there."""
    def fn(x, y, z):
        r = hash01(seed, x, y, z)
        if r < 0.16:
            return "minecraft:cracked_deepslate_bricks"
        if r < 0.24:
            return "minecraft:deepslate_tiles"
        return "minecraft:deepslate_bricks"
    return fn


def floor_stone(seed):
    def fn(x, y, z):
        r = hash01(seed, x, y, z)
        if r < 0.12:
            return "minecraft:tuff_bricks"
        if r < 0.18:
            return "minecraft:tuff"
        return "minecraft:polished_tuff"
    return fn


def ruin_stone(seed):
    """The watchtower stump: weathered stone bricks."""
    def fn(x, y, z):
        r = hash01(seed, x, y, z)
        if r < 0.22:
            return "minecraft:mossy_stone_bricks"
        if r < 0.38:
            return "minecraft:cracked_stone_bricks"
        if r < 0.42:
            return "minecraft:chiseled_stone_bricks"
        return "minecraft:stone_bricks"
    return fn


# ---------------------------------------------------------------------------------------------
# The spiral descent (shared by both templates)
# ---------------------------------------------------------------------------------------------
# The ring of 16 cells around a 3 x 3 newel, clockwise from the north-west corner, as offsets from the
# shaft's centre column. Steps go down counter-clockwise from ring index 2 (the north side, nearest
# the stump's door) so that the last step (index 15) lies on the west side, toward the antechamber.
RING = [(-2, -2), (-1, -2), (0, -2), (1, -2), (2, -2), (2, -1), (2, 0), (2, 1), (2, 2), (1, 2), (0, 2), (-1, 2),
        (-2, 2), (-2, 1), (-2, 0), (-2, -1)]
TOP_INDEX = 2
TOP_FEET = SURFACE_BURIED          # surface-local feet height of the first step (the stump floor)
STEPS = 20                         # feet heights 9 .. -10 surface-local = 27 .. 8 hall-local
HEADROOM = 3


def descent_cells():
    """Cells of the descent in surface-local y: {(dx, y, dz): block}. Unlisted ring cells are solid."""
    cells = {}
    for k in range(STEPS):
        dx, dz = RING[(TOP_INDEX - k) % len(RING)]
        feet = TOP_FEET - k
        for h in range(HEADROOM):
            cells[(dx, feet + h, dz)] = AIR
        if k % 4 == 1:
            cells[(dx, feet + 2, dz)] = LIGHT7
        if k == 0:
            cells[(dx, feet - 1, dz)] = "minecraft:stone_bricks"  # the landing at the stump's floor
        elif k == STEPS - 1:
            cells[(dx, feet - 1, dz)] = "minecraft:polished_deepslate"  # the antechamber's level
        else:
            # The step climbs toward the previous ring cell (the one above it).
            ux, uz = RING[(TOP_INDEX - k + 1) % len(RING)]
            facing = direction(ux - dx, uz - dz)
            cells[(dx, feet - 1, dz)] = stairs("minecraft:deepslate_brick_stairs", facing)
    return cells


def direction(dx, dz):
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def last_step():
    """(dx, feet surface-local, dz) of the bottom step."""
    dx, dz = RING[(TOP_INDEX - (STEPS - 1)) % len(RING)]
    return dx, TOP_FEET - (STEPS - 1), dz


def build_shaft(t, centre, y_offset, y_range, wall_fn, newel_fn):
    """The 7 x 7 shaft in template t: walls, newel, ring rock and the descent cells within y_range."""
    cx, cz = centre
    cells = descent_cells()
    for y in y_range:
        sy = y - y_offset
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                x, z = cx + dx, cz + dz
                if abs(dx) == 3 or abs(dz) == 3:
                    put(t, x, y, z, wall_fn(x, y, z))
                elif abs(dx) <= 1 and abs(dz) <= 1:
                    put(t, x, y, z, newel_fn(x, y, z))
                else:
                    put(t, x, y, z, cells.get((dx, sy, dz), wall_fn(x, y, z)))


# ---------------------------------------------------------------------------------------------
# Surface: the watchtower stump over the descent
# ---------------------------------------------------------------------------------------------
def build_surface():
    t = Template(*SURFACE_SIZE)
    seed = 811
    stone = ruin_stone(seed)
    ground = SURFACE_BURIED - 1  # layer 8: the ground under the stand
    cx, cz = SHAFT_SURFACE
    anchors = {}

    def in_stump(x, z, r):
        dx, dz = abs(x - cx), abs(z - cz)
        return dx <= r and dz <= r and dx + dz <= r + r // 2

    # The shaft under the stump (layers 0 to the ground layer).
    build_shaft(t, SHAFT_SURFACE, 0, range(0, ground + 1), stone, lambda x, y, z: "minecraft:chiseled_stone_bricks"
                if y % 4 == 0 else "minecraft:stone_bricks")

    # The stump's floor (the ground layer) around the shaft opening.
    descent = descent_cells()
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            if not in_stump(x, z, 4):
                continue
            if (x - cx, ground, z - cz) in descent:
                continue  # the landing and the opening over the first steps
            put(t, x, ground, z, stone(x, ground, z) if hash01(seed, x, z) > 0.2 else "minecraft:cobblestone")

    # Walls of the stump: an octagonal ring, broken to uneven heights, the door on the north side.
    for x in range(cx - 5, cx + 6):
        for z in range(cz - 5, cz + 6):
            if not in_stump(x, z, 5) or in_stump(x, z, 4):
                continue
            put(t, x, ground, z, stone(x, ground, z))
            height = 2 + int(value_noise(x, 0, z, 3.0, seed) * 6)
            if z < cz:
                height = min(height, 4)  # the side facing the player is the most ruined
            door = z == cz - 5 and abs(x - cx) <= 1
            for y in range(ground + 1, min(ground + 1 + height, SURFACE_SIZE[1])):
                if door and y <= ground + 3:
                    put(t, x, y, z, AIR)
                else:
                    put(t, x, y, z, stone(x, y, z))
            top = ground + height
            if top + 1 < SURFACE_SIZE[1] and not door and hash01(seed + 3, x, z) < 0.35:
                put(t, x, top + 1, z, "minecraft:moss_carpet")
    # Lintel stones over the door, one fallen.
    put(t, cx - 1, ground + 4, cz - 5, stone(cx - 1, ground + 4, cz - 5))
    put(t, cx + 1, ground + 4, cz - 5, stone(cx + 1, ground + 4, cz - 5))

    # Air inside the stump, so terrain inside it is cleared; the newel rises as a broken column.
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            if not in_stump(x, z, 4):
                continue
            for y in range(ground + 1, SURFACE_SIZE[1]):
                if abs(x - cx) <= 1 and abs(z - cz) <= 1 and y <= ground + 2 + int(hash01(seed + 5, x, z) * 3):
                    put(t, x, y, z, "minecraft:stone_bricks" if (x, z) != (cx, cz) else "minecraft:chiseled_stone_bricks")
                elif t.get(x, y, z) is None:
                    put(t, x, y, z, AIR)

    # The descent's cells inside the stump (headroom and light over the first steps).
    for (dx, y, dz), block in descent.items():
        if ground < y < SURFACE_SIZE[1]:
            put(t, cx + dx, y, cz + dz, block)

    # Fallen stones and a paving trace around the stump (most of the ground is left as it is).
    for x in range(SURFACE_SIZE[0]):
        for z in range(SURFACE_SIZE[2]):
            if in_stump(x, z, 5):
                continue
            n = value_noise(x, 0, z, 2.5, seed + 7)
            r = hash01(seed + 9, x, z)
            if z < cz - 5 and abs(x - cx) <= 1:
                put(t, x, ground, z, "minecraft:gravel" if r < 0.3 else "minecraft:cobblestone")  # the old path to the door
            elif n > 0.7 and r < 0.5:
                put(t, x, ground + 1, z, "minecraft:mossy_cobblestone" if r < 0.25 else "minecraft:cracked_stone_bricks")
                if n > 0.8 and r < 0.15:
                    put(t, x, ground + 2, z, slab("minecraft:stone_brick_slab"))
            elif r > 0.94:
                put(t, x, ground + 1, z, slab("minecraft:mossy_stone_brick_slab"))

    top_dx, top_dz = RING[TOP_INDEX]
    anchors["DESCENT_TOP"] = [(cx + top_dx, TOP_FEET, cz + top_dz)]
    return t, anchors


# ---------------------------------------------------------------------------------------------
# Hall
# ---------------------------------------------------------------------------------------------
def sunken_floor(x, z):
    """The floor block height of the hall at (x, z): the sunken floor or the main floor."""
    return SUNK_Y if 8 <= x <= 22 and 6 <= z <= 19 else FLOOR_Y


def build_hall():
    t = Template(*HALL_SIZE)
    seed = 823
    sx, sy, sz = HALL_SIZE
    rock = natural_rock(seed)
    wall = masonry(seed + 1)
    floor = floor_stone(seed + 2)
    anchors = {}
    room = set()  # air cells of the rooms, for the masonry lining

    def carve(x0, y0, z0, x1, y1, z1):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    room.add((x, y, z))

    # 1. Rooms (air), then everything else is rock; walls next to a room become masonry.
    carve(1, FLOOR_Y + 1, 1, 29, CEIL_Y - 1, 19)               # the hall, galleries and balcony volume
    carve(11, GAL_Y + 1, 21, 19, GAL_Y + 5, 29)                # antechamber (5 high)
    carve(14, GAL_Y + 1, 20, 16, GAL_Y + 3, 20)                # the threshold doorway
    carve(11, SUNK_Y + 1, 1, 19, DAIS_Y - 1, 4)                # the hoard vault inside the dais
    carve(14, SUNK_Y + 1, 5, 16, SUNK_Y + 2, 5)                # the vault door
    carve(20, GAL_Y + 1, 24, 21, GAL_Y + 3, 24)                # the descent's way into the antechamber
    for (x, y, z) in [(x, y, z) for x in range(1, 3) for y in range(GAL_Y + 1, GAL_Y + 4) for z in (4, 5, 6, 10, 11, 12)]:
        room.add((x, y, z))                                    # west sentinel alcoves
        room.add((30 - x, y, z))                               # east sentinel alcoves

    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                if (x, y, z) in room:
                    put(t, x, y, z, AIR)
                else:
                    near = any((x + dx, y + dy, z + dz) in room for dx, dy, dz in
                               ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))
                    put(t, x, y, z, wall(x, y, z) if near else rock(x, y, z))

    # 2. Floors.
    for x in range(1, 30):
        for z in range(1, 20):
            put(t, x, FLOOR_Y, z, floor(x, FLOOR_Y, z))
    # Sunken floor 15 x 14 (one lower), a step of slabs along its rim.
    for x in range(8, 23):
        for z in range(6, 20):
            put(t, x, FLOOR_Y, z, AIR)
            put(t, x, SUNK_Y, z, "minecraft:tuff_bricks" if (x + z) % 7 else "minecraft:tuff")
            if x in (8, 22):
                put(t, x, SUNK_Y + 1, z, slab("minecraft:polished_tuff_slab"))

    # 3. The raised platform: wings and dais (+3), with the vault inside the dais.
    for x in range(6, 25):
        for z in range(1, 6):
            for y in range(FLOOR_Y + 1, DAIS_Y):
                if (x, y, z) not in room or not (11 <= x <= 19 and z <= 5):
                    put(t, x, y, z, wall(x, y, z))
            top = "minecraft:polished_deepslate" if 10 <= x <= 20 else "minecraft:deepslate_tiles"
            if 10 <= x <= 20 and (z == 5 or x in (10, 20)):
                top = "minecraft:polished_blackstone_bricks"
            put(t, x, DAIS_Y, z, top)
    # The dais front (faces the hall): chiselled tuff with a bronze band.
    for x in range(10, 21):
        if not 14 <= x <= 16:
            put(t, x, FLOOR_Y + 1, 5, "minecraft:chiseled_tuff_bricks")
            put(t, x, FLOOR_Y + 2, 5, "minecraft:waxed_weathered_cut_copper")
    # Hoard vault: interior x 11-19, z 1-4, y 2-4 (floor y 1), the vault seal in the dais front.
    for x in range(11, 20):
        for z in range(1, 5):
            put(t, x, SUNK_Y, z, "minecraft:polished_deepslate")
            for y in range(SUNK_Y + 1, DAIS_Y):
                put(t, x, y, z, AIR)
    for x in range(14, 17):
        put(t, x, SUNK_Y, 5, "minecraft:polished_deepslate")
        for y in range(SUNK_Y + 1, SUNK_Y + 3):
            put(t, x, y, 5, VAULT_SEAL)
            anchors.setdefault("VAULT_SEAL", []).append((x, y, 5))
        put(t, x, SUNK_Y + 3, 5, "minecraft:waxed_weathered_cut_copper")
    put(t, 15, SUNK_Y + 1, 2, PLINTH)
    anchors["PLINTH"] = [(15, SUNK_Y + 1, 2)]
    for x in (11, 12, 13, 17, 18, 19):
        for y in range(SUNK_Y + 1, DAIS_Y):
            put(t, x, y, 1, empty_shelf("south"))
    for (x, z) in ((11, 4), (19, 4), (11, 3), (19, 3)):
        put(t, x, SUNK_Y + 1, z, ("minecraft:decorated_pot", {"cracked": "true" if x == 19 else "false", "facing": "south", "waterlogged": "false"}))
    put(t, 13, SUNK_Y + 1, 3, ("minecraft:candle", {"candles": "3", "lit": "false", "waterlogged": "false"}))
    put(t, 15, DAIS_Y - 1, 3, LIGHT7)

    # On the dais: the lectern with its book, shelves behind, Vasch's spot in front.
    lectern_nbt = {"id": "minecraft:lectern", "Book": {"id": "minecraft:writable_book", "count": Int(1)}, "Page": Int(0)}
    put(t, 15, DAIS_Y + 1, 1, ("minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"}), lectern_nbt)
    anchors["LECTERN"] = [(15, DAIS_Y + 1, 2)]
    anchors["DAIS"] = [(15, DAIS_Y + 1, 3)]
    for x in (10, 11, 12, 13, 17, 18, 19, 20):
        for y in range(DAIS_Y + 1, DAIS_Y + 4):
            if x in (13, 17) and y == DAIS_Y + 3:
                continue
            put(t, x, y, 1, "minecraft:bookshelf" if hash01(seed + 4, x, y) > 0.3 else empty_shelf("south"))
    put(t, 13, DAIS_Y + 3, 1, ("minecraft:candle", {"candles": "2", "lit": "false", "waterlogged": "false"}))
    put(t, 17, DAIS_Y + 3, 1, ("minecraft:candle", {"candles": "4", "lit": "false", "waterlogged": "false"}))
    # Pylons on the wings, two air cells above each.
    put(t, 7, DAIS_Y + 1, 3, PYLON)
    put(t, 23, DAIS_Y + 1, 3, PYLON)
    anchors["PYLON_1"] = [(7, DAIS_Y + 1, 3)]
    anchors["PYLON_2"] = [(23, DAIS_Y + 1, 3)]
    # Wing stairs (x 6-7 and 23-24) climb north from the hall floor.
    for xs in ((6, 7), (23, 24)):
        for x in xs:
            for i, z in enumerate((8, 7, 6)):
                y = FLOOR_Y + 1 + i
                for fill_y in range(FLOOR_Y + 1, y):
                    put(t, x, fill_y, z, wall(x, fill_y, z))
                put(t, x, y, z, stairs("minecraft:deepslate_brick_stairs", "north"))

    # 4. Galleries (+5), the south balcony and the undercrofts below them.
    for z in range(1, 20):
        for x in list(range(1, 6)) + list(range(25, 30)):
            put(t, x, GAL_Y, z, "minecraft:polished_deepslate" if x in (5, 25) else "minecraft:deepslate_tiles")
    for x in range(1, 30):
        for z in range(17, 20):
            put(t, x, GAL_Y, z, "minecraft:polished_deepslate" if z == 17 else "minecraft:deepslate_tiles")
    # Side walls above the galleries (the alcoves are cut into them).
    for z in range(1, 20):
        for y in range(GAL_Y + 1, CEIL_Y):
            for x in (1, 2):
                if not (y <= GAL_Y + 3 and z in (4, 5, 6, 10, 11, 12)):
                    put(t, x, y, z, wall(x, y, z))
                    put(t, 30 - x, y, z, wall(30 - x, y, z))
    for z in (4, 5, 6, 10, 11, 12):
        for x in (1, 29):
            put(t, x, GAL_Y, z, "minecraft:polished_deepslate")
    anchors["SENTINEL_1"] = [(2, GAL_Y + 1, 5)]
    anchors["SENTINEL_2"] = [(2, GAL_Y + 1, 11)]
    anchors["SENTINEL_3"] = [(28, GAL_Y + 1, 5)]
    anchors["SENTINEL_4"] = [(28, GAL_Y + 1, 11)]
    # Undercroft pillars carrying the galleries and the balcony.
    for (x, z) in [(5, 4), (5, 9), (5, 14), (25, 4), (25, 9), (25, 14), (9, 17), (15, 17), (21, 17)]:
        for y in range(sunken_floor(x, z) + 1, GAL_Y):
            put(t, x, y, z, "minecraft:polished_deepslate" if y != GAL_Y - 1 else "minecraft:chiseled_deepslate")
    # Undercroft shelves against the outer walls: the Archive's emptied stacks.
    for z in range(1, 17):
        for y in range(FLOOR_Y + 1, FLOOR_Y + 3):
            put(t, 1, y, z, "minecraft:bookshelf" if hash01(seed + 6, z, y) > 0.45 else empty_shelf("east"))
            put(t, 29, y, z, "minecraft:bookshelf" if hash01(seed + 7, z, y) > 0.45 else empty_shelf("west"))
    # Gallery stairs: x 6-7 (west) and 23-24 (east) climb south from z 12 to the balcony at z 17.
    for xs in ((6, 7), (23, 24)):
        for x in xs:
            for i, z in enumerate(range(12, 17)):
                y = FLOOR_Y + 1 + i
                for fill_y in range(FLOOR_Y + 1, y):
                    put(t, x, fill_y, z, wall(x, fill_y, z))
                put(t, x, y, z, stairs("minecraft:deepslate_brick_stairs", "south"))
    anchors["GALLERY_W"] = [(6, GAL_Y + 1, 17)]
    anchors["GALLERY_E"] = [(24, GAL_Y + 1, 17)]
    # Pylons 3 and 4 at the galleries' south ends.
    put(t, 4, GAL_Y + 1, 19, PYLON)
    put(t, 26, GAL_Y + 1, 19, PYLON)
    anchors["PYLON_3"] = [(4, GAL_Y + 1, 19)]
    anchors["PYLON_4"] = [(26, GAL_Y + 1, 19)]
    # Merlons along the galleries' inner edges (decor; the edge stays open for jumps).
    for z in range(2, 16, 3):
        put(t, 5, GAL_Y + 1, z, slab("minecraft:deepslate_brick_slab"))
        put(t, 25, GAL_Y + 1, z, slab("minecraft:deepslate_brick_slab"))

    # 5. Broken columns (2 x 2, at least 4 high from the sunken floor) and debris piles.
    columns = [(9, 9), (14, 9), (20, 9), (9, 15), (15, 15), (20, 15)]
    for i, (x0, z0) in enumerate(columns, start=1):
        anchors[f"COLUMN_{i}"] = [(x0, SUNK_Y + 1, z0)]
        tops = {}
        for dx in (0, 1):
            for dz in (0, 1):
                x, z = x0 + dx, z0 + dz
                # At least 4 high; the broken top leaves one or two stones a block higher.
                top = SUNK_Y + 4 + (1 if hash01(seed + 10, i, dx, dz) > 0.55 else 0)
                tops[(x, z)] = top
                for y in range(SUNK_Y + 1, top + 1):
                    put(t, x, y, z, "minecraft:polished_tuff" if y != SUNK_Y + 2 else "minecraft:chiseled_tuff")
        # A crystal sconce on the column's top, lit by a light block over the opposite stone.
        put(t, x0, tops[(x0, z0)] + 1, z0, cluster("up"))
        put(t, x0 + 1, tops[(x0 + 1, z0 + 1)] + 2, z0 + 1, LIGHT7)
    debris = [(11, 7), (19, 7), (11, 13), (19, 13)]
    for i, (x0, z0) in enumerate(debris, start=1):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if abs(dx) + abs(dz) == 2 and hash01(seed + 11, i, dx, dz) < 0.5:
                    continue
                put(t, x0 + dx, SUNK_Y + 1, z0 + dz, "minecraft:cobbled_deepslate" if (dx + dz) % 2 else "minecraft:gravel")
        put(t, x0, SUNK_Y + 2, z0, "minecraft:cracked_deepslate_bricks")
        put(t, x0, SUNK_Y + 3, z0, AIR)
        anchors[f"DEBRIS_{i}"] = [(x0, SUNK_Y + 2, z0)]

    # 6. Threshold wall and the antechamber (+5).
    for x in range(14, 17):
        for y in range(GAL_Y + 1, GAL_Y + 4):
            put(t, x, y, 20, DOOR_SEAL)
            anchors.setdefault("DOOR_SEAL", []).append((x, y, 20))
        put(t, x, GAL_Y, 20, "minecraft:polished_deepslate")
    put(t, 15, GAL_Y + 4, 20, "minecraft:chiseled_deepslate")
    anchors["THRESHOLD"] = [(15, GAL_Y + 1, 20)]
    for x in range(11, 20):
        for z in range(21, 30):
            put(t, x, GAL_Y, z, floor(x, GAL_Y, z))
    for x in range(20, 22):
        put(t, x, GAL_Y, 24, "minecraft:polished_deepslate")
    put(t, 12, GAL_Y + 1, 27, ("minecraft:brown_bed", {"facing": "south", "occupied": "false", "part": "foot"}))
    put(t, 12, GAL_Y + 1, 28, ("minecraft:brown_bed", {"facing": "south", "occupied": "false", "part": "head"}))
    anchors["BED"] = [(12, GAL_Y + 1, 27)]
    anchors["HEART"] = [(15, GAL_Y + 1, 28)]
    put(t, 15, GAL_Y + 1, 28, AIR)
    for x in (14, 16):
        put(t, x, GAL_Y, 28, "minecraft:chiseled_tuff_bricks")
    put(t, 15, GAL_Y, 28, "minecraft:polished_blackstone_bricks")
    # A rack of emptied holocron cradles, a cauldron, a supply barrel.
    for x in (17, 18):
        put(t, x, GAL_Y + 1, 29, empty_shelf("north"))
        put(t, x, GAL_Y + 2, 29, empty_shelf("north"))
    put(t, 19, GAL_Y + 1, 29, "minecraft:cauldron")
    put(t, 11, GAL_Y + 1, 29, ("minecraft:barrel", {"facing": "up", "open": "false"}))
    put(t, 11, GAL_Y + 1, 22, ("minecraft:decorated_pot", {"cracked": "true", "facing": "east", "waterlogged": "false"}))

    # 7. The descent shaft: x 21-27, z 22-28, from the antechamber level to the top.
    build_shaft(t, SHAFT_HALL, HALL_DY, range(GAL_Y, sy), wall,
                lambda x, y, z: "minecraft:chiseled_deepslate" if y % 4 == 0 else "minecraft:polished_deepslate")
    bx, bfeet, bz = last_step()
    exit_x, exit_z = SHAFT_HALL[0] + bx, SHAFT_HALL[1] + bz
    if exit_z != 24 or exit_x != 22 or bfeet + HALL_DY != GAL_Y + 1:
        raise SystemExit(f"descent bottom at {(exit_x, bfeet + HALL_DY, exit_z)}, expected (22, {GAL_Y + 1}, 24)")
    for x in range(20, 22):
        for y in range(GAL_Y + 1, GAL_Y + 4):
            put(t, x, y, 24, AIR)
    put(t, 20, GAL_Y + 3, 24, LIGHT7)

    # 8. Crystal sconces: an amethyst cluster on the wall, a light block (level 7) beside it.
    sconces = [
        # (cluster cell, facing, light cell)
        ((3, 11, 2), "east", (4, 10, 2)), ((3, 11, 5), "east", (4, 10, 5)), ((3, 11, 8), "east", (4, 10, 8)),
        ((3, 11, 11), "east", (4, 10, 11)), ((3, 11, 14), "east", (4, 10, 14)), ((3, 11, 18), "east", (4, 10, 17)),
        ((27, 11, 2), "west", (26, 10, 2)), ((27, 11, 5), "west", (26, 10, 5)), ((27, 11, 8), "west", (26, 10, 8)),
        ((27, 11, 11), "west", (26, 10, 11)), ((27, 11, 14), "west", (26, 10, 14)), ((27, 11, 18), "west", (26, 10, 17)),
        ((6, 8, 1), "south", (6, 8, 2)), ((24, 8, 1), "south", (24, 8, 2)),
        ((1, 5, 2), "east", (2, 5, 2)), ((29, 5, 2), "west", (28, 5, 2)),
        ((3, 5, 19), "north", (3, 5, 18)), ((27, 5, 19), "north", (27, 5, 18)),
        ((11, 10, 1), "south", (11, 11, 1)), ((19, 10, 1), "south", (19, 11, 1)),
        ((8, 11, 19), "north", (8, 12, 18)), ((22, 11, 19), "north", (22, 12, 18)),
        ((1, 5, 7), "east", (2, 5, 7)), ((1, 5, 12), "east", (2, 5, 12)), ((29, 5, 7), "west", (28, 5, 7)),
        ((29, 5, 12), "west", (28, 5, 12)), ((12, 5, 19), "north", (12, 5, 18)), ((18, 5, 19), "north", (18, 5, 18)),
        ((11, 10, 23), "east", (12, 10, 23)), ((19, 10, 26), "west", (18, 10, 26)),
    ]
    for (c, facing, light) in sconces:
        if t.get(*c) != AIR or t.get(*light) != AIR:
            raise SystemExit(f"sconce at {c} or its light {light} is not in air")
        put(t, *c, cluster(facing))
        put(t, *light, LIGHT7)
    # Floating light over the open floor (no visible source: the columns' crystals read as the source).
    for (x, z) in ((12, 11), (18, 11), (15, 6), (15, 17), (6, 10), (24, 10)):
        y = SUNK_Y + 5 if 8 <= x <= 22 and 6 <= z <= 19 else FLOOR_Y + 4
        if t.get(x, y, z) == AIR:
            put(t, x, y, z, LIGHT7)
    put(t, 13, GAL_Y + 3, 25, LIGHT7)
    put(t, 17, GAL_Y + 3, 22, LIGHT7)
    fill_light(t)

    # 9. Atmosphere: cobwebs in high corners, hanging chains from the ceiling.
    for (x, y, z) in ((3, 15, 1), (27, 15, 1), (3, 15, 19), (27, 15, 19), (11, 12, 29), (19, 12, 21)):
        if t.get(x, y, z) == AIR:
            put(t, x, y, z, "minecraft:cobweb")
    for (x, z, length) in ((12, 8, 3), (18, 12, 2), (15, 14, 4), (10, 16, 2)):
        for y in range(CEIL_Y - length, CEIL_Y):
            put(t, x, y, z, ("minecraft:iron_chain", {"axis": "y", "waterlogged": "false"}))

    return t, anchors


# ---------------------------------------------------------------------------------------------
# Checks (the anchor guarantees of the W5 plan, 4.9)
# ---------------------------------------------------------------------------------------------
def name_of(t, p):
    return t.get(*p) if t.inside(*p) else None


def is_air(t, p):
    """Passable: air, light, cobweb, or an open seal."""
    n = name_of(t, p)
    if n == "oldrepublic:reliquary_seal":
        return dict(t.cells[p][1]).get("open") == "true"
    return n in (AIR, "minecraft:light", "minecraft:cobweb")


def is_solid(t, p):
    n = name_of(t, p)
    return n is not None and n not in NON_SOLID and n != "oldrepublic:reliquary_seal"


def standable(t, p):
    """Feet cell p: air for two cells, something to stand on below."""
    below = (p[0], p[1] - 1, p[2])
    return is_air(t, p) and is_air(t, (p[0], p[1] + 1, p[2])) and (is_solid(t, below) or name_of(t, below) == "oldrepublic:reliquary_seal")


def check_hall(t, a):
    def one(name):
        return a[name][0]

    def fail(msg):
        raise SystemExit("reliquary_hall: " + msg)

    for name, cells in a.items():
        for c in cells:
            if not t.inside(*c):
                fail(f"{name} at {c} is outside the template")
    heart = one("HEART")
    if name_of(t, heart) != AIR or not is_solid(t, (heart[0], heart[1] - 1, heart[2])):
        fail("HEART must be air over a solid floor")
    bed = one("BED")
    if name_of(t, bed) != "minecraft:brown_bed" or name_of(t, (bed[0], bed[1], bed[2] + 1)) != "minecraft:brown_bed":
        fail("BED must be a bed's foot with its head south of it")
    for c in a["DOOR_SEAL"]:
        if name_of(t, c) != "oldrepublic:reliquary_seal" or dict(t.cells[c][1]).get("open") != "true":
            fail(f"DOOR_SEAL {c} is not an open door seal")
    for c in a["VAULT_SEAL"]:
        if name_of(t, c) != "oldrepublic:reliquary_seal" or dict(t.cells[c][1]).get("vault") != "true":
            fail(f"VAULT_SEAL {c} is not a vault seal")
    lectern = one("LECTERN")
    if not all(is_air(t, (lectern[0], lectern[1] + h, lectern[2])) for h in range(3)):
        fail("LECTERN needs 3 air cells")
    if not any(name_of(t, (lectern[0] + dx, lectern[1], lectern[2] + dz)) == "minecraft:lectern" for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        fail("no lectern beside LECTERN")
    dais = one("DAIS")
    if not standable(t, dais):
        fail("DAIS is not standable")
    for i in range(1, 5):
        p = one(f"PYLON_{i}")
        if name_of(t, p) != "oldrepublic:resonance_pylon" or not all(is_air(t, (p[0], p[1] + h, p[2])) for h in (1, 2)):
            fail(f"PYLON_{i} needs the pylon block with 2 air cells above")
        if i <= 2 and math.dist(p, dais) > 9:
            fail(f"PYLON_{i} is more than 9 blocks from the dais centre")
        if i >= 3 and p[1] != GAL_Y + 1:
            fail(f"PYLON_{i} is not on the gallery level")
    for name in ("GALLERY_W", "GALLERY_E"):
        if not standable(t, one(name)):
            fail(f"{name} is not standable")
    for i in range(1, 5):
        x, y, z = one(f"SENTINEL_{i}")
        # A sentinel is 1.1 wide and 2.3 high, centred on the cell: every cell its box touches is air.
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for h in range(3):
                    if not is_air(t, (x + dx, y + h, z + dz)):
                        fail(f"SENTINEL_{i} does not fit: {(x + dx, y + h, z + dz)} is {name_of(t, (x + dx, y + h, z + dz))}")
        if not is_solid(t, (x, y - 1, z)):
            fail(f"SENTINEL_{i} has no floor")
    for i in range(1, 7):
        x, y, z = one(f"COLUMN_{i}")
        for dx in (0, 1):
            for dz in (0, 1):
                for h in range(4):
                    if not is_solid(t, (x + dx, y + h, z + dz)):
                        fail(f"COLUMN_{i} is not 2 x 2 x 4 solid")
    for i in range(1, 5):
        x, y, z = one(f"DEBRIS_{i}")
        if not is_solid(t, (x, y, z)) or not is_air(t, (x, y + 1, z)) or not is_air(t, (x, y + 2, z)):
            fail(f"DEBRIS_{i} must be a solid top with air above")
    if name_of(t, one("PLINTH")) != "oldrepublic:hoard_plinth":
        fail("PLINTH is not the plinth")

    # The threshold plane: everything of the antechamber lies beyond it, the hall on the dais side.
    thr = one("THRESHOLD")
    axis = (0, 1) if thr[2] > dais[2] else (0, -1)
    if thr[0] != dais[0]:
        fail("DAIS and THRESHOLD must share a column line (axis along z)")
    for h in range(1, 4):
        p = (thr[0], thr[1], thr[2] + axis[1] * h)
        if not standable(t, p):
            fail(f"THRESHOLD + {h} is not walkable antechamber floor")
    for (x, y, z) in [(x, y, z) for x in range(11, 20) for y in range(GAL_Y + 1, GAL_Y + 6) for z in range(21, 30)]:
        if (z - thr[2]) * axis[1] <= 0:
            fail("antechamber cell on the dais side of the threshold")
    if (heart[2] - thr[2]) * axis[1] <= 0 or (bed[2] - thr[2]) * axis[1] <= 0:
        fail("the heart and the bed must lie beyond the threshold")

    # Walkability: the antechamber reaches the threshold, the balcony, both gallery stairs, the floor,
    # the wing stairs and the dais top; the vault door is reachable from the floor.
    reach = walk(t, (15, GAL_Y + 1, 23), lambda p: True)
    for name in ("GALLERY_W", "GALLERY_E", "DAIS", "LECTERN"):
        if one(name) not in reach:
            fail(f"{name} cannot be walked to from the antechamber")
    for p in ((12, SUNK_Y + 1, 11), (15, SUNK_Y + 1, 18), (3, FLOOR_Y + 1, 10), (27, FLOOR_Y + 1, 10)):
        if p not in reach:
            fail(f"hall floor {p} cannot be walked to")
    for i in (3, 4):
        x, y, z = one(f"PYLON_{i}")
        if not any((x + dx, y, z + dz) in reach for dx, dz in ((1, 0), (-1, 0), (0, -1))):
            fail(f"PYLON_{i} cannot be walked up to")
    for i in (1, 2):
        x, y, z = one(f"PYLON_{i}")
        if not any((x + dx, y, z + dz) in reach for dx, dz in ((1, 0), (-1, 0), (0, 1))):
            fail(f"PYLON_{i} cannot be walked up to")
    if (15, SUNK_Y + 1, 6) not in reach:
        fail("the vault door's approach cannot be walked to")
    return reach


def walk(t, start, ok):
    """Breadth-first walk over feet cells: level moves, steps down of 1 or 2, steps up onto stairs or slabs."""
    seen = {start}
    queue = deque([start])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1, -2):
                p = (x + dx, y + dy, z + dz)
                if p in seen or not t.inside(*p) or not standable(t, p) or not ok(p):
                    continue
                if dy == 1:
                    below = name_of(t, (p[0], p[1] - 1, p[2])) or ""
                    if not below.endswith(STEPPED) and not (name_of(t, (x, y - 1, z)) or "").endswith(STEPPED):
                        continue
                    if not is_air(t, (x, y + 2, z)):
                        continue
                if dy < 0 and not all(is_air(t, (p[0], p[1] + h, p[2])) for h in range(2, 2 - dy)):
                    continue
                seen.add(p)
                queue.append(p)
                break
    return seen


def check_descent(surface, s_anchors, hall):
    """The spiral runs unbroken from DESCENT_TOP through both templates into the antechamber."""
    cells = descent_cells()
    cx, cz = SHAFT_SURFACE
    hx, hz = SHAFT_HALL
    for (dx, y, dz), block in cells.items():
        expected = block[0] if isinstance(block, tuple) else block
        if 0 <= y < SURFACE_SIZE[1]:
            got = surface.get(cx + dx, y, cz + dz)
        elif 0 <= y + HALL_DY < HALL_SIZE[1]:
            got = hall.get(hx + dx, y + HALL_DY, hz + dz)
        else:
            continue
        if got != expected:
            raise SystemExit(f"descent cell {(dx, y, dz)} is {got}, expected {expected}")
    top = s_anchors["DESCENT_TOP"][0]
    if surface.get(*top) != AIR:
        raise SystemExit("DESCENT_TOP is not air")


def fill_light(t):
    """Hidden fill light: while a standing cell outside the shaft is unlit, a light block (level 7)
    goes two cells above the unlit cell that has the most unlit cells around it (no mob spawns in
    the dark corners; nothing visible is added)."""
    def in_shaft(p):
        return abs(p[0] - SHAFT_HALL[0]) <= 3 and abs(p[2] - SHAFT_HALL[1]) <= 3

    while True:
        level = light_levels(t)
        dark = sorted(p for p in t.cells if standable(t, p) and level.get(p, 0) == 0 and not in_shaft(p))
        if not dark:
            return
        best = max(dark, key=lambda c: (sum(1 for d in dark if abs(d[0] - c[0]) + abs(d[1] - c[1]) + abs(d[2] - c[2]) <= 4), -dark.index(c)))
        for h in (2, 1):
            cell = (best[0], best[1] + h, best[2])
            if t.get(*cell) == AIR:
                put(t, *cell, LIGHT7)
                break
        else:
            raise SystemExit(f"no room for fill light over {best}")


def light_levels(t):
    """Block light of every open cell, from light blocks (their level) and amethyst clusters (5)."""
    level = {}
    queue = deque()
    for p, (name, props, _) in t.cells.items():
        value = 0
        if name == "minecraft:light":
            value = int(dict(props)["level"])
        elif name == "minecraft:amethyst_cluster":
            value = 5
        if value:
            level[p] = value
            queue.append(p)
    while queue:
        p = queue.popleft()
        v = level[p] - 1
        if v <= 0:
            continue
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if not t.inside(*q) or is_solid(t, q):
                continue
            if level.get(q, 0) < v:
                level[q] = v
                queue.append(q)
    return level


def light_report(t):
    """(standing cells, unlit standing cells, brightest light) of a template."""
    level = light_levels(t)
    floor_cells = [p for p in t.cells if standable(t, p)]
    dark = [p for p in floor_cells if level.get(p, 0) == 0]
    return len(floor_cells), len(dark), max(level.values())


# ---------------------------------------------------------------------------------------------
def sidecar(path, anchors):
    text = "{\n" + ",\n".join(
        f'  "{anchor}": [' + ", ".join(f"[{x}, {y}, {z}]" for (x, y, z) in cells) + "]"
        for anchor, cells in anchors.items()) + "\n}\n"
    json.loads(text)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write(text)


def main():
    surface, s_anchors = build_surface()
    hall, h_anchors = build_hall()
    check_hall(hall, h_anchors)
    check_descent(surface, s_anchors, hall)
    cells, dark, brightest = light_report(hall)
    if brightest > 7:
        raise SystemExit(f"hall light {brightest} is above 7")
    if dark > 0:
        raise SystemExit(f"{dark} of {cells} standing cells are unlit")
    order = ["BED", "HEART", "THRESHOLD", "DOOR_SEAL", "LECTERN", "DAIS", "PYLON_1", "PYLON_2", "PYLON_3", "PYLON_4",
             "GALLERY_W", "GALLERY_E", "SENTINEL_1", "SENTINEL_2", "SENTINEL_3", "SENTINEL_4"] + \
        [f"COLUMN_{i}" for i in range(1, 7)] + [f"DEBRIS_{i}" for i in range(1, 5)] + ["PLINTH", "VAULT_SEAL"]
    h_anchors = {k: h_anchors[k] for k in order}
    for name, t, anchors in (("reliquary_surface", surface, s_anchors), ("reliquary_hall", hall, h_anchors)):
        n, p = t.save(os.path.join(STRUCTURES, name + ".nbt"))
        sidecar(os.path.join(SIDECARS, name + ".json"), anchors)
        print(f"{name}: size {t.size}, {n} blocks, {p} palette entries, anchors {', '.join(anchors)}")
    print(f"reliquary_hall light: {cells - dark} of {cells} standing cells lit, brightest {brightest}")


if __name__ == "__main__":
    main()
