"""ghessar/caravan_waystation (worldgen, Ghessar's dunes and salt flats): a round walled caravan stop.

Where the caravan road crosses the open sand there is a ring of mud wall with a gate north and a gate south,
and inside it domes: a well house in the middle of the court over the only water for a day's travel, a
cantina, a lodging house, a storeroom, the water seller's booth and the kajidic's toll booth at the north gate
(the chest). Stables lean against the west wall under canvas, traders' stalls stand round the well house, the
road runs through the court and round the well. It is the kajidic's: crimson cloth on the gate and the booth,
its condenser outside the wall, its grate over the well.

Two variants on the same plan: `kept`, a working stop (water in the well under the grate, goods on the stalls,
lamps lit); `ruined`, the stop after the water failed (the well dry, the domes broken open, the dunes come over
the west wall and into the court, the stalls bare, scavengers living in the cantina: a scavengers spawner).
47 x 47, the feet layer at y 2.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gkit as G  # noqa: E402
from gkit import S, AIR, B, mod, W, Noise, Template  # noqa: E402
from structlib import jigsaw  # noqa: E402

NAME = 'ghessar/caravan_waystation'
SX = SZ = 47
SY = 22
BURIED = 2
F = BURIED
FL = F - 1
C = 23                      # the ring's centre (x and z)
R_IN, R_OUT = 18.0, 20.0    # the ring wall
WALL_TOP = F + 3            # the wall's last full layer
SEED = 960320
LOOT = 'chests/ghessar/caravan_waystation'

WELL = (C, C, 5.0, 6.0)                  # the well house dome: cx, cz, r, h
DOMES = {
    # name: (cx, cz, r, h, door angles)
    'cantina': (35, 23, 5.0, 6.0, (270,)),
    'toll': (30, 10, 3.5, 4.5, (225,)),
    'water': (16, 10, 3.5, 4.5, (135,)),
    'store': (31, 35, 4.0, 5.0, (315,)),
    'lodging': (15, 35, 4.0, 5.0, (45,)),
}
STABLES = (238, 302)                     # the stables' arc (degrees clockwise from north) along the west wall


def dist(x, z):
    return math.hypot(x - C, z - C)


def ang(x, z, cx=C, cz=C):
    """Degrees clockwise from north (-z) round (cx, cz)."""
    return math.degrees(math.atan2(x - cx, -(z - cz))) % 360


def adiff(a, b):
    return abs((a - b + 180) % 360 - 180)


def dome(t, cx, cz, y0, r, h, wall, floor, doors=(), ruin=0.0, seed=0, lamp=True):
    """An adobe dome: a beehive shell of radius r rising h over its floor layer y0, air inside, arched doors
    (2 wide at the foot, 1 at the top, 3 high) at the given angles. ruin: how much of its upper half has fallen
    in (noise threshold). Returns (shell, inside) cell sets (inside after doors)."""
    shell, inside = set(), set()
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            for y in range(y0 + 1, y0 + int(h) + 2):
                dx, dz, dy = x - cx, z - cz, y - y0 - 0.5
                v = (dx * dx + dz * dz) / (r * r) + (dy * dy) / (h * h)
                if v > 1.0:
                    continue
                vin = (dx * dx + dz * dz) / ((r - 1) ** 2) + (dy * dy) / ((h - 1) ** 2)
                (shell if vin > 1.0 else inside).add((x, y, z))
    fall = Noise(seed + 5, 2.2)
    for c in shell:
        x, y, z = c
        if ruin and y - y0 > h * 0.45 and fall.at(x, y, z) > 1.0 - ruin:
            t.set(x, y, z, AIR)
            continue
        t.set(x, y, z, wall(x, y, z))
    for c in inside:
        t.set(*c, AIR)
    for x in range(int(cx - r), int(cx + r + 1)):
        for z in range(int(cz - r), int(cz + r + 1)):
            if (x, y0 + 1, z) in inside:
                t.set(x, y0, z, floor(x, y0, z))
    for a in doors:
        ux, uz = math.sin(math.radians(a)), -math.cos(math.radians(a))
        for c in list(shell):
            x, y, z = c
            along = (x - cx) * ux + (z - cz) * uz
            side = abs(-(x - cx) * uz + (z - cz) * ux)
            if along <= 0 or y > y0 + 3:
                continue
            if side <= (1.0 if y <= y0 + 2 else 0.5):
                t.set(x, y, z, AIR)
                inside.add(c)
        # a step of floor out of the door
        ox, oz = int(round(cx + ux * (r + 0.5))), int(round(cz + uz * (r + 0.5)))
        t.set(ox, y0, oz, floor(ox, y0, oz))
    if lamp:
        top = max(y for (x, y, z) in inside if x == int(round(cx)) and z == int(round(cz)))
        if (int(round(cx)), top + 1, int(round(cz))) in shell and t.get(int(round(cx)), top + 1, int(round(cz))) != AIR:
            t.set(int(round(cx)), top, int(round(cz)), G.chain('y'))
            t.set(int(round(cx)), top - 1, int(round(cz)), G.lantern(True))
        else:
            t.set(int(round(cx)), y0 + 1, int(round(cz)), G.lantern(False))
    return shell, inside


def build(variant):
    ruined = variant == 'ruined'
    seed = SEED + (50 if ruined else 0)
    t = Template(SX, SY, SZ, seed=seed, name=NAME + '/' + variant)
    rng = t.rng
    wall, base, roofm, floorm = G.wall_material('adobe', seed)
    court = G.mix(seed + 3, [('smooth_sandstone', 3), ('packed_mud', 2), ('or:adobe_bricks', 1), ('sandstone', 2)], 3.0)
    road = G.mix(seed + 4, [('coarse_dirt', 3), ('packed_mud', 2), ('sand', 2), ('gravel', 1)], 2.5)

    # ------------------------------------------------------------ ground: the open sand, the court level inside the ring
    g = G.Ground(t, F, 'dunes', seed=seed)
    g.noise(amp=3, scale=9.0)
    for x in range(SX):
        for z in range(SZ):
            if dist(x, z) < R_OUT + 1.5:
                g.h[x][z] = 2 * F
    if ruined:
        # the dunes have come over the west wall and into the court
        g.mound(6, 30, 12, 15, 14, raise_only=True)
        g.mound(14, 27, 8, 10, 8, raise_only=True)
        g.mound(36, 6, 7, 5, 6, raise_only=True)
    else:
        g.drift({(int(C - R_OUT), z) for z in range(10, 37)}, 'west', 6, 5, seed=1)
    g.taper(slope=2)
    g.feather()
    g.apply(clear_above=4)
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if d < R_IN and g.h[x][z] == 2 * F:
                t.set(x, FL, z, court(x, FL, z))
    # the road: north gate to south gate, round the well house
    for z in range(0, SZ):
        for x in range(C - 2, C + 3):
            if g.h[x][z] == 2 * F and abs(dist(x, z) - 7.5) > 1.6:
                if rng.random() < 0.8:
                    t.set(x, FL, z, road(x, FL, z))
    for x in range(SX):
        for z in range(SZ):
            if 6.2 <= dist(x, z) <= 8.6 and g.h[x][z] == 2 * F and rng.random() < 0.8:
                t.set(x, FL, z, road(x, FL, z))

    # ------------------------------------------------------------ the ring wall and its gates
    gaps = []
    if ruined:
        gaps = [(250, 9), (128, 6)]
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if not (R_IN <= d < R_OUT):
                continue
            a = ang(x, z)
            gate = abs(x - C) <= 1 and (z < 8 or z > SZ - 9)
            top = WALL_TOP
            for (ga, gw) in gaps:
                if adiff(a, ga) < gw:
                    top = FL + 1 + int(adiff(a, ga) / gw * 3)
            y_from = (g.h[x][z] + 1) // 2 - 1 if ruined else FL
            for y in range(min(FL, y_from), top + 1):
                if gate and y >= F:
                    t.set(x, y, z, AIR)
                    continue
                t.set(x, y, z, base(x, y, z) if y <= F else wall(x, y, z))
            if not gate and top == WALL_TOP:
                outer = d >= R_IN + 1.0
                if outer:
                    t.set(x, top + 1, z, G.slab('or:adobe_slab') if (x + z) % 3 else wall(x, top + 1, z))
            elif not gate and top < WALL_TOP:
                t.set(x, top + 1, z, G.scatter_pick(rng, [(G.slab('or:adobe_slab'), 3), ('sand', 2), (AIR, 2)]))
    # the gate pylons: rounded towers either side of each gate, a beam over, the kajidic's cloth
    for (gz, out) in ((int(C - R_IN - 1), -1), (int(C + R_IN + 1), 1)):
        for px in (C - 3, C + 3):
            for y in range(FL, F + 6):
                for (dx, dz) in ((0, 0), (0, 1), (0, -1), (-1 if px < C else 1, 0)):
                    t.set(px + dx, y, gz + dz, base(px, y, gz) if y <= F else wall(px + dx, y, gz + dz))
            t.set(px, F + 6, gz, G.slab('or:adobe_slab'))
        broken_beam = ruined and out > 0
        for x in range(C - 2, C + 3):
            if not (broken_beam and x > C):
                t.set(x, F + 4, gz, G.log('stripped_spruce_log', 'x'))
        # the cloths and the banner hang from the beam's outer face (the banner hangs on the beam itself)
        if not ruined:
            for x in (C - 1, C + 1):
                G.drape(t, x, F + 4, gz + out, 2, 'or:canvas_crimson')
            mod.banner(t, C, F + 4, gz + out, 'north' if out < 0 else 'south', base='red', wall=True)
        else:
            G.drape(t, C - 1, F + 4, gz + out, 1, 'or:canvas_crimson')
    # a toll bar at the north gate (a fence gate, open), lamps
    if not ruined:
        for (lx, lz) in ((C - 4, int(C - R_OUT) - 1), (C + 4, int(C - R_OUT) - 1), (C - 4, int(C + R_OUT) + 1),
                         (C + 4, int(C + R_OUT) + 1)):
            G.lamp_post(t, lx, g.feet(lx, lz), lz, height=3)

    # ------------------------------------------------------------ the well house in the middle of the court
    wcx, wcz, wr, wh = WELL
    shell, inside = dome(t, wcx, wcz, FL, wr, wh, wall, floorm, doors=(0, 90, 180, 270), ruin=0.35 if ruined else 0.0,
                         seed=seed + 10, lamp=not ruined)
    regions = [((0, 0, 0, SX - 1, SY - 1, SZ - 1), {c for c in inside if c[1] == F})]
    G.well(t, wcx, wcz, F, depth=4, curb='or:adobe_bricks', frame='spruce_fence', water=not ruined)
    if not ruined:
        # the kajidic's grate over the shaft, a jar, the dipper's bench
        t.set(wcx, F, wcz, S('iron_bars'))
        t.set(wcx, F + 1, wcz, AIR)
        for (x, z) in ((wcx - 3, wcz - 1), (wcx + 3, wcz + 1)):
            G.water_jar(t, x, F, z, full=True)
        t.set(wcx - 3, F, wcz + 1, G.stair('or:adobe_stairs', 'east'))
        t.set(wcx + 3, F, wcz - 1, G.stair('or:adobe_stairs', 'west'))
    else:
        for (x, z) in ((wcx - 3, wcz), (wcx + 2, wcz + 3)):
            t.set(x, F, z, S('cauldron'))
        t.set(wcx + 3, F, wcz - 2, S('bone_block', axis='x'))
        t.set(wcx - 2, F, wcz + 3, S('skeleton_skull', rotation=3))
        for c in inside:
            if c[1] == F and Noise(seed + 11, 2.0).at(*c) > 0.55 and G.free(t, *c):
                t.set(*c, G.slab('sandstone_slab'))

    # ------------------------------------------------------------ the domes round the court
    ruin_of = {'cantina': 0.15, 'toll': 0.1, 'water': 0.4, 'store': 0.45, 'lodging': 0.5} if ruined else {}
    dome_inside = {}
    for name, (cx, cz, r, h, doors) in DOMES.items():
        sh, ins = dome(t, cx, cz, FL, r, h, wall, floorm, doors=doors, ruin=ruin_of.get(name, 0.0), seed=seed + sum(map(ord, name)),
                       lamp=not ruined or name == 'cantina')
        dome_inside[name] = ins
        regions.append(((0, 0, 0, SX - 1, SY - 1, SZ - 1), {c for c in ins if c[1] == F}))
        # a sand step at the door where the court is lower or higher than the floor: the court is level, so none
    # the toll booth: the kajidic's agent's counter, his chest, the crimson cloth and banner
    cx, cz = DOMES['toll'][0], DOMES['toll'][1]
    t.chest(cx + 1, F, cz - 1, 'south', LOOT)
    if ruined:
        t.set(cx - 1, F, cz - 1, G.slab('sandstone_slab'))
        t.set(cx, F, cz + 1, 'sand')
        t.set(cx - 1, F, cz, G.slab('sandstone_slab'))
    else:
        G.slab_table(t, cx - 1, cz, cx, cz, F, top='or:adobe_slab')
        G.chair(t, cx, F, cz - 1, 'south', 'spruce_stairs')
        t.set(cx - 1, F + 1, cz, S('candle', candles=2, lit=True))
        t.set(cx - 2, F, cz - 1, G.barrel('up'))
    if not ruined:
        # the kajidic's banner on the booth's south side, over the court
        for z in range(cz + 2, cz + 6):
            if G.solid_at(t, cx, F + 2, z) and G.free(t, cx, F + 2, z + 1):
                mod.banner(t, cx, F + 2, z + 1, 'south', base='red', wall=True)
                break
    # the water seller's booth: jars, a counter, a tally on the wall
    cx, cz = DOMES['water'][0], DOMES['water'][1]
    if not ruined:
        for (dx, dz) in ((-1, -1), (0, -2), (1, -1)):
            G.water_jar(t, cx + dx, F, cz + dz, full=True)
        G.slab_table(t, cx + 1, cz + 1, cx + 1, cz + 1, F, top='or:adobe_slab')
        t.set(cx - 2, F, cz, G.barrel('up'))
        t.set(cx - 1, F + 1, cz - 2, G.pot('south'))
    else:
        for (dx, dz) in ((-1, -1), (1, -1)):
            t.set(cx + dx, F, cz + dz, S('cauldron'))
        t.set(cx, F, cz, S('decorated_pot', facing='south', cracked=True))
    # the cantina: a bar, tables, stools; in the ruined stop the scavengers' den
    cx, cz = DOMES['cantina'][0], DOMES['cantina'][1]
    if not ruined:
        for z in range(cz - 2, cz + 3):
            t.set(cx + 2, F, z, G.slab('or:adobe_slab', 'top'))
        for z in (cz - 2, cz + 2):
            t.set(cx + 3, F, z, G.barrel('west'))
        t.set(cx + 3, F, cz, S('brewing_stand'))
        for (tx, tz) in ((cx - 2, cz - 2), (cx - 2, cz + 2)):
            G.table(t, tx, F, tz, top='spruce_pressure_plate', leg='spruce_fence')
            G.chair(t, tx - 1, F, tz, 'east', 'spruce_stairs')
            G.chair(t, tx, F, tz - (1 if tz < cz else -1), 'south' if tz < cz else 'north', 'spruce_stairs')
        t.set(cx, F, cz + 3, G.pot('west'))
    else:
        G.bedroll(t, cx + 1, F, cz - 3, 'x', 'brown')
        G.bedroll(t, cx + 1, F, cz + 3, 'x', 'gray')
        t.set(cx, F, cz, G.campfire(True))
        for (dx, dz) in ((1, 0), (0, 1)):
            t.set(cx + dx, F, cz + dz, 'or:scorch_mark')
        t.set(cx + 3, F, cz - 1, 'or:cargo_crate')
        t.set(cx + 3, F, cz, 'or:cargo_crate_republic')
        t.set(cx + 3, F + 1, cz, G.barrel('up'))
        mod.spawner(t, cx - 2, F, cz + 1, 'scavengers', count=3)
    # the storeroom: crates and sacks
    cx, cz = DOMES['store'][0], DOMES['store'][1]
    for (dx, dz) in ((-1, -2), (0, -2), (1, -1), (2, 0), (2, 1), (-2, 1)):
        x, z = cx + dx, cz + dz
        if (x, F, z) in dome_inside['store'] and G.free(t, x, F, z):
            G.crate_stack(t, x, F, z, 1 + (rng.random() < 0.4) * (0 if ruined else 1), rng,
                          kinds=[('or:cargo_crate', 4), (G.barrel('up'), 2), ('hay_block', 1)])
    # the lodging house: bedrolls round a brazier
    cx, cz = DOMES['lodging'][0], DOMES['lodging'][1]
    for (dx, dz, al, col) in ((-2, -1, 'z', 'red'), (2, -1, 'z', 'brown'), (-1, 2, 'x', 'white')):
        x, z = cx + dx, cz + dz
        if ruined and rng.random() < 0.5:
            continue
        G.bedroll(t, x, F, z, al, col)
    if not ruined:
        t.set(cx, F, cz, G.campfire(True))

    # ------------------------------------------------------------ the stables along the west wall
    post_d, back_d = 12.5, R_IN
    roof_y = F + 3
    sh_rng = Noise(seed + 21, 2.5)
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            a = ang(x, z)
            if not (STABLES[0] <= a <= STABLES[1]) or not (post_d - 0.5 <= d < back_d):
                continue
            if g.h[x][z] > 2 * F + 1:
                continue
            if ruined and sh_rng.at(x, 0, z) > 0.45:
                continue
            t.set(x, roof_y if d < 15.5 else roof_y + 1, z, G.slab('or:canvas_slab', 'top') if (int(a) // 6) % 2 else 'or:canvas_navy')
    for a in range(STABLES[0], STABLES[1] + 1, 8):
        x = int(round(C + math.sin(math.radians(a)) * post_d))
        z = int(round(C - math.cos(math.radians(a)) * post_d))
        if g.h[x][z] <= 2 * F + 1 and not (ruined and rng.random() < 0.5):
            for y in range(F, roof_y):
                t.set(x, y, z, S('spruce_fence'))
        # stall dividers out from the wall, troughs and hay
        for dd in (15.0, 16.0, 17.0):
            xx = int(round(C + math.sin(math.radians(a)) * dd))
            zz = int(round(C - math.cos(math.radians(a)) * dd))
            if g.h[xx][zz] == 2 * F and G.free(t, xx, F, zz):
                t.set(xx, F, zz, S('spruce_fence'))
    for a in range(STABLES[0] + 4, STABLES[1], 8):
        xx = int(round(C + math.sin(math.radians(a)) * 17.0))
        zz = int(round(C - math.cos(math.radians(a)) * 17.0))
        if g.h[xx][zz] != 2 * F or not G.free(t, xx, F, zz):
            continue
        if ruined:
            t.set(xx, F, zz, G.scatter_pick(rng, [('sand', 2), (S('bone_block', axis='y'), 1), (AIR, 2)]))
        else:
            t.set(xx, F, zz, S('water_cauldron', level=1) if rng.random() < 0.5 else S('hay_block', axis='y'))
            xi = int(round(C + math.sin(math.radians(a)) * 15.0))
            zi = int(round(C - math.cos(math.radians(a)) * 15.0))
            if G.free(t, xi, F, zi) and rng.random() < 0.5:
                t.set(xi, F, zi, S('hay_block', axis='x'))

    # ------------------------------------------------------------ the traders' stalls round the well house
    stalls = [((28, 15, 31, 17), 'or:canvas_navy'), ((14, 15, 17, 17), 'or:canvas_crimson'),
              ((28, 29, 31, 31), 'or:canvas'), ((18, 30, 21, 32), 'or:canvas_navy')]
    goods = [('or:cargo_crate', 3), (G.barrel('up'), 2), (G.pot('north'), 2), ('hay_block', 1), ('white_wool', 1),
             ('red_wool', 1)]
    for (x0, z0, x1, z1), stripe in stalls:
        if ruined:
            for (px, pz) in ((x0, z0), (x1, z1)):
                for y in range(F, F + (3 if rng.random() < 0.5 else 2)):
                    t.set(px, y, pz, S('spruce_fence'))
            if rng.random() < 0.5:
                t.set(x0, F + 3, z0, G.slab('or:canvas_slab', 'top'))
            continue
        G.awning(t, x0, z0, x1, z1, F + 3, cloth='or:canvas_slab', stripes=stripe, posts='spruce_fence', post_from=F)
        for x in range(x0 + 1, x1):
            t.set(x, F, z0 + 1, G.slab('or:adobe_slab', 'top'))
        for x in range(x0 + 1, x1):
            if rng.random() < 0.7:
                t.set(x, F + 1, z0 + 1, G.scatter_pick(rng, goods) if rng.random() < 0.6 else S('candle', candles=1 + int(rng.random() * 3), lit=False))
        t.set(x0 + 1, F, z1, G.barrel('up'))

    # ------------------------------------------------------------ outside the wall: the caravan's skiff, the condenser, posts
    G.skiff(t, 4, g.feet(8, 4), 2, length=9, width=5, along='x', wreck=ruined)
    G.condenser(t, 41, g.feet(41, 9), 9, height=6, owned=not ruined)
    G.claim_pole(t, C - 5, g.feet(C - 5, 1), 1, height=7, cloth_side='east', banner_side='north')
    for (hx, hz) in ((C + 6, 1), (C + 7, 1), (C + 8, 1)):
        if G.free(t, hx, g.feet(hx, hz), hz):
            t.set(hx, g.feet(hx, hz), hz, S('spruce_fence'))
    if ruined:
        G.ribcage(t, 36, g.feet(36, 41), 41, 6, along='x', span=2, height=3, seed=seed + 31)
        G.scrap_heap(t, 8, g.feet(8, 40), 40, 2.0, 1.5, seed + 32)
    g.scatter(0.012)
    return t, regions


def check_walk(t, variant):
    extra = [('north gate', (C, F, int(C - R_IN) - 2)), ('south gate', (C, F, int(C + R_IN) + 2)),
             ('chest front', (DOMES['toll'][0] + 1, F, DOMES['toll'][1])),
             ('well house', (C, F, C - 3)), ('cantina', (DOMES['cantina'][0] - 1, F, DOMES['cantina'][1]))]
    return G.report_walk(t, NAME + '/' + variant, [], extra=extra)


VARIANTS = ('kept', 'ruined')


def worldgen():
    adapted = dict(yard_stand=F, clear_from=SY, clear_inset=0, support_depth=6, ramp_rings=2)
    W.pool(NAME + '/start', [W.element(NAME + '/' + v, adapted=adapted) for v in VARIANTS])
    W.structure(NAME, start_pool=NAME + '/start', biome_tag='has_structure/' + NAME, size=1, max_distance=48,
                start_height=1 - BURIED)
    W.structure_set(NAME, [(NAME, 1)], spacing=44, separation=16, salt=960301, placement='clear_of_origin',
                    min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:ghessar_dunes', 'oldrepublic:ghessar_saltflat'])
    G.write_loot(LOOT, (2, 4), [
        ('bread', 6, 1, 3), ('leather', 5, 1, 3), ('string', 4, 2, 4), ('gold_nugget', 4, 2, 6),
        ('or:credit_chit', 5, 1, 3), ('paper', 3, 1, 3), ('emerald', 1, 1, 1), ('or:medpac', 2, 1, 1)])


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
