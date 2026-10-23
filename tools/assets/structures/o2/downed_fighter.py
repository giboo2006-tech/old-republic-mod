"""oldrepublic:wilds/downed_fighter: a starfighter down in the wilds at the end of its own gouge.

Run: python3 tools/assets/structures/o2/downed_fighter.py [--preview DIR]

Two start variants, both 43 x 12 x 21 (long along x) and buried 3 (the ground's top block at
template y 2, feet at y 3). Only the crash itself is in the template: a long strip of churned earth
(levelled into the ground, ragged at its edges) with the gouge dug down its middle; the world's own
ground is kept round it, so the site fits any land biome.

* wilds/downed_fighter/republic_lean_to: a white Republic fighter has slid to a stop nose-first in
  its spoil, one wing dug in and the other raised, its canopy thrown open. The gouge runs back east
  past a trail of wreckage (a torn panel, a cannon, a wing tip, salvage). The pilot lived: beside the
  fighter is a lean-to of canvas over a bedroll with the survival kit (the chest), a dead camp fire
  with log seats, a rain cauldron, crates from the cargo hold and a distress beacon on a pole,
  its red light still burning.
* wilds/downed_fighter/imperial_grave: an Imperial fighter came in steeper: a short, deep gouge,
  one wing ripped off and lying flat in it, the ball cockpit tumbled out at the end with the other
  wing bent over it. Its viewport is smashed in; the fighter's locker (the chest) is still inside.
  The pilot did not live: a little way off, someone dug a grave, ringed it with stones and planted
  a wing strut over it with the pilot's black banner; the ejection seat lies beside it.

Worldgen: the structure (no monsters), its start pool, the set (spacing 40, separation 14, salt
960107, 4 chunks from the ruins), the biome tag (land biomes: no oceans, rivers, beaches, swamps,
caves or the steepest peaks) and the loot table chests/wilds/downed_fighter.
"""
import math

from common import (Template, S, AIR, B, Noise, pick, patches, stairs, slab, trapdoor, lantern, bed, wall_banner,
                    light_rooms, adapted, wilds_family, loot_item, loot_table, cli, save, rod, beam, pipes, campfire,
                    barrel)

SX, SY, SZ = 43, 12, 21
BURIED = 3
G = 3        # feet on the ground
SURF = 2     # the ground's top block
LOOT = 'chests/wilds/downed_fighter'
ZC = 10      # the gouge's centre line

FILL = [('dirt', 6), ('coarse_dirt', 2), ('stone', 1)]
CHURN = [('coarse_dirt', 5), ('dirt', 3), ('gravel', 2), ('rooted_dirt', 1)]
FLOOR = [(S('coarse_dirt'), 4), (S('gravel'), 3), (S('dirt'), 2), (S('mud'), 1)]
REPUBLIC = [('white_concrete', 6), ('or:republic_plating', 3), ('or:wreck_plating', 1), ('or:wreck_plating_scorched', 1)]
IMPERIAL = [('or:imperial_plating', 6), ('or:wreck_plating_scorched', 2), ('or:wreck_plating', 2)]
SIX = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
GROUND = ('minecraft:coarse_dirt', 'minecraft:dirt', 'minecraft:gravel', 'minecraft:rooted_dirt', 'minecraft:dirt_path')


def ground_ok(t, x, z):
    return t.name_at(x, SURF, z) in GROUND


# ---------------------------------------------------------------- the strip and the gouge

def strip(t, seed, half_width, extra=()):
    """The churned strip: fill to y 1 and churned earth at y 2 wherever |z - ZC| is within
    half_width(x) (roughened), plus the extra rectangles (x0, z0, x1, z1); explicit air above it
    for three layers so a small rise along the strip is cut away. Returns its cells."""
    fill = patches(seed, FILL, 3.0)
    top = patches(seed + 1, CHURN, 2.5)
    n = Noise(seed + 2, 2.0)
    cells = set()
    for x in range(SX):
        for z in range(SZ):
            inside = abs(z - ZC) <= half_width(x) + 1.4 * (n.at(x, 0, z) - 0.5)
            for (x0, z0, x1, z1) in extra:
                if x0 <= x <= x1 and z0 <= z <= z1 and n.at(x, 1, z) > 0.12 + 0.3 * (x in (x0, x1) or z in (z0, z1)):
                    inside = True
            if inside:
                cells.add((x, z))
                for y in range(0, SURF):
                    t.set(x, y, z, fill(x, y, z))
                t.set(x, SURF, z, top(x, SURF, z))
    return cells


def clear_over(t, cells, layers=3):
    """Explicit air over the strip (only where nothing is set), so a rise along it is cut away."""
    for (x, z) in cells:
        for y in range(G, G + layers):
            if t.get(x, y, z) is None:
                t.set(x, y, z, AIR)


def gouge(t, rng, x0, x1, width, depth):
    """Dig the gouge from x1 (where it touched down) back to x0: width(x) its half-width and
    depth(x) 1 or 2; the spoil thrown up along both lips. Returns the floor cells (x, y, z)."""
    floor = []
    for x in range(x0, x1 + 1):
        w = width(x)
        k = depth(x)
        for z in range(SZ):
            d = abs(z + 0.5 - (ZC + 0.5))
            if d <= w and ground_ok(t, x, z):
                t.set(x, SURF - k, z, pick(rng, FLOOR))
                for y in range(SURF - k + 1, SURF + 1):
                    t.set(x, y, z, AIR)
                floor.append((x, SURF - k, z))
            elif d <= w + 1.3 and ground_ok(t, x, z) and t.get(x, G, z) is None and rng.random() < 0.8:
                t.set(x, G, z, pick(rng, [(S('coarse_dirt'), 3), (S('dirt'), 2), (S('gravel'), 1)]))
    return floor


def scorch(t, rng, cells, p=0.3, debris=0.08):
    """Scorch marks and debris on the gouge floor and the ground round the wreck."""
    for (x, y, z) in cells:
        if t.get(x, y + 1, z) is None or t.name_at(x, y + 1, z) == 'minecraft:air':
            r = rng.random()
            if r < p:
                t.set(x, y + 1, z, S('or:scorch_mark'))
            elif r < p + debris:
                t.set(x, y + 1, z, S('or:debris'))


def heap(t, rng, cx, cz, rx, rz, h):
    """The spoil heap pushed up in front of the nose."""
    for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
        for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
            d = math.hypot((x - cx) / rx, (z - cz) / rz)
            top = int(h * (1 - d) + 0.5)
            if top <= 0 or t.get(x, 0, z) is None:
                continue
            for y in range(0, SURF + 1 + top):
                if t.get(x, y, z) is None or t.name_at(x, y, z) == 'minecraft:air':
                    t.set(x, y, z, pick(rng, [(S('coarse_dirt'), 3), (S('dirt'), 3), (S('gravel'), 1)]))


def place_on(t, cells, floor, x, z, st):
    """Put a piece of wreckage on the strip at (x, z): on the gouge floor when it is in the gouge."""
    if (x, z) not in cells:
        return
    fys = [fy for (fx, fy, fz) in floor if (fx, fz) == (x, z)]
    t.set(x, fys[0] + 1 if fys else G, z, st)


def plants(t, rng, cells, p=0.12):
    """A few weeds come back on the churned ground at the strip's edges."""
    for (x, z) in cells:
        if t.name_at(x, SURF, z) in ('minecraft:dirt', 'minecraft:rooted_dirt', 'minecraft:coarse_dirt') and \
                t.get(x, G, z) is None and abs(z - ZC) > 3 and rng.random() < p:
            t.set(x, G, z, pick(rng, [(S('short_grass'), 4), (S('fern'), 1), (S('bush'), 1), (S('dandelion'), 1)]))


# ---------------------------------------------------------------- variant 1: the Republic fighter and the lean-to

def republic_fighter(t, rng):
    """The fighter, nose west at x 7, body along z 9..11, canopy open, north wing dug in, south wing
    raised; engines at x 17..19."""
    pal = patches(7311, REPUBLIC, 2.0)
    for x in range(8, 18):
        for z in (9, 10, 11):
            t.set(x, SURF, z, pal(x, SURF, z))
            t.set(x, G, z, S('blue_concrete') if z != 10 and x % 3 else pal(x, G, z))
            t.set(x, G + 1, z, pal(x, G + 1, z) if (z != 10 or x > 12 or x < 9) else AIR)
            t.set(x, 1, z, pal(x, 1, z))
        if 13 <= x <= 16:
            t.set(x, G + 2, 10, slab('or:republic_plating_slab'))
    # the nose
    t.set(7, G, 10, stairs('or:republic_plating_stairs', 'east'))
    t.set(7, SURF, 10, S('white_concrete'))
    for z in (9, 11):
        t.set(7, G, z, stairs('or:republic_plating_stairs', 'east', 'top'))
    t.set(8, G + 1, 9, stairs('or:republic_plating_stairs', 'east'))
    t.set(8, G + 1, 11, stairs('or:republic_plating_stairs', 'east'))
    t.set(8, G + 1, 10, stairs('or:republic_plating_stairs', 'east'))
    # the open cockpit: the seat, the panel, the canopy hinged up at the back
    t.set(10, G + 1, 10, S('or:console', facing='east'))
    t.set(11, G + 1, 10, stairs('spruce_stairs', 'east'))
    t.set(12, G + 1, 10, AIR)
    for y in (G + 2, G + 3):
        t.set(13, y, 9, S('light_blue_stained_glass_pane', waterlogged=False))
        t.set(13, y, 10, S('light_blue_stained_glass_pane', waterlogged=False))
    t.set(13, G + 4, 10, slab('or:republic_plating_slab'))
    # the tail fin
    for x in (16, 17):
        for y in range(G + 3, G + 3 + (x - 14)):
            t.set(x, y, 10, S('blue_concrete') if y == G + 1 + (x - 14) + 1 else S('white_concrete'))
    # wings: the north one dug into the gouge's lip, the south one raised; cannons on the tips
    for x in range(12, 17):
        for z in (6, 7, 8):
            t.set(x, SURF if z > 6 else SURF - 1, z, pal(x, SURF, z) if (x + z) % 4 else S('or:wreck_plating_scorched'))
        for z in (12, 13):
            t.set(x, G, z, pal(x, G, z))
        for z in (14, 15):
            t.set(x, G + 1, z, S('white_concrete') if z == 14 else S('blue_concrete'))
    for x in range(10, 17):
        t.set(x, G + 2, 15, pipes('x'))
    t.set(9, G + 2, 15, rod('west'))
    t.set(17, G + 1, 15, S('or:durasteel_plating'))
    t.set(17, G + 1, 14, S('or:durasteel_plating'))
    t.set(13, G, 15, beam('y'))
    # engines and their scorched exhausts
    for z in (9, 11):
        for x in (17, 18):
            t.set(x, G, z, pipes('x'))
            t.set(x, G + 1, z, S('or:republic_plating'))
        t.set(19, G, z, S('or:durasteel_vent'))
        t.set(19, G + 1, z, S('or:wreck_plating_scorched'))
    t.set(18, G, 10, S('or:wreck_conduit'))
    t.set(18, G + 1, 10, slab('or:republic_plating_slab'))


def lean_to(t, rng):
    """The pilot's camp south of the fighter: the lean-to (canvas over a frame of struts) with the
    bedroll and the survival kit, the dead fire with log seats, the rain cauldron, the crates and the
    distress beacon. Returns the lean-to's region."""
    for x in range(4, 9):
        t.set(x, G + 2, 19, stairs('or:canvas_stairs', 'south'))
        t.set(x, G + 1, 18, stairs('or:canvas_stairs', 'south'))
        t.set(x, G, 17, slab('or:canvas_slab'))
        t.set(x, G, 19, S('or:canvas'))
        t.set(x, G + 1, 19, S('or:canvas'))
    for x in (3, 9):
        for y in range(G, G + 3):
            t.set(x, y, 19, S('spruce_fence', waterlogged=False))
        t.set(x, G, 17, S('spruce_fence', waterlogged=False))
    # under the canvas: bedroll, kit, a lamp
    bed(t, 4, G, 18, 'east', 'light_gray')
    t.chest(7, G, 18, 'north', loot=LOOT)
    t.set(8, G, 18, S('or:cargo_crate_republic'))
    t.set(6, G, 18, S('lantern', hanging=False, waterlogged=False))
    # the dead fire and its log seats
    t.set(7, G, 14, campfire(lit=False, facing='north'))
    for (x, z) in ((6, 14), (8, 14), (7, 13), (7, 15)):
        t.set(x, SURF, z, S('gravel'))
    t.set(5, G, 13, S('stripped_spruce_log', axis='z'))
    t.set(5, G, 14, S('stripped_spruce_log', axis='z'))
    t.set(9, G, 13, S('or:cargo_crate'))
    # rain water, crates out of the hold, the distress beacon on its pole
    t.set(2, G, 15, S('water_cauldron', level=2))
    t.set(3, G, 14, S('or:cargo_crate_republic'))
    t.set(3, G, 13, barrel('up'))
    t.set(3, G + 1, 14, S('or:cargo_crate'))
    for y in range(G, G + 3):
        t.set(11, y, 17, S('spruce_fence', waterlogged=False))
    t.set(11, G + 3, 17, S('or:light_panel_red'))
    t.set(11, G + 4, 17, rod('up'))
    t.set(11, G, 18, S('or:console', facing='north'))
    # the path trodden to the wreck
    for (x, z) in ((10, 15), (10, 14), (11, 13), (11, 12), (12, 12)):
        if t.name_at(x, SURF, z) in GROUND and t.get(x, G, z) is None:
            t.set(x, SURF, z, S('dirt_path'))
    return (4, G, 18, 8, G, 18)


def build_republic():
    t = Template(SX, SY, SZ, seed=960118, name='wilds/downed_fighter/republic_lean_to')
    rng = t.rng
    cells = strip(t, 7301, lambda x: 3.0 + max(0, 24 - x) * 0.12, extra=((1, 12, 13, 20),))
    floor = gouge(t, rng, 8, SX - 1, lambda x: 1.2 + (SX - 1 - x) * 0.035, lambda x: 2 if x < 26 else 1)
    heap(t, rng, 4.5, 10, 3.2, 4.2, 3)
    republic_fighter(t, rng)
    rooms = [lean_to(t, rng)]
    # the trail of wreckage back along the gouge
    for (x, z, st) in ((22, 12, slab('or:republic_plating_slab')), (23, 12, S('white_concrete')), (27, 9, S('or:wreck_salvage')),
                       (30, 7, slab('or:republic_plating_slab')), (31, 7, S('blue_concrete')), (33, 11, pipes('x')),
                       (34, 11, pipes('x')), (35, 11, rod('east')), (38, 9, S('or:wreck_plating_scorched')),
                       (24, 8, S('or:debris')), (29, 11, S('or:wreck_conduit')), (36, 13, S('white_concrete'))):
        place_on(t, cells, floor, x, z, st)
    scorch(t, rng, [c for c in floor if c[0] > 19], p=0.22)
    scorch(t, rng, [(x, SURF, z) for (x, z) in cells if 18 <= x <= 22 and abs(z - ZC) <= 3], p=0.6)
    plants(t, rng, cells)
    clear_over(t, cells)
    light_rooms(t, rooms)
    return t


# ---------------------------------------------------------------- variant 2: the Imperial fighter and the grave

def hex_wing(t, cells_of, x0, y0, z0, plane, w=7, h=8, broken=()):
    """A hexagonal wing panel: frame of plating round dark panels. plane 'xy' stands it upright
    (z fixed), 'xz' lays it flat (y fixed)."""
    for i in range(w):
        for j in range(h):
            cut = max(0, abs(j - (h - 1) / 2.0) - (h / 2.0 - 2.5))
            if i < cut or i > w - 1 - cut or (i, j) in broken:
                continue
            frame = i <= cut + 0.5 or i >= w - 1.5 - cut or j in (0, h - 1) or i == w // 2
            st = S('or:durasteel_plating') if frame else S('black_concrete')
            if plane == 'xy':
                t.set(x0 + i, y0 + j, z0, st)
            else:
                t.set(x0 + i, y0, z0 + j, st)
            cells_of.append((i, j))


def imperial_fighter(t, rng):
    """The ball cockpit at the end of the gouge (x 8..12, y 2..6, z 8..12), its viewport smashed in
    on the west, the north wing bent over it on its pylon. Returns the cockpit's region."""
    pal = patches(7411, IMPERIAL, 2.0)
    cx, cy, cz, r = 10.5, 4.0, 10.5, 2.6
    solid = set()
    for x in range(7, 14):
        for y in range(1, 8):
            for z in range(7, 14):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 + (z + 0.5 - cz) ** 2 <= r * r:
                    solid.add((x, y, z))
    shell = {c for c in solid if any((c[0] + a, c[1] + b, c[2] + d) not in solid for a, b, d in SIX)}
    for c in solid:
        t.set(*c, pal(*c) if c in shell else AIR)
    # the smashed viewport and its frame
    for (y, z) in ((3, 10), (4, 10)):
        t.set(8, y, z, AIR)
    for (y, z) in ((5, 10), (4, 9), (4, 11), (3, 9), (3, 11), (5, 9), (5, 11)):
        if (8, y, z) in shell:
            t.set(8, y, z, S('tinted_glass'))
    # inside: the seat, the console, the fighter's locker
    t.set(10, 2, 9, stairs('or:imperial_plating_stairs', 'west'))
    t.set(11, 2, 10, S('or:console', facing='west'))
    t.chest(10, 2, 11, 'west', loot=LOOT)
    t.set(11, 5, 10, S('or:light_panel_red'))
    # the pylon and the bent north wing
    t.set(10, 4, 7, beam('z'))
    t.set(10, 4, 6, beam('z'))
    wing = []
    hex_wing(t, wing, 7, 1, 5, 'xy', w=7, h=8)
    for x in range(8, 13):
        t.set(x, 9, 6, S('or:durasteel_plating') if x in (8, 12) or x == 10 else S('black_concrete'))
        t.set(x, 9, 7, slab('or:durasteel_plating_slab') if x != 10 else S('or:durasteel_plating'))
    # the south pylon, torn off short
    t.set(10, 4, 13, S('or:wreck_conduit'))
    t.set(10, 4, 14, beam('z'))
    return (9, 2, 9, 11, 5, 11)


def grave(t, rng, x, z):
    """The pilot's grave: a mound ringed with stones, a wing strut planted at its head with the
    pilot's black banner, the ejection seat beside it."""
    for dx in (0, 1):
        t.set(x + dx, G, z, S('coarse_dirt'))
        t.set(x + dx, SURF, z, S('dirt'))
    for (a, b) in ((-1, -1), (0, -1), (1, -1), (2, -1), (-1, 1), (0, 1), (1, 1), (2, 1), (3, 0)):
        if t.get(x + a, G, z + b) is None:
            t.set(x + a, G, z + b, pick(rng, [(S('cobblestone'), 2), (S('mossy_cobblestone'), 2), (S('stone_button', face='floor', facing='north', powered=False), 1)]))
    for y in range(G, G + 3):
        t.set(x - 1, y, z, beam('y'))
    t.set(x - 1, G + 3, z, S('or:imperial_plating'))
    wall_banner(t, x - 2, G + 2, z, 'west', 'black', [('minecraft:cross', 'red'), ('minecraft:border', 'black')])
    for (a, b, st) in ((4, 2, stairs('or:imperial_plating_stairs', 'north')), (4, 3, S('or:imperial_plating')),
                       (5, 2, S('iron_chain', axis='x', waterlogged=False)), (3, 3, S('or:debris'))):
        t.set(x + a, G, z + b, st)


def build_imperial():
    t = Template(SX, SY, SZ, seed=960119, name='wilds/downed_fighter/imperial_grave')
    rng = t.rng
    cells = strip(t, 7401, lambda x: 3.2 + max(0, 18 - x) * 0.15, extra=((0, 13, 9, 19),))
    floor = gouge(t, rng, 12, 33, lambda x: 1.6 + (33 - x) * 0.07, lambda x: 2 if x < 28 else 1)
    heap(t, rng, 5.5, 10, 2.6, 4.0, 2)
    rooms = [imperial_fighter(t, rng)]
    # the torn-off wing flat in the gouge
    wing = []
    hex_wing(t, wing, 21, SURF - 1, 7, 'xz', w=7, h=7, broken=((0, 3), (1, 4), (6, 0), (5, 6), (6, 6)))
    t.set(20, 1, 10, beam('x'))
    # wreckage thrown along the way
    for (x, z, st) in ((30, 8, S('or:wreck_plating_scorched')), (31, 12, S('or:wreck_salvage')), (34, 10, S('or:debris')),
                       (36, 9, S('or:wreck_plating')), (16, 13, S('or:imperial_plating_slab', type='bottom', waterlogged=False)),
                       (17, 7, S('or:wreck_conduit'))):
        place_on(t, cells, floor, x, z, st)
    grave(t, rng, 3, 16)
    scorch(t, rng, [c for c in floor], p=0.3)
    scorch(t, rng, [(x, SURF, z) for (x, z) in cells if 6 <= x <= 16 and abs(z - ZC) <= 4], p=0.45)
    plants(t, rng, cells, p=0.2)
    clear_over(t, cells)
    light_rooms(t, rooms)
    return t


# ---------------------------------------------------------------- worldgen

BIOMES = ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:snowy_plains', 'minecraft:ice_spikes',
          'minecraft:desert', '#minecraft:is_savanna', '#minecraft:is_forest', '#minecraft:is_taiga', '#minecraft:is_jungle',
          '#minecraft:is_badlands', '#minecraft:is_hill', 'minecraft:meadow', 'minecraft:cherry_grove',
          'minecraft:snowy_slopes', 'minecraft:stony_peaks']


def write_worldgen():
    loot_table(LOOT, [
        ((2, 4), [loot_item('oldrepublic:durasteel_plate', 8, (1, 2)), loot_item('copper_ingot', 5, (1, 3)),
                  loot_item('redstone', 4, (1, 3)), loot_item('oldrepublic:medpac', 4),
                  loot_item('oldrepublic:hyperfuel_cell', 2), loot_item('oldrepublic:alloy_plate', 2),
                  loot_item('oldrepublic:credit_chit', 3, (1, 3))]),
    ])
    ad = adapted(SY, yard_stand=G, support_depth=8, ramp_rings=2)
    wilds_family('downed_fighter', [('wilds/downed_fighter/republic_lean_to', ad, 1),
                                    ('wilds/downed_fighter/imperial_grave', ad, 1)],
                 BIOMES, spacing=40, separation=14, salt=960107, buried=BURIED)


def main():
    args = cli(__doc__)
    for name, build in (('republic_lean_to', build_republic), ('imperial_grave', build_imperial)):
        t = build()
        print(name, t.size, t.count())
        save(t, 'wilds/downed_fighter/' + name, args.preview)
    write_worldgen()


if __name__ == '__main__':
    main()
