"""planet/hymn_shrine (worldgen, Tyrvann stormwood and mire; docs/MILESTONE_6.md 5.6): the singing cairn.

An old Sith mound of two stepped terraces stands out of the mire (or the stormwood floor). On its crown
the Hymn's builders stood a bundle of black basalt columns like the pipes of an organ, bound with
verdigris copper and topped with copper pipes: when the storm blows through it, it sings. Lightning has
taken its crown, and stormglass grows in the break. Four holed singing stones keep the lower terrace
(soul lanterns burn in the throats of two of them; the south-east one was struck and lies broken across
the terrace edge), kneeling stones stand in rows before the stair, and the cantor's lectern faces the
cairn. The west wall of the mound has slumped under a dead stormwood whose roots split it.

The Hymnsworn camp round the mound's foot: the cantor's hide tent (the offering chest), a torn second
tent, a lean-to against the terrace with bedrolls, a cold soul fire with log seats, a bell gallows, and a
pile of salvaged copper pipe they mean to set into the cairn. A flagged path leads north.

Mire rule: every column that carries anything has a solid bottom layer, so the pool element's support
columns reach the bed of the water; the apron is flush with the water line, the terraces rise above it.

Keep: HEART (feet, before the cairn), CHEST_1 (the offering chest, chests/hymn_shrine), SQUAD (site
spawner, hymnsworn, by the fire), SURFACE_GUARD (feet, the camp yard; the 4-block ring round it is open).
"""
from common import *  # noqa: F401,F403

TEMPLATE = 'planet/hymn_shrine'
SIZE = (33, 26, 33)
BURIED = 2
GY, FY = BURIED - 1, BURIED      # the apron's surface (the water line in the mire) and its walk level
MX, MZ = 14, 13                   # the mound's centre
T1Y = FY + 2                      # terrace one's surface layer (walk FY + 3): a wall of three courses
T2Y = FY + 3                      # terrace two's surface layer (walk FY + 4)
LOOT_ID = 'oldrepublic:chests/hymn_shrine'
SEED = 905207

HEART = (MX, T2Y + 1, MZ - 4)
CHEST = (27, FY, 7)
SURFACE_GUARD = (20, FY, 26)
PADS = [(23, 5, 31, 12), (2, 24, 8, 30), (25, 15, 31, 21)]   # the cantor's tent, the torn tent, the gallows
SQUAD = (24, FY, 29)
CAMERA = ((MX, T1Y + 2, MZ - 6), (MX, T2Y + 8, MZ))   # from the top of the stair, up the cairn

# the cairn: column heights over terrace two, by (dx, dz) from the centre
CAIRN = {
    (-1, -2): 9, (0, -2): 12, (1, -2): 7,
    (-2, -1): 6, (-1, -1): 14, (0, -1): 16, (1, -1): 13, (2, -1): 8,
    (-2, 0): 10, (-1, 0): 15, (0, 0): 19, (1, 0): 17, (2, 0): 5,
    (-2, 1): 7, (-1, 1): 12, (0, 1): 14, (1, 1): 15, (2, 1): 9,
    (-1, 2): 8, (0, 2): 11, (1, 2): 6,
}
PIPES = {(0, -2): 3, (1, 1): 2, (-2, 0): 4, (2, 1): 2, (-1, -2): 3, (-2, 1): 2}
STRUCK = (1, 0)


def footprint(noise):
    apron = superellipse(SIZE[0], SIZE[2], 16, 16, 15.4, 15.4, power=3.5, noise=noise, amp=0.08, freq=0.35)
    apron = {(x, z) for (x, z) in apron if 1 <= x <= SIZE[0] - 2 and 1 <= z <= SIZE[2] - 2}
    path = {(x, z) for x in (MX - 1, MX, MX + 1) for z in range(0, 4)}
    t1 = superellipse(SIZE[0], SIZE[2], MX, MZ, 8.8, 8.4, power=3.0, noise=noise, amp=0.05, freq=0.6)
    t2 = superellipse(SIZE[0], SIZE[2], MX, MZ, 4.7, 4.7, power=2.0)
    return apron | path, path, t1, t2


def build(seed=SEED):
    t = Template(*SIZE, seed=seed, name=TEMPLATE)
    rng = t.rng
    sx, sy, sz = SIZE
    n_rim = Noise(seed + 1, 3.0)
    n_moss = Noise(seed + 2, 4.0)
    n_wet = Noise(seed + 3, 5.0)
    rough = HYMN.picker('rough', rng)
    wall = HYMN.picker('wall', rng)
    basalt = HYMN.picker('basalt', rng)
    worn = patch(Noise(seed + 4, 3.0), [('minecraft:packed_mud', 3), ('or:peat', 3), ('minecraft:cobbled_deepslate', 1),
                                        ('minecraft:gravel', 1), ('minecraft:packed_mud', 1), ('minecraft:tuff', 1)],
                 jitter=Noise(seed + 5, 1.0), mix=0.45)
    bank = patch(Noise(seed + 6, 3.0), [('minecraft:mud', 5), ('or:peat', 2), ('minecraft:moss_block', 2),
                                        ('minecraft:muddy_mangrove_roots', 1)], jitter=Noise(seed + 7, 1.0))

    apron, path, t1, t2 = footprint(n_rim)
    for (x0, z0, x1, z1) in PADS:
        apron |= {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}
    rim = ring_of(apron) | {(x, z) for (x, z) in apron
                            if ((abs(x - 16) / 15.4) ** 3.5 + (abs(z - 16) / 15.4) ** 3.5) ** (1 / 3.5) > 0.9}
    rim -= path
    t1_ring = ring_of(t1)
    t2_ring = ring_of(t2)
    breach = {(x, z) for (x, z) in t1 if x <= MX - 6 and MZ - 3 <= z <= MZ + 3}

    # ------------------------------------------------------------ the apron and its footing
    for (x, z) in sorted(apron):
        t.set(x, 0, z, pick(rng, [('minecraft:mud', 3), ('minecraft:clay', 1), ('or:peat', 1)])
              if (x, z) not in t1 else rough(x, 0, z))
    camp = {(x, z) for (x, z) in apron if (x >= MX + 9 or z >= MZ + 9)}
    skirt = {(x, z) for (x, z) in apron - t1
             if any((x + dx, z + dz) in t1 for dx in (-2, -1, 0, 1, 2) for dz in (-2, -1, 0, 1, 2))}
    flag_mats = [('minecraft:deepslate_tiles', 3), ('or:stormstone', 3), ('minecraft:polished_deepslate', 2),
                 ('or:stormstone_bricks', 1)]
    lost_ground = flag_floor(t, sorted(skirt | path), GY, seed + 10, flag_mats,
                             lost=lambda x, z, h: h.random() < (0.45 if (x, z) in path else 0.3),
                             lost_state=lambda x, z: worn)
    for (x, z) in sorted(apron - t1 - skirt - path):
        t.set(x, GY, z, bank(x, GY, z) if (x, z) in rim else worn(x, GY, z))
    # ------------------------------------------------------------ terrace one: a dressed wall, a flagged top
    for (x, z) in sorted(t1):
        t.set(x, GY, z, rough(x, GY, z))
        ring = (x, z) in t1_ring
        t.set(x, FY, z, rough(x, FY, z) if ring else 'or:stormstone')
        t.set(x, FY + 1, z, wall(x, FY + 1, z) if ring else 'or:stormstone')
    flag_floor(t, sorted(t1 - t1_ring), T1Y, seed + 11,
               [('minecraft:deepslate_tiles', 4), ('or:stormstone', 2), ('minecraft:polished_deepslate', 2),
                ('minecraft:smooth_basalt', 1)],
               lost=lambda x, z, h: h.random() < 0.08,
               lost_state=lambda x, z: 'minecraft:moss_block' if n_moss.at(x, z) > 0.55 else 'minecraft:cobbled_deepslate')
    for (x, z) in sorted(t1_ring):     # the coping: a pale dressed course that draws the terrace's edge
        t.set(x, T1Y, z, 'minecraft:polished_deepslate' if (x * 5 + z * 3) % 7 else 'minecraft:deepslate_tiles')
    # buttresses: piers stand proud of the wall every few cells, capped with a sloped stone
    for (x, z) in sorted(t1_ring):
        if (x * 7 + z * 3) % 6 or (x, z) in breach:
            continue
        for dx, dz, face in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
            ox, oz = x + dx, z + dz
            if (ox, oz) not in t1 and (ox, oz) in apron and abs(ox - MX) + abs(oz - MZ) > 9 \
                    and not (abs(ox - MX) <= 2 and abs(oz - MZ) > 6):
                t.set(ox, 0, oz, rough(ox, 0, oz))
                t.set(ox, GY, oz, rough(ox, GY, oz))
                t.set(ox, FY, oz, rough(ox, FY, oz))
                t.set(ox, FY + 1, oz, 'or:stormstone_bricks')
                t.set(ox, T1Y, oz, stair('or:stormstone_brick_stairs', face))
                break

    # the stairs up terrace one, north and south: two steps between cheek walls with lantern posts
    zn = min(z for (x, z) in t1 if x == MX)
    zs = max(z for (x, z) in t1 if x == MX)
    for (sgn, face, z_edge) in ((-1, 'south', zn), (1, 'north', zs)):
        steps = [z_edge + sgn * k for k in (3, 2, 1)]          # outermost first: FY, FY + 1, FY + 2
        for x in range(MX - 1, MX + 2):
            for i, z in enumerate(steps):
                t.set(x, 0, z, rough(x, 0, z))
                t.set(x, GY, z, rough(x, GY, z))
                for y in range(FY, FY + i):
                    t.set(x, y, z, 'or:stormstone_bricks')
                t.set(x, FY + i, z, stair('or:stormstone_brick_stairs', face))
            t.set(x, T1Y, z_edge, 'minecraft:polished_deepslate')
        for x in (MX - 2, MX + 2):                             # cheek walls stepping up with the stair
            for i, z in enumerate(steps):
                t.set(x, 0, z, rough(x, 0, z))
                t.set(x, GY, z, rough(x, GY, z))
                for y in range(FY, FY + i + 1):
                    t.set(x, y, z, rough(x, y, z) if y == FY else 'or:stormstone_bricks')
                t.set(x, FY + i + 1, z, 'minecraft:polished_deepslate' if i == 2 else stair('minecraft:polished_deepslate_stairs', face))
            t.set(x, T1Y + 1, steps[2], 'minecraft:polished_deepslate_wall')
            t.set(x, T1Y + 2, steps[2], lantern(soul=True))

    # the west wall has slumped where the dead tree's roots split it: rubble spills out, the top sags
    for (x, z) in sorted(breach):
        edge = (x, z) in t1_ring
        depth = MX - 5 - x                                       # 1 at the inner edge, 3 at the wall
        if edge:
            t.set(x, T1Y, z, AIR)
            if rng.random() < 0.6:
                t.set(x, FY + 1, z, AIR)
            elif rng.random() < 0.5:
                t.set(x, FY + 1, z, slab('minecraft:cobbled_deepslate_slab'))
        elif depth >= 2 or rng.random() < 0.4:
            t.set(x, T1Y, z, AIR)
            t.set(x, FY + 1, z, pick(rng, [('minecraft:gravel', 2), ('minecraft:cobbled_deepslate', 2), ('minecraft:tuff', 1)])
                  if depth < 3 else slab('minecraft:cobbled_deepslate_slab'))
    for _ in range(14):
        x = MX - 9 - rng.randint(0, 3)
        z = MZ + rng.randint(-4, 4)
        if (x, z) in apron and (x, z) not in t1 and t.is_solid(x, GY, z):
            t.set(x, FY, z, pick(rng, HYMN.options('rubble') + [('minecraft:cobbled_deepslate_slab[type=bottom]', 2)]))

    # ------------------------------------------------------------ terrace two and the cairn
    for (x, z) in sorted(t2):
        if (x, z) in t2_ring:
            card = (x == MX or z == MZ)
            t.set(x, T2Y, z, 'minecraft:chiseled_deepslate' if card and (x, z) != (MX, MZ - 5) else 'minecraft:polished_deepslate')
        else:
            t.set(x, T2Y, z, pick(rng, [('minecraft:deepslate_tiles', 3), ('minecraft:polished_basalt[axis=y]', 1),
                                        ('minecraft:cracked_deepslate_tiles', 1)]))
    zn2 = min(z for (x, z) in t2 if x == MX)
    zs2 = max(z for (x, z) in t2 if x == MX)
    for x in range(MX - 1, MX + 2):
        t.set(x, T2Y, zn2 - 1, stair('minecraft:polished_deepslate_stairs', 'south'))
        t.set(x, T2Y, zs2 + 1, stair('minecraft:polished_deepslate_stairs', 'north'))

    cy = T2Y + 1
    bands = (cy + 4, cy + 9)
    for (dx, dz), h in sorted(CAIRN.items()):
        x, z = MX + dx, MZ + dz
        for k in range(h):
            y = cy + k
            if y in bands:
                st = 'minecraft:oxidized_cut_copper' if (dx + dz + k) % 3 else 'minecraft:oxidized_copper'
            elif k == h - 1 and (dx, dz) != (0, 0):
                st = 'minecraft:polished_basalt[axis=y]'
            else:
                st = ('minecraft:polished_basalt[axis=y]', 'minecraft:basalt[axis=y]', 'minecraft:polished_basalt[axis=y]',
                      'minecraft:smooth_basalt')[(dx * 3 + dz * 5 + k // 3) % 4]
            t.set(x, y, z, st)
        top = cy + h
        if (dx, dz) in PIPES:
            for k in range(PIPES[(dx, dz)]):
                t.set(x, top + k, z, rod())
    # the strike: the crown is broken, stormglass grows in the break and down the struck column
    t.set(MX, cy + CAIRN[(0, 0)] - 1, MZ, 'minecraft:cracked_deepslate_tiles')
    t.set(MX, cy + CAIRN[(0, 0)], MZ, cluster('up'))
    sx_, sz_ = MX + STRUCK[0], MZ + STRUCK[1]
    t.set(sx_, cy + CAIRN[STRUCK], sz_, cluster('up'))
    t.set(sx_ + 1, cy + CAIRN[STRUCK] - 2, sz_, cluster('east'))
    t.set(MX, cy + 15, MZ - 2, cluster('north'))
    t.set(MX - 2, cy + 7, MZ, cluster('west'))
    # copper arms bound into the bundle carry soul lanterns on chains: the cairn burns cold at night
    for (dx, dz, y, face) in ((0, -3, cy + 9, 'north'), (3, 0, cy + 7, 'east'), (-3, 0, cy + 11, 'west'), (0, 3, cy + 6, 'south')):
        x, z = MX + dx, MZ + dz
        ax, az = B.step(face)
        root = (x - ax, z - az)
        if not t.is_solid(root[0], y, root[1]):
            continue
        t.set(x, y, z, S('oxidized_copper_bars', **{'north': face == 'south', 'south': face == 'north',
                                                     'east': face == 'west', 'west': face == 'east',
                                                     'waterlogged': False}))
        t.set(x, y - 1, z, chain('y', copper=True))
        t.set(x, y - 2, z, lantern(soul=True, hanging=True))
    vines_on(t, rng, ((MX - 3, cy, MZ - 3), (MX + 3, cy + 8, MZ + 3)), chance=0.12, max_len=3)

    # the cantor's lectern before the cairn, the kneeling ring on terrace two, offerings at the foot
    t.set(MX, cy, MZ - 3, S('lectern', facing='north', has_book=False, powered=False))
    for (x, z) in sorted(t2 - t2_ring):
        d = math.hypot(x - MX, z - MZ)
        if not (3.0 <= d <= 3.9) or abs(x - MX) <= 1:
            continue
        if rng.random() < 0.7:
            face = 'north' if z > MZ else 'south' if z < MZ else ('west' if x > MX else 'east')
            t.set(x, cy, z, stair('or:stormstone_brick_stairs', face) if rng.random() < 0.5 else slab('or:stormstone_brick_slab'))
        elif rng.random() < 0.6:
            t.set(x, cy, z, candle(rng.randint(1, 3), rng.choice(('black', 'gray'))))
    for (x, z, n) in ((MX - 2, MZ - 2, 3), (MX + 2, MZ - 2, 2), (MX + 2, MZ + 2, 4), (MX - 2, MZ + 2, 1)):
        if t.is_air(x, cy, z):
            t.set(x, cy, z, candle(n, 'black'))
    t.set(MX + 1, cy, MZ - 3, pot('north'))

    # ------------------------------------------------------------ terrace one: singing stones and kneelers
    # holed stones: three wide, seven high, the wind sings through the hole; soul lanterns burn in two throats
    stones = [((MX - 5, MZ - 5), 'x', True), ((MX + 5, MZ - 5), 'z', False), ((MX - 5, MZ + 5), 'z', True)]
    for (x, z), axis, lit in stones:
        for k in (-1, 0, 1):
            px, pz = (x + k, z) if axis == 'x' else (x, z + k)
            for c in range(7):
                y = T1Y + 1 + c
                if c in (2, 3) and k == 0:
                    t.set(px, y, pz, AIR)
                    continue
                if c == 6:
                    if k == 0:
                        t.set(px, y, pz, 'minecraft:chiseled_deepslate')
                    else:
                        face = ('east' if k < 0 else 'west') if axis == 'x' else ('south' if k < 0 else 'north')
                        t.set(px, y, pz, stair('minecraft:polished_deepslate_stairs', face))
                    continue
                st = basalt(px, y, pz) if c < 2 else wall(px, y, pz)
                t.set(px, y, pz, st)
        if lit:
            t.set(x, T1Y + 4, z, lantern(soul=True, hanging=True))
        else:
            t.set(x, T1Y + 3, z, candle(2, 'black'))
    # the struck stone, south-east: its stump split and glassed, the rest lying broken over the edge
    sx0, sz0 = MX + 5, MZ + 5
    for k in (-1, 0, 1):
        t.set(sx0, T1Y + 1, sz0 + k, basalt(sx0, T1Y + 1, sz0 + k))
        if k != 1:
            t.set(sx0, T1Y + 2, sz0 + k, 'minecraft:cracked_deepslate_bricks')
    t.set(sx0, T1Y + 3, sz0 - 1, cluster('up'))
    t.set(sx0, T1Y + 2, sz0 + 1, cluster('up'))
    scorch(t, rng, sx0 - 1, sz0, lambda px, pz: T1Y + 1 if (px, pz) in t1 else None, 3.0, density=0.9)
    lying = [(1, 'or:stormstone_bricks'), (2, 'or:stormstone_bricks'), (3, None), (4, 'minecraft:basalt[axis=x]'),
             (6, 'minecraft:polished_deepslate_stairs'), ]
    for (k, st) in lying:
        px = sx0 + k
        on_top = (px, sz0) in t1
        y = T1Y + 1 if on_top else FY
        for dz in (-1, 0, 1):
            pz = sz0 + dz
            if not t.inside(px, y, pz) or (px, pz) not in apron:
                continue
            if st is None:                               # the hole, lying open
                if dz != 0:
                    t.set(px, y, pz, 'or:stormstone_bricks')
                continue
            if st.endswith('_stairs'):
                t.set(px, y, pz, stair(st, 'west'))
            else:
                t.set(px, y, pz, st)
            if not on_top:
                t.set(px, GY, pz, rough(px, GY, pz))
                t.set(px, 0, pz, rough(px, 0, pz))
    # kneeling stones in rows before the north stair, an aisle left open up the middle
    for z in (zn + 1, zn + 2):
        for x in list(range(MX - 5, MX - 1)) + list(range(MX + 2, MX + 5)):
            if (x, z) in t1 and (x, z) not in t1_ring and t.is_air(x, T1Y + 1, z):
                if rng.random() < 0.82:
                    t.set(x, T1Y + 1, z, slab('or:stormstone_brick_slab') if (x + z) % 3 else stair('or:stormstone_brick_stairs', 'south'))
                elif rng.random() < 0.5:
                    t.set(x, T1Y + 1, z, candle(rng.randint(1, 4), 'black'))
    # moss creeping over the terrace, debris of the strike
    t1_top = {(x, z) for (x, z) in t1 if t.is_solid(x, T1Y, z)}
    scatter_on(t, rng, t1_top, lambda x, z: T1Y + 1, [('minecraft:moss_carpet', 5), ('oldrepublic:debris', 1)], 0.08)

    # ------------------------------------------------------------ the camp
    inside = []
    # the cantor's tent (east): hides over a dark frame, black cloth gables; the offering chest inside
    inside += tent(t, rng, 24, 6, 7, 6, FY, axis='z', cloth='minecraft:black_wool', door_end='high')
    t.chest(*CHEST, facing='south', loot=LOOT_ID)
    t.anchor('CHEST_1', *CHEST)
    for (x, z, c) in ((26, 8, 'black'), (26, 9, 'black'), (28, 9, 'gray'), (28, 10, 'purple')):
        t.set(x, FY, z, '%s_carpet' % c)
    t.set(28, FY, 7, candle(3, 'black'))
    t.set(26, FY, 7, barrel('up'))
    # the second tent (south-west), smaller and torn: the storm has had it
    inside += tent(t, rng, 3, 25, 5, 5, FY, axis='x', cloth='or:canvas', door_end='high', torn=0.3)
    t.set(5, FY, 27, 'gray_carpet')
    t.set(6, FY, 27, 'purple_carpet')
    # a lean-to against the terrace's south wall: posts, a hide awning, bedrolls under it
    lt_x0, lt_x1 = MX - 7, MX - 3
    for x in range(lt_x0, lt_x1 + 1):
        z_wall = max(z for (xx, z) in t1 if xx == x)
        z0 = z_wall + 1
        if (x, z0 + 2) not in apron:
            continue
        t.set(x, T1Y, z0, stair('or:canvas_stairs', 'north'))
        t.set(x, T1Y, z0 + 1, slab('or:canvas_slab'))
        if x in (lt_x0, lt_x1, lt_x0 + 2):
            t.set(x, FY, z0 + 2, S('minecraft:dark_oak_fence'))
            t.set(x, FY + 1, z0 + 2, S('minecraft:dark_oak_fence'))
            t.set(x, T1Y, z0 + 2, slab('minecraft:dark_oak_slab'))
        for z in (z0, z0 + 1):
            t.set(x, FY, z, AIR)
            t.set(x, FY + 1, z, AIR)
        inside += [(x, z0), (x, z0 + 1)]
    for x, c in ((MX - 7, 'black'), (MX - 6, 'gray'), (MX - 4, 'black'), (MX - 3, 'purple')):
        z0 = max(z for (xx, z) in t1 if xx == x) + 1
        t.set(x, FY, z0, '%s_carpet' % c)
        t.set(x, FY, z0 + 1, '%s_carpet' % c)
    t.set(MX - 5, FY, max(z for (xx, z) in t1 if xx == MX - 5) + 1, candle(2, 'gray'))
    # the fire: a cold soul fire in a ring of hearth stones, log seats, a cauldron
    fx, fz = 25, 25
    t.set(fx, GY, fz, 'minecraft:cobbled_deepslate')
    t.set(fx, FY, fz, campfire(soul=True, facing='south'))
    for (x, z) in ((fx - 3, fz), (fx - 3, fz + 1)):
        t.set(x, FY, z, log('minecraft:stripped_dark_oak_log', 'z'))
    for (x, z) in ((fx + 1, fz + 3), (fx, fz + 3)):
        t.set(x, FY, z, log('minecraft:stripped_dark_oak_log', 'x'))
    t.set(fx + 3, FY, fz - 1, S('cauldron'))
    t.set(fx + 2, FY, fz - 2, barrel('up'))
    t.set(fx + 3, FY, fz - 2, barrel('north'))
    t.set(fx + 2, FY, fz - 3, pot('west'))
    # the spawner by the fire: the Hymnsworn come when a stranger comes near
    mod.spawner(t, *SQUAD, 'hymnsworn', count=3, anchor='SQUAD')
    t.set(SQUAD[0], GY, SQUAD[2], 'minecraft:cobbled_deepslate')
    # the bell gallows: two posts, a beam, three bells that ring the singers in
    gx0, gx1, gz = 26, 30, 16
    for x in (gx0, gx1):
        t.set(x, GY, gz, 'minecraft:cobbled_deepslate')
        for y in range(FY, FY + 5):
            t.set(x, y, gz, log('minecraft:dark_oak_log'))
    for x in range(gx0, gx1 + 1):
        t.set(x, FY + 5, gz, log('minecraft:dark_oak_log', 'x'))
    t.set(gx0 - 1, FY + 5, gz, stair('minecraft:dark_oak_stairs', 'east', 'top'))
    t.set(gx1 + 1, FY + 5, gz, stair('minecraft:dark_oak_stairs', 'west', 'top'))
    for x in (gx0 + 1, gx0 + 3):
        t.set(x, FY + 4, gz, bell('north'))
    t.set(gx0 + 2, FY + 4, gz, chain('y'))
    t.set(gx0 + 2, FY + 3, gz, bell('north'))
    for x in (gx0, gx1):
        t.set(x, FY + 3, gz + 1, S('soul_wall_torch', facing='south'))
    # salvaged copper pipe stacked by the gallows, a chipped anvil to cut it
    for (x, y, z) in ((27, FY, 19), (28, FY, 19), (29, FY, 19), (27, FY, 20), (28, FY, 20), (28, FY + 1, 19)):
        t.set(x, y, z, rod(facing='east'))
    t.set(30, FY, 20, S('chipped_anvil', facing='north'))
    t.set(24, FY, 14, barrel('east'))
    t.set(24, FY, 15, S('or:cargo_crate'))

    # ------------------------------------------------------------ the dead tree and its roots (west)
    snag = (3, MZ)
    for (x, z) in ((2, MZ), (3, MZ), (2, MZ + 1), (3, MZ + 1), (4, MZ)):
        t.set(x, 0, z, 'minecraft:rooted_dirt')
        t.set(x, GY, z, 'minecraft:rooted_dirt')
    for y in range(FY, FY + 9):
        t.set(snag[0], y, snag[1], log('minecraft:dark_oak_log'))
    t.set(2, FY, MZ, S('minecraft:dark_oak_wood', axis='y'))
    t.set(3, FY, MZ + 1, S('minecraft:dark_oak_wood', axis='y'))
    for (x, y, z, ax) in ((4, FY + 6, MZ, 'x'), (5, FY + 7, MZ, 'x'), (2, FY + 8, MZ, 'x'), (3, FY + 7, MZ - 1, 'z'),
                          (3, FY + 8, MZ - 2, 'z')):
        t.set(x, y, z, log('minecraft:dark_oak_log', ax))
    for (x, y, z, n) in ((5, FY + 6, MZ, 3), (2, FY + 7, MZ, 2), (3, FY + 7, MZ - 2, 4), (4, FY + 5, MZ, 1)):
        for k in range(n):
            if t.is_air(x, y - k, z):
                t.set(x, y - k, z, moss_hang(k == n - 1))
    for start, n in (((4, FY, MZ + 1), 6), ((4, FY, MZ - 1), 5), ((5, FY + 1, MZ), 7)):
        root_veins(t, rng, start, n, down=False, state=roots())
    for (x, z) in ((4, MZ + 2), (5, MZ - 1), (2, MZ - 1), (1, MZ + 1)):
        if t.is_air(x, FY, z):
            t.set(x, FY, z, roots())

    # ------------------------------------------------------------ the path north and its marker stones
    for (x, z) in ((MX - 2, 1), (MX + 2, 0)):
        t.set(x, 0, z, 'minecraft:cobbled_deepslate')
        t.set(x, GY, z, 'minecraft:cobbled_deepslate')
        stele(t, rng, x, z, FY, 2 if x < MX else 3, top=candle(3, 'black') if x < MX else cluster('up'), mat=basalt)

    # ------------------------------------------------------------ the bank, wet ground, small things
    # standing water where the ground has sunk: a few small pools, each in a basin of its own
    def free(x, z):
        return (x, z) in apron and (x, z) not in rim and (x, z) not in t1 and (x, FY, z) not in t.cells \
            and t.is_solid(x, GY, z) and t.name_at(x, GY, z) != 'minecraft:cobbled_deepslate'
    seeds = [p for p in sorted(apron - t1 - path - rim) if n_wet.at(p[0] * 0.5, p[1] * 0.5) > 0.55]
    rng.shuffle(seeds)
    pools = 0
    for (cx_, cz_) in seeds:
        if pools >= 4:
            break
        cells = [(cx_ + dx, cz_ + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if abs(dx) + abs(dz) < 2 or rng.random() < 0.4]
        ring_ok = all(t.is_solid(x + dx, GY, z + dz) or (x + dx, z + dz) in cells
                      for (x, z) in cells for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if not all(free(x, z) for (x, z) in cells) or not ring_ok:
            continue
        for (x, z) in cells:
            t.set(x, GY, z, WATER)
            t.set(x, 0, z, 'minecraft:mud')
        pools += 1
    for (x, z) in sorted(rim):
        r = rng.random()
        if (x, FY, z) in t.cells or not t.is_solid(x, GY, z):
            continue
        if r < 0.10:
            t.set(x, FY, z, roots())
        elif r < 0.16:
            t.set(x, FY, z, 'minecraft:firefly_bush')
        elif r < 0.30:
            t.set(x, FY, z, 'minecraft:moss_carpet')
    ground = {(x, z) for (x, z) in apron - t1 if t.is_solid(x, GY, z) and (x, z) not in rim}
    scatter_on(t, rng, ground - camp, lambda x, z: FY, PLANTS_WET, 0.22)
    scatter_on(t, rng, ground & camp, lambda x, z: FY, [('oldrepublic:debris', 2), ('minecraft:moss_carpet', 3),
                                                       ('minecraft:short_grass', 2), ('minecraft:fern', 1)], 0.09)

    # ------------------------------------------------------------ anchors, the open sky, the tree guard
    mod.spot(t, 'HEART', *HEART)
    mod.spot(t, 'SURFACE_GUARD', *SURFACE_GUARD)
    for p in (HEART, SURFACE_GUARD):
        if not t.is_solid(p[0], p[1] - 1, p[2]):
            t.set(p[0], p[1] - 1, p[2], 'or:stormstone')
    for (x, z) in sorted(apron):
        for y in range(FY, FY + 8):
            if (x, y, z) not in t.cells:
                t.set(x, y, z, AIR)
    tree_guard(t, sorted(apron), FY + 6)
    lit = []
    for region in ((24, FY, 6, 30, FY + 3, 11), (3, FY, 25, 7, FY + 2, 29), (lt_x0, FY, MZ + 6, lt_x1, FY + 1, MZ + 12)):
        lit += mod.light_fill(t, region=region)
    return t


def check(t):
    assert t.is_air(*CAMERA[0]) and CAMERA[0] in t.cells, 'camera eye must be air'
    # the guard ring: four cells four blocks out, each open ground two high
    gx, gy, gz = SURFACE_GUARD
    for (x, z) in ((gx + 4, gz), (gx - 4, gz), (gx, gz + 4), (gx, gz - 4)):
        if not t.inside(x, gy, z):
            continue
        assert t.is_air(x, gy + 1, z) and t.is_air(x, gy + 2, z), 'guard ring blocked at %r' % ((x, z),)
    require_reach(t, (MX, FY, 0), [('HEART', HEART, 'feet'), ('SURFACE_GUARD', SURFACE_GUARD, 'feet'),
                                   ('CHEST_1', CHEST, 'touch'), ('SQUAD', SQUAD, 'touch')],
                  label=TEMPLATE)


def write_worldgen():
    W.pool('hymn_shrine/start', [W.element(TEMPLATE, adapted=dict(
        support_depth=10, clear_from=SIZE[1], clear_inset=0, yard_stand=BURIED, ramp_rings=0))])
    W.structure('hymn_shrine', start_pool='hymn_shrine/start', biome_tag='has_structure/hymn_shrine', size=1,
                max_distance=32, start_height=1 - BURIED)
    W.structure_set('hymn_shrines', [('hymn_shrine', 1)], spacing=30, separation=10, salt=905207,
                    placement='clear_of_origin', min_distance=256)


def main(opts):
    t = build()
    check(t)
    save(t, TEMPLATE, opts, views=('se', 'nw'), plans=[GY, FY, T1Y + 1, T2Y + 1])
    write_worldgen()
    return t


if __name__ == '__main__':
    main(args())
