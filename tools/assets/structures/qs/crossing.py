#!/usr/bin/env python3
"""The Crossing and the mentors' encampments (docs/MILESTONE_6.md 3.5 and 5.3).

  story/crossing          35 x 16 x 35, buried 1, terrain flat. An old Watch waystation where the
                          pilgrim road (paved, east to west: the Watch one way, the tombs the other)
                          crosses the settlers' track (north to south, the way the player comes in).
                          A low-walled court with a dry two-tier fountain in the middle; its north gate
                          arch has fallen (one pier and its springers still stand, the rest lies where
                          travellers levered it off the track); two waystation lanterns inside the gate;
                          worn benches against the court wall; outside, the pilgrims' rest-house stands
                          roofless, an old oak leans over the road and a waymarker keeps its candles.
  story/jedi_encampment   the same size and origin: only the cells that change (the rest unset, so
  story/sith_encampment   the Crossing shows through). Ilyra Saelen's camp in canvas and navy, or
                          Teyvar Sorn's in crimson and black, built into the waystation once the player
                          has joined (Java stamps it over the placed Crossing, same origin and rotation).

Anchors (the same cells in all three): HEART (17, 1, 12), the meeting point on the track just inside
the gate; MENTOR_SITH (13, 1, 17) and MENTOR_JEDI (21, 1, 17), 8 apart on the pilgrim road either side
of the fountain, both looking north to the gate. All three are feet anchors.

The overlays also write again the Crossing's plants, leaves and loose natural ground above the ground
layer, so a stamp that fits the terrain first (which clears such blocks) still leaves the Crossing whole.

Usage: python3 tools/assets/structures/qs/crossing.py [--preview <dir>]
"""
import math
import sys

from common import (AIR, B, LANTERN, LANTERN_HUNG, S, SOUL_LANTERN, SOUL_LANTERN_HUNG, Noise, Template, barrel, bed,
                    by_noise, campfire, candle, carpet, check, column, fence, leaves, log, mod, overlay, pick,
                    plants_on, preview_dir, slab, spot, stairs, wall, write)

SIZE = (35, 16, 35)
C = 17
SEED = 3301
LO, HI = 9, 25           # the court's wall lines; its floor is 10..24
HEART = (17, 1, 12)
MENTOR_SITH = (13, 1, 17)
MENTOR_JEDI = (21, 1, 17)
ANCHORS = {HEART: 'HEART', MENTOR_SITH: 'MENTOR_SITH', MENTOR_JEDI: 'MENTOR_JEDI'}
ANCHOR_CELLS = [p for (x, y, z) in ANCHORS for p in ((x, y - 1, z), (x, y, z), (x, y + 1, z))]
META = dict(buried=1, terrain='flat', max_slope=3, clearance=10, support_depth=6, ramp_rings=2, clear_above=8,
            spawn_radius=64)

GRASS = S('grass_block', snowy=False)
PODZOL = S('podzol', snowy=False)
PAVE = [(S('stone_bricks'), 5), (S('cracked_stone_bricks'), 3), (S('mossy_stone_bricks'), 2),
        (S('polished_andesite'), 1), (S('cobblestone'), 1)]
MASONRY = [(S('or:ruin_bricks'), 7), (S('or:ruin_stone'), 2), (S('or:ruin_stone_cracked'), 2),
           (S('or:ruin_stone_mossy'), 2)]
GAP = [(GRASS, 5), (S('moss_block'), 2), (S('coarse_dirt'), 3)]
RUBBLE = [(slab('or:ruin_brick_slab'), 3), (S('mossy_cobblestone'), 2), (S('cobblestone'), 1)]


def dist(x, z, cx=C, cz=C):
    return math.hypot(x - cx, z - cz)


def in_court(x, z):
    return LO < x < HI and LO < z < HI


def on_wall(x, z):
    return (x in (LO, HI) and LO <= z <= HI) or (z in (LO, HI) and LO <= x <= HI)


def in_gateway(x, z):
    """The four openings in the court wall: x 15..19 north and south, z 15..19 west and east."""
    return (z in (LO, HI) and 15 <= x <= 19) or (x in (LO, HI) and 15 <= z <= 19)


def track_x(z):
    """The settlers' track's centre line, wandering a little outside the court."""
    return C + (-1 if z <= 2 else 0) + (1 if z >= 31 else 0)


def road_z(x):
    """The pilgrim road's centre line."""
    return C + (1 if x <= 3 else 0) + (-1 if x >= 31 else 0)


# ---------------------------------------------------------------- the ground

def lay_ground(t):
    """Layer 0: the court's pavers, the paved pilgrim road (pavers lost to turf toward the ends), the
    rutted earth track and its worn verges. Plain grass between the roads is left to the world."""
    pave_n = Noise(SEED + 1, 2.2)
    wear_n = Noise(SEED + 2, 3.5)
    lost_n = Noise(SEED + 3, 1.9)
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            st = None
            if in_court(x, z) or in_gateway(x, z):
                edge = min(x - LO, HI - x, z - LO, HI - z)
                if lost_n.at(x, 0, z) > 0.70 + 0.03 * edge and dist(x, z) > 4.5:
                    st = by_noise(wear_n, GAP, x, 0, z)
                else:
                    st = by_noise(pave_n, PAVE, x, 0, z)
            elif on_wall(x, z):
                st = S('or:ruin_bricks')
            elif abs(z - road_z(x)) <= 2:
                end = min(x, SIZE[0] - 1 - x)
                keep = 0.40 + 0.05 * min(end, 8) - (0.15 if abs(z - road_z(x)) == 2 else 0)
                st = by_noise(pave_n, PAVE, x, 0, z) if lost_n.at(x, 0, z) < keep else by_noise(wear_n, GAP, x, 0, z)
            elif abs(x - track_x(z)) <= 1:
                if x == track_x(z):
                    st = by_noise(wear_n, [(GRASS, 3), (S('coarse_dirt'), 2)], x, 0, z)
                else:
                    st = by_noise(wear_n, [(S('dirt_path'), 6), (S('coarse_dirt'), 2), (S('gravel'), 1)], x, 0, z)
            elif abs(z - road_z(x)) == 3 or abs(x - track_x(z)) == 2 or (on_ring(x, z)):
                if wear_n.at(x, 7, z) > 0.45:
                    st = by_noise(lost_n, [(S('coarse_dirt'), 3), (GRASS, 4), (S('gravel'), 1)], x, 0, z)
            if st is not None:
                t.set(x, 0, z, st)
    # Kerb stones along the pilgrim road outside the court; most were carried off long ago.
    for x in list(range(0, LO)) + list(range(HI + 1, SIZE[0])):
        for side in (-3, 3):
            z = road_z(x) + side
            if t.rng.random() < 0.4 and t.get(x, 0, z) is not None:
                t.set(x, 1, z, slab('or:ruin_brick_slab') if t.rng.random() < 0.7 else S('or:ruin_stone_mossy'))


def on_ring(x, z):
    """The trodden strip just outside the court wall."""
    return (x in (LO - 1, HI + 1) and LO - 1 <= z <= HI + 1) or (z in (LO - 1, HI + 1) and LO - 1 <= x <= HI + 1)


# ---------------------------------------------------------------- the court wall

def court_wall(t):
    """A low wall (a course of dressed stone and a coping), broken in places with its stones fallen
    outward; plain gateposts at the south, west and east gates (the north gate is the arch)."""
    n = Noise(SEED + 51, 2.6)
    for x in range(LO, HI + 1):
        for z in range(LO, HI + 1):
            if not on_wall(x, z) or in_gateway(x, z):
                continue
            if z == LO and (13 <= x <= 14 or 20 <= x <= 21):
                continue  # the arch piers stand here
            v = n.at(x, 0, z)
            h = 2 if v < 0.62 else (1 if v < 0.80 else 0)
            if h >= 1:
                t.set(x, 1, z, by_noise(n, MASONRY, x, 1, z))
            if h >= 2:
                t.set(x, 2, z, wall('or:ruin_brick_wall') if n.at(x, 5, z) < 0.7 else slab('or:ruin_brick_slab'))
            if h < 2:
                # its stones lie outside, below the gap
                ox = x + (-1 if x == LO else 1 if x == HI else 0)
                oz = z + (-1 if z == LO else 1 if z == HI else 0)
                if t.get(ox, 1, oz) is None and t.rng.random() < 0.7:
                    t.set(ox, 1, oz, pick(t.rng, RUBBLE))
                    if t.get(ox, 0, oz) is None:
                        t.set(ox, 0, oz, S('coarse_dirt'))
    # The corners stand a little taller.
    for (x, z) in ((LO, LO), (HI, LO), (LO, HI), (HI, HI)):
        column(t, x, 1, 2, z, S('or:ruin_bricks'))
        t.set(x, 3, z, slab('or:ruin_brick_slab'))
    # Gateposts.
    for (x, z) in ((14, HI), (20, HI), (LO, 14), (LO, 20), (HI, 14), (HI, 20)):
        t.set(x, 1, z, S('or:ruin_bricks'))
        t.set(x, 2, z, S('or:ruin_pillar', axis='y'))
        t.set(x, 3, z, S('or:ruin_stone_carved'))
    t.set(HI, 3, 20, slab('or:ruin_brick_slab'))      # one cap has split


# ---------------------------------------------------------------- the fountain

def fountain(t):
    n = Noise(SEED + 11, 1.7)
    for x in range(C - 4, C + 5):
        for z in range(C - 4, C + 5):
            d = dist(x, z)
            if d <= 2.6:
                t.set(x, 0, z, by_noise(n, [(S('or:ruin_stone_cracked'), 3), (S('packed_mud'), 3), (S('moss_block'), 2),
                                            (S('coarse_dirt'), 1)], x, 0, z))
                t.set(x, 1, z, AIR)
            elif d <= 3.6:
                t.set(x, 0, z, S('or:ruin_bricks'))
                t.set(x, 1, z, by_noise(n, MASONRY, x, 1, z))
                if n.at(x, 2, z) > 0.28:
                    t.set(x, 2, z, slab('or:ruin_brick_slab'))
    # The spout: a fluted shaft with two bowls and a finial.
    t.set(C, 1, C, S('or:ruin_bricks'))
    t.set(C, 2, C, S('or:ruin_pillar', axis='y'))
    t.set(C, 3, C, S('or:ruin_stone_carved'))
    for f, (dx, dz) in (('east', (-1, 0)), ('west', (1, 0)), ('south', (0, -1)), ('north', (0, 1))):
        t.set(C + dx, 3, C + dz, stairs('or:ruin_brick_stairs', f, 'top'))
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        t.set(C + dx, 3, C + dz, slab('or:ruin_brick_slab', 'top'))
    column(t, C, 4, 5, C, S('or:ruin_pillar', axis='y'))
    t.set(C, 6, C, S('or:ruin_stone_carved'))
    for f, (dx, dz) in (('east', (-1, 0)), ('west', (1, 0)), ('south', (0, -1)), ('north', (0, 1))):
        t.set(C + dx, 6, C + dz, slab('or:ruin_brick_slab', 'top'))
    t.set(C, 7, C, wall('or:ruin_brick_wall'))
    t.set(C + 1, 3, C + 1, S('or:ruin_stone_mossy'))   # the lower bowl crumbled on one side
    t.set(C + 1, 6, C, AIR)
    # What the dry basin holds now: drifted leaves, moss, weeds in the cracked mud.
    for (x, z, st) in ((15, 16, S('leaf_litter', facing='north', segment_amount=3)),
                       (18, 18, S('leaf_litter', facing='east', segment_amount=2)),
                       (16, 19, S('short_grass')), (19, 16, S('fern')), (15, 18, S('moss_carpet')),
                       (18, 15, S('dead_bush'))):
        if st.startswith(('minecraft:short_grass', 'minecraft:fern')):
            t.set(x, 0, z, S('moss_block'))
        if st.startswith('minecraft:dead_bush'):
            t.set(x, 0, z, S('coarse_dirt'))
        t.set(x, 1, z, st)


# ---------------------------------------------------------------- the fallen arch of the north gate

def arch(t):
    n = Noise(SEED + 21, 1.6)
    zs = (8, 9)

    def mason(x, y, z):
        return by_noise(n, MASONRY, x, y, z)
    # The west pier stands to its impost, with the springers of the arch reaching over the track.
    for z in zs:
        for x in (13, 14):
            t.set(x, 0, z, S('or:ruin_bricks'))
            t.set(x, 1, z, S('or:ruin_stone_carved'))
            for y in range(2, 8):
                t.set(x, y, z, mason(x, y, z))
            t.set(x, 8, z, S('or:ruin_stone_carved'))
        t.set(15, 8, z, mason(15, 8, z))
        t.set(15, 9, z, mason(15, 9, z))
        t.set(14, 9, z, mason(14, 9, z))
        t.set(16, 9, z, stairs('or:ruin_brick_stairs', 'west', 'top'))
        t.set(16, 10, z, mason(16, 10, z))
    t.set(17, 10, 8, stairs('or:ruin_brick_stairs', 'west', 'top'))  # one more voussoir hangs on
    for z in zs:
        t.set(12, 1, z, stairs('or:ruin_brick_stairs', 'east'))
    for x in (13, 14):
        t.set(x, 1, 7, stairs('or:ruin_brick_stairs', 'south'))
        t.set(x, 1, 10, stairs('or:ruin_brick_stairs', 'north'))
    # The east pier broke at shoulder height.
    for z in zs:
        for x in (20, 21):
            t.set(x, 0, z, S('or:ruin_bricks'))
            t.set(x, 1, z, S('or:ruin_stone_carved'))
            t.set(x, 2, z, mason(x, 2, z))
    t.set(20, 3, 8, mason(20, 3, 8))
    t.set(21, 3, 9, mason(21, 3, 9))
    t.set(21, 4, 9, slab('or:ruin_brick_slab'))
    t.set(20, 3, 9, slab('or:ruin_brick_slab'))
    t.set(22, 1, 8, stairs('or:ruin_brick_stairs', 'west'))
    # The arch came down across the track; travellers levered the pieces off it. The biggest piece
    # (stones still mortared together) lies on its side east of the track, the keystone west of it,
    # smaller stones along both verges; the pavers of the gateway are cracked where it struck.
    for (x, y, z) in ((21, 1, 4), (22, 1, 4), (23, 1, 5), (24, 1, 5), (22, 2, 4), (23, 2, 5)):
        t.set(x, y, z, mason(x, y, z))
        if t.get(x, 0, z) is None:
            t.set(x, 0, z, S('coarse_dirt'))
    t.set(24, 2, 5, stairs('or:ruin_brick_stairs', 'east', 'top'))
    t.set(25, 1, 6, slab('or:ruin_brick_slab'))
    t.set(15, 1, 5, S('or:ruin_stone_carved'))                         # the keystone
    t.set(15, 0, 5, S('coarse_dirt'))
    for (x, z) in ((20, 6), (19, 3), (21, 2), (14, 6), (12, 6), (20, 1), (25, 3), (13, 3)):
        t.set(x, 1, z, pick(t.rng, [(slab('or:ruin_brick_slab'), 3), (S('or:ruin_stone_mossy'), 1),
                                    (stairs('or:ruin_brick_stairs', pick(t.rng, [('north', 1), ('east', 1), ('west', 1)])), 2)]))
        if t.get(x, 0, z) is None:
            t.set(x, 0, z, S('coarse_dirt'))
    for x in range(15, 20):
        for z in zs:
            t.set(x, 0, z, pick(t.rng, [(S('cracked_stone_bricks'), 3), (S('gravel'), 1), (S('dirt_path'), 2)]))


# ---------------------------------------------------------------- lanterns, benches, trough

def lanterns(t):
    # West: a plain waystation post with its lantern on top.
    t.set(14, 1, 11, S('or:ruin_bricks'))
    column(t, 14, 2, 3, 11, wall('or:ruin_brick_wall'))
    t.set(14, 4, 11, LANTERN)
    # East: a taller post whose iron arm holds its lantern out over the track.
    t.set(20, 1, 11, S('or:ruin_stone_mossy'))
    column(t, 20, 2, 4, 11, wall('or:ruin_brick_wall'))
    t.set(20, 5, 11, S('or:ruin_stone_carved'))
    t.set(19, 5, 11, S('iron_bars', north=False, east=False, south=False, west=False, waterlogged=False))
    t.set(19, 4, 11, LANTERN_HUNG)


def benches(t):
    # North-west, against the wall: whole.
    for x in (10, 11, 12):
        t.set(x, 1, 10, stairs('or:ruin_brick_stairs', 'north'))
    # North-east: its middle seat broken out and lying on the pavers.
    t.set(22, 1, 10, stairs('or:ruin_brick_stairs', 'north'))
    t.set(24, 1, 10, stairs('or:ruin_brick_stairs', 'north'))
    t.set(23, 1, 10, S('or:debris'))
    t.set(23, 1, 12, stairs('or:ruin_brick_stairs', 'east'))
    # West wall, south part: a bench of mossy stone.
    for z in (21, 22, 23):
        t.set(10, 1, z, stairs('mossy_stone_brick_stairs', 'west'))
    # South-east: older, two seats and a timber plank someone laid across to mend it.
    t.set(21, 1, 24, stairs('mossy_stone_brick_stairs', 'south'))
    t.set(22, 1, 24, slab('spruce_slab'))
    t.set(23, 1, 24, stairs('mossy_stone_brick_stairs', 'south'))


def trough(t):
    """A stone trough and a hitching rail by the track outside the gate, for the travellers' beasts."""
    t.set(9, 1, 4, S('or:ruin_bricks'))
    t.set(13, 1, 4, S('or:ruin_bricks'))
    for x in (10, 11, 12):
        t.set(x, 1, 4, S('cauldron'))
    for x in range(9, 14):
        t.set(x, 0, 4, S('or:ruin_stone'))
        t.set(x, 0, 5, S('coarse_dirt'))
    for x in range(9, 14):
        t.set(x, 1, 2, fence('spruce_fence'))
        t.set(x, 0, 2, S('coarse_dirt'))
    t.set(10, 1, 2, AIR)                                                # a rail is down
    t.set(10, 1, 1, log('stripped_spruce_log', 'z'))


# ---------------------------------------------------------------- the rest-house

def rest_house(t):
    """The pilgrims' rest-house, roofless: x 1..7, z 27..33, its door north toward the road."""
    n = Noise(SEED + 31, 2.0)
    tops = Noise(SEED + 32, 3.0)
    x0, x1, z0, z1 = 1, 7, 27, 33
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if not edge:
                t.set(x, 0, z, by_noise(n, [(S('or:ruin_stone'), 3), (S('or:ruin_stone_cracked'), 2), (S('moss_block'), 1),
                                            (S('coarse_dirt'), 1)], x, 0, z))
                for y in range(1, 5):
                    t.set(x, y, z, AIR)
                continue
            t.set(x, 0, z, S('or:ruin_bricks'))
            # The walls crumble toward the south-west corner, where the roof fell in.
            h = 4 + int(tops.at(x, 0, z) * 2.2) - (2 if (x <= 3 and z >= 31) else 0)
            h = max(1, min(5, h))
            for y in range(1, h + 1):
                t.set(x, y, z, by_noise(n, MASONRY, x, y, z))
            if h >= 4 and tops.at(x, 9, z) > 0.5:
                t.set(x, h + 1, z, slab('or:ruin_brick_slab'))
    # The doorway (2 wide), windows east and west.
    for x in (4, 5):
        for y in range(1, 4):
            t.set(x, y, z0, AIR)
        t.set(x, 0, z0, S('or:ruin_stone_cracked'))
        t.set(x, 0, z0 - 1, S('coarse_dirt'))
    t.set(4, 4, z0, S('or:ruin_stone_carved'))
    t.set(5, 4, z0, S('or:ruin_stone_carved'))
    for z in (29, 30):
        t.set(x1, 2, z, AIR)
        t.set(x1, 3, z, AIR)
    t.set(x1, 1, 29, stairs('or:ruin_brick_stairs', 'west', 'top'))
    t.set(x1, 1, 30, stairs('or:ruin_brick_stairs', 'west', 'top'))
    t.set(x0, 2, 29, AIR)
    t.set(x0, 3, 29, AIR)
    # One tie-beam still spans the room on its pilasters; another came down from the east wall.
    for x in range(x0, x1 + 1):
        t.set(x, 5, 28, log('stripped_spruce_log', 'x'))
    column(t, x0, 1, 4, 28, lambda x, y, z: by_noise(n, MASONRY, x, y, z))
    column(t, x1, 1, 4, 28, lambda x, y, z: by_noise(n, MASONRY, x, y, z))
    for (x, y, z) in ((6, 3, 31), (5, 2, 31), (4, 2, 30), (3, 1, 30)):
        t.set(x, y, z, log('stripped_spruce_log', 'x'))
    column(t, x1, 1, 3, 31, lambda x, y, z: by_noise(n, MASONRY, x, y, z))
    # The hearth on the west wall, its fire long dead.
    t.set(x0 + 1, 1, 30, S('cobblestone'))
    t.set(x0 + 1, 1, 32, S('cobblestone'))
    t.set(x0 + 1, 2, 30, stairs('cobblestone_stairs', 'east', 'top'))
    t.set(x0 + 1, 2, 32, stairs('cobblestone_stairs', 'east', 'top'))
    t.set(x0 + 1, 2, 31, S('cobblestone'))
    t.set(x0 + 1, 1, 31, campfire(False, facing='east'))
    t.set(x0 + 1, 0, 31, S('cobblestone'))
    t.set(x0 + 2, 1, 31, S('or:scorch_mark'))
    # A stone bench along the north wall inside, moss and leaves on the floor.
    t.set(2, 1, 28, stairs('or:ruin_brick_stairs', 'north'))
    t.set(3, 1, 28, stairs('or:ruin_brick_stairs', 'north'))
    t.set(6, 1, 28, S('moss_carpet'))
    t.set(5, 1, 32, S('moss_carpet'))
    t.set(6, 1, 30, S('leaf_litter', facing='south', segment_amount=3))
    # Rubble of the fallen corner, inside and spilling out.
    for (x, y, z) in ((2, 1, 32), (3, 1, 32), (2, 2, 32), (0, 1, 33), (0, 1, 32), (1, 1, 34), (2, 1, 34), (0, 1, 31)):
        t.set(x, y, z, pick(t.rng, RUBBLE))
        if y == 1 and t.get(x, 0, z) is None:
            t.set(x, 0, z, S('coarse_dirt'))
    # A birch grew up where the roof let the light in.
    bx, bz = 4, 32
    column(t, bx, 1, 6, bz, log('birch_log'))
    t.set(bx, 0, bz, S('rooted_dirt'))
    for (dx, dy, dz) in ((0, 7, 0), (-1, 6, 0), (1, 6, 0), (0, 6, -1), (0, 6, 1), (-1, 5, -1), (1, 5, 1), (1, 5, -1),
                         (0, 8, 0), (-1, 7, 0), (0, 7, 1), (1, 7, 0), (0, 7, -1), (2, 6, 0), (-2, 6, 1), (1, 7, 1),
                         (-1, 7, -1), (2, 5, 1), (-2, 5, -1)):
        p = (bx + dx, dy, bz + dz)
        if t.get(*p) in (None, AIR):
            t.set(*p, leaves('birch_leaves'))
    # A path from the road to its door.
    for z in range(20, z0 - 1):
        for x in (4, 5):
            if t.get(x, 0, z) is None:
                t.set(x, 0, z, S('dirt_path') if t.rng.random() < 0.7 else S('coarse_dirt'))


# ---------------------------------------------------------------- the old oak and the waymarker

def oak(t):
    """An old oak north-east of the court, its roots heaving the verge, its crown over the corner."""
    tx, tz = 29, 5
    n = Noise(SEED + 41, 1.8)
    for (x, z) in ((tx, tz), (tx + 1, tz), (tx, tz + 1), (tx + 1, tz + 1)):
        column(t, x, 0, 6, z, log('oak_log'))
    column(t, tx, 7, 9, tz + 1, log('oak_log'))
    for (x, z, a) in ((tx - 1, tz, 'x'), (tx + 2, tz + 1, 'x'), (tx, tz - 1, 'z'), (tx + 1, tz + 2, 'z'),
                      (tx - 2, tz + 1, 'x'), (tx + 2, tz - 1, 'z'), (tx - 1, tz + 2, 'x')):
        t.set(x, 1, z, log('oak_log', a))
        t.set(x, 0, z, S('rooted_dirt'))
    for (x, z) in ((tx - 1, tz - 1), (tx + 2, tz + 2), (tx + 3, tz), (tx, tz + 3), (tx - 3, tz), (tx - 2, tz + 2)):
        t.set(x, 0, z, PODZOL)
    # Limbs: a long one reaching west over the court corner, others up and out.
    limbs = [((tx - 1, 6, tz + 1), (tx - 5, 9, tz + 3)), ((tx + 1, 6, tz), (tx + 4, 9, tz - 2)),
             ((tx, 7, tz + 1), (tx - 1, 11, tz - 2)), ((tx + 1, 7, tz + 1), (tx + 3, 10, tz + 4))]
    tips = []
    for (a, b) in limbs:
        steps = max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2]))
        for i in range(steps + 1):
            f = i / steps
            p = (round(a[0] + (b[0] - a[0]) * f), round(a[1] + (b[1] - a[1]) * f), round(a[2] + (b[2] - a[2]) * f))
            dx, dy, dz = abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2])
            ax = 'y' if dy >= max(dx, dz) else ('x' if dx >= dz else 'z')
            t.set(*p, log('oak_log', ax))
        tips.append(b)
    tips.append((tx, 10, tz + 1))
    # Leaf clusters round each limb tip: a lobed, broken crown rather than one ball.
    for (cx, cy, cz) in tips:
        r = 2.6
        for x in range(int(cx - 4), int(cx + 5)):
            for y in range(int(cy - 2), int(cy + 3)):
                for z in range(int(cz - 4), int(cz + 5)):
                    d = ((x - cx) / r) ** 2 + ((y - cy - 0.5) / 1.8) ** 2 + ((z - cz) / r) ** 2
                    if d <= 1.0 and n.at(x, y, z) > 0.12 + 0.45 * d and t.inside(x, y, z) and t.get(x, y, z) is None:
                        t.set(x, y, z, leaves('oak_leaves'))
    # Leaf litter under it.
    for (x, z) in ((tx - 2, tz - 2), (tx - 3, tz + 2), (tx + 1, tz - 3), (tx - 2, tz + 3), (tx + 3, tz + 3), (tx - 4, tz)):
        if t.get(x, 0, z) is None:
            t.set(x, 0, z, GRASS)
        if t.get(x, 1, z) is None:
            t.set(x, 1, z, S('leaf_litter', facing='north', segment_amount=2))


def waymarker(t):
    """A pilgrims' waymarker outside the south gate: a carved post, candles and an old pot at its foot."""
    x, z = 21, 28
    t.set(x, 0, z, S('or:ruin_bricks'))
    t.set(x, 1, z, S('or:ruin_stone_carved'))
    column(t, x, 2, 3, z, S('or:ruin_pillar', axis='y'))
    t.set(x, 4, z, S('or:ruin_stone_carved'))
    t.set(x, 5, z, slab('or:ruin_brick_slab'))
    t.set(x - 1, 1, z, candle('white', 2, True))
    t.set(x, 1, z - 1, candle(None, 1, True))
    t.set(x + 1, 1, z, S('flower_pot'))
    t.set(x - 1, 0, z, S('or:ruin_stone'))
    t.set(x, 0, z - 1, S('or:ruin_stone'))
    t.set(x + 1, 0, z, S('or:ruin_stone_mossy'))


def greenery(t):
    """Flowers and grass on turf the template laid (never on the world's own ground)."""
    cells = [(x, 0, z) for x in range(SIZE[0]) for z in range(SIZE[2]) if t.get(x, 0, z) is not None]
    plants_on(t, cells, t.rng, 0.3)


def crossing():
    t = Template(*SIZE, seed=SEED, name='story/crossing')
    lay_ground(t)
    court_wall(t)
    fountain(t)
    arch(t)
    lanterns(t)
    benches(t)
    trough(t)
    rest_house(t)
    oak(t)
    waymarker(t)
    greenery(t)
    for (x, y, z), name in ANCHORS.items():
        if t.get(x, 0, z) is None or not t.is_solid(x, 0, z):
            t.set(x, 0, z, S('stone_bricks'))
        spot(t, name, x, y, z)
    t.finish()
    check(t, SIZE, feet=('HEART', 'MENTOR_JEDI', 'MENTOR_SITH'))
    return t


# ---------------------------------------------------------------- the encampments: shared pieces

def clone(t, name):
    """A copy of a template (cells, block entity data and anchors) under another name."""
    c = Template(*t.size, seed=t.seed, name=name)
    c.cells = dict(t.cells)
    c.nbt = dict(t.nbt)
    c.anchors = {k: [list(p) for p in v] for k, v in t.anchors.items()}
    return c


class Frame:
    """Local coordinates of a tent or pavilion: u across (0 .. w - 1), v along (0 .. l - 1), from the
    corner (x0, z0). axis 'z': the ridge runs north-south (u east, v south); axis 'x': west-east (u
    south, v east). door 'start' puts the open end at v = 0, 'end' at v = l - 1."""

    def __init__(self, t, x0, z0, w, l, axis, door):
        self.t, self.x0, self.z0, self.w, self.l, self.axis, self.door = t, x0, z0, w, l, axis, door
        self.fu, self.bu = ('east', 'west') if axis == 'z' else ('south', 'north')
        fv, bv = ('south', 'north') if axis == 'z' else ('east', 'west')
        # "in" points from the door toward the back.
        self.inward, self.outward = (fv, bv) if door == 'start' else (bv, fv)

    def xz(self, u, d):
        """The cell u across and d deep from the door row (d = 0 the door row, l - 1 the back)."""
        v = d if self.door == 'start' else self.l - 1 - d
        return (self.x0 + u, self.z0 + v) if self.axis == 'z' else (self.x0 + v, self.z0 + u)

    def set(self, u, y, d, state, nbt=None):
        x, z = self.xz(u, d)
        self.t.set(x, y, z, state, nbt=nbt)

    def get(self, u, y, d):
        x, z = self.xz(u, d)
        return self.t.get(x, y, z)

    def door_cell(self, y=1, out=1):
        """The cell in front of the door, `out` cells out."""
        return self.xz(self.w // 2, -out) + (y,)


def tent_floor(f, states, rng):
    for d in range(f.l):
        for u in range(f.w):
            f.set(u, 0, d, pick(rng, states))


def jedi_tent(t, x0, z0, l, axis, door, rng, cots=('light_gray', 'white')):
    """A ridge tent of cream canvas on navy skirts: 5 across, l long, the door at one end; two cots
    head to the back, a crate and a barrel by the door, a lantern hanging from the ridge."""
    f = Frame(t, x0, z0, 5, l, axis, door)
    tent_floor(f, [(S('coarse_dirt'), 3), (PODZOL, 2), (S('dirt_path'), 1)], rng)
    for d in range(l):
        f.set(0, 1, d, S('or:canvas_navy'))
        f.set(4, 1, d, S('or:canvas_navy'))
        f.set(0, 2, d, stairs('or:canvas_stairs', f.fu))
        f.set(4, 2, d, stairs('or:canvas_stairs', f.bu))
        f.set(1, 3, d, stairs('or:canvas_stairs', f.fu))
        f.set(3, 3, d, stairs('or:canvas_stairs', f.bu))
        f.set(2, 4, d, slab('or:canvas_slab'))
        for u in (1, 2, 3):
            for y in (1, 2):
                f.set(u, y, d, AIR)
        f.set(2, 3, d, AIR)
    back = l - 1
    for u in (1, 2, 3):
        for y in (1, 2):
            f.set(u, y, back, S('or:canvas'))
    f.set(2, 3, back, S('or:canvas'))
    for u in (1, 3):
        f.set(u, 1, 0, S('or:canvas_navy'))
        f.set(u, 2, 0, S('or:canvas_navy'))
    f.set(2, 3, 0, S('or:canvas'))
    # Inside: the cots head to the back wall, a runner down the middle, kit by the door.
    for u, color in zip((1, 3), cots):
        hx, hz = f.xz(u, back - 1)
        fx, fz = f.xz(u, back - 2)
        t.set(fx, 1, fz, S('%s_bed' % color, facing=f.inward, part='foot', occupied=False))
        t.set(hx, 1, hz, S('%s_bed' % color, facing=f.inward, part='head', occupied=False))
    for d in range(1, back):
        f.set(2, 1, d, carpet('brown'))
    if back - 2 > 1:
        f.set(1, 1, 1, S('or:cargo_crate_republic'))
        f.set(3, 1, 1, barrel('up'))
    f.set(2, 3, max(1, back // 2), LANTERN_HUNG)
    return f


def sith_tent(t, x0, z0, l, axis, door, rng, cots=('red', 'black')):
    """A field tent in black and crimson: straight black walls, a crimson roof stepped to a black
    ridge; two cots, an Imperial crate and a barrel, a soul lantern hanging from the ridge."""
    f = Frame(t, x0, z0, 5, l, axis, door)
    tent_floor(f, [(S('gravel'), 2), (S('coarse_dirt'), 3)], rng)
    for d in range(l):
        for y in (1, 2):
            f.set(0, y, d, S('black_wool'))
            f.set(4, y, d, S('black_wool'))
        f.set(0, 3, d, carpet('black'))
        f.set(4, 3, d, carpet('black'))
        f.set(1, 3, d, S('or:canvas_crimson'))
        f.set(3, 3, d, S('or:canvas_crimson'))
        f.set(2, 4, d, S('black_wool'))
        f.set(1, 4, d, carpet('red'))
        f.set(3, 4, d, carpet('red'))
        for u in (1, 2, 3):
            for y in (1, 2):
                f.set(u, y, d, AIR)
        f.set(2, 3, d, AIR)
    back = l - 1
    for u in (1, 2, 3):
        for y in (1, 2):
            f.set(u, y, back, S('black_wool'))
    f.set(2, 3, back, S('or:canvas_crimson'))
    for u in (1, 3):
        f.set(u, 1, 0, S('or:canvas_crimson'))
        f.set(u, 2, 0, S('or:canvas_crimson'))
    f.set(2, 3, 0, S('or:canvas_crimson'))
    for u, color in zip((1, 3), cots):
        hx, hz = f.xz(u, back - 1)
        fx, fz = f.xz(u, back - 2)
        t.set(fx, 1, fz, S('%s_bed' % color, facing=f.inward, part='foot', occupied=False))
        t.set(hx, 1, hz, S('%s_bed' % color, facing=f.inward, part='head', occupied=False))
    for d in range(1, back):
        f.set(2, 1, d, carpet('black'))
    if back - 2 > 1:
        f.set(1, 1, 1, S('or:cargo_crate_imperial'))
        f.set(3, 1, 1, barrel('up'))
    f.set(2, 3, max(1, back // 2), SOUL_LANTERN_HUNG)
    return f


def lamp_post(t, x, z, kind):
    """A lamp on a post: 'jedi' a lantern on a spruce post, 'sith' a soul lantern on a black stone
    post, 'red' a red deck light on a black stone post."""
    if kind == 'jedi':
        column(t, x, 1, 2, z, fence('spruce_fence'))
        t.set(x, 3, z, LANTERN)
    else:
        column(t, x, 1, 2, z, wall('polished_blackstone_wall'))
        t.set(x, 3, z, SOUL_LANTERN if kind == 'sith' else S('or:light_panel_red'))
    if t.get(x, 0, z) is None:
        t.set(x, 0, z, S('coarse_dirt'))


def crates(t, cells, block):
    for (x, y, z) in cells:
        t.set(x, y, z, S(block))
        if y == 1 and t.get(x, 0, z) is None:
            t.set(x, 0, z, S('coarse_dirt'))


def clear_rest_house(t):
    """Both mentors clear the rest-house first: the fallen beam and the rubble inside go, the hearth
    is mended, the leaves swept out."""
    for p in ((3, 1, 30), (4, 2, 30), (5, 2, 31), (6, 3, 31), (3, 1, 32), (2, 2, 32), (6, 1, 30), (6, 1, 28)):
        t.set(*p, AIR)
    t.set(2, 1, 32, S('cobblestone'))
    t.set(2, 1, 31, campfire(True, facing='east'))
    t.set(3, 1, 31, AIR)


def training_ground(t, floor):
    """The trodden patch south of the court (x 9..14, z 27..33) where the side drills."""
    n = Noise(SEED + 301, 1.8)
    for x in range(9, 15):
        for z in range(27, 34):
            if n.at(x, 0, z) > 0.25 or (10 <= x <= 13 and 28 <= z <= 32):
                t.set(x, 0, z, by_noise(n, floor, x, 0, z))
                t.set(x, 1, z, AIR)


def heart_mat(t, colours):
    """A mat of three carpets just north of the HEART, between the waystation's two lamps: where the
    mentor (who stands at the HEART once the camp is set) receives the student. It keeps the walkway."""
    hx, hy, hz = HEART
    for dx, colour in zip((-1, 0, 1), colours):
        t.set(hx + dx, hy, hz - 1, carpet(colour))


def banner_pair(t, base, patterns):
    """Two banners on the north face of the standing arch pier, over the gate."""
    for x in (13, 14):
        mod.banner(t, x, 6, 7, 'north', base, patterns)


# ---------------------------------------------------------------- the Jedi encampment

CHEVRON = [('oldrepublic:republic_chevron', 'white')]


def jedi_encampment(base):
    """Ilyra Saelen's camp: the Watch has come back to its waystation. The fountain and the trough
    hold water again, the broken bench is mended; canvas tents on navy skirts stand west and south-east
    of the court, the rest-house is roofed for Saelen's quarters, a cook fire burns by the old bench,
    and under the oak a Watch sentinel's meditation mat with a kyber shard."""
    t = clone(base, 'story/jedi_encampment')
    rng = Noise(SEED + 101, 1.0)
    r = __import__('random').Random(SEED + 101)
    # Water again, in the basin and the trough.
    for x in range(C - 3, C + 4):
        for z in range(C - 3, C + 4):
            if dist(x, z) <= 2.6 and (x, z) != (C, C):
                t.set(x, 1, z, S('water', level=0))
                if t.name_at(x, 0, z) in ('minecraft:coarse_dirt',):
                    t.set(x, 0, z, S('packed_mud'))
    for x in (10, 11, 12):
        t.set(x, 1, 4, S('water_cauldron', level=3))
    # The broken bench mended, its fallen seat set back.
    t.set(23, 1, 10, stairs('or:ruin_brick_stairs', 'north'))
    t.set(23, 1, 12, AIR)
    # Saelen's teaching mat at the camp's heart, between the two lamps: her student kneels on it.
    heart_mat(t, ('blue', 'white', 'blue'))
    # Tents: the sentinels' long tent west of the court (door to the track), two more south-east.
    jedi_tent(t, 1, 6, 7, 'x', 'end', r)
    jedi_tent(t, 27, 22, 6, 'z', 'start', r, cots=('white', 'light_gray'))
    jedi_tent(t, 27, 29, 6, 'x', 'start', r, cots=('light_gray', 'light_gray'))
    lamp_post(t, 8, 7, 'jedi')
    lamp_post(t, 32, 21, 'jedi')
    lamp_post(t, 26, 28, 'jedi')
    mod.banner(t, 26, 1, 22, 'north', 'blue', CHEVRON, wall=False)
    # Supplies: Republic crates in the court's north-east corner and by the long tent.
    crates(t, [(24, 1, 12), (24, 1, 13), (24, 2, 12), (23, 1, 13)], 'or:cargo_crate_republic')
    crates(t, [(1, 1, 12), (2, 1, 12), (1, 2, 12), (1, 1, 13)], 'or:cargo_crate_republic')
    t.set(3, 1, 12, barrel('up'))
    # The cook fire by the mossy bench: a spit over the coals, the kettle, a water barrel.
    t.set(12, 0, 22, S('cobblestone'))
    t.set(12, 1, 22, campfire(True, facing='north'))
    column(t, 12, 1, 2, 21, fence('spruce_fence'))
    column(t, 12, 1, 2, 23, fence('spruce_fence'))
    t.set(12, 2, 22, S('iron_chain', axis='z', waterlogged=False))
    t.set(13, 1, 23, S('cauldron'))
    t.set(10, 1, 24, barrel('up'))
    t.set(11, 1, 24, S('or:cargo_crate_republic'))
    t.set(14, 1, 22, log('stripped_spruce_log', 'z'))
    # Saelen's quarters in the rest-house: a canvas roof over the north half, her cot, a lectern
    # with the Watch's daybook, the hearth lit again.
    clear_rest_house(t)
    for x in range(2, 7):
        for z in (29, 30):
            t.set(x, 5, z, slab('or:canvas_slab'))
        t.set(x, 5, 31, S('or:canvas_navy'))
    bed(t, 6, 1, 30, 'light_gray', 'north')
    t.set(6, 1, 31, barrel('up'))
    mod.lectern(t, 6, 1, 32, 'west', book=True)
    for z in (28, 29, 30, 31):
        t.set(4, 1, z, carpet('blue'))
    t.set(3, 1, 30, S('decorated_pot', facing='south', cracked=False, waterlogged=False))
    t.set(4, 4, 29, LANTERN_HUNG)
    t.set(4, 4, 30, AIR)
    # The meditation mat under the oak: a carved stone set in moss, a kyber shard, candles.
    for x in range(28, 31):
        for z in range(9, 12):
            t.set(x, 0, z, S('moss_block'))
            t.set(x, 1, z, AIR)
    t.set(29, 0, 10, S('or:ruin_stone_carved'))
    t.set(29, 1, 10, carpet('light_blue'))
    t.set(29, 0, 8, S('or:ruin_stone_carved'))
    t.set(29, 1, 8, S('or:kyber_cluster', facing='up'))
    for (x, z) in ((28, 9), (30, 11), (30, 9), (28, 11)):
        t.set(x, 1, z, candle('white', 1 + (x + z) % 3, True))
    # A training ground south of the court: two stumps for staff drills, staves, a bench to watch from.
    training_ground(t, [(S('dirt_path'), 3), (S('coarse_dirt'), 2), (GRASS, 1)])
    for (x, z) in ((10, 29), (13, 31)):
        t.set(x, 1, z, log('oak_log'))
        t.set(x, 2, z, log('stripped_oak_log'))
        t.set(x + 1, 1, z, S('or:debris'))
    t.set(9, 1, 29, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(9, 2, 29, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    for x in (11, 12, 13):
        t.set(x, 1, 33, log('stripped_spruce_log', 'x'))
    lamp_post(t, 14, 28, 'jedi')
    # The Watch's colours on the arch again.
    banner_pair(t, 'blue', CHEVRON)
    return t


# ---------------------------------------------------------------- the Sith encampment

def sith_encampment(base):
    """Teyvar Sorn's field camp: the Empire holds the crossroads. A command pavilion with a war table
    west of the court, black-and-crimson tents south-east, Sorn's quarters in the rest-house, red deck
    lights at a checkpoint on the north gate, soul lanterns in the waystation lamps, Imperial crates
    stacked in the court and a cook fire by the south bench. The fountain is left dry."""
    t = clone(base, 'story/sith_encampment')
    r = __import__('random').Random(SEED + 201)
    # The waystation's lamps burn cold now; between them, at the camp's heart, Sorn's kneeling mat.
    t.set(14, 4, 11, SOUL_LANTERN)
    t.set(19, 4, 11, SOUL_LANTERN_HUNG)
    heart_mat(t, ('black', 'red', 'black'))
    # The command pavilion: black stone posts, a stepped crimson roof, the war table under it.
    x0, x1, z0, z1 = 1, 7, 6, 10
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, S('or:durasteel_deck') if (x0 < x < x1 and z0 < z < z1) else S('gravel'))
            for y in (1, 2, 3):
                t.set(x, y, z, AIR)
            t.set(x, 4, z, S('or:canvas_crimson'))
            if z0 < z < z1:
                t.set(x, 5, z, S('black_wool'))
        t.set(x, 6, (z0 + z1) // 2, S('or:canvas_crimson'))
        t.set(x, 5, z0, carpet('black'))
        t.set(x, 5, z1, carpet('black'))
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (4, z0), (4, z1)):
        column(t, x, 1, 3, z, wall('polished_blackstone_wall'))
    for x in (3, 5):
        t.set(x, 1, 8, slab('or:imperial_plating_slab', 'top'))
        t.set(x, 1, 7, slab('or:imperial_plating_slab', 'top'))
    t.set(4, 1, 7, slab('or:imperial_plating_slab', 'top'))
    t.set(4, 1, 8, S('or:holo_screen', facing='south'))
    t.set(2, 1, 9, S('or:console', facing='east'))
    t.set(6, 1, 9, S('or:console', facing='west'))
    t.set(2, 3, 7, SOUL_LANTERN_HUNG)
    t.set(6, 3, 9, SOUL_LANTERN_HUNG)
    t.set(4, 3, 8, S('or:light_panel_red'))
    # A weapon rack on the pavilion's west side, crates stacked east.
    column(t, 0, 1, 2, 7, fence('dark_oak_fence'))
    column(t, 0, 1, 2, 9, fence('dark_oak_fence'))
    t.set(0, 3, 7, log('stripped_dark_oak_log', 'z'))
    t.set(0, 3, 8, log('stripped_dark_oak_log', 'z'))
    t.set(0, 3, 9, log('stripped_dark_oak_log', 'z'))
    for z in (7, 8, 9):
        t.set(0, 1, z, S('lightning_rod', facing='up', powered=False, waterlogged=False)) if z == 8 else None
    t.set(0, 0, 8, S('gravel'))
    crates(t, [(8, 1, 9), (8, 1, 10), (8, 2, 10), (8, 1, 11), (7, 1, 12)], 'or:cargo_crate_imperial')
    mod.banner(t, 4, 1, 12, 'south', 'red', [], wall=False)
    # Tents south-east of the court.
    sith_tent(t, 27, 22, 6, 'z', 'start', r)
    sith_tent(t, 27, 29, 6, 'x', 'start', r, cots=('black', 'red'))
    lamp_post(t, 32, 21, 'red')
    lamp_post(t, 26, 28, 'sith')
    mod.banner(t, 26, 1, 22, 'north', 'black', [], wall=False)
    # A checkpoint on the north gate: red deck lights either side of the track, crates as a barrier.
    lamp_post(t, 15, 7, 'red')
    lamp_post(t, 19, 7, 'red')
    crates(t, [(15, 1, 6), (19, 1, 6), (20, 1, 7)], 'or:cargo_crate_imperial')
    # Imperial crates in the court's north-east corner, the fallen bench seat left where it lies.
    crates(t, [(24, 1, 11), (24, 1, 12), (24, 2, 12), (24, 1, 13), (23, 1, 13), (24, 2, 13)], 'or:cargo_crate_imperial')
    t.set(22, 1, 13, barrel('up'))
    # The cook fire by the south-east bench, ringed in black stone.
    t.set(22, 0, 22, S('polished_blackstone'))
    t.set(22, 1, 22, campfire(True, facing='south'))
    for (x, z, fc) in ((21, 22, 'east'), (23, 22, 'west'), (22, 21, 'south')):
        t.set(x, 1, z, stairs('polished_blackstone_brick_stairs', fc))
    t.set(24, 1, 22, barrel('up'))
    t.set(24, 1, 23, S('cauldron'))
    # Sorn's quarters in the rest-house: a crimson roof, his cot, a comm unit, the hearth lit.
    clear_rest_house(t)
    for x in range(2, 7):
        for z in (29, 30):
            t.set(x, 5, z, S('or:canvas_crimson'))
        t.set(x, 5, 31, S('black_wool'))
    bed(t, 6, 1, 30, 'red', 'north')
    t.set(6, 1, 31, S('or:console', facing='west'))
    t.set(6, 2, 31, S('or:holo_screen', facing='west'))
    t.set(6, 1, 32, S('or:cargo_crate_imperial'))
    for z in (28, 29, 30, 31):
        t.set(4, 1, z, carpet('black'))
    column(t, 3, 1, 2, 30, wall('polished_blackstone_wall'))
    t.set(3, 3, 30, S('or:light_panel_red'))
    t.set(4, 4, 29, SOUL_LANTERN_HUNG)
    # A drill yard south of the court: two black posts hung with chains, a rack of practice blades.
    training_ground(t, [(S('gravel'), 3), (S('coarse_dirt'), 2)])
    for (x, z) in ((10, 29), (13, 31)):
        column(t, x, 1, 2, z, wall('polished_blackstone_wall'))
        t.set(x, 3, z, S('polished_blackstone'))
        t.set(x + 1, 2, z, S('iron_chain', axis='x', waterlogged=False))
        t.set(x + 1, 1, z, S('or:scorch_mark'))
    for z in (32, 33):
        column(t, 9, 1, 2, z, fence('dark_oak_fence'))
    t.set(9, 3, 32, log('stripped_dark_oak_log', 'z'))
    t.set(9, 3, 33, log('stripped_dark_oak_log', 'z'))
    t.set(10, 1, 32, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(10, 1, 33, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    lamp_post(t, 14, 28, 'red')
    # The Empire's colours on the arch.
    banner_pair(t, 'black', [('minecraft:stripe_center', 'red')])
    return t


def finish_encampment(t, base):
    """Anchors as in the Crossing, connections joined in the whole, then only the changed cells."""
    for (x, y, z), name in ANCHORS.items():
        t.set(x, 0, z, base.get(x, 0, z))
        t.set(x, y, z, AIR)
        t.set(x, y + 1, z, AIR)
    t.finish()
    check(t, SIZE, feet=('HEART', 'MENTOR_JEDI', 'MENTOR_SITH'))
    out = overlay(base, t, 0, keep_cells=ANCHOR_CELLS)
    check(out, SIZE, feet=('HEART', 'MENTOR_JEDI', 'MENTOR_SITH'))
    return out


def report_overwrites(base, full):
    """Base cells (not air, not clutter) the encampment changed: a list to read, so nothing of the
    Crossing goes by accident."""
    from common import clutter
    rows = []
    for p in sorted(base.cells, key=lambda q: (q[1], q[2], q[0])):
        a, b = base.cells[p], full.cells.get(p)
        if a == b or B.name_of(a) == 'minecraft:air' or clutter(a):
            continue
        if B.name_of(a) == B.name_of(b or 'minecraft:air'):
            continue  # a changed connection only
        rows.append('  %r %s -> %s' % (p, B.name_of(a)[10:], B.name_of(b)[10:] if b else 'unset'))
    print('%s changes %d built cells of the Crossing:' % (full.name, len(rows)))
    print('\n'.join(rows))


def main(argv):
    prev = preview_dir(argv)
    base = crossing()
    write(base, 'story/crossing', prev, META, views=('se', 'nw'), plans=[0, 1])
    for build in (jedi_encampment, sith_encampment):
        full = build(base)
        if '--verbose' in argv:
            report_overwrites(base, full)
        out = finish_encampment(full, base)
        write(out, full.name, None, META, finish=False)
        if prev:
            from common import render
            full.name = full.name + ' (over the Crossing)'
            render(full, '%s/%s_full.png' % (prev, out.name.replace('/', '_')), finish=False,
                   views=('se', 'nw'), plans=[1])


if __name__ == '__main__':
    main(sys.argv[1:])
