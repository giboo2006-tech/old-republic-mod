"""ghessar/freighter_graveyard (worldgen, Ghessar's dunes): where ships go to be taken apart.

Ships fell here for a century (the dune sea is under the old hyperspace approach) and the ones that did not
fall were flown in to be broken. Hull sections lie half sunk in the dunes at every angle; salvagers have a
breaking yard in the middle of them with a gantry over the biggest section, a sorting yard of plate stacks,
a smelter and the yard boss's container shack (the yard's chest). Around the yard, one piece on each of three
sides: a freighter's bow driven nose-down into a dune, an engine section with its nozzles in the air and a
scaffold where the cutters work, a salvage crane over a pit, a tail fin standing out of the sand like a sail
with the cutters' camp at its foot, a hull stripped to its ribs, a shuttle lying on its back.

Pieces (jigsaw, rigid, all at the same feet layer): a start (two variants: yard_a, the breaking yard round a
midship section under the gantry; yard_b, the keel yard, the workshop built inside a hull's stripped bottom),
37 x 37, with jigsaws on three sides; each side draws from its own pool of two (north: bow, engines; east:
crane, fin; south: ribs, shuttle), 37 x 31. The fourth side meets the dunes. 31 + 37 + 31 = 99 blocks across.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gkit as G  # noqa: E402
from gkit import S, AIR, B, mod, W, Noise, Template  # noqa: E402
from structlib import jigsaw  # noqa: E402

NAME = 'ghessar/freighter_graveyard'
SY = 28
BURIED = 4
F = BURIED
SEED = 960310
LOOT = 'chests/ghessar/freighter_graveyard'
YARD_J = 'oldrepublic:freighter_graveyard/yard'
SIDE_J = 'oldrepublic:freighter_graveyard/side'
POOLS = {'north': NAME + '/north', 'east': NAME + '/east', 'south': NAME + '/south'}
SIDES = {'north': ('bow', 'engines'), 'east': ('crane', 'fin'), 'south': ('ribs', 'shuttle')}


# ---------------------------------------------------------------- shared pieces

def new_piece(name, sx, sz, seed):
    t = Template(sx, SY, sz, seed=seed, name=NAME + '/' + name)
    g = G.Ground(t, F, 'dunes', seed=seed)
    g.noise(amp=3, scale=11.0)
    return t, g


def finish_ground(g, junctions, natural):
    g.seam(junctions, width=6)
    g.taper(slope=2, sides=natural)
    g.feather(sides=natural)
    g.apply(clear_above=4)


def jig(t, x, z, facing, start):
    """The jigsaw on an edge at the feet layer: the yard's offer of a side, or a side's answer to the yard."""
    if start:
        t.jigsaw(x, F, z, facing + '_up', name=YARD_J, target=SIDE_J, pool=POOLS[start], final_state='minecraft:air')
    else:
        t.jigsaw(x, F, z, facing + '_up', name=SIDE_J, target=YARD_J, pool='minecraft:empty', final_state='minecraft:air')
    t.set(x, F + 1, z, AIR)
    t.set(x, F + 2, z, AIR)


def footprint(shell, inside, y):
    return {(x, z) for (x, yy, z) in (shell | inside) if yy == y}


# Ship liveries: the paint a hull still shows. main: the plating; seam: the panel rings every five blocks;
# stripe: the band along the flanks; scour: the sand-blasted top.
LIVERY = {
    'republic': dict(main=[('or:republic_plating', 9), ('or:republic_panel', 1)], seam='or:durasteel_plating',
                     stripe='red_terracotta', scour=[('or:republic_plating', 4), ('or:wreck_plating', 1)]),
    'imperial': dict(main=[('or:imperial_plating', 9), ('or:imperial_panel', 1)], seam='or:wreck_plating',
                     stripe='red_terracotta', scour=[('or:imperial_plating', 3), ('or:wreck_plating', 1)]),
    'courier': dict(main=[('or:durasteel_plating', 6), ('or:wreck_plating', 1)], seam='or:wreck_plating_scorched',
                    stripe='blue_terracotta', scour=[('or:durasteel_plating', 3), ('or:wreck_plating', 1)]),
    'hauler': dict(main=[('or:durasteel_plating', 5), ('or:wreck_plating', 2)], seam='or:wreck_plating_scorched',
                   stripe='yellow_terracotta', scour=[('or:durasteel_plating', 3), ('or:wreck_plating', 1)]),
    'barge': dict(main=[('waxed_weathered_cut_copper', 5), ('waxed_exposed_cut_copper', 1)], seam='or:rusted_plating',
                  stripe='or:durasteel_plating', scour=[('waxed_weathered_cut_copper', 2), ('waxed_exposed_cut_copper', 2)]),
}


def livery_skin(seed, livery, windows=False, ground=None, stripe=True):
    """A plating picker by a hull cell's section coordinates (lx along the hull, u across, v up): panel rings,
    the livery stripe along the flanks, a row of ports, rust running down the sides, the top scoured by sand,
    rust where the hull sits in the sand (ground(x, z): the feet layer there)."""
    L = LIVERY[livery]
    rust = Noise(seed + 1, 2.5)
    patch = Noise(seed + 2, 4.0)
    main = G.mix(seed + 3, L['main'], 3.0)
    scour = G.mix(seed + 4, L['scour'], 2.5)

    def pick(x, y, z, lx, u, v, w, h):
        au = abs(u)
        if abs(lx - round(lx / 5.0) * 5) < 0.5:
            return L['seam']
        if v > 0.72 * h:
            return scour(x, y, z)
        if windows and au > 0.55 * w and abs(v - 0.4 * h) < 0.5 and int(math.floor(lx)) % 3 == 1:
            return 'black_stained_glass'
        if stripe and au > 0.55 * w and abs(v) < 0.12 * h:
            return L['stripe']
        if v < 0.15 * h and rust.at(lx * 2.4, v * 0.3, u * 0.6) > 0.68:
            return 'or:rusted_plating'
        if ground and y <= ground(x, z) and patch.at(x, y, z) > 0.45:
            return 'or:rusted_plating'
        return main(x, y, z)
    return pick


def section_uv(h, lx, ly, lz):
    """A cell's place in its hull section: (u across, v up, half width, half height), roll applied."""
    yc, zc, w, hh, n = h.at(lx)
    dz, dy = lz - zc, ly - yc
    return dz * h.cos + dy * h.sin, -dz * h.sin + dy * h.cos, w, hh


def build_hull(t, ph, shell, inside, local, skin, sand=None, cut_soot=True, holes=0.0, seed=0, keep_inside=None,
               decks=()):
    """Writes a posed hull: its skin, air inside, sand inside below sand(x, z) (default: the feet layer: the dunes
    got in), decks at the given section heights v, soot and tears along the open ends."""
    h = ph.hull
    tear = Noise(seed + 9, 1.8)
    for c in shell:
        x, y, z = c
        lx, ly, lz = local[c]
        u, v, w, hh = section_uv(h, lx, ly, lz)
        near_cut = (lx < h.x0 + 1.6 and 'x0' in ph.open_ends) or (lx > h.x1 - 1.6 and 'x1' in ph.open_ends)
        if near_cut and tear.at(x, y, z) > 0.62:
            continue                                          # torn away at the cut
        if holes and tear.at(x * 1.3, y, z * 1.3) > 1.0 - holes and v > 0:
            if abs(lx - round(lx / 3.0) * 3) < 0.5:
                t.set(x, y, z, 'or:durasteel_beam[axis=y]')    # the frame the plates were cut from
            continue
        s = skin(x, y, z, lx, u, v, w, hh)
        if cut_soot and near_cut and tear.at(x, y + 5, z) > 0.4:
            s = 'or:wreck_plating_scorched'
        t.set(x, y, z, s)
    for c in inside:
        x, y, z = c
        if keep_inside and c in keep_inside:
            continue
        lvl = sand(x, z) if sand else F
        if y < lvl:
            t.set(x, y, z, 'sand')
            continue
        if y == lvl and t.get(x, y - 1, z) is None:
            t.set(x, y - 1, z, 'sand')
        lx, ly, lz = local[c]
        u, v, w, hh = section_uv(h, lx, ly, lz)
        if decks and any(abs(v - d) < 0.5 for d in decks) and abs(u) < w - 0.9:
            t.set(x, y, z, 'or:durasteel_grate' if tear.at(x, y + 9, z) > 0.68 else 'or:durasteel_deck')
        else:
            t.set(x, y, z, AIR)


def bells(t, ph, nozzles, x_from, length, flare=0.22, wall=1.0, box=None, outer='or:durasteel_deck'):
    """Engine bells on a hull's aft face (its x1 end, at own-frame x_from): for each (u, v, r) a cone of plating
    flaring out over `length`, a scorched lip, a dark throat. Returns the cells written."""
    h = ph.hull
    x0, y0, z0, x1, y1, z1 = box or (0, 0, 0, t.sx - 1, t.sy - 1, t.sz - 1)
    out = set()
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                lx, ly, lz = ph.to_local(x, y, z)
                if not (x_from - 0.5 <= lx <= x_from + length):
                    continue
                u, v, w, hh = section_uv(h, lx, ly, lz)
                for (u0, v0, r) in nozzles:
                    R = r + flare * max(0.0, lx - x_from)
                    d = math.hypot(u - u0, v - v0)
                    if R - wall < d <= R:
                        lip = lx > x_from + length - 1.0
                        t.set(x, y, z, 'or:rusted_plating' if lip else outer if d > R - 0.5 else 'or:wreck_plating_scorched')
                        out.add((x, y, z))
                        break
                    if d <= R - wall:
                        if lx < x_from + 0.6:
                            t.set(x, y, z, 'black_concrete' if d < R - wall - 0.6 else 'or:wreck_plating_scorched')
                        else:
                            t.set(x, y, z, AIR)
                        out.add((x, y, z))
                        break
    return out


def gantry(t, g, xa, xb, za, zb, top, trolley=None, load=None):
    """A breaking gantry: two portal frames (legs at the four corners) and beams along the top."""
    for (x, z) in ((xa, za), (xa, zb), (xb, za), (xb, zb)):
        y0 = g.feet(x, z)
        t.set(x, y0 - 1, z, 'or:durasteel_plating')
        for y in range(y0, top):
            t.set(x, y, z, 'or:durasteel_beam[axis=y]')
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if G.free(t, x + dx, y0, z + dz):
                t.set(x + dx, y0, z + dz, G.stair('or:durasteel_plating_stairs',
                                                  {(1, 0): 'west', (-1, 0): 'east', (0, 1): 'north', (0, -1): 'south'}[(dx, dz)]))
    for x in range(xa, xb + 1):
        t.set(x, top, za, 'or:durasteel_beam[axis=x]')
        t.set(x, top, zb, 'or:durasteel_beam[axis=x]')
    for z in range(za + 1, zb):
        t.set(xa, top, z, 'or:durasteel_beam[axis=z]')
        t.set(xb, top, z, 'or:durasteel_beam[axis=z]')
    # knee braces
    for (x, z) in ((xa, za), (xa, zb), (xb, za), (xb, zb)):
        dz = 1 if z == za else -1
        t.set(x, top - 1, z + dz, G.stair('or:durasteel_plating_stairs', 'north' if dz > 0 else 'south', 'top'))
    if trolley:
        tx = trolley
        for z in range(za + 1, zb):
            t.set(tx, top, z, 'or:durasteel_beam[axis=z]')
        tz = (za + zb) // 2
        t.set(tx, top - 1, tz, 'or:durasteel_vent')
        if load:
            ly, lw = load
            for y in range(ly + 1, top - 1):
                t.set(tx, y, tz, G.chain('y'))
            # a hull plate hanging from the hook, tilted
            for dx in range(-lw, lw + 1):
                for dz in (-1, 0, 1):
                    yy = ly - (1 if dx > 0 else 0)
                    t.set(tx + dx, yy, tz + dz, 'or:rusted_plating' if (dx + dz) % 3 == 0 else 'or:durasteel_plating')
            t.set(tx, ly + 1, tz, G.rod('down'))


def lamp(t, g, x, z, h=3, kind='lantern'):
    y = g.feet(x, z)
    G.lamp_post(t, x, y, z, height=h, post='spruce_fence', kind=kind)


def tracks(t, g, pts, width=2, seed=0):
    """Skiff tracks and footpaths: darker grit pressed into the sand."""
    grit = G.mix(seed, [('sand', 4), ('coarse_dirt', 2), ('gravel', 1)], 2.0)
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        n = max(abs(bx - ax), abs(bz - az), 1)
        for k in range(n + 1):
            x = int(round(ax + (bx - ax) * k / float(n)))
            z = int(round(az + (bz - az) * k / float(n)))
            for d in range(width):
                xx, zz = (x + d, z) if abs(bz - az) > abs(bx - ax) else (x, z + d)
                if not g.inside(xx, zz):
                    continue
                y = g.feet(xx, zz) - 1
                if g.h[xx][zz] % 2 == 0 and t.get(xx, y, zz) is not None and B.name_of(t.get(xx, y, zz)) in ('minecraft:sand', 'minecraft:sandstone'):
                    t.set(xx, y, zz, grit(xx, y, zz))


def debris_field(t, g, region, density, seed, avoid=None):
    rng = t.rng
    x0, z0, x1, z1 = region
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if avoid and (x, z) in avoid:
                continue
            if rng.random() >= density:
                continue
            y = g.feet(x, z)
            if not (G.free(t, x, y, z) and G.free(t, x, y + 1, z)) or g.h[x][z] % 2:
                continue
            r = rng.random()
            if not G.solid_at(t, x, y - 1, z):
                continue                    # the ground was cut away under it (a hull's floor below)
            if r < 0.45:
                t.set(x, y, z, 'or:debris')
            elif r < 0.65:
                t.set(x, y, z, G.slab('or:durasteel_plating_slab'))
            elif r < 0.8:
                t.set(x, y, z, G.stair('or:durasteel_plating_stairs', rng.choice(B.DIRS)))
            elif r < 0.9:
                t.set(x, y - 1, z, G.scatter_pick(rng, [('or:rusted_plating', 2), ('or:wreck_plating_scorched', 1)]))
            else:
                t.set(x, y, z, 'or:scorch_mark')


# ---------------------------------------------------------------- the yards (start pieces)

MID_KEYS = [(0, 0, 0, 7.0, 6.2, 2.6), (19, 0, 0, 7.0, 6.2, 2.6)]


def yard_a():
    """The breaking yard: a midship section under the gantry, the sorting yard, the smelter, the boss's shack."""
    t, g = new_piece('yard_a', 37, 37, SEED + 10)
    yaw = 0.42
    ph = G.PosedHull(MID_KEYS, (5, F + 2, 6), yaw=yaw, roll=0.08, open_ends=('x0', 'x1'))
    shell, inside, local = ph.voxels(t)
    foot = footprint(shell, inside, F)
    north = {(x, z) for (x, z) in foot if (x, z - 1) not in foot}
    g.drift(north, 'north', 7, 6, seed=1)
    g.drift({(x, z) for (x, z) in foot if (x - 1, z) not in foot}, 'west', 5, 4, seed=2)
    g.mound(30, 6, 6, 5, 4)
    finish_ground(g, ('north', 'east', 'south'), ('west',))
    build_hull(t, ph, shell, inside, local, livery_skin(SEED + 11, 'hauler', ground=g.feet), seed=SEED + 11)
    # its upper deck, half fallen in; a cargo crane's rail inside
    fallen = Noise(SEED + 18, 2.5)
    for c in inside:
        x, y, z = c
        if y == F + 4 and fallen.at(x, 0, z) < 0.7 and G.free(t, x, y, z):
            lx = local[c][0]
            if 2 < lx < 17:
                t.set(x, y, z, 'or:durasteel_grate' if fallen.at(x, 7, z) > 0.62 else 'or:durasteel_deck')
    for lx in (4, 9, 14):
        for lz in (-4, 4):
            c = ph.to_world(lx, -1.5, lz)
            if c in inside:
                t.set(*c, 'or:cargo_crate' if lz < 0 else 'or:cargo_crate_republic')
    # the gantry straddling it, a plate on the hook
    gantry(t, g, 11, 21, 3, 25, F + 17, trolley=16, load=(F + 9, 2))
    # the sorting yard (south-east): stacks in rows, bins of small scrap, a heap
    rng = t.rng
    for x in range(22, 34, 2):
        for z in (24, 27, 30):
            if rng.random() < 0.8:
                G.plate_stack(t, x, g.feet(x, z), z, 1 + int(rng.random() * 3), rng)
    for (bx, bz) in ((29, 20), (33, 20)):
        by = g.feet(bx, bz)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx or dz:
                    t.set(bx + dx, by, bz + dz, S('iron_bars'))
        t.set(bx, by, bz, 'or:debris')
        t.set(bx, by - 1, bz, 'or:rusted_plating')
    G.scrap_heap(t, 30, g.feet(30, 13), 13, 3.2, 3.5, SEED + 12)
    G.smelter(t, 25, g.feet(25, 19), 19, facing='west')
    # the boss's container shack (south-west), the yard's chest inside; his condenser, the kajidic's cloth
    sy = g.feet(7, 28)
    box = G.container_shack(t, 3, sy, 25, length=8, width=5, along='x', door_side='north', seed=SEED + 13)
    t.chest(4, sy, 26, 'east', LOOT)
    t.set(4, sy, 28, 'or:cargo_crate')
    G.slab_table(t, 7, 27, 8, 27, sy, top='or:durasteel_plating_slab')
    t.set(7, sy + 1, 27, S('or:holo_screen', facing='north'))
    G.chair(t, 8, sy, 28, 'north', 'or:durasteel_plating_stairs')
    t.set(9, sy, 28, G.barrel('up'))
    G.bedroll(t, 5, sy, 28, 'x', 'brown')
    t.set(6, sy + 2, 27, G.lantern(True))
    G.condenser(t, 12, g.feet(12, 30), 30, height=6, owned=True)
    G.claim_pole(t, 2, g.feet(2, 20), 20, height=7, cloth_side='east', banner_side='west')
    # lamps, tracks and the scatter of a yard that never stops working
    for (x, z) in ((10, 22), (22, 22), (34, 33), (19, 33)):
        lamp(t, g, x, z, 3)
    tracks(t, g, [(0, 18), (12, 20), (22, 22), (34, 22), (36, 18)], 2, SEED + 14)
    tracks(t, g, [(18, 36), (18, 26), (12, 20)], 2, SEED + 15)
    tracks(t, g, [(18, 0), (18, 3)], 2, SEED + 16)
    debris_field(t, g, (0, 0, 36, 36), 0.03, SEED + 17)
    g.scatter(0.008)
    for (side, (x, z)) in (('north', (18, 0)), ('east', (36, 18)), ('south', (18, 36))):
        jig(t, x, z, side, side)
    G.report_walk(t, 'yard_a', [], extra=[('chest front', (5, sy, 26)), ('north jigsaw', (18, F, 1)),
                                          ('east jigsaw', (35, F, 18)), ('south jigsaw', (18, F, 35))])
    return t, [((0, 0, 0, 36, SY - 1, 36), set(inside)), box]


OFFICE = {}
KEEL_KEYS = [(0, 0, 0, 5.5, 5.0, 2.4), (4, 0, 0, 8.6, 7.0, 2.4), (22, 0, 0, 8.6, 7.0, 2.4), (27, 0, 0, 6.0, 5.6, 2.2)]


def yard_b():
    """The keel yard: a big hull's bottom half, its top cut away, the workshop built inside it under canvas;
    the foreman's office in a cut-off cockpit beside it."""
    t, g = new_piece('yard_b', 37, 37, SEED + 20)
    yaw = -0.22
    ph = G.PosedHull(KEEL_KEYS, (4, F + 1, 22), yaw=yaw, roll=0.0)
    shell, inside, local = ph.voxels(t)
    cut = F + 3
    shell = {c for c in shell if c[1] <= cut}
    inside = {c for c in inside if c[1] <= cut}
    foot = footprint(shell, inside, F)
    g.drift({(x, z) for (x, z) in foot if (x, z - 1) not in foot}, 'north', 6, 5, seed=3)
    g.mound(31, 5, 6, 5, 5)
    finish_ground(g, ('north', 'east', 'south'), ('west',))
    build_hull(t, ph, shell, inside, local, livery_skin(SEED + 21, 'imperial', ground=g.feet), seed=SEED + 21, cut_soot=False)
    # the cut rim: torch-cut edges, scorched; ribs standing up out of it where the plating came off
    tear = Noise(SEED + 22, 2.0)
    for c in shell:
        x, y, z = c
        if y == cut:
            t.set(x, y, z, 'or:wreck_plating_scorched' if tear.at(x, 0, z) > 0.45 else 'or:rusted_plating')
    for lx in range(5, 23, 4):
        for lz in (-8, 8):
            base = ph.to_world(lx, cut - (F + 1), lz)
            if base[1] <= cut:
                for k in range(1, 5 - (lx % 3)):
                    c = (base[0], cut + k, base[2])
                    if t.inside(*c):
                        t.set(*c, 'or:durasteel_beam[axis=y]')
    # inside: the workshop under canvas strung between the ribs, benches, an engine on blocks
    floor = {(x, z) for (x, y, z) in inside if y == F}
    for (x, z) in floor:
        t.set(x, F - 1, z, 'or:durasteel_deck' if (x + 2 * z) % 9 else 'or:durasteel_grate')
    roof_y = cut + 3
    torn = Noise(SEED + 27, 2.0)
    for (x, z) in floor:
        lx, _, lz = ph.to_local(x, F, z)
        if 8 <= lx <= 18 and torn.at(x, 0, z) < 0.74:
            t.set(x, roof_y, z, 'or:canvas_navy' if int(math.floor(lz + 0.5)) % 4 == 0 else G.slab('or:canvas_slab', 'top'))
    for lx in (8, 13, 18):
        for lz in (-6, 6):
            c = ph.to_world(lx, 0, lz)
            x, z = c[0], c[2]
            if (x, z) in floor:
                for y in range(F, roof_y):
                    t.set(x, y, z, 'or:durasteel_beam[axis=y]')
    rng = t.rng
    stuff = [('or:cargo_crate', 3), (G.barrel('up'), 2), ('anvil[facing=north]', 1), ('grindstone[face=floor,facing=east]', 1),
             ('or:console[facing=south]', 1), ('or:cargo_crate_imperial', 1)]
    for (x, z) in sorted(floor):
        lx, ly, lz = ph.to_local(x, F, z)
        if abs(lz) > 5.2 and rng.random() < 0.35 and G.free(t, x, F, z):
            t.set(x, F, z, G.scatter_pick(rng, stuff))
    # the engine on blocks in the middle, opened up
    ec = ph.to_world(13, 0, 0)
    ex, ez = ec[0], ec[2]
    for dx in (-2, -1, 0, 1, 2):
        for dz in (-1, 0, 1):
            t.set(ex + dx, F, ez + dz, 'or:durasteel_vent' if dx in (-2, 2) else 'or:durasteel_pipes[axis=x]' if dz == 0 else 'or:durasteel_plating')
        t.set(ex + dx, F + 1, ez, 'or:durasteel_pipes[axis=x]' if dx != 0 else 'or:light_panel_red')
    t.set(ex, F + 2, ez, G.slab('or:durasteel_plating_slab'))
    for (x, z) in ((ex - 3, ez), (ex + 3, ez)):
        if G.free(t, x, F, z):
            t.set(x, F, z, 'or:scorch_mark')
    for lx in (9, 17):
        c = ph.to_world(lx, 0, 0)
        t.set(c[0], roof_y, c[2], 'or:canvas_navy')        # a whole cloth over each lamp to hang it from
        t.set(c[0], roof_y - 1, c[2], G.lantern(True))
    # a ramp of plates down into it from the yard (east end)
    for (lx, k) in ((24, 0), (25, 0), (26, 0)):
        for lz in (-1, 0, 1):
            c = ph.to_world(lx, 0, lz)
            x, z = c[0], c[2]
            for y in range(F, cut + 2):
                t.set(x, y, z, AIR)
            t.set(x, F - 1, z, 'or:durasteel_deck')
    # the foreman's office: a cockpit cut off another ship, a door cut in its flank, the chest inside
    ck = [(0, 0, 0, 3.4, 3.2, 2.2), (6, 0, 0, 3.4, 3.2, 2.2), (9, -0.4, 0, 2.2, 2.2, 2.0), (10, -0.6, 0, 1.0, 1.2, 2.0)]
    pc = G.PosedHull(ck, (22, F + 1, 4), yaw=0.15, roll=0.05)
    s2, i2, l2 = pc.voxels(t)
    for c in s2:
        lx, ly, lz = l2[c]
        t.set(*c, 'tinted_glass' if lx > 6.5 and ly > 0.2 else 'or:republic_plating' if abs(ly) < 0.6 else 'or:durasteel_plating')
    for c in i2:
        t.set(*c, AIR if c[1] >= F else 'or:durasteel_deck')
    # the door: cut through its north flank from the middle of its floor, toward the yard's north track
    inner = sorted(c for c in i2 if c[1] == F)
    inner_set = set(inner)
    mid = min(inner, key=lambda c: (abs(c[0] - 25), -c[2]))
    for dd in (0, 1):
        x = mid[0] + dd
        for z in range(mid[2] - 1, mid[2] - 7, -1):
            hull = any((x, y, z) in s2 or (x, y, z) in i2 for y in range(F - 1, F + 3))
            for y in (F, F + 1, F + 2):
                if (x, y, z) in s2 or not hull:
                    t.set(x, y, z, AIR)
            if t.get(x, F - 1, z) is None or (x, F - 1, z) in s2 or G.free(t, x, F - 1, z):
                t.set(x, F - 1, z, 'or:durasteel_plating')
            if not hull:
                break
    office_chest = next(c for c in inner if (c[0] + 1, F, c[2]) in inner_set and (c[0] - 1, F, c[2]) not in inner_set)
    t.chest(office_chest[0], F, office_chest[2], 'east', LOOT)
    OFFICE['chest front'] = (office_chest[0] + 1, F, office_chest[2])
    t.set(inner[-1][0], F, inner[-1][2], S('or:console', facing='west'))
    lx_, lz_ = inner[len(inner) // 2][0], inner[len(inner) // 2][2]
    ltop = max(c[1] for c in i2 if c[0] == lx_ and c[2] == lz_)
    t.set(lx_, ltop, lz_, G.lantern(True))
    # the derrick: a lattice mast at the hull's north-east edge, a boom out over the keel, an engine block on the hook
    mx, mz = 27, 12
    my = g.feet(mx, mz)
    for y in range(my, F + 20):
        for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            t.set(mx + dx, y, mz + dz, 'or:durasteel_beam[axis=y]' if (y + dx + dz) % 4 else 'or:durasteel_plating')
    for k in range(0, 12):
        x, y, z = mx - k, F + 19 - (k // 3), mz + 1 + k // 2
        t.set(x, y, z, 'or:durasteel_beam[axis=x]')
    hx, hy, hz = mx - 11, F + 15, mz + 6
    for y in range(F + 6, hy):
        t.set(hx, y, hz, G.chain('y'))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            t.set(hx + dx, F + 5, hz + dz, 'or:durasteel_vent' if dx == 0 and dz == 0 else 'or:wreck_plating')
            t.set(hx + dx, F + 4, hz + dz, 'or:durasteel_pipes[axis=y]' if (dx + dz) % 2 else 'or:wreck_plating_scorched')
    t.set(mx + 2, my, mz, S('or:console', facing='west'))
    t.set(mx + 2, my, mz + 1, G.barrel('up'))
    # plate stacks, a condenser, lamps, tracks, scatter
    rng = t.rng
    for (x, z) in ((30, 22), (31, 22), (32, 23), (30, 25), (33, 26), (29, 29), (31, 30)):
        G.plate_stack(t, x, g.feet(x, z), z, 1 + int(rng.random() * 3), rng)
    G.condenser(t, 34, g.feet(34, 9), 9, height=6, owned=True)
    G.claim_pole(t, 2, g.feet(2, 12), 12, height=7, cloth_side='east', banner_side='west')
    for (x, z) in ((18, 2), (34, 17), (8, 33), (24, 33)):
        lamp(t, g, x, z, 3)
    tracks(t, g, [(0, 14), (18, 9), (34, 18), (36, 18)], 2, SEED + 23)
    tracks(t, g, [(18, 0), (18, 9)], 2, SEED + 24)
    tracks(t, g, [(18, 36), (24, 30), (34, 18)], 2, SEED + 25)
    debris_field(t, g, (0, 0, 36, 36), 0.03, SEED + 26)
    g.scatter(0.008)
    for (side, (x, z)) in (('north', (18, 0)), ('east', (36, 18)), ('south', (18, 36))):
        jig(t, x, z, side, side)
    G.report_walk(t, 'yard_b', [], extra=[('chest front', OFFICE['chest front']), ('north jigsaw', (18, F, 1)),
                                          ('east jigsaw', (35, F, 18)), ('south jigsaw', (18, F, 35))])
    return t, [((0, 0, 0, 36, SY - 1, 36), set(i2))]


# ---------------------------------------------------------------- side pieces (jigsaw on the north edge)

SIDE_D = 31


def side_piece(name, seed):
    return new_piece(name, 37, SIDE_D, seed)


def side_finish(t, g):
    jig(t, 18, 0, 'north', None)


def ladder_up(t, x, z, y0, y1, facing, backing='or:durasteel_beam[axis=y]'):
    """A ladder from y0 to y1 (inclusive) at (x, z) facing `facing`, with something to hang on behind it."""
    dx, dz = B.step(B.opposite(facing))
    for y in range(y0, y1 + 1):
        if G.free(t, x + dx, y, z + dz) or not G.solid_at(t, x + dx, y, z + dz):
            t.set(x + dx, y, z + dz, backing)
        t.set(x, y, z, G.ladder(facing))


def scaffold(t, g, x, z, top, size=3, ladder_side='north', mid=True):
    """A cutters' scaffold: a tower of beams `size` square standing in the sand up to `top` (its deck layer),
    a grate platform at the top (and one halfway), a ladder up the middle of one side."""
    y0 = min(g.feet(x + dx, z + dz) for dx in range(size) for dz in range(size))
    for y in range(y0 - 1, top):
        for (dx, dz) in ((0, 0), (size - 1, 0), (0, size - 1), (size - 1, size - 1)):
            t.set(x + dx, y, z + dz, 'or:durasteel_beam[axis=y]')
    levels = [top] + ([(y0 + top) // 2] if mid and top - y0 > 6 else [])
    for py in levels:
        for dx in range(size):
            for dz in range(size):
                t.set(x + dx, py, z + dz, 'or:durasteel_grate')
    m = size // 2
    if ladder_side == 'north':
        lx, lz, inner = x + m, z - 1, (x + m, z)
    elif ladder_side == 'south':
        lx, lz, inner = x + m, z + size, (x + m, z + size - 1)
    elif ladder_side == 'west':
        lx, lz, inner = x - 1, z + m, (x, z + m)
    else:
        lx, lz, inner = x + size, z + m, (x + size - 1, z + m)
    ly0 = g.feet(lx, lz)
    ladder_up(t, lx, lz, ly0, top, ladder_side)
    # the step off the ladder's head onto the top platform (the mid platform keeps the cell: it backs the ladder)
    t.set(inner[0], top + 1, inner[1], AIR)
    return y0


BOW_KEYS = [(0, 0, 0, 5.0, 4.2, 2.6), (11, 0, 0, 5.0, 4.2, 2.6), (17, -0.3, 0, 4.5, 3.7, 2.4),
            (22, -0.9, 0, 3.4, 2.8, 2.2), (25, -1.5, 0, 2.1, 1.7, 2.0), (27, -1.9, 0, 0.9, 0.8, 2.0)]
BOW_YAW = 2.27


def bow():
    """A Republic freighter's bow, torn off its ship and driven nose-first into a dune: the white hull rises
    out of the sand at a slant with its stripe and its row of ports, the torn end open over the sand with the
    deck showing, a salvagers' scaffold up to it."""
    t, g = side_piece('bow', SEED + 30)
    ph = G.PosedHull(BOW_KEYS, (25, F + 6, 6), yaw=BOW_YAW, pitch=-0.3, roll=0.12, open_ends=('x0',))
    shell, inside, local = ph.voxels(t)
    nose = ph.to_world(26, 0, 0)
    g.mound(nose[0], nose[2], 8, 8, 13)
    mid = ph.to_world(18, 0, 0)
    g.mound(mid[0], mid[2], 7, 7, 8)
    finish_ground(g, ('north',), ('east', 'west', 'south'))
    build_hull(t, ph, shell, inside, local, livery_skin(SEED + 31, 'republic', windows=True, ground=g.feet),
               sand=g.feet, seed=SEED + 31, decks=(-1.4,))
    h = ph.hull
    # the canopy over the nose, framed
    for c in shell:
        lx, ly, lz = local[c]
        u, v, w, hh = section_uv(h, lx, ly, lz)
        if lx > 21.5 and v > 0.2 * hh and G.solid_at(t, *c) and c[1] >= g.feet(c[0], c[2]) - 1:
            t.set(*c, 'or:durasteel_plating' if int(math.floor(lx)) % 3 == 0 else 'black_stained_glass')
    # emergency panels still glowing inside, consoles, the crew's things
    for (lx, lz) in ((3.5, -4.3), (8.5, 4.3)):
        c = ph.to_world(lx, 0.3, lz)
        if c in shell:
            t.set(*c, 'or:light_panel_amber')
    for (lx, lz, s) in ((6, -3, S('or:console', facing='west')), (6, 3, G.barrel('up')), (10, 2, 'or:cargo_crate_republic'),
                        (10, -2, S('skeleton_skull', rotation=6))):
        c = ph.to_world(lx, -0.9, lz)
        if c in inside and G.free(t, *c) and G.solid_at(t, c[0], c[1] - 1, c[2]):
            t.set(*c, s)
    # the salvagers' scaffold up to the torn end, a grate bridge onto the deck
    deck = None
    for lz in (0, 1, -1, 2, -2):
        c = ph.to_world(1.0, -1.4, lz)
        if c in inside and B.name_of(t.get(*c) or '').endswith(('durasteel_deck', 'durasteel_grate')):
            deck = c
            break
    if deck:
        dx, dz = -math.cos(BOW_YAW), -math.sin(BOW_YAW)
        sx_, sz_ = int(round(deck[0] + dx * 4)) - 1, int(round(deck[2] + dz * 4)) - 1
        scaffold(t, g, sx_, sz_, deck[1], size=3, ladder_side='east')
        BOW_SPOTS['foot'] = (sx_ + 4, g.feet(sx_ + 4, sz_ + 1), sz_ + 1)
        n = max(abs(deck[0] - (sx_ + 1)), abs(deck[2] - (sz_ + 1)))
        for k in range(n + 1):
            x = int(round(sx_ + 1 + (deck[0] - sx_ - 1) * k / float(max(n, 1))))
            z = int(round(sz_ + 1 + (deck[2] - sz_ - 1) * k / float(max(n, 1))))
            t.set(x, deck[1], z, 'or:durasteel_grate')
            for y in (deck[1] + 1, deck[1] + 2):
                if not G.free(t, x, y, z) and (x, y, z) in shell:
                    t.set(x, y, z, AIR)
        BOW_SPOTS['deck'] = (deck[0], deck[1] + 1, deck[2])
    # plates already cut from its flank, stacked; tracks from the yard
    for (x, z, hgt) in ((14, 9, 2), (15, 10, 1), (13, 11, 3)):
        G.plate_stack(t, x, g.feet(x, z), z, hgt)
    lamp(t, g, 17, 7, 3)
    debris_field(t, g, (0, 3, 36, SIDE_D - 1), 0.03, SEED + 33)
    tracks(t, g, [(18, 0), (19, 4), (27, 3)], 2, SEED + 34)
    g.scatter(0.012)
    side_finish(t, g)
    G.report_walk(t, 'bow', [], extra=[('jigsaw', (18, F, 1)), ('scaffold foot', BOW_SPOTS['foot'])])
    return t, [((0, 0, 0, 36, SY - 1, SIDE_D - 1), {(x, y, z) for (x, y, z) in inside})]


BOW_SPOTS = {}

ENGINE_KEYS = [(0, 0, 0, 5.4, 4.8, 2.8), (5, 0, 0, 5.8, 5.1, 2.8), (12, 0, 0, 5.8, 5.1, 2.8)]
ENGINE_NOZZLES = [(-3.0, 0.4, 2.6), (3.0, 0.4, 2.6)]


def engines():
    """An Imperial freighter's engine section, cut from its ship and dropped tail-up: the dark drum lies at a
    slant with its three bells in the air, the cutters' scaffold against its flank."""
    t, g = side_piece('engines', SEED + 40)
    ph = G.PosedHull(ENGINE_KEYS, (13, F + 1, 25), yaw=-1.27, pitch=0.42, roll=0.1, open_ends=('x0',))
    shell, inside, local = ph.voxels(t)
    cut = ph.to_world(-1, 0, 0)
    g.mound(cut[0], cut[2], 8, 7, 10)
    foot = footprint(shell, inside, F)
    g.drift({(x, z) for (x, z) in foot if (x, z - 1) not in foot}, 'north', 5, 4, seed=5)
    finish_ground(g, ('north',), ('east', 'west', 'south'))
    build_hull(t, ph, shell, inside, local, livery_skin(SEED + 41, 'courier', ground=g.feet), sand=g.feet,
               seed=SEED + 41, holes=0.3)
    bells(t, ph, ENGINE_NOZZLES, 12.0, 4.6, flare=0.1)
    # the cutters' scaffold against its east flank, scorch where they are cutting, a plate already off
    side = max((ph.to_world(6, -1.0, 6.8), ph.to_world(6, -1.0, -6.8)), key=lambda c: c[0])
    sx_, sz_ = side[0] + 1, side[2] - 1
    top = g.feet(sx_ + 1, sz_ + 1) + 7
    scaffold(t, g, sx_, sz_, top, size=3, ladder_side='east')
    for c in shell:
        if abs(c[0] - sx_) <= 2 and abs(c[2] - (sz_ + 1)) <= 2 and abs(c[1] - top) <= 3 and G.solid_at(t, *c):
            t.set(*c, 'or:wreck_plating_scorched')
    t.set(sx_ + 1, top + 1, sz_ + 1, S('or:console', facing='west'))
    y = g.feet(sx_ + 4, sz_ + 5)
    for k in range(3):
        t.set(sx_ + 4, y + k, sz_ + 5, 'or:imperial_plating')
    t.set(sx_ + 4, y + 3, sz_ + 5, G.stair('or:imperial_plating_stairs', 'east', 'top'))
    for (x, z) in ((sx_ + 5, sz_ + 3), (sx_ + 5, sz_ + 2)):
        t.set(x, g.feet(x, z), z, G.barrel('east'))
    lamp(t, g, sx_ + 5, sz_ - 2, 3)
    debris_field(t, g, (0, 3, 36, SIDE_D - 1), 0.035, SEED + 42)
    tracks(t, g, [(18, 0), (24, 6), (sx_ + 4, sz_ + 1)], 2, SEED + 43)
    g.scatter(0.012)
    side_finish(t, g)
    return t, []


def crane():
    t, g = side_piece('crane', SEED + 50)
    # a pit where a hull plate is being dragged out of the sand
    g.trough([(10, 14), (22, 16)], width=6, depth2=5, fall=3)
    finish_ground(g, ('north',), ('east', 'west', 'south'))
    # the half-dug plate: a slab of hull rising out of the pit's sand at an angle
    for i in range(0, 10):
        for j in range(0, 4):
            x, z = 11 + i, 13 + j
            y = g.feet(x, z) - 1 + (i // 3)
            t.set(x, y, z, 'or:rusted_plating' if (i + j) % 3 == 0 else 'or:wreck_plating')
            if i % 3 == 2:
                t.set(x, y + 1, z, G.stair('or:durasteel_plating_stairs', 'west'))
    # the crane: a lattice mast with a boom out over the pit, its collars painted hauler ochre
    mx, mz = 25, 8
    my = g.feet(mx, mz)
    top = F + 22
    for y in range(my - 1, top):
        for (dx, dz) in ((0, 0), (2, 0), (0, 2), (2, 2)):
            t.set(mx + dx, y, mz + dz, 'or:durasteel_beam[axis=y]')
        if y % 4 == 0:
            t.set(mx + 1, y, mz, 'yellow_terracotta')
            t.set(mx + 1, y, mz + 2, 'or:durasteel_beam[axis=x]')
            t.set(mx, y, mz + 1, 'or:durasteel_beam[axis=z]')
            t.set(mx + 2, y, mz + 1, 'or:durasteel_beam[axis=z]')
    ladder_up(t, mx + 1, mz - 1, my, top - 1, 'north')
    for dx in range(-1, 4):
        for dz in range(-1, 4):
            t.set(mx + dx, top, mz + dz, 'or:durasteel_grate')
    t.set(mx + 1, top, mz - 1, G.trapdoor('iron_trapdoor', 'north', 'top', True))
    t.set(mx + 1, top + 1, mz + 3, S('or:console', facing='north'))
    t.set(mx + 1, top + 1, mz + 1, 'or:durasteel_vent')
    for (dx, dz) in ((-1, -1), (3, -1), (-1, 3), (3, 3)):
        t.set(mx + dx, top + 1, mz + dz, S('iron_bars'))
    # the boom: from the mast top out west and down toward the pit
    for k in range(0, 14):
        x = mx - 1 - k
        y = top - (k // 4)
        z = mz + 1 + (k // 3)
        t.set(x, y, z, 'or:durasteel_beam[axis=x]' if k % 5 else 'yellow_terracotta')
    hx, hz = mx - 14, mz + 5
    hy = top - 4
    for y in range(F + 4, hy):
        t.set(hx, y, hz, G.chain('y'))
    t.set(hx, F + 3, hz, G.rod('down'))
    # a stay cable back to an anchor block
    for k in range(1, 8):
        t.set(mx + 2 + k, top - k, mz + 1, G.chain('x') if k % 2 else G.chain('y'))
    t.set(mx + 10, g.feet(mx + 10, mz + 1), mz + 1, 'or:durasteel_plating')
    # the winch skiff and its drums
    G.skiff(t, 27, g.feet(29, 20), 18, length=8, width=5, along='x')
    for (x, z) in ((30, 19), (31, 19)):
        t.set(x, g.feet(29, 20) + 2, z, G.barrel('east'))
    t.set(28, g.feet(29, 20) + 2, 21, G.chain('x'))
    # a fuel bladder under canvas, a lamp, scatter
    for (x, z) in ((5, 6), (6, 6), (5, 7)):
        t.set(x, g.feet(x, z), z, 'or:canvas_navy')
    t.set(6, g.feet(6, 7), 7, G.slab('or:canvas_slab'))
    # plates already dragged out, stacked by the pit
    for (x, z, hgt) in ((8, 22, 2), (10, 23, 3), (12, 22, 1), (21, 24, 2)):
        G.plate_stack(t, x, g.feet(x, z), z, hgt)
    lamp(t, g, 22, 3, 3)
    lamp(t, g, 16, 22, 3)
    debris_field(t, g, (0, 3, 36, SIDE_D - 1), 0.03, SEED + 51)
    tracks(t, g, [(18, 0), (24, 6), (29, 17)], 2, SEED + 52)
    g.scatter(0.012)
    side_finish(t, g)
    return t, []


STERN_KEYS = [(0, 0, 0, 6.4, 5.4, 2.6), (14, 0, 0, 6.4, 5.4, 2.6), (20, 0.3, 0, 5.8, 4.9, 2.6)]
STERN_NOZZLES = [(-3.0, 0.6, 2.2), (3.0, 0.6, 2.2)]


def fin():
    """The stern of an old copper-hulled barge, the rest of it under the dune: its back rises out of the sand
    toward the yard with the two bells of its engines showing their rims, its tail fin standing up like a
    sail; the cutters camp in the fin's shade."""
    t, g = side_piece('fin', SEED + 60)
    ph = G.PosedHull(STERN_KEYS, (8, F - 5, 27), yaw=-1.48, pitch=0.2, roll=0.06)
    shell, inside, local = ph.voxels(t)
    bow_end = ph.to_world(2, 0, 0)
    g.mound(bow_end[0], bow_end[2], 8, 6, 10)
    foot = footprint(shell, inside, F)
    g.drift({(x, z) for (x, z) in foot if (x + 1, z) not in foot}, 'east', 5, 4, seed=8)
    finish_ground(g, ('north',), ('east', 'west', 'south'))
    h = ph.hull
    sand_all = lambda x, z: SY  # noqa: E731  the buried barge is full of sand
    build_hull(t, ph, shell, inside, local, livery_skin(SEED + 61, 'barge', ground=g.feet), sand=sand_all,
               seed=SEED + 61, cut_soot=False)
    bells(t, ph, STERN_NOZZLES, 20.0, 2.6, flare=0.18)
    # the fin: a swept plate on the back of the stern
    for x in range(t.sx):
        for y in range(F - 2, t.sy):
            for z in range(t.sz):
                lx, ly, lz = ph.to_local(x, y, z)
                if not (8.0 <= lx <= 20.5):
                    continue
                u, v, w, hh = section_uv(h, lx, ly, lz)
                if abs(u) > 0.62 or v < hh - 0.8:
                    continue
                k = min(1.0, (lx - 8.0) / 10.0)
                if v > hh + 11.0 * k:
                    continue
                edge = v > hh + 11.0 * k - 1.2 or lx > 19.6
                band = abs(v - (hh + 6.0)) < 0.6
                t.set(x, y, z, 'or:rusted_plating' if edge else 'or:durasteel_plating' if band else
                      'waxed_weathered_cut_copper' if (int(v) + int(lx)) % 5 else 'waxed_exposed_cut_copper')
    # the camp in its shade, on the east side: an awning off the hull, bedrolls, a fire, a condenser, a skiff
    cx0, cz0 = 19, 15
    cy = g.feet(cx0 + 3, cz0 + 2)
    for x in range(cx0, cx0 + 7):
        for z in range(cz0, cz0 + 5):
            t.set(x, cy + 3, z, G.slab('or:canvas_slab', 'top') if (z - cz0) % 2 == 0 else 'or:canvas')
    for (x, z) in ((cx0 + 6, cz0), (cx0 + 6, cz0 + 4)):
        for y in range(g.feet(x, z), cy + 3):
            t.set(x, y, z, S('spruce_fence'))
    G.bedroll(t, cx0 + 1, g.feet(cx0 + 1, cz0 + 1), cz0 + 1, 'x', 'brown')
    G.bedroll(t, cx0 + 1, g.feet(cx0 + 1, cz0 + 3), cz0 + 3, 'x', 'gray')
    t.set(cx0 + 4, g.feet(cx0 + 4, cz0 + 1), cz0 + 1, G.barrel('up'))
    t.set(cx0 + 4, g.feet(cx0 + 4, cz0 + 1) + 1, cz0 + 1, G.pot('south'))
    fx, fz = cx0 + 9, cz0 + 2
    t.set(fx, g.feet(fx, fz), fz, G.campfire(True))
    for (x, z) in ((fx - 1, fz), (fx + 1, fz), (fx, fz + 1)):
        if G.free(t, x, g.feet(x, z), z):
            t.set(x, g.feet(x, z), z, 'or:scorch_mark')
    G.condenser(t, cx0 + 8, g.feet(cx0 + 8, cz0 - 3), cz0 - 3, height=5)
    G.skiff(t, 25, g.feet(28, 24), 21, length=7, width=4, along='x')
    for (x, z, hgt) in ((cx0 + 2, cz0 + 7, 2), (cx0 + 3, cz0 + 7, 1), (cx0 - 1, cz0 + 9, 2)):
        G.plate_stack(t, x, g.feet(x, z), z, hgt)
    lamp(t, g, cx0 + 7, cz0 + 6, 2)
    debris_field(t, g, (0, 3, 36, SIDE_D - 1), 0.025, SEED + 62)
    tracks(t, g, [(18, 0), (20, 7), (cx0 + 6, cz0 + 2)], 2, SEED + 63)
    g.scatter(0.012)
    side_finish(t, g)
    return t, []


RIB_KEYS = [(0, 0, 0, 6.6, 5.8, 2.4), (18, 0, 0, 6.6, 5.8, 2.4)]


def ribs():
    t, g = side_piece('ribs', SEED + 70)
    ph = G.PosedHull(RIB_KEYS, (6, F + 1, 11), yaw=0.3, roll=0.25, open_ends=('x0', 'x1'))
    shell, inside, local = ph.voxels(t)
    foot = footprint(shell, inside, F)
    g.drift({(x, z) for (x, z) in foot if (x, z - 1) not in foot}, 'north', 5, 4, seed=6)
    g.mound(20, 21, 7, 4, 5)
    finish_ground(g, ('north',), ('east', 'west', 'south'))
    # only the ribs and the keel are left: shell cells near a rib station or at the bottom, beams
    plating = Noise(SEED + 71, 3.0)
    for c in shell:
        lx, ly, lz = local[c]
        rib = abs(lx - round(lx / 3.0) * 3) < 0.55
        keel = ly < -4.8 and abs(lz) < 1.5
        plate = ly < -3.0 or plating.at(*c) > 0.78
        if rib:
            t.set(*c, 'or:durasteel_beam[axis=y]' if abs(lz) > 3.5 else 'or:durasteel_beam[axis=z]')
        elif keel:
            t.set(*c, 'or:durasteel_beam[axis=x]')
        elif plate and c[1] <= F + 1:
            t.set(*c, 'or:rusted_plating' if (c[0] + c[1]) % 3 == 0 else 'or:wreck_plating')
    floor = Noise(SEED + 72, 3.0)
    for c in inside:
        if c[1] < F:
            t.set(*c, 'sand')
        elif c[1] == F and floor.at(*c) > 0.55:
            t.set(*c, G.slab('sandstone_slab'))
    # a spine beam along the top
    for lx in range(0, 19):
        c = ph.to_world(lx, 5.6, 0)
        if t.inside(*c):
            t.set(*c, 'or:durasteel_beam[axis=x]')
    debris_field(t, g, (0, 3, 36, SIDE_D - 1), 0.03, SEED + 73)
    G.scrap_heap(t, 28, g.feet(28, 9), 9, 2.5, 2.5, SEED + 74)
    G.scrap_heap(t, 9, g.feet(9, 25), 25, 2.2, 2.0, SEED + 76)
    tracks(t, g, [(18, 0), (15, 6)], 2, SEED + 75)
    g.scatter(0.012)
    side_finish(t, g)
    return t, []


SHUTTLE_KEYS = [(0, 0, 0, 3.2, 2.8, 2.2), (2, 0, 0, 3.8, 3.2, 2.2), (11, 0, 0, 3.8, 3.2, 2.2), (14, -0.3, 0, 2.8, 2.4, 2.0),
                (16, -0.5, 0, 1.3, 1.1, 2.0)]


def shuttle():
    """An Imperial shuttle on its side: one wing buried, the other standing up out of the sand like a black
    sail; its belly faces the dunes with a door cut in it, what was inside stacked outside."""
    t, g = side_piece('shuttle', SEED + 80)
    ph = G.PosedHull(SHUTTLE_KEYS, (8, F + 3, 18), yaw=-0.45, roll=math.pi / 2 - 0.12)
    shell, inside, local = ph.voxels(t)
    h = ph.hull
    foot = footprint(shell, inside, F)
    g.drift({(x, z) for (x, z) in foot if (x, z - 1) not in foot}, 'north', 6, 5, seed=7)
    finish_ground(g, ('north',), ('east', 'west', 'south'))
    build_hull(t, ph, shell, inside, local, livery_skin(SEED + 81, 'imperial', ground=g.feet), seed=SEED + 81,
               cut_soot=False)
    # the canopy on its nose, now looking along the sand
    for c in shell:
        lx, ly, lz = local[c]
        u, v, w, hh = section_uv(h, lx, ly, lz)
        if lx > 12.5 and v > 0.3 * hh and G.solid_at(t, *c):
            t.set(*c, 'black_stained_glass' if int(math.floor(lx)) % 3 else 'or:imperial_plating')
    # the wings: plates out of its flanks; one stands up, the other is under the shuttle
    span = 9.0
    for x in range(t.sx):
        for y in range(F - 1, t.sy):
            for z in range(t.sz):
                lx, ly, lz = ph.to_local(x, y, z)
                if not (1.5 <= lx <= 12.5):
                    continue
                u, v, w, hh = section_uv(h, lx, ly, lz)
                if abs(v + 0.6) > 0.62:
                    continue
                k = (abs(u) - (w - 0.4)) / span
                if k < 0 or k > 1:
                    continue
                trail, lead = 2.0 + 3.5 * k, 12.0 - 2.5 * k
                if not (trail <= lx <= lead):
                    continue
                if y < g.feet(x, z) - 1:
                    continue
                if k > 0.88 or lx > lead - 0.9:
                    s = 'or:durasteel_plating'                       # the tip and the leading edge
                elif abs(k - 0.55) < 0.06:
                    s = 'red_terracotta'                             # the squadron band
                elif abs(lx - round(lx / 4.0) * 4) < 0.5:
                    s = 'or:wreck_plating'
                else:
                    s = 'or:imperial_plating'
                t.set(x, y, z, s)
    # the door the salvagers cut in its belly, what came out of it stacked by the door
    door = []
    for c in shell:
        lx, ly, lz = local[c]
        u, v, w, hh = section_uv(h, lx, ly, lz)
        if 4.5 <= lx <= 6.5 and v < -0.6 * hh and F <= c[1] <= F + 2:
            t.set(*c, AIR)
            door.append(c)
    for c in inside:
        if c[1] == F and G.free(t, *c) and t.rng.random() < 0.2:
            t.set(*c, G.scatter_pick(t.rng, [('or:debris', 2), ('or:cargo_crate_imperial', 1), (S('or:console', facing='north'), 1)]))
    for (x, z, s) in ((14, 26, 'or:cargo_crate_imperial'), (15, 26, 'or:cargo_crate_imperial'), (14, 27, 'or:cargo_crate'),
                      (17, 27, G.barrel('up'))):
        t.set(x, g.feet(x, z), z, s)
    t.set(14, g.feet(14, 26) + 1, 26, 'or:cargo_crate_imperial')
    G.plate_stack(t, 19, g.feet(19, 25), 25, 2)
    G.scrap_heap(t, 28, g.feet(28, 12), 12, 2.4, 2.0, SEED + 83)
    lamp(t, g, 20, 23, 3)
    debris_field(t, g, (0, 3, 36, SIDE_D - 1), 0.035, SEED + 82)
    tracks(t, g, [(18, 0), (22, 8), (21, 20), (16, 25)], 2, SEED + 84)
    g.scatter(0.012)
    side_finish(t, g)
    return t, [((0, 0, 0, 36, SY - 1, SIDE_D - 1), set(inside))]


PIECES = [('yard_a', yard_a), ('yard_b', yard_b), ('bow', bow), ('engines', engines), ('crane', crane),
          ('fin', fin), ('ribs', ribs), ('shuttle', shuttle)]


def worldgen():
    adapted = dict(yard_stand=F, clear_from=SY, clear_inset=0, support_depth=8, ramp_rings=2)
    W.pool(NAME + '/start', [W.element(NAME + '/yard_a', adapted=adapted), W.element(NAME + '/yard_b', adapted=adapted)])
    for side, names in SIDES.items():
        W.pool(POOLS[side], [W.element(NAME + '/' + n, adapted=adapted) for n in names])
    W.structure(NAME, start_pool=NAME + '/start', biome_tag='has_structure/' + NAME, size=1, max_distance=64,
                start_height=1 - BURIED)
    W.structure_set(NAME, [(NAME, 1)], spacing=48, separation=18, salt=960300, placement='clear_of_origin',
                    min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:ghessar_dunes'])
    G.write_loot(LOOT, (3, 5), [
        ('or:durasteel_plate', 8, 1, 2), ('or:alloy_plate', 4, 1, 1), ('iron_ingot', 6, 1, 3),
        ('copper_ingot', 6, 2, 4), ('redstone', 4, 2, 4), ('gunpowder', 3, 1, 3), ('or:medpac', 2, 1, 1),
        ('or:hyperfuel_cell', 1, 1, 1)])


def check():
    r = jigsaw.assemble(**jigsaw.structure_args(NAME))
    print(r.report())
    return not r.problems


def main(preview_dir=None, only=None):
    for name, fn in PIECES:
        if only and name not in only:
            continue
        t, regions = fn()
        G.save(t, NAME + '/' + name, preview_dir, light_regions=regions)
    worldgen()
    check()


if __name__ == '__main__':
    args = sys.argv[1:]
    main(args[0] if args else None, set(args[1:]) or None)
