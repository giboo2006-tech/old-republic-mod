"""varkhast/ash_village: an abandoned village half buried in ash (worldgen; MILESTONE_6 5.5).

The mountain to the west put the village under four blocks of ash in a single season. Its people left with
what they could carry. The ash lies level with the upper floors now: the two-storey houses can be walked
into from the ash through their loft doors, the one-storey houses show only their roofs, and the streets
lie under the ash where the people walked. Ash has blown in through every window and piled against every
west wall.

Variants (start pool, equal weights):
  bell    someone came back and dug: the square is cleared down to its old paving around the well, with
          trenches to the south lane and to two doors; the bell tower still stands over the square, its
          shrine at its foot, its bell in the open chamber at the top.
  ember   the bell tower fell when the ash came and lies in pieces across the square; only a pit around the
          well has been dug; a seam of embers has opened through the village and some of the houses burned.

Run:  python3 tools/assets/structures/p3/ash_village.py [--preview DIR] [--only NAME]
"""
import vk
from vk import S, AIR, Template, mod, Mix, Field, stair, slab, STEP, OPP, RIGHT, LEFT

NAME = 'ash_village'
VARIANTS = ('bell', 'ember')
SIZE = (53, 30, 53)
BURIED = 7
G = 6               # the ash surface (ground layer)
F = 7               # feet on the ash
OLD = 2             # the old ground: street paving and house floors
S = 3               # feet on the old ground
SEED = 960253
SALT = 960253

TOWER = (24, 13)    # x0, z0 of the 5 x 5 bell tower; its door faces south onto the square
WELL = (26, 26)

# name, x0, z0, w, d, storeys, door side, kind
HOUSES = [
    ('h1', 4, 4, 9, 7, 2, 'south', 'home'),
    ('h2', 16, 4, 6, 6, 1, 'south', 'store'),
    ('h3', 35, 4, 10, 7, 2, 'south', 'weaver'),
    ('h4', 4, 18, 7, 9, 1, 'east', 'home'),
    ('h5', 40, 18, 9, 9, 2, 'west', 'inn'),
    ('h6', 4, 33, 9, 7, 2, 'east', 'smith'),
    ('h7', 38, 35, 8, 7, 1, 'west', 'home'),
    ('h8', 15, 41, 7, 6, 1, 'north', 'home'),
    ('h9', 30, 42, 9, 7, 2, 'north', 'home'),
]
# per variant: the houses whose roofs have fallen in, the ones that burned, where the chest is
STATES = {
    'bell': dict(collapsed={'h2', 'h7'}, burned=set(), chest='h5'),
    'ember': dict(collapsed={'h2', 'h3', 'h7', 'h8'}, burned={'h3', 'h8', 'h6'}, chest='h1'),
}


def build(preview=None, only=None):
    out = []
    for v in VARIANTS:
        if only and v != only:
            continue
        out.append(build_variant(v, preview))
    vk.worldgen(NAME, [(v, 1, SIZE[1]) for v in VARIANTS], ['ashfall'], spacing=44, separation=16, salt=SALT,
                buried=BURIED, support=10, ramp=2)
    vk.loot_table('chests/varkhast/ash_village', [
        vk.item('oldrepublic:credit_chit', 1, 4),
        vk.item('minecraft:iron_nugget', 2, 6),
        vk.item('minecraft:candle', 1, 3),
        vk.item('minecraft:string', 1, 3),
        vk.item('minecraft:gold_nugget', 1, 4, chance=0.6),
        vk.item('minecraft:copper_ingot', 1, 2, chance=0.5),
        vk.item('oldrepublic:grave_silk', 1, 1, chance=0.25),
        vk.item('minecraft:emerald', 1, 1, chance=0.2),
    ])
    return out


def build_variant(v, preview=None):
    tid = 'varkhast/%s/%s' % (NAME, v)
    t = Template(*SIZE, seed=SEED + VARIANTS.index(v) * 37, name=tid)
    rng = t.rng
    ash_fill(t, rng, v)
    dug = excavate(t, rng, v)
    rooms = {}
    for h in HOUSES:
        rooms[h[0]] = house(t, rng, v, *h)
    tower(t, rng, v)
    square(t, rng, v, dug)
    weather(t, rng, v, dug)
    if v == 'ember':
        embers(t, rng)
    lights(t, rooms)
    t.finish()
    goals = [(26, S, 22), (26, S, 25 if v == 'ember' else 30), hm('h1', 'up'), hm('h1', 'down'), hm('h5', 'up'),
             hm('h5', 'down'), hm('h9', 'up'), hm('h9', 'down'), hm('h3', 'up'), hm('h6', 'up'), hm('h6', 'down')]
    if v == 'bell':
        goals += [(26, S, 15), (26, 21, 15), (25, S, 41), hm('h4', 'down'), hm('h7', 'down')]
    vk.report(t, tid, ground_y=G, walk=((26, F, 52), goals, True))
    t.save(tid)
    if preview:
        vk.preview(t, '%s/%s_%s.png' % (preview, NAME, v), tid, plans=[F, S])
        vk.preview(t, '%s/%s_%s_cut.png' % (preview, NAME, v), tid + ' (cut)', views=('se', 'sw'),
                   plans=[], cut=[F + 2, S + 2])
    return t


def hm(name, floor):
    """A feet cell in the middle of a house's lower or upper room (clear of its stair)."""
    h = [h for h in HOUSES if h[0] == name][0]
    _, x0, z0, w, d, storeys, side, kind = h
    x, z = x0 + w // 2, z0 + d // 2
    if side in ('east', 'west'):
        x = x0 + w // 2 + (1 if side == 'east' else -1)
    else:
        z = z0 + d // 2 + (1 if side == 'south' else -1)
    return (x, S if floor == 'down' else F, z)


# ---------------------------------------------------------------- the ash

ASH_TOP = [('minecraft:light_gray_concrete_powder', 4), ('minecraft:gravel', 2), ('minecraft:tuff', 2),
           ('minecraft:gray_concrete_powder', 1)]


def ash_fill(t, rng, v):
    """Old ground at y OLD, four blocks of ash over it (packed to tuff below, loose powder on top), explicit
    air over the surface. Toward the template's edge the surface turns to the coarse dirt of the ashfall."""
    sx, sy, sz = SIZE
    found = Mix([('minecraft:cobbled_deepslate', 3), ('minecraft:tuff', 2), ('minecraft:deepslate', 1)], SEED + 1, 4.0)
    old = Mix([('minecraft:coarse_dirt', 3), ('minecraft:packed_mud', 2), ('minecraft:gravel', 1)], SEED + 2, 4.0)
    packed = Mix([('minecraft:tuff', 5), ('minecraft:smooth_basalt', 1), ('minecraft:gravel', 1)], SEED + 3, 3.0)
    loose = Mix([('minecraft:tuff', 2), ('minecraft:light_gray_concrete_powder', 2), ('minecraft:gravel', 1)],
                SEED + 4, 3.0)
    top = Mix(ASH_TOP, SEED + 5, 4.0)
    edge = Mix([('minecraft:coarse_dirt', 4), ('minecraft:gravel', 2), ('minecraft:tuff', 2),
                ('minecraft:light_gray_concrete_powder', 1)], SEED + 6, 4.0)
    n = Field(SEED + 7, 4.0)
    for x in range(sx):
        for z in range(sz):
            for y in range(0, OLD):
                t.set(x, y, z, found(x, y, z))
            t.set(x, OLD, z, old(x, OLD, z))
            for y in range(S, G - 1):
                t.set(x, y, z, packed(x, y, z))
            t.set(x, G - 1, z, loose(x, G - 1, z))
            d = min(x, z, sx - 1 - x, sz - 1 - z) + (n(x, 0, z) - 0.5) * 3.0
            t.set(x, G, z, top(x, G, z) if d > 4 else edge(x, G, z))
            for y in range(F, F + 4):
                t.set(x, y, z, AIR)


def dig(t, cells, pave, floor=True):
    """Clears cells (x, z) from the old ground up to the surface, paving their floor."""
    for (x, z) in cells:
        if floor:
            t.set(x, OLD, z, pave(x, OLD, z))
        for y in range(S, F + 4):
            t.set(x, y, z, AIR)


def ramp(t, x0, x1, z_foot, direction, pave):
    """Steps cut in the ash from the old ground up to the surface: the foot step at z_foot, climbing toward
    `direction` (north or south), x0..x1 wide."""
    dz = STEP[direction][1]
    for i in range(4):
        z = z_foot + dz * i
        for x in range(x0, x1 + 1):
            for y in range(S, S + i):
                t.set(x, y, z, 'minecraft:tuff')
            t.set(x, S + i, z, stair('minecraft:tuff_brick_stairs', direction))
            for y in range(S + i + 1, F + 4):
                t.set(x, y, z, AIR)


def excavate(t, rng, v):
    """The digging done since: returns the set of dug (x, z) cells at the old level."""
    pave = Mix([('minecraft:tuff_bricks', 4), ('minecraft:polished_tuff', 2), ('minecraft:cobbled_deepslate', 2),
                ('minecraft:cracked_deepslate_bricks', 1), ('minecraft:coarse_dirt', 1)], SEED + 20, 3.0)
    n = Field(SEED + 21, 3.0)
    cells = set()
    if v == 'bell':
        # the square, round-cornered, out to the tower's door
        for x in range(19, 34):
            for z in range(18, 34):
                dx, dz = min(x - 19, 33 - x), min(z - 18, 33 - z)
                if dx + dz + (n(x, 0, z) - 0.5) * 2.0 < 2.0:
                    continue
                cells.add((x, z))
        # the south lane, with steps up at its end
        for x in range(25, 28):
            for z in range(34, 45):
                cells.add((x, z))
        # a trench west to the door of h4, another east off the lane to the door of h7
        for x in range(11, 19):
            for z in range(21, 24):
                cells.add((x, z))
        for x in range(28, 38):
            for z in range(36, 39):
                cells.add((x, z))
        dig(t, cells, pave)
        ramp(t, 25, 27, 45, 'south', pave)
    else:
        for x in range(22, 31):
            for z in range(22, 31):
                dx, dz = min(x - 22, 30 - x), min(z - 22, 30 - z)
                if dx + dz + (n(x, 0, z) - 0.5) * 2.0 < 1.5:
                    continue
                cells.add((x, z))
        dig(t, cells, pave)
        ramp(t, 25, 27, 31, 'south', pave)
    return cells


# ---------------------------------------------------------------- houses

WALL = vk.ASH.options('wall')
ROOF = [('minecraft:deepslate_tile_stairs', 6), ('minecraft:cobbled_deepslate_stairs', 2), ('or:ash_brick_stairs', 1)]


def house(t, rng, v, name, x0, z0, w, d, storeys, side, kind):
    """An ash-brick house of one or two storeys on the old ground: basalt corner posts, a string course at
    the upper floor, a gabled tile roof. Returns its rooms [(x0, y0, z0, x1, y1, z1)]."""
    st = STATES[v]
    collapsed, burned = name in st['collapsed'], name in st['burned']
    x1, z1 = x0 + w - 1, z0 + d - 1
    wall = Mix(WALL, SEED + 100 + x0 * 7 + z0, 2.5)
    top_y = OLD + 4 * storeys       # the last wall course (5 or 9)
    eave = top_y + 1
    # footing and floor
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, OLD, z, 'minecraft:cobbled_deepslate' if x in (x0, x1) or z in (z0, z1)
                  else vk.pick(rng, [('minecraft:dark_oak_planks', 3), ('minecraft:spruce_planks', 1)]))
            for y in range(S, top_y + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if not edge:
                    t.set(x, y, z, AIR)
                elif (x in (x0, x1)) and (z in (z0, z1)):
                    t.set(x, y, z, 'minecraft:polished_basalt[axis=y]')
                elif storeys == 2 and y == OLD + 4:
                    t.set(x, y, z, 'minecraft:polished_basalt[axis=%s]' % ('x' if z in (z0, z1) else 'z'))
                else:
                    t.set(x, y, z, wall(x, y, z))
    rooms = [(x0 + 1, S, z0 + 1, x1 - 1, OLD + 3, z1 - 1)]
    if storeys == 2:
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                t.set(x, OLD + 4, z, 'minecraft:dark_oak_planks')
        rooms.append((x0 + 1, F, z0 + 1, x1 - 1, OLD + 7, z1 - 1))
    roof(t, rng, x0, z0, x1, z1, eave, wall, burned, chimney=kind in ('home', 'smith', 'inn'))
    # doors: the street door on the old ground, a loft door onto the ash on two-storey houses
    dcells = door_cells(x0, z0, w, d, side)
    ox, oz = STEP[side]
    outside_dug = all(vk.is_air_state(t.get(cx + ox, S, cz + oz)) for (cx, cz) in dcells)
    for (cx, cz) in dcells:
        for y in range(S, S + 3):
            t.set(cx, y, cz, AIR)
        if not outside_dug:
            # the ash came in through the doorway
            t.set(cx, S, cz, 'minecraft:light_gray_concrete_powder')
            t.set(cx, S + 1, cz, 'minecraft:light_gray_concrete_powder')
            t.set(cx - ox, S, cz - oz, 'minecraft:light_gray_concrete_powder')
    if outside_dug and not burned:
        (ax, az) = dcells[0]
        vk.door(t, ax, S, az, OPP[side], name='dark_oak_door', hinge='left', open_=True)
    if storeys == 2:
        for (cx, cz) in dcells:
            for y in range(F, F + 3):
                t.set(cx, y, cz, AIR)
        lx, lz = dcells[0]
        t.set(lx, F + 3, lz, 'minecraft:stripped_dark_oak_log[axis=%s]' % ('x' if side in ('north', 'south') else 'z'))
        t.set(dcells[1][0], F + 3, dcells[1][1],
              'minecraft:stripped_dark_oak_log[axis=%s]' % ('x' if side in ('north', 'south') else 'z'))
    windows(t, rng, x0, z0, x1, z1, storeys, dcells)
    keep = set()
    if storeys == 2:
        keep |= inner_stair(t, rng, x0, z0, x1, z1, side)
    for (cx, cz) in dcells:
        keep |= {(cx - ox, cz - oz), (cx - 2 * ox, cz - 2 * oz)}
    furnish(t, rng, rooms[0], kind, keep, False, name == st['chest'])
    if storeys == 2:
        furnish(t, rng, rooms[1], kind, keep, True, False)
    if collapsed:
        cave_in(t, rng, x0, z0, x1, z1, eave, storeys)
    if burned:
        char(t, rng, rooms)
    return rooms


def door_cells(x0, z0, w, d, side):
    x1, z1 = x0 + w - 1, z0 + d - 1
    if side in ('north', 'south'):
        a = x0 + w // 2 - 1
        z = z0 if side == 'north' else z1
        return [(a, z), (a + 1, z)]
    a = z0 + d // 2 - 1
    x = x0 if side == 'west' else x1
    return [(x, a), (x, a + 1)]


def roof(t, rng, x0, z0, x1, z1, eave, wall, burned, chimney=False):
    """A gabled roof over the walls, ridge along the longer side, eaves and verges one cell out."""
    mat = Mix(ROOF, SEED + 200 + x0 * 3 + z0, 2.0)
    along_x = (x1 - x0) >= (z1 - z0)
    if along_x:
        a0, a1, b0, b1 = x0, x1, z0, z1
    else:
        a0, a1, b0, b1 = z0, z1, x0, x1

    def put(a, y, b, s):
        if along_x:
            t.set(a, y, b, s)
        else:
            t.set(b, y, a, s)

    def stair_name(state):
        return vk.B.name_of(state)

    k = 0
    lo, hi = b0 - 1, b1 + 1
    while lo <= hi:
        y = eave + k
        for a in range(a0 - 1, a1 + 2):
            if lo == hi:
                put(a, y, lo, 'minecraft:deepslate_tiles' if a in (a0 - 1, a1 + 1) else
                    'minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]')
                continue
            name = stair_name(mat(a, y, lo))
            f_lo = ('south' if along_x else 'east')
            f_hi = ('north' if along_x else 'west')
            put(a, y, lo, stair(name, f_lo))
            put(a, y, hi, stair(name, f_hi))
        # the gable ends and the attic air between the slopes
        for b in range(lo + 1, hi):
            for a in (a0, a1):
                put(a, y, b, wall(a, y, b) if not burned else 'minecraft:dark_oak_planks')
            for a in range(a0 + 1, a1):
                put(a, y, b, AIR)
        k += 1
        lo += 1
        hi -= 1
    if chimney:
        # a chimney stack up the east (or south) gable, two cells above the ridge
        bm = (b0 + b1) // 2
        for y in range(eave, eave + k + 1):
            put(a1, y, bm, 'minecraft:deepslate_bricks')
        put(a1, eave + k + 1, bm, 'minecraft:deepslate_brick_wall')


def windows(t, rng, x0, z0, x1, z1, storeys, dcells):
    """Windows every third cell: where ash lies outside, it has poured in; above the ash they stand open,
    some with a shutter hanging."""
    skip = set(dcells)
    for (cx, cz) in dcells:
        for dd in (-1, 1):
            skip |= {(cx + dd, cz), (cx, cz + dd)}
    sides = []
    for x in range(x0 + 2, x1 - 1, 3):
        sides += [(x, z0, 'north'), (x, z1, 'south')]
    for z in range(z0 + 2, z1 - 1, 3):
        sides += [(x0, z, 'west'), (x1, z, 'east')]
    for (x, z, f) in sides:
        if (x, z) in skip:
            continue
        ox, oz = STEP[f]
        for base in ([S + 1] + ([F + 1] if storeys == 2 else [])):
            outside = t.get(x + ox, base, z + oz)
            if outside is not None and not vk.is_air_state(outside):
                t.set(x, base, z, 'minecraft:light_gray_concrete_powder')
                t.set(x, base - 1, z, wall_or(t, x, base - 1, z))
                if t.is_air(x - ox, S, z - oz):
                    t.set(x - ox, S, z - oz, 'minecraft:light_gray_concrete_powder')
                continue
            t.set(x, base, z, AIR)
            t.set(x, base + 1, z, AIR)
            # a shutter hanging open beside the window, hinged on the wall
            rx, rz = STEP[RIGHT[f]]
            if rng.random() < 0.45 and t.is_air(x + ox + rx, base + 1, z + oz + rz) \
                    and t.is_solid(x + rx, base + 1, z + rz):
                t.set(x + ox + rx, base + 1, z + oz + rz,
                      vk.trapdoor('minecraft:dark_oak_trapdoor', RIGHT[f], 'top', open_=True))


def wall_or(t, x, y, z):
    return t.get(x, y, z) or 'or:ash_bricks'


def inner_stair(t, rng, x0, z0, x1, z1, side):
    """A straight stair along the back wall from the street floor to the loft floor. Returns the cells to
    keep clear."""
    if side in ('south', 'north'):
        bz = z0 + 1 if side == 'south' else z1 - 1
        cells = [(x0 + 1 + i, bz) for i in range(4)]
        facing, inward = 'east', (0, 1 if side == 'south' else -1)
    else:
        bx = x0 + 1 if side == 'east' else x1 - 1
        cells = [(bx, z0 + 1 + i) for i in range(4)]
        facing, inward = 'south', (1 if side == 'east' else -1, 0)
    for i, (x, z) in enumerate(cells):
        for y in range(S, S + i):
            t.set(x, y, z, 'minecraft:dark_oak_planks')
        t.set(x, S + i, z, stair('minecraft:dark_oak_stairs', facing))
        for y in range(S + i + 1, S + i + 3):
            if y != OLD + 4 or i in (1, 2):
                t.set(x, y, z, AIR)
    keep = set(cells)
    ix, iz = inward
    fx, fz = STEP[facing]
    keep |= {(cells[0][0] + ix, cells[0][1] + iz), (cells[3][0] + fx, cells[3][1] + fz),
             (cells[3][0] + ix, cells[3][1] + iz)}
    return keep


def furnish(t, rng, room, kind, keep, upper, chest):
    x0, y, z0, x1, y1, z1 = room
    walls = []
    for x in range(x0, x1 + 1):
        walls += [(x, z0, 'south'), (x, z1, 'north')]
    for z in range(z0 + 1, z1):
        walls += [(x0, z, 'east'), (x1, z, 'west')]
    walls = [w for w in walls if (w[0], w[1]) not in keep and t.is_air(w[0], y, w[1]) and t.is_air(w[0], y + 1, w[1])]
    rng.shuffle(walls)
    walls.sort(key=lambda w: (w[0] * 31 + w[1] * 17) % 7)

    def take():
        while walls:
            c = walls.pop()
            if t.is_air(c[0], y, c[1]):
                return c
        return None

    if chest:
        c = take()
        if c:
            t.chest(c[0], y, c[1], facing=c[2], loot='chests/varkhast/ash_village')
    if upper or kind == 'home' and (x1 - x0) * (z1 - z0) < 20:
        # beds, a chest of clothes long since emptied, a pot or two
        for _ in range(2 if upper else 1):
            c = take()
            if c:
                x, z, f = c
                hx, hz = STEP[f]
                if t.is_air(x + hx, y, z + hz) and (x + hx, z + hz) not in keep:
                    # the head against the wall, the foot into the room
                    vk.bed(t, x + hx, y, z + hz, OPP[f], rng.choice(('gray', 'brown', 'black', 'red')))
        for s in [vk.barrel('up'), 'minecraft:decorated_pot', 'minecraft:potted_dead_bush']:
            c = take()
            if c:
                t.set(c[0], y, c[1], s)
        return
    items = {
        'home': ['minecraft:furnace[facing=%s,lit=false]', 'minecraft:cauldron', vk.barrel('up'), vk.barrel('up'),
                 'minecraft:potted_dead_bush', 'minecraft:decorated_pot', 'minecraft:crafting_table'],
        'store': [vk.barrel('up'), vk.barrel('up'), vk.barrel('up'), 'minecraft:brown_wool', 'minecraft:brown_wool',
                  'minecraft:dried_kelp_block', 'minecraft:decorated_pot', 'minecraft:barrel[facing=north,open=false]'],
        'weaver': ['minecraft:loom[facing=%s]', 'minecraft:loom[facing=%s]', 'minecraft:gray_wool', 'minecraft:red_wool',
                   vk.barrel('up'), 'minecraft:brown_wool'],
        'smith': ['minecraft:anvil[facing=%s]', 'minecraft:grindstone[face=floor,facing=%s]',
                  'minecraft:blast_furnace[facing=%s,lit=false]', 'minecraft:smithing_table', 'minecraft:cauldron',
                  vk.barrel('up')],
        'inn': [vk.barrel('east'), vk.barrel('east'), vk.barrel('up'), 'minecraft:furnace[facing=%s,lit=false]',
                'minecraft:decorated_pot', vk.barrel('up')],
    }[kind]
    for s in items:
        c = take()
        if not c:
            break
        t.set(c[0], y, c[1], s % c[2] if '%s' in s else s)
    # a table in the middle with stools, a dead candle on it
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    tables = [(cx, cz)] if kind != 'inn' else [(cx - 1, cz - 1), (cx + 1, cz + 1)]
    for (tx, tz) in tables:
        if (tx, tz) in keep or not t.is_air(tx, y, tz):
            continue
        t.set(tx, y, tz, 'minecraft:dark_oak_fence')
        t.set(tx, y + 1, tz, 'minecraft:dark_oak_pressure_plate[powered=false]')
        for (f, (dx, dz)) in (('west', (1, 0)), ('north', (0, 1)), ('east', (-1, 0)), ('south', (0, -1))):
            sx_, sz_ = tx + dx, tz + dz
            if (sx_, sz_) in keep or not t.is_air(sx_, y, sz_) or rng.random() < 0.4:
                continue
            t.set(sx_, y, sz_, stair('minecraft:dark_oak_stairs', OPP[f]) if rng.random() < 0.7 else
                  stair('minecraft:dark_oak_stairs', f))


def cave_in(t, rng, x0, z0, x1, z1, eave, storeys):
    """Part of the roof has fallen in: a hole over one end of the house, its tiles and beams on the floor."""
    n = Field(SEED + 300 + x0 + z0 * 3, 2.5)
    cx = x0 + (x1 - x0) * (0.3 if (x0 + z0) % 2 else 0.7)
    cz = z0 + (z1 - z0) * 0.5
    r = max(x1 - x0, z1 - z0) * 0.38
    floor_y = F if storeys == 2 else S
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            dd = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5 + (n(x, 0, z) - 0.5) * 2.0
            if dd > r:
                continue
            for y in range(eave, eave + 8):
                s = t.get(x, y, z)
                if s is not None and not vk.is_air_state(s):
                    t.set(x, y, z, AIR)
            inside = x0 < x < x1 and z0 < z < z1
            if inside and t.is_air(x, floor_y, z) and t.is_solid(x, floor_y - 1, z):
                r2 = n(x + 11, 0, z)
                if r2 < 0.35:
                    t.set(x, floor_y, z, stair('minecraft:deepslate_tile_stairs', vk.pick(rng, list(STEP))))
                elif r2 < 0.6:
                    t.set(x, floor_y, z, 'minecraft:light_gray_concrete_powder')
                elif r2 < 0.8:
                    t.set(x, floor_y, z, 'or:debris')
    # whatever was left hanging at the edge of the hole goes too: keep only roof cells still joined to
    # the walls
    box = [(x, y, z) for x in range(x0 - 1, x1 + 2) for z in range(z0 - 1, z1 + 2) for y in range(eave, eave + 9)]
    solid = {c for c in box if t.get(*c) is not None and not vk.is_air_state(t.get(*c))}
    held = set()
    todo = [(x, eave, z) for (x, y, z) in solid if y == eave and t.is_solid(x, eave - 1, z)]
    while todo:
        c = todo.pop()
        if c in held or c not in solid:
            continue
        held.add(c)
        x, y, z = c
        todo += [(x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)]
    for c in solid - held:
        t.set(*c, AIR)


def char(t, rng, rooms):
    """It burned: scorch on the floors, charred furniture."""
    for (x0, y, z0, x1, y1, z1) in rooms:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                s = t.get(x, y, z)
                name = vk.B.name_of(s) if s else ''
                if name.endswith('_bed') or name.endswith('_wool'):
                    t.set(x, y, z, 'or:debris')
                elif vk.is_air_state(s) and t.is_solid(x, y - 1, z) and rng.random() < 0.45:
                    t.set(x, y, z, 'or:scorch_mark')


# ---------------------------------------------------------------- the bell tower

def tower(t, rng, v):
    x0, z0 = TOWER
    x1, z1 = x0 + 4, z0 + 4
    wall = Mix(WALL, SEED + 400, 2.5)
    top = 23 if v == 'bell' else 9
    jag = Field(SEED + 401, 1.5)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, OLD, z, 'minecraft:polished_tuff' if 0 < x - x0 < 4 and 0 < z - z0 < 4 else 'minecraft:cobbled_deepslate')
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            h = top if v == 'bell' else top + int(jag(x, 0, z) * 4) - (2 if not corner else 0)
            for y in range(S, h + 1):
                if not edge:
                    t.set(x, y, z, AIR)
                elif corner:
                    t.set(x, y, z, 'minecraft:polished_basalt[axis=y]')
                elif y in (OLD + 5, 14, 19):
                    t.set(x, y, z, 'minecraft:polished_basalt[axis=%s]' % ('x' if z in (z0, z1) else 'z'))
                else:
                    t.set(x, y, z, wall(x, y, z))
            if not edge:
                for y in range(h + 1, F + 4):
                    t.set(x, y, z, AIR)
    # the shrine door: an arch onto the square
    for x in range(x0 + 1, x1):
        for y in range(S, S + 3):
            t.set(x, y, z1, AIR)
    t.set(x0 + 1, S + 3, z1, stair('or:ash_brick_stairs', 'east', 'top'))
    t.set(x1 - 1, S + 3, z1, stair('or:ash_brick_stairs', 'west', 'top'))
    # the shrine: a black altar on the back wall, dead candles, offerings
    t.set(x0 + 2, S, z0 + 1, 'minecraft:polished_blackstone')
    t.set(x0 + 1, S, z0 + 1, stair('minecraft:polished_blackstone_stairs', 'east'))
    t.set(x0 + 3, S, z0 + 1, stair('minecraft:polished_blackstone_stairs', 'west'))
    t.set(x0 + 2, S + 1, z0 + 1, vk.candle('black', 3, False))
    t.set(x0 + 1, S + 1, z0 + 1, vk.candle('red', 1, False))
    t.set(x0 + 2, S + 2, z0, 'minecraft:chiseled_polished_blackstone')
    if v == 'bell':
        t.set(x0 + 3, S, z0 + 3, 'minecraft:decorated_pot')
        # floors of plank with a ladder hole, the ladder up the west wall to the bell chamber
        for fy in (10, 15, 20):
            for x in range(x0 + 1, x1):
                for z in range(z0 + 1, z1):
                    t.set(x, fy, z, 'minecraft:dark_oak_planks')
        for y in range(S, 21):
            t.set(x0 + 1, y, z0 + 2, 'minecraft:ladder[facing=east,waterlogged=false]')
        # the bell chamber: open arches on every side over a low parapet, the bell hung from the cap
        for y in (21, 22, 23):
            for i in (1, 2, 3):
                for (x, z) in ((x0 + i, z0), (x0 + i, z1), (x0, z0 + i), (x1, z0 + i)):
                    t.set(x, y, z, AIR if y > 21 else 'minecraft:polished_blackstone_wall')
            for x in range(x0 + 1, x1):
                for z in range(z0 + 1, z1):
                    t.set(x, y, z, AIR)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                t.set(x, 24, z, 'or:ash_bricks')
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                    f = 'south' if z == z0 - 1 else 'north' if z == z1 + 1 else 'east' if x == x0 - 1 else 'west'
                    t.set(x, 24, z, stair('minecraft:deepslate_tile_stairs', f, 'top'))
        for k, y in enumerate((25, 26)):
            for x in range(x0 + k, x1 + 1 - k):
                for z in range(z0 + k, z1 + 1 - k):
                    if x in (x0 + k, x1 - k) or z in (z0 + k, z1 - k):
                        f = 'south' if z == z0 + k else 'north' if z == z1 - k else 'east' if x == x0 + k else 'west'
                        t.set(x, y, z, stair('minecraft:deepslate_tile_stairs', f))
                    else:
                        t.set(x, y, z, 'minecraft:deepslate_tiles')
        t.set(x0 + 2, 27, z0 + 2, 'minecraft:polished_blackstone_wall')
        t.set(x0 + 2, 28, z0 + 2, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
        t.set(x0 + 2, 23, z0 + 2, 'minecraft:bell[attachment=ceiling,facing=south,powered=false]')
    else:
        # the tower broke above its second floor; its top lies across the ash south-west of it
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                t.set(x, 10, z, 'minecraft:dark_oak_planks' if (x + z) % 3 else AIR)
        rub = Mix([('or:ash_bricks', 4), ('minecraft:polished_basalt[axis=x]', 1), ('minecraft:tuff_bricks', 1),
                   ('minecraft:deepslate_tiles', 1)], SEED + 402, 1.5)
        line = [(x0 - 1 - i, z1 + 2 + i) for i in range(0, 12)]
        for i, (lx, lz) in enumerate(line):
            for (dx, dz) in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
                x, z = lx + dx, lz + dz
                if not t.inside(x, F, z) or not t.is_solid(x, G, z):
                    continue
                if (x, z) in ((lx + 1, lz), (lx, lz + 1)) and i % 3 == 2:
                    continue
                if t.is_air(x, F, z):
                    t.set(x, F, z, rub(x, F, z))
                    if (dx, dz) == (0, 0) and i % 2 == 0 and i < 9:
                        t.set(x, F + 1, z, rub(x, F + 1, z))
        bx, bz = line[10]
        t.set(bx, F, bz, 'minecraft:bell[attachment=floor,facing=east,powered=false]')
        for (x, z) in ((bx + 1, bz), (bx, bz + 1)):
            if t.is_air(x, F, z):
                t.set(x, F, z, stair('minecraft:deepslate_tile_stairs', 'north'))


# ---------------------------------------------------------------- the square

def square(t, rng, v, dug):
    """The well, the market stalls and the cart, what was left in the square."""
    wx, wz = WELL
    for x in range(wx - 1, wx + 2):
        for z in range(wz - 1, wz + 2):
            if (x, z) == (wx, wz):
                for y in range(0, S + 1):
                    t.set(x, y, z, AIR)
                t.set(x, 0, z, 'minecraft:black_concrete')
            else:
                t.set(x, OLD, z, 'minecraft:polished_tuff')
                t.set(x, S, z, 'minecraft:cobbled_deepslate_wall')
    for x in (wx - 1, wx + 1):
        t.set(x, S + 1, wz, 'minecraft:dark_oak_fence')
        t.set(x, S + 2, wz, 'minecraft:dark_oak_fence')
    for x in range(wx - 1, wx + 2):
        t.set(x, S + 3, wz, 'minecraft:stripped_dark_oak_log[axis=x]')
    t.set(wx, S + 2, wz, vk.chain('y'))
    t.set(wx, S + 1, wz, vk.chain('y'))
    if v == 'bell':
        # stalls along the square's sides: a counter, a crimson awning on posts, what was for sale
        for (sx, sz, f) in ((21, 30, 'north'), (30, 30, 'north'), (21, 21, 'south')):
            stall(t, rng, sx, sz, f)
        # a cart left in the south lane
        cart(t, rng, 26, 38)
        # embers under the old paving at the foot of the tower: red light from below
        vk.ember_fissure(t, [(x, OLD, z) for (x, z) in vk.fissure_path(rng, (23, 20), 7, 0.5, [(1, 0)])
                             if (x, z) in dug], SEED + 500, glass=True)
    else:
        stall(t, rng, 23, 28, 'east')
        vk.ember_fissure(t, [(x, OLD, z) for (x, z) in vk.fissure_path(rng, (23, 23), 6, 0.5, [(1, 0)])
                             if (x, z) in dug and (x, z) != WELL], SEED + 500, glass=True)


def stall(t, rng, x, z, f):
    """A market stall: a slab counter 3 long facing f, posts at its back corners, an awning over it."""
    dx, dz = STEP[RIGHT[f]]
    bx, bz = STEP[OPP[f]]
    goods = [vk.barrel('up'), 'minecraft:decorated_pot', 'minecraft:dried_kelp_block', 'minecraft:brown_wool',
             'minecraft:potted_dead_bush', AIR]
    for i in range(3):
        cx, cz = x + dx * i, z + dz * i
        t.set(cx, S, cz, slab('minecraft:spruce_slab', 'top'))
        s = vk.pick(rng, goods)
        if s != AIR:
            t.set(cx, S + 1, cz, s)
        t.set(cx + bx * 2, S + 3, cz + bz * 2, 'or:canvas_crimson')
        t.set(cx + bx, S + 3, cz + bz, 'or:canvas_crimson')
        t.set(cx, S + 3, cz, 'or:canvas_crimson')
    for i in (0, 2):
        cx, cz = x + dx * i + bx * 2, z + dz * i + bz * 2
        for y in (S, S + 1, S + 2):
            t.set(cx, y, cz, 'minecraft:dark_oak_fence')


def cart(t, rng, x, z):
    """A hand cart tipped against the trench wall: a slab bed on trapdoor wheels, its shafts in the ash."""
    t.set(x, S, z, slab('minecraft:spruce_slab', 'top'))
    t.set(x, S, z + 1, slab('minecraft:spruce_slab', 'top'))
    t.set(x + 1, S, z, vk.trapdoor('minecraft:spruce_trapdoor', 'east', 'bottom', open_=True))
    t.set(x, S + 1, z, 'minecraft:light_gray_concrete_powder')
    t.set(x, S, z + 2, 'minecraft:spruce_fence')


# ---------------------------------------------------------------- ash weather

def weather(t, rng, v, dug):
    ash = Mix([('minecraft:light_gray_concrete_powder', 4), ('minecraft:gray_concrete_powder', 1),
               ('minecraft:gravel', 1)], SEED + 600, 2.0)
    ground = {s for s, _ in ASH_TOP} | {'minecraft:coarse_dirt', 'minecraft:tuff', 'minecraft:gravel'}
    vk.drift(t, (0, G, 0, SIZE[0] - 1, G, SIZE[2] - 1), ash, wind='west', depth=3, reach=4, seed=SEED + 601,
             cap='minecraft:light_gray_carpet', cap_chance=0.6, floor=ground)
    # the loft doors stay passable: clear what drifted in front of them
    for (name, x0, z0, w, d, storeys, side, kind) in HOUSES:
        if storeys != 2:
            continue
        ox, oz = STEP[side]
        for (cx, cz) in door_cells(x0, z0, w, d, side):
            for k in (0, 1, 2):
                for y in range(F, F + 3):
                    if t.is_solid(cx + ox * k, y, cz + oz * k) or vk.B.name_of(
                            t.get(cx + ox * k, y, cz + oz * k) or 'minecraft:air').endswith('carpet'):
                        t.set(cx + ox * k, y, cz + oz * k, AIR)
    # ash slumped from the cut faces onto the edges of the dug floor
    n = Field(SEED + 602, 2.5)
    for (x, z) in sorted(dug):
        if not t.is_air(x, S, z):
            continue
        walls = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                    if (x + dx, z + dz) not in dug and t.is_solid(x + dx, S, z + dz))
        if walls and n(x, 0, z) < 0.45:
            t.set(x, S, z, 'minecraft:light_gray_concrete_powder')
        elif walls and n(x, 0, z) < 0.65:
            t.set(x, S, z, 'minecraft:light_gray_carpet')
    # a film of ash on the surface and the dug floors
    vk.ash_cover(t, [(x, F, z) for x in range(SIZE[0]) for z in range(SIZE[2])], SEED + 603, chance=0.18)


def embers(t, rng):
    """A seam of embers has opened across the village, west to east: red light from under the ash."""
    path = vk.fissure_path(rng, (2, 30), 48, 0.45, [(1, 0)])
    cells = []
    for (x, z) in path:
        if not t.inside(x, G, z):
            continue
        if t.is_air(x, F, z) and B_name(t, x, G, z) in {vk.B.full_name(s) for s, _ in ASH_TOP} | {
                'minecraft:coarse_dirt', 'minecraft:tuff', 'minecraft:gravel', 'minecraft:light_gray_concrete_powder'}:
            cells.append((x, G, z))
    vk.ember_fissure(t, cells, SEED + 700, glass=True)


def B_name(t, x, y, z):
    s = t.get(x, y, z)
    return vk.B.name_of(s) if s else ''


def lights(t, rooms):
    for rs in rooms.values():
        for r in rs:
            mod.light_fill(t, r, level=8, strength=11)
    x0, z0 = TOWER
    mod.light_fill(t, (x0 + 1, S, z0 + 1, x0 + 3, 23, z0 + 3), level=8, strength=11)


if __name__ == '__main__':
    a = vk.args()
    build(a.preview, a.only)
