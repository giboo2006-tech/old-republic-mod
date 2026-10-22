"""hub/holdfast: Clan Hask's stronghold on Tyrvann (docs/MILESTONE_6.md 5.7).

    python3 tools/assets/structures/h2/holdfast.py [--preview out.png]

A fortress of stormstone and dark timber, 83 x 83, on a stone plinth above the mire. A stair climbs
from the mud to the clan gate in the north gatehouse: nine plates of clan iron (HOLDFAST_GATE) that
open by clan standing. Behind it lies the fire yard with its signal fire, Dorrun's longhouse to the
west and the forge to the east under the storm keep, whose copper spire and rods draw the lightning
away from the hold. The spine street runs from wall to wall; south of it the landing field with its
four pads, the healer's house and the barracks to the west, the spacer's shed and the stores to the
east, the training yard, the kennels and the cairns of the clan's dead along the south wall. Every
roof carries a lightning rod, and the hold is lit throughout: Tyrvann's storm makes its days dark.

Layers 0 and 1 are the plinth (the ground skirt); 2 is the yard and floor layer and 3 the feet
layer. The site meta puts layer 1 at the stand, so the yard stands two above the land.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import common as C  # noqa: E402
from common import S, AIR, Template, mod, meta, shapes, stair, slab, lantern  # noqa: E402

TEMPLATE = 'hub/holdfast'
SEED = 905501
N = 83          # the footprint (x and z)
H = 48          # height
G = 2           # yard and floor layer
F = 3           # feet layer

IN0, IN1 = 5, 77                         # inside the walls
WALK = 10                                # feet layer on the wall walk
HEART = (41, F, 47)
BAYS = [(34, F, 40), (48, F, 40), (34, F, 54), (48, F, 54)]
FIELD = (25, 31, 57, 63)                 # the field's fence (x0, z0, x1, z1)
GATE = [(x, y, 3) for x in (40, 41, 42) for y in (F, F + 1, F + 2)]

# ---------------------------------------------------------------- materials

STONE = [('or:stormstone_bricks', 9), ('or:stormstone', 3), ('deepslate_bricks', 2), ('cracked_deepslate_bricks', 1)]
ROUGH = [('or:stormstone', 5), ('cobbled_deepslate', 3), ('tuff', 2), ('or:stormstone_bricks', 1)]
YARD = [('packed_mud', 4), ('or:stormstone', 4), ('mud_bricks', 2), ('tuff', 1), ('coarse_dirt', 1)]
FLAGS = [('or:stormstone_bricks', 5), ('polished_deepslate', 2), ('or:stormstone', 2), ('deepslate_tiles', 1)]
PLATE = [('or:durasteel_plating', 5), ('or:wreck_plating_scorched', 2), ('or:rusted_plating', 2), ('or:imperial_panel', 1)]
PLANKS = [('dark_oak_planks', 6), ('spruce_planks', 3), ('mangrove_planks', 1)]
CRATES = [('or:cargo_crate', 3), ('barrel[facing=up,open=false]', 3), ('or:cargo_crate_imperial', 1)]
POST = 'dark_oak_log[axis=y]'
BEAM = {'x': 'stripped_dark_oak_log[axis=x]', 'z': 'stripped_dark_oak_log[axis=z]'}
COPPER = ('waxed_oxidized_cut_copper_stairs', 'waxed_oxidized_cut_copper')
GREEN = ('waxed_weathered_cut_copper_stairs', 'waxed_weathered_cut_copper')
SHINGLE = ('spruce_stairs', 'spruce_planks')
TILES = ('deepslate_tile_stairs', 'deepslate_tiles')
ROD = 'waxed_lightning_rod[facing=up,powered=false,waterlogged=false]'
OLD_ROD = 'waxed_weathered_lightning_rod[facing=up,powered=false,waterlogged=false]'
FIRE = 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]'
SIGNAL = 'campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]'
CLAN = ('orange', 'black')                 # Clan Hask's colours, plain cloth


# ---------------------------------------------------------------- building helpers

def stone(seed, scale=2.5):
    return C.mix(STONE, SEED + seed, scale)


def box(t, x0, z0, x1, z1, y0, y1, state):
    """Walls round the rectangle from y0 to y1, nothing inside."""
    for (x, z) in C.ring_cells(x0, z0, x1, z1):
        for y in range(y0, y1 + 1):
            C.put(t, x, y, z, state)


def hall(t, x0, z0, x1, z1, eave, lower, upper=None, floor=None, frame=True, every=4):
    """A building's shell: a stone footing course (floor layer and feet layer) of `lower`, then the
    wall to the eave layer of `upper` (timber framed: dark oak posts at the corners and every `every`
    cells, a stripped beam along the eave), air inside, the floor at G."""
    upper = upper if upper is not None else lower
    for (x, z) in C.ring_cells(x0, z0, x1, z1):
        post = frame and ((x in (x0, x1) and z in (z0, z1)) or (z in (z0, z1) and (x - x0) % every == 0)
                          or (x in (x0, x1) and (z - z0) % every == 0))
        for y in range(G, eave + 1):
            if y <= F:
                C.put(t, x, y, z, lower)
            elif post:
                t.set(x, y, z, POST)
            elif frame and y == eave:
                t.set(x, y, z, BEAM['x'] if z in (z0, z1) else BEAM['z'])
            else:
                C.put(t, x, y, z, upper)
    if floor is not None:
        t.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, floor)
    t.air(x0 + 1, F, z0 + 1, x1 - 1, eave, z1 - 1)


def gable(t, x0, z0, x1, z1, y, roof, axis, end, over=1):
    """A pitched roof over walls x0..x1, z0..z1 whose top layer is y: the eave rows hang one cell
    out at y, each row inward one higher, the ridge along `axis`. The gable ends are closed with
    `end` and the loft under the roof is air. roof is (stair, full block)."""
    st, full = roof
    if axis == 'x':
        a0, a1, b0, b1 = x0 - over, x1 + over, z0 - over, z1 + over
    else:
        a0, a1, b0, b1 = z0 - over, z1 + over, x0 - over, x1 + over
    lo_face, hi_face = ('south', 'north') if axis == 'x' else ('east', 'west')

    def at(a, b, yy, state):
        if axis == 'x':
            C.put(t, a, yy, b, state)
        else:
            C.put(t, b, yy, a, state)
    k = 0
    while b0 + k <= b1 - k:
        yy = y + k
        lo, hi = b0 + k, b1 - k
        for a in range(a0, a1 + 1):
            if lo == hi:
                at(a, lo, yy, slab(full.replace('_planks', '_slab').replace('tiles', 'tile_slab')
                                   .replace('cut_copper', 'cut_copper_slab'), 'bottom'))
            else:
                at(a, lo, yy, stair(st, lo_face))
                at(a, hi, yy, stair(st, hi_face))
        if k >= 1:
            ea0, ea1 = (x0, x1) if axis == 'x' else (z0, z1)
            for b in range(lo + 1, hi):
                at(ea0, b, yy, end)
                at(ea1, b, yy, end)
                for a in range(ea0 + 1, ea1):
                    at(a, b, yy, AIR)
        k += 1
    return y + k - 1                       # the ridge layer


def hip(t, x0, z0, x1, z1, y, roof, over=1, peak=None):
    """A hipped roof over walls x0..x1, z0..z1 whose top layer is y: rings of stairs climbing inward
    (outer corner stairs at the hips) to a ridge or a point; air under it; returns the top layer."""
    st, full = roof
    k = 0
    while True:
        a0, b0, a1, b1 = x0 - over + k, z0 - over + k, x1 + over - k, z1 + over - k
        yy = y + k
        if a0 > a1 or b0 > b1:
            return yy - 1
        if a0 == a1 or b0 == b1:
            t.fill(a0, yy, b0, a1, yy, b1, full)
            if peak is not None:
                t.set((a0 + a1) // 2, yy + 1, (b0 + b1) // 2, peak)
            return yy
        for x in range(a0 + 1, a1):
            t.set(x, yy, b0, stair(st, 'south'))
            t.set(x, yy, b1, stair(st, 'north'))
        for z in range(b0 + 1, b1):
            t.set(a0, yy, z, stair(st, 'east'))
            t.set(a1, yy, z, stair(st, 'west'))
        t.set(a0, yy, b0, stair(st, 'south', shape='outer_left'))
        t.set(a1, yy, b0, stair(st, 'south', shape='outer_right'))
        t.set(a0, yy, b1, stair(st, 'north', shape='outer_right'))
        t.set(a1, yy, b1, stair(st, 'north', shape='outer_left'))
        if k >= 1:
            t.air(a0 + 1, yy, b0 + 1, a1 - 1, yy, b1 - 1)
        k += 1


def door(t, side, x0, z0, x1, z1, at, width=2, height=3, frame=True):
    """A doorway through the `side` wall of the box from `at` along it, a dark oak lintel over it."""
    if side in ('north', 'south'):
        z = z0 if side == 'north' else z1
        t.air(at, F, z, at + width - 1, F + height - 1, z)
        if frame:
            for x in range(at - 1, at + width + 1):
                t.set(x, F + height, z, BEAM['x'])
    else:
        x = x0 if side == 'west' else x1
        t.air(x, F, at, x, F + height - 1, at + width - 1)
        if frame:
            for z in range(at - 1, at + width + 1):
                t.set(x, F + height, z, BEAM['z'])


def slits(t, x0, z0, x1, z1, y, every, state='iron_bars', sides=('north', 'south', 'east', 'west'), start=2,
          height=1):
    for side in sides:
        if side in ('north', 'south'):
            z = z0 if side == 'north' else z1
            for x in range(x0 + start, x1 - 1, every):
                for k in range(height):
                    t.set(x, y + k, z, state)
        else:
            x = x0 if side == 'west' else x1
            for z in range(z0 + start, z1 - 1, every):
                for k in range(height):
                    t.set(x, y + k, z, state)


def brazier(t, x, y, z, tall=1, post='deepslate_brick_wall', fire=FIRE):
    """A fire on a stone post `tall` high standing on the cell below y."""
    for k in range(tall):
        t.set(x, y + k, z, post)
    t.set(x, y + tall, z, fire)


def pole_banner(t, x, y, z, height, colour, face='south'):
    """A dark oak pole `height` tall with a plain banner hanging on its `face` side near the top."""
    for k in range(height):
        t.set(x, y + k, z, POST)
    t.set(x, y + height, z, 'dark_oak_fence')
    t.set(x, y + height + 1, z, lantern('lantern'))
    dx, dz = C.DIRS[face]
    C.banner(t, x + dx, y + height - 2, z + dz, face, colour)


def rod(t, x, y, z, old=False):
    t.set(x, y, z, OLD_ROD if old else ROD)


def bench(t, x, z, length, axis):
    """A log bench lying along `axis` at the feet layer."""
    for i in range(length):
        if axis == 'x':
            t.set(x + i, F, z, 'stripped_spruce_log[axis=x]')
        else:
            t.set(x, F, z + i, 'stripped_spruce_log[axis=z]')


def hides(t, x0, z0, x1, z1, y, posts=True):
    """A shelter of hides on dark oak posts: brown and grey wool, posts at the corners."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, y, z, 'brown_wool' if (x * 3 + z) % 4 else 'light_gray_wool')
    if posts:
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            for yy in range(F, y):
                t.set(x, yy, z, 'dark_oak_fence')


# ---------------------------------------------------------------- the build

def build(preview=None):
    t = Template(N, H, N, seed=SEED, name=TEMPLATE)
    ground(t)
    outer_wall(t)
    gatehouse(t)
    fire_yard(t)
    longhouse(t)
    forge(t)
    keep(t)
    spine(t)
    field(t)
    healers(t)
    barracks(t)
    spacer(t)
    stores(t)
    south_band(t)
    mid_towers(t)
    wall_stairs(t)
    details(t)
    skirt(t)
    people(t)
    lights(t)
    t.finish()
    checks(t)
    t.save(TEMPLATE, preview=preview, finish=False)
    # The hold stands on its two-layer plinth: layer 1 at the stand, the yard two above the land.
    meta.write(TEMPLATE, buried=1, max_slope=6, clearance=8, support_depth=16, ramp_rings=1, clear_above=8,
               terrain='flat', spawn_radius=64)
    C.report(t)
    return t


def ground(t):
    """The yard layer inside the walls and air over it up to the walk."""
    t.fill(IN0, G, IN0, IN1, G, IN1, C.mix(YARD, SEED + 1, 3.0))
    t.air(IN0, F, IN0, IN1, WALK + 2, IN1)


# ---------------------------------------------------------------- the wall and its towers

def outer_wall(t):
    """Two cells thick, its walk behind a crenellated parapet; square towers at three corners."""
    wall = stone(2)
    lo, hi = 3, N - 1 - 3
    for ring in (lo, lo + 1):
        for (x, z) in C.ring_cells(ring, ring, N - 1 - ring, N - 1 - ring):
            for y in range(G, WALK):
                t.set(x, y, z, wall(x, y, z))
    # a footing course of deepslate outside and in, a corbel ledge under the walk inside
    for (x, z) in C.ring_cells(lo, lo, hi, hi):
        t.set(x, F, z, 'polished_deepslate')
    for (x, z) in C.ring_cells(lo + 2, lo + 2, hi - 2, hi - 2):
        t.set(x, WALK - 1, z, slab('or:stormstone_brick_slab', 'top'))
    for i, (x, z) in enumerate(C.ring_cells(lo, lo, hi, hi)):
        t.set(x, WALK, z, wall(x, WALK, z))
        t.set(x, WALK + 1, z, wall(x, WALK + 1, z) if i % 2 == 0 else AIR)
    for ring in (lo + 1, lo + 2):
        for (x, z) in C.ring_cells(ring, ring, N - 1 - ring, N - 1 - ring):
            t.air(x, WALK, z, x, WALK + 2, z)
    for (x0, z0) in ((0, 0), (0, 74), (74, 74)):
        corner_tower(t, x0, z0)
    # buttresses outside every 8 cells, capped with stairs, and the clan's cloth on the inner faces
    for k in range(lo + 10, hi - 9, 8):
        if 31 <= k <= 51:
            continue
        for (x, z, face) in ((k, lo - 1, 'south'), (lo - 1, k, 'east'), (hi + 1, k, 'west'), (k, hi + 1, 'north')):
            t.fill(x, G, z, x, F + 3, z, 'polished_deepslate')
            t.set(x, F + 4, z, stair('or:stormstone_brick_stairs', face))
    for k in range(IN0 + 7, IN1 - 5, 10):
        for (x, z, face) in ((k, IN0, 'south'), (IN0, k, 'east'), (IN1, k, 'west'), (k, IN1, 'north')):
            C.banner(t, x, WALK - 3, z, face, CLAN[(k // 10) % 2])


def tower(t, x0, z0, x1, z1, top, seed, roof=COPPER, windows=(WALK + 2,)):
    """A square stone tower: solid to the walk, a room from the walk floor to `top` (doors where the
    walk meets it are cut by walk_doors), barred slits, a hipped copper roof with a rod on its point;
    returns the roof's top layer."""
    wall = stone(seed)
    box(t, x0, z0, x1, z1, G, top, wall)
    t.fill(x0 + 1, G, z0 + 1, x1 - 1, WALK - 2, z1 - 1, wall)
    t.fill(x0 + 1, WALK - 1, z0 + 1, x1 - 1, WALK - 1, z1 - 1, C.mix(PLANKS, SEED + seed + 1, 3.0))
    t.air(x0 + 1, WALK, z0 + 1, x1 - 1, top, z1 - 1)
    for y in windows:
        slits(t, x0, z0, x1, z1, y, 3, height=2)
    t.fill(x0 + 1, top, z0 + 1, x1 - 1, top, z1 - 1, 'dark_oak_planks')
    peak = hip(t, x0, z0, x1, z1, top + 1, roof, over=0)
    rod(t, (x0 + x1) // 2, peak + 1, (z0 + z1) // 2)
    return peak


def walk_doors(t, x0, z0, x1, z1):
    """Open the tower's walls where the wall walk (rings 3..4 from the edge) runs into it."""
    for (x, z) in C.ring_cells(x0, z0, x1, z1):
        on_walk = (x in (3, 4, N - 5, N - 4) and IN0 - 2 <= z <= IN1 + 2) or \
                  (z in (3, 4, N - 5, N - 4) and IN0 - 2 <= x <= IN1 + 2)
        if on_walk and (x0 < x < x1 or z0 < z < z1):
            t.air(x, WALK, z, x, WALK + 2, z)


def tower_ladder(t, x, z, y0, y1, facing):
    for y in range(y0, y1 + 1):
        t.set(x, y, z, S('ladder', facing=facing, waterlogged=False))


def corner_tower(t, x0, z0):
    x1, z1 = x0 + 8, z0 + 8
    top = WALK + 8
    tower(t, x0, z0, x1, z1, top, 10 + x0 + z0, windows=(WALK + 2, WALK + 5))
    walk_doors(t, x0, z0, x1, z1)
    west = x0 == 0
    lx = x0 + 1 if west else x1 - 1
    lz = z0 + 4
    tower_ladder(t, lx, lz, WALK, top - 1, 'east' if west else 'west')
    for (x, z) in ((x0 + 4, z0 + 2), (x0 + 4, z0 + 6), (x0 + 2, z0 + 4), (x0 + 6, z0 + 4)):
        C.hang(t, x, top - 2, z, drop=2)
    C.crates(t, [(x0 + 6, WALK, z0 + 6 if z0 == 0 else z0 + 2), (x0 + 6, WALK + 1, z0 + 6 if z0 == 0 else z0 + 2)],
             t.rng, CRATES)


MID_TOWERS = ((0, 44, 6, 50), (76, 44, 82, 50), (38, 76, 44, 82))


def mid_towers(t):
    """Towers astride the west, east and south walls, the walk running through them. The west and
    east ones have a guard room at their foot, a door from the lane and a ladder up to the walk."""
    for i, (x0, z0, x1, z1) in enumerate(MID_TOWERS):
        top = WALK + 6
        tower(t, x0, z0, x1, z1, top, 30 + i)
        walk_doors(t, x0, z0, x1, z1)
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        if i < 2:
            west = i == 0
            lx = x0 + 1 if west else x1 - 1
            t.air(x0 + 1, F, z0 + 1, x1 - 1, WALK - 2, z1 - 1)
            t.air(x1 if west else x0, F, cz - 1, x1 if west else x0, F + 2, cz)
            tower_ladder(t, lx, cz, F, top - 1, 'east' if west else 'west')
            C.hang(t, cx, WALK - 3, cz - 1, drop=2)
            C.crates(t, [(cx, F, z0 + 1), (cx + 1, F, z0 + 1), (cx, F + 1, z0 + 1)], t.rng, CRATES)
        else:
            tower_ladder(t, cx, z1 - 1, WALK, top - 1, 'north')
        C.hang(t, cx, top - 2, cz, drop=2)


# ---------------------------------------------------------------- the gatehouse

def gatehouse(t):
    """Two towers flanking the clan gate, the gate room over the passage joining them at the walk,
    the stair up from the mud. Outside the gate the passage is open to the sky; behind it a vault."""
    wall = stone(40)
    top = 18
    for (x0, x1) in ((33, 39), (43, 49)):
        west = x0 == 33
        box(t, x0, 0, x1, 10, G, top, wall)
        t.fill(x0 + 1, G, 1, x1 - 1, G, 9, C.mix(FLAGS, SEED + 41, 3.0))
        t.air(x0 + 1, F, 1, x1 - 1, top - 1, 9)
        for y in (WALK - 1, WALK + 4):
            t.fill(x0 + 1, y, 1, x1 - 1, y, 9, C.mix(PLANKS, SEED + 42 + y, 3.0))
        t.fill(x0 + 1, top, 1, x1 - 1, top, 9, 'dark_oak_planks')
        peak = hip(t, x0, 0, x1, 10, top + 1, COPPER, over=0)
        rod(t, (x0 + x1) // 2, peak + 1, 5)
        slits(t, x0, 0, x1, 10, WALK + 1, 3, height=2, sides=('north', 'south'))
        slits(t, x0, 0, x1, 10, WALK + 6, 3, height=2)
        # the guard room's door from the yard, a ladder on the outer wall up through both floors
        mid = (x0 + x1) // 2
        t.air(mid, F, 10, mid, F + 2, 10)
        t.set(mid, F + 3, 10, BEAM['x'])
        lx = x0 + 1 if west else x1 - 1
        tower_ladder(t, lx, 6, F, top - 1, 'east' if west else 'west')
        for y in (F + 3, WALK + 3, WALK + 7):
            C.hang(t, mid, y, 5, drop=1 if y != F + 3 else 3)
        C.crates(t, [(mid - 1, F, 1), (mid, F, 1), (mid - 1, F + 1, 1)], t.rng, CRATES)
        t.set(mid + 1, F, 1, 'barrel[facing=up,open=false]')
        # the walk comes in through the outer side
        side_x = x0 if west else x1
        t.air(side_x, WALK, 3, side_x, WALK + 2, 4)
    # the gate block over the passage (x 40..42, z 3..10), the gate room through it at the walk
    for x in (40, 41, 42):
        for z in range(3, 11):
            for y in range(F + 4, top + 1):
                t.set(x, y, z, wall(x, y, z))
    t.air(39, WALK, 4, 43, WALK + 3, 9)
    for x in (40, 41, 42):
        t.set(x, WALK + 1, 3, 'iron_bars')
    C.hang(t, 41, WALK + 2, 6, drop=2)
    for x in (40, 41, 42):
        t.set(x, top + 1, 3, wall(x, top + 1, 3) if x != 41 else AIR)
        t.set(x, top + 1, 10, wall(x, top + 1, 10) if x != 41 else AIR)
    # the passage: open to the sky outside the gate (z 0..2), vaulted behind it (z 4..10)
    t.air(40, F, 0, 42, top + 3, 2)
    t.fill(40, G, 3, 42, G, 10, C.mix(FLAGS, SEED + 44, 3.0))
    t.air(40, F, 4, 42, F + 3, 10)
    for z in (5, 8):
        t.set(40, F + 3, z, stair('or:stormstone_brick_stairs', 'east', 'top'))
        t.set(42, F + 3, z, stair('or:stormstone_brick_stairs', 'west', 'top'))
    for z in (6, 9):
        C.hang(t, 41, F + 3, z, drop=1)
    # the gate: nine plates of clan iron facing out of the hold, a beam over them
    for (x, y, z) in GATE:
        t.set(x, y, z, 'or:holdfast_gate[facing=north,open=false]')
    for x in (40, 41, 42):
        t.set(x, F + 3, 3, 'or:durasteel_beam[axis=x]')
    # the stair up from the mud: z 0 at the plinth's foot, z 1 and 2 climbing to the gate
    for x in (40, 41, 42):
        t.set(x, 0, 0, 'or:stormstone_bricks')
        t.set(x, 1, 0, AIR)
        t.set(x, 0, 1, 'or:stormstone_bricks')
        t.set(x, 1, 1, stair('or:stormstone_brick_stairs', 'south'))
        t.fill(x, 0, 2, x, 1, 2, 'or:stormstone_bricks')
        t.set(x, 2, 2, stair('or:stormstone_brick_stairs', 'south'))
    # torches either side of the approach, the clan's colours over it and over the yard
    for (x, face) in ((40, 'east'), (42, 'west')):
        t.set(x, F + 4, 1, S('wall_torch', facing=face))
        C.banner(t, x, top - 2, 1, face, CLAN[0])
    for x in (36, 46):
        C.banner(t, x, top - 4, 11, 'south', CLAN[1])
        C.banner(t, x, WALK - 2, 11, 'south', CLAN[0])
    for x in (38, 44):
        brazier(t, x, F, 12, tall=1)


# ---------------------------------------------------------------- the fire yard

def fire_yard(t):
    """Flagstones between the gate and the spine street, the signal fire in its stone pit, log
    benches round it, the contract terminal under a shelter of hides, the clan's poles."""
    flags = C.mix(FLAGS, SEED + 50, 3.0)
    t.fill(31, G, 11, 51, G, 26, flags)
    cx, cz = 41, 19
    for (x, z) in C.ring_cells(cx - 2, cz - 2, cx + 2, cz + 2):
        t.set(x, F, z, slab('or:stormstone_brick_slab', 'bottom'))
        t.set(x, G, z, 'polished_deepslate')
    t.fill(cx - 1, G, cz - 1, cx + 1, G, cz + 1, 'magma_block')
    t.set(cx, G, cz, 'hay_block[axis=y]')
    for (dx, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set(cx + dx, F, cz + dz, SIGNAL if (dx, dz) == (0, 0) else FIRE)
    for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        t.set(cx + dx, F, cz + dz, 'basalt[axis=y]')
    # benches
    bench(t, cx - 2, cz - 4, 5, 'x')
    bench(t, cx - 2, cz + 4, 5, 'x')
    bench(t, cx - 4, cz - 2, 5, 'z')
    bench(t, cx + 4, cz - 2, 5, 'z')
    # the spit racks and drums
    for (x, z) in ((cx - 4, cz - 4), (cx + 4, cz + 4)):
        t.set(x, F, z, 'barrel[facing=up,open=false]')
    t.set(cx + 4, F, cz - 4, 'note_block[instrument=basedrum,note=0,powered=false]')
    t.set(cx - 4, F, cz + 4, 'note_block[instrument=basedrum,note=0,powered=false]')
    # the shelter of hides over the contract terminal (east side)
    hides(t, 47, 12, 50, 16, F + 3)
    t.set(49, F, 14, 'or:contract_terminal[facing=west]')
    t.set(50, F, 13, 'barrel[facing=up,open=false]')
    t.set(50, F, 15, 'or:cargo_crate')
    C.hang(t, 48, F + 2, 14, drop=1)
    # a shelter of hides on the west side with seats
    hides(t, 31, 21, 34, 25, F + 3)
    for z in (22, 24):
        C.seat(t, 32, F, z, 'east', 'spruce_stairs')
    t.set(33, F, 23, slab('spruce_slab', 'top'))
    t.set(33, F + 1, 23, 'candle[candles=3,lit=true,waterlogged=false]')
    C.hang(t, 32, F + 2, 23, drop=1)
    # the clan's poles round the yard
    for (x, z, colour) in ((31, 12, CLAN[0]), (51, 25, CLAN[0]), (31, 26, CLAN[1]), (51, 18, CLAN[1])):
        pole_banner(t, x, F, z, 5, colour, face='east' if x == 31 else 'west')
    # weapon racks along the gatehouse's foot
    for x in (32, 33, 34, 48, 49, 50):
        t.set(x, F, 11, 'dark_oak_fence')
        t.set(x, F + 1, 11, 'iron_bars')


# ---------------------------------------------------------------- Dorrun's longhouse

def longhouse(t):
    """The clan's hall: a stone footing, timber walls, a steep shingled roof; the long hearth down
    its middle, tables and benches either side, Dorrun's high seat on a dais at the west end."""
    x0, z0, x1, z1 = 7, 10, 29, 23
    eave = F + 5
    lower = stone(60)
    upper = C.mix([('dark_oak_planks', 6), ('spruce_planks', 2), ('or:durasteel_plating', 1)], SEED + 61, 2.0)
    hall(t, x0, z0, x1, z1, eave, lower, upper, floor=C.mix(PLANKS, SEED + 62, 3.0))
    ridge = gable(t, x0, z0, x1, z1, eave, SHINGLE, 'x', upper)
    # crossed ridge beams at both gables and rods along the ridge
    for x in (x0 - 1, x1 + 1):
        t.set(x, ridge + 1, 16, 'dark_oak_fence')
        t.set(x, ridge + 1, 17, 'dark_oak_fence')
        t.set(x, ridge + 2, 16, 'dark_oak_fence')
        t.set(x, ridge + 2, 17, 'dark_oak_fence')
    for x in (12, 18, 24):
        rod(t, x, ridge + 1, 16 if x != 18 else 17)
    # doors: the east gable onto the fire yard, the south side onto the spine street
    door(t, 'east', x0, z0, x1, z1, 15, width=3, height=4)
    door(t, 'south', x0, z0, x1, z1, 18, width=2, height=3)
    # windows high in the long walls
    slits(t, x0, z0, x1, z1, F + 3, 4, 'or:stormstone_brick_wall', sides=('north', 'south'), start=2)
    # posts and cross beams inside
    for x in range(11, x1, 4):
        for z in (12, 21):
            t.fill(x, F, z, x, eave, z, POST)
        for z in range(z0 + 1, z1):
            t.set(x, eave, z, BEAM['z'])
        C.hang(t, x, eave - 2, 16, drop=2)
        C.hang(t, x, eave - 2, 17, drop=2)
    # the long hearth
    for x in range(13, 26):
        t.set(x, G, 16, 'polished_deepslate')
        t.set(x, G, 17, 'polished_deepslate')
        if x % 3 == 1:
            t.set(x, F, 16, FIRE)
            t.set(x, F, 17, FIRE)
        else:
            t.set(x, F, 16, slab('or:stormstone_brick_slab', 'bottom'))
            t.set(x, F, 17, slab('or:stormstone_brick_slab', 'bottom'))
    # tables and benches
    for z, face in ((14, 'south'), (19, 'north')):
        for x in range(13, 26):
            if x in (15, 19, 23):
                continue
            t.set(x, F, z, slab('dark_oak_slab', 'top'))
            C.seat(t, x, F, z - 1 if face == 'south' else z + 1, face, 'spruce_stairs')
        for x in (14, 18, 22, 25):
            t.set(x, F + 1, z, 'candle[candles=2,lit=true,waterlogged=false]')
    # the dais and the high seat
    t.fill(8, F, 13, 10, F, 20, 'or:stormstone_bricks')
    for z in range(13, 21):
        t.set(11, F, z, stair('or:stormstone_brick_stairs', 'west'))
    t.air(8, F + 1, 13, 10, F + 3, 20)
    t.set(8, F + 1, 16, stair('dark_oak_stairs', 'west'))
    t.set(8, F + 1, 17, stair('dark_oak_stairs', 'west'))
    t.set(8, F + 1, 15, 'dark_oak_fence')
    t.set(8, F + 1, 18, 'dark_oak_fence')
    t.fill(9, F + 1, 13, 9, F + 1, 14, 'barrel[facing=up,open=false]')
    t.set(9, F + 1, 20, 'barrel[facing=up,open=false]')
    C.carpet_run(t, 9, 15, 10, 18, F + 1, 'black')
    t.set(10, F + 1, 16, AIR)
    for (z, colour) in ((14, CLAN[0]), (16, CLAN[1]), (17, CLAN[1]), (19, CLAN[0])):
        C.banner(t, 8, F + 5, z, 'east', colour)
    for x in (19, 27):                     # on the wall posts
        C.banner(t, x, F + 3, 11, 'south', CLAN[0])
        C.banner(t, x, F + 3, 22, 'north', CLAN[1])
    # a fur rug by the door and a weapon rack
    C.carpet_run(t, 26, 15, 28, 17, F, 'brown')
    for z in (12, 13, 20, 21):
        t.set(28, F, z, 'dark_oak_fence')
        t.set(28, F + 1, z, 'iron_bars')


# ---------------------------------------------------------------- the forge and the storm keep

def forge(t):
    """The clan's smithy: open to the fire yard on the west under a tiled roof, the hearth of lava
    under a stone chimney on the east, anvils, the quench, the armourer's racks."""
    x0, z0, x1, z1 = 53, 9, 66, 24
    eave = F + 5
    lower = stone(70)
    hall(t, x0, z0, x1, z1, eave, lower, lower, floor=C.mix(FLAGS, SEED + 71, 3.0), every=3)
    t.air(x0, F, z0 + 1, x0, eave - 1, z1 - 1)
    for z in range(z0, z1 + 1, 3):
        t.fill(x0, F, z, x0, eave - 1, z, POST)
    for z in range(z0, z1 + 1):
        t.set(x0, eave, z, BEAM['z'])
    ridge = gable(t, x0, z0, x1, z1, eave, TILES, 'z', C.mix(STONE, SEED + 72, 2.0))
    # the chimney stack over the hearth, smoking from a fire on hay at its top
    cx0, cx1, cz0, cz1 = 62, 65, 15, 18
    for y in range(F, ridge + 5):
        for (x, z) in C.ring_cells(cx0, cz0, cx1, cz1):
            t.set(x, y, z, lower(x, y, z))
    t.air(cx0 + 1, F + 1, cz0 + 1, cx1 - 1, ridge + 3, cz1 - 1)
    t.fill(cx0 + 1, ridge + 3, cz0 + 1, cx1 - 1, ridge + 3, cz1 - 1, 'hay_block[axis=y]')
    t.fill(cx0 + 1, ridge + 4, cz0 + 1, cx1 - 1, ridge + 4, cz1 - 1, SIGNAL)
    t.air(cx0, F, cz0 + 1, cx0, F + 2, cz1 - 1)
    t.fill(cx0 + 1, G, cz0 + 1, cx1 - 1, G, cz1 - 1, 'magma_block')
    t.set(cx0 + 1, F, cz0 + 1, 'lava_cauldron')
    t.set(cx0 + 1, F, cz0 + 2, 'lava_cauldron')
    t.set(cx0 + 2, F, cz0 + 1, FIRE)
    t.set(cx0 + 2, F, cz0 + 2, FIRE)
    rod(t, cx0, ridge + 5, cz0, old=True)
    rod(t, cx1, ridge + 5, cz1, old=True)
    for z in (11, 21):
        rod(t, 59, ridge + 1, z)
    # the smith's floor
    t.set(60, F, 14, S('blast_furnace', facing='south', lit=False))
    t.set(61, F, 14, S('blast_furnace', facing='south', lit=False))
    t.set(58, F, 16, S('anvil', facing='east'))
    t.set(58, F, 19, S('chipped_anvil', facing='east'))
    t.set(61, F, 20, 'smithing_table')
    t.set(62, F, 21, S('grindstone', face='floor', facing='west'))
    t.set(60, F, 21, 'water_cauldron[level=3]')
    t.set(64, F, 12, 'barrel[facing=up,open=false]')
    t.set(65, F, 12, 'barrel[facing=up,open=false]')
    C.crates(t, [(65, F, 21), (65, F, 22), (64, F, 22), (65, F + 1, 22)], t.rng, CRATES)
    for z in (10, 11, 12):
        t.set(54, F, z, 'dark_oak_fence')
        t.set(54, F + 1, z, 'iron_bars')
    for x in (55, 56, 57):
        t.set(x, F, 23, 'dark_oak_fence')
        t.set(x, F + 1, 23, 'iron_bars')
        t.set(x, F + 2, 23, 'iron_bars')
    for z in (12, 17, 21):
        for x in range(x0 + 1, x1):
            if t.is_air(x, eave, z):
                t.set(x, eave, z, BEAM['x'])
    for (x, z) in ((57, 12), (57, 21), (60, 17)):
        C.hang(t, x, eave - 2, z, drop=2)
    C.banner(t, 66 - 1, F + 4, 11, 'west', CLAN[0])


def keep(t):
    """The storm keep in the north-east corner: four floors, a roof terrace behind its parapet with a
    fire at each corner and, in the middle, the copper spire bristling with rods."""
    x0, z0, x1, z1 = 67, 0, 82, 15
    wall = stone(80)
    top = 30
    box(t, x0, z0, x1, z1, G, top, wall)
    t.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, C.mix(FLAGS, SEED + 81, 3.0))
    t.air(x0 + 1, F, z0 + 1, x1 - 1, top, z1 - 1)
    floors = (WALK - 1, 17, 24, top)
    for y in floors:
        t.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, C.mix(PLANKS, SEED + 82 + y, 3.0))
    # buttressed corners
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(G, top + 3):
            t.set(x, y, z, 'polished_deepslate')
    # doors: the yard (south, x 73..75) and the walks
    t.air(73, F, z1, 75, F + 3, z1)
    for x in range(72, 77):
        t.set(x, F + 4, z1, BEAM['x'])
    walk_doors(t, x0, z0, x1, z1)
    # ladders up the inside of the east wall, a hatch through each floor
    for i, (y0, y1) in enumerate(((F, floors[0]), (floors[0] + 1, floors[1]), (floors[1] + 1, floors[2]),
                                  (floors[2] + 1, top))):
        tower_ladder(t, x1 - 1, 12 if i % 2 == 0 else 3, y0, y1, 'west')
    # windows on every floor
    for y in (F + 2, WALK + 2, 19, 26):
        slits(t, x0, z0, x1, z1, y, 3, height=2)
    # the parapet and the corner fires
    for i, (x, z) in enumerate(C.ring_cells(x0, z0, x1, z1)):
        t.set(x, top + 1, z, wall(x, top + 1, z))
        t.set(x, top + 2, z, wall(x, top + 2, z) if i % 2 == 0 else AIR)
    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        brazier(t, x, top + 1, z, tall=1)
    # the spire: a stepped copper drum with rods, a tall rod mast in the middle
    sx, sz = 75, 8
    for k, r in enumerate((3, 3, 2, 2, 1, 1)):
        y = top + 1 + k
        for x in range(sx - r, sx + r + 1):
            for z in range(sz - r, sz + r + 1):
                edge = max(abs(x - sx), abs(z - sz)) == r
                t.set(x, y, z, 'waxed_oxidized_cut_copper' if edge or k in (0, 2, 4) else 'waxed_oxidized_copper_grate')
    for y in range(top + 7, top + 12):
        t.set(sx, y, sz, 'waxed_oxidized_copper_grate' if y < top + 10 else 'waxed_oxidized_cut_copper')
    rod(t, sx, top + 12, sz)
    for (dx, dz) in ((3, 3), (-3, 3), (3, -3), (-3, -3)):
        rod(t, sx + dx, top + 3, sz + dz, old=True)
    for (dx, dz) in ((2, 2), (-2, 2), (2, -2), (-2, -2)):
        rod(t, sx + dx, top + 5, sz + dz)
    # the rooms: the armoury below, the guard room at the walk, the war room, the bunks
    for x in range(70, 80, 2):
        t.set(x, F, 1, 'dark_oak_fence')
        t.set(x, F + 1, 1, 'iron_bars')
    C.crates(t, [(80, F, 2), (79, F, 2), (80, F + 1, 2), (69, F, 13), (70, F, 14)], t.rng, CRATES)
    t.set(74, floors[1] + 1, 8, 'cartography_table')
    t.set(75, floors[1] + 1, 8, S('lectern', facing='south', has_book=False, powered=False))
    for (x, z) in ((72, 7), (72, 9), (77, 7), (77, 9)):
        C.seat(t, x, floors[1] + 1, z, 'east' if x == 72 else 'west', 'dark_oak_stairs')
    for z in (5, 8, 11):
        C.bed(t, 80, floors[2] + 1, z, 'east', 'brown')
    for above in floors:
        for (x, z) in ((71, 4), (77, 11), (73, 8)):
            if t.is_air(x, above - 1, z):
                C.hang(t, x, above - 1, z, drop=1)
    for (x, z, f) in ((x0 + 3, z1 + 1, 'south'), (x1 - 3, z1 + 1, 'south')):
        C.banner(t, x, top - 2, z, f, CLAN[0])


# ---------------------------------------------------------------- the spine street and the field

def spine(t):
    flags = C.mix(FLAGS, SEED + 90, 3.0)
    t.fill(IN0, G, 27, IN1, G, 30, flags)
    for x in (13, 21, 61, 69):
        brazier(t, x, F, 27, tall=1)


def field(t):
    """The landing field: four pads of clan iron on stone, braziers at the fence posts, cargo."""
    x0, z0, x1, z1 = FIELD
    t.fill(x0, G, z0, x1, G, z1, C.mix(FLAGS, SEED + 100, 3.0))
    t.air(x0, F, z0, x1, H - 1, z1)
    # the fence: stone posts and a low wall, openings mid-side
    for (x, z) in C.ring_cells(x0, z0, x1, z1):
        gap = (39 <= x <= 43 and z in (z0, z1)) or (45 <= z <= 49 and x in (x0, x1))
        if gap:
            continue
        corner = x in (x0, x1) and z in (z0, z1)
        post = corner or (x in (x0, x1) and (z - z0) % 8 == 0) or (z in (z0, z1) and (x - x0) % 8 == 0) \
            or (x in (38, 44) and z in (z0, z1)) or (z in (44, 50) and x in (x0, x1))
        if post:
            brazier(t, x, F, z, tall=1, post='or:stormstone_bricks')
        else:
            t.set(x, F, z, 'or:stormstone_brick_wall')
    for (x, y, z) in BAYS:
        pad(t, x, z)
    # the aisles between the pads and the HEART's vent
    for x in range(26, 57):
        for z in (46, 47, 48):
            t.set(x, G, z, 'polished_deepslate' if z != 47 else 'or:stormstone_bricks')
    for z in range(32, 63):
        for x in (40, 41, 42):
            if not 46 <= z <= 48:
                t.set(x, G, z, 'polished_deepslate' if x != 41 else 'or:stormstone_bricks')
    t.set(41, G, 47, 'or:durasteel_vent')
    # cargo outside the landing boxes
    rng = t.rng
    for (x, z) in ((27, 33), (28, 33), (27, 34), (55, 33), (55, 34), (27, 61), (28, 61), (55, 61), (54, 61), (55, 60)):
        C.crates(t, [(x, F, z)], rng, CRATES)
    C.crates(t, [(27, F + 1, 33), (55, F + 1, 61)], rng, CRATES)


def pad(t, cx, cz):
    """An 11 x 11 deck of clan iron, plated rim, amber lamps flush at the corners and mid-edges."""
    for x in range(cx - 5, cx + 6):
        for z in range(cz - 5, cz + 6):
            edge = max(abs(x - cx), abs(z - cz)) == 5
            t.set(x, G, z, 'or:durasteel_plating' if edge else 'or:durasteel_deck')
    for (dx, dz) in ((5, 5), (-5, 5), (5, -5), (-5, -5), (0, 5), (0, -5), (5, 0), (-5, 0)):
        t.set(cx + dx, G, cz + dz, 'or:light_panel_amber')
    for (dx, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set(cx + dx, G, cz + dz, 'or:durasteel_grate')


# ---------------------------------------------------------------- the west side: healers and barracks

def healers(t):
    """The healer's house: stone under a green copper roof, cots, the brewing bench, bacta, herbs."""
    x0, z0, x1, z1 = 7, 33, 21, 44
    eave = F + 4
    lower = stone(110)
    hall(t, x0, z0, x1, z1, eave, lower, C.mix(STONE, SEED + 111, 2.0), floor='or:pale_tiles', frame=False)
    ridge = gable(t, x0, z0, x1, z1, eave, GREEN, 'x', C.mix(STONE, SEED + 112, 2.0))
    t.fill(x0 + 1, eave + 1, z0 + 1, x1 - 1, eave + 1, z1 - 1, 'spruce_planks')
    rod(t, 10, ridge + 1, 38)
    rod(t, 18, ridge + 1, 39)
    door(t, 'east', x0, z0, x1, z1, 38, width=2, height=3)
    slits(t, x0, z0, x1, z1, F + 1, 3, 'light_blue_stained_glass_pane', sides=('north', 'south'))
    for x in (9, 12, 15, 18):
        C.bed(t, x, F, 35, 'north', 'white')
        C.bed(t, x, F, 42, 'south', 'white')
    t.set(8, F, 37, 'brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]')
    t.set(8, F, 38, 'water_cauldron[level=3]')
    t.set(8, F, 39, 'or:console[facing=east]')
    t.set(8, F, 40, 'barrel[facing=up,open=false]')
    for (x, z) in ((10, 36), (13, 41), (16, 36)):
        t.set(x, F, z, 'potted_fern')
    for x in (11, 17):
        t.set(x, eave + 1, 38, 'or:light_panel')
        t.set(x, eave + 1, 39, 'or:light_panel')
    C.hang(t, 14, eave, 38, drop=1)
    C.hang(t, 14, eave, 39, drop=1)
    t.set(20, F, 34, 'or:engineering_bench[facing=west]')
    C.banner(t, 22, F + 3, 36, 'east', 'white')


def barracks(t):
    """The warriors' barracks: a long hall of stone and timber under shingles, bunks along both walls
    with a sleeping loft over them, racks, a table and the stove."""
    x0, z0, x1, z1 = 7, 50, 21, 64
    eave = F + 6
    lower = stone(120)
    upper = C.mix([('dark_oak_planks', 5), ('spruce_planks', 3)], SEED + 121, 2.0)
    hall(t, x0, z0, x1, z1, eave, lower, upper, floor=C.mix(PLANKS, SEED + 122, 3.0))
    ridge = gable(t, x0, z0, x1, z1, eave, SHINGLE, 'z', upper)
    rod(t, 14, ridge + 1, 53)
    rod(t, 14, ridge + 1, 61)
    door(t, 'east', x0, z0, x1, z1, 56, width=2, height=3)
    slits(t, x0, z0, x1, z1, F + 1, 4, 'or:stormstone_brick_wall', sides=('west',))
    # bunks below, a loft over the west half with more
    for z in (52, 55, 58, 61):
        C.bed(t, 9, F, z, 'west', 'brown')
        t.set(10, F, z + 1, 'barrel[facing=up,open=false]')
    t.fill(8, F + 3, 51, 12, F + 3, 63, 'spruce_planks')
    t.air(8, F + 4, 51, 12, eave, 63)
    for z in (52, 56, 60):
        C.bed(t, 9, F + 4, z, 'west', 'black')
    for z in range(51, 64):
        t.set(13, F + 4, z, 'spruce_fence')
    t.set(13, F + 4, 57, AIR)
    for i in range(3):
        t.set(16 - i, F + i, 57, stair('spruce_stairs', 'west'))
    t.air(13, F + 3, 57, 13, F + 3, 57)
    t.set(13, F + 3, 57, 'spruce_planks')
    for z in (51, 52, 61, 62, 63):
        t.set(20, F, z, 'dark_oak_fence')
        t.set(20, F + 1, z, 'iron_bars')
    t.set(17, F, 53, slab('dark_oak_slab', 'top'))
    t.set(18, F, 53, slab('dark_oak_slab', 'top'))
    C.seat(t, 17, F, 52, 'south', 'spruce_stairs')
    C.seat(t, 18, F, 54, 'north', 'spruce_stairs')
    t.set(17, F, 62, S('smoker', facing='north', lit=False))
    t.set(18, F, 62, FIRE)
    for z in (52, 57, 61):
        for x in range(x0 + 1, x1):
            if t.is_air(x, eave, z):
                t.set(x, eave, z, BEAM['x'])
    for (x, z) in ((16, 52), (16, 61), (18, 57)):
        C.hang(t, x, eave - 2, z, drop=2)
    for z in (54, 60):
        C.banner(t, 20, F + 3, z, 'west', CLAN[1])


# ---------------------------------------------------------------- the east side: the spacer and the stores

def spacer(t):
    """The spacer's shed facing the field: a wide mouth, a speeder on blocks, the counter and bench."""
    x0, z0, x1, z1 = 61, 33, 75, 45
    eave = F + 6
    lower = stone(130)
    plate = C.mix(PLATE, SEED + 131, 2.0)
    hall(t, x0, z0, x1, z1, eave, lower, plate, floor=C.mix(FLAGS, SEED + 132, 3.0), every=3)
    t.air(x0, F, 36, x0, F + 3, 42)
    for z in range(35, 44):
        t.set(x0, F + 4, z, BEAM['z'])
    ridge = gable(t, x0, z0, x1, z1, eave, TILES, 'x', plate)
    rod(t, 64, ridge + 1, 39)
    rod(t, 72, ridge + 1, 39)
    # the counter by the mouth, the spacer behind it
    for z in (38, 39, 40):
        t.set(63, F, z, 'or:durasteel_plating' if z != 39 else 'or:console[facing=west]')
    C.crates(t, [(64, F, 36), (65, F, 36), (64, F + 1, 36), (66, F, 44), (67, F, 44), (74, F, 34), (74, F, 35),
                 (74, F + 1, 34), (73, F, 34)], t.rng, CRATES)
    t.set(70, F, 44, 'or:engineering_bench[facing=north]')
    # a speeder on blocks
    for x in range(67, 73):
        t.set(x, F, 39, 'or:wreck_plating' if x not in (67, 72) else 'or:durasteel_plating')
    for x in range(68, 72):
        t.set(x, F + 1, 39, 'or:durasteel_plating_slab[type=bottom,waterlogged=false]')
    t.set(66, F, 39, 'or:durasteel_plating_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
    for x in (68, 71):
        t.set(x, F, 38, 'or:wreck_salvage')
        t.set(x, F, 40, 'or:wreck_salvage')
    for x in (64, 67, 70, 73):
        for z in range(z0 + 1, z1):
            if t.is_air(x, eave, z):
                t.set(x, eave, z, BEAM['z'])
    for (x, z) in ((64, 41), (70, 36), (70, 42)):
        C.hang(t, x, eave - 2, z, drop=2)
    t.set(x1, F + 3, 39, 'or:light_panel_amber')


def stores(t):
    """The stores: grain, peat and hides in a barn under shingles, a loading door to the field."""
    x0, z0, x1, z1 = 61, 50, 75, 64
    eave = F + 6
    lower = stone(140)
    upper = C.mix([('dark_oak_planks', 4), ('spruce_planks', 3), ('or:durasteel_plating', 1)], SEED + 141, 2.0)
    hall(t, x0, z0, x1, z1, eave, lower, upper, floor=C.mix(PLANKS, SEED + 142, 3.0))
    ridge = gable(t, x0, z0, x1, z1, eave, SHINGLE, 'z', upper)
    rod(t, 68, ridge + 1, 53)
    rod(t, 68, ridge + 1, 61)
    door(t, 'west', x0, z0, x1, z1, 55, width=3, height=4)
    rng = t.rng
    for z in range(51, 64):
        if z in (55, 56, 57):
            continue
        for x in (73, 74):
            for y in (F, F + 1):
                if (x + z + y) % 4:
                    t.set(x, y, z, C.pick(rng, [('hay_block[axis=y]', 3), ('or:peat', 2), ('barrel[facing=up,open=false]', 2),
                                                ('or:cargo_crate', 1)]))
    for x in range(63, 72, 2):
        t.set(x, F, 51, 'barrel[facing=up,open=false]')
        t.set(x, F, 63, 'barrel[facing=up,open=false]')
    t.set(66, F, 57, 'composter[level=0]')
    t.set(67, F, 57, S('smoker', facing='west', lit=False))
    for z in (54, 57, 60):
        for x in range(x0 + 1, x1):
            if t.is_air(x, eave, z):
                t.set(x, eave, z, BEAM['x'])
    for (x, z) in ((65, 54), (65, 60), (70, 57)):
        C.hang(t, x, eave - 2, z, drop=2)


# ---------------------------------------------------------------- the south band

def south_band(t):
    """The training yard, the kennels, the cairns of the clan's dead."""
    rng = t.rng
    # the training yard: beaten earth, a fenced square, targets along the wall
    t.fill(24, G, 66, 58, G, 76, C.mix([('coarse_dirt', 3), ('packed_mud', 3), ('gravel', 1), ('mud', 1)], SEED + 150, 2.5))
    sx0, sz0, sx1, sz1 = 31, 67, 51, 74
    for (x, z) in C.ring_cells(sx0, sz0, sx1, sz1):
        if (x in (40, 41, 42) and z == sz0) or (z in (70, 71) and x in (sx0, sx1)):
            continue
        t.set(x, F, z, 'dark_oak_fence')
    for (x, z) in ((sx0, sz0), (sx1, sz0), (sx0, sz1), (sx1, sz1)):
        t.set(x, F, z, POST)
        t.set(x, F + 1, z, POST)
        t.set(x, F + 2, z, FIRE)
    for x in range(26, 58, 4):
        if 37 <= x <= 45:
            continue
        t.set(x, F, 76, 'hay_block[axis=y]')
        t.set(x, F + 1, 76, 'target[power=0]')
    for x in (26, 56):
        for z in (68, 70, 72):
            t.set(x, F, z, 'dark_oak_fence')
            t.set(x, F + 1, z, 'iron_bars')
    # the kennels: pens with straw and troughs under a lean-to
    t.fill(9, G, 67, 22, G, 76, C.mix([('packed_mud', 3), ('mud', 2), ('coarse_dirt', 2)], SEED + 151, 2.5))
    for (x, z) in C.ring_cells(10, 67, 22, 75):
        if z == 67 and x in (15, 16, 17):
            continue
        t.set(x, F, z, 'spruce_fence')
    for z in range(68, 75):
        t.set(16, F, z, 'spruce_fence')
    for x in range(11, 16):
        t.set(x, F + 3, 74, 'spruce_slab[type=bottom,waterlogged=false]')
        t.set(x, F + 3, 75, 'spruce_slab[type=bottom,waterlogged=false]')
    for x in range(17, 22):
        t.set(x, F + 3, 74, 'spruce_slab[type=bottom,waterlogged=false]')
        t.set(x, F + 3, 75, 'spruce_slab[type=bottom,waterlogged=false]')
    for x in (11, 21):
        t.fill(x, F + 1, 75, x, F + 2, 75, 'spruce_fence')
    for (x, z) in ((12, 72), (13, 73), (19, 73), (20, 72), (18, 69)):
        t.set(x, F, z, 'hay_block[axis=y]')
    t.set(14, F, 68, 'water_cauldron[level=3]')
    t.set(20, F, 68, 'water_cauldron[level=3]')
    lamp = 'lantern[hanging=false,waterlogged=false]'
    t.set(16, F + 1, 68, lamp)
    # the cairns of the clan's dead behind a low wall, black cloth on poles, candles
    t.fill(60, G, 67, 73, G, 76, C.mix([('moss_block', 2), ('or:stormstone', 3), ('coarse_dirt', 2), ('mud', 1)], SEED + 152, 2.0))
    for (x, z) in C.ring_cells(60, 67, 73, 76):
        if z == 67 and x in (66, 67):
            continue
        t.set(x, F, z, 'or:stormstone_brick_wall' if x not in (60, 73) or z not in (67, 76) else POST)
    for (x, z, h) in ((63, 70, 3), (66, 72, 2), (69, 70, 3), (71, 74, 2), (62, 74, 2), (67, 75, 1)):
        for k in range(h):
            t.set(x, F + k, z, 'cobbled_deepslate' if k == 0 else 'or:stormstone')
        t.set(x, F + h, z, 'candle[candles=%d,lit=true,waterlogged=false]' % (1 + (x + z) % 4))
    for (x, z) in ((61, 68), (72, 68)):
        pole_banner(t, x, F, z, 4, CLAN[1], face='south')
    t.set(66, F, 69, 'moss_carpet')
    t.set(70, F, 72, 'moss_carpet')


# ---------------------------------------------------------------- stairs to the walk

def wall_stairs(t):
    """Stairs up to the walk at both ends of the spine street and in the training yard."""
    def flight(cells):
        for (x, y, z, d) in cells:
            t.set(x, y, z, stair('or:stormstone_brick_stairs', d))
            t.fill(x, G, z, x, y - 1, z, 'or:stormstone_bricks')
            t.air(x, y + 1, z, x, y + 3, z)
    n = WALK - F
    flight([(IN0 + n - 1 - i, F + i, z, 'west') for i in range(n) for z in (29, 30)])
    flight([(IN1 - n + 1 + i, F + i, z, 'east') for i in range(n) for z in (29, 30)])
    flight([(x, F + i, IN1 - n + 1 + i, 'south') for i in range(n) for x in (28, 29)])
    flight([(x, F + i, IN1 - n + 1 + i, 'south') for i in range(n) for x in (53, 54)])


# ---------------------------------------------------------------- lamps and the plinth

def details(t):
    """Lamps along the lanes, the rain cistern by the keep, peat stacks, mud."""
    for (x, z) in ((6, 9), (6, 25), (30, 9), (52, 9), (52, 26), (76, 26), (9, 46), (74, 46), (23, 33), (23, 64),
                   (59, 33), (59, 64), (6, 66), (76, 66), (24, 76), (58, 76)):
        C.lamp_post(t, x, F, z, 2, 'dark_oak_fence')
    # the rain cistern in the keep's yard: copper gutters off the keep feed a stone basin
    t.fill(68, G, 17, 72, G, 21, 'or:stormstone_bricks')
    for (x, z) in C.ring_cells(68, 17, 72, 21):
        t.set(x, F, z, 'or:stormstone_bricks')
    t.fill(69, F, 18, 71, F, 20, 'water')
    for y in range(F + 1, 28):
        t.set(70, y, 16, 'waxed_oxidized_copper_grate' if y % 3 else 'waxed_oxidized_cut_copper')
    t.set(70, F + 1, 17, 'waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]')
    for (x, z) in ((74, 19), (75, 20), (74, 21), (76, 23), (73, 24)):
        t.set(x, F, z, 'or:peat')
    t.set(74, F + 1, 20, 'or:peat')
    C.crates(t, [(76, F, 18), (76, F, 19), (76, F + 1, 18)], t.rng, CRATES)
    # mud and puddles of peat along the lanes
    mud = C.mix([('mud', 2), ('packed_mud', 1), ('or:peat', 1)], SEED + 160, 2.0)
    for x in range(IN0, IN1 + 1):
        for z in range(IN0, IN1 + 1):
            if t.get(x, F, z) is not None and C.B.name_of(t.get(x, F, z)) == 'minecraft:air' and \
                    C.B.name_of(t.get(x, G, z)) in ('minecraft:packed_mud', 'minecraft:coarse_dirt') and (x * 7 + z * 13) % 11 == 0:
                t.set(x, G, z, mud(x, G, z))


def skirt(t):
    """The plinth: layer 1 under the hold, the walls and towers down to layer 0, a terrace round the
    wall (rings 1 and 2) with its retaining course, the wall's foot battered with stairs."""
    rough = C.mix(ROUGH, SEED + 170, 3.0)
    for x in range(IN0, IN1 + 1):
        for z in range(IN0, IN1 + 1):
            if t.get(x, 1, z) is None:
                t.set(x, 1, z, rough(x, 1, z))
    for (x, y, z), st in list(t.cells.items()):
        if y == G and C.B.name_of(st) != 'minecraft:air' and (x < IN0 or x > IN1 or z < IN0 or z > IN1):
            for yy in (0, 1):
                if t.get(x, yy, z) is None:
                    t.set(x, yy, z, 'polished_deepslate' if yy == 0 else rough(x, yy, z))
    walk = C.mix([('or:stormstone', 4), ('cobbled_deepslate', 2), ('tuff', 1), ('packed_mud', 1)], SEED + 171, 3.0)
    for ring in (1, 2):
        for (x, z) in C.ring_cells(ring, ring, N - 1 - ring, N - 1 - ring):
            if t.get(x, 1, z) is None:
                t.set(x, 0, z, 'cobbled_deepslate')
                t.set(x, 1, z, walk(x, 1, z) if ring == 1 else 'or:stormstone_bricks')
                if ring == 2 and t.get(x, 2, z) is None:
                    face = 'east' if x == 2 else 'west' if x == N - 3 else 'south' if z == 2 else 'north'
                    t.set(x, 2, z, stair('or:stormstone_brick_stairs', face))


# ---------------------------------------------------------------- the people

def people(t):
    mod.spot(t, 'HEART', *HEART)
    for i, (x, y, z) in enumerate(BAYS):
        mod.spot(t, 'PAD_BAY_%d' % (i + 1), x, y, z)
    t.set(38, F, 32, 'or:pad_terminal[facing=north]')
    t.set(38, F + 1, 32, AIR)
    t.anchor('PAD_TERMINAL', 38, F, 32)
    t.anchor('CONTRACT_TERMINAL', 49, F, 14)
    for (x, y, z) in GATE:
        t.anchor('HOLDFAST_GATE', x, y, z)
    mod.hub_spawner(t, 10, F + 1, 16, 'dorrun', anchor='NPC_DORRUN')
    mod.hub_spawner(t, 37, F, 22, 'varra', anchor='NPC_VARRA')
    mod.hub_spawner(t, 59, F, 17, 'vendor:hask_armourer', anchor='VENDOR_ARMOURER')
    mod.hub_spawner(t, 64, F, 39, 'vendor:spacer', anchor='VENDOR_SPACER')
    mod.spot(t, 'MEDBAY', 15, F, 38)
    posts = ((38, F, 11), (44, F, 11), (4, WALK, 36), (78, WALK, 36), (24, F, 43), (58, F, 43))
    for i, (x, y, z) in enumerate(posts):
        mod.hub_spawner(t, x, y, z, 'warrior', anchor='WARRIOR_%d' % (i + 1))
    for (x, y, z) in ((30, F, 13), (41, F, 65), (74, F, 23), (41, WALK, 5), (23, F, 54)):
        mod.hub_spawner(t, x, y, z, 'warrior')


def lights(t):
    rooms = [
        (8, F, 11, 28, 12, 22),          # the longhouse
        (54, F, 10, 65, 12, 23),         # the forge
        (68, F, 1, 81, 29, 14),          # the keep, every floor
        (34, F, 1, 48, 17, 9),           # the gatehouse
        (8, F, 34, 20, 9, 43),           # the healer's house
        (8, F, 51, 20, 12, 63),          # the barracks and its loft
        (62, F, 34, 74, 12, 44),         # the spacer's shed
        (62, F, 51, 74, 12, 63),         # the stores
    ]
    for (x0, z0, x1, z1) in ((0, 0, 8, 8), (0, 74, 8, 82), (74, 74, 82, 82)) + MID_TOWERS:
        rooms.append((x0 + 1, F, z0 + 1, x1 - 1, WALK + 8, z1 - 1))
    C.light_rooms(t, rooms)
    # outdoors: the storm darkens the day, so the yards and lanes get light too (not the pads' boxes)
    x0, z0, x1, z1 = FIELD
    yards = [(IN0, F, IN0, IN1, F, z0 - 1), (IN0, F, z1 + 1, IN1, F, IN1),
             (IN0, F, z0, x0 - 1, F, z1), (x1 + 1, F, z0, IN1, F, z1),
             (x0, F, z0, x1, F, 34), (x0, F, 60, x1, F, z1), (x0, F, 46, x1, F, 48), (40, F, z0, 42, F, z1),
             (x0, F, z0, 28, F, z1), (54, F, z0, x1, F, z1)]
    C.light_rooms(t, yards, level=10)
    C.light_rooms(t, [(IN0 - 2, WALK, IN0 - 2, IN1 + 2, WALK, IN1 + 2)], level=8)


def checks(t):
    for c in BAYS:
        C.check_pad(t, c)
    C.check_ladders(t)
    names = ['NPC_DORRUN', 'NPC_VARRA', 'VENDOR_ARMOURER', 'VENDOR_SPACER', 'MEDBAY', 'HEART'] + \
        ['WARRIOR_%d' % i for i in range(1, 7)] + ['PAD_BAY_%d' % i for i in range(1, 5)]
    C.check_people(t, BAYS[0], names, block_anchors=('PAD_TERMINAL', 'CONTRACT_TERMINAL'))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--preview', help='write a preview sheet PNG here')
    a = ap.parse_args(argv)
    build(preview=a.preview)


if __name__ == '__main__':
    main()
