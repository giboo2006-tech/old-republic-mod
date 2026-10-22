"""tyrvann/downed_interceptor (worldgen, Tyrvann stormwood; docs/MILESTONE_6.md 5.6; stretch): a Hymn
interceptor brought down in the stormwood.

The Hymn's interceptors (docs/design/space.md): a slim black hull, a needle nose, a ringed engine, swept
blade wings with tall tips, violet trim, and an engine that sings. This one came down through the
stormwood's crowns: snapped trunks and torn leaves mark its way in, it ploughed a furrow through the
forest floor and lies where it stopped. Its engine has not stopped singing: the crystal in its ring
still glows (crying obsidian, amethyst), the storm has grown stormglass on the hull, and its tall tip
carries a rod the lightning keeps finding. No crew: the pilot's case lies thrown out beside the canopy.

Two starts:
  wreck_a  at the end of a long furrow, nose down in the earth, rolled onto its west wing; the west
           wing's outer blade sheared off and lies in the furrow behind it; the engine raised and
           smoking; a trail of plating, glass and crystal down the furrow.
  wreck_b  broken in two: the forward hull with the canopy rolled on its side against a giant
           stormwood, the engine section driven tail-first into a crater with its wing standing up out
           of it; Clan Hask scavengers have been at it (a spear with their banner on the crater's rim,
           a pile of stripped plating).

Frame (buried 2): y 0 under the ground, y 1 the forest floor, y 2 the walk.
"""
from common import *  # noqa: F401,F403
import choir_ruin

NAME = 'tyrvann/downed_interceptor'
SALT = 960354
SIZE = (29, 22, 31)
BURIED = 2
GY, FY = 1, 2
VARIANTS = ('a', 'b')
LOOT_ID = 'oldrepublic:chests/tyrvann/downed_interceptor'
CAMERAS = {}

HULL = [('minecraft:polished_blackstone', 5), ('minecraft:black_concrete', 3), ('minecraft:blackstone', 1),
        ('oldrepublic:wreck_plating_scorched', 1)]
VIOLET = [('minecraft:purple_concrete', 4), ('minecraft:purple_terracotta', 1), ('minecraft:amethyst_block', 1)]
DIRT = {'minecraft:mud', 'minecraft:moss_block', 'minecraft:muddy_mangrove_roots', 'minecraft:rooted_dirt',
        'minecraft:coarse_dirt', 'minecraft:podzol', 'minecraft:dirt', 'minecraft:grass_block'}


def no_giant(x, z, state):
    """#dirt never in a cell of even x and z (no open 2x2 for the stormwood giant)."""
    if x % 2 == 0 and z % 2 == 0 and B.name_of(state) in DIRT:
        return 'minecraft:packed_mud'
    return state


# ---------------------------------------------------------------- the interceptor as a model

def interceptor(wings=('west', 'east'), tips=True, dihedral=(0.0, 0.0), span=10):
    """The interceptor as model voxels {(mx, my, mz): role}: the needle nose at mz 0 (forward), the hull
    behind it (a diamond, then 3 x 3) with the canopy over mz 8..11, the engine at mz 15..20 (a nozzle with
    its glowing core inside a ring of radius 3.5, violet on both faces, held on four struts), the blade wings swept back from the
    hull at my 1 out to |mx| span, rising dihedral[side] a cell outward in half steps (slab halves), the
    tall swept tips at |mx| span + 1. x east (starboard), y up."""
    m = {}
    for mz in range(0, 16):
        if mz <= 2:
            cells = [(0, 1)]
        elif mz <= 7:
            cells = [(x, y) for x in (-1, 0, 1) for y in (0, 1, 2) if abs(x) + abs(y - 1) <= 1]
        else:
            cells = [(x, y) for x in (-1, 0, 1) for y in (0, 1, 2)]
        for (x, y) in cells:
            m[(x, y, mz)] = 'nose' if mz <= 2 else 'hull'
    for mz in list(range(3, 8)) + list(range(12, 16)):
        m[(0, 2, mz)] = 'stripe'
    for mz in range(8, 12):
        m[(0, 3, mz)] = 'canopy'
        m[(-1, 2, mz)] = 'canopy'
        m[(1, 2, mz)] = 'canopy'
    m[(0, 3, 12)] = 'hull'
    for mz in range(15, 21):
        for x in range(-4, 5):
            for y in range(-3, 6):
                r = math.hypot(x, y - 1)
                if 16 <= mz <= 18 and 2.6 <= r <= 3.7:
                    m[(x, y, mz)] = 'ring' if mz == 17 else 'trim'
                elif r <= 1.5:
                    m[(x, y, mz)] = ('glow' if (x, y) == (0, 1) else 'core') if mz == 20 else 'hull'
                elif mz == 17 and (x == 0 or y == 1) and r < 2.6:
                    m[(x, y, mz)] = 'hull'
    for side in wings:
        sgn = -1 if side == 'west' else 1
        d = dihedral[0 if side == 'west' else 1]
        top = 1
        for ax in range(2, span + 1):
            level = int(round(2 * d * (ax - 1)))
            my, half = 1 + level // 2, ('hi' if level % 2 else 'lo')
            lead = 8 + int(round(0.8 * (ax - 1)))
            trail = 15 + int(round(0.35 * (ax - 1)))
            for mz in range(lead, trail + 1):
                m[(sgn * ax, my, mz)] = ('edge_' if mz == lead else 'wing_') + half
            top = my
        if tips:
            for k in range(-1, 8):
                z0 = 15 + max(0, k) // 2
                for mz in range(z0, 19):
                    m[(sgn * (span + 1), top + k, mz)] = 'tip_edge' if (k == 7 or mz == z0) else 'tip'
    return m


def _rot(a, b, q):
    """(a, b) turned q quarter turns: (a cos - b sin, a sin + b cos)."""
    q %= 4
    return ((a, b), (-b, a), (-a, -b), (b, -a))[q]


def turn(model, pivot, centre, roll=0, pitch=0, yaw=0, keep=None):
    """Model voxels turned by quarter turns about the pivot (a model point), which lands on the template
    cell `centre`: roll about the hull's axis (+1 lifts the east wing), pitch (+1 puts the nose down),
    yaw about y (+1 swings the nose east). keep(mx, my, mz) filters voxels. Returns {(x, y, z): role}
    with each role's slab half flipped where the turn turns the model over."""
    out = {}
    upside = (roll % 4 == 2) != (pitch % 4 == 2)
    for (mx, my, mz), role in sorted(model.items()):
        if keep is not None and not keep(mx, my, mz):
            continue
        x, y, z = mx - pivot[0], my - pivot[1], mz - pivot[2]
        x, y = _rot(x, y, roll)
        z, y = _rot(z, y, pitch)
        x, z = _rot(x, z, yaw)
        if upside and role.endswith(('_lo', '_hi')):
            role = role[:-2] + ('hi' if role.endswith('_lo') else 'lo')
        if (roll % 2 or pitch % 2) and role.endswith(('_lo', '_hi')):
            role = role[:-3] + '_full'
        out[(centre[0] + x, centre[1] + y, centre[2] + z)] = role
    return out


def realise(t, rng, cells, seed, broken=0.0, scorched=0.15):
    """Set the turned voxels: the black hull in patches (scorched in places), the needle of blackstone
    wall, the violet stripe and ring face, blade wings of blackstone slabs with purpur leading edges
    (full blocks where the wing stands on edge), the tall tips edged in purpur, the canopy of tinted and
    violet glass (cracked open at `broken`), the core of amethyst round the crying obsidian glow."""
    hull = patch(Noise(seed, 2.0), HULL, jitter=Noise(seed + 1, 0.8), mix=0.3)
    burn = Noise(seed + 2, 2.5)
    for (x, y, z), role in sorted(cells.items()):
        if not t.inside(x, y, z):
            continue
        if role in ('hull', 'ring', 'tip', 'wing_full'):
            st = hull(x, y, z)
            if burn.at(x, y, z) < scorched:
                st = 'oldrepublic:wreck_plating_scorched'
        elif role == 'nose':
            st = 'minecraft:polished_blackstone_wall'
        elif role == 'wing_lo':
            st = slab('minecraft:polished_blackstone_slab', 'bottom')
        elif role == 'wing_hi':
            st = slab('minecraft:polished_blackstone_slab', 'top')
        elif role == 'edge_lo':
            st = slab('minecraft:purpur_slab', 'bottom')
        elif role == 'edge_hi':
            st = slab('minecraft:purpur_slab', 'top')
        elif role in ('edge_full', 'tip_edge'):
            st = 'minecraft:purpur_block'
        elif role in ('stripe', 'trim'):
            st = pick(rng, VIOLET)
        elif role == 'canopy':
            if rng.random() < broken:
                st = AIR
            else:
                st = 'minecraft:tinted_glass' if rng.random() < 0.6 else 'minecraft:purple_stained_glass'
        elif role == 'core':
            st = 'minecraft:amethyst_block'
        else:
            st = 'minecraft:crying_obsidian'
        t.set(x, y, z, st)
    return cells


def crystal_on(t, rng, cells, roles=('core', 'glow'), chance=0.7):
    """Amethyst clusters (the engine's song) on the open faces of its core."""
    for (x, y, z), role in sorted(cells.items()):
        if role not in roles or rng.random() > chance:
            continue
        for face, (dx, dy, dz) in (('up', (0, 1, 0)), ('south', (0, 0, 1)), ('north', (0, 0, -1)), ('east', (1, 0, 0)),
                                   ('west', (-1, 0, 0)), ('down', (0, -1, 0))):
            q = (x + dx, y + dy, z + dz)
            if t.inside(*q) and q not in t.cells and q not in cells:
                t.set(*q, S('amethyst_cluster', facing=face, waterlogged=False))
                break


def stormglass_on(t, rng, cells, n):
    """Stormglass the storm grew on the hull: clusters on the wreck's top faces."""
    tops = [(x, y, z) for (x, y, z) in sorted(cells) if t.is_solid(x, y, z) and t.inside(x, y + 1, z)
            and (x, y + 1, z) not in t.cells and nature._full_block(t.name_at(x, y, z) or '')]
    rng.shuffle(tops)
    for (x, y, z) in tops[:n]:
        t.set(x, y + 1, z, cluster('up'))


def rod_on_tip(t, cells):
    """The lightning finds the wreck: a rod on the highest point of its tall tip."""
    tips = [c for c, r in cells.items() if r == 'tip_edge']
    if tips:
        x, y, z = max(tips, key=lambda c: (c[1], -c[2]))
        if t.inside(x, y + 1, z) and (x, y + 1, z) not in t.cells:
            t.set(x, y + 1, z, rod(oxidized=False))


# ---------------------------------------------------------------- the forest floor

def floor_patch(t, rng, cells, seed):
    """The crash clearing's floor at GY over the ground below: churned earth, leaf litter, no open 2x2."""
    worn = patch(Noise(seed, 2.5), [('minecraft:coarse_dirt', 4), ('minecraft:rooted_dirt', 2), ('minecraft:podzol', 2),
                                    ('minecraft:packed_mud', 2), ('minecraft:gravel', 1), ('minecraft:moss_block', 1)],
                 jitter=Noise(seed + 1, 0.9), mix=0.4)
    for (x, z) in sorted(cells):
        t.set(x, 0, z, pick(rng, [('minecraft:dirt', 3), ('minecraft:mud', 1), ('minecraft:packed_mud', 1)]))
        t.set(x, GY, z, no_giant(x, z, worn(x, GY, z)))


def furrow(t, rng, seed, spine, width_at):
    """The furrow the hull ploughed: along spine [(x, z)...], width_at(i) wide, the floor dug out of GY
    (the bottom at y 0 of scorched earth and gravel), the spoil thrown up in berms at FY on both sides.
    Returns (bed, berms): the cells of its bottom and of its banks."""
    bed, berm = set(), set()
    for i, (cx, cz) in enumerate(spine):
        w = width_at(i)
        for dx in range(-w // 2 - 1, w // 2 + 2):
            x = cx + dx
            if abs(dx) <= w // 2:
                bed.add((x, cz))
            else:
                berm.add((x, cz))
    berm -= bed
    for (x, z) in sorted(bed):
        if not t.inside(x, 0, z):
            continue
        t.set(x, 0, z, no_giant(x, z, pick(rng, [('minecraft:coarse_dirt', 3), ('minecraft:gravel', 2),
                                                 ('minecraft:packed_mud', 2), ('minecraft:rooted_dirt', 1),
                                                 ('minecraft:blackstone', 1)])))
        t.set(x, GY, z, AIR)
    for (x, z) in sorted(berm):
        if not t.inside(x, 0, z):
            continue
        t.set(x, 0, z, 'minecraft:dirt')
        t.set(x, GY, z, no_giant(x, z, pick(rng, [('minecraft:coarse_dirt', 3), ('minecraft:rooted_dirt', 2),
                                                  ('minecraft:podzol', 1)])))
        if rng.random() < 0.55:
            t.set(x, FY, z, no_giant(x, z, pick(rng, [('minecraft:coarse_dirt', 3), ('minecraft:rooted_dirt', 2),
                                                      ('minecraft:gravel', 1)])))
    return bed, berm


def snapped(t, rng, x, z, h, fall=None, length=0):
    """A stormwood the interceptor snapped coming down: a jagged stump h high, its trunk lying `fall`
    (a direction) on the floor beside it, `length` long, a few torn leaves."""
    t.set(x, 0, z, 'minecraft:dirt')
    t.set(x, GY, z, 'minecraft:rooted_dirt' if (x % 2 or z % 2) else 'minecraft:packed_mud')
    for y in range(FY, FY + h):
        t.set(x, y, z, log('minecraft:dark_oak_log'))
    t.set(x, FY + h, z, log('minecraft:stripped_dark_oak_log') if rng.random() < 0.5 else AIR)
    if fall:
        dx, dz = B.step(fall)
        ax = 'x' if dx else 'z'
        for k in range(2, 2 + length):
            px, pz = x + dx * k, z + dz * k
            if not t.inside(px, FY, pz):
                break
            t.set(px, FY, pz, log('minecraft:dark_oak_log', ax))
            if t.name_at(px, GY, pz) is None:
                t.set(px, 0, pz, 'minecraft:dirt')
                t.set(px, GY, pz, no_giant(px, pz, 'minecraft:coarse_dirt'))
        leaf = S('jungle_leaves', distance=1, persistent=True, waterlogged=False)
        ex, ez = x + dx * (length + 2), z + dz * (length + 2)
        for (ox, oz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1)):
            px, pz = ex + ox, ez + oz
            if t.inside(px, FY, pz) and t.is_air(px, FY, pz) and rng.random() < 0.75:
                if t.name_at(px, GY, pz) is None:
                    t.set(px, 0, pz, 'minecraft:dirt')
                    t.set(px, GY, pz, no_giant(px, pz, 'minecraft:coarse_dirt'))
                t.set(px, FY, pz, leaf)


def debris_trail(t, rng, cells, density):
    """Plating, glass and crystal strewn down the furrow and the clearing."""
    for (x, z) in sorted(cells):
        if rng.random() >= density:
            continue
        y = top_solid(t, x, z, y_from=FY + 1)
        if y is None or y + 1 >= t.size[1] or not t.is_air(x, y + 1, z):
            continue
        if not nature._full_block(t.name_at(x, y, z) or ''):
            continue
        st = pick(rng, [('oldrepublic:debris', 4), ('oldrepublic:wreck_plating_scorched', 2), ('oldrepublic:scorch_mark', 4),
                        ('minecraft:purple_stained_glass_pane', 1), ('minecraft:small_amethyst_bud[facing=up,waterlogged=false]', 2),
                        ('minecraft:polished_blackstone_pressure_plate', 1), ('oldrepublic:wreck_salvage', 1)])
        t.set(x, y + 1, z, st)


def finish(t, rng, open_cells, height=12):
    """Explicit air over the clearing (the world's undergrowth goes), stray blocks pruned, the guard
    lattice over everything the template tops (no tree roots on the wreck)."""
    sx, sy, sz = t.size
    for (x, z) in sorted(open_cells):
        for y in range(FY, min(sy, FY + height)):
            if (x, y, z) not in t.cells:
                t.set(x, y, z, AIR)
    gone = prune_floating(t)
    if gone:
        print('  pruned %d in %s' % (len(gone), t.name))
    cols = {(x, z) for (x, y, z) in t.cells if t.is_solid(x, y, z)}
    guard_trees(t, columns=cols)


# ---------------------------------------------------------------- the two starts

def wreck_a(seed):
    tid = NAME + '/wreck_a'
    t = Template(*SIZE, seed=seed, name=tid)
    rng = t.rng
    sx, sy, sz = SIZE
    clearing = superellipse(sx, sz, 14, 15, 13.5, 15.5, power=2.6, noise=Noise(seed + 3, 3.0), amp=0.1, freq=0.35)
    floor_patch(t, rng, clearing, seed + 4)
    spine = [(14 + (1 if z > 26 else 0), z) for z in range(sz - 1, 2, -1)]
    bed, berm = furrow(t, rng, seed + 5, spine, lambda i: 3 + min(4, i // 5))
    # the spoil heaped over the needle where it went in
    for (x, z) in ((13, 2), (14, 2), (15, 2), (13, 3), (15, 3), (14, 1), (12, 3), (16, 3)):
        t.set(x, 0, z, 'minecraft:dirt')
        t.set(x, GY, z, no_giant(x, z, 'minecraft:coarse_dirt'))
        if (x, z) in ((13, 2), (14, 2), (15, 2), (14, 1)):
            t.set(x, FY, z, no_giant(x, z, pick(rng, [('minecraft:coarse_dirt', 2), ('minecraft:rooted_dirt', 1)])))
    model = interceptor(dihedral=(-0.3, 0.28))
    # broken-backed: the forward hull a cell lower than the engine half; the west wing sheared at its root
    fore = turn(model, (0, 1, 0), (14, 2, 3), keep=lambda mx, my, mz: mz <= 9 and mx >= -4)
    aft = turn(model, (0, 1, 0), (14, 3, 3), keep=lambda mx, my, mz: mz >= 10 and mx >= -4)
    hulls = dict(fore)
    hulls.update(aft)
    realise(t, rng, hulls, seed + 6, broken=0.4)
    for (x, y, z) in sorted(hulls):
        if y == 2 and z == 12 and x in (13, 15):
            t.set(x, y, z, 'oldrepublic:wreck_plating_scorched')
    # the outer west wing, sheared off, lies upside down in the furrow behind with its tip in the earth
    blade = turn(model, (-7, 1, 15), (8, 2, 25), roll=2, yaw=-1, keep=lambda mx, my, mz: mx <= -5)
    realise(t, rng, blade, seed + 7, scorched=0.3)
    crystal_on(t, rng, hulls)
    stormglass_on(t, rng, hulls, 5)
    rod_on_tip(t, hulls)
    t.set(14, 0, 25, S('campfire', lit=True, signal_fire=True, facing='north', waterlogged=False))
    # the pilot's case, thrown out of the canopy onto the bank
    t.set(18, 0, 10, 'minecraft:dirt')
    t.set(18, GY, 10, 'minecraft:coarse_dirt')
    t.chest(18, FY, 10, 'west', loot=LOOT_ID)
    for (x, z, h, fall, n) in ((5, 28, 2, 'west', 3), (23, 27, 3, 'east', 4), (4, 18, 4, None, 0), (24, 14, 2, 'south', 4),
                               (8, 6, 3, None, 0)):
        snapped(t, rng, x, z, h, fall, n)
    scorch(t, rng, 14, 20, lambda x, z: (top_solid(t, x, z, y_from=FY) or 0) + 1, 7, density=0.45)
    debris_trail(t, rng, bed | berm, 0.16)
    finish(t, rng, clearing | bed | berm)
    CAMERAS[tid] = ((11, FY + 1, 29), (14, 5, 12))
    return t


def wreck_b(seed):
    tid = NAME + '/wreck_b'
    t = Template(*SIZE, seed=seed, name=tid)
    rng = t.rng
    sx, sy, sz = SIZE
    clearing = superellipse(sx, sz, 14, 15, 13.5, 15.5, power=2.6, noise=Noise(seed + 3, 3.0), amp=0.1, freq=0.35)
    floor_patch(t, rng, clearing, seed + 4)
    cx, cz = 15, 19                                        # the crater the engine section drove into
    crater = {(x, z) for (x, z) in clearing if math.hypot(x - cx, z - cz) <= 4.7}
    rim = {(x, z) for (x, z) in clearing if 4.7 < math.hypot(x - cx, z - cz) <= 6.3}
    for (x, z) in sorted(crater):
        t.set(x, 0, z, no_giant(x, z, pick(rng, [('minecraft:coarse_dirt', 2), ('minecraft:gravel', 2), ('minecraft:blackstone', 1),
                                                 ('minecraft:packed_mud', 1)])))
        t.set(x, GY, z, AIR)
    for (x, z) in sorted(rim):
        if rng.random() < 0.7:
            t.set(x, FY, z, no_giant(x, z, pick(rng, [('minecraft:coarse_dirt', 3), ('minecraft:rooted_dirt', 2), ('minecraft:gravel', 1)])))
    model = interceptor(wings=('west',))
    # tail first into the crater: the ring a drum standing in it, the hull's stub up out of the drum,
    # the west wing a black blade standing on edge beside it
    tail = turn(model, (0, 1, 19), (cx, 1, cz), pitch=-1, keep=lambda mx, my, mz: mz >= 12)
    realise(t, rng, tail, seed + 6, scorched=0.25)
    crystal_on(t, rng, tail, roles=('core', 'glow', 'trim'), chance=0.35)
    stormglass_on(t, rng, tail, 4)
    rod_on_tip(t, tail)
    # the forward hull rolled onto its side against a giant stormwood
    tx, tz = 19, 5
    for (x, z) in ((tx, tz), (tx + 1, tz), (tx, tz + 1), (tx + 1, tz + 1)):
        t.set(x, 0, z, 'minecraft:dirt')
        t.set(x, GY, z, 'minecraft:rooted_dirt' if (x % 2 or z % 2) else 'minecraft:packed_mud')
        for y in range(FY, FY + 15):
            t.set(x, y, z, log('minecraft:dark_oak_log'))
    leaf = S('jungle_leaves', distance=1, persistent=True, waterlogged=False)
    for y in range(FY + 11, FY + 17):
        r = 5.2 if y < FY + 15 else 3.5
        for x in range(int(tx + 0.5 - r), int(tx + 0.5 + r) + 2):
            for z in range(int(tz + 0.5 - r), int(tz + 0.5 + r) + 2):
                if math.hypot(x - tx - 0.5, z - tz - 0.5) <= r - rng.random() * 0.9 and t.inside(x, y, z) and t.is_air(x, y, z):
                    t.set(x, y, z, leaf)
    for (y, ox, oz, ax) in ((FY + 8, 1, 0, 'x'), (FY + 10, -1, 0, 'x'), (FY + 12, 0, 1, 'z'), (FY + 9, 0, -1, 'z')):
        bx = tx + (2 if ox > 0 else -1 if ox < 0 else 0)
        bz = tz + (2 if oz > 0 else -1 if oz < 0 else 0)
        for k in range(3):
            t.set(bx + ox * k, y + k // 2, bz + oz * k, log('minecraft:dark_oak_log', ax))
    fore = turn(model, (0, 1, 6), (tx - 3, 3, 8), roll=1, keep=lambda mx, my, mz: mz <= 11 and -3 <= mx <= 3)
    realise(t, rng, fore, seed + 7, broken=0.5)
    stormglass_on(t, rng, fore, 3)
    # Clan Hask have been at it: their spear and banner on the rim, a pile of stripped plate
    for y in range(FY, FY + 4):
        t.set(8, y, 24, S('dark_oak_fence'))
    t.set(8, FY + 4, 24, rod(oxidized=False))
    mod.banner(t, 8, FY + 3, 25, 'south', 'red')
    for (x, z, h) in ((6, 21, 2), (7, 21, 1), (6, 22, 1), (5, 21, 1)):
        for k in range(h):
            t.set(x, FY + k, z, pick(rng, [('oldrepublic:wreck_plating_scorched', 2), ('minecraft:polished_blackstone', 1),
                                           ('minecraft:purple_concrete', 1)]))
    t.set(13, 0, 9, 'minecraft:dirt')
    t.set(13, GY, 9, 'minecraft:coarse_dirt')
    t.chest(13, FY, 9, 'west', loot=LOOT_ID)
    t.set(cx + 3, 0, cz + 3, S('campfire', lit=True, signal_fire=True, facing='north', waterlogged=False))
    for (x, z, h, fall, n) in ((5, 28, 3, 'east', 4), (24, 27, 2, 'west', 3), (4, 11, 3, None, 0)):
        snapped(t, rng, x, z, h, fall, n)
    scorch(t, rng, cx, cz, lambda x, z: (top_solid(t, x, z, y_from=FY) or 0) + 1, 7, density=0.5)
    debris_trail(t, rng, clearing, 0.08)
    finish(t, rng, clearing)
    CAMERAS[tid] = ((11, FY + 1, 29), (14, 6, 16))
    return t


def build(variant):
    seed = SALT * 10 + VARIANTS.index(variant) + 1
    t = wreck_a(seed) if variant == 'a' else wreck_b(seed)
    eye = CAMERAS[t.name][0]
    assert t.name_at(*eye) == 'minecraft:air', ('camera eye', t.name, eye, t.name_at(*eye))
    chest = [p for p, nbt in sorted(t.nbt.items()) if 'LootTable' in str(nbt)]
    assert len(chest) == 1, chest
    require_reach(t, (eye[0], eye[1] - 1, eye[2]), [('pilot case', chest[0], 'touch')])
    return t


def write_worldgen():
    els = [W.element(NAME + '/wreck_' + v, adapted=dict(support_depth=8, clear_from=SIZE[1], clear_inset=0,
                                                        yard_stand=FY, ramp_rings=2)) for v in VARIANTS]
    W.pool(NAME + '/start', els)
    W.structure(NAME, NAME + '/start', 'has_structure/' + NAME, size=1, max_distance=32, start_height=1 - BURIED)
    W.structure_set(NAME, [(NAME, 1)], 40, 14, SALT, placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:tyrvann_stormwood'])
    loot_table('chests/tyrvann/downed_interceptor', [
        ((2, 4), [('oldrepublic:durasteel_plate', 6, 1, 2), ('copper_ingot', 4, 1, 3), ('redstone', 3, 1, 3),
                  ('oldrepublic:stormglass_shard', 4, 1, 3), ('amethyst_shard', 4, 1, 4), ('oldrepublic:lumen_thread', 2, 1, 2),
                  ('oldrepublic:medpac', 2, 1, 1), ('oldrepublic:credit_chit', 3, 1, 3)]),
        (1, [('oldrepublic:hyperfuel_cell', 2, 1, 1), ('oldrepublic:alloy_plate', 2, 1, 1), ('oldrepublic:hymnstone_shard', 1, 1, 1),
             ('oldrepublic:credit_chit', 3, 2, 5)]),
    ])


def main(opts):
    ts = {}
    for v in VARIANTS:
        tid = NAME + '/wreck_' + v
        t = build(v)
        ts[tid] = t
        if wanted(opts, tid):
            save(t, tid, opts, views=('se', 'nw'), plans=[0, GY, FY + 2])
    write_worldgen()
    res = jigsaw.assemble(NAME + '/start', max_distance=32, size=1, start_height=1 - BURIED)
    if res.problems:
        print(res.report())
        raise SystemExit('jigsaw problems in %s' % NAME)
    return ts


if __name__ == '__main__':
    main(args())
