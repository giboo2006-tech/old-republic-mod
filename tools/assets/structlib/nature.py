"""Age, ruin and nature: weathering, decay, rubble, overgrowth, scatter and drifts.

All randomness comes from the rng passed in (normally t.rng) or from Noise(seed), so results repeat.
Cells listed in t.anchors, jigsaw blocks and the mod's data blocks are never removed or replaced.
"""
import math

from . import blocks as B

PROTECTED = {'minecraft:jigsaw', 'oldrepublic:site_spawner', 'oldrepublic:hub_spawner', 'minecraft:chest',
             'minecraft:barrel', 'minecraft:trapped_chest'}


class Noise:
    """Deterministic value noise in 2D and 3D, values in [0, 1]."""

    def __init__(self, seed, scale=6.0):
        self.seed = seed & 0xFFFFFFFF
        self.scale = scale

    def _h(self, x, y, z):
        n = (x * 374761393 + y * 668265263 + z * 2147483647 + self.seed * 974711) & 0xFFFFFFFF
        n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
        return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0

    def at(self, x, y, z=0.0):
        x, y, z = x / self.scale, y / self.scale, z / self.scale
        x0, y0, z0 = math.floor(x), math.floor(y), math.floor(z)
        fx, fy, fz = x - x0, y - y0, z - z0
        fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)

        def lerp(a, b, f):
            return a + (b - a) * f
        c = [[[self._h(x0 + i, y0 + j, z0 + k) for k in (0, 1)] for j in (0, 1)] for i in (0, 1)]
        return lerp(lerp(lerp(c[0][0][0], c[0][0][1], fz), lerp(c[0][1][0], c[0][1][1], fz), fy),
                    lerp(lerp(c[1][0][0], c[1][0][1], fz), lerp(c[1][1][0], c[1][1][1], fz), fy), fx)


def _protected(t, p):
    s = t.cells.get(p)
    if s is None:
        return False
    if B.name_of(s) in PROTECTED:
        return True
    for cells in t.anchors.values():
        if list(p) in cells:
            return True
    return False


def _region(t, region):
    if region is None:
        return (0, 0, 0), (t.sx - 1, t.sy - 1, t.sz - 1)
    (x0, y0, z0), (x1, y1, z1) = region
    return (min(x0, x1), min(y0, y1), min(z0, z1)), (max(x0, x1), max(y0, y1), max(z0, z1))


def _cells_in(t, region):
    (x0, y0, z0), (x1, y1, z1) = _region(t, region)
    for p in sorted(t.cells, key=lambda q: (q[1], q[2], q[0])):
        if x0 <= p[0] <= x1 and y0 <= p[1] <= y1 and z0 <= p[2] <= z1:
            yield p


def weather(t, mapping, region=None, rng=None, noise=None, patchiness=0.0):
    """Swap blocks for aged variants. mapping: {block_name: [(variant_state, chance), ...]}.

    With noise (a Noise) and patchiness > 0, chances follow the noise so moss and cracks come in
    patches instead of salt and pepper (chance * (1 - patchiness + 2 * patchiness * noise))."""
    rng = rng or t.rng
    full = {B.full_name(k): v for k, v in mapping.items()}
    for p in list(_cells_in(t, region)):
        if _protected(t, p):
            continue
        s = t.cells[p]
        name, props = B.parse(s)
        opts = full.get(name)
        if not opts:
            continue
        k = 1.0
        if noise is not None and patchiness > 0:
            k = 1 - patchiness + 2 * patchiness * noise.at(*p)
        r = rng.random()
        acc = 0.0
        for variant, chance in opts:
            acc += chance * k
            if r < acc:
                vn, vp = B.parse(variant)
                merged = dict(props)
                merged.update(vp)
                # keep shape props (stairs facing etc.) when the variant is the same kind of block
                t.cells[p] = B.build(vn, merged if _same_kind(name, vn) else vp)
                break


def _same_kind(a, b):
    for suf in ('_stairs', '_slab', '_wall', '_fence'):
        if a.endswith(suf) and b.endswith(suf):
            return True
    return not any(a.endswith(s) for s in ('_stairs', '_slab', '_wall', '_fence'))


def exposed(t, p):
    """True when a cell touches an unset or air cell on any side."""
    x, y, z = p
    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        if t.is_air(x + dx, y + dy, z + dz):
            return True
    return False


def decay(t, region=None, chance=0.25, rng=None, height_bias=1.5, rubble=None, rubble_chance=0.6,
          keep=(), ground_y=None, noise=None):
    """Knock blocks out of a ruin: exposed blocks go with a chance that grows with height inside the
    region (height_bias), so walls crumble from the top. Removed blocks may land as rubble on the
    first solid cell below (rubble: a state or callable). keep: block names never removed."""
    rng = rng or t.rng
    (x0, y0, z0), (x1, y1, z1) = _region(t, region)
    keep = {B.full_name(k) for k in keep}
    span = max(1, y1 - y0)
    removed = []
    for p in sorted(list(_cells_in(t, region)), key=lambda q: (-q[1], q[2], q[0])):
        if p not in t.cells or _protected(t, p) or B.name_of(t.cells[p]) in keep:
            continue
        if B.name_of(t.cells[p]) in ('minecraft:air', 'minecraft:cave_air'):
            continue
        if not exposed(t, p):
            continue
        h = (p[1] - y0) / span
        c = chance * (0.25 + h * height_bias)
        if noise is not None:
            c *= 0.5 + noise.at(*p)
        if rng.random() < c:
            t.cells[p] = B.AIR
            t.nbt.pop(p, None)
            removed.append(p)
    if rubble is not None:
        for (x, y, z) in removed:
            if rng.random() > rubble_chance:
                continue
            yy = y - 1
            while yy >= y0 and t.is_air(x, yy, z):
                yy -= 1
            if yy < y0 or (ground_y is not None and yy < ground_y - 1):
                continue
            if t.is_solid(x, yy, z) and t.is_air(x, yy + 1, z):
                s = rubble(x, yy + 1, z) if callable(rubble) else rubble
                t.set(x, yy + 1, z, s)
    return removed


def overgrow(t, region=None, rng=None, vines=0.15, moss=0.25, top_leaves=0.0, lichen=0.0, roots=0.0,
             leaves='minecraft:oak_leaves', moss_block='minecraft:moss_carpet', vine_max=4,
             wall_names=None):
    """Nature takes the ruin back: vines hang down exposed walls, moss carpets the floors, leaves
    crown wall tops, glow lichen and hanging roots under overhangs. Only air cells are filled."""
    rng = rng or t.rng
    walls = {B.full_name(n) for n in wall_names} if wall_names else None
    cells = list(_cells_in(t, region))
    for p in cells:
        s = t.cells.get(p)
        if s is None or not t.is_solid(*p):
            continue
        n = B.name_of(s)
        if walls is not None and n not in walls:
            continue
        if not _full_block(n):
            continue
        x, y, z = p
        # moss on floors
        if moss and t.cells.get((x, y + 1, z)) in (None, B.AIR) and t.is_air(x, y + 1, z) and rng.random() < moss:
            if (x, y + 1, z) in t.cells:  # only inside the build (air set by the template), not in open sky
                t.set(x, y + 1, z, moss_block)
        if top_leaves and t.is_air(x, y + 1, z) and (x, y + 1, z) not in t.cells and rng.random() < top_leaves:
            t.set(x, y + 1, z, B.S(leaves, persistent=True, distance=1, waterlogged=False))
        # vines on the sides
        if vines:
            for d, dx, dz in (('north', 0, -1), ('south', 0, 1), ('east', 1, 0), ('west', -1, 0)):
                q = (x + dx, y, z + dz)
                if t.inside(*q) and t.is_air(*q) and rng.random() < vines / 4:
                    face = B.opposite(d)  # the vine cell's side that touches the wall
                    length = rng.randint(1, vine_max)
                    for k in range(length):
                        qq = (q[0], q[1] - k, q[2])
                        if not t.inside(*qq) or not t.is_air(*qq):
                            break
                        wall = (x, y - k, z)
                        if k > 0 and not t.is_solid(*wall):
                            break
                        t.set(*qq, B.S('vine', **{face: True}))
        if lichen and t.is_air(x, y - 1, z) and rng.random() < lichen:
            q = (x, y - 1, z)
            if t.inside(*q) and q in t.cells:
                t.set(*q, B.S('glow_lichen', up=True, waterlogged=False))
        if roots and t.is_air(x, y - 1, z) and rng.random() < roots:
            q = (x, y - 1, z)
            if t.inside(*q) and q in t.cells:
                t.set(*q, B.S('hanging_roots', waterlogged=False))


def _full_block(name):
    path = name.split(':', 1)[1]
    for s in ('_slab', '_stairs', '_fence', '_wall', '_pane', '_bars', '_door', '_trapdoor', '_carpet', '_torch',
              '_lantern', 'lantern', '_button', '_sign', '_banner', 'chain', 'rod', 'ladder', 'vine', 'leaves',
              'glass', 'lichen', 'roots', 'chest', 'barrel', 'bed', 'pot', 'campfire', 'candle', 'skull', 'head'):
        if path.endswith(s):
            return False
    return True


def scatter(t, region, choices, density, rng=None, on=None, need_air_above=True, max_y=None):
    """Put small things (plants, rocks, bones) on top of ground cells. choices: [(state, weight)].
    on: block names that count as ground (None: any full solid block)."""
    rng = rng or t.rng
    on = {B.full_name(n) for n in on} if on else None
    total = sum(w for _, w in choices)
    for p in list(_cells_in(t, region)):
        s = t.cells.get(p)
        if s is None:
            continue
        n = B.name_of(s)
        if on is not None and n not in on:
            continue
        if on is None and (not t.is_solid(*p) or not _full_block(n)):
            continue
        x, y, z = p
        if max_y is not None and y + 1 > max_y:
            continue
        if need_air_above and not t.is_air(x, y + 1, z):
            continue
        if not t.inside(x, y + 1, z) or rng.random() >= density:
            continue
        r = rng.random() * total
        for st, w in choices:
            r -= w
            if r <= 0:
                if st.endswith('[half=lower]') or 'tall' in st and 'half=lower' in st:
                    pass
                t.set(x, y + 1, z, st)
                if 'half=lower' in st and t.inside(x, y + 2, z) and t.is_air(x, y + 2, z):
                    t.set(x, y + 2, z, st.replace('half=lower', 'half=upper'))
                break


def drift(t, region, state, rng=None, depth=2, against=None):
    """Pile loose material (sand, ash, snow) against walls inside the region: air cells on the floor
    next to a wall get `state`, up to `depth` high, thinning with distance from the wall."""
    rng = rng or t.rng
    (x0, y0, z0), (x1, y1, z1) = _region(t, region)
    against = {B.full_name(n) for n in against} if against else None
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            # find the floor in this column
            for y in range(y0, y1 + 1):
                if not t.is_solid(x, y, z) or not t.is_air(x, y + 1, z) or (x, y + 1, z) not in t.cells:
                    continue
                near = 0
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nn = t.name_at(x + dx, y + 1, z + dz)
                    if nn and t.is_solid(x + dx, y + 1, z + dz) and (against is None or nn in against):
                        near += 1
                if near and rng.random() < 0.35 * near:
                    h = rng.randint(1, depth)
                    for k in range(1, h + 1):
                        if (x, y + k, z) in t.cells and t.is_air(x, y + k, z):
                            t.set(x, y + k, z, state(x, y + k, z) if callable(state) else state)
