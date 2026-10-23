"""Shared helpers of the QS story sites (docs/MILESTONE_6.md 3.5 and 5.3).

Every script in this folder builds one family of story/** templates with structlib and writes the
template, its site_anchors sidecar and its site meta. Everything is deterministic: randomness comes
from the template's seeded t.rng or from Noise(seed), so a rebuild writes byte-identical files.

Conventions used by every builder here:
  * x east, y up, z south; the entrance is the north side (z = 0).
  * `buried` B: layer B - 1 is the ground the player walks on, layer B is the feet layer.
  * Ground cells that are plain natural ground are left unset, so the biome's own top block (grass,
    sand, snow) shows; only worn ground (paths, paving, trampled earth) is written.
  * Feet anchors are made with spot(): air at the feet and the head over a block the template sets.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(HERE, '..', '..'))

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402
from structlib.nature import Noise  # noqa: E402

# ---------------------------------------------------------------- block states

LANTERN = S('lantern', hanging=False, waterlogged=False)
LANTERN_HUNG = S('lantern', hanging=True, waterlogged=False)
SOUL_LANTERN = S('soul_lantern', hanging=False, waterlogged=False)
SOUL_LANTERN_HUNG = S('soul_lantern', hanging=True, waterlogged=False)
CHAIN_Y = S('iron_chain', axis='y', waterlogged=False)


def campfire(lit=True, signal=False, facing='north', soul=False):
    return S('soul_campfire' if soul else 'campfire', facing=facing, lit=lit, signal_fire=signal, waterlogged=False)


def stairs(name, facing, half='bottom'):
    return S(name, facing=facing, half=half, shape='straight', waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def log(name, axis='y'):
    return S(name, axis=axis)


def trapdoor(name, facing, half='bottom', open_=False):
    return S(name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def gate(name, facing, open_=False):
    return S(name, facing=facing, open=open_, powered=False, in_wall=False)


def fence(name):
    return S(name, north=False, east=False, south=False, west=False, waterlogged=False)


def wall(name):
    return S(name, north='none', east='none', south='none', west='none', up=True, waterlogged=False)


def pane(name):
    return S(name, north=False, east=False, south=False, west=False, waterlogged=False)


def candle(color=None, n=1, lit=False):
    return S('%s_candle' % color if color else 'candle', candles=n, lit=lit, waterlogged=False)


def carpet(color):
    return S('%s_carpet' % color)


def barrel(facing='up', open_=False):
    return S('barrel', facing=facing, open=open_)


def leaves(name='oak_leaves'):
    return S(name, distance=1, persistent=True, waterlogged=False)


def bed(t, x, y, z, color, facing):
    """A bed whose foot is at (x, y, z) and whose head lies one cell toward `facing`."""
    dx, dz = B.step(facing)
    t.set(x, y, z, S('%s_bed' % color, facing=facing, part='foot', occupied=False))
    t.set(x + dx, y, z + dz, S('%s_bed' % color, facing=facing, part='head', occupied=False))


def column(t, x, y0, y1, z, state):
    for y in range(min(y0, y1), max(y0, y1) + 1):
        t.set(x, y, z, state(x, y, z) if callable(state) else state)


def pick(rng, options):
    """A weighted choice from [(value, weight), ...]."""
    total = sum(w for _, w in options)
    r = rng.random() * total
    for v, w in options:
        r -= w
        if r <= 0:
            return v
    return options[-1][0]


def by_noise(noise, options, x, y, z):
    """Chooses from [(state, weight), ...] by a noise value at the cell, so variants come in patches."""
    total = sum(w for _, w in options)
    r = noise.at(x, y, z) * total
    for v, w in options:
        r -= w
        if r <= 0:
            return v
    return options[-1][0]


def spot(t, name, x, y, z):
    """A feet anchor: air at the feet and the head; the cell below must be a block the template sets."""
    below = t.get(x, y - 1, z)
    if below is None or not t.is_solid(x, y - 1, z) or mod.walk_through(below):
        raise ValueError('feet anchor %s at %r has no block below (%r)' % (name, (x, y, z), below))
    mod.spot(t, name, x, y, z)


# ---------------------------------------------------------------- ground

PLANTS_TEMPERATE = [(S('short_grass'), 8), (S('fern'), 2), (S('bush'), 1), (S('dandelion'), 1), (S('poppy'), 1),
                    (S('azure_bluet'), 1), (S('oxeye_daisy'), 1)]
PLANTS_DRY = [(S('short_dry_grass'), 4), (S('dead_bush'), 1), (S('short_grass'), 2)]


def plants_on(t, cells, rng, density, options=PLANTS_TEMPERATE, on=('minecraft:grass_block', 'minecraft:moss_block',
                                                                       'minecraft:podzol', 'minecraft:rooted_dirt')):
    """Puts small plants on top of the given ground cells (only on the blocks in `on`, which the
    template itself sets, so a plant never stands on sand it cannot live on), where the cell above is
    free (unset or air)."""
    names = {B.full_name(n) for n in on}
    for (x, y, z) in cells:
        if t.name_at(x, y, z) not in names or not t.is_air(x, y + 1, z) or not t.inside(x, y + 1, z):
            continue
        if rng.random() < density:
            t.set(x, y + 1, z, pick(rng, options))


# ---------------------------------------------------------------- overlays (the encampments)

# What RuntimeSites.adaptTerrain would clear above the ground: plants and leaves, and natural ground
# blocks (#oldrepublic:story_ground) that are not also #oldrepublic:built_blocks.
STORY_GROUND = {'minecraft:' + n for n in (
    'grass_block', 'dirt', 'coarse_dirt', 'podzol', 'rooted_dirt', 'sand', 'red_sand', 'gravel', 'stone', 'granite',
    'diorite', 'andesite', 'sandstone', 'red_sandstone', 'snow_block', 'snow', 'terracotta', 'moss_block', 'clay',
    'tuff', 'smooth_basalt', 'basalt', 'smooth_sandstone', 'mud', 'packed_mud', 'muddy_mangrove_roots', 'calcite')}
_PLANTISH = ('grass', 'fern', 'bush', 'flower', 'dandelion', 'poppy', 'orchid', 'allium', 'bluet', 'tulip', 'daisy',
             'cornflower', 'lily_of_the_valley', 'rose', 'lilac', 'peony', 'sunflower', 'vine', 'lichen', 'roots',
             'leaf_litter', 'wildflowers', 'petals', 'leaves', 'mushroom', 'sapling', 'dripleaf')


def clutter(state):
    """True when the game's terrain fitting would clear this block above a site's ground."""
    n = B.name_of(state)
    if n in STORY_GROUND:
        return True
    path = n.split(':', 1)[1]
    if path.endswith('_carpet'):
        return False
    return any(k in path for k in _PLANTISH)


def overlay(base, full, ground_y, keep_cells=()):
    """The cells of `full` that differ from `base`, as a template of the same size (unset elsewhere,
    so the base shows through when the overlay is stamped over it with the same origin and rotation).

    Cells of `base` that `full` no longer has become explicit air. Base cells above `ground_y` that the
    terrain fitting would clear (plants, leaves, natural ground) are written again too, so the result
    is right whether or not the stamp fits the terrain first. keep_cells are always written."""
    out = Template(*full.size, seed=full.seed, name=full.name)
    cells = set(full.cells) | set(base.cells)
    for p in sorted(cells, key=lambda q: (q[1], q[2], q[0])):
        a, b = base.cells.get(p), full.cells.get(p)
        na, nb = base.nbt.get(p), full.nbt.get(p)
        if b is None:
            out.set(*p, AIR)
            continue
        if a != b or na != nb or p in keep_cells or (p[1] > ground_y and clutter(b)):
            out.set(*p, b, nbt=dict(nb) if nb is not None else None)
    for name, cs in full.anchors.items():
        for c in cs:
            out.anchor(name, *c)
    return out


# ---------------------------------------------------------------- checks

def check(t, size, feet=(), blocks=None, air=(), cells=None):
    """Raises when the template breaks a rule the game relies on.

    feet: anchor names that must be feet anchors (explicit air, air above, a set solid block below).
    blocks: {anchor: block id} that must hold that block.
    air: cells that must be explicit air. cells: {(x, y, z): block id} that must hold that block."""
    if tuple(t.size) != tuple(size):
        raise SystemExit('%s: size %r, expected %r' % (t.name, t.size, size))
    for name in feet:
        if name not in t.anchors:
            raise SystemExit('%s: anchor %s missing' % (t.name, name))
        for (x, y, z) in t.anchors[name]:
            if t.get(x, y, z) != AIR or t.get(x, y + 1, z) != AIR:
                raise SystemExit('%s: feet anchor %s at %r is not two cells of air' % (t.name, name, (x, y, z)))
            below = t.get(x, y - 1, z)
            if below is None or mod.walk_through(below) or not t.is_solid(x, y - 1, z):
                raise SystemExit('%s: feet anchor %s at %r has no block below' % (t.name, name, (x, y, z)))
    for name, block in (blocks or {}).items():
        if name not in t.anchors:
            raise SystemExit('%s: anchor %s missing' % (t.name, name))
        for (x, y, z) in t.anchors[name]:
            if t.name_at(x, y, z) != B.full_name(block):
                raise SystemExit('%s: anchor %s at %r holds %s, not %s' % (t.name, name, (x, y, z),
                                                                         t.get(x, y, z), block))
    for p in air:
        if t.get(*p) != AIR:
            raise SystemExit('%s: %r must be explicit air, is %r' % (t.name, p, t.get(*p)))
    for p, block in (cells or {}).items():
        if t.name_at(*p) != B.full_name(block):
            raise SystemExit('%s: %r must be %s, is %r' % (t.name, p, block, t.get(*p)))


def dark_floors(t, region, level=8):
    """Floor cells in region (x0, y0, z0, x1, y1, z1) whose block light is under `level`."""
    lit = mod.light_map(t)
    return [p for p in mod.floor_cells(t, region) if lit.get(p, 0) < level]


# ---------------------------------------------------------------- output

def preview_dir(argv):
    """The folder named after --preview on the command line, or None."""
    if '--preview' in argv:
        i = argv.index('--preview')
        if i + 1 < len(argv):
            return argv[i + 1]
    return None


def render(t, path, finish=True, **kw):
    """Renders a preview sheet of a Template (finished first, unless finish is False) to path."""
    from structlib import preview as P
    if finish:
        t.finish()
    img = P.sheet(t.to_nbt(), title=kw.pop('title', t.name or ''), **kw)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    img.convert('RGB').save(path)
    return path


def write(t, tid, prev=None, meta_fields=None, finish=True, **render_kw):
    """Saves the template (and its sidecar), writes its site meta and, with prev, a preview sheet.
    finish False keeps the connection states as they are (an overlay finished in its full context)."""
    t.name = tid
    t.save(tid, finish=finish)
    if meta_fields is not None:
        meta.write(tid, **meta_fields)
    if prev:
        render(t, os.path.join(prev, tid.replace('/', '_') + '.png'), finish=finish, **render_kw)
    solid = t.count()
    print('%s: size %s, %d blocks, anchors %s' % (tid, 'x'.join(str(v) for v in t.size), solid,
                                                ', '.join('%s%s' % (k, v[0]) for k, v in sorted(t.anchors.items()))))
    return t
