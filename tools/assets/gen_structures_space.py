#!/usr/bin/env python3
"""Generates the Silent Spindle (Milestone 5, W2; spec MILESTONE_5.md 5.7): the derelict Republic listening
station in the home system's orbit, placed once per server by SilentSpindle through RuntimeSites.stamp (no
terrain: it hangs in the void), and its anchor sidecar.

Deterministic: the same script always writes byte-identical files (the NBT writer and Template class of
gen_structures.py, the Site helpers of gen_structures_m3.py, fixed seeds). Writes under
src/main/resources/data/oldrepublic/:

  structure/station/spindle.nbt          64 x 40 x 48, nothing buried
  site_anchors/station/spindle.json      SPINDLE_DECK (the landing deck's centre, a feet cell),
                                         SPINDLE_POST_1-4 (the security droids' posts, feet cells),
                                         SPINDLE_LOG (the log terminal, an observer),
                                         SPINDLE_VAULT (the vault chest, no loot table: SilentSpindle rolls
                                         chests/spindle_vault per player per day when it is used)

The station: an octagonal spine 58 blocks long (x 3..60), its decks one level high (floor y 16, feet y 17,
ceiling y 24), split by bulkheads into, west to east:
  * the reactor room (x 4..12): the dead core in an iron cage, half its bulbs still lit, conduits along the
    ceiling, warning paint on the floor;
  * the hydroponics bay (x 14..25): planters gone to black vines (sculk, sculk veins, hanging roots, dead
    bushes) and an open floor hatch over the void (the orbit floor catches a fall, 5.1);
  * the ring junction (x 27..31): where the listening ring's spokes meet the spine, with the vault behind
    iron bars;
  * the central hall (x 33..44): the airlock corridor runs south from it to the landing deck;
  * the command deck (x 46..59): consoles along both walls, the log terminal at the east end under the
    viewport, and the listening dish on the roof above it.
Outside: the listening ring (a hoop of radius 14 around the spine at x 28..29, on four chain spokes) and the
landing deck on the south side (x 34..56, z 26..46, top y 16), clear to the sky: a courier or two interceptors
land there by hand (4.6). Its railing has gaps; open hatches face the void.

Template-local coordinates as in gen_structures_m3.py: x east, y up, z south. Unset cells are structure void
(the void around the station stays as it is). No floor circles, sigils or glowing runes: the deck's paint is
plain guide chevrons and a square.

Usage: python3 tools/assets/gen_structures_space.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_structures_m3 as m3  # noqa: E402
from gen_structures import AIR  # noqa: E402
from gen_structures_m3 import Site, bars, noise, sidecar, stairs  # noqa: E402

DATA = m3.DATA
SIZE = (64, 40, 48)
NAME = "station/spindle"

# The spine's cross-section: an octagon around (y, z) = (CY, CZ).
CY, CZ = 20, 16
X0, X1 = 3, 60
FLOOR_Y = 16
CEILING_Y = 24
BULKHEADS = (13, 26, 32, 45)
DOOR_Z = (15, 17)

# The listening ring and the landing deck.
RING_X = (28, 29)
RING_OUTER, RING_INNER = 14.5, 12.5
DECK_X = (34, 56)
DECK_Z = (26, 46)
CORRIDOR_X = (39, 41)

# Anchors (template-local; the Java side reads only the sidecar).
DECK = (45, 17, 36)
POSTS = ((40, 17, 28), (36, 17, 13), (19, 17, 13), (52, 17, 19))
LOG = (58, 17, 16)
VAULT = (29, 17, 11)

MOD = "oldrepublic:"
HULL = MOD + "republic_panel"
PLATING = MOD + "wreck_plating"
SCORCHED = MOD + "wreck_plating_scorched"
CONDUIT = MOD + "wreck_conduit"
DEEPSLATE = "minecraft:polished_deepslate"
TILE = "minecraft:polished_andesite"
SMOOTH = "minecraft:smooth_stone"
GRATE = "minecraft:waxed_oxidized_copper_grate"
GRATE_PROPS = {"waterlogged": "false"}
BULB_ON = "minecraft:waxed_oxidized_copper_bulb"
BULB_BRIGHT = "minecraft:waxed_copper_bulb"
BULB_PROPS_ON = {"lit": "true", "powered": "false"}
BULB_PROPS_OFF = {"lit": "false", "powered": "false"}
DEAD_LAMP = "minecraft:redstone_lamp"
DEAD_LAMP_PROPS = {"lit": "false"}
BARS = "minecraft:iron_bars"
CHAIN = "minecraft:iron_chain"
STRIPE_BLUE = "minecraft:blue_terracotta"
STRIPE_RED = "minecraft:red_terracotta"
PAINT_YELLOW = "minecraft:yellow_terracotta"
PAINT_WHITE = "minecraft:white_terracotta"


def octagon(y, z, radius, diagonal):
    u, v = abs(y - CY), abs(z - CZ)
    return max(u, v) <= radius and u + v <= diagonal


def in_hull(y, z):
    return octagon(y, z, 6, 9)


def in_room(y, z):
    return octagon(y, z, 5, 8)


def hull_block(x, y, z):
    """Weathered Republic plating: mostly panels, patched plates and a few scorch marks."""
    r = noise(5701, x // 2, y, z // 2)
    if r > 0.9:
        return SCORCHED
    if r > 0.72:
        return PLATING
    return HULL


def spine(t):
    """The octagonal hull, its floor and ceiling, the end caps and the viewport."""
    for x in range(X0, X1 + 1):
        for y in range(CY - 6, CY + 7):
            for z in range(CZ - 6, CZ + 7):
                if not in_hull(y, z):
                    continue
                cap = x in (X0, X1)
                if in_room(y, z) and not cap:
                    if y < FLOOR_Y:
                        t.set(x, y, z, CONDUIT if (x + z) % 5 == 0 else PLATING)
                    elif y == FLOOR_Y:
                        stripe = z in (CZ - 5, CZ + 5)
                        t.set(x, y, z, STRIPE_BLUE if stripe and x % 4 == 0 else (TILE if (x + z) % 2 else SMOOTH))
                    elif y >= CEILING_Y:
                        t.set(x, y, z, CONDUIT if y == CEILING_Y and z == CZ and x % 3 == 0 else hull_block(x, y, z))
                    else:
                        t.set(x, y, z, AIR)
                else:
                    t.set(x, y, z, hull_block(x, y, z))
    # The viewport in the east cap, over the command deck.
    for y in range(18, 23):
        for z in range(CZ - 3, CZ + 4):
            t.set(X1, y, z, "minecraft:glass")
    # Running lights along the spine's flanks (half of them dead).
    for x in range(X0 + 3, X1 - 2, 6):
        for z in (CZ - 6, CZ + 6):
            lit = noise(5711, x, 0, z) > 0.45
            t.set(x, CY, z, BULB_ON if lit else DEAD_LAMP, BULB_PROPS_ON if lit else DEAD_LAMP_PROPS)


def bulkheads(t):
    """Walls between the rooms, each with a 3 x 3 doorway framed in deepslate."""
    for x in BULKHEADS:
        for y in range(FLOOR_Y + 1, CEILING_Y):
            for z in range(CZ - 5, CZ + 6):
                if not in_room(y, z):
                    continue
                door = DOOR_Z[0] <= z <= DOOR_Z[1] and y <= FLOOR_Y + 3
                frame = (DOOR_Z[0] - 1 <= z <= DOOR_Z[1] + 1 and y <= FLOOR_Y + 4) and not door
                t.set(x, y, z, AIR if door else (DEEPSLATE if frame else hull_block(x, y, z)))


def ceiling_lights(t, x0, x1, step, seed):
    """Fittings in the ceiling: most dead, a few dim bulbs still lit."""
    for x in range(x0, x1 + 1, step):
        lit = noise(seed, x, 0, 0) > 0.55
        t.set(x, CEILING_Y, CZ, BULB_ON if lit else DEAD_LAMP, BULB_PROPS_ON if lit else DEAD_LAMP_PROPS)


def reactor(t):
    """The reactor room: the core in its cage, conduits to the walls, warning paint."""
    cx, cz = 8, CZ
    for y in range(FLOOR_Y + 1, CEILING_Y):
        if y == FLOOR_Y + 1:
            block, props = "minecraft:polished_blackstone", None
        elif y in (18, 22):
            block, props = "minecraft:sea_lantern", None
        elif y in (19, 21):
            block, props = BULB_ON, BULB_PROPS_ON
        else:
            block, props = BULB_BRIGHT, BULB_PROPS_OFF
        t.set(cx, y, cz, block, props)
    for y in range(FLOOR_Y + 2, CEILING_Y - 1):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == 0 and dz == 0:
                    continue
                broken = noise(5721, cx + dx, y, cz + dz) > 0.82
                if broken:
                    continue
                t.set(cx + dx, y, cz + dz, BARS, bars(east=dx != 1 and dz != 0, west=dx != -1 and dz != 0,
                                                      north=dz != -1 and dx != 0, south=dz != 1 and dx != 0))
    for y in (FLOOR_Y + 1, CEILING_Y - 1):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx or dz:
                    t.set(cx + dx, y, cz + dz, DEEPSLATE)
    # Warning paint around the core and conduits running to the walls along the ceiling.
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2:
                t.set(cx + dx, FLOOR_Y, cz + dz, STRIPE_RED if (dx + dz) % 2 == 0 else "minecraft:black_concrete")
    for x in range(cx + 2, 13):
        t.set(x, CEILING_Y - 1, cz, CONDUIT)
    for z in range(CZ - 4, CZ + 5):
        if abs(z - cz) > 1:
            t.set(cx, CEILING_Y - 1, z, CONDUIT)
    # A dead control bank by the door, and scorch where a panel blew.
    for z in (CZ - 4, CZ - 3):
        t.set(11, FLOOR_Y + 1, z, DEEPSLATE)
        t.set(11, FLOOR_Y + 2, z, "minecraft:daylight_detector", {"inverted": "true", "power": "0"})
    t.set(4, 19, CZ + 3, SCORCHED)
    t.set(4, 20, CZ + 4, SCORCHED)
    ceiling_lights(t, 5, 12, 7, 5731)


def hydroponics(t):
    """Planters gone to black vines, and an open hatch in the floor over the void."""
    x0, x1 = 14, 25
    for x in range(x0 + 1, x1):
        for z in (CZ - 4, CZ + 4):
            t.set(x, FLOOR_Y + 1, z, "minecraft:mud" if x % 3 else "minecraft:rooted_dirt")
            r = noise(5741, x, 1, z)
            if r > 0.55:
                t.set(x, FLOOR_Y + 2, z, "minecraft:sculk")
            elif r > 0.3:
                t.set(x, FLOOR_Y + 2, z, "minecraft:dead_bush")
        # Rails at the planters' inner edges.
        for z in (CZ - 3, CZ + 3):
            if x % 4 == 0:
                t.set(x, FLOOR_Y + 1, z, "minecraft:polished_deepslate_wall", {"east": "none", "west": "none", "north": "none",
                                                                                 "south": "none", "up": "true", "waterlogged": "false"})
    # Black vines on the walls and roots from the ceiling.
    for x in range(x0, x1 + 1):
        for z in range(CZ - 5, CZ + 6):
            if not in_room(CEILING_Y - 1, z):
                continue
            r = noise(5743, x, 2, z)
            if r > 0.7 and t.get(x, CEILING_Y - 1, z) == AIR:
                t.set(x, CEILING_Y - 1, z, "minecraft:hanging_roots", {"waterlogged": "false"})
            elif r > 0.5 and t.get(x, CEILING_Y - 1, z) == AIR:
                t.set(x, CEILING_Y - 1, z, "minecraft:sculk_vein", vein(up=True))
        for y in range(FLOOR_Y + 3, CEILING_Y - 1):
            for z, side in ((CZ - 5, "north"), (CZ + 5, "south")):
                if in_room(y, z) and t.get(x, y, z) == AIR and noise(5747, x, y, z) > 0.45:
                    t.set(x, y, z, "minecraft:sculk_vein", vein(**{side: True}))
    # The open hatch: the floor and the hull under it gone, scorched round the edge.
    hx, hz = (21, 22), (CZ + 1, CZ + 2)
    for x in range(hx[0] - 1, hx[1] + 2):
        for z in range(hz[0] - 1, hz[1] + 2):
            inner = hx[0] <= x <= hx[1] and hz[0] <= z <= hz[1]
            for y in range(CY - 6, FLOOR_Y + 1):
                if not in_hull(y, z):
                    continue
                if inner:
                    t.set(x, y, z, AIR)
                elif y == FLOOR_Y:
                    t.set(x, y, z, SCORCHED)
    for x, z in ((hx[0] - 1, hz[0]), (hx[1] + 1, hz[1])):
        t.set(x, FLOOR_Y + 1, z, "minecraft:iron_trapdoor", {"facing": "north" if z == hz[0] else "south", "half": "bottom",
                                                            "open": "true", "powered": "false", "waterlogged": "false"})
    ceiling_lights(t, 16, 25, 6, 5749)


def vein(**faces):
    props = {f: "false" for f in ("down", "up", "north", "south", "east", "west")}
    for face, on in faces.items():
        props[face] = "true" if on else "false"
    props["waterlogged"] = "false"
    return props


def junction(t):
    """The ring junction: the spokes' collar and the vault behind iron bars."""
    x0, x1 = 27, 31
    # The vault: a strongroom at the north wall, closed by bars with one gap.
    for x in range(x0, x1 + 1):
        for y in range(FLOOR_Y + 1, FLOOR_Y + 4):
            if x == VAULT[0]:
                continue
            t.set(x, y, CZ - 3, BARS, bars(east=x < x1, west=x > x0))
    for x in range(x0, x1 + 1):
        t.set(x, FLOOR_Y + 4, CZ - 3, DEEPSLATE)
        for z in range(CZ - 5, CZ - 3):
            t.set(x, FLOOR_Y, z, "minecraft:polished_deepslate")
    # Not a loot chest: the vault rolls chests/spindle_vault per player per day when it is used (SilentSpindle).
    t.set(*VAULT, "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"}, {"id": "minecraft:chest"})
    t.mark("SPINDLE_VAULT", *VAULT)
    for x in (x0, x1):
        t.set(x, FLOOR_Y + 1, CZ - 4, "minecraft:barrel", {"facing": "up", "open": "false"})
    ceiling_lights(t, 29, 29, 1, 5751)


def central_hall(t):
    """The hall: crates, a fallen console and the doorway to the airlock corridor."""
    for x, z, high in ((34, CZ - 4, True), (35, CZ - 4, False), (34, CZ - 3, False), (43, CZ + 4, True), (43, CZ + 3, False)):
        t.set(x, FLOOR_Y + 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
        if high:
            t.set(x, FLOOR_Y + 2, z, "minecraft:barrel", {"facing": "east", "open": "false"})
    t.set(42, FLOOR_Y + 1, CZ - 4, DEEPSLATE)
    t.set(42, FLOOR_Y + 1, CZ - 3, "minecraft:polished_deepslate_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(41, FLOOR_Y + 1, CZ - 4, "minecraft:daylight_detector", {"inverted": "false", "power": "0"})
    # Cables hanging from a torn ceiling panel.
    for y in range(FLOOR_Y + 4, CEILING_Y):
        t.set(37, y, CZ + 1, CHAIN, {"axis": "y", "waterlogged": "false"})
    t.set(37, CEILING_Y, CZ + 1, SCORCHED)
    ceiling_lights(t, 34, 44, 5, 5753)


def command_deck(t):
    """Consoles along both walls, chairs, the log terminal under the viewport."""
    for x in range(48, 58):
        for z in (CZ - 5, CZ + 5):
            if x % 3 == 2:
                continue
            t.set(x, FLOOR_Y + 1, z, DEEPSLATE)
            t.set(x, FLOOR_Y + 2, z, "minecraft:daylight_detector", {"inverted": str(x % 2 == 0).lower(), "power": "0"})
            if x % 3 == 0:
                facing = "south" if z < CZ else "north"  # the chair's back away from its console
                t.set(x, FLOOR_Y + 1, z + (2 if z < CZ else -2), "minecraft:polished_deepslate_stairs", stairs(facing))
    # The terminal's dais floor at the east end.
    for z in range(CZ - 3, CZ + 4):
        t.set(57, FLOOR_Y, z, "minecraft:polished_deepslate")
        t.set(58, FLOOR_Y, z, "minecraft:polished_deepslate")
    # The log terminal: its face to the room; a dead screen above it.
    t.set(*LOG, "minecraft:observer", {"facing": "west", "powered": "false"})
    t.mark("SPINDLE_LOG", *LOG)
    t.set(LOG[0], LOG[1] + 1, LOG[2], "minecraft:daylight_detector", {"inverted": "true", "power": "0"})
    for z in (CZ - 1, CZ + 1):
        t.set(58, FLOOR_Y + 1, z, DEEPSLATE)
        t.set(58, FLOOR_Y + 2, z, BULB_ON, BULB_PROPS_ON)
    ceiling_lights(t, 47, 56, 3, 5761)


def corridor(t):
    """The airlock corridor from the hall's south wall to the landing deck, its outer hatch standing open."""
    x0, x1 = CORRIDOR_X
    for z in range(CZ + 5, DECK_Z[0]):
        for x in range(x0 - 1, x1 + 2):
            for y in range(FLOOR_Y, FLOOR_Y + 5):
                wall = x in (x0 - 1, x1 + 1) or y in (FLOOR_Y, FLOOR_Y + 4)
                if wall:
                    if t.get(x, y, z) in (None, AIR) or not in_room(y, z):
                        t.set(x, y, z, TILE if y == FLOOR_Y else hull_block(x, y, z))
                else:
                    t.set(x, y, z, AIR)
    for z in (CZ + 7, DECK_Z[0] - 1):
        for x in (x0 - 1, x1 + 1):
            for y in range(FLOOR_Y + 1, FLOOR_Y + 4):
                t.set(x, y, z, DEEPSLATE)
    t.set(x0, FLOOR_Y + 3, DECK_Z[0] - 1, "minecraft:iron_trapdoor", {"facing": "south", "half": "top", "open": "true",
                                                                     "powered": "false", "waterlogged": "false"})
    t.set(x1, FLOOR_Y + 3, DECK_Z[0] - 1, "minecraft:iron_trapdoor", {"facing": "south", "half": "top", "open": "true",
                                                                     "powered": "false", "waterlogged": "false"})
    t.set(x0 + 1, FLOOR_Y + 3, CZ + 8, BULB_ON, BULB_PROPS_ON)


def deck(t):
    """The landing deck: plated, with guide paint, edge lights and a railing with gaps; girders under it."""
    (x0, x1), (z0, z1) = DECK_X, DECK_Z
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            square = (x in (x0 + 5, x1 - 5) and z0 + 5 <= z <= z1 - 5) or (z in (z0 + 5, z1 - 5) and x0 + 5 <= x <= x1 - 5)
            # Two guide chevrons at the south end, pointing at the corridor.
            row = z - (z1 - 6)
            chevron = 0 <= row <= 3 and abs(x - (x0 + x1) // 2) in (row * 2, row * 2 + 1) and row != 1
            if edge:
                block = DEEPSLATE
            elif square and noise(5771, x, 0, z) > 0.2:
                block = PAINT_WHITE
            elif chevron:
                block = PAINT_YELLOW
            else:
                block = TILE if noise(5773, x // 3, 0, z // 3) > 0.45 else SMOOTH
            t.set(x, FLOOR_Y, z, block)
            # Girders under the deck.
            if x % 6 == 4 or z % 6 == 2 or edge:
                t.set(x, FLOOR_Y - 1, z, PLATING)
            # The deck is clear to the sky (structure void above stays as the void).
            for y in range(FLOOR_Y + 1, SIZE[1]):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
    # Edge lights flush in the deck (the corners bright, the sides dim).
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        t.set(x, FLOOR_Y, z, "minecraft:sea_lantern")
    for x in range(x0 + 4, x1 - 2, 6):
        for z in (z0, z1):
            t.set(x, FLOOR_Y, z, BULB_BRIGHT, BULB_PROPS_ON)
    for z in range(z0 + 4, z1 - 2, 6):
        for x in (x0, x1):
            t.set(x, FLOOR_Y, z, BULB_BRIGHT, BULB_PROPS_ON)
    # The railing: bars along the edges with gaps where it was torn away, none in front of the corridor.
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if z == z0 and CORRIDOR_X[0] - 1 <= x <= CORRIDOR_X[1] + 1:
                continue
            if noise(5777, x, 1, z) > 0.3 and x not in (x0, x1):
                t.set(x, FLOOR_Y + 1, z, BARS, bars(east=x < x1, west=x > x0))
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            if noise(5779, x, 1, z) > 0.3:
                t.set(x, FLOOR_Y + 1, z, BARS, bars(north=z > z0, south=z < z1))
    # Struts from the spine down to the deck's far corners.
    for x in (x0 + 1, x1 - 1):
        for i in range(0, 12):
            z, y = CZ + 6 + i, CY - 6 + (i // 3)
            if y < FLOOR_Y and t.get(x, y, z) is None:
                t.set(x, y, z, "minecraft:polished_deepslate_wall", {"east": "none", "west": "none", "north": "low", "south": "low",
                                                                    "up": "false", "waterlogged": "false"})


def ring(t):
    """The listening ring: a plated hoop around the spine on four chain spokes."""
    for x in RING_X:
        for y in range(SIZE[1]):
            for z in range(SIZE[2]):
                r = ((y - CY) ** 2 + (z - CZ) ** 2) ** 0.5
                if RING_INNER <= r <= RING_OUTER and t.get(x, y, z) is None:
                    t.set(x, y, z, PLATING if noise(5781, x, y, z) > 0.75 else DEEPSLATE)
        for y in range(CY + 7, CY + 13):
            t.set(x, y, CZ, CHAIN, {"axis": "y", "waterlogged": "false"})
        for y in range(CY - 12, CY - 6):
            t.set(x, y, CZ, CHAIN, {"axis": "y", "waterlogged": "false"})
        for z in range(CZ - 12, CZ - 6):
            t.set(x, CY, z, CHAIN, {"axis": "z", "waterlogged": "false"})
        for z in range(CZ + 7, CZ + 13):
            t.set(x, CY, z, CHAIN, {"axis": "z", "waterlogged": "false"})


def dish(t):
    """The listening dish on the roof over the command deck: a mast, a shallow bowl, a feed horn."""
    mx, mz = 52, CZ
    top = CY + 7
    for y in range(top, top + 3):
        t.set(mx, y, mz, CHAIN, {"axis": "y", "waterlogged": "false"})
    t.set(mx, top + 3, mz, DEEPSLATE)
    for y in range(top + 4, top + 10):
        t.set(mx, y, mz, BARS, bars())
    t.set(mx, top + 10, mz, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    for dx in range(-5, 6):
        for dz in range(-5, 6):
            d2 = dx * dx + dz * dz
            if d2 == 0 or d2 > 22:
                continue
            y = top + 4 + (1 if d2 > 8 else 0) + (1 if d2 > 17 else 0)
            t.set(mx + dx, y, mz + dz, "minecraft:polished_andesite" if d2 > 17 else "minecraft:light_gray_concrete")
    t.set(mx, top + 8, mz, "minecraft:end_rod", {"facing": "down"})
    # Two whip antennas on the reactor's end.
    for z in (CZ - 2, CZ + 2):
        for y in range(top, top + 4):
            t.set(6, y, z, BARS, bars())
        t.set(6, top + 4, z, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})


def anchors(t):
    t.spot("SPINDLE_DECK", *DECK)
    for i, post in enumerate(POSTS, start=1):
        t.spot(f"SPINDLE_POST_{i}", *post)


def build():
    t = Site(NAME, SIZE, 0)
    spine(t)
    bulkheads(t)
    reactor(t)
    hydroponics(t)
    junction(t)
    central_hall(t)
    command_deck(t)
    corridor(t)
    ring(t)
    deck(t)
    dish(t)
    anchors(t)
    return t


def check(t):
    if t.size != SIZE:
        raise SystemExit(f"{t.name}: size {t.size}, expected {SIZE}")
    expected = ["SPINDLE_DECK", "SPINDLE_LOG", "SPINDLE_VAULT"] + [f"SPINDLE_POST_{i}" for i in range(1, 5)]
    if sorted(t.anchors) != sorted(expected):
        raise SystemExit(f"{t.name}: anchors {sorted(t.anchors)}, expected {sorted(expected)}")
    for anchor, cells in t.anchors.items():
        if len(cells) != 1:
            raise SystemExit(f"{t.name}: {anchor} has {len(cells)} cells")
        cell = cells[0]
        if not t.inside(*cell):
            raise SystemExit(f"{t.name}: {anchor} {cell} is outside the template")
        x, y, z = cell
        if anchor == "SPINDLE_LOG" and t.get(*cell) != "minecraft:observer":
            raise SystemExit(f"{t.name}: the log terminal at {cell} is {t.get(*cell)}")
        if anchor == "SPINDLE_VAULT" and t.get(*cell) != "minecraft:chest":
            raise SystemExit(f"{t.name}: the vault at {cell} is {t.get(*cell)}")
        if anchor.startswith("SPINDLE_POST") or anchor == "SPINDLE_DECK":
            if t.get(x, y, z) != AIR or t.get(x, y + 1, z) != AIR:
                raise SystemExit(f"{t.name}: {anchor} {cell} is not two cells of air")
            if t.get(x, y - 1, z) in (None, AIR):
                raise SystemExit(f"{t.name}: {anchor} {cell} does not stand on a block")
    # The deck is clear to the sky over its whole surface.
    (x0, x1), (z0, z1) = DECK_X, DECK_Z
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            for y in range(FLOOR_Y + 2, SIZE[1]):
                if t.get(x, y, z) not in (None, AIR):
                    raise SystemExit(f"{t.name}: {t.get(x, y, z)} at {(x, y, z)} stands over the landing deck")
    # Every room is reachable from the deck through the corridor and the doorways (a flood fill over feet cells).
    reach = walkable(t, DECK)
    for post in POSTS + ((LOG[0] - 1, LOG[1], LOG[2]), (VAULT[0], VAULT[1], VAULT[2] + 1), (8, 17, CZ + 3)):
        if post not in reach:
            raise SystemExit(f"{t.name}: {post} cannot be walked to from the deck")


def walkable(t, start):
    """Feet cells reachable from `start` walking on blocks (steps of one up or down)."""
    def stands(x, y, z):
        return (t.inside(x, y, z) and t.get(x, y, z) == AIR and t.get(x, y + 1, z) in (AIR, None)
                and t.get(x, y - 1, z) not in (None, AIR))
    seen = {start}
    todo = [start]
    while todo:
        x, y, z = todo.pop()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1):
                n = (x + dx, y + dy, z + dz)
                if n not in seen and stands(*n):
                    seen.add(n)
                    todo.append(n)
    return seen


def main():
    t = build()
    check(t)
    n, p = t.save(os.path.join(DATA, "structure", "station", "spindle.nbt"))
    sidecar(os.path.join(DATA, "site_anchors", "station", "spindle.json"), {k: t.anchors[k] for k in sorted(t.anchors)})
    print(f"{t.name}: size {t.size}, {n} blocks, {p} palette entries, anchors {', '.join(sorted(t.anchors))}")


if __name__ == "__main__":
    main()
