"""QM's kit for the mission sites (docs/MILESTONE_6.md 5.4): materials, ground, props and the save step.

Every mission template is placed by Java at runtime, far out from the mission's giver (FarSites), in
whatever overworld biome the target lands in, so the sites are built from things that read right
anywhere: scrap, durasteel, canvas, cargo crates, sandbags and stone. Ground is left to the world
except where people have worn it (coarse dirt, gravel, packed mud), and nothing here is grass.

Conventions the mission code relies on (kept from tools/assets/gen_structures_story_m3.py):
  * The entrance is the local north side (z = 0); the placer turns it toward the player.
  * Feet anchors (CREW_n, SQUAD_n, CHIEF, FENCE, WOUNDED, DOWNED_n, SENTINEL_n, HEART) are two cells
    of air over a solid block (mod.spot); nothing spawns from the template itself.
  * Block anchors hold their real block: CRATE a barrel (empty: the mission hands out its contents),
    BOOK a lectern holding a book, FIRE a lit campfire.
  * No other barrel, chest or lectern stands in a mission template, so decor is never mistaken for
    the objective (the cargo crates of the build kit are plain blocks).

Shared by the per-site scripts in this folder; build_all.py runs them all.
"""
import argparse
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..'))

from structlib import Template, S, AIR, mod, meta  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402
from structlib.nature import Noise  # noqa: E402

# ---------------------------------------------------------------- materials

CANVAS = 'oldrepublic:canvas'
CANVAS_NAVY = 'oldrepublic:canvas_navy'
CANVAS_CRIMSON = 'oldrepublic:canvas_crimson'
PLATING = 'oldrepublic:durasteel_plating'
DECK = 'oldrepublic:durasteel_deck'
GRATE = 'oldrepublic:durasteel_grate'
VENT = 'oldrepublic:durasteel_vent'
RUST = 'oldrepublic:rusted_plating'
WRECK = 'oldrepublic:wreck_plating'
SCORCHED = 'oldrepublic:wreck_plating_scorched'
CONDUIT = 'oldrepublic:wreck_conduit'
SALVAGE = 'oldrepublic:wreck_salvage'
REP_PLATING = 'oldrepublic:republic_plating'
IMP_PLATING = 'oldrepublic:imperial_plating'
REP_PANEL = 'oldrepublic:republic_panel'
IMP_PANEL = 'oldrepublic:imperial_panel'
CRATE = 'oldrepublic:cargo_crate'
CRATE_REP = 'oldrepublic:cargo_crate_republic'
CRATE_IMP = 'oldrepublic:cargo_crate_imperial'
LIGHT = 'oldrepublic:light_panel'
LIGHT_AMBER = 'oldrepublic:light_panel_amber'
LIGHT_RED = 'oldrepublic:light_panel_red'
SCORCH = 'oldrepublic:scorch_mark'
DEBRIS = 'oldrepublic:debris'
RUIN = 'oldrepublic:ruin_stone'
RUIN_CRACKED = 'oldrepublic:ruin_stone_cracked'
RUIN_MOSSY = 'oldrepublic:ruin_stone_mossy'
RUIN_CARVED = 'oldrepublic:ruin_stone_carved'
RUIN_BRICKS = 'oldrepublic:ruin_bricks'
PALE_TILES = 'oldrepublic:pale_tiles'

COARSE = 'minecraft:coarse_dirt'
GRAVEL = 'minecraft:gravel'
PACKED_MUD = 'minecraft:packed_mud'
ROOTED = 'minecraft:rooted_dirt'
DIRT = 'minecraft:dirt'
PATH = 'minecraft:dirt_path'
STONE = 'minecraft:stone'
ANDESITE = 'minecraft:andesite'
COBBLE = 'minecraft:cobblestone'
TUFF = 'minecraft:tuff'
HAY = 'minecraft:hay_block[axis=y]'
LADDER = 'minecraft:ladder'


def beam(axis='y'):
    return S('or:durasteel_beam', axis=axis)


def pipes(axis='y'):
    return S('or:durasteel_pipes', axis=axis)


def ruin_pillar(axis='y'):
    return S('or:ruin_pillar', axis=axis)


def stairs(name, facing, half='bottom'):
    return S(name, facing=facing, half=half, shape='straight', waterlogged=False)


def canvas_stairs(facing, half='bottom'):
    return S('or:canvas_stairs', facing=facing, half=half, shape='straight')


def plating_stairs(facing, half='bottom'):
    return S('or:durasteel_plating_stairs', facing=facing, half=half, shape='straight')


def slab(name, half='bottom'):
    if name.startswith('oldrepublic:') or name.startswith('or:'):
        return S(name, type=half)
    return S(name, type=half, waterlogged=False)


CANVAS_SLAB = slab('or:canvas_slab')
CANVAS_SLAB_TOP = slab('or:canvas_slab', 'top')
PLATING_SLAB = slab('or:durasteel_plating_slab')
PLATING_SLAB_TOP = slab('or:durasteel_plating_slab', 'top')
DECK_SLAB = slab('or:durasteel_deck_slab')
DECK_SLAB_TOP = slab('or:durasteel_deck_slab', 'top')


def lantern(hanging=False, kind='lantern'):
    return S(kind, hanging=hanging, waterlogged=False)


def chain(axis='y'):
    return S('iron_chain', axis=axis, waterlogged=False)


def bars():
    return S('iron_bars', east=False, north=False, south=False, west=False, waterlogged=False)


def fence(kind='spruce_fence'):
    return S(kind, east=False, north=False, south=False, west=False, waterlogged=False)


def campfire(lit=True, signal=False, facing='north', kind='campfire'):
    return S(kind, facing=facing, lit=lit, signal_fire=signal, waterlogged=False)


def trapdoor(kind, facing, half='top', open_=False):
    return S(kind, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def rod(facing='up'):
    return S('lightning_rod', facing=facing, powered=False, waterlogged=False)


def console(facing):
    return S('or:console', facing=facing)


def holo(facing):
    return S('or:holo_screen', facing=facing)


def carpet(color):
    return 'minecraft:%s_carpet' % color


def wool(color):
    return 'minecraft:%s_wool' % color


def log(kind='stripped_spruce_log', axis='y'):
    return S(kind, axis=axis)


def ladder(facing):
    return S('ladder', facing=facing, waterlogged=False)


def skull(rotation=0):
    return S('skeleton_skull', rotation=rotation, powered=False)


def cluster(facing='up', kind='amethyst_cluster'):
    return S(kind, facing=facing, waterlogged=False)


def pot():
    return S('decorated_pot', cracked=False, facing='north', waterlogged=False)


def candle(n=1, lit=False, color=None):
    return S('%scandle' % (color + '_' if color else ''), candles=n, lit=lit, waterlogged=False)


# ---------------------------------------------------------------- weighted picks and noise

class Mix:
    """Weighted block choices picked by coherent noise (patches) or per cell (rng)."""

    def __init__(self, choices, seed, scale=3.0, jitter=0.3):
        self.choices = [(c, w) for c, w in choices]
        self.total = float(sum(w for _, w in choices))
        self.noise = Noise(seed, scale)
        self.fine = Noise(seed + 7919, 1.1)
        self.jitter = jitter

    def at(self, x, y, z):
        v = (1 - self.jitter) * self.noise.at(x, y, z) + self.jitter * self.fine.at(x, y, z)
        r = v * self.total
        for c, w in self.choices:
            r -= w
            if r <= 0:
                return c
        return self.choices[-1][0]

    def __call__(self, x, y, z):
        return self.at(x, y, z)


def pick(rng, choices):
    total = sum(w for _, w in choices)
    r = rng.random() * total
    for c, w in choices:
        r -= w
        if r <= 0:
            return c
    return choices[-1][0]


WORN = [(COARSE, 5), (GRAVEL, 2), (PACKED_MUD, 2), (ROOTED, 1)]
TRACK = [(COARSE, 4), (PATH, 3), (GRAVEL, 2), (PACKED_MUD, 1)]
SCRAP = [(RUST, 5), (PLATING, 3), (WRECK, 2), (SCORCHED, 1), ('minecraft:waxed_weathered_cut_copper', 1)]
HULL = [(WRECK, 5), (PLATING, 3), (SCORCHED, 2), (RUST, 1)]

# ---------------------------------------------------------------- cell predicates

AIRS = ('minecraft:air', 'minecraft:cave_air')


def standable(state):
    """A block a person can stand on (what the checker counts as 'a block below')."""
    return state is not None and B.name_of(state) not in AIRS and not mod.walk_through(state) and \
        B.name_of(state) not in ('minecraft:water', 'minecraft:lava')


def is_open(t, x, y, z):
    """Unset or air."""
    return t.is_air(x, y, z)


def put(t, x, y, z, state, only_open=False):
    if only_open and not t.is_air(x, y, z):
        return False
    return t.set(x, y, z, state)


# ---------------------------------------------------------------- ground

def ground_blob(t, y, cx, cz, rx, rz, pick_fn, seed, fray=0.35, only_open=False, skip=None):
    """Worn ground on layer y inside a noisy ellipse (the edge frays by `fray`)."""
    n = Noise(seed, 3.2)
    cells = []
    for x in range(int(cx - rx - 2), int(cx + rx + 3)):
        for z in range(int(cz - rz - 2), int(cz + rz + 3)):
            if not t.inside(x, y, z) or (skip and skip(x, z)):
                continue
            d = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
            if d <= 1.0 + (n.at(x, 0, z) - 0.5) * 2 * fray:
                if only_open and t.get(x, y, z) is not None:
                    continue
                t.set(x, y, z, pick_fn(x, y, z))
                cells.append((x, z))
    return cells


def trail(t, y, points, width, pick_fn, seed, fray=0.6, skip=None):
    """A worn track along a polyline of (x, z) points, `width` cells wide, its edges frayed."""
    n = Noise(seed, 2.5)
    r = (width - 1) / 2.0
    done = set()
    for (ax, az), (bx, bz) in zip(points, points[1:]):
        steps = max(1, int(math.hypot(bx - ax, bz - az) * 3))
        for i in range(steps + 1):
            f = i / steps
            cx, cz = ax + (bx - ax) * f, az + (bz - az) * f
            for x in range(int(math.floor(cx - r - 2)), int(math.ceil(cx + r + 2)) + 1):
                for z in range(int(math.floor(cz - r - 2)), int(math.ceil(cz + r + 2)) + 1):
                    if (x, z) in done or not t.inside(x, y, z) or (skip and skip(x, z)):
                        continue
                    d = math.hypot(x - cx, z - cz)
                    if d <= r + (n.at(x, 0, z) - 0.5) * 2 * fray:
                        t.set(x, y, z, pick_fn(x, y, z))
                        done.add((x, z))
    return sorted(done)


def support(t, x, y, z, state=COARSE):
    """Makes the cell under (x, y, z) something to stand on."""
    if not standable(t.get(x, y - 1, z)):
        t.set(x, y - 1, z, state)


def spot(t, name, x, y, z, ground=COARSE):
    """A feet anchor (mod.spot) with a block under it."""
    if t.get(x, y, z) in (DEBRIS, SCORCH):  # litter swept aside for the person standing there
        t.set(x, y, z, AIR)
    if not t.is_air(x, y, z) or not t.is_air(x, y + 1, z):
        raise ValueError('%s at %r is not open (%s / %s)' % (name, (x, y, z), t.get(x, y, z), t.get(x, y + 1, z)))
    support(t, x, y, z, ground)
    mod.spot(t, name, x, y, z)


def litter(t, y, region, choices, density, rng, on_set_ground=True):
    """Small things on the feet layer y over set ground: debris, scorch marks, pebbles."""
    x0, z0, x1, z1 = region
    for z in range(min(z0, z1), max(z0, z1) + 1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            if not t.inside(x, y, z):
                continue
            below = t.get(x, y - 1, z)
            if on_set_ground and not standable(below):
                continue
            if t.get(x, y, z) is not None and not t.is_air(x, y, z):
                continue
            if _anchored(t, x, y, z):
                continue
            if rng.random() < density:
                t.set(x, y, z, pick(rng, choices))


def _anchored(t, x, y, z):
    for cells in t.anchors.values():
        if [x, y, z] in cells or [x, y - 1, z] in cells:
            return True
    return False


def scorch_burst(t, y, cx, cz, r, rng, density=0.55, debris=0.25):
    """Blast marks radiating from (cx, cz): scorch decals thick at the middle, debris thrown outward."""
    for z in range(int(cz - r - 1), int(cz + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if not t.inside(x, y, z) or _anchored(t, x, y, z):
                continue
            d = math.hypot(x - cx, z - cz)
            if d > r or not standable(t.get(x, y - 1, z)) or not t.is_air(x, y, z):
                continue
            p = density * (1.0 - d / (r + 0.5))
            if rng.random() < p:
                t.set(x, y, z, SCORCH)
            elif rng.random() < debris * (d / (r + 0.5)):
                t.set(x, y, z, DEBRIS)


def ellipse_cells(cx, cz, rx, rz, n=None, wobble=0.0):
    """Cells inside a (wobbly) ellipse, and the ring of inside cells with an outside 8-neighbour."""
    def inside(x, z):
        e = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
        if n is not None:
            e -= (n.at(x, 0, z) - 0.5) * wobble
        return e <= 1.0
    cells = {(x, z) for x in range(int(cx - rx - 3), int(cx + rx + 4)) for z in range(int(cz - rz - 3), int(cz + rz + 4))
             if inside(x, z)}
    ring = sorted((x, z) for (x, z) in cells
                  if any((x + dx, z + dz) not in cells for dx in (-1, 0, 1) for dz in (-1, 0, 1)))
    return cells, ring


# ---------------------------------------------------------------- props

def column(t, x, y0, y1, z, state):
    for y in range(y0, y1 + 1):
        t.set(x, y, z, state)


def lamp_post(t, x, y, z, height=3, post=None, lamp=None, arm=None):
    """A post with a lamp: on top, or hung from an arm toward `arm` (a direction)."""
    post = post or beam('y')
    column(t, x, y, y + height - 1, z, post)
    top = y + height
    if arm:
        dx, dz = B.step(arm)
        t.set(x + dx, top - 1, z + dz, bars())
        t.set(x + dx, top - 2, z + dz, lamp or lantern(True))
    else:
        t.set(x, top, z, lamp or lantern(False))


def crate_pile(t, cells, y, rng, kinds=((CRATE, 4), (CRATE_REP, 1)), tarp=0.0, max_h=3):
    """Cargo crates stacked on (x, z, height) cells; a canvas tarp (slabs) over some tops."""
    for (x, z, h) in cells:
        h = min(h, max_h)
        for k in range(h):
            t.set(x, y + k, z, pick(rng, kinds))
        if tarp and rng.random() < tarp:
            t.set(x, y + h, z, CANVAS_SLAB)


def sandbags(t, cells, y, rng, high=0.6):
    """Burlap sandbags: a course of canvas and packed mud with a slab course on some cells."""
    for (x, z) in cells:
        t.set(x, y, z, CANVAS if rng.random() < 0.6 else PACKED_MUD)
        if rng.random() < high:
            t.set(x, y + 1, z, CANVAS_SLAB if rng.random() < 0.7 else slab('mud_brick_slab'))


def fire_pit(t, x, y, z, rng, lit=True, signal=False, spit=None, stones=0.75, kind='campfire'):
    """A cook fire: the campfire (lit), a broken ring of stones and ash, a spit across it (axis x or z)."""
    t.set(x, y, z, campfire(lit=lit, signal=signal, kind=kind))
    t.set(x, y - 1, z, HAY if signal else GRAVEL)
    stone = [(slab('cobblestone_slab'), 3), (slab('andesite_slab'), 2), (slab('stone_slab'), 1), (COBBLE, 1)]
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        if spit and ((spit == 'x' and dz == 0 and dx != 0) or (spit == 'z' and dx == 0 and dz != 0)):
            continue
        if t.is_air(x + dx, y, z + dz) and rng.random() < stones:
            t.set(x + dx, y, z + dz, pick(rng, stone))
        t.set(x + dx, y - 1, z + dz, pick(rng, [(GRAVEL, 2), (COARSE, 2), (TUFF, 1)]))
    if spit:
        ax = (1, 0) if spit == 'x' else (0, 1)
        for s in (-1, 1):
            px, pz = x + ax[0] * s, z + ax[1] * s
            t.set(px, y, pz, bars())
            t.set(px, y + 1, pz, bars())
        t.set(x, y + 1, z, chain('x' if spit == 'x' else 'z'))


def bedroll(t, x, y, z, color='brown', along='x', length=2):
    dx, dz = (1, 0) if along == 'x' else (0, 1)
    for i in range(length):
        t.set(x + dx * i, y, z + dz * i, carpet(color))


def a_tent(t, x0, z0, length, width, y, ridge='z', door='min', ridge_block=None, back=None, front=None,
           lamp=True, floor=None, patches=None):
    """An A-frame tent of canvas stairs, `width` (odd: 5 or 7) across and `length` along its ridge.
    The ridge runs along `ridge` ('x' or 'z'); the door is at the 'min' or 'max' end of the ridge,
    'both' or 'none'. A door end keeps its lowest two courses open; a closed end is filled with
    `back`, the top of a door end with `front` (states or callables). patches: {(i, lvl, 'l'|'r'):
    state} swaps slope cells (i along the ridge from the min end, lvl the course from the ground).
    floor: the state (or callable) laid on y - 1 under the tent. Returns at(i, j) -> (x, z)."""
    k = width // 2

    def at(i, j):  # i along the ridge, j across (0 .. width - 1)
        return (x0 + j, z0 + i) if ridge == 'z' else (x0 + i, z0 + j)

    def put_s(x, yy, z, s):
        t.set(x, yy, z, s(x, yy, z) if callable(s) else s)

    up_l = 'east' if ridge == 'z' else 'south'
    up_r = 'west' if ridge == 'z' else 'north'
    rb = ridge_block or CANVAS
    for i in range(length):
        for lvl in range(k):
            lx, lz = at(i, lvl)
            rx, rz = at(i, width - 1 - lvl)
            put_s(lx, y + lvl, lz, (patches or {}).get((i, lvl, 'l'), canvas_stairs(up_l)))
            put_s(rx, y + lvl, rz, (patches or {}).get((i, lvl, 'r'), canvas_stairs(up_r)))
            for j in range(lvl + 1, width - 1 - lvl):
                ax, az = at(i, j)
                t.set(ax, y + lvl, az, AIR)
        cx, cz = at(i, k)
        put_s(cx, y + k, cz, rb)
        if floor is not None:
            for j in range(width):
                fx, fz = at(i, j)
                put_s(fx, y - 1, fz, floor)
    door_ends = {'min': [0], 'max': [length - 1], 'both': [0, length - 1], 'none': []}[door]
    for i in (0, length - 1):
        for lvl in range(k):
            for j in range(lvl + 1, width - 1 - lvl):
                ex, ez = at(i, j)
                if i in door_ends:
                    if lvl >= 2:
                        put_s(ex, y + lvl, ez, front or CANVAS)
                else:
                    put_s(ex, y + lvl, ez, back or CANVAS)
    if lamp and k >= 3:
        lx, lz = at(length // 2, k)
        t.set(lx, y + k - 1, lz, lantern(True))
    return at


def lean_to(t, x0, z0, x1, z1, y, high_side, height, rng=None, cloth=None, posts=None, drop=1):
    """A sloped canvas roof over the rectangle, high along `high_side` (a direction) at y + height,
    stepping down `drop` per cell toward the other side, on posts at the low corners."""
    xa, xb = sorted((x0, x1))
    za, zb = sorted((z0, z1))
    post = posts or beam('y')
    if high_side in ('north', 'south'):
        rows = list(range(za, zb + 1)) if high_side == 'north' else list(range(zb, za - 1, -1))
        face = high_side
        for k, z in enumerate(rows):
            yy = y + height - k * drop
            for x in range(xa, xb + 1):
                t.set(x, yy, z, cloth(x, yy, z) if callable(cloth) else (cloth or canvas_stairs(face)))
        low = rows[-1]
        for x in (xa, xb):
            column(t, x, y, y + height - (len(rows) - 1) * drop - 1, low, post)
    else:
        cols = list(range(xa, xb + 1)) if high_side == 'west' else list(range(xb, xa - 1, -1))
        face = high_side
        for k, x in enumerate(cols):
            yy = y + height - k * drop
            for z in range(za, zb + 1):
                t.set(x, yy, z, cloth(x, yy, z) if callable(cloth) else (cloth or canvas_stairs(face)))
        low = cols[-1]
        for z in (za, zb):
            column(t, low, y, y + height - (len(cols) - 1) * drop - 1, z, post)


def speeder(t, x, y, z, facing, body=PLATING, accent=LIGHT_AMBER, seat='black'):
    """A parked swoop bike, four cells long, nose toward `facing`, hovering half a block up."""
    dx, dz = B.step(facing)
    back = B.opposite(facing)
    cells = [(x - dx * i, z - dz * i) for i in range(4)]  # nose first
    nx, nz = cells[0]
    t.set(nx, y, nz, plating_stairs(back, 'top'))
    t.set(cells[1][0], y, cells[1][1], PLATING_SLAB_TOP)
    t.set(cells[1][0], y + 1, cells[1][1], rod('up'))
    t.set(cells[2][0], y, cells[2][1], PLATING_SLAB_TOP)
    t.set(cells[2][0], y + 1, cells[2][1], carpet(seat))
    t.set(cells[3][0], y, cells[3][1], pipes('x' if dx else 'z'))
    t.set(cells[3][0], y + 1, cells[3][1], plating_stairs(facing, 'bottom'))


def mast(t, x, y, z, height, top=LIGHT_RED, arms=(), rod_top=True):
    """A comm or sensor mast: a beam column, bar arms at the given heights, a light and a rod on top."""
    column(t, x, y, y + height - 1, z, beam('y'))
    for (h, d, n) in arms:
        dx, dz = B.step(d)
        for i in range(1, n + 1):
            t.set(x + dx * i, y + h, z + dz * i, bars())
    t.set(x, y + height, z, top)
    if rod_top:
        t.set(x, y + height + 1, z, rod('up'))


def table(t, x, y, z, top=None, leg=None):
    t.set(x, y, z, top or PLATING_SLAB_TOP)


def banner(t, x, y, z, facing, base, patterns=(), wall=True):
    mod.banner(t, x, y, z, facing, base=base, patterns=patterns, wall=wall)


# ---------------------------------------------------------------- checks, meta and save

CONTAINERS = ('minecraft:barrel', 'minecraft:chest', 'minecraft:trapped_chest', 'minecraft:lectern')
BLOCK_ANCHORS = {'CRATE': 'minecraft:barrel', 'BOOK': 'minecraft:lectern', 'FIRE': 'minecraft:campfire'}


def check(t, template_id, expected):
    """The anchors the mission code reads, each on the right block or on two cells of air over a block,
    and no decoy container anywhere else."""
    problems = []
    names = set(t.anchors)
    for name in expected:
        if name not in names:
            problems.append('missing anchor %s' % name)
    for name, cells in sorted(t.anchors.items()):
        for (x, y, z) in cells:
            if name in BLOCK_ANCHORS:
                if B.name_of(t.get(x, y, z) or '') != BLOCK_ANCHORS[name]:
                    problems.append('%s at %r holds %s' % (name, (x, y, z), t.get(x, y, z)))
                continue
            if not (t.get(x, y, z) and t.is_air(x, y, z) and t.get(x, y + 1, z) is not None and
                    mod.walk_through(t.get(x, y + 1, z))):
                problems.append('%s at %r is not two cells of air' % (name, (x, y, z)))
            if not standable(t.get(x, y - 1, z)):
                problems.append('%s at %r has %s below' % (name, (x, y, z), t.get(x, y - 1, z)))
    marked = {tuple(c) for n in BLOCK_ANCHORS for c in t.anchors.get(n, [])}
    for p, s in sorted(t.cells.items()):
        if B.name_of(s) in CONTAINERS and p not in marked:
            problems.append('decoy container %s at %r' % (B.name_of(s), p))
    for p, s in sorted(t.cells.items()):
        n = B.name_of(s)
        if n.endswith('_sign'):
            problems.append('sign at %r' % (p,))
    if problems:
        raise SystemExit('%s:\n  %s' % (template_id, '\n  '.join(problems)))


def preview_dir():
    return os.environ.get('QM_PREVIEW')


def save(t, template_id, expected, site_meta, preview=None, cut=None):
    """Checks the anchors, saves the template and its sidecar, writes the site meta, renders a preview."""
    check(t, template_id, expected)
    t.save(template_id)
    meta.write(template_id, **site_meta)
    out = preview or preview_dir()
    if out:
        from structlib import preview as P
        png = out if out.endswith('.png') else os.path.join(out, template_id.replace('/', '_') + '.png')
        img = P.sheet(t.to_nbt(), title='%s  buried %d' % (template_id, site_meta.get('buried', 1)),
                      ground=site_meta.get('buried', 1), cut=cut)
        os.makedirs(os.path.dirname(os.path.abspath(png)), exist_ok=True)
        img.convert('RGB').save(png)
    n = t.count()
    print('%s: size %s, buried %d, %d blocks, anchors %s' % (
        template_id, 'x'.join(str(v) for v in t.size), site_meta.get('buried', 1), n,
        ', '.join('%s%s' % (k, v[0]) for k, v in sorted(t.anchors.items()))))
    return t


def args():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', default=None, help='a folder to render preview sheets into')
    ap.add_argument('--only', default=None, help='build only the templates whose id contains this')
    a = ap.parse_args()
    if a.preview:
        os.environ['QM_PREVIEW'] = a.preview
    return a
