"""tyrvann/silenced_city (worldgen, Tyrvann mire; docs/MILESTONE_6.md 5.6): a district of the drowned city.

"The Hymn rolls over a city of pale roofs. Street by street, the people stop speaking, and listen, and
obey. At the end, nothing sings but the stone." (a verse stone's vision). This is the district round
one of its squares, sunk into the mire: the plaza with the black singing stone in its ring of water
(start piece, two variants: the stone standing, or felled), and four lots by jigsaw, one per side, each
drawn from its own pool so a district never repeats a building: the landmarks (the song hall, the bell
tower), the houses (a row of townhouses, a merchant's courtyard house), the waterfront (the quay, the
market colonnade) and the drowned ruins. The people brought their chairs to the square to listen; the
chairs are still there, turned to the stone, and in every house the chairs face the window that looks
at it.

Every piece shares the city's vertical frame (city_kit): footings at y 0, paving at the water line
y 1, the street at y 2. Pieces are rigid and line up at the plaza's jigsaws (y 2). Biomes: the mire
only (the plan also names the basalt, whose ridges cannot carry a 100-block rigid district; see the
report).
"""
from city_kit import *  # noqa: F401,F403
import city_lots

NAME = 'tyrvann/silenced_city'
PIECES = 'tyrvann/silenced_city/'
SALT = 960350
PLAZA = (41, 24, 41)
C = 20
LOOT_ID = 'oldrepublic:chests/tyrvann/silenced_city'
STREET, LOT = 'oldrepublic:city_street', 'oldrepublic:city_lot'
POOLS = {
    'a': {'north': 'landmarks', 'east': 'houses', 'south': 'waterfront', 'west': 'ruins'},
    'b': {'north': 'houses', 'east': 'landmarks', 'south': 'ruins', 'west': 'waterfront'},
}
LOTS = {
    'landmarks': ['song_hall', 'bell_tower'],
    'houses': ['townhouses', 'courtyard_house'],
    'waterfront': ['quay', 'colonnade'],
    'ruins': ['sunken_block'],
}
EMPTY_WEIGHT = {'ruins': 1}          # the ruins side is sometimes only the mire (an empty element)
CAMERAS = city_lots.CAMERAS
MAX_DISTANCE = 64


def squircle_d(x, z, cx, cz, r, p=3.0):
    return ((abs(x - cx) / r) ** p + (abs(z - cz) / r) ** p) ** (1.0 / p)


def street_jigsaw(t, x, z, facing, pool):
    t.jigsaw(x, WK, z, '%s_up' % facing, name=STREET, target=LOT, pool='oldrepublic:' + PIECES + pool)
    t.set(x, WK + 1, z, AIR)


def lot_jigsaw(t, x=15, z=0):
    t.jigsaw(x, WK, z, 'north_up', name=LOT, target=STREET, pool='minecraft:empty')
    t.set(x, WK + 1, z, AIR)


# ==================================================================== the plaza (start piece)

def plaza(variant, seed):
    t = Template(*PLAZA, seed=seed, name=PIECES + 'plaza_' + variant)
    rng = t.rng
    sx, sy, sz = PLAZA
    n_sink = Noise(seed + 1, 6.0)
    standing = variant == 'a'
    sunk_c = (4, 36) if standing else (36, 4)
    # the sunken corner: the paving has gone down into the mire there (the world's own water)
    sunk = {(x, z) for x in range(sx) for z in range(sz)
            if math.hypot(x - sunk_c[0], z - sunk_c[1]) < 6.5 + 2.5 * n_sink.at(x * 0.6, z * 0.6)}
    cells = {(x, z) for x in range(sx) for z in range(sz)} - sunk
    edge_of_sunk = {(x, z) for (x, z) in cells if any((x + dx, z + dz) in sunk for dx in (-1, 0, 1) for dz in (-1, 0, 1))}

    def lost(x, z, h):
        d = math.hypot(x - sunk_c[0], z - sunk_c[1])
        return h.random() < 0.06 + max(0.0, 0.5 - d / 40.0)
    gone = paving(t, rng, sorted(cells), seed + 2, lost=lost)
    # broken flags tip into the water at the sunken corner's lip
    for (x, z) in sorted(edge_of_sunk):
        if rng.random() < 0.45:
            t.set(x, WL, z, stair('minecraft:polished_diorite_stairs', rng.choice(DIRS4)) if rng.random() < 0.5
                  else slab('minecraft:polished_diorite_slab'))
    # ------------------------------------------------------------ the singing stone in its ring of water
    rim = {(x, z) for (x, z) in cells if 8.5 <= squircle_d(x, z, C, C, 1.0) <= 9.4}
    pool_cells = {(x, z) for (x, z) in cells if 6.5 < squircle_d(x, z, C, C, 1.0) < 8.5}
    plat = {(x, z) for (x, z) in cells if squircle_d(x, z, C, C, 1.0) <= 6.5}
    dais = {(x, z) for (x, z) in cells if squircle_d(x, z, C, C, 1.0, 2.0) <= 3.6}
    causeway = {(x, z) for (x, z) in pool_cells | rim if abs(x - C) <= 1 or abs(z - C) <= 1}
    for (x, z) in rim - causeway:
        t.set(x, WK, z, 'minecraft:polished_diorite' if (x + z) % 5 else 'minecraft:calcite')
    for (x, z) in pool_cells - causeway:
        t.set(x, WK, z, WATER)
    for (x, z) in causeway:
        t.set(x, WK, z, pick(rng, [('minecraft:polished_diorite', 2), ('oldrepublic:pale_tiles', 2)]))
    for (x, z) in plat:
        t.set(x, WK, z, 'minecraft:calcite')
        t.set(x, WK + 1, z, pick(rng, [('oldrepublic:pale_tiles', 4), ('minecraft:polished_diorite', 2),
                                       ('minecraft:calcite', 1)]))
    # steps: from the street up onto each causeway (at the rim), and from the causeway onto the platform
    for (dx, dz, face) in ((0, -1, 'south'), (0, 1, 'north'), (-1, 0, 'east'), (1, 0, 'west')):
        for k in (-1, 0, 1):
            # the outermost rim cell of this causeway
            r_out = max(r for r in range(6, 11) if (C + dx * r + (k if dx == 0 else 0), C + dz * r + (k if dz == 0 else 0)) in rim | pool_cells)
            px, pz = C + dx * r_out + (k if dx == 0 else 0), C + dz * r_out + (k if dz == 0 else 0)
            t.set(px, WK, pz, stair('minecraft:polished_diorite_stairs', face))
            r_in = min(r for r in range(3, 11) if (C + dx * r + (k if dx == 0 else 0), C + dz * r + (k if dz == 0 else 0)) in pool_cells)
            qx, qz = C + dx * (r_in - 1) + (k if dx == 0 else 0), C + dz * (r_in - 1) + (k if dz == 0 else 0)
            t.set(qx, WK + 1, qz, stair('minecraft:polished_diorite_stairs', face))
    for (x, z) in dais:
        t.set(x, WK + 2, z, 'minecraft:polished_deepslate' if squircle_d(x, z, C, C, 1.0, 2.0) > 2.6 else 'minecraft:polished_basalt[axis=y]')
    for (dx, dz, face) in ((0, -1, 'south'), (0, 1, 'north'), (-1, 0, 'east'), (1, 0, 'west')):
        t.set(C + dx * 4, WK + 2, C + dz * 4, stair('minecraft:polished_deepslate_stairs', face))
    sy0 = WK + 3                                             # the stone's foot
    if standing:
        for y in range(sy0, sy0 + 12):
            for x in range(C - 1, C + 2):
                for z in range(C - 1, C + 2):
                    band = y in (sy0 + 3, sy0 + 8)
                    st = 'minecraft:oxidized_cut_copper' if band and (x, z) != (C, C) else \
                        ('minecraft:polished_basalt[axis=y]' if (y - sy0) % 4 else 'minecraft:chiseled_deepslate')
                    t.set(x, y, z, st)
        for (x, z, f) in ((C, C - 1, 'south'), (C, C + 1, 'north'), (C - 1, C, 'east'), (C + 1, C, 'west')):
            t.set(x, sy0 + 12, z, stair('minecraft:polished_deepslate_stairs', f))
        for (x, z) in ((C - 1, C - 1), (C + 1, C - 1), (C - 1, C + 1), (C + 1, C + 1)):
            t.set(x, sy0 + 12, z, slab('minecraft:polished_deepslate_slab'))
        for y in range(sy0 + 12, sy0 + 15):
            t.set(C, y, C, 'minecraft:polished_basalt[axis=y]')
        t.set(C, sy0 + 15, C, 'minecraft:cracked_deepslate_tiles')
        t.set(C, sy0 + 16, C, cluster('up'))
        t.set(C, sy0 + 13, C - 1, cluster('north'))
        # the organ crown: copper pipes on the dais round the stone, the storm plays them
        for (x, z, h) in ((C - 2, C - 2, 8), (C + 2, C - 2, 10), (C - 2, C + 2, 9), (C + 2, C + 2, 7),
                          (C, C - 3, 4), (C + 3, C, 5), (C, C + 3, 3), (C - 3, C, 6)):
            song_pipe(t, x, z, sy0, h)
        # copper arms with soul lanterns on chains: the only light the square has left
        for (x, z, face, y) in ((C, C - 2, 'north', sy0 + 7), (C, C + 2, 'south', sy0 + 6), (C + 2, C, 'east', sy0 + 9),
                                (C - 2, C, 'west', sy0 + 5)):
            t.set(x, y, z, S('oxidized_copper_bars', north=False, south=False, east=False, west=False, waterlogged=False))
            t.set(x, y - 1, z, chain('y', copper=True))
            t.set(x, y - 2, z, lantern(soul=True, hanging=True))
        CAMERAS[PIECES + 'plaza_a'] = ((C, WK + 1, 4), (C, sy0 + 8, C))
    else:
        # felled: the shaft snapped at its foot and lies across the pool and the square, in three lengths
        for x in range(C - 1, C + 2):
            for z in range(C - 1, C + 2):
                t.set(x, sy0, z, 'minecraft:polished_basalt[axis=y]')
                if (x + z) % 2:
                    t.set(x, sy0 + 1, z, 'minecraft:cracked_deepslate_tiles')
        t.set(C, sy0 + 1, C, cluster('up'))
        lengths = [(3, 9, sy0 - 1), (10, 14, WK), (16, 19, WK - 0)]
        for (a, b, y) in lengths:
            for k in range(a, b + 1):
                for dz in (-1, 0, 1):
                    x, z = C + k, C + dz
                    if not t.inside(x, y, z):
                        continue
                    st = 'minecraft:oxidized_cut_copper' if k in (6, 12) and dz != 0 else S('polished_basalt', axis='x')
                    if (x, z) in pool_cells and y == WK:
                        t.set(x, WK, z, st)
                    else:
                        t.set(x, y, z, st)
                        if not t.is_solid(x, y - 1, z):
                            t.set(x, y - 1, z, 'minecraft:polished_diorite')
        # its pipes lie scattered where they fell, the crown's stormglass in the gutter
        for (x, z) in ((C + 5, C - 3), (C + 7, C + 3), (C + 12, C - 3), (C + 13, C + 3), (C + 17, C - 2)):
            if t.is_air(x, WK, z) and t.is_solid(x, WL, z):
                t.set(x, WK, z, rod(facing='east'))
        t.set(C + 20, WK, C, cluster('up'))
        for (x, z, h) in ((C - 2, C - 2, 5), (C - 2, C + 2, 3), (C, C + 3, 2)):
            song_pipe(t, x, z, sy0, h)
        CAMERAS[PIECES + 'plaza_b'] = ((C - 3, WK + 1, 3), (C + 10, WK + 2, C))
    # ------------------------------------------------------------ the listeners' chairs, turned to the stone
    for (x, z) in sorted(cells):
        d = squircle_d(x, z, C, C, 1.0, 2.0)
        ring = None
        for r0 in (10.5, 12.0, 13.5, 15.0):
            if abs(d - r0) < 0.5:
                ring = r0
        if ring is None or abs(x - C) <= 1 or abs(z - C) <= 1:
            continue
        if (x, z) in gone or not t.is_solid(x, WL, z) or (x, WK, z) in t.cells:
            continue
        if rng.random() > (0.75 - (ring - 10.5) * 0.12):
            continue
        dx, dz = C - x, C - z
        face = ('east' if dx > 0 else 'west') if abs(dx) > abs(dz) else ('south' if dz > 0 else 'north')
        if rng.random() < 0.12:
            t.set(x, WK, z, slab('minecraft:pale_oak_slab'))
        else:
            t.set(x, WK, z, stair('minecraft:pale_oak_stairs', opposite(face)))
    # ------------------------------------------------------------ lamps, roots, the corners
    for (x, z) in ((8, 8), (32, 8), (8, 32), (32, 32), (20, 6), (34, 20), (20, 34), (6, 20)):
        if (x, z) in cells and (x, z) not in sunk and not (x, WK, z) in t.cells:
            lamp_post(t, x, z, lit=rng.random() < 0.5, broken=rng.random() < 0.3)
    flood(t, [p for p in sorted(gone) if p not in edge_of_sunk])
    # lily pads and roots where the mire comes in
    for (x, z) in sorted(cells):
        if t.name_at(x, WL, z) == 'minecraft:water' and rng.random() < 0.3 and (x, WK, z) not in t.cells:
            t.set(x, WK, z, 'minecraft:lily_pad')
    for (x, z) in sorted(edge_of_sunk):
        if rng.random() < 0.25 and (x, WK, z) not in t.cells and t.is_solid(x, WL, z):
            t.set(x, WK, z, roots())
    corner_house = (32, 33) if standing else (2, 2)
    info = house(t, rng, corner_house[0], corner_house[1], 7, 6, storeys=2, ridge='x', front='west' if standing else 'east',
                 doors=(3,), flooded=True, seed=seed + 7, collapse='roof', furnish=False)
    # a dead pale tree out of a broken planter
    tx, tz = (6, 6) if standing else (34, 34)
    for (x, z) in ((tx - 1, tz - 1), (tx, tz - 1), (tx + 1, tz - 1), (tx - 1, tz), (tx + 1, tz), (tx - 1, tz + 1), (tx, tz + 1), (tx + 1, tz + 1)):
        t.set(x, WK, z, 'minecraft:polished_diorite_slab[type=bottom,waterlogged=false]' if (x + z) % 2 else 'minecraft:polished_diorite')
    t.set(tx, WL, tz, 'minecraft:rooted_dirt')
    t.set(tx, 0, tz, 'minecraft:rooted_dirt')
    for y in range(WK, WK + 7):
        t.set(tx, y, tz, log('minecraft:pale_oak_log'))
    for (x, y, z, ax) in ((tx + 1, WK + 5, tz, 'x'), (tx + 2, WK + 6, tz, 'x'), (tx, WK + 6, tz - 1, 'z'),
                          (tx - 1, WK + 4, tz, 'x'), (tx, WK + 7, tz, 'y')):
        t.set(x, y, z, log('minecraft:pale_oak_log', ax))
    for (x, y, z, n) in ((tx + 2, WK + 5, tz, 3), (tx - 1, WK + 3, tz, 2), (tx, WK + 5, tz - 1, 2)):
        for k in range(n):
            if not (x, y - k, z) in t.cells:
                t.set(x, y - k, z, moss_hang(k == n - 1))
    # ------------------------------------------------------------ the jigsaws to the four lots
    for (x, z, face) in ((C, 0, 'north'), (sx - 1, C, 'east'), (C, sz - 1, 'south'), (0, C, 'west')):
        street_jigsaw(t, x, z, face, POOLS[variant][face])
    open_air(t, cells, WK, WK + 6)
    prune_floating(t)
    guard_trees(t)
    return t


# ==================================================================== main

def build_all():
    out = {}
    out[PIECES + 'plaza_a'] = plaza('a', SALT * 10 + 1)
    out[PIECES + 'plaza_b'] = plaza('b', SALT * 10 + 2)
    for name in sorted(city_lots.BUILDERS):
        out[PIECES + name] = city_lots.build(name, SALT)
    return out


def element(tid, t):
    return W.element(tid, adapted=dict(support_depth=10, clear_from=t.size[1], clear_inset=0, yard_stand=WK,
                                       ramp_rings=0))


def write_worldgen(ts):
    W.pool(PIECES + 'start', [element(PIECES + 'plaza_a', ts[PIECES + 'plaza_a']),
                              element(PIECES + 'plaza_b', ts[PIECES + 'plaza_b'])])
    for pool, names in sorted(LOTS.items()):
        els = [element(PIECES + n, ts[PIECES + n]) for n in names]
        if EMPTY_WEIGHT.get(pool):
            els.append(W.element('minecraft:empty', weight=EMPTY_WEIGHT[pool]))
        W.pool(PIECES + pool, els)
    W.structure(NAME, PIECES + 'start', 'has_structure/' + NAME, size=1, max_distance=MAX_DISTANCE, start_height=-1)
    W.structure_set(NAME, [(NAME, 1)], 64, 24, SALT, placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:tyrvann_mire'])
    loot_table('chests/tyrvann/silenced_city', [
        ((2, 4), [('oldrepublic:credit_chit', 5, 1, 3), ('gold_nugget', 5, 2, 6), ('white_candle', 4, 1, 3),
                  ('paper', 4, 2, 5), ('book', 3, 1, 1), ('oldrepublic:lumen_thread', 2, 1, 2),
                  ('oldrepublic:stormglass_shard', 3, 1, 2), ('string', 3, 1, 3)]),
        (1, [('oldrepublic:credit_chit', 4, 2, 5), ('clock', 1, 1, 1), ('name_tag', 1, 1, 1),
             ('disc_fragment_5', 1, 1, 1), ('bell', 1, 1, 1), ('gold_ingot', 2, 1, 2)]),
    ])


def assemble():
    """The district as vanilla would build it from the pools (every start rotation), checked."""
    return jigsaw.assemble(PIECES + 'start', max_distance=MAX_DISTANCE, size=1, start_height=-1)


def composite(variant, picks, ts):
    """A preview of a whole district: the plaza `variant` with the lot `picks` {side: lot name or None},
    placed where the jigsaws put them (the start unrotated)."""
    plaza_t = ts[PIECES + 'plaza_' + variant]
    placed = [(plaza_t, (0, 0, 0), 'none')]
    for (jpos, jfront) in ((p, B.parse(s)[1].get('orientation', 'north_up').split('_')[0])
                           for p, s in sorted(plaza_t.cells.items()) if B.name_of(s) == 'minecraft:jigsaw'):
        side = jfront
        name = picks.get(side)
        if not name:
            continue
        lot = ts[PIECES + name]
        for rot in jigsaw.ROTATIONS:
            if jigsaw.rotate_dir('north', rot) == jigsaw._OPPOSITE[jfront]:
                break
        st = jigsaw._STEP[jfront]
        target = (jpos[0] + st[0], jpos[1] + st[1], jpos[2] + st[2])
        r = jigsaw.rotate_pos((15, WK, 0), rot)
        origin = (target[0] - r[0], target[1] - r[1], target[2] - r[2])
        placed.append((lot, origin, rot))
    boxes = [jigsaw.box_of(o, t.size, rot) for (t, o, rot) in placed]
    mn = [min(b[0][i] for b in boxes) for i in range(3)]
    mx = [max(b[1][i] for b in boxes) for i in range(3)]
    big = Template(mx[0] - mn[0] + 1, mx[1] - mn[1] + 1, mx[2] - mn[2] + 1, name='%s district %s' % (NAME, variant))
    for (t, o, rot), b in zip(placed, boxes):
        big.paste(t, b[0][0] - mn[0], b[0][1] - mn[1], b[0][2] - mn[2], rotation=jigsaw._QUARTERS[rot])
    return big


def main(opts):
    ts = build_all()
    for tid, (eye, look) in sorted(CAMERAS.items()):
        if tid in ts:
            assert ts[tid].name_at(*eye) == 'minecraft:air', ('camera eye', tid, eye, ts[tid].name_at(*eye))
    for tid, t in ts.items():
        if wanted(opts, tid):
            save(t, tid, opts, views=('se', 'nw'), plans=[WL, WK, WK + 1])
    write_worldgen(ts)
    res = assemble()
    print(res.report())
    if res.problems:
        raise SystemExit('jigsaw problems in %s' % NAME)
    if opts.preview and wanted(opts, NAME + '/district'):
        for variant, picks in (('a', {'north': 'song_hall', 'east': 'townhouses', 'south': 'quay', 'west': 'sunken_block'}),
                               ('b', {'north': 'courtyard_house', 'east': 'bell_tower', 'south': None, 'west': 'colonnade'})):
            big = composite(variant, picks, ts)
            out = os.path.join(opts.preview, NAME.replace('/', '__') + '__district_' + variant + '.png')
            render(big, out, '%s district %s %s' % (NAME, variant, 'x'.join(map(str, big.size))), views=('se', 'nw'),
                   plans=[WK], width=2000)
            print('preview', out)
    return ts


if __name__ == '__main__':
    main(args())
