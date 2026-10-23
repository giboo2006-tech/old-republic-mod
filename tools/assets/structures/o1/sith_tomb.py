"""The Sith tomb (worker O1): the sunken gate and its second variant, the path, the Imperial dig camp,
the processional and the burial chamber (re-skinned, mechanism exact), the pilgrim niche and the rubble
room.

A tomb that breathes: black stone, red light welling up through floor grates, soul fire burning cold at
the doors, frost on the threshold in the desert heat.

    python3 tools/assets/structures/o1/sith_tomb.py [--only gate] [--preview DIR]
"""
import math

import common as C
from common import S, mod, Noise, stairs, slab

GS = (41, 30, 41)
B = 6            # buried: the stand is local y 6
G = B - 1        # ground top
ST = B
CX = 20          # the gate's north-south axis
PIT = (12, 14, 28, 32)   # the forecourt's floor (x0, z0, x1, z1)
PIT_ST = 2               # the forecourt's stand level
JIG_DOWN = (20, 1, 40)   # to the processional (its entry corridor's feet are at gate y 1)
JIG_PATH = (0, ST, 5)
DIRS4 = ('north', 'south', 'east', 'west')
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def mats(seed):
    m = {}
    m['tomb'] = C.mix(seed + 1, [('or:tomb_bricks', 9), ('or:tomb_stone', 3), ('or:tomb_stone_cracked', 2)], 3.0)
    m['rough'] = C.mix(seed + 2, [('or:tomb_stone', 5), ('or:tomb_stone_cracked', 3), ('or:tomb_bricks', 2)], 2.5)
    m['pave'] = C.mix(seed + 3, [('or:tomb_stone', 5), ('or:tomb_bricks', 3), ('or:tomb_stone_cracked', 2),
                                 ('minecraft:polished_blackstone', 1)], 2.5)
    m['sand'] = C.mix(seed + 4, [('minecraft:sand', 14), ('minecraft:coarse_dirt', 2), ('minecraft:gravel', 1),
                                 ('minecraft:red_sand', 3), ('minecraft:smooth_sandstone', 1)], 5.0)
    m['sub'] = C.mix(seed + 5, [('minecraft:sand', 5), ('minecraft:sandstone', 4), ('minecraft:smooth_sandstone', 1)], 3.0)
    m['deep'] = C.mix(seed + 6, [('minecraft:sandstone', 4), ('minecraft:stone', 2), ('minecraft:gravel', 1)], 3.0)
    m['rock'] = C.mix(seed + 7, [('minecraft:sandstone', 5), ('minecraft:smooth_sandstone', 2), ('minecraft:cut_sandstone', 1),
                                 ('minecraft:terracotta', 1)], 2.0)
    return m


# ---------------------------------------------------------------- small pieces

def urn(t, x, y, z):
    """A funerary urn: the tomb's counted resource (8 to 10 on the main route, spec 3.2: the processional's
    five and the burial chamber's three, as before). Every other urn-shaped thing is a decorated pot."""
    t.set(x, y, z, 'or:funerary_urn')


def pot(t, x, y, z, facing='north', cracked=False):
    t.set(x, y, z, S('decorated_pot', facing=facing, cracked=cracked, waterlogged=False),
          nbt={'id': 'minecraft:decorated_pot'})


def red_candle(t, x, y, z, n=3):
    t.set(x, y, z, S('red_candle', candles=n, lit=True, waterlogged=False))


def soul_brazier(t, x, y, z):
    """A carved block with cold soul fire on it."""
    t.set(x, y, z, 'or:tomb_stone_carved')
    t.set(x, y + 1, z, S('soul_campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))


def underglow(t, cells, y_floor):
    """Red light welling up: a grate in the floor with a red light under it."""
    for (x, z) in cells:
        t.set(x, y_floor, z, 'or:durasteel_grate')
        t.set(x, y_floor - 1, z, 'or:light_panel_red')


def frost(t, cells, y, rng, p=0.45):
    """Thin frost on full blocks where the tomb breathes out (snow layers keep in the desert)."""
    for (x, z) in cells:
        if rng.random() < p and C.is_airish(t, x, y, z):
            below = C.name(t.get(x, y - 1, z)) or ''
            if below in ('oldrepublic:tomb_stone', 'oldrepublic:tomb_bricks', 'oldrepublic:tomb_stone_cracked',
                         'minecraft:polished_blackstone', 'oldrepublic:tomb_stone_carved'):
                t.set(x, y, z, S('snow', layers=1))


def rubble(t, x, y, z, rng):
    r = rng.random()
    if r < 0.35:
        t.set(x, y, z, slab('or:tomb_brick_slab'))
    elif r < 0.5:
        t.set(x, y, z, stairs('or:tomb_brick_stairs', rng.choice(DIRS4)))
    elif r < 0.65:
        t.set(x, y, z, 'or:debris')
    else:
        t.set(x, y, z, rng.choice(['or:tomb_stone', 'or:tomb_stone_cracked', 'or:tomb_bricks']))


def vec_dir(dx, dz):
    if dx > 0:
        return 'east'
    if dx < 0:
        return 'west'
    return 'south' if dz > 0 else 'north'


def guardian(t, x, z, y0, facing, m):
    """A colossal hooded Sith guardian on a plinth, a sword held point-down before it (plinth 3 x 3 at
    y0..y0+1, figure 9 high)."""
    f = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[facing]
    side = (-f[1], f[0])
    body = C.mix(t.seed + 77, [('or:tomb_bricks', 3), ('or:tomb_stone', 3), ('or:tomb_stone_cracked', 1)], 2.0)

    def at(a, b):   # a: along the facing, b: sideways
        return (x + f[0] * a + side[0] * b, z + f[1] * a + side[1] * b)

    def inward(b):  # the direction from a cell at sideways offset b back toward the body
        return vec_dir(-side[0] * b, -side[1] * b)
    for a in (-1, 0, 1):
        for b in (-1, 0, 1):
            px, pz = at(a, b)
            t.set(px, y0, pz, 'or:tomb_stone_carved')
            t.set(px, y0 + 1, pz, m['tomb'](px, y0 + 1, pz))
    y = y0 + 2
    for dy in range(0, 4):            # the robe, fuller behind
        for b in (-1, 0, 1):
            px, pz = at(0, b)
            t.set(px, y + dy, pz, body(px, y + dy, pz))
            if dy < 3 or b == 0:
                px, pz = at(-1, b)
                t.set(px, y + dy, pz, body(px, y + dy, pz))
    for b in (-1, 1):                 # the hem spreading forward
        px, pz = at(1, b)
        t.set(px, y, pz, stairs('or:tomb_brick_stairs', OPP[facing]))
    sx, sz = at(1, 0)                 # the sword, point down, hands on the pommel
    t.set(sx, y, sz, 'or:tomb_stone_carved')
    for dy in range(1, 4):
        t.set(sx, y + dy, sz, S('or:tomb_pillar', axis='y'))
    t.set(sx, y + 4, sz, 'or:tomb_stone_carved')
    t.set(sx, y + 5, sz, stairs('or:tomb_brick_stairs', OPP[facing], half='top'))
    for dy in (4, 5):                 # chest and shoulders
        for b in (-1, 0, 1):
            px, pz = at(0, b)
            t.set(px, y + dy, pz, body(px, y + dy, pz))
    for b in (-2, 2):
        px, pz = at(0, b)
        t.set(px, y + 5, pz, stairs('or:tomb_brick_stairs', inward(b), half='top'))
        px, pz = at(0, b)
        t.set(px, y + 4, pz, stairs('or:tomb_brick_stairs', inward(b), half='bottom'))
    hx, hz = at(0, 0)                 # the hood and the dark face in it
    t.set(hx, y + 6, hz, 'minecraft:polished_blackstone')
    px, pz = at(-1, 0)
    t.set(px, y + 6, pz, body(px, y + 6, pz))
    for b in (-1, 1):
        px, pz = at(0, b)
        t.set(px, y + 6, pz, stairs('or:tomb_brick_stairs', inward(b)))
    t.set(hx, y + 7, hz, stairs('or:tomb_brick_stairs', OPP[facing]))
    px, pz = at(-1, 0)
    t.set(px, y + 7, pz, slab('or:tomb_brick_slab'))


# ---------------------------------------------------------------- the gate (variant 1: the sunken gate)

class Gate:
    def __init__(self, seed, variant):
        self.tid = 'sith_tomb/gate' + ('' if variant == 1 else '_%d' % variant)
        self.t = C.Template(*GS, seed=seed, name=self.tid)
        self.v = variant
        self.m = mats(seed)
        self.rng = self.t.rng
        self.n = Noise(seed + 11, 4.0)
        self.n2 = Noise(seed + 12, 2.0)
        self.top = {}
        self.indoor = []

    def ring(self, x, z):
        return min(x, z, self.t.sx - 1 - x, self.t.sz - 1 - z)

    def gy(self, x, z):
        return self.top.get((x, z), G)

    def ground(self):
        t, m = self.t, self.m
        for x in range(t.sx):
            for z in range(t.sz):
                for y in range(0, 3):
                    t.set(x, y, z, m['deep'](x, y, z))
                for y in (3, 4):
                    t.set(x, y, z, m['sub'](x, y, z))
                t.set(x, G, z, m['sand'](x, G, z))

    def dunes(self, keep_clear, scale=7.0, amp=2.6):
        """Wind-built sand over the ground: ridges up to three high, thinning to nothing on the ramp rings
        and wherever keep_clear(x, z) says (approaches, anchors)."""
        t, m = self.t, self.m
        dn = Noise(t.seed + 21, scale)
        for x in range(t.sx):
            for z in range(t.sz):
                if keep_clear(x, z):
                    continue
                taper = max(0.0, min(1.0, (self.ring(x, z) - 1) / 3.0))
                h = int(round((amp * dn.at(x * 0.8, 0, z * 1.3) - 0.7) * taper))
                for y in range(G + 1, G + 1 + max(0, h)):
                    t.set(x, y, z, 'minecraft:red_sand' if self.n.at(x, y * 3, z) > 0.68 else 'minecraft:sand')
                if h > 0:
                    self.top[(x, z)] = G + h


def pit(g):
    """The forecourt sunk four deep into the sand, its walls lined with pilasters and urn niches, red
    light welling up through two floor channels toward the door."""
    t, m, rng = g.t, g.m, g.rng
    x0, z0, x1, z1 = PIT
    # retaining walls (two thick) and the floor
    for x in range(x0 - 2, x1 + 3):
        for z in range(z0 - 2, z1 + 3):
            inner = x0 <= x <= x1 and z0 <= z <= z1
            if inner:
                t.set(x, 0, z, m['rough'](x, 0, z))
                t.set(x, 1, z, m['pave'](x, 1, z))
                for y in range(PIT_ST, GS[1]):
                    t.set(x, y, z, C.AIR)
            else:
                for y in range(0, G + 1):
                    t.set(x, y, z, m['tomb'](x, y, z) if y >= 2 else m['rough'](x, y, z))
                # a broken parapet above the sand
                h = int(round(1.6 * g.n.at(x, 3, z) + 0.4))
                for y in range(G + 1, G + 1 + h):
                    t.set(x, y, z, m['tomb'](x, y, z))
                if h:
                    t.set(x, G + 1 + h, z, slab('or:tomb_brick_slab') if g.n2.at(x, 5, z) < 0.6 else C.AIR)
    # pilasters on the side walls with niches between them
    for z in range(z0 + 2, z1 - 1, 4):
        for (px, wx) in ((x0, x0 - 1), (x1, x1 + 1)):
            for y in range(PIT_ST, G + 1):
                t.set(px, y, z, S('or:tomb_pillar', axis='y') if y < G else 'or:tomb_stone_carved')
            t.set(px, G + 1, z, stairs('or:tomb_brick_stairs', 'east' if px == x0 else 'west'))
    for z in range(z0 + 4, z1 - 1, 4):
        for wx, face in ((x0 - 1, 'east'), (x1 + 1, 'west')):
            for y in (PIT_ST, PIT_ST + 1):
                t.set(wx, y, z, C.AIR)
            t.set(wx, PIT_ST - 1, z, 'or:tomb_stone_carved')
            t.set(wx, PIT_ST + 2, z, stairs('or:tomb_brick_stairs', face, half='top'))
            if (z // 4) % 2:
                pot(t, wx, PIT_ST, z, facing=face, cracked=bool(z % 3))
            else:
                red_candle(t, wx, PIT_ST, z, n=3 if z % 3 else 2)
    # the two channels of red light running to the door
    underglow(t, [(14, z) for z in range(z0 + 2, z1 - 4)] + [(22, z) for z in range(z0 + 2, z1 - 4)], 1)
    # sand blown in over the north corners and along the west wall
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            dw = min(x - x0, x1 - x, z - z0)
            corner = min(math.hypot(x - x0, z - z0), math.hypot(x - x1, z - z0))
            depth = 0
            if corner < 5:
                depth = 2 if corner < 2.5 else 1
            elif dw == 0 and g.n.at(x, 9, z) > 0.55:
                depth = 1
            for y in range(PIT_ST, PIT_ST + depth):
                if C.is_airish(t, x, y, z):
                    t.set(x, y, z, 'minecraft:suspicious_sand' if rng.random() < 0.06 else 'minecraft:sand')


def grand_stair(g):
    """Wind-cut steps down from the sand to the forecourt, between stepped side walls."""
    t, m, rng = g.t, g.m, g.rng
    xa, xb = CX - 5, CX + 5
    for x in range(xa - 2, xb + 3):
        for z in range(4, 10):
            t.set(x, G, z, m['pave'](x, G, z))
            for y in range(ST, GS[1]):
                t.set(x, y, z, C.AIR)
    for k, z in enumerate(range(10, 15)):
        y = G - k
        for x in range(xa, xb + 1):
            worn = rng.random() < 0.10
            t.set(x, y, z, slab('or:tomb_brick_slab', 'double') if worn else stairs('or:tomb_brick_stairs', 'north'))
            for yy in range(0, y):
                t.set(x, yy, z, m['rough'](x, yy, z))
            for yy in range(y + 1, GS[1]):
                t.set(x, yy, z, C.AIR)
        for x in (xa - 2, xa - 1, xb + 1, xb + 2):
            top = max(y + 2, PIT_ST + 1)
            for yy in range(0, top + 1):
                t.set(x, yy, z, m['tomb'](x, yy, z))
            for yy in range(top + 1, GS[1]):
                t.set(x, yy, z, C.AIR)
            if x in (xa - 1, xb + 1):
                t.set(x, top + 1, z, stairs('or:tomb_brick_stairs', 'south'))
    # sand drifted on the edges of the steps
    for (x, z) in ((xa, 11), (xa, 12), (xb, 10), (xa + 1, 12), (xb, 13)):
        y = G - (z - 10) + 1
        if C.is_airish(t, x, y, z):
            t.set(x, y, z, 'minecraft:sand')
    # braziers at the head of the stair
    soul_brazier(t, xa - 2, ST, 8)
    soul_brazier(t, xb + 2, ST, 8)
    mod.spot(t, 'STAIR_MOUTH', CX, ST, 9)


def portal(g):
    """The pylon over the door, battered back as it rises; the door frame stands proud; two guardians
    with swords point-down watch the forecourt; cold breath on the threshold."""
    t, m, rng = g.t, g.m, g.rng
    x0, z0, x1, z1 = PIT
    for x in range(x0 - 2, x1 + 3):
        dx = abs(x - CX)
        top = 15 if dx <= 3 else (21 if dx <= 8 else 21 - (dx - 8) * 3)    # two towers over a lower gate
        for z in range(z1 + 1, GS[2]):
            back = z - (z1 + 1)          # 0 at the face
            rear = GS[2] - 1 - z         # 0 at the back
            for y in range(0, top + 1):
                lean = (y - G) // 4 if y > G else 0
                if back < lean or rear < (y - G) // 5:
                    continue
                t.set(x, y, z, m['tomb'](x, y, z) if y > 1 else m['rough'](x, y, z))
            if back == 0 or back == 1:
                for y in range(top + 1, GS[1]):
                    if (x, y, z) not in t.cells:
                        t.set(x, y, z, C.AIR)
        # tower corners in carved stone, string courses and a cornice
        if dx in (4, 8):
            for y in range(G + 1, top + 1):
                z = z1 + 1 + max(0, (y - G) // 4)
                t.set(x, y, z, 'or:tomb_stone_carved' if y % 5 else S('or:tomb_pillar', axis='y'))
        for y in (9, 13):
            z = z1 + 1 + max(0, (y - G) // 4)
            if y <= top:
                t.set(x, y, z, 'or:tomb_stone_carved')
        z = z1 + 1 + max(0, (top - G) // 4)
        if top >= ST:
            t.set(x, top + 1, z, slab('or:tomb_brick_slab'))
    # the door through the pylon (3 wide, 3 high), a step down, and the passage to the processional
    # (5 wide, 3 high: the processional's own entry is 5 wide)
    for z in (z1 + 1, z1 + 2):
        for x in range(CX - 1, CX + 2):
            t.set(x, PIT_ST - 1, z, m['pave'](x, PIT_ST - 1, z))
            for y in range(PIT_ST, PIT_ST + 3):
                t.set(x, y, z, C.AIR)
    for z in range(z1 + 3, GS[2]):
        for x in range(CX - 2, CX + 3):
            t.set(x, 0, z, m['pave'](x, 0, z))
            for y in (1, 2, 3):
                t.set(x, y, z, C.AIR)
            t.set(x, 4, z, m['rough'](x, 4, z))
        for x in (CX - 3, CX + 3):
            for y in range(0, 5):
                t.set(x, y, z, m['tomb'](x, y, z))
    for x in (CX - 1, CX, CX + 1):
        t.set(x, 1, z1 + 3, stairs('or:tomb_brick_stairs', 'north'))
    for z in (z1 + 5, z1 + 7):
        for x in (CX - 2, CX + 2):
            t.set(x, 0, z, 'or:light_panel_red')
            t.set(x, 1, z, 'or:durasteel_grate')
    t.set(CX, 3, z1 + 6, S('soul_lantern', hanging=True, waterlogged=False))
    t.set(CX - 2, 3, z1 + 4, 'minecraft:cobweb')
    t.jigsaw(*JIG_DOWN, 'south_up', name='gate_down', target='processional_entry', pool='sith_tomb/underground',
             joint='aligned')
    # the frame standing proud of the face
    for y in range(PIT_ST, PIT_ST + 5):
        for jx in (CX - 2, CX + 2):
            t.set(jx, y, z1, S('or:tomb_pillar', axis='y') if y < PIT_ST + 3 else 'or:tomb_stone_carved')
    for x in range(CX - 3, CX + 4):
        t.set(x, PIT_ST + 3, z1, 'or:tomb_stone_carved')
        t.set(x, PIT_ST + 4, z1, stairs('or:tomb_brick_stairs', 'north', half='bottom') if abs(x - CX) < 3
              else slab('or:tomb_brick_slab'))
    for x in range(CX - 1, CX + 2):
        t.set(x, PIT_ST + 3, z1 + 1, 'or:tomb_stone_carved')
    # cold fire either side of the door, frost on the threshold
    soul_brazier(t, CX - 3, PIT_ST, z1 - 1)
    soul_brazier(t, CX + 3, PIT_ST, z1 - 1)
    frost(t, [(x, z) for x in range(CX - 3, CX + 4) for z in range(z1 - 3, z1 + 1)], PIT_ST, rng, p=0.18)
    frost(t, [(x, z) for x in range(CX - 1, CX + 2) for z in (z1 + 1, z1 + 2)], PIT_ST, rng, p=0.35)
    # guardians flanking the door, facing the stair
    guardian(t, CX - 6, z1 - 3, PIT_ST, 'north', m)
    guardian(t, CX + 6, z1 - 3, PIT_ST, 'north', m)


def obelisks(g):
    """The half-sunk obelisk gateway: one standing to the north-east, one fallen along the west wall,
    broken in two and sunk in the sand."""
    t, m, rng = g.t, g.m, g.rng
    # standing (north-east)
    ox, oz = CX + 11, 5
    for dx in range(-2, 4):
        for dz in range(-2, 4):
            t.set(ox + dx, ST, oz + dz, 'or:tomb_stone_carved' if dx in (-2, 3) or dz in (-2, 3) else m['tomb'](ox + dx, ST, oz + dz))
    for dx in range(-1, 3):
        for dz in range(-1, 3):
            t.set(ox + dx, ST + 1, oz + dz, m['tomb'](ox + dx, ST + 1, oz + dz))
    for y in range(ST + 2, ST + 20):
        for dx in (0, 1):
            for dz in (0, 1):
                t.set(ox + dx, y, oz + dz, 'or:tomb_stone_carved' if (y - ST) % 6 == 1 else m['tomb'](ox + dx, y, oz + dz))
    yt = ST + 20
    for (dx, dz, f) in ((0, 0, 'south'), (1, 0, 'west'), (0, 1, 'east'), (1, 1, 'north')):
        t.set(ox + dx, yt, oz + dz, stairs('or:tomb_brick_stairs', f))
    # fallen (west): its plinth and stump at the north end, the shaft lying south in two pieces
    fx, fz = 4, 4
    for dx in range(-1, 3):
        for dz in range(-1, 3):
            t.set(fx + dx, ST, fz + dz, m['tomb'](fx + dx, ST, fz + dz))
    for y in range(ST + 1, ST + 4):
        for dx in (0, 1):
            for dz in (0, 1):
                if y < ST + 3 or (dx + dz) % 2 == 0:
                    t.set(fx + dx, y, fz + dz, m['tomb'](fx + dx, y, fz + dz))
    for z in range(fz + 4, fz + 19):
        if fz + 10 <= z <= fz + 11:
            continue
        for dx in (0, 1):
            t.set(fx + dx, G, z, 'or:tomb_stone_carved' if (z - fz) % 6 == 1 else m['tomb'](fx + dx, G, z))
            if z < fz + 16:
                t.set(fx + dx, ST, z, m['tomb'](fx + dx, ST, z))
    for (x, z) in ((3, 14), (6, 15), (5, 9), (3, 20)):
        if C.is_airish(t, x, ST, z):
            rubble(t, x, ST, z, rng)
    for z in range(fz + 3, fz + 20):
        for x in (fx - 1, fx + 2):
            if C.is_airish(t, x, ST, z) and g.n.at(x, 2, z) > 0.45:
                t.set(x, ST, z, 'minecraft:sand')


def surface(g):
    """Wind-cut rocks, dead brush, bleached bones; the Imperial dig's floodlight and winch at the edge."""
    t, m, rng = g.t, g.m, g.rng
    for (x, z, r) in ((37, 20, 1.6), (2, 31, 1.4), (37, 34, 1.2), (12, 2, 1.0), (28, 1, 0.9), (3, 38, 1.1)):
        for dx in range(-2, 3):
            for dy in range(0, 3):
                for dz in range(-2, 3):
                    if dx * dx + (dy * 1.5) ** 2 + dz * dz <= r * r + rng.random() * 0.5 and g.ring(x + dx, z + dz) >= 1:
                        C.put(t, x + dx, ST + dy, z + dz, m['rock'], only_empty=True)
    # the Imperial floodlight mast at the forecourt's east edge, a cable down the wall
    mx, mz = PIT[2] + 4, 22
    for y in range(ST, ST + 6):
        t.set(mx, y, mz, S('iron_bars', north=False, south=False, east=False, west=False, waterlogged=False))
    t.set(mx, ST + 6, mz, 'or:durasteel_plating')
    t.set(mx - 1, ST + 6, mz, 'or:light_panel')
    t.set(mx, ST, mz + 1, 'or:cargo_crate_imperial')
    # the winch over the forecourt's east side
    wx = PIT[2] + 3
    for z in (17, 21):
        for y in range(ST, ST + 4):
            t.set(wx, y, z, S('spruce_fence', waterlogged=False))
    for z in range(17, 22):
        t.set(wx, ST + 4, z, S('stripped_spruce_log', axis='z'))
    for x in range(PIT[2], wx):
        t.set(x, ST + 4, 19, S('stripped_spruce_log', axis='x'))
    for y in range(PIT_ST + 2, ST + 4):
        t.set(PIT[2], y, 19, S('iron_chain', axis='y', waterlogged=False))
    t.set(PIT[2], PIT_ST + 1, 19, S('lantern', hanging=True, waterlogged=False))
    t.set(PIT[2] - 1, PIT_ST, 18, 'or:cargo_crate_imperial')
    t.set(PIT[2] - 1, PIT_ST, 17, S('barrel', facing='up', open=False))
    # a ladder down the west wall where the diggers climb
    for y in range(PIT_ST, G + 1):
        t.set(PIT[0], y, 27, S('ladder', facing='east', waterlogged=False))
    # brush and bones
    for (x, z) in ((2, 9), (36, 12), (8, 37), (35, 37), (15, 3), (38, 26), (1, 24), (25, 2)):
        if C.is_airish(t, x, ST, z) and C.name(t.get(x, G, z)) in ('minecraft:sand', 'minecraft:red_sand', 'minecraft:coarse_dirt'):
            t.set(x, ST, z, 'minecraft:dead_bush')
    for (x, z, ax) in ((9, 3, 'x'), (37, 15, 'z'), (33, 38, 'x')):
        if C.is_airish(t, x, ST, z):
            t.set(x, ST, z, S('bone_block', axis=ax))


def gate_v1(seed=62001):
    g = Gate(seed, 1)
    t = g.t
    g.ground()
    g.dunes(lambda x, z: (CX - 4 <= x <= CX + 4 and z <= 9) or (x <= 4 and 3 <= z <= 7) or
            (PIT[0] - 3 <= x <= PIT[2] + 3 and PIT[1] - 3 <= z))
    pit(g)
    grand_stair(g)
    portal(g)
    obelisks(g)
    surface(g)
    t.jigsaw(*JIG_PATH, 'west_up', name='gate_path', target='path_in', pool='sith_tomb/path', joint='aligned')
    t.set(1, ST, 5, C.AIR)
    mod.spot(t, 'SURFACE_GUARD', CX, ST, 3)
    C.air_up(t, 0, 0, t.sx - 1, t.sz - 1, ST)
    mod.light_fill(t, region=(PIT[0], PIT_ST, PIT[1], PIT[2], PIT_ST + 3, PIT[3]), level=8, strength=11)
    mod.light_fill(t, region=(CX - 2, 1, PIT[3] + 1, CX + 2, 3, GS[2] - 1), level=8, strength=11)
    return g


# ---------------------------------------------------------------- the gate, variant 2: the throat in the butte

BANDS = ['minecraft:terracotta', 'minecraft:orange_terracotta', 'minecraft:orange_terracotta', 'minecraft:red_terracotta',
         'minecraft:terracotta', 'minecraft:brown_terracotta', 'minecraft:white_terracotta', 'minecraft:light_gray_terracotta',
         'minecraft:smooth_red_sandstone', 'minecraft:red_terracotta', 'minecraft:terracotta']


def butte_top(g, x, z):
    """The rock's top (absolute y) at (x, z), or None: a flat-topped butte south of the facade line z 22,
    its sides eroded in steps."""
    if z < 22:
        return None
    dx, dz = (x - 20) / 14.5, (z - 30) / 9.0
    d = math.hypot(dx, dz)
    f = (1.08 - d) / 0.33 + (g.n.at(x, 50, z) - 0.5) * 0.9
    if f <= 0:
        return None
    h = 15 * min(1.0, f)
    h = int(h // 2 * 2) + (1 if g.n2.at(x, 51, z) > 0.6 else 0)
    if z <= 23:
        h = max(h, 15)
    return G + max(1, h)


def butte(g):
    t = g.t
    off = Noise(t.seed + 31, 9.0)
    for x in range(t.sx):
        for z in range(t.sz):
            top = butte_top(g, x, z)
            if top is None or g.ring(x, z) < 2:
                continue
            for y in range(G + 1, top + 1):
                k = (y + int(off.at(x, 0, z) * 2.5)) % len(BANDS)
                t.set(x, y, z, BANDS[k])
            g.top[(x, z)] = top
            if g.n2.at(x, 52, z) > 0.84 and t.rng.random() < 0.5 and C.is_airish(t, x, top + 1, z):
                t.set(x, top + 1, z, 'minecraft:dead_bush')


def court_v2(g):
    """A court cut down into the sandstone before the facade, a stair of wind-worn steps from the north."""
    t, m, rng = g.t, g.m, g.rng
    cut = C.mix(t.seed + 33, [('minecraft:cut_sandstone', 4), ('minecraft:sandstone', 4), ('minecraft:smooth_sandstone', 2),
                              ('minecraft:chiseled_sandstone', 1)], 2.5)
    x0, z0, x1, z1 = 10, 12, 30, 21
    for x in range(x0 - 2, x1 + 3):
        for z in range(z0 - 2, z1 + 1):
            inner = x0 <= x <= x1 and z0 <= z <= z1
            if inner:
                t.set(x, 0, z, m['rough'](x, 0, z))
                t.set(x, 1, z, m['pave'](x, 1, z) if g.n.at(x, 60, z) > 0.3 else 'minecraft:smooth_sandstone')
                for y in range(PIT_ST, GS[1]):
                    t.set(x, y, z, C.AIR)
            else:
                for y in range(0, G + 1):
                    t.set(x, y, z, cut(x, y, z))
                if g.n2.at(x, 61, z) > 0.45:
                    t.set(x, G + 1, z, cut(x, G + 1, z))
    # the stair down from the north
    xa, xb = 16, 24
    for x in range(xa - 1, xb + 2):
        for z in range(4, 8):
            t.set(x, G, z, cut(x, G, z))
            for y in range(ST, GS[1]):
                t.set(x, y, z, C.AIR)
    for k, z in enumerate(range(8, 12)):
        y = G - k
        for x in range(xa, xb + 1):
            t.set(x, y, z, stairs('minecraft:sandstone_stairs', 'north') if rng.random() > 0.12 else
                  'minecraft:smooth_sandstone_slab[type=double,waterlogged=false]')
            for yy in range(0, y):
                t.set(x, yy, z, cut(x, yy, z))
            for yy in range(y + 1, GS[1]):
                t.set(x, yy, z, C.AIR)
        for x in (xa - 1, xb + 1):
            for yy in range(0, max(y + 2, PIT_ST + 1) + 1):
                t.set(x, yy, z, cut(x, yy, z))
    soul_brazier(t, xa - 1, ST, 6)
    soul_brazier(t, xb + 1, ST, 6)
    mod.spot(t, 'STAIR_MOUTH', CX, ST, 6)
    # sand blown into the court's corners
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            c = min(math.hypot(x - x0, z - z0), math.hypot(x - x1, z - z0))
            if c < 3.5 and C.is_airish(t, x, PIT_ST, z):
                t.set(x, PIT_ST, z, 'minecraft:sand')
                if c < 1.6:
                    t.set(x, PIT_ST + 1, z, 'minecraft:sand')


def facade_v2(g):
    """The tomb front set into the butte's cliff: a black temple face, four engaged columns, an
    entablature and a stepped pediment; guardians in the side bays; the doorway into the throat."""
    t, m, rng = g.t, g.m, g.rng
    fx0, fx1 = 10, 30
    for x in range(fx0, fx1 + 1):
        for z in (22, 23):
            for y in range(0, 18):
                t.set(x, y, z, m['tomb'](x, y, z) if y > 1 else m['rough'](x, y, z))
    for x in range(fx0, fx1 + 1):            # plinth course standing proud
        for y in (2, 3):
            t.set(x, y, 21, 'or:tomb_stone_carved' if y == 3 else m['tomb'](x, y, 21))
    for cx in (11, 15, 25, 29):              # engaged columns
        for y in range(4, 15):
            t.set(cx, y, 21, S('or:tomb_pillar', axis='y'))
        t.set(cx, 4, 21, 'or:tomb_stone_carved')
        t.set(cx, 15, 21, 'or:tomb_stone_carved')
    for x in range(fx0, fx1 + 1):            # entablature
        t.set(x, 16, 21, 'or:tomb_stone_carved')
        t.set(x, 17, 21, stairs('or:tomb_brick_stairs', 'south', half='top'))
    for i, y in enumerate(range(18, 22)):    # pediment
        xa, xb = fx0 + 2 + i * 2, fx1 - 2 - i * 2
        for x in range(xa, xb + 1):
            for z in (22, 23):
                t.set(x, y, z, m['tomb'](x, y, z))
            if x in (xa, xb):
                t.set(x, y, 21, stairs('or:tomb_brick_stairs', 'east' if x == xa else 'west'))
            else:
                t.set(x, y, 21, slab('or:tomb_brick_slab') if y == 21 else C.AIR)
        t.set(xa, y, 22, stairs('or:tomb_brick_stairs', 'east'))
        t.set(xb, y, 22, stairs('or:tomb_brick_stairs', 'west'))
    # the side bays: recessed, a guardian before each
    for (bx0, bx1) in ((12, 14), (26, 28)):
        for x in range(bx0, bx1 + 1):
            for y in range(5, 14):
                t.set(x, y, 22, C.AIR)
                t.set(x, y, 21, C.AIR)
            t.set(x, 14, 22, stairs('or:tomb_brick_stairs', 'north', half='top'))
        t.set((bx0 + bx1) // 2, 13, 23, 'or:light_panel_red')
    guardian(t, 13, 19, PIT_ST, 'north', m)
    guardian(t, 27, 19, PIT_ST, 'north', m)
    # the doorway: a deep frame, the door 3 wide and 4 high, and the throat behind it
    for x in range(17, 24):
        for y in range(2, 10):
            if x in (17, 23) or y == 9:
                t.set(x, y, 21, 'or:tomb_stone_carved' if y in (2, 9) or x in (17, 23) and y % 3 == 0
                      else S('or:tomb_pillar', axis='y'))
    for x in range(18, 23):
        for y in range(2, 9):
            t.set(x, y, 21, C.AIR)
        t.set(x, 8, 22, stairs('or:tomb_brick_stairs', 'north', half='top'))
    for x in range(19, 22):
        for z in (22, 23, 24):
            t.set(x, 1, z, m['pave'](x, 1, z))
            for y in range(2, 6):
                t.set(x, y, z, C.AIR)
        t.set(x, 6, 22, 'or:tomb_stone_carved')
    # the throat: down a step and on under the rock to the processional
    for z in range(25, GS[2]):
        for x in range(CX - 3, CX + 4):
            edge = x in (CX - 3, CX + 3)
            for y in range(0, 5):
                if edge or y in (0, 4):
                    t.set(x, y, z, m['tomb'](x, y, z) if not (y == 0 and not edge) else m['pave'](x, y, z))
                else:
                    t.set(x, y, z, C.AIR)
    for x in range(CX - 1, CX + 2):
        t.set(x, 1, 25, stairs('or:tomb_brick_stairs', 'north'))
    for z in range(27, GS[2], 3):
        for x in (CX - 2, CX + 2):
            t.set(x, 0, z, 'or:light_panel_red')
            t.set(x, 1, z, 'or:durasteel_grate')
    for z in (28, 34):
        t.set(CX, 3, z, S('soul_lantern', hanging=True, waterlogged=False))
    for (x, z) in ((CX - 2, 30), (CX + 2, 36)):
        t.set(x, 3, z, 'minecraft:cobweb')
    for (x, z) in ((CX + 2, 31), (CX - 2, 37)):
        t.set(x, 1, z, S('bone_block', axis='z'))
    t.jigsaw(*JIG_DOWN, 'south_up', name='gate_down', target='processional_entry', pool='sith_tomb/underground',
             joint='aligned')
    soul_brazier(t, 16, PIT_ST, 20)
    soul_brazier(t, 24, PIT_ST, 20)
    frost(t, [(x, z) for x in range(17, 24) for z in range(18, 21)], PIT_ST, rng, p=0.15)
    frost(t, [(x, z) for x in range(19, 22) for z in range(22, 25)], PIT_ST, rng, p=0.35)


def excavation_v2(g):
    """The Empire's work on the face: a scaffold tower up the west side with a winch, crates at its
    foot, a fallen column drum in the court, a leaning obelisk half sunk at the court's corner."""
    t, m, rng = g.t, g.m, g.rng
    for y in range(PIT_ST, 15):
        for (x, z) in ((8, 19), (9, 19), (8, 20), (9, 20)):
            t.set(x, y, z, 'minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]')
    for (x, z) in ((10, 20), (10, 19)):
        t.set(x, 14, z, 'minecraft:spruce_planks')
    t.set(8, 15, 19, S('spruce_fence', waterlogged=False))
    t.set(8, 16, 19, S('stripped_spruce_log', axis='x'))
    t.set(9, 16, 19, S('stripped_spruce_log', axis='x'))
    t.set(10, 16, 19, S('stripped_spruce_log', axis='x'))
    for y in range(PIT_ST + 2, 14):
        t.set(10, y, 19, S('iron_chain', axis='y', waterlogged=False))
    t.set(10, PIT_ST + 1, 19, S('lantern', hanging=True, waterlogged=False))
    t.set(11, PIT_ST, 18, 'or:cargo_crate_imperial')
    t.set(12, PIT_ST, 17, 'or:cargo_crate_imperial')
    t.set(11, PIT_ST + 1, 18, S('barrel', facing='up', open=False))
    # a drum fallen from the east column's broken top
    for y in range(13, 15):
        t.set(29, y, 21, C.AIR)
    t.set(29, 13, 21, slab('or:tomb_brick_slab'))
    for x in range(25, 29):
        t.set(x, PIT_ST, 16, S('or:tomb_pillar', axis='x') if x < 28 else 'or:tomb_stone_carved')
    rubble(t, 28, PIT_ST, 18, rng)
    rubble(t, 26, PIT_ST, 15, rng)
    # the leaning obelisk, half sunk in the sand north-east of the court
    C.tilted_cylinder(t, (33.0, 3.0, 9.0), (36.5, 17.0, 13.0), 1.05, 'or:tomb_bricks')
    for (x, z) in ((32, 8), (33, 10), (34, 9), (32, 10)):
        t.set(x, ST, z, 'minecraft:sand')


def gate_v2(seed=62002):
    g = Gate(seed, 2)
    t = g.t
    g.ground()
    g.dunes(lambda x, z: (CX - 5 <= x <= CX + 5 and z <= 8) or (x <= 4 and 3 <= z <= 7) or
            (7 <= x <= 33 and 9 <= z <= 23) or z >= 22, amp=2.2)
    butte(g)
    court_v2(g)
    facade_v2(g)
    excavation_v2(g)
    surface_v2(g)
    t.jigsaw(*JIG_PATH, 'west_up', name='gate_path', target='path_in', pool='sith_tomb/path', joint='aligned')
    t.set(1, ST, 5, C.AIR)
    mod.spot(t, 'SURFACE_GUARD', CX, ST, 2)
    C.air_up(t, 0, 0, t.sx - 1, t.sz - 1, ST)
    mod.light_fill(t, region=(CX - 2, 1, 22, CX + 2, 4, GS[2] - 1), level=8, strength=11)
    return g


def surface_v2(g):
    t, m, rng = g.t, g.m, g.rng
    for (x, z, r) in ((4, 14, 1.5), (36, 18, 1.3), (6, 30, 1.2), (12, 3, 1.0), (30, 2, 1.1)):
        for dx in range(-2, 3):
            for dy in range(0, 3):
                for dz in range(-2, 3):
                    if dx * dx + (dy * 1.5) ** 2 + dz * dz <= r * r + rng.random() * 0.5 and g.ring(x + dx, z + dz) >= 1:
                        C.put(t, x + dx, g.gy(x + dx, z + dz) + 1 + dy, z + dz, m['rock'], only_empty=True)
    for (x, z) in ((2, 9), (37, 12), (6, 2), (34, 4), (14, 1), (2, 22), (38, 26)):
        y = g.gy(x, z) + 1
        if C.is_airish(t, x, y, z):
            t.set(x, y, z, 'minecraft:dead_bush')
    for (x, z, ax) in ((9, 4, 'x'), (35, 8, 'z')):
        y = g.gy(x, z) + 1
        if C.is_airish(t, x, y, z):
            t.set(x, y, z, S('bone_block', axis=ax))


# ---------------------------------------------------------------- the underground (re-skinned in place)

MECH = ('oldrepublic:tomb_vent', 'oldrepublic:veiled_stone', 'minecraft:jigsaw', 'oldrepublic:site_spawner',
        'oldrepublic:echo_urn', 'oldrepublic:urn_keeper', 'oldrepublic:holocron_pedestal', 'oldrepublic:puzzle_seal',
        'minecraft:chest')
STONE = ('oldrepublic:tomb_stone', 'oldrepublic:tomb_stone_cracked', 'oldrepublic:tomb_stone_carved')


def is_air(t, x, y, z):
    s = t.get(x, y, z)
    return s is not None and C.name(s) in ('minecraft:air', 'minecraft:cave_air')


def faces(t, x, y, z):
    """Which of the six neighbours are explicit air (a cell next to the void is not a face)."""
    return {d for d, (dx, dy, dz) in (('up', (0, 1, 0)), ('down', (0, -1, 0)), ('n', (0, 0, -1)), ('s', (0, 0, 1)),
                                      ('w', (-1, 0, 0)), ('e', (1, 0, 0)))
            if is_air(t, x + dx, y + dy, z + dz)}


def reskin(t, m, frozen, band_every=0, band_axis='z', rib_every=0):
    """Dress the stone shell by role: paving on floors, rough stone on ceilings, dressed bricks on walls,
    carved bands every `band_every` cells along band_axis and carved ribs across the ceiling."""
    for (x, y, z), st in list(t.cells.items()):
        if (x, y, z) in frozen or C.name(st) not in STONE:
            continue
        f = faces(t, x, y, z)
        if not f:
            continue
        along = z if band_axis == 'z' else x
        if 'up' in f:
            t.set(x, y, z, m['pave'](x, y, z))
        elif 'down' in f and not (f & {'n', 's', 'w', 'e'}):
            if rib_every and along % rib_every == 0:
                t.set(x, y, z, 'or:tomb_stone_carved')
            else:
                t.set(x, y, z, m['rough'](x, y, z))
        else:
            if band_every and along % band_every == 0:
                t.set(x, y, z, S('or:tomb_pillar', axis='y'))
            else:
                t.set(x, y, z, m['tomb'](x, y, z))


def floor_of(t, x, z, y_hi, y_lo=0):
    """The highest y in [y_lo, y_hi] that is air with a solid block under it (a walking cell)."""
    for y in range(y_hi, y_lo, -1):
        if is_air(t, x, y, z) and not is_air(t, x, y - 1, z) and t.get(x, y - 1, z) is not None:
            return y
    return None


def processional(seed=62020):
    """The processional keeps its size, its slope and every mechanism cell; the shell is dressed in tomb
    bricks with pilasters, urn niches cut into the walls, red light rising through grates at the walls'
    feet, frost and sand blown in at the top."""
    t = C.load_base('sith_tomb/processional', seed)
    m = mats(seed)
    rng = t.rng
    mech = [p for p, s in t.cells.items() if C.name(s) in MECH]
    frozen = C.neighbourhood(t, MECH, radius=1)
    snap = C.snapshot(t, frozen)
    # close the old open trench at the top into a corridor roof (sand above it would pour in)
    for z in range(0, 6):
        for x in range(1, 8):
            if (x, 13, z) not in frozen:
                t.set(x, 13, z, m['rough'](x, 13, z))
        for x in (1, 7):
            for y in range(10, 13):
                if (x, y, z) not in frozen and (t.get(x, y, z) is None or is_air(t, x, y, z)):
                    t.set(x, y, z, m['tomb'](x, y, z))
    for z in range(0, 6):
        for x in (0, 8):
            for y in range(9, 14):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, m['rough'](x, y, z))
    reskin(t, m, frozen, band_every=5, rib_every=5)
    # niches with urns and candles cut into the walls, away from the side doors and the vents
    for (wx, inner, z) in ((1, 2, 6), (7, 6, 8), (1, 2, 16), (7, 6, 22), (1, 2, 21), (7, 6, 11), (1, 2, 24)):
        fy = floor_of(t, inner, z, 12)
        if fy is None:
            continue
        cells = [(wx, fy, z), (wx, fy + 1, z)]
        if any(c in frozen for c in cells) or (wx, fy + 2, z) in frozen:
            continue
        for c in cells:
            t.set(*c, C.AIR)
        t.set(wx, fy - 1, z, 'or:tomb_stone_carved')
        t.set(wx, fy + 2, z, stairs('or:tomb_brick_stairs', 'east' if wx == 1 else 'west', half='top'))
        if z % 2:
            urn(t, wx, fy, z)
        else:
            red_candle(t, wx, fy, z, n=2 + z % 3)
    # red light at the feet of the walls
    for (x, z) in ((2, 5), (6, 7), (2, 11), (6, 15), (2, 20), (6, 20), (2, 23), (6, 25)):
        fy = floor_of(t, x, z, 12)
        if fy is None:
            continue
        g, lamp = (x, fy - 1, z), (x, fy - 2, z)
        if g in frozen or lamp in frozen:
            continue
        if C.name(t.get(*g)) in ('oldrepublic:tomb_vent',):
            continue
        t.set(*g, 'or:durasteel_grate')
        t.set(*lamp, 'or:light_panel_red')
    # the breath at the top: frost, sand blown down from the gate, cobwebs, a few bones
    for z in range(0, 7):
        for x in range(2, 7):
            fy = floor_of(t, x, z, 12)
            if fy is None or (x, fy, z) in frozen:
                continue
            if z <= 1 and x in (2, 6) and rng.random() < 0.7:
                t.set(x, fy, z, 'minecraft:sand')
            elif rng.random() < 0.22 and C.name(t.get(x, fy - 1, z)) in STONE + ('oldrepublic:tomb_bricks',
                                                                              'minecraft:polished_blackstone'):
                t.set(x, fy, z, S('snow', layers=1))
    for (x, y, z) in ((2, 12, 3), (6, 11, 8), (2, 9, 12), (6, 6, 19)):
        if is_air(t, x, y, z) and (x, y, z) not in frozen:
            t.set(x, y, z, 'minecraft:cobweb')
    for (x, z) in ((6, 12), (2, 17)):
        fy = floor_of(t, x, z, 12)
        if fy and (x, fy, z) not in frozen:
            t.set(x, fy, z, S('bone_block', axis='z'))
    C.restore(t, snap)
    for p in mech:
        assert C.name(t.get(*p)) in MECH, p
    mod.light_fill(t, level=8, strength=11)
    C.restore(t, snap)
    return t


def burial_chamber(seed=62030):
    """The burial chamber keeps its size, its urn puzzle, the seals, the keeper, the pedestal and the chest
    where they are. The hall is dressed: clustered piers, a ribbed vault, red light welling along the
    nave from the entry stair to the dais, a sarcophagus in the sealed crypt, urns in the walls."""
    t = C.load_base('sith_tomb/burial_chamber', seed)
    m = mats(seed)
    rng = t.rng
    mech = [p for p, s in t.cells.items() if C.name(s) in MECH]
    frozen = C.neighbourhood(t, MECH, radius=1)
    snap = C.snapshot(t, frozen)
    reskin(t, m, frozen, band_every=4, band_axis='x', rib_every=4)
    # the floor grid of carved stones goes; paving with a black border along the walls
    for x in range(2, 25):
        for z in range(2, 25):
            if (x, 1, z) in frozen or not is_air(t, x, 2, z):
                continue
            edge = x in (2, 24) or z in (2, 24)
            t.set(x, 1, z, 'minecraft:polished_blackstone' if edge else m['pave'](x, 1, z))
    # the four piers become clustered columns
    for (px, pz) in ((6, 6), (20, 6), (6, 20), (20, 20)):
        for y in range(2, 11):
            if (px, y, pz) not in frozen:
                t.set(px, y, pz, S('or:tomb_pillar', axis='y') if 2 < y < 10 else 'or:tomb_stone_carved')
            for (dx, dz, f) in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
                q = (px + dx, y, pz + dz)
                if q in frozen or not is_air(t, *q):
                    continue
                if y == 2:
                    t.set(*q, stairs('or:tomb_brick_stairs', f))
                elif y == 9:
                    t.set(*q, stairs('or:tomb_brick_stairs', f, half='top'))
                elif y == 10:
                    t.set(*q, 'or:tomb_stone_carved')
    # ribs across the vault between the piers
    for z in (6, 20):
        for x in range(3, 24):
            if is_air(t, x, 10, z) and (x, 10, z) not in frozen:
                t.set(x, 10, z, 'or:tomb_stone_carved')
    for x in (6, 20):
        for z in range(3, 24):
            if is_air(t, x, 10, z) and (x, 10, z) not in frozen:
                t.set(x, 10, z, 'or:tomb_stone_carved')
    # the nave's red light: two channels from the foot of the entry stair to the dais
    for z in range(8, 14):
        for x in (10, 16):
            if (x, 1, z) in frozen or (x, 0, z) in frozen or not is_air(t, x, 2, z):
                continue
            t.set(x, 1, z, 'or:durasteel_grate')
            t.set(x, 0, z, 'or:light_panel_red')
    # wall niches holding funerary urns and red candles on the long walls
    for z in (4, 11, 13, 22):
        for (wx, face) in ((1, 'east'), (25, 'west')):
            cells = [(wx, 2, z), (wx, 3, z)]
            if any(c in frozen for c in cells) or (wx, 4, z) in frozen:
                continue
            for c in cells:
                t.set(*c, C.AIR)
            t.set(wx, 4, z, stairs('or:tomb_brick_stairs', face, half='top'))
            t.set(wx, 1, z, 'or:tomb_stone_carved')
            if z in (11, 22):
                pot(t, wx, 2, z, facing=face, cracked=z == 22)
            else:
                red_candle(t, wx, 2, z, n=3)
    # the dais: an altar with candles and a cold brazier either side
    for (x, z) in ((10, 15), (16, 15)):
        if (x, 3, z) not in frozen and is_air(t, x, 3, z):
            t.set(x, 3, z, S('soul_campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
    for (x, z) in ((12, 15), (14, 15)):
        if (x, 5, z) not in frozen and is_air(t, x, 5, z):
            red_candle(t, x, 5, z, n=4)
    # the sealed crypt: a sarcophagus on a plinth, lid of slabs, a hooded effigy's head
    for x in range(10, 15):
        for z in range(20, 23):
            if (x, 2, z) in frozen:
                continue
            if 11 <= x <= 13 and z == 21:
                t.set(x, 2, z, 'minecraft:polished_blackstone')
                t.set(x, 3, z, slab('or:tomb_brick_slab'))
    for (x, y, z) in ((10, 5, 19), (14, 5, 19), (10, 5, 23)):
        if is_air(t, x, y, z) and (x, y, z) not in frozen:
            t.set(x, y, z, 'minecraft:cobweb')
    # hanging soul lanterns on chains from the vault
    for (x, z) in ((9, 9), (17, 9), (13, 6), (9, 17), (17, 17)):
        if all(is_air(t, x, y, z) for y in (7, 8, 9)):
            t.set(x, 9, z, S('iron_chain', axis='y', waterlogged=False))
            t.set(x, 8, z, S('soul_lantern', hanging=True, waterlogged=False))
    # bones of those who came before, frost by the entry
    for (x, z, ax) in ((4, 22, 'x'), (22, 4, 'z'), (23, 19, 'x')):
        if is_air(t, x, 2, z) and (x, 2, z) not in frozen:
            t.set(x, 2, z, S('bone_block', axis=ax))
    for x in range(11, 16):
        for z in range(0, 4):
            if is_air(t, x, 6, z) and (x, 6, z) not in frozen and rng.random() < 0.35 and \
                    C.name(t.get(x, 5, z)) in STONE + ('oldrepublic:tomb_bricks', 'minecraft:polished_blackstone'):
                t.set(x, 6, z, S('snow', layers=1))
    C.restore(t, snap)
    for p in mech:
        assert C.name(t.get(*p)) in MECH, p
    mod.light_fill(t, level=8, strength=11)
    C.restore(t, snap)
    return t


# ---------------------------------------------------------------- the path, the dig camp, the side rooms

def path_piece(seed=62040):
    """The wind-cut track from the gate to the dig camp (terrain-matching): worn sandstone slabs in drifted
    sand, a fallen drum of black stone, brush. 7 x 3 x 13; jigsaws at (3, 1, 0) and (3, 1, 12)."""
    t = C.Template(7, 3, 13, seed=seed, name='sith_tomb/path')
    rng = t.rng
    sand = C.mix(seed + 1, [('minecraft:sand', 6), ('minecraft:red_sand', 1), ('minecraft:coarse_dirt', 1)], 3.0)
    track = C.mix(seed + 2, [('minecraft:smooth_sandstone', 4), ('minecraft:coarse_dirt', 3), ('minecraft:gravel', 2),
                             ('minecraft:sandstone', 2)], 1.6)
    for x in range(7):
        for z in range(13):
            wob = int(round(math.sin(z * 0.5) * 0.8))
            on = abs(x - 3 - wob) <= 1
            t.set(x, 0, z, track(x, 0, z) if on else sand(x, 0, z))
            t.set(x, 1, z, C.AIR)
            t.set(x, 2, z, C.AIR)
    for z in (2, 5, 9):
        x = 3 + int(round(math.sin(z * 0.5) * 0.8))
        t.set(x, 0, z, 'minecraft:smooth_sandstone_slab[type=double,waterlogged=false]')
    t.set(0, 1, 7, 'or:tomb_stone_carved')
    t.set(0, 1, 8, S('or:tomb_pillar', axis='z'))
    t.set(0, 2, 7, slab('or:tomb_brick_slab'))
    for (x, z) in ((6, 3), (1, 11), (6, 10)):
        t.set(x, 1, z, 'minecraft:dead_bush')
    t.set(6, 1, 6, S('bone_block', axis='x'))
    t.jigsaw(3, 1, 0, 'north_up', name='path_in', target='gate_path', pool='minecraft:empty')
    t.jigsaw(3, 1, 12, 'south_up', name='path_out', target='camp_entry', pool='sith_tomb/side')
    return t


def a_tent(t, x0, x1, zc, y=1, roof='or:canvas', end='or:canvas_crimson', open_end='x0'):
    for x in range(x0, x1 + 1):
        t.set(x, y + 2, zc, slab(roof + '_slab'))
        t.set(x, y + 1, zc - 1, stairs(roof + '_stairs', 'south'))
        t.set(x, y + 1, zc + 1, stairs(roof + '_stairs', 'north'))
        t.set(x, y, zc - 2, stairs(roof + '_stairs', 'south'))
        t.set(x, y, zc + 2, stairs(roof + '_stairs', 'north'))
        for z in (zc - 1, zc, zc + 1):
            t.set(x, y, z, C.AIR)
        t.set(x, y + 1, zc, C.AIR)
    back = x1 if open_end == 'x0' else x0
    for z in (zc - 1, zc, zc + 1):
        t.set(back, y, z, end)
    t.set(back, y + 1, zc, end)


def dig_camp(seed=62050):
    """The Imperial dig: a command tent with the survey on a holo-table, barracks tents, a supply depot
    with its generator, a comms mast, and the finds laid out in rows by the sieve and the test trench.
    31 x 12 x 31, buried 1; the path comes in from the east at (30, 1, 15)."""
    t = C.Template(31, 12, 31, seed=seed, name='sith_tomb/dig_camp')
    rng = t.rng
    m = mats(seed)
    yard = C.mix(seed + 3, [('minecraft:sand', 10), ('minecraft:coarse_dirt', 3), ('minecraft:gravel', 2),
                            ('minecraft:red_sand', 2)], 4.0)
    trod = C.mix(seed + 4, [('minecraft:coarse_dirt', 4), ('minecraft:gravel', 3), ('minecraft:sand', 2)], 1.8)
    for x in range(31):
        for z in range(31):
            t.set(x, 0, z, yard(x, 0, z))
    C.shapes.path(t, [(30, 15), (22, 15), (16, 14), (10, 13)], lambda x, z: 0, 3, trod, rng=rng, jitter=0.4)
    C.shapes.path(t, [(15, 14), (15, 22), (20, 24)], lambda x, z: 0, 2, trod, rng=rng, jitter=0.4)
    # the command tent (north-west): crimson walls, a canvas roof, the survey on the table
    x0, z0, x1, z1 = 3, 3, 11, 10
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set(x, 0, z, 'or:durasteel_deck' if not edge else yard(x, 0, z))
            for y in range(1, 4):
                t.set(x, y, z, 'or:canvas_crimson' if edge else C.AIR)
    for x in range(x0, x1 + 1):
        for i, z in enumerate(range(z0, z1 + 1)):
            k = min(i, z1 - z0 - i)
            if k <= 1:
                t.set(x, 4, z, stairs('or:canvas_stairs', 'south' if i <= 1 else 'north'))
            else:
                t.set(x, 4, z, 'or:canvas')
                t.set(x, 5, z, slab('or:canvas_slab') if k == 2 else 'or:canvas')
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(1, 5):
            t.set(x, y, z, S('or:durasteel_beam', axis='y'))
    for z in (6, 7):
        for y in (1, 2):
            t.set(x1, y, z, C.AIR)
    t.set(6, 1, 6, 'or:durasteel_deck')
    t.set(7, 1, 6, 'or:durasteel_deck')
    t.set(6, 2, 6, S('or:holo_screen', facing='south'))
    t.set(7, 2, 6, S('or:console', facing='south'))
    t.set(5, 1, 6, 'minecraft:cartography_table')
    t.chest(4, 1, 9, facing='east', loot='chests/sith_tomb')
    t.anchor('CHEST_1', 4, 1, 9)
    t.set(4, 1, 8, 'or:cargo_crate_imperial')
    t.set(4, 2, 8, 'or:cargo_crate_imperial')
    t.set(10, 1, 4, S('barrel', facing='up', open=False))
    t.set(4, 1, 4, 'or:cargo_crate_imperial')
    t.set(8, 3, 6, 'or:light_panel')
    mod.banner(t, 7, 3, 4, 'south', base='black')
    mod.banner(t, 5, 3, 4, 'south', base='red')
    # barracks: two tents, bedrolls, a rack of kit
    for zc in (19, 25):
        a_tent(t, 3, 9, zc, roof='or:canvas', end='or:canvas_crimson', open_end='x1')
        for x in (4, 6, 8):
            t.set(x, 1, zc - 1, 'minecraft:red_carpet' if x != 6 else 'minecraft:black_carpet')
        t.set(4, 1, zc + 1, S('barrel', facing='up', open=False))
    # the supply depot (north-east): crates in stacks, fuel, the generator humming under a tarp
    for (x, z, h) in ((19, 3, 3), (20, 3, 2), (21, 3, 3), (19, 4, 2), (24, 3, 1), (25, 3, 2), (26, 4, 1),
                      (20, 5, 1), (25, 5, 2)):
        for y in range(1, h + 1):
            t.set(x, y, z, 'or:cargo_crate_imperial' if (x + y + z) % 3 else 'or:cargo_crate')
    for (x, z) in ((22, 7), (23, 7), (22, 8)):
        t.set(x, 1, z, S('barrel', facing='up', open=False))
    t.set(26, 1, 8, 'or:durasteel_vent')
    t.set(27, 1, 8, 'or:durasteel_plating')
    t.set(26, 2, 8, S('or:durasteel_pipes', axis='x'))
    t.set(27, 2, 8, 'or:light_panel_amber')
    t.set(25, 1, 8, S('or:console', facing='west'))
    for x in range(18, 28):
        for z in range(2, 10):
            if x in (18, 27) and z in (2, 9):
                for y in range(1, 5):
                    t.set(x, y, z, S('or:durasteel_beam', axis='y'))
            if not (x in (18, 27) and z in (2, 9)):
                t.set(x, 5, z, slab('or:canvas_slab'))
    # the comms mast, its console and a red light on top
    for y in range(1, 10):
        t.set(15, y, 7, S('iron_bars', north=False, south=False, east=False, west=False, waterlogged=False))
    t.set(15, 10, 7, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    t.set(15, 9, 7, 'or:light_panel_red')
    t.set(15, 1, 8, S('or:console', facing='south'))
    t.set(14, 1, 7, 'or:cargo_crate_imperial')
    # the yard: cook fire and seats, a water drum
    t.set(14, 1, 17, S('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
    t.set(12, 1, 17, S('stripped_spruce_log', axis='z'))
    t.set(16, 1, 18, S('stripped_spruce_log', axis='x'))
    t.set(14, 1, 19, 'minecraft:water_cauldron[level=3]')
    for y in (1, 2):
        t.set(19, y, 15, C.AIR)
    mod.spawner(t, 19, 1, 15, 'dig_team', anchor='SQUAD')
    # the finds (south-east): a test square dug into the sand inside a rim of spoil, roped off
    for x in range(20, 28):
        for z in range(22, 27):
            inner = 21 <= x <= 26 and 23 <= z <= 25
            if inner:
                t.set(x, 0, z, 'minecraft:suspicious_sand' if (x * 5 + z) % 4 == 0 else
                      ('minecraft:coarse_dirt' if (x + z) % 2 else 'minecraft:sand'))
            elif not (x == 20 and z == 24):
                t.set(x, 1, z, 'minecraft:gravel' if (x + 2 * z) % 3 == 0 else 'minecraft:coarse_dirt')
                if (x + z) % 4 == 0:
                    t.set(x, 2, z, S('spruce_fence', waterlogged=False))
    for x in range(16, 20):
        for z in (27, 29):
            r = (x * 3 + z) % 4
            t.set(x, 1, z, 'or:tomb_stone_carved' if r == 0 else slab('or:tomb_brick_slab') if r == 1 else
                  stairs('or:tomb_brick_stairs', 'north') if r == 2 else 'or:debris')
    t.set(22, 1, 28, 'minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]')
    t.set(22, 2, 28, S('spruce_trapdoor', facing='north', half='bottom', open=False, powered=False, waterlogged=False))
    t.set(23, 1, 28, 'minecraft:suspicious_sand')
    t.set(24, 1, 28, 'minecraft:sand')
    for (x, z) in ((25, 28), (26, 28)):
        t.set(x, 1, z, 'or:cargo_crate')
    # floodlights at the corners of the yard
    for (x, z) in ((2, 13), (28, 2), (28, 29), (12, 29)):
        for y in range(1, 5):
            t.set(x, y, z, S('or:durasteel_beam', axis='y'))
        t.set(x, 5, z, 'or:light_panel_amber')
    # sandbag barricades along the north and south edges, with gaps
    for (xa, xb, z) in ((13, 17, 1), (3, 9, 29), (14, 19, 29)):
        for x in range(xa, xb + 1):
            t.set(x, 1, z, 'minecraft:sandstone_wall[up=true,north=none,south=none,east=low,west=low,waterlogged=false]'
                  if x not in (xa, xb) else 'minecraft:sandstone')
    t.jigsaw(30, 1, 15, 'east_up', name='camp_entry', target='path_out', pool='minecraft:empty')
    C.air_up(t, 0, 0, 30, 30, 1)
    mod.light_fill(t, region=(4, 1, 4, 10, 3, 9), level=8, strength=11)
    return t


def pilgrim_niche(seed=62060):
    """The Wayfarer's alcove behind the processional's veiled stones: a pilgrim's rest in the black rock,
    the pedestal under a soul lantern. 9 x 7 x 9; the jigsaw at (0, 1, 4)."""
    t = C.Template(9, 7, 9, seed=seed, name='sith_tomb/pilgrim_niche')
    m = mats(seed)
    t.fill(0, 0, 0, 8, 6, 8, m['rough'])
    for x in range(1, 8):
        for z in range(1, 8):
            t.set(x, 0, z, m['pave'](x, 0, z))
            for y in range(1, 5):
                t.set(x, y, z, C.AIR)
    for y in (1, 2):
        t.set(0, y, 4, C.AIR)
    for x in range(1, 8):
        t.set(x, 4, 1, stairs('or:tomb_brick_stairs', 'north', half='top'))
        t.set(x, 4, 7, stairs('or:tomb_brick_stairs', 'south', half='top'))
    for z in range(2, 7):
        t.set(1, 4, z, stairs('or:tomb_brick_stairs', 'west', half='top'))
    # the pedestal on a carved step at the east end, soul fire either side
    for z in range(2, 7):
        t.set(7, 0, z, 'or:tomb_stone_carved')
    mod.pedestal(t, 6, 1, 4, 'wayfarer', always_ready=True, anchor='PEDESTAL_WAYFARER')
    t.set(7, 1, 4, 'or:tomb_stone_carved')
    t.set(7, 2, 4, S('soul_lantern', hanging=False, waterlogged=False))
    for z in (2, 6):
        t.set(7, 1, z, 'or:tomb_stone_carved')
        t.set(7, 2, z, S('soul_campfire', facing='west', lit=True, signal_fire=False, waterlogged=False))
    # the pilgrim's rest: bedroll, pack, a cold fire ring, candles
    t.set(2, 1, 1, 'minecraft:gray_carpet')
    t.set(3, 1, 1, 'minecraft:gray_carpet')
    t.set(4, 1, 1, 'minecraft:white_carpet')
    t.set(1, 1, 1, S('barrel', facing='up', open=False))
    t.set(3, 1, 6, S('campfire', facing='north', lit=False, signal_fire=False, waterlogged=False))
    red_candle(t, 5, 1, 7 - 1, n=2)
    t.set(1, 1, 7 - 1, S('bone_block', axis='y'))
    t.set(2, 3, 7, 'minecraft:cobweb')
    t.jigsaw(0, 1, 4, 'west_up', name='niche_entry', target='proc_niche', pool='minecraft:empty')
    mod.light_fill(t, region=(1, 1, 1, 7, 3, 7), level=8, strength=11)
    return t


def rubble_room(seed=62070):
    """A side vault half fallen in: its ceiling broke and sand poured through, pots lie smashed, the cache
    and two funerary urns sit in the dry corners, and the doorway is choked by collapsed rubble that only a
    Lift clears (spec 3.2, as the old room: two rubble blocks inside the two-thick door, (9, 1..2, 5)).
    11 x 8 x 11; the jigsaw on the east face (10, 1, 5)."""
    t = C.Template(11, 8, 11, seed=seed, name='sith_tomb/rubble_room')
    m = mats(seed)
    rng = t.rng
    t.fill(0, 0, 0, 10, 7, 10, m['rough'])
    for x in range(1, 10):
        for z in range(1, 10):
            t.set(x, 0, z, m['pave'](x, 0, z))
            for y in range(1, 6):
                t.set(x, y, z, C.AIR)
    for y in (1, 2):
        t.set(10, y, 5, C.AIR)
    # pilasters and a carved course
    for (x, z) in ((1, 3), (1, 7), (9, 3), (9, 7), (3, 1), (7, 1), (3, 9), (7, 9)):
        for y in range(1, 6):
            t.set(x, y, z, S('or:tomb_pillar', axis='y') if y < 5 else 'or:tomb_stone_carved')
    # the break in the ceiling and the sand cone under it
    for x in range(3, 7):
        for z in range(3, 7):
            d = math.hypot(x - 4.5, z - 4.5)
            if d < 1.6:
                t.set(x, 6, z, C.AIR)
                t.set(x, 7, z, 'minecraft:sand')
            h = int(round(3.2 - d * 1.2))
            for y in range(1, 1 + max(0, h)):
                t.set(x, y, z, 'minecraft:sand' if rng.random() > 0.08 else 'minecraft:suspicious_sand')
    for (x, z) in ((2, 6), (7, 3), (3, 8), (6, 7), (8, 6)):
        rubble(t, x, 1, z, rng)
    t.set(2, 1, 2, 'or:tomb_stone_cracked')
    # the doorway: the wall thickened round it and the fallen stone jammed in it
    for z in (4, 6):
        for y in (1, 2, 3):
            t.set(9, y, z, m['rough'](9, y, z))
    t.set(9, 3, 5, 'or:tomb_stone_carved')
    for y in (1, 2):
        t.set(9, y, 5, S('or:collapsed_rubble', cleared=False, age=0))
    # the cache in the dry corner, the two urns, a smashed pot, a soul lantern
    t.chest(1, 1, 5, facing='east', loot='chests/force_cache')
    t.anchor('CACHE', 1, 1, 5)
    urn(t, 1, 1, 8)
    urn(t, 8, 1, 1)
    pot(t, 1, 1, 2, facing='east', cracked=True)
    t.set(8, 4, 8, S('soul_lantern', hanging=True, waterlogged=False))
    t.set(8, 5, 8, S('iron_chain', axis='y', waterlogged=False))
    t.set(1, 4, 1, 'minecraft:cobweb')
    t.set(9, 4, 9, 'minecraft:cobweb')
    t.jigsaw(10, 1, 5, 'east_up', name='rubble_entry', target='proc_rubble', pool='minecraft:empty')
    mod.light_fill(t, region=(1, 1, 1, 9, 4, 9), level=8, strength=11)
    return t


# ---------------------------------------------------------------- build

def build(a):
    out = {}
    if C.wanted(a, 'gate') and not (a.only or '').endswith('_2'):
        g = gate_v1()
        probs = C.route_problems(g.t, (CX, ST, 3), [(CX, PIT_ST, 20), (CX, 1, GS[2] - 1), (2, ST, 5), (16, PIT_ST, 18)],
                                 label='gate')
        for p in probs:
            print('CHECK', p)
        out[g.tid] = C.save(g.t, g.tid)
    if C.wanted(a, 'gate_2'):
        g = gate_v2()
        probs = C.route_problems(g.t, (CX, ST, 2), [(CX, PIT_ST, 16), (CX, 1, GS[2] - 1), (2, ST, 5)], label='gate_2')
        for p in probs:
            print('CHECK', p)
        out[g.tid] = C.save(g.t, g.tid)
    if C.wanted(a, 'path'):
        out['sith_tomb/path'] = C.save(path_piece(), 'sith_tomb/path')
    if C.wanted(a, 'dig_camp'):
        t = dig_camp()
        for p in C.route_problems(t, (29, 1, 15), [(19, 1, 16), (8, 1, 7), (6, 1, 19), (22, 1, 27)], label='camp'):
            print('CHECK', p)
        out['sith_tomb/dig_camp'] = C.save(t, 'sith_tomb/dig_camp')
    if C.wanted(a, 'pilgrim_niche'):
        out['sith_tomb/pilgrim_niche'] = C.save(pilgrim_niche(), 'sith_tomb/pilgrim_niche')
    if C.wanted(a, 'rubble_room'):
        t = rubble_room()
        # in through the doorway once the Lift cleared its rubble: the chest's front and both urns
        goals = [(2, 1, 5), (2, 1, 8), (8, 1, 2)]
        rubble = [(9, 1, 5), (9, 2, 5)]
        for p in C.route_problems(t, (10, 1, 5), goals, passable=rubble, label='rubble_room'):
            print('CHECK', p)
        if not C.route_problems(t, (10, 1, 5), goals[:1], label='rubble_room shut'):
            print('CHECK rubble_room: the cache is reachable with the doorway still choked')
        out['sith_tomb/rubble_room'] = C.save(t, 'sith_tomb/rubble_room')
    if C.wanted(a, 'processional'):
        t = processional()
        out['sith_tomb/processional'] = C.save(t, 'sith_tomb/processional')
    if C.wanted(a, 'burial_chamber'):
        t = burial_chamber()
        out['sith_tomb/burial_chamber'] = C.save(t, 'sith_tomb/burial_chamber')
    for k, t in out.items():
        print(k, t.size, t.count())
    return out


def main():
    build(C.args())


if __name__ == '__main__':
    main()
