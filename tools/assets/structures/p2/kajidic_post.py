"""planet/kajidic_post (worldgen, Ghessar's canyons): a toll fort of the Vessba kajidic on a canyon road.

"The kajidic owns the pads, the walls, the water and the debts." This is all four in one place: a square
adobe caravanserai on a red sandstone footing, a range of rooms behind every wall with the wall-walk on their
flat roofs, a corner tower at each angle, a gatehouse over the road and a tall keep where the kajidic's
agent counts what the road pays. In the yard the post's water sits in a cistern under a locked grate, fed by
the condensers on the keep's roof; caravans pay at the tap to water their beasts. A debtor sits in the cage
by the gate; outside, a gibbet makes the point to anyone who might not pay.

Rooms: the counting room (the post's chest) on the keep's ground floor, the guard hall over it, the agent's
quarters at the top, the roof terrace with the condensers and the kajidic's banner; barracks and a mess in the
west range, open stables in the east range, storerooms north, the toll office and the debtors' cells south.

Kept anchors (docs/MILESTONE_6.md 5.6): HEART (the yard), CHEST_1 (chests/kajidic_post), SQUAD (the
cartel_enforcers site spawner, three of them, in the yard by the keep door), SURFACE_GUARD (on the road
outside the gate).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gkit as G  # noqa: E402
from gkit import S, AIR, B, mod, W, Noise, Template  # noqa: E402

ID = 'planet/kajidic_post'
SX, SY, SZ = 43, 27, 45
BURIED = 3
F = BURIED            # feet layer of the yard
FL = F - 1            # floor layer
RY = F + 4            # range roofs (the wall-walk stands on them)
WK = RY + 1           # wall-walk feet
SEED = 960302

X0, X1, Z0, Z1 = 5, 37, 5, 37      # the outer wall's lines
D = 5                               # range depth: inner walls at X0 + D, X1 - D, Z0 + D, Z1 - D
YX0, YX1, YZ0, YZ1 = X0 + D + 1, X1 - D - 1, Z0 + D + 1, Z1 - D - 1   # the yard: 11 .. 31
KEEP = (16, 5, 26, 14)              # x0, z0, x1, z1
KF = (FL, 7, 12, 17)                # the keep's floors (the last is its roof)
GATE = (17, 30, 25, 37)
GX = (20, 22)                       # the gate passage, x
TOWERS = ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1))
TOWER_TOP = 12                      # tower roof (feet 13)


def build():
    t = Template(SX, SY, SZ, seed=SEED, name=ID)
    wall, base, roofm, floorm = G.wall_material('kajidic', SEED)
    band = 'red_terracotta'
    rng = t.rng

    # ------------------------------------------------------------ ground: a canyon floor, the fort on a level pad
    g = G.Ground(t, F, 'canyon', seed=SEED)
    g.noise(amp=2, scale=8.0)
    for x in range(X0 - 2, X1 + 3):
        for z in range(Z0 - 2, Z1 + 5):
            if g.inside(x, z):
                g.h[x][z] = 2 * F
    # red sand banked against the north and west walls by the canyon wind
    g.drift({(X0 - 1, z) for z in range(Z0 + 2, Z1 - 1)}, 'west', 5, 4, seed=1)
    g.drift({(x, Z0 - 1) for x in range(X0 + 2, X1 - 1)}, 'north', 4, 4, seed=2)
    g.mound(X1 + 4, 30, 3, 9, 2)
    g.taper(slope=2)
    g.feather()
    g.apply(clear_above=4)

    # ------------------------------------------------------------ the yard: cleared to above the wall-walk (the
    # pool element's clear_from takes the terrain above the template)
    t.air(YX0, F, YZ0, YX1, WK + 3, YZ1)

    # ------------------------------------------------------------ the ranges: rooms behind every wall
    def in_ring(x, z):
        return X0 <= x <= X1 and Z0 <= z <= Z1 and (x <= X0 + D or x >= X1 - D or z <= Z0 + D or z >= Z1 - D)

    def corner(x, z):
        return (x <= X0 + D or x >= X1 - D) and (z <= Z0 + D or z >= Z1 - D)

    def outer(x, z):
        return x in (X0, X1) or z in (Z0, Z1)

    def inner(x, z):
        return ((x in (X0 + D, X1 - D) and Z0 + D <= z <= Z1 - D) or
                (z in (Z0 + D, Z1 - D) and X0 + D <= x <= X1 - D))

    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if not in_ring(x, z):
                continue
            for y in range(0, FL):
                t.set(x, y, z, base(x, y, z))
            if corner(x, z):
                for y in range(FL, RY + 1):
                    t.set(x, y, z, base(x, y, z) if y <= F else wall(x, y, z))
                continue
            t.set(x, FL, z, floorm(x, FL, z))
            if outer(x, z):
                for y in range(F, WK + 1):
                    t.set(x, y, z, base(x, y, z) if y <= F else wall(x, y, z))
            elif inner(x, z):
                for y in range(F, RY):
                    t.set(x, y, z, base(x, y, z) if y <= F else wall(x, y, z))
            else:
                for y in range(F, RY):
                    t.set(x, y, z, AIR)
            if not outer(x, z):
                t.set(x, RY, z, roofm(x, RY, z))
    # the outer wall's battered foot, and the parapet's merlons
    for x in range(X0 - 1, X1 + 2):
        for z in range(Z0 - 1, Z1 + 2):
            ring = (x in (X0 - 1, X1 + 1) and Z0 <= z <= Z1) or (z in (Z0 - 1, Z1 + 1) and X0 <= x <= X1)
            if not ring or (GX[0] - 1 <= x <= GX[1] + 1 and z == Z1 + 1):
                continue
            facing = 'east' if x == X0 - 1 else 'west' if x == X1 + 1 else 'south' if z == Z0 - 1 else 'north'
            for y in range(0, F + 1):
                t.set(x, y, z, base(x, y, z))
            t.set(x, F + 1, z, G.stair('or:adobe_stairs', facing))
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if outer(x, z) and (x + z) % 2 == 0:
                t.set(x, WK + 1, z, wall(x, WK + 1, z))
    # the wall-walk's inner edge: a low wall
    for x in range(X0 + D, X1 - D + 1):
        for z in range(Z0 + D, Z1 - D + 1):
            if inner(x, z):
                t.set(x, WK, z, S('mud_brick_wall'))
    # explicit air over the walk
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            if in_ring(x, z) and not outer(x, z) and not inner(x, z):
                for y in range(WK, WK + 3):
                    t.set(x, y, z, AIR)

    # range partitions (all rooms open off the yard)
    def partition_x(x, z0, z1):
        for z in range(z0, z1 + 1):
            for y in range(F, RY):
                t.set(x, y, z, wall(x, y, z))

    def partition_z(z, x0, x1):
        for x in range(x0, x1 + 1):
            for y in range(F, RY):
                t.set(x, y, z, wall(x, y, z))
    partition_z(21, X0 + 1, X0 + D - 1)           # barracks | mess
    partition_x(KEEP[0] - 1, Z0 + 1, Z0 + D - 1)  # north storerooms end at the keep
    partition_x(KEEP[2] + 1, Z0 + 1, Z0 + D - 1)
    partition_x(GATE[0] - 1, Z1 - D + 1, Z1 - 1)  # toll office | gatehouse | cells
    partition_x(GATE[2] + 1, Z1 - D + 1, Z1 - 1)

    def door_in(x, z, axis, width=2, tall=3, lintel='stripped_spruce_log'):
        """An opening through the wall cell (x, z) and its neighbours along `axis` ('x' or 'z')."""
        for i in range(width):
            cx, cz = (x + i, z) if axis == 'x' else (x, z + i)
            for y in range(F, F + tall):
                t.set(cx, y, cz, AIR)
            t.set(cx, F + tall, cz, G.log(lintel, axis))

    door_in(X0 + D, 15, 'z')                      # barracks
    door_in(X0 + D, 23, 'z')                      # mess
    door_in(12, Z0 + D, 'x')                      # north-west storeroom
    door_in(29, Z0 + D, 'x')                      # north-east storeroom
    door_in(13, Z1 - D, 'x')                      # toll office
    door_in(28, Z1 - D, 'x')                      # cells
    # windows: slits through the outer wall, small barred windows onto the yard
    for z in (13, 17, 19, 25, 29):
        t.set(X0, F + 2, z, S('iron_bars'))
    for x in (12, 14, 28, 30):
        t.set(x, F + 2, Z0, S('iron_bars'))
    for x in (12, 15, 27, 30):
        t.set(x, F + 2, Z1, S('iron_bars'))
    for z in (12, 18, 26, 30):
        t.set(X0 + D, F + 2, z, S('iron_bars'))
    for x in (15, 26):
        t.set(x, F + 2, Z1 - D, S('iron_bars'))
    # the east range is the stables: open to the yard between pillars, a beam over the openings
    for z in range(Z0 + D + 1, Z1 - D):
        pillar = (z - (Z0 + D + 1)) % 4 == 0 or z == Z1 - D - 1
        for y in range(F, RY):
            if pillar:
                t.set(X1 - D, y, z, G.log('stripped_spruce_log', 'y') if y < RY - 1 else wall(X1 - D, y, z))
            else:
                t.set(X1 - D, y, z, AIR if y < RY - 1 else G.log('stripped_spruce_log', 'z'))
    # vigas: roof beams out through the yard faces of the ranges
    for z in range(Z0 + D + 2, Z1 - D - 1, 3):
        if G.free(t, X0 + D + 1, RY - 1, z):
            t.set(X0 + D + 1, RY - 1, z, G.log('stripped_spruce_log', 'x'))
    for x in list(range(YX0 + 1, KEEP[0] - 1, 3)) + list(range(KEEP[2] + 2, YX1, 3)):
        if G.free(t, x, RY - 1, Z0 + D + 1):
            t.set(x, RY - 1, Z0 + D + 1, G.log('stripped_spruce_log', 'z'))

    # the outer faces: roof beams through the wall in a rhythm, buttresses, the render scoured off the foot of
    # the wall by blown sand (the brick shows), a crimson banner on each side wall
    scour = Noise(SEED + 15, 3.0)
    sides = (('west', X0, -1, 0), ('east', X1, 1, 0), ('north', Z0, 0, -1), ('south', Z1, 0, 1))
    for (side, line, dx, dz) in sides:
        along = range(Z0 + D + 1, Z1 - D) if side in ('west', 'east') else range(X0 + D + 1, X1 - D)
        for i in along:
            x, z = (line, i) if side in ('west', 'east') else (i, line)
            if side == 'south' and GATE[0] - 1 <= x <= GATE[2] + 1:
                continue
            if (i - along[0]) % 3 == 1 and G.free(t, x + dx, RY - 1, z + dz):
                t.set(x + dx, RY - 1, z + dz, G.log('stripped_spruce_log', 'x' if dx else 'z'))
            for y in range(F, F + 3):
                if scour.at(x, y * 2, z) > 0.58 - 0.06 * (y - F):
                    t.set(x, y, z, 'or:adobe_bricks')
            if (i - along[0]) % 8 == 4 and side != 'south':
                for y in range(0, F + 4):
                    t.set(x + dx, y, z + dz, base(x + dx, y, z + dz))
                t.set(x + dx, F + 4, z + dz, G.stair('or:adobe_stairs', {'west': 'east', 'east': 'west', 'north': 'south'}[side]))
                for y in range(0, F + 2):
                    t.set(x + 2 * dx, y, z + 2 * dz, base(x, y, z))
                t.set(x + 2 * dx, F + 2, z + 2 * dz, G.stair('or:adobe_stairs', {'west': 'east', 'east': 'west', 'north': 'south'}[side]))
    mod.banner(t, X0 - 1, WK - 1, 22, 'west', base='red')
    mod.banner(t, X1 + 1, WK - 1, 22, 'east', base='red')

    # ------------------------------------------------------------ the corner towers
    for (cx, cz) in TOWERS:
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                edge = x in (cx - 2, cx + 2) or z in (cz - 2, cz + 2)
                for y in range(0, TOWER_TOP + 1):
                    if y <= RY or edge or y == TOWER_TOP:
                        t.set(x, y, z, base(x, y, z) if y <= F + 1 else wall(x, y, z))
                    else:
                        t.set(x, y, z, AIR)
                if edge and (x + z) % 2 == 0:
                    t.set(x, TOWER_TOP + 1, z, wall(x, TOWER_TOP + 1, z))
                if edge and (x + z) % 2 == 1:
                    t.set(x, TOWER_TOP + 1, z, G.slab('or:adobe_slab'))
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                for y in range(TOWER_TOP + 1, TOWER_TOP + 4):
                    t.set(x, y, z, AIR)
        # a crimson band under the parapet
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                if x in (cx - 2, cx + 2) or z in (cz - 2, cz + 2):
                    t.set(x, TOWER_TOP - 1, z, band)
        # doors onto the walks (toward the yard), arrow slits outward
        sx_ = 1 if cx == X0 else -1
        sz_ = 1 if cz == Z0 else -1
        for y in (WK, WK + 1):
            t.set(cx + 2 * sx_, y, cz + sz_, AIR)
            t.set(cx + sz_ * 0 + sx_, y, cz + 2 * sz_, AIR)
        for (dx, dz) in ((-2 * sx_, 0), (0, -2 * sz_)):
            t.set(cx + dx, WK + 1, cz + dz, S('iron_bars'))
        # a ladder up to the roof in the outer corner
        lx, lz = cx - sx_, cz - sz_
        lf = 'east' if sx_ > 0 else 'west'
        for y in range(WK, TOWER_TOP + 1):
            t.set(lx, y, lz, G.ladder(lf))
        t.set(cx, WK + 2, cz, G.lantern(True))
        t.set(cx, WK + 3, cz, G.chain('y'))

    # tower roofs: sunshades of crimson cloth on two, a fire on the third, a lamp on the fourth
    for (cx, cz), what in zip(TOWERS, ('shade', 'fire', 'lamp', 'shade')):
        ty = TOWER_TOP + 1
        if what == 'shade':
            for (dx, dz) in ((-1, -1), (1, 1)):
                for y in range(ty, ty + 2):
                    t.set(cx + dx, y, cz + dz, S('spruce_fence'))
            for x in range(cx - 1, cx + 2):
                for z in range(cz - 1, cz + 2):
                    t.set(x, ty + 2, z, 'or:canvas_crimson' if (x + z) % 2 == 0 else G.slab('or:canvas_slab', 'top'))
            t.set(cx, ty, cz, G.stair('spruce_stairs', 'north'))
        elif what == 'fire':
            G.brazier(t, cx, ty, cz, stand='cut_red_sandstone')
        else:
            t.set(cx, ty, cz, 'or:light_panel_amber')
            t.set(cx, ty + 1, cz, S('iron_bars'))

    # ------------------------------------------------------------ the keep
    kx0, kz0, kx1, kz1 = KEEP
    ktop = KF[-1]
    for x in range(kx0, kx1 + 1):
        for z in range(kz0, kz1 + 1):
            edge = x in (kx0, kx1) or z in (kz0, kz1)
            for y in range(0, ktop + 2):
                if y < FL:
                    t.set(x, y, z, base(x, y, z))
                elif edge:
                    t.set(x, y, z, base(x, y, z) if y <= F + 1 else wall(x, y, z))
                elif y in KF:
                    t.set(x, y, z, floorm(x, y, z) if y == FL else 'stripped_spruce_wood' if (x % 3 == 0 and y < ktop)
                          else roofm(x, y, z) if y == ktop else 'spruce_planks')
                else:
                    t.set(x, y, z, AIR)
            if edge:
                t.set(x, ktop + 1, z, wall(x, ktop + 1, z))
                if (x + z) % 2 == 0:
                    t.set(x, ktop + 2, z, wall(x, ktop + 2, z))
                for y in (KF[1], KF[2]):
                    t.set(x, y, z, band if y == KF[2] else base(x, y, z))
    for x in range(kx0 + 1, kx1):
        for z in range(kz0 + 1, kz1):
            for y in range(ktop + 1, ktop + 4):
                t.set(x, y, z, AIR)
    # vigas through the side walls under each floor
    for y in (KF[1], KF[2], ktop):
        for z in range(kz0 + 2, kz1 - 1, 3):
            for x in (kx0 - 1, kx1 + 1):
                if G.free(t, x, y, z):
                    t.set(x, y, z, G.log('stripped_spruce_log', 'x'))
    # the door, the porch and the windows
    for x in range(20, 23):
        for y in range(F, F + 3):
            t.set(x, y, kz1, AIR)
        t.set(x, F + 3, kz1, G.log('stripped_spruce_log', 'x'))
    for x in (19, 23):
        t.set(x, F + 3, kz1 + 1, G.log('stripped_spruce_log', 'z'))
    for (x, y) in ((18, 9), (24, 9), (18, 14), (24, 14)):
        t.set(x, y, kz1, S('iron_bars'))
        t.set(x, y + 1, kz1, S('iron_bars'))
        t.set(x, y - 1, kz1 + 1, G.stair('or:adobe_stairs', 'north', 'top'))
    for (x, y, z) in ((kx0, 9, 11), (kx1, 9, 11), (kx0, 14, 9), (kx1, 14, 9), (kx0, 14, 12), (kx1, 14, 12)):
        t.set(x, y, z, S('iron_bars'))
    # level 2 opens onto the north walk to the west and east
    for z in (7, 8):
        for y in (WK, WK + 1, WK + 2):
            t.set(kx0, y, z, AIR)
            t.set(kx1, y, z, AIR)
        t.set(kx0, WK + 3, z, G.log('stripped_spruce_log', 'z'))
        t.set(kx1, WK + 3, z, G.log('stripped_spruce_log', 'z'))
    # stairs: ground -> level 2 up the east side (climbing north), level 2 -> 3 up the west side (climbing
    # south), a ladder to the roof
    for i in range(5):
        z = 12 - i
        y = F + i
        for x in (24, 25):
            t.set(x, y, z, G.stair('spruce_stairs', 'north'))
            for yy in range(FL + 1, y):
                t.set(x, yy, z, 'spruce_planks')
            for yy in range(y + 1, y + 4):
                if yy != KF[1] or z >= 8:
                    if yy < KF[1] or (yy == KF[1] and z >= 8):
                        t.set(x, yy, z, AIR)
    for x in (24, 25):
        for z in range(8, 12):
            if (x, z) != (24, 8) and (x, z) != (25, 8):
                t.set(x, KF[1], z, AIR)
    for x in (24, 25):
        t.set(x, KF[1], 8, G.stair('spruce_stairs', 'north'))
        t.set(x, KF[1], 12, 'spruce_planks')
    for z in range(9, 13):
        t.set(23, WK, z, S('spruce_fence'))
    for i in range(5):
        z = 8 + i
        y = WK + i
        for x in (17, 18):
            t.set(x, y, z, G.stair('spruce_stairs', 'south'))
            for yy in range(KF[1] + 1, y):
                t.set(x, yy, z, 'spruce_planks')
            for yy in range(y + 1, y + 4):
                if yy <= KF[2] and not (yy == KF[2] and z == 12):
                    t.set(x, yy, z, AIR)
    for x in (17, 18):
        t.set(x, KF[2], 12, G.stair('spruce_stairs', 'south'))
    for z in range(8, 12):
        t.set(19, KF[2] + 1, z, S('spruce_fence'))
    for y in range(KF[2] + 1, ktop + 1):
        t.set(21, y, kz0 + 1, G.ladder('south'))

    # ------------------------------------------------------------ the gatehouse
    gx0, gz0, gx1, gz1 = GATE
    gtop = TOWER_TOP
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            edge = x in (gx0, gx1) or z in (gz0, gz1)
            for y in range(0, gtop + 1):
                if y < FL or y == gtop or y == RY or edge or y <= RY:
                    t.set(x, y, z, base(x, y, z) if y <= F + 1 else wall(x, y, z))
                else:
                    t.set(x, y, z, AIR)
            if edge:
                if (x + z) % 2 == 0:
                    t.set(x, gtop + 1, z, wall(x, gtop + 1, z))
                t.set(x, gtop - 1, z, band)
            else:
                for y in range(gtop + 1, gtop + 4):
                    t.set(x, y, z, AIR)
    # the gate room floor and its rooms' interiors
    for x in range(gx0 + 1, gx1):
        for z in range(gz0 + 1, gz1):
            t.set(x, RY, z, 'spruce_planks')
            for y in range(F, RY):
                if not (GX[0] - 1 <= x <= GX[1] + 1):
                    t.set(x, y, z, wall(x, y, z))
    # the passage
    for x in range(GX[0], GX[1] + 1):
        for z in range(gz0, gz1 + 1):
            t.set(x, FL, z, 'coarse_dirt' if (x + z) % 3 else 'gravel')
            for y in range(F, F + 4):
                t.set(x, y, z, AIR)
        t.set(x, F + 3, gz1, S('iron_bars'))       # the portcullis, raised
        t.set(x, F + 4, gz1, 'cut_red_sandstone')
        t.set(x, F + 4, gz0, 'cut_red_sandstone')
    for z in (32, 35):
        t.set(21, RY, z, G.trapdoor('iron_trapdoor', 'north', 'top', False))   # murder holes
    # doors from the gate room onto the south walk
    for z in (33, 34):
        for y in (WK, WK + 1, WK + 2):
            t.set(gx0, y, z, AIR)
            t.set(gx1, y, z, AIR)
    for y in range(WK, gtop + 1):
        t.set(gx1 - 1, y, gz0 + 1, G.ladder('west'))
    for x in range(gx0 + 2, gx1 - 1, 2):
        t.set(x, WK + 1, gz1, S('iron_bars'))
    t.set(21, WK + 1, gz0, S('iron_bars'))
    # red banners either side of the gate, on the outer face
    for x in (18, 24):
        mod.banner(t, x, F + 6, gz1 + 1, 'south', base='red')
    for x in (19, 23):
        mod.banner(t, x, 9, kz1 + 1, 'south', base='red')

    # ------------------------------------------------------------ the yard
    pave = G.mix(SEED + 20, [('cut_red_sandstone', 5), ('smooth_red_sandstone', 3), ('red_sand', 2),
                             ('or:adobe_bricks', 1)], 3.0)
    worn = G.mix(SEED + 21, [('coarse_dirt', 4), ('red_sand', 3), ('packed_mud', 1)], 2.5)
    for x in range(YX0, YX1 + 1):
        for z in range(YZ0, YZ1 + 1):
            if G.free(t, x, F, z):
                path = abs(x - 21) <= 1 or (abs(z - 23) <= 0 and x > 21)
                t.set(x, FL, z, worn(x, FL, z) if path else pave(x, FL, z))
                t.set(x, FL - 1, z, base(x, FL - 1, z))
    # the cistern: the post's water, under a locked grate, shaded by a frame of beams
    cx0, cz0, cx1, cz1 = 12, 18, 17, 23
    G.cistern(t, cx0, cz0, cx1, cz1, F, depth=2, rim='cut_red_sandstone', lid=True, water=True)
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            if x in (cx0, cx1) or z in (cz0, cz1):
                t.set(x, F, z, 'cut_red_sandstone' if (x + z) % 2 else 'smooth_red_sandstone')
                t.set(x, FL, z, 'cut_red_sandstone')
    t.set(14, F, 20, AIR)
    t.set(15, F, 20, AIR)
    for (x, z) in ((cx0, cz0), (cx1, cz0), (cx0, cz1), (cx1, cz1)):
        for y in range(F + 1, F + 5):
            t.set(x, y, z, G.log('stripped_spruce_log', 'y'))
    for x in range(cx0, cx1 + 1):
        t.set(x, F + 5, cz0, G.log('stripped_spruce_log', 'x'))
        t.set(x, F + 5, cz1, G.log('stripped_spruce_log', 'x'))
    for z in range(cz0 + 1, cz1):
        t.set(cx0, F + 5, z, G.log('stripped_spruce_log', 'z'))
        t.set(cx1, F + 5, z, G.log('stripped_spruce_log', 'z'))
        if z % 2 == 0:
            for x in range(cx0 + 1, cx1):
                t.set(x, F + 5, z, G.slab('or:canvas_slab', 'top') if x % 2 else 'or:canvas_crimson')
    # the bucket chain over the opening and its winch
    t.set(14, F + 4, 20, G.chain('y'))
    t.set(14, F + 3, 20, G.chain('y'))
    t.set(14, F + 2, 20, G.chain('y'))
    t.set(14, F + 1, 20, G.chain('y'))
    t.set(14, F + 5, 20, G.log('stripped_spruce_log', 'x'))
    t.set(15, F + 5, 20, G.log('stripped_spruce_log', 'x'))
    t.set(13, F + 5, 20, G.log('stripped_spruce_log', 'x'))
    # the pipe from the keep's condensers down the keep's west face into the cistern
    for y in range(F, ktop + 1):
        t.set(kx0 - 1, y, kz1 - 1, G.rod('up', 'weathered_lightning_rod'))
    for z in range(kz1, cz0):
        t.set(kx0 - 1, FL, z, 'or:durasteel_pipes[axis=z]')
    # the tap: a spout over a trough of jars where caravans pay to water their beasts; the tally keeper's stool
    t.set(cx1 + 1, F, 21, S('water_cauldron', level=3))
    t.set(cx1 + 1, F, 22, S('water_cauldron', level=1))
    t.set(cx1, F + 1, 21, S('tripwire_hook', facing='east', attached=False, powered=False))
    t.set(cx1 + 1, F, 19, G.barrel('up'))                       # the cash box (empty: it has been collected)
    G.chair(t, cx1 + 2, F, 19, 'west')
    # the debtor's cage by the gate
    for x in range(12, 15):
        for z in range(27, 30):
            edge = x in (12, 14) or z in (27, 29)
            for y in range(F, F + 3):
                if edge:
                    t.set(x, y, z, S('iron_bars'))
            t.set(x, F + 3, z, G.trapdoor('iron_trapdoor', 'north', 'bottom', False))
    t.set(13, F, 28, S('brown_carpet'))
    t.set(13, F + 2, 28, G.chain('y'))
    for x in (12, 14):
        for z in (27, 29):
            t.set(x, F + 3, z, 'or:adobe_bricks')
    # the trade stall: a counter under a striped awning by the gate
    G.awning(t, 25, 25, 30, 29, F + 4, cloth='or:canvas_slab', stripes='or:canvas_crimson', posts='spruce_fence')
    G.slab_table(t, 25, 26, 25, 28, F, top='smooth_red_sandstone_slab')
    for (x, z, s) in ((27, 26, 'or:cargo_crate'), (28, 26, G.barrel('up')), (28, 28, 'or:cargo_crate_republic'),
                      (27, 28, G.pot('west')), (29, 27, 'hay_block'), (29, 26, G.barrel('up')),
                      (29, 28, 'or:cargo_crate')):
        t.set(x, F, z, s)
    t.set(25, F + 1, 27, S('or:holo_screen', facing='west'))
    t.set(29, F + 1, 26, G.pot('west'))
    t.set(27, F + 3, 26, G.lantern(True))                # under a crimson stripe (a whole block) of the awning
    t.set(26, F, 27, G.stair('spruce_stairs', 'east'))
    # the hitching rail and the beasts' trough before the stables
    for z in range(12, 31):
        if z not in (15, 16, 23, 24, 25) and (z % 6) != 0:
            t.set(YX1, F, z, S('spruce_fence'))
    for z in (15, 16):
        t.set(YX1, F, z, S('water_cauldron', level=2))
    # the stair up to the west walk, against the mess
    for i in range(5):
        z = 30 - i
        y = F + i
        t.set(YX0, y, z, G.stair('or:adobe_stairs', 'north'))
        for yy in range(FL, y):
            t.set(YX0, yy, z, wall(YX0, yy, z))
    t.set(YX0, RY, 25, roofm(YX0, RY, 25))
    for z in (24, 25, 26):
        t.set(X0 + D, WK, z, AIR)
    t.set(YX0, RY, 24, AIR)
    # braziers at the keep door, a lamp post by the gate, crimson drapes from the keep's parapet
    G.brazier(t, 18, F, 15, stand='cut_red_sandstone')
    G.brazier(t, 24, F, 15, stand='cut_red_sandstone')
    G.lamp_post(t, 19, F, 29, height=3)
    G.lamp_post(t, 23, F, 29, height=3)
    for x in (17, 25):
        G.drape(t, x, ktop - 1, kz1 + 1, 4)
    # the agent's balcony over the door: he watches the yard and the gate from here
    for x in range(20, 23):
        for y in (KF[2] + 1, KF[2] + 2, KF[2] + 3):
            t.set(x, y, kz1, AIR)
        t.set(x, KF[2] + 4, kz1, G.log('stripped_spruce_log', 'x'))
    for x in range(19, 24):
        for z in (kz1 + 1, kz1 + 2):
            t.set(x, KF[2], z, G.slab('spruce_slab', 'top'))
            t.set(x, KF[2] + 4, z, 'or:canvas_crimson' if (x + z) % 2 else G.slab('or:canvas_slab', 'top'))
        t.set(x, KF[2] + 1, kz1 + 3, S('spruce_fence'))
        t.set(x, KF[2] + 1, kz1 + 2, AIR)
        t.set(x, KF[2] + 1, kz1 + 1, AIR)
    for z in (kz1 + 1, kz1 + 2):
        t.set(19, KF[2] + 1, z, S('spruce_fence'))
        t.set(23, KF[2] + 1, z, S('spruce_fence'))
    for x in (19, 21, 23):
        t.set(x, KF[2] - 1, kz1 + 1, G.log('stripped_spruce_log', 'z'))
    for x in (19, 23):
        for y in range(KF[2] + 2, KF[2] + 4):
            t.set(x, y, kz1 + 3, S('spruce_fence'))
        t.set(x, KF[2] + 4, kz1 + 3, 'or:canvas_crimson')
    for x in range(20, 23):
        t.set(x, KF[2] + 4, kz1 + 3, G.slab('or:canvas_slab', 'top'))
    t.set(22, KF[2] + 1, kz1 + 2, G.stair('spruce_stairs', 'east'))
    # sand blown in at the gate and in the corners
    for (x, z) in ((20, 31), (22, 30), (21, 31), (12, 12), (11, 13), (31, 31), (30, 31), (11, 31)):
        if G.free(t, x, F, z):
            t.set(x, F, z, G.slab('smooth_red_sandstone_slab') if (x + z) % 2 else S('red_sand'))
    for (x, z) in ((16, 25), (19, 27), (26, 17), (23, 20), (28, 13)):
        if G.free(t, x, F, z):
            t.set(x, F, z, 'or:debris')

    # ------------------------------------------------------------ rooms
    # barracks (west range, north half): bunks against the outer wall, lockers, a rack of rifles
    bx0, bx1 = X0 + 1, X0 + D - 1
    for z in (12, 16, 19):
        G.bed(t, bx0, F, z, 'north', 'red')
        t.set(bx0, F + 1, z, G.slab('spruce_slab', 'top'))
        t.set(bx0, F + 1, z + 1, G.slab('spruce_slab', 'top'))
        G.bed(t, bx0, F + 2, z, 'north', 'brown')
    for z in (11, 14):
        t.set(bx1, F, z, G.trapdoor('iron_trapdoor', 'west', 'top', False))
        t.set(bx1, F + 1, z, G.trapdoor('iron_trapdoor', 'west', 'top', z == 14))
    for z in (18, 19):
        t.set(bx1, F, z, 'or:cargo_crate_imperial')
        t.set(bx1, F + 1, z, G.rod('up'))
    t.set(bx0 + 1, F, 11, G.barrel('up'))
    t.set(bx0 + 2, F + 3, 15, G.lantern(True))
    t.set(bx0 + 2, F + 3, 18, G.lantern(True))
    # the mess (west range, south half): a long table, benches, a stove and the water ration jars
    G.slab_table(t, bx0 + 2, 24, bx0 + 2, 29, F, top='spruce_slab')
    for z in (24, 26, 28):
        t.set(bx0 + 1, F, z, G.stair('spruce_stairs', 'west'))
        t.set(bx0 + 3, F, z + 1, G.stair('spruce_stairs', 'east'))
    t.set(bx0 + 2, F + 1, 25, G.candle(2))
    t.set(bx0 + 2, F + 1, 28, G.pot('north'))
    t.set(bx0, F, 31, S('smoker', facing='east', lit=True))
    t.set(bx0 + 1, F, 31, S('water_cauldron', level=1))
    t.set(bx0, F, 30, G.barrel('east'))
    t.set(bx0, F, 22, G.pot('east'))
    t.set(bx0, F, 23, S('cauldron'))
    t.set(bx1, F, 31, G.barrel('up'))
    t.set(bx0 + 2, F + 3, 26, G.lantern(True))
    t.set(bx0 + 2, F + 3, 30, G.lantern(True))
    # stables (east range): stalls between the pillars, hay, troughs
    sx0, sx1 = X1 - D + 1, X1 - 1
    for x in range(sx0, sx1 + 1):
        for z in range(Z0 + D + 1, Z1 - D):
            t.set(x, FL, z, 'coarse_dirt' if (x * 3 + z) % 4 else 'packed_mud')
    for z in range(Z0 + D + 1, Z1 - D, 4):
        if z == Z0 + D + 1:
            continue
        for x in range(sx0, sx1):
            t.set(x, F, z, S('spruce_fence'))
    for z in range(Z0 + D + 2, Z1 - D - 1, 4):
        t.set(sx1, F, z, S('water_cauldron', level=1 + (z % 3)))
        t.set(sx1, F, z + 1, 'hay_block')
        if z % 8 == 0:
            t.set(sx1, F + 1, z + 1, 'hay_block')
        if G.free(t, sx1 - 1, F, z + 2):
            t.set(sx1 - 1, F, z + 2, S('yellow_carpet'))
    for z in (14, 22, 30):
        t.set(sx0 + 1, RY - 1, z, G.lantern(True))
    # north storerooms: crates of tribute, water jars, rolled cloth
    for (x0_, x1_) in ((YX0, KEEP[0] - 2), (KEEP[2] + 2, YX1)):
        for x in range(x0_, x1_ + 1):
            for z in (Z0 + 1, Z0 + 2):
                if rng.random() < 0.7:
                    G.crate_stack(t, x, F, z, 1 + int(rng.random() * 2.2), rng,
                                  [('or:cargo_crate', 4), ('or:cargo_crate_republic', 2), (G.barrel(), 2),
                                   ('or:canvas_crimson', 1), ('or:canvas', 1)])
        t.set(x0_, F, Z0 + 3, G.pot('south'))
        t.set(x1_, F, Z0 + 4, S('water_cauldron', level=3))
        t.set((x0_ + x1_) // 2, F + 3, Z0 + 3, G.lantern(True))
    # the toll office (south range, west): a desk with its screen, a ledger, confiscated goods
    ox0, ox1 = YX0, GATE[0] - 2
    G.slab_table(t, ox0 + 1, 34, ox0 + 3, 34, F, top='spruce_slab')
    t.set(ox0 + 2, F, 35, G.stair('spruce_stairs', 'south'))
    t.set(ox0 + 1, F + 1, 34, S('or:holo_screen', facing='south'))
    mod.lectern(t, ox0 + 3, F, 35, 'north')
    t.set(ox0, F, 36, 'or:cargo_crate_imperial')
    t.set(ox0, F + 1, 36, 'or:cargo_crate')
    t.set(ox1, F, 36, G.barrel('up'))
    t.set(ox1, F, 35, 'or:cargo_crate_republic')
    t.set(ox0 + 4, F, 33, S('or:console', facing='north'))
    t.set(ox0 + 2, F + 3, 35, G.lantern(True))
    mod.banner(t, ox0 + 2, F + 2, Z1 - 1, 'north', base='red')
    # the debtors' cells (south range, east): two barred cells, bedrolls, a chain on the wall
    cx_a = GATE[2] + 2
    for x in range(cx_a, YX1 + 1):
        for y in range(F, RY):
            t.set(x, y, 34, S('iron_bars'))
    for y in range(F, RY):
        t.set(cx_a + 3, y, 35, S('iron_bars'))
        t.set(cx_a + 3, y, 36, S('iron_bars'))
    t.set(cx_a + 1, F, 34, AIR)
    t.set(cx_a + 1, F + 1, 34, AIR)
    G.door(t, cx_a + 1, F, 34, 'south', 'left', 'iron_door', False)
    t.set(cx_a + 4, F, 34, AIR)
    t.set(cx_a + 4, F + 1, 34, AIR)
    G.door(t, cx_a + 4, F, 34, 'south', 'left', 'iron_door', False)
    t.set(cx_a, F, 36, S('brown_carpet'))
    t.set(cx_a + 1, F, 36, S('brown_carpet'))
    t.set(cx_a + 5, F, 36, S('gray_carpet'))
    t.set(cx_a + 2, F + 2, 36, G.chain('y'))
    t.set(cx_a + 2, F + 1, 36, G.chain('y'))
    t.set(cx_a + 4, F, 35, S('cauldron'))
    t.set(cx_a + 2, F + 3, 33, G.lantern(True))
    t.set(YX1, F, 33, 'or:cargo_crate')

    # the keep, ground floor: the counting room
    G.slab_table(t, 18, 9, 22, 9, F, top='spruce_slab')                    # the counter
    t.set(20, F + 1, 9, S('or:holo_screen', facing='south'))
    t.set(18, F + 1, 9, G.candle(3, True))
    t.set(22, F + 1, 9, G.pot('south'))
    for x in (19, 21):
        t.set(x, F, 8, G.stair('spruce_stairs', 'south'))                  # the agent's seat
    t.chest(20, F, 6, 'south', 'chests/kajidic_post')
    t.anchor('CHEST_1', 20, F, 6)
    t.set(19, F, 6, 'or:cargo_crate_imperial')
    t.set(21, F, 6, G.barrel('up'))
    t.set(18, F, 6, S('or:console', facing='south'))
    t.set(22, F, 6, 'or:cargo_crate')
    t.set(22, F + 1, 6, 'or:cargo_crate_republic')
    for z in range(10, 14):
        t.set(21, F, z, S('red_carpet'))
    t.set(17, F, 13, G.pot('east'))
    t.set(17, F, 12, S('water_cauldron', level=3))
    t.set(17, F, 7, G.barrel('up'))
    G.hanging_lamp(t, 20, KF[1] - 1, 11, drop=1)
    t.set(17, F + 3, 10, 'or:light_panel_amber')
    # level 2: the guard hall
    g2 = KF[1] + 1
    G.slab_table(t, 20, 10, 22, 11, g2, top='spruce_slab')
    for (x, z, f) in ((19, 10, 'east'), (19, 11, 'east'), (23, 11, 'west')):
        G.chair(t, x, g2, z, f)
    t.set(21, g2 + 1, 10, G.candle(1, True))
    for x in (20, 22):
        t.set(x, g2, kz0 + 1, 'or:cargo_crate_imperial')
        t.set(x, g2 + 1, kz0 + 1, G.rod('up'))
    t.set(21, g2, kz0 + 1, 'grindstone[face=floor,facing=north]')
    t.set(19, g2, 13, G.barrel('up'))
    t.set(22, g2, 13, G.barrel('up'))
    G.hanging_lamp(t, 21, KF[2] - 1, 11, drop=1)
    t.set(24, g2 + 2, 13, 'or:light_panel_amber')
    # level 3: the agent's quarters
    g3 = KF[2] + 1
    G.bed(t, 24, g3, 12, 'north', 'red')
    for x in range(20, 25):
        for z in range(9, 12):
            if G.free(t, x, g3, z) and (x, z) != (24, 11):
                t.set(x, g3, z, S('red_carpet'))
    G.slab_table(t, 22, 7, 23, 7, g3, top='spruce_slab')
    t.set(22, g3 + 1, 7, G.pot('south'))
    t.set(23, g3 + 1, 7, G.candle(2, True))
    G.chair(t, 22, g3, 8, 'north')
    # the agent's water: a glass tank in the corner, full
    for x in (24, 25):
        for z in (6, 7):
            t.set(x, g3, z, 'cut_red_sandstone')
            t.set(x, g3 + 1, z, S('water'))
            t.set(x, g3 + 2, z, 'glass')
    t.set(24, g3 + 1, 7, 'glass')
    t.set(25, g3 + 1, 7, 'glass')
    t.set(24, g3 + 1, 6, S('water'))
    t.set(25, g3 + 1, 6, 'glass')
    t.set(20, g3, 13, G.barrel('up'))
    G.hanging_lamp(t, 22, ktop - 1, 10, drop=1)
    # the roof terrace: condensers, a lamp, a sunshade, the kajidic's banner
    rt = ktop + 1
    for (x, z) in ((18, 7), (24, 7), (18, 12)):
        G.condenser(t, x, rt, z, height=5, owned=True)
    for x in range(21, 25):
        for z in range(10, 13):
            t.set(x, rt + 3, z, 'or:canvas_crimson' if (x + z) % 2 == 0 else G.slab('or:canvas_slab', 'top'))
    for (x, z) in ((21, 10), (24, 12)):
        for y in range(rt, rt + 3):
            t.set(x, y, z, S('spruce_fence'))
    G.chair(t, 22, rt, 11, 'south')
    for y in range(rt, rt + 5):
        t.set(kx1 - 1, y, kz1 - 1, S('spruce_fence'))
    mod.banner(t, kx1 - 1, rt + 5, kz1 - 1, 'south', base='red', wall=False)
    t.set(kx0 + 1, rt, kz1 - 1, 'cut_red_sandstone')
    t.set(kx0 + 1, rt + 1, kz1 - 1, G.lantern(False))
    t.set(21, KF[-1], kz0 + 1, G.ladder('south'))
    t.set(21, rt, kz0 + 1, AIR)

    # ------------------------------------------------------------ outside: the road, the toll barrier, the gibbet
    road = G.mix(SEED + 30, [('coarse_dirt', 4), ('red_sand', 3), ('gravel', 2), ('packed_mud', 1)], 2.0)
    for z in range(Z1 + 1, SZ):
        for x in range(GX[0] - 1, GX[1] + 2):
            y = g.feet(x, z) - 1
            if y >= 0 and z < SZ - 3:
                t.set(x, y, z, road(x, y, z))
    # the barrier, raised, and the toll collector's booth
    for y in range(F, F + 4):
        t.set(GX[1] + 2, y, Z1 + 2, G.log('stripped_spruce_log', 'y') if y > F else 'cut_red_sandstone')
    t.set(GX[1] + 2, F + 4, Z1 + 2, S('red_wool'))
    bx, bz = GX[1] + 3, Z1 + 1
    for x in range(bx, bx + 3):
        for z in range(bz, bz + 3):
            edge = x in (bx, bx + 2) or z in (bz, bz + 2)
            t.set(x, FL, z, 'cut_red_sandstone')
            for y in range(F, F + 3):
                t.set(x, y, z, wall(x, y, z) if edge else AIR)
            t.set(x, F + 3, z, G.slab('or:adobe_slab'))
    t.set(bx, F + 1, bz + 1, S('iron_bars'))
    t.set(bx + 1, F, bz + 1, G.stair('spruce_stairs', 'east'))
    for y in (F, F + 1):
        t.set(bx + 1, y, bz + 2, AIR)
    G.awning(t, bx - 1, bz, bx - 1, bz + 2, F + 3, cloth='or:canvas_crimson', posts=None)
    # the gibbet: a post, an arm, an empty hanging cage; what fell out of it lies below
    px, pz = GX[0] - 5, Z1 + 2
    for y in range(F, F + 6):
        t.set(px, y, pz, G.log('stripped_spruce_log', 'y') if y > F else 'cut_red_sandstone')
    for x in (px + 1, px + 2):
        t.set(x, F + 5, pz, G.log('stripped_spruce_log', 'x'))
    t.set(px + 2, F + 4, pz, G.chain('y'))
    t.set(px + 2, F + 3, pz, S('iron_bars'))
    t.set(px + 2, F + 2, pz, S('iron_bars'))
    t.set(px + 2, F, pz, S('skeleton_skull', rotation=3, powered=False))
    t.set(px + 1, F, pz + 1, 'bone_block[axis=z]')
    # hitching posts by the gate, boulders, dry brush
    for (x, z) in ((GX[0] - 2, Z1 + 3), (GX[1] + 6, Z1 + 3)):
        t.set(x, F, z, S('spruce_fence'))
        t.set(x, F + 1, z, S('spruce_fence'))
    for (x, z, r) in ((39, 12, 1.6), (11, 41, 1.8), (35, 41, 1.2), (40, 30, 1.1)):
        G.boulder(t, x, g.feet(x, z), z, r, SEED + x * 7 + z)
    g.scatter(0.03, avoid={(x, z) for x in range(X0 - 1, X1 + 2) for z in range(Z0 - 1, Z1 + 2)} |
              {(x, z) for x in range(GX[0] - 2, GX[1] + 3) for z in range(Z1, SZ)})

    # ------------------------------------------------------------ anchors
    mod.spot(t, 'HEART', 21, F, 23)
    mod.spawner(t, 18, F, 18, 'cartel_enforcers', count=3, anchor='SQUAD')
    mod.spot(t, 'SURFACE_GUARD', 21, g.feet(21, Z1 + 4), Z1 + 4)

    # ------------------------------------------------------------ light the rooms
    regions = [
        (X0 + 1, F, YZ0, X0 + D - 1, RY - 1, YZ1),           # barracks and mess
        (X1 - D + 1, F, YZ0, X1 - 1, RY - 1, YZ1),           # stables
        (YX0, F, Z0 + 1, KEEP[0] - 2, RY - 1, Z0 + D - 1),   # storerooms
        (KEEP[2] + 2, F, Z0 + 1, YX1, RY - 1, Z0 + D - 1),
        (YX0, F, Z1 - D + 1, GATE[0] - 2, RY - 1, Z1 - 1),   # toll office, cells
        (GATE[2] + 2, F, Z1 - D + 1, YX1, RY - 1, Z1 - 1),
        (kx0 + 1, F, kz0 + 1, kx1 - 1, ktop - 1, kz1 - 1),   # the keep
        (gx0 + 1, WK, gz0 + 1, gx1 - 1, TOWER_TOP - 1, gz1 - 1),
    ]
    for (cx, cz) in TOWERS:
        regions.append((cx - 1, WK, cz - 1, cx + 1, TOWER_TOP - 1, cz + 1))
    return t, regions


def worldgen():
    """The structure keeps its id, pool and set; the set moves to the clear-of-origin placement (salt kept)."""
    W.pool('kajidic_post/start', [W.element(ID, adapted=dict(yard_stand=F, clear_from=SY, clear_inset=0,
                                                                support_depth=6, ramp_rings=2))])
    W.structure('kajidic_post', start_pool='kajidic_post/start', biome_tag='has_structure/kajidic_post',
                size=1, max_distance=48, start_height=1 - BURIED)
    W.structure_set('kajidic_posts', [('kajidic_post', 1)], spacing=32, separation=12, salt=905203,
                    placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/kajidic_post', ['oldrepublic:ghessar_canyons'])


def main(preview_dir=None):
    t, regions = build()
    G.save(t, ID, preview_dir, light_regions=regions)
    worldgen()
    G.report_walk(t, ID, ['HEART', 'SURFACE_GUARD'], extra=[
        ('chest front', (20, F, 7)), ('keep level 2', (21, KF[1] + 1, 12)), ('keep level 3', (21, KF[2] + 1, 12)),
        ('west walk', (8, WK, 20)), ('south walk', (12, WK, 35)), ('gate room', (21, WK, 33)),
        ('NW tower', (5, WK, 5)), ('SE tower', (37, WK, 37)), ('stables', (34, F, 21)), ('cells corridor', (27, F, 33))])
    return t


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else None)
