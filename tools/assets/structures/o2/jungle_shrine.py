"""oldrepublic:wilds/jungle_shrine: a Jedi shrine the jungle has taken back, built round its pool.

Run: python3 tools/assets/structures/o2/jungle_shrine.py [--preview DIR]

Two start variants, both 37 x 22 x 37 and buried 3 (the ground's top block at template y 2, feet
at y 3); the clearing is levelled into the jungle floor, ragged at its rim.

* wilds/jungle_shrine/pool_court: a long reflecting pool between two colonnades (some columns
  fallen, one lying in the water), entered through a gate whose lintel has come down. At the far
  end three steps climb to the shrine: an open-fronted hall under a stepped roof, the statue of a
  Jedi holding a lit blade, the altar with its candles and the keeper's offering chest, lumen moss
  glowing on the floor. A jungle tree has grown up through the hall's corner and split its wall;
  vines hang from everything.
* wilds/jungle_shrine/sunken_dome: a round shrine under a dome, its north-east quarter fallen in
  where a great tree forced its roots through the wall; the rubble lies on the floor in the light
  from the hole. Inside, four columns stand round a round pool under the oculus; at the pool's edge
  the meditation stone, and in a niche in the south wall the shrine's chest. A portico with steps
  on the north, vines down the broken edge of the dome, moss and lumen moss in the cracks.

The meditation stone is the real block (an Independent can respec beside it). Worldgen: the
structure (no monsters), its start pool, the set (spacing 48, separation 18, salt 960109, 4 chunks
from the ruins), the biome tag (#minecraft:is_jungle) and the loot table chests/wilds/jungle_shrine.
"""
import math

from common import (Template, S, AIR, B, Noise, pick, patches, stairs, slab, lantern, light_rooms, adapted,
                    wilds_family, loot_item, loot_table, cli, save, ring_of, carve, end_rod, HORIZONTAL)

SX, SY, SZ = 37, 22, 37
BURIED = 3
G = 3        # feet on the floor
SURF = 2     # the floor's top block
LOOT = 'chests/wilds/jungle_shrine'

FILL = [('dirt', 6), ('rooted_dirt', 2), ('coarse_dirt', 1)]
TOP = [('grass_block[snowy=false]', 6), ('podzol[snowy=false]', 3), ('moss_block', 2), ('coarse_dirt', 1), ('rooted_dirt', 1)]
RUIN = [('or:ruin_stone', 5), ('or:ruin_bricks', 4), ('or:ruin_stone_mossy', 4), ('mossy_stone_bricks', 2), ('or:ruin_stone_cracked', 1)]
PAVE = [('or:ruin_bricks', 4), ('or:ruin_stone_mossy', 3), ('or:ruin_stone', 2), ('mossy_cobblestone', 1), ('moss_block', 1)]
STONE_NAMES = ('oldrepublic:ruin_stone', 'oldrepublic:ruin_bricks', 'oldrepublic:ruin_stone_mossy', 'minecraft:mossy_stone_bricks',
               'oldrepublic:ruin_stone_cracked', 'oldrepublic:ruin_pillar', 'oldrepublic:ruin_stone_carved')
LEAVES = S('jungle_leaves', distance=1, persistent=True, waterlogged=False)


def empty(t, x, y, z):
    s = t.get(x, y, z)
    return s is None or B.name_of(s) == 'minecraft:air'


# ---------------------------------------------------------------- ground and growth

def ground(t, seed):
    """The clearing: earth to y 1 and the jungle floor at y 2, ragged in the outer three rings."""
    top = patches(seed, TOP, 3.0)
    fill = patches(seed + 1, FILL, 3.0)
    n = Noise(seed + 2, 3.0)
    for x in range(SX):
        for z in range(SZ):
            r = ring_of(t, x, z)
            if r < 3 and n.at(x, 0, z) < (3 - r) * 0.25:
                continue
            for y in range(0, SURF):
                t.set(x, y, z, fill(x, y, z))
            t.set(x, SURF, z, top(x, SURF, z))


def pave(t, seed, cells, moss=0.25, rng=None):
    pal = patches(seed, PAVE, 2.0)
    for (x, z) in cells:
        if t.get(x, SURF, z) is not None:
            t.set(x, SURF, z, pal(x, SURF, z))
            if rng is not None and rng.random() < moss and t.get(x, G, z) is None:
                t.set(x, G, z, S('moss_carpet'))


def vines(t, rng, p=0.12, y0=G, max_len=6):
    """Vines down the faces of the stonework (and leaves): each hangs from an exposed face cell
    downward while there is air, up to max_len."""
    faces = []
    for (x, y, z), st in list(t.cells.items()):
        if y < y0 + 1 or B.name_of(st) not in STONE_NAMES + ('minecraft:jungle_leaves', 'minecraft:jungle_log'):
            continue
        for d in HORIZONTAL:
            dx, dz = B.step(d)
            if empty(t, x + dx, y, z + dz) and t.inside(x + dx, y, z + dz):
                faces.append((x + dx, y, z + dz, B.opposite(d)))
    faces.sort()
    for (x, y, z, toward) in faces:
        if rng.random() >= p:
            continue
        n = rng.randint(1, max_len)
        props = {d: (d == toward) for d in HORIZONTAL}
        for k in range(n):
            if y - k < G or not empty(t, x, y - k, z):
                break
            t.set(x, y - k, z, S('vine', up=False, **props))


def growth(t, rng, density=0.35):
    """Ferns, bushes and azaleas on the jungle floor; large ferns here and there."""
    small = [(S('fern'), 6), (S('short_grass'), 5), (S('bush'), 2), (S('azalea'), 1), (S('flowering_azalea'), 1),
             (S('moss_carpet'), 2)]
    for x in range(SX):
        for z in range(SZ):
            if t.name_at(x, SURF, z) not in ('minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block') \
                    or t.get(x, G, z) is not None:
                continue
            r = rng.random()
            if r < density * 0.15 and t.get(x, G + 1, z) is None:
                t.set(x, G, z, S('large_fern', half='lower'))
                t.set(x, G + 1, z, S('large_fern', half='upper'))
            elif r < density:
                t.set(x, G, z, pick(rng, small))


def tree(t, rng, x, z, h, crown=4.0, lean=(0, 0)):
    """A jungle tree: a 2 x 2 trunk h tall from the floor, buttress roots, cocoa on the trunk and a
    persistent crown of leaves (centre shifted by lean) with vines hanging from its rim."""
    for y in range(SURF, G + h):
        for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            t.set(x + dx, y, z + dz, S('jungle_log', axis='y'))
    for (dx, dz, ax, n) in ((-1, 0, 'x', 2), (2, 1, 'x', 2), (0, -1, 'z', 2), (1, 2, 'z', 2)):
        for k in range(n):
            px = x + dx + (k * (1 if dx > 1 else -1) if ax == 'x' else 0)
            pz = z + dz + (k * (1 if dz > 1 else -1) if ax == 'z' else 0)
            if t.inside(px, G, pz):
                t.set(px, G if k == 0 else SURF, pz, S('jungle_log', axis=ax))
    for (cx_, cz_, f) in ((x - 1, z, 'east'), (x + 2, z + 1, 'west')):
        y = G + 2 + rng.randint(0, 3)
        if empty(t, cx_, y, cz_):
            t.set(cx_, y, cz_, S('cocoa', facing=f, age=2))
    cx, cy, cz = x + 1 + lean[0], G + h, z + 1 + lean[1]
    for px in range(int(cx - crown) - 1, int(cx + crown) + 2):
        for py in range(cy - 2, cy + 3):
            for pz in range(int(cz - crown) - 1, int(cz + crown) + 2):
                d = math.hypot(px - cx, pz - cz) / crown + abs(py - cy - 0.5) / 2.6
                if d <= 1.0 and rng.random() < 0.92 and empty(t, px, py, pz):
                    t.set(px, py, pz, LEAVES)
    steps = max(abs(lean[0]), abs(lean[1]))
    for k in range(1, steps + 1):
        bx = int(round(x + 0.5 + lean[0] * k / float(steps)))
        bz = int(round(z + 0.5 + lean[1] * k / float(steps)))
        t.set(bx, G + h - 1, bz, S('jungle_log', axis='x' if abs(lean[0]) >= abs(lean[1]) else 'z'))


def pillar(t, x, z, y0, h, cap=True):
    """A column: base block, ruin_pillar shaft, carved capital."""
    t.set(x, y0, z, S('or:ruin_bricks'))
    for y in range(y0 + 1, y0 + h - (1 if cap else 0)):
        t.set(x, y, z, S('or:ruin_pillar', axis='y'))
    if cap:
        t.set(x, y0 + h - 1, z, S('or:ruin_stone_carved'))


def statue(t, x, z, y0, facing):
    """A robed Jedi on a plinth, a lit blade held upright before the chest."""
    fx, fz = B.step(facing)
    t.set(x, y0, z, S('or:ruin_stone_carved'))
    for y in (y0 + 1, y0 + 2):
        t.set(x, y, z, S('or:ruin_pillar', axis='y'))
    left, right = B.left_of(facing), B.right_of(facing)
    for side in (left, right):
        sx, sz = B.step(side)
        t.set(x + sx, y0 + 2, z + sz, stairs('or:ruin_brick_stairs', B.opposite(side), 'top'))
        t.set(x + sx, y0 + 1, z + sz, S('or:ruin_brick_wall'))
    t.set(x, y0 + 3, z, S('or:ruin_stone_carved'))
    t.set(x, y0 + 4, z, slab('or:ruin_brick_slab'))
    t.set(x + fx, y0 + 1, z + fz, S('or:ruin_brick_wall'))
    t.set(x + fx, y0 + 2, z + fz, end_rod('up'))


def pool(t, rng, cells, lilies=0.12):
    """A pool flush with the floor: water at y 1 and 2 over a stone bed at y 0 (every cell round it
    is solid to y 2, so the water stays)."""
    for (x, z) in cells:
        t.set(x, 0, z, S('or:ruin_bricks'))
        t.set(x, 1, z, S('water', level=0))
        t.set(x, SURF, z, S('water', level=0))
        if rng.random() < lilies:
            t.set(x, G, z, S('lily_pad'))


def lumen(t, rng, cells, p=0.4):
    for (x, z) in cells:
        if t.get(x, G, z) is None and rng.random() < p and t.name_at(x, SURF, z).startswith('oldrepublic:ruin'):
            t.set(x, G, z, S('or:lumen_moss'))


# ---------------------------------------------------------------- variant 1: the pool court

def build_pool_court():
    t = Template(SX, SY, SZ, seed=960120, name='wilds/jungle_shrine/pool_court')
    rng = t.rng
    ground(t, 7501)
    wall = patches(7502, RUIN, 2.5)
    rooms = []
    court = [(x, z) for x in range(8, 29) for z in range(5, 28)]
    pave(t, 7503, court, rng=rng)
    pool(t, rng, [(x, z) for x in range(14, 23) for z in range(9, 22)])
    # the low enclosure wall, broken
    n = Noise(7504, 3.0)
    for x in range(6, 31):
        for z in range(3, 36):
            if x in (6, 30) or z == 3:
                if z == 3 and 15 <= x <= 21:
                    continue
                h = int(1 + 2.5 * n.at(x, 0, z))
                for y in range(G, G + h):
                    t.set(x, y, z, wall(x, y, z))
                if h >= 2 and rng.random() < 0.3:
                    t.set(x, G + h, z, slab('or:ruin_brick_slab'))
    # the gate: two tall columns, the lintel fallen across the path
    for x in (15, 21):
        pillar(t, x, 3, G, 7)
        t.set(x, G + 7, 3, slab('or:ruin_brick_slab'))
    for x in range(16, 21):
        t.set(x, G, 1 if x != 18 else 2, S('or:ruin_stone_carved') if x in (16, 20) else S('or:ruin_bricks'))
    # the colonnades either side of the pool: columns every three, an architrave with gaps
    fallen = {(11, 16), (25, 10), (25, 19)}
    for x in (11, 25):
        for z in range(7, 27, 3):
            if (x, z) in fallen:
                t.set(x, G, z, S('or:ruin_bricks'))
                t.set(x, G + 1, z, S('or:ruin_pillar', axis='y'))
                continue
            pillar(t, x, z, G, 6)
        for z in range(7, 26):
            if not any(abs(z - fz) <= 2 for (fx, fz) in fallen if fx == x) and n.at(x, 5, z) > 0.25:
                t.set(x, G + 6, z, slab('or:ruin_brick_slab') if z % 3 else S('or:ruin_bricks'))
    # a fallen column lying in the pool, another across the walk
    for z in range(16, 21):
        t.set(16, 1, z, S('or:ruin_pillar', axis='z'))
    for x in range(8, 11):
        t.set(x, G, 18, S('or:ruin_pillar', axis='x'))
    for x in range(26, 29):
        t.set(x, G, 12, S('or:ruin_pillar', axis='x'))
    # the shrine: a platform three steps up, the hall, its stepped roof
    for x in range(10, 27):
        for z in range(26, 35):
            for y in range(G, G + 2):
                t.set(x, y, z, wall(x, y, z) if (x in (10, 26) or z in (26, 34)) else S('or:ruin_bricks'))
            t.set(x, G + 2, z, S('or:ruin_bricks') if (x + z) % 3 else S('or:ruin_stone_mossy'))
    for x in range(14, 23):
        t.set(x, G, 23, stairs('or:ruin_brick_stairs', 'south'))
        t.set(x, G, 24, S('or:ruin_bricks'))
        t.set(x, G + 1, 24, stairs('or:ruin_brick_stairs', 'south'))
        for y in (G, G + 1):
            t.set(x, y, 25, S('or:ruin_bricks'))
        t.set(x, G + 2, 25, stairs('or:ruin_brick_stairs', 'south'))
    hy = G + 3   # the hall's floor feet
    for x in range(12, 25):
        for z in range(28, 34):
            edge = x in (12, 24) or z == 33
            for y in range(hy, hy + 6):
                if edge:
                    t.set(x, y, z, wall(x, y, z))
                elif z == 28 and x in (13, 15, 21, 23):
                    t.set(x, y, z, S('or:ruin_pillar', axis='y') if y < hy + 5 else S('or:ruin_stone_carved'))
                else:
                    t.set(x, y, z, AIR)
            if not edge:
                t.set(x, hy + 5, z, S('or:ruin_bricks'))
    for x in range(12, 25):
        t.set(x, hy + 5, 28, S('or:ruin_stone_carved') if x % 2 else S('or:ruin_bricks'))
    for k in range(4):
        for x in range(11 + k, 26 - k):
            for z in range(27 + k, 35 - k):
                if x in (11 + k, 25 - k) or z in (27 + k, 34 - k):
                    face = 'north' if z == 27 + k else 'south' if z == 34 - k else 'west' if x == 11 + k else 'east'
                    t.set(x, hy + 6 + k, z, stairs('or:ruin_brick_stairs', B.opposite(face)))
                else:
                    t.set(x, hy + 6 + k, z, S('or:ruin_bricks'))
    # inside: the statue, the altar and its candles, the chest, lumen moss
    statue(t, 18, 32, hy, 'north')
    t.set(17, hy, 30, S('or:ruin_stone_carved'))
    t.set(18, hy, 30, slab('or:ruin_brick_slab', 'top'))
    t.set(19, hy, 30, S('or:ruin_stone_carved'))
    for x in (17, 19):
        t.set(x, hy + 1, 30, S('white_candle', candles=3, lit=True, waterlogged=False))
    t.set(18, hy + 1, 30, S('amethyst_cluster', facing='up', waterlogged=False))
    t.chest(14, hy, 32, 'east', loot=LOOT)
    for (x, z) in ((13, 29), (14, 29), (22, 31), (23, 32), (20, 29), (16, 31)):
        t.set(x, hy, z, S('or:lumen_moss'))
    for x in (14, 22):
        t.set(x, hy + 4, 32, lantern(True))
        t.set(x, hy + 4, 29, lantern(True))
    rooms.append((13, hy, 29, 23, hy + 4, 32))
    # the tree that split the hall's south-east corner, its rubble
    for y in range(hy, hy + 5):
        t.set(24, y, 32, AIR)
        t.set(24, y, 31, AIR if y > hy + 1 else S('or:ruin_stone_cracked'))
    tree(t, rng, 25, 32, 15, crown=5.0, lean=(-2, -1))
    for (x, z) in ((27, 29), (28, 30), (27, 31), (28, 28)):
        t.set(x, G, z, pick(rng, [(S('or:ruin_stone_cracked'), 2), (S('mossy_cobblestone'), 2), (slab('or:ruin_brick_slab'), 1)]))
    # trees round the clearing
    tree(t, rng, 2, 6, 13, crown=4.5, lean=(1, 0))
    tree(t, rng, 32, 4, 12, crown=4.0)
    tree(t, rng, 2, 31, 11, crown=4.0, lean=(1, -1))
    # lanterns on posts at the pool's head and foot
    for (x, z) in ((13, 8), (23, 8), (13, 22), (23, 22)):
        t.set(x, G, z, S('or:ruin_brick_wall'))
        t.set(x, G + 1, z, S('lantern', hanging=False, waterlogged=False))
    lumen(t, rng, [(x, z) for x in (12, 13, 23, 24) for z in range(7, 26)], 0.18)
    growth(t, rng)
    vines(t, rng, p=0.18)
    carve(t, 0, 0, SX - 1, SZ - 1, G, G + 4, bank=1, noise=Noise(7599, 3.0))
    light_rooms(t, rooms)
    return t


# ---------------------------------------------------------------- variant 2: the sunken dome

def build_sunken_dome():
    t = Template(SX, SY, SZ, seed=960121, name='wilds/jungle_shrine/sunken_dome')
    rng = t.rng
    ground(t, 7601)
    wall = patches(7602, RUIN, 2.5)
    cx, cz = 18, 19
    R_IN, R_OUT = 7.4, 8.5
    rooms = []

    def ang(x, z):
        return math.degrees(math.atan2(z + 0.5 - (cz + 0.5), x + 0.5 - (cx + 0.5))) % 360

    def fallen(x, z, y):
        """The north-east quarter that came down (angle 285..360 and 0..20 round from east)."""
        a = ang(x, z)
        return (a >= 280 or a <= 15) and y >= 8 + int(3 * math.sin(math.radians(a * 4)))

    # the terrace round the rotunda, the floor inside
    pave(t, 7603, [(x, z) for x in range(SX) for z in range(SZ) if math.hypot(x - cx, z - cz) <= 11.5], rng=rng)
    # walls (y 3..9) with a door north and windows
    for x in range(cx - 9, cx + 10):
        for z in range(cz - 9, cz + 10):
            d = math.hypot(x - cx, z - cz)
            if R_IN < d <= R_OUT:
                a = ang(x, z)
                for y in range(G, G + 7):
                    if fallen(x, z, y):
                        continue
                    door = abs(a - 270) < 9 and y < G + 4
                    window = any(abs(a - w) < 6 for w in (0, 45, 135, 180, 225, 315)) and y in (G + 2, G + 3)
                    t.set(x, y, z, AIR if door or window else wall(x, y, z))
            elif d <= R_IN:
                for y in range(G, G + 7):
                    t.set(x, y, z, AIR)
    # the dome: a shell from y 10 up, an oculus at the top, the fallen quarter open
    for x in range(cx - 9, cx + 10):
        for z in range(cz - 9, cz + 10):
            for y in range(G + 7, SY):
                r = math.sqrt((x - cx) ** 2 + (z - cz) ** 2 + ((y - (G + 7)) * 1.1) ** 2)
                if r <= R_OUT and not fallen(x, z, y) and math.hypot(x - cx, z - cz) > 1.6:
                    t.set(x, y, z, wall(x, y, z) if r > R_IN - 0.4 else AIR)
    # rubble under the hole, a root through the wall
    n = Noise(7604, 2.0)
    for x in range(cx, cx + 8):
        for z in range(cz - 8, cz + 1):
            d = math.hypot(x - cx, z - cz)
            if 2.5 < d < R_IN and fallen(x, z, 20) and n.at(x, 0, z) > 0.35:
                t.set(x, G, z, pick(rng, [(S('or:ruin_stone_cracked'), 3), (S('mossy_cobblestone'), 2), (S('or:ruin_stone_mossy'), 2),
                                          (slab('or:ruin_brick_slab'), 2)]))
                if n.at(x, 1, z) > 0.65:
                    t.set(x, G + 1, z, S('or:ruin_stone_cracked'))
    tree(t, rng, 28, 8, 14, crown=5.5, lean=(-3, 2))
    for k in range(5):
        t.set(27 - k, G, 10 + k, S('jungle_log', axis='x'))
    # inside: four columns, the round pool under the oculus, the meditation stone, the niche
    pool(t, rng, [(x, z) for x in range(cx - 3, cx + 4) for z in range(cz - 3, cz + 4) if math.hypot(x - cx, z - cz) <= 2.9], 0.15)
    for (x, z) in [(x, z) for x in range(cx - 4, cx + 5) for z in range(cz - 4, cz + 5) if 2.9 < math.hypot(x - cx, z - cz) <= 3.8]:
        t.set(x, G, z, slab('or:ruin_brick_slab') if (x + z) % 2 else S('or:ruin_brick_wall'))
    for (x, z) in ((cx - 5, cz - 5), (cx + 5, cz - 5), (cx - 5, cz + 5), (cx + 5, cz + 5)):
        pillar(t, x, z, G, 7)
    t.set(cx, G, cz + 3, AIR)
    t.set(cx, G, cz + 5, S('or:meditation_stone'))
    t.set(cx, G, cz + 4, S('light_blue_carpet'))
    for x in (cx - 1, cx, cx + 1):
        for y in (G, G + 1, G + 2):
            t.set(x, y, cz + 8, AIR if y < G + 2 or x == cx else S('or:ruin_stone_carved'))
    for x in (cx - 1, cx, cx + 1):
        for y in (G, G + 1, G + 2):
            t.set(x, y, cz + 9, wall(x, y, cz + 9))
    t.chest(cx, G, cz + 8, 'north', loot=LOOT)
    t.set(cx - 1, G, cz + 8, S('white_candle', candles=2, lit=True, waterlogged=False))
    t.set(cx + 1, G, cz + 8, S('white_candle', candles=3, lit=True, waterlogged=False))
    lumen(t, rng, [(x, z) for x in range(cx - 7, cx + 8) for z in range(cz - 7, cz + 8)
                   if 4.0 < math.hypot(x - cx, z - cz) < 7.0], 0.2)
    for (x, z) in ((cx - 6, cz), (cx + 6, cz + 1), (cx - 2, cz - 6)):
        t.set(x, G, z, S('or:ruin_brick_wall'))
        t.set(x, G + 1, z, S('lantern', hanging=False, waterlogged=False))
    rooms.append((cx - 6, G, cz - 6, cx + 6, G + 4, cz + 6))
    # the portico and its steps on the north
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 13, cz - 8):
            t.set(x, SURF, z, S('or:ruin_bricks'))
    for x in (cx - 3, cx + 3):
        for z in (cz - 12, cz - 9):
            pillar(t, x, z, G, 6)
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 12, cz - 8):
            if (x + z) % 5 and n.at(x, 3, z) > 0.2:
                t.set(x, G + 6, z, slab('or:ruin_brick_slab'))
    for x in range(cx - 2, cx + 3):
        t.set(x, SURF, cz - 14, stairs('or:ruin_brick_stairs', 'south'))
    # trees round the clearing
    tree(t, rng, 3, 4, 12, crown=4.0, lean=(1, 1))
    tree(t, rng, 3, 30, 13, crown=4.5)
    tree(t, rng, 31, 31, 11, crown=4.0, lean=(-1, 0))
    growth(t, rng, density=0.3)
    vines(t, rng, p=0.16)
    carve(t, 0, 0, SX - 1, SZ - 1, G, G + 4, bank=1, noise=Noise(7699, 3.0))
    light_rooms(t, rooms)
    return t


# ---------------------------------------------------------------- worldgen

def write_worldgen():
    loot_table(LOOT, [
        (1, [loot_item('oldrepublic:lumen_thread', count=(1, 2))]),
        (1, [loot_item('emerald', count=(1, 2))]),
        (1, [loot_item('gold_nugget', count=(2, 5))]),
        (1, [loot_item('amethyst_shard', count=(1, 3))]),
        (1, [loot_item('book')]),
    ])
    ad = adapted(SY, yard_stand=G, support_depth=8, ramp_rings=2)
    wilds_family('jungle_shrine', [('wilds/jungle_shrine/pool_court', ad, 1), ('wilds/jungle_shrine/sunken_dome', ad, 1)],
                 ['#minecraft:is_jungle'], spacing=48, separation=18, salt=960109, buried=BURIED)


def main():
    args = cli(__doc__)
    for name, build in (('pool_court', build_pool_court), ('sunken_dome', build_sunken_dome)):
        t = build()
        print(name, t.size, t.count())
        save(t, 'wilds/jungle_shrine/' + name, args.preview)
    write_worldgen()


if __name__ == '__main__':
    main()
