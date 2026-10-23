"""selvaren/hollowhorn_fold: a herders' stone fold and byre in the meadows (docs/MILESTONE_6.md 5.5), two
variants, each with a `hollowhorns` site spawner: the herders are gone, the hollowhorns still come in.

Herders' building, not the Watch's: field stone laid dry, spruce, hay, turf.

- `fold` (37 x 15 x 35): a great oval fold of drystone with three pens inside, its gate to the south; the
  byre along its north side with stalls and a hay loft, a turf-roofed bothy with the herders' chest, a
  row of water troughs, a rubbing stone.
- `steading` (33 x 16 x 39): a square fold of four pens, its walls broken where the beasts pushed out, a
  silver tree fallen across it; the old two-storey byre with half its roof gone and the chest under the
  loft; the herders' cairn.

Worldgen: pool selvaren/hollowhorn_fold/start (both, adapted terrain), structure selvaren/hollowhorn_fold in
the meadow, set 36/12, salt 960203, clear of the origin.
"""
import math

from selvaren_lib import (AIR, B, FIELD, Noise, S, Template, W, check_chests, check_reach, clear_bowl, ground,
                          loot_table, mod, nature, plants, save, silver_tree, slab, stair, weather, field_stone)
import watchkit as K

SEED = 960203
BURIED = 3
FLOOR = BURIED - 1
FEET = BURIED
LOOT = 'chests/selvaren/hollowhorn_fold'

DRY = [('minecraft:cobblestone', 4), ('minecraft:mossy_cobblestone', 4), ('minecraft:stone', 2),
       ('minecraft:andesite', 2), ('minecraft:tuff', 1)]
COPE = [('minecraft:cobblestone_slab[type=bottom,waterlogged=false]', 3),
        ('minecraft:mossy_cobblestone_slab[type=bottom,waterlogged=false]', 3),
        ('minecraft:stone_slab[type=bottom,waterlogged=false]', 1),
        ('minecraft:andesite_slab[type=bottom,waterlogged=false]', 1)]


def chests(t):
    return {'chest %d,%d,%d' % p: [p] for p, st in sorted(t.cells.items()) if B.name_of(st) == 'minecraft:chest'}


def spawners(t):
    return {'spawner %d,%d,%d' % p: [p] for p, st in sorted(t.cells.items()) if B.name_of(st) == 'oldrepublic:site_spawner'}


def dry(k, x, y, z):
    return K.pickw(DRY, 0.7 * k.field_n.at(x, y, z) + 0.3 * k.field_n._h(x, y, z))


def drystone(t, k, cells, h=2, cope=True, gaps=()):
    """A drystone wall over cells {(x, z)}: h courses of field stone, a cope of rough slabs; gaps left out."""
    for (x, z) in sorted(cells):
        if (x, z) in gaps:
            continue
        for y in range(FEET, FEET + h):
            t.set(x, y, z, dry(k, x, y, z))
        if cope:
            t.set(x, FEET + h, z, K.pickw(COPE, k.field_n._h(x, 99, z)))
        t.set(x, FLOOR, z, 'minecraft:cobblestone')


def oval_ring(cx, cz, rx, rz):
    inside = {(x, z) for x in range(int(cx - rx - 1), int(cx + rx + 2)) for z in range(int(cz - rz - 1), int(cz + rz + 2))
              if ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 <= 1.0}
    ring = {(x, z) for (x, z) in inside
            if any((x + dx, z + dz) not in inside for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    return inside, ring


def grazed(t, cells, seed):
    """Turf the beasts keep short: grass, bare earth and mud by the gate and troughs."""
    n = Noise(seed, 2.5)
    for (x, z) in sorted(cells):
        v = n.at(x, z)
        t.set(x, FLOOR, z, 'minecraft:coarse_dirt' if v > 0.68 else 'minecraft:packed_mud' if v > 0.62 else
              'minecraft:grass_block[snowy=false]')


def straw(t, cells, seed, density=0.3):
    n = Noise(seed, 2.0)
    for (x, z) in sorted(cells):
        if t.get(x, FEET, z) == AIR and n.at(x, z) > 1.0 - density:
            t.set(x, FEET, z, 'minecraft:yellow_carpet')


def stalls(t, x0, x1, z_back, z_front, every=3):
    """Stalls against a back wall: spruce fence partitions every `every` cells, a hay rack in each."""
    for x in range(x0, x1 + 1):
        if (x - x0) % every == 0:
            for z in range(min(z_back, z_front), max(z_back, z_front) + 1):
                t.set(x, FEET, z, 'minecraft:spruce_fence')
        else:
            t.set(x, FEET, z_back, S('minecraft:hay_block', axis='x'))


# ---------------------------------------------------------------- the fold

class Fold:
    SX, SY, SZ = 37, 15, 35
    OVAL = (16, 20, 11.5, 9.5)
    BYRE = (17, 2, 33, 9)
    BOTHY = (2, 3, 9, 9)
    GATE = (15, 16)                # the gate's two cells on the south of the oval
    SPAWN = (17, 21)


def build_fold():
    P = Fold
    seed = SEED
    t = Template(P.SX, P.SY, P.SZ, seed=seed, name='selvaren/hollowhorn_fold/fold')
    k = K.Kit(t, seed)
    k.field_n = Noise(seed + 5, 2.0)
    cols = {(x, z) for x in range(P.SX) for z in range(P.SZ)}
    ground(t, cols, FLOOR, seed + 11, kind='meadow')
    clear_bowl(t, cols, 18, 17, FEET, P.SY - 1, r_full=15.0, r_out=24.0, rim_h=4)
    silver_tree(t, 33, FEET, 30, 10, seed + 31, boughs=2, spread=2.6)
    silver_tree(t, 1, FEET, 31, 8, seed + 33, boughs=2, spread=2.2)
    cx, cz, rx, rz = P.OVAL
    inside, ring = oval_ring(cx, cz, rx, rz)
    grazed(t, inside, seed + 41)
    gate_z = max(z for (x, z) in ring if x == P.GATE[0])
    gate = {(x, gate_z) for x in P.GATE}
    byre_door = {(x, z) for (x, z) in ring if 21 <= x <= 23 and z < cz}
    drystone(t, k, ring, gaps=gate | byre_door)
    for (x, z) in sorted(gate):
        t.set(x, FLOOR, z, 'minecraft:coarse_dirt')
    for (x, z) in ((P.GATE[0] - 1, gate_z), (P.GATE[1] + 1, gate_z)):
        for y in range(FEET, FEET + 3):
            t.set(x, y, z, 'minecraft:stone' if y < FEET + 2 else 'minecraft:mossy_cobblestone')
        t.set(x, FEET + 3, z, slab('minecraft:mossy_cobblestone_slab'))
    t.set(P.GATE[0], FEET, gate_z, S('minecraft:spruce_fence_gate', facing='south', open=True, in_wall=False, powered=False))
    t.set(P.GATE[1], FEET, gate_z, S('minecraft:spruce_fence_gate', facing='south', open=True, in_wall=False, powered=False))
    # pens on the west inside the oval: low walls running in from the ring
    for zz in (17, 23):
        cells = {(x, zz) for x in range(int(cx - rx), 9) if (x, zz) in inside and (x, zz) not in ring}
        drystone(t, k, cells, h=1, gaps={(8, zz)})
    # the troughs along the east inside, the rubbing stone, hay, straw
    for z in (18, 19, 20):
        t.set(25, FEET, z, S('minecraft:water_cauldron', level=3))
    t.set(25, FEET, 17, 'minecraft:mossy_cobblestone_wall')
    t.set(25, FEET, 21, 'minecraft:cobblestone_wall')
    field_stone(t, [(12, 25)], FEET, [3], t.rng, top='stair', mossy=0.6)
    for (x, z, ax) in ((19, 13, 'x'), (20, 13, 'x'), (19, 14, 'z'), (10, 20, 'z')):
        t.set(x, FEET, z, S('minecraft:hay_block', axis=ax))
    t.set(20, FEET + 1, 13, S('minecraft:hay_block', axis='x'))
    straw(t, {c for c in inside if c not in ring}, seed + 43, 0.18)
    mod.spawner(t, P.SPAWN[0], FEET, P.SPAWN[1], 'hollowhorns', count=0, bound=False)
    byre(t, k, P, seed)
    bothy(t, k, P, seed)
    # the track out of the gate, south
    n = Noise(seed + 51, 2.0)
    for z in range(gate_z + 1, P.SZ):
        for x in (P.GATE[0] - 1, P.GATE[0], P.GATE[1], P.GATE[1] + 1):
            if n.at(x, z) > 0.32 and t.get(x, FEET, z) in (None, AIR):
                t.set(x, FLOOR, z, 'minecraft:coarse_dirt' if n.at(x * 2, z * 2) > 0.5 else 'minecraft:dirt_path')
    weather(t, seed=seed + 21, patchiness=0.5)
    plants(t, cols - inside, 'meadow', 0.45, seed + 61, y_hi=FEET)
    plants(t, inside, 'meadow', 0.08, seed + 63, y_hi=FEET)
    mod.light_fill(t, (2, FEET, 2, 33, FEET + 5, 9), level=8, strength=12)
    return t


def byre(t, k, P, seed):
    """The byre: field stone walls, a spruce-shingled roof, stalls along its north wall, the loft at the west
    end with hay, a wide door into the fold."""
    r = P.BYRE
    x0, z0, x1, z1 = r
    h = 4
    for (x, z) in K.perimeter(r):
        for y in range(FLOOR, FEET + h):
            t.set(x, y, z, 'minecraft:cobblestone' if y == FLOOR else dry(k, x, y, z))
    for (x, z) in K.inside(r):
        t.set(x, FLOOR, z, 'minecraft:packed_mud' if (x + z) % 5 else 'minecraft:coarse_dirt')
        for y in range(FEET, FEET + h + 6):
            t.set(x, y, z, AIR)
    # corner stones and a timber wall plate under the eaves
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(FEET, FEET + h):
            t.set(x, y, z, 'minecraft:stone' if (y - FEET) % 2 else 'minecraft:andesite')
    k.gable(r, FEET + h, ridge='x', overhang=1, tiles=lambda x, y, z: 'minecraft:spruce_stairs' if (x * 7 + y * 3) % 9
            else 'minecraft:dark_oak_stairs', ridge_slab='minecraft:dark_oak_slab',
            end_wall=lambda x, y, z: 'minecraft:spruce_planks' if (x + y) % 3 else 'minecraft:stripped_spruce_log[axis=y]')
    for x in range(x0, x1 + 1):
        t.set(x, FEET + h - 1, z0, 'minecraft:stripped_spruce_log[axis=x]')
        t.set(x, FEET + h - 1, z1, 'minecraft:stripped_spruce_log[axis=x]')
    # doors: three wide into the fold, two at the east end
    for x in range(21, 24):
        for y in range(FEET, FEET + 3):
            t.set(x, y, z1, AIR)
        t.set(x, FLOOR, z1, 'minecraft:coarse_dirt')
    for z in (5, 6):
        for y in range(FEET, FEET + 3):
            t.set(x1, y, z, AIR)
    stalls(t, 24, x1 - 1, z0 + 1, z0 + 3)
    # the loft at the west end, a ladder up
    for x in range(x0 + 1, x0 + 6):
        for z in range(z0 + 1, z1):
            t.set(x, FEET + 3, z, 'minecraft:spruce_planks' if (x + z) % 4 else 'minecraft:spruce_slab[type=top,waterlogged=false]')
    for x in range(x0 + 1, x0 + 5):
        for z in range(z0 + 1, z1 - 1):
            if (x + z) % 3:
                t.set(x, FEET + 4, z, S('minecraft:hay_block', axis='x' if x % 2 else 'z'))
    for y in range(FEET, FEET + 5):
        t.set(x0 + 1, y, z1 - 1, S('minecraft:ladder', facing='north', waterlogged=False))
    for x in range(x0 + 1, x0 + 5):
        t.set(x, FEET, z0 + 1, S('minecraft:hay_block', axis='x'))
    t.set(x0 + 2, FEET, z1 - 1, 'minecraft:composter[level=5]')
    t.set(x0 + 3, FEET, z1 - 1, 'minecraft:barrel[facing=up,open=false]')
    straw(t, set(K.inside(r)), seed + 71, 0.35)
    K.hang_lantern(t, 27, FEET + 3, (z0 + z1) // 2, 3)
    K.hang_lantern(t, 21, FEET + 3, (z0 + z1) // 2, 3)


def turf_roof(t, rect, y, ridge='x', seed=0):
    """A sod roof over rect (wall lines): spruce eaves, then courses of turf and moss stepping to the ridge,
    earth packed under them down to the ceiling, grass and flowers on top."""
    x0, z0, x1, z1 = rect
    n = Noise(seed, 2.0)
    a0, a1 = (z0 - 1, z1 + 1) if ridge == 'x' else (x0 - 1, x1 + 1)
    b0, b1 = (x0 - 1, x1 + 1) if ridge == 'x' else (z0 - 1, z1 + 1)
    rows = (a1 - a0 + 2) // 2
    tops = []
    for i in range(rows):
        yy = y + i
        for a in range(a0 + i, a1 - i + 1):
            for b in range(b0, b1 + 1):
                x, z = (b, a) if ridge == 'x' else (a, b)
                edge = a in (a0 + i, a1 - i)
                if i == 0 and edge:
                    face = ('south' if a == a0 else 'north') if ridge == 'x' else ('east' if a == a0 else 'west')
                    t.set(x, yy, z, stair('minecraft:spruce_stairs', face))
                    continue
                if edge:
                    t.set(x, yy, z, 'minecraft:moss_block' if n.at(x, yy, z) > 0.6 else 'minecraft:grass_block[snowy=false]')
                    tops.append((x, yy, z))
                elif t.get(x, yy, z) in (None, AIR):
                    t.set(x, yy, z, 'minecraft:rooted_dirt' if (x + z) % 3 == 0 else 'minecraft:dirt')
        if a0 + i >= a1 - i:
            break
    for (x, yy, z) in tops:
        if t.get(x, yy + 1, z) in (None, AIR) and n.at(x * 3, z * 3) > 0.45:
            t.set(x, yy + 1, z, K.pickw([('minecraft:short_grass', 6), ('minecraft:fern', 2),
                                         ('minecraft:wildflowers[facing=north,flower_amount=2]', 1)], n.at(x, z, 7)))


def bothy(t, k, P, seed):
    """The herders' bothy: a low stone hut under a turf roof, a hearth, a bed, the chest."""
    r = P.BOTHY
    x0, z0, x1, z1 = r
    h = 3
    for (x, z) in K.perimeter(r):
        for y in range(FLOOR, FEET + h):
            t.set(x, y, z, 'minecraft:cobblestone' if y == FLOOR else dry(k, x, y, z))
    for (x, z) in K.inside(r):
        t.set(x, FLOOR, z, 'minecraft:spruce_planks' if (x + 2 * z) % 5 else 'minecraft:packed_mud')
        for y in range(FEET, FEET + h):
            t.set(x, y, z, AIR)
        t.set(x, FEET + h, z, 'minecraft:spruce_planks')
    turf_roof(t, r, FEET + h, ridge='x', seed=seed + 81)
    mz = (z0 + z1) // 2
    # the door, east, facing the fold; a window south
    for y in range(FEET, FEET + 2):
        t.set(x1, y, mz, AIR)
    t.set(x1, FEET + 2, mz, 'minecraft:stripped_spruce_log[axis=z]')
    t.set(x0 + 3, FEET + 1, z1, S('minecraft:glass_pane', **K.pane_props('south')))
    # hearth and chimney at the west end
    t.set(x0 + 1, FEET, mz, S('minecraft:campfire', lit=False, signal_fire=False, facing='east', waterlogged=False))
    for y in range(FEET + 1, FEET + h + 7):
        t.set(x0 + 1, y, mz, 'minecraft:cobblestone' if y < FEET + h + 4 else 'minecraft:mossy_cobblestone')
    t.set(x0 + 1, FEET, mz - 1, 'minecraft:cobblestone')
    t.set(x0 + 1, FEET, mz + 1, 'minecraft:cobblestone')
    # the bed, the chest, a barrel, a stool, the lantern
    K.bed(t, x1 - 1, FEET, z0 + 1, 'east', color='brown')
    t.chest(x0 + 1, FEET, z1 - 1, facing='east', loot=LOOT)
    t.set(x0 + 2, FEET, z1 - 1, 'minecraft:barrel[facing=up,open=false]')
    t.set(x0 + 3, FEET, z0 + 1, stair('minecraft:spruce_stairs', 'north'))
    t.set(x0 + 4, FEET, z0 + 1, 'minecraft:crafting_table')
    t.set(x0 + 2, FEET, mz, 'minecraft:brown_carpet')
    K.hang_lantern(t, x0 + 4, FEET + 2, mz, 1)


# ---------------------------------------------------------------- the steading

class Steading:
    SX, SY, SZ = 33, 16, 39
    FOLD = (3, 15, 29, 36)
    BYRE = (5, 2, 27, 11)
    SPAWN = (9, 31)


def build_steading():
    P = Steading
    seed = SEED + 1000
    t = Template(P.SX, P.SY, P.SZ, seed=seed, name='selvaren/hollowhorn_fold/steading')
    k = K.Kit(t, seed)
    k.field_n = Noise(seed + 5, 2.0)
    cols = {(x, z) for x in range(P.SX) for z in range(P.SZ)}
    ground(t, cols, FLOOR, seed + 11, kind='meadow')
    clear_bowl(t, cols, 16, 19, FEET, P.SY - 1, r_full=15.0, r_out=24.0, rim_h=4)
    silver_tree(t, 30, FEET, 3, 11, seed + 31, boughs=3, spread=2.4)
    x0, z0, x1, z1 = P.FOLD
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    inside = set(K.inside(P.FOLD))
    grazed(t, inside, seed + 41)
    # the walls: the square and its cross; broken where the beasts pushed out
    walls = set(K.perimeter(P.FOLD)) | {(mx, z) for z in range(z0, z1 + 1)} | {(x, mz) for x in range(x0, x1 + 1)}
    breaks = {(x, z1) for x in range(19, 22)} | {(x1, z) for z in (29, 30)} | {(x0, z) for z in range(20, 22)}
    gates = {(mx, z0 + 4), (mx, z0 + 5), (mx - 6, mz), (mx + 6, mz), (mx, z1 - 5), (mx - 6, z0), (mx - 5, z0)}
    drystone(t, k, walls, gaps=breaks | gates)
    for (x, z) in sorted(gates):
        t.set(x, FLOOR, z, 'minecraft:coarse_dirt')
    for (x, z) in sorted(breaks):
        t.set(x, FLOOR, z, 'minecraft:coarse_dirt')
    k.rubble({(x + dx, z + dz) for (x, z) in breaks for dx in (-1, 0, 1) for dz in (-1, 0, 1)} - walls,
             lambda x, z: FEET, density=0.9, seed=3)
    t.set(mx - 6, FEET, z0, S('minecraft:spruce_fence_gate', facing='north', open=True, in_wall=False, powered=False))
    # a silver tree came down across the south-east pen and the wall
    for x in range(mx + 3, x1 + 3):
        t.set(x, FEET, 33, 'minecraft:birch_log[axis=x]')
        if (x, 33) in walls:
            for y in range(FEET + 1, FEET + 3):
                t.set(x, y, 33, AIR)
    t.set(mx + 2, FEET, 33, 'minecraft:birch_log[axis=y]')
    t.set(mx + 2, FEET, 34, 'minecraft:birch_log[axis=z]')
    t.set(mx + 2, FEET, 32, 'minecraft:birch_log[axis=z]')
    for (x, y, z) in [(x1 + 1, FEET + 1, 33), (x1 + 2, FEET + 1, 33), (x1 + 1, FEET, 32), (x1 + 2, FEET, 34),
                      (x1 + 1, FEET + 1, 34), (x1 + 2, FEET + 1, 32), (x1 + 3, FEET, 33)]:
        if t.get(x, y, z) in (None, AIR):
            t.set(x, y, z, S('minecraft:oak_leaves', persistent=True, distance=1, waterlogged=False))
    # a lean-to shelter in the north-east pen
    for z in range(z0 + 1, mz):
        if z in (z0 + 1, mz - 1):
            for x in (x1 - 3, x1 - 1):
                t.set(x, FEET, z, 'minecraft:spruce_fence')
                t.set(x, FEET + 1, z, 'minecraft:spruce_fence')
        for x in range(x1 - 3, x1):
            t.set(x, FEET + 2 + (x - (x1 - 3)) // 2, z, stair('minecraft:spruce_stairs', 'east'))
    for (x, z) in ((x1 - 2, z0 + 3), (x1 - 2, z0 + 4)):
        t.set(x, FEET, z, S('minecraft:hay_block', axis='z'))
    # troughs at the cross, the rubbing stone in the south-west pen
    for x in (mx + 1, mx + 2):
        t.set(x, FEET, mz + 1, S('minecraft:water_cauldron', level=2))
    field_stone(t, [(7, 29)], FEET, [2], t.rng, top='slab', mossy=0.7)
    straw(t, inside - walls, seed + 43, 0.12)
    mod.spawner(t, P.SPAWN[0], FEET, P.SPAWN[1], 'hollowhorns', count=0, bound=False)
    old_byre(t, k, P, seed)
    # the herders' cairn, south-east, outside the fold
    for (x, z, h) in ((30, 37, 3), (31, 37, 2), (30, 38, 2), (29, 37, 1)):
        for y in range(FEET, FEET + h):
            t.set(x, y, z, K.pickw(DRY, k.field_n._h(x, y, z)))
    t.set(30, FEET + 3, 37, slab('minecraft:mossy_cobblestone_slab'))
    # the track from the byre door down to the gate
    n = Noise(seed + 51, 2.0)
    for z in range(12, 16):
        for x in (mx - 7, mx - 6, mx - 5):
            if n.at(x, z) > 0.3 and t.get(x, FEET, z) in (None, AIR):
                t.set(x, FLOOR, z, 'minecraft:coarse_dirt' if n.at(x * 2, z * 2) > 0.5 else 'minecraft:dirt_path')
    weather(t, seed=seed + 21, patchiness=0.6)
    plants(t, cols - inside, 'meadow', 0.45, seed + 61, y_hi=FEET)
    plants(t, inside, 'meadow', 0.1, seed + 63, y_hi=FEET)
    mod.light_fill(t, (5, FEET, 2, 27, FEET + 5, 11), level=8, strength=12)
    return t


def old_byre(t, k, P, seed):
    """The old byre: two storeys of field stone, a loft over the west half reached by a ladder, the roof gone
    from the east half (its rafters show), stalls below, the chest under the loft."""
    r = P.BYRE
    x0, z0, x1, z1 = r
    h = 6
    n = Noise(seed + 71, 3.0)
    for (x, z) in K.perimeter(r):
        for y in range(FLOOR, FEET + h):
            t.set(x, y, z, 'minecraft:cobblestone' if y == FLOOR else dry(k, x, y, z))
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(FEET, FEET + h):
            t.set(x, y, z, 'minecraft:stone' if (y - FEET) % 2 else 'minecraft:andesite')
    for (x, z) in K.inside(r):
        t.set(x, FLOOR, z, 'minecraft:packed_mud' if (x * 3 + z) % 5 else 'minecraft:coarse_dirt')
        for y in range(FEET, FEET + h + 7):
            t.set(x, y, z, AIR)
    keep = lambda x, y, z: x < 17 + int(3 * n.at(y, z))
    k.gable(r, FEET + h, ridge='x', overhang=1, keep=keep,
            tiles=lambda x, y, z: 'minecraft:spruce_stairs' if (x * 5 + y) % 7 else 'minecraft:mossy_cobblestone_stairs',
            ridge_slab='minecraft:spruce_slab',
            end_wall=lambda x, y, z: 'minecraft:spruce_planks' if (x + y) % 3 else 'minecraft:stripped_spruce_log[axis=y]')
    k.break_top([c for c in K.perimeter(r) if c[0] > 19], FEET + 3, FEET + h - 1, seed=23)
    # doors: south into the yard (two wide), the loft door above it with a hay hoist beam
    for x in (11, 12):
        for y in range(FEET, FEET + 3):
            t.set(x, y, z1, AIR)
    t.set(8, FEET + 4, z1, AIR)
    t.set(8, FEET + 5, z1, AIR)
    t.set(8, FEET + 6, z1 + 1, 'minecraft:stripped_spruce_log[axis=z]')
    t.set(8, FEET + 6, z1, 'minecraft:stripped_spruce_log[axis=z]')
    t.set(8, FEET + 5, z1 + 1, 'minecraft:iron_chain[axis=y,waterlogged=false]')
    for x in range(x0 + 3, x1, 4):
        if x in (11, 12):
            continue
        t.set(x, FEET + 1, z0, AIR)
        if x > 13:
            t.set(x, FEET + 1, z1, AIR)
    # the loft over the west half
    lx1 = 14
    for x in range(x0 + 1, lx1 + 1):
        for z in range(z0 + 1, z1):
            t.set(x, FEET + 3, z, 'minecraft:spruce_planks' if (x + z) % 5 else 'minecraft:stripped_spruce_log[axis=x]')
    for x in range(x0 + 1, lx1):
        for z in range(z0 + 1, z0 + 4):
            if (x * 2 + z) % 3:
                t.set(x, FEET + 4, z, S('minecraft:hay_block', axis='x'))
    for y in range(FEET, FEET + 5):
        t.set(lx1 + 1, y, z0 + 1, S('minecraft:ladder', facing='south', waterlogged=False))
    for x in (x0 + 4, x0 + 8, lx1):
        for y in range(FEET, FEET + 3):
            t.set(x, y, z1 - 1, 'minecraft:spruce_fence' if y < FEET + 2 else 'minecraft:stripped_spruce_log[axis=y]')
    # under the loft: the chest, barrels, a cart's wheel (a grindstone), harness on the wall
    t.chest(x0 + 1, FEET, z0 + 1, facing='south', loot=LOOT)
    t.set(x0 + 2, FEET, z0 + 1, 'minecraft:barrel[facing=up,open=false]')
    t.set(x0 + 1, FEET, z0 + 2, 'minecraft:barrel[facing=up,open=false]')
    t.set(x0 + 3, FEET, z0 + 1, S('minecraft:grindstone', face='floor', facing='north'))
    K.hang_lantern(t, 10, FEET + 1, 6, 2)
    # the east half under the sky: stalls, rubble, hay gone to ferns
    stalls(t, 17, x1 - 1, z0 + 1, z0 + 3, every=3)
    k.rubble({(x, z) for (x, z) in K.inside(r) if x > 16}, lambda x, z: FEET, density=0.45, seed=29)
    straw(t, {(x, z) for (x, z) in K.inside(r) if x <= 16}, seed + 73, 0.35)


# ---------------------------------------------------------------- worldgen

def worldgen(templates):
    W.pool('selvaren/hollowhorn_fold/start', [W.element('selvaren/hollowhorn_fold/' + name, adapted=dict(
        yard_stand=FEET, clear_from=sy, clear_inset=2, support_depth=10, ramp_rings=2)) for name, sy in templates])
    W.structure('selvaren/hollowhorn_fold', start_pool='selvaren/hollowhorn_fold/start',
                biome_tag='has_structure/selvaren/hollowhorn_fold', size=1, max_distance=48,
                step='surface_structures', start_height=1 - BURIED)
    W.structure_set('selvaren/hollowhorn_fold', [('selvaren/hollowhorn_fold', 1)], spacing=36, separation=12,
                    salt=960203, placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/selvaren/hollowhorn_fold', ['oldrepublic:selvaren_meadow'])


def loot():
    loot_table(LOOT, [
        (1, [('oldrepublic:hollowhorn_hide', 1, 3, 1)], None),
        ((2, 4), [('oldrepublic:raw_hollowhorn', 1, 3, 4), ('leather', 1, 3, 4), ('bread', 1, 3, 3),
                  ('string', 1, 4, 3), ('bone', 1, 4, 3), ('oldrepublic:peat', 2, 4, 3), ('iron_nugget', 2, 6, 2),
                  ('oldrepublic:hollowhorn_horn', 1, 1, 1), ('emerald', 1, 1, 1)], None),
    ])


def main():
    loot()
    t = build_fold()
    t.finish()
    check_chests(t)
    check_reach(t, FEET, dict(chests(t), **spawners(t)))
    save(t, 'selvaren/hollowhorn_fold/fold', 'hollowhorn_fold_fold')
    t = build_steading()
    t.finish()
    check_chests(t)
    check_reach(t, FEET, dict(chests(t), loft=[(8, FEET + 4, 8)], **spawners(t)))
    save(t, 'selvaren/hollowhorn_fold/steading', 'hollowhorn_fold_steading')
    worldgen([('fold', Fold.SY), ('steading', Steading.SY)])


if __name__ == '__main__':
    main()
