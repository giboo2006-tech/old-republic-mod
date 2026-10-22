"""wilds/smuggler_cove: a smugglers' hideout on a stony shore (docs/MILESTONE_6.md 5.2, worker O3).

The story. A crew of coast-runners worked this shore for years: Rimward crystal and stolen Watch relief chits
came in by skiff at night, went up the hoist and out over the hills. The coastal patrols never found the
cave. Then someone talked. The crew left the same night, in a hurry: the fire still burns on the quay, the
skiff in the pool is half loaded, a chair lies on its back, the ledger is still open on its lectern, a crate
hangs from the hoist over the water, and the real stash is still behind the barrels in the den.

Two start variants, the sea to the south of both (41 x 31 x 41):

  cove  a rock headland with a sea cave: a gated boat pool and a quay inside, the crew's den at the back,
        a ladder shaft up to a comms shack on top, a timber lookout tower with an alarm bell, a hoist over
        the cave mouth and a jetty running out to sea with a crane at its head.
  arch  a sea stack pierced by a tide arch, with a dock ledge and a stash chamber inside; a boathouse,
        a bunkhouse and a storehouse on stilts beside it, catwalks, a jetty, steps cut up the stack to a
        lookout platform.

Water (common.py): layer SEA = 6 is the water line and DECK = 7 the first free layer above the sea. The
structure starts at 1 - DECK projected on WORLD_SURFACE_WG, which counts water: over the sea the first free
layer is 63, so the water line lands on the sea's top water layer and the jetty deck one block above it.
Nothing at or below SEA is explicit air, posts and rock run down to layer 0 (the pool element's support
continues them to the sea floor), the template's own water is the cave pool behind its sea gate, and the
structure applies waterlogging to whatever lands in the sea. On a sandy beach start the whole hideout
stands a block or two higher and the jetty runs over the sand like a pier at low tide; on a cliff-top
start the jetty becomes a hoist pier on stone footings.

    python3 tools/assets/structures/o3/smuggler_cove.py [--preview DIR] [--only cove,arch]
"""
import math

from common import (AIR, S, B, Template, mod, Noise, Rock, mix, pick, wet, stairs, slab, trapdoor, log, lantern,
                    chain, campfire, candle, door, bed, wood, post, boards, railing, ladder, hang_lamp, lamp_post,
                    crate_stack, crate_pile, table, Hut, skiff, blob_heights, rock_columns, stair_path, stilts,
                    shapes,
                    no_air_below, report, save, only_names, write_worldgen, loot_table, item, empty)

NAME = 'smuggler_cove'
SEA = 6      # the water line: the sea's top water layer
DECK = 7     # first free layer above the sea; the jetty and quay decks
SX, SY, SZ = 41, 31, 41
LOOT = 'chests/wilds/smuggler_cove'
LOOT_SHACK = 'chests/wilds/smuggler_cove_shack'

PLANTS = [(S('short_grass'), 30), (S('fern'), 6), (S('bush'), 3), (S('tall_grass', half='lower'), 4),
          (S('large_fern', half='lower'), 1)]


def top_block(seed):
    """The ground's top block: grass and stone up high, wet rock in the splash zone, gravel under water."""
    high = mix([('minecraft:grass_block', 36), ('minecraft:stone', 18), ('minecraft:gravel', 8),
                ('minecraft:coarse_dirt', 10), ('minecraft:moss_block', 5), ('minecraft:andesite', 13),
                ('minecraft:tuff', 6), ('minecraft:grass_block', 4)], seed, 3.5)
    splash = mix([('minecraft:mossy_cobblestone', 4), ('minecraft:gravel', 3), ('minecraft:tuff', 3),
                  ('minecraft:stone', 3), ('minecraft:moss_block', 1)], seed + 1, 2.5)
    under = mix([('minecraft:gravel', 5), ('minecraft:sand', 3), ('minecraft:clay', 1), ('minecraft:tuff', 2)],
                seed + 2, 2.5)

    def f(x, y, z):
        if y <= SEA - 1:
            return under(x, y, z)
        if y <= DECK + 1:
            return splash(x, y, z)
        return high(x, y, z)
    return f


def pad(heights, x0, z0, x1, z1, h, blend=3):
    """Level the ground of a rectangle to layer h, easing the heights around it back to the natural ones."""
    for (x, z), v in list(heights.items()):
        dx = max(x0 - x, 0, x - x1)
        dz = max(z0 - z, 0, z - z1)
        d = max(dx, dz)
        if d == 0:
            heights[(x, z)] = h
        elif d <= blend:
            k = d / float(blend + 1)
            heights[(x, z)] = int(round(h * (1 - k) + v * k))


def plants(t, heights, rng, density=0.35):
    for (x, z), h in sorted(heights.items()):
        if t.get(x, h, z) != 'minecraft:grass_block' or t.get(x, h + 1, z) is not None:
            continue
        if h + 2 >= SY or rng.random() > density:
            continue
        st = pick(rng, PLANTS)
        t.set(x, h + 1, z, st)
        if 'half=lower' in st:
            t.set(x, h + 2, z, st.replace('half=lower', 'half=upper'))


def skerry(heights, cx, cz, r, peak, seed):
    """A small rock poking out of the sea (added to a height map)."""
    n = Noise(seed, 2.0)
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            if not (0 <= x < SX and 0 <= z < SZ):
                continue
            d = math.hypot(x - cx, z - cz) / r + 0.35 * (n.at(x, 0, z) - 0.5)
            if d >= 1.0:
                continue
            h = int(round(SEA - 3 + (peak - SEA + 3) * (1 - max(0.0, d) ** 1.6)))
            heights[(x, z)] = max(heights.get((x, z), -1), h)


# ================================================================ variant cove

def cave_ceiling(x, z, n):
    c = DECK + 4 + int(round(3 * (1 - ((x - 16) / 8.0) ** 2)))
    if n.at(x, 0, z) > 0.66:
        c += 1
    if z >= 21:
        c -= 1  # the mouth is a little lower than the hall
    return c


def build_cove():
    t = Template(SX, SY, SZ, seed=960150, name='cove')
    rng = t.rng
    rock = Rock(1501, wet_band=(SEA - 1, DECK + 1))
    top = top_block(1502)
    cn = Noise(1503, 2.5)

    # ---- the headland: a rock knoll in the north half, steep to the sea on its south side
    hts = blob_heights(SX, SZ, cx=19, cz=12, radii=(19.0, 21.0, 11.5, 11.5), base=DECK - 3, peak=DECK + 15,
                       powers=(1.8, 2.0, 1.7, 4.5), seed=1504, wobble=0.14, bumps=3.0)
    # weathered tors on the crown and a lower shoulder to the east
    for (cx, cz, r, rise) in ((14, 13, 2.2, 3), (24, 9, 1.8, 2), (34, 15, 3.5, 2), (5, 12, 2.0, 2)):
        for (x, z), h in list(hts.items()):
            d = math.hypot(x - cx, z - cz)
            if d < r:
                hts[(x, z)] = h + int(round(rise * (1 - d / r) + 0.4))

    # the sea cave: quay (x 9..14), pool (x 15..20), east ledge (x 21..23), den at the back (z 4..8)
    cave = {}
    for x in range(9, 24):
        for z in range(4, 23):
            if z == 22 and x > 20:
                continue  # the east jamb of the mouth
            cave[(x, z)] = cave_ceiling(x, z, cn)
    # keep rock over the cave: two layers over the hall, one over the mouth; ease the ground around it
    for (x, z), c in cave.items():
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                p = (x + dx, z + dz)
                if not (0 <= p[0] < SX and 0 <= p[1] < SZ):
                    continue
                need = c + (1 if z >= 21 else 2) - (1 if (dx or dz) and p not in cave else 0)
                if hts.get(p, -1) < need:
                    hts[p] = need
    # the cliff face over the mouth runs a little higher than the knoll falls there
    for x in range(7, 25):
        for z in (21, 22):
            if (x, z) in hts:
                hts[(x, z)] = max(hts[(x, z)], DECK + 6 + (1 if z == 21 else 0))

    # level pads: the comms shack over the den, the lookout tower on the east shoulder
    F = DECK + 9            # shack floor layer
    pad(hts, 8, 4, 14, 9, F - 1, blend=2)
    skerry(hts, 33, 30, 2.6, DECK + 2, 1505)
    skerry(hts, 37, 25, 1.7, DECK + 1, 1506)
    skerry(hts, 23, 34, 2.2, SEA - 1, 1507)
    skerry(hts, 3, 29, 2.4, DECK + 3, 1508)
    skerry(hts, 1, 24, 2.0, DECK + 1, 1509)

    # the channel in front of the sea gate and the jetty's line stay open water
    for (x, z) in list(hts):
        if (13 <= x <= 21 and 23 <= z <= 27) or (8 <= x <= 12 and z >= 23):
            hts[(x, z)] = min(hts[(x, z)], SEA - 2)
    rock_columns(t, hts, rock, top=top)

    # ---- carve the cave
    for (x, z), c in cave.items():
        in_pool = 15 <= x <= 20 and 9 <= z <= 21
        if in_pool:
            t.set(x, 0, z, pick(rng, [('minecraft:gravel', 5), ('minecraft:sand', 3), ('minecraft:clay', 1)]))
            for y in range(1, SEA + 1):
                t.set(x, y, z, 'minecraft:water')
            for y in range(DECK, c + 1):
                t.set(x, y, z, AIR)
        else:
            # quay, ledge and den floors: rock up to DECK, air above
            for y in range(0, DECK):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, rock(x, y, z))
            t.set(x, DECK, z, rock(x, DECK, z) if z >= 9 else 'minecraft:spruce_planks')
            for y in range(DECK + 1, c + 1):
                t.set(x, y, z, AIR)
    # the den floor: boards, a rug
    boards(t, 9, 4, 23, 8, DECK, [('minecraft:spruce_planks', 5), ('minecraft:dark_oak_planks', 2),
                                  ('minecraft:oak_planks', 1)], rng, along='x')
    for x in range(15, 19):
        for z in range(5, 8):
            t.set(x, DECK + 1, z, 'minecraft:brown_carpet' if (x + z) % 5 else 'minecraft:green_carpet')
    # boards along the pool edge of the quay
    boards(t, 13, 9, 14, 22, DECK, [('minecraft:spruce_planks', 4), ('minecraft:oak_planks', 1)], rng, along='z',
           sag=0.06)
    # the cave walls and roof a little wet and alive: moss near the mouth, lichen and dripstone inside
    for (x, z), c in cave.items():
        roof = (x, c + 1, z)
        if t.get(*roof) is not None and t.get(*roof) != AIR and t.get(x, c, z) == AIR:
            r = rng.random()
            if z >= 19 and r < 0.35:
                t.set(*roof, 'minecraft:moss_block')
                if rng.random() < 0.4:
                    t.set(x, c, z, S('glow_lichen', down=False, up=True, north=False, south=False, east=False,
                                     west=False, waterlogged=False))
            elif 15 <= x <= 20 and 9 <= z <= 20 and r < 0.12:
                t.set(x, c, z, S('pointed_dripstone', thickness='tip', vertical_direction='down', waterlogged=False))
                t.set(*roof, 'minecraft:dripstone_block')
            elif r < 0.07:
                t.set(x, c, z, S('glow_lichen', up=True, down=False, north=False, south=False, east=False,
                                 west=False, waterlogged=False))
    # pool life: seagrass and a few sea pickles on the bottom
    for x in range(15, 21):
        for z in range(9, 22):
            r = rng.random()
            if r < 0.25:
                t.set(x, 1, z, 'minecraft:seagrass')
            elif r < 0.30:
                t.set(x, 1, z, S('sea_pickle', pickles=rng.randint(1, 3), waterlogged=True))

    # ---- the sea gate across the pool mouth (z 22): a log boom under water, fence teeth above, a beam
    for x in range(15, 21):
        for y in range(0, SEA + 1):
            t.set(x, y, 22, log('dark_oak_log', 'x'))
        for y in range(DECK, DECK + 3):
            t.set(x, y, 22, S('dark_oak_fence', waterlogged=False))
    for x in range(14, 22):
        t.set(x, DECK + 3, 22, log('dark_oak_log', 'x'))
    post(t, 14, 22, DECK + 2, DECK + 1, log('dark_oak_log'))
    for x in (15, 20):
        for y in range(DECK + 4, cave[(x, 22)] + 1):
            t.set(x, y, 22, chain('y'))
    t.set(13, DECK + 1, 21, S('grindstone', face='floor', facing='east'))   # the gate winch

    # ---- the quay
    t.set(11, DECK + 1, 14, campfire('north', lit=True))
    for z in (13, 14, 15):
        t.set(9, DECK + 1, z, log('stripped_spruce_log', 'z'))
    t.set(11, DECK + 1, 16, log('stripped_spruce_log', 'x'))
    t.set(12, DECK + 1, 16, log('stripped_spruce_log', 'x'))
    t.set(12, DECK + 1, 12, stairs('spruce_stairs', 'north', 'top'))     # the chair knocked over
    t.set(12, DECK + 1, 10, lantern())                                   # a lantern set down and left
    t.set(10, DECK + 1, 12, 'minecraft:cauldron')
    crate_pile(t, 9, 17, 11, 20, DECK + 1, rng, density=0.8, max_h=3)
    crate_stack(t, 12, DECK + 1, 19, rng, height=1)
    for z in (11, 16, 20):
        t.set(14, DECK + 1, z, S('spruce_fence', waterlogged=False))     # mooring posts
    t.set(14, DECK + 2, 16, lantern())
    mod.spot(t, 'HEART', 12, DECK + 1, 14)

    # the skiff in the pool, half loaded
    skiff(t, 17, DECK, 11, 'south', length=8, wood_kind='spruce', water=True, rng=rng,
          cargo=('oldrepublic:cargo_crate', 'oldrepublic:cargo_crate_republic', None,
                 S('barrel', facing='up', open=False)))

    # the east ledge: barrels, nets, a lamp
    for z in (10, 11, 12):
        crate_stack(t, 23, DECK + 1, z, rng, height=rng.choice((1, 2)),
                    kinds=[(S('barrel', facing='up', open=False), 3), ('oldrepublic:cargo_crate', 1)])
    t.set(22, DECK + 1, 13, S('barrel', facing='west', open=False))
    lamp_post(t, 21, DECK + 1, 17, height=1)
    t.set(23, DECK + 1, 19, 'minecraft:composter')
    t.set(23, DECK + 2, 20, 'minecraft:cobweb')

    # ---- the den at the back of the cave
    bed(t, 11, DECK + 1, 4, 'north', 'brown')
    bed(t, 13, DECK + 1, 4, 'north', 'green')
    for z in (4, 5):
        t.set(11, DECK + 2, z, slab('spruce_slab', 'top'))
    bed(t, 11, DECK + 3, 4, 'north', 'gray')                               # the upper bunk
    t.set(12, DECK + 1, 6, S('chest', facing='north', type='single', waterlogged=False))  # an empty footlocker
    table(t, 17, DECK + 1, 6, kind='slab', on=candle('white', 2, lit=True))
    t.set(16, DECK + 1, 6, stairs('spruce_stairs', 'west'))
    t.set(18, DECK + 1, 6, stairs('spruce_stairs', 'east'))
    t.set(17, DECK + 1, 7, stairs('spruce_stairs', 'north', 'top'))      # a second chair, overturned
    mod.lectern(t, 20, DECK + 1, 5, facing='south', book=True)            # the ledger, still open
    t.set(21, DECK + 1, 4, S('oldrepublic:console', facing='south'))
    t.set(22, DECK + 1, 4, S('oldrepublic:holo_screen', facing='south'))
    crate_pile(t, 21, 6, 23, 8, DECK + 1, rng, density=0.6, max_h=2)
    # the stash: a nook in the east wall behind two barrels
    t.set(24, DECK + 1, 7, S('chest', facing='west', type='single', waterlogged=False), nbt={'LootTable': 'oldrepublic:' + LOOT})
    t.set(24, DECK + 2, 7, AIR)
    t.set(24, DECK + 3, 7, rock(24, DECK + 3, 7))
    t.set(23, DECK + 1, 7, S('barrel', facing='up', open=False))
    t.set(23, DECK + 2, 7, S('barrel', facing='west', open=False))
    t.set(23, DECK + 1, 8, S('barrel', facing='up', open=False))
    # the ladder shaft up to the shack (the den's west end)
    for y in range(DECK + 1, F):
        t.set(10, y, 6, S('ladder', facing='east', waterlogged=False))
        for (px, pz) in ((9, 6), (10, 5), (10, 7), (11, 6)):
            if y > cave.get((px, pz), 0):
                t.set(px, y, pz, rock(px, y, pz))
    for y in range(DECK + 1, F):
        t.set(9, y, 6, rock(9, y, 6))
    # lamps
    hang_lamp(t, 13, cave[(13, 6)] - 1, 6, drop=1)
    hang_lamp(t, 19, cave[(19, 7)] - 2, 7, drop=2)
    hang_lamp(t, 11, cave[(11, 18)] - 1, 18, drop=1)
    hang_lamp(t, 22, cave[(22, 15)] - 1, 15, drop=1)
    hang_lamp(t, 17, cave[(17, 20)] - 2, 20, drop=2)

    # ---- the comms shack on top, over the den: a trapdoor in its floor opens on the shaft
    hut = Hut(8, F, 4, 7, 6, h=4, walls='spruce', frame='stripped_spruce_log', roof='shed', roof_wood='spruce',
              door=('west', 3, 1), windows=[('south', 3), ('east', 3)], rng=rng)
    hut.build(t)
    for x in range(8, 15):
        for z in range(4, 10):
            for y in range(hts.get((x, z), F - 1) + 1, F):
                t.set(x, y, z, rock(x, y, z))      # footing where the knoll falls away
    door('spruce_door', 8, F + 1, 7, t, facing='east', open_=True)
    t.set(8, F + 3, 7, 'minecraft:spruce_planks')
    t.set(10, F, 6, trapdoor('spruce_trapdoor', 'north', 'top', False))
    t.set(13, F + 1, 6, S('oldrepublic:console', facing='west'))
    t.set(13, F + 2, 6, S('oldrepublic:holo_screen', facing='west'))
    t.set(12, F + 1, 6, stairs('spruce_stairs', 'west'))
    bed(t, 9, F + 1, 5, 'north', 'blue')
    t.chest(11, F + 1, 8, facing='north', loot=LOOT_SHACK)
    t.set(13, F + 1, 8, S('barrel', facing='up', open=False))
    t.set(12, F + 1, 8, 'oldrepublic:cargo_crate')
    hang_lamp(t, 11, F + 3, 6, drop=1)
    # the aerial on the roof
    t.set(13, F + 7, 8, S('spruce_fence', waterlogged=False))
    t.set(13, F + 8, 8, S('spruce_fence', waterlogged=False))
    t.set(13, F + 9, 8, S('lightning_rod', facing='up', powered=False, waterlogged=False))

    # ---- steps from the jetty root up the west flank to the shack door
    steps = []
    for i, z in enumerate(range(22, 12, -1)):
        steps.append((6, DECK + i, z))
    steps += [(6, DECK + 9, 12), (6, F, 11), (6, F, 10), (6, F, 9), (6, F, 8), (6, F, 7), (7, F, 7)]
    for (x, y, z) in steps:
        for yy in range(hts.get((x, z), -1) + 1, y):
            t.set(x, yy, z, rock(x, yy, z))
    stair_path(t, steps, 'cobblestone_stairs', 'minecraft:cobblestone', clear=3)
    # the shore walk from the steps to the quay mouth
    for x in range(6, 10):
        t.set(x, DECK, 22, 'minecraft:cobblestone' if x < 9 else t.get(x, DECK, 22))
        for y in range(DECK + 1, DECK + 4):
            if t.get(x, y, 22) not in (None, AIR) and x < 9:
                t.set(x, y, 22, AIR)

    # ---- the lookout tower on the east shoulder
    P = DECK + 18
    legs = [(27, 7), (31, 7), (27, 11), (31, 11)]
    for (x, z) in legs:
        g = hts.get((x, z), DECK)
        t.set(x, g, z, 'minecraft:cobblestone')
        post(t, x, z, P - 1, g + 1, log('stripped_spruce_log'))
    gmax = max(hts.get(p, DECK) for p in legs)
    for yb in (gmax + 3, P - 4):
        for x in range(28, 31):
            for z in (7, 11):
                t.set(x, yb, z, S('spruce_fence', waterlogged=False))
        for z in range(8, 11):
            for x in (27, 31):
                t.set(x, yb, z, S('spruce_fence', waterlogged=False))
    for x in range(27, 32):
        t.set(x, P - 1, 7, log('stripped_spruce_log', 'x'))
        t.set(x, P - 1, 11, log('stripped_spruce_log', 'x'))
    # the ladder up the inside of the north side
    g29 = hts.get((29, 8), DECK)
    for y in range(min(hts.get((29, 7), DECK), g29) + 1, P):
        t.set(29, y, 7, 'minecraft:spruce_planks')
    ladder(t, 29, 8, g29 + 1, P - 1, 'south')
    for x in range(26, 33):
        for z in range(6, 13):
            t.set(x, P, z, 'minecraft:spruce_planks')
    t.set(29, P, 8, trapdoor('spruce_trapdoor', 'south', 'top', True))
    for x in range(26, 33):
        for z in range(6, 13):
            if x in (26, 32) or z in (6, 12):
                t.set(x, P + 1, z, S('spruce_fence', waterlogged=False))
    for (x, z) in ((26, 6), (32, 6), (26, 12), (32, 12)):
        post(t, x, z, P + 3, P + 1, log('stripped_spruce_log'))
    for x in range(25, 34):
        for z in range(5, 14):
            if min(x - 25, 33 - x, z - 5, 13 - z) == 0:
                f = 'south' if z == 5 else 'north' if z == 13 else 'east' if x == 25 else 'west'
                t.set(x, P + 4, z, stairs('spruce_stairs', f))
            else:
                t.set(x, P + 4, z, 'minecraft:spruce_planks')
    for x in range(27, 32):
        for z in range(7, 12):
            if min(x - 27, 31 - x, z - 7, 11 - z) == 0:
                f = 'south' if z == 7 else 'north' if z == 11 else 'east' if x == 27 else 'west'
                t.set(x, P + 5, z, stairs('spruce_stairs', f))
            else:
                t.set(x, P + 5, z, slab('spruce_slab', 'bottom'))
    t.set(29, P + 3, 10, S('bell', attachment='ceiling', facing='north', powered=False))
    t.set(30, P + 1, 11, S('spruce_fence', waterlogged=False))
    t.set(30, P + 2, 11, S('lightning_rod', facing='south', powered=False, waterlogged=False))  # the spyglass
    t.set(27, P + 2, 7, lantern())
    t.set(31, P + 1, 8, S('barrel', facing='up', open=False))
    for x in range(27, 32):
        for z in range(7, 12):
            for y in range(P + 1, P + 4):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)

    # ---- the hoist over the cave mouth, a crate still hanging from it
    g = max(hts.get((16, 19), DECK), hts.get((20, 19), DECK))
    for x in (16, 20):
        post(t, x, 19, g + 4, hts.get((x, 19), DECK) + 1, log('stripped_spruce_log'))
    for x in range(16, 21):
        t.set(x, g + 5, 19, log('stripped_spruce_log', 'x'))
    for z in range(18, 26):
        t.set(18, g + 6, z, log('stripped_spruce_log', 'z'))
    t.set(18, g + 5, 20, stairs('spruce_stairs', 'north', 'top'))
    for y in range(DECK + 2, g + 6):
        t.set(18, y, 25, chain('y'))
    t.set(18, DECK + 1, 25, 'oldrepublic:cargo_crate')

    # ---- the cargo lean-to on the knoll top
    lt = [(21, 14), (25, 14), (21, 17), (25, 17)]
    roof_y = max(hts.get(p, DECK) for p in lt) + 3
    for (x, z) in lt:
        post(t, x, z, roof_y - 1, hts.get((x, z), DECK) + 1, S('spruce_fence', waterlogged=False))
    for x in range(20, 27):
        for z in range(14, 18):
            t.set(x, roof_y, z, slab('oldrepublic:canvas_slab', 'bottom'))
    for x in range(22, 25):
        for z in range(15, 17):
            g = hts.get((x, z), DECK)
            room = roof_y - 1 - g
            if room >= 1:
                crate_stack(t, x, g + 1, z, rng, height=min(room, rng.choice((1, 2, 2))))

    # ---- the jetty, its crane, the second skiff
    keep = set()
    posts = [(9, z) for z in (24, 27, 30, 33)] + [(11, z) for z in (24, 27, 30, 33)]
    posts += [(5, 35), (5, 38), (10, 38), (15, 35), (15, 38), (8, 35), (12, 35), (14, 37)]
    keep |= set(posts)
    boards(t, 9, 23, 11, 34, DECK, [('minecraft:spruce_planks', 5), ('minecraft:oak_planks', 2),
                                     ('minecraft:dark_oak_planks', 1)], rng, along='x', missing=0.03, sag=0.05,
           keep=keep)
    boards(t, 5, 35, 15, 38, DECK, [('minecraft:spruce_planks', 5), ('minecraft:oak_planks', 2)], rng, along='z',
           missing=0.03, sag=0.05, keep=keep)
    for (x, z) in posts:
        post(t, x, z, DECK, 0, log('spruce_log'))
    for (x, z) in ((9, 24), (11, 30), (5, 38), (15, 35), (15, 38), (5, 35)):
        t.set(x, DECK + 1, z, log('spruce_log'))                 # bollards
    lamp_post(t, 9, DECK + 1, 33, height=2)
    lamp_post(t, 11, DECK + 1, 27, height=2)
    # the crane at the jetty head
    post(t, 14, 37, DECK + 6, DECK + 1, log('stripped_spruce_log'))
    for x in range(15, 18):
        t.set(x, DECK + 6, 37, log('stripped_spruce_log', 'x'))
    t.set(15, DECK + 5, 37, stairs('spruce_stairs', 'west', 'top'))
    for y in range(DECK + 3, DECK + 6):
        t.set(17, y, 37, chain('y'))
    t.set(17, DECK + 2, 37, S('lantern', hanging=True, waterlogged=False))
    crate_pile(t, 6, 36, 8, 37, DECK + 1, rng, density=0.7, max_h=2)
    t.set(12, DECK + 1, 37, S('barrel', facing='up', open=False))
    skiff(t, 13, DECK, 26, 'south', length=7, wood_kind='spruce', water=False, rng=rng,
          cargo=(S('barrel', facing='up', open=False),))

    # ---- grass and plants on the knoll
    plants(t, hts, rng)
    # a wind-bent spruce on the knoll's north shoulder
    tx, tz = 22, 5
    g = hts.get((tx, tz), DECK)
    post(t, tx, tz, g + 3, g + 1, log('spruce_log'))
    t.set(tx + 1, g + 4, tz, log('spruce_log', 'x'))
    for (lx, ly, lz) in ((tx, g + 4, tz), (tx + 1, g + 5, tz), (tx + 2, g + 4, tz), (tx + 1, g + 4, tz + 1),
                         (tx + 1, g + 4, tz - 1), (tx - 1, g + 3, tz), (tx, g + 3, tz + 1), (tx + 2, g + 5, tz),
                         (tx + 3, g + 4, tz), (tx, g + 4, tz - 1)):
        if t.get(lx, ly, lz) is None:
            t.set(lx, ly, lz, S('spruce_leaves', distance=1, persistent=True, waterlogged=False))

    # ---- light the cave, the den and the shack
    mod.light_fill(t, (8, DECK + 1, 3, 24, DECK + 8, 22))
    mod.light_fill(t, (9, F + 1, 5, 13, F + 4, 8))
    return t


# ================================================================ variant arch

def build_arch():
    sy = 28
    t = Template(SX, sy, SZ, seed=960151, name='arch')
    rng = t.rng
    rock = Rock(1511, wet_band=(SEA - 1, DECK + 1))
    top = top_block(1512)
    an = Noise(1513, 2.5)
    FL = DECK + 1           # the floors of the stilt houses and the boardwalk
    TOP = DECK + 13         # the lookout deck on the stack

    # ---- the stack: flat-topped, sheer-sided
    hts = blob_heights(SX, SZ, cx=12, cz=17, radii=(10.0, 8.5, 9.5, 10.0), base=DECK - 3, peak=DECK + 14,
                       powers=(5.0, 5.0, 5.0, 5.0), seed=1514, wobble=0.10, bumps=2.0)
    for (x, z) in list(hts):
        if x >= 21:
            hts[(x, z)] = min(hts[(x, z)], SEA - 1)
    pad(hts, 5, 13, 16, 20, TOP - 1, blend=2)
    span = [z for z in range(SZ) if hts.get((12, z), -1) >= DECK + 2]
    zn, zs = min(span), max(span)
    tunnel = {}
    for x in range(9, 16):
        for z in range(zn - 1, zs + 2):
            c = DECK + 3 + int(round(3 * (1 - ((x - 12) / 4.0) ** 2)))
            if an.at(x, 0, z) > 0.7:
                c += 1
            tunnel[(x, z)] = c
            if (x, z) in hts and zn <= z <= zs:
                hts[(x, z)] = max(hts[(x, z)], c + (2 if zn < z < zs else 1))
    for z in range(zn, zs + 1):
        for x, xc in ((8, 9), (16, 15)):
            hts[(x, z)] = max(hts.get((x, z), -1), tunnel[(xc, z)] + 1)
    # the stash chamber west of the ledge (x 4..7, z 14..18) keeps rock around and over it
    for x in range(3, 9):
        for z in range(13, 20):
            hts[(x, z)] = max(hts.get((x, z), -1), DECK + 6)
    skerry(hts, 30, 36, 2.4, DECK + 1, 1515)
    skerry(hts, 4, 33, 3.0, DECK + 3, 1516)
    skerry(hts, 22, 31, 1.6, SEA, 1517)
    rock_columns(t, hts, rock, top=top)

    # ---- carve the arch: the sea runs through (unset at and below the water line), the ledge on its west side
    for (x, z), c in sorted(tunnel.items()):
        if (x, z) not in hts:
            continue
        ledge = x <= 10 and zn < z < zs
        h = hts[(x, z)]
        if ledge:
            t.set(x, DECK, z, rock(x, DECK, z))
            for y in range(DECK + 1, min(c, h) + 1):
                t.set(x, y, z, AIR)
        else:
            for y in range(0, SEA + 1):
                t.set(x, y, z, None)
            for y in range(DECK, min(c, h) + 1):
                t.set(x, y, z, AIR)
    # the arch's roof: moss and lichen at the mouths, dripstone over the water
    for (x, z), c in sorted(tunnel.items()):
        roof = (x, c + 1, z)
        if t.get(x, c, z) != AIR or t.get(*roof) in (None, AIR):
            continue
        r = rng.random()
        if (z <= zn + 2 or z >= zs - 2) and r < 0.4:
            t.set(*roof, 'minecraft:moss_block')
        elif x >= 11 and r < 0.10:
            t.set(*roof, 'minecraft:dripstone_block')
            t.set(x, c, z, S('pointed_dripstone', thickness='tip', vertical_direction='down', waterlogged=False))
        elif r < 0.16:
            t.set(x, c, z, S('glow_lichen', up=True, down=False, north=False, south=False, east=False, west=False,
                             waterlogged=False))
    # the dock ledge: mooring posts, a lamp, a few crates; a skiff tied up in the channel
    zm = (zn + zs) // 2
    for z in range(zn + 2, zs - 1, 4):
        t.set(10, DECK + 1, z, S('spruce_fence', waterlogged=False))
    lamp_post(t, 10, DECK + 1, zm, height=1)
    crate_pile(t, 9, zm + 2, 9, zm + 4, DECK + 1, rng, density=0.9, max_h=2)
    t.set(9, DECK + 1, zm - 3, S('barrel', facing='up', open=False))
    skiff(t, 13, DECK, zn + 2, 'south', length=6, wood_kind='spruce', water=False, rng=rng,
          cargo=('oldrepublic:cargo_crate', None, S('barrel', facing='up', open=False)))
    mod.spot(t, 'HEART', 9, DECK + 1, zm - 1)

    # ---- the stash chamber, open to the ledge at z 15..16, a ladder shaft up to the lookout deck
    for x in range(4, 8):
        for z in range(14, 19):
            t.set(x, DECK, z, 'minecraft:spruce_planks' if (x + z) % 3 else 'minecraft:dark_oak_planks')
            for y in range(DECK + 1, DECK + 5):
                t.set(x, y, z, AIR)
    for z in (15, 16):
        t.set(8, DECK, z, rock(8, DECK, z))
        for y in range(DECK + 1, DECK + 4):
            t.set(8, y, z, AIR)
    t.set(4, DECK + 1, 18, S('chest', facing='east', type='single', waterlogged=False),
          nbt={'LootTable': 'oldrepublic:' + LOOT})
    t.set(5, DECK + 1, 18, S('barrel', facing='up', open=False))
    t.set(4, DECK + 2, 18, S('barrel', facing='east', open=False))
    crate_pile(t, 6, 17, 7, 18, DECK + 1, rng, density=0.8, max_h=2)
    bed(t, 4, DECK + 1, 16, 'west', 'red')
    mod.lectern(t, 7, DECK + 1, 14, facing='south', book=True)
    t.set(6, DECK + 1, 15, stairs('spruce_stairs', 'north', 'top'))      # a stool, kicked over
    hang_lamp(t, 6, DECK + 3, 16, drop=1)
    for y in range(DECK + 1, TOP):
        t.set(5, y, 14, S('ladder', facing='south', waterlogged=False))
        for (px, pz) in ((4, 14), (6, 14), (5, 13)):
            if y > DECK + 4 or pz == 13:
                t.set(px, y, pz, rock(px, y, pz))
        if y > DECK + 4:
            t.set(5, y, 15, rock(5, y, 15))

    # ---- the lookout deck on the stack top
    for x in range(5, 17):
        for z in range(13, 21):
            t.set(x, TOP, z, 'minecraft:spruce_planks' if (x * 7 + z) % 5 else 'minecraft:oak_planks')
            for y in range(TOP + 1, TOP + 4):
                t.set(x, y, z, AIR)
    t.set(5, TOP, 14, trapdoor('spruce_trapdoor', 'south', 'top', False))
    for x in range(5, 17):
        for z in range(13, 21):
            edge = x in (5, 16) or z in (13, 20)
            if edge and not (x == 16 and z in (13, 14)) and not (x == 5 and z == 14):
                t.set(x, TOP + 1, z, S('spruce_fence', waterlogged=False))
    # a windbreak shelter in the south-west corner
    for (x, z) in ((5, 17), (8, 17), (5, 20), (8, 20)):
        post(t, x, z, TOP + 3, TOP + 1, log('stripped_spruce_log'))
    for x in range(4, 10):
        for z in range(16, 22):
            t.set(x, TOP + 4, z, slab('spruce_slab', 'bottom'))
    for z in range(18, 20):
        t.set(5, TOP + 2, z, 'minecraft:spruce_planks')
        t.set(5, TOP + 3, z, 'minecraft:spruce_planks')
    t.set(6, TOP + 1, 19, stairs('spruce_stairs', 'west'))
    t.set(6, TOP + 1, 18, stairs('spruce_stairs', 'west'))
    t.set(7, TOP + 1, 20, S('barrel', facing='up', open=False))
    hang_lamp(t, 7, TOP + 3, 18, drop=0)
    # the signal mast: a yard with a lantern, the bell, the spyglass, an unlit signal fire
    post(t, 13, 16, TOP + 6, TOP + 1, log('stripped_spruce_log'))
    for x in (12, 14):
        t.set(x, TOP + 5, 16, S('spruce_fence', waterlogged=False))
    t.set(14, TOP + 4, 16, lantern(hanging=True))
    t.set(12, TOP + 4, 16, chain('y'))
    t.set(12, TOP + 3, 16, lantern(hanging=True))
    t.set(11, TOP + 1, 14, S('bell', attachment='floor', facing='east', powered=False))
    t.set(15, TOP + 1, 19, S('spruce_fence', waterlogged=False))
    t.set(15, TOP + 2, 19, S('lightning_rod', facing='south', powered=False, waterlogged=False))
    t.set(10, TOP + 1, 19, 'minecraft:cobblestone')
    t.set(10, TOP + 2, 19, campfire('south', lit=False))

    # ---- steps cut up the stack's east flank, from the boardwalk to the deck
    steps = [(x, FL + (23 - z), z) for z in range(23, 12, -1) for x in (18, 19)]
    steps = [(18, FL + i, 23 - i) for i in range(11)]
    steps += [(17, DECK + 12, 13), (16, TOP, 13)]
    for (x, y, z) in steps:
        for xx in ((x, x + 1) if x == 18 else (x,)):
            for yy in range(hts.get((xx, z), -1) + 1, y):
                t.set(xx, yy, z, rock(xx, yy, z))
    stair_path(t, steps, 'cobblestone_stairs', 'minecraft:cobblestone', clear=3)
    twin = [(19, y, z) for (x, y, z) in steps if x == 18]
    stair_path(t, twin, 'cobblestone_stairs', 'minecraft:cobblestone', clear=3)
    t.set(16, TOP, 13, 'minecraft:spruce_planks')

    # ---- the boardwalk and its spurs (floor FL), on posts
    planks = [('minecraft:spruce_planks', 5), ('minecraft:oak_planks', 2), ('minecraft:dark_oak_planks', 1)]
    boards(t, 18, 22, 39, 24, FL, planks, rng, along='z', missing=0.04, sag=0.06)
    stilts(t, 18, 22, 39, 24, FL, log('spruce_log'), step=3, inner=False)
    boards(t, 23, 17, 25, 21, FL, planks, rng, along='x', sag=0.05)
    stilts(t, 23, 17, 25, 21, FL, log('spruce_log'), step=4, inner=False)
    boards(t, 36, 16, 38, 21, FL, planks, rng, along='x', sag=0.05)
    stilts(t, 36, 16, 38, 21, FL, log('spruce_log'), step=4, inner=False)
    boards(t, 27, 25, 30, 25, FL, planks, rng, along='z')
    for (x, z) in ((20, 22), (26, 24), (32, 22), (39, 24)):
        lamp_post(t, x, FL + 1, z, height=2)

    # ---- the boathouse: walkways round a water slip open to the north
    for x in range(22, 33):
        for z in range(6, 17):
            slip = 26 <= x <= 28 and z <= 13
            if not slip:
                t.set(x, FL, z, 'minecraft:spruce_planks' if (x + 2 * z) % 7 else 'minecraft:dark_oak_planks')
    for x in (22, 25, 29, 32):
        for z in (6, 9, 13, 16):
            if x in (22, 32) or z in (6, 16) or True:
                post(t, x, z, FL, 0, log('spruce_log'))
    for x in (22, 32):
        for z in range(6, 17):
            for y in range(FL + 1, FL + 6):
                t.set(x, y, z, 'minecraft:spruce_planks')
        for z in (6, 10, 13, 16):
            post(t, x, z, FL + 5, FL + 1, log('stripped_spruce_log'))
    for x in range(23, 32):
        for y in range(FL + 1, FL + 6):
            t.set(x, y, 16, 'minecraft:spruce_planks')
        t.set(x, FL + 5, 6, log('stripped_spruce_log', 'x'))
        for z in (10, 13):
            t.set(x, FL + 5, z, log('stripped_spruce_log', 'x'))
    for x in (25, 29):
        post(t, x, 16, FL + 5, FL + 1, log('stripped_spruce_log'))
    for x in range(23, 32):
        for z in range(7, 16):
            for y in range(FL + 1, FL + 5):
                t.set(x, y, z, AIR)
        for y in range(FL + 1, FL + 5):
            t.set(x, y, 6, AIR)
    t.set(24, FL + 1, 16, AIR)
    t.set(24, FL + 2, 16, AIR)
    door('spruce_door', 24, FL + 1, 16, t, facing='north', open_=True)
    for (x, z) in ((22, 8), (22, 12), (32, 8), (32, 11), (32, 14)):
        t.set(x, FL + 3, z, AIR)
    shapes.gable_roof(t, 22, 6, 32, 16, FL + 6, 'minecraft:spruce_stairs', ridge='z', overhang=1,
                      gable='minecraft:spruce_planks')
    skiff(t, 27, DECK, 7, 'south', length=6, wood_kind='oak', water=False, rng=rng,
          cargo=(None, 'oldrepublic:cargo_crate'))
    t.set(23, FL + 1, 7, 'minecraft:smithing_table')
    t.set(23, FL + 1, 9, S('grindstone', face='floor', facing='east'))
    t.set(23, FL + 1, 11, 'minecraft:cauldron')
    t.set(23, FL + 1, 12, S('barrel', facing='up', open=False))
    t.set(31, FL + 1, 7, S('barrel', facing='up', open=False))
    t.set(31, FL + 2, 7, S('barrel', facing='west', open=False))
    crate_pile(t, 30, 9, 31, 12, FL + 1, rng, density=0.6, max_h=2)
    t.set(31, FL + 1, 15, 'minecraft:crafting_table')
    for y in range(FL + 2, FL + 5):
        t.set(27, y, 10, chain('y'))
    t.set(27, FL + 1, 10, lantern(hanging=True))
    hang_lamp(t, 24, FL + 4, 13, drop=0)
    hang_lamp(t, 30, FL + 4, 10, drop=0)

    # ---- the bunkhouse and the storehouse
    bunk = Hut(35, FL, 8, 5, 8, h=4, walls='spruce', frame='stripped_spruce_log', roof='gable',
               roof_wood='dark_oak', door=('south', 2, 1), windows=[('east', 2), ('east', 5), ('west', 3)], rng=rng)
    bunk.build(t)
    stilts(t, 35, 8, 39, 15, FL - 1, log('spruce_log'), step=2, inner=False)
    door('spruce_door', 37, FL + 1, 15, t, facing='north', open_=False)
    t.set(37, FL + 3, 15, 'minecraft:spruce_planks')
    bed(t, 36, FL + 1, 9, 'north', 'brown')
    bed(t, 38, FL + 1, 9, 'north', 'gray')
    t.set(38, FL + 1, 13, S('smoker', facing='west', lit=True))
    table(t, 36, FL + 1, 12, kind='slab', on=candle(None, 1, lit=True))
    t.set(36, FL + 1, 13, stairs('spruce_stairs', 'south'))
    t.chest(38, FL + 1, 12, facing='west', loot=LOOT_SHACK)
    hang_lamp(t, 37, FL + 3, 11, drop=1)
    store = Hut(25, FL, 26, 8, 6, h=4, walls='spruce', frame='stripped_spruce_log', roof='shed', roof_wood='spruce',
                door=('north', 3, 2), windows=[('south', 2), ('south', 5)], rng=rng)
    store.build(t)
    stilts(t, 25, 26, 32, 31, FL - 1, log('spruce_log'), step=3, inner=True)
    crate_pile(t, 26, 27, 27, 30, FL + 1, rng, density=0.8, max_h=3)
    crate_pile(t, 30, 28, 31, 30, FL + 1, rng, density=0.8, max_h=3)
    t.set(31, FL + 1, 27, S('barrel', facing='up', open=False))
    hang_lamp(t, 28, FL + 3, 28, drop=1)

    # ---- the jetty (a step down from the boardwalk), its crane, a skiff
    for x in range(34, 37):
        t.set(x, FL, 25, stairs('spruce_stairs', 'north'))
    keep = {(34, z) for z in (26, 29, 32)} | {(36, z) for z in (26, 29, 32)}
    keep |= {(31, 35), (31, 38), (35, 38), (39, 35), (39, 38)}
    boards(t, 34, 26, 36, 34, DECK, planks, rng, along='x', missing=0.03, sag=0.05, keep=keep)
    boards(t, 31, 35, 39, 38, DECK, planks, rng, along='z', missing=0.03, sag=0.05, keep=keep)
    for (x, z) in sorted(keep):
        post(t, x, z, DECK, 0, log('spruce_log'))
    for (x, z) in ((34, 26), (36, 32), (31, 38), (39, 35)):
        t.set(x, DECK + 1, z, log('spruce_log'))
    lamp_post(t, 36, DECK + 1, 29, height=2)
    post(t, 38, 37, DECK, 0, log('spruce_log'))
    post(t, 38, 37, DECK + 6, DECK + 1, log('stripped_spruce_log'))
    for z in range(34, 37):
        t.set(38, DECK + 6, z, log('stripped_spruce_log', 'z'))
    t.set(38, DECK + 5, 36, stairs('spruce_stairs', 'south', 'top'))
    for y in range(DECK + 3, DECK + 6):
        t.set(38, y, 34, chain('y'))
    t.set(38, DECK + 2, 34, 'oldrepublic:cargo_crate')
    crate_pile(t, 32, 36, 34, 37, DECK + 1, rng, density=0.7, max_h=2)
    skiff(t, 38, DECK, 26, 'south', length=7, wood_kind='spruce', water=False, rng=rng,
          cargo=(S('barrel', facing='up', open=False), 'oldrepublic:cargo_crate'))

    plants(t, hts, rng, density=0.3)
    mod.light_fill(t, (4, DECK + 1, zn, 10, DECK + 7, zs))
    mod.light_fill(t, (23, FL + 1, 7, 31, FL + 4, 15))
    mod.light_fill(t, (36, FL + 1, 9, 38, FL + 4, 14))
    mod.light_fill(t, (26, FL + 1, 27, 31, FL + 4, 30))
    return t


# ================================================================ worldgen, loot

def write_loot():
    loot_table(LOOT, [
        ((3, 5), [item('gold_nugget', 8, 3, 8), item('oldrepublic:durasteel_plate', 5, 1, 2),
                  item('oldrepublic:alloy_plate', 3), item('oldrepublic:medpac', 3),
                  item('gunpowder', 4, 1, 3), item('iron_ingot', 5, 1, 3), item('copper_ingot', 4, 2, 4),
                  item('oldrepublic:credit_chit', 3, 2, 5), item('emerald', 2, 1, 2)]),
    ])
    loot_table(LOOT_SHACK, [
        ((2, 3), [item('bread', 5, 1, 3), item('paper', 4, 1, 3), item('iron_nugget', 4, 2, 6),
                  item('string', 3, 1, 3), item('leather', 2, 1, 2), item('gunpowder', 2, 1, 2),
                  item('oldrepublic:credit_chit', 2, 1, 3), empty(3)]),
    ])


def write_structure(variants):
    write_worldgen(NAME, variants, buried=DECK, spacing=48, separation=18, salt=960150,
                   biomes=['#minecraft:is_beach', 'minecraft:stony_shore'],
                   adapted=dict(yard_stand=DECK + 1, clear_from=SY, clear_inset=SX, support_depth=16, ramp_rings=0),
                   liquid='apply_waterlogging', max_distance=64)


BUILDERS = [('cove', build_cove), ('arch', build_arch)]


def main():
    only = only_names()
    write_loot()
    for name, fn in BUILDERS:
        if only and name not in only:
            continue
        t = fn()
        bad = no_air_below(t, SEA)
        if bad:
            raise SystemExit('%s: explicit air at or below the water line: %s' % (name, bad[:10]))
        save(t, 'wilds/%s/%s' % (NAME, name), views=('se', 'nw'), ground=DECK)
        report(t, 'wilds/%s/%s' % (NAME, name))
    write_structure([(name, 1) for name, _ in BUILDERS])


if __name__ == '__main__':
    main()
