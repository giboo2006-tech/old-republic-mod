"""varkhast/cliff_mausoleum: a tomb facade carved into a mass of red rock (worldgen; docs/MILESTONE_6.md 5.5).

A butte of banded red rock rises over a forecourt of the valley floor. Its north face is cut back into a
facade of dressed red sandstone between two pilasters: two black colossi of hooded Sith lords, swords
point-down, flank a portal seven blocks high under a lintel and a relief of a sword. A grand stair climbs
from the forecourt to a terrace before the facade, where red light comes up through slots in the floor at
the colossi's feet. Inside, a pillared antechamber with burial niches; at its back a narrow stair goes down
into the inner crypt under it, where the sarcophagus lies on a dais between two channels of red light, and
the dead keep it (site_spawner `tomb`). An acolyte came here for a trial and did not leave.

Variants (start pool, equal weights):
  sealed  the portal is shut by a slab door that someone forced aside; the colossi are whole; the
          sarcophagus is closed.
  broken  the west colossus has fallen across the terrace, part of the cornice lies in the forecourt,
          red sand has drifted up the stair; the sarcophagus lid is thrown down and the dead are out.

Run:  python3 tools/assets/structures/p3/cliff_mausoleum.py [--preview DIR] [--only NAME]
"""
import random

import vk
from vk import S, AIR, Template, mod, Mix, Field, stair, slab, STEP, OPP, RIGHT, LEFT

NAME = 'cliff_mausoleum'
VARIANTS = ('sealed', 'broken')
SIZE = (41, 32, 40)
BURIED = 2
G = 1           # the valley floor (ground layer)
F = 2           # feet on the valley floor
T = 7           # the terrace floor (feet T + 1)
SEED = 960250
SALT = 960250

# the plan (x 0..40 west to east, z 0..39 north to south; the facade faces north)
STAIR_X = (17, 23)          # the grand stair, steps at z 10..15
TERRACE = (5, 16, 35, 20)   # x0, z0, x1, z1 of the terrace floor (y T)
FACADE_Z = 21
PORTAL = (18, 22)           # the portal opening, x; y T+1 .. T+7
ANTE = (13, T + 1, 24, 27, T + 8, 31)    # antechamber interior
CRYPT = (11, F, 23, 31, F + 4, 32)       # crypt interior (its ceiling is the antechamber floor)
COLOSSI = (11, 29)          # the middle x of the two statues (front row z 19)

ROCK_TOP = 27


def build(preview=None, only=None):
    out = []
    for v in VARIANTS:
        if only and v != only:
            continue
        out.append(build_variant(v, preview))
    vk.worldgen(NAME, [(v, 1, SIZE[1]) for v in VARIANTS], ['redrock', 'tomb_valley'], spacing=40, separation=14,
                salt=SALT, buried=BURIED, support=10, ramp=2)
    vk.loot_table('chests/varkhast/cliff_mausoleum', [
        vk.item('oldrepublic:grave_silk', 1, 3),
        vk.item('minecraft:gold_ingot', 1, 4),
        vk.item('minecraft:bone', 2, 4),
        vk.item('oldrepublic:duskglass_shard', 1, 2, chance=0.4),
        vk.item('oldrepublic:credit_chit', 2, 6, chance=0.5),
        vk.item('minecraft:emerald', 1, 2, chance=0.25),
    ])
    return out


def build_variant(v, preview=None):
    tid = 'varkhast/%s/%s' % (NAME, v)
    t = Template(*SIZE, seed=SEED + VARIANTS.index(v) * 17, name=tid)
    rng = t.rng
    rock(t, rng, v)
    forecourt(t, rng, v)
    stair_and_podium(t, rng, v)
    terrace(t, rng, v)
    crypt(t, rng, v)
    antechamber(t, rng, v)
    descent(t, rng, v)
    facade(t, rng, v)
    colossi(t, rng, v)
    weather(t, rng, v)
    lights(t)
    t.finish()
    vk.report(t, tid, ground_y=G, walk=((20, F, 1), [
        (20, T + 1, 18), (20, T + 1, 28), (9, T + 1, 30), (8, F, 22), (21, F, 24), (21, F + 1, 30),
        (14, F, 31), (28, F, 24)], True))
    t.save(tid)
    if preview:
        vk.preview(t, '%s/%s_%s.png' % (preview, NAME, v), tid, plans=[F, T + 1])
        vk.preview(t, '%s/%s_%s_cut.png' % (preview, NAME, v), tid + ' (cut)', views=('se', 'sw'),
                   plans=[], cut=[T + 3, F + 3])
    return t


# ---------------------------------------------------------------- the rock mass

def mass_height(x, z, n, e, v):
    """The top of the rock column at (x, z), or None where the forecourt, the stair and the terrace are.
    The butte has a flat top, near-vertical banded cliffs with one bench, and a talus at their foot; the
    template's edges fall to the ground so the rock meets the valley on every side. n: top noise, e: how
    far in from the edge the cliff stands."""
    sx, sz = SIZE[0], SIZE[2]
    top = ROCK_TOP + (n(x, 0, z) - 0.5) * 3.0
    if z >= FACADE_Z:
        d = min(x, sx - 1 - x, sz - 1 - z) + (e(x, 0, z) - 0.5) * 5.0
    elif x <= 4 or x >= 36:
        # the cheeks: spurs either side of the forecourt, their inner faces sheer, their noses falling north
        d = min(x, sx - 1 - x) + 3.0 + (e(x, 0, z) - 0.5) * 3.0
        top -= max(0.0, 15 - z) * 2.3
    else:
        return None
    if d >= 6.0:
        h = top
    elif d >= 4.0:
        h = top * 0.64                       # the bench
    elif d >= 2.0:
        h = 3.0 + (d - 2.0) * 2.5            # the talus
    elif d >= 0.6:
        h = 1.5 + d * 0.8
    else:
        return None
    return int(round(min(h, top)))


def rock(t, rng, v):
    n = Field(SEED + 3, 7.0)
    e = Field(SEED + 4, 4.0)
    strata = vk.redrock(SEED + 5, base_y=0)
    cap = Mix([('minecraft:red_sand', 3), ('minecraft:terracotta', 2), ('minecraft:coarse_dirt', 2),
               ('minecraft:red_terracotta', 1), ('minecraft:red_sandstone', 1)], SEED + 7, 3.0)
    heights = {}
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            h = mass_height(x, z, n, e, v)
            if h is None or h < G:
                continue
            h = min(h, SIZE[1] - 2)
            heights[(x, z)] = h
            for y in range(0, h + 1):
                t.set(x, y, z, strata(x, y, z))
            # the top: sand and soil only where the column is not a sheer edge (sand needs a flat bed)
            t.set(x, h, z, cap(x, h, z))
            if rng.random() < 0.04 and vk.B.name_of(t.get(x, h, z)) in ('minecraft:red_sand', 'minecraft:coarse_dirt',
                                                                          'minecraft:terracotta'):
                t.set(x, h + 1, z, vk.pick(rng, [('minecraft:dead_bush', 3), ('minecraft:short_dry_grass', 2)]))
    # loose talus at the foot of the cliffs: gravel and sandstone rubble on the lower steps
    for (x, z), h in heights.items():
        if h <= 6 and rng.random() < 0.3:
            t.set(x, h, z, vk.pick(rng, [('minecraft:gravel', 2), ('minecraft:red_sandstone', 1), ('minecraft:red_sand', 2)]))
    # a hoodoo on the back of the butte: a banded column under a wider cap
    hx, hz = (31, 30) if v == 'sealed' else (8, 30)
    base = heights.get((hx, hz))
    if base is not None:
        top = min(SIZE[1] - 2, base + 5)
        for y in range(base, top + 1):
            for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                t.set(hx + dx, y, hz + dz, strata(hx + dx, y + 3, hz + dz))
        for dx in range(-1, 3):
            for dz in range(-1, 3):
                if (dx, dz) not in ((-1, -1), (2, 2), (-1, 2)):
                    t.set(hx + dx, top, hz + dz, 'minecraft:red_sandstone' if (dx + dz) % 3 else 'minecraft:smooth_red_sandstone')


# ---------------------------------------------------------------- the forecourt

def forecourt(t, rng, v):
    under = vk.under_mix('red', SEED + 11)
    floor = vk.ground_mix('valley', SEED + 12)
    pave = Mix([('or:tomb_stone', 4), ('or:tomb_stone_cracked', 3), ('minecraft:polished_blackstone', 2),
                ('minecraft:red_sand', 2)], SEED + 13, 2.5)
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            if t.get(x, G, z) is not None:
                continue
            t.set(x, 0, z, under(x, 0, z))
            t.set(x, G, z, floor(x, G, z))
            # explicit air over the open forecourt clears the terrain the valley may leave there
            for y in range(F, F + 5):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
    # the processional paving from the stair foot to the north edge, half lost in the sand
    for z in range(0, 10):
        for x in range(STAIR_X[0] - 1, STAIR_X[1] + 2):
            if rng.random() < 0.25 + z * 0.06:
                t.set(x, G, z, pave(x, G, z))
    # red light from below: two cracks in the valley floor either side of the paving
    cracks = [vk.fissure_path(rng, (9, 3), 7, 0.45, [(1, 0), (0, 1)]),
              vk.fissure_path(rng, (31, 3), 7, 0.45, [(-1, 0), (0, 1)])]
    for path in cracks:
        cells = [(x, G, z) for (x, z) in path if 6 <= x <= 34 and 0 <= z <= 8]
        vk.ember_fissure(t, cells, SEED + 14)
    # lava braziers before the stair
    for x in (STAIR_X[0] - 2, STAIR_X[1] + 2):
        t.set(x, G, 9, 'minecraft:polished_blackstone')
        vk.lava_brazier(t, x, F, 9, post='minecraft:polished_blackstone_brick_wall', h=2,
                        base='minecraft:chiseled_polished_blackstone')


# ---------------------------------------------------------------- the grand stair and the podium

def stair_and_podium(t, rng, v):
    x0, x1 = STAIR_X
    brick = Mix([('or:tomb_bricks', 6), ('minecraft:polished_blackstone_bricks', 3),
                 ('minecraft:cracked_polished_blackstone_bricks', 2)], SEED + 20, 3.0)
    under = vk.under_mix('red', SEED + 21)
    for k in range(6):
        z = 10 + k
        y = F + k
        for x in range(x0, x1 + 1):
            t.set(x, 0, z, under(x, 0, z))
            for yy in range(G, y):
                t.set(x, yy, z, brick(x, yy, z))
            t.set(x, y, z, stair('or:tomb_brick_stairs', 'south'))
            for yy in range(y + 1, y + 5):
                t.set(x, yy, z, AIR)
        # the balustrades: a stepped wall over solid cheeks
        for x in (x0 - 1, x1 + 1):
            t.set(x, 0, z, under(x, 0, z))
            for yy in range(G, y + 1):
                t.set(x, yy, z, brick(x, yy, z))
            t.set(x, y + 1, z, 'minecraft:polished_blackstone_brick_wall')
    # the podium under the terrace: black masonry with a red sandstone cap course and blind niches
    tx0, tz0, tx1, tz1 = TERRACE
    for x in range(tx0, tx1 + 1):
        for z in range(tz0, tz1 + 1):
            t.set(x, 0, z, under(x, 0, z))
            for yy in range(G, T):
                t.set(x, yy, z, brick(x, yy, z))
    for x in range(tx0, tx1 + 1):
        if x0 <= x <= x1:
            continue
        t.set(x, T - 1, tz0, 'minecraft:cut_red_sandstone')
        if (x - tx0) % 4 == 2 and not (x0 - 2 <= x <= x1 + 2):
            # a blind niche with a skull in it
            t.set(x, F + 1, tz0, AIR)
            t.set(x, F + 2, tz0, AIR)
            t.set(x, F + 3, tz0, stair('or:tomb_brick_stairs', 'north', 'top'))
            t.set(x, F + 1, tz0, vk.skull(8) if rng.random() < 0.6 else vk.candle('black', 1, False))
        t.set(x, F, tz0, 'minecraft:chiseled_polished_blackstone' if x % 4 == 1 else brick(x, F, tz0))


# ---------------------------------------------------------------- the terrace

def terrace(t, rng, v):
    tx0, tz0, tx1, tz1 = TERRACE
    floor = Mix([('or:tomb_stone', 5), ('minecraft:polished_blackstone', 3), ('or:tomb_stone_cracked', 2)], SEED + 30, 2.5)
    for x in range(tx0, tx1 + 1):
        for z in range(tz0, tz1 + 1):
            t.set(x, T, z, floor(x, T, z))
            for y in range(T + 1, T + 18):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
    # a band of polished stone leads from the stair to the portal
    for z in range(tz0, tz1 + 1):
        for x in range(PORTAL[0], PORTAL[1] + 1):
            t.set(x, T, z, 'minecraft:polished_blackstone' if x in PORTAL else 'or:tomb_stone')
    # the parapet along the front edge, with braziers at its ends
    for x in range(tx0, tx1 + 1):
        if STAIR_X[0] - 1 <= x <= STAIR_X[1] + 1:
            continue
        t.set(x, T + 1, tz0, 'minecraft:polished_blackstone_brick_wall')
    for x in (tx0, tx1):
        t.set(x, T + 1, tz0, 'minecraft:chiseled_polished_blackstone')
        vk.lava_brazier(t, x, T + 2, tz0, post='minecraft:polished_blackstone_brick_wall', h=1)
    for x in (STAIR_X[0] - 1, STAIR_X[1] + 1):
        t.set(x, T + 1, tz0, 'minecraft:chiseled_polished_blackstone')
        t.set(x, T + 2, tz0, vk.candle('red', 3, True) if v == 'sealed' else vk.candle('red', 2, False))
    # red light from below at the feet of the colossi: slots of red glass over fire
    for cx in COLOSSI:
        cells = [(x, T, 17) for x in range(cx - 3, cx + 4)]
        vk.ember_fissure(t, cells, SEED + 31 + cx, glass=True)


# ---------------------------------------------------------------- the facade

def facade(t, rng, v):
    z = FACADE_Z
    dressed = Mix([('minecraft:cut_red_sandstone', 5), ('minecraft:smooth_red_sandstone', 3),
                   ('minecraft:red_sandstone', 2)], SEED + 40, 3.0)
    black = Mix([('or:tomb_stone', 4), ('minecraft:polished_blackstone', 3), ('or:tomb_bricks', 2)], SEED + 41, 2.5)
    x0, x1 = 5, 35
    top = 24                      # the cornice; the attic crown sits above it
    # the dressed face: fluted bays of cut and smooth sandstone
    for x in range(x0, x1 + 1):
        for y in range(T + 1, top + 1):
            t.set(x, y, z, 'minecraft:smooth_red_sandstone' if x % 2 == 0 else dressed(x, y, z))
        for y in range(T + 1, top + 4):
            for zz in range(z + 1, z + 3):
                if t.get(x, y, zz) is None or vk.B.name_of(t.get(x, y, zz)) == 'minecraft:air':
                    t.set(x, y, zz, 'minecraft:red_sandstone')
    # the base course
    for x in range(x0, x1 + 1):
        t.set(x, T + 1, z, 'minecraft:chiseled_red_sandstone' if x % 3 == 0 else 'minecraft:smooth_red_sandstone')
    # black pilasters at both ends, proud of the face, on plinths and under carved capitals
    for px in (x0, x0 + 1, x1 - 1, x1):
        for y in range(T + 1, top):
            t.set(px, y, z - 1, S('or:tomb_pillar', axis='y') if px in (x0 + 1, x1 - 1) else black(px, y, z - 1))
        t.set(px, T + 1, z - 1, 'minecraft:polished_blackstone')
        t.set(px, top - 1, z - 1, 'or:tomb_stone_carved')
    # the entablature in black: architrave, a frieze of black and red, a cornice of stairs
    for x in range(x0, x1 + 1):
        t.set(x, top - 1, z - 1, black(x, top - 1, z - 1) if x not in (x0 + 1, x1 - 1) else 'or:tomb_stone_carved')
        t.set(x, top, z, 'minecraft:chiseled_polished_blackstone' if x % 4 == 1 else 'minecraft:chiseled_red_sandstone')
        t.set(x, top, z - 1, 'minecraft:chiseled_polished_blackstone' if x % 4 == 1 else 'minecraft:cut_red_sandstone')
        t.set(x, top + 1, z - 2, stair('or:tomb_brick_stairs', 'south', 'top'))
        t.set(x, top + 1, z - 1, black(x, top + 1, z - 1))
        t.set(x, top + 1, z, black(x, top + 1, z))
    # the attic: a stepped crown over the portal, black-edged, a gilded block at its heart
    for i, (ax0, ax1) in enumerate(((13, 27), (15, 25), (18, 22))):
        y = top + 2 + i
        for x in range(ax0, ax1 + 1):
            t.set(x, y, z, 'minecraft:cut_red_sandstone' if x not in (ax0, ax1) else
                  stair('or:tomb_brick_stairs', 'east' if x == ax0 else 'west'))
            t.set(x, y, z + 1, 'minecraft:red_sandstone')
    t.set(20, top + 3, z - 1, 'minecraft:chiseled_polished_blackstone')
    t.set(20, top + 4, z, 'minecraft:gilded_blackstone')
    t.set(20, top + 5, z, stair('or:tomb_brick_stairs', 'south'))
    # the portal: black jambs of pillars, a lintel, a relief of a sword above
    px0, px1 = PORTAL
    for y in range(T + 1, T + 11):
        for x in (px0 - 2, px0 - 1, px1 + 1, px1 + 2):
            outer = x in (px0 - 2, px1 + 2)
            t.set(x, y, z - 1, S('or:tomb_pillar', axis='y') if not outer else black(x, y, z - 1))
            t.set(x, y, z, black(x, y, z))
    for x in range(px0 - 2, px1 + 3):
        t.set(x, T + 8, z - 1, 'or:tomb_stone_carved')
        t.set(x, T + 9, z - 1, black(x, T + 9, z - 1))
        t.set(x, T + 10, z - 1, 'minecraft:chiseled_polished_blackstone' if x == 20 else 'or:tomb_stone_carved')
        t.set(x, T + 11, z - 2, stair('or:tomb_brick_stairs', 'south', 'top'))
        t.set(x, T + 11, z - 1, black(x, T + 11, z - 1))
    t.set(20, T + 9, z - 1, 'minecraft:gilded_blackstone')
    # the opening, through the facade and the rock behind it
    for x in range(px0, px1 + 1):
        for y in range(T + 1, T + 8):
            for zz in range(z - 1, ANTE[2]):
                t.set(x, y, zz, AIR)
        for zz in range(z - 1, ANTE[2]):
            t.set(x, T, zz, 'minecraft:polished_blackstone' if x in (px0, px1) else 'or:tomb_stone_carved')
    # the relief: a recessed black panel with a sword in gold, point down
    for x in range(17, 24):
        for y in range(T + 12, top - 1):
            edge = x in (17, 23) or y in (T + 12, top - 2)
            t.set(x, y, z, black(x, y, z) if edge else 'minecraft:polished_blackstone')
    for y in range(T + 13, top - 2):
        t.set(20, y, z, 'minecraft:gilded_blackstone' if y < top - 4 else 'minecraft:chiseled_polished_blackstone')
    t.set(19, top - 4, z, stair('minecraft:polished_blackstone_stairs', 'east', 'top'))
    t.set(21, top - 4, z, stair('minecraft:polished_blackstone_stairs', 'west', 'top'))
    # the door slab of the sealed variant: forced aside, a gap at the east jamb
    if v == 'sealed':
        for x in range(px0, px1 - 1):
            for y in range(T + 1, T + 8):
                t.set(x, y, z + 1, black(x, y, z + 1) if (x + y) % 5 else 'or:tomb_stone_carved')
        for y in range(T + 1, T + 8):
            t.set(px0 - 1, y, z + 1, black(px0 - 1, y, z + 1))
        t.set(px1 - 1, T + 1, z - 1, 'or:debris')
        t.set(px1, T + 1, z - 2, 'or:debris')
        t.set(px1 - 2, T + 1, z - 1, slab('minecraft:polished_blackstone_slab'))


# ---------------------------------------------------------------- the colossi

def colossi(t, rng, v):
    # the colossi are cut from grey-black basalt, so they stand out from the black frame of the facade
    mat = Mix([('minecraft:smooth_basalt', 5), ('minecraft:polished_basalt[axis=y]', 2), ('minecraft:basalt[axis=y]', 1),
               ('or:tomb_stone', 1)], SEED + 50, 2.5)
    for i, cx in enumerate(COLOSSI):
        # the plinth
        for x in range(cx - 3, cx + 4):
            for z in (18, 19, 20):
                t.set(x, T + 1, z, 'or:tomb_stone_carved' if z == 18 else 'minecraft:polished_blackstone')
        if v == 'broken' and i == 0:
            # fallen: the robe stands to the belt; the rest lies broken across the terrace and the stair cheek
            cells = vk.statue(t, cx, T + 2, 19, 'north', random.Random(SEED + 51), height=13, mat=mat)
            for (x, y, z) in cells:
                if y > T + 2 + 6:
                    t.set(x, y, z, AIR if z < FACADE_Z else 'minecraft:red_sandstone')
            # the torso and hood lie on the terrace to the east of the plinth, broken in three
            rubble = Mix([('or:tomb_stone', 3), ('minecraft:polished_blackstone', 2), ('or:tomb_stone_cracked', 2)],
                         SEED + 52, 2.0)
            for x in range(cx - 2, cx + 3):
                for z in (16, 17):
                    t.set(x, T + 1, z, rubble(x, T + 1, z))
            for x in range(cx - 1, cx + 2):
                t.set(x, T + 2, 17, rubble(x, T + 2, 17))
            t.set(cx + 2, T + 2, 16, stair('or:tomb_brick_stairs', 'east'))
            t.set(cx - 3, T + 1, 16, slab('or:tomb_brick_slab'))
            # the hood rolled down into the forecourt
            hx, hz = cx - 1, 12
            for (dx, dy, dz) in ((0, 0, 0), (1, 0, 0), (0, 0, 1), (1, 0, 1), (0, 1, 0), (1, 1, 1)):
                t.set(hx + dx, F + dy, hz + dz, mat(hx + dx, F + dy, hz + dz))
            t.set(hx + 1, F + 1, hz, 'minecraft:blackstone')
            t.set(hx, F + 1, hz + 1, stair('or:tomb_brick_stairs', 'north'))
        else:
            vk.statue(t, cx, T + 2, 19, 'north', random.Random(SEED + 51 + i), height=13, mat=mat)


# ---------------------------------------------------------------- the antechamber

def room(t, box, wall, floor=None, ceil=None):
    """A room carved out of the rock: its shell lined with masonry, its inside cleared."""
    x0, y0, z0, x1, y1, z1 = box
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(y0 - 1, y1 + 2):
                inside = x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1
                if inside:
                    t.set(x, y, z, AIR)
                elif y == y0 - 1:
                    vk.put(t, x, y, z, floor or wall)
                elif y == y1 + 1:
                    vk.put(t, x, y, z, ceil or wall)
                else:
                    vk.put(t, x, y, z, wall)


def antechamber(t, rng, v):
    x0, y0, z0, x1, y1, z1 = ANTE
    wall = Mix([('or:tomb_bricks', 7), ('minecraft:polished_blackstone_bricks', 3),
                ('minecraft:cracked_polished_blackstone_bricks', 2), ('or:tomb_stone', 1)], SEED + 60, 3.0)
    floor = Mix([('or:tomb_stone', 5), ('minecraft:polished_blackstone', 2), ('or:tomb_stone_cracked', 2)], SEED + 61, 2.5)
    room(t, ANTE, wall, floor, Mix([('or:tomb_bricks', 3), ('minecraft:polished_blackstone_bricks', 1)], SEED + 62, 3.0))
    # re-open the portal passage into it
    for x in range(PORTAL[0], PORTAL[1] + 1):
        for y in range(y0, y0 + 7):
            t.set(x, y, z0 - 1, AIR)
        t.set(x, y0 - 1, z0 - 1, 'or:tomb_stone_carved')
    # the runner of black stone down the middle
    for z in range(z0, z1 + 1):
        for x in (19, 20, 21):
            t.set(x, y0 - 1, z, 'minecraft:polished_blackstone' if x != 20 else 'minecraft:polished_blackstone_bricks')
    # four pillars with carved capitals and bases
    for px in (16, 24):
        for pz in (26, 30):
            for y in range(y0, y1 + 1):
                t.set(px, y, pz, S('or:tomb_pillar', axis='y'))
            t.set(px, y0, pz, 'or:tomb_stone_carved')
            t.set(px, y1, pz, 'or:tomb_stone_carved')
            for (dx, dz, f) in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
                t.set(px + dx, y1, pz + dz, stair('or:tomb_brick_stairs', f, 'top'))
    # burial niches in the side walls: recesses with urns, skulls and candles
    for (wx, inward, zs) in ((x0 - 1, 'east', (25, 27)), (x1 + 1, 'west', (25, 28, 31))):
        for z in zs:
            for y in (y0 + 1, y0 + 2):
                t.set(wx, y, z, AIR)
            t.set(wx, y0 + 3, z, stair('or:tomb_brick_stairs', inward, 'top'))
            t.set(wx, y0, z, 'or:tomb_stone_carved')
            t.set(wx, y0 + 1, z, vk.pick(rng, [('or:funerary_urn', 3), (vk.skull(rng.randrange(16)), 2),
                                                 ('minecraft:decorated_pot', 1)]))
            if rng.random() < 0.5:
                t.set(wx, y0 + 2, z, vk.candle('black', rng.randrange(1, 4), False))
            # the wall behind the niche
            bx = wx - 1 if inward == 'east' else wx + 1
            t.set(bx, y0 + 1, z, wall(bx, y0 + 1, z))
            t.set(bx, y0 + 2, z, wall(bx, y0 + 2, z))
    # lava braziers between the pillars, high slit vents of red glass in the back wall
    for (bx, bz) in ((16, 28), (24, 28)):
        vk.lava_brazier(t, bx, y0, bz, post='minecraft:polished_blackstone_wall', h=1,
                        base='minecraft:chiseled_polished_blackstone')
    # banners of the dead lord, plain black, either side of the back door
    for bx in (17, 23):
        mod.banner(t, bx, y0 + 5, z1, 'north', base='black')


# ---------------------------------------------------------------- the descent

def descent(t, rng, v):
    """From a landing west of the antechamber a narrow stair goes down north under a stepped ceiling to
    the crypt's west door."""
    wall = Mix([('or:tomb_bricks', 6), ('minecraft:polished_blackstone_bricks', 3), ('minecraft:blackstone', 2)],
               SEED + 70, 3.0)
    floor = Mix([('or:tomb_stone', 4), ('minecraft:polished_blackstone', 2), ('or:tomb_stone_cracked', 2)], SEED + 71, 2.5)
    # the landing, through a doorway in the antechamber's west wall
    room(t, (7, T + 1, 29, 11, T + 4, 31), wall, floor)
    for z in (29, 30, 31):
        for y in range(T + 1, T + 4):
            t.set(12, y, z, AIR)
    t.set(12, T + 4, 29, stair('or:tomb_brick_stairs', 'south', 'top'))
    t.set(12, T + 4, 31, stair('or:tomb_brick_stairs', 'north', 'top'))
    t.set(12, T + 4, 30, 'or:tomb_stone_carved')
    # the flight, down to the north
    for k in range(1, 7):
        z = 29 - k
        s = T + 1 - k                     # the feet over this step
        for x in (7, 8, 9):
            for y in range(G, s - 1):
                t.set(x, y, z, wall(x, y, z))
            t.set(x, s - 1, z, stair('or:tomb_brick_stairs', 'south'))
            for y in range(s, s + 4):
                t.set(x, y, z, AIR)
            t.set(x, s + 4, z, wall(x, s + 4, z))
        for y in range(G, s + 5):
            t.set(6, y, z, wall(6, y, z))
            t.set(10, y, z, wall(10, y, z))
    # the foot of the stair, and the crypt's west door
    for x in (7, 8, 9):
        t.set(x, G, 22, floor(x, G, 22))
        for y in range(F, F + 4):
            t.set(x, y, 22, AIR)
        t.set(x, F + 4, 22, wall(x, F + 4, 22))
        for y in range(G, F + 5):
            t.set(x, y, 21, wall(x, y, 21))
    for y in range(G, F + 5):
        t.set(6, y, 22, wall(6, y, 22))
        t.set(10, y, 22, wall(10, y, 22))
    for z in (23, 24):
        for y in range(F, F + 3):
            t.set(10, y, z, AIR)
    t.set(10, F + 3, 23, 'or:tomb_stone_carved')
    t.set(10, F + 3, 24, 'or:tomb_stone_carved')
    # a soul lantern over the landing, another over the foot of the stair
    vk.hanging_lamp(t, 9, T + 4, 30, length=1, soul=True)
    vk.hanging_lamp(t, 8, F + 3, 22, length=1, soul=True)
    # where the acolyte stopped: a black robe, a dead candle and a skull at the head of the stair
    t.set(8, T + 1, 30, 'minecraft:black_carpet')
    t.set(9, T + 1, 31, 'minecraft:black_carpet')
    t.set(7, T + 1, 31, vk.skull(6))
    t.set(7, T + 1, 29, vk.candle('black', 1, False))
    if v == 'broken':
        t.set(11, T + 1, 31, 'minecraft:bone_block[axis=x]')


# ---------------------------------------------------------------- the inner crypt

def crypt(t, rng, v):
    x0, y0, z0, x1, y1, z1 = CRYPT
    wall = Mix([('or:tomb_bricks', 6), ('minecraft:polished_blackstone_bricks', 3),
                ('minecraft:cracked_polished_blackstone_bricks', 2)], SEED + 80, 3.0)
    floor = Mix([('or:tomb_stone', 5), ('minecraft:polished_blackstone', 3), ('or:tomb_stone_cracked', 2)], SEED + 81, 2.5)
    room(t, CRYPT, wall, floor, Mix([('or:tomb_bricks', 4), ('minecraft:polished_blackstone_bricks', 1)], SEED + 82, 3.0))
    # pillars
    for px in (14, 28):
        for pz in (26, 30):
            for y in range(y0, y1 + 1):
                t.set(px, y, pz, S('or:tomb_pillar', axis='y'))
            t.set(px, y1, pz, 'or:tomb_stone_carved')
    # the dais and the sarcophagus
    dx0, dz0, dx1, dz1 = 18, 25, 24, 30
    for x in range(dx0, dx1 + 1):
        for z in range(dz0, dz1 + 1):
            edge = x in (dx0, dx1) or z in (dz0, dz1)
            if edge:
                f = 'north' if z == dz1 else 'south' if z == dz0 else 'west' if x == dx1 else 'east'
                corner = x in (dx0, dx1) and z in (dz0, dz1)
                t.set(x, y0, z, slab('minecraft:polished_blackstone_brick_slab') if corner else
                      stair('or:tomb_brick_stairs', OPP[f]))
            else:
                t.set(x, y0, z, 'minecraft:polished_blackstone' if (x + z) % 2 else 'or:tomb_stone')
    sx0, sz0, sx1, sz1 = 20, 26, 22, 28
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            t.set(x, y0 + 1, z, 'or:tomb_stone_carved' if x in (sx0, sx1) or z in (sz0, sz1) else 'minecraft:soul_sand')
    if v == 'sealed':
        for x in range(sx0, sx1 + 1):
            for z in range(sz0, sz1 + 1):
                t.set(x, y0 + 2, z, slab('or:tomb_brick_slab') if (x, z) != (21, 27) else 'minecraft:gilded_blackstone')
        t.set(21, y0 + 2, 26, 'minecraft:chiseled_polished_blackstone')
    else:
        # the lid lies broken on the dais, the bones are out
        t.set(19, y0 + 1, 27, slab('or:tomb_brick_slab'))
        t.set(19, y0 + 1, 28, slab('or:tomb_brick_slab'))
        t.set(23, y0 + 1, 26, 'or:tomb_stone_cracked')
        t.set(21, y0 + 2, 27, vk.skull(3))
        t.set(21, y0 + 1, 27, 'minecraft:bone_block[axis=z]')
        t.set(21, y0 + 1, 28, 'minecraft:bone_block[axis=z]')
    # red light from below: two channels of red glass over fire either side of the dais
    for cx in (16, 26):
        vk.ember_fissure(t, [(cx, y0 - 1, z) for z in range(z0 + 1, z1)], SEED + 83 + cx, glass=True)
    # loculi in the north wall: shelves of bones
    for lx in range(12, 31, 3):
        if 18 <= lx <= 24:
            continue
        t.set(lx, y0 + 1, z0 - 1, AIR)
        t.set(lx + 1, y0 + 1, z0 - 1, AIR)
        t.set(lx, y0 + 1, z0 - 2, wall(lx, y0 + 1, z0 - 2))
        t.set(lx + 1, y0 + 1, z0 - 2, wall(lx + 1, y0 + 1, z0 - 2))
        t.set(lx, y0 + 1, z0 - 1, vk.skull(0) if rng.random() < 0.5 else 'minecraft:bone_block[axis=x]')
    # the chest at the sarcophagus's foot, the keeper of the dead under the dais step
    t.chest(21, y0 + 1, 29, facing='south', loot='chests/varkhast/cliff_mausoleum')
    mod.spawner(t, 21, y0, 23, 'tomb')
    # urns and candles along the walls
    for (ux, uz) in ((12, 24), (12, 31), (30, 24), (13, 32), (29, 31)):
        t.set(ux, y0, uz, vk.pick(rng, [('or:funerary_urn', 3), ('minecraft:decorated_pot', 1)]))
    for (cx, cz) in ((12, 27), (30, 28)):
        t.set(cx, y0, cz, vk.candle('red', 3, v == 'sealed'))
    if v == 'broken':
        for (bx, bz) in ((17, 24), (25, 31), (15, 29)):
            t.set(bx, y0, bz, vk.skull(rng.randrange(16)))
        t.set(23, y0, 24, 'or:debris')


# ---------------------------------------------------------------- weather and ruin

def weather(t, rng, v):
    sand = Mix([('minecraft:red_sand', 5), ('minecraft:gravel', 1)], SEED + 90, 2.0)
    # red sand banks against the foot of the podium and the stair (the wind comes down the valley from the north)
    vk.drift(t, (5, G, 0, 35, G, 15), sand, wind='north', depth=2 if v == 'broken' else 1, reach=4, seed=SEED + 91,
             against={'or:tomb_bricks', 'minecraft:polished_blackstone_bricks',
                      'minecraft:cracked_polished_blackstone_bricks', 'minecraft:chiseled_polished_blackstone',
                      'minecraft:cut_red_sandstone'})
    if v == 'broken':
        # part of the cornice lies in the forecourt
        for (x, z) in ((26, 6), (27, 6), (27, 7), (28, 5), (12, 4)):
            t.set(x, F, z, vk.pick(rng, [('minecraft:cut_red_sandstone', 2), ('minecraft:chiseled_red_sandstone', 1),
                                         (stair('minecraft:red_sandstone_stairs', 'east'), 1)]))
        # and the facade has lost a corner of its cornice
        for x in range(30, 36):
            for y in (24, 25):
                for z in (19, 20):
                    if t.get(x, y, z) is not None and (x >= 31 or z == 19):
                        t.set(x, y, z, AIR)
    # dead bushes and dry grass on the forecourt
    for _ in range(18):
        x, z = rng.randrange(0, SIZE[0]), rng.randrange(0, 10)
        if vk.B.name_of(t.get(x, G, z)) in ('minecraft:red_sand', 'minecraft:coarse_dirt', 'minecraft:gravel') \
                and vk.is_air_state(t.get(x, F, z)):
            t.set(x, F, z, vk.pick(rng, [('minecraft:dead_bush', 2), ('minecraft:short_dry_grass', 3)]))


def lights(t):
    for region in (ANTE, CRYPT, (7, T + 1, 29, 11, T + 3, 31), (7, F, 22, 9, T, 28),
                   (PORTAL[0], T + 1, FACADE_Z - 1, PORTAL[1], T + 3, ANTE[2] - 1)):
        mod.light_fill(t, region, level=8, strength=11)


if __name__ == '__main__':
    a = vk.args()
    build(a.preview, a.only)
