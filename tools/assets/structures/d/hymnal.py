#!/usr/bin/env python3
"""The Hymnal on Tyrvann (docs/MILESTONE_6.md 5.8): the arch over the cleft and the halls under it, both kept
at their size and reworked in place.

  site/hymnal_arch  15 x 31 x 15, buried 17. Above the ground a new pointed arch of Sith masonry rises out of
                    a broken rank of basalt organ pipes (the song-engine below, shown at the surface). The
                    storm strikes its keystone every half minute: the crown is fused black and still glows,
                    stormglass crusts every ledge the bolts run down, and the ground at its feet is scorched.
                    Below the ground the stair shaft is kept cell for cell (every air, stair and tread cell, and
                    every cell Hymnal.shaft() writes); only its lining is re-faced and given candle niches, so
                    the way down glows red from below.
  site/hymnal       37 x 28 x 79, buried 0: the processional, the Heart and the undercroft, re-dressed around
                    the sealed plating doors, every anchor, the door sides and the round Heart (radius 15.5).

The halls' origin stays (-11, -28, 9) in the arch's frame (Hymnal.HALLS_OFFSET), written as the arch's link.

Usage: python3 tools/assets/structures/d/hymnal.py [arch] [halls]   (D_PREVIEW=<folder> renders previews)
"""
import math

from common import (S, AIR, B, Noise, DTemplate, load, mechanism, protect_routes, check_reach, mixer, hash01,
                    save, fill_light, stairs, slab, wall, bars, chain, lantern, candle, skull, cluster, is_air_set,
                    mod, SIDES, write_meta, dark_floors)
import tomb_kit as K

ARCH = 'site/hymnal_arch'
HALLS = 'site/hymnal'
HALLS_LINK = (-11, -28, 9)

GROUND = 16                      # the arch's ground layer (buried 17)
FEET = 17
ARCH_TOP = (7, 30, 5)            # the keystone, where the cosmetic bolts strike
STAIR_MOUTH = (7, 17, 2)
MOUTH = [(x, z) for x in range(6, 9) for z in range(4, 10)]
# The cells Hymnal.shaft() writes inside the arch when the halls sink (the bottom landing's ends).
JAVA_SHAFT = [(x, y, z) for x in range(4, 11) for y in range(0, 4) for z in range(9, 15)]

STORMGLASS = 'oldrepublic:stormglass_cluster'


def storm(facing='up'):
    return S(STORMGLASS, facing=facing)


def pillar(name='polished_basalt', axis='y'):
    return S(name, axis=axis)


def masonry(seed):
    """The Hymnal's walls: Sith tomb bricks, Tyrvann's own stormstone, black brick, in patches."""
    return mixer(seed, [('oldrepublic:tomb_bricks', 5), ('oldrepublic:stormstone_bricks', 3),
                        ('polished_blackstone_bricks', 2), ('cracked_polished_blackstone_bricks', 2)], 3.0)


def halls_style(seed):
    return K.Style(seed,
                   wall=[('oldrepublic:tomb_bricks', 5), ('oldrepublic:stormstone_bricks', 3),
                         ('polished_blackstone_bricks', 2), ('cracked_polished_blackstone_bricks', 2)],
                   floor=[('polished_blackstone', 5), ('oldrepublic:tomb_stone', 3), ('polished_deepslate', 2),
                          ('oldrepublic:stormstone_bricks', 1), ('oldrepublic:tomb_stone_cracked', 1)],
                   ceiling=[('oldrepublic:stormstone', 3), ('oldrepublic:tomb_bricks', 3), ('blackstone', 2),
                            ('basalt', 1)],
                   base=[('polished_blackstone', 3), ('oldrepublic:tomb_stone', 2)],
                   cornice='polished_blackstone',
                   pilaster=S('oldrepublic:tomb_pillar', axis='y'),
                   rib='oldrepublic:tomb_bricks',
                   replace=['polished_blackstone_bricks', 'cracked_polished_blackstone_bricks', 'polished_blackstone',
                            'blackstone', 'basalt', 'smooth_basalt', 'polished_deepslate', 'deepslate_tiles'],
                   pilaster_every=4, rib_every=3, rib_axis='z')


# ---------------------------------------------------------------- the arch

def arch_profile(x):
    """(the highest air cell of the opening under the arch, the arch's top) at column x (None: no arch)."""
    d = abs(x - 7)
    top = {0: 30, 1: 30, 2: 30, 3: 29, 4: 28, 5: 27, 6: 25}.get(d)
    if top is None:
        return None
    if d >= 4:
        return (None, top)                       # a leg: solid from the ground up
    return ({0: 28, 1: 27, 2: 26, 3: 24}[d], top)


def build_arch():
    tid = ARCH
    t = load(tid, seed=930101)
    # The shaft below the ground: every cell the player walks, every tread and floor, and the bottom landing
    # Hymnal.shaft() rewrites stay exact. Only the lining walls may be re-faced and opened into niches.
    walk = set()
    for p, s in t.cells.items():
        x, y, z = p
        if y > GROUND:
            continue
        n = B.name_of(s)
        if n in ('minecraft:air', 'minecraft:cave_air') or not K.solid(t, p) or '_stairs' in n or '_wall' in n:
            walk.add(p)
            walk.add((x, y - 1, z))
    t.freeze([p for p in walk if t.inside(*p) and p[1] <= GROUND])
    t.freeze([p for p in JAVA_SHAFT if t.inside(*p)])
    t.freeze([(x, GROUND, z) for (x, z) in MOUTH])
    t.freeze([(7, GROUND, 2), (7, GROUND, 3)])

    m = {
        'brick': masonry(930102),
        'pave': mixer(930103, [('oldrepublic:stormstone_bricks', 4), ('polished_blackstone', 2),
                               ('oldrepublic:tomb_stone', 2), ('cracked_polished_blackstone_bricks', 2),
                               ('oldrepublic:tomb_stone_cracked', 1)], 2.2),
        'rock': mixer(930104, [('basalt', 4), ('blackstone', 3), ('oldrepublic:stormstone', 3), ('tuff', 1),
                               ('gravel', 1), ('smooth_basalt', 1)], 2.0),
        'ring': mixer(930105, [('oldrepublic:stormstone_bricks', 4), ('oldrepublic:tomb_bricks', 3),
                               ('polished_blackstone_bricks', 1)], 2.5),
    }

    # Everything above the ground is rebuilt: clear it (trees and terrain bumps go too).
    for x in range(t.sx):
        for z in range(t.sz):
            for y in range(FEET, t.sy):
                t.set(x, y, z, AIR)

    # The ground: dressed paving round the cleft, broken into the ridge's own rock, fraying into the world.
    fray = Noise(930106, 2.6)
    for x in range(t.sx):
        for z in range(t.sz):
            if t.is_frozen(x, GROUND, z):
                continue
            d = math.hypot(x - 7, (z - 6.5) * 1.08)
            if d > 7.6 or (d > 6.2 and fray.at(x, 0, z) < (d - 6.2) / 1.6):
                if (x, GROUND, z) in t.cells and d > 7.6:
                    t.cells.pop((x, GROUND, z), None)
                continue
            if d <= 4.6 or (d <= 5.6 and fray.at(x, 1, z) > 0.45):
                t.set(x, GROUND, z, m['pave'](x, GROUND, z))
            else:
                t.set(x, GROUND, z, m['rock'](x, GROUND, z))
            # nothing under the ground layer may stay world air: fill the columns under new ground
            for y in range(GROUND - 1, GROUND - 3, -1):
                if (x, y, z) not in t.cells:
                    t.set(x, y, z, m['rock'](x, y, z))

    # The arch: two legs of three by three, a pointed opening seven wide, the keystone on top.
    legs = []
    for x in range(1, 14):
        prof = arch_profile(x)
        if prof is None:
            continue
        hi, top = prof
        for z in range(4, 7):
            for y in range(FEET, top + 1):
                if hi is not None and y <= hi:
                    continue
                corner = (x in (1, 3, 11, 13)) and z in (4, 6) and y <= 24
                if corner:
                    st = pillar()
                elif hi is None and y <= FEET + 1:
                    st = 'polished_blackstone' if hash01(930107, x, y, z) < 0.6 else 'oldrepublic:tomb_stone'
                elif hi is None and y == 24:
                    st = 'polished_blackstone'                     # the impost band
                elif hi is not None or y >= 25:
                    st = m['ring'](x, y, z)                         # the voussoirs
                else:
                    st = m['brick'](x, y, z)
                t.set(x, y, z, st)
                if hi is None:
                    legs.append((x, y, z))
    t.set(*ARCH_TOP, 'chiseled_polished_blackstone')
    # The plinths splay out at the legs' feet; the impost juts as a ledge on both faces.
    for x0 in (1, 11):
        for x in range(x0, x0 + 3):
            t.set(x, FEET, 3, stairs('oldrepublic:tomb_brick_stairs', 'south'))
            t.set(x, FEET, 7, stairs('oldrepublic:tomb_brick_stairs', 'north'))
            t.set(x, 24, 3, stairs('polished_blackstone_brick_stairs', 'south', half='top'))
            t.set(x, 24, 7, stairs('polished_blackstone_brick_stairs', 'north', half='top'))
        xo = x0 - 1 if x0 == 1 else x0 + 3
        for z in range(4, 7):
            t.set(xo, FEET, z, stairs('oldrepublic:tomb_brick_stairs', 'east' if x0 == 1 else 'west'))
            t.set(xo, 24, z, stairs('polished_blackstone_brick_stairs', 'east' if x0 == 1 else 'west', half='top'))
    # The arch's soffit: a chamfer of upside-down stairs where the opening narrows.
    for x in range(4, 11):
        hi, top = arch_profile(x)
        for z in range(4, 7):
            if z == 5:
                continue
            p = (x, hi, z)
            if hi < 28 and abs(x - 7) == 3:
                t.set(*p, stairs('polished_blackstone_brick_stairs', 'west' if x < 7 else 'east', half='top'))

    # The east leg lost its outer corner to a strike; the pieces lie at its foot.
    for (x, y, z) in ((13, 20, 4), (13, 21, 4), (13, 21, 5), (12, 21, 4), (13, 22, 4)):
        t.set(x, y, z, AIR)
    t.set(14, FEET, 2, pillar(axis='x'))
    t.set(13, FEET, 1, 'cracked_polished_blackstone_bricks')
    t.set(14, FEET, 3, slab('oldrepublic:stormstone_brick_slab'))
    t.set(12, FEET, 2, 'oldrepublic:debris')

    # The crown, fused by the bolts: obsidian glass where they hit, magma still glowing under the keystone's
    # shoulders, and fulgurite streaks running down the west leg's north face to the ground.
    for (x, y, z) in ((8, 30, 5), (8, 30, 4), (7, 30, 6), (6, 29, 4), (7, 29, 4), (9, 29, 4), (5, 28, 4)):
        t.set(x, y, z, 'obsidian')
    for (x, y, z) in ((6, 30, 4), (9, 30, 5), (8, 29, 4)):
        t.set(x, y, z, 'blackstone')
    for (x, y, z) in ((6, 28, 5), (7, 29, 5), (8, 28, 5)):
        t.set(x, y, z, 'magma_block')                      # the soffit over the cleft, still hot
    streak = [(4, 28, 4), (3, 27, 4), (3, 26, 4), (2, 25, 4), (2, 24, 4), (2, 23, 4), (2, 22, 4), (1, 21, 4),
              (1, 20, 4), (2, 19, 4), (2, 18, 4)]
    for i, p in enumerate(streak):
        t.set(*p, 'obsidian' if i % 3 != 2 else 'blackstone')
    # Stormglass grows where the storm comes down: a crust on the crown's shoulders and the impost ledges,
    # a few crystals on the legs' faces. Only on whole blocks (never on another crystal).
    def whole(q):
        s = t.cells.get(q)
        return s is not None and K.solid(t, q) and B.name_of(s) != STORMGLASS and '_stairs' not in B.name_of(s) \
            and '_wall' not in B.name_of(s) and '_slab' not in B.name_of(s)
    for x in range(0, 15):
        for z in range(2, 9):
            for y in range(FEET, 31):
                p = (x, y, z)
                if not is_air_set(t, p) or t.is_frozen(*p) or (x, z) in MOUTH:
                    continue
                h = hash01(930108, *p)
                below = (x, y - 1, z)
                if y >= 26 and h < 0.4 and (whole(below) or B.name_of(t.cells.get(below, AIR)).endswith('_stairs')):
                    t.set(*p, storm('up'))
                    continue
                if y >= 21 and h < 0.06:
                    for d, dx, dz in SIDES:
                        q = (x - dx, y, z - dz)
                        if q in legs and whole(q):
                            t.set(*p, storm(d))
                            break

    # The rank of pipes behind the cleft: the song-engine's voice shown at the surface, broken by the
    # storm. Each is a polished basalt shaft on a rough foot; the tall ones keep a crown of stormglass.
    rank = [(3, 12, 4, 'glass'), (4, 12, 6, 'broken'), (5, 13, 8, 'glass'), (6, 12, 9, 'glass'),
            (7, 13, 11, 'glass'), (8, 12, 8, 'broken'), (9, 13, 3, 'stump'), (10, 12, 7, 'glass'),
            (11, 13, 5, 'glass'), (1, 10, 6, 'glass'), (2, 11, 3, 'stump'), (13, 10, 5, 'broken'),
            (12, 9, 2, 'stump'), (0, 2, 4, 'glass'), (1, 1, 2, 'stump'), (14, 8, 3, 'broken')]
    for (x, z, h, end) in rank:
        if not K.solid(t, (x, GROUND, z)):
            t.set(x, GROUND, z, m['rock'](x, GROUND, z))
        for y in range(FEET, FEET + h):
            t.set(x, y, z, 'basalt' if y == FEET else pillar())
        top = FEET + h
        if end == 'glass' and top <= 30:
            t.set(x, top, z, storm('up'))
        elif end == 'broken':
            t.set(x, top - 1, z, stairs('polished_blackstone_brick_stairs',
                                         ('north', 'east', 'south', 'west')[int(hash01(930109, x, z) * 4) % 4]))
    # The toppled half of the stump at (9, 13): it fell east across the paving and broke in two.
    for (x, z) in ((10, 14), (11, 14), (13, 14)):
        t.set(x, FEET, z, pillar(axis='x'))
    t.set(12, FEET, 14, 'oldrepublic:debris')
    # Scorch where the bolts came down the west leg and the pipes; grit and broken glass on the paving.
    for (x, z) in ((0, 3), (0, 7), (1, 8), (4, 2), (2, 2), (9, 11), (12, 12), (2, 9), (5, 11)):
        if t.free(x, FEET, z) and K.solid(t, (x, GROUND, z)):
            t.set(x, FEET, z, 'oldrepublic:scorch_mark')
    for x in range(t.sx):
        for z in range(t.sz):
            p = (x, FEET, z)
            if not t.free(*p) or not K.solid(t, (x, GROUND, z)) or (x, z) in MOUTH:
                continue
            if abs(x - 7) <= 1 and z <= 3:
                continue                                   # the way in stays clean
            h = hash01(930110, x, z)
            if h < 0.07:
                t.set(*p, 'oldrepublic:debris')
            elif h < 0.10:
                t.set(*p, storm('up'))

    # The cleft's lips: a low broken parapet on either side of the stair, its last course fallen in.
    for z in range(5, 10):
        for x in (5, 9):
            if hash01(930111, x, z) < 0.75:
                t.set(x, FEET, z, wall('oldrepublic:stormstone_brick_wall'))
    for x in range(5, 10):
        if hash01(930112, x, 10) < 0.8:
            t.set(x, FEET, 10, wall('oldrepublic:stormstone_brick_wall'))
    # Offerings the Hymnsworn left at the cleft: red candles, a cracked pot, the skulls of the silenced.
    t.set(4, FEET, 8, candle(3, lit=True, color='red'))
    t.set(10, FEET, 9, candle(2, lit=True, color='red'))
    t.set(4, FEET, 9, skull(4))
    t.set(10, FEET, 8, S('decorated_pot', facing='west', cracked=True, waterlogged=False))
    t.set(10, FEET, 7, candle(4, lit=True, color='red'))

    # The shaft's lining: re-faced in the halls' masonry, then red candle niches in the walls where the walk
    # is dark, so the way down glows from below. The cells walked and Hymnal.shaft()'s cells stay as they are.
    K.dress(t, halls_style(930113), (3, 0, 0, 13, GROUND - 1, 14))
    niche_light(t, (4, 0, 0, 12, GROUND, 14), 930114)

    # Nothing above the ground floats: every prop and pipe stands on a ground cell.
    for x in range(t.sx):
        for z in range(t.sz):
            if K.solid(t, (x, FEET, z)) or (not is_air_set(t, (x, FEET, z)) and (x, FEET, z) in t.cells):
                if not K.solid(t, (x, GROUND, z)) and not t.is_frozen(x, GROUND, z):
                    t.set(x, GROUND, z, m['rock'](x, GROUND, z))

    t.anchor('ARCH_TOP', *ARCH_TOP)
    check_reach(t, [STAIR_MOUTH], [(7, 11, 10), (7, 5, 2), (7, 1, 8)], None, tid)
    write_meta(tid, buried=17, max_slope=5, clearance=4, support_depth=4, ramp_rings=1, clear_above=4,
               terrain='high', links={HALLS: HALLS_LINK})
    return save(t, tid, 'hymnal_arch', views=('se', 'nw'), plans=[GROUND, FEET, 24])


def niche_light(t, region, seed, level=8, candles=4):
    """Lights the dark walk in region with red candle niches cut into its lining (a wall cell beside a dark feet
    cell, solid above and below and with solid behind it). Raises when a walk cell stays dark."""
    floors = mod.floor_cells(t, region)
    lit = mod.light_map(t)
    used = set()
    for _ in range(200):
        dark = [p for p in floors if lit.get(p, 0) < level]
        if not dark:
            return sorted(used)
        best = None
        for (x, y, z) in sorted(dark):
            for d, dx, dz in SIDES:
                for yy in (y, y + 1):
                    w = (x + dx, yy, z + dz)
                    if w in used or not t.inside(*w) or t.is_frozen(*w) or not K.solid(t, w):
                        continue
                    if not (K.solid(t, (w[0], yy - 1, w[2])) and K.solid(t, (w[0], yy + 1, w[2]))):
                        continue
                    behind = (w[0] + dx, yy, w[2] + dz)
                    if not K.solid(t, behind) and behind in t.cells:
                        continue
                    trial = mod.light_map(t, {w: candle(candles, lit=True, color='red')})
                    gain = sum(1 for p in dark if trial.get(p, 0) >= level)
                    if best is None or gain > best[0]:
                        best = (gain, w)
            if best is not None and best[0] > 0:
                break
        if best is None or best[0] == 0:
            raise SystemExit('%s: cannot light the walk at %r' % (t.name, dark[:6]))
        t.set(*best[1], candle(candles, lit=True, color='red'))
        used.add(best[1])
        lit = mod.light_map(t)
    raise SystemExit('%s: too many niches' % t.name)

# ---------------------------------------------------------------- the halls (kept)

HEART_C = (18, 60)               # the Heart's middle (HEART), its round wall at radius 15.5
HEART_R = 15.5
DOOR_SIDES = ([(x, y, z) for x in range(17, 20) for y in range(10, 13) for z in (43, 45)]
              + [(x, y, z) for x in (21, 23) for y in range(10, 13) for z in range(37, 40)])
JUNCTION = (15, 24, 0, 21, 27, 5)        # where the processional's first flight meets the arch's last
PROCESSIONAL = (0, 8, 0, 36, 27, 44)
HEART_BOX = (0, 9, 44, 36, 27, 78)
UNDERCROFT = (0, 1, 44, 36, 8, 78)


def in_heart(x, z, margin=0.0):
    return math.hypot(x - HEART_C[0], z - HEART_C[1]) <= HEART_R + margin


def build_halls():
    tid = HALLS
    t = load(tid, seed=930201)
    t.freeze(mechanism(tid))
    t.freeze(list(t.nbt))
    for cells in t.anchors.values():
        for c in cells:
            p = tuple(c)
            if not t.inside(*p):
                continue
            t.freeze([p])
            if is_air_set(t, p):
                t.freeze([(p[0], p[1] + 1, p[2])])
    t.freeze([p for p in DOOR_SIDES if t.inside(*p)])
    t.freeze_box(*JUNCTION)
    # the lightning rods' posts, the dais and its steps, the lectern: the fight's furniture stays
    t.freeze([(x, y, z) for x in range(14, 23) for y in range(9, 13) for z in range(68, 76)
              if (x, y, z) in t.cells and not is_air_set(t, (x, y, z))])
    for name in ('ROD_1', 'ROD_2', 'ROD_3', 'ROD_4'):
        (x, y, z), = t.anchors[name]
        t.freeze([(x, yy, z) for yy in range(9, y + 1)])
    pipes = sorted({(x, z) for (x, y, z), s in t.cells.items()
                    if B.name_of(s) == 'minecraft:polished_basalt' and y >= 10 and z >= 44})
    for (x, z) in pipes:
        t.freeze([(x, y, z) for y in range(t.sy) if B.name_of(t.cells.get((x, y, z), AIR)) == 'minecraft:polished_basalt'])
    opens = {p for p, s in t.cells.items() if B.name_of(s) == 'oldrepublic:sealed_plating'}
    start = (18, 26, 5)
    targets = [(18, 22, 15), (18, 16, 28), (18, 10, 41), (18, 10, 46), (18, 10, 60), (18, 11, 71), (24, 10, 38),
               (24, 2, 47), (18, 2, 52)]
    reach0, keys = protect_routes(t, [start], opens, targets=targets)
    K.niche_ok('oldrepublic:tomb_bricks', 'oldrepublic:stormstone_bricks', 'polished_blackstone_bricks',
               'cracked_polished_blackstone_bricks', 'polished_blackstone', 'oldrepublic:tomb_stone')

    K.dress(t, halls_style(930202), (0, 0, 0, t.sx - 1, t.sy - 1, t.sz - 1))

    heart_floor = 9
    # The song-engine's vents: beside some of the pipes the floor is a grate over the furnace that drives
    # the engine, and its red glow comes up through the bars (and shows in the undercroft's roof below).
    vents = 0
    for i, (px, pz) in enumerate(pipes):
        if not in_heart(px, pz, -2) or (px, pz) in ((16, 75), (20, 75)):
            continue
        sides = [(px + dx, pz + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        sides.sort(key=lambda q: hash01(930203, q[0], q[1]))
        for (x, z) in sides[:1 + (i % 2)]:
            if t.is_frozen(x, heart_floor, z) or t.is_frozen(x, heart_floor + 1, z):
                continue
            if not is_air_set(t, (x, heart_floor + 1, z)) or not K.solid(t, (x, heart_floor, z)):
                continue
            t.set(x, heart_floor, z, bars())
            t.set(x, heart_floor - 1, z, 'shroomlight')
            vents += 1
    # Votive niches at the foot of every wall: red candles in the base course, the light from below.
    def votive(x, y, z, facing):
        n = 2 + int(hash01(930204, x, y, z) * 3)
        return candle(n, lit=True, color='red' if hash01(930205, x, y, z) < 0.8 else 'black')
    K.niches(t, PROCESSIONAL, votive, every=4, seed=930206, height=1)
    K.niches(t, HEART_BOX, votive, every=3, seed=930207, height=1)
    K.niches(t, UNDERCROFT, votive, every=4, seed=930208, height=1)
    # The choir: higher niches in the Heart's round wall and along the processional hold the skulls of the
    # singers the Long Hymn used up, facing the engine they sang to.
    def choir(x, y, z, facing):
        h = hash01(930209, x, y, z)
        if h < 0.62:
            rot = {'south': 0, 'west': 4, 'north': 8, 'east': 12}[facing]
            return skull(rot)
        return 'bone_block' if h < 0.75 else None
    K.niches(t, HEART_BOX, choir, every=2, seed=930210, height=3)
    K.niches(t, PROCESSIONAL, choir, every=3, seed=930211, height=3)

    # Lamps: soul lanterns hung from the dome's crown over the engine (cold light over the fight) and on the
    # undercroft's chains.
    hung = 0
    for (x, z) in ((18, 50), (27, 57), (25, 68), (11, 68), (9, 57), (18, 70)):
        top = max((y for y in range(12, t.sy) if is_air_set(t, (x, y, z))), default=None)
        if top is None or not K.solid(t, (x, top + 1, z)) or not t.free(x, top, z):
            continue
        length = max(1, top - 17)
        if all(t.free(x, top - k, z) for k in range(length + 1)):
            K.hang(t, x, top, z, length, lantern(soul=True, hanging=True))
            hung += 1
    for (p, s) in list(t.cells.items()):
        x, y, z = p
        if B.name_of(s) == 'minecraft:iron_chain' and 1 <= y <= 8 and z >= 44:
            q = (x, y - 1, z)
            if t.free(*q) and is_air_set(t, (x, y - 2, z)):
                t.set(*q, lantern(soul=True, hanging=True))
                hung += 1

    # The dome is cracked where the ridge presses on it: water seeps through and hangs in stone teeth.
    crack = [(18 + dx, 60 + dz) for dx, dz in ((-9, -6), (-8, -5), (-7, -5), (-6, -4), (-5, -4), (-4, -3),
                                               (-3, -3), (-2, -2), (-1, -2), (0, -1), (1, 0), (2, 0), (3, 1),
                                               (4, 2), (5, 2), (6, 3), (7, 4))]
    for i, (x, z) in enumerate(crack):
        top = max((y for y in range(14, t.sy) if is_air_set(t, (x, y, z))), default=None)
        if top is None or not K.solid(t, (x, top + 1, z)) or t.is_frozen(x, top + 1, z):
            continue
        t.set(x, top + 1, z, 'dripstone_block')
        n = 1 + int(hash01(930212, x, z) * 3) if i % 2 == 0 else 0
        kinds = {1: ['tip'], 2: ['frustum', 'tip'], 3: ['base', 'frustum', 'tip']}.get(n, [])
        for k, th in enumerate(kinds):
            if t.free(x, top - k, z):
                t.set(x, top - k, z, S('pointed_dripstone', thickness=th, vertical_direction='down',
                                      waterlogged=False))

    # The Heart's hangings: long crimson cloths on the round wall between the choir's niches, high over the
    # fight; and stormglass on every pipe's crown, where the engine drew the storm down.
    banners = 0
    for k in range(10):
        a = (k + 0.5) * 2 * math.pi / 10
        x = int(round(HEART_C[0] + math.sin(a) * (HEART_R - 0.6)))
        z = int(round(HEART_C[1] - math.cos(a) * (HEART_R - 0.6)))
        if z <= 47 or z >= 72:
            continue                                   # not over the door, not over the dais
        for r in range(0, 3):
            px, pz = x - int(round(math.sin(a) * r)), z + int(round(math.cos(a) * r))
            p = (px, 14, pz)
            if not t.free(*p) or not is_air_set(t, (px, 13, pz)):
                continue
            back = None
            for d, dx, dz in SIDES:
                if K.solid(t, (px + dx, 14, pz + dz)) and math.hypot(px + dx - HEART_C[0], pz + dz - HEART_C[1]) > \
                        math.hypot(px - HEART_C[0], pz - HEART_C[1]):
                    back = d
                    break
            if back is None:
                continue
            t.set(*p, S('red_wall_banner', facing=B.opposite(back)))
            banners += 1
            break
    for (px, pz) in pipes:
        top = max(y for y in range(t.sy) if B.name_of(t.cells.get((px, y, pz), AIR)) == 'minecraft:polished_basalt')
        if t.free(px, top + 1, pz) and top + 1 < t.sy - 1:
            t.set(px, top + 1, pz, storm('up'))

    # The processional's story.
    processional_props(t)

    fill_light(t, (1, 1, 1, t.sx - 2, t.sy - 2, t.sz - 2), level=8, strength=11)
    check_reach(t, [start], list(keys), opens, tid)
    write_meta(tid, buried=0, max_slope=4, clearance=2, support_depth=0, ramp_rings=0, clear_above=0)
    print('  vents %d, lamps %d, banners %d' % (vents, hung, banners))
    return save(t, tid, 'hymnal', cut=[13, 21], plans=[2, 10, 16, 22])


def processional_props(t):
    """The landings: the storm-callers' posts scorched and crusted with stormglass; the stormback's pit with
    what it was fed; the Hask war party that got as far as the first landing; the runner and braziers at
    the Heart's door."""
    # Around each storm-caller's post: the bolts they call leave scorch and stormglass in the floor.
    for name in ('SENTINEL_1', 'SENTINEL_2'):
        (sx, sy, sz), = t.anchors[name]
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                p = (sx + dx, sy, sz + dz)
                if (dx, dz) == (0, 0) or not t.free(*p) or not K.solid(t, (p[0], sy - 1, p[2])):
                    continue
                h = hash01(930220, *p)
                r = math.hypot(dx, dz)
                if h < 0.5 - r * 0.1:
                    t.set(*p, 'oldrepublic:scorch_mark')
                elif h > 0.93:
                    t.set(*p, storm('up'))
    # The first landing: the Hask war party's last stand. Bones, a broken spear, a dead fire.
    for (x, z, st) in ((21, 16, skull(10)), (21, 17, 'bone_block'), (20, 17, 'oldrepublic:debris'),
                       (15, 17, S('campfire', facing='north', lit=False, signal_fire=False, waterlogged=False)),
                       (14, 17, S('lightning_rod', facing='east', powered=False, waterlogged=False)),
                       (16, 10, skull(2)), (22, 11, 'oldrepublic:debris'), (14, 16, 'oldrepublic:scorch_mark')):
        if t.free(x, 22, z) and K.solid(t, (x, 21, z)):
            t.set(x, 22, z, st)
    # The pit: what the stormback was fed, and the chains it is held by.
    gx, gy, gz = t.anchors['GUARD'][0]
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            p = (gx + dx, gy, gz + dz)
            if not t.free(*p) or not K.solid(t, (p[0], gy - 1, p[2])):
                continue
            h = hash01(930221, *p)
            if h < 0.16:
                t.set(*p, S('bone_block', axis='xyz'[int(h * 60) % 3]))
            elif h < 0.26:
                t.set(*p, skull(int(h * 100) % 16))
            elif h < 0.34:
                t.set(*p, 'oldrepublic:debris')
    for (x, z) in ((8, 24), (11, 28)):
        top = max((y for y in range(gy, t.sy) if is_air_set(t, (x, y, z))), default=None)
        if top is not None and K.solid(t, (x, top + 1, z)) and all(t.free(x, top - k, z) for k in range(top - gy - 1)):
            for y in range(gy + 2, top + 1):
                t.set(x, y, z, chain('y'))
    # The Heart's door: braziers either side and a runner to its plating.
    # The Heart's door: a frame of carved columns and a lintel, braziers either side, a runner to the plating.
    for x in (16, 20):
        if all(t.free(x, y, 43) for y in range(10, 13)) and K.solid(t, (x, 9, 43)):
            for y in range(10, 13):
                t.set(x, y, 43, S('oldrepublic:tomb_pillar', axis='y'))
            if t.free(x, 13, 43) or is_air_set(t, (x, 13, 43)):
                t.set(x, 13, 43, 'chiseled_polished_blackstone')
    for x in range(17, 20):
        if is_air_set(t, (x, 13, 43)) and not t.is_frozen(x, 13, 43):
            t.set(x, 13, 43, stairs('oldrepublic:tomb_brick_stairs', 'south', half='top'))
    for x in (15, 21):
        if t.free(x, 10, 42) and K.solid(t, (x, 9, 42)):
            t.set(x, 10, 42, S('campfire', facing='south', lit=True, signal_fire=False, waterlogged=False))
    for z in range(36, 43):
        p = (18, 10, z)
        if is_air_set(t, p) and not t.is_frozen(*p) and K.solid(t, (18, 9, z)):
            t.set(*p, 'red_carpet' if z % 3 else 'black_carpet')


def main(only=None):
    builds = {'arch': build_arch, 'halls': build_halls}
    return [f() for k, f in builds.items() if not only or k in only]


if __name__ == '__main__':
    import sys
    main(sys.argv[1:])
