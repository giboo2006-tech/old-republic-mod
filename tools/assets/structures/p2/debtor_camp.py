"""site/debtor_camp (runtime, Ghessar's canyons): where a man who owes the Vessba kajidic went to ground.

A salvage crew's camp in the lee of a banded rock outcrop: the rock's lip overhangs a shallow alcove where the
debtor sleeps beside the strongbox the kajidic wants. In front of it, around a fire pit, the crew's lean-tos of
plating and canvas, their workshop round a landspeeder stripped for parts, and the empty water barrels that
say how long they have been out here. A crescent of scrap walls closes the camp toward the open canyon,
with one way in; on it someone has already tied the kajidic's crimson cloth. "They know where he is."

The template's north side faces the player's approach (the placer turns it so): the way in is on the north.

Kept anchors (docs/MILESTONE_6.md 5.6): HEART (south of the fire), NPC_DEBTOR (north of the fire, where he
waits), SPAWNER (feet cell in the crew's corner where the scavengers rise to fight, SaltgateWays), STRONGBOX
(the debtor's chest, no loot table: the box is the story's, not loot).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gkit as G  # noqa: E402
from gkit import S, AIR, B, mod, meta, W, Noise, Template  # noqa: E402

ID = 'site/debtor_camp'
SX, SY, SZ = 35, 17, 35
BURIED = 2
F = BURIED
FL = F - 1
SEED = 960303
CX = 17

# canyon strata, bottom to top (a band is two or three cells deep)
STRATA = ['red_sandstone', 'terracotta', 'orange_terracotta', 'orange_terracotta', 'red_terracotta',
          'smooth_red_sandstone', 'terracotta', 'white_terracotta', 'orange_terracotta', 'red_sandstone',
          'brown_terracotta', 'terracotta', 'red_terracotta', 'orange_terracotta']


def smooth(a, b, v):
    if v <= a:
        return 0.0
    if v >= b:
        return 1.0
    k = (v - a) / float(b - a)
    return k * k * (3 - 2 * k)


def build():
    t = Template(SX, SY, SZ, seed=SEED, name=ID)
    rng = t.rng

    # ------------------------------------------------------------ ground: a dry canyon floor
    g = G.Ground(t, F, 'canyon', seed=SEED)
    g.noise(amp=1, scale=7.0)
    g.taper(slope=2)
    g.feather()
    g.apply(clear_above=4)

    # ------------------------------------------------------------ the outcrop: banded rock wrapping the camp's back
    wob = Noise(SEED + 1, 4.0)
    hn = Noise(SEED + 2, 5.0)
    band_n = Noise(SEED + 3, 9.0)

    def stratum(x, y, z):
        k = int(y + (band_n.at(x, 0, z) - 0.5) * 3)
        k = max(0, k)
        return STRATA[(k // 2) % len(STRATA)]

    def face_z(x):
        return 22 + 5 * (1 - ((x - CX) / 18.0) ** 2) + (wob.at(x, 0, 0) - 0.5) * 2.5

    rock = set()
    top_of = {}
    for x in range(SX):
        for z in range(SZ):
            fz = face_z(x)
            if z < fz:
                continue
            # a steep face, a tall mass behind it, its back falling away in the last cells before the edge
            s = smooth(2.0, 7.0, x) * smooth(2.0, 7.0, SX - 1 - x) * smooth(1.6, 4.5, SZ - 1 - z)
            s *= 0.75 + 0.25 * smooth(fz - 0.5, fz + 1.5, z)
            h = F + (11 + (hn.at(x, 0, z) - 0.5) * 5) * s
            top = int(round(h))
            if top <= F:
                continue
            top_of[(x, z)] = top
            for y in range(0, top):
                rock.add((x, y, z))
    # the lip: an overhang over the camp's back, two or three cells thick, under the rock's brow
    lip = set()
    for x in range(9, 27):
        fz = int(math.floor(face_z(x)))
        for dz in (1, 2, 3):
            z = fz - dz
            reach = 3 - int(abs(x - CX) / 4.0)
            if dz > reach:
                continue
            brow = top_of.get((x, fz + 1), F + 6)
            y1 = min(brow, F + 10)
            y0 = F + 6 + dz - (1 if wob.at(x, 3, z) > 0.6 else 0)
            for y in range(y0, y1):
                lip.add((x, y, z))
    # the alcove: the face hollowed under the lip, two cells deep
    alcove = set()
    for x in range(12, 24):
        fz = int(math.floor(face_z(x)))
        for z in range(fz - 1, fz + 2):
            for y in range(F, F + 4 + (1 if 15 <= x <= 20 else 0)):
                alcove.add((x, y, z))
    for c in rock | lip:
        if c not in alcove:
            t.set(*c, stratum(*c))
    for c in alcove:
        if c in rock or c in lip:
            t.set(*c, AIR)
    for x in range(12, 24):
        fz = int(math.floor(face_z(x)))
        for z in range(fz - 1, fz + 2):
            t.set(x, FL, z, 'coarse_dirt' if (x + z) % 3 else 'red_sand')
    # the rock's tops: grit and a few dry tufts; red sand fallen at its foot
    for (x, z), top in top_of.items():
        if (x, top - 1, z) in rock and (x, top, z) not in lip:
            t.set(x, top - 1, z, 'coarse_dirt' if wob.at(x, 9, z) > 0.55 else 'red_sand')
            if rng.random() < 0.06:
                t.set(x, top, z, S('dead_bush'))
    for x in range(4, 31):
        fz = int(math.floor(face_z(x)))
        z = fz - 1
        if (x, F, z) not in alcove and G.free(t, x, F, z) and rng.random() < 0.55:
            t.set(x, F, z, G.slab('smooth_red_sandstone_slab') if rng.random() < 0.6 else S('red_sand'))

    # ------------------------------------------------------------ the scrap wall: a crescent with one way in
    scrap = G.mix(SEED + 5, G.SCRAP, 2.0)
    wall_cells = []
    for x in range(3, 32):
        zb = 7.5 - 4.0 * (1 - ((x - CX) / 15.0) ** 2)
        z = int(round(zb))
        if 14 <= x <= 20:
            continue
        wall_cells.append((x, z))
    for (x, z) in wall_cells:
        y = g.feet(x, z)
        hgt = 2 + (1 if (x * 7 + z) % 5 == 0 else 0)
        for k in range(hgt):
            t.set(x, y + k, z, scrap(x, y + k, z))
        cap = rng.random()
        if cap < 0.45:
            t.set(x, y + hgt, z, G.slab('or:durasteel_plating_slab'))
        elif cap < 0.6:
            t.set(x, y + hgt, z, S('iron_bars'))
        # a second layer behind it here and there: crates and drums the crew piled up
        if rng.random() < 0.35 and G.free(t, x, y, z + 1):
            t.set(x, y, z + 1, G.scatter_pick(rng, [('or:cargo_crate', 3), (G.barrel('up'), 2), ('or:rusted_plating', 2)]))
    # the gap's posts and the kajidic's mark on them
    for x in (13, 21):
        y = g.feet(x, 4)
        for k in range(3):
            t.set(x, y + k, 4, 'or:durasteel_beam[axis=y]')
    G.claim_pole(t, 12, g.feet(12, 2), 2, height=6, cloth_side='east', banner_side='north')
    # a wrecked swoop shoved into the wall's west arm
    sw = [(5, 0, 6, 'or:wreck_plating_scorched'), (6, 0, 6, 'or:wreck_plating'), (7, 0, 6, G.stair('or:durasteel_plating_stairs', 'east')),
          (5, 1, 6, G.slab('or:durasteel_plating_slab')), (4, 0, 6, 'or:durasteel_vent')]
    for (x, dy, z, s) in sw:
        t.set(x, g.feet(x, z) + dy, z, s)

    # ------------------------------------------------------------ paths: trodden dirt from the gap to the fire,
    # the tents and the alcove
    trod = G.mix(SEED + 6, [('coarse_dirt', 4), ('packed_mud', 1), ('red_sand', 2)], 2.0)
    pts = [(17, 3), (17, 14), (22, 24), (17, 14), (8, 16), (17, 14), (26, 11)]
    for i in range(0, len(pts) - 1):
        (ax, az), (bx, bz) = pts[i], pts[i + 1]
        n = max(abs(bx - ax), abs(bz - az))
        for k in range(n + 1):
            x = int(round(ax + (bx - ax) * k / float(n)))
            z = int(round(az + (bz - az) * k / float(n)))
            for (dx, dz) in ((0, 0), (1, 0), (0, 1)):
                y = g.feet(x + dx, z + dz) - 1
                if G.free(t, x + dx, y + 1, z + dz) and t.get(x + dx, y, z + dz) is not None:
                    t.set(x + dx, y, z + dz, trod(x + dx, y, z + dz))

    # ------------------------------------------------------------ the fire pit and its benches
    fx, fz = CX, 14
    fy = g.feet(fx, fz)
    t.set(fx, fy, fz, G.campfire(True))
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        t.set(fx + dx, fy, fz + dz, 'or:scorch_mark')
    for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        t.set(fx + dx, fy - 1, fz + dz, 'cut_red_sandstone' if (dx + dz) % 2 else 'red_sandstone')
    # a spit over it: two posts and a bar
    for dx in (-2, 2):
        t.set(fx + dx, fy, fz, S('iron_bars'))
        t.set(fx + dx, fy + 1, fz, S('iron_bars'))
    t.set(fx - 1, fy + 2, fz, G.chain('x'))
    t.set(fx, fy + 2, fz, G.chain('x'))
    t.set(fx + 1, fy + 2, fz, G.chain('x'))
    # log benches (stripped trunks hauled from who knows where: wreck beams)
    for x in range(fx - 2, fx + 3):
        t.set(x, fy, fz + 3, 'or:durasteel_beam[axis=x]' if x != fx else G.slab('or:durasteel_plating_slab'))
    for z in range(fz - 1, fz + 2):
        t.set(fx - 4, fy, z, G.log('stripped_spruce_log', 'z'))
        t.set(fx + 4, fy, z, G.stair('spruce_stairs', 'west'))
    t.set(fx + 3, fy, fz - 3, S('cauldron'))
    t.set(fx - 3, fy, fz - 3, G.pot('south', cracked=True))

    # ------------------------------------------------------------ the debtor's corner in the alcove
    az = int(math.floor(face_z(21)))
    ay = F
    # his bed, his lamp, his box; a canvas hung across the alcove's mouth for shade
    G.bedroll(t, 19, ay, az, 'x', 'blue')
    t.chest(22, ay, az, 'north')
    t.anchor('STRONGBOX', 22, ay, az)
    t.set(22, ay - 1, az, 'or:durasteel_plating')
    t.set(23, ay, az, G.chain('x'))
    t.set(21, ay, az - 1, G.lantern(False))
    t.set(18, ay, az, G.barrel('up'))
    t.set(18, ay + 1, az, G.candle(1, False))
    t.set(23, ay, az + 1, 'or:cargo_crate')
    for x in range(17, 24):
        if G.free(t, x, ay + 3, az - 2):
            t.set(x, ay + 3, az - 2, 'or:canvas_navy' if x % 3 else G.slab('or:canvas_slab', 'top'))
    for (x, z) in ((17, az - 2), (23, az - 2)):
        for y in range(ay, ay + 3):
            t.set(x, y, z, S('spruce_fence'))
    # his skiff's console, carried in: the one thing he would not sell
    t.set(15, ay, az, S('or:console', facing='north'))
    t.set(15, ay + 1, az, S('or:holo_screen', facing='north'))
    t.set(14, ay, az - 1, G.stair('or:durasteel_plating_stairs', 'east'))

    # ------------------------------------------------------------ the crew's lean-tos (west)
    def lean_to(x0, z0, x1, z1, open_side, cloth):
        """Plates propped on a beam: the back wall low, the roof sloping up to the open side."""
        y = g.feet((x0 + x1) // 2, (z0 + z1) // 2)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                back = (open_side == 'east' and x == x0) or (open_side == 'west' and x == x1)
                if back:
                    t.set(x, y, z, scrap(x, y, z))
                    t.set(x, y + 1, z, G.stair('or:durasteel_plating_stairs', 'east' if open_side == 'west' else 'west'))
                else:
                    t.set(x, y + 2, z, cloth if (x + z) % 3 else G.slab('or:canvas_slab', 'top'))
        ox = x1 if open_side == 'east' else x0
        for z in (z0, z1):
            for k in range(2):
                t.set(ox, y + k, z, S('spruce_fence'))
        return y
    y1 = lean_to(4, 11, 7, 15, 'east', 'or:canvas')
    G.bedroll(t, 5, y1, 12, 'x', 'brown')
    G.bedroll(t, 5, y1, 14, 'x', 'gray')
    t.set(6, y1, 13, G.barrel('up'))
    y2 = lean_to(4, 17, 7, 21, 'east', 'or:canvas_navy')
    G.bedroll(t, 5, y2, 18, 'x', 'brown')
    t.set(5, y2, 20, 'or:cargo_crate')
    t.set(6, y2, 20, S('or:debris'))
    # their tools on a rack: a frame of bars with rods and a chain
    rx, rz = 10, 21
    ry = g.feet(rx, rz)
    for x in (rx, rx + 2):
        for k in range(3):
            t.set(x, ry + k, rz, 'or:durasteel_beam[axis=y]')
    for x in range(rx, rx + 3):
        t.set(x, ry + 3, rz, 'or:durasteel_beam[axis=x]')
    t.set(rx + 1, ry + 2, rz, G.chain('y'))
    t.set(rx + 1, ry + 1, rz, G.rod('down'))
    t.set(rx + 1, ry, rz, 'grindstone[face=floor,facing=north]')
    # empty water barrels and a condenser stripped to its shaft
    for (x, z) in ((10, 11), (11, 11), (10, 12)):
        t.set(x, g.feet(x, z), z, G.barrel('up', open_=True))
    t.set(12, g.feet(12, 11), 11, S('cauldron'))
    cx_, cz_ = 12, 24
    cy_ = g.feet(cx_, cz_)
    t.set(cx_, cy_, cz_, 'or:durasteel_plating')
    for k in range(1, 4):
        t.set(cx_, cy_ + k, cz_, 'or:durasteel_pipes[axis=y]')
    t.set(cx_, cy_ + 4, cz_, G.rod('up'))
    t.set(cx_ + 1, cy_, cz_, S('cauldron'))
    t.set(cx_ - 1, cy_, cz_ + 1, G.slab('or:durasteel_plating_slab'))

    # ------------------------------------------------------------ the workshop (east): a landspeeder stripped
    # for parts on blocks, its panels sorted in stacks
    lx, lz = 24, 9
    ly = g.feet(lx, lz)
    for x in range(lx, lx + 6):
        for z in range(lz, lz + 3):
            edge_x = x in (lx, lx + 5)
            if (x in (lx + 1, lx + 4)) and z in (lz, lz + 2):
                t.set(x, ly, z, 'or:cargo_crate')                       # the blocks it stands on
            if not edge_x or z == lz + 1:
                t.set(x, ly + 1, z, G.slab('or:durasteel_plating_slab', 'top') if (x + z) % 4 else 'or:wreck_plating')
    t.set(lx + 4, ly + 2, lz + 1, G.stair('or:durasteel_plating_stairs', 'west'))   # its seat
    t.set(lx + 5, ly + 2, lz + 1, S('or:console', facing='west'))
    t.set(lx, ly + 2, lz + 1, 'or:durasteel_vent')                       # the engine, half out
    t.set(lx - 1, ly + 1, lz + 1, G.chain('x'))
    t.set(lx - 1, ly, lz + 1, 'or:durasteel_pipes[axis=x]')
    for (x, z, hgt) in ((lx + 1, lz + 4, 3), (lx + 2, lz + 4, 2), (lx + 4, lz + 4, 1), (lx + 6, lz + 1, 2)):
        for k in range(hgt):
            t.set(x, g.feet(x, z) + k, z, 'or:rusted_plating' if k % 2 else 'or:durasteel_plating')
    t.set(lx + 3, g.feet(lx + 3, lz + 4), lz + 4, 'anvil[facing=east]')
    t.set(lx + 5, g.feet(lx + 5, lz + 4), lz + 4, S('smithing_table'))
    G.awning(t, lx, lz - 1, lx + 5, lz + 3, ly + 4, cloth='or:canvas_slab', stripes=None, posts='spruce_fence')
    t.set(lx + 2, ly + 4, lz + 1, 'or:canvas')                  # a whole cloth in the awning to hang the lamp from
    t.set(lx + 2, ly + 3, lz + 1, G.lantern(True))

    # the debtor's skiff: it brought him here and will not fly again; the crew have had its plating
    kx, kz = 24, 16
    ky = g.feet(kx + 3, kz + 2)
    G.skiff(t, kx, ky, kz, length=7, width=4, along='x', wreck=True)
    for (x, z) in ((kx + 2, kz + 1), (kx + 3, kz + 2), (kx + 4, kz + 1), (kx + 5, kz + 2)):
        t.set(x, ky + 1, z, AIR)                                         # deck plates lifted
    t.set(kx + 3, ky + 1, kz + 1, 'or:durasteel_beam[axis=x]')
    t.set(kx + 4, ky + 1, kz + 2, 'or:durasteel_beam[axis=x]')
    # a drying line by the lean-tos: a cord between two posts and the crew's rags on it
    for z in (23, 27):
        for k in range(3):
            t.set(4, g.feet(4, z) + k, z, S('spruce_fence'))
    ly2 = g.feet(4, 23) + 2
    for z in range(24, 27):
        t.set(4, ly2, z, G.chain('z'))
    t.set(4, ly2 - 1, 24, 'or:canvas')
    t.set(4, ly2 - 1, 26, 'or:canvas_navy')
    # a firing step of sandbags inside the gap
    for x in (14, 20):
        y = g.feet(x, 6)
        t.set(x, y, 6, 'or:canvas')
        t.set(x, y + 1, 6, G.slab('or:canvas_slab'))

    # ------------------------------------------------------------ scatter: grit, dry brush, a bone or two
    for (x, z) in ((20, 18), (13, 9), (25, 17), (9, 8), (28, 20), (15, 20)):
        y = g.feet(x, z)
        if G.free(t, x, y, z):
            t.set(x, y, z, 'or:debris')
    t.set(29, g.feet(29, 24), 24, 'bone_block[axis=x]')
    t.set(30, g.feet(30, 24), 24, G.slab('smooth_red_sandstone_slab'))
    g.scatter(0.03, avoid={(x, z) for x in range(3, 32) for z in range(2, 30)})

    # ------------------------------------------------------------ anchors
    mod.spot(t, 'NPC_DEBTOR', fx, g.feet(fx, fz - 3), fz - 3)
    mod.spot(t, 'HEART', fx, g.feet(fx, fz + 2), fz + 2)
    mod.spot(t, 'SPAWNER', 9, g.feet(9, 16), 16)
    return t, [('strongbox front', (22, F, az - 1)), ('debtor bed', (20, F, az - 1))]


def main(preview_dir=None):
    t, extra = build()
    G.save(t, ID, preview_dir)
    meta.write(ID, buried=BURIED, terrain='low', biomes='#oldrepublic:has_structure/ghessar/debtor_camp',
               max_slope=5, clearance=8, support_depth=6, ramp_rings=2, clear_above=8)
    W.biome_tag('has_structure/ghessar/debtor_camp', ['oldrepublic:ghessar_canyons'])
    G.report_walk(t, ID, ['HEART', 'NPC_DEBTOR', 'SPAWNER'], extra=extra)
    return t


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else None)
