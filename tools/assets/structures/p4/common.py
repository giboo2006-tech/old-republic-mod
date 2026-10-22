"""Tyrvann's shared building kit (worker P4, Milestone 6): materials, ground, mire footings, props,
overgrowth, checks, loot and previews for the storm jungle's structures.

Every generator in this folder imports it:

    from common import *          # Template, S, AIR, shapes, nature, palettes, mod, meta, W, jigsaw, B, Noise

Tyrvann (docs/MILESTONE_6.md 5.6; the survey's planets.txt 6): black basalt and stormstone, standing black
water, roots through everything, pale hanging moss, lightning that never stops (stormglass grows where it
strikes), the Long Hymn's song-engines of verdigris copper, Clan Hask's dark wood and hides.

The mire rule: a template that can land in the mire keeps something solid in its bottom layer under
every column that carries anything (stilts, footings, banks), so the pool element's support columns
reach the bed of the water; nothing stands on the water. Open water inside a template is left unset
(the world's water stays) or set as water in a basin with walls; explicit air is never set at or below
the water line outside a closed room.

All randomness comes from seeded rngs and Noise, so every run writes the same bytes.
"""
import argparse
import json
import math
import os
import random
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..'))

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta, worldgen as W, jigsaw  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402,F401
from structlib.nature import Noise  # noqa: E402,F401
from structlib.palettes import Palette  # noqa: E402,F401

ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'oldrepublic')
LOOT = os.path.join(DATA, 'loot_table')

WATER = 'minecraft:water'

# ---------------------------------------------------------------- materials

# The Hymn's masonry: the stormstone family, dressed deepslate and black basalt.
HYMN = Palette(
    wall=[('or:stormstone_bricks', 7), ('or:stormstone', 2), ('minecraft:deepslate_bricks', 2),
          ('minecraft:cracked_deepslate_bricks', 1)],
    rough=[('or:stormstone', 5), ('minecraft:cobbled_deepslate', 2), ('minecraft:basalt[axis=y]', 1),
           ('minecraft:tuff', 1)],
    floor=[('or:stormstone', 3), ('or:stormstone_bricks', 2), ('minecraft:deepslate_tiles', 2),
           ('minecraft:cracked_deepslate_tiles', 1), ('minecraft:polished_deepslate', 1)],
    plinth=[('minecraft:polished_deepslate', 3), ('minecraft:deepslate_tiles', 2), ('or:stormstone', 1)],
    dark=[('minecraft:polished_blackstone_bricks', 4), ('minecraft:cracked_polished_blackstone_bricks', 2),
          ('minecraft:blackstone', 2)],
    basalt=[('minecraft:basalt[axis=y]', 4), ('minecraft:smooth_basalt', 3), ('minecraft:blackstone', 2),
            ('minecraft:polished_basalt[axis=y]', 1)],
    trim='minecraft:polished_deepslate',
    chisel='minecraft:chiseled_deepslate',
    pillar='minecraft:polished_basalt[axis=y]',
    stairs='or:stormstone_brick_stairs',
    slab='or:stormstone_brick_slab',
    wall_top='or:stormstone_brick_wall',
    rubble=[('or:stormstone', 3), ('minecraft:cobbled_deepslate', 2), ('minecraft:tuff', 1),
            ('or:stormstone_brick_slab[type=bottom]', 2)],
)

# The silenced city's pale roofs: pale oak shingles, diorite tiles, calcite ridges.
PALE = Palette(
    roof=[('minecraft:pale_oak_planks', 5), ('minecraft:polished_diorite', 3), ('minecraft:calcite', 1)],
    stairs=[('minecraft:pale_oak_stairs', 5), ('minecraft:polished_diorite_stairs', 3), ('minecraft:diorite_stairs', 1)],
    slab=[('minecraft:pale_oak_slab', 4), ('minecraft:polished_diorite_slab', 3)],
    ridge='minecraft:calcite',
    plaster=[('minecraft:calcite', 3), ('minecraft:diorite', 2), ('minecraft:polished_diorite', 1)],
)

# The Hymn's song-engines: verdigris copper pipes, grates and bars.
COPPER = Palette(
    plate=[('minecraft:oxidized_cut_copper', 5), ('minecraft:weathered_cut_copper', 2), ('minecraft:oxidized_copper', 2)],
    pipe='minecraft:oxidized_lightning_rod',
    grate='minecraft:oxidized_copper_grate[waterlogged=false]',
    stairs='minecraft:oxidized_cut_copper_stairs',
    slab='minecraft:oxidized_cut_copper_slab',
)

# Ground of the stormwood and the mire: what a worn camp floor or an old court is bedded in.
GROUND = Palette(
    top=[('minecraft:podzol', 3), ('minecraft:coarse_dirt', 3), ('minecraft:mud', 2), ('or:peat', 2),
         ('minecraft:moss_block', 2), ('minecraft:rooted_dirt', 1), ('minecraft:packed_mud', 1)],
    worn=[('minecraft:packed_mud', 3), ('minecraft:coarse_dirt', 3), ('or:peat', 2), ('minecraft:gravel', 1),
          ('minecraft:mud', 1)],
    under=[('minecraft:mud', 4), ('or:peat', 2), ('minecraft:clay', 1), ('minecraft:packed_mud', 1),
           ('minecraft:rooted_dirt', 1)],
    bank=[('minecraft:mud', 4), ('minecraft:muddy_mangrove_roots', 2), ('or:peat', 2), ('minecraft:moss_block', 1)],
)

STONE_AGE = {   # weathering by cause: cracks everywhere, moss low and where water runs
    'or:stormstone_bricks': [('minecraft:cracked_deepslate_bricks', 0.07), ('or:stormstone', 0.06)],
    'minecraft:deepslate_bricks': [('minecraft:cracked_deepslate_bricks', 0.25)],
    'minecraft:deepslate_tiles': [('minecraft:cracked_deepslate_tiles', 0.25)],
    'minecraft:polished_blackstone_bricks': [('minecraft:cracked_polished_blackstone_bricks', 0.25)],
}
STONE_MOSS = {
    'or:stormstone_bricks': [('minecraft:mossy_cobblestone', 0.10), ('minecraft:moss_block', 0.05)],
    'or:stormstone': [('minecraft:mossy_cobblestone', 0.12), ('minecraft:moss_block', 0.06)],
    'minecraft:deepslate_bricks': [('minecraft:mossy_cobblestone', 0.10)],
    'minecraft:cobbled_deepslate': [('minecraft:mossy_cobblestone', 0.15)],
    'minecraft:polished_deepslate': [('minecraft:moss_block', 0.05)],
}

PLANTS_WET = [('minecraft:short_grass', 6), ('minecraft:fern', 4), ('minecraft:moss_carpet', 3),
              ('minecraft:leaf_litter[facing=north,segment_amount=3]', 2), ('minecraft:firefly_bush', 1),
              ('minecraft:brown_mushroom', 1), ('minecraft:bush', 1)]
PLANTS_DRY = [('minecraft:short_grass', 4), ('minecraft:fern', 2), ('minecraft:dead_bush', 1),
              ('minecraft:leaf_litter[facing=east,segment_amount=2]', 2), ('minecraft:moss_carpet', 2)]


# ---------------------------------------------------------------- small state helpers

def stair(name, facing, half='bottom'):
    return S(name, facing=facing, half=half, shape='straight', waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def log(name, axis='y'):
    return S(name, axis=axis)


def candle(n=1, color='black', lit=True):
    name = 'candle' if color is None else '%s_candle' % color
    return S(name, candles=max(1, min(4, n)), lit=lit, waterlogged=False)


def lantern(soul=True, hanging=False, copper=False):
    if copper:
        return S('oxidized_copper_lantern', hanging=hanging, waterlogged=False)
    return S('soul_lantern' if soul else 'lantern', hanging=hanging, waterlogged=False)


def campfire(soul=False, lit=True, facing='north', signal=False):
    return S('soul_campfire' if soul else 'campfire', facing=facing, lit=lit, signal_fire=signal, waterlogged=False)


def rod(oxidized=True, facing='up', kind=None):
    name = kind or ('oxidized_lightning_rod' if oxidized else 'lightning_rod')
    return S(name, facing=facing, powered=False, waterlogged=False)


def chain(axis='y', copper=False):
    return S('oxidized_copper_chain' if copper else 'iron_chain', axis=axis, waterlogged=False)


def trapdoor(name, facing, half='bottom', open_=False):
    return S(name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def skull(rotation=0):
    return S('skeleton_skull', rotation=rotation % 16)


def wall_skull(facing):
    return S('skeleton_wall_skull', facing=facing)


def cluster(facing='up'):
    return S('or:stormglass_cluster', facing=facing)


def moss_hang(tip):
    return S('pale_hanging_moss', tip=tip)


def roots(waterlogged=False):
    return S('mangrove_roots', waterlogged=waterlogged)


def bell(facing='north', attachment='ceiling'):
    return S('bell', attachment=attachment, facing=facing, powered=False)


def pot(facing='north'):
    return S('decorated_pot', facing=facing, cracked=False, waterlogged=False)


def barrel(facing='up'):
    return S('barrel', facing=facing, open=False)


def bed(t, x, y, z, facing, color='brown'):
    """A bed whose head lies one cell toward `facing` from (x, y, z) (the foot)."""
    dx, dz = B.step(facing)
    t.set(x, y, z, S('%s_bed' % color, facing=facing, occupied=False, part='foot'))
    t.set(x + dx, y, z + dz, S('%s_bed' % color, facing=facing, occupied=False, part='head'))


def pick(rng, choices):
    total = sum(w for _, w in choices)
    r = rng.random() * total
    for s, w in choices:
        r -= w
        if r <= 0:
            return s
    return choices[-1][0]


def patch(noise, choices, jitter=None, mix=0.25):
    """A callable f(x, y, z) choosing from weighted choices by coherent noise (patches, not salt and pepper)."""
    total = sum(w for _, w in choices)

    def f(x, y, z):
        v = noise.at(x, y, z)
        if jitter is not None:
            v = (1 - mix) * v + mix * jitter.at(x, y, z)
        r = v * total
        for s, w in choices:
            r -= w
            if r <= 0:
                return s
        return choices[-1][0]
    return f


def opposite(d):
    return B.opposite(d)


DIRS4 = ('north', 'east', 'south', 'west')


# ---------------------------------------------------------------- ground

def blob(sx, sz, cx, cz, rx, rz, noise, wobble=0.25, scale=1.0):
    """The cells (x, z) of a noisy ellipse: a natural outline for an island, a clearing or a court."""
    out = set()
    for x in range(sx):
        for z in range(sz):
            dx, dz = (x - cx) / rx, (z - cz) / rz
            d = math.sqrt(dx * dx + dz * dz)
            ang = math.atan2(dz, dx)
            k = 1.0 + wobble * (noise.at(math.cos(ang) * 4 * scale + 50, math.sin(ang) * 4 * scale + 50, 0) * 2 - 1)
            if d <= k:
                out.add((x, z))
    return out


def earth(t, cells, top_y, top, under, bottom=0, skip=None):
    """Fill earth under a set of (x, z) cells: `top` at top_y, `under` down to `bottom` (states or callables)."""
    for (x, z) in cells:
        if skip and (x, z) in skip:
            continue
        for y in range(bottom, top_y + 1):
            st = top if y == top_y else under
            t.set(x, y, z, st(x, y, z) if callable(st) else st)


def clear_air(t, cells, y0, y1, only_unset=True):
    """Explicit air over cells (clears plants, terrain or a sapling the world has there)."""
    for (x, z) in cells:
        for y in range(y0, y1 + 1):
            if not only_unset or (x, y, z) not in t.cells:
                t.set(x, y, z, AIR)


def column(t, x, z, y0, y1, state):
    for y in range(min(y0, y1), max(y0, y1) + 1):
        t.set(x, y, z, state(x, y, z) if callable(state) else state)


def footing(t, x, z, y_top, state, bottom=0):
    """A solid footing from the bottom layer up to y_top under (x, z): the pool element's support columns
    continue it down to the bed of the water or the ground."""
    column(t, x, z, bottom, y_top, state)


def top_solid(t, x, z, y_from=None):
    """The y of the highest solid cell of a column at or below y_from (None if there is none)."""
    y = t.sy - 1 if y_from is None else y_from
    while y >= 0:
        if t.is_solid(x, y, z):
            return y
        y -= 1
    return None


# ---------------------------------------------------------------- overgrowth and decay by cause

def hang_moss(t, rng, region, chance=0.12, max_len=4, only_template=True):
    """Pale hanging moss under overhangs: from the underside of a solid cell into air below it."""
    (x0, y0, z0), (x1, y1, z1) = region
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(max(y0, y1), min(y0, y1) - 1, -1):
                if not t.is_solid(x, y, z) or not t.is_air(x, y - 1, z):
                    continue
                if only_template and (x, y - 1, z) not in t.cells:
                    continue
                name = t.name_at(x, y, z) or ''
                if any(name.endswith(s) for s in ('_slab', '_stairs', 'lantern', '_chain', 'chain', '_bars', 'moss',
                                                  '_fence', '_wall', 'rod', 'bell', 'candle', 'glass_pane')):
                    continue
                if rng.random() >= chance:
                    continue
                n = rng.randint(1, max_len)
                cells = []
                for k in range(1, n + 1):
                    q = (x, y - k, z)
                    if not t.inside(*q) or not t.is_air(*q) or (only_template and q not in t.cells):
                        break
                    if not t.is_air(x, y - k - 1, z) and k < n:
                        break
                    cells.append(q)
                for i, q in enumerate(cells):
                    t.set(*q, moss_hang(i == len(cells) - 1))


def hang_roots(t, rng, region, chance=0.08):
    """Hanging roots under floors and ceilings, where something grows above."""
    (x0, y0, z0), (x1, y1, z1) = region
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                if t.is_solid(x, y, z) and t.is_air(x, y - 1, z) and (x, y - 1, z) in t.cells \
                        and nature._full_block(t.name_at(x, y, z)) and rng.random() < chance:
                    t.set(x, y - 1, z, S('hanging_roots', waterlogged=False))


def root_veins(t, rng, start, length, down=True, state=None):
    """A mangrove root that worms through masonry: a chain of root blocks wandering downward."""
    state = state or roots()
    x, y, z = start
    for _ in range(length):
        if t.inside(x, y, z) and t.name_at(x, y, z) not in ('minecraft:chest', 'oldrepublic:verse_stone',
                                                            'oldrepublic:site_spawner'):
            t.set(x, y, z, state)
        r = rng.random()
        if r < 0.45 and down:
            y -= 1
        elif r < 0.65:
            x += rng.choice((-1, 1))
        elif r < 0.85:
            z += rng.choice((-1, 1))
        else:
            y += 0 if down else 1
        if y < 0:
            break


def vines_on(t, rng, region, chance=0.1, max_len=4):
    """Vines on exposed faces of solid blocks in a region (only into air cells the template sets)."""
    nature.overgrow(t, region=region, rng=rng, vines=chance, moss=0.0, vine_max=max_len)


def moss_tops(t, rng, region, chance=0.25, carpet='minecraft:moss_carpet'):
    """Moss carpets on top of walls and ledges (into air the template sets)."""
    (x0, y0, z0), (x1, y1, z1) = region
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                if t.is_solid(x, y, z) and nature._full_block(t.name_at(x, y, z)) and (x, y + 1, z) in t.cells \
                        and t.is_air(x, y + 1, z) and rng.random() < chance:
                    t.set(x, y + 1, z, carpet)


def scorch(t, rng, cx, cz, y_of, r, density=0.6):
    """Lightning scorch: decals radiating from a strike, thinning outward."""
    for x in range(int(cx - r), int(cx + r) + 1):
        for z in range(int(cz - r), int(cz + r) + 1):
            d = math.hypot(x - cx, z - cz)
            if d > r or rng.random() > density * (1 - d / (r + 0.5)):
                continue
            y = y_of(x, z)
            if y is None or not t.inside(x, y, z) or not t.is_air(x, y, z):
                continue
            if not t.is_solid(x, y - 1, z) or not nature._full_block(t.name_at(x, y - 1, z)):
                continue
            t.set(x, y, z, 'oldrepublic:scorch_mark')


def scatter_on(t, rng, cells, y_of, choices, density, need_air=True):
    """Put small things on the ground cells (x, z) at y_of(x, z) (the air cell above the ground)."""
    for (x, z) in sorted(cells):
        if rng.random() >= density:
            continue
        y = y_of(x, z)
        if y is None or not t.inside(x, y, z):
            continue
        if need_air and not (t.is_air(x, y, z)):
            continue
        below = t.name_at(x, y - 1, z)
        if below is None or not t.is_solid(x, y - 1, z) or not nature._full_block(below):
            continue
        st = pick(rng, choices)
        t.set(x, y, z, st)
        if 'half=lower' in st and t.inside(x, y + 1, z):
            t.set(x, y + 1, z, st.replace('half=lower', 'half=upper'))


def rubble(t, rng, x, y, z, states, n=4, spread=1.5):
    """A small fall of stones around (x, z) landing on the first solid cell at or below y."""
    for _ in range(n):
        px = int(round(x + rng.uniform(-spread, spread)))
        pz = int(round(z + rng.uniform(-spread, spread)))
        py = y
        while py > 0 and t.is_air(px, py - 1, pz) and (px, py - 1, pz) in t.cells:
            py -= 1
        if not t.inside(px, py, pz) or not t.is_air(px, py, pz) or not t.is_solid(px, py - 1, pz):
            continue
        t.set(px, py, pz, pick(rng, states))


# ---------------------------------------------------------------- props

def kneeler(t, x, y, z, facing, state='or:stormstone_brick_stairs'):
    """A low kneeling stone facing `facing` (a stair whose back is toward the singer's knees)."""
    t.set(x, y, z, stair(state, opposite(facing)))


def candles_at(t, rng, spots, colors=('black', 'black', 'gray', 'white')):
    for (x, y, z) in spots:
        t.set(x, y, z, candle(rng.randint(1, 4), rng.choice(colors)))


def hanging_lantern(t, x, y_top, z, drop, soul=True, copper_chain=False, copper=False):
    """A lantern hanging `drop` cells below the block at y_top (chains between)."""
    for k in range(1, drop):
        t.set(x, y_top - k, z, chain('y', copper=copper_chain))
    t.set(x, y_top - drop, z, lantern(soul=soul, hanging=True, copper=copper))


def banner_pole(t, x, y0, z, height, facing, color, patterns=(), wood='dark_oak'):
    """A pole of fence with a wall banner hanging off its top on the `facing` side."""
    for y in range(y0, y0 + height):
        t.set(x, y, z, S('%s_fence' % wood))
    dx, dz = B.step(facing)
    t.set(x, y0 + height, z, log('%s_log' % wood, 'y'))
    mod.banner(t, x + dx, y0 + height - 1, z + dz, facing, color, patterns)


def stele(t, rng, x, z, y0, h, top=None, mat=None, lean=None):
    """A standing stone of h courses on y0 (a 1x1 shaft), optional cap; lean (dx, dz) offsets the top third."""
    mat = mat or HYMN.picker('basalt', rng)
    for k in range(h):
        ox, oz = (0, 0)
        if lean and k >= max(1, (2 * h) // 3):
            ox, oz = lean
        st = mat(x + ox, y0 + k, z + oz) if callable(mat) else mat
        t.set(x + ox, y0 + k, z + oz, st)
    if top:
        ox, oz = lean if (lean and h >= 2) else (0, 0)
        t.set(x + ox, y0 + h, z + oz, top)


# ---------------------------------------------------------------- checks

PASSABLE_EXTRA = ('minecraft:water',)


def _passable(t, p):
    s = t.cells.get(p)
    if s is None:
        return True
    return mod.walk_through(s) or B.name_of(s) in PASSABLE_EXTRA or B.name_of(s).endswith('_door') \
        or B.name_of(s) in ('oldrepublic:site_spawner', 'oldrepublic:hub_spawner')


def _floor_ok(t, p):
    s = t.cells.get(p)
    if s is None:
        return False
    n = B.name_of(s)
    if n in ('minecraft:air', 'minecraft:cave_air', 'minecraft:water', 'minecraft:light'):
        return False
    return not mod.walk_through(s) or n.endswith('_carpet')


def walk(t, start, natural_floor_below=None):
    """Cells a person can reach on foot from start (feet cell): standing cells with a floor below, steps of
    at most one up (stairs and slabs count as half steps) and drops of at most three. Unset cells are open
    air; natural_floor_below(x, z) -> y may name the natural ground's feet height for unset columns."""
    seen = {start}
    q = deque([start])
    sx, sy, sz = t.size
    while q:
        x, y, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < sx and 0 <= nz < sz):
                continue
            for dy in (0, 1, -1, -2, -3):
                ny = y + dy
                if ny < 1 or ny >= sy - 1:
                    continue
                p = (nx, ny, nz)
                if p in seen:
                    break
                if not _passable(t, p) or not _passable(t, (nx, ny + 1, nz)):
                    continue
                if dy == 1 and not _passable(t, (x, y + 2, z)):
                    continue
                below = (nx, ny - 1, nz)
                ok = _floor_ok(t, below)
                if not ok and natural_floor_below is not None and below not in t.cells:
                    ok = natural_floor_below(nx, nz) == ny
                if not ok:
                    continue
                if dy < 0 and any(not _passable(t, (nx, y + k, nz)) for k in range(dy, 2)):
                    continue
                seen.add(p)
                q.append(p)
                break
    return seen


def require_reach(t, start, targets, natural_floor_below=None, label=''):
    """Raises when a target feet cell (or a cell next to a target block) cannot be reached from start."""
    seen = walk(t, start, natural_floor_below)
    bad = []
    for name, (x, y, z), mode in targets:
        if mode == 'feet':
            ok = (x, y, z) in seen
        else:  # 'touch': stand within reach of the block
            ok = any(abs(px - x) + abs(pz - z) <= 3 and -2 <= y - py <= 2 for (px, py, pz) in seen)
        if not ok:
            bad.append('%s %s' % (name, (x, y, z)))
    if bad:
        raise SystemExit('%s: cannot walk to %s from %s' % (label or t.name, ', '.join(bad), start))
    return seen


def count_solid(t):
    return t.count()


# ---------------------------------------------------------------- loot

def _entry(name, weight=1, lo=None, hi=None, extra=()):
    e = {'name': name if ':' in name else 'minecraft:' + name, 'type': 'minecraft:item', 'weight': weight}
    funcs = []
    if lo is not None and (hi is None or hi != 1 or lo != 1):
        funcs.append({'add': False, 'count': {'max': float(hi if hi is not None else lo), 'min': float(lo),
                                              'type': 'minecraft:uniform'}, 'function': 'minecraft:set_count'})
    funcs += list(extra)
    if funcs:
        e['functions'] = funcs
    return e


def loot_table(path, pools):
    """Writes data/oldrepublic/loot_table/<path>.json in the repository's chest style. pools: [(rolls, entries)]
    where rolls is a number or (lo, hi) and entries are (item, weight, lo, hi)."""
    out = []
    for rolls, entries in pools:
        r = float(rolls) if not isinstance(rolls, tuple) else {'max': float(rolls[1]), 'min': float(rolls[0]),
                                                              'type': 'minecraft:uniform'}
        out.append({'entries': [_entry(*e) for e in entries], 'rolls': r})
    data = {'pools': out, 'random_sequence': 'oldrepublic:' + path, 'type': 'minecraft:chest'}
    p = os.path.join(LOOT, path + '.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    text = json.dumps(data, indent=2, sort_keys=True) + '\n'
    if not (os.path.exists(p) and open(p).read() == text):
        with open(p, 'w') as f:
            f.write(text)
    return p


def structure_tag(tag_path, structures):
    """tags/worldgen/structure/<tag_path>.json with optional entries (the locator's style)."""
    p = os.path.join(DATA, 'tags', 'worldgen', 'structure', tag_path + '.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    data = {'values': [{'id': s if ':' in s else 'oldrepublic:' + s, 'required': False} for s in structures]}
    text = json.dumps(data, indent=2, sort_keys=True) + '\n'
    if not (os.path.exists(p) and open(p).read() == text):
        with open(p, 'w') as f:
            f.write(text)
    return p


# ---------------------------------------------------------------- running and previews

def args(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', default=None, help='render preview sheets into this folder (none by default)')
    ap.add_argument('--only', default=None, help='build only the template whose id contains this text')
    return ap.parse_args(argv)


def wanted(opts, template_id):
    return not opts.only or opts.only in template_id


_PATCHED = []


def _patch_preview(P):
    """The preview knows only the plain lightning rod and draws a few thin 26.2 blocks as full cubes; draw
    them as the game does (a workaround kept in this folder: structlib is not ours to change)."""
    if _PATCHED:
        return
    base = P.shape

    def shape(block_id, props):
        name = block_id.partition(':')[2]
        if name.endswith('_lightning_rod'):
            return base('minecraft:lightning_rod', props)
        if name in ('pale_hanging_moss', 'firefly_bush', 'hanging_roots'):
            return [(0.2, 0, 0.2, 0.8, 0.85, 0.8)]
        if name == 'pale_moss_carpet':
            return [(0, 0, 0, 1, 0.0625, 1)]
        return base(block_id, props)
    P.shape = shape
    _PATCHED.append(True)


def render(t, out, title, views=('se', 'nw'), plans='auto', cut=None, ground=None, width=1600):
    from structlib import preview as P
    _patch_preview(P)
    img = P.sheet(t.to_nbt(), title=title, views=views, plans=plans, cut=cut, ground=ground, width=width)
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    img.convert('RGB').save(out)
    return out


def save(t, template_id, opts=None, views=('se', 'nw'), plans='auto', cut=None, ground=None, width=1600,
         suffix=''):
    """Saves the template (and its sidecar) and, when opts.preview names a folder, renders a sheet there."""
    t.save(template_id)
    if opts is not None and getattr(opts, 'preview', None):
        from structlib import preview as P
        _patch_preview(P)
        img = P.sheet(t.to_nbt(), title=template_id, views=views, plans=plans, cut=cut, ground=ground, width=width)
        os.makedirs(opts.preview, exist_ok=True)
        out = os.path.join(opts.preview, template_id.replace('/', '__') + suffix + '.png')
        img.convert('RGB').save(out)
        print('preview', out)
    print('saved %s %s, %d blocks, anchors %s' % (template_id, 'x'.join(map(str, t.size)), t.count(),
                                                  ', '.join(sorted(t.anchors))))
    return t


# ---------------------------------------------------------------- worldgen guards and shared builders

TREE_GUARD = 'minecraft:light[level=0]'


def tree_guard(t, cells, y, step=3):
    """An invisible lattice of level-0 light blocks over open courts of a worldgen template.

    Tyrvann's tree features (stormwood_trees, mire_trees) have no soil filter, so after the structures
    are placed a tree can root on any flagstone or roof with free space above it. A tree needs the 3x3
    column above its first trunk block free (two_layers_feature_size) and a light block is not free
    (not air, not replaceable by trees): a lattice of one cell in every 3x3 at y stops them, unseen and
    with no collision. Only cells the template already sets as air get one (never terrain)."""
    for (x, z) in cells:
        if x % step == 0 and z % step == 0 and t.inside(x, y, z):
            s = t.cells.get((x, y, z))
            if s is not None and B.name_of(s) == 'minecraft:air':
                t.set(x, y, z, TREE_GUARD)


def superellipse(sx, sz, cx, cz, rx, rz, power=4.0, noise=None, amp=0.0, freq=1.0):
    """Cells of a rounded rectangle (|dx|^p + |dz|^p <= 1), its rim eroded by noise."""
    out = set()
    for x in range(sx):
        for z in range(sz):
            d = (abs(x - cx) / rx) ** power + (abs(z - cz) / rz) ** power
            d = d ** (1.0 / power)
            lim = 1.0 - (amp * noise.at(x * freq, z * freq) if noise is not None else 0.0)
            if d <= lim:
                out.add((x, z))
    return out


def ring_of(cells):
    """The outer ring of a set of (x, z) cells (cells with a 4-neighbour outside the set)."""
    return {(x, z) for (x, z) in cells
            if any((x + dx, z + dz) not in cells for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def flag_floor(t, cells, y, seed, mats, size=2, origin=(0, 0), lost=None, lost_state=None, crack=0.35):
    """A floor of big square flags (size x size cells share one stone), some lost to the ground.

    mats: weighted [(state, weight)] chosen per flag; lost(x, z, h) -> bool decides per flag (h is the
    flag's own Random) and lost_state(x, z) gives what shows where a flag is gone. Returns the lost cells."""
    gone = set()
    for (x, z) in sorted(cells):
        gx, gz = (x - origin[0]) // size, (z - origin[1]) // size
        h = random.Random(seed * 1000003 + gx * 7919 + gz * 104729)
        mat = pick(h, mats)
        is_lost = lost(x, z, h) if lost else False
        if is_lost:
            gone.add((x, z))
            if lost_state:
                st = lost_state(x, z)
                t.set(x, y, z, st(x, y, z) if callable(st) else st)
            continue
        if h.random() < crack:
            mat = {'minecraft:deepslate_tiles': 'minecraft:cracked_deepslate_tiles',
                   'minecraft:deepslate_bricks': 'minecraft:cracked_deepslate_bricks',
                   'minecraft:polished_blackstone_bricks': 'minecraft:cracked_polished_blackstone_bricks',
                   }.get(mat, mat)
        t.set(x, y, z, mat)
    return gone


def tent(t, rng, x0, z0, width, length, y, axis='z', cloth='or:canvas', stairs='or:canvas_stairs',
         ridge='or:canvas_slab[type=top]', pole='minecraft:dark_oak_fence', door_end='low', torn=0.0,
         back_closed=True):
    """A ridge tent of hides: a roof of cloth stairs climbing to a ridge, pole tips at the ridge's ends.

    (x0, z0) is the corner; width runs across the ridge (odd: 5 or 7), length along it; axis 'z' puts the
    ridge along z. The end rows are gables of cloth under the roof; door_end ('low' or 'high' x/z) has a
    doorway in its gable, back_closed False leaves the other end open. torn rips roof cells (air or a
    sagging slab) at that rate. Returns the cells (x, z) inside where a person can stand."""
    half = width // 2
    inside = []
    slab_name = stairs.replace('_stairs', '_slab')
    for a in range(length):
        end = 'low' if a == 0 else 'high' if a == length - 1 else None
        for k in range(-half, half + 1):
            x, z = (x0 + half + k, z0 + a) if axis == 'z' else (x0 + a, z0 + half + k)
            dist = abs(k)
            ry = y + (half - dist)
            if dist == 0:
                st = ridge
            else:
                face = ('east' if k < 0 else 'west') if axis == 'z' else ('south' if k < 0 else 'north')
                st = stair(stairs, face)
                if torn and end is None and rng.random() < torn:
                    st = AIR if rng.random() < 0.5 else slab(slab_name)
            t.set(x, ry, z, st)
            gable = end is not None and (end == door_end or back_closed)
            for yy in range(y, ry):
                if gable and not (end == door_end and dist == 0 and yy < y + 2):
                    t.set(x, yy, z, cloth)
                else:
                    t.set(x, yy, z, AIR)
            if ry - y >= 2 and (not gable or (end == door_end and dist == 0)):
                inside.append((x, z))
            if dist == 0 and end is not None:
                t.set(x, ry, z, pole)
                t.set(x, ry + 1, z, pole)
    return inside


# Cells a tree may grow through (TreeFeature.isFree: air, #replaceable_by_trees, logs), and cells that do not
# block motion (the OCEAN_FLOOR heightmap the tree features stand on skips them).
_TREE_FREE = ('minecraft:air', 'minecraft:cave_air', 'minecraft:water', 'minecraft:short_grass', 'minecraft:fern',
              'minecraft:tall_grass', 'minecraft:large_fern', 'minecraft:dead_bush', 'minecraft:vine',
              'minecraft:glow_lichen', 'minecraft:hanging_roots', 'minecraft:seagrass', 'minecraft:jigsaw')
_NO_MOTION = ('minecraft:air', 'minecraft:cave_air', 'minecraft:water', 'minecraft:light', 'minecraft:lily_pad',
              'minecraft:jigsaw', 'minecraft:short_grass', 'minecraft:fern', 'minecraft:tall_grass',
              'minecraft:large_fern', 'minecraft:dead_bush', 'minecraft:vine', 'minecraft:glow_lichen',
              'minecraft:hanging_roots', 'minecraft:pale_hanging_moss', 'minecraft:firefly_bush',
              'oldrepublic:scorch_mark', 'minecraft:lever', 'minecraft:tripwire')
_NO_MOTION_END = ('_carpet', 'lantern', 'chain', 'candle', '_pressure_plate', '_button', '_torch', 'torch',
                  '_sapling', '_flower', '_tulip', 'mushroom', '_banner', 'skull', '_head', 'redstone_wire')


def _blocks_motion(name):
    return name not in _NO_MOTION and not name.endswith(_NO_MOTION_END) and not name.endswith('_leaves')


def _tree_free(name):
    return name in _TREE_FREE or name.endswith('_leaves') or name.endswith('_log') or name.endswith('_wood') \
        or name.endswith('moss') or name.endswith('_carpet')


def guard_trees(t, reach=6, step=3, columns=None):
    """The tree guard for a whole worldgen template: roofs, ruined floors, courts and walls.

    Tyrvann's tree features root a tree on the top motion-blocking cell of any column (OCEAN_FLOOR, no
    soil filter) when the 3x3 above its base is free for the trunk's height (6 or more). For every column
    the template tops, the one lattice column (x and z multiples of `step`) inside that 3x3 must hold a
    non-free cell 1..reach above the base; where none is there, an invisible level-0 light block goes in
    (into air or unset cells only). Columns the template leaves empty (the world's own ground) are left
    alone. Returns the cells it set."""
    sx, sy, sz = t.size
    tops = {}
    for (x, y, z), s in t.cells.items():
        if _blocks_motion(B.name_of(s)) and y > tops.get((x, z), -1):
            tops[(x, z)] = y
    added = []
    for (x, z), ytop in sorted(tops.items()):
        if columns is not None and (x, z) not in columns:
            continue
        base = ytop + 1
        if base >= sy:
            continue
        lx, lz = step * int(round(x / float(step))), step * int(round(z / float(step)))
        if not (0 <= lx < sx and 0 <= lz < sz):
            continue
        blocked = False
        for y in range(base + 1, min(sy, base + reach + 1)):
            s = t.cells.get((lx, y, lz))
            if s is not None and not _tree_free(B.name_of(s)):
                blocked = True
                break
        if blocked:
            continue
        for y in list(range(base + 3, min(sy, base + reach + 1))) + list(range(base + 1, base + 3)):
            if y >= sy:
                continue
            s = t.cells.get((lx, y, lz))
            if s is None or B.name_of(s) in ('minecraft:air', 'minecraft:cave_air'):
                t.set(lx, y, lz, TREE_GUARD)
                added.append((lx, y, lz))
                break
    return added


_EDGE_NEIGHBOURS = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1)
                    if 0 < abs(dx) + abs(dy) + abs(dz) <= 2]
_NOT_HOLDING = ('minecraft:air', 'minecraft:cave_air', 'minecraft:water', 'minecraft:light', 'minecraft:lava')


def prune_floating(t, region=None, keep=('minecraft:lily_pad', 'minecraft:jigsaw')):
    """Removes bits a collapse left hanging in the air: every block not joined to the template's bottom layer
    through other blocks (never through water or air) by a face or an edge (a stair roof climbs by its
    edges), inside region ((x0, y0, z0), (x1, y1, z1); default everything). Lily pads (they float) and
    jigsaws stay. Returns the cells cleared."""
    held = set()
    queue = deque()
    for (x, y, z), s in t.cells.items():
        if y == 0 and B.name_of(s) not in _NOT_HOLDING:
            held.add((x, y, z))
            queue.append((x, y, z))
    while queue:
        x, y, z = queue.popleft()
        for dx, dy, dz in _EDGE_NEIGHBOURS:
            q = (x + dx, y + dy, z + dz)
            if q in held:
                continue
            s = t.cells.get(q)
            if s is None or B.name_of(s) in _NOT_HOLDING:
                continue
            held.add(q)
            queue.append(q)
    gone = []
    for p, s in sorted(t.cells.items()):
        name = B.name_of(s)
        if p in held or name in _NOT_HOLDING or name in keep:
            continue
        if region is not None:
            (x0, y0, z0), (x1, y1, z1) = region
            if not (x0 <= p[0] <= x1 and y0 <= p[1] <= y1 and z0 <= p[2] <= z1):
                continue
        gone.append(p)
    for p in gone:
        t.set(*p, AIR)
    return gone
