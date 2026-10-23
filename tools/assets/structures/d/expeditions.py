#!/usr/bin/env python3
"""The crystal expeditions (docs/MILESTONE_6.md 5.8): two surface pieces built new and two deep pieces kept at
their size and reworked in place.

  expedition/singing_hollow_surface  27 x 22 x 27, buried 3 (Selvaren). A mossy tor split by a cleft. The
        hollowhorns wallow in the meadow at its foot; two Watch pillars mark the cleft, one of them fallen; a
        Watch scout's camp stands cold beside it. Crystals sing in the cleft's walls, and at its dead end a
        ladder goes down the rock to the grotto. Behind the cleft a slit runs down through the tor under a
        roof of leaves: the grotto's one shaft of daylight.
  expedition/singing_hollow_grotto   31 x 16 x 31, kept: the chimes, the counterweights and their chains, the
        seal, the relic, the ladder and the walk the playthrough takes stay exact. Re-dressed as a living
        cave: deepslate and tuff at the foot of the walls, calcite bands, aurelite veins, moss and lumen moss
        where the daylight from the slit falls, glow lichen, dripstone, and the scout's bones at the seal.
  expedition/duskglass_rift_surface  27 x 12 x 27, buried 2 (Varkhast). A fault in the red rock that gapes
        into the rift, its lips black glass; an Imperial survey's winch still stands over the gap, its cable
        cut; their camp was struck in a hurry.
  expedition/duskglass_rift_rift     23 x 28 x 31, kept (not grown): ledges, ladders, anchors and spawners
        exact; re-dressed in black glass and basalt, and at the bottom the rock bleeds red light.

Alignment (PlanetSites.sink, RuntimeSites.placeSunken): the deep piece is centred on the surface's stand
column with its top layer right under the surface's layer 0. With a 27-wide surface:
  grotto (x, z) = hollow surface (x + 2, z + 2): the grotto's ladder column (15, 15) is the surface's (13, 13)
  rift   (x, z) = rift surface (x - 2, z + 2):   the rift's top ladder (11, 11) is the surface's (13, 9)

Usage: python3 tools/assets/structures/d/expeditions.py [hollow] [grotto] [rift_surface] [rift]
"""
import math

from common import (S, AIR, B, Noise, DTemplate, load, mechanism, protect_routes, check_reach, mixer, hash01,
                    save, fill_light, stairs, slab, wall, fence, bars, chain, lantern, candle, skull, cluster,
                    is_air_set, mod, SIDES, NEIGHBOURS6, write_meta, ladder, log, campfire, supports)
import tomb_kit as K
import props as P

HOLLOW = 'expedition/singing_hollow_surface'
GROTTO = 'expedition/singing_hollow_grotto'
RIFT_SURFACE = 'expedition/duskglass_rift_surface'
RIFT = 'expedition/duskglass_rift_rift'


def leaves(name='oak_leaves'):
    return S(name, distance=1, persistent=True, waterlogged=False)


def lichen(face):
    props = {d: False for d in ('north', 'south', 'east', 'west', 'up', 'down')}
    props[face] = True
    props['waterlogged'] = False
    return S('glow_lichen', **props)


def vine(face):
    props = {d: False for d in ('north', 'south', 'east', 'west', 'up')}
    props[face] = True
    return S('vine', **props)


def drip(thickness, down=True):
    return S('pointed_dripstone', thickness=thickness, vertical_direction='down' if down else 'up', waterlogged=False)


def hang_drip(t, x, y_top, z, n):
    """A stalactite of n cells hanging from the solid cell above (x, y_top, z)."""
    kinds = {1: ['tip'], 2: ['frustum', 'tip'], 3: ['base', 'frustum', 'tip'], 4: ['base', 'middle', 'frustum', 'tip']}[n]
    for k, th in enumerate(kinds):
        t.set(x, y_top - k, z, drip(th))


# ---------------------------------------------------------------- the Singing Hollow: surface

HS, HSY = 27, 22
H_GROUND, H_FEET = 2, 3
H_LADDER = (13, 13)
H_SLIT = [(15, 9), (16, 9), (15, 10), (16, 10)]       # over the grotto's (17..18, 11..12)
H_HEART = (13, H_FEET, 4)
H_GUARD = (7, H_FEET, 5)


def tor_height(x, z, noise):
    """The tor's height above the ground at a column (0: no rock): a crag rising from the meadow to a crest
    behind the ladder, in rough terraces."""
    dx, dz = (x - 13) / 10.5, (z - 14.5) / 9.6
    r = math.hypot(dx, dz)
    h = (1.0 - r ** 1.4) * 13.0 + (noise.at(x, 0, z) - 0.5) * 4.0
    if h > 3:
        h = 3 + int((h - 3) / 2.2) * 2.2 + (noise.at(x, 7, z) - 0.5) * 1.5      # terraces
    # the cleft: open from the meadow to the ladder, its walls sheer
    if 12 <= x <= 14 and z <= 13:
        return 0
    if 11 <= x <= 15 and z <= 6:
        return 0
    return max(0, int(round(h)))


def build_hollow_surface():
    tid = HOLLOW
    t = DTemplate(HS, HSY, HS, seed=940101)
    t.name = tid
    noise = Noise(940102, 3.2)
    fray = Noise(940103, 2.8)
    rock = mixer(940104, [('stone', 5), ('andesite', 3), ('mossy_cobblestone', 2), ('tuff', 2), ('cobblestone', 1)], 2.4)
    low = mixer(940105, [('tuff', 3), ('mossy_cobblestone', 3), ('andesite', 2), ('stone', 2)], 2.0)
    top = mixer(940106, [('moss_block', 4), ('grass_block', 3), ('podzol', 1), ('mossy_cobblestone', 1)], 2.2)
    meadow = mixer(940107, [('grass_block', 8), ('podzol', 1), ('coarse_dirt', 1)], 3.0)

    heights = {}
    for x in range(HS):
        for z in range(HS):
            d = math.hypot(x - 13, z - 13)
            h = tor_height(x, z, noise)
            heights[(x, z)] = h
            inside = d <= 11.0 or (d <= 13.4 and fray.at(x, 0, z) > (d - 11.0) / 2.4)
            if not inside and h == 0:
                continue
            # ground and subsoil
            t.set(x, H_GROUND, z, meadow(x, H_GROUND, z) if h == 0 else low(x, H_GROUND, z))
            for y in range(0, H_GROUND):
                t.set(x, y, z, 'dirt' if h == 0 and y == 1 else rock(x, y, z))
            for y in range(H_FEET, HSY):
                t.set(x, y, z, AIR)
            for y in range(H_FEET, H_FEET + h):
                t.set(x, y, z, low(x, y, z) if y < H_FEET + 3 else rock(x, y, z))
            if h > 0:
                yt = H_FEET + h - 1
                # broad tops take moss and turf; faces and knobs stay rock
                if all(heights.get((x + dx, z + dz), tor_height(x + dx, z + dz, noise)) >= h
                       for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) and hash01(940118, x, z) < 0.7:
                    t.set(x, yt, z, top(x, yt, z))

    # The ladder down: a hole at the cleft's dead end, the ladder on the rock at its back.
    lx, lz = H_LADDER
    for y in range(0, H_FEET + 1):
        t.set(lx, y, lz, S('ladder', facing='north', waterlogged=False))
        t.set(lx, y, lz + 1, rock(lx, y, lz + 1) if y >= H_FEET else 'stone')
        if y < H_FEET:
            for (x, z) in ((lx - 1, lz), (lx + 1, lz), (lx, lz - 1)):
                t.set(x, y, z, 'stone' if y < H_GROUND else low(x, y, z))
    for y in range(H_FEET + 1, H_FEET + 4):
        if t.is_air(lx, y, lz):
            t.set(lx, y, lz, AIR)
    # The cleft's floor: worn stone and gravel, a step down toward the ladder.
    for z in range(4, 13):
        for x in range(11, 16):
            if heights[(x, z)] == 0 and (x, H_GROUND, z) in t.cells:
                t.set(x, H_GROUND, z, 'gravel' if hash01(940108, x, z) < 0.3 else
                      'coarse_dirt' if z < 8 else 'mossy_cobblestone' if hash01(940109, x, z) < 0.5 else 'stone')

    # The slit: straight down through the tor to the grotto's roof, under a roof of leaves; vines down its walls.
    for (x, z) in H_SLIT:
        hgt = heights[(x, z)]
        for y in range(0, H_FEET + hgt):
            t.set(x, y, z, AIR)
        t.set(x, H_FEET + hgt - 1 if hgt > 0 else H_GROUND, z, leaves())
    for (x, z) in H_SLIT:
        for d, dx, dz in SIDES:
            q = (x + dx, z + dz)
            if q in H_SLIT:
                continue
            for y in range(1, H_FEET + heights[(x, z)] - 1):
                if K.solid(t, (q[0], y, q[1])) and hash01(940110, x, y, z) < 0.45 and t.is_air(x, y, z):
                    t.set(x, y, z, vine(d))
                    break

    # Singing stone in the cleft's walls: calcite and amethyst, crystals growing into the cleft above head
    # height, glow lichen where water runs.
    for z in range(5, 14):
        for x in (11, 15, 10, 16):
            for y in range(H_FEET, H_FEET + 8):
                p = (x, y, z)
                if not K.solid(t, p) or B.name_of(t.cells[p]) in ('minecraft:ladder',):
                    continue
                into = (x + (1 if x < 13 else -1), y, z)
                if not t.is_air(*into) or into[0] < 11 or into[0] > 15:
                    continue
                h = hash01(940111, *p)
                if h < 0.16:
                    t.set(*p, 'amethyst_block' if h < 0.09 else 'calcite')
                    if h < 0.09 and y >= H_FEET + 2 and t.is_air(*into):
                        t.set(*into, cluster('amethyst_cluster' if h < 0.05 else 'large_amethyst_bud',
                                             'east' if x < 13 else 'west'))
                elif h > 0.9 and y >= H_FEET + 1:
                    t.set(*into, lichen('west' if x < 13 else 'east'))
    # The back of the cleft over the ladder: a vein of the same stone.
    for y in range(H_FEET + 1, H_FEET + 6):
        p = (lx, y, lz + 1)
        if K.solid(t, p) and y % 2 == 0:
            t.set(*p, 'calcite' if y % 4 else 'amethyst_block')

    # Silver trees on the tor (birch trunks, oak crowns), clinging where the top is broad enough.
    for (x, z, hgt) in ((7, 15, 5), (19, 18, 5), (11, 22, 4), (20, 12, 4)):
        base = H_FEET + heights[(x, z)]
        if heights[(x, z)] == 0 or base + hgt + 2 >= HSY:
            continue
        t.set(x, base - 1, z, 'dirt')
        for y in range(base, base + hgt):
            t.set(x, y, z, log('birch_log'))
        cy = base + hgt - 1
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for dy in range(-1, 3):
                    q = (x + dx, cy + dy, z + dz)
                    r = abs(dx) + abs(dz) + max(0, dy)
                    if t.inside(*q) and t.is_air(*q) and (q in t.cells) and r <= 3 and hash01(940112, *q) < 0.85 - 0.15 * r \
                            and (q[0], q[2]) not in H_SLIT:
                        t.set(*q, leaves())
                    elif t.inside(*q) and q not in t.cells and r <= 2:
                        t.set(*q, leaves())

    # The meadow: the hollowhorns' wallow and their trodden path, grass and silverleaf, a hollowhorn's bones.
    gx, gy, gz = H_GUARD
    for x in range(gx - 3, gx + 4):
        for z in range(gz - 3, gz + 3):
            if math.hypot(x - gx, (z - gz) * 1.3) <= 3.2 and t.inside(x, H_GROUND, z) and (x, H_GROUND, z) in t.cells:
                h = hash01(940113, x, z)
                t.set(x, H_GROUND, z, 'mud' if h < 0.45 else 'coarse_dirt' if h < 0.75 else 'rooted_dirt')
    for z in range(0, 5):
        for x in (12, 13, 14):
            if (x, H_GROUND, z) not in t.cells:
                t.set(x, H_GROUND - 1, z, 'dirt')
                for y in range(H_FEET, H_FEET + 4):
                    t.set(x, y, z, AIR)
            t.set(x, H_GROUND, z, 'dirt_path' if x == 13 or hash01(940115, x, z) < 0.5 else 'coarse_dirt')
    for (x, z, st) in ((4, 4, S('bone_block', axis='x')), (5, 3, skull(5)), (10, 2, S('bone_block', axis='z'))):
        t.set(x, H_FEET, z, st)
    # The Watch's pillars at the cleft's mouth: one stands, its fellow fell across the meadow.
    for y in range(H_FEET, H_FEET + 5):
        t.set(10, y, 5, S('oldrepublic:ruin_pillar', axis='y'))
    t.set(10, H_FEET + 5, 5, 'oldrepublic:ruin_stone_mossy')
    t.set(16, H_FEET, 5, S('oldrepublic:ruin_pillar', axis='y'))
    t.set(16, H_FEET + 1, 5, 'oldrepublic:ruin_stone_mossy')
    for x in range(17, 21):
        t.set(x, H_FEET, 4, S('oldrepublic:ruin_pillar', axis='x'))
    t.set(21, H_FEET, 4, 'oldrepublic:ruin_stone_mossy')
    # The scout's camp, cold: a dead fire, a log to sit on, a bedroll, a pack and the lantern he left lit.
    t.set(4, H_FEET, 10, campfire(lit=False))
    t.set(5, H_FEET, 9, log('stripped_birch_log', 'z'))
    t.set(5, H_FEET, 10, log('stripped_birch_log', 'z'))
    t.set(2, H_FEET, 10, 'white_carpet')
    t.set(2, H_FEET, 11, 'white_carpet')
    t.set(2, H_FEET, 12, 'brown_carpet')
    t.set(3, H_FEET, 12, S('barrel', facing='up', open=False))
    t.set(6, H_FEET, 12, fence('birch_fence'))
    t.set(6, H_FEET + 1, 12, lantern())
    # Cairns of the Watch along the way in, one fallen.
    for (x, z, hgt) in ((13, 1, 2), (8, 7, 1)):
        for y in range(H_FEET, H_FEET + hgt):
            t.set(x - 1 if x == 13 else x, y, z, 'mossy_cobblestone' if y == H_FEET else 'cobblestone_wall')

    # Grass, ferns and silverleaf on the open ground; moss carpet under the tor.
    for x in range(HS):
        for z in range(HS):
            p = (x, H_FEET, z)
            if not (t.is_air(*p) and p in t.cells) or not K.solid(t, (x, H_GROUND, z)):
                continue
            if B.name_of(t.cells[(x, H_GROUND, z)]) not in ('minecraft:grass_block', 'minecraft:podzol'):
                continue
            if 12 <= x <= 14 and z <= 5:
                continue
            h = hash01(940116, x, z)
            if h < 0.12:
                t.set(*p, 'short_grass')
            elif h < 0.16:
                t.set(*p, 'fern')
            elif h < 0.20:
                t.set(*p, 'oldrepublic:silverleaf')
            elif h < 0.21:
                t.set(*p, 'lily_of_the_valley')
    for (x, z), h in heights.items():
        if h <= 0:
            continue
        top_y = H_FEET + h
        if t.inside(x, top_y, z) and t.is_air(x, top_y, z) and (x, top_y, z) in t.cells \
                and B.name_of(t.cells.get((x, top_y - 1, z), AIR)) in ('minecraft:moss_block', 'minecraft:grass_block'):
            r = hash01(940117, x, z)
            if r < 0.25:
                t.set(x, top_y, z, 'moss_carpet')
            elif r < 0.35:
                t.set(x, top_y, z, 'fern')

    mod.spot(t, 'HEART', *H_HEART)
    mod.spawner(t, *H_GUARD, 'hollowhorns', anchor='GUARD')
    check_reach(t, [(13, H_FEET, 0)], [(13, H_FEET, 12), (13, 1, 13)], None, tid)
    write_meta(tid, buried=3, max_slope=4, clearance=4, support_depth=6, ramp_rings=2, clear_above=8, terrain='any')
    return save(t, tid, 'hollow_surface', views=('se', 'nw'), plans=[H_GROUND, H_FEET, 8])


# ---------------------------------------------------------------- the Singing Hollow: grotto (kept)

G_FRONTS = [(20, 2, 13), (19, 2, 15), (11, 2, 15), (10, 2, 13), (11, 2, 11), (19, 2, 11)]   # PlaythroughTest
G_MIDDLE, G_HUB, G_SEAL = (15, 2, 13), (15, 2, 11), (15, 2, 25)
G_PATH = [(12, 2, 13), (12, 2, 19), (15, 2, 20), (15, 2, 24), (15, 2, 26)]
G_SLIT = [(x + 2, z + 2) for (x, z) in H_SLIT]            # (17..18, 11..12)


def build_grotto():
    tid = GROTTO
    t = load(tid, seed=940201)
    t.freeze(mechanism(tid))
    t.freeze(list(t.nbt))
    for cells in t.anchors.values():
        for c in cells:
            p = tuple(c)
            t.freeze([p])
            if is_air_set(t, p):
                t.freeze([(p[0], p[1] + 1, p[2])])
    # the counterweights' chains, the ladder, its rock and its foot
    t.freeze([p for p, s in t.cells.items() if B.name_of(s) in ('minecraft:iron_chain', 'minecraft:ladder')])
    t.freeze([(15, y, 16) for y in range(0, 16)] + [(15, 1, 14)])
    t.reserve([(15, y, 14) for y in range(2, 5)])
    # the playthrough's cells (their floors exact, their air kept clear of props), and the clear line from
    # each front to its hanging stone
    for p in G_FRONTS + [G_MIDDLE, G_HUB, G_SEAL] + G_PATH:
        t.freeze([(p[0], 1, p[2])])
        t.reserve([(p[0], y, p[2]) for y in range(2, 5)])
    weights = [tuple(t.anchors['WEIGHT_%d' % i][0]) for i in range(1, 7)]
    for (fx, fy, fz), (wx, wy, wz) in zip(G_FRONTS, weights):
        t.reserve([(x, y, z) for x in range(min(fx, wx), max(fx, wx) + 1) for y in range(2, 5)
                   for z in range(min(fz, wz), max(fz, wz) + 1)])
        t.freeze([(x, 1, z) for x in range(min(fx, wx), max(fx, wx) + 1) for z in range(min(fz, wz), max(fz, wz) + 1)])
    opens = {p for p, s in t.cells.items() if B.name_of(s) == 'oldrepublic:puzzle_seal'}
    start = tuple(t.anchors['HEART'][0])
    reach0, keys = protect_routes(t, [start], opens, targets=G_FRONTS + [G_MIDDLE, G_HUB, (15, 2, 24), (15, 2, 27)])

    # The rock in bands: deepslate and tuff at the foot of the walls, stone and andesite above with calcite
    # seams, dripstone in the roof; the floor moss, gravel and worn stone. Aurelite veins, the Watch's stones
    # and the lumen moss stay.
    band = Noise(940202, 3.5)
    keep = {'oldrepublic:aurelite_ore', 'oldrepublic:ruin_stone', 'oldrepublic:ruin_stone_mossy',
            'oldrepublic:ruin_stone_carved', 'oldrepublic:lumen_moss', 'minecraft:moss_block'}

    def rule(p, st, f):
        if B.name_of(st) in keep or not f or t.is_frozen(*p):
            return None
        x, y, z = p
        h = hash01(940203, *p)
        v = y + (band.at(x, 0, z) - 0.5) * 3.0
        if f == {'up'}:
            firm = K.solid(t, (x, y - 1, z))
            return 'moss_block' if h < 0.35 else ('gravel' if firm else 'stone') if h < 0.45 else \
                'mossy_cobblestone' if h < 0.65 else 'cobbled_deepslate' if h < 0.8 else 'stone'
        if 'down' in f and not (f - {'down'}):
            return 'dripstone_block' if h < 0.35 else 'stone' if h < 0.7 else 'andesite'
        if v < 4.0:
            return 'deepslate' if h < 0.45 else 'cobbled_deepslate' if h < 0.7 else 'tuff'
        if 6.2 < v < 7.4 and h < 0.7:
            return 'calcite'
        return 'stone' if h < 0.45 else 'andesite' if h < 0.7 else 'tuff' if h < 0.85 else 'mossy_cobblestone'
    from common import reskin
    reskin(t, rule)

    # The slit of daylight: the roof opens to the surface's slit; the floor under it is green.
    for (x, z) in G_SLIT:
        ys = [y for y in range(2, t.sy) if is_air_set(t, (x, y, z))]
        if not ys:
            continue
        for y in range(max(ys) + 1, t.sy):
            t.set(x, y, z, AIR)
        for d, dx, dz in SIDES:
            q = (x + dx, z + dz)
            if q in G_SLIT:
                continue
            for y in range(max(ys) - 1, t.sy):
                if K.solid(t, (q[0], y, q[1])) and hash01(940204, x, y, z) < 0.4 and t.free(x, y, z):
                    t.set(x, y, z, vine(d))
    for x in range(15, 21):
        for z in range(9, 15):
            p = (x, 2, z)
            if math.hypot(x - 17.5, z - 11.5) > 2.6 or not K.solid(t, (x, 1, z)) or t.is_frozen(x, 1, z):
                continue
            t.set(x, 1, z, 'moss_block')
            if t.free(*p):
                h = hash01(940205, x, z)
                t.set(*p, 'moss_carpet' if h < 0.5 else 'fern' if h < 0.65 else 'short_grass' if h < 0.8 else AIR)

    # Glow lichen on the walls where water runs, stalactites in the roof, a few stalagmites at the walls' feet.
    for p, st in list(t.cells.items()):
        x, y, z = p
        if not K.solid(t, p) or t.is_frozen(*p) or y < 3:
            continue
        for d, dx, dz in SIDES:
            q = (x + dx, y, z + dz)
            if t.free(*q) and hash01(940206, *q) < 0.035:
                t.set(*q, lichen({'east': 'west', 'west': 'east', 'south': 'north', 'north': 'south'}[d]))
                break
    for x in range(t.sx):
        for z in range(t.sz):
            ys = [y for y in range(3, t.sy - 1) if is_air_set(t, (x, y, z)) and K.solid(t, (x, y + 1, z))]
            if not ys or (x, z) in G_SLIT:
                continue
            y = max(ys)
            h = hash01(940207, x, z)
            if h < 0.06 and y >= 7 and all(t.free(x, y - k, z) for k in range(3)):
                hang_drip(t, x, y, z, 1 + int(h * 50) % 3)
    for (x, y, z) in sorted(reach0):
        if hash01(940208, x, z) < 0.03 and t.free(x, y, z) and t.free(x, y + 1, z) and \
                sum(1 for _, dx, dz in SIDES if K.solid(t, (x + dx, y, z + dz))) >= 2:
            t.set(x, y, z, drip('tip', down=False))

    # The Watch scout who came down alone: his bones at the seal he could not open, his lantern still lit.
    for (x, z, st) in ((13, 23, skull(9)), (13, 22, S('bone_block', axis='z')), (17, 23, lantern()),
                       (12, 22, 'oldrepublic:debris')):
        if t.free(x, 2, z) and K.solid(t, (x, 1, z)):
            t.set(x, 2, z, st)

    fill_light(t, (1, 1, 1, t.sx - 2, t.sy - 2, t.sz - 2), level=8, strength=10)
    check_reach(t, [start], list(keys), opens, tid)
    write_meta(tid, buried=0, max_slope=4, clearance=2, support_depth=0, ramp_rings=0, clear_above=0)
    return save(t, tid, 'hollow_grotto', cut=[5, 9], plans=[2, 13, 15])



# ---------------------------------------------------------------- the duskglass rift: surface

RS, RSY = 27, 12
R_GROUND, R_FEET = 1, 2
R_GAP = [(x, z) for x in range(12, 15) for z in range(9, 18)]   # over the rift's neck (10..12, 11..19)
R_LADDER = (13, 9)          # over the rift's top ladder (11, 23..27, 11): facing south, its rock at z 8
R_HEART = (13, R_FEET, 4)
R_WINCH_Z = 13
# The fault runs on as a seam of black glass, north-east and south-west of the gap.
R_SEAM = [(14, 7), (14, 6), (15, 5), (15, 4), (16, 3), (16, 2), (17, 1), (17, 0),
          (13, 18), (13, 19), (12, 20), (12, 21), (11, 22), (11, 23), (10, 24), (10, 25), (9, 26)]
GLASS = [('obsidian', 6), ('tinted_glass', 3), ('blackstone', 2), ('crying_obsidian', 1)]


def build_rift_surface():
    tid = RIFT_SURFACE
    t = DTemplate(RS, RSY, RS, seed=950101)
    t.name = tid
    fray = Noise(950102, 2.6)
    ground = mixer(950103, [('red_sand', 5), ('coarse_dirt', 3), ('terracotta', 2), ('red_terracotta', 1),
                            ('gravel', 1), ('tuff', 1)], 3.0)
    firm = mixer(950104, [('terracotta', 3), ('coarse_dirt', 3), ('red_terracotta', 2), ('tuff', 1)], 2.6)
    sub = mixer(950105, [('red_sandstone', 4), ('terracotta', 3), ('orange_terracotta', 1), ('stone', 2),
                         ('tuff', 1)], 2.5)
    band = {0: 'red_terracotta', 1: 'orange_terracotta', 2: 'terracotta', 3: 'brown_terracotta'}
    glass = mixer(950106, GLASS, 1.4)
    gap = set(R_GAP)
    seam = set(R_SEAM)

    def lip_dist(x, z):
        return min(max(abs(x - gx), abs(z - gz)) for (gx, gz) in R_GAP)

    # The ground: a frayed disc of red earth, two layers deep, cleared above.
    inside = set()
    for x in range(RS):
        for z in range(RS):
            d = math.hypot(x - 13, z - 13)
            if d <= 11.0 or (d <= 13.4 and fray.at(x, 0, z) > (d - 11.0) / 2.4):
                inside.add((x, z))
                t.set(x, 0, z, sub(x, 0, z))
                t.set(x, R_GROUND, z, ground(x, R_GROUND, z))
                for y in range(R_FEET, RSY):
                    t.set(x, y, z, AIR)

    # The fault's east side stands a little higher: a ridge of banded rock shoved up along the gap.
    ridge = {}
    for (x, z) in inside:
        if (x, z) in gap:
            continue
        dl = lip_dist(x, z)
        if x > 14 and 7 <= z <= 19 and dl <= 2 + int(fray.at(x, 3, z) * 2.2):
            ridge[(x, z)] = 2 if dl == 1 else 1
        elif dl == 1:
            ridge[(x, z)] = 1
    for (x, z), h in ridge.items():
        for k in range(h):
            y = R_FEET + k
            t.set(x, y, z, band[(y + (x + z) // 3) % 4] if lip_dist(x, z) > 1 else glass(x, y, z))
        t.set(x, R_GROUND, z, firm(x, R_GROUND, z))

    # The gap: open down into the rift; its lips and walls black glass where the rock ran molten.
    for (x, z) in gap:
        for y in range(0, RSY):
            t.set(x, y, z, AIR)
    for (x, z) in inside:
        dl = lip_dist(x, z)
        if (x, z) in gap:
            continue
        if dl == 1:
            t.set(x, 0, z, glass(x, 0, z))
            t.set(x, R_GROUND, z, glass(x, R_GROUND, z))
        elif dl <= 3 and hash01(950107, x, z) < 0.55 - 0.15 * dl:
            # glass splashed over the ground near the lips
            t.set(x, R_GROUND, z, 'obsidian' if hash01(950108, x, z) < 0.4 else 'blackstone'
                  if hash01(950109, x, z) < 0.6 else 'smooth_basalt')
    for (x, z) in seam:
        if (x, z) in inside:
            t.set(x, R_GROUND, z, glass(x, R_GROUND, z))
            t.set(x, 0, z, 'obsidian')
            for dx, dz in ((1, 0), (-1, 0)):
                q = (x + dx, z + dz)
                if q in inside and q not in seam and q not in gap and hash01(950110, *q) < 0.3:
                    t.set(q[0], R_GROUND, q[1], 'blackstone' if hash01(950111, *q) < 0.5 else 'smooth_basalt')

    # The ladder down at the gap's north end, on the rock of its end wall; a gap in the lip to reach it.
    lx, lz = R_LADDER
    for y in (0, R_GROUND):
        t.set(lx, y, lz, ladder('south'))
        t.set(lx, y, lz - 1, 'blackstone')
    for (x, z) in ((lx, lz - 1), (lx, lz - 2)):
        for y in range(R_FEET, RSY):
            t.set(x, y, z, AIR)
    t.set(lx, R_GROUND, lz - 2, 'polished_blackstone')

    # The survey's track from the north, trodden into the red earth.
    for z in range(0, lz - 2):
        for x in (12, 13, 14):
            if (x, z) not in inside:
                inside.add((x, z))
                t.set(x, 0, z, sub(x, 0, z))
            if (x, z) in inside and (x, z) not in seam:
                h = hash01(950112, x, z)
                if x == 13 or h < 0.5:
                    t.set(x, R_GROUND, z, 'coarse_dirt' if h < 0.6 else 'gravel' if h < 0.8 else 'packed_mud')
                for y in range(R_FEET, R_FEET + 3):
                    t.set(x, y, z, AIR)

    # The winch: a durasteel gantry over the middle of the gap, a pulley wheel under its beam, and the
    # cable hanging two blocks below it, cut. Its drum and motor on the ridge to the east.
    wz = R_WINCH_Z
    for x, y0 in ((11, R_FEET), (15, R_FEET)):
        t.set(x, R_GROUND, wz, 'polished_blackstone')
        for y in range(y0, R_FEET + 4):
            t.set(x, y, wz, S('oldrepublic:durasteel_beam', axis='y'))
    for x in range(11, 16):
        t.set(x, R_FEET + 4, wz, S('oldrepublic:durasteel_beam', axis='x'))
    t.set(13, R_FEET + 3, wz, S('grindstone', face='ceiling', facing='east'))
    for y in (R_FEET + 1, R_FEET + 2):
        t.set(13, y, wz, chain('y'))
    ey = R_FEET + ridge.get((17, wz), 0)
    for z in (wz - 1, wz, wz + 1):
        if t.is_air(17, ey - 1, z) or (17, ey - 1, z) not in t.cells:
            t.set(17, ey - 1, z, 'polished_blackstone')
    t.set(17, ey, wz - 1, S('oldrepublic:durasteel_beam', axis='z'))
    t.set(17, ey, wz, S('oldrepublic:durasteel_beam', axis='z'))
    t.set(17, ey, wz + 1, 'oldrepublic:imperial_plating')
    t.set(18, ey, wz + 1, S('lever', face='wall', facing='east', powered=True))
    for x in range(16, 17):
        if t.is_air(x, ey, wz):
            t.set(x, ey, wz, chain('x'))
    # A red survey lamp on its mast at the ladder; its fellow at the far end lies where it fell.
    P.mast(t, 11, R_FEET, 8, 3, head='oldrepublic:light_panel_red')
    P.mast(t, 16, R_FEET, 21, 3, head='oldrepublic:light_panel_red', toppled='east')

    # The survey's camp to the west, struck in a hurry: one tent down, the other's frame bare, crates
    # left where they stood, the fire kicked out, a console still on.
    camp = [(x, z) for x in range(2, 10) for z in range(9, 20)]
    for (x, z) in camp:
        if (x, z) in inside and lip_dist(x, z) > 1:
            h = hash01(950113, x, z)
            t.set(x, R_GROUND, z, 'coarse_dirt' if h < 0.55 else 'red_sand' if h < 0.8 else 'gravel')
    # the fallen tent: crimson canvas flat on the ground over a crate, its pole across it
    for (x, z) in [(3, 10), (4, 10), (5, 10), (3, 11), (4, 11), (5, 11), (6, 11), (3, 12), (4, 12), (5, 12),
                   (6, 12), (4, 13), (5, 13)]:
        t.set(x, R_FEET, z, 'red_carpet')
    t.set(4, R_FEET, 11, 'oldrepublic:cargo_crate_imperial')
    t.set(4, R_FEET + 1, 11, 'red_carpet')
    for x in (3, 4, 5):
        t.set(x, R_FEET, 14, S('oldrepublic:durasteel_beam', axis='x'))
    # the bare frame: four posts and a ridge pole, one strip of canvas left hanging
    for (x, z) in ((3, 16), (7, 16), (3, 19), (7, 19)):
        for y in range(R_FEET, R_FEET + 2):
            t.set(x, y, z, fence('dark_oak_fence'))
    for z in range(16, 20):
        t.set(5, R_FEET + 3, z, S('oldrepublic:durasteel_beam', axis='z'))
    for x in (4, 6):
        for z in (16, 19):
            t.set(x, R_FEET + 2, z, fence('dark_oak_fence'))
    t.set(5, R_FEET + 2, 16, fence('dark_oak_fence'))
    t.set(5, R_FEET + 2, 19, fence('dark_oak_fence'))
    for z in (17, 18):
        t.set(4, R_FEET + 2, z, stairs('oldrepublic:canvas_stairs', 'east'))
    t.set(4, R_FEET + 1, 18, 'oldrepublic:canvas_crimson')
    # bedrolls under it, one kicked aside; the fire; the crates and the console
    t.set(5, R_FEET, 17, S('red_carpet'))
    t.set(5, R_FEET, 18, S('gray_carpet'))
    t.set(6, R_FEET, 18, S('gray_carpet'))
    t.set(8, R_FEET, 14, campfire(lit=False))
    t.set(9, R_FEET, 13, 'oldrepublic:scorch_mark')
    for (x, z, n) in ((8, 10, 2), (9, 10, 1), (8, 11, 1), (2, 15, 1)):
        for k in range(n):
            t.set(x, R_FEET + k, z, 'oldrepublic:cargo_crate_imperial')
    t.set(9, R_FEET + 1, 10, S('oldrepublic:console', facing='east'))
    t.set(7, R_FEET, 12, 'oldrepublic:cargo_crate_imperial')
    t.set(9, R_FEET, 16, S('barrel', facing='up', open=True))
    t.set(10, R_FEET, 17, 'oldrepublic:debris')
    t.set(6, R_FEET, 15, 'oldrepublic:debris')
    t.set(2, R_FEET, 17, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    # Blaster scorches where the hounds came up out of the gap, and what they left of one surveyor.
    def feet_y(x, z):
        if (x, z) not in inside or (x, z) in gap:
            return None
        y = R_FEET + ridge.get((x, z), 0)
        return y if t.is_air(x, y, z) and (x, y, z) in t.cells and K.solid(t, (x, y - 1, z)) else None
    P.scorch_fan(t, 9, 14, feet_y, (1, 0), 3, 0.6, seed=950114)
    P.scorch_fan(t, 16, 9, feet_y, (-1, -1), 3, 0.5, seed=950115)
    for (x, z, st) in ((10, 19, skull(6)), (9, 20, S('bone_block', axis='x')), (15, 22, 'oldrepublic:debris'),
                       (11, 18, 'oldrepublic:scorch_mark')):
        y = feet_y(x, z)
        if y is not None:
            t.set(x, y, z, st)

    # Dry red country: boulders of tuff and basalt, dead brush, dry grass, ashroot.
    rocky = mixer(950116, [('tuff', 3), ('smooth_basalt', 2), ('red_sandstone', 2), ('basalt', 1)], 1.8)
    for (cx, cz, rx, ry, rz) in ((21, 6, 1.8, 1.0, 1.5), (4, 23, 1.5, 0.9, 1.9), (22, 21, 1.3, 0.8, 1.2)):
        P.boulder(t, cx, R_FEET, cz, rx, ry, rz, lambda x, y, z: rocky(x, y, z), seed=950117)
    for (x, z) in sorted(inside):
        y = feet_y(x, z)
        if y is None or (x, z) in seam or lip_dist(x, z) <= 2 or (12 <= x <= 14 and z < 9) or (x, z) in camp:
            continue
        below = B.name_of(t.cells.get((x, y - 1, z), AIR))
        if below not in ('minecraft:red_sand', 'minecraft:coarse_dirt', 'minecraft:terracotta'):
            continue
        h = hash01(950118, x, z)
        if h < 0.05:
            t.set(x, y, z, 'dead_bush')
        elif h < 0.10:
            t.set(x, y, z, 'short_dry_grass')
        elif h < 0.12:
            t.set(x, y, z, 'tall_dry_grass')
        elif h < 0.135:
            t.set(x, y, z, 'oldrepublic:ashroot')

    mod.spot(t, 'HEART', *R_HEART)
    check_reach(t, [(13, R_FEET, 0)], [R_HEART, (13, R_FEET, 8), (13, R_GROUND, 9), (13, 0, 9)], None, tid)
    write_meta(tid, buried=2, max_slope=4, clearance=4, support_depth=4, ramp_rings=2, clear_above=6, terrain='any')
    return save(t, tid, 'rift_surface', views=('se', 'nw'), plans=[R_GROUND, R_FEET, 4])


# ---------------------------------------------------------------- the duskglass rift: the rift (kept)

RIFT_START = (11, 26, 11)           # on the top ladder, under the surface's
RIFT_CAMERA = (6, 13, 12)           # PlanetClientSteps' stand (feet), looking at the RELIC
RIFT_EYE = (10, 23, 12)             # this worker's CAMERA: over the top shelf's edge, down the shaft to the crystal
RIFT_SKY = [(x, z) for x in range(10, 13) for z in range(11, 20)]   # under the surface's gap: daylight falls here
TO_SUPPORT = {'south': (0, 0, -1), 'north': (0, 0, 1), 'east': (-1, 0, 0), 'west': (1, 0, 0),
              'up': (0, -1, 0), 'down': (0, 1, 0)}
RIFT_KEEP = ('minecraft:ladder', 'minecraft:lantern', 'oldrepublic:duskglass_cluster', 'oldrepublic:budding_duskglass',
             'oldrepublic:kyber_cluster', 'oldrepublic:site_spawner')


def line3(a, b, step=0.05):
    """The cells a straight line from point a to point b passes through (b's cell left out)."""
    n = max(1, int(math.dist(a, b) / step))
    out = []
    end = tuple(int(math.floor(v)) for v in b)
    for i in range(n + 1):
        f = i / n
        q = tuple(int(math.floor(a[k] + (b[k] - a[k]) * f)) for k in range(3))
        if q != end and q not in out:
            out.append(q)
    return out


def build_rift():
    tid = RIFT
    t = load(tid, seed=950201)
    t.freeze(mechanism(tid))
    t.freeze(list(t.nbt))
    for cells in t.anchors.values():
        for c in cells:
            p = tuple(c)
            t.freeze([p])
            if is_air_set(t, p):
                t.freeze([(p[0], p[1] + 1, p[2])])
    # the ladders and the rock they hang on, the survey's lanterns, the crystals the rift grew and what
    # they grow on
    for p, st in list(t.cells.items()):
        n, props = B.parse(st)
        if n not in RIFT_KEEP:
            continue
        t.freeze([p])
        if n == 'minecraft:lantern':
            off = (0, 1, 0) if props.get('hanging') in (True, 'true') else (0, -1, 0)
        elif 'facing' in props:
            off = TO_SUPPORT[props['facing']]
        else:
            continue
        t.freeze([(p[0] + off[0], p[1] + off[1], p[2] + off[2])])
    reach0 = check_reach(t, [RIFT_START], [], None, tid)
    walk = set(reach0) | {(x, y + 1, z) for (x, y, z) in reach0}
    # the shelves' floors stay as the rift laid them; the bottom's may change
    t.freeze([(x, y - 1, z) for (x, y, z) in reach0 if y > 2])
    sky = {(x, y, z) for (x, z) in RIFT_SKY for y in range(2, t.sy) if is_air_set(t, (x, y, z))}
    t.reserve(sky)
    relic = tuple(t.anchors['RELIC'][0])
    heart = tuple(t.anchors['HEART'][0])
    views = line3((RIFT_CAMERA[0] + 0.5, RIFT_CAMERA[1] + 1.62, RIFT_CAMERA[2] + 0.5), [v + 0.5 for v in relic]) + \
        line3([v + 0.5 for v in RIFT_EYE], [v + 0.5 for v in relic])
    t.reserve([q for q in views if is_air_set(t, q)])
    targets = [heart, RIFT_CAMERA, (10, 22, 11), (11, 2, 15)]
    _, keys = protect_routes(t, [RIFT_START], None, targets=targets)
    anchors_at = [tuple(c) for cells in t.anchors.values() for c in cells]

    def far_from_anchors(p, r=3):
        return all(abs(p[0] - a[0]) + abs(p[2] - a[2]) > r or abs(p[1] - a[1]) > 3 for a in anchors_at)

    def open_cell(p):
        """A cell rock or glass may grow into: set air, not frozen or reserved, off every walk and its headroom."""
        return (t.inside(*p) and is_air_set(t, p) and not t.is_frozen(*p) and p not in (t.reserved or ())
                and p not in walk and (p[0], p[1] - 1, p[2]) not in walk and p not in sky)

    # The walls: the box the old rift was cut as becomes rock that bulges and overhangs, in two passes.
    bulge = Noise(950202, 2.6)
    for pas, thr in ((0, 0.60), (1, 0.70)):
        grow = []
        for p in list(t.cells):
            if not open_cell(p) or p[1] < 3:
                continue
            x, y, z = p
            near = sum(1 for dx, dy, dz in NEIGHBOURS6 if K.solid(t, (x + dx, y + dy, z + dz)))
            if near and bulge.at(x, y * 0.8, z) + 0.06 * near > thr + 0.1 * pas and \
                    all(open_cell(q) or K.solid(t, q) for q in [(x + dx, y + dy, z + dz) for dx, dy, dz in NEIGHBOURS6]):
                grow.append(p)
        for p in grow:
            t.set(*p, 'blackstone')

    # Re-skin: tilted strata with veins of black glass running down through them; obsidian and crying
    # obsidian low down, magma in the cracks at the foot of the walls; basalt hanging from the roof.
    strata = Noise(950203, 4.0)
    low = mixer(950204, [('blackstone', 4), (S('basalt', axis='y'), 3), ('smooth_basalt', 2), ('tuff', 2),
                         ('obsidian', 2)], 2.2)
    high = mixer(950205, [('blackstone', 4), (S('basalt', axis='y'), 3), ('smooth_basalt', 3), ('tuff', 2)], 2.6)

    def rule(p, st, f):
        n = B.name_of(st)
        if t.is_frozen(*p) or n in RIFT_KEEP or n in ('minecraft:tinted_glass', 'minecraft:polished_blackstone') or not f:
            return None
        x, y, z = p
        h = hash01(950206, *p)
        if f == {'up'}:
            if y != 1:
                return None
            if K.solid(t, (x, y - 1, z)) and h < 0.12:
                return 'gravel'
            return 'blackstone' if h < 0.45 else 'smooth_basalt' if h < 0.7 else 'tuff' if h < 0.85 else 'obsidian'
        if f == {'down'}:
            return S('basalt', axis='y') if h < 0.5 else 'blackstone' if h < 0.8 else 'smooth_basalt'
        vein = (x * 0.3 + z * 0.25 + y * 0.85 + strata.at(x, y, z) * 3.0) % 6.5
        if vein < 0.8:
            return 'obsidian' if h < 0.55 else 'tinted_glass' if h < 0.8 else 'crying_obsidian' if y < 12 else 'obsidian'
        if y <= 3 and h < 0.16:
            return 'magma_block'
        if y <= 6 and h < 0.10:
            return 'crying_obsidian'
        return low(x, y, z) if y < 10 else high(x, y, z)
    from common import reskin
    reskin(t, rule)

    # Shards of black glass out of the walls into the void, slanting up or down; spires of it on the floor
    # at the walls' feet.
    roots = []
    for p in sorted(t.cells):
        x, y, z = p
        if not (4 <= y <= 22) or not K.solid(t, p) or t.is_frozen(*p) or hash01(950207, *p) > 0.05:
            continue
        for d, dx, dz in SIDES:
            if open_cell((x + dx, y, z + dz)) and open_cell((x + 2 * dx, y, z + 2 * dz)):
                if all(max(abs(x - r[0]), abs(y - r[1]), abs(z - r[2])) > 4 for r, _ in roots):
                    roots.append((p, (dx, dz)))
                break
    shard_cells = []
    for (x, y, z), (dx, dz) in roots:
        h = hash01(950208, x, y, z)
        dy = 1 if h < 0.45 else -1 if h < 0.8 else 0
        length = 3 + int(hash01(950209, x, y, z) * 4)
        cells = []
        for k in range(1, length + 1):
            x, z = x + dx, z + dz
            if k % 2 == 0:
                y += dy
            if not open_cell((x, y, z)):
                break
            cells.append((x, y, z))
        if len(cells) < 2:
            continue
        for i, q in enumerate(cells):
            t.set(*q, 'obsidian' if i == 0 else 'black_stained_glass' if i == len(cells) - 1 else 'tinted_glass')
            twin = (q[0], q[1] + (-1 if dy > 0 else 1), q[2])
            if i < len(cells) // 2 and open_cell(twin):
                t.set(*twin, 'obsidian' if i == 0 else 'tinted_glass')
        shard_cells += cells
    spires = []
    for (x, z) in sorted({(p[0], p[2]) for p in reach0 if p[1] == 2}):
        p = (x, 2, z)
        wall_near = any(K.solid(t, (x + dx, 2, z + dz)) for _, dx, dz in SIDES)
        if not wall_near or p in (t.reserved or ()) or not far_from_anchors(p, 4) or hash01(950210, x, z) > 0.16:
            continue
        if any(abs(x - sx) + abs(z - sz) < 4 for sx, sz, _ in spires):
            continue
        hgt = 3 + int(hash01(950211, x, z) * 5)
        spires.append((x, z, hgt))
    for (x, z, hgt) in spires:
        for (ox, oz, dh) in ((0, 0, 0), (1, 0, 2), (0, 1, 3), (-1, 0, 2), (0, -1, 3)):
            q = (x + ox, 2, z + oz)
            hh = hgt - dh - int(hash01(950212, *q) * 2)
            if hh < 1 or (ox or oz) and hash01(950213, *q) < 0.5:
                continue
            if q in (t.reserved or ()) or not is_air_set(t, q) or t.is_frozen(*q):
                continue
            for k in range(hh):
                c = (q[0], 2 + k, q[2])
                if not is_air_set(t, c) or c in (t.reserved or ()) or t.is_frozen(*c) or c in sky:
                    break
                t.set(*c, 'crying_obsidian' if k == 0 and (ox, oz) == (0, 0) else 'obsidian' if k < hh // 3 else
                      'black_stained_glass' if k == hh - 1 else 'tinted_glass')

    # Red light from below: cracks of magma run out across the floor from the spires' feet.
    cracks = set()
    for (x, z, hgt) in spires:
        cx, cz = x, z
        for k in range(5 + int(hash01(950214, x, z) * 5)):
            h = hash01(950215, cx, k, cz)
            dx, dz = ((1, 0), (-1, 0), (0, 1), (0, -1))[int(h * 4) % 4]
            cx, cz = cx + dx, cz + dz
            feet = (cx, 2, cz)
            if not t.inside(*feet) or not K.solid(t, (cx, 1, cz)) or t.is_frozen(cx, 1, cz):
                continue
            if feet in (t.reserved or ()) or not far_from_anchors(feet, 3):
                continue
            cracks.add((cx, 1, cz))
    for q in sorted(cracks):
        t.set(*q, 'magma_block')

    # The survey: their red lamps bolted into the rock at every shelf; at the bottom the cable that fell
    # when it was cut, the crate it was lowering, broken open, and the man who reached the crystal.
    shelves = {}
    for (x, y, z) in reach0:
        if y > 2:
            shelves.setdefault(y, []).append((x, y, z))
    lamps = []
    for y in sorted(shelves):
        cells = shelves[y]
        if len(cells) < 6:
            continue
        best = None
        for (x, _, z) in sorted(cells):
            for _, dx, dz in SIDES:
                w = (x + dx, y + 1, z + dz)
                if K.solid(t, w) and not t.is_frozen(*w) and w not in shard_cells and \
                        B.name_of(t.cells[w]) not in RIFT_KEEP and not is_air_set(t, (w[0] + dx, w[1], w[2] + dz)):
                    score = hash01(950216, *w)
                    if best is None or score < best[0]:
                        best = (score, w)
        if best:
            t.set(*best[1], 'oldrepublic:light_panel_red')
            lamps.append(best[1])

    def free_floor(x, z, r=0):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                q = (x + dx, 2, z + dz)
                if t.free(*q) and K.solid(t, (q[0], 1, q[2])) and t.free(q[0], 3, q[2]) and far_from_anchors(q, 1):
                    return q
        return None
    placed = []
    for (x, z, st) in ((10, 20, chain('x')), (9, 20, chain('z')), (9, 21, chain('x')), (12, 21, chain('z')),
                       (8, 19, 'oldrepublic:cargo_crate_imperial'), (7, 19, 'oldrepublic:debris'),
                       (8, 18, 'oldrepublic:debris'), (7, 20, lantern()), (13, 18, skull(4)),
                       (14, 17, S('bone_block', axis='z')), (12, 22, 'oldrepublic:debris')):
        q = free_floor(x, z)
        if q:
            t.set(*q, st)
            placed.append(q)
    sq = tuple(t.anchors['SQUAD_2'][0])
    P.scorch_fan(t, sq[0], sq[2], lambda x, z: 2 if t.free(x, 2, z) and K.solid(t, (x, 1, z)) else None,
                 (-1, -1), 3, 0.6, seed=950217, density=0.4)
    # a cache on the top shelf, left at its dead end
    for (x, z) in ((4, 9), (4, 10), (5, 9)):
        if t.free(x, 22, z) and t.free(x, 23, z) and K.solid(t, (x, 21, z)):
            t.set(x, 22, z, 'oldrepublic:cargo_crate_imperial')
            t.set(x, 23, z, lantern())
            placed.append((x, 22, z))
            break

    floors = check_reach(t, [RIFT_START], list(keys), None, tid)
    fill_light(t, level=8, strength=10, floors=[p for p in floors if supports(t.cells.get((p[0], p[1] - 1, p[2])))])
    check_reach(t, [RIFT_START], list(keys), None, tid)
    print('  rift: bulges, %d shards, %d spires, %d crack cells, %d lamps, %d props' %
          (len(roots), len(spires), len(cracks), len(lamps), len(placed)))
    write_meta(tid, buried=0, max_slope=4, clearance=2, support_depth=0, ramp_rings=0, clear_above=0)
    return save(t, tid, 'rift', cut=[12, 20], plans=[2, 13, 22])

def main(only=None):
    builds = {'hollow': build_hollow_surface, 'grotto': build_grotto, 'rift_surface': build_rift_surface, 'rift': build_rift}
    return [f() for k, f in builds.items() if not only or k in only]


if __name__ == '__main__':
    import sys
    main(sys.argv[1:])
