"""oldrepublic:wilds/war_scar: an old battlefield of the war, gone quiet under the grass.

Run: python3 tools/assets/structures/o2/war_scar.py [--preview DIR]

Two start variants, both 61 x 24 x 61 and buried 4 (the field's top block at template y 3, feet at
y 4). The field is levelled into the ground (earth below, turf on top, ragged at the rim where the
world's own ground takes over); trenches, pits and craters are dug into it.

* wilds/war_scar/walker_line: an Imperial assault walker lies on its side where it fell, its head a
  few strides short of the Republic trench line it was crossing. Its footprints come in from the
  north; one upper leg still juts out over the grass, the other lies snapped off beside it, and you
  can climb through the torn flank into its troop bay (a crew locker among the tumbled crates; its
  red running light still glows). In front of the trench the barricades it trampled, wire and
  anti-armour hedgehogs; the trench itself zig-zags across the field with sandbags, duckboards and
  fire steps, a turret emplacement at each end (one blasted to a stump, one still aimed at the
  walker's head) and the command dugout with the officer's footlocker. Behind the line the memorial:
  a cairn under a tattered Republic banner and two rows of rifles planted in the ground among poppies.
* wilds/war_scar/gunship_battery: an Imperial artillery battery, three gun pits joined by zig-zag
  trenches, and the Republic gunship that came down on it: a long gouge from the east, the gunship
  nose-down in its own spoil heap with its tail in the air, its south wing torn off and lying across
  the third gun pit. One gun still points at the sky, one is a wreck beside its crater; the
  battery's stores are in the half-sunk ammunition bunker. The gunship's troop bay is open at the
  side door; north of it the crew's graves, the wing's flap stood up as their headstone.

Craters (some rain-filled), scorched ground with scorch marks and debris, and grass and poppies over
everything. Wreck plating and salvage blocks can be cut with the fusion cutter.

Worldgen: the structure (no monsters), its start pool, the set (spacing 56, separation 22, salt
960105, 4 chunks from the ruins), the biome tag and the loot table chests/wilds/war_scar.
"""
import math

from common import (Template, S, AIR, B, Noise, pick, patches, stairs, slab, trapdoor, lantern, barrel, bed,
                    wall_banner, standing_banner, light_rooms, adapted, wilds_family, loot_item, loot_table, cli, save,
                    ring_of, rod, beam, pipes, carve, weapon_rack, HORIZONTAL)

SX, SY, SZ = 61, 24, 61
BURIED = 4
G = 4        # feet on the field
SURF = 3     # the field's top block
LOOT = 'chests/wilds/war_scar'

FILL = [('dirt', 7), ('coarse_dirt', 2), ('gravel', 1)]
TURF = [('grass_block[snowy=false]', 16), ('coarse_dirt', 3), ('dirt', 1), ('rooted_dirt', 1), ('gravel', 1)]
BURNT = [('coarse_dirt', 5), ('gravel', 2), ('dirt', 2), ('blackstone', 1), ('tuff', 1)]
SANDBAG = [(S('packed_mud'), 5), (S('mud_bricks'), 2)]
SANDBAG_TOP = [(S('packed_mud'), 3), (S('mud_bricks'), 1), (slab('mud_brick_slab'), 3)]
WALKER = [('or:imperial_plating', 6), ('or:wreck_plating', 3), ('or:wreck_plating_scorched', 3), ('or:durasteel_plating', 1)]
GUNSHIP = [('white_concrete', 5), ('or:republic_plating', 4), ('or:wreck_plating', 3), ('or:wreck_plating_scorched', 2)]
FERRO = [('polished_deepslate', 4), ('deepslate_tiles', 3), ('cracked_deepslate_tiles', 2)]
TURF_NAMES = ('minecraft:grass_block', 'minecraft:coarse_dirt', 'minecraft:dirt', 'minecraft:rooted_dirt', 'minecraft:gravel')
FILL_NAMES = ('minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:gravel')
SIX = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
RODS = ['waxed_exposed_lightning_rod', 'waxed_weathered_lightning_rod', 'waxed_oxidized_lightning_rod']


def empty(t, x, y, z):
    s = t.get(x, y, z)
    return s is None or B.name_of(s) == 'minecraft:air'


def ground_ok(t, x, z):
    """The field's own top block is still there (not dug, not outside the field)."""
    return t.name_at(x, SURF, z) in TURF_NAMES


def old_rod(rng, facing='up'):
    return S(rng.choice(RODS), facing=facing, powered=False, waterlogged=False)


# ---------------------------------------------------------------- the field

def field(t, seed):
    """Earth to y 2 and turf at y 3 over the footprint, ragged in the outer three rings."""
    top = patches(seed, TURF, 4.0)
    fill = patches(seed + 1, FILL, 3.0)
    n = Noise(seed + 2, 3.0)
    for x in range(SX):
        for z in range(SZ):
            r = ring_of(t, x, z)
            if r < 3 and n.at(x, 0, z) < (3 - r) * 0.25:
                continue
            for y in range(0, SURF):
                t.set(x, y, z, fill(x, y, z))
            t.set(x, SURF, z, top(x, SURF, z))


def dig(t, x, z, floor_y, floor):
    """Dig one column of the field down to a floor block at floor_y (air above it to the top block)."""
    if t.get(x, SURF, z) is None:
        return
    t.set(x, floor_y, z, floor)
    for y in range(floor_y + 1, SURF + 1):
        t.set(x, y, z, AIR)


def crater(t, rng, cx, cz, r, depth=2, water=False):
    """A shell crater: a bowl dug up to `depth` deep, a thrown-up rim, rain water in the deepest
    level when `water` (the whole deepest level, so it cannot run)."""
    deep = []
    for x in range(int(cx - r) - 2, int(cx + r) + 3):
        for z in range(int(cz - r) - 2, int(cz + r) + 3):
            if not t.inside(x, 0, z) or not ground_ok(t, x, z):
                continue
            d = math.hypot(x - cx, z - cz)
            if d <= r:
                k = max(1, min(depth, int(depth * (1 - (d / r) ** 2) + 0.75)))
                dig(t, x, z, SURF - k, pick(rng, [(S('coarse_dirt'), 4), (S('gravel'), 3), (S('mud'), 1), (S('blackstone'), 1)]))
                if k == depth:
                    deep.append((x, SURF - k + 1, z))
            elif d <= r + 1.3 and rng.random() < 0.7 and t.get(x, G, z) is None:
                t.set(x, G, z, pick(rng, [(S('coarse_dirt'), 3), (S('dirt'), 2), (S('gravel'), 1)]))
    if water and len(deep) >= 2:
        for c in deep:
            t.set(*c, S('water', level=0))
    return deep


def burn(t, rng, cx, cz, r, seed, decal=0.3):
    """A scorched patch round (cx, cz): burnt ground in the turf, scorch decals, a little debris."""
    n = Noise(seed, 2.5)
    pal = patches(seed + 1, BURNT, 2.0)
    for x in range(int(cx - r) - 2, int(cx + r) + 3):
        for z in range(int(cz - r) - 2, int(cz + r) + 3):
            if not t.inside(x, 0, z):
                continue
            d = math.hypot(x - cx, z - cz) / r
            if d > 0.7 + 0.5 * n.at(x, 0, z) or not ground_ok(t, x, z):
                continue
            t.set(x, SURF, z, pal(x, SURF, z))
            if t.get(x, G, z) is None:
                roll = rng.random()
                if roll < decal * (1.2 - d):
                    t.set(x, G, z, S('or:scorch_mark'))
                elif roll > 0.975:
                    t.set(x, G, z, S('or:debris'))


def grow(t, rng, density=0.3, flowers=0.35):
    """Grass and flowers over the field's turf, tall grass here and there."""
    small = [(S('short_grass'), 14), (S('poppy'), 2 * flowers), (S('cornflower'), 1 * flowers),
             (S('oxeye_daisy'), 1 * flowers), (S('dandelion'), 1 * flowers), (S('bush'), 1)]
    for x in range(SX):
        for z in range(SZ):
            if t.name_at(x, SURF, z) != 'minecraft:grass_block' or t.get(x, G, z) is not None:
                continue
            roll = rng.random()
            if roll < density * 0.12 and t.get(x, G + 1, z) is None:
                t.set(x, G, z, S('tall_grass', half='lower'))
                t.set(x, G + 1, z, S('tall_grass', half='upper'))
            elif roll < density:
                t.set(x, G, z, pick(rng, small))


def poppies(t, rng, cx, cz, r, p=0.5):
    for x in range(int(cx - r), int(cx + r) + 1):
        for z in range(int(cz - r), int(cz + r) + 1):
            if math.hypot(x - cx, z - cz) <= r and t.name_at(x, SURF, z) == 'minecraft:grass_block' \
                    and t.get(x, G, z) is None and rng.random() < p:
                t.set(x, G, z, S('poppy') if rng.random() < 0.8 else S('cornflower'))


# ---------------------------------------------------------------- trenches, wire, barricades

def trench(t, rng, cells, front='north', steps=5):
    """A trench through the cells (x, z): floor at y 1 with duckboards, plank-and-post revetment,
    sandbags along the front lip, spoil along the rear, fire steps against the front wall and, at
    intervals, a stepped way out over the rear lip."""
    cells = set(cells)
    back = B.opposite(front)
    for (x, z) in sorted(cells):
        if t.get(x, SURF, z) is None:
            continue
        t.set(x, 1, z, pick(rng, [(S('packed_mud'), 3), (S('mud'), 1), (S('coarse_dirt'), 2)]))
        t.set(x, 2, z, trapdoor('spruce_trapdoor', 'north') if (x + z) % 2 == 0 and rng.random() < 0.75 else AIR)
        t.set(x, 3, z, AIR)
    n_front = 0
    n_back = 0
    for (x, z) in sorted(cells):
        if t.get(x, 1, z) is None:
            continue
        for d in HORIZONTAL:
            dx, dz = B.step(d)
            nx, nz = x + dx, z + dz
            if (nx, nz) in cells or not t.inside(nx, 0, nz):
                continue
            if t.name_at(nx, 2, nz) in FILL_NAMES:
                post = (nx + nz) % 3 == 0
                for y in (2, 3):
                    if post:
                        t.set(nx, y, nz, S('stripped_spruce_log', axis='y'))
                    elif rng.random() < 0.8:
                        t.set(nx, y, nz, S('spruce_planks'))
            if not ground_ok(t, nx, nz) and t.name_at(nx, SURF, nz) not in ('minecraft:spruce_planks', 'minecraft:stripped_spruce_log'):
                continue
            if d == front and t.get(nx, G, nz) is None:
                t.set(nx, G, nz, pick(rng, SANDBAG_TOP))
                if rng.random() < 0.3 and t.get(nx, G + 1, nz) is None and B.name_of(t.get(nx, G, nz)) != 'minecraft:mud_brick_slab':
                    t.set(nx, G + 1, nz, slab('mud_brick_slab'))
                n_front += 1
                if n_front % steps == 0:
                    t.set(x, 2, z, S('packed_mud'))  # a fire step against the front wall
            elif d == back and t.get(nx, G, nz) is None:
                n_back += 1
                if n_back % (steps * 3) == 2:
                    t.set(x, 2, z, stairs('spruce_stairs', back))  # the way out: two steps up the rear wall
                    t.set(nx, 3, nz, stairs('spruce_stairs', back))
                elif rng.random() < 0.35:
                    t.set(nx, G, nz, pick(rng, [(S('coarse_dirt'), 2), (S('packed_mud'), 1), (slab('mud_brick_slab'), 1)]))


def zigzag(x0, x1, z_a, z_b, period=8, width=2):
    """Cells of a fire trench from x0 to x1: bays alternately at z_a and z_b, joined by traverses."""
    cells = set()
    for x in range(x0, x1 + 1):
        zc = z_a if (x // period) % 2 == 0 else z_b
        for k in range(width):
            cells.add((x, zc + k))
        if x % period == 0 and x > x0:
            for z in range(min(z_a, z_b), max(z_a, z_b) + width):
                cells.add((x, z))
    return cells


def path_cells(points, width=2):
    """Cells of a trench along axis-aligned legs between the points (x, z), `width` wide."""
    cells = set()
    for (xa, za), (xb, zb) in zip(points, points[1:]):
        for x in range(min(xa, xb), max(xa, xb) + 1):
            for z in range(min(za, zb), max(za, zb) + 1):
                for k in range(width):
                    for j in range(width):
                        cells.add((x + k, z + j))
    return cells


def wire(t, rng, x0, x1, z):
    """Barbed wire: posts every four cells with tangles of wire (cobweb) between them, gaps here and there."""
    for x in range(x0, x1 + 1):
        if not ground_ok(t, x, z) or t.get(x, G, z) is not None:
            continue
        if (x - x0) % 4 == 0:
            t.set(x, G, z, S('spruce_fence', waterlogged=False))
            if rng.random() < 0.7:
                t.set(x, G + 1, z, S('spruce_fence', waterlogged=False))
        elif rng.random() < 0.55:
            t.set(x, G, z, S('cobweb'))


def hedgehog(t, x, z):
    """An anti-armour hedgehog: three beams crossed."""
    for dx in (-1, 0, 1):
        t.set(x + dx, G, z, beam('x'))
    for dz in (-1, 1):
        t.set(x, G + 1, z + dz, beam('z'))
    t.set(x, G + 1, z, S('or:rusted_plating'))
    t.set(x, G + 2, z, beam('y'))


def barricade(t, rng, x0, x1, z, gaps=(), crushed=()):
    """A durasteel barricade along z: beam posts every three cells, plating between, bars on top;
    trampled flat where `crushed`."""
    for x in range(x0, x1 + 1):
        if any(a <= x <= b for a, b in gaps) or not ground_ok(t, x, z):
            continue
        if any(a <= x <= b for a, b in crushed):
            zz = z + rng.choice((-1, 0, 0, 1))
            if rng.random() < 0.6 and ground_ok(t, x, zz) and t.get(x, G, zz) is None:
                t.set(x, G, zz, slab('or:durasteel_plating_slab') if rng.random() < 0.7 else S('or:wreck_plating_scorched'))
            continue
        if (x - x0) % 3 == 0:
            t.set(x, G, z, beam('y'))
            t.set(x, G + 1, z, beam('y'))
        else:
            t.set(x, G, z, pick(rng, [(S('or:durasteel_plating'), 3), (S('or:rusted_plating'), 3), (S('or:wreck_plating_scorched'), 1)]))
            if rng.random() < 0.6:
                t.set(x, G + 1, z, S('iron_bars', waterlogged=False) if rng.random() < 0.6 else S('or:rusted_plating'))


# ---------------------------------------------------------------- hulls

def hull_cells(axis, a0, a1, across, rh, yc, rv, p=4.0, scale=None):
    """A hull along `axis` ('x' or 'z') from a0 to a1: at each station a the cross-section
    |dh / rh|^p + |dv / rv|^p <= 1 round (across, yc(a)); scale(a) shrinks it toward the ends.
    Returns (solid, shell) as sets of (x, y, z)."""
    solid = set()
    for a in range(a0, a1 + 1):
        s = scale(a) if scale else 1.0
        y0 = yc(a) if callable(yc) else yc
        for h in range(int(across - rh) - 1, int(across + rh) + 2):
            for y in range(int(y0 - rv) - 1, int(y0 + rv) + 2):
                dh = (h + 0.5 - across) / (rh * s)
                dv = (y + 0.5 - y0) / (rv * s)
                if abs(dh) ** p + abs(dv) ** p <= 1.0:
                    solid.add((h, y, a) if axis == 'z' else (a, y, h))
    shell = {c for c in solid if any((c[0] + dx, c[1] + dy, c[2] + dz) not in solid for dx, dy, dz in SIX)}
    return solid, shell


# ---------------------------------------------------------------- the walker (variant 1)

def walker_leg(t, rng, y0, z0, pal, upto=13, part='whole'):
    """One leg reaching west from the hull's belly (the hull's west face is x 30): hip, thigh,
    knee, shin, ankle and foot, 2 x 2 through y0..y0+1, z0..z0+1. part 'upper' stops at a torn
    knee; 'lower' is the knee, shin and foot alone (lying elsewhere)."""
    def block(x0, x1, ya, yb, za, zb, st):
        for x in range(x0, x1 + 1):
            for y in range(ya, yb + 1):
                for z in range(za, zb + 1):
                    t.set(x, y, z, st(x, y, z) if callable(st) else st)
    if part in ('whole', 'upper'):
        block(28, 29, y0 - 1, y0 + 2, z0 - 1, z0 + 2, lambda x, y, z: S('or:wreck_conduit') if (y, z) in ((y0, z0), (y0 + 1, z0 + 1)) else S('or:durasteel_plating'))
        block(23, 27, y0, y0 + 1, z0, z0 + 1, pal)
        for x in range(23, 28):
            t.set(x, y0 + 2, z0, pipes('x'))
        if part == 'upper':
            for (y, z) in ((y0 - 1, z0), (y0, z0), (y0, z0 + 1), (y0 + 2, z0 + 1)):
                t.set(22, y, z, S('or:wreck_plating_scorched'))
            t.set(21, y0, z0, S('or:wreck_conduit'))
            return
    block(21, 22, y0 - 1, y0 + 2, z0, z0 + 1, lambda x, y, z: S('or:wreck_conduit') if y == y0 else S('or:durasteel_plating'))
    block(16, 20, y0, y0 + 1, z0, z0 + 1, pal)
    for x in range(16, 21):
        t.set(x, y0 - 1 if y0 > G else y0 + 2, z0 + 1, pipes('x'))
    block(15, 15, y0, y0 + 1, z0, z0 + 1, beam('x'))
    block(upto, 14, y0 - 1, y0 + 2, z0 - 1, z0 + 2, lambda x, y, z: S('or:durasteel_plating') if (y + z) % 3 else S('or:wreck_plating'))


def walker(t, rng):
    """The fallen walker: hull x 30..36, y 4..11, z 10..27 lying on its left side (belly west, back
    east), head to the south (z 30..37) with its guns at the trench, legs reaching west. Returns the
    troop bay's region."""
    pal = patches(7111, WALKER, 2.5)
    end = {10: 0.82, 11: 0.93, 27: 0.9}
    solid, shell = hull_cells('z', 10, 27, 33.5, 3.5, 8.0, 4.0, p=4.0, scale=lambda a: end.get(a, 1.0))
    for c in solid:
        t.set(*c, pal(*c) if c in shell else AIR)
    # ribs round the hull every four stations, vents along the back
    for c in shell:
        x, y, z = c
        if z in (13, 17, 21, 25):
            dh, dv = abs(x + 0.5 - 33.5) / 3.5, abs(y + 0.5 - 8.0) / 4.0
            t.set(x, y, z, beam('x') if dv > dh else beam('y'))
        elif x == 36 and y in (7, 8) and z in (12, 14, 23, 24):
            t.set(x, y, z, S('or:durasteel_vent'))
        elif x == 36 and y == 9:
            t.set(x, y, z, S('red_concrete') if z % 5 else S('or:wreck_plating_scorched'))
    # the breach in its flank and back: torn plating, the ribs left standing
    n = Noise(7112, 2.0)
    for c in shell:
        x, y, z = c
        if 15 <= z <= 20 and (x >= 35 or y >= 11) and y >= 5 and z not in (17,) and n.at(x, y, z) < 0.8:
            if not (z in (16, 20) and n.at(x, y, z) > 0.55):
                t.set(x, y, z, AIR)
    for z in (16, 17, 18, 19):
        t.set(37, G, z, slab('or:durasteel_plating_slab') if z != 17 else S('or:wreck_plating_scorched'))
        t.set(36, G, z, slab('or:durasteel_plating_slab'))
    # inside: the troop bay on its side, crates tumbled to the bottom, cables hanging
    for (x, y, z, st) in ((31, 5, 23, S('or:cargo_crate_imperial')), (32, 5, 23, S('or:cargo_crate_imperial')),
                          (31, 6, 23, S('or:cargo_crate_imperial')), (31, 5, 24, S('or:cargo_crate')),
                          (31, 5, 12, barrel('east')), (32, 5, 12, S('or:wreck_salvage')), (31, 5, 13, S('or:cargo_crate_imperial')),
                          (35, 5, 22, slab('or:durasteel_deck_slab')), (34, 5, 14, S('or:debris'))):
        t.set(x, y, z, st)
    t.chest(31, 5, 25, 'east', loot=LOOT)
    for (x, z, n_) in ((33, 22, 2), (32, 13, 1), (34, 25, 2), (33, 11, 1)):
        for k in range(n_):
            t.set(x, 10 - k, z, S('iron_chain', axis='y', waterlogged=False))
    t.set(30, 7, 24, S('or:console', facing='east'))
    t.set(30, 8, 24, S('or:holo_screen', facing='east'))
    t.set(33, 11, 24, S('or:light_panel_amber'))
    t.set(33, 11, 12, S('or:light_panel_amber'))
    for z in (19, 21):
        t.set(31, 8, z, stairs('or:imperial_plating_stairs', 'west', 'top'))  # the benches, now on the wall
    # neck and head, the head's guns at the trench
    for x in range(32, 36):
        for y in range(6, 10):
            for z in (28, 29):
                t.set(x, y, z, pipes('z') if (x + y) % 2 else S('or:wreck_conduit'))
    hs, hshell = hull_cells('z', 30, 37, 33.5, 3.0, 7.0, 3.0, p=3.0, scale=lambda a: 0.85 if a == 37 else 1.0)
    for c in hs:
        t.set(*c, pal(*c) if c in hshell else S('or:durasteel_plating'))
    for x in range(32, 36):
        t.set(x, 7, 37, S('tinted_glass'))
        t.set(x, 8, 37, S('tinted_glass'))
    t.set(33, 6, 37, S('or:light_panel_red'))
    for z in range(38, 42):
        t.set(31, 5, z, pipes('z'))
    for z in range(38, 40):
        t.set(31, 8, z, pipes('z'))
    t.set(31, 8, 40, S('or:wreck_plating_scorched'))
    for z in (31, 33, 35):
        t.set(36, 9, z, S('or:durasteel_vent'))
    # the legs: the lower pair on the ground, the upper rear leg jutting, the upper front one snapped
    walker_leg(t, rng, G, 13, pal)
    walker_leg(t, rng, G, 22, pal)
    walker_leg(t, rng, 9, 13, pal)
    walker_leg(t, rng, 9, 22, pal, part='upper')
    # the snapped-off lower leg lying beside the head
    for x in range(16, 21):
        for y in (G, G + 1):
            for z in (27, 28):
                t.set(x, y, z, pal(x, y, z))
    for y in range(SURF, G + 3):
        for z in range(26, 30):
            t.set(14, y, z, S('or:durasteel_plating') if (y + z) % 3 else S('or:wreck_plating'))
            t.set(13, y, z, S('or:wreck_plating_scorched'))
    for y in range(G - 1, G + 3):
        t.set(21, y, 27, S('or:wreck_conduit') if y == G else S('or:durasteel_plating'))
        t.set(22, y, 28, S('or:wreck_plating_scorched'))
    return (31, 5, 11, 35, 10, 26)


def turret(t, rng, cx, cz, state):
    """A Republic turret emplacement: a ferrocrete pad in a ring of sandbags open at the back (south),
    the gun on its pedestal aiming west ('intact') or blasted to a stump ('stump')."""
    for x in range(cx - 5, cx + 6):
        for z in range(cz - 5, cz + 6):
            d = math.hypot(x - cx, z - cz)
            if d <= 2.6 and ground_ok(t, x, z):
                t.set(x, SURF, z, S('polished_andesite') if d <= 1.6 else S('andesite'))
            elif 3.0 < d <= 4.3 and not (z > cz + 1 and abs(x - cx) <= 1) and t.get(x, G, z) is None and ground_ok(t, x, z):
                t.set(x, G, z, pick(rng, SANDBAG))
                if rng.random() < 0.6:
                    t.set(x, G + 1, z, slab('mud_brick_slab'))
    if state == 'intact':
        t.set(cx, G, cz, beam('y'))
        t.set(cx, G + 1, cz, beam('y'))
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                for y in (G + 2, G + 3):
                    t.set(x, y, z, S('or:republic_plating') if (x, z) != (cx + 1, cz) else S('or:durasteel_vent'))
        t.set(cx, G + 4, cz, slab('or:republic_plating_slab'))
        for x in range(cx - 6, cx - 1):
            t.set(x, G + 3, cz - 1, pipes('x'))
            t.set(x, G + 3, cz + 1, pipes('x'))
        t.set(cx - 1, G + 2, cz, S('blue_concrete'))
        t.set(cx + 2, G, cz, S('or:console', facing='west'))
    else:
        t.set(cx, G, cz, beam('y'))
        for (dx, dz) in ((1, 0), (0, 1), (-1, 0)):
            t.set(cx + dx, G, cz + dz, S('or:wreck_plating_scorched'))
        t.set(cx, G + 1, cz, S('or:wreck_conduit'))
        for x in (cx + 2, cx + 3):
            for z in (cz - 2, cz - 1):
                t.set(x, G, z, S('or:republic_plating') if x == cx + 2 else S('or:wreck_plating_scorched'))
                t.set(x, G + 1, z, slab('or:republic_plating_slab'))
        for z in range(cz + 1, cz + 5):
            t.set(cx - 2, G, z, pipes('z'))
        burn(t, rng, cx, cz, 4.5, 7150, decal=0.6)


def dugout(t, rng, x0, z0, x1, z1, door_x):
    """The command dugout: a planked room dug in (floor y 0, head room y 1..2, ceiling y 3) under an
    earth mound with sandbags and an aerial; its door through the north wall at x door_x..+1, down
    two steps from the trench two cells north. Returns its rooms."""
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            wall = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            corner = x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1)
            t.set(x, 0, z, S('spruce_planks'))
            for y in (1, 2):
                t.set(x, y, z, (S('stripped_spruce_log', axis='y') if corner or x % 3 == 0 else S('spruce_planks')) if wall else AIR)
            t.set(x, 3, z, S('stripped_spruce_log', axis='x') if (z - z0) % 2 == 0 and not wall else S('spruce_planks'))
            edge = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            t.set(x, G, z, pick(rng, SANDBAG) if edge and rng.random() < 0.8 else S('grass_block', snowy=False) if not edge else S('coarse_dirt'))
    # the way in: down from the trench through a short planked passage
    for x in (door_x, door_x + 1):
        t.set(x, 1, z0 - 3, stairs('spruce_stairs', 'north'))
        t.set(x, 2, z0 - 3, AIR)
        t.set(x, 3, z0 - 3, AIR)
        t.set(x, G, z0 - 3, AIR)
        t.set(x, 0, z0 - 2, S('spruce_planks'))
        t.set(x, 1, z0 - 2, AIR)
        t.set(x, 2, z0 - 2, AIR)
        t.set(x, 3, z0 - 2, S('spruce_planks'))
        t.set(x, G, z0 - 2, pick(rng, SANDBAG))
        t.set(x, 1, z0 - 1, AIR)
        t.set(x, 2, z0 - 1, AIR)
    for y in (1, 2):
        t.set(door_x - 1, y, z0 - 2, S('spruce_planks'))
        t.set(door_x + 2, y, z0 - 2, S('spruce_planks'))
    # inside: the officer's footlocker, a cot, the map table, the field radio, a lantern
    t.chest(x1, 1, z1, 'west', loot=LOOT)
    bed(t, x0 + 1, 1, z1, 'west', 'blue')
    t.set(x0, 1, z0, S('cartography_table'))
    t.set(x0 + 1, 1, z0, stairs('spruce_stairs', 'east'))
    t.set(x0, 2, z0 + 1, S('or:holo_screen', facing='east'))
    t.set(x0, 1, z0 + 1, slab('spruce_slab', 'top'))
    t.set(x1, 1, z0, S('or:console', facing='west'))
    t.set(x1, 2, z0, S('or:holo_screen', facing='west'))
    t.set(x1, 1, z0 + 1, barrel('up'))
    t.set(x1 - 1, 1, z1, S('or:cargo_crate_republic'))
    t.set((x0 + x1) // 2, 2, (z0 + z1) // 2, lantern(True))
    wall_banner(t, (x0 + x1) // 2 + 1, 2, z1, 'north', 'blue', [('minecraft:chevron', 'white'), ('minecraft:border', 'blue')])
    # the aerial on the mound
    for y in range(G + 1, G + 5):
        t.set(x1, y, z1, beam('y'))
    t.set(x1, G + 5, z1, rod('up'))
    return [(x0, 1, z0, x1, 2, z1), (door_x, 1, z0 - 2, door_x + 1, 2, z0 - 1)]


def memorial(t, rng, cx, cz):
    """The memorial behind the line: a cairn under a tattered Republic banner, two rows of rifles
    planted in the ground in front of it, candles on the cairn, poppies all round."""
    stones = [(S('cobblestone'), 3), (S('mossy_cobblestone'), 3), (S('stone'), 2), (S('andesite'), 1)]
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            t.set(x, G, z, pick(rng, stones))
    for (x, z) in ((cx, cz), (cx - 1, cz), (cx, cz + 1), (cx + 1, cz)):
        t.set(x, G + 1, z, pick(rng, stones))
    t.set(cx, G + 2, cz, pick(rng, stones))
    for y in range(G + 3, G + 7):
        t.set(cx, y, cz, S('spruce_fence', waterlogged=False))
    standing_banner(t, cx, G + 7, cz, 'north', 'blue', [('minecraft:chevron', 'white'), ('minecraft:border', 'blue'),
                                                         ('minecraft:half_vertical', 'blue')])
    for (x, z) in ((cx - 1, cz - 1), (cx + 1, cz + 1), (cx + 1, cz - 1)):
        t.set(x, G + 1, z, S('white_candle', candles=rng.randint(1, 3), lit=True, waterlogged=False))
    for row in (cz - 5, cz - 3):
        for x in range(cx - 6, cx + 7, 2):
            if ground_ok(t, x, row):
                t.set(x, G, row, old_rod(rng))
                t.set(x, SURF, row, S('coarse_dirt'))
    poppies(t, rng, cx, cz - 2, 6.5, 0.45)


def build_walker_line():
    t = Template(SX, SY, SZ, seed=960116, name='wilds/war_scar/walker_line')
    rng = t.rng
    field(t, 7101)
    rooms = []
    # the walker's footprints coming in from the north
    for (x0, z0) in ((27, 0), (37, 3), (27, 6)):
        for x in range(x0, x0 + 3):
            for z in range(z0, z0 + 3):
                dig(t, x, z, SURF - 1, S('coarse_dirt') if (x + z) % 2 else S('mud'))
    # shell craters across no-man's land
    for (cx, cz, r, depth, water) in ((9, 10, 3.5, 2, True), (49, 8, 3.0, 2, False), (17, 33, 2.5, 2, False),
                                      (46, 27, 3.2, 2, True), (5, 30, 2.2, 1, False), (55, 20, 2.6, 2, False),
                                      (20, 4, 2.0, 1, False), (8, 21, 2.4, 2, False), (56, 33, 2.0, 1, False)):
        crater(t, rng, cx, cz, r, depth, water)
    rooms.append(walker(t, rng))
    # the Republic line: barricades, wire, hedgehogs, the trench, the dugout, two turrets
    barricade(t, rng, 3, 57, 34, gaps=((13, 15), (44, 46)), crushed=((25, 41),))
    for (x0, x1) in ((3, 24), (40, 57)):
        wire(t, rng, x0, x1, 38)
    for (x, z) in ((7, 25), (21, 30), (11, 37), (43, 37), (52, 30), (41, 31), (24, 38)):
        hedgehog(t, x, z)
    trench(t, rng, zigzag(2, 58, 42, 45))
    rooms += dugout(t, rng, 38, 50, 44, 54, 41)
    turret(t, rng, 8, 51, 'stump')
    turret(t, rng, 52, 51, 'intact')
    memorial(t, rng, 22, 55)
    # scorched ground: round the walker, along its path, at the craters
    for i, (cx, cz, r) in enumerate(((33, 18, 12), (31, 5, 6), (20, 30, 5), (47, 22, 6), (9, 12, 6), (50, 8, 5),
                                     (35, 39, 5), (16, 44, 4), (56, 44, 4))):
        burn(t, rng, cx, cz, r, 7120 + i * 7)
    for (x, z) in ((25, 12), (39, 21), (27, 31), (38, 8), (19, 18), (42, 28)):
        if ground_ok(t, x, z) and t.get(x, G, z) is None:
            t.set(x, G, z, S('or:wreck_plating_scorched') if (x + z) % 2 else S('or:wreck_salvage'))
    grow(t, rng, density=0.32)
    carve(t, 0, 0, SX - 1, SZ - 1, G, G + 3, bank=1, noise=Noise(7199, 3.0))
    light_rooms(t, rooms)
    return t


# ---------------------------------------------------------------- the battery and the gunship (variant 2)

def gun_pit(t, rng, cx, cz, r_in=3.2):
    """A gun pit: dug to a floor at y 1, its wall of sandbags rising to a two-high parapet."""
    for x in range(int(cx - r_in) - 2, int(cx + r_in) + 3):
        for z in range(int(cz - r_in) - 2, int(cz + r_in) + 3):
            d = math.hypot(x - cx, z - cz)
            if d <= r_in:
                dig(t, x, z, 1, pick(rng, [(S('gravel'), 2), (S('packed_mud'), 2), (S('coarse_dirt'), 1)]))
            elif d <= r_in + 1.2 and ground_ok(t, x, z):
                for y in (2, 3):
                    t.set(x, y, z, pick(rng, SANDBAG))
                t.set(x, G, z, pick(rng, SANDBAG))
                if rng.random() < 0.75:
                    t.set(x, G + 1, z, slab('mud_brick_slab'))


def gun(t, rng, cx, cz, state):
    """The battery's gun in its pit: 'ready' (on its turntable, barrel raised to the east),
    'wrecked' (blown off its mount, barrel lying in pieces) or 'crushed' (flattened under a wing)."""
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            t.set(x, 1, z, S('or:durasteel_deck'))
    if state == 'ready':
        t.set(cx, 2, cz, S('or:imperial_plating'))
        t.set(cx, 3, cz, S('or:imperial_plating'))
        t.set(cx - 1, 2, cz, stairs('or:imperial_plating_stairs', 'east'))
        t.set(cx - 2, 2, cz, slab('or:imperial_plating_slab'))
        for dz in (-1, 1):
            t.set(cx, 2, cz + dz, slab('or:imperial_plating_slab', 'top'))
            t.set(cx + 1, 3, cz + dz, S('iron_bars', waterlogged=False))
        t.set(cx - 1, 3, cz, pipes('x'))
        for (dx, dy) in ((1, 4), (2, 4), (3, 5), (4, 5), (5, 6), (6, 6), (7, 7)):
            t.set(cx + dx, dy, cz, pipes('x'))
        t.set(cx, 4, cz, S('or:durasteel_plating'))
    elif state == 'wrecked':
        t.set(cx, 2, cz, S('or:wreck_plating_scorched'))
        t.set(cx + 1, 2, cz, slab('or:imperial_plating_slab'))
        t.set(cx - 1, 2, cz + 1, S('or:wreck_conduit'))
        for z in range(cz - 2, cz + 2):
            t.set(cx + 2, 2, z, pipes('z'))
        t.set(cx - 2, 2, cz - 1, pipes('x'))
        burn(t, rng, cx, cz, 4.0, 7250, decal=0.7)
    else:
        t.set(cx, 2, cz, S('or:wreck_plating'))
        t.set(cx + 1, 2, cz, pipes('x'))
        t.set(cx + 2, 2, cz + 1, pipes('x'))
        t.set(cx - 1, 2, cz - 1, slab('or:imperial_plating_slab'))


def battery_kit(t, rng, cx, cz):
    """What stands round a working gun: crates of shells, a rack of rounds, spent casings."""
    for (x, z) in ((cx - 2, cz + 2), (cx - 2, cz + 1)):
        t.set(x, 2, z, S('or:cargo_crate_imperial'))
    t.set(cx - 2, 3, cz + 2, S('or:cargo_crate_imperial'))
    weapon_rack(t, cx - 1, 2, cz - 2, 'x', n=3, base=slab('or:imperial_plating_slab'))
    for (x, z) in ((cx + 1, cz + 2), (cx + 2, cz + 1), (cx, cz + 2)):
        t.set(x, 2, z, old_rod(rng, 'east'))


def bunker(t, rng, x0, z0, x1, z1, door_x):
    """The ammunition bunker: ferrocrete walls (floor y 0, room y 1..3, roof y 4) half sunk under an
    earth mound, a door down from the trench at x door_x..+1 in the north wall. Returns its room."""
    ferro = patches(7260, FERRO, 2.5)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            wall = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            t.set(x, 0, z, ferro(x, 0, z))
            for y in (1, 2, 3):
                t.set(x, y, z, ferro(x, y, z) if wall else AIR)
            t.set(x, G, z, ferro(x, G, z))
            if not wall:
                t.set(x, G + 1, z, S('grass_block', snowy=False) if (x + z) % 4 else S('coarse_dirt'))
            elif rng.random() < 0.7:
                t.set(x, G + 1, z, pick(rng, SANDBAG_TOP))
    for x in (door_x, door_x + 1):
        t.set(x, 1, z0 - 1, stairs('polished_deepslate_stairs', 'north'))
        t.set(x, 2, z0 - 1, AIR)
        t.set(x, 3, z0 - 1, AIR)
    # shells racked along the west wall, crates of charges, the battery's strongbox
    for z in range(z0, z1 + 1, 1):
        if z != z0:
            t.set(x0, 1, z, slab('or:imperial_plating_slab', 'top'))
            t.set(x0, 2, z, old_rod(rng))
    for (x, z) in ((x1, z0), (x1 - 1, z0), (x1, z0 + 1)):
        t.set(x, 1, z, S('or:cargo_crate_imperial'))
    t.set(x1, 2, z0, S('or:cargo_crate_imperial'))
    t.chest(x1, 1, z1, 'west', loot=LOOT)
    t.set(x1 - 1, 1, z1, barrel('up'))
    t.set((x0 + x1) // 2, 1, z1, S('or:console', facing='north'))
    t.set((x0 + x1) // 2, 2, z1, S('or:holo_screen', facing='north'))
    t.set((x0 + x1) // 2, G, (z0 + z1) // 2, S('or:light_panel_red'))
    t.set(x0 + 1, 3, z0 + 1, lantern(True))
    # the battery's standard on the mound
    for y in range(G + 1, G + 5):
        t.set(x0, y, z1 + 1, beam('y'))
    standing_banner(t, x0, G + 5, z1 + 1, 'north', 'black', [('minecraft:cross', 'red'), ('minecraft:border', 'black')])
    return (x0, 1, z0, x1, 3, z1)


def gunship(t, rng):
    """The Republic gunship nose-down at the end of its gouge: hull x 19..50 rising to the tail,
    centred on z 24.5; the north wing and engine on, the south wing torn away, the tail fin up.
    Returns the troop bay's region."""
    x_nose, x_tail, zc = 19, 50, 24.5

    def yc(x):
        return 1.6 + (x - x_nose) * 0.27

    pal = patches(7211, GUNSHIP, 2.5)
    soil = patches(7212, FILL, 2.0)
    end = {x_tail: 0.78, x_tail - 1: 0.92, x_nose: 0.8}
    solid, shell = hull_cells('x', x_nose, x_tail, zc, 4.0, yc, 3.3, p=3.0, scale=lambda a: end.get(a, 1.0))
    for c in solid:
        x, y, z = c
        if c in shell:
            dh = abs(z + 0.5 - zc) / 4.0
            stripe = dh > 0.7 and abs(y + 0.5 - yc(x)) < 0.6
            glass = x <= x_nose + 4 and y + 0.5 > yc(x) + 1.4
            pane = S('light_blue_stained_glass') if (x + z) % 4 == 0 else S('tinted_glass')
            t.set(x, y, z, S('blue_concrete') if stripe else pane if glass else pal(x, y, z))
        else:
            t.set(x, y, z, soil(x, y, z) if y <= SURF else AIR)
    # the walkway up the tilted bay: a step wherever the floor rises
    floor = {}
    for x in range(x_nose + 1, x_tail):
        ys = [y for y in range(0, SY) if (x, y, 24) in solid and (x, y, 24) not in shell and t.get(x, y, 24) == 'minecraft:air']
        if ys:
            floor[x] = min(ys)
    for x in sorted(floor):
        if x + 1 in floor and floor[x + 1] > floor[x]:
            for z in range(21, 29):
                if t.get(x, floor[x], z) == 'minecraft:air' and (x, floor[x] - 1, z) in solid:
                    t.set(x, floor[x], z, stairs('or:durasteel_plating_stairs', 'east'))
    # the side door (south face) and the debris stacked up to it
    for x in range(37, 41):
        fy = floor.get(x, 6)
        for y in range(fy, fy + 3):
            for z in (27, 28, 29):
                if (x, y, z) in shell:
                    t.set(x, y, z, AIR)
        for k, z in enumerate(range(29, 33)):
            top = fy - 1 - k
            if top >= G:
                t.set(x, top, z, stairs('or:durasteel_plating_stairs', 'north'))
                for y in range(G, top):
                    t.set(x, y, z, S('or:wreck_plating') if (x + y) % 2 else S('or:rusted_plating'))
    # benches and gear in the bay, a light still working
    for x in range(40, 47, 2):
        fy = floor.get(x)
        if fy is not None:
            t.set(x, fy, 22, slab('or:durasteel_deck_slab', 'top') if t.get(x, fy, 22) == 'minecraft:air' else t.get(x, fy, 22))
            t.set(x, fy, 27, S('or:cargo_crate_republic') if x % 4 == 0 else barrel('up'))
    top_y = {x: max(y for (xx, y, z) in solid if xx == x and z == 24) for x in range(x_nose, x_tail + 1)}
    for x in (36, 44):
        t.set(x, top_y[x], 24, S('or:light_panel'))
    for x in (42, 47):
        t.set(x, top_y[x] - 1, 25, S('iron_chain', axis='y', waterlogged=False))
    # the tail fin, the north wing and its engine, the stub of the south wing
    for x in range(44, x_tail + 1):
        h = x - 42
        for k in range(1, h + 1):
            t.set(x, top_y[x] + k, 24, S('blue_concrete') if k == h else pal(x, top_y[x] + k, 24))
    for z in range(13, 21):
        for x in range(33 + (20 - z) // 2, 40):
            y = int(round(yc(x) - 1.5))
            t.set(x, y, z, S('or:republic_plating') if (x + z) % 4 else S('white_concrete'))
    eng, eshell = hull_cells('x', 31, 41, 14.5, 1.5, lambda x: yc(x) - 0.5, 1.5, p=2.0)
    for c in eng:
        x, y, z = c
        st = S('or:durasteel_vent') if x == 41 else S('or:wreck_conduit') if x == 31 else S('white_concrete') if (x + y) % 3 else S('or:republic_plating')
        t.set(x, y, z, st)
    for x in range(34, 39):
        y = int(round(yc(x) - 1.5))
        t.set(x, y, 29, S('or:wreck_plating_scorched'))
        if x % 2:
            t.set(x, y, 30, S('or:wreck_conduit'))
    return [(x0, floor.get(x0, 6), 21, x0, floor.get(x0, 6) + 3, 28) for x0 in range(31, x_tail)]


def build_gunship_battery():
    t = Template(SX, SY, SZ, seed=960117, name='wilds/war_scar/gunship_battery')
    rng = t.rng
    field(t, 7201)
    rooms = []
    # the gouge from the east, its spoil thrown up on both sides, the heap before the nose
    zc = 24.5
    for x in range(17, SX):
        w = 2.6 + max(0, 32 - x) * 0.12
        k = 2 if x < 34 else 1
        for z in range(14, 36):
            d = abs(z + 0.5 - zc)
            if d <= w:
                dig(t, x, z, SURF - k, pick(rng, [(S('coarse_dirt'), 4), (S('dirt'), 2), (S('gravel'), 2), (S('rooted_dirt'), 1)]))
            elif d <= w + 1.6 and ground_ok(t, x, z) and t.get(x, G, z) is None:
                t.set(x, G, z, pick(rng, [(S('coarse_dirt'), 3), (S('dirt'), 3), (S('gravel'), 1)]))
                if x < 30 and d <= w + 0.8:
                    t.set(x, G + 1, z, S('coarse_dirt'))
    for x in range(11, 21):
        for z in range(17, 33):
            h = int(3.6 - math.hypot((x - 17) * 1.2, (z - zc) * 0.55) / 2.2)
            if h <= 0 or t.get(x, SURF - 2, z) is None:
                continue
            for y in range(SURF - 2, G + h):
                if empty(t, x, y, z):
                    t.set(x, y, z, pick(rng, [(S('coarse_dirt'), 3), (S('dirt'), 3), (S('gravel'), 1)]))
    rooms += gunship(t, rng)
    # wreckage strewn back along the gouge
    for (x, z, st) in ((52, 23, S('or:wreck_plating_scorched')), (53, 24, S('or:wreck_salvage')), (56, 26, S('or:wreck_plating')),
                       (58, 23, pipes('x')), (57, 23, pipes('x')), (54, 27, S('or:wreck_conduit')), (51, 26, slab('or:republic_plating_slab'))):
        t.set(x, SURF, z, st)
    for x in range(50, 57):
        for y in (G, G + 1):
            for z in (33, 34):
                t.set(x, y, z, S('or:durasteel_vent') if x == 56 else S('white_concrete') if (x + y) % 3 else S('or:wreck_plating_scorched'))
    # the battery: three gun pits joined by trenches, the bunker behind
    for (cx, cz) in ((13, 45), (27, 51), (44, 44)):
        gun_pit(t, rng, cx, cz)
    trench(t, rng, path_cells([(15, 47), (20, 47), (20, 50), (23, 50)]))
    trench(t, rng, path_cells([(31, 50), (35, 50), (35, 46), (40, 46)]))
    trench(t, rng, path_cells([(13, 49), (13, 50)]))
    gun(t, rng, 13, 45, 'ready')
    battery_kit(t, rng, 13, 45)
    gun(t, rng, 27, 51, 'wrecked')
    gun(t, rng, 44, 44, 'crushed')
    rooms.append(bunker(t, rng, 12, 53, 18, 57, 13))
    # the torn south wing across the third pit
    for x in range(39, 49):
        for z in range(40 + max(0, x - 45), 47):
            y = G + 1 if x >= 42 else G
            edge = z == 46 or x == 48
            t.set(x, y, z, S('blue_concrete') if edge else S('white_concrete') if (x * 3 + z) % 7 else S('or:republic_plating'))
    # the crew's graves north of the wreck: the wing's flap as their headstone
    for x in range(29, 36):
        for y in range(G, G + 3):
            if not (y == G + 2 and x in (29, 35)):
                t.set(x, y, 7, S('blue_concrete') if y == G + 1 else S('or:republic_plating'))
    for x in (30, 32, 34):
        t.set(x, G, 9, S('coarse_dirt'))
        t.set(x, G, 10, S('coarse_dirt'))
        t.set(x, G, 8, old_rod(rng))
    poppies(t, rng, 32, 10, 4.5, 0.5)
    for (cx, cz, r, depth, water) in ((6, 31, 3.0, 2, True), (22, 38, 2.6, 2, False), (36, 38, 2.4, 2, False),
                                      (52, 52, 3.4, 2, True), (8, 8, 3.0, 1, False), (48, 9, 2.8, 2, False),
                                      (56, 40, 2.5, 2, False), (33, 57, 2.4, 2, False), (4, 52, 2.2, 1, False)):
        crater(t, rng, cx, cz, r, depth, water)
    for i, (cx, cz, r) in enumerate(((22, 24, 8), (35, 25, 9), (50, 25, 7), (27, 51, 5), (44, 44, 5),
                                     (8, 30, 5), (52, 52, 5), (36, 38, 4))):
        burn(t, rng, cx, cz, r, 7220 + i * 7)
    grow(t, rng, density=0.3)
    carve(t, 0, 0, SX - 1, SZ - 1, G, G + 3, bank=1, noise=Noise(7299, 3.0))
    light_rooms(t, rooms)
    return t


# ---------------------------------------------------------------- worldgen

def write_worldgen():
    loot_table(LOOT, [
        ((3, 4), [loot_item('oldrepublic:durasteel_plate', 8, (1, 2)), loot_item('iron_ingot', 6, (1, 3)),
                  loot_item('gunpowder', 5, (1, 3)), loot_item('redstone', 4, (1, 3)),
                  loot_item('oldrepublic:medpac', 2), loot_item('oldrepublic:alloy_plate', 2)]),
    ])
    ad = adapted(SY, yard_stand=G, support_depth=8, ramp_rings=2)
    wilds_family('war_scar', [('wilds/war_scar/walker_line', ad, 1), ('wilds/war_scar/gunship_battery', ad, 1)],
                 ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:savanna', 'minecraft:snowy_plains',
                  'minecraft:meadow'],
                 spacing=56, separation=22, salt=960105, buried=BURIED)


def main():
    args = cli(__doc__)
    for name, build in (('walker_line', build_walker_line), ('gunship_battery', build_gunship_battery)):
        t = build()
        print(name, t.size, t.count())
        save(t, 'wilds/war_scar/' + name, args.preview)
    write_worldgen()


if __name__ == '__main__':
    main()
