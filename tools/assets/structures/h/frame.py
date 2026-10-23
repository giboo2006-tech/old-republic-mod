"""The Proving's frame: where Zarvos's Sepulchre lies under the Proving and the stair that joins them.
Shared by proving.py and sepulchre.py so the two halves of the stair always meet.

Coordinates here are the hub template hub/proving's own (95 x 59 x 95, ground layer y 7, feet layer y 8).
PlanetHubs stamps hub/sepulchre at SEPULCHRE_OFFSET in this frame and, when the Proving stands on its
raised-platform fallback, sinks the Sepulchre and joins the two with a walled ladder shaft over
STAIR_LOCAL (x and z of the box; the ladder column at (minX + 2, *, minZ), facing south).

The stair: a switchback well in the box, three flights of five steps.
    z 11        the ladder row (ladder column at x 28, hub y -2..8, on the north wall)
    z 12..13    north landings: hub feet 8 (the Descent chamber's floor) and feet -2
    z 14..18    flights: west lane x 26..28 (flights 1 and 3, going down southward),
                east lane x 30..32 (flight 2, going down northward), spine wall x 29 between them
    z 19..20    south landings: feet 3 and feet -7
The lowest landing opens south through the well's wall into the Sepulchre's processional.
"""
from common import S, AIR, stairs, ladder, wall_block, lantern, chain

SEPULCHRE_OFFSET = (20, -16, 8)            # hub/sepulchre's origin in the Proving's frame
SEPULCHRE_SIZE = (55, 16, 51)
STAIR_LOCAL = (26, 0, 11, 32, 7, 20)       # minX, minY, minZ, maxX, maxY, maxZ (PlanetHubs.STAIR_LOCAL)
STAIR_DOWN = (27, 8, 12)                   # the anchor at the top of the stair
LADDER_X, LADDER_Z = 28, 11
LADDER_Y = (-2, 8)
TOP_FEET = 8
BOTTOM_FEET = -7
EXIT = (27, 31)                            # x span of the opening south out of the lowest landing (z 21..22)
# PlanetHubs.stairLocal (Milestone 6, J1) still derives the stair from STAIR_DOWN with the old stair's shape
# (x -1..+3, y -3..-1, z 0..+6 round the anchor) and probes the gap under that box's corner: in this frame the
# cell below (26, 5, 12), inside the well. A lantern hangs there from the top landing so the probe reads
# ground (no sink) whenever the Proving stands on the land; see the report's requests.
PROBE = (26, 4, 12)

X0, Z0, X1, Z1 = 26, 11, 32, 20


def stair_cells(wall, wall2=None, step='oldrepublic:tomb_brick_stairs', parapet='minecraft:polished_blackstone_brick_wall'):
    """{(x, y, z): state} of the whole stair well in the Proving's frame, from the Sepulchre's processional
    level (y -9) up to the parapet round the opening (y 8). wall / wall2 are callables (x, y, z) -> state
    for the inner and outer rings of the well's walls."""
    wall2 = wall2 or wall
    cells = {}
    ylo, yhi = BOTTOM_FEET - 2, TOP_FEET - 1      # -9 .. 7
    # the walls: two rings round the box
    for y in range(ylo, yhi + 1):
        for x in range(X0 - 2, X1 + 3):
            for z in range(Z0 - 2, Z1 + 3):
                ring = max(X0 - x, x - X1, Z0 - z, z - Z1)
                if ring == 1:
                    cells[(x, y, z)] = wall(x, y, z)
                elif ring == 2:
                    cells[(x, y, z)] = wall2(x, y, z)
                elif ring <= 0:
                    cells[(x, y, z)] = AIR
    # the floor under the lowest landing and the well's bottom
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            cells[(x, ylo, z)] = wall(x, ylo, z)
    # the spine between the lanes
    for y in range(ylo, yhi + 1):
        for z in range(14, 19):
            cells[(29, y, z)] = wall(29, y, z)

    def landing(feet, z0, z1):
        for x in range(X0, X1 + 1):
            for z in range(z0, z1 + 1):
                cells[(x, feet - 1, z)] = wall(x, feet - 1, z)
                cells[(x, feet - 2, z)] = wall(x, feet - 2, z)

    landing(TOP_FEET, Z0, 13)          # incl. the ladder row
    landing(3, 19, 20)
    landing(-2, Z0, 13)
    landing(BOTTOM_FEET, 19, 20)

    def flight(lane, feet_from, south):
        """Five steps down from a landing at feet_from: southward in the west lane, northward in the east."""
        xs = (26, 27, 28) if lane == 'west' else (30, 31, 32)
        for i in range(1, 6):
            z = 13 + i if south else 19 - i
            y = feet_from - 1 - i
            for x in xs:
                cells[(x, y, z)] = stairs(step, 'north' if south else 'south')
                cells[(x, y - 1, z)] = wall(x, y - 1, z)       # a stringer under each step
                for k in range(1, 4):
                    if cells.get((x, y + k, z)) == AIR or (x, y + k, z) not in cells:
                        cells[(x, y + k, z)] = AIR

    flight('west', TOP_FEET, True)     # 8 -> 3
    flight('east', 3, False)           # 3 -> -2
    flight('west', -2, True)           # -2 -> -7
    # the top: the Descent chamber's floor covers the well but for the north landing and the head of flight 1
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if not (x <= 28 and 14 <= z <= 16) and z > 13:
                cells[(x, TOP_FEET - 1, z)] = wall(x, TOP_FEET - 1, z)
    # parapet round the head of flight 1 (spine side and south side) at the chamber's feet layer
    for z in range(14, 17):
        cells[(29, TOP_FEET, z)] = S(parapet, up=True, waterlogged=False)
    for x in range(26, 29):
        cells[(x, TOP_FEET, 17)] = S(parapet, up=True, waterlogged=False)
    # the ladder column on the north wall, from the top landing down to the lower north landing; its top cell
    # stands one above the chamber's floor against a stub of wall, so it is easy to step onto
    for y in range(LADDER_Y[0], LADDER_Y[1] + 1):
        cells[(LADDER_X, y, LADDER_Z)] = ladder('south')
    cells[(LADDER_X, LADDER_Y[0] - 1, LADDER_Z)] = wall(LADDER_X, LADDER_Y[0] - 1, LADDER_Z)
    for x in (LADDER_X - 1, LADDER_X, LADDER_X + 1):
        cells[(x, TOP_FEET, LADDER_Z - 1)] = wall(x, TOP_FEET, LADDER_Z - 1)
    # a lantern on a chain under the top landing, over the lower north landing (also the placer's probe cell)
    cells[(PROBE[0], PROBE[1] + 1, PROBE[2])] = chain()
    cells[PROBE] = lantern(hanging=True)
    # the way out of the lowest landing: south through both rings of the wall
    for x in range(EXIT[0], EXIT[1] + 1):
        for z in (Z1 + 1, Z1 + 2):
            for y in range(BOTTOM_FEET, BOTTOM_FEET + 4):
                cells[(x, y, z)] = AIR
            cells[(x, BOTTOM_FEET - 1, z)] = wall(x, BOTTOM_FEET - 1, z)
    return cells


def to_sepulchre(p):
    """A point of the Proving's frame in the Sepulchre template's own coordinates."""
    return (p[0] - SEPULCHRE_OFFSET[0], p[1] - SEPULCHRE_OFFSET[1], p[2] - SEPULCHRE_OFFSET[2])
