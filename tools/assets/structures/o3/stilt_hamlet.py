"""wilds/stilt_hamlet: an abandoned swamp village on stilts (docs/MILESTONE_6.md 5.2, worker O3).

The story. Marsh folk lived here for generations: reed-cutters, eel fishers, weavers, keepers of a little
shrine built on an old Watch stone that was here before them. Then the crystal poachers came up the river
(their dig is wilds/poacher_dig): they needed hands, and they took them. The open cage still stands on the
dock, the headman's hut was burnt when he refused, the boats were stove in so no one could follow. The rest
of the folk fled into the reeds. The hamlet has been empty a season: boards rot and sag, a walk has fallen
into the water, roofs are open to the rain. But the candles at the shrine are still lit. Someone comes back.

Two start variants:
  reedholm   53 x 20 x 53  boardwalks from a central commons (fire pit, alarm bell) to seven huts, a fish
             house with drying racks, a lookout platform, the shrine on its stone footing to the west and
             the dock with the cage to the south.
  oakhollow  51 x 30 x 57  the hamlet built round a great old swamp oak on a mud island: a ring walk at its
             roots, huts on spurs, a treehouse lookout up in its crown, the shrine between its roots,
             racks, a dock with the cage.

Water (common.py): layer SEA = 5 is the water line and DECK = 6 the first free layer above it (start height
1 - DECK on WORLD_SURFACE_WG: swamps are flat at sea level, so the water line lands on the swamp's water
or a block or two above it on a mud bank). Boardwalks and floors are at FL = DECK + 1, two blocks above the
water. Every post is a log down to layer 0 (the pool element's support continues it to the swamp bottom);
nothing at or below SEA is explicit air; the structure applies waterlogging.

    python3 tools/assets/structures/o3/stilt_hamlet.py [--preview DIR] [--only reedholm,oakhollow]
"""
import math

from common import (AIR, S, B, Template, mod, Noise, mix, pick, wet, stairs, slab, trapdoor, log, lantern, chain,
                    campfire, candle, door, bed, wood, post, boards, railing, ladder, hang_lamp, lamp_post, crate_stack,
                    crate_pile, table, shelf, Hut, skiff, stilts, blob_heights, rock_columns, no_air_below, report,
                    save, only_names, write_worldgen, loot_table, item, empty, shapes)

NAME = 'stilt_hamlet'
SEA = 5
DECK = 6
FL = DECK + 1          # boardwalks and hut floors
LOOT = 'chests/wilds/stilt_hamlet'
LOOT_SHRINE = 'chests/wilds/stilt_hamlet_shrine'

WOODS = ('spruce', 'dark_oak', 'mangrove', 'oak')
ROT = [('minecraft:spruce_planks', 4), ('minecraft:mangrove_planks', 3), ('minecraft:dark_oak_planks', 2),
       ('minecraft:oak_planks', 2)]


def mud_walls(seed):
    return mix([('minecraft:packed_mud', 5), ('minecraft:mud_bricks', 3), ('minecraft:mangrove_planks', 2),
                ('minecraft:packed_mud', 2), ('minecraft:muddy_mangrove_roots', 1)], seed, 2.0)


def walk(t, rng, x0, z0, x1, z1, along, missing=0.04, sag=0.07, step=3, rails=False, y=FL):
    """A boardwalk over the rectangle at layer y on log posts (the boards run across `along`)."""
    boards(t, x0, z0, x1, z1, y, ROT, rng, along='z' if along == 'x' else 'x', missing=missing, sag=sag)
    cells = stilts(t, x0, z0, x1, z1, y, log('spruce_log'), step=step, inner=False)
    if rails:
        for (x, z) in cells:
            if rng.random() < 0.5:
                t.set(x, y + 1, z, S('spruce_fence', waterlogged=False))
    return cells


def moss_on(t, rng, x0, z0, x1, z1, y, chance=0.08):
    """Moss carpet and leaf litter on a deck: the walks have not been swept in a season."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            below = t.get(x, y, z)
            if below is None or t.get(x, y + 1, z) is not None or not below.endswith('_planks'):
                continue
            r = rng.random()
            if r < chance:
                t.set(x, y + 1, z, 'minecraft:moss_carpet')
            elif r < chance * 1.6:
                t.set(x, y + 1, z, S('leaf_litter', facing=rng.choice(('north', 'east', 'south', 'west')),
                                     segment_amount=rng.randint(1, 4)))


def house(t, rng, x0, z0, w, d, walls='spruce', roof='gable', roof_wood=None, door_=('south', 2, 1), windows=(),
          h=4, burnt=0.0, damage=0.0, door_block=True, seed=0):
    """A hut on stilts with its floor at FL. Returns the interior box (x0, y0, z0, x1, y1, z1)."""
    wk = walls if walls != 'mud' else 'mangrove'
    hut = Hut(x0, FL, z0, w, d, h=h, walls=wk, frame='stripped_%s_log' % ('spruce' if wk == 'mangrove' else wk)
              if wk != 'mangrove' else 'stripped_mangrove_log', floor=wk, roof=roof, roof_wood=roof_wood or wk,
              door=door_, windows=windows, rng=rng, burnt=burnt, roof_damage=damage,
              wall_mix=mud_walls(seed) if walls == 'mud' else None)
    box = hut.build(t)
    stilts(t, x0, z0, x0 + w - 1, z0 + d - 1, FL - 1, log('spruce_log' if wk != 'mangrove' else 'mangrove_log'),
           step=3, inner=True)
    if roof == 'thatch':
        # a season of rain: the thatch has gone green in patches
        for x in range(x0 - 1, x0 + w + 1):
            for z in range(z0 - 1, z0 + d + 1):
                for y in range(FL + h + 1, t.sy):
                    st = t.get(x, y, z)
                    if not st or 'hay_block' not in st:
                        continue
                    r = rng.random()
                    if r < 0.22:
                        t.set(x, y, z, 'minecraft:moss_block')
                    if t.get(x, y + 1, z) is None and rng.random() < 0.18 and y + 1 < t.sy:
                        t.set(x, y + 1, z, 'minecraft:moss_carpet')
    side, off, width = door_
    if door_block and width == 1:
        cells = hut.opening(side, off, 1, FL + 1, 1)
        (x, y, z) = cells[0]
        facing = {'south': 'north', 'north': 'south', 'east': 'west', 'west': 'east'}[side]
        door('%s_door' % wk, x, y, z, t, facing=facing, open_=rng.random() < 0.6)
        t.set(x, y + 2, z, wood(wk)['planks'])
    # vines creeping up the walls
    for _ in range(int(w * d * 0.15)):
        sidev = rng.choice(('north', 'south', 'east', 'west'))
        if sidev in ('north', 'south'):
            vx = rng.randint(x0 + 1, x0 + w - 2)
            vz = z0 - 1 if sidev == 'north' else z0 + d
            prop = {'south': True} if sidev == 'north' else {'north': True}
        else:
            vz = rng.randint(z0 + 1, z0 + d - 2)
            vx = x0 - 1 if sidev == 'west' else x0 + w
            prop = {'east': True} if sidev == 'west' else {'west': True}
        top_y = FL + h - rng.randint(0, 1)
        for vy in range(top_y, top_y - rng.randint(2, 4), -1):
            if t.get(vx, vy, vz) is None:
                p = dict(north=False, south=False, east=False, west=False, up=False)
                p.update(prop)
                t.set(vx, vy, vz, S('vine', **p))
    return box


def rack(t, x0, z0, x1, z1, y, rng):
    """A drying rack: fence posts, a log rail, strands hanging from it, a few dried bundles on top."""
    along_x = abs(x1 - x0) >= abs(z1 - z0)
    for (x, z) in ((x0, z0), (x1, z1)):
        t.set(x, y, z, S('spruce_fence', waterlogged=False))
        t.set(x, y + 1, z, S('spruce_fence', waterlogged=False))
    if along_x:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            t.set(x, y + 2, z0, log('stripped_spruce_log', 'x'))
            if min(x0, x1) < x < max(x0, x1) and rng.random() < 0.7:
                t.set(x, y + 1, z0, 'minecraft:hanging_roots')
            if rng.random() < 0.3:
                t.set(x, y + 3, z0, 'minecraft:dried_kelp_block')
    else:
        for z in range(min(z0, z1), max(z0, z1) + 1):
            t.set(x0, y + 2, z, log('stripped_spruce_log', 'z'))
            if min(z0, z1) < z < max(z0, z1) and rng.random() < 0.7:
                t.set(x0, y + 1, z, 'minecraft:hanging_roots')
            if rng.random() < 0.3:
                t.set(x0, y + 3, z, 'minecraft:dried_kelp_block')


def cage(t, x0, z0, y, open_side='south'):
    """The poachers' cage: iron bars on three sides and a roof of bars, the fourth side's door hanging open."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            edge = x in (x0, x0 + 2) or z in (z0, z0 + 2)
            for yy in (y, y + 1):
                if edge:
                    t.set(x, yy, z, S('iron_bars', waterlogged=False))
                else:
                    t.set(x, yy, z, AIR)
            t.set(x, y + 2, z, 'minecraft:spruce_planks' if edge else 'minecraft:spruce_slab[type=bottom,waterlogged=false]')
    # the door side: the middle bars out, an iron door swung open
    dx, dz = {'south': (x0 + 1, z0 + 2), 'north': (x0 + 1, z0), 'west': (x0, z0 + 1), 'east': (x0 + 2, z0 + 1)}[open_side]
    facing = B.opposite(open_side)
    t.set(dx, y, dz, S('iron_door', facing=facing, half='lower', hinge='left', open=True, powered=False))
    t.set(dx, y + 1, dz, S('iron_door', facing=facing, half='upper', hinge='left', open=True, powered=False))


def shrine(t, rng, x0, z0, burning=True):
    """The shrine on its Watch stone footing: a 7 x 7 stone platform on stone piers, an open pavilion over the
    old stone, a kyber cluster on it, candles still lit, a soul lantern, offerings in pots and the offering box."""
    stone = mix([('minecraft:mossy_stone_bricks', 4), ('minecraft:stone_bricks', 3),
                 ('minecraft:cracked_stone_bricks', 2), ('oldrepublic:watch_stone', 2)], 3301, 2.0)
    x1, z1 = x0 + 6, z0 + 6
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, FL, z, stone(x, FL, z))
            corner = x in (x0, x1) and z in (z0, z1)
            if corner or (x in (x0, x1) and z == z0 + 3) or (z in (z0, z1) and x == x0 + 3):
                for y in range(0, FL):
                    t.set(x, y, z, stone(x, y, z))
    cx, cz = x0 + 3, z0 + 3
    # the old stone and what sits on it
    t.set(cx, FL + 1, cz, 'oldrepublic:watch_stone')
    t.set(cx, FL + 2, cz, S('oldrepublic:kyber_cluster', facing='up'))
    for (x, z, n) in ((cx - 1, cz - 1, 3), (cx + 1, cz - 1, 2), (cx - 1, cz + 1, 1), (cx + 1, cz + 1, 4),
                      (cx, cz - 2, 2), (cx - 2, cz + 1, 1)):
        t.set(x, FL + 1, z, candle(rng.choice(('white', 'light_gray', None)), n, lit=burning))
    t.set(cx + 2, FL + 1, cz - 1, 'minecraft:potted_blue_orchid')
    t.set(cx - 2, FL + 1, cz - 1, 'minecraft:potted_fern')
    t.set(cx + 1, FL + 1, cz + 2, S('decorated_pot', facing='north', cracked=False, waterlogged=False))
    t.chest(cx - 1, FL + 1, cz + 2, facing='north', loot=LOOT_SHRINE)
    # the pavilion: four posts, a low pyramid roof of dark oak
    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        post(t, x, z, FL + 4, FL + 1, log('stripped_dark_oak_log'))
    shapes.hip_roof(t, x0, z0, x1, z1, FL + 5, 'minecraft:dark_oak_stairs', body='minecraft:dark_oak_planks',
                    cap='minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if t.get(x, FL + 4, z) is None:
                t.set(x, FL + 4, z, 'minecraft:dark_oak_planks' if x in (x0 + 1, x1 - 1) or z in (z0 + 1, z1 - 1) else AIR)
    for x in range(x0 + 2, x1 - 1):
        for z in range(z0 + 2, z1 - 1):
            for y in range(FL + 1, FL + 4):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
    hang_lamp(t, cx, FL + 3, cz + 1, drop=1, soul=True)
    return (x0, FL, z0, x1, FL + 4, z1)


def commons(t, rng, x0, z0, x1, z1):
    """The central platform: a stone-lined fire pit gone cold, the alarm bell, benches, a stove-in skiff."""
    boards(t, x0, z0, x1, z1, FL, ROT, rng, along='x', sag=0.05)
    stilts(t, x0, z0, x1, z1, FL, log('spruce_log'), step=3, inner=True)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            t.set(x, FL, z, 'minecraft:cobblestone' if (x, z) != (cx, cz) else 'minecraft:packed_mud')
    t.set(cx, FL + 1, cz, campfire('north', lit=False))
    for (x, z, f) in ((cx - 2, cz, 'west'), (cx + 2, cz, 'east'), (cx, cz - 2, 'north')):
        t.set(x, FL + 1, z, stairs('spruce_stairs', f))
    t.set(cx + 2, FL + 1, cz + 1, stairs('spruce_stairs', 'east'))
    t.set(cx - 2, FL + 1, cz - 1, 'minecraft:cauldron')
    # the alarm bell on its gallows, near the north-east corner
    bx, bz = x1 - 1, z0 + 1
    for z in (bz - 1, bz + 1):
        post(t, bx, z, FL + 3, FL + 1, S('spruce_fence', waterlogged=False))
    for z in range(bz - 1, bz + 2):
        t.set(bx, FL + 4, z, log('stripped_spruce_log', 'z'))
    t.set(bx, FL + 3, bz, S('bell', attachment='ceiling', facing='east', powered=False))
    # a skiff dragged up onto the platform, its bottom stove in
    skiff(t, x0 + 2, FL + 1, z1 - 6, 'south', length=5, wood_kind='spruce', rng=rng)
    t.set(x0 + 2, FL, z1 - 4, None)          # the hole in its bottom
    t.set(x0 + 1, FL + 1, z1 - 4, AIR)       # and a side plank torn off
    return (cx, cz)


# ================================================================ variant reedholm

def build_reedholm():
    sx, sy, sz = 53, 20, 53
    t = Template(sx, sy, sz, seed=960152, name='reedholm')
    rng = t.rng

    # ---- the walks: four arms from the commons
    walk(t, rng, 8, 25, 21, 27, along='x', rails=True)               # west arm to the shrine
    walk(t, rng, 25, 8, 27, 21, along='z', rails=True)               # north arm to the lookout
    walk(t, rng, 31, 24, 35, 26, along='x', rails=True)              # east arm ...
    walk(t, rng, 41, 24, 44, 26, along='x', rails=True)
    walk(t, rng, 36, 24, 40, 26, along='x', missing=0.55, sag=0.2)   # ... where it fell in
    t.set(38, SEA, 23, log('spruce_log', 'x'))                       # the fallen post, floating on its side
    t.set(39, SEA, 23, log('spruce_log', 'x'))
    t.set(37, SEA, 27, log('spruce_log', 'z'))
    walk(t, rng, 26, 31, 28, 44, along='z', rails=True)              # south arm to the dock
    cx, cz = commons(t, rng, 22, 22, 30, 30)
    moss_on(t, rng, 8, 8, 44, 44, FL)

    # ---- the huts
    homes = []
    # west arm: the headman's hut (north side) and a weaver's (south side)
    b = house(t, rng, 9, 17, 7, 6, walls='spruce', roof='thatch', door_=('south', 3, 1),
              windows=[('west', 2), ('north', 3)], seed=11)
    walk(t, rng, 12, 23, 12, 24, along='z', missing=0, sag=0)
    homes.append(('headman', b))
    b = house(t, rng, 10, 30, 7, 6, walls='mud', roof='gable', roof_wood='spruce', door_=('north', 3, 1),
              windows=[('south', 3), ('west', 2)], seed=12, damage=0.15)
    walk(t, rng, 13, 28, 13, 29, along='z', missing=0, sag=0)
    homes.append(('weaver', b))
    # north arm: two homes
    b = house(t, rng, 17, 10, 7, 6, walls='dark_oak', roof='gable', door_=('east', 2, 1),
              windows=[('north', 3), ('west', 2)], seed=13)
    walk(t, rng, 24, 12, 24, 12, along='x', missing=0, sag=0)
    homes.append(('home', b))
    b = house(t, rng, 29, 12, 6, 6, walls='mangrove', roof='shed', door_=('west', 2, 1),
              windows=[('east', 2), ('south', 2)], seed=14, damage=0.3)
    walk(t, rng, 28, 14, 28, 14, along='x', missing=0, sag=0)
    homes.append(('home2', b))
    # east arm: the burnt hut (north side), a store hut (south side)
    b = house(t, rng, 35, 16, 7, 7, walls='spruce', roof='gable', roof_wood='dark_oak', door_=('south', 3, 1),
              windows=[('east', 3)], seed=15, burnt=0.45, damage=0.7, door_block=False)
    walk(t, rng, 38, 23, 38, 23, along='z', missing=0, sag=0)
    homes.append(('burnt', b))
    b = house(t, rng, 36, 29, 7, 6, walls='oak', roof='thatch', door_=('north', 3, 1), windows=[('east', 2)],
              seed=16)
    walk(t, rng, 39, 27, 39, 28, along='z', missing=0, sag=0)
    homes.append(('store', b))
    # south arm: a sagging hut
    b = house(t, rng, 18, 35, 7, 6, walls='mud', roof='thatch', door_=('east', 2, 1), windows=[('west', 2)],
              seed=17, damage=0.35)
    walk(t, rng, 25, 37, 25, 37, along='x', missing=0, sag=0)
    homes.append(('home3', b))

    # ---- the fish house at the end of the east arm: racks over the water, a smokehouse
    boards(t, 45, 20, 50, 30, FL, ROT, rng, along='x', missing=0.05, sag=0.08)
    stilts(t, 45, 20, 50, 30, FL, log('spruce_log'), step=3, inner=True)
    rack(t, 46, 21, 50, 21, FL + 1, rng)
    rack(t, 46, 29, 50, 29, FL + 1, rng)
    rack(t, 50, 23, 50, 27, FL + 1, rng)
    t.set(46, FL + 1, 24, S('smoker', facing='east', lit=False))
    t.set(46, FL + 1, 25, S('barrel', facing='up', open=False))
    t.set(46, FL + 2, 25, S('barrel', facing='up', open=False))
    t.set(46, FL + 1, 26, 'minecraft:composter')
    t.set(47, FL + 1, 27, 'minecraft:cobweb')
    # ---- the lookout at the end of the north arm: a raised platform with the reed-watch's stool
    boards(t, 22, 3, 30, 7, FL, ROT, rng, along='z', sag=0.05)
    stilts(t, 22, 3, 30, 7, FL, log('spruce_log'), step=4, inner=True)
    for x in range(22, 31):
        for z in (3,):
            t.set(x, FL + 1, z, S('spruce_fence', waterlogged=False))
    for z in range(3, 8):
        t.set(22, FL + 1, z, S('spruce_fence', waterlogged=False))
        t.set(30, FL + 1, z, S('spruce_fence', waterlogged=False))
    rack(t, 23, 5, 27, 5, FL + 1, rng)
    t.set(29, FL + 1, 5, stairs('spruce_stairs', 'south'))
    lamp_post(t, 29, FL + 1, 4, height=2)

    # ---- the shrine to the west, on its stone footing, reached by a narrow plank walk
    shrine(t, rng, 0, 23)
    walk(t, rng, 7, 26, 7, 26, along='x', missing=0, sag=0)

    # ---- the dock at the end of the south arm: the cage, a moored boat, a stove-in one
    boards(t, 20, 45, 34, 48, DECK, ROT, rng, along='z', missing=0.04, sag=0.06)
    stilts(t, 20, 45, 34, 48, DECK, log('spruce_log'), step=3, inner=False)
    for x in range(26, 29):
        t.set(x, FL, 45, stairs('spruce_stairs', 'north'))
    cage(t, 30, 45, DECK + 1, open_side='west')
    t.set(29, DECK + 1, 48, S('barrel', facing='up', open=False))
    for (x, z) in ((20, 45), (20, 48), (34, 48)):
        t.set(x, DECK + 1, z, log('spruce_log'))
    skiff(t, 23, DECK, 50, 'east', length=6, wood_kind='spruce', water=False, rng=rng)
    lamp_post(t, 21, DECK + 1, 47, height=2)
    for (x, z) in ((25, 47), (33, 46)):
        t.set(x, DECK + 1, z, 'oldrepublic:scorch_mark')

    # ---- furnish the huts
    furnish(t, rng, homes)
    mod.spot(t, 'HEART', cx, FL + 1, cz + 2)
    for (_, b) in homes:
        mod.light_fill(t, b)
    return t


def beam_lamp(t, x0, z0, x1, z1, y1):
    """A tie beam across the hut under its roof (at the top plate) and a lantern hanging from it."""
    zm = (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        t.set(x, y1 + 1, zm, log('stripped_spruce_log', 'x'))
    t.set((x0 + x1) // 2, y1, zm, lantern(hanging=True))


def furnish(t, rng, homes):
    for kind, (x0, y0, z0, x1, y1, z1) in homes:
        if kind in ('headman', 'home', 'home2', 'home3'):
            bed(t, x0, y0, z0, 'north', rng.choice(('brown', 'green', 'gray', 'lime')))
            if x1 - x0 >= 3:
                bed(t, x0 + 1, y0, z0, 'north', rng.choice(('brown', 'yellow', 'green')))
            table(t, x1, y0, z1, kind='slab', wood_kind='spruce',
                  on=candle(None, 1, lit=False) if rng.random() < 0.5 else 'minecraft:flower_pot')
            t.set(x1 - 1, y0, z1, stairs('spruce_stairs', 'west'))
            t.set(x1, y0, z0, S('barrel', facing='up', open=False))
            if kind == 'headman':
                t.chest(x0, y0, z1, facing='east', loot=LOOT)
                mod.lectern(t, x1, y0, z0 + 1, facing='west', book=False)
                shelf(t, x0, y0 + 1, z1, 'spruce_shelf', 'east')
            else:
                t.set(x0, y0, z1, S('furnace', facing='east', lit=False))
            if rng.random() < 0.5:
                t.set(x1, y1, z0, 'minecraft:cobweb')
            beam_lamp(t, x0, z0, x1, z1, y1)
        elif kind == 'weaver':
            t.set(x0, y0, z0, S('loom', facing='south'))
            t.set(x0 + 1, y0, z0, S('loom', facing='south'))
            t.set(x1, y0, z0, S('hay_block', axis='y'))
            t.set(x1, y0 + 1, z0, S('hay_block', axis='x'))
            t.set(x1, y0, z1, 'minecraft:white_wool')
            t.set(x0, y0, z1, S('barrel', facing='up', open=False))
            t.set(x0 + 1, y1, z1, 'minecraft:cobweb')
            beam_lamp(t, x0, z0, x1, z1, y1)
        elif kind == 'burnt':
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    r = rng.random()
                    if r < 0.25:
                        t.set(x, y0, z, 'oldrepublic:scorch_mark')
                    elif r < 0.35:
                        t.set(x, y0, z, 'oldrepublic:debris')
            t.set(x1, y0, z1, S('dark_oak_stairs', facing='north', half='top', shape='straight', waterlogged=False))
            t.set(x0, y0, z0, 'minecraft:coal_block')
        elif kind == 'store':
            crate_pile(t, x0, z0, x1, z0 + 1, y0, rng, density=0.7, max_h=2,
                       kinds=[(S('barrel', facing='up', open=False), 4), ('minecraft:hay_block', 2),
                              ('oldrepublic:cargo_crate', 1)])
            t.set(x1, y0, z1, 'minecraft:composter')
            t.set(x0, y0, z1, 'minecraft:cauldron')
            beam_lamp(t, x0, z0, x1, z1, y1)


# ================================================================ variant oakhollow

def great_oak(t, rng, cx, cz, base_y, height=14):
    """A great old swamp oak: a 2 x 2 trunk with root buttresses, three thick limbs, a broad crown of
    persistent leaves, vines hanging from it."""
    trunk = [(cx, cz), (cx + 1, cz), (cx, cz + 1), (cx + 1, cz + 1)]
    for (x, z) in trunk:
        for y in range(0, base_y + height):
            t.set(x, y, z, log('oak_log'))
    # root buttresses flaring out at the water line
    for (dx, dz, ax) in ((-1, 0, 'x'), (2, 1, 'x'), (0, -1, 'z'), (1, 2, 'z'), (-1, 1, 'x'),
                         (1, -1, 'z'), (0, 2, 'z')):
        x, z = cx + dx, cz + dz
        for y in range(0, base_y + rng.randint(2, 4)):
            t.set(x, y, z, S('oak_wood', axis='y'))
    for (dx, dz) in ((-2, 0), (3, 1), (0, -2), (1, 3), (-2, 2), (3, -1)):
        x, z = cx + dx, cz + dz
        for y in range(0, base_y + 2):
            t.set(x, y, z, 'minecraft:muddy_mangrove_roots' if y >= base_y else 'minecraft:mud')
        t.set(x, base_y + 2, z, S('mangrove_roots', waterlogged=False))
    top = base_y + height
    crown = []
    limbs = [(-7, -3, 3, 6), (7, -2, 2, 5), (1, 7, 4, 7), (-4, 5, 2, 4), (4, 4, 3, 3), (-1, -7, 3, 5)]
    for (lx, lz, rise, drop) in limbs:
        sx, sz = cx + (1 if lx > 0 else 0), cz + (1 if lz > 0 else 0)
        n = max(abs(lx), abs(lz))
        y0 = top - drop
        for i in range(1, n + 1):
            x = sx + int(round(lx * i / float(n)))
            z = sz + int(round(lz * i / float(n)))
            y = y0 + int(round(rise * i / float(n)))
            ax = 'x' if abs(lx) >= abs(lz) else 'z'
            t.set(x, y, z, log('oak_log', ax))
            crown.append((x, y, z))
    crown.append((cx, top, cz))
    leaves = S('oak_leaves', distance=1, persistent=True, waterlogged=False)
    dark = S('dark_oak_leaves', distance=1, persistent=True, waterlogged=False)
    n = Noise(4401, 2.0)
    for (x, y, z) in crown:
        r = 3.8 if (x, y, z) != (cx, top, cz) else 4.6
        for xx in range(int(x - r - 1), int(x + r + 2)):
            for zz in range(int(z - r - 1), int(z + r + 2)):
                for yy in range(y - 1, y + 3):
                    d = math.sqrt((xx - x) ** 2 + (zz - z) ** 2 + ((yy - y - 0.5) * 1.6) ** 2)
                    if d < r + 0.6 * (n.at(xx, yy, zz) - 0.5) and t.inside(xx, yy, zz) and t.get(xx, yy, zz) is None:
                        t.set(xx, yy, zz, leaves if n.at(xx + 7, yy, zz) > 0.3 else dark)
    # vines hanging from the crown's underside
    for (x, y, z), s in list(t.cells.items()):
        if 'leaves' not in s or rng.random() > 0.18:
            continue
        for (dx, dz, face) in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
            vx, vz = x + dx, z + dz
            if t.get(vx, y, vz) is None and t.inside(vx, y, vz):
                p = dict(north=False, south=False, east=False, west=False, up=False)
                p[face] = True
                for vy in range(y, max(FL + 2, y - rng.randint(2, 6)), -1):
                    if t.get(vx, vy, vz) is not None:
                        break
                    t.set(vx, vy, vz, S('vine', **p))
                break
    return top


def build_oakhollow():
    sx, sy, sz = 51, 30, 57
    t = Template(sx, sy, sz, seed=960153, name='oakhollow')
    rng = t.rng
    cx, cz = 24, 26

    # ---- the island the oak grows on: a mound of mud the folk banked up, two blocks above the water
    n = Noise(4402, 3.0)
    isle = {}
    for x in range(sx):
        for z in range(sz):
            r = math.hypot(x - cx - 0.5, z - cz - 0.5) / 9.5 + 0.12 * (n.at(x, 0, z) - 0.5)
            if r < 0.80:
                isle[(x, z)] = DECK + 1
            elif r < 0.90:
                isle[(x, z)] = DECK
            elif r < 1.0:
                isle[(x, z)] = SEA - 1
    soil = mix([('minecraft:mud', 4), ('minecraft:dirt', 2), ('minecraft:packed_mud', 1)], 4403, 2.0)

    def top(x, y, z):
        if y <= SEA - 1:
            return 'minecraft:mud'
        return pick(rng, [('minecraft:grass_block', 5), ('minecraft:mud', 2), ('minecraft:moss_block', 2),
                          ('minecraft:podzol', 1)])
    rock_columns(t, isle, soil, top=top)
    for (x, z), h in sorted(isle.items()):
        if h >= DECK and t.get(x, h + 1, z) is None and rng.random() < 0.45:
            t.set(x, h + 1, z, pick(rng, [(S('short_grass'), 5), (S('fern'), 3), ('minecraft:blue_orchid', 1),
                                          ('minecraft:brown_mushroom', 1)]))
    oak_top = great_oak(t, rng, cx, cz, FL, height=15)

    # ---- the ring walk round the island (floor FL), on posts
    ring = (cx - 9, cz - 9, cx + 10, cz + 10)
    x0, z0, x1, z1 = ring
    walk(t, rng, x0, z0, x1, z0 + 2, along='x', rails=True)
    walk(t, rng, x0, z1 - 2, x1, z1, along='x', rails=True)
    walk(t, rng, x0, z0 + 3, x0 + 2, z1 - 3, along='z', rails=True)
    walk(t, rng, x1 - 2, z0 + 3, x1, z1 - 3, along='z', missing=0.3, sag=0.15)   # the side that is falling in
    t.set(x1 - 1, SEA, cz, log('spruce_log', 'z'))
    t.set(x1 - 1, SEA, cz + 1, log('spruce_log', 'z'))
    moss_on(t, rng, x0, z0, x1, z1, FL)

    # ---- the shrine between the oak's roots: candles and the old stone on the island, south-west of the trunk
    sx0, sz0 = cx - 4, cz + 3
    t.set(sx0, FL + 1, sz0, 'oldrepublic:watch_stone')
    t.set(sx0, FL + 2, sz0, S('oldrepublic:kyber_cluster', facing='up'))
    for y in range(0, FL + 1):
        t.set(sx0, y, sz0, 'minecraft:mossy_stone_bricks')
    for (dx, dz, n) in ((1, 0, 3), (-1, 0, 2), (0, 1, 1), (1, 1, 2), (-1, -1, 1)):
        x, z = sx0 + dx, sz0 + dz
        g = isle.get((x, z), DECK)
        t.set(x, g, z, 'minecraft:mossy_stone_bricks')
        t.set(x, g + 1, z, candle(rng.choice(('white', None)), n, lit=True))
    t.set(sx0 + 1, isle.get((sx0 + 1, sz0 - 1), DECK) + 1, sz0 - 1, 'minecraft:potted_blue_orchid')
    t.chest(sx0 - 1, isle.get((sx0 - 1, sz0 + 1), DECK) + 1, sz0 + 1, facing='east', loot=LOOT_SHRINE)
    lamp_post(t, sx0 - 1, isle.get((sx0 - 1, sz0 - 1), DECK) + 1, sz0 - 1, height=1, soul=True)

    # ---- the treehouse lookout up in the crown, reached by a ladder up the trunk's east side
    ty = oak_top - 8
    hx0, hz0 = cx - 2, cz - 2
    for x in range(hx0, hx0 + 6):
        for z in range(hz0, hz0 + 6):
            if (x, z) in ((cx, cz), (cx + 1, cz), (cx, cz + 1), (cx + 1, cz + 1)):
                continue
            t.set(x, ty, z, 'minecraft:spruce_planks' if (x + z) % 4 else 'minecraft:oak_planks')
            for y in range(ty + 1, ty + 3):
                if t.get(x, y, z) is not None and 'log' not in t.get(x, y, z):
                    t.set(x, y, z, AIR)
                elif t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
            if x in (hx0, hx0 + 5) or z in (hz0, hz0 + 5):
                t.set(x, ty + 1, z, S('spruce_fence', waterlogged=False))
    for (x, z) in ((hx0, hz0), (hx0 + 5, hz0), (hx0, hz0 + 5), (hx0 + 5, hz0 + 5)):
        if t.get(x, ty - 1, z) is None:
            t.set(x, ty - 1, z, stairs('spruce_stairs', 'north' if z == hz0 else 'south', 'top'))
    for y in range(isle.get((cx + 2, cz), FL) + 1, ty):
        t.set(cx + 2, y, cz, S('ladder', facing='east', waterlogged=False))
    t.set(cx + 2, ty, cz, trapdoor('spruce_trapdoor', 'east', 'top', True))
    t.set(cx + 2, ty + 1, cz, AIR)
    t.set(hx0 + 1, ty + 1, hz0 + 1, S('barrel', facing='up', open=False))
    t.set(hx0 + 4, ty + 1, hz0 + 4, stairs('spruce_stairs', 'south'))
    t.set(hx0 + 4, ty + 1, hz0 + 1, lantern())
    t.set(hx0 + 1, ty + 1, hz0 + 4, S('bell', attachment='floor', facing='north', powered=False))

    # ---- huts on spurs off the ring
    homes = []
    b = house(t, rng, x0 - 1, z0 - 10, 7, 7, walls='spruce', roof='thatch', door_=('south', 3, 1),
              windows=[('west', 3), ('north', 3)], seed=21)
    walk(t, rng, x0 + 2, z0 - 3, x0 + 2, z0 - 1, along='z', missing=0, sag=0)
    homes.append(('headman', b))
    b = house(t, rng, cx - 1, z0 - 9, 6, 6, walls='mud', roof='gable', roof_wood='dark_oak', door_=('south', 2, 1),
              windows=[('east', 2)], seed=22, damage=0.2)
    walk(t, rng, cx + 1, z0 - 3, cx + 1, z0 - 1, along='z', missing=0, sag=0)
    homes.append(('weaver', b))
    b = house(t, rng, x1 + 2, z0 + 1, 6, 7, walls='mangrove', roof='shed', door_=('west', 3, 1),
              windows=[('east', 3)], seed=23, burnt=0.4, damage=0.65, door_block=False)
    walk(t, rng, x1 + 1, z0 + 4, x1 + 1, z0 + 4, along='x', missing=0, sag=0)
    homes.append(('burnt', b))
    b = house(t, rng, x1 + 2, cz + 4, 6, 6, walls='oak', roof='thatch', door_=('west', 2, 1),
              windows=[('south', 2)], seed=24)
    walk(t, rng, x1 + 1, cz + 6, x1 + 1, cz + 6, along='x', missing=0, sag=0)
    homes.append(('store', b))
    b = house(t, rng, x0 - 9, cz - 3, 7, 6, walls='dark_oak', roof='gable', door_=('east', 2, 1),
              windows=[('west', 2), ('north', 3)], seed=25)
    walk(t, rng, x0 - 2, cz - 1, x0 - 1, cz - 1, along='x', missing=0, sag=0)
    homes.append(('home', b))
    b = house(t, rng, x0 - 1, z1 + 3, 7, 6, walls='mud', roof='thatch', door_=('north', 3, 1),
              windows=[('south', 3)], seed=26, damage=0.3)
    walk(t, rng, x0 + 2, z1 + 1, x0 + 2, z1 + 2, along='z', missing=0, sag=0)
    homes.append(('home3', b))

    # ---- racks on the north-east corner platform, the dock with the cage to the south
    boards(t, x1 + 1, z0 - 7, x1 + 8, z0 - 1, FL, ROT, rng, along='x', sag=0.08, missing=0.05)
    stilts(t, x1 + 1, z0 - 7, x1 + 8, z0 - 1, FL, log('spruce_log'), step=3, inner=True)
    walk(t, rng, x1 - 1, z0 - 2, x1, z0 - 1, along='x', missing=0, sag=0)
    rack(t, x1 + 2, z0 - 6, x1 + 7, z0 - 6, FL + 1, rng)
    rack(t, x1 + 2, z0 - 3, x1 + 7, z0 - 3, FL + 1, rng)
    t.set(x1 + 8, FL + 1, z0 - 1, S('smoker', facing='west', lit=False))
    dx0 = cx - 2
    walk(t, rng, dx0, z1 + 1, dx0 + 2, z1 + 9, along='z', rails=True)
    boards(t, dx0 - 5, z1 + 10, dx0 + 8, z1 + 13, DECK, ROT, rng, along='z', missing=0.04, sag=0.06)
    stilts(t, dx0 - 5, z1 + 10, dx0 + 8, z1 + 13, DECK, log('spruce_log'), step=3, inner=False)
    for x in range(dx0, dx0 + 3):
        t.set(x, FL, z1 + 10, stairs('spruce_stairs', 'north'))
    cage(t, dx0 + 4, z1 + 10, DECK + 1, open_side='south')
    skiff(t, dx0 - 3, DECK, z1 + 15, 'east', length=6, wood_kind='spruce', rng=rng)
    lamp_post(t, dx0 - 5, DECK + 1, z1 + 13, height=2)
    t.set(dx0 + 1, DECK + 1, z1 + 12, 'oldrepublic:scorch_mark')
    t.set(dx0 + 8, DECK + 1, z1 + 13, S('barrel', facing='up', open=False))

    furnish(t, rng, homes)
    hx, hz = cx - 2, cz + 1
    mod.spot(t, 'HEART', hx, isle.get((hx, hz), DECK) + 1, hz)
    for (_, b) in homes:
        mod.light_fill(t, b)
    return t


# ================================================================ worldgen, loot

def write_loot():
    loot_table(LOOT, [
        ((2, 4), [item('string', 4, 1, 4), item('leather', 3, 1, 2), item('charcoal', 3, 1, 3),
                  item('paper', 2, 1, 2), item('bread', 4, 1, 3), item('iron_nugget', 3, 2, 5),
                  item('bone', 2, 1, 2), item('oldrepublic:peat', 2, 1, 3), item('emerald', 1, 1, 1)]),
    ])
    loot_table(LOOT_SHRINE, [
        ((1, 3), [item('candle', 5, 1, 3), item('paper', 3, 1, 2), item('gold_nugget', 3, 1, 4),
                  item('amethyst_shard', 2, 1, 2), item('oldrepublic:lumen_thread', 1, 1, 1), empty(2)]),
    ])


BUILDERS = [('reedholm', build_reedholm), ('oakhollow', build_oakhollow)]


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
    write_worldgen(NAME, [(name, 1) for name, _ in BUILDERS], buried=DECK, spacing=48, separation=18, salt=960151,
                   biomes=['minecraft:swamp', 'minecraft:mangrove_swamp'],
                   adapted=dict(yard_stand=FL, clear_from=26, clear_inset=60, support_depth=12, ramp_rings=0),
                   liquid='apply_waterlogging', max_distance=64)


if __name__ == '__main__':
    main()
