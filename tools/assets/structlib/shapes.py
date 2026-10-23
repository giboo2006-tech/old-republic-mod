"""Geometric building primitives that draw into a Template.

Every function takes the Template first. A `state` argument may be a state string or a callable
f(x, y, z) -> state (for example ``pal.picker('wall', t.rng)``), so one call can mix weathered
variants. Corners are inclusive. Directions are 'north' (-z), 'south' (+z), 'east' (+x), 'west' (-x).
"""
import math

from . import blocks as B


def _put(t, x, y, z, state, only_empty=False):
    if only_empty and not t.is_air(x, y, z):
        return
    s = state(x, y, z) if callable(state) else state
    t.set(x, y, z, s)


# ---------------------------------------------------------------- boxes

def box(t, x0, y0, z0, x1, y1, z1, state, only_empty=False):
    return t.fill(x0, y0, z0, x1, y1, z1, state, only_empty=only_empty)


def walls(t, x0, y0, z0, x1, y1, z1, state):
    """The four side walls of a box (no floor, no ceiling)."""
    xa, xb = sorted((x0, x1))
    za, zb = sorted((z0, z1))
    t.fill(xa, y0, za, xb, y1, za, state)
    t.fill(xa, y0, zb, xb, y1, zb, state)
    t.fill(xa, y0, za, xa, y1, zb, state)
    t.fill(xb, y0, za, xb, y1, zb, state)


def room(t, x0, y0, z0, x1, y1, z1, wall, floor=None, ceiling=None, interior=B.AIR):
    """A closed room: walls, floor at y0, ceiling at y1, air inside (unless interior is None)."""
    walls(t, x0, y0, z0, x1, y1, z1, wall)
    if floor is not None:
        t.fill(x0, y0, z0, x1, y0, z1, floor)
    if ceiling is not None:
        t.fill(x0, y1, z0, x1, y1, z1, ceiling)
    if interior is not None:
        t.fill(min(x0, x1) + 1, min(y0, y1) + 1, min(z0, z1) + 1, max(x0, x1) - 1, max(y0, y1) - 1,
               max(z0, z1) - 1, interior)


def frame(t, x0, y0, z0, x1, y1, z1, state):
    """The 12 edges of a box (posts and beams)."""
    xa, xb = sorted((x0, x1))
    ya, yb = sorted((y0, y1))
    za, zb = sorted((z0, z1))
    for x in (xa, xb):
        for z in (za, zb):
            t.fill(x, ya, z, x, yb, z, state)
    for y in (ya, yb):
        for z in (za, zb):
            t.fill(xa, y, z, xb, y, z, state)
        for x in (xa, xb):
            t.fill(x, y, za, x, y, zb, state)


# ---------------------------------------------------------------- lines and paths

def line(t, p0, p1, state, only_empty=False):
    """A 3D line of cells from p0 to p1 (both included), with no diagonal gaps."""
    x0, y0, z0 = p0
    x1, y1, z1 = p1
    n = max(abs(x1 - x0), abs(y1 - y0), abs(z1 - z0))
    if n == 0:
        _put(t, x0, y0, z0, state, only_empty)
        return
    last = None
    for i in range(n + 1):
        f = i / n
        p = (round(x0 + (x1 - x0) * f), round(y0 + (y1 - y0) * f), round(z0 + (z1 - z0) * f))
        if last is not None and abs(p[0] - last[0]) + abs(p[2] - last[2]) == 2 and p[1] == last[1]:
            _put(t, p[0], p[1], last[2], state, only_empty)  # close a diagonal step
        _put(t, *p, state, only_empty)
        last = p


def path(t, points, y_of, width, state, rng=None, jitter=0.0, only_on=None):
    """A ground path through (x, z) points. y_of(x, z) gives the cell's y (the ground layer).

    width is the full width in cells; jitter (0..1) frays the edges. only_on: a set of block names
    the path may replace (None replaces anything)."""
    r = (width - 1) / 2.0
    for (ax, az), (bx, bz) in zip(points, points[1:]):
        n = max(1, int(math.hypot(bx - ax, bz - az) * 2))
        for i in range(n + 1):
            f = i / n
            cx, cz = ax + (bx - ax) * f, az + (bz - az) * f
            rr = r + (rng.uniform(-jitter, jitter) if rng and jitter else 0)
            for dx in range(int(-rr - 1), int(rr + 2)):
                for dz in range(int(-rr - 1), int(rr + 2)):
                    if dx * dx + dz * dz <= rr * rr + 0.25:
                        x, z = int(round(cx + dx)), int(round(cz + dz))
                        y = y_of(x, z)
                        if y is None or not t.inside(x, y, z):
                            continue
                        if only_on is not None and t.name_at(x, y, z) not in only_on:
                            continue
                        _put(t, x, y, z, state)


# ---------------------------------------------------------------- round shapes

def disk(t, cx, y, cz, r, state, hollow=False, thick=1, only_empty=False):
    """A filled (or ring) disk of radius r centred on (cx, cz); r may be fractional."""
    rr = r * r
    inner = (r - thick) ** 2
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            d = (x - cx) ** 2 + (z - cz) ** 2
            if d <= rr + 0.25 and (not hollow or d > inner + 0.25):
                _put(t, x, y, z, state, only_empty)


def cylinder(t, cx, y0, y1, cz, r, state, hollow=False, thick=1, only_empty=False):
    for y in range(min(y0, y1), max(y0, y1) + 1):
        disk(t, cx, y, cz, r, state, hollow=hollow, thick=thick, only_empty=only_empty)


def ellipsoid(t, cx, cy, cz, rx, ry, rz, state, hollow=False, thick=1, ymin=None, ymax=None, only_empty=False):
    """A filled or hollow ellipsoid; ymin/ymax clip it (a dome is ymin=cy)."""
    for y in range(int(cy - ry - 1), int(cy + ry + 2)):
        if (ymin is not None and y < ymin) or (ymax is not None and y > ymax):
            continue
        for z in range(int(cz - rz - 1), int(cz + rz + 2)):
            for x in range(int(cx - rx - 1), int(cx + rx + 2)):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                if d > 1.0:
                    continue
                if hollow:
                    rxi, ryi, rzi = max(0.5, rx - thick), max(0.5, ry - thick), max(0.5, rz - thick)
                    di = ((x - cx) / rxi) ** 2 + ((y - cy) / ryi) ** 2 + ((z - cz) / rzi) ** 2
                    if di <= 1.0:
                        continue
                _put(t, x, y, z, state, only_empty)


def dome(t, cx, y, cz, r, state, thick=1, height=None, interior=None):
    """A hemispherical (or squashed, with height) shell standing on y; interior fills the inside."""
    h = height or r
    if interior is not None:
        ellipsoid(t, cx, y, cz, r - thick, h - thick, r - thick, interior, ymin=y)
    ellipsoid(t, cx, y, cz, r, h, r, state, hollow=True, thick=thick, ymin=y)


def cone(t, cx, y0, cz, r, h, state, hollow=False):
    """A cone (or spire) from radius r at y0 to a point at y0 + h - 1."""
    for i in range(h):
        rr = r * (1 - i / max(1, h))
        if rr < 0.5:
            _put(t, int(round(cx)), y0 + i, int(round(cz)), state)
        else:
            disk(t, cx, y0 + i, cz, rr, state, hollow=hollow)


# ---------------------------------------------------------------- arches, pillars, stairs

def arch(t, a0, a1, y, plane, axis, rise, state, thick=1, depth=1, fill_above=None, top=None):
    """An arch spanning a0..a1 along `axis` ('x' or 'z'), springing at y, in the plane `plane` of the
    other axis (depth cells deep from there). rise is the height of the crown above y. The opening
    below the curve is cleared to air; fill_above (a state) fills between the curve and top."""
    span = abs(a1 - a0)
    c = (a0 + a1) / 2.0
    half = span / 2.0
    for a in range(min(a0, a1), max(a0, a1) + 1):
        u = (a - c) / max(0.5, half)
        curve = rise * math.sqrt(max(0.0, 1 - u * u))
        yc = y + int(round(curve))
        for d in range(depth):
            p = plane + d
            for k in range(thick):
                yy = yc + k
                _set_axis(t, axis, a, yy, p, state)
            for yy in range(y, yc):
                _set_axis(t, axis, a, yy, p, B.AIR)
            if fill_above is not None and top is not None:
                for yy in range(yc + thick, top + 1):
                    _set_axis(t, axis, a, yy, p, fill_above)


def _set_axis(t, axis, a, y, p, state):
    if axis == 'x':
        _put(t, a, y, p, state)
    else:
        _put(t, p, y, a, state)


def pillar(t, x, y0, y1, z, shaft, base=None, capital=None):
    t.fill(x, y0, z, x, y1, z, shaft)
    if base is not None:
        _put(t, x, y0, z, base)
    if capital is not None:
        _put(t, x, y1, z, capital)


def stair_run(t, x, y, z, direction, length, stair, fill=None, width=1, side='right'):
    """A straight staircase climbing toward `direction` for `length` steps, `width` wide (extending to
    the given side of the climb). stair is a block name ('stone_brick_stairs'); fill fills below each step."""
    dx, dz = B.step(direction)
    sx, sz = B.step(B.right_of(direction) if side == 'right' else B.left_of(direction))
    for i in range(length):
        for w in range(width):
            px, pz = x + dx * i + sx * w, z + dz * i + sz * w
            _put(t, px, y + i, pz, B.S(stair, facing=direction, half='bottom', shape='straight', waterlogged=False))
            if fill is not None:
                for yy in range(y, y + i):
                    _put(t, px, yy, pz, fill)
            for yy in range(y + i + 1, y + i + 4):
                if t.is_air(px, yy, pz):
                    t.set(px, yy, pz, B.AIR)


def spiral_stair(t, cx, y0, y1, cz, r, step_state, core=None, clear=True, start=0):
    """A spiral of steps (usually slabs or stairs) around a core column, one step per cell of height.

    r is the outer radius; step_state may be callable. The stairwell inside r is cleared to air."""
    turn = 0
    ring = []
    for x in range(int(cx - r), int(cx + r) + 1):
        for z in range(int(cz - r), int(cz + r) + 1):
            d = math.hypot(x - cx, z - cz)
            if 0.5 < d <= r + 0.3:
                ring.append((math.atan2(z - cz, x - cx), x, z))
    ring.sort()
    if clear:
        for y in range(y0, y1 + 1):
            for _, x, z in ring:
                t.set(x, y, z, B.AIR)
    for i, y in enumerate(range(y0, y1 + 1)):
        _, x, z = ring[(start + i * max(1, len(ring) // 8)) % len(ring)]
        for k in range(max(1, len(ring) // 8)):
            _, x, z = ring[(start + i * max(1, len(ring) // 8) + k) % len(ring)]
            _put(t, x, y, z, step_state)
        turn += 1
    if core is not None:
        t.fill(int(round(cx)), y0, int(round(cz)), int(round(cx)), y1, int(round(cz)), core)


# ---------------------------------------------------------------- roofs

def gable_roof(t, x0, z0, x1, z1, y, stair, body=None, slab=None, ridge='x', overhang=0, gable=None):
    """A gable roof over the rectangle, eaves at y. ridge 'x' runs the ridge along x (slopes face north
    and south). stair: a stairs block name; body fills under the slopes (None leaves them hollow);
    slab caps an odd-width ridge; gable fills the triangular end walls."""
    xa, xb = sorted((x0, x1))
    za, zb = sorted((z0, z1))
    if ridge == 'x':
        xa -= overhang
        xb += overhang
        za -= overhang
        zb += overhang
        w = zb - za + 1
        for i in range((w + 1) // 2):
            yy = y + i
            front, back = za + i, zb - i
            if front > back:
                break
            if front == back:
                t.fill(xa, yy, front, xb, yy, front, slab or B.S(stair.replace('_stairs', '_slab'), type='bottom'))
                break
            t.fill(xa, yy, front, xb, yy, front, B.S(stair, facing='south', half='bottom', shape='straight'))
            t.fill(xa, yy, back, xb, yy, back, B.S(stair, facing='north', half='bottom', shape='straight'))
            if body is not None and back - front > 1:
                t.fill(xa + overhang, yy, front + 1, xb - overhang, yy, back - 1, body)
            if gable is not None:
                for x in (xa + overhang, xb - overhang):
                    t.fill(x, yy, front + 1, x, yy, back - 1, gable)
    else:
        xa -= overhang
        xb += overhang
        za -= overhang
        zb += overhang
        w = xb - xa + 1
        for i in range((w + 1) // 2):
            yy = y + i
            front, back = xa + i, xb - i
            if front > back:
                break
            if front == back:
                t.fill(front, yy, za, front, yy, zb, slab or B.S(stair.replace('_stairs', '_slab'), type='bottom'))
                break
            t.fill(front, yy, za, front, yy, zb, B.S(stair, facing='east', half='bottom', shape='straight'))
            t.fill(back, yy, za, back, yy, zb, B.S(stair, facing='west', half='bottom', shape='straight'))
            if body is not None and back - front > 1:
                t.fill(front + 1, yy, za + overhang, back - 1, yy, zb - overhang, body)
            if gable is not None:
                for z in (za + overhang, zb - overhang):
                    t.fill(front + 1, yy, z, back - 1, yy, z, gable)


def hip_roof(t, x0, z0, x1, z1, y, stair, body=None, cap=None):
    """A hipped roof: every side slopes up; stair shapes at the corners are set by Template.finish()."""
    xa, xb = sorted((x0, x1))
    za, zb = sorted((z0, z1))
    i = 0
    while xa + i <= xb - i and za + i <= zb - i:
        yy = y + i
        ax, bx, az, bz = xa + i, xb - i, za + i, zb - i
        if ax == bx or az == bz:
            t.fill(ax, yy, az, bx, yy, bz, cap or B.S(stair.replace('_stairs', '_slab'), type='bottom'))
            break
        t.fill(ax, yy, az, bx, yy, az, B.S(stair, facing='south', half='bottom', shape='straight'))
        t.fill(ax, yy, bz, bx, yy, bz, B.S(stair, facing='north', half='bottom', shape='straight'))
        t.fill(ax, yy, az + 1, ax, yy, bz - 1, B.S(stair, facing='east', half='bottom', shape='straight'))
        t.fill(bx, yy, az + 1, bx, yy, bz - 1, B.S(stair, facing='west', half='bottom', shape='straight'))
        if body is not None and bx - ax > 1 and bz - az > 1:
            t.fill(ax + 1, yy, az + 1, bx - 1, yy, bz - 1, body)
        i += 1


def flat_roof(t, x0, z0, x1, z1, y, state, parapet=None, parapet_h=1):
    t.fill(x0, y, z0, x1, y, z1, state)
    if parapet is not None:
        walls(t, x0, y + 1, z0, x1, y + parapet_h, z1, parapet)


# ---------------------------------------------------------------- columns of terrain

def foundation(t, x0, z0, x1, z1, y, state, depth=6):
    """Footings below a floor at y: fills y-1 down to y-depth so a building never floats on a slope.
    In worldgen these cells go into the ground; runtime placement keeps them below the surface."""
    if depth <= 0 or y <= 0:
        return
    t.fill(x0, max(0, y - depth), z0, x1, y - 1, z1, state)
