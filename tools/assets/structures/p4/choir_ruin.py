"""tyrvann/choir_ruin (worldgen, Tyrvann stormwood and basalt; docs/MILESTONE_6.md 5.6): the Hymnsworn's
choir hall, fallen in.

The Hymn built its choir halls in its own black masonry: a long hall on a deep stepped podium, a pointed
vault on ribs that come down onto basalt pilasters, buttresses down the flanks, two song towers at the
front corners crowned with copper pipes the storm plays, a portal with stormglass grown over its keystone.
Inside, the choir stalls rise in three tiers along both walls, every seat turned to the empty floor
between them; at the far end, under a half dome, the cantor's dais and the organ: a fan of verdigris
pipes and three basalt voice columns. The Hymnsworn still keep it (a site spawner, hymnsworn) and still
light it (soul lanterns on the ribs, black candles on the stall ends); the cantor's chest is on the dais.

Two starts:
  hall_a  the vault fell in over the middle bays: one rib still stands across the gap as a free arch, the
          east wall went with it, and a stormwood grows out of the rubble mound in the choir.
  hall_b  the apse fell: the half dome is gone and the organ stands open to the sky (lightning has found
          it, stormglass on its pipes); the west wall toppled outward and lies in courses on the ground,
          a dead stormwood beside it.

Frame (buried 2): y 0 footings, y 1 the ground (the apron), y 2 the walk outside; the podium's courses at
y 1..3, the hall floor at y 3 (walk 4).
"""
from common import *  # noqa: F401,F403

NAME = 'tyrvann/choir_ruin'
SALT = 960351
SIZE = (37, 27, 41)
BURIED = 2
GY, FY, PY = 1, 2, 3
X0, X1, Z0, Z1 = 11, 25, 7, 31            # side walls, facade, the arch to the apse
XC = 18
SPRING = 14
RIBS = (11, 15, 19, 23, 27)
AZ, AR = 31, 6.5                          # the apse
LOOT_ID = 'oldrepublic:chests/tyrvann/choir_ruin'
VARIANTS = ('a', 'b')
CAMERAS = {}
SPAWNERS = {'a': (18, PY + 1, 11), 'b': (18, PY + 1, 13)}


def vault_h(x):
    """The pointed vault's underside... its shell height over column x (the crown at XC)."""
    d = abs(x - XC)
    return SPRING + int(round(8 * (1 - (min(d, 7.5) / 7.5) ** 1.5)))


def shell_top(x):
    """Fill each shell column up to its neighbours so the vault has no gaps."""
    return max(vault_h(x), max(vault_h(x - 1), vault_h(x + 1)) - 1)


def apse_d(x, z):
    return math.hypot(x - XC, z - AZ)


def dome_h(x, z):
    d = apse_d(x, z)
    return 13 + int(round(math.sqrt(max(0.0, AR * AR - d * d)) * 0.9))


def mound(t, rng, cx, cz, r, h, y0, mats, top_mats):
    """A heap of fallen masonry on the floor at y0 (its first course at y0 + 1)."""
    cells = []
    for x in range(int(cx - r) - 1, int(cx + r) + 2):
        for z in range(int(cz - r) - 1, int(cz + r) + 2):
            d = math.hypot(x - cx, z - cz) + rng.random() * 0.8
            if d > r or not t.inside(x, y0 + 1, z):
                continue
            k = max(1, int(round(h * (1 - d / r) + rng.random() * 0.7)))
            for y in range(y0 + 1, y0 + 1 + k):
                if t.inside(x, y, z):
                    t.set(x, y, z, pick(rng, mats))
            if t.inside(x, y0 + 1 + k, z) and rng.random() < 0.5:
                t.set(x, y0 + 1 + k, z, pick(rng, top_mats))
            cells.append((x, z))
    return cells


def stormwood(t, rng, x, z, y0, height, crown=True, lean=(0, 0)):
    """A stormwood: dark oak trunk, a few limbs, a jungle-leaf crown (persistent) with vines; or a dead snag."""
    top = y0 + height
    for y in range(y0, top):
        ox, oz = (lean if y > y0 + height * 2 // 3 else (0, 0))
        t.set(x + ox, y, z + oz, log('minecraft:dark_oak_log'))
    ox, oz = lean
    limbs = [((1, 0), 'x'), ((-1, 0), 'x'), ((0, 1), 'z'), ((0, -1), 'z')]
    rng.shuffle(limbs)
    for i, ((dx, dz), ax) in enumerate(limbs[:3]):
        ly = top - 2 - i * 2
        for k in (1, 2):
            t.set(x + ox + dx * k, ly + (k - 1), z + oz + dz * k, log('minecraft:dark_oak_log', ax))
    if not crown:
        return
    leaf = S('jungle_leaves', distance=1, persistent=True, waterlogged=False)
    cx, cz = x + ox, z + oz
    for y in range(top - 3, top + 2):
        r = 3.6 if y < top else 2.6
        for xx in range(int(cx - r), int(cx + r) + 1):
            for zz in range(int(cz - r), int(cz + r) + 1):
                if math.hypot(xx - cx, zz - cz) <= r - rng.random() * 0.8 and t.inside(xx, y, zz) \
                        and t.is_air(xx, y, zz):
                    t.set(xx, y, zz, leaf)
    for xx in range(cx - 4, cx + 5):
        for zz in range(cz - 4, cz + 5):
            for y in range(top - 3, top + 1):
                if t.name_at(xx, y, zz) == 'minecraft:jungle_leaves' and rng.random() < 0.12:
                    n = rng.randint(1, 4)
                    for k in range(1, n + 1):
                        if not t.inside(xx, y - k, zz) or not t.is_air(xx, y - k, zz) or (xx, y - k, zz) in t.cells \
                                and t.name_at(xx, y - k, zz) != 'minecraft:air':
                            break
                        t.set(xx, y - k, zz, S('vine', north=False, south=False, east=False, west=False, up=True))
                    break


def build(variant):
    seed = SALT * 10 + VARIANTS.index(variant) + 1
    tid = NAME + '/hall_' + variant
    t = Template(*SIZE, seed=seed, name=tid)
    rng = t.rng
    sx, sy, sz = SIZE
    wall = HYMN.picker('wall', rng)
    rough = HYMN.picker('rough', rng)
    basalt = HYMN.picker('basalt', rng)
    worn = patch(Noise(seed + 4, 3.0), [('minecraft:packed_mud', 3), ('or:peat', 3), ('minecraft:cobbled_deepslate', 1),
                                        ('minecraft:gravel', 1), ('minecraft:tuff', 1), ('minecraft:moss_block', 1)],
                 jitter=Noise(seed + 5, 1.0), mix=0.45)
    jag = Noise(seed + 9, 2.5)
    rubble_mats = [('or:stormstone', 3), ('minecraft:cobbled_deepslate', 3), ('or:stormstone_bricks', 2),
                   ('minecraft:cracked_deepslate_bricks', 1), ('minecraft:tuff', 1)]
    rubble_tops = [(slab('or:stormstone_brick_slab'), 3), (stair('or:stormstone_brick_stairs', rng.choice(DIRS4)), 2),
                   ('minecraft:moss_carpet', 2), (slab('minecraft:cobbled_deepslate_slab'), 1)]
    # ---------------------------------------------------------------- ground, podium
    apron = superellipse(sx, sz, 18, 20.5, 17.6, 19.8, power=3.0, noise=Noise(seed + 1, 3.0), amp=0.08, freq=0.4)
    p_out = superellipse(sx, sz, XC, 21.5, 11.6, 18.6, power=7.0)
    p_in = superellipse(sx, sz, XC, 21.5, 10.6, 17.6, power=7.0)
    for (x, z) in sorted(apron | p_out):
        t.set(x, 0, z, rough(x, 0, z))
        t.set(x, GY, z, worn(x, GY, z) if (x, z) not in p_out else rough(x, GY, z))
    for (x, z) in sorted(p_out):
        t.set(x, FY, z, 'or:stormstone_bricks' if (x, z) not in p_in else rough(x, FY, z))
    edge_in = ring_of(p_in)
    flag_floor(t, sorted(p_in - edge_in), PY, seed + 11,
               [('minecraft:deepslate_tiles', 4), ('or:stormstone', 2), ('minecraft:polished_deepslate', 2),
                ('minecraft:smooth_basalt', 1)], lost=lambda x, z, h: h.random() < 0.07,
               lost_state=lambda x, z: 'minecraft:moss_block' if (x + z) % 3 else 'minecraft:cobbled_deepslate')
    for (x, z) in sorted(edge_in):
        t.set(x, PY, z, 'minecraft:polished_deepslate' if (x * 3 + z) % 5 else 'minecraft:deepslate_tiles')
    # the stair down to the ground at the front
    zf = min(z for (x, z) in p_in if x == XC)
    for x in range(XC - 2, XC + 3):
        for k, y in ((1, PY), (2, FY)):
            z = zf - k
            t.set(x, 0, z, rough(x, 0, z))
            for yy in range(GY, y):
                t.set(x, yy, z, rough(x, yy, z))
            t.set(x, y, z, stair('or:stormstone_brick_stairs', 'south'))
    for x in (XC - 3, XC + 3):
        for k in (1, 2):
            z = zf - k
            t.set(x, 0, z, rough(x, 0, z))
            for yy in range(GY, PY + 1):
                t.set(x, yy, z, 'or:stormstone_bricks')
        t.set(x, PY + 1, zf - 1, 'minecraft:polished_deepslate_wall')
        t.set(x, PY + 2, zf - 1, lantern(soul=True))
    # ---------------------------------------------------------------- the hall: walls, vault, ribs
    hall_x = range(X0, X1 + 1)
    for z in range(Z0, Z1 + 1):
        for x in (X0, X1):
            for y in range(PY + 1, vault_h(x) + 1):
                t.set(x, y, z, wall(x, y, z))
    for z in range(Z0 + 1, Z1):
        for x in range(X0 + 1, X1):
            for y in range(PY + 1, vault_h(x)):
                t.set(x, y, z, AIR)
            for y in range(vault_h(x), shell_top(x) + 1):
                t.set(x, y, z, wall(x, y, z))
    for z in (Z0, Z1):                                      # the facade and the back gable, full height
        for x in hall_x:
            for y in range(PY + 1, shell_top(x) + 1):
                t.set(x, y, z, wall(x, y, z))
    for z in RIBS:
        for x in range(X0 + 1, X1):
            for y in range(vault_h(x), shell_top(x) + 1):
                t.set(x, y, z, 'minecraft:polished_deepslate')
            t.set(x, vault_h(x) - 1, z, 'minecraft:polished_deepslate')
        for x in (X0 + 1, X1 - 1):
            for y in range(PY + 1, vault_h(x) - 1):
                t.set(x, y, z, 'minecraft:polished_basalt[axis=y]')
            t.set(x, PY + 1, z, 'minecraft:chiseled_deepslate')
        for (bx, face) in ((X0 - 1, 'east'), (X1 + 1, 'west')):   # buttresses
            for y in range(PY + 1, SPRING - 1):
                t.set(bx, y, z, rough(bx, y, z))
            t.set(bx, SPRING - 1, z, stair('or:stormstone_brick_stairs', face))
            bx2 = bx + (-1 if face == 'east' else 1)
            for y in range(PY + 1, PY + 6):
                t.set(bx2, y, z, rough(bx2, y, z))
            t.set(bx2, PY + 6, z, stair('or:stormstone_brick_stairs', face))
    def topcol(x):
        return vault_h(x) if x in (X0, X1) else shell_top(x)
    for z in range(Z0, Z1 + 1):                             # tile the vault's outside where it steps
        for x in range(X0, X1 + 1):
            if x == XC:
                continue
            inner = x + 1 if x < XC else x - 1
            if topcol(inner) > topcol(x) and t.is_air(x, topcol(x) + 1, z):
                t.set(x, topcol(x) + 1, z, stair('minecraft:deepslate_tile_stairs', 'east' if x < XC else 'west'))
        for x in (X0, X1):                                  # a plinth course and a cornice down the flanks
            t.set(x, PY + 1, z, 'minecraft:polished_deepslate')
            t.set(x, SPRING - 1, z, 'minecraft:polished_deepslate')
    for z in RIBS[::2]:                                     # the storm's pipes on every other buttress
        for bx in (X0 - 1, X1 + 1):
            for k in range(2 + (z % 3)):
                t.set(bx, SPRING + k, z, rod())
    for z in range(Z0 + 2, Z1 - 1):                        # lancets between the buttresses
        if z in RIBS or (z - 1) in RIBS or (z + 1) in RIBS:
            continue
        for x in (X0, X1):
            kind = 'bars' if (x + z) % 3 else 'broken'
            for k in range(4):
                t.set(x, PY + 4 + k, z, S('iron_bars') if kind == 'bars' else AIR)
            t.set(x, PY + 8, z, AIR)
    # the facade: the portal, its keystone, the throat window, pilasters, the song towers
    for x in range(XC - 1, XC + 2):
        for y in range(PY + 1, PY + 6):
            t.set(x, y, Z0, AIR)
    t.set(XC - 1, PY + 6, Z0, stair('or:stormstone_brick_stairs', 'west', 'top'))
    t.set(XC + 1, PY + 6, Z0, stair('or:stormstone_brick_stairs', 'east', 'top'))
    t.set(XC, PY + 6, Z0, AIR)
    t.set(XC, PY + 7, Z0, 'minecraft:chiseled_deepslate')
    t.set(XC, PY + 7, Z0 - 1, cluster('north'))
    for y in range(PY + 10, PY + 16):
        t.set(XC, y, Z0, S('iron_bars'))
    for x in (XC - 3, XC + 3):
        for y in range(PY + 1, PY + 13):
            t.set(x, y, Z0 - 1, 'minecraft:polished_basalt[axis=y]')
        t.set(x, PY + 13, Z0 - 1, stair('or:stormstone_brick_stairs', 'south'))
    for (tx, tz) in ((X0 - 2, Z0 - 2), (X1 + 1, Z0 - 2)):
        for x in (tx, tx + 1):
            for z in (tz, tz + 1):
                for y in range(PY + 1, 21):
                    t.set(x, y, z, 'minecraft:oxidized_cut_copper' if y in (10, 16) else basalt(x, y, z))
        for (x, z, h) in ((tx, tz, 3), (tx + 1, tz, 2), (tx, tz + 1, 1), (tx + 1, tz + 1, 4)):
            for k in range(h):
                t.set(x, 21 + k, z, rod())
        t.set(tx, 20, tz - 1, cluster('north'))
    # ---------------------------------------------------------------- the apse: dome, dais, organ
    apse = {(x, z) for x in range(XC - 7, XC + 8) for z in range(AZ, sz) if apse_d(x, z) <= AR}
    apse_in = {(x, z) for (x, z) in apse if apse_d(x, z) <= AR - 1}
    for (x, z) in sorted(apse):
        if z <= Z1:
            continue
        for y in range(GY, PY + 1):
            t.set(x, y, z, rough(x, y, z) if y < PY else 'minecraft:polished_deepslate')
        t.set(x, 0, z, rough(x, 0, z))
        if (x, z) not in apse_in:
            for y in range(PY + 1, 14):
                t.set(x, y, z, wall(x, y, z))
        else:
            for y in range(PY + 1, dome_h(x, z)):
                t.set(x, y, z, AIR)
        top = dome_h(x, z)
        nb = max([dome_h(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                  if (x + dx, z + dz) in apse] + [top])
        for y in range(top, max(top, nb - 1) + 1):
            t.set(x, y, z, wall(x, y, z))
    for x in range(XC - 5, XC + 6):                          # the arch from the hall into the apse
        for y in range(PY + 1, PY + 1 + int(round(9 * (1 - (abs(x - XC) / 5.6) ** 1.6)))):
            t.set(x, y, Z1, AIR)
    for x in range(XC - 2, XC + 3):
        t.set(x, PY + 1, Z1, stair('minecraft:polished_deepslate_stairs', 'south'))
    for (x, z) in sorted(apse_in):
        if z > Z1:
            t.set(x, PY + 1, z, 'minecraft:polished_deepslate')
    organ = sorted((x, z) for (x, z) in apse_in if z > Z1 and 3.6 <= apse_d(x, z))
    voices = [(XC - 2, AZ + 4), (XC, AZ + 4), (XC + 2, AZ + 4)]
    for (x, z) in organ:
        if (x, z) in voices:
            continue
        h = 9 - abs(x - XC) + (1 if (x + z) % 2 else 0)
        t.set(x, PY + 2, z, 'minecraft:oxidized_cut_copper' if (x + z) % 2 else 'minecraft:oxidized_copper')
        for k in range(max(2, h)):
            t.set(x, PY + 3 + k, z, rod())
    for (x, z) in voices:
        for y in range(PY + 2, PY + 11 + (1 if x == XC else 0)):
            t.set(x, y, z, 'minecraft:oxidized_cut_copper' if y in (PY + 5, PY + 9) else 'minecraft:polished_basalt[axis=y]')
        t.set(x, PY + 11 + (1 if x == XC else 0), z, cluster('up'))
    t.set(XC, PY + 2, AZ + 1, S('lectern', facing='south', has_book=False, powered=False))
    t.chest(XC - 3, PY + 2, AZ + 1, 'east', loot=LOOT_ID)
    for (x, z) in ((XC + 3, AZ + 1), (XC - 1, AZ + 2), (XC + 1, AZ + 2)):
        t.set(x, PY + 2, z, candle(rng.randint(2, 4), 'black', lit=True))
    hanging_lantern(t, XC, dome_h(XC, AZ + 2), AZ + 2, 4, soul=True, copper_chain=True)
    # ---------------------------------------------------------------- the choir: three tiers each side
    for z in range(Z0 + 1, Z1):
        for (x1, x2, x3, face) in ((X0 + 3, X0 + 2, X0 + 1, 'west'), (X1 - 3, X1 - 2, X1 - 1, 'east')):
            if z in RIBS:
                t.set(x1, PY + 1, z, 'minecraft:polished_deepslate')
                t.set(x2, PY + 1, z, 'minecraft:polished_deepslate')
                t.set(x2, PY + 2, z, 'minecraft:polished_deepslate')
                t.set(x1, PY + 2, z, candle(rng.randint(1, 3), 'black', lit=rng.random() < 0.6))
                continue
            if z in (Z0 + 1, Z1 - 1):
                continue
            t.set(x1, PY + 1, z, stair('or:stormstone_brick_stairs', face))
            t.set(x2, PY + 1, z, 'or:stormstone_bricks')
            t.set(x2, PY + 2, z, stair('or:stormstone_brick_stairs', face))
            t.set(x3, PY + 1, z, 'or:stormstone_bricks')
            t.set(x3, PY + 2, z, 'or:stormstone_bricks')
            t.set(x3, PY + 3, z, stair('or:stormstone_brick_stairs', face))
    for z in RIBS:
        hanging_lantern(t, XC, vault_h(XC) - 1, z, 7, soul=True, copper_chain=True)
    # ---------------------------------------------------------------- the fall
    if variant == 'a':
        for (x, y, z) in sorted(p for p in t.cells if p[1] >= PY + 5 and X0 - 2 <= p[0] <= X1 + 3):
            reach = 4.6 + 1.6 * jag.at(x, y)
            if abs(z - 19) > reach or z in (19,) and x <= X1 - 1:
                continue
            if y >= vault_h(min(max(x, X0), X1)) - 1 or (x >= X1 - 1 and y >= PY + 5 + int(4 * jag.at(z, y))):
                if rng.random() < 0.95:
                    t.set(x, y, z, AIR)
        for (x, z) in [(x, z) for x in range(X1 - 1, X1 + 1) for z in range(15, 24) if z != 19]:
            for y in range(PY + 9, SPRING + 3):
                if rng.random() < 0.7:
                    t.set(x, y, z, AIR)
        heap = mound(t, rng, XC - 1, 18.5, 4.6, 3, PY, rubble_mats, rubble_tops)
        mound(t, rng, X1 - 3, 21, 3.0, 2, PY, rubble_mats, rubble_tops)
        mound(t, rng, X0 + 4, 16, 2.2, 1, PY, rubble_mats, rubble_tops)
        for _ in range(16):                                    # what fell outward from the east wall
            x, z = rng.randint(X1 + 2, X1 + 8), rng.randint(14, 24)
            rubble(t, rng, x, PY + 2, z, rubble_mats + rubble_tops, n=1, spread=0.5)
        tx, tz = XC - 2, 21
        hy = PY + 1
        while t.is_solid(tx, hy, tz):
            hy += 1
        stormwood(t, rng, tx, tz, hy, 16, crown=True)
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1)):
            root_veins(t, rng, (tx + dx, hy, tz + dz), 4)
        CAMERAS[tid] = ((XC, PY + 2, Z0 + 2), (XC - 2, 15, 24))
    else:
        for (x, z) in sorted(apse):                             # the half dome and the apse wall's top
            if z <= Z1 - 1:
                continue
            top = PY + 4 + int(round(5 * jag.at(x, z)))
            for y in range(top, sy):
                if (x, y, z) in t.cells and t.name_at(x, y, z) not in ('minecraft:chest',):
                    if (x, z) in apse_in and t.name_at(x, y, z) in ('minecraft:oxidized_lightning_rod',
                                                                   'minecraft:polished_basalt',
                                                                   'minecraft:oxidized_cut_copper',
                                                                   'oldrepublic:stormglass_cluster'):
                        continue
                    t.set(x, y, z, AIR)
        for (x, y, z) in sorted(p for p in t.cells if p[1] >= PY + 6 and X0 - 2 <= p[0] <= X1 + 2 and 25 <= p[2] <= Z1):
            if p_reach(z, x, jag) and rng.random() < 0.9:
                t.set(x, y, z, AIR)
        for z in range(10, 21):                                 # the west wall toppled outward
            stump = PY + 1 + int(round(2.5 * jag.at(z, 3.0)))
            for y in range(stump, SPRING + 4):
                for x in range(X0 - 2, XC):
                    if (x, y, z) in t.cells and (x > X0 or y >= stump) and (x <= X0 or y >= vault_h(x) - 1):
                        t.set(x, y, z, AIR)
            for k in range(1, 9):                               # its courses lying on the ground
                x = X0 - 2 - k
                if not t.inside(x, FY, z) or rng.random() < 0.25:
                    continue
                gy = GY
                while t.is_solid(x, gy + 1, z):
                    gy += 1
                t.set(x, gy + 1, z, 'or:stormstone_bricks' if rng.random() < 0.7 else pick(rng, rubble_tops))
        mound(t, rng, X0 + 3, 15, 3.4, 2, PY, rubble_mats, rubble_tops)
        mound(t, rng, XC + 2, 28.5, 3.2, 2, PY, rubble_mats, rubble_tops)
        for (x, z) in organ[::3]:                               # the storm found the organ
            y = sy - 1
            while y > PY and not t.is_solid(x, y, z):
                y -= 1
            if y > PY + 2 and t.is_air(x, y + 1, z):
                t.set(x, y + 1, z, cluster('up'))
        stormwood(t, rng, 4, 27, FY, 13, crown=False, lean=(0, 1))
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            root_veins(t, rng, (4 + dx, FY, 27 + dz), 4)
        CAMERAS[tid] = ((XC, PY + 2, Z0 + 2), (XC, PY + 8, AZ + 3))
    # ---------------------------------------------------------------- the spawner, the camp they keep
    sxp, syp, szp = SPAWNERS[variant]
    mod.spawner(t, sxp, syp, szp, 'hymnsworn', count=4)
    for (x, z) in ((XC - 2, Z0 + 3), (XC + 2, Z0 + 3)):
        t.set(x, PY + 1, z, 'minecraft:polished_deepslate_wall')
        t.set(x, PY + 2, z, lantern(soul=True))
    # ---------------------------------------------------------------- overgrowth, finish
    yard = [(x, z) for (x, z) in sorted(apron | p_out)]
    for (x, z) in yard:
        top = max([y for y in range(sy) if t.is_solid(x, y, z)] + [0])
        for y in range(top + 1, min(sy, top + 7)):
            if (x, y, z) not in t.cells:
                t.set(x, y, z, AIR)
    for (x, z) in sorted(p_in):
        if t.is_air(x, PY + 1, z) and t.is_solid(x, PY, z) and rng.random() < 0.06:
            t.set(x, PY + 1, z, 'minecraft:moss_carpet')
    for (x, z) in sorted(apron - p_out):
        if t.is_air(x, FY, z) and rng.random() < 0.1:
            below = t.name_at(x, GY, z)
            if below == 'minecraft:moss_block':
                t.set(x, FY, z, pick(rng, [('minecraft:fern', 2), ('minecraft:short_grass', 2), ('minecraft:moss_carpet', 1)]))
            else:
                t.set(x, FY, z, 'minecraft:moss_carpet')
    hang_moss(t, rng, ((X0 - 1, PY + 4, Z0), (X1 + 1, sy - 1, sz - 1)), chance=0.1, max_len=4)
    vines_on(t, rng, ((X0 - 3, PY + 1, Z0 - 3), (X1 + 3, sy - 1, sz - 1)), chance=0.07, max_len=4)
    for k in range(3):
        root_veins(t, rng, (rng.choice((X0, X1)), rng.randint(SPRING - 2, SPRING + 1), rng.randint(Z0 + 2, Z1 - 2)), 7)
    prune_floating(t)
    guard_trees(t, columns=p_out | set((x, z) for x in range(X0 - 3, X1 + 4) for z in range(Z0 - 3, sz)))
    check(t, variant)
    return t


def p_reach(z, x, jag):
    return z >= 25 + int(round(2 * jag.at(x, 7.0)))


def check(t, variant):
    eye = CAMERAS[t.name][0]
    assert t.name_at(*eye) == 'minecraft:air', ('camera eye not air', t.name, eye, t.name_at(*eye))
    x, y, z = SPAWNERS[variant]
    assert t.name_at(x, y, z) == 'oldrepublic:site_spawner' and t.is_air(x, y + 1, z), 'spawner'
    require_reach(t, (XC, FY, 1), [('nave', (XC, PY + 1, Z0 + 3), 'feet'), ('spawner', (x, y, z), 'touch'),
                                   ('chest', (XC - 3, PY + 2, AZ + 1), 'touch')], label=t.name)


def write_worldgen(ts):
    els = [W.element(NAME + '/hall_' + v, adapted=dict(support_depth=10, clear_from=SIZE[1], clear_inset=0,
                                                       yard_stand=FY, ramp_rings=2)) for v in VARIANTS]
    W.pool(NAME + '/start', els)
    W.structure(NAME, NAME + '/start', 'has_structure/' + NAME, size=1, max_distance=32, start_height=1 - BURIED)
    W.structure_set(NAME, [(NAME, 1)], 40, 14, SALT, placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:tyrvann_stormwood', 'oldrepublic:tyrvann_basalt'])
    loot_table('chests/tyrvann/choir_ruin', [
        ((2, 4), [('oldrepublic:stormglass_shard', 5, 1, 3), ('black_candle', 4, 1, 4), ('oldrepublic:lumen_thread', 3, 1, 2),
                  ('oldrepublic:credit_chit', 4, 1, 4), ('bone', 3, 1, 3), ('copper_ingot', 3, 2, 5),
                  ('paper', 2, 1, 3)]),
        (1, [('echo_shard', 1, 1, 1), ('oldrepublic:credit_chit', 3, 3, 6), ('disc_fragment_5', 1, 1, 1),
             ('amethyst_shard', 2, 1, 3)]),
    ])


def main(opts):
    ts = {}
    for v in VARIANTS:
        tid = NAME + '/hall_' + v
        t = build(v)
        ts[tid] = t
        if wanted(opts, tid):
            save(t, tid, opts, views=('se', 'nw'), plans=[GY, PY + 1, PY + 5])
    write_worldgen(ts)
    res = jigsaw.assemble(NAME + '/start', max_distance=32, size=1, start_height=1 - BURIED)
    if res.problems:
        print(res.report())
        raise SystemExit('jigsaw problems in %s' % NAME)
    return ts


if __name__ == '__main__':
    main(args())
