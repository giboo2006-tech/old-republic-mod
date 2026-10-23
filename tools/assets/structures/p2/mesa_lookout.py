"""ghessar/mesa_lookout (worldgen, Ghessar's canyons): a watch post on top of a lone rock pillar.

A spire of banded red rock stands alone on the canyon floor, thirty-odd blocks high, its cap of harder stone
wider than its waist. Somebody has built on top: a deck of planks and plating out over the drop, a lean-to of
canvas and plate, a signal brazier, a spyglass on a post and the kajidic's cloth, so that whoever sits up there
sees every caravan for a day's ride. The cache (the chest) is in the lean-to; water jars, because there is no
water up there but what you carry.

Two variants of the way up: `lift`, a cargo lift on a beam track up the south face, its cage stuck halfway on
its chains and a ladder beside the track; `stair`, a switchback stair of timber flights pinned to the south
face. 21 x 21, 44 high, the feet layer at y 3 (the pillar's top at y 36).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gkit as G  # noqa: E402
from gkit import S, AIR, B, mod, W, Noise, Template  # noqa: E402
from structlib import jigsaw  # noqa: E402

NAME = 'ghessar/mesa_lookout'
SX = SZ = 21
SY = 44
BURIED = 3
F = BURIED
FL = F - 1
CX, CZ = 10, 8.5          # the pillar's axis
TOP = 36                  # the cap's top layer (the deck's feet at TOP + 1)
SEED = 960340
LOOT = 'chests/ghessar/mesa_lookout'
STRATA = ['red_sandstone', 'terracotta', 'orange_terracotta', 'red_sandstone', 'smooth_red_sandstone',
          'red_terracotta', 'red_sandstone', 'orange_terracotta', 'terracotta', 'brown_terracotta']


def radius(y):
    """The pillar's mean radius at layer y: a talus foot, a waist, a wider cap."""
    h = y - F
    if h < 0:
        return 9.0
    if h < 5:
        return 8.6 - 0.55 * h
    if y < TOP - 4:
        k = (h - 5) / float(TOP - 4 - F - 5)
        return 6.0 - 1.0 * k + 0.4 * math.sin(k * 5.0)
    return 5.2 + 0.45 * (y - (TOP - 4))


def pillar(t, seed):
    """The rock: banded strata, a lumpy outline, the cap of harder stone."""
    n = Noise(seed + 1, 3.0)
    band = Noise(seed + 2, 6.0)
    rock = set()
    for y in range(0, TOP + 1):
        r0 = radius(y)
        for x in range(SX):
            for z in range(SZ):
                dx, dz = x - CX, z - CZ
                a = math.atan2(dz, dx)
                r = r0 * (1 + 0.16 * (n.at(math.cos(a) * 4 + 10, y * 0.35, math.sin(a) * 4 + 10) - 0.5) * 2)
                if math.hypot(dx, dz) <= r:
                    rock.add((x, y, z))
    for (x, y, z) in rock:
        if y >= TOP - 2:
            s = 'cut_red_sandstone' if band.at(x, y, z) > 0.45 else 'smooth_red_sandstone'
        else:
            k = int((y + 0.25 * x + 2.5 * band.at(x, 0, z)) / 3.0) % len(STRATA)
            s = STRATA[k]
        t.set(x, y, z, s)
    return rock


def surface_cell(rock, y, a):
    """The outermost rock cell at layer y in direction a (radians, 0 = +x)."""
    best = None
    for k in range(0, 24):
        r = k * 0.5
        x = int(round(CX + math.cos(a) * r))
        z = int(round(CZ + math.sin(a) * r))
        if (x, y, z) in rock:
            best = (x, y, z)
    return best


def deck(t, rng, ruined=False):
    """The lookout on top: a plank deck over the cap and out over the south-east drop on beams, a fence rail, a
    lean-to with the cache, the brazier, the spyglass, the kajidic's cloth."""
    y = TOP
    cells = set()
    for x in range(2, 19):
        for z in range(1, 19):
            d = math.hypot(x - CX, z - CZ)
            inside_cap = t.get(x, y, z) not in (None, AIR)
            over = 13 <= x <= 18 and 9 <= z <= 15
            if inside_cap or over:
                if d > 9.6:
                    continue
                cells.add((x, z))
    patch = Noise(SEED + 9, 2.0)
    for (x, z) in cells:
        t.set(x, y, z, 'or:durasteel_deck' if patch.at(x, 0, z) > 0.7 else 'spruce_planks')
        for yy in range(y + 1, y + 6):
            t.set(x, yy, z, AIR)
    # beams under the overhang, struts back into the rock
    for x in range(13, 19):
        for z in (9, 15):
            if (x, z) in cells and t.get(x, y - 1, z) in (None, AIR):
                t.set(x, y - 1, z, 'or:durasteel_beam[axis=x]')
    for z in range(9, 16, 3):
        for k in range(1, 6):
            x = 18 - k
            yy = y - 1 - k
            if t.get(x, yy, z) not in (None, AIR):
                break
            t.set(x, yy, z, 'or:durasteel_beam[axis=x]')
    # the rail round the deck's edge
    for (x, z) in cells:
        edge = any((x + dx, z + dz) not in cells for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            t.set(x, y + 1, z, S('spruce_fence'))
    return cells


def lean_to(t, rng):
    """The watchers' lean-to on the cap's north half: plate walls, a canvas roof, the cache, bedrolls, jars."""
    y = TOP + 1
    x0, x1, z0, z1 = 6, 12, 3, 7
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            for yy in range(y, y + 3):
                if edge and not (z == z1 and x0 + 2 <= x <= x0 + 3 and yy < y + 2):
                    if z == z1 and yy == y + 2 and x0 + 2 <= x <= x0 + 3:
                        t.set(x, yy, z, 'or:rusted_plating')
                    elif z == z1 and x0 + 2 <= x <= x0 + 3:
                        t.set(x, yy, z, AIR)
                    else:
                        t.set(x, yy, z, 'or:wreck_plating' if (x + yy) % 3 else 'or:rusted_plating')
                else:
                    t.set(x, yy, z, AIR)
            t.set(x, y + 3, z, 'or:canvas_crimson' if z == z0 + 2 else G.slab('or:canvas_slab', 'top'))
    t.chest(x0 + 1, y, z0 + 1, 'south', LOOT)
    G.bedroll(t, x0 + 3, y, z0 + 1, 'x', 'brown')
    G.water_jar(t, x1 - 1, y, z0 + 1, full=True)
    G.water_jar(t, x1 - 1, y, z0 + 2, full=False)
    t.set(x1 - 1, y, z0 + 3, G.barrel('up'))
    t.set(x0 + 1, y, z0 + 3, G.pot('east'))
    t.set(x0 + 4, y + 2, z0 + 2, G.lantern(True))
    t.set(x0 + 4, y + 3, z0 + 2, 'or:canvas')
    return (x0 + 1, y, z0 + 1, x1 - 1, y + 2, z1 - 1)


def build(variant):
    lift = variant == 'lift'
    seed = SEED + (0 if lift else 50)
    t = Template(SX, SY, SZ, seed=seed, name=NAME + '/' + variant)
    rng = t.rng
    g = G.Ground(t, F, 'canyon', seed=seed)
    g.noise(amp=2, scale=6.0)
    for x in range(2, 19):
        for z in range(14, 20):
            g.h[x][z] = 2 * F
    g.taper(slope=2)
    g.feather()
    g.apply(clear_above=3)
    rock = pillar(t, seed)
    # talus: boulders fallen from the pillar round its foot
    for (bx, bz, r) in ((3, 15, 1.8), (16, 3, 1.6), (2, 4, 1.2), (17, 16, 1.4), (12, 18, 1.1)):
        G.boulder(t, bx, g.feet(bx, bz), bz, r, seed + bx * 7 + bz)
    cells = deck(t, rng)
    box = lean_to(t, rng)
    y = TOP + 1
    # the signal brazier and the spyglass on the overhang, the kajidic's banner on a pole
    G.brazier(t, 16, y, 12, stand='cut_red_sandstone')
    t.set(17, y, 10, S('spruce_fence'))
    t.set(17, y + 1, 10, G.rod('east', 'exposed_lightning_rod'))
    G.chair(t, 14, y, 14, 'south', 'spruce_stairs')
    for k in range(0, 5):
        t.set(5, y + k, 9, S('iron_bars') if k < 4 else 'cut_red_sandstone')
    G.drape(t, 6, y + 3, 9, 2, 'or:canvas_crimson')
    mod.banner(t, 4, y + 3, 9, 'west', base='red', wall=True)
    regions = [box]
    spots = {}
    # ------------------------------------------------------------ the way up: south face
    if lift:
        # the track: two beam columns standing off the face from the foot to the deck, the ladder between them
        # on a spine of plate; the winch house over it on the deck; the cage stuck halfway on its chains
        tx, tz = 10, 17
        foot = g.feet(tx, tz)
        for yy in range(foot - 1, TOP + 1):
            for x in (tx - 2, tx + 2):
                t.set(x, yy, tz, 'or:durasteel_beam[axis=y]')
            t.set(tx, yy, tz - 1, 'or:durasteel_plating' if yy % 6 else 'or:durasteel_vent')
            for x in (tx - 1, tx + 1):
                if t.get(x, yy, tz) in (None, AIR):
                    t.set(x, yy, tz, AIR)
            # tie the spine back into the rock every sixth layer
            if yy % 6 == 0:
                for z in range(tz - 2, int(CZ), -1):
                    if (tx, yy, z) in rock:
                        break
                    t.set(tx, yy, z, 'or:durasteel_beam[axis=z]')
        # the ladder ends level with the deck's floor, where its backing (the spine) ends
        for yy in range(foot, TOP + 1):
            t.set(tx, yy, tz, G.ladder('south'))
        for yy in range(foot + 5, TOP + 1, 6):
            for x in range(tx - 2, tx + 3):
                if x != tx:
                    t.set(x, yy, tz + 1, 'or:durasteel_beam[axis=x]')
        # the deck reaches out to the ladder's head
        for x in range(tx - 1, tx + 2):
            for z in range(tz - 4, tz):
                if t.get(x, TOP, z) in (None, AIR):
                    t.set(x, TOP, z, 'or:durasteel_grate')
                for yy in range(TOP + 1, TOP + 4):
                    if not B.name_of(t.get(x, yy, z) or 'minecraft:air').endswith('_fence'):
                        t.set(x, yy, z, AIR)
        t.set(tx, TOP + 1, tz - 1, AIR)
        # the way off the ladder: the deck's rail opens in front of its head, and the landing beside the head
        # is railed on its open sides
        t.set(tx, TOP + 1, tz - 2, AIR)
        for x in (tx - 2, tx + 2):
            t.set(x, TOP, tz - 1, 'or:durasteel_grate')
            t.set(x, TOP + 1, tz - 1, S('spruce_fence'))
        for x in (tx - 1, tx + 1):
            t.set(x, TOP, tz, 'or:durasteel_beam[axis=x]')
            t.set(x, TOP + 1, tz, S('spruce_fence'))
        # the winch house over the track's head
        wy = TOP + 1
        for x in (tx - 2, tx + 2):
            for yy in range(wy, wy + 3):
                t.set(x, yy, tz, 'or:durasteel_beam[axis=y]')
        for x in range(tx - 2, tx + 3):
            t.set(x, wy + 3, tz, 'or:durasteel_beam[axis=x]')
            t.set(x, wy + 3, tz + 1, G.slab('or:durasteel_plating_slab'))
        t.set(tx + 1, wy, tz - 2, S('grindstone', face='floor', facing='east'))
        t.set(tx - 1, wy, tz - 2, S('or:console', facing='south'))
        # the cage, stuck halfway, hanging on its chains
        cy = F + 15
        for yy in range(cy + 3, wy + 3):
            t.set(tx, yy, tz + 1, G.chain('y'))
        for x in range(tx - 1, tx + 2):
            for z in (tz + 1, tz + 2, tz + 3):
                t.set(x, cy - 1, z, 'or:durasteel_grate')
                t.set(x, cy + 2, z, G.slab('or:durasteel_plating_slab', 'top'))
                for yy in (cy, cy + 1):
                    edge = x != tx or z == tz + 3
                    t.set(x, yy, z, S('iron_bars') if edge else AIR)
        t.set(tx, cy, tz + 2, 'or:cargo_crate')
        # the cage's landing at the foot: a plated pad, a lamp, the winch skiff's crates
        for x in range(tx - 2, tx + 3):
            for z in range(tz + 1, min(SZ, tz + 4)):
                t.set(x, g.feet(x, z) - 1, z, 'or:durasteel_deck')
        G.lamp_post(t, tx + 3, g.feet(tx + 3, tz + 2), tz + 2, height=3)
        t.set(tx - 3, g.feet(tx - 3, tz + 2), tz + 2, 'or:cargo_crate')
        spots['ladder foot'] = (tx, g.feet(tx, tz + 1), tz + 1)
        LADDER_HEAD[variant] = (tx, TOP + 1, tz - 1)
    else:
        # switchback flights of timber stairs pinned to the south face, landings at the turns; the last step
        # comes up level with the deck
        face_z = 15
        steps = []
        x, yy, dirx = 4, F, 1
        flights = 0
        done = False
        lx = x
        while not done:
            n = 8 if flights else 6
            lane = face_z + 2 * (flights % 2)
            for k in range(n):
                for z in (lane, lane + 1):
                    t.set(x, yy, z, G.stair('spruce_stairs', 'east' if dirx > 0 else 'west'))
                    for h in (1, 2, 3):
                        t.set(x, yy + h, z, AIR)
                    if t.get(x, yy - 1, z) in (None, AIR):
                        t.set(x, yy - 1, z, 'spruce_slab[type=top,waterlogged=false]')
                if lane > face_z:
                    t.set(x, yy + 1, lane + 2, S('spruce_fence'))
                steps.append((x, yy, lane))
                x += dirx
                yy += 1
                if yy > TOP:
                    done = True
                    break
            if done:
                break
            # the landing: two cells of planks across both lanes, a rail on its outer edges, then turn
            for k in range(2):
                for z in range(face_z, face_z + 4):
                    t.set(x, yy - 1, z, 'spruce_planks')
                    for h in (0, 1, 2):
                        t.set(x, yy + h, z, AIR)
                t.set(x, yy, face_z + 4, S('spruce_fence'))
                if k == 0:
                    lx = x
                x += dirx
            x -= dirx
            t.set(x, yy, face_z + 4, S('spruce_fence'))
            if 0 <= x + dirx < SX:
                for z in range(face_z, face_z + 4):
                    if t.get(x + dirx, yy, z) in (None, AIR):
                        t.set(x + dirx, yy, z, S('spruce_fence'))
            # a post under the landing's outer corner down to the rock or the ground
            for d in range(2, 40):
                if yy - d < 0 or t.get(x, yy - d, face_z + 3) not in (None, AIR):
                    break
                t.set(x, yy - d, face_z + 3, 'or:durasteel_beam[axis=y]' if d % 5 else 'spruce_log[axis=y]')
            dirx = -dirx
            x += dirx
            flights += 1
            if flights % 2 == 0:
                t.set(lx, yy + 1, face_z + 4, G.lantern(False))
        # the stair's head: planks level with the deck from the last step in to the cap
        head = [(xx, z) for xx in range(x - 1, x + 2) for z in range(face_z - 5, face_z + 4) if 0 <= xx < SX]
        for (xx, z) in head:
            if t.get(xx, TOP, z) in (None, AIR):
                t.set(xx, TOP, z, 'spruce_planks')
            for h in (1, 2, 3):
                t.set(xx, TOP + h, z, AIR)
        # a rail along its open edges (the drop), never across the last step
        hs = set(head)
        for (xx, z) in head:
            if B.name_of(t.get(xx, TOP, z) or AIR).endswith('_stairs'):
                continue
            for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (xx + dx, z + dz)
                if n not in hs and t.inside(n[0], TOP, n[1]) and t.get(n[0], TOP, n[1]) in (None, AIR) \
                        and t.get(n[0], TOP - 1, n[1]) in (None, AIR):
                    t.set(xx, TOP + 1, z, S('spruce_fence'))
        spots['stair foot'] = (4, F, face_z)
        spots['deck'] = (10, TOP + 1, 12)
        # cut the rock back where it bulges over the flights
        for (sx_, sy_, sz_) in steps:
            for z in (sz_, sz_ + 1):
                for h in (1, 2, 3):
                    if (sx_, sy_ + h, z) in rock:
                        t.set(sx_, sy_ + h, z, AIR)
    g.scatter(0.02, options=[(S('dead_bush'), 4), (S('short_dry_grass'), 3)])
    return t, regions, spots


LADDER_HEAD = {}
CHEST_FRONT = (7, TOP + 1, 5)


def check_walk(t, variant, spots):
    """On foot from the edge to the way up; for the lift also from the ladder's head on to the deck and the chest
    (the walker does not climb ladders, so the climb itself is the one step it cannot see)."""
    head = LADDER_HEAD.get(variant)
    extra = list(spots.items()) + ([] if head else [('chest front', CHEST_FRONT)])
    ok = G.report_walk(t, NAME + '/' + variant, [], extra=extra)
    if head:
        goals = [('deck', (10, TOP + 1, 12)), ('chest front', CHEST_FRONT)]
        bad = ['%s %s' % (l, c) for (l, c) in goals if not G.walk(t, [head], c)[0]]
        if bad:
            print('%s: NOT WALKABLE from the ladder head %s: %s' % (NAME + '/' + variant, head, ', '.join(bad)))
        else:
            print('%s: walk check from the ladder head passed (deck, chest front)' % (NAME + '/' + variant))
        ok = ok and not bad
    return ok


VARIANTS = ('lift', 'stair')


def worldgen():
    adapted = dict(yard_stand=F, clear_from=SY, clear_inset=0, support_depth=8, ramp_rings=2)
    W.pool(NAME + '/start', [W.element(NAME + '/' + v, adapted=adapted) for v in VARIANTS])
    W.structure(NAME, start_pool=NAME + '/start', biome_tag='has_structure/' + NAME, size=1, max_distance=32,
                start_height=1 - BURIED)
    W.structure_set(NAME, [(NAME, 1)], spacing=36, separation=12, salt=960303, placement='clear_of_origin',
                    min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:ghessar_canyons'])
    G.write_loot(LOOT, (2, 4), [
        ('gold_nugget', 5, 2, 6), ('gunpowder', 4, 1, 3), ('bread', 5, 1, 3), ('or:medpac', 2, 1, 1),
        ('or:credit_chit', 4, 1, 3), ('iron_ingot', 3, 1, 2), ('or:sunstone', 1, 1, 1)])


def main(preview_dir=None, only=None):
    for v in VARIANTS:
        if only and v not in only:
            continue
        t, regions, spots = build(v)
        G.save(t, NAME + '/' + v, preview_dir, light_regions=regions)
        check_walk(t, v, spots)
    worldgen()
    r = jigsaw.assemble(**jigsaw.structure_args(NAME))
    print(r.report())


if __name__ == '__main__':
    args = sys.argv[1:]
    main(args[0] if args else None, set(args[1:]) or None)
