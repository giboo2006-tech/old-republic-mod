#!/usr/bin/env python3
"""mission/smuggler_cache and mission/smuggler_cache_2 (docs/MILESTONE_6.md 5.4).

The Republic's "Credits from the Core" (r2_credits_from_the_core): the stolen relief chits lead to a
smugglers' dugout and a crate with a record inside. The chief (CHIEF) stands before the way in; the
locked crate (CRATE, a barrel) is at the back of the cache; four crew (CREW_1..4) about the place.
Both variants: 27 x 14 x 27, buried 3 (layers 0 to 2 underground; the cache's floor is layer 0, the
ground the player walks up on is layer 2), low ground (a valley floor, a creek bank).

  smuggler_cache    "The Dugout": a cellar dug into the valley floor and roofed with beams and earth, a
                    mound with a shored ramp down into it under a canvas awning, a stovepipe through
                    the turf. Inside: the hold of crates with the locked one at the back, the bunk room,
                    the card table. Outside: a skiff under netting, a lookout's hide, a cook fire.
  smuggler_cache_2  "The Buried Container": a long cargo container sunk into the ground to its roof,
                    the cache inside; a hatch and ladder at one end, a ramp cut down to its doors at the
                    other; a beam crane and a hauler on the surface, the crew's camp round a fire.

Usage: python3 tools/assets/structures/qm/smuggler_cache.py [--preview <folder>] [--only <id part>]
"""
import math

import qmkit as K
from qmkit import S, AIR, Template, B, Noise, mod

SIZE = (27, 14, 27)
BURIED = 3
ANCHORS = ['CHIEF', 'CRATE', 'CREW_1', 'CREW_2', 'CREW_3', 'CREW_4', 'HEART']
META = dict(buried=BURIED, terrain='low', max_slope=4, clearance=10, support_depth=8, ramp_rings=2, clear_above=6)

EARTH = [(K.COARSE, 4), (K.ROOTED, 2), (K.PACKED_MUD, 2), (K.DIRT, 2), (K.GRAVEL, 1)]
SHORE = [(K.PLATING, 2), (K.RUST, 2), (S('stripped_spruce_log', axis='y'), 3), (S('spruce_planks'), 2)]


def barrel():
    return S('barrel', facing='up', open=False)


def skiff(t, x, y, z, rng, length=7):
    """A cargo skiff hovering half a block up, nose north (-z), under a sagging net of canvas."""
    for dz in range(length):
        for dx in range(3):
            edge = dx in (0, 2)
            t.set(x + dx, y, z + dz, K.PLATING_SLAB_TOP if not edge or dz in (0, length - 1) else K.plating_stairs(
                'east' if dx == 0 else 'west', 'top'))
    t.set(x + 1, y, z - 1, K.plating_stairs('south', 'top'))
    t.set(x + 1, y + 1, z + 1, K.console('south'))
    t.set(x, y + 1, z + 1, K.rod('up'))
    for dz in range(3, length):
        if rng.random() < 0.6:
            t.set(x + 1, y + 1, z + dz, K.CRATE if rng.random() < 0.7 else K.CRATE_REP)
    t.set(x + 1, y, z + length, K.VENT)
    # the net: canvas slabs on poles, sagging, holes
    for (px, pz) in ((x - 1, z), (x + 3, z), (x - 1, z + length - 1), (x + 3, z + length - 1)):
        K.column(t, px, y, y + 2, pz, K.fence('spruce_fence'))
    for dz in range(-1, length + 1):
        for dx in range(-1, 4):
            if rng.random() < 0.82:
                mid = 0 <= dx <= 2 and 1 <= dz <= length - 2
                t.set(x + dx, y + 3 if not mid else y + 2, z + dz,
                      K.CANVAS_SLAB if not mid else K.CANVAS_SLAB_TOP)


# =============================================================================================
# smuggler_cache: The Dugout
# =============================================================================================

def build_dugout():
    t = Template(*SIZE, seed=7101)
    rng = t.rng
    L, G, F = 0, 2, 3   # the cache floor, the ground, the feet layer up top
    worn = K.Mix(K.WORN, 7103, scale=4.5, jitter=0.15)
    earth = K.Mix(EARTH, 7105, 2.4, 0.3)
    shore = K.Mix(SHORE, 7107, 1.8, 0.3)

    K.ground_blob(t, G, 13.0, 10.0, 11.0, 9.0, worn, 7109, fray=0.25)
    K.trail(t, G, [(12, -1), (12, 3), (12, 6)], 3, K.Mix(K.TRACK, 7111, 3.5, 0.2), 7113)

    # --- the cellar: rooms dug to layer 0, shored walls, a beam-and-plate ceiling at y 4 ----------------------
    X0, X1, Z0, Z1 = 5, 21, 12, 23
    HOLD = (5, 15)       # the hold's x range; the bunk room beyond a partition at 16
    CEIL = 4
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            wall = x in (X0, X1) or z in (Z0, Z1)
            t.set(x, L - 0, z, K.PACKED_MUD if wall else (S('spruce_planks') if (x + z) % 5 else K.DECK))
            for y in range(1, CEIL):
                if wall:
                    post = (x - X0) % 4 == 0 or (z - Z0) % 4 == 0 and x in (X0, X1)
                    t.set(x, y, z, K.beam('y') if post and (x in (X0, X1) or z in (Z0, Z1)) and (x + z) % 4 == 0 else shore(x, y, z))
                else:
                    t.set(x, y, z, AIR)
            t.set(x, CEIL, z, K.beam('x') if (z - Z0) % 3 == 0 else (S('spruce_planks') if (x + z) % 3 else K.PLATING))
    # the partition between hold and bunk room, a doorway
    for z in range(Z0 + 1, Z1):
        for y in range(1, CEIL):
            if z in (17, 18) and y < 3:
                continue
            t.set(16, y, z, shore(16, y, z))
    # the mound over it: earth over the roof, domed in the middle, stepping down to the ground round it
    mcx, mcz = (X0 + X1) / 2.0, (Z0 + Z1) / 2.0
    mn = Noise(7115, 2.6)
    for x in range(X0 - 3, X1 + 4):
        for z in range(Z0 - 3, Z1 + 4):
            if not t.inside(x, F, z):
                continue
            d = max(X0 - x, x - X1, Z0 - z, z - Z1, 0)
            e = ((x - mcx) / ((X1 - X0) / 2.0 + 0.5)) ** 2 + ((z - mcz) / ((Z1 - Z0) / 2.0 + 0.5)) ** 2
            v = mn.at(x, 0, z)
            if d == 0:
                t.set(x, CEIL + 1, z, earth(x, CEIL + 1, z))
                if e + (v - 0.5) * 0.3 < 0.62:
                    t.set(x, CEIL + 2, z, earth(x, CEIL + 2, z))
                    if e + (v - 0.5) * 0.3 < 0.22:
                        t.set(x, CEIL + 3, z, K.slab('minecraft:mud_brick_slab') if v > 0.55 else earth(x, CEIL + 3, z))
                elif v > 0.5:
                    t.set(x, CEIL + 2, z, K.slab('minecraft:mud_brick_slab'))
            elif d == 1:
                t.set(x, F, z, earth(x, F, z))
                t.set(x, F + 1, z, earth(x, F + 1, z) if v > 0.35 else K.slab('minecraft:mud_brick_slab'))
                if v > 0.6:
                    t.set(x, F + 2, z, K.slab('minecraft:mud_brick_slab'))
            elif d == 2:
                t.set(x, F, z, earth(x, F, z))
                if v > 0.45:
                    t.set(x, F + 1, z, K.slab('minecraft:mud_brick_slab'))
            elif d == 3 and v > 0.4:
                t.set(x, F, z, K.slab('minecraft:mud_brick_slab'))
    # a tarp pegged over part of the mound, a periscope of pipe and bars
    for x in range(14, 19):
        for z in range(18, 22):
            top = max(y for y in range(CEIL + 1, CEIL + 4) if t.get(x, y, z) not in (None, AIR))
            if (x + z) % 4:
                t.set(x, top + 1, z, K.CANVAS_SLAB)
    t.set(8, CEIL + 3, 14, K.pipes('y'))
    t.set(8, CEIL + 4, 14, K.bars())
    # --- the ramp down from the north, shored, under a canvas awning; the door frame ------------------------
    RX0, RX1 = 11, 13
    for k, z in enumerate(range(Z0 - 5, Z0 + 1)):
        floor_y = max(L, G - k // 1 + 0) if False else max(L, G - (k * 2) // 3)
        for x in range(RX0, RX1 + 1):
            for y in range(floor_y + 1, CEIL + 3):
                t.set(x, y, z, AIR)
            t.set(x, floor_y, z, K.stairs('minecraft:mud_brick_stairs', 'south') if False else (
                K.PACKED_MUD if floor_y > L else S('spruce_planks')))
        for x in (RX0 - 1, RX1 + 1):
            for y in range(floor_y, CEIL + 1):
                t.set(x, y, z, K.beam('y') if (z % 2 == 0) else shore(x, y, z))
    # stairs where the ramp drops a level
    for k, z in enumerate(range(Z0 - 5, Z0 + 1)):
        fy = max(L, G - (k * 2) // 3)
        nfy = max(L, G - ((k + 1) * 2) // 3)
        if nfy < fy:
            for x in range(RX0, RX1 + 1):
                t.set(x, fy, z, K.stairs('minecraft:mud_brick_stairs', 'north'))
    for x in range(RX0 - 1, RX1 + 2):
        t.set(x, CEIL, Z0, K.beam('x'))
    for y in range(1, CEIL):
        t.set(RX0 - 1, y, Z0, K.beam('y'))
        t.set(RX1 + 1, y, Z0, K.beam('y'))
    for x in range(RX0, RX1 + 1):
        for y in range(1, CEIL):
            t.set(x, y, Z0, AIR)
    # the awning over the ramp's head on four poles
    for (x, z) in ((RX0 - 1, Z0 - 6), (RX1 + 1, Z0 - 6)):
        K.column(t, x, F, F + 2, z, K.beam('y'))
    for x in range(RX0 - 1, RX1 + 2):
        for z in range(Z0 - 6, Z0 - 1):
            t.set(x, F + 3 if z < Z0 - 3 else F + 3, z, K.CANVAS_SLAB if (x + z) % 4 else K.CANVAS)
    t.set(RX1 + 1, F + 2, Z0 - 5, K.lantern(True)) if False else None
    t.set(RX0 - 1, F + 3, Z0 - 6, K.lantern())

    # --- inside: the hold of crates, the locked one at the back; bunks; the card table; the stove ------------------
    crates = [(6, 22, 3), (7, 22, 2), (8, 22, 3), (6, 21, 2), (12, 22, 2), (13, 22, 3), (14, 22, 2), (14, 21, 1),
              (6, 13, 2), (7, 13, 1), (14, 13, 2), (6, 14, 1)]
    K.crate_pile(t, crates, 1, rng, kinds=((K.CRATE, 4), (K.CRATE_REP, 2), (K.CRATE_IMP, 1)), tarp=0.25)
    cx_, cz_ = 10, 22
    t.set(cx_, 1, cz_, barrel())
    t.anchor('CRATE', cx_, 1, cz_)
    t.set(9, 1, cz_, K.chain('x'))
    t.set(11, 1, cz_, K.chain('x'))
    t.set(cx_, 2, cz_, K.CANVAS_SLAB)
    t.set(cx_, 3, cz_, K.lantern(True))
    t.set(9, 2, cz_, K.LIGHT_AMBER) if False else None
    # the card table and its stools
    for (x, z) in ((10, 17), (11, 17)):
        t.set(x, 1, z, K.PLATING_SLAB_TOP)
    t.set(10, 2, 17, K.carpet('green'))
    t.set(11, 2, 17, K.candle(2, lit=True))
    for (x, z) in ((9, 17), (12, 17), (10, 16)):
        t.set(x, 1, z, K.CANVAS_SLAB)
    # the bunk room
    for z in (14, 16, 20):
        t.set(20, 1, z, K.PLATING_SLAB_TOP)
        t.set(20, 2, z, K.carpet('brown' if z % 4 else 'gray'))
        t.set(19, 1, z, K.PLATING_SLAB_TOP)
        t.set(19, 2, z, K.carpet('brown' if z % 4 else 'gray'))
    t.set(18, 1, 22, K.CRATE)
    t.set(18, 2, 22, K.lantern())
    t.set(20, 1, 22, S('cauldron'))
    # the stove and its pipe through the turf
    t.set(17, 1, 13, S('blast_furnace', facing='south', lit=False))
    for y in range(2, CEIL + 3):
        t.set(17, y, 13, K.pipes('y'))
    t.set(17, CEIL + 3, 13, K.campfire(lit=True, signal=False)) if False else None
    t.set(17, CEIL + 3, 13, K.VENT)
    for (x, z) in ((8, 17), (14, 18), (18, 18)):
        t.set(x, 3, z, K.lantern(True))
    mod.light_fill(t, (X0 + 1, 1, Z0 + 1, X1 - 1, 2, Z1 - 1))

    # --- the surface: the skiff under its net, the lookout's hide, the cook fire, a crate stack ----------------------
    skiff(t, 3, F, 3, rng)
    hx, hz = 21, 6
    for (x, z) in ((20, 5), (21, 5), (22, 5), (20, 6), (22, 6), (20, 7), (22, 7)):
        K.sandbags(t, [(x, z)], F, rng, high=0.7)
        t.set(x, G, z, K.COARSE)
    for x in range(20, 23):
        for z in range(5, 9):
            if (x, z) != (21, 6):
                pass
    for (x, z) in ((20, 8), (22, 8)):
        K.column(t, x, F, F + 1, z, K.fence('spruce_fence'))
    for x in range(20, 23):
        for z in range(6, 9):
            t.set(x, F + 2, z, K.CANVAS_SLAB if (x + z) % 3 else K.CANVAS)
    fx, fz = 17, 8
    K.fire_pit(t, fx, F, fz, rng, spit='x', stones=0.6)
    t.set(fx - 1, F, fz + 2, K.log(axis='x'))
    t.set(fx, F, fz + 2, K.log(axis='x'))
    t.set(fx + 2, F, fz - 1, K.CRATE)
    K.crate_pile(t, [(8, 9, 2), (9, 9, 1), (8, 10, 1)], F, rng, kinds=((K.CRATE, 3), (K.CRATE_REP, 1)), tarp=0.5)
    K.lamp_post(t, 15, F, 6, height=3, arm='west')
    # the landing light: a tall pole by the mound with an amber lamp to bring skiffs in at night
    K.mast(t, 24, F, 15, 9, top=K.LIGHT_AMBER, arms=((6, 'west', 1), (8, 'north', 1)))
    t.set(24, G, 15, K.GRAVEL)
    t.set(23, F + 5, 15, K.lantern(True))
    for (x, z) in ((23, 16), (25, 14)):
        t.set(x, F, z, K.CRATE)

    # --- people ------------------------------------------------------------------------------------------
    K.spot(t, 'CHIEF', 12, F, Z0 - 7)        # before the way down
    K.spot(t, 'CREW_1', 21, F, 6)            # in the lookout's hide
    K.spot(t, 'CREW_2', 17, F, 6)            # at the fire
    K.spot(t, 'CREW_3', 11, 1, 19)           # in the hold
    K.spot(t, 'CREW_4', 18, 1, 15)           # in the bunk room
    K.spot(t, 'HEART', 12, F, Z0 - 9)        # the yard before the mound

    K.litter(t, F, (0, 0, 26, Z0 - 3), [(K.DEBRIS, 3)], 0.02, rng)
    return t


def build_container():
    t = Template(*SIZE, seed=7201)
    rng = t.rng
    L, G, F = 0, 2, 3
    worn = K.Mix(K.WORN, 7203, scale=4.5, jitter=0.15)
    earth = K.Mix(EARTH, 7205, 2.4, 0.3)
    shore = K.Mix(SHORE, 7207, 1.8, 0.3)
    hull = K.Mix([(K.CRATE, 5), (K.RUST, 2), (K.PLATING, 1)], 7209, 2.0, 0.3)

    K.ground_blob(t, G, 13.0, 13.0, 12.0, 12.0, worn, 7211, fray=0.25)
    K.trail(t, G, [(11, -1), (11, 2)], 3, K.Mix(K.TRACK, 7213, 3.5, 0.2), 7215)

    # --- the container, sunk to its shoulders, doors north ----------------------------------------------
    X0, X1, Z0, Z1 = 8, 14, 8, 21
    TOP = 4
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            wall = x in (X0, X1) or z in (Z0, Z1)
            t.set(x, L, z, K.PLATING if wall else (K.DECK if (x + z) % 4 else K.GRATE))
            for y in range(1, TOP):
                if wall:
                    corner = x in (X0, X1) and z in (Z0, Z1)
                    rib = x in (X0, X1) and (z - Z0) % 3 == 0
                    t.set(x, y, z, K.beam('y') if corner or rib else hull(x, y, z))
                else:
                    t.set(x, y, z, AIR)
            t.set(x, TOP, z, K.beam('x') if z in (Z0, Z1) else (K.PLATING if (x in (X0, X1)) else (
                K.RUST if (x * 3 + z) % 5 == 0 else K.PLATING_SLAB_TOP)))
    # its doors: the north end open, one leaf swung back against the cut
    for x in range(X0 + 1, X1):
        for y in range(1, TOP):
            t.set(x, y, Z0, AIR)
    for y in range(1, TOP):
        t.set(X0 + 1, y, Z0 - 1, K.PLATING) if False else None
    # earth banked against both flanks and the south end up to the roof's edge, some on the roof
    for x in range(X0 - 3, X1 + 4):
        for z in range(Z0, Z1 + 4):
            if not t.inside(x, F, z):
                continue
            d = max(X0 - x, x - X1, z - Z1, 0)
            if d == 0:
                continue
            v = Noise(7217, 2.4).at(x, 0, z)
            if d == 1:
                t.set(x, F, z, earth(x, F, z))
                if v > 0.4:
                    t.set(x, F + 1, z, K.slab('minecraft:mud_brick_slab'))
            elif d == 2:
                t.set(x, F, z, earth(x, F, z) if v > 0.35 else K.slab('minecraft:mud_brick_slab'))
            elif d == 3 and v > 0.5:
                t.set(x, F, z, K.slab('minecraft:mud_brick_slab'))
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 3, Z1 - 3):
            if (x + z) % 3 == 0 or Noise(7219, 2.0).at(x, 0, z) > 0.6:
                t.set(x, TOP + 1, z, K.CANVAS_SLAB if (x + z) % 2 else K.slab('minecraft:mud_brick_slab'))
    # --- the cut down to the doors: shored sides, stairs -------------------------------------------------
    RX0, RX1 = X0 + 1, X1 - 1
    levels = {Z0 - 1: L, Z0 - 2: L, Z0 - 3: 1, Z0 - 4: 2, Z0 - 5: G}
    for z, fy in levels.items():
        for x in range(RX0, RX1 + 1):
            t.set(x, fy, z, S('spruce_planks') if fy == L else K.stairs('minecraft:mud_brick_stairs', 'north'))
            if fy == G:
                t.set(x, fy, z, K.PACKED_MUD)
            for y in range(fy + 1, TOP + 2):
                t.set(x, y, z, AIR)
        for x in (RX0 - 1, RX1 + 1):
            for y in range(fy, F + 1):
                t.set(x, y, z, K.beam('y') if z % 2 == 0 else shore(x, y, z))
    for x in range(RX0 - 1, RX1 + 2):
        t.set(x, F + 1, Z0 - 5, K.slab('minecraft:mud_brick_slab')) if x in (RX0 - 1, RX1 + 1) else None
    t.set(RX0 - 1, F + 1, Z0 - 1, K.lantern())
    t.set(RX1 + 1, F + 1, Z0 - 3, K.lantern())

    # --- inside: crates down both walls, the locked crate at the far end, a cot, a table -----------------------
    for z in range(Z0 + 2, Z1 - 1):
        if z in (13, 14):
            continue
        for x, side in ((X0 + 1, 0), (X1 - 1, 1)):
            h = 1 + (1 if (z + side) % 3 else 0) + (1 if (z * 7 + side) % 5 == 0 else 0)
            for k in range(min(h, 3)):
                t.set(x, 1 + k, z, K.pick(rng, ((K.CRATE, 4), (K.CRATE_REP, 2), (K.CRATE_IMP, 1))))
    cx_, cz_ = 11, Z1 - 1
    t.set(cx_, 1, cz_, barrel())
    t.anchor('CRATE', cx_, 1, cz_)
    t.set(cx_ - 1, 1, cz_, K.chain('x'))
    t.set(cx_ + 1, 1, cz_, K.chain('x'))
    t.set(cx_, 2, cz_, K.CANVAS_SLAB)
    t.set(X0 + 1, 1, 13, K.PLATING_SLAB_TOP)
    t.set(X0 + 1, 2, 13, K.holo('east'))
    t.set(X0 + 1, 1, 14, K.CANVAS_SLAB)
    t.set(X1 - 1, 1, 13, K.carpet('brown'))
    t.set(X1 - 1, 1, 14, K.carpet('brown'))
    for z in (11, 17):
        t.set(11, TOP - 1, z, K.lantern(True))
    mod.light_fill(t, (X0 + 1, 1, Z0 + 1, X1 - 1, 2, Z1 - 1))
    # the roof hatch at the far end and its ladder
    t.set(11, TOP, Z1 - 3, AIR)
    for y in range(1, TOP + 1):
        t.set(11, y, Z1 - 3, K.ladder('south')) if t.get(11, y, Z1 - 2) not in (None, AIR) else None
    t.set(11, TOP + 1, Z1 - 2, S('iron_trapdoor', facing='north', half='bottom', open=True, powered=False, waterlogged=False))

    # --- the crane over the hatch, the hauler, the crew's camp round a fire -----------------------------
    for x in (X0 - 1, X1 + 1):
        K.column(t, x, F, F + 6, Z1 - 3, K.beam('y'))
        t.set(x, G, Z1 - 3, K.GRAVEL)
    for x in range(X0 - 1, X1 + 2):
        t.set(x, F + 7, Z1 - 3, K.beam('x'))
    t.set(11, F + 6, Z1 - 3, K.chain('y'))
    t.set(11, F + 5, Z1 - 3, K.chain('y'))
    t.set(11, F + 4, Z1 - 3, K.CRATE)
    t.set(X0 - 1, F + 8, Z1 - 3, K.LIGHT_AMBER)
    t.set(X1 + 1, F + 8, Z1 - 3, K.rod())
    # the hauler on the east side, being loaded
    for z in range(9, 16):
        for x in (19, 20, 21):
            t.set(x, F, z, K.PLATING_SLAB_TOP)
    t.set(20, F, 8, K.plating_stairs('south', 'top'))
    t.set(20, F + 1, 9, K.console('north'))
    t.set(19, F + 1, 9, K.rod('up'))
    for (x, z) in ((19, 12), (20, 12), (21, 13), (19, 14), (20, 14), (21, 15), (20, 15)):
        t.set(x, F + 1, z, K.pick(rng, ((K.CRATE, 3), (K.CRATE_REP, 1))))
    t.set(20, F + 2, 14, K.CRATE)
    t.set(20, F, 16, K.VENT)
    # the crew's lean-to and fire on the west
    K.lean_to(t, 1, 10, 4, 15, F, 'west', 3)
    for z in (10, 15):
        K.column(t, 1, F, F + 2, z, K.beam('y'))
    for z in (11, 13):
        K.bedroll(t, 2, F, z, color='brown' if z == 11 else 'gray', along='x', length=2)
    t.set(2, F, 15, K.CRATE)
    t.set(2, F + 1, 15, K.lantern())
    fx, fz = 5, 6
    K.fire_pit(t, fx, F, fz, rng, spit='z', stones=0.6)
    t.set(fx - 2, F, fz, K.log(axis='z'))
    t.set(fx + 2, F, fz + 1, K.CRATE)
    t.set(fx + 1, F, fz - 2, S('cauldron'))
    K.lamp_post(t, 16, F, 4, height=3, arm='west')
    K.mast(t, 22, F, 21, 8, top=K.LIGHT_RED, arms=((5, 'north', 1),))
    t.set(22, G, 21, K.GRAVEL)

    # --- people ------------------------------------------------------------------------------------------
    K.spot(t, 'CHIEF', 11, F, Z0 - 7)        # at the head of the cut, before the doors
    K.spot(t, 'CREW_1', 4, F, 8)             # by the fire
    K.spot(t, 'CREW_2', 18, F, 11)           # loading the hauler
    K.spot(t, 'CREW_3', 11, 1, 11)           # inside the container
    K.spot(t, 'CREW_4', 18, F, Z1 - 2)       # by the crane
    K.spot(t, 'HEART', 11, F, Z0 - 5)        # the head of the cut

    K.litter(t, F, (0, 0, 26, 26), [(K.DEBRIS, 3)], 0.015, rng)
    return t


def main():
    a = K.args()
    for tid, build in (('mission/smuggler_cache', build_dugout), ('mission/smuggler_cache_2', build_container)):
        if a.only and a.only not in tid:
            continue
        K.save(build(), tid, ANCHORS, META)


if __name__ == '__main__':
    main()
