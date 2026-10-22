"""planet/heartstone: the Watch's first home, the great corrupted stone in its ring (docs/MILESTONE_6.md 5.5).

"The Watch kept its first home here, around a great waystone called the Heartstone." The great stone, a
rugged tower of dark rock 19 blocks high, stands split down its north face; in the back of the cleft, at
eye level, sits the heartstone, red light rising from the cracks at its foot. Corruption spreads from it
in noise patches: the floor of the old ceremonial court blackened and cracked near the stone, the turf
gone to coarse dirt and dry grass, then to grass again at the ring. Eleven standing stones ring the court
(a trilithon gate in the north, one leaning, one broken, one fallen outward); outside them the first
enclave lies in fragments, a wall corner, a cell's foundation, a stair to nowhere, cold braziers, and the
silver trees there have died standing.

Runtime site (PlanetSites.HEARTSTONE, shared): 35 x 26 x 35, buried 3. HEART (17, 3, 15) is the site
position; STONE (17, 5, 18) holds the heartstone. The story's waves spawn on rings of radius 9 and 11
round HEART (WorldArcs.ringSpot, 7 angles); every one of those spots is open ground in all four
rotations (checked below).
"""
import math

from selvaren_lib import (AIR, B, FIELD, Noise, Palette, S, Template, WATCH, check_chests, field_stone, meta,
                          mod, nature, pick, ring_open, save, silver_tree, slab, stair)

SX, SY, SZ = 35, 26, 35
BURIED = 3
FEET = 3
TURF = 2
HEART = (17, 3, 15)
STONE = (17, 5, 18)
GC = (17.0, 20.0)       # the great stone's foot centre
SEED = 960201

GREAT = [('minecraft:deepslate[axis=y]', 7), ('minecraft:cobbled_deepslate', 2), ('minecraft:tuff', 3),
         ('minecraft:blackstone', 2), ('minecraft:basalt[axis=y]', 1), ('minecraft:polished_deepslate', 1)]


def corruption(x, z, n):
    """0 (clean) .. 1 (dead): falls off from the great stone, in noise patches."""
    d = math.hypot(x - GC[0], z - GC[1])
    return max(0.0, min(1.0, 1.35 - d / 12.0 + (n.at(x, z) - 0.5) * 0.7))


def build():
    t = Template(SX, SY, SZ, seed=SEED, name='planet/heartstone')
    n = Noise(SEED + 1, 5.0)
    ground_layer(t, n)
    road_and_court(t, n)
    great_stone(t)
    ring(t)
    enclave(t)
    trees(t)
    field_props(t, n)
    t.anchor('STONE', *STONE)
    mod.spot(t, 'HEART', *HEART)
    return t


def ground_layer(t, n):
    """Turf over the footprint (a ragged edge left to the world), soured toward the stone in patches."""
    edge = Noise(SEED + 2, 3.0)
    var = Noise(SEED + 3, 2.2)
    for x in range(SX):
        for z in range(SZ):
            border = min(x, z, SX - 1 - x, SZ - 1 - z)
            if border == 0 and edge.at(x, z) < 0.55:
                continue
            k = corruption(x, z, n)
            v = var.at(x, z)
            if k > 0.8:
                top = 'minecraft:coarse_dirt' if v < 0.45 else 'minecraft:pale_moss_block' if v < 0.62 else \
                    'minecraft:rooted_dirt' if v < 0.8 else 'minecraft:blackstone'
            elif k > 0.5:
                top = 'minecraft:coarse_dirt' if v < 0.35 else 'minecraft:podzol[snowy=false]' if v < 0.55 else \
                    'minecraft:pale_moss_block' if v < 0.66 else 'minecraft:rooted_dirt' if v < 0.78 else \
                    'minecraft:grass_block[snowy=false]'
            elif k > 0.25:
                top = 'minecraft:grass_block[snowy=false]' if v < 0.62 else 'minecraft:coarse_dirt' if v < 0.85 else \
                    'minecraft:podzol[snowy=false]'
            else:
                top = 'minecraft:grass_block[snowy=false]' if v < 0.88 else 'minecraft:coarse_dirt'
            t.set(x, TURF, z, top)
            t.set(x, TURF - 1, z, 'minecraft:dirt' if k < 0.8 else 'minecraft:coarse_dirt')
            t.set(x, 0, z, FIELD.pick('stone', t.rng) if k < 0.6 else 'minecraft:deepslate[axis=y]')
            # explicit air over the court and the ring (the placer clears the box anyway)
            for y in range(FEET, FEET + 2):
                t.set(x, y, z, AIR)


def road_and_court(t, n):
    """The road in from the north through the gate, and the old ceremonial court round HEART, broken and
    blackened in tongues that reach out from the stone."""
    floor = WATCH.patches('floor', seed=SEED + 11, scale=2.4)
    gap = Noise(SEED + 12, 2.0)
    tongue = Noise(SEED + 14, 3.2)
    dark = Palette(d=[('minecraft:cracked_polished_blackstone_bricks', 3), ('minecraft:polished_blackstone_bricks', 2),
                      ('minecraft:cracked_deepslate_tiles', 2), ('minecraft:deepslate_tiles', 1),
                      ('minecraft:blackstone', 1)]).patches('d', seed=SEED + 13, scale=2.0)
    for z in range(0, 9):
        for x in range(14, 21):
            half = 1.7 + (gap.at(x * 0.7, z) - 0.5) * 1.4
            if abs(x - 17) <= half and gap.at(x, z * 1.3) < 0.8:
                t.set(x, TURF, z, floor(x, TURF, z) if gap.at(x * 2, z) < 0.6 else 'minecraft:gravel')
    for x in range(SX):
        for z in range(SZ):
            d = math.hypot(x - 17, (z - 16.5) * 0.95)
            if d > 8.6:
                continue
            if gap.at(x * 1.2, z * 1.2) > 0.8 - (8.6 - d) * 0.02:
                continue  # a lost flag: the soil shows
            dg = math.hypot(x - GC[0], z - GC[1])
            score = 1.25 - dg / 7.0 + (tongue.at(x, z) - 0.5) * 1.1
            t.set(x, TURF, z, dark(x, TURF, z) if score > 0.62 else floor(x, TURF, z))
    # a ring kerb of the Watch's stone round the court, half sunk and broken
    for a in range(0, 360, 4):
        r = 8.2
        x = int(round(17 + r * math.sin(math.radians(a))))
        z = int(round(16.5 - r * math.cos(math.radians(a)) / 0.95))
        if gap.at(x * 3, z * 3) < 0.62 and t.inside(x, TURF, z):
            t.set(x, TURF, z, 'minecraft:polished_andesite' if corruption(x, z, n) < 0.6 else 'minecraft:polished_deepslate')


def great_stone(t):
    """The great stone: a rugged mass of dark rock broadside to the approach (about 9 wide, 6 deep at its
    foot), 21 blocks high to a crown split in two horns, a cleft down its north face to the heartstone."""
    body = Palette(s=GREAT).patches('s', seed=SEED + 21, scale=2.6)
    out = Noise(SEED + 22, 1.6)
    vein = Noise(SEED + 23, 4.0)
    pale = Noise(SEED + 24, 2.2)
    mass = set()
    top = 21
    for y in range(FEET, top + 1):
        f = (y - FEET) / float(top - FEET)
        rx = 4.4 - 2.5 * f ** 1.2
        rz = 3.1 - 1.5 * f ** 1.2
        cx = GC[0] + 0.4 * math.sin(f * 2.5)
        cz = GC[1] - 0.7 * f
        for x in range(9, 26):
            for z in range(13, 27):
                ang = math.atan2(z - cz, x - cx)
                wob = (out.at(math.cos(ang) * 3 + 10, y * 0.6, math.sin(ang) * 3 + 10) - 0.5) * 0.45
                d = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
                if d <= 1.0 + wob:
                    mass.add((x, y, z))
    # the crown: two horns either side of a notch over the cleft
    for y in range(top + 1, top + 4):
        for (x, z) in ((15, 19), (16, 19), (15, 20), (19, 19), (18, 19), (19, 20)):
            if y == top + 3 and (x, z) not in ((15, 19), (19, 20)):
                continue
            if y == top + 2 and (x, z) in ((16, 19), (18, 19)):
                continue
            mass.add((x, y, z))
    for y in range(top - 2, top + 4):
        for z in range(15, 22):
            mass.discard((17, y, z))
    # keep the stone off the wave rings: nothing of it farther than 8.4 from HEART
    mass = {p for p in mass if math.hypot(p[0] - HEART[0], p[2] - HEART[2]) <= 8.4}
    for p in sorted(mass):
        x, y, z = p
        v = 1.0 - abs(2.0 * vein.at(x, y * 0.8, z) - 1.0)
        s = body(*p)
        near_cleft = abs(x - 17) <= 1 and y <= FEET + 9
        if v > (0.84 if near_cleft else 0.9):
            s = 'minecraft:obsidian' if v > 0.965 else 'minecraft:blackstone'
        elif pale.at(x, y, z) > 0.72 and y < 15:
            s = 'minecraft:pale_moss_block'
        t.set(x, y, z, s)
    # sloped ledges where the stone steps in, so the outline is rough rather than stepped
    for (x, y, z) in sorted(mass):
        if (x, y + 1, z) in mass:
            continue
        for d in ('north', 'south', 'east', 'west'):
            dx, dz = B.step(d)
            if (x + dx, y + 1, z + dz) in mass and t.rng.random() < 0.6:
                t.set(x, y + 1, z, stair(t.rng.choice(['minecraft:cobbled_deepslate_stairs', 'minecraft:deepslate_tile_stairs',
                                                       'minecraft:blackstone_stairs', 'minecraft:tuff_stairs']), d))
                break
        else:
            if t.rng.random() < 0.3:
                t.set(x, y + 1, z, slab(t.rng.choice(['minecraft:cobbled_deepslate_slab', 'minecraft:tuff_slab'])))
    # the cleft: a slot down the north face to the heartstone, which sits in its back at eye level; the
    # slot runs up the face as a dark seam toward the notch of the crown
    carved = []
    for y in range(FEET, FEET + 12):
        zs = sorted(z for (x, yy, z) in mass if x == 17 and yy == y)
        if not zs:
            continue
        deep = 17 if y <= FEET + 4 else zs[0]
        for z in range(zs[0], max(zs[0], deep) + 1):
            t.set(17, y, z, AIR)
            carved.append((17, y, z))
        if y > FEET + 6:
            for z in zs[1:3]:
                t.set(17, y, z, 'minecraft:obsidian' if (y + z) % 2 else 'minecraft:blackstone')
    for p in ((16, FEET, 17), (18, FEET, 17), (17, FEET, 18), (17, FEET + 1, 18)):
        t.set(*p, AIR)
    t.set(*STONE, 'or:heartstone', nbt={'id': 'oldrepublic:heartstone'})
    # red light from below: magma in the cracks at the cleft's foot
    for p in ((17, TURF, 17), (16, TURF, 17), (18, TURF, 17), (17, TURF, 18)):
        t.set(*p, 'minecraft:magma_block')
    for p in ((16, TURF, 18), (18, TURF, 18), (17, TURF, 16)):
        t.set(*p, 'minecraft:blackstone')
    # pale moss hanging from the cleft's lip, a cobweb under it
    top = max(carved, key=lambda c: c[1])
    if (top[0], top[1] + 1, top[2]) in mass:
        t.set(*top, S('minecraft:pale_hanging_moss', tip=True))
        below = (top[0], top[1] - 1, top[2])
        if t.get(*below) == AIR:
            t.set(*below, 'minecraft:cobweb')
    # the foot: fallen blocks and a broken step of dark stone round the north half
    for (x, z) in ((14, 17), (13, 18), (20, 17), (21, 18), (15, 16), (19, 16), (12, 20), (22, 20)):
        if (x, FEET, z) in mass or math.hypot(x - HEART[0], z - HEART[2]) > 8.4:
            continue
        r = t.rng.random()
        if r < 0.45:
            t.set(x, FEET, z, stair(t.rng.choice(['minecraft:cobbled_deepslate_stairs', 'minecraft:blackstone_stairs']),
                                    'south' if z >= 17 else 'north'))
        elif r < 0.8:
            t.set(x, FEET, z, t.rng.choice(['minecraft:cobbled_deepslate', 'minecraft:blackstone', 'minecraft:tuff']))
        else:
            t.set(x, FEET, z, slab('minecraft:cobbled_deepslate_slab'))
    return mass


def ring(t):
    """Eleven standing stones round HEART at radius about 13.4, a trilithon gate in the north."""
    rng = t.rng
    hx, hz = HEART[0], HEART[2]
    # the gate: two uprights 7 high, a lintel across, cracked
    for x0 in (13, 20):
        for x in (x0, x0 + 1):
            for y in range(FEET, FEET + 7):
                t.set(x, y, 2, 'minecraft:stone_bricks' if rng.random() < 0.5 else FIELD.pick('stone', rng))
    for x in range(12, 23):
        t.set(x, FEET + 7, 2, 'minecraft:polished_andesite' if 13 <= x <= 21 else stair('minecraft:stone_brick_stairs',
                                                                                         'east' if x == 12 else 'west'))
    t.set(16, FEET + 7, 2, 'minecraft:cracked_stone_bricks')
    t.set(18, FEET + 7, 2, 'minecraft:mossy_stone_bricks')
    t.set(17, FEET + 8, 2, slab('minecraft:stone_brick_slab'))
    kinds = ['tall', 'lean', 'tall', 'broken', 'tall', 'pair', 'fallen', 'tall', 'lean', 'tall']
    for k in range(1, 11):
        a = math.radians(k * 360.0 / 11)
        r = 13.4
        cx, cz = hx + r * math.sin(a), hz - r * math.cos(a)
        # the stone's two cells lie along the tangent
        tx, tz = math.cos(a), math.sin(a)
        cells = sorted({(int(round(cx - tx * 0.5)), int(round(cz - tz * 0.5))),
                        (int(round(cx + tx * 0.5)), int(round(cz + tz * 0.5)))})
        cells = [c for c in cells if 0 < c[0] < SX - 1 and 0 < c[1] < SZ - 1]
        out = ('east' if math.sin(a) > 0.7 else 'west' if math.sin(a) < -0.7 else 'south' if math.cos(a) < 0 else 'north')
        kind = kinds[k - 1]
        dark = math.hypot(cx - GC[0], cz - GC[1]) < 10.5

        def mat():
            return rng.choice(['minecraft:tuff', 'minecraft:deepslate[axis=y]', 'minecraft:andesite']) if dark \
                else FIELD.pick('stone', rng)
        if kind == 'tall':
            h = 6 + rng.randrange(3)
            field_stone(t, cells, FEET, [h, h - 1 - rng.randrange(2)][:len(cells)], rng, top='stair', mat=mat, mossy=0.15)
        elif kind == 'lean':
            field_stone(t, cells, FEET, [6, 5][:len(cells)], rng, top='slab', lean=out, mat=mat, mossy=0.1)
        elif kind == 'pair':
            field_stone(t, cells, FEET, [7, 4][:len(cells)], rng, top='mixed', mat=mat, mossy=0.2)
        elif kind == 'broken':
            field_stone(t, cells, FEET, [2, 1][:len(cells)], rng, top='slab', mat=mat, mossy=0.3)
            dx, dz = B.step(out)
            for i in range(2, 5):
                for (x, z) in cells[:1]:
                    t.set(x + dx * i, FEET, z + dz * i, mat() if i < 4 else stair('minecraft:stone_stairs', B.opposite(out)))
        elif kind == 'fallen':
            dx, dz = B.step(out)
            for i in range(0, 6):
                for (x, z) in cells:
                    if t.inside(x + dx * i, FEET, z + dz * i):
                        t.set(x + dx * i, FEET, z + dz * i, mat())
                        if i == 5:
                            t.set(x + dx * i, FEET, z + dz * i, stair('minecraft:mossy_cobblestone_stairs', B.opposite(out)))
            # the pit it left
            for (x, z) in cells:
                t.set(x, TURF, z, 'minecraft:coarse_dirt')


def enclave(t):
    """What is left of the first enclave outside the ring: a wall corner, a cell, a stair, cold braziers."""
    rng = t.rng
    wall = WATCH.picker('old', rng)
    # north-west: the corner of a wall, two to four courses, a window slot
    for x in range(1, 8):
        h = 4 - (x // 3) if x < 7 else 1
        for y in range(FEET, FEET + h):
            t.set(x, y, 5, wall(x, y, 5))
        t.set(x, TURF, 5, 'minecraft:cobblestone')
    for z in range(5, 11):
        h = 4 if z < 7 else 2 if z < 9 else 1
        for y in range(FEET, FEET + h):
            t.set(1, y, z, wall(1, y, z))
        t.set(1, TURF, z, 'minecraft:cobblestone')
    t.set(1, FEET + 2, 6, AIR)
    t.set(1, FEET + 4, 5, stair('or:watch_stone_stairs', 'east'))
    # south-west: a monk's cell, its walls one or two courses, a doorway east, a dead tree grown inside
    for x in range(1, 9):
        for z in range(26, 33):
            edge = x in (1, 8) or z in (26, 32)
            t.set(x, TURF, z, WATCH.pick('floor', rng) if not edge else 'minecraft:cobblestone')
            if edge and not (x == 8 and z in (28, 29)):
                h = 2 if (x + z) % 3 else 1
                if z == 32 or x == 1:
                    h += 1
                for y in range(FEET, FEET + h):
                    t.set(x, y, z, wall(x, y, z))
    t.set(2, FEET, 27, slab('or:watch_stone_slab'))
    t.set(3, FEET, 27, 'minecraft:cobblestone')
    t.set(2, FEET, 31, 'minecraft:mossy_cobblestone')
    t.set(7, FEET, 31, stair('minecraft:spruce_stairs', 'south'))   # the cell's bench
    t.set(6, FEET, 31, S('minecraft:spruce_trapdoor', facing='east', half='bottom', open=True, powered=False,
                           waterlogged=False))
    # south-east: a stair of five steps climbing to nothing, a broken pier beside it
    for i in range(5):
        for x in (28, 29, 30):
            t.set(x, FEET + i, 33 - i, stair('or:watch_stone_stairs', 'north'))
            for y in range(FEET, FEET + i):
                t.set(x, y, 33 - i, WATCH.pick('wall', rng))
    for y in range(FEET, FEET + 5):
        t.set(31, y, 29, wall(31, y, 29))
    t.set(31, FEET + 5, 29, stair('or:watch_stone_stairs', 'west'))
    t.set(27, FEET, 30, 'minecraft:mossy_cobblestone')
    # north-east: a fallen column of the gate court, its drum lying in the grass
    for x in range(27, 32):
        t.set(x, FEET, 4, 'minecraft:stripped_dark_oak_log[axis=x]' if x == 27 else 'minecraft:stone_bricks')
    t.set(32, FEET, 4, 'minecraft:chiseled_stone_bricks')
    t.set(26, FEET, 3, 'minecraft:polished_andesite')
    t.set(26, FEET + 1, 3, slab('minecraft:polished_andesite_slab'))
    # the enclave's boundary wall in pieces, east and west
    for z in range(13, 21):
        if z % 4 == 3:
            continue
        for x, h in ((0, 2 + (z % 2)), (34, 1 + (z % 3 == 0))):
            for y in range(FEET, FEET + h):
                t.set(x, y, z, wall(x, y, z))
            t.set(x, TURF, z, 'minecraft:cobblestone')
    # cold braziers between the ring stones: the fires the Watch kept here
    for k in (0.5, 3.5, 7.5, 10.5):
        a = math.radians(k * 360.0 / 11)
        x = int(round(HEART[0] + 12.6 * math.sin(a)))
        z = int(round(HEART[2] - 12.6 * math.cos(a)))
        t.set(x, TURF, z, 'minecraft:polished_andesite')
        t.set(x, FEET, z, S('minecraft:campfire', facing='north', lit=False, signal_fire=False, waterlogged=False))


def trees(t):
    """Silver trees that died standing, outside the ring."""
    for (x, z, h, seed) in ((4, 29, 9, 1), (31, 6, 10, 2), (32, 25, 8, 3)):
        trunk, _ = silver_tree(t, x, FEET, z, h, SEED + 40 + seed, boughs=3, spread=2.0, dead=True)
        t.set(x, TURF, z, 'minecraft:rooted_dirt')
        # pale moss hangs from the boughs, a cobweb or two
        for (bx, by, bz) in sorted(trunk):
            if by > FEET + 3 and t.get(bx, by - 1, bz) in (None, AIR) and t.rng.random() < 0.35:
                t.set(bx, by - 1, bz, S('minecraft:pale_hanging_moss', tip=True))
            elif by > FEET + 4 and t.rng.random() < 0.08:
                for d in ('north', 'east', 'south', 'west'):
                    dx, dz = B.step(d)
                    if t.get(bx + dx, by, bz + dz) in (None, AIR):
                        t.set(bx + dx, by, bz + dz, 'minecraft:cobweb')
                        break


def field_props(t, n):
    """Plants by how sour the ground is, bones of beasts that came to the stone, pale moss at its foot."""
    for x in range(SX):
        for z in range(SZ):
            if t.get(x, FEET, z) != AIR:
                continue
            top = t.name_at(x, TURF, z)
            if top not in ('minecraft:grass_block', 'minecraft:podzol', 'minecraft:coarse_dirt', 'minecraft:rooted_dirt',
                           'minecraft:pale_moss_block'):
                continue
            k = corruption(x, z, n)
            r = t.rng.random()
            if k < 0.25 and top == 'minecraft:grass_block':
                if r < 0.3:
                    t.set(x, FEET, z, pick([('minecraft:short_grass', 10), ('minecraft:fern', 2), ('oldrepublic:silverleaf', 1),
                                            ('minecraft:short_dry_grass', 2), ('minecraft:bush', 1)], t.rng))
            elif k < 0.7:
                if r < 0.22:
                    t.set(x, FEET, z, pick([('minecraft:short_dry_grass', 6), ('minecraft:tall_dry_grass', 2),
                                            ('minecraft:dead_bush', 2)], t.rng))
            elif top == 'minecraft:pale_moss_block' and r < 0.5:
                t.set(x, FEET, z, 'minecraft:pale_moss_carpet[bottom=true,east=none,north=none,south=none,west=none]')
    for (x, z, axis) in ((4, 13, 'x'), (5, 13, 'x'), (28, 21, 'z'), (28, 22, 'z'), (12, 30, 'x')):
        t.set(x, FEET, z, 'minecraft:bone_block[axis=%s]' % axis)
    t.set(5, FEET, 10, 'minecraft:bone_block[axis=y]')


def check_rings(t):
    bad = ring_open(t, HEART, radii=(9, 11), count=7, height=SY - FEET)
    if bad:
        raise SystemExit('heartstone: wave ring spots blocked: %r' % bad[:8])


def main():
    t = build()
    t.finish()
    check_rings(t)
    check_chests(t)
    save(t, 'planet/heartstone', 'heartstone')
    meta.write('planet/heartstone', buried=BURIED, terrain='flat', max_slope=5, clearance=12, support_depth=12,
               ramp_rings=2, clear_above=10)


if __name__ == '__main__':
    main()
