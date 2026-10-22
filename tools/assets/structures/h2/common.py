"""Shared helpers for worker H2's Chapter Five hubs (hub/saltgate, hub/holdfast).

Every helper draws into a structlib Template with template-local coordinates (x east, y up, z south).
All randomness comes from the template's rng or from nature.Noise(seed), so a rebuild is byte-identical.
The checks at the bottom walk the finished template the way a player would (feet and head clear, a
block below, one block up or three down per step) so a build fails loudly when a hub person, a pad bay
or a terminal cannot be reached from the landing field.
"""
import os
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, '..', '..')))

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402

DIRS = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
LEFT = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}
RIGHT = {v: k for k, v in LEFT.items()}


# ---------------------------------------------------------------- states

def stair(name, facing, half='bottom', shape='straight'):
    return S(name, facing=facing, half=half, shape=shape, waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def log(name, axis='y'):
    return S(name, axis=axis)


def trapdoor(name, facing, half='bottom', open_=False):
    return S(name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def lantern(name='lantern', hanging=False):
    return S(name, hanging=hanging, waterlogged=False)


def chain(name='iron_chain', axis='y'):
    return S(name, axis=axis, waterlogged=False)


def mix(choices, seed, scale=4.0):
    """A callable (x, y, z) -> state choosing from [(state, weight)] in coherent noise patches."""
    return palettes.Palette(m=choices).patches('m', seed=seed, scale=scale)


def rmix(choices, rng):
    """A callable (x, y, z) -> state choosing per cell from [(state, weight)]."""
    return palettes.Palette(m=choices).picker('m', rng)


def pick(rng, choices):
    total = sum(w for _, w in choices)
    r = rng.random() * total
    for s, w in choices:
        r -= w
        if r <= 0:
            return s
    return choices[-1][0]


def call(state, x, y, z):
    return state(x, y, z) if callable(state) else state


# ---------------------------------------------------------------- cells and boxes

def fill(t, x0, y0, z0, x1, y1, z1, state, only_empty=False):
    return t.fill(x0, y0, z0, x1, y1, z1, state, only_empty=only_empty)


def put(t, x, y, z, state):
    t.set(x, y, z, call(state, x, y, z))


def put_if_air(t, x, y, z, state):
    if t.is_air(x, y, z):
        t.set(x, y, z, call(state, x, y, z))


def walls(t, x0, y0, z0, x1, y1, z1, state):
    shapes.walls(t, x0, y0, z0, x1, y1, z1, state)


def ring_cells(x0, z0, x1, z1):
    """The border cells of a rectangle, in a fixed order."""
    out = []
    for x in range(x0, x1 + 1):
        out.append((x, z0))
    for z in range(z0 + 1, z1 + 1):
        out.append((x1, z))
    for x in range(x1 - 1, x0 - 1, -1):
        out.append((x, z1))
    for z in range(z1 - 1, z0, -1):
        out.append((x0, z))
    return out


def opening(t, x0, y0, z0, x1, y1, z1):
    """Clear a doorway or window box to air."""
    t.air(x0, y0, z0, x1, y1, z1)


def arch_top(t, x0, x1, z0, z1, y, stair_name):
    """Round the two upper corners of a doorway (x0..x1 or z0..z1 wide, its top row at y) with
    upside-down stairs, so a square opening reads as an arch."""
    if z0 == z1 or (x1 - x0) >= (z1 - z0):   # the opening spans x: the wall runs along x
        for z in range(z0, z1 + 1):
            t.set(x0, y, z, stair(stair_name, 'east', 'top'))
            t.set(x1, y, z, stair(stair_name, 'west', 'top'))
    else:
        for x in range(x0, x1 + 1):
            t.set(x, y, z0, stair(stair_name, 'south', 'top'))
            t.set(x, y, z1, stair(stair_name, 'north', 'top'))


# ---------------------------------------------------------------- lights

def hang(t, x, y, z, drop=1, lamp='lantern', chain_name='iron_chain'):
    """A lantern hanging `drop` cells below the ceiling cell at y + drop: chains then the lantern at y."""
    for k in range(1, drop):
        t.set(x, y + k, z, chain(chain_name))
    t.set(x, y, z, lantern(lamp, hanging=True))


def lamp_post(t, x, y, z, height, post, lamp='lantern', cap=None):
    """A post `height` tall standing on the cell below y, a lantern on top."""
    for k in range(height):
        t.set(x, y + k, z, call(post, x, y + k, z))
    if cap is not None:
        t.set(x, y + height, z, cap)
        t.set(x, y + height + 1, z, lantern(lamp))
    else:
        t.set(x, y + height, z, lantern(lamp))


def light_rooms(t, regions, level=8):
    """Invisible light over the dark floors of each interior region (x0, y0, z0, x1, y1, z1)."""
    lit = []
    for r in regions:
        lit += mod.light_fill(t, r, level=level, strength=13)
    return lit


# ---------------------------------------------------------------- furniture

def bed(t, x, y, z, facing, colour='white'):
    """A bed whose foot is at (x, y, z) and whose head lies one cell toward `facing`."""
    dx, dz = DIRS[facing]
    t.set(x, y, z, S('%s_bed' % colour, facing=facing, part='foot', occupied=False))
    t.set(x + dx, y, z + dz, S('%s_bed' % colour, facing=facing, part='head', occupied=False))


def seat(t, x, y, z, looks, stair_name):
    """A stair chair; the sitter looks toward `looks`."""
    t.set(x, y, z, stair(stair_name, OPP[looks]))


def table(t, x, y, z, top):
    t.set(x, y, z, slab(top, 'top') if top.endswith('_slab') else top)


def crates(t, cells, rng, choices):
    for (x, y, z) in cells:
        t.set(x, y, z, pick(rng, choices))


def barrel(t, x, y, z, facing='up'):
    t.set(x, y, z, S('barrel', facing=facing, open=False))


def pot(t, x, y, z, facing='north'):
    t.set(x, y, z, S('decorated_pot', facing=facing, cracked=False, waterlogged=False))


def carpet_run(t, x0, z0, x1, z1, y, colour):
    t.fill(x0, y, z0, x1, y, z1, '%s_carpet' % colour)


# ---------------------------------------------------------------- awnings and cloth

def awning(t, a0, a1, front, y, out, cloth, depth=2, poles=(), pole_state=None, street=3, slope=False):
    """A cloth awning along a wall. The wall runs along x when `out` is north or south (cells
    (a, y, front + i * dz)) and along z when `out` is east or west (cells (front + i * dx, y, a)),
    for a in a0..a1 and rows i in 0..depth-1. cloth(k, i) gives the block of the k-th cell along the
    span in row i (stripes). slope lowers the awning half a block per row with plain canvas slabs.
    poles: span indices that get a pole from the outer row down to the street (feet level `street`)."""
    dx, dz = DIRS[out]
    for i in range(depth):
        for k, a in enumerate(range(a0, a1 + 1)):
            if slope:
                yy = y - i // 2
                st = slab('or:canvas_slab', 'top' if i % 2 == 0 else 'bottom')
            else:
                yy = y
                st = cloth(k, i)
            if dz != 0:
                t.set(a, yy, front + dz * i, st)
            else:
                t.set(front + dx * i, yy, a, st)
    if poles and pole_state:
        i = depth - 1
        top = (y - i // 2) if slope else y
        for k in poles:
            a = a0 + k
            for py in range(street, top):
                if dz != 0:
                    t.set(a, py, front + dz * i, pole_state)
                else:
                    t.set(front + dx * i, py, a, pole_state)


def stripes(a='or:canvas', b='or:canvas_crimson', width=2):
    return lambda k, i: a if (k // width) % 2 == 0 else b


def banner(t, x, y, z, facing, colour):
    """A plain hanging banner on a wall (no patterns: the art rules allow plain colours)."""
    mod.banner(t, x, y, z, facing, base=colour, patterns=(), wall=True)


# ---------------------------------------------------------------- stairs

def steps(t, x, y, z, direction, length, name, width=1, side='right', fill_below=None):
    """A straight flight climbing toward `direction`: step i at (x, y + i, z) + i * direction."""
    shapes.stair_run(t, x, y, z, direction, length, name, fill=fill_below, width=width, side=side)


# ---------------------------------------------------------------- checks

def _walk(state):
    return state is None or mod.walk_through(state)


def _floor(state):
    return state is not None and not mod.walk_through(state) and B.name_of(state) not in (
        'minecraft:water', 'minecraft:lava') and not _tall(state)


def _tall(state):
    """Blocks a person cannot step onto (fences and walls are 1.5 high)."""
    n = B.name_of(state)
    return n.endswith('_fence') or n.endswith('_wall') or n.endswith('_fence_gate') or n.endswith('_bars') \
        or n.endswith('_pane') or n == 'oldrepublic:holdfast_gate'


def standable(t, x, y, z):
    if not t.inside(x, y, z) or not t.inside(x, y + 1, z) or y < 1:
        return False
    return _walk(t.get(x, y, z)) and _walk(t.get(x, y + 1, z)) and _floor(t.get(x, y - 1, z))


def _ladder(state):
    return state is not None and B.name_of(state) == 'minecraft:ladder'


def reachable(t, start, region=None):
    """Every feet cell reachable from `start` (a standable feet cell): four-way steps, up one or down
    three, and up or down ladders. Doors count as open. region (x0, z0, x1, z1) bounds the walk."""
    def passable(s):
        if s is None:
            return True
        n = B.name_of(s)
        return mod.walk_through(s) or n.endswith('_door') or n == 'oldrepublic:hub_spawner' or _ladder(s)

    def stand(x, y, z):
        if not t.inside(x, y, z) or not t.inside(x, y + 1, z) or y < 1:
            return False
        feet = t.get(x, y, z)
        return passable(feet) and passable(t.get(x, y + 1, z)) and (_floor(t.get(x, y - 1, z)) or _ladder(feet))

    seen = {start}
    q = deque([start])
    x0, z0, x1, z1 = region or (0, 0, t.sx - 1, t.sz - 1)
    while q:
        x, y, z = q.popleft()
        nxt = []
        if _ladder(t.get(x, y, z)) or _ladder(t.get(x, y - 1, z)):
            for ny in (y + 1, y - 1):
                if stand(x, ny, z) or (passable(t.get(x, ny, z)) and passable(t.get(x, ny + 1, z))
                                       and (_ladder(t.get(x, ny, z)) or _ladder(t.get(x, ny - 1, z)))):
                    nxt.append((x, ny, z))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (x0 <= nx <= x1 and z0 <= nz <= z1):
                continue
            for ny in (y, y + 1, y - 1, y - 2, y - 3):
                if ny == y + 1 and not passable(t.get(x, y + 2, z)):
                    continue
                if stand(nx, ny, nz):
                    if ny < y and not all(passable(t.get(nx, k, nz)) for k in range(ny, y + 2)):
                        break
                    nxt.append((nx, ny, nz))
                    break
        for p in nxt:
            if p not in seen:
                seen.add(p)
                q.append(p)
    return seen


NOT_STURDY = ('air', 'bars', 'pane', 'fence', 'slab', 'stairs', 'ladder', 'lantern', 'chain', 'carpet', 'trapdoor',
              'door', 'hub_spawner', 'light', 'wall', 'rod', 'banner', 'torch', 'candle', 'button', 'scaffolding',
              'glass', 'leaves', 'sapling', 'grass', 'bush', 'cactus', 'water')


def check_ladders(t):
    """Every ladder hangs on a full block (a ladder on air, bars or glass breaks at the first update)."""
    bad = []
    for (x, y, z), st in sorted(t.cells.items()):
        if B.name_of(st) != 'minecraft:ladder':
            continue
        dx, dz = DIRS[B.parse(st)[1]['facing']]
        held = t.cells.get((x - dx, y, z - dz))
        name = B.name_of(held) if held is not None else 'unset'
        if held is None or any(w in name.split(':')[1] for w in NOT_STURDY):
            bad.append(((x, y, z), name))
    if bad:
        raise SystemExit('%s: %d ladder(s) on nothing: %s' % (t.name, len(bad), bad[:12]))


def check_room(t, name, half=1, height=3):
    """A large person's anchor: every cell within `half` around it, `height` high, is free to stand in."""
    x, y, z = t.anchors[name][0]
    for dx in range(-half, half + 1):
        for dz in range(-half, half + 1):
            for dy in range(height):
                st = t.cells.get((x + dx, y + dy, z + dz))
                if st is None or not (mod.walk_through(st) or B.name_of(st) == 'oldrepublic:hub_spawner'):
                    raise SystemExit('%s: %s has no room at %s (%s)' % (t.name, name, (x + dx, y + dy, z + dz), st))


def check_people(t, start, names, block_anchors=()):
    """Raise unless every anchor in `names` (feet or spawner cells) is a reachable feet cell, and every
    block anchor (terminals) has a reachable feet cell beside it."""
    seen = reachable(t, start)
    bad = []
    for n in names:
        for c in t.anchors.get(n, []):
            if tuple(c) not in seen:
                bad.append('%s %s' % (n, c))
    for n in block_anchors:
        for (x, y, z) in t.anchors.get(n, []):
            if not any((x + dx, y, z + dz) in seen for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                bad.append('%s %s (no reachable cell beside it)' % (n, [x, y, z]))
    if bad:
        raise SystemExit('%s: not reachable from %s: %s' % (t.name, list(start), ', '.join(bad)))
    return seen


def check_pad(t, cell, half=3, height=4, deck=5):
    """A pad bay: the 7 x 4 x 7 landing box is air, the sky above the bay is open to the template's
    top, and the deck under the box is flat full blocks."""
    x, y, z = cell
    bad = []
    for dx in range(-deck, deck + 1):
        for dz in range(-deck, deck + 1):
            below = t.get(x + dx, y - 1, z + dz)
            if below is None or mod.walk_through(below) or B.name_of(below).endswith('_slab') \
                    or B.name_of(below).endswith('_stairs'):
                bad.append('deck %s' % [x + dx, y - 1, z + dz])
            for yy in range(y, t.sy):
                s = t.get(x + dx, yy, z + dz)
                if s is not None and B.name_of(s) not in ('minecraft:air', 'minecraft:cave_air'):
                    bad.append('sky %s %s' % ([x + dx, yy, z + dz], s))
    if bad:
        raise SystemExit('%s: pad bay %s is not clear: %s' % (t.name, list(cell), ', '.join(bad[:12])))


def report(t, label=''):
    solid = t.count()
    air = sum(1 for s in t.cells.values() if B.name_of(s) in ('minecraft:air', 'minecraft:cave_air'))
    lights = sum(1 for s in t.cells.values() if B.name_of(s) == 'minecraft:light')
    print('%s %s size %s: %d not air (%d invisible lights), %d air' % (label, t.name, list(t.size), solid, lights, air))
