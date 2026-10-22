"""tyrvann/hask_camp (worldgen, Tyrvann stormwood and mire; docs/MILESTONE_6.md 5.6): a Clan Hask hunting camp.

Clan Hask hunt the stormwood and the mire from their holdfast: dark wood, clan iron, hides, and a lightning
rod over everything they build to pull the storm off their heads. A hunting party's camp, out for the
hollowhorns and gone for the day: hide tents round a fire, racks of hides drying, a butchering board and a
smoker, a rack of hunting spears, a windbreak of clan iron plate, and the trophy post the clan raises
wherever it hunts: a dark post banded with clan iron, a hollowhorn's skull with its horns on top, the
smaller kills' skulls round its foot. Nobody here is hostile (no spawner): the clan's warriors are out.

Two starts:
  camp_a  on the ground: a trampled clearing (or a mud flat in the mire, on its own footings), four tents
          round a ring of stones, the trophy post on the path in.
  camp_b  on stilts: a plank deck on dark oak legs to the bed of the mire (or the forest floor), three tents,
          a stone hearth, a drying deck over a gangway, a watch platform with its rod, a ladder down.

Frame (buried 2): y 0 footings, y 1 the ground or the water line, y 2 the walk; camp_b's deck at y 3 (walk 4).
"""
from common import *  # noqa: F401,F403

NAME = 'tyrvann/hask_camp'
SALT = 960352
SIZE = (35, 20, 35)
BURIED = 2
GY, FY = 1, 2
DY = 3                                     # camp_b's deck
C = 17
LOOT_ID = 'oldrepublic:chests/tyrvann/hask_camp'
VARIANTS = ('a', 'b')
CAMERAS = {}

CLAN_IRON = [('oldrepublic:wreck_plating', 5), ('oldrepublic:wreck_plating_scorched', 2), ('oldrepublic:rusted_plating', 2)]
HIDES = ('brown', 'white', 'light_gray', 'brown', 'black')


def post(t, x, z, y0, h, state=None):
    for y in range(y0, y0 + h):
        t.set(x, y, z, state or S('dark_oak_fence'))


def lamp_stake(t, x, z, y0, h=2):
    post(t, x, z, y0, h)
    t.set(x, y0 + h, z, lantern(soul=False))


def spear(t, x, z, y0, h=3):
    """A Hask hunting spear stood on its butt: a dark oak shaft, an iron head (a lightning rod)."""
    post(t, x, z, y0, h)
    t.set(x, y0 + h, z, rod(oxidized=False))


def drying_rack(t, rng, x0, z0, length, axis, y0):
    """Two posts and a beam with hides hanging off both sides of it (wall banners)."""
    dx, dz = (1, 0) if axis == 'x' else (0, 1)
    x1, z1 = x0 + dx * (length - 1), z0 + dz * (length - 1)
    for (x, z) in ((x0, z0), (x1, z1)):
        post(t, x, z, y0, 3)
    for k in range(length):
        x, z = x0 + dx * k, z0 + dz * k
        t.set(x, y0 + 3, z, log('minecraft:stripped_dark_oak_log', axis))
    sides = (('north', 0, -1), ('south', 0, 1)) if axis == 'x' else (('west', -1, 0), ('east', 1, 0))
    for k in range(1, length - 1):
        for (face, sx, sz) in sides:
            if rng.random() < 0.75:
                x, z = x0 + dx * k + sx, z0 + dz * k + sz
                mod.banner(t, x, y0 + 3, z, face, rng.choice(HIDES))


def trophy_post(t, rng, x, z, y0):
    """The clan's trophy post: a dark post banded with clan iron, a hollowhorn skull on top (the brow, the
    long face, the horns sweeping out and up to dark tips), the lesser kills' skulls on stakes round its
    foot, the clan's banners, the rod over it all."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            t.set(x + dx, y0 - 1, z + dz, 'minecraft:cobbled_deepslate' if (dx, dz) != (0, 0) else 'minecraft:polished_deepslate')
    for y in range(y0, y0 + 7):
        t.set(x, y, z, 'oldrepublic:wreck_plating' if y in (y0 + 2, y0 + 5) else log('minecraft:stripped_dark_oak_log'))
    sy = y0 + 7
    t.set(x, sy, z, log('minecraft:bone_block', 'y'))                     # the face, the muzzle forward (north)
    t.set(x, sy, z - 1, log('minecraft:bone_block', 'z'))
    for dx in (-1, 0, 1):                                                 # the brow
        t.set(x + dx, sy + 1, z, log('minecraft:bone_block', 'x'))
    for sgn in (-1, 1):                                                   # the horns
        t.set(x + 2 * sgn, sy + 1, z, log('minecraft:bone_block', 'x'))
        t.set(x + 3 * sgn, sy + 1, z, log('minecraft:bone_block', 'x'))
        t.set(x + 3 * sgn, sy + 2, z, log('minecraft:bone_block', 'y'))
        t.set(x + 3 * sgn, sy + 3, z, 'minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]')
    t.set(x, sy + 2, z, rod(oxidized=False))
    t.set(x, sy + 3, z, rod(oxidized=False))
    for (face, dx, dz) in (('east', 1, 0), ('west', -1, 0)):
        mod.banner(t, x + dx, y0 + 4, z + dz, face, 'red' if face == 'east' else 'black')
    for (dx, dz, rot) in ((-2, -1, 8), (2, -1, 8), (-2, 1, 4), (2, 1, 12), (0, 2, 0)):
        post(t, x + dx, z + dz, y0, 1 + (dx + dz) % 2)
        t.set(x + dx, y0 + 1 + (dx + dz) % 2, z + dz, skull((rot + rng.randint(-1, 1)) % 16))


def butchery(t, x, z, y, facing='north'):
    """A butchering board on two trestles, a grindstone, a smoker and a cauldron for the water."""
    for k in range(3):
        t.set(x + k, y, z, S('dark_oak_fence') if k != 1 else slab('minecraft:dark_oak_slab', 'top'))
        t.set(x + k, y + 1, z, slab('minecraft:dark_oak_slab') if k != 1 else AIR)
    t.set(x + 1, y + 1, z, slab('minecraft:dark_oak_slab'))
    t.set(x + 3, y, z, S('grindstone', face='floor', facing='east'))
    t.set(x - 1, y, z, S('smoker', facing=facing, lit=False))
    t.set(x - 1, y, z + 1, S('cauldron'))


def hearth(t, rng, x, z, y, campfire_lit=True):
    """A fire in a ring of stones, the log seats round it."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) == (0, 0):
                t.set(x, y, z, campfire(soul=False, lit=campfire_lit, facing='north'))
            else:
                t.set(x + dx, y, z + dz, slab(pick(rng, [('minecraft:cobblestone_slab', 2), ('minecraft:mossy_cobblestone_slab', 2),
                                                         ('minecraft:cobbled_deepslate_slab', 1)])))
    for (dx, dz, ax) in ((0, -3, 'x'), (0, 3, 'x'), (-3, 0, 'z'), (3, 0, 'z')):
        for k in (-1, 0, 1):
            px, pz = (x + dx + k, z + dz) if ax == 'x' else (x + dx, z + dz + k)
            if rng.random() < 0.85:
                t.set(px, y, pz, log('minecraft:stripped_dark_oak_log', ax))


def tent_furnish(t, rng, inside, y, chest=None, beds=1, color='brown'):
    cells = sorted(inside)
    rng.shuffle(cells)
    placed = 0
    for (x, z) in cells:
        if placed >= beds:
            break
        for face in DIRS4:
            dx, dz = B.step(face)
            if (x + dx, z + dz) in inside and t.is_air(x, y, z) and t.is_air(x + dx, y, z + dz):
                bed(t, x, y, z, face, color)
                placed += 1
                break
    for (x, z) in cells:
        if t.is_air(x, y, z) and rng.random() < 0.3:
            t.set(x, y, z, pick(rng, [(barrel('up'), 2), ('minecraft:brown_carpet', 3), ('minecraft:red_carpet', 1),
                                      (lantern(soul=False), 1)]))
    if chest:
        cx, cz, face = chest
        t.chest(cx, y, cz, face, loot=LOOT_ID)


def ground(t, rng, cells, seed):
    """The trampled camp floor: packed mud, coarse dirt, gravel and peat, a few podzol and moss cells
    (never a 2x2 of them), on footings."""
    worn = patch(Noise(seed, 3.0), [('minecraft:packed_mud', 4), ('minecraft:coarse_dirt', 3), ('or:peat', 2),
                                    ('minecraft:gravel', 1), ('minecraft:mud', 1)],
                 jitter=Noise(seed + 1, 1.0), mix=0.45)
    soil = []
    for (x, z) in sorted(cells):
        t.set(x, 0, z, pick(rng, [('minecraft:mud', 3), ('or:peat', 2), ('minecraft:packed_mud', 1)]))
        st = worn(x, GY, z)
        if (x % 2 or z % 2) and rng.random() < 0.12:
            st = rng.choice(('minecraft:podzol', 'minecraft:moss_block', 'minecraft:rooted_dirt'))
        if x % 2 == 0 and z % 2 == 0 and st in ('minecraft:coarse_dirt', 'minecraft:mud'):
            st = 'minecraft:packed_mud'
        t.set(x, GY, z, st)
        if st in ('minecraft:podzol', 'minecraft:moss_block', 'minecraft:rooted_dirt', 'minecraft:coarse_dirt',
                  'minecraft:mud'):
            soil.append((x, z))
    return soil


def camp_a(seed):
    tid = NAME + '/camp_a'
    t = Template(*SIZE, seed=seed, name=tid)
    rng = t.rng
    sx, sy, sz = SIZE
    site = superellipse(sx, sz, C, C, 16.4, 16.4, power=4.0, noise=Noise(seed + 2, 3.0), amp=0.08, freq=0.4)
    soil = ground(t, rng, site, seed + 3)
    path = [(x, z) for z in range(0, C - 3) for x in (C - 1, C, C + 1) if (x, z) in site]
    for (x, z) in path:
        t.set(x, GY, z, pick(rng, [('minecraft:gravel', 2), ('minecraft:coarse_dirt', 2), ('minecraft:packed_mud', 2)])
              if not (x % 2 == 0 and z % 2 == 0) else 'minecraft:packed_mud')
    hearth(t, rng, C, C, FY)
    # the tents: the hunt leader's (crimson gables), two more, one torn by the storm
    leader = tent(t, rng, 4, 4, 7, 7, FY, axis='z', cloth='or:canvas_crimson', door_end='high')
    tent_furnish(t, rng, leader, FY, chest=(7, 5, 'south'), beds=2, color='red')
    t2 = tent(t, rng, 24, 5, 5, 6, FY, axis='z', door_end='high')
    tent_furnish(t, rng, t2, FY, beds=1, color='brown')
    t3 = tent(t, rng, 4, 23, 5, 6, FY, axis='x', door_end='high')
    tent_furnish(t, rng, t3, FY, beds=1, color='brown')
    t4 = tent(t, rng, 23, 24, 5, 6, FY, axis='x', door_end='low', torn=0.35)
    tent_furnish(t, rng, t4, FY, beds=1, color='gray')
    for (x, y, z) in ((7, FY + 5, 4), (26, FY + 4, 5), (4, FY + 4, 25), (28, FY + 4, 26)):
        if t.name_at(x, y - 1, z) == 'minecraft:dark_oak_fence':
            t.set(x, y, z, rod(oxidized=False))
    # the trophy post on the path in, the racks, the spears, the butchery, the clan iron windbreak
    trophy_post(t, rng, C, 9, FY)
    drying_rack(t, rng, 24, 15, 6, 'z', FY)
    drying_rack(t, rng, 10, 26, 6, 'x', FY)
    for k in range(4):                                          # the spear rack: a bar on two stakes, the spears against it
        spear(t, 12 + k, 4, FY, 2 + k % 2)
    post(t, 11, 3, FY, 1)
    post(t, 16, 3, FY, 1)
    for x in range(11, 17):
        t.set(x, FY + 1, 3, log('minecraft:stripped_dark_oak_log', 'x'))
    butchery(t, 24, 12, FY)
    for (x, z) in [(x, 3) for x in range(4, 11)] + [(3, z) for z in range(4, 12)]:
        if (x, z) in site and rng.random() < 0.8:
            t.set(x, FY, z, pick(rng, CLAN_IRON))
            if rng.random() < 0.6:
                t.set(x, FY + 1, z, pick(rng, CLAN_IRON))
    for (x, z) in ((C - 6, C - 6), (C + 6, C - 6), (C - 7, C + 5), (C + 6, C + 6)):
        lamp_stake(t, x, z, FY)
    for (x, z, h) in ((27, 21, 2), (28, 21, 1), (27, 22, 1), (12, 13, 1)):
        for k in range(h):
            t.set(x, FY + k, z, pick(rng, [('oldrepublic:cargo_crate', 2), (barrel('up'), 2)]))
    # the leavings of the hunt: bones by the butchery, a hide pegged out on the ground
    for (x, z) in ((22, 11), (23, 9), (21, 13), (26, 10)):
        if t.is_air(x, FY, z):
            t.set(x, FY, z, pick(rng, [('minecraft:bone_block[axis=x]', 1), (skull(rng.randint(0, 15)), 1),
                                       ('minecraft:brown_carpet', 1)]))
    for x in range(9, 13):
        for z in range(17, 20):
            if t.is_air(x, FY, z):
                t.set(x, FY, z, 'minecraft:brown_carpet' if (x, z) not in ((9, 17), (12, 19)) else AIR)
    for (x, z) in ((8, 16), (13, 16), (8, 20), (13, 20)):
        t.set(x, FY, z, S('dark_oak_fence'))
    finish(t, rng, site, soil)
    CAMERAS[tid] = ((C, FY + 1, 2), (C, FY + 6, 12))
    return t


def camp_b(seed):
    tid = NAME + '/camp_b'
    t = Template(*SIZE, seed=seed, name=tid)
    rng = t.rng
    sx, sy, sz = SIZE
    planks = patch(Noise(seed + 2, 2.0), [('minecraft:dark_oak_planks', 4), ('minecraft:spruce_planks', 2),
                                          ('minecraft:stripped_dark_oak_wood', 1)], jitter=Noise(seed + 3, 0.7))
    main = {(x, z) for x in range(5, 27) for z in range(7, 26)} - {(5, 7), (26, 7), (5, 25), (26, 25), (6, 7), (5, 8)}
    dry = {(x, z) for x in range(27, 34) for z in range(18, 27)}
    gang = {(x, z) for x in range(27, 28) for z in range(20, 23)}
    landing = {(x, z) for x in range(14, 19) for z in range(2, 7)}
    deck = main | dry | gang | landing
    for (x, z) in sorted(deck):
        t.set(x, DY, z, planks(x, DY, z) if (x, z) not in landing else 'minecraft:spruce_planks')
        for y in range(DY + 1, DY + 7):
            t.set(x, y, z, AIR)
    # legs to the bed: a dark oak log every third cell, braces under the edges
    for (x, z) in sorted(deck):
        if (x % 3 == 0 and z % 3 == 0) or (x, z) in ((5, 9), (26, 8), (5, 24), (26, 24), (33, 18), (33, 26), (27, 26),
                                                   (14, 2), (18, 2), (14, 6), (18, 6)):
            for y in range(0, DY):
                t.set(x, y, z, log('minecraft:dark_oak_log'))
    for (x, z) in sorted(ring_of(deck)):
        for face in DIRS4:
            dx, dz = B.step(face)
            if (x + dx, z + dz) not in deck and t.is_air(x, DY - 1, z) and rng.random() < 0.25:
                t.set(x, DY - 1, z, stair('minecraft:dark_oak_stairs', opposite(face), 'top'))
                break
    # the railing, with gaps at the ladder and the gangway
    rail = [p for p in sorted(ring_of(main | dry)) if p not in landing and p not in gang]
    for (x, z) in rail:
        if (x, z) in ((C, 7), (C - 1, 7), (C + 1, 7), (26, 21), (27, 21), (26, 20), (26, 22)):
            continue
        if rng.random() < 0.85:
            t.set(x, DY + 1, z, S('dark_oak_fence'))
    for (x, z) in ((14, 2), (18, 2)):
        post(t, x, z, DY + 1, 2)
        t.set(x, DY + 3, z, lantern(soul=False))
    for y in range(0, DY):                                      # the steps down from the landing
        t.set(C - 1, y, 2, log('minecraft:dark_oak_log'))
        t.set(C + 1, y, 2, log('minecraft:dark_oak_log'))
    for (y, zz) in ((DY - 1, 1), (DY - 2, 0)):
        for x in range(C - 1, C + 2):
            t.set(x, y, zz, stair('minecraft:dark_oak_stairs', 'south'))
            if y > 0:
                t.set(x, 0, zz, log('minecraft:dark_oak_log') if x != C else 'minecraft:dark_oak_planks')
                for yy in range(1, y):
                    t.set(x, yy, zz, 'minecraft:dark_oak_planks')
    # the hearth on its stones in the middle of the deck
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3:
                t.set(C + dx, DY, 16 + dz, pick(rng, [('minecraft:cobblestone', 2), ('minecraft:cobbled_deepslate', 2),
                                                      ('minecraft:mossy_cobblestone', 1)]))
    hearth(t, rng, C, 16, DY + 1)
    # the tents on the deck
    lt = tent(t, rng, 6, 9, 7, 6, DY + 1, axis='z', cloth='or:canvas_crimson', door_end='high')
    tent_furnish(t, rng, lt, DY + 1, chest=(9, 10, 'south'), beds=2, color='red')
    t2 = tent(t, rng, 21, 9, 5, 6, DY + 1, axis='z', door_end='high')
    tent_furnish(t, rng, t2, DY + 1, beds=1)
    t3 = tent(t, rng, 6, 19, 5, 6, DY + 1, axis='x', door_end='high', torn=0.25)
    tent_furnish(t, rng, t3, DY + 1, beds=1, color='gray')
    for (x, y, z) in ((9, DY + 5, 9), (23, DY + 4, 9), (6, DY + 4, 21)):
        if t.name_at(x, y - 1, z) == 'minecraft:dark_oak_fence':
            t.set(x, y, z, rod(oxidized=False))
    # the trophy post at the head of the landing; the spears; the butchery
    trophy_post(t, rng, C, 9, DY + 1)
    for k in range(3):
        spear(t, 12 + k, 8, DY + 1, 2 + k % 2)
    butchery(t, 18, 23, DY + 1)
    # the drying deck over the gangway: three racks of hides over the water
    for (x0, z0) in ((28, 19), (28, 22), (28, 25)):
        drying_rack(t, rng, x0, z0, 5, 'x', DY + 1)
    # the watch platform on four legs over the deck's back corner, its ladder, its rod
    wx0, wz0 = 22, 19
    for (x, z) in ((wx0, wz0), (wx0 + 3, wz0), (wx0, wz0 + 3), (wx0 + 3, wz0 + 3)):
        for y in range(DY + 1, DY + 8):
            t.set(x, y, z, log('minecraft:dark_oak_log'))
    hole = (wx0 + 1, wz0 + 2)
    for x in range(wx0, wx0 + 4):
        for z in range(wz0, wz0 + 4):
            edge = x in (wx0, wx0 + 3) or z in (wz0, wz0 + 3)
            t.set(x, DY + 8, z, 'minecraft:dark_oak_planks' if (x, z) != hole else AIR)
            t.set(x, DY + 9, z, S('dark_oak_fence') if edge else AIR)
            t.set(x, DY + 10, z, AIR)
    for y in range(DY + 1, DY + 8):                                 # the ladder post, the ladder up through the hole
        t.set(wx0 + 1, y, wz0 + 3, log('minecraft:dark_oak_log'))
    for y in range(DY + 1, DY + 9):
        t.set(hole[0], y, hole[1], S('ladder', facing='north', waterlogged=False))
    t.set(wx0 + 3, DY + 10, wz0, rod(oxidized=False))
    t.set(wx0 + 3, DY + 11, wz0, rod(oxidized=False))
    t.set(wx0, DY + 10, wz0 + 3, lantern(soul=False))
    for (x, z) in ((7, 16), (26, 16), (C, 25)):
        lamp_stake(t, x, z, DY + 1)
    for (x, z, h) in ((25, 12, 2), (25, 13, 1), (24, 14, 1), (8, 14, 1)):
        for k in range(h):
            t.set(x, DY + 1 + k, z, pick(rng, [('oldrepublic:cargo_crate', 2), (barrel('up'), 2)]))
    for (x, z) in [(x, 7) for x in range(8, 13)]:
        if rng.random() < 0.8:
            t.set(x, DY + 1, z, pick(rng, CLAN_IRON))
    finish(t, rng, deck, [], deck_y=DY)
    CAMERAS[tid] = ((C, DY + 2, 3), (C, DY + 6, 14))
    return t


def finish(t, rng, cells, soil, deck_y=None):
    sx, sy, sz = t.size
    walk_y = (deck_y if deck_y is not None else GY) + 1
    for (x, z) in sorted(cells):
        for y in range(walk_y, min(sy, walk_y + 7)):
            if (x, y, z) not in t.cells:
                t.set(x, y, z, AIR)
    for (x, z) in sorted(soil):
        if t.is_air(x, FY, z) and rng.random() < 0.14:
            t.set(x, FY, z, pick(rng, [('minecraft:short_grass', 3), ('minecraft:fern', 2), ('minecraft:dead_bush', 1)]))
    for (x, z) in sorted(cells):
        if t.is_air(x, walk_y, z) and t.is_solid(x, walk_y - 1, z) and rng.random() < 0.04:
            t.set(x, walk_y, z, pick(rng, [('minecraft:moss_carpet', 2), ('minecraft:leaf_litter[facing=north,segment_amount=3]', 2)]))
    hang_moss(t, rng, ((0, walk_y + 1, 0), (sx - 1, sy - 1, sz - 1)), chance=0.05, max_len=2)
    prune_floating(t)
    guard_trees(t, columns=set(cells))


def build(variant):
    seed = SALT * 10 + VARIANTS.index(variant) + 1
    t = camp_a(seed) if variant == 'a' else camp_b(seed)
    eye = CAMERAS[t.name][0]
    assert t.name_at(*eye) == 'minecraft:air', ('camera eye', t.name, eye, t.name_at(*eye))
    return t


def write_worldgen():
    els = [W.element(NAME + '/camp_' + v, adapted=dict(support_depth=10, clear_from=SIZE[1], clear_inset=0,
                                                       yard_stand=FY, ramp_rings=0)) for v in VARIANTS]
    W.pool(NAME + '/start', els)
    W.structure(NAME, NAME + '/start', 'has_structure/' + NAME, size=1, max_distance=32, start_height=1 - BURIED)
    W.structure_set(NAME, [(NAME, 1)], 40, 14, SALT, placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:tyrvann_stormwood', 'oldrepublic:tyrvann_mire'])
    loot_table('chests/tyrvann/hask_camp', [
        ((2, 4), [('oldrepublic:hollowhorn_hide', 5, 1, 3), ('oldrepublic:raw_hollowhorn', 4, 1, 3),
                  ('oldrepublic:cooked_hollowhorn', 3, 1, 2), ('leather', 3, 1, 3), ('string', 3, 1, 4),
                  ('iron_nugget', 3, 2, 6), ('oldrepublic:credit_chit', 3, 1, 3)]),
        (1, [('oldrepublic:hollowhorn_horn', 2, 1, 1), ('oldrepublic:clan_iron', 2, 1, 2), ('oldrepublic:medpac', 1, 1, 1),
             ('oldrepublic:credit_chit', 3, 2, 5), ('bone', 2, 2, 4)]),
    ])


def main(opts):
    ts = {}
    for v in VARIANTS:
        tid = NAME + '/camp_' + v
        t = build(v)
        ts[tid] = t
        if wanted(opts, tid):
            save(t, tid, opts, views=('se', 'nw'), plans=[GY, FY, DY + 1])
    write_worldgen()
    res = jigsaw.assemble(NAME + '/start', max_distance=32, size=1, start_height=1 - BURIED)
    if res.problems:
        print(res.report())
        raise SystemExit('jigsaw problems in %s' % NAME)
    return ts


if __name__ == '__main__':
    main(args())
