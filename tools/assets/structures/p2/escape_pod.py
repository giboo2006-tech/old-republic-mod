"""ghessar/escape_pod (worldgen, Ghessar's dunes and salt flats): an escape pod in its own crater.

Something came down hard: a bowl punched into the ground with a rim of thrown sand, glassed and scorched in
the middle, and in it a ship's escape pod stuck nose-down at a slant, its retro nozzles in the air and its
hatch blown. Whoever rode it down is gone or is bones; what they did not carry off is in the pod's locker
(the chest).

Two variants: `republic`, a white pod; the survivor camped in its shade for a while (a bedroll, a cold fire, a
drogue chute spread on the rim to be seen from the air) and then walked; `imperial`, a dark pod whose hatch was
blown from inside, its rider still sitting by it as bones. 19 x 19, the feet layer at y 4 (the crater's floor
lower).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gkit as G  # noqa: E402
from gkit import S, AIR, B, mod, W, Noise, Template  # noqa: E402
from structlib import jigsaw  # noqa: E402
import freighter_graveyard as FGY  # noqa: E402

NAME = 'ghessar/escape_pod'
SX = SZ = 19
SY = 17
BURIED = 4
F = BURIED
FL = F - 1
C = 9
SEED = 960306
LOOT = 'chests/ghessar/escape_pod'
POD_KEYS = [(0, 0, 0, 0.9, 0.9, 2.0), (0.8, 0, 0, 2.0, 2.0, 2.0), (1.6, 0, 0, 2.4, 2.4, 2.2), (6.0, 0, 0, 2.4, 2.4, 2.2),
            (7.2, 0, 0, 1.9, 1.9, 2.0), (8.0, 0, 0, 0.9, 0.9, 2.0)]


def build(variant):
    imperial = variant == 'imperial'
    seed = SEED + (50 if imperial else 0)
    t = Template(SX, SY, SZ, seed=seed, name=NAME + '/' + variant)
    rng = t.rng
    g = G.Ground(t, F, 'dunes', seed=seed)
    g.noise(amp=1, scale=6.0)
    # the crater: a bowl 3 deep with a rim of thrown sand, lopsided down-range (east)
    for x in range(SX):
        for z in range(SZ):
            d = math.hypot((x - C) / 1.15, z - C)
            if d < 6.5:
                g.h[x][z] = int(round(2 * F - 6 * (1 - (d / 6.5) ** 2)))
            elif d < 8.5:
                g.h[x][z] = max(g.h[x][z], int(round(2 * F + 3 * (1 - abs(d - 7.3) / 1.2))))
    g.taper(slope=2)
    g.feather()
    g.apply(clear_above=4)
    glass = Noise(seed + 3, 1.6)
    for x in range(SX):
        for z in range(SZ):
            d = math.hypot((x - C) / 1.15, z - C)
            y = (g.h[x][z] + 1) // 2 - 1
            if g.h[x][z] % 2:
                continue
            if d < 3.0 and glass.at(x, 0, z) > 0.5:
                t.set(x, y, z, 'tinted_glass' if glass.at(x, 5, z) > 0.75 else 'glass')
            elif d < 5.5 and glass.at(x, 9, z) > 0.55 and G.free(t, x, y + 1, z):
                t.set(x, y + 1, z, 'or:scorch_mark')
    # the pod: nose down in the crater's floor, slanting west, retro nozzles up
    yaw = math.pi + 0.35
    ph = G.PosedHull(POD_KEYS, (C - 3, F + 3, C + 1), yaw=yaw, pitch=-0.9, roll=0.3)
    shell, inside, local = ph.voxels(t)
    skin = FGY.livery_skin(seed + 5, 'imperial' if imperial else 'republic', ground=g.feet)
    FGY.build_hull(t, ph, shell, inside, local, skin, sand=g.feet, seed=seed + 5, cut_soot=False)
    # retro nozzles: three short pipes out of the tail cap, sooty
    for (u, v) in ((0.9, 0.9), (-0.9, 0.9), (0.0, -1.1)):
        for k in (1, 2):
            c = ph.to_world(-k * 0.7, v, u)
            if t.inside(*c):
                t.set(*c, 'or:wreck_plating_scorched' if k == 2 else 'or:durasteel_vent')
    # the hatch: a door-sized hole in the pod's upper flank, its door thrown onto the sand
    hatch = []
    for c in shell:
        lx, ly, lz = local[c]
        u, v, w, hh = FGY.section_uv(ph.hull, lx, ly, lz)
        if 2.6 <= lx <= 5.4 and u > 1.2 and abs(v) < 1.3:
            t.set(*c, AIR)
            hatch.append(c)
    # a window on the other side
    for c in shell:
        lx, ly, lz = local[c]
        u, v, w, hh = FGY.section_uv(ph.hull, lx, ly, lz)
        if 3.5 <= lx <= 4.5 and u < -1.6 and abs(v) < 0.6 and G.solid_at(t, *c):
            t.set(*c, 'black_stained_glass')
    hx, hz = C + 3, C - 4
    hy = g.feet(hx, hz)
    for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        t.set(hx + dx, hy, hz + dz, G.trapdoor('iron_trapdoor', 'south', 'bottom'))
    # inside: the seat, the locker (the chest), a console; light from the open hatch and a panel
    floor = sorted((c for c in inside if G.free(t, *c) and G.solid_at(t, c[0], c[1] - 1, c[2])), key=lambda c: (c[1], c[0], c[2]))
    if floor:
        c0 = floor[0]
        t.chest(c0[0], c0[1], c0[2], 'east', LOOT)
        CHEST[variant] = c0
        if len(floor) > 2:
            c1 = floor[-1]
            t.set(*c1, S('or:console', facing='west'))
    for c in shell:
        lx, ly, lz = local[c]
        u, v, w, hh = FGY.section_uv(ph.hull, lx, ly, lz)
        if 4.6 <= lx <= 5.4 and v > 1.6 and abs(u) < 0.6:
            t.set(*c, 'or:light_panel_amber' if not imperial else 'or:light_panel_red')
            break
    # the skid of its landing: a furrow of scorch and debris toward the east rim
    for k in range(3, 9):
        x, z = C + k, C + (k // 3)
        if g.inside(x, z) and rng.random() < 0.7:
            y = g.feet(x, z)
            if G.free(t, x, y, z):
                t.set(x, y, z, G.scatter_pick(rng, [('or:scorch_mark', 3), ('or:debris', 2), (G.slab('or:durasteel_plating_slab'), 1)]))
    if not imperial:
        # the survivor's camp in the pod's shade, the drogue chute spread on the rim, tracks walking off
        cx, cz = C + 2, C + 3
        G.bedroll(t, cx, g.feet(cx, cz), cz, 'x', 'white')
        t.set(cx + 2, g.feet(cx + 2, cz + 1), cz + 1, G.campfire(False))
        t.set(cx - 1, g.feet(cx - 1, cz + 1), cz + 1, S('cauldron'))
        chute = [(x, z) for x in range(2, 8) for z in range(14, 18) if math.hypot(x - 5, (z - 16) * 1.4) < 3.2]
        for (x, z) in chute:
            y = g.feet(x, z)
            if G.free(t, x, y, z):
                t.set(x, y, z, S('white_carpet') if (x + z) % 3 else S('orange_carpet'))
        for k in range(0, 4):
            x, z = 11 + k, 15 + k // 2
            if g.inside(x, z):
                y = g.feet(x, z)
                if G.free(t, x, y, z) and k % 2 == 0:
                    t.set(x, y, z, S('or:scorch_mark'))
    else:
        # its rider, sitting against the pod by the hatch: bones and his helmet
        bx, bz = C + 2, C - 2
        by = g.feet(bx, bz)
        t.set(bx, by, bz, S('bone_block', axis='y'))
        t.set(bx, by + 1, bz, S('skeleton_skull', rotation=4))
        t.set(bx + 1, by, bz, S('bone_block', axis='x'))
        t.set(bx - 1, by, bz + 1, 'or:cargo_crate_imperial')
        t.set(bx + 1, by, bz + 2, G.slab('or:imperial_plating_slab'))
    g.scatter(0.02)
    # the bowl's air up to above the rim, so that a dune flank higher than the crater's centre (where the
    # structure takes its height) does not hang over it
    for x in range(SX):
        for z in range(SZ):
            if math.hypot((x - C) / 1.15, z - C) < 8.5:
                for y in range(g.feet(x, z), F + 7):
                    if t.get(x, y, z) is None:
                        t.set(x, y, z, AIR)
    return t, [((0, 0, 0, SX - 1, SY - 1, SZ - 1), set(inside))]


CHEST = {}


def check_walk(t, variant):
    """The crater floor by the pod and the chest's front cell (inside the pod, entered through the hatch)."""
    pts = []
    for (label, (x, z)) in (('crater floor', (C + 2, C)), ('crater east', (C + 4, C + 1))):
        for yy in range(1, SY - 2):
            if G._stand(t, x, yy, z) is not None:
                pts.append((label, (x, yy, z)))
                break
    c = CHEST.get(variant)
    if c:
        pts.append(('chest front', (c[0] + 1, c[1], c[2])))
    return G.report_walk(t, NAME + '/' + variant, [], extra=pts)


VARIANTS = ('republic', 'imperial')


def worldgen():
    adapted = dict(yard_stand=F, clear_from=SY, clear_inset=0, support_depth=6, ramp_rings=2)
    W.pool(NAME + '/start', [W.element(NAME + '/' + v, adapted=adapted) for v in VARIANTS])
    W.structure(NAME, start_pool=NAME + '/start', biome_tag='has_structure/' + NAME, size=1, max_distance=32,
                start_height=1 - BURIED)
    W.structure_set(NAME, [(NAME, 1)], spacing=32, separation=10, salt=960305, placement='clear_of_origin',
                    min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:ghessar_dunes', 'oldrepublic:ghessar_saltflat'])
    G.write_loot(LOOT, (2, 3), [
        ('or:medpac', 4, 1, 2), ('bread', 5, 1, 3), ('torch', 4, 2, 4), ('string', 3, 1, 3),
        ('or:hyperfuel_cell', 1, 1, 1), ('or:credit_chit', 4, 1, 2), ('iron_ingot', 3, 1, 2)])


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
