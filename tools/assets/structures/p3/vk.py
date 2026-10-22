"""Varkhast (worker P3): the shared kit of the Varkhast generators (docs/MILESTONE_6.md 5.5).

Ancient, dead and dangerous: black stone, red light from below, ash drifting against walls, and the
Empire digging up what should stay buried. Every Varkhast script imports this module:

    import vk
    from vk import S, AIR, Template

Materials: the build kit's tomb_bricks and ash_bricks families, imperial_plating for the Empire and
banded terracotta for the red rock. Kit walls (ash_brick_wall, tomb_brick_wall) are not used (an early
structure checker refused their 'none' sides); vanilla blackstone and deepslate walls stand in for them.

Everything here is deterministic: noise from structlib.nature.Noise, choices from a seeded rng.
"""
import json
import math
import os
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..'))

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta, worldgen as W, jigsaw  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402
from structlib.nature import Noise  # noqa: E402
from structlib.palettes import Palette  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'oldrepublic')
LOOT = os.path.join(DATA, 'loot_table')

DIRS = ('north', 'east', 'south', 'west')
STEP = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
RIGHT = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}
LEFT = {v: k for k, v in RIGHT.items()}

GRAVITY = {'minecraft:sand', 'minecraft:red_sand', 'minecraft:gravel', 'minecraft:suspicious_sand',
           'minecraft:suspicious_gravel', 'minecraft:anvil', 'minecraft:chipped_anvil', 'minecraft:damaged_anvil',
           'minecraft:pointed_dripstone', 'minecraft:scaffolding'} | {
    'minecraft:%s_concrete_powder' % c for c in ('white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink',
                                                  'gray', 'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green',
                                                  'red', 'black')}


# ---------------------------------------------------------------- noise and material mixes

# Deciles of 0.75 * Noise(scale) + 0.25 * Noise(1.3), measured, so a mix follows its weights.
_EQ = [0.0, 0.269, 0.339, 0.391, 0.438, 0.48, 0.527, 0.583, 0.639, 0.706, 1.0]
_EQ1 = [0.0, 0.207, 0.296, 0.362, 0.42, 0.472, 0.537, 0.614, 0.682, 0.766, 1.0]


def equalize(v, table=_EQ):
    for i in range(10):
        if v <= table[i + 1]:
            a, b = table[i], table[i + 1]
            return min(1.0, max(0.0, (i + (v - a) / max(1e-9, b - a)) / 10.0))
    return 1.0


class Field:
    """Smooth noise in [0, 1], evenly spread (equalized), for heights, lean and damage."""

    def __init__(self, seed, scale=6.0):
        self.n = Noise(seed, scale)

    def __call__(self, x, y=0.0, z=0.0):
        return equalize(self.n.at(x, y, z), _EQ1)


class Mix:
    """Weighted block states in coherent noise patches whose shares follow the weights (palettes.patches
    favours the middle option). Call it as f(x, y, z) -> state."""

    def __init__(self, options, seed, scale=4.0, grain=0.25):
        if isinstance(options, str):
            options = [options]
        self.opts = [(B.build(*B.parse(s)), 1) if isinstance(s, str) else (B.build(*B.parse(s[0])), s[1])
                     for s in options]
        self.total = float(sum(w for _, w in self.opts))
        self.noise = Noise(seed, scale)
        self.jit = Noise(seed + 977, 1.3)
        self.grain = grain

    def value(self, x, y, z):
        return equalize((1 - self.grain) * self.noise.at(x, y, z) + self.grain * self.jit.at(x, y, z))

    def __call__(self, x, y, z):
        u = self.value(x, y, z) * self.total
        for s, w in self.opts:
            u -= w
            if u <= 0:
                return s
        return self.opts[-1][0]


def mix(options, seed, scale=4.0, grain=0.25):
    return Mix(options, seed, scale, grain)


def pick(rng, options):
    """A weighted choice [(value, weight)] or a plain list."""
    if options and not isinstance(options[0], tuple):
        return options[rng.randrange(len(options))]
    total = sum(w for _, w in options)
    r = rng.random() * total
    for v, w in options:
        r -= w
        if r <= 0:
            return v
    return options[-1][0]


# ---------------------------------------------------------------- palettes

TOMB = Palette(
    wall=[('or:tomb_bricks', 8), ('minecraft:polished_blackstone_bricks', 3), ('or:tomb_stone', 2),
          ('minecraft:cracked_polished_blackstone_bricks', 2), ('or:tomb_stone_cracked', 2)],
    floor=[('or:tomb_stone', 5), ('minecraft:polished_blackstone', 3), ('or:tomb_bricks', 2), ('or:tomb_stone_cracked', 2)],
    trim='or:tomb_stone_carved',
    pillar='or:tomb_pillar[axis=y]',
    stairs='or:tomb_brick_stairs',
    slab='or:tomb_brick_slab',
    wall_top='minecraft:polished_blackstone_brick_wall',
    chisel='minecraft:chiseled_polished_blackstone',
    gild='minecraft:gilded_blackstone',
    rough=[('minecraft:blackstone', 4), ('minecraft:basalt[axis=y]', 2), ('minecraft:smooth_basalt', 1)],
)

ASH = Palette(
    wall=[('or:ash_bricks', 8), ('minecraft:tuff_bricks', 2), ('minecraft:cobbled_deepslate', 1), ('minecraft:polished_tuff', 1)],
    footing=[('minecraft:cobbled_deepslate', 3), ('minecraft:blackstone', 2), ('minecraft:polished_basalt[axis=y]', 1)],
    trim='minecraft:polished_basalt[axis=x]',
    post='minecraft:polished_basalt[axis=y]',
    stairs='or:ash_brick_stairs',
    slab='or:ash_brick_slab',
    wall_top='minecraft:tuff_brick_wall',
    beam='minecraft:stripped_dark_oak_log',
    roof=[('or:rusted_plating', 3), ('or:ash_brick_slab[type=bottom]', 2), ('minecraft:dark_oak_planks', 1)],
)

IMP = Palette(
    wall=[('or:imperial_plating', 9), ('or:imperial_panel', 2), ('or:durasteel_plating', 1)],
    floor=[('or:durasteel_deck', 6), ('or:imperial_plating', 1)],
    trim='or:durasteel_beam[axis=x]',
    post='or:durasteel_beam[axis=y]',
    stairs='or:imperial_plating_stairs',
    slab='or:imperial_plating_slab',
    deck_slab='or:durasteel_deck_slab',
    crate=[('or:cargo_crate_imperial', 5), ('or:cargo_crate', 2)],
    light='or:light_panel',
    red='or:light_panel_red',
)

TERRACOTTA_BANDS = ['minecraft:terracotta', 'minecraft:red_terracotta', 'minecraft:orange_terracotta',
                    'minecraft:terracotta', 'minecraft:brown_terracotta', 'minecraft:red_terracotta',
                    'minecraft:light_gray_terracotta', 'minecraft:terracotta', 'minecraft:orange_terracotta',
                    'minecraft:red_sandstone', 'minecraft:white_terracotta', 'minecraft:red_terracotta',
                    'minecraft:brown_terracotta', 'minecraft:terracotta', 'minecraft:yellow_terracotta']


def redrock(seed, base_y=0):
    """Banded red rock (Varkhast's bandlands cliffs): horizontal strata of terracotta 1 to 3 thick that
    undulate a little; sandstone shows where the band says so. f(x, y, z) -> state."""
    rng_bands = []
    import random
    r = random.Random(seed)
    order = list(TERRACOTTA_BANDS)
    for _ in range(64):
        rng_bands += [order[r.randrange(len(order))]] * r.choice((1, 1, 2, 2, 3))
    wobble = Noise(seed + 31, 9.0)
    grit = Noise(seed + 77, 2.0)

    def f(x, y, z):
        yy = y - base_y + int(round((wobble.at(x, 0, z) - 0.5) * 3.0))
        s = rng_bands[yy % len(rng_bands)]
        g = grit.at(x, y, z)
        if g > 0.78 and s == 'minecraft:terracotta':
            return 'minecraft:red_sandstone'
        if g < 0.16 and s == 'minecraft:red_terracotta':
            return 'minecraft:smooth_red_sandstone'
        return s
    return f


def ground_mix(kind, seed):
    """The walking surface of each biome: 'ash' (ashfall), 'red' (redrock), 'valley' (tomb valley)."""
    if kind == 'ash':
        return Mix([('minecraft:gravel', 3), ('minecraft:light_gray_concrete_powder', 2), ('minecraft:tuff', 2),
                    ('minecraft:coarse_dirt', 2), ('minecraft:smooth_basalt', 1), ('minecraft:gray_concrete_powder', 1)],
                   seed, 5.0)
    if kind == 'red':
        return Mix([('minecraft:red_sand', 5), ('minecraft:coarse_dirt', 2), ('minecraft:terracotta', 1),
                    ('minecraft:gravel', 1), ('minecraft:red_sandstone', 1)], seed, 5.0)
    if kind == 'valley':
        return Mix([('minecraft:red_sand', 4), ('minecraft:gravel', 2), ('minecraft:coarse_dirt', 2),
                    ('minecraft:smooth_sandstone', 1), ('minecraft:red_sandstone', 1)], seed, 5.0)
    raise ValueError(kind)


def under_mix(kind, seed):
    """What lies under the walking surface (foundation layers that may show on a downhill edge)."""
    if kind == 'ash':
        return Mix([('minecraft:tuff', 3), ('minecraft:smooth_basalt', 2), ('minecraft:cobbled_deepslate', 1),
                    ('minecraft:gravel', 1)], seed, 6.0)
    return Mix([('minecraft:red_sandstone', 3), ('minecraft:terracotta', 2), ('minecraft:red_terracotta', 1),
                ('minecraft:smooth_red_sandstone', 1)], seed, 6.0)


# ---------------------------------------------------------------- small block helpers

def stair(name, facing, half='bottom'):
    return S(name, facing=facing, half=half, shape='straight', waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def axis_of(d):
    return 'x' if d in ('east', 'west') else 'z'


def lantern(hanging=True, soul=False):
    return S('soul_lantern' if soul else 'lantern', hanging=hanging, waterlogged=False)


def chain(axis='y'):
    return S('iron_chain', axis=axis, waterlogged=False)


def candle(color='red', n=1, lit=True):
    name = 'candle' if not color else '%s_candle' % color
    return S(name, candles=n, lit=lit, waterlogged=False)


def campfire(lit=True, facing='north', soul=False, signal=False):
    return S('soul_campfire' if soul else 'campfire', facing=facing, lit=lit, signal_fire=signal, waterlogged=False)


def trapdoor(name, facing, half='top', open_=False):
    return S(name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def door(t, x, y, z, facing, name='iron_door', hinge='left', open_=False):
    t.set(x, y, z, S(name, facing=facing, half='lower', hinge=hinge, open=open_, powered=False))
    t.set(x, y + 1, z, S(name, facing=facing, half='upper', hinge=hinge, open=open_, powered=False))


def double_door(t, x, y, z, facing, name='iron_door', open_=False):
    """Two doors side by side: the first at (x, z), the second one step to the right of facing."""
    rx, rz = STEP[RIGHT[facing]]
    door(t, x, y, z, facing, name, hinge='left', open_=open_)
    door(t, x + rx, y, z + rz, facing, name, hinge='right', open_=open_)


def bed(t, x, y, z, facing, color='black'):
    """A bed whose foot is at (x, z) and whose head lies one step toward facing."""
    dx, dz = STEP[facing]
    t.set(x, y, z, S('%s_bed' % color, facing=facing, part='foot', occupied=False))
    t.set(x + dx, y, z + dz, S('%s_bed' % color, facing=facing, part='head', occupied=False))


def lever(facing, on=False):
    return S('lever', face='wall', facing=facing, powered=on)


def skull(rotation=0, wither=False):
    return S('wither_skeleton_skull' if wither else 'skeleton_skull', rotation=rotation, powered=False)


def wall_skull(facing, wither=False):
    return S('wither_skeleton_wall_skull' if wither else 'skeleton_wall_skull', facing=facing, powered=False)


def bone(axis='x'):
    return S('bone_block', axis=axis)


def barrel(facing='up'):
    return S('barrel', facing=facing, open=False)


def is_air_state(s):
    return s is not None and B.name_of(s) in ('minecraft:air', 'minecraft:cave_air')


def put(t, x, y, z, state):
    s = state(x, y, z) if callable(state) else state
    t.set(x, y, z, s)


def put_if_air(t, x, y, z, state):
    """Set the cell only where the template has air or nothing."""
    if t.is_air(x, y, z):
        put(t, x, y, z, state)
        return True
    return False


# ---------------------------------------------------------------- terrain

def foundation(t, x0, z0, x1, z1, top, state, mask=None):
    """Solid layers from y 0 up to `top` (inclusive) under a footprint, so the support pass of the pool
    element (or the runtime placer) finds a solid bottom layer and footings reach the ground."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if mask is not None and not mask(x, z):
                continue
            for y in range(0, top + 1):
                put(t, x, y, z, state)


def heightfield_mass(t, cells, height, state, top_state=None, y0=0, overhang=None):
    """Fill rock columns: cells is an iterable of (x, z); height(x, z) gives the top y (inclusive).
    top_state paints the top cell of each column (red sand on a mesa)."""
    for (x, z) in cells:
        h = height(x, z)
        if h is None or h < y0:
            continue
        for y in range(y0, h + 1):
            put(t, x, y, z, state)
        if top_state is not None:
            put(t, x, h, z, top_state)


def clear_air(t, x0, y0, z0, x1, y1, z1, only_unset=False):
    """Explicit air (the world is cleared there). only_unset keeps cells the template already set."""
    for y in range(min(y0, y1), max(y0, y1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                if not t.inside(x, y, z):
                    continue
                if only_unset and (x, y, z) in t.cells:
                    continue
                t.set(x, y, z, AIR)


def column_top(t, x, z, y_from=None, y_to=0):
    """The highest solid cell at or below y_from in a column, or None."""
    y_from = t.sy - 1 if y_from is None else y_from
    for y in range(y_from, y_to - 1, -1):
        if t.is_solid(x, y, z):
            return y
    return None


# ---------------------------------------------------------------- ash and sand drifts

def drift(t, region, material, wind='west', depth=3, reach=4, seed=0, cap=None, cap_chance=0.6,
          against=None, only_air=False, floor=None):
    """Pile ash (or sand) on the windward side of obstacles, highest against the face the wind meets and
    thinning over `reach` cells. region (x0, y0, z0, x1, y1, z1) bounds the floor cells looked at;
    material f(x, y, z) or a state; cap: a thin top state (a carpet) laid on the last block at the drift's
    edge. against: block names that catch ash (None: any solid full block). only_air: only fill cells
    the template already set to air (inside a building), else unset cells too (open ground).
    floor: block names a drift may rest on (None: any solid full block)."""
    x0, y0, z0, x1, y1, z1 = region
    wx, wz = STEP[OPP[wind]]  # the wind blows from `wind` toward the opposite side
    n = Field(seed, 3.0)
    against = {B.full_name(a) for a in against} if against else None
    floor = {B.full_name(a) for a in floor} if floor else None
    placed = []
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                if not _drift_floor(t, x, y, z, floor):
                    continue
                if not _open(t, x, y + 1, z, only_air):
                    continue
                # the nearest obstacle downwind at drift height
                dist = None
                for d in range(1, reach + 1):
                    qx, qz = x + wx * d, z + wz * d
                    if not t.inside(qx, y + 1, qz):
                        break
                    s = t.get(qx, y + 1, qz)
                    if s is not None and _catches(s, against):
                        dist = d
                        break
                    if not _open(t, qx, y + 1, qz, only_air) and s is not None:
                        break
                if dist is None:
                    continue
                h = depth * (1.0 - (dist - 1) / float(reach)) + (n(x, y, z) - 0.5) * 1.6
                k = int(math.floor(h))
                for i in range(1, k + 1):
                    if not t.inside(x, y + i, z) or not _open(t, x, y + i, z, only_air):
                        k = i - 1
                        break
                    put(t, x, y + i, z, material)
                    placed.append((x, y + i, z))
                if cap is not None and h - k > 0.35 and n(x + 7, y, z) < cap_chance:
                    top = y + k + 1
                    if t.inside(x, top, z) and _open(t, x, top, z, only_air):
                        put(t, x, top, z, cap)
                break
    return placed


def _drift_floor(t, x, y, z, floor):
    s = t.get(x, y, z)
    if s is None or not t.is_solid(x, y, z):
        return False
    n = B.name_of(s)
    if floor is not None:
        return n in floor
    return nature._full_block(n)


def _open(t, x, y, z, only_air):
    s = t.get(x, y, z)
    if s is None:
        return not only_air
    return is_air_state(s)


def _catches(s, against):
    n = B.name_of(s)
    if is_air_state(s):
        return False
    if against is not None:
        return n in against
    return nature._full_block(n) or n.endswith('_stairs') or n.endswith('_wall') or n.endswith('_slab')


def ash_cover(t, cells, seed, carpet='minecraft:light_gray_carpet', chance=0.5):
    """A thin film of ash on floors: carpets in noise patches on the given (x, y, z) feet cells."""
    f = Field(seed, 3.5)
    for (x, y, z) in cells:
        if f(x, y, z) < chance and is_air_state(t.get(x, y, z)) or (f(x, y, z) < chance and t.get(x, y, z) is None):
            if t.is_solid(x, y - 1, z) and nature._full_block(B.name_of(t.get(x, y - 1, z))):
                t.set(x, y, z, carpet)


# ---------------------------------------------------------------- lights

def lava_brazier(t, x, y, z, post='minecraft:polished_blackstone_wall', h=1, base=None):
    """A black stone post (h cells of a wall block) carrying a lava-filled bowl: red light from a fire
    pit, no fire spread. base: an optional block under the post."""
    if base is not None:
        t.set(x, y, z, base)
        y += 1
    for i in range(h):
        t.set(x, y + i, z, post)
    t.set(x, y + h, z, 'minecraft:lava_cauldron')
    return (x, y + h, z)


def fire_bowl(t, x, y, z, post='minecraft:polished_blackstone_wall', h=1, lit=True, soul=False):
    for i in range(h):
        t.set(x, y + i, z, post)
    t.set(x, y + h, z, campfire(lit=lit, soul=soul))


def hanging_lamp(t, x, y_top, z, length=2, soul=True):
    """A lantern hanging on iron chains from the block above y_top (chains from y_top down)."""
    for i in range(length):
        t.set(x, y_top - i, z, chain('y'))
    t.set(x, y_top - length, z, lantern(hanging=True, soul=soul))


def ember_fissure(t, cells, seed, glass=False):
    """Red light from below: floor cells (x, y, z) of the walking surface become magma; with glass the
    magma lies one deeper under red glass so it can be walked on."""
    f = Field(seed, 2.0)
    for (x, y, z) in cells:
        if glass:
            t.set(x, y - 1, z, 'minecraft:magma_block' if f(x, y, z) < 0.7 else 'minecraft:shroomlight')
            t.set(x, y, z, 'minecraft:red_stained_glass')
        else:
            t.set(x, y, z, 'minecraft:magma_block')


def fissure_path(rng, start, length, wander=0.5, step_dirs=None):
    """A wandering crack of (x, z) cells from start, about `length` long."""
    x, z = start
    out = [(x, z)]
    dx, dz = rng.choice(step_dirs or [(1, 0), (-1, 0), (0, 1), (0, -1)])
    for _ in range(length - 1):
        if rng.random() < wander:
            if dx == 0:
                x += rng.choice((-1, 1))
            else:
                z += rng.choice((-1, 1))
        x += dx
        z += dz
        out.append((x, z))
    return out


def flood_pole(t, x, y, z, h=5, facing='south', post='or:durasteel_beam[axis=y]', head='or:light_panel', hood=True):
    """A floodlight: a beam post with a lamp head facing one way and a trapdoor visor over it."""
    for i in range(h):
        t.set(x, y + i, z, post)
    dx, dz = STEP[facing]
    t.set(x + dx, y + h - 1, z + dz, head)
    if hood:
        t.set(x + dx, y + h, z + dz, trapdoor('iron_trapdoor', OPP[facing], half='bottom'))
    t.set(x, y + h, z, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')


# ---------------------------------------------------------------- obelisks and statues

def obelisk(t, x, y, z, h, rng, lean=(0, 0), mat=None, band=None, cap=True, plinth=True, sink=0, cracked=0.12):
    """A two-wide black obelisk whose shaft occupies (x..x+1, z..z+1) at its foot, h cells tall above
    the plinth, leaning by lean = (dx, dz) cells at the top (sheared one step at a time). The plinth is
    a 4x4 step around the foot, sunk `sink` cells on the side the obelisk leans to. Returns the cells."""
    mat = mat or Mix([('or:tomb_bricks', 6), ('minecraft:polished_blackstone_bricks', 3),
                      ('minecraft:cracked_polished_blackstone_bricks', 1)], rng.randrange(1 << 20), 3.0)
    band = band or 'minecraft:chiseled_polished_blackstone'
    cells = []
    if plinth:
        ldx = (1 if lean[0] > 0 else -1 if lean[0] < 0 else 0)
        ldz = (1 if lean[1] > 0 else -1 if lean[1] < 0 else 0)
        for px in range(x - 1, x + 3):
            for pz in range(z - 1, z + 3):
                edge = px in (x - 1, x + 2) or pz in (z - 1, z + 2)
                low = (ldx and (px - x - 0.5) * ldx > 1) or (ldz and (pz - z - 0.5) * ldz > 1)
                top = y - (sink if low else 0)
                for yy in range(max(0, top - 2), top + 1):
                    t.set(px, yy, pz, 'or:tomb_stone' if not edge else 'minecraft:polished_blackstone')
                if edge and not low and rng.random() < 0.8:
                    facing = 'north' if pz == z - 1 else 'south' if pz == z + 2 else 'west' if px == x - 1 else 'east'
                    corner = px in (x - 1, x + 2) and pz in (z - 1, z + 2)
                    if not corner:
                        t.set(px, top + 1, pz, stair('or:tomb_brick_stairs', OPP[facing]))
                    else:
                        t.set(px, top + 1, pz, slab('or:tomb_brick_slab'))
        y += 1
    band_at = int(h * 0.72)
    for i in range(h):
        ox = int(round(lean[0] * i / max(1, h - 1)))
        oz = int(round(lean[1] * i / max(1, h - 1)))
        yy = y + i
        for (cx, cz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            px, pz = x + cx + ox, z + cz + oz
            if i == band_at:
                s = band
            elif i == band_at + 1 and rng.random() < 0.5:
                s = 'minecraft:gilded_blackstone'
            else:
                s = mat(px, yy, pz)
                if rng.random() < cracked:
                    s = 'or:tomb_stone_cracked'
            t.set(px, yy, pz, s)
            cells.append((px, yy, pz))
        # a lean that steps sideways leaves the lower block's top exposed: chip it with a slab
    if cap:
        i = h
        ox = int(round(lean[0] * i / max(1, h - 1)))
        oz = int(round(lean[1] * i / max(1, h - 1)))
        yy = y + h
        t.set(x + ox, yy, z + oz, stair('or:tomb_brick_stairs', 'south'))
        t.set(x + 1 + ox, yy, z + oz, stair('or:tomb_brick_stairs', 'south'))
        t.set(x + ox, yy, z + 1 + oz, stair('or:tomb_brick_stairs', 'north'))
        t.set(x + 1 + ox, yy, z + 1 + oz, stair('or:tomb_brick_stairs', 'north'))
        cells += [(x + ox, yy, z + oz), (x + 1 + ox, yy, z + oz), (x + ox, yy, z + 1 + oz), (x + 1 + ox, yy, z + 1 + oz)]
    return cells


def fallen_obelisk(t, x, y, z, length, direction, rng, mat=None, pieces=3):
    """An obelisk lying on the ground (feet layer y) from (x, z) toward direction, broken in pieces with
    gaps and rubble between them; the stump and plinth stay at the start."""
    mat = mat or Mix([('or:tomb_bricks', 6), ('minecraft:polished_blackstone_bricks', 3),
                      ('minecraft:cracked_polished_blackstone_bricks', 2)], rng.randrange(1 << 20), 3.0)
    dx, dz = STEP[direction]
    sx, sz = STEP[RIGHT[direction]]
    ax = axis_of(direction)
    # the stump
    for i in range(2):
        for (cx, cz) in ((0, 0), (sx, sz)):
            t.set(x + cx, y + i, z + cz, mat(x + cx, y + i, z + cz))
    t.set(x, y + 2, z, 'or:tomb_stone_cracked')
    pos = 3
    cuts = sorted(rng.sample(range(4, max(5, length - 2)), min(pieces - 1, max(0, length - 6)))) if length > 6 else []
    for i in range(3, length):
        if any(abs(i - c) == 0 for c in cuts):
            # a break: rubble, not shaft
            for (cx, cz) in ((0, 0), (sx, sz)):
                px, pz = x + dx * i + cx, z + dz * i + cz
                t.set(px, y, pz, pick(rng, [('minecraft:blackstone', 2), (slab('minecraft:polished_blackstone_brick_slab'), 2),
                                            ('or:debris', 1)]))
            continue
        drop = 0
        for (cx, cz) in ((0, 0), (sx, sz)):
            px, pz = x + dx * i + cx, z + dz * i + cz
            t.set(px, y - drop, pz, S('or:tomb_pillar', axis=ax) if rng.random() < 0.15 else mat(px, y, pz))
            if i < length - 2:
                t.set(px, y + 1 - drop, pz, mat(px, y + 1, pz))
            elif i == length - 1:
                t.set(px, y + 1 - drop, pz, stair('or:tomb_brick_stairs', OPP[direction]))
        pos = i
    return pos


def obelisk3(t, x, y, z, h, rng, lean=(0, 0), mat=None, base=True, cracked=0.1, tip='minecraft:polished_blackstone_wall'):
    """A black obelisk on a stepped base. (x, z) is the middle of its shaft and y the feet layer of the
    ground it stands on: the base is a 3x3 block at y in a ring of stairs, the shaft rises from y + 1,
    h layers to the gilded tip. The lower two thirds are three wide with chamfered corners (walls), a
    carved band marks the neck, the neck is cross-shaped, and a pyramidion of stairs closes it. lean
    (dx, dz) is how far the top stands off the foot; the base sinks on the side it leans to.
    Returns the shaft's cells."""
    mat = mat or Mix([('or:tomb_bricks', 5), ('minecraft:polished_blackstone_bricks', 3), ('or:tomb_stone', 2)],
                     rng.randrange(1 << 20), 3.0)
    ldx = (lean[0] > 0) - (lean[0] < 0)
    ldz = (lean[1] > 0) - (lean[1] < 0)
    cells = []
    if base:
        for px in range(x - 2, x + 3):
            for pz in range(z - 2, z + 3):
                ring = abs(px - x) == 2 or abs(pz - z) == 2
                low = (ldx and (px - x) * ldx >= 1) or (ldz and (pz - z) * ldz >= 1)
                t.set(px, y - 1, pz, 'minecraft:polished_blackstone')
                if ring:
                    if low or (abs(px - x) == 2 and abs(pz - z) == 2):
                        continue
                    f = 'north' if pz - z == 2 else 'south' if pz - z == -2 else 'west' if px - x == 2 else 'east'
                    t.set(px, y, pz, stair('or:tomb_brick_stairs', f))
                else:
                    t.set(px, y, pz, 'minecraft:chiseled_polished_blackstone' if (px, pz) == (x, z) else
                          slab('minecraft:polished_blackstone_slab', 'top') if low else 'minecraft:polished_blackstone')
    band = max(2, int(round(h * 0.64)))
    for i in range(h):
        ox = int(round(lean[0] * i / max(1, h - 1)))
        oz = int(round(lean[1] * i / max(1, h - 1)))
        cx, cz, yy = x + ox, z + oz, y + 1 + i

        def put_(px, pz, state):
            if state is not None and not isinstance(state, str) and callable(state):
                state = state(px, yy, pz)
            if state is not None and isinstance(state, str) and rng.random() < cracked and \
                    B.name_of(state) in ('oldrepublic:tomb_bricks', 'minecraft:polished_blackstone_bricks'):
                state = 'or:tomb_stone_cracked'
            t.set(px, yy, pz, state)
            cells.append((px, yy, pz))

        sides = ((0, -1, 'south'), (0, 1, 'north'), (1, 0, 'west'), (-1, 0, 'east'))
        if i < band:
            put_(cx, cz, mat)
            for (dx, dz, _) in sides:
                put_(cx + dx, cz + dz, mat)
            for (dx, dz) in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
                put_(cx + dx, cz + dz, 'minecraft:polished_blackstone_wall' if i > 0 else 'minecraft:polished_blackstone')
        elif i == band:
            put_(cx, cz, 'minecraft:chiseled_polished_blackstone')
            for (dx, dz, _) in sides:
                put_(cx + dx, cz + dz, 'minecraft:chiseled_polished_blackstone')
        elif i < h - 2:
            put_(cx, cz, mat)
            for (dx, dz, _) in sides:
                put_(cx + dx, cz + dz, mat)
        elif i == h - 2:
            put_(cx, cz, mat)
            for (dx, dz, f) in sides:
                put_(cx + dx, cz + dz, stair('or:tomb_brick_stairs', f))
        else:
            put_(cx, cz, 'minecraft:gilded_blackstone')
            t.set(cx, yy + 1, cz, tip)
            cells.append((cx, yy + 1, cz))
    return cells


def fallen_obelisk3(t, x, y, z, length, direction, rng, mat=None, breaks=2, stump=2):
    """A three-wide obelisk fallen from its base at (x, z) (the middle of the shaft; feet layer y) toward
    `direction`: a stump `stump` high on the base, then the shaft lying on the ground, three wide and two
    high with its upper corners chamfered, broken in places, its pyramidion at the far end. Returns the
    (x, z) cells the lying shaft covers."""
    mat = mat or Mix([('or:tomb_bricks', 5), ('minecraft:polished_blackstone_bricks', 3),
                      ('minecraft:cracked_polished_blackstone_bricks', 2)], rng.randrange(1 << 20), 3.0)
    dx, dz = STEP[direction]
    rx, rz = STEP[RIGHT[direction]]
    obelisk3(t, x, y, z, 0, rng, mat=mat)                       # the base
    for i in range(stump):
        for (a, b) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if i == stump - 1 and (a, b) != (0, 0) and rng.random() < 0.5:
                continue
            t.set(x + a, y + 1 + i, z + b, mat(x + a, y + 1 + i, z + b))
    covered = []
    gaps = set()
    if breaks:
        for _ in range(breaks):
            gaps.add(rng.randrange(4, max(5, length - 2)))
    for i in range(3, length + 3):
        px, pz = x + dx * i, z + dz * i
        if not t.inside(px, y, pz):
            break
        if i in gaps:
            # a break: rubble and a slab in the gap
            t.set(px, y, pz, pick(rng, [('or:debris', 2), (slab('minecraft:polished_blackstone_brick_slab'), 1)]))
            covered.append((px, pz))
            continue
        tipped = i >= length + 1
        for r in (-1, 0, 1):
            qx, qz = px + rx * r, pz + rz * r
            if tipped:
                if r == 0:
                    t.set(qx, y, qz, stair('or:tomb_brick_stairs', OPP[direction]) if i == length + 2 else mat(qx, y, qz))
            else:
                t.set(qx, y, qz, mat(qx, y, qz))
                if r == 0:
                    t.set(qx, y + 1, qz, mat(qx, y + 1, qz))
                else:
                    t.set(qx, y + 1, qz, stair('or:tomb_brick_stairs', OPP[RIGHT[direction]] if r > 0 else RIGHT[direction]))
            covered.append((qx, qz))
    return covered


def statue(t, x, y, z, facing, rng, height=14, mat=None, trim=None, sword=True, face='minecraft:blackstone',
           gild='minecraft:gilded_blackstone'):
    """A colossal robed and hooded Sith figure facing `facing`, five cells wide and three deep, `height`
    cells from the hem to the peak of the hood, standing on (x, y - 1, z): (x, z) is the middle of its
    front row. The robe flares at the hem and falls in folds, a belt is picked out in gold, the arms come
    down to hands clasped on the pommel of a sword held point-down in front, and the face is a shadow
    under the hood. Returns the cells it set."""
    mat = mat or Mix([('or:tomb_stone', 5), ('minecraft:polished_blackstone', 3), ('or:tomb_bricks', 1)],
                     rng.randrange(1 << 20), 2.5)
    fold = 'minecraft:polished_blackstone'
    trim = trim or 'or:tomb_stone_carved'
    st = 'or:tomb_brick_stairs'
    fx, fz = STEP[facing]
    rx, rz = STEP[RIGHT[facing]]
    cells = []

    def at(r, f, yy, state):
        px, pz = x + rx * r + fx * f, z + rz * r + fz * f
        put(t, px, y + yy, pz, state)
        cells.append((px, y + yy, pz))

    belt = max(3, int(round(height * 0.48)))
    shoulder = max(belt + 2, int(round(height * 0.76)))
    head = shoulder + 1
    peak = height
    # the robe: 5 wide, 3 deep, folds in the front
    for yy in range(0, belt):
        for r in range(-2, 3):
            for f in (0, -1, -2):
                if yy > 0 and abs(r) == 2 and f == 0 and yy >= belt - 2:
                    continue  # the robe narrows a little under the arms
                at(r, f, yy, fold if (f == 0 and abs(r) == 1 and yy > 0) else mat)
    # the hem flares out in front and at the sides
    for r in range(-2, 3):
        at(r, 1, 0, stair(st, OPP[facing]))
    for f in (0, -1, -2):
        at(-3, f, 0, stair(st, RIGHT[facing]))
        at(3, f, 0, stair(st, LEFT[facing]))
    # the belt in gold and carved stone
    for r in range(-2, 3):
        for f in (0, -1, -2):
            at(r, f, belt, gild if (f == 0 and r == 0) else trim if f == 0 else mat)
    # the torso and the sleeves
    for yy in range(belt + 1, shoulder):
        for r in range(-1, 2):
            for f in (0, -1, -2):
                at(r, f, yy, mat)
        for r in (-2, 2):
            for f in (0, -1):
                at(r, f, yy, fold if f == 0 else mat)
    # the shoulders slope down to the sides
    for r in range(-1, 2):
        for f in (0, -1, -2):
            at(r, f, shoulder, mat)
    for f in (0, -1, -2):
        at(-2, f, shoulder, stair(st, RIGHT[facing]))
        at(2, f, shoulder, stair(st, LEFT[facing]))
    # the forearms come forward to the hands on the pommel, under the belt
    if sword:
        for r in (-1, 1):
            at(r, 1, belt, stair(st, RIGHT[facing] if r < 0 else LEFT[facing], 'top'))
        at(0, 1, belt, gild)                            # the pommel between the hands
        at(0, 1, belt - 1, 'minecraft:chiseled_polished_blackstone')
        at(-1, 1, belt - 1, stair(st, RIGHT[facing], 'top'))  # the crossguard
        at(1, 1, belt - 1, stair(st, LEFT[facing], 'top'))
        for yy in range(1, belt - 1):
            at(0, 1, yy, 'minecraft:polished_blackstone_wall')  # the blade, point down
    # the hood: 3 wide, 3 deep, the face a shadow under a brow, a peak at the back
    for yy in range(head, peak):
        for r in (-1, 0, 1):
            for f in (0, -1, -2):
                if r == 0 and f == 0:
                    if yy == peak - 1:
                        at(r, f, yy, stair(st, facing, 'top'))  # the brow over the face
                    else:
                        put(t, x + fx * 0, y + yy, z + fz * 0, AIR)  # the face is set back
                    continue
                if r == 0 and f == -1 and yy < peak - 1:
                    at(r, f, yy, face)
                    continue
                at(r, f, yy, mat)
    for f in (-1, -2):
        at(0, f, peak, stair(st, facing) if f == -1 else mat)
    at(0, -2, peak + 1, stair(st, facing))
    return cells


# ---------------------------------------------------------------- Imperial prefabs

def prefab(t, x0, y0, z0, w, d, h, door_side, rng, kind='bunks', door_at=None, windows=True, roof_unit=True,
           lights=True, damage=0.0, palette=None):
    """An Imperial prefab hut: a deck floor at y0 (flush with the ground layer, so the feet layer is y0+1),
    plated walls with beam posts and a black window band, a lipped roof, double iron doors on door_side
    with red lights over them and heavy pressure plates on both sides (the doors slide open for anyone
    walking up). h is the clear height inside. Inside (x0+1..x0+w-2, y0+1..y0+h, z0+1..z0+d-2) it is
    cleared to air and furnished by `kind` ('bunks', 'lab', 'store', 'office', 'mess', 'none').
    Returns the interior box."""
    pal = palette or IMP
    x1, z1 = x0 + w - 1, z0 + d - 1
    top = y0 + h + 1
    wall = Mix(pal.options('wall'), rng.randrange(1 << 20), 3.0)
    # a footing ring under the walls and the deck
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                t.set(x, y0 - 1, z, 'minecraft:polished_deepslate') if y0 > 0 else None
    t.fill(x0, y0, z0, x1, y0, z1, Mix(pal.options('floor'), rng.randrange(1 << 20), 3.0))
    # walls
    for yy in range(y0 + 1, top):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                put(t, x, yy, z, wall)
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                put(t, x, yy, z, wall)
    # beam posts at the corners and every 4 cells
    posts = set()
    for x in list(range(x0, x1 + 1, 4)) + [x1]:
        posts |= {(x, z0), (x, z1)}
    for z in list(range(z0, z1 + 1, 4)) + [z1]:
        posts |= {(x0, z), (x1, z)}
    for (px, pz) in posts:
        for yy in range(y0 + 1, top):
            t.set(px, yy, pz, 'or:durasteel_beam[axis=y]')
    # interior air
    t.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, top - 1, z1 - 1, AIR)
    # roof with a lip of upside-down stairs
    t.fill(x0, top, z0, x1, top, z1, Mix([('or:imperial_plating', 4), ('or:durasteel_plating', 1)], rng.randrange(1 << 20), 3.0))
    for x in range(x0, x1 + 1):
        t.set(x, top, z0 - 1, stair(pal['stairs'], 'south', 'top'))
        t.set(x, top, z1 + 1, stair(pal['stairs'], 'north', 'top'))
    for z in range(z0, z1 + 1):
        t.set(x0 - 1, top, z, stair(pal['stairs'], 'east', 'top'))
        t.set(x1 + 1, top, z, stair(pal['stairs'], 'west', 'top'))
    for (px, pz) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
        t.set(px, top, pz, slab(pal['slab'], 'top'))
    # window band (black glass panes, recessed look) on the long walls
    if windows:
        wy = y0 + 2
        for x in range(x0 + 1, x1):
            for z in (z0, z1):
                if (x, z) not in posts and (x - x0) % 4 in (1, 2):
                    t.set(x, wy, z, 'minecraft:black_stained_glass_pane')
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                if (x, z) not in posts and (z - z0) % 4 in (1, 2):
                    t.set(x, wy, z, 'minecraft:black_stained_glass_pane')
    # the doors
    if door_at is None:
        if door_side in ('north', 'south'):
            door_at = x0 + w // 2 - 1
        else:
            door_at = z0 + d // 2 - 1
    if door_side == 'north':
        cells = [(door_at, z0), (door_at + 1, z0)]
    elif door_side == 'south':
        cells = [(door_at, z1), (door_at + 1, z1)]
    elif door_side == 'west':
        cells = [(x0, door_at), (x0, door_at + 1)]
    else:
        cells = [(x1, door_at), (x1, door_at + 1)]
    (ax_, az_), (bx_, bz_) = cells
    for (cx, cz) in cells:
        for yy in range(y0 + 1, y0 + 4):
            t.set(cx, yy, cz, AIR)
    facing_in = OPP[door_side]
    # doors open toward the inside; facing is the way a player walking in faces
    door(t, ax_, y0 + 1, az_, facing_in, hinge='left' if door_side in ('north', 'east') else 'right')
    door(t, bx_, y0 + 1, bz_, facing_in, hinge='right' if door_side in ('north', 'east') else 'left')
    ox, oz = STEP[door_side]
    ix, iz = STEP[facing_in]
    for (cx, cz) in cells:
        for yy in range(y0 + 3, top):
            t.set(cx, yy, cz, 'or:durasteel_plating')
        # a threshold outside and pressure plates on both sides: the doors slide open for anyone walking up
        t.set(cx + ox, y0, cz + oz, 'or:durasteel_deck')
        t.set(cx + ox, y0 + 1, cz + oz, 'minecraft:heavy_weighted_pressure_plate[power=0]')
        t.set(cx + ix, y0 + 1, cz + iz, 'minecraft:heavy_weighted_pressure_plate[power=0]')
    if lights:
        for (cx, cz) in cells:
            t.set(cx, y0 + 4, cz, pal['red'])
    # a roof unit and an aerial
    if roof_unit and w >= 5 and d >= 5:
        ux, uz = x0 + 1 + rng.randrange(max(1, w - 4)), z0 + 1 + rng.randrange(max(1, d - 4))
        t.set(ux, top + 1, uz, 'or:durasteel_vent')
        t.set(ux + 1, top + 1, uz, 'or:durasteel_vent')
        t.set(ux, top + 1, uz + 1, S('or:durasteel_pipes', axis='x'))
        t.set(ux + 1, top + 1, uz + 1, S('or:durasteel_pipes', axis='x'))
        t.set(x1 - 1, top + 1, z1 - 1, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
        t.set(x1 - 1, top + 2, z1 - 1, 'minecraft:end_rod[facing=up]')
    box = (x0 + 1, y0 + 1, z0 + 1, x1 - 1, top - 1, z1 - 1)
    furnish(t, box, kind, door_side, cells, rng)
    if lights:
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        t.set(cx, top, cz, pal['light'])
        if w >= 9 or d >= 9:
            if w >= d:
                t.set(x0 + 2, top, cz, pal['light'])
                t.set(x1 - 2, top, cz, pal['light'])
            else:
                t.set(cx, top, z0 + 2, pal['light'])
                t.set(cx, top, z1 - 2, pal['light'])
    return box


def furnish(t, box, kind, door_side, door_cells, rng):
    """Furniture along the walls of a prefab's interior, keeping a clear lane from the door."""
    x0, y0, z0, x1, y1, z1 = box
    keep = set()
    for (cx, cz) in door_cells:
        ix, iz = STEP[OPP[door_side]]
        for k in range(1, 3):
            keep.add((cx + ix * k, cz + iz * k))
    walls = []
    for x in range(x0, x1 + 1):
        walls.append((x, z0, 'south'))
        walls.append((x, z1, 'north'))
    for z in range(z0 + 1, z1):
        walls.append((x0, z, 'east'))
        walls.append((x1, z, 'west'))
    walls = [w for w in walls if (w[0], w[1]) not in keep]
    y = y0
    if kind == 'bunks':
        # beds along the long walls, a footlocker crate at each foot, a locker row
        long_x = (x1 - x0) >= (z1 - z0)
        if long_x:
            for x in range(x0, x1 + 1, 2):
                for (z, f) in ((z0, 'north'), (z1, 'south')):
                    if (x, z) in keep or (x, z + (1 if f == 'north' else -1)) in keep:
                        continue
                    if t.is_air(x, y, z) and t.is_air(x, y, z + (1 if f == 'north' else -1)):
                        bed(t, x, y, z + (1 if f == 'north' else -1), f, rng.choice(('black', 'gray', 'red')))
        else:
            for z in range(z0, z1 + 1, 2):
                for (x, f) in ((x0, 'west'), (x1, 'east')):
                    if (x, z) in keep or (x + (1 if f == 'west' else -1), z) in keep:
                        continue
                    if t.is_air(x, y, z) and t.is_air(x + (1 if f == 'west' else -1), y, z):
                        bed(t, x + (1 if f == 'west' else -1), y, z, f, rng.choice(('black', 'gray', 'red')))
        for (x, z, f) in walls:
            if t.is_air(x, y, z) and t.is_air(x, y + 1, z) and rng.random() < 0.25:
                t.set(x, y, z, pick(rng, [('or:cargo_crate_imperial', 2), (barrel('up'), 1)]))
    elif kind == 'lab':
        placed = 0
        for (x, z, f) in walls:
            if not t.is_air(x, y, z):
                continue
            r = rng.random()
            if r < 0.3:
                t.set(x, y, z, S('or:console', facing=f))
                placed += 1
            elif r < 0.45:
                t.set(x, y + 1, z, S('or:holo_screen', facing=f))
                t.set(x, y, z, slab('or:durasteel_plating_slab', 'top'))
            elif r < 0.6:
                t.set(x, y, z, slab('or:durasteel_plating_slab', 'top'))
                t.set(x, y + 1, z, pick(rng, ['minecraft:decorated_pot', skull(rng.randrange(16)), 'or:funerary_urn',
                                               candle('black', 1, False)]))
            elif r < 0.7:
                mod.lectern(t, x, y, z, f, book=True)
    elif kind == 'store':
        for (x, z, f) in walls:
            if not t.is_air(x, y, z):
                continue
            r = rng.random()
            if r < 0.55:
                t.set(x, y, z, pick(rng, IMP.options('crate')))
                if rng.random() < 0.5 and t.is_air(x, y + 1, z):
                    t.set(x, y + 1, z, pick(rng, IMP.options('crate')))
            elif r < 0.75:
                t.set(x, y, z, barrel('up'))
    elif kind == 'office':
        for (x, z, f) in walls:
            if not t.is_air(x, y, z):
                continue
            r = rng.random()
            if r < 0.2:
                t.set(x, y + 1, z, S('or:holo_screen', facing=f))
            elif r < 0.35:
                t.set(x, y, z, barrel('up'))
    elif kind == 'mess':
        cx = (x0 + x1) // 2
        for z in range(z0 + 1, z1):
            if (cx, z) not in keep:
                t.set(cx, y, z, slab('or:durasteel_plating_slab', 'top'))
                for sx in (cx - 1, cx + 1):
                    if t.is_air(sx, y, z) and (sx, z) not in keep:
                        t.set(sx, y, z, stair('or:imperial_plating_stairs', 'east' if sx < cx else 'west'))


# ---------------------------------------------------------------- loot and worldgen

def item(name, lo, hi=None, chance=None):
    """One loot pool of a single item: lo..hi of it, with an optional chance."""
    hi = lo if hi is None else hi
    e = {'type': 'minecraft:item', 'name': name if ':' in name else 'minecraft:' + name}
    if not (lo == 1 and hi == 1):
        e['functions'] = [{'function': 'minecraft:set_count',
                           'count': {'type': 'minecraft:uniform', 'min': float(lo), 'max': float(hi)}}]
    p = {'rolls': 1.0, 'entries': [e]}
    if chance is not None:
        p['conditions'] = [{'condition': 'minecraft:random_chance', 'chance': chance}]
    return p


def loot_table(path, pools):
    """Writes data/oldrepublic/loot_table/<path>.json (a chest table) in the repository's style."""
    obj = {'type': 'minecraft:chest', 'pools': pools, 'random_sequence': 'oldrepublic:' + path}
    full = os.path.join(LOOT, path + '.json')
    os.makedirs(os.path.dirname(full), exist_ok=True)
    text = json.dumps(obj, indent=2) + '\n'
    if not (os.path.exists(full) and open(full).read() == text):
        with open(full, 'w') as f:
            f.write(text)
    return full


def worldgen(name, variants, biomes, spacing, separation, salt, buried, support=10, ramp=2, inset=0,
             max_distance=80, processors=None, no_monsters=True, step='surface_structures'):
    """The worldgen files of oldrepublic:varkhast/<name>: a start pool of the variants (each a (template,
    weight, height) triple), the jigsaw structure (start_height 1 - buried), the clear_of_origin set and
    the biome tag."""
    sid = 'varkhast/%s' % name
    els = []
    for (tpl, weight, height) in variants:
        els.append(W.element('varkhast/%s/%s' % (name, tpl), processors=processors or 'minecraft:empty',
                             adapted=dict(yard_stand=buried, clear_from=height, clear_inset=inset,
                                          support_depth=support, ramp_rings=ramp), weight=weight))
    out = [W.pool('%s/start' % sid, els)]
    out.append(W.structure(sid, start_pool='%s/start' % sid, biome_tag='has_structure/%s' % sid, size=1,
                           max_distance=max_distance, step=step, start_height=1 - buried, no_monsters=no_monsters))
    out.append(W.structure_set(sid, [(sid, 1)], spacing=spacing, separation=separation, salt=salt,
                               placement='clear_of_origin', min_distance=256))
    out.append(W.biome_tag('has_structure/%s' % sid, ['oldrepublic:varkhast_%s' % b for b in biomes]))
    return out


# ---------------------------------------------------------------- checks (my own, beside check_m6_structures)

def floating(t, ground_y=0, ignore=()):
    """Solid cells not joined (through solid cells, 6 ways) to a cell at y <= ground_y: things that would
    float. Returns a list of components (lists of cells), largest first."""
    solid = {p for p, s in t.cells.items() if not is_air_state(s) and B.name_of(s) not in ignore
             and B.name_of(s) != 'minecraft:light'}
    seen = set()
    q = deque(p for p in solid if p[1] <= ground_y)
    seen |= set(q)
    while q:
        x, y, z = q.popleft()
        for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if n in solid and n not in seen:
                seen.add(n)
                q.append(n)
    rest = solid - seen
    comps = []
    while rest:
        p = rest.pop()
        comp = [p]
        q = deque([p])
        while q:
            x, y, z = q.popleft()
            for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
                if n in rest:
                    rest.discard(n)
                    comp.append(n)
                    q.append(n)
        comps.append(comp)
    comps.sort(key=len, reverse=True)
    return comps


def unsupported_gravity(t):
    """Gravity blocks (sand, gravel, concrete powder, anvils) with nothing solid set under them."""
    out = []
    for (x, y, z), s in t.cells.items():
        if B.name_of(s) in GRAVITY and y > 0:
            below = t.get(x, y - 1, z)
            if below is None or is_air_state(below) or mod.walk_through(below) and not B.name_of(below).endswith('_slab'):
                out.append((x, y, z))
    return out


def _clear(s, unset_is_air):
    """A person's body can pass this cell (feet or head)."""
    if s is None:
        return unset_is_air
    n = B.name_of(s)
    path = n.split(':', 1)[1]
    if is_air_state(s) or path in ('light', 'site_spawner', 'structure_void'):
        return True
    if path.endswith('_door') or path == 'ladder' or path.endswith('_pressure_plate') or path.endswith('_button'):
        return True
    if path in ('lava', 'water') or path.endswith('_cauldron') and 'lava' in path:
        return False
    if path.endswith('_trapdoor'):
        p = B.parse(s)[1]
        return p.get('open') == 'true' or p.get('half') == 'top'
    return mod.walk_through(s) and not path.endswith('_stairs') and not path.endswith('_slab')


def _surfaces(t, x, z, unset_is_air):
    """Standing surfaces in a column: [(height, kind)], kind 'floor', 'stair' (climb from its low sides,
    rise 1) or 'ladder'. A surface needs room for a person (1.8 cells) above it."""
    out = []
    for y in range(t.sy):
        s = t.get(x, y, z)
        if s is None:
            continue
        n, p = B.parse(s)
        path = n.split(':', 1)[1]
        h = None
        kind = 'floor'
        if path == 'ladder':
            h, kind = float(y), 'ladder'
        elif is_air_state(s) or _clear(s, False):
            continue
        elif path.endswith('_slab'):
            h = y + (0.5 if p.get('type') == 'bottom' else 1.0)
        elif path.endswith('_stairs'):
            h = y + 1.0
            kind = 'stair' if p.get('half', 'bottom') == 'bottom' else 'floor'
        elif path.endswith('_trapdoor'):
            h = y + (0.1875 if p.get('half') == 'bottom' else 1.0)
        elif path.endswith('_fence') or (path.endswith('_wall') and 'banner' not in path) or path in ('lava', 'water') \
                or 'lava' in path or path.endswith('campfire') or path.endswith('_bed'):
            continue
        elif mod.walk_through(s):
            continue
        else:
            h = y + 1.0
        if h is None:
            continue
        # room above: every cell the body overlaps from h to h + 1.8
        ok = True
        lo = int(math.floor(h + 0.001)) if kind != 'ladder' else y
        hi = int(math.floor(h + 1.79))
        for yy in range(lo, hi + 1):
            if yy == y and kind != 'ladder':
                if h - y < 1.0:
                    continue  # standing inside the cell of a bottom slab or trapdoor
                continue
            if not t.inside(x, yy, z):
                break
            if not _clear(t.get(x, yy, z), unset_is_air) and not (kind == 'ladder' and yy == y):
                ok = False
                break
        if ok:
            out.append((h, kind, (s if kind == 'stair' else None)))
    return out


def walkable_from(t, start, max_drop=3.0, unset_is_air=False):
    """Every standing surface a person can walk to from the feet cell `start` without jumping (rises of at
    most half a cell, or a stair from its low sides, or a ladder) and without breaking blocks. Returns a
    dict {(x, z, height): True}; use reached(result, cell) to test a feet cell."""
    cols = {}

    def surf(x, z):
        if (x, z) not in cols:
            cols[(x, z)] = _surfaces(t, x, z, unset_is_air) if 0 <= x < t.sx and 0 <= z < t.sz else []
        return cols[(x, z)]

    sx, sy, sz = start
    begin = None
    for (h, kind, st) in surf(sx, sz):
        if sy - 1 < h <= sy + 0.5:
            begin = (sx, sz, h, kind)
    if begin is None:
        return {}
    seen = {(sx, sz, begin[2]): True}
    q = deque([begin])
    while q:
        x, z, h, kind = q.popleft()
        if kind != 'ladder' and any(k == 'ladder' and abs(hh - h) < 0.01 for (hh, k, _) in surf(x, z)):
            kind = 'ladder'
        # up and down a ladder
        if kind == 'ladder':
            for (h2, k2, st2) in surf(x, z):
                if (x, z, h2) not in seen and abs(h2 - h) <= 1.01:
                    seen[(x, z, h2)] = True
                    q.append((x, z, h2, k2))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for (h2, k2, st2) in surf(nx, nz):
                if (nx, nz, h2) in seen:
                    continue
                rise = h2 - h
                ok = False
                if rise <= 0.51 and -rise <= max_drop:
                    ok = True
                elif k2 == 'stair' and rise <= 1.01:
                    fac = B.parse(st2)[1].get('facing')
                    # a stair's high back faces `facing`; it is climbed from any other side
                    ok = STEP.get(fac) != (-dx, -dz)
                elif kind == 'ladder' and rise <= 1.51:
                    ok = True
                elif k2 == 'ladder' and abs(rise) <= 1.51:
                    ok = True
                if ok:
                    # the body passes between: the cells of the higher of the two heights must be clear in
                    # the cell moved into (checked by its own room) and in the cell left (its room)
                    seen[(nx, nz, h2)] = True
                    q.append((nx, nz, h2, k2))
    return seen


def reached(result, cell):
    """True when the feet cell (x, y, z) stands on a surface in the walk result."""
    x, y, z = cell
    for (cx, cz, h) in result:
        if cx == x and cz == z and y - 1 < h <= y + 0.5:
            return True
    return False


def next_to(t, result, cell):
    """True when a person can reach a cell (a chest, a lever, a spawner): stand beside or on top of it."""
    x, y, z = cell
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for yy in (y, y - 1, y + 1):
            if reached(result, (x + dx, yy, z + dz)):
                return True
    return reached(result, (x, y + 1, z))


def report(t, name, ground_y=0, walk=None, gravity=True):
    """Prints my checks for one template: floating parts, unsupported gravity blocks, walk routes.
    walk: (start, [goal cells]) to test that every goal is reachable on foot."""
    out = []
    comps = floating(t, ground_y)
    if comps:
        out.append('%d floating component(s): %s' % (len(comps), ', '.join(
            '%d cells at %s (%s)' % (len(c), min(c), B.name_of(t.get(*min(c)))) for c in comps[:8])))
    if gravity:
        g = unsupported_gravity(t)
        if g:
            out.append('%d unsupported gravity block(s): %s' % (len(g), sorted(g)[:8]))
    if walk:
        start, goals = walk[0], walk[1]
        unset = walk[2] if len(walk) > 2 else False
        reach = walkable_from(t, start, unset_is_air=unset)
        miss = [g for g in goals if not (reached(reach, g) or next_to(t, reach, g))]
        out.append('walk from %s: %d surfaces reachable, %d/%d goals reached%s' % (
            start, len(reach), len(goals) - len(miss), len(goals), (' MISSING %s' % miss) if miss else ''))
    print('[%s] %s' % (name, '; '.join(out) if out else 'ok'))
    return out


def preview(t, path, title, views=('se', 'nw'), plans='auto', cut=None, width=1600):
    """Renders the template (as it would be saved) to a PNG sheet."""
    from structlib import preview as P
    t.finish()
    img = P.sheet(t.to_nbt(), title=title, views=views, plans=plans, cut=cut, width=width)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    img.convert('RGB').save(path)
    return path


def crop(t, x0, y0, z0, x1, y1, z1):
    """A copy of part of a template (for close-up previews)."""
    c = Template(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1, seed=0)
    for (x, y, z), s in t.cells.items():
        if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1:
            c.cells[(x - x0, y - y0, z - z0)] = s
            if (x, y, z) in t.nbt:
                c.nbt[(x - x0, y - y0, z - z0)] = t.nbt[(x, y, z)]
    return c


def args(argv=None):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', default=None, help='a folder to render preview sheets into')
    ap.add_argument('--only', default=None, help='build only the variant with this name')
    return ap.parse_args(argv)
