"""The ship sites: ship/cradle (the hulk's field repair yard) and ship/beacon_pad (the home landing pad).

    python3 tools/assets/structures/qs2/ship_sites.py [cradle|beacon_pad ...] [--preview DIR]

ship/cradle, 33 x 18 x 37, buried 1 (docs/MILESTONE_6.md 5.3). Ships.placeHulk sets the Chapter Four
hulk down at HEART, nose to the south (+z), its tail ramp toward the entrance (north). The hulk's
model brings its own tarp and timber jacks, so the yard keeps the old template's whole 17 x 21 (x 8..24,
z 8..28) empty from the ground up: nothing a ship, a landing or a rider stepping off could meet, and
nothing over it (the repaired ship lifts straight up). Round that bay stands the field repair yard: a
lattice crane with an engine nacelle on its hook, a scaffold gallery along the port side, a canvas
workshop with the tool benches, a rack of power cells feeding a cable to the bay, stacks of hull
plate, crates and timber cribbing under tarps, fuel tanks, a salvaged nacelle on blocks, and a
floodlight mast at each corner of the bay.

ship/beacon_pad, 11 x 3 x 11, buried 1 (keep size). LandingBeaconBlock.layPad lays it over the area
the beacon checked: a dark deck of smooth basalt plates, a lighter touchdown plate at the centre, a
painted (terracotta) edge stripe, pale corner plates with a light over each, and a worn margin. Every
ground block is natural ground (#oldrepublic:story_ground, none of #oldrepublic:built_blocks) and the
layers above hold only invisible light blocks, so the area passes the beacon's check again when a
beacon is set there anew (TravelGameTests: the ground under the stand is stone, andesite or gravel).
"""
import json
import os
import sys

sys.dont_write_bytecode = True

from common import (Template, S, AIR, B, Noise, mod, meta, shapes, stairs, slab, fence, bars, log, lantern, chain,
                    light, check_anchor_spot, save, scene_sheets, preview_arg)

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', '..'))
TAGS = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'oldrepublic', 'tags', 'block')

# ---------------------------------------------------------------- the cradle

SX, SY, SZ = 33, 18, 37
BURIED = 1
HEART = (16, 1, 18)
BAY = (8, 1, 8, 24, SY - 1, 28)   # the old template's 17 x 21 round HEART: empty from the ground up
SEED = 6201

DECK = 'or:durasteel_deck'
PLATE = 'or:durasteel_plating'
PLATE_SLAB = 'or:durasteel_plating_slab'
PLATE_STAIRS = 'or:durasteel_plating_stairs'
RUST = 'or:rusted_plating'
VENT = 'or:durasteel_vent'
GRATE = 'or:durasteel_grate'
BEAM = 'or:durasteel_beam'
PIPES = 'or:durasteel_pipes'
LAMP = 'or:light_panel'
AMBER = 'or:light_panel_amber'
RED = 'or:light_panel_red'
CRIB = 'stripped_spruce_log'
POST = 'spruce_fence'


def beam(axis='y'):
    return S(BEAM, axis=axis)


def pipes(axis='y'):
    return S(PIPES, axis=axis)


def facing(name, f):
    return S(name, facing=f)


def in_bay(x, y, z):
    x0, y0, z0, x1, y1, z1 = BAY
    return x0 <= x <= x1 and z0 <= z <= z1 and y >= y0


def ground(t, seed):
    """The yard's ground: worn earth and gravel where the crew work and walk, the world's own ground
    showing through toward the rim; deck plates and sleepers under the hulk; a track in from the north."""
    n = Noise(seed + 1, 5.0)
    m = Noise(seed + 2, 1.8)
    work = [(8, 8, 24, 28, 1.0), (0, 0, 7, 6, 0.8), (25, 0, 32, 7, 0.7), (25, 13, 32, 21, 0.8), (5, 9, 7, 27, 0.8),
            (0, 8, 4, 28, 0.55), (8, 29, 24, 34, 0.6), (13, 0, 19, 7, 1.0)]
    for x in range(SX):
        for z in range(SZ):
            w = 0.3
            for (x0, z0, x1, z1, v) in work:
                if x0 <= x <= x1 and z0 <= z <= z1:
                    w = max(w, v)
            rim = min(x, z, SX - 1 - x, SZ - 1 - z)
            v = 0.6 * n.at(x, 0, z) + 0.4 * m.at(x, 0, z)
            score = 0.55 * w + 0.45 * v - (0.25 if rim == 0 else 0.12 if rim == 1 else 0.0)
            if score < 0.42:
                continue
            if v > 0.66:
                s = 'gravel'
            elif v > 0.5:
                s = 'coarse_dirt'
            elif v > 0.36:
                s = 'packed_mud'
            else:
                s = 'coarse_dirt' if m.at(x, 3, z) > 0.5 else 'dirt'
            t.set(x, 0, z, s)
    # the track in from the north, worn down the middle of the bay to the ramp
    for z in range(0, 13):
        for x in range(14, 19):
            if x in (14, 18) and n.at(x, 5, z) < 0.45:
                continue
            t.set(x, 0, z, 'dirt_path')
    # the cradle bed: deck plates under the hull, timber sleepers across them
    p = Noise(seed + 3, 2.2)
    for x in range(11, 22):
        for z in range(12, 25):
            edge = x in (11, 21) or z in (12, 24)
            if edge and p.at(x, 0, z) < 0.35:
                continue
            t.set(x, 0, z, DECK if p.at(x, 1, z) > 0.18 else RUST)
    for z in (13, 17, 21):
        for x in range(12, 21):
            t.set(x, 0, z, log(CRIB, 'x'))
    # oil and scorch on the bay floor round the bed
    for x in range(8, 25):
        for z in range(8, 29):
            if t.get(x, 0, z) and B.name_of(t.get(x, 0, z)) in ('minecraft:gravel', 'minecraft:packed_mud') \
                    and p.at(x, 4, z) > 0.78:
                t.set(x, 0, z, 'smooth_basalt')


def mast(t, x, z, toward, h=8):
    """A floodlight mast: a beam on a plated footing, a two-lamp head reaching toward the bay."""
    dx, dz = B.step(toward)
    t.set(x, 0, z, DECK)
    t.set(x, 1, z, PLATE)
    for y in range(2, h):
        t.set(x, y, z, beam())
    t.set(x, h, z, LAMP)
    t.set(x + dx, h, z + dz, LAMP)
    t.set(x, h + 1, z, slab(PLATE_SLAB))
    t.set(x + dx, h + 1, z + dz, slab(PLATE_SLAB))
    t.set(x + dx, h - 1, z + dz, stairs(PLATE_STAIRS, B.opposite(toward), 'top'))
    # the lamps' junction box at its foot, on the side away from the bay
    t.set(x - dx, 0, z - dz, DECK)
    t.set(x - dx, 1, z - dz, VENT)


def workshop(t):
    """The crew's workshop in the north-west corner: a canvas roof on posts over the tool benches,
    its back to the track, its open side to the bay."""
    x0, x1, z0, z1 = 0, 6, 0, 5
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, 'spruce_planks' if (x + 2 * z) % 7 else 'stripped_spruce_wood[axis=x]')
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (3, z0)):
        for y in range(1, 4):
            t.set(x, y, z, log('spruce_log'))
    # the roof: canvas pitched along x, ridge over z 2..3
    shapes.gable_roof(t, x0, z0, x1, z1, 4, 'or:canvas_stairs', body=None, slab=slab('or:canvas_slab'), ridge='x',
                      gable=None)
    # back wall of canvas against the north wind, open at the sides above the benches
    for x in range(x0 + 1, x1):
        if x != 3:
            for y in (1, 2, 3):
                t.set(x, y, z0, 'or:canvas')
    # the benches along the back wall
    t.set(1, 1, 1, 'crafting_table')
    t.set(2, 1, 1, 'smithing_table')
    t.set(4, 1, 1, S('chipped_anvil', facing='east'))
    t.set(5, 1, 1, S('grindstone', face='floor', facing='east'))
    t.set(1, 1, 2, facing('or:console', 'east'))
    t.set(1, 2, 2, facing('or:holo_screen', 'east'))
    t.set(1, 1, 3, 'stonecutter[facing=east]')
    t.set(5, 1, 4, S('barrel', facing='up', open=False))
    t.set(5, 2, 4, S('barrel', facing='up', open=False))
    t.set(5, 1, 3, 'or:cargo_crate')
    # a work lamp under the ridge
    t.set(3, 5, 2, chain())
    t.set(3, 4, 2, lantern(hanging=True))


def power(t):
    """Power cells in the north-east corner: a generator, a rack of cells (one spent), and a cable run
    to a junction box at the bay's edge."""
    # the generator: a vented housing with a console, an exhaust stack
    for x in range(28, 31):
        for z in (1, 2):
            t.set(x, 0, z, DECK)
            t.set(x, 1, z, VENT if (x + z) % 2 else PLATE)
            t.set(x, 2, z, PLATE_SLAB if x == 28 else PLATE)
    t.set(27, 1, 2, facing('or:console', 'west'))
    t.set(27, 0, 2, DECK)
    for y in range(3, 7):
        t.set(30, y, 1, pipes())
    t.set(30, 7, 1, S('or:durasteel_grate'))
    # the cells: plated cans with a lit charge band, on a deck pallet; the spent one shows red
    cells = [(26, 4, AMBER), (27, 4, AMBER), (28, 4, RED), (29, 4, AMBER), (26, 5, AMBER), (27, 5, None)]
    for (x, z, band) in cells:
        t.set(x, 0, z, DECK)
        if band is None:
            t.set(x, 1, z, slab(PLATE_SLAB))
            continue
        t.set(x, 1, z, pipes())
        t.set(x, 2, z, band)
        t.set(x, 3, z, slab(PLATE_SLAB))
    t.set(29, 1, 5, S('or:cargo_crate'))
    t.set(30, 1, 5, S('or:cargo_crate'))
    t.set(30, 2, 5, slab('or:canvas_slab'))
    # the cable: chain laid on the ground from the rack to the junction box by the bay
    for z in range(6, 11):
        t.set(26, 1, z, chain('z'))
    t.set(25, 1, 11, VENT)
    t.set(26, 1, 11, chain('x'))
    t.set(25, 2, 11, facing('or:console', 'west'))
    t.set(25, 3, 11, slab(PLATE_SLAB))


def crane(t):
    """The lattice crane east of the bay: a tower of beams with braced faces and a ladder inside, the
    operator's cab, a jib along the bay with its counterweight, and an engine nacelle on the hook over
    timber cribbing."""
    x0, x1, z0, z1 = 27, 29, 16, 18
    mid_x, mid_z = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(26, 31):
        for z in range(15, 20):
            t.set(x, 0, z, DECK)
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        t.set(x, 1, z, PLATE)
        for y in range(2, 12):
            t.set(x, y, z, beam())
    for y in range(1, 12):
        ring = y in (4, 8, 11)
        for (x, z, axis) in ((mid_x, z1, 'x'), (x0, mid_z, 'z'), (x1, mid_z, 'z')):
            if ring:
                t.set(x, y, z, beam(axis))
            elif y % 4 == 2:
                t.set(x, y, z, bars())
        # the north face's middle is a grated shaft the ladder is fixed to
        t.set(mid_x, y, z0, beam('x') if ring else GRATE)
        t.set(mid_x, y, mid_z, S('ladder', facing='south', waterlogged=False))
    # the cab: deck floor with a hatch over the ladder, glazed toward the bay, a lamp in its roof
    t.fill(x0, 12, z0, x1, 12, z1, DECK)
    t.set(mid_x, 12, mid_z, S('spruce_trapdoor', facing='south', half='top', open=False, powered=False,
                              waterlogged=False))
    for y in (13, 14):
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            t.set(x, y, z, PLATE)
        t.set(x0, y, mid_z, S('glass_pane'))
        t.set(mid_x, y, z0, S('glass_pane') if y == 13 else PLATE)
        t.set(mid_x, y, z1, S('glass_pane') if y == 13 else PLATE)
        t.set(x1, y, mid_z, facing('or:console', 'west') if y == 13 else VENT)
        t.set(mid_x, y, mid_z, AIR)
    t.fill(x0, 15, z0, x1, 15, z1, PLATE)
    t.set(mid_x, 15, mid_z, LAMP)
    # the jib along z over x 28, an A-frame over the cab, the counterweight hung under the north end
    for z in range(8, 33):
        t.set(mid_x, 16, z, beam('z'))
    t.set(mid_x, 17, mid_z, beam())
    t.set(mid_x, 17, mid_z - 1, stairs(PLATE_STAIRS, 'south'))
    t.set(mid_x, 17, mid_z + 1, stairs(PLATE_STAIRS, 'north'))
    for x in range(x0, x1 + 1):
        for z in (8, 9):
            for y in (14, 15):
                t.set(x, y, z, RUST if (x + y + z) % 3 == 0 else PLATE)
    t.set(mid_x, 15, 32, LAMP)
    # the trolley and hook, and the load: a sublight nacelle, vents aft, its cowling forward
    t.set(mid_x, 15, 26, VENT)
    for y in range(7, 15):
        t.set(mid_x, y, 26, chain())
    for x in (27, 28):
        for z in (25, 26, 27):
            for y in (5, 6):
                t.set(x, y, z, VENT if z == 27 else RUST if (x + y + z) % 4 == 0 else PLATE)
        t.set(x, 6, 24, stairs(PLATE_STAIRS, 'south'))
        t.set(x, 5, 24, stairs(PLATE_STAIRS, 'south', 'top'))
    # the cribbing waiting for it
    for x in (26, 27, 28, 29):
        t.set(x, 1, 25, log(CRIB, 'x'))
        t.set(x, 1, 27, log(CRIB, 'x'))
    for z in (25, 26, 27):
        t.set(27, 2, z, log(CRIB, 'z'))
        t.set(28, 2, z, log(CRIB, 'z'))
    for x in range(25, 31):
        for z in range(23, 30):
            if t.get(x, 0, z) is None:
                t.set(x, 0, z, 'gravel' if (x * 7 + z * 3) % 5 else 'coarse_dirt')


def scaffolds(t):
    """A scaffold gallery along the hull's port side: three towers and plank walks between them."""
    for z0 in (10, 17, 24):
        for x in (6, 7):
            for z in (z0, z0 + 1):
                for y in range(1, 7):
                    t.set(x, y, z, S('scaffolding', bottom=False, distance=0, waterlogged=False))
    for z in list(range(12, 17)) + list(range(19, 24)):
        t.set(6, 6, z, slab('spruce_slab', 'top'))
        t.set(7, 6, z, slab('spruce_slab', 'top'))
    # tools and a hanging lamp on the walk
    t.set(6, 7, 13, S('or:cargo_crate'))
    t.set(7, 7, 22, S('barrel', facing='up', open=False))
    t.set(6, 7, 15, lantern())
    t.set(6, 7, 20, lantern())
    # a tarp thrown over the middle tower against the rain
    for x in (6, 7):
        for z in (17, 18):
            t.set(x, 7, z, slab('or:canvas_slab'))
    t.set(5, 6, 17, stairs('or:canvas_stairs', 'east', 'top'))
    t.set(5, 6, 18, stairs('or:canvas_stairs', 'east', 'top'))


def stores(t):
    """The west yard: plate stacks under a tarp, crates, timber cribbing, a welding bench, fuel drums."""
    # a flat tarp on four poles over the plate stacks
    for (x, z) in ((0, 9), (4, 9), (0, 13), (4, 13)):
        for y in range(1, 4):
            t.set(x, y, z, fence(POST))
    for x in range(0, 5):
        for z in range(9, 14):
            t.set(x, 4, z, slab('or:canvas_slab'))
    stacks = {(1, 10): 3, (2, 10): 2, (3, 10): 1, (1, 11): 2, (2, 11): 3, (3, 11): 1, (1, 12): 1, (2, 12): 1}
    for (x, z), h in sorted(stacks.items()):
        for y in range(1, h + 1):
            t.set(x, y, z, RUST if (x + y + z) % 3 == 0 else PLATE)
        if h < 3:
            t.set(x, h + 1, z, slab(PLATE_SLAB))
    # crates under a draped tarp
    for x in (1, 2, 3):
        for z in (16, 17):
            t.set(x, 1, z, 'or:cargo_crate_republic' if (x, z) == (2, 16) else 'or:cargo_crate')
            if x != 3:
                t.set(x, 2, z, 'or:cargo_crate')
    for x in (1, 2):
        for z in (16, 17):
            t.set(x, 3, z, slab('or:canvas_slab'))
    t.set(3, 2, 16, stairs('or:canvas_stairs', 'west'))
    t.set(3, 2, 17, stairs('or:canvas_stairs', 'west'))
    t.set(0, 2, 16, stairs('or:canvas_stairs', 'east'))
    t.set(0, 2, 17, stairs('or:canvas_stairs', 'east'))
    # timber cribbing stacked crosswise
    for x in (1, 2, 3):
        t.set(x, 1, 20, log(CRIB, 'x'))
        t.set(x, 1, 22, log(CRIB, 'x'))
    for z in (20, 21, 22):
        t.set(1, 2, z, log(CRIB, 'z'))
        t.set(3, 2, z, log(CRIB, 'z'))
    t.set(2, 3, 21, log(CRIB, 'x'))
    # the welding bench: gas bottles, a vise, a lamp on a post
    t.set(2, 1, 25, slab(PLATE_SLAB, 'top'))
    t.set(3, 1, 25, slab(PLATE_SLAB, 'top'))
    t.set(4, 1, 25, S('anvil', facing='north'))
    for (x, z) in ((1, 25), (1, 26)):
        t.set(x, 1, z, pipes())
        t.set(x, 2, z, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    for y in (1, 2, 3):
        t.set(4, y, 27, fence(POST))
    t.set(4, 4, 27, lantern())
    # fuel drums
    for (x, y, z) in ((1, 1, 28), (2, 1, 28), (1, 2, 28), (1, 1, 29)):
        t.set(x, y, z, S('barrel', facing='up', open=False))


def south_yard(t):
    """South of the nose: a salvaged nacelle on blocks, two fuel tanks, crates."""
    # the nacelle lying on cribbing, its intake toward the bay
    for x in range(10, 16):
        for z in (32, 33):
            for y in (2, 3):
                t.set(x, y, z, VENT if x == 15 else GRATE if x == 10 else RUST if (x + y + z) % 3 == 0 else PLATE)
    for z in (32, 33):
        t.set(16, 2, z, stairs(PLATE_STAIRS, 'west', 'bottom'))
        t.set(16, 3, z, stairs(PLATE_STAIRS, 'west', 'top'))
    for x in (11, 14):
        for z in (31, 32, 33, 34):
            t.set(x, 1, z, log(CRIB, 'z'))
    t.set(12, 4, 32, pipes('x'))
    t.set(13, 4, 32, pipes('x'))
    t.set(9, 1, 31, S('or:debris'))
    t.set(12, 1, 34, S('or:debris'))
    # two fuel tanks on a deck, a pipe between them, a valve stem on each
    for x in range(18, 25):
        for z in range(31, 35):
            t.set(x, 0, z, DECK)
    for x0 in (19, 22):
        for x in (x0, x0 + 1):
            for z in (32, 33):
                for y in range(1, 5):
                    t.set(x, y, z, PLATE if y in (1, 4) else 'iron_block')
                t.set(x, 5, z, slab(PLATE_SLAB))
        t.set(x0, 6, 32, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(21, 2, 32, pipes('x'))
    t.set(21, 2, 33, pipes('x'))
    t.set(21, 1, 31, facing('or:console', 'north'))
    t.set(21, 0, 31, DECK)
    # crates in the south-west corner and a lamp post
    for (x, y, z, s) in ((2, 1, 32, 'or:cargo_crate'), (3, 1, 32, 'or:cargo_crate_imperial'), (2, 2, 32, 'or:cargo_crate'),
                         (2, 1, 33, 'or:cargo_crate'), (3, 1, 33, 'or:cargo_crate'), (4, 1, 34, 'or:cargo_crate_republic')):
        t.set(x, y, z, s)
    t.set(2, 3, 32, slab('or:canvas_slab'))
    for y in (1, 2, 3):
        t.set(6, y, 34, fence(POST))
    t.set(6, 4, 34, lantern())
    # a heap of stripped hull sections in the south-east corner
    n = Noise(SEED + 9, 1.6)
    for x in range(27, 32):
        for z in range(31, 36):
            h = int(n.at(x, 0, z) * 3.2 - (abs(x - 29) + abs(z - 33)) * 0.45)
            for y in range(1, h + 1):
                t.set(x, y, z, RUST if n.at(x, y, z) > 0.45 else PLATE)
            if h >= 0 and n.at(x, 9, z) > 0.55:
                t.set(x, max(1, h + 1), z, stairs(PLATE_STAIRS, ('north', 'east', 'south', 'west')[(x + z) % 4]))


def build_cradle():
    t = Template(SX, SY, SZ, seed=SEED, name='ship/cradle')
    ground(t, SEED)
    workshop(t)
    power(t)
    crane(t)
    scaffolds(t)
    stores(t)
    south_yard(t)
    for (x, z, toward) in ((7, 7, 'south'), (25, 7, 'south'), (7, 29, 'north'), (25, 29, 'north')):
        mast(t, x, z, toward)
    # The bay round the hulk is kept empty from the ground up.
    x0, y0, z0, x1, y1, z1 = BAY
    for (x, y, z), s in t.cells.items():
        if in_bay(x, y, z) and not B.name_of(s) in ('minecraft:air', 'minecraft:cave_air'):
            raise AssertionError('%s at %r is inside the hulk bay' % (s, (x, y, z)))
    t.fill(x0, y0, z0, x1, y1, z1, AIR)
    mod.spot(t, 'HEART', *HEART)
    mod.light_fill(t, (0, 1, 0, 6, 3, 5))
    check_anchor_spot(t, 'HEART')
    return t


def write_cradle_meta():
    meta.write('ship/cradle', buried=BURIED, max_slope=3, clearance=4, support_depth=4, ramp_rings=2, clear_above=4,
               terrain='flat', spawn_radius=64)


# ---------------------------------------------------------------- the beacon pad

PAD_SIZE = (11, 3, 11)
PAD_BURIED = 1
PAD_HEART = (5, 1, 5)
PAD_SEED = 6211
STRIPE = 'yellow_terracotta'
CORNER = 'calcite'
SCORCH = 'black_terracotta'


def build_beacon_pad():
    """The home pad: only ground blocks and four invisible lights."""
    sx, sy, sz = PAD_SIZE
    t = Template(sx, sy, sz, seed=PAD_SEED, name='ship/beacon_pad')
    cx, _, cz = PAD_HEART
    n = Noise(PAD_SEED + 1, 2.0)
    plates = Noise(PAD_SEED + 2, 3.0)
    burn = Noise(PAD_SEED + 3, 1.6)
    for x in range(sx):
        for z in range(sz):
            d = max(abs(x - cx), abs(z - cz))
            r = n.at(x, 0, z)
            if d == 5:
                s = 'coarse_dirt' if r > 0.55 else 'gravel' if r > 0.25 else 'andesite'
            elif d == 4:
                s = CORNER if abs(x - cx) == 4 and abs(z - cz) == 4 else STRIPE
                if s == STRIPE and r > 0.86:
                    s = 'gravel'          # the paint worn through where the ramp drags
            elif d <= 1:
                # the touchdown plate: tamped andesite, patched with stone
                s = 'andesite' if d == 0 or r > 0.35 else 'stone'
            else:
                # deck plates: smooth basalt, a few replaced with tuff
                s = 'tuff' if plates.at(x // 2, 0, z // 2) > 0.72 else 'smooth_basalt'
                # the engines' scorch, worst next to the touchdown plate
                if burn.at(x, 0, z) > (0.6 if d == 2 else 0.8):
                    s = SCORCH
            t.set(x, 0, z, s)
    for (x, z) in ((1, 1), (9, 1), (1, 9), (9, 9)):
        t.set(x, 1, z, light(14))
    mod.spot(t, 'HEART', *PAD_HEART)
    return t


def _tag_values(name, seen=None):
    """The block ids of one of the mod's block tags, nested mod tags expanded; vanilla tags as '#...'."""
    seen = seen or set()
    out = set()
    with open(os.path.join(TAGS, name + '.json')) as f:
        values = json.load(f)['values']
    for v in values:
        v = v['id'] if isinstance(v, dict) else v
        if v.startswith('#oldrepublic:'):
            sub = v.split(':', 1)[1]
            if sub not in seen:
                seen.add(sub)
                out |= _tag_values(sub, seen)
        else:
            out.add(v)
    return out


def check_pad(t):
    """Raise unless the pad passes the landing beacon's area check when laid: natural ground (not
    built) in the ground layer, nothing but air and invisible lights above it, sky over 90% of it,
    stone, andesite or gravel under the stand."""
    ground = _tag_values('story_ground')
    built = _tag_values('built_blocks')
    terracotta = '#minecraft:terracotta' in ground
    sx, sy, sz = t.size
    covered = 0
    for (x, y, z), s in t.cells.items():
        n = B.name_of(s)
        if y == 0:
            natural = n in ground or (terracotta and n.endswith('terracotta') and 'glazed' not in n)
            if not natural or n in built:
                raise AssertionError('%s at %r is not natural ground' % (n, (x, y, z)))
        elif n == 'minecraft:light':
            covered += 1
        elif n not in ('minecraft:air', 'minecraft:cave_air'):
            raise AssertionError('%s at %r stands above the pad' % (n, (x, y, z)))
    if covered / float(sx * sz) > 0.1:
        raise AssertionError('the lights cover %d of %d columns' % (covered, sx * sz))
    cx, cy, cz = PAD_HEART
    if B.name_of(t.get(cx, cy - 1, cz)) not in ('minecraft:stone', 'minecraft:andesite', 'minecraft:gravel'):
        raise AssertionError('the ground under the stand must be stone, andesite or gravel')
    check_anchor_spot(t, 'HEART')


def write_pad_meta():
    meta.write('ship/beacon_pad', buried=PAD_BURIED, max_slope=2, clearance=0, support_depth=8, ramp_rings=2,
               clear_above=6, terrain='flat', spawn_radius=64)


# ---------------------------------------------------------------- main

def main(argv):
    prev, which = preview_arg(argv)
    which = which or ['cradle', 'beacon_pad']
    for w in which:
        if w == 'cradle':
            t = build_cradle()
            out = save(t, 'ship/cradle', prev, cut=[2, 7, 14], plans=[1, 6])
            if prev:
                out.append(scene_sheets(t, prev, 'ship_cradle', BURIED - 1, margin=8, cuts=[3]))
            write_cradle_meta()
        elif w == 'beacon_pad':
            t = build_beacon_pad()
            check_pad(t)
            out = save(t, 'ship/beacon_pad', prev, cut=None, plans=[0])
            if prev:
                out.append(scene_sheets(t, prev, 'ship_beacon_pad', PAD_BURIED - 1, margin=6))
            write_pad_meta()
        else:
            raise SystemExit('unknown site %r (cradle, beacon_pad)' % w)
        print('ship/%s: %d cells %s' % (w, t.count(), ' '.join(out)))


if __name__ == '__main__':
    main(sys.argv[1:])
