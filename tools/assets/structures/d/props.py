"""Props and small builds shared by worker D's surface sites: canvas tents, crate stacks, lamp masts, scorch
fans, rubble, boulders and worn ground. Every function takes the Template first and template-local
coordinates; randomness comes from hash01(seed, ...) so a rebuild is identical.
"""
import math

from common import S, AIR, B, hash01, stairs, slab, wall, fence, chain, lantern, light, log, trapdoor, campfire


def put(t, x, y, z, state, only_air=False):
    if only_air:
        s = t.cells.get((x, y, z))
        if s is not None and B.name_of(s) not in ('minecraft:air', 'minecraft:cave_air'):
            return False
    return t.set(x, y, z, state)


def tent(t, x0, z0, w, d, y, ridge='z', canvas='or:canvas', side='or:canvas_stairs', cap='or:canvas_slab',
         trim=None, door='north', frame='minecraft:spruce_fence', floor=None, seed=0, sag=True):
    """A ridge tent over the rectangle x0..x0+w-1, z0..z0+d-1 with its floor cells at y (feet layer).
    ridge 'z' runs the ridge north-south (w should be odd); the door end is open. Returns the interior
    cells (feet layer)."""
    inside = []
    if ridge == 'z':
        half = w // 2
        cx = x0 + half
        for z in range(z0, z0 + d):
            for i in range(half + 1):
                yy = y + i
                for x, facing in ((x0 + i, 'east'), (x0 + w - 1 - i, 'west')):
                    if x == cx:
                        put(t, x, yy, z, cap if cap else canvas)
                    else:
                        st = trim if (trim and i == 0) else side
                        put(t, x, yy, z, stairs(st, facing) if st.endswith('_stairs') else st)
                for x in range(x0 + i + 1, x0 + w - 1 - i):
                    if x != cx or i < half:
                        put(t, x, yy, z, AIR)
                        if i == 0:
                            inside.append((x, yy, z))
        # end walls: the back closed, the door end open in the middle
        for zz, is_door in ((z0, door == 'north'), (z0 + d - 1, door == 'south')):
            for i in range(half):
                yy = y + i
                for x in range(x0 + i + 1, x0 + w - 1 - i):
                    if is_door and abs(x - cx) <= (1 if w >= 5 else 0) and i < 2:
                        continue
                    put(t, x, yy, zz, canvas)
        # poles at the door
        if frame:
            for zz in (z0, z0 + d - 1):
                pass
    else:
        half = d // 2
        cz = z0 + half
        for x in range(x0, x0 + w):
            for i in range(half + 1):
                yy = y + i
                for z, facing in ((z0 + i, 'south'), (z0 + d - 1 - i, 'north')):
                    if z == cz:
                        put(t, x, yy, z, cap if cap else canvas)
                    else:
                        st = trim if (trim and i == 0) else side
                        put(t, x, yy, z, stairs(st, facing) if st.endswith('_stairs') else st)
                for z in range(z0 + i + 1, z0 + d - 1 - i):
                    if z != cz or i < half:
                        put(t, x, yy, z, AIR)
                        if i == 0:
                            inside.append((x, yy, z))
        for xx, is_door in ((x0, door == 'west'), (x0 + w - 1, door == 'east')):
            for i in range(half):
                yy = y + i
                for z in range(z0 + i + 1, z0 + d - 1 - i):
                    if is_door and abs(z - cz) <= (1 if d >= 5 else 0) and i < 2:
                        continue
                    put(t, xx, yy, z, canvas)
    if floor:
        for (x, yy, z) in inside:
            put(t, x, yy - 1, z, floor(x, yy - 1, z) if callable(floor) else floor)
    return inside


def crates(t, x, y, z, kinds, seed=0, height=2):
    """A small stack of crates (full blocks) at (x, y, z): 1-3 high, varied."""
    h = 1 + int(hash01(seed, x, y, z) * height)
    for k in range(h):
        put(t, x, y + k, z, kinds[int(hash01(seed + 1, x, y + k, z) * len(kinds)) % len(kinds)])
    return h


def mast(t, x, y, z, height, head='oldrepublic:light_panel', pole=None, arm=None, toppled=None):
    """A lamp mast: a durasteel beam `height` tall with a lamp head (and an arm toward `arm`)."""
    pole = pole or S('oldrepublic:durasteel_beam', axis='y')
    if toppled:
        dx, dz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[toppled]
        axis = 'x' if dx else 'z'
        for k in range(height):
            put(t, x + dx * k, y, z + dz * k, S('oldrepublic:durasteel_beam', axis=axis))
        put(t, x + dx * height, y, z + dz * height, 'oldrepublic:light_panel_amber' if head else head)
        return
    for k in range(height):
        put(t, x, y + k, z, pole)
    put(t, x, y + height, z, head)
    if arm:
        dx, dz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[arm]
        put(t, x + dx, y + height, z + dz, head)


def scorch_fan(t, cx, cz, y_of, direction, length, spread, seed=0, density=0.55):
    """Scorch decals radiating from (cx, cz) toward `direction` ((dx, dz)), widening by `spread` per block.
    y_of(x, z) gives the feet cell or None."""
    dx, dz = direction
    n = 0
    for r in range(1, length + 1):
        half = int(r * spread) + 1
        for k in range(-half, half + 1):
            x = int(round(cx + dx * r + (-dz) * k))
            z = int(round(cz + dz * r + dx * k))
            y = y_of(x, z)
            if y is None:
                continue
            fall = 1.0 - r / (length + 1.0)
            if hash01(seed, x, y, z) < density * fall + 0.1:
                s = t.cells.get((x, y, z))
                if s is not None and B.name_of(s) in ('minecraft:air', 'minecraft:cave_air'):
                    t.set(x, y, z, 'oldrepublic:scorch_mark')
                    n += 1
    return n


def boulder(t, cx, y, cz, rx, ry, rz, state, seed=0):
    """A rough boulder (an ellipsoid with a noisy edge) sitting on y."""
    for x in range(int(cx - rx - 1), int(cx + rx + 2)):
        for z in range(int(cz - rz - 1), int(cz + rz + 2)):
            for yy in range(y, int(y + ry * 2 + 1)):
                d = ((x - cx) / rx) ** 2 + ((yy - y - ry * 0.6) / ry) ** 2 + ((z - cz) / rz) ** 2
                if d <= 1.0 - 0.35 * hash01(seed, x, yy, z):
                    put(t, x, yy, z, state(x, yy, z) if callable(state) else state)


def worn(t, x, y, z, options, seed=0):
    """Worn ground: replace the ground cell with one of [(state, weight)] by hash."""
    total = sum(w for _, w in options)
    r = hash01(seed, x, y, z) * total
    for st, w in options:
        r -= w
        if r <= 0:
            t.set(x, y, z, st)
            return st
    return None


def line_cells(a, b):
    """Cells of a 2D line from a to b (x, z), no diagonal gaps."""
    (x0, z0), (x1, z1) = a, b
    n = max(abs(x1 - x0), abs(z1 - z0))
    out = []
    last = None
    for i in range(n + 1):
        f = i / n if n else 0
        p = (int(round(x0 + (x1 - x0) * f)), int(round(z0 + (z1 - z0) * f)))
        if last and abs(p[0] - last[0]) + abs(p[1] - last[1]) == 2:
            out.append((p[0], last[1]))
        out.append(p)
        last = p
    return out
