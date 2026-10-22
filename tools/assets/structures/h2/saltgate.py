"""hub/saltgate: Ghessar's port town under the Vessba kajidic (docs/MILESTONE_6.md 5.7).

    python3 tools/assets/structures/h2/saltgate.py [--preview out.png]

A walled adobe town of 89 x 89 built round its landing field. The caravan gate, an arch of salt brick,
is on the north (local z = 0) side; the main street runs from it past the cantina (west) and the bazaar
(east) to the customs gate of the field, where the harbourmaster takes his fee. Four pad bays fill the
field with the hub's HEART between them, so every ship lands with its ramp toward the town's centre.
The market street rings the field; behind it stand the counting-house (north-west, behind the
cantina), the kajidic's water tower (north-east), the medbay (west), the hangar in an old freighter
hull (east), Vessba's palace with its pool of open water (south), homes and the scrap yard. Every door
has an enforcer or a price.

Layers 0 and 1 are the plinth (the ground skirt: a terrace with its retaining course round the wall,
the stair up to the caravan gate); 2 is the street and floor layer and 3 the feet layer. The site meta
puts layer 1 at the stand, so the streets stand two above the land.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import common as C  # noqa: E402
from common import S, AIR, Template, mod, meta, shapes, stair, slab, lantern  # noqa: E402

TEMPLATE = 'hub/saltgate'
SEED = 905301
N = 89          # the footprint (x and z)
H = 44          # height
G = 2           # street and floor layer
F = 3           # feet layer

IN0, IN1 = 5, 83                         # inside the walls
FIELD0, FIELD1 = 28, 60                  # the landing field's low wall
HEART = (44, F, 44)
BAYS = [(37, F, 37), (51, F, 37), (37, F, 51), (51, F, 51)]
WALK = 9                                 # feet layer on the wall walk

# ---------------------------------------------------------------- materials

ADOBE = [('or:adobe', 14), ('smooth_sandstone', 3), ('sandstone', 1), ('or:adobe_bricks', 1)]
ADOBE_WORN = [('or:adobe', 12), ('smooth_sandstone', 3), ('sandstone', 2), ('or:adobe_bricks', 1)]
MUD = [('packed_mud', 5), ('mud_bricks', 3), ('or:adobe', 3), ('or:adobe_bricks', 1)]
SALT = [('or:salt_bricks', 8), ('smooth_sandstone', 2), ('cut_sandstone', 1)]
WHITE = [('white_terracotta', 5), ('smooth_sandstone', 2), ('or:salt_bricks', 1)]
STREET = [('sandstone', 6), ('smooth_sandstone', 3), ('or:salt_crust', 1), ('packed_mud', 1), ('coarse_dirt', 1)]
PAVING = [('or:adobe_bricks', 4), ('smooth_sandstone', 3), ('sandstone', 2), ('cut_sandstone', 1)]
APRON = [('smooth_sandstone', 5), ('or:salt_crust', 2), ('sandstone', 2), ('cut_sandstone', 1)]
TILE = [('smooth_sandstone', 4), ('cut_sandstone', 2), ('orange_terracotta', 1)]
SCRAP = [('or:rusted_plating', 5), ('or:wreck_plating', 3), ('or:durasteel_plating', 2),
         ('waxed_weathered_cut_copper', 1), ('or:wreck_plating_scorched', 1)]
CRATES = [('or:cargo_crate', 4), ('barrel[facing=up,open=false]', 2), ('or:cargo_crate_republic', 1)]
POLE = 'stripped_spruce_log[axis=y]'
FENCE = 'spruce_fence'
SPRUCE_TOP = slab('spruce_slab', 'top')
ROOFS = []          # (x0, z0, x1, z1, y) of every flat roof terrace, for the roof clutter


def build(preview=None):
    del ROOFS[:]
    t = Template(N, H, N, seed=SEED, name=TEMPLATE)
    ground(t)
    outer_wall(t)
    wall_stairs(t)
    north_gate(t)
    field(t)
    customs(t)
    cantina(t)
    counting_house(t)
    bazaar(t)
    water_tower(t)
    west_quarter(t)
    medbay(t)
    hangar(t)
    palace(t)
    south_west_homes(t)
    scrap_yard(t)
    infill(t)
    mid_towers(t)
    streets(t)
    roofs(t)
    drifts(t)
    skirt(t)
    people(t)
    lights(t)
    t.finish()
    checks(t)
    t.save(TEMPLATE, preview=preview, finish=False)
    # The town stands on its two-layer skirt: layer 1 at the stand, the street two above the land.
    meta.write(TEMPLATE, buried=1, max_slope=6, clearance=8, support_depth=16, ramp_rings=1, clear_above=8,
               terrain='flat', spawn_radius=64)
    C.report(t)
    return t


# ---------------------------------------------------------------- building helpers

def shell(t, x0, z0, x1, z1, top, wall, floor=None, roof=None, parapet=1, y0=G, vigas=True, band=None,
          caps=True, interior=True):
    """A flat-roofed adobe building: walls x0..x1, z0..z1 from y0 (the floor layer) up to the roof layer
    `top`, air inside, the floor at y0, the roof at `top`, a parapet `parapet` high with rounded caps on
    its corners, and the ends of the roof beams (vigas) sticking out under the eaves."""
    for (x, z) in C.ring_cells(x0, z0, x1, z1):
        for y in range(y0, top + 1):
            t.set(x, y, z, C.call(wall, x, y, z))
        if band is not None:
            t.set(x, top, z, C.call(band, x, top, z))
    if floor is not None:
        t.fill(x0 + 1, y0, z0 + 1, x1 - 1, y0, z1 - 1, floor)
    if interior:
        t.air(x0 + 1, y0 + 1, z0 + 1, x1 - 1, top - 1, z1 - 1)
    t.fill(x0 + 1, top, z0 + 1, x1 - 1, top, z1 - 1, roof if roof is not None else wall)
    if parapet:
        for i, (x, z) in enumerate(C.ring_cells(x0, z0, x1, z1)):
            for k in range(1, parapet + 1):
                t.set(x, top + k, z, C.call(wall, x, top + k, z))
        if caps:
            for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
                t.set(x, top + parapet + 1, z, 'or:adobe_wall')
        t.air(x0 + 1, top + 1, z0 + 1, x1 - 1, top + parapet + 1, z1 - 1)
    if parapet:
        ROOFS.append((x0 + 1, z0 + 1, x1 - 1, z1 - 1, top))
    if vigas:
        y = top - 1
        for x in range(x0 + 2, x1 - 1, 4):
            C.put_if_air(t, x, y, z0 - 1, 'stripped_spruce_log[axis=z]')
            C.put_if_air(t, x, y, z1 + 1, 'stripped_spruce_log[axis=z]')
        for z in range(z0 + 2, z1 - 1, 4):
            C.put_if_air(t, x0 - 1, y, z, 'stripped_spruce_log[axis=x]')
            C.put_if_air(t, x1 + 1, y, z, 'stripped_spruce_log[axis=x]')


def door(t, side, x0, z0, x1, z1, at, width=1, height=3, y=F, arch=None):
    """A doorway through the `side` wall ('north', 'south', 'east', 'west') of the box, starting at
    `at` along the wall; arch rounds its top corners with that stair block."""
    if side in ('north', 'south'):
        z = z0 if side == 'north' else z1
        t.air(at, y, z, at + width - 1, y + height - 1, z)
        if arch and width >= 3:
            C.arch_top(t, at, at + width - 1, z, z, y + height - 1, arch)
    else:
        x = x0 if side == 'west' else x1
        t.air(x, y, at, x, y + height - 1, at + width - 1)
        if arch and width >= 3:
            for zz in (at, at + width - 1):
                t.set(x, y + height - 1, zz, stair(arch, 'south' if zz == at else 'north', 'top'))


def windows(t, x0, z0, x1, z1, y, every, state, sides=('north', 'south', 'east', 'west'), skip=(), start=2):
    """Window cells in the walls at height y, every `every` cells along each side (corners kept)."""
    for side in sides:
        if side in ('north', 'south'):
            z = z0 if side == 'north' else z1
            for x in range(x0 + start, x1 - 1, every):
                if (x, z) not in skip and t.get(x, y, z) is not None and not t.is_air(x, y, z):
                    t.set(x, y, z, state)
        else:
            x = x0 if side == 'west' else x1
            for z in range(z0 + start, z1 - 1, every):
                if (x, z) not in skip and t.get(x, y, z) is not None and not t.is_air(x, y, z):
                    t.set(x, y, z, state)


def shade(t, x0, z0, x1, z1, y, cloth='or:canvas', posts=True, post=POLE, street=None):
    """A flat cloth sunshade on four poles over a roof terrace or a yard."""
    t.fill(x0, y, z0, x1, y, z1, cloth)
    if posts:
        base = street if street is not None else F
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            t.fill(x, base, z, x, y - 1, z, post)


def stall(t, x0, z0, x1, z1, front, rng, goods, cloth_a='or:canvas', cloth_b='or:canvas_crimson'):
    """A market stall: four poles, a striped cloth roof at feet + 3, a counter along its `front` side
    with goods on it and crates behind."""
    roof = F + 3
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        t.fill(x, F, z, x, roof - 1, z, FENCE)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k = (x - x0) if front in ('north', 'south') else (z - z0)
            t.set(x, roof, z, cloth_a if k % 2 == 0 else cloth_b)
    if front in ('north', 'south'):
        z = z0 if front == 'north' else z1
        for x in range(x0 + 1, x1):
            t.set(x, F, z, SPRUCE_TOP)
            g = C.pick(rng, goods)
            if g:
                t.set(x, F + 1, z, g)
    else:
        x = x0 if front == 'west' else x1
        for z in range(z0 + 1, z1):
            t.set(x, F, z, SPRUCE_TOP)
            g = C.pick(rng, goods)
            if g:
                t.set(x, F + 1, z, g)


GOODS = [('decorated_pot[cracked=false,facing=north,waterlogged=false]', 3), ('flower_pot', 1), ('potted_cactus', 1),
         ('potted_dead_bush', 1), ('red_carpet', 1), ('brown_carpet', 1), ('white_carpet', 1), ('candle[candles=2,lit=false,waterlogged=false]', 1),
         ('dried_kelp_block', 1), (None, 2)]


def ext_stair(t, x, z, direction, y0, steps, name='or:adobe_stairs', fill='or:adobe', width=1, side='right'):
    """An outside stair against a wall, solid underneath."""
    shapes.stair_run(t, x, y0, z, direction, steps, name, fill=fill, width=width, side=side)


def jar(t, x, y, z, rng):
    t.set(x, y, z, C.pick(rng, [('decorated_pot[cracked=false,facing=north,waterlogged=false]', 3),
                                ('decorated_pot[cracked=true,facing=east,waterlogged=false]', 1)]))


# ---------------------------------------------------------------- ground and walls

def ground(t):
    """The street layer inside the walls and air over the town up to the eaves."""
    street = C.mix(STREET, SEED + 1, 3.0)
    t.fill(IN0, G, IN0, IN1, G, IN1, street)
    t.air(IN0, F, IN0, IN1, 8, IN1)


def outer_wall(t):
    """Two cells thick, its walk on top behind a crenellated parapet, round towers at the corners."""
    wall = C.mix(ADOBE_WORN, SEED + 2, 2.5)
    lo, hi = 3, N - 1 - 3
    for ring in ((lo, hi), (lo + 1, hi - 1)):
        for (x, z) in C.ring_cells(ring[0], ring[0], ring[1], ring[1]):
            for y in range(G, WALK):
                t.set(x, y, z, wall(x, y, z))
    # plinth course of brick at the foot of the wall, inside and out
    for (x, z) in C.ring_cells(lo, lo, hi, hi):
        t.set(x, F, z, 'or:adobe_bricks')
    for (x, z) in C.ring_cells(lo + 2, lo + 2, hi - 2, hi - 2):
        t.set(x, WALK - 1, z, slab('or:adobe_slab', 'top'))
    for i, (x, z) in enumerate(C.ring_cells(lo, lo, hi, hi)):
        t.set(x, WALK, z, wall(x, WALK, z))
        if i % 3 == 0:
            t.set(x, WALK + 1, z, 'or:adobe_bricks')
    for (x, z) in C.ring_cells(lo + 1, lo + 1, hi - 1, hi - 1):
        t.air(x, WALK, z, x, WALK + 2, z)
    for (x, z) in C.ring_cells(lo + 2, lo + 2, hi - 2, hi - 2):
        t.air(x, WALK, z, x, WALK + 2, z)
    # buttresses outside, every 9 cells: the wall leans on them against the dunes
    for k in range(lo + 7, hi - 6, 9):
        if 34 <= k <= 54:
            continue
        for (x, z, d) in ((k, lo - 1, 'south'), (k, hi + 1, 'north'), (lo - 1, k, 'east'), (hi + 1, k, 'west')):
            t.fill(x, G, z, x, F + 1, z, wall)
            t.set(x, F + 2, z, stair('or:adobe_stairs', d))
    for (cx, cz) in ((lo + 1, lo + 1), (hi - 1, lo + 1), (lo + 1, hi - 1), (hi - 1, hi - 1)):
        corner_tower(t, cx, cz)


def corner_tower(t, cx, cz):
    """A round tower at a corner of the wall: its room open onto the walk, a crenellated top."""
    wall = C.mix(ADOBE, SEED + 3 + cx + cz, 2.5)
    top = WALK + 6
    shapes.cylinder(t, cx, G, top - 1, cz, 3.5, wall)
    shapes.disk(t, cx, F, cz, 3.5, 'or:adobe_bricks')
    shapes.cylinder(t, cx, WALK, top - 1, cz, 2.5, AIR)
    shapes.disk(t, cx, WALK - 1, cz, 2.5, 'or:adobe_bricks')
    shapes.disk(t, cx, top, cz, 3.5, 'or:adobe_bricks')
    shapes.disk(t, cx, top + 1, cz, 3.5, wall, hollow=True)
    for (dx, dz) in ((3, 0), (-3, 0), (0, 3), (0, -3)):
        t.set(cx + dx, top + 2, cz + dz, 'or:adobe_wall')
    # doorways onto the walk on the two wall sides, slit windows outward
    inward_x = 1 if cx < N // 2 else -1
    inward_z = 1 if cz < N // 2 else -1
    t.air(cx + 3 * inward_x, WALK, cz, cx + 2 * inward_x, WALK + 2, cz)
    t.air(cx, WALK, cz + 3 * inward_z, cx, WALK + 2, cz + 2 * inward_z)
    t.air(cx - 3 * inward_x, WALK + 2, cz, cx - 3 * inward_x, WALK + 3, cz)
    t.air(cx, WALK + 2, cz - 3 * inward_z, cx, WALK + 3, cz - 3 * inward_z)
    # a ladder on the outer wall, up through the roof
    lx, lz = cx - 2 * inward_x, cz - inward_z
    for y in range(WALK, top + 1):
        t.set(lx, y, lz, S('ladder', facing='east' if inward_x > 0 else 'west', waterlogged=False))
    C.hang(t, cx, top - 2, cz, drop=2)
    t.set(cx, top + 1, cz, AIR)


def wall_stairs(t):
    """Stairs up to the walk at the ends of the lanes: north, south, west and east."""
    def flight(cells):
        for (x, y, z, d) in cells:
            t.set(x, y, z, stair('or:adobe_stairs', d))
            t.fill(x, G, z, x, y - 1, z, 'or:adobe')
            t.air(x, y + 1, z, x, y + 3, z)
    n = WALK - F
    flight([(x, F + i, IN0 + n - 1 - i, 'north') for i in range(n) for x in (26, 27)])
    flight([(x, F + i, IN1 - n + 1 + i, 'south') for i in range(n) for x in (61, 62)])
    flight([(IN0 + n - 1 - i, F + i, z, 'west') for i in range(n) for z in (64, 65)])
    flight([(IN1 - n + 1 + i, F + i, z, 'east') for i in range(n) for z in (23, 24)])


def north_gate(t):
    """The caravan gate: a salt-brick arch between two square towers, the walk bridged over it."""
    salt = C.mix(SALT, SEED + 4, 3.0)
    wall = C.mix(ADOBE, SEED + 5, 2.5)
    top = 17
    for (x0, x1) in ((35, 41), (47, 53)):
        shell(t, x0, 0, x1, 8, top, wall, floor=C.mix(PAVING, SEED + 6), roof='or:adobe_bricks', parapet=1,
              vigas=False, caps=False)
        for (x, z) in C.ring_cells(x0, 0, x1, 8):
            t.set(x, F, z, 'or:salt_bricks')
            t.set(x, top, z, 'or:salt_bricks')
            if (x + z) % 2 == 0:
                t.set(x, top + 1, z, AIR)
        t.fill(x0 + 1, WALK - 1, 1, x1 - 1, WALK - 1, 7, 'or:adobe_bricks')     # the upper floor
        inner = x1 if x0 == 35 else x0
        outer = x0 if x0 == 35 else x1
        # doors: into the town on the inner side, onto the walk on the outer side, onto the bridge
        t.air(x0 + 2, F, 8, x0 + 4, F + 3, 8)
        C.arch_top(t, x0 + 2, x0 + 4, 8, 8, F + 3, 'or:adobe_stairs')
        t.air(outer, WALK, 4, outer, WALK + 2, 4)
        t.air(inner, WALK, 4, inner, WALK + 2, 4)
        # arrow slits out over the approach, windows over the street
        for x in range(x0 + 2, x1 - 1, 2):
            t.set(x, WALK + 2, 0, 'iron_bars')
            t.set(x, F + 2, 0, 'iron_bars')
            t.set(x, WALK + 2, 8, 'iron_bars')
        # ladder up through both floors to the roof
        lx = x0 + 1 if x0 == 35 else x1 - 1
        for y in range(F, top + 1):
            t.set(lx, y, 7, S('ladder', facing='north', waterlogged=False))
        t.set(lx, WALK - 1, 7, S('ladder', facing='north', waterlogged=False))
        t.set(lx, top, 7, S('ladder', facing='north', waterlogged=False))
        C.hang(t, x0 + 3, F + 4, 4, drop=2)
        C.hang(t, x0 + 3, WALK + 4, 4, drop=2)
        # guard room things
        t.set(x0 + 4 if x0 == 35 else x0 + 2, F, 1, 'or:cargo_crate')
        C.barrel(t, x0 + 5 if x0 == 35 else x0 + 1, F, 1)
        t.set(x0 + 2 if x0 == 35 else x0 + 4, F, 2, SPRUCE_TOP)
        # crimson banners on the outer faces
        for bx in (x0 + 2, x1 - 2):
            t.set(bx, top - 2, 0, 'or:salt_bricks')
    # the arch: the opening x 42..46, y 3..8, through the wall and under the bridge
    arch = WALK - 1
    t.air(42, F, 2, 46, arch - 1, 8)
    t.fill(42, G, 3, 46, G, 8, C.mix(PAVING, SEED + 7))
    for z in range(2, 6):
        t.fill(41, G, z, 41, arch, z, salt)
        t.fill(47, G, z, 47, arch, z, salt)
        t.fill(42, arch, z, 46, arch, z, salt)
        t.set(42, arch - 1, z, stair('sandstone_stairs', 'east', 'top'))
        t.set(46, arch - 1, z, stair('sandstone_stairs', 'west', 'top'))
    t.set(44, arch, 2, 'chiseled_sandstone')
    t.set(44, arch, 5, 'chiseled_sandstone')
    # the bridge: floor on top of the arch, parapet outside
    t.fill(42, WALK - 1, 2, 46, WALK - 1, 5, 'or:adobe_bricks')
    t.air(42, WALK, 3, 46, WALK + 3, 5)
    for x in range(41, 48):
        t.set(x, WALK, 2, 'or:salt_bricks')
        if x % 2 == 0:
            t.set(x, WALK + 1, 2, 'or:salt_bricks')
    C.hang(t, 44, arch - 2, 3, drop=2)
    C.hang(t, 44, arch - 2, 5, drop=2)
    # a canvas awning over the inner mouth of the gate
    C.awning(t, 42, 46, 6, arch, 'south', C.stripes(width=1), depth=1)
    # the approach between the towers' feet: the land, a step to the terrace, a step to the gate
    for x in range(42, 47):
        t.set(x, 0, 0, 'smooth_sandstone')
        t.air(x, 1, 0, x, 8, 0)
        t.set(x, 0, 1, 'or:adobe_bricks')
        t.set(x, 1, 1, stair('sandstone_stairs', 'south'))
        t.air(x, 2, 1, x, 8, 1)
        t.fill(x, 0, 2, x, 1, 2, 'or:adobe_bricks')
        t.set(x, 2, 2, stair('sandstone_stairs', 'south'))


# ---------------------------------------------------------------- the landing field

def field(t):
    a, b = FIELD0, FIELD1
    apron = C.mix(APRON, SEED + 10, 3.0)
    t.fill(a, G, a, b, G, b, apron)
    t.air(a, F, a, b, H - 1, b)
    # the low blast wall with a gate in the middle of every side
    cap = slab('smooth_sandstone_slab', 'bottom')
    for (x, z) in C.ring_cells(a, a, b, b):
        t.set(x, F, z, 'or:salt_bricks')
        t.set(x, F + 1, z, cap)
    for g0 in (42,):
        t.air(g0, F, a, g0 + 4, F + 1, a)
        t.air(g0, F, b, g0 + 4, F + 1, b)
        t.air(a, F, g0, a, F + 1, g0 + 4)
        t.air(b, F, g0, b, F + 1, g0 + 4)
        for (x, z) in ((g0 - 1, a), (g0 + 5, a), (g0 - 1, b), (g0 + 5, b), (a, g0 - 1), (a, g0 + 5), (b, g0 - 1), (b, g0 + 5)):
            t.fill(x, F, z, x, F + 2, z, 'or:salt_bricks')
            t.set(x, F + 3, z, lantern('lantern'))
    for (bx, _, bz) in BAYS:
        pad(t, bx, bz)
    # the cross aisle: a fuel line in a grate channel, drains
    for k in range(a + 2, b - 1):
        if 32 <= k <= 56 and not (42 <= k <= 46):
            t.set(44, G, k, 'or:durasteel_grate')
            t.set(k, G, 44, 'or:durasteel_grate')
    t.set(44, G, 44, 'or:durasteel_vent')
    # landing light masts at the field's corners, cargo stacks in the corners
    for (x, z) in ((a + 1, a + 1), (b - 1, a + 1), (a + 1, b - 1), (b - 1, b - 1)):
        t.fill(x, F, z, x, F + 6, z, 'or:durasteel_beam[axis=y]')
        t.set(x, F + 7, z, 'or:light_panel_amber')
        t.set(x, F + 8, z, 'waxed_lightning_rod[facing=up,powered=false,waterlogged=false]')
    rng = t.rng
    for (x0, z0, sx, sz) in ((a + 2, a + 1, 1, 1), (b - 2, a + 1, -1, 1), (a + 2, b - 1, 1, -1), (b - 2, b - 1, -1, -1)):
        for i in range(3):
            for j in range(2):
                x, z = x0 + sx * i, z0 + sz * j
                if abs(x - 44) < 3 or abs(z - 44) < 3:
                    continue
                h = 1 + (rng.random() < 0.5) + (i == 0 and j == 0)
                for y in range(F, F + h):
                    t.set(x, y, z, C.pick(rng, CRATES))


def pad(t, cx, cz):
    """An 11 x 11 bay of durasteel deck framed in salt brick, amber lights flush at its corners and
    scorch marks round its middle."""
    deck = C.mix([('or:durasteel_deck', 9), ('or:durasteel_plating', 1), ('or:rusted_plating', 1)], cx * 7 + cz, 3.0)
    t.fill(cx - 5, G, cz - 5, cx + 5, G, cz + 5, deck)
    for (x, z) in C.ring_cells(cx - 5, cz - 5, cx + 5, cz + 5):
        t.set(x, G, z, 'or:salt_bricks')
    for (x, z) in ((cx - 5, cz - 5), (cx + 5, cz - 5), (cx - 5, cz + 5), (cx + 5, cz + 5), (cx, cz - 5),
                   (cx, cz + 5), (cx - 5, cz), (cx + 5, cz)):
        t.set(x, G, z, 'or:light_panel_amber')
    t.set(cx, G, cz, 'or:durasteel_grate')


def customs(t):
    """The harbourmaster's booth at the field's north gate: a counter window onto the gate, the pad
    terminal beside it, the tariff board (a holo screen) over the counter."""
    wall = C.mix(SALT, SEED + 11, 3.0)
    shell(t, 36, 29, 40, 31, 7, wall, floor='smooth_sandstone', roof='or:salt_bricks', parapet=1, vigas=False)
    t.air(40, F, 30, 40, F, 30)
    t.set(40, F, 30, SPRUCE_TOP)
    t.air(40, F + 1, 30, 40, F + 2, 30)
    t.air(38, F, 31, 38, F + 2, 31)
    t.set(37, F, 30, 'or:console[facing=east]')
    C.awning(t, 29, 31, 41, F + 3, 'east', C.stripes(width=1), depth=1)
    # the gate posts carry the kajidic's red cloth
    for x in (41, 47):
        t.set(x, F + 3, FIELD0, AIR)


# ---------------------------------------------------------------- the cantina (north-west of the main street)

def cantina(t):
    """The cantina: a long low hall with the bar, booths, the fence's curtained corner, Varra's table and
    the contract terminal; a terrace bar on the roof under canvas."""
    rng = t.rng
    x0, z0, x1, z1 = 28, 9, 40, 22
    top = 9
    wall = C.mix(ADOBE, SEED + 20, 2.5)
    shell(t, x0, z0, x1, z1, top, wall, floor=C.mix(TILE, SEED + 21, 2.0), roof='or:adobe', parapet=1)
    t.fill(x0, F, z0, x1, F, z1, 'or:adobe_bricks')
    t.air(x0 + 1, F, z0 + 1, x1 - 1, F, z1 - 1)
    # doors: the main street (east), the market street (south), the lane behind (west)
    door(t, 'east', x0, z0, x1, z1, 14, width=3, height=4, arch='or:adobe_stairs')
    door(t, 'south', x0, z0, x1, z1, 30, width=3, height=4, arch='or:adobe_stairs')
    door(t, 'west', x0, z0, x1, z1, 18, width=2, height=3)
    windows(t, x0, z0, x1, z1, F + 2, 3, 'iron_bars', sides=('north', 'east'))
    # the bar: an L of counter in the north-west, the bartender behind it, kegs and shelves
    for x in range(29, 35):
        t.set(x, F, 14, 'cut_sandstone')
        t.set(x, F + 1, 14, AIR)
    for z in range(10, 14):
        t.set(34, F, z, 'cut_sandstone')
    t.set(34, F, 12, AIR)
    t.set(34, F, 12, slab('smooth_sandstone_slab', 'bottom'))
    for x in range(29, 34):
        C.barrel(t, x, F, 10, 'south')
        t.set(x, F + 1, 10, C.pick(rng, [('decorated_pot[cracked=false,facing=south,waterlogged=false]', 2),
                                         ('flower_pot', 1), ('brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]', 2)]))
    for x in range(30, 34, 3):
        C.hang(t, x, F + 3, 12, drop=3)
    for x in range(29, 34):
        C.seat(t, x, F, 15, 'north', 'spruce_stairs') if x % 2 == 1 else None
    # tables in the hall
    for (tx, tz) in ((31, 18), (36, 12), (36, 17)):
        t.set(tx, F, tz, 'barrel[facing=up,open=false]')
        t.set(tx, F + 1, tz, 'candle[candles=3,lit=true,waterlogged=false]')
        for (dx, dz, d) in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
            if t.is_air(tx + dx, F, tz + dz) and (tx + dx, tz + dz) not in ((36, 15), (35, 15)):
                C.seat(t, tx + dx, F, tz + dz, d, 'spruce_stairs')
    # Varra's booth by the east window: a table, a bench, the clan's grey cloth on the wall
    t.set(38, F, 19, SPRUCE_TOP)
    t.set(38, F, 20, SPRUCE_TOP)
    C.seat(t, 39, F, 19, 'west', 'spruce_stairs')
    C.seat(t, 39, F, 20, 'west', 'spruce_stairs')
    mod.banner(t, 39, F + 3, 21, 'north', base='gray')
    # the fence's corner: crimson curtains, crates of goods that came off other people's ships
    for z in (19, 20):
        t.set(31, F, z, 'or:canvas_crimson')
        t.set(31, F + 2, z, 'or:canvas_crimson')
    t.set(31, F + 3, 19, 'or:canvas_crimson')
    t.set(31, F + 3, 20, 'or:canvas_crimson')
    t.set(31, F + 3, 21, 'or:canvas_crimson')
    C.crates(t, [(29, F, 21), (29, F + 1, 21), (30, F, 21), (29, F, 20)], rng, CRATES)
    t.set(30, F + 1, 21, 'or:cargo_crate_imperial')
    # the contract terminal by the south door
    t.set(35, F, 21, 'or:contract_terminal[facing=north]')
    t.set(35, F + 1, 21, AIR)
    # rugs
    for (x, z) in ((32, 16), (33, 16), (32, 17), (33, 17)):
        if t.is_air(x, F, z):
            t.set(x, F, z, 'brown_carpet')
    # the roof terrace: canvas over benches, a stair up from the market street, a still and jars
    shade(t, 30, 11, 36, 16, top + 4, cloth='or:canvas_crimson', street=top + 1)
    for (x, z) in ((32, 13), (34, 14)):
        t.set(x, top + 1, z, 'barrel[facing=up,open=false]')
        C.seat(t, x - 1, top + 1, z, 'east', 'spruce_stairs')
        C.seat(t, x + 1, top + 1, z, 'west', 'spruce_stairs')
    for i in range(top - G):
        x = 33 + i
        t.set(x, F + i, 23, stair('or:adobe_stairs', 'east'))
        t.fill(x, G, 23, x, F + i - 1, 23, 'or:adobe')
    t.air(39, top + 1, z1, 39, top + 2, z1)
    t.set(39, top, z1, 'or:adobe')
    jar(t, 38, top + 1, 20, rng)
    jar(t, 29, top + 1, 21, rng)


# ---------------------------------------------------------------- the counting-house (north-west corner)

def counting_house(t):
    """The kajidic's counting-house: salt-brick walls, eight guard posts on two floors, Dask's room
    upstairs over the lane, a roof terrace where the clan can come down on it, a tower over the vault."""
    rng = t.rng
    x0, z0, x1, z1 = 5, 5, 22, 22
    salt = C.mix(SALT, SEED + 30, 2.5)
    adobe = C.mix(ADOBE, SEED + 31, 2.5)

    def wall(x, y, z):
        return salt(x, y, z) if y <= 8 else adobe(x, y, z)
    top = 13
    shell(t, x0, z0, x1, z1, top, wall, floor=C.mix(TILE, SEED + 32, 2.0), roof='or:adobe', parapet=2, vigas=True,
          band=lambda x, y, z: 'cut_sandstone')
    # a string course between the floors
    for (x, z) in C.ring_cells(x0, z0, x1, z1):
        t.set(x, 8, z, 'cut_sandstone')
    t.fill(x0 + 1, 8, z0 + 1, x1 - 1, 8, z1 - 1, 'spruce_planks')
    # the door on the lane, an iron grille folded back inside it
    door(t, 'east', x0, z0, x1, z1, 13, width=3, height=4, arch='sandstone_stairs')
    for z in (12, 16):
        t.fill(x1, F, z, x1, F + 4, z, 'chiseled_sandstone')
    t.set(21, F, 12, 'iron_bars')
    t.set(21, F + 1, 12, 'iron_bars')
    t.set(21, F, 16, 'iron_bars')
    t.set(21, F + 1, 16, 'iron_bars')
    windows(t, x0, z0, x1, z1, F + 2, 4, 'iron_bars', sides=('south', 'east'))
    windows(t, x0, z0, x1, z1, 10, 3, 'iron_bars', sides=('south', 'east'))
    # the vault in the tower's foot (x 6..10, z 6..10): water casks, gold, a barred door
    t.fill(11, F, 6, 11, 7, 11, 'or:salt_bricks')
    t.fill(6, F, 11, 11, 7, 11, 'or:salt_bricks')
    t.air(11, F, 8, 11, F + 1, 9)
    t.set(11, F, 8, 'iron_bars')
    t.set(11, F + 1, 8, 'iron_bars')
    t.set(11, F, 9, 'iron_bars')
    t.set(11, F + 1, 9, 'iron_bars')
    for (x, z) in ((6, 6), (7, 6), (6, 7)):
        t.set(x, F, z, 'water_cauldron[level=3]')
    t.set(8, F, 6, 'raw_gold_block')
    t.set(9, F, 6, 'or:cargo_crate')
    t.set(9, F + 1, 6, 'raw_gold_block')
    t.set(6, F, 9, 'barrel[facing=up,open=false]')
    t.set(6, F, 10, 'barrel[facing=up,open=false]')
    t.set(10, F, 6, 'gold_block')
    # the counting hall: ledger desks, the cashier's grille, crates of pledges
    for (dx, dz) in ((14, 9), (18, 9), (14, 17)):
        t.fill(dx, F, dz, dx + 1, F, dz, SPRUCE_TOP)
        C.lectern(t, dx + 2, F, dz, facing='west') if hasattr(C, 'lectern') else mod.lectern(t, dx + 2, F, dz, facing='west')
        C.seat(t, dx, F, dz + 1, 'north', 'spruce_stairs')
    for z in range(13, 17):
        t.set(16, F, z, 'cut_sandstone')
        t.set(16, F + 1, z, 'iron_bars')
    t.air(16, F, 15, 16, F + 1, 15)
    C.crates(t, [(6, F, 13), (6, F, 14), (6, F + 1, 13), (7, F, 13), (6, F, 18), (6, F, 19), (6, F + 1, 19), (7, F, 19)],
             rng, CRATES)
    t.set(9, F, 14, 'cartography_table')
    C.seat(t, 9, F, 15, 'north', 'spruce_stairs')
    # the stair up along the south wall, climbing west
    for i in range(6):
        x = 20 - i
        t.set(x, F + i, 21, stair('spruce_stairs', 'west'))
        if i > 0:
            t.fill(x, F, 21, x, F + i - 1, 21, 'spruce_planks')
    t.air(16, 8, 21, 20, 8, 21)
    t.set(15, 8, 21, 'spruce_planks')
    for x in range(16, 21):
        t.set(x, 9, 20, FENCE)
    # upstairs: Dask's room over the lane, records along the walls, a screen for messages
    t.fill(18, 9, 12, 18, 9, 16, SPRUCE_TOP)
    t.set(18, 10, 13, 'candle[candles=2,lit=true,waterlogged=false]')
    t.set(21, 10, 14, 'or:holo_screen[facing=west]')
    for z in (8, 9, 10, 18, 19):
        t.set(21, 9, z, 'chiseled_bookshelf[facing=west,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]')
    for x in range(12, 16):
        t.set(x, 9, 6, 'bookshelf')
        t.set(x, 10, 6, 'bookshelf')
    C.crates(t, [(6, 9, 20), (7, 9, 21 - 1)], rng, CRATES)
    t.set(19, 9, 17, 'red_carpet')
    for (x, z) in ((19, 12), (19, 13), (19, 15), (19, 16), (20, 12), (20, 16)):
        t.set(x, 9, z, 'red_carpet')
    # the stair to the roof, climbing east along the north wall
    for i in range(5):
        x = 12 + i
        t.set(x, 9 + i, 6 + 1, stair('spruce_stairs', 'east'))
    t.air(12, top, 7, 16, top, 7)
    t.set(17, top, 7, 'or:adobe')
    # the tower over the vault: up through the roof to a lookout with the kajidic's lamp
    tw = 31
    for (x, z) in C.ring_cells(5, 5, 11, 11):
        for y in range(top, tw + 1):
            t.set(x, y, z, wall(x, y, z))
    t.air(6, 9, 6, 10, tw - 1, 10)
    t.fill(6, 8, 6, 10, 8, 10, 'spruce_planks')
    t.air(11, 9, 8, 11, 11, 9)
    for y in range(9, tw + 1):
        t.set(8, y, 6, S('ladder', facing='south', waterlogged=False))
    t.set(8, 8, 6, 'spruce_planks')
    t.fill(6, tw, 6, 10, tw, 10, 'or:salt_bricks')
    t.set(8, tw, 6, S('ladder', facing='south', waterlogged=False))
    for (x, z) in C.ring_cells(5, 5, 11, 11):
        t.set(x, tw + 1, z, 'or:salt_bricks' if (x + z) % 2 == 0 else AIR)
    for y in range(16, tw - 1, 4):
        for (x, z) in ((10, 5), (11, 8), (8, 11), (5, 8)):          # not behind the ladder at x 8
            t.set(x, y, z, 'iron_bars')
    t.set(8, tw + 2, 8, 'or:salt_bricks')
    t.set(8, tw + 3, 8, lantern('lantern'))
    for (x, z) in ((5, 5), (11, 5), (5, 11), (11, 11)):
        t.set(x, tw + 2, z, 'or:adobe_wall')
    for (bx, bz, f) in ((8, 4, 'north'), (12, 8, 'east')):
        mod.banner(t, bx, tw - 3, bz, f, base='red')
    # the roof terrace: a canvas shade on the west half, clear sky over Dask's room
    shade(t, 12, 15, 16, 21, top + 4, cloth='or:canvas', street=top + 1)
    jar(t, 21, top + 1, 21, rng)
    jar(t, 13, top + 1, 21, rng)


# ---------------------------------------------------------------- the bazaar (north-east of the main street)

def bazaar(t):
    """Shops along the east side under striped awnings, stalls in the square, the water seller."""
    rng = t.rng
    t.fill(48, G, 9, 54, G, 22, C.mix(PAVING, SEED + 40, 2.5))
    shops = ((55, 9, 60, 13), (55, 14, 60, 18), (55, 19, 60, 22))
    for i, (x0, z0, x1, z1) in enumerate(shops):
        wall = C.mix(ADOBE if i != 1 else MUD, SEED + 41 + i, 2.5)
        top = (12, 8, 11)[i]
        shell(t, x0, z0, x1, z1, top, wall, floor=C.mix(TILE, SEED + 44 + i), roof='or:adobe', parapet=1)
        t.air(x0, F, z0 + 1, x0, F + 2, z1 - 1)
        for z in range(z0 + 1, z1):
            t.set(x0 + 1, F, z, SPRUCE_TOP)
            g = C.pick(rng, GOODS)
            if g:
                t.set(x0 + 1, F + 1, z, g)
        C.awning(t, z0, z1, x0 - 1, F + 3, 'west', C.stripes(width=1), depth=2, poles=(0, z1 - z0), pole_state=FENCE)
        C.crates(t, [(x1 - 1, F, z0 + 1), (x1 - 2, F, z0 + 1), (x1 - 1, F + 1, z0 + 1)], rng, CRATES)
        if top >= 11:
            t.fill(x0 + 1, F + 4, z0 + 1, x1 - 1, F + 4, z1 - 1, 'spruce_planks')
            for y in range(F, top + 1):
                t.set(x1 - 1, y, z1 - 1, S('ladder', facing='west', waterlogged=False))
            t.air(x1 - 1, top + 1, z1 - 1, x1 - 1, top + 2, z1 - 1)
            C.bed(t, x0 + 1, F + 5, z0 + 1, 'east', 'brown' if i else 'red')
            jar(t, x0 + 1, F + 5, z1 - 1, rng)
            windows(t, x0, z0, x1, z1, F + 6, 2, 'iron_bars', sides=('west',))
            C.hang(t, x0 + 2, F + 2, (z0 + z1) // 2, drop=1)
            C.hang(t, x0 + 3, top - 2, (z0 + z1) // 2, drop=1)
        else:
            C.hang(t, x0 + 2, top - 2, (z0 + z1) // 2, drop=1)
    # stalls in the square
    stall(t, 48, 10, 50, 12, 'east', rng, GOODS)
    stall(t, 48, 15, 50, 17, 'east', rng, GOODS)
    stall(t, 51, 19, 53, 21, 'north', rng, GOODS, cloth_b='or:canvas_navy')
    # the water seller: a cistern kiosk with water on show, behind a grille
    t.fill(48, F, 6, 53, F, 6, 'or:salt_bricks')
    t.set(49, F, 7, 'water_cauldron[level=3]')
    t.set(50, F, 7, 'water_cauldron[level=2]')
    t.set(52, F, 7, 'water_cauldron[level=3]')
    jar(t, 51, F, 7, rng)


# ---------------------------------------------------------------- the water tower (north-east corner)

def water_tower(t):
    """The kajidic's condenser tower: a square adobe shaft, a glass tank of water half way up, a crown
    of copper vanes and rods; the cistern house at its foot with the water trough on the lane."""
    rng = t.rng
    wall = C.mix(ADOBE, SEED + 50, 2.5)
    cx, cz = 76, 13
    # base hall
    shell(t, 71, 8, 81, 18, 9, wall, floor=C.mix(TILE, SEED + 51), roof='or:adobe_bricks', parapet=1)
    door(t, 'west', 71, 8, 81, 18, 12, width=3, height=4, arch='or:adobe_stairs')
    # the shaft
    for (x, z) in C.ring_cells(cx - 2, cz - 2, cx + 2, cz + 2):
        for y in range(10, 27):
            t.set(x, y, z, wall(x, y, z))
    t.air(cx - 1, 10, cz - 1, cx + 1, 26, cz + 1)
    # the tank: glass walls round the shaft, water inside
    for (x, z) in C.ring_cells(cx - 3, cz - 3, cx + 3, cz + 3):
        for y in range(19, 24):
            t.set(x, y, z, 'light_blue_stained_glass')
    t.fill(cx - 3, 18, cz - 3, cx + 3, 18, cz + 3, 'waxed_weathered_cut_copper')
    t.fill(cx - 3, 24, cz - 3, cx + 3, 24, cz + 3, 'waxed_weathered_cut_copper')
    for (x, z) in C.ring_cells(cx - 2, cz - 2, cx + 2, cz + 2):
        pass
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            if abs(x - cx) == 2 or abs(z - cz) == 2:
                for y in range(19, 24):
                    t.set(x, y, z, 'water')
    # the crown: a copper cap, vanes and rods
    for (x, z) in C.ring_cells(cx - 2, cz - 2, cx + 2, cz + 2):
        t.set(x, 27, z, 'waxed_weathered_cut_copper')
    t.fill(cx - 1, 27, cz - 1, cx + 1, 27, cz + 1, 'waxed_weathered_copper_grate')
    for y in range(28, 34):
        t.set(cx, y, cz, 'waxed_weathered_cut_copper' if y < 31 else 'waxed_weathered_copper_grate')
    for (dx, dz) in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        for y in range(28, 31):
            t.set(cx + dx, y, cz + dz, 'waxed_weathered_copper_grate')
        t.set(cx + dx, 31, cz + dz, 'waxed_weathered_lightning_rod[facing=up,powered=false,waterlogged=false]')
    t.set(cx, 34, cz, 'waxed_weathered_lightning_rod[facing=up,powered=false,waterlogged=false]')
    for (x, z) in ((cx - 2, cz - 2), (cx + 2, cz - 2), (cx - 2, cz + 2), (cx + 2, cz + 2)):
        t.set(x, 28, z, 'waxed_weathered_lightning_rod[facing=up,powered=false,waterlogged=false]')
    # pipes from the tank down into the hall
    for y in range(10, 18):
        t.set(cx - 3, y, cz + 3, 'or:durasteel_pipes[axis=y]')
    # inside the hall: the cistern, valves, an enforcer's bench
    shapes.disk(t, cx, G, cz, 2.5, 'water')
    shapes.disk(t, cx, F, cz, 3.2, 'or:salt_bricks', hollow=True)
    t.set(cx, F + 4, cz - 4 + 1, AIR)
    for (x, z) in ((73, 10), (73, 16), (79, 10), (79, 16)):
        t.set(x, F, z, 'barrel[facing=up,open=false]')
    # the cistern house and the trough on the lane
    shell(t, 66, 9, 70, 17, 6, C.mix(SALT, SEED + 52, 2.5), floor='smooth_sandstone', roof='or:salt_bricks', parapet=1,
          vigas=False)
    shapes.dome(t, 68, 7, 13, 2.5, 'smooth_sandstone')
    door(t, 'west', 66, 9, 70, 17, 12, width=3, height=3)
    t.fill(66, F, 18, 70, F, 18, 'or:salt_bricks')
    t.fill(67, G, 18, 69, G, 18, 'water')
    t.set(68, F, 18, 'water')
    t.set(67, F, 18, 'or:salt_bricks')
    t.set(69, F, 18, 'or:salt_bricks')
    t.air(66, F, 19, 70, 8, 22)
    C.crates(t, [(80, F, 20), (81, F, 20), (81, F + 1, 20), (82, F, 21), (82, F, 6), (81, F, 6)], rng, CRATES)


# ---------------------------------------------------------------- the west quarter

def west_quarter(t):
    """Two homes north of the medbay and a droid workshop south of it."""
    rng = t.rng
    home(t, 5, 28, 13, 35, 12, SEED + 60, door_side='east', door_at=31)
    home(t, 14, 28, 22, 35, 8, SEED + 61, door_side='east', door_at=30, mud=True)
    # the workshop: a scrap-roofed shed open to the lane
    wall = C.mix(MUD, SEED + 62, 2.5)
    shell(t, 7, 53, 22, 60, 8, wall, floor=C.mix([('or:durasteel_deck', 2), ('packed_mud', 2), ('coarse_dirt', 1)], SEED + 63),
          roof=C.mix(SCRAP, SEED + 64), parapet=0, vigas=False)
    t.air(22, F, 54, 22, F + 3, 59)
    t.set(22, F + 4, 54, 'or:durasteel_beam[axis=z]')
    for z in range(54, 60):
        t.set(22, F + 4, z, 'or:durasteel_beam[axis=z]')
    t.set(12, F, 54, 'anvil[facing=north]')
    t.set(13, F, 54, 'smithing_table')
    t.set(9, F, 54, 'blast_furnace[facing=south,lit=true]')
    for x in (15, 16, 17):
        t.set(x, F, 59, 'or:wreck_salvage')
    t.set(16, F + 1, 59, 'or:wreck_conduit')
    t.set(10, F, 58, 'or:console[facing=north]')
    t.set(8, F, 58, 'grindstone[face=floor,facing=north]')
    C.hang(t, 14, 6, 56, drop=2)


def home(t, x0, z0, x1, z1, top, seed, door_side='east', door_at=None, mud=False):
    """A home of one floor (top under 11) or two: a hearth, water jars, a table and a loom below, beds
    above, a ladder from the floor through the upper floor to the roof terrace."""
    rng = t.rng
    wall = C.mix(MUD if mud else ADOBE_WORN, seed, 2.5)
    shell(t, x0, z0, x1, z1, top, wall, floor=C.mix(TILE, seed + 1, 2.0), roof='or:adobe', parapet=1)
    if door_at is None:
        door_at = (z0 + z1) // 2 if door_side in ('east', 'west') else (x0 + x1) // 2
    door(t, door_side, x0, z0, x1, z1, door_at, width=1, height=3)
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    inside = {'east': (ix1, door_at), 'west': (ix0, door_at), 'north': (door_at, iz0), 'south': (door_at, iz1)}[door_side]
    keep = {(inside[0] + dx, inside[1] + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    corners = [(ix0, iz0), (ix1, iz0), (ix0, iz1), (ix1, iz1)]
    corners.sort(key=lambda c: -((c[0] - inside[0]) ** 2 + (c[1] - inside[1]) ** 2))
    lad = corners[0]
    face = 'east' if lad[0] == ix0 else 'west'
    two = top >= 11
    upper = F + 4
    windows(t, x0, z0, x1, z1, F + 1, 3, 'iron_bars')
    if two:
        t.fill(ix0, upper, iz0, ix1, upper, iz1, 'spruce_planks')
        windows(t, x0, z0, x1, z1, upper + 2, 3, 'iron_bars')
    for y in range(F, top + 1):
        t.set(lad[0], y, lad[1], S('ladder', facing=face, waterlogged=False))
    t.air(lad[0], top + 1, lad[1], lad[0], top + 2, lad[1])

    def free(x, z, y=F):
        return (x, z) not in keep and (x, z) != lad and t.is_air(x, y, z)
    # the hearth and the jars in the far corners, a table by the wall, a rug in the middle
    hearth, jars = corners[1], corners[2]
    if free(*hearth):
        t.set(hearth[0], F, hearth[1], 'smoker[facing=%s,lit=true]' % ('east' if hearth[0] == ix0 else 'west'))
    for (x, z) in (jars, (jars[0] + (1 if jars[0] == ix0 else -1), jars[1])):
        if free(x, z):
            jar(t, x, F, z, rng)
    mx, mz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    if free(mx, iz1) and free(mx - 1, iz1):
        t.set(mx, F, iz1, SPRUCE_TOP)
        C.seat(t, mx - 1, F, iz1, 'east', 'spruce_stairs')
    if free(mx, iz0) and (ix1 - ix0) >= 4:
        t.set(mx, F, iz0, 'loom[facing=south]' if mud else 'barrel[facing=up,open=false]')
    if free(mx, mz):
        t.set(mx, F, mz, 'brown_carpet' if mud else 'red_carpet')
    if two:
        C.hang(t, mx, upper - 1, mz, drop=1)
        # upstairs: beds along the far wall, a chest of clothes (a barrel), a jar
        bz = iz0 if lad[1] != iz0 else iz1
        bx = ix0 + 1 if lad[0] == ix0 else ix0
        if (ix1 - ix0) >= 3:
            C.bed(t, bx, upper + 1, bz, 'east', 'brown' if mud else 'red')
        t.set(ix1 if lad[0] != ix1 else ix0, upper + 1, iz1 if bz == iz0 else iz0, 'barrel[facing=up,open=false]')
        t.set(mx, upper + 1, mz, 'white_carpet')
        C.hang(t, mx, top - 2, mz, drop=1)
    else:
        if (ix1 - ix0) >= 4 and free(ix0 + 1, iz0) and free(ix0 + 2, iz0):
            C.bed(t, ix0 + 1, F, iz0, 'east', 'brown' if mud else 'red')
        C.hang(t, mx, top - 2, mz, drop=1)


def medbay(t):
    """The medbay facing the field's west gate: beds, two bacta tanks, the droid's console."""
    x0, z0, x1, z1 = 8, 37, 22, 51
    wall = C.mix(WHITE, SEED + 70, 2.5)
    shell(t, x0, z0, x1, z1, 8, wall, floor=C.mix([('or:pale_tiles', 6), ('smooth_sandstone', 1)], SEED + 71),
          roof='white_terracotta', parapet=1, vigas=False)
    door(t, 'east', x0, z0, x1, z1, 43, width=3, height=4, arch='sandstone_stairs')
    windows(t, x0, z0, x1, z1, F + 2, 3, 'light_blue_stained_glass_pane', sides=('north', 'south'))
    for z in range(39, 50, 3):
        C.bed(t, 10, F, z, 'west', 'white')
    for z in (39, 42, 48):
        t.set(11, F, z, 'white_carpet')
    # bacta tanks: glass round a column of water on a plinth
    for (x, z) in ((14, 39), (14, 49)):
        t.set(x, F, z, 'or:durasteel_plating')
        for y in (F + 1, F + 2):
            t.set(x, y, z, 'light_blue_stained_glass')
        t.set(x, F + 3, z, 'or:durasteel_vent')
    t.set(17, F, 39, 'or:console[facing=south]')
    t.set(17, F, 49, 'or:console[facing=north]')
    for z in range(40, 49):
        if z in (43, 44, 45):
            continue
    for (x, z) in ((11, 41), (11, 47), (17, 44)):
        C.hang(t, x, 6, z, drop=1, lamp='lantern')
    t.set(15, 7, 44, 'or:light_panel')
    t.set(11, 7, 44, 'or:light_panel')
    t.set(19, 7, 40, 'or:light_panel')
    t.set(19, 7, 48, 'or:light_panel')
    # red cloth outside marks it from across the field
    C.awning(t, 41, 47, 23, F + 4, 'east', lambda k, i: 'white_wool' if k % 2 else 'red_wool', depth=1)


# ---------------------------------------------------------------- the hangar (east)

def hangar(t):
    """An old freighter hull cut open for a hangar: a barrel vault of patched plating on ribs, a hull
    on its struts inside, the shipwright's bench and the spacer's counter at its mouth, a crane."""
    rng = t.rng
    x0, z0, x1, z1 = 67, 30, 82, 58
    plate = C.mix(SCRAP, SEED + 80, 3.0)
    base = C.mix(ADOBE_WORN, SEED + 81, 2.5)
    t.fill(x0, G, z0, x1, G, z1, C.mix([('or:durasteel_deck', 5), ('smooth_sandstone', 2), ('or:durasteel_grate', 1),
                                         ('or:rusted_plating', 1)], SEED + 82, 3.0))
    # the low walls on the long sides and the back, the hull's vault over them
    for z in range(z0, z1 + 1):
        for y in range(G, 8):
            t.set(x1, y, z, base(x1, y, z))
    for x in range(x0, x1 + 1):
        for y in range(G, 8):
            t.set(x, y, z0, base(x, y, z0))
            t.set(x, y, z1, base(x, y, z1))
    t.air(x0 + 1, F, z0 + 1, x1 - 1, 8, z1 - 1)
    cxm = (x0 + x1) / 2.0
    r = (x1 - x0) / 2.0
    import math
    for z in range(z0, z1 + 1):
        rib = (z - z0) % 4 == 0
        for x in range(x0, x1 + 1):
            u = (x - cxm) / r
            yy = 7 + int(round(7 * math.sqrt(max(0.0, 1 - u * u))))
            t.set(x, yy, z, 'or:durasteel_beam[axis=z]' if rib and abs(u) < 0.9 else plate(x, yy, z))
            if abs(u) > 0.75:
                t.set(x, yy - 1, z, plate(x, yy - 1, z))
            if z in (z0, z1):
                for y in range(8, yy):
                    t.set(x, y, z, plate(x, y, z))
    # holes in the hull, scorched round their edges
    for (hx, hz) in ((73, 37), (77, 50)):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                for y in range(12, 16):
                    if t.get(hx + dx, y, hz + dz) is not None:
                        t.set(hx + dx, y, hz + dz, AIR if abs(dx) + abs(dz) < 2 else 'or:wreck_plating_scorched')
    # the mouth: the west side open between beam posts
    for z in range(z0, z1 + 1, 4):
        t.fill(x0, G, z, x0, 7, z, 'or:durasteel_beam[axis=y]')
    t.air(x0, F, z0 + 1, x0, 6, z1 - 1)
    for z in range(z0, z1 + 1, 4):
        t.fill(x0, F, z, x0, 6, z, 'or:durasteel_beam[axis=y]')
    t.fill(x0, 7, z0, x0, 7, z1, 'or:durasteel_beam[axis=z]')
    # the hull under repair: a short freighter body on struts, its ramp down
    hull(t, 75, 41, 75, 51)
    # the shipwright's bench and the spacer's counter
    t.fill(70, F, 43, 70, F, 46, 'or:durasteel_plating_slab[type=top,waterlogged=false]')
    t.set(70, F, 42, 'smithing_table')
    t.set(70, F, 47, 'anvil[facing=east]')
    t.set(71, F + 2, 41, AIR)
    for z in range(33, 37):
        t.set(71, F, z, 'or:durasteel_plating_slab[type=top,waterlogged=false]')
    C.crates(t, [(72, F, 32), (73, F, 32), (72, F + 1, 32), (72, F, 31), (73, F, 31), (74, F, 31), (73, F + 1, 31)], rng, CRATES)
    t.set(71, F + 1, 34, 'or:console[facing=west]')
    # parts racks along the back wall
    for z in range(32, 57, 2):
        t.set(81, F, z, C.pick(rng, [('or:wreck_salvage', 2), ('or:cargo_crate', 2), ('barrel[facing=up,open=false]', 1),
                                     ('or:wreck_conduit', 1)]))
        if rng.random() < 0.6:
            t.set(81, F + 1, z, C.pick(rng, CRATES))
    for z in range(33, 56, 5):
        t.set(81, F + 3, z, 'or:light_panel_amber')
    for (x, z) in ((72, 38), (72, 54), (79, 38), (79, 54)):
        C.hang(t, x, 10, z, drop=3, lamp='lantern')
    # the crane: a lattice mast at the hangar's corner, a jib over the hull, a hook on a chain
    mx, mz = 83, 59
    for y in range(F, 30):
        t.set(mx, y, mz, 'or:durasteel_beam[axis=y]')
    for x in range(74, 84):
        t.set(x, 30, mz, 'or:durasteel_beam[axis=x]')
    t.set(mx, 31, mz, 'waxed_weathered_lightning_rod[facing=up,powered=false,waterlogged=false]')
    t.set(84, 29, mz, 'or:cargo_crate')
    for y in range(17, 30):
        t.set(74, y, mz, C.chain('iron_chain')) if y > 20 else None
    t.set(74, 20, mz, 'heavy_core[waterlogged=false]')


def hull(t, x0, z0, x1, z1):
    """A small freighter hull resting on struts: a plated body, a cockpit glass at its nose (north),
    engines at its tail, a ramp down on its west side."""
    plate = C.mix([('or:durasteel_plating', 5), ('or:rusted_plating', 2), ('or:wreck_plating', 1)], SEED + 85, 2.5)
    for z in range(z0, z1 + 1):
        for (dx, dy) in ((-2, 5), (-1, 6), (0, 6), (1, 6), (2, 5), (-3, 4), (3, 4), (-3, 5), (3, 5), (-2, 7), (-1, 7), (0, 7),
                         (1, 7), (2, 7)):
            t.set(x0 + dx, dy, z, plate(x0 + dx, dy, z))
    t.air(x0 - 2, 5, z0 + 1, x0 + 2, 6, z1 - 1)
    t.fill(x0 - 2, 4, z0, x0 + 2, 4, z1, 'or:durasteel_deck')
    for (x, z) in ((x0 - 2, z0 + 1), (x0 + 2, z0 + 1), (x0 - 2, z1 - 1), (x0 + 2, z1 - 1)):
        t.fill(x, F, z, x, F, z, 'or:durasteel_beam[axis=y]')
    for x in range(x0 - 2, x0 + 3):
        t.set(x, 5, z0, 'light_blue_stained_glass')
        t.set(x, 6, z0, 'light_blue_stained_glass') if abs(x - x0) < 2 else None
    for (x, y) in ((x0 - 1, 5), (x0 + 1, 5)):
        t.set(x, y, z1 + 1, 'or:durasteel_vent')
    t.set(x0, 5, z1 + 1, 'or:light_panel_red')
    # the ramp down to the west
    t.set(x0 - 3, 4, 46, 'or:durasteel_plating_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
    t.set(x0 - 3, 4, 47, 'or:durasteel_plating_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
    t.set(x0 - 4, F, 46, 'or:durasteel_plating_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
    t.set(x0 - 4, F, 47, 'or:durasteel_plating_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
    t.air(x0 - 3, 5, 46, x0 - 3, 6, 47)


# ---------------------------------------------------------------- Vessba's palace (south)

def palace(t):
    """The palace: a garden wall on the market street with a gate of salt, a forecourt round a pool of
    open water, the audience hall with its copper dome over Vessba's dais."""
    rng = t.rng
    salt = C.mix(SALT, SEED + 90, 2.5)
    adobe = C.mix(ADOBE, SEED + 91, 2.5)
    # the garden wall and its gate
    for x in range(28, 61):
        for y in range(G, 7):
            t.set(x, y, 66, salt(x, y, 66))
        t.set(x, 7, 66, 'or:adobe_wall' if x % 2 else 'or:salt_bricks')
    for z in range(66, 72):
        for y in range(G, 7):
            t.set(28, y, z, salt(28, y, z))
            t.set(60, y, z, salt(60, y, z))
    t.air(42, F, 66, 46, 7, 66)
    for x in (40, 41, 47, 48):
        t.fill(x, G, 65, x, 9, 66, 'or:salt_bricks')
        t.set(x, 10, 65, 'or:adobe_wall')
    t.fill(40, 9, 65, 48, 9, 66, 'or:salt_bricks')
    t.air(42, F, 65, 46, 8, 65)
    C.arch_top(t, 42, 46, 65, 66, 8, 'sandstone_stairs')
    for x in (41, 47):
        mod.banner(t, x, 8, 64, 'north', base='red')
    # the forecourt: paving, the pool, cactus beds
    t.fill(29, G, 67, 59, G, 71, C.mix(PAVING, SEED + 92, 2.5))
    t.air(29, F, 67, 59, 8, 71)
    t.fill(36, G, 68, 52, G, 70, 'or:salt_bricks')
    t.fill(37, G, 68, 51, G, 70, 'water')
    t.fill(36, F, 68, 52, F, 70, 'smooth_sandstone_slab[type=bottom,waterlogged=false]')
    t.air(37, F, 68, 51, F, 70)
    t.fill(36, G - 1, 68, 52, G - 1, 70, 'or:salt_bricks')
    for x in (37, 51):
        t.set(x, G, 69, 'water')
    t.set(44, G, 69, 'or:salt_bricks')
    t.set(44, F, 69, 'chiseled_sandstone')
    t.set(44, F + 1, 69, 'or:salt_bricks')
    t.set(44, F + 2, 69, lantern('waxed_copper_lantern'))
    for (x, z) in ((31, 68), (33, 70), (55, 68), (57, 70), (30, 70), (58, 68)):
        t.set(x, F, z, 'cactus') if (x + z) % 2 == 0 else t.set(x, F, z, 'dead_bush')
        t.set(x, G, z, 'coarse_dirt')
    # the audience hall
    x0, z0, x1, z1 = 32, 72, 56, 83
    top = 13
    shell(t, x0, z0, x1, z1, top, lambda x, y, z: salt(x, y, z) if y < 6 else adobe(x, y, z), floor=C.mix(
        [('smooth_sandstone', 4), ('or:salt_bricks', 2), ('cut_sandstone', 1)], SEED + 93, 2.0), roof='or:adobe_bricks',
        parapet=1, vigas=False, band=lambda x, y, z: 'cut_sandstone')
    door(t, 'north', x0, z0, x1, z1, 42, width=5, height=5, arch='sandstone_stairs')
    windows(t, x0, z0, x1, z1, 7, 4, 'iron_bars', sides=('north', 'east', 'west'))
    windows(t, x0, z0, x1, z1, 8, 4, 'iron_bars', sides=('north', 'east', 'west'))
    # pillars in two rows, crimson cloth between them
    for x in (37, 51):
        for z in (75, 79):
            shapes.pillar(t, x, F, top - 1, z, 'cut_sandstone', base='chiseled_sandstone', capital='chiseled_sandstone')
            mod.banner(t, x, top - 3, z - 1, 'north', base='red')
    for z in range(73, 78):
        t.set(44, G, z, 'or:salt_bricks')
    C.carpet_run(t, 43, 73, 45, 77, F, 'red')
    # the dais: two steps up, Vessba's bulk on it, Teel beside the steps
    t.fill(39, F, 78, 49, F, 82, 'cut_sandstone')
    t.fill(40, F + 1, 79, 48, F + 1, 82, 'smooth_sandstone')
    for x in range(39, 50):
        t.set(x, F, 78, stair('sandstone_stairs', 'north'))
    for x in range(40, 49):
        t.set(x, F + 1, 79, stair('sandstone_stairs', 'north'))
    t.fill(42, F + 2, 80, 46, F + 2, 82, 'red_carpet')
    t.air(42, F + 2, 80, 46, F + 2, 82)
    t.fill(42, F + 2, 80, 46, F + 2, 82, 'red_carpet')
    for x in (40, 48):
        t.set(x, F + 2, 81, 'or:salt_bricks')
        t.set(x, F + 3, 81, lantern('waxed_copper_lantern'))
    for x in range(41, 48):
        t.set(x, F + 2, 82, 'red_wool' if x % 2 else 'orange_wool') if x in (41, 47) else None
    # water jars and food round the dais
    for (x, z) in ((39, 81), (49, 81), (39, 82), (49, 82)):
        jar(t, x, F + 1, z, rng)
    # the drum and the dome over the dais: the hall opens up into it
    whitewash = C.mix([('or:salt_bricks', 5), ('calcite', 3), ('smooth_quartz', 1)], SEED + 94, 2.0)
    dcx, dcz = 44, 78
    for y in range(top, top + 4):
        shapes.disk(t, dcx, y, dcz, 5.2, salt if y < top + 3 else 'cut_sandstone', hollow=True)
    for (dx, dz) in ((5, 0), (-5, 0), (0, -5), (0, 5), (4, 3), (-4, 3), (4, -3), (-4, -3)):
        t.set(dcx + dx, top + 2, dcz + dz, 'iron_bars')
    shapes.dome(t, dcx, top + 4, dcz, 5.2, whitewash, thick=1)
    shapes.ellipsoid(t, dcx, top + 4, dcz, 4.2, 4.2, 4.2, AIR, ymin=top + 4)
    for (x, y, z), st in list(t.cells.items()):          # a copper band where the dome meets the drum
        if y == top + 4 and abs(x - dcx) <= 6 and abs(z - dcz) <= 6 and C.B.name_of(st) != 'minecraft:air':
            t.set(x, y, z, 'waxed_weathered_cut_copper')
    shapes.cylinder(t, dcx, top, top + 3, dcz, 4.2, AIR)
    t.set(dcx, top + 10, dcz, 'waxed_weathered_cut_copper')
    t.set(dcx, top + 11, dcz, 'waxed_weathered_lightning_rod[facing=up,powered=false,waterlogged=false]')
    C.hang(t, dcx, top - 1, dcz, drop=9, lamp='waxed_copper_lantern', chain_name='waxed_copper_chain')
    for (x, z) in ((36, 74), (52, 74), (36, 81), (52, 81)):
        C.hang(t, x, top - 2, z, drop=1)
    # two minarets at the front corners, copper caps and lamps
    for (mx, mz) in ((32, 72), (56, 72)):
        for x in range(mx - 1, mx + 2):
            for z in range(mz - 1, mz + 2):
                for y in range(G, 20):
                    t.set(x, y, z, salt(x, y, z) if y < 6 or y > 16 else adobe(x, y, z))
        for y in (8, 12, 16):
            for (x, z) in ((mx, mz - 1), (mx - 1, mz), (mx + 1, mz)):
                t.set(x, y, z, 'iron_bars')
        t.fill(mx - 1, 20, mz - 1, mx + 1, 20, mz + 1, 'cut_sandstone')
        t.set(mx, 21, mz, 'waxed_weathered_cut_copper')
        for (x, z) in ((mx - 1, mz), (mx + 1, mz), (mx, mz - 1), (mx, mz + 1)):
            t.set(x, 21, z, 'waxed_weathered_cut_copper_slab[type=bottom,waterlogged=false]')
        t.set(mx, 22, mz, 'waxed_weathered_lightning_rod[facing=up,powered=false,waterlogged=false]')
        for (x, z) in ((mx - 1, mz - 1), (mx + 1, mz - 1), (mx - 1, mz + 1), (mx + 1, mz + 1)):
            t.set(x, 21, z, lantern('waxed_copper_lantern'))


# ---------------------------------------------------------------- homes and the scrap yard

def south_west_homes(t):
    home(t, 5, 66, 13, 74, 12, SEED + 100, door_side='east', door_at=70)
    home(t, 15, 66, 22, 73, 8, SEED + 101, door_side='south', door_at=18, mud=True)
    home(t, 5, 76, 12, 83, 11, SEED + 102, door_side='north', door_at=8, mud=True)
    home(t, 15, 76, 22, 83, 13, SEED + 103, door_side='north', door_at=18)
    # the well in the yard between them: dry, a bucket chain over it
    shapes.disk(t, 14, F, 75, 1.2, 'or:adobe_bricks', hollow=True)
    t.set(14, F, 75, AIR)
    t.set(14, G, 75, 'or:salt_crust')


def scrap_yard(t):
    """A salvager's yard behind a scrap fence: piles of plating, an engine nacelle, the workshop."""
    rng = t.rng
    wall = C.mix(SCRAP, SEED + 110, 2.0)
    for x in range(66, 84):
        for y in range(F, 6):
            if x not in (70, 71, 72):
                t.set(x, y, 66, wall(x, y, 66))
    shell(t, 74, 72, 83, 83, 8, C.mix(MUD, SEED + 111), floor='packed_mud', roof=C.mix(SCRAP, SEED + 112), parapet=0, vigas=False)
    door(t, 'west', 74, 72, 83, 83, 76, width=3, height=3)
    t.set(78, F, 82, 'blast_furnace[facing=north,lit=true]')
    t.set(80, F, 82, 'anvil[facing=west]')
    t.set(82, F, 79, 'or:console[facing=west]')
    # the nacelle: a cylinder of plating on its side
    import math
    for z in range(69, 79):
        for dx in range(-3, 4):
            for dy in range(-3, 4):
                d = math.hypot(dx, dy)
                if 2.2 < d <= 3.3:
                    x, y = 69 + dx, 6 + dy
                    if y >= F:
                        t.set(x, y, z, wall(x, y, z))
    for (x, z) in ((67, 80), (68, 81), (71, 82), (78, 69), (80, 70)):
        for y in range(F, F + 1 + int(rng.random() * 2)):
            t.set(x, y, z, C.pick(rng, [('or:wreck_salvage', 2), ('or:rusted_plating', 2), ('or:debris', 1)]))


# ---------------------------------------------------------------- streets

def streets(t):
    """Paving on the main street, stalls along the field wall, lamps, drifts of sand."""
    rng = t.rng
    t.fill(41, G, 9, 47, G, 27, C.mix(PAVING, SEED + 120, 3.0))
    for z in range(10, 23, 6):
        C.lamp_post(t, 41, F, z, 3, 'sandstone_wall')
        C.lamp_post(t, 47, F, z + 3, 3, 'sandstone_wall')
    for (x, z) in ((23, 30), (27, 40), (23, 50), (27, 58), (61, 30), (65, 40), (61, 52), (65, 58), (32, 23), (56, 27),
                   (32, 65), (56, 61)):
        C.lamp_post(t, x, F, z, 3, 'sandstone_wall')
    # stalls along the outside of the field wall
    stall(t, 30, 25, 32, 27, 'north', rng, GOODS)
    stall(t, 52, 25, 54, 27, 'north', rng, GOODS)
    stall(t, 25, 52, 27, 54, 'west', rng, GOODS, cloth_b='or:canvas_navy')
    stall(t, 61, 32, 63, 34, 'east', rng, GOODS)
    stall(t, 33, 61, 35, 63, 'south', rng, GOODS)
    stall(t, 52, 61, 54, 63, 'south', rng, GOODS, cloth_b='or:canvas_navy')


# ---------------------------------------------------------------- infill, towers, roofs, drifts

def vaporator(t, x, y, z, tall=3):
    """A moisture vaporator: a pale shaft on a footing with a copper collar and a rod on top."""
    t.set(x, y, z, 'polished_andesite')
    for k in range(1, tall):
        t.set(x, y + k, z, 'andesite_wall')
    t.set(x, y + tall, z, 'waxed_exposed_copper_grate')
    t.set(x, y + tall + 1, z, 'waxed_exposed_lightning_rod[facing=up,powered=false,waterlogged=false]')


def infill(t):
    """Smaller buildings in the gaps: the cantina's still house, a lodging house on the bazaar's north
    side, the palace's wings, a salvager's house and a wreck in the scrap yard, alley clutter."""
    rng = t.rng
    # north strip, west of the gate: the still house (the cantina's spirits are brewed from cactus)
    shell(t, 28, 5, 34, 8, 7, C.mix(ADOBE_WORN, SEED + 140, 2.5), floor='packed_mud', roof='or:adobe', parapet=1)
    door(t, 'east', 28, 5, 34, 8, 6, width=1, height=3)
    t.set(29, F, 6, 'brewing_stand[has_bottle_0=true,has_bottle_1=true,has_bottle_2=false]')
    t.set(30, F, 6, 'cauldron')
    t.set(31, F, 6, 'smoker[facing=south,lit=true]')
    C.barrel(t, 29, F, 7, 'east')
    C.barrel(t, 30, F, 7, 'east')
    t.set(32, F, 7, 'or:cargo_crate')
    # north strip, east of the bazaar: a lodging house of two floors with a terrace
    lodging = C.mix(ADOBE, SEED + 141, 2.5)
    shell(t, 54, 5, 60, 8, 11, lodging, floor=C.mix(TILE, SEED + 142), roof='or:adobe', parapet=1)
    t.fill(55, 7, 6, 59, 7, 7, 'spruce_planks')
    door(t, 'south', 54, 5, 60, 8, 57, width=1, height=3)
    t.air(55, 7, 7, 55, 7, 7)
    for y in range(F, 8):
        t.set(55, y, 6, S('ladder', facing='south', waterlogged=False))
    t.air(55, 7, 6, 55, 7, 6)
    t.set(55, 7, 6, S('ladder', facing='south', waterlogged=False))
    C.bed(t, 57, 8, 6, 'east', 'brown')
    C.bed(t, 57, F, 6, 'east', 'red')
    windows(t, 54, 5, 60, 8, F + 1, 3, 'iron_bars', sides=('south', 'east'))
    windows(t, 54, 5, 60, 8, 9, 3, 'iron_bars', sides=('south', 'east'))
    # the palace's wings: Teel's office (west) and the guard house (east), two floors each
    for (x0, x1, door_x) in ((28, 31, 31), (57, 60, 57)):
        wing = C.mix(SALT, SEED + 143 + x0, 2.5)
        shell(t, x0, 72, x1, 83, 11, lambda x, y, z, w=wing: w(x, y, z) if y < 7 else C.mix(ADOBE, SEED + 150)(x, y, z),
              floor=C.mix(TILE, SEED + 144), roof='or:adobe', parapet=1, vigas=False)
        t.fill(x0 + 1, 7, 73, x1 - 1, 7, 82, 'spruce_planks')
        t.air(door_x, F, 76, door_x, F + 2, 76)
        windows(t, x0, 72, x1, 83, 9, 3, 'iron_bars', sides=('north', 'east' if x0 > 40 else 'west'))
    t.set(29, F, 74, SPRUCE_TOP)
    t.set(29, F, 75, 'lectern[facing=east,has_book=false,powered=false]')
    C.crates(t, [(29, F, 81), (30, F, 81), (29, F + 1, 81), (29, F, 82)], rng, CRATES)
    for z in (74, 78, 82):
        C.bed(t, 59, F, z - 1, 'south', 'gray') if z < 82 else None
    t.set(58, F, 74, 'or:cargo_crate_imperial')
    # salvager's house in the scrap yard
    shell(t, 66, 77, 72, 83, 8, C.mix(MUD, SEED + 145, 2.5), floor='packed_mud', roof=C.mix(SCRAP, SEED + 146), parapet=1)
    door(t, 'north', 66, 77, 72, 83, 69, width=1, height=3)
    C.bed(t, 67, F, 81, 'north', 'brown')
    t.set(71, F, 82, 'smoker[facing=west,lit=true]')
    jar(t, 71, F, 78, rng)
    # a wrecked fighter in the yard: a plated body, one wing in the sand, a cracked canopy
    for x in range(76, 82):
        t.set(x, F, 69, 'or:wreck_plating')
        t.set(x, F + 1, 69, 'or:wreck_plating_scorched' if x % 2 else 'or:wreck_plating')
    t.set(82, F + 1, 69, 'tinted_glass')
    t.set(82, F, 69, 'or:wreck_plating')
    for z in range(70, 73):
        t.set(78, F, z, 'or:wreck_plating')
    t.set(78, F + 1, 70, 'or:wreck_conduit')
    t.set(75, F, 69, 'or:scorch_mark')
    t.set(75, F, 68, 'or:debris')


MID_TOWERS = ((1, 41, 5, 47), (83, 41, 87, 47))     # x0, z0, x1, z1: across the west and east walls


def mid_towers(t):
    """Square towers halfway along the west and east walls, astride the walk: enforcer lookouts. The walk
    runs through them; a ladder climbs to the roof under a crimson shade."""
    for (x0, z0, x1, z1) in MID_TOWERS:
        west = x0 < N // 2
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        wall = C.mix(ADOBE, SEED + 160 + cx + cz, 2.5)
        top = WALK + 5
        for (x, z) in C.ring_cells(x0, z0, x1, z1):
            for y in range(G, top + 1):
                t.set(x, y, z, wall(x, y, z))
            t.set(x, F, z, 'or:adobe_bricks')
            t.set(x, top + 1, z, 'or:salt_bricks' if (x + z) % 2 == 0 else AIR)
        t.fill(x0 + 1, G, z0 + 1, x1 - 1, WALK - 1, z1 - 1, wall)
        t.air(x0 + 1, WALK, z0 + 1, x1 - 1, top - 1, z1 - 1)
        t.fill(x0 + 1, top, z0 + 1, x1 - 1, top, z1 - 1, 'or:adobe_bricks')
        t.air(x0 + 1, top + 1, z0 + 1, x1 - 1, top + 1, z1 - 1)
        # the walk (x 3..4 or 84..85) runs in through the north side and out through the south
        wx0, wx1 = (3, 4) if west else (84, 85)
        t.air(wx0, WALK, z0, wx1, WALK + 2, z0)
        t.air(wx0, WALK, z1, wx1, WALK + 2, z1)
        # slits out over the land, a ladder on the outer wall up to the roof
        out = x0 if west else x1
        for z in (cz - 2, cz + 2):
            t.set(out, WALK + 2, z, 'iron_bars')
        lx = x0 + 1 if west else x1 - 1
        for y in range(WALK, top + 1):
            t.set(lx, y, cz, S('ladder', facing='east' if west else 'west', waterlogged=False))
        C.hang(t, cx, top - 2, cz - 2, drop=2)
        C.hang(t, cx, top - 2, cz + 2, drop=2)
        shade(t, cx - 1, cz - 1, cx + 1, cz + 1, top + 4, cloth='or:canvas_crimson', street=top + 1)


def roofs(t):
    """Life on the flat roofs: vaporators, water jars, a bed rolled out under the stars."""
    rng = t.rng
    for (x0, z0, x1, z1, y) in ROOFS:
        cells = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)
                 if t.get(x, y, z) is not None and not t.is_air(x, y, z) and t.is_air(x, y + 1, z)
                 and t.is_air(x, y + 2, z) and (x in (x0, x1) or z in (z0, z1))]
        if not cells:
            continue
        n = max(1, len(cells) // 9)
        for _ in range(n):
            x, z = cells[int(rng.random() * len(cells))]
            if not t.is_air(x, y + 1, z):
                continue
            r = rng.random()
            if r < 0.3:
                vaporator(t, x, y + 1, z, tall=2 + int(rng.random() * 2))
            elif r < 0.7:
                jar(t, x, y + 1, z, rng)
            elif r < 0.85:
                t.set(x, y + 1, z, 'barrel[facing=up,open=false]')
            else:
                t.set(x, y + 1, z, 'white_carpet' if rng.random() < 0.5 else 'red_carpet')


def drifts(t):
    """Sand blown in against the walls and into corners, dry growth where nobody walks."""
    rng = t.rng
    noise = C.nature.Noise(SEED + 170, 3.0)
    for x in range(IN0, IN1 + 1):
        for z in range(IN0, IN1 + 1):
            if not (t.is_air(x, F, z) and t.get(x, F, z) is not None and t.is_air(x, F + 1, z)):
                continue
            below = t.get(x, G, z)
            if below is None or C.B.name_of(below) in ('oldrepublic:durasteel_deck', 'oldrepublic:salt_bricks',
                                                        'oldrepublic:light_panel_amber', 'oldrepublic:durasteel_grate',
                                                        'oldrepublic:durasteel_plating', 'oldrepublic:rusted_plating',
                                                        'oldrepublic:durasteel_vent', 'water'):
                continue
            if FIELD0 - 1 <= x <= FIELD1 + 1 and FIELD0 - 1 <= z <= FIELD1 + 1:
                continue
            if any(t.is_solid(x, y, z) for y in range(F + 2, F + 12)):
                continue                      # under a roof or an awning: swept
            walls = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if t.is_solid(x + dx, F, z + dz)
                        and C.B.name_of(t.get(x + dx, F, z + dz)).split(':')[1] in (
                            'adobe', 'adobe_bricks', 'smooth_sandstone', 'sandstone', 'packed_mud', 'mud_bricks',
                            'salt_bricks', 'white_terracotta'))
            if walls == 0:
                continue
            v = noise.at(x, 0, z)
            r = rng.random()
            if walls >= 2 and r < 0.5 + v * 0.3:
                t.set(x, F, z, 'sand')
            elif r < 0.12 + v * 0.25:
                t.set(x, F, z, 'sand')
            elif r < 0.2 + v * 0.2:
                t.set(x, F, z, C.pick(rng, [('dead_bush', 2), ('short_dry_grass', 3), ('tall_dry_grass', 1), ('or:debris', 1)]))


# ---------------------------------------------------------------- the platform and its skirt

def skirt(t):
    """The town stands on a platform two layers above the land (site meta buried 1): layer 1 is fill
    under every street and floor, the walls and towers go down to layer 0, and outside them a terrace
    walk on layer 1 behind a kerb of brick drops to the land on layer 0. A broad stair climbs from the
    land to the caravan gate. Where the land falls away the placer's fill and apron carry on below."""
    fill = C.mix([('sandstone', 5), ('smooth_sandstone', 1)], SEED + 130, 4.0)
    for x in range(IN0, IN1 + 1):
        for z in range(IN0, IN1 + 1):
            if t.get(x, 1, z) is None:
                t.set(x, 1, z, fill(x, 1, z))
    # walls, towers and buttresses reach down to layer 0
    for (x, y, z), st in list(t.cells.items()):
        if y == G and not C.B.name_of(st) in ('minecraft:air',) and (x < IN0 or x > IN1 or z < IN0 or z > IN1):
            for yy in (0, 1):
                if t.get(x, yy, z) is None:
                    t.set(x, yy, z, 'or:adobe_bricks' if yy == 0 else st)
    # the terrace walk round the outside, its kerb, and the land ring left to the world
    walk = C.mix([('sandstone', 4), ('smooth_sandstone', 2), ('coarse_dirt', 1), ('or:salt_crust', 1)], SEED + 131, 3.0)
    for ring, top in ((1, 'kerb'), (2, 'walk')):
        lo, hi = ring, N - 1 - ring
        for (x, z) in C.ring_cells(lo, lo, hi, hi):
            if t.get(x, 1, z) is None:
                t.set(x, 0, z, 'or:adobe_bricks')
                t.set(x, 1, z, 'cut_sandstone' if top == 'kerb' and (x + z) % 5 else ('or:adobe_bricks' if top == 'kerb' else walk(x, 1, z)))


# ---------------------------------------------------------------- people, lights, checks

def people(t):
    mod.spot(t, 'HEART', *HEART)
    for i, (x, y, z) in enumerate(BAYS):
        mod.spot(t, 'PAD_BAY_%d' % (i + 1), x, y, z)
    # the field
    t.set(41, F, 31, 'or:pad_terminal[facing=east]')
    t.set(41, F + 1, 31, AIR)
    t.anchor('PAD_TERMINAL', 41, F, 31)
    mod.hub_spawner(t, 43, F, 30, 'host', anchor='HOST')
    # the cantina
    mod.hub_spawner(t, 31, F, 12, 'bartender', anchor='CANTINA')
    mod.hub_spawner(t, 30, F, 20, 'vendor:cartel_fence', anchor='VENDOR_FENCE')
    mod.hub_spawner(t, 37, F, 20, 'varra', anchor='NPC_VARRA')
    t.anchor('CONTRACT_TERMINAL', 35, F, 21)
    # the counting-house
    mod.hub_spawner(t, 23, F, 16, 'doorman', anchor='COUNTING_HOUSE_DOOR')
    for i, (x, y, z) in enumerate(((13, F, 7), (20, F, 7), (8, F, 15), (13, F, 19),
                                   (8, 9, 13), (13, 9, 9), (16, 9, 18), (8, 9, 19))):
        mod.spot(t, 'COUNTING_HOUSE_%d' % (i + 1), x, y, z)
    mod.spot(t, 'NPC_DASK', 19, 9, 14)
    # the palace
    mod.hub_spawner(t, 44, F + 2, 81, 'vessba', anchor='NPC_VESSBA')
    mod.hub_spawner(t, 41, F, 77, 'teel', anchor='NPC_TEEL')
    # the hangar
    mod.hub_spawner(t, 69, F, 44, 'vendor:saltgate_shipwright', anchor='VENDOR_SHIPWRIGHT')
    mod.hub_spawner(t, 70, F, 35, 'vendor:spacer', anchor='VENDOR_SPACER')
    # the medbay
    mod.spot(t, 'MEDBAY', 19, F, 44)
    # enforcers: the caravan gate, the counting-house lane, the palace gate, the water tower, the walk
    posts = ((41, F, 9), (47, F, 9), (24, F, 20), (40, F, 63), (64, F, 13), (44, WALK, 4))
    for i, (x, y, z) in enumerate(posts):
        mod.hub_spawner(t, x, y, z, 'enforcer', anchor='ENFORCER_%d' % (i + 1))
    for (x, y, z) in ((48, F, 63), (49, F, 25), (60 - 1, F, 47 - 2)):
        mod.hub_spawner(t, x, y, z, 'enforcer')


def lights(t):
    # the roof terraces over the cantina and the counting-house: a lantern under each awning
    for (x, y, z) in ((33, 12, 12), (33, 12, 15), (14, 16, 17), (14, 16, 19)):
        C.hang(t, x, y, z)
    # the counting-house tower has no floors between its foot and its top: light along its ladder
    for y in (16, 21, 26):
        t.set(9, y, 7, S('light', level=13))
    towers =[(cx - 3, WALK, cz - 3, cx + 3, WALK + 5, cz + 3) for (cx, cz) in ((4, 4), (84, 4), (4, 84), (84, 84))]
    towers += [(x0, WALK, z0, x1, WALK + 5, z1) for (x0, z0, x1, z1) in MID_TOWERS]
    C.light_rooms(t, [
        (29, F, 10, 39, 8, 21),      # cantina
        (30, 10, 11, 36, 11, 16),    # under its roof awning
        (6, F, 6, 21, 12, 21),       # counting-house, both floors
        (12, 14, 15, 16, 14, 21),    # under its roof awning
        (6, 9, 6, 10, 30, 10),       # its tower
        (56, F, 10, 59, 11, 21),     # shops, both floors
        (29, F, 6, 33, 6, 7),        # still house
        (55, F, 6, 59, 10, 7),       # lodging
        (72, F, 9, 80, 8, 17),       # water tower hall
        (67, F, 10, 69, 5, 16),      # cistern house
        (6, F, 29, 21, 11, 34),      # homes
        (9, F, 38, 21, 7, 50),       # medbay
        (8, F, 54, 21, 7, 59),       # workshop
        (68, F, 31, 81, 13, 57),     # hangar
        (29, F, 73, 59, 12, 82),     # audience hall and its wings
        (39, 13, 73, 49, 21, 83),    # drum and dome
        (6, F, 67, 21, 12, 82),      # south-west homes
        (75, F, 73, 82, 7, 82),      # scrap workshop
        (67, F, 78, 71, 7, 82),      # salvager's house
        (67, 4, 70, 71, 5, 76),      # the lean-to on the salvage heap
        (36, F, 0, 52, 16, 8),       # gate towers
        (37, F, 30, 39, 6, 30),      # customs
    ] + towers)
    # the streets, the walk and the roofs people climb to at night: a softer light than the rooms, never in a
    # pad's landing box
    C.light_rooms(t, [(IN0, F, IN0, IN1, F, 33), (IN0, F, 55, IN1, F, IN1), (IN0, F, 34, 33, F, 54),
                      (55, F, 34, IN1, F, 54), (41, F, 34, 47, F, 54), (34, F, 41, 54, F, 47),
                      (IN0 - 2, WALK, IN0 - 2, IN1 + 2, WALK, IN1 + 2),
                      (29, 10, 10, 39, 11, 21), (6, 14, 6, 21, 14, 21),     # the cantina's and counting-house's roofs
                      (36, 17, 0, 52, 20, 8)] +                             # the gate towers' tops
                     [(x0, WALK + 6, z0, x1, WALK + 9, z1) for (x0, _, z0, x1, _, z1) in towers], level=7)


def checks(t):
    for c in BAYS:
        C.check_pad(t, c)
    C.check_ladders(t)
    C.check_room(t, 'NPC_VESSBA', half=1, height=3)       # Vessba is 1.9 wide and 2 tall
    people_names = ['HOST', 'CANTINA', 'VENDOR_FENCE', 'NPC_VARRA', 'COUNTING_HOUSE_DOOR', 'NPC_DASK', 'NPC_VESSBA',
                    'NPC_TEEL', 'VENDOR_SHIPWRIGHT', 'VENDOR_SPACER', 'MEDBAY', 'HEART'] + \
        ['COUNTING_HOUSE_%d' % i for i in range(1, 9)] + ['ENFORCER_%d' % i for i in range(1, 7)] + \
        ['PAD_BAY_%d' % i for i in range(1, 5)]
    C.check_people(t, BAYS[0], people_names, block_anchors=('PAD_TERMINAL', 'CONTRACT_TERMINAL'))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--preview', help='also render a preview sheet to this PNG')
    args = ap.parse_args(argv)
    build(args.preview)


if __name__ == '__main__':
    main()
