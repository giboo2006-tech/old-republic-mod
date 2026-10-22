"""selvaren/watch_cloister: a Watch monastery gone quiet (docs/MILESTONE_6.md 5.5), two variants.

The Watch kept houses like this across the meadows and the highwood: four ranges round a cloister walk
and its garden, a bell tower over them. Since the war they stand empty. Moss takes the roofs, silver trees
grow in the garths, and the bells still hang.

- `bell` (49 x 34 x 49): the Hall of Listening on the north, the library on the east with the house's
  chest, the refectory and kitchen on the south, the brothers' cells on the west (roofless now) with the
  gate through them. The bell tower stands whole at the north-east corner. Behind the ranges lie the
  Watch's graves, a kitchen yard with hives, and an old silver tree.
- `fallen` (see build_fallen): the same order, older and further gone.

Worldgen: pool selvaren/watch_cloister/start (both, adapted terrain), structure selvaren/watch_cloister in
the meadow and the highwood, set 40/14, salt 960200, clear of the origin.
"""
import math

from selvaren_lib import (AIR, B, FIELD, Noise, Palette, S, Template, W, WATCH, check_chests, check_reach,
                          clear_bowl, ground, loot_table, mod, nature, plants, save, silver_tree, slab, stair,
                          weather, field_stone, surface_y)
import watchkit as K

SEED = 960200
BURIED = 3
FLOOR = BURIED - 1
FEET = BURIED


# ---------------------------------------------------------------- the bell variant

class Bell:
    SX, SY, SZ = 49, 34, 49
    HALL = (14, 5, 34, 14)
    LIB = (34, 14, 43, 34)
    REF = (14, 34, 34, 43)
    CELLS = (5, 14, 14, 34)
    TOWER = (35, 5, 43, 13)
    ARC = (18, 18, 30, 30)
    WALK = (14, 14, 34, 34)       # the range walls round the walk


def build_bell():
    P = Bell
    t = Template(P.SX, P.SY, P.SZ, seed=SEED, name='selvaren/watch_cloister/bell')
    k = K.Kit(t, SEED)
    cols = {(x, z) for x in range(P.SX) for z in range(P.SZ)}
    ground(t, cols, FLOOR, SEED + 11, kind='mixed')
    clear_bowl(t, cols, P.SX // 2, P.SZ // 2, FEET, P.SY - 1, r_full=20.0, r_out=34.0, rim_h=6)

    walk_and_garth(t, k, P)
    hall(t, k, P)
    library(t, k, P)
    refectory(t, k, P)
    cells(t, k, P)
    tower(t, k, P)
    grounds(t, k, P)

    weather(t, seed=SEED + 21, patchiness=0.6)
    nature.overgrow(t, region=((0, FEET, 0), (P.SX - 1, P.SY - 1, P.SZ - 1)), vines=0.12, moss=0.0, lichen=0.0,
                    wall_names=['oldrepublic:watch_stone', 'minecraft:mossy_stone_bricks', 'minecraft:stone_bricks',
                                'minecraft:cracked_stone_bricks', 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'])
    mod.light_fill(t, (6, FEET, 6, 42, FEET + 12, 42), level=8, strength=12)
    return t


# ---------------------------------------------------------------- the walk and the garden

def walk_and_garth(t, k, P):
    x0, z0, x1, z1 = P.WALK
    a0, b0, a1, b1 = P.ARC
    n = Noise(SEED + 51, 3.0)
    # flagstones over the walk, worn to earth where the roof fell
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if a0 < x < a1 and b0 < z < b1:
                continue
            t.set(x, FLOOR, z, k.floor(x, FLOOR, z))
            for y in range(FEET, FEET + 8):
                t.set(x, y, z, AIR)
    # the arcade: piers every four cells, three-wide round-headed openings, a frieze course at the springing
    spring = FEET + 3
    for side in K.DIRS:
        line = K.side_cells(P.ARC, side)
        for i, (x, z) in enumerate(line):
            pier = i % 4 == 0
            if pier:
                t.set(x, FEET, z, 'minecraft:polished_andesite')
                for y in range(FEET + 1, spring):
                    t.set(x, y, z, 'minecraft:stone_brick_wall')
                t.set(x, spring, z, 'minecraft:chiseled_stone_bricks')
            else:
                j = i % 4
                ax, az = K.along(side)
                first = 'west' if ax else 'north'
                last = 'east' if ax else 'south'
                for y in range(FEET, spring):
                    t.set(x, y, z, AIR)
                if j == 1:
                    t.set(x, spring, z, stair('oldrepublic:watch_stone_stairs', first, 'top'))
                elif j == 3:
                    t.set(x, spring, z, stair('oldrepublic:watch_stone_stairs', last, 'top'))
                else:
                    t.set(x, spring, z, slab('oldrepublic:watch_stone_slab', 'top'))
            t.set(x, FLOOR, z, 'minecraft:polished_andesite' if pier else k.floor(x, FLOOR, z))
    # the walk's roof: a lean-to from the arcade (low) up to the range walls, hipped at the corners
    roof = {}
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if a0 < x < a1 and b0 < z < b1:
                continue
            d = max(a0 - x, x - a1, b0 - z, z - b1, 0)
            roof[(x, z)] = d
    low = spring + 1
    fallen = K.Noise(SEED + 53, 3.5)
    for (x, z), d in sorted(roof.items()):
        y = low + d
        # the south-west corner's roof is down
        if x < 22 and z > 26 and fallen.at(x, z) > 0.35:
            if (x + z) % 3 == 0 and d in (1, 2):
                t.set(x, y, z, 'minecraft:stripped_dark_oak_log[axis=%s]' % ('x' if x < a0 else 'z'))
            continue
        gaps = [(a0 - x, 'east'), (x - a1, 'west'), (b0 - z, 'south'), (z - b1, 'north')]
        facing = max(gaps)[1]
        # the slope rises away from the garden: the stair's high side faces out
        facing = B.opposite(facing)
        t.set(x, y, z, stair(k.roof_tile(x, y, z, 0.08 - 0.03 * d), facing))
        for yy in range(FEET, y):
            if t.get(x, yy, z) in (None, AIR) and not (d == 0):
                t.set(x, yy, z, AIR)
    # rubble under the fallen corner
    k.rubble({(x, z) for (x, z) in roof if x < 22 and z > 26}, lambda x, z: FEET, density=0.5, seed=3)
    garth(t, k, P)


def garth(t, k, P):
    a0, b0, a1, b1 = P.ARC
    cx, cz = (a0 + a1) // 2, (b0 + b1) // 2
    n = Noise(SEED + 61, 2.5)
    beds = []
    for x in range(a0 + 1, a1):
        for z in range(b0 + 1, b1):
            path = abs(x - cx) <= 0 or abs(z - cz) <= 0
            if path:
                t.set(x, FLOOR, z, 'minecraft:gravel' if n.at(x, z) > 0.55 else 'minecraft:dirt_path')
            else:
                t.set(x, FLOOR, z, 'minecraft:grass_block[snowy=false]' if n.at(x, z) < 0.66 else 'minecraft:moss_block')
                beds.append((x, z))
    # the well at the crossing
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            x, z = cx + dx, cz + dz
            if dx == dz == 0:
                t.set(x, FLOOR - 1, z, 'minecraft:water')
                t.set(x, FLOOR, z, 'minecraft:water')
                continue
            t.set(x, FLOOR, z, 'minecraft:mossy_stone_bricks' if (dx + dz) % 2 else 'minecraft:stone_bricks')
            if dx and dz:
                t.set(x, FEET, z, 'minecraft:stone_brick_wall')
                if (dx, dz) in ((-1, -1), (1, 1)):
                    t.set(x, FEET + 1, z, 'minecraft:stone_brick_wall')
                    t.set(x, FEET + 2, z, 'minecraft:stripped_spruce_log[axis=y]')
            else:
                t.set(x, FEET, z, slab('minecraft:stone_brick_slab'))
    t.set(cx - 1, FEET + 3, cz - 1, 'minecraft:stripped_spruce_log[axis=x]')
    t.set(cx, FEET + 3, cz - 1, 'minecraft:stripped_spruce_log[axis=x]')
    t.set(cx + 1, FEET + 3, cz - 1, 'minecraft:stripped_spruce_log[axis=x]')
    t.set(cx + 1, FEET + 2, cz + 1, 'minecraft:stripped_spruce_log[axis=y]')
    # herb beds and the lumen moss the brothers grew for its light
    for (x, z) in beds:
        v = n.at(x * 1.7, z * 1.7)
        if v > 0.7:
            t.set(x, FEET, z, 'oldrepublic:lumen_moss')
        elif v > 0.45:
            t.set(x, FEET, z, K.pickw([('oldrepublic:silverleaf', 4), ('minecraft:fern', 3), ('minecraft:short_grass', 3),
                                       ('minecraft:lily_of_the_valley', 1), ('minecraft:allium', 1)], k.rng.random()))
        elif v < 0.2:
            t.set(x, FEET, z, S('minecraft:wildflowers', facing=K.DIRS[k.rng.randrange(4)], flower_amount=3))
    # the old silver tree of the garth, its crown over the walk's roofs
    silver_tree(t, a0 + 3, FEET, b0 + 3, 13, SEED + 63, boughs=4, spread=3.0)
    # a stone bench under it
    t.set(a0 + 5, FEET, b0 + 2, stair('oldrepublic:watch_stone_stairs', 'north'))
    t.set(a0 + 6, FEET, b0 + 2, stair('oldrepublic:watch_stone_stairs', 'north'))


# ---------------------------------------------------------------- the ranges

def hall(t, k, P):
    """The Hall of Listening: tall, lancet windows to the north, meditation stones in rows, a reading desk.
    Its west end has lost its roof; a sapling of silver tree stands in the rubble."""
    r = P.HALL
    x0, z0, x1, z1 = r
    h = 9
    n = Noise(SEED + 71, 3.0)

    def hole(x, y, z):
        return not (x < x0 + 7 and n.at(x, y, z) > 0.3)
    k.walls(r, FEET, h)
    k.plinth_skirt(r, FEET, sides=('north', 'west'))
    k.buttresses(r, FEET, h, every=4, sides=('north',))
    for (x, z) in K.inside(r):
        t.set(x, FLOOR, z, k.floor(x, FLOOR, z))
        for y in range(FEET, FEET + h + 7):
            t.set(x, y, z, AIR)
    for x in range(x0 + 2, x1 - 1, 4):
        k.window(x, FEET + 3, z0, 'north', h=4, glass=x > x0 + 6)
    for x in (x0 + 4, x1 - 4):
        k.window(x, FEET + 4, z1, 'south', h=2, glass=True)
    k.window(x0, FEET + 3, (z0 + z1) // 2, 'west', h=4, glass=False)
    k.door(23, FEET, z1, 'south', width=3, height=4)
    k.door(x1, FEET, 10, 'east', width=2, height=4)           # into the tower, onto its first step
    k.gable(r, FEET + h, ridge='x', overhang=1, keep=hole)
    # the hall: two rows of meditation stones facing the reading desk at the east end
    for x in range(x0 + 8, x1 - 3, 3):
        for z in (z0 + 3, z1 - 3):
            t.set(x, FEET, z, 'oldrepublic:meditation_stone')
    dz = (z0 + z1) // 2
    t.set(x1 - 2, FLOOR, dz - 1, 'minecraft:polished_andesite')
    t.set(x1 - 2, FLOOR, dz, 'minecraft:polished_andesite')
    mod.lectern(t, x1 - 2, FEET, dz, facing='west', book=True)
    K.candle(t, x1 - 2, FEET, dz - 2, n=3, color='white')
    K.candle(t, x1 - 2, FEET, dz + 2, n=2, color='white')
    mod.banner(t, x1 - 1, FEET + 4, dz - 1, 'west', base='green')
    mod.banner(t, x1 - 1, FEET + 4, dz + 1, 'west', base='green')
    for x in (x0 + 9, x0 + 14):
        K.hang_lantern(t, x, FEET + 5, dz, 4)
    # the west end: roof down, rubble, a young silver tree, ferns
    k.rubble({(x, z) for (x, z) in K.inside(r) if x < x0 + 7}, lambda x, z: FEET, density=0.7, seed=5)
    silver_tree(t, x0 + 3, FEET, z0 + 3, 9, SEED + 73, boughs=2, spread=2.0)


def library(t, k, P):
    """The library: shelves along the walls, reading lecterns, the house's chest under the east window."""
    r = P.LIB
    x0, z0, x1, z1 = r
    h = 8
    k.walls(r, FEET, h)
    k.plinth_skirt(r, FEET, sides=('east', 'south'))
    k.buttresses(r, FEET, h, every=4, sides=('east',))
    for (x, z) in K.inside(r):
        t.set(x, FLOOR, z, 'minecraft:spruce_planks' if (x + z) % 7 else 'minecraft:stripped_spruce_wood[axis=x]')
        for y in range(FEET, FEET + h + 6):
            t.set(x, y, z, AIR)
    for z in range(z0 + 3, z1 - 1, 4):
        k.window(x1, FEET + 3, z, 'east', h=3)
    k.door(x0, FEET, 23, 'west', width=3, height=4)
    k.gable(r, FEET + h, ridge='z', overhang=1)
    # shelves along both long walls between the windows, two high
    for z in range(z0 + 1, z1):
        if (z - z0 - 3) % 4 == 0:
            continue
        for y in (FEET, FEET + 1):
            t.set(x1 - 1, y, z, 'minecraft:bookshelf' if (z + y) % 5 else 'minecraft:chiseled_bookshelf[facing=west,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=true]')
        if 22 <= z <= 24:
            continue
        for y in (FEET, FEET + 1):
            t.set(x0 + 1, y, z, 'minecraft:bookshelf' if (z * 3 + y) % 6 else 'minecraft:chiseled_bookshelf[facing=east,slot_0_occupied=false,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=true,slot_5_occupied=false]')
    # reading desks down the middle
    for z in (z0 + 4, z0 + 9, z0 + 14):
        mod.lectern(t, 38, FEET, z, facing='south', book=z == z0 + 9)
        K.candle(t, 39, FEET, z, n=2, color='white')
    t.set(38, FEET, z0 + 17, 'minecraft:cartography_table')
    t.chest(39, FEET, z0 + 18, facing='west', loot='chests/selvaren/watch_cloister')
    for z in (z0 + 6, z0 + 12, z0 + 17):
        K.hang_lantern(t, 38, FEET + 5, z, 6)


def refectory(t, k, P):
    """The refectory (two long tables, a reader's lectern, a cold hearth) and the kitchen to its east."""
    r = P.REF
    x0, z0, x1, z1 = r
    h = 8
    n = Noise(SEED + 81, 3.0)

    def hole(x, y, z):
        return not (20 <= x <= 25 and n.at(x, y, z) > 0.45)
    k.walls(r, FEET, h)
    k.plinth_skirt(r, FEET, sides=('south',))
    k.buttresses(r, FEET, h, every=4, sides=('south',))
    for (x, z) in K.inside(r):
        t.set(x, FLOOR, z, k.floor(x, FLOOR, z))
        for y in range(FEET, FEET + h + 6):
            t.set(x, y, z, AIR)
    # the wall between refectory and kitchen, with a serving door
    for z in range(z0 + 1, z1):
        for y in range(FEET, FEET + h):
            t.set(28, y, z, k.wall_cell(28, y, z, FEET, h))
    k.door(28, FEET, 38, 'east', width=2, height=3)
    for x in range(x0 + 2, x1 - 1, 4):
        if x in (24, 28):
            continue
        k.window(x, FEET + 3, z1, 'south', h=3)
    k.door(23, FEET, z0, 'north', width=3, height=4)
    k.door(x1, FEET, 39, 'east', width=2, height=3)
    k.gable(r, FEET + h, ridge='x', overhang=1, keep=hole)
    # long tables with benches
    for z in (37, 40):
        for x in range(x0 + 2, 26):
            t.set(x, FEET, z, S('minecraft:spruce_slab', type='top', waterlogged=False) if x % 4 else
                  'minecraft:spruce_fence')
            for bz, f in ((z - 1, 'south'), (z + 1, 'north')):
                if bz in (36, 41) and k.rng.random() < 0.8:
                    t.set(x, FEET, bz, stair('minecraft:spruce_stairs', f))
    # one bench knocked over, a bowl left
    t.set(18, FEET, 38, S('minecraft:spruce_trapdoor', facing='east', half='bottom', open=True, powered=False,
                          waterlogged=False))
    mod.lectern(t, x0 + 1, FEET, 38, facing='east', book=False)
    # the hearth on the west wall: a cold fire under a hood
    hx = x0 + 1
    t.set(hx, FEET, 40, S('minecraft:campfire', lit=False, signal_fire=False, facing='east', waterlogged=False))
    for y in range(FEET + 1, FEET + h + 4):
        t.set(hx, y, 40, 'minecraft:bricks' if y < FEET + 3 else 'minecraft:mud_bricks')
    # the kitchen
    t.set(30, FEET, z1 - 1, S('minecraft:smoker', facing='north', lit=False))
    t.set(31, FEET, z1 - 1, S('minecraft:furnace', facing='north', lit=False))
    t.set(32, FEET, z1 - 1, 'minecraft:cauldron')
    for x in (30, 31):
        for y in range(FEET + 1, FEET + h + 4):
            t.set(x, y, z1 - 1, 'minecraft:bricks')
    t.set(33, FEET, 36, 'minecraft:barrel[facing=up,open=false]')
    t.set(33, FEET, 37, 'minecraft:barrel[facing=up,open=false]')
    t.set(33, FEET + 1, 36, 'minecraft:barrel[facing=up,open=false]')
    t.set(29, FEET, 36, S('minecraft:decorated_pot', facing='south', cracked=False, waterlogged=False))
    t.chest(31, FEET, 36, facing='south', loot='chests/selvaren/watch_cloister_2')
    K.hang_lantern(t, 31, FEET + 5, 39, 4)
    K.hang_lantern(t, 18, FEET + 5, 38, 4)
    K.hang_lantern(t, 24, FEET + 5, 39, 4)
    k.rubble({(x, z) for x in range(20, 26) for z in range(z0 + 1, z1)}, lambda x, z: FEET, density=0.35, seed=7)


def cells(t, k, P):
    """The brothers' cells, roofless now, walls broken to different heights; the gate passage through them."""
    r = P.CELLS
    x0, z0, x1, z1 = r
    h = 7
    k.walls(r, FEET, h, sides=('west', 'north', 'south', 'east'))
    k.plinth_skirt(r, FEET, sides=('west',))
    for (x, z) in K.inside(r):
        t.set(x, FLOOR, z, k.floor(x, FLOOR, z))
        for y in range(FEET, FEET + h + 4):
            t.set(x, y, z, AIR)
    # partitions: a corridor on the east, cells to the west of it behind a wall with a door to each
    for z in (18, 22, 26, 30):
        for x in range(x0 + 1, x0 + 6):
            for y in range(FEET, FEET + h - 1):
                t.set(x, y, z, k.wall_cell(x, y, z, FEET, h - 1))
    doors = (17, 21, 27, 31)
    for z in range(z0 + 1, z1):
        if 22 <= z <= 26 or z in doors:
            continue
        for y in range(FEET, FEET + h - 1):
            t.set(x0 + 6, y, z, k.wall_cell(x0 + 6, y, z, FEET, h - 1))
    # cells: a bed, a stool, a candle
    for i, zc in enumerate((16, 20, 28, 32)):
        K.bed(t, x0 + 1, FEET, zc, 'west', color='green' if i % 3 else 'white')
        t.set(x0 + 4, FEET, zc - 1 if zc in (16, 28) else zc + 1, stair('minecraft:spruce_stairs', 'east'))
        if i != 2:
            K.candle(t, x0 + 1, FEET, zc + 1 if zc in (16, 28) else zc - 1, n=1, color='white', lit=False)
    # the gate passage at z 23..25, a vault through the range
    for z in (23, 24, 25):
        for x in range(x0, x1 + 1):
            t.set(x, FLOOR, z, 'minecraft:stone_bricks' if (x + z) % 3 else 'minecraft:mossy_stone_bricks')
            for y in range(FEET, FEET + 4):
                t.set(x, y, z, AIR)
            t.set(x, FEET + 4, z, k.wall(x, FEET + 4, z))
    for x in range(x0, x1 + 1):
        for z in (22, 26):
            for y in range(FEET, FEET + 5):
                t.set(x, y, z, k.wall_cell(x, y, z, FEET, h))
        t.set(x, FEET + 3, 23, stair('oldrepublic:watch_stone_stairs', 'north', 'top'))
        t.set(x, FEET + 3, 25, stair('oldrepublic:watch_stone_stairs', 'south', 'top'))
    for z in (22, 26):
        t.set(x0 - 1, FEET, z, 'minecraft:mossy_stone_bricks')
        t.set(x0 - 1, FEET + 1, z, 'minecraft:stone_brick_wall')
        t.set(x0 - 1, FEET + 2, z, S('minecraft:lantern', hanging=False, waterlogged=False))
    t.set(x0 + 1, FEET, 23, S('minecraft:dark_oak_fence_gate', facing='east', open=True, in_wall=False, powered=False))
    # roofless, walls broken: the cut falls lower toward the south
    top_cells = [c for c in K.perimeter(r) if not (22 <= c[1] <= 26 and c[0] in (x0, x1))]
    top_cells += [(x, z) for x in range(x0 + 1, x0 + 7) for z in (18, 22, 26, 30)]
    top_cells += [(x0 + 6, z) for z in range(z0 + 1, z1) if not 22 <= z <= 26]
    k.break_top(top_cells, FEET + 3, FEET + h - 1, seed=9)
    k.rubble(set(K.inside(r)), lambda x, z: FEET, density=0.35, seed=11)
    silver_tree(t, x0 + 3, FEET, 24 + 6, 8, SEED + 91, boughs=2, spread=2.2)


def tower(t, k, P):
    """The bell tower: two-cell walls, a stair round its shaft, a belfry with four arches and the bell."""
    r = P.TOWER
    x0, z0, x1, z1 = r
    top = 27
    for (x, z) in K.perimeter(r, thick=2):
        for y in range(FLOOR, top + 1):
            k_ = y - FEET
            if y == FLOOR:
                s = 'minecraft:stone_bricks'
            elif k_ == 0:
                s = k.plinth(x, y, z)
            elif y in (FEET + 8, FEET + 16):
                s = WATCH['trim']
            else:
                s = k.wall(x, y, z)
            t.set(x, y, z, s)
    # corner quoins of chiseled stone every fourth course
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(FEET + 3, top, 4):
            t.set(x, y, z, 'minecraft:chiseled_stone_bricks')
    ix0, iz0, ix1, iz1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            t.set(x, FLOOR, z, 'minecraft:stone_bricks')
            for y in range(FEET, top + 1):
                t.set(x, y, z, AIR)
    # the square stair round the shaft: up the walls, one rise per step
    ring = []
    for x in range(ix0, ix1 + 1):
        ring.append((x, iz1, 'east'))
    for z in range(iz1 - 1, iz0 - 1, -1):
        ring.append((ix1, z, 'north'))
    for x in range(ix1 - 1, ix0 - 1, -1):
        ring.append((x, iz0, 'west'))
    for z in range(iz0 + 1, iz1):
        ring.append((ix0, z, 'south'))
    belfry = FEET + 19
    y = FEET
    i = 0
    L = len(ring)
    while y < belfry:
        x, z, f = ring[i % L]
        t.set(x, y, z, stair('minecraft:stone_brick_stairs', f))
        # solid under the flight, down to three cells over the turn below (its headroom)
        for yy in range(max(FEET, y - L + 4), y):
            if t.get(x, yy, z) == AIR:
                t.set(x, yy, z, 'minecraft:stone_bricks')
        y += 1
        i += 1
    # the doorway from the hall, through both courses of the wall, onto the first step
    for x in (x0, x0 + 1):
        for z in (10, 11):
            for y in range(FEET, FEET + 3):
                t.set(x, y, z, AIR)
            t.set(x, FEET + 3, z, WATCH['trim'])
    # the belfry floor, open over the last four steps (headroom for the climb)
    last = {(c[0], c[1]) for c in [ring[(i - j) % L] for j in range(1, 5)]}
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            if (x, z) not in last:
                t.set(x, belfry - 1, z, 'minecraft:spruce_planks')
    # the belfry: an arch on each face, the bell under a beam
    for side in K.DIRS:
        cells = K.side_cells(r, side)
        mid = cells[len(cells) // 2]
        ax, az = K.along(side)
        dx, dz = K.STEP[side]
        for off in (-1, 0, 1):
            cx, cz = mid[0] + ax * off, mid[1] + az * off
            for depth in (0, 1):
                px, pz = cx - dx * depth, cz - dz * depth
                for yy in range(belfry, belfry + 4):
                    t.set(px, yy, pz, AIR)
                t.set(px, belfry + 3, pz, stair('oldrepublic:watch_stone_stairs',
                                                ('west' if ax else 'north') if off == -1 else
                                                ('east' if ax else 'south'), 'top') if off else
                      slab('oldrepublic:watch_stone_slab', 'top'))
            t.set(cx + dx, belfry - 1, cz + dz, stair('oldrepublic:watch_stone_stairs', B.opposite(side)))
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(ix0, ix1 + 1):
        t.set(x, belfry + 5, cz, 'minecraft:stripped_dark_oak_log[axis=x]')
    t.set(cx, belfry + 4, cz, S('minecraft:bell', attachment='ceiling', facing='north', powered=False))
    # a pyramid roof of tiles over a corbel course
    for (x, z) in K.perimeter((x0 - 1, z0 - 1, x1 + 1, z1 + 1)):
        side = 'north' if z == z0 - 1 else 'south' if z == z1 + 1 else 'west' if x == x0 - 1 else 'east'
        t.set(x, top, z, stair('oldrepublic:watch_stone_stairs', B.opposite(side), 'top'))
    yy = top + 1
    i = 0
    while x0 - 1 + i <= x1 + 1 - i:
        rr = (x0 - 1 + i, z0 - 1 + i, x1 + 1 - i, z1 + 1 - i)
        if rr[0] == rr[2]:
            t.set(rr[0], yy, rr[1], 'minecraft:deepslate_tile_wall')
            t.set(rr[0], yy + 1, rr[1], S('minecraft:lightning_rod', facing='up', powered=False, waterlogged=False))
            break
        for (x, z) in K.perimeter(rr):
            side = 'north' if z == rr[1] else 'south' if z == rr[3] else 'west' if x == rr[0] else 'east'
            t.set(x, yy, z, stair(k.roof_tile(x, yy, z, 0.06 if side == 'north' else 0.0), B.opposite(side)))
        for (x, z) in K.inside(rr):
            if yy >= top + 2:
                t.set(x, yy, z, 'minecraft:deepslate_tiles')
        yy += 1
        i += 1
    # slit windows up the shaft
    for side in K.DIRS:
        cells = K.side_cells(r, side)
        mid = cells[len(cells) // 2]
        dx, dz = K.STEP[side]
        for y in (FEET + 5, FEET + 11, FEET + 15):
            for depth in (0, 1):
                t.set(mid[0] - dx * depth, y, mid[1] - dz * depth, AIR)
                t.set(mid[0] - dx * depth, y + 1, mid[1] - dz * depth, AIR)
    # lights on the way up
    K.lantern(t, ix0, FEET, iz0)
    mod.light_fill(t, (ix0, FEET, iz0, ix1, belfry + 1, iz1), level=8, strength=11)


# ---------------------------------------------------------------- outside the ranges

def grounds(t, k, P):
    n = Noise(SEED + 101, 3.0)
    # the graves of the Watch in the south-west corner: low field stones in rows, a drystone wall
    gx0, gz0, gx1, gz1 = 5, 36, 13, 44
    for (x, z) in K.perimeter((gx0, gz0, gx1, gz1)):
        if (x, z) in ((9, gz0),):
            continue
        t.set(x, FEET, z, 'minecraft:mossy_cobblestone_wall' if n.at(x, z) > 0.4 else 'minecraft:cobblestone_wall')
    for i, (x, z) in enumerate([(7, 38), (9, 38), (11, 38), (7, 41), (9, 41), (11, 41)]):
        if i == 4:
            continue
        hgt = 1 + (i % 2)
        field_stone(t, [(x, z)], FEET, [hgt], k.rng, top='slab' if i % 3 else 'stair', mossy=0.5)
        t.set(x, FLOOR, z + 1, 'minecraft:moss_block')
    silver_tree(t, 12, FEET, 43, 10, SEED + 103, boughs=3, spread=2.6)
    # the kitchen yard in the south-east: hives on posts, the woodpile, a compost bin
    for (x, z) in ((37, 37), (40, 37)):
        t.set(x, FEET, z, 'minecraft:spruce_fence')
        t.set(x, FEET + 1, z, S('minecraft:beehive', facing='south', honey_level=3))
    for x in range(36, 42):
        t.set(x, FEET, 42, 'minecraft:stripped_spruce_log[axis=x]')
        if x < 40:
            t.set(x, FEET + 1, 42, 'minecraft:stripped_spruce_log[axis=x]')
    t.set(42, FEET, 39, S('minecraft:composter', level=4))
    t.set(42, FEET, 40, 'minecraft:barrel[facing=up,open=false]')
    # an old silver tree north-west of the hall, and the path from the gate out to the road
    silver_tree(t, 8, FEET, 8, 12, SEED + 105, boughs=3, spread=2.8)
    for x in range(0, 5):
        for z in (23, 24, 25):
            if n.at(x, z) > 0.25:
                t.set(x, FLOOR, z, 'minecraft:dirt_path' if n.at(x * 2, z * 2) > 0.45 else 'minecraft:coarse_dirt')
    # the kitchen door's path out to the yard, and the way round to the graves
    for x in range(35, 43):
        for z in (39, 40):
            if n.at(x * 1.5, z * 1.5) > 0.3 and t.name_at(x, FLOOR, z) in K.GROUND_NAMES and t.get(x, FEET, z) in (None, AIR):
                t.set(x, FLOOR, z, 'minecraft:dirt_path' if n.at(x, z, 5) > 0.4 else 'minecraft:coarse_dirt')
    for z in range(26, 36):
        x = 3 + (z % 3 == 0)
        if t.name_at(x, FLOOR, z) in K.GROUND_NAMES and t.get(x, FEET, z) in (None, AIR):
            t.set(x, FLOOR, z, 'minecraft:dirt_path' if n.at(x, z, 9) > 0.35 else 'minecraft:coarse_dirt')
    plants(t, {(x, z) for x in range(P.SX) for z in range(P.SZ)}, 'meadow', 0.3, SEED + 107, y_hi=FEET)


# ---------------------------------------------------------------- the fallen variant

class Fallen:
    SX, SY, SZ = 53, 27, 47
    WALK = (14, 10, 40, 32)       # the walls round the walk
    ARC = (18, 14, 36, 28)        # the arcade round the garth
    NORTH = (14, 3, 40, 10)       # the dormitory: foundations now
    LIB = (40, 10, 48, 32)        # the library: half its roof left
    CHURCH = (9, 32, 48, 41)      # the church: roofless but for the east gable
    TOWER = (1, 32, 9, 40)        # the bell tower: a stump
    TREE = (31, 18)               # the great silver tree of the garth
    BELL = (22, 24)               # where the bell came down with its beam
    FALL = ((6, 35), (22, 24))    # the line the tower fell along


def seg_dist(x, z, a, b):
    (ax, az), (bx, bz) = a, b
    vx, vz = bx - ax, bz - az
    f = max(0.0, min(1.0, ((x - ax) * vx + (z - az) * vz) / float(vx * vx + vz * vz)))
    return math.hypot(x - (ax + vx * f), z - (az + vz * f)), f


def build_fallen():
    P = Fallen
    seed = SEED + 1000
    t = Template(P.SX, P.SY, P.SZ, seed=seed, name='selvaren/watch_cloister/fallen')
    k = K.Kit(t, seed)
    cols = {(x, z) for x in range(P.SX) for z in range(P.SZ)}
    ground(t, cols, FLOOR, seed + 11, kind='mixed')
    clear_bowl(t, cols, 26, 23, FEET, P.SY - 1, r_full=22.0, r_out=36.0, rim_h=6)

    church(t, k, P, seed)
    tower_stump(t, k, P, seed)
    dormitory(t, k, P, seed)
    library_half(t, k, P, seed)
    walk_fallen(t, k, P, seed)
    the_fall(t, k, P, seed)
    garth_fallen(t, k, P, seed)
    grounds_fallen(t, k, P, seed)

    weather(t, seed=seed + 21, patchiness=0.75)
    nature.overgrow(t, region=((0, FEET, 0), (P.SX - 1, P.SY - 1, P.SZ - 1)), vines=0.2, moss=0.0, lichen=0.0,
                    wall_names=['oldrepublic:watch_stone', 'minecraft:mossy_stone_bricks', 'minecraft:stone_bricks',
                                'minecraft:cracked_stone_bricks', 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'])
    mod.light_fill(t, (4, FEET, 3, 48, FEET + 10, 41), level=8, strength=12)
    return t


def arcade_side(t, k, arc, side, spring, whole=True, n=None):
    """One side of a cloister arcade: piers every four cells with round heads between them (whole), or the
    piers' stumps alone, one or two courses high (not whole)."""
    line = K.side_cells(arc, side)
    ax, az = K.along(side)
    first = 'west' if ax else 'north'
    last = 'east' if ax else 'south'
    for i, (x, z) in enumerate(line):
        pier = i % 4 == 0
        t.set(x, FLOOR, z, 'minecraft:polished_andesite' if pier else k.floor(x, FLOOR, z))
        if not whole:
            if pier:
                hgt = 1 + int(2.5 * (n.at(x, z) if n else 0.5))
                t.set(x, FEET, z, 'minecraft:polished_andesite')
                for y in range(FEET + 1, FEET + hgt):
                    t.set(x, y, z, 'minecraft:stone_brick_wall')
            continue
        if pier:
            t.set(x, FEET, z, 'minecraft:polished_andesite')
            for y in range(FEET + 1, spring):
                t.set(x, y, z, 'minecraft:stone_brick_wall')
            t.set(x, spring, z, 'minecraft:chiseled_stone_bricks')
        else:
            j = i % 4
            for y in range(FEET, spring):
                t.set(x, y, z, AIR)
            if j == 1:
                t.set(x, spring, z, stair('oldrepublic:watch_stone_stairs', first, 'top'))
            elif j == 3:
                t.set(x, spring, z, stair('oldrepublic:watch_stone_stairs', last, 'top'))
            else:
                t.set(x, spring, z, slab('oldrepublic:watch_stone_slab', 'top'))


def walk_fallen(t, k, P, seed):
    """The walk: flagstones going to moss; the south and east arcades stand under their roofs, holed; the north
    and west arcades are stumps; the west wall has the gate."""
    x0, z0, x1, z1 = P.WALK
    a0, b0, a1, b1 = P.ARC
    n = Noise(seed + 51, 3.0)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if a0 < x < a1 and b0 < z < b1:
                continue
            v = n.at(x, z)
            t.set(x, FLOOR, z, k.floor(x, FLOOR, z) if v < 0.6 else
                  'minecraft:moss_block' if v < 0.7 else 'minecraft:grass_block[snowy=false]')
            for y in range(FEET, FEET + 8):
                t.set(x, y, z, AIR)
    # the west wall of the walk, with the gate; its top broken
    for z in range(z0, z1 + 1):
        for y in range(FEET, FEET + 6):
            t.set(x0, y, z, k.wall_cell(x0, y, z, FEET, 6))
    k.door(x0, FEET, 20, 'west', width=3, height=4)
    k.break_top([(x0, z) for z in range(z0, z1 + 1) if not 20 <= z <= 22], FEET + 2, FEET + 5, seed=13)
    spring = FEET + 3
    pn = Noise(seed + 52, 2.0)
    arcade_side(t, k, P.ARC, 'south', spring, whole=True)
    arcade_side(t, k, P.ARC, 'east', spring, whole=True)
    arcade_side(t, k, P.ARC, 'north', spring, whole=False, n=pn)
    arcade_side(t, k, P.ARC, 'west', spring, whole=False, n=pn)
    # the roof over the south and east walks, holed where it rotted through
    hn = Noise(seed + 53, 3.0)
    low = spring + 1
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if a0 < x < a1 and b0 < z < b1:
                continue
            if not (x > a1 or z > b1) or x < a0:
                continue
            d = max(a0 - x, x - a1, b0 - z, z - b1, 0)
            y = low + d
            if hn.at(x, z) > 0.62:
                if (x + z) % 3 == 0 and d in (1, 2):
                    t.set(x, y, z, 'minecraft:stripped_dark_oak_log[axis=%s]' % ('z' if x > a1 else 'x'))
                continue
            gaps = [(a0 - x, 'east'), (x - a1, 'west'), (b0 - z, 'south'), (z - b1, 'north')]
            facing = B.opposite(max(gaps)[1])
            t.set(x, y, z, stair(k.roof_tile(x, y, z, 0.1 - 0.03 * d), facing))
    k.rubble({(x, z) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1)
              if not (a0 < x < a1 and b0 < z < b1) and (x <= a0 or z <= b0)}, lambda x, z: FEET, density=0.3, seed=3)


def church(t, k, P, seed):
    """The church on the south of the walk: roofless, its walls broken lower toward the west where the tower
    came down; the east gable stands with its tall window over the altar."""
    r = P.CHURCH
    x0, z0, x1, z1 = r
    h = 10
    n = Noise(seed + 71, 3.0)
    k.walls(r, FEET, h)
    k.plinth_skirt(r, FEET, sides=('south', 'east'))
    k.buttresses(r, FEET, h, every=4, sides=('south', 'east'))
    for (x, z) in K.inside(r):
        v = n.at(x, z)
        t.set(x, FLOOR, z, k.floor(x, FLOOR, z) if v < 0.55 + (x - x0) * 0.01 else
              'minecraft:grass_block[snowy=false]' if v < 0.75 else 'minecraft:moss_block')
        for y in range(FEET, FEET + h + 8):
            t.set(x, y, z, AIR)
    # only the east gable keeps its stone; its tiles are a coping now
    k.gable(r, FEET + h, ridge='x', overhang=0, keep=lambda x, y, z: x >= x1, rafters=False)
    for z in range(z0 + 3, z1 - 2, 3):
        k.window(x0 + 0, FEET + 4, z, 'west', h=3, glass=False)
    for x in range(x0 + 4, x1 - 2, 5):
        k.window(x, FEET + 4, z1, 'south', h=4, glass=False)
        k.window(x, FEET + 4, z0, 'north', h=4, glass=False)
    # the east window: two lights under one head, the glass long gone
    for z in (35, 36, 37, 38):
        for y in range(FEET + 3, FEET + 10):
            t.set(x1, y, z, AIR if z in (36, 37) or y < FEET + 9 else k.wall(x1, y, z))
    for y in range(FEET + 3, FEET + 9):
        t.set(x1, y, 35, k.wall(x1, y, 35))
        t.set(x1, y, 38, k.wall(x1, y, 38))
    t.set(x1, FEET + 9, 36, stair('oldrepublic:watch_stone_stairs', 'north', 'top'))
    t.set(x1, FEET + 9, 37, stair('oldrepublic:watch_stone_stairs', 'south', 'top'))
    t.set(x1, FEET + 10, 36, 'minecraft:chiseled_stone_bricks')
    t.set(x1, FEET + 10, 37, 'minecraft:chiseled_stone_bricks')
    t.set(x1 + 1, FEET + 2, 36, stair('oldrepublic:watch_stone_stairs', 'west'))
    t.set(x1 + 1, FEET + 2, 37, stair('oldrepublic:watch_stone_stairs', 'west'))
    k.door(26, FEET, z0, 'north', width=3, height=4)       # from the walk
    k.door(40, FEET, z1, 'south', width=2, height=3)       # to the graves
    # the walls fall away toward the west: the tower took the west end with it
    cut = Noise(seed + 72, 4.0)
    for (x, z) in K.perimeter(r):
        if x >= x1 - 2:
            continue
        f = (x - x0) / float(x1 - 2 - x0)
        lo = FEET + 1 + int(round(5 * f))
        hi = FEET + 3 + int(round(6 * f))
        top = lo + int(round((hi - lo) * cut.at(x, z)))
        for y in range(top, FEET + h + 8):
            if t.get(x, y, z) is not None:
                t.set(x, y, z, None if y > FEET + h else AIR)
    for (x, z) in [(x, z) for x in range(x0, x1 + 2) for z in (z0 - 1, z1 + 1)]:
        if x < x1 - 2:
            top = None
            for y in range(FEET + h - 2, FEET, -1):
                if t.is_solid(x, y, z) and not t.is_solid(x, y, z + (1 if z == z0 - 1 else -1)):
                    t.set(x, y, z, None)
    # the altar end: two steps up, the altar, a reading desk, candles; a banner on the east wall
    for x in range(x1 - 4, x1):
        for z in range(z0 + 1, z1):
            t.set(x, FLOOR, z, 'minecraft:polished_andesite')
            if x == x1 - 4:
                t.set(x, FEET, z, slab('oldrepublic:watch_stone_slab'))
            elif x >= x1 - 3:
                t.set(x, FEET, z, 'minecraft:polished_andesite')
    t.set(x1 - 2, FEET + 1, 36, 'minecraft:chiseled_stone_bricks')
    t.set(x1 - 2, FEET + 1, 37, 'minecraft:chiseled_stone_bricks')
    t.set(x1 - 2, FEET + 2, 36, slab('minecraft:polished_andesite_slab'))
    t.set(x1 - 2, FEET + 2, 37, slab('minecraft:polished_andesite_slab'))
    K.candle(t, x1 - 2, FEET + 3, 36, n=3, color='white', lit=False)
    K.candle(t, x1 - 1, FEET + 1, 34, n=2, color='white', lit=False)
    K.candle(t, x1 - 1, FEET + 1, 39, n=4, color='white', lit=False)
    mod.lectern(t, x1 - 4, FEET + 1, 37, facing='west', book=True)
    mod.banner(t, x1 - 1, FEET + 6, 34, 'west', base='green')
    # meditation stones in the nave, some moved, some under turf
    for x in range(x0 + 14, x1 - 6, 3):
        for z in (z0 + 2, z1 - 2):
            if n.at(x * 2.0, z * 2.0) > 0.3:
                t.set(x, FEET, z, 'oldrepublic:meditation_stone')
    k.rubble(set((x, z) for (x, z) in K.inside(r) if x < x1 - 5), lambda x, z: FEET, density=0.45, seed=5)
    silver_tree(t, 30, FEET, 34, 11, seed + 73, boughs=3, spread=2.6)


def tower_stump(t, k, P, seed):
    """The bell tower: broken off, lowest on the north-east where it fell; its stair climbs into the air."""
    r = P.TOWER
    x0, z0, x1, z1 = r
    top = FEET + 15
    cn = Noise(seed + 81, 2.0)
    cx, cz = (x0 + x1) / 2.0, (z0 + z1) / 2.0
    for (x, z) in K.perimeter(r, thick=2):
        # high on the south-west, low on the north-east
        f = ((cx - x) + (z - cz)) / 8.0 + 0.5 + (cn.at(x, z) - 0.5) * 0.5
        f = max(0.0, min(1.0, f))
        cut = FEET + 3 + int(round(12 * f))
        for y in range(FLOOR, cut):
            if y == FLOOR:
                s = 'minecraft:stone_bricks'
            elif y == FEET:
                s = k.plinth(x, y, z)
            elif y == FEET + 8:
                s = WATCH['trim']
            else:
                s = k.old(x, y, z)
            t.set(x, y, z, s)
        for y in range(cut, P.SY):
            if t.get(x, y, z) is not None:
                t.set(x, y, z, AIR)
    for (x, z) in ((x0, z1), (x0, z0), (x1, z1)):
        for y in range(FEET + 3, top, 4):
            if t.is_solid(x, y, z):
                t.set(x, y, z, 'minecraft:chiseled_stone_bricks')
    ix0, iz0, ix1, iz1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            t.set(x, FLOOR, z, 'minecraft:stone_bricks')
            for y in range(FEET, P.SY):
                t.set(x, y, z, AIR)
    ring = [(x, iz1, 'east') for x in range(ix0, ix1 + 1)]
    ring += [(ix1, z, 'north') for z in range(iz1 - 1, iz0 - 1, -1)]
    ring += [(x, iz0, 'west') for x in range(ix1 - 1, ix0 - 1, -1)]
    ring += [(ix0, z, 'south') for z in range(iz0 + 1, iz1)]
    y, i = FEET, 0
    while y < FEET + 11:
        x, z, f = ring[i % len(ring)]
        t.set(x, y, z, stair('minecraft:stone_brick_stairs', f))
        for yy in range(FEET, y):
            if t.get(x, yy, z) == AIR:
                t.set(x, yy, z, 'minecraft:stone_bricks')
        y += 1
        i += 1
    # the doorway into the church, through the shared wall
    for z in (36, 37):
        for x in (x1 - 1, x1):
            for y in range(FEET, FEET + 3):
                t.set(x, y, z, AIR)
    # the breach where the tower tore open toward the walk
    for (x, z) in ((x1, z0), (x1 - 1, z0), (x1, z0 + 1), (x1 - 1, z0 + 1), (x1 - 2, z0), (x1, z0 + 2)):
        for y in range(FEET + 1 + (x + z) % 2, P.SY):
            if t.get(x, y, z) is not None:
                t.set(x, y, z, AIR)
    k.rubble({(x, z) for x in range(ix0, ix1 + 1) for z in range(iz0, iz1 + 1)}, lambda x, z: FEET,
             density=0.5, seed=15)
    K.lantern(t, ix1, FEET, iz0)


def dormitory(t, k, P, seed):
    """The dormitory on the north of the walk: its walls a course or three high, turf over its floor, a
    hearth's back still standing."""
    r = P.NORTH
    x0, z0, x1, z1 = r
    n = Noise(seed + 91, 3.0)
    k.walls(r, FEET, 6)
    for (x, z) in K.inside(r):
        v = n.at(x, z)
        t.set(x, FLOOR, z, k.floor(x, FLOOR, z) if v < 0.45 else
              'minecraft:grass_block[snowy=false]' if v < 0.72 else 'minecraft:moss_block')
        for y in range(FEET, FEET + 8):
            t.set(x, y, z, AIR)
    for x in range(x0 + 6, x1 - 1, 6):
        for z in range(z0 + 1, z1):
            for y in range(FEET, FEET + 5):
                t.set(x, y, z, k.wall_cell(x, y, z, FEET, 6))
    k.door(26, FEET, z1, 'south', width=2, height=3)
    cells = list(K.perimeter(r)) + [(x, z) for x in range(x0 + 6, x1 - 1, 6) for z in range(z0 + 1, z1)]
    k.break_top(cells, FEET + 1, FEET + 4, seed=17)
    # the hearth's chimney back stands taller on the north wall
    for y in range(FEET, FEET + 7):
        t.set(23, y, z0, 'minecraft:bricks' if y < FEET + 4 else 'minecraft:mud_bricks')
        t.set(24, y, z0, 'minecraft:bricks' if y < FEET + 3 else 'minecraft:mud_bricks')
    t.set(23, FEET, z0 + 1, S('minecraft:campfire', lit=False, signal_fire=False, facing='south', waterlogged=False))
    t.set(24, FEET, z0 + 1, 'minecraft:mud_bricks')
    # a bed frame left in a cell, a stool, a chest of drawers long empty
    K.bed(t, 16, FEET, z0 + 1, 'north', color='green')
    t.set(30, FEET, z1 - 1, 'minecraft:barrel[facing=up,open=true]')
    k.rubble(set(K.inside(r)), lambda x, z: FEET, density=0.35, seed=19)
    silver_tree(t, 35, FEET, z0 + 3, 7, seed + 93, boughs=2, spread=2.0)


def library_half(t, k, P, seed):
    """The library on the east: the north half under its roof, shelves and the house's chest; the south half
    open to the sky, its shelves fallen."""
    r = P.LIB
    x0, z0, x1, z1 = r
    h = 8
    zk = 21                          # the roof holds north of here
    n = Noise(seed + 101, 3.0)
    k.walls(r, FEET, h)
    k.plinth_skirt(r, FEET, sides=('east',))
    k.buttresses(r, FEET, h, every=4, sides=('east',))
    for (x, z) in K.inside(r):
        if z < zk:
            t.set(x, FLOOR, z, 'minecraft:spruce_planks' if (x + z) % 7 else 'minecraft:stripped_spruce_wood[axis=x]')
        else:
            v = n.at(x, z)
            t.set(x, FLOOR, z, 'minecraft:spruce_planks' if v < 0.4 else
                  'minecraft:moss_block' if v < 0.6 else 'minecraft:grass_block[snowy=false]')
        for y in range(FEET, FEET + h + 7):
            t.set(x, y, z, AIR)
    for z in range(z0 + 3, z1 - 1, 4):
        k.window(x1, FEET + 3, z, 'east', h=3, glass=z < zk and z % 8 == 5)
    k.door(x0, FEET, 15, 'west', width=3, height=4)
    k.door(x0, FEET, 27, 'west', width=2, height=3)
    k.gable(r, FEET + h, ridge='z', overhang=1, keep=lambda x, y, z: z < zk + (x + y) % 3 - 1)
    k.break_top([c for c in K.perimeter(r) if c[1] > zk + 2], FEET + 3, FEET + h - 1, seed=21)
    for z in range(z0 + 1, z1):
        if (z - z0 - 3) % 4 == 0:
            continue
        for y in (FEET, FEET + 1):
            if z < zk:
                t.set(x1 - 1, y, z, 'minecraft:bookshelf' if (z + y) % 5 else
                      'minecraft:chiseled_bookshelf[facing=west,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=false]')
            elif y == FEET and n.at(z * 3.0, 1.0) > 0.5:
                t.set(x1 - 1, y, z, 'minecraft:bookshelf')
    for z in range(z0 + 1, zk):
        if 15 <= z <= 17:
            continue
        for y in (FEET, FEET + 1):
            t.set(x0 + 1, y, z, 'minecraft:bookshelf' if (z * 3 + y) % 6 else
                  'minecraft:chiseled_bookshelf[facing=east,slot_0_occupied=false,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]')
    mod.lectern(t, 44, FEET, 13, facing='south', book=True)
    K.candle(t, 45, FEET, 13, n=2, color='white', lit=False)
    t.chest(44, FEET, 11, facing='south', loot='chests/selvaren/watch_cloister')
    t.set(43, FEET, 11, 'minecraft:barrel[facing=up,open=false]')
    t.set(45, FEET, 18, 'minecraft:cartography_table')
    K.hang_lantern(t, 44, FEET + 5, 16, 6)
    # the fallen shelves of the south half: books in the ferns
    for (x, z) in ((42, 25), (43, 25), (45, 28), (44, 29)):
        t.set(x, FEET, z, 'minecraft:bookshelf')
    t.set(43, FEET + 1, 25, slab('minecraft:spruce_slab'))
    k.rubble({(x, z) for (x, z) in K.inside(r) if z >= zk}, lambda x, z: FEET, density=0.4, seed=23)
    silver_tree(t, 46, FEET, 29, 9, seed + 103, boughs=2, spread=2.2)


def the_fall(t, k, P, seed):
    """The line the tower fell along: walls it broke, the heap of its stones, two big pieces of its masonry,
    and at the end of it the bell, lying in the turf under the beam it came down with."""
    a, b = P.FALL
    n = Noise(seed + 111, 2.0)
    # what it broke on the way down
    for x in range(P.TOWER[2] - 1, P.ARC[0] + 2):
        for z in range(P.ARC[3] - 2, P.CHURCH[1] + 3):
            d, f = seg_dist(x, z, a, b)
            if d < 1.6 + n.at(x, z):
                for y in range(FEET + 1 + int(2 * f + n.at(x, z, 3)), FEET + 9):
                    if t.is_solid(x, y, z) and (x, z) not in K.perimeter(P.TOWER, thick=2):
                        t.set(x, y, z, AIR)
    # the heap: deep near the tower, thinning toward the bell
    for x in range(0, P.SX):
        for z in range(0, P.SZ):
            d, f = seg_dist(x, z, a, b)
            if f <= 0.0 or f >= 0.97:
                continue
            if (x, z) in K.perimeter(P.TOWER, thick=2):
                continue
            width = 2.6 - 1.2 * f + (n.at(x, z) - 0.5) * 1.6
            if d > width:
                continue
            hgt = int(round((2.2 - 1.8 * f) * (1.0 - d / max(0.5, width)) + n.at(x, z, 7) * 0.8))
            y = FEET
            while y < P.SY and t.get(x, y, z) not in (None, AIR):
                y += 1
            for i in range(hgt):
                t.set(x, y + i, z, K.pickw(K.RUBBLE + [('oldrepublic:watch_stone', 3), ('minecraft:mossy_stone_bricks', 2),
                                                       ('minecraft:cracked_stone_bricks', 2)], k.rng.random()))
            top = y + max(0, hgt)
            if t.get(x, top, z) in (None, AIR) and k.rng.random() < 0.5:
                st = K.pickw(K.RUBBLE_LOOSE, k.rng.random())
                if 'facing=' in st:
                    st = B.with_props(st, facing=K.DIRS[k.rng.randrange(4)])
                t.set(x, top, z, st)
    # two pieces of the belfry that came down whole: a quoin block and an arch head lying on its side
    for (px, pz) in ((13, 31), (17, 28)):
        y = FEET
        while t.get(px, y, pz) not in (None, AIR):
            y += 1
        for dx in (0, 1):
            for dz in (0, 1):
                for dy in (0, 1):
                    t.set(px + dx, y + dy, pz + dz, k.old(px + dx, y + dy, pz + dz) if (dx + dy + dz) % 3 else
                          'minecraft:chiseled_stone_bricks')
        t.set(px, y + 2, pz, stair('oldrepublic:watch_stone_stairs', 'east', 'top'))
    # the bell, hanging from its broken beam where it came to rest
    bx, bz = P.BELL
    for x in range(bx - 2, bx + 3):
        for y in range(FEET, FEET + 3):
            t.set(x, y, bz, AIR)
    t.set(bx - 2, FEET, bz, 'minecraft:mossy_cobblestone')
    t.set(bx + 2, FEET, bz, 'minecraft:cobblestone')
    t.set(bx + 2, FEET + 1, bz, 'minecraft:mossy_stone_bricks')
    for x in range(bx - 2, bx + 4):
        t.set(x, FEET + 1 + (1 if x > bx + 1 else 0), bz, 'minecraft:stripped_dark_oak_log[axis=x]')
    t.set(bx + 1, FEET + 1, bz, 'minecraft:stripped_dark_oak_log[axis=x]')
    t.set(bx, FEET, bz, S('minecraft:bell', attachment='ceiling', facing='north', powered=False))
    t.set(bx, FLOOR, bz, 'minecraft:coarse_dirt')
    t.set(bx + 1, FLOOR, bz + 1, 'minecraft:coarse_dirt')
    t.set(bx - 1, FLOOR, bz - 1, 'minecraft:rooted_dirt')


def garth_fallen(t, k, P, seed):
    """The garth gone to meadow round the great silver tree; the paths are only a line in the grass, the basin
    at the crossing still holds rain."""
    a0, b0, a1, b1 = P.ARC
    cx, cz = (a0 + a1) // 2, (b0 + b1) // 2
    n = Noise(seed + 121, 2.5)
    cells = []
    for x in range(a0 + 1, a1):
        for z in range(b0 + 1, b1):
            path = (x == cx or z == cz) and n.at(x * 2.0, z * 2.0) > 0.35
            t.set(x, FLOOR, z, 'minecraft:coarse_dirt' if path and n.at(x, z, 5) > 0.5 else
                  'minecraft:dirt_path' if path else
                  'minecraft:moss_block' if n.at(x, z) > 0.7 else 'minecraft:grass_block[snowy=false]')
            if t.get(x, FEET, z) in (None,):
                t.set(x, FEET, z, AIR)
            if not path:
                cells.append((x, z))
    # the basin at the crossing
    t.set(cx, FLOOR, cz, 'minecraft:stone_bricks')
    t.set(cx, FEET, cz, S('minecraft:water_cauldron', level=3))
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set(cx + dx, FLOOR, cz + dz, 'minecraft:mossy_stone_bricks')
    # the great tree, its roots heaving the turf
    tx, tz = P.TREE
    trunk, leaves = silver_tree(t, tx, FEET, tz, 14, seed + 123, boughs=4, spread=3.6)
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1)):
        for i in range(2, 4 + (dx + dz) % 2):
            x, z = tx + dx * i, tz + dz * i
            if t.get(x, FEET, z) == AIR and n.at(x, z, 9) > 0.35:
                t.set(x, FEET, z, 'minecraft:birch_log[axis=%s]' % ('x' if dx and not dz else 'z' if dz and not dx else 'y'))
            elif t.name_at(x, FLOOR, z) in K.GROUND_NAMES:
                t.set(x, FLOOR, z, 'minecraft:rooted_dirt')
    for (x, z) in cells:
        v = n.at(x * 1.7, z * 1.7)
        if t.get(x, FEET, z) != AIR:
            continue
        if v > 0.72:
            t.set(x, FEET, z, 'oldrepublic:lumen_moss')
        elif v > 0.5 and t.get(x, FEET + 1, z) == AIR and k.rng.random() < 0.6:
            t.set(x, FEET, z, 'minecraft:tall_grass[half=lower]')
            t.set(x, FEET + 1, z, 'minecraft:tall_grass[half=upper]')
    plants(t, set(cells), 'shade', 0.6, seed + 125, y_hi=FEET)


def grounds_fallen(t, k, P, seed):
    n = Noise(seed + 131, 3.0)
    # the way in from the west, to the gate in the walk's wall
    for x in range(0, P.WALK[0]):
        for z in (20, 21, 22):
            if n.at(x, z) > 0.28 and t.get(x, FEET, z) in (None, AIR):
                t.set(x, FLOOR, z, 'minecraft:dirt_path' if n.at(x * 2, z * 2) > 0.45 else 'minecraft:coarse_dirt')
    for z in (19, 23):
        t.set(P.WALK[0] - 1, FEET, z, 'minecraft:mossy_stone_bricks')
        t.set(P.WALK[0] - 1, FEET + 1, z, 'minecraft:stone_brick_wall')
        t.set(P.WALK[0] - 1, FEET + 2, z, S('minecraft:lantern', hanging=False, waterlogged=False))
    # the old orchard on the north-west, inside a drystone wall gone to gaps
    for (x, z) in K.perimeter((1, 1, 12, 16)):
        if n.at(x * 1.5, z * 1.5) > 0.42 and not (5 <= x <= 7 and z == 16):
            t.set(x, FEET, z, 'minecraft:mossy_cobblestone_wall' if n.at(x, z, 3) > 0.45 else 'minecraft:cobblestone_wall')
    silver_tree(t, 4, FEET, 5, 8, seed + 133, boughs=2, spread=2.4)
    silver_tree(t, 9, FEET, 11, 7, seed + 135, boughs=2, spread=2.2)
    silver_tree(t, 5, FEET, 13, 6, seed + 137, boughs=0, spread=1.6, dead=True)
    # the graves south of the church
    for i, x in enumerate(range(14, 46, 4)):
        if i in (3, 6):
            continue
        z = 44 + (i % 2)
        field_stone(t, [(x, z)], FEET, [1 + (i % 3 == 0)], k.rng, top='slab' if i % 2 else 'stair', mossy=0.6)
        t.set(x, FLOOR, z - 1, 'minecraft:moss_block')
    plants(t, {(x, z) for x in range(P.SX) for z in range(P.SZ)}, 'meadow', 0.32, seed + 137, y_hi=FEET)


# ---------------------------------------------------------------- worldgen

def worldgen(templates):
    W.pool('selvaren/watch_cloister/start', [W.element('selvaren/watch_cloister/' + name, adapted=dict(
        yard_stand=FEET, clear_from=sy, clear_inset=2, support_depth=12, ramp_rings=2)) for name, sy in templates])
    W.structure('selvaren/watch_cloister', start_pool='selvaren/watch_cloister/start',
                biome_tag='has_structure/selvaren/watch_cloister', size=1, max_distance=64,
                step='surface_structures', start_height=1 - BURIED)
    W.structure_set('selvaren/watch_cloister', [('selvaren/watch_cloister', 1)], spacing=40, separation=14,
                    salt=960200, placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/selvaren/watch_cloister', ['oldrepublic:selvaren_meadow', 'oldrepublic:selvaren_highwood'])


def loot():
    loot_table('chests/selvaren/watch_cloister', [
        (1, [('oldrepublic:lumen_thread', 1, 3, 1)], None),
        (1, [('paper', 2, 5, 1)], None),
        ((2, 4), [('book', 1, 2, 4), ('candle', 1, 3, 5), ('emerald', 1, 2, 3), ('oldrepublic:aurelite_shard', 1, 2, 2),
                  ('experience_bottle', 1, 3, 2), ('oldrepublic:credit_chit', 2, 5, 3), ('golden_apple', 1, 1, 1)],
         None),
    ])
    loot_table('chests/selvaren/watch_cloister_2', [
        (1, [('bread', 2, 5, 1)], None),
        ((1, 3), [('charcoal', 1, 3, 4), ('candle', 1, 2, 3), ('oldrepublic:peat', 1, 3, 3), ('string', 1, 3, 2),
                  ('iron_nugget', 2, 5, 2), ('emerald', 1, 1, 1)], None),
    ])


def chests(t):
    return {'chest %d,%d,%d' % p: [p] for p, st in sorted(t.cells.items()) if B.name_of(st) == 'minecraft:chest'}


def main():
    loot()
    t = build_bell()
    t.finish()
    check_chests(t)
    belfry = FEET + 19
    check_reach(t, FEET, dict(chests(t), belfry=[(x, belfry, z) for x in range(37, 42) for z in range(7, 12)],
                              lectern=[(32, FEET, 9)]))
    save(t, 'selvaren/watch_cloister/bell', 'watch_cloister_bell')
    t = build_fallen()
    t.finish()
    check_chests(t)
    check_reach(t, FEET, dict(chests(t), altar=[(46, FEET + 1, 36)], bell=[(Fallen.BELL[0], FEET, Fallen.BELL[1])]))
    save(t, 'selvaren/watch_cloister/fallen', 'watch_cloister_fallen')
    worldgen([('bell', Bell.SY), ('fallen', Fallen.SY)])


if __name__ == '__main__':
    main()
