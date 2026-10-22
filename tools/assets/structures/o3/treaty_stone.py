"""wilds/treaty_stone: a weathered Old Republic monument where a treaty was signed (docs/MILESTONE_6.md 5.2, O3).

The story. Long before the frontier was settled, the Republic and the Sith Empire ended a war in this meadow
and raised a stone to the peace: a white stele in a colonnaded court, the two signatories cast in bronze on
either side of it. The peace did not last. The Republic's envoy was pulled down on his face; the Imperial
one was broken at the waist and his upper half thrown down behind him; the columns followed. Both sides
still send patrols to the stone, as if it still mattered who held it: a Republic banner and supply crates
on one side, an Imperial banner on the other, scorch marks where they last met. The country folk take no
side. They leave candles, flowers and a little something in the offering box on the stone's step.

Two start variants (the paving is layer 1, raised one block over the meadow, whose surface is layer 0):
  court   41 x 18 x 41  a square plaza, a broken peristyle round the stele on its stepped base, the two
          bronze signatories on their plinths either side (one face-down, one broken in half), a dry
          reflecting pool on the south approach, an oak grown through the paving, the patrols' camps.
  avenue  33 x 18 x 41  a processional way through a half-fallen triumphal arch, past the Republic's
          envoy lying full length across the road and the Imperial one broken on his plinth, to the stele
          in a colonnaded exedra; the patrols' camps at either side.

    python3 tools/assets/structures/o3/treaty_stone.py [--preview DIR] [--only court,avenue]
"""
import math

from common import (AIR, S, B, Template, mod, Noise, mix, pick, stairs, slab, log, campfire, candle,
                    report, save, only_names, write_worldgen, loot_table, item)

NAME = 'treaty_stone'
LOOT = 'chests/wilds/treaty_stone'
PILLAR = 'oldrepublic:ruin_pillar'
BURIED = 1         # layer 1 is where a player stands on the meadow; the paving is raised to it
SY = 18

GRASSES = [(S('short_grass'), 12), ('minecraft:dandelion', 1), ('minecraft:poppy', 1), ('minecraft:oxeye_daisy', 1),
           ('minecraft:azure_bluet', 1), (S('fern'), 2)]
BRONZE = [('minecraft:oxidized_copper', 5), ('minecraft:waxed_weathered_copper', 2)]
CUT = 'minecraft:oxidized_cut_copper'
HEAD = 'minecraft:oxidized_chiseled_copper'
HAND = S('oxidized_copper_grate', waterlogged=False)


def pave(t, rng, cells, field, seed, weathering=0.16):
    """Paving at layer 1 (layer 0 under it, so on falling ground the support columns hold it up), worn in
    patches where moss, gravel and grass have taken the joints and a few slabs are gone."""
    wf = mix([('minecraft:mossy_cobblestone', 3), ('minecraft:gravel', 2), ('minecraft:grass_block', 5),
              ('minecraft:coarse_dirt', 2), ('minecraft:moss_block', 2)], seed, 2.5)
    n = Noise(seed + 1, 7.0)
    for (x, z) in cells:
        t.set(x, 0, z, pick(rng, [('minecraft:stone', 3), ('minecraft:dirt', 2), ('minecraft:cobblestone', 1)]))
        patch = max(0.0, n.at(x, 0, z) - 0.4) * 2.5
        if rng.random() < weathering * (0.25 + 2.0 * patch):
            st = wf(x, 1, z)
            t.set(x, 1, z, st)
            if st == 'minecraft:grass_block' and rng.random() < 0.35:
                t.set(x, 2, z, pick(rng, GRASSES))
            elif st == 'minecraft:moss_block' and rng.random() < 0.3:
                t.set(x, 2, z, 'minecraft:moss_carpet')
        else:
            t.set(x, 1, z, field(x, z))
    for (x, z) in cells:
        if t.get(x, 2, z) is None:
            t.set(x, 2, z, AIR)


def clear_over(t, cells, y0=2):
    """Explicit air over paved cells up to the top: on a slope the plaza is cut level into the rising ground
    instead of being buried in it. Cells something already stands in are left alone."""
    for (x, z) in cells:
        for y in range(y0, t.sy):
            if t.get(x, y, z) is None:
                t.set(x, y, z, AIR)


def column(t, x, z, y0, height, intact=True):
    """A column standing on layer y0: a base block, a shaft of ruin pillars, a capital when intact, a broken
    stump otherwise. Returns its top layer."""
    t.set(x, y0, z, 'minecraft:polished_diorite')
    for y in range(y0 + 1, y0 + height):
        t.set(x, y, z, S(PILLAR, axis='y'))
    if intact:
        t.set(x, y0 + height, z, 'minecraft:chiseled_stone_bricks')
        return y0 + height
    t.set(x, y0 + height - 1, z, ('minecraft:cracked_stone_bricks', 'minecraft:mossy_stone_bricks')[(x + z) % 2])
    return y0 + height - 1


def fallen_column(t, rng, x, z, y, direction, length):
    """A column lying where it fell from its base at (x, z): drums of pillar on their sides with a gap, the
    capital at the end, a chip of debris beside it."""
    dx, dz = B.step(direction)
    t.set(x, y, z, 'minecraft:polished_diorite')
    ax = 'x' if dx else 'z'
    for i in range(1, length + 1):
        if i == length // 2 + 1 and length > 3:
            continue
        t.set(x + dx * i, y, z + dz * i, S(PILLAR, axis=ax) if i < length else 'minecraft:chiseled_stone_bricks')
    sx, sz = B.step(B.right_of(direction))
    t.set(x + dx * 2 + sx, y, z + dz * 2 + sz, 'oldrepublic:debris')


def architrave(t, a, b, y):
    """A lintel of quartz pillar between two column capitals on one line, at layer y."""
    (x0, z0), (x1, z1) = a, b
    ax = 'x' if z0 == z1 else 'z'
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            t.set(x, y, z, S('quartz_pillar', axis=ax))


def frame(cx, cz, facing):
    """Cell (x, z) at `a` to the right of and `b` ahead of (cx, cz), for a figure looking toward `facing`."""
    fx, fz = B.step(facing)
    rx, rz = B.step(B.right_of(facing))
    return lambda a, b: (cx + rx * a + fx * b, cz + rz * a + fz * b)


def plinth(t, cx, cz, y, size=5, height=2):
    """A square plinth on layer y, a step of stairs round its foot; held up from layer 0 when it stands on
    the meadow. Returns the first layer above it."""
    h = size // 2
    for x in range(cx - h - 1, cx + h + 2):
        for z in range(cz - h - 1, cz + h + 2):
            for yy in range(0, y):
                if t.get(x, yy, z) is None:
                    t.set(x, yy, z, 'minecraft:stone')
            if abs(x - cx) <= h and abs(z - cz) <= h:
                corner = abs(x - cx) == h and abs(z - cz) == h
                for yy in range(y, y + height):
                    t.set(x, yy, z, 'minecraft:chiseled_stone_bricks' if corner and yy == y + height - 1
                          else 'minecraft:polished_andesite')
            elif abs(x - cx) == h + 1 and abs(z - cz) == h + 1:
                t.set(x, y, z, slab('polished_andesite_slab', 'bottom'))
            else:
                f = 'east' if x < cx - h else 'west' if x > cx + h else 'south' if z < cz - h else 'north'
                t.set(x, y, z, stairs('polished_andesite_stairs', f))
    return y + height


def lay(t, rng, cells, ground):
    """Bronze pieces lying on the ground: cells [(x, z, state, raised)] put at ground(x, z) + raised; a piece
    off the paving gets the meadow block under it so it never hangs over a dip."""
    for (x, z, st, up) in cells:
        g = ground(x, z)
        if g == 1 and t.get(x, 0, z) is None:
            t.set(x, 0, z, 'minecraft:grass_block')
        t.set(x, g + up, z, st if st != 'bronze' else pick(rng, BRONZE))


def statue_fallen(t, rng, cx, cz, y, facing, ground, staff=True):
    """A signatory pulled down on his face: two broken ankles on the plinth (top layer y - 1), the figure full
    length in front of it (robe, body, shoulders, head, one arm along his side, the other snapped off and
    lying apart), his staff of office beside him."""
    at = frame(cx, cz, facing)
    for a in (-1, 1):
        x, z = at(a, 0)
        t.set(x, y, z, S('oxidized_cut_copper_slab', type='bottom', waterlogged=False))
    cells = []
    for b in (4, 5):
        cells += [at(a, b) + (CUT, 0) for a in (-1, 0, 1)]
    for b in (6, 7, 8):
        cells += [at(a, b) + ('bronze', 0) for a in (-1, 0, 1)]
    for b in (7, 8):
        cells.append(at(0, b) + (S('oxidized_cut_copper_slab', type='bottom', waterlogged=False), 1))
    cells += [at(a, 9) + ('bronze', 0) for a in (-2, -1, 0, 1, 2)]
    cells.append(at(0, 10) + (HEAD, 0))
    cells += [at(2, b) + ('bronze', 0) for b in (6, 7, 8)] + [at(2, 5) + (HAND, 0)]
    cells += [at(-2, 8) + (CUT, 0), at(-3, 4) + (HAND, 0), at(-3, 5) + ('bronze', 0)]
    if staff:
        for b in (8, 9, 10):
            x, z = at(3, b)
            cells.append((x, z, S('oxidized_lightning_rod', facing=facing, powered=False, waterlogged=False), 0))
    lay(t, rng, cells, ground)


def statue_broken(t, rng, cx, cz, y, facing, ground):
    """A signatory broken at the waist: robe and legs still standing on the plinth (from layer y), the upper
    half thrown down behind him (shoulders, head, an arm), the other arm further off."""
    at = frame(cx, cz, facing)
    for a in (-1, 0, 1):
        for b in (-1, 0):
            x, z = at(a, b)
            t.set(x, y, z, CUT)
        x, z = at(a, 0)
        t.set(x, y + 1, z, pick(rng, BRONZE))
        t.set(x, y + 2, z, pick(rng, BRONZE))
    x, z = at(0, -1)
    t.set(x, y + 1, z, S('oxidized_cut_copper_stairs', facing=facing, half='bottom', shape='straight',
                         waterlogged=False))
    x, z = at(-1, 0)
    t.set(x, y + 3, z, pick(rng, BRONZE))
    x, z = at(0, 0)
    t.set(x, y + 3, z, S('oxidized_cut_copper_stairs', facing=B.right_of(facing), half='bottom', shape='straight',
                         waterlogged=False))
    cells = []
    cells += [at(a, -4) + (CUT, 0) for a in (-1, 0, 1)]
    for b in (-5, -6):
        cells += [at(a, b) + ('bronze', 0) for a in (-1, 0, 1)]
        cells.append(at(0, b) + (S('oxidized_cut_copper_slab', type='bottom', waterlogged=False), 1))
    cells += [at(a, -7) + ('bronze', 0) for a in (-2, -1, 0, 1, 2)]
    cells.append(at(0, -8) + (HEAD, 0))
    cells += [at(2, -5) + ('bronze', 0), at(2, -6) + ('bronze', 0), at(2, -4) + (HAND, 0)]
    cells += [at(-4, -5) + ('bronze', 0), at(-4, -6) + (CUT, 0), at(-4, -4) + (HAND, 0)]
    lay(t, rng, cells, ground)


def empty_plinth(t, cx, cz, y, facing):
    """A plinth whose figure was carted off: only the feet are left."""
    at = frame(cx, cz, facing)
    for a in (-1, 1):
        x, z = at(a, 0)
        t.set(x, y, z, S('oxidized_cut_copper_slab', type='bottom', waterlogged=False))


def stele(t, cx, cz, y0):
    """The treaty stone on a stepped base (7 x 7, 5 x 5 and 3 x 3 from layer y0): a white monolith three wide
    and one thick with a carved band, under a capstone. Returns its top layer."""
    for (r, y, st) in ((3, y0, 'minecraft:polished_andesite'), (2, y0 + 1, 'minecraft:polished_diorite'),
                       (1, y0 + 2, 'minecraft:smooth_quartz')):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                t.set(x, y, z, st)
    base = y0 + 3
    for y in range(base, base + 9):
        for x in (cx - 1, cx, cx + 1):
            st = 'minecraft:calcite' if x == cx else 'minecraft:smooth_quartz'
            if y == base + 6:
                st = 'minecraft:chiseled_quartz_block'
            t.set(x, y, cz, st)
    top = base + 9
    t.set(cx - 1, top, cz, stairs('smooth_quartz_stairs', 'east'))
    t.set(cx + 1, top, cz, stairs('smooth_quartz_stairs', 'west'))
    t.set(cx, top, cz, 'minecraft:quartz_block')
    t.set(cx, top + 1, cz, slab('smooth_quartz_slab', 'bottom'))
    return top + 1


def offerings(t, rng, cx, cz, y0):
    """Candles (some still burning), pots of flowers and a decorated pot on the stele's front steps (south),
    the offering box in the middle of the lowest step."""
    t.chest(cx, y0 + 1, cz + 3, facing='south', loot=LOOT)
    low = [(cx - 3, S('decorated_pot', facing='north', cracked=True, waterlogged=False)),
           (cx - 2, candle('white', 3, True)), (cx - 1, 'minecraft:potted_poppy'),
           (cx + 1, candle('yellow', 2, True)), (cx + 2, 'minecraft:potted_cornflower'), (cx + 3, candle(None, 2, False))]
    for x, st in low:
        t.set(x, y0 + 1, cz + 3, st)
    for x, st in ((cx - 2, candle('white', 1, True)), (cx, 'minecraft:potted_oxeye_daisy'), (cx + 2, candle('white', 2, False))):
        t.set(x, y0 + 2, cz + 2, st)


def banner(t, x, y, z, base, patterns=(), facing='south'):
    mod.banner(t, x, y, z, facing, base=base, patterns=patterns, wall=False)


def patrol(t, rng, x, z, side, facing):
    """A patrol's stop by the stone, on a trodden pad set at layer 0 (on falling ground it is held up like the
    paving, on rising ground the camp is cut level): a standing banner, supply crates, a cold fire with a log
    to sit on, a bedroll, scorch marks."""
    for xx in range(x - 1, x + 4):
        for zz in range(z - 1, z + 4):
            if xx in (x - 1, x + 3) and zz in (z - 1, z + 3):
                continue
            near = abs(xx - (x + 1)) + abs(zz - (z + 2)) <= 1
            t.set(xx, 0, zz, 'minecraft:coarse_dirt' if near or rng.random() < 0.25 else
                  'minecraft:dirt_path' if rng.random() < 0.25 else 'minecraft:grass_block')
            for y in range(1, 5):
                if t.get(xx, y, zz) is None:
                    t.set(xx, y, zz, AIR)
    if side == 'republic':
        banner(t, x, 1, z, 'blue', [('oldrepublic:republic_chevron', 'white')], facing)
        crate = 'oldrepublic:cargo_crate_republic'
        roll = 'minecraft:blue_carpet'
    else:
        banner(t, x, 1, z, 'red', [], facing)
        crate = 'oldrepublic:cargo_crate_imperial'
        roll = 'minecraft:black_carpet'
    t.set(x + 1, 1, z, crate)
    t.set(x + 1, 2, z, crate)
    t.set(x + 2, 1, z, crate)
    t.set(x + 1, 1, z + 2, campfire('north', lit=False))
    t.set(x, 1, z + 2, log('stripped_oak_log', 'z'))
    t.set(x + 3, 1, z + 1, roll)
    t.set(x + 3, 1, z + 2, roll)
    for (dx, dz) in ((-1, 3), (2, 3), (3, -1)):
        if t.get(x + dx, 0, z + dz) is not None:
            t.set(x + dx, 1, z + dz, 'oldrepublic:scorch_mark')


def oak(t, rng, x, z, y0, height=6, seed=5604):
    """An old oak grown up through the paving or the meadow: a trunk from layer 0, the slabs round it heaved
    up, three boughs and a broad, ragged crown."""
    for y in range(0, y0 + height):
        t.set(x, y, z, log('oak_log'))
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set(x + dx, y0 - 1, z + dz, 'minecraft:rooted_dirt' if rng.random() < 0.6 else 'minecraft:coarse_dirt')
        if y0 >= 2 and rng.random() < 0.5:
            t.set(x + dx, y0, z + dz, slab('polished_andesite_slab', 'bottom'))
    top = y0 + height
    leaves = S('oak_leaves', distance=1, persistent=True, waterlogged=False)
    n = Noise(seed, 1.7)
    ends = []
    for k, (dx, dz) in enumerate(((2, 1), (-2, 1), (0, -2))):
        y = top - 1 + k % 2
        for i in (1, 2):
            bx, bz = x + dx * i // 2, z + dz * i // 2
            t.set(bx, y + i - 1, bz, log('oak_log', 'x' if abs(dx) > abs(dz) else 'z'))
        ends.append((x + dx, y + 1, z + dz))
    ends.append((x, top + 1, z))
    for (ex, ey, ez) in ends:
        for xx in range(ex - 3, ex + 4):
            for zz in range(ez - 3, ez + 4):
                for yy in range(ey - 1, ey + 3):
                    d = math.sqrt((xx - ex) ** 2 + (zz - ez) ** 2 + ((yy - ey - 0.5) * 1.8) ** 2)
                    if d < 2.9 + 0.9 * (n.at(xx, yy, zz) - 0.5) and t.inside(xx, yy, zz) and t.get(xx, yy, zz) in (None, AIR):
                        t.set(xx, yy, zz, leaves)
    for y in range(y0, top + 1):
        t.set(x, y, z, log('oak_log'))


# ================================================================ variant court

def build_court():
    sx, sz = 41, 41
    t = Template(sx, SY, sz, seed=960156, name='court')
    rng = t.rng
    c = 20
    tiles = mix([('oldrepublic:pale_tiles', 6), ('minecraft:calcite', 2), ('minecraft:smooth_quartz', 1)], 5603, 3.0)

    def field(x, z):
        ring = max(abs(x - c), abs(z - c))
        if ring >= 13:
            return 'minecraft:polished_andesite' if (x + z) % 2 else 'minecraft:stone_bricks'
        if ring in (6, 7):
            return 'minecraft:polished_diorite'
        if abs(x - c) <= 1 or abs(z - c) <= 1:
            return 'oldrepublic:ruin_bricks'
        return tiles(x, 1, z)
    cells = [(x, z) for x in range(c - 14, c + 15) for z in range(c - 14, c + 15)]
    pave(t, rng, cells, field, 5601)
    steps = []
    for i in range(-3, 4):
        for (x, z, f) in ((c + i, c - 15, 'south'), (c + i, c + 15, 'north'), (c - 15, c + i, 'east'),
                          (c + 15, c + i, 'west')):
            t.set(x, 1, z, stairs('stone_brick_stairs', f))
            t.set(x, 0, z, 'minecraft:stone')
            steps.append((x, z))

    # ---- the stele on its stepped base, the offerings and the offering box on its front step
    sc = c - 1
    stele(t, c, sc, 2)
    offerings(t, rng, c, sc, 2)
    mod.spot(t, 'HEART', c, 2, c + 4)

    # ---- the peristyle: twelve columns on a 13 x 13 square, open in the middle of each side
    fate = {(c - 6, c - 6): 'intact', (c - 3, c - 6): 'intact', (c + 3, c - 6): ('broken', 2), (c + 6, c - 6): ('fallen', 'north'),
            (c - 6, c - 3): 'intact', (c + 6, c - 3): 'intact', (c - 6, c + 3): ('broken', 4), (c + 6, c + 3): ('fallen', 'west'),
            (c - 6, c + 6): 'intact', (c - 3, c + 6): 'intact', (c + 3, c + 6): 'intact', (c + 6, c + 6): ('broken', 3)}
    tops = {}
    for (x, z), f in sorted(fate.items()):
        if f == 'intact':
            tops[(x, z)] = column(t, x, z, 2, 7)
        elif f[0] == 'broken':
            column(t, x, z, 2, f[1], intact=False)
        else:
            fallen_column(t, rng, x, z, 2, f[1], 4 if f[1] == 'west' else 5)
    for a, b in (((c - 6, c - 6), (c - 6, c - 3)), ((c - 6, c - 6), (c - 3, c - 6)), ((c - 6, c + 6), (c - 3, c + 6)),
                 ((c - 3, c + 6), (c + 3, c + 6))):
        architrave(t, a, b, tops[a] + 1)
    # cold braziers in the court's corners, benches facing the stone
    for (x, z) in ((c - 5, c - 5), (c + 5, c - 5), (c - 5, c + 5), (c + 5, c + 5)):
        t.set(x, 2, z, S('stone_brick_wall', up=True, north='none', south='none', east='none', west='none',
                         waterlogged=False))
        t.set(x, 3, z, campfire('north', lit=False))
    for (x, z, f) in ((c - 5, c - 2, 'west'), (c - 5, c, 'west'), (c + 5, c - 2, 'east'), (c + 5, c, 'east')):
        t.set(x, 2, z, stairs('polished_diorite_stairs', f))

    # ---- the dry reflecting pool on the south approach
    for x in range(c - 4, c + 5):
        for z in range(c + 8, c + 12):
            if x in (c - 4, c + 4) or z in (c + 8, c + 11):
                t.set(x, 1, z, 'minecraft:smooth_stone')
                continue
            t.set(x, 1, z, AIR)
            t.set(x, 0, z, pick(rng, [('minecraft:mossy_stone_bricks', 4), ('minecraft:cracked_stone_bricks', 2),
                                      ('minecraft:coarse_dirt', 1), ('minecraft:moss_block', 1)]))
            r = rng.random()
            if r < 0.3:
                t.set(x, 1, z, S('leaf_litter', facing=rng.choice(('north', 'east', 'south', 'west')),
                                 segment_amount=rng.randint(1, 4)))
            elif r < 0.4:
                t.set(x, 1, z, 'minecraft:dead_bush')

    # ---- the signatories: the Republic's envoy face-down, the Imperial one broken in half
    on_plaza = lambda x, z: 2 if (abs(x - c) <= 14 and abs(z - c) <= 14) else 1   # noqa: E731
    top = plinth(t, c - 10, sc, 2)
    statue_fallen(t, rng, c - 10, sc, top, 'south', on_plaza)
    top = plinth(t, c + 10, sc, 2)
    statue_broken(t, rng, c + 10, sc, top, 'south', on_plaza)

    # an old oak has broken through the paving in the north-west corner
    oak(t, rng, c - 11, c - 11, 2, height=6)

    # ---- the patrols' camps outside the plaza, scorch marks across it, a surveyor's flag
    patrol(t, rng, c + 17, c - 12, 'republic', 'west')
    patrol(t, rng, c - 18, c + 9, 'empire', 'east')
    for _ in range(8):
        x, z = rng.randint(c - 13, c + 13), rng.randint(c - 13, c + 13)
        if t.get(x, 2, z) == AIR and t.get(x, 1, z) not in (None, AIR):
            t.set(x, 2, z, 'oldrepublic:scorch_mark')
    for _ in range(5):
        x, z = rng.randint(c - 13, c + 13), rng.randint(c - 13, c + 13)
        if t.get(x, 2, z) == AIR and t.get(x, 1, z) not in (None, AIR):
            t.set(x, 2, z, 'oldrepublic:debris')
    banner(t, c + 13, 2, c + 13, 'white', [], 'north')
    clear_over(t, cells + steps)
    return t


# ================================================================ variant avenue

def build_avenue():
    sx, sz = 33, 41
    t = Template(sx, SY, sz, seed=960157, name='avenue')
    rng = t.rng
    c = 16
    tiles = mix([('oldrepublic:pale_tiles', 6), ('minecraft:calcite', 2), ('minecraft:smooth_quartz', 1)], 5605, 3.0)

    def field(x, z):
        if abs(x - c) == 3:
            return 'minecraft:polished_andesite'
        if z % 5 == 0 or x == c:
            return 'oldrepublic:ruin_bricks'
        return tiles(x, 1, z)

    def court_field(x, z):
        if x in (c - 8, c + 8) or z in (1, 12):
            return 'minecraft:stone_bricks'
        if abs(x - c) <= 1:
            return 'oldrepublic:ruin_bricks'
        return tiles(x, 1, z)
    road = [(x, z) for x in range(c - 3, c + 4) for z in range(13, 39)]
    court = [(x, z) for x in range(c - 8, c + 9) for z in range(1, 13)]
    pave(t, rng, road, field, 5606)
    pave(t, rng, court, court_field, 5608)
    steps = [(c + i, 39) for i in range(-3, 4)]
    for (x, z) in steps:
        t.set(x, 1, z, stairs('stone_brick_stairs', 'north'))
        t.set(x, 0, z, 'minecraft:stone')
    paved = set(road) | set(court)
    on_road = lambda x, z: 2 if (x, z) in paved else 1   # noqa: E731

    # ---- the exedra: a bench along three sides, columns before it, the stele in the middle
    for x in range(c - 8, c + 9):
        t.set(x, 2, 1, stairs('polished_diorite_stairs', 'north'))
    for z in range(2, 12):
        t.set(c - 8, 2, z, stairs('polished_diorite_stairs', 'west'))
        t.set(c + 8, 2, z, stairs('polished_diorite_stairs', 'east'))
    fate = {(c - 6, 2): 'intact', (c - 3, 2): 'intact', (c + 3, 2): ('broken', 3), (c + 6, 2): 'intact',
            (c - 7, 5): 'intact', (c - 7, 8): ('broken', 2), (c - 7, 11): 'intact',
            (c + 7, 5): 'intact', (c + 7, 8): ('fallen', 'west'), (c + 7, 11): ('broken', 4)}
    tops = {}
    for (x, z), f in sorted(fate.items()):
        if f == 'intact':
            tops[(x, z)] = column(t, x, z, 2, 7)
        elif f[0] == 'broken':
            column(t, x, z, 2, f[1], intact=False)
        else:
            fallen_column(t, rng, x, z, 2, f[1], 3)
    architrave(t, (c - 6, 2), (c - 3, 2), tops[(c - 6, 2)] + 1)
    sc = 7
    stele(t, c, sc, 2)
    offerings(t, rng, c, sc, 2)
    mod.spot(t, 'HEART', c, 2, 12)
    for (x, z) in ((c - 5, 11), (c + 5, 11)):
        t.set(x, 2, z, S('stone_brick_wall', up=True, north='none', south='none', east='none', west='none',
                         waterlogged=False))
        t.set(x, 3, z, campfire('north', lit=False))

    # ---- the triumphal arch across the road (z 28 .. 30): the east pier whole with the springing of the
    #      span, the west pier broken off at half height, the fallen half of the span heaped round its foot
    az0, az1 = 28, 30
    for (x0, x1, h) in ((c - 7, c - 4, 7), (c + 4, c + 7, 12)):
        for x in range(x0, x1 + 1):
            for z in range(az0, az1 + 1):
                t.set(x, 0, z, 'minecraft:stone')
                for y in range(1, h + 1):
                    edge = x in (x0, x1) or z in (az0, az1)
                    st = 'oldrepublic:ruin_bricks' if edge else 'minecraft:stone_bricks'
                    if y == 1:
                        st = 'minecraft:polished_andesite'
                    elif y in (5, 9) and edge:
                        st = 'minecraft:smooth_quartz'
                    t.set(x, y, z, st)
    for z in range(az0, az1 + 1):
        # the broken top of the west pier
        for (x, y, st) in ((c - 7, 7, None), (c - 6, 7, 'minecraft:cracked_stone_bricks'), (c - 4, 7, None),
                           (c - 4, 6, 'minecraft:cracked_stone_bricks')):
            if z != az0 or x != c - 6:
                t.set(x, y, z, st if st else AIR)
        # the surviving half of the span, springing from the east pier
        t.set(c + 3, 8, z, stairs('stone_brick_stairs', 'east', 'top'))
        t.set(c + 3, 9, z, 'oldrepublic:ruin_bricks')
        t.set(c + 2, 9, z, stairs('stone_brick_stairs', 'east', 'top'))
        for x in range(c, c + 4):
            t.set(x, 10, z, 'oldrepublic:ruin_bricks')
            t.set(x, 11, z, 'oldrepublic:ruin_bricks' if x > c else 'minecraft:cracked_stone_bricks')
            t.set(x, 12, z, 'minecraft:smooth_quartz' if x > c else AIR)
        for x in range(c + 4, c + 8):
            t.set(x, 13, z, slab('smooth_quartz_slab', 'bottom'))
    t.set(c - 1, 11, az0 + 1, 'oldrepublic:ruin_bricks')
    rubble = [('oldrepublic:ruin_bricks', 4), ('minecraft:cracked_stone_bricks', 3), ('minecraft:cobblestone', 2),
              ('minecraft:smooth_quartz', 1)]
    for (x, z, h) in ((c - 3, 27, 2), (c - 2, 27, 1), (c - 3, 31, 1), (c - 2, 29, 1), (c - 1, 28, 1), (c - 3, 26, 1),
                      (c - 8, 27, 1), (c - 8, 31, 2), (c - 9, 30, 1), (c - 1, 31, 1)):
        g = on_road(x, z)
        if g == 1 and t.get(x, 0, z) is None:
            t.set(x, 0, z, 'minecraft:grass_block')
        for y in range(g, g + h):
            t.set(x, y, z, pick(rng, rubble))
    t.set(c - 2, 2, 25, 'oldrepublic:debris')
    t.set(c + 1, 2, 32, 'oldrepublic:debris')

    # ---- the signatories beside the road: the Republic's envoy pulled down across it, the Imperial one broken
    #      on his plinth facing him; further down an empty plinth, its figure carted off
    top = plinth(t, c - 7, 19, 1)
    statue_fallen(t, rng, c - 7, 19, top, 'east', on_road)
    top = plinth(t, c + 7, 19, 1)
    statue_broken(t, rng, c + 7, 19, top, 'west', on_road)
    top = plinth(t, c - 7, 36, 1)
    empty_plinth(t, c - 7, 36, top, 'east')

    # ---- the patrols' camps, scorch marks on the road, an old oak by the lower plinth
    patrol(t, rng, c + 11, 26, 'republic', 'west')
    patrol(t, rng, c - 13, 6, 'empire', 'east')
    for _ in range(7):
        x, z = rng.randint(c - 3, c + 3), rng.randint(13, 38)
        if t.get(x, 2, z) == AIR and t.get(x, 1, z) not in (None, AIR):
            t.set(x, 2, z, 'oldrepublic:scorch_mark')
    oak(t, rng, c + 9, 36, 1, height=6, seed=5607)
    clear_over(t, road + court + steps)
    return t


# ================================================================ worldgen, loot

def write_loot():
    loot_table(LOOT, [
        ((2, 3), [item('paper', 4, 2, 5), item('book', 2), item('emerald', 2, 1, 2), item('gold_nugget', 4, 2, 6),
                  item('candle', 4, 1, 3), item('amethyst_shard', 2, 1, 2), item('oldrepublic:lumen_thread', 1, 1, 1)]),
    ])


BUILDERS = [('court', build_court), ('avenue', build_avenue)]


def main():
    only = only_names()
    write_loot()
    for name, fn in BUILDERS:
        if only and name not in only:
            continue
        t = fn()
        save(t, 'wilds/%s/%s' % (NAME, name), views=('se', 'nw'))
        report(t, 'wilds/%s/%s' % (NAME, name))
    write_worldgen(NAME, [(name, 1) for name, _ in BUILDERS], buried=BURIED, spacing=56, separation=22, salt=960153,
                   biomes=['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:meadow',
                           'minecraft:cherry_grove'],
                   adapted=dict(yard_stand=BURIED + 1, clear_from=SY, clear_inset=6, support_depth=8, ramp_rings=2),
                   max_distance=64)


if __name__ == '__main__':
    main()
