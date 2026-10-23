"""OR's shared helpers (Milestone 7: the meteor craters, docs/MILESTONE_7.md 4.1).

Every script in this folder imports this module first: it puts tools/assets on the path so the `structlib`
imports work, and holds the command line, saving with an optional preview sheet, the pick helpers, the
oldrepublic:adapted_pool_element settings and the writer for a wilds structure's worldgen family. Nothing here is
random except through a Template's own seeded rng or a Noise(seed), so every build is byte-identical.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.abspath(os.path.join(HERE, '..', '..'))
if ASSETS not in sys.path:
    sys.path.insert(0, ASSETS)

from structlib import Template, S, AIR, palettes, worldgen as W  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402,F401
from structlib.nature import Noise  # noqa: E402,F401


# ---------------------------------------------------------------- command line and saving

def cli(doc):
    ap = argparse.ArgumentParser(description=doc, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--preview', metavar='DIR', help='render a preview sheet of every template into DIR (a scratch folder)')
    return ap.parse_args()


def save(t, template_id, preview_dir=None, views=('se', 'nw'), plans='auto', cut=None, width=1600):
    """Writes the template (finish, nbt, sidecar) and, with preview_dir, its preview sheet."""
    path = t.save(template_id)
    if preview_dir:
        from structlib import preview as P
        img = P.sheet(t.to_nbt(), title=template_id, views=views, plans=plans, cut=cut, width=width)
        os.makedirs(preview_dir, exist_ok=True)
        out = os.path.join(preview_dir, template_id.replace('/', '_') + '.png')
        img.convert('RGB').save(out)
        print('preview', out)
    return path


# ---------------------------------------------------------------- picks

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


def patches(seed, opts, scale=4.0):
    """A callable (x, y, z) -> state choosing from [(state, weight)] by coherent noise (variants clump in
    patches; the middle of the list is the commonest)."""
    return palettes.Palette(r=opts).patches('r', seed=seed, scale=scale)


# ---------------------------------------------------------------- worldgen families

def adapted(template_height, yard_stand, support_depth=8, ramp_rings=2, clear_inset=0):
    """The oldrepublic:adapted_pool_element settings of a piece: clear_from is the piece's height (the fit removes
    terrain above the piece, inset from its edges; inside it the template's own air cells cut)."""
    return dict(yard_stand=yard_stand, clear_from=template_height, clear_inset=clear_inset,
                support_depth=support_depth, ramp_rings=ramp_rings)


def wilds_family(name, starts, biomes, spacing, separation, salt, buried, placement='random_spread', min_distance=None,
                 exclusion=('oldrepublic:ruins', 4), max_distance=64, no_monsters=True):
    """Writes the worldgen of oldrepublic:wilds/<name>: the start pool (starts: [(template id, adapted dict,
    weight)]), the structure (its start set `buried` layers into the ground), the structure set and the
    has_structure biome tag (biomes: vanilla biome ids or #tags)."""
    sid = 'wilds/' + name
    W.pool(sid + '/start', [W.element(tid, adapted=ad, weight=w) for tid, ad, w in starts])
    W.structure(sid, start_pool=sid + '/start', biome_tag='has_structure/' + sid, size=1,
                max_distance=max_distance, step='surface_structures', start_height=1 - buried,
                no_monsters=no_monsters)
    W.structure_set(sid, [(sid, 1)], spacing=spacing, separation=separation, salt=salt, exclusion=exclusion,
                    placement=placement, min_distance=min_distance)
    W.biome_tag('has_structure/' + sid, list(biomes))
    return sid
