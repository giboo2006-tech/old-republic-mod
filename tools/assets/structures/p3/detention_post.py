"""site/detention_post: the Imperial detention post on Varkhast (runtime, per player; docs/MILESTONE_6.md 5.5).

The Return's "Amends" (ReturnTask): three Republic prisoners held in a cell block, four guards (two
security droids at the gate, two troopers in the cell corridor) and a warden at his desk. Clean, cold and
efficient: a fenced compound on the ash plain with a gatehouse and checkpoint on its north side (the
entrance: the placer turns it toward the player's approach), two corner guard towers, a cell block of
five barred cells behind iron doors that open with the lever beside each door, a fenced exercise pen,
barracks, a mess, a comms room under a lattice mast and a landing pad with cargo waiting to be loaded.

Anchors (feet cells): HEART (the yard), WARDEN (behind his desk), GUARD_1/2 (the droids at the gate),
GUARD_3/4 (the troopers in the corridor), PRISONER_1..3 (in three of the cells).

Run:  python3 tools/assets/structures/p3/detention_post.py [--preview DIR]
"""
import vk
from vk import S, AIR, Template, mod, meta, Mix, stair, slab, door, lantern, STEP, OPP, RIGHT, LEFT

TEMPLATE = 'site/detention_post'
SIZE = (49, 28, 49)
BURIED = 2
G = 1          # the ground (surface) layer
F = 2          # the feet layer
SEED = 960270

# the fence line (inclusive rectangle) and the gate gap in its north side
FX0, FX1, FZ0, FZ1 = 3, 45, 5, 45
GATE = (22, 26)
# the cell block box (walls included) and its rooms
CB = (9, 27, 39, 45)            # x0, z0, x1, z1 (its back wall is part of the perimeter)
CELL_X0 = (10, 16, 22, 28, 34)  # each cell is x0..x0+4, z 40..44; its door is at (x0, 39), its lever at (x0-1, 3, 38)
CELL_Z = (40, 44)
PRISON_CELLS = (1, 2, 3)        # which cells hold PRISONER_1..3


def build(preview=None):
    t = Template(*SIZE, seed=SEED, name=TEMPLATE)
    rng = t.rng
    sx, sy, sz = SIZE

    ground(t, rng)
    fence(t, rng)
    gatehouse(t, rng)
    tower(t, rng, 4, 6, door_side='east', seed=1)
    tower(t, rng, 40, 40, door_side='north', seed=2)
    cell_block(t, rng)
    exercise_pen(t, rng)
    yard(t, rng)
    landing_pad(t, rng)
    comms(t, rng)
    barracks(t, rng)
    outside(t, rng)

    # people
    mod.spot(t, 'HEART', 24, F, 21)
    mod.spot(t, 'GUARD_1', 22, F, 10)
    mod.spot(t, 'GUARD_2', 26, F, 10)

    lights(t)
    t.finish()
    vk.report(t, TEMPLATE, ground_y=G, walk=((24, F, 1), [
        (24, F, 21), (24, F, 32), (18, F, 42), (24, F, 42), (30, F, 42), (19, F, 37), (31, F, 37),
        (15, 3, 38), (21, 3, 38), (27, 3, 38), (6, 15, 8), (42, 15, 42), (6, F, 40), (12, F, 32), (35, F, 33),
        (7, F, 15), (7, F, 23), (39, F, 22)],
        True))
    t.save(TEMPLATE)
    meta.write(TEMPLATE, buried=BURIED, terrain='flat', max_slope=4, clearance=10, support_depth=8, ramp_rings=2,
               clear_above=8, biomes='#oldrepublic:has_structure/varkhast/detention_post', spawn_radius=64)
    vk.W.biome_tag('has_structure/varkhast/detention_post', ['oldrepublic:varkhast_ashfall', 'oldrepublic:varkhast_redrock'])
    vk.loot_table('chests/varkhast/detention_post', [
        vk.item('oldrepublic:credit_chit', 3, 8),
        vk.item('oldrepublic:medpac', 1, 2),
        vk.item('oldrepublic:durasteel_plate', 1, 3),
        vk.item('minecraft:bread', 1, 3, chance=0.6),
        vk.item('oldrepublic:alloy_plate', 1, 1, chance=0.25),
    ])
    if preview:
        vk.preview(t, '%s/detention_post.png' % preview, TEMPLATE)
        vk.preview(t, '%s/detention_post_cut.png' % preview, TEMPLATE + ' (cut)', views=('se', 'nw'), plans=[2, 15],
                   cut=[6, 9])
    return t


# ---------------------------------------------------------------- ground

def ground(t, rng):
    sx, sy, sz = t.size
    under = vk.under_mix('ash', SEED + 1)
    ash = vk.ground_mix('ash', SEED + 2)
    pave = Mix([('minecraft:smooth_stone', 6), ('minecraft:polished_andesite', 3), ('minecraft:andesite', 1)], SEED + 3, 5.0)
    for x in range(sx):
        for z in range(sz):
            t.set(x, 0, z, under(x, 0, z))
            inside = FX0 < x < FX1 and FZ0 < z < FZ1
            if inside:
                s = pave(x, G, z)
                # expansion joints of dark stone every six cells
                if (x - FX0) % 7 == 0 or (z - FZ0) % 7 == 0:
                    s = 'minecraft:polished_deepslate'
                t.set(x, G, z, s)
            else:
                t.set(x, G, z, ash(x, G, z))
    # the road in from the north, through the gate to the cell block's door
    road = Mix([('or:durasteel_deck', 5), ('minecraft:polished_deepslate', 2), ('minecraft:gravel', 1)], SEED + 4, 3.0)
    for z in range(0, CB[1]):
        for x in range(22, 27):
            t.set(x, G, z, road(x, G, z) if z < FZ0 else 'or:durasteel_deck')
    for z in range(6, CB[1]):
        t.set(22, G, z, 'minecraft:polished_deepslate')
        t.set(27, G, z, 'minecraft:polished_deepslate')


# ---------------------------------------------------------------- the perimeter fence

def fence(t, rng):
    line = []
    for x in range(FX0, FX1 + 1):
        line += [(x, FZ0, 'x'), (x, FZ1, 'x')]
    for z in range(FZ0 + 1, FZ1):
        line += [(FX0, z, 'z'), (FX1, z, 'z')]
    for (x, z, ax) in line:
        if z == FZ0 and 17 <= x <= 31:
            continue  # the gatehouse
        if z == FZ1 and CB[0] <= x <= CB[2]:
            continue  # the cell block's back wall
        post = (ax == 'x' and (x - FX0) % 4 == 0) or (ax == 'z' and (z - FZ0) % 4 == 0) or x in (FX0, FX1) and z in (FZ0, FZ1)
        t.set(x, G, z, 'minecraft:polished_deepslate')
        t.set(x, F, z, 'minecraft:polished_deepslate')
        if post:
            for y in range(F + 1, F + 5):
                t.set(x, y, z, 'or:durasteel_beam[axis=y]')
            k = (x + z) // 4
            t.set(x, F + 5, z, 'or:light_panel_red' if k % 2 == 0 else S('or:imperial_plating_slab', type='bottom'))
        else:
            for y in range(F + 1, F + 4):
                t.set(x, y, z, 'minecraft:iron_bars')
            t.set(x, F + 4, z, vk.chain(ax))


# ---------------------------------------------------------------- the gatehouse and checkpoint

def gatehouse(t, rng):
    wall = Mix([('or:imperial_plating', 8), ('or:imperial_panel', 2)], SEED + 10, 3.0)
    for (bx0, bx1, inner) in ((17, 21, 21), (27, 31, 27)):
        z0, z1 = 2, 8
        for x in range(bx0, bx1 + 1):
            for z in range(z0, z1 + 1):
                t.set(x, G, z, 'or:durasteel_deck')
                edge = x in (bx0, bx1) or z in (z0, z1)
                for y in range(F, F + 6):
                    t.set(x, y, z, wall(x, y, z) if edge else AIR)
                t.set(x, F + 6, z, 'or:imperial_plating')
        # beam corners and a dark plinth band
        for (px, pz) in ((bx0, z0), (bx1, z0), (bx0, z1), (bx1, z1)):
            for y in range(F, F + 6):
                t.set(px, y, pz, 'or:durasteel_beam[axis=y]')
        for x in range(bx0, bx1 + 1):
            for z in (z0, z1):
                if x not in (bx0, bx1):
                    t.set(x, F, z, 'minecraft:polished_deepslate')
        for z in range(z0 + 1, z1):
            for x in (bx0, bx1):
                t.set(x, F, z, 'minecraft:polished_deepslate')
        # windows: north (toward the road) and toward the gate passage
        mid = (bx0 + bx1) // 2
        for x in (mid - 1, mid, mid + 1):
            t.set(x, F + 2, z0, 'minecraft:black_stained_glass_pane')
        for z in (4, 5, 6):
            t.set(inner, F + 2, z, 'minecraft:black_stained_glass_pane')
        # the slot the blast door slides into
        for y in range(F, F + 5):
            t.set(inner, y, 5, 'or:durasteel_plating') if y != F + 2 else None
        # a door to the yard (south), plates both sides
        dx0 = mid - 1 if inner == 21 else mid
        vk.double_door(t, dx0, F, z1, 'north')
        for x in (dx0, dx0 + 1):
            t.set(x, F + 2, z1, 'or:durasteel_plating')
            t.set(x, F, z1 + 1, 'minecraft:heavy_weighted_pressure_plate[power=0]')
            t.set(x, F, z1 - 1, 'minecraft:heavy_weighted_pressure_plate[power=0]')
        # inside: a console desk at the north window, a chair, a screen and a locker
        for x in (mid - 1, mid, mid + 1):
            t.set(x, F, z0 + 1, S('or:console', facing='south'))
        t.set(mid, F, z0 + 2, stair('or:imperial_plating_stairs', 'north'))
        t.set(bx0 + 1 if inner == 27 else bx1 - 1, F + 1, 5, S('or:holo_screen', facing='east' if inner == 27 else 'west'))
        t.set(bx1 - 1 if inner == 27 else bx0 + 1, F, z1 - 2, vk.barrel('up'))
        t.set(mid, F + 5, 5, 'or:light_panel')
        # roof: a parapet of bars, a floodlight looking down the road
        for x in range(bx0, bx1 + 1):
            for z in range(z0, z1 + 1):
                if x in (bx0, bx1) or z in (z0, z1):
                    t.set(x, F + 7, z, 'minecraft:iron_bars')
        t.set(mid, F + 7, z0, 'minecraft:polished_deepslate')
        vk.flood_pole(t, mid, F + 7, z0 + 1, h=2, facing='north')
    # the bridge over the gate: a deck on a lintel beam, railed, with the banners of the Empire
    for x in range(21, 28):
        for z in range(2, 9):
            t.set(x, F + 6, z, 'or:imperial_plating' if 22 <= x <= 26 else t.get(x, F + 6, z))
    for x in range(22, 27):
        t.set(x, F + 5, 2, 'or:durasteel_beam[axis=x]')
        t.set(x, F + 5, 8, 'or:durasteel_beam[axis=x]')
        for z in range(3, 8):
            t.set(x, F + 5, z, 'or:durasteel_beam[axis=x]' if z in (3, 7) else 'or:imperial_plating')
        t.set(x, F + 7, 2, 'minecraft:iron_bars')
        t.set(x, F + 7, 8, 'minecraft:iron_bars')
        for z in range(0, 10):
            for y in range(F, F + 5):
                if t.get(x, y, z) is None or z in range(2, 9):
                    t.set(x, y, z, AIR)
    t.set(24, F + 5, 2, 'or:light_panel_red')
    t.set(24, F + 5, 8, 'or:light_panel_red')
    for x in (22, 26):
        mod.banner(t, x, F + 4, 1, 'north', base='black')
        t.set(x, F + 5, 1, S('or:imperial_plating_slab', type='top'))
    # outside: a checkpoint boom across the road, concrete teeth, two warning lights
    # the boom stands raised: a striped arm up beside its post
    t.set(21, F, 1, 'minecraft:polished_deepslate')
    t.set(21, F + 1, 1, 'minecraft:polished_deepslate_wall')
    for y in range(F + 2, F + 5):
        t.set(21, y, 1, S('end_rod', facing='up'))
    t.set(21, F + 1, 0, 'or:light_panel_red')
    for x in (18, 19, 20, 28, 29, 30):
        if (x + rng.randrange(2)) % 2 == 0:
            t.set(x, F, 0, stair('or:imperial_plating_stairs', 'north'))
        else:
            t.set(x, F, 0, slab('minecraft:polished_deepslate_slab', 'bottom'))
    for x in (16, 32):
        vk.flood_pole(t, x, F, 1, h=4, facing='north')


# ---------------------------------------------------------------- guard towers

def tower(t, rng, x0, z0, door_side, seed):
    """A 5x5 plated shaft with a ladder inside, a 7x7 watch cab at the top with a railing, a roof and
    searchlights; the shaft's door is 2 wide on door_side."""
    x1, z1 = x0 + 4, z0 + 4
    cab_floor = F + 12
    wall = Mix([('or:imperial_plating', 8), ('or:imperial_panel', 2), ('or:durasteel_plating', 1)], SEED + 20 + seed, 3.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, G, z, 'or:durasteel_deck')
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(F, cab_floor):
                t.set(x, y, z, (wall(x, y, z) if edge else AIR))
            if edge:
                t.set(x, F, z, 'minecraft:polished_deepslate')
    for (px, pz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(F, cab_floor):
            t.set(px, y, pz, 'or:durasteel_beam[axis=y]')
    # slit windows up the shaft
    for y in range(F + 4, cab_floor - 1, 3):
        t.set(x0 + 2, y, z0, 'minecraft:black_stained_glass_pane')
        t.set(x0 + 2, y, z1, 'minecraft:black_stained_glass_pane')
        t.set(x0, y, z0 + 2, 'minecraft:black_stained_glass_pane')
        t.set(x1, y, z0 + 2, 'minecraft:black_stained_glass_pane')
    # door
    if door_side == 'east':
        cells = [(x1, z0 + 1), (x1, z0 + 2)]
        facing = 'west'
    else:
        cells = [(x0 + 1, z0), (x0 + 2, z0)]
        facing = 'south'
    for (cx, cz) in cells:
        for y in range(F, F + 3):
            t.set(cx, y, cz, AIR)
        t.set(cx, F + 2, cz, 'or:durasteel_plating')
    (ax_, az_), (bx_, bz_) = cells
    door(t, ax_, F, az_, facing, hinge='left')
    door(t, bx_, F, bz_, facing, hinge='right')
    ox, oz = STEP[OPP[facing]]
    for (cx, cz) in cells:
        t.set(cx + ox, F, cz + oz, 'minecraft:heavy_weighted_pressure_plate[power=0]')
        t.set(cx - ox, F, cz - oz, 'minecraft:heavy_weighted_pressure_plate[power=0]')
        t.set(cx, F + 3, cz, 'or:light_panel_red')
    # the ladder on the inner face of the wall opposite the door, up through a hatch in the cab floor
    if door_side == 'east':
        lx, lz, lf = x0 + 1, z0 + 2, 'east'
    else:
        lx, lz, lf = x0 + 2, z1 - 1, 'north'
    for y in range(F, cab_floor + 1):
        t.set(lx, y, lz, S('ladder', facing=lf, waterlogged=False))
    t.set(x0 + 2, F + 6, z0 + 2 if door_side == 'east' else z0 + 1, 'or:light_panel')
    # the cab
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if (x, z) != (lx, lz):
                t.set(x, cab_floor, z, 'or:durasteel_deck')
            edge = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            for y in range(cab_floor + 1, cab_floor + 4):
                t.set(x, y, z, AIR)
            if edge:
                t.set(x, cab_floor + 1, z, 'minecraft:iron_bars')
    # under-cab brackets
    for x in range(x0 - 1, x1 + 2):
        t.set(x, cab_floor - 1, z0 - 1, stair('or:imperial_plating_stairs', 'south', 'top'))
        t.set(x, cab_floor - 1, z1 + 1, stair('or:imperial_plating_stairs', 'north', 'top'))
    for z in range(z0, z1 + 1):
        t.set(x0 - 1, cab_floor - 1, z, stair('or:imperial_plating_stairs', 'east', 'top'))
        t.set(x1 + 1, cab_floor - 1, z, stair('or:imperial_plating_stairs', 'west', 'top'))
    # corner posts and the roof
    for (px, pz) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
        for y in range(cab_floor + 1, cab_floor + 4):
            t.set(px, y, pz, 'or:durasteel_beam[axis=y]')
    roof = cab_floor + 4
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            t.set(x, roof, z, 'or:imperial_plating')
    for x in range(x0 - 2, x1 + 3):
        t.set(x, roof, z0 - 2, stair('or:imperial_plating_stairs', 'south', 'top'))
        t.set(x, roof, z1 + 2, stair('or:imperial_plating_stairs', 'north', 'top'))
    for z in range(z0 - 1, z1 + 2):
        t.set(x0 - 2, roof, z, stair('or:imperial_plating_stairs', 'east', 'top'))
        t.set(x1 + 2, roof, z, stair('or:imperial_plating_stairs', 'west', 'top'))
    t.set(x0 + 2, roof, z0 + 2, 'or:light_panel')
    # in the cab: a console, a chair, a stool by the rail
    cx, cz = (x1 + 1, z0 + 1) if door_side == 'east' else (x1, z1 + 1)
    t.set(x0, cab_floor + 1, z0, S('or:console', facing='south'))
    t.set(x0, cab_floor + 1, z0 + 1, stair('or:imperial_plating_stairs', 'north'))
    # searchlights on the roof corners and an aerial
    t.set(x0 - 1, roof + 1, z0 - 1, 'or:durasteel_beam[axis=y]')
    t.set(x0 - 1, roof + 2, z0 - 1, 'or:light_panel')
    t.set(x0 - 1, roof + 3, z0 - 1, vk.trapdoor('iron_trapdoor', 'south', 'bottom'))
    t.set(x1 + 1, roof + 1, z1 + 1, 'or:durasteel_beam[axis=y]')
    t.set(x1 + 1, roof + 2, z1 + 1, 'or:light_panel')
    t.set(x1 + 1, roof + 3, z1 + 1, vk.trapdoor('iron_trapdoor', 'north', 'bottom'))
    t.set(x1 + 1, roof + 1, z0 - 1, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    t.set(x1 + 1, roof + 2, z0 - 1, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    t.set(x1 + 1, roof + 3, z0 - 1, 'or:light_panel_red')


# ---------------------------------------------------------------- the cell block

def cell_block(t, rng):
    x0, z0, x1, z1 = CB
    top = F + 5           # last interior layer of the wings (ceiling at F + 6)
    hall_top = F + 7      # the hall rises higher
    wall = Mix([('or:imperial_plating', 8), ('or:imperial_panel', 2), ('minecraft:polished_deepslate', 1)], SEED + 30, 3.0)
    inner = Mix([('or:durasteel_plating', 6), ('minecraft:polished_andesite', 2), ('minecraft:smooth_stone', 1)], SEED + 32, 3.0)
    floor = Mix([('or:durasteel_deck', 6), ('minecraft:polished_deepslate', 1)], SEED + 31, 3.0)
    roof = Mix([('or:imperial_plating', 6), ('minecraft:polished_deepslate', 2), ('or:durasteel_plating', 1)], SEED + 33, 4.0)
    hx0, hx1 = 17, 32     # the hall's walls
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, G, z, floor(x, G, z))
            edge = x in (x0, x1) or z in (z0, z1)
            in_hall = hx0 <= x <= hx1 and z0 <= z <= 35
            ceil = (hall_top + 1) if in_hall else (top + 1)
            for y in range(F, ceil):
                t.set(x, y, z, wall(x, y, z) if edge else AIR)
            t.set(x, ceil, z, roof(x, ceil, z))
            if edge:
                t.set(x, F, z, 'minecraft:polished_deepslate')
    # the hall's raised walls (x 17 and 32 above the wing roofs, and its south wall z 35)
    for y in range(top + 1, hall_top + 1):
        for z in range(z0, 36):
            t.set(hx0, y, z, wall(hx0, y, z))
            t.set(hx1, y, z, wall(hx1, y, z))
        for x in range(hx0, hx1 + 1):
            t.set(x, y, 35, wall(x, y, 35))
    # partitions: north rooms | corridor | cells
    for z in range(z0 + 1, 36):
        for y in range(F, top + 1):
            t.set(hx0, y, z, inner(hx0, y, z) if y > F else 'minecraft:polished_deepslate')
            t.set(hx1, y, z, inner(hx1, y, z) if y > F else 'minecraft:polished_deepslate')
    for x in range(x0 + 1, x1):
        for y in range(F, top + 1):
            t.set(x, y, 35, inner(x, y, 35) if y > F else 'minecraft:polished_deepslate')
    # the cell fronts (bars) and dividers
    for x in range(x0 + 1, x1):
        for y in range(F, top + 1):
            t.set(x, y, 39, 'minecraft:iron_bars' if y <= F + 3 else 'or:imperial_plating')
    for i, cx0 in enumerate(CELL_X0):
        if cx0 - 1 > x0:
            for z in range(39, z1):
                for y in range(F, top + 1):
                    t.set(cx0 - 1, y, z, 'or:durasteel_beam[axis=y]' if z == 39 else inner(cx0 - 1, y, z))
    # doors in the partitions
    def opening(cells, h=3):
        for (cx, cz) in cells:
            for y in range(F, F + h):
                t.set(cx, y, cz, AIR)
    opening([(12, 35), (13, 35)])
    opening([(20, 35), (21, 35)])
    opening([(28, 35), (29, 35)])
    opening([(35, 35), (36, 35)])
    for (cx, cz) in ((12, 35), (13, 35), (20, 35), (21, 35), (28, 35), (29, 35), (35, 35), (36, 35)):
        t.set(cx, F + 3, cz, 'or:durasteel_beam[axis=x]')
    # the main door: double iron doors under a porch, plates both sides, a scanner frame inside
    for x in (24, 25):
        for y in range(F, F + 3):
            t.set(x, y, z0, AIR)
    vk.double_door(t, 24, F, z0, 'south')
    for x in (24, 25):
        t.set(x, F + 2, z0, 'or:durasteel_plating')
        t.set(x, F, z0 - 1, 'minecraft:heavy_weighted_pressure_plate[power=0]')
        t.set(x, F, z0 + 1, 'minecraft:heavy_weighted_pressure_plate[power=0]')
    for x in (22, 27):
        for y in range(F, F + 4):
            t.set(x, y, z0 - 2, 'or:durasteel_beam[axis=y]')
    for x in range(21, 29):
        for z in (z0 - 2, z0 - 1):
            t.set(x, F + 4, z, 'or:imperial_plating' if z == z0 - 1 else stair('or:imperial_plating_stairs', 'south', 'top'))
    t.set(24, F + 3, z0 - 1, 'or:light_panel')
    t.set(25, F + 3, z0 - 1, 'or:light_panel')
    for x in (23, 26):
        for y in range(F, F + 3):
            t.set(x, y, z0 + 1, 'minecraft:iron_bars')
        t.set(x, F + 3, z0 + 1, 'or:light_panel_red')
    for x in (23, 24, 25, 26):
        t.set(x, F + 3, z0 + 1, 'or:durasteel_beam[axis=x]') if x in (24, 25) else None
    # exterior: pilasters proud of the north face, a red light band, a cornice and a parapet
    for x in (x0, 13, hx0, hx1, 36, x1):
        for y in range(F, top + 2):
            t.set(x, y, z0 - 1, 'or:durasteel_beam[axis=y]')
    for x in (x0 + 2, x1 - 2):
        t.set(x, top, z0, 'or:light_panel_red')
    for x in range(x0, x1 + 1):
        t.set(x, top + 1, z1 + 1, stair('or:imperial_plating_stairs', 'north', 'top'))
        if not (hx0 <= x <= hx1):
            t.set(x, top + 1, z0 - 1, stair('or:imperial_plating_stairs', 'south', 'top')) if t.get(x, top + 1, z0 - 1) is None else None
            t.set(x, top + 2, z0, 'minecraft:polished_deepslate_wall')
        t.set(x, top + 2, z1, 'minecraft:polished_deepslate_wall')
    for z in range(z0, z1 + 1):
        t.set(x0 - 1, top + 1, z, stair('or:imperial_plating_stairs', 'east', 'top')) if t.get(x0 - 1, top + 1, z) is None else None
        t.set(x1 + 1, top + 1, z, stair('or:imperial_plating_stairs', 'west', 'top')) if t.get(x1 + 1, top + 1, z) is None else None
        t.set(x0, top + 2, z, 'minecraft:polished_deepslate_wall')
        t.set(x1, top + 2, z, 'minecraft:polished_deepslate_wall')
    for x in range(hx0, hx1 + 1):
        t.set(x, hall_top + 2, z0, 'minecraft:polished_deepslate_wall')
        t.set(x, hall_top + 2, 35, 'minecraft:polished_deepslate_wall')
    for z in range(z0, 36):
        t.set(hx0, hall_top + 2, z, 'minecraft:polished_deepslate_wall')
        t.set(hx1, hall_top + 2, z, 'minecraft:polished_deepslate_wall')
    # the hall's clerestory and its banners over the porch
    for x in range(hx0 + 2, hx1 - 1):
        if x % 3 != 0:
            t.set(x, hall_top - 1, z0, 'minecraft:black_stained_glass_pane')
    for x in (20, 29):
        mod.banner(t, x, hall_top, z0 - 1, 'north', base='black')
        mod.banner(t, x + 1 if x == 20 else x - 1, hall_top, z0 - 1, 'north', base='red')
    # slit windows in the wings
    for x in (11, 15, 34, 37):
        t.set(x, F + 3, z0, 'minecraft:black_stained_glass_pane')
        t.set(x, F + 4, z0, 'minecraft:black_stained_glass_pane')
    for cx0 in CELL_X0:
        t.set(cx0 + 2, top, z1, 'minecraft:iron_bars')
    # pipes along the south face and the roof plant
    for x in range(x0 + 1, x1):
        t.set(x, F + 1, z1 + 1, S('or:durasteel_pipes', axis='x'))
    for (px, pz) in ((12, 33), (13, 33), (36, 41), (36, 42), (11, 41)):
        t.set(px, top + 2, pz, 'or:durasteel_vent')
    for x in range(34, 38):
        t.set(x, top + 2, 33, S('or:durasteel_pipes', axis='x'))

    # ---- the hall: the warden's desk, his screens, a bench for new arrivals, lockers
    # the warden's desk across the hall, facing the door: arrivals stand before it
    for x in range(21, 29):
        t.set(x, F, 31, S('or:console', facing='north') if 22 <= x <= 27 else slab('or:durasteel_plating_slab', 'top'))
    for x in (22, 23, 26, 27):
        t.set(x, F + 2, 34, S('or:holo_screen', facing='north'))
    t.set(24, F + 1, 34, S('or:holo_screen', facing='north'))
    t.set(25, F + 1, 34, S('or:holo_screen', facing='north'))
    for x in range(22, 28):
        t.set(x, F, 34, 'or:durasteel_plating' if x not in (24, 25) else slab('or:durasteel_plating_slab', 'top'))
    mod.spot(t, 'WARDEN', 24, F, 32)
    for x in (24, 25):
        for z in (29, 30):
            t.set(x, F, z, 'minecraft:red_carpet')
    # the bench for new arrivals and the evidence lockers
    for z in (29, 30, 31):
        t.set(18, F, z, stair('or:imperial_plating_stairs', 'west'))
    for z in (30, 31, 32, 33):
        t.set(31, F + 1, z, vk.trapdoor('iron_trapdoor', 'west', 'top'))
        t.set(31, F, z, 'or:durasteel_plating')
    for (x, z) in ((18, 34), (31, 34)):
        t.set(x, F, z, vk.barrel('up'))
    mod.banner(t, 24, F + 6, 34, 'north', base='black')
    mod.banner(t, 25, F + 6, 34, 'north', base='black')
    for x in (19, 24, 25, 30):
        t.set(x, hall_top + 1, 31, 'or:light_panel')

    # ---- interrogation room (west): the chair, the droid on its chain, a console, a drain
    t.set(13, F, 31, stair('or:imperial_plating_stairs', 'south'))
    t.set(12, F, 31, 'minecraft:iron_bars')
    t.set(14, F, 31, 'minecraft:iron_bars')
    t.set(14, top, 30, vk.chain('y'))
    t.set(14, top - 1, 30, 'minecraft:black_concrete')
    t.set(14, top - 2, 30, S('minecraft:observer', facing='down', powered=False))
    t.set(10, F, 29, S('or:console', facing='east'))
    t.set(10, F + 1, 30, S('or:holo_screen', facing='east'))
    t.set(10, F, 30, 'or:durasteel_plating')
    t.set(13, G, 33, 'or:durasteel_grate')
    t.set(16, F, 28, vk.barrel('up'))
    t.set(16, F, 29, vk.barrel('up'))
    t.set(13, top + 1, 31, 'or:light_panel_red')
    t.set(11, top + 1, 29, 'or:light_panel')

    # ---- guard room (east): bunks, lockers, a card table, the rifle rack
    vk.bed(t, 37, F, 30, 'north', 'gray')
    vk.bed(t, 38, F, 30, 'north', 'gray')
    vk.bed(t, 38, F, 33, 'south', 'gray')
    for z in (29, 30, 31):
        t.set(33, F + 1, z, vk.trapdoor('iron_trapdoor', 'east', 'top'))
        t.set(33, F, z, 'or:durasteel_plating')
    t.set(35, F, 31, slab('or:durasteel_plating_slab', 'top'))
    t.set(36, F, 31, slab('or:durasteel_plating_slab', 'top'))
    t.set(35, F + 1, 31, S('or:holo_screen', facing='south'))
    t.set(35, F, 32, stair('or:imperial_plating_stairs', 'north'))
    t.set(36, F, 30, stair('or:imperial_plating_stairs', 'south'))
    for x in (34, 35, 36):
        t.set(x, F + 1, 28, S('minecraft:lightning_rod', facing='east', powered=False, waterlogged=False))
    t.set(34, F, 28, 'or:durasteel_plating')
    t.set(35, F, 28, 'or:durasteel_plating')
    t.set(36, F, 28, 'or:durasteel_plating')
    t.set(36, top + 1, 31, 'or:light_panel')

    # ---- the corridor: lights in the ceiling, cameras at the ends, a drain gutter, the troopers' posts
    for x in range(x0 + 1, x1):
        t.set(x, G, 37, 'or:durasteel_grate' if x % 2 == 0 else 'or:durasteel_deck')
    for x in (12, 18, 24, 30, 36):
        t.set(x, top + 1, 37, 'or:light_panel')
    t.set(x0 + 1, top, 36, S('minecraft:observer', facing='west', powered=False))
    t.set(x1 - 1, top, 36, S('minecraft:observer', facing='east', powered=False))
    mod.spot(t, 'GUARD_3', 19, F, 37)
    mod.spot(t, 'GUARD_4', 31, F, 37)

    # ---- the cells: a door at the west of each front, its lever on the corridor side of the pillar
    for i, cx0 in enumerate(CELL_X0):
        door(t, cx0, F, 39, 'south')
        t.set(cx0, F + 2, 39, 'or:imperial_plating')
        t.set(cx0 - 1, F + 1, 38, vk.lever('north'))
        cz0, cz1 = CELL_Z
        # the bunk: a stone shelf on the back wall, a pallet on it; a basin; a high slit window
        for x in (cx0 + 1, cx0 + 2, cx0 + 3):
            t.set(x, F, cz1, slab('minecraft:smooth_stone_slab', 'top'))
        t.set(cx0 + 4, F, cz1, 'minecraft:cauldron')
        t.set(cx0 + 2, top + 1, 42, 'or:light_panel_red')
        if i in PRISON_CELLS:
            n = PRISON_CELLS.index(i) + 1
            mod.spot(t, 'PRISONER_%d' % n, cx0 + 2, F, 42)
            t.set(cx0 + 1, F + 1, cz1, 'minecraft:gray_carpet')
            t.set(cx0 + 2, F + 1, cz1, 'minecraft:gray_carpet')
            if n == 2:
                t.set(cx0 + 4, F, cz0, 'minecraft:decorated_pot')  # the untouched ration tin
            if n == 3:
                t.set(cx0, F, cz1 - 1, 'minecraft:light_gray_carpet')
        elif i == 0:
            # an empty cell, stripped: only the pallet rolled up against the bars
            t.set(cx0 + 3, F, cz0, 'minecraft:gray_carpet')
        else:
            # the last cell holds the prisoners' confiscated gear
            t.set(cx0 + 1, F, cz0, 'or:cargo_crate_republic')
            t.set(cx0 + 1, F, cz0 + 1, 'or:cargo_crate_republic')
            t.set(cx0 + 1, F + 1, cz0, 'or:cargo_crate')
            t.chest(cx0 + 4, F, cz0, facing='west', loot='chests/varkhast/detention_post')
            t.set(cx0 + 2, F + 1, cz1, 'minecraft:blue_carpet')


# ---------------------------------------------------------------- the exercise pen (west of the cell block)

def exercise_pen(t, rng):
    px0, pz0, px1, pz1 = 4, 31, 8, 44
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            t.set(x, G, z, 'minecraft:smooth_stone' if (x + z) % 5 else 'minecraft:polished_andesite')
            edge = x == px0 or z in (pz0, pz1)
            for y in range(F, F + 4):
                t.set(x, y, z, 'minecraft:iron_bars' if edge else AIR)
            t.set(x, F + 4, z, 'minecraft:iron_bars')
            if edge:
                t.set(x, G, z, 'minecraft:polished_deepslate')
    for (x, z) in ((px0, pz0), (px0, pz1), (px1, pz0), (px1, pz1), (px0, 37), (px0, 38)):
        for y in range(F, F + 5):
            t.set(x, y, z, 'or:durasteel_beam[axis=y]')
    # the door from the corridor (west wall of the cell block), plates on both sides
    for z in (37, 38):
        for y in range(F, F + 3):
            t.set(9, y, z, AIR)
        t.set(9, F + 2, z, 'or:durasteel_plating')
        t.set(8, F, z, 'minecraft:heavy_weighted_pressure_plate[power=0]')
        t.set(10, F, z, 'minecraft:heavy_weighted_pressure_plate[power=0]')
    door(t, 9, F, 37, 'east', hinge='left')
    door(t, 9, F, 38, 'east', hinge='right')
    # a bench, a water trough and ash blown in through the mesh
    for z in (33, 34, 35):
        t.set(5, F, z, stair('or:imperial_plating_stairs', 'west'))
    t.set(5, F, 41, 'minecraft:cauldron')
    t.set(6, G, 42, 'or:durasteel_grate')


# ---------------------------------------------------------------- the yard

def yard(t, rng):
    # the standard: a beam mast in the yard's centre with the Empire's banners
    mx, mz = 24, 18
    for y in range(F, F + 11):
        t.set(mx, y, mz, 'or:durasteel_beam[axis=y]')
    t.set(mx, F + 11, mz, 'or:light_panel_red')
    for (f, b) in (('north', 'black'), ('south', 'black'), ('east', 'red'), ('west', 'red')):
        dx, dz = STEP[f]
        mod.banner(t, mx + dx, F + 9, mz + dz, f, base=b)
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1), (0, -1), (0, 1), (-1, 0), (1, 0)):
        t.set(mx + dx, G, mz + dz, 'minecraft:polished_deepslate')
    # droid charging pods by the gate (GUARD_1 and GUARD_2 stand in front of them)
    for x in (20, 28):
        t.set(x, F, 10, 'or:durasteel_plating')
        t.set(x, F + 1, 10, 'or:light_panel_amber')
        t.set(x, F + 2, 10, 'or:durasteel_vent')
        t.set(x, F, 11, S('or:console', facing='south'))
    # the holding cage by the gate, where new arrivals wait in the open: bars on beam posts, a barred roof,
    # an iron door on the yard side opened by the lever on its post, a bench, ash blown in
    cx0, cz0, cx1, cz1 = 14, 11, 17, 14
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            t.set(x, G, z, 'minecraft:polished_deepslate')
            edge = x in (cx0, cx1) or z in (cz0, cz1)
            corner = x in (cx0, cx1) and z in (cz0, cz1)
            for y in range(F, F + 3):
                t.set(x, y, z, 'or:durasteel_beam[axis=y]' if corner else 'minecraft:iron_bars' if edge else AIR)
            t.set(x, F + 3, z, S('or:imperial_plating_slab', type='bottom', waterlogged=False) if edge
                  else 'minecraft:iron_bars')
    door(t, cx1, F, 12, 'west', hinge='right')
    t.set(cx1 + 1, F + 1, cz0, vk.lever('east'))
    t.set(cx0 + 1, F, cz1 - 1, stair('or:imperial_plating_stairs', 'south'))
    t.set(cx0 + 2, F, cz1 - 1, stair('or:imperial_plating_stairs', 'south'))
    t.set(cx0 + 1, F, cz0 + 1, 'minecraft:light_gray_carpet')
    # floodlights on poles at the yard's corners
    for (x, z, f) in ((12, 8, 'south'), (33, 25, 'west'), (13, 24, 'east')):
        vk.flood_pole(t, x, F, z, h=5, facing=f)
    # a cargo sled waiting by the pad with crates for the next shuttle
    for (x, z) in ((29, 13), (30, 13), (29, 14), (30, 14)):
        t.set(x, F, z, S('or:durasteel_deck_slab', type='bottom', waterlogged=False))
    t.set(29, F, 13, 'or:cargo_crate_imperial')
    t.set(30, F, 14, 'or:cargo_crate_imperial')
    t.set(29, F + 1, 13, 'or:cargo_crate')
    t.set(29, F, 15, vk.barrel('up'))
    # ash blown in under the west fence
    ash = Mix([('minecraft:light_gray_concrete_powder', 3), ('minecraft:gravel', 1)], SEED + 40, 2.0)
    vk.drift(t, (FX0 + 1, G, FZ0 + 1, FX0 + 4, G, FZ1 - 1), ash, wind='east', depth=1, reach=3, seed=SEED + 41,
             cap='minecraft:light_gray_carpet', against={'minecraft:polished_deepslate', 'minecraft:iron_bars'},
             floor={'minecraft:smooth_stone', 'minecraft:polished_andesite', 'minecraft:andesite',
                    'minecraft:polished_deepslate'})


# ---------------------------------------------------------------- the landing pad

def landing_pad(t, rng):
    x0, z0, x1, z1 = 32, 8, 43, 17
    deck = Mix([('or:durasteel_deck', 7), ('or:durasteel_plating', 1)], SEED + 50, 3.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set(x, G, z, 'minecraft:gray_concrete' if edge and (x + z) % 2 == 0 else
                  'minecraft:black_concrete' if edge else deck(x, G, z))
    # landing marks: two parallel bars of light grey (no circles)
    for z in range(z0 + 3, z1 - 2):
        t.set(x0 + 3, G, z, 'minecraft:light_gray_concrete')
        t.set(x1 - 3, G, z, 'minecraft:light_gray_concrete')
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        t.set(x, F, z, 'or:light_panel')
        t.set(x, F + 1, z, S('or:imperial_plating_slab', type='bottom', waterlogged=False))
    for (x, z) in ((x0 + 6, z0), (x0 + 6, z1), (x0, z0 + 6), (x1, z0 + 6)):
        t.set(x, F, z, 'or:light_panel_red')
    # the fuel line: pipes along the ground to a pump
    t.set(x0 - 1, F, z1 - 1, S('or:durasteel_pipes', axis='x'))
    t.set(x0 - 2, F, z1 - 1, 'minecraft:iron_block')
    t.set(x0 - 2, F + 1, z1 - 1, S('minecraft:hopper', facing='down', enabled=True))


# ---------------------------------------------------------------- comms room and mast, generator

def comms(t, rng):
    box = vk.prefab(t, 36, G, 20, 9, 5, 4, 'west', rng, kind='lab')
    # the lattice mast south of it: beam corners, rungs of bars, dishes, a red light at the top
    mx0, mz0 = 41, 32
    topy = sy_top = 26
    for y in range(F, topy):
        for (cx, cz) in ((mx0, mz0), (mx0 + 2, mz0), (mx0, mz0 + 2), (mx0 + 2, mz0 + 2)):
            t.set(cx, y, cz, 'or:durasteel_beam[axis=y]')
        if y % 3 == 0:
            for c in ((mx0 + 1, mz0), (mx0 + 1, mz0 + 2), (mx0, mz0 + 1), (mx0 + 2, mz0 + 1)):
                t.set(c[0], y, c[1], 'minecraft:iron_bars')
    # a cap plate on the four corners, then the top beam and its red light
    for x in range(mx0, mx0 + 3):
        for z in range(mz0, mz0 + 3):
            t.set(x, topy, z, S('or:imperial_plating_slab', type='bottom', waterlogged=False))
    t.set(mx0 + 1, topy, mz0 + 1, 'or:durasteel_beam[axis=y]')
    t.set(mx0 + 1, topy + 1, mz0 + 1, 'or:light_panel_red')
    # two dishes on short arms off the mast faces: a plate with a rim of stairs and a feed horn
    for (y, f) in ((F + 15, 'north'), (F + 19, 'west')):
        dx, dz = STEP[f]
        rx, rz = STEP[RIGHT[f]]
        ax, az = mx0 + 1 + dx, mz0 + 1 + dz          # the face cell between two corners
        t.set(ax, y, az, S('or:durasteel_beam', axis=vk.axis_of(f)))
        cx, cz = ax + dx, az + dz                      # the dish's middle
        t.set(cx, y, cz, 'or:durasteel_plating')
        t.set(cx + rx, y, cz + rz, stair('or:imperial_plating_stairs', LEFT[f]))
        t.set(cx - rx, y, cz - rz, stair('or:imperial_plating_stairs', RIGHT[f]))
        t.set(cx, y + 1, cz, stair('or:imperial_plating_stairs', OPP[f]))
        t.set(cx, y - 1, cz, stair('or:imperial_plating_stairs', OPP[f], 'top'))
        t.set(cx + dx, y, cz + dz, S('minecraft:lightning_rod', facing=f, powered=False, waterlogged=False))
    for x in range(mx0 - 1, mx0 + 4):
        for z in range(mz0 - 1, mz0 + 4):
            t.set(x, G, z, 'minecraft:polished_deepslate')
    # the generator: a plated housing, vents, pipes into the cell block, amber light
    gx0, gz0 = 40, 36
    for x in range(gx0, gx0 + 4):
        for z in range(gz0, gz0 + 3):
            for y in range(F, F + 3):
                outer = x in (gx0, gx0 + 3) or z in (gz0, gz0 + 2) or y == F + 2
                t.set(x, y, z, ('or:durasteel_vent' if (x + y) % 2 == 0 else 'or:durasteel_plating') if outer else 'or:durasteel_pipes[axis=x]')
    t.set(gx0 + 1, F + 1, gz0, 'or:light_panel_amber')
    t.set(gx0 + 2, F + 1, gz0 + 2, 'or:light_panel_amber')
    t.set(gx0 + 1, F + 3, gz0 + 1, 'or:durasteel_vent')
    for x in range(CB[2] + 1, gx0):
        t.set(x, F + 1, gz0 + 1, S('or:durasteel_pipes', axis='x'))


# ---------------------------------------------------------------- barracks and mess (west side)

def barracks(t, rng):
    vk.prefab(t, 4, G, 12, 7, 7, 4, 'east', rng, kind='bunks')
    vk.prefab(t, 4, G, 21, 7, 5, 4, 'east', rng, kind='mess')


# ---------------------------------------------------------------- outside the fence

def outside(t, rng):
    ash = Mix([('minecraft:light_gray_concrete_powder', 4), ('minecraft:gravel', 2), ('minecraft:tuff', 1)], SEED + 60, 2.0)
    # the wind comes from the west and the north: ash banks against the outer face of the fence there
    vk.drift(t, (0, G, 0, FX0 - 1, G, 48), ash, wind='west', depth=2, reach=3, seed=SEED + 61,
             cap='minecraft:light_gray_carpet', against={'minecraft:polished_deepslate', 'or:durasteel_beam'})
    vk.drift(t, (0, G, 0, 48, G, FZ0 - 1), ash, wind='north', depth=2, reach=3, seed=SEED + 62,
             cap='minecraft:light_gray_carpet',
             against={'minecraft:polished_deepslate', 'or:durasteel_beam', 'oldrepublic:imperial_plating',
                      'oldrepublic:imperial_panel'})
    # dead brush on the bare ground and a few bones of something the post shot
    for _ in range(26):
        x, z = rng.randrange(0, 49), rng.randrange(0, 49)
        if FX0 - 1 <= x <= FX1 + 1 and FZ0 - 1 <= z <= FZ1 + 1:
            continue
        if t.name_at(x, G, z) in ('minecraft:coarse_dirt', 'minecraft:gravel', 'minecraft:tuff') and t.get(x, F, z) is None:
            t.set(x, F, z, vk.pick(rng, [('minecraft:dead_bush', 3), ('minecraft:short_dry_grass', 2), ('or:debris', 1)])
                  if t.name_at(x, G, z) != 'minecraft:tuff' else 'or:debris')


def lights(t):
    # interiors reach light 8 everywhere a person can walk (the visible panels first, the rest invisible)
    for region in ((10, F, 28, 38, F + 7, 44), (18, F, 3, 20, F + 4, 7), (28, F, 3, 30, F + 4, 7),
                   (5, F, 7, 7, F + 14, 9), (41, F, 41, 43, F + 14, 43), (5, F, 13, 9, F + 4, 17),
                   (5, F, 22, 9, F + 4, 24), (37, F, 21, 43, F + 4, 23), (5, F, 32, 8, F + 3, 43)):
        mod.light_fill(t, region, level=8, strength=11)


if __name__ == '__main__':
    a = vk.args()
    build(a.preview)
