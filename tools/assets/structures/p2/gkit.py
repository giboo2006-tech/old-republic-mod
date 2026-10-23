"""Ghessar's building kit (Milestone 6, worker P2): the shared materials and pieces of the Hutt desert
world's structures.

Ghessar is hard, bright and thirsty: sand drifts against every wall, shade is cloth, water is wealth and
the Vessba kajidic owns "the pads, the walls, the water and the debts". Its builders use the kit's adobe
and salt, sandstone, scrap and old plating. This module holds:

  ground      a half-block heightfield for dunes, canyon floors and salt pans (drifts, berms, troughs,
              feathered edges that meet the natural terrain), and scatter
  materials   noise-patch pickers for adobe, kajidic stone, scrap and hull plating
  builders    adobe walls and rooms, awnings, lean-tos, lamps, furniture, condensers, cisterns, wells,
              scrap heaps, bones, a salvage gantry, the kajidic claim pole, a cargo skiff
  hulls       a freighter hull lying along x (superellipse sections, pitch and roll, decks, bulkheads)
  checks      a walk check (half-block steps, no jumps) from a template edge to an anchor

Template-local axes: x east, y up, z south. Every random choice uses t.rng or a Noise seed.
"""
import math
import os
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..'))

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta, worldgen as W  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402
from structlib.nature import Noise  # noqa: E402
from structlib.palettes import Palette  # noqa: E402

LIGHT = 'minecraft:light'

# ---------------------------------------------------------------- small state helpers


def stair(name, facing, half='bottom'):
    return S(name, facing=facing, half=half, shape='straight', waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def log(name, axis='y'):
    return S(name, axis=axis)


def trapdoor(name, facing, half='top', open_=False):
    return S(name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def ladder(facing):
    return S('ladder', facing=facing, waterlogged=False)


def chain(axis='y'):
    return S('iron_chain', axis=axis, waterlogged=False)


def lantern(hanging=False, kind='lantern'):
    return S(kind, hanging=hanging, waterlogged=False)


def campfire(lit=True, facing='north'):
    return S('campfire', facing=facing, lit=lit, signal_fire=False, waterlogged=False)


def barrel(facing='up', open_=False):
    return S('barrel', facing=facing, open=open_)


def rod(facing='up', name='lightning_rod'):
    return S(name, facing=facing, powered=False, waterlogged=False)


def pot(facing='north', cracked=False):
    return S('decorated_pot', facing=facing, cracked=cracked, waterlogged=False)


def candle(n=1, lit=False, color=''):
    return S((color + '_candle') if color else 'candle', candles=n, lit=lit, waterlogged=False)


def bed(t, x, y, z, facing, color='brown'):
    """A bed whose head is at (x, y, z) and whose foot lies behind it (opposite `facing`)."""
    dx, dz = B.step(facing)
    t.set(x, y, z, S('%s_bed' % color, facing=facing, part='head', occupied=False))
    t.set(x - dx, y, z - dz, S('%s_bed' % color, facing=facing, part='foot', occupied=False))


def door(t, x, y, z, facing, hinge='left', name='spruce_door', open_=False):
    t.set(x, y, z, S(name, facing=facing, half='lower', hinge=hinge, open=open_, powered=False))
    t.set(x, y + 1, z, S(name, facing=facing, half='upper', hinge=hinge, open=open_, powered=False))


def mix(seed, options, scale=4.0):
    """A callable f(x, y, z) choosing among weighted states in coherent noise patches."""
    return Palette(r=options).patches('r', seed=seed, scale=scale)


def scatter_pick(rng, options):
    total = sum(w for _, w in options)
    r = rng.random() * total
    for s, w in options:
        r -= w
        if r <= 0:
            return s
    return options[-1][0]


def solid_at(t, x, y, z):
    s = t.get(x, y, z)
    return s is not None and not mod.walk_through(s) and not mod.see_through(s)


def free(t, x, y, z):
    """The cell is unset or air (the template may build there)."""
    s = t.get(x, y, z)
    return s is None or B.name_of(s) in ('minecraft:air', 'minecraft:cave_air')


# ---------------------------------------------------------------- materials

DUNE = dict(
    top=[('sand', 12), ('sand', 1)],
    under=[('sand', 5), ('sandstone', 1)],
    deep=[('sandstone', 6), ('smooth_sandstone', 1), ('sand', 1)],
    slab='sandstone_slab',
    drift='sand',
)
CANYON = dict(
    top=[('red_sand', 9), ('coarse_dirt', 2), ('terracotta', 1)],
    under=[('red_sand', 4), ('red_sandstone', 2)],
    deep=[('red_sandstone', 4), ('terracotta', 2), ('orange_terracotta', 1)],
    slab='smooth_red_sandstone_slab',
    drift='red_sand',
)
SALT = dict(
    top=[('or:salt_crust', 10), ('calcite', 2), ('sand', 1)],
    under=[('sandstone', 4), ('sand', 1)],
    deep=[('sandstone', 6), ('smooth_sandstone', 1)],
    slab='smooth_quartz_slab',
    drift='white_concrete_powder',
)
GROUNDS = {'dunes': DUNE, 'canyon': CANYON, 'salt': SALT}

# Adobe of the caravan roads: sand-plaster render over mud brick, the render gone in patches.
ADOBE = dict(
    wall=[('or:adobe', 10), ('or:adobe_bricks', 3), ('smooth_sandstone', 1)],
    base=[('or:adobe_bricks', 5), ('cut_sandstone', 2), ('sandstone', 1)],
    roof=[('or:adobe', 5), ('packed_mud', 1), ('smooth_sandstone', 1)],
    floor=[('smooth_sandstone', 4), ('or:adobe_bricks', 2), ('packed_mud', 1)],
)
# The kajidic's work: red sandstone footings, adobe walls, crimson bands.
KAJIDIC = dict(
    wall=[('or:adobe', 9), ('or:adobe_bricks', 3), ('cut_red_sandstone', 1)],
    base=[('cut_red_sandstone', 4), ('red_sandstone', 2), ('smooth_red_sandstone', 2)],
    roof=[('or:adobe', 4), ('smooth_red_sandstone', 1)],
    floor=[('smooth_red_sandstone', 3), ('or:adobe_bricks', 2), ('cut_red_sandstone', 1)],
)
SCRAP = [('or:rusted_plating', 6), ('or:durasteel_plating', 3), ('or:wreck_plating', 3),
         ('weathered_cut_copper', 1), ('or:wreck_plating_scorched', 1)]
HULL = [('or:durasteel_plating', 10), ('or:wreck_plating', 4), ('or:rusted_plating', 2)]


# ---------------------------------------------------------------- ground

class Ground:
    """The walking surface over the template's footprint in half blocks: h[x][z] = 2 * the feet height.

    h 10 is a full ground block at y 4 with feet at y 5; 11 adds a bottom slab at y 5 (feet at 5.5).
    Build the heightfield (mounds, drifts, troughs, feathering), then apply() it before building on it."""

    def __init__(self, t, feet, kind='dunes', seed=0):
        self.t = t
        self.base = feet
        self.kind = kind
        self.mat = GROUNDS[kind]
        self.seed = seed
        self.h = [[2 * feet for _ in range(t.sz)] for _ in range(t.sx)]
        self.open = [[True for _ in range(t.sz)] for _ in range(t.sx)]  # clear above with explicit air

    def inside(self, x, z):
        return 0 <= x < self.t.sx and 0 <= z < self.t.sz

    def get(self, x, z):
        return self.h[x][z] if self.inside(x, z) else None

    def feet(self, x, z):
        """The feet layer at (x, z) (the cell above the top full ground block)."""
        return (self.h[x][z] + 1) // 2

    def raise_to(self, x, z, h2):
        if self.inside(x, z) and h2 > self.h[x][z]:
            self.h[x][z] = int(h2)

    def lower_to(self, x, z, h2):
        if self.inside(x, z) and h2 < self.h[x][z]:
            self.h[x][z] = int(h2)

    def noise(self, amp=2, scale=7.0, seed_offset=0):
        """Gentle ripples of +-amp half blocks."""
        n = Noise(self.seed + 11 + seed_offset, scale)
        for x in range(self.t.sx):
            for z in range(self.t.sz):
                self.h[x][z] += int(round((n.at(x, 0, z) - 0.5) * 2 * amp))

    def mound(self, cx, cz, rx, rz, height2, power=1.6, raise_only=True):
        """An elliptical mound `height2` half blocks high at its centre, falling to 0 at its rim."""
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                if not self.inside(x, z):
                    continue
                d = math.sqrt(((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2)
                if d >= 1.0:
                    continue
                v = 2 * self.base + height2 * (1 - d ** power)
                if raise_only:
                    self.raise_to(x, z, round(v))
                else:
                    self.h[x][z] = int(round(v))

    def trough(self, points, width, depth2, fall=1.5):
        """A furrow along a polyline of (x, z), `depth2` half blocks deep in its middle."""
        for x in range(self.t.sx):
            for z in range(self.t.sz):
                d = _dist_to_polyline(x, z, points)
                if d < width / 2.0 + fall:
                    k = 1.0 if d <= width / 2.0 else max(0.0, 1 - (d - width / 2.0) / fall)
                    self.lower_to(x, z, round(2 * self.base - depth2 * k))

    def drift(self, wall_cells, side, height2, length, seed=0, jag=0.35):
        """Sand banked against cells (a set of (x, z)) on `side` ('north', ...): `height2` half blocks at the
        wall, thinning to nothing `length` cells out, ragged by noise."""
        dx, dz = B.step(side)
        n = Noise(self.seed + 31 + seed, 3.0)
        for (x, z) in wall_cells:
            for i in range(1, length + 1):
                px, pz = x + dx * i, z + dz * i
                if not self.inside(px, pz):
                    break
                k = 1 - (i - 1) / float(length)
                rag = 1 + (n.at(px, 0, pz) - 0.5) * 2 * jag
                self.raise_to(px, pz, round(2 * self.base + height2 * k * rag))

    def ring(self, x, z, sides=None):
        """Cells in from the nearest of the given edges ('north', 'south', 'east', 'west'; default all)."""
        sx, sz = self.t.sx, self.t.sz
        sides = sides or ('north', 'south', 'east', 'west')
        d = []
        if 'west' in sides:
            d.append(x)
        if 'east' in sides:
            d.append(sx - 1 - x)
        if 'north' in sides:
            d.append(z)
        if 'south' in sides:
            d.append(sz - 1 - z)
        return min(d) if d else 10 ** 6

    def taper(self, rings=3, slope=2, up=True, down=True, sides=None):
        """Caps the ground near the template's edges so it meets the feathered rings with no cliff: a cell
        r cells in from the edge stays within slope * (r - rings + 1) half blocks of the base level."""
        sx, sz = self.t.sx, self.t.sz
        for x in range(sx):
            for z in range(sz):
                r = self.ring(x, z, sides)
                if r < rings:
                    continue
                lim = slope * (r - rings + 1)
                if up:
                    self.h[x][z] = min(self.h[x][z], 2 * self.base + lim)
                if down:
                    self.h[x][z] = max(self.h[x][z], 2 * self.base - lim)

    def seam(self, sides, width=4):
        """Eases the ground to the base level along the given edges, where a jigsaw neighbour's ground (also
        at the base level there) joins this piece's."""
        for x in range(self.t.sx):
            for z in range(self.t.sz):
                r = self.ring(x, z, sides)
                if r < width:
                    k = r / float(width)
                    self.h[x][z] = int(round(2 * self.base + (self.h[x][z] - 2 * self.base) * k))

    def feather(self, rings=3, drop=(3, 2, 1), sides=None):
        """Lowers the outer rings toward the natural terrain and leaves the cells above them unset, so the
        template's ground meets higher terrain without a cut and lower terrain without a ledge. sides: the
        edges that meet natural terrain (a jigsaw piece leaves out the edges where it joins its neighbours)."""
        sx, sz = self.t.sx, self.t.sz
        for x in range(sx):
            for z in range(sz):
                r = self.ring(x, z, sides)
                if r < rings:
                    self.h[x][z] = min(self.h[x][z], 2 * self.base - drop[r])
                    self.open[x][z] = False

    def apply(self, clear_above=3, skip=None, top=None):
        """Writes the ground: columns from y 0 to the surface (top, under, deep), a slab where the height
        is odd, and explicit air `clear_above` cells over the surface (not over the feathered rings)."""
        t = self.t
        m = self.mat
        ftop = mix(self.seed + 1, top or m['top'], 3.0)
        fund = mix(self.seed + 2, m['under'], 4.0)
        fdeep = mix(self.seed + 3, m['deep'], 5.0)
        for x in range(t.sx):
            for z in range(t.sz):
                if skip and (x, z) in skip:
                    continue
                h2 = self.h[x][z]
                full = h2 // 2  # cells 0 .. full-1 are full blocks
                for y in range(0, min(full, t.sy)):
                    depth = full - 1 - y
                    s = ftop(x, y, z) if depth == 0 else fund(x, y, z) if depth <= 2 else fdeep(x, y, z)
                    t.set(x, y, z, s)
                if h2 % 2 == 1 and full < t.sy:
                    t.set(x, full, z, slab(m['slab']))
                if self.open[x][z] and clear_above > 0:
                    y0 = (h2 + 1) // 2
                    for y in range(y0, min(t.sy, y0 + clear_above)):
                        if t.get(x, y, z) is None:
                            t.set(x, y, z, AIR)

    def scatter(self, density=0.02, options=None, region=None, avoid=None):
        """Dry plants and small things on open ground cells (the ground's own top blocks only)."""
        t = self.t
        opts = options or [(S('dead_bush'), 4), (S('short_dry_grass'), 5), (S('tall_dry_grass', half='lower'), 1)]
        x0, z0, x1, z1 = region or (0, 0, t.sx - 1, t.sz - 1)
        tops = {B.full_name(s) for s, _ in self.mat['top']}
        for x in range(max(0, x0), min(t.sx - 1, x1) + 1):
            for z in range(max(0, z0), min(t.sz - 1, z1) + 1):
                if avoid and (x, z) in avoid:
                    continue
                h2 = self.h[x][z]
                if h2 % 2:
                    continue
                y = h2 // 2
                if y >= t.sy - 2 or B.name_of(t.get(x, y - 1, z) or '') not in tops:
                    continue
                if not free(t, x, y, z) or not free(t, x, y + 1, z):
                    continue
                if t.rng.random() >= density:
                    continue
                s = scatter_pick(t.rng, opts)
                if 'half=lower' in s:
                    t.set(x, y, z, s)
                    t.set(x, y + 1, z, s.replace('half=lower', 'half=upper'))
                else:
                    t.set(x, y, z, s)


def _dist_to_polyline(x, z, pts):
    best = 1e9
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        vx, vz = bx - ax, bz - az
        L = vx * vx + vz * vz
        u = 0.0 if L == 0 else max(0.0, min(1.0, ((x - ax) * vx + (z - az) * vz) / L))
        px, pz = ax + u * vx, az + u * vz
        best = min(best, math.hypot(x - px, z - pz))
    return best


def surface_y(t, x, z, top=None):
    """The feet layer at (x, z): the first walk-through cell above the highest solid cell below `top`."""
    top = t.sy - 1 if top is None else top
    for y in range(top, -1, -1):
        s = t.get(x, y, z)
        if s is not None and not mod.walk_through(s):
            n = B.name_of(s)
            if n.endswith('_slab') and 'type=bottom' in s:
                return y + 1
            return y + 1
    return 0


# ---------------------------------------------------------------- walls and rooms

def wall_material(kind, seed):
    """(wall, base, roof, floor) pickers of a material family ('adobe' or 'kajidic')."""
    fam = ADOBE if kind == 'adobe' else KAJIDIC
    return (mix(seed, fam['wall'], 3.5), mix(seed + 5, fam['base'], 3.0), mix(seed + 7, fam['roof'], 4.0),
            mix(seed + 9, fam['floor'], 3.0))


def adobe_house(t, x0, z0, x1, z1, y0, height, seed, kind='adobe', doors=(), windows=(), parapet=True,
                vigas='stripped_spruce_log', spouts=True, roof=True, round_corners=True, floor=True,
                band=None, interior=True):
    """A flat-roofed adobe building: the floor at y0, walls y0+1 .. y0+height, the roof slab at y0+height+1,
    a parapet over it with a slab cap, rounded corners, protruding roof beams (vigas) and roof spouts.

    doors: [(side, offset, width, tall)] cut through the wall (side 'north'... offset along the wall from its
    west or north end); windows: [(side, offset, y, tall)]. band: a state for a painted course under the
    parapet (the kajidic's crimson). Returns the roof y."""
    wall, base, roofp, floorp = wall_material(kind, seed)
    ytop = y0 + height
    yroof = ytop + 1
    # walls
    for y in range(y0 + 1, ytop + 1):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                t.set(x, y, z, base(x, y, z) if y <= y0 + 1 else wall(x, y, z))
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                t.set(x, y, z, base(x, y, z) if y <= y0 + 1 else wall(x, y, z))
    if band is not None:
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                t.set(x, ytop, z, band)
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                t.set(x, ytop, z, band)
    if floor:
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                t.set(x, y0, z, floorp(x, y0, z))
    if interior:
        t.air(x0 + 1, y0 + 1, z0 + 1, x1 - 1, ytop, z1 - 1)
    if roof:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                t.set(x, yroof, z, roofp(x, yroof, z))
        if parapet:
            for x in range(x0, x1 + 1):
                for z in (z0, z1):
                    t.set(x, yroof + 1, z, wall(x, yroof + 1, z))
            for z in range(z0, z1 + 1):
                for x in (x0, x1):
                    t.set(x, yroof + 1, z, wall(x, yroof + 1, z))
            n = Noise(seed + 41, 2.0)
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        if n.at(x, 0, z) > 0.62:
                            t.set(x, yroof + 2, z, slab('or:adobe_slab'))
    if round_corners:
        top = yroof + 1 if (roof and parapet) else ytop
        for (cx, cz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            t.set(cx, top + 1, cz, None)
            t.set(cx, top, cz, slab('or:adobe_slab'))
    # vigas: roof beams through the walls on the long sides
    if vigas and roof:
        long_x = (x1 - x0) >= (z1 - z0)
        if long_x:
            for x in range(x0 + 2, x1 - 1, 3):
                for z, side in ((z0 - 1, 'north'), (z1 + 1, 'south')):
                    if t.inside(x, ytop, z) and free(t, x, ytop, z):
                        t.set(x, ytop, z, log(vigas, 'z') if 'log' in vigas else vigas)
        else:
            for z in range(z0 + 2, z1 - 1, 3):
                for x in (x0 - 1, x1 + 1):
                    if t.inside(x, ytop, z) and free(t, x, ytop, z):
                        t.set(x, ytop, z, log(vigas, 'x') if 'log' in vigas else vigas)
    # canales: spouts at the parapet's foot
    if spouts and roof and parapet:
        r = t.rng
        for side in ('north', 'south', 'east', 'west'):
            if r.random() < 0.6:
                if side in ('north', 'south'):
                    x = r.randint(x0 + 2, max(x0 + 2, x1 - 2))
                    z = z0 - 1 if side == 'north' else z1 + 1
                else:
                    z = r.randint(z0 + 2, max(z0 + 2, z1 - 2))
                    x = x0 - 1 if side == 'west' else x1 + 1
                if t.inside(x, yroof, z) and free(t, x, yroof, z):
                    t.set(x, yroof, z, trapdoor('spruce_trapdoor', B.opposite(side), 'top'))
    for (side, off, width, tall) in doors:
        cut_door(t, x0, z0, x1, z1, y0, side, off, width, tall)
    for (side, off, y, tall) in windows:
        cut_window(t, x0, z0, x1, z1, side, off, y, tall)
    return yroof


def _wall_cell(x0, z0, x1, z1, side, off):
    if side == 'north':
        return x0 + off, z0
    if side == 'south':
        return x0 + off, z1
    if side == 'west':
        return x0, z0 + off
    return x1, z0 + off


def cut_door(t, x0, z0, x1, z1, y0, side, off, width=2, tall=3, lintel='stripped_spruce_log'):
    """An opening `width` wide and `tall` high through a wall of the box, with a lintel over it."""
    along_x = side in ('north', 'south')
    for i in range(width):
        x, z = _wall_cell(x0, z0, x1, z1, side, off + i)
        for y in range(y0 + 1, y0 + 1 + tall):
            t.set(x, y, z, AIR)
        if lintel:
            t.set(x, y0 + 1 + tall, z, log(lintel, 'x' if along_x else 'z') if 'log' in lintel else lintel)


def cut_window(t, x0, z0, x1, z1, side, off, y, tall=1, fill='iron_bars', sill=True):
    x, z = _wall_cell(x0, z0, x1, z1, side, off)
    for k in range(tall):
        t.set(x, y + k, z, S(fill) if fill else AIR)
    if sill:
        dx, dz = B.step(side)
        if t.inside(x + dx, y - 1, z + dz) and free(t, x + dx, y - 1, z + dz):
            t.set(x + dx, y - 1, z + dz, stair('or:adobe_stairs', B.opposite(side), 'top'))


def buttress(t, x, z, y0, y1, side, kind='adobe'):
    """A tapered buttress standing outside a wall at (x, z) (the cell just outside it), leaning on `side`'s wall."""
    m = 'or:adobe_bricks' if kind == 'adobe' else 'cut_red_sandstone'
    for y in range(y0, y1):
        t.set(x, y, z, m)
    t.set(x, y1, z, stair('or:adobe_stairs', B.opposite(side)))


# ---------------------------------------------------------------- cloth

def awning(t, x0, z0, x1, z1, y, cloth='or:canvas_slab', stripes=None, posts='spruce_fence', post_from=None,
           post_every=0):
    """A cloth shade of top slabs at y over the rectangle (head room under it), `stripes` (another cloth) on every
    other row along z, and posts at the corners (and every `post_every` cells along x) from post_from
    (default y - 3) up to it."""
    xa, xb = sorted((x0, x1))
    za, zb = sorted((z0, z1))
    for x in range(xa, xb + 1):
        for z in range(za, zb + 1):
            st = stripes if (stripes and (z - za) % 2 == 1) else cloth
            t.set(x, y, z, slab(st, 'top') if st.endswith('_slab') else st)
    if posts:
        pf = y - 3 if post_from is None else post_from
        corners = [(xa, za), (xb, za), (xa, zb), (xb, zb)]
        if post_every:
            for x in range(xa + post_every, xb, post_every):
                corners += [(x, za), (x, zb)]
        for (px, pz) in corners:
            for yy in range(pf, y):
                t.set(px, yy, pz, S(posts) if '[' not in posts else posts)


def drape(t, x, y_top, z, length, cloth='or:canvas_crimson'):
    """A hanging cloth strip from y_top down `length` cells."""
    for k in range(length):
        t.set(x, y_top - k, z, cloth)


# ---------------------------------------------------------------- lights

def hanging_lamp(t, x, y_top, z, drop=1, kind='lantern'):
    """A lantern hanging `drop` chain links under the ceiling cell y_top + 1 (the lamp ends at y_top - drop)."""
    for k in range(drop):
        t.set(x, y_top - k, z, chain('y'))
    t.set(x, y_top - drop, z, lantern(True, kind))


def lamp_post(t, x, y, z, height=3, post='spruce_fence', kind='lantern'):
    for k in range(height):
        t.set(x, y + k, z, S(post))
    t.set(x, y + height, z, lantern(False, kind))


def brazier(t, x, y, z, stand='or:adobe_bricks'):
    """A fire bowl: a lit campfire on a stand."""
    t.set(x, y, z, stand)
    t.set(x, y + 1, z, campfire(True))


# ---------------------------------------------------------------- furniture and props

def table(t, x, y, z, top='spruce_pressure_plate', leg='spruce_fence'):
    t.set(x, y, z, S(leg))
    if top:
        t.set(x, y + 1, z, S(top) if top.endswith('pressure_plate') else top)


def slab_table(t, x0, z0, x1, z1, y, top='or:durasteel_plating_slab'):
    """A table top of top slabs at y (the legs are the slabs' own thickness: a counter)."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            t.set(x, y, z, slab(top, 'top'))


def chair(t, x, y, z, facing, name='spruce_stairs'):
    """A seat looking toward `facing` (its back on the other side)."""
    t.set(x, y, z, stair(name, B.opposite(facing)))


def crate_stack(t, x, y, z, height, rng, kinds=None):
    kinds = kinds or [('or:cargo_crate', 5), (barrel(), 2), ('or:cargo_crate_republic', 1)]
    for k in range(height):
        t.set(x, y + k, z, scatter_pick(rng, kinds))


def bedroll(t, x, y, z, along='x', color='brown'):
    t.set(x, y, z, S('%s_carpet' % color))
    if along == 'x':
        t.set(x + 1, y, z, S('%s_carpet' % color))
    else:
        t.set(x, y, z + 1, S('%s_carpet' % color))


def water_jar(t, x, y, z, full=True):
    t.set(x, y, z, S('water_cauldron', level=3) if full else S('cauldron'))


# ---------------------------------------------------------------- water works

def condenser(t, x, y, z, height=6, owned=False, rng=None):
    """A moisture condenser: a plated foot, a shaft of pipes, a copper collector head with rods, and a tap
    over a jar at its foot. owned: the kajidic's, its tap caged."""
    t.set(x, y, z, 'or:durasteel_plating')
    for k in range(1, height - 1):
        t.set(x, y + k, z, 'or:durasteel_pipes[axis=y]' if k % 3 else 'weathered_copper_grate[waterlogged=false]')
    t.set(x, y + height - 1, z, 'weathered_cut_copper')
    t.set(x, y + height, z, rod('up'))
    for (dx, dz, f) in ((1, 0, 'east'), (-1, 0, 'west'), (0, 1, 'south'), (0, -1, 'north')):
        if free(t, x + dx, y + height - 1, z + dz):
            t.set(x + dx, y + height - 1, z + dz, trapdoor('iron_trapdoor', f, 'top', True))
    # the tap and its jar to the south
    if free(t, x, y + 1, z + 1):
        t.set(x, y + 1, z + 1, S('tripwire_hook', facing='south', attached=False, powered=False))
    if free(t, x, y, z + 1):
        t.set(x, y, z + 1, S('water_cauldron', level=2 if not owned else 3))
    if owned:
        for (dx, dz) in ((-1, 1), (1, 1), (-1, 2), (0, 2), (1, 2)):
            for k in range(0, 2):
                if free(t, x + dx, y + k, z + dz):
                    t.set(x + dx, y + k, z + dz, S('iron_bars'))


def cistern(t, x0, z0, x1, z1, y, depth=2, rim='or:adobe_bricks', lid=True, water=True):
    """A sunk water tank: a rim one high over the ground at y, water `depth` deep below it, and a lid of
    iron bars over all of it (lid=True: the water is locked away)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                for yy in range(y - depth, y + 1):
                    t.set(x, yy, z, rim)
                t.set(x, y - depth - 1, z, rim)
            else:
                for yy in range(y - depth, y):
                    t.set(x, yy, z, S('water') if water else AIR)
                t.set(x, y - depth - 1, z, rim)
                t.set(x, y, z, S('iron_bars') if lid else AIR)


def well(t, cx, cz, y, depth=4, curb='or:adobe_bricks', frame='spruce_fence', water=True):
    """A 3x3 well: a curb wall one high around a 1x1 shaft with water at its foot, a windlass frame over it."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx == 0 and dz == 0:
                for yy in range(y - depth, y + 1):
                    t.set(cx, yy, cz, AIR)
                for yy in range(y - depth - 1, y - depth + 1):
                    t.set(cx, yy, cz, S('water') if water else 'sand')
                t.set(cx, y - depth - 2, cz, curb)
            else:
                t.set(cx + dx, y, cz + dz, curb)
                for yy in range(y - depth - 1, y):
                    t.set(cx + dx, yy, cz + dz, curb)
    for dx in (-1, 1):
        t.set(cx + dx, y + 1, cz, S(frame))
        t.set(cx + dx, y + 2, cz, S(frame))
    t.set(cx, y + 3, cz, log('stripped_spruce_log', 'x'))
    t.set(cx - 1, y + 3, cz, log('stripped_spruce_log', 'x'))
    t.set(cx + 1, y + 3, cz, log('stripped_spruce_log', 'x'))
    t.set(cx, y + 2, cz, chain('y'))
    t.set(cx, y + 1, cz, chain('y'))


# ---------------------------------------------------------------- scrap and bones

def scrap_heap(t, cx, y, cz, r, height, seed, rng=None, extra=None):
    """A heap of salvage: plates and beams piled high in the middle, slabs and stairs at the flanks, grit
    decals around. `y` is the feet layer of the ground under it."""
    rng = rng or t.rng
    n = Noise(seed, 2.0)
    pick = mix(seed, SCRAP, 2.0)
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            d = math.hypot(x - cx, z - cz) / max(0.5, r)
            if d > 1.15:
                continue
            hh = height * (1 - d * d) + (n.at(x, 0, z) - 0.5) * 1.6
            k = int(hh)
            # a column of the heap reaches down to the ground where the ground falls away under its rim
            yb = y
            while k > 0 and yb > max(0, y - 3) and not solid_at(t, x, yb - 1, z):
                yb -= 1
            for yy in range(yb, y + max(0, k)):
                t.set(x, yy, z, pick(x, yy, z))
            top = y + max(0, k)
            # (the random draws come first, so a skipped cell does not change the rest of the heap)
            on_ground = k > 0 or (solid_at(t, x, top - 1, z) and free(t, x, top, z))
            if 0 <= hh - k and d <= 1.0 and rng.random() < 0.7:
                s = scatter_pick(rng, [(slab('or:durasteel_plating_slab'), 3),
                                       (stair('or:durasteel_plating_stairs', rng.choice(B.DIRS)), 2),
                                       ('or:debris', 3), (S('iron_bars'), 1), (chain('x'), 1)])
                if on_ground:
                    t.set(x, top, z, s)
            elif d <= 1.15 and k <= 0 and rng.random() < 0.5:
                if on_ground:
                    t.set(x, top, z, 'or:debris')
    if extra:
        for (dx, dy, dz, s) in extra:
            t.set(int(cx + dx), y + dy, int(cz + dz), s)


def ribcage(t, x0, y, z, length, along='x', span=3, height=4, seed=0):
    """A great beast's ribs arching out of the sand: bone pillars in pairs along a spine."""
    rng = t.rng
    for i in range(length):
        if i % 2:
            continue
        h = max(1, height - abs(i - length // 2) // 2 + rng.randint(-1, 0))
        for side in (-1, 1):
            for k in range(h):
                lean = 1 if k >= h - 1 else 0
                if along == 'x':
                    t.set(x0 + i, y + k, z + side * (span - lean), S('bone_block', axis='y'))
                else:
                    t.set(z + side * (span - lean), y + k, x0 + i, S('bone_block', axis='y'))
    for i in range(length):
        if along == 'x':
            t.set(x0 + i, y, z, S('bone_block', axis='x'))
        else:
            t.set(z, y, x0 + i, S('bone_block', axis='z'))


# ---------------------------------------------------------------- salvage gear, poles, vehicles

def gantry(t, x, z0, z1, y_feet_n, y_feet_s, y_top, hook_y, trolley_z=None, load=None):
    """A salvage gantry: two legs of beams at (x, z0) and (x, z1) from their feet to y_top, a cross beam
    along z on top, a trolley and a chain dropping to hook_y (where `load` hangs, if given)."""
    for (z, yf) in ((z0, y_feet_n), (z1, y_feet_s)):
        for y in range(yf, y_top):
            t.set(x, y, z, log('or:durasteel_beam', 'y'))
        t.set(x, yf, z, 'or:durasteel_plating')
        # foot braces
        for dx in (-1, 1):
            t.set(x + dx, yf, z, stair('or:durasteel_plating_stairs', 'east' if dx < 0 else 'west'))
    for z in range(z0, z1 + 1):
        t.set(x, y_top, z, log('or:durasteel_beam', 'z'))
    tz = (z0 + z1) // 2 if trolley_z is None else trolley_z
    t.set(x, y_top - 1, tz, 'or:durasteel_plating')
    for y in range(hook_y + 1, y_top - 1):
        t.set(x, y, tz, chain('y'))
    if load:
        t.set(x, hook_y, tz, load)
    else:
        t.set(x, hook_y, tz, rod('down'))


def claim_pole(t, x, y, z, height=7, cloth_side='east', banner_side='south', banner_color='red'):
    """The kajidic's claim: a pole of bars with a crossbar, a crimson cloth hanging from it and a red banner.
    `y` is the foot cell."""
    t.set(x, y, z, 'cut_red_sandstone')
    for k in range(1, height):
        t.set(x, y + k, z, S('iron_bars'))
    dx, dz = B.step(cloth_side)
    t.set(x, y + height, z, log('or:durasteel_beam', 'x' if dx else 'z'))
    t.set(x + dx, y + height, z + dz, log('or:durasteel_beam', 'x' if dx else 'z'))
    t.set(x + 2 * dx, y + height, z + 2 * dz, log('or:durasteel_beam', 'x' if dx else 'z'))
    for k in range(1, 4):
        t.set(x + 2 * dx, y + height - k, z + 2 * dz, 'or:canvas_crimson')
    bx, bz = B.step(banner_side)
    if free(t, x + bx, y + height - 2, z + bz):
        mod.banner(t, x + bx, y + height - 2, z + bz, banner_side, banner_color)


def skiff(t, x0, y, z0, length=9, width=5, along='x', wreck=False):
    """A cargo skiff (repulsor barge) resting on the ground at feet layer y: a plated deck one up on skids,
    rails, a control post at the stern and crates on the deck."""
    cells = []
    for i in range(length):
        for j in range(width):
            cells.append((i, j))

    def P(i, yy, j):
        return (x0 + i, yy, z0 + j) if along == 'x' else (x0 + j, yy, z0 + i)
    for (i, j) in cells:
        edge_i = i in (0, length - 1)
        edge_j = j in (0, width - 1)
        if (i in (1, length - 2)) and j in (1, width - 2):
            t.set(*P(i, y, j), log('or:durasteel_beam', 'x' if along == 'z' else 'z'))
        t.set(*P(i, y + 1, j), slab('or:durasteel_deck_slab', 'top') if not (edge_i and edge_j) else
              slab('or:durasteel_plating_slab', 'top'))
        if edge_j and not edge_i:
            t.set(*P(i, y + 2, j), S('iron_bars'))
    # bow: stairs sloping down at the front
    for j in range(width):
        t.set(*P(length, y + 1, j), stair('or:durasteel_plating_stairs', B.opposite('east' if along == 'x' else 'south'), 'top'))
    # control post at the stern
    t.set(*P(1, y + 2, width // 2), 'or:console[facing=%s]' % ('east' if along == 'x' else 'south'))
    if wreck:
        t.set(*P(length - 2, y + 2, 1), 'or:scorch_mark')


def boulder(t, cx, y, cz, r, seed, mats=None, squash=0.7):
    """A weathered rock lump of radius r (blocks) resting on the ground at feet layer y, sunk a cell into it."""
    mats = mats or [('red_sandstone', 4), ('smooth_red_sandstone', 2), ('terracotta', 1)]
    pick = mix(seed, mats, 2.0)
    n = Noise(seed, 1.7)
    R = int(r) + 1
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            for yy in range(y - 1, y + int(r * squash) + 2):
                if not t.inside(x, yy, z):
                    continue
                d = math.sqrt((x - cx) ** 2 + ((yy - (y - 0.5)) / squash) ** 2 + (z - cz) ** 2)
                if d <= r * (0.75 + 0.5 * n.at(x, yy, z)):
                    t.set(x, yy, z, pick(x, yy, z))


# ---------------------------------------------------------------- hulls

class Hull:
    """A ship's hull lying along x from x0 to x1: superellipse sections (half width w, half height h, exponent n,
    centre yc and zc) interpolated between keyframes [(x, yc, zc, w, h, n)], rolled `roll` radians about x
    (positive: the hull's top leans toward -z, north)."""

    def __init__(self, keys, roll=0.0):
        self.keys = sorted(keys)
        self.x0 = self.keys[0][0]
        self.x1 = self.keys[-1][0]
        self.roll = roll
        self.cos = math.cos(roll)
        self.sin = math.sin(roll)

    def at(self, x):
        ks = self.keys
        if x <= ks[0][0]:
            return ks[0][1:]
        for a, b in zip(ks, ks[1:]):
            if a[0] <= x <= b[0]:
                f = (x - a[0]) / float(b[0] - a[0])
                return tuple(a[i] + (b[i] - a[i]) * f for i in range(1, 6))
        return ks[-1][1:]

    def local(self, x, y, z):
        """(u, v): the cell's offset in the section's own axes (u across, v up)."""
        yc, zc, w, h, n = self.at(x)
        dz, dy = z - zc, y - yc
        u = dz * self.cos + dy * self.sin
        v = -dz * self.sin + dy * self.cos
        return u, v

    def value(self, x, y, z, shrink=0.0):
        yc, zc, w, h, n = self.at(x)
        u, v = self.local(x, y, z)
        ww, hh = max(0.3, w - shrink), max(0.3, h - shrink)
        return abs(u / ww) ** n + abs(v / hh) ** n

    def voxels(self, sy, sz, thick=1.15):
        """(shell, inside): sets of cells. The shell is closed: every face neighbour of an inside cell is shell
        or inside."""
        shell, inside = set(), set()
        for x in range(self.x0, self.x1 + 1):
            for y in range(0, sy):
                for z in range(0, sz):
                    if self.value(x, y, z) <= 1.0:
                        if x in (self.x0, self.x1) or self.value(x, y, z, thick) > 1.0:
                            shell.add((x, y, z))
                        else:
                            inside.add((x, y, z))
        for (x, y, z) in list(inside):
            for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
                if q not in inside and q not in shell and 0 <= q[1] < sy and 0 <= q[2] < sz:
                    shell.add(q)
        return shell, inside


class PosedHull:
    """A hull (keyframes in its own frame: x along it, centre yc, zc about 0) set into the template at
    `origin` (where its own x = 0, y = 0, z = 0 lands), turned `yaw` radians about y (from +x toward +z),
    pitched `pitch` radians (positive lifts its +x end) and rolled `roll` radians about its own axis.
    open_ends: which ends ('x0', 'x1') are cut open (no end cap: the section shows its decks)."""

    def __init__(self, keys, origin, yaw=0.0, pitch=0.0, roll=0.0, open_ends=()):
        self.hull = Hull(keys, roll)
        self.ox, self.oy, self.oz = origin
        self.cyaw, self.syaw = math.cos(yaw), math.sin(yaw)
        self.cp, self.sp = math.cos(pitch), math.sin(pitch)
        self.open_ends = open_ends

    def to_local(self, x, y, z):
        dx, dy, dz = x - self.ox, y - self.oy, z - self.oz
        lx = dx * self.cyaw + dz * self.syaw
        lz = -dx * self.syaw + dz * self.cyaw
        px = lx * self.cp + dy * self.sp
        py = -lx * self.sp + dy * self.cp
        return px, py, lz

    def to_world(self, lx, ly, lz):
        px = lx * self.cp - ly * self.sp
        py = lx * self.sp + ly * self.cp
        wx = px * self.cyaw - lz * self.syaw
        wz = px * self.syaw + lz * self.cyaw
        return (int(round(self.ox + wx)), int(round(self.oy + py)), int(round(self.oz + wz)))

    def value(self, x, y, z, shrink=0.0):
        lx, ly, lz = self.to_local(x, y, z)
        h = self.hull
        if lx < h.x0 - 0.5 or lx > h.x1 + 0.5:
            return 9.0, lx, ly, lz
        yc, zc, w, hh, n = h.at(lx)
        dz, dy = lz - zc, ly - yc
        u = dz * h.cos + dy * h.sin
        v = -dz * h.sin + dy * h.cos
        ww, hh2 = max(0.3, w - shrink), max(0.3, hh - shrink)
        return abs(u / ww) ** n + abs(v / hh2) ** n, lx, ly, lz

    def voxels(self, t, box=None, thick=1.15):
        """(shell, inside, local): the hull's cells inside the template (or box), and each cell's own-frame
        coordinates."""
        x0, y0, z0, x1, y1, z1 = box or (0, 0, 0, t.sx - 1, t.sy - 1, t.sz - 1)
        h = self.hull
        shell, inside, local = set(), set(), {}
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    v, lx, ly, lz = self.value(x, y, z)
                    if v > 1.0:
                        continue
                    local[(x, y, z)] = (lx, ly, lz)
                    cap = (lx < h.x0 + 0.6 and 'x0' not in self.open_ends) or (lx > h.x1 - 0.6 and 'x1' not in self.open_ends)
                    if cap or self.value(x, y, z, thick)[0] > 1.0:
                        shell.add((x, y, z))
                    else:
                        inside.add((x, y, z))
        for (x, y, z) in list(inside):
            for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
                if q in inside or q in shell or not t.inside(*q):
                    continue
                lx = self.to_local(*q)[0]
                if (lx < h.x0 and 'x0' in self.open_ends) or (lx > h.x1 and 'x1' in self.open_ends):
                    continue
                shell.add(q)
                local.setdefault(q, self.to_local(*q))
        return shell, inside, local


# ---------------------------------------------------------------- salvage yards

def plate_stack(t, x, y, z, height, rng=None):
    """Sorted salvage: plates stacked flat, rusted and clean, a slab on top."""
    rng = rng or t.rng
    for k in range(height):
        t.set(x, y + k, z, scatter_pick(rng, [('or:durasteel_plating', 3), ('or:rusted_plating', 3), ('or:wreck_plating', 2)]))
    if rng.random() < 0.6:
        t.set(x, y + height, z, slab('or:durasteel_plating_slab'))


def container_shack(t, x0, y, z0, length=6, width=4, along='x', door_side='south', seed=0, awning_cloth=None):
    """A cargo container turned into a shack: plated walls of crate blocks, a door, a slit window and an
    awning over the door. Returns the interior box (x0, y, z0, x1, y1, z1)."""
    cloth = awning_cloth or slab('or:canvas_slab', 'top')
    xs = range(x0, x0 + (length if along == 'x' else width))
    zs = range(z0, z0 + (width if along == 'x' else length))
    xa, xb, za, zb = xs[0], xs[-1], zs[0], zs[-1]
    n = Noise(seed, 2.0)
    for x in xs:
        for z in zs:
            edge = x in (xa, xb) or z in (za, zb)
            t.set(x, y - 1, z, 'or:durasteel_deck')
            for yy in range(y, y + 3):
                if edge:
                    k = n.at(x, yy, z)
                    t.set(x, yy, z, 'or:cargo_crate_imperial' if k > 0.7 else 'or:rusted_plating' if k > 0.5 else 'or:cargo_crate')
                else:
                    t.set(x, yy, z, AIR)
            t.set(x, y + 3, z, 'or:rusted_plating' if n.at(x, 9, z) > 0.55 else 'or:durasteel_plating')
    # door in the middle of door_side, an awning out over it
    if door_side in ('south', 'north'):
        dz = zb if door_side == 'south' else za
        dx = (xa + xb) // 2
        for yy in (y, y + 1, y + 2):
            t.set(dx, yy, dz, AIR)
            t.set(dx + 1, yy, dz, AIR)
        out = 1 if door_side == 'south' else -1
        for x in range(dx - 1, dx + 3):
            t.set(x, y + 3, dz + out, cloth)
        t.set(xa + 1 if dx - 1 > xa else xb - 1, y + 1, dz, S('iron_bars'))
    else:
        dx = xb if door_side == 'east' else xa
        dz = (za + zb) // 2
        for yy in (y, y + 1, y + 2):
            t.set(dx, yy, dz, AIR)
            t.set(dx, yy, dz + 1, AIR)
        out = 1 if door_side == 'east' else -1
        for z in range(dz - 1, dz + 3):
            t.set(dx + out, y + 3, z, cloth)
    return (xa + 1, y, za + 1, xb - 1, y + 2, zb - 1)


def smelter(t, x, y, z, facing='south', seed=0):
    """A scrap smelter: a blast furnace in a plated hood, a chimney of pipes, slag beside it."""
    t.set(x, y, z, S('blast_furnace', facing=facing, lit=True))
    t.set(x, y + 1, z, 'or:durasteel_vent')
    for k in range(2, 6):
        t.set(x, y + k, z, 'or:durasteel_pipes[axis=y]')
    dx, dz = B.step(facing)
    for (sx_, sz_) in ((dz, dx), (-dz, -dx)):
        t.set(x + sx_, y, z + sz_, 'or:rusted_plating')
        t.set(x + sx_, y + 1, z + sz_, stair('or:durasteel_plating_stairs', facing, 'top'))
    t.set(x + dx, y, z + dz, 'or:scorch_mark')
    t.set(x + dx * 2 + dz, y, z + dz * 2 + dx, 'magma_block')
    t.set(x + dx * 2 - dz, y, z + dz * 2 - dx, 'blackstone')


def write_loot(path, rolls, entries):
    """A chest loot table at loot_table/<path>.json in the format of the mod's tables: one pool of `rolls`
    (min, max) uniform rolls over entries [(item, weight, count_min, count_max)] (counts of 1 take no
    function)."""
    import json
    root = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
    full = os.path.join(root, 'src', 'main', 'resources', 'data', 'oldrepublic', 'loot_table', path + '.json')
    ents = []
    for (item, weight, a, b) in entries:
        if item.startswith('or:'):
            item = 'oldrepublic:' + item[3:]          # the scripts' shorthand for the mod's namespace
        e = {'type': 'minecraft:item', 'name': item if ':' in item else 'minecraft:' + item, 'weight': weight}
        if (a, b) != (1, 1):
            e['functions'] = [{'function': 'minecraft:set_count', 'add': False,
                               'count': {'type': 'minecraft:uniform', 'min': float(a), 'max': float(b)}}]
        ents.append(e)
    data = {'type': 'minecraft:chest', 'random_sequence': 'oldrepublic:' + path,
            'pools': [{'rolls': {'type': 'minecraft:uniform', 'min': float(rolls[0]), 'max': float(rolls[1])},
                       'entries': ents}]}
    os.makedirs(os.path.dirname(full), exist_ok=True)
    text = json.dumps(data, indent=2, sort_keys=True) + '\n'
    if not (os.path.exists(full) and open(full).read() == text):
        with open(full, 'w') as f:
            f.write(text)
    return full


# ---------------------------------------------------------------- checks

def _stand(t, x, y, z):
    """The standing height (in half blocks) of a person whose feet are in cell (x, y, z), or None."""
    if not t.inside(x, y, z):
        return None
    feet = t.get(x, y, z)
    below = t.get(x, y - 1, z) if y > 0 else None
    head = t.get(x, y + 1, z)
    head2 = t.get(x, y + 2, z)
    if feet is not None and not mod.walk_through(feet):
        n = B.name_of(feet)
        if (n.endswith('_slab') and 'type=bottom' in feet) or (n.endswith('_stairs') and 'half=bottom' in feet):
            if (head is None or mod.walk_through(head)) and (head2 is None or mod.walk_through(head2)):
                return 2 * y + 1
        return None
    if below is None or mod.walk_through(below):
        return None
    if head is not None and not mod.walk_through(head):
        return None
    nb = B.name_of(below)
    if nb.endswith('_fence') or nb.endswith('_wall') or nb.endswith('_bars') or nb.endswith('_pane'):
        return None
    return 2 * y


def _is_stair(t, x, y, z):
    s = t.get(x, y, z)
    return s is not None and B.name_of(s).endswith('_stairs') and 'half=bottom' in s


def walk(t, starts, goal, max_drop=3):
    """True when a person can walk from any of the start feet cells to the goal feet cell, climbing at most a
    half block per step (slabs and stairs) and dropping at most max_drop blocks. Returns (ok, visited)."""
    seen = set()
    q = deque()
    for s in starts:
        if _stand(t, *s) is not None:
            q.append(s)
            seen.add(s)
    while q:
        x, y, z = q.popleft()
        if (x, y, z) == tuple(goal):
            return True, seen
        h = _stand(t, x, y, z)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in range(1, -max_drop - 1, -1):
                n = (x + dx, y + dy, z + dz)
                if n in seen:
                    continue
                hn = _stand(t, *n)
                if hn is None:
                    continue
                if hn - h > (2 if _is_stair(t, x, y, z) else 1):
                    continue
                if hn > h and dy >= 0:
                    # climbing: the head must clear the step
                    hc = t.get(x, y + 2, z)
                    if hc is not None and not mod.walk_through(hc):
                        continue
                seen.add(n)
                q.append(n)
                break
    return False, seen


def edge_starts(t, ground=None):
    """Feet cells on the template's outer ring where a person can stand."""
    out = []
    for x in range(t.sx):
        for z in range(t.sz):
            if x not in (0, t.sx - 1) and z not in (0, t.sz - 1):
                continue
            for y in range(1, t.sy - 1):
                if _stand(t, x, y, z) is not None:
                    out.append((x, y, z))
    return out


def report_walk(t, name, anchors, extra=()):
    """Checks that every named anchor (feet anchors) and every (label, feet cell) in extra can be reached on
    foot from the template's edge, and prints the result."""
    starts = edge_starts(t)
    bad = []
    goals = [(a, tuple(cell)) for a in anchors for cell in t.anchors.get(a, [])] + [(l, tuple(c)) for (l, c) in extra]
    for (a, cell) in goals:
        ok, _ = walk(t, starts, cell)
        if not ok:
            bad.append('%s %s' % (a, cell))
    if bad:
        print('%s: NOT WALKABLE from the edge: %s' % (name, ', '.join(bad)))
    else:
        print('%s: walk check passed (%s)' % (name, ', '.join(list(anchors) + [l for (l, _) in extra])))
    return not bad


# ---------------------------------------------------------------- lighting

def light_inside(t, region, allowed=None, level=8, strength=13):
    """mod.light_fill limited to floor cells in `allowed` (a set of cells; None: every floor in the region), so
    a region box drawn round a hull or a building does not light the open ground beside it."""
    from collections import deque as _dq
    floors = [p for p in mod.floor_cells(t, region) if allowed is None or p in allowed]
    added = {}
    lit = mod.light_map(t)
    for _ in range(4096):
        dark = [p for p in floors if lit.get(p, 0) < level]
        if not dark:
            break
        buckets = {}
        for p in dark:
            buckets.setdefault((p[0] // 5, p[1] // 4, p[2] // 5), []).append(p)
        cells = max(sorted(buckets.items()), key=lambda kv: len(kv[1]))[1]
        mx = sum(p[0] for p in cells) / len(cells)
        mz = sum(p[2] for p in cells) / len(cells)
        x, y, z = min(cells, key=lambda p: ((p[0] - mx) ** 2 + (p[2] - mz) ** 2, p))
        head = (x, y + 1, z)
        if B.name_of(t.cells.get(head)) not in ('minecraft:air', 'minecraft:cave_air') or head in added:
            head = (x, y, z)
        added[head] = S('light', level=strength)
        if lit.get(head, 0) < strength:
            lit[head] = strength
            mod._spread(t, lit, _dq([head]), added)
    for p, s2 in added.items():
        t.set(*p, s2)
    return sorted(added)


# ---------------------------------------------------------------- saving

def save(t, template_id, preview_dir=None, views=None, light_regions=(), **kw):
    """Fills dark floors in light_regions to light 8 (each a region box, or (box, allowed cells)), then saves
    the template (and a preview sheet into preview_dir when given)."""
    for region in light_regions:
        if len(region) == 2:
            light_inside(t, region[0], region[1])
        else:
            mod.light_fill(t, region)
    png = None
    if preview_dir:
        png = os.path.join(preview_dir, template_id.replace('/', '__') + '.png')
    path = t.save(template_id, preview=png, **kw)
    print('%s: size %s, %d blocks, anchors %s' % (template_id, list(t.size), t.count(), ', '.join(sorted(t.anchors))))
    return path


def render(template_id, out, views='se,nw', plans='auto', cut=None, width=1600):
    """Renders a saved template's preview sheet with the structlib previewer."""
    from structlib import preview as P
    from structlib import nbtio
    data = nbtio.load(os.path.join(HERE, '..', '..', '..', '..', 'src', 'main', 'resources', 'data', 'oldrepublic',
                                   'structure', template_id + '.nbt'))
    kw = {}
    if cut:
        kw['cut'] = cut
    img = P.sheet(data, title=template_id, views=tuple(views.split(',')), plans=plans, width=width, **kw)
    img.convert('RGB').save(out)
    return out
