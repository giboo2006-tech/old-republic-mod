"""hub/sepulchre: Zarvos's Sepulchre, the tomb under the Proving (docs/MILESTONE_6.md 5.7).

55 x 16 x 51 (grown from 33 x 16 x 41). PlanetHubs stamps it at the Proving's site meta link to it
(frame.SEPULCHRE_OFFSET, (20, -16, 8) in the Proving's frame), so its top layer lies just under the Proving's
lowest one. Every cell is set: the rooms are cut out of a block of the planet's deep rock and dressed in tomb
brick.

    the stair     the lower half of the Proving's stair well (frame.py): the flights below the Descent, the
                  ladder's foot and the lowest landing
    processional  south out of the well: hooded statues in niches, the long stair down, the antechamber with
                  its burial niches, and the great door into the hall
    the hall      Zarvos's hall, where the trial is fought: an octagon vaulted on eight pillars over a stepped
                  pit, his black coffin on its plinth in the pit, red light from the walls' feet and the plinth
    ROOM_1        the acolytes' crypt (north): sarcophagi, an altar, burial niches
    ROOM_2        the hall of his guard (south): statues of his warriors, one of them fallen
    ROOM_3        the inner vault (east): its stone leaves folded back, the bar that sealed it cast aside and
                  the cradle on the dais empty (the codex was taken long ago; see the Zarvos holocron lines)

The trial (WorldArcs.tickSepulchre) spawns its waves on rings of 7 and 8 round HEART at HEART.y + 6, and the
echo 4 east of HEART at the same height: the hall is open to y 11 out to radius 9, nothing stands in those
columns but the pit's steps, and the coffin stands under the echo's column (ECHO, on its lid).

Run from the repository root: python3 tools/assets/structures/h/sepulchre.py
"""
import math

from common import *  # noqa: F401,F403
import frame
import proving

ID = 'hub/sepulchre'
SEED = 0x5E9C7E
SX, SY, SZ = frame.SEPULCHRE_SIZE
TOP = SY - 1

HEART = (30, 2, 27)             # feet, in the pit
ECHO = (34, 4, 27)              # feet, on the coffin's lid: the echo's spawn column (HEART + 4 east)
ROOMS = {'ROOM_1': (30, 4, 8), 'ROOM_2': (30, 4, 45), 'ROOM_3': (49, 4, 27)}
FLOOR = 3                       # the floor layer of the walkway, the rooms and the antechamber (feet 4)
PIT = 1                         # the pit's floor layer (feet 2)
HALL_R, WALL_R = 11.5, 13.5     # the hall's octagon: inside, and the outside of its wall
LAND = frame.BOTTOM_FEET - frame.SEPULCHRE_OFFSET[1]          # 9: feet on the well's lowest landing
PX0, PX1 = (v - frame.SEPULCHRE_OFFSET[0] for v in frame.EXIT)  # 7, 11: the processional's width
PZ0 = frame.Z1 + 3 - frame.SEPULCHRE_OFFSET[2]                 # 15: first row south of the well
ANTE = (7, 24, 16, 30)          # the antechamber's inside: x0, z0, x1, z1
R1 = (23, 3, 37, 12)            # the rooms' insides
R2 = (23, 42, 37, 49)
R3 = (46, 21, 53, 33)

M = Palette(
    rock=[('minecraft:blackstone', 6), (S('basalt', axis='y'), 2), ('minecraft:smooth_basalt', 2), ('minecraft:tuff', 2),
          (S('deepslate', axis='y'), 3), ('minecraft:cobbled_deepslate', 1)],
    tomb=[('oldrepublic:tomb_bricks', 6), ('oldrepublic:tomb_stone', 2), ('minecraft:polished_blackstone_bricks', 2),
          ('oldrepublic:tomb_stone_cracked', 1)],
    vault=[('oldrepublic:tomb_stone', 4), ('oldrepublic:tomb_bricks', 3), ('oldrepublic:tomb_stone_cracked', 1)],
    flag=[('minecraft:polished_blackstone', 4), ('minecraft:polished_deepslate', 2), ('minecraft:deepslate_tiles', 2),
          ('minecraft:cracked_deepslate_tiles', 1), ('minecraft:polished_blackstone_bricks', 1)],
    pit=[('minecraft:polished_deepslate', 3), ('minecraft:deepslate_tiles', 2), ('minecraft:polished_blackstone', 1)],
    inner=[('minecraft:polished_blackstone_bricks', 5), ('minecraft:cracked_polished_blackstone_bricks', 2),
           ('minecraft:polished_blackstone', 1)],
)

TOMB_STAIRS = proving.TOMB_STAIRS
PBB_STAIRS = proving.PBB_STAIRS
TILE_SLAB = proving.TILE_SLAB
TOMB_SLAB = 'oldrepublic:tomb_brick_slab'
RED = proving.RED
CHISELED = proving.CHISELED
DARK_WALL = proving.DARK_WALL
PILLAR = S('oldrepublic:tomb_pillar', axis='y')
URN = 'oldrepublic:funerary_urn'
OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}

WEATHER = {
    'oldrepublic:tomb_bricks': [('oldrepublic:tomb_stone_cracked', 0.07)],
    'minecraft:polished_blackstone_bricks': [('minecraft:cracked_polished_blackstone_bricks', 0.10)],
    'minecraft:deepslate_tiles': [('minecraft:cracked_deepslate_tiles', 0.10)],
}


def patches(role, seed, scale=3.0):
    return M.patches(role, seed=SEED + seed, scale=scale)


def oct_r(x, z):
    """Distance from HEART's column by the hall's octagon (flat sides facing the axes)."""
    a, b = abs(x - HEART[0]), abs(z - HEART[2])
    return max(a, b, (a + b) / math.sqrt(2))


def dome(r):
    """The highest air layer of the hall's vault at octagon radius r (the crown at 13, so the vault's ribs and
    crown stone stay under the template's top layer, which is plain rock)."""
    return int(round(13 - 3.0 * (r / HALL_R) ** 2))


def ring_spots():
    """The trial's spawn columns (WorldArcs.ringSpot): radius 7 and 8 at the seven angles, and the echo's."""
    spots = []
    for radius in (7, 8):
        for i in range(7):
            a = i * (math.pi * 2 / 7)
            spots.append((HEART[0] + int(math.floor(math.cos(a) * radius + 0.5)),
                          HEART[2] + int(math.floor(math.sin(a) * radius + 0.5))))
    spots.append((HEART[0] + 4, HEART[2]))
    return spots


# ---------------------------------------------------------------- pieces

def statue(t, x, y, z, looks):
    """A hooded figure on its pedestal (feet layer y): pedestal, robe, shoulders, hood, looking `looks`."""
    t.set(x, y, z, CHISELED)
    t.set(x, y + 1, z, wall_block(DARK_WALL))
    t.set(x, y + 2, z, PILLAR)
    t.set(x, y + 3, z, stairs(PBB_STAIRS, OPPOSITE[looks], 'top'))


def sarcophagus(t, x, y, z, axis='x'):
    """A stone sarcophagus three long on the floor (feet layer y), carved at the ends, with a slab lid."""
    for i in range(3):
        p = (x + i, z) if axis == 'x' else (x, z + i)
        t.set(p[0], y, p[1], 'oldrepublic:tomb_stone_carved' if i != 1 else 'oldrepublic:tomb_stone')
        t.set(p[0], y + 1, p[1], slab(TOMB_SLAB))


def niche(t, x, y, z, rng, odds=(0.3, 0.25, 0.12)):
    """A burial niche cut one deep into a wall: an urn, a candle, a skull or nothing."""
    r = rng.random()
    urn, light, skull = odds
    if r < urn:
        t.set(x, y, z, URN)
    elif r < urn + light:
        t.set(x, y, z, candle(rng.randint(1, 3), 'black'))
    elif r < urn + light + skull:
        t.set(x, y, z, S('skeleton_skull', rotation=rng.randrange(16), powered=False))
    else:
        t.set(x, y, z, AIR)


def arch_door(t, axis, a, b, y0, y1, depth):
    """A doorway through a wall: cells a..b along the wall (x for a wall along x, else z), through it at the
    rows `depth`, air from y0 to y1 over a polished sill, its top corners eased with upside-down stairs."""
    for v in range(a, b + 1):
        for d in depth:
            p = (v, d) if axis == 'x' else (d, v)
            t.set(p[0], y0 - 1, p[1], 'minecraft:polished_blackstone')
            for y in range(y0, y1 + 1):
                t.set(p[0], y, p[1], AIR)
    if b - a >= 2:
        for d in depth:
            for v, facing in ((a, 'west' if axis == 'x' else 'north'), (b, 'east' if axis == 'x' else 'south')):
                p = (v, d) if axis == 'x' else (d, v)
                t.set(p[0], y1, p[1], stairs(PBB_STAIRS, facing, 'top'))


# ---------------------------------------------------------------- the stair and the processional

def stair(t):
    """The well's lower half, in the same stone as the Proving's half (frame.py, the Proving's palette)."""
    tomb = proving.patches('tomb', 401, 2.5)
    for (x, y, z), s in sorted(frame.stair_cells(tomb, parapet=DARK_WALL).items()):
        if y < 0:
            t.set(*frame.to_sepulchre((x, y, z)), s)


def processional(t):
    """From the well's lowest landing south: a landing between two statues, four steps down, the antechamber."""
    flag = patches('flag', 12, 2.2)
    # the landing (feet 9), air to y 12
    t.fill(PX0, LAND - 1, PZ0, PX1, LAND - 1, 17, flag)
    t.fill(PX0, LAND, PZ0, PX1, 12, 17, AIR)
    # four steps down southward to the antechamber's floor
    for i in range(1, 5):
        z, y = 17 + i, LAND - 1 - i
        for x in range(PX0, PX1 + 1):
            t.set(x, y, z, stairs(TOMB_STAIRS, 'north'))
            for yy in range(y + 1, 13):
                t.set(x, yy, z, AIR)
    t.fill(PX0, FLOOR, 22, PX1, FLOOR, 23, flag)
    t.fill(PX0, FLOOR + 1, 22, PX1, 12, 23, AIR)
    # hooded statues in niches: two on the landing, two at the stair's foot, looking across
    for x, looks in ((PX0 - 1, 'east'), (PX1 + 1, 'west')):
        statue(t, x, LAND, 16, looks)
        statue(t, x, FLOOR + 1, 22, looks)
    # red light from the floor at the head and the foot of the stair
    for x in (PX0, PX1):
        t.set(x, LAND - 1, 17, RED)
        t.set(x, FLOOR, 23, RED)


def antechamber(t):
    """A low hall of burial niches between the processional and the great door into Zarvos's hall."""
    x0, z0, x1, z1 = ANTE
    t.fill(x0, FLOOR, z0, x1, FLOOR, z1, patches('flag', 13, 2.2))
    t.fill(x0, FLOOR + 1, z0, x1, 9, z1, AIR)
    # beams across the ceiling
    for x in (9, 12, 15):
        for z in range(z0, z1 + 1):
            t.set(x, 9, z, S('polished_basalt', axis='z'))
    # burial niches in the south, north and west walls
    rng = t.rng
    for x in range(8, 17, 2):
        for y in (5, 7):
            niche(t, x, y, z1 + 1, rng)
    for x in (14, 16):
        for y in (5, 7):
            niche(t, x, y, z0 - 1, rng)
    for z in (25, 27, 29):
        for y in (5, 7):
            niche(t, x0 - 1, y, z, rng)
    # the great door into the hall: chiseled jambs, eased arch, red light at the jambs' feet (hall side)
    arch_door(t, 'z', 25, 29, FLOOR + 1, 8, depth=(17, 18))
    for z in (24, 30):
        for x in (17, 18):
            for y in range(FLOOR + 1, 9):
                t.set(x, y, z, CHISELED)
        t.set(18, FLOOR + 1, z, RED)
        t.set(16, FLOOR, z, RED)
    for z in range(24, 31):
        t.set(17, 9, z, CHISELED)
        t.set(18, 9, z, CHISELED)


# ---------------------------------------------------------------- the hall

PILLARS = [(10, 4), (4, 10), (-4, 10), (-10, 4), (-10, -4), (-4, -10), (4, -10), (10, -4)]


def hall(t):
    """The octagon: wall, stepped pit, walkway, pillars, vault and ribs."""
    cx, _, cz = HEART
    wall = patches('tomb', 21, 3.0)
    vault = patches('vault', 22, 3.0)
    flag = patches('flag', 23, 2.2)
    pitf = patches('pit', 24, 2.0)
    for x in range(cx - 14, cx + 15):
        for z in range(cz - 14, cz + 15):
            r = oct_r(x, z)
            if r > WALL_R:
                continue
            if r > HALL_R:
                for y in range(0, TOP):
                    t.set(x, y, z, wall(x, y, z))
                continue
            if r <= 5.5:
                top, half = PIT, None
            elif r <= 6.5:
                top, half = PIT, PIT + 1
            elif r <= 7.5:
                top, half = PIT + 1, PIT + 2
            else:
                top, half = FLOOR, None
            for y in range(0, top):
                t.set(x, y, z, wall(x, y, z))
            t.set(x, top, z, pitf(x, top, z) if r <= 5.5 else flag(x, top, z))
            if half is not None:
                t.set(x, half, z, slab(TILE_SLAB))
            h = dome(r)
            for y in range((half if half is not None else top) + 1, h + 1):
                t.set(x, y, z, AIR)
            for y in range(h + 1, TOP):
                t.set(x, y, z, vault(x, y, z))
    # the vault's ribs: from each pillar to the crown, on the ceiling's underside
    angles = [math.atan2(dz, dx) for dx, dz in PILLARS]
    for x in range(cx - 12, cx + 13):
        for z in range(cz - 12, cz + 13):
            r = oct_r(x, z)
            if r > HALL_R or r < 1:
                continue
            th = math.atan2(z - cz, x - cx)
            d = math.hypot(x - cx, z - cz)
            if any(math.cos(th - a) > 0 and d * abs(math.sin(th - a)) < 0.55 for a in angles):
                t.set(x, dome(r) + 1, z, S('polished_basalt', axis='y'))
    t.set(cx, dome(0) + 1, cz, CHISELED)
    # the pillars: a flared base, the shaft, a capital on corbels under the rib
    for dx, dz in PILLARS:
        x, z = cx + dx, cz + dz
        h = dome(oct_r(x, z))
        t.set(x, FLOOR + 1, z, 'minecraft:polished_blackstone_bricks')
        for y in range(FLOOR + 2, h):
            t.set(x, y, z, PILLAR)
        t.set(x, h, z, CHISELED)
        for (ox, oz, toward) in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
            t.set(x + ox, FLOOR + 1, z + oz, stairs(PBB_STAIRS, toward))
            t.set(x + ox, h - 1, z + oz, stairs(PBB_STAIRS, toward, 'top'))
    # carved panels over the red lights on the slanted sides
    for sx_ in (-1, 1):
        for sz_ in (-1, 1):
            for a, b in ((9, 8), (8, 9)):
                for y in range(FLOOR + 3, FLOOR + 8):
                    t.set(cx + sx_ * a, y, cz + sz_ * b, 'oldrepublic:tomb_stone_carved')
    # plain red banners either side of each door
    for (x, z, facing) in ((cx - 11, cz - 3, 'east'), (cx - 11, cz + 3, 'east'), (cx - 3, cz - 11, 'south'),
                           (cx + 3, cz - 11, 'south'), (cx - 3, cz + 11, 'north'), (cx + 3, cz + 11, 'north'),
                           (cx + 11, cz - 2, 'west'), (cx + 11, cz + 2, 'west')):
        mod.banner(t, x, 10, z, facing, base='red')
    # a bare chain hanging from the crown over the pit
    for y in range(dome(0) - 3, dome(0) + 1):
        t.set(cx, y, cz, chain())
    # red light at the wall's foot on the four slanted sides
    for sx_ in (-1, 1):
        for sz_ in (-1, 1):
            for a, b in ((9, 8), (8, 9)):
                t.set(cx + sx_ * a, FLOOR + 1, cz + sz_ * b, RED)
    # black candles along the walkway's rim on the slanted sides
    for sx_ in (-1, 1):
        for sz_ in (-1, 1):
            t.set(cx + sx_ * 7, FLOOR + 1, cz + sz_ * 7, candle(3, 'black'))


def coffin(t):
    """Zarvos's coffin on its plinth in the pit, under the echo's column; red light from the plinth's corners."""
    ex, ey, ez = ECHO
    for x in range(ex - 1, ex + 2):
        for z in range(ez - 1, ez + 2):
            corner = x != ex and z != ez
            t.set(x, PIT + 1, z, RED if corner else 'minecraft:polished_blackstone_bricks')
    t.set(ex - 1, PIT + 2, ez, CHISELED)
    t.set(ex, PIT + 2, ez, 'oldrepublic:tomb_stone_carved')
    t.set(ex + 1, PIT + 2, ez, CHISELED)
    t.set(ex - 1, PIT + 2, ez - 1, candle(2, 'black'))
    t.set(ex + 1, PIT + 2, ez + 1, candle(2, 'black'))
    t.set(ex + 1, PIT + 2, ez - 1, slab('minecraft:polished_blackstone_brick_slab'))
    t.set(ex - 1, PIT + 2, ez + 1, slab('minecraft:polished_blackstone_brick_slab'))


def hall_doors(t):
    """North to the crypt, south to the guard, east to the inner vault; red light at the jambs' feet."""
    cx, _, cz = HEART
    arch_door(t, 'x', cx - 2, cx + 2, FLOOR + 1, 8, depth=(13, 14, 15))      # ROOM_1
    arch_door(t, 'x', cx - 2, cx + 2, FLOOR + 1, 8, depth=(39, 40, 41))      # ROOM_2
    arch_door(t, 'z', cz - 1, cz + 1, FLOOR + 1, 7, depth=(42, 43, 44, 45))  # ROOM_3, the vault door
    for x in (cx - 3, cx + 3):
        for z in (15, 39):
            t.set(x, FLOOR + 1, z, RED)
            for y in range(FLOOR + 2, 9):
                t.set(x, y, z, CHISELED)
        for y in range(FLOOR + 2, 9):
            t.set(x, y, 13, CHISELED)
            t.set(x, y, 41, CHISELED)
    for z in (cz - 2, cz + 2):
        t.set(42, FLOOR + 1, z, RED)
        for x in (42, 43, 44, 45):
            for y in range(FLOOR + 2, 8):
                t.set(x, y, z, CHISELED if x in (42, 45) else 'minecraft:polished_blackstone_bricks')
    for z in range(cz - 2, cz + 3):
        for x in (44, 45):
            t.set(x, 8, z, CHISELED)


# ---------------------------------------------------------------- the rooms

def room_shell(t, box, y0, y1, role, seed):
    x0, z0, x1, z1 = box
    t.fill(x0 - 1, y0, z0 - 1, x1 + 1, y1, z1 + 1, patches(role, seed, 3.0))


def barrel_vault(t, box, top, along='x'):
    """Air in a room: walls straight to `top`, then two steps in toward the middle."""
    x0, z0, x1, z1 = box
    t.fill(x0, FLOOR + 1, z0, x1, top, z1, AIR)
    if along == 'x':
        t.fill(x0, top + 1, z0 + 1, x1, top + 1, z1 - 1, AIR)
        t.fill(x0, top + 2, z0 + 3, x1, top + 2, z1 - 3, AIR)
    else:
        t.fill(x0 + 1, top + 1, z0, x1 - 1, top + 1, z1, AIR)
        t.fill(x0 + 3, top + 2, z0, x1 - 3, top + 2, z1, AIR)


def crypt(t):
    """ROOM_1, the acolytes' crypt: four sarcophagi, burial niches, the altar under a carved relief."""
    x0, z0, x1, z1 = R1
    t.fill(x0, FLOOR, z0, x1, FLOOR, z1, patches('flag', 31, 2.2))
    barrel_vault(t, R1, 8)
    for x in (25, 30, 35):
        for z in range(z0, z1 + 1):
            for y in (9, 10):
                if t.name_at(x, y, z) == 'minecraft:air':
                    t.set(x, y, z, 'minecraft:polished_blackstone_bricks')
    for x in (24, 34):
        sarcophagus(t, x, FLOOR + 1, 5)
        sarcophagus(t, x, FLOOR + 1, 10)
    # the altar
    t.set(30, FLOOR + 1, z0, CHISELED)
    t.set(30, FLOOR + 2, z0, candle(3, 'black'))
    for x in (29, 30, 31):
        for y in (5, 6, 7):
            t.set(x, y, z0 - 1, 'oldrepublic:tomb_stone_carved')
    for x in (28, 32):
        t.set(x, FLOOR, z0 + 1, RED)
    rng = t.rng
    for x in (24, 26, 34, 36):
        for y in (5, 7):
            niche(t, x, y, z0 - 1, rng)
    for z in (5, 7, 9):
        for y in (5, 7):
            niche(t, x0 - 1, y, z, rng)
            niche(t, x1 + 1, y, z, rng)
    for (x, z) in ((x0, z0), (x1, z0)):
        t.set(x, 8, z, 'minecraft:cobweb')
    for x in (x0, x1):
        t.set(x, FLOOR, 8, RED)


def guard(t):
    """ROOM_2, the hall of Zarvos's guard: six warriors on pedestals along the side walls (one fallen), a
    carved relief between two cold cauldrons."""
    x0, z0, x1, z1 = R2
    t.fill(x0, FLOOR, z0, x1, FLOOR, z1, patches('flag', 41, 2.2))
    barrel_vault(t, R2, 8)
    for z in (43, 46, 49):
        statue(t, x0, FLOOR + 1, z, 'east')
        if z != 46:
            statue(t, x1, FLOOR + 1, z, 'west')
    # the fallen one: a broken pedestal, the body across the floor, rubble
    t.set(x1, FLOOR + 1, 46, slab('minecraft:polished_blackstone_slab'))
    for x in (34, 35):
        t.set(x, FLOOR + 1, 46, S('oldrepublic:tomb_pillar', axis='x'))
    t.set(33, FLOOR + 1, 46, stairs(PBB_STAIRS, 'east', 'bottom'))
    for (x, z) in ((36, 45), (33, 47), (35, 47), (32, 45)):
        t.set(x, FLOOR + 1, z, 'oldrepublic:debris')
    # the relief on the south wall and the cauldrons before it
    for x in (29, 30, 31):
        for y in range(FLOOR + 1, FLOOR + 6):
            t.set(x, y, z1 + 1, 'oldrepublic:tomb_stone_carved')
    for x in (27, 33):
        t.set(x, FLOOR + 1, z1, S('cauldron'))
    t.set(30, FLOOR, z1, RED)
    rng = t.rng
    for z in (44, 47):
        for y in (5, 7):
            niche(t, x0 - 1, y, z, rng)
            niche(t, x1 + 1, y, z, rng)
    for x in (x0 + 2, x1 - 2):
        t.set(x, FLOOR, z0, RED)
    t.set(x0, 8, z1, 'minecraft:cobweb')


def inner_vault(t):
    """ROOM_3, the inner vault: its stone leaves folded back against the wall, the sealing bar cast aside, the
    cradle on the dais empty, shelves bare but for a few urns."""
    x0, z0, x1, z1 = R3
    t.fill(x0, FLOOR, z0, x1, FLOOR, z1, patches('flag', 51, 2.2))
    barrel_vault(t, R3, 9, along='z')
    # the leaves, folded back flat against the west wall either side of the door
    for z in (23, 24, 30, 31):
        for y in range(FLOOR + 1, FLOOR + 5):
            t.set(x0, y, z, 'oldrepublic:tomb_stone_carved' if y == FLOOR + 2 else 'minecraft:polished_blackstone')
    # the bar, cast aside along the north wall
    for x in (48, 49, 50):
        t.set(x, FLOOR + 1, z0, S('polished_basalt', axis='x'))
    # the dais and the empty cradle
    for z in range(25, 30):
        t.set(50, FLOOR + 1, z, stairs(PBB_STAIRS, 'east'))
        for x in range(51, x1 + 1):
            t.set(x, FLOOR + 1, z, 'minecraft:polished_blackstone_bricks')
    t.set(52, FLOOR + 2, 27, CHISELED)
    for z in (26, 28):
        t.set(51, FLOOR + 2, z, candle(1, 'black', lit=False))
    t.set(53, FLOOR + 2, 25, URN)
    for z in (24, 30):
        t.set(50, FLOOR, z, RED)
    for x in (x0 + 1, x1 - 1):
        t.set(x, FLOOR, z0 + 1, RED)
        t.set(x, FLOOR, z1 - 1, RED)
    # bare shelves: niches two high in the north and south walls
    rng = t.rng
    for x in range(47, 54, 2):
        for z in (z0 - 1, z1 + 1):
            for y in (5, 7):
                if rng.random() < 0.15:
                    t.set(x, y, z, URN)
                elif rng.random() < 0.1:
                    t.set(x, y, z, 'minecraft:cobweb')
                else:
                    t.set(x, y, z, AIR)
    for (x, z) in ((47, 26), (48, 30), (49, 22), (52, 32), (47, 32)):
        t.set(x, FLOOR + 1, z, 'oldrepublic:debris' if (x + z) % 2 else 'oldrepublic:scorch_mark')


# ---------------------------------------------------------------- assembly

def check_trial(t):
    """The hall must take the trial's spawns: air at HEART.y + 6 and over it in every spawn column."""
    y = HEART[1] + 6
    for (x, z) in ring_spots():
        for yy in (y, y + 1):
            if t.name_at(x, yy, z) not in ('minecraft:air', 'minecraft:light', 'minecraft:iron_chain'):
                raise ValueError('trial spawn column (%d, %d) is blocked at y %d by %s' % (x, z, yy, t.name_at(x, yy, z)))


def build():
    t = Template(SX, SY, SZ, seed=SEED, name=ID)
    t.fill(0, 0, 0, SX - 1, TOP, SZ - 1, patches('rock', 1, 5.0))
    # dressed shells round every space, then the spaces
    t.fill(PX0 - 2, FLOOR - 1, PZ0, PX1 + 2, 14, 23, patches('tomb', 11, 2.5))
    x0, z0, x1, z1 = ANTE
    t.fill(x0 - 2, FLOOR - 1, z0 - 2, x1 + 1, 11, z1 + 2, patches('tomb', 14, 2.5))
    room_shell(t, R1, FLOOR - 1, 12, 'tomb', 32)
    room_shell(t, R2, FLOOR - 1, 12, 'tomb', 42)
    room_shell(t, R3, FLOOR - 1, 13, 'inner', 52)
    t.fill(42, FLOOR - 1, HEART[2] - 2, 45, 9, HEART[2] + 2, patches('inner', 53, 3.0))
    stair(t)
    hall(t)
    processional(t)
    antechamber(t)
    crypt(t)
    guard(t)
    inner_vault(t)
    hall_doors(t)
    coffin(t)
    nature.weather(t, WEATHER, noise=Noise(SEED + 3, 4.0), patchiness=0.5)
    mod.spot(t, 'HEART', *HEART)
    mod.spot(t, 'ECHO', *ECHO)
    for name, p in sorted(ROOMS.items()):
        mod.spot(t, name, *p)
    mod.light_fill(t, region=(0, 1, 0, SX - 1, TOP - 1, SZ - 1), level=6, strength=11)
    check_trial(t)
    return t


def main():
    t = build()
    report(t)
    save(t, ID, preview_name='sepulchre', cut=9)
    return t


if __name__ == '__main__':
    main()
