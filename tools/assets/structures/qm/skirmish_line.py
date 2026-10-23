#!/usr/bin/env python3
"""mission/skirmish_line and mission/skirmish_line_2 (docs/MILESTONE_6.md 5.4).

The patrol_intercept contract: a patrol of the other side (Republic or Imperial, by who gives the
contract) has crossed the treaty line and dug in. The player finds the squad (SQUAD_1..5) holding an old
border work. Neutral ground: both armies built here, both armies' scrap lies here. Both variants:
39 x 14 x 23, buried 2 (layer 1 is the ground the player walks up on, layer 0 the trench floors),
flat ground, anchors SQUAD_1..5 and HEART.

  skirmish_line    "The Treaty Line": a zig-zag trench with sandbag parapets and two dugouts at its
                   ends, wire and craters in front, the treaty pylon in no-man's-land (its west face
                   Republic white, its east face Imperial black), the squad's supplies behind the line.
  skirmish_line_2  "The Old Battery": a gun pit in an earth ring round the wreck of a heavy gun, its
                   barrel still laid on the line, a sunken magazine behind it reached by a crawl
                   trench, foxholes either side and a searchlight frame over it all.

Usage: python3 tools/assets/structures/qm/skirmish_line.py [--preview <folder>] [--only <id part>]
"""
import math

import qmkit as K
from qmkit import S, AIR, Template, B, Noise, mod

SIZE = (39, 14, 23)
BURIED = 2
ANCHORS = ['HEART', 'SQUAD_1', 'SQUAD_2', 'SQUAD_3', 'SQUAD_4', 'SQUAD_5']
META = dict(buried=BURIED, terrain='flat', max_slope=3, clearance=10, support_depth=8, ramp_rings=2, clear_above=6)

FLOOR = [(K.GRATE, 4), (K.DECK, 2), (K.PACKED_MUD, 2), (K.GRAVEL, 1)]
REVET = [(K.RUST, 3), (K.PLATING, 2), (K.PACKED_MUD, 3), (S('mud_bricks'), 2)]


def dig(t, cells, floor_y, floor, revet, sides=True):
    """A trench: the ground layer (floor_y + 1) taken out over `cells`, a floor laid at floor_y, the
    cut faces faced with plates and mud brick (revetment)."""
    cs = set(cells)
    for (x, z) in sorted(cs):
        t.set(x, floor_y + 1, z, AIR)
        t.set(x, floor_y, z, floor(x, floor_y, z))
    if sides:
        for (x, z) in sorted(cs):
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if (nx, nz) in cs or not t.inside(nx, floor_y + 1, nz):
                    continue
                if t.get(nx, floor_y + 1, nz) is None:
                    t.set(nx, floor_y + 1, nz, revet(nx, floor_y + 1, nz))
    return cs


def crater(t, cx, cz, r, G, F, rng):
    """A shell crater: the ground blown out one deep and scorched, a rim of thrown earth."""
    for x in range(int(cx - r - 2), int(cx + r + 3)):
        for z in range(int(cz - r - 2), int(cz + r + 3)):
            if not t.inside(x, G, z):
                continue
            d = math.hypot(x - cx, z - cz)
            if d <= r:
                t.set(x, G, z, AIR)
                t.set(x, G - 1, z, K.COARSE if rng.random() < 0.6 else K.GRAVEL)
                if rng.random() < 0.5:
                    t.set(x, G, z, K.SCORCH)
            elif d <= r + 1.3 and t.get(x, F, z) is None:
                t.set(x, G, z, K.COARSE if rng.random() < 0.6 else K.ROOTED)
                if rng.random() < 0.55:
                    t.set(x, F, z, K.slab('minecraft:mud_brick_slab') if rng.random() < 0.4 else (
                        K.COARSE if rng.random() < 0.5 else K.DEBRIS))


def wire(t, cells, F, rng):
    """Wire on stakes: bars between fence posts, a break here and there."""
    for i, (x, z) in enumerate(cells):
        if rng.random() < 0.15:
            continue
        if i % 3 == 0:
            t.set(x, F, z, K.fence('spruce_fence'))
            t.set(x, F + 1, z, K.rod())
        else:
            t.set(x, F, z, K.bars())
        K.support(t, x, F, z, K.COARSE)


# =============================================================================================
# skirmish_line: The Treaty Line
# =============================================================================================

def build_treaty_line():
    t = Template(*SIZE, seed=6701)
    rng = t.rng
    L, G, F = 0, 1, 2  # trench floors, the ground, the feet layer up top
    worn = K.Mix(K.WORN, 6703, scale=4.5, jitter=0.15)
    floor = K.Mix(FLOOR, 6705, 2.5, 0.3)
    revet = K.Mix(REVET, 6707, 2.2, 0.3)

    K.ground_blob(t, G, 19.0, 13.0, 18.0, 9.5, worn, 6709, fray=0.25)

    # --- the trench: bays of seven, stepping back and forth, from end to end ---------------------------
    trench = []
    for x in range(3, 36):
        bay = (x - 3) // 7
        z0 = 10 if bay % 2 == 0 else 12
        for z in range(z0, z0 + 3):
            trench.append((x, z))
    # the ends turn back toward the dugouts
    for (x0, x1) in ((3, 5), (33, 35)):
        for x in range(x0, x1 + 1):
            for z in range(13, 16):
                trench.append((x, z))
    cs = dig(t, trench, L, floor, revet)
    # the parapet (front, north) of sandbags two high, the parados (back) one high, with firing steps
    for (x, z) in sorted(cs):
        if (x, z - 1) not in cs and z - 1 >= 0:
            if t.get(x, F, z - 1) is None:
                K.sandbags(t, [(x, z - 1)], F, rng, high=0.8)
            if x % 5 == 0:
                t.set(x, G, z, K.slab('minecraft:mud_brick_slab'))  # a firing step
        if (x, z + 1) not in cs and t.inside(x, F, z + 1) and t.get(x, F, z + 1) is None and z + 1 < 16:
            if rng.random() < 0.6:
                K.sandbags(t, [(x, z + 1)], F, rng, high=0.15)
    # revetment posts along the front wall, a duckboard of grate down the middle
    for (x, z) in sorted(cs):
        if (x, z - 1) not in cs and x % 3 == 0:
            t.set(x, G, z - 1, K.beam('y'))
    # loopholes: plates with a slit in the parapet, a heavy repeater on its tripod in one bay
    for x in (8, 20, 30):
        z = min(z for (xx, z) in cs if xx == x)
        t.set(x, F, z - 1, K.RUST)
        t.set(x, F + 1, z - 1, K.bars())
        t.set(x, F + 2, z - 1, K.PLATING_SLAB)
    gx = 14
    gz = min(z for (xx, z) in cs if xx == gx)
    t.set(gx, G, gz, K.CRATE)
    t.set(gx, F, gz, K.pipes('z'))
    t.set(gx, F, gz - 1, K.plating_stairs('south', 'top'))
    t.set(gx + 1, G, gz, K.CRATE_REP)
    t.set(gx - 1, G, gz, K.CRATE_IMP)

    # --- the dugouts at both ends: sunk floors, plate walls, roofs under earth and sandbags ------------
    def dugout(x0, z0, x1, z1, door_x, lamp=K.lantern(True)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                wall = x in (x0, x1) or z in (z0, z1)
                t.set(x, L, z, K.GRATE if (x + z) % 3 == 0 else K.DECK)
                for y in (G, F, F + 1):
                    if wall:
                        t.set(x, y, z, K.beam('y') if (x in (x0, x1) and z in (z0, z1)) else (
                            K.RUST if (x * 3 + y + z) % 4 == 0 else K.PLATING))
                    else:
                        t.set(x, y, z, AIR)
                t.set(x, F + 2, z, K.PLATING_SLAB_TOP if not wall else K.PLATING)
                t.set(x, F + 3, z, K.COARSE if (x + z) % 3 else K.ROOTED)
                if not wall and rng.random() < 0.5:
                    K.sandbags(t, [(x, z)], F + 4, rng, high=0.0)
        for y in (G, F):
            t.set(door_x, y, z0, AIR)
        for xx in range(x0 - 1, x1 + 2):  # the mound's edge steps down to the ground
            for zz in (z0 - 1, z1 + 1):
                if t.inside(xx, F, zz) and t.get(xx, F, zz) is None and not (xx, zz) in cs:
                    t.set(xx, F, zz, K.COARSE if (xx + zz) % 2 else K.slab('minecraft:mud_brick_slab'))
        for zz in range(z0, z1 + 1):
            for xx in (x0 - 1, x1 + 1):
                if t.inside(xx, F, zz) and t.get(xx, F, zz) is None and (xx, zz) not in cs:
                    t.set(xx, F, zz, K.COARSE if (xx + zz) % 2 else K.slab('minecraft:mud_brick_slab'))
        # bunks, a crate table, a lamp
        t.set(x0 + 1, G, z1 - 1, K.PLATING_SLAB_TOP)
        t.set(x0 + 1, F, z1 - 1, K.carpet('gray'))
        t.set(x0 + 2, G, z1 - 1, K.PLATING_SLAB_TOP)
        t.set(x0 + 2, F, z1 - 1, K.carpet('gray'))
        t.set(x1 - 1, G, z1 - 1, K.CRATE)
        t.set(x1 - 1, F, z1 - 1, lamp)
        t.set(x1 - 1, G, z0 + 1, K.CRATE_REP if x0 < 19 else K.CRATE_IMP)
        mod.light_fill(t, (x0 + 1, G, z0 + 1, x1 - 1, F, z1 - 1))

    dugout(2, 16, 8, 21, 4)
    dugout(30, 16, 36, 21, 34)
    for (x, z) in ((4, 16),):
        pass

    # --- no-man's-land: wire, craters, the treaty pylon -------------------------------------------------
    wire(t, [(x, 7) for x in range(1, 12)] + [(x, 6) for x in range(24, 38)], F, rng)
    wire(t, [(x, 8) for x in range(13, 17)], F, rng)
    crater(t, 8.5, 3.5, 1.6, G, F, rng)
    crater(t, 29.0, 3.0, 1.9, G, F, rng)
    crater(t, 13.0, 1.0, 1.1, G, F, rng)
    K.scorch_burst(t, F, 24.0, 8.5, 2.4, rng, density=0.4, debris=0.4)
    # the pylon: a plinth, a shaft whose west face is Republic white and east face Imperial black
    px, pz = 20, 3
    for x in range(px - 1, px + 2):
        for z in range(pz - 1, pz + 2):
            t.set(x, G, z, K.RUIN)
            t.set(x, F, z, K.RUIN_CARVED if (x + z) % 2 else K.RUIN)
            t.set(x, F + 1, z, K.slab('or:ruin_brick_slab'))
    for y in range(F + 1, F + 10):
        t.set(px, y, pz, K.PLATING if y % 3 else K.VENT)
        if y <= F + 8:
            t.set(px - 1, y, pz, S('or:republic_plating') if y > F + 1 else K.slab('or:republic_plating_slab', 'top'))
            t.set(px + 1, y, pz, S('or:imperial_plating') if y > F + 1 else K.slab('or:imperial_plating_slab', 'top'))
    t.set(px - 1, F + 1, pz, S('or:republic_plating'))
    t.set(px + 1, F + 1, pz, S('or:imperial_plating'))
    t.set(px, F + 10, pz, K.LIGHT_AMBER)
    t.set(px, F + 11, pz, K.rod())
    for (x, y) in ((px - 1, F + 5), (px + 1, F + 3), (px + 1, F + 7)):
        t.set(x, y, pz, K.SCORCHED)  # blaster scars
    t.set(px - 1, F + 9, pz, S('or:republic_plating_stairs', facing='east', half='bottom', shape='straight', waterlogged=False))
    t.set(px + 1, F + 9, pz, S('or:imperial_plating_stairs', facing='west', half='bottom', shape='straight', waterlogged=False))
    # old war scrap in no-man's-land: a plate of each side's armour half sunk in the mud
    for (x, z, s) in ((5, 2, K.REP_PLATING), (6, 2, S('or:republic_plating_stairs', facing='north', half='bottom', shape='straight', waterlogged=False)),
                      (33, 9, K.IMP_PLATING), (34, 9, S('or:imperial_plating_stairs', facing='west', half='bottom', shape='straight', waterlogged=False)),
                      (26, 1, K.RUST), (16, 5, K.DEBRIS)):
        t.set(x, F, z, s)
        K.support(t, x, F, z)

    # --- behind the line: the squad's supplies under a tarp, the comm set, a cook pot ---------------------
    K.lean_to(t, 14, 17, 22, 19, F, 'south', 3)
    for x in range(14, 23):
        for z in range(17, 20):
            t.set(x, G, z, K.PACKED_MUD if (x + z) % 4 else K.GRAVEL)
    K.crate_pile(t, [(15, 19, 2), (16, 19, 1), (17, 19, 2), (20, 19, 1), (21, 19, 2)], F, rng,
                 kinds=((K.CRATE, 4), (K.CRATE_REP, 1), (K.CRATE_IMP, 1)), tarp=0.2)
    t.set(19, F, 19, K.PLATING_SLAB_TOP)
    t.set(19, F + 1, 19, K.holo('north'))
    t.set(18, F, 19, K.console('north'))
    K.mast(t, 24, F, 19, 7, top=K.LIGHT_RED, arms=((5, 'west', 1), (6, 'east', 1)))
    t.set(24, G, 19, K.GRAVEL)
    t.set(12, F, 19, K.campfire(lit=True))
    t.set(12, G, 19, K.GRAVEL)
    t.set(12, F + 1, 19, K.chain('y')) if False else None
    t.set(11, F, 19, K.log(axis='z'))
    t.set(13, F, 20, S('cauldron'))
    # duckboards out of the trench to the supply line
    for z in (13, 14, 15, 16):
        if (19, z) not in cs:
            t.set(19, G, z, K.GRATE if z % 2 else K.DECK)

    # --- people ------------------------------------------------------------------------------------------
    K.spot(t, 'SQUAD_1', 11, G, 13)    # in the trench, west bays
    K.spot(t, 'SQUAD_2', 16, G, 13)
    K.spot(t, 'SQUAD_3', 26, G, 13)    # east bays
    K.spot(t, 'SQUAD_4', 34, G, 14)    # at the east dugout's mouth
    K.spot(t, 'SQUAD_5', 17, F, 16)    # at the supplies
    K.spot(t, 'HEART', 20, G, 11)      # the middle of the line

    K.litter(t, F, (0, 0, 38, 9), [(K.DEBRIS, 3), (K.SCORCH, 2)], 0.03, rng)
    return t


def build_battery():
    t = Template(*SIZE, seed=6801)
    rng = t.rng
    L, G, F = 0, 1, 2
    worn = K.Mix(K.WORN, 6803, scale=4.5, jitter=0.15)
    floor = K.Mix(FLOOR, 6805, 2.5, 0.3)
    revet = K.Mix(REVET, 6807, 2.2, 0.3)
    earth = K.Mix([(K.COARSE, 4), (K.ROOTED, 2), (K.PACKED_MUD, 2), (K.GRAVEL, 1)], 6809, 2.4, 0.3)
    bn = Noise(6811, 2.6)

    K.ground_blob(t, G, 19.0, 12.0, 18.0, 10.0, worn, 6813, fray=0.25)

    # --- the gun pit: a sunk floor inside a ring of earth two high, sandbags on the crest -----------------
    cx, cz = 19.0, 10.0
    pit = [(x, z) for x in range(12, 27) for z in range(3, 18) if math.hypot(x - cx, z - cz) <= 5.2]
    crawl = [(x, z) for x in (18, 19, 20) for z in range(15, 18)]
    cs = dig(t, pit + crawl, L, floor, revet)
    for x in range(10, 29):
        for z in range(1, 20):
            d = math.hypot(x - cx, z - cz)
            if (x, z) in cs or not (5.3 < d <= 7.6):
                continue
            if abs(x - cx) <= 1 and z < cz:
                continue  # the gap the barrel looks out of
            if abs(x - cx) <= 1.5 and z > cz:
                continue  # the way back to the magazine
            h = 2 if d < 6.6 else 1
            if bn.at(x, 0, z) > 0.7:
                h -= 1
            for k in range(h):
                t.set(x, F + k, z, earth(x, F + k, z))
            if h == 2 and rng.random() < 0.45:
                K.sandbags(t, [(x, z)], F + 2, rng, high=0.0)
            t.set(x, G, z, K.COARSE)
    # --- the gun: a turntable, the housing, the barrel laid north and snapped ----------------------------
    gx, gz = 19, 10
    for x in range(gx - 2, gx + 3):
        for z in range(gz - 2, gz + 3):
            if abs(x - gx) + abs(z - gz) <= 3:
                t.set(x, L, z, K.PLATING if (x + z) % 2 else K.DECK)
    for x in range(gx - 1, gx + 2):
        for z in range(gz - 1, gz + 2):
            t.set(x, G, z, K.PLATING if (x, z) != (gx, gz) else K.pipes('y'))
            t.set(x, F, z, K.WRECK if (x + z) % 2 else K.PLATING)
            t.set(x, F + 1, z, K.plating_stairs('south') if z == gz - 1 else (K.VENT if z == gz + 1 else K.SCORCHED))
    t.set(gx, F + 2, gz, K.PLATING_SLAB)
    t.set(gx - 1, F + 2, gz + 1, K.pipes('x'))
    t.set(gx + 1, F + 2, gz + 1, K.rod('up'))
    for z in range(gz - 6, gz - 1):
        t.set(gx, F + 1, z, K.pipes('z'))
    t.set(gx, F + 1, gz - 6, K.SCORCHED)
    t.set(gx, F, gz - 7, K.DEBRIS)
    t.set(gx - 1, F, gz - 9, K.pipes('z'))
    t.set(gx - 1, F, gz - 8, K.pipes('z'))
    t.set(gx - 1, F, gz - 10, K.VENT) if gz - 10 >= 0 else None
    K.support(t, gx - 1, F, gz - 9)
    K.support(t, gx - 1, F, gz - 8)
    # spent shells heaped by the gun, a rack of live ones
    for (x, z, f) in ((gx - 3, gz + 2, 'east'), (gx - 3, gz + 3, 'north'), (gx - 2, gz + 3, 'east'), (gx + 3, gz + 2, 'west')):
        t.set(x, G, z, K.rod(f))
    t.set(gx + 3, G, gz - 1, K.CRATE)
    t.set(gx + 3, G, gz, K.CRATE)
    t.set(gx + 3, F, gz - 1, K.rod('up'))
    t.set(gx + 3, F, gz, K.rod('up'))

    # --- the magazine behind, sunk, under earth; the crawl trench in ------------------------------------
    MX0, MX1, MZ0, MZ1 = 14, 24, 17, 21
    for x in range(MX0, MX1 + 1):
        for z in range(MZ0, MZ1 + 1):
            wall = x in (MX0, MX1) or z in (MZ0, MZ1)
            t.set(x, L, z, K.DECK if (x + z) % 3 else K.GRATE)
            for y in (G, F, F + 1):
                if wall:
                    t.set(x, y, z, K.beam('y') if (x in (MX0, MX1) and z in (MZ0, MZ1)) else (
                        K.RUIN_BRICKS if y == G else (K.PLATING if (x + y) % 3 else K.RUST)))
                else:
                    t.set(x, y, z, AIR)
            t.set(x, F + 2, z, K.PLATING_SLAB_TOP if not wall else K.PLATING)
            if not wall:
                t.set(x, F + 3, z, earth(x, F + 3, z))
                if rng.random() < 0.3:
                    t.set(x, F + 4, z, K.slab('minecraft:mud_brick_slab'))
    for x in (18, 19, 20):
        for y in (G, F):
            t.set(x, y, MZ0, AIR)
    t.set(17, F + 1, MZ0, K.LIGHT_AMBER)
    for (x, h) in ((15, 2), (16, 1), (22, 2), (23, 2)):
        K.crate_pile(t, [(x, MZ1 - 1, h)], G, rng, kinds=((K.CRATE, 4), (K.CRATE_REP, 1), (K.CRATE_IMP, 1)))
    for x in (17, 18, 20, 21):
        t.set(x, G, MZ1 - 1, K.PLATING_SLAB_TOP)
        t.set(x, F, MZ1 - 1, K.rod('up'))
    t.set(15, G, MZ0 + 1, K.CRATE_IMP)
    t.set(23, G, MZ0 + 1, K.CRATE_REP)
    t.set(19, F + 1, 19, K.lantern(True))
    mod.light_fill(t, (MX0 + 1, G, MZ0 + 1, MX1 - 1, F, MZ1 - 1))

    # --- foxholes on both flanks, wire and craters out front ---------------------------------------------
    for (fx, fz) in ((5, 8), (9, 15), (31, 6), (34, 14)):
        hole = [(fx, fz), (fx + 1, fz), (fx, fz + 1), (fx + 1, fz + 1)]
        dig(t, hole, L, floor, revet, sides=True)
        for x in range(fx - 1, fx + 3):
            for z in range(fz - 1, fz + 3):
                if (x, z) not in hole and t.get(x, F, z) is None and rng.random() < 0.8:
                    K.sandbags(t, [(x, z)], F, rng, high=0.3)
    wire(t, [(x, 1) for x in range(0, 15)] + [(x, 2) for x in range(24, 39)], F, rng)
    crater(t, 9.0, 3.0, 1.5, G, F, rng)
    crater(t, 29.5, 10.5, 1.4, G, F, rng)
    crater(t, 3.5, 13.0, 1.2, G, F, rng)

    # --- the searchlight frame on the east flank ----------------------------------------------------------
    sx, sz = 30, 17
    for (x, z) in ((sx, sz), (sx + 2, sz), (sx, sz + 2), (sx + 2, sz + 2)):
        K.column(t, x, F, F + 6, z, K.beam('y'))
        t.set(x, G, z, K.GRAVEL)
    for x in range(sx, sx + 3):
        for z in range(sz, sz + 3):
            if t.get(x, F + 7, z) is None:
                t.set(x, F + 7, z, K.GRATE)
    for k in (2, 4):
        t.set(sx + 1, F + k, sz, K.beam('x'))
        t.set(sx + 1, F + k, sz + 2, K.beam('x'))
    for y in range(F, F + 8):
        t.set(sx + 1, y, sz + 3, K.ladder('south'))
    t.set(sx + 1, F + 7, sz + 2, AIR)
    t.set(sx + 1, F + 7, sz + 3, K.ladder('south'))
    t.set(sx + 1, F + 8, sz + 1, K.PLATING)
    t.set(sx + 1, F + 8, sz, K.LIGHT)
    t.set(sx + 1, F + 9, sz + 1, K.PLATING_SLAB)
    for (x, z) in ((sx, sz), (sx + 2, sz), (sx, sz + 2), (sx + 2, sz + 2)):
        t.set(x, F + 8, z, K.bars())

    # --- the burnt supply speeder on the west, a field kitchen ------------------------------------------
    for z in range(16, 21):
        for x in (3, 4, 5):
            t.set(x, F, z, K.PLATING_SLAB_TOP if (x + z) % 3 else K.SCORCHED)
    t.set(4, F, 15, K.plating_stairs('south', 'top'))
    t.set(3, F + 1, 18, K.CRATE)
    t.set(5, F + 1, 19, K.CRATE_IMP)
    t.set(4, F + 1, 16, K.SCORCHED)
    t.set(2, F, 17, K.pipes('z'))
    t.set(6, F, 19, K.pipes('z'))
    K.scorch_burst(t, F, 4.0, 18.0, 2.5, rng, density=0.5)
    t.set(10, F, 20, K.campfire(lit=True))
    t.set(10, G, 20, K.GRAVEL)
    t.set(11, F, 20, K.log(axis='z'))
    t.set(9, F, 21, S('cauldron'))
    t.set(10, F, 21, K.CRATE)
    K.lamp_post(t, 12, F, 18, height=3, arm='west')

    # --- people ------------------------------------------------------------------------------------------
    K.spot(t, 'SQUAD_1', 16, G, 9)     # in the gun pit
    K.spot(t, 'SQUAD_2', 5, G, 8)      # the west foxhole
    K.spot(t, 'SQUAD_3', 32, G, 7)     # the east foxhole
    K.spot(t, 'SQUAD_4', 19, G, 19)    # in the magazine
    K.spot(t, 'SQUAD_5', 29, F, 16)    # under the searchlight
    K.spot(t, 'HEART', 22, G, 11)      # beside the gun

    K.litter(t, F, (0, 0, 38, 4), [(K.DEBRIS, 3), (K.SCORCH, 2)], 0.04, rng)
    return t


def main():
    a = K.args()
    for tid, build in (('mission/skirmish_line', build_treaty_line), ('mission/skirmish_line_2', build_battery)):
        if a.only and a.only not in tid:
            continue
        K.save(build(), tid, ANCHORS, META)


if __name__ == '__main__':
    main()
