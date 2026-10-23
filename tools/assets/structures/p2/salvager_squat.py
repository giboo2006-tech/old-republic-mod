"""ghessar/salvager_squat (worldgen, Ghessar's dunes and canyons): a scavengers' shanty town of scrap.

The scavengers who strip the wrecks live in what they did not sell: shacks of mismatched plate and crate
sides under patched awnings, a market square where the gang lays out the week's take, a water tank they guard
harder than anything, a scrap totem with a skull on it so nobody mistakes whose place this is. The gang is home
(a scavengers spawner in the square); the boss keeps the cut in his shack (the chest).

Two variants: `market`, an open ring of shacks round the market square with a lookout of stacked containers;
`walled`, the same people behind a wall of hull plates stood on end, a cargo container for a gate, the market
along the lane from the gate to the boss's shack, which is a ship's cockpit. 45 x 45, the feet layer at y 2.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gkit as G  # noqa: E402
from gkit import S, AIR, B, mod, W, Noise, Template  # noqa: E402
from structlib import jigsaw  # noqa: E402

NAME = 'ghessar/salvager_squat'
SX = SZ = 45
SY = 24
BURIED = 2
F = BURIED
FL = F - 1
SEED = 960345
LOOT = 'chests/ghessar/salvager_squat'
TOP_MIX = [('sand', 5), ('coarse_dirt', 3), ('packed_mud', 1), ('gravel', 1)]
PANELS = [('or:rusted_plating', 5), ('or:wreck_plating', 4), ('or:durasteel_plating', 2), ('or:cargo_crate', 2),
          ('or:wreck_plating_scorched', 2), ('waxed_weathered_cut_copper', 1), ('or:cargo_crate_imperial', 1),
          ('or:imperial_plating', 1), ('or:republic_plating', 1)]
CLOTHS = ['or:canvas', 'or:canvas_navy', 'or:canvas_crimson']


def shack(t, x0, z0, w, d, door, seed, roof='plate', furnish=True, lit=True):
    """A shack of scrap: walls of mismatched panels (2-wide pieces), 4 high, a 2 x 3 door on `door`'s side with an
    awning over it, a roof of plate slabs or patched canvas, a bedroll, a crate, a lamp. Returns its interior box."""
    rng = t.rng
    x1, z1 = x0 + w - 1, z0 + d - 1
    pick = {}

    def panel(x, z):
        k = (x // 2, z // 2)
        if k not in pick:
            pick[k] = G.scatter_pick(rng, PANELS)
        return pick[k]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set(x, FL, z, 'or:durasteel_deck' if not edge and (x + z) % 4 else 'packed_mud')
            for y in range(F, F + 4):
                if edge:
                    t.set(x, y, z, panel(x, z) if not (y == F + 1 and rng.random() < 0.08) else S('iron_bars'))
                else:
                    t.set(x, y, z, AIR)
            if roof == 'plate':
                t.set(x, F + 4, z, G.slab(G.scatter_pick(rng, [('or:durasteel_plating_slab', 5), ('or:imperial_plating_slab', 1),
                                                                ('or:republic_plating_slab', 1)])))
            else:
                t.set(x, F + 4, z, G.slab('or:canvas_slab') if rng.random() < 0.8 else rng.choice(CLOTHS))
    # the door: 2 wide, 3 high, in the middle of its side; an awning out over it
    if door in ('north', 'south'):
        dz = z0 if door == 'north' else z1
        dx = (x0 + x1) // 2
        cells = [(dx, dz), (dx + 1, dz)] if dx + 1 < x1 else [(dx - 1, dz), (dx, dz)]
        out = (0, -1) if door == 'north' else (0, 1)
    else:
        dx = x0 if door == 'west' else x1
        dz = (z0 + z1) // 2
        cells = [(dx, dz), (dx, dz + 1)] if dz + 1 < z1 else [(dx, dz - 1), (dx, dz)]
        out = (-1, 0) if door == 'west' else (1, 0)
    for (x, z) in cells:
        for y in range(F, F + 3):
            t.set(x, y, z, AIR)
        t.set(x, F + 3, z, 'or:durasteel_beam[axis=%s]' % ('x' if door in ('north', 'south') else 'z'))
        t.set(x + out[0], FL, z + out[1], 'packed_mud')
    cloth = rng.choice(CLOTHS)
    for (x, z) in cells + [(cells[0][0] - abs(out[1]), cells[0][1] - abs(out[0])), (cells[-1][0] + abs(out[1]), cells[-1][1] + abs(out[0]))]:
        for k in (1, 2):
            ax, az = x + out[0] * k, z + out[1] * k
            if t.inside(ax, F + 3, az) and G.free(t, ax, F + 3, az):
                t.set(ax, F + 3, az, G.slab('or:canvas_slab', 'top') if k == 1 else cloth)
    box = (x0 + 1, F, z0 + 1, x1 - 1, F + 3, z1 - 1)
    if furnish:
        inner = [(x, z) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1)
                 if all(abs(x - cx) + abs(z - cz) > 1 for (cx, cz) in cells)]
        rng.shuffle(inner)
        inner.sort(key=lambda c: -(min(c[0] - x0, x1 - c[0], c[1] - z0, z1 - c[1]) == 1))
        things = [lambda x, z: G.bedroll(t, x, F, z, 'x' if x + 1 < x1 and (x + 1, z) not in [c for c in cells] else 'z', rng.choice(['brown', 'gray', 'red'])),
                  lambda x, z: t.set(x, F, z, G.scatter_pick(rng, [('or:cargo_crate', 3), (G.barrel('up'), 2)])),
                  lambda x, z: t.set(x, F, z, G.scatter_pick(rng, [('or:debris', 2), (G.pot('north'), 1), ('or:wreck_salvage', 1)]))]
        for f, (x, z) in zip(things, inner):
            if G.free(t, x, F, z) and G.free(t, x + 1, F, z):
                f(x, z)
        if lit:
            t.set((x0 + x1) // 2, F + 3, (z0 + z1) // 2, G.lantern(True))
    return box


def stall(t, x0, z0, x1, z1, rng, cloth, bare=False):
    """A market stall: a counter of top slabs on crates, a patched awning on poles, the take laid out."""
    if bare:
        for (px, pz) in ((x0, z0), (x1, z1)):
            for y in range(F, F + 2 + (rng.random() < 0.5)):
                t.set(px, y, pz, S('iron_bars'))
        return
    G.awning(t, x0, z0, x1, z1, F + 3, cloth='or:canvas_slab', stripes=cloth, posts='iron_bars', post_from=F)
    goods = [('or:wreck_salvage', 3), ('or:debris', 2), (S('or:console', facing='north'), 1), ('or:durasteel_vent', 1),
             (G.pot('north'), 1), (S('candle', candles=2, lit=False), 1), ('or:cargo_crate', 1), (G.barrel('up'), 1)]
    for x in range(x0 + 1, x1):
        t.set(x, F, z0 + 1, 'or:cargo_crate')
        if rng.random() < 0.7:
            t.set(x, F + 1, z0 + 1, G.scatter_pick(rng, goods))
    for x in range(x0 + 1, x1):
        if rng.random() < 0.6 and G.free(t, x, F, z1 - 1):
            t.set(x, F, z1 - 1, G.scatter_pick(rng, goods))


def totem(t, x, z, y, rng):
    """The gang's sign: a pole of beams with a hull plate across it, chains, a skull on top, a red rag."""
    for k in range(0, 8):
        t.set(x, y + k, z, 'or:durasteel_beam[axis=y]')
    for dx in (-2, -1, 1, 2):
        t.set(x + dx, y + 5, z, 'or:rusted_plating' if abs(dx) == 1 else G.stair('or:durasteel_plating_stairs', 'east' if dx < 0 else 'west', 'top'))
    for dx in (-2, 2):
        for k in (3, 4):
            t.set(x + dx, y + k, z, G.chain('y'))
    t.set(x, y + 8, z, S('skeleton_skull', rotation=8))
    G.drape(t, x, y + 6, z + 1, 2, 'or:canvas_crimson')
    t.set(x + 1, y, z, 'or:scorch_mark')
    t.set(x - 1, y, z + 1, S('bone_block', axis='y'))


def water_tank(t, x0, z0, rng):
    """The gang's water: a tank of plates on legs, a tap over a jar, bars round it and a chain on the tap."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            t.set(x, F, z, 'or:durasteel_beam[axis=y]' if (x in (x0, x0 + 2) and z in (z0, z0 + 2)) else AIR)
            for y in (F + 1, F + 2, F + 3):
                edge = x in (x0, x0 + 2) or z in (z0, z0 + 2)
                t.set(x, y, z, ('waxed_weathered_cut_copper' if (x + y) % 2 else 'or:rusted_plating') if edge else S('water'))
            t.set(x, F + 4, z, G.slab('or:durasteel_plating_slab'))
    t.set(x0 + 1, F + 1, z0 + 1, 'or:rusted_plating')
    t.set(x0 + 1, F, z0 + 3, S('water_cauldron', level=2))
    t.set(x0 + 1, F + 1, z0 + 3, S('tripwire_hook', facing='south', attached=False, powered=False))
    for (x, z) in ((x0 - 1, z0 + 3), (x0 + 3, z0 + 3), (x0 - 1, z0 + 4), (x0, z0 + 4), (x0 + 2, z0 + 4), (x0 + 3, z0 + 4)):
        t.set(x, F, z, S('iron_bars'))
        t.set(x, F + 1, z, S('iron_bars'))


def build(variant):
    walled = variant == 'walled'
    seed = SEED + (50 if walled else 0)
    t = Template(SX, SY, SZ, seed=seed, name=NAME + '/' + variant)
    rng = t.rng
    g = G.Ground(t, F, 'dunes', seed=seed)
    g.noise(amp=2, scale=8.0)
    for x in range(5, 40):
        for z in range(5, 40):
            g.h[x][z] = 2 * F
    if walled:
        g.drift({(5, z) for z in range(8, 37)}, 'west', 6, 5, seed=1)
    else:
        g.mound(4, 38, 8, 7, 8)
    g.taper(slope=2)
    g.feather()
    g.apply(clear_above=4, top=TOP_MIX)
    regions = []
    spots = {}
    if not walled:
        # ------------------------------------------------------------ an open ring of shacks round the market square
        shacks = [((8, 8, 6, 5), 'south'), ((16, 6, 5, 5), 'south'), ((23, 6, 6, 5), 'south'),
                  ((31, 9, 6, 6), 'west'), ((32, 18, 5, 6), 'west'), ((31, 27, 6, 5), 'west'),
                  ((22, 33, 6, 5), 'north'), ((8, 30, 6, 6), 'east'), ((7, 19, 5, 6), 'east')]
        for k, ((x0, z0, w, d), door) in enumerate(shacks):
            box = shack(t, x0, z0, w, d, door, seed + k, roof='canvas' if k % 3 == 1 else 'plate')
            regions.append(box)
        # the boss's shack: the big one on the east, the chest at its back wall
        bx0, bz0 = 31, 9
        t.chest(bx0 + 4, F, bz0 + 1, 'west', LOOT)
        spots['chest front'] = (bx0 + 3, F, bz0 + 1)
        t.set(bx0 + 4, F, bz0 + 4, S('or:holo_screen', facing='west'))
        # the market square and its stalls
        sq = [((15, 15, 18, 17), 'or:canvas_navy'), ((23, 15, 26, 17), 'or:canvas_crimson'), ((15, 25, 18, 27), 'or:canvas'),
              ((23, 26, 26, 28), 'or:canvas_navy')]
        for (x0, z0, x1, z1), cloth in sq:
            stall(t, x0, z0, x1, z1, rng, cloth)
        totem(t, 21, 21, F, rng)
        water_tank(t, 11, 23, rng)
        mod.spawner(t, 20, F, 23, 'scavengers', count=3)
        # the lookout: two containers stacked, a ladder, a platform with a rail and a lamp
        lx0, lz0 = 37, 34
        for (yy, along) in ((F, 'x'), (F + 4, 'z')):
            for x in range(lx0 - 4, lx0 + 1):
                for z in range(lz0 - 2, lz0 + 1):
                    for y in range(yy, yy + 3):
                        edge = x in (lx0 - 4, lx0) or z in (lz0 - 2, lz0)
                        t.set(x, y, z, ('or:cargo_crate_imperial' if yy > F else 'or:cargo_crate') if edge else AIR)
                    t.set(x, yy + 3, z, 'or:rusted_plating')
        for x in range(lx0 - 4, lx0 + 1):
            for z in range(lz0 - 2, lz0 + 1):
                if x in (lx0 - 4, lx0) or z in (lz0 - 2, lz0):
                    t.set(x, F + 8, z, S('iron_bars'))
        for y in range(F, F + 8):
            t.set(lx0 - 2, y, lz0 - 3, G.ladder('north'))
        t.set(lx0 - 2, F + 8, lz0 - 2, AIR)
        t.set(lx0 - 3, F + 8, lz0 - 1, G.lantern(False))
        t.set(lx0 - 1, F + 8, lz0 - 1, 'or:durasteel_vent')
        spots['square'] = (21, F, 18)
        spots['ladder foot'] = (lx0 - 2, F, lz0 - 4)
    else:
        # ------------------------------------------------------------ a wall of hull plates, a container gate, a lane
        wx0, wz0, wx1, wz1 = 6, 6, 38, 38
        jag = Noise(seed + 3, 2.0)
        for x in range(wx0, wx1 + 1):
            for z in range(wz0, wz1 + 1):
                if not (x in (wx0, wx1) or z in (wz0, wz1)):
                    continue
                if z == wz1 and 19 <= x <= 25:
                    continue                                      # the gate gap, filled by the container
                hgt = 4 + int(jag.at(x, 0, z) * 3)
                k = (x // 3 + z // 3) % 5
                mat = ['or:rusted_plating', 'or:wreck_plating', 'or:republic_plating', 'or:wreck_plating_scorched', 'or:imperial_plating'][k]
                for y in range(FL, F + hgt):
                    t.set(x, y, z, mat)
                if (x + z) % 6 == 0:
                    t.set(x, F + hgt, z, G.rod('up', 'exposed_lightning_rod'))
        # the gate: a cargo container through the wall, both ends open
        for x in range(19, 26):
            for z in range(wz1 - 3, wz1 + 4):
                if z > SZ - 1:
                    continue
                edge = x in (19, 25)
                for y in range(F, F + 4):
                    t.set(x, y, z, ('or:cargo_crate' if (z + y) % 4 else 'or:cargo_crate_republic') if edge else AIR)
                t.set(x, F + 4, z, 'or:rusted_plating')
                t.set(x, FL, z, 'or:durasteel_deck')
        for x in (20, 24):
            G.drape(t, x, F + 3, wz1 + 4, 2, 'or:canvas_crimson')
        # the lane north from the gate, shacks either side, the boss's cockpit at its head
        shacks = [((8, 26, 6, 6), 'east'), ((8, 18, 6, 6), 'east'), ((8, 9, 7, 6), 'east'),
                  ((29, 27, 7, 6), 'west'), ((30, 19, 6, 6), 'west'), ((29, 9, 7, 6), 'west')]
        for k, ((x0, z0, w, d), door) in enumerate(shacks):
            regions.append(shack(t, x0, z0, w, d, door, seed + k, roof='canvas' if k % 2 else 'plate'))
        sq = [((16, 27, 18, 29), 'or:canvas_navy'), ((26, 26, 28, 28), 'or:canvas_crimson'), ((16, 18, 18, 20), 'or:canvas'),
              ((26, 17, 28, 19), 'or:canvas_navy')]
        for (x0, z0, x1, z1), cloth in sq:
            stall(t, x0, z0, x1, z1, rng, cloth)
        # the boss's office: a cockpit cut from a hauler, laid at the lane's head, the chest inside
        import freighter_graveyard as FGY
        ck = [(0, 0, 0, 3.6, 3.2, 2.2), (7, 0, 0, 3.6, 3.2, 2.2), (10, -0.4, 0, 2.4, 2.2, 2.0), (11.5, -0.6, 0, 1.0, 1.2, 2.0)]
        pc = G.PosedHull(ck, (16, F + 1, 11), yaw=0.0, roll=0.0, open_ends=('x0',))
        s2, i2, l2 = pc.voxels(t)
        FGY.build_hull(t, pc, s2, i2, l2, FGY.livery_skin(seed + 9, 'hauler'), sand=lambda x, z: F, seed=seed + 9,
                       cut_soot=False)
        for c in s2:
            lx, ly, lz = l2[c]
            if lx > 8.5 and ly > 0.3 and G.solid_at(t, *c):
                t.set(*c, 'black_stained_glass')
        for c in i2:
            if c[1] == FL:
                t.set(*c, 'or:durasteel_deck')
        t.chest(19, F, 10, 'south', LOOT)
        spots['chest front'] = (19, F, 11)
        t.set(22, F, 12, S('or:console', facing='west'))
        ctop = max(c[1] for c in i2 if c[0] == 20 and c[2] == 11)
        t.set(20, ctop, 11, G.lantern(True))
        regions.append(((0, 0, 0, SX - 1, SY - 1, SZ - 1), {c for c in i2 if c[1] == F}))
        totem(t, 22, 23, F, rng)
        water_tank(t, 26, 11, rng)
        mod.spawner(t, 21, F, 20, 'scavengers', count=3)
        spots['lane'] = (22, F, 30)
    # the squat's junk: heaps, a stolen condenser, a wrecked skiff, scatter
    hx, hz = (38, 40) if not walled else (41, 41)
    G.scrap_heap(t, hx, g.feet(hx, hz), hz, 2.4, 2.0, seed + 41)
    G.condenser(t, 4 if walled else 27, g.feet(4, 4) if walled else F, 4 if walled else 22, height=5)
    if not walled:
        G.skiff(t, 2, g.feet(4, 14), 13, length=7, width=4, along='z', wreck=True)
    for (x, z) in ((13, 20), (28, 14), (28, 31)) if not walled else ((15, 23), (27, 23), (22, 33)):
        G.lamp_post(t, x, F, z, height=3)
    g.scatter(0.01)
    return t, regions, spots


def check_walk(t, variant, spots):
    return G.report_walk(t, NAME + '/' + variant, [], extra=list(spots.items()))


VARIANTS = ('market', 'walled')


def worldgen():
    adapted = dict(yard_stand=F, clear_from=SY, clear_inset=0, support_depth=6, ramp_rings=2)
    W.pool(NAME + '/start', [W.element(NAME + '/' + v, adapted=adapted) for v in VARIANTS])
    W.structure(NAME, start_pool=NAME + '/start', biome_tag='has_structure/' + NAME, size=1, max_distance=48,
                start_height=1 - BURIED)
    W.structure_set(NAME, [(NAME, 1)], spacing=40, separation=14, salt=960304, placement='clear_of_origin',
                    min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:ghessar_dunes', 'oldrepublic:ghessar_canyons'])
    G.write_loot(LOOT, (2, 4), [
        ('or:durasteel_plate', 6, 1, 2), ('copper_ingot', 5, 1, 3), ('iron_nugget', 5, 2, 6), ('redstone', 3, 1, 3),
        ('bread', 4, 1, 2), ('string', 3, 1, 3), ('or:credit_chit', 4, 1, 3), ('or:medpac', 2, 1, 1)])


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
