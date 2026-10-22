"""Watchers' Rest (oldrepublic:jedi_ruin): a ruined Watch enclave in the frontier forests.

Pieces (all share the start's rotation: the courtyard's down jigsaw is aligned):
  courtyard, courtyard_2  51 x 30 x 51, buried 4 (start pool; adapted pool element)
  stair                   11 x 12 x 14, the switchback descent under the sanctuary
  listening_hall          25 x 14 x 25, reworked in place (mechanism exact), lit by a shaft from the garden
  pilgrim_niche           the Wayfarer's niche behind the veiled panel
  anchor_cache            the storeroom west of the hall

Courtyard frame: the stair piece's origin is (18, -12, 37) and the hall's (15, -14, 12), so the garden's
light shaft (x 24..26, z 24..28) drops through the hall ceiling at hall x 9..11, z 12..16, beside HEART.
"""
import math

import common as C
from common import S, mod, nature, Noise, stairs, slab

CY = (51, 30, 51)
G = 3          # ground top layer
ST = 4         # stand level (buried 4)
AX = 25        # the court's north-south axis
JIG = (25, 0, 43)
STAIR_ORIGIN = (18, -12, 37)
HALL_ORIGIN = (15, -14, 12)
SHAFT = (24, 26, 24, 28)   # x0, x1, z0, z1 of the light shaft in courtyard coordinates
DOOR_Z = 38                # the veiled stones
APSE = (25, 38)            # the sanctuary's centre (the facade's middle)
RUIN_FULL = ('oldrepublic:ruin_bricks', 'oldrepublic:ruin_stone', 'oldrepublic:ruin_stone_cracked',
             'oldrepublic:ruin_stone_mossy', 'oldrepublic:ruin_stone_carved')
DIRS4 = ('north', 'south', 'east', 'west')


def mats(seed):
    m = {}
    m['wall'] = C.mix(seed + 1, [('or:ruin_bricks', 10), ('or:ruin_stone', 3), ('or:ruin_stone_cracked', 2),
                                 ('or:ruin_stone_mossy', 1)], 3.5)
    m['plinth'] = C.mix(seed + 2, [('or:ruin_stone', 4), ('or:ruin_stone_mossy', 4), ('or:ruin_stone_cracked', 2),
                                   ('minecraft:mossy_cobblestone', 1)], 3.0)
    m['pave'] = C.mix(seed + 3, [('or:ruin_stone', 6), ('or:ruin_bricks', 3), ('or:ruin_stone_cracked', 2),
                                 ('or:ruin_stone_mossy', 2)], 2.5)
    m['earth'] = C.mix(seed + 4, [('minecraft:dirt', 7), ('minecraft:coarse_dirt', 2), ('minecraft:rooted_dirt', 1)], 3)
    m['deep'] = C.mix(seed + 5, [('minecraft:dirt', 3), ('minecraft:stone', 4), ('minecraft:andesite', 1),
                                 ('minecraft:gravel', 1)], 3)
    m['grass'] = C.mix(seed + 6, [(S('grass_block', snowy=False), 22), (S('podzol', snowy=False), 1),
                                  ('minecraft:coarse_dirt', 1), ('minecraft:moss_block', 3)], 6.0)
    m['bed'] = C.mix(seed + 7, [(S('grass_block', snowy=False), 6), ('minecraft:moss_block', 4),
                                (S('podzol', snowy=False), 2), ('minecraft:rooted_dirt', 1)], 2.5)
    m['path'] = C.mix(seed + 8, [('minecraft:dirt_path', 6), ('minecraft:coarse_dirt', 3), ('minecraft:gravel', 2),
                                 ('or:ruin_stone', 2)], 2.0)
    m['found'] = C.mix(seed + 9, [('or:ruin_stone', 3), ('minecraft:stone', 2), ('minecraft:cobblestone', 1)], 3)
    m['dome'] = C.mix(seed + 10, [('or:ruin_bricks', 6), ('or:ruin_stone', 3), ('or:ruin_stone_mossy', 2),
                                  ('minecraft:mossy_stone_bricks', 1)], 3.0)
    return m


# ---------------------------------------------------------------- small builders

def column(t, x, z, y0, top, broken=None, rng=None):
    """A Watch column: carved base, pillar shaft, carved capital. broken: height to stop at (a stump)."""
    stop = top if broken is None else min(top, broken)
    for y in range(y0, stop + 1):
        if y == y0 or y == top:
            t.set(x, y, z, 'or:ruin_stone_carved')
        else:
            t.set(x, y, z, S('or:ruin_pillar', axis='y'))
    if broken is not None and broken < top and rng and rng.random() < 0.5:
        t.set(x, stop + 1, z, slab('or:ruin_brick_slab'))


def column_feet(t, x, z, y):
    """Stair feet around a column base (they break the square silhouette)."""
    for d, (dx, dz) in (('west', (1, 0)), ('east', (-1, 0)), ('north', (0, 1)), ('south', (0, -1))):
        if C.is_airish(t, x - dx, y, z - dz):
            t.set(x - dx, y, z - dz, stairs('or:ruin_brick_stairs', d))


def fallen_column(t, x, z, y, direction, length, rng=None):
    """A column lying on the ground from (x, z) toward direction, capital at the far end."""
    dx, dz = {'west': (-1, 0), 'east': (1, 0), 'north': (0, -1), 'south': (0, 1)}[direction]
    axis = 'x' if dx else 'z'
    for i in range(length):
        cx, cz = x + dx * i, z + dz * i
        if rng and 0 < i < length - 1 and rng.random() < 0.15:
            t.set(cx, y, cz, slab('or:ruin_brick_slab'))
            continue
        t.set(cx, y, cz, 'or:ruin_stone_carved' if i == length - 1 else S('or:ruin_pillar', axis=axis))


def rubble_at(t, x, y, z, rng):
    """One piece of loose masonry on the cell (x, y, z) (which must be air over something solid)."""
    r = rng.random()
    if r < 0.32:
        t.set(x, y, z, slab('or:ruin_brick_slab'))
    elif r < 0.46:
        t.set(x, y, z, stairs('or:ruin_brick_stairs', rng.choice(DIRS4)))
    elif r < 0.58:
        t.set(x, y, z, 'or:debris')
    elif r < 0.82:
        t.set(x, y, z, rng.choice(['or:ruin_stone', 'or:ruin_stone_cracked', 'or:ruin_bricks', 'or:ruin_stone_mossy']))
        if rng.random() < 0.25 and C.is_airish(t, x, y + 1, z):
            t.set(x, y + 1, z, slab('or:ruin_brick_slab'))
    else:
        t.set(x, y, z, 'minecraft:moss_carpet')


def hanging_lantern(t, x, y, z, soul=True, chain=0):
    """A lantern hanging at (x, y, z) from the block above the chain."""
    for k in range(chain):
        t.set(x, y + 1 + k, z, S('iron_chain', axis='y', waterlogged=False))
    t.set(x, y, z, S('soul_lantern' if soul else 'lantern', hanging=True, waterlogged=False))


def standing_lantern(t, x, y, z, soul=True):
    t.set(x, y, z, S('soul_lantern' if soul else 'lantern', hanging=False, waterlogged=False))


def candle(t, x, y, z, n=2, lit=True, colour=''):
    t.set(x, y, z, S((colour + '_candle') if colour else 'candle', candles=n, lit=lit, waterlogged=False))


def pot(t, x, y, z, facing='north', cracked=False):
    t.set(x, y, z, S('decorated_pot', facing=facing, cracked=cracked, waterlogged=False),
          nbt={'id': 'minecraft:decorated_pot'})


def kyber(t, x, y, z, facing='up'):
    t.set(x, y, z, S('or:kyber_cluster', facing=facing))


# ---------------------------------------------------------------- the courtyard

class Court:
    """One courtyard variant; the helpers share its noise, materials and ground heights."""

    def __init__(self, seed, variant):
        tid = 'jedi_ruin/courtyard' + ('' if variant == 1 else '_%d' % variant)
        self.t = C.Template(*CY, seed=seed, name=tid)
        self.tid = tid
        self.v = variant
        self.m = mats(seed)
        self.rng = self.t.rng
        self.n = Noise(seed + 101, 5.0)
        self.n2 = Noise(seed + 202, 2.0)
        self.gaps = Noise(seed + 303, 6.0)
        self.top = {}     # (x, z) -> ground top y where it is not G (pools, pits)
        self.indoor = []  # regions to light (x0, y0, z0, x1, y1, z1)
        self.no_moss = set()

    def gy(self, x, z):
        return self.top.get((x, z), G)

    def ring(self, x, z):
        return min(x, z, self.t.sx - 1 - x, self.t.sz - 1 - z)

    # ground ------------------------------------------------------
    def ground(self):
        t, m = self.t, self.m
        for x in range(t.sx):
            for z in range(t.sz):
                t.set(x, 0, z, m['deep'](x, 0, z))
                t.set(x, 1, z, m['deep'](x, 1, z) if self.n.at(x, 1, z) < 0.45 else m['earth'](x, 1, z))
                t.set(x, 2, z, m['earth'](x, 2, z))
                t.set(x, 3, z, m['grass'](x, 3, z))

    def pave(self, x0, z0, x1, z1, loss=0.15, kind='pave'):
        """Paving at the ground layer, with gaps where grass and moss have taken it back."""
        t, m = self.t, self.m
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if self.gaps.at(x, 7, z) * 0.8 + self.n2.at(x, 9, z) * 0.2 < loss + 0.12:
                    t.set(x, G, z, m['bed'](x, G, z))
                else:
                    t.set(x, G, z, m[kind](x, G, z) if kind in m else kind)

    # walls -------------------------------------------------------
    def wall(self, x0, z0, x1, z1, top_of, thick=2, buttress=0, outward=-1, cap=True):
        """A straight wall from (x0, z0) along x or z for its length, `thick` cells toward +x/+z, rising to
        top_of(i) (None: no wall there). Plinth course at the stand level, a coping on top, and every
        `buttress` cells a pilaster standing proud on the outer face (outward -1: toward -z/-x)."""
        t, m = self.t, self.m
        along_x = z0 == z1
        n = (abs(x1 - x0) if along_x else abs(z1 - z0)) + 1
        for i in range(n):
            x = min(x0, x1) + i if along_x else x0
            z = z0 if along_x else min(z0, z1) + i
            h = top_of(i)
            if h is None or h < G:
                continue
            for d in range(thick):
                cx, cz = (x, z + d) if along_x else (x + d, z)
                for y in range(1, h + 1):
                    t.set(cx, y, cz, m['plinth'](cx, y, cz) if y <= ST else m['wall'](cx, y, cz))
                if cap and h >= ST + 2:
                    t.set(cx, h + 1, cz, slab('or:ruin_brick_slab') if self.n2.at(cx, h, cz) < 0.6
                          else C.wall_block('or:ruin_brick_wall'))
            if buttress and i % buttress == buttress // 2 and h >= ST + 3:
                off = -1 if outward < 0 else thick
                bx, bz = (x, z + off) if along_x else (x + off, z)
                bh = min(h - 1, ST + 4)
                for y in range(1, bh + 1):
                    t.set(bx, y, bz, m['plinth'](bx, y, bz) if y <= ST else m['wall'](bx, y, bz))
                face = ('north' if outward < 0 else 'south') if along_x else ('west' if outward < 0 else 'east')
                t.set(bx, bh + 1, bz, stairs('or:ruin_brick_stairs', C_opp(face)))

    def profile(self, lo, hi, seed, breaches=()):
        """A ragged top-of-wall function; breaches: [(i0, i1, height)] force low spots."""
        nn = Noise(self.t.seed + 7000 + seed, 4.5)

        def f(i):
            h = lo + (hi - lo) * nn.at(i, 0, seed)
            for (a, b, bh) in breaches:
                if a <= i <= b:
                    edge = min(i - a, b - i)
                    h = min(h, bh + edge * 1.5)
            return int(round(h))
        return f

    def finish_air(self):
        C.air_up(self.t, 0, 0, self.t.sx - 1, self.t.sz - 1, ST)

    def sprinkle(self, region, choices, density, avoid_rings=2):
        x0, z0, x1, z1 = region
        cells = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if self.ring(x, z) >= avoid_rings]
        C.sprinkle(self.t, cells, self.gy, choices, density, self.rng)


def C_opp(d):
    return {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}[d]


# ---------------------------------------------------------------- shared pieces of both courtyards

def sanctuary(c, broken_side='east'):
    """The half-dome sanctuary on the south side: a facade on z 37..38 with the veiled doorway (STAIR_MOUTH),
    an apse behind it closed at walking height, a quarter-sphere vault broken open on one side, and the
    stairwell going down from the doorway (flight A to the stair piece's jigsaw)."""
    t, m, rng = c.t, c.m, c.rng
    cx, cz = APSE
    R, r = 10.5, 8.5
    # foundation under the apse and the facade (the stairwell walls)
    for x in range(14, 37):
        for z in range(36, 50):
            d = math.hypot(x - cx, z - cz)
            if z <= 38 or d <= R:
                for y in range(0, G + 1):
                    t.set(x, y, z, m['found'](x, y, z))
    # apse wall y 1..12
    for x in range(13, 38):
        for z in range(39, 50):
            d = math.hypot(x - cx, z - cz)
            if r < d <= R:
                for y in range(1, 13):
                    t.set(x, y, z, m['plinth'](x, y, z) if y <= ST + 1 else m['wall'](x, y, z))
            elif d <= r:
                t.set(x, G, z, C.mix(c.t.seed + 31, [('or:ruin_stone', 3), ('or:ruin_stone_cracked', 1),
                                                     ('or:ruin_bricks', 2)], 2.0)(x, G, z))
    # the apse's buttresses (radial, every 30 degrees), stepped
    for k in range(1, 6):
        a = math.pi * k / 6.0
        bx = cx + math.cos(a) * (R + 1.0)
        bz = cz + math.sin(a) * (R + 1.0)
        for (px, pz) in ((int(round(bx)), int(round(bz))),):
            if not (0 <= pz < t.sz - 1):
                continue
            for y in range(1, 11):
                t.set(px, y, pz, m['plinth'](px, y, pz) if y <= ST + 1 else m['wall'](px, y, pz))
            t.set(px, 11, pz, slab('or:ruin_brick_slab'))
    # the facade: two cells thick on z 37..38, x 14..36, up to y 15 with an attic to y 20 in the middle
    for x in range(14, 37):
        top = 15 if abs(x - cx) > 5 else 20 - max(0, abs(x - cx) - 3)
        for z in (37, 38):
            for y in range(1, top + 1):
                t.set(x, y, z, m['plinth'](x, y, z) if y <= ST + 1 else m['wall'](x, y, z))
    # cornice at y 15 (stairs standing out on the north face) and coping on the attic
    for x in range(14, 37):
        if abs(x - cx) > 5:
            t.set(x, 15, 36, stairs('or:ruin_brick_stairs', 'south', half='top'))
            t.set(x, 16, 37, slab('or:ruin_brick_slab'))
            t.set(x, 16, 38, slab('or:ruin_brick_slab'))
        else:
            top = 20 - max(0, abs(x - cx) - 3)
            t.set(x, top + 1, 37, stairs('or:ruin_brick_stairs', 'east' if x < cx else 'west') if x != cx
                  else 'or:ruin_stone_carved')
    # pilasters on the facade (proud at z 36), carved caps
    for px in (14, 18, 32, 36):
        for y in range(ST, 15):
            t.set(px, y, 36, S('or:ruin_pillar', axis='y') if y > ST else 'or:ruin_stone_carved')
        t.set(px, 15, 36, 'or:ruin_stone_carved')
    # the doorway: jambs (x 23 and 27) and lintel stand proud; reveal at z 37; veiled stones at z 38
    for y in range(ST, 9):
        for jx in (22, 23, 27, 28):
            t.set(jx, y, 36, S('or:ruin_pillar', axis='y') if y < 8 else 'or:ruin_stone_carved')
    for x in range(22, 29):
        t.set(x, 8, 36, 'or:ruin_stone_carved')
        t.set(x, 9, 36, stairs('or:ruin_brick_stairs', 'south', half='bottom') if x not in (22, 28) else
              slab('or:ruin_brick_slab'))
    for x in range(24, 27):
        for y in range(ST, ST + 3):
            t.set(x, y, 36, C.AIR)
            t.set(x, y, 37, C.AIR)
            t.set(x, y, DOOR_Z, S('or:veiled_stone', skin='ruin', veil='veiled'))
        t.set(x, ST + 3, 37, 'or:ruin_stone_carved')   # lintel inside the reveal
        t.set(x, G, 36, 'or:ruin_stone_carved')         # threshold
        t.set(x, G, 37, 'or:ruin_stone_carved')
    t.anchor('STAIR_MOUTH', AX, ST, DOOR_Z)
    # a recessed panel above the door and the lunette (barred: the apse is shut at walking height)
    for x in range(23, 28):
        for y in range(10, 12):
            t.set(x, y, 37, C.AIR if x in (24, 25, 26) else 'or:ruin_stone_carved')
    for x in range(23, 28):
        for y in range(12, 18):
            dxl = abs(x - cx)
            if y - 12 + dxl * 1.6 <= 5.5:
                t.set(x, y, 37, C.AIR)
                t.set(x, y, 38, S('iron_bars', north=False, south=False, east=dxl < 2, west=dxl < 2, waterlogged=False))
    # the half-dome: a quarter sphere over the apse springing at y 13, broken open on one side
    hole = Noise(c.t.seed + 77, 3.0)
    domecu_f = C.mix(c.t.seed + 78, [('minecraft:waxed_oxidized_copper', 5), ('minecraft:waxed_weathered_copper', 3),
                                     ('minecraft:waxed_oxidized_cut_copper', 2)], 2.5)
    domecu = lambda x, y, z: domecu_f(x, y, z)
    for x in range(13, 38):
        for z in range(38, 50):
            for y in range(12, 25):
                dx, dy, dz = x - cx, y - 12, z - cz
                d = math.sqrt(dx * dx + dy * dy + dz * dz)
                if d > R or d <= R - 1.6:
                    continue
                if z == 38 and abs(dx) <= 5 and y < 19:
                    continue  # the lunette's opening stays clear
                side = (dx if broken_side == 'east' else -dx)
                if side > 1.0 and dy > 2 and hole.at(x, y, z) + side * 0.06 + dy * 0.03 > 0.78:
                    continue  # the broken side
                if d > R - 0.75 and dy >= 1:
                    t.set(x, y, z, domecu(x, y, z))
                else:
                    t.set(x, y, z, m['dome'](x, y, z))
    # apse interior air up to the vault
    for x in range(14, 37):
        for z in range(39, 50):
            if math.hypot(x - cx, z - cz) <= r:
                for y in range(ST, 22):
                    dx, dy, dz = x - cx, y - 12, z - cz
                    if y >= 12 and math.sqrt(dx * dx + dy * dy + dz * dz) > R - 1.6:
                        continue
                    if (x, y, z) not in t.cells or C.is_airish(t, x, y, z):
                        t.set(x, y, z, C.AIR)
    # the stairwell: flight A from the doorway down to the stair piece (steps k = 0..2 in this template)
    for x in range(24, 27):
        for k in range(0, 7):
            z = 39 + k
            y_step = 2 - k
            for y in range(max(0, y_step + 1), ST + 3):
                t.set(x, y, z, C.AIR)
            if y_step >= 0:
                t.set(x, y_step, z, stairs('or:ruin_brick_stairs', 'north'))
                for y in range(0, y_step):
                    t.set(x, y, z, m['found'](x, y, z))
    # stairwell walls in the ground (x 23 and 27) and the balustrade around the well
    for z in range(39, 47):
        for wx in (23, 27):
            for y in range(0, G + 1):
                t.set(wx, y, z, 'or:ruin_bricks')
            t.set(wx, ST, z, C.wall_block('or:ruin_brick_wall'))
    for x in range(23, 28):
        t.set(x, ST, 46, C.wall_block('or:ruin_brick_wall'))
        for y in range(0, G + 1):
            t.set(x, y, 46, 'or:ruin_bricks')
    for wx in (23, 27):
        t.set(wx, ST + 1, 39, S('soul_lantern', hanging=False, waterlogged=False))
        t.set(wx, ST + 1, 46, S('soul_lantern', hanging=False, waterlogged=False))
    t.jigsaw(*JIG, 'down_south', name='courtyard_stair', target='stair_top', pool='jedi_ruin/underground',
             joint='aligned')
    # inside the apse: a kneeling Watch figure in a niche behind the well, facing the door
    sx, sz = AX, 47
    for x in (24, 25, 26):
        for y in range(ST, ST + 4):
            t.set(x, y, sz, C.AIR)
        t.set(x, ST + 4, sz, 'or:ruin_stone_carved')
    t.set(sx, ST, sz, 'or:ruin_stone_carved')
    t.set(sx, ST + 1, sz, 'or:ruin_stone')
    t.set(sx, ST + 2, sz, 'or:ruin_stone_mossy')
    t.set(sx, ST + 3, sz, 'minecraft:smooth_stone')
    t.set(sx - 1, ST + 2, sz, stairs('or:ruin_brick_stairs', 'east', half='top'))
    t.set(sx + 1, ST + 2, sz, stairs('or:ruin_brick_stairs', 'west', half='top'))
    t.set(sx - 1, ST, sz, stairs('or:ruin_brick_stairs', 'east'))
    t.set(sx + 1, ST, sz, stairs('or:ruin_brick_stairs', 'west'))
    t.set(sx, ST + 2, sz - 1, stairs('or:ruin_brick_stairs', 'south'))
    for a_deg in (35, 65, 115, 145):
        a = math.radians(a_deg)
        nx = int(round(cx + math.cos(a) * (r + 0.6)))
        nz = int(round(cz + math.sin(a) * (r + 0.6)))
        for y in (ST + 1, ST + 2):
            t.set(nx, y, nz, C.AIR)
        t.set(nx, ST, nz, 'or:ruin_stone_carved')
        kyber(t, nx, ST + 1, nz, 'up')
        t.set(nx, ST + 3, nz, stairs('or:ruin_brick_stairs', 'north' if nz > cz else 'south', half='top'))
    # chains and soul lanterns hanging from the vault
    for (lx, lz, ly) in ((21, 43, 10), (29, 43, 10), (25, 46, 11)):
        top_y = ly + 1
        while top_y < 23 and C.is_airish(t, lx, top_y, lz):
            top_y += 1
        if top_y < 23:
            hanging_lantern(t, lx, ly, lz, soul=True, chain=top_y - ly - 1)
    c.indoor.append((16, ST, 39, 34, 11, 48))


def stand_spot(c):
    """The stand in front of the doorway (PlaythroughTest walks here) stays clear."""
    t = c.t
    for z in range(33, 37):
        for x in range(23, 28):
            t.set(x, G, z, c.m['pave'](x, G, z))
            for y in range(ST, ST + 3):
                if z < 36 or x in (24, 25, 26):
                    t.set(x, y, z, C.AIR)


def light_shaft(c):
    """The garden's light shaft over the Listening Hall: open through the ground layers, covered by a
    verdigris grate flush with the paving, framed in carved stone with four lamp posts."""
    t = c.t
    x0, x1, z0, z1 = SHAFT
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            inner = x0 <= x <= x1 and z0 <= z <= z1
            for y in range(0, G):
                t.set(x, y, z, C.AIR if inner else ('or:ruin_bricks' if y > 0 else 'or:ruin_stone'))
            t.set(x, G, z, 'minecraft:waxed_oxidized_copper_grate' if inner else 'or:ruin_stone_carved')
    for (px, pz) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
        t.set(px, ST, pz, C.wall_block('or:ruin_brick_wall'))
        standing_lantern(t, px, ST + 1, pz, soul=True)


def statue(c, x, z, facing='south'):
    """The robed Watch figure on a plinth, its face worn smooth (plinth 3 x 3, figure 6 high)."""
    t, m = c.t, c.m
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            t.set(x + dx, ST, z + dz, 'or:ruin_stone_carved')
            t.set(x + dx, ST + 1, z + dz, m['plinth'](x + dx, ST + 1, z + dz))
    fz = 1 if facing == 'south' else -1
    body = C.mix(c.t.seed + 55, [('or:ruin_stone', 4), ('or:ruin_stone_mossy', 2), ('or:ruin_stone_cracked', 1)], 2.0)
    # robe hem (wide), body, shoulders, hooded head
    for dx in (-1, 0, 1):
        t.set(x + dx, ST + 2, z, body(x + dx, ST + 2, z))
    t.set(x, ST + 2, z - fz, body(x, ST + 2, z - fz))
    t.set(x, ST + 2, z + fz, stairs('or:ruin_brick_stairs', C_opp(facing)))
    for y in (ST + 3, ST + 4):
        t.set(x, y, z, body(x, y, z))
        t.set(x, y, z - fz, body(x, y, z - fz))
    t.set(x - 1, ST + 3, z, stairs('or:ruin_brick_stairs', 'east'))
    t.set(x + 1, ST + 3, z, stairs('or:ruin_brick_stairs', 'west'))
    t.set(x, ST + 4, z + fz, stairs('or:ruin_brick_stairs', C_opp(facing), half='top'))   # clasped hands
    t.set(x - 1, ST + 5, z, stairs('or:ruin_brick_stairs', 'east', half='top'))
    t.set(x + 1, ST + 5, z, stairs('or:ruin_brick_stairs', 'west', half='top'))
    t.set(x, ST + 5, z, body(x, ST + 5, z))
    t.set(x, ST + 6, z, 'minecraft:smooth_stone')                 # the worn face
    t.set(x, ST + 6, z - fz, stairs('or:ruin_brick_stairs', facing, half='bottom'))   # hood behind
    t.set(x, ST + 7, z, slab('or:ruin_brick_slab'))


COPPER = [('minecraft:waxed_oxidized_cut_copper', 5), ('minecraft:waxed_weathered_cut_copper', 3),
          ('minecraft:waxed_exposed_cut_copper', 1)]


def copper_at(seed, scale=3.0):
    """A callable (x, y, z) -> the cut copper block name to use there (verdigris in patches)."""
    f = C.mix(seed, COPPER, scale)
    return lambda x, y, z: C.name(f(x, y, z))


def gable(c, x0, z0, x1, z1, y, ridge, keep=None, gable_ends=True):
    """A cut-copper gable roof over the rectangle, eaves at y; keep(x, z) False leaves a hole."""
    t, m = c.t, c.m
    cu = copper_at(c.t.seed + 41)
    if ridge == 'x':
        w = z1 - z0 + 1
        for i in range((w + 1) // 2):
            yy = y + i
            front, back = z0 + i, z1 - i
            for x in range(x0, x1 + 1):
                for (z, f) in ((front, 'south'), (back, 'north')):
                    if keep and not keep(x, z):
                        continue
                    if front == back:
                        t.set(x, yy, z, slab(cu(x, yy, z) + '_slab'))
                    else:
                        t.set(x, yy, z, stairs(cu(x, yy, z) + '_stairs', f))
            if gable_ends:
                for x in (x0, x1):
                    for z in range(front + 1, back):
                        if not keep or keep(x, z):
                            t.set(x, yy, z, m['wall'](x, yy, z))
    else:
        w = x1 - x0 + 1
        for i in range((w + 1) // 2):
            yy = y + i
            front, back = x0 + i, x1 - i
            for z in range(z0, z1 + 1):
                for (x, f) in ((front, 'east'), (back, 'west')):
                    if keep and not keep(x, z):
                        continue
                    if front == back:
                        t.set(x, yy, z, slab(cu(x, yy, z) + '_slab'))
                    else:
                        t.set(x, yy, z, stairs(cu(x, yy, z) + '_stairs', f))
            if gable_ends:
                for z in (z0, z1):
                    for x in range(front + 1, back):
                        if not keep or keep(x, z):
                            t.set(x, yy, z, m['wall'](x, yy, z))


def cloister(c, x0, z0, x1, z1, inner, cols, collapsed=False, fall_dir='west', roof_y=10, roof=None, keep=None):
    """A covered walk between the enclosure wall and a line of columns. inner: ('z', 12) means the column
    line runs along z = 12 (the north wing), ('x', 12) along x = 12. collapsed: stumps and fallen shafts."""
    t, m, rng = c.t, c.m, c.rng
    c.pave(x0, z0, x1, z1, loss=0.10 if not collapsed else 0.3)
    axis, line = inner
    for p in cols:
        x, z = (p, line) if axis == 'z' else (line, p)
        if collapsed:
            stump = ST + rng.randint(0, 2)
            column(t, x, z, ST, roof_y - 2, broken=stump, rng=rng)
            fdx, fdz = {'west': (-1, 0), 'east': (1, 0), 'north': (0, -1), 'south': (0, 1)}[fall_dir]
            fallen_column(t, x + fdx * 2, z + fdz * 2, ST, fall_dir, 4 + rng.randint(0, 1), rng)
        else:
            column(t, x, z, ST, roof_y - 2)
            column_feet(t, x, z, ST)
    if collapsed:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if C.is_airish(t, x, ST, z) and rng.random() < 0.18:
                    rubble_at(t, x, ST, z, rng)
        return
    # architrave and frieze along the column line, a copper gable over the walk and the wall
    if axis == 'z':
        for x in range(x0, x1 + 1):
            if keep and not keep(x, line):
                continue
            t.set(x, roof_y - 1, line, S('or:ruin_pillar', axis='x'))
            t.set(x, roof_y, line, m['wall'](x, roof_y, line))
            for z in range(z0, line):
                for y in range(ST, roof_y + 1):
                    if C.is_airish(t, x, y, z):
                        t.set(x, y, z, C.AIR)
        rx0, rz0, rx1, rz1 = roof
        gable(c, rx0, rz0, rx1, rz1, roof_y, 'x', keep=keep)
        c.indoor.append((x0, ST, z0, x1, roof_y - 1, line))
    else:
        for z in range(z0, z1 + 1):
            if keep and not keep(line, z):
                continue
            t.set(line, roof_y - 1, z, S('or:ruin_pillar', axis='z'))
            t.set(line, roof_y, z, m['wall'](line, roof_y, z))
            for x in range(x0, line):
                for y in range(ST, roof_y + 1):
                    if C.is_airish(t, x, y, z):
                        t.set(x, y, z, C.AIR)
        rx0, rz0, rx1, rz1 = roof
        gable(c, rx0, rz0, rx1, rz1, roof_y, 'z', keep=keep)
        c.indoor.append((x0, ST, z0, line, roof_y - 1, z1))


def tower(c, cx, cz, rad, low, high, low_angle, door_side='west'):
    """The watchtower: an octagonal shaft two cells thick, broken off along a slant that is lowest on the
    side it fell toward (low_angle, radians, x east / z south). A stair winds up round a solid core to a
    lookout on the core's top at y low."""
    t, m, rng = c.t, c.m, c.rng
    nn = Noise(c.t.seed + 404, 2.5)
    cells = C.octagon_cells(cx, cz, rad)
    inner = set(C.octagon_cells(cx, cz, rad - 2))
    foot = C.octagon_cells(cx, cz, rad + 1)
    for (x, z) in foot:
        if (x, z) not in cells:
            for y in range(1, ST + 1):
                t.set(x, y, z, m['plinth'](x, y, z))
            t.set(x, ST + 1, z, slab('or:ruin_brick_slab'))
    for (x, z) in cells:
        a = math.atan2(z - cz, x - cx)
        f = (1 - math.cos(a - low_angle)) / 2.0          # 0 on the low side, 1 opposite
        top = int(round(low + (high - low) * f + (nn.at(x, 0, z) - 0.5) * 4))
        if (x, z) in inner:
            t.set(x, G, z, 'or:ruin_stone')
            continue
        for y in range(1, top + 1):
            if y <= ST + 1:
                st = m['plinth'](x, y, z)
            elif y % 6 == 3:
                st = 'or:ruin_stone_carved'   # string courses
            else:
                st = m['wall'](x, y, z)
            t.set(x, y, z, st)
    # merlons standing on the high side of the break
    for (x, z) in cells:
        if (x, z) in inner:
            continue
        a = math.atan2(z - cz, x - cx)
        f = (1 - math.cos(a - low_angle)) / 2.0
        if f > 0.8 and (x + z) % 2 == 0:
            top = int(round(low + (high - low) * f + (nn.at(x, 0, z) - 0.5) * 4))
            t.set(x, top + 1, z, m['wall'](x, top + 1, z))
            t.set(x, top + 2, z, slab('or:ruin_brick_slab'))
    # interior: core and winding stair
    xs = sorted({x for x, z in inner})
    zs = sorted({z for x, z in inner})
    ix0, ix1, iz0, iz1 = xs[0], xs[-1], zs[0], zs[-1]
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            for y in range(ST, low + 4):
                t.set(x, y, z, C.AIR)
    for x in range(ix0 + 1, ix1):
        for z in range(iz0 + 1, iz1):
            for y in range(ST, low):
                t.set(x, y, z, m['wall'](x, y, z))
            t.set(x, low - 1, z, 'or:ruin_stone_carved')
    ringpath = [(ix0, iz1 - 1), (ix0, iz1)] + [(x, iz1) for x in range(ix0 + 1, ix1 + 1)] + \
               [(ix1, z) for z in range(iz1 - 1, iz0 - 1, -1)] + [(x, iz0) for x in range(ix1 - 1, ix0 - 1, -1)]
    y = ST
    steps = []
    for i, (x, z) in enumerate(ringpath):
        if y >= low:
            break
        if i + 1 < len(ringpath):
            nx, nz = ringpath[i + 1]
            d = 'east' if nx > x else 'west' if nx < x else 'south' if nz > z else 'north'
        else:
            d = 'north'
        t.set(x, y, z, stairs('or:ruin_brick_stairs', d))
        for yy in range(ST, y):
            t.set(x, yy, z, m['wall'](x, yy, z))
        steps.append((x, y, z))
        y += 1
    # door through the wall
    if door_side == 'west':
        dz0 = iz0 + 1
        for x in range(cells and min(x for x, z in cells), ix0):
            for z in (dz0, dz0 + 1):
                for yy in range(ST, ST + 3):
                    t.set(x, yy, z, C.AIR)
                t.set(x, G, z, 'or:ruin_stone_carved')
            t.set(x, ST + 3, dz0, 'or:ruin_stone_carved')
            t.set(x, ST + 3, dz0 + 1, 'or:ruin_stone_carved')
    # arrow slits on the high side
    for (x, z) in cells:
        if (x, z) in inner:
            continue
        a = math.atan2(z - cz, x - cx)
        if (1 - math.cos(a - low_angle)) / 2.0 < 0.55:
            continue
        neigh_in = any((x + dx, z + dz) in inner for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if neigh_in:
            continue
        if (x + z) % 4 == 0:
            for yy in (low + 2, low + 3):
                if t.get(x, yy, z):
                    t.set(x, yy, z, C.AIR)
                    # and the cell behind toward the inside
                    bx = x + (1 if x < cx else -1 if x > cx else 0)
                    bz = z + (1 if z < cz else -1 if z > cz else 0)
                    if t.get(bx, yy, bz) and (bx, bz) not in inner:
                        t.set(bx, yy, bz, C.AIR)
    c.indoor.append((ix0, ST, iz0, ix1, low, iz1))
    return steps


def lying_drum(c, xc, yc, z0, z1, r, crown=False, gap=None):
    """A section of the tower's octagonal shaft lying on its side along z (axis at x xc, height yc): a
    shell one to two cells thick with carved string courses, ragged where it broke, merlons on the crown's
    far rim. gap: a z range left open (where it cracked in two)."""
    t, m, rng = c.t, c.m, c.rng
    nn = Noise(c.t.seed + 515, 1.8)
    cells = C.disk_cells(xc, yc, r)             # (x, y) pairs, using the helper's x/z as x/y
    inner = set(C.disk_cells(xc, yc, r - 1.0))
    for z in range(z0, z1 + 1):
        if gap and gap[0] <= z <= gap[1]:
            continue
        end = min(z - z0, z1 - z)
        for (x, y) in cells:
            if y < ST:
                continue
            if (x, y) in inner:
                if y == ST and rng.random() < 0.5:
                    rubble_at(c.t, x, y, z, rng)
                elif C.is_airish(t, x, y, z):
                    t.set(x, y, z, C.AIR)
                continue
            if end < 2 and nn.at(x, y, z) < 0.35 + 0.15 * (2 - end):
                continue   # the broken end is ragged
            if (z - z0) % 5 == 2:
                st = 'or:ruin_stone_carved'
            elif (z - z0) % 5 == 4 and abs(y - yc) < 1 and (x < xc - r + 1.5 or x > xc + r - 1.5):
                st = C.AIR    # an arrow slit, now lying on its side
            else:
                st = m['wall'](x, y, z)
            t.set(x, y, z, st)
            c.no_moss.add((x, y, z))
    if crown:
        # merlons stand out radially from the far rim; the parapet walk's slabs lie inside
        zc = z1 + 1
        for (x, y) in cells:
            if (x, y) in inner or y < ST:
                continue
            if (x + y) % 2 == 0:
                t.set(x, y, zc, m['wall'](x, y, zc))
        for (x, y) in inner:
            if y > ST and nn.at(x, y, zc) > 0.5:
                t.set(x, y, z1, slab('or:ruin_brick_slab', 'top' if x < xc else 'bottom'))


def fallen_tower(c, xc, z_base, low, rad):
    """The tower's upper shaft where it fell, due south along the east wing: two drum sections on their
    sides (it cracked in two), the crown with its merlons at the far end, rubble spilling both ways."""
    t, rng = c.t, c.rng
    yc = ST + rad - 1.0
    z0 = z_base
    lying_drum(c, xc, yc, z0, z0 + 13, rad - 0.5, crown=True, gap=(z0 + 7, z0 + 8))
    # rubble along both sides, thick by the stump and where it cracked
    for x in range(int(xc - rad - 6), int(xc + rad + 4)):
        for z in range(z0 - 3, z0 + 18):
            if not (0 <= x < t.sx and 0 <= z < t.sz) or c.ring(x, z) < 2:
                continue
            w = abs(x - xc)
            if w <= rad - 0.5 and z0 <= z <= z0 + 14:
                continue
            dens = 0.5 * max(0.0, 1 - (w - rad) / 6.0)
            if z < z0 + 2 or z0 + 6 <= z <= z0 + 9:
                dens += 0.25
            y = ST
            while y < ST + 3 and not C.is_airish(t, x, y, z):
                y += 1
            if y >= ST + 3 or rng.random() > dens:
                continue
            if not C.is_airish(t, x, y, z):
                continue
            rubble_at(t, x, y, z, rng)


def room_box(c, x0, z0, x1, z1, top_of, roof_y=None, roof_loss=0.0):
    """Walls of a small building (one cell thick) from y 1 to top_of(x, z) and an optional slab roof."""
    t, m = c.t, c.m
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                h = top_of(x, z)
                for y in range(1, h + 1):
                    t.set(x, y, z, m['plinth'](x, y, z) if y <= ST else m['wall'](x, y, z))
            else:
                t.set(x, G, z, m['pave'](x, G, z))
                for y in range(ST, (roof_y or ST + 6)):
                    t.set(x, y, z, C.AIR)
    if roof_y:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if c.n2.at(x, roof_y, z * 1.3) < roof_loss:
                    continue
                t.set(x, roof_y, z, slab('or:ruin_brick_slab', 'double' if x in (x0, x1) or z in (z0, z1) else 'bottom'))


def hipr(c, x0, z0, x1, z1, y, cu, hole=None):
    """A cut-copper hipped roof; hole (x, z, r) leaves a broken patch open."""
    t = c.t
    i = 0
    while x0 + i <= x1 - i and z0 + i <= z1 - i:
        yy = y + i
        ax, bx, az, bz = x0 + i, x1 - i, z0 + i, z1 - i
        ring = []
        if ax == bx or az == bz:
            for x in range(ax, bx + 1):
                for z in range(az, bz + 1):
                    ring.append((x, z, None))
        else:
            for x in range(ax, bx + 1):
                ring.append((x, az, 'south'))
                ring.append((x, bz, 'north'))
            for z in range(az + 1, bz):
                ring.append((ax, z, 'east'))
                ring.append((bx, z, 'west'))
        for (x, z, f) in ring:
            if hole and math.hypot(x - hole[0], z - hole[1]) <= hole[2]:
                continue
            t.set(x, yy, z, slab(cu(x, yy, z) + '_slab') if f is None else stairs(cu(x, yy, z) + '_stairs', f))
        if ax == bx or az == bz:
            break
        i += 1


def scriptorium(c, x0, z0, x1, z1, door_x):
    """The Watch's reading room: shelves stripped by the diggers, a lectern with no book, a desk, candles."""
    t, m, rng = c.t, c.m, c.rng
    apse = lambda x, z: math.hypot(x - APSE[0], z - APSE[1]) <= 10.5 and z >= 39
    room_box(c, x0, z0, x1, z1, lambda x, z: 9, roof_y=None)
    cu = copper_at(c.t.seed + 43)
    hipr(c, x0, z0, x1, z1, 10, cu, hole=(x0 + 5, z0 + 6, 2))
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if apse(x, z):
                for y in range(1, 10):
                    t.set(x, y, z, m['wall'](x, y, z))
    for x in (door_x, door_x + 1):
        for y in range(ST, ST + 3):
            t.set(x, y, z0, C.AIR)
        t.set(x, G, z0, 'or:ruin_stone_carved')
    t.set(door_x - 1, ST + 3, z0, 'or:ruin_stone_carved')
    t.set(door_x + 2, ST + 3, z0, 'or:ruin_stone_carved')
    # shelves on the west wall, one fallen; a lectern; a desk with a stool; candles
    for z in range(z0 + 2, z1 - 1):
        for y in range(ST, ST + 3):
            if z == z0 + 5 and y > ST:
                continue
            t.set(x0 + 1, y, z, 'minecraft:bookshelf' if (y + z) % 3 else
                  S('chiseled_bookshelf', facing='east', **{'slot_%d_occupied' % i: False for i in range(6)}))
    t.set(x0 + 2, ST, z0 + 5, S('spruce_trapdoor', facing='east', half='bottom', open=False, powered=False, waterlogged=False))
    t.set(x0 + 3, ST, z0 + 6, 'minecraft:bookshelf')
    mod.lectern(t, x0 + 4, ST, z1 - 2, facing='north', book=False)
    t.set(x0 + 3, ST, z0 + 2, stairs('spruce_stairs', 'east'))
    t.set(x0 + 4, ST, z0 + 2, S('spruce_trapdoor', facing='north', half='top', open=False, powered=False, waterlogged=False))
    candle(t, x0 + 4, ST + 1, z0 + 2, n=3)
    candle(t, x0 + 1, ST + 3, z1 - 2, n=2)
    pot(t, x1 - 1, ST, z1 - 1, cracked=True)
    t.set(x1 - 1, ST, z0 + 1, S('barrel', facing='up', open=False))
    t.set(x0 + 1, ST + 3, z0 + 1, 'minecraft:cobweb')
    hanging_lantern(t, (x0 + x1) // 2, 8, (z0 + z1) // 2, soul=True, chain=1)
    c.indoor.append((x0 + 1, ST, z0 + 1, x1 - 1, 9, z1 - 1))


def ruined_room(c, x0, z0, x1, z1, fall='west'):
    """A side building whose walls fell in the direction of the collapse; a birch grows out of it."""
    t, m, rng = c.t, c.m, c.rng
    nn = Noise(c.t.seed + 808, 3.0)
    apse = lambda x, z: math.hypot(x - APSE[0], z - APSE[1]) <= 10.5 and z >= 39

    def top(x, z):
        base = 5 + int(4 * nn.at(x, 0, z))
        if fall == 'west' and x == x1:
            base -= 2
        return base
    room_box(c, x0, z0, x1, z1, top)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if apse(x, z):
                for y in range(1, 10):
                    t.set(x, y, z, m['wall'](x, y, z))
            elif rng.random() < 0.3:
                rubble_at(t, x, ST, z, rng)
    C.tree(t, (x0 + x1) // 2, ST, z1 - 2, kind='birch', height=8, rng=rng, crown=2.4)
    for z in range(z0 + 1, z1, 3):
        C.put(t, x0 + 1, ST, z, slab('or:ruin_brick_slab'), only_empty=True)


def garden_beds(c, beds, pool=None):
    """Garden beds (x0, z0, x1, z1) in grass and moss with carved kerbs flush with the paving."""
    t, m = c.t, c.m
    for (x0, z0, x1, z1) in beds:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                t.set(x, G, z, 'or:ruin_stone_carved' if edge and c.n2.at(x, 3, z) > 0.25 else m['bed'](x, G, z))
    if pool:
        x0, z0, x1, z1 = pool
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x0 <= x <= x1 and z0 <= z <= z1:
                    t.set(x, 1, z, 'or:ruin_stone')
                    t.set(x, 2, z, 'minecraft:water')
                    t.set(x, G, z, 'minecraft:water')
                    c.top[(x, z)] = None
                else:
                    t.set(x, G, z, 'or:ruin_stone_carved')
        for (x, z) in ((x0, z0), (x1 - 1, z1), (x0 + 2, z1 - 1)):
            t.set(x, ST, z, 'minecraft:lily_pad')


def diggers_v1(c):
    """Vasch's diggers camp under the north cloister's east end and dig into the fallen crown."""
    t, m, rng = c.t, c.m, c.rng
    # bedrolls along the back wall, kit and crates
    for (x, z, col) in ((29, 8, 'brown'), (31, 8, 'green'), (33, 9, 'brown')):
        t.set(x, ST, z, S('%s_carpet' % col))
        t.set(x + 1, ST, z, 'minecraft:white_carpet')
    t.chest(35, ST, 8, facing='south', loot='chests/jedi_ruin')
    t.anchor('CHEST_1', 35, ST, 8)
    t.set(36, ST, 8, S('barrel', facing='up', open=False))
    t.set(36, ST + 1, 8, S('barrel', facing='north', open=False))
    t.set(28, ST, 8, 'or:cargo_crate')
    t.set(28, ST + 1, 8, S('lantern', hanging=False, waterlogged=False))
    # a tarp awning out over the garden edge, on spruce posts
    for x in range(29, 35):
        for z in range(13, 16):
            t.set(x, 9, z, slab('or:canvas_slab', 'top') if z < 15 else stairs('or:canvas_stairs', 'south', half='bottom'))
    for (px, pz) in ((29, 15), (34, 15)):
        for y in range(ST, 9):
            t.set(px, y, pz, S('spruce_fence', waterlogged=False))
    # cook fire with log seats and a pot
    t.set(31, ST, 17, S('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
    t.set(30, ST, 19, S('stripped_spruce_log', axis='x'))
    t.set(31, ST, 19, S('stripped_spruce_log', axis='x'))
    t.set(33, ST, 17, S('stripped_spruce_log', axis='z'))
    t.set(29, ST, 17, 'minecraft:cauldron')
    # the finds table: crates of relics, pots, a map
    t.set(32, ST, 13, 'minecraft:cartography_table')
    t.set(32, ST + 1, 13, S('copper_lantern', hanging=False, waterlogged=False))
    t.set(33, ST, 13, S('spruce_fence', waterlogged=False))
    t.set(33, ST + 1, 13, S('spruce_pressure_plate', powered=False))
    pot(t, 30, ST, 13, facing='south')
    pot(t, 34, ST, 14, facing='west', cracked=True)
    t.set(30, ST, 14, S('barrel', facing='up', open=True))
    t.set(29, ST, 14, 'minecraft:grindstone[face=floor,facing=north]')
    # the dig: a pit beside the crown, a breach they cut into its side, spoil heaps, a sieve, a winch
    for x in range(33, 37):
        for z in range(25, 29):
            t.set(x, G, z, C.AIR)
            t.set(x, 2, z, C.AIR)
            t.set(x, 1, z, 'minecraft:suspicious_gravel' if (x + z) % 3 == 0 else 'minecraft:coarse_dirt')
            for y in range(ST, ST + 3):
                if C.name(t.get(x, y, z)) not in ('oldrepublic:ruin_stone_carved',):
                    t.set(x, y, z, C.AIR)
            c.top[(x, z)] = 1
    for z in (26, 27):
        for x in (37, 38):
            for y in range(ST + 1, ST + 4):
                t.set(x, y, z, C.AIR)
        t.set(37, ST, z, stairs('spruce_stairs', 'east'))
    t.set(33, 2, 25, S('ladder', facing='east', waterlogged=False))
    t.set(33, G, 25, S('ladder', facing='east', waterlogged=False))
    t.set(32, 2, 25, 'or:ruin_bricks')
    t.set(32, G, 25, 'or:ruin_bricks')
    for (x, z) in ((32, 29), (31, 27), (32, 24), (36, 30)):
        t.set(x, ST, z, 'minecraft:suspicious_gravel' if (x * z) % 2 else 'minecraft:gravel')
    t.set(30, ST, 29, 'minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]')
    t.set(30, ST + 1, 29, S('spruce_trapdoor', facing='west', half='bottom', open=False, powered=False, waterlogged=False))
    for (x, z) in ((32, 24), (36, 24), (34, 29)):
        for y in range(ST, ST + 4):
            t.set(x, y, z, S('spruce_fence', waterlogged=False))
    for x in range(32, 37):
        t.set(x, ST + 4, 24, S('stripped_spruce_log', axis='x'))
    for z in range(25, 30):
        t.set(34, ST + 4, z, S('stripped_spruce_log', axis='z'))
    t.set(34, ST + 3, 26, S('iron_chain', axis='y', waterlogged=False))
    t.set(34, ST + 2, 26, S('iron_chain', axis='y', waterlogged=False))
    t.set(34, ST + 1, 26, S('lantern', hanging=True, waterlogged=False))
    for y in range(ST, ST + 3):
        t.set(31, y, 22, C.AIR)
    t.set(31, G, 22, 'minecraft:coarse_dirt')
    mod.spawner(t, 31, ST, 22, 'diggers', anchor='DIGGERS')


def outside_v1(c):
    """The pilgrim road from the gate, trees and undergrowth round the walls."""
    t, m, rng = c.t, c.m, c.rng
    pts = [(25, 6), (25, 3), (24, 1), (21, -1)]
    shapes_path = C.shapes.path
    shapes_path(t, pts, lambda x, z: G, 3, m['path'], rng=rng, jitter=0.6)
    for (x, z) in ((20, 1), (29, 2), (18, 3)):
        rubble_at(t, x, ST, z, rng)
    # gate pylons outside the north wall
    for px0 in (20, 28):
        for x in range(px0, px0 + 3):
            for z in (4, 5):
                for y in range(1, 13):
                    t.set(x, y, z, m['plinth'](x, y, z) if y <= ST + 1 else m['wall'](x, y, z))
                t.set(x, 13, z, 'or:ruin_stone_carved')
        t.set(px0 + 1, 14, 4, C.wall_block('or:ruin_brick_wall'))
        t.set(px0 + 1, 14, 5, C.wall_block('or:ruin_brick_wall'))
        hanging_lantern(t, px0 + 1, 10, 3, soul=True, chain=0)
        t.set(px0 + 1, 11, 3, stairs('or:ruin_brick_stairs', 'south', half='top'))
    # trees and bushes
    C.tree(t, 3, ST, 22, kind='oak', height=7, rng=rng, crown=3.2)
    C.tree(t, 47, ST, 30, kind='birch', height=8, rng=rng, crown=2.5)
    C.tree(t, 46, ST, 46, kind='spruce', height=9, rng=rng, crown=2.2)
    C.tree(t, 4, ST, 44, kind='oak', height=6, rng=rng, crown=2.8, lean=(1, 0))
    for (x, z, k) in ((9, 3, 'oak'), (40, 2, 'azalea'), (2, 10, 'oak'), (48, 14, 'birch'), (16, 49, 'oak'), (38, 48, 'azalea')):
        if c.ring(x, z) >= 2:
            C.bush(t, x, ST, z, kind=k, rng=rng)
    # the west wall's fallen courses lie outside it (the collapse leaned south-west)
    for z in range(10, 46):
        for x in range(2, 6):
            if rng.random() < 0.18 * (1 - (6 - x) / 5.0) + (0.12 if 26 <= z <= 33 else 0):
                if C.is_airish(t, x, ST, z):
                    rubble_at(t, x, ST, z, rng)


def finish_court(c):
    """Moss and lichen where water runs, vines on the outer faces, plants, explicit air, light."""
    t, rng = c.t, c.rng
    c.finish_air()
    sky = {}
    for x in range(t.sx):
        for z in range(t.sz):
            for y in range(t.sy - 1, -1, -1):
                if not C.is_airish(t, x, y, z):
                    sky[(x, z)] = y
                    break
    wet = Noise(t.seed + 909, 3.0)
    for (x, y, z), s in list(t.cells.items()):
        n = C.name(s)
        if n not in ('oldrepublic:ruin_bricks', 'oldrepublic:ruin_stone'):
            continue
        if (x, y, z) in c.no_moss:
            continue
        exposed_top = sky.get((x, z)) == y
        low = y <= ST + 1
        north = C.is_airish(t, x, y, z - 1) and y >= ST
        p = 0.0
        if exposed_top:
            p += 0.45
        if low:
            p += 0.25
        if north:
            p += 0.2
        if p and wet.at(x, y, z) * p > 0.22:
            t.set(x, y, z, 'oldrepublic:ruin_stone_mossy')
    nature.overgrow(t, region=((0, ST, 0), (t.sx - 1, t.sy - 1, t.sz - 1)), rng=rng, vines=0.10, moss=0.0,
                    lichen=0.0, roots=0.0, vine_max=4, wall_names=['or:ruin_bricks', 'or:ruin_stone_mossy',
                                                                  'or:ruin_stone', 'or:ruin_stone_cracked'])
    # plants on open ground away from the ramp rings
    c.sprinkle((0, 0, t.sx - 1, t.sz - 1), C.PLANTS_FOREST, 0.22)
    C.fix_vines(t)


def light_court(c):
    t = c.t
    lit = []
    for reg in c.indoor:
        lit += mod.light_fill(t, region=reg, level=8, strength=11)
    return lit


def courtyard_v1(seed=61001):
    c = Court(seed, 1)
    t, m, rng = c.t, c.m, c.rng
    c.ground()
    # enclosure (north z 6..7, west x 6..7, east x 43..44, south z 47..48)
    c.wall(6, 6, 44, 6, lambda i: None if 17 <= i <= 21 else (9 if i < 24 else 7), thick=2, buttress=6, outward=-1,
           cap=False)
    c.wall(6, 6, 6, 48, lambda i: 9 if i < 30 else 8, thick=2, buttress=6, outward=-1, cap=False)
    c.wall(43, 6, 43, 48, c.profile(5, 9, 3, breaches=((9, 25, 2), (32, 36, 4))), thick=2, buttress=7, outward=1)
    c.wall(6, 47, 44, 47, c.profile(6, 9, 4, breaches=((30, 36, 4),)), thick=2, buttress=0)
    for x in range(23, 28):
        for z in (6, 7):
            t.set(x, 9, z, 'or:ruin_stone_carved')
            for y in range(ST, 9):
                t.set(x, y, z, C.AIR)
    # paving: terrace before the facade, the axis path and the cross path
    c.pave(13, 33, 37, 36, loss=0.12)
    c.pave(23, 6, 27, 36, loss=0.08)
    c.pave(13, 25, 37, 27, loss=0.14)
    for x in range(23, 28):
        for z in (6, 7):
            t.set(x, G, z, 'or:ruin_stone_carved' if z == 6 else m['pave'](x, G, z))
    # garden beds, the reflecting pool in the south-west bed
    garden_beds(c, [(13, 13, 22, 24), (28, 13, 37, 24), (13, 28, 22, 32), (28, 28, 37, 32)], pool=(15, 29, 20, 31))
    # cloisters: north and west intact, east collapsed toward the garden (west)
    north_keep = lambda x, z: x < 29 or (x < 33 and c.gaps.at(x, 0, z) > 0.55)
    cloister(c, 8, 8, 36, 12, ('z', 12), [12, 16, 20, 22, 28, 32, 36], roof=(6, 6, 36, 13), keep=north_keep)
    cloister(c, 8, 14, 12, 34, ('x', 12), [16, 20, 24, 28, 32], roof=(6, 14, 13, 34))
    cloister(c, 38, 15, 42, 34, ('x', 38), [31], collapsed=True, fall_dir='south')
    # the light shaft over the hall, the statue on the axis
    light_shaft(c)
    statue(c, AX, 16, facing='south')
    # the old oak in the north-west bed, its roots through the paving
    C.tree(t, 17, ST, 18, kind='oak', height=9, rng=rng, crown=4.2, roots=4)
    for (x, z) in ((19, 20), (20, 21), (21, 22), (15, 21)):
        t.set(x, G, z, 'minecraft:rooted_dirt')
        t.set(x, 2, z, 'minecraft:rooted_dirt')
    for (x, z) in ((30, 30), (35, 31), (29, 29)):
        C.bush(t, x, ST, z, kind='flowering_azalea', rng=rng)
    # benches by the pool
    for x in (15, 17, 19):
        t.set(x, ST, 33, stairs('or:ruin_brick_stairs', 'south'))
    # the tower in the north-east corner, fallen toward the south-west
    tower(c, 41, 10, 4.5, 16, 27, math.pi / 2, door_side='west')
    fallen_tower(c, 41, 16, 16, 4.5)
    # the sanctuary, the side rooms
    sanctuary(c, broken_side='east')
    stand_spot(c)
    scriptorium(c, 7, 36, 16, 47, door_x=10)
    ruined_room(c, 34, 36, 43, 47, fall='west')
    diggers_v1(c)
    outside_v1(c)
    mod.spot(t, 'SURFACE_GUARD', AX, ST, 10)
    finish_court(c)
    light_court(c)
    return c


# ---------------------------------------------------------------- courtyard 2: the slide

SLIDE_CREST = (9, 9)


def slide_height(c, x, z):
    """Cells of earth above the ground top G that the old landslide left at (x, z): highest at the crest
    in the north-west corner, thinning out south-east, never past the axis (x 22) or the ramp rings."""
    d = math.hypot((x - SLIDE_CREST[0]) * 0.9, (z - SLIDE_CREST[1]) * 1.0)
    h = 8.5 - d / 1.8 + (c.slide.at(x, 0, z) - 0.5) * 3.0
    h = min(h, (c.ring(x, z) - 1) * 1.7, (21.5 - x) * 1.6)
    return max(0, int(round(h)))


def boulder(t, x, y, z, r, rng, mat):
    for dx in range(-2, 3):
        for dy in range(0, 3):
            for dz in range(-2, 3):
                if dx * dx + (dy * 1.4) ** 2 + dz * dz <= r * r + rng.random() * 0.6:
                    C.put(t, x + dx, y + dy, z + dz, mat, only_empty=True)


def landslide(c):
    """The slide that came down off the hill to the north-west: earth over the north-west quarter with
    boulders, young birches grown on it, its run-out of stones and broken masonry spread south-east."""
    t, m, rng = c.t, c.m, c.rng
    top_mat = C.mix(t.seed + 61, [(S('grass_block', snowy=False), 14), (S('podzol', snowy=False), 2),
                                  ('minecraft:coarse_dirt', 1), ('minecraft:moss_block', 3)], 4.0)
    under = C.mix(t.seed + 62, [('minecraft:dirt', 6), ('minecraft:rooted_dirt', 2), ('minecraft:coarse_dirt', 2),
                                ('minecraft:gravel', 1), ('minecraft:stone', 1)], 2.5)
    for x in range(t.sx):
        for z in range(t.sz):
            h = slide_height(c, x, z)
            if not h:
                continue
            for y in range(G + 1, G + h + 1):
                t.set(x, y, z, top_mat(x, y, z) if y == G + h else under(x, y, z))
            c.top[(x, z)] = G + h
    stone = C.mix(t.seed + 63, [('minecraft:mossy_cobblestone', 4), ('minecraft:stone', 3), ('minecraft:andesite', 2),
                                ('minecraft:tuff', 2)], 2.0)
    for (x, z, r) in ((5, 13, 1.6), (13, 5, 1.4), (12, 13, 1.2), (20, 15, 1.1), (21, 20, 0.9), (13, 19, 1.0),
                      (7, 20, 1.3), (11, 17, 1.0), (4, 4, 1.8), (17, 3, 1.3), (9, 2, 1.1), (2, 9, 1.2),
                      (8, 15, 0.9), (18, 8, 1.0)):
        boulder(t, x, c.gy(x, z) + 1, z, r, rng, stone)
    # the run-out: loose masonry and stones, thinning toward the court's middle
    for x in range(8, 24):
        for z in range(8, 34):
            if slide_height(c, x, z) or not C.is_airish(t, x, ST, z):
                continue
            d = math.hypot(x - 14, z - 16)
            if rng.random() < 0.20 * max(0.0, 1 - d / 16.0):
                rubble_at(t, x, ST, z, rng)
    C.tree(t, 8, c.gy(8, 6) + 1, 6, kind='birch', height=8, rng=rng, crown=2.6)
    C.tree(t, 4, c.gy(4, 16) + 1, 16, kind='birch', height=7, rng=rng, crown=2.3, lean=(1, 0))
    C.tree(t, 14, c.gy(14, 3) + 1, 3, kind='oak', height=7, rng=rng, crown=3.0)
    C.tree(t, 11, c.gy(11, 11) + 1, 11, kind='birch', height=9, rng=rng, crown=2.5)
    for (x, z) in ((6, 9), (16, 7), (3, 6), (12, 15)):
        C.bush(t, x, c.gy(x, z) + 1, z, kind='oak', rng=rng)


def toppled_wall_west(c, z0, z1, length):
    """The west wall between z0 and z1 pushed over east by the slide: stubs on its footing, the wall lying
    flat on the grass in panels that cracked apart as it fell, its coping at the far end."""
    t, m, rng = c.t, c.m, c.rng
    shift = [0, 1, 0, -1, 1, 0]
    for z in range(z0, z1 + 1):
        p = (z - z0) // 6
        crack = (z - z0) % 6 == 5
        for x in (6, 7):
            for y in range(1, ST + rng.randint(0, 2)):
                t.set(x, y, z, m['plinth'](x, y, z))
        if crack:
            if rng.random() < 0.5:
                rubble_at(t, 9 + rng.randint(0, 4), ST, z, rng)
            continue
        s = shift[p % len(shift)]
        for i in range(length):
            x = 8 + i + s
            t.set(x, ST, z, m['wall'](x, ST, z) if i < length - 1 else 'or:ruin_stone_carved')
            if i < length - 1 and c.n2.at(x, 40, z) > 0.35:
                t.set(x, ST + 1, z, m['wall'](x, ST + 1, z))
        if (z - z0) % 6 == 2:   # a buttress lying across the panel
            for i in range(length - 2):
                t.set(8 + i + s, ST + 1, z, m['plinth'](8 + i + s, ST + 1, z))


def colonnade(c):
    """Two rows of tall columns flanking the axis. The west row took the slide: a stump half buried, one
    shaft lying across the walk in drums, one snapped with its top fallen south; the east row stands with
    its architrave."""
    t, m, rng = c.t, c.m, c.rng
    zs = (11, 15, 19, 23, 27, 31)
    top = ST + 9
    for z in zs:
        column(t, 29, z, ST, top)
        column_feet(t, 29, z, ST)
    for z in range(10, 33):
        if z in (17, 18):        # a gap where a stretch of the architrave came down
            continue
        t.set(29, top + 1, z, S('or:ruin_pillar', axis='z') if z not in zs else 'or:ruin_stone_carved')
        if c.n2.at(29, 50, z) > 0.3:
            t.set(29, top + 2, z, m['wall'](29, top + 2, z))
    for (y, z) in ((ST, 17), (ST, 18), (ST + 1, 17)):
        t.set(30, y, z, S('or:ruin_pillar', axis='z'))
    t.set(28, ST, 18, 'or:ruin_stone_carved')
    # the west row (x 21)
    column(t, 21, 11, ST, top, broken=ST + 1, rng=rng)
    column(t, 21, 15, ST, top, broken=ST, rng=None)
    for x in range(22, 31):                       # the shaft that fell east across the walk, in drums
        if 24 <= x <= 26:
            continue
        t.set(x, ST, 15, S('or:ruin_pillar', axis='x') if x < 30 else 'or:ruin_stone_carved')
    t.set(25, ST, 16, slab('or:ruin_brick_slab'))
    t.set(27, ST, 13, S('or:ruin_pillar', axis='z'))
    column(t, 21, 19, ST, top, broken=ST + 4, rng=None)
    fallen_column(t, 22, 20, ST, 'south', 4, rng)
    column(t, 21, 23, ST, top)
    column(t, 21, 27, ST, top)
    column(t, 21, 31, ST, top, broken=ST + 2, rng=rng)
    for z in (23, 27):
        column_feet(t, 21, z, ST)
    for z in range(22, 29):
        t.set(21, top + 1, z, S('or:ruin_pillar', axis='z') if z not in (23, 27) else 'or:ruin_stone_carved')


def echo_hall(c):
    """The Hall of Echoes along the east wall: tall arched windows to the colonnade, a copper roof caved in
    at its middle, benches facing a dais, banners in the Watch's colours."""
    t, m, rng = c.t, c.m, c.rng
    x0, x1, z0, z1 = 33, 44, 9, 31
    wall_top = 13
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x <= x0 + 1 or x >= x1 - 1 or z <= z0 + 1 or z >= z1 - 1
            if edge:
                for y in range(1, wall_top + 1):
                    st = m['plinth'](x, y, z) if y <= ST + 1 else ('or:ruin_stone_carved' if y == 11 else m['wall'](x, y, z))
                    t.set(x, y, z, st)
            else:
                t.set(x, G, z, m['pave'](x, G, z))
                for y in range(ST, wall_top + 6):
                    t.set(x, y, z, C.AIR)
    # buttresses on the west face, between the windows
    for z in (11, 15, 19, 23, 27, 31):
        for y in range(1, 10):
            t.set(x0 - 1, y, z, m['plinth'](x0 - 1, y, z) if y <= ST + 1 else m['wall'](x0 - 1, y, z))
        t.set(x0 - 1, 10, z, stairs('or:ruin_brick_stairs', 'east'))
    # tall arched windows through the west wall, slits through the east
    for z in (13, 17, 25, 29):
        for y in range(ST + 2, 11):
            for x in (x0, x0 + 1):
                t.set(x, y, z, C.AIR)
        t.set(x0, ST + 1, z, 'or:ruin_stone_carved')
        for x in (x0, x0 + 1):
            t.set(x, 10, z, slab('or:ruin_brick_slab', 'top'))
        for y in range(ST + 3, 9):
            t.set(x1 - 1, y, z, C.AIR)
            t.set(x1, y, z, S('iron_bars', east=False, west=False, north=True, south=True, waterlogged=False))
    # the door from the colonnade (3 wide, 4 high) with a carved frame
    for z in (20, 21, 22):
        for y in range(ST, ST + 4):
            t.set(x0, y, z, C.AIR)
            t.set(x0 + 1, y, z, C.AIR)
        t.set(x0, G, z, 'or:ruin_stone_carved')
        t.set(x0 + 1, G, z, 'or:ruin_stone_carved')
    for y in range(ST, ST + 5):
        t.set(x0 - 1, y, 19, S('or:ruin_pillar', axis='y') if y < ST + 4 else 'or:ruin_stone_carved')
        t.set(x0 - 1, y, 23, S('or:ruin_pillar', axis='y') if y < ST + 4 else 'or:ruin_stone_carved')
    for z in range(19, 24):
        t.set(x0 - 1, ST + 4, z, 'or:ruin_stone_carved')
        t.set(x0 - 1, ST + 5, z, stairs('or:ruin_brick_stairs', 'east'))
    # the roof: a copper gable along z, caved in over the middle
    hole = lambda x, z: not (16 <= z <= 21 and abs(x - 38.5) + c.n2.at(x, 60, z) * 3 < 5.5)
    gable(c, x0, z0, x1, z1, wall_top + 1, 'z', keep=hole)
    # what fell in: copper, a rafter, rubble
    cu = copper_at(t.seed + 47)
    for (x, z) in ((36, 17), (37, 18), (39, 19), (40, 17), (38, 20), (41, 20), (36, 21)):
        t.set(x, ST, z, slab(cu(x, ST, z) + '_slab'))
    for x in range(35, 41):
        t.set(x, ST, 19 if x < 38 else 18, S('stripped_spruce_log', axis='x'))
    for (x, z) in ((37, 16), (40, 21), (35, 19), (42, 18)):
        rubble_at(t, x, ST, z, rng)
    # benches facing the dais at the south end
    for z in (11, 13, 15, 23, 25):
        for x in (35, 36, 37, 40, 41, 42):
            if C.is_airish(t, x, ST, z):
                t.set(x, ST, z, stairs('or:ruin_brick_stairs', 'south'))
    for x in range(35, 43):
        for z in (27, 28, 29):
            t.set(x, ST, z, slab('or:ruin_brick_slab', 'bottom') if z == 27 else 'or:ruin_stone_carved')
    mod.lectern(t, 38, ST + 1, 28, facing='north', book=False)
    for (x, z) in ((36, 29), (41, 29)):
        t.set(x, ST + 1, z, 'or:ruin_stone_carved')
        kyber(t, x, ST + 2, z)
    candle(t, 39, ST + 1, 28, n=3)
    for (x, colour) in ((37, 'cyan'), (40, 'white')):
        mod.banner(t, x, ST + 4, 30, 'north', base=colour)
    for z in (12, 16, 24, 28):
        mod.banner(t, 42, ST + 5, z, 'west', base='cyan' if z % 8 == 4 else 'white')
    for (x, z) in ((38, 12), (38, 25)):
        hanging_lantern(t, x, 10, z, soul=True, chain=3)
    t.set(35, ST + 3, 10, 'minecraft:cobweb')
    t.set(42, ST + 4, 29, 'minecraft:cobweb')
    c.indoor.append((35, ST, 11, 42, wall_top, 29))
    c.no_moss.update((x, y, z) for x in range(x0 + 2, x1 - 1) for y in range(ST, wall_top + 1) for z in range(z0 + 2, z1 - 1))


def well_house(c, x0, z0, x1, z1, door_x):
    """The well house beside the sanctuary: a covered well with its winch, a trough, jars and a bench."""
    t, m, rng = c.t, c.m, c.rng
    apse = lambda x, z: math.hypot(x - APSE[0], z - APSE[1]) <= 10.5 and z >= 39
    room_box(c, x0, z0, x1, z1, lambda x, z: 8)
    cu = copper_at(t.seed + 49)
    hipr(c, x0, z0, x1, z1, 9, cu, hole=(x1 - 2, z1 - 2, 1.6))
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if apse(x, z):
                for y in range(1, 10):
                    t.set(x, y, z, m['wall'](x, y, z))
    for x in (door_x, door_x + 1):
        for y in range(ST, ST + 3):
            t.set(x, y, z0, C.AIR)
        t.set(x, G, z0, 'or:ruin_stone_carved')
    t.set(door_x - 1, ST + 3, z0, 'or:ruin_stone_carved')
    t.set(door_x + 2, ST + 3, z0, 'or:ruin_stone_carved')
    wx, wz = x0 + 5, z0 + 4
    for x in range(wx - 1, wx + 3):
        for z in range(wz - 1, wz + 3):
            inner = wx <= x <= wx + 1 and wz <= z <= wz + 1
            if inner:
                t.set(x, 0, z, 'minecraft:stone')
                for y in range(1, ST):
                    t.set(x, y, z, 'minecraft:water')
                t.set(x, ST, z, C.AIR)
            else:
                t.set(x, 1, z, 'or:ruin_stone')
                t.set(x, 2, z, 'or:ruin_stone')
                t.set(x, G, z, 'or:ruin_stone')
                t.set(x, ST, z, 'or:ruin_stone_carved' if (x + z) % 2 else slab('or:ruin_brick_slab', 'double'))
    for x in (wx - 1, wx + 2):
        t.set(x, ST + 1, wz, S('spruce_fence', waterlogged=False))
        t.set(x, ST + 2, wz, S('spruce_fence', waterlogged=False))
    for x in range(wx - 1, wx + 3):
        t.set(x, ST + 3, wz, S('stripped_spruce_log', axis='x'))
    t.set(wx, ST + 2, wz, S('iron_chain', axis='y', waterlogged=False))
    t.set(wx, ST + 1, wz, S('iron_chain', axis='y', waterlogged=False))
    t.set(x1 - 1, ST, z0 + 1, 'minecraft:water_cauldron[level=2]')
    t.set(x1 - 1, ST, z0 + 2, S('barrel', facing='up', open=False))
    pot(t, x1 - 1, ST, z1 - 1, facing='west')
    pot(t, x1 - 2, ST, z1 - 1, facing='north', cracked=True)
    for z in range(z0 + 3, z0 + 6):
        if C.is_airish(t, x1 - 1, ST, z):
            t.set(x1 - 1, ST, z, stairs('or:ruin_brick_stairs', 'east'))
    hanging_lantern(t, wx + 1, 8, wz - 2, soul=False, chain=0)
    c.indoor.append((x0 + 1, ST, z0 + 1, x1 - 1, 8, z1 - 1))


def tent(t, x0, x1, zc, colour='or:canvas', open_end='x0'):
    """An A-frame canvas tent along x, ridge at zc, open at one end and closed with a panel at the other."""
    for x in range(x0, x1 + 1):
        t.set(x, ST + 2, zc, slab('or:canvas_slab'))
        t.set(x, ST + 1, zc - 1, stairs('or:canvas_stairs', 'south'))
        t.set(x, ST + 1, zc + 1, stairs('or:canvas_stairs', 'north'))
        t.set(x, ST, zc - 2, stairs('or:canvas_stairs', 'south'))
        t.set(x, ST, zc + 2, stairs('or:canvas_stairs', 'north'))
        for z in (zc - 1, zc, zc + 1):
            t.set(x, ST, z, C.AIR)
        t.set(x, ST + 1, zc, C.AIR)
    back = x1 if open_end == 'x0' else x0
    for z in (zc - 1, zc, zc + 1):
        t.set(back, ST, z, colour)
    t.set(back, ST + 1, zc, colour)


def diggers_v2(c):
    """The diggers cut into the slide's flank where a buried wall shows, and run their spoil out on a
    track to a heap and a sieve; a tent by the fallen wall, stores under a tarp against the old columns."""
    t, m, rng = c.t, c.m, c.rng
    x0, x1, z0, z1 = 15, 19, 12, 18
    while any(c.gy(x, z1 + 1) > G for x in range(x0, x1 + 1)):
        z1 += 1          # the cut runs out to where the slide's toe ends
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(ST, ST + 9):
                t.set(x, y, z, C.AIR)
            t.set(x, G, z, 'minecraft:coarse_dirt' if (x + z) % 3 else 'minecraft:gravel')
            c.top.pop((x, z), None)
    # the buried wall they reached, its doorway still choked
    for x in range(x0 - 1, x1 + 2):
        for y in range(1, ST + 4):
            t.set(x, y, z0 - 1, m['wall'](x, y, z0 - 1))
    for y in range(ST, ST + 3):
        t.set(16, y, z0 - 1, 'or:ruin_stone_carved')
        t.set(18, y, z0 - 1, 'or:ruin_stone_carved')
    t.set(17, ST, z0 - 1, 'minecraft:suspicious_gravel')
    t.set(17, ST + 1, z0 - 1, 'minecraft:gravel')
    t.set(17, ST + 2, z0 - 1, C.AIR)
    for x in range(15, 20):
        t.set(x, ST + 3, z0 - 1, 'or:ruin_stone_carved')
    # shoring along the cut's earth faces, frames across it
    for z in range(z0, z1 + 1):
        for x in (x0 - 1, x1 + 1):
            if not C.is_airish(t, x, ST, z):
                h = c.gy(x, z)
                for y in range(ST, max(ST + 1, h + 1)):
                    t.set(x, y, z, 'minecraft:spruce_planks' if z % 3 else S('stripped_spruce_log', axis='y'))
    for z in (z0 + 1, z0 + 4):
        for x in range(x0, x1 + 1):
            t.set(x, ST + 3, z, S('stripped_spruce_log', axis='x'))
        for x in (x0, x1):
            for y in range(ST, ST + 3):
                t.set(x, y, z, S('spruce_fence', waterlogged=False))
    hanging_lantern(t, 17, ST + 2, z0 + 1, soul=False, chain=0)
    t.set(15, ST, z0, 'or:cargo_crate')
    t.set(19, ST, z0 + 2, S('barrel', facing='up', open=True))
    # the track out to the spoil heap
    for z in range(z0, 28):
        t.set(17, ST, z, S('rail', shape='north_south', waterlogged=False))
    for (x, z, h) in ((16, 28, 2), (17, 28, 2), (18, 28, 1), (16, 29, 1), (17, 29, 2), (15, 28, 1), (18, 29, 1),
                      (17, 30, 1)):
        for y in range(ST, ST + h):
            t.set(x, y, z, 'minecraft:gravel' if (x + y) % 2 else 'minecraft:coarse_dirt')
    t.set(16, ST + 1, 28, 'minecraft:suspicious_gravel')
    # the sieve and the finds under a tarp strung from the column line
    for z in range(24, 32):
        t.set(19, ST + 4, z, slab('or:canvas_slab', 'top'))
        t.set(20, ST + 4, z, slab('or:canvas_slab', 'top'))
    for z in (24, 31):
        for y in range(ST, ST + 4):
            t.set(19, y, z, S('spruce_fence', waterlogged=False))
    t.set(19, ST, 27, 'minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]')
    t.set(19, ST + 1, 27, S('spruce_trapdoor', facing='west', half='bottom', open=False, powered=False, waterlogged=False))
    t.set(20, ST, 27, 'minecraft:composter[level=0]')
    t.chest(20, ST, 30, facing='west', loot='chests/jedi_ruin')
    t.anchor('CHEST_1', 20, ST, 30)
    t.set(20, ST, 31, S('barrel', facing='up', open=False))
    t.set(20, ST + 1, 31, 'or:cargo_crate')
    t.set(20, ST, 29, 'or:cargo_crate')
    t.set(20, ST, 25, 'minecraft:cartography_table')
    t.set(20, ST + 1, 25, S('copper_lantern', hanging=False, waterlogged=False))
    pot(t, 20, ST, 26, facing='west')
    pot(t, 19, ST, 25, facing='west', cracked=True)
    # a tent by the fallen wall, the cook fire and seats
    tent(t, 9, 13, 30, colour='or:canvas', open_end='x1')
    t.set(11, ST, 30, 'minecraft:brown_carpet')
    t.set(12, ST, 30, 'minecraft:white_carpet')
    t.set(10, ST, 29, 'minecraft:green_carpet')
    t.set(10, ST, 31, S('barrel', facing='up', open=False))
    t.set(13, ST, 25, S('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
    t.set(12, ST, 23, S('stripped_spruce_log', axis='x'))
    t.set(13, ST, 23, S('stripped_spruce_log', axis='x'))
    t.set(11, ST, 25, 'minecraft:cauldron')
    t.set(15, ST, 26, S('barrel', facing='up', open=True))
    for y in range(ST, ST + 3):
        t.set(15, y, 31, C.AIR)
    mod.spawner(t, 15, ST, 31, 'diggers', anchor='DIGGERS')


def gate_arch(c):
    """The gate: two piers and a lintel spanning the walk, lanterns hung under it."""
    t, m = c.t, c.m
    for px0 in (20, 28):
        for x in range(px0, px0 + 3):
            for z in range(4, 8):
                for y in range(1, 12):
                    t.set(x, y, z, m['plinth'](x, y, z) if y <= ST + 1 else m['wall'](x, y, z))
    for x in range(20, 31):
        for z in range(4, 8):
            t.set(x, 11, z, 'or:ruin_stone_carved' if z in (4, 7) else m['wall'](x, 11, z))
            t.set(x, 12, z, m['wall'](x, 12, z))
        if x % 2 == 0:
            t.set(x, 13, 4, m['wall'](x, 13, 4))
            t.set(x, 13, 7, m['wall'](x, 13, 7))
    for x in range(23, 28):
        for z in range(4, 8):
            for y in range(ST, 11):
                t.set(x, y, z, C.AIR)
        t.set(x, 10, 4, stairs('or:ruin_brick_stairs', 'south', half='top') if x in (23, 27) else C.AIR)
        t.set(x, 10, 7, stairs('or:ruin_brick_stairs', 'north', half='top') if x in (23, 27) else C.AIR)
    hanging_lantern(t, 24, 10, 6, soul=True)
    hanging_lantern(t, 26, 10, 5, soul=True)


def outside_v2(c):
    t, m, rng = c.t, c.m, c.rng
    C.shapes.path(t, [(25, 7), (25, 3), (27, 1), (30, -1)], lambda x, z: G, 3, m['path'], rng=rng, jitter=0.6)
    C.tree(t, 47, ST, 9, kind='spruce', height=9, rng=rng, crown=2.2)
    C.tree(t, 46, ST, 37, kind='oak', height=7, rng=rng, crown=3.0)
    C.tree(t, 2, ST, 30, kind='birch', height=7, rng=rng, crown=2.3)
    C.tree(t, 31, ST, 49, kind='oak', height=6, rng=rng, crown=2.6, lean=(0, -1))
    for (x, z, k) in ((40, 2, 'oak'), (48, 22, 'azalea'), (3, 40, 'oak'), (20, 49, 'azalea'), (47, 46, 'birch')):
        C.bush(t, x, ST, z, kind=k, rng=rng)
    for (x, z) in ((33, 2), (19, 1), (48, 30)):
        rubble_at(t, x, ST, z, rng)


def courtyard_v2(seed=61002):
    """Watchers' Rest after the slide: the hill came down on the north-west, buried that corner, laid the
    west wall flat and threw the west colonnade across the walk; the east side stands."""
    c = Court(seed, 2)
    c.slide = Noise(seed + 505, 4.0)
    t, m, rng = c.t, c.m, c.rng
    c.ground()
    # enclosure: north wall east of the gate, east wall (part of the hall), south wall, west stubs
    c.wall(28, 6, 44, 6, lambda i: 8, thick=2, buttress=6, outward=-1, cap=True)
    c.wall(6, 6, 22, 6, lambda i: 5 if i > 10 else 4, thick=2, buttress=0, cap=False)
    c.wall(43, 6, 43, 48, c.profile(7, 9, 5, breaches=((33, 38, 5),)), thick=2, buttress=7, outward=1)
    c.wall(6, 47, 44, 47, c.profile(6, 9, 6, breaches=((20, 24, 5),)), thick=2, buttress=0)
    c.wall(6, 27, 6, 48, lambda i: 8, thick=2, buttress=5, outward=-1)
    toppled_wall_west(c, 8, 26, 7)
    # paving: walk, terrace, the colonnade aisle, the approach to the hall
    c.pave(23, 6, 27, 36, loss=0.10)
    c.pave(13, 33, 37, 36, loss=0.14)
    c.pave(20, 9, 30, 32, loss=0.30)
    c.pave(28, 19, 32, 23, loss=0.12)
    c.pave(36, 32, 42, 37, loss=0.25)
    light_shaft(c)
    colonnade(c)
    echo_hall(c)
    gate_arch(c)
    landslide(c)
    sanctuary(c, broken_side='west')
    stand_spot(c)
    tower(c, 10, 42, 4.0, 17, 26, -math.pi / 4, door_side='west')
    well_house(c, 34, 37, 43, 46, door_x=38)
    diggers_v2(c)
    outside_v2(c)
    # the south-east bed: a pair of old trees and a stone bench under them
    C.tree(t, 33, ST, 34, kind='oak', height=6, rng=rng, crown=2.8)
    mod.spot(t, 'SURFACE_GUARD', AX, ST, 10)
    finish_court(c)
    light_court(c)
    return c


# ---------------------------------------------------------------- the stair

def stair_piece(seed=61010):
    """The switchback descent: flight A comes down from the sanctuary's well (x 6..8), a landing with a
    kneeling figure, flight B returns north (x 2..4) to the corridor and the hall door (exit jigsaw)."""
    t = C.Template(11, 12, 14, seed=seed, name='jedi_ruin/stair')
    rng = t.rng
    wall = C.mix(seed + 1, [('or:ruin_bricks', 8), ('or:ruin_stone', 3), ('or:ruin_stone_cracked', 2),
                            ('or:ruin_stone_mossy', 2)], 3.0)
    t.fill(0, 0, 0, 10, 11, 13, wall)
    # flight A: steps k = 3..7 of the descent (courtyard z 42..46), y = 14 - k, facing north
    for k in range(3, 8):
        z = k + 2
        y = 14 - k
        for x in range(6, 9):
            t.set(x, y, z, stairs('or:ruin_brick_stairs', 'north'))
            for yy in range(y + 1, 12):
                t.set(x, yy, z, C.AIR)
    # landing (z 10..12) at stand y 7
    for x in range(2, 9):
        for z in range(10, 13):
            t.set(x, 6, z, C.mix(seed + 2, [('or:ruin_stone_carved', 2), ('or:ruin_stone', 3)], 1.5)(x, 6, z))
            for y in range(7, 11):
                t.set(x, y, z, C.AIR)
    # flight B: m = 0..5 at z 9..4, y = 5 - m, facing south (rising toward the landing)
    for m in range(0, 6):
        z = 9 - m
        y = 5 - m
        for x in range(2, 5):
            t.set(x, y, z, stairs('or:ruin_brick_stairs', 'south'))
            for yy in range(y + 1, min(11, y + 6)):
                t.set(x, yy, z, C.AIR)
        # a stepped vault over the flight: upside-down stairs along the top edge
        top = min(11, y + 6)
        for x in (2, 4):
            if top < 11:
                t.set(x, top - 1, z, stairs('or:ruin_brick_stairs', 'east' if x == 2 else 'west', half='top'))
    # corridor to the hall door: x 2..4, z 0..3, floor y 0, 4 high
    for x in range(2, 5):
        for z in range(0, 4):
            t.set(x, 0, z, 'or:ruin_stone_carved' if z == 0 else wall(x, 0, z))
            for y in range(1, 5):
                t.set(x, y, z, C.AIR)
    for z in range(0, 4):
        t.set(2, 4, z, stairs('or:ruin_brick_stairs', 'east', half='top'))
        t.set(4, 4, z, stairs('or:ruin_brick_stairs', 'west', half='top'))
    # the door frame at the corridor's mouth
    for y in range(1, 5):
        t.set(1, y, 0, S('or:ruin_pillar', axis='y'))
        t.set(5, y, 0, S('or:ruin_pillar', axis='y'))
    # the spine between the flights (x 5) with niches of kyber light; arches over the landing
    for (z, y) in ((6, 6), (8, 8)):
        t.set(5, y, z, C.AIR)
        kyber(t, 5, y, z, 'up')
        t.set(5, y + 1, z, stairs('or:ruin_brick_stairs', 'north', half='top'))
    for x in range(2, 9):
        t.set(x, 10, 10, stairs('or:ruin_brick_stairs', 'south', half='top'))
    # the kneeling figure on the landing against the south wall, lanterns either side
    fx, fz = 5, 12
    t.set(fx, 7, fz, 'or:ruin_stone_carved')
    t.set(fx, 8, fz, 'or:ruin_stone_mossy')
    t.set(fx, 9, fz, 'minecraft:smooth_stone')
    t.set(fx, 10, fz, slab('or:ruin_brick_slab'))
    t.set(fx - 1, 8, fz, stairs('or:ruin_brick_stairs', 'east', half='top'))
    t.set(fx + 1, 8, fz, stairs('or:ruin_brick_stairs', 'west', half='top'))
    t.set(fx, 8, fz - 1, stairs('or:ruin_brick_stairs', 'south'))
    for x in (3, 7):
        t.set(x, 7, 12, S('soul_lantern', hanging=False, waterlogged=False))
    # plain moss: lumen moss is the ruin's counted resource (10 to 14 per ruin, spec 3.2)
    for (x, z) in ((2, 10), (8, 12), (6, 11), (2, 12)):
        t.set(x, 7, z, 'minecraft:moss_carpet')
    # roots and webs where the garden's water seeps in
    for (x, y, z) in ((3, 10, 7), (7, 10, 11), (2, 10, 11), (8, 11, 8)):
        if C.is_airish(t, x, y, z) and not C.is_airish(t, x, y + 1, z):
            t.set(x, y, z, S('hanging_roots', waterlogged=False))
    t.set(8, 10, 12, 'minecraft:cobweb')
    hanging_lantern(t, 3, 4, 2, soul=True)
    # one cracked step: a slab where a stair broke (still walkable)
    t.set(3, 3, 7, slab('or:ruin_brick_slab'))
    # the jigsaws
    t.jigsaw(7, 11, 6, 'up_south', name='stair_top', target='courtyard_stair', pool='minecraft:empty', joint='aligned')
    t.jigsaw(3, 1, 0, 'north_up', name='stair_exit', target='hall_entry', pool='jedi_ruin/underground')
    mod.light_fill(t, region=(1, 1, 0, 9, 11, 13), level=8, strength=11)
    return t


# ---------------------------------------------------------------- the Listening Hall (mechanism exact)

MECH = ('minecraft:redstone_wire', 'minecraft:repeater', 'oldrepublic:step_stone', 'oldrepublic:counterweight_stone',
        'oldrepublic:veiled_stone', 'oldrepublic:holocron_pedestal', 'minecraft:jigsaw', 'oldrepublic:site_spawner',
        'minecraft:chest')
# Full, solid, redstone-conducting blocks a frozen ruin_stone cell may become (the circuit cannot tell).
SWAP_OK = set(RUIN_FULL) | {'minecraft:moss_block', 'minecraft:grass_block', 'minecraft:rooted_dirt'}
HALL_TEST_CELLS = [(7, 3, 20), (19, 3, 13), (14, 3, 19), (5, 3, 13), (5, 3, 5), (19, 3, 18), (12, 3, 14)]


def restore_frozen(t, snap, swap_ok=SWAP_OK, swappable=('oldrepublic:ruin_stone',)):
    """Put every frozen cell back, except a full conductor that only became another full conductor."""
    put_back = []
    for p, (s, nb) in snap.items():
        cur = t.cells.get(p)
        if cur == s and t.nbt.get(p) == nb:
            continue
        if s is not None and C.name(s) in swappable and cur is not None and C.name(cur) in swap_ok and '[' not in cur:
            continue
        put_back.append(p)
        C.restore(t, {p: (s, nb)})
    return put_back


def listening_hall(seed=61020):
    t = C.load_base('jedi_ruin/listening_hall', seed)
    rng = t.rng
    frozen = C.neighbourhood(t, MECH, 1)
    snap = C.snapshot(t, frozen)
    mech_before = {p: (s, t.nbt.get(p)) for p, s in t.cells.items() if C.name(s) in MECH}
    air0 = {p for p, s in t.cells.items() if C.name(s) == 'minecraft:air'}
    interior = {(x, y, z) for (x, y, z) in air0 if 3 <= x <= 21 and 5 <= z <= 21 and 3 <= y <= 11}
    wall = C.mix(seed + 1, [('or:ruin_bricks', 8), ('or:ruin_stone', 3), ('or:ruin_stone_cracked', 2),
                            ('or:ruin_stone_mossy', 1)], 3.0)
    plinth = C.mix(seed + 2, [('or:ruin_stone_mossy', 3), ('or:ruin_stone', 3), ('or:ruin_stone_cracked', 2)], 2.5)
    field = C.mix(seed + 3, [('or:ruin_bricks', 5), ('or:ruin_stone', 3), ('or:ruin_stone_cracked', 1)], 2.0)
    ceiling = C.mix(seed + 4, [('or:ruin_bricks', 6), ('or:ruin_stone', 3), ('or:ruin_stone_mossy', 1)], 3.0)

    def face(p):
        x, y, z = p
        return any((x + dx, y + dy, z + dz) in interior for dx, dy, dz in
                   ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)))

    # 1. re-skin every visible ruin_stone face by its role
    for p, s in list(t.cells.items()):
        if C.name(s) != 'oldrepublic:ruin_stone' or not face(p):
            continue
        x, y, z = p
        if (x, y + 1, z) in interior and y == 2:
            st = 'or:ruin_stone_carved' if x in (3, 21) or z in (5, 21) or x == 12 else field(x, y, z)
        elif (x, y - 1, z) in interior and y >= 12:
            st = ceiling(x, y, z)
        elif y == 3:
            st = plinth(x, y, z)
        else:
            st = wall(x, y, z)
        t.set(x, y, z, st)
    # 2. the oculus: a shaft from the garden above (courtyard x 24..26, z 24..28) onto a planted bed
    for x in range(8, 13):
        for z in range(11, 18):
            inner = 9 <= x <= 11 and 12 <= z <= 16
            if inner:
                t.set(x, 12, z, C.AIR)
                t.set(x, 13, z, C.AIR)
            else:
                t.set(x, 12, z, 'or:ruin_stone_carved')
    bed = C.mix(seed + 5, [('minecraft:moss_block', 4), (S('grass_block', snowy=False), 3), ('minecraft:rooted_dirt', 1)], 1.6)
    for x in range(9, 12):
        for z in range(12, 17):
            t.set(x, 2, z, bed(x, 2, z))
    for (x, z, st) in ((9, 12, 'minecraft:fern'), (10, 13, 'minecraft:short_grass'), (9, 15, 'minecraft:lily_of_the_valley'),
                       (10, 16, 'minecraft:fern'), (11, 12, 'minecraft:short_grass'), (9, 14, 'minecraft:moss_carpet'),
                       (11, 16, 'minecraft:short_grass'), (10, 14, 'minecraft:azalea')):
        t.set(x, 3, z, st)
    # 3. lamps and ornament that the old hall had are replaced
    for p, s in list(t.cells.items()):
        if C.name(s) in ('minecraft:lantern',):
            t.set(*p, C.AIR)
    # 4. pilasters along the walls (base, shaft, capital), clear of the routes and the frozen cells
    pil = [(3, 9), (3, 16), (3, 20), (21, 10), (21, 16), (21, 20), (9, 21), (17, 21), (20, 21), (4, 21)]
    for (x, z) in pil:
        cells = [(x, y, z) for y in range(3, 12)]
        if any(c in frozen for c in cells):
            continue
        for y in range(3, 12):
            t.set(x, y, z, 'or:ruin_stone_carved' if y in (3, 11) else S('or:ruin_pillar', axis='y'))
    # 5. ribs across the vault and soul lanterns hanging from them; a cornice round the walls
    for zr in (10, 18):
        for x in range(3, 22):
            if (x, 11, zr) not in frozen:
                t.set(x, 11, zr, S('or:ruin_pillar', axis='x'))
    for (x, z) in ((6, 10), (18, 10), (6, 18), (18, 18), (12, 18)):
        hanging_lantern(t, x, 9, z, soul=True, chain=1)
    for z in range(8, 22):
        for (x, f) in ((3, 'west'), (21, 'east')):
            if C.is_airish(t, x, 11, z) and (x, 11, z) not in frozen:
                t.set(x, 11, z, stairs('or:ruin_brick_stairs', f, half='top'))
    for x in range(4, 21):
        if C.is_airish(t, x, 11, 21) and (x, 11, 21) not in frozen:
            t.set(x, 11, 21, stairs('or:ruin_brick_stairs', 'south', half='top'))
    # 6. the entry widened to three wide and three high, framed in carved stone
    for x in range(5, 8):
        for z in range(22, 25):
            for y in range(3, 6):
                if (x, y, z) not in frozen or C.is_airish(t, x, y, z):
                    t.set(x, y, z, C.AIR)
    for y in range(3, 7):
        for x in (4, 8):
            t.set(x, y, 22, 'or:ruin_stone_carved')
    for x in range(4, 9):
        t.set(x, 6, 22, 'or:ruin_stone_carved')
    # 7. the gallery: a balustrade on its edge, a blind arcade with kyber niches under it
    for x in range(13, 22):
        if (x, 10, 7) not in frozen and C.is_airish(t, x, 10, 7):
            t.set(x, 10, 7, C.wall_block('or:ruin_brick_wall'))
    for x0 in (14, 18):
        for x in (x0, x0 + 1):
            for y in range(3, 6):
                if (x, y, 7) not in frozen:
                    t.set(x, y, 7, C.AIR)
            t.set(x, 6, 7, 'or:ruin_stone_carved')
        kyber(t, x0, 3, 7, 'up')
        t.set(x0 + 1, 3, 7, 'or:lumen_moss')
    for x in (13, 16, 17, 20, 21):
        if (x, 3, 7) not in frozen:
            for y in range(3, 9):
                t.set(x, y, 7, 'or:ruin_stone_carved' if y in (3, 8) else S('or:ruin_pillar', axis='y'))
    # 8. benches, braziers, kyber in the corners, mats round the shaft
    for x in list(range(9, 13)) + list(range(16, 20)):
        if C.is_airish(t, x, 3, 21) and (x, 3, 21) not in frozen:
            t.set(x, 3, 21, stairs('or:ruin_brick_stairs', 'south'))
    for z in (11, 12):
        if (21, 3, z) not in frozen:
            t.set(21, 3, z, stairs('or:ruin_brick_stairs', 'east'))
    for (x, z) in ((4, 20), (20, 20)):
        t.set(x, 3, z, S('soul_campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
    for (x, y, z, f) in ((3, 3, 21, 'up'), (21, 3, 21, 'up'), (3, 6, 12, 'east'), (21, 6, 15, 'west')):
        if (x, y, z) not in frozen and C.is_airish(t, x, y, z):
            kyber(t, x, y, z, f)
    for (x, z) in ((7, 13), (7, 15), (14, 12), (14, 16)):
        t.set(x, 3, z, 'minecraft:cyan_carpet')
    # plain moss here: the hall keeps its 7 patches of lumen moss and the alcoves add 2 (the ruin's 10 to 14)
    for (x, y, z) in ((20, 3, 9), (4, 3, 18), (16, 3, 20), (10, 3, 8)):
        if C.is_airish(t, x, y, z) and (x, y, z) not in frozen:
            t.set(x, y, z, 'minecraft:moss_carpet')
    # 9. put the frozen cells back (a ruin_stone may stay a different full conductor)
    restore_frozen(t, snap)
    for p, (s, nb) in mech_before.items():
        assert t.cells.get(p) == s and t.nbt.get(p) == nb, ('mechanism moved', p)
    mod.light_fill(t, region=(3, 3, 5, 21, 11, 21), level=8, strength=11)
    restore_frozen(t, snap)
    return t


def pilgrim_niche(seed=61030):
    """The Wayfarer's niche behind the veiled panel: a pilgrims' rest with the pedestal in a lit alcove.
    9 x 7 x 7; the jigsaw (0, 1, 3) and the stand (3, 1, 3) stay where the old niche had them."""
    t = C.Template(9, 7, 7, seed=seed, name='jedi_ruin/pilgrim_niche')
    wall = C.mix(seed + 1, [('or:ruin_bricks', 6), ('or:ruin_stone', 3), ('or:ruin_stone_mossy', 2)], 2.5)
    t.fill(0, 0, 0, 8, 6, 6, wall)
    for x in range(1, 8):
        for z in range(1, 6):
            t.set(x, 0, z, 'or:ruin_stone_carved' if (x + z) % 2 == 0 else 'or:ruin_stone')
            for y in range(1, 5):
                t.set(x, y, z, C.AIR)
    for y in range(1, 3):
        t.set(0, y, 3, C.AIR)
    # vault: upside-down stairs along the long walls
    for x in range(1, 8):
        t.set(x, 4, 1, stairs('or:ruin_brick_stairs', 'north', half='top'))
        t.set(x, 4, 5, stairs('or:ruin_brick_stairs', 'south', half='top'))
    # the pedestal's alcove at the east end
    t.set(7, 1, 3, C.AIR)
    mod.pedestal(t, 6, 1, 3, 'wayfarer', always_ready=True, anchor='PEDESTAL_WAYFARER')
    t.set(7, 1, 2, 'or:ruin_stone_carved')
    t.set(7, 1, 4, 'or:ruin_stone_carved')
    standing_lantern(t, 7, 2, 2, soul=False)
    standing_lantern(t, 7, 2, 4, soul=False)
    # a bedroll, a pack, a basin of water, offerings of candles and flowers
    t.set(2, 1, 1, 'minecraft:brown_carpet')
    t.set(3, 1, 1, 'minecraft:brown_carpet')
    t.set(4, 1, 1, 'minecraft:white_carpet')
    t.set(1, 1, 1, S('barrel', facing='up', open=False))
    t.set(1, 1, 5, S('water_cauldron', level=3))
    candle(t, 5, 1, 5, n=3)
    t.set(4, 1, 5, S('potted_lily_of_the_valley'))
    t.set(6, 1, 5, 'or:lumen_moss')
    t.set(6, 1, 1, 'or:lumen_moss')
    t.jigsaw(0, 1, 3, 'west_up', name='niche_entry', target='hall_niche', pool='minecraft:empty')
    mod.light_fill(t, region=(1, 1, 1, 7, 4, 5), level=8, strength=11)
    return t


def anchor_cache(seed=61040):
    """The storeroom west of the hall: shelves and crates the Watch left, a fallen corner, and the cache
    as the old one had it (spec: a pull_anchor above a puzzle_seal in the west wall guards
    chests/force_cache): the chest in a niche of the west wall behind the seal, the anchor over the seal,
    straight ahead of the door so a Pull from the passage reaches it (its release powers the seal under
    it). The open seal has no shape: the chest is opened through it from the cells beside it, (1, 1, 3)
    and (1, 1, 5), which the old room walled in. 9 x 6 x 9; the jigsaw on the east face (8, 1, 4)."""
    t = C.Template(9, 6, 9, seed=seed, name='jedi_ruin/anchor_cache')
    rng = t.rng
    wall = C.mix(seed + 1, [('or:ruin_bricks', 6), ('or:ruin_stone', 3), ('or:ruin_stone_cracked', 2)], 2.5)
    t.fill(0, 0, 0, 8, 5, 8, wall)
    for x in range(1, 8):
        for z in range(1, 8):
            t.set(x, 0, z, C.mix(seed + 2, [('or:ruin_stone', 3), ('or:ruin_bricks', 2)], 1.5)(x, 0, z))
            for y in range(1, 4):
                t.set(x, y, z, C.AIR)
    for y in range(1, 3):
        t.set(8, y, 4, C.AIR)
    # the cache: chest in the wall niche (air over it for the lid), the seal in front, the anchor on it
    t.chest(0, 1, 4, facing='east', loot='chests/force_cache')
    t.anchor('CACHE', 0, 1, 4)
    t.set(0, 2, 4, C.AIR)
    t.set(1, 1, 4, S('or:puzzle_seal', skin='ruin', open=False, age=0))
    t.set(1, 2, 4, S('or:pull_anchor', strain=0, age=0))
    t.set(1, 3, 4, 'or:ruin_stone_carved')
    for z in (1, 2, 6, 7):
        for y in (1, 2):
            t.set(1, y, z, 'minecraft:bookshelf' if (y + z) % 2 else S('barrel', facing='east', open=False))
    for x in (3, 4, 5):
        t.set(x, 1, 7, 'or:cargo_crate' if x != 4 else S('barrel', facing='up', open=False))
    t.set(3, 2, 7, S('barrel', facing='up', open=False))
    pot(t, 6, 1, 1, cracked=False)
    pot(t, 5, 1, 1, cracked=True)
    # the fallen north-west corner: rubble heaped where the vault gave way
    for (x, z) in ((2, 1), (3, 1), (2, 2), (4, 1)):
        rubble_at(t, x, 1, z, rng)
    t.set(2, 3, 1, 'or:ruin_stone_cracked')
    hanging_lantern(t, 4, 3, 4, soul=True)
    t.set(7, 3, 7, 'minecraft:cobweb')
    t.jigsaw(8, 1, 4, 'east_up', name='cache_entry', target='hall_cache', pool='minecraft:empty')
    mod.light_fill(t, region=(1, 1, 1, 7, 3, 7), level=8, strength=11)
    return t


# ---------------------------------------------------------------- checks and main

def hall_checks(t):
    probs = C.route_problems(t, (7, 3, 20), HALL_TEST_CELLS, label='hall')
    probs += C.route_problems(t, (12, 10, 5), [(17, 10, 6), (20, 10, 6)], label='gallery')
    return probs


def court_checks(c):
    t = c.t
    open_door = [(x, y, DOOR_Z) for x in (24, 25, 26) for y in (ST, ST + 1, ST + 2)]
    extra = [(35, ST, 9)] if c.v == 1 else [(41, ST, 22), (37, ST, 39), (17, ST, 14), (15, ST, 32)]
    probs = C.route_problems(t, (AX, ST, 9), [(AX, ST, 35), (AX, ST, 37), (AX, ST, 1)] + extra,
                             label='courtyard')
    # (the last step, (AX, 0, 42), stands on the stair piece below: structure_check walks it composed)
    probs += C.route_problems(t, (AX, ST, 35), [(AX, 3, 39), (AX, 1, 41)], passable=open_door,
                              label='stairwell')
    return probs


def build(a):
    out = {}
    if C.wanted(a, 'courtyard'):
        c = courtyard_v1()
        for p in court_checks(c):
            print('CHECK', p)
        out['jedi_ruin/courtyard'] = C.save(c.t, 'jedi_ruin/courtyard')
    if C.wanted(a, 'courtyard_2'):
        c = courtyard_v2()
        for p in court_checks(c):
            print('CHECK', p)
        out['jedi_ruin/courtyard_2'] = C.save(c.t, 'jedi_ruin/courtyard_2')
    if C.wanted(a, 'stair'):
        t = stair_piece()
        top = (7, 11, 6)
        r = C.route_problems(t, top, [(3, 1, 0), (5, 7, 10)], label='stair')
        for p in r:
            print('CHECK', p)
        out['jedi_ruin/stair'] = C.save(t, 'jedi_ruin/stair')
    if C.wanted(a, 'listening_hall'):
        t = listening_hall()
        for p in hall_checks(t):
            print('CHECK', p)
        out['jedi_ruin/listening_hall'] = C.save(t, 'jedi_ruin/listening_hall')
    if C.wanted(a, 'pilgrim_niche'):
        out['jedi_ruin/pilgrim_niche'] = C.save(pilgrim_niche(), 'jedi_ruin/pilgrim_niche')
    if C.wanted(a, 'anchor_cache'):
        out['jedi_ruin/anchor_cache'] = C.save(anchor_cache(), 'jedi_ruin/anchor_cache')
    for k, t in out.items():
        print(k, t.size, t.count())
    return out


def main():
    build(C.args())


def structure_check():
    """Assemble the structure as worldgen would and walk the whole route: gate, open veil, stair, hall."""
    from structlib import jigsaw as JG
    args = JG.structure_args('oldrepublic:jedi_ruin')
    res = JG.assemble(**args)
    print(res.report())
    for start, rot, pieces in res.runs:
        if rot != 'none':
            continue
        temps = {p.template.split(':', 1)[1]: C.load_saved(p.template.split(':', 1)[1]) for p in pieces}
        big, off = C.compose(pieces, temps)
        org = {p.template.split(':', 1)[1]: p.box[0] for p in pieces}

        def w(tid, cell):
            o = org[tid]
            return (o[0] + cell[0] + off[0], o[1] + cell[1] + off[1], o[2] + cell[2] + off[2])
        veil = [w(start.split(':', 1)[1], (x, y, DOOR_Z)) for x in (24, 25, 26) for y in (ST, ST + 1, ST + 2)]
        goals = [w('jedi_ruin/listening_hall', c) for c in HALL_TEST_CELLS]
        # the cache: the front of its seal (the Pull's stand) and the cells beside the seal, from which the
        # chest is reached through the open seal (the anchor over the seal leaves no headroom in its cell)
        goals += [w('jedi_ruin/anchor_cache', c) for c in ((2, 1, 4), (1, 1, 3), (1, 1, 5))]
        # the pilgrim's niche behind the hall's veiled panel: PlaythroughTest's stand and the pedestal's front
        goals += [w('jedi_ruin/pilgrim_niche', (3, 1, 3)), w('jedi_ruin/pilgrim_niche', (5, 1, 3))]
        opened = veil + [w('jedi_ruin/listening_hall', (22, y, 18)) for y in (3, 4)]
        probs = C.route_problems(big, w(start.split(':', 1)[1], (AX, ST, 35)), goals, passable=opened,
                                 label=start + ' route')
        for p in probs:
            print('CHECK', p)
        if not probs:
            print(start, 'route ok: doorway -> stair -> hall -> cache (beside its seal) and niche (panel open)')
    return res


if __name__ == '__main__':
    main()
