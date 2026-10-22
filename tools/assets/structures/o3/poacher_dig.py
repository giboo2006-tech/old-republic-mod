"""wilds/poacher_dig: a crystal poachers' dig (docs/MILESTONE_6.md 5.2, worker O3).

The story. A crystal seam broke the surface here, and poachers found it before anyone from the Watch did.
They dug fast and badly: spoil thrown anywhere, scaffolds lashed together, shoring that will not last the
winter. They brought their labour with them from the marsh villages (wilds/stilt_hamlet): the pen still
stands by the tents. Some of them are still here, guarding what they have sorted (a site spawner of the
scavengers group). In the sorting shed the good stones are picked out of the rubble; the rest go on the
heaps. At night the seam glows at the bottom of the cut.

Two start variants:
  cut    41 x 21 x 41  a terraced open cut stepping down three benches to a floor under a glowing seam,
         scaffolds and ladders on its face, a winch at the rim lowering a bucket, rails to the spoil heaps,
         a sorting shed, the camp with tents, a cook fire, the pen and the guards' spawner.
  shaft  39 x 27 x 39  a timber headframe over a shaft dropping to a gallery cut along the seam, a winch
         house, spoil heaps, a sorting shed, the camp with tents, the pen and the guards' spawner.

Placement: the yard's ground block is layer G and a player stands at G + 1, which the structure starts at
(start height 1 - (G + 1) on WORLD_SURFACE_WG). Below the yard the template is solid ground down to layer 0
(the pool element's support continues it on a slope, its ramp steps the yard's edge down to lower ground);
the cut and the shaft are explicit air, so on a hillside the dig cuts a face into the hill. Monsters are kept
out (spawn_overrides); the guards come from the spawner.

    python3 tools/assets/structures/o3/poacher_dig.py [--preview DIR] [--only cut,shaft]
"""
import math

from common import (AIR, S, B, Template, mod, Noise, Rock, mix, pick, stairs, slab, trapdoor, log, lantern, chain,
                    campfire, candle, door, bed, wood, post, boards, ladder, hang_lamp, lamp_post, crate_stack,
                    crate_pile, table, shapes, blob_heights, report, save, only_names, write_worldgen, loot_table,
                    item, empty)

NAME = 'poacher_dig'
LOOT = 'chests/wilds/poacher_dig'
LOOT_CAMP = 'chests/wilds/poacher_dig_camp'

YARD = [('minecraft:coarse_dirt', 9), ('minecraft:gravel', 3), ('minecraft:dirt', 3), ('minecraft:rooted_dirt', 1),
        ('minecraft:coarse_dirt', 3), ('minecraft:stone', 1)]
SPOIL = [('minecraft:gravel', 6), ('minecraft:cobblestone', 4), ('minecraft:tuff', 3), ('minecraft:andesite', 2),
         ('minecraft:stone', 2), ('minecraft:coarse_dirt', 2)]


def ground(t, sx, sz, G, rock, yard):
    """Solid ground from layer 0 to the yard's top layer G (the pool element's support carries it further down
    a slope), the top block a work yard's mix. The upper layers of the outer rings are dumped spoil, so where the
    yard stands above lower ground its side reads as a spoil bank."""
    spoil = mix(SPOIL, 1599, 1.8)
    for x in range(sx):
        for z in range(sz):
            ring = min(x, z, sx - 1 - x, sz - 1 - z)
            for y in range(0, G):
                if ring <= 2 and y >= G - 5:
                    t.set(x, y, z, spoil(x, y, z))
                elif y >= G - 3:
                    t.set(x, y, z, pick(t.rng, [('minecraft:dirt', 5), ('minecraft:coarse_dirt', 2),
                                                ('minecraft:stone', 1)]))
                else:
                    t.set(x, y, z, rock(x, y, z))
            t.set(x, G, z, yard(x, G, z))


def clear_above(t, x0, z0, x1, z1, y0, y1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)


def heap(t, cx, cz, G, r, h, rng, seed):
    """A spoil heap: a rough cone of rubble on the yard."""
    n = Noise(seed, 2.0)
    f = mix(SPOIL, seed + 1, 1.6)
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            if not t.inside(x, G, z):
                continue
            d = math.hypot(x - cx, z - cz) / r + 0.25 * (n.at(x, 0, z) - 0.5)
            if d >= 1.0:
                continue
            top = G + int(round(h * (1 - max(0.0, d)) ** 0.8))
            for y in range(G + 1, top + 1):
                t.set(x, y, z, f(x, y, z))


def tent(t, x0, z0, G, length, along='z', cloth='oldrepublic:canvas', rng=None, bedroll=True):
    """An A-frame tent of canvas stairs over a ridge pole, open at both ends, bedrolls inside."""
    for i in range(length):
        if along == 'z':
            x, z = x0, z0 + i
            t.set(x - 1, G + 1, z, stairs('oldrepublic:canvas_stairs', 'east'))
            t.set(x + 1, G + 1, z, stairs('oldrepublic:canvas_stairs', 'west'))
            t.set(x, G + 2, z, slab('oldrepublic:canvas_slab', 'bottom'))
            t.set(x, G + 1, z, AIR)
            if bedroll and 0 < i < length - 1:
                t.set(x, G + 1, z, 'minecraft:white_carpet' if i % 2 else 'minecraft:brown_carpet')
        else:
            x, z = x0 + i, z0
            t.set(x, G + 1, z - 1, stairs('oldrepublic:canvas_stairs', 'south'))
            t.set(x, G + 1, z + 1, stairs('oldrepublic:canvas_stairs', 'north'))
            t.set(x, G + 2, z, slab('oldrepublic:canvas_slab', 'bottom'))
            t.set(x, G + 1, z, AIR)
            if bedroll and 0 < i < length - 1:
                t.set(x, G + 1, z, 'minecraft:white_carpet' if i % 2 else 'minecraft:brown_carpet')
    # poles at the ends
    if along == 'z':
        for z in (z0 - 1, z0 + length):
            t.set(x0, G + 1, z, S('spruce_fence', waterlogged=False))
            t.set(x0, G + 2, z, S('spruce_fence', waterlogged=False))
    else:
        for x in (x0 - 1, x0 + length):
            t.set(x, G + 1, z0, S('spruce_fence', waterlogged=False))
            t.set(x, G + 2, z0, S('spruce_fence', waterlogged=False))


def pen(t, x0, z0, G):
    """The pen the diggers were kept in: iron bars round a 3 x 4 floor of straw, the door shut."""
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 6):
            edge = x in (x0, x0 + 4) or z in (z0, z0 + 5)
            if edge:
                t.set(x, G + 1, z, S('iron_bars', waterlogged=False))
                t.set(x, G + 2, z, S('iron_bars', waterlogged=False))
            else:
                t.set(x, G + 1, z, 'minecraft:yellow_carpet' if (x + z) % 3 else AIR)
                t.set(x, G + 2, z, AIR)
            t.set(x, G + 3, z, slab('spruce_slab', 'bottom') if not edge else 'minecraft:spruce_planks')
    for (x, z) in ((x0, z0), (x0 + 4, z0), (x0, z0 + 5), (x0 + 4, z0 + 5)):
        t.set(x, G + 1, z, log('spruce_log'))
        t.set(x, G + 2, z, log('spruce_log'))
    t.set(x0 + 2, G + 1, z0 + 5, S('iron_door', facing='north', half='lower', hinge='left', open=False, powered=False))
    t.set(x0 + 2, G + 2, z0 + 5, S('iron_door', facing='north', half='upper', hinge='left', open=False, powered=False))
    t.set(x0 + 3, G + 1, z0 + 1, S('iron_chain', axis='x', waterlogged=False))
    t.set(x0 + 1, G + 1, z0 + 2, 'minecraft:cauldron')


def sorting_shed(t, x0, z0, G, rng, loot):
    """An open shed (posts, a canvas roof) over the sorting tables, the haul chest, barrels of rubble."""
    w, d = 7, 5
    for (x, z) in ((x0, z0), (x0 + w - 1, z0), (x0, z0 + d - 1), (x0 + w - 1, z0 + d - 1), (x0 + 3, z0),
                   (x0 + 3, z0 + d - 1)):
        post(t, x, z, G + 3, G + 1, log('stripped_spruce_log'))
    for x in range(x0 - 1, x0 + w + 1):
        for z in range(z0 - 1, z0 + d + 1):
            t.set(x, G + 4, z, slab('oldrepublic:canvas_slab', 'bottom') if x in (x0 - 1, x0 + w) or
                  z in (z0 - 1, z0 + d) else 'oldrepublic:canvas')
    for x in range(x0 + 1, x0 + w - 1):
        for z in range(z0 + 1, z0 + d - 1):
            for y in range(G + 1, G + 4):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
    t.set(x0 + 1, G + 1, z0 + 1, 'minecraft:smithing_table')
    t.set(x0 + 2, G + 1, z0 + 1, S('stonecutter', facing='south'))
    t.set(x0 + 4, G + 1, z0 + 1, S('grindstone', face='floor', facing='south'))
    t.set(x0 + 5, G + 1, z0 + 1, 'minecraft:crafting_table')
    table(t, x0 + 1, G + 1, z0 + 3, kind='slab', on=S('amethyst_cluster', facing='up', waterlogged=False))
    table(t, x0 + 2, G + 1, z0 + 3, kind='slab', on=S('oldrepublic:kyber_cluster', facing='up'))
    t.set(x0 + 4, G + 1, z0 + 3, 'minecraft:cauldron')
    t.chest(x0 + 5, G + 1, z0 + 3, facing='west', loot=loot)
    t.set(x0 + 6, G + 1, z0 + 2, S('barrel', facing='up', open=False))
    t.set(x0, G + 1, z0 + 2, S('barrel', facing='up', open=False))
    t.set(x0 + 3, G + 3, z0 + 2, lantern(hanging=True))


def camp(t, x0, z0, G, rng, loot_camp):
    """Tents, the cook fire, the pen, crates, the guards' spawner."""
    tent(t, x0 + 2, z0, G, 5, along='z')
    tent(t, x0 + 7, z0 + 1, G, 4, along='z')
    fx, fz = x0 + 5, z0 + 8
    t.set(fx, G, fz, 'minecraft:cobblestone')
    t.set(fx, G + 1, fz, campfire('north', lit=True))
    t.set(fx - 2, G + 1, fz, log('stripped_spruce_log', 'z'))
    t.set(fx + 2, G + 1, fz, log('stripped_spruce_log', 'z'))
    t.set(fx, G + 1, fz + 2, log('stripped_spruce_log', 'x'))
    t.set(fx + 1, G + 1, fz - 2, 'minecraft:cauldron')
    crate_pile(t, x0 + 10, z0 + 7, x0 + 11, z0 + 9, G + 1, rng, density=0.8, max_h=2)
    t.chest(x0, G + 1, z0 + 7, facing='east', loot=loot_camp)
    t.set(x0, G + 1, z0 + 8, S('barrel', facing='up', open=False))
    lamp_post(t, x0 + 1, G + 1, z0 + 10, height=2)
    lamp_post(t, x0 + 9, G + 1, z0 + 5, height=2)
    mod.spawner(t, fx + 1, G + 1, fz + 1, 'scavengers', count=3, anchor='SPAWNER')
    return (fx, fz)


# ================================================================ variant cut

def build_cut():
    sx, sy, sz = 41, 21, 41
    G = 11
    t = Template(sx, sy, sz, seed=960154, name='cut')
    rng = t.rng
    rock = Rock(1521, kinds=[('stone', 38), ('andesite', 20), ('tuff', 16), ('cobblestone', 4), ('diorite', 5),
                             ('gravel', 4), ('granite', 4)])
    yard = mix(YARD, 1522, 5.0)
    ground(t, sx, sz, G, rock, yard)
    # the hill the cut goes into: it rises behind the face (north) and falls away to the yard
    hill = blob_heights(sx, sz, cx=20, cz=1, radii=(19.0, 19.0, 3.0, 9.0), base=G - 1, peak=G + 8,
                        powers=(1.8, 1.8, 1.0, 1.6), seed=1528, wobble=0.10, bumps=1.6)
    hilltop = mix([('minecraft:coarse_dirt', 3), ('minecraft:stone', 3), ('minecraft:gravel', 1),
                   ('minecraft:grass_block', 3), ('minecraft:andesite', 1)], 1529, 3.0)
    for (x, z), h in sorted(hill.items()):
        if h <= G:
            continue
        for y in range(G, h + 1):
            t.set(x, y, z, rock(x, y, z))
        t.set(x, h, z, hilltop(x, h, z))
        if t.get(x, h, z) == 'minecraft:grass_block' and rng.random() < 0.4:
            t.set(x, h + 1, z, pick(rng, [(S('short_grass'), 5), (S('dead_bush'), 1), (S('fern'), 1)]))

    # ---- the cut: rim (G), bench 1 (G - 3), bench 2 (G - 6), floor (G - 9); the face on the north side
    px0, pz0, px1, pz1 = 8, 3, 32, 21
    def level(x, z):
        if not (px0 <= x <= px1 and pz0 <= z <= pz1):
            return None
        if z == pz0:
            return G                       # the face's foot line keeps the rim
        if 13 <= x <= 27 and z <= 11:
            return G - 9
        if 11 <= x <= 29 and z <= 15:
            return G - 6
        if 9 <= x <= 31 and z <= 19:
            return G - 3
        return G
    seam = Noise(1523, 3.0)
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            lv = level(x, z)
            if lv is None or lv == G:
                continue
            t.set(x, lv, z, pick(rng, [('minecraft:gravel', 3), ('minecraft:stone', 2), ('minecraft:tuff', 2),
                                       ('minecraft:cobblestone', 1)]))
            for y in range(lv + 1, G + 1):
                t.set(x, y, z, AIR)
    # air over the cut and its rim: a hillside gets a face cut into it
    for x in range(px0, px1 + 1):
        for z in range(pz0 + 1, pz1 + 1):
            for y in range(G + 1, sy):
                t.set(x, y, z, AIR)
    clear_above(t, px0 - 1, pz1 + 1, px1 + 1, pz1 + 1, G + 1, G + 4)
    # the face above the rim: the hill cut straight down, its seam running up it
    for x in range(px0, px1 + 1):
        for y in range(G + 1, sy):
            if t.get(x, y, pz0) not in (None, AIR) and seam.at(x, y * 1.8, 0) > 0.62:
                t.set(x, y, pz0, 'oldrepublic:crystal_veined_stone')
    # the seam: crystal-veined stone in the face and the floor's back wall, clusters growing out of it
    for x in range(px0, px1 + 1):
        for y in range(G - 9, G + 1):
            z = pz0
            lv = level(x, z + 1)
            if lv is None or y <= lv:
                continue
            v = seam.at(x, y * 1.8, 0)
            if y <= G - 5 and v > 0.45:
                t.set(x, y, z, 'oldrepublic:crystal_veined_stone')
                if t.get(x, y, z + 1) == AIR and rng.random() < 0.28:
                    t.set(x, y, z + 1, S('oldrepublic:kyber_cluster', facing='south') if rng.random() < 0.6
                          else S('amethyst_cluster', facing='south', waterlogged=False))
            elif v > 0.8:
                t.set(x, y, z, 'minecraft:amethyst_block')
    for x in range(13, 28):
        for z in range(4, 12):
            if seam.at(x * 1.3, 0, z * 1.3) > 0.7:
                t.set(x, G - 9, z, 'oldrepublic:crystal_veined_stone')
            elif rng.random() < 0.04:
                t.set(x, G - 8, z, S('oldrepublic:kyber_cluster', facing='up'))
    # ramps between the levels (stairs cut down the benches)
    for i in range(3):                                       # rim to bench 1, along the east side, going north
        t.set(31, G - i, 19 - i, stairs('cobblestone_stairs', 'south'))
        for y in range(G - i + 1, G + 2):
            t.set(31, y, 19 - i, AIR)
    for i in range(3):                                       # bench 1 to bench 2 on the west side
        t.set(10 + i, G - 4 - i, 15, stairs('cobblestone_stairs', 'west'))
        for y in range(G - 3 - i, G + 1):
            t.set(10 + i, y, 15, AIR)
    for i in range(3):                                       # bench 2 to the floor on the east side
        t.set(28 - i, G - 7 - i, 11, stairs('cobblestone_stairs', 'east'))
        for y in range(G - 6 - i, G + 1):
            t.set(28 - i, y, 11, AIR)
    # scaffolds up the face, a ladder on the bench risers
    for x in (15, 16, 24):
        for y in range(G - 8, G + 2):
            t.set(x, y, 4, S('scaffolding', bottom=False, distance=0, waterlogged=False))
    for y in range(G - 8, G - 5):
        t.set(20, y, 12, S('ladder', facing='north', waterlogged=False))
    for y in range(G - 5, G - 2):
        t.set(20, y, 16, S('ladder', facing='north', waterlogged=False))
    for y in range(G - 2, G + 1):
        t.set(20, y, 20, S('ladder', facing='north', waterlogged=False))
    # shoring on the bench edges, lamps, tools left where they fell
    for (x, z, lv) in ((12, 16, G - 3), (28, 16, G - 3), (14, 12, G - 6), (26, 12, G - 6)):
        post(t, x, z, lv + 2, lv + 1, log('spruce_log'))
        t.set(x, lv + 3, z, lantern())
    t.set(17, G - 8, 7, lantern())
    t.set(23, G - 8, 9, S('barrel', facing='up', open=False))
    t.set(19, G - 8, 5, S('barrel', facing='east', open=False))
    t.set(25, G - 8, 8, 'minecraft:cauldron')
    # the winch at the south rim: an A-frame, a boom over the cut, a bucket on a chain at the floor
    for x in (18, 22):
        post(t, x, 22, G + 5, G + 1, log('stripped_spruce_log'))
    for x in range(18, 23):
        t.set(x, G + 6, 22, log('stripped_spruce_log', 'x'))
    for z in range(13, 23):
        t.set(20, G + 7, z, log('stripped_spruce_log', 'z'))
    t.set(20, G + 6, 21, stairs('spruce_stairs', 'south', 'top'))
    t.set(19, G + 1, 23, S('grindstone', face='floor', facing='east'))
    for y in range(G - 7, G + 7):
        t.set(20, y, 13, chain('y'))
    t.set(20, G - 8, 13, 'minecraft:cauldron')
    # rails from the ramp head to the heaps
    for x in range(22, 31):
        t.set(x, G + 1, 24, S('rail', shape='east_west', waterlogged=False))
    t.set(31, G + 1, 24, S('rail', shape='north_west', waterlogged=False))
    for z in range(21, 24):
        t.set(31, G + 1, z, S('rail', shape='north_south', waterlogged=False))
    t.set(31, G + 1, 20, AIR)
    # spoil heaps east and west of the cut
    heap(t, 37, 10, G, 4.5, 5, rng, 1524)
    heap(t, 36, 18, G, 3.0, 3, rng, 1525)
    heap(t, 3, 12, G, 4.0, 4, rng, 1526)
    heap(t, 4, 20, G, 2.5, 2, rng, 1527)
    # the sorting shed by the ramp head, the camp to the south
    sorting_shed(t, 23, 27, G, rng, LOOT)
    fx, fz = camp(t, 5, 26, G, rng, LOOT_CAMP)
    pen(t, 33, 31, G)
    clear_above(t, 2, 22, 38, 38, G + 1, G + 4)
    mod.spot(t, 'HEART', 20, G - 8, 9)
    return t, G


# ================================================================ variant shaft

def build_shaft():
    sx, sy, sz = 39, 27, 39
    G = 11
    t = Template(sx, sy, sz, seed=960155, name='shaft')
    rng = t.rng
    rock = Rock(1531, kinds=[('stone', 38), ('andesite', 20), ('tuff', 16), ('cobblestone', 4), ('diorite', 5),
                             ('gravel', 4), ('granite', 4)])
    yard = mix(YARD, 1532, 5.0)
    ground(t, sx, sz, G, rock, yard)
    seam = Noise(1533, 3.0)

    # ---- the shaft (3 x 3, timbered) from the yard down to the gallery
    cx, cz = 19, 13
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            inner = abs(x - cx) <= 1 and abs(z - cz) <= 1
            for y in range(2, G + 1):
                if inner:
                    t.set(x, y, z, AIR)
                elif (abs(x - cx) == 2 and abs(z - cz) == 2):
                    t.set(x, y, z, log('spruce_log'))
                elif y % 4 == 0:
                    t.set(x, y, z, 'minecraft:spruce_planks')
    for y in range(2, G + 1):
        t.set(cx, y, cz - 1, S('ladder', facing='south', waterlogged=False))
    # the collar at the top: a plank deck round the opening with a gate rail
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            if abs(x - cx) <= 1 and abs(z - cz) <= 1:
                continue
            t.set(x, G, z, 'minecraft:spruce_planks' if (x + z) % 4 else 'minecraft:dark_oak_planks')
    for x in range(cx - 2, cx + 3):
        for z in (cz - 2, cz + 2):
            if x != cx:
                t.set(x, G + 1, z, S('spruce_fence', waterlogged=False))
    for z in range(cz - 1, cz + 2):
        t.set(cx - 2, G + 1, z, S('spruce_fence', waterlogged=False))
        t.set(cx + 2, G + 1, z, S('spruce_fence', waterlogged=False))
    t.set(cx, G + 1, cz + 2, S('spruce_fence_gate', facing='south', open=True, in_wall=False, powered=False))
    t.set(cx, G + 1, cz - 2, AIR)

    # ---- the gallery along the seam at the shaft's foot: floor layer 3, 4 high, running east-west
    gy = 1
    for x in range(6, 33):
        for z in range(cz - 2, cz + 3):
            w = 2 if abs(x - cx) > 3 else 2
            if abs(z - cz) > w:
                continue
            t.set(x, gy, z, pick(rng, [('minecraft:gravel', 3), ('minecraft:stone', 2), ('minecraft:tuff', 1)]))
            for y in range(gy + 1, gy + 5):
                t.set(x, y, z, AIR)
    for x in range(6, 33):
        for z in (cz - 3, cz + 3):
            for y in range(gy, gy + 6):
                v = seam.at(x, y * 1.6, z)
                if v > 0.5:
                    t.set(x, y, z, 'oldrepublic:crystal_veined_stone')
                    zz = cz - 2 if z < cz else cz + 2
                    if gy < y < gy + 5 and t.get(x, y, zz) == AIR and rng.random() < 0.3:
                        f = 'south' if z < cz else 'north'
                        t.set(x, y, zz, S('oldrepublic:kyber_cluster', facing=f) if rng.random() < 0.6
                              else S('amethyst_cluster', facing=f, waterlogged=False))
        for z in range(cz - 2, cz + 3):
            if seam.at(x * 1.2, gy + 5, z) > 0.6:
                t.set(x, gy + 5, z, 'oldrepublic:crystal_veined_stone')
    # shoring sets every 5 blocks, lamps, a cart track along the gallery
    for x in range(8, 32, 5):
        if abs(x - cx) <= 2:
            continue
        for z in (cz - 2, cz + 2):
            post(t, x, z, gy + 3, gy + 1, log('spruce_log'))
        for z in range(cz - 2, cz + 3):
            t.set(x, gy + 4, z, log('spruce_log', 'z'))
        t.set(x, gy + 3, cz, lantern(hanging=True))
    for x in range(8, 31):
        t.set(x, gy + 1, cz + 1, S('rail', shape='east_west', waterlogged=False))
    t.set(7, gy + 1, cz, S('barrel', facing='up', open=False))
    t.set(31, gy + 1, cz - 1, S('barrel', facing='east', open=False))
    t.set(30, gy + 1, cz - 2, 'minecraft:cauldron')
    for y in range(gy + 1, gy + 3):
        t.set(cx + 1, y, cz + 1, AIR)
    mod.spot(t, 'HEART', cx, gy + 1, cz)

    # ---- the headframe over the shaft: four legs, braces, a sheave beam, the hoist chain
    top = G + 12
    legs = [(cx - 2, cz - 2), (cx + 2, cz - 2), (cx - 2, cz + 2), (cx + 2, cz + 2)]
    for (x, z) in legs:
        post(t, x, z, top, G + 1, log('stripped_spruce_log'))
    for yb in (G + 4, G + 8):
        for x in range(cx - 1, cx + 2):
            for z in (cz - 2, cz + 2):
                t.set(x, yb, z, S('spruce_fence', waterlogged=False))
        for z in range(cz - 1, cz + 2):
            for x in (cx - 2, cx + 2):
                t.set(x, yb, z, S('spruce_fence', waterlogged=False))
    for x in range(cx - 2, cx + 3):
        t.set(x, top + 1, cz - 2, log('stripped_spruce_log', 'x'))
        t.set(x, top + 1, cz + 2, log('stripped_spruce_log', 'x'))
    for z in range(cz - 1, cz + 2):
        t.set(cx, top + 1, z, log('stripped_spruce_log', 'z'))
    t.set(cx, top + 2, cz, S('spruce_trapdoor', facing='north', half='bottom', open=False, powered=False,
                              waterlogged=False))
    for y in range(gy + 6, top + 1):
        if t.get(cx, y, cz) in (None, AIR):
            t.set(cx, y, cz, chain('y'))
    # back braces: two raking struts down to the winch house
    for i in range(1, 7):
        t.set(cx - 2, top + 1 - i, cz + 2 + i, log('stripped_spruce_log', 'z'))
        t.set(cx + 2, top + 1 - i, cz + 2 + i, log('stripped_spruce_log', 'z'))
    for x in (cx - 2, cx + 2):
        post(t, x, cz + 8, top - 6, G + 1, log('stripped_spruce_log'))
    # the winch house behind it: a small plank shed with the drum
    wx0, wz0 = cx - 3, cz + 7
    for x in range(wx0, wx0 + 7):
        for z in range(wz0, wz0 + 5):
            edge = x in (wx0, wx0 + 6) or z in (wz0, wz0 + 4)
            for y in range(G + 1, G + 4):
                t.set(x, y, z, 'minecraft:spruce_planks' if edge else AIR)
            t.set(x, G + 4, z, slab('spruce_slab', 'bottom'))
    for (x, z) in ((wx0, wz0), (wx0 + 6, wz0), (wx0, wz0 + 4), (wx0 + 6, wz0 + 4)):
        post(t, x, z, G + 3, G + 1, log('stripped_spruce_log'))
    for y in (G + 1, G + 2):
        t.set(cx, y, wz0, AIR)
    for x in range(wx0 + 1, wx0 + 6):
        t.set(x, G + 2, wz0 + 2, log('stripped_spruce_log', 'x'))
    t.set(wx0 + 1, G + 1, wz0 + 3, S('grindstone', face='floor', facing='east'))
    t.set(wx0 + 5, G + 1, wz0 + 3, S('barrel', facing='up', open=False))
    t.set(wx0 + 5, G + 1, wz0 + 1, 'minecraft:furnace[facing=west,lit=true]')
    hang_lamp(t, cx, G + 3, wz0 + 1, drop=0)
    for (x, z) in ((wx0 + 2, wz0 + 4), (wx0 + 6, wz0 + 2)):
        t.set(x, G + 2, z, AIR)
    clear_above(t, cx - 4, cz - 4, cx + 4, cz + 13, G + 1, G + 6)

    # ---- heaps, the sorting shed, the camp, the pen
    heap(t, 5, 6, G, 4.5, 5, rng, 1534)
    heap(t, 33, 6, G, 4.0, 4, rng, 1535)
    heap(t, 34, 15, G, 3.0, 3, rng, 1536)
    for x in range(cx + 3, 30):
        t.set(x, G + 1, cz, S('rail', shape='east_west', waterlogged=False))
    sorting_shed(t, 4, 15, G, rng, LOOT)
    fx, fz = camp(t, 3, 24, G, rng, LOOT_CAMP)
    pen(t, 27, 25, G)
    clear_above(t, 2, 14, 36, 36, G + 1, G + 4)
    return t, G


# ================================================================ worldgen, loot

def write_loot():
    loot_table(LOOT, [
        ((2, 4), [item('iron_nugget', 8, 3, 9), item('amethyst_shard', 6, 1, 3), item('bread', 4, 1, 3),
                  item('torch', 5, 2, 6), item('oldrepublic:crystal_veined_stone', 3, 1, 2),
                  item('iron_ingot', 2, 1, 2), item('gold_nugget', 2, 2, 5)]),
    ])
    loot_table(LOOT_CAMP, [
        ((2, 3), [item('bread', 5, 1, 3), item('string', 3, 1, 3), item('leather', 2, 1, 2), item('torch', 4, 2, 5),
                  item('iron_nugget', 4, 2, 6), item('gunpowder', 2, 1, 2), item('oldrepublic:credit_chit', 1, 1, 3),
                  empty(3)]),
    ])


BUILDERS = [('cut', build_cut), ('shaft', build_shaft)]


def main():
    only = only_names()
    write_loot()
    for name, fn in BUILDERS:
        if only and name not in only:
            continue
        t, G = fn()
        assert G == G_ALL and t.sy == SY[name], (name, G, t.sy)
        save(t, 'wilds/%s/%s' % (NAME, name), views=('se', 'nw'))
        report(t, 'wilds/%s/%s' % (NAME, name))
    write_worldgen(NAME, [(name, 1, dict(clear_from=SY[name])) for name, _ in BUILDERS], buried=G_ALL + 1,
                   spacing=52, separation=20, salt=960152,
                   biomes=['#minecraft:is_hill', '#minecraft:is_badlands', 'minecraft:stony_peaks',
                           'minecraft:windswept_savanna'],
                   adapted=dict(yard_stand=G_ALL + 1, clear_inset=4, support_depth=8, ramp_rings=2),
                   max_distance=64)


G_ALL = 11                        # both variants: the yard's ground block layer
SY = {'cut': 21, 'shaft': 27}     # template heights (the pool element clears terrain above them)

if __name__ == '__main__':
    main()
