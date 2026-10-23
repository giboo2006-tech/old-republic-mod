"""The silenced city's lots (tyrvann/silenced_city, worker P4): the buildings round the plaza.

Every lot is 31 cells wide and faces the plaza across its own street (z 0..4); its jigsaw sits at
(15, 2, 0) facing north, so once it is attached north is the plaza and the singing stone, and that is
where every chair in it looks. The lots share the city's frame (city_kit): footings at y 0, paving and
standing water at y 1, the street at y 2. Pools (silenced_city.write_worldgen): the landmarks (song hall,
bell tower), the houses (townhouses, courtyard house), the waterfront (quay, colonnade) and the ruins
(the sunken block); the plaza draws one lot per side, each side from its own pool.
"""
from city_kit import *  # noqa: F401,F403

PIECES = 'tyrvann/silenced_city/'
LOT_W = 31
ROWS = 5                                  # the street strip z 0..4
LOOT_ID = 'oldrepublic:chests/tyrvann/silenced_city'
STREET, LOT = 'oldrepublic:city_street', 'oldrepublic:city_lot'
CAMERAS = {}
LOOK = 'north'                            # where the plaza (and the stone) lies, seen from a lot


def new_lot(name, height, depth, seed):
    return Template(LOT_W, height, depth, seed=seed, name=PIECES + name)


def lot_jigsaw(t, x=15, z=0):
    t.jigsaw(x, WK, z, 'north_up', name=LOT, target=STREET, pool='minecraft:empty')
    t.set(x, WK + 1, z, AIR)


def lot_street(t, rng, seed, lamps=(3, 27), lost_rate=0.08, chairs=5, rows=ROWS):
    """The street along the front of a lot, continuing the plaza's paving, a kerb of dark setts at the
    house fronts, two lamps, a few chairs carried out to listen (turned to the plaza), the jigsaw."""
    cells = [(x, z) for x in range(LOT_W) for z in range(rows)]

    def lost(x, z, h):
        return h.random() < lost_rate and not (13 <= x <= 17 and z <= 1)
    gone = paving(t, rng, cells, seed, lost=lost)
    for x in range(LOT_W):
        if (x, rows - 1) not in gone:
            t.set(x, WL, rows - 1, pick(rng, [('minecraft:polished_tuff', 3), ('minecraft:tuff_bricks', 2),
                                              ('minecraft:cobbled_deepslate', 1)]))
    for x in lamps:
        lamp_post(t, x, rows - 1, lit=rng.random() < 0.6, broken=rng.random() < 0.25)
    flood(t, gone)
    spots = [(x, z) for (x, z) in cells if 1 <= z <= rows - 2 and (x, z) not in gone and abs(x - 15) > 1]
    rng.shuffle(spots)
    for (x, z) in spots[:chairs]:
        if t.is_air(x, WK, z):
            t.set(x, WK, z, stair('minecraft:pale_oak_stairs', opposite(LOOK)) if rng.random() > 0.2
                  else slab('minecraft:pale_oak_slab'))
    lot_jigsaw(t)
    return cells, gone


def yard(t, rng, cells, seed, lost_rate=0.15):
    """A paved back yard (flags at the water line, some lost and flooded where they sit in a basin)."""
    gone = paving(t, rng, sorted(cells), seed, lost=lambda x, z, h: h.random() < lost_rate)
    flood(t, gone)
    return gone


def garden_wall(t, rng, cells, h=1, broken=0.2, state='minecraft:mossy_stone_brick_wall'):
    """A low wall on footings, broken here and there (a stump of rubble where it fell)."""
    for (x, z) in cells:
        t.set(x, 0, z, pick(rng, FOOTING))
        t.set(x, WL, z, pick(rng, [('minecraft:mossy_stone_bricks', 2), ('minecraft:stone_bricks', 1),
                                   ('minecraft:cracked_stone_bricks', 1)]))
        if rng.random() < broken:
            if rng.random() < 0.5:
                t.set(x, WK, z, slab('minecraft:mossy_stone_brick_slab'))
            continue
        for k in range(h):
            t.set(x, WK + k, z, state)


def well(t, x, z, roof=True):
    """A town well: a ring of diorite wall round water, two posts and a little roof, a chain into the water."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            t.set(x + dx, 0, z + dz, 'minecraft:cobbled_deepslate')
            if (dx, dz) == (0, 0):
                t.set(x, WL, z, WATER)
                t.set(x, WK, z, AIR)
            else:
                t.set(x + dx, WL, z + dz, 'minecraft:polished_diorite')
                t.set(x + dx, WK, z + dz, 'minecraft:diorite_wall')
    if roof:
        for k in (1, 2):
            t.set(x - 1, WK + k, z, S('pale_oak_fence'))
            t.set(x + 1, WK + k, z, S('pale_oak_fence'))
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                t.set(x + dx, WK + 3, z + dz, slab('minecraft:pale_oak_slab'))
        t.set(x, WK + 2, z, chain('y'))
        t.set(x, WK + 1, z, chain('y'))


def chimney(t, x, z, y0, y1):
    for y in range(y0, y1 + 1):
        t.set(x, y, z, 'minecraft:mud_bricks' if y % 3 else 'minecraft:bricks')
    t.set(x, y1 + 1, z, 'minecraft:mud_brick_wall')


def overgrow_lot(t, rng, box, moss=0.08, vines=0.05):
    (x0, y0, z0), (x1, y1, z1) = box
    hang_moss(t, rng, box, chance=moss, max_len=3)
    vines_on(t, rng, box, chance=vines, max_len=3)


def finish_lot(t, open_cells, y_top=WK + 6):
    open_air(t, open_cells, WK, y_top)
    return guard_trees(t)


# ==================================================================== the houses: townhouses

def townhouses(seed):
    """A row of three tall pale houses on the street, the middle one's roof fallen in, yards behind with a
    well, a shed and a drowned garden. Every upper room has its chairs at the front windows."""
    D, H = 24, 23
    t = new_lot('townhouses', H, D, seed)
    rng = t.rng
    street, _ = lot_street(t, rng, seed + 1, lamps=(10, 21))
    h1 = house(t, rng, 1, 5, 9, 10, storeys=3, ridge='z', front='north', doors=(4,), seed=seed + 11, face_to=LOOK)
    h2 = house(t, rng, 12, 5, 8, 9, storeys=2, ridge='x', front='north', doors=(2, 5), seed=seed + 12,
               collapse='roof', face_to=LOOK)
    h3 = house(t, rng, 22, 5, 8, 11, storeys=2, roof='hip', front='north', doors=(3,), seed=seed + 13, face_to=LOOK)
    chimney(t, 18, 13, 9, 15)
    # a balcony on the third house, over the street, with two chairs turned to the plaza
    for x in range(23, 29):
        for z in range(2, 5):
            edge = x in (23, 28) or z == 2
            t.set(x, 5, z, slab('minecraft:pale_oak_slab', 'top'))
            t.set(x, 6, z, S('pale_oak_fence') if edge else AIR)
            t.set(x, 7, z, AIR)
    for x in (24, 27):
        t.set(x, 4, 4, stair('minecraft:pale_oak_stairs', 'south', 'top'))
    t.set(25, 6, 5, AIR)
    t.set(25, 7, 5, AIR)
    t.set(24, 6, 3, stair('minecraft:pale_oak_stairs', opposite(LOOK)))
    t.set(26, 6, 4, stair('minecraft:pale_oak_stairs', opposite(LOOK)))
    t.set(27, 7, 2, lantern(soul=True))
    # the alleys between the houses
    alleys = [(x, z) for x in (10, 11, 20, 21) for z in range(5, 15)]
    yard(t, rng, alleys, seed + 2, lost_rate=0.1)
    # the yards behind, walled at the back
    yard1 = [(x, z) for x in range(0, 10) for z in range(15, 22)]
    yard2 = [(x, z) for x in range(10, 21) for z in range(14, 22) if not (x == 10 and z == 14)]
    yard3 = [(x, z) for x in range(21, 31) for z in range(16, 22)]
    yard(t, rng, yard1, seed + 3)
    yard(t, rng, yard2, seed + 4, lost_rate=0.25)
    pond = [(x, z) for x in range(23, 29) for z in range(17, 21)]
    garden = [p for p in yard3 if p not in pond]
    soil = earth_patch(t, rng, garden)
    for (x, z) in pond:
        t.set(x, 0, z, 'minecraft:mud')
        t.set(x, WL, z, WATER)
        if rng.random() < 0.35:
            t.set(x, WK, z, 'minecraft:lily_pad')
    for (x, z) in soil:
        if rng.random() < 0.3:
            t.set(x, WK, z, pick(rng, [('minecraft:dead_bush', 3), ('minecraft:fern', 1), ('minecraft:short_grass', 2)]))
    garden_wall(t, rng, [(x, 22) for x in range(0, 31)], broken=0.25)
    garden_wall(t, rng, [(10, z) for z in range(16, 22) if z != 18] + [(21, z) for z in range(17, 22) if z != 19],
                broken=0.3)
    well(t, 5, 18)
    # a lean-to shed against the middle house, a washing line, its fallen roof in the yard
    for x in range(13, 18):
        for z in (14, 15, 16):
            if z == 16 and x in (13, 17):
                for y in (WK, WK + 1):
                    t.set(x, y, z, S('pale_oak_fence'))
            t.set(x, WK + 2, z, stair('minecraft:pale_oak_stairs', 'south') if z == 16 else slab('minecraft:pale_oak_slab', 'top'))
    t.set(14, WK, 15, barrel('up'))
    t.set(15, WK, 15, barrel('north'))
    t.set(16, WK, 15, S('cauldron'))
    for x in (12, 19):
        for y in range(WK, WK + 3):
            t.set(x, y, 20, S('pale_oak_fence'))
    for x in range(13, 19):
        t.set(x, WK + 2, 20, chain('x'))
    for (x, z) in ((13, 18), (16, 19), (18, 17), (14, 21)):
        if t.is_air(x, WK, z):
            t.set(x, WK, z, pick(rng, [(slab(ROOF_SLAB), 2), (stair(ROOF, rng.choice(DIRS4)), 2), ('oldrepublic:debris', 1)]))
    # a dead pale tree at the pond
    tx, tz = 29, 18
    t.set(tx, WL, tz, 'minecraft:rooted_dirt')
    for y in range(WK, WK + 6):
        t.set(tx, y, tz, log('minecraft:pale_oak_log'))
    t.set(tx - 1, WK + 4, tz, log('minecraft:pale_oak_log', 'x'))
    t.set(tx - 2, WK + 5, tz, log('minecraft:pale_oak_log', 'x'))
    t.set(tx, WK + 5, tz - 1, log('minecraft:pale_oak_log', 'z'))
    for (x, y, z, n) in ((tx - 2, WK + 4, tz, 3), (tx - 1, WK + 3, tz, 2), (tx, WK + 4, tz - 1, 2)):
        for k in range(n):
            if not t.is_solid(x, y - k, z):
                t.set(x, y - k, z, moss_hang(k == n - 1))
    # the chest: the top room of the tall house, against its back wall
    t.chest(5, 10, 13, 'north', loot=LOOT_ID)
    for k in range(3):
        root_veins(t, rng, (rng.choice((1, 9, 12, 19, 22, 29)), rng.randint(7, 11), rng.choice((5, 14))), 7)
    open_cells = [(x, z) for x in range(LOT_W) for z in range(ROWS)] + alleys + yard1 + yard2 + yard3
    open_air(t, open_cells, WK, WK + 6)
    overgrow_lot(t, rng, ((0, 2, 0), (30, H - 1, D - 1)))
    prune_floating(t)
    guard_trees(t)
    CAMERAS[PIECES + 'townhouses'] = ((15, WK + 1, 2), (5, 9, 9))
    return t


# ==================================================================== the landmarks: the song hall

def song_hall(seed):
    """The city's song hall, where the Hymn set its organ: a pale basilica with aisles and a clerestory, the
    middle bays fallen in, the nave flooded. Every pew has been turned round to face the door, and the
    stone beyond it; the cantor's lectern stands at the organ facing their backs."""
    D, H = 31, 28
    t = new_lot('song_hall', H, D, seed)
    rng = t.rng
    lot_street(t, rng, seed + 1, lamps=(5, 25), chairs=3)
    body = body_picker(seed + 5, tide_to=2)
    X0, X1, Z0, Z1 = 6, 24, 6, 26          # the outer walls
    AL, AR = 10, 20                         # the arcades
    PIERS = (9, 12, 15, 18, 21, 24)         # z of the arcade piers
    EAVE_A, EAVE_N, APSE_TOP = 8, 17, 14    # the aisle roofs' foot, the nave's eaves, the apse's roof
    AC, AZ = 15, 26                         # the apse's centre
    apse = {(x, z) for x in range(AC - 5, AC + 6) for z in range(AZ, D) if math.hypot(x - AC, z - AZ) <= 4.6}
    apse_in = {(x, z) for (x, z) in apse if math.hypot(x - AC, z - AZ) < 3.6}
    fore = [(x, z) for x in range(1, 30) for z in range(ROWS, Z0)]
    sides = [(x, z) for x in list(range(1, X0)) + list(range(X1 + 1, 30)) for z in range(Z0, 22)]
    yard(t, rng, fore + sides, seed + 2, lost_rate=0.12)
    hall = [(x, z) for x in range(X0, X1 + 1) for z in range(Z0, Z1 + 1)]
    for (x, z) in hall + sorted(apse):
        t.set(x, 0, z, pick(rng, FOOTING))
    inner = sorted(set((x, z) for x in range(X0 + 1, X1) for z in range(Z0 + 1, Z1)) | apse_in)
    gone = flag_floor(t, inner, WL, seed + 3, [('oldrepublic:pale_tiles', 3), ('minecraft:polished_diorite', 2),
                                               ('minecraft:calcite', 1)], lost=lambda x, z, h: h.random() < 0.3,
                      lost_state=silt)
    # ---------------------------------------------------------------- walls
    for (x, z) in hall:
        if x in (X0, X1) or z in (Z0, Z1):
            for y in range(1, EAVE_A):
                t.set(x, y, z, body(x, y, z))
    for (x, z) in sorted(apse - apse_in):
        for y in range(1, APSE_TOP):
            t.set(x, y, z, body(x, y, z))
    for z in (Z0, Z1):                                      # the nave's end walls and gables
        for x in range(AL, AR + 1):
            for y in range(EAVE_A, EAVE_N):
                t.set(x, y, z, body(x, y, z))
        for i in range(0, 7):
            for x in range(AL + i, AR - i + 1):
                t.set(x, EAVE_N + i, z, body(x, EAVE_N + i, z))
    for (x, z) in sorted(set(inner) - {(x, z) for (x, z) in apse_in if False}):
        for y in range(2, EAVE_N + 6):
            if (x, z) in apse_in and y >= APSE_TOP:
                continue
            t.set(x, y, z, AIR)
    # the arcades: piers, round arches, the clerestory wall above
    for xa in (AL, AR):
        for z in range(Z0 + 1, Z1):
            for y in range(1, EAVE_N):
                t.set(xa, y, z, body(xa, y, z) if y >= 7 else AIR)
            if t.is_air(xa, 1, z):
                t.set(xa, 1, z, 'minecraft:polished_diorite')
        for zp in PIERS:
            for y in range(1, 7):
                t.set(xa, y, zp, 'minecraft:calcite' if y == 1 else ('minecraft:chiseled_stone_bricks' if y == 6
                                                                     else 'minecraft:polished_diorite'))
        for zp in PIERS[:-1]:
            t.set(xa, 6, zp + 1, stair('minecraft:polished_diorite_stairs', 'north', 'top'))
            t.set(xa, 6, zp + 2, stair('minecraft:polished_diorite_stairs', 'south', 'top'))
            for y in (13, 14, 15):
                for dz in (1, 2):
                    window(t, xa, y, zp + dz, 'glass' if (zp + y + dz) % 4 else 'broken', h=1)
    # the aisle roofs, lean-to from the outer walls up to the arcade walls
    for z in range(Z0 - 1, Z1 + 2):
        for k in range(0, 5):
            t.set(X0 - 1 + k, EAVE_A + k, z, stair('minecraft:pale_oak_stairs', 'east'))
            t.set(X1 + 1 - k, EAVE_A + k, z, stair('minecraft:pale_oak_stairs', 'west'))
    for z in (Z0, Z1):
        for k in range(1, 5):
            for y in range(EAVE_A, EAVE_A + k):
                t.set(X0 - 1 + k, y, z, body(X0 - 1 + k, y, z))
                t.set(X1 + 1 - k, y, z, body(X1 + 1 - k, y, z))
    # the nave roof in pale stone, the apse's roof
    shapes.gable_roof(t, AL - 1, Z0 - 1, AR + 1, Z1 + 1, EAVE_N, STONE_ROOF, ridge='z')
    for (x, z) in sorted(apse):
        if z > Z1:
            d = math.hypot(x - AC, z - AZ)
            t.set(x, APSE_TOP, z, 'minecraft:polished_diorite' if d < 4.0 else slab('minecraft:polished_diorite_slab'))
            if d < 2.9:
                t.set(x, APSE_TOP + 1, z, slab('minecraft:polished_diorite_slab'))
    for x in range(AC - 2, AC + 3):                         # the arch from the nave into the apse
        for y in range(2, APSE_TOP - 2 + (1 if abs(x - AC) < 2 else 0)):
            t.set(x, y, Z1, AIR)
    # ---------------------------------------------------------------- the facade
    for x in (AL - 1, AR + 1):
        for y in range(1, EAVE_N + 1):
            t.set(x, y, Z0 - 1, 'minecraft:calcite' if y % 4 else 'minecraft:polished_diorite')
        t.set(x, 0, Z0 - 1, 'minecraft:cobbled_deepslate')
    for x in range(AC - 1, AC + 2):
        t.set(x, 1, Z0, 'minecraft:polished_diorite')
        for y in range(2, 6):
            t.set(x, y, Z0, AIR)
    t.set(AC - 1, 5, Z0, stair('minecraft:polished_diorite_stairs', 'west', 'top'))
    t.set(AC + 1, 5, Z0, stair('minecraft:polished_diorite_stairs', 'east', 'top'))
    t.set(AC, 6, Z0, 'minecraft:chiseled_stone_bricks')
    for x in (X0 + 2, X1 - 2):
        t.set(x, 1, Z0, 'minecraft:polished_diorite')
        t.set(x, 2, Z0, AIR)
        t.set(x, 3, Z0, AIR)
    for x in range(AC - 3, AC + 4):                         # the round window: panes, a stone cross, a stone ring
        for y in range(8, 15):
            d = math.hypot(x - AC, y - 11)
            if d <= 2.6:
                t.set(x, y, Z0, 'minecraft:polished_diorite' if (x == AC or y == 11) else
                      ('minecraft:glass_pane' if (x + y) % 3 else AIR))
            elif d <= 3.4:
                t.set(x, y, Z0, 'minecraft:calcite')
    for z in range(Z0 + 2, Z1 - 1, 3):
        window(t, X0, 3, z, 'glass' if z % 2 else 'broken')
        window(t, X1, 3, z, 'bars' if z % 2 else 'glass')
    # ---------------------------------------------------------------- the apse: dais, organ, lectern, chest
    for (x, z) in sorted(apse_in) + [(x, z) for x in range(AL + 1, AR) for z in (Z1 - 2, Z1 - 1)]:
        t.set(x, WL, z, 'minecraft:polished_deepslate')
        t.set(x, WK, z, stair('minecraft:polished_deepslate_stairs', 'south') if z == Z1 - 2
              else 'minecraft:polished_deepslate')
    for x in range(AC - 2, AC + 3):
        t.set(x, WK, Z1, 'minecraft:polished_deepslate')
    for (x, z) in sorted(apse_in):
        d = math.hypot(x - AC, z - AZ)
        if d >= 2.3 and z >= AZ:
            h = 9 - int(abs(x - AC) * 1.3) + (1 if (x + z) % 2 else 0)
            t.set(x, WK + 1, z, 'minecraft:oxidized_cut_copper' if (x + z) % 2 else 'minecraft:oxidized_copper')
            for k in range(max(3, h)):
                t.set(x, WK + 2 + k, z, rod())
    t.set(AC, WK + 1, Z1 - 1, S('lectern', facing='south', has_book=False, powered=False))
    for (x, z) in ((AC - 2, Z1 - 1), (AC + 2, Z1 - 1)):
        t.set(x, WK + 1, z, candle(3, 'black', lit=False))
    t.chest(AC + 1, WK + 1, AZ + 1, 'west', loot=LOOT_ID)
    t.set(AC - 1, WK + 1, AZ + 1, barrel('up'))
    hanging_lantern(t, AC, APSE_TOP, AZ + 2, 3, soul=True, copper_chain=True)
    # ---------------------------------------------------------------- pews, all turned round to face the door
    for z in range(Z0 + 3, Z1 - 3, 2):
        for x in list(range(AL + 1, AC - 1)) + list(range(AC + 2, AR)):
            if not t.is_solid(x, WL, z) or not t.is_air(x, WK, z):
                continue
            r = rng.random()
            if r < 0.12:
                continue
            t.set(x, WK, z, slab('minecraft:pale_oak_slab') if r < 0.22 else stair('minecraft:pale_oak_stairs', 'south'))
    flood(t, [p for p in gone if t.is_air(p[0], WK, p[1])])
    for z in (Z0 + 4, Z1 - 2):
        hanging_lantern(t, AC, EAVE_N + 6, z, 8, soul=True, copper_chain=True)
    for zp in PIERS[1:-1]:
        for xa, f in ((AL, 'east'), (AR, 'west')):
            if rng.random() < 0.5:
                t.set(xa + (1 if f == 'east' else -1), 5, zp, S('soul_wall_torch', facing=f))
    # ---------------------------------------------------------------- the fallen bays
    jag = Noise(seed + 9, 2.0)
    for (x, y, z) in sorted(p for p in t.cells if p[1] >= EAVE_A):
        reach = 4.2 + 1.6 * jag.at(x, z) - (x - AL) * 0.12
        if abs(z - 16.5) > reach or x > AC + 2 + 2 * jag.at(z, y):
            continue
        if y >= EAVE_N - 1 or (x <= AL and y >= 9 + int(4 * jag.at(y, z))) or (x < AL and rng.random() < 0.6):
            if rng.random() < 0.93:
                t.set(x, y, z, AIR)
    for _ in range(44):
        x, z = rng.randint(AL - 3, AC + 3), rng.randint(12, 21)
        rubble(t, rng, x, WK + 3, z, [(slab('minecraft:polished_diorite_slab'), 2), ('minecraft:calcite', 2),
                                      ('oldrepublic:debris', 3), (stair(STONE_ROOF, rng.choice(DIRS4)), 2),
                                      (stair('minecraft:pale_oak_stairs', rng.choice(DIRS4)), 2)], n=1, spread=1.0)
    for (x, z) in ((AC - 2, 16), (AC - 3, 15), (AC - 1, 17)):
        if t.is_air(x, WK + 1, z) and t.is_solid(x, WK, z):
            t.set(x, WK + 1, z, cluster('up'))
            break
    for k in range(3):
        root_veins(t, rng, (rng.choice((X0, X1)), rng.randint(5, 7), rng.randint(9, 22)), 6)
    open_air(t, [(x, z) for x in range(LOT_W) for z in range(ROWS)] + fore + sides, WK, WK + 6)
    hang_moss(t, rng, ((X0, 6, Z0), (X1, H - 1, Z1)), chance=0.1, max_len=4)
    vines_on(t, rng, ((0, 2, 0), (30, 16, D - 1)), chance=0.04, max_len=3)
    prune_floating(t)
    guard_trees(t)
    CAMERAS[PIECES + 'song_hall'] = ((AC, WK + 1, Z0 + 1), (AC, 7, Z1 - 1))
    return t


# ==================================================================== the waterfront: the quay

def crate_stack(t, rng, x, z, y0, h):
    for k in range(h):
        t.set(x, y0 + k, z, pick(rng, [('oldrepublic:cargo_crate', 3), (barrel(rng.choice(('up', 'north', 'east'))), 1)]))


def quay(seed):
    """The waterfront: a warehouse with its roof fallen in and its cargo standing in the flood, the
    harbour master's house, the quay with bollards and a crane, the canal walled at both ends with a sunk
    boat and an arched bridge, and the far bank."""
    D, H = 28, 20
    t = new_lot('quay', H, D, seed)
    rng = t.rng
    lot_street(t, rng, seed + 1, lamps=(17, 29), chairs=4)
    body = body_picker(seed + 5, tide_to=2)
    # ---------------------------------------------------------------- the warehouse
    WX0, WX1, WZ0, WZ1, WTOP = 2, 15, 6, 15, 7
    for x in range(WX0, WX1 + 1):
        for z in range(WZ0, WZ1 + 1):
            t.set(x, 0, z, pick(rng, FOOTING))
            edge = x in (WX0, WX1) or z in (WZ0, WZ1)
            for y in range(1, WTOP + 1):
                if edge:
                    t.set(x, y, z, log(TIMBER, 'y') if (x in (WX0, WX1) and z in (WZ0, WZ1)) else body(x, y, z))
                else:
                    t.set(x, y, z, WATER if y == 1 else AIR)
    for x in range(WX0, WX1 + 1):
        for z in (WZ0, WZ1):
            t.set(x, WTOP, z, log(TIMBER, 'x'))
    for z in range(WZ0, WZ1 + 1):
        for x in (WX0, WX1):
            t.set(x, WTOP, z, log(TIMBER, 'z'))
    for z in (WZ0, WZ1):                                     # the loading doors front and back
        for x in range(7, 10):
            t.set(x, 1, z, 'minecraft:polished_diorite')
            for y in range(2, 6):
                t.set(x, y, z, AIR)
        t.set(6, 5, z, log(TIMBER, 'x'))
        t.set(10, 5, z, log(TIMBER, 'x'))
        for x in range(7, 10):
            t.set(x, 6, z, log(TIMBER, 'x'))
    for x in (4, 12):
        window(t, x, 4, WZ0, 'bars')
        window(t, x, 4, WZ1, 'broken')
    for z in (8, 11, 13):
        window(t, WX0, 4, z, 'bars')
        window(t, WX1, 4, z, 'glass' if z % 2 else 'broken')
    for x in range(WX0 + 1, WX1):                            # the floor beams that held the loft
        if x % 3 == 0:
            t.set(x, 5, WZ0 + 1, log('minecraft:stripped_dark_oak_log', 'z'))
    shapes.gable_roof(t, WX0 - 1, WZ0 - 1, WX1 + 1, WZ1 + 1, WTOP + 1, ROOF, ridge='x')
    for x in (WX0, WX1):
        for i in range(1, 6):
            for z in range(WZ0 + i, WZ1 - i + 1):
                t.set(x, WTOP + i, z, body(x, WTOP + i, z))
    for (x, y, z) in sorted(p for p in t.cells if p[1] > WTOP):
        if WX0 - 1 <= x <= WX1 + 1 and math.hypot(x - 11.5, (z - 10.5) * 0.9) < 4.6 + rng.random() * 0.8:
            t.set(x, y, z, AIR)
    for (x, z, h) in ((3, 7, 3), (4, 7, 2), (3, 8, 2), (3, 14, 3), (4, 14, 1), (14, 7, 2), (14, 14, 3),
                      (13, 14, 2), (14, 13, 1), (5, 10, 1), (6, 12, 2)):
        crate_stack(t, rng, x, z, 1, h)
    t.set(10, 2, 9, log('minecraft:stripped_pale_oak_log', 'x'))
    t.set(11, 2, 9, log('minecraft:stripped_pale_oak_log', 'x'))
    t.set(12, 2, 9, log('minecraft:stripped_pale_oak_log', 'x'))
    for x in range(9, 14):
        for z in range(8, 13):
            if t.is_air(x, 2, z) and rng.random() < 0.25:
                t.set(x, 2, z, pick(rng, [(slab(ROOF_SLAB), 2), (stair(ROOF, rng.choice(DIRS4)), 2), ('oldrepublic:debris', 1)]))
    song_pipe(t, WX1 + 1, WZ0 + 2, WK, 8)
    t.set(WX1 + 1, 0, WZ0 + 2, 'minecraft:cobbled_deepslate')
    t.set(WX1 + 1, 1, WZ0 + 2, 'minecraft:polished_diorite')
    # ---------------------------------------------------------------- the harbour master's house
    hm = house(t, rng, 18, 6, 10, 8, storeys=2, roof='hip', front='north', doors=(2,), seed=seed + 12, face_to=LOOK)
    t.chest(26, 6, 9, 'west', loot=LOOT_ID)
    # ---------------------------------------------------------------- the quay
    walk = [(x, z) for x in range(1, 30) for z in range(16, 20)] + [(16, z) for z in range(6, 16)] + \
        [(17, z) for z in range(6, 16)] + [(x, z) for x in range(18, 30) for z in (14, 15)]
    yard(t, rng, walk, seed + 3, lost_rate=0.06)
    for x in range(1, 30):
        t.set(x, WL, 19, pick(rng, [('minecraft:polished_deepslate', 3), ('minecraft:deepslate_tiles', 1)]))
    for x in (3, 10, 20, 27):
        t.set(x, WK, 19, 'minecraft:polished_deepslate_wall')
    for (x, z, h) in ((21, 15, 2), (22, 15, 1), (28, 14, 2), (28, 15, 3), (12, 17, 2), (13, 17, 1), (2, 17, 1)):
        crate_stack(t, rng, x, z, WK, h)
    # the crane on the quay's edge, its hook over the canal
    cx, cz = 24, 18
    t.set(cx, WL, cz, 'minecraft:polished_deepslate')
    for y in range(WK, WK + 9):
        t.set(cx, y, cz, log('minecraft:dark_oak_log'))
    for z in range(cz - 1, cz + 6):
        t.set(cx, WK + 9, z, log('minecraft:dark_oak_log', 'z'))
    t.set(cx, WK + 8, cz + 1, stair('minecraft:dark_oak_stairs', 'north', 'top'))
    t.set(cx, WK + 8, cz - 1, stair('minecraft:dark_oak_stairs', 'south', 'top'))
    for y in range(WK + 3, WK + 9):
        t.set(cx, y, cz + 5, chain('y'))
    t.set(cx, WK + 2, cz + 5, 'oldrepublic:cargo_crate')
    # ---------------------------------------------------------------- the canal, walled at both ends
    for x in range(0, 31):
        for z in range(20, 26):
            t.set(x, 0, z, 'minecraft:mud' if (x + z) % 3 else 'minecraft:clay')
            if x in (0, 30):
                t.set(x, WL, z, 'minecraft:mossy_stone_bricks')
                t.set(x, WK, z, 'minecraft:mossy_stone_brick_wall')
                t.set(x, WK + 1, z, S('iron_bars'))
            else:
                t.set(x, WL, z, WATER)
    for x in (0, 30):
        for z in (19, 26):
            for y in range(WK, WK + 3):
                t.set(x, y, z, 'minecraft:mossy_stone_bricks' if y < WK + 2 else slab('minecraft:mossy_stone_brick_slab'))
    for x in range(1, 30):                                   # the far bank
        t.set(x, 0, 26, pick(rng, FOOTING))
        t.set(x, WL, 26, 'minecraft:polished_deepslate')
        t.set(x, 0, 27, pick(rng, FOOTING))
        t.set(x, WL, 27, pick(rng, [('minecraft:mossy_stone_bricks', 2), ('minecraft:stone_bricks', 1)]))
        if rng.random() > 0.2:
            t.set(x, WK, 27, 'minecraft:mossy_stone_brick_wall')
    for x in (8, 22):
        for y in range(WL, WK + 3):
            t.set(x, y, 20, log('minecraft:dark_oak_log'))
    # an arched bridge at the west end
    for x in range(4, 7):
        for z in (20, 25):                                   # abutments on the canal's edges
            t.set(x, 0, z, 'minecraft:cobbled_deepslate')
            t.set(x, WL, z, 'minecraft:polished_diorite')
        prof = {19: WK - 1, 20: WK, 21: WK + 1, 22: WK + 1, 23: WK + 1, 24: WK + 1, 25: WK, 26: WK - 1}
        for z, y in prof.items():
            if z in (20, 25):
                t.set(x, y, z, stair('minecraft:polished_diorite_stairs', 'south' if z == 20 else 'north'))
            elif z in (19, 26):
                continue
            else:
                t.set(x, y, z, 'minecraft:polished_diorite')
                if x in (4, 6):
                    t.set(x, y + 1, z, 'minecraft:diorite_wall')
        t.set(x, WK, 21, stair('minecraft:polished_diorite_stairs', 'north', 'top'))
        t.set(x, WK, 24, stair('minecraft:polished_diorite_stairs', 'south', 'top'))
    # the sunk boat: a hull half under, its bow up on the bank's foot, the mast across the quay
    hull = [(x, z) for x in range(13, 21) for z in range(21, 25) if not ((x in (13, 20)) and z in (21, 24))]
    for (x, z) in hull:
        edge = any((x + dx, z + dz) not in hull for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            t.set(x, WL, z, stair('minecraft:dark_oak_stairs', 'north' if z == 21 else 'south' if z == 24 else
                                  ('west' if x <= 14 else 'east')))
        if x == 20:
            t.set(x, WK, z, slab('minecraft:dark_oak_slab'))
    t.set(19, 0, 22, 'minecraft:dark_oak_planks')
    t.set(18, 0, 23, 'minecraft:dark_oak_planks')
    for z in range(17, 22):
        t.set(16, WK, z, log('minecraft:stripped_dark_oak_log', 'z'))
    # ---------------------------------------------------------------- finish
    open_air(t, [(x, z) for x in range(LOT_W) for z in range(ROWS)] + walk +
             [(x, z) for x in range(1, 30) for z in range(20, 28)], WK, WK + 6)
    for x in range(1, 30):
        for z in range(20, 26):
            if t.name_at(x, WL, z) == 'minecraft:water' and t.is_air(x, WK, z) and rng.random() < 0.15:
                t.set(x, WK, z, 'minecraft:lily_pad')
    hang_moss(t, rng, ((WX0, 3, WZ0), (WX1, H - 1, WZ1)), chance=0.1, max_len=3)
    vines_on(t, rng, ((0, 2, 0), (30, 14, D - 1)), chance=0.05, max_len=3)
    prune_floating(t)
    guard_trees(t)
    CAMERAS[PIECES + 'quay'] = ((5, WK + 2, 22), (20, 4, 17))
    return t


# ==================================================================== the ruins: the sunken block

def sunken_block(seed):
    """A block that went down into the mire: an upper storey with the water through its windows, a roof
    with its house under it (the attic still dry, the chest in it), a corner left standing over a fallen
    roof, a dead stormwood, and a boardwalk on posts the survivors laid across it. Most of the lot is the
    mire's own water (left to the world)."""
    D, H = 27, 18
    t = new_lot('sunken_block', H, D, seed)
    rng = t.rng
    street, gone = lot_street(t, rng, seed + 1, lamps=(4,), lost_rate=0.22, chairs=3)
    for (x, z) in sorted(street):                       # the street's far edge breaking up into the water
        if z == ROWS - 1 and rng.random() < 0.4:
            t.set(x, WL, z, stair('minecraft:polished_diorite_stairs', rng.choice(DIRS4)) if rng.random() < 0.6
                  else slab('minecraft:polished_diorite_slab'))
    body = body_picker(seed + 5, tide_to=1)
    # ---------------------------------------------------------------- house A: the upper storey at the water
    ax0, az0, ax1, az1 = 1, 7, 8, 14
    for x in range(ax0, ax1 + 1):
        for z in range(az0, az1 + 1):
            edge = x in (ax0, ax1) or z in (az0, az1)
            t.set(x, 0, z, 'minecraft:pale_oak_planks' if not edge else body(x, 0, z))
            for y in range(1, 5):
                if edge:
                    t.set(x, y, z, log(TIMBER, 'y') if (x in (ax0, ax1) and z in (az0, az1)) else body(x, y, z))
                else:
                    t.set(x, y, z, WATER if y == 1 else AIR)
    for (x, z) in [(x, z) for x in range(ax0, ax1 + 1) for z in (az0, az1)] + \
            [(x, z) for z in range(az0, az1 + 1) for x in (ax0, ax1)]:
        k = (x - ax0) if z in (az0, az1) else (z - az0)
        if k % 2 == 1 and 0 < k < 7:
            window(t, x, 1, z, 'broken' if (x + z) % 3 else 'glass')
    t.set(4, 1, az0, AIR)
    t.set(4, 2, az0, AIR)
    t.set(4, 3, az0, AIR)
    for x in range(ax0, ax1 + 1):
        for z in range(az0, az1 + 1):
            if x in (ax0, ax1) or z in (az0, az1):
                t.set(x, 5, z, log(TIMBER, 'y') if (x in (ax0, ax1) and z in (az0, az1)) else
                      log(TIMBER, 'x' if z in (az0, az1) else 'z'))
    gable(t, body, ax0, az0, ax1, az1, 5, 'z')
    for (x, y, z) in sorted(p for p in t.cells if p[1] >= 5 and ax0 - 1 <= p[0] <= ax1 + 1 and az0 - 1 <= p[2] <= az1 + 1):
        if math.hypot(x - 6, z - 12) < 2.8 and rng.random() < 0.85:
            t.set(x, y, z, AIR)
    for (x, z) in ((3, 9), (5, 11), (6, 9)):
        t.set(x, 1, z, stair('minecraft:pale_oak_stairs', opposite(LOOK)))
    # ---------------------------------------------------------------- house B: the roof, the attic still dry
    bx0, bz0, bx1, bz1 = 12, 7, 20, 14
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            t.set(x, 0, z, 'minecraft:pale_oak_planks' if bx0 < x < bx1 and bz0 < z < bz1 else body(x, 0, z))
    for x in range(bx0 + 1, bx1):
        for z in range(bz0 + 1, bz1):
            t.set(x, 1, z, 'minecraft:pale_oak_planks')
            for y in (2, 3, 4):
                t.set(x, y, z, AIR)
    shapes.gable_roof(t, bx0, bz0, bx1, bz1, 1, ROOF, ridge='x', body=None)
    for x in (bx0, bx1):
        for i in range(0, 4):
            for z in range(bz0 + i + 1, bz1 - i):
                t.set(x, 1 + i, z, body(x, 1 + i, z))
        window(t, x, 2, 10, 'broken', h=1)
        window(t, x, 2, 11, 'broken', h=1)
    for (x, z) in ((15, 7), (16, 7), (16, 8), (15, 8)):     # the hole you drop in through
        t.set(x, 1 + (z - 7), z, AIR)
        t.set(x, 2 + (z - 7), z, AIR)
    t.set(15, 2, 8, AIR)
    chimney(t, 18, 12, 1, 6)
    t.chest(13, 2, 12, 'east', loot=LOOT_ID)
    t.set(13, 2, 11, barrel('up'))
    t.set(19, 2, 9, barrel('east'))
    t.set(17, 2, 12, candle(2, 'white', lit=False))
    # ---------------------------------------------------------------- house C: a corner standing, its roof fallen
    cx0, cz0, cx1, cz1 = 22, 8, 29, 15
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            edge = x in (cx0, cx1) or z in (cz0, cz1)
            if not edge:
                continue
            t.set(x, 0, z, pick(rng, FOOTING))
            d = abs(x - cx1) + abs(z - cz0)
            top = 9 - d if d < 6 else (2 if rng.random() < 0.6 else 1)
            for y in range(1, max(1, top) + 1):
                t.set(x, y, z, log(TIMBER, 'y') if (x, z) == (cx1, cz0) else body(x, y, z))
    window(t, cx1, 4, cz0 + 2, 'broken')
    window(t, cx1 - 2, 4, cz0, 'glass')
    for x in range(cx0 - 1, cx1 + 1):                        # the roof, slid off into the water as a raft
        for z in range(cz1 + 2, cz1 + 6):
            if (x + z) % 7 == 0:
                continue
            t.set(x, 0, z, pick(rng, FOOTING))
            t.set(x, WL, z, stair(ROOF, 'north' if z < cz1 + 4 else 'south') if (x * 3 + z) % 5 else slab(ROOF_SLAB, 'top'))
    # ---------------------------------------------------------------- the boardwalk on posts
    bw = [(10, z) for z in range(ROWS, 21)] + [(11, z) for z in range(ROWS, 21)] + \
        [(x, z) for x in range(12, 30) for z in (19, 20)] + [(x, z) for x in (21,) for z in range(16, 19)]
    for (x, z) in bw:
        t.set(x, WL, z, 'minecraft:dark_oak_planks' if rng.random() > 0.08 else slab('minecraft:dark_oak_slab', 'top'))
        if (x + z) % 3 == 0 or (x, z) in ((10, ROWS), (11, ROWS)):
            t.set(x, 0, z, log('minecraft:dark_oak_log'))
    for (x, z) in ((10, 9), (11, 15), (14, 19), (26, 20), (21, 17)):
        t.set(x, 0, z, log('minecraft:dark_oak_log'))
        for y in (WK, WK + 1):
            t.set(x, y, z, S('dark_oak_fence'))
        t.set(x, WK + 2, z, lantern(soul=True) if (x + z) % 2 else S('lantern', hanging=False, waterlogged=False))
    # ---------------------------------------------------------------- a dead stormwood in the water
    tx, tz = 6, 21
    for dx in (0, 1):
        for dz in (0, 1):
            t.set(tx + dx, 0, tz + dz, log('minecraft:dark_oak_log'))
            for y in range(WL, WL + 9 + dx - dz):
                t.set(tx + dx, y, tz + dz, log('minecraft:dark_oak_log'))
    for (x, y, z, ax) in ((tx - 1, 7, tz, 'x'), (tx - 2, 8, tz, 'x'), (tx + 2, 6, tz + 1, 'x'), (tx + 3, 7, tz + 1, 'x'),
                          (tx, 9, tz - 1, 'z'), (tx, 10, tz - 2, 'z'), (tx + 1, 10, tz, 'y'), (tx + 1, 11, tz, 'y')):
        t.set(x, y, z, log('minecraft:dark_oak_log', ax))
    for (x, z) in ((tx - 1, tz), (tx + 2, tz + 1), (tx, tz + 2), (tx + 1, tz - 1), (tx - 1, tz + 1), (tx + 2, tz - 1)):
        t.set(x, 0, z, roots())
        t.set(x, WL, z, S('muddy_mangrove_roots', axis='y'))
    for (x, y, z, n) in ((tx - 2, 7, tz, 3), (tx + 3, 6, tz + 1, 4), (tx, 8, tz - 2, 2), (tx - 1, 6, tz, 2)):
        for k in range(n):
            if not t.is_solid(x, y - k, z):
                t.set(x, y - k, z, moss_hang(k == n - 1))
    # a song-pipe column fallen across the water
    for x in range(14, 20):
        t.set(x, WK, 23, rod(facing='east'))
    t.set(13, 0, 23, 'minecraft:cobbled_deepslate')
    t.set(13, WL, 23, 'minecraft:polished_diorite')
    t.set(13, WK, 23, 'minecraft:oxidized_cut_copper')
    # ---------------------------------------------------------------- finish
    open_air(t, [(x, z) for x in range(LOT_W) for z in range(ROWS)] + bw, WK, WK + 5)
    hang_moss(t, rng, ((0, 2, 5), (30, H - 1, D - 1)), chance=0.1, max_len=3)
    vines_on(t, rng, ((0, 1, 5), (30, 10, D - 1)), chance=0.06, max_len=2)
    prune_floating(t)
    guard_trees(t)
    CAMERAS[PIECES + 'sunken_block'] = ((10, WK + 1, 6), (17, 3, 12))
    return t


# ==================================================================== the landmarks: the bell tower

def bell_tower(seed):
    """The bell tower that rang the hours until the Hymn came: a tall pale tower with a copper spire and
    a lightning rod (the storm strikes it), its bells still hanging in the belfry, a house beside it, a
    walled yard, and the city's graves behind, half drowned."""
    D, H = 27, 40
    t = new_lot('bell_tower', H, D, seed)
    rng = t.rng
    lot_street(t, rng, seed + 1, lamps=(4, 26), chairs=4)
    body = body_picker(seed + 5, tide_to=2)
    TX0, TX1, TZ0, TZ1, TTOP = 11, 19, 6, 14, 25
    TC = 15
    # ---------------------------------------------------------------- the tower
    for x in range(TX0, TX1 + 1):
        for z in range(TZ0, TZ1 + 1):
            t.set(x, 0, z, pick(rng, FOOTING))
            edge = x in (TX0, TX1) or z in (TZ0, TZ1)
            for y in range(1, TTOP + 1):
                if edge:
                    t.set(x, y, z, body(x, y, z))
                else:
                    t.set(x, y, z, WATER if y == 1 else AIR)
    for (x, z) in ((TX0, TZ0), (TX1, TZ0), (TX0, TZ1), (TX1, TZ1)):    # corner buttresses, stepping in
        for (dx, dz) in ((-1 if x == TX0 else 1, 0), (0, -1 if z == TZ0 else 1)):
            t.set(x + dx, 0, z + dz, 'minecraft:cobbled_deepslate')
            for y in range(1, 12):
                t.set(x + dx, y, z + dz, 'minecraft:polished_diorite' if y < 11 else
                      stair('minecraft:polished_diorite_stairs', opposite('west' if dx < 0 else 'east' if dx > 0 else
                                                                         'north' if dz < 0 else 'south')))
    for y in (9, 17):                                         # floors, with the ladder's hole
        for x in range(TX0 + 1, TX1):
            for z in range(TZ0 + 1, TZ1):
                t.set(x, y, z, 'minecraft:pale_oak_planks')
        for x in range(TX0, TX1 + 1):
            for z in (TZ0, TZ1):
                t.set(x, y, z, log(TIMBER, 'x'))
    for y in range(1, 20):
        t.set(TX0 + 1, y, TZ1 - 1, S('ladder', facing='north', waterlogged=(y == 1)))
    t.set(TC, 1, TZ0, 'minecraft:polished_diorite')               # the door
    for y in (2, 3, 4):
        t.set(TC, y, TZ0, AIR)
    t.set(TC - 1, 4, TZ0, stair('minecraft:polished_diorite_stairs', 'west', 'top'))
    t.set(TC + 1, 4, TZ0, stair('minecraft:polished_diorite_stairs', 'east', 'top'))
    t.set(TC, 5, TZ0, 'minecraft:chiseled_stone_bricks')
    for y in (6, 7, 13, 14):                                  # slits
        for (x, z) in ((TC, TZ0), (TC, TZ1), (TX0, 10), (TX1, 10)):
            if (x, z) != (TC, TZ0) or y > 6:
                t.set(x, y, z, S('iron_bars'))
    # the belfry: open arches on every face, the bell frame, the bells
    BY = 19
    for x in range(TX0 + 1, TX1):
        for z in range(TZ0 + 1, TZ1):
            t.set(x, BY, z, 'minecraft:pale_oak_planks')
    t.set(TX0 + 1, BY, TZ1 - 1, S('ladder', facing='north', waterlogged=False))
    TCZ = (TZ0 + TZ1) // 2
    for (axis, plane) in (('x', TZ0), ('x', TZ1), ('z', TX0), ('z', TX1)):
        mid = TC if axis == 'x' else TCZ
        for a in range(mid - 2, mid + 3):
            for y in range(BY + 1, BY + 5):
                x, z = (a, plane) if axis == 'x' else (plane, a)
                corner = abs(a - mid) == 2 and y == BY + 4
                t.set(x, y, z, stair('minecraft:polished_diorite_stairs',
                                     ('west' if a < mid else 'east') if axis == 'x' else ('north' if a < mid else 'south'),
                                     'top') if corner else AIR)
        x, z = (TC, plane) if axis == 'x' else (plane, TCZ)
        t.set(x, BY + 1, z, 'minecraft:diorite_wall')
    for x in range(TX0, TX1 + 1):
        t.set(x, BY + 5, 10, log('minecraft:dark_oak_log', 'x'))
    t.set(TC, BY + 4, 10, bell('north', 'ceiling'))
    for xb in (13, 17):                                       # cross beams wall to wall, two small bells, a lamp
        for z in range(TZ0, TZ1 + 1):
            if z != 10:
                t.set(xb, BY + 5, z, log('minecraft:dark_oak_log', 'z'))
    for z in (8, 12):
        t.set(13, BY + 4, z, chain('y'))
        t.set(13, BY + 3, z, bell('east', 'ceiling'))
    t.set(17, BY + 4, 12, chain('y'))
    t.set(17, BY + 3, 12, chain('y'))
    t.set(17, BY + 2, 12, lantern(soul=True, hanging=True))
    # the copper spire and its rod
    shapes.hip_roof(t, TX0 - 1, TZ0 - 1, TX1 + 1, TZ1 + 1, TTOP + 1, 'minecraft:oxidized_cut_copper_stairs',
                    body='minecraft:oxidized_cut_copper')
    for y in range(TTOP + 6, TTOP + 9):
        t.set(TC, y, 10, 'minecraft:oxidized_cut_copper')
    for y in range(TTOP + 9, TTOP + 12):
        t.set(TC, y, 10, rod())
    t.set(TC, TTOP + 12, 10, rod())
    for (x, z) in ((TX0 - 1, TZ0 - 1), (TX1 + 1, TZ0 - 1), (TX0 - 1, TZ1 + 1), (TX1 + 1, TZ1 + 1)):
        t.set(x, TTOP + 1, z, 'minecraft:diorite_wall')
        t.set(x, TTOP + 2, z, rod())
    for (x, y, z) in ((TC, TTOP + 8, 9), (TC + 1, TTOP + 6, 10)):
        t.set(x, y, z, cluster('north' if z == 9 else 'east'))
    # ---------------------------------------------------------------- the house beside it
    house(t, rng, 21, 6, 9, 9, storeys=2, ridge='z', front='north', doors=(4,), seed=seed + 12, face_to=LOOK,
          collapse='corner')
    # ---------------------------------------------------------------- the walled yard west: a well, a dead tree
    wy = [(x, z) for x in range(1, 10) for z in range(6, 15)]
    yard(t, rng, wy, seed + 3, lost_rate=0.1)
    garden_wall(t, rng, [(x, 6) for x in range(1, 10) if x not in (5, 6)] + [(1, z) for z in range(7, 15)], broken=0.2)
    well(t, 5, 10)
    for (x, z) in ((3, 8), (7, 8), (3, 13)):
        if t.is_air(x, WK, z):
            t.set(x, WK, z, stair('minecraft:pale_oak_stairs', opposite(LOOK)))
    # ---------------------------------------------------------------- the graves, half drowned
    gy = [(x, z) for x in range(1, 30) for z in range(16, 26)]
    soil = earth_patch(t, rng, gy)
    for (x, z) in gy:
        if z >= 22 and (x * 7 + z * 3) % 5 < 2 or (x > 22 and z > 19):
            t.set(x, WL, z, WATER)
            t.set(x, 0, z, 'minecraft:mud')
    flood(t, [(x, z) for (x, z) in gy if t.name_at(x, WL, z) == 'minecraft:water'])
    garden_wall(t, rng, [(x, 26) for x in range(0, 31)] + [(0, z) for z in range(16, 26)] + [(30, z) for z in range(16, 26)],
                broken=0.3)
    for x in range(3, 29, 3):
        for z in (17, 20, 23):
            if t.name_at(x, WL, z) == 'minecraft:water' or not t.is_solid(x, WL, z) or abs(x - TC) <= 1:
                continue
            r = rng.random()
            if r < 0.15:
                t.set(x, WK, z, stair('minecraft:polished_diorite_stairs', rng.choice(DIRS4)))
            elif r < 0.3:
                t.set(x, WK, z, slab('minecraft:polished_diorite_slab'))
            else:
                t.set(x, WK, z, 'minecraft:diorite_wall' if r < 0.7 else 'minecraft:polished_diorite')
                if r >= 0.7:
                    t.set(x, WK + 1, z, slab('minecraft:polished_diorite_slab'))
            if t.is_air(x, WK, z + 1) and rng.random() < 0.4:
                t.set(x, WK, z + 1, candle(rng.randint(1, 2), 'white', lit=False))
    for (x, z) in soil:
        if t.is_air(x, WK, z) and t.name_at(x, WL, z) != 'minecraft:water' and rng.random() < 0.25:
            t.set(x, WK, z, pick(rng, [('minecraft:fern', 2), ('minecraft:short_grass', 3), ('minecraft:dead_bush', 1)]))
    path = [(TC, z) for z in range(15, 26)] + [(TC + 1, z) for z in range(15, 26)]
    for (x, z) in path:
        t.set(x, 0, z, pick(rng, FOOTING))
        t.set(x, WL, z, pick(rng, [('minecraft:polished_diorite', 2), ('oldrepublic:pale_tiles', 1)]))
        t.set(x, WK, z, AIR)
    t.set(TC, 1, TZ1, 'minecraft:polished_diorite')
    t.set(TC, 2, TZ1, AIR)
    t.set(TC, 3, TZ1, AIR)
    # ---------------------------------------------------------------- finish
    open_air(t, [(x, z) for x in range(LOT_W) for z in range(ROWS)] + wy + gy + path, WK, WK + 6)
    hang_moss(t, rng, ((TX0, 10, TZ0), (TX1, TTOP, TZ1)), chance=0.12, max_len=3)
    vines_on(t, rng, ((0, 2, 0), (30, TTOP, D - 1)), chance=0.05, max_len=4)
    root_veins(t, rng, (TX1, 12, TZ1 - 2), 9)
    prune_floating(t)
    guard_trees(t)
    CAMERAS[PIECES + 'bell_tower'] = ((TC, WK + 1, 2), (TC, BY + 3, 10))
    return t


# ==================================================================== the houses: the courtyard house

def courtyard_house(seed):
    """A merchant's house round a courtyard: the front range with a gateway through it, two wings with
    galleries, the courtyard with its fountain (the Hymn's pipe stands in it) and the chairs set out in rows
    facing the gate, a garden wall behind."""
    D, H = 27, 20
    t = new_lot('courtyard_house', H, D, seed)
    rng = t.rng
    lot_street(t, rng, seed + 1, lamps=(2, 28), chairs=4)
    body = body_picker(seed + 5, tide_to=2)
    front = house(t, rng, 3, 5, 25, 6, storeys=2, ridge='x', front='north', doors=(4, 20), seed=seed + 11,
                  face_to=LOOK)
    west = house(t, rng, 3, 13, 6, 9, storeys=2, ridge='z', front='east', doors=(4,), seed=seed + 12, face_to=LOOK)
    east = house(t, rng, 22, 13, 6, 9, storeys=2, ridge='z', front='west', doors=(4,), seed=seed + 13, face_to=LOOK,
                 collapse='corner')
    # the gateway through the front range: a vaulted passage with walls to the flooded rooms either side
    for z in range(5, 11):
        for x in range(13, 18):
            if x in (13, 17):
                for y in range(1, 5):
                    t.set(x, y, z, body(x, y, z))
            else:
                t.set(x, 0, z, pick(rng, FOOTING))
                t.set(x, WL, z, pick(rng, [('minecraft:polished_diorite', 2), ('oldrepublic:pale_tiles', 1)]))
                for y in (2, 3, 4):
                    t.set(x, y, z, AIR)
        t.set(14, 4, z, stair('minecraft:polished_diorite_stairs', 'west', 'top'))
        t.set(16, 4, z, stair('minecraft:polished_diorite_stairs', 'east', 'top'))
    for z in (5, 10):
        t.set(15, 5, z, 'minecraft:chiseled_stone_bricks')
    t.set(15, 4, 5, AIR)
    # the courtyard
    court = [(x, z) for x in range(9, 22) for z in range(11, 25)]
    yard(t, rng, court, seed + 3, lost_rate=0.12)
    fc = (15, 18)
    for (x, z) in court:
        d = math.hypot(x - fc[0], z - fc[1])
        if d <= 2.6:
            t.set(x, WL, z, 'minecraft:polished_diorite')
            t.set(x, WK, z, WATER if d < 1.8 else 'minecraft:polished_diorite')
        elif d <= 3.2:
            t.set(x, WK, z, slab('minecraft:polished_diorite_slab'))
    t.set(fc[0], WK, fc[1], 'minecraft:calcite')
    t.set(fc[0], WK + 1, fc[1], 'minecraft:calcite')
    t.set(fc[0], WK + 2, fc[1], 'minecraft:oxidized_cut_copper')
    song_pipe(t, fc[0], fc[1], WK + 3, 4)
    for (x, z) in court:                                      # the chairs, in rows, facing the gate
        if z in (13, 22) or (z in (15, 21) and abs(x - 15) > 3):
            if (x, z) in court and abs(x - 15) > 0 and t.is_air(x, WK, z) and t.is_solid(x, WL, z) and rng.random() < 0.6:
                t.set(x, WK, z, stair('minecraft:pale_oak_stairs', opposite(LOOK)))
    # the galleries on the wings' courtyard sides
    for (gx, wx) in ((9, 8), (21, 22)):
        for z in range(13, 22):
            t.set(gx, 5, z, slab('minecraft:pale_oak_slab', 'top'))
            t.set(gx, 6, z, S('pale_oak_fence') if z not in (13,) else AIR)
            t.set(gx, 7, z, AIR)
            if z in (14, 17, 20):
                for y in range(WK, 5):
                    t.set(gx, y, z, S('pale_oak_fence'))
        for z in (15, 19):
            t.set(wx, 6, z, AIR)
            t.set(wx, 7, z, AIR)
    t.set(9, 6, 13, stair('minecraft:pale_oak_stairs', 'south'))
    # the garden wall and its gate
    garden_wall(t, rng, [(x, 25) for x in range(3, 28) if x not in (15,)] + [(3, 23), (3, 24), (27, 23), (27, 24)],
                h=2, broken=0.15, state='minecraft:diorite_wall')
    t.set(15, 0, 25, pick(rng, FOOTING))
    t.set(15, WL, 25, 'minecraft:polished_diorite')
    for (x, z) in ((10, 23), (20, 23)):                       # planters with dead pale trees
        t.set(x, WL, z, 'minecraft:rooted_dirt')
        for y in range(WK, WK + 4):
            t.set(x, y, z, log('minecraft:pale_oak_log'))
        t.set(x + (1 if x < 15 else -1), WK + 3, z, log('minecraft:pale_oak_log', 'x'))
        for k in range(2):
            t.set(x + (1 if x < 15 else -1), WK + 2 - k, z, moss_hang(k == 1))
    t.chest(7, 6, 19, 'west', loot=LOOT_ID)
    open_air(t, [(x, z) for x in range(LOT_W) for z in range(ROWS)] + court + [(15, 25)], WK, WK + 6)
    hang_moss(t, rng, ((0, 4, 5), (30, H - 1, D - 1)), chance=0.08, max_len=3)
    vines_on(t, rng, ((0, 2, 0), (30, 12, D - 1)), chance=0.05, max_len=3)
    prune_floating(t)
    guard_trees(t)
    CAMERAS[PIECES + 'courtyard_house'] = ((15, WK + 1, 6), (15, 4, 18))
    return t


# ==================================================================== the waterfront: the colonnade

def colonnade(seed):
    """The market colonnade on the waterfront: a portico of pale columns along the street (two fallen,
    their drums across the steps), the covered market behind it with its canvas stalls still set out,
    half its roof in, and the loading yard at the back."""
    D, H = 26, 18
    t = new_lot('colonnade', H, D, seed)
    rng = t.rng
    lot_street(t, rng, seed + 1, lamps=(), chairs=3)
    # the portico: a raised floor, columns on the front row, an architrave and a flat roof
    por = [(x, z) for x in range(1, 30) for z in range(5, 10)]
    for (x, z) in por:
        t.set(x, 0, z, pick(rng, FOOTING))
        t.set(x, WL, z, 'minecraft:polished_diorite')
        t.set(x, WK, z, stair('minecraft:polished_diorite_stairs', 'south') if z == 5 else
              pick(rng, [('oldrepublic:pale_tiles', 3), ('minecraft:calcite', 1)]))
    COLS = list(range(2, 30, 3))
    fallen = (COLS[2], COLS[6])
    for x in COLS:
        for z in (6, 9):
            if x in fallen and z == 6:
                t.set(x, WK + 1, z, 'minecraft:calcite')
                t.set(x, WK + 2, z, slab('minecraft:polished_diorite_slab'))
                continue
            t.set(x, WK + 1, z, 'minecraft:calcite')
            for y in range(WK + 2, WK + 7):
                t.set(x, y, z, S('quartz_pillar', axis='y') if z == 6 else 'minecraft:polished_diorite')
            t.set(x, WK + 7, z, 'minecraft:chiseled_stone_bricks')
    for x in range(1, 30):
        for z in range(5, 11):
            if z in (5, 10):
                t.set(x, WK + 8, z, stair('minecraft:polished_diorite_stairs', 'north' if z == 5 else 'south', 'top')
                      if z == 5 else 'minecraft:polished_diorite')
            t.set(x, WK + 9, z, slab('minecraft:polished_diorite_slab') if z in (5, 10) else 'minecraft:polished_diorite')
        t.set(x, WK + 8, 6, 'minecraft:polished_diorite')
        t.set(x, WK + 8, 9, 'minecraft:polished_diorite')
    for fx in fallen:                                          # the gaps over the fallen columns, the drums
        for (x, y, z) in sorted(p for p in t.cells if p[1] >= WK + 7 and abs(p[0] - fx) <= 1 + (p[1] - WK - 7)
                                and 5 <= p[2] <= 10):
            if rng.random() < 0.8:
                t.set(x, y, z, AIR)
        for z in (2, 3, 4):
            t.set(fx, WK, z, S('quartz_pillar', axis='z'))
        t.set(fx, WK, 1, 'minecraft:chiseled_stone_bricks')
    # the covered market: an open hall on timber posts, canvas stalls, half its roof gone
    mk = [(x, z) for x in range(1, 30) for z in range(11, 21)]
    yard(t, rng, mk, seed + 3, lost_rate=0.1)
    for x in range(1, 30, 4):
        for z in (11, 20):
            for y in range(WK, WK + 6):
                t.set(x, y, z, log(TIMBER))
    for x in range(1, 30):
        for z in (11, 20):
            t.set(x, WK + 6, z, log(TIMBER, 'x'))
    shapes.gable_roof(t, 0, 10, 30, 21, WK + 7, ROOF, ridge='x')
    jag = Noise(seed + 9, 3.0)
    for (x, y, z) in sorted(p for p in t.cells if p[1] >= WK + 7 and 10 <= p[2] <= 21):
        if x > 14 + 4 * jag.at(z, y) and rng.random() < 0.9:
            t.set(x, y, z, AIR)
    stalls = [(4, 14), (10, 14), (16, 14), (22, 14), (7, 18), (13, 18), (19, 18), (25, 18)]
    cloth = ['or:canvas', 'or:canvas_navy', 'or:canvas_crimson']
    for i, (sx_, sz_) in enumerate(stalls):
        c = cloth[i % 3]
        for dx in (-1, 0, 1):
            t.set(sx_ + dx, WK, sz_, pick(rng, [(barrel('up'), 2), ('oldrepublic:cargo_crate', 2),
                                                (S('pale_oak_planks'), 1)]))
            if rng.random() < 0.5:
                t.set(sx_ + dx, WK + 1, sz_, pick(rng, [(pot(rng.choice(DIRS4)), 2), (candle(1, 'white', lit=False), 1),
                                                        (S('flower_pot'), 1)]))
        for dx in (-1, 1):
            for y in (WK, WK + 1, WK + 2):
                t.set(sx_ + dx * 2, y, sz_, S('pale_oak_fence'))
        torn = rng.random() < 0.35
        for dx in range(-2, 3):
            for dz in (-1, 0, 1):
                if torn and rng.random() < 0.4:
                    continue
                t.set(sx_ + dx, WK + 3, sz_ + dz, slab('or:canvas_slab', 'top'))
        if not torn:
            t.set(sx_, WK + 3, sz_, c)
    # the loading yard behind
    by = [(x, z) for x in range(1, 30) for z in range(21, 26)]
    yard(t, rng, by, seed + 4, lost_rate=0.2)
    for (x, z, h) in ((3, 23, 2), (4, 23, 1), (3, 24, 1), (26, 22, 3), (27, 22, 2), (26, 23, 1), (12, 24, 1)):
        crate_stack(t, rng, x, z, WK, h)
    for x in range(1, 30):
        if rng.random() > 0.3:
            t.set(x, WK, 25, 'minecraft:mossy_stone_brick_wall')
    t.chest(13, WK, 18, 'north', loot=LOOT_ID)
    for x in (1, 29):
        song_pipe(t, x, 5, WK + 1, 7)
    open_air(t, [(x, z) for x in range(LOT_W) for z in range(ROWS)] + por + mk + by, WK, WK + 6)
    hang_moss(t, rng, ((0, 4, 5), (30, H - 1, D - 1)), chance=0.1, max_len=3)
    vines_on(t, rng, ((0, 2, 0), (30, 12, D - 1)), chance=0.06, max_len=3)
    prune_floating(t)
    guard_trees(t)
    CAMERAS[PIECES + 'colonnade'] = ((15, WK + 1, 7), (25, 5, 15))
    return t


# ==================================================================== registry

BUILDERS = {
    'townhouses': townhouses,
    'courtyard_house': courtyard_house,
    'song_hall': song_hall,
    'bell_tower': bell_tower,
    'quay': quay,
    'colonnade': colonnade,
    'sunken_block': sunken_block,
}


def build(name, salt):
    return BUILDERS[name](salt * 100 + sorted(BUILDERS).index(name) + 11)


if __name__ == '__main__':
    opts = args()
    for name in sorted(BUILDERS):
        tid = PIECES + name
        if wanted(opts, tid):
            t = build(name, 960350)
            save(t, tid, opts, views=('se', 'nw'), plans=[WL, WK, 6])
