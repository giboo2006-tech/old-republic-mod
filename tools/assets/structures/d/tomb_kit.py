"""Shared dressing of worker D's underground halls: re-skin by role (floor, wall rows, ceiling), pilasters,
corbels, wall niches, hanging lights, cobwebs and floor litter, always around frozen and reserved cells.

A Style names the materials of one family (the greater tomb's Sith stone, the Reliquary's deepslate,
the Hymnal's black basalt); dress() applies them to a loaded template's surfaces that face air the
template sets, so nothing outside the rooms changes.
"""
from common import (B, S, AIRS, DTemplate, faces, is_air_set, mixer, hash01, stairs, slab, chain, lantern, candle,
                    cobweb, SIDES)


class Style:
    def __init__(self, seed, wall, floor, ceiling, base=None, cornice=None, pilaster=None, rib=None,
                 replace=None, pilaster_every=4, rib_every=4, corbel=None, rib_axis='both'):
        self.seed = seed
        self.wall = mixer(seed, wall, 3.5) if isinstance(wall, list) else wall
        self.floor = mixer(seed + 1, floor, 3.0) if isinstance(floor, list) else floor
        self.ceiling = mixer(seed + 2, ceiling, 4.0) if isinstance(ceiling, list) else ceiling
        self.base = mixer(seed + 3, base, 2.5) if isinstance(base, list) else base
        self.cornice = cornice
        self.pilaster = pilaster
        self.rib = rib
        self.replace = {B.full_name(n) for n in replace} if replace else None
        self.pilaster_every = pilaster_every
        self.rib_every = rib_every
        self.corbel = corbel
        self.rib_axis = rib_axis


def _call(f, p):
    return f(*p) if callable(f) else f


def wall_rows(t, p, f):
    """(base, top) for a wall cell: whether one of its room sides has floor right below (base row) or
    ceiling right above (top row)."""
    x, y, z = p
    base = top = False
    for d, dx, dz in SIDES:
        if d not in f:
            continue
        a = (x + dx, y, z + dz)
        below = t.cells.get((a[0], y - 1, a[2]))
        above = t.cells.get((a[0], y + 1, a[2]))
        if below is not None and B.name_of(below) not in AIRS:
            base = True
        if above is not None and B.name_of(above) not in AIRS:
            top = True
    return base, top


def dress(t, style, region=None):
    """Re-skins every cell of style.replace (all solid cells when None) that faces set air."""
    changes = {}
    for p, s in t.cells.items():
        if region and not (region[0] <= p[0] <= region[3] and region[1] <= p[1] <= region[4]
                           and region[2] <= p[2] <= region[5]):
            continue
        n = B.name_of(s)
        if n in AIRS or p in t.nbt:
            continue
        if style.replace is not None and n not in style.replace:
            continue
        if isinstance(t, DTemplate) and t.is_frozen(*p):
            continue
        f = faces(t, p)
        if not f:
            continue
        x, y, z = p
        side = f - {'up', 'down'}
        if side:
            base, top = wall_rows(t, p, f)
            along_x = bool(side & {'north', 'south'})
            k = x if along_x else z
            if base and style.base is not None:
                ns = _call(style.base, p)
            elif top and style.cornice is not None:
                ns = _call(style.cornice, p)
            elif style.pilaster is not None and k % style.pilaster_every == 0:
                ns = _call(style.pilaster, p)
            else:
                ns = _call(style.wall, p)
        elif 'up' in f:
            ns = _call(style.floor, p)
        else:
            ns = _call(style.ceiling, p)
            if style.rib is not None:
                ax = style.rib_axis
                if (ax in ('x', 'both') and x % style.rib_every == 0) or (ax in ('z', 'both') and z % style.rib_every == 0):
                    ns = _call(style.rib, p)
        if ns != s:
            changes[p] = ns
    for p, s in changes.items():
        t.set(*p, s)
    return len(changes)


def room_cells(t, region):
    """Set air cells inside region."""
    x0, y0, z0, x1, y1, z1 = region
    return [(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)
            if is_air_set(t, (x, y, z))]


def solid(t, p):
    s = t.cells.get(p)
    return s is not None and B.name_of(s) not in AIRS and not B.name_of(s).endswith(('_carpet', 'light'))


def corbels(t, region, stair_name, every=3, seed=0):
    """Upside-down stairs under the ceiling along the walls (a corbel table), every `every` cells."""
    n = 0
    for (x, y, z) in room_cells(t, region):
        if not solid(t, (x, y + 1, z)) or not t.free(x, y, z):
            continue
        for d, dx, dz in SIDES:
            if solid(t, (x + dx, y, z + dz)) and ((x if dz else z) % every == 0):
                # not in a corner, and with room below (headroom stays 3 over the floor)
                if solid(t, (x - dx, y, z - dz)):
                    continue
                if not all(is_air_set(t, (x, y - k, z)) for k in (1, 2, 3)):
                    continue
                t.set(x, y, z, stairs(stair_name, d, half='top'))
                n += 1
                break
    return n


def cobwebs(t, region, chance=0.35, seed=0):
    """Cobwebs in upper corners (an air cell under the ceiling with two walls)."""
    n = 0
    for (x, y, z) in room_cells(t, region):
        if not solid(t, (x, y + 1, z)) or not t.free(x, y, z):
            continue
        walls = sum(1 for _, dx, dz in SIDES if solid(t, (x + dx, y, z + dz)))
        if walls >= 2 and hash01(seed, x, y, z) < chance:
            t.set(x, y, z, cobweb())
            n += 1
    return n


def niches(t, region, fill, every=5, seed=0, height=1, depth_check=True):
    """Carves wall niches one cell deep at `height` above a floor along the walls, every `every` cells,
    where the wall is at least two thick; fill(x, y, z, facing) -> state or None for the niche cell
    (facing: the side the niche opens to). Returns the niche cells."""
    out = []
    x0, y0, z0, x1, y1, z1 = region
    for (x, y, z) in room_cells(t, region):
        if not solid(t, (x, y - height, z)) or any(not is_air_set(t, (x, y - k, z)) for k in range(0, height)):
            continue
        for d, dx, dz in SIDES:
            w = (x + dx, y, z + dz)
            k = w[0] if dz else w[2]
            if k % every != 0 or not solid(t, w):
                continue
            if isinstance(t, DTemplate) and (t.is_frozen(*w) or w in (t.reserved or ())):
                continue
            behind = (w[0] + dx, y, w[2] + dz)
            if depth_check and not solid(t, behind):
                continue
            if not solid(t, (w[0], y + 1, w[2])) or not solid(t, (w[0], y - 1, w[2])):
                continue
            if B.name_of(t.cells[w]) not in _NICHE_OK:
                continue
            if hash01(seed, *w) > 0.8:
                continue
            opens = {'east': 'west', 'west': 'east', 'south': 'north', 'north': 'south'}[d]
            st = fill(w[0], y, w[2], opens)
            t.set(*w, st if st is not None else 'minecraft:air')
            out.append(w)
            break
    return out


_NICHE_OK = set()


def niche_ok(*names):
    _NICHE_OK.update(B.full_name(n) for n in names)


def hang(t, x, y_ceiling, z, length, end):
    """A chain from the ceiling cell above (x, y_ceiling, z) down `length` cells with `end` at its foot."""
    for k in range(length):
        t.set(x, y_ceiling - k, z, chain('y'))
    t.set(x, y_ceiling - length, z, end)


def litter(t, region, choices, density, seed=0, keep_free=True):
    """Floor litter (decals, small props) on free floor cells. choices: [(state, weight)]."""
    total = sum(w for _, w in choices)
    n = 0
    for (x, y, z) in room_cells(t, region):
        if not solid(t, (x, y - 1, z)):
            continue
        if keep_free and not t.free(x, y, z):
            continue
        if not keep_free and isinstance(t, DTemplate) and t.is_frozen(x, y, z):
            continue
        h = hash01(seed, x, y, z)
        if h >= density:
            continue
        r = hash01(seed + 7, x, y, z) * total
        for st, w in choices:
            r -= w
            if r <= 0:
                t.set(x, y, z, st)
                n += 1
                break
    return n


def base_lights(t, region, every, state, names, seed=0, chance=1.0):
    """Light from below: every `every` cells along a wall's base course (the wall cell beside a standing
    player's feet) becomes `state` when it is one of `names`. Returns the cells changed."""
    names = {B.full_name(n) for n in names}
    out = []
    for (x, y, z) in room_cells(t, region):
        if not solid(t, (x, y - 1, z)):
            continue
        for d, dx, dz in SIDES:
            w = (x + dx, y, z + dz)
            k = w[0] if dz else w[2]
            if k % every or not solid(t, w) or w in out:
                continue
            if isinstance(t, DTemplate) and t.is_frozen(*w):
                continue
            if B.name_of(t.cells[w]) not in names or hash01(seed, *w) > chance:
                continue
            t.set(*w, state)
            out.append(w)
    return out
