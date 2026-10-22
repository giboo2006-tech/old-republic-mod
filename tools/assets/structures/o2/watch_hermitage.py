"""oldrepublic:wilds/watch_hermitage: an old Jedi watcher's stone tower on a ridge.

Run: python3 tools/assets/structures/o2/watch_hermitage.py [--preview DIR]

Two start variants, both 25 x 42 x 25 and buried 2 (feet at template y 2):

* wilds/watch_hermitage/spire: a round tower of pale Jedi stone under a green copper cone, its
  spiral stair in a turret on the north side. Ground floor the watcher's hall (table, hearth
  brazier, his robe on the wall), then the library (shelves all round, a lectern, the reading seat
  in the window alcove), then his cell (bed, desk, the small shrine, his chest), and at the top an
  open arcade with a telescope and a star table. Outside, a walled herb garden with a little pool
  and a stone lantern, and on the south-east edge of the ridge the meditation ledge: cushions, a
  row of candles on the parapet and the meditation stone, looking out over the valley.
* wilds/watch_hermitage/ruin: a square watch-stone tower, older and abandoned, its stair climbing
  round the outside of three faces to a door on every floor; the top storey has fallen in, its
  stones lying on the slope. Inside the same life, gone to moss and cobweb; the garden run wild
  round a fallen statue; the ledge on the north-west rocks.

The meditation stone (oldrepublic:meditation_stone) is the real block: an Independent can respec
skills beside it, which fits a hermit's retreat out in the wilds.

Worldgen: the structure, its start pool, the set (spacing 52, separation 20, salt 960103, 4 chunks
from the ruins), the biome tag and the loot table chests/wilds/watch_hermitage.
"""
import math

from common import (Template, S, AIR, B, Noise, put, col, pick, patches, stairs, slab, trapdoor, lantern, chain_y,
                    barrel, bed, wall_banner, carve, campfire, light_rooms, scatter_on, adapted, wilds_family, loot_item,
                    loot_table, cli, save, ring_of, rod)

SX, SY, SZ = 25, 42, 25
BURIED = 2
G = 2
LOOT = 'chests/wilds/watch_hermitage'
FLOORS = (2, 10, 18, 26)

RUIN = [('or:ruin_stone', 6), ('or:ruin_bricks', 4), ('or:ruin_stone_mossy', 2), ('or:ruin_stone_cracked', 1)]
WATCH = [('or:watch_stone', 8), ('mossy_stone_bricks', 2), ('cracked_stone_bricks', 1), ('or:ruin_stone_mossy', 1)]
ROCK = [('stone', 5), ('andesite', 3), ('tuff', 2), ('cobblestone', 1), ('mossy_cobblestone', 1)]
TURF = [('grass_block[snowy=false]', 6), ('podzol[snowy=false]', 2), ('coarse_dirt', 2), ('moss_block', 1)]
COPPER = 'waxed_oxidized_cut_copper'


def wall_of(name):
    return S(name)


# ---------------------------------------------------------------- ground

def rock_base(t, seed, cx, cz, rad, extra=()):
    """The ridge-top the hermitage stands on: rock at y 0, turf at y 1 over a rough disc (plus the
    extra discs (cx, cz, r)), ragged at the rim; outside it the world is kept."""
    rock = patches(seed, ROCK, 3.0)
    turf = patches(seed + 1, TURF, 3.5)
    n = Noise(seed + 2, 2.0)
    discs = [(cx, cz, rad)] + list(extra)
    for x in range(SX):
        for z in range(SZ):
            d = min(math.hypot(x - a, z - b) - r for a, b, r in discs)
            if d > 0.6 * n.at(x, 0, z) + 0.2 or ring_of(t, x, z) == 0 and n.at(x, 1, z) < 0.5:
                continue
            t.set(x, 0, z, rock(x, 0, z))
            t.set(x, 1, z, turf(x, 1, z) if d < -1.5 or n.at(x, 2, z) > 0.45 else rock(x, 1, z))
    # no lone stubs on the rim: a cell needs two neighbours to stay
    for _ in range(2):
        lone = [(x, z) for x in range(SX) for z in range(SZ) if (x, 0, z) in t.cells and
                sum((x + dx, 0, z + dz) in t.cells for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) < 2]
        for (x, z) in lone:
            t.cells.pop((x, 0, z), None)
            t.cells.pop((x, 1, z), None)


def smooth_cone(t, cx, cz, y0, y1, names, stair_name, reach=99.0):
    """Stairs on the stepped outer surface between y0 and y1 (the high side toward the axis), only
    within `reach` of the axis (so two cones side by side do not smooth each other)."""
    for y in range(y0, y1 + 1):
        for x in range(SX):
            for z in range(SZ):
                if math.hypot(x - cx, z - cz) > reach or t.name_at(x, y, z) not in names or not t.is_air(x, y + 1, z) or (x, z) == (cx, cz):
                    continue
                dx, dz = x - cx, z - cz
                out = ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north')
                ox, oz = B.step(out)
                if t.is_air(x + ox, y, z + oz):
                    t.set(x, y, z, stairs(stair_name, B.opposite(out)))


def cone_roof(t, cx, cz, y0, r0, h, block, stair_name):
    """A solid cone of `block` from radius r0 at y0 to a point h layers up, its steps smoothed."""
    for k in range(h):
        r = r0 * (1.0 - k / float(h))
        for x in range(int(cx - r0) - 1, int(cx + r0) + 2):
            for z in range(int(cz - r0) - 1, int(cz + r0) + 2):
                d = math.hypot(x - cx, z - cz)
                if d <= r + 0.25 or (x, z) == (cx, cz):
                    t.set(x, y0 + k, z, S(block))
    smooth_cone(t, cx, cz, y0, y0 + h, ('minecraft:' + block,), block + '_stairs', reach=r0 + 1.0)


# ---------------------------------------------------------------- furniture

def shelves_round(t, cx, cz, rin, y0, y1, skip_angles=(), rng=None):
    """Bookshelves round the inside of a round wall (the ring just inside radius rin), leaving gaps
    at the given angles (degrees, 0 = east, 90 = south)."""
    for x in range(int(cx - rin) - 1, int(cx + rin) + 2):
        for z in range(int(cz - rin) - 1, int(cz + rin) + 2):
            d = math.hypot(x - cx, z - cz)
            if not (rin - 1.0 < d <= rin):
                continue
            a = math.degrees(math.atan2(z - cz, x - cx)) % 360
            if any(min(abs(a - s), 360 - abs(a - s)) < 28 for s in skip_angles):
                continue
            for y in range(y0, y1 + 1):
                if rng is not None and rng.random() < 0.18:
                    st = S('chiseled_bookshelf', facing=_face_in(x, z, cx, cz), slot_0_occupied=True, slot_1_occupied=False,
                           slot_2_occupied=True, slot_3_occupied=True, slot_4_occupied=False, slot_5_occupied=True)
                else:
                    st = S('bookshelf')
                t.set(x, y, z, st)


def _face_in(x, z, cx, cz):
    dx, dz = cx - x, cz - z
    return ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north')


def candle(n=1, lit=True, color=None):
    return S('%scandle' % (color + '_' if color else ''), candles=n, lit=lit, waterlogged=False)


def meditation_ledge(t, rng, cells, stone_at, facing, rail_cells, candles_on):
    """The ledge floor (cells (x, z) at y 1), its parapet (walls at y 2), candles on the parapet,
    cushions and the meditation stone."""
    for (x, z) in cells:
        t.set(x, 0, z, S('stone'))
        t.set(x, 1, z, S('or:ruin_bricks') if (x + z) % 3 else S('or:ruin_stone_carved'))
        for y in range(2, 6):
            t.set(x, y, z, AIR)
    for (x, z) in rail_cells:
        t.set(x, 0, z, S('stone'))
        t.set(x, 1, z, S('or:ruin_bricks'))
        t.set(x, 2, z, S('or:ruin_brick_wall'))
    for (x, z) in candles_on:
        t.set(x, 3, z, candle(rng.randint(1, 3), True, 'white'))
    x, z = stone_at
    t.set(x, 2, z, S('or:meditation_stone'))
    dx, dz = B.step(B.opposite(facing))
    for k, c in ((1, 'brown_carpet'), (2, 'light_blue_carpet')):
        t.set(x + dx * k, 2, z + dz * k, S(c))


# ---------------------------------------------------------------- the spire (round tower, turret stair)

def spiral_turret(t, tx, tz, start_index, top_landing_y, floors_door, wall, door_cells):
    """A 3 x 3 spiral stair round a newel at (tx, tz): stairs on the side cells, landings in the
    corners, rising 4 a turn from y G. floors_door: the floor feet levels whose landing is at
    door_cells (the ring index the doors open from)."""
    ring = [(0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1)]
    steps = []
    j = 0
    while True:
        i = (start_index + j) % 8
        y = G + j // 2
        steps.append((j, i, y))
        if j % 2 == 1 and y + 1 >= top_landing_y:
            break
        j += 1
    for (j, i, y) in steps:
        dx, dz = ring[i]
        x, z = tx + dx, tz + dz
        if j % 2 == 0:
            nx, nz = ring[(i + 1) % 8]
            d = 'east' if nx - dx > 0 else 'west' if nx - dx < 0 else ('south' if nz - dz > 0 else 'north')
            t.set(x, y, z, stairs('or:ruin_brick_stairs', d))
        else:
            t.set(x, y, z, S('or:ruin_bricks'))
        if j < 6:
            for yy in range(1, y):
                t.set(x, yy, z, S('or:ruin_bricks'))
        for yy in range(y + 1, y + 4):
            if t.get(x, yy, z) is None:
                t.set(x, yy, z, AIR)
    for y in range(1, top_landing_y + 3):
        t.set(tx, y, tz, S('or:ruin_pillar', axis='y'))
    return steps


def build_spire():
    t = Template(SX, SY, SZ, seed=960114, name='wilds/watch_hermitage/spire')
    rng = t.rng
    cx, cz = 12, 13
    R_OUT, R_IN = 4.6, 3.6
    tx, tz = 12, 7
    rock_base(t, 6301, cx, cz, 10.5, extra=((4, 10, 4.5), (19, 19, 4.5)))
    wall = patches(6302, RUIN, 2.5)

    def turret_cell(x, y, z, corner, edge):
        """The turret's ring and core: walls to y 33, then the lookout (parapet at 34, open at 35
        between corner piers) under a lintel course at 36."""
        if not edge:
            return AIR
        if y < 34 or y == 36:
            return wall(x, y, z)
        if corner:
            return S('or:ruin_pillar', axis='y')
        return S('or:ruin_brick_wall') if y == 34 else AIR
    rooms = []
    # --- the tower shell, floors and bands
    for x in range(cx - 5, cx + 6):
        for z in range(cz - 5, cz + 6):
            d = math.hypot(x - cx, z - cz)
            if d <= R_OUT:
                t.set(x, 0, z, S('stone'))
                t.set(x, 1, z, S('or:ruin_bricks') if d <= R_IN else S('or:ruin_stone_carved'))
            for y in range(2, 30):
                if R_IN < d <= R_OUT:
                    t.set(x, y, z, S('or:ruin_stone_carved') if y in (9, 17, 25) else wall(x, y, z))
                elif d <= R_IN:
                    if y in (9, 17, 25):
                        t.set(x, y, z, S('spruce_planks') if y < 25 else S('or:ruin_bricks'))
                    else:
                        t.set(x, y, z, AIR)
    # projecting string courses at the two lower floor bands
    for yb in (9, 17):
        for x in range(cx - 6, cx + 7):
            for z in range(cz - 6, cz + 7):
                if R_OUT < math.hypot(x - cx, z - cz) <= R_OUT + 1.0:
                    t.set(x, yb, z, stairs('or:ruin_brick_stairs', _face_in(x, z, cx, cz), 'top'))
    # corbels under the eaves and the copper cone
    for x in range(cx - 6, cx + 7):
        for z in range(cz - 6, cz + 7):
            d = math.hypot(x - cx, z - cz)
            if R_OUT < d <= R_OUT + 1.0:
                t.set(x, 29, z, stairs('or:ruin_brick_stairs', _face_in(x, z, cx, cz), 'top'))  # corbels
    cone_roof(t, cx, cz, 30, 5.7, 9, COPPER, COPPER)
    for y in range(30, 37):
        for x in range(cx - 4, cx + 5):
            for z in range(cz - 4, cz + 5):
                if math.hypot(x - cx, z - cz) <= 5.7 * (1 - (y - 30) / 9.0) - 1.5:
                    t.set(x, y, z, AIR)
    t.set(cx, 38, cz, S(COPPER))
    t.set(cx, 39, cz, rod('up'))
    # a battered plinth round the foot of the tower
    for x in range(cx - 6, cx + 7):
        for z in range(cz - 6, cz + 7):
            d = math.hypot(x - cx, z - cz)
            if R_OUT < d <= R_OUT + 1.0 and t.is_air(x, 2, z) and not t.is_air(x, 1, z):
                t.set(x, 2, z, stairs('or:ruin_brick_stairs', _face_in(x, z, cx, cz)))
    # --- the open arcade on the top floor: arches between eight piers, a low parapet
    for x in range(cx - 5, cx + 6):
        for z in range(cz - 5, cz + 6):
            d = math.hypot(x - cx, z - cz)
            if not (R_IN < d <= R_OUT):
                continue
            a = math.degrees(math.atan2(z - cz, x - cx)) % 360
            pier = abs(((a + 22.5) % 45) - 22.5) < 9
            if pier:
                for y in range(26, 29):
                    t.set(x, y, z, S('or:ruin_pillar', axis='y'))
            else:
                t.set(x, 26, z, S('or:ruin_brick_wall'))
                t.set(x, 27, z, AIR)
                t.set(x, 28, z, stairs('or:ruin_brick_stairs', _face_in(x, z, cx, cz), 'top'))
    # --- the turret on the north and its spiral stair
    for x in range(tx - 2, tx + 3):
        for z in range(tz - 2, tz + 3):
            if math.hypot(x - cx, z - cz) <= R_OUT:
                continue
            edge = abs(x - tx) == 2 or abs(z - tz) == 2
            corner = abs(x - tx) == 2 and abs(z - tz) == 2
            t.set(x, 0, z, S('stone'))
            t.set(x, 1, z, S('or:ruin_bricks'))
            for y in range(2, 37):
                t.set(x, y, z, turret_cell(x, y, z, corner, edge))
    # above the main eaves the turret's south face stands clear of the cone
    for x in range(tx - 2, tx + 3):
        for y in range(30, 37):
            t.set(x, y, tz + 2, turret_cell(x, y, tz + 2, abs(x - tx) == 2, True))
    spiral_turret(t, tx, tz, 6, 34, FLOORS[1:], wall, None)
    # the lookout at the top of the turret: a floor beside the stairwell, open on all four sides
    # above a parapet, a lantern over the well, and a copper cap rising clear of the main roof
    for (x, z) in ((tx - 1, tz - 1), (tx - 1, tz)):
        t.set(x, 33, z, S('or:ruin_bricks'))
    for x in range(tx - 1, tx + 2):
        for z in range(tz - 1, tz + 2):
            if (x, z) != (tx, tz):
                t.set(x, 36, z, S('or:ruin_bricks'))
    t.set(tx + 1, 35, tz - 1, lantern(True))
    cone_roof(t, tx, tz, 37, 2.7, 4, COPPER, COPPER)
    t.set(tx, 41, tz, rod('up'))
    # slits up the turret, doors from the landings into the tower and at the ground
    for (x, z, ys) in ((tx - 2, tz, (6, 13, 21, 28)), (tx + 2, tz, (4, 11, 19, 27)), (tx, tz - 2, (8, 15, 23))):
        for y in ys:
            t.set(x, y, z, S('glass_pane', waterlogged=False))
    for F in FLOORS:
        t.set(tx - 1, F, tz + 2, AIR)
        t.set(tx - 1, F + 1, tz + 2, AIR)
        if F == G:
            t.set(tx, F, tz + 2, AIR)
            t.set(tx, F + 1, tz + 2, AIR)
    # --- the front door south, under a carved lintel, steps to the garden walk
    for y in (2, 3, 4):
        t.set(cx, y, cz + 4, AIR)
    for x in (cx - 1, cx + 1):
        for y in (2, 3, 4):
            t.set(x, y, cz + 4, S('or:ruin_pillar', axis='y'))
    t.set(cx, 5, cz + 4, S('or:ruin_stone_carved'))
    t.set(cx, 5, cz + 5, stairs('or:ruin_brick_stairs', 'north', 'top'))
    t.set(cx, 4, cz + 5, lantern(True))
    # --- windows: tall lancets on each floor
    for F, angles in ((2, (180, 300)), (10, (0, 120, 240)), (18, (60, 200, 320))):
        for a in angles:
            x = int(round(cx + 4.1 * math.cos(math.radians(a))))
            z = int(round(cz + 4.1 * math.sin(math.radians(a))))
            if math.hypot(x - cx, z - cz) > R_IN:
                for y in (F + 1, F + 2):
                    t.set(x, y, z, S('glass_pane', waterlogged=False))
                t.set(x, F + 3, z, S('or:ruin_stone_carved'))
    # --- ground floor: the hall
    F = 2
    t.set(cx, F, cz, S('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set(cx + dx, F, cz + dz, slab('or:ruin_brick_slab', 'bottom'))
    for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        t.set(cx + dx, F, cz + dz, S('or:ruin_brick_wall'))
    t.set(cx, 8, cz, chain_y())
    t.set(cx, 7, cz, lantern(True))
    t.set(cx - 3, F, cz - 1, slab('spruce_slab', 'top'))
    t.set(cx - 3, F, cz, slab('spruce_slab', 'top'))
    t.set(cx - 3, F + 1, cz - 1, S('decorated_pot', facing='east', cracked=False, waterlogged=False))
    t.set(cx - 3, F + 1, cz, candle(2))
    t.set(cx - 2, F, cz - 1, stairs('spruce_stairs', 'west'))
    t.set(cx + 3, F, cz - 1, barrel('west'))
    t.set(cx + 3, F, cz, S('water_cauldron', level=3))
    t.set(cx + 3, F, cz + 1, barrel('up'))
    t.set(cx + 2, F, cz + 2, S('spruce_shelf', facing='north', powered=False, side_chain='unconnected', waterlogged=False))
    wall_banner(t, cx - 3, F + 2, cz + 2, 'east', 'brown', [('minecraft:stripe_center', 'white'), ('minecraft:border', 'brown')])
    for (x, z) in ((cx - 1, cz + 2), (cx, cz + 2), (cx + 1, cz - 2), (cx - 1, cz - 2)):
        t.set(x, F, z, S('brown_carpet'))
    rooms.append((cx - 3, F, cz - 3, cx + 3, F + 5, cz + 3))
    # --- library
    F = 10
    shelves_round(t, cx, cz, R_IN, F, F + 2, skip_angles=(0, 120, 240, 270), rng=rng)
    t.set(cx, F, cz, S('lectern', facing='south', has_book=False, powered=False))
    t.set(cx - 1, F, cz + 1, slab('spruce_slab', 'top'))
    t.set(cx - 1, F + 1, cz + 1, candle(3))
    t.set(cx + 1, F, cz + 1, stairs('spruce_stairs', 'east'))
    # the alcove: a window seat facing east with a reading lamp
    t.set(cx + 3, F, cz, stairs('spruce_stairs', 'east'))
    t.set(cx + 3, F, cz - 1, slab('spruce_slab', 'top'))
    t.set(cx + 3, F + 1, cz - 1, S('lantern', hanging=False, waterlogged=False))
    t.set(cx, 16, cz, chain_y())
    t.set(cx, 15, cz, lantern(True))
    for (x, z) in ((cx - 1, cz - 1), (cx, cz - 1), (cx + 1, cz - 1), (cx - 1, cz), (cx + 1, cz)):
        t.set(x, F, z, S('light_blue_carpet'))
    rooms.append((cx - 3, F, cz - 3, cx + 3, F + 5, cz + 3))
    # --- the watcher's cell
    F = 18
    bed(t, cx - 2, F, cz + 1, 'north', 'brown')
    t.chest(cx - 2, F, cz + 2, 'north', loot=LOOT)
    t.set(cx + 2, F, cz + 1, slab('spruce_slab', 'top'))
    t.set(cx + 2, F, cz + 2, slab('spruce_slab', 'top'))
    t.set(cx + 1, F, cz + 1, stairs('spruce_stairs', 'west'))
    t.set(cx + 2, F + 1, cz + 2, candle(1))
    t.set(cx + 2, F + 1, cz + 1, S('flower_pot'))
    # the shrine: a niche of candles over a cushion, a small blue banner
    t.set(cx, F, cz - 3, S('or:ruin_stone_carved'))
    t.set(cx, F + 1, cz - 3, candle(3, True, 'light_blue'))
    t.set(cx - 1, F, cz - 3, candle(2, True, 'white'))
    t.set(cx + 1, F, cz - 3, candle(1, True, 'white'))
    t.set(cx, F, cz - 2, S('light_blue_carpet'))
    wall_banner(t, cx, F + 2, cz - 3, 'south', 'light_blue', [('minecraft:circle', 'white'), ('minecraft:stripe_center', 'white')])
    t.set(cx, 24, cz, chain_y())
    t.set(cx, 23, cz, lantern(True))
    rooms.append((cx - 3, F, cz - 3, cx + 3, F + 5, cz + 3))
    # --- the arcade: telescope, star table, bench
    F = 26
    t.set(cx, F, cz, S('oak_fence'))
    t.set(cx, F + 1, cz, rod('north'))
    t.set(cx, F + 1, cz - 1, rod('north'))
    t.set(cx + 2, F, cz + 1, S('cartography_table'))
    t.set(cx - 2, F, cz + 1, stairs('spruce_stairs', 'west'))
    t.set(cx - 2, F, cz + 2, stairs('spruce_stairs', 'west'))
    t.set(cx, 29, cz, lantern(True))
    rooms.append((cx - 3, F, cz - 3, cx + 3, F + 2, cz + 3))
    rooms.append((tx - 1, G, tz - 1, tx + 1, 35, tz + 1))
    # --- the garden west: low walls, beds, the pool and a stone lantern
    gx0, gz0, gx1, gz1 = 1, 6, 6, 18
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            edge = x in (gx0, gx1) or z in (gz0, gz1)
            t.set(x, 0, z, S('stone'))
            t.set(x, 1, z, S('or:ruin_bricks') if edge else S('rooted_dirt'))
            if edge and not (x == gx1 and z in (12, 13)):
                t.set(x, 2, z, S('or:ruin_brick_wall'))
    plants = [(S('sweet_berry_bush', age=2), 3), (S('fern'), 3), (S('azalea'), 2), (S('flowering_azalea'), 1),
              (S('wildflowers', facing='north', flower_amount=3), 2), (S('firefly_bush'), 1), (S('lily_of_the_valley'), 1)]
    for x in range(gx0 + 1, gx1):
        for z in range(gz0 + 1, gz1):
            if z in (12, 13):
                t.set(x, 1, z, S('or:ruin_bricks'))  # the walk across the garden
                continue
            if (x, z) in ((2, 8), (3, 8), (2, 9), (3, 9)):
                t.set(x, 1, z, S('water', level=0))
                t.set(x, 0, z, S('mossy_cobblestone'))
                continue
            if rng.random() < 0.75:
                t.set(x, 2, z, pick(rng, plants))
    t.set(2, 2, 8, S('lily_pad'))
    t.set(5, 2, 16, S('or:ruin_brick_wall'))
    t.set(5, 3, 16, S('lantern', hanging=False, waterlogged=False))
    t.set(2, 2, 15, stairs('or:ruin_brick_stairs', 'west'))
    t.set(2, 2, 14, stairs('or:ruin_brick_stairs', 'west'))
    # walk from the garden gate to the door, and round to the ledge
    walk = [(x, 12) for x in range(7, 11)] + [(x, 13) for x in range(7, 11)] + \
           [(12, z) for z in range(18, 21)] + [(13, 19), (14, 19), (15, 19), (16, 19), (16, 20)]
    for (x, z) in walk:
        t.set(x, 0, z, S('stone'))
        t.set(x, 1, z, S('or:ruin_bricks') if (x + z) % 4 else S('mossy_cobblestone'))
    # --- the meditation ledge south-east
    ledge = [(x, z) for x in range(16, 23) for z in range(18, 24) if math.hypot(x - 19, z - 20.5) <= 3.4]
    rail = [(x, z) for x in range(15, 24) for z in range(17, 25) if 3.4 < math.hypot(x - 19, z - 20.5) <= 4.4
            and (x > 16 or z > 21) and not (x, z) in ((16, 19), (16, 20))]
    meditation_ledge(t, rng, ledge, (20, 21), 'south', rail, [c for c in rail if (c[0] + c[1]) % 3 == 0])
    # --- scatter: ferns and stones on the turf
    turf = [(x, 1, z) for x in range(SX) for z in range(SZ) if t.name_at(x, 1, z) in
            ('minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block', 'minecraft:coarse_dirt') and t.is_air(x, 2, z)]
    scatter_on(t, rng, turf, [(S('short_grass'), 5), (S('fern'), 2), (S('bush'), 1), (S('leaf_litter', facing='north', segment_amount=2), 1)], 0.25)
    carve(t, 0, 0, SX - 1, SZ - 1, G, G + 9, bank=1, noise=Noise(6399, 3.0))
    light_rooms(t, rooms)
    return t


# ---------------------------------------------------------------- the ruin (square tower, outside stair)

def build_ruin():
    t = Template(SX, SY, SZ, seed=960115, name='wilds/watch_hermitage/ruin')
    rng = t.rng
    x0, z0, x1, z1 = 8, 9, 16, 17
    cx, cz = 12, 13
    rock_base(t, 6401, cx, cz, 10.0, extra=((4, 4, 4.0), (19, 18, 4.5)))
    wall = patches(6402, WATCH, 2.5)
    rooms = []
    n = Noise(6403, 2.0)

    def chamfer(x, z):
        return False

    # broken top: the walls of the top storey stand to a ragged line, lowest on the north-east
    def top_of(x, z):
        a = math.degrees(math.atan2(z - cz, x - cx)) % 360
        drop = 0 if 110 <= a <= 250 else (3 if 250 < a <= 300 or 70 <= a < 110 else 6)
        return 30 - drop - (1 if n.at(x, 0, z) > 0.55 else 0)

    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            t.set(x, 0, z, S('stone'))
            t.set(x, 1, z, S('or:watch_stone'))
            top = top_of(x, z) if edge else 29
            for y in range(2, 30):
                if edge:
                    if y > top:
                        t.set(x, y, z, AIR)
                    elif corner:
                        t.set(x, y, z, S('or:ruin_stone_carved') if y in (9, 17, 25) else S('or:ruin_pillar', axis='y'))
                    else:
                        t.set(x, y, z, S('or:ruin_stone_carved') if y in (9, 17, 25) else wall(x, y, z))
                else:
                    if y in (9, 17):
                        t.set(x, y, z, S('spruce_planks') if n.at(x, y, z) > 0.12 or y == 9 else AIR)
                    elif y == 25:
                        t.set(x, y, z, S('or:watch_stone') if n.at(x, y, z) > 0.4 else AIR)
                    else:
                        t.set(x, y, z, AIR)
    # --- the outside stair: north face climbing east, east face climbing south, south face climbing west
    st = 'or:watch_stone_stairs'

    def step(x, y, z, climb, wall_side):
        t.set(x, y, z, stairs(st, climb))
        if y <= G + 3:
            for yy in range(0, y):
                t.set(x, yy, z, S('or:watch_stone') if yy > 1 else S('stone'))
        else:
            t.set(x, y - 1, z, stairs(st, wall_side, 'top'))
        for yy in range(y + 1, y + 4):
            t.set(x, yy, z, AIR)

    def landing(x, y, z, wall_side=None, pier=False):
        t.set(x, y, z, S('or:watch_stone'))
        if pier:
            for yy in range(0, y):
                t.set(x, yy, z, S('or:watch_stone') if yy > 1 else S('stone'))
            t.set(x, y - 1, z, S('or:ruin_stone_carved'))
        elif wall_side:
            t.set(x, y - 1, z, stairs(st, wall_side, 'top'))
        for yy in range(y + 1, y + 4):
            t.set(x, yy, z, AIR)

    for i, x in enumerate(range(7, 15)):
        step(x, G + i, z0 - 1, 'east', 'south')
        if i % 3 == 1 and G + i > G + 2:
            t.set(x, G + i, z0 - 2, slab('or:watch_stone_slab', 'top'))
            t.set(x, G + i + 1, z0 - 2, S('or:watch_stone_wall'))
    landing(15, 9, z0 - 1, 'south')
    landing(16, 9, z0 - 1, 'south')
    landing(17, 9, z0 - 1, pier=True)
    for i, z in enumerate(range(9, 17)):
        step(x1 + 1, 10 + i, z, 'south', 'west')
        if i % 3 == 1:
            t.set(x1 + 2, 10 + i, z, slab('or:watch_stone_slab', 'top'))
            t.set(x1 + 2, 11 + i, z, S('or:watch_stone_wall'))
    landing(17, 17, 17, 'west')
    landing(17, 17, 18, 'north')
    t.set(17, 15, 18, stairs(st, 'north', 'top'))
    t.set(17, 16, 18, S('or:ruin_stone_carved'))
    landing(16, 17, 18, 'north')
    landing(15, 17, 18, 'north')
    for i, x in enumerate(range(14, 6, -1)):
        step(x, 18 + i, z1 + 1, 'west', 'north')
        if i % 3 == 2:
            t.set(x, 18 + i, z1 + 2, slab('or:watch_stone_slab', 'top'))
            t.set(x, 19 + i, z1 + 2, S('or:watch_stone_wall'))
    landing(6, 25, 18, 'east')
    t.set(6, 23, 18, stairs(st, 'east', 'top'))
    t.set(6, 24, 18, S('or:ruin_stone_carved'))
    landing(6, 25, 17, 'east')
    landing(7, 25, 17, 'east')
    # doors: F1 from the north landing, F2 from the south landing, the broken top from the west
    for y in (10, 11):
        t.set(15, y, z0, AIR)
    for y in (18, 19):
        t.set(15, y, z1, AIR)
    t.set(x0, 25, z1, S('or:watch_stone'))
    for y in range(26, 30):
        t.set(x0, y, z1, AIR)
        t.set(x0, y, z1 - 1, AIR)
    t.set(x0, 25, z1 - 1, S('or:watch_stone'))
    t.set(x0 + 1, 25, z1 - 1, S('or:watch_stone'))
    # ground floor door, west
    for y in (2, 3, 4):
        t.set(x0, y, cz, AIR)
    t.set(x0, 5, cz, S('or:ruin_stone_carved'))
    t.set(x0 - 1, 1, cz, S('or:watch_stone'))
    # windows
    for (x, z, F) in ((cx, z1, 2), (x1, cz, 10), (x0, cz - 1, 10), (cx + 1, z0, 18), (x0, cz + 1, 18)):
        for y in (F + 1, F + 2):
            t.set(x, y, z, S('glass_pane', waterlogged=False) if (x + y) % 3 else AIR)
    # --- ground floor: the hall gone to moss
    F = 2
    t.set(cx - 1, F, cz, slab('spruce_slab', 'top'))
    t.set(cx, F, cz, slab('spruce_slab', 'top'))
    t.set(cx + 1, F, cz, stairs('spruce_stairs', 'north', 'top'))  # the table's end collapsed
    t.set(cx - 1, F, cz + 1, stairs('spruce_stairs', 'south'))
    t.set(cx - 2, F, cz - 2, S('cauldron'))
    t.set(cx + 3, F, cz - 3, barrel('up'))
    t.set(cx + 3, F, cz + 3, S('decorated_pot', facing='west', cracked=True, waterlogged=False))
    t.set(cx - 3, F, cz + 3, S('cobweb'))
    for (x, z) in ((cx + 2, cz + 1), (cx - 2, cz - 1), (cx + 1, cz + 3), (cx - 3, cz - 2)):
        t.set(x, F, z, S('moss_carpet'))
    t.set(cx, 8, cz, chain_y())
    t.set(cx, 7, cz, lantern(True, True))
    rooms.append((x0 + 1, F, z0 + 1, x1 - 1, F + 5, z1 - 1))
    # --- library: shelves on three walls, one fallen
    F = 10
    for x in range(x0 + 1, x1):
        for y in (F, F + 1, F + 2):
            if not chamfer(x, z0 + 1) and x not in (cx + 1,):
                t.set(x, y, z0 + 1, S('bookshelf') if rng.random() > 0.15 else S('chiseled_bookshelf', facing='south',
                      slot_0_occupied=True, slot_1_occupied=False, slot_2_occupied=False, slot_3_occupied=True,
                      slot_4_occupied=False, slot_5_occupied=False))
    for z in range(z0 + 2, z1 - 1):
        for y in (F, F + 1):
            if not chamfer(x1 - 1, z) and z != cz:
                t.set(x1 - 1, y, z, S('bookshelf'))
    for x in range(cx - 2, cx + 1):
        t.set(x, F, z1 - 2, S('bookshelf'))  # the one that fell, lying on its face
    t.set(cx + 1, F, z1 - 2, S('cobweb'))
    t.set(cx - 2, F, cz, S('lectern', facing='east', has_book=False, powered=False))
    t.set(cx, F, cz, S('spruce_slab', type='top', waterlogged=False))
    t.set(cx, F + 1, cz, candle(2, False))
    t.set(cx + 1, F, cz, stairs('spruce_stairs', 'east'))
    t.set(cx, 16, cz, chain_y())
    t.set(cx, 15, cz, lantern(True, True))
    rooms.append((x0 + 1, F, z0 + 1, x1 - 1, F + 5, z1 - 1))
    # --- the cell: the bed, the chest, moss through the broken floor above
    F = 18
    bed(t, x0 + 2, F, z1 - 3, 'south', 'brown')
    t.chest(x0 + 3, F, z1 - 2, 'west', loot=LOOT)
    t.set(x1 - 2, F, z0 + 2, slab('spruce_slab', 'top'))
    t.set(x1 - 2, F + 1, z0 + 2, candle(1, False))
    t.set(x1 - 3, F, z0 + 2, stairs('spruce_stairs', 'east'))
    for (x, z) in ((cx, cz), (cx + 1, cz), (cx, cz + 1), (cx - 1, cz - 1)):
        t.set(x, F, z, S('moss_carpet'))
    t.set(cx + 2, F, cz + 1, S('or:watch_stone'))
    t.set(cx + 2, F + 1, cz + 1, slab('or:watch_stone_slab', 'bottom'))
    t.set(cx - 2, F, cz + 2, S('cobweb'))
    rooms.append((x0 + 1, F, z0 + 1, x1 - 1, F + 5, z1 - 1))
    # --- the broken top: fallen stones on the floor and on the slope below
    for (x, z) in ((cx + 1, cz - 1), (cx + 2, cz), (cx - 1, cz + 2), (cx + 2, cz + 2)):
        if not t.is_air(x, 25, z):
            t.set(x, 26, z, pick(rng, [(S('or:watch_stone'), 2), (slab('or:watch_stone_slab', 'bottom'), 2), (S('cobblestone'), 1)]))
    for (x, z) in ((19, 6), (20, 7), (18, 5), (21, 9), (20, 4), (17, 4)):
        if t.get(x, 1, z) is None:
            continue  # off the rock: the stone rolled on down the slope
        t.set(x, 2, z, pick(rng, [(S('or:watch_stone'), 2), (S('mossy_cobblestone'), 2), (slab('or:watch_stone_slab', 'bottom'), 1)]))
        t.set(x, 1, z, S('stone'))
        t.set(x, 0, z, S('stone'))
    # --- garden east: overgrown beds round a fallen statue
    for x in range(18, 24):
        for z in range(10, 20):
            if math.hypot(x - 20.5, z - 15) > 4.8:
                continue
            t.set(x, 0, z, S('stone'))
            edge = math.hypot(x - 20.5, z - 15) > 3.8
            t.set(x, 1, z, S('or:watch_stone') if edge else S('rooted_dirt') if (x + z) % 2 else S('moss_block'))
            if edge and rng.random() < 0.6:
                t.set(x, 2, z, S('or:watch_stone_wall'))
            elif not edge and rng.random() < 0.7:
                t.set(x, 2, z, pick(rng, [(S('fern'), 3), (S('sweet_berry_bush', age=3), 2), (S('azalea'), 1),
                                          (S('firefly_bush'), 1), (S('bush'), 2)]))
    # the statue of a robed watcher, fallen across the beds
    t.set(20, 2, 13, S('or:ruin_stone_carved'))
    t.set(20, 2, 14, S('or:ruin_pillar', axis='z'))
    t.set(20, 2, 15, S('or:ruin_pillar', axis='z'))
    t.set(20, 2, 16, S('or:ruin_stone'))
    t.set(21, 2, 16, slab('or:ruin_brick_slab', 'bottom'))
    t.set(20, 2, 17, S('or:ruin_bricks'))
    # --- the meditation ledge on the north-west rocks
    ledge = [(x, z) for x in range(1, 8) for z in range(1, 8) if math.hypot(x - 4, z - 4) <= 2.6]
    rail = [(x, z) for x in range(0, 9) for z in range(0, 9) if 2.6 < math.hypot(x - 4, z - 4) <= 3.6 and (x < 5 or z < 5)
            and not (x >= 5 and z >= 5)]
    meditation_ledge(t, rng, ledge, (3, 3), 'north', rail, [c for c in rail if (c[0] * 2 + c[1]) % 3 == 0])
    for (x, z) in ((6, 6), (7, 7), (7, 8), (8, 9)):
        t.set(x, 0, z, S('stone'))
        t.set(x, 1, z, S('or:watch_stone') if (x + z) % 2 else S('mossy_cobblestone'))
    # moss and vines on the walls, roots and turf
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(2, 26):
                if t.name_at(x, y, z) == 'oldrepublic:watch_stone' and rng.random() < 0.08:
                    t.set(x, y, z, S('mossy_stone_bricks'))
    for (x, z, f) in ((x0 - 1, cz + 2, 'east'), (x1 + 1, cz - 2, 'west'), (cx - 2, z1 + 1, 'north')):
        for y in range(3, 3 + rng.randint(3, 7)):
            if t.is_air(x, y, z):
                t.set(x, y, z, S('vine', north=(f == 'north'), south=(f == 'south'), east=(f == 'east'), west=(f == 'west'), up=False))
    turf = [(x, 1, z) for x in range(SX) for z in range(SZ) if t.name_at(x, 1, z) in
            ('minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block', 'minecraft:coarse_dirt') and t.is_air(x, 2, z)]
    scatter_on(t, rng, turf, [(S('short_grass'), 5), (S('fern'), 3), (S('bush'), 1), (S('leaf_litter', facing='east', segment_amount=3), 1)], 0.3)
    carve(t, 0, 0, SX - 1, SZ - 1, G, G + 9, bank=1, noise=Noise(6499, 3.0))
    light_rooms(t, rooms)
    return t


# ---------------------------------------------------------------- worldgen

def write_worldgen():
    loot_table(LOOT, [
        (1, [loot_item('oldrepublic:lumen_thread', count=(1, 2))]),
        (1, [loot_item('paper', count=(2, 4))]),
        (1, [loot_item('book')]),
        (1, [loot_item('candle', count=(1, 2))]),
        (1, [loot_item('emerald', count=(1, 2))]),
    ])
    ad = adapted(SY, yard_stand=G, support_depth=8, ramp_rings=2)
    wilds_family('watch_hermitage', [('wilds/watch_hermitage/spire', ad, 1), ('wilds/watch_hermitage/ruin', ad, 1)],
                 ['#minecraft:is_hill', 'minecraft:meadow', '#minecraft:is_taiga', 'minecraft:stony_peaks'],
                 spacing=52, separation=20, salt=960103, buried=BURIED)


def main():
    args = cli(__doc__)
    for name, build in (('spire', build_spire), ('ruin', build_ruin)):
        t = build()
        print(name, t.size, t.count())
        save(t, 'wilds/watch_hermitage/' + name, args.preview)
    write_worldgen()


if __name__ == '__main__':
    main()
