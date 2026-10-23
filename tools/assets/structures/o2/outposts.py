"""The two outposts on the treaty line: outpost/republic_post and outpost/imperial_post.

Run: python3 tools/assets/structures/o2/outposts.py [--preview DIR]

Both are real garrisons, 45 x 45 (the same footprint, so Outposts centres them alike), one layer
buried, entrance (the gate) on the north side:

* Republic Forward Post: white and navy prefab plating, clean and disciplined. A walled square with
  four corner watchtowers and a gatehouse; inside, a parade ground with the bounty terminal, the
  lattice comms tower behind it (its mast lamp 10 up and 4 behind the terminal's screen, which the
  client's SiteAmbience blinks), barracks with bunks, a med bay, a mess hall with its galley, a motor
  pool with a half-stripped speeder under a gantry, a supply depot, a landing pad with a fuel line,
  a shooting range, a generator and a water tower.
* Imperial Listening Post: black plating with red light strips, brutal geometry. Battered walls with
  chamfered bastions, a heavy gatehouse with the crimson-lit lintel, the angular sensor spire on the
  command keep (the landmark), barracks, a holding block with its cells, a mess, a motor pool, a
  landing pad and dish arrays on the bastions.

Anchors kept (WorldM3 tests, BountyTerminalBlockEntity): TERMINAL (the bounty terminal of the army,
facing north), OFFICER and GARRISON_1..4 as air feet cells. The garrison is leashed 12 blocks round
the terminal (sites.garrisonLeash), so every garrison post is within 11 blocks of it, and the officer
stands 4 north of the terminal in open sky (the cameos stand 2.5 beside him on the ground there, never
on the terminal).

Also written: the worldgen (pools republic_outpost/start and imperial_outpost/start, both structures,
the outposts set, the biome and structure tags) and the site meta of both templates (Java places
them at runtime too, 400 to 700 blocks out on high ground).
"""
import math

from common import (Template, S, AIR, B, Noise, W, meta, mod, nature, shapes, put, col, box, unset, pick,
                    picker, patches, stairs, slab, trapdoor, lantern, chain_y, bars, beam, pipes, rod, end_rod,
                    barrel, bed, wall_banner, standing_banner, carve, lamp_post, hanging_lamp, crate_pile, bunk,
                    locker, weapon_rack, light_rooms, scatter_on, adapted, cli, save)

SIZE = 45
BURIED = 1
REP_H = 34
IMP_H = 34

# The terminal, the officer and the Republic mast lamp (SiteAmbience: 10 up, 4 behind the screen).
TERMINAL = (22, 1, 21)
OFFICER = (22, 1, 17)
MAST_LAMP = (TERMINAL[0], TERMINAL[1] + 10, TERMINAL[2] + 4)

CHEVRON = [('oldrepublic:republic_chevron', 'white')]
IMPERIAL_FLAG = [('minecraft:rhombus', 'red'), ('minecraft:stripe_bottom', 'red'), ('minecraft:border', 'red')]


def dist(a, b):
    return math.sqrt(sum((a[i] - b[i]) ** 2 for i in range(3)))


# =============================================================================================
# Republic Forward Post
# =============================================================================================

R_WALL = [('or:republic_plating', 12), ('or:republic_panel', 2)]
R_PLINTH = [('or:durasteel_plating', 7), ('or:durasteel_vent', 1)]
R_CAP = slab('or:republic_plating_slab', 'bottom')
R_TRIM = S('blue_concrete')
R_GLASS = S('light_blue_stained_glass_pane', waterlogged=False)
R_FLOOR = [('or:pale_tiles', 10), ('or:durasteel_deck', 1)]
R_DECK = S('or:durasteel_deck')
R_LIGHT = S('or:light_panel')

# Building footprints (x0, z0, x1, z1), inclusive.
R_BARRACKS = (4, 11, 13, 24)
R_MEDBAY = (4, 27, 13, 32)
R_MESS = (6, 34, 18, 41)
R_TOWER = (20, 23, 24, 27)
R_GENERATOR = (26, 23, 29, 27)
R_MOTOR = (31, 9, 41, 21)
R_DEPOT = (31, 23, 41, 27)
R_PAD = (26, 29, 38, 41)
R_RANGE = (19, 30, 25, 41)
R_PARADE = (15, 10, 29, 21)
R_GARRISON = [(17, 1, 12), (30, 1, 15), (15, 1, 28), (16, 1, 17)]  # rifleman, rifleman, medic, sergeant


def r_ground(t):
    """y 0: the yard's worn ground, the margin outside the wall, roads and the parade ground."""
    yard = patches(4101, [('gravel', 7), ('coarse_dirt', 2), ('dirt_path', 1), ('andesite', 1)], 5.0)
    margin = patches(4102, [('grass_block[snowy=false]', 5), ('coarse_dirt', 3), ('gravel', 2), ('dirt', 1)], 4.0)
    n = Noise(4103, 3.0)
    for x in range(SIZE):
        for z in range(SIZE):
            inside = 3 <= x <= 41 and 5 <= z <= 41
            t.set(x, 0, z, yard(x, 0, z) if inside else margin(x, 0, z))
    # The road in from the north, through the gate to the parade ground (frayed at the edges).
    road = patches(4104, [('dirt_path', 5), ('gravel', 3), ('coarse_dirt', 1)], 3.0)
    for z in range(0, 11):
        for x in range(18, 27):
            core = 20 <= x <= 24
            if core or n.at(x, 0, z) > 0.55:
                t.set(x, 0, z, road(x, 0, z))
    # Gravel walks between the buildings.
    for (x0, z0, x1, z1) in ((14, 9, 30, 9), (14, 25, 30, 25), (14, 26, 19, 33), (14, 10, 14, 33),
                             (19, 28, 39, 28), (30, 9, 30, 28)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if n.at(x, 1, z) > 0.25:
                    t.set(x, 0, z, road(x, 0, z))


def r_wall(t, rng):
    """The perimeter: a durasteel plinth, white plating, a slab cap, beam pilasters outside every
    five cells with a navy band, recessed light panels on the inner face, a gate gap in the north."""
    body = patches(4111, R_WALL, 3.0)
    plinth = patches(4112, R_PLINTH, 2.5)
    x0, z0, x1, z1 = 2, 4, 42, 42
    cells = []
    for x in range(x0, x1 + 1):
        cells += [(x, z0, 'north'), (x, z1, 'south')]
    for z in range(z0 + 1, z1):
        cells += [(x0, z, 'west'), (x1, z, 'east')]
    for (x, z, side) in cells:
        if side == 'north' and 20 <= x <= 24:
            continue
        t.set(x, 0, z, R_DECK)
        put(t, x, 1, z, plinth)
        for y in (2, 3, 4):
            put(t, x, y, z, body)
        t.set(x, 5, z, R_CAP)
        along = x if side in ('north', 'south') else z
        dx, dz = B.step(side)
        if along % 5 == 0:
            px, pz = x + dx, z + dz
            if t.inside(px, 1, pz):
                t.set(px, 0, pz, R_DECK)
                col(t, px, pz, 1, 3, beam('y'))
                t.set(px, 4, pz, R_TRIM)
                t.set(px, 5, pz, beam('y'))
                t.set(px, 6, pz, R_CAP)
        if along % 10 == 7:
            t.set(x, 3, z, R_LIGHT)  # recessed lamp, lights both faces


def r_watchtower(t, rng, x0, z0, door_side):
    """A 5 x 5 corner watchtower: beam legs, a clad base with a door, a ladder, a lookout deck at y 9
    under a slab roof, a searchlight and an antenna."""
    x1, z1 = x0 + 4, z0 + 4
    body = patches(4121 + x0 * 7 + z0, R_WALL, 2.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, R_DECK)
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            for y in range(1, 13):
                if corner and y <= 11:
                    t.set(x, y, z, beam('y'))
                elif edge and y <= 4:
                    t.set(x, y, z, S('or:durasteel_plating') if y == 1 else body(x, y, z))
                elif edge and y == 5:
                    t.set(x, y, z, R_TRIM)
                elif not edge and y <= 8:
                    t.set(x, y, z, AIR)
            if not corner:
                t.set(x, 9, z, R_DECK)  # lookout deck
            if edge and not corner:
                t.set(x, 10, z, slab('or:republic_plating_slab', 'bottom'))
            if not edge:
                t.set(x, 10, z, AIR)
                t.set(x, 11, z, AIR)
            t.set(x, 12, z, slab('or:republic_plating_slab', 'bottom') if edge else S('or:republic_plating'))
    # The deck's ring between the legs, and air above the parapet.
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and not (x in (x0, x1) and z in (z0, z1)):
                t.set(x, 11, z, AIR)
    cx, cz = x0 + 2, z0 + 2
    t.set(cx, 9, cz, AIR)  # the ladder hatch
    for y in range(1, 10):
        t.set(cx, y, cz, S('ladder', facing='east', waterlogged=False) if y < 10 else AIR)
    t.set(cx - 1, 9, cz, R_DECK)
    col(t, cx - 1, cz, 1, 8, S('or:durasteel_plating'))  # the ladder's backing column
    # Door on the yard side.
    dx, dz = B.step(door_side)
    if door_side in ('east', 'west'):
        px = x1 if door_side == 'east' else x0
        for z in (cz, cz + 1 if door_side == 'east' else cz - 1):
            t.set(px, 1, z, AIR)
            t.set(px, 2, z, AIR)
        t.set(px, 3, cz, R_LIGHT)
    else:
        pz = z1 if door_side == 'south' else z0
        for x in (cx, cx + 1):
            t.set(x, 1, pz, AIR)
            t.set(x, 2, pz, AIR)
        t.set(cx, 3, pz, R_LIGHT)
    # Searchlight and antenna on the roof.
    t.set(cx, 13, cz, rod('up'))
    t.set(cx, 14, cz, rod('up'))
    t.set(x0 + 1, 11, z0 + 1, S('or:light_panel'))


def r_gatehouse(t, rng):
    """Two pylons and a bridge over the 5-wide gate; navy chevron banners on the outer faces."""
    body = patches(4131, R_WALL, 2.0)
    for (x0, x1) in ((17, 19), (25, 27)):
        for x in range(x0, x1 + 1):
            for z in range(2, 7):
                t.set(x, 0, z, R_DECK)
                for y in range(1, 9):
                    edge_x = x in (x0, x1)
                    edge_z = z in (2, 6)
                    if y == 1:
                        st = S('or:durasteel_plating')
                    elif y == 6:
                        st = R_TRIM
                    else:
                        st = body(x, y, z)
                    t.set(x, y, z, st)
                t.set(x, 9, z, slab('or:republic_plating_slab', 'bottom') if (x in (x0, x1) or z in (2, 6)) else R_DECK)
        for z in (2, 6):
            for x in (x0, x1):
                col(t, x, z, 1, 9, beam('y'))
        t.set((x0 + x1) // 2, 10, 4, S('or:light_panel'))  # floodlight
    # The bridge: a beam lintel each side, a deck between, a rail of bars.
    for x in range(20, 25):
        for z in (2, 6):
            t.set(x, 6, z, beam('x'))
        for z in (3, 4, 5):
            t.set(x, 6, z, R_DECK)
        for z in (2, 6):
            t.set(x, 7, z, bars())
        for y in range(1, 6):
            for z in range(0, 8):
                if unset(t, x, y, z) or B.name_of(t.get(x, y, z)) == 'minecraft:air':
                    t.set(x, y, z, AIR)
    for x in (20, 24):
        t.set(x, 5, 3, R_LIGHT)  # lights under the bridge
    # Door tracks of the open blast gate, slid into the pylons.
    for x in range(20, 25):
        t.set(x, 0, 4, S('or:durasteel_grate'))
    wall_banner(t, 18, 5, 1, 'north', 'blue', CHEVRON)
    wall_banner(t, 26, 5, 1, 'north', 'blue', CHEVRON)
    wall_banner(t, 18, 5, 7, 'south', 'blue', CHEVRON)
    wall_banner(t, 26, 5, 7, 'south', 'blue', CHEVRON)


def r_approach(t, rng):
    """Sandbag chicanes either side of the road outside the gate, and two lamp posts."""
    for (x0, x1) in ((13, 16), (28, 31)):
        for x in range(x0, x1 + 1):
            t.set(x, 1, 1, S('or:canvas'))
            t.set(x, 2, 1, slab('or:canvas_slab', 'bottom') if (x + 1) % 3 else stairs('or:canvas_stairs', 'south'))
            t.set(x, 1, 0, stairs('or:canvas_stairs', 'south'))
    lamp_post(t, 18, 1, 0, 4, head=R_LIGHT, cap=R_CAP)
    lamp_post(t, 26, 1, 0, 4, head=R_LIGHT, cap=R_CAP)


def r_building_shell(t, rng, fp, height, seed, doors=(), windows=(), roof_y=None, trim=True, floor=None):
    """A prefab building: durasteel plinth row, white plating body, a navy band under the slab
    parapet, corner beams standing proud of the roof, a tiled floor and air inside.
    doors: [(side, a0, a1)] gaps 3 high; windows: [(side, a0, a1)] glass at y 2..3."""
    x0, z0, x1, z1 = fp
    roof_y = roof_y or height
    body = patches(seed, R_WALL, 3.0)
    floor = floor or patches(seed + 1, R_FLOOR, 4.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set(x, 0, z, R_DECK if edge else floor(x, 0, z))
            for y in range(1, roof_y):
                if edge:
                    if y == 1:
                        t.set(x, y, z, pick(rng, R_PLINTH))
                    elif y == roof_y - 1 and trim:
                        t.set(x, y, z, R_TRIM)
                    else:
                        t.set(x, y, z, body(x, y, z))
                else:
                    t.set(x, y, z, AIR)
            t.set(x, roof_y, z, S('or:republic_plating') if not edge else S('or:durasteel_plating'))
            if edge:
                t.set(x, roof_y + 1, z, R_CAP)
    for x in (x0, x1):
        for z in (z0, z1):
            col(t, x, z, 1, roof_y + 1, beam('y'))
            t.set(x, roof_y + 2, z, R_CAP)
    for side, a0, a1 in windows:
        for a in range(a0, a1 + 1):
            x, z = _side_cell(fp, side, a)
            for y in (2, 3):
                t.set(x, y, z, R_GLASS)
            dx, dz = B.step(side)
            put(t, x + dx, 1, z + dz, stairs('or:republic_plating_stairs', B.opposite(side), 'top'), only_empty=True)
            put(t, x + dx, 4, z + dz, trapdoor('iron_trapdoor', side, 'top', False), only_empty=True)
    for side, a0, a1 in doors:
        for a in range(a0, a1 + 1):
            x, z = _side_cell(fp, side, a)
            for y in (1, 2, 3):
                t.set(x, y, z, AIR)
            t.set(x, 0, z, R_DECK)
        # frame: beams either side outside, a light over the door, an awning
        dx, dz = B.step(side)
        for a in (a0 - 1, a1 + 1):
            x, z = _side_cell(fp, side, a)
            col(t, x + dx, z + dz, 1, 3, beam('y'))
        for a in range(a0 - 1, a1 + 2):
            x, z = _side_cell(fp, side, a)
            t.set(x + dx, 4, z + dz, slab('or:canvas_slab', 'top') if a0 <= a <= a1 else beam('x' if side in ('north', 'south') else 'z'))
        x, z = _side_cell(fp, side, a0)
        t.set(x, 4, z, R_LIGHT)
    return body


def _side_cell(fp, side, a):
    x0, z0, x1, z1 = fp
    if side == 'north':
        return a, z0
    if side == 'south':
        return a, z1
    if side == 'west':
        return x0, a
    return x1, a


def roof_kit(t, rng, fp, roof_y, solar=0, vents=2, antenna=True, hatch=True, skylight=None):
    """Plant on a flat roof inside the parapet: solar panels (daylight detectors) in rows, vent
    housings, a hatch and an antenna mast, all on roof_y + 1."""
    x0, z0, x1, z1 = fp
    y = roof_y + 1
    free = lambda x, z: t.is_air(x, y, z) and not t.is_air(x, roof_y, z)
    long_x = (x1 - x0) >= (z1 - z0)
    if skylight:
        for (x, z) in skylight:
            t.set(x, roof_y, z, S('or:durasteel_grate'))
    for k in range(solar):
        # a row of panels along the long side, one cell in from the parapet
        if long_x:
            zz = z0 + 2 + 2 * k
            for x in range(x0 + 2, x1 - 1):
                if free(x, zz):
                    t.set(x, y, zz, S('daylight_detector', inverted=False, power=0))
        else:
            xx = x0 + 2 + 2 * k
            for z in range(z0 + 2, z1 - 1):
                if free(xx, z):
                    t.set(xx, y, z, S('daylight_detector', inverted=False, power=0))
    spots = [(x, z) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1) if free(x, z)]
    rng.shuffle(spots)
    placed = 0
    for (x, z) in spots:
        if placed >= vents:
            break
        if any(not t.is_air(x + dx, y, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dz) != (0, 0)
               and x0 < x + dx < x1 and z0 < z + dz < z1):
            continue
        t.set(x, y, z, S('or:durasteel_vent'))
        t.set(x, y + 1, z, slab('or:durasteel_plating_slab', 'bottom'))
        placed += 1
    if hatch:
        for (x, z) in spots:
            if free(x, z):
                t.set(x, y, z, trapdoor('iron_trapdoor', 'north', 'bottom', False))
                break
    if antenna:
        x, z = x1 - 1, z0 + 1
        if free(x, z):
            col(t, x, z, y, y + 1, beam('y'))
            t.set(x, y + 2, z, rod('up'))
            t.set(x, y + 3, z, end_rod('up'))


def r_wall_walk(t, rng):
    """A grated walk on the inside of the north wall either side of the gatehouse: grates at y 3 on
    two rows, a bar rail on the inner row, bracket beams under it and a ladder up at each end."""
    for (xa, xb, ladder_x) in ((6, 16, 7), (28, 38, 37)):
        for x in range(xa, xb + 1):
            t.set(x, 3, 5, S('or:durasteel_grate'))
            t.set(x, 3, 6, S('or:durasteel_grate'))
            t.set(x, 4, 6, bars())
            if x % 5 == 0:
                col(t, x, 6, 1, 2, beam('y'))
                t.set(x, 0, 6, R_DECK)
            for y in (1, 2):
                if unset(t, x, y, 5):
                    t.set(x, y, 5, AIR)
                if unset(t, x, y, 6):
                    t.set(x, y, 6, AIR)
        for y in (1, 2, 3):
            t.set(ladder_x, y, 5, S('ladder', facing='south', waterlogged=False))
        t.set(ladder_x, 4, 6, AIR)
        # a lamp on the rail at the middle of each run
        mid = (xa + xb) // 2
        t.set(mid, 4, 6, S('or:durasteel_plating'))
        t.set(mid, 5, 6, lantern(False))


def shuttle(t, cx, y, zs):
    """A Republic light shuttle parked nose north on the pad (y is the pad's top block layer + 1):
    landing struts, a white hull with the navy flash, a glazed nose, stub wings with cannon and nav
    lights, twin engines and the boarding ramp down at the tail; inside, two seats at the consoles
    and benches."""
    hull = S('white_concrete')
    plate = S('or:republic_plating')
    # struts
    for (dx, dz) in ((-2, 2), (2, 2), (-2, 6), (2, 6)):
        t.set(cx + dx, y, zs + dz, beam('y'))
    # belly, walls, roof
    for z in range(zs + 1, zs + 8):
        for dx in range(-2, 3):
            x = cx + dx
            side = abs(dx) == 2
            t.set(x, y + 1, z, S('or:durasteel_plating') if not side else slab('or:durasteel_plating_slab', 'top'))
            if side:
                t.set(x, y + 2, z, R_TRIM if z % 3 else hull)
                t.set(x, y + 3, z, hull)
                t.set(x, y + 4, z, slab('or:republic_plating_slab', 'bottom'))
            else:
                t.set(x, y + 2, z, AIR)
                t.set(x, y + 3, z, AIR)
                t.set(x, y + 4, z, plate)
    # the nose: chamfered belly, a sloped glazed front
    for dx in (-1, 0, 1):
        t.set(cx + dx, y + 1, zs, stairs('or:durasteel_plating_stairs', 'south', 'top'))
        t.set(cx + dx, y + 2, zs, stairs('or:republic_plating_stairs', 'south'))
        t.set(cx + dx, y + 2, zs + 1, S('light_blue_stained_glass'))
        t.set(cx + dx, y + 3, zs + 1, S('light_blue_stained_glass'))
        t.set(cx + dx, y + 4, zs + 1, stairs('or:republic_plating_stairs', 'south'))
    for dx in (-2, 2):
        t.set(cx + dx, y + 2, zs + 1, stairs('or:republic_plating_stairs', 'south'))
        t.set(cx + dx, y + 3, zs + 1, stairs('or:republic_plating_stairs', 'south'))
        t.set(cx + dx, y + 4, zs + 1, AIR)
    # dorsal fin and sensor
    t.set(cx, y + 5, zs + 5, slab('or:republic_plating_slab', 'bottom'))
    t.set(cx, y + 5, zs + 6, plate)
    t.set(cx, y + 6, zs + 6, slab('or:republic_plating_slab', 'bottom'))
    t.set(cx, y + 5, zs + 7, plate)
    t.set(cx, y + 6, zs + 7, R_TRIM)
    t.set(cx, y + 7, zs + 7, rod('up'))
    t.set(cx, y + 5, zs + 3, S('or:light_panel_red'))
    # stub wings with cannon and nav lights
    for sgn, nav in ((-1, 'or:light_panel_red'), (1, 'or:light_panel')):
        for k in (3, 4, 5):
            for z in range(zs + 3, zs + 7):
                if k == 5 and z == zs + 3:
                    continue
                t.set(cx + sgn * k, y + 3, z, slab('or:republic_plating_slab', 'bottom') if k < 5 else
                      (S(nav) if z == zs + 5 else slab('or:republic_plating_slab', 'bottom')))
        t.set(cx + sgn * 3, y + 3, zs + 2, rod('north'))
        t.set(cx + sgn * 4, y + 2, zs + 4, pipes('z'))
        t.set(cx + sgn * 4, y + 2, zs + 3, rod('north'))
    # engines and the ramp
    for dx in (-2, 2):
        t.set(cx + dx, y + 2, zs + 8, pipes('z'))
        t.set(cx + dx, y + 3, zs + 8, pipes('z'))
        t.set(cx + dx, y + 2, zs + 9, S('or:light_panel'))
        t.set(cx + dx, y + 3, zs + 9, slab('or:durasteel_plating_slab', 'top'))
    for dx in (-1, 1):
        t.set(cx + dx, y + 2, zs + 8, S('or:durasteel_plating'))
        t.set(cx + dx, y + 3, zs + 8, hull)
        t.set(cx + dx, y + 4, zs + 8, slab('or:republic_plating_slab', 'bottom'))
    t.set(cx, y + 1, zs + 8, stairs('or:durasteel_plating_stairs', 'north'))
    t.set(cx, y, zs + 9, stairs('or:durasteel_plating_stairs', 'north'))
    t.set(cx, y + 2, zs + 8, AIR)
    t.set(cx, y + 3, zs + 8, AIR)
    t.set(cx, y + 4, zs + 8, plate)
    # cabin: consoles and seats up front, benches along the sides, a light
    for dx in (-1, 1):
        t.set(cx + dx, y + 2, zs + 2, S('or:console', facing='south'))
        t.set(cx + dx, y + 2, zs + 3, stairs('or:republic_plating_stairs', 'south'))
        for z in (zs + 5, zs + 6):
            t.set(cx + dx, y + 2, z, stairs('or:republic_plating_stairs', 'west' if dx < 0 else 'east'))
    t.set(cx, y + 4, zs + 4, S('or:light_panel'))
    return (cx - 1, y + 2, zs + 2, cx + 1, y + 3, zs + 7)


def r_barracks(t, rng):
    fp = R_BARRACKS
    x0, z0, x1, z1 = fp
    r_building_shell(t, rng, fp, 5, 4141, doors=[('east', 17, 18)],
                     windows=[('east', 13, 14), ('east', 21, 22), ('west', 15, 16), ('west', 20, 21),
                              ('north', 7, 8), ('south', 9, 10)])
    # West bunks (head to the wall), lockers between them.
    for z in range(z0 + 2, z1, 2):
        bunk(t, x0 + 2, 1, z, 'west', 'white')
        if z + 1 < z1:
            locker(t, x0 + 1, 1, z + 1, 'east', S('or:durasteel_plating'))
    # East bunks, clear of the door.
    for z in (z0 + 2, z0 + 4, z1 - 4, z1 - 2):
        bunk(t, x1 - 2, 1, z, 'east', 'white')
        put(t, x1 - 3, 1, z, barrel('east'))  # foot locker
    # North end: the weapons rack under the unit banner; south end: the briefing table and screen.
    weapon_rack(t, x0 + 3, 1, z0 + 1, 'x', 4)
    wall_banner(t, x0 + 4, 3, z0 + 1, 'south', 'blue', CHEVRON)
    for x in range(x0 + 4, x0 + 7):
        put(t, x, 1, z1 - 2, slab('smooth_stone_slab', 'top'))
        put(t, x, 1, z1 - 3, stairs('spruce_stairs', 'north'))
    t.set(x0 + 5, 3, z1, S('or:holo_screen', facing='north'))
    t.set(x0 + 4, 2, z1 - 2, S('flower_pot'))
    for (x, z) in ((x0 + 4, z0 + 4), (x0 + 5, z0 + 9), (x0 + 4, z1 - 4)):
        t.set(x, 5, z, R_LIGHT)
    # Roof plant: a vent stack and a water line from the tower.
    t.set(x0 + 3, 6, z0 + 3, S('or:durasteel_vent'))
    t.set(x0 + 3, 7, z0 + 3, slab('or:durasteel_plating_slab', 'bottom'))
    t.set(x1 - 3, 6, z1 - 3, S('or:durasteel_vent'))
    return (x0 + 1, 1, z0 + 1, x1 - 1, 4, z1 - 1)


def r_medbay(t, rng):
    fp = R_MEDBAY
    x0, z0, x1, z1 = fp
    r_building_shell(t, rng, fp, 5, 4151, doors=[('east', 29, 30)],
                     windows=[('north', 7, 9), ('west', 29, 30)])
    # The red-cross-free Republic med bay: three beds under curtains, a bacta tank, the medicine counter.
    for z in (z0 + 1, z0 + 3):
        bed(t, x0 + 2, 1, z, 'west', 'white')
        t.set(x0 + 3, 1, z + 1, S('white_stained_glass_pane', waterlogged=False))
        t.set(x0 + 3, 2, z + 1, S('white_stained_glass_pane', waterlogged=False))
    t.set(x0 + 1, 3, z0 + 2, S('or:holo_screen', facing='east'))
    # bacta tank in the north-east corner
    tx, tz = x1 - 2, z0 + 1
    t.set(tx, 1, tz, S('or:durasteel_deck'))
    t.set(tx, 2, tz, S('light_blue_stained_glass'))
    t.set(tx, 3, tz, S('light_blue_stained_glass'))
    t.set(tx, 4, tz, S('or:durasteel_vent'))
    t.set(tx - 1, 1, tz, S('water_cauldron', level=3))
    # counter along the south wall
    for x in range(x0 + 2, x0 + 6):
        t.set(x, 1, z1 - 1, S('or:pale_tiles') if x % 2 else S('or:durasteel_plating'))
        t.set(x, 2, z1 - 1, S('brewing_stand', has_bottle_0=(x % 2 == 0), has_bottle_1=True, has_bottle_2=False)
              if x != x0 + 3 else S('or:console', facing='north'))
    t.set(x1 - 1, 1, z1 - 1, S('or:cargo_crate_republic'))
    t.set(x1 - 2, 1, z1 - 1, barrel('north'))
    for (x, z) in ((x0 + 3, z0 + 2), (x1 - 3, z1 - 2)):
        t.set(x, 5, z, R_LIGHT)
    return (x0 + 1, 1, z0 + 1, x1 - 1, 4, z1 - 1)


def r_mess(t, rng):
    fp = R_MESS
    x0, z0, x1, z1 = fp
    r_building_shell(t, rng, fp, 5, 4161, doors=[('north', 11, 12)],
                     windows=[('north', 8, 9), ('north', 14, 15), ('west', 37, 38)])
    # Two tables along z with benches, the galley along the east end behind a serving counter.
    for tx in (x0 + 3, x0 + 7):
        for z in range(z0 + 2, z1 - 1):
            t.set(tx, 1, z, slab('smooth_stone_slab', 'top'))
            t.set(tx - 1, 1, z, stairs('spruce_stairs', 'west'))
            t.set(tx + 1, 1, z, stairs('spruce_stairs', 'east'))
    # A meal left half eaten when the alarm went: a cut cake, cups, a tray.
    t.set(x0 + 3, 2, z0 + 3, S('cake', bites=3))
    t.set(x0 + 3, 2, z0 + 5, S('flower_pot'))
    t.set(x0 + 7, 2, z0 + 2, S('light_weighted_pressure_plate', power=0))
    t.set(x0 + 7, 2, z0 + 4, S('flower_pot'))
    t.set(x0 + 6, 1, z0 + 4, AIR)  # a bench pushed back
    t.set(x0 + 6, 1, z0 + 3, stairs('spruce_stairs', 'north'))
    gx = x1 - 2
    for z in range(z0 + 1, z1):
        t.set(gx, 1, z, S('or:durasteel_plating') if z != z0 + 3 else AIR)
        if z != z0 + 3:
            t.set(gx, 2, z, slab('smooth_stone_slab', 'bottom') if z % 2 else S('flower_pot'))
    kitchen = [S('smoker', facing='west', lit=True), S('furnace', facing='west', lit=False), S('water_cauldron', level=3),
               barrel('west'), S('or:cargo_crate_republic'), barrel('up')]
    for i, z in enumerate(range(z0 + 1, z1)):
        t.set(x1 - 1, 1, z, kitchen[i % len(kitchen)])
    t.set(x1 - 1, 2, z0 + 2, S('or:durasteel_vent'))
    for (x, z) in ((x0 + 3, z0 + 3), (x0 + 7, z1 - 2), (x1 - 2, z0 + 3)):
        t.set(x, 5, z, R_LIGHT)
    wall_banner(t, x0 + 1, 3, z0 + 4, 'east', 'blue', CHEVRON)
    return (x0 + 1, 1, z0 + 1, x1 - 1, 4, z1 - 1)


def r_comms_tower(t, rng):
    """The landmark: a clad comms room, a 3 x 3 lattice to y 27 round a pipe core, the mast lamp at
    y 11 (SiteAmbience), two railed platforms reached by a ladder up the south face from the ground,
    a big dish facing east, a small one facing north-west and an antenna crown with a red aviation
    light."""
    r_building_shell(t, rng, R_TOWER, 6, 4171, doors=[('west', 25, 25)], windows=[('east', 24, 26)])
    # comms room: consoles under the screens on the north wall, a seat, a rack of pipes
    t.set(21, 1, 24, S('or:console', facing='south'))
    t.set(23, 1, 24, S('or:console', facing='south'))
    t.set(22, 3, 23, S('or:holo_screen', facing='south'))
    t.set(21, 3, 23, S('or:holo_screen', facing='south'))
    t.set(23, 2, 23, S('or:holo_screen', facing='south'))
    t.set(22, 1, 25, stairs('spruce_stairs', 'south'))
    t.set(23, 1, 26, S('or:durasteel_vent'))
    t.set(23, 2, 26, pipes('y'))
    t.set(21, 1, 26, barrel('up'))
    t.set(21, 6, 25, R_LIGHT)
    lx, ly, lz = MAST_LAMP
    legs = ((21, 24), (23, 24), (21, 26), (23, 26))
    top = 27
    for y in range(7, top + 1):
        for (x, z) in legs:
            t.set(x, y, z, beam('y'))
        t.set(lx, y, lz, pipes('y'))
        if y % 3 == 1:
            for (x, z) in ((22, 24), (21, 25), (23, 25), (22, 26)):
                t.set(x, y, z, bars())
    t.set(lx, ly, lz, S('sea_lantern'))
    for (x, z) in ((22, 24), (21, 25), (23, 25), (22, 26)):
        t.set(x, ly, z, AIR)
    # the ladder up the south face from the ground to the upper platform, on a conduit column
    t.set(22, 7, 27, R_TRIM)
    for y in range(8, 21):
        t.set(22, y, 27, pipes('y'))
    for y in range(1, 21):
        t.set(22, y, 28, S('ladder', facing='south', waterlogged=False))
    # platforms at 10 and 20 with railings (the ladder comes up through a hatch)
    for py in (10, 20):
        for x in range(20, 25):
            for z in range(23, 29):
                if (x, z) in legs or (x, z) in ((lx, lz), (22, 27), (22, 28)):
                    continue
                t.set(x, py, z, S('or:durasteel_grate'))
                if x in (20, 24) or z in (23, 28):
                    t.set(x, py + 1, z, bars())
        t.set(24, py + 1, 27, S('or:light_panel'))
    # the big dish facing east on an arm at y 17
    for x in (24, 25, 26):
        t.set(x, 17, 25, beam('x'))
    for dy in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dy) == 2 and abs(dz) == 2:
                continue
            ring = abs(dy) == 2 or abs(dz) == 2
            t.set(27, 17 + dy, 25 + dz, S('white_concrete') if not ring else (R_TRIM if (dy + dz) % 2 else S('or:republic_plating')))
    for dy in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dy, dz) != (0, 0):
                t.set(26, 17 + dy, 25 + dz, S('or:republic_plating') if dy == 0 else
                      stairs('or:republic_plating_stairs', 'east', 'top' if dy > 0 else 'bottom'))
    t.set(28, 17, 25, beam('x'))
    t.set(29, 17, 25, end_rod('east'))
    # the small dish facing north-west near the top
    t.set(21, 23, 23, S('white_concrete'))
    t.set(20, 23, 23, S('white_concrete'))
    t.set(21, 24, 23, stairs('or:republic_plating_stairs', 'south', 'bottom'))
    t.set(21, 22, 23, stairs('or:republic_plating_stairs', 'south', 'top'))
    t.set(20, 24, 23, stairs('or:republic_plating_stairs', 'east', 'bottom'))
    # crown: a cap, rods, the red aviation light
    for x in range(21, 24):
        for z in range(24, 27):
            t.set(x, top + 1, z, slab('or:republic_plating_slab', 'bottom'))
    t.set(22, top + 1, 25, S('or:light_panel_red'))
    for (x, z, h) in ((22, 25, 4), (21, 24, 2), (23, 26, 3)):
        for k in range(h):
            t.set(x, top + 2 + k, z, rod('up'))
    t.set(22, top + 6, 25, end_rod('up'))
    return (21, 1, 24, 23, 5, 26)


def r_generator(t, rng):
    x0, z0, x1, z1 = R_GENERATOR
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, R_DECK)
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            for y in (1, 2):
                if corner:
                    t.set(x, y, z, pipes('y'))
                elif edge:
                    t.set(x, y, z, S('or:durasteel_vent') if (x + z + y) % 2 else S('or:durasteel_plating'))
                else:
                    t.set(x, y, z, S('or:durasteel_plating'))
            t.set(x, 3, z, slab('or:durasteel_plating_slab', 'bottom') if edge else S('or:light_panel_amber'))
    # exhaust stacks and the conduit to the tower (in the ground)
    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z1 - 1)):
        col(t, x, z, 4, 6, pipes('y'))
        t.set(x, 7, z, bars())
    t.set(x0 - 1, 0, 25, pipes('x'))
    for z in range(z1 + 1, 29):
        t.set(x0 + 1, 0, z, pipes('z'))


def r_motor_pool(t, rng):
    """An open-fronted vehicle shed: a military speeder in one bay, a half-stripped speeder bike
    under a chain hoist in the other, tool benches and fuel drums along the back."""
    x0, z0, x1, z1 = R_MOTOR
    body = patches(4181, R_WALL, 3.0)
    floor = patches(4182, [('or:durasteel_deck', 6), ('coarse_dirt', 1), ('gravel', 1)], 3.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, floor(x, 0, z))
            back = x == x1
            side = z in (z0, z1)
            for y in range(1, 6):
                if back or (side and x > x0):
                    t.set(x, y, z, S('or:durasteel_plating') if y == 1 else (R_TRIM if y == 5 else body(x, y, z)))
                else:
                    t.set(x, y, z, AIR)
            t.set(x, 6, z, S('or:durasteel_grate') if (x - x0) % 4 == 2 and z0 < z < z1 and x < x1 else S('or:durasteel_plating'))
    for z in range(z0, z1 + 1, 4):
        col(t, x0, z, 1, 5, beam('y'))
    for z in range(z0, z1 + 1):
        t.set(x0, 6, z, beam('z'))
        t.set(x0 - 1, 6, z, slab('or:republic_plating_slab', 'bottom'))
    for z in (z0, z1):
        for x in (x1,):
            col(t, x, z, 1, 7, beam('y'))
    # Bay 1: the military speeder (white hull, navy flash), parked nose to the yard.
    speeder(t, x0 + 2, 1, z0 + 2, 'west')
    # Bay 2: the speeder bike on its stand under the gantry, its engine hanging from the hoist.
    gz = z1 - 4
    for x in range(x0 + 1, x1):
        t.set(x, 5, gz, beam('x'))
    for y in (3, 4):
        t.set(x0 + 4, y, gz, chain_y())
    t.set(x0 + 4, 2, gz, pipes('x'))
    t.set(x0 + 3, 1, gz + 1, slab('or:durasteel_plating_slab', 'top'))
    t.set(x0 + 4, 1, gz + 1, stairs('or:durasteel_plating_stairs', 'east', 'top'))
    t.set(x0 + 5, 1, gz + 1, trapdoor('iron_trapdoor', 'west', 'top', False))
    t.set(x0 + 2, 1, gz + 1, S('or:debris'))
    t.set(x0 + 3, 1, gz + 2, S('or:debris'))
    # Back wall benches.
    bench = [S('smithing_table'), S('anvil', facing='north'), S('grindstone', face='floor', facing='north'),
             S('crafting_table'), barrel('up'), barrel('up'), S('or:cargo_crate'), S('stonecutter', facing='west')]
    for i, z in enumerate(range(z0 + 1, z1)):
        if i % 3 == 2:
            continue
        t.set(x1 - 1, 1, z, bench[i % len(bench)])
    for z in (z0 + 2, z0 + 7, z1 - 2):
        t.set(x1, 3, z, R_LIGHT)
    for (x, z) in ((x0 + 3, z0 + 3), (x0 + 3, z1 - 3), (x0 + 7, z0 + 6)):
        t.set(x, 6, z, R_LIGHT)
    for (x, z) in ((x0 + 2, z0 + 6), (x0 + 6, z0 + 7), (x0 + 5, z0 + 3)):
        put(t, x, 1, z, S('or:scorch_mark'), only_empty=True)
    return (x0 + 1, 1, z0 + 1, x1 - 1, 5, z1 - 1)


def speeder(t, x, y, z, facing):
    """A military landspeeder 3 wide and 5 long hovering a half block up (top slabs), nose toward
    `facing` ('west' here: built along x)."""
    for i in range(5):
        for j in range(3):
            px, pz = x + i, z + j
            if i == 0:
                st = stairs('or:republic_plating_stairs', 'east', 'top') if j != 1 else slab('or:republic_plating_slab', 'top')
            elif i == 4:
                st = S('or:durasteel_plating') if j != 1 else pipes('x')
            else:
                st = slab('or:republic_plating_slab', 'top')
            t.set(px, y, pz, st)
    t.set(x + 1, y + 1, z, R_GLASS)
    t.set(x + 1, y + 1, z + 2, R_GLASS)
    t.set(x + 1, y + 1, z + 1, R_GLASS)
    t.set(x + 2, y + 1, z + 1, stairs('spruce_stairs', 'east'))
    t.set(x + 3, y + 1, z + 1, stairs('spruce_stairs', 'east'))
    t.set(x + 4, y + 1, z, slab('or:durasteel_plating_slab', 'bottom'))
    t.set(x + 4, y + 1, z + 2, slab('or:durasteel_plating_slab', 'bottom'))
    t.set(x + 2, y + 1, z, slab('or:republic_plating_slab', 'bottom'))


def r_depot(t, rng):
    x0, z0, x1, z1 = R_DEPOT
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, S('or:durasteel_deck') if (x + z) % 5 else S('gravel'))
    crates = [('or:cargo_crate_republic', 5), ('or:cargo_crate', 3), ('barrel[facing=up,open=false]', 1)]
    crate_pile(t, rng, x0 + 3, z0, x0 + 5, z0 + 1, 1, crates, 3)
    crate_pile(t, rng, x0 + 7, z0, x1 - 1, z0 + 1, 1, crates, 2)
    crate_pile(t, rng, x0 + 6, z1 - 1, x1 - 1, z1, 1, crates, 3)
    for x in range(x0 + 1, x0 + 3):
        t.set(x, 1, z1, slab('spruce_slab', 'bottom'))  # an empty pallet
    # the loader crane
    cx, cz = x0, z0 + 2
    col(t, cx, cz, 1, 7, beam('y'))
    for x in range(cx, cx + 6):
        t.set(x, 8, cz, beam('x'))
    for y in (6, 7):
        t.set(cx + 5, y, cz, chain_y())
    t.set(cx + 5, 5, cz, S('or:cargo_crate_republic'))
    t.set(cx, 9, cz, R_LIGHT)


def r_pad(t, rng):
    """A raised square landing pad: deck plates with a tiled inner square, white and navy edge
    stripes, corner lights, steps on the west and north, a fuel line to the tank in the corner and
    crates half loaded at its edge."""
    x0, z0, x1, z1 = R_PAD
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, R_DECK)
            edge = x in (x0, x1) or z in (z0, z1)
            inner = x in (x0 + 3, x1 - 3) and z0 + 3 <= z <= z1 - 3 or z in (z0 + 3, z1 - 3) and x0 + 3 <= x <= x1 - 3
            if edge:
                t.set(x, 1, z, R_TRIM if (x + z) % 2 else S('or:republic_plating'))
            elif inner:
                t.set(x, 1, z, S('or:pale_tiles'))
            else:
                t.set(x, 1, z, S('or:durasteel_deck') if (x * 3 + z) % 7 else S('or:durasteel_grate'))
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        t.set(x, 2, z, beam('y'))
        t.set(x, 3, z, R_LIGHT)
    for z in range(z0 + 4, z0 + 9):
        t.set(x0 - 1, 1, z, stairs('or:republic_plating_stairs', 'east'))
    for x in range(x0 + 4, x0 + 9):
        t.set(x, 1, z0 - 1, stairs('or:republic_plating_stairs', 'south'))
    # the shuttle, crates waiting to be loaded and a cargo sled beside it
    cabin = shuttle(t, x0 + 7, 2, z0 + 2)
    crate_pile(t, rng, x0 + 1, z0 + 2, x0 + 2, z0 + 4, 2, [('or:cargo_crate_republic', 3), ('or:cargo_crate', 2)], 2)
    for x in range(x0 + 1, x0 + 4):
        t.set(x, 2, z1 - 2, slab('or:durasteel_deck_slab', 'bottom'))
    t.set(x0 + 1, 3, z1 - 2, S('or:cargo_crate'))
    t.set(x0 + 2, 3, z1 - 2, S('or:cargo_crate_republic'))
    # fuel tank and line
    tx0, tz0 = x1 + 1, z0 + 1
    for x in range(tx0, tx0 + 3):
        for z in range(tz0, tz0 + 3):
            t.set(x, 0, z, R_DECK)
            for y in (1, 2, 3):
                corner = x in (tx0, tx0 + 2) and z in (tz0, tz0 + 2)
                t.set(x, y, z, beam('y') if corner else (S('white_concrete') if y != 2 else R_TRIM))
            t.set(x, 4, z, slab('or:durasteel_plating_slab', 'bottom'))
    for x in range(x1 - 1, tx0):
        t.set(x, 2, tz0 + 1, pipes('x'))
    t.set(tx0 + 1, 5, tz0 + 1, pipes('y'))
    t.set(tx0 + 1, 6, tz0 + 1, S('or:light_panel_red'))
    return cabin


def r_range(t, rng):
    """A shooting range against the south wall: sandbag firing line, three lanes, targets on hay."""
    x0, z0, x1, z1 = R_RANGE
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if z >= z1 - 1:
                t.set(x, 0, z, S('sand') if (x + z) % 3 else S('coarse_dirt'))
            for_lane = x in (x0 + 1, x0 + 3, x0 + 5)
            if z == z0 + 1:
                t.set(x, 1, z, S('or:canvas') if not for_lane else slab('or:canvas_slab', 'bottom'))
    for x in (x0 + 1, x0 + 3, x0 + 5):
        t.set(x, 1, z1 - 1, S('hay_block', axis='y'))
        t.set(x, 2, z1 - 1, S('target', power=0))
    for x in range(x0, x1 + 1):
        t.set(x, 1, z1, S('sand') if x % 2 else S('coarse_dirt'))  # the backstop berm against the wall
        if x % 3 == 0:
            t.set(x, 2, z1, S('sand'))
    t.set(x0, 1, z0 + 1, barrel('up'))
    t.set(x1, 1, z0 + 1, S('or:cargo_crate_republic'))
    lamp_post(t, x0, 1, z0 + 3, 4, head=R_LIGHT, cap=R_CAP)
    lamp_post(t, x1, 1, z0 + 3, 4, head=R_LIGHT, cap=R_CAP)


def r_water_tower(t, rng):
    x0, z0 = 15, 27
    for (x, z) in ((x0, z0), (x0 + 3, z0), (x0, z0 + 3), (x0 + 3, z0 + 3)):
        t.set(x, 0, z, R_DECK)
        col(t, x, z, 1, 6, beam('y'))
    for x in range(x0, x0 + 4):
        for z in range(z0, z0 + 4):
            corner = x in (x0, x0 + 3) and z in (z0, z0 + 3)
            for y in range(7, 11):
                if corner:
                    t.set(x, y, z, beam('y') if y == 7 else AIR)
                else:
                    t.set(x, y, z, R_TRIM if y == 8 else S('white_concrete'))
            t.set(x, 11, z, slab('or:republic_plating_slab', 'bottom') if not corner else AIR)
    for y in range(1, 7):
        t.set(x0 + 2, y, z0 + 2, pipes('y'))  # the feed pipe down the middle
    t.set(x0 + 2, 0, z0 + 2, R_DECK)
    t.set(x0 + 2, 1, z0 + 3, S('water_cauldron', level=3))  # the tap


def r_guard_booth(t, rng):
    x0, z0, x1, z1 = 28, 7, 30, 9
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, R_DECK)
            edge = x in (x0, x1) or z in (z0, z1)
            for y in (1, 2, 3):
                if x in (x0, x1) and z in (z0, z1):
                    t.set(x, y, z, beam('y'))
                elif edge:
                    t.set(x, y, z, R_GLASS if y == 2 else S('or:republic_plating'))
                else:
                    t.set(x, y, z, AIR)
            t.set(x, 4, z, slab('or:republic_plating_slab', 'bottom'))
    t.set(x0, 1, z0 + 1, AIR)
    t.set(x0, 2, z0 + 1, AIR)
    t.set(x0 + 1, 1, z1 - 1, S('or:console', facing='west'))
    t.set(x0 + 1, 3, z0 + 1, R_LIGHT)


def r_emplacement(t, rng):
    """A sandbagged repeater nest covering the gate court from the west."""
    cx, cz = 15, 7
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if (x, z) == (cx, cz):
                continue
            if (x, z) == (cx, cz + 1):
                continue  # the way in
            t.set(x, 1, z, S('or:canvas'))
            facing = 'north' if z < cz else ('south' if z > cz else ('west' if x < cx else 'east'))
            t.set(x, 2, z, stairs('or:canvas_stairs', B.opposite(facing)))
    t.set(cx, 1, cz, slab('or:durasteel_plating_slab', 'bottom'))
    t.set(cx, 2, cz, S('or:durasteel_pipes', axis='z'))
    t.set(cx, 2, cz - 1, rod('north'))


def r_parade(t, rng):
    x0, z0, x1, z1 = R_PARADE
    tile = patches(4191, [('or:durasteel_deck', 8), ('or:durasteel_grate', 1)], 3.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set(x, 0, z, S('or:pale_tiles') if edge else tile(x, 0, z))
    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1)):
        col(t, x, z, 1, 8, bars())
        standing_banner(t, x, 9, z, 'north', 'blue', CHEVRON)
        t.set(x, 0, z, S('or:durasteel_plating'))
    lamp_post(t, x0, 1, z1, 4, head=R_LIGHT, cap=R_CAP)
    lamp_post(t, x1, 1, z1, 4, head=R_LIGHT, cap=R_CAP)
    lamp_post(t, x0, 1, z0 + 5, 4, head=R_LIGHT, cap=R_CAP)
    lamp_post(t, x1, 1, z0 + 5, 4, head=R_LIGHT, cap=R_CAP)


def r_terminal(t):
    x, y, z = TERMINAL
    t.set(x, y, z, S('or:bounty_terminal', faction='republic', facing='north'), nbt={'id': 'oldrepublic:bounty_terminal'})
    t.anchor('TERMINAL', x, y, z)
    t.set(x, 0, z, S('or:republic_plating'))
    for dx in (-1, 0, 1):
        t.set(x + dx, 0, z + 1, S('or:durasteel_plating'))
    mod.spot(t, 'OFFICER', *OFFICER)
    for i, g in enumerate(R_GARRISON, start=1):
        assert dist(g, TERMINAL) <= 11.0, (i, g, dist(g, TERMINAL))
        mod.spot(t, 'GARRISON_%d' % i, *g)


def r_decor(t, rng):
    """Small things on the ground: tufts against the walls outside, grit and a few scorches inside."""
    outside = [(x, 0, z) for x in range(SIZE) for z in range(SIZE)
               if (x <= 1 or x >= 43 or z <= 3 or z >= 43) and B.name_of(t.get(x, 0, z) or '') == 'minecraft:grass_block']
    scatter_on(t, rng, outside, [(S('short_grass'), 6), (S('fern'), 1), (S('bush'), 1)], 0.35)
    inside = [(x, 0, z) for x in range(3, 42) for z in range(5, 42)
              if B.name_of(t.get(x, 0, z) or '') in ('minecraft:gravel', 'minecraft:coarse_dirt', 'minecraft:packed_mud')]
    scatter_on(t, rng, inside, [(S('or:debris'), 3), (S('short_grass'), 1)], 0.04)


def build_republic():
    t = Template(SIZE, REP_H, SIZE, seed=960110, name='outpost/republic_post')
    rng = t.rng
    r_ground(t)
    r_parade(t, rng)
    r_wall(t, rng)
    r_watchtower(t, rng, 1, 3, 'south')
    r_watchtower(t, rng, 39, 3, 'south')
    r_watchtower(t, rng, 1, 39, 'north')
    r_watchtower(t, rng, 39, 39, 'north')
    r_gatehouse(t, rng)
    r_wall_walk(t, rng)
    r_approach(t, rng)
    rooms = [r_barracks(t, rng), r_medbay(t, rng), r_mess(t, rng), r_motor_pool(t, rng), r_comms_tower(t, rng)]
    roof_kit(t, rng, R_BARRACKS, 5, solar=2, vents=2)
    roof_kit(t, rng, R_MEDBAY, 5, solar=1, vents=1, antenna=False)
    roof_kit(t, rng, R_MESS, 5, solar=2, vents=2, antenna=False)
    r_generator(t, rng)
    r_depot(t, rng)
    rooms.append(r_pad(t, rng))
    r_range(t, rng)
    r_water_tower(t, rng)
    r_guard_booth(t, rng)
    r_emplacement(t, rng)
    r_terminal(t)
    # Lamps along the road in.
    for (x, z) in ((19, 9), (25, 9)):
        lamp_post(t, x, 1, z, 4, head=R_LIGHT, cap=R_CAP)
    r_decor(t, rng)
    carve(t, 0, 0, SIZE - 1, SIZE - 1, 1, 6, bank=1, noise=Noise(4199, 3.0))
    light_rooms(t, rooms)
    return t


# =============================================================================================
# Imperial Listening Post
# =============================================================================================

I_WALL = [('or:imperial_plating', 12), ('or:imperial_panel', 2), ('polished_deepslate', 1)]
I_PLINTH = [('polished_deepslate', 5), ('deepslate_tiles', 3), ('cracked_deepslate_tiles', 1)]
I_FLOOR = [('polished_blackstone', 6), ('or:durasteel_deck', 3), ('polished_blackstone_bricks', 1)]
I_DECK = S('polished_blackstone')
I_RED = S('or:light_panel_red')
I_STRIPE = S('red_concrete')
I_GLASS = S('tinted_glass')
I_SLAB = 'or:imperial_plating_slab'
I_STAIR = 'or:imperial_plating_stairs'

# footprints (x0, z0, x1, z1), inclusive
I_GATEHOUSE = (15, 0, 29, 9)
I_BARRACKS = (4, 10, 12, 21)
I_MESS = (4, 24, 12, 34)
I_HOLDING = (32, 10, 40, 19)
I_MOTOR = (32, 22, 40, 30)
I_PAD = (29, 31, 37, 40)
I_KEEP = (14, 23, 28, 36)
I_FORECOURT = (14, 10, 30, 21)
I_GARRISON = [(17, 1, 13), (28, 1, 13), (13, 1, 22), (30, 1, 21)]  # rifleman, rifleman, grenadier, sergeant


def i_ground(t):
    yard = patches(5101, [('gravel', 5), ('coarse_dirt', 3), ('packed_mud', 2), ('blackstone', 1)], 4.0)
    margin = patches(5102, [('coarse_dirt', 4), ('gravel', 3), ('grass_block[snowy=false]', 2), ('dirt', 1)], 3.5)
    for x in range(SIZE):
        for z in range(SIZE):
            inside = 3 <= x <= 41 and 5 <= z <= 41
            t.set(x, 0, z, yard(x, 0, z) if inside else margin(x, 0, z))
    # the scorched approach and the hard standing of the forecourt
    road = patches(5103, [('or:durasteel_deck', 4), ('polished_blackstone', 3), ('gravel', 2)], 3.0)
    n = Noise(5104, 3.0)
    for z in range(0, 10):
        for x in range(18, 27):
            if 20 <= x <= 24 or n.at(x, 0, z) > 0.55:
                t.set(x, 0, z, road(x, 0, z))
    fx0, fz0, fx1, fz1 = I_FORECOURT
    tile = patches(5105, I_FLOOR, 3.0)
    for x in range(fx0, fx1 + 1):
        for z in range(fz0, fz1 + 1):
            edge = x in (fx0, fx1) or z in (fz0, fz1)
            t.set(x, 0, z, I_STRIPE if edge and (x + z) % 4 == 0 else tile(x, 0, z))
    # walks of deck plate between the blocks
    for (x0, z0, x1, z1) in ((13, 9, 13, 36), (31, 9, 31, 31), (13, 22, 31, 22), (4, 22, 12, 23), (32, 20, 40, 21)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if n.at(x, 2, z) > 0.2:
                    t.set(x, 0, z, road(x, 0, z))


def i_wall(t, rng):
    """The perimeter: a battered wall of black plating on a deepslate plinth, a sloped skirt outside,
    angular buttress fins every six cells, a red strip lamp on the inner face between the fins."""
    body = patches(5111, I_WALL, 3.0)
    plinth = patches(5112, I_PLINTH, 2.5)
    x0, z0, x1, z1 = 3, 5, 41, 41
    cells = []
    for x in range(x0, x1 + 1):
        cells += [(x, z0, 'north'), (x, z1, 'south')]
    for z in range(z0 + 1, z1):
        cells += [(x0, z, 'west'), (x1, z, 'east')]
    for (x, z, side) in cells:
        if side == 'north' and 15 <= x <= 29:
            continue  # the gatehouse
        t.set(x, 0, z, I_DECK)
        put(t, x, 1, z, plinth)
        put(t, x, 2, z, plinth)
        for y in (3, 4, 5):
            put(t, x, y, z, body)
        t.set(x, 6, z, slab(I_SLAB, 'bottom'))
        dx, dz = B.step(side)
        ox, oz = x + dx, z + dz
        if t.inside(ox, 1, oz):
            # the sloped skirt outside, its high side against the wall
            t.set(ox, 0, oz, I_DECK)
            t.set(ox, 1, oz, stairs('polished_deepslate_stairs', B.opposite(side)))
        along = x if side in ('north', 'south') else z
        if along % 6 == 0:
            # a buttress fin: two cells out at the foot, stepping back up to the cap
            for k, (h, st) in enumerate(((4, 'polished_deepslate_stairs'), (2, I_STAIR))):
                fx, fz = x + dx * (k + 1), z + dz * (k + 1)
                if not t.inside(fx, 1, fz):
                    continue
                t.set(fx, 0, fz, I_DECK)
                for y in range(1, h):
                    t.set(fx, y, fz, S('polished_deepslate'))
                t.set(fx, h, fz, stairs(st, B.opposite(side)))
        if along % 6 == 3:
            t.set(x, 4, z, I_RED)


def i_bastion(t, rng, cx, cz):
    """A chamfered corner bastion (7 x 7 with cut corners) rising to y 10, a guard room inside, a
    tinted slit on each face, a chamfered parapet, a dish array and a red lamp on the roof."""
    body = patches(5121 + cx * 3 + cz, I_WALL, 2.0)
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if abs(dx) + abs(dz) > 5:
                continue
            x, z = cx + dx, cz + dz
            if not t.inside(x, 0, z):
                continue
            edge = abs(dx) == 3 or abs(dz) == 3 or abs(dx) + abs(dz) == 5
            t.set(x, 0, z, I_DECK if edge else S('or:durasteel_deck'))
            for y in range(1, 10):
                if edge:
                    t.set(x, y, z, S('polished_deepslate') if y <= 2 else (I_STRIPE if y == 8 else body(x, y, z)))
                else:
                    t.set(x, y, z, AIR if y < 9 else S('or:imperial_plating'))
            if edge:
                out = 'north' if dz <= -2 and abs(dz) >= abs(dx) else 'south' if dz >= 2 and abs(dz) >= abs(dx) else \
                    'west' if dx < 0 else 'east'
                t.set(x, 10, z, stairs(I_STAIR, B.opposite(out), 'top'))
    for (dx, dz) in ((0, -3), (0, 3), (-3, 0), (3, 0)):
        if t.inside(cx + dx, 4, cz + dz):
            t.set(cx + dx, 4, cz + dz, I_GLASS)
            t.set(cx + dx, 5, cz + dz, I_GLASS)
    t.set(cx, 9, cz, I_RED)
    # the guard room: a console under a screen, a crate
    t.set(cx - 1, 1, cz - 1, S('or:cargo_crate_imperial'))
    t.set(cx + 1, 1, cz + 1, barrel('up'))
    # roof: a dish array (three angled panels) round a mast
    col(t, cx, cz, 10, 12, beam('y'))
    t.set(cx, 13, cz, rod('up'))
    t.set(cx, 14, cz, I_RED)
    for (dx, dz, f) in ((-1, 0, 'west'), (1, 0, 'east'), (0, -1, 'north')):
        t.set(cx + dx, 11, cz + dz, stairs(I_STAIR, B.opposite(f), 'top'))
        t.set(cx + dx, 12, cz + dz, stairs(I_STAIR, B.opposite(f)))
    t.set(cx, 11, cz + 1, slab(I_SLAB, 'top'))
    return (cx - 2, 1, cz - 2, cx + 2, 8, cz + 2)


def i_bastion_door(t, x, z):
    """A door into a bastion from the yard (two air cells and a red lamp over it)."""
    t.set(x, 1, z, AIR)
    t.set(x, 2, z, AIR)
    t.set(x, 3, z, I_RED)


def i_gatehouse(t, rng):
    """The heavy gatehouse: a lintel block over the 5-wide, 5-high gate standing proud of two
    battered flank towers, its face lit by a crimson strip under a beam; the blast door half down,
    the Imperial banners on the towers, guard rooms with ladders to the roof walk and a heavy
    repeater over the gate."""
    x0, _, x1, z1 = I_GATEHOUSE
    zf = 1  # the front face
    body = patches(5131, I_WALL, 2.5)
    plinth = patches(5132, I_PLINTH, 2.0)
    for x in range(x0, x1 + 1):
        gate = 20 <= x <= 24
        for z in range(zf, z1 + 1):
            t.set(x, 0, z, I_DECK if not gate else S('or:durasteel_deck'))
            # the towers step back: up to y 3 at the front row, y 7 at the second, y 11 behind
            top = 11 if gate or z >= zf + 2 else (3 if z == zf else 7)
            for y in range(1, top + 1):
                if gate and y <= 5:
                    t.set(x, y, z, AIR)
                elif y <= 2:
                    t.set(x, y, z, plinth(x, y, z))
                elif y == 10 and (x in (x0, x1) or z in (zf, zf + 2, z1)):
                    t.set(x, y, z, I_STRIPE)
                else:
                    t.set(x, y, z, body(x, y, z))
            if not gate and z in (zf, zf + 1):
                t.set(x, top + 1, z, stairs(I_STAIR, 'south'))
    # guard rooms in the towers with a door into the gate passage and a ladder to the roof
    for (ax0, ax1, door_x, lad_x, lad_f) in ((x0 + 1, 18, 19, x0 + 1, 'east'), (26, x1 - 1, 25, x1 - 1, 'west')):
        for x in range(ax0, ax1 + 1):
            for z in range(zf + 2, z1):
                t.set(x, 0, z, S('or:durasteel_deck'))
                for y in range(1, 5):
                    t.set(x, y, z, AIR)
        t.set(door_x, 1, zf + 5, AIR)
        t.set(door_x, 2, zf + 5, AIR)
        t.set(door_x, 3, zf + 5, I_RED)
        for y in range(1, 12):
            t.set(lad_x, y, z1 - 1, S('ladder', facing=lad_f, waterlogged=False))
        mid = (ax0 + ax1) // 2
        t.set(mid, 3, zf + 1, I_GLASS)
        t.set(mid, 6, zf + 2, I_GLASS)
        t.set(mid, 1, zf + 2, S('or:console', facing='south'))
        t.set(mid, 3, zf + 2, AIR)
        weapon_rack(t, ax0 + (1 if lad_f == 'east' else 0), 1, zf + 4, 'x', 2)
        t.set(mid, 4, zf + 5, I_RED)
        t.set(ax1 if lad_f == 'east' else ax0, 1, z1 - 1, S('or:cargo_crate_imperial'))
    # the lintel: a crimson light strip across the gate under a beam; the blast door half down
    for x in range(20, 25):
        t.set(x, 6, zf, I_RED)
        t.set(x, 7, zf, beam('x'))
        t.set(x, 5, zf + 2, S('or:durasteel_plating'))
        t.set(x, 4, zf + 2, trapdoor('iron_trapdoor', 'north', 'top', False))
        t.set(x, 0, zf + 2, S('or:durasteel_grate'))
        t.set(x, 0, zf + 6, S('or:durasteel_grate'))
    for x in (19, 25):
        col(t, x, zf, 1, 5, beam('y'))
    for x in (17, 27):
        wall_banner(t, x, 6, zf, 'north', 'black', IMPERIAL_FLAG)
    # the roof walk at y 12: a parapet of stairs leaning out, the repeater over the gate, lamps
    for x in range(x0, x1 + 1):
        for z in range(zf, z1 + 1):
            if t.is_air(x, 12, z) and not t.is_air(x, 11, z):
                edge = x in (x0, x1) or z in (zf + 2, z1) or (z == zf and 20 <= x <= 24)
                if edge:
                    f = 'north' if z <= zf + 2 else 'south' if z == z1 else 'west' if x == x0 else 'east'
                    t.set(x, 12, z, stairs(I_STAIR, B.opposite(f), 'top'))
    for x in range(20, 25):
        t.set(x, 12, zf, stairs(I_STAIR, 'south', 'top'))
        t.set(x, 12, zf + 2, AIR)
    t.set(22, 12, zf + 1, slab('or:durasteel_plating_slab', 'bottom'))
    t.set(22, 13, zf + 1, pipes('z'))
    t.set(22, 13, zf, rod('north'))
    t.set(21, 12, zf + 3, S('or:cargo_crate_imperial'))
    for x in (x0, x1):
        t.set(x, 13, zf + 2, I_RED)
    for (lx, lz) in ((x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        t.set(lx, 12, lz, AIR)
    return [(x0 + 1, 1, zf + 2, 18, 4, z1 - 1), (26, 1, zf + 2, x1 - 1, 4, z1 - 1)]


def i_block(t, rng, fp, height, seed, doors=(), slits=(), skirt=True, roof=True):
    """An Imperial block: a deepslate plinth, black plating walls, a red stripe course under a
    chamfered parapet (stairs leaning out), a stair skirt round the foot, tinted slit windows and
    deep doors with a red lamp. doors: [(side, a0, a1)]; slits: [(side, a)] glazed at y 2..3."""
    x0, z0, x1, z1 = fp
    body = patches(seed, I_WALL, 3.0)
    floor = patches(seed + 1, I_FLOOR, 4.0)
    roof_deck = patches(seed + 2, [('or:durasteel_plating', 5), ('or:durasteel_deck', 3), ('or:durasteel_vent', 1)], 3.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set(x, 0, z, I_DECK if edge else floor(x, 0, z))
            for y in range(1, height):
                if edge:
                    st = pick(rng, I_PLINTH) if y == 1 else (I_STRIPE if y == height - 1 else body(x, y, z))
                    t.set(x, y, z, st)
                else:
                    t.set(x, y, z, AIR)
            if roof:
                t.set(x, height, z, roof_deck(x, height, z) if not edge else S('polished_deepslate'))
    if roof:
        # chamfered parapet: stairs leaning out on the edges, solid corners
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                side = ('north' if z == z0 else 'south' if z == z1 else 'west' if x == x0 else 'east' if x == x1 else None)
                if side is None:
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                t.set(x, height + 1, z, S('polished_deepslate') if corner else stairs(I_STAIR, B.opposite(side), 'top'))
    if skirt:
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x0 <= x <= x1 and z0 <= z <= z1:
                    continue
                if not t.inside(x, 1, z) or not unset(t, x, 1, z):
                    continue
                if x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1):
                    continue
                side = 'north' if z < z0 else 'south' if z > z1 else 'west' if x < x0 else 'east'
                t.set(x, 1, z, stairs('polished_deepslate_stairs', B.opposite(side)))
    for side, a in slits:
        x, z = _side_cell(fp, side, a)
        t.set(x, 2, z, I_GLASS)
        t.set(x, 3, z, I_GLASS)
    for side, a0, a1 in doors:
        dx, dz = B.step(side)
        for a in range(a0, a1 + 1):
            x, z = _side_cell(fp, side, a)
            for y in (1, 2, 3):
                t.set(x, y, z, AIR)
            t.set(x, 0, z, S('or:durasteel_deck'))
            t.set(x + dx, 1, z + dz, AIR) if t.inside(x + dx, 1, z + dz) and not unset(t, x + dx, 1, z + dz) and \
                B.name_of(t.get(x + dx, 1, z + dz)).endswith('_stairs') else None
            t.set(x + dx, 0, z + dz, S('or:durasteel_deck'))
        # a deep frame: angled jambs and the red lamp in the lintel
        for a in (a0 - 1, a1 + 1):
            x, z = _side_cell(fp, side, a)
            for y in (1, 2, 3):
                t.set(x + dx, y, z + dz, S('polished_deepslate'))
        for a in range(a0 - 1, a1 + 2):
            x, z = _side_cell(fp, side, a)
            t.set(x + dx, 4, z + dz, stairs(I_STAIR, B.opposite(side), 'top'))
        x, z = _side_cell(fp, side, (a0 + a1) // 2)
        t.set(x, 4, z, I_RED)


def i_barracks(t, rng):
    fp = I_BARRACKS
    x0, z0, x1, z1 = fp
    i_block(t, rng, fp, 6, 5141, doors=[('east', 16, 16)], slits=[('west', 13), ('west', 18), ('north', 8), ('east', 12), ('east', 20)])
    # bunks against both long walls, black frames, red blankets; lockers and foot chests
    for z in (z0 + 1, z0 + 3, z0 + 6, z0 + 8):
        bunk(t, x0 + 2, 1, z, 'west', 'red')
        put(t, x0 + 3, 1, z, barrel('east'))
    for z in (z0 + 1, z0 + 3, z1 - 2):
        bunk(t, x1 - 2, 1, z, 'east', 'black')
    for z in (z0 + 2, z0 + 7):
        locker(t, x0 + 1, 1, z, 'east', S('or:imperial_plating'))
    # the sergeant's desk at the south end, the duty roster screen, the arms rack
    t.set(x0 + 3, 1, z1 - 1, slab('polished_blackstone_slab', 'top'))
    t.set(x0 + 4, 1, z1 - 1, slab('polished_blackstone_slab', 'top'))
    t.set(x0 + 3, 1, z1 - 2, stairs('dark_oak_stairs', 'south'))
    t.set(x0 + 3, 2, z1 - 1, S('or:console', facing='north'))
    t.set(x0 + 4, 3, z1, S('or:holo_screen', facing='north'))
    weapon_rack(t, x0 + 2, 1, z0 + 10, 'z', 1)
    wall_banner(t, x0 + 4, 3, z0 + 1, 'south', 'black', IMPERIAL_FLAG)
    for (x, z) in ((x0 + 4, z0 + 3), (x0 + 4, z0 + 8)):
        t.set(x, 6, z, I_RED)
    t.set(x0 + 4, 6, z1 - 2, S('or:light_panel'))
    roof_vents(t, rng, fp, 6, 2)
    return (x0 + 1, 1, z0 + 1, x1 - 1, 5, z1 - 1)


def roof_vents(t, rng, fp, roof_y, n):
    x0, z0, x1, z1 = fp
    spots = [(x, z) for x in range(x0 + 2, x1 - 1) for z in range(z0 + 2, z1 - 1)]
    rng.shuffle(spots)
    for (x, z) in spots[:n]:
        t.set(x, roof_y + 1, z, S('or:durasteel_vent'))
        t.set(x, roof_y + 2, z, slab('or:durasteel_plating_slab', 'bottom'))


def i_mess(t, rng):
    fp = I_MESS
    x0, z0, x1, z1 = fp
    i_block(t, rng, fp, 6, 5151, doors=[('east', 28, 29)], slits=[('west', 26), ('west', 31), ('south', 8)])
    # one long steel table, stools, the galley behind a counter at the south end
    for z in range(z0 + 2, z1 - 3):
        t.set(x0 + 4, 1, z, slab('polished_blackstone_slab', 'top'))
        t.set(x0 + 3, 1, z, stairs('dark_oak_stairs', 'west') if z % 2 else AIR)
        t.set(x0 + 5, 1, z, stairs('dark_oak_stairs', 'east') if z % 2 == 0 else AIR)
    t.set(x0 + 4, 2, z0 + 3, S('flower_pot'))
    t.set(x0 + 4, 2, z0 + 6, S('light_weighted_pressure_plate', power=0))
    for x in range(x0 + 1, x1):
        t.set(x, 1, z1 - 2, S('polished_deepslate') if x != x0 + 6 else AIR)
        if x != x0 + 6:
            t.set(x, 2, z1 - 2, slab('polished_blackstone_slab', 'bottom'))
    galley = [S('smoker', facing='north', lit=True), S('furnace', facing='north', lit=False), barrel('north'),
              S('water_cauldron', level=3), S('or:cargo_crate_imperial'), barrel('up'), S('smoker', facing='north', lit=False)]
    for i, x in enumerate(range(x0 + 1, x1)):
        t.set(x, 1, z1 - 1, galley[i % len(galley)])
    t.set(x0 + 3, 2, z1 - 1, S('or:durasteel_vent'))
    for (x, z) in ((x0 + 4, z0 + 3), (x0 + 4, z0 + 7), (x0 + 6, z1 - 2)):
        t.set(x, 6, z, S('or:light_panel_amber'))
    wall_banner(t, x0 + 1, 3, z0 + 5, 'east', 'black', IMPERIAL_FLAG)
    roof_vents(t, rng, fp, 6, 2)
    return (x0 + 1, 1, z0 + 1, x1 - 1, 5, z1 - 1)


def i_holding(t, rng):
    """The holding block: a corridor down the west side, three barred cells along the east wall
    (one door cut open and its prisoner gone), the interrogation chair in the last cell and the
    guard's desk by the door."""
    fp = I_HOLDING
    x0, z0, x1, z1 = fp
    i_block(t, rng, fp, 6, 5161, doors=[('west', 14, 14)], slits=[('north', 36), ('south', 36), ('east', 12), ('east', 18)])
    cells = ((z0 + 1, z0 + 2), (z0 + 4, z0 + 5), (z0 + 7, z0 + 8))
    for (za, zb) in cells:
        for z in (za, zb):
            for y in (1, 2, 3):
                t.set(x0 + 3, y, z, bars())
            t.set(x0 + 3, 4, z, S('or:imperial_plating'))
    for zw in (z0 + 3, z0 + 6):
        for x in range(x0 + 3, x1):
            for y in range(1, 6):
                t.set(x, y, zw, S('or:imperial_plating'))
    for i, (za, zb) in enumerate(cells):
        t.set(x1 - 1, 1, za, slab('or:durasteel_plating_slab', 'bottom'))  # a cot
        t.set(x1 - 1, 4, zb, S('or:light_panel_red'))
        if i == 0:
            t.set(x0 + 4, 1, zb, S('cauldron'))
    # the middle cell: bars cut, scorched, empty
    t.set(x0 + 3, 1, z0 + 5, AIR)
    t.set(x0 + 3, 2, z0 + 5, AIR)
    t.set(x0 + 3, 3, z0 + 5, S('or:imperial_plating'))
    t.set(x0 + 2, 1, z0 + 5, S('or:debris'))
    t.set(x0 + 4, 1, z0 + 4, S('or:scorch_mark'))
    t.set(x0 + 5, 1, z0 + 5, S('or:debris'))
    # the last cell: the interrogation chair under a hanging lamp, its console
    t.set(x0 + 5, 1, z0 + 8, stairs('polished_blackstone_stairs', 'south'))
    t.set(x0 + 5, 4, z0 + 8, chain_y())
    t.set(x0 + 5, 3, z0 + 8, lantern(True, False))
    t.set(x1 - 1, 1, z0 + 7, S('or:console', facing='west'))
    # the guard's desk by the door
    t.set(x0 + 1, 1, z0 + 2, slab('polished_blackstone_slab', 'top'))
    t.set(x0 + 1, 2, z0 + 2, S('or:console', facing='east'))
    t.set(x0 + 2, 1, z0 + 2, stairs('dark_oak_stairs', 'east'))
    t.set(x0 + 1, 1, z1 - 1, S('or:cargo_crate_imperial'))
    t.set(x0 + 1, 3, z0 + 1, S('or:holo_screen', facing='east'))
    for z in (z0 + 2, z0 + 7):
        t.set(x0 + 2, 6, z, I_RED)
    roof_vents(t, rng, fp, 6, 1)
    return (x0 + 1, 1, z0 + 1, x1 - 1, 5, z1 - 1)


def i_motor_pool(t, rng):
    """A black shed open to the west: a heavy speeder on blocks with its engine out, fuel drums,
    a parts rack, a chain hoist on a gantry beam."""
    x0, z0, x1, z1 = I_MOTOR
    body = patches(5171, I_WALL, 3.0)
    floor = patches(5172, [('or:durasteel_deck', 5), ('polished_blackstone', 2), ('coarse_dirt', 1)], 3.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, floor(x, 0, z))
            back = x == x1
            side = z in (z0, z1)
            for y in range(1, 6):
                if back or (side and x > x0):
                    t.set(x, y, z, pick(rng, I_PLINTH) if y == 1 else (I_STRIPE if y == 5 else body(x, y, z)))
                else:
                    t.set(x, y, z, AIR)
            t.set(x, 6, z, S('or:imperial_plating') if (x - x0) % 3 else S('or:durasteel_grate'))
    for z in (z0, z0 + 4, z1):
        col(t, x0, z, 1, 5, beam('y'))
    for z in range(z0, z1 + 1):
        t.set(x0, 6, z, beam('z'))
        t.set(x0 - 1, 6, z, stairs(I_STAIR, 'east', 'top'))
    # the speeder: black hull, red flash, on stands, engine on the hoist
    sx, sz = x0 + 2, z0 + 2
    for i in range(5):
        for j in range(3):
            x, z = sx + i, sz + j
            t.set(x, 1, z, slab(I_SLAB, 'top') if 0 < i < 4 else stairs(I_STAIR, 'east' if i == 0 else 'west', 'top'))
    t.set(sx + 1, 2, sz + 1, stairs('dark_oak_stairs', 'east'))
    t.set(sx + 2, 2, sz + 1, stairs('dark_oak_stairs', 'east'))
    t.set(sx + 1, 2, sz, I_GLASS)
    t.set(sx + 1, 2, sz + 2, I_GLASS)
    t.set(sx + 3, 2, sz, slab(I_SLAB, 'bottom'))
    t.set(sx + 3, 2, sz + 2, slab(I_SLAB, 'bottom'))
    t.set(sx + 4, 2, sz + 1, AIR)
    gz = sz + 1
    for x in range(x0 + 1, x1):
        t.set(x, 5, gz, beam('x'))
    t.set(sx + 5, 4, gz, chain_y())
    t.set(sx + 5, 3, gz, chain_y())
    t.set(sx + 5, 2, gz, pipes('x'))
    # back wall: parts rack, drums, tools
    bench = [S('anvil', facing='north'), barrel('up'), S('or:cargo_crate_imperial'), S('smithing_table'), barrel('up'),
             S('grindstone', face='floor', facing='north')]
    for i, z in enumerate(range(z0 + 1, z1)):
        if i % 3 != 1:
            t.set(x1 - 1, 1, z, bench[i % len(bench)])
    for z in (z0 + 2, z1 - 2):
        t.set(x1, 3, z, I_RED)
    for (x, z) in ((x0 + 3, z0 + 6), (x0 + 6, z0 + 3)):
        t.set(x, 6, z, S('or:light_panel'))
    for (x, z) in ((x0 + 2, z1 - 2), (x0 + 5, z1 - 1), (x0 + 4, z0 + 6)):
        put(t, x, 1, z, S('or:scorch_mark'), only_empty=True)
    crate_pile(t, rng, x0 + 2, z1 - 2, x0 + 4, z1 - 1, 1, [('or:cargo_crate_imperial', 3), ('barrel[facing=up,open=false]', 1)], 2)
    return (x0 + 1, 1, z0 + 1, x1 - 1, 5, z1 - 1)


def i_pad(t, rng):
    """A sunken-edged landing pad: black deck, red and black hazard edge, red corner lamps, an
    Imperial shuttle with its wings folded up, a fuel bowser beside it."""
    x0, z0, x1, z1 = I_PAD
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, I_DECK)
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                t.set(x, 1, z, I_STRIPE if (x + z) % 2 else S('black_concrete'))
            else:
                t.set(x, 1, z, S('or:durasteel_deck') if (x + 2 * z) % 6 else S('or:durasteel_grate'))
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        t.set(x, 2, z, I_RED)
    for x in range(x0, x0 + 3):
        t.set(x, 1, z0 - 1, stairs(I_STAIR, 'south'))
    cabin = imperial_shuttle(t, (x0 + x1) // 2, 2, z0)
    # the fuel bowser
    for z in (z1 - 2, z1 - 1):
        t.set(x1 - 1, 2, z, S('or:durasteel_plating'))
        t.set(x1 - 1, 3, z, slab('or:durasteel_plating_slab', 'bottom'))
    t.set(x1 - 2, 2, z1 - 1, pipes('x'))
    return cabin


def imperial_shuttle(t, cx, y, zs):
    """An Imperial shuttle, nose north: a black wedge hull with the red flash, a narrow dark
    viewport, the tall central fin and two wings folded up, a ramp at the tail."""
    hull = S('or:imperial_plating')
    dark = S('black_concrete')
    for (dx, dz) in ((-1, 2), (1, 2), (-1, 6), (1, 6)):
        t.set(cx + dx, y, zs + dz, beam('y'))
    for z in range(zs + 1, zs + 8):
        for dx in range(-2, 3):
            x = cx + dx
            side = abs(dx) == 2
            t.set(x, y + 1, z, slab('or:durasteel_plating_slab', 'top') if side else S('or:durasteel_plating'))
            if side:
                t.set(x, y + 2, z, I_STRIPE if z % 3 == 1 else hull)
                t.set(x, y + 3, z, stairs(I_STAIR, 'east' if dx > 0 else 'west'))
            else:
                t.set(x, y + 2, z, AIR)
                t.set(x, y + 3, z, AIR)
                t.set(x, y + 4, z, hull if dx == 0 else stairs(I_STAIR, 'east' if dx > 0 else 'west'))
    for dx in (-1, 0, 1):
        t.set(cx + dx, y + 1, zs, stairs('or:durasteel_plating_stairs', 'south', 'top'))
        t.set(cx + dx, y + 2, zs, stairs(I_STAIR, 'south'))
        t.set(cx + dx, y + 2, zs + 1, I_GLASS)
        t.set(cx + dx, y + 3, zs + 1, stairs(I_STAIR, 'south') if dx else S('red_stained_glass'))
    for dx in (-2, 2):
        t.set(cx + dx, y + 2, zs + 1, stairs(I_STAIR, 'south'))
        t.set(cx + dx, y + 3, zs + 1, AIR)
    # the tall fin
    for z in range(zs + 3, zs + 8):
        h = 1 + (z - zs - 3) // 1
        for k in range(min(h, 4)):
            t.set(cx, y + 5 + k, z, hull if k < min(h, 4) - 1 else stairs(I_STAIR, 'south'))
    t.set(cx, y + 9, zs + 7, I_RED)
    # wings folded up: vertical plates beside the hull
    for sgn in (-1, 1):
        x = cx + sgn * 3
        for z in range(zs + 3, zs + 7):
            t.set(x, y + 2, z, S('or:imperial_plating'))
            t.set(x, y + 3, z, S('or:imperial_plating'))
            t.set(x, y + 4, z, stairs(I_STAIR, 'east' if sgn < 0 else 'west'))
        t.set(x, y + 1, zs + 4, beam('y'))
        t.set(x, y + 2, zs + 2, rod('north'))
        t.set(x, y + 3, zs + 6, I_RED)
    # engines and ramp
    for dx in (-2, 2):
        t.set(cx + dx, y + 2, zs + 8, pipes('z'))
        t.set(cx + dx, y + 2, zs + 9, I_RED)
        t.set(cx + dx, y + 3, zs + 8, slab(I_SLAB, 'bottom'))
    for dx in (-1, 1):
        t.set(cx + dx, y + 2, zs + 8, dark)
        t.set(cx + dx, y + 3, zs + 8, hull)
        t.set(cx + dx, y + 4, zs + 8, slab(I_SLAB, 'bottom'))
    t.set(cx, y + 1, zs + 8, stairs('or:durasteel_plating_stairs', 'north'))
    t.set(cx, y, zs + 9, stairs('or:durasteel_plating_stairs', 'north'))
    t.set(cx, y + 2, zs + 8, AIR)
    t.set(cx, y + 3, zs + 8, AIR)
    t.set(cx, y + 4, zs + 8, hull)
    for dx in (-1, 1):
        t.set(cx + dx, y + 2, zs + 2, S('or:console', facing='south'))
        t.set(cx + dx, y + 2, zs + 3, stairs(I_STAIR, 'south'))
        for z in (zs + 5, zs + 6):
            t.set(cx + dx, y + 2, z, stairs(I_STAIR, 'west' if dx < 0 else 'east'))
    t.set(cx, y + 4, zs + 4, I_RED)
    return (cx - 1, y + 2, zs + 2, cx + 1, y + 3, zs + 7)


def i_keep(t, rng):
    """The command keep: a battered bunker whose upper block carries the sensor spire. Inside, the
    command room round a holo table with consoles along the walls, the commander's dais, the
    armoury behind a grille and a stair up to the roof terrace."""
    fp = I_KEEP
    x0, z0, x1, z1 = fp
    i_block(t, rng, fp, 6, 5181, doors=[('north', 21, 23)],
            slits=[('north', 16), ('north', 18), ('north', 26), ('west', 27), ('west', 32), ('east', 26), ('south', 18), ('south', 25)])
    # the holo table under the spire core
    for x in range(20, 25):
        for z in range(27, 30):
            edge = x in (20, 24) or z in (27, 29)
            t.set(x, 1, z, S('polished_deepslate') if edge else S('sea_lantern'))
    for x in range(21, 24):
        t.set(x, 2, 28, S('light_blue_stained_glass_pane', waterlogged=False))
    # consoles along the walls under screens, chairs
    for (x, z, f) in ((x0 + 1, 25, 'east'), (x0 + 1, 26, 'east'), (x0 + 1, 29, 'east'), (x1 - 1, 25, 'west'), (x1 - 1, 26, 'west'),
                      (x1 - 1, 28, 'west')):
        t.set(x, 1, z, S('or:console', facing=f))
        t.set(x, 3, z, S('or:holo_screen', facing=f))
    t.set(x0 + 2, 1, 25, stairs('dark_oak_stairs', 'west'))
    t.set(x1 - 2, 1, 26, stairs('dark_oak_stairs', 'east'))
    # the dais and the commander's chair at the south, banners either side
    for x in range(19, 26):
        t.set(x, 1, z1 - 3, slab('polished_blackstone_slab', 'bottom'))
        t.set(x, 1, z1 - 2, S('polished_blackstone'))
    t.set(22, 2, z1 - 2, stairs('polished_blackstone_stairs', 'south'))
    t.set(22, 3, z1 - 1, S('or:holo_screen', facing='north'))
    wall_banner(t, 20, 4, z1 - 1, 'north', 'black', IMPERIAL_FLAG)
    wall_banner(t, 24, 4, z1 - 1, 'north', 'black', IMPERIAL_FLAG)
    # the armoury behind a grille in the south-west corner
    for z in range(z1 - 5, z1):
        for y in (1, 2, 3):
            t.set(x0 + 4, y, z, bars())
    t.set(x0 + 4, 1, z1 - 3, AIR)
    t.set(x0 + 4, 2, z1 - 3, AIR)
    weapon_rack(t, x0 + 1, 1, z1 - 4, 'z', 3)
    for (x, y) in ((x0 + 2, 1), (x0 + 3, 1), (x0 + 2, 2)):
        t.set(x, y, z1 - 1, S('or:cargo_crate_imperial'))
    # the stair up to the roof terrace along the east wall, rising south
    sx = x1 - 1
    for k in range(1, 7):
        z = z0 + 6 + k
        t.set(sx, k, z, stairs('polished_blackstone_stairs', 'south'))
        for y in range(k + 1, min(k + 3, 7)):
            t.set(sx, y, z, AIR)
    t.set(sx, 7, z0 + 12, AIR)
    # lights: red pools at the walls and a cold light over the table
    for (x, z) in ((17, 26), (26, 25), (17, 32), (25, 32)):
        t.set(x, 6, z, I_RED)
    t.set(22, 6, 26, S('or:light_panel'))
    return (x0 + 1, 1, z0 + 1, x1 - 1, 5, z1 - 1)


def i_spire(t, rng):
    """The sensor spire on the keep: an upper block (y 7..12), then a chamfered shaft that narrows
    5 > 3 > 1 to a red beacon at y 32, angled sensor vanes on its faces and red strips up its
    corners. The silhouette of the post from far away."""
    cx, cz = 22, 29
    # upper block (sensor room) x 18..26, z 25..33
    ux0, uz0, ux1, uz1 = 18, 25, 26, 33
    body = patches(5191, I_WALL, 2.5)
    for x in range(ux0, ux1 + 1):
        for z in range(uz0, uz1 + 1):
            edge = x in (ux0, ux1) or z in (uz0, uz1)
            chamfer = x in (ux0, ux1) and z in (uz0, uz1)
            for y in range(7, 12):
                if chamfer:
                    continue
                t.set(x, y, z, (I_STRIPE if y == 11 else body(x, y, z)) if edge else AIR)
            if not chamfer:
                t.set(x, 12, z, S('or:imperial_plating') if not edge else S('polished_deepslate'))
        # chamfered corners as stairs
    for (x, z, f) in ((ux0, uz0, 'south'), (ux1, uz0, 'south'), (ux0, uz1, 'north'), (ux1, uz1, 'north')):
        for y in range(7, 13):
            t.set(x, y, z, stairs(I_STAIR, f, 'bottom' if y % 2 else 'top'))
    for (x, z) in ((ux0, 29), (ux1, 29), (22, uz0), (22, uz1)):
        t.set(x, 9, z, I_GLASS)
        t.set(x, 10, z, I_GLASS)
    # sensor room inside: the scanner core, two consoles
    for y in range(7, 12):
        t.set(cx, y, cz, pipes('y'))
    t.set(cx - 1, 7, cz, S('or:console', facing='west'))
    t.set(cx + 1, 7, cz, S('or:console', facing='east'))
    t.set(cx, 11, cz - 2, S('or:light_panel'))
    # shaft: octagonal (corners cut) 5 wide to y 19, 3 wide to y 26, then the needle
    for y in range(13, 33):
        r = 2 if y <= 19 else (1 if y <= 26 else 0)
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                x, z = cx + dx, cz + dz
                if r == 2 and abs(dx) == 2 and abs(dz) == 2:
                    continue
                if r == 2 and (abs(dx) == 2 or abs(dz) == 2) and abs(dx) + abs(dz) == 3:
                    # the cut corner faces: dark deepslate with a red strip every fourth layer
                    t.set(x, y, z, I_RED if y % 4 == 0 else S('polished_deepslate'))
                elif (dx, dz) == (0, 0) and r > 0:
                    t.set(x, y, z, pipes('y'))
                else:
                    t.set(x, y, z, S('or:imperial_plating'))
        # step-back ledges where the shaft narrows: stairs leaning in
        if y in (20, 27):
            rr = 2 if y == 20 else 1
            for dx in range(-rr, rr + 1):
                for dz in range(-rr, rr + 1):
                    if max(abs(dx), abs(dz)) != rr or (rr == 2 and abs(dx) == 2 and abs(dz) == 2):
                        continue
                    f = 'north' if dz == -rr else 'south' if dz == rr else 'west' if dx == -rr else 'east'
                    if abs(dx) == rr and abs(dz) == rr:
                        t.set(cx + dx, y, cz + dz, slab(I_SLAB, 'bottom'))
                    else:
                        t.set(cx + dx, y, cz + dz, stairs(I_STAIR, B.opposite(f)))
    # buttress fins from the sensor room roof up the four cut corners
    for (sx, sz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for k, h in ((3, 1), (2, 3)):
            x, z = cx + sx * k, cz + sz * k
            if k == 3:
                t.set(x, 13, z, stairs(I_STAIR, 'south' if sz < 0 else 'north'))
            else:
                for y in range(13, 13 + h):
                    t.set(x, y, z, S('polished_deepslate'))
                t.set(x, 13 + h, z, stairs(I_STAIR, 'south' if sz < 0 else 'north'))
    # sensor vanes: angled plates on the four faces of the wide section
    for (dx, dz, f) in ((0, -3, 'north'), (0, 3, 'south'), (-3, 0, 'west'), (3, 0, 'east')):
        for y in (15, 16, 17):
            t.set(cx + dx, y, cz + dz, stairs(I_STAIR, B.opposite(f), 'top' if y == 15 else 'bottom') if y != 16 else I_STRIPE)
    # vanes on the narrow section
    for (dx, dz, f) in ((0, -2, 'north'), (0, 2, 'south'), (-2, 0, 'west'), (2, 0, 'east')):
        t.set(cx + dx, 23, cz + dz, stairs(I_STAIR, B.opposite(f), 'top'))
        t.set(cx + dx, 24, cz + dz, stairs(I_STAIR, B.opposite(f)))
    # the needle and the beacon
    t.set(cx, 32, cz, I_RED)
    t.set(cx, 33, cz, rod('up'))
    for (dx, dz, f) in ((0, -1, 'north'), (0, 1, 'south'), (-1, 0, 'west'), (1, 0, 'east')):
        t.set(cx + dx, 28, cz + dz, stairs(I_STAIR, B.opposite(f)))
    return (19, 7, 26, 25, 11, 32)


def i_generator(t, rng):
    """Fuel tanks and the reactor housing in the south-west corner, their pipes into the keep."""
    for (x0, z0) in ((7, 37), (10, 37)):
        for x in range(x0, x0 + 2):
            for z in range(z0, z0 + 3):
                t.set(x, 0, z, I_DECK)
                for y in (1, 2, 3):
                    t.set(x, y, z, S('or:imperial_plating') if y != 2 else I_STRIPE)
                t.set(x, 4, z, slab('or:durasteel_plating_slab', 'bottom'))
    t.set(8, 5, 38, pipes('y'))
    t.set(11, 5, 38, pipes('y'))
    for x in range(12, 14):
        t.set(x, 1, 38, pipes('x'))
    t.set(9, 1, 40, S('or:durasteel_vent'))
    t.set(9, 2, 40, I_RED)


def i_yard_south(t, rng):
    """Behind the keep: a fenced scrap yard of crates, a broken walker leg and a dish on a mast."""
    crates = [('or:cargo_crate_imperial', 4), ('or:cargo_crate', 2), ('barrel[facing=up,open=false]', 1)]
    crate_pile(t, rng, 15, 38, 18, 40, 1, crates, 3)
    crate_pile(t, rng, 24, 39, 27, 40, 1, crates, 2)
    # a dish on a mast facing south over the wall
    col(t, 21, 39, 1, 6, beam('y'))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            if abs(dx) + abs(dy) < 2:
                t.set(21 + dx, 8 + dy, 40, S('gray_concrete') if (dx, dy) != (0, 0) else beam('z'))
            else:
                t.set(21 + dx, 8 + dy, 40, stairs(I_STAIR, 'north', 'top' if dy > 0 else 'bottom'))
    t.set(21, 7, 39, beam('y'))
    t.set(21, 8, 39, beam('z'))


def i_terminal(t):
    x, y, z = TERMINAL
    t.set(x, y, z, S('or:bounty_terminal', faction='imperial', facing='north'), nbt={'id': 'oldrepublic:bounty_terminal'})
    t.anchor('TERMINAL', x, y, z)
    t.set(x, 0, z, S('or:imperial_plating'))
    for dx in (-1, 1):
        t.set(x + dx, 0, z, S('polished_deepslate'))
    mod.spot(t, 'OFFICER', *OFFICER)
    for i, g in enumerate(I_GARRISON, start=1):
        assert dist(g, TERMINAL) <= 11.0, (i, g, dist(g, TERMINAL))
        mod.spot(t, 'GARRISON_%d' % i, *g)


def i_forecourt(t, rng):
    """Two banner pylons at the forecourt's north corners and four floodlight masts."""
    for (x, z) in ((15, 11), (29, 11)):
        t.set(x, 0, z, S('polished_deepslate'))
        col(t, x, z, 1, 7, beam('y'))
        t.set(x, 8, z, I_RED)
        standing_banner(t, x, 9, z, 'north', 'black', IMPERIAL_FLAG)
    for (x, z) in ((14, 17), (30, 17)):
        lamp_post(t, x, 1, z, 5, head=S('or:light_panel'), cap=slab(I_SLAB, 'bottom'))


def i_decor(t, rng):
    outside = [(x, 0, z) for x in range(SIZE) for z in range(SIZE)
               if (x <= 1 or x >= 43 or z <= 3 or z >= 43) and B.name_of(t.get(x, 0, z) or '') == 'minecraft:grass_block']
    scatter_on(t, rng, outside, [(S('short_grass'), 5), (S('dead_bush'), 1)], 0.3)
    inside = [(x, 0, z) for x in range(4, 41) for z in range(6, 41)
              if B.name_of(t.get(x, 0, z) or '') in ('minecraft:gravel', 'minecraft:coarse_dirt', 'minecraft:packed_mud')]
    scatter_on(t, rng, inside, [(S('or:debris'), 3), (S('or:scorch_mark'), 2)], 0.05)


def build_imperial():
    t = Template(SIZE, IMP_H, SIZE, seed=960111, name='outpost/imperial_post')
    rng = t.rng
    i_ground(t)
    i_wall(t, rng)
    rooms = [i_bastion(t, rng, cx, cz) for (cx, cz) in ((3, 5), (41, 5), (3, 41), (41, 41))]
    for (x, z) in ((6, 7), (38, 7), (5, 38), (39, 38)):
        i_bastion_door(t, x, z)
    rooms += i_gatehouse(t, rng)
    rooms += [i_barracks(t, rng), i_mess(t, rng), i_holding(t, rng), i_motor_pool(t, rng), i_keep(t, rng)]
    rooms.append(i_spire(t, rng))
    rooms.append(i_pad(t, rng))
    i_generator(t, rng)
    i_yard_south(t, rng)
    i_forecourt(t, rng)
    i_terminal(t)
    i_decor(t, rng)
    carve(t, 0, 0, SIZE - 1, SIZE - 1, 1, 6, bank=1, noise=Noise(5199, 3.0))
    light_rooms(t, rooms)
    return t


# =============================================================================================
# worldgen, meta
# =============================================================================================

def write_worldgen():
    W.pool('republic_outpost/start', [W.element('outpost/republic_post', adapted=adapted(REP_H, support_depth=8))])
    W.pool('imperial_outpost/start', [W.element('outpost/imperial_post', adapted=adapted(IMP_H, support_depth=8))])
    for army, biomes in (('republic', ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:meadow',
                                       'minecraft:forest', 'minecraft:birch_forest', 'minecraft:taiga', 'minecraft:savanna']),
                         ('imperial', ['minecraft:desert', 'minecraft:badlands', 'minecraft:wooded_badlands',
                                       'minecraft:savanna_plateau', 'minecraft:windswept_savanna', 'minecraft:dark_forest',
                                       'minecraft:snowy_plains', 'minecraft:taiga'])):
        W.structure('%s_outpost' % army, start_pool='%s_outpost/start' % army, biome_tag='has_structure/%s_outpost' % army,
                    size=1, max_distance=48, start_height=1 - BURIED)
        W.biome_tag('has_structure/%s_outpost' % army, biomes)
    W.structure_set('outposts', [('republic_outpost', 1), ('imperial_outpost', 1)], spacing=40, separation=16,
                    salt=734214, exclusion=('oldrepublic:ruins', 6))
    for army in ('republic', 'imperial'):
        meta.write('outpost/%s_post' % army, buried=BURIED, terrain='high', max_slope=5, clearance=8,
                   support_depth=8, ramp_rings=2, clear_above=8, spawn_radius=64)


def main():
    args = cli(__doc__)
    for name, build in (('republic_post', build_republic), ('imperial_post', build_imperial)):
        t = build()
        print(name, t.size, t.count())
        save(t, 'outpost/' + name, args.preview)
    write_worldgen()


if __name__ == '__main__':
    main()
