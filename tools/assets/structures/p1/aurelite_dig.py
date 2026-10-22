"""selvaren/aurelite_dig: a small aurelite mine into a highwood crag (docs/MILESTONE_6.md 5.5, the stretch),
two variants.

Aurelite comes out of the highwood's crags in thin gold-green veins. The diggers worked a crag a season or
two and moved on; their timber still holds.

Each template carries its own crag (a knoll of stone, steep on its south face), so it reads the same on a
slope or on the flat.

- `adit` (29 x 22 x 29): a timbered adit into the crag's face, sets every three cells, rails down its
  middle; a drift each side and a chamber at the end with the veins showing in their walls; outside, the
  spoil heap beside the track, the diggers' shed with the chest.
- `face` (27 x 22 x 31): an open cut stepped into the crag in three benches, veins in the faces, a timber
  crane with its bucket on the chain, cut stone stacked; a short adit at the foot; the diggers' hut gone
  roofless with the chest still in it.

Worldgen: pool selvaren/aurelite_dig/start (both, adapted terrain), structure selvaren/aurelite_dig in the
highwood, set 44/16, salt 960204, clear of the origin.
"""
import math

from selvaren_lib import (AIR, B, Noise, S, Template, W, check_chests, check_reach, clear_bowl, ground, loot_table,
                          mod, nature, plants, save, silver_tree, slab, stair, weather)
import watchkit as K

SEED = 960204
BURIED = 3
FLOOR = BURIED - 1
FEET = BURIED
LOOT = 'chests/selvaren/aurelite_dig'

ROCK = [('minecraft:stone', 6), ('minecraft:andesite', 3), ('minecraft:tuff', 2), ('minecraft:cobblestone', 1)]
ORE = 'oldrepublic:aurelite_ore'
RAIL_NS = 'minecraft:rail[shape=north_south,waterlogged=false]'
POST = 'minecraft:stripped_spruce_log[axis=y]'


def chests(t):
    return {'chest %d,%d,%d' % p: [p] for p, st in sorted(t.cells.items()) if B.name_of(st) == 'minecraft:chest'}


def crag(t, seed, face_z, peak, cx, back=6.0, width=None):
    """A knoll of rock over the template's north part: highest behind its steep south face (z = face_z(x)),
    falling to nothing at the west, east and north edges; turf and moss on top. Returns {(x, z): top y}."""
    n = Noise(seed, 3.0)
    width = width or t.sx / 2.0
    tops = {}
    for x in range(t.sx):
        fz = face_z(x)
        for z in range(t.sz):
            if z > fz:
                continue
            hx = max(0.0, 1.0 - (abs(x - cx) / width) ** 2)
            hz = min(1.0, (z + 0.5) / back)
            h = int(round(peak * hx * hz + (n.at(x, z) - 0.5) * 3.0))
            if h < 1:
                continue
            top = FLOOR + h
            for y in range(FLOOR + 1, top + 1):
                t.set(x, y, z, K.pickw(ROCK, 0.7 * n.at(x, y, z) + 0.3 * n._h(x, y, z)))
            tops[(x, z)] = top
    # turf on the flatter tops, moss and coarse earth on the edges; the face stays bare
    for (x, z), top in tops.items():
        flat = all(abs(tops.get((x + dx, z + dz), top) - top) <= 1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if (x, z + 1) not in tops:
            continue
        v = n.at(x, z, 7)
        if flat:
            t.set(x, top, z, 'minecraft:grass_block[snowy=false]' if v < 0.6 else
                  'minecraft:podzol[snowy=false]' if v < 0.75 else 'minecraft:moss_block')
        elif v > 0.5:
            t.set(x, top, z, 'minecraft:mossy_cobblestone' if v > 0.7 else 'minecraft:coarse_dirt')
    return tops


def veins(t, cells, seed, density=0.35):
    """Aurelite in the rock walls next to the open cells: a vein follows the noise, so the ore comes in runs."""
    n = Noise(seed, 1.8)
    for (x, y, z) in sorted(cells):
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0)):
            q = (x + dx, y + dy, z + dz)
            s = t.get(*q)
            if s is None or B.name_of(s) not in ('minecraft:stone', 'minecraft:andesite', 'minecraft:tuff',
                                                  'minecraft:cobblestone'):
                continue
            if n.at(*q) > 1.0 - density:
                t.set(*q, ORE)


def timber_set(t, x0, x1, z, y0, h):
    """A mine set across a tunnel: posts in the side cells, a cap beam over them."""
    for x in (x0, x1):
        for y in range(y0, y0 + h):
            t.set(x, y, z, POST)
    for x in range(x0, x1 + 1):
        t.set(x, y0 + h, z, 'minecraft:spruce_log[axis=x]')


def bore(t, cells, keep_floor=True):
    """Opens the cells (explicit air), the floor under them rough stone or earth."""
    opened = []
    for (x, y, z) in cells:
        t.set(x, y, z, AIR)
        opened.append((x, y, z))
    return opened


# ---------------------------------------------------------------- adit

class Adit:
    SX, SY, SZ = 29, 22, 29
    CX = 14
    FACE = 16                      # the crag's south face (roughly)
    SHED = (19, 20, 26, 25)


def build_adit():
    P = Adit
    seed = SEED
    t = Template(P.SX, P.SY, P.SZ, seed=seed, name='selvaren/aurelite_dig/adit')
    k = K.Kit(t, seed)
    cols = {(x, z) for x in range(P.SX) for z in range(P.SZ)}
    ground(t, cols, FLOOR, seed + 11, kind='highwood')
    clear_bowl(t, cols, 14, 18, FEET, P.SY - 1, r_full=12.0, r_out=20.0, rim_h=4)
    fn = Noise(seed + 13, 3.0)
    tops = crag(t, seed + 15, lambda x: P.FACE + int(2.5 * fn.at(x, 0)) - (1 if abs(x - P.CX) < 3 else 0),
                peak=15, cx=P.CX, back=3.5, width=15.0)
    silver_tree(t, 9, max(tops.get((9, 7), FLOOR), FLOOR) + 1, 7, 9, seed + 31, boughs=2, spread=2.2)
    silver_tree(t, 21, max(tops.get((21, 5), FLOOR), FLOOR) + 1, 5, 8, seed + 33, boughs=2, spread=2.0)
    # the adit: five wide between its sets, four high, north from the face to the chamber
    opened = []
    for z in range(8, P.FACE + 4):
        for x in range(P.CX - 2, P.CX + 3):
            for y in range(FEET, FEET + 4):
                if t.get(x, y, z) not in (None, AIR) or z <= P.FACE + 3:
                    t.set(x, y, z, AIR)
                    opened.append((x, y, z))
            t.set(x, FLOOR, z, 'minecraft:gravel' if (x + z) % 4 == 0 else 'minecraft:packed_mud')
    # the drifts east and west at z 9, and the chamber at the end
    for x in list(range(P.CX - 9, P.CX - 2)) + list(range(P.CX + 3, P.CX + 10)):
        for z in range(11, 14):
            for y in range(FEET, FEET + 3):
                t.set(x, y, z, AIR)
                opened.append((x, y, z))
            t.set(x, FLOOR, z, 'minecraft:packed_mud' if (x + z) % 3 else 'minecraft:gravel')
    for x in range(P.CX - 4, P.CX + 5):
        for z in range(4, 8):
            for y in range(FEET, FEET + 5):
                t.set(x, y, z, AIR)
                opened.append((x, y, z))
            t.set(x, FLOOR, z, 'minecraft:packed_mud')
    veins(t, opened, seed + 41, density=0.3)
    # the sets, every three cells down the adit; a frame at the portal
    for z in range(9, P.FACE + 3, 3):
        timber_set(t, P.CX - 2, P.CX + 2, z, FEET, 3)
    fz = P.FACE + 1
    timber_set(t, P.CX - 2, P.CX + 2, fz + 2, FEET, 3)
    for x in range(P.CX - 3, P.CX + 4):
        t.set(x, FEET + 4, fz + 2, 'minecraft:spruce_slab[type=bottom,waterlogged=false]')
    # rails down the middle and out to the tip
    for z in range(5, P.FACE + 4):
        t.set(P.CX, FEET, z, RAIL_NS)
    t.set(P.CX, FEET, P.FACE + 4, 'minecraft:rail[shape=north_west,waterlogged=false]')
    for x in range(P.CX - 8, P.CX):
        t.set(x, FEET, P.FACE + 4, 'minecraft:rail[shape=east_west,waterlogged=false]')
    # the chamber: a fall of rock in one corner, a lantern on a post, a cart's load in a barrel
    k.rubble({(x, z) for x in range(P.CX + 1, P.CX + 5) for z in range(4, 7)}, lambda x, z: FEET, density=0.9, seed=5)
    for (x, z, h) in ((P.CX + 3, 4, 3), (P.CX + 4, 4, 4), (P.CX + 4, 5, 2), (P.CX + 3, 5, 1)):
        for y in range(FEET, FEET + h):
            t.set(x, y, z, K.pickw(ROCK, (x * 7 + y * 3 + z) % 10 / 10.0))
    t.set(P.CX - 3, FEET, 5, POST)
    t.set(P.CX - 3, FEET + 1, 5, POST)
    K.lantern(t, P.CX - 3, FEET + 2, 5)
    t.set(P.CX, FEET, 4, 'minecraft:barrel[facing=up,open=true]')
    t.set(P.CX - 1, FEET, 4, 'minecraft:stone_slab[type=bottom,waterlogged=false]')
    for (x, z) in ((P.CX - 7, 12), (P.CX + 7, 12)):
        K.lantern(t, x, FEET, z + 1)
    for z in (11, 17):
        K.hang_lantern(t, P.CX, FEET + 2, z - 1, 2)
    # outside: the spoil heap on the west (the track's row kept clear), the shed on the east
    hn = Noise(seed + 51, 2.5)
    for x in range(0, P.CX - 3):
        for z in range(P.FACE + 2, P.SZ - 2):
            if abs(z - (P.FACE + 4)) <= 1:
                continue
            d = math.hypot((x - 4) / 1.2, z - (P.FACE + 8))
            h = int(round(4.2 - d * 0.75 + hn.at(x, z) * 1.2))
            if h <= 0 or (x, z) in tops:
                continue
            for y in range(FEET, FEET + h):
                t.set(x, y, z, K.pickw([('minecraft:gravel', 4), ('minecraft:cobblestone', 2), ('minecraft:andesite', 2),
                                        ('minecraft:tuff', 1)], hn._h(x, y, z)))
    t.set(P.CX - 9, FEET, P.FACE + 4, 'minecraft:cobblestone_wall')
    shed(t, k, P.SHED, seed)
    # the track's ground worn to earth
    for x in range(P.CX - 3, P.CX + 4):
        for z in range(P.FACE + 4, P.SZ):
            if t.get(x, FEET, z) in (None, AIR) and B.name_of(t.get(x, FLOOR, z)) in K.GROUND_NAMES:
                t.set(x, FLOOR, z, 'minecraft:coarse_dirt' if (x * 3 + z) % 5 else 'minecraft:gravel')
    weather(t, seed=seed + 61, patchiness=0.5)
    nature.overgrow(t, region=((0, FEET, 0), (P.SX - 1, P.SY - 1, P.SZ - 1)), vines=0.06, moss=0.0, lichen=0.0,
                    wall_names=['minecraft:stone', 'minecraft:andesite', 'minecraft:tuff', 'minecraft:cobblestone'])
    plants(t, cols, 'highwood', 0.4, seed + 71)
    mod.light_fill(t, (P.CX - 10, FEET, 1, P.CX + 10, FEET + 4, P.FACE + 4), level=8, strength=12)
    return t


def shed(t, k, r, seed, roofed=True):
    """The diggers' shed: spruce posts and plank walls on a stone sill, a lean-to roof; the chest, a grindstone,
    a stonecutter, a barrel, a lantern."""
    x0, z0, x1, z1 = r
    for (x, z) in K.perimeter(r):
        t.set(x, FLOOR, z, 'minecraft:cobblestone')
        corner = x in (x0, x1) and z in (z0, z1)
        for y in range(FEET, FEET + 3):
            t.set(x, y, z, POST if corner else 'minecraft:spruce_planks' if y < FEET + 2 or (x + z) % 2 else
                  'minecraft:stripped_spruce_wood[axis=y]')
    for (x, z) in K.inside(r):
        t.set(x, FLOOR, z, 'minecraft:spruce_planks')
        for y in range(FEET, FEET + 4):
            t.set(x, y, z, AIR)
    # the door on the west (toward the adit), a window east
    for y in range(FEET, FEET + 2):
        t.set(x0, y, (z0 + z1) // 2, AIR)
    t.set(x1, FEET + 1, (z0 + z1) // 2, S('minecraft:glass_pane', **K.pane_props('east')))
    if roofed:
        cells = {(x, z): (z1 + 1 - z) // 2 for x in range(x0 - 1, x1 + 2) for z in range(z0 - 1, z1 + 2)}
        for (x, z), kk in sorted(cells.items()):
            t.set(x, FEET + 3 + kk, z, stair('minecraft:spruce_stairs', 'north'))
            for y in range(FEET + 3, FEET + 3 + kk):
                if (x, z) in set(K.perimeter(r)):
                    t.set(x, y, z, 'minecraft:spruce_planks')
        K.hang_lantern(t, (x0 + x1) // 2, FEET + 2, (z0 + z1) // 2, 2)
    t.chest(x1 - 1, FEET, z0 + 1, facing='south', loot=LOOT)
    t.set(x1 - 2, FEET, z0 + 1, 'minecraft:barrel[facing=up,open=false]')
    t.set(x0 + 1, FEET, z1 - 1, S('minecraft:grindstone', face='floor', facing='east'))
    t.set(x0 + 2, FEET, z1 - 1, S('minecraft:stonecutter', facing='north'))
    t.set(x1 - 1, FEET, z1 - 1, 'minecraft:crafting_table')


# ---------------------------------------------------------------- face

class Face:
    SX, SY, SZ = 27, 22, 31
    CX = 13
    FACE = 17
    HUT = (1, 21, 7, 27)


def build_face():
    P = Face
    seed = SEED + 1000
    t = Template(P.SX, P.SY, P.SZ, seed=seed, name='selvaren/aurelite_dig/face')
    k = K.Kit(t, seed)
    cols = {(x, z) for x in range(P.SX) for z in range(P.SZ)}
    ground(t, cols, FLOOR, seed + 11, kind='highwood')
    clear_bowl(t, cols, 13, 19, FEET, P.SY - 1, r_full=12.0, r_out=20.0, rim_h=4)
    fn = Noise(seed + 13, 3.0)
    tops = crag(t, seed + 15, lambda x: P.FACE + int(2.0 * fn.at(x, 0)), peak=16, cx=P.CX, back=5.0, width=14.0)
    silver_tree(t, 5, max(tops.get((5, 6), FLOOR), FLOOR) + 1, 6, 8, seed + 31, boughs=2, spread=2.0)
    # the cut: three benches stepping back into the crag, each three high, its floor the next one's sill
    opened = []
    benches = [(FEET, 14), (FEET + 3, 10), (FEET + 6, 7)]       # (floor feet y, back face z)
    for bi, (fy, bz) in enumerate(benches):
        half = 7 - bi
        front = benches[bi - 1][1] if bi else P.SZ
        for x in range(P.CX - half, P.CX + half + 1):
            for z in range(bz, P.SZ):
                if (x, z) not in tops:
                    continue
                for y in range(fy, P.SY):
                    if t.get(x, y, z) not in (None, AIR):
                        t.set(x, y, z, AIR)
                        if y < fy + 3:
                            opened.append((x, y, z))
                if fy > FEET and z < front:
                    t.set(x, fy - 1, z, K.pickw(ROCK, (x * 5 + z) % 10 / 10.0) if (x + z) % 4 else 'minecraft:gravel')
    veins(t, opened, seed + 41, density=0.32)
    # a flight of three steps up to each bench from the one below, on its west side
    for bi in (1, 2):
        fy, bz = benches[bi]
        lower_fy = benches[bi - 1][0]
        lip = benches[bi - 1][1] - 1
        sx = P.CX - (7 - bi) + 1
        for j in range(3):
            z, top = lip + 1 + j, fy - 1 - j
            for y in range(lower_fy, top):
                t.set(sx, y, z, K.pickw(ROCK, (sx + y + z) % 10 / 10.0))
            t.set(sx, top, z, stair('minecraft:cobblestone_stairs', 'north'))
            for y in range(top + 1, top + 4):
                t.set(sx, y, z, AIR)
    # the adit at the foot of the cut, into the back of the lowest bench
    for z in range(5, benches[0][1]):
        for x in range(P.CX - 1, P.CX + 2):
            for y in range(FEET, FEET + 3):
                t.set(x, y, z, AIR)
                opened.append((x, y, z))
        t.set(P.CX, FLOOR, z, 'minecraft:gravel')
    for z in range(6, benches[0][1], 3):
        timber_set(t, P.CX - 1, P.CX + 1, z, FEET, 2)
    veins(t, [(x, y, z) for (x, y, z) in opened if z < benches[0][1]], seed + 43, density=0.4)
    K.lantern(t, P.CX, FEET, 5)
    # the crane on the lowest bench: a mast, a jib, the chain and its bucket
    mx, mz = P.CX + 4, benches[0][1] + 6
    for y in range(FEET, FEET + 9):
        t.set(mx, y, mz, POST)
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set(mx + dx, FEET, mz + dz, stair('minecraft:spruce_stairs', B.opposite('east' if dx > 0 else 'west' if dx < 0 else 'south' if dz > 0 else 'north')))
    for x in range(mx - 5, mx + 2):
        t.set(x, FEET + 8, mz, 'minecraft:spruce_log[axis=x]')
    for y in range(FEET + 3, FEET + 8):
        t.set(mx - 5, y, mz, 'minecraft:iron_chain[axis=y,waterlogged=false]')
    t.set(mx - 5, FEET + 2, mz, 'minecraft:barrel[facing=up,open=true]')
    t.set(mx + 1, FEET + 7, mz, 'minecraft:spruce_fence')
    # cut stone stacked by the track
    for (x, z, h) in ((P.CX + 7, benches[0][1] + 9, 2), (P.CX + 8, benches[0][1] + 9, 1), (P.CX + 7, benches[0][1] + 10, 1)):
        for y in range(FEET, FEET + h):
            t.set(x, y, z, 'minecraft:polished_andesite' if (x + y) % 2 else 'minecraft:smooth_stone')
    # the hut, roofless, the chest still in it
    shed(t, k, P.HUT, seed, roofed=False)
    hx0, hz0, hx1, hz1 = P.HUT
    for (x, z) in K.perimeter(P.HUT):
        if (x + z) % 3 == 0:
            t.set(x, FEET + 2, z, AIR)
    k.rubble(set(K.inside(P.HUT)), lambda x, z: FEET, density=0.3, seed=9)
    weather(t, seed=seed + 61, patchiness=0.6)
    nature.overgrow(t, region=((0, FEET, 0), (P.SX - 1, P.SY - 1, P.SZ - 1)), vines=0.08, moss=0.0, lichen=0.0,
                    wall_names=['minecraft:stone', 'minecraft:andesite', 'minecraft:tuff', 'minecraft:cobblestone'])
    plants(t, cols, 'highwood', 0.4, seed + 71)
    mod.light_fill(t, (P.CX - 3, FEET, 4, P.CX + 3, FEET + 3, benches[0][1]), level=8, strength=12)
    return t


# ---------------------------------------------------------------- worldgen

def worldgen(templates):
    W.pool('selvaren/aurelite_dig/start', [W.element('selvaren/aurelite_dig/' + name, adapted=dict(
        yard_stand=FEET, clear_from=sy, clear_inset=1, support_depth=12, ramp_rings=2)) for name, sy in templates])
    W.structure('selvaren/aurelite_dig', start_pool='selvaren/aurelite_dig/start',
                biome_tag='has_structure/selvaren/aurelite_dig', size=1, max_distance=32,
                step='surface_structures', start_height=1 - BURIED)
    W.structure_set('selvaren/aurelite_dig', [('selvaren/aurelite_dig', 1)], spacing=44, separation=16,
                    salt=960204, placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/selvaren/aurelite_dig', ['oldrepublic:selvaren_highwood'])


def loot():
    loot_table(LOOT, [
        (1, [('oldrepublic:aurelite_shard', 2, 4, 1)], None),
        ((2, 4), [('torch', 2, 6, 4), ('charcoal', 2, 5, 3), ('iron_nugget', 3, 8, 3), ('copper_ingot', 1, 3, 3),
                  ('iron_ingot', 1, 2, 2), ('bread', 1, 3, 3), ('gunpowder', 1, 2, 1), ('emerald', 1, 1, 1)], None),
    ])


def main():
    loot()
    t = build_adit()
    t.finish()
    check_chests(t)
    check_reach(t, FEET, dict(chests(t), chamber=[(Adit.CX - 2, FEET, 6)], west_drift=[(Adit.CX - 8, FEET, 12)],
                              east_drift=[(Adit.CX + 8, FEET, 12)]))
    save(t, 'selvaren/aurelite_dig/adit', 'aurelite_dig_adit')
    t = build_face()
    t.finish()
    check_chests(t)
    check_reach(t, FEET, dict(chests(t), adit=[(Face.CX, FEET, 6)], top_bench=[(Face.CX, FEET + 6, 9)]))
    save(t, 'selvaren/aurelite_dig/face', 'aurelite_dig_face')
    worldgen([('adit', Adit.SY), ('face', Face.SY)])


if __name__ == '__main__':
    main()
