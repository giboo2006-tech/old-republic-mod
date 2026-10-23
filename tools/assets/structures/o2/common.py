"""O2's shared helpers (the outposts and the first half of the overworld wilds).

Every generator in this folder imports this module first: it puts tools/assets on the path so the
`structlib` imports work, and it holds what several of O2's structures share: the terrain fit
(ground plinths and banked carves), furniture, lights, the loot table writer and the writer for a
wilds structure's worldgen family. Nothing here is random except through a Template's own seeded
rng or a Noise(seed), so every build is byte-identical.
"""
import argparse
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.abspath(os.path.join(HERE, '..', '..'))
if ASSETS not in sys.path:
    sys.path.insert(0, ASSETS)

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta, worldgen as W, jigsaw  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402
from structlib.nature import Noise  # noqa: E402

REPO = os.path.abspath(os.path.join(ASSETS, '..', '..'))
DATA = os.path.join(REPO, 'src', 'main', 'resources', 'data', 'oldrepublic')
LOOT = os.path.join(DATA, 'loot_table')

HORIZONTAL = ('north', 'east', 'south', 'west')


# ---------------------------------------------------------------- command line and saving

def cli(doc):
    ap = argparse.ArgumentParser(description=doc)
    ap.add_argument('--preview', metavar='DIR', help='render a preview sheet of every template into DIR')
    return ap.parse_args()


def save(t, template_id, preview_dir=None, views=('se', 'nw'), plans='auto', cut=None, width=1600, suffix=''):
    """Writes the template (finish, nbt, sidecar) and, with preview_dir, its preview sheet."""
    path = t.save(template_id)
    if preview_dir:
        from structlib import preview as P
        img = P.sheet(t.to_nbt(), title=template_id, views=views, plans=plans, cut=cut, width=width)
        os.makedirs(preview_dir, exist_ok=True)
        out = os.path.join(preview_dir, template_id.replace('/', '_') + suffix + '.png')
        img.convert('RGB').save(out)
        print('preview', out)
    return path


def closeup(t, box, out, views=('se', 'nw'), width=1600, cut=None):
    """Renders the part of the template inside box (x0, y0, z0, x1, y1, z1) larger, to check details."""
    from structlib import preview as P
    x0, y0, z0, x1, y1, z1 = box
    g = P.Grid.from_template(t.to_nbt())
    cells = {(x - x0, y - y0, z - z0): v for (x, y, z), v in g.cells.items()
             if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1}
    sub = P.Grid((x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1), cells)
    q = {'se': 0, 'sw': 1, 'nw': 2, 'ne': 3}
    each = width // max(1, len(views))
    imgs = [P.render_iso(sub, q[v], max_width=each - 20, cut=cut, label='%s %s view %s' % (t.name, box, v.upper()))
            for v in views]
    img = P._stack(imgs)
    img.convert('RGB').save(out)
    print('closeup', out)


# ---------------------------------------------------------------- small cell helpers

def put(t, x, y, z, state, only_empty=False):
    if state is None:
        return
    if only_empty and not t.is_air(x, y, z):
        return
    s = state(x, y, z) if callable(state) else state
    t.set(x, y, z, s)


def col(t, x, z, y0, y1, state, only_empty=False):
    for y in range(min(y0, y1), max(y0, y1) + 1):
        put(t, x, y, z, state, only_empty)


def box(t, x0, y0, z0, x1, y1, z1, state, only_empty=False):
    return t.fill(x0, y0, z0, x1, y1, z1, state, only_empty=only_empty)


def unset(t, x, y, z):
    return (x, y, z) not in t.cells


def pick(rng, opts):
    """One state from [(state, weight)] or a plain state."""
    if isinstance(opts, str):
        return opts
    total = sum(w for _, w in opts)
    r = rng.random() * total
    for s, w in opts:
        r -= w
        if r <= 0:
            return s
    return opts[-1][0]


def picker(rng, opts):
    return lambda x, y, z: pick(rng, opts)


def patches(seed, opts, scale=4.0):
    """A callable choosing from [(state, weight)] by coherent noise (variants clump in patches)."""
    return palettes.Palette(r=opts).patches('r', seed=seed, scale=scale)


def stairs(name, facing, half='bottom'):
    return S(name, facing=facing, half=half, shape='straight', waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def trapdoor(name, facing, half='bottom', open_=False):
    return S(name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def lantern(hanging=False, soul=False):
    return S('soul_lantern' if soul else 'lantern', hanging=hanging, waterlogged=False)


def chain_y():
    return S('iron_chain', axis='y', waterlogged=False)


def bars():
    return S('iron_bars', waterlogged=False)


def beam(axis='y'):
    return S('or:durasteel_beam', axis=axis)


def pipes(axis='y'):
    return S('or:durasteel_pipes', axis=axis)


def rod(facing='up'):
    return S('lightning_rod', facing=facing, powered=False, waterlogged=False)


def end_rod(facing='up'):
    return S('end_rod', facing=facing)


def campfire(lit=True, facing='north', signal=False):
    return S('campfire', facing=facing, lit=lit, signal_fire=signal, waterlogged=False)


def barrel(facing='up'):
    return S('barrel', facing=facing, open=False)


def bed(t, x, y, z, facing, color='white'):
    """A bed whose foot is at (x, z) and head one cell toward `facing`."""
    dx, dz = B.step(facing)
    t.set(x, y, z, S('%s_bed' % color, facing=facing, occupied=False, part='foot'))
    t.set(x + dx, y, z + dz, S('%s_bed' % color, facing=facing, occupied=False, part='head'))


def wall_banner(t, x, y, z, facing, base, patterns):
    mod.banner(t, x, y, z, facing, base, patterns)


def standing_banner(t, x, y, z, facing, base, patterns):
    mod.banner(t, x, y, z, facing, base, patterns, wall=False)


# ---------------------------------------------------------------- terrain fit

def ring_of(t, x, z):
    """How many cells (x, z) is in from the template's outer edge (0 on the edge)."""
    return min(x, z, t.sx - 1 - x, t.sz - 1 - z)


def carve(t, x0, z0, x1, z1, y0, top, bank=1, noise=None):
    """Explicit air from y0 up to `top` over the rectangle, only in cells nothing else has set, so a
    hillside inside the footprint is cut away. With bank > 0 the cut is lower toward the template's
    outer edge (bank layers more per ring inward), so higher ground outside is cut back in steps
    instead of a sheer box; noise (a Noise) roughens the steps a little."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            h = top
            if bank:
                h = min(top, y0 + ring_of(t, x, z) * bank + (1 if noise is not None and noise.at(x, 0, z) > 0.6 else 0))
            for y in range(y0, h + 1):
                if unset(t, x, y, z):
                    t.set(x, y, z, AIR)


def plinth(t, x0, z0, x1, z1, y_top, state, depth, edge_noise=None, edge=0.0):
    """Ground cells from y_top down `depth` layers (the template's own ground under a site that
    sits a few layers into the terrain). edge_noise and edge thin the outer ring so the plinth's
    border is ragged."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if edge_noise is not None and edge > 0:
                r = min(x - min(x0, x1), z - min(z0, z1), max(x0, x1) - x, max(z0, z1) - z)
                if r == 0 and edge_noise.at(x, 0, z) < edge:
                    continue
            for y in range(max(0, y_top - depth + 1), y_top + 1):
                put(t, x, y, z, state, only_empty=True)


# ---------------------------------------------------------------- furniture and fittings

def lamp_post(t, x, y, z, height=3, post=None, head=None, cap=None):
    """A post `height` cells tall standing on y with a light head on top and an optional cap."""
    post = post or beam('y')
    head = head or S('or:light_panel')
    col(t, x, z, y, y + height - 2, post)
    put(t, x, y + height - 1, z, head)
    if cap:
        put(t, x, y + height, z, cap)


def hanging_lamp(t, x, y_top, z, length=1, soul=False):
    """A lantern hanging under the ceiling cell y_top + 1, on `length - 1` cells of chain."""
    for i in range(length - 1):
        t.set(x, y_top - i, z, chain_y())
    t.set(x, y_top - length + 1, z, lantern(hanging=True, soul=soul))


def table(t, x, y, z, top=None, leg=None):
    """A one-cell table: a top-half slab, with a post below it when leg is given (the slab top sits at
    the height of a full block, chairs are stairs beside it)."""
    put(t, x, y, z, top or slab('smooth_stone_slab', 'top'))


def chair(t, x, y, z, back, wood='spruce'):
    """A stair as a seat whose back is toward `back` (the sitter faces the other way)."""
    t.set(x, y, z, stairs('%s_stairs' % wood if ':' not in wood and not wood.endswith('_stairs') else wood, back))


def crate_pile(t, rng, x0, z0, x1, z1, y, opts, max_h=3, density=0.85, taper=True):
    """A pile of crates over a rectangle: taller in the middle, never two piles exactly alike."""
    cx, cz = (x0 + x1) / 2.0, (z0 + z1) / 2.0
    rx, rz = max(0.5, (x1 - x0) / 2.0 + 0.5), max(0.5, (z1 - z0) / 2.0 + 0.5)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if rng.random() > density:
                continue
            d = max(abs(x - cx) / rx, abs(z - cz) / rz) if taper else 0
            h = max(1, int(round(max_h * (1.0 - 0.55 * d) + rng.uniform(-0.7, 0.4))))
            for k in range(min(h, max_h)):
                put(t, x, y + k, z, pick(rng, opts))


def bunk(t, x, y, z, facing, color='white', upper=True, frame=None):
    """A bunk: a bed with its foot at (x, z) and head toward `facing`, and a second bed one layer up."""
    bed(t, x, y, z, facing, color)
    if upper:
        bed(t, x, y + 1, z, facing, color)


def locker(t, x, y, z, facing, body=None, height=2):
    """A locker standing against a wall: a column of plating with iron trapdoor doors on its open face."""
    body = body or S('or:durasteel_plating')
    col(t, x, z, y, y + height - 1, body)
    dx, dz = B.step(facing)
    for k in range(height):
        put(t, x + dx, y + k, z + dz, trapdoor('iron_trapdoor', facing, 'bottom' if k == 0 else 'top', True),
            only_empty=True)


def weapon_rack(t, x, y, z, along, n=3, base=None):
    """A rack of rifles: lightning rods standing on a slab bench, `n` cells along `along` ('x' or 'z')."""
    base = base or slab('or:durasteel_plating_slab', 'bottom')
    for i in range(n):
        px, pz = (x + i, z) if along == 'x' else (x, z + i)
        put(t, px, y, pz, base)
        put(t, px, y + 1, pz, rod('up'))


def light_rooms(t, regions, level=8):
    """light_fill over each interior region (x0, y0, z0, x1, y1, z1); returns the cells it lit."""
    lit = []
    for r in regions:
        lit += mod.light_fill(t, r, level=level)
    return lit


def scatter_on(t, rng, cells, choices, density):
    """Small things on top of the given ground cells (x, y, z) when the cell above is free air."""
    total = sum(w for _, w in choices)
    for (x, y, z) in cells:
        if rng.random() >= density or not t.inside(x, y + 1, z):
            continue
        above = t.get(x, y + 1, z)
        if above is not None and B.name_of(above) != 'minecraft:air':
            continue
        r = rng.random() * total
        for st, w in choices:
            r -= w
            if r <= 0:
                t.set(x, y + 1, z, st)
                break


# ---------------------------------------------------------------- loot tables

def loot_item(name, weight=1, count=None):
    e = {'name': name if ':' in name else 'minecraft:' + name, 'type': 'minecraft:item'}
    if weight != 1:
        e['weight'] = weight
    if count is not None:
        lo, hi = count if isinstance(count, tuple) else (count, count)
        if lo == hi:
            e['functions'] = [{'add': False, 'count': float(lo), 'function': 'minecraft:set_count'}]
        else:
            e['functions'] = [{'add': False, 'count': {'max': float(hi), 'min': float(lo), 'type': 'minecraft:uniform'},
                               'function': 'minecraft:set_count'}]
    return e


def loot_table(table, pools):
    """Writes data/oldrepublic/loot_table/<table>.json. pools: [(rolls, [entries])], rolls a number
    or a (min, max) pair. Written in the style of the existing chest tables (sorted keys, LF)."""
    out = []
    for rolls, entries in pools:
        if isinstance(rolls, tuple):
            r = {'max': float(rolls[1]), 'min': float(rolls[0]), 'type': 'minecraft:uniform'}
        else:
            r = float(rolls)
        out.append({'entries': entries, 'rolls': r})
    data = {'pools': out, 'random_sequence': 'oldrepublic:' + table, 'type': 'minecraft:chest'}
    path = os.path.join(LOOT, table + '.json')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(data, indent=2, sort_keys=True) + '\n'
    if not (os.path.exists(path) and open(path).read() == text):
        with open(path, 'w') as f:
            f.write(text)
    return path


# ---------------------------------------------------------------- worldgen families

def adapted(template_height, yard_stand=1, support_depth=8, ramp_rings=2, clear_inset=0):
    """The oldrepublic:adapted_pool_element settings of a piece: clear_from is the piece's height
    (the fit removes terrain above the piece; inside it the template's own air cells cut)."""
    return dict(yard_stand=yard_stand, clear_from=template_height, clear_inset=clear_inset,
                support_depth=support_depth, ramp_rings=ramp_rings)


def wilds_family(name, starts, biomes, spacing, separation, salt, buried, max_distance=64,
                 exclusion=('oldrepublic:ruins', 4), no_monsters=True):
    """Writes the worldgen of oldrepublic:wilds/<name>: the start pool (starts: [(template id,
    adapted dict, weight)]), the structure, the structure set (random spread, an exclusion zone around
    the ruins) and the has_structure biome tag (biomes: vanilla biome ids or #tags)."""
    sid = 'wilds/' + name
    W.pool(sid + '/start', [W.element(tid, adapted=ad, weight=w) for tid, ad, w in starts])
    W.structure(sid, start_pool=sid + '/start', biome_tag='has_structure/' + sid, size=1,
                max_distance=max_distance, step='surface_structures', start_height=1 - buried,
                no_monsters=no_monsters)
    W.structure_set(sid, [(sid, 1)], spacing=spacing, separation=separation, salt=salt, exclusion=exclusion)
    W.biome_tag('has_structure/' + sid, list(biomes))
    return sid


def check_ground_box(t):
    """Sanity numbers of a template: size and non-air count (budget 60,000)."""
    return t.size, t.count()
