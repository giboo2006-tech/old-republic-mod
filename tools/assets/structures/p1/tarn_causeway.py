"""selvaren/tarn_causeway: a stone causeway over a tarn to a shrine island (docs/MILESTONE_6.md 5.5), two
variants.

The Watch built its quiet places where the water was still. A causeway of dressed stone crosses the tarn to
an island and its shrine; storms and ice have broken spans out of it since.

Each template carries its own tarn: banks all round its edge hold the water in, so it sits as well on dry
ground as in the tarn biome's own water. The water's surface is layer FLOOR (the ground's top); the deck
stands one above it. Worldgen keeps ramp rings off (a ramp would cut the banks) and supports the bed deep.

- `isle` (55 x 21 x 25): straight across from the west bank to the island, two spans broken (a jump of two
  and of three, or a swim), lantern posts on the parapets; a small arched shrine on the island with its
  roof, the meditation stone under it, the chest at its back; a silver tree.
- `broken` (49 x 19 x 31): from the south bank north, then east along a bend to the island; a whole span
  gone at the bend (stepping stones of fallen blocks), the parapets mostly down; the shrine roofless with two
  of its piers fallen; a drowned silver tree standing in the water.

Worldgen: pool selvaren/tarn_causeway/start (both, adapted terrain, no ramp), structure
selvaren/tarn_causeway in the tarn, set 36/12, salt 960201, clear of the origin.
"""
import math
from collections import deque

from selvaren_lib import (AIR, B, FIELD, Noise, S, Template, W, WATCH, check_chests, check_reach, clear_bowl,
                          loot_table, mod, nature, plants, save, silver_tree, slab, stair, weather, field_stone)
import watchkit as K

SEED = 960201
BURIED = 5
FLOOR = BURIED - 1               # the water's surface, the banks' turf
FEET = BURIED
LOOT = 'chests/selvaren/tarn_causeway'
WATER = 'minecraft:water'

BED = [('minecraft:mud', 3), ('minecraft:gravel', 3), ('minecraft:clay', 2), ('minecraft:sand', 1),
       ('minecraft:coarse_dirt', 1)]
BELOW = [('minecraft:mossy_cobblestone', 4), ('minecraft:cobblestone', 2), ('minecraft:mossy_stone_bricks', 3),
         ('minecraft:stone_bricks', 1)]


def chests(t):
    return {'chest %d,%d,%d' % p: [p] for p, st in sorted(t.cells.items()) if B.name_of(st) == 'minecraft:chest'}


# ---------------------------------------------------------------- the tarn

def tarn(t, wet, seed, max_depth=4):
    """Lays the ground of every column: banks where not `wet`, water over a bed where wet, the bed deeper
    the farther from the bank. Returns {(x, z): bed top y} for the wet columns."""
    n = Noise(seed, 3.0)
    dist = {}
    q = deque()
    for x in range(t.sx):
        for z in range(t.sz):
            if (x, z) not in wet:
                dist[(x, z)] = 0
                q.append((x, z))
    while q:
        x, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c = (x + dx, z + dz)
            if c in wet and c not in dist:
                dist[c] = dist[(x, z)] + 1
                q.append(c)
    beds = {}
    for x in range(t.sx):
        for z in range(t.sz):
            d = dist.get((x, z), 0)
            if (x, z) in wet:
                depth = max(1, min(max_depth, 1 + int((d - 1) * 0.8 + n.at(x, z) * 1.2)))
                top = FLOOR - depth
                beds[(x, z)] = top
                for y in range(0, top + 1):
                    t.set(x, y, z, 'minecraft:stone' if y == 0 else
                          K.pickw(BED, 0.6 * n.at(x, y, z) + 0.4 * n._h(x, y, z)) if y == top else 'minecraft:dirt')
                for y in range(top + 1, FLOOR + 1):
                    t.set(x, y, z, WATER)
            else:
                for y in range(0, FLOOR):
                    t.set(x, y, z, 'minecraft:stone' if y == 0 else 'minecraft:dirt')
                near = any((x + dx, z + dz) in wet for dx in (-1, 0, 1) for dz in (-1, 0, 1))
                v = n.at(x * 1.3, z * 1.3)
                top = ('minecraft:gravel' if v > 0.6 else 'minecraft:mud' if v > 0.5 else 'minecraft:coarse_dirt') \
                    if near and v > 0.42 else ('minecraft:podzol[snowy=false]' if v > 0.7 else 'minecraft:grass_block[snowy=false]')
                t.set(x, FLOOR, z, top)
    return beds


def water_life(t, wet, beds, seed, deck=()):
    """Seagrass on the bed, lily pads on the still water, reeds and ferns at the edge."""
    n = Noise(seed, 2.5)
    for (x, z) in sorted(wet):
        if (x, z) in deck or t.get(x, FLOOR, z) != WATER:
            continue
        top = beds[(x, z)]
        v = n.at(x, z)
        if v > 0.55 and t.get(x, top + 1, z) == WATER:
            if top + 2 <= FLOOR and v > 0.7 and t.get(x, top + 2, z) == WATER:
                t.set(x, top + 1, z, 'minecraft:tall_seagrass[half=lower]')
                t.set(x, top + 2, z, 'minecraft:tall_seagrass[half=upper]')
            else:
                t.set(x, top + 1, z, 'minecraft:seagrass')
        if n.at(x * 2.0, z * 2.0, 5) > 0.66 and t.get(x, FEET, z) in (None, AIR):
            t.set(x, FEET, z, 'minecraft:lily_pad')
    # reeds where the bank meets the water
    for x in range(t.sx):
        for z in range(t.sz):
            if (x, z) in wet or (x, z) in deck:
                continue
            if B.name_of(t.get(x, FLOOR, z)) not in ('minecraft:grass_block', 'minecraft:mud', 'minecraft:coarse_dirt',
                                                      'minecraft:podzol', 'minecraft:gravel', 'minecraft:sand'):
                continue
            if t.get(x, FEET, z) != AIR:
                continue
            touching = any((x + dx, z + dz) in wet for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if touching and n.at(x, z, 9) > 0.5 and B.name_of(t.get(x, FLOOR, z)) != 'minecraft:gravel':
                h = 1 + int(n.at(x, z, 11) * 2.9)
                for y in range(FEET, FEET + h):
                    t.set(x, y, z, 'minecraft:sugar_cane[age=0]')


# ---------------------------------------------------------------- the causeway

def footprint(segs):
    """{(x, z): 'walk' | 'edge'} of a causeway five wide along the axis-aligned centrelines `segs`."""
    deck = {}
    for (x0, z0), (x1, z1) in segs:
        if z0 == z1:
            cells = [((x, z0 + d), abs(d) == 2) for x in range(min(x0, x1), max(x0, x1) + 1) for d in range(-2, 3)]
        else:
            cells = [((x0 + d, z), abs(d) == 2) for z in range(min(z0, z1), max(z0, z1) + 1) for d in range(-2, 3)]
        for c, edge in cells:
            if deck.get(c) != 'walk':
                deck[c] = 'edge' if edge else 'walk'
    # a cell on the inside of a bend is walk if any segment walks it
    return deck


def causeway(t, k, deck, beds, broken, seed, parapet=0.85, posts=8, ends=()):
    """The dyke under the deck from the bed up (mossy below the waterline), the paving on top, the curbs and
    their parapet (kept where the noise is under `parapet`), lantern posts every `posts` cells along the
    curbs; `broken` cells are down: water through them, their stones on the bed. `ends`: cells with no
    parapet (the landings)."""
    n = Noise(seed, 2.0)
    pav = WATCH.patches('floor', seed + 1, 2.5)
    for (x, z), role in sorted(deck.items()):
        bed = beds.get((x, z))
        if (x, z) in broken:
            if bed is not None:
                if n.at(x, z, 3) > 0.45:
                    t.set(x, bed + 1, z, K.pickw(K.RUBBLE_LOOSE, n._h(x, 1, z)).replace('waterlogged=false', 'waterlogged=true'))
                    if bed + 2 <= FLOOR and n.at(x, z, 4) > 0.6:
                        t.set(x, bed + 1, z, K.pickw(BELOW, n._h(x, 2, z)))
                        t.set(x, bed + 2, z, K.pickw(K.RUBBLE_LOOSE, n._h(x, 3, z)).replace('waterlogged=false', 'waterlogged=true'))
            continue
        lo = (bed + 1) if bed is not None else FLOOR
        for y in range(lo, FEET):
            t.set(x, y, z, K.pickw(BELOW, n._h(x, y, z)) if y <= FLOOR else k.wall(x, y, z))
        if role == 'walk':
            t.set(x, FEET, z, pav(x, FEET, z))
        else:
            t.set(x, FEET, z, 'minecraft:polished_andesite' if n.at(x, z) > 0.4 else k.wall(x, FEET, z))
            if (x, z) not in ends and n.at(x * 1.7, z * 1.7, 7) < parapet:
                t.set(x, FEET + 1, z, 'minecraft:stone_brick_wall' if n.at(x, z, 8) > 0.45 else 'minecraft:mossy_stone_brick_wall')
    # the broken edges crumble: a cell or two of the paving at a break's lip goes to slabs
    for (x, z) in sorted(broken):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c = (x + dx, z + dz)
            if c in deck and c not in broken and n.at(*c, 6) > 0.5:
                t.set(c[0], FEET, c[1], slab('oldrepublic:watch_stone_slab'))
                t.set(c[0], FEET + 1, c[1], AIR)
    # lantern posts
    edges = sorted(c for c, r in deck.items() if r == 'edge' and c not in broken and c not in ends)
    for (x, z) in edges:
        if (x + z) % posts == 0 and t.get(x, FEET + 1, z) not in (None, AIR):
            t.set(x, FEET + 1, z, 'minecraft:stone_brick_wall')
            t.set(x, FEET + 2, z, 'minecraft:stone_brick_wall')
            K.lantern(t, x, FEET + 3, z)


def bank_stones(t, k, cells, seed):
    """Two field stones guarding a landing."""
    rng = t.rng
    for i, (x, z) in enumerate(cells):
        field_stone(t, [(x, z)], FEET, [3 + i % 2], rng, top='stair', mossy=0.5)


# ---------------------------------------------------------------- the shrine

def shrine(t, k, c, base, seed, whole=True):
    """A small Watch shrine: a square of four corner piers with round arches between them on every side, a
    tiled pyramid roof (whole), the meditation stone on a dais in the middle, candles, the chest at the back
    (east). Not whole: the roof is down inside it, two piers stand as stumps."""
    cx, cz = c
    x0, z0, x1, z1 = cx - 3, cz - 3, cx + 3, cz + 3
    n = Noise(seed, 2.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(FLOOR, base):
                t.set(x, y, z, K.pickw(BELOW, n._h(x, y, z)))
            t.set(x, base, z, 'minecraft:polished_andesite' if (x + z) % 2 else 'oldrepublic:watch_stone')
            for y in range(base + 1, base + 12):
                t.set(x, y, z, AIR)
    # steps up on all four sides
    for side in K.DIRS:
        dx, dz = K.STEP[side]
        for (x, z) in K.side_cells((x0, z0, x1, z1), side):
            px, pz = x + dx, z + dz
            if t.get(px, base, pz) in (None, AIR) or B.name_of(t.get(px, base, pz)) in K.GROUND_NAMES:
                t.set(px, base, pz, stair('oldrepublic:watch_stone_stairs', B.opposite(side)))
    top = base + 5
    piers = [(x0, z0), (x1, z0), (x0, z1), (x1, z1)]
    stumps = {(x1, z0), (x0, z1)} if not whole else set()
    for (px, pz) in piers:
        h = top if (px, pz) not in stumps else base + 1 + int(2 * n.at(px, pz))
        for y in range(base + 1, h + 1):
            t.set(px, y, pz, 'minecraft:chiseled_stone_bricks' if y == top else k.wall(px, y, pz))
    if whole:
        # arches between the piers: a round head of stairs under the entablature
        for side in K.DIRS:
            cells = K.side_cells((x0, z0, x1, z1), side)[1:-1]
            ax, az = K.along(side)
            first, last = ('west', 'east') if ax else ('north', 'south')
            for i, (x, z) in enumerate(cells):
                if i == 0:
                    t.set(x, top - 1, z, stair('oldrepublic:watch_stone_stairs', first, 'top'))
                elif i == len(cells) - 1:
                    t.set(x, top - 1, z, stair('oldrepublic:watch_stone_stairs', last, 'top'))
                t.set(x, top, z, WATCH['trim'])
        # the roof: a corbel course, then a pyramid of tiles to a lantern finial
        for (x, z) in K.perimeter((x0 - 1, z0 - 1, x1 + 1, z1 + 1)):
            side = 'north' if z == z0 - 1 else 'south' if z == z1 + 1 else 'west' if x == x0 - 1 else 'east'
            t.set(x, top, z, stair('oldrepublic:watch_stone_stairs', B.opposite(side), 'top'))
        y = top + 1
        i = 0
        while True:
            r = (x0 - 1 + i, z0 - 1 + i, x1 + 1 - i, z1 + 1 - i)
            if r[0] > r[2]:
                break
            if r[0] == r[2]:
                t.set(r[0], y, r[1], 'minecraft:chiseled_stone_bricks')
                K.lantern(t, r[0], y + 1, r[1])
                break
            for (x, z) in K.perimeter(r):
                side = 'north' if z == r[1] else 'south' if z == r[3] else 'west' if x == r[0] else 'east'
                t.set(x, y, z, stair(k.roof_tile(x, y, z, 0.06 if side == 'north' else 0.0), B.opposite(side)))
            for (x, z) in K.inside(r):
                if y > top + 1:
                    t.set(x, y, z, 'minecraft:deepslate_tiles')
            y += 1
            i += 1
        K.hang_lantern(t, cx, top - 2, cz - 2, 4)
    else:
        # the roof came down inside: its tiles and the entablature's stones over the floor
        k.rubble({(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)} - set(piers),
                 lambda x, z: base + 1, density=0.55, seed=37)
        for (x, z) in ((x0 + 1, z1 + 1), (x1 + 1, z0 + 2), (x1 + 2, z0 + 3)):
            t.set(x, base + 1, z, stair(k.roof_tile(x, base + 1, z, 0.2), K.DIRS[(x + z) % 4]))
    # the dais, the meditation stone, candles; the chest at the back
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            t.set(x, base + 1, z, AIR)
    t.set(cx, base + 1, cz, 'oldrepublic:meditation_stone')
    for (x, z) in ((cx - 1, cz - 1), (cx + 1, cz + 1)):
        K.candle(t, x, base + 1, z, n=2 + (x + z) % 2, color='white', lit=False)
    t.chest(x1 - 1, base + 1, cz, facing='west', loot=LOOT)
    t.set(x1 - 1, base + 1, cz - 1, 'minecraft:decorated_pot[cracked=false,facing=west,waterlogged=false]')
    return (x0, z0, x1, z1)


# ---------------------------------------------------------------- isle

class Isle:
    SX, SY, SZ = 55, 21, 25
    ISLAND = (44, 12, 6.8)
    SHRINE = (45, 12)
    SEGS = [((3, 12), (39, 12))]
    BROKEN_X = ((15, 16), (26, 28))


def build_isle():
    P = Isle
    seed = SEED
    t = Template(P.SX, P.SY, P.SZ, seed=seed, name='selvaren/tarn_causeway/isle')
    k = K.Kit(t, seed)
    n = Noise(seed + 3, 4.0)
    ix, iz, ir = P.ISLAND

    def is_wet(x, z):
        if x < 6 + 2 * n.at(0, z) or x > P.SX - 4 - 2 * n.at(1, z) or z < 2 + n.at(x, 0) * 1.5 or z > P.SZ - 3 - n.at(x, 1) * 1.5:
            return False
        return math.hypot(x - ix, z - iz) > ir + (n.at(x, z, 5) - 0.5) * 2.0
    wet = {(x, z) for x in range(P.SX) for z in range(P.SZ) if is_wet(x, z)}
    beds = tarn(t, wet, seed + 11)
    cols = {(x, z) for x in range(P.SX) for z in range(P.SZ)}
    clear_bowl(t, cols, P.SX // 2, P.SZ // 2, FEET, P.SY - 1, r_full=22.0, r_out=30.0, rim_h=4)
    # the island rises a cell over the water in its middle
    for x in range(P.SX):
        for z in range(P.SZ):
            if (x, z) not in wet and math.hypot(x - ix, z - iz) < ir - 1.5:
                t.set(x, FLOOR, z, 'minecraft:dirt')
                t.set(x, FEET, z, 'minecraft:grass_block[snowy=false]' if n.at(x, z) < 0.7 else 'minecraft:moss_block')
    deck = footprint(P.SEGS)
    broken = {(x, z) for (x, z) in deck for (a, b) in P.BROKEN_X if a <= x <= b}
    ends = {(x, z) for (x, z) in deck if x <= 5 or x >= 37}
    silver_tree(t, 41, FEET + 1, 15, 10, seed + 31, boughs=3, spread=2.4)
    causeway(t, k, deck, beds, broken, seed + 21, parapet=0.8, posts=8, ends=ends)
    # the landing: two steps down to the bank, the guardian stones
    for z in range(11, 14):
        t.set(2, FLOOR, z, 'minecraft:polished_andesite')
        t.set(3, FEET, z, stair('oldrepublic:watch_stone_stairs', 'east'))
    bank_stones(t, k, [(2, 9), (2, 15)], seed)
    shrine(t, k, P.SHRINE, FEET, seed + 41, whole=True)
    # the island's path from the causeway to the shrine's steps
    for x in range(38, 42):
        for z in range(11, 14):
            if t.get(x, FEET + 1, z) in (None, AIR) and B.name_of(t.get(x, FEET, z)) in K.GROUND_NAMES:
                t.set(x, FEET, z, 'minecraft:dirt_path' if (x + z) % 3 else 'minecraft:coarse_dirt')
    water_life(t, wet, beds, seed + 51, deck=set(deck))
    weather(t, seed=seed + 61, patchiness=0.6)
    plants(t, cols - wet - set(deck), 'highwood', 0.4, seed + 71, y_hi=FEET + 1)
    mod.light_fill(t, (41, FEET + 1, 8, 49, FEET + 4, 16), level=8, strength=12)
    return t


# ---------------------------------------------------------------- broken

class Broken:
    SX, SY, SZ = 49, 19, 31
    ISLAND = (38, 10, 7.0)
    SHRINE = (39, 10)
    SEGS = [((12, 28), (12, 10)), ((12, 10), (32, 10))]
    GAP = {(x, z) for x in range(15, 20) for z in range(8, 13)}     # the span at the bend, gone
    SNAG = (26, 18)


def build_broken():
    P = Broken
    seed = SEED + 1000
    t = Template(P.SX, P.SY, P.SZ, seed=seed, name='selvaren/tarn_causeway/broken')
    k = K.Kit(t, seed)
    n = Noise(seed + 3, 4.0)
    ix, iz, ir = P.ISLAND

    def is_wet(x, z):
        if x < 3 + 2 * n.at(0, z) or x > P.SX - 4 - 2 * n.at(1, z) or z < 2 + n.at(x, 0) * 1.5 or z > P.SZ - 5 - n.at(x, 1) * 2.0:
            return False
        return math.hypot(x - ix, z - iz) > ir + (n.at(x, z, 5) - 0.5) * 2.0
    wet = {(x, z) for x in range(P.SX) for z in range(P.SZ) if is_wet(x, z)}
    beds = tarn(t, wet, seed + 11)
    cols = {(x, z) for x in range(P.SX) for z in range(P.SZ)}
    clear_bowl(t, cols, P.SX // 2, P.SZ // 2, FEET, P.SY - 1, r_full=20.0, r_out=28.0, rim_h=4)
    for x in range(P.SX):
        for z in range(P.SZ):
            if (x, z) not in wet and math.hypot(x - ix, z - iz) < ir - 1.5:
                t.set(x, FLOOR, z, 'minecraft:dirt')
                t.set(x, FEET, z, 'minecraft:grass_block[snowy=false]' if n.at(x, z) < 0.7 else 'minecraft:moss_block')
    deck = footprint(P.SEGS)
    broken = {c for c in deck if c in P.GAP}
    broken |= {(x, z) for (x, z) in deck if 20 <= z <= 21}               # and a small break on the south arm
    ends = {(x, z) for (x, z) in deck if z >= 27 or x >= 31}
    causeway(t, k, deck, beds, broken, seed + 21, parapet=0.35, posts=11, ends=ends)
    # stepping stones over the gap: fallen blocks whose tops stand at the water
    for (x, z) in ((15, 10), (17, 11), (19, 10)):
        bed = beds.get((x, z))
        if bed is None:
            continue
        for y in range(bed + 1, FLOOR + 1):
            t.set(x, y, z, K.pickw(BELOW, n._h(x, y, z)))
        t.set(x, FEET, z, slab('minecraft:mossy_stone_brick_slab'))
    # the landing on the south bank
    for x in range(11, 14):
        t.set(x, FLOOR, 30, 'minecraft:polished_andesite')
        if t.get(x, FEET, 29) in (None, AIR):
            t.set(x, FEET, 29, stair('oldrepublic:watch_stone_stairs', 'north'))
    bank_stones(t, k, [(9, 29), (15, 29)], seed)
    shrine(t, k, P.SHRINE, FEET, seed + 41, whole=False)
    # the drowned tree: a grey snag standing in the water
    sx, sz = P.SNAG
    bed = beds.get((sx, sz))
    if bed is not None:
        for y in range(bed + 1, FEET + 9):
            t.set(sx, y, sz, 'minecraft:stripped_birch_log[axis=y]')
        t.set(sx + 1, FEET + 5, sz, 'minecraft:stripped_birch_log[axis=x]')
        t.set(sx + 2, FEET + 6, sz, 'minecraft:stripped_birch_log[axis=x]')
        t.set(sx, FEET + 7, sz - 1, 'minecraft:stripped_birch_log[axis=z]')
        t.set(sx - 1, FEET + 4, sz, 'minecraft:stripped_birch_log[axis=x]')
    silver_tree(t, 35, FEET + 1, 14, 9, seed + 31, boughs=2, spread=2.3)
    for (x, z) in sorted(deck):
        if z < 14 and x > 30 and t.get(x, FEET + 1, z) in (None, AIR) and B.name_of(t.get(x, FEET, z)) in K.GROUND_NAMES:
            t.set(x, FEET, z, 'minecraft:dirt_path')
    water_life(t, wet, beds, seed + 51, deck=set(deck))
    weather(t, seed=seed + 61, patchiness=0.8)
    plants(t, cols - wet - set(deck), 'highwood', 0.45, seed + 71, y_hi=FEET + 1)
    mod.light_fill(t, (35, FEET + 1, 6, 43, FEET + 3, 14), level=8, strength=12)
    return t


# ---------------------------------------------------------------- worldgen

def worldgen(templates):
    W.pool('selvaren/tarn_causeway/start', [W.element('selvaren/tarn_causeway/' + name, adapted=dict(
        yard_stand=FEET, clear_from=sy, clear_inset=2, support_depth=24, ramp_rings=0)) for name, sy in templates])
    W.structure('selvaren/tarn_causeway', start_pool='selvaren/tarn_causeway/start',
                biome_tag='has_structure/selvaren/tarn_causeway', size=1, max_distance=48,
                step='surface_structures', start_height=1 - BURIED)
    W.structure_set('selvaren/tarn_causeway', [('selvaren/tarn_causeway', 1)], spacing=36, separation=12,
                    salt=960201, placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/selvaren/tarn_causeway', ['oldrepublic:selvaren_tarn'])


def loot():
    loot_table(LOOT, [
        (1, [('oldrepublic:lumen_thread', 1, 2, 1)], None),
        (1, [('candle', 1, 3, 1)], None),
        ((1, 3), [('emerald', 1, 2, 3), ('oldrepublic:aurelite_shard', 1, 2, 2), ('experience_bottle', 1, 3, 3),
                  ('amethyst_shard', 1, 3, 2), ('paper', 1, 3, 2), ('golden_apple', 1, 1, 1)], None),
    ])


# Set blocks that flowing water washes away (they do not block motion), by a part of their name; the exceptions are
# full blocks whose names share a part. Sugar cane, ladders, doors and signs hold water although they are soft.
SOFT = ('short_grass', 'tall_grass', 'dry_grass', 'fern', 'bush', 'leaf_litter', 'wildflowers', 'petals', 'lily_pad',
        'lantern', 'candle', 'chain', 'rail', 'carpet', 'torch', 'sapling', 'mushroom', 'allium', 'cornflower',
        'lily_of_the_valley', 'poppy', 'dandelion', 'tulip', 'orchid', 'bluet', 'daisy', 'vine', 'lichen',
        'pressure_plate', 'button', 'flower_pot', 'potted_')
NOT_SOFT = ('minecraft:jack_o_lantern', 'minecraft:sea_lantern', 'minecraft:brown_mushroom_block',
            'minecraft:red_mushroom_block', 'minecraft:mushroom_stem')
HOLDS = ('minecraft:sugar_cane', 'minecraft:ladder')


def check_water(t):
    """The tarn must hold its water: no water cell may touch, sideways or below, the template's edge, a cell it
    leaves to the world, air or a block water washes away; only water, waterlogged blocks and solid ones hold it."""
    def wet(s):
        return s is not None and (B.name_of(s) in (WATER, 'minecraft:seagrass', 'minecraft:tall_seagrass')
                                  or 'waterlogged=true' in s)

    def holds(q):
        s = t.get(*q)
        if s is None:
            return False
        name = B.name_of(s)
        if wet(s) or name in HOLDS:
            return True
        return t.is_solid(*q) and (name in NOT_SOFT or not any(part in name for part in SOFT))

    leaks = []
    for (x, y, z), s in sorted(t.cells.items()):
        if wet(s):
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
                q = (x + dx, y + dy, z + dz)
                if not t.inside(*q) or not holds(q):
                    leaks.append(q)
    if leaks:
        raise SystemExit('%s: the tarn leaks at %r' % (t.name, leaks[:6]))


def main():
    loot()
    t = build_isle()
    t.finish()
    check_chests(t)
    check_water(t)
    cx, cz = Isle.SHRINE
    check_reach(t, FEET, dict(chests(t), stone=[(cx, FEET + 1, cz)]))
    save(t, 'selvaren/tarn_causeway/isle', 'tarn_causeway_isle')
    t = build_broken()
    t.finish()
    check_chests(t)
    check_water(t)
    cx, cz = Broken.SHRINE
    check_reach(t, FEET, dict(chests(t), stone=[(cx, FEET + 1, cz)]))
    save(t, 'selvaren/tarn_causeway/broken', 'tarn_causeway_broken')
    worldgen([('isle', Isle.SY), ('broken', Broken.SY)])


if __name__ == '__main__':
    main()
