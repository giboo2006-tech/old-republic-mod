"""site/verse_stone (runtime, per player, Tyrvann; docs/MILESTONE_6.md 5.6): the listening court.

A tall black monolith stands on a stepped plinth in a court of old flagstones; the verse stone sits in a
window through its heart at eye height, showing its face north and south. The Hymnsworn bound a verdigris
song-band round the shaft and set copper pipes at the plinth's corners to call the lightning down; it took
the monolith's crown, and stormglass grew in the break. A broken ring of standing stones keeps the court
(two have fallen and lie where they broke, one leans, one is split and scorched by a strike); two rows of
kneeling stones face the stone from the north, where the pilgrims' path comes in. Roots have lifted the
flagstones at the west rim around a dead stormwood stump. South of the stone the guards keep a cold fire
and their sleeping mats (the SPAWNER spot).

Ancient, alone, listening. One template stands in the stormwood, the mire and on the basalt, so it is
black stone, copper and moss, and the ground outside the court is left to the biome.

Keep: STONE (the verse stone, awake), HEART (feet, the approach), SPAWNER (feet, the guards' spot).
"""
from common import *  # noqa: F401,F403

TEMPLATE = 'site/verse_stone'
SIZE = (23, 21, 23)
BURIED = 2          # y 0 footings, y 1 the flagstones, y 2 the feet layer
C = 11              # the monolith's centre column (x and z)
GY = BURIED - 1     # the ground layer
FY = BURIED         # the feet layer

STONE = (C, FY + 1, C)          # eye height from the court
HEART = (C, FY, 6)
SPAWNER = (C, FY, 16)
CAMERA = ((C - 2, FY + 1, 4), (C, FY + 5, C))   # eye, look: from the path, the monolith and its window


def court_cells(noise):
    """A squared court (a superellipse, not a circle), eroded at the rim."""
    out = set()
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            dx, dz = abs(x - C) / 8.4, abs(z - C) / 8.4
            d = (dx ** 4 + dz ** 4) ** 0.25
            if d <= 1.0 - 0.10 * noise.at(x * 1.3, z * 1.3):
                out.add((x, z))
    return out


def build(seed=7311):
    t = Template(*SIZE, seed=seed, name=TEMPLATE)
    rng = t.rng
    n_rim = Noise(seed + 1, 2.5)
    n_flag = Noise(seed + 2, 2.0)
    n_moss = Noise(seed + 3, 3.0)
    sx, sy, sz = SIZE
    body = HYMN.picker('basalt', rng)
    worn = GROUND.patches('worn', seed + 4)

    # ------------------------------------------------------------ the court: big flags, some lost, some tipped
    court = court_cells(n_rim)
    path = {(C, z) for z in range(0, 4)} | {(C - 1, 1), (C + 1, 2), (C - 1, 3), (C + 1, 0)}
    flag_mats = ['minecraft:polished_deepslate', 'minecraft:deepslate_tiles', 'or:stormstone',
                 'minecraft:deepslate_tiles', 'minecraft:polished_deepslate', 'or:stormstone_bricks']
    lost_soil = {}
    for (x, z) in sorted(court | path):
        gx, gz = (x - C + 1) // 2, (z - C + 1) // 2           # 2x2 flags centred on the monolith
        h = random.Random(seed * 1000003 + gx * 7919 + gz * 104729)
        mat = flag_mats[h.randrange(len(flag_mats))]
        rim = ((abs(x - C) / 8.4) ** 4 + (abs(z - C) / 8.4) ** 4) ** 0.25
        lost = h.random() < 0.10 + 0.55 * max(0.0, rim - 0.55) * 2
        if (x, z) in path and (x, z) not in court:
            lost = h.random() < 0.35
        if lost:
            t.set(x, GY, z, worn(x, GY, z) if n_moss.at(x, z) < 0.55 else 'minecraft:moss_block')
            lost_soil[(x, z)] = True
        else:
            if mat == 'minecraft:deepslate_tiles' and h.random() < 0.4:
                mat = 'minecraft:cracked_deepslate_tiles'
            t.set(x, GY, z, mat)
        t.set(x, 0, z, pick(rng, [('minecraft:cobbled_deepslate', 3), ('minecraft:mud', 2), ('minecraft:tuff', 1)]))
    # the court is open to the sky: nothing grows in it but what the template puts there
    clear_air(t, court, FY, sy - 1)
    clear_air(t, path - court, FY, FY + 3)

    # ------------------------------------------------------------ the monolith on its plinth
    x0, x1, z0, z1 = C - 1, C + 1, C - 1, C + 1        # the 3x3 core
    for x in range(C - 2, C + 3):
        for z in range(C - 2, C + 3):
            core = x0 <= x <= x1 and z0 <= z <= z1
            corner = abs(x - C) == 2 and abs(z - C) == 2
            if core:
                t.set(x, FY, z, 'minecraft:chiseled_deepslate' if (x, z) == (C, C) else 'minecraft:polished_deepslate')
            elif corner:
                t.set(x, FY, z, 'minecraft:polished_deepslate')
            elif (x, z) in ((C, C - 2), (C, C + 2)):
                t.set(x, FY, z, AIR)            # the standing places, at court level in front of the window
            else:
                face = 'east' if x == C - 2 else 'west' if x == C + 2 else 'south' if z == C - 2 else 'north'
                t.set(x, FY, z, stair('minecraft:polished_deepslate_stairs', face))
    # the window course: jambs of polished basalt, the stone in the middle, open north and south
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, FY + 1, z, 'minecraft:polished_basalt[axis=y]')
    t.set(C, FY + 1, C - 1, AIR)
    t.set(C, FY + 1, C + 1, AIR)
    t.set(*STONE, S('or:verse_stone', awake=True), nbt={'id': 'oldrepublic:verse_stone'})
    t.anchor('STONE', *STONE)
    # lintel, then the tooled shaft: courses of polished and smooth basalt, a rough course every third
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, FY + 2, z, 'minecraft:chiseled_deepslate' if x == C else 'minecraft:polished_deepslate')
    shaft_top = FY + 11
    for y in range(FY + 3, shaft_top + 1):
        k = y - (FY + 3)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                st = ('minecraft:polished_basalt[axis=y]', 'minecraft:smooth_basalt', None)[k % 3]
                t.set(x, y, z, st or body(x, y, z))
    # the song-band: a verdigris course the Hymnsworn bound round the shaft
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) != (C, C):
                t.set(x, FY + 5, z, 'minecraft:oxidized_cut_copper' if (x + z) % 2 else 'minecraft:oxidized_copper')
    # the cap steps in to the pinnacle; the strike took its crown and stormglass grew in the break
    for (x, z, f) in ((C, z0, 'south'), (C, z1, 'north'), (x0, C, 'east'), (x1, C, 'west'),
                      (x0, z0, 'south'), (x1, z0, 'south'), (x0, z1, 'north'), (x1, z1, 'north')):
        t.set(x, shaft_top + 1, z, stair('minecraft:polished_deepslate_stairs', f))
    t.set(C, shaft_top + 1, C, 'minecraft:polished_basalt[axis=y]')
    t.set(C, shaft_top + 2, C, 'minecraft:polished_basalt[axis=y]')
    t.set(C, shaft_top + 3, C, 'minecraft:basalt[axis=y]')
    t.set(C, shaft_top + 4, C, 'minecraft:cracked_deepslate_tiles')
    t.set(C, shaft_top + 5, C, cluster('up'))
    t.set(C + 1, shaft_top + 3, C, cluster('east'))
    t.set(C, shaft_top + 2, C - 1, cluster('north'))
    t.set(x1, shaft_top, z1, 'minecraft:cracked_deepslate_tiles')
    # copper pipes on the plinth's corners, organ-fashion: the storm plays them
    for (x, z, h) in ((C - 2, C - 2, 5), (C + 2, C - 2, 7), (C - 2, C + 2, 6), (C + 2, C + 2, 4)):
        for k in range(h):
            t.set(x, FY + 1 + k, z, rod())

    # ------------------------------------------------------------ the ring of standing stones
    # (angle, courses, kind): the north (the path) and the south (the guards' fire) are left open, the west
    # is the dead tree's. Two stones have fallen and lie where they broke, in pieces along the rim.
    ring = [(-58, 5, 'slab'), (-25, 0, 'fallen_n'), (8, 6, 'glass'), (38, 4, 'struck'),
            (125, 5, 'lean'), (155, 0, 'fallen_s'), (215, 6, 'glass'), (245, 2, None)]
    for ang, h, kind in ring:
        a = math.radians(ang)
        x = int(round(C + 9.2 * math.cos(a)))
        z = int(round(C + 9.2 * math.sin(a)))
        t.set(x, GY, z, 'minecraft:cobbled_deepslate')
        t.set(x, 0, z, 'minecraft:cobbled_deepslate')
        if kind and kind.startswith('fallen'):
            dz = -1 if kind == 'fallen_n' else 1
            t.set(x, FY, z, body(x, FY, z))                       # the stump, snapped off
            t.set(x, FY + 1, z, slab('minecraft:cobbled_deepslate_slab'))
            for k in (1, 2, 4):                                   # two lengths, a gap where it broke
                pz = z + dz * k
                if (x, pz) not in court:
                    t.set(x, GY, pz, worn(x, GY, pz))
                    t.set(x, 0, pz, 'minecraft:mud')
                    clear_air(t, [(x, pz)], FY + 1, FY + 2, only_unset=True)
                t.set(x, FY, pz, S('minecraft:polished_basalt' if k == 2 else 'minecraft:basalt', axis='z'))
            gz = z + dz * 3
            t.set(x, FY, gz, 'oldrepublic:debris' if (x, gz) in court else 'minecraft:moss_carpet')
            t.set(x + (1 if x < C else -1), FY, z + dz * 4, slab('or:stormstone_brick_slab'))
            continue
        top = slab('or:stormstone_brick_slab') if kind == 'slab' else cluster('up') if kind == 'glass' else None
        lean = (1 if x < C else -1, 0) if kind == 'lean' else None
        stele(t, rng, x, z, FY, h, top=top, mat=body, lean=lean)
        if kind == 'struck':
            t.set(x, FY + h - 1, z, 'minecraft:cracked_deepslate_tiles')
            t.set(x, FY + h, z, cluster('up'))
            t.set(x, FY + h - 2, z + 1, cluster('south'))
            scorch(t, rng, x - 1, z, lambda px, pz: FY, 2.4, density=0.95)
        elif rng.random() < 0.7:
            ox = 1 if x < C else -1
            if t.is_air(x + ox, FY, z) and (x + ox, z) in court:
                t.set(x + ox, FY, z, candle(rng.randint(1, 3), rng.choice(('black', 'gray'))))

    # ------------------------------------------------------------ the kneeling rows (north of the stone)
    for z in (5, 7):
        for x in (C - 4, C - 3, C - 2, C + 2, C + 3, C + 4):
            if (x, z) in court:
                t.set(x, FY, z, slab('or:stormstone_brick_slab') if (x * 3 + z) % 4 else slab('minecraft:polished_deepslate_slab'))
    candles_at(t, rng, [(C - 4, FY + 1, 5), (C + 3, FY + 1, 7), (C - 2, FY + 1, 7), (C + 4, FY + 1, 5)])
    # offerings at the monolith's foot
    t.set(C - 1, FY, C - 3, candle(3, 'black'))
    t.set(C + 1, FY, C - 3, candle(2, 'gray'))
    t.set(C - 1, FY, C + 3, candle(2, 'black'))
    t.set(C - 3, FY, C - 2, pot('south'))
    t.set(C + 3, FY, C + 1, skull(6))
    t.set(C - 2, FY + 1, C - 2, AIR)

    # ------------------------------------------------------------ the guards' post (south)
    fire = (C + 4, FY, 17)
    t.set(fire[0], GY, fire[2], 'minecraft:cobbled_deepslate')
    t.set(*fire, campfire(soul=True, facing='west'))
    for (x, z) in ((C + 3, 17), (C + 5, 17), (C + 4, 16), (C + 4, 18)):
        t.set(x, FY, z, slab('minecraft:cobbled_deepslate_slab'))
    for (x, z, c) in ((C - 3, 16, 'black'), (C - 3, 17, 'black'), (C - 4, 18, 'gray'), (C - 5, 18, 'gray')):
        t.set(x, FY, z, '%s_carpet' % c)
    t.set(C - 5, FY, 16, barrel('up'))
    t.set(C - 4, FY, 15, candle(2, 'gray'))
    # a lantern post of black iron by the fire
    for k in range(3):
        t.set(C + 6, FY + k, 15, 'minecraft:polished_blackstone_wall')
    t.set(C + 6, FY + 3, 15, lantern(soul=True))
    t.set(C + 6, GY, 15, 'minecraft:cobbled_deepslate')

    # ------------------------------------------------------------ roots, moss and the old stump
    for (x, z) in ((2, 10), (2, 11), (3, 10), (3, 11)):
        t.set(x, GY, z, 'minecraft:rooted_dirt')
        t.set(x, 0, z, 'minecraft:rooted_dirt')
    for (x, z) in ((2, 10), (3, 10), (2, 11)):
        t.set(x, FY, z, log('minecraft:dark_oak_log'))
    t.set(2, FY + 1, 10, log('minecraft:dark_oak_log'))
    t.set(2, FY + 2, 10, S('minecraft:dark_oak_wood', axis='y'))
    for (x, y, z) in ((4, FY, 10), (4, FY, 11), (5, FY, 11), (3, FY, 12), (4, FY, 9), (3, FY, 11)):
        if t.is_air(x, y, z):
            t.set(x, y, z, roots())
    t.set(6, FY, 11, stair('minecraft:deepslate_tile_stairs', 'east'))   # flags tipped up by the roots
    t.set(5, FY, 12, slab('minecraft:deepslate_tile_slab'))
    t.set(6, GY, 11, 'minecraft:rooted_dirt')
    t.set(5, GY, 12, 'minecraft:rooted_dirt')
    # moss creeping in from the rim; ferns and grass where the flagstones are gone
    # standing water where the flags are gone in the middle of the court: it floods with every storm
    for (x, z) in sorted(lost_soil):
        rim = ((abs(x - C) / 8.4) ** 4 + (abs(z - C) / 8.4) ** 4) ** 0.25
        if rim > 0.8 or (x, z) in path or not t.is_air(x, FY, z) or n_moss.at(x * 0.7 + 50, z * 0.7) > 0.5:
            continue
        nbrs = [(x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)]
        if all((n in court) and (t.is_solid(n[0], GY, n[1]) or t.name_at(n[0], GY, n[1]) == 'minecraft:water')
               for n in nbrs):
            t.set(x, GY, z, WATER)
            t.set(x, 0, z, 'minecraft:mud')
            del lost_soil[(x, z)]
    soil = {p for p in court if p in lost_soil}
    scatter_on(t, rng, soil, lambda x, z: FY, PLANTS_WET, 0.5)
    flags = court - soil
    scatter_on(t, rng, flags, lambda x, z: FY, [('minecraft:moss_carpet', 6), ('oldrepublic:debris', 1)], 0.07)
    # vines climbing the monolith's north-west faces (the storm side), never across the window
    vines_on(t, rng, ((x0 - 1, FY + 3, z0 - 1), (x1 + 1, shaft_top, z1 + 1)), chance=0.16, max_len=4)

    # ------------------------------------------------------------ anchors
    mod.spot(t, 'HEART', *HEART)
    mod.spot(t, 'SPAWNER', *SPAWNER)
    for p in (HEART, SPAWNER):
        if not t.is_solid(p[0], GY, p[2]):
            t.set(p[0], GY, p[2], 'or:stormstone')
    return t


def check(t):
    # the stone shows its faces north and south, and every spot is walkable from the path
    assert t.is_air(C, FY + 1, C - 1) and t.is_air(C, FY + 1, C + 1)
    assert CAMERA[0] in t.cells and t.is_air(*CAMERA[0])
    require_reach(t, (C, FY, 0), [('HEART', HEART, 'feet'), ('SPAWNER', SPAWNER, 'feet'), ('STONE', STONE, 'touch')],
                  natural_floor_below=lambda x, z: FY, label=TEMPLATE)


def write_meta():
    # Runtime placement: a clearing in its biome (Java binds each of the three stones to one), the
    # flagstones one layer under the feet, the court open to the sky.
    meta.write(TEMPLATE, buried=BURIED, max_slope=4, clearance=12, support_depth=8, ramp_rings=2, clear_above=10,
               terrain='any', spawn_radius=64)


def main(opts):
    t = build()
    check(t)
    save(t, TEMPLATE, opts, views=('se', 'nw'), plans=[GY, FY, FY + 1])
    write_meta()
    return t


if __name__ == '__main__':
    main(args())
