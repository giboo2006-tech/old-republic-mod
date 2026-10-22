"""hub/greenwatch: what is left of the Watch's first enclave on Selvaren, a Jedi enclave reclaimed from the forest
(docs/MILESTONE_6.md 5.7; planets survey 3.3).

A walled town of worn watch stone, 91 x 46 x 91, three layers buried. Moss and roots have the old work;
timber, canvas and new stone are the Watch's repairs.

    north wall: the Watch Tower (NW corner, a ruined stone shaft with a rebuilt timber belfry, climbable),
                the quartermaster's store, the north gate, the cloister with the great silver tree (NE)
    middle:     the Keeper's Hall (a rotunda under a broken dome) and its forecourt where Keeper Ansa waits,
                the refectory and the Artifice workshop (W), the market square, the Healing House (E)
    street:     the east-west road between the west and east gates
    south:      the landing field (four 11 x 11 bays on an old parade terrace), cottages and the garden (SW),
                the spacer's shed and more cottages (SE), the south gate

Run from the repository root: python3 tools/assets/structures/h/greenwatch.py
"""
import math

from common import *  # noqa: F401,F403

ID = 'hub/greenwatch'
SEED = 0x47E3A7

# The town is drawn in its own frame (TX x TY x TZ, ground layer y 2, feet y 3) and pasted into the hub
# template at SHIFT, over a ground skirt of SKIRT rings stepping down below the town (see common.skirt).
TX, TY, TZ = 91, 46, 91
SKIRT = 4                      # rings of the skirt round the town wall
DROP = 5                       # layers added under the town (the skirt's depth)
SHIFT = (SKIRT - 2, DROP, SKIRT - 2)
SX, SY, SZ = TX + 2 * (SKIRT - 2), TY + DROP, TZ + 2 * (SKIRT - 2)
HUB_GROUND = GROUND + DROP     # the hub template's ground layer (y 7) and feet layer (y 8): buried 8
HUB_FEET = FEET + DROP

HEART = (45, FEET, 66)         # town frame
BAYS = [(38, 59), (52, 59), (38, 73), (52, 73)]   # PAD_BAY_1..4 centres (town frame); each bay is 11 x 11
BAY_R = 5
HOST = (45, FEET, 34)

# ---------------------------------------------------------------- materials

M = Palette(
    ashlar=[('or:watch_stone', 10), ('minecraft:stone_bricks', 2), ('minecraft:mossy_stone_bricks', 3),
            ('minecraft:cracked_stone_bricks', 1)],
    old=[('or:watch_stone', 5), ('minecraft:mossy_stone_bricks', 5), ('minecraft:cracked_stone_bricks', 2),
         ('minecraft:mossy_cobblestone', 2)],
    plinth=[('minecraft:cobblestone', 2), ('minecraft:mossy_cobblestone', 3), ('minecraft:andesite', 1),
            ('minecraft:stone', 1)],
    flag=[('or:watch_stone', 4), ('minecraft:polished_andesite', 2), ('minecraft:stone_bricks', 2),
          ('minecraft:mossy_stone_bricks', 2), ('minecraft:cracked_stone_bricks', 1), ('minecraft:andesite', 1)],
    terrace=[('minecraft:stone_bricks', 4), ('minecraft:mossy_stone_bricks', 3), ('minecraft:cracked_stone_bricks', 2),
             ('or:watch_stone', 3), ('minecraft:polished_andesite', 1)],
    lane=[('minecraft:dirt_path', 6), ('minecraft:coarse_dirt', 2), ('minecraft:mossy_cobblestone', 2),
          ('minecraft:cobblestone', 1), ('minecraft:packed_mud', 1)],
    earth=[(grass(), 14), (S('podzol', snowy=False), 2), ('minecraft:moss_block', 2), ('minecraft:coarse_dirt', 1),
           ('minecraft:rooted_dirt', 1)],
    planks=[('minecraft:spruce_planks', 4), ('minecraft:dark_oak_planks', 1)],
    floorboards=[('minecraft:spruce_planks', 5), ('minecraft:dark_oak_planks', 2), ('minecraft:stripped_spruce_wood', 1)],
)

STONE_WALL = 'minecraft:stone_brick_wall' if not USE_KIT_WALLS else 'oldrepublic:watch_stone_wall'
MOSSY_WALL = 'minecraft:mossy_stone_brick_wall'
STEP = 'oldrepublic:watch_stone_stairs'
STEP_SLAB = 'oldrepublic:watch_stone_slab'
ROOF = 'minecraft:dark_oak_stairs'
ROOF_SLAB = 'minecraft:dark_oak_slab'
SPRUCE_ROOF = 'minecraft:spruce_stairs'
POST = 'minecraft:stripped_dark_oak_log'
BEAM = 'minecraft:dark_oak_log'

WEATHER = {
    'oldrepublic:watch_stone': [('minecraft:mossy_stone_bricks', 0.10), ('minecraft:cracked_stone_bricks', 0.05)],
    'oldrepublic:watch_stone_stairs': [('minecraft:mossy_stone_brick_stairs', 0.15)],
    'oldrepublic:watch_stone_slab': [('minecraft:mossy_stone_brick_slab', 0.15)],
    'minecraft:stone_bricks': [('minecraft:mossy_stone_bricks', 0.2), ('minecraft:cracked_stone_bricks', 0.1)],
    'minecraft:cobblestone': [('minecraft:mossy_cobblestone', 0.35)],
    'minecraft:stone_brick_wall': [('minecraft:mossy_stone_brick_wall', 0.3)],
    'minecraft:stone_brick_stairs': [('minecraft:mossy_stone_brick_stairs', 0.25)],
}

GROUND_NAMES = ('minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block', 'minecraft:coarse_dirt',
                'minecraft:rooted_dirt')


def pick(t, role):
    return M.picker(role, t.rng)


# ---------------------------------------------------------------- the ground

def ground(t):
    earth = M.patches('earth', seed=SEED + 1, scale=5.0)
    t.fill(0, GROUND, 0, TX - 1, GROUND, TZ - 1, earth)


def pave(t, x0, z0, x1, z1, role, seed, scale=3.0, y=GROUND):
    t.fill(x0, y, z0, x1, y, z1, M.patches(role, seed=seed, scale=scale))


def lane(t, points, width, seed, jitter=0.35):
    lp = M.patches('lane', seed=seed, scale=2.5)
    shapes.path(t, points, lambda x, z: GROUND, width, lp, rng=t.rng, jitter=jitter,
                only_on=set(GROUND_NAMES) | {'minecraft:grass_block[snowy=false]', 'minecraft:podzol[snowy=false]'})


# ---------------------------------------------------------------- walls and gates

GATES = {
    'north': (57, 60),   # x span in the north wall
    'south': (43, 47),   # x span in the south wall
    'west': (48, 51),    # z span in the west wall
    'east': (48, 51),    # z span in the east wall
}
BREACHES = [('north', 22, 27), ('east', 66, 70), ('south', 16, 20), ('west', 30, 33)]


def wall_cells():
    """(x, z, outer side) of the 2-thick curtain wall ring, x 2..88, z 2..88."""
    out = []
    for x in range(2, 89):
        for z in (2, 3):
            out.append((x, z, 'north'))
        for z in (87, 88):
            out.append((x, z, 'south'))
    for z in range(4, 87):
        for x in (2, 3):
            out.append((x, z, 'west'))
        for x in (87, 88):
            out.append((x, z, 'east'))
    return out


def in_gate(x, z, side):
    a, b = GATES[side]
    v = x if side in ('north', 'south') else z
    return a <= v <= b


def breach_at(x, z, side):
    for s, a, b in BREACHES:
        v = x if s in ('north', 'south') else z
        if s == side and a <= v <= b:
            return a, b
    return None


def curtain_wall(t):
    ash = pick(t, 'ashlar')
    old = pick(t, 'old')
    plinth = pick(t, 'plinth')
    n = Noise(SEED + 7, 6.0)
    for (x, z, side) in wall_cells():
        outer = (side == 'north' and z == 2) or (side == 'south' and z == 88) or \
                (side == 'west' and x == 2) or (side == 'east' and x == 88)
        for y in range(0, GROUND + 1):
            t.set(x, y, z, plinth(x, y, z))
        if in_gate(x, z, side):
            continue
        br = breach_at(x, z, side)
        top = 8
        if br:
            a, b = br
            v = x if side in ('north', 'south') else z
            mid = (a + b) / 2.0
            top = 3 + int(abs(v - mid) * 1.2) + (1 if outer else 0)
        else:
            top = 8 - (1 if n.at(x, 0, z) > 0.78 else 0)
        for y in range(FEET, top + 1):
            t.set(x, y, z, (old if n.at(x, y, z) > 0.55 else ash)(x, y, z))
        if not br and outer and top == 8:
            # merlons on the outer row, the walk behind them
            if (x + z) % 3 != 0:
                t.set(x, 9, z, ash(x, 9, z))
            else:
                t.set(x, 9, z, wall_block(STONE_WALL))
    # timber palisade patching two of the breaches
    for side, a, b in BREACHES[:2]:
        for v in range(a, b + 1):
            if side == 'north':
                x, z = v, 3
            else:
                x, z = 87, v
            top = 9 if v % 2 else 8
            for y in range(FEET, top + 1):
                if t.is_air(x, y, z):
                    t.set(x, y, z, log('stripped_spruce_log'))
            t.set(x, top + 1, z, fence('spruce_fence'))
    # rubble spilled at the foot of the open breaches
    for side, a, b in BREACHES[2:]:
        for v in range(a - 1, b + 2):
            for d in range(0, 3):
                if side == 'south':
                    x, z = v, 86 - d
                else:
                    x, z = 4 + d, v
                if t.rng.random() < 0.55 - d * 0.15:
                    t.set(x, FEET, z, t.rng.choice(['minecraft:mossy_cobblestone', 'minecraft:cobblestone',
                                                    slab('mossy_cobblestone_slab'), 'minecraft:moss_block']))


def gate(t, side, banners=True):
    """A gate through the curtain wall: an arched opening, two flanking piers and a covered timber walk."""
    a, b = GATES[side]
    ash = pick(t, 'ashlar')
    if side in ('north', 'south'):
        zs = (2, 3) if side == 'north' else (87, 88)
        zo = zs[0] - 1 if side == 'north' else zs[1] + 1   # outside
        zi = zs[1] + 1 if side == 'north' else zs[0] - 1   # inside
        out_dir = 'north' if side == 'north' else 'south'
        in_dir = B.opposite(out_dir)
        for z in zs:
            opening(t, a, FEET, z, b, FEET + 4, z)
            # an arch: the top corners stepped in
            t.set(a, FEET + 4, z, stairs(STEP, 'east', 'top'))
            t.set(b, FEET + 4, z, stairs(STEP, 'west', 'top'))
            for x in range(a, b + 1):
                t.set(x, FEET + 5, z, ash(x, FEET + 5, z))
            pave(t, a, z, b, z, 'flag', SEED + 11)
        # piers, one cell proud of the wall on both faces, up to y 12
        for px in (a - 2, a - 1, b + 1, b + 2):
            for z in (zo, zs[0], zs[1], zi):
                for y in range(0, 13 if z in zs else 11):
                    t.set(px, y, z, ash(px, y, z) if y > GROUND else pick(t, 'plinth')(px, y, z))
        for px in (a - 2, b + 1):
            for z in (zo, zi):
                t.set(px, 11, z, slab(STEP_SLAB))
                t.set(px + 1, 11, z, slab(STEP_SLAB))
        # a covered walk over the gate: dark oak floor and roof on beams
        for x in range(a - 1, b + 2):
            for z in zs:
                t.set(x, 9, z, S('spruce_planks'))
                t.set(x, 10, z, AIR)
                t.set(x, 11, z, AIR)
                t.set(x, 12, z, slab(ROOF_SLAB))
            t.set(x, 10, zo + (1 if side == 'north' else -1), fence('spruce_fence'))
        for z in zs:
            for px in (a - 1, b + 1):
                t.set(px, 10, z, log(POST))
                t.set(px, 11, z, log(POST))
        # lanterns on the inner piers
        for px in (a - 1, b + 1):
            bracket_lamp(t, px, FEET + 4, zi, in_dir)
        if banners:
            banner(t, a - 2, FEET + 6, zo + (-1 if side == 'north' else 1), out_dir, 'green')
            banner(t, b + 2, FEET + 6, zo + (-1 if side == 'north' else 1), out_dir, 'green')
    else:
        xs = (2, 3) if side == 'west' else (87, 88)
        xo = xs[0] - 1 if side == 'west' else xs[1] + 1
        xi = xs[1] + 1 if side == 'west' else xs[0] - 1
        out_dir = 'west' if side == 'west' else 'east'
        in_dir = B.opposite(out_dir)
        for x in xs:
            opening(t, x, FEET, a, x, FEET + 4, b)
            t.set(x, FEET + 4, a, stairs(STEP, 'south', 'top'))
            t.set(x, FEET + 4, b, stairs(STEP, 'north', 'top'))
            for z in range(a, b + 1):
                t.set(x, FEET + 5, z, ash(x, FEET + 5, z))
            pave(t, x, a, x, b, 'flag', SEED + 12)
        for pz in (a - 2, a - 1, b + 1, b + 2):
            for x in (xo, xs[0], xs[1], xi):
                for y in range(0, 13 if x in xs else 11):
                    t.set(x, y, pz, ash(x, y, pz) if y > GROUND else pick(t, 'plinth')(x, y, pz))
        for pz in (a - 2, b + 1):
            for x in (xo, xi):
                t.set(x, 11, pz, slab(STEP_SLAB))
                t.set(x, 11, pz + 1, slab(STEP_SLAB))
        for z in range(a - 1, b + 2):
            for x in xs:
                t.set(x, 9, z, S('spruce_planks'))
                t.set(x, 10, z, AIR)
                t.set(x, 11, z, AIR)
                t.set(x, 12, z, slab(ROOF_SLAB))
            t.set(xo + (1 if side == 'west' else -1), 10, z, fence('spruce_fence'))
        for x in xs:
            for pz in (a - 1, b + 1):
                t.set(x, 10, pz, log(POST))
                t.set(x, 11, pz, log(POST))
        for pz in (a - 1, b + 1):
            bracket_lamp(t, xi, FEET + 4, pz, in_dir)
        if banners:
            banner(t, xo + (-1 if side == 'west' else 1), FEET + 6, a - 2, out_dir, 'green')
            banner(t, xo + (-1 if side == 'west' else 1), FEET + 6, b + 2, out_dir, 'green')


def corner_tower(t, x0, z0, height, collapse):
    """A 7 x 7 ruined corner tower; `collapse` (0..1) bites into its top from one side."""
    ash = pick(t, 'ashlar')
    old = pick(t, 'old')
    n = Noise(x0 * 31 + z0, 3.0)
    for x in range(x0, x0 + 7):
        for z in range(z0, z0 + 7):
            edge = x in (x0, x0 + 6) or z in (z0, z0 + 6)
            for y in range(0, GROUND + 1):
                t.set(x, y, z, pick(t, 'plinth')(x, y, z))
            top = height - int(collapse * 6 * n.at(x, 0, z) + (collapse * 4 if (x - x0) + (z - z0) > 8 else 0))
            for y in range(FEET, top + 1):
                if edge:
                    t.set(x, y, z, (old if y > top - 3 else ash)(x, y, z))
                elif y == 9:
                    t.set(x, y, z, S('spruce_planks') if n.at(x, 5, z) > 0.3 else AIR)
                else:
                    t.set(x, y, z, AIR)
    return n


# ---------------------------------------------------------------- the Watch Tower (NW corner)

def watch_tower(t):
    """The enclave's old bell tower: a 9 x 9 stone shaft cracked open on its west face, a stair climbing its
    inside walls, and a rebuilt timber belfry with a bell and a lantern you can see from the meadows."""
    x0, z0, x1, z1 = 2, 2, 10, 10
    ash = pick(t, 'ashlar')
    old = pick(t, 'old')
    n = Noise(SEED + 21, 4.0)
    top = 33
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(0, GROUND + 1):
                t.set(x, y, z, pick(t, 'plinth')(x, y, z))
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            for y in range(FEET, top + 1):
                if edge:
                    s = old(x, y, z) if (y < 8 or n.at(x, y, z) > 0.6) else ash(x, y, z)
                    if corner:
                        s = 'minecraft:polished_andesite' if y % 6 == 0 else s
                    t.set(x, y, z, s)
                else:
                    t.set(x, y, z, AIR)
            # floor inside at the ground layer
            if not edge:
                t.set(x, GROUND, z, M.picker('flag', t.rng)(x, GROUND, z))
    # string courses and a plinth batter
    for y in (FEET, 14, 24):
        for x in range(x0 - 1, x1 + 2):
            for z in (z0 - 1, z1 + 1):
                if t.inside(x, y, z) and not (y == FEET and (z < 2 or x < 2)):
                    t.set(x, y, z, stairs('stone_brick_stairs', 'south' if z == z1 + 1 else 'north', 'bottom')
                          if y == FEET else stairs(STEP, 'south' if z == z1 + 1 else 'north', 'top'))
        for z in range(z0, z1 + 1):
            for x in (x1 + 1,):
                t.set(x, y, z, stairs('stone_brick_stairs', 'east', 'bottom') if y == FEET else stairs(STEP, 'east', 'top'))
    # the crack: a ragged split down the north face above the wall walk, mended with timber
    for y in range(12, 27):
        w = 1 if y < 17 else 2
        cx = 6 + int(n.at(0, y, 0) * 2) - 1
        for dx in range(w):
            t.set(cx + dx, y, z0, AIR)
            if y % 4 == 0:
                t.set(cx + dx, y, z0, log('stripped_spruce_log', 'x'))
    # doorways: south (into the town) and east (onto the north wall walk)
    opening(t, 5, FEET, z1, 7, FEET + 3, z1)
    t.set(5, FEET + 3, z1, stairs(STEP, 'east', 'top'))
    t.set(7, FEET + 3, z1, stairs(STEP, 'west', 'top'))
    opening(t, x1, 9, 3, x1, 11, 4)          # onto the north wall walk
    opening(t, 3, 9, z1, 4, 11, z1)          # onto the west wall walk
    # windows: arrow slits, two to a face, every few floors
    for y in (7, 12, 17, 22, 27):
        for (x, z) in ((4, z0), (8, z0), (x0, 4), (x0, 8), (x1, 6), (6, z1)):
            if not (y < 10 and (x == x0 or z == z0)):
                t.set(x, y, z, AIR)
                t.set(x, y + 1, z, AIR)
    # the stair climbing the inside walls: one step a cell, clockwise from the door
    ring = []
    for x in range(x0 + 1, x1):
        ring.append((x, z0 + 1, 'east'))
    for z in range(z0 + 2, z1):
        ring.append((x1 - 1, z, 'south'))
    for x in range(x1 - 2, x0, -1):
        ring.append((x, z1 - 1, 'west'))
    for z in range(z1 - 2, z0 + 1, -1):
        ring.append((x0 + 1, z, 'north'))
    # start just inside the door (the south side, heading west then north)
    start = next(i for i, (x, z, d) in enumerate(ring) if (x, z) == (x1 - 2, z1 - 1))
    y = FEET
    i = start
    landings = {9: 'walk', 31: 'top'}
    while y < top - 1:
        x, z, d = ring[i % len(ring)]
        t.set(x, y, z, stairs('spruce_stairs', d))
        for k in (1, 2, 3):
            if t.get(x, y + k, z) is None or t.is_air(x, y + k, z):
                t.set(x, y + k, z, AIR)
        i += 1
        y += 1
    # a floor at the wall walk (y 8 top, feet 9) with the stair hole, and the belfry floor at y 33
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if x0 + 1 < x < x1 - 1 and z0 + 1 < z < z1 - 1:
                t.set(x, 8, z, S('spruce_planks') if (x + z) % 5 else AIR)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, top, z, S('spruce_planks') if (x, z) != (x0 + 1, z0 + 1) and (x, z) != (x0 + 1, z0 + 2) else AIR)
    # the stair's last steps run up to the belfry hatch at the north-west corner
    # (the ring ends at the west side going north, so the top lands at (x0 + 1, z0 + 1))
    # the belfry: timber posts, open sides with fence rails, a hip roof and a spire
    by = top + 1
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (6, z0), (6, z1), (x0, 6), (x1, 6)):
        for y in range(by, by + 5):
            t.set(x, y, z, log(BEAM))
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if t.get(x, by, z) is None:
                t.set(x, by, z, fence('spruce_fence'))
            t.set(x, by + 4, z, log(BEAM, 'x'))
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if t.get(x, by, z) is None:
                t.set(x, by, z, fence('spruce_fence'))
            t.set(x, by + 4, z, log(BEAM, 'z'))
    t.fill(x0 + 1, by, z0 + 1, x1 - 1, by + 3, z1 - 1, AIR)
    # bell under the roof, hanging from a beam
    t.fill(x0 + 1, by + 4, 6, x1 - 1, by + 4, 6, log(BEAM, 'x'))
    t.set(6, by + 3, 6, S('bell', attachment='ceiling', facing='north', powered=False))
    # roof: hip roof of dark oak with an overhang, then a slim spire with a lantern
    shapes.hip_roof(t, x0 - 1, z0 - 1, x1 + 1, z1 + 1, by + 5, ROOF)
    for y in range(by + 5, by + 10):
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
    t.set(6, TY - 4, 6, log(BEAM))
    t.set(6, TY - 3, 6, fence('spruce_fence'))
    t.set(6, TY - 2, 6, lantern(False))
    # lanterns in the belfry, at the landings
    t.set(x0 + 1, by, z1 - 1, lantern(False))
    t.set(x1 - 1, by, z0 + 1, lantern(False))
    hang_lantern(t, 6, 20, 6, 24)
    t.fill(6, 24, 6, 6, 24, 6, log(BEAM, 'x'))
    t.fill(x0 + 1, 24, 6, x1 - 1, 24, 6, log(BEAM, 'x'))
    hang_lantern(t, 6, 12, 6, 14)
    t.fill(x0 + 1, 14, 6, x1 - 1, 14, 6, log(BEAM, 'x'))
    # banners off the belfry
    banner(t, 6, by + 2, z1 + 1, 'south', 'green')
    banner(t, x1 + 1, by + 2, 6, 'east', 'green')
    # roots and ivy on the old shaft
    nature.overgrow(t, region=((x0, FEET, z0), (x1, 26, z1)), vines=0.25, moss=0.0, roots=0.0, vine_max=6)


# ---------------------------------------------------------------- the Keeper's Hall

HALL_C = (45, 19)
HALL_R = 10


def keepers_hall(t):
    """The enclave's hall: a rotunda of watch stone under a verdigris dome, the dome broken open on the
    north-east and patched with timber and canvas, a portico on the south. Inside: the Watch's worn benches,
    archive niches, a plinth with a small kyber crystal under the oculus, light falling through the break."""
    cx, cz = HALL_C
    R = HALL_R
    ash = pick(t, 'ashlar')
    old = pick(t, 'old')
    flag = M.patches('flag', seed=SEED + 31, scale=2.5)
    copper = Palette(c=[('minecraft:waxed_oxidized_cut_copper', 7), ('minecraft:waxed_weathered_cut_copper', 3),
                        ('minecraft:waxed_oxidized_copper', 1)]).patches('c', seed=SEED + 34, scale=2.2)
    n = Noise(SEED + 33, 3.5)
    floor_y = FEET          # the hall floor is one step up from the forecourt: floor block y 3, feet 4
    feet = floor_y + 1
    drum_top = 13
    base = 14               # the cornice; the dome springs from it
    DR, DH = R - 0.5, 11.0  # the dome's radius and height

    def broken(x, y, z):
        """The north-east break in the dome."""
        ang = math.atan2(z - cz, x - cx)
        lo = -1.85 + 0.25 * n.at(x, 0, z)
        hi = -0.70 - 0.25 * n.at(0, y, z)
        return lo < ang < hi and y >= base + 2 + int(3 * n.at(x, y, 0))

    # the drum: floor, a 2-thick wall, air inside
    for x in range(cx - R - 1, cx + R + 2):
        for z in range(cz - R - 1, cz + R + 2):
            d = math.hypot(x - cx, z - cz)
            if d > R + 0.5:
                continue
            for y in range(0, floor_y):
                t.set(x, y, z, pick(t, 'plinth')(x, y, z))
            t.set(x, floor_y, z, flag(x, floor_y, z))
            for y in range(feet, drum_top + 1):
                if d > R - 1.5:
                    t.set(x, y, z, (old if y < feet + 2 or n.at(x, y, z) > 0.62 else ash)(x, y, z))
                else:
                    t.set(x, y, z, AIR)
            # the cornice course
            t.set(x, base, z, 'minecraft:polished_andesite' if d > R - 1.5 else AIR)
    # a cornice lip one cell proud of the drum
    for x in range(cx - R - 2, cx + R + 3):
        for z in range(cz - R - 2, cz + R + 3):
            d = math.hypot(x - cx, z - cz)
            if R + 0.5 < d <= R + 1.5 and t.get(x, base, z) is None:
                t.set(x, base, z, slab('polished_andesite_slab', 'top'))
    # pilasters between the windows, lancet windows in the drum
    for k in range(8):
        a = k * math.pi / 4
        x = int(round(cx + math.cos(a) * (R + 0.6)))
        z = int(round(cz + math.sin(a) * (R + 0.6)))
        for y in range(feet, base):
            t.set(x, y, z, 'minecraft:polished_andesite' if y % 4 else 'minecraft:chiseled_stone_bricks')
        t.set(x, GROUND, z, 'minecraft:polished_andesite')
        t.set(x, FEET, z, 'minecraft:polished_andesite')
        wa = a + math.pi / 8
        if math.sin(wa) > 0.9:
            continue   # the door is there
        for rr in (R - 1.2, R - 0.6, R, R + 0.3):
            wx = int(round(cx + math.cos(wa) * rr))
            wz = int(round(cz + math.sin(wa) * rr))
            for y in range(feet + 2, feet + 6):
                t.set(wx, y, wz, AIR)
            if rr >= R:
                t.set(wx, feet + 1, wz, slab(STEP_SLAB, 'top'))
    # the dome: a verdigris shell with eight stone ribs, an oculus at the crown, broken on the north-east
    for y in range(base + 1, base + int(DH) + 1):
        for x in range(cx - R, cx + R + 1):
            for z in range(cz - R, cz + R + 1):
                fo = ((x - cx) / DR) ** 2 + ((y - base) / DH) ** 2 + ((z - cz) / DR) ** 2
                if fo > 1.0:
                    continue
                fi = ((x - cx) / (DR - 1.3)) ** 2 + ((y - base) / (DH - 1.3)) ** 2 + ((z - cz) / (DR - 1.3)) ** 2
                if math.hypot(x - cx, z - cz) < 1.6:
                    t.set(x, y, z, AIR)           # the oculus
                    continue
                if fi <= 1.0:
                    t.set(x, y, z, AIR)
                    continue
                if broken(x, y, z):
                    t.set(x, y, z, AIR)
                    continue
                ang = math.atan2(z - cz, x - cx)
                rib = min(abs(((ang - k * math.pi / 4) + math.pi) % (2 * math.pi) - math.pi) for k in range(8))
                dist = math.hypot(x - cx, z - cz)
                t.set(x, y, z, 'minecraft:polished_andesite' if rib * max(dist, 1.0) < 0.55 else copper(x, y, z))
    top = base + int(DH)
    shapes.disk(t, cx, top, cz, 2.3, 'minecraft:polished_andesite', hollow=True, thick=1)
    for (x, z) in ((cx - 2, cz), (cx + 2, cz), (cx, cz - 2), (cx, cz + 2)):
        t.set(x, top + 1, z, wall_block(STONE_WALL))
    # the timber repair over the break: rafters along the dome line and a canvas tarp on the lower part
    for k in range(6):
        a = -1.8 + k * 0.22
        for s in range(0, 40):
            f = s / 39.0
            phi = f * math.pi / 2 * 0.92
            x = int(round(cx + math.cos(a) * DR * math.cos(phi)))
            z = int(round(cz + math.sin(a) * DR * math.cos(phi)))
            y = base + int(round(DH * math.sin(phi)))
            if broken(x, y, z) and t.is_air(x, y, z):
                t.set(x, y, z, log('stripped_spruce_log', 'y' if phi > 0.9 else ('x' if abs(math.cos(a)) > 0.7 else 'z')))
                if k in (1, 2, 3) and phi < 0.75 and t.is_air(x, y + 1, z):
                    t.set(x, y + 1, z, 'oldrepublic:canvas')
    # copper sheets and stone fallen inside and out under the break
    for (x, z) in ((cx + 4, cz - 6), (cx + 5, cz - 5), (cx + 6, cz - 4), (cx + 3, cz - 7), (cx + 6, cz - 6)):
        t.set(x, feet, z, t.rng.choice([slab('waxed_oxidized_cut_copper_slab'), 'minecraft:mossy_cobblestone',
                                         'minecraft:moss_carpet', slab('mossy_stone_brick_slab')]))
    for (x, z) in ((cx + 9, cz - 9), (cx + 10, cz - 8), (cx + 8, cz - 10), (cx + 11, cz - 6)):
        t.set(x, FEET, z, t.rng.choice([slab('waxed_oxidized_cut_copper_slab'), S('waxed_oxidized_cut_copper_stairs',
                                         facing='north', half='bottom', shape='straight', waterlogged=False)]))
    # a timber scaffold against the broken side, outside
    for (sx, sz) in ((53, 9), (56, 13), (50, 7)):
        for y in range(FEET, 17):
            t.set(sx, y, sz, fence('spruce_fence'))
        for y in (8, 12, 16):
            for dx in (-1, 0, 1):
                if t.is_air(sx + dx, y, sz):
                    t.set(sx + dx, y, sz, slab('spruce_slab', 'top'))
    t.set(53, 17, 9, lantern(False))
    # the portico: four columns and a pediment in front of the south door
    pz0, pz1 = cz + R - 1, cz + R + 3
    for x in range(cx - 5, cx + 6):
        for z in range(pz0, pz1 + 1):
            t.set(x, GROUND, z, flag(x, GROUND, z))
            if z >= cz + R:
                t.set(x, FEET, z, stairs(STEP, 'north') if z == pz1 else flag(x, FEET, z))
                for y in range(feet, 10):
                    t.set(x, y, z, AIR)
    for x in (cx - 5, cx - 2, cx + 2, cx + 5):
        for y in range(feet, 9):
            t.set(x, y, pz1 - 1, 'minecraft:polished_andesite' if y not in (feet, 8) else 'minecraft:chiseled_stone_bricks')
    for x in range(cx - 6, cx + 7):
        for z in range(pz0 + 1, pz1 + 1):
            t.set(x, 9, z, ash(x, 9, z))
        t.set(x, 10, pz1, stairs(STEP, 'north'))
        t.set(x, 10, pz0 + 1, ash(x, 10, pz0 + 1))
    for x in range(cx - 4, cx + 5):
        t.set(x, 11, pz0 + 1, stairs(STEP, 'south') if abs(x - cx) > 2 else ash(x, 11, pz0 + 1))
        if abs(x - cx) <= 2:
            t.set(x, 11, pz1, stairs(STEP, 'north'))
            t.set(x, 12, pz0 + 1, slab(STEP_SLAB))
    t.fill(cx - 4, 11, pz1 - 1, cx + 4, 11, pz0 + 2, AIR)
    # the doorway into the rotunda: 3 wide, 5 high, round-headed
    for z in range(cz + R - 2, cz + R + 1):
        opening(t, cx - 1, feet, z, cx + 1, feet + 4, z)
        t.set(cx - 2, feet + 4, z, stairs(STEP, 'east', 'top'))
        t.set(cx + 2, feet + 4, z, stairs(STEP, 'west', 'top'))
    # steps from the forecourt up to the portico floor
    for x in range(cx - 5, cx + 6):
        t.set(x, FEET, pz1 + 1, AIR)
    # inside: worn benches in two arcs, archive niches, the plinth and kyber
    for k in range(-5, 6):
        for rr, face in ((5.5, None), (7.5, None)):
            a = math.pi / 2 + k * 0.23
            x = int(round(cx + math.cos(a) * rr))
            z = int(round(cz - math.sin(a) * rr))
            if abs(k) <= 1:
                continue
            dx, dz = cx - x, cz - z
            facing = ('south' if dz < 0 else 'north') if abs(dz) >= abs(dx) else ('east' if dx < 0 else 'west')
            if t.is_air(x, feet, z):
                t.set(x, feet, z, stairs('spruce_stairs', B.opposite(facing)))
    for k in range(-5, 6):
        a = -math.pi / 2 + k * 0.23
        for rr in (5.5, 7.5):
            x = int(round(cx + math.cos(a) * rr))
            z = int(round(cz - math.sin(a) * rr))
            dx, dz = cx - x, cz - z
            facing = ('south' if dz < 0 else 'north') if abs(dz) >= abs(dx) else ('east' if dx < 0 else 'west')
            if t.is_air(x, feet, z) and abs(k) > 1:
                t.set(x, feet, z, stairs('spruce_stairs', B.opposite(facing)))
    # the plinth: three worn steps and a kyber cluster under the oculus
    shapes.disk(t, cx, feet, cz, 1.6, slab(STEP_SLAB))
    t.set(cx, feet, cz, 'minecraft:chiseled_stone_bricks')
    t.set(cx, feet + 1, cz, S('oldrepublic:kyber_cluster', facing='up'))
    # niches in the wall: bookshelves, lecterns, candles
    for k, a in enumerate((math.pi * 0.95, math.pi * 1.2, -math.pi * 0.05, math.pi * 0.2, math.pi * 0.62, math.pi * 0.38)):
        x = int(round(cx + math.cos(a) * (R - 1.4)))
        z = int(round(cz + math.sin(a) * (R - 1.4)))
        if math.sin(a) > 0.85:
            continue
        for y in (feet, feet + 1, feet + 2):
            if not broken(x, y, z):
                t.set(x, y, z, 'minecraft:bookshelf' if y < feet + 2 else 'minecraft:chiseled_stone_bricks')
        ix = int(round(cx + math.cos(a) * (R - 2.6)))
        iz = int(round(cz + math.sin(a) * (R - 2.6)))
        dx, dz = x - ix, z - iz
        face = ('south' if dz > 0 else 'north') if abs(dz) >= abs(dx) else ('east' if dx > 0 else 'west')
        if k % 2 == 0 and t.is_air(ix, feet, iz):
            mod.lectern(t, ix, feet, iz, facing=B.opposite(face))
        elif t.is_air(ix, feet, iz):
            t.set(ix, feet, iz, candle(3, 'white'))
    # moss and roots on the old floor, rubble under the break
    # lanterns hang on long chains from the dome's inner face
    for (x, z) in ((cx - 5, cz - 2), (cx + 5, cz + 2), (cx - 4, cz + 5), (cx + 4, cz + 5), (cx - 2, cz - 5)):
        hang_lantern(t, x, feet + 5, z, feet + 6)
        y = feet + 6
        while t.is_air(x, y, z) and y < base + int(DH):
            t.set(x, y, z, chain())
            y += 1
    nature.overgrow(t, region=((cx - R - 1, feet, cz - R - 1), (cx + R + 1, drum_top, cz + R + 1)),
                    vines=0.2, moss=0.12, roots=0.08, vine_max=5)
    # Watch banners each side of the door, inside and out
    banner(t, cx - 3, feet + 3, cz + R + 1, 'south', 'green')
    banner(t, cx + 3, feet + 3, cz + R + 1, 'south', 'green')
    banner(t, cx - 3, feet + 3, cz + R - 2, 'north', 'white')
    banner(t, cx + 3, feet + 3, cz + R - 2, 'north', 'white')
    mod.light_fill(t, region=(cx - R + 1, feet, cz - R + 1, cx + R - 1, feet + 2, cz + R + 3))


def forecourt(t):
    """The paved court before the hall where Keeper Ansa meets those who land, with two old standing stones
    and a pair of young silver trees."""
    cx, cz = HALL_C
    flag = M.patches('flag', seed=SEED + 41, scale=2.5)
    for x in range(34, 57):
        for z in range(cz + HALL_R + 4, 37):
            if (x - 45) ** 2 / 140.0 + (z - 36) ** 2 / 60.0 <= 1.3:
                t.set(x, GROUND, z, flag(x, GROUND, z))
    # standing stones (worn, quiet, no marks)
    for (x, z) in ((38, 33), (52, 33)):
        for y in range(FEET, FEET + 4):
            t.set(x, y, z, 'minecraft:polished_andesite' if y < FEET + 3 else 'minecraft:andesite_wall')
        t.set(x + (1 if x < 45 else -1), FEET, z, 'minecraft:mossy_cobblestone')
    for (x, z) in ((36, 31), (54, 31)):
        silver_tree(t, x, FEET, z, 7, t.rng, crown=2.6)
    mod.hub_spawner(t, *HOST, 'host', anchor='HOST')
    lamp_post(t, 41, FEET, 36)
    lamp_post(t, 49, FEET, 36)


# ---------------------------------------------------------------- the cloister and the great silver tree

def cloister(t):
    """The apprentices' cloister: an arcaded walk round a mossy garth, the great silver tree in the middle,
    cells for the apprentices along the north and east sides."""
    x0, z0, x1, z1 = 62, 6, 84, 28
    ash = pick(t, 'ashlar')
    old = pick(t, 'old')
    flag = M.patches('flag', seed=SEED + 51, scale=2.0)
    # outer walls 1 thick, 6 high, with a lean-to roof over the walk
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            inner = x0 + 4 <= x <= x1 - 4 and z0 + 4 <= z <= z1 - 4
            t.set(x, GROUND, z, flag(x, GROUND, z) if not inner else t.get(x, GROUND, z))
            if edge:
                for y in range(0, GROUND):
                    t.set(x, y, z, pick(t, 'plinth')(x, y, z))
                for y in range(FEET, 9):
                    t.set(x, y, z, (old if y < 5 else ash)(x, y, z))
            elif not inner:
                for y in range(FEET, 8):
                    t.set(x, y, z, AIR)
    # the garth: moss, ferns, earth
    for x in range(x0 + 4, x1 - 3):
        for z in range(z0 + 4, z1 - 3):
            t.set(x, GROUND, z, t.rng.choice([grass(), grass(), 'minecraft:moss_block', S('podzol', snowy=False)]))
    # arcade: piers every 3 cells with round-ish heads, around the garth
    ax0, az0, ax1, az1 = x0 + 3, z0 + 3, x1 - 3, z1 - 3
    for x in range(ax0, ax1 + 1):
        for z in range(az0, az1 + 1):
            if not (x in (ax0, ax1) or z in (az0, az1)):
                continue
            pier = ((x - ax0) % 3 == 0 and z in (az0, az1)) or ((z - az0) % 3 == 0 and x in (ax0, ax1))
            t.set(x, GROUND, z, 'minecraft:polished_andesite')
            if pier:
                for y in range(FEET, 7):
                    t.set(x, y, z, 'minecraft:stone_bricks' if y < 6 else 'minecraft:chiseled_stone_bricks')
            else:
                t.set(x, 6, z, stairs(STEP, 'north', 'top') if z == az0 else stairs(STEP, 'south', 'top') if z == az1
                      else stairs(STEP, 'west', 'top') if x == ax0 else stairs(STEP, 'east', 'top'))
                t.set(x, FEET, z, AIR)
            t.set(x, 7, z, ash(x, 7, z))
    # the walk's roof: a dark oak lean-to from the outer wall down to the arcade
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x0 + 1 <= x <= x1 - 1 and z0 + 1 <= z <= z1 - 1 and not (ax0 < x < ax1 and az0 < z < az1):
                dist = min(x - x0, x1 - x, z - z0, z1 - z)
                y = 10 - dist
                if dist <= 3:
                    face = None
                    dn, ds, dw, de = z - z0, z1 - z, x - x0, x1 - x
                    m = min(dn, ds, dw, de)
                    face = 'north' if m == dn else 'south' if m == ds else 'west' if m == dw else 'east'
                    t.set(x, y, z, stairs(ROOF, B.opposite(face)) if dist > 0 else ash(x, y, z))
                    for yy in range(FEET, y):
                        if t.get(x, yy, z) is None:
                            t.set(x, yy, z, AIR)
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            t.set(x, 10, z, stairs(ROOF, 'south' if z == z0 else 'north'))
            t.set(x, 9, z, ash(x, 9, z))
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            t.set(x, 10, z, stairs(ROOF, 'east' if x == x0 else 'west'))
            t.set(x, 9, z, ash(x, 9, z))
    # cells along the north and east walls (behind the walk): beds, desks, candles
    for i, cx in enumerate((66, 72, 78)):
        # north cells: z 1..? are outside (the curtain wall is at z 2..3); cells sit in the walk's back
        bed(t, cx, FEET, z0 + 1, 'east', 'green')
        t.set(cx + 2, FEET, z0 + 1, barrel('up'))
        t.set(cx + 2, FEET + 1, z0 + 1, candle(2, 'white'))
    for i, cz in enumerate((11, 17, 23)):
        bed(t, x1 - 1, FEET, cz, 'south', 'white')
        mod.lectern(t, x1 - 1, FEET, cz + 2, facing='west')
    # doorways: west side into the north lane, south side toward the Healing House
    opening(t, x0, FEET, 15, x0, FEET + 2, 17)
    opening(t, 72, FEET, z1, 74, FEET + 2, z1)
    # the great silver tree in the garth
    silver_tree(t, 72, FEET, 16, 19, t.rng, wide=True, crown=7.5)
    # a ring of stones at the tree's foot where the apprentices sit, and their spawners
    for (x, z) in ((69, 14), (76, 19), (70, 20), (75, 13)):
        t.set(x, FEET, z, stairs(STEP, 'north') if z < 17 else stairs(STEP, 'south'))
    mod.hub_spawner(t, 68, FEET, 18, 'apprentice', anchor='RESIDENT_1')
    mod.hub_spawner(t, 77, FEET, 16, 'apprentice', anchor='RESIDENT_2')
    for (x, z) in ((ax0 + 1, az0 + 1), (ax1 - 1, az0 + 1), (ax0 + 1, az1 - 1), (ax1 - 1, az1 - 1)):
        pass
    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1), (73, z0 + 1), (x1 - 1, 17),
                   (x0 + 1, 21), (67, z1 - 1)):
        hang_lantern(t, x, 7, z, 9 if min(x - x0, x1 - x, z - z0, z1 - z) <= 1 else 8)
    nature.overgrow(t, region=((x0, FEET, z0), (x1, 10, z1)), vines=0.12, moss=0.18, vine_max=3)
    mod.light_fill(t, region=(x0 + 1, FEET, z0 + 1, x1 - 1, FEET + 1, z1 - 1))


# ---------------------------------------------------------------- houses round the square

def stone_ground_floor(t, x0, z0, x1, z1, top, role='ashlar'):
    """Walls of a stone building from the feet layer to `top`, footings under them, explicit air inside."""
    st = pick(t, role)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                for y in range(0, GROUND + 1):
                    t.set(x, y, z, pick(t, 'plinth')(x, y, z))
                for y in range(FEET, top + 1):
                    t.set(x, y, z, st(x, y, z))
            else:
                for y in range(FEET, top + 1):
                    t.set(x, y, z, AIR)
    # quoins
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(FEET, top + 1, 2):
            t.set(x, y, z, 'minecraft:polished_andesite')


def timber_storey(t, x0, z0, x1, z1, y0, y1, infill='minecraft:spruce_planks'):
    """A timber-framed upper storey: dark oak posts at corners and every 3 cells, a beam on top, infill between."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if not edge:
                for y in range(y0, y1 + 1):
                    t.set(x, y, z, AIR)
                continue
            post = (x in (x0, x1) and (z - z0) % 3 == 0) or (z in (z0, z1) and (x - x0) % 3 == 0) or \
                   (x in (x0, x1) and z in (z0, z1))
            for y in range(y0, y1 + 1):
                if post:
                    t.set(x, y, z, log(BEAM))
                elif y == y1:
                    t.set(x, y, z, log(BEAM, 'x' if z in (z0, z1) else 'z'))
                else:
                    t.set(x, y, z, infill)


def floor_boards(t, x0, z0, x1, z1, y):
    t.fill(x0, y, z0, x1, y, z1, M.patches('floorboards', seed=x0 * 7 + z0, scale=2.0))


def windows_on(t, x0, z0, x1, z1, y, every=3, state=None, sides=('north', 'south', 'east', 'west'), skip=()):
    state = state or pane('glass_pane')
    for x in range(x0 + 2, x1 - 1, every):
        for z, s in ((z0, 'north'), (z1, 'south')):
            if s in sides and (x, z) not in skip:
                t.set(x, y, z, state)
                t.set(x, y + 1, z, state)
    for z in range(z0 + 2, z1 - 1, every):
        for x, s in ((x0, 'west'), (x1, 'east')):
            if s in sides and (x, z) not in skip:
                t.set(x, y, z, state)
                t.set(x, y + 1, z, state)


def quartermaster(t):
    """The Watch quartermaster's store north-west of the hall: a stone ground floor with a counter, shelves
    of crates and barrels, a timber upper floor (the store loft) under a steep roof."""
    x0, z0, x1, z1 = 13, 6, 28, 17
    rng = t.rng
    stone_ground_floor(t, x0, z0, x1, z1, 7)
    floor_boards(t, x0 + 1, z0 + 1, x1 - 1, z1 - 1, GROUND)
    timber_storey(t, x0, z0, x1, z1, 8, 12)
    floor_boards(t, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 8)
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            t.set(x, 8, z, log(BEAM, 'x'))
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            t.set(x, 8, z, log(BEAM, 'z'))
    gable_roof(t, x0, z0, x1, z1, 13, ROOF, ridge='x', overhang=1, gable='minecraft:spruce_planks')
    # doors: south (2 wide) onto the lane, east toward the hall
    opening(t, 19, FEET, z1, 20, FEET + 2, z1)
    door(t, 19, FEET, z1, 'south', hinge='left', open_=True)
    door(t, 20, FEET, z1, 'south', hinge='right', open_=True)
    opening(t, x1, FEET, 10, x1, FEET + 2, 11)
    windows_on(t, x0, z0, x1, z1, FEET + 1, every=4, skip={(19, z1), (20, z1), (x1, 10), (x1, 11)})
    windows_on(t, x0, z0, x1, z1, 10, every=3)
    # the counter across the room with a lift-flap, the quartermaster behind it
    for x in range(15, 26):
        if x not in (19, 20):
            t.set(x, FEET, 12, slab('spruce_slab', 'top') if x % 4 else barrel('north'))
    t.set(20, FEET, 12, trapdoor('spruce_trapdoor', 'south', 'top'))
    mod.hub_spawner(t, 21, FEET, 10, 'vendor:watch_quartermaster', anchor='VENDOR_1')
    # shelves of goods behind the counter
    for x in range(x0 + 1, x1):
        for y in (FEET, FEET + 1, FEET + 2):
            t.set(x, y, z0 + 1, rng.choice(['minecraft:bookshelf', barrel('south'), 'oldrepublic:cargo_crate',
                                             'minecraft:spruce_planks', barrel('up'), S('hay_block', axis='y')]))
    for z in (8, 9, 10):
        t.set(x0 + 1, FEET, z, barrel('east'))
        t.set(x0 + 1, FEET + 1, z, rng.choice([barrel('east'), 'oldrepublic:cargo_crate']))
    # the shop floor: crates, bolts of cloth, sacks
    for (x, z) in ((15, 15), (16, 15), (25, 15), (26, 14), (26, 15)):
        crate_stack(t, x, FEET, z, rng, ['oldrepublic:cargo_crate', barrel('up'), S('hay_block', axis='y')],
                    height=rng.choice((1, 2)))
    t.set(14, FEET, 13, 'minecraft:green_wool')
    t.set(14, FEET + 1, 13, 'minecraft:white_wool')
    t.set(14, FEET, 14, 'minecraft:brown_wool')
    # a stair to the loft along the west wall, rising north, and the hole it climbs through
    for i in range(5):
        z = 15 - i
        t.set(x0 + 1, FEET + i, z, stairs('spruce_stairs', 'north'))
        for k in range(1, 4):
            t.set(x0 + 1, FEET + i + k, z, AIR)
    for z in range(9, 14):
        t.set(x0 + 1, 8, z, AIR)
    t.set(x0 + 1, FEET + 4, 10, slab('spruce_slab', 'top'))
    # the loft: sacks and crates, the quartermaster's bed
    bed(t, 25, 9, 8, 'east', 'brown')
    for (x, z) in ((16, 8), (17, 8), (16, 15), (22, 15), (23, 15)):
        crate_stack(t, x, 9, z, rng, ['oldrepublic:cargo_crate', barrel('up'), S('hay_block', axis='y')], height=1)
    hang_lantern(t, 17, 6, 12, 8)
    hang_lantern(t, 24, 6, 12, 8)
    hang_lantern(t, 20, 11, 11, 13)
    bracket_lamp(t, 21, FEET + 3, z1, 'south')
    banner(t, 18, FEET + 3, z1 + 1, 'south', 'green')
    mod.light_fill(t, region=(x0 + 1, FEET, z0 + 1, x1 - 1, 10, z1 - 1))


def refectory(t):
    """The refectory (the Watch's mess): a long stone hall under a timber roof, two long tables, a hearth in
    the west gable, the kitchen corner, lanterns on chains."""
    x0, z0, x1, z1 = 5, 21, 27, 33
    stone_ground_floor(t, x0, z0, x1, z1, 7)
    t.fill(x0 + 1, GROUND, z0 + 1, x1 - 1, GROUND, z1 - 1, M.patches('flag', seed=SEED + 61, scale=2.0))
    gable_roof(t, x0, z0, x1, z1, 8, ROOF, ridge='x', overhang=1, gable=pick(t, 'ashlar'))
    for x in range(x0 + 3, x1 - 1, 4):
        t.fill(x, 8, z0 + 1, x, 8, z1 - 1, log(BEAM, 'z'))
    # doors: two on the south side, one on the east toward the square
    for dx in (10, 20):
        opening(t, dx, FEET, z1, dx + 1, FEET + 2, z1)
    opening(t, x1, FEET, 26, x1, FEET + 2, 28)
    windows_on(t, x0, z0, x1, z1, FEET + 1, every=3, sides=('north', 'south'),
               skip={(10, z1), (11, z1), (20, z1), (21, z1)})
    # the hearth in the west gable: a cobble chimney breast with a fire
    for y in range(FEET, 14):
        for z in range(25, 30):
            t.set(x0, y, z, 'minecraft:cobblestone' if (y + z) % 3 else 'minecraft:mossy_cobblestone')
    for z in range(25, 30):
        t.set(x0 + 1, FEET + 3, z, stairs('stone_brick_stairs', 'east', 'top'))
    t.set(x0 + 1, FEET, 27, campfire(True, 'east'))
    t.set(x0 + 1, FEET, 26, 'minecraft:cobblestone')
    t.set(x0 + 1, FEET, 28, 'minecraft:cobblestone')
    t.set(x0 + 1, FEET + 1, 27, AIR)
    t.set(x0 + 1, FEET + 2, 27, AIR)
    t.set(x0, 14, 27, wall_block('minecraft:cobblestone_wall'))
    # two long tables with benches
    for tz in (25, 29):
        for x in range(9, 24):
            if x == 16:
                continue
            t.set(x, FEET, tz, slab('spruce_slab', 'top'))
            t.set(x, FEET, tz - 1, stairs('spruce_stairs', 'north'))
            t.set(x, FEET, tz + 1, stairs('spruce_stairs', 'south'))
        for x in (10, 13, 19, 22):
            t.set(x, FEET + 1, tz, candle(1 + (x % 3)))
    # the kitchen corner (east end)
    t.set(x1 - 1, FEET, z0 + 1, S('smoker', facing='west', lit=False))
    t.set(x1 - 2, FEET, z0 + 1, barrel('up'))
    t.set(x1 - 3, FEET, z0 + 1, S('water_cauldron', level=3))
    t.set(x1 - 4, FEET, z0 + 1, slab('spruce_slab', 'top'))
    t.set(x1 - 1, FEET, z0 + 2, barrel('west'))
    t.set(x1 - 1, FEET + 1, z0 + 2, barrel('west'))
    t.set(x1 - 1, FEET, z1 - 1, S('hay_block', axis='y'))
    for x in (9, 14, 19, 24):
        hang_lantern(t, x, 6, 27, 8)
    bracket_lamp(t, 15, FEET + 3, z1, 'south')
    mod.hub_spawner(t, 18, FEET, 27, 'apprentice')
    mod.light_fill(t, region=(x0 + 1, FEET, z0 + 1, x1 - 1, FEET + 2, z1 - 1))


def workshop(t):
    """The Artifice workshop: the trainer's bench room in a stone house, an open forge shed on its east side and
    a yard of seasoning logs."""
    x0, z0, x1, z1 = 5, 37, 19, 46
    stone_ground_floor(t, x0, z0, x1, z1, 6)
    floor_boards(t, x0 + 1, z0 + 1, x1 - 1, z1 - 1, GROUND)
    gable_roof(t, x0, z0, x1, z1, 7, SPRUCE_ROOF, ridge='x', overhang=1, gable='minecraft:spruce_planks')
    opening(t, 12, FEET, z1, 13, FEET + 2, z1)
    opening(t, x1, FEET, 41, x1, FEET + 2, 42)
    windows_on(t, x0, z0, x1, z1, FEET + 1, every=4, skip={(12, z1), (13, z1), (x1, 41), (x1, 42)})
    # benches along the north wall
    t.set(7, FEET, z0 + 1, S('oldrepublic:engineering_bench', facing='south'))
    t.set(8, FEET, z0 + 1, 'minecraft:smithing_table')
    t.set(9, FEET, z0 + 1, S('stonecutter', facing='south'))
    t.set(10, FEET, z0 + 1, S('grindstone', face='floor', facing='south'))
    t.set(11, FEET, z0 + 1, 'minecraft:fletching_table')
    t.set(13, FEET, z0 + 1, slab('spruce_slab', 'top'))
    t.set(14, FEET, z0 + 1, slab('spruce_slab', 'top'))
    t.set(13, FEET + 1, z0 + 1, S('oldrepublic:kyber_cluster', facing='up'))
    t.set(15, FEET, z0 + 1, barrel('south'))
    t.set(16, FEET, z0 + 1, 'oldrepublic:cargo_crate')
    t.set(17, FEET, z0 + 1, 'minecraft:cartography_table')
    t.set(x0 + 1, FEET, 40, 'minecraft:bookshelf')
    t.set(x0 + 1, FEET + 1, 40, 'minecraft:bookshelf')
    t.set(x0 + 1, FEET, 41, 'minecraft:crafting_table')
    t.set(x0 + 1, FEET, 43, S('anvil', facing='east'))
    mod.hub_spawner(t, 10, FEET, 41, 'vendor:trainer_artifice', anchor='VENDOR_3')
    hang_lantern(t, 9, 6, 41, 7)
    hang_lantern(t, 15, 6, 41, 7)
    bracket_lamp(t, 14, FEET + 3, z1, 'south')
    mod.light_fill(t, region=(x0 + 1, FEET, z0 + 1, x1 - 1, FEET + 2, z1 - 1))
    # the forge shed: an open lean-to east of the house
    sx0, sz0, sx1, sz1 = 20, 38, 27, 45
    for (x, z) in ((sx1, sz0), (sx1, sz1), (sx1, 41)):
        for y in range(FEET, 7):
            t.set(x, y, z, log(BEAM))
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            t.set(x, 7 if x < 24 else 6, z, slab('spruce_slab', 'bottom' if x < 24 else 'top'))
            t.set(x, GROUND, z, t.rng.choice(['minecraft:coarse_dirt', 'minecraft:packed_mud', 'minecraft:cobblestone']))
    t.set(20, FEET, 39, S('blast_furnace', facing='east', lit=True))
    t.set(20, FEET + 1, 39, 'minecraft:cobblestone')
    t.set(20, FEET + 2, 39, wall_block('minecraft:cobblestone_wall'))
    t.set(21, FEET, 39, S('anvil', facing='south'))
    t.set(22, FEET, 44, S('water_cauldron', level=3))
    for (x, z) in ((25, 39), (26, 39), (25, 40)):
        t.set(x, FEET, z, log('spruce_log', 'z'))
    t.set(26, FEET, 44, S('hay_block', axis='y'))
    hang_lantern(t, 23, 5, 42, 7)


def healing_house(t):
    """The Healing House: the Medtech trainer's ward. A stone L round a small herb court: the ward with four
    beds in the north range, the still-room and stores in the east range."""
    x0, z0, x1, z1 = 64, 31, 85, 45
    stone_ground_floor(t, x0, z0, x1, 37, 7)
    floor_boards(t, x0 + 1, z0 + 1, x1 - 1, 36, GROUND)
    gable_roof(t, x0, z0, x1, 37, 8, ROOF, ridge='x', overhang=1, gable=pick(t, 'ashlar'))
    stone_ground_floor(t, 79, 37, x1, z1, 6)
    t.fill(80, GROUND, 38, 84, GROUND, 44, M.patches('flag', seed=SEED + 71))
    gable_roof(t, 79, 38, x1, z1, 7, ROOF, ridge='z', overhang=1, gable=pick(t, 'ashlar'))
    t.fill(80, FEET, 37, 84, FEET + 2, 37, AIR)   # the two ranges open into each other
    # the herb court: a low wall, beds of herbs, a well
    for x in range(x0, 79):
        for z in range(38, z1 + 1):
            if x == x0 or z == z1:
                t.set(x, FEET, z, pick(t, 'ashlar')(x, FEET, z))
                t.set(x, FEET + 1, z, wall_block(MOSSY_WALL if (x + z) % 2 else STONE_WALL))
            elif (x + z) % 4 == 0:
                t.set(x, GROUND, z, 'minecraft:rooted_dirt')
            else:
                t.set(x, GROUND, z, grass())
    for (x, z) in ((67, 40), (68, 40), (67, 41), (70, 43), (71, 43), (74, 40), (75, 41), (76, 40), (75, 43)):
        t.set(x, GROUND, z, grass())
        t.set(x, FEET, z, t.rng.choice(['minecraft:fern', 'minecraft:lily_of_the_valley', 'minecraft:azure_bluet',
                                        'oldrepublic:silverleaf', S('sweet_berry_bush', age=1)]))
    shapes.disk(t, 72, FEET, 41, 1.3, pick(t, 'ashlar'))
    t.set(72, FEET, 41, 'minecraft:water')
    t.set(72, GROUND, 41, 'minecraft:water')
    t.set(72, 1, 41, 'minecraft:mossy_cobblestone')
    for (x, z) in ((71, 40), (73, 42)):
        for y in range(FEET + 1, FEET + 3):
            t.set(x, y, z, fence('spruce_fence'))
    t.fill(71, FEET + 3, 40, 73, FEET + 3, 42, slab('spruce_slab'))
    # gates and doors: the court from the street (south), the ward from the court and from the square (west)
    t.fill(70, FEET, z1, 71, FEET + 1, z1, AIR)
    opening(t, 71, FEET, 37, 72, FEET + 2, 37)
    opening(t, x0, FEET, 33, x0, FEET + 2, 35)
    windows_on(t, x0, z0, x1, 37, FEET + 1, every=3, sides=('north',))
    windows_on(t, 79, 37, x1, z1, FEET + 1, every=3, sides=('east',))
    # the ward: four beds against the north wall, white curtains between them
    for i, bx in enumerate((67, 71, 75, 79)):
        bed(t, bx, FEET, z0 + 2, 'north', 'white')
        t.set(bx + 1, FEET, z0 + 1, barrel('up'))
        t.set(bx + 1, FEET + 1, z0 + 1, potted(t.rng))
        if i < 3:
            t.set(bx + 2, FEET, z0 + 1, 'minecraft:white_wool')
            t.set(bx + 2, FEET + 1, z0 + 1, 'minecraft:white_wool')
    mod.spot(t, 'MEDBAY', 73, FEET, 34)
    mod.hub_spawner(t, 69, FEET, 35, 'vendor:trainer_medtech', anchor='VENDOR_2')
    # the still-room
    t.set(84, FEET, 39, S('brewing_stand', has_bottle_0=False, has_bottle_1=False, has_bottle_2=False))
    t.set(84, FEET, 40, S('water_cauldron', level=3))
    t.set(84, FEET, 41, 'minecraft:bookshelf')
    t.set(84, FEET + 1, 41, 'minecraft:bookshelf')
    t.set(84, FEET, 42, barrel('west'))
    t.set(84, FEET, 43, barrel('west'))
    t.set(84, FEET + 1, 43, potted(t.rng))
    t.set(80, FEET, 44, S('hay_block', axis='y'))
    t.set(80, FEET, 43, S('composter', level=4))
    for (x, y, z, top) in ((68, 6, 34, 8), (76, 6, 34, 8), (82, 5, 41, 7)):
        hang_lantern(t, x, y, z, top)
    banner(t, x0 - 1, FEET + 3, 32, 'west', 'white')
    banner(t, x0 - 1, FEET + 3, 36, 'west', 'white')
    mod.light_fill(t, region=(x0 + 1, FEET, z0 + 1, x1 - 1, FEET + 2, z1 - 1))


def cottage(t, x0, z0, x1, z1, door_side, seed, colour='green'):
    """A Watch cottage: stone walls, a timber gable, one room with a bed, a table, a stove and shelves."""
    rng = t.rng
    stone_ground_floor(t, x0, z0, x1, z1, 6, role='ashlar' if seed % 2 else 'old')
    floor_boards(t, x0 + 1, z0 + 1, x1 - 1, z1 - 1, GROUND)
    ridge = 'x' if (x1 - x0) >= (z1 - z0) else 'z'
    gable_roof(t, x0, z0, x1, z1, 7, ROOF if seed % 3 else SPRUCE_ROOF, ridge=ridge, overhang=1,
               gable='minecraft:spruce_planks' if seed % 2 else pick(t, 'ashlar'))
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    if door_side in ('north', 'south'):
        dx, dz = mx, (z0 if door_side == 'north' else z1)
        opening(t, dx, FEET, dz, dx + 1, FEET + 2, dz)
        door(t, dx, FEET, dz, door_side, hinge='left', open_=True)
        door(t, dx + 1, FEET, dz, door_side, hinge='right', open_=True)
        doorway = {(dx, dz), (dx + 1, dz)}
        lamp = (dx + 2, FEET + 3, dz, door_side)
    else:
        dx, dz = (x1 if door_side == 'east' else x0), mz
        opening(t, dx, FEET, dz, dx, FEET + 2, dz + 1)
        door(t, dx, FEET, dz, door_side, hinge='left', open_=True)
        door(t, dx, FEET, dz + 1, door_side, hinge='right', open_=True)
        doorway = {(dx, dz), (dx, dz + 1)}
        lamp = (dx, FEET + 3, dz + 2, door_side)
    windows_on(t, x0, z0, x1, z1, FEET + 1, every=3, skip=doorway)
    if door_side in ('north', 'south'):
        back = z1 - 1 if door_side == 'north' else z0 + 1
        bed(t, x0 + 1, FEET, back, 'east', colour)
        t.set(x1 - 1, FEET, back, 'minecraft:bookshelf')
        t.set(x1 - 1, FEET + 1, back, potted(rng))
        t.set(x1 - 2, FEET, back, barrel('up'))
        t.set(x1 - 2, FEET + 1, back, candle(2))
        table(t, mx, FEET, mz, 'spruce_slab')
        chair(t, mx - 1, FEET, mz, 'west')
        chair(t, mx + 2, FEET, mz, 'east')
        t.set(mx + 1, FEET, mz, slab('spruce_slab', 'top'))
        t.set(x0 + 1, FEET, mz, S('furnace', facing='east', lit=False))
    else:
        bx = x0 + 1 if door_side == 'east' else x1 - 1
        bed(t, bx, FEET, z0 + 1, 'south', colour)
        t.set(bx, FEET, z1 - 1, 'minecraft:bookshelf')
        t.set(bx, FEET + 1, z1 - 1, potted(rng))
        t.set(bx, FEET, z1 - 2, barrel('up'))
        table(t, mx, FEET, mz, 'spruce_slab')
        chair(t, mx, FEET, mz - 1, 'north')
        chair(t, mx, FEET, mz + 1, 'south')
        t.set(mx, FEET, z0 + 1, S('furnace', facing='south', lit=False))
    t.set(mx, FEET + 1, mz, candle(1 + seed % 3))
    hang_lantern(t, mx, 5, mz + 1, 7)
    bracket_lamp(t, *lamp)
    mod.light_fill(t, region=(x0 + 1, FEET, z0 + 1, x1 - 1, FEET + 2, z1 - 1))


def spacer_shed(t):
    """The spacer's shed beside the landing field: an open-fronted timber hangar on stone posts, a fuel store
    of barrels, a work bench and parts, the spacer at the counter."""
    x0, z0, x1, z1 = 63, 54, 76, 66
    ash = pick(t, 'ashlar')
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (x0, 60), (x1, 60), (69, z0), (69, z1)):
        for y in range(0, GROUND + 1):
            t.set(x, y, z, pick(t, 'plinth')(x, y, z))
        for y in range(FEET, 9):
            t.set(x, y, z, ash(x, y, z) if y < 5 else log(BEAM))
    for z in range(z0, z1 + 1):
        for y in range(FEET, 9):
            if t.get(x1, y, z) is None:
                t.set(x1, y, z, 'minecraft:spruce_planks' if y != 6 or z % 3 else pane('glass_pane'))
    for x in range(x0 + 5, x1 + 1):
        for y in range(FEET, 6):
            if t.get(x, y, z0) is None:
                t.set(x, y, z0, 'minecraft:spruce_planks')
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, GROUND, z, t.rng.choice(['minecraft:packed_mud', 'minecraft:coarse_dirt', 'minecraft:spruce_planks',
                                              'minecraft:cobblestone']))
            for y in range(FEET, 9):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
    gable_roof(t, x0, z0, x1, z1, 9, SPRUCE_ROOF, ridge='z', overhang=1, gable='minecraft:spruce_planks')
    for z in range(57, 62):
        t.set(66, FEET, z, slab('spruce_slab', 'top') if z != 59 else barrel('west'))
    mod.hub_spawner(t, 68, FEET, 59, 'vendor:spacer', anchor='VENDOR_4')
    for (x, z) in ((74, 55), (75, 55), (74, 56), (75, 56), (75, 57), (74, 65), (75, 65), (75, 64)):
        crate_stack(t, x, FEET, z, t.rng, [barrel('up'), barrel('up'), 'oldrepublic:cargo_crate'],
                    height=t.rng.choice((1, 2)))
    t.set(75, FEET, 60, S('oldrepublic:engineering_bench', facing='west'))
    t.set(75, FEET, 61, 'minecraft:smithing_table')
    t.set(75, FEET, 62, S('grindstone', face='floor', facing='west'))
    t.set(72, FEET, 63, 'oldrepublic:wreck_salvage')
    t.set(71, FEET, 63, 'oldrepublic:debris')
    t.set(72, FEET, 64, S('oldrepublic:durasteel_pipes', axis='x'))
    hang_lantern(t, 69, 8, 59, 10)
    hang_lantern(t, 72, 8, 63, 10)
    hang_lantern(t, 72, 8, 56, 10)
    mod.light_fill(t, region=(x0, FEET, z0, x1, FEET + 2, z1))


# ---------------------------------------------------------------- the market square

def market(t):
    """The market in the old square: stalls of canvas and timber on the west and east sides, the notice post
    (contract terminal) under its little roof, a trough, lamps. The middle stays open: it is the way from the
    landing field to the Keeper."""
    flag = M.patches('terrace', seed=SEED + 81, scale=2.2)
    rng = t.rng
    for x in range(30, 61):
        for z in range(37, 48):
            if (x in (30, 60) or z in (37, 47)) and rng.random() < 0.35:
                continue
            t.set(x, GROUND, z, flag(x, GROUND, z))
    stalls = [(31, 38, 'green'), (31, 43, 'white'), (55, 38, 'brown'), (55, 43, 'green')]
    for (sx, sz, colour) in stalls:
        fx = sx + 4
        for (x, z) in ((sx, sz), (fx, sz), (sx, sz + 3), (fx, sz + 3)):
            for y in range(FEET, FEET + 3):
                t.set(x, y, z, fence('spruce_fence'))
        for x in range(sx, fx + 1):
            for z in range(sz, sz + 4):
                t.set(x, FEET + 3, z, 'oldrepublic:canvas' if (x + z) % 2 else 'minecraft:%s_wool' % colour)
        front = 'east' if sx < 45 else 'west'
        cx = fx if sx < 45 else sx
        for z in range(sz + 1, sz + 3):
            t.set(cx, FEET, z, slab('spruce_slab', 'top') if z % 2 else barrel(front))
        back = sx if sx < 45 else fx
        for z in range(sz + 1, sz + 3):
            crate_stack(t, back, FEET, z, rng, [barrel('up'), 'oldrepublic:cargo_crate', S('hay_block', axis='y'),
                                                'minecraft:melon', 'minecraft:pumpkin'], height=rng.choice((1, 2)))
        t.set(cx, FEET + 1, sz + 1, potted(rng))
        t.set((sx + fx) // 2, FEET + 2, sz + 1, lantern(True))
    # the notice post: the contract terminal under a small timber roof, facing the square
    tx, tz = 50, 38
    t.set(tx, FEET, tz, S('oldrepublic:contract_terminal', facing='south'))
    t.set(tx, FEET + 1, tz, AIR)
    t.anchor('CONTRACT_TERMINAL', tx, FEET, tz)
    for x in (tx - 1, tx + 1):
        for y in range(FEET, FEET + 3):
            t.set(x, y, tz, log(POST))
    for x in range(tx - 2, tx + 3):
        t.set(x, FEET + 3, tz, stairs(ROOF, 'south'))
        t.set(x, FEET + 3, tz - 1, stairs(ROOF, 'north'))
    t.set(tx, FEET + 2, tz, lantern(True))
    # a stone trough and a bench
    for x in range(52, 56):
        t.set(x, GROUND, 45, pick(t, 'ashlar')(x, GROUND, 45))
        t.set(x, FEET, 46, pick(t, 'ashlar')(x, FEET, 46))
        t.set(x, FEET, 45, 'minecraft:water' if 52 < x < 55 else pick(t, 'ashlar')(x, FEET, 45))
        t.set(x, FEET, 44, pick(t, 'ashlar')(x, FEET, 44))
    for x in (34, 35, 36):
        t.set(x, FEET, 47, stairs('spruce_stairs', 'south'))
    for (x, z) in ((37, 37), (53, 37), (37, 47), (53, 47)):
        lamp_post(t, x, FEET, z, height=3)


# ---------------------------------------------------------------- the landing field

def landing_field(t):
    """The landing field: an old parade terrace of grey stone, four bays of 11 x 11 kept flat and open to the
    sky, the field's lamps outside the bays, the pad terminal on the east edge, moss in the joints."""
    joints = Palette(j=[('minecraft:andesite', 3), ('minecraft:mossy_cobblestone', 3), ('minecraft:cobblestone', 2),
                        ('minecraft:moss_block', 2), ('minecraft:tuff', 1)]).patches('j', seed=SEED + 91, scale=2.0)
    x0, z0, x1, z1 = 31, 52, 59, 80
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, GROUND, z, joints(x, GROUND, z))
            t.set(x, 1, z, pick(t, 'plinth')(x, 1, z))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            inbay = any(abs(x - bx) <= BAY_R and abs(z - bz) <= BAY_R for bx, bz in BAYS)
            if not inbay and t.name_at(x, GROUND, z) == 'minecraft:moss_block' and t.rng.random() < 0.5:
                t.set(x, FEET, z, 'minecraft:moss_carpet')
    bay_stone = Palette(p=[('minecraft:polished_andesite', 4), ('minecraft:stone_bricks', 3), ('minecraft:smooth_stone', 2),
                           ('minecraft:cracked_stone_bricks', 1)])
    for i, (bx, bz) in enumerate(BAYS):
        t.fill(bx - BAY_R, FEET, bz - BAY_R, bx + BAY_R, 14, bz + BAY_R, AIR)
        t.fill(bx - BAY_R, GROUND, bz - BAY_R, bx + BAY_R, GROUND, bz + BAY_R, bay_stone.patches('p', seed=SEED + i, scale=2.0))
        mod.spot(t, 'PAD_BAY_%d' % (i + 1), bx, FEET, bz)
    # the field's edge: a low kerb with gaps, lamps at the corners and the middles
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if abs(x - 45) > 2 and t.rng.random() < 0.8:
                t.set(x, FEET, z, slab(STEP_SLAB) if t.rng.random() < 0.7 else slab('mossy_stone_brick_slab'))
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if abs(z - HEART[2]) > 2 and t.rng.random() < 0.8:
                t.set(x, FEET, z, slab(STEP_SLAB) if t.rng.random() < 0.7 else slab('mossy_stone_brick_slab'))
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (45, z0), (45, z1), (x0, HEART[2]), (x1, HEART[2])):
        lamp_post(t, x, FEET, z, height=4, base=pick(t, 'ashlar')(x, FEET, z))
    mod.spot(t, 'HEART', *HEART)
    # the pad terminal on the east edge, facing the field, under a small canopy
    px, pz = 60, 58
    t.set(px, FEET, pz, S('oldrepublic:pad_terminal', facing='west'))
    t.set(px, FEET + 1, pz, AIR)
    t.anchor('PAD_TERMINAL', px, FEET, pz)
    for z in (pz - 1, pz + 1):
        for y in range(FEET, FEET + 3):
            t.set(px + 1, y, z, log(POST))
    for z in range(pz - 1, pz + 2):
        t.set(px, FEET + 3, z, stairs(SPRUCE_ROOF, 'west'))
        t.set(px + 1, FEET + 3, z, slab('spruce_slab', 'top'))
    for (x, z) in ((31, 64), (31, 65), (59, 64), (59, 76), (59, 77)):
        crate_stack(t, x, FEET, z, t.rng, ['oldrepublic:cargo_crate', 'oldrepublic:cargo_crate_republic', barrel('up')],
                    height=t.rng.choice((1, 2)))


# ---------------------------------------------------------------- gardens, trees, lanes, lights

def garden(t):
    """The Watch garden south-west: beds of crops gone half wild, a compost heap, an orchard of young silver
    trees and a bench."""
    x0, z0, x1, z1 = 18, 67, 29, 84
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                if (x + z) % 5:
                    t.set(x, FEET, z, fence('spruce_fence'))
                continue
            if z < 76 and x % 3 != 0:
                t.set(x, GROUND, z, S('farmland', moisture=7))
                t.set(x, FEET, z, t.rng.choice([S('wheat', age=7), S('carrots', age=5), S('potatoes', age=6),
                                                S('beetroots', age=3), 'minecraft:short_grass']))
            elif z < 76:
                t.set(x, GROUND, z, 'minecraft:water' if x % 6 == 0 else 'minecraft:dirt_path')
    t.fill(x0, FEET, 72, x0, FEET, 73, AIR)
    t.set(x1 - 1, FEET, z1 - 2, S('composter', level=6))
    t.set(x1 - 2, FEET, z1 - 2, S('hay_block', axis='y'))
    for (x, z) in ((21, 80), (26, 79)):
        silver_tree(t, x, FEET, z, 6, t.rng, crown=2.4)
    for x in (23, 24):
        t.set(x, FEET, 82, stairs('spruce_stairs', 'south'))


def lanes(t):
    lane(t, [(58, 4), (58, 20), (59, 30), (58, 37)], 4, SEED + 101)            # north gate to the square
    lane(t, [(31, 5), (31, 20), (30, 34), (30, 37)], 3, SEED + 102)            # past the store and refectory
    lane(t, [(10, 18), (31, 19)], 3, SEED + 103)
    lane(t, [(2, 49.5), (30, 49.5), (60, 49.5), (88, 49.5)], 4, SEED + 104, jitter=0.25)   # the street
    lane(t, [(45, 88), (45, 80)], 5, SEED + 105, jitter=0.2)                   # the south road
    lane(t, [(10, 51), (10, 54)], 2, SEED + 106)
    lane(t, [(23, 51), (23, 54)], 2, SEED + 107)
    lane(t, [(62, 60), (65, 60)], 3, SEED + 108)
    lane(t, [(10, 64), (10, 67)], 2, SEED + 109)
    lane(t, [(70, 67), (70, 69)], 2, SEED + 110)
    lane(t, [(82, 51), (82, 54)], 2, SEED + 111)


def town_trees(t):
    """Silver trees the town has let stand, and saplings in the ruins."""
    for (x, z, h, c) in ((33, 22, 9, 3.2), (59, 33, 8, 3.0), (86, 47, 7, 2.6), (5, 48, 7, 2.6), (30, 84, 6, 2.2),
                         (61, 84, 7, 2.6), (86, 66, 6, 2.4), (5, 65, 6, 2.4), (29, 47, 6, 2.2)):
        silver_tree(t, x, FEET, z, h, t.rng, crown=c)
    for (x, z) in ((58, 26), (12, 20), (60, 46)):
        t.set(x, FEET, z, S('spruce_sapling', stage=0))


def street_lamps(t):
    for (x, z) in ((15, 52), (27, 52), (63, 52), (75, 52), (85, 52), (5, 52), (56, 22), (60, 12), (33, 12),
                   (33, 28), (45, 84), (40, 84), (50, 84)):
        if t.is_air(x, FEET, z):
            lamp_post(t, x, FEET, z, height=3)


def ground_cover(t):
    on = ['minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block']
    nature.scatter(t, ((0, GROUND, 0), (TX - 1, GROUND, TZ - 1)),
                   [('minecraft:short_grass', 30), ('minecraft:fern', 12), ('oldrepublic:silverleaf', 6),
                    ('minecraft:lily_of_the_valley', 2), ('minecraft:azure_bluet', 2), ('minecraft:oxeye_daisy', 1),
                    ('minecraft:moss_carpet', 6), ('minecraft:brown_mushroom', 1), ('minecraft:bush', 3),
                    (S('leaf_litter', facing='north', segment_amount=3), 4)],
                   0.22, rng=t.rng, on=on, max_y=FEET)


def cottages(t):
    cottage(t, 5, 54, 15, 63, 'north', 1, 'green')
    cottage(t, 18, 54, 28, 63, 'north', 2, 'white')
    cottage(t, 5, 67, 15, 76, 'east', 3, 'brown')
    cottage(t, 5, 79, 15, 85, 'east', 4, 'green')
    cottage(t, 78, 54, 85, 63, 'north', 5, 'white')
    cottage(t, 64, 70, 74, 78, 'north', 6, 'green')
    cottage(t, 77, 70, 85, 78, 'north', 7, 'brown')
    # the south-east kitchen garden, kept by an apprentice
    mod.hub_spawner(t, 70, FEET, 82, 'apprentice', anchor='RESIDENT_3')
    for x in range(64, 86):
        for z in range(80, 86):
            if (x + z) % 7 and t.get(x, FEET, z) is None:
                if z in (81, 84):
                    t.set(x, GROUND, z, S('farmland', moisture=7))
                    t.set(x, FEET, z, t.rng.choice([S('carrots', age=7), S('beetroots', age=3), S('potatoes', age=7)]))
                elif z in (82, 83) and x % 5 == 0:
                    t.set(x, GROUND, z, 'minecraft:water')


def extra_people(t):
    """One more apprentice, at the foot of the tower where they keep the bell."""
    mod.hub_spawner(t, 12, FEET, 13, 'apprentice')


# ---------------------------------------------------------------- build

def build_town():
    t = Template(TX, TY, TZ, seed=SEED, name=ID + ' (town)')
    ground(t)
    lanes(t)
    curtain_wall(t)
    for side in ('north', 'south', 'west', 'east'):
        gate(t, side)
    for (x0, z0, h, c) in ((82, 2, 12, 0.6), (2, 82, 11, 0.9), (82, 82, 12, 0.3)):
        corner_tower(t, x0, z0, h, c)
    watch_tower(t)
    keepers_hall(t)
    forecourt(t)
    cloister(t)
    quartermaster(t)
    refectory(t)
    workshop(t)
    healing_house(t)
    market(t)
    landing_field(t)
    spacer_shed(t)
    cottages(t)
    garden(t)
    town_trees(t)
    street_lamps(t)
    extra_people(t)
    nature.weather(t, WEATHER, noise=Noise(SEED + 3, 5.0), patchiness=0.6)
    ground_cover(t)
    return t


def skirt_blocks(t):
    """The Greenwatch's skirt: a mossy stone course at the wall's foot, grass and fern steps, a retaining
    face of old cobble and stone at the edge with ivy on it."""
    n = Noise(SEED + 201, 4.0)
    earth = M.patches('earth', seed=SEED + 202, scale=4.0)
    face_p = Palette(f=[('minecraft:mossy_cobblestone', 4), ('minecraft:cobblestone', 2), ('minecraft:stone_bricks', 2),
                        ('minecraft:mossy_stone_bricks', 3), ('minecraft:andesite', 1)]).patches('f', seed=SEED + 203)

    def top(x, y, z, d):
        if d == 1:
            return face_p(x, y, z) if n.at(x, 0, z) < 0.6 else earth(x, y, z)
        if d == SKIRT:
            return earth(x, y, z) if n.at(x, 3, z) < 0.7 else face_p(x, y, z)
        return earth(x, y, z)
    return top, face_p


def build():
    town = build_town()
    t = Template(SX, SY, SZ, seed=SEED, name=ID)
    t.paste(town, *SHIFT)
    sx0, _, sz0 = SHIFT
    gates = [('north',) + tuple(v + sx0 for v in GATES['north']), ('south',) + tuple(v + sx0 for v in GATES['south']),
             ('west',) + tuple(v + sz0 for v in GATES['west']), ('east',) + tuple(v + sz0 for v in GATES['east'])]
    top, face = skirt_blocks(t)
    skirt(t, SKIRT, HUB_GROUND, top, face, gates=gates, stair='stone_brick_stairs', parapet=MOSSY_WALL,
          stair_base=lambda x, y, z: 'minecraft:stone_bricks')
    # lamps at the foot of each gate's stair, ivy and ferns on the skirt
    for side, a, b in gates:
        for v in (a - 1, b + 1):
            if side == 'north':
                p = (v, 0)
            elif side == 'south':
                p = (v, SZ - 1)
            elif side == 'west':
                p = (0, v)
            else:
                p = (SX - 1, v)
            y = HUB_GROUND - SKIRT + 2
            t.set(p[0], y, p[1], wall_block(MOSSY_WALL))
            t.set(p[0], y + 1, p[1], lantern(False))
    nature.overgrow(t, region=((0, 0, 0), (SX - 1, HUB_GROUND - 1, SZ - 1)), vines=0.25, moss=0.0, vine_max=3)
    band = [B.name_of(s) for s in (grass(), S('podzol', snowy=False))] + ['minecraft:moss_block']
    for (x, z) in [(x, z) for x in range(SX) for z in range(SZ) if 0 < ring_distance(t, SKIRT, x, z)]:
        d = ring_distance(t, SKIRT, x, z)
        y = HUB_GROUND - d
        if t.name_at(x, y, z) in band and t.get(x, y + 1, z) is None and t.rng.random() < 0.3:
            t.set(x, y + 1, z, t.rng.choice(['minecraft:fern', 'minecraft:short_grass', 'minecraft:short_grass',
                                             'minecraft:bush', 'oldrepublic:silverleaf']))
    footings(t, 'minecraft:mossy_cobblestone', top_of=lambda x, z: HUB_GROUND - 1 - ring_distance(t, SKIRT, x, z))
    support_gravity(t)
    return t


def main():
    t = build()
    report(t)
    save(t, ID, preview_name='greenwatch')
    meta.write(ID, buried=HUB_FEET, max_slope=12, clearance=8, support_depth=24, ramp_rings=RAMP_RINGS,
               clear_above=16, terrain='flat', spawn_radius=96)
    return t


if __name__ == '__main__':
    main()
