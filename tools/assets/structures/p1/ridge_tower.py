"""selvaren/ridge_tower: a Watch signal tower on the highwood ridges (docs/MILESTONE_6.md 5.5), two variants.

The Watch passed word across Selvaren by fire: towers taller than the silver trees, a fire basket in an iron
cage on each, a keeper in a hut at the foot. Some keepers stayed after the war.

- `kept` (19 x 42 x 21): a square tower of watch stone with clasping buttresses, a stair climbing its walls
  to a corbelled platform, the cage with its signal fire still burning (the smoke stands over the canopy);
  the keeper's cottage at the foot with the house's chest, a woodpile and a garden. Nobody is home.
- `cold` (21 x 36 x 19): a round tower, its top broken and the cage thrown down at its foot; ladders from
  floor to floor, one floor half gone; the keeper's cottage roofless with a tree in it.

Worldgen: pool selvaren/ridge_tower/start (both, adapted terrain), structure selvaren/ridge_tower in the
highwood, set 40/14, salt 960202, clear of the origin.
"""
import math

from selvaren_lib import (AIR, B, Noise, S, Template, W, WATCH, check_chests, check_reach, clear_bowl, ground,
                          loot_table, mod, nature, plants, save, silver_tree, slab, stair, weather)
import watchkit as K

SEED = 960202
BURIED = 3
FLOOR = BURIED - 1
FEET = BURIED
LOOT = 'chests/selvaren/ridge_tower'


def chests(t):
    return {'chest %d,%d,%d' % p: [p] for p, st in sorted(t.cells.items()) if B.name_of(st) == 'minecraft:chest'}


def overgrow(t, vines):
    nature.overgrow(t, region=((0, FEET, 0), (t.sx - 1, t.sy - 1, t.sz - 1)), vines=vines, moss=0.0, lichen=0.0,
                    wall_names=['oldrepublic:watch_stone', 'minecraft:mossy_stone_bricks', 'minecraft:stone_bricks',
                                'minecraft:cracked_stone_bricks', 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'])


# ---------------------------------------------------------------- kept

class Kept:
    SX, SY, SZ = 19, 42, 21
    TOWER = (5, 2, 13, 10)
    HUT = (2, 13, 10, 19)
    PLAT = FEET + 30              # feet on the platform
    CAGE = (7, 4, 11, 8)


def build_kept():
    P = Kept
    t = Template(P.SX, P.SY, P.SZ, seed=SEED, name='selvaren/ridge_tower/kept')
    k = K.Kit(t, SEED)
    cols = {(x, z) for x in range(P.SX) for z in range(P.SZ)}
    ground(t, cols, FLOOR, SEED + 11, kind='highwood')
    clear_bowl(t, cols, 9, 10, FEET, P.SY - 1, r_full=8.5, r_out=13.0, rim_h=5)
    # the trees first: the masonry takes back any cell a bough reached into
    silver_tree(t, 17, FEET, 2, 15, SEED + 31, boughs=3, spread=2.4)
    silver_tree(t, 1, FEET, 4, 12, SEED + 33, boughs=2, spread=2.0)
    silver_tree(t, 16, FEET, 19, 11, SEED + 35, boughs=2, spread=2.4)
    square_tower(t, k, P)
    cage(t, k, P, lit=True)
    cottage(t, k, P, SEED, roofed=True)
    yard(t, k, P)
    weather(t, seed=SEED + 21, patchiness=0.5)
    overgrow(t, 0.07)
    plants(t, cols, 'highwood', 0.35, SEED + 41, y_hi=FEET)
    mod.light_fill(t, (1, FEET, 1, 17, P.PLAT, 19), level=8, strength=12)
    return t


def square_tower(t, k, P):
    """A square tower of watch stone: clasping buttresses at the corners, string courses every eight, slit
    windows, a stair climbing its four walls round an open shaft to the platform."""
    x0, z0, x1, z1 = P.TOWER
    F = P.PLAT - 1
    for (x, z) in K.perimeter(P.TOWER):
        for y in range(FLOOR, F + 1):
            if y == FLOOR:
                s = 'minecraft:stone_bricks'
            elif y == FEET:
                s = k.plinth(x, y, z)
            elif (y - FEET) % 8 == 0 or y == F:
                s = WATCH['trim']
            else:
                s = k.wall(x, y, z)
            t.set(x, y, z, s)
    # quoins
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(FEET + 15, F, 3):
            t.set(x, y, z, 'minecraft:chiseled_stone_bricks')
    # clasping buttresses: two cells proud on each face of every corner, stepping back under sloped caps
    for (cx, cz, sx, sz) in ((x0, z0, -1, -1), (x1, z0, 1, -1), (x0, z1, -1, 1), (x1, z1, 1, 1)):
        for (bx, bz, top, face) in ((cx + sx, cz, FEET + 13, 'east' if sx < 0 else 'west'),
                                    (cx, cz + sz, FEET + 13, 'south' if sz < 0 else 'north'),
                                    (cx + sx, cz + sz, FEET + 9, 'east' if sx < 0 else 'west')):
            for y in range(FLOOR, top):
                t.set(bx, y, bz, k.plinth(bx, y, bz) if y <= FEET + 1 else k.wall(bx, y, bz))
            t.set(bx, top, bz, stair('oldrepublic:watch_stone_stairs', face))
    k.plinth_skirt(P.TOWER, FEET, sides=('north', 'east', 'west'))
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            t.set(x, FLOOR, z, 'minecraft:stone_bricks' if (x + z) % 3 else 'minecraft:polished_andesite')
            for y in range(FEET, F + 1):
                t.set(x, y, z, AIR)
    # the stair: from the middle of the south wall, east, round the four walls
    mid = (ix0 + ix1) // 2
    ring = [(x, iz1, 'east') for x in range(mid, ix1 + 1)]
    ring += [(ix1, z, 'north') for z in range(iz1 - 1, iz0 - 1, -1)]
    ring += [(x, iz0, 'west') for x in range(ix1 - 1, ix0 - 1, -1)]
    ring += [(ix0, z, 'south') for z in range(iz0 + 1, iz1 + 1)]
    ring += [(x, iz1, 'east') for x in range(ix0 + 1, mid)]
    L = len(ring)
    y, i = FEET, 0
    while y <= F:
        x, z, f = ring[i % L]
        t.set(x, y, z, stair('minecraft:stone_brick_stairs', f))
        for yy in range(max(FEET, y - L + 4), y):
            if t.get(x, yy, z) == AIR:
                t.set(x, yy, z, 'minecraft:stone_bricks')
        y += 1
        i += 1
    holes = {(c[0], c[1]) for c in [ring[(i - j) % L] for j in range(1, 6)]}
    # the platform: planks over the shaft, the wall heads, a corbelled walk round them, a parapet
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            if (x, z) not in holes:
                if t.get(x, F, z) == AIR:
                    t.set(x, F, z, 'minecraft:spruce_planks')
    for (x, z) in K.perimeter((x0 - 1, z0 - 1, x1 + 1, z1 + 1)):
        side = 'north' if z == z0 - 1 else 'south' if z == z1 + 1 else 'west' if x == x0 - 1 else 'east'
        if (x, z) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
            t.set(x, F - 1, z, 'minecraft:chiseled_stone_bricks')
        else:
            t.set(x, F - 1, z, stair('oldrepublic:watch_stone_stairs', B.opposite(side), 'top'))
        t.set(x, F, z, WATCH['trim'])
        corner = x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1)
        merlon = corner or (x - x0) % 4 == 0 and z in (z0 - 1, z1 + 1) or (z - z0) % 4 == 0 and x in (x0 - 1, x1 + 1)
        if merlon:
            t.set(x, F + 1, z, k.wall(x, F + 1, z))
            t.set(x, F + 2, z, slab('oldrepublic:watch_stone_slab'))
        else:
            t.set(x, F + 1, z, 'minecraft:stone_brick_wall')
    # the door: south, two wide, onto the first step and the landing beside it
    k.door(mid - 1, FEET, z1, 'south', width=2, height=4)
    t.set(mid - 1, FLOOR, z1 + 1, 'minecraft:polished_andesite')
    t.set(mid, FLOOR, z1 + 1, 'minecraft:polished_andesite')
    # slit windows up the faces, staggered with the stair
    for side in K.DIRS:
        cells = K.side_cells(P.TOWER, side)
        for n, (x, z) in enumerate(cells):
            if n not in (2, 6):
                continue
            for y in range(FEET + 4 + (n + len(side)) % 5, F - 3, 6):
                t.set(x, y, z, AIR)
                t.set(x, y + 1, z, AIR)
    # a store in the foot of the shaft
    t.set(ix0 + 1, FEET, iz0 + 1, 'minecraft:barrel[facing=up,open=false]')
    t.set(ix0 + 2, FEET, iz0 + 1, 'minecraft:barrel[facing=up,open=false]')
    t.set(ix0 + 1, FEET + 1, iz0 + 1, 'minecraft:barrel[facing=up,open=false]')
    t.set(ix1 - 1, FEET, iz0 + 1, S('minecraft:hay_block', axis='x'))
    t.set(ix1 - 1, FEET, iz0 + 2, S('minecraft:hay_block', axis='z'))
    K.lantern(t, ix0 + 1, FEET + 2, iz0 + 1)


def cage(t, k, P, lit):
    """The beacon: a fire basket on a hay bale (the signal fire's smoke stands high), in an iron cage under a
    small tiled roof with a smoke hole, lanterns at its corners."""
    x0, z0, x1, z1 = P.CAGE
    y0 = P.PLAT
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            t.set(x, y0 - 1, z, 'minecraft:polished_andesite')
            t.set(x, y0, z, 'minecraft:stone_brick_wall' if (x, z) != (cx, cz) else 'minecraft:hay_block[axis=y]')
    t.set(cx, y0 + 1, cz, S('minecraft:campfire', lit=lit, signal_fire=True, facing='south', waterlogged=False))
    for (x, z) in K.perimeter(P.CAGE):
        post = x in (x0, x1) and z in (z0, z1)
        for y in range(y0, y0 + 4):
            if post:
                t.set(x, y, z, 'minecraft:stone_brick_wall' if y < y0 + 3 else 'minecraft:chiseled_stone_bricks')
            elif (x, z) == (cx, z1) and y < y0 + 2:
                t.set(x, y, z, AIR)               # the keeper's gap
            else:
                t.set(x, y, z, 'minecraft:iron_bars')
    # the roof: one ring of tiles, the smoke hole open over the fire
    top = y0 + 4
    for (x, z) in K.perimeter((x0 - 1, z0 - 1, x1 + 1, z1 + 1)):
        side = 'north' if z == z0 - 1 else 'south' if z == z1 + 1 else 'west' if x == x0 - 1 else 'east'
        t.set(x, top - 1, z, stair(k.roof_tile(x, top - 1, z, 0.0), B.opposite(side)))
    for (x, z) in K.perimeter(P.CAGE):
        side = 'north' if z == z0 else 'south' if z == z1 else 'west' if x == x0 else 'east'
        t.set(x, top, z, stair(k.roof_tile(x, top, z, 0.0), B.opposite(side)))
    for (x, z) in K.perimeter((x0 + 1, z0 + 1, x1 - 1, z1 - 1)):
        if (x, z) != (cx, cz):
            t.set(x, top + 1, z, slab('minecraft:deepslate_tile_slab'))
    t.set(x0, top + 1, z0, S('minecraft:lightning_rod', facing='up', powered=False, waterlogged=False))
    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z1 - 1)):
        K.hang_lantern(t, x, top - 2, z, 3)


def cottage(t, k, P, seed, roofed):
    """The keeper's cottage: stone walls, a steep tiled roof, the hearth and chimney at the west end, a bed,
    a table, the logbook on its desk, the house's chest."""
    r = P.HUT
    x0, z0, x1, z1 = r
    h = 5
    k.walls(r, FEET, h)
    k.plinth_skirt(r, FEET, sides=('south', 'east'))
    for (x, z) in K.inside(r):
        t.set(x, FLOOR, z, 'minecraft:spruce_planks' if (x * 3 + z) % 5 else 'minecraft:stripped_spruce_wood[axis=x]')
        for y in range(FEET, FEET + h + 6):
            t.set(x, y, z, AIR)
    mz = (z0 + z1) // 2
    k.door(6, FEET, z0, 'north', width=2, height=3)
    k.window(x0 + 2, FEET + 1, z1, 'south', h=2, glass=roofed)
    k.window(x1 - 2, FEET + 1, z1, 'south', h=2, glass=roofed)
    k.window(x1, FEET + 1, mz, 'east', h=2, glass=roofed)
    if roofed:
        k.gable(r, FEET + h, ridge='x', overhang=1)
    # the hearth and its chimney in the west wall
    for y in range(FEET, FEET + h + 6 if roofed else FEET + h + 2):
        t.set(x0, y, mz, 'minecraft:bricks' if y < FEET + 3 else 'minecraft:mud_bricks')
        t.set(x0 - 1, y, mz, 'minecraft:cobblestone' if y < FEET + 2 else 'minecraft:mud_bricks')
    for z in (mz - 1, mz + 1):
        t.set(x0 + 1, FEET, z, 'minecraft:bricks')
        t.set(x0 + 1, FEET + 1, z, slab('minecraft:brick_slab', 'top'))
    t.set(x0 + 1, FEET, mz, S('minecraft:campfire', lit=False, signal_fire=False, facing='east', waterlogged=False))
    t.set(x0 + 1, FEET + 1, mz, 'minecraft:bricks')
    t.set(x0 + 1, FEET + 2, mz, 'minecraft:bricks')
    # furniture
    K.bed(t, x1 - 1, FEET, z1 - 1, 'east', color='green')
    t.chest(x1 - 1, FEET, z0 + 1, facing='west', loot=LOOT)
    t.set(x1 - 2, FEET, z0 + 1, 'minecraft:barrel[facing=up,open=false]')
    mod.lectern(t, x0 + 2, FEET, z0 + 1, facing='south', book=True)
    t.set(x0 + 3, FEET, z1 - 1, 'minecraft:spruce_fence')
    t.set(x0 + 3, FEET + 1, z1 - 1, 'minecraft:spruce_pressure_plate[powered=false]')
    t.set(x0 + 2, FEET, z1 - 1, stair('minecraft:spruce_stairs', 'west'))
    t.set(x0 + 4, FEET, z1 - 1, stair('minecraft:spruce_stairs', 'east'))
    t.set(x0 + 1, FEET, z1 - 1, 'minecraft:crafting_table')
    if roofed:
        K.hang_lantern(t, x0 + 4, FEET + 3, mz, 3)


def yard(t, k, P):
    """The woodpile along the cottage, the garden beyond it, the path to the tower door, a bench."""
    x0, z0, x1, z1 = P.HUT
    for z in range(z0 + 1, z1):
        t.set(x1 + 1, FEET, z, 'minecraft:spruce_log[axis=z]')
        if z < z1 - 1:
            t.set(x1 + 1, FEET + 1, z, 'minecraft:spruce_log[axis=z]')
    t.set(x1 + 1, FEET + 2, z0 + 2, slab('minecraft:spruce_slab'))
    for x in range(12, 17):
        for z in range(14, 18):
            if x in (12, 16) or z in (14, 17):
                t.set(x, FEET, z, 'minecraft:spruce_fence')
                continue
            t.set(x, FLOOR, z, 'minecraft:farmland[moisture=7]')
            t.set(x, FEET, z, S('minecraft:carrots' if x % 2 else 'minecraft:potatoes', age=7))
    t.set(14, FEET, 14, S('minecraft:spruce_fence_gate', facing='north', open=False, in_wall=False, powered=False))
    t.set(13, FEET, 18, S('minecraft:water_cauldron', level=3))
    t.set(17, FEET, 15, S('minecraft:composter', level=3))
    # the path between the doors
    n = Noise(SEED + 51, 2.0)
    for (x, z) in ((8, 11), (9, 11), (8, 12), (7, 12), (6, 12), (9, 12)):
        t.set(x, FLOOR, z, 'minecraft:dirt_path' if n.at(x, z) > 0.3 else 'minecraft:coarse_dirt')
    t.set(11, FEET, 11, stair('minecraft:spruce_stairs', 'north'))
    t.set(12, FEET, 11, stair('minecraft:spruce_stairs', 'north'))


# ---------------------------------------------------------------- cold

class Cold:
    SX, SY, SZ = 21, 36, 19
    C = (10, 7)                   # the tower's middle
    R_OUT, R_IN = 4.7, 3.4
    LEVELS = (FEET + 7, FEET + 14, FEET + 21, FEET + 28)     # floor layers; the last is the top
    HUT = (1, 12, 8, 17)


def build_cold():
    P = Cold
    seed = SEED + 1000
    t = Template(P.SX, P.SY, P.SZ, seed=seed, name='selvaren/ridge_tower/cold')
    k = K.Kit(t, seed)
    cols = {(x, z) for x in range(P.SX) for z in range(P.SZ)}
    ground(t, cols, FLOOR, seed + 11, kind='highwood')
    clear_bowl(t, cols, 10, 9, FEET, P.SY - 1, r_full=8.5, r_out=13.5, rim_h=5)
    silver_tree(t, 18, FEET, 2, 13, seed + 31, boughs=3, spread=2.4)
    silver_tree(t, 2, FEET, 3, 9, seed + 33, boughs=0, spread=1.5, dead=True)
    silver_tree(t, 17, FEET, 16, 12, seed + 35, boughs=2, spread=2.6)
    round_tower(t, k, P, seed)
    thrown_cage(t, k, P, seed)
    cottage(t, k, P, seed, roofed=False)
    cold_cottage(t, k, P, seed)
    weather(t, seed=seed + 21, patchiness=0.7)
    overgrow(t, 0.16)
    plants(t, cols, 'highwood', 0.45, seed + 41, y_hi=FEET)
    mod.light_fill(t, (1, FEET, 1, 15, P.LEVELS[-1] + 1, 17), level=8, strength=12)
    return t


def round_tower(t, k, P, seed):
    """A round tower: one wall of watch stone, wooden floors every seven courses with ladders between them up
    the inside of the wall, the top broken away toward the east, the third floor half fallen."""
    cx, cz = P.C
    n = Noise(seed + 61, 2.0)
    top_floor = P.LEVELS[-1]
    cells = {}
    for x in range(cx - 6, cx + 7):
        for z in range(cz - 6, cz + 7):
            d = math.hypot(x - cx, z - cz)
            if d <= P.R_OUT:
                cells[(x, z)] = d
    wall = {c for c, d in cells.items() if d > P.R_IN}
    inner = {c for c, d in cells.items() if d <= P.R_IN}
    for (x, z) in sorted(wall):
        # broken lower toward the east, where the cage went over
        ang = math.atan2(z - cz, x - cx)
        f = 0.5 + 0.5 * math.cos(ang) + (n.at(x, z) - 0.5) * 0.6
        cut = top_floor + 3 - int(round(7 * max(0.0, min(1.0, f))))
        for y in range(FLOOR, cut):
            if y == FLOOR:
                s = 'minecraft:stone_bricks'
            elif y == FEET:
                s = k.plinth(x, y, z)
            elif y in P.LEVELS:
                s = WATCH['trim']
            else:
                s = k.old(x, y, z) if y > FEET + 18 else k.wall(x, y, z)
            t.set(x, y, z, s)
        for y in range(cut, t.sy):
            if t.get(x, y, z) is None:
                t.set(x, y, z, AIR)
    for (x, z) in sorted(inner):
        t.set(x, FLOOR, z, 'minecraft:stone_bricks')
        for y in range(FEET, t.sy):
            t.set(x, y, z, AIR)
    # a battered plinth round the foot
    for (x, z) in sorted(wall):
        for (dx, dz, side) in ((1, 0, 'east'), (-1, 0, 'west'), (0, 1, 'south'), (0, -1, 'north')):
            q = (x + dx, z + dz)
            if q not in cells and t.get(q[0], FEET, q[1]) in (None, AIR):
                t.set(q[0], FEET, q[1], stair('minecraft:mossy_cobblestone_stairs' if n.at(*q, 3) > 0.5 else
                                              'minecraft:cobblestone_stairs', B.opposite(side)))
    # floors with their ladder holes: north, east, south, west in turn
    ladders = [((cx, cz - 3), 'south'), ((cx + 3, cz), 'west'), ((cx, cz + 3), 'north'), ((cx - 3, cz), 'east')]
    gone = Noise(seed + 63, 2.5)
    for li, fy in enumerate(P.LEVELS):
        lx, lz = ladders[li][0]
        for (x, z) in sorted(inner):
            if (x, z) == (lx, lz):
                continue
            if li == 2 and gone.at(x, z) + (x - cx) * 0.12 > 0.62:          # the third floor, half fallen
                continue
            if li == 3 and (x - cx) + (n.at(x, z, 9) - 0.5) * 3 > 1.0:       # the top, broken to the east
                continue
            t.set(x, fy, z, 'minecraft:spruce_planks' if (x + z + li) % 4 else 'minecraft:stripped_spruce_log[axis=x]')
        if li == 3:
            for (x, z) in sorted(inner):
                if t.get(x, fy, z) not in (None, AIR):
                    t.set(x, fy - 1, z, 'minecraft:dark_oak_planks' if (x + z) % 2 else 'minecraft:spruce_planks')
        # the ladder from the floor below up through this one, the wall behind it whole to its top
        below = FEET if li == 0 else P.LEVELS[li - 1] + 1
        bx, bz = lx - K.STEP[ladders[li][1]][0], lz - K.STEP[ladders[li][1]][1]
        for y in range(below, fy + 2):
            t.set(lx, y, lz, S('minecraft:ladder', facing=ladders[li][1], waterlogged=False))
            if not t.is_solid(bx, y, bz):
                t.set(bx, y, bz, k.old(bx, y, bz))
    # the doorway on the south, the first ladder's foot a step inside
    for y in range(FEET, FEET + 3):
        t.set(cx, y, cz + 4, AIR)
    t.set(cx, FEET + 3, cz + 4, 'minecraft:chiseled_stone_bricks')
    t.set(cx - 1, FEET + 2, cz + 4, stair('oldrepublic:watch_stone_stairs', 'west', 'top'))
    t.set(cx + 1, FEET + 2, cz + 4, stair('oldrepublic:watch_stone_stairs', 'east', 'top'))
    t.set(cx, FLOOR, cz + 5, 'minecraft:polished_andesite')
    # slit windows: one per floor on each quarter
    for li, fy in enumerate((FEET,) + P.LEVELS[:-1]):
        for (dx, dz) in ((0, -4), (4, 0), (0, 4), (-4, 0)):
            if (dx, dz) == (0, 4) and li == 0:
                continue
            x, z = cx + dx, cz + dz
            if t.is_solid(x, fy + 3, z):
                t.set(x, fy + 3, z, AIR)
                t.set(x, fy + 4, z, AIR)
    # what lies on the floors: rubble from above, a barrel, a bedroll
    k.rubble({c for c in inner if c[0] > cx}, lambda x, z: P.LEVELS[0] + 1 if t.get(x, P.LEVELS[0], z) not in (None, AIR)
             else None, density=0.5, seed=7)
    k.rubble(set(inner), lambda x, z: FEET, density=0.25, seed=9)
    t.set(cx - 2, FEET, cz - 1, 'minecraft:barrel[facing=up,open=true]')
    t.set(cx - 2, P.LEVELS[1] + 1, cz + 1, 'minecraft:brown_carpet')
    t.set(cx - 2, P.LEVELS[1] + 1, cz + 2, 'minecraft:brown_carpet')
    # what is left of the cage on the top: two posts and a few bars
    ty = top_floor + 1
    for (x, z) in ((cx - 2, cz - 2), (cx - 2, cz + 2)):
        if t.get(x, top_floor, z) not in (None, AIR):
            t.set(x, ty, z, 'minecraft:stone_brick_wall')
            t.set(x, ty + 1, z, 'minecraft:stone_brick_wall')
    for z in (cz - 1, cz + 1):
        if t.get(cx - 2, top_floor, z) not in (None, AIR):
            t.set(cx - 2, ty, z, 'minecraft:iron_bars')
    if t.get(cx - 1, top_floor, cz - 2) not in (None, AIR):
        t.set(cx - 1, ty, cz - 2, 'minecraft:iron_bars')


def thrown_cage(t, k, P, seed):
    """The cage at the tower's foot, thrown down by a storm: bars, the fire basket's stones, a burst hay bale,
    the cold campfire, tiles, the lightning rod lying in the grass."""
    cx, cz = P.C
    rng = t.rng
    spots = [(cx + 6, cz - 1), (cx + 7, cz + 1), (cx + 6, cz + 3), (cx + 8, cz + 2), (cx + 7, cz - 2),
             (cx + 9, cz), (cx + 5, cz + 5), (cx + 8, cz + 4)]
    for i, (x, z) in enumerate(spots):
        if not t.inside(x, FEET, z):
            continue
        kind = i % 4
        if kind == 0:
            t.set(x, FEET, z, 'minecraft:iron_bars')
            if rng.random() < 0.5:
                t.set(x, FEET + 1, z, 'minecraft:iron_bars')
        elif kind == 1:
            t.set(x, FEET, z, stair(k.roof_tile(x, FEET, z, 0.2), K.DIRS[rng.randrange(4)]))
        elif kind == 2:
            t.set(x, FEET, z, 'minecraft:stone_brick_wall')
        else:
            t.set(x, FEET, z, slab('minecraft:deepslate_tile_slab'))
    t.set(cx + 7, FEET, cz, S('minecraft:hay_block', axis='x'))
    t.set(cx + 8, FEET, cz - 1, S('minecraft:campfire', lit=False, signal_fire=False, facing='west', waterlogged=False))
    t.set(cx + 6, FEET, cz + 1, S('minecraft:lightning_rod', facing='east', powered=False, waterlogged=False))
    t.set(cx + 5, FEET, cz + 2, 'minecraft:iron_chain[axis=x,waterlogged=false]')
    for (x, z) in ((cx + 6, cz), (cx + 7, cz + 3), (cx + 9, cz + 1)):
        if t.get(x, FEET, z) in (None, AIR):
            t.set(x, FEET, z, 'minecraft:iron_bars')
    k.rubble({(x, z) for x in range(cx + 5, cx + 10) for z in range(cz - 3, cz + 5)}, lambda x, z: FEET,
             density=0.35, seed=13)


def cold_cottage(t, k, P, seed):
    """The cottage gone cold: walls broken, the roof down inside it, a young tree, the chest under the fallen
    rafters' corner."""
    r = P.HUT
    x0, z0, x1, z1 = r
    k.break_top([c for c in K.perimeter(r) if not (c[0] == x0 and c[1] in range((z0 + z1) // 2 - 1, (z0 + z1) // 2 + 2))],
                FEET + 2, FEET + 4, seed=17)
    for x in range(x0 + 1, x1 - 2):
        if x % 2:
            t.set(x, FEET + 3, (z0 + z1) // 2, 'minecraft:stripped_dark_oak_log[axis=x]')
    for (x, z) in ((x0 + 2, z0 + 1), (x0 + 3, z1 - 1), (x1 - 2, z0 + 2)):
        t.set(x, FEET, z, stair(k.roof_tile(x, FEET, z, 0.2), K.DIRS[(x + z) % 4]))
    k.rubble(set(K.inside(r)), lambda x, z: FEET, density=0.3, seed=19)
    silver_tree(t, x0 + 3, FEET, z0 + 2, 7, seed + 71, boughs=2, spread=1.8)


# ---------------------------------------------------------------- worldgen

def worldgen(templates):
    W.pool('selvaren/ridge_tower/start', [W.element('selvaren/ridge_tower/' + name, adapted=dict(
        yard_stand=FEET, clear_from=sy, clear_inset=1, support_depth=14, ramp_rings=2)) for name, sy in templates])
    W.structure('selvaren/ridge_tower', start_pool='selvaren/ridge_tower/start',
                biome_tag='has_structure/selvaren/ridge_tower', size=1, max_distance=32,
                step='surface_structures', start_height=1 - BURIED)
    W.structure_set('selvaren/ridge_tower', [('selvaren/ridge_tower', 1)], spacing=40, separation=14,
                    salt=960202, placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/selvaren/ridge_tower', ['oldrepublic:selvaren_highwood'])


def loot():
    loot_table(LOOT, [
        (1, [('torch', 3, 8, 1)], None),
        ((2, 4), [('bread', 1, 3, 4), ('cooked_mutton', 1, 3, 3), ('charcoal', 2, 6, 4), ('string', 1, 3, 2),
                  ('leather', 1, 3, 2), ('iron_ingot', 1, 2, 2), ('emerald', 1, 2, 2),
                  ('oldrepublic:lumen_thread', 1, 2, 1), ('oldrepublic:medpac', 1, 1, 1),
                  ('oldrepublic:credit_chit', 1, 4, 2)], None),
    ])


def main():
    loot()
    t = build_kept()
    t.finish()
    check_chests(t)
    P = Kept
    check_reach(t, FEET, dict(chests(t), platform=[(5, P.PLAT, 2), (13, P.PLAT, 10)], lectern=[(4, FEET, 14)]))
    save(t, 'selvaren/ridge_tower/kept', 'ridge_tower_kept')
    t = build_cold()
    t.finish()
    check_chests(t)
    cx, cz = Cold.C
    check_reach(t, FEET, dict(chests(t), top=[(cx - 2, Cold.LEVELS[-1] + 1, cz)]))
    save(t, 'selvaren/ridge_tower/cold', 'ridge_tower_cold')
    worldgen([('kept', Kept.SY), ('cold', Cold.SY)])


if __name__ == '__main__':
    main()
