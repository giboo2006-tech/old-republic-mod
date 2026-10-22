"""The silenced city's building kit (tyrvann/silenced_city; worker P4).

"The Hymn rolls over a city of pale roofs. Street by street, the people stop speaking, and listen, and
obey. At the end, nothing sings but the stone." The city sank into the mire after it fell silent: pale
stone walls stained dark where the water stood, half-timbered in pale oak, pale shingle roofs, verdigris
song-pipes the Hymn ran along its streets, roots and pale moss through everything. In every room the
chairs are turned toward the window that looks at the singing stone.

Every piece shares one vertical frame so rigid jigsaw pieces line up: y 0 the footings (and the floors of
flooded rooms), y 1 the water line (paving flush with the mire, or a room's standing water), y 2 the
walk level of the streets. Every column that carries anything is solid at y 0, so the pool element's
support columns reach the bed of the mire.
"""
from common import *  # noqa: F401,F403

WL, WK = 1, 2                     # the water line (paving) and the walk level

PALE_BODY = [('minecraft:calcite', 5), ('minecraft:polished_diorite', 3), ('minecraft:diorite', 2)]
TIDE = [('minecraft:mossy_stone_bricks', 3), ('minecraft:mud_bricks', 2), ('minecraft:tuff_bricks', 2),
        ('minecraft:polished_tuff', 1), ('minecraft:cracked_stone_bricks', 1)]
PAVING = [('minecraft:calcite', 3), ('minecraft:polished_diorite', 3), ('oldrepublic:pale_tiles', 2),
          ('minecraft:diorite', 2), ('minecraft:smooth_stone', 1)]
TIMBER = 'minecraft:stripped_pale_oak_log'
ROOF = 'minecraft:pale_oak_stairs'
ROOF_SLAB = 'minecraft:pale_oak_slab'
STONE_ROOF = 'minecraft:polished_diorite_stairs'
FLOOR_UP = [('minecraft:pale_oak_planks', 5), ('oldrepublic:pale_tiles', 2)]
FOOTING = [('minecraft:cobbled_deepslate', 2), ('minecraft:mud_bricks', 2), ('minecraft:mossy_cobblestone', 1)]


def body_picker(seed, tide_to=2):
    """Pale walls by noise patches, water-stained (mossy, mud and tuff brick) up to y tide_to."""
    pale = patch(Noise(seed, 3.0), PALE_BODY, jitter=Noise(seed + 1, 0.9), mix=0.35)
    tide = patch(Noise(seed + 2, 2.5), TIDE, jitter=Noise(seed + 3, 0.8), mix=0.4)
    stain = Noise(seed + 4, 2.0)

    def f(x, y, z):
        if y <= tide_to or (y == tide_to + 1 and stain.at(x, y, z) > 0.62):
            return tide(x, y, z)
        return pale(x, y, z)
    return f


def flood(t, cells, y=WL, bottom='minecraft:mud'):
    """Standing water at y over (x, z) cells, kept only where it sits in a basin of its own: every
    neighbour at y is water or a solid cell the template sets, so nothing drains away. Returns the cells."""
    cells = set(cells)

    def holds(n):
        if n in cells:
            return True
        if not t.inside(n[0], y, n[1]):
            return False
        name = t.name_at(n[0], y, n[1])
        return name == 'minecraft:water' or t.is_solid(n[0], y, n[1])
    changed = True
    while changed:
        changed = False
        for c in sorted(cells):
            if not all(holds((c[0] + dx, c[1] + dz)) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                cells.discard(c)
                changed = True
    for (x, z) in sorted(cells):
        t.set(x, y, z, WATER)
        if not t.is_solid(x, y - 1, z):
            t.set(x, y - 1, z, bottom)
    return cells


def lamp_post(t, x, z, lit=True, broken=False, y=WK):
    """A lamp of the city: a diorite post and a lantern. The Hymn's lamps still burn, cold."""
    t.set(x, y - 1, z, 'minecraft:polished_diorite')
    t.set(x, y - 2, z, 'minecraft:cobbled_deepslate')
    for k in range(3):
        t.set(x, y + k, z, 'minecraft:diorite_wall')
    t.set(x, y + 3, z, 'minecraft:polished_diorite')
    if broken:
        t.set(x, y + 4, z, S('minecraft:lightning_rod', facing='up', powered=False, waterlogged=False))
        return
    t.set(x, y + 4, z, lantern(soul=True) if lit else S('lantern', hanging=False, waterlogged=False))


def window(t, x, y, z, kind='glass', h=2):
    """A window h high at (x, y.., z): glass panes, broken (open) or bars."""
    for k in range(h):
        t.set(x, y + k, z, {'glass': 'minecraft:glass_pane', 'bars': 'minecraft:iron_bars'}.get(kind, AIR))


def chairs_facing(t, rng, cells, y, facing, chance=0.7, wood='pale_oak'):
    """Chairs (stairs that seat someone looking `facing`), a few knocked flat."""
    for (x, z) in cells:
        if rng.random() > chance or not t.is_air(x, y, z):
            continue
        if rng.random() < 0.15:
            t.set(x, y, z, slab('minecraft:%s_slab' % wood))
        else:
            t.set(x, y, z, stair('minecraft:%s_stairs' % wood, opposite(facing)))


def table(t, x, y, z, wood='pale_oak'):
    t.set(x, y, z, S('minecraft:%s_fence' % wood))
    t.set(x, y + 1, z, S('minecraft:%s_pressure_plate' % wood, powered=False))


def gable(t, body, x0, z0, x1, z1, ry, ridge, stairs=ROOF):
    """A gable roof (overhang 1) with eaves at ry, its end walls filled with the house's wall."""
    w, d = x1 - x0 + 1, z1 - z0 + 1
    shapes.gable_roof(t, x0 - 1, z0 - 1, x1 + 1, z1 + 1, ry, stairs, ridge=ridge)
    if ridge == 'z':
        for z in (z0, z1):
            for i in range(1, w // 2 + 1):
                for x in range(x0 + i, x1 - i + 1):
                    t.set(x, ry + i, z, body(x, ry + i, z))
    else:
        for x in (x0, x1):
            for i in range(1, d // 2 + 1):
                for z in range(z0 + i, z1 - i + 1):
                    t.set(x, ry + i, z, body(x, ry + i, z))


def house(t, rng, x0, z0, w, d, storeys=2, ridge='z', front='north', doors=(), flooded=True, seed=0,
          roof='gable', roof_stairs=ROOF, collapse=None, windows_kind=('glass', 'broken'), furnish=True,
          face_to=None):
    """A pale house over x0..x0+w-1, z0..z0+d-1, `storeys` storeys of four layers (ground walls y 1..4, an
    upper floor at y 5 with walls 6..8, a plate at 9 ...). doors: offsets along the front facade. flooded:
    the ground floor lies at y 0 under standing water at y 1 (else dry tiles at y 1). collapse: None, 'roof'
    (fallen in) or 'corner' (the back corner down to the stumps). face_to: where the chairs look (the front
    by default). Returns {'rooms': light regions, 'top': the eaves y, 'interior': cells, 'box': ...}."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    body = body_picker(seed)
    top = 1 + 4 * storeys
    face_to = face_to or front
    perim = {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if x in (x0, x1) or z in (z0, z1)}
    inner = {(x, z) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1)}
    corners = {(x0, z0), (x0, z1), (x1, z0), (x1, z1)}
    rooms = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, pick(rng, FOOTING))
    for (x, z) in inner:
        t.set(x, 1, z, WATER if flooded else pick(rng, [('oldrepublic:pale_tiles', 3), ('minecraft:polished_diorite', 1)]))
        for y in range(2, 5):
            t.set(x, y, z, AIR)
    if not flooded:
        rooms.append((x0 + 1, 2, z0 + 1, x1 - 1, 4, z1 - 1))
    for s in range(storeys):
        yb = 1 + 4 * s
        for (x, z) in perim:
            for y in range(yb, yb + 4):
                t.set(x, y, z, log(TIMBER, 'y') if (x, z) in corners else body(x, y, z))
        if s > 0:
            for (x, z) in perim - corners:
                t.set(x, yb, z, log(TIMBER, 'x' if z in (z0, z1) else 'z'))
            for (x, z) in inner:
                t.set(x, yb, z, pick(rng, FLOOR_UP))
                for y in range(yb + 1, yb + 4):
                    t.set(x, y, z, AIR)
            rooms.append((x0 + 1, yb + 1, z0 + 1, x1 - 1, yb + 3, z1 - 1))
    for (x, z) in perim:
        t.set(x, top, z, log(TIMBER, 'y') if (x, z) in corners else log(TIMBER, 'x' if z in (z0, z1) else 'z'))
    for (x, z) in inner:
        t.set(x, top, z, 'minecraft:pale_oak_planks')
    # windows two high on every facade, every other cell, clear of the corners
    for s in range(storeys):
        wy = 2 if s == 0 else 2 + 4 * s
        for (x, z) in sorted(perim - corners):
            along_x = z in (z0, z1)
            k = (x - x0) if along_x else (z - z0)
            n = w if along_x else d
            if k % 2 == 1 and 1 < k < n - 2 or (n <= 5 and k == n // 2):
                window(t, x, wy, z, windows_kind[(x * 7 + z * 13 + s) % len(windows_kind)])
    # doors: a threshold at the water line, an opening two high, a pale oak door hanging open
    for off in doors:
        if front in ('north', 'south'):
            x, z = x0 + off, (z0 if front == 'north' else z1)
        else:
            x, z = (x0 if front == 'west' else x1), z0 + off
        t.set(x, 1, z, 'minecraft:polished_diorite')
        t.set(x, 4, z, log(TIMBER, 'x' if front in ('north', 'south') else 'z'))
        if rng.random() < 0.6:
            t.set(x, 2, z, S('pale_oak_door', facing=front, half='lower', hinge='left', open=True, powered=False))
            t.set(x, 3, z, S('pale_oak_door', facing=front, half='upper', hinge='left', open=True, powered=False))
        else:
            t.set(x, 2, z, AIR)
            t.set(x, 3, z, AIR)
    if roof == 'gable':
        gable(t, body, x0, z0, x1, z1, top, ridge, roof_stairs)
    elif roof == 'hip':
        shapes.hip_roof(t, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top, roof_stairs)
    interior = sorted(inner)
    if furnish and storeys > 1:
        fill_rooms(t, rng, x0, z0, x1, z1, storeys, face_to)
        lx, lz = x0 + 1, z1 - 1                                   # a ladder up from the water, back corner
        for y in range(1, top):
            t.set(lx, y, lz, S('ladder', facing='north', waterlogged=(y == 1 and flooded)))
    if collapse == 'roof':
        cx, cz = (x0 + x1) / 2.0, (z0 + z1) / 2.0
        for (x, y, z) in [p for p in list(t.cells) if x0 - 1 <= p[0] <= x1 + 1 and z0 - 1 <= p[2] <= z1 + 1 and p[1] >= top]:
            if math.hypot(x - cx, z - cz) < min(w, d) / 2.0 + 0.3 - (y - top) * 0.2 and rng.random() < 0.85:
                t.set(x, y, z, AIR)
        yfl = 5 if storeys > 1 else 1
        for (x, z) in interior:
            if rng.random() < 0.22 and t.is_air(x, yfl + 1, z):
                t.set(x, yfl + 1, z, pick(rng, [(slab(ROOF_SLAB), 3), (stair(ROOF, rng.choice(DIRS4)), 2),
                                                ('minecraft:calcite', 1), ('oldrepublic:debris', 2)]))
    elif collapse == 'corner':
        bx, bz = x1, z1
        reach = (w + d) * 0.42
        for p in [p for p in list(t.cells) if x0 - 1 <= p[0] <= x1 + 1 and z0 - 1 <= p[2] <= z1 + 1 and p[1] >= 3]:
            x, y, z = p
            if abs(x - bx) + abs(z - bz) + (y - 3) * 0.3 < reach + rng.random() * 1.6:
                t.set(x, y, z, AIR)
    return {'rooms': rooms, 'top': top, 'interior': interior, 'box': (x0, z0, x1, z1)}


def fill_rooms(t, rng, x0, z0, x1, z1, storeys, face_to):
    """The upper rooms of a house: chairs at the window that looks at the stone, a table, a bed, shelves."""
    fx, fz = B.step(face_to)
    for s in range(1, storeys):
        y = 2 + 4 * s
        inner = [(x, z) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1)]
        near = [(x, z) for (x, z) in inner
                if (fz == -1 and z == z0 + 1) or (fz == 1 and z == z1 - 1) or (fx == -1 and x == x0 + 1) or (fx == 1 and x == x1 - 1)]
        chairs_facing(t, rng, near, y, face_to, chance=0.6)
        far = [(x, z) for (x, z) in inner
               if (fz == -1 and z == z1 - 1) or (fz == 1 and z == z0 + 1) or (fx == -1 and x == x1 - 1) or (fx == 1 and x == x0 + 1)]
        for i, (x, z) in enumerate(far):
            if not t.is_air(x, y, z):
                continue
            r = (i + s) % 5
            if r == 0:
                t.set(x, y, z, 'minecraft:bookshelf')
                t.set(x, y + 1, z, 'minecraft:bookshelf' if rng.random() < 0.5 else candle(rng.randint(1, 3), 'white', lit=False))
            elif r == 2:
                t.set(x, y, z, barrel('up'))
            elif r == 3:
                t.set(x, y, z, pot(opposite(face_to)))
        mid = [(x, z) for (x, z) in inner if (x, z) not in near and (x, z) not in far]
        if mid:
            x, z = mid[len(mid) // 2]
            if t.is_air(x, y, z) and t.is_air(x, y + 1, z):
                table(t, x, y, z)
        if len(inner) >= 12 and s == storeys - 1:
            side = [(x, z) for (x, z) in inner if x == x1 - 1 and (x, z) not in near and (x, z) not in far]
            if len(side) >= 2 and t.is_air(*(side[0][0], y, side[0][1])) and t.is_air(side[1][0], y, side[1][1]):
                bed(t, side[0][0], y, side[0][1], 'south' if side[1][1] > side[0][1] else 'north', 'white')


def silt(x, z):
    """What shows where a flag is lost: mud, with clay in every cell of even x and z, so no 2x2 of the
    mire's #dirt is ever open (the stormwood giant roots on one) while mire roots still find mud."""
    return 'minecraft:clay' if x % 2 == 0 and z % 2 == 0 else 'minecraft:mud'


def paving(t, rng, cells, seed, lost=lambda x, z, h: False, mats=PAVING, size=2):
    """Pale flags at the water line over footings; returns the cells where a flag is lost (for water)."""
    for (x, z) in cells:
        t.set(x, 0, z, pick(rng, FOOTING))
    return flag_floor(t, sorted(cells), WL, seed, mats, size=size, lost=lost, lost_state=silt)


def song_pipe(t, x, z, y0, h, top=True):
    """A verdigris song-pipe the Hymn ran up the city's walls: a copper rod column with a grate collar."""
    for k in range(h):
        t.set(x, y0 + k, z, rod())
    if top:
        t.set(x, y0 + h, z, rod())


def open_air(t, cells, y0, y1):
    """Explicit air over open ground (clears the world's plants and terrain) where the template sets nothing."""
    for (x, z) in cells:
        for y in range(y0, y1 + 1):
            if t.inside(x, y, z) and (x, y, z) not in t.cells:
                t.set(x, y, z, AIR)


def earth_patch(t, rng, cells, y=WL):
    """A garden bed at the water line: dirt that plants take, with packed mud in every cell of even x and z
    (no open 2x2 of #dirt for the stormwood giant). Returns the cells that take plants."""
    soil = []
    for (x, z) in cells:
        t.set(x, 0, z, pick(rng, FOOTING))
        if x % 2 == 0 and z % 2 == 0:
            t.set(x, y, z, 'minecraft:packed_mud')
        else:
            t.set(x, y, z, pick(rng, [('minecraft:coarse_dirt', 3), ('minecraft:rooted_dirt', 2), ('minecraft:mud', 2)]))
            soil.append((x, z))
    return soil

