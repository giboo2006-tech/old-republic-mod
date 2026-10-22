"""varkhast/obelisk_way: a processional way of leaning obelisks to a sunken shrine (worldgen; MILESTONE_6 5.5).

A paved road runs down the tomb valley between seven pairs of black obelisks. The valley has pushed them
over a little more every century: most lean, some have fallen across the road, and red sand has drifted
over the paving. The acolytes who failed their trials lie in a row of low graves outside the obelisks.
At the far end a broad stair goes down into a court sunk into the valley floor, where a hooded lord of
the Empire's first age stands over an altar under a black canopy, and red light comes up through the
cracks in the floor.

Variants (start pool, equal weights):
  gate    the way opens between two broken pylons whose lintel lies across the road; most obelisks still
          stand, leaning outward; the canopy over the altar is whole.
  fallen  no gate; three obelisks have fallen across the road and another is a stump; the canopy has
          come down on one side and sand has blown into the court.

Run:  python3 tools/assets/structures/p3/obelisk_way.py [--preview DIR] [--only NAME]
"""
import random

import vk
from vk import S, AIR, Template, mod, Mix, Field, stair, slab, STEP, OPP, RIGHT, LEFT

NAME = 'obelisk_way'
VARIANTS = ('gate', 'fallen')
SIZE = (27, 26, 67)
BURIED = 7
G = 6           # the valley floor
F = 7           # feet on the valley floor
CF = 2          # feet on the floor of the sunken court
SEED = 960251
SALT = 960251

ROAD = (10, 16)                         # the paving, x
PAIRS = (6, 13, 20, 27, 34, 41)         # z of the middles of the obelisk pairs
WEST_X, EAST_X = 7, 19                  # x of the middles of the shafts
COURT = (5, 50, 21, 64)                 # the sunken court with its walls: x0, z0, x1, z1


def build(preview=None, only=None):
    out = []
    for v in VARIANTS:
        if only and v != only:
            continue
        out.append(build_variant(v, preview))
    vk.worldgen(NAME, [(v, 1, SIZE[1]) for v in VARIANTS], ['tomb_valley'], spacing=44, separation=16, salt=SALT,
                buried=BURIED, support=10, ramp=2)
    vk.loot_table('chests/varkhast/obelisk_way', [
        vk.item('minecraft:gold_ingot', 1, 3),
        vk.item('oldrepublic:grave_silk', 1, 2),
        vk.item('minecraft:gold_nugget', 3, 9),
        vk.item('minecraft:bone', 1, 3),
        vk.item('oldrepublic:sunstone', 1, 1, chance=0.2),
        vk.item('minecraft:emerald', 1, 1, chance=0.2),
    ])
    return out


def build_variant(v, preview=None):
    tid = 'varkhast/%s/%s' % (NAME, v)
    t = Template(*SIZE, seed=SEED + VARIANTS.index(v) * 31, name=tid)
    rng = t.rng
    ground(t, rng, v)
    road(t, rng, v)
    court(t, rng, v)
    obelisks(t, rng, v)
    if v == 'gate':
        gate(t, rng)
    graves(t, rng, v)
    weather(t, rng, v)
    lights(t)
    t.finish()
    goals = [(13, F, 20), (13, F, 45), (13, CF, 53), (13, CF + 1, 57), (12, CF + 1, 60), (7, CF, 62), (19, CF, 52)]
    vk.report(t, tid, ground_y=G, walk=((13, F, 1), goals, True))
    t.save(tid)
    if preview:
        vk.preview(t, '%s/%s_%s.png' % (preview, NAME, v), tid, plans=[F, CF + 1])
    return t


# ---------------------------------------------------------------- the valley floor

def ground(t, rng, v):
    under = vk.under_mix('red', SEED + 1)
    floor = vk.ground_mix('valley', SEED + 2)
    sx, sy, sz = SIZE
    for x in range(sx):
        for z in range(sz):
            for y in range(0, G):
                t.set(x, y, z, under(x, y, z))
            t.set(x, G, z, floor(x, G, z))
            # explicit air a few blocks over the open ground clears the valley's bumps
            for y in range(F, F + 4):
                t.set(x, y, z, AIR)


# ---------------------------------------------------------------- the road

def road(t, rng, v):
    pave = Mix([('or:tomb_stone', 5), ('or:tomb_stone_cracked', 3), ('minecraft:polished_blackstone', 2),
                ('or:tomb_bricks', 1)], SEED + 10, 2.5)
    sand = Field(SEED + 11, 3.0)
    x0, x1 = ROAD
    for z in range(0, COURT[1] - 3):
        for x in range(x0, x1 + 1):
            # the paving shows through the sand most in the middle of the road and near the shrine
            cover = sand(x, 0, z) + (0.18 if x in (x0, x1) else 0.0) - z * 0.004
            t.set(x, G, z, pave(x, G, z) if cover < 0.47 else 'minecraft:red_sand' if cover < 0.82 else 'minecraft:gravel')
        for x in (x0 - 1, x1 + 1):
            t.set(x, G, z, 'minecraft:polished_blackstone_bricks' if sand(x, 1, z) < 0.7 else 'minecraft:red_sand')


# ---------------------------------------------------------------- the obelisks

def obelisks(t, rng, v):
    """Six pairs of obelisks, taller toward the shrine. Most lean outward; in the fallen variant two have
    come down across the road, each taking its partner's shaft with it (only that base is left), and one
    is a stump."""
    falls = {'gate': {(2, 'east'): 'stump'},
             'fallen': {(1, 'west'): 'across', (1, 'east'): 'base', (3, 'east'): 'across', (3, 'west'): 'base',
                        (5, 'east'): 'stump'}}[v]
    mat = Mix([('or:tomb_bricks', 5), ('minecraft:polished_blackstone_bricks', 3), ('or:tomb_stone', 2)], SEED + 15, 3.0)
    for i, zp in enumerate(PAIRS):
        for side, sx in (('west', WEST_X), ('east', EAST_X)):
            h = 10 + i + rng.randrange(0, 2)
            fate = falls.get((i, side))
            out = -1 if side == 'west' else 1
            if fate == 'across':
                # fallen across the road; its tip lies on the far base
                direction = 'east' if side == 'west' else 'west'
                vk.fallen_obelisk3(t, sx, F, zp, EAST_X - WEST_X - 3, direction, rng, mat=mat, breaks=1)
                # a way through the fallen shaft in the middle of the road
                for x in (12, 13, 14):
                    for z in (zp - 1, zp, zp + 1):
                        for y in (F, F + 1, F + 2):
                            t.set(x, y, z, AIR)
                        t.set(x, F, z, vk.pick(rng, [('or:debris', 2), (slab('minecraft:polished_blackstone_brick_slab'), 1),
                                                     (AIR, 3)]))
            elif fate == 'base':
                vk.obelisk3(t, sx, F, zp, 0, rng, mat=mat)
                for (dx, dz) in ((1, 0), (0, 1), (-1, -1)):
                    t.set(sx + dx, F + 1, zp + dz, vk.pick(rng, [('or:debris', 2), ('minecraft:polished_blackstone_bricks', 1)]))
            elif fate == 'stump':
                vk.obelisk3(t, sx, F, zp, 0, rng, mat=mat)
                for y in range(F + 1, F + 4):
                    for (dx, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                        if y < F + 3 or (dx + dz) % 2 == 0:
                            t.set(sx + dx, y, zp + dz, mat(sx + dx, y, zp + dz))
            else:
                lean_out = rng.choice((0, 1, 1, 2, 2)) if v == 'gate' else rng.choice((1, 1, 2, 2, 3))
                lean_along = rng.choice((-1, 0, 0, 0, 1))
                vk.obelisk3(t, sx, F, zp, h, rng, lean=(out * lean_out, lean_along), mat=mat)


# ---------------------------------------------------------------- the gate

def gate(t, rng):
    """Two broken pylons flank the start of the way; their lintel lies across the road."""
    brick = Mix([('or:tomb_bricks', 6), ('minecraft:polished_blackstone_bricks', 3),
                 ('minecraft:cracked_polished_blackstone_bricks', 2)], SEED + 20, 3.0)
    for (x0, x1, broken) in ((3, 8, 3), (18, 23, 5)):
        for z in range(0, 4):
            for x in range(x0, x1 + 1):
                for y in range(G - 1, G + 1):
                    t.set(x, y, z, brick(x, y, z))
        top = F + 13
        for y in range(F, top):
            inset = (y - F) // 5            # the pylon batters in as it rises
            for x in range(x0 + inset, x1 - inset + 1):
                for z in range(0 + (1 if inset >= 2 else 0), 4):
                    # the broken top: the outer side has lost its upper courses
                    lost = y > top - broken and (x - x0) > (x1 - x0) // 2 if x0 > 10 else y > top - broken and (x - x0) < 2
                    if lost:
                        continue
                    edge = x in (x0 + inset, x1 - inset) or z in (0, 3)
                    band = y in (F + 4, F + 9)
                    t.set(x, y, z, 'minecraft:chiseled_polished_blackstone' if band and edge else brick(x, y, z))
        # a lava brazier in a niche on the road side
        nx = x1 if x0 < 10 else x0
        t.set(nx, F + 2, 1, AIR)
        t.set(nx, F + 1, 1, 'minecraft:lava_cauldron')
        t.set(nx, F + 3, 1, stair('or:tomb_brick_stairs', 'east' if x0 < 10 else 'west', 'top'))
    # the lintel, fallen and broken in three across the road
    for (x, y, z) in ((9, F, 2), (10, F, 2), (11, F, 2), (11, F + 1, 2), (15, F, 3), (16, F, 3), (17, F, 3),
                      (15, F + 1, 3), (16, F, 2)):
        t.set(x, y, z, brick(x, y, z) if (x + z) % 3 else 'or:tomb_stone_carved')
    t.set(13, F, 3, 'or:debris')
    t.set(12, F, 1, slab('minecraft:polished_blackstone_brick_slab'))


# ---------------------------------------------------------------- the sunken court

def court(t, rng, v):
    x0, z0, x1, z1 = COURT
    wall = Mix([('or:tomb_bricks', 6), ('minecraft:polished_blackstone_bricks', 3),
                ('minecraft:cracked_polished_blackstone_bricks', 2), ('or:tomb_stone', 1)], SEED + 30, 3.0)
    floor = Mix([('or:tomb_stone', 5), ('minecraft:polished_blackstone', 3), ('or:tomb_stone_cracked', 2)], SEED + 31, 2.5)
    # the pit: walls from the floor to the valley floor, a parapet above
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set(x, 0, z, 'minecraft:polished_blackstone')
            t.set(x, CF - 1, z, floor(x, CF - 1, z) if not edge else wall(x, CF - 1, z))
            for y in range(CF, F):
                t.set(x, y, z, wall(x, y, z) if edge else AIR)
            if edge:
                t.set(x, G, z, wall(x, G, z))
                t.set(x, F, z, 'minecraft:polished_blackstone_brick_wall')
            else:
                for y in range(F, F + 4):
                    t.set(x, y, z, AIR)
    # the stair down from the end of the road, between cheek walls
    sx0, sx1 = ROAD
    for k in range(5):
        z = z0 - 3 + k                # 47 .. 51
        top = G - 1 - k               # 5 .. 1
        for x in range(sx0, sx1 + 1):
            for y in range(1, top):
                t.set(x, y, z, wall(x, y, z))
            t.set(x, top, z, stair('or:tomb_brick_stairs', 'north'))
            for y in range(top + 1, F + 3):
                t.set(x, y, z, AIR)
        for x in (sx0 - 1, sx1 + 1):
            for y in range(1, F):
                t.set(x, y, z, wall(x, y, z))
            t.set(x, F, z, 'minecraft:polished_blackstone_brick_wall')
    # the parapet steps up into two posts with braziers where the stair meets it
    for x in (sx0 - 1, sx1 + 1):
        t.set(x, F, z0 - 3, 'minecraft:chiseled_polished_blackstone')
        vk.lava_brazier(t, x, F + 1, z0 - 3, post='minecraft:polished_blackstone_brick_wall', h=1)
    # niches in the side walls: urns, skulls, candles
    for z in range(z0 + 2, z1 - 1, 3):
        for (wx, inward) in ((x0, 'east'), (x1, 'west')):
            t.set(wx, CF + 1, z, AIR)
            t.set(wx, CF + 2, z, stair('or:tomb_brick_stairs', inward, 'top'))
            t.set(wx, CF + 1, z, vk.pick(rng, [('or:funerary_urn', 3), (vk.skull(rng.randrange(16)), 2),
                                                 (vk.candle('red', 2, True), 1)]))
            bx = wx - 1 if inward == 'east' else wx + 1
            t.set(bx, CF + 1, z, wall(bx, CF + 1, z))
    # red light from below: cracks across the floor, glowing through red glass
    for start in ((8, 53), (17, 54), (7, 58), (19, 60)):
        path = vk.fissure_path(rng, start, 6, 0.5, [(1, 0), (-1, 0), (0, 1)])
        cells = [(x, CF - 1, z) for (x, z) in path if x0 < x < x1 and z0 < z < z1 and not (8 <= x <= 18 and 55 <= z <= 61)]
        vk.ember_fissure(t, cells, SEED + 32 + start[0], glass=True)
    # lava braziers in the four corners
    for (bx, bz) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        vk.lava_brazier(t, bx, CF, bz, post='minecraft:polished_blackstone_wall', h=1)
    sanctuary(t, rng, v, wall)


def sanctuary(t, rng, v, wall):
    dx0, dz0, dx1, dz1 = 8, 55, 18, 61
    # the dais: one step up, stairs on its edges
    for x in range(dx0, dx1 + 1):
        for z in range(dz0, dz1 + 1):
            edge = x in (dx0, dx1) or z in (dz0, dz1)
            corner = x in (dx0, dx1) and z in (dz0, dz1)
            if corner:
                t.set(x, CF, z, slab('minecraft:polished_blackstone_brick_slab'))
            elif edge:
                f = 'south' if z == dz0 else 'north' if z == dz1 else 'east' if x == dx0 else 'west'
                t.set(x, CF, z, stair('or:tomb_brick_stairs', f))
            else:
                t.set(x, CF, z, 'or:tomb_stone' if (x + z) % 2 else 'minecraft:polished_blackstone')
    # the canopy: four pillars, a black roof in two steps
    roof_y = CF + 7
    pillars = ((9, 56), (17, 56), (9, 60), (17, 60))
    for i, (px, pz) in enumerate(pillars):
        fallen = v == 'fallen' and i in (1, 3)
        top = roof_y - 1 if not fallen else CF + 2 + i // 2
        for y in range(CF + 1, top + 1):
            t.set(px, y, pz, S('or:tomb_pillar', axis='y'))
        t.set(px, CF + 1, pz, 'or:tomb_stone_carved')
        if not fallen:
            t.set(px, top, pz, 'or:tomb_stone_carved')
    if v == 'gate':
        for x in range(dx0, dx1 + 1):
            for z in range(dz0, dz1 + 1):
                t.set(x, roof_y, z, wall(x, roof_y, z) if x in (dx0, dx1) or z in (dz0, dz1) else 'minecraft:polished_blackstone')
        for x in range(dx0 + 1, dx1):
            for z in range(dz0 + 1, dz1):
                t.set(x, roof_y + 1, z, wall(x, roof_y + 1, z))
        for x in range(dx0 + 2, dx1 - 1):
            for z in range(dz0 + 2, dz1 - 1):
                t.set(x, roof_y + 2, z, stair('or:tomb_brick_stairs', 'north' if z < 58 else 'south'))
        t.set(13, roof_y + 2, 58, 'minecraft:chiseled_polished_blackstone')
        t.set(13, roof_y + 3, 58, 'minecraft:gilded_blackstone')
        for x in range(dx0, dx1 + 1):
            t.set(x, roof_y - 1, dz0, stair('or:tomb_brick_stairs', 'south', 'top'))
            t.set(x, roof_y - 1, dz1, stair('or:tomb_brick_stairs', 'north', 'top'))
        vk.hanging_lamp(t, 13, roof_y - 1, 58, length=2, soul=False)
    else:
        # the roof has slid off the east pillars: its west half still stands, the east half lies broken
        # on the dais beside the altar
        for x in range(dx0, 13):
            for z in range(dz0, dz1 + 1):
                t.set(x, roof_y, z, wall(x, roof_y, z))
        for x in range(dx0 + 1, 12):
            for z in range(dz0 + 1, dz1):
                t.set(x, roof_y + 1, z, wall(x, roof_y + 1, z))
        for x in range(15, dx1 + 1):
            for z in range(dz0, dz1 + 1):
                y = CF + 1 + (1 if x == 15 else 0)
                if rng.random() < 0.85:
                    t.set(x, y, z, wall(x, y, z) if (x + z) % 4 else 'or:tomb_stone_cracked')
                    if x == 15:
                        t.set(x, CF + 1, z, wall(x, CF + 1, z))
    # the altar, the offering chest, candles
    for x in (12, 13, 14):
        t.set(x, CF + 1, 59, 'minecraft:chiseled_polished_blackstone' if x != 13 else 'minecraft:gilded_blackstone')
        t.set(x, CF + 2, 59, vk.candle('red', rng.randrange(1, 4), v == 'gate') if x != 13 else
              slab('minecraft:polished_blackstone_slab'))
    t.chest(13, CF + 1, 60, facing='north', loot='chests/varkhast/obelisk_way')
    # the lord at the back of the court
    lord = Mix([('minecraft:smooth_basalt', 5), ('minecraft:polished_basalt[axis=y]', 2), ('or:tomb_stone', 2)], SEED + 40, 2.5)
    cells = vk.statue(t, 13, CF, 62, 'north', random.Random(SEED + 41), height=12, mat=lord)
    if v == 'fallen':
        # headless: the hood lies at the foot of the dais
        for (x, y, z) in cells:
            if y >= CF + 10:
                t.set(x, y, z, AIR)
        t.set(19, CF, 57, lord(19, CF, 57))
        t.set(19, CF + 1, 57, stair('or:tomb_brick_stairs', 'west'))
        t.set(20, CF, 57, 'minecraft:blackstone')


# ---------------------------------------------------------------- the acolytes' graves

def graves(t, rng, v):
    """Low graves in a row outside the obelisks on the west side (and a few on the east): a mound of red
    earth with a black head-post, some with a skull or a burnt-out candle on them."""
    mound = Mix([('minecraft:coarse_dirt', 3), ('minecraft:red_sand', 2), ('minecraft:terracotta', 1)], SEED + 50, 2.0)
    rows = [(2, z) for z in range(8, 44, 4)] + [(24, z) for z in range(10, 40, 7)]
    for (x, z) in rows:
        if rng.random() < 0.2:
            continue
        t.set(x, G, z, 'minecraft:polished_blackstone')
        t.set(x, F, z, 'minecraft:polished_blackstone_wall')
        t.set(x, G, z + 1, mound(x, G, z + 1))
        t.set(x, G, z + 2, mound(x, G, z + 2))
        t.set(x, F, z + 1, slab('minecraft:polished_blackstone_slab') if rng.random() < 0.3 else
              vk.pick(rng, [(vk.skull(rng.randrange(16)), 1), (vk.candle('black', 1, False), 2), (AIR, 2)]))
        if rng.random() < 0.5:
            t.set(x, F + 1, z, vk.candle('red', 1, False))


# ---------------------------------------------------------------- weather

def weather(t, rng, v):
    sand = Mix([('minecraft:red_sand', 5), ('minecraft:gravel', 1)], SEED + 60, 2.0)
    against = {'or:tomb_bricks', 'minecraft:polished_blackstone_bricks', 'minecraft:cracked_polished_blackstone_bricks',
               'minecraft:polished_blackstone', 'or:tomb_stone', 'or:tomb_stone_cracked'}
    vk.drift(t, (0, G, 0, SIZE[0] - 1, G, COURT[1] - 4), sand, wind='north', depth=2, reach=3, seed=SEED + 61,
             against=against)
    if v == 'fallen':
        # sand has blown over the north parapet into the court
        vk.drift(t, (COURT[0] + 1, CF - 1, COURT[1] + 1, COURT[2] - 1, CF - 1, COURT[1] + 4), sand, wind='south',
                 depth=2, reach=3, seed=SEED + 62, against={'or:tomb_bricks', 'minecraft:polished_blackstone_bricks',
                                                            'minecraft:cracked_polished_blackstone_bricks'},
                 only_air=True)
    for _ in range(30):
        x, z = rng.randrange(0, SIZE[0]), rng.randrange(0, COURT[1] - 2)
        if vk.B.name_of(t.get(x, G, z)) in ('minecraft:red_sand', 'minecraft:coarse_dirt', 'minecraft:gravel') \
                and vk.is_air_state(t.get(x, F, z)):
            t.set(x, F, z, vk.pick(rng, [('minecraft:dead_bush', 2), ('minecraft:short_dry_grass', 3)]))


def lights(t):
    mod.light_fill(t, (8, CF + 1, 55, 18, CF + 6, 61), level=8, strength=11)


if __name__ == '__main__':
    a = vk.args()
    build(a.preview, a.only)
