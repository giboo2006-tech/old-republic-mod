"""ghessar/salt_works (worldgen, Ghessar's salt flats): where the kajidic makes salt out of buried water.

Under the flat lies brine. A derrick over a bore pumps it up into a grid of shallow pans; the sun does the
rest. The pans run from fresh brine through bitter liquor to the white crust the rakers pull into rows, and a
conveyor on trestles carries the raked salt to the refinery shed, where it is dried, sacked and stacked for the
skiffs. The foreman's office (the chest) flies the kajidic's cloth; the rakers sleep in a container bunkhouse.

Two variants on the same plan: `working` (brine in the pans, salt on the belt, the pump's fire lit) and
`abandoned` (the bore ran dry: the pans cracked white, the derrick down across them, the shed's roof half
gone, drifts of salt in its doors, and a sand scour brood nesting in it: a scour_brood spawner).
53 x 53, the feet layer at y 2.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gkit as G  # noqa: E402
from gkit import S, AIR, B, mod, W, Noise, Template  # noqa: E402
from structlib import jigsaw  # noqa: E402

NAME = 'ghessar/salt_works'
SX = SZ = 53
SY = 24
BURIED = 2
F = BURIED
FL = F - 1
SEED = 960330
LOOT = 'chests/ghessar/salt_works'

PAN_X = (5, 13, 21)          # pan interiors: x0 .. x0 + 6
PAN_Z = (17, 24, 31, 38)     # pan interiors: z0 .. z0 + 5
PAN_W, PAN_D = 7, 6
SHED = (35, 24, 48, 34)      # x0, z0, x1, z1 (walls on the box's edge)
BORE = (10, 8)               # the brine bore under the derrick
BELT_Z = (29, 30)            # the conveyor's two lanes
BELT_Y = F + 2
OFFICE = (38, 5)             # the foreman's container: x0, z0 (7 x 5)
BUNK = (40, 13)              # the bunkhouse: x0, z0 (8 x 5)


def stage(i, j, abandoned):
    """What a pan holds: 0 fresh brine, 1 bitter liquor, 2 crystallising, 3 raked salt."""
    if abandoned:
        return 4
    return (i + j * 2) % 4 if j < 3 else 3


def pans(t, g, abandoned, rng):
    crust = G.mix(SEED + 5, [('or:salt_crust', 5), ('calcite', 2), ('white_concrete_powder', 1)], 2.0)
    cracked = G.mix(SEED + 6, [('or:salt_crust', 6), ('calcite', 2), ('gravel', 1), ('sand', 1)], 2.5)
    rim = 'or:salt_bricks'
    bloom = Noise(SEED + 7, 1.6)
    for i, x0 in enumerate(PAN_X):
        for j, z0 in enumerate(PAN_Z):
            st = stage(i, j, abandoned)
            # walkway and rim round it: salt bricks at the ground layer, a slab kerb
            for x in range(x0 - 1, x0 + PAN_W + 1):
                for z in range(z0 - 1, z0 + PAN_D + 1):
                    edge = x in (x0 - 1, x0 + PAN_W) or z in (z0 - 1, z0 + PAN_D)
                    if edge:
                        t.set(x, FL, z, rim)
                        t.set(x, FL - 1, z, rim)
                        continue
                    t.set(x, FL - 2, z, rim)
                    if st == 0:
                        t.set(x, FL - 1, z, 'or:salt_crust')
                        t.set(x, FL, z, S('water'))
                    elif st == 1:
                        t.set(x, FL - 1, z, 'calcite')
                        t.set(x, FL, z, S('water') if bloom.at(x, 0, z) < 0.68 else 'or:salt_crust')
                    elif st == 2:
                        t.set(x, FL - 1, z, 'calcite')
                        t.set(x, FL, z, crust(x, FL, z) if bloom.at(x, 0, z) > 0.4 else S('water'))
                    elif st == 3:
                        t.set(x, FL - 1, z, 'calcite')
                        t.set(x, FL, z, 'or:salt_crust')
                        # raked into rows
                        if (z - z0) % 2 == 0 and x0 < x < x0 + PAN_W - 1:
                            t.set(x, F, z, G.slab('smooth_quartz_slab'))
                    else:
                        t.set(x, FL - 1, z, 'calcite')
                        t.set(x, FL, z, cracked(x, FL, z))
                        if rng.random() < 0.04:
                            t.set(x, F, z, S('bone_block', axis='y') if rng.random() < 0.3 else 'or:debris')
                    for y in range(F, F + 3):
                        if t.get(x, y, z) is None:
                            t.set(x, y, z, AIR)
            # the kerb: slabs along the pan's long sides (walkable half steps), sluice gaps
            for x in range(x0, x0 + PAN_W):
                for z in (z0 - 1, z0 + PAN_D):
                    if x != x0 + 3:
                        t.set(x, F, z, G.slab('or:salt_brick_slab'))
            # the rakers' tools leaning on a heap in a raked pan
            if st == 3 and (i + j) % 2 == 0:
                hx, hz = x0 + PAN_W - 2, z0 + 1
                t.set(hx, F, hz, 'or:salt_crust')
                t.set(hx, F + 1, hz, G.slab('smooth_quartz_slab'))
                t.set(hx + 1, F, hz, G.slab('smooth_quartz_slab'))


def derrick(t, g, abandoned, rng):
    """The brine bore: a lattice derrick over a cased hole, the pump house beside it, the feed pipe along the
    pans' heads. Abandoned: the derrick lies across the pans where it fell."""
    bx, bz = BORE
    # the bore: a cased shaft
    for y in range(0, F):
        t.set(bx, y, bz, S('water') if (y < FL and not abandoned) else AIR if not abandoned else 'gravel')
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        t.set(bx + dx, FL, bz + dz, 'or:durasteel_plating')
        t.set(bx + dx, F, bz + dz, 'or:durasteel_pipes[axis=y]' if (dx == 0 or dz == 0) else S('iron_bars'))
    t.set(bx, F, bz, 'or:durasteel_vent')
    top = F + 13
    if not abandoned:
        for y in range(F, top):
            for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
                k = (top - y) / float(top - F)
                ox = int(round(dx * (0.45 + 0.55 * k)))
                oz = int(round(dz * (0.45 + 0.55 * k)))
                t.set(bx + ox, y, bz + oz, 'or:durasteel_beam[axis=y]')
            if y % 4 == 1:
                k = (top - y) / float(top - F)
                r = int(round(2 * (0.45 + 0.55 * k)))
                for d in range(-r, r + 1):
                    for (sx_, sz_) in ((d, -r), (d, r)):
                        t.set(bx + sx_, y, bz + sz_, 'or:durasteel_beam[axis=x]')
                    for (sx_, sz_) in ((-r, d), (r, d)):
                        t.set(bx + sx_, y, bz + sz_, 'or:durasteel_beam[axis=z]')
        t.set(bx, top, bz, 'or:durasteel_plating')
        t.set(bx, top + 1, bz, G.rod('up', 'weathered_lightning_rod'))
        for y in range(F + 1, top):
            t.set(bx, y, bz, G.chain('y') if y > F + 3 else 'or:durasteel_pipes[axis=y]')
    else:
        # fallen east across the walkway between the first pans, the crown in the sand
        for k in range(0, 13):
            x = bx + 1 + k
            y = F + max(0, 2 - k // 5)
            for dz in (-1, 1):
                t.set(x, y, bz + dz, 'or:durasteel_beam[axis=x]')
            if k % 4 == 0:
                t.set(x, y, bz, 'or:durasteel_beam[axis=z]')
        for (dx, dz) in ((-2, -2), (-2, 2)):
            for y in range(F, F + 3):
                t.set(bx + dx, y, bz + dz, 'or:durasteel_beam[axis=y]')
    # the pump house: a plated hut with the boiler's fire, pipes out to the pans
    px0, pz0 = bx - 5, bz - 3
    for x in range(px0, px0 + 4):
        for z in range(pz0, pz0 + 5):
            edge = x in (px0, px0 + 3) or z in (pz0, pz0 + 4)
            t.set(x, FL, z, 'or:durasteel_deck')
            for y in range(F, F + 3):
                if edge and not (x == px0 + 3 and z == pz0 + 2 and y < F + 2):
                    if abandoned and y == F + 2 and rng.random() < 0.4:
                        continue
                    t.set(x, y, z, 'or:rusted_plating' if (x + y + z) % 3 == 0 else 'or:wreck_plating')
                else:
                    t.set(x, y, z, AIR)
            if not abandoned or rng.random() < 0.5:
                t.set(x, F + 3, z, G.slab('or:durasteel_plating_slab'))
    t.set(px0 + 1, F, pz0 + 1, S('smoker', facing='east', lit=not abandoned))
    t.set(px0 + 1, F + 1, pz0 + 1, 'or:durasteel_pipes[axis=y]')
    t.set(px0 + 1, F + 2, pz0 + 1, 'or:durasteel_pipes[axis=y]')
    t.set(px0 + 1, F + 3, pz0 + 1, 'or:durasteel_vent')
    if not abandoned:
        t.set(px0 + 1, F + 4, pz0 + 1, G.campfire(True))
    t.set(px0 + 2, F, pz0 + 3, S('or:console', facing='west'))
    t.set(px0 + 1, F, pz0 + 3, G.barrel('up'))
    if not abandoned:
        t.set(px0 + 2, F + 2, pz0 + 2, G.lantern(True))
    # the feed pipe: from the bore along z 15 over the pans' heads
    for x in range(bx + 2, PAN_X[-1] + PAN_W + 1):
        if abandoned and 14 <= x <= 17:
            continue
        t.set(x, F, 15, 'or:durasteel_pipes[axis=x]')
    for x0 in PAN_X:
        t.set(x0 + 3, F, 16, 'or:durasteel_pipes[axis=z]' if not abandoned else 'or:debris')


def shed(t, g, abandoned, rng):
    """The refinery shed: plated walls on a salt-brick footing, a roof of plating on beams, the belt coming in
    through the west wall, the driers and the sacking floor inside, big doors south."""
    x0, z0, x1, z1 = SHED
    roof_y = F + 5
    hole = Noise(SEED + 41, 3.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set(x, FL, z, 'or:salt_bricks' if edge else 'or:durasteel_deck' if (x + z) % 5 else 'or:durasteel_grate')
            for y in range(F, roof_y):
                if edge:
                    corner = x in (x0, x1) and z in (z0, z1)
                    if corner or (x - x0) % 4 == 0 and z in (z0, z1) or (z - z0) % 4 == 0 and x in (x0, x1):
                        t.set(x, y, z, 'or:durasteel_beam[axis=y]')
                    elif abandoned and y >= F + 3 and hole.at(x, y, z) > 0.6:
                        t.set(x, y, z, AIR)
                    else:
                        t.set(x, y, z, 'or:rusted_plating' if hole.at(x, y * 3, z) > 0.62 else
                              'or:durasteel_plating' if y < F + 3 else 'or:wreck_plating')
                else:
                    t.set(x, y, z, AIR)
            if abandoned and hole.at(x, 50, z) > 0.45:
                continue
            t.set(x, roof_y, z, 'or:durasteel_beam[axis=x]' if (x - x0) % 4 == 0 else G.slab('or:durasteel_plating_slab'))
    # windows: slits of bars along the long walls
    for x in range(x0 + 2, x1 - 1, 4):
        for z in (z0, z1):
            if G.solid_at(t, x, F + 2, z):
                t.set(x, F + 2, z, S('iron_bars'))
                t.set(x + 1, F + 2, z, S('iron_bars'))
    # doors: the belt's port west, a door north, the big doors south
    for z in BELT_Z:
        for y in (BELT_Y, BELT_Y + 1):
            t.set(x0, y, z, AIR)
    for y in range(F, F + 3):
        t.set(x0 + 6, y, z0, AIR)
        t.set(x0 + 7, y, z0, AIR)
        for x in range(x0 + 5, x0 + 9):
            t.set(x, y, z1, AIR)
    t.set(x0 + 6, FL, z0 - 1, 'or:salt_bricks')
    t.set(x0 + 7, FL, z0 - 1, 'or:salt_bricks')
    # inside: the belt's end over a hopper line, the driers, sacks stacked, the weigh scale
    for x in range(x0 + 1, x0 + 5):
        for z in BELT_Z:
            t.set(x, BELT_Y, z, G.slab('or:durasteel_deck_slab', 'top'))
    for z in BELT_Z:
        t.set(x0 + 5, BELT_Y - 1, z, S('hopper', facing='down'))
        t.set(x0 + 5, BELT_Y - 2, z, 'or:durasteel_plating')
    for x in range(x0 + 7, x1 - 1, 3):
        t.set(x, F, z0 + 2, S('blast_furnace', facing='south', lit=not abandoned))
        t.set(x, F + 1, z0 + 2, 'or:durasteel_vent')
        for y in range(F + 2, roof_y):
            t.set(x, y, z0 + 2, 'or:durasteel_pipes[axis=y]')
    sacks = [('or:canvas', 5), ('white_wool', 2), ('or:cargo_crate', 1)]
    for x in range(x0 + 7, x1):
        for z in range(z0 + 5, z1 - 1):
            if (x - x0) % 3 == 0:
                continue
            if abandoned and rng.random() < 0.55:
                continue
            h = 1 + int(rng.random() * (3 if not abandoned else 2))
            for k in range(h):
                t.set(x, F + k, z, G.scatter_pick(rng, sacks))
    t.set(x0 + 2, F, z1 - 2, 'or:durasteel_plating')
    t.set(x0 + 2, F + 1, z1 - 2, S('or:console', facing='east'))
    t.set(x0 + 3, F, z1 - 2, G.slab('or:durasteel_deck_slab'))
    if not abandoned:
        for x in (x0 + 4, x0 + 9):
            G.hanging_lamp(t, x, roof_y - 1, (z0 + z1) // 2, drop=1)
    else:
        # the scour brood in the sacking floor, salt drifted in at the doors, bones
        mod.spawner(t, x0 + 4, F, (z0 + z1) // 2, 'scour_brood', count=3)
        for x in range(x0 + 4, x0 + 10):
            for z in (z1 - 1, z1 - 2):
                if G.free(t, x, F, z) and rng.random() < 0.6:
                    t.set(x, F, z, G.slab('smooth_quartz_slab'))
        t.set(x0 + 2, F, z0 + 3, S('bone_block', axis='z'))
        t.set(x0 + 3, F, z0 + 4, S('skeleton_skull', rotation=5))
    return (x0 + 1, F, z0 + 1, x1 - 1, roof_y - 1, z1 - 1)


def belt(t, g, abandoned, rng):
    """The conveyor: two lanes of deck slabs on beam trestles from the raked pans to the shed's west port."""
    xa, xb = PAN_X[-1] + PAN_W - 3, SHED[0]
    for x in range(xa, xb):
        if abandoned and 30 <= x <= 32:
            continue
        for z in BELT_Z:
            t.set(x, BELT_Y, z, G.slab('or:durasteel_deck_slab', 'top'))
            if not abandoned and rng.random() < 0.18:
                t.set(x, BELT_Y + 1, z, G.slab('smooth_quartz_slab') if rng.random() < 0.6 else 'or:canvas')
        if (x - xa) % 3 == 0:
            for z in (BELT_Z[0] - 1, BELT_Z[1] + 1):
                for y in range(g.feet(x, z) if g.inside(x, z) else F, BELT_Y + 1):
                    t.set(x, y, z, 'or:durasteel_beam[axis=y]')
        for z in (BELT_Z[0] - 1, BELT_Z[1] + 1):
            t.set(x, BELT_Y + 1 if (x - xa) % 3 == 0 else BELT_Y, z, 'or:durasteel_beam[axis=x]')
    # the loader at the pans' end: a hopper on a frame over the last raked pan's head
    lx = xa
    for z in BELT_Z:
        t.set(lx - 1, BELT_Y, z, S('hopper', facing='east'))
    for y in range(F, BELT_Y):
        t.set(lx - 1, y, BELT_Z[0] - 1, 'or:durasteel_beam[axis=y]')
        t.set(lx - 1, y, BELT_Z[1] + 1, 'or:durasteel_beam[axis=y]')


def heaps(t, g, abandoned, rng):
    """Salt heaps south of the shed, white cones with slab skirts."""
    pile = G.mix(SEED + 51, [('or:salt_crust', 4), ('calcite', 3), ('white_concrete_powder', 1), ('smooth_quartz', 1)], 2.0)
    for (cx, cz, r, h) in ((37, 42, 4.0, 4.5), (44, 41, 3.2, 3.5), (41, 47, 2.6, 2.5)):
        if abandoned:
            h *= 0.6
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.hypot(x - cx, z - cz) / r
                if d > 1.1 or not g.inside(x, z):
                    continue
                hh = h * max(0.0, 1 - d) ** 0.9
                y0 = g.feet(x, z)
                k = int(hh)
                for y in range(y0, y0 + k):
                    t.set(x, y, z, pile(x, y, z))
                if hh - k >= 0.35 or (k == 0 and d <= 1.0):
                    t.set(x, y0 + k, z, G.slab('smooth_quartz_slab'))


def build(variant):
    abandoned = variant == 'abandoned'
    seed = SEED + (50 if abandoned else 0)
    t = Template(SX, SY, SZ, seed=seed, name=NAME + '/' + variant)
    rng = t.rng
    g = G.Ground(t, F, 'salt', seed=seed)
    g.noise(amp=1, scale=10.0)
    for x in range(3, 50):
        for z in range(3, 50):
            g.h[x][z] = 2 * F
    if abandoned:
        g.drift({(SHED[0] + 5 + k, SHED[3] + 1) for k in range(4)}, 'south', 3, 5, seed=2)
        g.mound(48, 46, 5, 6, 4)
    g.taper(slope=2)
    g.feather()
    g.apply(clear_above=4)
    pans(t, g, abandoned, rng)
    derrick(t, g, abandoned, rng)
    box = shed(t, g, abandoned, rng)
    belt(t, g, abandoned, rng)
    heaps(t, g, abandoned, rng)
    # the foreman's office (the chest) and the bunkhouse: cargo containers, the kajidic's cloth and pole
    ox, oz = OFFICE
    obox = G.container_shack(t, ox, F, oz, length=7, width=5, along='x', door_side='south', seed=seed + 61)
    t.chest(ox + 1, F, oz + 1, 'south', LOOT)
    G.slab_table(t, ox + 3, oz + 1, ox + 4, oz + 1, F, top='or:durasteel_plating_slab')
    t.set(ox + 3, F + 1, oz + 1, S('or:holo_screen', facing='south'))
    G.chair(t, ox + 4, F, oz + 2, 'north', 'or:durasteel_plating_stairs')
    t.set(ox + 5, F, oz + 1, G.barrel('up'))
    if not abandoned:
        t.set(ox + 2, F + 2, oz + 2, G.lantern(True))
        mod.banner(t, ox + 1, F + 2, oz + 5, 'south', base='red', wall=True)
    bx, bz = BUNK
    bbox = G.container_shack(t, bx, F, bz, length=8, width=5, along='x', door_side='west', seed=seed + 62)
    for k, col in enumerate(('brown', 'gray', 'white')):
        G.bedroll(t, bx + 2 + 2 * k, F, bz + 1, 'z', col)
    t.set(bx + 6, F, bz + 3, G.barrel('up'))
    if not abandoned:
        t.set(bx + 4, F + 2, bz + 2, G.lantern(True))
    G.claim_pole(t, ox - 2, g.feet(ox - 2, oz + 6), oz + 6, height=7, cloth_side='east', banner_side='west')
    G.condenser(t, 47, g.feet(47, 9), 9, height=6, owned=True)
    # the loading pad and a skiff; lamps along the walkways
    for x in range(31, 39):
        for z in range(16, 21):
            t.set(x, FL, z, 'or:salt_bricks' if (x + z) % 4 else 'or:durasteel_deck')
    G.skiff(t, 31, F, 16, length=8, width=5, along='x', wreck=abandoned)
    if not abandoned:
        for (x, z) in ((29, 15), (29, 23), (20, 44), (33, 37), (12, 15)):
            G.lamp_post(t, x, g.feet(x, z), z, height=3)
    else:
        G.scrap_heap(t, 34, g.feet(34, 12), 12, 1.8, 1.2, seed + 63)
    g.scatter(0.006, options=[(S('dead_bush'), 3), (S('short_dry_grass'), 2)])
    regions = [box, obox, bbox]
    SPOTS['heaps'] = (40, g.feet(40, 38), 38)
    return t, regions


SPOTS = {}


def check_walk(t, variant):
    extra = [('chest front', (OFFICE[0] + 1, F, OFFICE[1] + 2)), ('shed floor', (SHED[0] + 6, F, SHED[1] + 3)),
             ('pans walkway', (PAN_X[1] - 1, F, PAN_Z[2] + 2)), ('bore', (BORE[0] + 3, F, BORE[1])),
             ('heaps', SPOTS['heaps'])]
    return G.report_walk(t, NAME + '/' + variant, [], extra=extra)


VARIANTS = ('working', 'abandoned')


def worldgen():
    adapted = dict(yard_stand=F, clear_from=SY, clear_inset=0, support_depth=6, ramp_rings=2)
    W.pool(NAME + '/start', [W.element(NAME + '/' + v, adapted=adapted) for v in VARIANTS])
    W.structure(NAME, start_pool=NAME + '/start', biome_tag='has_structure/' + NAME, size=1, max_distance=48,
                start_height=1 - BURIED)
    W.structure_set(NAME, [(NAME, 1)], spacing=40, separation=14, salt=960302, placement='clear_of_origin',
                    min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:ghessar_saltflat'])
    G.write_loot(LOOT, (2, 4), [
        ('bread', 5, 1, 3), ('iron_nugget', 5, 2, 6), ('copper_ingot', 4, 1, 3), ('charcoal', 4, 2, 5),
        ('or:credit_chit', 5, 1, 3), ('torch', 3, 2, 4), ('string', 3, 1, 3), ('or:medpac', 2, 1, 1)])


def main(preview_dir=None, only=None):
    for v in VARIANTS:
        if only and v not in only:
            continue
        t, regions = build(v)
        G.save(t, NAME + '/' + v, preview_dir, light_regions=regions)
        check_walk(t, v)
    worldgen()
    r = jigsaw.assemble(**jigsaw.structure_args(NAME))
    print(r.report())


if __name__ == '__main__':
    args = sys.argv[1:]
    main(args[0] if args else None, set(args[1:]) or None)
