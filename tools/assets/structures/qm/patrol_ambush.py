#!/usr/bin/env python3
"""mission/patrol_ambush and mission/patrol_ambush_2 (docs/MILESTONE_6.md 5.4).

The Republic's "Silent Patrol" (r1_silent_patrol): a patrol went quiet on an old ruin road. The player
finds them where the road passes a broken speeder-sled: three troopers down (DOWNED_1..3) and three of
the old stone watchers standing over them (SENTINEL_1..3, each beside one of the downed), woken and bound
to keep the road clear. The patrol's supply barrel (CRATE) lies with the spilled cargo. Both variants:
29 x 14 x 29, buried 1, any ground. The plinths the watchers stood on along the road are empty now.

  patrol_ambush    "The Bend": the old paved road runs in from the north under a standing arch and
                   bends east; the watchers' plinths line it, empty; the sled lies on its side at the
                   bend with its cargo spilled across the stones.
  patrol_ambush_2  "The Causeway Gate": the road runs straight through the stumps of an old gatehouse;
                   the watchers' niches in the gate passage stand empty; the sled has gone into the west
                   tower's foot.

Usage: python3 tools/assets/structures/qm/patrol_ambush.py [--preview <folder>] [--only <id part>]
"""
import math

import qmkit as K
from qmkit import S, AIR, Template, B, Noise, mod

SIZE = (29, 14, 29)
BURIED = 1
ANCHORS = ['CRATE', 'DOWNED_1', 'DOWNED_2', 'DOWNED_3', 'HEART', 'SENTINEL_1', 'SENTINEL_2', 'SENTINEL_3']
META = dict(buried=BURIED, terrain='any', max_slope=4, clearance=10, support_depth=8, ramp_rings=2, clear_above=6)

PAVING = [(K.RUIN, 4), (K.RUIN_CRACKED, 3), (K.PALE_TILES, 1), (K.RUIN_MOSSY, 2)]
OLD = [(K.RUIN_BRICKS, 4), (K.RUIN, 3), (K.RUIN_CRACKED, 2), (K.RUIN_MOSSY, 1)]


def rep_stairs(facing, half='bottom'):
    return S('or:republic_plating_stairs', facing=facing, half=half, shape='straight', waterlogged=False)


REP_SLAB = S('or:republic_plating_slab', type='bottom')
REP_SLAB_TOP = S('or:republic_plating_slab', type='top')


def road(t, y, points, width, seed, gaps=0.12):
    """An old paved road: flags of ruin stone laid on layer y, worn through in places (the ground shows),
    kerb stones along both edges."""
    pave = K.Mix(PAVING, seed, 2.0, 0.4)
    n = Noise(seed + 1, 1.6)
    cells = K.trail(t, y, points, width, pave, seed + 2, fray=0.25)
    for (x, z) in cells:
        if n.at(x, 0, z) < gaps:
            t.set(x, y, z, K.COARSE if (x + z) % 2 else K.GRAVEL)
    cs = set(cells)
    for (x, z) in cells:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if (nx, nz) not in cs and t.inside(nx, y, nz) and t.get(nx, y + 1, nz) is None and n.at(nx, 3, nz) > 0.42:
                t.set(nx, y + 1, nz, K.slab('or:ruin_brick_slab'))
                t.set(nx, y, nz, K.RUIN)
    return cs


def plinth(t, x, y, z, rng, broken=False):
    """A watcher's plinth: a carved base and a capstone, empty now; a broken one lies tumbled."""
    t.set(x, y - 1, z, K.RUIN)
    if broken:
        t.set(x, y, z, K.RUIN_CRACKED)
        t.set(x + rng.choice([-1, 1]), y, z, K.slab('or:ruin_brick_slab'))
        return
    t.set(x, y, z, K.RUIN_CARVED)
    t.set(x, y + 1, z, K.slab('or:ruin_brick_slab'))


def sled(t, x, y, z, rng, facing='east', on_side=True):
    """The patrol's speeder-sled (Republic white), wrecked: its body on its side, a skid torn off, the
    front scorched, the windscreen gone."""
    dx, dz = B.step(facing)
    px, pz = -dz, dx  # across
    for i in range(5):
        cx, cz = x + dx * i, z + dz * i
        t.set(cx, y, cz, K.REP_PLATING if i not in (0, 4) else K.SCORCHED)
        t.set(cx + px, y, cz + pz, REP_SLAB_TOP if i % 2 else K.REP_PLATING)
        if on_side:
            t.set(cx, y + 1, cz, REP_SLAB if i in (1, 2, 3) else K.SCORCHED)
        K.support(t, cx, y, cz)
        K.support(t, cx + px, y, cz + pz)
    t.set(x + dx * 5, y, z + dz * 5, K.pipes('x' if dx else 'z'))
    t.set(x - dx, y, z - dz, rep_stairs(B.opposite(facing), 'top'))
    t.set(x + px * 2 + dx * 2, y, z + pz * 2 + dz * 2, K.PLATING_SLAB)  # the torn skid
    K.support(t, x + px * 2 + dx * 2, y, z + pz * 2 + dz * 2)


# =============================================================================================
# patrol_ambush: The Bend
# =============================================================================================

def column_ruin(t, x, y, z, h, cap=True):
    """A standing column of the old road: a carved base, a shaft of pillar, a slab capital if whole."""
    t.set(x, y - 1, z, K.RUIN)
    t.set(x, y, z, K.RUIN_CARVED)
    for k in range(1, h):
        t.set(x, y + k, z, K.ruin_pillar('y'))
    if cap:
        t.set(x, y + h, z, K.slab('or:ruin_brick_slab'))


def build_bend():
    t = Template(*SIZE, seed=6901)
    rng = t.rng
    G, F = 0, 1
    old = K.Mix(OLD, 6903, 2.4, 0.3)
    moss = Noise(6905, 2.0)

    cells = road(t, G, [(13.5, -1), (13.5, 9), (15, 14), (20, 17.5), (29, 19)], 6, 6907)

    # --- the standing arch the road passes under ------------------------------------------------------
    az = 5
    for x in (10, 17):
        for y in range(F, F + 6):
            t.set(x, y, az, K.ruin_pillar('y') if 0 < y - F < 5 else K.RUIN_CARVED)
        t.set(x, G, az, K.RUIN)
        for dx in (-1, 1):
            t.set(x + dx, F, az, K.stairs('or:ruin_brick_stairs', 'east' if dx < 0 else 'west'))
            t.set(x + dx, G, az, K.RUIN)
    for x in range(10, 18):
        t.set(x, F + 6, az, K.RUIN_BRICKS if x not in (13, 14) else K.RUIN_CARVED)
    t.set(11, F + 5, az, K.stairs('or:ruin_brick_stairs', 'east', 'top'))
    t.set(16, F + 5, az, K.stairs('or:ruin_brick_stairs', 'west', 'top'))
    for x in (12, 13, 14, 15):
        t.set(x, F + 7, az, K.RUIN_BRICKS if x in (13, 14) else K.slab('or:ruin_brick_slab'))
    t.set(13, F + 8, az, K.RUIN_CARVED)
    t.set(14, F + 8, az, K.stairs('or:ruin_brick_stairs', 'west'))
    for (x, y) in ((10, F + 6), (17, F + 5), (17, F + 6), (10, F + 1), (17, F + 1)):
        if moss.at(x, y, 0) > 0.45:
            t.set(x, y, az, K.RUIN_MOSSY)

    # --- the colonnade along the outside of the bend, one column fallen across the grass ---------------
    for (x, z, h) in ((19, 11, 6), (22, 12, 3), (25, 13, 5), (28, 14, 2), (8, 10, 4), (8, 14, 2)):
        column_ruin(t, x, F, z, h, cap=h >= 4)
    for x in range(20, 25):
        t.set(x, F, 8, K.ruin_pillar('x'))
        K.support(t, x, F, 8, K.COARSE)
    t.set(25, F, 8, K.RUIN_CARVED)
    t.set(19, F, 8, K.slab('or:ruin_brick_slab'))
    K.support(t, 25, F, 8, K.COARSE)
    K.support(t, 19, F, 8, K.COARSE)

    # --- the watchers' plinths along the road, empty --------------------------------------------------
    for (x, z, br) in ((10, 9, False), (17, 9, False), (11, 14, True), (19, 21, False), (23, 15, False),
                       (26, 22, True), (9, 2, False), (18, 2, True)):
        plinth(t, x, F, z, rng, broken=br)

    # --- the roadside shrine inside the bend: a dais, three broken walls, the great plinth, the binding ----
    SX0, SZ0, SX1, SZ1 = 3, 19, 10, 26
    for x in range(SX0, SX1 + 1):
        for z in range(SZ0, SZ1 + 1):
            t.set(x, G, z, K.PALE_TILES if (x + z) % 3 else K.RUIN_CRACKED)
            wall = x in (SX0, SX1) or z == SZ1
            if wall:
                h = 1 + int(round(3.0 * moss.at(x * 2, 4, z * 2)))
                if x == SX1 and z < SZ0 + 3:
                    h = 0  # the corner fallen in
                for k in range(h):
                    t.set(x, F + k, z, K.RUIN_MOSSY if k == 0 and moss.at(x, 0, z) > 0.5 else old(x, F + k, z))
                if h >= 3:
                    t.set(x, F + h, z, K.slab('or:ruin_brick_slab'))
    for x in range(SX0 + 1, SX1):
        t.set(x, F, SZ0 - 1, K.stairs('or:ruin_brick_stairs', 'south')) if (x + 1) % 3 else None
        K.support(t, x, F, SZ0 - 1, K.RUIN)
    for (x, z) in ((SX0 + 1, SZ0), (SX1 - 1, SZ0)):
        column_ruin(t, x, F, z, 3 if x < 6 else 2, cap=x < 6)
    gx, gz = (SX0 + SX1) // 2, SZ1 - 2
    for dx in (-1, 0, 1):
        t.set(gx + dx, F, gz, K.RUIN_CARVED if dx == 0 else K.stairs('or:ruin_brick_stairs', 'east' if dx < 0 else 'west'))
    t.set(gx, F + 1, gz, K.RUIN_CARVED)
    t.set(gx, F + 2, gz, K.slab('or:ruin_brick_slab'))
    # the thing that woke them: a shard of black stone on the great plinth's step, lit red from below
    t.set(gx, F, gz - 1, K.LIGHT_RED)
    t.set(gx, F + 1, gz - 1, S('polished_blackstone_wall', east='none', north='none', south='none', west='none', up=True,
                               waterlogged=False))
    t.set(gx, F + 2, gz - 1, S('pointed_dripstone', thickness='tip', vertical_direction='up', waterlogged=False))
    for (x, z, s) in ((SX1 + 1, SZ0 + 1, K.RUIN_CRACKED), (SX1 + 2, SZ0, K.slab('or:ruin_brick_slab')), (SX1 + 1, SZ0 - 1, K.RUIN)):
        t.set(x, F, z, s)
        K.support(t, x, F, z)

    # --- the sled on its side at the bend, its cargo spilled across the road ------------------------------
    sled(t, 16, F, 14, rng, facing='east')
    K.scorch_burst(t, F, 18.5, 13.5, 3.2, rng, density=0.5, debris=0.3)
    for (x, z, s) in ((21, 13, K.CRATE_REP), (23, 14, K.CRATE_REP), (20, 12, K.CRATE), (23, 12, REP_SLAB),
                      (24, 13, K.CRATE)):
        t.set(x, F, z, s)
        K.support(t, x, F, z)
    t.set(21, F + 1, 13, K.CRATE_REP)
    t.set(22, F, 13, S('barrel', facing='up', open=False))
    t.anchor('CRATE', 22, F, 13)
    K.support(t, 22, F, 13)
    t.set(19, F, 18, K.carpet('white'))
    t.set(20, F, 18, K.carpet('blue'))
    t.set(12, F, 15, K.carpet('blue'))
    K.scorch_burst(t, F, 13.5, 11.0, 2.0, rng, density=0.35, debris=0.2)
    for (x, y, z) in ((10, F + 2, az), (17, F + 3, az), (19, F + 2, 11), (8, F + 1, 10)):
        t.set(x, y, z, K.SCORCHED)  # blaster scars on the stone

    # --- the old toll-house on the road's shoulder (north-east): broken walls, its roof fallen in ----------
    HX0, HZ0, HX1, HZ1 = 21, 1, 27, 6
    hh = Noise(6911, 2.2)
    for x in range(HX0, HX1 + 1):
        for z in range(HZ0, HZ1 + 1):
            t.set(x, G, z, K.PALE_TILES if moss.at(x * 3, 2, z * 3) > 0.5 else K.RUIN_CRACKED)
            if not (x in (HX0, HX1) or z in (HZ0, HZ1)) or (x == HX0 and z in (3, 4)):
                continue
            h = 1 + int(round(3.4 * hh.at(x * 2, 9, z * 2)))
            if (x, z) in ((HX0, 2), (HX0, 5)):
                h = 3                                   # the door jambs
            if (x, z) == (HX1, HZ0):
                h = 5                                   # the corner that still stands
            for k in range(h):
                t.set(x, F + k, z, K.RUIN_MOSSY if k == 0 and moss.at(x, 0, z) > 0.5 else old(x, F + k, z))
            if h >= 3 and (x, z) not in ((HX0, 2), (HX0, 5)):
                t.set(x, F + h, z, K.slab('or:ruin_brick_slab'))
    for z in (2, 3, 4, 5):
        t.set(HX0, F + 3, z, K.RUIN_CARVED if z in (3, 4) else K.stairs('or:ruin_brick_stairs', 'south' if z == 2 else 'north', 'top'))
    for (x, z) in ((23, 2), (24, 3), (25, 2), (25, 4), (23, 5)):   # the roof, fallen in
        t.set(x, F, z, K.slab('or:ruin_brick_slab') if (x + z) % 2 else K.RUIN_CRACKED)
    t.set(24, F, 5, K.ruin_pillar('x'))
    t.set(25, F, 5, K.ruin_pillar('x'))
    t.set(22, F, 4, K.carpet('white'))                 # a scout's helmet and pack, left where he sheltered
    t.set(22, F, 5, K.carpet('blue'))
    # a low embankment wall along the far side of the road beyond the bend, mostly fallen
    for x in range(14, 28):
        h = int(round(2.2 * hh.at(x * 3, 20, 0) - 0.3))
        for k in range(max(0, h)):
            t.set(x, F + k, 26, old(x, F + k, 26))
        if h > 0:
            t.set(x, G, 26, K.RUIN)
        elif hh.at(x, 21, 3) > 0.55:
            t.set(x, F, 25, K.slab('or:ruin_brick_slab'))
            K.support(t, x, F, 25)

    # --- people ------------------------------------------------------------------------------------------
    K.spot(t, 'DOWNED_1', 14, F, 12)       # on the road where the sled turned over
    K.spot(t, 'SENTINEL_1', 13, F, 13)
    K.spot(t, 'DOWNED_2', 18, F, 17)       # thrown clear at the bend
    K.spot(t, 'SENTINEL_2', 19, F, 16)
    K.spot(t, 'DOWNED_3', 24, F, 17)       # crawled toward the crates
    K.spot(t, 'SENTINEL_3', 25, F, 18)
    K.spot(t, 'HEART', 15, F, 10)          # the road at the bend

    K.litter(t, F, (2, 0, 28, 28), [(K.DEBRIS, 1), (K.slab('or:ruin_brick_slab'), 1)], 0.015, rng)
    return t


def build_gate():
    t = Template(*SIZE, seed=7001)
    rng = t.rng
    G, F = 0, 1
    old = K.Mix(OLD, 7003, 2.4, 0.3)
    moss = Noise(7005, 2.0)
    hn = Noise(7007, 3.0)

    road(t, G, [(14.5, -1), (14.5, 29)], 6, 7009, gaps=0.15)

    # --- the gatehouse: two tower stumps either side of the road, the passage between ---------------------
    TZ0, TZ1 = 11, 17
    towers = ((6, 11), (18, 23))
    for (x0, x1) in towers:
        for x in range(x0, x1 + 1):
            for z in range(TZ0, TZ1 + 1):
                t.set(x, G, z, K.RUIN)
                wall = x in (x0, x1) or z in (TZ0, TZ1)
                if not wall:
                    t.set(x, G, z, K.PALE_TILES if moss.at(x * 3, 1, z * 3) > 0.45 else K.RUIN_CRACKED)
                    continue
                h = 5 + int(round(4.0 * hn.at(x * 2, 0, z * 2)))
                if (x0 == 6 and z == TZ0 and x >= 9) or (x0 == 18 and x == x1 and z >= 15):
                    h = 2 + (x + z) % 2  # broken down
                for k in range(h):
                    y = F + k
                    s_ = K.RUIN if k == 0 else (K.RUIN_MOSSY if k <= 2 and moss.at(x, y, z) > 0.62 else old(x, y, z))
                    if (x in (x0, x1) and z in (TZ0, TZ1)) and k > 0:
                        s_ = K.RUIN_BRICKS if k % 3 else K.RUIN_CARVED  # quoins
                    t.set(x, y, z, s_)
                if h >= 6 and (x + z) % 2 == 0:
                    t.set(x, F + h, z, K.slab('or:ruin_brick_slab'))
        # a doorway into each stump from the road side, light slits
        door_x = x1 if x0 == 6 else x0
        for y in (F, F + 1, F + 2):
            t.set(door_x, y, (TZ0 + TZ1) // 2, AIR)
        t.set(door_x, F + 3, (TZ0 + TZ1) // 2, K.RUIN_CARVED)
    # the watchers' niches in the passage walls, their plinths empty
    for (x, side) in ((11, 'east'), (18, 'west')):
        for z in (TZ0 + 1, TZ1 - 1):
            for y in (F, F + 1, F + 2):
                t.set(x, y, z, AIR)
            t.set(x, F + 3, z, K.stairs('or:ruin_brick_stairs', side, 'top'))
            plinth(t, x, F, z, rng)
            inner = x - 1 if side == 'east' else x + 1
            t.set(inner, F, z, K.RUIN_CARVED)
            t.set(inner, F + 1, z, K.RUIN_CARVED)
            t.set(inner, F + 2, z, K.RUIN_CARVED)
    # the lintel that spanned the passage, fallen in pieces across its south mouth
    for (x, z, s_) in ((13, TZ1 + 1, K.RUIN_BRICKS), (14, TZ1 + 2, K.ruin_pillar('z')), (15, TZ1 + 2, K.ruin_pillar('z')),
                       (16, TZ1 + 1, K.slab('or:ruin_brick_slab')), (12, TZ1 + 2, K.RUIN_CRACKED), (17, TZ1 + 3, K.slab('or:ruin_brick_slab'))):
        t.set(x, F, z, s_)
        K.support(t, x, F, z, K.RUIN)
    for x in (12, 17):
        t.set(x, F + 4, TZ0, K.RUIN_BRICKS)
        t.set(x, F + 4, TZ1, K.RUIN_BRICKS)
    t.set(12, F + 5, TZ0, K.stairs('or:ruin_brick_stairs', 'east'))
    t.set(17, F + 5, TZ0, K.stairs('or:ruin_brick_stairs', 'west'))
    for (x, z) in ((12, TZ0), (17, TZ0), (12, TZ1), (17, TZ1)):
        K.column(t, x, F, F + 3, z, K.ruin_pillar('y'))
    # stairs up inside the east stump to its broken top, a look-out over the road
    for k in range(5):
        t.set(19 + k if k < 4 else 22, F + k, TZ1 - 1 if k < 4 else TZ1 - 2, K.stairs('or:ruin_brick_stairs', 'east') if k < 4
              else K.stairs('or:ruin_brick_stairs', 'north'))
    for x in range(19, 23):
        t.set(x, F + 5, TZ0 + 1, K.slab('or:ruin_brick_slab', 'top'))
        t.set(x, F + 5, TZ0 + 2, K.slab('or:ruin_brick_slab', 'top'))
    # the curtain walls running off either side, broken
    for (xs, z) in ((range(0, 6), 14), (range(24, 29), 14)):
        for x in xs:
            h = int(round(3.2 * moss.at(x * 3, 2, z)))
            t.set(x, G, z, K.RUIN)
            for k in range(h):
                t.set(x, F + k, z, old(x, F + k, z))
    for (x, z) in ((1, 13), (2, 15), (26, 15), (27, 13), (25, 12)):
        t.set(x, F, z, K.slab('or:ruin_brick_slab') if (x + z) % 2 else K.RUIN_CRACKED)
        K.support(t, x, F, z)

    # --- the sled, gone into the west tower's foot; cargo spilled north; the barrel -------------------------
    sled(t, 6, F, 9, rng, facing='east')
    K.scorch_burst(t, F, 9.0, 9.5, 3.4, rng, density=0.55, debris=0.35)
    for (x, y) in ((9, F + 1), (10, F + 2), (11, F + 1)):
        t.set(x, y, TZ0, K.SCORCHED)
    for (x, z, s_) in ((8, 6, K.CRATE_REP), (9, 7, K.CRATE), (6, 6, REP_SLAB), (5, 7, K.CRATE_REP), (10, 5, K.DEBRIS)):
        t.set(x, F, z, s_)
        K.support(t, x, F, z)
    t.set(7, F, 6, S('barrel', facing='up', open=False))
    t.anchor('CRATE', 7, F, 6)
    K.support(t, 7, F, 6)
    t.set(12, F, 8, K.carpet('blue'))
    t.set(13, F, 21, K.carpet('white'))
    K.scorch_burst(t, F, 14.5, 14.0, 2.2, rng, density=0.35, debris=0.15)
    # plinths along the road outside the gate, empty
    for (x, z, br) in ((10, 3, False), (19, 3, False), (10, 24, True), (19, 24, False), (19, 8, True)):
        plinth(t, x, F, z, rng, broken=br)

    # --- people ------------------------------------------------------------------------------------------
    K.spot(t, 'DOWNED_1', 13, F, 9)        # before the gate, by the wreck
    K.spot(t, 'SENTINEL_1', 14, F, 8)
    K.spot(t, 'DOWNED_2', 15, F, 13)       # in the passage
    K.spot(t, 'SENTINEL_2', 16, F, 14)
    K.spot(t, 'DOWNED_3', 15, F, 20)       # past the fallen lintel
    K.spot(t, 'SENTINEL_3', 14, F, 21)
    K.spot(t, 'HEART', 14, F, 15)          # the gate passage

    K.litter(t, F, (0, 0, 28, 28), [(K.DEBRIS, 1), (K.slab('or:ruin_brick_slab'), 1)], 0.012, rng)
    return t


def main():
    a = K.args()
    for tid, build in (('mission/patrol_ambush', build_bend), ('mission/patrol_ambush_2', build_gate)):
        if a.only and a.only not in tid:
            continue
        K.save(build(), tid, ANCHORS, META)


if __name__ == '__main__':
    main()
