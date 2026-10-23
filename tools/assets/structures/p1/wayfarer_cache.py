"""planet/wayfarer_cache: Tessaly's Wayfarer cache (docs/MILESTONE_6.md 5.5).

PlanetSites places it on the Independent's own world, Selvaren or Varkhast, so it is built in the Wayfarers'
manner rather than either world's: brass and verdigris over dressed tuff, and everything round, as their
holocrons are brass spheres. The ground round it is left to the world (the template sets none above its
footings), so it sits as well in a Selvaren meadow as on Varkhast's ash.

Above ground there is a green copper dome, half sunk and ribbed with brass. It is crowned by a grated
oculus and stands in a ring of dressed tuff. A dromos, an open stair cut into the ground between low walls,
runs down from the north to a lintelled door under the dome's brow. Lantern posts and a waymark stand at
its mouth, by a cold camp.

Inside is a beehive hall. A ledge lies by the door, with the pilgrims' log on a lectern. Then a chasm
crosses the whole hall to dark water at its bottom, with a ladder back up. Three brass plates hang across
the chasm on chains from the dome (PLATFORM_1..3, one block apart as before). Beyond them is the dais, with
the cache (CACHE) between two copper watchers and the Wayfarers' star chart in the wall. Day falls through
the oculus onto the plates. Tessaly teaches at HEART, on the dais. The script checks that no place reached
without the plates comes within WorldArcs.CACHE_REACH (5) of it, and that the plates do reach it.

27 x 25 x 27, buried 15: the dome stands nine layers over the ground. Runtime site with site meta.
"""
import math

from selvaren_lib import AIR, B, Noise, S, Template, check_chests, meta, mod, save, slab, stair

SX, SY, SZ = 27, 25, 27
BURIED = 15
GROUND = BURIED - 1        # the world's ground layer (the template sets only its own works in it)
FLOOR = 7                  # the hall's floor: ledge, plates and dais
FEET = FLOOR + 1
CX, CZ = 13, 16            # the hall's middle in plan
RX, RZ = 6.5, 8.5          # the drum's inside (an ellipse in plan)
WALL = 2.0                 # the drum's wall
SPRING = 12                # the dome springs from this layer
DOME_H = 10.0              # the dome's inside height over the springline
DOME_P = 1.6               # its profile: 2 is a half ellipsoid, less is a beehive
SHELL = 1.25               # the dome's shell
LEDGE_END = 11             # the ledge's last row
CHASM = (12, 18)           # the chasm's rows
WATER_TOP = 2              # water in the chasm's bottom layers 1..2
HEART = (13, FEET, 21)
CACHE = (13, FEET, 23)
PLATES = {1: 13, 2: 15, 3: 17}   # PLATFORM_i: its row
RUN = (12, 13, 14)         # the plates' columns, the door's and the dromos's
REACH = 5.0                # WorldArcs.CACHE_REACH
SEED = 905301

BRASS = 'minecraft:waxed_cut_copper'
BRASS_SLAB = 'minecraft:waxed_cut_copper_slab'
BRASS_STAIRS = 'minecraft:waxed_cut_copper_stairs'
RIB = 'minecraft:waxed_exposed_cut_copper'
RIB_STAIRS = 'minecraft:waxed_exposed_cut_copper_stairs'
GRATE = 'minecraft:waxed_oxidized_copper_grate'
CHAIN = 'minecraft:waxed_copper_chain[axis=y,waterlogged=false]'
BARS = 'minecraft:waxed_copper_bars'
LANTERN = 'minecraft:waxed_copper_lantern'
BULB = 'minecraft:waxed_copper_bulb[lit=true,powered=false]'

# the dome's verdigris, in patches
PANELS = [('minecraft:oxidized_cut_copper', 7), ('minecraft:waxed_weathered_cut_copper', 3),
          ('minecraft:oxidized_copper', 1)]
PANEL_STAIRS = {'minecraft:oxidized_cut_copper': 'minecraft:oxidized_cut_copper_stairs',
                'minecraft:waxed_weathered_cut_copper': 'minecraft:waxed_weathered_cut_copper_stairs',
                'minecraft:oxidized_copper': 'minecraft:oxidized_cut_copper_stairs',
                RIB: RIB_STAIRS, BRASS: BRASS_STAIRS}
DRESSED = [('minecraft:tuff_bricks', 9), ('minecraft:polished_tuff', 2), ('minecraft:chiseled_tuff', 1)]
ROUGH = [('minecraft:tuff', 8), ('minecraft:tuff_bricks', 2), ('minecraft:cobbled_deepslate', 2),
         ('minecraft:deepslate', 1)]
FLOORING = [('minecraft:polished_tuff', 8), ('minecraft:tuff_bricks', 2)]


def pick(choices, noise, x, y, z):
    """A choice by noise (patches, not salt and pepper)."""
    total = sum(w for _, w in choices)
    r = noise.at(x, y, z) * total
    for s, w in choices:
        r -= w
        if r <= 0:
            return s
    return choices[-1][0]


# ---------------------------------------------------------------- the shape

def plan(x, z, grow=0.0):
    return ((x - CX) / (RX + grow)) ** 2 + ((z - CZ) / (RZ + grow)) ** 2


def dome(x, y, z, grow=0.0):
    """Below 1 inside the dome's inner surface (grow 0) or outer surface (grow SHELL), for y >= SPRING."""
    h = max(0.0, y - SPRING) / (DOME_H + grow)
    return plan(x, z, grow) + h ** DOME_P


def hall_cells():
    """The hall's air (drum, dome, chasm above the water) and the chasm's water, as two sets."""
    air, water = set(), set()
    for x in range(SX):
        for z in range(SZ):
            if plan(x, z) > 1.0:
                continue
            chasm = CHASM[0] <= z <= CHASM[1]
            for y in range(1, SY):
                if y < FEET and not chasm:
                    continue
                if y >= SPRING and dome(x, y, z) > 1.0:
                    continue
                (water if y <= WATER_TOP else air).add((x, y, z))
    return air, water


def build():
    t = Template(SX, SY, SZ, seed=SEED, name='planet/wayfarer_cache')
    n = Noise(SEED, 3.0)
    air, water = hall_cells()
    inside = air | water

    # the mass under the floor and the drum's wall, rough where nobody sees it, dressed where they do
    for x in range(SX):
        for z in range(SZ):
            if plan(x, z, WALL) > 1.0:
                continue
            for y in range(0, SPRING):
                p = (x, y, z)
                if p in inside:
                    continue
                if y < FLOOR:
                    t.set(*p, pick(ROUGH, n, x, y, z))
                elif y == FLOOR:
                    t.set(*p, pick(FLOORING, n, x, y, z) if plan(x, z) <= 1.0 else 'minecraft:tuff_bricks')
                elif y == FEET:
                    t.set(*p, 'minecraft:polished_tuff')
                elif y == SPRING - 1:
                    t.set(*p, 'minecraft:chiseled_tuff_bricks' if (x + z) % 4 == 0 else 'minecraft:tuff_bricks')
                else:
                    t.set(*p, pick(DRESSED, n, x, y, z))
    # the dome's shell: verdigris panels, brass springline and oculus ring, eight ribs
    shell = set()
    for x in range(SX):
        for z in range(SZ):
            for y in range(SPRING, SY):
                p = (x, y, z)
                if p in inside or dome(x, y, z, SHELL) > 1.0:
                    continue
                shell.add(p)
    # close the shell where the voxels step: every face of the hall above the springline is covered
    for (x, y, z) in sorted(inside):
        if y < SPRING - 1:
            continue
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y, z + 1), (x, y, z - 1)):
            if q not in inside and q[1] >= SPRING and t.inside(*q):
                shell.add(q)
    for p in sorted(shell):
        x, y, z = p
        dx, dz = x - CX, z - CZ
        if y in (SPRING, GROUND):        # the springline inside, the band at the ground outside
            s = BRASS
        elif dx == 0 or dz == 0 or abs(dx) == abs(dz):
            s = RIB
        else:
            s = pick(PANELS, n, x * 1.3, y * 1.3, z * 1.3)
        t.set(*p, s)
    # the oculus: a grate over the dome's crown, ringed with brass, and a rod for a finial
    top = max(y for (_, y, _) in shell)
    for (x, y, z) in sorted(shell):
        if y >= top - 1 and abs(x - CX) <= 2 and abs(z - CZ) <= 2:
            t.set(x, y, z, BRASS)
    for x in RUN:
        for z in (CZ - 1, CZ, CZ + 1):
            t.set(x, top, z, S(GRATE, waterlogged=False))
            for y in range(SPRING, top):
                if (x, y, z) in shell:
                    t.set(x, y, z, AIR)
                    air.add((x, y, z))
    t.set(CX, top + 1, CZ, S('minecraft:waxed_lightning_rod', facing='up', powered=False, waterlogged=False))
    # four grated portholes in the flanks, between the ribs, to let shafts of day across the hall
    for deg in (22.5, -22.5, 157.5, 202.5):
        a = math.radians(deg)
        for y in (BURIED + 2, BURIED + 3):
            for k in range(0, 40):
                f = k / 4.0
                p = (int(round(CX + math.cos(a) * f)), y, int(round(CZ + math.sin(a) * f * RZ / RX)))
                if p in shell:
                    t.set(*p, S(GRATE, waterlogged=False))
    smooth(t, shell, inside, top)

    for p in sorted(air):
        t.set(*p, AIR)
    for p in sorted(water):
        t.set(*p, 'minecraft:water')
    return t, air, water, shell


def smooth(t, shell, inside, top):
    """Stairs along the dome's outside steps, so it reads as a dome and not a stepped mound: on every shell
    cell whose top is open and lies just outside the ideal surface, a stair of the cell's own copper, its
    high side toward the crown."""
    for (x, y, z) in sorted(shell):
        c = (x, y + 1, z)
        if c in shell or c in inside or not t.inside(*c) or c[1] <= GROUND or t.get(*c) is not None:
            continue
        if c[1] >= top or dome(*c, grow=SHELL) > 1.2:
            continue
        nx, nz = (x - CX) / RX ** 2, (z - CZ) / RZ ** 2
        if abs(nx) >= abs(nz):
            facing = 'west' if nx > 0 else 'east'
        else:
            facing = 'north' if nz > 0 else 'south'
        below = B.name_of(t.get(x, y, z))
        name = PANEL_STAIRS.get(below)
        if name:
            t.set(*c, stair(name, facing))


# ---------------------------------------------------------------- the way in

def dromos(t):
    """An open stair cut down from the north edge to the door, between tuff walls with low parapets."""
    n = Noise(SEED + 3, 2.5)
    last = 5                                   # the last step's row; the door's threshold follows
    for z in range(0, last + 1):
        step_y = GROUND - 1 - z                # z 0: one below the ground
        for x in RUN:
            for y in range(FLOOR, step_y):
                t.set(x, y, z, pick(DRESSED, n, x, y, z))
            t.set(x, step_y, z, stair('minecraft:tuff_brick_stairs', 'north'))
            for y in range(step_y + 1, GROUND + 3):
                t.set(x, y, z, AIR)
        for x in (RUN[0] - 1, RUN[-1] + 1):
            for y in range(FLOOR, GROUND):
                t.set(x, y, z, pick(DRESSED, n, x, y, z))
            t.set(x, GROUND, z, 'minecraft:polished_tuff')
            t.set(x, GROUND + 1, z, 'minecraft:tuff_brick_wall')
        # recessed lamps in the walls
        if z == 3:
            for x in (RUN[0] - 1, RUN[-1] + 1):
                t.set(x, GROUND - 2, z, BULB)
    # posts at the mouth, lanterns on them
    for x in (RUN[0] - 1, RUN[-1] + 1):
        t.set(x, GROUND + 1, 0, 'minecraft:chiseled_tuff_bricks')
        t.set(x, GROUND + 2, 0, slab(BRASS_SLAB))
        t.set(x, GROUND + 3, 0, S(LANTERN, hanging=False, waterlogged=False))
    # the threshold, through the facade (z 6) and the drum's wall (z 7)
    for z in (6, 7):
        for x in RUN:
            t.set(x, FLOOR, z, 'minecraft:polished_tuff')
            for y in range(FEET, FEET + 3):
                t.set(x, y, z, AIR)
        for x in (RUN[0] - 1, RUN[-1] + 1):
            for y in range(FEET, FEET + 3):
                t.set(x, y, z, 'minecraft:polished_tuff' if y != FEET + 1 else 'minecraft:chiseled_tuff_bricks')
    # the facade over the door: a brass lintel, a grated relieving triangle, dressed tuff to the ground
    for x in range(RUN[0] - 2, RUN[-1] + 3):
        for y in range(FLOOR, GROUND + 1):
            if t.get(x, y, 6) is None:
                t.set(x, y, 6, pick(DRESSED, n, x, y, 6))
        t.set(x, GROUND, 6, 'minecraft:polished_tuff')
    for x in range(RUN[0] - 1, RUN[-1] + 2):
        for z in (6, 7):
            t.set(x, FEET + 3, z, BRASS)
    for z in (6, 7):
        for x in RUN:
            t.set(x, FEET + 4, z, S(GRATE, waterlogged=False))
        t.set(CX, FEET + 5, z, S(GRATE, waterlogged=False))


def surface(t, shell):
    """The tuff ring round the dome's foot, the waymark and the cold camp. The ground itself is the world's."""
    n = Noise(SEED + 5, 2.0)
    for x in range(SX):
        for z in range(SZ):
            if (x, GROUND, z) in shell or t.get(x, GROUND, z) is not None:
                continue
            f = dome(x, GROUND, z, SHELL)
            if 1.0 < f <= 1.42:
                t.set(x, GROUND, z, 'minecraft:polished_tuff' if f <= 1.2 else pick(
                    [('minecraft:tuff_bricks', 3), ('minecraft:tuff', 1)], n, x, 0, z))
    # the waymark: a tuff post, a brass cap, a lantern for whoever walks the road after dark
    wx, wz = 8, 2
    t.set(wx, GROUND, wz, 'minecraft:polished_tuff')
    for y, s in ((GROUND + 1, 'minecraft:tuff_bricks'), (GROUND + 2, 'minecraft:tuff_bricks'),
                 (GROUND + 3, 'minecraft:chiseled_tuff_bricks'), (GROUND + 4, 'minecraft:polished_tuff_wall')):
        t.set(wx, y, wz, s)
    t.set(wx, GROUND + 5, wz, S(LANTERN, hanging=False, waterlogged=False))
    t.set(wx + 1, GROUND + 1, wz, stair('minecraft:tuff_brick_stairs', 'west'))
    # the cold camp: a ring of stones round a dead fire, a log to sit on, packs
    fx, fz = 20, 3
    t.set(fx, GROUND + 1, fz, S('minecraft:campfire', lit=False, signal_fire=False, facing='north', waterlogged=False))
    t.set(fx - 1, GROUND + 1, fz + 2, 'minecraft:stripped_spruce_log[axis=x]')
    t.set(fx, GROUND + 1, fz + 2, 'minecraft:stripped_spruce_log[axis=x]')
    t.set(fx + 2, GROUND + 1, fz - 1, 'minecraft:barrel[facing=up,open=false]')
    t.set(fx + 2, GROUND + 1, fz, S('minecraft:brown_carpet'))
    t.set(fx + 2, GROUND + 1, fz + 1, S('minecraft:brown_carpet'))


# ---------------------------------------------------------------- inside

def hall(t, air, water):
    n = Noise(SEED + 7, 2.0)
    # brass lips where you jump from and land
    for x in range(SX):
        for z in (LEDGE_END, CHASM[1] + 1):
            if (x, FEET, z) in air:
                t.set(x, FLOOR, z, BRASS)
    # railings along both lips, open over the plates' run
    for x in range(SX):
        if x in RUN:
            continue
        for z in (LEDGE_END, CHASM[1] + 1):
            if (x, FEET, z) in air:
                t.set(x, FEET, z, S(BARS, waterlogged=False))
    # the plates: brass, slung on chains from the dome
    for i, z in sorted(PLATES.items()):
        for x in RUN:
            t.set(x, FLOOR, z, BRASS if x == CX else 'minecraft:waxed_cut_copper')
            t.anchor('PLATFORM_%d' % i, x, FLOOR, z)
        for x in (RUN[0] - 1, RUN[-1] + 1):
            hang(t, x, FEET, z, air)
    # the ladder up out of the water, on the ledge's face
    for y in range(1, FLOOR + 1):
        t.set(CX, y, CHASM[0], S('minecraft:ladder', facing='south', waterlogged=y <= WATER_TOP))
    # glow lichen on the chasm's walls, low down, and on its floor's edges
    for (x, y, z) in sorted(air):
        if not (WATER_TOP < y < FLOOR - 1) or not (CHASM[0] <= z <= CHASM[1]):
            continue
        faces = {}
        for d, (dx, dz) in (('west', (-1, 0)), ('east', (1, 0)), ('north', (0, -1)), ('south', (0, 1))):
            if t.is_solid(x + dx, y, z + dz) and 'ladder' not in (t.name_at(x + dx, y, z + dz) or ''):
                faces[d] = True
        if faces and n.at(x, y, z) > 0.62 and t.get(x, y, z) == AIR:
            t.set(x, y, z, S('minecraft:glow_lichen', waterlogged=False, **faces))
    # lanterns on chains over the ledge and the dais
    for (x, z) in ((10, 9), (16, 9), (10, 22), (16, 22)):
        hang(t, x, FEET + 3, z, air, lantern=True)


def hang(t, x, y0, z, air, lantern=False):
    """A chain from the dome down to y0 (a lantern at its foot when asked)."""
    y = y0
    while (x, y + 1, z) in air:
        y += 1
    for yy in range(y0, y + 1):
        t.set(x, yy, z, CHAIN)
    if lantern:
        t.set(x, y0, z, S(LANTERN, hanging=True, waterlogged=False))


def ledge(t):
    """The pilgrims' log on its lectern, the Wayfarers' packs, a watcher sitting by the door."""
    mod.lectern(t, 10, FEET, 10, facing='east', book=True)
    t.set(16, FEET, 9, 'minecraft:barrel[facing=up,open=false]')
    t.set(17, FEET, 10, 'oldrepublic:cargo_crate')
    t.set(16, FEET + 1, 9, S('minecraft:waxed_copper_lantern', hanging=False, waterlogged=False))
    t.set(11, FEET, 8, S('minecraft:waxed_copper_golem_statue', copper_golem_pose='sitting', facing='east',
                         waterlogged=False))


def dais(t):
    """The cache between two copper watchers, the star chart in the wall, the Wayfarers' cloth."""
    mod.spot(t, 'HEART', *HEART)
    t.chest(*CACHE, facing='north', loot='chests/wayfarer_cache_m4')
    t.anchor('CACHE', *CACHE)
    for x in (11, 15):
        t.set(x, FEET, CACHE[2], S('minecraft:waxed_oxidized_copper_golem_statue', copper_golem_pose='standing',
                                   facing='north', waterlogged=False))
    for x in (12, 14):
        t.set(x, FEET, 24, BULB)
    # the star chart: three screens in a brass frame
    for x in RUN:
        t.set(x, FEET + 2, 25, S('oldrepublic:holo_screen', facing='north'))
        t.set(x, FEET + 1, 25, BRASS)
        t.set(x, FEET + 3, 25, BRASS)
    for x in (RUN[0] - 1, RUN[-1] + 1):
        for y in range(FEET + 1, FEET + 4):
            t.set(x, y, 25, 'minecraft:waxed_chiseled_copper')
    t.set(8, FEET, 21, 'minecraft:cartography_table')
    t.set(18, FEET, 21, 'minecraft:lodestone')
    mod.banner(t, 8, FEET + 2, 21, 'east', base='cyan')
    mod.banner(t, 18, FEET + 2, 21, 'west', base='cyan')


# ---------------------------------------------------------------- checks

def passable(t, p):
    if not t.inside(*p):
        return p[1] >= BURIED
    s = t.get(*p)
    if s is None:
        return p[1] >= BURIED               # the world's ground below the stand, cleared air above
    name = B.name_of(s)
    if name in ('minecraft:water', 'minecraft:ladder'):
        return True
    return mod.walk_through(s)


def standable(t, p, gone=()):
    x, y, z = p
    if not (passable(t, p) or p in gone) or not (passable(t, (x, y + 1, z)) or (x, y + 1, z) in gone):
        return False
    below = (x, y - 1, z)
    s = t.get(*p)
    if s and B.name_of(s) in ('minecraft:water', 'minecraft:ladder'):
        return True
    return below not in gone and not passable(t, below)


def reached(t, gone=()):
    """Every feet cell a person reaches from the ground outside: walking, a one-block climb, drops, jumps of up
    to four across open air (a sprint jump), swimming and ladders. `gone`: cells taken as air (the plates)."""
    def ok(p):
        return standable(t, p, gone)

    start = [(x, BURIED, z) for x in range(-1, SX + 1) for z in (-1, SZ) if ok((x, BURIED, z))]
    seen = set(start)
    todo = list(start)
    while todo:
        x, y, z = todo.pop()
        cur = t.get(x, y, z)
        climb = cur is not None and B.name_of(cur) in ('minecraft:water', 'minecraft:ladder')
        nxt = []
        if climb:
            nxt += [(x, y + 1, z), (x, y - 1, z)]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for d in range(1, 5):
                qx, qz = x + dx * d, z + dz * d
                if d > 1:
                    mid = (x + dx * (d - 1), y, z + dz * (d - 1))
                    if not ((passable(t, mid) or mid in gone) and (passable(t, (mid[0], y + 1, mid[2]))
                                                                    or (mid[0], y + 1, mid[2]) in gone)):
                        break
                for qy in ([y + 1, y] if d == 1 else [y]):
                    if (qy == y + 1 and not passable(t, (x, y + 2, z))):
                        continue
                    nxt.append((qx, qy, qz))
                # a drop from the edge, or from the far end of a jump
                if passable(t, (qx, y, qz)) or (qx, y, qz) in gone:
                    for qy in range(y - 1, -2, -1):
                        q = (qx, qy, qz)
                        if ok(q):
                            nxt.append(q)
                            break
                        if not (passable(t, q) or q in gone):
                            break
        for q in nxt:
            if q in seen or not (-1 <= q[0] <= SX and -1 <= q[2] <= SZ and 0 <= q[1] < SY + 2):
                continue
            if ok(q):
                seen.add(q)
                todo.append(q)
    return seen


def near_heart(p):
    """How near a person whose feet stand in cell p can bring their position to HEART's middle."""
    hx, hy, hz = HEART[0] + 0.5, HEART[1] + 0.5, HEART[2] + 0.5
    x, y, z = p
    dx = max(x - hx, 0.0, hx - (x + 1))
    dz = max(z - hz, 0.0, hz - (z + 1))
    return math.sqrt(dx * dx + (y - hy) ** 2 + dz * dz)


def check_reach(t):
    plates = {tuple(c) for i in PLATES for c in t.anchors['PLATFORM_%d' % i]}
    without = reached(t, gone=plates)
    close = sorted((round(near_heart(p), 2), p) for p in without if near_heart(p) <= REACH)
    if close:
        raise SystemExit('wayfarer_cache: within %.1f of HEART without the plates: %r' % (REACH, close[:6]))
    nearest = min((near_heart(p), p) for p in without)
    with_plates = reached(t)
    if tuple(HEART) not in with_plates:
        raise SystemExit('wayfarer_cache: the plates do not lead to HEART')
    return nearest


def main():
    t, air, water, shell = build()
    dromos(t)
    surface(t, shell)
    hall(t, air, water)
    ledge(t)
    dais(t)
    lit = mod.light_fill(t, (6, FEET, 6, 20, FEET + 3, 25), level=9, strength=12)
    t.finish()
    check_chests(t)
    nearest = check_reach(t)
    save(t, 'planet/wayfarer_cache', 'wayfarer_cache')
    meta.write('planet/wayfarer_cache', buried=BURIED, terrain='flat', max_slope=4, clearance=8, support_depth=8,
               ramp_rings=2, clear_above=6)
    print('wayfarer_cache: %d cells, %d light blocks, nearest without the plates %.2f from HEART at %r'
          % (t.count(), len(lit), nearest[0], nearest[1]))


if __name__ == '__main__':
    main()
