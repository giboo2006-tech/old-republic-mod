"""Shared pieces for O3's wilds structures (docs/MILESTONE_6.md 5.2, second half).

smuggler_cove, stilt_hamlet, poacher_dig, treaty_stone and sunken_freighter all build from here:
materials (natural rock, weathered timber), timber work (posts, decks, railings, ladders, huts, roofs),
props (crates, tables, bunks, lamps, boats), water handling (waterlogging, the water line) and the
writers for worldgen JSON and chest loot. Every random choice goes through a seeded rng or a Noise,
so a run writes byte-identical files.

Water structures (cove, hamlet) keep these rules (see each script's docstring):
  * layer W of the template is the water line: the top water block when the start lands on water
    (WORLD_SURFACE_WG counts water, so the first free layer above the sea is layer B = W + 1);
  * nothing below or at W is explicit air (air there would cut a pocket into the sea);
  * stilts and posts are logs down to layer 0 (a full block, so the pool element's support pass
    continues them to the bottom; a waterlogged fence would stop it);
  * waterloggable blocks the template puts in its own water are waterlogged here, and the structure
    uses apply_waterlogging so the ones that land in the world's water are too.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..'))

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta, worldgen as W, jigsaw  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402
from structlib.nature import Noise  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'oldrepublic')
WATER = 'minecraft:water'


# ---------------------------------------------------------------- command line

def preview_dir():
    """The folder to render previews into: --preview DIR on the command line (none by default)."""
    if '--preview' in sys.argv:
        i = sys.argv.index('--preview')
        if i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    return None


def only_names():
    """--only a,b limits a script to some of its templates (for quick iterations)."""
    if '--only' in sys.argv:
        i = sys.argv.index('--only')
        if i + 1 < len(sys.argv):
            return set(sys.argv[i + 1].split(','))
    return None


def save(t, template_id, views=None, cut=None, plans=None, ground=None):
    """Save a template and, with --preview, render its sheet into the preview folder (ground: a plane drawn
    at that layer, the water line of a shore structure)."""
    t.save(template_id)
    out = preview_dir()
    if out:
        from structlib import preview as P
        os.makedirs(out, exist_ok=True)
        kw = {}
        if views:
            kw['views'] = views
        if cut:
            kw['cut'] = cut
        if plans:
            kw['plans'] = plans
        if ground is not None:
            kw['ground'] = ground
        img = P.sheet(t.to_nbt(), title=template_id, **kw)
        img.convert('RGB').save(os.path.join(out, template_id.replace('/', '_') + '.png'))
    return t


# ---------------------------------------------------------------- states

_WATERLOGGABLE = ('_stairs', '_slab', '_fence', '_wall', '_pane', '_bars', '_chain', 'lantern', '_trapdoor', 'ladder',
                  'lightning_rod', 'scaffolding', 'chest', 'candle', 'sea_pickle', 'decorated_pot', '_grate',
                  'hanging_roots', 'glow_lichen', 'mangrove_roots', 'big_dripleaf', 'small_dripleaf', 'pointed_dripstone',
                  '_coral', '_coral_fan', '_coral_wall_fan', 'conduit', 'iron_bars', 'light', '_shelf', 'copper_bulb')
_NOT_WATERLOGGABLE = ('_fence_gate',)


def waterloggable(state):
    n = B.name_of(state).split(':', 1)[1]
    if any(n.endswith(s) for s in _NOT_WATERLOGGABLE):
        return False
    return any(n.endswith(s) or n == s.strip('_') for s in _WATERLOGGABLE)


def wet(state):
    """The state waterlogged, when the block can hold water (a double slab cannot)."""
    if state is None or not waterloggable(state):
        return state
    n, p = B.parse(state)
    if p.get('type') == 'double':
        return state
    p['waterlogged'] = 'true'
    return B.build(n, p)


def dry(state):
    """The state with waterlogged=false when it has the property (explicit, so it never inherits water)."""
    if state is None or not waterloggable(state):
        return state
    n, p = B.parse(state)
    p['waterlogged'] = 'false'
    return B.build(n, p)


def stairs(name, facing, half='bottom'):
    return S(name, facing=facing, half=half, shape='straight', waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def trapdoor(name, facing='north', half='bottom', open_=False):
    return S(name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def log(name, axis='y'):
    return S(name, axis=axis)


def lantern(hanging=False, soul=False):
    return S('soul_lantern' if soul else 'lantern', hanging=hanging, waterlogged=False)


def chain(axis='y'):
    return S('iron_chain', axis=axis, waterlogged=False)


def campfire(facing='north', lit=True, signal=False, soul=False):
    return S('soul_campfire' if soul else 'campfire', facing=facing, lit=lit, signal_fire=signal, waterlogged=False)


def candle(color=None, n=1, lit=True):
    name = (color + '_candle') if color else 'candle'
    return S(name, candles=n, lit=lit, waterlogged=False)


def door(name, x, y, z, t, facing='north', hinge='left', open_=False):
    """A two-high door at (x, y, z) facing `facing` (the side it opens toward when closed is its front)."""
    t.set(x, y, z, S(name, facing=facing, half='lower', hinge=hinge, open=open_, powered=False))
    t.set(x, y + 1, z, S(name, facing=facing, half='upper', hinge=hinge, open=open_, powered=False))


def bed(t, x, y, z, facing, color='brown'):
    """A bed whose head is at (x, y, z) and whose foot lies away from `facing` (the head's side)."""
    dx, dz = B.step(facing)
    t.set(x, y, z, S(color + '_bed', facing=facing, part='head', occupied=False), nbt={'id': 'minecraft:bed'})
    t.set(x - dx, y, z - dz, S(color + '_bed', facing=facing, part='foot', occupied=False), nbt={'id': 'minecraft:bed'})


# ---------------------------------------------------------------- materials

def wood(kind):
    """The parts of one vanilla wood: planks, slab, stairs, fence, gate, trapdoor, door, log, stripped log."""
    if kind == 'bamboo':
        return dict(planks='bamboo_planks', slab='bamboo_slab', stairs='bamboo_stairs', fence='bamboo_fence',
                    gate='bamboo_fence_gate', trapdoor='bamboo_trapdoor', door='bamboo_door', log='bamboo_block',
                    stripped='stripped_bamboo_block', wood='bamboo_block', mosaic='bamboo_mosaic',
                    shelf='bamboo_shelf', button='bamboo_button', plate='bamboo_pressure_plate')
    return dict(planks=kind + '_planks', slab=kind + '_slab', stairs=kind + '_stairs', fence=kind + '_fence',
                gate=kind + '_fence_gate', trapdoor=kind + '_trapdoor', door=kind + '_door', log=kind + '_log',
                stripped='stripped_' + kind + '_log', wood=kind + '_wood', shelf=kind + '_shelf',
                button=kind + '_button', plate=kind + '_pressure_plate')


class Rock:
    """Natural rock in coherent patches: stone, andesite, tuff and cobble, with calcite and diorite seams.
    wet_band is a layer range (y0, y1) where the rock turns mossy and dark (the splash zone)."""

    def __init__(self, seed, wet_band=None, kinds=None):
        self.n1 = Noise(seed, 5.0)
        self.n2 = Noise(seed + 11, 2.2)
        self.n3 = Noise(seed + 23, 9.0)
        self.wet_band = wet_band
        self.kinds = kinds or [('stone', 40), ('andesite', 22), ('tuff', 14), ('cobblestone', 8),
                               ('stone', 6), ('diorite', 5), ('calcite', 3), ('gravel', 2)]
        self.total = sum(w for _, w in self.kinds)

    def __call__(self, x, y, z):
        if self.wet_band and self.wet_band[0] <= y <= self.wet_band[1]:
            v = self.n2.at(x, y, z)
            if v < 0.33:
                return 'minecraft:mossy_cobblestone'
            if v < 0.55:
                return 'minecraft:tuff'
            if v < 0.7:
                return 'minecraft:cobblestone'
            if v < 0.8:
                return 'minecraft:moss_block'
        # horizontal-ish strata: the slow noise is stretched in y
        v = 0.7 * self.n1.at(x * 0.8, y * 2.2, z * 0.8) + 0.3 * self.n2.at(x, y, z)
        r = v * self.total
        for name, w in self.kinds:
            r -= w
            if r <= 0:
                return 'minecraft:' + name
        return 'minecraft:stone'


def mix(options, seed, scale=3.0):
    """A callable choosing from [(state, weight)] in noise patches (scale in cells)."""
    total = sum(w for _, w in options)
    n = Noise(seed, scale)
    j = Noise(seed + 5, 1.1)

    def f(x, y, z):
        r = (0.7 * n.at(x, y, z) + 0.3 * j.at(x, y, z)) * total
        for s, w in options:
            r -= w
            if r <= 0:
                return s
        return options[-1][0]
    return f


def pick(rng, options):
    """One choice from [(value, weight)] with the template's rng."""
    total = sum(w for _, w in options)
    r = rng.random() * total
    for v, w in options:
        r -= w
        if r <= 0:
            return v
    return options[-1][0]


# ---------------------------------------------------------------- timber work

def post(t, x, z, y_top, y_bottom, state):
    """A column of `state` (a log, usually) from y_bottom up to y_top."""
    for y in range(min(y_top, y_bottom), max(y_top, y_bottom) + 1):
        t.set(x, y, z, state)


def boards(t, x0, z0, x1, z1, y, kinds, rng, along='x', missing=0.0, sag=0.0, keep=None, wetline=None):
    """Deck boards over a rectangle at layer y: runs of 2 to 4 cells along `along` share one plank kind
    (kinds: [(planks state, weight)]), a few boards are missing (unset, so the water or ground shows) and a
    few sag (a top slab of the same wood). keep: cells never left missing (where posts meet the deck)."""
    xa, xb = sorted((x0, x1))
    za, zb = sorted((z0, z1))
    keep = keep or set()
    if along == 'x':
        lines = [[(x, z) for x in range(xa, xb + 1)] for z in range(za, zb + 1)]
    else:
        lines = [[(x, z) for z in range(za, zb + 1)] for x in range(xa, xb + 1)]
    for line in lines:
        i = 0
        while i < len(line):
            run = rng.randint(2, 4)
            kind = pick(rng, kinds)
            for (x, z) in line[i:i + run]:
                r = rng.random()
                if r < missing and (x, z) not in keep:
                    t.set(x, y, z, None)
                    continue
                if r < missing + sag and kind.endswith('_planks'):
                    st = slab(kind.replace('_planks', '_slab'), 'top')
                    t.set(x, y, z, wet(st) if wetline is not None and y <= wetline else st)
                else:
                    t.set(x, y, z, kind)
            i += run


def stilts(t, x0, z0, x1, z1, y_top, state, step=3, inner=True, bottom=0):
    """Posts under a floor: at the corners and every `step` cells along the edges (and inside, on the same
    grid, when inner), from layer `bottom` up to y_top (the floor layer itself takes the post's end)."""
    xa, xb = sorted((x0, x1))
    za, zb = sorted((z0, z1))
    xs = sorted(set(list(range(xa, xb + 1, step)) + [xb]))
    zs = sorted(set(list(range(za, zb + 1, step)) + [zb]))
    cells = []
    for x in xs:
        for z in zs:
            if inner or x in (xa, xb) or z in (za, zb):
                post(t, x, z, y_top, bottom, state)
                cells.append((x, z))
    return cells


def railing(t, cells, fence, y):
    for (x, z) in cells:
        t.set(x, y, z, S(fence, waterlogged=False))


def ladder(t, x, z, y0, y1, facing):
    """A ladder from y0 to y1 on the wall behind it (facing away from the wall)."""
    for y in range(min(y0, y1), max(y0, y1) + 1):
        t.set(x, y, z, S('ladder', facing=facing, waterlogged=False))


def hang_lamp(t, x, y, z, drop=1, soul=False):
    """A lantern hanging `drop` cells below the block at y + drop + 1 on a chain (drop 0: straight under it)."""
    for k in range(drop):
        t.set(x, y + 1 + k, z, chain('y'))
    t.set(x, y, z, lantern(hanging=True, soul=soul))


def lamp_post(t, x, y, z, height=3, post_state='minecraft:spruce_fence', soul=False):
    """A fence post `height` high from y with a lantern on top."""
    for k in range(height):
        t.set(x, y + k, z, S(B.name_of(post_state), waterlogged=False) if post_state.endswith('fence') else post_state)
    t.set(x, y + height, z, lantern(hanging=False, soul=soul))


# ---------------------------------------------------------------- props

CRATES = [('or:cargo_crate', 5), ('minecraft:barrel[facing=up,open=false]', 3), ('or:cargo_crate_republic', 1),
          ('minecraft:spruce_planks', 1)]


def crate_stack(t, x, y, z, rng, height=None, kinds=CRATES):
    """A stack of 1 to 3 crates and barrels (a barrel on its side now and then)."""
    h = height or rng.choice((1, 1, 2, 2, 3))
    for k in range(h):
        st = pick(rng, kinds)
        if 'barrel' in st and rng.random() < 0.3:
            st = S('barrel', facing=rng.choice(('north', 'east', 'south', 'west')), open=False)
        t.set(x, y + k, z, st)
    return h


def crate_pile(t, x0, z0, x1, z1, y, rng, density=0.7, max_h=3, kinds=CRATES):
    """A pile of crates over a rectangle, taller toward its middle."""
    xa, xb = sorted((x0, x1))
    za, zb = sorted((z0, z1))
    cx, cz = (xa + xb) / 2.0, (za + zb) / 2.0
    rx, rz = max(1.0, (xb - xa) / 2.0 + 0.5), max(1.0, (zb - za) / 2.0 + 0.5)
    for x in range(xa, xb + 1):
        for z in range(za, zb + 1):
            if rng.random() > density:
                continue
            d = math.sqrt(((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2)
            h = max(1, min(max_h, int(round((1.2 - d) * max_h + rng.uniform(-0.6, 0.6)))))
            crate_stack(t, x, y, z, rng, height=h, kinds=kinds)


def table(t, x, y, z, kind='slab', wood_kind='spruce', facing='north', on=None):
    """A one-cell table at (x, y, z): 'slab' a top slab, 'stairs' an upside-down stair (a table with a
    skirt, its back toward `facing`), 'post' a fence leg under a pressure plate (a high counter).
    on: a state set on the table top (a candle, a lantern, a pot), for 'slab' and 'stairs'."""
    w = wood(wood_kind)
    if kind == 'stairs':
        t.set(x, y, z, stairs(w['stairs'], facing, 'top'))
    elif kind == 'post':
        t.set(x, y, z, S(w['fence'], waterlogged=False))
        t.set(x, y + 1, z, S(w['plate'], powered=False))
        return
    else:
        t.set(x, y, z, slab(w['slab'], 'top'))
    if on:
        t.set(x, y + 1, z, on)


def stool(t, x, y, z, name='spruce_stairs', facing='north'):
    t.set(x, y, z, stairs(name, facing))


def shelf(t, x, y, z, name, facing):
    """A wooden shelf on a wall, facing out (only `facing` is set: the other properties keep their defaults)."""
    t.set(x, y, z, S(name, facing=facing))


# ---------------------------------------------------------------- huts

class Hut:
    """A timber hut: a plank floor at y0, log corner posts, plank walls with a top plate, a roof.

    door: (side, offset along the wall from its west/north end, width); windows: [(side, offset)] cut as
    shuttered openings; roof: 'gable' (ridge along the longer side), 'shed' (one slope), 'thatch' (a gable
    of hay and moss), 'canvas' (a gable of canvas slabs). Interior air is explicit. Returns the hut's
    interior box (x0, y, z0, x1, y1, z1) for furnishing and light."""

    def __init__(self, x0, y0, z0, w, d, h=4, walls='spruce', frame='stripped_spruce_log', floor=None, roof='gable',
                 roof_wood='spruce', door=('south', 1, 2), windows=(), rng=None, rot=0.0, burnt=0.0,
                 roof_damage=0.0, wall_mix=None):
        self.x0, self.y0, self.z0, self.w, self.d, self.h = x0, y0, z0, w, d, h
        self.x1, self.z1 = x0 + w - 1, z0 + d - 1
        self.walls, self.frame, self.floor = walls, frame, floor or walls
        self.roof, self.roof_wood = roof, roof_wood
        self.door, self.windows = door, windows
        self.rng, self.rot, self.burnt, self.roof_damage = rng, rot, burnt, roof_damage
        self.wall_mix = wall_mix

    def wall_state(self, x, y, z):
        if self.wall_mix:
            s = self.wall_mix(x, y, z)
        else:
            s = wood(self.walls)['planks']
        if self.burnt and self.rng.random() < self.burnt:
            s = self.rng.choice(('minecraft:dark_oak_planks', 'minecraft:blackstone', 'minecraft:coal_block')) \
                if self.rng.random() < 0.3 else 'minecraft:dark_oak_planks'
        return s

    def opening(self, side, off, width, y_from, height):
        x0, z0, x1, z1 = self.x0, self.z0, self.x1, self.z1
        cells = []
        for k in range(width):
            for dy in range(height):
                if side == 'south':
                    cells.append((x0 + off + k, y_from + dy, z1))
                elif side == 'north':
                    cells.append((x0 + off + k, y_from + dy, z0))
                elif side == 'west':
                    cells.append((x0, y_from + dy, z0 + off + k))
                else:
                    cells.append((x1, y_from + dy, z0 + off + k))
        return cells

    def build(self, t):
        x0, y0, z0, x1, z1, h = self.x0, self.y0, self.z0, self.x1, self.z1, self.h
        wd = wood(self.walls)
        # floor
        t.fill(x0, y0, z0, x1, y0, z1, wood(self.floor)['planks'])
        # walls
        for y in range(y0 + 1, y0 + h + 1):
            for x in range(x0, x1 + 1):
                for z in (z0, z1):
                    t.set(x, y, z, self.wall_state(x, y, z))
            for z in range(z0, z1 + 1):
                for x in (x0, x1):
                    t.set(x, y, z, self.wall_state(x, y, z))
        # top plate of stripped logs along the walls, corner posts
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                t.set(x, y0 + h, z, log(self.frame, 'x'))
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                t.set(x, y0 + h, z, log(self.frame, 'z'))
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            for y in range(y0, y0 + h + 1):
                t.set(x, y, z, log(self.frame, 'y'))
        # interior air
        t.air(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y0 + h, z1 - 1)
        # door
        side, off, width = self.door
        for c in self.opening(side, off, width, y0 + 1, 3 if h >= 4 else 2):
            t.set(*c, AIR)
        # windows: an opening with a trapdoor shutter swung open above it
        for (wside, woff) in self.windows:
            for c in self.opening(wside, woff, 1, y0 + 2, 1):
                t.set(*c, AIR)
        self.build_roof(t)
        return (x0 + 1, y0 + 1, z0 + 1, x1 - 1, y0 + h - 1, z1 - 1)

    def build_roof(self, t):
        x0, y0, z0, x1, z1, h = self.x0, self.y0, self.z0, self.x1, self.z1, self.h
        rw = wood(self.roof_wood)
        top = y0 + h
        rng = self.rng
        if self.roof in ('gable', 'thatch', 'canvas'):
            along_x = (x1 - x0) >= (z1 - z0)
            if self.roof == 'gable':
                st_name, body, sl = rw['stairs'], rw['planks'], rw['slab']
            elif self.roof == 'thatch':
                st_name, body, sl = None, 'minecraft:hay_block', 'minecraft:moss_carpet'
            else:
                st_name, body, sl = 'oldrepublic:canvas_stairs', 'oldrepublic:canvas', 'oldrepublic:canvas_slab'
            if along_x:
                za, zb = z0 - 1, z1 + 1
                i = 0
                while za + i <= zb - i:
                    yy = top + 1 + i
                    for x in range(x0 - 1, x1 + 2):
                        for z in (za + i, zb - i):
                            if self.roof_damage and rng.random() < self.roof_damage * (0.5 + i * 0.2):
                                continue
                            if za + i == zb - i:
                                t.set(x, yy, z, slab(sl) if sl.endswith('_slab') else sl)
                            elif st_name:
                                t.set(x, yy, z, stairs(st_name, 'south' if z == za + i else 'north'))
                            else:
                                t.set(x, yy, z, 'minecraft:hay_block[axis=x]')
                    # gable ends
                    if zb - i - (za + i) > 1:
                        for x in (x0, x1):
                            for z in range(za + i + 1, zb - i):
                                if z0 <= z <= z1:
                                    t.set(x, yy, z, body if self.roof != 'canvas' else rw['planks'])
                        for x in range(x0 + 1, x1):
                            for z in range(za + i + 1, zb - i):
                                if z0 < z < z1:
                                    t.set(x, yy, z, AIR)
                    i += 1
            else:
                xa, xb = x0 - 1, x1 + 1
                i = 0
                while xa + i <= xb - i:
                    yy = top + 1 + i
                    for z in range(z0 - 1, z1 + 2):
                        for x in (xa + i, xb - i):
                            if self.roof_damage and rng.random() < self.roof_damage * (0.5 + i * 0.2):
                                continue
                            if xa + i == xb - i:
                                t.set(x, yy, z, slab(sl) if sl.endswith('_slab') else sl)
                            elif st_name:
                                t.set(x, yy, z, stairs(st_name, 'east' if x == xa + i else 'west'))
                            else:
                                t.set(x, yy, z, 'minecraft:hay_block[axis=z]')
                    if xb - i - (xa + i) > 1:
                        for z in (z0, z1):
                            for x in range(xa + i + 1, xb - i):
                                if x0 <= x <= x1:
                                    t.set(x, yy, z, body if self.roof != 'canvas' else rw['planks'])
                        for z in range(z0 + 1, z1):
                            for x in range(xa + i + 1, xb - i):
                                if x0 < x < x1:
                                    t.set(x, yy, z, AIR)
                    i += 1
        elif self.roof == 'shed':
            # one slope falling to the south: stairs rows climbing north
            n = z1 - z0 + 3
            for i in range(n):
                z = z1 + 1 - i
                yy = top + 1 + i // 2
                for x in range(x0 - 1, x1 + 2):
                    if self.roof_damage and rng.random() < self.roof_damage:
                        continue
                    if i % 2 == 0:
                        t.set(x, yy, z, stairs(rw['stairs'], 'north'))
                    else:
                        t.set(x, yy, z, slab(rw['slab'], 'bottom'))
                if x0 <= x1:
                    for x in (x0, x1):
                        for y in range(top + 1, yy):
                            if z0 <= z <= z1:
                                t.set(x, y, z, rw['planks'])
        elif self.roof == 'flat':
            for x in range(x0 - 1, x1 + 2):
                for z in range(z0 - 1, z1 + 2):
                    if self.roof_damage and rng.random() < self.roof_damage:
                        continue
                    t.set(x, top + 1, z, slab(rw['slab'], 'bottom'))


# ---------------------------------------------------------------- boats

def skiff(t, x, y, z, facing, length=7, wood_kind='spruce', water=False, rng=None, cargo=()):
    """A small open boat, 3 wide, `length` long, its bow toward `facing`, with its gunwales at layer y
    (the hull bottom is at y - 1). water: the boat floats in the template's own water (hull blocks below the
    water line are waterlogged and the inside of the hull is air above it). cargo: states set in the hull."""
    wd = wood(wood_kind)
    fx, fz = B.step(facing)
    rx, rz = B.step(B.right_of(facing))

    def at(a, s):  # a: along (0 at stern), s: side -1..1
        return (x + fx * a + rx * s, z + fz * a + rz * s)

    def put(px, py, pz, st):
        t.set(px, py, pz, wet(st) if water else dry(st))
    for a in range(length):
        bow = a == length - 1
        stern = a == 0
        for s in (-1, 0, 1):
            px, pz = at(a, s)
            if bow and s != 0:
                continue
            if s == 0:
                put(px, y - 1, pz, slab(wd['slab'], 'top'))  # keel and bottom boards
                if bow:
                    put(px, y, pz, stairs(wd['stairs'], B.opposite(facing)))
                elif stern:
                    put(px, y, pz, trapdoor(wd['trapdoor'], B.opposite(facing), 'top', False))
                else:
                    t.set(px, y, pz, AIR if not water else AIR)
            else:
                side = B.right_of(facing) if s == 1 else B.left_of(facing)
                put(px, y - 1, pz, stairs(wd['stairs'], B.opposite(side), 'top'))
                put(px, y, pz, trapdoor(wd['trapdoor'], B.opposite(side), 'bottom', True))
    # a thwart (seat) and cargo
    mid = length // 2
    px, pz = at(mid, 0)
    t.set(px, y, pz, slab(wd['slab'], 'bottom'))
    for i, st in enumerate(cargo):
        if st:
            px, pz = at(1 + i, 0)
            t.set(px, y, pz, st)


# ---------------------------------------------------------------- rock masses

def blob_heights(sx, sz, cx, cz, radii, base, peak, powers, seed, wobble=0.12, bumps=2.0):
    """A rock mass as {(x, z): top layer}: a noisy rounded outline around (cx, cz) whose top rises from
    `base` at the edge to `peak` in the middle. radii and powers are (west, east, north, south): a high
    power keeps the top flat and drops it steeply at the edge (a cliff), a low one makes a gentle slope."""
    n1 = Noise(seed, 7.0)
    n2 = Noise(seed + 3, 3.0)
    out = {}
    for x in range(sx):
        for z in range(sz):
            dx = (x - cx) / float(radii[0] if x < cx else radii[1])
            dz = (z - cz) / float(radii[2] if z < cz else radii[3])
            r = math.sqrt(dx * dx + dz * dz) + wobble * (n1.at(x, 0, z) - 0.5) * 2
            if r >= 1.0:
                continue
            # the power of the side the cell leans toward
            wx, wz = abs(dx), abs(dz)
            px = powers[0] if x < cx else powers[1]
            pz = powers[2] if z < cz else powers[3]
            pw = (px * wx + pz * wz) / (wx + wz) if wx + wz > 0 else (px + pz) / 2.0
            h = base + (peak - base) * (1.0 - max(0.0, r) ** pw) + bumps * (n2.at(x, 0, z) - 0.5)
            out[(x, z)] = int(math.floor(h))
    return out


def rock_columns(t, heights, rock, top=None, bottom=0):
    """Rock from `bottom` up to each column's top layer; top(x, y, z) may give the top block (None keeps rock)."""
    for (x, z), h in sorted(heights.items()):
        for y in range(bottom, h + 1):
            t.set(x, y, z, rock(x, y, z))
        if top is not None and h >= bottom:
            s = top(x, h, z)
            if s:
                t.set(x, h, z, s)


def stair_path(t, cells, stair, block, clear=3):
    """Steps along a path: cells [(x, y, z)] give the tread block layer of each cell in order. A cell one
    higher than the one before it gets a stair (`stair` name) facing up the path; every other cell gets
    `block`; `clear` cells of air are left above each tread."""
    prev = None
    for i, (x, y, z) in enumerate(cells):
        if prev is not None and y == prev[1] + 1:
            dx, dz = x - prev[0], z - prev[2]
            facing = 'east' if dx > 0 else 'west' if dx < 0 else 'south' if dz > 0 else 'north'
            t.set(x, y, z, stairs(stair, facing))
            t.set(x, y - 1, z, block)
        else:
            t.set(x, y, z, block)
        for k in range(1, clear + 1):
            t.set(x, y + k, z, AIR)
        prev = (x, y, z)


# ---------------------------------------------------------------- ground and nature

def scatter_on(t, cells, choices, density, rng):
    """Small things on top of given ground cells [(x, y, z)] (y is the ground block)."""
    total = sum(w for _, w in choices)
    for (x, y, z) in cells:
        if rng.random() >= density or not t.inside(x, y + 1, z):
            continue
        if t.get(x, y + 1, z) not in (None, AIR):
            continue
        r = rng.random() * total
        for st, w in choices:
            r -= w
            if r <= 0:
                t.set(x, y + 1, z, st)
                if 'half=lower' in st and t.inside(x, y + 2, z) and t.get(x, y + 2, z) in (None, AIR):
                    t.set(x, y + 2, z, st.replace('half=lower', 'half=upper'))
                break


def surface_cells(t, x0, z0, x1, z1, names=None):
    """The top set, solid, full cell of every column in the rectangle (with open air or nothing above)."""
    out = []
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(t.sy - 2, -1, -1):
                s = t.get(x, y, z)
                if s is None or B.name_of(s) in ('minecraft:air', 'minecraft:cave_air'):
                    continue
                if names is not None and B.name_of(s) not in names:
                    break
                if t.is_solid(x, y, z) and nature._full_block(B.name_of(s)):
                    out.append((x, y, z))
                break
    return out


# ---------------------------------------------------------------- checks

def no_air_below(t, w_layer):
    """Explicit air cells at or below the water line (they would cut pockets into the sea)."""
    bad = []
    for (x, y, z), s in t.cells.items():
        if y <= w_layer and B.name_of(s) in ('minecraft:air', 'minecraft:cave_air'):
            bad.append((x, y, z))
    return sorted(bad)


def report(t, name):
    n = t.count()
    print('%-34s %3dx%3dx%3d  %6d blocks  %d anchors' % (name, t.sx, t.sy, t.sz, n, sum(len(v) for v in t.anchors.values())))


# ---------------------------------------------------------------- worldgen and loot

def write_worldgen(name, variants, buried, spacing, separation, salt, biomes, adapted, liquid='ignore_waterlogging',
                   max_distance=64, step='surface_structures', heightmap='WORLD_SURFACE_WG', processors=None):
    """The pool, structure, set (4 chunks clear of the ruins) and biome tag of wilds/<name>.
    variants: [(template name under wilds/<name>/, weight[, adapted overrides])]; all start variants share one
    buried value (the structure has one start height)."""
    sid = 'wilds/' + name
    els = []
    for entry in variants:
        v, wgt = entry[0], entry[1]
        ad = dict(adapted)
        if len(entry) > 2:
            ad.update(entry[2])        # per-variant settings (clear_from follows each template's height)
        els.append(W.element(sid + '/' + v, processors=processors or 'minecraft:empty', adapted=ad, weight=wgt))
    W.pool(sid + '/start', els)
    W.structure(sid, start_pool=sid + '/start', biome_tag='has_structure/' + sid, size=1, max_distance=max_distance,
                step=step, start_height=1 - buried, heightmap=heightmap, liquid_settings=liquid)
    W.structure_set(sid, [(sid, 1)], spacing=spacing, separation=separation, salt=salt, exclusion=('ruins', 4))
    W.biome_tag('has_structure/' + sid, biomes)


def count(lo, hi):
    return {'type': 'minecraft:uniform', 'min': float(lo), 'max': float(hi)}


def item(name, weight=None, lo=None, hi=None):
    e = {'type': 'minecraft:item', 'name': name if ':' in name else 'minecraft:' + name}
    if lo is not None:
        e['functions'] = [{'function': 'minecraft:set_count', 'add': False, 'count': count(lo, hi)}]
    if weight is not None:
        e['weight'] = weight
    return e


def empty(weight):
    return {'type': 'minecraft:empty', 'weight': weight}


def loot_table(path, pools):
    """Writes data/oldrepublic/loot_table/<path>.json. pools: [(rolls, [entries])], rolls a number or (lo, hi)."""
    out = []
    for rolls, entries in pools:
        r = count(*rolls) if isinstance(rolls, tuple) else float(rolls)
        out.append({'entries': entries, 'rolls': r})
    data = {'pools': out, 'random_sequence': 'oldrepublic:' + path, 'type': 'minecraft:chest'}
    p = os.path.join(DATA, 'loot_table', path + '.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    text = json.dumps(data, indent=2, sort_keys=True) + '\n'
    if not (os.path.exists(p) and open(p).read() == text):
        with open(p, 'w') as f:
            f.write(text)
    return p
