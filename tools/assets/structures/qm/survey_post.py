#!/usr/bin/env python3
"""mission/survey_post and mission/survey_post_2 (docs/MILESTONE_6.md 5.4).

Imperial Intelligence's "Border Incident" (i2_border_incident): a Republic recon squad crossed the
treaty line and struck one of the Empire's survey posts. The player finds the squad (SQUAD_1..5) holding
the post and the post's own man lying wounded (WOUNDED). Both variants: 31 x 20 x 31, buried 1, high
ground (a survey post sits where it can see), the post's look Imperial (dark plating with the red
stripe, Imperial crates), the squad's kit Republic (white crates), the fight's marks everywhere.

  survey_post    "The Survey Mast": a prefab station on a hilltop inside a ring of barricades, a lattice
                 mast with a dish and red lights over it. The gate checkpoint is blasted open, the hut's
                 door burnt through; the squad has piled its kit by the generator.
  survey_post_2  "The Dig": the surveyors were opening an old ruin. Their trench cuts the hilltop, a
                 tripod crane stands over the pit, the finds lie sorted on tarps, their tents and the
                 command tent with its holo table stand round it, the comm mast beside.

Usage: python3 tools/assets/structures/qm/survey_post.py [--preview <folder>] [--only <id part>]
"""
import math

import qmkit as K
from qmkit import S, AIR, Template, B, Noise, mod

SIZE = (31, 20, 31)
BURIED = 1
ANCHORS = ['HEART', 'SQUAD_1', 'SQUAD_2', 'SQUAD_3', 'SQUAD_4', 'SQUAD_5', 'WOUNDED']
META = dict(buried=BURIED, terrain='high', max_slope=5, clearance=10, support_depth=8, ramp_rings=2, clear_above=6)

IMP_MIX = [(K.IMP_PLATING, 6), (K.IMP_PANEL, 1), (K.PLATING, 1)]


def imp_stairs(facing, half='bottom'):
    return S('or:imperial_plating_stairs', facing=facing, half=half, shape='straight', waterlogged=False)


IMP_SLAB = S('or:imperial_plating_slab', type='bottom')
IMP_SLAB_TOP = S('or:imperial_plating_slab', type='top')


def barricade(t, cells, y, rng, scorch=None):
    """Imperial barriers along `cells`: low walls of plating with sloped tops, bars between them;
    within `scorch` (cx, cz, r) of a blast they are thrown down and burnt."""
    for i, (x, z) in enumerate(cells):
        if scorch and math.hypot(x - scorch[0], z - scorch[1]) < scorch[2]:
            if rng.random() < 0.35:
                t.set(x, y, z, K.SCORCHED if rng.random() < 0.5 else imp_stairs(rng.choice(['north', 'south', 'east', 'west'])))
                K.support(t, x, y, z, K.GRAVEL)
            continue
        r = rng.random()
        seg = (i // 4) % 3  # runs of barrier, of bars, and gaps
        if seg == 2 and r < 0.75:
            continue
        K.support(t, x, y, z, K.GRAVEL)
        if seg == 1:
            t.set(x, y, z, K.bars())
            if r < 0.5:
                t.set(x, y + 1, z, K.bars())
        else:
            t.set(x, y, z, K.IMP_PLATING if r < 0.7 else K.PLATING)
            if r < 0.45:
                t.set(x, y + 1, z, IMP_SLAB)


# =============================================================================================
# survey_post: The Survey Mast
# =============================================================================================

def build_mast_post():
    t = Template(*SIZE, seed=6501)
    rng = t.rng
    G, F = 0, 1
    worn = K.Mix(K.WORN, 6503, scale=4.5, jitter=0.15)
    track = K.Mix(K.TRACK, 6505, 3.5, 0.2)
    imp = K.Mix(IMP_MIX, 6507, scale=2.6, jitter=0.2)

    cx, cz = 15.0, 16.5
    K.ground_blob(t, G, cx, cz, 12.0, 11.0, worn, 6509, fray=0.2)
    K.trail(t, G, [(13, -1), (14, 4), (15, 6)], 3, track, 6511)

    # --- the barricade ring, the checkpoint blasted open --------------------------------------------
    wob = Noise(6513, 4.0)
    yard, ring = K.ellipse_cells(cx, cz, 13.0, 11.6, wob, 0.12)
    order = sorted(ring, key=lambda p: math.atan2(p[1] - cz, p[0] - cx))
    gz = min(z for (x, z) in ring if x == 15)
    gate = {(x, z) for (x, z) in ring if abs(x - 15) <= 1 and z < cz}
    barricade(t, [c for c in order if c not in gate], F, rng, scorch=(15, gz, 3.6))
    # the checkpoint booth, its barrier arm snapped and lying across the way
    bx, bz = 18, gz + 1
    for y in range(F, F + 3):
        t.set(bx, y, bz, K.IMP_PLATING if y != F + 1 else S('gray_stained_glass'))
    t.set(bx, F + 3, bz, IMP_SLAB)
    t.set(bx - 1, F, bz, K.beam('y'))
    t.set(bx - 1, F + 1, bz, K.LIGHT_RED)
    for x in (14, 15):
        t.set(x, F, gz + 2, K.beam('x'))
    t.set(13, F, gz + 3, K.SCORCHED)
    K.scorch_burst(t, F, 15, gz + 1, 3.2, rng, density=0.6, debris=0.35)

    # --- the prefab station --------------------------------------------------------------------------
    X0, X1, Z0, Z1 = 8, 17, 17, 23
    H = 4
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            t.set(x, G, z, K.DECK if (x + z) % 4 else K.GRATE)
            wall = x in (X0, X1) or z in (Z0, Z1)
            for y in range(F, F + H):
                if wall:
                    corner = x in (X0, X1) and z in (Z0, Z1)
                    pilaster = (z in (Z0, Z1) and (x - X0) % 3 == 0) or (x in (X0, X1) and (z - Z0) % 3 == 0)
                    if corner or pilaster:
                        s = K.beam('y')
                    elif y == F:
                        s = K.PLATING  # the footing
                    elif y == F + 2 and (x + z) % 2 == 0:
                        s = S('gray_stained_glass') if z in (Z0, Z1) else K.bars()  # slit windows
                    elif y == F + H - 1:
                        s = K.IMP_PANEL  # the band under the roof
                    else:
                        s = imp(x, y, z)
                    t.set(x, y, z, s)
                else:
                    t.set(x, y, z, AIR)
            t.set(x, F + H, z, IMP_SLAB_TOP if not (x in (X0, X1) or z in (Z0, Z1)) else K.IMP_PLATING)
    for x in range(X0, X1 + 1):  # the parapet
        for z in (Z0, Z1):
            if (x + z) % 2 == 0:
                t.set(x, F + H + 1, z, IMP_SLAB)
    for z in range(Z0, Z1 + 1):
        for x in (X0, X1):
            if (x + z) % 2 == 0:
                t.set(x, F + H + 1, z, IMP_SLAB)
    # the door burnt through: the opening blackened, the door plate lying outside
    dx0 = 12
    for x in (dx0, dx0 + 1):
        for y in (F, F + 1, F + 2):
            t.set(x, y, Z0, AIR)
    for (x, y) in ((dx0 - 1, F), (dx0 - 1, F + 1), (dx0 - 1, F + 2), (dx0 + 2, F + 2), (dx0, F + 3), (dx0 + 1, F + 3),
                   (dx0 + 2, F)):
        t.set(x, y, Z0, K.SCORCHED)
    t.set(dx0, F, Z0 - 2, IMP_SLAB)
    t.set(dx0 + 1, F, Z0 - 2, IMP_SLAB)
    t.set(dx0 + 1, F, Z0 - 3, K.SCORCH)
    K.support(t, dx0, F, Z0 - 2)
    K.support(t, dx0 + 1, F, Z0 - 2)
    K.scorch_burst(t, F, dx0 + 0.5, Z0 - 1, 2.6, rng, density=0.5, debris=0.3)
    # the entry canopy, half torn away; the roof's vent stack, its searchlight and a small dish
    for x in range(dx0 - 1, dx0 + 3):
        if x != dx0 + 2:
            t.set(x, F + H - 1, Z0 - 1, IMP_SLAB_TOP)
    t.set(dx0 + 2, F + H - 2, Z0 - 1, imp_stairs('east', 'top'))
    t.set(dx0 - 1, F + H - 2, Z0 - 1, K.LIGHT_RED)
    K.column(t, X0 + 2, F + H + 1, F + H + 2, Z1 - 2, K.pipes('y'))
    t.set(X0 + 2, F + H + 3, Z1 - 2, K.VENT)
    t.set(X0 + 3, F + H + 1, Z1 - 2, K.VENT)
    t.set(X1 - 1, F + H + 1, Z0 + 1, K.beam('y'))
    t.set(X1 - 1, F + H + 2, Z0 + 1, K.LIGHT)
    t.set(X1 - 1, F + H + 2, Z0, K.bars())
    t.set(X1 - 2, F + H + 1, Z1 - 1, K.rod('up'))
    t.set(X1 - 2, F + H + 2, Z1 - 1, K.plating_stairs('north', 'top'))
    t.set(X1 - 3, F + H + 2, Z1 - 1, K.plating_stairs('east', 'top'))
    for (x, z) in ((X0 + 5, Z0 + 2), (X0 + 6, Z0 + 2), (X0 + 5, Z0 + 3)):
        t.set(x, F + H + 1, z, K.CRATE_IMP)
    K.banner(t, X1 + 1, F + 2, Z0 + 2, 'east', 'black')
    K.banner(t, X0 - 1, F + 2, Z0 + 2, 'west', 'black')
    # inside: bunks, the survey console and its holo, the map table, the locker, the lights
    for (x, z) in ((X0 + 1, Z1 - 1), (X0 + 2, Z1 - 1)):
        t.set(x, F, z, K.PLATING_SLAB_TOP)
        t.set(x, F + 1, z, K.carpet('gray'))
    t.set(X0 + 1, F + 2, Z1 - 1, IMP_SLAB_TOP)
    t.set(X0 + 2, F + 2, Z1 - 1, IMP_SLAB_TOP)
    t.set(X0 + 1, F, Z0 + 1, K.CRATE_IMP)
    t.set(X0 + 1, F + 1, Z0 + 1, K.CRATE_IMP)
    t.set(X1 - 1, F, Z1 - 1, K.console('north'))
    t.set(X1 - 2, F, Z1 - 1, K.console('north'))
    t.set(X1 - 1, F + 1, Z1 - 1, K.holo('north'))
    t.set(X1 - 2, F + 1, Z1 - 1, S('or:holo_screen', facing='north'))
    for x in range(X0 + 4, X0 + 7):
        t.set(x, F, Z1 - 2, IMP_SLAB_TOP)
    t.set(X0 + 4, F + 1, Z1 - 2, K.carpet('white'))
    t.set(X0 + 5, F + 1, Z1 - 2, K.carpet('light_gray'))
    t.set(X0 + 6, F + 1, Z1 - 2, K.candle(1, lit=False))
    t.set(X1 - 1, F, Z0 + 2, S('or:engineering_bench')) if False else None
    t.set(X1 - 1, F, Z0 + 1, K.CRATE_IMP)
    t.set(X1 - 1, F, Z0 + 2, imp_stairs('west'))  # an overturned stool
    for x in (X0 + 3, X1 - 3):
        t.set(x, F + H - 1, (Z0 + Z1) // 2, K.LIGHT)
    mod.light_fill(t, (X0 + 1, F, Z0 + 1, X1 - 1, F + 2, Z1 - 1))

    # --- the lattice mast with its dish and red lights -------------------------------------------------
    mx, mz = 22, 13
    for (x, z) in ((mx - 1, mz - 1), (mx + 1, mz - 1), (mx - 1, mz + 1), (mx + 1, mz + 1)):
        K.column(t, x, F, F + 9, z, K.beam('y'))
        t.set(x, G, z, K.GRAVEL)
    for y in (F + 3, F + 6, F + 9):
        for (x, z) in ((mx, mz - 1), (mx, mz + 1)):
            t.set(x, y, z, K.beam('x'))
        for (x, z) in ((mx - 1, mz), (mx + 1, mz)):
            t.set(x, y, z, K.beam('z'))
    for k in range(1, 3):
        for y in (F + 3 + k, F + 6 + k):
            t.set(mx, y, mz - 1, K.bars())
            t.set(mx + 1, y, mz, K.bars())
    K.column(t, mx, F + 9, F + 16, mz, K.beam('y'))
    t.set(mx, F + 17, mz, K.LIGHT_RED)
    t.set(mx, F + 18, mz, K.rod())
    for (x, z) in ((mx - 1, mz), (mx + 1, mz), (mx, mz - 1), (mx, mz + 1)):
        t.set(x, F + 13, z, K.bars())
    t.set(mx - 2, F + 13, mz, K.LIGHT_RED)
    t.set(mx + 2, F + 13, mz, K.LIGHT_RED)
    # the dish: a tilted bowl of plating stairs facing south-west, on an arm
    dxz = (mx - 1, mz + 2)
    t.set(mx, F + 11, mz + 1, K.beam('z'))
    for (x, y, z, s) in ((dxz[0], F + 11, dxz[1], K.PLATING), (dxz[0] - 1, F + 11, dxz[1], K.plating_stairs('east')),
                         (dxz[0] + 1, F + 11, dxz[1], K.plating_stairs('west')), (dxz[0], F + 12, dxz[1], K.plating_stairs('south', 'top')),
                         (dxz[0], F + 10, dxz[1], K.plating_stairs('south', 'bottom')), (dxz[0], F + 11, dxz[1] + 1, K.rod('south'))):
        t.set(x, y, z, s)
    for y in range(F, F + 10):
        t.set(mx, y, mz, K.ladder('north')) if False else None
    t.set(mx, F, mz, K.pipes('y'))
    t.set(mx, F + 1, mz, K.pipes('y'))
    t.set(mx, F + 2, mz, K.VENT)

    # --- the generator and the fuel cells by the station; the squad's kit piled there ---------------------
    for (x, z) in ((19, 20), (19, 21), (20, 20), (20, 21)):
        t.set(x, F, z, K.PLATING)
        t.set(x, G, z, K.GRAVEL)
    t.set(19, F + 1, 20, K.VENT)
    t.set(20, F + 1, 20, K.pipes('x'))
    t.set(19, F + 1, 21, K.LIGHT_AMBER)
    t.set(20, F + 1, 21, IMP_SLAB)
    t.set(18, F + 1, 20, K.pipes('x'))
    t.set(18, F + 2, 20, K.pipes('x')) if False else None
    for z in (19, 22):
        t.set(21, F, z, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    K.crate_pile(t, [(21, 23, 2), (22, 23, 1), (22, 22, 1), (20, 24, 1)], F, rng, kinds=((K.CRATE_REP, 3), (K.CRATE, 1)), tarp=0.2)
    t.set(22, F + 1, 22, K.holo('north'))
    t.set(19, F, 24, K.CANVAS)
    t.set(18, F, 24, K.carpet('white'))
    t.set(18, F, 25, K.carpet('red'))
    # the survey instrument at the brow, still aimed out over the country
    ix, iz = 5, 12
    t.set(ix, F, iz, K.beam('y'))
    t.set(ix, F + 1, iz, K.beam('y'))
    t.set(ix, F + 2, iz, K.holo('west'))
    t.set(ix + 1, F, iz, K.CRATE_IMP)
    t.set(ix + 1, F + 1, iz, K.candle(1, lit=False)) if False else None
    t.set(ix - 1, F, iz + 1, K.fence('spruce_fence'))
    t.set(ix - 1, F + 1, iz + 1, K.carpet('red'))
    t.set(ix + 4, F, iz + 6, K.fence('spruce_fence'))
    t.set(ix + 4, F + 1, iz + 6, K.carpet('red'))
    # an Imperial crate shot open and tipped over, a dropped helmet
    t.set(7, F, 25, K.CRATE_IMP)
    t.set(6, F, 26, IMP_SLAB)
    t.set(8, F, 26, K.DEBRIS)
    K.lamp_post(t, 7, F, 15, height=3, post=K.beam('y'), lamp=K.LIGHT_RED)
    K.lamp_post(t, 22, F, 17, height=3, post=K.beam('y'), lamp=K.LIGHT)

    # --- people ------------------------------------------------------------------------------------------
    K.spot(t, 'WOUNDED', dx0 - 2, F, Z0 + 2)   # inside, by the door he held
    K.spot(t, 'SQUAD_1', 14, F, gz + 4)        # at the blasted gate
    K.spot(t, 'SQUAD_2', 17, F, gz + 3)
    K.spot(t, 'SQUAD_3', dx0 + 2, F, Z0 - 2)   # at the station's door
    K.spot(t, 'SQUAD_4', mx - 2, F, mz + 1)    # under the mast
    K.spot(t, 'SQUAD_5', 20, F, 22)            # by the kit
    K.spot(t, 'HEART', 13, F, 13)              # the yard before the station

    K.litter(t, F, (4, 5, 26, 27), [(K.DEBRIS, 3), (K.SCORCH, 2)], 0.03, rng)
    return t


def build_dig():
    t = Template(*SIZE, seed=6601)
    rng = t.rng
    G, F = 0, 1
    worn = K.Mix(K.WORN, 6603, scale=4.5, jitter=0.15)
    track = K.Mix(K.TRACK, 6605, 3.5, 0.2)
    spoil = K.Mix([(K.COARSE, 4), (K.GRAVEL, 3), (K.DIRT, 2), (K.ROOTED, 1)], 6607, 2.5, 0.3)
    old = K.Mix([(K.RUIN, 4), (K.RUIN_CRACKED, 3), (K.RUIN_MOSSY, 2), (K.RUIN_BRICKS, 2)], 6609, 2.2, 0.3)

    K.ground_blob(t, G, 15.0, 16.0, 13.0, 12.5, worn, 6611, fray=0.22)
    K.trail(t, G, [(17, -1), (16, 4), (15, 8)], 3, track, 6613)

    # --- the trench: the hilltop opened down to the old foundations -------------------------------------
    TX0, TX1, TZ0, TZ1 = 9, 21, 12, 22
    tn = Noise(6615, 3.0)
    pit = set()
    for x in range(TX0, TX1 + 1):
        for z in range(TZ0, TZ1 + 1):
            edge = min(x - TX0, TX1 - x, z - TZ0, TZ1 - z)
            if edge == 0 and tn.at(x, 0, z) < 0.45:
                continue  # a ragged edge
            pit.add((x, z))
            t.set(x, G, z, AIR)
            t.set(x, F, z, AIR)
    # the old walls standing up out of the trench floor, a carved stone, a broken pillar, old flags
    walls = [(x, 15) for x in range(TX0, 17)] + [(16, z) for z in range(15, TZ1 + 1)] + [(x, 19) for x in range(17, TX1 + 1)]
    for (x, z) in walls:
        if (x, z) not in pit:
            continue
        h = 1 + (1 if tn.at(x * 2, 5, z * 2) > 0.5 else 0) + (1 if tn.at(x * 3, 9, z) > 0.72 else 0)
        for k in range(h):
            t.set(x, G + k, z, old(x, G + k, z))
    for (x, z) in sorted(pit):
        if t.get(x, G, z) == AIR and (x < 16 and z > 15) and (x + z) % 3:
            t.set(x, G - 0, z, AIR)
    for (x, z) in ((11, 17), (12, 17), (11, 18), (12, 18), (13, 18), (12, 19), (13, 19), (11, 20), (12, 20)):
        t.set(x, G, z, K.PALE_TILES if (x + z) % 3 else K.RUIN_CRACKED)  # old flags laid bare (on the trench floor level)
    for (x, z) in ((11, 17), (12, 17), (11, 18), (12, 18), (13, 18), (12, 19), (13, 19), (11, 20), (12, 20)):
        t.set(x, F, z, AIR)
    t.set(14, G, 20, K.RUIN_CARVED)
    t.set(14, F, 20, K.ruin_pillar('y'))
    t.set(18, G, 21, K.ruin_pillar('x'))
    t.set(19, G, 21, K.ruin_pillar('x'))
    t.set(13, G, 13, S('decorated_pot', cracked=True, facing='south', waterlogged=False))
    # the spoil heaps along both sides
    for x in list(range(TX0 - 3, TX0)) + list(range(TX1 + 1, TX1 + 4)):
        for z in range(TZ0 - 1, TZ1 + 2):
            d = min(abs(x - (TX0 - 1)), abs(x - (TX1 + 1)))
            hgt = 2 - d + (1 if tn.at(x * 2, 3, z * 2) > 0.6 else 0)
            for k in range(max(0, hgt)):
                t.set(x, F + k, z, spoil(x, F + k, z))
            if hgt > 0:
                t.set(x, G, z, K.DIRT)
    # the way down: a ramp of trodden earth at the north end, ladders at the south
    for (x, z, s) in ((14, TZ0, K.stairs('minecraft:mud_brick_stairs', 'north')), (15, TZ0, K.stairs('minecraft:mud_brick_stairs', 'north'))):
        t.set(x, G, z, s)
    for x in (14, 15):
        t.set(x, G, TZ0 - 1, K.PATH)
    t.set(19, G, TZ1, K.ladder('north')) if False else None
    # the surveyors' boardwalk across the trench on beams
    for x in range(TX0 - 1, TX1 + 2):
        t.set(x, F, 17, K.DECK_SLAB_TOP if x % 3 else K.GRATE)
        if (x, 17) in pit and x % 4 == 0:
            t.set(x, G, 17, K.beam('y'))
    # the tripod crane over the pit: two posts, the beam across, a chain and a bucket
    for z in (TZ0 - 2, TZ1 + 2):
        K.column(t, 18, F, F + 7, z, K.beam('y'))
        t.set(18, G, z, K.GRAVEL)
    for z in range(TZ0 - 2, TZ1 + 3):
        t.set(18, F + 8, z, K.beam('z'))
    for (z, y) in ((TZ0 - 2, F + 9), (TZ1 + 2, F + 9)):
        t.set(18, y, z, K.LIGHT_RED)
    for y in range(F + 3, F + 8):
        t.set(18, y, 18, K.chain('y'))
    t.set(18, F + 2, 18, S('cauldron'))
    # floodlights on posts aimed at the pit; one shot down
    for (x, z) in ((TX0 - 1, TZ0 - 2), (TX1 + 1, TZ0 - 2), (TX0 - 1, TZ1 + 2)):
        K.column(t, x, F, F + 3, z, K.beam('y'))
        t.set(x, F + 4, z, K.LIGHT)
        t.set(x, G, z, K.GRAVEL)
    t.set(TX1 + 2, F, TZ1 + 2, K.beam('x'))
    t.set(TX1 + 3, F, TZ1 + 2, K.beam('x'))
    t.set(TX1 + 4, F, TZ1 + 2, K.LIGHT)
    for (x, z) in ((TX1 + 2, TZ1 + 2), (TX1 + 3, TZ1 + 2), (TX1 + 4, TZ1 + 2)):
        K.support(t, x, F, z)

    # --- the finds sorted on tarps north of the pit --------------------------------------------------------
    for x in range(9, 13):
        for z in (8, 9):
            t.set(x, F, z, K.carpet('white' if (x + z) % 2 else 'light_gray'))
    t.set(9, F, 8, S('decorated_pot', cracked=False, facing='north', waterlogged=False))
    t.set(11, F, 9, K.slab('or:ruin_brick_slab'))
    t.set(12, F, 8, K.RUIN_CARVED)
    t.set(10, F, 9, K.skull(4))
    t.set(13, F, 8, K.CRATE_IMP)
    t.set(13, F + 1, 8, K.holo('north'))

    # --- the command tent: canvas walls, a holo table, consoles ---------------------------------------------
    CX0, CX1, CZ0, CZ1 = 22, 27, 6, 11
    for x in range(CX0, CX1 + 1):
        for z in range(CZ0, CZ1 + 1):
            t.set(x, G, z, K.DECK if (x + z) % 3 else K.GRATE)
            wall = x in (CX0, CX1) or z in (CZ0, CZ1)
            for y in range(F, F + 3):
                if wall:
                    door = z == CZ0 and x in (24, 25) or (x == CX0 and z == 9)
                    t.set(x, y, z, AIR if door else (K.beam('y') if (x in (CX0, CX1) and z in (CZ0, CZ1)) else
                                                    (K.CANVAS_NAVY if y < F + 2 else K.CANVAS)))
                else:
                    t.set(x, y, z, AIR)
    for x in range(CX0 - 1, CX1 + 2):
        for z in range(CZ0 - 1, CZ1 + 2):
            k = min(z - (CZ0 - 1), (CZ1 + 1) - z)
            y = F + 3 + min(k, 3)
            t.set(x, F + 3 + min(k, 2), z, K.canvas_stairs('south') if z < (CZ0 + CZ1) / 2.0 - 0.5 else (
                K.canvas_stairs('north') if z > (CZ0 + CZ1) / 2.0 + 0.5 else K.CANVAS))
    for x in range(CX0, CX1 + 1):
        for z in range(CZ0 + 1, CZ1):
            for y in range(F + 3, F + 6):
                if t.get(x, y, z) is None:
                    lo = F + 3 + min(z - (CZ0 - 1), (CZ1 + 1) - z, 2)
                    if y < lo:
                        t.set(x, y, z, AIR)
    t.set(24, F, 8, IMP_SLAB_TOP)
    t.set(25, F, 8, IMP_SLAB_TOP)
    t.set(24, F + 1, 8, K.holo('north'))
    t.set(25, F + 1, 8, K.holo('north'))
    t.set(CX1 - 1, F, CZ1 - 1, K.console('west'))
    t.set(CX1 - 1, F, CZ1 - 2, K.console('west'))
    t.set(CX0 + 1, F, CZ1 - 1, K.CRATE_IMP)
    t.set(CX0 + 2, F, CZ1 - 1, K.CRATE_IMP)
    t.set(CX0 + 1, F + 1, CZ1 - 1, K.CRATE_IMP)
    t.set(CX1 - 1, F, CZ0 + 1, imp_stairs('north'))
    t.set(24, F + 3, 9, K.lantern(True)) if False else None
    t.set(24, F + 2, 9, K.LIGHT) if False else None
    mod.light_fill(t, (CX0 + 1, F, CZ0 + 1, CX1 - 1, F + 2, CZ1 - 1))
    K.banner(t, CX0 - 1, F + 2, CZ0 + 1, 'west', 'black')

    # --- the surveyors' tents to the west, the comm mast, the generator ---------------------------------------
    tw = K.a_tent(t, 2, 9, 5, 5, F, ridge='z', door='max', back=K.CANVAS_NAVY)
    for i in range(5):
        for j in range(5):
            x, z = tw(i, j)
            t.set(x, G, z, K.COARSE)
    x, z = tw(1, 1)
    K.bedroll(t, x, F, z, color='gray', along='z', length=3)
    x, z = tw(1, 3)
    K.bedroll(t, x, F, z, color='black', along='z', length=2)
    x, z = tw(3, 3)
    t.set(x, F, z, K.CRATE_IMP)
    tw2 = K.a_tent(t, 2, 17, 5, 5, F, ridge='z', door='min', back=K.CANVAS_NAVY,
                   patches={(2, 1, 'l'): K.SCORCHED, (3, 0, 'l'): AIR})
    for i in range(5):
        for j in range(5):
            x, z = tw2(i, j)
            t.set(x, G, z, K.COARSE)
    x, z = tw2(2, 3)
    K.bedroll(t, x, F, z, color='gray', along='z', length=2)
    mx, mz = 25, 16
    K.mast(t, mx, F, mz, 12, top=K.LIGHT_RED, arms=((7, 'west', 2), (10, 'south', 1), (10, 'north', 1)))
    t.set(mx, G, mz, K.GRAVEL)
    for (x, z) in ((mx - 1, mz + 1), (mx + 1, mz + 1)):
        t.set(x, F, z, K.CRATE_IMP)
    t.set(mx, F + 1, mz + 1, K.pipes('y')) if False else None
    for (x, z) in ((26, 20), (27, 20), (26, 21)):
        t.set(x, F, z, K.PLATING)
        t.set(x, G, z, K.GRAVEL)
    t.set(26, F + 1, 20, K.VENT)
    t.set(27, F + 1, 20, K.LIGHT_AMBER)
    # the squad's kit dumped by the command tent, an Imperial crate kicked open
    K.crate_pile(t, [(21, 6, 2), (20, 6, 1), (21, 7, 1)], F, rng, kinds=((K.CRATE_REP, 3), (K.CRATE, 1)))
    t.set(20, F, 9, K.CRATE_IMP)
    t.set(19, F, 10, IMP_SLAB)
    # survey stakes with red rags round the hilltop
    for (x, z) in ((4, 5), (10, 3), (22, 3), (28, 9), (28, 24), (20, 28), (9, 27), (2, 24)):
        t.set(x, F, z, K.fence('spruce_fence'))
        t.set(x, F + 1, z, K.carpet('red'))
        K.support(t, x, F, z)
    # where the fight went: scorch round the north end of the pit and the command tent's door
    K.scorch_burst(t, F, 15, 10, 3.0, rng, density=0.45, debris=0.3)
    K.scorch_burst(t, F, 24, 4, 2.2, rng, density=0.5, debris=0.25)
    K.lamp_post(t, 8, F, 12, height=3, post=K.beam('y'), lamp=K.LIGHT)

    # --- people ------------------------------------------------------------------------------------------
    K.spot(t, 'WOUNDED', 11, F, 10)          # by the finds he was cataloguing
    K.spot(t, 'SQUAD_1', 16, F, 7)           # holding the way in
    K.spot(t, 'SQUAD_2', 19, F, TZ1 + 3)     # at the crane's foot
    K.spot(t, 'SQUAD_3', 24, F, 5)           # at the command tent
    K.spot(t, 'SQUAD_4', 5, F, 15)           # between the tents
    K.spot(t, 'SQUAD_5', 15, F, 24)          # at the far end of the pit
    K.spot(t, 'HEART', 15, F, 10)            # the north lip of the dig

    K.litter(t, F, (3, 3, 28, 28), [(K.DEBRIS, 3), (K.SCORCH, 1)], 0.02, rng)
    return t


def main():
    a = K.args()
    for tid, build in (('mission/survey_post', build_mast_post), ('mission/survey_post_2', build_dig)):
        if a.only and a.only not in tid:
            continue
        K.save(build(), tid, ANCHORS, META)


if __name__ == '__main__':
    main()
