"""The Template canvas: a box of block states that saves as a structure template.

    from structlib.template import Template
    from structlib.blocks import S

    t = Template(31, 16, 31, seed=1234)       # x, y, z sizes; the seed makes every pick repeatable
    t.fill(0, 0, 0, 30, 0, 30, 'or:ruin_stone')
    t.set(15, 1, 15, S('lantern', hanging=False))
    t.anchor('HEART', 15, 1, 15)               # a named cell for the Java side (site_anchors sidecar)
    t.save('jedi_ruin/courtyard')              # writes the .nbt, the sidecar and a preview when asked

Coordinates are template-local. Unset cells are left out of the template, so the world keeps whatever
is there (the same as structure_void); set AIR where the template must clear (rooms, doorways).
Every random choice goes through ``t.rng`` (a seeded random.Random), so a script run twice writes
byte-identical files.
"""
import json
import os
import random

from . import blocks as B
from . import nbtio
from .nbtio import Byte, Double, Long, TagList

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'oldrepublic')
STRUCTURES = os.path.join(DATA, 'structure')
ANCHORS = os.path.join(DATA, 'site_anchors')


class Template:
    def __init__(self, sx, sy, sz, seed=0, name=None):
        self.size = (sx, sy, sz)
        self.cells = {}
        self.nbt = {}
        self.entities = []
        self.anchors = {}
        self.rng = random.Random(seed)
        self.seed = seed
        self.name = name

    # ------------------------------------------------------------ bounds

    @property
    def sx(self):
        return self.size[0]

    @property
    def sy(self):
        return self.size[1]

    @property
    def sz(self):
        return self.size[2]

    def inside(self, x, y, z):
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    # ------------------------------------------------------------ cells

    def set(self, x, y, z, state, nbt=None, force=True):
        """Set one cell. state None clears the cell (the world is kept there). Out-of-bounds is ignored."""
        if not self.inside(x, y, z):
            return False
        p = (x, y, z)
        if not force and p in self.cells:
            return False
        if state is None:
            self.cells.pop(p, None)
            self.nbt.pop(p, None)
            return True
        self.cells[p] = B.full_name(state) if '[' not in state else _canon(state)
        if nbt is not None:
            self.nbt[p] = nbt
        else:
            self.nbt.pop(p, None)
        return True

    def get(self, x, y, z):
        return self.cells.get((x, y, z))

    def name_at(self, x, y, z):
        s = self.cells.get((x, y, z))
        return B.name_of(s) if s else None

    def is_air(self, x, y, z):
        s = self.cells.get((x, y, z))
        return s is None or B.name_of(s) in ('minecraft:air', 'minecraft:cave_air')

    def is_solid(self, x, y, z):
        s = self.cells.get((x, y, z))
        return s is not None and B.name_of(s) not in ('minecraft:air', 'minecraft:cave_air', 'minecraft:water',
                                                        'minecraft:lava')

    def fill(self, x0, y0, z0, x1, y1, z1, state, replace=None, only_empty=False):
        """Fill a box (inclusive corners). state may be a str, a Palette role picker (callable) or None.

        replace: only change cells whose block name is in this collection ('air' matches unset cells too).
        only_empty: only fill cells that are unset or air.
        """
        xa, xb = sorted((x0, x1))
        ya, yb = sorted((y0, y1))
        za, zb = sorted((z0, z1))
        n = 0
        for y in range(ya, yb + 1):
            for z in range(za, zb + 1):
                for x in range(xa, xb + 1):
                    if not self.inside(x, y, z):
                        continue
                    if replace is not None and not self._matches(x, y, z, replace):
                        continue
                    if only_empty and not self.is_air(x, y, z):
                        continue
                    s = state(x, y, z) if callable(state) else state
                    self.set(x, y, z, s)
                    n += 1
        return n

    def _matches(self, x, y, z, names):
        s = self.cells.get((x, y, z))
        if s is None:
            return 'air' in names or 'minecraft:air' in names or None in names
        n = B.name_of(s)
        return n in names or n.split(':', 1)[1] in names

    def replace_all(self, old, new, prob=1.0, region=None):
        """Replace every cell whose block name is `old` (a name or a set of names) by `new` (str or callable)."""
        olds = {B.full_name(o) for o in ([old] if isinstance(old, str) else old)}
        n = 0
        for p, s in list(self.cells.items()):
            if region and not _in_region(p, region):
                continue
            if B.name_of(s) in olds and (prob >= 1.0 or self.rng.random() < prob):
                ns = new(*p) if callable(new) else new
                self.set(*p, ns)
                n += 1
        return n

    def clear(self, x0, y0, z0, x1, y1, z1):
        """Unset a box: the template will not touch the world there."""
        return self.fill(x0, y0, z0, x1, y1, z1, None)

    def air(self, x0, y0, z0, x1, y1, z1):
        """Fill a box with air: the template clears the world there."""
        return self.fill(x0, y0, z0, x1, y1, z1, B.AIR)

    # ------------------------------------------------------------ composition

    def paste(self, other, ox, oy, oz, rotation=0, mirror=None, skip_air=False, keep_existing=False):
        """Copy another Template into this one at (ox, oy, oz), turned clockwise `rotation` quarters
        (around the other template's own origin corner, then shifted so it stays at +x, +z of the
        offset) and optionally mirrored ('x' or 'z') first. Anchors and block entities come along."""
        sx, sy, sz = other.size
        for (x, y, z), s in other.cells.items():
            if skip_air and B.name_of(s) in ('minecraft:air', 'minecraft:cave_air'):
                continue
            st = s
            mx, mz = x, z
            if mirror == 'x':
                mx = sx - 1 - x
                st = B.mirror_x(st)
            elif mirror == 'z':
                mz = sz - 1 - z
                st = B.mirror_z(st)
            rx, rz = _rot_xz(mx, mz, sx, sz, rotation)
            st = B.rotate(st, rotation)
            p = (ox + rx, oy + y, oz + rz)
            if keep_existing and p in self.cells:
                continue
            nbt = other.nbt.get((x, y, z))
            if nbt is not None and B.name_of(st) == 'minecraft:jigsaw':
                nbt = dict(nbt)
            self.set(*p, st, nbt=nbt)
        for name, cells in other.anchors.items():
            for (x, y, z) in cells:
                mx = sx - 1 - x if mirror == 'x' else x
                mz = sz - 1 - z if mirror == 'z' else z
                rx, rz = _rot_xz(mx, mz, sx, sz, rotation)
                self.anchor(name, ox + rx, oy + y, oz + rz)
        for e in other.entities:
            ex, ey, ez = e['pos']
            mx = sx - ex if mirror == 'x' else ex
            mz = sz - ez if mirror == 'z' else ez
            rx, rz = _rot_xz_f(mx, mz, sx, sz, rotation)
            bx, by, bz = e['blockPos']
            bmx = sx - 1 - bx if mirror == 'x' else bx
            bmz = sz - 1 - bz if mirror == 'z' else bz
            brx, brz = _rot_xz(bmx, bmz, sx, sz, rotation)
            ne = dict(e)
            ne['pos'] = [ox + rx, oy + ey, oz + rz]
            ne['blockPos'] = [ox + brx, oy + by, oz + brz]
            self.entities.append(ne)

    # ------------------------------------------------------------ anchors, block entities, entities

    def anchor(self, name, x, y, z):
        """Record a named cell for the site_anchors sidecar (the Java side finds spots by these names)."""
        if not self.inside(x, y, z):
            raise ValueError('anchor %s at %r is outside the template %r' % (name, (x, y, z), self.size))
        self.anchors.setdefault(name, [])
        if [x, y, z] not in self.anchors[name]:
            self.anchors[name].append([x, y, z])

    def chest(self, x, y, z, facing='north', loot=None, block='chest', waterlogged=False, **extra):
        """A chest or barrel with a loot table id (data/oldrepublic/loot_table/<path>.json)."""
        props = {'facing': facing}
        if B.full_name(block) in ('minecraft:chest', 'minecraft:trapped_chest'):
            props.update(type='single', waterlogged=waterlogged)
        if B.full_name(block) == 'minecraft:barrel':
            props = {'facing': facing, 'open': False}
        nbt = {}
        if loot:
            nbt['LootTable'] = loot if ':' in loot else 'oldrepublic:' + loot
        nbt.update(extra)
        self.set(x, y, z, B.S(block, **props), nbt=nbt or None)

    def jigsaw(self, x, y, z, orientation, name='minecraft:empty', target='minecraft:empty',
               pool='minecraft:empty', final_state='minecraft:air', joint=None, selection_priority=0,
               placement_priority=0):
        """A jigsaw block. orientation is FrontAndTop, e.g. 'north_up' (horizontal) or 'down_south'.

        joint defaults to 'rollable' for vertical connectors and 'aligned' for horizontal ones."""
        front = orientation.split('_')[0]
        if joint is None:
            joint = 'rollable' if front in ('up', 'down') else 'aligned'
        nbt = {
            'name': _ns(name), 'target': _ns(target), 'pool': _ns(pool),
            'final_state': final_state, 'joint': joint,
            'selection_priority': selection_priority, 'placement_priority': placement_priority,
        }
        self.set(x, y, z, B.S('jigsaw', orientation=orientation), nbt=nbt)

    def entity(self, x, y, z, nbt):
        """An entity (decoration only: armor stands, item frames). x, y, z are floats in template space."""
        self.entities.append({'pos': [float(x), float(y), float(z)],
                              'blockPos': [int(x // 1), int(y // 1), int(z // 1)], 'nbt': nbt})

    # ------------------------------------------------------------ output

    def to_nbt(self):
        palette, index = [], {}
        blocks = []
        # sorted for byte-identical output: y, then z, then x
        for (x, y, z) in sorted(self.cells, key=lambda p: (p[1], p[2], p[0])):
            s = self.cells[(x, y, z)]
            if s not in index:
                index[s] = len(palette)
                name, props = B.parse(s)
                entry = {'Name': name}
                if props:
                    entry['Properties'] = {k: props[k] for k in sorted(props)}
                palette.append(entry)
            b = {'pos': TagList(nbtio.TAG_INT, [x, y, z]), 'state': index[s]}
            nb = self.nbt.get((x, y, z))
            if nb is not None:
                nb = dict(nb)
                nb.setdefault('id', _be_id(s))
                b['nbt'] = nb
            blocks.append(b)
        ents = []
        for e in self.entities:
            ents.append({'pos': TagList(nbtio.TAG_DOUBLE, [Double(v) for v in e['pos']]),
                         'blockPos': TagList(nbtio.TAG_INT, e['blockPos']), 'nbt': e['nbt']})
        return {
            'DataVersion': nbtio.DATA_VERSION,
            'size': TagList(nbtio.TAG_INT, list(self.size)),
            'palette': TagList(nbtio.TAG_COMPOUND, palette),
            'blocks': TagList(nbtio.TAG_COMPOUND, blocks),
            'entities': TagList(nbtio.TAG_COMPOUND, ents),
        }

    def finish(self):
        """Join fences, walls, panes and bars to their neighbours and give stairs their corner shapes."""
        from . import connect
        connect.finish(self)

    def save(self, template_id, preview=None, anchors=True, finish=True):
        """Write data/oldrepublic/structure/<template_id>.nbt and, when it has anchors, the
        site_anchors sidecar. preview: a PNG path to render a preview sheet to (optional).
        finish runs finish() first."""
        if finish:
            self.finish()
        path = os.path.join(STRUCTURES, template_id + '.nbt')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nbtio.dump(self.to_nbt(), path)
        side = os.path.join(ANCHORS, template_id + '.json')
        if anchors and self.anchors:
            os.makedirs(os.path.dirname(side), exist_ok=True)
            with open(side, 'w') as f:
                json.dump({k: self.anchors[k] for k in sorted(self.anchors)}, f, indent=2, sort_keys=True)
                f.write('\n')
        if preview:
            from . import preview as P
            img = P.sheet(self.to_nbt(), title=template_id)
            os.makedirs(os.path.dirname(os.path.abspath(preview)), exist_ok=True)
            img.convert('RGB').save(preview)
        return path

    def count(self):
        """Number of set cells that are not air."""
        return sum(1 for s in self.cells.values() if B.name_of(s) not in ('minecraft:air', 'minecraft:cave_air'))


# ---------------------------------------------------------------- helpers

def _canon(state):
    n, p = B.parse(state)
    return B.build(n, p)


def _ns(v):
    return v if ':' in v else 'oldrepublic:' + v


def _in_region(p, region):
    (x0, y0, z0), (x1, y1, z1) = region
    return min(x0, x1) <= p[0] <= max(x0, x1) and min(y0, y1) <= p[1] <= max(y0, y1) and \
        min(z0, z1) <= p[2] <= max(z0, z1)


def _rot_xz(x, z, sx, sz, quarter):
    """Clockwise rotation of a cell inside an sx * sz footprint, kept at +x, +z."""
    quarter %= 4
    if quarter == 0:
        return x, z
    if quarter == 1:
        return sz - 1 - z, x
    if quarter == 2:
        return sx - 1 - x, sz - 1 - z
    return z, sx - 1 - x


def _rot_xz_f(x, z, sx, sz, quarter):
    quarter %= 4
    if quarter == 0:
        return x, z
    if quarter == 1:
        return sz - z, x
    if quarter == 2:
        return sx - x, sz - z
    return z, sx - x


_BE_IDS = {
    'minecraft:chest': 'minecraft:chest', 'minecraft:trapped_chest': 'minecraft:trapped_chest',
    'minecraft:barrel': 'minecraft:barrel', 'minecraft:jigsaw': 'minecraft:jigsaw',
    'minecraft:lectern': 'minecraft:lectern', 'minecraft:decorated_pot': 'minecraft:decorated_pot',
    'minecraft:campfire': 'minecraft:campfire', 'minecraft:soul_campfire': 'minecraft:campfire',
    'minecraft:brushable_block': 'minecraft:brushable_block',
    'minecraft:suspicious_sand': 'minecraft:brushable_block', 'minecraft:suspicious_gravel': 'minecraft:brushable_block',
    'minecraft:spawner': 'minecraft:mob_spawner', 'minecraft:furnace': 'minecraft:furnace',
    'minecraft:blast_furnace': 'minecraft:blast_furnace', 'minecraft:smoker': 'minecraft:smoker',
    'minecraft:hopper': 'minecraft:hopper', 'minecraft:dispenser': 'minecraft:dispenser',
    'minecraft:dropper': 'minecraft:dropper', 'minecraft:chiseled_bookshelf': 'minecraft:chiseled_bookshelf',
    'minecraft:shelf': 'minecraft:shelf',
}


def _be_id(state):
    n = B.name_of(state)
    if n in _BE_IDS:
        return _BE_IDS[n]
    if n.endswith('_banner'):
        return 'minecraft:banner'
    if n.endswith('_sign'):
        return 'minecraft:hanging_sign' if 'hanging' in n else 'minecraft:sign'
    if n.endswith('_shulker_box'):
        return 'minecraft:shulker_box'
    if n.endswith('_bed'):
        return 'minecraft:bed'
    if n.endswith('_skull') or n.endswith('_head'):
        return 'minecraft:skull'
    return n
