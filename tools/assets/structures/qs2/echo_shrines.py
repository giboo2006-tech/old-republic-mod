"""The three Echo Shrines: site/echo_shrine_jedi, site/echo_shrine_sith, site/echo_shrine_wayfarer.

    python3 tools/assets/structures/qs2/echo_shrines.py [jedi|sith|wayfarer ...] [--preview DIR]

A half-buried shrine you descend into (docs/MILESTONE_6.md 5.3). The three share one layout and
differ in material, light and dressing:

  * a broad stair drops from the ground into a sunken forecourt, between two guardian statues;
  * the gate front (two pylons, a lintel and a pediment) stands in the forecourt's south wall, its
    doorway closed by veiled stone (found by Sense, opened by Push or a lit saber, as in M3);
  * behind it a gallery looks down into the buried nave: twin stairs run down the side aisles, a dark
    undercroft lies under the gallery, and the nave's roof (one bay fallen in) rises out of the ground;
  * at the far end the M3 counterweight puzzle, cell for cell: Push the counterweight in the apse wall
    and the seal in front of the pedestal opens;
  * a spire over the apse marks the place from far away.

Jedi: pale ruin stone, moss and roots, the cold blue of kyber. Sith: black tomb stone, chains and
bone, red light from below (lava behind bars). Wayfarer: weathered tuff built round an older ruin
core, cairns and lanterns, and the scavengers' camp in the forecourt (their GUARD spawner is there:
they could never open the veiled door).

Template: 27 x 24 x 27, buried 9 (the ground layer is y 8, the surface feet layer y 9).
Anchors: HEART (nave floor), PEDESTAL, COUNTERWEIGHT, GUARD.
"""
import sys

sys.dont_write_bytecode = True

from common import (Template, S, AIR, B, Noise, Palette, shapes, nature, mod, meta, stairs, slab, wall, fence,
                    bars, log, trapdoor, lantern, chain, candle, campfire, kyber, vine, lichen, skull, wall_skull,
                    light, pot, place_bed, place_mechanism, verify_mechanism, check_anchor_spot, dark_floors, save,
                    scene_sheets, preview_arg, grow_vines, open_cell)

SX, SY, SZ = 27, 24, 27
BURIED = 9
G = BURIED - 1          # the ground layer
F = BURIED              # the surface feet layer
CX = 13                 # the axis
C = (12, 3, 23)         # the counterweight (the M3 circuit hangs off it)
HEART = (13, 1, 19)
GUARD_HALL = (13, 1, 17)
GUARD_CAMP = (8, 5, 8)
SEEDS = {'jedi': 6101, 'sith': 6102, 'wayfarer': 6103}

# Layout (x east, z south). The hall shell is x 4..22, z 10..26, y 0..8.
NAVE_X = (10, 16)
AISLES = ((6, 8), (18, 20))
INNER = (9, 17)
PIT_X, PIT_Z = (6, 20), (6, 9)
STAIR_X = (10, 16)
GALLERY_Z = (13, 15)
AISLE_STAIR_Z = (16, 19)
APSE_Z = 23
SPIRE = (13, 25)
ROOF_HOLE_Z = (18, 20)
WINDOW_Z = (16, 17, 19, 20)   # the clerestory windows (y 9..10 in x 9 and 17)
INTERIOR = (4, 1, 10, 22, G, 26)  # the light check and fill region: the gate passage and the hall


class Style:
    """A shrine kind's materials."""

    def __init__(self, kind, skin, guard_group, guard_count, wall, floor, trim, pillar, stair, slab_, wall_top,
                 junction, plain, rubble, worn, core=None):
        self.kind = kind
        self.skin = skin
        self.guard_group = guard_group
        self.guard_count = guard_count
        self.pal = Palette(wall=wall, floor=floor, rubble=rubble, worn=worn)
        self.trim = trim
        self.pillar = pillar
        self.stair = stair
        self.slab = slab_
        self.wall_top = wall_top
        self.junction = junction
        self.plain = plain
        # the older stone round the veiled door and the puzzle (it matches the puzzle blocks' skin)
        self.core = core or Palette(wall=wall, trim=trim, pillar=pillar, stair=stair)


RUIN_CORE = Palette(wall=[('or:ruin_stone', 4), ('or:ruin_stone_cracked', 1), ('or:ruin_stone_mossy', 1)],
                    trim='or:ruin_stone_carved', pillar='or:ruin_pillar[axis=y]', stair='or:ruin_brick_stairs')

STYLES = {
    'jedi': Style(
        'jedi', 'ruin', 'watcher', 0,
        wall=[('or:ruin_bricks', 6), ('or:ruin_stone', 3), ('or:ruin_stone_cracked', 1), ('or:ruin_stone_mossy', 1)],
        floor=[('or:ruin_stone', 5), ('or:ruin_bricks', 2), ('or:ruin_stone_cracked', 1), ('calcite', 1)],
        trim='or:ruin_stone_carved', pillar='or:ruin_pillar[axis=y]', stair='or:ruin_brick_stairs',
        slab_='or:ruin_brick_slab', wall_top='or:ruin_brick_wall', junction='or:ruin_stone_carved',
        plain='or:ruin_stone',
        rubble=[('or:ruin_brick_slab', 3), ('gravel', 2), ('or:ruin_stone', 1), ('mossy_cobblestone', 1)],
        worn=[('coarse_dirt', 3), ('moss_block', 2), ('rooted_dirt', 1), ('gravel', 1)],
        core=RUIN_CORE),
    'sith': Style(
        'sith', 'tomb', 'tomb', 0,
        wall=[('or:tomb_bricks', 6), ('or:tomb_stone', 3), ('or:tomb_stone_cracked', 1),
              ('polished_blackstone_bricks', 1)],
        floor=[('or:tomb_stone', 5), ('or:tomb_bricks', 2), ('polished_blackstone', 1), ('or:tomb_stone_cracked', 1)],
        trim='or:tomb_stone_carved', pillar='or:tomb_pillar[axis=y]', stair='or:tomb_brick_stairs',
        slab_='or:tomb_brick_slab', wall_top='or:tomb_brick_wall', junction='or:tomb_stone_carved',
        plain='or:tomb_stone',
        rubble=[('or:tomb_brick_slab', 3), ('blackstone', 2), ('basalt[axis=y]', 1), ('gravel', 1)],
        worn=[('coarse_dirt', 3), ('gravel', 2), ('blackstone', 1), ('smooth_basalt', 1)],
        core=Palette(wall=[('or:tomb_stone', 4), ('or:tomb_stone_cracked', 1), ('smooth_basalt', 1)],
                     trim='or:tomb_stone_carved', pillar='or:tomb_pillar[axis=y]', stair='or:tomb_brick_stairs')),
    'wayfarer': Style(
        'wayfarer', 'ruin', 'scavengers', 2,
        wall=[('tuff_bricks', 5), ('tuff', 2), ('polished_tuff', 1), ('mossy_stone_bricks', 1),
              ('cracked_stone_bricks', 1)],
        floor=[('polished_tuff', 4), ('tuff_bricks', 2), ('mossy_cobblestone', 1), ('cobblestone', 1)],
        trim='chiseled_tuff_bricks', pillar='chiseled_tuff', stair='tuff_brick_stairs',
        slab_='tuff_brick_slab', wall_top='tuff_brick_wall', junction='or:ruin_stone_carved',
        plain='or:ruin_stone',
        rubble=[('tuff_brick_slab', 3), ('cobblestone', 2), ('gravel', 2), ('mossy_cobblestone', 1)],
        worn=[('coarse_dirt', 3), ('gravel', 2), ('dirt_path', 1), ('packed_mud', 1)],
        core=RUIN_CORE),
}


# ---------------------------------------------------------------- helpers

def patches(st, role, seed, scale=4.0):
    return st.pal.patches(role, seed=seed, scale=scale)


def box(t, x0, y0, z0, x1, y1, z1, state):
    t.fill(x0, y0, z0, x1, y1, z1, state)


def air(t, x0, y0, z0, x1, y1, z1):
    t.fill(x0, y0, z0, x1, y1, z1, AIR)


def put(t, x, y, z, state, over_air_only=False):
    if over_air_only and not t.is_air(x, y, z):
        return
    t.set(x, y, z, state)


# ---------------------------------------------------------------- the buried shell and the forecourt

def shell(t, st, seed):
    wallp = patches(st, 'wall', seed + 1)
    # The hall's buried block, the forecourt's retaining walls and the terraces beside the stair.
    box(t, 4, 0, 10, 22, G, 26, wallp)
    box(t, 4, 3, 0, 5, G, 9, wallp)
    box(t, 21, 3, 0, 22, G, 9, wallp)
    box(t, 6, 3, 0, 9, G, 5, wallp)
    box(t, 17, 3, 0, 20, G, 5, wallp)
    # The forecourt: floor at y 4, open to the sky.
    box(t, PIT_X[0], 3, PIT_Z[0], PIT_X[1], 3, PIT_Z[1], wallp)
    box(t, PIT_X[0], 4, PIT_Z[0], PIT_X[1], 4, PIT_Z[1], patches(st, 'floor', seed + 2, 3.0))
    air(t, PIT_X[0], 5, PIT_Z[0], PIT_X[1], G, PIT_Z[1])
    # A plinth course along the foot of the forecourt walls.
    for z in range(PIT_Z[0], PIT_Z[1] + 1):
        t.set(5, 4, z, st.trim)
        t.set(21, 4, z, st.trim)
    # The broad stair down from the north: y 8 at z 2 to y 5 at z 5, on solid fill.
    for i, z in enumerate(range(2, 6)):
        y = G - i
        box(t, STAIR_X[0], 3, z, STAIR_X[1], y - 1, z, wallp)
        box(t, STAIR_X[0], y, z, STAIR_X[1], y, z, stairs(st.stair, 'north'))
        if y + 1 <= G:
            air(t, STAIR_X[0], y + 1, z, STAIR_X[1], G, z)
    # The stair's cheeks: carved stones stepping down beside the treads.
    for i, z in enumerate(range(2, 6)):
        for x in (STAIR_X[0] - 1, STAIR_X[1] + 1):
            t.set(x, G - i, z, st.trim)
    # The stair head and the apron: paving in the ground layer.
    box(t, STAIR_X[0] - 1, G, 0, STAIR_X[1] + 1, G, 1, patches(st, 'floor', seed + 3, 3.0))
    # Coping round the forecourt's rim, broken in places.
    rim = [(5, z) for z in range(0, PIT_Z[1] + 1)] + [(21, z) for z in range(0, PIT_Z[1] + 1)]
    rim += [(x, 5) for x in range(6, 10)] + [(x, 5) for x in range(17, 21)]
    rim += [(9, z) for z in range(0, 5)] + [(17, z) for z in range(0, 5)]
    n = Noise(seed + 7, 2.5)
    for (x, z) in rim:
        if n.at(x, 0, z) < 0.72:
            t.set(x, F, z, wall(st.wall_top))
        elif n.at(x, 3, z) < 0.5:
            t.set(x, F, z, slab(st.slab))
    for (x, z) in ((5, 0), (21, 0), (9, 0), (17, 0), (5, PIT_Z[1]), (21, PIT_Z[1])):
        t.set(x, F, z, st.trim)
        t.set(x, F + 1, z, slab(st.slab))


def hall(t, st, seed):
    floorp = patches(st, 'floor', seed + 4, 3.0)
    core = st.core
    # Gate: the doorway through the 3-thick front (veiled infill in the middle layer), in the old core stone.
    for x in range(11, 16):
        for y in range(4, G + 1):
            for z in range(10, 13):
                t.set(x, y, z, core.pick('wall', t.rng))
    air(t, 12, 5, 10, 14, 8, 12)
    for x in range(12, 15):
        for y in range(5, 9):
            t.set(x, y, 11, S('or:veiled_stone', age=0, skin=st.skin, veil='veiled'))
    # Gallery behind the gate: floor y 4 across the whole hall, 3 deep.
    box(t, 6, 4, GALLERY_Z[0], 20, 4, GALLERY_Z[1], floorp)
    air(t, 6, 5, GALLERY_Z[0], 20, 7, GALLERY_Z[1])
    # Undercroft under the gallery's middle, open to the nave between two piers.
    air(t, NAVE_X[0], 1, GALLERY_Z[0], NAVE_X[1], 3, GALLERY_Z[1])
    box(t, NAVE_X[0], 0, GALLERY_Z[0], NAVE_X[1], 0, 22, floorp)
    for x in (NAVE_X[0], NAVE_X[1]):
        shapes.pillar(t, x, 1, 3, GALLERY_Z[1], st.pillar, base=st.trim, capital=st.trim)
    # Nave: floor y 0, open to the roof (y 11), 7 wide, from the undercroft's mouth to the apse.
    air(t, NAVE_X[0], 1, GALLERY_Z[1] + 1, NAVE_X[1], 11, 22)
    air(t, NAVE_X[0], 5, GALLERY_Z[0], NAVE_X[1], 11, GALLERY_Z[1])
    # The gallery's rail over the nave.
    for x in range(NAVE_X[0] + 1, NAVE_X[1]):
        t.set(x, 5, GALLERY_Z[1], wall(st.wall_top))
    for x in (NAVE_X[0], NAVE_X[1]):
        t.set(x, 5, GALLERY_Z[1], st.trim)
        t.set(x, 6, GALLERY_Z[1], slab(st.slab))
    # Aisles: twin stairs from the gallery's ends down to the aisle floor, ceiling y 8.
    for (xa, xb) in AISLES:
        for i, z in enumerate(range(AISLE_STAIR_Z[0], AISLE_STAIR_Z[1] + 1)):
            y = 4 - i
            box(t, xa, y, z, xb, y, z, stairs(st.stair, 'north'))
            air(t, xa, y + 1, z, xb, 7, z)
        box(t, xa, 0, AISLE_STAIR_Z[1] + 1, xb, 0, 22, floorp)
        air(t, xa, 1, AISLE_STAIR_Z[1] + 1, xb, 7, 22)
    # The inner walls between aisle and nave: openings at the gallery, windows over the stairs and an
    # arch at the floor.
    for x in INNER:
        air(t, x, 5, GALLERY_Z[0], x, 7, GALLERY_Z[0] + 1)
        air(t, x, 5, 17, x, 6, 18)
        t.set(x, 7, 17, stairs(st.stair, 'south', 'top'))
        t.set(x, 7, 18, stairs(st.stair, 'north', 'top'))
        air(t, x, 1, 20, x, 4, 21)
        t.set(x, 5, 20, stairs(st.stair, 'south', 'top'))
        t.set(x, 5, 21, stairs(st.stair, 'north', 'top'))
    # Pilasters standing proud of the nave walls, with corbels under the roof.
    for z in (16, 22):
        for x, face in ((NAVE_X[0], 'east'), (NAVE_X[1], 'west')):
            shapes.pillar(t, x, 1, 10, z, st.pillar, base=st.trim, capital=st.trim)
            t.set(x, 11, z, stairs(st.stair, B.opposite(face), 'top'))


def roof_and_clerestory(t, st, seed):
    wallp = patches(st, 'wall', seed + 5)
    # Clerestory walls rise out of the ground over the inner walls; windows above the ground.
    for x in INNER:
        box(t, x, G, GALLERY_Z[0] - 1, x, 11, APSE_Z, wallp)
        t.fill(x, G, GALLERY_Z[0] - 1, x, G, APSE_Z, st.trim)
        for z0 in WINDOW_Z[::2]:
            air(t, x, 9, z0, x, 10, z0 + 1)
            t.set(x, 11, z0, st.trim)
            t.set(x, 11, z0 + 1, st.trim)
        for z in (15, 18, 21):
            # buttresses on the outside, stepping down to the aisle roof
            bx = x - 1 if x == INNER[0] else x + 1
            t.set(bx, F, z, st.trim)
            t.set(bx, F + 1, z, slab(st.slab))
    # The apse wall carries on up to the roof.
    box(t, NAVE_X[0], G, APSE_Z, NAVE_X[1], 11, APSE_Z, wallp)
    # Gable roof, ridge along the nave, gable ends over the gate and the apse.
    shapes.gable_roof(t, INNER[0], 12, INNER[1], APSE_Z, 12, st.stair, body=None, slab=slab(st.slab),
                      ridge='z', gable=wallp)
    # The inside of the roof is open to the nave.
    for y in range(12, 16):
        for x in range(INNER[0] + 1 + (y - 12), INNER[1] - (y - 12)):
            for z in range(13, APSE_Z):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)


def roof_fall(t, st, seed):
    """One bay of the roof has fallen in: a ragged hole over z 18..20, its stones on the nave floor."""
    z0, z1 = ROOF_HOLE_Z
    n = Noise(seed + 11, 1.7)
    for z in range(z0 - 1, z1 + 2):
        for x in range(INNER[0], INNER[1] + 1):
            for y in range(12, 17):
                s = t.get(x, y, z)
                if s is None or t.is_air(x, y, z):
                    continue
                inner = z0 <= z <= z1 and INNER[0] + 1 <= x <= INNER[1] - 1
                edge = (z in (z0 - 1, z1 + 1) or x in (INNER[0], INNER[1])) and n.at(x, y, z) > 0.62
                if inner or edge:
                    t.set(x, y, z, AIR)
    # the fallen stones lie under the hole, against the walls (the middle of the nave stays clear)
    rub = patches(st, 'rubble', seed + 12, 2.0)
    for (x, z, h) in ((10, 18, 2), (11, 18, 1), (10, 19, 1), (16, 19, 2), (15, 20, 1), (16, 20, 1), (11, 20, 1)):
        for y in range(1, h + 1):
            t.set(x, y, z, st.pal.pick('wall', t.rng) if y < h else rub(x, y, z))
    t.set(15, 1, 18, stairs(st.stair, 'west'))
    t.set(11, 1, 21, slab(st.slab))
    # a broken rafter end still hangs on
    t.set(12, 12, z0, stairs(st.stair, 'east', 'top'))


def apse(t, st, seed):
    """The nave's end wall: the seal and the counterweight in a carved frame of the old core stone, a
    deep niche over the frame (each kind fills it), and trim bands that break up the wall."""
    z = APSE_Z
    core = st.core
    for x in (11, 15):
        shapes.pillar(t, x, 1, 4, z, core['pillar'], base=core['trim'], capital=core['trim'])
    for x in range(12, 15):
        for y in range(1, 5):
            t.set(x, y, z, core.pick('wall', t.rng))
    t.fill(11, 5, z, 15, 5, z, core['trim'])
    # the niche: 3 wide, 3 tall, 2 deep, its top corners eased with upside-down stairs
    air(t, 12, 6, z, 14, 8, z + 1)
    t.set(12, 8, z, stairs(core['stair'], 'west', 'top'))
    t.set(14, 8, z, stairs(core['stair'], 'east', 'top'))
    t.fill(11, 9, z, 15, 9, z, core['trim'])
    t.fill(10, 10, z, 16, 10, z, st.trim)
    back = {'jedi': 'or:crystal_veined_stone', 'sith': 'crying_obsidian', 'wayfarer': 'or:ruin_stone'}[st.kind]
    t.fill(12, 6, z + 2, 14, 8, z + 2, back)
    if st.kind == 'jedi':
        # a growth of kyber in the niche, the light the whole nave is lit by
        t.set(13, 6, z + 1, kyber('up'))
        t.set(12, 7, z + 1, kyber('east'))
        t.set(14, 7, z + 1, kyber('west'))
        t.set(13, 8, z + 1, kyber('down'))
        t.set(12, 6, z, kyber('up'))
        t.set(14, 6, z, kyber('up'))
    elif st.kind == 'sith':
        # a mask on a black stand over embers, red candles either side
        t.set(12, 5, z + 1, 'magma_block')
        t.set(14, 5, z + 1, 'magma_block')
        t.set(13, 6, z + 1, st.trim)
        t.set(13, 7, z + 1, skull(8, wither=True))
        t.set(12, 6, z + 1, candle('red', 3, True))
        t.set(14, 6, z + 1, candle('red', 2, True))
        t.set(13, 6, z, slab(st.slab))
    else:
        # travellers' offerings: a lantern, candles and a potted fern on the old stone
        t.set(13, 6, z + 1, lantern())
        t.set(12, 6, z + 1, candle('white', 3, True))
        t.set(14, 6, z + 1, S('potted_fern'))
        t.set(12, 6, z, candle('yellow', 1, True))
        t.set(14, 6, z, S('potted_dandelion'))


def gate_front(t, st, seed):
    wallp = patches(st, 'wall', seed + 6)
    core = st.core
    # Pylons either side of the door, standing out one block into the forecourt.
    for (xa, xb, top) in ((8, 10, 15), (16, 18, 15)):
        box(t, xa, 4, 10, xb, top, 12, wallp)
        box(t, xa, 5, 10, xb, 5, 12, st.trim)
        box(t, xa, G + 1, 10, xb, G + 1, 12, st.trim)
        box(t, xa, 13, 10, xb, 13, 12, st.trim)
        # a tall niche in the face, a shallow panel above the ground line
        mid = (xa + xb) // 2
        air(t, mid, 6, 10, mid, 8, 10)
        t.set(mid, 8, 10, stairs(st.stair, 'south', 'top'))
        air(t, mid, 10, 10, mid, 12, 10)
        t.set(mid, 12, 10, slab(st.slab, 'top'))
        # cornice: upside-down stairs all round, then the cap and a finial
        for x in range(xa - 1, xb + 2):
            for z in range(9, 14):
                if xa <= x <= xb and 10 <= z <= 12:
                    continue
                face = 'south' if z == 9 else 'north' if z == 13 else 'east' if x == xa - 1 else 'west'
                if x in (xa - 1, xb + 1) and z in (9, 13):
                    continue
                t.set(x, 14, z, stairs(st.stair, face, 'top'))
        box(t, xa, 15, 10, xb, 15, 12, st.trim)
        t.set(mid, 16, 11, st.pillar)
        t.set(mid, 17, 11, slab(st.slab))
    # Jambs, lintel, attic and cornice over the doorway (the old core stone).
    for x in (11, 15):
        shapes.pillar(t, x, 5, 8, 10, core['pillar'], base=core['trim'], capital=core['trim'])
    box(t, 11, G + 1, 10, 15, G + 1, 12, core['trim'])
    box(t, 11, G + 2, 11, 15, 11, 12, wallp)
    for x in range(11, 16):
        t.set(x, 12, 11, stairs(st.stair, 'north'))
    t.set(13, 10, 10, stairs(st.stair, 'north'))
    t.set(12, 10, 10, stairs(st.stair, 'east'))
    t.set(14, 10, 10, stairs(st.stair, 'west'))
    # The wings of the gate front: a low parapet over the forecourt's south wall.
    for x in list(range(4, 8)) + list(range(19, 23)):
        t.set(x, G + 1, 10, wall(st.wall_top))


def pylon_ruin(t, st, seed):
    """The east pylon's top has broken off and fallen into the forecourt's east corner."""
    n = Noise(seed + 21, 1.6)
    for y in range(11, 18):
        for x in range(15, 20):
            for z in range(9, 14):
                if t.get(x, y, z) is None:
                    continue
                # a ragged diagonal break: high by the door, low on the outer side
                cut = 15.2 - (x - 16) * 1.4 + 2.4 * (n.at(x, 0, z) - 0.5)
                if y > cut:
                    t.set(x, y, z, None)
    rub = patches(st, 'rubble', seed + 22, 2.0)
    heap = [(18, 9, 2), (19, 9, 2), (20, 9, 1), (19, 8, 1), (20, 8, 1), (17, 9, 1), (20, 7, 1), (16, 9, 1)]
    for (x, z, h) in heap:
        for k in range(h):
            y = 5 + k
            t.set(x, y, z, st.pal.pick('wall', t.rng) if k < h - 1 else rub(x, y, z))
    # the cornice block that fell, lying on its side
    t.set(18, 5, 8, stairs(st.stair, 'north', 'top'))
    t.set(17, 5, 8, st.trim)


# ---------------------------------------------------------------- statues

def statue(t, st, cx, cz, broken=False):
    """A guardian on a 3 x 3 plinth on the terrace beside the stair, facing north, 6 blocks tall."""
    kind = st.kind
    body = st.plain if kind != 'wayfarer' else 'polished_tuff'
    box(t, cx - 1, F, cz - 1, cx + 1, F, cz + 1, st.trim)
    for (dx, dz, f) in ((-1, 0, 'east'), (1, 0, 'west'), (0, 1, 'north')):
        t.set(cx + dx, F + 1, cz + dz, stairs(st.stair, f))
    t.set(cx, F + 1, cz, body)
    t.set(cx, F + 2, cz, body)
    # arms hang from the shoulders beside the body (so it reads as a figure, not a cross)
    t.set(cx - 1, F + 2, cz, S(st.wall_top, north='none', east='low', south='none', west='none', up=True,
                               waterlogged=False))
    t.set(cx + 1, F + 2, cz, S(st.wall_top, north='none', east='none', south='none', west='low', up=True,
                               waterlogged=False))
    t.set(cx - 1, F + 3, cz, stairs(st.stair, 'east', 'top'))
    t.set(cx + 1, F + 3, cz, stairs(st.stair, 'west', 'top'))
    t.set(cx, F + 3, cz, body)
    t.set(cx, F + 4, cz, st.trim)
    t.set(cx, F + 5, cz, slab(st.slab))
    if kind == 'jedi':
        # hands on a hilt, the blade planted point-down in front
        t.set(cx, F + 2, cz - 1, stairs(st.stair, 'north', 'top'))
        t.set(cx, F + 1, cz - 1, S('end_rod', facing='down'))
    elif kind == 'sith':
        # arms folded, hood forward; embers smoulder at its feet
        t.set(cx, F + 2, cz - 1, stairs(st.stair, 'south', 'top'))
        t.set(cx, F + 4, cz - 1, slab(st.slab, 'top'))
        t.set(cx - 1, F, cz - 1, 'magma_block')
        t.set(cx + 1, F, cz - 1, 'magma_block')
    else:
        # a traveller with a staff and a lantern hung from it
        t.set(cx + 1, F + 1, cz - 1, fence('spruce_fence'))
        t.set(cx + 1, F + 2, cz - 1, fence('spruce_fence'))
        t.set(cx + 1, F + 3, cz - 1, fence('spruce_fence'))
        t.set(cx + 1, F + 4, cz - 1, fence('spruce_fence'))
        t.set(cx + 2, F + 4, cz - 1, fence('spruce_fence'))
        t.set(cx + 2, F + 3, cz - 1, lantern(hanging=True))
        t.set(cx, F + 2, cz + 1, S('or:canvas'))
    if broken:
        # the head and shoulders have fallen; they lie on the terrace beside the plinth
        for y in (F + 3, F + 4, F + 5):
            for dx in (-1, 0, 1):
                t.set(cx + dx, y, cz, None)
        t.set(cx, F + 4, cz - 1, None)
        t.set(cx, F + 3, cz, stairs(st.stair, 'north', 'top'))
        t.set(cx + 2, F, cz + 2, st.trim)
        t.set(cx + 2, F, cz + 1, stairs(st.stair, 'west'))
        t.set(cx - 2, F, cz + 2, slab(st.slab))


# ---------------------------------------------------------------- spires

def spire(t, st, seed):
    sx, sz = SPIRE
    wallp = patches(st, 'wall', seed + 31)
    # A stepped plinth behind the apse gable, over the back of the hall.
    box(t, sx - 3, F, sz - 1, sx + 3, F, sz + 1, st.trim)
    box(t, sx - 2, F + 1, sz - 1, sx + 2, F + 2, sz + 1, wallp)
    box(t, sx - 2, F + 3, sz - 1, sx + 2, F + 3, sz + 1, st.trim)
    if st.kind == 'jedi':
        # an obelisk with chamfered corners and a pyramidion, crowned with kyber
        box(t, sx - 1, F + 4, sz - 1, sx + 1, F + 5, sz + 1, st.trim)
        for y in range(F + 6, F + 12):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    corner = dx != 0 and dz != 0
                    t.set(sx + dx, y, sz + dz, wall(st.wall_top) if corner else wallp(sx + dx, y, sz + dz))
        box(t, sx - 1, F + 12, sz - 1, sx + 1, F + 12, sz + 1, st.trim)
        shapes.hip_roof(t, sx - 1, sz - 1, sx + 1, sz + 1, F + 13, st.stair)
        t.set(sx, F + 13, sz, 'calcite')
        t.set(sx, F + 14, sz, kyber('up'))
        for (dx, dz, f) in ((-2, 0, 'west'), (2, 0, 'east'), (0, -2, 'north')):
            t.set(sx + dx, F + 12, sz + dz, kyber(f))
    elif st.kind == 'sith':
        # a black fang between two horns that curl in toward it, a claw raised out of the ground, red
        # light at its foot
        cp = Palette(c=[('polished_blackstone_bricks', 3), ('or:tomb_bricks', 2), ('blackstone', 1)])
        for y in range(F + 4, F + 7):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    t.set(sx + dx, y, sz + dz, cp.pick('c', t.rng))
        for x in range(sx - 1, sx + 2):
            t.set(x, F + 5, sz - 1, 'crying_obsidian')

        def bas(y):
            return 'polished_basalt[axis=y]' if y % 3 else 'basalt[axis=y]'
        tip = wall('polished_blackstone_wall')
        for y in range(F + 7, F + 14):
            t.set(sx, y, sz, bas(y))
        t.set(sx, F + 14, sz, tip)
        for dz in (-1, 1):
            t.set(sx, F + 7, sz + dz, bas(F + 7))
            t.set(sx, F + 8, sz + dz, tip)
        for side in (-1, 1):
            for y in range(F + 4, F + 10):
                t.set(sx + 2 * side, y, sz, bas(y))
            for y in range(F + 10, F + 12):
                t.set(sx + side, y, sz, bas(y))
            t.set(sx + side, F + 12, sz, tip)
        for (x, z) in ((sx - 3, sz - 1), (sx + 3, sz - 1), (sx - 3, sz + 1), (sx + 3, sz + 1)):
            t.set(x, F, z, 'magma_block')
    else:
        # a waymarker cairn: stones stacked by many hands, a lantern on top for the road
        stones = [('cobblestone', 3), ('mossy_cobblestone', 2), ('tuff', 2), ('andesite', 1), ('stone', 1)]
        pal = Palette(s=stones)
        n = Noise(seed + 33, 2.0)
        for i, y in enumerate(range(F + 4, F + 11)):
            r = 1.6 - i * 0.15
            ox = round((n.at(0, y, 0) - 0.5) * 1.2)
            oz = round((n.at(5, y, 5) - 0.5) * 1.2)
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if dx * dx + dz * dz <= r * r:
                        t.set(sx + dx + (ox if i > 3 else 0), y, sz + dz + (oz if i > 4 else 0), pal.pick('s', t.rng))
        top = F + 11
        t.set(sx, top, sz, 'cobblestone')
        t.set(sx, top + 1, sz, fence('spruce_fence'))
        t.set(sx, top + 2, sz, fence('spruce_fence'))
        t.set(sx, top + 3, sz, lantern())
        for dx in (-1, 1):
            t.set(sx + dx, top + 2, sz, fence('spruce_fence'))
            t.set(sx + dx, top + 1, sz, lantern(hanging=True))


# ---------------------------------------------------------------- the surface over the buried hall

def surface(t, st, seed):
    """The hall's roof slabs show in the ground as a terrace, soil drifted over its edges and against the
    clerestory; worn ground in the strips of natural ground either side."""
    n = Noise(seed + 41, 4.0)
    m = Noise(seed + 44, 1.8)
    floorp = patches(st, 'floor', seed + 42, 3.0)
    soil = {'jedi': ('grass_block', 'moss_block', 'rooted_dirt'),
            'sith': ('coarse_dirt', 'gravel', 'coarse_dirt'),
            'wayfarer': ('grass_block', 'coarse_dirt', 'grass_block')}[st.kind]
    for x in range(4, 23):
        for z in range(10, 27):
            if INNER[0] <= x <= INNER[1] and z <= APSE_Z:
                continue
            if t.get(x, F, z) is not None:
                continue
            edge = 1.0 if x in (4, 22) or z == 26 else 0.6 if x in (5, 8, 18, 21) or z == 25 else 0.0
            v = 0.7 * n.at(x, 0, z) + 0.3 * m.at(x, 0, z) + 0.22 * edge
            if v > 0.62:
                s = soil[0] if v > 0.7 else soil[1 + int(m.at(x, 7, z) * 2) % 2]
                if s == 'gravel' and t.is_air(x, G - 1, z):
                    s = 'coarse_dirt'     # gravel over a vault would fall into it
                t.set(x, G, z, s)
            elif x in (4, 22) or z == 26:
                t.set(x, G, z, st.trim)
            else:
                t.set(x, G, z, floorp(x, G, z))
    # earth has drifted against the clerestory walls; it has half buried every window (so the hall cannot
    # be walked into from the surface: the veiled door stays the way in)
    drift = soil[0] if st.kind != 'sith' else 'coarse_dirt'
    for z in range(13, APSE_Z + 1):
        for x in (INNER[0] - 1, INNER[1] + 1):
            if t.get(x, F, z) is not None:
                continue
            soil_below = B.name_of(t.get(x, G, z) or '') in ('minecraft:' + soil[0], 'minecraft:' + soil[1])
            if z in WINDOW_Z:
                if not soil_below:
                    t.set(x, G, z, drift)
                t.set(x, F, z, drift)
            elif soil_below and m.at(x, 3, z) > 0.45:
                t.set(x, F, z, drift)
    # the outer walls' coping shows as a broken line of stones
    for x in range(4, 23):
        if n.at(x, 9, 26) < 0.42 and t.get(x, F, 26) is None and B.name_of(t.get(x, G, 26)) == B.full_name(st.trim):
            t.set(x, F, 26, slab(st.slab))
    for z in range(11, 26):
        for x in (4, 22):
            if n.at(x, 9, z) < 0.38 and t.get(x, F, z) is None and B.name_of(t.get(x, G, z)) == B.full_name(st.trim):
                t.set(x, F, z, slab(st.slab))
    # worn ground in the strips of natural ground either side, thinning away from the walls
    worn = patches(st, 'worn', seed + 43, 2.5)
    for x in list(range(0, 4)) + list(range(23, SX)):
        for z in range(SZ):
            if t.get(x, G, z) is not None:
                continue
            d = 4 - x if x < 4 else x - 22
            if n.at(x, 4, z) > 0.40 + 0.08 * d:
                t.set(x, G, z, worn(x, G, z))


# ---------------------------------------------------------------- dressing per kind

def dress_jedi(t, st, seed):
    # kyber grows from the cracks along the nave walls, cold blue pools on the floor
    for (x, z, f) in ((NAVE_X[0], 17, 'up'), (NAVE_X[0], 21, 'up'), (NAVE_X[1], 18, 'up'), (NAVE_X[1], 21, 'up'),
                      (NAVE_X[0], 19, 'up')):
        t.set(x, 1, z, kyber(f))
    for (x, y, z, f) in ((NAVE_X[0] + 1, 3, 16, 'east'), (NAVE_X[1] - 1, 3, 16, 'west'), (11, 6, APSE_Z - 1, 'north'),
                         (15, 6, APSE_Z - 1, 'north'), (12, 3, 13, 'south'), (14, 3, 13, 'south')):
        if t.is_air(x, y, z):
            t.set(x, y, z, kyber(f))
    # candles on the step before the seal
    for (x, z) in ((11, 22), (15, 22)):
        t.set(x, 1, z, candle('white', 3, True))
    t.set(12, 1, 22, candle('light_blue', 1, True))
    t.set(14, 1, 22, candle('white', 2, True))
    # soul lanterns hang on chains from the ridge over the nave
    for z in (16, 22):
        for y in range(7, 16):
            if t.is_air(13, y, z):
                t.set(13, y, z, chain())
        t.set(13, 6, z, lantern(hanging=True, soul=True))
    # meditation mats before the apse
    for x in (11, 15):
        for z in (17, 19):
            put(t, x, 1, z, S('light_blue_carpet'), over_air_only=True)
    # the undercroft: a pilgrim's last camp among the urns
    for (x, z) in ((11, 13), (15, 13), (16, 14)):
        t.set(x, 1, z, 'or:funerary_urn')
    t.set(12, 1, 13, S('white_carpet'))
    t.set(13, 1, 13, S('white_carpet'))
    t.set(14, 1, 14, lantern())
    # aisles: a meditation alcove (west) and a shrine of offerings (east)
    t.set(7, 1, 22, 'or:ruin_stone_carved')
    t.set(7, 2, 22, kyber('up'))
    t.set(6, 1, 21, S('light_blue_carpet'))
    t.set(8, 1, 21, S('light_blue_carpet'))
    t.set(19, 1, 22, pot('north'))
    t.set(18, 1, 22, pot('north', cracked=True))
    t.set(20, 1, 21, candle('white', 2, True))
    for (x, z) in ((7, 20), (19, 20)):
        t.set(x, 7, z, lantern(hanging=True, soul=True))
    # the forecourt: a rain puddle, moss and ferns, lanterns at the door
    for (x, z) in ((7, 7), (8, 7), (7, 8)):
        t.set(x, 4, z, S('water', level=0))
    t.set(11, 7, 9, lantern(soul=True))
    t.set(15, 7, 9, lantern(soul=True))
    t.set(11, 6, 9, 'or:ruin_stone_carved')
    t.set(15, 6, 9, 'or:ruin_stone_carved')
    t.set(11, 5, 9, 'or:ruin_stone')
    t.set(15, 5, 9, 'or:ruin_stone')
    for mid in (9, 17):
        t.set(mid, 6, 10, kyber('up'))


def dress_sith(t, st, seed):
    # red light from below: lava pools along the nave caged in iron grilles, two high in front so that
    # nobody jumps (or is knocked) over them, and over the top so that nobody stepping off the aisle
    # ledge above drops in
    for (lx, gx) in ((NAVE_X[0], NAVE_X[0] + 1), (NAVE_X[1], NAVE_X[1] - 1)):
        for z in (17, 18):
            t.set(lx, 1, z, S('lava', level=0))
            t.set(gx, 1, z, bars())
            t.set(gx, 2, z, bars())
            if t.is_air(lx, 2, z):
                t.set(lx, 2, z, bars())
        t.set(lx, 1, 19, st.trim)
        t.set(gx, 1, 19, st.trim)
        t.set(gx, 2, 19, campfire(True, 'north'))
        t.set(lx, 2, 19, chain())
    # chains hang from the roof; skulls in the wall niches
    for (x, z, top) in ((12, 16, 15), (14, 16, 15), (13, 21, 16), (11, 21, 13), (15, 21, 13)):
        for y in range(5, top):
            if t.is_air(x, y, z):
                t.set(x, y, z, chain())
    t.set(13, 4, 21, lantern(hanging=True, soul=False))
    # candles and a skull on the step before the seal
    for (x, z) in ((11, 22), (15, 22)):
        t.set(x, 1, z, candle('red', 3, True))
    t.set(14, 1, 22, candle('black', 2, True))
    t.set(12, 1, 22, skull(8))
    # cages in the aisles' far corners, with what is left of their prisoners
    for (x, z) in ((6, 22), (20, 22)):
        for y in (1, 2, 3):
            t.set(x, y, z - 1, bars())
            t.set(x + (1 if x == 6 else -1), y, z - 1, bars())
        t.set(x, 1, z, 'bone_block')
        t.set(x, 2, z, skull(0 if x == 6 else 4))
    t.set(7, 7, 20, lantern(hanging=True))
    t.set(19, 7, 20, lantern(hanging=True))
    t.set(7, 1, 22, 'magma_block')
    t.set(19, 1, 22, 'magma_block')
    # the undercroft: an ossuary
    for (x, z) in ((11, 13), (12, 13), (14, 13), (15, 13)):
        t.set(x, 1, z, 'bone_block' if (x + z) % 2 else 'or:funerary_urn')
    t.set(13, 1, 13, 'crying_obsidian')
    t.set(13, 2, 13, skull(0, wither=True))
    for (x, z) in ((11, 14), (15, 14)):
        t.set(x, 3, z, chain())
        t.set(x, 2, z, lantern(hanging=True))
    # the forecourt: braziers at the door, bones in the ash
    for x in (11, 15):
        t.set(x, 5, 9, st.trim)
        t.set(x, 6, 9, campfire(True, 'north'))
    for (x, z) in ((7, 7), (19, 6), (8, 9)):
        t.set(x, 5, z, 'bone_block')
    t.set(19, 5, 7, skull(6))
    for mid in (9, 17):
        t.set(mid, 6, 10, 'magma_block')
        t.set(mid, 7, 10, chain())


def dress_wayfarer(t, st, seed):
    # lanterns on posts down the nave, travellers' candles and cairns
    for (x, z) in ((NAVE_X[0] + 1, 17), (NAVE_X[1] - 1, 17), (NAVE_X[0] + 1, 21), (NAVE_X[1] - 1, 21)):
        t.set(x, 1, z, fence('spruce_fence'))
        t.set(x, 2, z, lantern())
    for (x, y, z) in ((NAVE_X[0], 1, 18), (NAVE_X[1], 1, 20)):
        t.set(x, y, z, 'cobblestone')
        t.set(x, y + 1, z, slab('cobblestone_slab'))
    # offerings left by travellers on the step before the seal
    for (x, z, s) in ((11, 22, candle('yellow', 3, True)), (15, 22, candle('white', 2, True)),
                      (12, 22, S('potted_cornflower')), (14, 22, S('potted_dandelion'))):
        t.set(x, 1, z, s)
    t.set(10, 1, 22, S('barrel', facing='up', open=False))
    # the undercroft: where travellers sleep out the weather
    place_bed(t, 11, 1, 14, 'brown', 'north')
    place_bed(t, 15, 1, 14, 'green', 'north')
    t.set(13, 1, 13, S('barrel', facing='up', open=False))
    t.set(13, 2, 13, lantern())
    t.set(12, 1, 13, S('or:canvas_slab', type='bottom'))
    # aisles: travellers' packs and a cold hearth
    t.set(7, 1, 22, campfire(False))
    t.set(6, 1, 22, S('barrel', facing='up', open=False))
    t.set(19, 1, 22, S('or:cargo_crate'))
    t.set(20, 1, 21, S('barrel', facing='up', open=False))
    for (x, z) in ((7, 20), (19, 20)):
        t.set(x, 7, z, chain())
        t.set(x, 6, z, lantern(hanging=True))
    # the forecourt: the scavengers' camp, and their failed dig at the sealed door
    for (x, z) in ((6, 6), (6, 9)):
        for y in (5, 6, 7):
            t.set(x, y, z, fence('spruce_fence'))
    for z in range(6, 10):
        t.set(7, 8, z, S('or:canvas_slab', type='bottom'))
        t.set(6, 8, z, S('or:canvas_slab', type='bottom'))
    place_bed(t, 7, 5, 9, 'red', 'north')
    t.set(9, 5, 7, campfire(True, 'east'))
    t.set(8, 5, 6, S('barrel', facing='up', open=False))
    t.set(6, 5, 7, S('or:cargo_crate'))
    t.set(10, 5, 9, S('scaffolding', bottom=False, distance=0, waterlogged=False))
    t.set(10, 6, 9, S('scaffolding', bottom=False, distance=0, waterlogged=False))
    t.set(12, 5, 9, slab(st.slab))
    t.set(14, 5, 9, S('or:debris'))
    t.set(13, 5, 8, S('or:debris'))
    t.set(16, 5, 9, S('or:debris'))
    t.set(17, 6, 6, lantern(hanging=True))
    t.set(17, 7, 6, fence('spruce_fence'))
    for mid in (9, 17):
        t.set(mid, 6, 10, lantern())


# ---------------------------------------------------------------- age

def age(t, st, seed):
    n = Noise(seed + 61, 3.0)
    if st.kind == 'jedi':
        nature.weather(t, {'or:ruin_bricks': [('or:ruin_stone_mossy', 0.18), ('or:ruin_stone_cracked', 0.06)],
                           'or:ruin_stone': [('or:ruin_stone_mossy', 0.2)]},
                       region=((4, 3, 0), (22, 11, 12)), noise=n, patchiness=0.8)
        # vines climb the forecourt's faces from below and hang from the pylon tops, with a gap between
        # (y 9..11) a climber cannot cross, and none on the clerestory: nothing makes a ladder onto the roof
        grow_vines(t, ((4, 4, 0), (22, 8, 13)), 0.55, F, max_len=3, allow=lambda q: q[2] <= 9)
        grow_vines(t, ((4, 13, 0), (22, 17, 13)), 0.55, F, max_len=2, allow=lambda q: q[2] <= 9)
        grow_vines(t, ((4, 13, 13), (22, 17, 26)), 0.25, F, max_len=2)
        nature.overgrow(t, region=((6, 0, 13), (20, 4, 22)), vines=0.0, moss=0.12, moss_block='minecraft:moss_carpet')
    elif st.kind == 'sith':
        nature.weather(t, {'or:tomb_bricks': [('or:tomb_stone_cracked', 0.1), ('blackstone', 0.04)]},
                       region=((4, 3, 0), (22, 17, 12)), noise=n, patchiness=0.8)
    else:
        nature.weather(t, {'tuff_bricks': [('mossy_stone_bricks', 0.12), ('cracked_stone_bricks', 0.05)]},
                       region=((4, 3, 0), (22, 17, 12)), noise=n, patchiness=0.8)
        grow_vines(t, ((4, 4, 0), (22, 8, 13)), 0.25, F, max_len=2, allow=lambda q: q[2] <= 9)
        grow_vines(t, ((4, 13, 0), (22, 14, 13)), 0.25, F, max_len=2, allow=lambda q: q[2] <= 9)
    # roots come through the cracks under the soil of the aisle roofs
    if st.kind != 'sith':
        for (x, y, z), s in sorted(t.cells.items()):
            if y == G and B.name_of(s) in ('minecraft:rooted_dirt', 'minecraft:moss_block', 'minecraft:grass_block') \
                    and t.is_air(x, y - 1, z) and (x, y - 1, z) in t.cells and t.rng.random() < 0.35:
                t.set(x, y - 1, z, S('hanging_roots', waterlogged=False))
    else:
        for (x, y, z) in ((10, 3, 13), (16, 3, 14), (6, 7, 21), (20, 7, 22), (10, 10, 17), (16, 10, 21),
                          (6, 3, 21), (20, 3, 20), (11, 3, 15), (8, 7, 13)):
            if t.is_air(x, y, z) and (x, y, z) in t.cells:
                t.set(x, y, z, 'cobweb')


def surface_life(t, st, seed):
    if st.kind == 'jedi':
        nature.scatter(t, ((0, G, 0), (26, G, 26)), [(S('short_grass'), 6), (S('fern'), 3), (S('azalea'), 1),
                                                     (S('lily_of_the_valley'), 1)], 0.35,
                       on=['grass_block', 'moss_block'])
        nature.scatter(t, ((0, G, 0), (26, G, 26)), [(S('moss_carpet'), 1)], 0.15, on=['or:ruin_stone',
                                                                                        'or:ruin_bricks'])
    elif st.kind == 'sith':
        nature.scatter(t, ((0, G, 0), (26, G, 26)), [(S('dead_bush'), 3), (S('or:scorch_mark'), 1)], 0.12,
                       on=['coarse_dirt', 'gravel'])
    else:
        nature.scatter(t, ((0, G, 0), (26, G, 26)), [(S('short_grass'), 6), (S('fern'), 1), (S('poppy'), 1),
                                                     (S('oxeye_daisy'), 1)], 0.3, on=['grass_block'])
        # travellers' cairns along the rim
        for (x, z, h) in ((3, 2, 3), (23, 3, 2), (2, 12, 2), (24, 18, 3), (3, 24, 2)):
            for k in range(h):
                t.set(x, F + k, z, ['cobblestone', 'mossy_cobblestone', 'tuff', 'andesite'][(x + k) % 4])
            t.set(x, F + h, z, slab('cobblestone_slab') if h < 3 else lantern())


# ---------------------------------------------------------------- the build

def build(kind):
    st = STYLES[kind]
    seed = SEEDS[kind]
    t = Template(SX, SY, SZ, seed=seed, name='site/echo_shrine_' + kind)
    shell(t, st, seed)
    hall(t, st, seed)
    roof_and_clerestory(t, st, seed)
    gate_front(t, st, seed)
    pylon_ruin(t, st, seed)
    roof_fall(t, st, seed)
    apse(t, st, seed)
    spire(t, st, seed)
    statue(t, st, 7, 2)
    statue(t, st, 19, 2, broken=True)
    surface(t, st, seed)
    age(t, st, seed)
    {'jedi': dress_jedi, 'sith': dress_sith, 'wayfarer': dress_wayfarer}[kind](t, st, seed)
    surface_life(t, st, seed)
    # The puzzle goes in last, over whatever the dressing did, and its front stays open.
    place_mechanism(t, C, kind, st.skin, st.plain, st.junction)
    for p in ((12, 3, 22), (13, 1, 22), (13, 2, 22)):
        t.set(*p, AIR)
    mod.spot(t, 'HEART', *HEART)
    if kind == 'wayfarer':
        mod.spawner(t, *GUARD_CAMP, st.guard_group, count=st.guard_count, anchor='GUARD')
    else:
        mod.spawner(t, *GUARD_HALL, st.guard_group, count=st.guard_count, anchor='GUARD')
    mod.light_fill(t, INTERIOR)
    verify_mechanism(t, C, kind, st.skin)
    check_anchor_spot(t, 'HEART')
    return t


def write_meta(kind):
    meta.write('site/echo_shrine_' + kind, buried=BURIED, max_slope=4, clearance=6, support_depth=8, ramp_rings=2,
               clear_above=6, terrain='any', spawn_radius=64)


def main(argv):
    prev, kinds = preview_arg(argv)
    kinds = kinds or ['jedi', 'sith', 'wayfarer']
    for kind in kinds:
        t = build(kind)
        tid = 'site/echo_shrine_' + kind
        out = save(t, tid, prev, cut=[4, 8, 12], plans=[1, 5, 9])
        if prev:
            out.append(scene_sheets(t, prev, tid.replace('/', '_'), G, cuts=[7, 11]))
        write_meta(kind)
        dark = dark_floors(t, INTERIOR)
        print('%s: %d cells, %d dark floor cells %s' % (tid, t.count(), len(dark), ' '.join(out)))


if __name__ == '__main__':
    main(sys.argv[1:])
