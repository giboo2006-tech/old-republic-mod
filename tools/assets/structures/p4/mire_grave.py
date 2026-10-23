"""planet/mire_grave (worldgen, Tyrvann mire; docs/MILESTONE_6.md 5.6): the warrior's barrow.

A Clan Hask warrior went into the mire after a stormback and neither came back. The clan found them
together: they raised a barrow of stones over the warrior on a mud hummock, drove the warrior's blade
into its head, ringed it with the spears of the hunting party and hung the clan's cloth from two of
them; the beast they left where it fell, and its ribcage still stands out of the black water beside the
grave, the skull half sunk at the end of the spine. The grave goods lie in a stone-lined pit at the
barrow's foot (CHEST_1: the clan iron that tyrvann_recover sends a player for). A dead mangrove hangs
pale moss over the water; roots, peat and fireflies.

Mire rule: the hummock, the barrow, the spine and every rib stand on solid footing in the bottom layer,
so the pool element's support reaches the bed of the water; the open water is the world's own.

Keep: HEART (feet, before the blade), CHEST_1 (the pit, chests/mire_grave), SURFACE_GUARD (feet, the
hummock's open ground; the 4-block ring round it stays on the island).
"""
from common import *  # noqa: F401,F403

TEMPLATE = 'planet/mire_grave'
SIZE = (25, 13, 25)
BURIED = 2
GY, FY = BURIED - 1, BURIED       # the water line and the walk level on the low island
LOOT_ID = 'oldrepublic:chests/mire_grave'
SEED = 905209

BX, BZ = 10, 15                   # the barrow's middle
HEART = (15, FY + 1, 15)          # on the hummock before the blade
CHEST = (5, FY, 15)               # in the pit at the barrow's foot (one below the hummock's walk level)
GUARD_NEAR = (16, 11)             # SURFACE_GUARD: the open cell nearest this whose 4-block ring is open island ground
CAMERA = ((19, FY + 2, 12), (10, FY + 3, 15))   # from the ribcage's shadow over the barrow to the blade


def build(seed=SEED):
    t = Template(*SIZE, seed=seed, name=TEMPLATE)
    rng = t.rng
    sx, sy, sz = SIZE
    n_rim = Noise(seed + 1, 3.0)
    n_soil = Noise(seed + 2, 3.5)
    stones = patch(Noise(seed + 3, 2.5), [('minecraft:mossy_cobblestone', 4), ('minecraft:cobbled_deepslate', 3),
                                          ('or:stormstone', 2), ('minecraft:tuff', 1)], jitter=Noise(seed + 4, 0.9), mix=0.5)
    soil = patch(n_soil, [('minecraft:mud', 4), ('or:peat', 3), ('minecraft:packed_mud', 1), ('minecraft:moss_block', 2)],
                 jitter=Noise(seed + 5, 1.0), mix=0.4)

    island = blob(sx, sz, 12, 14, 10.4, 9.0, n_rim, wobble=0.16)
    island = {(x, z) for (x, z) in island if 1 <= x <= sx - 2 and 2 <= z <= sz - 2}
    hummock = blob(sx, sz, 11, 15, 7.4, 6.2, Noise(seed + 6, 3.0), wobble=0.12)
    hummock &= island
    shore = ring_of(island)

    # ------------------------------------------------------------ the island: mud at the water line, a hummock
    for (x, z) in sorted(island):
        t.set(x, 0, z, pick(rng, [('minecraft:mud', 4), ('minecraft:clay', 1), ('or:peat', 2)]))
        if (x, z) in hummock:
            t.set(x, GY, z, pick(rng, [('minecraft:mud', 3), ('or:peat', 2), ('minecraft:packed_mud', 1)]))
            t.set(x, FY, z, soil(x, FY, z))
        else:
            t.set(x, GY, z, 'minecraft:muddy_mangrove_roots' if (x, z) in shore and rng.random() < 0.3 else soil(x, GY, z))

    # ------------------------------------------------------------ the barrow: stones heaped over the warrior
    barrow = set()
    for x in range(BX - 4, BX + 5):
        for z in range(BZ - 3, BZ + 4):
            d = ((x - BX) / 4.3) ** 2 + ((z - BZ) / 2.7) ** 2
            if d <= 1.0:
                barrow.add((x, z))
                h = 2 if d < 0.35 else 1
                for k in range(h):
                    t.set(x, FY + 1 + k, z, stones(x, FY + 1 + k, z))
                if d < 0.12 and rng.random() < 0.7:
                    t.set(x, FY + 3, z, slab('minecraft:cobbled_deepslate_slab') if rng.random() < 0.6 else
                          slab('minecraft:mossy_cobblestone_slab'))
    # a kerb of bigger stones round the barrow's edge, broken
    for (x, z) in sorted(ring_of(barrow)):
        if rng.random() < 0.55:
            t.set(x, FY + 1, z, pick(rng, [('minecraft:polished_deepslate', 2), ('or:stormstone', 2),
                                           ('minecraft:mossy_stone_bricks', 1)]))
    moss_tops(t, rng, ((BX - 5, FY + 1, BZ - 4), (BX + 5, FY + 3, BZ + 4)), chance=0.35)
    # the blade at the head of the barrow: an iron blade with a chain crossguard, driven in to the hilt
    hx = BX + 4
    t.set(hx, FY + 1, BZ, 'minecraft:polished_deepslate')
    t.set(hx, FY + 2, BZ, S('iron_bars', north=False, south=False, east=False, west=False, waterlogged=False))
    t.set(hx, FY + 3, BZ, S('iron_bars', north=False, south=False, east=False, west=False, waterlogged=False))
    t.set(hx, FY + 4, BZ, chain('y'))
    t.set(hx, FY + 3, BZ - 1, chain('z'))
    t.set(hx, FY + 3, BZ + 1, chain('z'))
    t.set(hx, FY + 5, BZ, S('stone_button', face='floor', facing='east', powered=False))
    # the pit at the barrow's foot: stone-lined, the chest of grave goods, a slab half drawn over it
    px, pz = CHEST[0], CHEST[2]
    for (x, z) in ((px - 1, pz - 1), (px - 1, pz), (px - 1, pz + 1), (px, pz - 1), (px, pz + 1),
                   (px + 1, pz - 1), (px + 1, pz + 1)):
        t.set(x, FY, z, pick(rng, [('minecraft:polished_deepslate', 2), ('minecraft:cobbled_deepslate', 2),
                                   ('minecraft:mossy_cobblestone', 1)]))
    t.set(px, GY, pz, 'minecraft:cobbled_deepslate')
    t.chest(px, FY, pz, facing='east', loot=LOOT_ID)
    t.anchor('CHEST_1', px, FY, pz)
    t.set(px, FY + 1, pz, AIR)
    t.set(px + 1, FY, pz, AIR)                       # the step down into the pit, toward the barrow
    t.set(px - 1, FY + 1, pz, slab('minecraft:polished_deepslate_slab'))   # the lid, pushed aside
    t.set(px - 1, FY + 1, pz + 1, candle(2, 'red'))

    # ------------------------------------------------------------ the hunting party's spears and cloth
    spears = [(BX - 6, BZ - 3, 3, None), (BX - 3, BZ - 5, 4, 'red'), (BX + 2, BZ - 5, 3, None),
              (BX + 6, BZ - 3, 4, None), (BX + 6, BZ + 3, 3, None), (BX + 1, BZ + 5, 4, 'gray'),
              (BX - 4, BZ + 5, 3, None), (BX - 7, BZ + 2, 2, None)]
    for i, (x, z, h, cloth) in enumerate(spears):
        if (x, z) not in island:
            continue
        base = FY + 1 if (x, z) in hummock else FY
        t.set(x, base - 1, z, 'minecraft:packed_mud')
        lean = (i % 3 == 1) and cloth is None
        for k in range(h):
            ox = (1 if x > BX else -1) if (lean and k == h - 1) else 0
            t.set(x + ox, base + k, z, S('dark_oak_fence'))
        tip_x = x + ((1 if x > BX else -1) if lean else 0)
        t.set(tip_x, base + h, z, rod(oxidized=False, kind='minecraft:waxed_lightning_rod'))
        if cloth:
            face = 'north' if z < BZ else 'south'
            mod.banner(t, x, base + h - 1, z + (-1 if face == 'north' else 1), face, cloth)

    # ------------------------------------------------------------ the stormback: spine, ribs, the sunken skull
    spine_z = 5
    sx0, sx1 = 6, 19
    bone = lambda ax: S('bone_block', axis=ax)  # noqa: E731
    for x in range(sx0, sx1 + 1):
        if x in (11, 16):                                  # the spine broken where it lies deepest
            continue
        t.set(x, 0, spine_z, 'minecraft:mud')
        t.set(x, GY, spine_z, bone('x'))
        if x % 3 == 1 and sx0 < x < sx1:                   # a vertebra's spur above the water
            t.set(x, FY, spine_z, bone('y'))
    # ribs: clean arcs going out along the water, then up, then in over the spine; tallest amidships,
    # every third cell, some snapped (the piece lies in the water below), two gone
    arc = [(1, 0), (2, 0), (3, 1), (4, 2), (4, 3), (4, 4), (4, 5), (3, 6), (2, 7), (1, 7)]
    for i, x in enumerate(range(sx0 + 1, sx1, 3)):
        mid = 1 - abs(i - 1.8) / 2.6
        keep = 6 + int(round(4 * mid))                    # how much of the arc stands
        for side in (-1, 1):
            if (i, side) in ((0, -1), (4, 1)):
                continue
            snapped = (i, side) in ((1, 1), (3, -1))
            prof = arc[:max(3, keep - 4)] if snapped else arc[:keep]
            for k, (dz, dy) in enumerate(prof):
                z = spine_z + side * dz
                if not t.inside(x, GY + dy, z):
                    continue
                vertical = k + 1 < len(prof) and prof[k + 1][0] == dz
                t.set(x, GY + dy, z, bone('y' if vertical else 'z'))
                if dy == 0:
                    t.set(x, 0, z, 'minecraft:mud')
            if snapped:
                z = spine_z + side * 5
                if t.inside(x + 1, GY, z):
                    for dx in (0, 1):
                        t.set(x + dx, GY, z, bone('x'))
                        t.set(x + dx, 0, z, 'minecraft:mud')
    # the skull at the spine's head, half sunk: a long brow, the eye sockets, the jaw open on the water
    for (x, y, z) in ((20, GY, 4), (20, GY, 5), (20, GY, 6), (21, GY, 4), (21, GY, 6), (22, GY, 4), (22, GY, 6),
                      (23, GY, 5), (20, FY, 5), (21, FY, 4), (21, FY, 5), (21, FY, 6), (22, FY, 5)):
        t.set(x, y, z, bone('x'))
        t.set(x, 0, z, 'minecraft:mud')
    t.set(21, GY, 5, WATER)                              # the open jaw holds black water
    t.set(21, 0, 5, 'minecraft:mud')
    for (x, z, tip) in ((20, 3, 'north'), (20, 7, 'south')):  # the horns, sweeping back
        t.set(x, 0, z, 'minecraft:mud')
        t.set(x, GY, z, bone('y'))
        t.set(x, FY, z, bone('y'))
        dz = -1 if tip == 'north' else 1
        t.set(x - 1, FY + 1, z + dz, bone('z'))
    hang_moss(t, rng, ((sx0, GY + 3, 0), (sx1, GY + 8, 10)), chance=0.35, max_len=3, only_template=False)
    moss_tops(t, rng, ((sx0, GY, 0), (sx1, GY + 8, 10)), chance=0.15)

    # ------------------------------------------------------------ the dead mangrove and the mire's small things
    tx, tz = 4, 9
    for (x, z) in ((tx, tz), (tx + 1, tz), (tx, tz + 1)):
        t.set(x, 0, z, 'minecraft:muddy_mangrove_roots')
        t.set(x, GY, z, 'minecraft:muddy_mangrove_roots')
    for (x, y, z) in ((tx - 1, FY, tz), (tx + 2, FY, tz), (tx, FY, tz - 1), (tx + 1, FY, tz + 1), (tx - 1, FY, tz + 1)):
        if (x, z) in island and not t.is_solid(x, y, z):
            t.set(x, y, z, roots())
    for y in range(FY, FY + 7):
        t.set(tx, y, tz, log('minecraft:mangrove_log'))
    for (x, y, z, ax) in ((tx + 1, FY + 5, tz, 'x'), (tx + 2, FY + 6, tz, 'x'), (tx, FY + 6, tz - 1, 'z'),
                          (tx - 1, FY + 4, tz, 'x'), (tx - 2, FY + 5, tz, 'x')):
        t.set(x, y, z, log('minecraft:mangrove_log', ax))
    for (x, y, z, n) in ((tx + 2, FY + 5, tz, 3), (tx - 2, FY + 4, tz, 2), (tx, FY + 5, tz - 1, 2), (tx + 1, FY + 4, tz, 1)):
        for k in range(n):
            if t.is_air(x, y - k, z):
                t.set(x, y - k, z, moss_hang(k == n - 1))
    # pools on the island, each in a basin of its own
    for (cx_, cz_) in ((16, 19), (7, 20), (19, 12)):
        cells = [(cx_ + dx, cz_ + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if abs(dx) + abs(dz) < 2]
        if all((x, z) in island and (x, z) not in shore and (x, z) not in hummock and (x, FY, z) not in t.cells
               for (x, z) in cells):
            ok = all(t.is_solid(x + dx, GY, z + dz) or (x + dx, z + dz) in cells
                     for (x, z) in cells for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if ok:
                for (x, z) in cells:
                    t.set(x, GY, z, WATER)
    # the hummock's flank: a step of roots and peat down to the water line
    for (x, z) in sorted(ring_of(hummock)):
        if (x, FY + 1, z) not in t.cells and rng.random() < 0.18:
            t.set(x, FY + 1, z, 'minecraft:moss_carpet')
    low = {(x, z) for (x, z) in island - hummock if t.is_solid(x, GY, z) and (x, FY, z) not in t.cells}
    high = {(x, z) for (x, z) in hummock if t.is_solid(x, FY, z) and (x, FY + 1, z) not in t.cells}
    scatter_on(t, rng, low, lambda x, z: FY, [('minecraft:short_grass', 3), ('minecraft:fern', 2),
                                             ('minecraft:firefly_bush', 2), ('minecraft:moss_carpet', 3),
                                             ('minecraft:mangrove_roots[waterlogged=false]', 2)], 0.3)
    scatter_on(t, rng, high, lambda x, z: FY + 1, [('minecraft:short_grass', 4), ('minecraft:fern', 3),
                                                  ('minecraft:moss_carpet', 2), ('minecraft:brown_mushroom', 1),
                                                  ('minecraft:leaf_litter[facing=north,segment_amount=2]', 1)], 0.25)

    # ------------------------------------------------------------ anchors, the open sky, the tree guard
    mod.spot(t, 'HEART', *HEART)
    for (x, z) in sorted(island):
        y0 = FY + 1 if (x, z) in hummock else FY
        for y in range(y0, y0 + 5):
            if (x, y, z) not in t.cells:
                t.set(x, y, z, AIR)
    best = None
    for (x, z) in sorted(island):
        y = FY + 1 if (x, z) in hummock else FY
        if not open_ground(t, x, y, z):
            continue
        if all(open_ground(t, x + dx, None, z + dz) for dx, dz in ((4, 0), (-4, 0), (0, 4), (0, -4))):
            d = (x - GUARD_NEAR[0]) ** 2 + (z - GUARD_NEAR[1]) ** 2
            if best is None or d < best[0]:
                best = (d, (x, y, z))
    global SURFACE_GUARD
    SURFACE_GUARD = best[1]
    mod.spot(t, 'SURFACE_GUARD', *SURFACE_GUARD)
    tree_guard(t, sorted(island), FY + 5)
    return t


def open_ground(t, x, y, z):
    """Air two high over a solid cell the template sets (y None: at whichever walk level the column has)."""
    ys = (FY + 1, FY) if y is None else (y,)
    for yy in ys:
        if t.is_solid(x, yy - 1, z) and (x, yy - 1, z) in t.cells:
            return (x, yy, z) in t.cells and t.is_air(x, yy, z) and t.is_air(x, yy + 1, z)
    return False


def check(t):
    assert CAMERA[0] in t.cells and t.is_air(*CAMERA[0]), 'camera eye must be air'
    for p in (HEART, SURFACE_GUARD):
        assert t.is_solid(p[0], p[1] - 1, p[2]), 'anchor %r has nothing under it' % (p,)
    require_reach(t, (HEART[0], HEART[1], HEART[2]), [('SURFACE_GUARD', SURFACE_GUARD, 'feet'),
                                                      ('CHEST_1', CHEST, 'touch')], label=TEMPLATE)


def write_worldgen():
    W.pool('mire_grave/start', [W.element(TEMPLATE, adapted=dict(
        support_depth=10, clear_from=SIZE[1], clear_inset=0, yard_stand=BURIED, ramp_rings=0))])
    W.structure('mire_grave', start_pool='mire_grave/start', biome_tag='has_structure/mire_grave', size=1,
                max_distance=32, start_height=1 - BURIED)
    W.structure_set('mire_graves', [('mire_grave', 1)], spacing=34, separation=12, salt=905209,
                    placement='clear_of_origin', min_distance=256)


def main(opts):
    t = build()
    check(t)
    save(t, TEMPLATE, opts, views=('se', 'nw'), plans=[GY, FY, FY + 1])
    write_worldgen()
    return t


if __name__ == '__main__':
    main(args())
