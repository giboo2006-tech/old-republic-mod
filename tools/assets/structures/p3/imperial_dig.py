"""varkhast/imperial_dig: an Imperial excavation (worldgen; MILESTONE_6 5.5).

The Empire found something under the ash and dug for it: a benched pit cut down through grey ash and banded
red rock to old black stone, a tower crane over it, prefab huts, a tent where the finds were sorted and the
spoil heaped along the edges. Something went wrong. The security droids that guarded the dig still keep it
(site_spawner `security_droids`), and the diggers who did not get away lie where they fell.

Variants (start pool, equal weights):
  door   the pit has laid bare the top of a buried facade; a cutting rig stands at its sealed door, which
         the cutters had begun to open; the crane still holds a carved capstone over the pit.
  head   the pit has laid bare the face of a colossal head lying on its back, a drill rig on its brow; the
         crane's jib has snapped and hangs into the pit, and the bunkhouse has burned.

Run:  python3 tools/assets/structures/p3/imperial_dig.py [--preview DIR] [--only NAME]
"""
import math

import vk
from vk import S, AIR, Template, mod, Mix, Field, stair, slab, STEP, OPP

NAME = 'imperial_dig'
VARIANTS = ('door', 'head')
SIZE = (51, 40, 51)
BURIED = 13
G = 12              # the ground layer
F = 13              # feet on the ground
B1, B2, PF = 10, 7, 4   # feet on the upper bench, on the lower bench and on the pit floor
SEED = 960252
SALT = 960252

# the plan (x 0..50 west to east, z 0..50 north to south; the way in is from the south)
PIT = (19, 9, 43, 37)       # the rim: x0, z0, x1, z1
BENCH = 3                   # bench width
STAIR_X = (30, 32)          # the diggers' stair down the south side (feet F-1 at z 37, PF at z 29)
PATH_X = (11, 12)           # the camp road, south gate to the office
ROAD_Z = (39, 40)           # the spur from the camp road to the stair head
TOWER = (15, 23)            # the crane's mast (3 x 3 around it)
DECK_Y = F + 19             # the slewing deck on the mast
JIB_Y = DECK_Y + 1          # the jib's bottom chords
JIB_END = 38
TROLLEY_X = 31
FACADE_Z = 12               # door variant: the buried facade, x 25..37, y PF..G-1
FACADE_X = (25, 37)
DOOR_X = (30, 32)
HEAD_X = 31                 # head variant: the middle of the face (brow north, chin south)

HUTS = {
    # x0, z0, w, d, door side
    'door': dict(office=(1, 2, 10, 7, 'south'), bunks=(1, 12, 9, 7, 'east'), store=(1, 35, 7, 6, 'east'),
                 pods=(13, 2), spawn=(8, F, 11), remains=[(9, F, 10, 'east'), (16, F, 34, 'south')]),
    'head': dict(office=(1, 36, 10, 7, 'north'), bunks=(1, 2, 9, 7, 'south'), store=(1, 12, 7, 6, 'east'),
                 pods=(13, 42), spawn=(8, F, 33), remains=[(7, F, 10, 'south'), (16, F, 36, 'east'),
                                                          (12, F, 19, 'north')]),
}
TENT = (1, 22, 9, 30)


def build(preview=None, only=None):
    out = []
    for v in VARIANTS:
        if only and v != only:
            continue
        out.append(build_variant(v, preview))
    vk.worldgen(NAME, [(v, 1, SIZE[1]) for v in VARIANTS], ['redrock', 'ashfall'], spacing=40, separation=14,
                salt=SALT, buried=BURIED, support=10, ramp=2)
    vk.loot_table('chests/varkhast/imperial_dig', [
        vk.item('oldrepublic:credit_chit', 3, 8),
        vk.item('oldrepublic:durasteel_plate', 1, 3),
        vk.item('minecraft:gold_nugget', 2, 6),
        vk.item('oldrepublic:medpac', 1, 2, chance=0.5),
        vk.item('minecraft:iron_ingot', 1, 3, chance=0.5),
        vk.item('oldrepublic:grave_silk', 1, 2, chance=0.35),
        vk.item('oldrepublic:duskglass_shard', 1, 1, chance=0.3),
    ])
    return out


def build_variant(v, preview=None):
    tid = 'varkhast/%s/%s' % (NAME, v)
    t = Template(*SIZE, seed=SEED + VARIANTS.index(v) * 29, name=tid)
    rng = t.rng
    level = terrain(t, rng, v)
    if v == 'door':
        facade(t, rng)
    else:
        head(t, rng)
    rim(t, rng, v, level)
    crane(t, rng, v)
    huts = camp(t, rng, v)
    heaps(t, rng, v)
    story(t, rng, v)
    weather(t, rng, v, level)
    lights(t, v, huts)
    t.finish()
    h = HUTS[v]
    goals = [hut_middle(h['office']), hut_middle(h['bunks']), hut_middle(h['store']), (5, F, 26),
             (h['pods'][0] + 1, F, h['pods'][1] + 1), (PATH_X[0], F, 10), (20, B1, 23), (23, B2, 23),
             (31, PF, 30), (40, B1, 23)]
    if v == 'door':
        goals += [(31, PF, 14), (31, F, 10), (26, PF, 20)]
    else:
        goals += [(32, 7, 23), (30, 7, 17), (27, PF, 30)]
    vk.report(t, tid, ground_y=G, walk=((PATH_X[0], F, 50), goals, True))
    t.save(tid)
    if preview:
        vk.preview(t, '%s/%s_%s.png' % (preview, NAME, v), tid, plans=[F, PF + 1])
        vk.preview(t, '%s/%s_%s_cut.png' % (preview, NAME, v), tid + ' (cut)', views=('se', 'sw'),
                   plans=[], cut=[F + 2, B2 + 2])
    return t


def hut_middle(h):
    x0, z0, w, d, _ = h
    return (x0 + w // 2, F, z0 + d // 2)


# ---------------------------------------------------------------- the ground and the pit

def strata_fn(seed):
    """What the pit cuts through: grey ash and tuff over a band of mixed rock over banded red rock."""
    ash = Mix([('minecraft:tuff', 4), ('minecraft:light_gray_terracotta', 2), ('minecraft:smooth_basalt', 1),
               ('minecraft:calcite', 1)], seed, 3.0)
    rock = Mix([('minecraft:cobbled_deepslate', 3), ('minecraft:deepslate', 2), ('minecraft:basalt[axis=y]', 2),
                ('minecraft:andesite', 1)], seed + 1, 3.0)
    red = vk.redrock(seed + 2, base_y=0)
    wob = Field(seed + 3, 6.0)

    def f(x, y, z):
        yy = y + int(round((wob(x, 0, z) - 0.5) * 2.0))
        if yy >= 10:
            return ash(x, y, z)
        if yy >= 7:
            return rock(x, y, z)
        return red(x, y, z)
    return f


def pit_level(x, z, v, n):
    """Feet level of the column: F outside the pit, B1 / B2 on the benches, PF on the floor."""
    x0, z0, x1, z1 = PIT
    if v == 'door':
        fx0, fx1 = FACADE_X
        if fx0 - 1 <= x <= fx1 + 1 and z <= FACADE_Z and (z < FACADE_Z or fx0 <= x <= fx1):
            return F        # the ground over the buried facade
    dx = min(x - x0, x1 - x)
    dz = min(z - z0, z1 - z)
    r = 4.0
    if dx < r and dz < r:
        d = r - math.hypot(r - dx, r - dz)
    else:
        d = min(dx, dz)
    if d >= -0.5 and not (STAIR_X[0] - 1 <= x <= STAIR_X[1] + 1):
        d += (n(x, 0, z) - 0.5) * 1.2
    if d < 0:
        lvl = F
    elif d < BENCH:
        lvl = B1
    elif d < 2 * BENCH:
        lvl = B2
    else:
        lvl = PF
    if v == 'door' and FACADE_X[0] <= x <= FACADE_X[1] and FACADE_Z < z <= FACADE_Z + 3 and lvl != F:
        lvl = PF            # the trench dug down in front of the facade
    return lvl


def terrain(t, rng, v):
    sx, sy, sz = SIZE
    n = Field(SEED + 3, 4.0)
    level = {(x, z): pit_level(x, z, v, n) for x in range(sx) for z in range(sz)}
    # the stair: one step down per cell from the south rim to the floor, through both benches
    steps = {}
    for x in range(STAIR_X[0], STAIR_X[1] + 1):
        for z in range(PIT[3], PIT[1], -1):
            s = F - (PIT[3] + 1 - z)
            if s <= level[(x, z)]:
                break
            steps[(x, z)] = s
    strata = strata_fn(SEED + 20)
    # each bench is floored with the band it was cut down to, so the terraces read as rings from the rim
    floors = {
        F: Mix([('minecraft:coarse_dirt', 6), ('minecraft:gravel', 2), ('minecraft:tuff', 2)], SEED + 21, 7.0),
        B1: Mix([('minecraft:cobbled_deepslate', 3), ('minecraft:gravel', 3), ('minecraft:deepslate', 1),
                 ('minecraft:tuff', 1)], SEED + 22, 3.0),
        B2: Mix([('minecraft:terracotta', 3), ('minecraft:red_sand', 2), ('minecraft:red_terracotta', 2),
                 ('minecraft:coarse_dirt', 1)], SEED + 23, 3.0),
        PF: Mix([('minecraft:red_sand', 4), ('minecraft:orange_terracotta', 2), ('minecraft:red_terracotta', 1),
                 ('minecraft:gravel', 1)], SEED + 24, 3.0),
    }
    for x in range(sx):
        for z in range(sz):
            lvl = level[(x, z)]
            top = lvl - 1
            for y in range(0, top):
                t.set(x, y, z, strata(x, y, z))
            if (x, z) in steps:
                s = steps[(x, z)]
                for y in range(top, s - 1):
                    t.set(x, y, z, strata(x, y, z))
                t.set(x, s - 1, z, stair('or:durasteel_plating_stairs', 'south'))
                lo = s
            else:
                t.set(x, top, z, floors[lvl](x, top, z))
                lo = lvl
            for y in range(lo, F + 4):
                t.set(x, y, z, AIR)
    # posts with chains along the stair, so it reads as built, not dug
    for z in range(PIT[3], PIT[1], -1):
        for x in (STAIR_X[0] - 1, STAIR_X[1] + 1):
            s = steps.get((x + (1 if x < STAIR_X[0] else -1), z))
            if s is None or (PIT[3] - z) % 3 != 0 or level[(x, z)] >= s:
                continue
            for y in range(level[(x, z)] - 1, s - 1):
                t.set(x, y, z, strata(x, y, z))
            t.set(x, s - 1, z, 'or:durasteel_deck')
            t.set(x, s, z, 'or:durasteel_beam[axis=y]')
            t.set(x, s + 1, z, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    return level


# ---------------------------------------------------------------- what the dig found

def facade(t, rng):
    """The top of a tomb facade, laid bare in the pit's north face: pilasters, niches, a frieze and a
    cornice in black stone, its sealed door half cut through."""
    fx0, fx1 = FACADE_X
    z = FACADE_Z
    wall = Mix(vk.TOMB.options('wall'), SEED + 40, 2.5)
    for x in range(fx0, fx1 + 1):
        for y in range(PF, G):
            t.set(x, y, z, wall(x, y, z))
        t.set(x, PF, z, 'minecraft:polished_blackstone_bricks')
        t.set(x, 10, z, 'minecraft:chiseled_polished_blackstone' if x % 2 == 1 else 'or:tomb_bricks')
        t.set(x, 11, z, 'minecraft:polished_blackstone_bricks')
        t.set(x, 11, z + 1, stair('or:tomb_brick_stairs', 'north', 'top'))
        if not (DOOR_X[0] - 1 <= x <= DOOR_X[1] + 1):
            t.set(x, PF, z + 1, stair('or:tomb_brick_stairs', 'north'))
    # the line of the facade runs on under the benches either side
    for x in range(PIT[0], PIT[2] + 1):
        if fx0 <= x <= fx1:
            continue
        for y in range(PF, G):
            if t.is_solid(x, y, z):
                t.set(x, y, z, wall(x, y, z))
    # pilasters, a base and a capital each
    for x in (26, 28, 34, 36):
        t.set(x, PF, z + 1, 'minecraft:polished_blackstone')
        for y in range(PF + 1, 10):
            t.set(x, y, z + 1, 'or:tomb_pillar[axis=y]')
        t.set(x, 10, z + 1, 'minecraft:chiseled_polished_blackstone')
    # niches between the outer pilasters, an urn in each
    for x in (27, 35):
        for y in (6, 7, 8):
            t.set(x, y, z, AIR)
            t.set(x, y, z - 1, 'or:tomb_bricks')
        t.set(x, 5, z, 'minecraft:polished_blackstone_slab[type=top,waterlogged=false]')
        t.set(x, 6, z, 'or:funerary_urn')
        t.set(x, 9, z, 'or:tomb_stone_carved')
    # the door: jambs, a carved lintel under a hood, a gilded keystone; the seal is set back a cell
    for y in range(PF, 9):
        t.set(DOOR_X[0] - 1, y, z, 'minecraft:polished_blackstone')
        t.set(DOOR_X[1] + 1, y, z, 'minecraft:polished_blackstone')
        for x in range(DOOR_X[0], DOOR_X[1] + 1):
            t.set(x, y, z, AIR)
            t.set(x, y, z - 1, 'minecraft:polished_blackstone_bricks' if (x + y) % 3
                  else 'minecraft:cracked_polished_blackstone_bricks')
    for x in range(DOOR_X[0] - 1, DOOR_X[1] + 2):
        t.set(x, 9, z, 'or:tomb_stone_carved')
        t.set(x, 9, z + 1, stair('or:tomb_brick_stairs', 'north', 'top'))
    t.set(HEAD_X, 10, z, 'minecraft:gilded_blackstone')
    # the cutters' work: a glowing kerf down one side of the seal, and one block already pulled out
    for y in range(PF, PF + 4):
        t.set(DOOR_X[1], y, z - 1, 'minecraft:magma_block')
    t.set(DOOR_X[1], PF + 4, z - 1, 'minecraft:cracked_polished_blackstone_bricks')
    t.set(HEAD_X, PF + 3, z - 1, AIR)
    t.set(HEAD_X, PF + 3, z - 2, AIR)
    t.set(HEAD_X, PF + 3, z - 3, 'minecraft:black_concrete')
    # the old paving the trench uncovered in front of the door
    pave = Mix([('or:tomb_stone', 4), ('minecraft:polished_blackstone_bricks', 2), ('or:tomb_stone_cracked', 2),
                ('minecraft:red_sand', 2)], SEED + 41, 2.0)
    for x in range(fx0 + 1, fx1):
        for zz in range(z + 1, z + 6):
            if not t.is_solid(x, PF, zz) or vk.B.name_of(t.get(x, PF, zz)).endswith('_stairs'):
                t.set(x, PF - 1, zz, pave(x, PF - 1, zz))
    # the cutting rig: a beam frame with a cutter head at the seal, its cable sunk in the trench floor
    for x in (DOOR_X[0] - 1, DOOR_X[1] + 1):
        for y in range(PF, PF + 5):
            t.set(x, y, z + 3, 'or:durasteel_beam[axis=y]')
    for x in range(DOOR_X[0] - 1, DOOR_X[1] + 2):
        t.set(x, PF + 5, z + 3, 'or:durasteel_beam[axis=x]')
    for x in range(DOOR_X[0], DOOR_X[1] + 1):
        t.set(x, PF + 2, z + 3, 'or:durasteel_beam[axis=x]')
    t.set(DOOR_X[1], PF + 2, z + 2, 'minecraft:end_rod[facing=north]')
    t.set(HEAD_X, PF + 5, z + 2, 'or:light_panel')
    for zz in range(z + 4, 29):
        t.set(DOOR_X[1] + 1, PF - 1, zz, 'or:durasteel_pipes[axis=z]')
    # work lights in the trench, aimed at the facade
    vk.flood_pole(t, fx0 + 1, PF, z + 6, h=4, facing='north')
    vk.flood_pole(t, fx1 - 1, PF, z + 6, h=4, facing='north')


def head_tops():
    """{(x, z): (top y, kind)} of the colossal head lying face up on the pit floor: a hooded face, its
    brow north, its chin to the south where the stair comes down. kind: face, hood, circlet, eye, stair."""
    out = {}
    cx = HEAD_X
    # the hood: a two-cell shell around the brow and the sides of the face
    for x in range(cx - 6, cx + 7):
        out[(x, 13)] = (7, 'hood')
        out[(x, 14)] = (8, 'hood')
    for z in range(15, 30):
        for (xo, xi) in ((cx - 6, cx - 5), (cx + 6, cx + 5)):
            drop = 0 if z <= 25 else (1 if z <= 27 else (2 if z == 28 else 3))
            out[(xo, z)] = (7 - drop, 'hood')
            out[(xi, z)] = (8 - drop, 'hood')
    # the face, 9 wide, 13 long
    for x in range(cx - 4, cx + 5):
        for z in range(15, 28):
            out[(x, z)] = (6, 'face')
    for x in range(cx - 4, cx + 5):
        out[(x, 15)] = (7, 'circlet')
        out[(x, 17)] = (7, 'face')                     # the brow
    for z in (18, 19):
        for x in (cx - 3, cx - 2, cx + 2, cx + 3):
            out[(x, z)] = (4, 'eye')                   # the sockets
    for z in range(18, 22):
        out[(cx, z)] = (7, 'face')                     # the bridge of the nose
    for (x, z) in ((cx - 4, 20), (cx - 3, 20), (cx + 3, 20), (cx + 4, 20), (cx - 4, 21), (cx + 4, 21)):
        out[(x, z)] = (7, 'face')                      # the cheekbones
    out[(cx - 1, 21)] = (7, 'face')
    out[(cx + 1, 21)] = (7, 'face')
    out[(cx, 21)] = (8, 'face')                        # the tip of the nose
    out[(cx, 22)] = (7, 'face')
    for x in range(cx - 2, cx + 3):
        out[(x, 24)] = (5, 'face')                     # the line of the mouth
    # the chin and the jaw step down to the floor as stairs, so a person can walk up onto the face
    for x in range(cx - 2, cx + 3):
        out[(x, 26)] = (6, 'stair')
        out[(x, 28)] = (4, 'stair')
    for x in range(cx - 3, cx + 4):
        out[(x, 27)] = (5, 'stair' if abs(x - cx) <= 2 else 'face')
    return out


def head(t, rng):
    # the face in grey basalt, worn smooth; the hood in the black brick of the tombs
    face = Mix([('minecraft:smooth_basalt', 5), ('minecraft:polished_basalt[axis=y]', 2),
                ('minecraft:polished_blackstone', 1)], SEED + 50, 3.0)
    hood = Mix(vk.TOMB.options('wall'), SEED + 51, 2.5)
    core = 'minecraft:blackstone'
    for (x, z), (top, kind) in head_tops().items():
        for y in range(PF - 1, top):
            t.set(x, y, z, core)
        if kind == 'stair':
            t.set(x, top, z, stair('minecraft:polished_blackstone_stairs', 'north'))
        elif kind == 'eye':
            t.set(x, top, z, 'minecraft:magma_block')
            for y in range(top + 1, 7):
                t.set(x, y, z, AIR)
        elif kind == 'circlet':
            t.set(x, top, z, 'minecraft:gilded_blackstone' if x % 2 == 1 else 'minecraft:polished_blackstone_bricks')
        elif kind == 'hood':
            t.set(x, top, z, hood(x, top, z))
        else:
            t.set(x, top, z, face(x, top, z))
    # a crack runs from the brow through the left eye to the cheek: red light from under the face
    tops = head_tops()
    for (x, z) in ((HEAD_X - 2, 15), (HEAD_X - 2, 16), (HEAD_X - 3, 17), (HEAD_X - 4, 20), (HEAD_X - 4, 21),
                   (HEAD_X - 4, 22), (HEAD_X - 3, 23)):
        top = tops[(x, z)][0]
        t.set(x, top, z, 'minecraft:red_stained_glass')
        t.set(x, top - 1, z, 'minecraft:magma_block')
    # the drill rig on the brow: a beam frame and a drill string sunk into the stone
    z = 16
    for x in (HEAD_X - 2, HEAD_X + 2):
        for y in range(7, 10):
            t.set(x, y, z, 'or:durasteel_beam[axis=y]')
    for x in range(HEAD_X - 2, HEAD_X + 3):
        t.set(x, 10, z, 'or:durasteel_beam[axis=x]')
    for y in range(6, 10):
        t.set(HEAD_X, y, z, 'or:durasteel_pipes[axis=y]')
    t.set(HEAD_X, 11, z, 'or:durasteel_vent')
    # floodlights on the lower bench aimed at the face
    vk.flood_pole(t, 23, B2, 20, h=4, facing='east')
    vk.flood_pole(t, 39, B2, 20, h=4, facing='west')


# ---------------------------------------------------------------- the rim: railings and floodlights

def rim(t, rng, v, level):
    gap = {(x, PIT[3] + 1) for x in range(STAIR_X[0] - 1, STAIR_X[1] + 2)}
    cells = []
    for (x, z), lvl in sorted(level.items()):
        if lvl != F or (x, z) in gap:
            continue
        if any(level.get((x + dx, z + dz), F) < F for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            cells.append((x, z))
    n = Field(SEED + 70, 3.0)
    for (x, z) in cells:
        if not vk.is_air_state(t.get(x, F, z)):
            continue
        if v == 'head' and n(x, 0, z) > 0.78:
            continue                    # torn down when the camp was overrun
        if (x + z) % 5 == 0:
            t.set(x, F, z, 'or:durasteel_beam[axis=y]')
            t.set(x, F + 1, z, 'minecraft:polished_deepslate_slab[type=bottom,waterlogged=false]')
        else:
            t.set(x, F, z, 'minecraft:iron_bars')
    # floodlights round the rim, aimed into the pit
    x0, z0, x1, z1 = PIT
    for (x, z, f) in ((x0 - 1, 14, 'east'), (x0 - 1, 32, 'east'), (x1 + 1, 14, 'west'), (x1 + 1, 32, 'west'),
                      (22, z0 - 1, 'south'), (40, z0 - 1, 'south'), (24, z1 + 1, 'north'), (38, z1 + 1, 'north')):
        ox, oz = STEP[OPP[f]]
        while level[(x, z)] != F:
            x, z = x + ox, z + oz
        if v == 'head' and (x, z) == (x1 + 1, 32):
            # one has been knocked flat
            for i in range(5):
                t.set(x, F, z + 2 + i, 'or:durasteel_beam[axis=z]')
            t.set(x, F, z + 7, 'or:light_panel')
            continue
        vk.flood_pole(t, x, F, z, h=6, facing=f)


# ---------------------------------------------------------------- the crane

def crane(t, rng, v):
    tx, tz = TOWER
    beam_y, beam_x, beam_z = 'or:durasteel_beam[axis=y]', 'or:durasteel_beam[axis=x]', 'or:durasteel_beam[axis=z]'
    # the footing: a deck pad with ballast blocks at its corners
    for x in range(tx - 2, tx + 3):
        for z in range(tz - 2, tz + 3):
            t.set(x, G, z, 'minecraft:polished_deepslate')
    for (x, z) in ((tx - 2, tz - 2), (tx + 2, tz - 2), (tx - 2, tz + 2), (tx + 2, tz + 2)):
        t.set(x, F, z, 'or:imperial_plating')
        t.set(x, F + 1, z, slab('or:imperial_plating_slab'))
    # the mast: four corner beams braced every fourth level, bars between
    for y in range(F, DECK_Y):
        k = y - F
        for (x, z) in ((tx - 1, tz - 1), (tx + 1, tz - 1), (tx - 1, tz + 1), (tx + 1, tz + 1)):
            t.set(x, y, z, beam_y)
        for (x, z, b) in ((tx, tz - 1, beam_x), (tx, tz + 1, beam_x), (tx - 1, tz, beam_z), (tx + 1, tz, beam_z)):
            if k % 4 == 0:
                t.set(x, y, z, b)
            elif k % 4 == 2:
                t.set(x, y, z, 'minecraft:iron_bars')
            else:
                t.set(x, y, z, AIR)
        t.set(tx, y, tz, AIR if k else 'or:durasteel_deck')
    # the slewing deck and the operator's cab hung under the jib on the pit side
    for x in range(tx - 1, tx + 2):
        for z in range(tz - 1, tz + 2):
            t.set(x, DECK_Y, z, 'or:durasteel_plating')
    cy = DECK_Y - 4
    for x in (tx + 2, tx + 3):
        for z in range(tz - 1, tz + 2):
            t.set(x, cy, z, 'or:durasteel_deck')
            t.set(x, cy + 3, z, 'or:imperial_plating')
            for y in (cy + 1, cy + 2):
                glass = x == tx + 3 or (x == tx + 2 and z != tz and y == cy + 2)
                t.set(x, y, z, 'minecraft:gray_stained_glass_pane' if glass else 'or:imperial_plating')
    t.set(tx + 2, cy + 1, tz, stair('or:imperial_plating_stairs', 'west'))
    t.set(tx + 2, cy + 2, tz, AIR)
    t.set(tx + 2, cy - 1, tz, 'or:light_panel_red')
    # chords along the whole length (counter-jib west, jib east), a top chord over the jib
    snap = 27 if v == 'head' else None
    end = (snap - 1) if snap else JIB_END
    for x in range(6, end + 1):
        t.set(x, JIB_Y, tz - 1, beam_x)
        t.set(x, JIB_Y, tz + 1, beam_x)
        if (x - tx) % 3 == 0 and x > tx + 1:
            t.set(x, JIB_Y, tz, beam_z)
        if tx - 1 <= x <= min(end, JIB_END - 1):
            t.set(x, JIB_Y + 1, tz, beam_x)
            if (x - tx) % 3 == 1 and x > tx + 1:
                t.set(x, JIB_Y + 1, tz - 1, 'minecraft:iron_bars')
                t.set(x, JIB_Y + 1, tz + 1, 'minecraft:iron_bars')
    for x in range(6, tx - 1):
        t.set(x, JIB_Y, tz, 'or:durasteel_grate')
    # the hoist house and the counterweights on the counter-jib
    for x in range(9, 12):
        for z in range(tz - 1, tz + 2):
            t.set(x, JIB_Y + 1, z, 'or:durasteel_plating')
            t.set(x, JIB_Y + 2, z, 'or:durasteel_vent' if (x + z) % 2 else 'or:imperial_plating')
    for x in range(6, 9):
        for z in range(tz - 1, tz + 2):
            for y in range(JIB_Y - 3, JIB_Y):
                t.set(x, y, z, 'or:imperial_panel' if y == JIB_Y - 2 else 'or:imperial_plating')
    t.set(6, JIB_Y + 1, tz, 'or:light_panel_red')
    if snap is None:
        # the jib's end, its warning light, and the trolley with a carved capstone hanging over the pit
        for z in range(tz - 1, tz + 2):
            t.set(JIB_END, JIB_Y, z, 'or:imperial_plating')
        t.set(JIB_END, JIB_Y + 1, tz, 'or:imperial_plating')
        t.set(JIB_END, JIB_Y + 2, tz, 'or:light_panel_red')
        t.set(TROLLEY_X, JIB_Y, tz, 'or:durasteel_plating')
        for y in range(18, JIB_Y):
            t.set(TROLLEY_X, y, tz, vk.chain('y'))
        for x in range(TROLLEY_X - 1, TROLLEY_X + 2):
            t.set(x, 17, tz, beam_x)
        for x in (TROLLEY_X - 1, TROLLEY_X + 1):
            t.set(x, 16, tz, vk.chain('y'))
        stone = Mix([('or:tomb_stone_carved', 3), ('or:tomb_stone', 1)], SEED + 81, 1.5)
        for x in range(TROLLEY_X - 1, TROLLEY_X + 2):
            for z in range(tz - 1, tz + 2):
                for y in (14, 15):
                    t.set(x, y, z, stone(x, y, z))
        t.set(TROLLEY_X, 15, tz, 'minecraft:chiseled_polished_blackstone')
    else:
        # the jib broke at a joint: its outer half hangs straight down into the pit
        for z in range(tz - 1, tz + 2):
            t.set(snap, JIB_Y, z, 'or:durasteel_plating')
        for y in range(21, JIB_Y):
            t.set(snap, y, tz - 1, beam_y)
            t.set(snap, y, tz + 1, beam_y)
            t.set(snap, y, tz, 'minecraft:iron_bars' if y % 3 == 0 else AIR)
        for z in range(tz - 1, tz + 2):
            t.set(snap, 20, z, 'or:imperial_plating')
        t.set(snap, 19, tz, 'or:light_panel_red')
        # the cab took the blow: its glass is gone and its plating is burned
        for x in (tx + 2, tx + 3):
            for z in range(tz - 1, tz + 2):
                for y in (cy + 1, cy + 2):
                    if 'glass' in (t.get(x, y, z) or ''):
                        t.set(x, y, z, AIR)
        t.set(tx + 3, cy + 3, tz - 1, 'or:wreck_plating_scorched')
        t.set(tx + 2, cy + 3, tz + 1, 'or:wreck_plating_scorched')
    # the hook's cable winds down the mast to a winch at its foot
    t.set(tx + 2, F, tz, 'or:durasteel_vent')
    t.set(tx + 2, F + 1, tz, slab('or:durasteel_plating_slab'))


# ---------------------------------------------------------------- the camp

def camp(t, rng, v):
    spec = HUTS[v]
    path = Mix([('or:durasteel_deck', 3), ('minecraft:gravel', 2), ('minecraft:coarse_dirt', 1),
                ('or:durasteel_grate', 1)], SEED + 60, 2.0)
    for z in range(9, SIZE[2]):
        for x in PATH_X:
            t.set(x, G, z, path(x, G, z))
    for x in range(PATH_X[0], STAIR_X[1] + 2):
        for z in ROAD_Z:
            t.set(x, G, z, path(x, G, z))
    huts = {}
    for name, kind in (('office', 'none'), ('bunks', 'bunks'), ('store', 'store')):
        x0, z0, w, d, side = spec[name]
        box = vk.prefab(t, x0, G, z0, w, d, 4, side, rng, kind=kind)
        huts[name] = box
        # a spur of deck from the door to the camp road
        dx, dz = STEP[side]
        if side in ('north', 'south'):
            dxa = x0 + w // 2 - 1
            zs = (z0 - 2) if side == 'north' else (z0 + d + 1)
            for x in range(dxa, PATH_X[0]):
                for zz in (zs, zs - dz):
                    t.set(x, G, zz, path(x, G, zz))
        else:
            dza = z0 + d // 2 - 1
            for x in range(x0 + w + 1, PATH_X[0]):
                for zz in (dza, dza + 1):
                    t.set(x, G, zz, path(x, G, zz))
    office(t, rng, huts['office'], spec['office'][4], v)
    # banners of the Empire either side of the office door
    x0, z0, w, d, side = spec['office']
    bz = z0 + d if side == 'south' else z0 - 1
    for bx in (x0 + 1, x0 + w - 2):
        mod.banner(t, bx, F + 2, bz, side, base='black')
    if v == 'head':
        burn(t, rng, huts['bunks'], spec['bunks'])
    pods(t, rng, *spec['pods'], v=v)
    tent(t, rng, v)
    generator(t, rng)
    gate(t, rng)
    return huts


def office(t, rng, box, side, v):
    """The overseer's office: a desk and console on the back wall, the dig log on its lectern, the
    finds shelf, the strongbox by the door."""
    x0, y, z0, x1, y1, z1 = box
    back, f = (z0, 'south') if side == 'south' else (z1, 'north')
    front = z1 if side == 'south' else z0
    dz = STEP[f][1]
    t.set(x0, y, back, slab('or:durasteel_plating_slab', 'top'))
    t.set(x0, y + 1, back, S('or:holo_screen', facing=f))
    t.set(x0 + 1, y, back, S('or:console', facing=f))
    t.set(x0 + 2, y, back, slab('or:durasteel_plating_slab', 'top'))
    t.set(x0 + 2, y + 1, back, S('or:holo_screen', facing=f))
    t.set(x0 + 1, y, back + dz, stair('or:imperial_plating_stairs', f))
    t.set(x0 + 4, y, back, slab('or:durasteel_plating_slab', 'top'))
    t.set(x0 + 4, y + 1, back, 'minecraft:decorated_pot')
    t.set(x0 + 5, y, back, slab('or:durasteel_plating_slab', 'top'))
    t.set(x0 + 5, y + 1, back, vk.skull(8 if f == 'south' else 0))
    mod.lectern(t, x1 - 1, y, back, f, book=True)
    t.set(x1, y, back, 'or:cargo_crate_imperial')
    t.set(x1, y + 1, back, 'or:cargo_crate_imperial')
    t.set(x1, y, back + dz, vk.barrel('up'))
    t.set(x0, y, front, 'minecraft:cartography_table')
    t.set(x0, y, front - dz, vk.barrel('up'))
    t.chest(x1, y, front, facing='west', loot='chests/varkhast/imperial_dig')


def burn(t, rng, box, spec):
    """The bunkhouse burned: holes in its roof, scorched and missing plates, its bunks charred."""
    x0, y0, z0, x1, y1, z1 = box
    top = y1 + 1
    n = Field(SEED + 90, 2.5)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if n(x, 0, z) > 0.62 and x0 <= x <= x1 and z0 <= z <= z1:
                t.set(x, top, z, AIR)
            for y in range(y0, top):
                s = t.get(x, y, z)
                if s is None or not (x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)):
                    continue
                name = vk.B.name_of(s)
                if name in ('oldrepublic:imperial_plating', 'oldrepublic:imperial_panel', 'oldrepublic:durasteel_plating'):
                    r = n(x, y * 3, z)
                    if r > 0.8 and y > y0:
                        t.set(x, y, z, AIR)
                    elif r > 0.45:
                        t.set(x, y, z, 'or:wreck_plating_scorched')
    # whatever stood on the roof over a hole has fallen through
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if vk.is_air_state(t.get(x, top, z)):
                for y in (top + 1, top + 2):
                    if t.get(x, y, z) is not None:
                        t.set(x, y, z, AIR)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s = t.get(x, y0, z)
            name = vk.B.name_of(s) if s else ''
            if name.endswith('_bed'):
                t.set(x, y0, z, 'or:debris' if (x + z) % 2 else 'or:scorch_mark')
            elif vk.is_air_state(s) and n(x + 5, 0, z) < 0.55:
                t.set(x, y0, z, 'or:scorch_mark')


def pods(t, rng, x0, z0, v):
    """Two charging pods for the security droids, both empty."""
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 3):
            t.set(x, G, z, 'or:durasteel_deck')
        for y in range(F, F + 3):
            t.set(x, y, z0, 'or:imperial_plating')
        t.set(x, F + 3, z0, 'or:imperial_plating_slab[type=bottom,waterlogged=false]')
        t.set(x, F + 3, z0 + 1, 'or:imperial_plating')
    for x in (x0, x0 + 2, x0 + 4):
        for y in range(F, F + 3):
            t.set(x, y, z0 + 1, 'or:durasteel_beam[axis=y]')
    for x in (x0 + 1, x0 + 3):
        for y in range(F, F + 3):
            t.set(x, y, z0 + 1, AIR)
        t.set(x, F + 1, z0, 'or:durasteel_pipes[axis=y]')
        t.set(x, F + 3, z0 + 1, 'or:light_panel_red')
        t.set(x, F, z0 + 1, 'or:scorch_mark' if x == x0 + 3 else AIR)


def tent(t, rng, v):
    """The finds tent: a crimson awning on beam posts over two long tables of what came out of the pit."""
    x0, z0, x1, z1 = TENT
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            # eaves, two slopes and a ridge, each row stepped onto the next
            dz = min(z - z0, z1 - z)
            for y in {0: (F + 2,), 1: (F + 2, F + 3), 2: (F + 3,), 3: (F + 3, F + 4)}.get(dz, (F + 4,)):
                t.set(x, y, z, 'or:canvas_crimson')
    for x in (x0, (x0 + x1) // 2, x1):
        for z in (z0, z1):
            for y in (F, F + 1):
                t.set(x, y, z, 'or:durasteel_beam[axis=y]')
    for x in (x0 + 2, x1 - 2):
        t.set(x, F + 3, (z0 + z1) // 2, vk.chain('y'))
        t.set(x, F + 2, (z0 + z1) // 2, vk.lantern(True))
    finds = [('minecraft:decorated_pot', 3), (vk.skull(0), 2), ('or:funerary_urn', 2), ('or:tomb_stone_carved', 1),
             ('minecraft:chiseled_polished_blackstone', 1), (vk.candle('black', 2, False), 1), (vk.bone('x'), 1),
             (AIR, 2)]
    for z in (z0 + 2, z1 - 2):
        for x in range(x0 + 2, x1 - 1):
            t.set(x, F, z, slab('or:durasteel_plating_slab', 'top'))
            s = vk.pick(rng, finds)
            if s.startswith('minecraft:skeleton_skull'):
                s = vk.skull(rng.randrange(16))
            t.set(x, F + 1, z, s)
    # crates of packed finds and a sieve frame along the back
    for z in range(z0 + 1, z1):
        if z in (z0 + 2, z1 - 2):
            continue
        r = rng.random()
        if r < 0.5:
            t.set(x0, F, z, vk.pick(rng, vk.IMP.options('crate')))
        elif r < 0.7:
            t.set(x0, F, z, vk.barrel('up'))
    t.set(x1 - 1, F, (z0 + z1) // 2, vk.trapdoor('minecraft:iron_trapdoor', 'north', 'top'))


def generator(t, rng):
    tx, tz = TOWER
    gx0, gz0 = tx - 1, tz + 5
    for x in range(gx0 - 1, gx0 + 4):
        for z in range(gz0 - 1, gz0 + 4):
            t.set(x, G, z, 'or:durasteel_deck')
    for x in range(gx0, gx0 + 3):
        for z in range(gz0, gz0 + 3):
            t.set(x, F, z, 'or:durasteel_plating')
            t.set(x, F + 1, z, 'or:durasteel_vent' if (x + z) % 2 == 0 else 'or:durasteel_plating')
    t.set(gx0 - 1, F + 1, gz0 + 1, 'or:light_panel_amber')
    t.set(gx0 + 1, F + 2, gz0 + 1, 'or:durasteel_pipes[axis=y]')
    t.set(gx0 + 1, F + 3, gz0 + 1, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    for z in range(gz0 - 2, gz0):
        t.set(gx0 + 1, G, z, 'or:durasteel_pipes[axis=z]')
    for z in range(gz0, gz0 + 3):
        t.set(gx0 + 3, F, z, vk.barrel('up'))
    t.set(gx0 + 3, F + 1, gz0 + 1, vk.barrel('up'))


def gate(t, rng):
    """Where the camp road comes in: two posts and a beam, red lights on the posts."""
    z = SIZE[2] - 3
    xa, xb = PATH_X[0] - 2, PATH_X[1] + 2
    for x in (xa, xb):
        t.set(x, G, z, 'minecraft:polished_deepslate')
        for y in range(F, F + 4):
            t.set(x, y, z, 'or:imperial_plating')
        t.set(x, F + 3, z + 1, 'or:light_panel_red')
    for x in range(xa, xb + 1):
        t.set(x, F + 4, z, 'or:durasteel_beam[axis=x]')
    # the boom lies where it was knocked off
    for x in range(xb + 1, xb + 5):
        t.set(x, F, z + 1, 'or:durasteel_beam[axis=x]')


# ---------------------------------------------------------------- spoil heaps

def heaps(t, rng, v):
    spoil = Mix([('minecraft:coarse_dirt', 3), ('minecraft:gravel', 3), ('minecraft:tuff', 2),
                 ('minecraft:red_sand', 2), ('minecraft:terracotta', 1), ('minecraft:andesite', 1)], SEED + 100, 2.5)
    n = Field(SEED + 101, 3.0)

    def mound(cx, cz, rx, rz, h):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                if not (0 <= x < SIZE[0] and 0 <= z < SIZE[2]):
                    continue
                d = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
                hh = h * (1.0 - d) + (n(x, 0, z) - 0.5) * 1.6
                k = int(hh)
                if k <= 0 or not vk.is_air_state(t.get(x, F, z)) or not t.is_solid(x, G, z):
                    continue
                for y in range(F, F + k):
                    if not vk.is_air_state(t.get(x, y, z)) and t.get(x, y, z) is not None:
                        break
                    t.set(x, y, z, spoil(x, y, z))
                if n(x + 9, 0, z) < 0.35 and t.is_air(x, F + k, z):
                    t.set(x, F + k, z, 'or:debris')

    # a long heap along the east edge, another in the south-east corner, a small one north
    for (cz, rz) in ((12, 6.5), (26, 7.0), (40, 6.0)):
        mound(47.0, cz, 3.2, rz, 4.6)
    mound(42, 46, 7.0, 4.2, 5.2)
    mound(31, 3, 5.5, 2.6, 2.6 if v == 'door' else 3.4)
    # the narrow-gauge track from the stair head to the south-east heap, a stop at its end
    z = ROAD_Z[1] + 1
    for x in range(STAIR_X[1] + 2, 38):
        t.set(x, G, z, 'minecraft:gravel')
        t.set(x, F, z, 'minecraft:rail[shape=east_west,waterlogged=false]')
    t.set(38, F, z, 'minecraft:polished_deepslate_wall')


# ---------------------------------------------------------------- what happened

def remains(t, x, y, z, facing, rng):
    """A dead digger: a skull and a bone, blaster scorch around them."""
    dx, dz = STEP[facing]
    t.set(x, y, z, vk.skull(rng.randrange(16)))
    t.set(x + dx, y, z + dz, vk.bone('x' if dx else 'z'))
    for (ox, oz) in ((dx * 2, dz * 2), (-dz, dx), (dz, -dx), (dx - dz, dz + dx)):
        if t.is_air(x + ox, y, z + oz) and t.is_solid(x + ox, y - 1, z + oz):
            t.set(x + ox, y, z + oz, 'or:scorch_mark')


def story(t, rng, v):
    spec = HUTS[v]
    for (x, y, z, f) in spec['remains']:
        remains(t, x, y, z, f, rng)
    sx, sy, sz = spec['spawn']
    mod.spawner(t, sx, sy, sz, 'security_droids')
    if v == 'door':
        remains(t, 27, PF, 16, 'east', rng)
        remains(t, 35, PF, 18, 'south', rng)
        t.set(HEAD_X, PF, 19, vk.lantern(False))
        mod.spawner(t, HEAD_X, PF, 20, 'security_droids')
        # one droid did not get up again: its wreck at the stair foot
        t.set(34, PF, 29, 'or:wreck_salvage')
        t.set(35, PF, 29, 'or:scorch_mark')
    else:
        remains(t, 29, 7, 23, 'east', rng)
        remains(t, 34, PF, 30, 'west', rng)
        t.set(33, 7, 25, vk.lantern(False))
        mod.spawner(t, HEAD_X + 2, 7, 22, 'security_droids')
        # the trolley and its chains fell with the jib
        t.set(36, PF, 30, 'or:durasteel_plating')
        t.set(36, PF + 1, 30, 'or:tomb_stone_carved')
        t.set(35, PF, 31, vk.chain('x'))
        t.set(37, PF, 31, 'or:tomb_stone_cracked')
    # blaster scorch on the camp ground and the pit floor
    n = Field(SEED + 110, 2.0)
    for _ in range(30):
        x, z = rng.randrange(SIZE[0]), rng.randrange(SIZE[2])
        for y in (F, B1, B2, PF):
            if t.is_air(x, y, z) and t.is_solid(x, y - 1, z) and vk.B.name_of(t.get(x, y - 1, z)) not in (
                    'minecraft:iron_bars',) and not vk.B.name_of(t.get(x, y - 1, z)).endswith(('_stairs', '_slab')):
                if n(x, y, z) < 0.6:
                    t.set(x, y, z, 'or:scorch_mark')
                break


# ---------------------------------------------------------------- weather

def weather(t, rng, v, level):
    ash = Mix([('minecraft:light_gray_concrete_powder', 3), ('minecraft:gravel', 1), ('minecraft:tuff', 1)],
              SEED + 120, 2.0)
    vk.drift(t, (0, G, 0, SIZE[0] - 1, G, SIZE[2] - 1), ash, wind='west', depth=2, reach=3, seed=SEED + 121,
             cap='minecraft:light_gray_carpet', cap_chance=0.5,
             against={'oldrepublic:imperial_plating', 'oldrepublic:imperial_panel', 'oldrepublic:durasteel_plating',
                      'oldrepublic:wreck_plating_scorched', 'oldrepublic:cargo_crate_imperial', 'oldrepublic:cargo_crate'})
    cells = []
    for (x, z), lvl in level.items():
        if lvl == B1:
            cells.append((x, lvl, z))
    vk.ash_cover(t, cells, SEED + 122, chance=0.25)


def lights(t, v, huts):
    for box in huts.values():
        x0, y0, z0, x1, y1, z1 = box
        mod.light_fill(t, (x0, y0, z0, x1, y1, z1), level=8, strength=11)
    x0, z0, x1, z1 = TENT
    mod.light_fill(t, (x0, F, z0, x1, F + 2, z1), level=8, strength=11)
    px, pz = HUTS[v]['pods']
    mod.light_fill(t, (px, F, pz + 1, px + 4, F + 1, pz + 1), level=8, strength=11)


if __name__ == '__main__':
    a = vk.args()
    build(a.preview, a.only)
