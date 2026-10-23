"""Shared helpers for worker O1's generators (the overworld landmarks: jedi_ruin, sith_tomb, wreck_field,
crystal_cave). Deterministic: every random choice goes through a seeded rng or nature.Noise.

Run the family scripts (or build_all.py) from anywhere:
    python3 tools/assets/structures/o1/build_all.py [--preview DIR]
"""
import math
import os
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, '..', '..')))  # tools/assets

from structlib.template import Template  # noqa: E402,F401
from structlib.blocks import S  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402,F401
from structlib import shapes, nature, palettes, mod, meta, jigsaw  # noqa: E402,F401
from structlib import worldgen as W  # noqa: E402,F401
from structlib.nature import Noise  # noqa: E402
from structlib.palettes import Palette  # noqa: E402

BASE = os.path.join(HERE, 'base')
AIR = 'minecraft:air'

PREVIEW_DIR = None  # set by main(): a scratch folder for preview sheets (never the repository)


def args(argv=None):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', default=None, help='folder for preview PNGs (scratch, never the repo)')
    ap.add_argument('--only', default=None, help='build only template names containing this text')
    a = ap.parse_args(argv)
    global PREVIEW_DIR
    PREVIEW_DIR = a.preview
    return a


def wanted(a, name):
    return a.only is None or a.only in name


def save(t, tid):
    """Save a template (and its preview when a preview folder is set)."""
    png = None
    if PREVIEW_DIR:
        png = os.path.join(PREVIEW_DIR, tid.replace('/', '__') + '.png')
    t.save(tid, preview=png)
    return t


def load_base(tid, seed=0):
    """A pristine copy of a template this worker reworks in place (tools/assets/structures/o1/base), so a
    rerun never transforms an already transformed template."""
    t = Template.load(os.path.join(BASE, tid), seed=seed)
    t.name = tid
    return t


# ---------------------------------------------------------------- materials

def mix(seed, options, scale=4.0):
    """A callable (x, y, z) -> state choosing among weighted options in noise patches."""
    return Palette(m=options).patches('m', seed=seed, scale=scale)


def name(state):
    return B.name_of(state) if state else None


def is_airish(t, x, y, z):
    s = t.get(x, y, z)
    return s is None or name(s) in ('minecraft:air', 'minecraft:cave_air')


def put(t, x, y, z, state, only_empty=False):
    if only_empty and not is_airish(t, x, y, z):
        return False
    s = state(x, y, z) if callable(state) else state
    return t.set(x, y, z, s)


def air_up(t, x0, z0, x1, z1, y0, y1=None):
    """Explicit air in every unset cell of the box from y0 up (to the template top)."""
    y1 = t.sy - 1 if y1 is None else y1
    for x in range(max(0, min(x0, x1)), min(t.sx - 1, max(x0, x1)) + 1):
        for z in range(max(0, min(z0, z1)), min(t.sz - 1, max(z0, z1)) + 1):
            for y in range(max(0, y0), min(t.sy - 1, y1) + 1):
                if (x, y, z) not in t.cells:
                    t.cells[(x, y, z)] = AIR


def stairs(block, facing, half='bottom', shape='straight'):
    return S(block, facing=facing, half=half, shape=shape, waterlogged=False)


def slab(block, kind='bottom'):
    return S(block, type=kind, waterlogged=False)


def wall_block(block):
    return S(block, up=True, north='none', south='none', east='none', west='none', waterlogged=False)


def leaves(kind='oak'):
    return S('%s_leaves' % kind, persistent=True, distance=1, waterlogged=False)


def log(kind, axis='y'):
    return S('%s_log' % kind, axis=axis)


# ---------------------------------------------------------------- geometry

def dist_to_segment(p, a, b):
    """(distance from p to segment ab, position along it 0..1)."""
    ax, ay, az = a
    bx, by, bz = b
    px, py, pz = p
    vx, vy, vz = bx - ax, by - ay, bz - az
    ll = vx * vx + vy * vy + vz * vz
    u = 0.0 if ll == 0 else max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy + (pz - az) * vz) / ll))
    cx, cy, cz = ax + vx * u, ay + vy * u, az + vz * u
    return math.sqrt((px - cx) ** 2 + (py - cy) ** 2 + (pz - cz) ** 2), u


def tilted_cylinder(t, a, b, r, state, hollow=False, thick=1.0, only_empty=False, keep=None, cap=True):
    """A cylinder of radius r from point a to point b (floats, cell centres), at any angle.
    hollow leaves the inside alone (thick: shell thickness). keep(x, y, z, u) may veto a cell (breaks).
    cap False leaves the ends open."""
    xs = [a[0], b[0]]
    ys = [a[1], b[1]]
    zs = [a[2], b[2]]
    out = []
    for x in range(int(math.floor(min(xs) - r - 1)), int(math.ceil(max(xs) + r + 1)) + 1):
        for y in range(int(math.floor(min(ys) - r - 1)), int(math.ceil(max(ys) + r + 1)) + 1):
            for z in range(int(math.floor(min(zs) - r - 1)), int(math.ceil(max(zs) + r + 1)) + 1):
                d, u = dist_to_segment((x, y, z), a, b)
                if d > r:
                    continue
                # the perpendicular distance only counts inside the segment (u strictly inside or cap)
                if not cap and (u <= 0.0 or u >= 1.0):
                    continue
                if hollow and d <= r - thick and 0.0 < u < 1.0:
                    continue
                if keep is not None and not keep(x, y, z, u):
                    continue
                if put(t, x, y, z, state, only_empty):
                    out.append((x, y, z))
    return out


def ring_cells(cx, cz, r, thick=1.0):
    """Cells of a ring (outer radius r) around (cx, cz), sorted by angle."""
    out = []
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            d = math.hypot(x - cx, z - cz)
            if r - thick < d <= r + 0.25:
                out.append((math.atan2(z - cz, x - cx), x, z))
    out.sort()
    return [(x, z) for _, x, z in out]


def disk_cells(cx, cz, r):
    out = []
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            if math.hypot(x - cx, z - cz) <= r + 0.25:
                out.append((x, z))
    return out


def octagon_cells(cx, cz, r):
    """Cells of a filled octagon of 'radius' r (flat sides), centred on a cell or a cell corner."""
    out = []
    k = r * 0.41421356 + 0.5
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            dx, dz = abs(x - cx), abs(z - cz)
            if dx <= r and dz <= r and dx + dz <= r + k:
                out.append((x, z))
    return out


# ---------------------------------------------------------------- nature

def tree(t, x, y, z, kind='oak', height=6, rng=None, crown=2.6, log_kind=None, leaf_kind=None, lean=(0, 0),
         only_empty=True, roots=0):
    """A hand-placed tree with persistent leaves: trunk from (x, y, z) up `height`, a lumpy crown.
    lean (dx, dz) shifts the top of the trunk; roots spreads a few root logs at the base."""
    rng = rng or t.rng
    lk = log_kind or kind
    fk = leaf_kind or kind
    tx, tz = x, z
    top = (x, y + height - 1, z)
    for i in range(height):
        f = i / max(1, height - 1)
        tx = x + int(round(lean[0] * f))
        tz = z + int(round(lean[1] * f))
        put(t, tx, y + i, tz, log(lk), only_empty=False)
        top = (tx, y + i, tz)
    cx, cy, cz = top
    n = Noise(rng.randrange(1 << 30), 1.7)
    rr = crown
    for dx in range(-int(rr) - 2, int(rr) + 3):
        for dy in range(-2, int(rr) + 2):
            for dz in range(-int(rr) - 2, int(rr) + 3):
                d = math.sqrt(dx * dx + (dy * 1.4) ** 2 + dz * dz)
                if d <= rr + 0.8 * (n.at(cx + dx, cy + dy, cz + dz) - 0.35):
                    put(t, cx + dx, cy + dy + 1, cz + dz, leaves(fk), only_empty=only_empty)
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1))[:roots]:
        put(t, x + dx, y, z + dz, log(lk, 'x' if dx else 'z'), only_empty=True)
    return top


def bush(t, x, y, z, kind='oak', r=1.2, rng=None):
    rng = rng or t.rng
    for dx in range(-2, 3):
        for dy in range(0, 2):
            for dz in range(-2, 3):
                if math.sqrt(dx * dx + (dy * 1.6) ** 2 + dz * dz) <= r + rng.random() * 0.5:
                    put(t, x + dx, y + dy, z + dz, leaves(kind), only_empty=True)


SOIL = {'minecraft:grass_block', 'minecraft:moss_block', 'minecraft:podzol', 'minecraft:coarse_dirt',
        'minecraft:rooted_dirt', 'minecraft:dirt', 'minecraft:mud', 'minecraft:mycelium'}


def sprinkle(t, cells_xz, y_of, choices, density, rng, need_air=True, on=SOIL):
    """Place small plants on top of ground cells: cells_xz [(x, z)], y_of(x, z) -> ground top y (or None).
    Only on soil (on) so nothing grows out of paving."""
    total = sum(w for _, w in choices)
    for (x, z) in cells_xz:
        y = y_of(x, z)
        if y is None or rng.random() >= density:
            continue
        if need_air and not is_airish(t, x, y + 1, z):
            continue
        if name(t.get(x, y, z)) not in on:
            continue
        r = rng.random() * total
        for st, w in choices:
            r -= w
            if r <= 0:
                if 'half=lower' in st:
                    if is_airish(t, x, y + 2, z):
                        t.set(x, y + 1, z, st)
                        t.set(x, y + 2, z, st.replace('half=lower', 'half=upper'))
                else:
                    t.set(x, y + 1, z, st)
                break


PLANTS_FOREST = [('minecraft:short_grass', 10), ('minecraft:fern', 4), ('minecraft:leaf_litter[facing=north,segment_amount=3]', 3),
                 ('minecraft:bush', 2), ('minecraft:tall_grass[half=lower]', 2), ('minecraft:large_fern[half=lower]', 1),
                 ('minecraft:wildflowers[facing=east,flower_amount=3]', 1), ('minecraft:lily_of_the_valley', 1),
                 ('minecraft:moss_carpet', 2)]


# ---------------------------------------------------------------- walking

_PARTIAL = ('_slab', '_stairs')


def _kind(state):
    if state is None:
        return 'void'
    n, p = B.parse(state)
    if n in ('minecraft:air', 'minecraft:cave_air', 'minecraft:jigsaw', 'minecraft:light', 'oldrepublic:lumen_moss'):
        return 'air'
    if n.endswith('_slab'):
        return 'slab_' + p.get('type', 'bottom')
    if n.endswith('_stairs'):
        return 'stairs_' + p.get('half', 'bottom')
    if n.endswith(('_fence', '_wall', '_fence_gate')) or n in ('minecraft:iron_bars',) or n.endswith('_pane'):
        return 'tall'
    if mod.walk_through(state):
        return 'air'
    return 'solid'


def _clear(kind):
    return kind in ('air', 'void')


def stand_height(t, x, y, z, passable=()):
    """Height of a player's soles with feet in cell (x, y, z), or None if they cannot stand there."""
    def k(px, py, pz):
        if (px, py, pz) in passable:
            return 'air'
        return _kind(t.get(px, py, pz))
    feet = k(x, y, z)
    if feet == 'slab_bottom':
        h = y + 0.5
        need = (y + 1, y + 2)
    elif feet == 'stairs_bottom':
        h = y + 0.5
        need = (y + 1, y + 2)
    elif _clear(feet):
        below = k(x, y - 1, z)
        if below in ('solid', 'slab_top', 'stairs_bottom', 'stairs_top'):
            h = float(y)
        else:
            return None
        need = (y + 1,)
    else:
        return None
    for yy in need:
        if not _clear(k(x, yy, z)):
            return None
    return h


def reach(t, start, passable=(), limit=400000, max_up=0.5, max_down=1.0):
    """Feet cells a player can walk to from `start` without jumping (steps up at most half a block, or a
    stair), dropping at most `max_down`. Unset cells count as air. Returns {cell: height}."""
    passable = set(passable)
    h0 = stand_height(t, *start, passable=passable)
    if h0 is None:
        return {}
    seen = {tuple(start): h0}
    q = deque([tuple(start)])
    while q and len(seen) < limit:
        x, y, z = q.popleft()
        h = seen[(x, y, z)]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < t.sx and 0 <= nz < t.sz):
                continue
            for ny in (y, y + 1, y - 1, y - 2):
                if (nx, ny, nz) in seen or not (0 < ny < t.sy):
                    continue
                nh = stand_height(t, nx, ny, nz, passable=passable)
                if nh is None:
                    continue
                up = max_up
                # a run of stairs: each stair's back half is a full block, so the next stair is half a step
                if _kind(t.get(x, y, z)) == 'stairs_bottom' and _kind(t.get(nx, ny, nz)) == 'stairs_bottom':
                    up = 1.0
                if nh - h > up + 1e-6 or h - nh > max_down + 1e-6:
                    continue
                # head room over the edge when stepping up
                if nh > h and not _clear(_kind(t.get(x, int(math.floor(nh)) + 1, z))) and (x, int(math.floor(nh)) + 1, z) not in passable:
                    continue
                seen[(nx, ny, nz)] = nh
                q.append((nx, ny, nz))
                break
    return seen


def route_problems(t, start, goals, passable=(), label=''):
    r = reach(t, start, passable=passable)
    out = []
    if not r:
        out.append('%s start %s is not a place to stand' % (label, list(start)))
        return out
    for g in goals:
        if tuple(g) not in r:
            out.append('%s %s is not reachable from %s' % (label, list(g), list(start)))
    return out


# ---------------------------------------------------------------- freezing mechanism cells

def snapshot(t, cells):
    return {p: (t.cells.get(p), t.nbt.get(p)) for p in cells}


def restore(t, snap):
    for p, (s, nb) in snap.items():
        if s is None:
            t.cells.pop(p, None)
            t.nbt.pop(p, None)
        else:
            t.cells[p] = s
            if nb is not None:
                t.nbt[p] = nb
            else:
                t.nbt.pop(p, None)


def changed(t, snap):
    return sorted(p for p, (s, nb) in snap.items() if t.cells.get(p) != s or t.nbt.get(p) != nb)


def neighbourhood(t, names, radius=1):
    """Every cell within `radius` (Chebyshev) of a cell whose block is in `names`."""
    names = {B.full_name(n) for n in names}
    out = set()
    for p, s in t.cells.items():
        if B.name_of(s) in names:
            x, y, z = p
            for dx in range(-radius, radius + 1):
                for dy in range(-radius, radius + 1):
                    for dz in range(-radius, radius + 1):
                        q = (x + dx, y + dy, z + dz)
                        if t.inside(*q):
                            out.add(q)
    return out


def light_report(t, region=None, level=8):
    lit = mod.light_map(t)
    floors = mod.floor_cells(t, region)
    dark = [p for p in floors if lit.get(p, 0) < level]
    return len(floors), dark


def count_solid(t):
    return t.count()


# ---------------------------------------------------------------- whole-structure checks

def compose(run_pieces, templates):
    """Paste the pieces of one jigsaw run (unrotated start) into one big template; returns (template,
    offset) where offset maps world cells of the run to the composed template's cells. Jigsaw blocks are
    turned into their final air."""
    xs = [p.box[0][0] for p in run_pieces] + [p.box[1][0] for p in run_pieces]
    ys = [p.box[0][1] for p in run_pieces] + [p.box[1][1] for p in run_pieces]
    zs = [p.box[0][2] for p in run_pieces] + [p.box[1][2] for p in run_pieces]
    off = (-min(xs), -min(ys), -min(zs))
    big = Template(max(xs) - min(xs) + 1, max(ys) - min(ys) + 1, max(zs) - min(zs) + 1)
    for p in run_pieces:
        tid = p.template.split(':', 1)[1]
        src = templates[tid]
        rot = {'none': 0, 'clockwise_90': 1, 'clockwise_180': 2, 'counterclockwise_90': 3}[p.rotation]
        big.paste(src, p.box[0][0] + off[0], p.box[0][1] + off[1], p.box[0][2] + off[2], rotation=rot)
    for q, s in list(big.cells.items()):
        if B.name_of(s) == 'minecraft:jigsaw':
            big.cells[q] = AIR
    return big, off


def load_saved(tid):
    t = Template.load(tid)
    return t


def crop_preview(t, box, png, views='se,sw', width=1400, plans=None):
    """Render a cropped region of a template (scratch previews only). box = (x0, y0, z0, x1, y1, z1)."""
    from structlib import preview as P
    x0, y0, z0, x1, y1, z1 = box
    sub = Template(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
    for (x, y, z), s in t.cells.items():
        if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1:
            sub.cells[(x - x0, y - y0, z - z0)] = s
    data = sub.to_nbt()
    import subprocess
    from structlib import nbtio
    tmp = png + '.nbt'
    nbtio.dump(data, tmp)
    args = ['python3', os.path.join(HERE, '..', '..', 'structlib', 'preview.py'), tmp, '-o', png, '--views', views,
            '--width', str(width)]
    if plans:
        args += ['--plans', plans]
    subprocess.run(args, check=True, capture_output=True)
    os.remove(tmp)
    return png


def fix_vines(t):
    """Remove vines that hang from nothing (their wall went in a later pass), top down."""
    sides = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
    changed = True
    while changed:
        changed = False
        for (x, y, z), s in sorted(t.cells.items(), key=lambda kv: -kv[0][1]):
            if name(s) != 'minecraft:vine':
                continue
            n, props = B.parse(s)
            ok = False
            for d, (dx, dz) in sides.items():
                if props.get(d) == 'true' and not is_airish(t, x + dx, y, z + dz) and \
                        not mod.walk_through(t.get(x + dx, y, z + dz)):
                    ok = True
            above = t.get(x, y + 1, z)
            if not ok and not (above and name(above) == 'minecraft:vine'):
                t.cells[(x, y, z)] = AIR
                changed = True


# ---------------------------------------------------------------- harvestable resources

# The spec (docs/MILESTONE_3.md 3.2) counts each family's harvestable resource per structure: 10 to 14
# lumen moss per Watchers' Rest, 8 to 10 funerary urns on a tomb's main route (gate, processional, burial
# chamber), 6 to 8 salvage piles per wreck field. Decoration must not add more of them.
RESOURCES = ('oldrepublic:lumen_moss', 'oldrepublic:funerary_urn', 'oldrepublic:wreck_salvage')


def budget(t, block, keep, replacement, prefer=()):
    """Keep at most `keep` cells of the resource `block`: those in `prefer` first, then the rest in a fixed
    order (a hash of the position, so they spread over the template); every other one becomes
    `replacement`. Returns the cells kept."""
    full = B.full_name(block)
    cells = sorted(p for p, s in t.cells.items() if name(s) == full)
    rest = sorted((p for p in cells if p not in prefer),
                  key=lambda p: ((p[0] * 73856093) ^ (p[1] * 19349663) ^ (p[2] * 83492791)) & 0xffffff)
    order = [p for p in prefer if p in cells] + rest
    for p in order[keep:]:
        t.set(*p, replacement)
    return order[:keep]


def resource_count(t, block):
    full = B.full_name(block)
    return sum(1 for s in t.cells.values() if name(s) == full)
