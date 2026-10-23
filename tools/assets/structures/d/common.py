"""Shared helpers of worker D's structures (docs/MILESTONE_6.md 5.8): the Reliquary, the Hymnal, the
expeditions, the hunting camp and the greater tomb.

Every script in this folder builds one structure or family with structlib and writes its templates,
anchor sidecars, site meta and (for the greater tomb) its worldgen files. Everything is deterministic:
randomness comes from a template's seeded t.rng or from Noise(seed), so a rebuild writes byte-identical
files. Previews are only rendered when the environment variable D_PREVIEW names a folder (a scratch
folder, never the repository).

Conventions:
  * x east, y up, z south; a runtime site's entrance is its north side (z = 0).
  * `buried` B: layer B - 1 is the ground the player walks on, layer B is the feet layer.
  * Templates that keep their size are reworked in place: Template.load, then every mechanism cell,
    seal, door, route cell and anchor cell is frozen (kept byte-identical, block entity data included)
    and the re-skin and detail go around them. save() restores and verifies the frozen cells after
    Template.finish(), so a stair or wall connection next to them cannot change them either.
"""
import copy
import json
import math
import os
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True
ASSETS = os.path.abspath(os.path.join(HERE, '..', '..'))
if ASSETS not in sys.path:
    sys.path.insert(0, ASSETS)

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta, worldgen as W  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402
from structlib.nature import Noise  # noqa: E402

ROOT = os.path.abspath(os.path.join(ASSETS, '..', '..'))
with open(os.path.join(ROOT, 'tools', 'm6_baseline.json')) as _f:
    BASELINE = json.load(_f)

AIRS = ('minecraft:air', 'minecraft:cave_air')

# ---------------------------------------------------------------- block states


def stairs(name, facing, half='bottom', shape='straight'):
    return S(name, facing=facing, half=half, shape=shape, waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def wall(name):
    return S(name, north='none', east='none', south='none', west='none', up=True, waterlogged=False)


def fence(name):
    return S(name, north=False, east=False, south=False, west=False, waterlogged=False)


def bars(name='iron_bars'):
    return S(name, north=False, east=False, south=False, west=False, waterlogged=False)


def log(name, axis='y'):
    return S(name, axis=axis)


def trapdoor(name, facing, half='bottom', open_=False):
    return S(name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def lantern(soul=False, hanging=False):
    return S('soul_lantern' if soul else 'lantern', hanging=hanging, waterlogged=False)


def chain(axis='y', name='iron_chain'):
    return S(name, axis=axis, waterlogged=False)


def candle(n=1, lit=True, color=None):
    return S('%s_candle' % color if color else 'candle', candles=n, lit=lit, waterlogged=False)


def campfire(lit=True, soul=False, facing='north', signal=False):
    return S('soul_campfire' if soul else 'campfire', facing=facing, lit=lit, signal_fire=signal, waterlogged=False)


def ladder(facing):
    return S('ladder', facing=facing, waterlogged=False)


def cluster(name, facing='up'):
    return S(name, facing=facing, waterlogged=False)


def pillar_block(name, axis='y'):
    return S(name, axis=axis)


def light(level):
    return S('light', level=level, waterlogged=False)


def cobweb():
    return 'minecraft:cobweb'


def skull(rotation=0):
    return S('skeleton_skull', rotation=rotation, powered=False)


def pot():
    return S('decorated_pot', facing='north', cracked=False, waterlogged=False)


# ---------------------------------------------------------------- frozen cells

class DTemplate(Template):
    """A Template whose frozen cells refuse every write through set() (and are restored on save()).

    reserved cells (routes: feet and head cells a player must keep walking through) may be re-skinned
    but take no props: free() says whether a prop may go into a cell."""
    frozen = None
    reserved = None

    def set(self, x, y, z, state, nbt=None, force=True):
        if self.frozen is not None and (x, y, z) in self.frozen:
            return False
        return super().set(x, y, z, state, nbt=nbt, force=force)

    def reserve(self, cells):
        if self.reserved is None:
            self.reserved = set()
        self.reserved.update(tuple(p) for p in cells)

    def free(self, x, y, z, need_air=True):
        """A prop may go into this cell: inside, not frozen, not reserved and (need_air) set air."""
        p = (x, y, z)
        if not self.inside(*p) or (self.frozen is not None and p in self.frozen):
            return False
        if self.reserved is not None and p in self.reserved:
            return False
        return is_air_set(self, p) if need_air else True

    def freeze(self, cells):
        if self.frozen is None:
            self.frozen = {}
        for p in cells:
            p = tuple(p)
            if p not in self.frozen:
                self.frozen[p] = (self.cells.get(p), copy.deepcopy(self.nbt.get(p)))

    def freeze_box(self, x0, y0, z0, x1, y1, z1):
        self.freeze([(x, y, z) for x in range(min(x0, x1), max(x0, x1) + 1)
                     for y in range(min(y0, y1), max(y0, y1) + 1)
                     for z in range(min(z0, z1), max(z0, z1) + 1) if self.inside(x, y, z)])

    def is_frozen(self, x, y, z):
        return self.frozen is not None and (x, y, z) in self.frozen

    def restore(self):
        """Puts every frozen cell back exactly as it was frozen."""
        for p, (s, n) in (self.frozen or {}).items():
            if s is None:
                self.cells.pop(p, None)
                self.nbt.pop(p, None)
            else:
                self.cells[p] = s
                if n is not None:
                    self.nbt[p] = copy.deepcopy(n)
                else:
                    self.nbt.pop(p, None)

    def changed_frozen(self):
        out = []
        for p, (s, n) in (self.frozen or {}).items():
            if self.cells.get(p) != s or self.nbt.get(p) != n:
                out.append(p)
        return out


BASE = os.path.join(HERE, 'base')


def load(template_id, seed):
    """A template this worker reworks in place, read from its pristine copy in base/ (the template and its
    sidecar as they stood before Milestone 6), so a rerun never transforms an already transformed file."""
    t = DTemplate.load(os.path.join(BASE, template_id), seed=seed)
    t.name = template_id
    return t


def mechanism(template_id):
    """The baseline's mechanism cells of a template: {(x, y, z): block name}."""
    entry = BASELINE['templates'].get(template_id, {})
    return {tuple(int(v) for v in k.split(',')): n for k, n in entry.get('mechanism', {}).items()}


def anchor_cells(t, feet_headroom=True):
    """Every anchor cell, plus the head cell over each feet anchor (an anchor on air)."""
    out = set()
    for cells in t.anchors.values():
        for c in cells:
            p = tuple(c)
            out.add(p)
            if feet_headroom and t.is_air(*p) and p in t.cells:
                out.add((p[0], p[1] + 1, p[2]))
    return out


# ---------------------------------------------------------------- walking

_CLIMB = ('ladder', 'vine', 'scaffolding', 'twisting_vines', 'weeping_vines')


def _path(state):
    return B.name_of(state).split(':', 1)[1] if state else ''


def passable(state, open_cells=None, p=None):
    """A person's feet or head fit in the cell."""
    if open_cells is not None and p in open_cells:
        return True
    if state is None:
        return False
    n = _path(state)
    return mod.walk_through(state) or n in _CLIMB or n.endswith('_carpet')


def supports(state):
    return state is not None and not passable(state) and not _path(state) in ('water', 'lava')


def standable(t, p, open_cells=None):
    x, y, z = p
    feet, head, below = t.cells.get(p), t.cells.get((x, y + 1, z)), t.cells.get((x, y - 1, z))
    if not passable(feet, open_cells, p) or not passable(head, open_cells, (x, y + 1, z)):
        return False
    return supports(below) or _path(feet) in _CLIMB or _path(below) in _CLIMB


def reach(t, starts, open_cells=None, max_drop=3, region=None):
    """Feet cells reachable on foot from `starts`: level steps, one block up (with headroom), drops of at
    most max_drop and ladders. open_cells count as passable (doors and seals that open)."""
    seen = set()
    q = deque()
    for s in starts:
        s = tuple(s)
        if standable(t, s, open_cells):
            seen.add(s)
            q.append(s)
    while q:
        x, y, z = q.popleft()
        here = t.cells.get((x, y, z))
        nxt = []
        if _path(here) in _CLIMB:
            nxt += [(x, y + 1, z), (x, y - 1, z)]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            nxt.append((nx, y, nz))
            if passable(t.cells.get((x, y + 2, z)), open_cells, (x, y + 2, z)):
                nxt.append((nx, y + 1, nz))
            for d in range(1, max_drop + 1):
                nxt.append((nx, y - d, nz))
                if not passable(t.cells.get((nx, y - d + 1, nz)), open_cells, (nx, y - d + 1, nz)):
                    break
        for p in nxt:
            if p in seen or not t.inside(*p):
                continue
            if region and not (region[0] <= p[0] <= region[3] and region[1] <= p[1] <= region[4]
                               and region[2] <= p[2] <= region[5]):
                continue
            if standable(t, p, open_cells):
                seen.add(p)
                q.append(p)
    return seen


def check_reach(t, starts, targets, open_cells=None, label=''):
    """Raises when a target cell is no longer reachable from the starts."""
    got = reach(t, starts, open_cells)
    missing = [tuple(p) for p in targets if tuple(p) not in got]
    if missing:
        raise SystemExit('%s: cannot walk to %s' % (label, missing[:12]))
    return got


def _paths(t, starts, open_cells, max_drop=3):
    """BFS parents over standable cells from the starts (as reach())."""
    parent = {}
    q = deque()
    for s in starts:
        s = tuple(s)
        if standable(t, s, open_cells):
            parent[s] = None
            q.append(s)
    while q:
        x, y, z = q.popleft()
        here = t.cells.get((x, y, z))
        nxt = []
        if _path(here) in _CLIMB:
            nxt += [(x, y + 1, z), (x, y - 1, z)]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            nxt.append((nx, y, nz))
            if passable(t.cells.get((x, y + 2, z)), open_cells, (x, y + 2, z)):
                nxt.append((nx, y + 1, nz))
            for d in range(1, max_drop + 1):
                nxt.append((nx, y - d, nz))
                if not passable(t.cells.get((nx, y - d + 1, nz)), open_cells, (nx, y - d + 1, nz)):
                    break
        for p in nxt:
            if p in parent or not t.inside(*p):
                continue
            if standable(t, p, open_cells):
                parent[p] = (x, y, z)
                q.append(p)
    return parent


def protect_routes(t, starts, open_cells=None, targets=(), near_anchors=True, width=1):
    """Reserves the walk from `starts` to every target (and, with near_anchors, to the cells next to every
    anchor) as a corridor `width` cells either side of the shortest path: feet and head cells stay clear.
    Returns (reachable cells before any change, the key cells that must stay reachable)."""
    parent = _paths(t, starts, open_cells)
    reach0 = set(parent)
    keys = set(tuple(p) for p in targets if tuple(p) in reach0)
    if near_anchors:
        for cells in t.anchors.values():
            for (ax, ay, az) in cells:
                for p in reach0:
                    if abs(p[0] - ax) + abs(p[2] - az) <= 1 and abs(p[1] - ay) <= 1:
                        keys.add(p)
    route = set()
    for k in keys:
        p = k
        while p is not None:
            route.add(p)
            p = parent[p]
    corridor = set(route)
    for (x, y, z) in route:
        for dx in range(-width, width + 1):
            for dz in range(-width, width + 1):
                q = (x + dx, y, z + dz)
                if q in reach0:
                    corridor.add(q)
    cells = set()
    for (x, y, z) in corridor:
        cells.add((x, y, z))
        cells.add((x, y + 1, z))
    t.reserve(cells)
    return reach0, keys


# ---------------------------------------------------------------- light

def dark_floors(t, region=None, level=8):
    """Floor cells in region (x0, y0, z0, x1, y1, z1) whose block light is under `level`."""
    lit = mod.light_map(t)
    return [p for p in mod.floor_cells(t, region) if lit.get(p, 0) < level]


def fill_light(t, region=None, level=8, strength=12, rise=3, floors=None):
    """mod.light_fill that never writes a frozen cell: the invisible light goes into the first set-air cell
    from the dark floor's head cell up to `rise` above its feet (then its feet cell). Returns the cells lit;
    raises when a floor cell stays dark. floors: only these feet cells (default: every floor cell)."""
    floors = mod.floor_cells(t, region) if floors is None else sorted(floors)
    added = {}
    lit = mod.light_map(t)

    def ok(q):
        if not t.inside(*q) or q in added:
            return False
        if isinstance(t, DTemplate) and t.is_frozen(*q):
            return False
        return is_air_set(t, q)
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
        spot = None
        for q in [(x, y + k, z) for k in range(1, rise + 1)] + [(x, y, z)]:
            if ok(q):
                spot = q
                break
        if spot is None:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                for k in range(1, rise + 1):
                    q = (x + dx, y + k, z + dz)
                    if ok(q):
                        spot = q
                        break
                if spot:
                    break
        if spot is None:
            raise SystemExit('%s: no free cell to light the floor at %r' % (t.name, (x, y, z)))
        added[spot] = B.S('light', level=strength)
        t.set(*spot, added[spot])
        if lit.get(spot, 0) < strength:
            lit[spot] = strength
            mod._spread(t, lit, deque([spot]), {})
    left = [p for p in floors if lit.get(p, 0) < level]
    if left:
        raise SystemExit('%s: dark floor cells left %r' % (t.name, left[:8]))
    return sorted(added)


# ---------------------------------------------------------------- materials

def mixer(seed, options, scale=4.0, jitter=0.25):
    """f(x, y, z) -> state chosen from [(state, weight)] by coherent noise (patches, not salt and pepper)."""
    noise = Noise(seed, scale)
    fine = Noise(seed + 977, 1.3)
    total = float(sum(w for _, w in options))

    def f(x, y, z):
        v = (1 - jitter) * noise.at(x, y, z) + jitter * fine.at(x, y, z)
        r = v * total
        for s, w in options:
            r -= w
            if r <= 0:
                return s
        return options[-1][0]
    return f


def hash01(seed, x, y=0, z=0):
    n = (x * 374761393 + y * 668265263 + z * 2147483647 + seed * 974711) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


NEIGHBOURS6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
SIDES = (('east', 1, 0), ('west', -1, 0), ('south', 0, 1), ('north', 0, -1))


def is_air_set(t, p):
    """The template sets air at p (an unset cell keeps the world and is not air for the re-skin)."""
    s = t.cells.get(p)
    return s is not None and B.name_of(s) in AIRS


def faces(t, p):
    """Which sides of a solid cell look into air the template sets: a set of 'up', 'down', 'east'..."""
    x, y, z = p
    out = set()
    if is_air_set(t, (x, y + 1, z)):
        out.add('up')
    if is_air_set(t, (x, y - 1, z)):
        out.add('down')
    for d, dx, dz in SIDES:
        if is_air_set(t, (x + dx, y, z + dz)):
            out.add(d)
    return out


def reskin(t, rule, region=None, names=None):
    """Re-skins solid cells that face set air. rule(p, state, faces) -> new state or None (keep).
    names: only cells whose block name is in this collection."""
    names = {B.full_name(n) for n in names} if names else None
    changes = {}
    for p, s in t.cells.items():
        if region and not (region[0] <= p[0] <= region[3] and region[1] <= p[1] <= region[4]
                           and region[2] <= p[2] <= region[5]):
            continue
        n = B.name_of(s)
        if n in AIRS or (names is not None and n not in names):
            continue
        if p in t.nbt:
            continue
        f = faces(t, p)
        ns = rule(p, s, f)
        if ns is not None and ns != s:
            changes[p] = ns
    for p, s in changes.items():
        t.set(*p, s)
    return len(changes)


# ---------------------------------------------------------------- output

def preview_dir():
    d = os.environ.get('D_PREVIEW')
    if d:
        os.makedirs(d, exist_ok=True)
    return d


def preview(t, name, **kw):
    """Renders a preview sheet of t into $D_PREVIEW/<name>.png (nothing when D_PREVIEW is unset)."""
    d = preview_dir()
    if not d:
        return None
    from structlib import preview as P
    img = P.sheet(t.to_nbt(), title=t.name or name, **kw)
    path = os.path.join(d, name + '.png')
    img.convert('RGB').save(path)
    return path


def save(t, template_id, preview_name=None, check=None, **preview_kw):
    """Finishes connections, restores and verifies the frozen cells, saves the template and its sidecar,
    and renders a preview when asked. Returns a one-line summary."""
    t.finish()
    if isinstance(t, DTemplate):
        t.restore()
        bad = t.changed_frozen()
        if bad:
            raise SystemExit('%s: frozen cells changed: %s' % (template_id, bad[:10]))
    if check:
        check(t)
    t.name = template_id
    t.save(template_id, anchors=True, finish=False)
    if preview_name:
        preview(t, preview_name, **preview_kw)
    n = t.count()
    if n > 60000:
        raise SystemExit('%s: %d cells are not air (budget 60,000)' % (template_id, n))
    if max(t.size) > 128:
        raise SystemExit('%s: size %r is over 128' % (template_id, t.size))
    line = '%s %dx%dx%d, %d not air, anchors %s' % (template_id, t.sx, t.sy, t.sz, n,
                                                   ', '.join('%s%s' % (k, '' if len(v) == 1 else '*%d' % len(v))
                                                             for k, v in sorted(t.anchors.items())))
    print(line)
    return line


def write_meta(template_id, **fields):
    return meta.write(template_id, **fields)


def ring_dist(x, z, cx, cz):
    return math.hypot(x - cx, z - cz)
