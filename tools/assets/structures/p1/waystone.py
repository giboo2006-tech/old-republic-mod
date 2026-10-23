"""planet/waystone and planet/waystone_corrupted: a Watch way-shrine of Selvaren (docs/MILESTONE_6.md 5.5).

The Watch set stones so pilgrims could find water and shelter. Each site is a great field menhir with the
waystone set as a carved stele at its north foot, on a skirted plinth in a court of old flagstones; seven
lesser stones stand round it (one leaning, one broken, one fallen where an old silver tree heaved it); a
pilgrims' basin and a bench under the tree; candles and a pot of flowers that pilgrims still leave; and a
cairn of Archive stone whose west face is a veiled doorway hiding a cache.

The corrupted template is the clean one with its living things stripped (the tree a grey snag, the turf
gone to coarse dirt and dry grass, the basin fouled) and dark veins crawling through the stone; it is a
cell-for-cell superset of the clean one (Waystones.decide stamps it over the clean one) and adds the GUARD
spawner (twisted hollowhorns, bound).

Both: 21 x 18 x 21, buried 2 (Waystones.BURIED), STONE (10, 3, 10) facing north, HEART (10, 2, 8) in front
of it, CACHE (16, 2, 12) in the cairn. Worldgen: pool waystone/start, structure waystone, set waystones
(clear_of_origin from 256 blocks, salt 905117 kept).
"""
import math

from selvaren_lib import (AIR, B, FIELD, Noise, Palette, RUIN, S, Template, W, WATCH, check_chests, check_reach,
                          check_superset, clear_bowl, field_stone, ground, meta, mod, nature, plants, save,
                          silver_tree, slab, stair, weather, blob)

SX, SY, SZ = 21, 18, 21
BURIED = 2
FEET = 2
TURF = 1
STONE = (10, 3, 10)
HEART = (10, 2, 8)
CACHE = (16, 2, 12)
GUARD = (6, 2, 7)
CX, CZ = 10, 12          # the menhir's middle
SEED = 905117


def build_clean():
    t = Template(SX, SY, SZ, seed=SEED, name='planet/waystone')
    rng = t.rng
    inside = blob(10, 10, 9.4, SEED + 1, rough=1.1, scale=3.5)
    cols = {(x, z) for x in range(SX) for z in range(SZ) if inside(x, z)}
    # the path's mouth always reaches the north edge
    cols |= {(x, z) for x in range(8, 13) for z in range(0, 3)}
    ground(t, cols, TURF, SEED + 2, kind='mixed')
    clear_bowl(t, cols, 10, 10, FEET, SY - 1, r_full=6.5, r_out=9.6, rim_h=2)

    court(t, cols)
    menhir_and_stele(t)
    ring_stones(t)
    cairn(t)
    basin(t)
    silver_tree(t, 3, FEET, 16, 11, SEED + 7, boughs=3, spread=2.5)
    bench(t)
    offerings(t)

    weather(t, seed=SEED + 9)
    nature.overgrow(t, region=((0, FEET, 0), (SX - 1, SY - 1, SZ - 1)), vines=0.10, moss=0.0, lichen=0.0,
                    wall_names=['minecraft:stone', 'minecraft:andesite', 'minecraft:mossy_cobblestone',
                                'minecraft:cobblestone', 'minecraft:tuff', 'oldrepublic:watch_stone',
                                'minecraft:mossy_stone_bricks', 'minecraft:stone_bricks'])
    shade = {(x, z) for (x, z) in cols if math.hypot(x - 4, z - 16) <= 4.2}
    plants(t, cols - shade, 'meadow', 0.34, SEED + 11)
    plants(t, shade, 'shade', 0.55, SEED + 12)

    t.anchor('STONE', *STONE)
    mod.spot(t, 'HEART', *HEART)
    t.anchor('CACHE', *CACHE)
    return t, cols


def court(t, cols):
    """The pilgrims' road from the north and the flagstone court round the stone, gappy with turf and moss."""
    n = Noise(SEED + 21, 2.2)
    floor = WATCH.patches('floor', seed=SEED + 22, scale=2.5)
    path = Palette(p=[('minecraft:dirt_path', 6), ('minecraft:gravel', 2), ('minecraft:coarse_dirt', 2),
                      ('minecraft:mossy_cobblestone', 2)]).patches('p', seed=SEED + 23, scale=2.0)
    # the road: in from the north edge, frayed
    for z in range(0, 9):
        half = 1.6 + (n.at(10, z) - 0.5) * 1.2
        for x in range(6, 15):
            if abs(x - 10) <= half and (x, z) in cols:
                t.set(x, TURF, z, path(x, TURF, z) if z < 6 or n.at(x, z * 1.7) < 0.55 else floor(x, TURF, z))
    # a fainter track east, toward the next stone
    for x in range(13, SX):
        zc = 9 + int(round(math.sin(x * 0.5) * 0.8))
        for z in (zc, zc + 1):
            if (x, z) in cols and n.at(x * 1.3, z) < 0.62:
                t.set(x, TURF, z, path(x, TURF, z))
    # the court: flagstones within ~5 of the menhir, missing in patches
    for (x, z) in sorted(cols):
        d = math.hypot(x - CX, (z - CZ) * 1.1)
        if d <= 5.3 and n.at(x * 1.1, z * 1.1) < 0.72 - max(0.0, d - 3.5) * 0.18:
            t.set(x, TURF, z, floor(x, TURF, z))


def menhir_and_stele(t):
    """The great stone: a blade of field stone 3 wide and 2 deep (x 9..11, z 12..13) on a flared foot,
    narrowing and leaning a little west to a slanted tip at y 14; and the waystone stele free-standing
    before its north face (x 10, z 10), with a one-cell slot between them."""
    body = Palette(s=[('minecraft:stone', 6), ('minecraft:andesite', 4), ('minecraft:tuff', 1),
                      ('minecraft:cobblestone', 1)]).patches('s', seed=SEED + 31, scale=2.2)
    moss = Noise(SEED + 32, 2.5)

    def rect(x0, x1, z0, z1):
        return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}
    profile = {
        2: rect(8, 12, 12, 14) - {(8, 14), (12, 14)},
        3: rect(8, 12, 12, 13) | rect(9, 11, 14, 14),
        4: rect(9, 11, 12, 14) | {(8, 12), (12, 13)},
        5: rect(9, 11, 12, 13) | {(10, 14)},
        6: rect(9, 11, 12, 13),
        7: rect(9, 11, 12, 13),
        8: rect(9, 11, 12, 13) - {(11, 13)},
        9: rect(9, 11, 12, 12) | rect(9, 10, 13, 13),
        10: rect(9, 10, 12, 13) | {(11, 12)},
        11: rect(9, 10, 12, 13),
        12: rect(9, 10, 12, 12) | {(9, 13)},
        13: {(9, 12)},
    }
    for y, cells in profile.items():
        for (x, z) in sorted(cells):
            s = body(x, y, z)
            # moss on the shaded north face and the west side, in patches
            if (z == min(zz for (xx, zz) in cells if xx == x) or x == min(xx for (xx, zz) in cells)) \
                    and moss.at(x, y, z) > 0.56:
                s = 'minecraft:mossy_cobblestone' if moss.at(x, y * 2, z) > 0.5 else 'minecraft:moss_block'
            t.set(x, y, z, s)
    # sloped shoulders wherever the blade narrows, and a slanted tip
    ys = sorted(profile)
    for lo, hi in zip(ys, ys[1:]):
        upper = profile[hi]
        for (x, z) in sorted(profile[lo]):
            if (x, z) in upper:
                continue
            toward = None
            for d in ('north', 'south', 'east', 'west'):
                dx, dz = B.step(d)
                if (x + dx, z + dz) in upper:
                    toward = d
                    break
            if toward and t.rng.random() < 0.85:
                t.set(x, hi, z, stair(t.rng.choice(['minecraft:stone_stairs', 'minecraft:andesite_stairs',
                                                    'minecraft:cobblestone_stairs']), toward))
    t.set(10, 13, 12, stair('minecraft:andesite_stairs', 'west'))
    t.set(9, 14, 12, stair('minecraft:stone_stairs', 'south'))
    # the stele: carved plinth, the waystone (facing north), a dressed block and a sloped coping
    t.set(10, 2, 10, 'minecraft:chiseled_stone_bricks')
    t.set(*STONE, S('or:waystone', facing='north'),
          nbt={'id': 'oldrepublic:waystone', 'pending': B_byte(1), 'local': int_array(STONE)})
    t.set(10, 4, 10, 'minecraft:polished_andesite')
    t.set(10, 5, 10, stair('or:watch_stone_stairs', 'south'))
    for y in range(2, 6):
        t.set(10, y, 11, AIR)  # the slot between stele and stone
    # the skirt: a step of the Watch's stone round the stone's foot and the stele, broken here and there
    foot = profile[2] | {(10, 10)}
    for x in range(6, 15):
        for z in range(8, 17):
            if (x, z) in foot:
                continue
            near = [(x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if (x + dx, z + dz) in foot]
            if not near:
                continue
            toward = None
            for d in ('south', 'north', 'east', 'west'):
                dx, dz = B.step(d)
                if (x + dx, z + dz) in foot:
                    toward = d
                    break
            if toward is None:  # a corner of the skirt: face along the nearest side
                fx, fz = near[0]
                toward = 'south' if fz > z else 'north'
            r = t.rng.random()
            if r < 0.12 and (x, z) not in ((10, 9), (9, 10), (11, 10)):
                continue  # a lost step: the turf shows
            name = 'or:watch_stone_stairs' if r < 0.75 else t.rng.choice(['minecraft:mossy_stone_brick_stairs',
                                                                       'minecraft:stone_brick_stairs'])
            t.set(x, FEET, z, stair(name, toward))
            t.set(x, TURF, z, WATCH.pick('floor', t.rng))


def ring_stones(t):
    """Seven lesser stones round the court, every one different."""
    rng = t.rng
    # 0: tall and paired, north-east of the road
    field_stone(t, [(13, 3), (14, 3)], FEET, [4, 3], rng, top='stair', mossy=0.4)
    # 1: leaning out to the east
    field_stone(t, [(18, 8)], FEET, [4], rng, top='slab', lean='east')
    # 2: broken: a stump, its top lying in the grass beside it
    field_stone(t, [(16, 16), (16, 17)], FEET, [1, 2], rng, top='slab', mossy=0.5)
    for x, z in ((17, 18), (18, 18), (18, 19)):
        t.set(x, FEET, z, FIELD.pick('moss', rng))
    t.set(19, FEET, 19, stair('minecraft:mossy_cobblestone_stairs', 'west'))
    # 3: behind the great stone, squat and wide
    field_stone(t, [(11, 18), (12, 18)], FEET, [3, 2], rng, top='stair')
    # 4: fallen where the silver tree's roots heaved it, lying east-west
    for x in range(5, 9):
        t.set(x, FEET, 19, FIELD.pick('stone', rng) if x != 8 else stair('minecraft:stone_stairs', 'west'))
    t.set(6, FEET + 1, 19, slab('minecraft:mossy_cobblestone_slab'))
    # 5: west, a pair, slab-capped
    field_stone(t, [(2, 8), (2, 7)], FEET, [3, 2], rng, top='slab')
    # 6: north-west of the road, leaning in
    field_stone(t, [(6, 3)], FEET, [3], rng, top='stair', lean='south')


def cairn(t):
    """A cairn of Archive stone east of the menhir: the cache chest inside, behind a west face of two veiled stones
    that open as one doorway (Sense reveals them, Push or a lit saber opens them) so a person can step up to the chest."""
    rng = t.rng
    cx, y, cz = CACHE
    base = [(cx + dx, cz + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)] + [(cx + 2, cz), (cx, cz + 2), (cx + 1, cz - 2)]
    for (x, z) in base:
        t.set(x, y, z, RUIN.pick('wall', rng))
        t.set(x, y - 1, z, 'minecraft:cobblestone')
    for (x, z) in ((cx - 1, cz), (cx + 1, cz), (cx, cz - 1), (cx, cz + 1), (cx + 1, cz + 1), (cx - 1, cz - 1)):
        t.set(x, y + 1, z, RUIN.pick('wall', rng))
    t.set(cx + 1, y + 1, cz - 1, slab('or:ruin_brick_slab'))
    t.set(cx - 1, y + 1, cz + 1, stair('or:ruin_brick_stairs', 'east'))
    t.set(cx, y + 1, cz, slab('or:ruin_brick_slab'))        # never a full block over the chest
    t.set(cx, y + 2, cz, 'or:ruin_stone_mossy')              # the capstone
    t.set(cx + 1, y + 2, cz, slab('or:ruin_brick_slab'))
    t.chest(cx, y, cz, facing='west', loot='chests/force_cache')
    t.set(cx - 1, y, cz, S('or:veiled_stone', skin='ruin', veil='veiled'))
    t.set(cx - 1, y + 1, cz, S('or:veiled_stone', skin='ruin', veil='veiled'))
    t.set(cx + 2, y + 1, cz, 'minecraft:moss_carpet')


def basin(t):
    """The pilgrims' basin west of the stone: a stone trough fed by a spring stone, water at the feet layer."""
    rng = t.rng
    for x in range(3, 7):
        for z in range(10, 14):
            edge = x in (3, 6) or z in (10, 13)
            t.set(x, TURF, z, WATCH.pick('floor', rng) if edge else 'minecraft:stone')
            t.set(x, TURF - 1, z, 'minecraft:stone')
            if edge:
                if (x, z) in ((6, 11), (6, 12)):
                    t.set(x, FEET, z, stair('or:watch_stone_stairs', 'west'))
                elif (x, z) in ((4, 13), (5, 13)):
                    t.set(x, FEET, z, stair('minecraft:stone_brick_stairs', 'north'))
                else:
                    t.set(x, FEET, z, WATCH.pick('wall', rng))
            else:
                t.set(x, FEET, z, 'minecraft:water[level=0]')
    # the spring stone at the back (west), with its carved face
    t.set(3, FEET + 1, 11, 'minecraft:chiseled_stone_bricks')
    t.set(3, FEET + 1, 12, WATCH.pick('wall', rng))
    t.set(3, FEET + 2, 11, stair('or:watch_stone_stairs', 'east'))
    t.set(3, FEET + 2, 12, slab('or:watch_stone_slab'))
    t.set(4, FEET + 1, 11, S('minecraft:oak_trapdoor', facing='east', half='top', open=False, powered=False,
                               waterlogged=False))  # the spout lip
    t.set(2, FEET, 11, 'minecraft:mossy_cobblestone')
    t.set(2, FEET, 12, 'minecraft:moss_block')


def bench(t):
    """A pilgrims' bench under the tree, facing the stone."""
    for z in (14, 15):
        t.set(7, FEET, z, stair('minecraft:spruce_stairs', 'west'))
    t.set(7, FEET, 13, S('minecraft:spruce_trapdoor', facing='north', half='bottom', open=True, powered=False,
                           waterlogged=False))
    t.set(7, FEET, 16, S('minecraft:spruce_trapdoor', facing='south', half='bottom', open=True, powered=False,
                           waterlogged=False))
    t.set(8, FEET, 15, 'minecraft:flower_pot')


def offerings(t):
    """What pilgrims still leave: candles on the flagstones before the stele, a pot of flowers, a lantern."""
    t.set(9, FEET, 8, S('minecraft:white_candle', candles=3, lit=True, waterlogged=False))
    t.set(11, FEET, 8, S('minecraft:white_candle', candles=2, lit=True, waterlogged=False))
    t.set(9, TURF, 8, 'or:watch_stone')
    t.set(11, TURF, 8, 'or:watch_stone')
    t.set(12, FEET, 9, 'minecraft:potted_lily_of_the_valley')
    t.set(12, TURF, 9, 'minecraft:polished_andesite')


# ---------------------------------------------------------------- corruption

DEAD_PLANTS = [('minecraft:short_dry_grass', 6), ('minecraft:dead_bush', 3), (AIR, 4)]
LIVING = ('short_grass', 'fern', 'tall_grass', 'large_fern', 'wildflowers', 'pink_petals', 'bush', 'allium',
          'cornflower', 'silverleaf', 'lily_of_the_valley', 'brown_mushroom', 'leaf_litter', 'moss_carpet')
# Dark veins: a core of blackstone and a halo of deepslate, by the block family they cross.
VEIN = {
    'minecraft:stone': ('minecraft:blackstone', 'minecraft:deepslate'),
    'minecraft:andesite': ('minecraft:blackstone', 'minecraft:cobbled_deepslate'),
    'minecraft:tuff': ('minecraft:polished_blackstone', 'minecraft:deepslate'),
    'minecraft:cobblestone': ('minecraft:blackstone', 'minecraft:cobbled_deepslate'),
    'minecraft:mossy_cobblestone': ('minecraft:blackstone', 'minecraft:cobbled_deepslate'),
    'minecraft:moss_block': ('minecraft:rooted_dirt', 'minecraft:rooted_dirt'),
    'oldrepublic:watch_stone': ('minecraft:cracked_polished_blackstone_bricks', 'minecraft:cracked_deepslate_bricks'),
    'minecraft:stone_bricks': ('minecraft:polished_blackstone_bricks', 'minecraft:deepslate_bricks'),
    'minecraft:mossy_stone_bricks': ('minecraft:cracked_polished_blackstone_bricks', 'minecraft:cracked_deepslate_bricks'),
    'minecraft:cracked_stone_bricks': ('minecraft:cracked_polished_blackstone_bricks', 'minecraft:cracked_deepslate_bricks'),
    'minecraft:polished_andesite': ('minecraft:polished_blackstone', 'minecraft:polished_deepslate'),
    'minecraft:chiseled_stone_bricks': ('minecraft:chiseled_polished_blackstone', 'minecraft:chiseled_deepslate'),
    'minecraft:stone_stairs': ('minecraft:blackstone_stairs', 'minecraft:cobbled_deepslate_stairs'),
    'minecraft:andesite_stairs': ('minecraft:blackstone_stairs', 'minecraft:cobbled_deepslate_stairs'),
    'minecraft:cobblestone_stairs': ('minecraft:blackstone_stairs', 'minecraft:cobbled_deepslate_stairs'),
    'minecraft:mossy_cobblestone_stairs': ('minecraft:blackstone_stairs', 'minecraft:cobbled_deepslate_stairs'),
    'oldrepublic:watch_stone_stairs': ('minecraft:polished_blackstone_brick_stairs', 'minecraft:deepslate_brick_stairs'),
    'minecraft:stone_brick_stairs': ('minecraft:polished_blackstone_brick_stairs', 'minecraft:deepslate_brick_stairs'),
    'minecraft:mossy_stone_brick_stairs': ('minecraft:polished_blackstone_brick_stairs', 'minecraft:deepslate_brick_stairs'),
    'minecraft:stone_slab': ('minecraft:blackstone_slab', 'minecraft:cobbled_deepslate_slab'),
    'minecraft:andesite_slab': ('minecraft:blackstone_slab', 'minecraft:cobbled_deepslate_slab'),
    'minecraft:mossy_cobblestone_slab': ('minecraft:blackstone_slab', 'minecraft:cobbled_deepslate_slab'),
    'oldrepublic:watch_stone_slab': ('minecraft:polished_blackstone_brick_slab', 'minecraft:deepslate_brick_slab'),
}


def corrupt(clean):
    """The corrupted waystone: a copy of every clean cell, soured, plus the guard."""
    t = Template(SX, SY, SZ, seed=SEED + 1000, name='planet/waystone_corrupted')
    for p, s in clean.cells.items():
        t.set(*p, s, nbt=dict(clean.nbt[p]) if p in clean.nbt else None)
    for name, cells in clean.anchors.items():
        for c in cells:
            t.anchor(name, *c)
    rng = t.rng
    spread = Noise(SEED + 1001, 4.0)
    vein = Noise(SEED + 1002, 4.5)
    vein2 = Noise(SEED + 1003, 3.0)

    def strength(x, y, z):
        d = math.hypot(x - CX, z - CZ)
        return max(0.0, min(1.0, 1.25 - d / 10.5 + (spread.at(x, z) - 0.5) * 0.9))

    for p in sorted(t.cells, key=lambda q: (q[1], q[2], q[0])):
        s = t.cells[p]
        name = B.name_of(s)
        path = name.split(':', 1)[1]
        x, y, z = p
        k = strength(x, y, z)
        if name == 'oldrepublic:waystone':
            t.set(x, y, z, S('or:waystone_corrupted', facing='north'),
                  nbt={'id': 'oldrepublic:waystone', 'pending': B_byte(0), 'local': int_array(STONE)})
        elif path.endswith('_leaves'):
            t.set(x, y, z, AIR)
        elif path == 'birch_log':
            t.set(x, y, z, s.replace('minecraft:birch_log', 'minecraft:stripped_birch_log'))
        elif name == 'minecraft:grass_block' or name == 'minecraft:podzol':
            if rng.random() < k:
                r = rng.random()
                t.set(x, y, z, 'minecraft:coarse_dirt' if r < 0.5 else 'minecraft:podzol[snowy=false]' if r < 0.75
                      else 'minecraft:rooted_dirt' if r < 0.9 else 'minecraft:dirt')
        elif any(path == l or path.startswith(l) for l in LIVING):
            if B.parse(s)[1].get('half') == 'upper':
                continue  # handled with its lower half
            if rng.random() < k + 0.15:
                ns = pick_dead(rng)
                t.set(x, y, z, ns)
                if B.parse(s)[1].get('half') == 'lower':
                    t.set(x, y + 1, z, AIR)
        elif name == 'minecraft:water':
            t.set(x, y, z, 'minecraft:mud')
        elif path.endswith('candle'):
            t.set(x, y, z, B.with_props(s, lit=False))
        elif name == 'minecraft:potted_lily_of_the_valley':
            t.set(x, y, z, 'minecraft:potted_dead_bush')
        elif name == 'minecraft:lantern':
            t.set(x, y, z, AIR)
        elif name == 'minecraft:moss_block' and y > TURF:
            if rng.random() < k:
                t.set(x, y, z, 'minecraft:tuff')
        elif name in VEIN:
            # dark veins: thin ridges of noise, a blackstone core with a deepslate halo, thicker near the stone
            v = max(1.0 - abs(2.0 * vein.at(x, y * 1.2, z) - 1.0), 1.0 - abs(2.0 * vein2.at(z, y * 1.2, x) - 1.0))
            core, halo = VEIN[name]
            props = B.parse(s)[1]
            if v > 0.95 - 0.07 * k:
                t.set(x, y, z, B.build(core, props) if props else core)
            elif v > 0.88 - 0.1 * k and rng.random() < 0.7:
                t.set(x, y, z, B.build(halo, props) if props else halo)
    # cobwebs in the snag and at the menhir's foot
    for p in ((3, 9, 16), (4, 10, 15), (2, 9, 17), (8, 3, 12), (12, 3, 13), (11, 5, 13)):
        if t.get(*p) == AIR:
            t.set(*p, 'minecraft:cobweb')
    # the twisted herd's spawner, in the open court north-west of the stone
    mod.spawner(t, *GUARD, 'hollowhorns', count=2, bound=True, anchor='GUARD')
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            for dy in (1, 2):
                q = (GUARD[0] + dx, GUARD[1] + dy, GUARD[2] + dz)
                cur = t.get(*q)
                if cur is not None and cur != AIR and mod.walk_through(cur):
                    t.set(*q, AIR)
    return t


def pick_dead(rng):
    total = sum(w for _, w in DEAD_PLANTS)
    r = rng.random() * total
    for s, w in DEAD_PLANTS:
        r -= w
        if r <= 0:
            return s
    return DEAD_PLANTS[-1][0]


def B_byte(v):
    from structlib.nbtio import Byte
    return Byte(v)


def int_array(p):
    from structlib.nbtio import IntArray
    return IntArray(list(p))


# ---------------------------------------------------------------- worldgen

def worldgen():
    W.pool('waystone/start', [W.element('planet/waystone', adapted=dict(
        yard_stand=FEET, clear_from=SY, clear_inset=3, support_depth=24, ramp_rings=0))])
    W.structure('waystone', start_pool='waystone/start', biome_tag='has_structure/waystone', size=1,
                max_distance=24, step='surface_structures', start_height=1 - BURIED)
    W.structure_set('waystones', [('waystone', 1)], spacing=20, separation=8, salt=905117,
                    placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/waystone', ['#oldrepublic:is_selvaren'])
    meta.write('planet/waystone_corrupted', buried=BURIED, terrain='any', support_depth=24, ramp_rings=0)


def check_cache(t):
    """A person walks up to the stone; the cache is shut while the doorway is veiled and in reach once it opens."""
    veil = [p for p, s in t.cells.items() if B.name_of(s) == 'oldrepublic:veiled_stone']
    shut = check_reach(t, FEET, {'stone': [STONE], 'heart': [HEART]})
    if any(abs(x - CACHE[0]) + abs(z - CACHE[2]) <= 1 and abs(y - CACHE[1]) <= 1 for (x, y, z) in shut):
        raise SystemExit('%s: the cache is in reach without opening the veiled doorway' % t.name)
    check_reach(t, FEET, {'cache (doorway open)': [CACHE]}, gone=veil)


def main():
    clean, cols = build_clean()
    clean.finish()
    bad = corrupt(clean)
    check_superset(clean, bad)
    check_chests(clean)
    check_chests(bad)
    check_cache(clean)
    check_cache(bad)
    save(clean, 'planet/waystone', 'waystone')
    save(bad, 'planet/waystone_corrupted', 'waystone_corrupted')
    worldgen()


if __name__ == '__main__':
    main()
