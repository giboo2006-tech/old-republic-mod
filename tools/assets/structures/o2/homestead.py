"""oldrepublic:wilds/homestead: a moisture farmer's homestead, half sunk in the ground.

Run: python3 tools/assets/structures/o2/homestead.py [--preview DIR]

Two start variants, both 41 x 17 x 41 and buried 6 (the yard the player walks on is template y 6;
the courtyard floor and the rooms are five layers down):

* wilds/homestead/vessk: the Vessk family's farm, lived in. A round sunken courtyard with a
  hydroponic planter, a laundry line and a shade; down the entrance stair under its dome, the
  kitchen under a big dome (the table laid for four), the garage with a landspeeder half put back
  together under a chain hoist and a ramp up to the yard, the parents' room and the children's room
  under small domes, the storeroom with the family's strongbox. Five vaporators stand in the yard,
  a water tank, a solar rack, the grandparents' two graves.
* wilds/homestead/orrin: the Orrin farm after the raid. A square courtyard drifted with sand,
  blaster scorches on every wall, the living dome caved in, the speeder burnt out in the garage,
  vaporators knocked flat, one fresh grave with a rifle on it. The strongbox is still under the rug
  in the parents' room, where the raiders never looked.

Worldgen: the structure, its start pool (both variants), the set (spacing 44, separation 16,
salt 960101, 4 chunks from the ruins), the biome tag and the loot table chests/wilds/homestead.
"""
import math

from common import (Template, S, AIR, B, Noise, shapes, put, col, pick, patches, stairs, slab, trapdoor, lantern,
                    chain_y, bars, beam, pipes, rod, barrel, bed, wall_banner, carve, campfire, light_rooms,
                    scatter_on, adapted, wilds_family, loot_item, loot_table, cli, save, unset, ring_of)

SX, SY, SZ = 41, 17, 41
BURIED = 6
G = 6            # the yard's feet layer
SURF = G - 1     # the yard's top block layer

ADOBE = [('or:adobe', 10), ('or:adobe_bricks', 2), ('smooth_sandstone', 1)]
FLOOR = [('or:adobe_bricks', 5), ('cut_sandstone', 3), ('smooth_sandstone', 2)]
YARD = [('coarse_dirt', 5), ('packed_mud', 3), ('sand', 3), ('dirt_path', 1)]
FILL = [('packed_mud', 3), ('coarse_dirt', 2), ('dirt', 2)]
LOOT = 'chests/wilds/homestead'


# ---------------------------------------------------------------- ground and digging

def yard(t, seed):
    """The packed-earth yard over the whole footprint (ragged in the outer rings), filled solid
    below so the rooms can be dug out of it and the farm sits level on uneven ground."""
    top = patches(seed, YARD, 4.0)
    fill = patches(seed + 1, FILL, 3.0)
    n = Noise(seed + 2, 2.5)
    for x in range(SX):
        for z in range(SZ):
            r = ring_of(t, x, z)
            if r < 3 and n.at(x, 0, z) < (3 - r) * 0.22:
                continue
            for y in range(0, SURF):
                t.set(x, y, z, fill(x, y, z))
            t.set(x, SURF, z, top(x, SURF, z))


def walls_around(t, x0, z0, x1, z1, y0, y1, pal, skip=()):
    """The ring of cells just outside the interior box x0..x1, z0..z1 from y0 to y1."""
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if x0 <= x <= x1 and z0 <= z <= z1:
                continue
            if (x, z) in skip:
                continue
            for y in range(y0, y1 + 1):
                put(t, x, y, z, pal)


def sunk_room(t, x0, z0, x1, z1, wall, floor, top=4, ceiling=None):
    """A room dug out of the yard: floor y 0, air y 1..top, walls round it to the yard's top layer,
    a ceiling from top + 1 to the yard's top layer (None leaves the yard's own ground there)."""
    walls_around(t, x0, z0, x1, z1, 0, SURF, wall)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            put(t, x, 0, z, floor)
            for y in range(1, top + 1):
                t.set(x, y, z, AIR)
            for y in range(top + 1, SURF + 1):
                if ceiling is not None:
                    put(t, x, y, z, ceiling)


def dome_over(t, cx, cz, r, shell, base=SURF, glass=None, hole=None, cap=None):
    """A dome rising from the yard over a sunk room: the shell from `base` up, air inside it all the
    way down into the room below (the room's ceiling opens into the dome). glass: cells (x, z) of
    the topmost ring to glaze; hole: (x, z, rad) a caved-in breach."""
    rr = r + 0.45
    for y in range(base, base + r + 2):
        dy = y - base
        for x in range(cx - r - 1, cx + r + 2):
            for z in range(cz - r - 1, cz + r + 2):
                d = math.sqrt((x - cx) ** 2 + dy ** 2 + (z - cz) ** 2)
                if d <= rr - 1.0:
                    if y >= base:
                        t.set(x, y, z, AIR)
                elif d <= rr:
                    if hole and math.sqrt((x - hole[0]) ** 2 + (z - hole[1]) ** 2 + (y - (base + r - 1)) ** 2) <= hole[2]:
                        t.set(x, y, z, AIR)
                        continue
                    put(t, x, y, z, shell)
    smooth_dome(t, cx, cz, r, base)
    if cap:
        t.set(cx, base + r, cz, cap)
    if glass:
        for (x, y, z) in glass:
            t.set(x, y, z, S('light_blue_stained_glass') if not isinstance(glass, dict) else glass[(x, y, z)])


DOME_BLOCKS = ('oldrepublic:adobe', 'oldrepublic:adobe_bricks', 'minecraft:smooth_sandstone')


def smooth_dome(t, cx, cz, r, base):
    """Turns the dome's stepped outer surface into stairs leaning out (the high side toward the
    middle), so the terraces read as a curve."""
    for y in range(base + 1, base + r + 1):
        for x in range(cx - r - 1, cx + r + 2):
            for z in range(cz - r - 1, cz + r + 2):
                if t.name_at(x, y, z) not in DOME_BLOCKS or not t.is_air(x, y + 1, z):
                    continue
                if (x, z) == (cx, cz):
                    continue
                dx, dz = x - cx, z - cz
                out = ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north')
                ox, oz = B.step(out)
                if not t.is_air(x + ox, y, z + oz):
                    continue
                t.set(x, y, z, stairs('or:adobe_stairs', B.opposite(out)))


def tunnel(t, x0, z0, x1, z1, h=3, frame=None, arch_axis=None):
    """A passage through a wall: air from y 1 to h, a floor of the room's kind under it."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(1, h + 1):
                t.set(x, y, z, AIR)


def stair_down(t, x0, x1, z_top, direction, wall, fill, y_top=SURF, steps=5):
    """A staircase `x0..x1` wide (along x) going down in `direction` ('south' or 'north') from the
    yard's top layer to the room floor: stairs facing back up, filled below, walls either side, air
    above each step."""
    dz = 1 if direction == 'south' else -1
    up = 'north' if direction == 'south' else 'south'
    for k in range(steps):
        z = z_top + dz * k
        y = y_top - k
        for x in range(x0, x1 + 1):
            t.set(x, y, z, stairs('or:adobe_stairs', up))
            for yy in range(0, y):
                put(t, x, yy, z, fill)
            for yy in range(y + 1, SURF + 3):
                t.set(x, yy, z, AIR)
        for x in (x0 - 1, x1 + 1):
            for yy in range(0, SURF + 1):
                put(t, x, yy, z, wall)
    return z_top + dz * steps


# ---------------------------------------------------------------- things of the farm

def vaporator(t, x, z, h=6, state='ok', fall='east'):
    """A moisture vaporator standing on the yard: a footing, a conduit stem, a copper condenser
    sleeve with grille rings, the cap and the collector needle. state 'ok', 'broken' (stem only,
    the top lying beside it) or 'down' (knocked flat toward `fall`)."""
    for y in range(0, G):
        put(t, x, y, z, S('or:adobe_bricks') if y == SURF else S('packed_mud'))
    parts = [S('smooth_stone_slab', type='double', waterlogged=False), pipes('y'), S('waxed_weathered_copper_grate', waterlogged=False),
             S('waxed_weathered_cut_copper'), S('waxed_weathered_copper_grate', waterlogged=False), pipes('y'),
             S('waxed_weathered_cut_copper_slab', type='bottom', waterlogged=False), rod('up')]
    parts = parts[:max(4, h)] if h < 8 else parts
    if state == 'ok':
        for i, p in enumerate(parts):
            t.set(x, G + i, z, p)
        # side fins: bars round the condenser
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            t.set(x + dx, G + 2, z + dz, S('waxed_weathered_copper_bars', waterlogged=False))
        t.set(x + 1, G, z, S('waxed_weathered_cut_copper_slab', type='bottom', waterlogged=False))
        t.set(x, G, z + 1, S('cauldron'))
    elif state == 'broken':
        t.set(x, G, z, parts[0])
        t.set(x, G + 1, z, parts[1])
        t.set(x, G + 2, z, S('waxed_weathered_cut_copper_slab', type='bottom', waterlogged=False))
        dx, dz = B.step(fall)
        t.set(x + dx, G, z + dz, S('waxed_oxidized_copper_grate', waterlogged=False))
        t.set(x + 2 * dx, G, z + 2 * dz, S('or:scorch_mark'))
    else:
        dx, dz = B.step(fall)
        axis = 'x' if dx else 'z'
        t.set(x, G, z, S('smooth_stone_slab', type='bottom', waterlogged=False))
        lying = [pipes(axis), S('waxed_oxidized_copper_grate', waterlogged=False), S('waxed_weathered_cut_copper'),
                 S('waxed_oxidized_copper_grate', waterlogged=False), pipes(axis), rod(fall)]
        for i, p in enumerate(lying, start=1):
            if t.inside(x + dx * i, G, z + dz * i):
                for y in range(0, G):
                    put(t, x + dx * i, y, z + dz * i, S('packed_mud'), only_empty=True)
                t.set(x + dx * i, G, z + dz * i, p)
        t.set(x + dx * 2 - dz, G, z + dz * 2 + dx, S('or:debris'))


def landspeeder(t, x, y, z, state='repair'):
    """A civilian two-seat landspeeder, nose west, 3 wide (z .. z + 2) and 6 long (x .. x + 5):
    an orange hull of red sandstone, an open cockpit behind a windscreen, three engine pods at the
    back. 'repair': hovering on blocks, one pod off on the floor, panels open. 'burnt': scorched
    hull, glass gone, pods torn off."""
    hull = S('cut_red_sandstone_slab', type='top', waterlogged=False) if state == 'repair' else S('or:wreck_plating_scorched')
    for i in range(6):
        for j in range(3):
            px, pz = x + i, z + j
            if i == 0:
                st = stairs('smooth_red_sandstone_stairs', 'east', 'top') if state == 'repair' else S('or:wreck_plating_scorched')
                if j != 1:
                    st = slab('smooth_red_sandstone_slab', 'top') if state == 'repair' else S('or:debris')
            elif i >= 4:
                st = S('smooth_red_sandstone') if state == 'repair' else S('or:wreck_plating')
            else:
                st = hull
            t.set(px, y, pz, st)
    # cockpit: two seats, the windscreen ahead of them, the dash
    if state == 'repair':
        t.set(x + 1, y + 1, z, S('glass_pane', waterlogged=False))
        t.set(x + 1, y + 1, z + 1, S('glass_pane', waterlogged=False))
        t.set(x + 1, y + 1, z + 2, S('glass_pane', waterlogged=False))
        t.set(x + 2, y + 1, z, stairs('spruce_stairs', 'east'))
        t.set(x + 2, y + 1, z + 2, stairs('spruce_stairs', 'east'))
        t.set(x + 2, y + 1, z + 1, S('or:console', facing='east'))
        t.set(x + 3, y + 1, z + 1, slab('smooth_red_sandstone_slab', 'bottom'))
        # engine pods: two in place, the third on the floor beside it
        t.set(x + 4, y + 1, z, pipes('x'))
        t.set(x + 5, y + 1, z, S('or:durasteel_vent'))
        t.set(x + 4, y + 1, z + 2, pipes('x'))
        t.set(x + 5, y + 1, z + 2, S('or:durasteel_vent'))
        t.set(x + 4, y + 1, z + 1, S('or:durasteel_plating'))
    else:
        t.set(x + 2, y + 1, z, stairs('blackstone_stairs', 'east'))
        t.set(x + 2, y + 1, z + 2, S('or:debris'))
        t.set(x + 4, y + 1, z + 1, S('or:wreck_plating_scorched'))
        t.set(x + 1, y + 1, z + 1, S('or:debris'))


def table_set(t, x, z, y=1, along='z', n=2, chairs=True, wood='spruce'):
    """A table `n` cells long with a chair at each side of every cell."""
    for i in range(n):
        tx, tz = (x, z + i) if along == 'z' else (x + i, z)
        t.set(tx, y, tz, slab('smooth_stone_slab', 'top'))
        if chairs:
            if along == 'z':
                t.set(tx - 1, y, tz, stairs('%s_stairs' % wood, 'west'))
                t.set(tx + 1, y, tz, stairs('%s_stairs' % wood, 'east'))
            else:
                t.set(tx, y, tz - 1, stairs('%s_stairs' % wood, 'north'))
                t.set(tx, y, tz + 1, stairs('%s_stairs' % wood, 'south'))


def grave(t, x, z, fresh=False, rifle=False):
    """A desert grave on the yard: a mound of packed earth with a cairn of stones at its head
    (north), a candle; a fresh one has a rifle planted in it."""
    for dz in (0, 1):
        t.set(x, SURF, z + dz, S('packed_mud') if fresh else S('coarse_dirt'))
        t.set(x, G, z + dz, slab('mud_brick_slab', 'bottom') if fresh else S('or:debris'))
    t.set(x, G, z - 1, S('cobblestone_wall', up=True, north='none', south='none', east='none', west='none', waterlogged=False))
    t.set(x, G + 1, z - 1, S('candle', candles=1, lit=False, waterlogged=False))
    if rifle:
        t.set(x, G + 1, z + 1, rod('up'))


# ---------------------------------------------------------------- the Vessk farm (lived in)

def build_vessk():
    t = Template(SX, SY, SZ, seed=960112, name='wilds/homestead/vessk')
    rng = t.rng
    wall = patches(6101, ADOBE, 3.0)
    floor = patches(6102, FLOOR, 3.0)
    fill = patches(6103, FILL, 3.0)
    yard(t, 6100)
    rooms = []
    # --- the round courtyard, five layers down
    cx, cz, r = 20, 20, 6.0
    for x in range(cx - 8, cx + 9):
        for z in range(cz - 8, cz + 9):
            d = math.hypot(x - cx, z - cz)
            if d <= r + 0.3:
                put(t, x, 0, z, floor)
                for y in range(1, SURF + 1):
                    t.set(x, y, z, AIR)
            elif d <= r + 1.5:
                for y in range(0, SURF + 1):
                    put(t, x, y, z, wall)
                t.set(x, G, z, S('or:adobe_wall', up=True, north='none', south='none', east='none', west='none', waterlogged=False))
    # the rim course: a cut sandstone band under the parapet
    for x in range(cx - 8, cx + 9):
        for z in range(cz - 8, cz + 9):
            d = math.hypot(x - cx, z - cz)
            if r + 0.3 < d <= r + 1.5:
                t.set(x, SURF - 1, z, S('cut_sandstone'))
    # --- the entrance: a dome over a stair down from the north into the courtyard
    ex, ez = 20, 8
    for x in range(ex - 4, ex + 5):
        for z in range(ez - 4, ez + 5):
            if math.hypot(x - ex, z - ez) <= 4.2:
                put(t, x, SURF, z, floor)
    zb = stair_down(t, 19, 21, 9, 'south', wall, fill)
    for z in range(zb, 15):
        for x in range(19, 22):
            put(t, x, 0, z, floor)
            for y in range(1, 4):
                t.set(x, y, z, AIR)
            for y in range(4, SURF + 1):
                put(t, x, y, z, wall)
        for x in (18, 22):
            for y in range(0, SURF + 1):
                put(t, x, y, z, wall)
    dome_over(t, ex, ez, 4, wall, base=G, cap=S('or:adobe_bricks'))
    for x in range(ex - 1, ex + 2):  # the door north, under a canvas hood
        for y in (G, G + 1, G + 2):
            t.set(x, y, ez - 4, AIR)
            t.set(x, y, ez - 3, AIR)
        t.set(x, G + 3, ez - 5, slab('or:canvas_slab', 'bottom'))
        t.set(x, G + 3, ez - 4, S('or:canvas'))
    for x in (ex - 2, ex + 2):
        col(t, x, ez - 5, G, G + 2, S('or:adobe_wall', up=True, north='none', south='none', east='none', west='none', waterlogged=False))
    # inside the entrance dome: a water jar, a shelf of tools, a hanging lantern, the gauge
    t.set(ex - 2, G, ez, S('decorated_pot', facing='east', cracked=False, waterlogged=False))
    t.set(ex + 2, G, ez, S('water_cauldron', level=2))
    t.set(ex + 2, G, ez - 1, barrel('up'))
    t.set(ex - 2, G, ez - 1, S('or:console', facing='east'))
    t.set(ex, G + 3, ez - 1, chain_y())
    t.set(ex, G + 2, ez - 1, lantern(True))
    rooms.append((ex - 3, G, ez - 3, ex + 3, G + 2, ez + 3))
    # --- the kitchen under the big west dome
    kx0, kz0, kx1, kz1 = 4, 15, 11, 25
    sunk_room(t, kx0, kz0, kx1, kz1, wall, floor, top=4, ceiling=wall)
    dome_over(t, 8, 20, 5, wall, base=SURF, glass=[(8, SURF + 5, 20)], cap=None)
    t.set(8, SURF + 5, 20, S('light_blue_stained_glass'))
    tunnel(t, 12, 19, 13, 21)
    for z in (19, 20, 21):
        put(t, 12, 0, z, floor)
        put(t, 13, 0, z, floor)
    # the counter along the west wall, the hearth in the corner, shelves, the table for four
    counter = [S('smoker', facing='east', lit=True), S('cut_sandstone'), S('water_cauldron', level=3), S('cut_sandstone'),
               barrel('east'), S('furnace', facing='east', lit=False), S('cut_sandstone'), S('composter', level=3)]
    for i, z in enumerate(range(kz0 + 1, kz1)):
        t.set(kx0, 1, z, counter[i % len(counter)])
    for z in (kz0 + 2, kz0 + 4, kz0 + 7):
        t.set(kx0, 2, z, S('flower_pot'))
    t.set(kx0, 2, kz0 + 5, S('decorated_pot', facing='east', cracked=False, waterlogged=False))
    t.set(kx0, 1, kz0, S('campfire', facing='south', lit=True, signal_fire=False, waterlogged=False))
    for y in range(SURF + 1, SURF + 5):
        t.set(kx0 + 1, y, kz0 + 1, pipes('y'))  # the flue up through the dome
    t.set(kx0 + 1, SURF + 5, kz0 + 1, S('or:adobe_wall', up=True, north='none', south='none', east='none', west='none', waterlogged=False))
    t.set(kx0 + 1, 2, kz0, S('or:adobe_bricks'))
    table_set(t, 8, 19, along='z', n=2)
    t.set(8, 2, 19, S('flower_pot'))
    t.set(8, 2, 20, S('candle', candles=3, lit=True, waterlogged=False))
    for (x, z) in ((7, 17), (9, 17), (7, 22), (9, 22), (10, 18), (10, 21)):
        t.set(x, 1, z, S('orange_carpet') if (x + z) % 2 else S('brown_carpet'))
    for x in range(kx0 + 2, kx1):
        t.set(x, 1, kz1, S('spruce_shelf', facing='north', powered=False, side_chain='unconnected', waterlogged=False))
    t.set(kx1, 1, kz0 + 1, barrel('west'))
    t.set(kx1, 2, kz0 + 1, barrel('up'))
    t.set(8, SURF + 4, 20, chain_y())
    t.set(8, SURF + 3, 20, chain_y())
    t.set(8, SURF + 2, 20, lantern(True))
    wall_banner(t, kx1, 3, 17, 'west', 'orange', [('minecraft:stripe_middle', 'brown'), ('minecraft:border', 'yellow')])
    rooms.append((kx0, 1, kz0, kx1, SURF + 3, kz1))
    # --- the garage to the east, a ramp up to the yard
    gx0, gz0, gx1, gz1 = 28, 15, 35, 26
    sunk_room(t, gx0, gz0, gx1, gz1, wall, patches(6104, [('or:durasteel_deck', 3), ('or:adobe_bricks', 2), ('coarse_dirt', 1)], 3.0),
              top=4, ceiling=None)
    for x in range(gx0 - 1, gx1 + 2):
        for z in range(gz0 - 1, gz1 + 2):
            edge = x in (gx0 - 1, gx1 + 1) or z in (gz0 - 1, gz1 + 1)
            t.set(x, SURF, z, S('or:durasteel_plating') if edge else
                  (S('or:durasteel_grate') if (x - gx0) % 4 == 2 else S('or:rusted_plating')))
    for z in range(gz0, gz1 + 1, 4):
        t.set(gx0 + 2, SURF, z, beam('x'))
    tunnel(t, 26, 18, 27, 22, h=4)
    for z in range(18, 23):
        for x in (26, 27):
            put(t, x, 0, z, floor)
    # ramp east from the garage floor to the yard
    for k, x in enumerate(range(gx1 + 1, gx1 + 6)):
        for z in range(18, 23):
            t.set(x, k + 1, z, stairs('or:adobe_stairs', 'east'))
            for y in range(0, k + 1):
                put(t, x, y, z, fill)
            for y in range(k + 2, G + 3):
                t.set(x, y, z, AIR)
        for z in (17, 23):
            for y in range(0, SURF + 1):
                put(t, x, y, z, wall)
            t.set(x, G, z, S('or:adobe_wall', up=True, north='none', south='none', east='none', west='none', waterlogged=False))
    for z in range(18, 23):
        for y in range(1, 5):
            t.set(gx1 + 1, y, z, AIR)
        t.set(gx1 + 1, SURF, z, beam('z'))
    # the speeder half put together under the hoist
    landspeeder(t, 29, 1, 19, 'repair')
    for x in range(gx0, gx1 + 1):
        t.set(x, 4, 24, beam('x'))
    t.set(31, 3, 24, chain_y())
    t.set(31, 2, 24, pipes('x'))
    t.set(30, 1, 24, S('or:durasteel_vent'))
    t.set(32, 1, 24, slab('or:durasteel_plating_slab', 'bottom'))
    for (x, z) in ((29, 23), (33, 22), (34, 18)):
        t.set(x, 1, z, S('or:debris'))
    bench = [S('crafting_table'), S('smithing_table'), barrel('up'), S('anvil', facing='east'), S('or:cargo_crate'),
             S('grindstone', face='floor', facing='east'), barrel('up')]
    for i, x in enumerate(range(gx0, gx1 + 1)):
        t.set(x, 1, gz0, bench[i % len(bench)])
    for i, x in enumerate(range(gx0, gx1 - 2)):
        t.set(x, 1, gz1, [S('or:cargo_crate'), barrel('up'), S('or:rusted_plating'), S('or:wreck_salvage'), barrel('north')][i % 5])
    for (x, z) in ((gx0 + 1, gz0 + 3), (gx1 - 1, gz1 - 2), (gx0 + 5, gz1 - 1)):
        t.set(x, 4, z, S('or:light_panel_amber'))
    rooms.append((gx0, 1, gz0, gx1, 4, gz1))
    # --- the bedrooms and the storeroom to the south
    bx0, bz0, bx1, bz1 = 12, 29, 28, 34
    sunk_room(t, bx0, bz0, bx1, bz1, wall, floor, top=4, ceiling=wall)
    for xw in (17, 23):
        for z in range(bz0, bz1 + 1):
            for y in range(1, 5):
                t.set(xw, y, z, wall(xw, y, z))
        t.set(xw, 1, bz0 + 3, AIR)
        t.set(xw, 2, bz0 + 3, AIR)
    tunnel(t, 19, 27, 21, 28)
    for x in (19, 20, 21):
        put(t, x, 0, 27, floor)
        put(t, x, 0, 28, floor)
    dome_over(t, 14, 31, 3, wall, base=SURF, cap=S('light_blue_stained_glass'))
    dome_over(t, 26, 31, 3, wall, base=SURF, cap=S('light_blue_stained_glass'))
    # parents: the wide bed, a dresser, the family picture on the screen
    bed(t, 13, 1, 33, 'north', 'brown')
    bed(t, 14, 1, 33, 'north', 'brown')
    t.set(13, 1, 34, AIR)
    bed(t, 13, 1, 33, 'south', 'brown')
    bed(t, 14, 1, 33, 'south', 'brown')
    t.set(16, 1, 34, barrel('north'))
    t.set(16, 1, 33, barrel('west'))
    t.set(16, 2, 34, S('candle', candles=2, lit=True, waterlogged=False))
    t.set(12, 3, 31, S('or:holo_screen', facing='east'))
    t.set(15, 1, 30, S('white_carpet'))
    t.set(14, 1, 30, S('white_carpet'))
    # children: two little beds, toys, a painted banner
    bed(t, 25, 1, 33, 'south', 'light_blue')
    bed(t, 27, 1, 33, 'south', 'yellow')
    t.set(26, 1, 34, S('decorated_pot', facing='north', cracked=False, waterlogged=False))
    t.set(24, 1, 30, slab('cut_sandstone_slab', 'top'))
    t.set(24, 2, 30, slab('cut_red_sandstone_slab', 'bottom'))  # a model speeder on the table
    t.set(25, 1, 30, S('light_blue_carpet'))
    t.set(26, 1, 31, S('yellow_carpet'))
    t.set(28, 1, 30, S('candle', candles=1, lit=True, waterlogged=False))
    wall_banner(t, 26, 3, 29, 'south', 'light_blue', [('minecraft:circle', 'yellow'), ('minecraft:stripe_bottom', 'brown')])
    # storeroom: barrels, crates, the strongbox
    for (x, z) in ((18, 33), (18, 34), (22, 34), (22, 33)):
        t.set(x, 1, z, barrel('up') if (x + z) % 2 else S('or:cargo_crate'))
    t.set(18, 2, 34, barrel('up'))
    t.chest(20, 1, 34, 'north', loot=LOOT)
    t.set(20, 4, 32, lantern(True))
    rooms.append((bx0, 1, bz0, bx1, 4, bz1))
    rooms.append((13, SURF, 29, 15, SURF + 2, 33))
    # --- the courtyard: the planter, the shade and bench, the laundry, lamps
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if (x, z) == (cx, cz):
                t.set(x, 0, z, S('moss_block'))
                t.set(x, 1, z, S('flowering_azalea'))
            else:
                t.set(x, 1, z, slab('or:adobe_slab', 'bottom') if (x + z) % 2 else S('moss_carpet'))
    t.set(cx + 2, 1, cz, S('composter', level=5))
    t.set(cx - 2, 1, cz + 2, S('water_cauldron', level=3))
    for (x, z) in ((cx + 3, cz + 3), (cx + 3, cz - 3)):
        col(t, x, z, 1, 3, S('oak_fence', waterlogged=False))
    for z in range(cz - 3, cz + 4):
        t.set(cx + 3, 4, z, S('or:canvas_slab', type='bottom', waterlogged=False))
        if math.hypot(4, z - cz) <= 6.2:
            t.set(cx + 4, 4, z, S('or:canvas_slab', type='bottom', waterlogged=False))
    for z in (cz - 1, cz, cz + 1):
        t.set(cx + 4, 1, z, stairs('spruce_stairs', 'east'))
    t.set(cx + 3, 3, cz, lantern(True))
    t.set(cx + 3, 4, cz, S('or:canvas'))
    # laundry line across the west side
    col(t, cx - 4, cz - 3, 1, 3, S('oak_fence', waterlogged=False))
    col(t, cx - 4, cz + 3, 1, 3, S('oak_fence', waterlogged=False))
    for z in range(cz - 2, cz + 3):
        t.set(cx - 4, 3, z, S('iron_chain', axis='z', waterlogged=False))
    for z, c in ((cz - 2, 'white'), (cz, 'light_blue'), (cz + 2, 'orange')):
        wall_banner(t, cx - 4, 2, z, 'east', c, [])
    # lamps on the courtyard wall
    for (x, z) in ((cx - 4, cz - 4), (cx + 4, cz + 4), (cx - 5, cz + 2)):
        if t.is_air(x, 1, z):
            t.set(x, 1, z, S('lantern', hanging=False, waterlogged=False))
    # --- the yard: vaporators, the water tank, the solar rack, the graves, the track
    for (x, z) in ((4, 5), (36, 5), (4, 36), (36, 37), (32, 9)):
        vaporator(t, x, z, 8)
    # water tank on legs
    tx, tz = 12, 7
    for (dx, dz) in ((0, 0), (2, 0), (0, 2), (2, 2)):
        col(t, tx + dx, tz + dz, G, G + 1, beam('y'))
    for dx in range(3):
        for dz in range(3):
            for y in (G + 2, G + 3):
                corner = dx in (0, 2) and dz in (0, 2)
                t.set(tx + dx, y, tz + dz, S('waxed_weathered_cut_copper') if not corner else S('waxed_weathered_copper_grate', waterlogged=False))
            t.set(tx + dx, G + 4, tz + dz, S('waxed_weathered_cut_copper_slab', type='bottom', waterlogged=False))
    t.set(tx + 1, G, tz + 1, pipes('y'))
    t.set(tx + 1, G + 1, tz + 1, pipes('y'))
    # solar rack
    for x in range(26, 31):
        t.set(x, G, 3, S('or:durasteel_plating') if x in (26, 30) else AIR)
        t.set(x, G + 1, 3, S('daylight_detector', inverted=False, power=0))
        t.set(x, G, 4, S('daylight_detector', inverted=False, power=0))
    # the grandparents' graves to the south-west
    grave(t, 7, 30)
    grave(t, 9, 30)
    # the speeder track from the ramp to the yard's edge, and the walk to the door
    for x in range(gx1 + 6, SX):
        for z in range(18, 23):
            if not t.is_air(x, SURF, z):
                t.set(x, SURF, z, S('dirt_path') if z in (19, 21) else S('coarse_dirt'))
    for z in range(0, ez - 4):
        for x in range(19, 22):
            if not t.is_air(x, SURF, z):
                t.set(x, SURF, z, S('dirt_path'))
    # scrub in the yard
    cells = [(x, SURF, z) for x in range(SX) for z in range(SZ) if t.name_at(x, SURF, z) in
             ('minecraft:coarse_dirt', 'minecraft:sand', 'minecraft:packed_mud') and t.is_air(x, G, z)]
    scatter_on(t, rng, cells, [(S('dead_bush'), 3), (S('short_dry_grass'), 3), (S('bush'), 1)], 0.05)
    carve(t, 0, 0, SX - 1, SZ - 1, G, G + 5, bank=1, noise=Noise(6199, 3.0))
    light_rooms(t, rooms)
    return t


# ---------------------------------------------------------------- the Orrin farm (after the raid)

def build_orrin():
    t = Template(SX, SY, SZ, seed=960113, name='wilds/homestead/orrin')
    rng = t.rng
    wall = patches(6201, ADOBE, 3.0)
    floor = patches(6202, FLOOR, 3.0)
    fill = patches(6203, FILL, 3.0)
    yard(t, 6200)
    rooms = []
    # --- the square courtyard, x 15..25, z 16..26
    qx0, qz0, qx1, qz1 = 15, 16, 25, 26
    sunk_room(t, qx0, qz0, qx1, qz1, wall, floor, top=SURF, ceiling=None)
    for x in range(qx0 - 1, qx1 + 2):
        for z in range(qz0 - 1, qz1 + 2):
            if qx0 <= x <= qx1 and qz0 <= z <= qz1:
                continue
            t.set(x, SURF - 1, z, S('cut_sandstone'))
            if (x + z) % 5 != 0:
                t.set(x, G, z, slab('or:adobe_slab', 'bottom'))
    # --- the entrance: a stair cut from the north under a small dome (its east side shot away)
    zb = stair_down(t, 19, 21, 8, 'south', wall, fill)
    for z in range(zb, qz0):
        for x in range(19, 22):
            put(t, x, 0, z, floor)
            for y in range(1, 4):
                t.set(x, y, z, AIR)
            for y in range(4, SURF + 1):
                put(t, x, y, z, wall)
        for x in (18, 22):
            for y in range(0, SURF + 1):
                put(t, x, y, z, wall)
    for x in range(18, 23):
        for z in range(4, 9):
            put(t, x, SURF, z, floor)
    dome_over(t, 20, 7, 3, wall, base=G, hole=(22, 6, 2.2))
    for x in range(19, 22):
        for y in (G, G + 1):
            t.set(x, y, 4, AIR)
    for (x, z) in ((23, 6), (23, 7), (24, 5), (22, 4)):
        put(t, x, G, z, pick(rng, [(S('or:adobe_bricks'), 2), (S('cobblestone'), 1), (S('gravel'), 1)]))
    # --- the living dome east, caved in
    lx0, lz0, lx1, lz1 = 29, 16, 36, 26
    sunk_room(t, lx0, lz0, lx1, lz1, wall, floor, top=4, ceiling=wall)
    dome_over(t, 32, 21, 5, wall, base=SURF, hole=(34, 19, 3.2))
    tunnel(t, 26, 20, 28, 22)
    for z in range(20, 23):
        for x in (26, 27, 28):
            put(t, x, 0, z, floor)
    # the room: the table overturned, broken pots, the counter, rubble under the breach
    t.set(31, 1, 20, slab('smooth_stone_slab', 'top'))
    t.set(31, 1, 21, stairs('spruce_stairs', 'north', 'top'))  # the table on its side
    t.set(30, 1, 22, stairs('spruce_stairs', 'west'))
    t.set(33, 1, 23, stairs('spruce_stairs', 'south', 'top'))
    for (x, z) in ((34, 19), (35, 18), (34, 18), (33, 19), (35, 20), (34, 20)):
        t.set(x, 1, z, pick(rng, [(S('or:adobe_bricks'), 3), (S('sand'), 2), (slab('or:adobe_slab', 'bottom'), 2)]))
    t.set(34, 2, 19, S('sand'))
    counter = [S('smoker', facing='west', lit=False), S('cut_sandstone'), S('cauldron'), barrel('west'), S('cut_sandstone'),
               S('decorated_pot', facing='west', cracked=True, waterlogged=False)]
    for i, z in enumerate(range(lz0 + 1, lz1)):
        t.set(lx1, 1, z, counter[i % len(counter)])
    t.set(lx0 + 1, 1, lz1, S('decorated_pot', facing='north', cracked=True, waterlogged=False))
    t.set(lx0 + 2, 1, lz1, S('cobweb'))
    t.set(lx1 - 1, 4, lz0, S('cobweb'))
    for (x, z) in ((30, 18), (32, 24), (35, 23)):
        t.set(x, 1, z, S('or:scorch_mark'))
    t.set(lx0 + 1, 1, lz0 + 1, S('lantern', hanging=False, waterlogged=False))
    rooms.append((lx0, 1, lz0, lx1, SURF + 3, lz1))
    # --- the garage west, the speeder burnt out, a ramp up to the west
    gx0, gz0, gx1, gz1 = 5, 16, 12, 27
    sunk_room(t, gx0, gz0, gx1, gz1, wall, patches(6204, [('or:durasteel_deck', 3), ('or:adobe_bricks', 2), ('coarse_dirt', 2)], 3.0),
              top=4, ceiling=None)
    for x in range(gx0 - 1, gx1 + 2):
        for z in range(gz0 - 1, gz1 + 2):
            edge = x in (gx0 - 1, gx1 + 1) or z in (gz0 - 1, gz1 + 1)
            if not edge and (x * 7 + z * 3) % 9 == 0:
                t.set(x, SURF, z, AIR)  # roof plates torn off
                continue
            t.set(x, SURF, z, S('or:durasteel_plating') if edge else
                  (S('or:wreck_plating_scorched') if (x + z) % 3 == 0 else S('or:rusted_plating')))
    tunnel(t, 13, 19, 14, 23, h=4)
    for z in range(19, 24):
        for x in (13, 14):
            put(t, x, 0, z, floor)
    for k, x in enumerate(range(gx0 - 1, gx0 - 6, -1)):
        for z in range(19, 24):
            t.set(x, k + 1, z, stairs('or:adobe_stairs', 'west'))
            for y in range(0, k + 1):
                put(t, x, y, z, fill)
            for y in range(k + 2, G + 3):
                t.set(x, y, z, AIR)
        for z in (18, 24):
            for y in range(0, SURF + 1):
                put(t, x, y, z, wall)
    for z in range(19, 24):
        for y in range(1, 5):
            t.set(gx0 - 1, y, z, AIR)
    landspeeder(t, 6, 1, 20, 'burnt')
    for (x, z) in ((9, 18), (10, 25), (7, 24), (11, 21)):
        t.set(x, 1, z, S('or:scorch_mark'))
    for (x, z) in ((8, 25), (11, 18), (6, 18)):
        t.set(x, 1, z, S('or:debris'))
    for i, z in enumerate(range(gz0, gz1 + 1, 2)):
        t.set(gx1, 1, z, [S('or:wreck_salvage'), barrel('west'), S('or:rusted_plating'), S('anvil', facing='north')][i % 4])
    t.set(gx1, 2, gz0 + 4, S('cobweb'))
    rooms.append((gx0, 1, gz0, gx1, 4, gz1))
    # --- the bedrooms to the south, the strongbox under the rug
    bx0, bz0, bx1, bz1 = 13, 30, 27, 35
    sunk_room(t, bx0, bz0, bx1, bz1, wall, floor, top=4, ceiling=wall)
    for xw in (18, 22):
        for z in range(bz0, bz1 + 1):
            for y in range(1, 5):
                t.set(xw, y, z, wall(xw, y, z))
        t.set(xw, 1, bz0 + 2, AIR)
        t.set(xw, 2, bz0 + 2, AIR)
    tunnel(t, 19, 27, 21, 29)
    for x in (19, 20, 21):
        for z in (27, 28, 29):
            put(t, x, 0, z, floor)
    dome_over(t, 15, 33, 3, wall, base=SURF, cap=S('light_blue_stained_glass'))
    dome_over(t, 25, 33, 3, wall, base=SURF, hole=(26, 32, 1.6))
    # parents: the bed stripped, the dresser ransacked, the rug over the strongbox
    bed(t, 14, 1, 34, 'north', 'brown')
    bed(t, 15, 1, 34, 'north', 'brown')
    t.set(17, 1, 35, barrel('north'))
    t.set(17, 1, 31, S('decorated_pot', facing='west', cracked=True, waterlogged=False))
    t.chest(15, 0, 31, 'north', loot=LOOT)
    for (x, z) in ((14, 31), (15, 31), (16, 31), (14, 32), (15, 32), (16, 32)):
        t.set(x, 1, z, S('red_carpet') if (x + z) % 2 else S('orange_carpet'))
    t.set(13, 3, 32, S('or:holo_screen', facing='east'))
    # children: the beds, sand blown in through the broken dome, a toy left behind
    bed(t, 24, 1, 35, 'north', 'light_blue')
    for (x, z) in ((25, 32), (26, 32), (26, 31), (25, 31), (26, 33)):
        t.set(x, 1, z, S('sand'))
    t.set(25, 2, 32, slab('or:adobe_slab', 'bottom'))
    t.set(27, 1, 34, slab('cut_red_sandstone_slab', 'bottom'))  # the model speeder, dropped
    t.set(23, 1, 31, S('cobweb'))
    # storeroom: emptied, crates smashed
    for (x, z) in ((19, 35), (21, 35)):
        t.set(x, 1, z, S('or:debris'))
    t.set(20, 1, 35, barrel('up'))
    t.set(21, 1, 33, S('or:scorch_mark'))
    t.set(20, 4, 33, lantern(True))
    t.set(14, 4, 33, S('or:light_panel_amber'))
    rooms.append((bx0, 1, bz0, bx1, 4, bz1))
    # --- the courtyard: sand drifted into the corners, the planter dead, scorches
    n = Noise(6205, 2.0)
    for x in range(qx0, qx1 + 1):
        for z in range(qz0, qz1 + 1):
            edge_d = min(x - qx0, qx1 - x, z - qz0, qz1 - z)
            if edge_d <= 1 and n.at(x, 0, z) > 0.35:
                t.set(x, 1, z, S('sand'))
                if edge_d == 0 and n.at(x, 1, z) > 0.6:
                    t.set(x, 2, z, S('sand'))
    for x in range(19, 22):
        for z in range(20, 23):
            t.set(x, 1, z, slab('or:adobe_slab', 'bottom') if (x, z) != (20, 21) else S('coarse_dirt'))
    t.set(20, 2, 21, S('dead_bush'))
    for (x, z) in ((17, 18), (23, 24), (24, 18), (16, 23)):
        t.set(x, 1, z, S('or:scorch_mark'))
    t.set(23, 1, 19, S('cauldron'))
    col(t, 17, 25, 1, 3, S('oak_fence', waterlogged=False))
    t.set(17, 3, 24, S('iron_chain', axis='z', waterlogged=False))
    t.set(24, 1, 25, S('lantern', hanging=False, waterlogged=False))
    rooms.append((qx0, 1, qz0, qx1, 3, qz1))
    # --- the yard: vaporators knocked down, one broken, the fresh grave with a rifle on it
    vaporator(t, 4, 5, state='down', fall='east')
    vaporator(t, 36, 6, state='broken', fall='south')
    vaporator(t, 5, 36, state='down', fall='north')
    vaporator(t, 35, 36, 8)
    grave(t, 30, 9, fresh=True, rifle=True)
    for (x, z) in ((27, 8), (31, 6), (33, 12), (10, 9), (25, 4), (12, 35), (29, 31)):
        t.set(x, G, z, S('or:scorch_mark'))
    # blaster scorches on the dome
    for (x, y, z) in ((29, SURF + 2, 18), (31, SURF + 3, 16), (18, G + 1, 6)):
        if not t.is_air(x, y, z):
            t.set(x, y, z, S('or:adobe_bricks'))
    for z in range(0, 4):
        for x in range(19, 22):
            if not t.is_air(x, SURF, z):
                t.set(x, SURF, z, S('dirt_path') if x != 20 else S('coarse_dirt'))
    cells = [(x, SURF, z) for x in range(SX) for z in range(SZ) if t.name_at(x, SURF, z) in
             ('minecraft:coarse_dirt', 'minecraft:sand', 'minecraft:packed_mud') and t.is_air(x, G, z)]
    scatter_on(t, rng, cells, [(S('dead_bush'), 4), (S('short_dry_grass'), 2), (S('or:debris'), 1)], 0.07)
    carve(t, 0, 0, SX - 1, SZ - 1, G, G + 5, bank=1, noise=Noise(6299, 3.0))
    light_rooms(t, rooms)
    return t


# ---------------------------------------------------------------- worldgen

def write_worldgen():
    loot_table(LOOT, [
        (1, [loot_item('oldrepublic:credit_chit', count=(2, 6))]),
        (1, [loot_item('bread', count=(2, 4))]),
        ((1, 2), [loot_item('copper_ingot', 4, (1, 3)), loot_item('iron_nugget', 4, (3, 6)), loot_item('leather', 3, (1, 2)),
                  loot_item('string', 3, (1, 3)), loot_item('oldrepublic:medpac', 1), loot_item('oldrepublic:hyperfuel_cell', 1)]),
    ])
    ad = adapted(SY, yard_stand=G, support_depth=6, ramp_rings=3)
    wilds_family('homestead', [('wilds/homestead/vessk', ad, 1), ('wilds/homestead/orrin', ad, 1)],
                 ['minecraft:desert', '#minecraft:is_badlands', '#minecraft:is_savanna', 'minecraft:plains'],
                 spacing=44, separation=16, salt=960101, buried=BURIED)


def main():
    args = cli(__doc__)
    for name, build in (('vessk', build_vessk), ('orrin', build_orrin)):
        t = build()
        print(name, t.size, t.count())
        save(t, 'wilds/homestead/' + name, args.preview)
    write_worldgen()


if __name__ == '__main__':
    main()
