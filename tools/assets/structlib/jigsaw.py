"""Jigsaw assembly on flat ground, the way vanilla's JigsawPlacement does it, to check a multi-piece
structure before it runs in game (docs/STRUCTURES.md 4 and 6).

    from structlib import jigsaw
    result = jigsaw.assemble('wilds/dune_camp/start', max_distance=64, size=4)
    print(result.report())          # pieces per start rotation, and every problem found
    assert not result.problems

The pools are read from data/oldrepublic/worldgen/template_pool and the templates from
data/oldrepublic/structure; pass templates={'wilds/dune_camp/tent': t} to use Template objects you have
not saved yet. Every start element is tried with every start rotation. At each jigsaw the candidates
are tried in pool order (then the fallback pool), each in the four rotations, and the first that fits
is placed, as vanilla does with its shuffled order; besides, every element of the pool is tested in
that spot, and an element that fits at none of the spots that offer it is reported, because vanilla
would silently never place it.

The vanilla rules: the child's jigsaw faces the parent's, its name is the parent's target, a vertical
pair keeps its top unless the joint is rollable, the child's box must not overlap any placed box
(a connection inside the parent's box may place inside the parent but not over its other children),
every box stays inside max_distance of the start's centre, pieces at depth `size` take only the
fallback pool, and an empty element drawn from the pool ends the attempt with nothing placed.
"""
import glob
import json
import os
from collections import deque

from . import blocks as B
from . import nbtio

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data')

ROTATIONS = ('none', 'clockwise_90', 'clockwise_180', 'counterclockwise_90')
_QUARTERS = {'none': 0, 'clockwise_90': 1, 'clockwise_180': 2, 'counterclockwise_90': 3}
_STEP = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0),
         'down': (0, -1, 0)}
_OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east', 'up': 'down', 'down': 'up'}
_CW = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north', 'up': 'up', 'down': 'down'}


def _id(v):
    return v if ':' in v else 'oldrepublic:' + v


def _path(v):
    ns, _, p = _id(v).partition(':')
    return ns, p


def rotate_pos(pos, rotation):
    """Vanilla StructureTemplate.transform with the pivot at the origin."""
    x, y, z = pos
    q = _QUARTERS[rotation]
    if q == 1:
        return (-z, y, x)
    if q == 2:
        return (-x, y, -z)
    if q == 3:
        return (z, y, -x)
    return (x, y, z)


def rotate_dir(d, rotation):
    for _ in range(_QUARTERS[rotation]):
        d = _CW[d]
    return d


def box_of(origin, size, rotation):
    """The inclusive world box (min, max) of a template placed at origin with rotation."""
    sx, sy, sz = size
    corners = [rotate_pos((x, 0, z), rotation) for x in (0, sx - 1) for z in (0, sz - 1)]
    xs = [c[0] for c in corners]
    zs = [c[2] for c in corners]
    return ((origin[0] + min(xs), origin[1], origin[2] + min(zs)),
            (origin[0] + max(xs), origin[1] + sy - 1, origin[2] + max(zs)))


def intersects(a, b):
    return all(a[0][i] <= b[1][i] and b[0][i] <= a[1][i] for i in range(3))


def inside(inner, outer):
    return all(outer[0][i] <= inner[0][i] and inner[1][i] <= outer[1][i] for i in range(3))


def contains(box, p):
    return all(box[0][i] <= p[i] <= box[1][i] for i in range(3))


class Piece:
    def __init__(self, template, origin, rotation, depth, element, box, parent=None, inner=False):
        self.template = template
        self.origin = origin
        self.rotation = rotation
        self.depth = depth
        self.element = element
        self.box = box
        self.parent = parent
        self.inner = inner  # placed inside its parent's box

    def __repr__(self):
        return '%s@%s/%s(d%d)' % (self.template, self.origin, self.rotation, self.depth)


class Shape:
    """What assemble needs of a template: size and jigsaws [(pos, front, top, name, target, pool, joint)],
    plus the cells above each jigsaw (for the opening check)."""

    def __init__(self, name, size, jigsaws, open_above):
        self.name = name
        self.size = size
        self.jigsaws = jigsaws
        self.open_above = open_above

    @classmethod
    def from_template(cls, name, t):
        jigsaws, open_above = [], {}
        for p, s in t.cells.items():
            n, props = B.parse(s)
            if n != 'minecraft:jigsaw':
                continue
            nb = t.nbt.get(p, {})
            front, top = props.get('orientation', 'north_up').split('_')
            jigsaws.append((p, front, top, nb.get('name', 'minecraft:empty'), nb.get('target', 'minecraft:empty'),
                            nb.get('pool', 'minecraft:empty'), nb.get('joint', 'aligned')))
            above = t.cells.get((p[0], p[1] + 1, p[2]))
            open_above[p] = above is None or B.name_of(above) in ('minecraft:air', 'minecraft:cave_air')
        return cls(name, tuple(t.size), sorted(jigsaws), open_above)

    @classmethod
    def from_nbt(cls, name, data):
        palette = data['palette']
        cells = {}
        jigsaws, open_above = [], {}
        for b in data['blocks']:
            cells[tuple(b['pos'])] = palette[b['state']]
        for b in data['blocks']:
            entry = palette[b['state']]
            if entry['Name'] != 'minecraft:jigsaw':
                continue
            p = tuple(b['pos'])
            nb = b.get('nbt', {})
            front, top = entry.get('Properties', {}).get('orientation', 'north_up').split('_')
            jigsaws.append((p, front, top, nb.get('name', 'minecraft:empty'), nb.get('target', 'minecraft:empty'),
                            nb.get('pool', 'minecraft:empty'), nb.get('joint', 'aligned')))
            above = cells.get((p[0], p[1] + 1, p[2]))
            open_above[p] = above is None or above['Name'] in ('minecraft:air', 'minecraft:cave_air')
        return cls(name, tuple(data['size']), sorted(jigsaws), open_above)


def load_pool(pool_id):
    ns, p = _path(pool_id)
    path = os.path.join(DATA, ns, 'worldgen', 'template_pool', p + '.json')
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return json.load(f)


def load_shape(template_id, templates=None):
    ns, p = _path(template_id)
    if templates:
        for key in (template_id, p, _id(template_id)):
            if key in templates:
                return Shape.from_template(_id(template_id), templates[key])
    path = os.path.join(DATA, ns, 'structure', p + '.nbt')
    if not os.path.exists(path):
        return None
    return Shape.from_nbt(_id(template_id), nbtio.load(path))


class Result:
    def __init__(self):
        self.runs = []       # (start template, start rotation, [Piece])
        self.problems = []   # strings
        self.never_fit = {}  # element template -> pools offering it

    @property
    def pieces(self):
        """The pieces of the first run (the first start element, unrotated)."""
        return self.runs[0][2] if self.runs else []

    def counts(self, run=0):
        out = {}
        for piece in self.runs[run][2]:
            out[piece.template] = out.get(piece.template, 0) + 1
        return out

    def report(self):
        lines = []
        for start, rot, pieces in self.runs:
            counts = {}
            for piece in pieces:
                counts[piece.template] = counts.get(piece.template, 0) + 1
            lines.append('%s %s: %d pieces %s' % (start, rot, len(pieces), json.dumps(counts, sort_keys=True)))
        lines += ['PROBLEM ' + p for p in self.problems]
        return '\n'.join(lines)


def _elements(pool):
    """[(template id or None for empty, projection, weight)]; feature and list elements are skipped."""
    out = []
    for e in pool.get('elements', []):
        el = e['element']
        kind = el.get('element_type', '')
        if kind == 'minecraft:empty_pool_element':
            out.append((None, 'rigid', e.get('weight', 1)))
        elif 'location' in el:
            out.append((_id(el['location']), el.get('projection', 'rigid'), e.get('weight', 1)))
    return out


def assemble(start_pool, templates=None, max_distance=64, size=7, start_height=0, ground=64, start_jigsaw=None):
    """Assembles the structure from start_pool for every start element and rotation; returns a Result.
    start_height is the structure's absolute start height (1 - buried for this mod's structures)."""
    result = Result()
    shapes = {}
    pools = {}
    offered = {}   # element -> pools offering it
    fitted = set()  # elements that fit at some spot

    def shape(tid):
        if tid not in shapes:
            shapes[tid] = load_shape(tid, templates)
            if shapes[tid] is None:
                result.problems.append('template %s does not exist' % tid)
        return shapes[tid]

    def pool(pid):
        pid = _id(pid)
        if pid not in pools:
            pools[pid] = load_pool(pid)
            if pools[pid] is None and pid != 'minecraft:empty':
                result.problems.append('pool %s does not exist' % pid)
        return pools[pid]

    start = pool(start_pool)
    if not start:
        return result
    for (start_tid, start_proj, _) in _elements(start):
        if start_tid is None:
            continue
        s = shape(start_tid)
        if s is None:
            continue
        for start_rot in ROTATIONS:
            pieces = _run(s, start_tid, start_proj, start_rot, shape, pool, max_distance, size, start_height, ground,
                          result, offered, fitted, start_jigsaw)
            result.runs.append((start_tid, start_rot, pieces))
    for tid, pids in sorted(offered.items()):
        if tid not in fitted:
            result.never_fit[tid] = sorted(pids)
            result.problems.append('%s (in %s) never fits where its pool is offered' % (tid, ', '.join(sorted(pids))))
    return result


def _run(s, start_tid, start_proj, start_rot, shape, pool, max_distance, size, start_height, ground, result,
         offered, fitted, start_jigsaw):
    # The start piece: layer 1 - start_height sits at the ground's first free layer (vanilla: ground level delta 1).
    origin = (0, ground - 1 + start_height, 0)
    if start_jigsaw:
        for j in s.jigsaws:
            if j[3] == _id(start_jigsaw):
                r = rotate_pos(j[0], start_rot)
                origin = (-r[0], origin[1], -r[2])
                break
    box = box_of(origin, s.size, start_rot)
    start = Piece(start_tid, origin, start_rot, 0, (start_tid, start_proj), box)
    cx = (box[0][0] + box[1][0] + 1) // 2
    cz = (box[0][2] + box[1][2] + 1) // 2
    cy = box[0][1] + 1
    limit = ((cx - max_distance, max(cy - max_distance, -64 + 7), cz - max_distance),
             (cx + max_distance, min(cy + max_distance, 319), cz + max_distance))
    pieces = [start]
    queue = deque([start])
    tag = '%s %s' % (start_tid, start_rot)
    while queue:
        parent = queue.popleft()
        pshape = shape(parent.template)
        for (jpos, jfront, jtop, jname, jtarget, jpool, jjoint) in pshape.jigsaws:
            if _id(jpool) == 'minecraft:empty':
                continue
            p = pool(jpool)
            if p is None:
                continue
            wpos = tuple(parent.origin[i] + rotate_pos(jpos, parent.rotation)[i] for i in range(3))
            front = rotate_dir(jfront, parent.rotation)
            top = rotate_dir(jtop, parent.rotation)
            step = _STEP[front]
            target = (wpos[0] + step[0], wpos[1] + step[1], wpos[2] + step[2])
            internal = contains(parent.box, target)
            candidates = []
            if parent.depth != size:
                candidates += _elements(p)
            fallback = p.get('fallback', 'minecraft:empty')
            if _id(fallback) != 'minecraft:empty':
                fp = pool(fallback)
                if fp:
                    candidates += _elements(fp)
            for (tid, proj, _) in candidates:
                if tid is not None:
                    offered.setdefault(tid, set()).add(_id(jpool))
            placed = None
            for (tid, proj, _) in candidates:
                if tid is None:
                    break
                cs = shape(tid)
                if cs is None:
                    continue
                fit = _fit(cs, tid, proj, parent, pshape, jpos, jtop, jtarget, jjoint, front, top, target, internal,
                           pieces, limit, ground)
                if fit is not None:
                    fitted.add(tid)
                    if placed is None:
                        placed = fit
            if placed is None:
                if any(tid is not None for tid, _, _ in candidates) and not any(tid is None for tid, _, _ in candidates):
                    result.problems.append('%s: nothing from %s attaches to %s of %s at %s' % (
                        tag, _id(jpool), jname, parent.template, wpos))
                continue
            corigin, crot, tid, proj, cbox = placed
            child = Piece(tid, corigin, crot, parent.depth + 1, (tid, proj), cbox, parent, internal)
            pieces.append(child)
            if child.depth <= size:
                queue.append(child)
    return pieces


def _fit(cs, tid, proj, parent, pshape, jpos, jtop, jtarget, jjoint, front, top, target, internal, pieces, limit,
         ground):
    rigid = parent.element[1] == 'rigid' and proj == 'rigid'
    for rot in ROTATIONS:
        for (cpos, cfront, ctop, cname, ctarget, cpool, cjoint) in cs.jigsaws:
            if _id(cname) != _id(jtarget):
                continue
            wf = rotate_dir(cfront, rot)
            wt = rotate_dir(ctop, rot)
            if wf != _OPPOSITE[front]:
                continue
            if front in ('up', 'down') and jjoint != 'rollable' and wt != top:
                continue
            r = rotate_pos(cpos, rot)
            if rigid:
                oy = target[1] - r[1]
            else:
                oy = ground - r[1]
            corigin = (target[0] - r[0], oy, target[2] - r[2])
            cbox = box_of(corigin, cs.size, rot)
            if internal:
                if not inside(cbox, parent.box):
                    continue
                others = [q for q in pieces if q.parent is parent and q.inner]
            else:
                if not inside(cbox, limit):
                    continue
                others = pieces
            if any(intersects(cbox, q.box) for q in others):
                continue
            if front not in ('up', 'down'):
                if not pshape.open_above.get(jpos, True) or not cs.open_above.get(cpos, True):
                    continue
            return corigin, rot, tid, proj, cbox
    return None


def structure_args(structure_id):
    """(start_pool, max_distance, size, start_height) read from a structure JSON, for assemble(**...)."""
    ns, p = _path(structure_id)
    with open(os.path.join(DATA, ns, 'worldgen', 'structure', p + '.json')) as f:
        s = json.load(f)
    sh = s.get('start_height', {})
    height = sh.get('absolute', 0) if isinstance(sh, dict) else 0
    return dict(start_pool=s['start_pool'], max_distance=s.get('max_distance_from_center', 64), size=s.get('size', 7),
                start_height=height, start_jigsaw=s.get('start_jigsaw_name'))


def all_structures():
    """Every jigsaw structure of the mod, as ids."""
    out = []
    base = os.path.join(DATA, 'oldrepublic', 'worldgen', 'structure')
    for path in sorted(glob.glob(os.path.join(base, '**', '*.json'), recursive=True)):
        with open(path) as f:
            if json.load(f).get('type') == 'minecraft:jigsaw':
                out.append('oldrepublic:' + os.path.relpath(path, base)[:-5].replace(os.sep, '/'))
    return out
