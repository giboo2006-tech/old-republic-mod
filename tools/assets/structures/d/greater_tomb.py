#!/usr/bin/env python3
"""The greater tomb on Varkhast (docs/MILESTONE_6.md 5.8): a worldgen jigsaw of four pieces.

  greater_tomb/gate          35 x 22 x 35, new: the Imperial dig at a sunken Sith forecourt. A grand stair
                             goes down from the dig camp into the excavated forecourt; at its south end the
                             tomb's portal, whose door slab lies broken outward on the paving, opens on the
                             descent (jigsaw tomb_down at the bottom of the portal).
  greater_tomb/descent       11 x 18 x 27, kept: re-skinned; the dig's power cable and lamps lead down
  greater_tomb/urn_vault     21 x 12 x 21, kept with its mechanism exact (urn keeper, urn offsets, seals)
  greater_tomb/sealed_crypt  21 x 12 x 21, kept: the crypt with the relic

The structure set keeps its salt (905123), spacing 36 and separation 14 and now uses
oldrepublic:clear_of_origin (min_distance 256). The gate is buried 6 (start_height -5).

Usage: python3 tools/assets/structures/d/greater_tomb.py      (D_PREVIEW=<folder> renders previews)
"""
import math

from common import (S, AIR, B, W, Noise, DTemplate, load, mechanism, protect_routes, check_reach, dark_floors,
                    mixer, hash01, save, fill_light, stairs, slab, wall, fence, bars, chain, lantern, candle, cobweb, skull,
                    light, log, trapdoor, campfire, is_air_set, mod, shapes, nature, SIDES)
import tomb_kit as K

TOMB_WALL = [('or:tomb_bricks', 6), ('or:tomb_stone', 2), ('or:tomb_stone_cracked', 2),
             ('polished_blackstone_bricks', 1)]
TOMB_FLOOR = [('or:tomb_stone', 4), ('polished_blackstone', 2), ('or:tomb_stone_cracked', 2),
              ('cracked_polished_blackstone_bricks', 1)]
TOMB_CEIL = [('or:tomb_bricks', 4), ('or:tomb_stone', 3), ('or:tomb_stone_cracked', 2)]
TOMB_BASE = [('polished_blackstone_bricks', 3), ('cracked_polished_blackstone_bricks', 1)]
CARVED = 'oldrepublic:tomb_stone_carved'
PILLAR = S('oldrepublic:tomb_pillar', axis='y')
RED = 'oldrepublic:light_panel_red'
URN = 'oldrepublic:funerary_urn'

K.niche_ok('oldrepublic:tomb_stone', 'oldrepublic:tomb_stone_cracked', 'oldrepublic:tomb_bricks',
           'polished_blackstone_bricks', 'oldrepublic:tomb_stone_carved')


def tomb_style(seed, rib_axis='z'):
    return K.Style(seed, TOMB_WALL, TOMB_FLOOR, TOMB_CEIL, base=TOMB_BASE, cornice=CARVED, pilaster=PILLAR,
                   rib='polished_blackstone_bricks',
                   replace=['or:tomb_stone', 'or:tomb_stone_cracked', 'or:tomb_stone_carved'],
                   pilaster_every=4, rib_every=4, rib_axis=rib_axis)


def openings(t):
    """Jigsaws, seals and plating count as passable for the walk checks."""
    return {p for p, s in t.cells.items()
            if B.name_of(s) in ('minecraft:jigsaw', 'oldrepublic:puzzle_seal', 'oldrepublic:sealed_plating')}


def freeze_common(t, tid):
    """Mechanism cells, jigsaws, block entities and anchors (with the head cell of feet anchors)."""
    t.freeze(mechanism(tid))
    t.freeze([p for p, s in t.cells.items() if B.name_of(s) == 'minecraft:jigsaw'])
    t.freeze(list(t.nbt))
    for cells in t.anchors.values():
        for c in cells:
            p = tuple(c)
            t.freeze([p])
            if is_air_set(t, p):
                t.freeze([(p[0], p[1] + 1, p[2])])
    for p, s in list(t.cells.items()):
        if B.name_of(s) == 'oldrepublic:site_spawner':
            t.freeze([(p[0], p[1] + 1, p[2])])


def red_base(t, region, every=4, seed=0):
    """Red light from below: every `every` cells of a wall's base row (the course at a standing player's
    feet) becomes a recessed red panel."""
    n = 0
    for (x, y, z) in K.room_cells(t, region):
        if not K.solid(t, (x, y - 1, z)):
            continue
        for d, dx, dz in SIDES:
            w = (x + dx, y, z + dz)
            k = w[0] if dz else w[2]
            if k % every or not K.solid(t, w) or t.is_frozen(*w):
                continue
            if B.name_of(t.cells[w]) not in ('polished_blackstone_bricks', 'cracked_polished_blackstone_bricks',
                                             'minecraft:polished_blackstone_bricks',
                                             'minecraft:cracked_polished_blackstone_bricks'):
                continue
            t.set(*w, RED)
            n += 1
    return n


def lights_ok(t, region, label, level=8, strength=11):
    return fill_light(t, region, level=level, strength=strength)


# ---------------------------------------------------------------- descent

def build_descent():
    tid = 'greater_tomb/descent'
    t = load(tid, seed=905201)
    freeze_common(t, tid)
    # The stairway's treads stay exactly as they are (the route's walking surface).
    t.freeze([p for p, s in t.cells.items() if 'stairs' in s or 'slab' in s])
    opens = openings(t)
    top, bottom = (5, 15, 1), (5, 2, 25)
    reach0, keys = protect_routes(t, [top], opens, targets=[bottom])
    region = (0, 0, 0, t.sx - 1, t.sy - 1, t.sz - 1)

    K.dress(t, tomb_style(905202), region)
    red_base(t, region, every=4)

    # The Imperial dig's power cable runs down the east wall's foot, with work lamps every few steps.
    for z in range(1, 26):
        for y in range(2, 17):
            p = (7, y, z)
            if is_air_set(t, p) and K.solid(t, (7, y - 1, z)) and t.free(*p):
                if z % 6 == 3:
                    t.set(*p, 'oldrepublic:light_panel_amber' if z < 20 else 'oldrepublic:light_panel')
                elif z in (24, 25):
                    pass
                break
    # Niches with urns and stubs of candles along the level corridor, a skull in one.
    def niche_fill(x, y, z, facing):
        h = hash01(77, x, y, z)
        if h < 0.45:
            return URN
        if h < 0.75:
            return candle(1 + int(h * 10) % 3, lit=True, color='red' if h < 0.6 else None)
        if h < 0.85:
            return skull(0)
        return 'minecraft:bone_block' if h < 0.92 else None
    K.niches(t, (3, 3, 13, 7, 4, 25), niche_fill, every=3, seed=905203, height=2)
    # Soul lanterns on chains over the corridor; cobwebs in the corners.
    for z in (16, 22):
        if t.free(5, 6, z) and t.free(5, 5, z):
            K.hang(t, 5, 6, z, 1, lantern(soul=True, hanging=True))
    K.cobwebs(t, region, chance=0.4, seed=905204)
    # What the dig left behind: a fallen lamp stand, scattered rubble and scorch where something burned.
    K.litter(t, (3, 2, 2, 7, 16, 25), [('oldrepublic:debris', 5), ('oldrepublic:scorch_mark', 2)], 0.16,
             seed=905205, keep_free=False)
    lights_ok(t, (3, 2, 0, 7, 16, 26), tid)
    check_reach(t, [top], list(keys) + [bottom], opens, tid)
    return save(t, tid, 'greater_tomb_descent', cut=[9], plans=[2, 8, 15])


# ---------------------------------------------------------------- urn vault

def build_vault():
    tid = 'greater_tomb/urn_vault'
    t = load(tid, seed=905301)
    freeze_common(t, tid)
    # The urn pedestals (carved stone under each echo urn) and the keeper's wall stay exactly as they are.
    for (x, y, z), n in mechanism(tid).items():
        if n == 'oldrepublic:echo_urn':
            t.freeze([(x, y - 1, z)])
    t.freeze_box(2, 2, 14, 7, 4, 14)
    opens = openings(t)
    entry, door = (10, 2, 1), (10, 2, 18)
    reach0, keys = protect_routes(t, [entry], opens, targets=[door])
    region = (0, 0, 0, t.sx - 1, t.sy - 1, t.sz - 1)
    K.dress(t, tomb_style(905302), region)
    red_base(t, region, every=4)
    K.corbels(t, (2, 6, 2, 18, 9, 18), 'polished_blackstone_brick_stairs', every=3)

    # Free-standing columns get a tomb pillar shaft with a carved base and capital.
    for (x, z) in ((6, 6), (14, 6), (14, 14)):
        ys = [y for y in range(2, 11) if K.solid(t, (x, y, z)) and not t.is_frozen(x, y, z)]
        if ys:
            for y in ys:
                t.set(x, y, z, PILLAR)
            t.set(x, ys[0], z, 'polished_blackstone_bricks')
            t.set(x, ys[-1], z, CARVED)

    # Braziers of red light at the room's corners and soul lanterns over the urns.
    for (x, z) in ((3, 3), (17, 3), (17, 17)):
        if t.free(x, 2, z):
            t.set(x, 2, z, campfire(lit=True, soul=False))
    for (x, z) in ((4, 4), (16, 4), (16, 11), (11, 8)):
        top = max(y for y in range(2, 11) if is_air_set(t, (x, y, z)))
        if top >= 7 and t.free(x, top, z):
            K.hang(t, x, top, z, 2, lantern(soul=True, hanging=True))
    def niche_fill(x, y, z, facing):
        h = hash01(91, x, y, z)
        return URN if h < 0.5 else candle(2, lit=True, color='red') if h < 0.75 else skull(0) if h < 0.82 else None
    K.niches(t, (2, 3, 2, 18, 4, 18), niche_fill, every=4, seed=905303, height=2)
    K.cobwebs(t, region, chance=0.3, seed=905304)
    K.litter(t, (2, 2, 2, 18, 2, 18), [('oldrepublic:debris', 4), ('oldrepublic:scorch_mark', 1)], 0.08,
             seed=905305, keep_free=False)
    lights_ok(t, (2, 2, 1, 18, 9, 19), tid)
    check_reach(t, [entry], list(keys) + [door], opens, tid)
    return save(t, tid, 'greater_tomb_urn_vault', cut=[6], plans=[2, 3])


# ---------------------------------------------------------------- sealed crypt

def build_crypt():
    tid = 'greater_tomb/sealed_crypt'
    t = load(tid, seed=905401)
    freeze_common(t, tid)
    # The sarcophagus and the dais with its steps stay as they are; the relic candle is an anchor.
    t.freeze_box(6, 2, 13, 14, 4, 18)
    opens = openings(t)
    entry = (10, 2, 1)
    reach0, keys = protect_routes(t, [entry], opens)
    region = (0, 0, 0, t.sx - 1, t.sy - 1, t.sz - 1)
    K.dress(t, tomb_style(905402), region)
    red_base(t, region, every=3)
    K.corbels(t, (2, 6, 3, 18, 10, 18), 'polished_blackstone_brick_stairs', every=2)
    for (x, z) in ((4, 15), (16, 5), (4, 5)):
        if t.free(x, 2, z):
            t.set(x, 2, z, campfire(lit=True, soul=True))
    for (x, z) in ((10, 6), (6, 10), (14, 10)):
        top = max(y for y in range(2, 11) if is_air_set(t, (x, y, z)))
        if t.free(x, top, z):
            K.hang(t, x, top, z, 2, lantern(soul=True, hanging=True))
    K.cobwebs(t, region, chance=0.35, seed=905403)
    K.litter(t, (2, 2, 3, 18, 2, 12), [('oldrepublic:debris', 3), (skull(4), 1), ('minecraft:bone_block', 1)], 0.05,
             seed=905404)
    lights_ok(t, (2, 2, 1, 18, 9, 18), tid)
    check_reach(t, [entry], keys, opens, tid)
    return save(t, tid, 'greater_tomb_sealed_crypt', cut=[6], plans=[2, 3])

# ---------------------------------------------------------------- gate (new)

GX, GY, GZ = 35, 22, 35
G_BURIED = 6
GROUND = G_BURIED - 1        # the ground layer the camp stands on
FEET = G_BURIED              # the feet layer
CX = 17
PIT = (9, 18, 25, 30)        # x0, z0, x1, z1 of the forecourt floor (y 0)
STAIR = (13, 13, 21, 17)     # the grand stair: x0, z0, x1, z1 (one step per row, down to the south)
FACADE_Z = 31                # the facade's north face (z 31..34)


def gate_materials(seed):
    m = {}
    m['top'] = mixer(seed, [('red_sand', 5), ('coarse_dirt', 2), ('red_sandstone', 1), ('gravel', 1)], 3.0)
    m['path'] = mixer(seed + 1, [('gravel', 3), ('coarse_dirt', 3), ('packed_mud', 2), ('red_sand', 1)], 2.0)
    m['wall'] = mixer(seed + 2, TOMB_WALL, 3.0)
    m['floor'] = mixer(seed + 3, TOMB_FLOOR, 2.5)
    m['base'] = mixer(seed + 4, TOMB_BASE, 2.0)
    band = Noise(seed + 5, 5.0)

    def strata(x, y, z):
        v = y + (band.at(x, 0, z) - 0.5) * 1.6
        if v < 0.8:
            return 'red_terracotta' if hash01(seed + 6, x, y, z) < 0.7 else 'terracotta'
        if v < 2.0:
            return 'orange_terracotta' if hash01(seed + 7, x, y, z) < 0.6 else 'red_terracotta'
        if v < 3.2:
            return 'red_sandstone' if hash01(seed + 8, x, y, z) < 0.75 else 'smooth_red_sandstone'
        return 'red_sandstone' if hash01(seed + 9, x, y, z) < 0.5 else 'coarse_dirt'
    m['strata'] = strata
    return m


def in_pit(x, z):
    return PIT[0] <= x <= PIT[2] and PIT[1] <= z <= PIT[3]


def in_stair(x, z):
    return STAIR[0] <= x <= STAIR[2] and STAIR[1] <= z <= STAIR[3]


def stair_floor(z):
    """The top block's y of the grand stair's row z (a stair block), the forecourt floor is 0."""
    return GROUND - 1 - (z - STAIR[1])


def statue(t, x0, z0, headless=False, seed=0):
    """A seated guardian 3 wide on a plinth, facing north; its back to the south."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            t.set(x, FEET, z, 'polished_blackstone_bricks')
    for x in range(x0, x0 + 3):
        t.set(x, FEET + 1, z0, stairs('polished_blackstone_brick_stairs', 'south'))      # the knees
        for z in (z0 + 1, z0 + 2):
            t.set(x, FEET + 1, z, 'oldrepublic:tomb_bricks')
            t.set(x, FEET + 2, z, 'oldrepublic:tomb_bricks')
        t.set(x, FEET + 2, z0, AIR)
    t.set(x0 + 1, FEET + 3, z0 + 1, 'oldrepublic:tomb_bricks')                           # the chest
    t.set(x0 + 1, FEET + 3, z0 + 2, 'oldrepublic:tomb_bricks')
    t.set(x0, FEET + 3, z0 + 1, stairs('oldrepublic:tomb_brick_stairs', 'east'))        # the shoulders
    t.set(x0 + 2, FEET + 3, z0 + 1, stairs('oldrepublic:tomb_brick_stairs', 'west'))
    t.set(x0, FEET + 2, z0 + 1, 'oldrepublic:tomb_bricks')
    t.set(x0 + 1, FEET + 1, z0, 'polished_blackstone_bricks')                           # hands on the knees
    if not headless:
        t.set(x0 + 1, FEET + 4, z0 + 1, 'chiseled_polished_blackstone')                 # the head
        t.set(x0 + 1, FEET + 5, z0 + 1, slab('polished_blackstone_slab'))
    else:
        t.set(x0 + 1, FEET + 4, z0 + 1, slab('polished_blackstone_brick_slab'))         # the broken neck


def obelisk_pier(t, x0, z0, top, m):
    """A 3 x 3 pier rising from the forecourt floor through the facade into an obelisk."""
    for y in range(0, top + 1):
        for x in range(x0, x0 + 3):
            for z in range(z0, z0 + 3):
                edge = x in (x0, x0 + 2) and z in (z0, z0 + 2)
                if y <= 1:
                    st = 'polished_blackstone_bricks'
                elif y in (6, 12) or y == top:
                    st = CARVED
                elif edge:
                    st = 'polished_blackstone'
                else:
                    st = PILLAR
                t.set(x, y, z, st)
    cx, cz = x0 + 1, z0 + 1
    for d, dx, dz in SIDES:
        t.set(cx + dx, top + 1, cz + dz, stairs('polished_blackstone_brick_stairs', B.opposite(d)))
    for x, z in ((x0, z0), (x0 + 2, z0), (x0, z0 + 2), (x0 + 2, z0 + 2)):
        t.set(x, top + 1, z, slab('polished_blackstone_brick_slab'))
    for y in range(top + 1, top + 4):
        t.set(cx, y, cz, PILLAR)
    t.set(cx, top + 4, cz, 'chiseled_polished_blackstone')
    t.set(cx, top + 5, cz, wall('polished_blackstone_wall'))


def command_post(t, x0, z0, seed):
    """The Imperial prefab: 7 x 7 plating walls, a deck floor, a door on the west, the dig's console."""
    x1, z1 = x0 + 6, z0 + 6
    plating = mixer(seed, [('oldrepublic:imperial_plating', 5), ('oldrepublic:durasteel_plating', 2),
                           ('oldrepublic:imperial_panel', 1)], 2.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, GROUND, z, 'oldrepublic:durasteel_deck')
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(FEET, FEET + 3):
                t.set(x, y, z, plating(x, y, z) if edge else AIR)
            t.set(x, FEET + 3, z, 'oldrepublic:imperial_plating' if edge else 'oldrepublic:durasteel_plating')
            if edge:
                t.set(x, FEET + 4, z, slab('oldrepublic:imperial_plating_slab'))
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(FEET, FEET + 4):
            t.set(x, y, z, S('oldrepublic:durasteel_beam', axis='y'))
    # the door (2 wide, 3 high) on the west side, toward the camp; a window strip north and south
    for z in (z0 + 3, z0 + 4):
        for y in range(FEET, FEET + 3):
            t.set(x0, y, z, AIR)
    for x in (x0 + 2, x0 + 4):
        t.set(x, FEET + 1, z0, 'oldrepublic:durasteel_grate')
        t.set(x, FEET + 1, z1, 'oldrepublic:durasteel_grate')
    t.set(x0 + 3, FEET + 3, z0 + 3, 'oldrepublic:light_panel')
    # inside: the console and holo screen at the east wall, the chest, a cot, the map table
    t.set(x1 - 1, FEET, z0 + 2, S('oldrepublic:console', facing='west'))
    t.set(x1, FEET + 1, z0 + 2, 'oldrepublic:imperial_plating')
    t.set(x1 - 1, FEET + 1, z0 + 3, S('oldrepublic:holo_screen', facing='west'))
    t.chest(x1 - 1, FEET, z0 + 4, facing='west', loot='chests/sith_tomb')
    t.anchor('CHEST_1', x1 - 1, FEET, z0 + 4)
    t.set(x0 + 1, FEET, z0 + 1, S('gray_bed', facing='south', part='head', occupied=False))
    t.set(x0 + 1, FEET, z0 + 2, S('gray_bed', facing='south', part='foot', occupied=False))
    t.set(x0 + 3, FEET, z0 + 1, 'cartography_table')
    t.set(x0 + 4, FEET, z0 + 1, 'oldrepublic:cargo_crate_imperial')
    t.set(x0 + 4, FEET + 1, z0 + 1, S('decorated_pot', facing='south', cracked=False, waterlogged=False))
    t.set(x0 + 1, FEET, z1 - 1, 'barrel')
    t.set(x0 + 3, FEET, z1 - 1, S('oldrepublic:console', facing='north'))
    # the roof: an antenna and a crate
    for y in range(FEET + 5, FEET + 9):
        t.set(x0 + 1, y, z0 + 1, S('oldrepublic:durasteel_beam', axis='y'))
    t.set(x0 + 1, FEET + 9, z0 + 1, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(x1 - 1, FEET + 5, z1 - 1, 'oldrepublic:cargo_crate_imperial')


def build_gate():
    import props as P
    tid = 'greater_tomb/gate'
    t = DTemplate(GX, GY, GZ, seed=905101)
    t.name = tid
    m = gate_materials(905102)

    # The ground: a plug of strata with the surface on top, so the site never depends on the terrain.
    for x in range(GX):
        for z in range(GZ):
            for y in range(0, GROUND):
                t.set(x, y, z, m['strata'](x, y, z))
            t.set(x, GROUND, z, m['top'](x, GROUND, z))
    t.fill(0, FEET, 0, GX - 1, GY - 1, GZ - 1, AIR)

    # The forecourt: floor at y 0, open above, red sand still unexcavated against the cut walls.
    for x in range(PIT[0], PIT[2] + 1):
        for z in range(PIT[1], FACADE_Z):
            t.set(x, 0, z, m['floor'](x, 0, z))
            for y in range(1, FEET):
                t.set(x, y, z, AIR)
    way = mixer(905113, [('polished_blackstone_bricks', 5), ('cracked_polished_blackstone_bricks', 3),
                         ('oldrepublic:tomb_stone_cracked', 1)], 2.0)
    for x in range(CX - 2, CX + 3):
        for z in range(PIT[1], FACADE_Z):
            t.set(x, 0, z, way(x, 0, z))
    for x in range(PIT[0], PIT[2] + 1):
        for z in range(PIT[1], FACADE_Z):
            edge = min(x - PIT[0], PIT[2] - x)
            if edge == 0 and hash01(5, x, z) < 0.75 or edge == 1 and hash01(6, x, z) < 0.3:
                t.set(x, 1, z, 'red_sand')
                if edge == 0 and hash01(7, x, z) < 0.35:
                    t.set(x, 2, z, 'red_sand')
    # Timber shoring in the cut walls: posts every four blocks, a waling plank course.
    for z in range(PIT[1], FACADE_Z):
        for x in (PIT[0] - 1, PIT[2] + 1):
            for y in range(1, GROUND + 1):
                if z % 4 == 2:
                    t.set(x, y, z, log('stripped_spruce_log'))
                elif y == 3:
                    t.set(x, y, z, 'spruce_planks')
    # The grand stair: nine wide, one step per row, cut into the north end; retaining walls either side.
    for z in range(STAIR[1], STAIR[3] + 1):
        fy = stair_floor(z)
        for x in range(STAIR[0], STAIR[2] + 1):
            for y in range(0, fy):
                t.set(x, y, z, m['wall'](x, y, z))
            t.set(x, fy, z, stairs('polished_blackstone_brick_stairs', 'north') if 0 < x - STAIR[0] < 8
                  else stairs('oldrepublic:tomb_brick_stairs', 'north'))
            for y in range(fy + 1, FEET):
                t.set(x, y, z, AIR)
        for x in (STAIR[0] - 1, STAIR[2] + 1):
            for y in range(0, FEET):
                t.set(x, y, z, m['wall'](x, y, z) if y < GROUND else CARVED)
            t.set(x, FEET, z, wall('oldrepublic:tomb_brick_wall'))
    for x in (STAIR[0] - 1, STAIR[2] + 1):                      # newels at the stair head
        t.set(x, FEET, STAIR[1] - 1, 'polished_blackstone_bricks')
        t.set(x, FEET + 1, STAIR[1] - 1, CARVED)
    for x in range(STAIR[0], STAIR[2] + 1):                     # the landing at the top
        t.set(x, GROUND, STAIR[1] - 1, m['floor'](x, GROUND, STAIR[1] - 1))
    # The forecourt's north wall either side of the stair: the cut, with the old paving's edge showing.
    for x in list(range(PIT[0], STAIR[0] - 1)) + list(range(STAIR[2] + 2, PIT[2] + 1)):
        for y in range(1, GROUND + 1):
            t.set(x, y, PIT[1] - 1, m['strata'](x, y, PIT[1] - 1) if y != 3 else 'spruce_planks')

    # The facade: the tomb's front, from the forecourt floor up past the ground, with the portal. Above the
    # ground its mass steps back into the dune like a buried mastaba, red sand drifted on the steps.
    top = 11
    fx0, fx1 = PIT[0] - 1, PIT[2] + 1
    for x in range(fx0, fx1 + 1):
        end = x in (fx0, fx1)
        for z in range(FACADE_Z, GZ):
            back = z - FACADE_Z
            ztop = top - (0 if back <= 1 else 2 * (back - 1))          # 11, 11, 9, 7
            if end:
                ztop = min(ztop, top - 2)
            for y in range(0, ztop + 1):
                if y <= 1:
                    st = m['base'](x, y, z)
                elif y in (6, top) and back == 0:
                    st = CARVED
                else:
                    st = m['wall'](x, y, z)
                t.set(x, y, z, st)
            if back >= 2 or end:
                face = 'north' if back >= 2 else ('east' if x == fx0 else 'west')
                drift = hash01(905110, x, z) < 0.2 + 0.2 * back
                t.set(x, ztop + 1, z, 'red_sand' if drift else stairs('oldrepublic:tomb_brick_stairs', face))
    for x in range(fx0 + 1, fx1):
        t.set(x, top, FACADE_Z - 1, stairs('oldrepublic:tomb_brick_stairs', 'south', half='top'))
        t.set(x, top + 1, FACADE_Z, wall('oldrepublic:tomb_brick_wall') if x % 2 else
              'polished_blackstone_bricks')
    # Drifts of red sand bury the ends of the mass.
    for z in range(FACADE_Z - 1, GZ):
        for x, k in ((fx0 - 1, 0), (fx0 - 2, 1), (fx1 + 1, 0), (fx1 + 2, 1)):
            h = 2 - k - (1 if z == GZ - 1 else 0)
            for y in range(FEET, FEET + h):
                if hash01(905111, x, y, z) < 0.85:
                    t.set(x, y, z, 'red_sand' if hash01(905112, x, y, z) < 0.8 else 'gravel')
    # Narrow niches in the wings, each with a cluster of red candles.
    for x in (PIT[0], PIT[2]):
        for y in (2, 3, 4):
            t.set(x, y, FACADE_Z, AIR)
        t.set(x, 1, FACADE_Z, CARVED)
        t.set(x, 2, FACADE_Z, candle(3, lit=True, color='red'))
        t.set(x, 5, FACADE_Z, 'chiseled_polished_blackstone')
        for y in (1, 2):
            t.set(x, y, FACADE_Z - 1, AIR)
    # The portal: a recess 7 wide and 5 high stepping in to the tunnel 5 wide and 3 high.
    for z in range(FACADE_Z, GZ):
        depth = z - FACADE_Z
        half = 3 if depth == 0 else 2
        hi = 5 if depth == 0 else 4 if depth == 1 else 3
        for x in range(CX - half, CX + half + 1):
            t.set(x, 0, z, m['floor'](x, 0, z) if depth < 2 else 'polished_blackstone')
            for y in range(1, hi + 1):
                t.set(x, y, z, AIR)
    for z in (FACADE_Z, FACADE_Z + 1):                            # the jambs: stepped carved stone
        for x in (CX - 4, CX + 4):
            for y in range(1, 7):
                t.set(x, y, z, CARVED if y % 2 else 'polished_blackstone_bricks')
    for x in range(CX - 3, CX + 4):                               # the lintel
        t.set(x, 6, FACADE_Z, 'chiseled_polished_blackstone' if x in (CX - 3, CX + 3) else 'polished_blackstone')
        t.set(x, 6, FACADE_Z - 1, stairs('polished_blackstone_brick_stairs', 'south', half='top'))
    for x in range(CX - 3, CX + 4):                               # a frieze of teeth over it
        if x % 2 == 1:
            t.set(x, 9, FACADE_Z - 1, stairs('polished_blackstone_stairs', 'south', half='top'))
    for z in (FACADE_Z + 2, FACADE_Z + 3):                        # red light from below in the tunnel
        for x in (CX - 3, CX + 3):
            t.set(x, 1, z, RED)
    for x in (CX - 3, CX + 3):
        t.set(x, 1, FACADE_Z + 1, RED)
    t.jigsaw(CX, 1, GZ - 1, 'south_up', name='oldrepublic:tomb_down', target='oldrepublic:descent_top',
             pool='oldrepublic:greater_tomb/descent')
    # Obelisk piers either side of the portal.
    obelisk_pier(t, CX - 7, FACADE_Z - 1, 13, m)
    obelisk_pier(t, CX + 5, FACADE_Z - 1, 13, m)
    # A stepped gable over the portal; its east shoulder has broken off and lies in the forecourt.
    for i in range(3):
        y = top + 1 + i
        a, b = CX - 4 + i, CX + 4 - i
        for z in (FACADE_Z, FACADE_Z + 1):
            for x in range(a, b + 1):
                if x in (a, b):
                    st = stairs('oldrepublic:tomb_brick_stairs', 'east' if x == a else 'west')
                else:
                    st = CARVED if i == 0 and z == FACADE_Z else m['wall'](x, y, z)
                t.set(x, y, z, st)
    t.set(CX, top + 4, FACADE_Z, 'chiseled_polished_blackstone')
    t.set(CX, top + 5, FACADE_Z, wall('polished_blackstone_wall'))
    for (x, y) in ((CX + 3, top + 2), (CX + 2, top + 3), (CX + 3, top + 1)):
        for z in (FACADE_Z, FACADE_Z + 1):
            t.set(x, y, z, AIR)
    t.set(CX + 3, top + 1, FACADE_Z + 1, stairs('oldrepublic:tomb_brick_stairs', 'west'))
    t.set(CX + 2, top + 2, FACADE_Z + 1, stairs('oldrepublic:tomb_brick_stairs', 'west'))

    # The door that sealed the portal lies broken on the paving, thrown outward.
    door = [(x, z) for x in range(CX - 2, CX + 3) for z in range(24, 30) if z != 27]
    for (x, z) in door:
        st = 'polished_blackstone' if 0 < abs(x - CX) < 2 or z in (25, 28) else CARVED
        if hash01(11, x, z) < 0.2:
            st = 'cracked_polished_blackstone_bricks'
        t.set(x, 1, z, st)
    for x in range(CX - 2, CX + 3):
        t.set(x, 2, 29, stairs('polished_blackstone_stairs', 'north'))       # the end propped on rubble
        t.set(x, 1, 27, 'oldrepublic:debris' if x % 2 else slab('polished_blackstone_slab'))
    P.scorch_fan(t, CX, FACADE_Z - 1, lambda x, z: (1 if in_pit(x, z) and is_air_set(t, (x, 1, z)) else None),
                 (0, -1), 9, 0.55, seed=905103)
    for (x, y) in ((CX - 3, 2), (CX + 3, 3), (CX - 2, 7), (CX + 2, 8), (CX - 1, 7)):
        t.set(x, y, FACADE_Z, 'cracked_polished_blackstone_bricks')

    # The dig in the forecourt: lamps, crates, the sorting bench, a half-dug colossal head, ladders.
    P.mast(t, PIT[0] + 1, 1, PIT[1] + 1, 4, arm='south')
    P.mast(t, PIT[2] - 1, 1, PIT[1] + 3, 4, toppled='west')
    for (x, z) in ((23, 29), (24, 29), (24, 28)):
        P.crates(t, x, 1, z, ['oldrepublic:cargo_crate_imperial', 'oldrepublic:cargo_crate'], seed=905104)
    # The first team that went in, laid out under canvas by the west wall, a red candle at each head.
    for z in (25, 27, 29):
        for x in (10, 11):
            t.set(x, 1, z, slab('oldrepublic:canvas_slab'))
        t.set(12, 1, z, candle(1 + z % 2, lit=True, color='red'))
    t.set(10, 1, 26, 'oldrepublic:scorch_mark')
    # Work lamps the dig set up at the portal.
    P.mast(t, CX - 4, 1, FACADE_Z - 2, 3, head='oldrepublic:light_panel_amber')
    P.mast(t, CX + 4, 1, FACADE_Z - 2, 3, head='oldrepublic:light_panel_amber')
    for x in (22, 23, 24):
        t.set(x, 1, 27, 'oldrepublic:cargo_crate_imperial' if x != 23 else 'barrel')
    t.set(22, 2, 27, S('decorated_pot', facing='north', cracked=True, waterlogged=False))
    t.set(24, 2, 27, S('decorated_pot', facing='west', cracked=False, waterlogged=False))
    t.set(23, 2, 27, 'oldrepublic:funerary_urn')
    t.set(24, 1, 25, S('decorated_pot', facing='north', cracked=True, waterlogged=False))
    t.set(21, 1, 28, 'oldrepublic:durasteel_grate')
    t.set(21, 2, 28, S('oldrepublic:console', facing='west'))
    for (x, y, z) in ((10, 1, 23), (11, 1, 23), (12, 1, 23), (10, 1, 24), (11, 1, 24), (12, 1, 24),
                      (10, 2, 23), (11, 2, 23), (12, 2, 23), (11, 3, 23)):
        t.set(x, y, z, 'polished_blackstone')                      # the colossal head, face up
    t.set(10, 2, 24, stairs('polished_blackstone_stairs', 'south'))
    t.set(12, 2, 24, stairs('polished_blackstone_stairs', 'south'))
    t.set(11, 2, 24, 'chiseled_polished_blackstone')
    for (x, z) in ((10, 22), (13, 22), (13, 25), (9, 25)):
        t.set(x, 1, z, fence('spruce_fence'))                     # the excavation grid's stakes
    for y in range(1, FEET):
        t.set(PIT[0], y, 21, S('ladder', facing='east', waterlogged=False))
        t.set(PIT[2], y, 26, S('ladder', facing='west', waterlogged=False))
    from structlib import mod as M
    M.spawner(t, 12, 1, 20, 'dig_team', anchor='SQUAD')

    # The gantry crane over the forecourt, its load still hanging.
    for y in range(FEET, FEET + 10):
        t.set(PIT[0] - 3, y, 24, S('oldrepublic:durasteel_beam', axis='y'))
        t.set(PIT[2] + 3, y, 24, S('oldrepublic:durasteel_beam', axis='y'))
    for x in range(PIT[0] - 3, PIT[2] + 4):
        t.set(x, FEET + 10, 24, S('oldrepublic:durasteel_beam', axis='x'))
    t.set(CX - 1, FEET + 9, 24, 'oldrepublic:durasteel_plating')
    for y in range(FEET + 3, FEET + 9):
        t.set(CX - 1, y, 24, chain('y'))
    for x in range(CX - 2, CX + 1):
        t.set(x, FEET + 2, 24, 'polished_blackstone' if x != CX - 1 else CARVED)
    for x in (PIT[0] - 3, PIT[2] + 3):                            # footings and braces
        t.set(x, GROUND, 24, 'oldrepublic:durasteel_plating')
        for z in (23, 25):
            t.set(x, FEET, z, S('oldrepublic:durasteel_beam', axis='z'))

    # The camp on the surface: the approach, the guardians, the command post, the crew tents.
    for z in range(0, STAIR[1] - 1):
        for x in range(CX - 2, CX + 3):
            if abs(x - CX) < 2 or hash01(12, x, z) < 0.6:
                t.set(x, GROUND, z, m['path'](x, GROUND, z))
    statue(t, CX - 8, 9, seed=1)
    statue(t, CX + 6, 9, headless=True, seed=2)
    t.set(CX + 9, FEET, 7, 'chiseled_polished_blackstone')       # the fallen head
    command_post(t, 27, 1, 905105)
    M.spawner(t, 25, FEET, 6, 'security_droids', count=2, anchor='GUARD')
    for x in range(26, 28):
        for z in range(4, 6):
            t.set(x, GROUND, z, m['path'](x, GROUND, z))
    P.tent(t, 1, 1, 6, 5, FEET, ridge='x', door='east', trim='oldrepublic:canvas_stairs')
    P.tent(t, 1, 8, 6, 5, FEET, ridge='x', door='east')
    for (x, z) in ((2, 2), (2, 9)):
        t.set(x, FEET, z + 1, S('gray_bed', facing='east', part='foot', occupied=False))
        t.set(x + 1, FEET, z + 1, S('gray_bed', facing='east', part='head', occupied=False))
    t.set(4, FEET, 3, 'barrel')
    t.set(4, FEET, 10, 'oldrepublic:cargo_crate_imperial')
    t.set(9, FEET, 7, campfire(lit=True))
    t.set(9, FEET, 6, log('stripped_spruce_log', 'x'))
    t.set(10, FEET, 8, log('stripped_spruce_log', 'z'))
    for (x, z) in ((8, 3), (8, 4), (9, 3)):
        P.crates(t, x, FEET, z, ['oldrepublic:cargo_crate_imperial', 'oldrepublic:cargo_crate', 'barrel'], seed=905106)
    # Lamps at the forecourt's corners, a generator with its cable, rails along the cut.
    P.mast(t, PIT[0] - 1, FEET, PIT[1] - 2, 5, arm='south')
    P.mast(t, PIT[2] + 1, FEET, PIT[1] - 2, 5, arm='south')
    t.set(29, FEET, 11, 'oldrepublic:durasteel_plating')
    t.set(30, FEET, 11, 'oldrepublic:durasteel_plating')
    t.set(29, FEET + 1, 11, S('oldrepublic:durasteel_pipes', axis='y'))
    t.set(30, FEET, 12, S('oldrepublic:console', facing='west'))
    for x in range(PIT[2] + 2, 29):
        t.set(x, FEET, 12, chain('x'))
    for z in range(PIT[1], FACADE_Z - 1):
        if z % 5 != 0:
            t.set(PIT[0] - 1, FEET, z, bars())
            t.set(PIT[2] + 1, FEET, z, bars())
    # Spoil heaps at the south corners and the fallen obelisk on the east.
    P.boulder(t, 3.5, FEET, 25, 3.2, 1.6, 5.5, mixer(905107, [('red_sand', 4), ('gravel', 2), ('coarse_dirt', 1)], 2.0))
    P.boulder(t, 31.0, FEET, 32, 3.0, 1.2, 2.5, mixer(905108, [('red_sand', 4), ('gravel', 2)], 2.0))
    for z in range(15, 27):
        for x in (31, 32):
            for y in (GROUND, FEET):
                st = PILLAR.replace('axis=y', 'axis=z') if (x, y) != (32, FEET) else 'polished_blackstone'
                t.set(x, y, z, st)
    for x in (31, 32):
        t.set(x, FEET, 27, slab('polished_blackstone_slab'))
        t.set(x, FEET, 13, 'polished_blackstone_bricks')
        t.set(x, FEET + 1, 13, 'oldrepublic:tomb_bricks' if x == 31 else CARVED)
        t.set(x, FEET, 14, 'polished_blackstone_bricks')

    # The camp's north barricade either side of the gap the path comes in by, with lamp posts at the gap.
    for x in list(range(8, 14)) + list(range(21, 27)):
        post = x in (8, 11, 13, 21, 23, 26)
        if post:
            t.set(x, FEET, 1, S('oldrepublic:durasteel_beam', axis='y'))
            t.set(x, FEET + 1, 1, S('oldrepublic:durasteel_beam', axis='y'))
        else:
            t.set(x, FEET, 1, slab('oldrepublic:durasteel_plating_slab', 'top') if hash01(13, x) < 0.3
                  else 'oldrepublic:durasteel_plating')
            t.set(x, FEET + 1, 1, bars())
    for x in (CX - 3, CX + 3):
        P.mast(t, x, FEET, 1, 4, head='oldrepublic:light_panel_amber')
    # A speeder bike parked by the command post, floating on its repulsors.
    for x in (21, 22, 23):
        t.set(x, FEET, 5, slab('oldrepublic:imperial_plating_slab', 'top'))
    t.set(23, FEET + 1, 5, bars())
    t.set(21, FEET + 1, 5, slab('oldrepublic:durasteel_plating_slab'))
    # Dead bushes and dry grass on the open ground.
    for x in range(GX):
        for z in range(GZ):
            if not (is_air_set(t, (x, FEET, z)) and is_air_set(t, (x, FEET + 1, z))):
                continue
            g = B.name_of(t.cells.get((x, GROUND, z)))
            if g not in ('minecraft:red_sand', 'minecraft:coarse_dirt'):
                continue
            if abs(x - CX) <= 3 and z < STAIR[1]:
                continue
            h = hash01(905114, x, z)
            if h < 0.035:
                t.set(x, FEET, z, 'dead_bush')
            elif h < 0.06:
                t.set(x, FEET, z, 'short_dry_grass')

    # Anchors on the surface.
    from structlib import mod as M2
    M2.spot(t, 'STAIR_MOUTH', CX, FEET, STAIR[1] - 1)
    M2.spot(t, 'SURFACE_GUARD', CX, FEET, 4)

    # Light: the forecourt, the portal, the command post and the tents.
    fill_light(t, (PIT[0], 1, PIT[1], PIT[2], 5, GZ - 1), level=8, strength=12)
    fill_light(t, (27, FEET, 1, 33, FEET + 2, 7), level=8, strength=11)
    fill_light(t, (1, FEET, 1, 6, FEET + 2, 12), level=8, strength=11)
    opens = openings(t)
    check_reach(t, [(CX, FEET, 0)], [(CX, 1, GZ - 2), (CX, FEET, STAIR[1] - 1), (CX, FEET, 4), (31, FEET, 5),
                                    (12, 1, 19), (25, FEET, 5)], opens, tid)
    return save(t, tid, 'greater_tomb_gate', views=('se', 'nw'), plans=[0, FEET])


def tomb_worldgen():
    """The pools (start fields for the new gate), the structure (buried 6) and the set (clear_of_origin)."""
    tomb = 'oldrepublic:sith_tomb_weathering'
    W.pool('greater_tomb/start', [W.element('greater_tomb/gate', processors=tomb, adapted=dict(
        yard_stand=G_BURIED, clear_from=GY, clear_inset=0, support_depth=8, ramp_rings=2))])
    W.pool('greater_tomb/descent', [W.element('greater_tomb/descent', processors=tomb)])
    W.pool('greater_tomb/vault', [W.element('greater_tomb/urn_vault', processors=tomb)])
    W.pool('greater_tomb/crypt', [W.element('greater_tomb/sealed_crypt', processors=tomb)])
    W.structure('greater_tomb', start_pool='greater_tomb/start', biome_tag='has_structure/greater_tomb', size=4,
                max_distance=96, step='surface_structures', start_height=1 - G_BURIED)
    W.structure_set('greater_tombs', [('greater_tomb', 1)], spacing=36, separation=14, salt=905123,
                    placement='clear_of_origin', min_distance=256)


def main(only=None):
    builds = {'gate': build_gate, 'descent': build_descent, 'vault': build_vault, 'crypt': build_crypt}
    lines = [f() for k, f in builds.items() if not only or k in only]
    tomb_worldgen()
    return lines


if __name__ == '__main__':
    import sys
    main(sys.argv[1:])
