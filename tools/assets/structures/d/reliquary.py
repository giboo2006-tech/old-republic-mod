#!/usr/bin/env python3
"""The Reliquary of Orren Vasch, the Collector (docs/MILESTONE_6.md 5.8): two runtime templates.

  boss/reliquary_surface  29 x 24 x 29, new, buried 9: the watchtower that fell into a sinkhole. The ground
                          has opened into a bell-shaped cavern whose walls still glitter with the crystals the
                          Collector left; the tower's newel stands alone in the void and its spiral stair winds
                          down around it from a bridge at the tower door to a well at the newel's foot, where the
                          hall's own spiral takes over. A crack runs from the cavern through the tower's split
                          north-west wall: it goes down to the hall's ceiling and lets one blade of daylight
                          into the arena.
  boss/reliquary_hall     31 x 18 x 31, kept: mechanism, seals, anchors and the spiral exact; re-dressed.

Alignment (unchanged rule of ReliquarySites.hallOriginUnder): the hall's top layer lies directly under the
surface's layer 0 and the hall's SHAFT column (24, 25) lies under the surface's SHAFT column (14, 14), so the
hall's origin is the surface's origin + (-10, -18, -11), unrotated. The anchors:
  surface SHAFT (14, 8, 14): the newel block in the ground layer (the shaft's top cell)
  hall    SHAFT (24, 8, 25): the newel's foot at the antechamber floor (the shaft's foot)

Usage: python3 tools/assets/structures/d/reliquary.py [surface] [hall]   (D_PREVIEW=<folder> renders previews)
"""
import math

from common import (S, AIR, B, Noise, DTemplate, load, mechanism, protect_routes, check_reach, mixer, hash01,
                    save, fill_light, stairs, slab, wall, fence, bars, chain, lantern, candle, cobweb, skull, light,
                    cluster, is_air_set, mod, SIDES, write_meta)
import tomb_kit as K

SX, SY, SZ = 29, 24, 29
BURIED = 9
GROUND = BURIED - 1
FEET = BURIED
C = 14                                  # the newel's centre column (x and z)
SHAFT = (C, GROUND, C)
HALL_SHAFT = (24, 8, 25)
HALL_OFFSET = (C - HALL_SHAFT[0], -18, C - HALL_SHAFT[2])   # hall origin - surface origin


def to_hall(x, y, z):
    return (x - HALL_OFFSET[0], y - HALL_OFFSET[1], z - HALL_OFFSET[2])


# The bell: the cavern's radius by layer (the opening at the ground layer, widest near the floor).
BELL = {8: 5.2, 7: 6.3, 6: 7.4, 5: 8.3, 4: 8.9, 3: 9.3, 2: 9.5, 1: 9.5}

# The stair down around the newel, counterclockwise seen from above: (cells, feet layer, facing up-hill).
PATH = [
    ([(14, 11), (14, 12), (15, 11), (15, 12)], 9, None),
    ([(13, 11), (13, 12)], 8, 'east'),
    ([(11, 11), (12, 11), (11, 12), (12, 12)], 7, None),
    ([(11, 13), (12, 13)], 6, 'north'),
    ([(11, 14), (12, 14)], 5, 'north'),
    ([(11, 15), (12, 15)], 4, 'north'),
    ([(11, 16), (12, 16), (11, 17), (12, 17)], 3, None),
    ([(13, 16), (13, 17)], 2, 'west'),
    ([(14, 16), (14, 17)], 1, 'west'),
]
WELL = [(15, 16), (16, 14), (16, 15), (16, 16), (15, 17), (16, 17)]
# The crack: from the cavern's north-west wall up through the split tower wall and away under the ground.
CRACK = [(10, 10), (10, 9), (9, 9), (9, 8), (8, 8), (8, 7), (8, 6), (8, 5), (7, 5), (7, 4), (7, 3), (6, 2)]


def dist(x, z):
    return math.hypot(x - C, z - C)


def angle(x, z):
    return math.atan2(z - C, x - C)


def surface_materials(seed):
    m = {}
    m['brick'] = mixer(seed, [('stone_bricks', 6), ('cracked_stone_bricks', 3), ('mossy_stone_bricks', 3),
                              ('cobblestone', 1)], 3.0)
    m['rubble'] = mixer(seed + 1, [('cobblestone', 4), ('mossy_cobblestone', 2), ('gravel', 3), ('stone', 2),
                                   ('tuff', 2), ('cracked_stone_bricks', 2)], 1.8)
    m['ground'] = mixer(seed + 2, [('coarse_dirt', 4), ('gravel', 3), ('stone', 2), ('cobblestone', 1),
                                   ('rooted_dirt', 1)], 2.5)
    band = Noise(seed + 3, 4.0)

    def rock(x, y, z):
        v = y + (band.at(x, 0, z) - 0.5) * 2.0
        h = hash01(seed + 4, x, y, z)
        if v < 2.5:
            return 'deepslate' if h < 0.6 else 'cobbled_deepslate' if h < 0.8 else 'tuff'
        if v < 5.5:
            return 'tuff' if h < 0.5 else 'stone' if h < 0.8 else 'andesite'
        return 'stone' if h < 0.55 else 'andesite' if h < 0.75 else 'gravel' if h < 0.85 else 'dirt'
    m['rock'] = rock
    m['deep'] = mixer(seed + 5, [('polished_deepslate', 5), ('deepslate_bricks', 3), ('cracked_deepslate_bricks', 2)],
                      2.5)
    return m


def build_surface():
    tid = 'boss/reliquary_surface'
    t = DTemplate(SX, SY, SZ, seed=920101)
    t.name = tid
    m = surface_materials(920102)
    wobble = Noise(920103, 2.6)

    def bell(x, y, z):
        r = BELL.get(y)
        return r is not None and dist(x, z) < r + (wobble.at(x, y, z) - 0.5) * 1.6

    # The ruin's ground: explicit near the tower, fraying into the world's own ground beyond.
    fray = Noise(920122, 3.0)
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if d <= 11.5:
                if d <= 8.5 or fray.at(x, 0, z) > (d - 8.5) / 3.0:
                    t.set(x, GROUND, z, m['ground'](x, GROUND, z))
                for y in range(FEET, SY):
                    t.set(x, y, z, AIR)
    # The cavern and its rock shell, the floor at layer 0 (the hall's roof lies under it).
    for x in range(SX):
        for z in range(SZ):
            for y in range(1, GROUND + 1):
                if bell(x, y, z):
                    t.set(x, y, z, AIR)
            for y in range(0, GROUND + 1):
                if t.is_air(x, y, z) and (x, y, z) in t.cells:
                    continue
                near = any(bell(x + dx, yy, z + dz) for dx in (-2, -1, 0, 1, 2) for dz in (-2, -1, 0, 1, 2)
                           for yy in (y, y + 1) if abs(dx) + abs(dz) <= 3)
                if near or (y == 0 and dist(x, z) < 11.0):
                    t.set(x, y, z, m['rock'](x, y, z))
    for x in range(SX):
        for z in range(SZ):
            if dist(x, z) < 10.6:
                t.set(x, 0, z, m['rubble'](x, 0, z) if hash01(920104, x, z) < 0.55 else m['rock'](x, 0, z))

    # The newel: the descent's spine, deepslate below the ground and the old tower's stone above it.
    newel_top = {}
    for x in range(C - 1, C + 2):
        for z in range(C - 1, C + 2):
            top = 19 - (1 if x == C + 1 else 0) - (2 if (x, z) == (C + 1, C + 1) else 0) - (1 if z == C - 1 else 0)
            newel_top[(x, z)] = top
            for y in range(0, top + 1):
                if y <= GROUND:
                    st = 'chiseled_deepslate' if y in (2, 6) else m['deep'](x, y, z)
                else:
                    st = 'chiseled_stone_bricks' if y in (10, 14, 18) else m['brick'](x, y, z)
                t.set(x, y, z, st)
    t.set(C, 20, C, 'cracked_stone_bricks')
    t.set(C - 1, 20, C, stairs('stone_brick_stairs', 'east'))

    # The stair: treads of deepslate around the newel; the lower turns stand on a solid base.
    tread = mixer(920105, [('deepslate_brick_stairs', 3), ('polished_deepslate_stairs', 2),
                           ('cobbled_deepslate_stairs', 1)], 2.0)
    landing = mixer(920106, [('polished_deepslate', 3), ('deepslate_bricks', 2), ('cracked_deepslate_bricks', 1)], 2.0)
    path_cells = {}
    for cells, feet, facing in PATH:
        for (x, z) in cells:
            path_cells[(x, z)] = feet
            y = feet - 1
            if facing:
                t.set(x, y, z, stairs(tread(x, y, z), facing))
            else:
                t.set(x, y, z, landing(x, y, z))
            for yy in range(feet, feet + 3):
                if t.inside(x, yy, z) and not (abs(x - C) <= 1 and abs(z - C) <= 1):
                    t.set(x, yy, z, AIR)
            if y <= 3:
                for yy in range(0, y):
                    t.set(x, yy, z, m['deep'](x, yy, z))
            elif max(abs(x - C), abs(z - C)) == 3:
                # a soffit under the outer edge of the floating turns
                t.set(x, y - 1, z, stairs('deepslate_brick_stairs', facing or 'north', half='top')
                      if facing else slab('deepslate_brick_slab', 'top'))
    # The well at the newel's foot: the hall's spiral opens under it.
    for (x, z) in WELL:
        t.set(x, 0, z, AIR)
        t.set(x, 1, z, AIR)
        t.set(x, 2, z, AIR)

    # The bridge from the tower door to the newel, the floor that did not fall.
    for x in range(C - 1, C + 2):
        for z in range(7, 11):
            t.set(x, GROUND, z, landing(x, GROUND, z) if z > 8 else m['brick'](x, GROUND, z))
            t.set(x, GROUND - 1, z, slab('stone_brick_slab', 'top') if x != C else 'stone_bricks')
    for z in (9, 10):
        t.set(C - 1, FEET, z, wall('stone_brick_wall') if z == 9 else AIR)
    t.set(C + 1, FEET, 9, wall('stone_brick_wall'))
    t.set(C + 1, FEET + 1, 9, AIR)

    # The tower: a ring wall, tall to the north-west, gone to the south-east where it fell.
    nw = -3 * math.pi / 4
    hnoise = Noise(920107, 2.0)
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if not 5.3 < d <= 6.7:
                continue
            a = angle(x, z)
            c = max(0.0, math.cos(a - nw))
            h = int(round(1 + 12 * c ** 1.6 + (hnoise.at(x, 0, z) - 0.5) * 3))
            if c <= 0 and hash01(920108, x, z) < 0.45:
                h = 0
            for y in range(FEET, FEET + h):
                t.set(x, y, z, m['brick'](x, y, z))
            t.set(x, GROUND, z, m['brick'](x, GROUND, z))
            for y in range(GROUND - 2, GROUND):
                if not t.is_air(x, y, z) or (x, y, z) not in t.cells:
                    t.set(x, y, z, m['brick'](x, y, z))
            if h >= 6:
                top = FEET + h - 1
                t.set(x, top, z, 'stone_bricks' if hash01(920109, x, z) < 0.5 else 'mossy_stone_bricks')
    # The door on the north, 3 wide and 4 high, its arch in stairs.
    for x in range(C - 1, C + 2):
        for y in range(FEET, FEET + 4):
            for z in (7, 8):
                t.set(x, y, z, AIR)
    for z in (7, 8):
        t.set(C - 1, FEET + 3, z, stairs('stone_brick_stairs', 'east', half='top'))
        t.set(C + 1, FEET + 3, z, stairs('stone_brick_stairs', 'west', half='top'))
        if not t.is_air(C, FEET + 4, z):
            t.set(C, FEET + 4, z, 'chiseled_stone_bricks')
    # Arrow slits in the tall wall.
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if 5.3 < d <= 6.7 and (x + z) % 4 == 0 and math.cos(angle(x, z) - nw) > 0.55:
                for y in (FEET + 4, FEET + 5):
                    if not t.is_air(x, y, z) and (x, y, z) in t.cells:
                        inner = dist(x, z) < 6.0
                        t.set(x, y, z, AIR if inner or hash01(5, x, y, z) < 0.6 else 'iron_bars')
    # Stubs of the tower's own stair, still climbing the newel above the ground to floors that are gone.
    for i, (x, z, f) in enumerate(((16, 13, 'south'), (16, 14, 'south'), (16, 15, 'south'), (15, 16, 'west'),
                                   (14, 16, 'west'))):
        y = FEET + 1 + i
        t.set(x, y, z, stairs('stone_brick_stairs', f))
        t.set(x, y - 1, z, stairs('stone_brick_stairs', B.opposite(f), half='top') if i else AIR)

    # The crack: open from the hall's ceiling to the sky, through the split north-west wall.
    for (x, z) in CRACK:
        for y in range(0, SY):
            if y <= GROUND or (y >= FEET and t.inside(x, y, z) and dist(x, z) > 5.3 and dist(x, z) <= 6.9):
                t.set(x, y, z, AIR)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, z + dz)
            if q in CRACK:
                continue
            for y in range(0, GROUND + 1):
                p = (q[0], y, q[1])
                if t.inside(*p) and p not in t.cells:
                    t.set(*p, m['rock'](*p))
    # The ground beside the crack has slumped into it: a lip of gravel and a fallen block across one end.
    for (x, z) in CRACK[3:]:
        for dx in (-1, 1):
            p = (x + dx, GROUND, z)
            if t.inside(*p) and (x + dx, z) not in CRACK and hash01(920110, *p) < 0.5:
                t.set(*p, 'gravel')
    t.set(6, FEET, 4, 'mossy_cobblestone')
    t.set(6, FEET, 5, stairs('cobblestone_stairs', 'east'))

    # The fallen top of the tower: drums of wall lying to the south-east, and blocks in the cavern.
    fall = (math.cos(math.pi / 4), math.sin(math.pi / 4))
    for k, r in enumerate((8.5, 11.0, 13.4)):
        cx, cz = C + fall[0] * r, C + fall[1] * r
        for x in range(int(cx) - 2, int(cx) + 3):
            for z in range(int(cz) - 2, int(cz) + 3):
                if not t.inside(x, FEET, z):
                    continue
                dd = math.hypot(x - cx, z - cz)
                if dd <= 2.1 and hash01(920111 + k, x, z) < 0.85:
                    t.set(x, GROUND, z, m['rubble'](x, GROUND, z))
                    hgt = 2 if dd < 1.2 else 1
                    for y in range(FEET, FEET + hgt):
                        if dd > 1.0 or hgt == 1:
                            t.set(x, y, z, m['brick'](x, y, z))
                        else:
                            t.set(x, y, z, AIR if hash01(7, x, y, z) < 0.5 else 'cobblestone')
                elif dd <= 2.9 and hash01(920114 + k, x, z) < 0.35:
                    t.set(x, FEET, z, slab('stone_brick_slab') if hash01(3, x, z) < 0.5 else
                          stairs('stone_brick_stairs', ('north', 'east', 'south', 'west')[int(hash01(4, x, z) * 4) % 4]))
    for (x, z, h) in ((19, 20, 3), (20, 18, 2), (17, 21, 2), (21, 16, 1)):
        for y in range(1, 1 + h):
            t.set(x, y, z, m['brick'](x, y, z))
        t.set(x, 1 + h, z, slab('stone_brick_slab') if hash01(8, x, z) < 0.5 else 'mossy_cobblestone')

    # The crystals the Collector left: amethyst in veins along the walls, the floor's growths, and the
    # sockets where the rest were cut out (scarred cobbled deepslate).
    vein = Noise(920115, 3.0)
    floor_cells = []
    for (p, s) in list(t.cells.items()):
        x, y, z = p
        if B.name_of(s) in ('minecraft:air', 'minecraft:cave_air') or y > 6 or dist(x, z) > 11.5:
            continue
        if not any(is_air_set(t, (x + dx, y + dy, z + dz)) and bell(x + dx, y + dy, z + dz)
                   for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0))):
            continue
        if abs(x - C) <= 1 and abs(z - C) <= 1:
            continue
        if (x, z) in path_cells or (x, z) in WELL:
            continue
        v = vein.at(x, y, z)
        if v > 0.8 and hash01(920121, *p) < 0.5:
            t.set(*p, 'pearlescent_froglight')                 # the vein's glowing heart
        elif v > 0.72:
            t.set(*p, 'amethyst_block' if hash01(920116, *p) < 0.75 else 'budding_amethyst')
        elif v > 0.64:
            t.set(*p, 'calcite')
        elif v < 0.2 and hash01(920117, *p) < 0.5:
            t.set(*p, 'cobbled_deepslate')
    for (p, s) in list(t.cells.items()):
        x, y, z = p
        n = B.name_of(s)
        if n not in ('minecraft:amethyst_block', 'minecraft:budding_amethyst', 'minecraft:calcite'):
            continue
        for d, dx, dy, dz in (('up', 0, 1, 0), ('down', 0, -1, 0), ('east', 1, 0, 0), ('west', -1, 0, 0),
                              ('south', 0, 0, 1), ('north', 0, 0, -1)):
            q = (x + dx, y + dy, z + dz)
            if not t.free(*q) or not bell(*q) or (q[0], q[2]) in path_cells:
                continue
            h = hash01(920118, *q)
            if n != 'minecraft:calcite' and h < 0.45:
                t.set(*q, cluster('amethyst_cluster' if h < 0.25 else 'large_amethyst_bud', d))
    # Broken crystal on the floor, roots hanging from the bell's roof, cobwebs in its upper hollows.
    for x in range(SX):
        for z in range(SZ):
            if not t.free(x, 1, z) or not K.solid(t, (x, 0, z)) or (x, z) in path_cells:
                continue
            h = hash01(920119, x, z)
            if h < 0.05:
                t.set(x, 1, z, cluster('small_amethyst_bud' if h < 0.03 else 'medium_amethyst_bud', 'up'))
            elif h < 0.09:
                t.set(x, 1, z, 'oldrepublic:debris')
    for x in range(SX):
        for z in range(SZ):
            for y in range(GROUND - 1, 1, -1):
                p = (x, y, z)
                if t.free(*p) and K.solid(t, (x, y + 1, z)) and B.name_of(t.cells[(x, y + 1, z)]) in (
                        'minecraft:dirt', 'minecraft:stone', 'minecraft:andesite', 'minecraft:gravel',
                        'minecraft:rooted_dirt', 'minecraft:coarse_dirt') and hash01(920120, *p) < 0.3:
                    t.set(*p, 'hanging_roots')
                    break

    # The Collector's winch at the rim: a timber frame over the opening, its chain down to a cradle.
    for (x, z) in ((18, 9), (19, 10)):
        for y in range(FEET, FEET + 4):
            t.set(x, y, z, S('dark_oak_log', axis='y'))
    t.set(18, FEET + 4, 9, S('dark_oak_log', axis='x'))
    t.set(19, FEET + 4, 10, S('dark_oak_log', axis='z'))
    for (x, z) in ((18, 10), (17, 10), (17, 11)):
        t.set(x, FEET + 4, z, S('dark_oak_log', axis='z' if x == 18 else 'x'))
    for y in range(2, FEET + 4):
        if t.free(17, y, 11) or t.is_air(17, y, 11):
            t.set(17, y, 11, chain('y'))
    t.set(17, 1, 11, 'barrel')
    t.set(18, FEET, 10, 'oldrepublic:cargo_crate')

    # The Collector's lamps: soul lanterns on chains from the bell's roof over the turns of the stair.
    hung = 0
    for (x, z) in ((8, 14), (9, 18), (13, 21), (8, 10), (19, 17)):
        top = max((y for y in range(1, GROUND + 1) if t.free(x, y, z)), default=None)
        if top is not None and K.solid(t, (x, top + 1, z)) and top >= 6:
            K.hang(t, x, top, z, top - 5, lantern(soul=True, hanging=True))
            hung += 1
    if hung < 3:
        raise SystemExit('%s: only %d lamps hung' % (tid, hung))
    # The watch bell lies where it fell, in the rubble at the cavern's east side, and the tower's bones
    # with it: a skull under a fallen block.
    spot = min((p for p in ((x, 1, z) for x in range(17, 23) for z in range(10, 17))
                if t.free(*p) and K.solid(t, (p[0], 0, p[2])) and t.free(p[0], 2, p[2])),
               key=lambda p: (abs(p[0] - 20) + abs(p[2] - 13), p))
    t.set(*spot, S('bell', attachment='floor', facing='west', powered=False))
    for (x, z) in ((18, 19), (18, 20)):
        if t.free(x, 1, z):
            t.set(x, 1, z, skull(6) if z == 19 else 'bone_block')
    # Where the tower's upper floor rested: a corbel course on the inside of the tall wall.
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if not 5.3 < d <= 6.7:
                continue
            y = FEET + 6
            if K.solid(t, (x, y, z)) and K.solid(t, (x, y + 1, z)):
                dx, dz = C - x, C - z
                inward = ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north')
                q = (x + (1 if inward == 'east' else -1 if inward == 'west' else 0), y,
                     z + (1 if inward == 'south' else -1 if inward == 'north' else 0))
                if is_air_set(t, q) and dist(*q[0::2]) < d and (x + z) % 2 == 0:
                    t.set(*q, stairs('stone_brick_stairs', B.opposite(inward), half='top'))

    # Light: the cavern glows from below; the stair and the well are lit for the walk down.
    t.anchor('SHAFT', *SHAFT)
    mod.spot(t, 'DESCENT_TOP', C, FEET, 8)
    fill_light(t, (0, 1, 0, SX - 1, GROUND, SZ - 1), level=8, strength=11)
    opens = set()
    check_reach(t, [(C, FEET, 4)], [(C, 1, 16), (C, FEET, 8)], opens, tid)
    for p in [(15, 0, 16), (15, 1, 16), (16, 0, 16)]:
        if not is_air_set(t, p):
            raise SystemExit('%s: the well cell %r is not open' % (tid, p))
    write_meta(tid, buried=BURIED, max_slope=4, clearance=4, support_depth=8, ramp_rings=0, clear_above=6,
               terrain='any')
    return save(t, tid, 'reliquary_surface', views=('se', 'nw'), plans=[1, GROUND, FEET])


# ---------------------------------------------------------------- the hall (kept)

HALL_CRACK = [(x - HALL_OFFSET[0], z - HALL_OFFSET[2]) for (x, z) in CRACK]


def hall_style(seed):
    return K.Style(seed,
                   wall=[('deepslate_bricks', 5), ('cracked_deepslate_bricks', 2), ('deepslate_tiles', 2),
                         ('cracked_deepslate_tiles', 1)],
                   floor=[('polished_tuff', 4), ('tuff_bricks', 3), ('polished_deepslate', 1), ('tuff', 1)],
                   ceiling=[('deepslate_tiles', 4), ('deepslate_bricks', 2), ('cobbled_deepslate', 1)],
                   base=[('polished_deepslate', 3), ('deepslate_tiles', 1)],
                   cornice='polished_deepslate', pilaster=S('polished_basalt', axis='y'), rib='polished_deepslate',
                   replace=['deepslate', 'deepslate_bricks', 'cracked_deepslate_bricks', 'deepslate_tiles', 'tuff',
                            'cobbled_deepslate', 'polished_deepslate', 'polished_tuff', 'tuff_bricks'],
                   pilaster_every=4, rib_every=4, rib_axis='x')


def build_hall():
    tid = 'boss/reliquary_hall'
    t = load(tid, seed=920201)
    t.freeze(mechanism(tid))
    t.freeze(list(t.nbt))
    for cells in t.anchors.values():
        for c in cells:
            p = tuple(c)
            t.freeze([p])
            if is_air_set(t, p):
                t.freeze([(p[0], p[1] + 1, p[2])])
    # the spiral shaft's blocks, as it is walked (its air may take light)
    t.freeze([(x, y, z) for x in range(21, 28) for y in range(7, 18) for z in range(22, 29)
              if (x, y, z) in t.cells and not is_air_set(t, (x, y, z))])
    t.freeze_box(12, 7, 20, 18, 12, 20)      # the threshold wall and the door seal's frame
    # the hoard vault, its seal and the dais front, the dais top with the lectern: every block stays
    t.freeze([(x, y, z) for x in range(10, 21) for y in range(1, 8) for z in range(0, 6)
              if (x, y, z) in t.cells and not is_air_set(t, (x, y, z))])
    opens = {p for p, s in t.cells.items() if B.name_of(s) == 'oldrepublic:reliquary_seal'}
    start = (22, 8, 24)
    reach0, keys = protect_routes(t, [start], opens, targets=[(15, 8, 25), (15, 8, 19), (15, 2, 10), (15, 6, 4)])
    region = (0, 0, 0, t.sx - 1, t.sy - 1, t.sz - 1)

    K.dress(t, hall_style(920202), region)
    # Light from below: crying obsidian weeps in the base course, crystals grow from the broken columns.
    K.base_lights(t, (1, 1, 1, 29, 12, 29), 5, 'crying_obsidian',
                  ['polished_deepslate', 'deepslate_tiles'], seed=920203, chance=0.85)
    for name in ('COLUMN_1', 'COLUMN_2', 'COLUMN_3', 'COLUMN_4', 'COLUMN_5', 'COLUMN_6'):
        (cx, cy, cz), = t.anchors[name]
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                p = (cx + dx, cy, cz + dz)
                if t.free(*p) and K.solid(t, (p[0], p[1] - 1, p[2])) and hash01(920204, *p) < 0.35:
                    t.set(*p, cluster('amethyst_cluster' if hash01(920205, *p) < 0.5 else 'large_amethyst_bud'))

    # The crack: the ceiling opens over the arena and the balcony; iron bars hold the roof's last course.
    for (x, z) in HALL_CRACK:
        if not t.inside(x, 15, z) or not is_air_set(t, (x, 15, z)):
            continue
        t.set(x, 16, z, AIR)
        t.set(x, 17, z, bars())
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, 16, z + dz)
            if (x + dx, z + dz) not in HALL_CRACK and is_air_set(t, (q[0], 15, q[2])) and hash01(920206, *q) < 0.45:
                t.set(*q, AIR)
        if t.free(x, 15, z + 1) and hash01(920207, x, z) < 0.5:
            t.set(x, 15, z + 1, 'hanging_roots')
        # what fell through it lies below
        ys = [y for y in range(1, 15) if is_air_set(t, (x, y, z)) and K.solid(t, (x, y - 1, z))]
        for y in ys[:1]:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    q = (x + dx, y, z + dz)
                    if t.free(*q) and K.solid(t, (q[0], y - 1, q[2])) and hash01(920208, *q) < 0.55:
                        t.set(*q, 'oldrepublic:debris' if hash01(920209, *q) < 0.7 else 'gravel')

    K.cobwebs(t, region, chance=0.25, seed=920210)
    fill_light(t, (1, 1, 1, 29, 16, 29), level=8, strength=11)
    t.anchor('SHAFT', *HALL_SHAFT)
    check_reach(t, [start], list(keys), opens, tid)
    write_meta(tid, buried=0, max_slope=4, clearance=2, support_depth=0, ramp_rings=0, clear_above=0)
    return save(t, tid, 'reliquary_hall', cut=[5, 10, 13], plans=[2, 8])


def main(only=None):
    builds = {'surface': build_surface, 'hall': build_hall}
    return [f() for k, f in builds.items() if not only or k in only]


if __name__ == '__main__':
    import sys
    main(sys.argv[1:])
