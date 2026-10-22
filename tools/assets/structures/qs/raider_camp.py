#!/usr/bin/env python3
"""story/raider_camp, "Smoke on the Ridge" (docs/MILESTONE_6.md 3.5 and 5.3): Captain Hessa Dray's
stronghold on a ridge. 51 x 26 x 51, buried 1 (layer 0 is the ground), terrain high.

A ring of palisade (stakes of spruce and dark oak, patched with ship scrap) round a trampled yard,
a dry ditch outside it. The gate is in the north wall between two watch towers joined by a walkway:
the west tower of logs, the east one of scrap and cargo plating. Inside:
  * the yard's cook fire with log benches (HEART);
  * the captain's longhouse, south-east: a timber hall with a wide door and a porch (CAPTAIN);
  * a rock crag in the south-west corner, which the palisade runs into, stairs cut up its face and on
    top the signal tower: a lit campfire with signal_fire on a hay bale nineteen blocks above the yard,
    whose smoke shows over the ridge (SIGNAL_FIRE is the campfire's cell);
  * a mine adit in the crag where the settlers are made to dig crystal, rails and veined stone;
  * three cages at the crag's foot, each built differently, their gates standing open (CAPTIVE_1..3
    inside them, ACOLYTE in front: the deserter the Captain hired guards them);
  * the workshop, north-east, and a speeder up on blocks in front of it;
  * the crew's patched tents north-west, the settlers' stolen carts, hay and crystal crates.

Anchors are feet anchors except SIGNAL_FIRE. Crew spots have free ground east of them.

Usage: python3 tools/assets/structures/qs/raider_camp.py [--preview <dir>]
"""
import math
import sys

from common import (AIR, CHAIN_Y, LANTERN, LANTERN_HUNG, S, Noise, Template, barrel, by_noise, campfire, carpet,
                    check, column, fence, gate, log, mod, preview_dir, slab, spot, stairs, wall, write)

SIZE = (51, 26, 51)
SEED = 5101
CX, CZ = 25, 26          # the palisade's centre
RING = 20.0               # the palisade's "radius" (a rounded square)
POW = 3.0
META = dict(buried=1, terrain='high', max_slope=6, clearance=12, support_depth=10, ramp_rings=3, clear_above=6,
            spawn_radius=64)

GATE_X = range(23, 28)    # the gateway, x 23..27, in the north wall (z 6)
CRAG = (11, 39)           # the crag's centre; its top is layer 8
CRAG_TOP = 8
CRAG_R = 9.5

ANCHORS = {
    'GATE': (25, 1, 6), 'HEART': (25, 1, 21), 'CAPTAIN': (34, 1, 27), 'ACOLYTE': (12, 1, 20),
    'CAPTIVE_1': (8, 1, 23), 'CAPTIVE_2': (12, 1, 24), 'CAPTIVE_3': (16, 1, 23),
    'CREW_1': (22, 1, 13), 'CREW_2': (28, 1, 13), 'CREW_3': (29, 1, 22), 'CREW_4': (35, 1, 19),
    'CREW_5': (15, 1, 18), 'CREW_6': (30, 1, 28), 'CREW_7': (21, 1, 35),
}
SIGNAL = (9, 20, 38)          # the signal campfire, on a hay bale on the tower's platform

GRASS = S('grass_block', snowy=False)
TRAMPLED = [(S('coarse_dirt'), 5), (GRASS, 2), (S('gravel'), 1), (S('packed_mud'), 1), (S('dirt'), 2)]
ROCK = [(S('stone'), 5), (S('andesite'), 3), (S('tuff'), 2), (S('cobblestone'), 1), (S('mossy_cobblestone'), 1)]
SCRAP = [(S('or:rusted_plating'), 5), (S('or:wreck_plating'), 3), (S('or:wreck_plating_scorched'), 2),
         (S('or:durasteel_plating'), 1), (S('or:cargo_crate'), 1)]
STAKES = [(log('stripped_spruce_log'), 5), (log('spruce_log'), 3), (log('dark_oak_log'), 2)]


def rho(x, z):
    """A distance-like measure from the centre whose level lines are rounded squares (20 = the wall)."""
    r = (abs(x - CX) / RING) ** POW + (abs(z - CZ) / RING) ** POW
    return RING * r ** (1.0 / POW)


def inside(x, z):
    return rho(x, z) <= RING


def crag_h(x, z, n):
    """The crag's top layer at a column (0 where there is no rock)."""
    d = math.hypot(x - CRAG[0], z - CRAG[1])
    if d <= 3.8:
        return CRAG_TOP
    if d >= CRAG_R:
        return 0
    f = 1 - (d - 3.8) / (CRAG_R - 3.8)
    h = CRAG_TOP * f ** 0.75 + (n.at(x, 0, z) - 0.5) * 3.0
    return max(0, min(CRAG_TOP, int(round(h))))


# ---------------------------------------------------------------- ground, ditch, crag

def ground(t):
    n = Noise(SEED + 1, 3.0)
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            r = rho(x, z)
            if r <= RING + 1:
                t.set(x, 0, z, by_noise(n, TRAMPLED, x, 0, z))
            elif RING + 1.5 < r <= RING + 3.4:
                # the ditch, a few old stakes left standing in it
                t.set(x, 0, z, fence('spruce_fence') if n.at(x, 5, z) > 0.86 else AIR)
    # The approach: a track up to the gate, planks over the ditch.
    for z in range(0, 7):
        for x in range(24, 27):
            r = rho(x, z)
            t.set(x, 0, z, S('spruce_planks') if RING + 1.5 < r <= RING + 3.4 else S('dirt_path'))


def paths(t):
    """Worn paths across the yard from the gate to the fire and on to each corner."""
    n = Noise(SEED + 2, 2.0)
    legs = [((25, 6), (25, 22)), ((25, 22), (34, 29)), ((25, 22), (20, 36)), ((25, 22), (35, 19)),
            ((25, 22), (15, 17)), ((25, 22), (12, 21))]
    for (a, b) in legs:
        steps = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
        for i in range(steps + 1):
            x = round(a[0] + (b[0] - a[0]) * i / steps)
            z = round(a[1] + (b[1] - a[1]) * i / steps)
            for (dx, dz) in ((0, 0), (1, 0), (0, 1)):
                if t.name_at(x + dx, 0, z + dz) not in (None, 'minecraft:air') and n.at(x + dx, 0, z + dz) > 0.2:
                    t.set(x + dx, 0, z + dz, S('dirt_path') if n.at(x, 3, z) > 0.35 else S('coarse_dirt'))


def crag(t):
    """The rock the camp is built against: the palisade runs into it."""
    n = Noise(SEED + 3, 2.4)
    m = Noise(SEED + 4, 1.6)
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            h = crag_h(x, z, n)
            if h <= 0:
                continue
            for y in range(0, h + 1):
                t.set(x, y, z, by_noise(m, ROCK, x, y, z))
            if h <= 3 and m.at(x, 9, z) > 0.45:
                t.set(x, h, z, S('moss_block') if m.at(x, 7, z) > 0.6 else GRASS)
            elif h == CRAG_TOP and m.at(x, 5, z) > 0.7:
                t.set(x, h, z, S('mossy_cobblestone'))
            for y in range(h + 1, h + 4):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
    # Stairs cut up the crag's east face, two wide, from the yard to the top.
    for k in range(0, CRAG_TOP):
        x = 19 - k
        for z in (37, 38):
            for y in range(0, 1 + k):
                if t.get(x, y, z) is None or t.is_air(x, y, z):
                    t.set(x, y, z, by_noise(m, ROCK, x, y, z))
            t.set(x, 1 + k, z, stairs('cobblestone_stairs' if (k + z) % 3 else 'mossy_cobblestone_stairs', 'west'))
            for y in range(2 + k, 5 + k):
                t.set(x, y, z, AIR)
    for z in (37, 38):
        t.set(20, 0, z, S('coarse_dirt'))
    # The mine adit in the north face, where the captives dig: a timber frame, rails, veined stone.
    for z in range(31, 37):
        for x in (10, 11):
            t.set(x, 0, z, by_noise(m, ROCK, x, 0, z))
            t.set(x, 1, z, S('rail', shape='north_south', waterlogged=False) if x == 10 else AIR)
            t.set(x, 2, z, AIR)
            if t.get(x, 3, z) is None or t.is_air(x, 3, z):
                t.set(x, 3, z, by_noise(m, ROCK, x, 3, z))
        for x in (9, 12):
            for y in (1, 2, 3):
                if t.get(x, y, z) is None or t.is_air(x, y, z):
                    t.set(x, y, z, by_noise(m, ROCK, x, y, z))
    for (x, y, z) in ((9, 2, 35), (12, 1, 34), (12, 2, 36), (10, 3, 35), (9, 1, 36), (11, 3, 33)):
        t.set(x, y, z, S('or:crystal_veined_stone'))
    for x in (10, 11):
        t.set(x, 1, 37, S('or:crystal_veined_stone'))
        t.set(x, 2, 37, S('or:crystal_veined_stone'))
    for z in (31, 34):
        column(t, 9, 1, 2, z, log('stripped_spruce_log'))
        column(t, 12, 1, 2, z, log('stripped_spruce_log'))
        for x in (9, 10, 11, 12):
            t.set(x, 3, z, log('stripped_spruce_log', 'x'))
    t.set(11, 2, 32, AIR)
    t.set(11, 1, 35, LANTERN)
    t.set(11, 1, 31, S('or:debris'))
    t.set(11, 1, 33, S('amethyst_cluster', facing='up', waterlogged=False))


# ---------------------------------------------------------------- the palisade and the gatehouse

def palisade(t):
    n = Noise(SEED + 11, 2.2)
    hn = Noise(SEED + 12, 4.0)
    rock = Noise(SEED + 3, 2.4)
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            if not inside(x, z):
                continue
            if all(inside(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                continue
            if z <= 7 and x in GATE_X:
                continue
            if crag_h(x, z, rock) >= 2:
                continue  # the rock is the wall here
            seg = int((math.atan2(z - CZ, x - CX) + math.pi) / (2 * math.pi) * 30)
            scrap = (seg * 7 + 3) % 5 == 0
            h = 4 + (1 if hn.at(x, 0, z) > 0.6 else 0) - (1 if hn.at(x, 0, z) < 0.18 else 0)
            for y in range(1, h + 1):
                if scrap:
                    st = by_noise(n, SCRAP, x, y, z)
                    if y >= 3 and n.at(x, y + 9, z) > 0.75:
                        st = S('iron_bars', north=False, east=False, south=False, west=False, waterlogged=False)
                else:
                    st = by_noise(n, STAKES, x, 0, z)
                t.set(x, y, z, st)
            if not scrap and (x + z) % 2 == 0:
                t.set(x, h + 1, z, fence('spruce_fence'))       # the stakes' sharpened tips
            if scrap and n.at(x, 30, z) > 0.6:
                t.set(x, h + 1, z, CHAIN_Y)
            t.set(x, 0, z, S('coarse_dirt'))


def gatehouse(t):
    """The gate between two watch towers: the west one of logs, the east one of scrap; a walkway joins
    them over the gate; the gate's leaves stand open against the towers."""
    # The lintel over the gateway and the walkway behind it.
    for x in range(22, 29):
        t.set(x, 6, 6, log('stripped_dark_oak_log', 'x'))
        if x % 2 == 0:
            t.set(x, 7, 6, log('stripped_dark_oak_log'))
    for x in range(23, 28):
        for z in (7, 8, 9):
            t.set(x, 6, z, S('spruce_planks') if (x * 3 + z) % 7 else S('or:rusted_plating'))
        t.set(x, 7, 9, fence('spruce_fence'))
        for y in range(1, 6):
            for z in (6, 7, 8, 9):
                t.set(x, y, z, AIR)
        t.set(x, 0, 6, S('dirt_path'))
    t.set(25, 5, 7, LANTERN_HUNG)
    # Stout gateposts under the lintel's ends, the crew's mark hung on them for whoever comes up the track.
    for x in (22, 28):
        column(t, x, 1, 5, 6, log('dark_oak_log'))
        mod.banner(t, x, 4, 5, 'north', 'brown', [('minecraft:cross', 'black')])
    # The leaves: planks braced with dark oak (west), a sheet of hull plating (east), swung open.
    for z in (7, 8):
        for y in range(1, 5):
            t.set(23, y, z, S('spruce_planks') if y != 2 else log('stripped_dark_oak_log', 'z'))
            t.set(27, y, z, by_noise(Noise(SEED + 13, 1.0), [(S('or:rusted_plating'), 2), (S('or:wreck_plating'), 1)], 27, y, z))
    t.set(23, 5, 7, AIR)

    # The west tower, of logs.
    x0, z0 = 18, 7
    for (x, z) in ((x0, z0), (x0 + 4, z0), (x0, z0 + 4), (x0 + 4, z0 + 4)):
        column(t, x, 1, 9, z, log('stripped_dark_oak_log'))
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            if (x, z) not in ((x0, z0), (x0 + 4, z0), (x0, z0 + 4), (x0 + 4, z0 + 4)):
                t.set(x, 6, z, S('spruce_planks'))
            for y in range(1, 6):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
    for x in range(x0 + 1, x0 + 4):
        t.set(x, 3, z0, log('spruce_log', 'x'))
        t.set(x, 3, z0 + 4, log('spruce_log', 'x'))
    for z in range(z0 + 1, z0 + 4):
        t.set(x0, 3, z, log('spruce_log', 'z'))
    column(t, 20, 1, 5, z0 + 3, S('spruce_planks'))
    for y in range(1, 7):
        t.set(20, y, z0 + 4, S('ladder', facing='south', waterlogged=False))
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            edge = x in (x0, x0 + 4) or z in (z0, z0 + 4)
            corner = x in (x0, x0 + 4) and z in (z0, z0 + 4)
            if edge and not corner and (x, z) != (20, z0 + 4) and not (x == x0 + 4 and z in (z0 + 1, z0 + 2)):
                t.set(x, 7, z, fence('spruce_fence'))
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            ring = x in (x0, x0 + 4) or z in (z0, z0 + 4)
            if ring:
                f = 'north' if z == z0 + 4 else 'south' if z == z0 else 'east' if x == x0 else 'west'
                if x in (x0, x0 + 4) and z in (z0, z0 + 4):
                    f = 'south' if z == z0 else 'north'
                t.set(x, 10, z, stairs('spruce_stairs', f))
            else:
                t.set(x, 10, z, S('spruce_planks'))
    t.set(x0 + 2, 11, z0 + 2, slab('spruce_slab'))
    for (x, z) in ((x0 + 1, z0 + 2), (x0 + 3, z0 + 2), (x0 + 2, z0 + 1), (x0 + 2, z0 + 3)):
        t.set(x, 11, z, slab('spruce_slab'))
    t.set(x0 + 2, 9, z0 + 2, LANTERN_HUNG)
    t.set(x0 + 1, 5, z0 + 2, LANTERN_HUNG)                        # under the platform

    # The east tower, of scrap: plated walls round a ladder, a grated platform, a plated roof.
    x0, z0 = 28, 7
    sn = Noise(SEED + 14, 1.3)
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            edge = x in (x0, x0 + 4) or z in (z0, z0 + 4)
            for y in range(1, 6):
                if edge:
                    t.set(x, y, z, by_noise(sn, SCRAP, x, y, z))
                else:
                    t.set(x, y, z, AIR)
            if (x, z) != (30, z0 + 1):
                t.set(x, 6, z, S('or:durasteel_grate'))
    for y in (1, 2):
        t.set(30, y, z0 + 4, AIR)                                   # its door, south
    for y in range(1, 7):
        t.set(30, y, z0 + 1, S('ladder', facing='south', waterlogged=False))
    t.set(30, 6, z0 + 1, S('ladder', facing='south', waterlogged=False))
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            edge = x in (x0, x0 + 4) or z in (z0, z0 + 4)
            if not edge or (x == x0 and z in (z0 + 1, z0 + 2)) or (x, z) == (30, z0 + 1):
                continue
            corner = x in (x0, x0 + 4) and z in (z0, z0 + 4)
            if corner:
                column(t, x, 7, 8, z, S('or:durasteel_beam', axis='y'))
            else:
                t.set(x, 7, z, by_noise(sn, SCRAP, x, 7, z) if sn.at(x, 1, z) < 0.6
                      else S('iron_bars', north=False, east=False, south=False, west=False, waterlogged=False))
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            t.set(x, 9, z, slab('or:durasteel_plating_slab'))
    t.set(x0 + 4, 10, z0 + 4, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(x0 + 4, 11, z0 + 4, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(x0 + 2, 8, z0 + 2, LANTERN_HUNG)
    t.set(x0 + 3, 5, z0 + 3, LANTERN_HUNG)                        # inside the plated base
    t.set(x0, 7, z0, S('or:durasteel_beam', axis='y'))


# ---------------------------------------------------------------- the yard

def cook_fire(t):
    t.set(25, 0, 24, S('cobblestone'))
    t.set(25, 1, 24, campfire(True, facing='north'))
    column(t, 24, 1, 2, 24, fence('spruce_fence'))
    column(t, 26, 1, 2, 24, fence('spruce_fence'))
    t.set(25, 2, 24, S('iron_chain', axis='x', waterlogged=False))
    for z in (23, 24, 25):
        t.set(22, 1, z, log('stripped_spruce_log', 'z'))
    for x in (24, 25, 26):
        t.set(x, 1, 27, log('spruce_log', 'x'))
    t.set(28, 1, 25, log('oak_log'))                                # a stump to sit on
    t.set(23, 1, 26, barrel('up'))
    t.set(27, 1, 26, S('cauldron'))
    for (x, z) in ((23, 18), (31, 22), (19, 28), (27, 29)):
        column(t, x, 1, 2, z, fence('spruce_fence'))
        t.set(x, 3, z, LANTERN)


def longhouse(t):
    """The captain's hall: posts and planks patched with scrap, a long roof, a wide door to the yard."""
    x0, x1, z0, z1 = 28, 41, 30, 38
    n = Noise(SEED + 21, 1.8)
    wall_mix = [(S('spruce_planks'), 6), (S('dark_oak_planks'), 2), (S('or:rusted_plating'), 1)]
    posts = {x0, x1, 32, 37}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, S('spruce_planks') if x0 < x < x1 and z0 < z < z1 else S('cobblestone'))
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(1, 5):
                if not edge:
                    t.set(x, y, z, AIR)
                elif (x in posts and z in (z0, z1)) or (z == 34 and x in (x0, x1)):
                    t.set(x, y, z, log('stripped_spruce_log'))
                else:
                    t.set(x, y, z, by_noise(n, wall_mix, x, y, z))
    for x in range(x0, x1 + 1):
        t.set(x, 4, z0, log('stripped_spruce_log', 'x'))
        t.set(x, 4, z1, log('stripped_spruce_log', 'x'))
    # Windows (barred) and the wide door.
    for (x, z) in ((30, z0), (39, z0), (30, z1), (35, z1), (39, z1)):
        t.set(x, 2, z, S('iron_bars', north=False, east=False, south=False, west=False, waterlogged=False))
    for x in (33, 34, 35):
        for y in (1, 2, 3):
            t.set(x, y, z0, AIR)
    # The roof, its ridge along x.
    rows = {29: 4, 30: 5, 31: 6, 32: 7, 33: 8, 34: 9, 35: 8, 36: 7, 37: 6, 38: 5, 39: 4}
    rn = Noise(SEED + 22, 2.5)
    for x in range(x0 - 1, x1 + 2):
        for z, y in rows.items():
            name = 'spruce_stairs' if rn.at(x, 0, z) < 0.72 else 'dark_oak_stairs'
            if z < 34:
                t.set(x, y, z, stairs(name, 'south'))
            elif z > 34:
                t.set(x, y, z, stairs(name, 'north'))
            else:
                t.set(x, y, z, slab('dark_oak_slab'))
                t.set(x, y - 1, z, log('stripped_dark_oak_log', 'x') if x0 <= x <= x1 else AIR)
    for x in (x0, x1):
        for z, top in rows.items():
            if z0 < z < z1:
                for y in range(5, top):
                    t.set(x, y, z, by_noise(n, wall_mix, x, y, z))
    # Inside: a long table with benches, the captain's corner, a hearth, plunder.
    for x in range(31, 37):
        t.set(x, 1, 34, slab('spruce_slab', 'top'))
        t.set(x, 1, 33, stairs('spruce_stairs', 'north') if x % 2 else AIR)
        t.set(x, 1, 35, stairs('spruce_stairs', 'south') if x % 2 == 0 else AIR)
    t.set(31, 2, 34, candle_lit())
    t.set(34, 2, 34, S('lantern', hanging=False, waterlogged=False))
    t.set(37, 1, 34, S('cartography_table'))
    for z in range(31, 38):
        t.set(29, 1, z, S('hay_block', axis='y') if z in (31, 32) else AIR)
    t.set(29, 2, 31, S('hay_block', axis='y'))
    t.set(29, 1, 33, S('or:cargo_crate'))
    t.set(29, 1, 34, S('or:cargo_crate'))
    t.set(29, 2, 34, S('or:cargo_crate'))
    t.set(29, 1, 35, barrel('up'))
    t.set(29, 2, 35, LANTERN)
    t.set(30, 1, 37, S('amethyst_cluster', facing='up', waterlogged=False))
    t.set(29, 1, 37, S('or:cargo_crate'))
    t.set(29, 2, 37, S('amethyst_cluster', facing='up', waterlogged=False))
    # The captain's corner: her bed, her chest, a rug.
    t.set(40, 1, 37, S('brown_bed', facing='east', part='head', occupied=False))
    t.set(39, 1, 37, S('brown_bed', facing='east', part='foot', occupied=False))
    t.set(40, 1, 36, barrel('west'))                               # her sea chest: no loot (the quest pays)
    t.set(40, 1, 31, barrel('up'))
    t.set(40, 1, 32, barrel('up'))
    t.set(40, 2, 31, S('decorated_pot', facing='west', cracked=False, waterlogged=False))
    for x in (38, 39):
        for z in (35, 36):
            t.set(x, 1, z, carpet('red'))
    # The hearth in the east gable and its chimney.
    for y in range(1, 11):
        t.set(x1, y, 34, S('cobblestone'))
    t.set(x1 - 1, 1, 34, campfire(True, facing='west'))
    t.set(x1 - 1, 0, 34, S('cobblestone'))
    # Light: lanterns on chains from the ridge beam.
    for x in (31, 35, 39):
        t.set(x, 7, 34, CHAIN_Y)
        t.set(x, 6, 34, LANTERN_HUNG)
    # The porch: a plank deck before the door, a lantern under the eave.
    for x in range(32, 37):
        for z in (27, 28, 29):
            t.set(x, 0, z, S('spruce_planks') if z > 27 else S('coarse_dirt'))
    t.set(34, 3, 29, LANTERN_HUNG)
    column(t, 32, 1, 1, 28, log('stripped_spruce_log'))
    column(t, 36, 1, 1, 28, log('stripped_spruce_log'))
    t.set(32, 2, 28, fence('spruce_fence'))
    t.set(36, 2, 28, fence('spruce_fence'))


def candle_lit():
    return S('candle', candles=3, lit=True, waterlogged=False)


def cages(t):
    """Three cages at the crag's foot, their gates open: iron bars, a stake pen, a scrap box."""
    # Iron bars between log corners, a log frame over the edges.
    x0, z0 = 7, 22
    bars = S('iron_bars', north=False, east=False, south=False, west=False, waterlogged=False)
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            t.set(x, 0, z, S('hay_block', axis='y') if (x, z) == (x0 + 1, z0 + 1) else S('coarse_dirt'))
            corner = x in (x0, x0 + 2) and z in (z0, z0 + 2)
            if corner:
                column(t, x, 1, 3, z, log('stripped_dark_oak_log'))
            elif (x, z) != (x0 + 1, z0 + 1):
                column(t, x, 1, 2, z, bars)
    t.set(x0 + 1, 1, z0, gate('dark_oak_fence_gate', 'north', True))
    t.set(x0 + 1, 2, z0, AIR)
    t.set(x0 + 1, 3, z0, log('stripped_dark_oak_log', 'x'))
    t.set(x0 + 1, 3, z0 + 2, log('stripped_dark_oak_log', 'x'))
    t.set(x0, 3, z0 + 1, log('stripped_dark_oak_log', 'z'))
    t.set(x0 + 2, 3, z0 + 1, log('stripped_dark_oak_log', 'z'))
    # A pen of stakes.
    x0, z0 = 11, 23
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            t.set(x, 0, z, S('hay_block', axis='y') if (x, z) == (x0 + 1, z0 + 1) else S('coarse_dirt'))
            corner = x in (x0, x0 + 2) and z in (z0, z0 + 2)
            if corner:
                column(t, x, 1, 3, z, log('stripped_spruce_log'))
            elif (x, z) != (x0 + 1, z0 + 1):
                column(t, x, 1, 2, z, fence('spruce_fence'))
    t.set(x0 + 1, 1, z0, gate('spruce_fence_gate', 'north', True))
    t.set(x0 + 1, 2, z0, AIR)
    # A box of scrap plating with barred sides, a chain over its corner.
    x0, z0 = 15, 22
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            t.set(x, 0, z, S('hay_block', axis='y') if (x, z) == (x0 + 1, z0 + 1) else S('or:durasteel_deck'))
            corner = x in (x0, x0 + 2) and z in (z0, z0 + 2)
            if corner:
                column(t, x, 1, 3, z, S('or:rusted_plating'))
            elif (x, z) != (x0 + 1, z0 + 1):
                column(t, x, 1, 2, z, bars)
    t.set(x0 + 1, 1, z0, gate('dark_oak_fence_gate', 'north', True))
    t.set(x0 + 1, 2, z0, AIR)
    t.set(x0 + 2, 4, z0 + 2, CHAIN_Y)
    for z in (z0, z0 + 2):
        t.set(x0 + 1, 3, z, S('or:durasteel_beam', axis='x'))
    # The chains that held them, a water bucket's cauldron, a crate of picks.
    t.set(10, 1, 21, S('cauldron'))
    t.set(14, 1, 21, S('or:cargo_crate'))


def workshop(t):
    """An open shed of posts and a lean-to roof against a scrap back wall: tools and salvage."""
    x0, x1, z0, z1 = 32, 40, 12, 17
    n = Noise(SEED + 31, 1.4)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, S('gravel') if n.at(x, 0, z) > 0.55 else S('coarse_dirt'))
            for y in (1, 2, 3):
                t.set(x, y, z, AIR)
        for y in (1, 2, 3, 4):
            t.set(x, y, z0, by_noise(n, SCRAP, x, y, z0) if x not in (x0, x1) else log('stripped_spruce_log'))
    for (x, z) in ((x0, z1), (x1, z1), (36, z1)):
        column(t, x, 1, 3, z, log('stripped_spruce_log'))
    for x in range(x0 - 1, x1 + 2):
        t.set(x, 5, z0, slab('spruce_slab'))
        for z in range(z0 + 1, z0 + 3):
            t.set(x, 4, z, S('spruce_planks') if x in (x0 - 1, x1 + 1) else slab('spruce_slab', 'top'))
        for z in range(z0 + 3, z1 + 2):
            t.set(x, 4, z, slab('spruce_slab'))
    for x in range(x0 + 1, x1):
        t.set(x, 3, z1, log('spruce_log', 'x'))
    # The benches along the back wall.
    t.set(33, 1, 13, S('anvil', facing='east'))
    t.set(34, 1, 13, S('grindstone', face='floor', facing='north'))
    t.set(35, 1, 13, S('smithing_table'))
    t.set(36, 1, 13, S('blast_furnace', facing='south', lit=False))
    t.set(37, 1, 13, S('crafting_table'))
    t.set(38, 1, 13, barrel('up'))
    t.set(39, 1, 13, S('or:cargo_crate'))
    t.set(39, 2, 13, S('or:cargo_crate'))
    # Salvage: plating, a ship's vent, bars, chain, scraps on the floor.
    t.set(39, 1, 15, S('or:wreck_plating_scorched'))
    t.set(39, 1, 16, S('or:durasteel_vent'))
    t.set(38, 1, 16, S('iron_bars', north=False, east=False, south=False, west=False, waterlogged=False))
    t.set(39, 2, 15, S('or:durasteel_plating_slab', type='bottom', waterlogged=False))
    for (x, z) in ((34, 15), (36, 16), (37, 14), (33, 16)):
        t.set(x, 1, z, S('or:debris'))
    t.set(34, 3, 14, LANTERN_HUNG)
    t.set(38, 3, 15, LANTERN_HUNG)


def speeder(t):
    """A battered speeder on a repair stand in front of the workshop, nose west."""
    z = 20
    for x in (35, 38):
        t.set(x, 1, z + 1, wall('cobblestone_wall'))
    # Hull: plated sides, a pointed nose, a windscreen, two seats, twin engines at the back.
    for x in range(34, 39):
        t.set(x, 2, z, S('or:durasteel_plating_slab', type='top', waterlogged=False))
        t.set(x, 2, z + 2, S('or:durasteel_plating_slab', type='top', waterlogged=False))
    t.set(33, 2, z + 1, stairs('or:durasteel_plating_stairs', 'east', 'top'))
    t.set(34, 2, z + 1, S('or:durasteel_plating'))
    t.set(35, 2, z + 1, S('or:durasteel_plating'))
    t.set(35, 3, z + 1, S('glass_pane', north=True, south=True, east=False, west=False, waterlogged=False))
    t.set(36, 2, z + 1, stairs('spruce_stairs', 'east'))
    t.set(37, 2, z + 1, stairs('spruce_stairs', 'east'))
    t.set(38, 2, z + 1, S('or:durasteel_plating'))
    for zz in (z, z + 2):
        t.set(39, 2, zz, S('or:durasteel_vent'))
        t.set(40, 2, zz, S('lightning_rod', facing='east', powered=False, waterlogged=False))
    t.set(39, 2, z + 1, S('or:rusted_plating'))
    t.set(36, 1, z + 2, S('or:debris'))
    t.set(37, 1, z, S('or:debris'))


def tents(t):
    """The crew's tents, patched canvas over spruce poles, bedrolls inside."""
    patch = [(S('or:canvas'), 6), (S('brown_wool'), 2), (S('green_wool'), 1)]
    n = Noise(SEED + 41, 1.0)
    for (x0, z0) in ((10, 12), (16, 13)):
        # 5 across (x), 5 long (z), door south.
        for z in range(z0, z0 + 5):
            for x in range(x0, x0 + 5):
                t.set(x, 0, z, S('coarse_dirt'))
            t.set(x0, 1, z, by_noise(n, patch, x0, 1, z))
            t.set(x0 + 4, 1, z, by_noise(n, patch, x0 + 4, 1, z))
            t.set(x0, 2, z, stairs('or:canvas_stairs', 'east') if n.at(x0, 2, z) < 0.75 else S('brown_wool'))
            t.set(x0 + 4, 2, z, stairs('or:canvas_stairs', 'west') if n.at(x0 + 4, 2, z) < 0.75 else S('green_wool'))
            t.set(x0 + 1, 3, z, stairs('or:canvas_stairs', 'east'))
            t.set(x0 + 3, 3, z, stairs('or:canvas_stairs', 'west'))
            t.set(x0 + 2, 4, z, slab('or:canvas_slab'))
            for x in (x0 + 1, x0 + 2, x0 + 3):
                t.set(x, 1, z, AIR)
                t.set(x, 2, z, AIR)
            t.set(x0 + 2, 3, z, AIR)
        for x in (x0 + 1, x0 + 2, x0 + 3):
            t.set(x, 1, z0, S('or:canvas'))
            t.set(x, 2, z0, S('or:canvas'))
        t.set(x0 + 2, 3, z0, S('or:canvas'))
        column(t, x0 + 2, 1, 3, z0 + 5, fence('spruce_fence'))        # the front pole
        t.set(x0 + 2, 4, z0 + 5, AIR)
        t.set(x0 + 1, 1, z0 + 1, S('brown_bed', facing='north', part='head', occupied=False))
        t.set(x0 + 1, 1, z0 + 2, S('brown_bed', facing='north', part='foot', occupied=False))
        t.set(x0 + 3, 1, z0 + 1, S('green_bed', facing='north', part='head', occupied=False))
        t.set(x0 + 3, 1, z0 + 2, S('green_bed', facing='north', part='foot', occupied=False))
        t.set(x0 + 3, 1, z0 + 3, barrel('up'))
        t.set(x0 + 2, 3, z0 + 2, LANTERN_HUNG)
    # A lean-to of hides west of them, a fire between.
    t.set(14, 0, 19, S('cobblestone'))
    t.set(14, 1, 19, campfire(True, facing='east'))
    for z in (16, 17, 18, 19):
        t.set(7, 1, z, fence('spruce_fence'))
        t.set(7, 2, z, fence('spruce_fence'))
        t.set(7, 3, z, S('brown_wool'))
        t.set(8, 3, z, stairs('or:canvas_stairs', 'west'))
        t.set(8, 0, z, S('coarse_dirt'))
        t.set(8, 1, z, S('hay_block', axis='z') if z in (16, 17) else AIR)


def carts(t):
    """Two of the settlers' carts: one loaded with stolen hay and crystal, one smashed."""
    def cart(x0, z0, load):
        for x in (x0, x0 + 1):
            for z in range(z0, z0 + 3):
                t.set(x, 1, z, slab('spruce_slab', 'top'))
        t.set(x0 - 1, 1, z0 + 1, S('grindstone', face='wall', facing='west'))
        t.set(x0 + 2, 1, z0 + 1, S('grindstone', face='wall', facing='east'))
        for (x, z) in ((x0, z0), (x0 + 1, z0), (x0, z0 + 2), (x0 + 1, z0 + 2)):
            t.set(x, 2, z, fence('spruce_fence'))
        t.set(x0, 1, z0 - 1, fence('spruce_fence'))
        t.set(x0 + 1, 1, z0 - 1, fence('spruce_fence'))
        for (dx, y, dz, st) in load:
            t.set(x0 + dx, y, z0 + dz, st)
    cart(41, 22, [(0, 2, 1, S('hay_block', axis='z')), (1, 2, 1, S('or:cargo_crate')),
                  (1, 3, 1, S('amethyst_cluster', facing='up', waterlogged=False))])
    cart(19, 19, [(0, 2, 1, S('hay_block', axis='x')), (1, 2, 1, barrel('up'))])
    # The smashed one: its bed tipped on the ground, a wheel off, its load spilled.
    for (x, z, st) in ((17, 29, slab('spruce_slab')), (18, 29, slab('spruce_slab')), (17, 30, slab('spruce_slab')),
                       (18, 30, stairs('spruce_stairs', 'west')), (16, 30, S('grindstone', face='floor', facing='north')),
                       (19, 31, S('hay_block', axis='x')), (16, 28, S('or:crystal_veined_stone')),
                       (15, 29, S('or:cargo_crate'))):
        t.set(x, 1, z, st)
    # Hay stacked by the crag stairs: fuel for the signal fire.
    for (x, y, z) in ((22, 1, 38), (22, 1, 39), (23, 1, 39), (22, 2, 39)):
        t.set(x, y, z, S('hay_block', axis='y'))
    # Crystal crates the settlers dug, waiting for the buyers.
    for (x, z) in ((19, 26), (20, 26)):
        t.set(x, 1, z, S('or:cargo_crate'))
    t.set(19, 2, 26, S('amethyst_cluster', facing='up', waterlogged=False))


def signal_tower(t):
    """The signal tower on the crag: four posts, a platform nineteen blocks above the yard, a hay bale
    on it and the signal fire on the bale. Its ladder rises in the middle; the column over the fire is
    kept open for the smoke."""
    top = CRAG_TOP + 1                        # the crag's walking level
    px, pz = SIGNAL[0], SIGNAL[2] + 1         # the ladder's column
    for (x, z) in ((px - 1, pz - 1), (px + 1, pz - 1), (px - 1, pz + 1), (px + 1, pz + 1)):
        column(t, x, top, 17, z, log('stripped_spruce_log'))
    t.set(px, 13, pz + 1, log('spruce_log', 'x'))
    t.set(px - 1, 13, pz, log('spruce_log', 'z'))
    t.set(px + 1, 13, pz, log('spruce_log', 'z'))
    column(t, px, top, 17, pz - 1, S('spruce_planks'))
    for y in range(top, 19):
        t.set(px, y, pz, S('ladder', facing='south', waterlogged=False))
    for x in range(px - 2, px + 3):
        for z in range(pz - 2, pz + 3):
            if (x, z) != (px, pz):
                t.set(x, 18, z, S('spruce_planks'))
            if x in (px - 2, px + 2) or z in (pz - 2, pz + 2):
                t.set(x, 19, z, fence('spruce_fence'))
    t.set(px, 19, pz - 1, S('hay_block', axis='y'))
    t.set(*SIGNAL, campfire(True, signal=True, facing='south'))
    t.set(px - 1, 19, pz + 1, S('hay_block', axis='x'))
    t.set(px + 2, 19, pz, AIR)
    for y in range(SIGNAL[1] + 1, SIZE[1]):
        t.set(SIGNAL[0], y, SIGNAL[2], AIR)
    t.set(px + 2, 20, pz + 2, LANTERN)
    # A lantern post at the foot of the stairs.
    column(t, 21, 1, 2, 37, fence('spruce_fence'))
    t.set(21, 3, 37, LANTERN)


def anchors(t):
    for name, (x, y, z) in ANCHORS.items():
        if t.get(x, y - 1, z) is None or not t.is_solid(x, y - 1, z) or mod.walk_through(t.get(x, y - 1, z)):
            t.set(x, y - 1, z, S('coarse_dirt'))
        spot(t, name, x, y, z)
    t.anchor('SIGNAL_FIRE', *SIGNAL)


def raider_camp():
    t = Template(*SIZE, seed=SEED, name='story/raider_camp')
    ground(t)
    paths(t)
    crag(t)
    palisade(t)
    gatehouse(t)
    cook_fire(t)
    longhouse(t)
    cages(t)
    workshop(t)
    speeder(t)
    tents(t)
    carts(t)
    signal_tower(t)
    anchors(t)
    t.finish()
    feet = [k for k in ANCHORS]
    check(t, SIZE, feet=feet, blocks={'SIGNAL_FIRE': 'campfire'}, cells={(SIGNAL[0], SIGNAL[1] - 1, SIGNAL[2]): 'hay_block'})
    return t


def main(argv):
    prev = preview_dir(argv)
    t = raider_camp()
    write(t, 'story/raider_camp', prev, META, views=('se', 'nw'), plans=[1])


if __name__ == '__main__':
    main(sys.argv[1:])
