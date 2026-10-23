#!/usr/bin/env python3
"""mission/raider_camp_small and mission/raider_camp_small_2 (docs/MILESTONE_6.md 5.4).

Used by the clear_camp contract (the frontier board, the mentors' duties) and by Chapter Four's
"A Part Off the Books" (poachers squatting on a crashed freighter). Both variants: 29 x 18 x 29,
buried 1 (layer 0 is the ground, layer 1 the feet), high ground, anchors CREW_1..4 and HEART.

  raider_camp_small    "The Toll Fort": a raider crew has walled a hilltop with scrap beside the
                       settlers' track. A toll pole blocks the track; a watchtower with a smoking
                       signal brazier and a crimson rag watches it; inside the scrap palisade: the
                       crew's bunk tent, a cook fire with a spit and gnawed bones, stolen settler
                       crates, water and hay, two swoop bikes and a crystal-cutting lean-to.
  raider_camp_small_2  "The Wreck Camp": the forward half of a light freighter lies where it came
                       down, its nose dug in at the end of a long furrow. Poachers live in its hold
                       (a breach cut in its flank), keep a lookout on its back, and are hauling the
                       engine pod apart under a beam crane. Awnings, a cook fire and a scrap fence
                       make the yard in its lee.

Usage: python3 tools/assets/structures/qm/raider_camp_small.py [--preview <folder>]
"""
import math

import qmkit as K
from qmkit import S, AIR, Template, B, Noise, mod

SIZE = (29, 18, 29)
BURIED = 1
ANCHORS = ['CREW_1', 'CREW_2', 'CREW_3', 'CREW_4', 'HEART']
META = dict(buried=BURIED, terrain='high', max_slope=5, clearance=10, support_depth=8, ramp_rings=2, clear_above=6)


# =============================================================================================
# helpers
# =============================================================================================

def ellipse_cells(cx, cz, rx, rz, n=None, wobble=0.0):
    """Cells inside a (wobbly) ellipse, and the ring of inside cells with an outside 8-neighbour."""
    def inside(x, z):
        e = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
        if n is not None:
            e -= (n.at(x, 0, z) - 0.5) * wobble
        return e <= 1.0
    cells = {(x, z) for x in range(int(cx - rx - 3), int(cx + rx + 4)) for z in range(int(cz - rz - 3), int(cz + rz + 4))
             if inside(x, z)}
    ring = sorted((x, z) for (x, z) in cells
                  if any((x + dx, z + dz) not in cells for dx in (-1, 0, 1) for dz in (-1, 0, 1)))
    return cells, ring


def palisade(t, ring, yard, y, rng, mix, heights, gate, posts_every=3, centre=(14.5, 17.0)):
    """A scrap palisade on the ring cells: sheets of plating 2 to 5 high (`heights`) with a ragged cap of
    bars, rods and bent plates; a beam post every few cells standing one higher, some topped with a rod
    or a skull; a firing slit of bars here and there; outside, a berm of sandbags and stones at its foot.
    gate: cells left open."""
    order = sorted(ring, key=lambda p: math.atan2(p[1] - centre[1], p[0] - centre[0]))
    for i, (x, z) in enumerate(order):
        if (x, z) in gate:
            continue
        h = heights(x, z)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):  # the berm outside
            ox, oz = x + dx, z + dz
            if (ox, oz) in yard or not t.inside(ox, y, oz) or t.get(ox, y, oz) is not None:
                continue
            r = rng.random()
            if r < 0.26:
                t.set(ox, y, oz, K.slab(rng.choice(['minecraft:cobblestone_slab', 'minecraft:mud_brick_slab',
                                                    'or:canvas_slab', 'minecraft:cobblestone_slab'])))
            elif r < 0.36:
                t.set(ox, y, oz, K.CANVAS if rng.random() < 0.5 else K.PACKED_MUD)
            else:
                continue
            K.support(t, ox, y, oz, K.GRAVEL)
        if i % posts_every == 0:
            K.column(t, x, y, y + h, z, K.beam('y'))
            if rng.random() < 0.4:
                t.set(x, y + h + 1, z, K.skull(rng.randrange(16)) if rng.random() < 0.3 else K.rod())
            continue
        for k in range(h):
            s = mix(x, y + k, z)
            if k == 1 and h >= 3 and rng.random() < 0.14:
                s = K.bars()  # a firing slit
            t.set(x, y + k, z, s)
        r = rng.random()
        if r < 0.16:
            t.set(x, y + h, z, K.bars())
        elif r < 0.25:
            t.set(x, y + h, z, K.rod())
        elif r < 0.36:
            t.set(x, y + h, z, K.plating_stairs(rng.choice(['north', 'south', 'east', 'west'])))
        elif r < 0.44:
            t.set(x, y + h, z, K.PLATING_SLAB)


# =============================================================================================
# raider_camp_small: The Toll Fort
# =============================================================================================

def build_toll_fort():
    t = Template(*SIZE, seed=6101)
    rng = t.rng
    G, F = 0, 1  # ground and feet layers
    worn = K.Mix(K.WORN, 6103, scale=4.5, jitter=0.15)
    track = K.Mix(K.TRACK, 6105, scale=3.5, jitter=0.2)
    scrap = K.Mix(K.SCRAP, 6107, scale=2.6, jitter=0.25)

    # --- the yard and the palisade around it --------------------------------------------------
    cx, cz = 14.5, 17.0
    wob = Noise(6109, 4.0)
    yard, ring = ellipse_cells(cx, cz, 10.6, 8.7, wob, 0.18)
    gate = {(13, z) for z in range(6, 11)} | {(14, z) for z in range(6, 11)} | {(15, z) for z in range(6, 11)}
    gate &= set(ring)
    K.ground_blob(t, G, cx, cz + 0.5, 10.4, 8.6, worn, 6111, fray=0.12)
    hn = Noise(6113, 3.0)

    def wall_h(x, z):
        h = int(round(0.6 + 5.0 * hn.at(x, 0, z)))
        if abs(x - 14) <= 3 and z < cz:  # higher either side of the gate
            h += 1
        return max(2, min(5, h))
    palisade(t, ring, yard, F, rng, scrap, wall_h, gate)
    for (x, z) in ring:  # footings: the wall stands on worn ground
        t.set(x, G, z, K.GRAVEL if (x + z) % 3 else K.COARSE)

    # the gate: two tall posts, a crossbar with chains and a skull, the leaves swung in
    gx0, gx1 = 12, 16
    gz = min(z for (x, z) in ring if x == 14)
    for x in (gx0, gx1):
        K.column(t, x, F, F + 5, gz, K.beam('y'))
        t.set(x, G, gz, K.GRAVEL)
    for x in range(gx0, gx1 + 1):
        t.set(x, F + 5, gz, K.beam('x'))
    t.set(gx0, F + 6, gz, K.lantern())
    t.set(gx1, F + 6, gz, K.CANVAS_CRIMSON)
    t.set(gx1, F + 7, gz, K.rod())
    for x, n in ((13, 2), (15, 1)):
        for k in range(n):
            t.set(x, F + 4 - k, gz, K.chain('y'))
    t.set(14, F + 4, gz, K.chain('y'))
    t.set(14, F + 3, gz, K.skull(8))
    for (x, z) in ((gx0 + 1, gz + 1), (gx0 + 1, gz + 2), (gx1 - 1, gz + 1)):
        for k in range(3):
            t.set(x, F + k, z, scrap(x, F + k, z))
    for x in range(13, 16):
        for z in range(gz - 1, gz + 2):
            t.set(x, F, z, AIR)
            t.set(x, F + 1, z, AIR)

    # --- the settlers' track along the north, the toll pole across it ---------------------------
    track_cells = K.trail(t, G, [(-1, 3.0), (6, 2.5), (12, 3.5), (18, 3.0), (24, 2.0), (29, 2.5)], 4, track, 6115)
    K.trail(t, G, [(14, 3.5), (14, 6.0), (14, gz)], 3, track, 6117)
    tx = 21
    for z in (0, 5):
        K.column(t, tx, F, F + 2, z, K.beam('y'))
        t.set(tx, G, z, K.GRAVEL)
    for z in range(1, 5):
        t.set(tx, F + 1, z, K.beam('z'))
    t.set(tx, F + 3, 0, K.lantern())
    t.set(tx, F + 3, 5, K.CANVAS_CRIMSON)
    t.set(tx, F, 2, K.chain('y'))
    t.set(tx, F, 3, K.chain('y'))
    # the toll man's seat and his take: a crate stool, a pot of coin, a brazier on a stone
    t.set(23, F, 6, K.CRATE)
    t.set(23, F + 1, 6, K.CANVAS_SLAB)
    t.set(24, F, 7, K.pot())
    t.set(19, F, 6, K.COBBLE)
    t.set(19, F + 1, 6, K.campfire(lit=True))
    t.set(19, G, 6, K.GRAVEL)

    # --- the watchtower in the north-east of the yard --------------------------------------------
    px0, pz0, px1, pz1 = 19, 12, 22, 15
    deck_y = F + 9
    roof_y = deck_y + 3
    corners = ((px0, pz0), (px1, pz0), (px0, pz1), (px1, pz1))
    for (x, z) in corners:
        K.column(t, x, F, roof_y, z, K.beam('y'))
        t.set(x, G, z, K.GRAVEL)
    # a ledge of beams halfway up, bars braced across two faces
    for x in range(px0 + 1, px1):
        t.set(x, F + 4, pz0, K.beam('x'))
        t.set(x, F + 4, pz1, K.beam('x'))
    for z in range(pz0 + 1, pz1):
        t.set(px0, F + 4, z, K.beam('z'))
        t.set(px1, F + 4, z, K.beam('z'))
    for k in range(1, 4):
        t.set(px0 + (k % 2) + 1, F + 4 + k, pz1, K.bars())
        t.set(px1, F + 4 + k, pz0 + (k % 2) + 1, K.bars())
    # the platform, one wider than the posts, with an outrigger east for the signal brazier
    for x in range(px0 - 1, px1 + 2):
        for z in range(pz0 - 1, pz1 + 2):
            if (x, z) in corners:
                continue
            t.set(x, deck_y, z, K.GRATE if (x + z) % 3 == 0 else K.DECK)
    ox = px1 + 2
    for z in range(pz0 - 1, pz1 + 1):
        t.set(ox, deck_y, z, K.GRATE)
    for z in (pz0, pz1 - 1):
        t.set(ox, deck_y - 1, z, K.plating_stairs('west', 'top'))
    t.set(ox, deck_y, pz1 - 2, K.HAY)
    t.set(ox, deck_y + 1, pz1 - 2, K.campfire(lit=True, signal=True))
    t.set(ox, deck_y + 1, pz1 - 1, K.slab('minecraft:cobblestone_slab'))
    t.set(ox, deck_y + 1, pz1 - 3, K.COBBLE)
    t.set(px0 + 1, deck_y, pz0, AIR)  # the ladder hole
    for y in range(F, deck_y + 1):
        t.set(px0 + 1, y, pz0, K.ladder('east'))
    # the rail, with gaps
    for x in range(px0 - 1, px1 + 2):
        for z in range(pz0 - 1, pz1 + 2):
            edge = x == px0 - 1 or z in (pz0 - 1, pz1 + 1)
            if edge and t.get(x, deck_y + 1, z) is None and rng.random() < 0.75:
                t.set(x, deck_y + 1, z, K.bars())
    # a pitched canvas roof on the posts, its back gable closed
    for k in range(3):
        for z in range(pz0 - 1, pz1 + 2):
            t.set(px0 - 1 + k, roof_y + k, z, K.canvas_stairs('east'))
            t.set(px1 + 1 - k, roof_y + k, z, K.canvas_stairs('west'))
    for x in (px0 + 1, px1 - 1):
        t.set(x, roof_y + 1, pz1 + 1, K.CANVAS)
    t.set(px0 + 1, roof_y + 1, pz0 + 1, K.lantern(True))
    # the crimson rag on a pole at the outrigger's end, a red beacon on top
    K.column(t, ox, deck_y + 1, deck_y + 6, pz0 - 1, K.beam('y'))
    t.set(ox, deck_y + 7, pz0 - 1, K.rod())
    t.set(ox, deck_y + 5, pz0 - 1, K.LIGHT_RED)
    K.banner(t, ox + 1, deck_y + 4, pz0 - 1, 'east', 'red')
    # a crate of bolts and a lookout's stool up there
    t.set(px0, deck_y + 1, pz1 + 1, K.CRATE)
    t.set(px1 - 1, deck_y + 1, pz0, K.CANVAS_SLAB)

    # --- the crew's bunk tent (west), the small tent (north-west) --------------------------------
    at = K.a_tent(t, 5, 17, 7, 7, F, ridge='x', door='max',
                  patches={(1, 0, 'l'): K.wool('brown'), (4, 1, 'r'): K.wool('brown'), (2, 2, 'l'): K.CANVAS})
    for i in range(7):
        for j in range(7):
            x, z = at(i, j)
            t.set(x, G, z, K.PACKED_MUD if (i + j) % 4 else K.COARSE)
    for i in (1, 2, 3, 4):
        K.bedroll(t, *at(i, 1)[:1], F, at(i, 1)[1], color='brown' if i % 2 else 'gray', length=1)
        K.bedroll(t, *at(i, 5)[:1], F, at(i, 5)[1], color='gray' if i % 2 else 'green', length=1)
    x, z = at(1, 3)
    t.set(x, F, z, K.CRATE)
    t.set(x, F + 1, z, K.candle(2))
    for (x, z) in ((12, 16), (12, 24)):
        if (x, z) in yard and (x, z) not in ring:
            t.set(x, F, z, K.fence())
    small = K.a_tent(t, 6, 12, 4, 5, F, ridge='z', door='min', back=K.wool('brown'))
    for i in range(4):
        for j in range(5):
            x, z = small(i, j)
            t.set(x, G, z, K.COARSE)
    x, z = small(1, 1)
    K.bedroll(t, x, F, z, color='red', along='z', length=2)
    x, z = small(1, 3)
    t.set(x, F, z, K.CRATE_REP)

    # --- the cook fire in the middle of the yard ---------------------------------------------------
    fx, fz = 15, 19
    K.fire_pit(t, fx, F, fz, rng, spit='x', stones=0.7)
    t.set(fx - 2, F, fz + 1, K.log(axis='z'))
    t.set(fx - 2, F, fz, K.log(axis='z'))
    t.set(fx + 2, F, fz + 2, K.CRATE)
    t.set(fx + 1, F, fz + 2, K.CANVAS_SLAB)
    t.set(fx + 3, F, fz, S('cauldron'))
    t.set(fx - 1, F, fz + 3, S('bone_block', axis='x'))
    t.set(fx - 2, F, fz + 3, S('bone_block', axis='z'))
    t.set(fx - 1, F + 1, fz + 3, K.skull(5))
    K.lamp_post(t, fx + 3, F, fz - 2, height=3, arm='west')

    # --- the workshop lean-to against the south wall -----------------------------------------------
    wz = max(z for (x, z) in ring if x == 17)
    K.lean_to(t, 14, wz - 3, 20, wz - 1, F, 'south', 3)
    for x in range(14, 21):
        for z in range(wz - 3, wz):
            t.set(x, G, z, K.PACKED_MUD if (x * 7 + z) % 5 else K.GRAVEL)
    t.set(14, F, wz - 1, S('stonecutter', facing='north'))
    t.set(15, F, wz - 1, K.PLATING_SLAB_TOP)
    t.set(15, F + 1, wz - 1, K.cluster())
    t.set(16, F, wz - 1, K.PLATING_SLAB_TOP)
    t.set(16, F + 1, wz - 1, S('amethyst_cluster', facing='up', waterlogged=False))
    t.set(17, F, wz - 1, S('grindstone', face='floor', facing='north'))
    t.set(18, F, wz - 1, S('chipped_anvil', facing='east'))
    t.set(19, F, wz - 1, K.CRATE)
    t.set(19, F + 1, wz - 1, S('large_amethyst_bud', facing='up', waterlogged=False))
    t.set(20, F, wz - 2, K.CRATE)
    t.set(17, F + 2, wz - 1, K.LIGHT_AMBER)
    for z in range(wz - 3, wz):
        for x in range(15, 20):
            if t.get(x, F + 1, z) is None:
                t.set(x, F + 1, z, AIR)
            if t.get(x, F + 2, z) is None:
                t.set(x, F + 2, z, AIR)

    # --- the stolen goods: settler crates, hay, the water sled -------------------------------------
    K.crate_pile(t, [(20, 16, 2), (21, 16, 1), (20, 17, 3), (21, 17, 2), (22, 17, 1), (20, 18, 1), (23, 16, 1)], F, rng,
                 kinds=((K.CRATE, 4), (K.CRATE_REP, 2)), tarp=0.6)
    t.set(23, F, 18, K.HAY)
    t.set(23, F, 19, K.HAY)
    t.set(23, F + 1, 18, S('hay_block', axis='z'))
    for (x, z) in ((21, 20), (22, 20), (21, 21), (22, 21)):
        t.set(x, F, z, K.PLATING_SLAB_TOP)
    t.set(21, F + 1, 20, S('water_cauldron', level=3))
    t.set(22, F + 1, 21, S('water_cauldron', level=2))
    t.set(20, F, 21, K.plating_stairs('west', 'top'))

    # --- swoop bikes by the gate, a lamp post ---------------------------------------------------------
    K.speeder(t, 11, F, 11, 'north')
    K.speeder(t, 16, F, 12, 'west')
    K.lamp_post(t, 12, F, 11, height=2)

    # --- outside: a refuse heap of scrap and bones, plates leaning on the wall ----------------------
    for (x, z, s) in ((2, 20, K.RUST), (3, 21, K.SCORCHED), (2, 21, K.DEBRIS), (3, 22, S('bone_block', axis='y')),
                      (1, 19, K.slab('cobblestone_slab')), (26, 22, K.RUST), (26, 23, K.plating_stairs('west'))):
        t.set(x, F, z, s)
        K.support(t, x, F, z, K.GRAVEL)

    # --- people ----------------------------------------------------------------------------------------
    K.spot(t, 'CREW_1', 22, F, 5)   # the toll man by the pole
    K.spot(t, 'CREW_2', 14, F, 21)  # at the fire
    K.spot(t, 'CREW_3', 17, F, wz - 2)  # at the cutting bench
    K.spot(t, 'CREW_4', 18, F, 14)  # under the tower
    K.spot(t, 'HEART', 15, F, 16)   # the heart of the yard, before the fire

    # --- wear and litter -----------------------------------------------------------------------------------
    K.scorch_burst(t, F, 14, gz + 1, 2.5, rng, density=0.5)
    K.litter(t, F, (5, 9, 24, 25), [(K.DEBRIS, 5), (K.SCORCH, 1), (S('bone_block', axis='y'), 0)], 0.05, rng)
    K.litter(t, F, (0, 0, 28, 7), [(K.DEBRIS, 3)], 0.04, rng)
    return t


# =============================================================================================
# raider_camp_small_2: The Wreck Camp
# =============================================================================================

def build_wreck_camp():
    t = Template(*SIZE, seed=6201)
    rng = t.rng
    G, F = 0, 1
    worn = K.Mix(K.WORN, 6203, scale=4.5, jitter=0.15)
    hullmix = K.Mix([(K.WRECK, 5), (K.PLATING, 3), (K.SCORCHED, 1), (K.RUST, 1)], 6205, scale=2.4, jitter=0.2)
    scrap = K.Mix(K.SCRAP, 6207, scale=2.6, jitter=0.25)
    dmg = Noise(6209, 2.2)

    # --- the hull: the forward half of a light freighter, nose east and dug in, rolled onto its south
    # side so that its north wing stands up over the yard ------------------------------------------------
    x0, x1 = 3, 26
    zc0 = 21.0

    def section(x):
        """(yc, rz, ry) of the hull section at x: wide and low, tapering to a dug-in nose."""
        f = (x - x0) / float(x1 - x0)
        rz, ry = 5.3, 4.0
        if x > 13:
            k = (x - 13) / float(x1 - 13 + 1)
            rz *= 1 - 0.62 * k * k
            ry *= 1 - 0.45 * k * k
        return 2.8 - 1.3 * f, rz, ry

    def inside(x, y, z, shrink=0.0):
        yc, rz, ry = section(x)
        zz = z - zc0 - 0.35 * (y - yc)
        return (abs(zz) / (rz - shrink)) ** 2.2 + (abs(y - yc) / (ry - shrink)) ** 2.2 <= 1.0

    def torn(x, y, z):  # the hull ends raggedly where the tail tore away
        return x < 7 + int(3 * dmg.at(0, y, z))

    shell, hold = set(), set()
    for x in range(x0, x1 + 1):
        for y in range(0, 10):
            for z in range(12, 29):
                if not inside(x, y, z):
                    continue
                if inside(x, y, z, 1.0) and x < x1 - 1:
                    hold.add((x, y, z))
                else:
                    shell.add((x, y, z))
    hull = {c for c in shell if not torn(*c)}

    def top_at(x):
        ys = [y for (xx, y, zz) in hull if xx == x]
        return max(ys) if ys else None

    def flank_z(x, y):  # the outermost hull cell on the north side at (x, y)
        zs = [z for (xx, yy, z) in hull if xx == x and yy == y]
        return min(zs) if zs else None

    for (x, y, z) in sorted(hull):
        t.set(x, y, z, hullmix(x, y, z))
    for (x, y, z) in sorted(hold):
        if not torn(x, y, z):
            t.set(x, y, z, (K.DECK if (x * 3 + z) % 5 else K.GRATE) if y == 0 else AIR)
    # bevel the top edges with stairs so the hull reads smooth, not boxy
    for (x, y, z) in sorted(hull):
        yc, rz, ry = section(x)
        if (x, y + 1, z) in hull or y <= yc + 0.5:
            continue
        n_in, s_in = (x, y, z - 1) in hull, (x, y, z + 1) in hull
        if s_in and not n_in:
            t.set(x, y, z, K.plating_stairs('south'))
        elif n_in and not s_in:
            t.set(x, y, z, K.plating_stairs('north'))
    # a white band of Republic registry plating along the north flank, vents in a row under it
    for x in range(9, 24):
        yc, rz, ry = section(x)
        yb = int(round(yc + 0.4))
        fz = flank_z(x, yb)
        if fz is None:
            continue
        if dmg.at(x, yb, fz) < 0.7:
            t.set(x, yb, fz, K.REP_PLATING)
        if x % 3 == 0 and flank_z(x, yb - 1) is not None:
            t.set(x, yb - 1, flank_z(x, yb - 1), K.VENT)
    # a sensor mast on its back
    mx = 9
    ty = top_at(mx)
    mz = sorted(z for (xx, yy, z) in hull if xx == mx and yy == ty)[0]
    K.mast(t, mx, ty + 1, mz, 4, top=K.LIGHT_RED, arms=((2, 'south', 1), (3, 'west', 2)))
    # exposed ribs where the tail tore away, cables hanging from the torn edge
    for rx in (4, 6):
        for (x, y, z) in sorted(shell):
            if x != rx or dmg.at(x * 7, y, z) > 0.74:
                continue
            yc, rz, ry = section(x)
            side = abs(z - zc0 - 0.35 * (y - yc)) > rz * 0.6
            t.set(x, y, z, K.beam('y') if side else K.beam('z'))
    for (x, y, z) in sorted(hull):
        if (x - 1, y, z) not in hull and y >= 3 and rng.random() < 0.3 and t.get(x - 1, y - 1, z) is None:
            t.set(x - 1, y - 1, z, K.chain('y'))
            if rng.random() < 0.5 and t.get(x - 1, y - 2, z) is None:
                t.set(x - 1, y - 2, z, K.CONDUIT if y - 2 == 0 else K.chain('y'))
    # the crew's tarp across the open end of the hold
    for (x, y, z) in sorted(hold):
        if x == 10 and y >= 1 and not torn(x, y, z) and dmg.at(x, y * 3, z) < 0.7:
            t.set(x, y, z, K.CANVAS if (y + z) % 5 else K.wool('brown'))
    # scorched around the nose, a raised canopy over the cockpit
    for (x, y, z) in sorted(hull):
        if x >= 21 and dmg.at(x, y, z) > 0.56:
            t.set(x, y, z, K.SCORCHED)
    for x in (19, 20, 21):
        ty = top_at(x)
        zs = sorted(z for (xx, yy, z) in hull if xx == x and yy == ty)
        for z in zs[:2]:
            t.set(x, ty + 1, z, S('tinted_glass') if x == 19 else S('gray_stained_glass'))
    for z in sorted(z for (xx, yy, z) in hull if xx == 22 and yy == top_at(22))[:2]:
        t.set(22, top_at(22) + 1, z, K.plating_stairs('west'))
    # earth heaped over the buried nose and thrown up ahead of it
    for x in range(22, 29):
        for z in range(14, 29):
            d = abs(z - zc0) / 6.0 + max(0, x - 24) / 5.0
            for y in (1, 2, 3):
                lim = (1.0, 0.62, 0.35)[y - 1]
                if d < lim + (dmg.at(x, y, z) - 0.5) * 0.3 and t.get(x, y, z) is None:
                    t.set(x, y, z, K.COARSE if dmg.at(x, y * 5, z) > 0.42 else K.ROOTED)
                    t.set(x, y - 1, z, t.get(x, y - 1, z) or K.DIRT)

    # --- the north wing, wrenched up into the air over the yard; its tip propped as a lookout ---------
    root = min(flank_z(x, 4) for x in range(10, 19) if flank_z(x, 4) is not None)
    wing = set()
    for d in range(1, 8):
        y = 3 + d
        z = root - d
        xa, xb = 10 + int(round(d * 0.45)), 19 - int(round(d * 0.75))
        for x in range(xa, xb + 1):
            if d == 7 and dmg.at(x, 9, 9) > 0.62:
                continue  # the broken tip
            edge = x in (xa, xb)
            t.set(x, y, z, K.SCORCHED if (edge and x == xa and d % 3 == 0) else K.plating_stairs('north'))
            wing.add((x, y, z))
        if t.get(xb + 1, y, z) is None and d < 6:
            t.set(xb + 1, y, z, K.PLATING_SLAB_TOP if d % 2 else K.RUST)
    t.set(10 + int(round(7 * 0.45)), 10, root - 7, K.LIGHT_RED)  # its nav light, still red
    wz = root - 8  # the lookout perch on two props
    px0, px1 = 12, 15
    for x in range(px0, px1 + 1):
        for z in (wz, wz - 1):
            t.set(x, 10, z, K.GRATE)
    for x in (px0, px1):
        K.column(t, x, F, 9, wz - 1, K.beam('y'))
        t.set(x, G, wz - 1, K.GRAVEL)
    for x in range(px0, px1 + 1):
        if x != px0 + 1:
            t.set(x, 11, wz - 2, K.bars())
    t.set(px1 + 1, 11, wz, K.bars())
    t.set(px1, 11, wz, K.campfire(lit=False))
    t.set(px1, 11, wz - 1, K.CANVAS_SLAB)
    for y in range(F, 11):
        t.set(px0 + 1, y, wz - 2, K.ladder('north')) if False else None
    for y in range(F, 11):
        t.set(px0, y, wz - 2, K.ladder('north'))
    t.set(px0, G, wz - 2, K.GRAVEL)
    # tarps hung from the wing's leading edge make a shelter under it
    for d in (2, 3, 4):
        xb = 19 - int(round(d * 0.75))
        for k in range(1, 3 + d - 1):
            y = 3 + d - k
            if y >= F + 2 and t.get(xb + 1, y, root - d) is None:
                t.set(xb + 1, y, root - d, K.CANVAS if (k + d) % 4 else K.wool('brown'))

    # --- the breach torched into the north flank under the wing root: the door to the hold --------------
    bx0, bx1 = 12, 14
    for x in range(bx0, bx1 + 1):
        for y in range(1, 4):
            for z in range(12, 20):
                if (x, y, z) in hull:
                    t.set(x, y, z, AIR)
    for x in (bx0 - 1, bx1 + 1):
        for y in range(1, 5):
            fz = flank_z(x, y)
            if fz is not None:
                t.set(x, y, fz, K.SCORCHED)
    # inside: bunks along the far wall, crates of loot, a table, the dead nav console, lamps
    back = {}
    for (x, y, z) in hold:
        if y == 1 and not torn(x, y, z):
            back[x] = max(back.get(x, 0), z)
    for x in range(11, 20, 2):
        if x in back:
            K.bedroll(t, x, 1, back[x] - 1, color='gray' if x % 4 == 1 else 'brown', along='x', length=1)
            t.set(x, 1, back[x], K.CRATE if x % 3 else K.CANVAS_SLAB)
    K.crate_pile(t, [(x, back[x], h) for (x, h) in ((18, 1), (19, 2), (20, 1)) if x in back], 1, rng,
                 kinds=((K.CRATE, 3), (K.CRATE_IMP, 1)), tarp=0.4)
    for (x, z) in ((11, 21), (12, 21)):
        t.set(x, 1, z, K.PLATING_SLAB_TOP)
    t.set(11, 2, 21, K.cluster())
    t.set(12, 2, 21, K.candle(3, lit=True))
    t.set(11, 1, 22, S('stripped_spruce_log', axis='x'))
    cx_ = max(x for (x, y, z) in hold if y == 1 and not torn(x, y, z))
    t.set(cx_, 1, 21, K.console('west'))
    t.set(cx_, 2, 21, K.holo('west'))
    for x in (12, 17):
        zl = int(round(zc0))
        for y in range(9, 1, -1):
            if (x, y, zl) in hold and t.get(x, y + 1, zl) not in (None, AIR):
                t.set(x, y, zl, K.lantern(True))
                break
    mod.light_fill(t, (10, 1, 13, 24, 4, 28))

    # --- the furrow it ploughed, coming in from the west ---------------------------------------------------
    for x in range(0, 7):
        for z in range(15, 28):
            d = abs(z - zc0) - (2.6 + 0.8 * dmg.at(x, 0, z))
            if (x, 0, z) in shell or t.get(x, 0, z) is not None:
                continue
            if d < 0:
                t.set(x, 0, z, AIR)
                t.set(x, 1, z, AIR)
            elif d < 1.4:
                t.set(x, 1, z, K.COARSE if dmg.at(x, 1, z) > 0.45 else K.ROOTED)
                t.set(x, 0, z, K.DIRT)
    for (x, z, s) in ((1, 20, K.SCORCHED), (0, 19, K.RUST), (5, 23, K.CONDUIT), (3, 18, K.SALVAGE)):
        if t.get(x, 0, z) == AIR:
            t.set(x, 0, z, s)
        else:
            t.set(x, 1, z, s)

    # --- the yard in its lee: worn ground, a scrap fence, the fire, the engine pod --------------------
    K.ground_blob(t, G, 14.0, 8.0, 12.0, 6.6, worn, 6211, fray=0.15, only_open=True)
    K.trail(t, G, [(16, -1), (16, 3), (15, 6)], 3, K.Mix(K.TRACK, 6213, 3.5, 0.2), 6215)
    fence_cells = [(2, z) for z in range(4, 14)] + [(x, 2) for x in range(3, 11)] + \
                  [(27, z) for z in range(4, 14)] + [(x, 2) for x in range(21, 27)]
    fn = Noise(6217, 2.5)
    for i, (x, z) in enumerate(fence_cells):
        if rng.random() < 0.12:
            continue
        h = 1 + (1 if fn.at(x, 0, z) > 0.45 else 0)
        for k in range(h):
            t.set(x, F + k, z, scrap(x, F + k, z) if i % 4 else K.CRATE)
        t.set(x, G, z, K.GRAVEL)
        if i % 5 == 0:
            K.column(t, x, F, F + h, z, K.beam('y'))
    # the crew's beds under the wing, the cutting bench by its trailing edge
    for (x, z, c) in ((10, 12, 'brown'), (10, 10, 'gray'), (12, 12, 'green')):
        K.bedroll(t, x, F, z, color=c, along='x', length=2)
    t.set(10, F, 11, K.CRATE)
    t.set(10, F + 1, 11, K.lantern())
    t.set(17, F, 12, S('stonecutter', facing='north'))
    t.set(18, F, 12, K.PLATING_SLAB_TOP)
    t.set(18, F + 1, 12, K.cluster())
    t.set(19, F, 12, K.CRATE)
    t.set(19, F, 11, K.CRATE_IMP)
    t.set(19, F + 1, 12, S('large_amethyst_bud', facing='up', waterlogged=False))
    t.set(17, F + 2, 12, K.LIGHT_AMBER) if t.get(17, F + 2, 12) is None else None
    # the cook fire and its seats, west of the wing
    fx, fz = 6, 9
    K.fire_pit(t, fx, F, fz, rng, spit='x', stones=0.65)
    t.set(fx, F, fz + 2, K.log(axis='x'))
    t.set(fx + 1, F, fz + 2, K.log(axis='x'))
    t.set(fx - 2, F, fz - 1, K.CRATE)
    t.set(fx - 2, F, fz + 1, K.CANVAS_SLAB)
    t.set(fx + 2, F, fz - 2, S('cauldron'))
    K.lamp_post(t, 8, F, 5, height=3, arm='west')
    K.lamp_post(t, 24, F, 11, height=3, arm='west')
    # the engine pod they are stripping, under a beam crane, east of the wing
    ex, ez = 21, 7
    for x in range(ex, ex + 4):
        for z in range(ez - 1, ez + 2):
            for y in (F, F + 1):
                if z in (ez - 1, ez + 1) and y == F + 1:
                    t.set(x, y, z, K.plating_stairs('north' if z == ez + 1 else 'south', 'bottom'))
                else:
                    t.set(x, y, z, K.pipes('x') if y == F + 1 else K.PLATING)
            t.set(x, G, z, K.GRAVEL)
    t.set(ex - 1, F, ez, K.VENT)
    t.set(ex - 1, F + 1, ez, K.SCORCHED)
    t.set(ex + 4, F, ez, K.VENT)
    for (x, z) in ((ex + 4, ez - 1), (ex + 4, ez + 1)):
        t.set(x, F, z, K.SCORCHED)
    for (x, z) in ((ex + 3, ez - 3), (ex + 3, ez + 3)):
        K.column(t, x, F, F + 4, z, K.beam('y'))
        t.set(x, G, z, K.GRAVEL)
    for z in range(ez - 3, ez + 4):
        t.set(ex + 3, F + 5, z, K.beam('z'))
    t.set(ex + 3, F + 4, ez, K.chain('y'))
    t.set(ex + 3, F + 3, ez, K.chain('y'))
    t.set(ex + 3, F + 2, ez, K.pipes('y'))
    for (x, z) in ((ex, ez + 3), (ex + 1, ez + 3), (ex + 2, ez + 3)):
        t.set(x, F, z, K.carpet('light_gray'))
    t.set(ex, F, ez + 3, S('chipped_anvil', facing='north'))
    t.set(ex + 2, F, ez + 3, K.SALVAGE)

    # --- people -------------------------------------------------------------------------------------------
    K.spot(t, 'CREW_1', ex + 1, F, ez - 2)   # at the crane
    K.spot(t, 'CREW_2', fx, F, fz - 2)       # at the fire
    K.spot(t, 'CREW_3', 13, F, 14)           # at the breach
    K.spot(t, 'CREW_4', 18, F, 3)            # watching the way in
    K.spot(t, 'HEART', 15, F, 9)             # the middle of the yard, under the wing

    K.litter(t, F, (3, 3, 26, 14), [(K.DEBRIS, 5), (K.SCORCH, 1)], 0.05, rng)
    K.scorch_burst(t, F, 24, 13, 2.0, rng)
    return t


def main():
    a = K.args()
    for tid, build in (('mission/raider_camp_small', build_toll_fort), ('mission/raider_camp_small_2', build_wreck_camp)):
        if a.only and a.only not in tid:
            continue
        K.save(build(), tid, ANCHORS, META)


if __name__ == '__main__':
    main()
