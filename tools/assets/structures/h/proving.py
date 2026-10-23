"""hub/proving: the Proving on Varkhast, the Empire's academy, a Sith proving ground carved from black stone
against a crag (docs/MILESTONE_6.md 5.7; planets survey 3.4).

A walled town drawn in its own frame (91 x 56 x 91, ground layer y 4, feet y 5) and set over a ground skirt in
the 95 x 59 x 95 hub template (ground layer y 7, feet y 8: buried 8).

    north:   the crag, a ridge of black rock with the academy cut into its face: the Hall of Trials behind a
             carved facade and its great portal, the Descent (the stair down to Zarvos's Sepulchre) behind a
             tomb door, the Overseer's spire on the ridge, the fallen older wing at the crag's foot (NE)
    middle:  the forecourt and the rostrum where the Overseer stands, the sunken arena where acolytes fight,
             the barracks, the Overseer's house and the training yard (W), the cantina and the med bay (E)
    street:  the road between the west and east gates, the bazaar's stalls on its south side
    south:   the landing field (four 11 x 11 bays inside blast walls), the armoury, the spacer's kiosk and the
             firing range (SW), the pad tower, the quartermaster's depot, the comm mast and the staff
             houses (SE), the south gate

The stair down lives in frame.py, shared with sepulchre.py.

Run from the repository root: python3 tools/assets/structures/h/proving.py
"""
from common import *  # noqa: F401,F403
import frame

ID = 'hub/proving'
SEED = 0x5E7A11

G, F = 4, 5                     # the town frame's ground and feet layers (the arena pit goes 4 below)
TX, TY, TZ = 91, 56, 91
SKIRT = 4
DROP = 3
SHIFT = (SKIRT - 2, DROP, SKIRT - 2)
SX, SY, SZ = TX + 2 * (SKIRT - 2), TY + DROP, TZ + 2 * (SKIRT - 2)
HUB_GROUND = G + DROP           # 7
HUB_FEET = F + DROP             # 8

HEART = (45, F, 70)             # town frame
BAYS = [(38, 63), (52, 63), (38, 77), (52, 77)]    # PAD_BAY_1..4
BAY_R = 5
HOST = (45, F + 3, 30)          # on the rostrum
GATES = {'west': (53, 56), 'east': (53, 56), 'south': (43, 47)}


def to_town(p):
    return (p[0] - SHIFT[0], p[1] - SHIFT[1], p[2] - SHIFT[2])


# ---------------------------------------------------------------- materials

M = Palette(
    rock=[('minecraft:blackstone', 9), (S('basalt', axis='y'), 3), ('minecraft:smooth_basalt', 2), ('minecraft:tuff', 2),
          (S('deepslate', axis='y'), 1), ('minecraft:cobbled_deepslate', 1)],
    rocktop=[('minecraft:blackstone', 6), ('minecraft:smooth_basalt', 3), ('minecraft:tuff', 3),
             ('minecraft:cobbled_deepslate', 1), ('minecraft:coarse_dirt', 1)],
    band=[('minecraft:red_terracotta', 3), ('minecraft:brown_terracotta', 2), ('minecraft:terracotta', 1)],
    ashlar=[('minecraft:polished_blackstone_bricks', 8), ('minecraft:cracked_polished_blackstone_bricks', 2),
            ('oldrepublic:tomb_bricks', 4), ('minecraft:polished_blackstone', 1)],
    tomb=[('oldrepublic:tomb_bricks', 6), ('oldrepublic:tomb_stone', 2), ('minecraft:polished_blackstone_bricks', 2),
          ('oldrepublic:tomb_stone_cracked', 1)],
    flag=[('minecraft:polished_blackstone', 4), ('minecraft:polished_blackstone_bricks', 3),
          ('minecraft:smooth_basalt', 2), ('minecraft:tuff_bricks', 1), ('minecraft:polished_tuff', 1),
          ('minecraft:cracked_polished_blackstone_bricks', 1)],
    road=[('minecraft:smooth_basalt', 3), ('minecraft:polished_tuff', 2), ('minecraft:tuff_bricks', 2),
          ('minecraft:polished_blackstone', 2), ('minecraft:tuff', 1)],
    earth=[('minecraft:coarse_dirt', 8), ('minecraft:terracotta', 2), ('minecraft:tuff', 2),
           ('minecraft:smooth_basalt', 1), ('minecraft:packed_mud', 1), ('minecraft:red_terracotta', 1)],
    town=[('oldrepublic:ash_bricks', 7), ('minecraft:tuff_bricks', 2), ('minecraft:polished_tuff', 1)],
    plate=[('oldrepublic:imperial_plating', 5), ('oldrepublic:sealed_plating', 1), ('oldrepublic:durasteel_plating', 2)],
    plinth=[('minecraft:blackstone', 3), ('minecraft:polished_blackstone', 2), ('minecraft:cobbled_deepslate', 1)],
    deck=[('minecraft:smooth_basalt', 3), ('minecraft:polished_deepslate', 2), ('minecraft:polished_blackstone', 2),
          ('minecraft:tuff', 1)],
    bay=[('oldrepublic:durasteel_deck', 4), ('minecraft:polished_andesite', 2), ('minecraft:smooth_stone', 2)],
    tiles=[('minecraft:polished_deepslate', 3), ('minecraft:deepslate_tiles', 3), ('minecraft:cracked_deepslate_tiles', 1),
           ('minecraft:polished_tuff', 1)],
)

DARK_WALL = 'oldrepublic:tomb_brick_wall' if USE_KIT_WALLS else 'minecraft:polished_blackstone_brick_wall'
ASH_WALL = 'oldrepublic:ash_brick_wall' if USE_KIT_WALLS else 'minecraft:tuff_brick_wall'
PBB_STAIRS = 'minecraft:polished_blackstone_brick_stairs'
PBB_SLAB = 'minecraft:polished_blackstone_brick_slab'
TOMB_STAIRS = 'oldrepublic:tomb_brick_stairs'
TILE_STAIRS = 'minecraft:deepslate_tile_stairs'
TILE_SLAB = 'minecraft:deepslate_tile_slab'
ASH_STAIRS = 'oldrepublic:ash_brick_stairs'
ASH_SLAB = 'oldrepublic:ash_brick_slab'
PLATE_STAIRS = 'oldrepublic:imperial_plating_stairs'
PLATE_SLAB = 'oldrepublic:imperial_plating_slab'
RED = 'oldrepublic:light_panel_red'
PANEL = 'oldrepublic:light_panel'
AMBER = 'oldrepublic:light_panel_amber'
BASALT_Y = S('polished_basalt', axis='y')
CHISELED = 'minecraft:chiseled_polished_blackstone'
BARS = 'minecraft:iron_bars'
PANE = 'minecraft:gray_stained_glass_pane'

WEATHER = {
    'minecraft:polished_blackstone_bricks': [('minecraft:cracked_polished_blackstone_bricks', 0.12)],
    'oldrepublic:tomb_bricks': [('oldrepublic:tomb_stone_cracked', 0.06)],
    'oldrepublic:ash_bricks': [('minecraft:tuff_bricks', 0.05)],
    'minecraft:polished_blackstone': [('minecraft:blackstone', 0.08)],
}


def pick(t, role):
    return M.picker(role, t.rng)


def patches(role, seed, scale=3.0):
    return M.patches(role, seed=SEED + seed, scale=scale)


def interp(points, x):
    if x <= points[0][0]:
        return points[0][1]
    for (x0, v0), (x1, v1) in zip(points, points[1:]):
        if x0 <= x <= x1:
            return v0 + (v1 - v0) * (x - x0) / float(x1 - x0)
    return points[-1][1]


def shell_box(t, x0, z0, x1, z1, y0, y1, wall, floor=None, corner=None, plinth=None, air=True):
    """A one-thick building: plinth course at y0 (the floor layer), walls y0+1..y1, corner posts, the floor at
    y0 inside and air y0+1..y1 inside."""
    t.fill(x0, y0, z0, x1, y0, z1, plinth or wall)
    shapes.walls(t, x0, y0 + 1, z0, x1, y1, z1, wall)
    if corner is not None:
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            t.fill(x, y0, z, x, y1, z, corner)
    if floor is not None:
        t.fill(x0 + 1, y0, z0 + 1, x1 - 1, y0, z1 - 1, floor)
    if air:
        t.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1, z1 - 1, AIR)


def windows(t, x0, z0, x1, z1, y0, y1, every=3, state=BARS, sides=('north', 'south', 'east', 'west'), skip=()):
    """Window slits in the walls of a building on the rectangle, every `every` cells, from y0 to y1."""
    for side in sides:
        if side in ('north', 'south'):
            z = z0 if side == 'north' else z1
            for x in range(x0 + 2, x1 - 1, every):
                if x not in skip:
                    t.fill(x, y0, z, x, y1, z, state)
        else:
            x = x0 if side == 'west' else x1
            for z in range(z0 + 2, z1 - 1, every):
                if z not in skip:
                    t.fill(x, y0, z, x, y1, z, state)


def parapet(t, x0, z0, x1, z1, y, state, gaps=()):
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if (x, z) not in gaps:
                t.set(x, y, z, wall_block(state))
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            if (x, z) not in gaps:
                t.set(x, y, z, wall_block(state))


def brazier(t, x, y, z, base=CHISELED):
    """A fire bowl: a campfire on a pedestal (y is the pedestal's cell)."""
    t.set(x, y, z, base)
    t.set(x, y + 1, z, campfire(True, 'north'))


def post_lamp(t, x, y, z, height=3, post=DARK_WALL, soul=False):
    for yy in range(y, y + height):
        t.set(x, yy, z, wall_block(post))
    t.set(x, y + height, z, lantern(False, soul))


# ---------------------------------------------------------------- the ground

def ground(t):
    earth = patches('earth', 1, 5.0)
    t.fill(2, G, 2, TX - 3, G, TZ - 3, earth)


def pave(t, x0, z0, x1, z1, role, seed, scale=3.0, y=G, ragged=0.0):
    p = patches(role, seed, scale)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if ragged and (x in (x0, x1) or z in (z0, z1)) and t.rng.random() < ragged:
                continue
            t.set(x, y, z, p(x, y, z))


# ---------------------------------------------------------------- the crag

# The crag's south face (its front line, z) and the height of its ridge above the ground, along x.
FRONT = [(2, 13), (8, 17), (13, 23), (14, 24), (30, 24), (31, 26), (59, 26), (62, 25), (68, 22), (76, 19),
         (82, 16), (88, 13)]
RIDGE = [(2, 10), (10, 15), (17, 22), (30, 26), (45, 29), (60, 26), (70, 20), (80, 15), (88, 10)]
_FN = Noise(SEED + 21, 5.0)

# Built volumes cut into the crag (x0, z0, x1, z1, y0, y1): the rock does not need to wall them.
DESCENT = (17, 4, 34, 23)
HALL = (35, 4, 55, 23)
FACADE = (31, 24, 59, 26)
FACADE_TOP = G + 19
DESCENT_TOP = G + 11
HALL_TOP = G + 17
TOMB_DOOR = (25, 27)            # x span of the Descent's door, through z 22..24


def front_z(x):
    f = interp(FRONT, x)
    if 14 <= x <= 61:
        return int(round(f))
    return int(round(f + (_FN.at(x, 0, 0) - 0.5) * 3))


def crag_heights():
    n1, n2 = Noise(SEED + 22, 7.0), Noise(SEED + 23, 2.5)
    heights = {}
    for x in range(2, TX - 2):
        f = front_z(x)
        ridge = G + interp(RIDGE, x)
        zr = f - 7
        for z in range(2, f + 1):
            h = ridge - (z - zr) * 0.45 if z >= zr else ridge - (zr - z) * 0.85
            h += (n1.at(x, 0, z) - 0.5) * 5 + (n2.at(x, 3, z) - 0.5) * 2
            heights[(x, z)] = max(G + 4, int(round(h)))
    return heights


def reserved_cells():
    out = set()
    boxes = [DESCENT + (G, DESCENT_TOP + 2), HALL + (G, HALL_TOP + 2), FACADE + (G, FACADE_TOP),
             (TOMB_DOOR[0] - 1, 24, TOMB_DOOR[1] + 1, 24, G, G + 5)]
    for (x0, z0, x1, z1, y0, y1) in boxes:
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    out.add((x, y, z))
    return out


NB6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


def crag(t):
    """The ridge of black rock: a shell two cells thick over its own hollow (the placer's clearing leaves the
    inside empty), basalt columns on the faces, red strata, a crust of grit and ash on top."""
    heights = crag_heights()
    solid = set()
    for (x, z), h in heights.items():
        for y in range(G, h + 1):
            solid.add((x, y, z))
    reserved = reserved_cells()

    def open_(p):
        return p[1] >= G and p not in solid and p not in reserved

    body = solid - reserved
    layer1 = {p for p in body if any(open_((p[0] + a, p[1] + b, p[2] + c)) for a, b, c in NB6)}
    layer2 = {p for p in body - layer1 if any((p[0] + a, p[1] + b, p[2] + c) in layer1 for a, b, c in NB6)}
    rock = patches('rock', 24, 3.0)
    top = patches('rocktop', 25, 3.5)
    band = patches('band', 26, 4.0)
    bn = Noise(SEED + 27, 9.0)
    for p in sorted(layer1 | layer2):
        x, y, z = p
        if open_((x, y + 1, z)):
            s = top(x, y, z)
        else:
            off = int(bn.at(x, 0, z) * 4)
            s = band(x, y, z) if (y + off) % 8 == 0 and bn.at(x, y, z) > 0.35 else rock(x, y, z)
        t.set(x, y, z, s)
    return heights


# ---------------------------------------------------------------- the academy in the crag

def facade(t):
    """The academy's face, cut flush with the cliff like a tomb front: pilasters of polished basalt, two slit
    windows into the Hall, two blind niches lit red from their sills, the great portal with a raised
    portcullis and the Overseer's slot window above it, a corbelled cornice, banners."""
    x0, z0, x1, z1 = FACADE
    t.fill(x0, G, z0, x1, FACADE_TOP, z1, patches('ashlar', 31, 3.0))
    # plinth course and the step along the foot
    for x in range(x0, x1 + 1):
        t.set(x, G, z1, CHISELED if x % 4 == 1 else 'minecraft:polished_blackstone')
        t.set(x, F, 27, stairs(PBB_STAIRS, 'north'))
    # pilasters (proud of the face at z 27)
    for x in (31, 35, 39, 51, 55, 59):
        t.set(x, F, 27, CHISELED)
        for y in range(F + 1, FACADE_TOP - 2):
            t.set(x, y, 27, BASALT_Y)
        t.set(x, FACADE_TOP - 2, 27, CHISELED)
    # the cornice: corbels on the pilasters' line and a ledge
    for x in range(x0, x1 + 1):
        t.set(x, FACADE_TOP - 1, 27, stairs(PBB_STAIRS, 'north', 'top'))
        t.set(x, FACADE_TOP, 27, slab(PBB_SLAB))
    # slit windows into the Hall
    for x in (37, 53):
        for y in range(F + 3, F + 11):
            t.set(x, y, 26, BARS)
            t.set(x, y, 25, AIR)
            t.set(x, y, 24, AIR)
        t.set(x, F + 2, 27, stairs(PBB_STAIRS, 'north', 'top'))
    # blind niches glowing red from below
    for x in (33, 57):
        for y in range(F + 2, F + 11):
            t.set(x, y, 26, AIR)
        t.set(x, F + 1, 26, RED)
        t.set(x, F + 11, 26, stairs(PBB_STAIRS, 'north', 'top'))
    # the portal: 5 wide, 8 high, framed by stepped jambs, a lintel and the slot window above
    for x in range(43, 48):
        for y in range(F, F + 8):
            for z in range(24, 28):
                t.set(x, y, z, AIR)
        t.set(x, G, 25, 'minecraft:polished_blackstone')
        t.set(x, G, 26, 'minecraft:polished_blackstone')
        t.set(x, G, 24, 'minecraft:polished_blackstone')
        t.set(x, F + 7, 25, BARS)              # the raised portcullis
        t.set(x, F + 6, 25, BARS)
    for x in (41, 42, 48, 49):
        for y in range(F, F + 10):
            t.set(x, y, 27, BASALT_Y if x in (42, 48) else patches('ashlar', 32)(x, y, 27))
    for x in range(41, 50):
        t.set(x, F + 8, 27, CHISELED)
        t.set(x, F + 9, 27, stairs(PBB_STAIRS, 'north', 'top'))
        t.set(x, F + 10, 27, slab(PBB_SLAB))
    for y in range(F + 11, FACADE_TOP - 3):
        t.set(45, y, 26, BARS)
        t.set(45, y, 25, AIR)
        t.set(45, y, 24, AIR)
    for x in (44, 46):
        for y in range(F + 11, FACADE_TOP - 3):
            t.set(x, y, 27, stairs(PBB_STAIRS, 'east' if x == 44 else 'west'))
    # banners on the pilasters
    for x in (35, 39, 51, 55):
        banner(t, x, F + 13, 28, 'south', 'red')
        banner(t, x, F + 8, 28, 'south', 'black')


def descent(t):
    """The Descent: a tomb-brick chamber behind the tomb door where the stair to the Sepulchre opens in the
    floor (frame.py draws the well and its parapets), urns along the walls, red light from below, chains
    from the beams."""
    x0, z0, x1, z1 = DESCENT
    t.fill(x0, G, z0, x1, DESCENT_TOP + 2, z1, patches('tomb', 41, 2.5))
    t.fill(x0 + 2, F, z0 + 2, x1 - 2, DESCENT_TOP, z1 - 2, AIR)
    t.fill(x0 + 2, G, z0 + 2, x1 - 2, G, z1 - 2, patches('flag', 42, 2.0))
    # beams and pilasters
    for z in (8, 12, 16, 20):
        for x in range(x0 + 2, x1 - 1):
            t.set(x, DESCENT_TOP, z, S('polished_basalt', axis='x'))
        for x in (x0 + 2, x1 - 2):
            for y in range(F, DESCENT_TOP):
                t.set(x, y, z, BASALT_Y)
    # red light panels set in the walls' foot between the pilasters, urns beside them
    for z in (10, 14, 18):
        t.set(x0 + 1, F, z, RED)
        t.set(x1 - 1, F, z, RED)
        t.set(x0 + 2, F, z + 1, 'oldrepublic:funerary_urn')
        t.set(x1 - 2, F, z - 1, 'oldrepublic:funerary_urn')
    for x in (21, 30):
        t.set(x, F, z0 + 1, RED)
        t.set(x + (1 if x == 21 else -1), F, z0 + 2, 'oldrepublic:funerary_urn')
    # chains hanging from the beams, a few with lanterns
    for (x, z, n, lamp) in ((22, 12, 3, True), (29, 16, 4, False), (22, 20, 2, False), (29, 8, 3, True)):
        for k in range(n):
            t.set(x, DESCENT_TOP - 1 - k, z, chain())
        if lamp:
            t.set(x, DESCENT_TOP - 1 - n, z, lantern(True))
    # the tomb door through the south wall and the rock: framed outside by basalt jambs and a lintel
    a, b = TOMB_DOOR
    for x in range(a, b + 1):
        for z in (22, 23, 24):
            for y in range(F, F + 4):
                t.set(x, y, z, AIR)
            t.set(x, G, z, 'minecraft:polished_blackstone')
    for x in (a - 1, b + 1):
        for y in range(F, F + 5):
            t.set(x, y, 24, BASALT_Y)
            t.set(x, y, 25, BASALT_Y if y < F + 4 else CHISELED)
    for x in range(a - 1, b + 2):
        t.set(x, F + 4, 24, CHISELED)
        t.set(x, F + 5, 25, stairs(PBB_STAIRS, 'north', 'top'))
    for x in (a - 2, b + 2):
        t.set(x, G, 25, RED)
    # the passage east into the Hall of Trials
    for x in range(x1 - 1, HALL[0] + 2):
        for z in range(13, 16):
            for y in range(F, F + 4):
                t.set(x, y, z, AIR)
            t.set(x, G, z, 'minecraft:polished_blackstone')
        t.set(x, F + 4, 12, CHISELED)
        t.set(x, F + 4, 16, CHISELED)
    # a stone bench and an iron-bound chest of offerings (a barrel) by the west wall
    for z in (11, 12, 13):
        t.set(x0 + 3, F, z, stairs(TOMB_STAIRS, 'west'))
    t.set(x0 + 3, F, 15, barrel('up'))
    # (lit in build(), once the stair well is in)


def hall_of_trials(t):
    """The Hall of Trials: a vaulted nave with two rows of tomb pillars lit red from their feet, kneeling mats
    before the dais, the Overseer's black seat between fire bowls, records in the side niches."""
    x0, z0, x1, z1 = HALL
    t.fill(x0, G, z0, x1, HALL_TOP + 2, z1, patches('ashlar', 51, 3.0))
    for y in range(F, HALL_TOP + 1):
        inset = max(0, y - (HALL_TOP - 3))
        t.fill(x0 + 2 + inset, y, z0 + 2, x1 - 2 - inset, y, z1, AIR)
    # floor: dark flags with a polished aisle
    t.fill(x0 + 2, G, z0 + 2, x1 - 2, G, z1, patches('flag', 52, 2.2))
    for z in range(z0 + 2, z1 + 1):
        for x in range(44, 47):
            t.set(x, G, z, 'minecraft:polished_blackstone')
    # pillars, their red footlights and the vault ribs between them
    for z in (9, 13, 17, 21):
        for x in (40, 50):
            shapes.pillar(t, x, F, HALL_TOP - 3, z, S('oldrepublic:tomb_pillar', axis='y'), base=CHISELED,
                          capital=CHISELED)
            t.set(x + (1 if x == 40 else -1), G, z, RED)
        for x in range(x0 + 2, x1 - 1):
            if t.is_air(x, HALL_TOP - 2, z):
                t.set(x, HALL_TOP - 2, z, stairs(PBB_STAIRS, 'south', 'top') if z % 2 else
                      stairs(PBB_STAIRS, 'north', 'top'))
    # the dais and the seat
    for x in range(41, 50):
        for z in range(z0 + 2, z0 + 6):
            t.set(x, F, z, 'minecraft:polished_blackstone_bricks')
        t.set(x, F, z0 + 6, stairs(PBB_STAIRS, 'north'))
    t.set(45, F + 1, z0 + 3, stairs('minecraft:polished_blackstone_stairs', 'north'))
    t.set(44, F + 1, z0 + 3, wall_block(DARK_WALL))
    t.set(46, F + 1, z0 + 3, wall_block(DARK_WALL))
    for y in range(F + 1, F + 4):
        t.set(45, y, z0 + 2, 'minecraft:polished_blackstone')
    t.set(45, F + 4, z0 + 2, CHISELED)
    brazier(t, 42, F + 1, z0 + 4)
    brazier(t, 48, F + 1, z0 + 4)
    for x in (41, 49):
        t.set(x, F + 1, z0 + 2, candle(3, 'red'))
    banner(t, 43, F + 8, z0 + 2, 'south', 'red')
    banner(t, 47, F + 8, z0 + 2, 'south', 'red')
    banner(t, 45, F + 10, z0 + 2, 'south', 'black')
    # kneeling mats before the dais
    for z in range(13, 22, 2):
        for x in (42, 43, 47, 48):
            t.set(x, F, z, 'minecraft:red_carpet' if (x + z) % 3 else 'minecraft:black_carpet')
    # niches in the side walls: records (shelves and lecterns) west, racks and urns east
    for z in (11, 15, 19):
        for y in range(F, F + 4):
            t.set(x0 + 1, y, z, AIR)
            t.set(x1 - 1, y, z, AIR)
        t.set(x0 + 1, F + 4, z, stairs(PBB_STAIRS, 'east', 'top'))
        t.set(x1 - 1, F + 4, z, stairs(PBB_STAIRS, 'west', 'top'))
        t.set(x0 + 1, F, z, 'minecraft:bookshelf')
        t.set(x0 + 1, F + 1, z, 'minecraft:bookshelf' if z != 15 else 'minecraft:chiseled_bookshelf[facing=east,slot_0_occupied=false,slot_1_occupied=false,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]')
        t.set(x1 - 1, F, z, 'oldrepublic:funerary_urn')
        t.set(x1 - 1, F + 1, z, candle(2, 'black'))
    mod.lectern(t, x0 + 3, F, 15, 'east', book=True)
    # lanterns on chains over the nave
    for z in (11, 19):
        hang_lantern(t, 45, HALL_TOP - 4, z, HALL_TOP + 1)
    # a few acolytes' things: a dropped practice blade's rack, a toppled stool
    t.set(x1 - 3, F, 22, stairs('minecraft:dark_oak_stairs', 'east'))
    mod.hub_spawner(t, 45, F, 16, 'acolyte')
    mod.light_fill(t, region=(x0 + 2, F, z0 + 2, x1 - 2, HALL_TOP, z1))


def spire(t, heights):
    """The Overseer's spire on the ridge above the Hall: a black needle in three stages with slit windows lit
    red inside, corbelled cornices, a pyramidion and an iron spike. The skyline's landmark."""
    x0, z0 = 42, 11
    base = min(heights.get((x, z), G + 20) for x in range(x0, x0 + 7) for z in range(z0, z0 + 7)) - 1
    base = min(base, HALL_TOP + 3)
    ash = patches('ashlar', 61, 2.5)
    s0, s1, s2 = 39, 46, 50                  # the tops of the three stages
    stages = [(0, base, s0, 7), (1, s0 + 1, s1, 5), (2, s1 + 1, s2, 3)]
    for (inset, y0, y1, w) in stages:
        xa, za = x0 + inset, z0 + inset
        xb, zb = xa + w - 1, za + w - 1
        for yy in range(y0, y1 + 1):
            for x in range(xa, xb + 1):
                for z in range(za, zb + 1):
                    if x in (xa, xb) or z in (za, zb) or yy in (y0, y1):
                        corner = x in (xa, xb) and z in (za, zb)
                        t.set(x, yy, z, BASALT_Y if corner and w > 3 else ash(x, yy, z))
        for x in range(xa - 1, xb + 2):
            for z in range(za - 1, zb + 2):
                if x in (xa - 1, xb + 1) or z in (za - 1, zb + 1):
                    if z == za - 1:
                        facing = 'south'
                    elif z == zb + 1:
                        facing = 'north'
                    else:
                        facing = 'east' if x == xa - 1 else 'west'
                    t.set(x, y1, z, stairs(PBB_STAIRS, facing, 'top'))
    # slit windows lit red from inside: four on the lower stage, four on the middle one
    cx, cz = x0 + 3, z0 + 3
    for (wx, wz, ix, iz) in ((cx, z0, cx, z0 + 1), (cx, z0 + 6, cx, z0 + 5), (x0, cz, x0 + 1, cz),
                             (x0 + 6, cz, x0 + 5, cz)):
        for yy in range(s0 - 7, s0 - 1):
            t.set(wx, yy, wz, BARS)
        t.set(ix, s0 - 8, iz, RED)
    for (wx, wz, ix, iz) in ((cx, z0 + 1, cx, z0 + 2), (cx, z0 + 5, cx, z0 + 4), (x0 + 1, cz, x0 + 2, cz),
                             (x0 + 5, cz, x0 + 4, cz)):
        for yy in range(s1 - 4, s1 - 1):
            t.set(wx, yy, wz, BARS)
        t.set(ix, s0 + 1, iz, RED)
    # the pyramidion and the spike
    t.set(cx, s2 + 1, cz, 'minecraft:polished_blackstone')
    for (dx, dz, f) in ((0, -1, 'south'), (0, 1, 'north'), (-1, 0, 'east'), (1, 0, 'west')):
        t.set(cx + dx, s2 + 1, cz + dz, stairs('minecraft:polished_blackstone_stairs', f))
    for yy in range(s2 + 2, s2 + 5):
        t.set(cx, yy, cz, BARS)
    # red light from below at the spire's foot
    for (x, z) in ((x0 - 1, z0 - 1), (x0 + 7, z0 - 1), (x0 - 1, z0 + 7), (x0 + 7, z0 + 7)):
        h = heights.get((x, z))
        if h is not None and h < base + 4:
            t.set(x, h, z, RED)


# ---------------------------------------------------------------- the forecourt and the arena

def needle(t, x, z, h):
    """A basalt needle on a plinth whose corners glow red."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            t.set(x + dx, F, z + dz, RED if dx and dz else 'minecraft:polished_blackstone_bricks')
    t.set(x, F + 1, z, CHISELED)
    for y in range(F + 2, F + h):
        t.set(x, y, z, BASALT_Y)
    t.set(x, F + h, z, 'minecraft:polished_blackstone')
    t.set(x, F + h + 1, z, BARS)


def forecourt(t):
    """The parade before the academy: black flags from the crag's foot to the arena, the rostrum where the
    Overseer addresses the acolytes, basalt needles lit red at their feet, fire bowls, benches facing the
    arena, the contract board under the facade."""
    flag = patches('flag', 71, 2.6)
    for x in range(14, 63):
        for z in range(front_z(x) + 1, 37):
            if z >= 29 and x < 28:
                continue
            t.set(x, G, z, flag(x, G, z))
    # the rostrum
    for x in range(43, 48):
        for z in range(29, 32):
            for y in range(G, F + 2):
                t.set(x, y, z, 'minecraft:polished_blackstone_bricks' if (x + y + z) % 5 else CHISELED)
            t.set(x, F + 2, z, 'minecraft:polished_blackstone')
        t.set(x, F + 3, 29, wall_block(DARK_WALL))
    for z in (30, 31):
        t.set(43, F + 3, z, wall_block(DARK_WALL))
        t.set(47, F + 3, z, wall_block(DARK_WALL))
    for x in range(44, 47):
        for i, z in enumerate((32, 33, 34)):
            y = F + 2 - i
            for yy in range(G, y):
                t.set(x, yy, z, 'minecraft:polished_blackstone_bricks')
            t.set(x, y, z, stairs(PBB_STAIRS, 'north'))
            for k in range(1, 4):
                t.set(x, y + k, z, AIR)
    for x in (43, 47):
        for i, z in enumerate((32, 33)):
            for yy in range(G, F + 2 - i):
                t.set(x, yy, z, 'minecraft:polished_blackstone_bricks')
            t.set(x, F + 2 - i, z, slab(PBB_SLAB))
    mod.hub_spawner(t, *HOST, 'host', anchor='HOST')
    for x in (42, 48):
        for y in range(F, F + 7):
            t.set(x, y, 31, wall_block(DARK_WALL))
        t.set(x, F + 7, 31, lantern(False))
        banner(t, x, F + 5, 32, 'south', 'red')
    # needles and fire bowls
    needle(t, 34, 31, 12)
    needle(t, 56, 31, 12)
    for (x, z) in ((40, 29), (50, 29), (40, 35), (50, 35)):
        brazier(t, x, F, z)
    # benches facing the arena
    for x in list(range(34, 42)) + list(range(49, 57)):
        if x not in (37, 53):
            t.set(x, F, 36, stairs(PBB_STAIRS, 'north'))
    # the contract board under the facade, with a little canopy between the pilasters
    t.set(57, F, 27, S('oldrepublic:contract_terminal', facing='south'))
    t.set(57, F + 1, 27, AIR)
    t.anchor('CONTRACT_TERMINAL', 57, F, 27)
    for x in range(56, 59):
        for z in (27, 28):
            t.set(x, F + 3, z, slab(PBB_SLAB, 'top'))
    t.set(57, F + 2, 28, lantern(True))
    t.set(56, F, 27, barrel('up'))


def arena(t):
    """The proving ground: a pit four deep walled in black brick, stairs down at both ends, red light set in
    the foot of its walls, fire bowls on the corners, practice posts, scorched sand-coloured earth. The four
    acolytes fight here."""
    x0, z0, x1, z1 = 33, 38, 57, 51
    ash = patches('ashlar', 81, 2.5)
    pit = Palette(p=[('minecraft:coarse_dirt', 5), ('minecraft:packed_mud', 3), ('minecraft:smooth_basalt', 1),
                     ('minecraft:terracotta', 1)]).patches('p', seed=SEED + 82, scale=2.0)
    t.fill(x0 + 1, 1, z0 + 1, x1 - 1, F + 3, z1 - 1, AIR)
    t.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, pit)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                for y in range(0, G):
                    t.set(x, y, z, ash(x, y, z))
                t.set(x, G, z, CHISELED if (x + z) % 6 == 0 else 'minecraft:polished_blackstone')
                gap = 43 <= x <= 47 and z in (z0, z1)
                corner = x in (x0, x1) and z in (z0, z1)
                if not gap and not corner:
                    t.set(x, F, z, wall_block(DARK_WALL))
    # red light in the foot of the walls
    for x in range(x0 + 3, x1 - 1, 4):
        t.set(x, 1, z0, RED)
        t.set(x, 1, z1, RED)
    for z in range(z0 + 3, z1 - 1, 4):
        t.set(x0, 1, z, RED)
        t.set(x1, 1, z, RED)
    # stairs down at both ends
    for x in range(44, 47):
        for i in range(4):
            for (z, facing) in ((z0 + i, 'north'), (z1 - i, 'south')):
                y = G - i
                t.set(x, y, z, stairs(PBB_STAIRS, facing))
                for yy in range(0, y):
                    t.set(x, yy, z, 'minecraft:polished_blackstone_bricks')
                for k in range(1, 4):
                    if y + k < G + 1 or not t.get(x, y + k, z):
                        t.set(x, y + k, z, AIR)
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        brazier(t, x, F, z)
    # practice posts and the fighters
    for (x, z) in ((36, 41), (36, 48), (54, 41), (54, 48)):
        t.set(x, 1, z, BASALT_Y)
        t.set(x, 2, z, BASALT_Y)
        t.set(x, 3, z, chain())
    for i, (x, z) in enumerate(((40, 43), (40, 46), (50, 43), (50, 46))):
        mod.hub_spawner(t, x, 1, z, 'acolyte', anchor='RESIDENT_%d' % (i + 1))
    rng = t.rng
    for _ in range(14):
        x, z = rng.randint(x0 + 1, x1 - 1), rng.randint(z0 + 1, z1 - 1)
        if t.is_air(x, 1, z) and t.get(x, 1, z) == AIR and t.name_at(x, 0, z) != 'minecraft:polished_blackstone_bricks':
            t.set(x, 1, z, 'oldrepublic:scorch_mark')


# ---------------------------------------------------------------- walls, gates, towers

WALL_TOP = G + 6        # the wall walk (y 10); merlons above


def wall_cells():
    """(x, z) of the curtain wall: west and east from the crag to the south wall, and the south wall."""
    cells = set()
    for x in (2, 3, TX - 4, TX - 3):
        for z in range(front_z(x) + 1, TZ - 2):
            cells.add((x, z))
    for z in (TZ - 4, TZ - 3):
        for x in range(2, TX - 2):
            cells.add((x, z))
    return cells


def in_gate(x, z):
    if x in (2, 3, TX - 4, TX - 3) and 53 <= z <= 56:
        return True
    return z in (TZ - 4, TZ - 3) and 43 <= x <= 47


def curtain(t):
    """The curtain wall: black brick on a blackstone footing, a walk on top with merlons, pilasters inside,
    lamps on the inner face."""
    ash = patches('ashlar', 91, 3.0)
    plinth = patches('plinth', 92, 3.0)
    for (x, z) in sorted(wall_cells()):
        outer = x in (2, TX - 3) or z == TZ - 3
        if in_gate(x, z):
            t.set(x, G, z, 'minecraft:polished_blackstone')
            for y in range(F, WALL_TOP):
                t.set(x, y, z, AIR)
            t.set(x, WALL_TOP, z, ash(x, WALL_TOP, z))
            continue
        t.set(x, G - 1, z, plinth(x, G - 1, z))
        t.set(x, G, z, plinth(x, G, z))
        for y in range(F, WALL_TOP + 1):
            t.set(x, y, z, ash(x, y, z))
        if outer and (x + z) % 2 == 0:
            t.set(x, WALL_TOP + 1, z, 'minecraft:polished_blackstone_bricks')
            t.set(x, WALL_TOP + 2, z, slab(PBB_SLAB))
    # pilasters on the inner face every 8, and lamps between
    for z in range(20, TZ - 4, 8):
        for x in (4, TX - 5):
            if not (49 <= z <= 60) and t.is_air(x, F, z):
                for y in range(F, WALL_TOP):
                    t.set(x, y, z, 'minecraft:polished_blackstone_bricks')
                t.set(x, WALL_TOP, z, slab(PBB_SLAB))
    for x in range(10, TX - 10, 8):
        if not (37 <= x <= 53) and t.is_air(x, F, TZ - 5):
            for y in range(F, WALL_TOP):
                t.set(x, y, TZ - 5, 'minecraft:polished_blackstone_bricks')
            t.set(x, WALL_TOP, TZ - 5, slab(PBB_SLAB))


def tower(t, x0, z0, x1, z1, top, crown=True, light=False):
    """A closed tower of black brick on the wall line: corner pilasters, a corbelled crown, a searchlight."""
    ash = patches('ashlar', 93 + x0 + z0, 2.5)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set(x, G - 1, z, 'minecraft:blackstone')
            if edge:
                corner = x in (x0, x1) and z in (z0, z1)
                for y in range(G, top + 1):
                    t.set(x, y, z, BASALT_Y if corner else ash(x, y, z))
            t.set(x, top, z, ash(x, top, z))
    if crown:
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                    if 0 <= x < TX and 0 <= z < TZ and not (x < 2 or z < 2 or x > TX - 3 or z > TZ - 3):
                        if z == z0 - 1:
                            f = 'south'
                        elif z == z1 + 1:
                            f = 'north'
                        else:
                            f = 'east' if x == x0 - 1 else 'west'
                        t.set(x, top, z, stairs(PBB_STAIRS, f, 'top'))
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x in (x0, x1) or z in (z0, z1)) and (x + z) % 2 == 0:
                    t.set(x, top + 1, z, wall_block(DARK_WALL))
    if light:
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        t.set(cx, top + 1, cz, 'minecraft:sea_lantern')
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            t.set(cx + dx, top + 1, cz + dz, BARS)
        for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            t.set(cx + dx, top + 1, cz + dz, wall_block(DARK_WALL))
            t.set(cx + dx, top + 2, cz + dz, wall_block(DARK_WALL))
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                t.set(cx + dx, top + 3, cz + dz, slab(PBB_SLAB))


def gates(t):
    """Three gates: towers either side, a lintel with the walk over it, a raised portcullis, red banners on
    the outer faces, fire bowls inside."""
    top = G + 11
    # west and east
    for (xa, xb, outx, face) in ((2, 6, 1, 'west'), (TX - 7, TX - 3, TX - 2, 'east')):
        for (za, zb) in ((49, 52), (57, 60)):
            tower(t, xa, za, xb, zb, top)
            for z in (za + 1, zb - 1):
                banner(t, outx, F + 5, z, face, 'red')
        wx = (2, 3) if face == 'west' else (TX - 4, TX - 3)
        for x in wx:
            for z in range(53, 57):
                for y in range(WALL_TOP, top + 1):
                    t.set(x, y, z, patches('ashlar', 95)(x, y, z))
                t.set(x, WALL_TOP - 1, z, BARS)
        inner = 7 if face == 'west' else TX - 8
        for z in (52, 57):
            brazier(t, inner, F, z)
    # south
    for (xa, xb) in ((38, 42), (48, 52)):
        tower(t, xa, TZ - 7, xb, TZ - 3, top)
        for x in (xa + 1, xb - 1):
            banner(t, x, F + 5, TZ - 2, 'south', 'red')
    for z in (TZ - 4, TZ - 3):
        for x in range(43, 48):
            for y in range(WALL_TOP, top + 1):
                t.set(x, y, z, patches('ashlar', 96)(x, y, z))
            t.set(x, WALL_TOP - 1, z, BARS)
    for x in (42, 48):
        brazier(t, x, F, TZ - 8)
    # corner towers with searchlights
    tower(t, 2, TZ - 9, 8, TZ - 3, G + 13, light=True)
    tower(t, TX - 9, TZ - 9, TX - 3, TZ - 3, G + 13, light=True)


# ---------------------------------------------------------------- the west quarter

def barracks(t):
    """The acolytes' barracks: two storeys of ash brick on a black footing, bunks down both walls, a long
    table and lockers below, more bunks above, a stair along the north wall, a flat roof with vents."""
    x0, z0, x1, z1 = 5, 29, 18, 44
    wall = patches('town', 101, 3.0)
    shell_box(t, x0, z0, x1, z1, G, G + 9, wall, floor=patches('tiles', 102, 2.5),
              corner='minecraft:polished_blackstone_bricks', plinth=patches('plinth', 103))
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            t.set(x, G + 5, z, 'minecraft:polished_blackstone_bricks')
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            t.set(x, G + 5, z, 'minecraft:polished_blackstone_bricks')
    t.fill(x0 + 1, G + 5, z0 + 1, x1 - 1, G + 5, z1 - 1, 'minecraft:dark_oak_planks')
    t.fill(x0, G + 10, z0, x1, G + 10, z1, patches('tiles', 104, 2.5))
    parapet(t, x0, z0, x1, z1, G + 11, DARK_WALL)
    windows(t, x0, z0, x1, z1, F + 1, F + 2, every=3, skip=(35, 36, 37, 38))
    windows(t, x0, z0, x1, z1, G + 7, G + 8, every=3)
    # the door (east, onto the alley by the Overseer's house) with a lintel and a lamp
    opening(t, x1, F, 36, x1, F + 2, 37)
    t.set(x1, F + 3, 36, CHISELED)
    t.set(x1, F + 3, 37, CHISELED)
    bracket_lamp(t, x1, F + 3, 35, 'east', arm=DARK_WALL)
    # ground floor: bunks, the long table, lockers, the stair
    for z in (31, 33, 35, 37, 39, 41, 43):
        bed(t, x0 + 2, F, z, 'west', 'red')
    for z in (31, 33, 40, 42):
        bed(t, x1 - 2, F, z, 'east', 'black')
    for z in range(33, 41):
        t.set(11, F, z, slab('dark_oak_slab', 'top'))
        t.set(12, F, z, slab('dark_oak_slab', 'top'))
        if z % 2:
            t.set(10, F, z, stairs('dark_oak_stairs', 'west'))
            t.set(13, F, z, stairs('dark_oak_stairs', 'east'))
    t.set(11, F + 1, 34, candle(2))
    t.set(12, F + 1, 39, 'minecraft:flower_pot')
    for x in range(8, 11):
        t.set(x, F, z0 + 1, barrel('south'))
    t.fill(13, G + 5, z0 + 1, 16, G + 5, z0 + 1, AIR)
    shapes.stair_run(t, 16, F, z0 + 1, 'west', 5, 'minecraft:dark_oak_stairs')
    for x in range(13, 17):
        t.set(x, G + 6, z0 + 2, fence('dark_oak_fence'))
    hang_lantern(t, 8, F + 2, 36, G + 5)
    hang_lantern(t, 15, F + 2, 37, G + 5)
    mod.hub_spawner(t, 8, F, 38, 'acolyte')
    # upper floor: bunks and footlockers
    for z in (32, 34, 36, 38, 40, 42):
        bed(t, x0 + 2, G + 6, z, 'west', 'red')
        t.set(x0 + 3, G + 6, z + 1, barrel('up'))
    for z in (34, 36, 38, 40, 42):
        bed(t, x1 - 2, G + 6, z, 'east', 'black')
    hang_lantern(t, 11, G + 8, 37, G + 10)
    # the roof: vents and an aerial
    for (x, z) in ((8, 33), (15, 40)):
        t.set(x, G + 11, z, 'oldrepublic:durasteel_vent')
    for y in range(G + 11, G + 15):
        t.set(16, y, 31, BARS)
    t.set(16, G + 15, 31, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    mod.light_fill(t, region=(x0 + 1, F, z0 + 1, x1 - 1, G + 4, z1 - 1))
    mod.light_fill(t, region=(x0 + 1, G + 6, z0 + 1, x1 - 1, G + 9, z1 - 1))


def overseer_house(t):
    """The Overseer's house: a narrow tower of black brick by the lane, a study below (shelves, desk, a
    screen), a bedroom above, a meditation room with a window to the crag at the top, a hipped roof."""
    x0, z0, x1, z1 = 22, 29, 27, 40
    wall = patches('ashlar', 111, 2.5)
    shell_box(t, x0, z0, x1, z1, G, G + 14, wall, floor=patches('tiles', 112, 2.0), corner=BASALT_Y,
              plinth=patches('plinth', 113))
    for y in (G + 5, G + 10):
        t.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, 'minecraft:dark_oak_planks')
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                t.set(x, y, z, CHISELED)
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                t.set(x, y, z, CHISELED)
    shapes.hip_roof(t, x0 - 1, z0 - 1, x1 + 1, z1 + 1, G + 15, TILE_STAIRS, body='minecraft:deepslate_tiles')
    windows(t, x0, z0, x1, z1, F + 1, F + 2, every=3, state=PANE, sides=('north', 'south', 'west'))
    windows(t, x0, z0, x1, z1, G + 7, G + 8, every=3, state=PANE)
    windows(t, x0, z0, x1, z1, G + 12, G + 13, every=2, state=PANE, sides=('north', 'south'))
    opening(t, x1, F, 34, x1, F + 2, 35)
    door(t, x1, F, 34, 'east', wood='dark_oak', hinge='left')
    door(t, x1, F, 35, 'east', wood='dark_oak', hinge='right')
    t.set(x1, F + 2, 34, BARS)
    t.set(x1, F + 2, 35, BARS)
    bracket_lamp(t, x1, F + 3, 33, 'east', arm=DARK_WALL)
    # study
    for z in range(z0 + 1, z0 + 6):
        t.set(x0 + 1, F, z, 'minecraft:bookshelf')
        t.set(x0 + 1, F + 1, z, 'minecraft:bookshelf')
    t.set(x0 + 3, F, 32, slab('polished_blackstone_slab', 'top'))
    t.set(x0 + 4, F, 32, slab('polished_blackstone_slab', 'top'))
    t.set(x0 + 3, F + 1, 32, candle(1, 'red'))
    chair(t, x0 + 3, F, 33, 'south', wood='dark_oak')
    t.set(x0 + 2, F + 1, z0, S('oldrepublic:holo_screen', facing='south'))
    mod.lectern(t, x0 + 4, F, 37, 'north', book=True)
    t.set(x0 + 2, F, 39, potted(t.rng, ('potted_dead_bush', 'potted_wither_rose')))
    # the stair to the bedroom along the west wall, then the east wall
    t.fill(x0 + 1, G + 5, 35, x0 + 1, G + 5, 38, AIR)
    shapes.stair_run(t, x0 + 1, F, 38, 'north', 5, 'minecraft:dark_oak_stairs')
    t.fill(x1 - 1, G + 10, 31, x1 - 1, G + 10, 34, AIR)
    shapes.stair_run(t, x1 - 1, G + 6, 31, 'south', 5, 'minecraft:dark_oak_stairs')
    # bedroom
    bed(t, x0 + 3, G + 6, 31, 'north', 'black')
    t.set(x0 + 2, G + 6, 30, barrel('up'))
    for z in (36, 37):
        t.set(x0 + 3, G + 6, z, 'minecraft:red_carpet')
        t.set(x0 + 4, G + 6, z, 'minecraft:red_carpet')
    t.set(x1 - 1, G + 6, 39, barrel('west'))
    t.set(x1 - 1, G + 7, 39, barrel('west'))
    # meditation room
    for z in (36, 37):
        t.set(x0 + 2, G + 11, z, 'minecraft:black_carpet')
        t.set(x0 + 3, G + 11, z, 'minecraft:black_carpet')
    t.set(x0 + 1, G + 11, 39, candle(4, 'red'))
    t.set(x1 - 1, G + 11, 39, candle(3, 'black'))
    for z in (30, 39):
        hang_lantern(t, x0 + 3, G + 13, z, G + 15)
    hang_lantern(t, x0 + 3, F + 3, 35, G + 5)
    hang_lantern(t, x0 + 3, G + 8, 34, G + 10)
    for (y0, y1) in ((F, G + 4), (G + 6, G + 9), (G + 11, G + 14)):
        mod.light_fill(t, region=(x0 + 1, y0, z0 + 1, x1 - 1, y1, z1 - 1))


def training_yard(t):
    """The yard by the barracks: practice posts, a weapon rack, a trough, benches."""
    pave(t, 16, 46, 27, 52, 'road', 121, 2.4, ragged=0.4)
    for (x, z) in ((18, 48), (21, 48), (24, 48), (18, 51), (24, 51)):
        t.set(x, F, z, BASALT_Y)
        t.set(x, F + 1, z, BASALT_Y)
        t.set(x, F + 2, z, chain())
        t.set(x, F + 3, z, chain())
        t.set(x, F + 4, z, wall_block(DARK_WALL))
    for x in range(19, 24):
        t.set(x, F, 46, fence('dark_oak_fence'))
        t.set(x, F + 1, 46, BARS if x % 2 else fence('dark_oak_fence'))
    t.set(26, F, 47, 'minecraft:water_cauldron[level=3]')
    t.set(26, F, 48, 'minecraft:cauldron')
    for x in (16, 17):
        t.set(x, F, 50, stairs(PBB_STAIRS, 'west'))
    post_lamp(t, 27, F, 52, 3)


def officers_house(t):
    """A small house for the drill officers: two bunks, a table, a stove, lockers."""
    x0, z0, x1, z1 = 8, 46, 14, 51
    wall = patches('town', 131, 2.5)
    shell_box(t, x0, z0, x1, z1, G, G + 5, wall, floor='minecraft:dark_oak_planks',
              corner='minecraft:polished_blackstone_bricks', plinth=patches('plinth', 132))
    shapes.gable_roof(t, x0, z0, x1, z1, G + 6, TILE_STAIRS, body='minecraft:deepslate_tiles', ridge='x', overhang=1,
                      gable=wall)
    windows(t, x0, z0, x1, z1, F + 1, F + 2, every=3, state=PANE, sides=('north', 'west'))
    opening(t, x1, F, 48, x1, F + 2, 49)
    bed(t, x0 + 1, F, 47, 'south', 'gray')
    bed(t, x0 + 1, F, 50, 'north', 'gray')
    t.set(x0 + 3, F, 48, slab('dark_oak_slab', 'top'))
    chair(t, x0 + 3, F, 49, 'south', wood='dark_oak')
    t.set(x0 + 5, F, 47, 'minecraft:smoker[facing=south,lit=true]')
    t.set(x0 + 5, F, 50, barrel('north'))
    hang_lantern(t, x0 + 3, F + 3, 48, G + 6)
    mod.light_fill(t, region=(x0 + 1, F, z0 + 1, x1 - 1, G + 4, z1 - 1))


# ---------------------------------------------------------------- the east quarter

def attic_air(t, x0, z0, x1, z1, y0, y1):
    """Explicit air under a hipped roof (so the hall below rises into it), only where nothing is set."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, y, z) not in t.cells:
                    t.set(x, y, z, AIR)


def cantina(t):
    """The cantina, the garrison's mess: a tall hall of ash brick under a hipped tile roof, a crimson awning
    over the door, a bar of black stone with red light under its stools, tables, a kitchen corner, a droid
    serving."""
    x0, z0, x1, z1 = 64, 28, 85, 41
    wall = patches('town', 141, 3.0)
    shell_box(t, x0, z0, x1, z1, G, G + 7, wall, floor=patches('tiles', 142, 2.2),
              corner='minecraft:polished_blackstone_bricks', plinth=patches('plinth', 143))
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            t.set(x, G + 7, z, 'minecraft:polished_blackstone_bricks')
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            t.set(x, G + 7, z, 'minecraft:polished_blackstone_bricks')
    shapes.hip_roof(t, x0 - 1, z0 - 1, x1 + 1, z1 + 1, G + 8, TILE_STAIRS)
    attic_air(t, x0 + 1, z0 + 1, x1 - 1, z1 - 1, G + 8, G + 14)
    windows(t, x0, z0, x1, z1, F + 1, F + 3, every=4, state=PANE, sides=('north', 'south'))
    # the door and its awning
    opening(t, x0, F, 33, x0, F + 2, 35)
    for z in range(32, 37):
        t.set(x0 - 1, F + 3, z, 'oldrepublic:canvas_crimson')
        t.set(x0 - 2, F + 3, z, 'oldrepublic:canvas_crimson' if z in (32, 36) else slab('oldrepublic:canvas_slab'))
    for z in (32, 36):
        for y in range(F, F + 3):
            t.set(x0 - 2, y, z, wall_block(DARK_WALL))
    t.set(x0 - 1, F + 2, 34, lantern(True))
    # the bar
    for z in range(31, 39):
        t.set(79, F, z, 'oldrepublic:imperial_plating' if z % 3 else 'minecraft:polished_blackstone_bricks')
        chair(t, 78, F, z, 'west', wood='dark_oak')
        t.set(78, G, z, RED)
    for x in (80, 81):
        t.set(x, F, 31, 'oldrepublic:imperial_plating')
        t.set(x, F, 38, 'oldrepublic:imperial_plating')
    for z in range(30, 40):
        t.set(84, F, z, barrel('west') if z % 3 else 'minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]')
        t.set(84, F + 1, z, potted(t.rng, ('potted_dead_bush', 'potted_red_mushroom', 'potted_crimson_fungus')) if z % 4 == 0
              else 'minecraft:air')
    t.set(80, F + 1, 31, candle(3, 'red'))
    t.set(81, F + 1, 38, 'minecraft:decorated_pot[cracked=false,facing=north,waterlogged=false]')
    mod.hub_spawner(t, 82, F, 35, 'droid')
    # the kitchen corner
    t.set(84, F, 29, 'minecraft:smoker[facing=west,lit=true]')
    t.set(83, F, 29, 'minecraft:furnace[facing=south,lit=true]')
    t.set(82, F, 29, 'minecraft:water_cauldron[level=2]')
    t.set(81, F, 29, barrel('up'))
    # tables
    for (tx, tz) in ((68, 31), (68, 37), (73, 31), (73, 37), (70, 34)):
        t.set(tx, F, tz, slab('dark_oak_slab', 'top'))
        t.set(tx + 1, F, tz, slab('dark_oak_slab', 'top'))
        chair(t, tx - 1, F, tz, 'west', wood='dark_oak')
        chair(t, tx + 2, F, tz, 'east', wood='dark_oak')
        if t.rng.random() < 0.6:
            t.set(tx, F + 1, tz, candle(1, 'red'))
    t.set(69, F + 1, 37, 'minecraft:flower_pot')
    for (x, z) in ((70, 31), (76, 35), (70, 38)):
        hang_lantern(t, x, F + 4, z, G + 9)
    mod.hub_spawner(t, 71, F, 36, 'acolyte')
    mod.hub_spawner(t, 75, F, 33, 'guard')
    # a game left on a side table: a toppled stool, a dropped bowl's worth of mess (a candle burnt out)
    t.set(66, F, 39, stairs('dark_oak_stairs', 'north', 'top'))
    t.set(66, F, 29, barrel('east'))
    t.set(67, F, 29, barrel('up'))
    mod.light_fill(t, region=(x0 + 1, F, z0 + 1, x1 - 1, G + 7, z1 - 1))


def med_bay(t):
    """The med bay: a white-lit ward of imperial plating, four beds with monitors, the bacta tank, medicine
    cabinets."""
    x0, z0, x1, z1 = 64, 44, 82, 52
    wall = patches('plate', 151, 2.5)
    shell_box(t, x0, z0, x1, z1, G, G + 5, wall, floor='minecraft:smooth_stone',
              corner='minecraft:polished_blackstone_bricks', plinth=patches('plinth', 152))
    t.fill(x0, G + 6, z0, x1, G + 6, z1, patches('tiles', 153))
    parapet(t, x0, z0, x1, z1, G + 7, ASH_WALL)
    for x in range(x0 + 2, x1 - 1, 4):
        for z in (46, 50):
            t.set(x, G + 6, z, PANEL)
    windows(t, x0, z0, x1, z1, F + 1, F + 2, every=4, state=PANE, sides=('north',))
    opening(t, x0, F, 47, x0, F + 2, 48)
    t.set(x0 - 1, F + 3, 47, slab(PLATE_SLAB, 'top'))
    t.set(x0 - 1, F + 3, 48, slab(PLATE_SLAB, 'top'))
    bracket_lamp(t, x0, F + 3, 49, 'west', arm=DARK_WALL)
    for x in (67, 70, 73, 76):
        bed(t, x, F, 46, 'north', 'white')
        t.set(x + 1, F, 45, S('oldrepublic:console', facing='south'))
    # the bacta tank
    for y in range(F, F + 3):
        t.set(80, y, 48, 'minecraft:water')
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            t.set(80 + dx, y, 48 + dz, 'minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]')
    t.set(80, G, 48, PANEL)
    t.set(80, F + 3, 48, 'oldrepublic:imperial_plating')
    for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        for y in range(F, F + 4):
            t.set(80 + dx, y, 48 + dz, 'oldrepublic:imperial_plating' if y in (F, F + 3) else 'minecraft:iron_bars')
    t.set(78, F, 51, S('oldrepublic:holo_screen', facing='north'))
    for x in (66, 67, 68, 71, 72):
        t.set(x, F, 51, barrel('north') if x % 2 else 'minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=true,has_bottle_2=false]')
    t.set(70, F, 51, 'minecraft:water_cauldron[level=3]')
    mod.spot(t, 'MEDBAY', 73, F, 49)
    mod.hub_spawner(t, 77, F, 49, 'droid')
    mod.light_fill(t, region=(x0 + 1, F, z0 + 1, x1 - 1, G + 5, z1 - 1))


# ---------------------------------------------------------------- streets and the landing field

def streets(t):
    pave(t, 2, 53, TX - 3, 56, 'road', 161, 2.8)
    pave(t, 28, 37, 32, 52, 'road', 162, 2.8)
    pave(t, 58, 37, 62, 52, 'road', 163, 2.8)
    pave(t, 19, 29, 21, 45, 'road', 164, 2.4, ragged=0.3)
    pave(t, 4, 57, 30, 60, 'road', 165, 2.6, ragged=0.3)
    pave(t, 61, 61, 69, 62, 'road', 166, 2.6, ragged=0.3)
    pave(t, 43, 84, 47, 86, 'road', 167, 2.6)


def landing_field(t):
    """The landing field: a deck of dark basalt and deepslate, four bays of lighter deck 11 x 11 kept open to
    the sky, blast walls with lamps down both sides, kerbs at the ends, cargo waiting."""
    x0, z0, x1, z1 = 31, 57, 59, 83
    t.fill(x0, G, z0, x1, G, z1, patches('deck', 171, 2.2))
    for i, (bx, bz) in enumerate(BAYS):
        t.fill(bx - BAY_R, F, bz - BAY_R, bx + BAY_R, F + 15, bz + BAY_R, AIR)
        t.fill(bx - BAY_R, G, bz - BAY_R, bx + BAY_R, G, bz + BAY_R, patches('bay', 172 + i, 2.0))
        mod.spot(t, 'PAD_BAY_%d' % (i + 1), bx, F, bz)
        # scorched patches in the deck itself (nothing may stand on a bay: a landing hull must fit)
        for (dx, dz) in ((2, 1), (-1, -3), (-3, 2)):
            if t.rng.random() < 0.7:
                t.set(bx + dx, G, bz + dz, 'minecraft:smooth_basalt')
                t.set(bx + dx + 1, G, bz + dz, 'minecraft:polished_blackstone')
    mod.spot(t, 'HEART', *HEART)
    plate = patches('plate', 176, 2.0)
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            if 69 <= z <= 71 or (x == x1 and 64 <= z <= 68):
                continue
            t.set(x, F, z, plate(x, F, z))
            t.set(x, F + 1, z, 'minecraft:polished_blackstone_bricks')
            t.set(x, F + 2, z, lantern(False) if z % 6 == 0 else slab(PBB_SLAB))
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if not 43 <= x <= 47:
                t.set(x, F, z, slab(PBB_SLAB))
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        post_lamp(t, x, F, z, 4)
    # cargo in the margins
    for (x, z, h) in ((32, 59, 2), (32, 60, 1), (58, 75, 2), (58, 76, 1), (32, 80, 1), (58, 60, 1)):
        for k in range(h):
            t.set(x, F + k, z, t.rng.choice(['oldrepublic:cargo_crate_imperial', 'oldrepublic:cargo_crate_imperial',
                                             barrel('up')]))
    # the pad terminal at the east edge, under a plated canopy
    px, pz = 60, 66
    t.set(px, F, pz, S('oldrepublic:pad_terminal', facing='west'))
    t.set(px, F + 1, pz, AIR)
    t.anchor('PAD_TERMINAL', px, F, pz)
    for z in (pz - 1, pz + 1):
        for y in range(F, F + 3):
            t.set(px + 1, y, z, wall_block(DARK_WALL))
    for z in range(pz - 1, pz + 2):
        t.set(px, F + 3, z, slab(PLATE_SLAB, 'top'))
        t.set(px + 1, F + 3, z, slab(PLATE_SLAB, 'top'))
    t.set(px, F + 2, pz, lantern(True))


# ---------------------------------------------------------------- the south-west: armoury, kiosk, range

def armoury(t):
    """The armoury: where the Empire's smiths and armourers work. A forge at the west end (blast furnace,
    anvil, grindstone, quench trough), the engineering bench, racks of bars and chains, crates of plate."""
    x0, z0, x1, z1 = 8, 61, 21, 71
    wall = patches('town', 181, 3.0)
    shell_box(t, x0, z0, x1, z1, G, G + 6, wall, floor=patches('tiles', 182, 2.2),
              corner='minecraft:polished_blackstone_bricks', plinth=patches('plinth', 183))
    gable_roof(t, x0, z0, x1, z1, G + 7, TILE_STAIRS, ridge='x', overhang=1, gable=wall)
    windows(t, x0, z0, x1, z1, F + 1, F + 2, every=3, sides=('south', 'west', 'east'))
    opening(t, 14, F, z0, 15, F + 2, z0)
    t.set(13, F + 3, z0 - 1, slab(PLATE_SLAB, 'top'))
    for x in (14, 15, 16):
        t.set(x, F + 3, z0 - 1, slab(PLATE_SLAB, 'top'))
    bracket_lamp(t, 13, F + 3, z0, 'north', arm=DARK_WALL)
    # the forge
    t.set(9, F, 63, 'minecraft:blast_furnace[facing=east,lit=true]')
    t.set(9, F, 64, 'minecraft:blast_furnace[facing=east,lit=true]')
    for y in range(F + 1, G + 9):
        t.set(9, y, 63, 'minecraft:polished_blackstone_bricks')
        t.set(9, y, 64, 'minecraft:polished_blackstone_bricks')
    t.set(11, F, 64, 'minecraft:anvil[facing=north]')
    t.set(11, F, 67, 'minecraft:grindstone[face=floor,facing=north]')
    t.set(9, F, 66, 'minecraft:smithing_table')
    t.set(9, F, 68, 'minecraft:water_cauldron[level=3]')
    t.set(9, F, 69, 'minecraft:cauldron')
    t.set(20, F, 64, S('oldrepublic:engineering_bench', facing='west'))
    t.set(20, F, 66, 'minecraft:stonecutter[facing=west]')
    # racks along the south wall and crates
    for x in range(12, 20):
        t.set(x, F, 70, fence('dark_oak_fence') if x % 3 else 'oldrepublic:cargo_crate_imperial')
        t.set(x, F + 1, 70, BARS if x % 2 else chain('y'))
    for (x, z) in ((19, 62), (20, 62), (20, 69)):
        t.set(x, F, z, 'oldrepublic:cargo_crate_imperial')
    t.set(19, F + 1, 62, 'oldrepublic:cargo_crate_imperial')
    mod.hub_spawner(t, 13, F, 66, 'vendor:trainer_armstech', anchor='VENDOR_2')
    mod.hub_spawner(t, 17, F, 66, 'vendor:trainer_armourcraft', anchor='VENDOR_3')
    hang_lantern(t, 12, F + 3, 63, G + 7)
    hang_lantern(t, 17, F + 3, 68, G + 7)
    mod.light_fill(t, region=(x0 + 1, F, z0 + 1, x1 - 1, G + 6, z1 - 1))


def kiosk(t):
    """The spacer's kiosk on the apron by the field: a plated booth open to the north under a crimson awning,
    a counter, crates of ship parts, a screen."""
    x0, z0, x1, z1 = 23, 61, 29, 66
    wall = patches('plate', 191, 2.0)
    shell_box(t, x0, z0, x1, z1, G, G + 4, wall, floor=patches('tiles', 192), corner=BASALT_Y)
    t.fill(x0, G + 5, z0, x1, G + 5, z1, slab(PLATE_SLAB))
    for x in range(x0 + 1, x1):
        t.set(x, F, z0, slab(PLATE_SLAB, 'top') if x != 26 else AIR)
        for y in range(F + 1, G + 4):
            t.set(x, y, z0, AIR)
        t.set(x, G + 4, z0 - 1, 'oldrepublic:canvas_crimson')
        t.set(x, G + 4, z0 - 2, slab('oldrepublic:canvas_slab'))
    t.set(26, F + 1, z0, AIR)
    for x in (x0, x1):
        for y in range(F, G + 4):
            t.set(x, y, z0 - 2, wall_block(DARK_WALL))
    mod.hub_spawner(t, 26, F, 63, 'vendor:spacer', anchor='VENDOR_4')
    t.set(24, F, 65, 'oldrepublic:cargo_crate_imperial')
    t.set(24, F + 1, 65, 'oldrepublic:cargo_crate')
    t.set(28, F, 65, S('oldrepublic:durasteel_pipes', axis='y'))
    t.set(28, F, 64, barrel('up'))
    t.set(26, F + 1, z1, S('oldrepublic:holo_screen', facing='north'))
    t.set(26, G + 3, 64, lantern(True))
    mod.light_fill(t, region=(x0 + 1, F, z0, x1 - 1, G + 3, z1 - 1))


def firing_range(t):
    """The firing range: a roofed stand on the east, targets on hay bales before an earth berm on the west."""
    pave(t, 23, 73, 28, 81, 'road', 201, 2.0)
    for (x, z) in ((23, 73), (28, 73), (23, 81), (28, 81)):
        for y in range(F, G + 4):
            t.set(x, y, z, wall_block(DARK_WALL))
    t.fill(23, G + 4, 73, 28, G + 4, 81, slab(PBB_SLAB, 'top'))
    for z in range(74, 81):
        if z not in (77,):
            t.set(23, F, z, fence('dark_oak_fence'))
    for z in (75, 79):
        t.set(27, F, z, stairs('dark_oak_stairs', 'east'))
    t.set(26, F + 2, 77, lantern(True))
    t.set(26, F + 3, 77, chain())
    for z in range(73, 82):
        for y in range(F, F + 3):
            t.set(4, y, z, pick(t, 'earth')(4, y, z) if y < F + 2 else 'minecraft:coarse_dirt')
            if y < F + 2:
                t.set(5, y, z, 'minecraft:coarse_dirt')
    for z in (74, 77, 80):
        t.set(7, F, z, S('hay_block', axis='y'))
        t.set(7, F + 1, z, 'minecraft:target[power=0]')
        t.set(8, F, z + 1, 'oldrepublic:scorch_mark')
    mod.hub_spawner(t, 25, F, 77, 'guard')


# ---------------------------------------------------------------- the south-east: bazaar, tower, depot, houses

def bazaar(t):
    """Stalls on the street's south side: posts, crimson and black awnings, counters facing the street,
    crates and pots behind."""
    rng = t.rng
    for i, (xa, xb) in enumerate(((61, 65), (67, 71), (73, 77), (79, 82))):
        cloth = 'oldrepublic:canvas_crimson' if i % 2 == 0 else 'minecraft:black_wool'
        for x in (xa, xb):
            for z in (57, 60):
                for y in range(F, F + 3):
                    t.set(x, y, z, wall_block(DARK_WALL))
        for x in range(xa, xb + 1):
            for z in range(57, 61):
                t.set(x, F + 3, z, cloth if (x + z) % 3 else 'oldrepublic:canvas_crimson')
            if xa < x < xb:
                t.set(x, F, 57, slab('dark_oak_slab', 'top') if x % 2 else barrel('north'))
                crate_stack(t, x, F, 60, rng, ['oldrepublic:cargo_crate_imperial', barrel('up'),
                                                'minecraft:decorated_pot[cracked=false,facing=north,waterlogged=false]'],
                            height=rng.choice((1, 2)))
        t.set(xa + 1, F + 1, 57, potted(rng, ('potted_dead_bush', 'potted_red_mushroom', 'potted_cactus')))
        t.set((xa + xb) // 2, F + 2, 58, lantern(True))


def pad_tower(t):
    """The pad control tower: three storeys of plating with a glazed cab looking over the field, a ladder up
    the inside, consoles, a mast with a red light."""
    x0, z0, x1, z1 = 62, 63, 67, 68
    wall = patches('plate', 211, 2.0)
    shell_box(t, x0, z0, x1, z1, G, G + 13, wall, floor=patches('tiles', 212), corner=BASALT_Y)
    for y in (G + 5, G + 10):
        t.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, 'oldrepublic:durasteel_deck')
    t.fill(x0, G + 14, z0, x1, G + 14, z1, 'oldrepublic:imperial_plating')
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            t.set(x, G + 14, z, slab(PLATE_SLAB, 'top'))
    # the cab's glazing
    for y in range(G + 11, G + 14):
        for x in range(x0 + 1, x1):
            t.set(x, y, z0, PANE)
            t.set(x, y, z1, PANE)
        for z in range(z0 + 1, z1):
            t.set(x0, y, z, PANE)
            t.set(x1, y, z, PANE)
    opening(t, 64, F, z0, 65, F + 2, z0)
    for y in range(F, G + 11):
        t.set(66, y, 67, ladder('west'))
    t.set(66, G + 5, 67, ladder('west'))
    t.set(66, G + 10, 67, ladder('west'))
    for (y, n) in ((F, 2), (G + 6, 3), (G + 11, 3)):
        for k in range(n):
            t.set(x0 + 1 + k, y, z0 + 1, S('oldrepublic:console', facing='south'))
    t.set(x0 + 1, G + 11, 66, S('oldrepublic:console', facing='east'))
    chair(t, x0 + 2, G + 11, 66, 'west', wood='dark_oak')
    t.set(x0 + 1, G + 7, 67, S('oldrepublic:holo_screen', facing='east'))
    mod.hub_spawner(t, 64, F, 66, 'droid')
    for y in range(G + 15, G + 20):
        t.set(63, y, 64, BARS)
    t.set(63, G + 20, 64, RED)
    for (y0, y1) in ((F, G + 4), (G + 6, G + 9), (G + 11, G + 13)):
        mod.light_fill(t, region=(x0 + 1, y0, z0 + 1, x1 - 1, y1, z1 - 1))


def depot(t):
    """The quartermaster's depot: a plated warehouse with a wide door to the field, the counter by the door,
    racks of crates down the hall, a gantry crane over the door, a droid at work."""
    x0, z0, x1, z1 = 70, 63, 86, 74
    lower, upper = patches('plate', 221, 2.5), patches('town', 222, 3.0)
    shell_box(t, x0, z0, x1, z1, G, G + 7, lambda x, y, z: lower(x, y, z) if y <= G + 3 else upper(x, y, z),
              floor='oldrepublic:durasteel_deck', corner='minecraft:polished_blackstone_bricks',
              plinth=patches('plinth', 223))
    t.fill(x0, G + 8, z0, x1, G + 8, z1, patches('tiles', 224))
    parapet(t, x0, z0, x1, z1, G + 9, ASH_WALL)
    for x in range(x0 + 3, x1 - 1, 4):
        for z in (66, 71):
            t.set(x, G + 8, z, AMBER)
    opening(t, x0, F, 66, x0, F + 4, 70)
    for z in range(66, 71):
        t.set(x0, F + 4, z, S('minecraft:iron_trapdoor', facing='west', half='top', open=False, powered=False,
                               waterlogged=False))
    windows(t, x0, z0, x1, z1, F + 2, F + 3, every=4, state=BARS, sides=('north', 'south'))
    # the counter and the quartermaster
    for z in range(64, 68):
        t.set(75, F, z, 'oldrepublic:imperial_plating' if z != 67 else slab(PLATE_SLAB, 'top'))
    t.set(75, F + 1, 64, S('oldrepublic:console', facing='west'))
    mod.hub_spawner(t, 76, F, 65, 'vendor:imperial_quartermaster', anchor='VENDOR_1')
    # racks
    rng = t.rng
    for z in (66, 69, 72):
        for x in range(79, 86):
            t.set(x, F, z, rng.choice(['oldrepublic:cargo_crate_imperial', 'oldrepublic:cargo_crate', barrel('up')]))
            if rng.random() < 0.7:
                t.set(x, F + 1, z, rng.choice(['oldrepublic:cargo_crate_imperial', barrel('up')]))
            t.set(x, F + 2, z, slab('dark_oak_slab', 'top') if x % 3 else fence('dark_oak_fence'))
            if rng.random() < 0.4:
                t.set(x, F + 3, z, 'oldrepublic:cargo_crate_imperial')
    mod.hub_spawner(t, 80, F, 70, 'droid', anchor='RESIDENT_7')
    t.set(78, F, 71, 'oldrepublic:cargo_crate_imperial')
    # the gantry crane over the door
    for x in range(x0 - 3, x0 + 3):
        t.set(x, G + 9, 68, S('oldrepublic:durasteel_beam', axis='x'))
    for y in range(F + 2, G + 9):
        t.set(x0 - 3, y, 66, wall_block(DARK_WALL))
        t.set(x0 - 3, y, 70, wall_block(DARK_WALL))
    t.set(x0 - 3, G + 9, 66, S('oldrepublic:durasteel_beam', axis='y'))
    t.set(x0 - 3, G + 9, 70, S('oldrepublic:durasteel_beam', axis='y'))
    for z in range(66, 71):
        t.set(x0 - 3, G + 9, z, S('oldrepublic:durasteel_beam', axis='z'))
    for y in range(F + 4, G + 9):
        t.set(x0 - 2, y, 68, chain())
    t.set(x0 - 2, F + 3, 68, 'oldrepublic:cargo_crate_imperial')
    mod.light_fill(t, region=(x0 + 1, F, z0 + 1, x1 - 1, G + 7, z1 - 1))


def comm_mast(t):
    """The comm mast: a lattice of black posts and iron bars on a plinth, guyed, a red light at the top."""
    cx, cz = 64, 80
    t.fill(cx - 2, F, cz - 2, cx + 2, F, cz + 2, 'minecraft:polished_blackstone_bricks')
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for y in range(F + 1, F + 19):
            t.set(cx + dx, y, cz + dz, wall_block(DARK_WALL))
    for y in range(F + 3, F + 19, 4):
        for (dx, dz) in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            t.set(cx + dx, y, cz + dz, BARS)
    t.set(cx, F + 19, cz, 'oldrepublic:imperial_plating')
    t.set(cx, F + 20, cz, RED)
    t.set(cx, F + 21, cz, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    t.set(cx + 2, F + 1, cz, S('oldrepublic:console', facing='east'))


def staff_house(t, x0, z0, x1, z1, door, seed, colour):
    """A small house for the academy's staff: two bunks, a table, a stove, a chest of drawers, a banner."""
    wall = patches('town', seed, 2.5)
    shell_box(t, x0, z0, x1, z1, G, G + 4, wall, floor='minecraft:dark_oak_planks',
              corner='minecraft:polished_blackstone_bricks', plinth=patches('plinth', seed + 1))
    shapes.hip_roof(t, x0 - 1, z0 - 1, x1 + 1, z1 + 1, G + 5, TILE_STAIRS)
    attic_air(t, x0 + 1, z0 + 1, x1 - 1, z1 - 1, G + 5, G + 8)
    windows(t, x0, z0, x1, z1, F + 1, F + 2, every=3, state=PANE)
    side, a = door
    if side == 'north':
        opening(t, a, F, z0, a + 1, F + 2, z0)
    else:
        opening(t, x0, F, a, x0, F + 2, a + 1)
    bed(t, x1 - 1, F, z1 - 2, 'south', colour)
    bed(t, x1 - 2, F, z1 - 2, 'south', colour)
    t.set(x0 + 2, F, z0 + 2, slab('dark_oak_slab', 'top'))
    chair(t, x0 + 2, F, z0 + 3, 'south', wood='dark_oak')
    t.set(x0 + 1, F, z1 - 1, 'minecraft:furnace[facing=east,lit=true]')
    t.set(x1 - 1, F, z0 + 1, barrel('west'))
    banner(t, x0 + 1, F + 2, z1 - 1, 'north', colour)
    hang_lantern(t, (x0 + x1) // 2, F + 3, (z0 + z1) // 2, G + 6)
    mod.light_fill(t, region=(x0 + 1, F, z0 + 1, x1 - 1, G + 4, z1 - 1))


def staff_houses(t):
    staff_house(t, 67, 76, 73, 82, ('north', 69), 231, 'red')
    staff_house(t, 75, 76, 81, 81, ('north', 77), 233, 'black')


# ---------------------------------------------------------------- the crag's foot: the fallen wing and the talus

def ruins(t, heights):
    """The fallen older wing at the crag's foot (NE): column stumps and a toppled column, a broken arch, heaps
    of black rubble, the old floor showing through the dust."""
    rng = t.rng
    old = patches('tomb', 241, 2.0)
    for x in range(62, 86):
        for z in range(front_z(x) + 1, 27):
            if rng.random() < 0.55:
                t.set(x, G, z, old(x, G, z))
    for (x, z, h) in ((64, 24, 5), (68, 23, 3), (72, 22, 6), (76, 21, 2), (80, 20, 4), (66, 26, 2)):
        if z > front_z(x):
            t.set(x, F, z, CHISELED)
            for y in range(F + 1, F + h):
                t.set(x, y, z, S('oldrepublic:tomb_pillar', axis='y'))
    for x in range(69, 75):
        t.set(x, F, 26, S('oldrepublic:tomb_pillar', axis='x'))
    t.set(68, F, 26, CHISELED)
    for (x, z) in ((78, 24), (82, 24)):
        if z > front_z(x):
            for y in range(F, F + 6):
                t.set(x, y, z, patches('ashlar', 242)(x, y, z))
    for x in (79, 80):
        t.set(x, F + 5, 24, stairs(PBB_STAIRS, 'east' if x == 79 else 'west', 'top'))
    for (cx, cz) in ((71, 25), (83, 22), (63, 22)):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                x, z = cx + dx, cz + dz
                if z > front_z(x) and t.is_air(x, F, z) and rng.random() < 0.75:
                    t.set(x, F, z, rng.choice(['minecraft:blackstone', 'minecraft:cobbled_deepslate',
                                               'minecraft:cracked_polished_blackstone_bricks']))
                    if dx == 0 and dz == 0:
                        t.set(x, F + 1, z, slab('cobbled_deepslate_slab'))
    for _ in range(18):
        x = rng.randint(61, 86)
        z = rng.randint(front_z(x) + 1, 27) if front_z(x) + 1 <= 27 else 27
        if t.is_air(x, F, z) and t.get(x, G, z) is not None:
            t.set(x, F, z, 'oldrepublic:debris')


def talus(t, heights):
    """Fallen rock at the crag's foot (NW): boulders, grit, dead scrub."""
    rng = t.rng
    for (cx, cz, r) in ((6, 18, 1.6), (10, 24, 1.2), (5, 25, 1.0), (12, 27, 0.9)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                for y in range(F, F + 3):
                    d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5 + (y - F) * 0.9
                    if d <= r and z > front_z(x) and 4 <= x and t.is_air(x, y, z):
                        t.set(x, y, z, rng.choice(['minecraft:blackstone', 'minecraft:blackstone', 'minecraft:basalt[axis=y]',
                                                   'minecraft:smooth_basalt']))


# ---------------------------------------------------------------- lights, people, ground cover

def lamps(t):
    for x in (10, 22, 66, 76, 84):
        post_lamp(t, x, F, 57 if x < 60 else 61, 3)
    for x in (36, 45, 54):
        post_lamp(t, x, F, 52, 3)
    for z in (40, 47):
        post_lamp(t, 27, F, z, 3)
        post_lamp(t, 63, F, z, 3)
    for (x, z) in ((16, 26), (62, 33)):
        post_lamp(t, x, F, z, 3)


def people(t):
    mod.hub_spawner(t, 8, F, 55, 'guard', anchor='RESIDENT_5')
    mod.hub_spawner(t, TX - 9, F, 54, 'guard', anchor='RESIDENT_6')
    mod.hub_spawner(t, 44, F, 85, 'guard')
    mod.hub_spawner(t, 41, F, 28, 'guard')
    mod.hub_spawner(t, 21, F, 50, 'acolyte')


EARTH_NAMES = ('minecraft:coarse_dirt', 'minecraft:terracotta', 'minecraft:tuff', 'minecraft:packed_mud',
               'minecraft:red_terracotta')


def ground_cover(t):
    rng = t.rng
    for x in range(4, TX - 4):
        for z in range(4, TZ - 4):
            if t.name_at(x, G, z) in EARTH_NAMES and t.get(x, F, z) is None and rng.random() < 0.07:
                t.set(x, F, z, rng.choice(['minecraft:dead_bush', 'minecraft:short_dry_grass', 'minecraft:short_dry_grass',
                                           'minecraft:tall_dry_grass', 'oldrepublic:ashroot']))
    # grit and scrub on the crag's ledges
    for (x, y, z), s in list(t.cells.items()):
        if y > G + 4 and B.name_of(s) in ('minecraft:coarse_dirt', 'minecraft:terracotta') and t.get(x, y + 1, z) is None \
                and rng.random() < 0.15:
            t.set(x, y + 1, z, rng.choice(['minecraft:dead_bush', 'minecraft:short_dry_grass']))


# ---------------------------------------------------------------- build

def build_town():
    t = Template(TX, TY, TZ, seed=SEED, name=ID + ' (town)')
    ground(t)
    streets(t)
    heights = crag(t)
    facade(t)
    descent(t)
    hall_of_trials(t)
    spire(t, heights)
    forecourt(t)
    arena(t)
    curtain(t)
    gates(t)
    barracks(t)
    overseer_house(t)
    training_yard(t)
    officers_house(t)
    cantina(t)
    med_bay(t)
    landing_field(t)
    armoury(t)
    kiosk(t)
    firing_range(t)
    bazaar(t)
    pad_tower(t)
    depot(t)
    comm_mast(t)
    staff_houses(t)
    ruins(t, heights)
    talus(t, heights)
    lamps(t)
    people(t)
    nature.weather(t, WEATHER, noise=Noise(SEED + 3, 5.0), patchiness=0.6)
    ground_cover(t)
    return t


def skirt_blocks(t):
    """The Proving's skirt: a course of black brick at the wall's foot, grit and ash steps, a retaining face of
    blackstone and deepslate rubble at the edge."""
    n = Noise(SEED + 301, 4.0)
    earth = patches('earth', 302, 4.0)
    face_p = Palette(f=[('minecraft:blackstone', 4), ('minecraft:polished_blackstone_bricks', 2),
                        ('minecraft:cobbled_deepslate', 2), ('minecraft:basalt[axis=y]', 1),
                        ('minecraft:tuff', 1)]).patches('f', seed=SEED + 303)

    def top(x, y, z, d):
        if d == 1:
            return face_p(x, y, z) if n.at(x, 0, z) < 0.65 else earth(x, y, z)
        if d == SKIRT:
            return earth(x, y, z) if n.at(x, 3, z) < 0.7 else face_p(x, y, z)
        return earth(x, y, z)
    return top, face_p


def build():
    town = build_town()
    t = Template(SX, SY, SZ, seed=SEED, name=ID)
    t.paste(town, *SHIFT)
    # the stair down (hub frame), its anchor, and light for the Descent and the well
    tomb = patches('tomb', 401, 2.5)
    for (x, y, z), s in sorted(frame.stair_cells(tomb, parapet=DARK_WALL).items()):
        if y >= 0:
            t.set(x, y, z, s)
    t.anchor('STAIR_DOWN', *frame.STAIR_DOWN)
    dx0, dz0, dx1, dz1 = DESCENT
    mod.light_fill(t, region=(dx0 + 2 + SHIFT[0], HUB_FEET, dz0 + 2 + SHIFT[2], dx1 - 2 + SHIFT[0],
                              DESCENT_TOP + SHIFT[1] - 1, dz1 - 2 + SHIFT[2]))
    mod.light_fill(t, region=(frame.X0, 1, frame.Z0, frame.X1, HUB_GROUND, frame.Z1))
    # the skirt
    sx0, _, sz0 = SHIFT
    gates_ = [('west',) + tuple(v + sz0 for v in GATES['west']), ('east',) + tuple(v + sz0 for v in GATES['east']),
              ('south',) + tuple(v + sx0 for v in GATES['south'])]
    top, face = skirt_blocks(t)
    skirt(t, SKIRT, HUB_GROUND, top, face, gates=gates_, stair=PBB_STAIRS, parapet=DARK_WALL,
          stair_base=lambda x, y, z: 'minecraft:polished_blackstone_bricks', loose=LOOSE + EARTH_NAMES)
    for side, a, b in gates_:
        for v in (a - 1, b + 1):
            p = {'west': (0, v), 'east': (SX - 1, v), 'south': (v, SZ - 1)}[side]
            y = HUB_GROUND - SKIRT + 2
            t.set(p[0], y, p[1], wall_block(DARK_WALL))
            t.set(p[0], y + 1, p[1], lantern(False))
    for (x, z) in [(x, z) for x in range(SX) for z in range(SZ) if 0 < ring_distance(t, SKIRT, x, z)]:
        y = HUB_GROUND - ring_distance(t, SKIRT, x, z)
        if t.name_at(x, y, z) in EARTH_NAMES and t.get(x, y + 1, z) is None and t.rng.random() < 0.18:
            t.set(x, y + 1, z, t.rng.choice(['minecraft:dead_bush', 'minecraft:short_dry_grass', 'oldrepublic:ashroot']))
    footings(t, 'minecraft:blackstone', top_of=lambda x, z: HUB_GROUND - 1 - ring_distance(t, SKIRT, x, z))
    support_gravity(t)
    return t


def main():
    t = build()
    report(t)
    save(t, ID, preview_name='proving')
    meta.write(ID, buried=HUB_FEET, max_slope=12, clearance=8, support_depth=24, ramp_rings=RAMP_RINGS,
               clear_above=16, terrain='flat', spawn_radius=96, links={'hub/sepulchre': frame.SEPULCHRE_OFFSET})
    return t


if __name__ == '__main__':
    main()
