"""Shared helpers for P1's Selvaren generators: the Watch's old country (docs/MILESTONE_6.md 5.5).

Quiet stone, overgrown order, light through trees. Every script in this folder imports this module:

    from selvaren_lib import *          # Template, S, AIR, shapes, nature, mod, meta, W, B, palettes...

Previews are rendered only when the environment variable P1_PREVIEW names a folder (never the
repository), so `build_all.py` run by the lead writes nothing but the data files.

Everything random goes through a template's seeded ``t.rng`` or a ``Noise(seed)``: two runs write
byte-identical files.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..'))

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta, worldgen as W  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402,F401
from structlib.nature import Noise  # noqa: E402
from structlib.palettes import Palette  # noqa: E402

PREVIEW = os.environ.get('P1_PREVIEW')

# ---------------------------------------------------------------- palettes

# The Watch's dressed stone: watch_stone ashlar, weathered by patches of moss and frost cracks. Copings,
# piers and parapets use vanilla stone brick walls, whose mossy twin the weathering pass can swap in.
WATCH = Palette(
    wall=[('or:watch_stone', 12), ('minecraft:stone_bricks', 2), ('minecraft:mossy_stone_bricks', 2),
          ('minecraft:cracked_stone_bricks', 1)],
    old=[('or:watch_stone', 6), ('minecraft:mossy_stone_bricks', 4), ('minecraft:cracked_stone_bricks', 2),
         ('minecraft:stone_bricks', 1), ('minecraft:mossy_cobblestone', 1)],
    plinth=[('minecraft:cobblestone', 3), ('minecraft:mossy_cobblestone', 3), ('minecraft:andesite', 2),
            ('minecraft:tuff', 1), ('minecraft:stone', 2)],
    floor=[('or:watch_stone', 5), ('minecraft:polished_andesite', 2), ('minecraft:stone_bricks', 2),
           ('minecraft:mossy_stone_bricks', 1), ('minecraft:mossy_cobblestone', 1)],
    trim='minecraft:polished_andesite',
    carved='minecraft:chiseled_stone_bricks',
    stairs='or:watch_stone_stairs',
    slab='or:watch_stone_slab',
    wall_top=[('minecraft:stone_brick_wall', 3), ('minecraft:mossy_stone_brick_wall', 2),
              ('minecraft:andesite_wall', 1)],
    timber='minecraft:stripped_dark_oak_log',
    beam='minecraft:dark_oak_log',
    roof=[('minecraft:dark_oak_planks', 3), ('minecraft:spruce_planks', 1)],
    roof_stairs='minecraft:dark_oak_stairs',
    light='minecraft:lantern',
    cloth='minecraft:green_wool',
    rubble=[('minecraft:cobblestone', 2), ('minecraft:mossy_cobblestone', 2), ('minecraft:andesite', 1)],
)

# Rough field stone: menhirs, drystone, cairns.
FIELD = Palette(
    stone=[('minecraft:stone', 5), ('minecraft:andesite', 3), ('minecraft:cobblestone', 1),
           ('minecraft:mossy_cobblestone', 2), ('minecraft:tuff', 1)],
    moss=[('minecraft:mossy_cobblestone', 3), ('minecraft:moss_block', 1), ('minecraft:mossy_stone_bricks', 1)],
)

# The Archive's sand-coloured ruin stone (the oldest work, older than the Watch's own).
RUIN = Palette(
    wall=[('or:ruin_stone', 5), ('or:ruin_stone_mossy', 2), ('or:ruin_stone_cracked', 1), ('or:ruin_bricks', 2)],
)

# Weathering of the Watch's stone: moss and cracks in patches (nature.weather mapping).
WATCH_WEATHER = {
    'oldrepublic:watch_stone': [('minecraft:mossy_stone_bricks', 0.12), ('minecraft:cracked_stone_bricks', 0.06)],
    'oldrepublic:watch_stone_stairs': [('minecraft:mossy_stone_brick_stairs', 0.18), ('minecraft:stone_brick_stairs', 0.08)],
    'oldrepublic:watch_stone_slab': [('minecraft:mossy_stone_brick_slab', 0.18), ('minecraft:stone_brick_slab', 0.08)],
    'minecraft:stone_bricks': [('minecraft:mossy_stone_bricks', 0.2), ('minecraft:cracked_stone_bricks', 0.1)],
    'minecraft:cobblestone': [('minecraft:mossy_cobblestone', 0.35)],
    'minecraft:stone_brick_wall': [('minecraft:mossy_stone_brick_wall', 0.3)],
    'minecraft:stone_brick_stairs': [('minecraft:mossy_stone_brick_stairs', 0.25)],
    'minecraft:stone_brick_slab': [('minecraft:mossy_stone_brick_slab', 0.25)],
}

GROUND_TOP = {
    # role -> [(state, weight)]: the turf of each biome family
    'meadow': [('minecraft:grass_block[snowy=false]', 10), ('minecraft:coarse_dirt', 1)],
    'highwood': [('minecraft:grass_block[snowy=false]', 6), ('minecraft:podzol[snowy=false]', 3),
                 ('minecraft:moss_block', 2)],
    'tarn': [('minecraft:grass_block[snowy=false]', 7), ('minecraft:gravel', 1), ('minecraft:coarse_dirt', 1)],
    'mixed': [('minecraft:grass_block[snowy=false]', 9), ('minecraft:podzol[snowy=false]', 1),
              ('minecraft:moss_block', 1), ('minecraft:coarse_dirt', 1)],
}

SOIL = {'minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block', 'minecraft:coarse_dirt', 'minecraft:dirt',
        'minecraft:rooted_dirt', 'minecraft:mud'}
GRASSY = {'minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block'}

PLANTS = {
    'meadow': [('minecraft:short_grass', 30), ('minecraft:fern', 4), ('minecraft:tall_grass[half=lower]', 5),
               ('minecraft:wildflowers[facing=north,flower_amount=3]', 3),
               ('minecraft:wildflowers[facing=east,flower_amount=2]', 3),
               ('minecraft:pink_petals[facing=south,flower_amount=2]', 2), ('minecraft:bush', 3),
               ('minecraft:allium', 1), ('minecraft:cornflower', 1), ('oldrepublic:silverleaf', 2)],
    'highwood': [('minecraft:fern', 14), ('minecraft:short_grass', 14), ('minecraft:large_fern[half=lower]', 3),
                 ('oldrepublic:silverleaf', 6), ('minecraft:bush', 4),
                 ('minecraft:leaf_litter[facing=north,segment_amount=3]', 5),
                 ('minecraft:leaf_litter[facing=west,segment_amount=2]', 4), ('minecraft:lily_of_the_valley', 1),
                 ('minecraft:brown_mushroom', 1)],
    'shade': [('minecraft:fern', 8), ('oldrepublic:silverleaf', 8), ('minecraft:leaf_litter[facing=south,segment_amount=4]', 5),
              ('minecraft:leaf_litter[facing=east,segment_amount=2]', 4), ('minecraft:moss_carpet', 3),
              ('minecraft:brown_mushroom', 1)],
    'dead': [('minecraft:short_dry_grass', 10), ('minecraft:tall_dry_grass', 3), ('minecraft:dead_bush', 4)],
}


def pick(choices, rng):
    total = sum(w for _, w in choices)
    r = rng.random() * total
    for s, w in choices:
        r -= w
        if r <= 0:
            return s
    return choices[-1][0]


def weather(t, region=None, seed=0, patchiness=0.6, extra=None):
    """Moss and cracks in noise patches over the Watch's stone."""
    mapping = dict(WATCH_WEATHER)
    if extra:
        mapping.update(extra)
    nature.weather(t, mapping, region=region, noise=Noise(seed, 5.0), patchiness=patchiness)


# ---------------------------------------------------------------- ground

def blob(cx, cz, r, seed, rough=1.5, scale=4.0):
    """An irregular disc test: f(x, z) -> True inside. Radius r wobbles by +-rough with the noise."""
    n = Noise(seed, scale)

    def inside(x, z):
        d = math.hypot(x - cx, z - cz)
        return d <= r + (n.at(x, z) - 0.5) * 2 * rough
    return inside


def ground(t, cols, top_y, seed, kind='mixed', depth=None, under='minecraft:dirt', base=None):
    """Lays the ground in columns `cols` ({(x, z)}): the turf at top_y, dirt below, and `base` (default field
    stone) at layer 0 so the footing columns of worldgen support start from solid rock. Returns nothing."""
    top = Palette(top=GROUND_TOP[kind]).patches('top', seed=seed, scale=3.5)
    base = base or FIELD.picker('stone', t.rng)
    for (x, z) in sorted(cols):
        for y in range(0, top_y):
            t.set(x, y, z, base(x, y, z) if (y == 0 and callable(base)) else (base if y == 0 else under))
        t.set(x, top_y, z, top(x, top_y, z))


def clear_bowl(t, cols, cx, cz, y0, y1, r_full, r_out, rim_h=2):
    """Explicit air over the ground of `cols` from y0 up: the full height y1 within r_full of the centre,
    falling to rim_h layers at r_out, so a hillside is cut into a bowl instead of a square pit."""
    for (x, z) in sorted(cols):
        d = math.hypot(x - cx, z - cz)
        if d <= r_full:
            top = y1
        else:
            f = min(1.0, (d - r_full) / max(0.5, r_out - r_full))
            top = int(round(y1 - (y1 - (y0 + rim_h - 1)) * f))
        for y in range(y0, top + 1):
            if t.get(x, y, z) is None:
                t.set(x, y, z, AIR)


def surface_y(t, x, z, y_hi, y_lo=0):
    """The highest y in [y_lo, y_hi] holding a solid template cell in column (x, z), or None."""
    for y in range(y_hi, y_lo - 1, -1):
        if t.is_solid(x, y, z):
            return y
    return None


def plants(t, cols, kind, density, seed, y_hi=None, on=GRASSY, avoid=()):
    """Plants on the turf of `cols` in noise clumps: only on soil, only into explicit air (inside the build)."""
    n = Noise(seed, 3.0)
    choices = PLANTS[kind]
    y_hi = t.sy - 2 if y_hi is None else y_hi
    for (x, z) in sorted(cols):
        if (x, z) in avoid:
            continue
        y = surface_y(t, x, z, y_hi)
        if y is None or t.name_at(x, y, z) not in on:
            continue
        if t.get(x, y + 1, z) not in (AIR,):
            continue
        if t.rng.random() > density * (0.3 + 1.4 * n.at(x, z)):
            continue
        s = pick(choices, t.rng)
        if 'half=lower' in s:
            if t.get(x, y + 2, z) != AIR:
                continue
            t.set(x, y + 1, z, s)
            t.set(x, y + 2, z, s.replace('half=lower', 'half=upper'))
        else:
            if 'facing=' in s:
                s = B.with_props(s, facing=B.DIRS[t.rng.randrange(4)])
            t.set(x, y + 1, z, s)


# ---------------------------------------------------------------- stones and trees

def stair(name, facing, half='bottom'):
    return S(name, facing=facing, half=half, shape='straight', waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def menhir(t, x, y, z, h, rng, lean=None, cap='stair', facing='north', mossy_side='north', wide=False):
    """A lesser standing stone: a column of field stone h high (2 cells wide along x when wide), mossy on one
    side, topped by a sloped cap. lean: a direction the top courses step toward."""
    cells = [(x, z), (x + 1, z)] if wide else [(x, z)]
    ox = oz = 0
    for i in range(h):
        if lean and i >= max(1, h - 2):
            dx, dz = B.step(lean)
            if i == max(1, h - 2):  # the kink course spans both cells, so the stone reads as one piece
                for (cx, cz) in cells:
                    t.set(cx, y + i, cz, FIELD.pick('stone', rng))
            ox, oz = dx, dz
        for (cx, cz) in cells:
            s = FIELD.pick('stone', rng)
            if rng.random() < 0.35:
                s = FIELD.pick('moss', rng)
            t.set(cx + ox, y + i, cz + oz, s)
    top_y = y + h
    if cap == 'stair':
        for (cx, cz) in cells:
            t.set(cx + ox, top_y, cz + oz, stair(rng.choice(['minecraft:stone_stairs', 'minecraft:andesite_stairs',
                                                             'minecraft:mossy_cobblestone_stairs']), facing))
    elif cap == 'slab':
        for (cx, cz) in cells:
            t.set(cx + ox, top_y, cz + oz, slab(rng.choice(['minecraft:stone_slab', 'minecraft:andesite_slab',
                                                            'minecraft:mossy_cobblestone_slab'])))
    # moss on the shaded face
    dx, dz = B.step(mossy_side)
    for i in range(h):
        for (cx, cz) in cells:
            px, pz = cx + dx, cz + dz
            if t.get(px, y + i, pz) == AIR and rng.random() < 0.3:
                t.set(px, y + i, pz, S('vine', **{B.opposite(mossy_side): True}))


def leaf_blob(cells, cx, cy, cz, rx, ry, n, fray=0.9):
    """Adds the cells of a frayed ellipsoid of leaves (flatter below its middle) to `cells`."""
    for yy in range(int(cy - ry - 1), int(cy + ry + 2)):
        ryy = ry if yy >= cy else ry * 0.7
        for xx in range(int(cx - rx - 1), int(cx + rx + 2)):
            for zz in range(int(cz - rx - 1), int(cz + rx + 2)):
                d = ((xx - cx) / rx) ** 2 + ((yy - cy) / max(0.6, ryy)) ** 2 + ((zz - cz) / rx) ** 2
                if d <= 1.0 + (n.at(xx, yy, zz) - 0.5) * fray:
                    cells.add((xx, yy, zz))


def silver_tree(t, x, y, z, h, seed, boughs=3, spread=2.4, lean=None, dead=False, leaves='minecraft:oak_leaves'):
    """An old Selvaren silver tree (birch trunk, oak crown, as the highwood's own) rooted at (x, y, z):
    a trunk h high, `boughs` limbs reaching out and up from its upper half, each ending in a frayed clump of
    leaves, and a clump at the top. dead: a bare grey snag of stripped birch, no leaves.
    Returns (trunk cells, leaf cells)."""
    n = Noise(seed, 1.8)
    rng = t.rng
    wood = 'minecraft:stripped_birch_log' if dead else 'minecraft:birch_log'
    tx, tz = x, z
    trunk = []
    for i in range(h):
        if lean and i == int(h * 0.6):
            dx, dz = B.step(lean)
            t.set(tx + dx, y + i - 1, tz + dz, wood + '[axis=y]')
            trunk.append((tx + dx, y + i - 1, tz + dz))
            tx, tz = tx + dx, tz + dz
        t.set(tx, y + i, tz, wood + '[axis=y]')
        trunk.append((tx, y + i, tz))
    # buttress roots
    for d in ('north', 'east', 'south', 'west'):
        if rng.random() < 0.55:
            dx, dz = B.step(d)
            t.set(x + dx, y, z + dz, '%s[axis=%s]' % (wood, 'x' if dx else 'z'))
            trunk.append((x + dx, y, z + dz))
    leaves_at = set()
    top_y = y + h - 1
    leaf_blob(leaves_at, tx, top_y + 0.8, tz, spread * 0.85, 2.0, n)
    dirs = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]
    start = rng.randrange(8)
    for b in range(boughs):
        dx, dz = dirs[(start + b * (8 // boughs) + rng.randrange(2)) % 8]
        by = y + int(h * (0.45 + 0.1 * b + 0.25 * rng.random()))
        by = min(by, top_y - 1)
        length = 2 + rng.randrange(2)
        px, py, pz = tx, by, tz
        for i in range(1, length + 1):
            qx, qz = tx + dx * i, tz + dz * i
            qy = by + (i - 1) // 2
            axis = 'x' if abs(dx) >= abs(dz) else 'z'
            if dx and dz and i > 0:  # close the diagonal step
                t.set(qx - dx, qy, qz, '%s[axis=%s]' % (wood, 'x'))
                trunk.append((qx - dx, qy, qz))
            t.set(qx, qy, qz, '%s[axis=%s]' % (wood, axis))
            trunk.append((qx, qy, qz))
            px, py, pz = qx, qy, qz
        leaf_blob(leaves_at, px, py + 1, pz, spread * (0.8 + 0.25 * rng.random()), 1.7, n)
    trunk_set = set(trunk)
    placed = set()
    if not dead:
        for p in sorted(leaves_at):
            if p in trunk_set or not t.inside(*p):
                continue
            cur = t.get(*p)
            if cur is not None and cur != AIR:
                continue
            t.set(*p, S(leaves, persistent=True, distance=1, waterlogged=False))
            placed.add(p)
    return trunk_set, placed


def field_stone(t, cells, y, heights, rng, top='stair', lean=None, mossy=0.3, mat=None):
    """A lesser standing stone over `cells` (one to three columns, heights[i] courses each), finished with
    a sloped stair or a slab course on every column; lean steps the top two courses over one cell."""
    mat = mat or (lambda: FIELD.pick('stone', rng))
    tall = max(heights)
    for (cx, cz), h in zip(cells, heights):
        ox = oz = 0
        for i in range(h):
            if lean and h >= 3 and i >= h - 2:
                dx, dz = B.step(lean)
                if i == h - 2:
                    t.set(cx, y + i, cz, mat())
                ox, oz = dx, dz
            t.set(cx + ox, y + i, cz + oz, FIELD.pick('moss', rng) if rng.random() < mossy else mat())
        if top is None:
            continue
        # the cap leans toward the tallest neighbour, or along `lean`
        face = lean or 'north'
        for (ox2, oz2), h2 in zip(cells, heights):
            if h2 > h:
                face = 'east' if ox2 > cx else 'west' if ox2 < cx else 'south' if oz2 > cz else 'north'
        if top == 'stair' or (top == 'mixed' and rng.random() < 0.5):
            name = rng.choice(['minecraft:stone_stairs', 'minecraft:andesite_stairs', 'minecraft:mossy_cobblestone_stairs',
                               'minecraft:cobblestone_stairs'])
            t.set(cx + ox, y + h, cz + oz, stair(name, face))
        else:
            name = rng.choice(['minecraft:stone_slab', 'minecraft:andesite_slab', 'minecraft:mossy_cobblestone_slab'])
            t.set(cx + ox, y + h, cz + oz, slab(name))


# ---------------------------------------------------------------- checks the scripts run on themselves

FULL_ABOVE_OK = ('_slab', '_stairs', '_carpet', 'air', '_trapdoor', '_pressure_plate', 'candle', 'flower_pot',
                 'lantern', 'torch', 'leaf_litter', 'petals', 'wildflowers', 'moss_carpet', 'skull', 'head')


def check_chests(t):
    """Every chest can open: the cell above is not a full block (a full block above keeps a chest shut)."""
    bad = []
    for p, s in t.cells.items():
        if B.name_of(s) in ('minecraft:chest', 'minecraft:trapped_chest'):
            above = t.get(p[0], p[1] + 1, p[2])
            if above is None:
                bad.append((p, 'unset above (the world decides)'))
                continue
            path = B.name_of(above).split(':', 1)[1]
            if not any(path.endswith(k) or path == k for k in FULL_ABOVE_OK):
                bad.append((p, above))
    if bad:
        raise SystemExit('%s: chests that cannot open: %r' % (t.name, bad))


def check_superset(clean, corrupt):
    """The corrupted template covers every cell of the clean one (it is stamped over it)."""
    missing = [p for p in clean.cells if p not in corrupt.cells]
    if missing:
        raise SystemExit('corrupted template misses %d clean cells, e.g. %r' % (len(missing), sorted(missing)[:5]))
    jig = [p for p, s in corrupt.cells.items() if B.name_of(s) == 'minecraft:jigsaw']
    if jig:
        raise SystemExit('corrupted template has jigsaw blocks at %r' % jig)


def _passable(t, p, stand_y):
    if not t.inside(*p):
        return p[1] >= stand_y
    s = t.get(*p)
    if s is None:
        return p[1] >= stand_y             # the world's ground below the stand, cleared air above
    if B.name_of(s) in ('minecraft:water', 'minecraft:ladder', 'minecraft:vine'):
        return True
    return mod.walk_through(s)


def reach(t, stand_y, gone=()):
    """Every feet cell a person reaches from the ground round the template (feet at stand_y): walking, a
    one-block climb, drops, jumps of up to four across open air, swimming, ladders and vines. Stairs and
    slabs count as full blocks (a climb), which only errs on the safe side. `gone`: cells taken as air."""
    gone = set(gone)

    def pas(p):
        return p in gone or _passable(t, p, stand_y)

    def ok(p):
        x, y, z = p
        if not (pas(p) and pas((x, y + 1, z))):
            return False
        s = t.get(*p)
        if s and B.name_of(s) in ('minecraft:water', 'minecraft:ladder', 'minecraft:vine'):
            return True
        return not pas((x, y - 1, z))

    sx, sy, sz = t.sx, t.sy, t.sz
    start = [(x, stand_y, z) for x in range(-1, sx + 1) for z in (-1, sz)]
    start += [(x, stand_y, z) for x in (-1, sx) for z in range(0, sz)]
    start = [p for p in start if ok(p)]
    seen = set(start)
    todo = list(start)
    while todo:
        x, y, z = todo.pop()
        cur = t.get(x, y, z)
        nxt = []
        if cur is not None and B.name_of(cur) in ('minecraft:water', 'minecraft:ladder', 'minecraft:vine'):
            nxt += [(x, y + 1, z), (x, y - 1, z)]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for d in range(1, 5):
                qx, qz = x + dx * d, z + dz * d
                if d > 1:
                    mx, mz = x + dx * (d - 1), z + dz * (d - 1)
                    if not (pas((mx, y, mz)) and pas((mx, y + 1, mz))):
                        break
                for qy in ([y + 1, y] if d == 1 else [y]):
                    if qy == y + 1 and not pas((x, y + 2, z)):
                        continue
                    nxt.append((qx, qy, qz))
                if pas((qx, y, qz)):
                    for qy in range(y - 1, -2, -1):
                        q = (qx, qy, qz)
                        if ok(q):
                            nxt.append(q)
                            break
                        if not pas(q):
                            break
        for q in nxt:
            if q in seen or not (-1 <= q[0] <= sx and -1 <= q[2] <= sz and 0 <= q[1] < sy + 2):
                continue
            if ok(q):
                seen.add(q)
                todo.append(q)
    return seen


def check_reach(t, stand_y, targets, gone=(), reached=None):
    """targets: {name: [cells]}: each is reached when some feet cell a person reaches stands next to one of
    its cells (the same cell, a horizontal neighbour, or one up or down). Raises on any missed."""
    got = reached if reached is not None else reach(t, stand_y, gone)
    missed = []
    for name, cells in sorted(targets.items()):
        hit = False
        for (x, y, z) in cells:
            for dx, dy, dz in ((0, 0, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0),
                               (1, -1, 0), (-1, -1, 0), (0, -1, 1), (0, -1, -1)):
                if (x + dx, y + dy, z + dz) in got:
                    hit = True
                    break
            if hit:
                break
        if not hit:
            missed.append(name)
    if missed:
        raise SystemExit('%s: out of reach: %s' % (t.name, ', '.join(missed)))
    return got


def rot_cell(x, z, sx, sz, q):
    """Vanilla rotation of a template cell about the origin (StructureTemplate.transform), q clockwise quarters."""
    q %= 4
    if q == 0:
        return x, z
    if q == 1:
        return -z, x
    if q == 2:
        return -x, -z
    return z, -x


def ring_open(t, heart, radii, count=7, height=None):
    """The ring spots WorldArcs.ringSpot uses round `heart` (angle i*2pi/count at each radius, the height
    of MOTION_BLOCKING_NO_LEAVES capped at heart.y + 6), checked in all four rotations. A spot is fine when
    nothing but walk-through blocks and leaves stands in its column above the feet layer (one block at the
    feet layer only lifts the spawn by one). Returns [(rotation, radius, i, cell, block)] of the bad ones."""
    hx, hy, hz = heart
    top = t.sy if height is None else min(t.sy, hy + height)
    bad = []
    for q in range(4):
        hrx, hrz = rot_cell(hx, hz, t.sx, t.sz, q)
        for r in radii:
            for i in range(count):
                a = i * 2 * math.pi / count
                wx = hrx + int(math.floor(math.cos(a) * r + 0.5))
                wz = hrz + int(math.floor(math.sin(a) * r + 0.5))
                lx, lz = rot_cell(wx, wz, t.sx, t.sz, (4 - q) % 4)
                for y in range(hy + 1, top):
                    s = t.get(lx, y, lz)
                    if s is None or mod.walk_through(s) or B.name_of(s).endswith('_leaves'):
                        continue
                    bad.append((q, r, i, (lx, y, lz), s))
                    break
    return bad


def save(t, template_id, preview_name=None, views=None):
    """Saves the template (and its anchors); renders a preview into $P1_PREVIEW when that is set."""
    path = t.save(template_id)
    if PREVIEW and preview_name:
        render(template_id, preview_name, views=views)
    return path


def render(template_id, name, views=None, cut=None, plans=None):
    if not PREVIEW:
        return None
    import subprocess
    nbt = os.path.join(HERE, '..', '..', '..', '..', 'src', 'main', 'resources', 'data', 'oldrepublic', 'structure',
                       template_id + '.nbt')
    out = os.path.join(PREVIEW, name + '.png')
    cmd = [sys.executable, os.path.join(HERE, '..', '..', 'structlib', 'preview.py'), os.path.abspath(nbt), '-o', out]
    if views:
        cmd += ['--views', views]
    if cut:
        cmd += ['--cut', cut]
    if plans:
        cmd += ['--plans', plans]
    subprocess.run(cmd, check=True)
    return out


# ---------------------------------------------------------------- loot tables

LOOT = os.path.join(HERE, '..', '..', '..', '..', 'src', 'main', 'resources', 'data', 'oldrepublic', 'loot_table')


def loot_table(path, pools):
    """Writes loot_table/<path>.json, a chest table in the house style. pools: a list of
    (rolls, [(item, min, max, weight)], chance): rolls a number or (min, max); chance None or a number."""
    import json
    out = []
    for rolls, entries, chance in pools:
        p = {'rolls': float(rolls) if not isinstance(rolls, tuple) else
             {'type': 'minecraft:uniform', 'min': float(rolls[0]), 'max': float(rolls[1])}, 'entries': []}
        for item, lo, hi, weight in entries:
            e = {'type': 'minecraft:item', 'name': item if ':' in item else 'minecraft:' + item}
            if weight != 1:
                e['weight'] = weight
            if (lo, hi) != (1, 1):
                e['functions'] = [{'function': 'minecraft:set_count',
                                   'count': {'type': 'minecraft:uniform', 'min': float(lo), 'max': float(hi)}}]
            p['entries'].append(e)
        if chance is not None:
            p['conditions'] = [{'condition': 'minecraft:random_chance', 'chance': chance}]
        out.append(p)
    data = {'type': 'minecraft:chest', 'pools': out, 'random_sequence': 'oldrepublic:' + path}
    f = os.path.join(LOOT, path + '.json')
    os.makedirs(os.path.dirname(f), exist_ok=True)
    text = json.dumps(data, indent=2) + '\n'
    if not (os.path.exists(f) and open(f).read() == text):
        with open(f, 'w') as fh:
            fh.write(text)
    return f
