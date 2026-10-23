#!/usr/bin/env python3
"""mission/ambush_rocks (docs/MILESTONE_6.md 5.4): the hunters' rocks of Chapter Four's two ambushes.

PlanetSites.placeAmbush sets this on a planet surface (never the home world) on a ring 24 to 32 blocks
around the player, entrance toward them, and TheHunt drops the Mandalorian hunters in on jet packs around
HEART (Hunters.spawnAmbush: 4 blocks out from it, at the height map, then falling from 14 up). So HEART
stands in the middle of an open rock pan that nothing overhangs for 5 blocks around.

"The Hunters' Rocks": a broken ring of weathered tors around that pan, open to the north. The hunters set
it up as a funnel: the side gaps are mined (iron pressure plates half sunk in the gravel), a trip wire is
strung across the way in, and a sniper's perch with a canvas sun net and a tracker sits on the tallest
rock. In the lee of the leaning stone they kept a cold camp: bedrolls, crates, a fire never lit. Jet pack
burns scar the pan and the rock tops. Neutral rock only (stone, andesite, tuff, cobble, gravel), so it
reads as an outcrop on all four planets: Selvaren's moss, Varkhast's red sand, Ghessar's salt flats and
Tyrvann's mud.

23 x 12 x 23, buried 1, any ground; one template, no variant (the brief).

Usage: python3 tools/assets/structures/qm/ambush_rocks.py [--preview <folder>]
"""
import math
import random

import qmkit as K
from qmkit import S, AIR, Template, B, Noise, mod

SIZE = (23, 12, 23)
BURIED = 1
ANCHORS = ['HEART']
META = dict(buried=BURIED, terrain='any', max_slope=3, clearance=8, support_depth=8, ramp_rings=2, clear_above=6)

F = BURIED          # the feet layer
G = F - 1           # the ground layer
HX, HZ = 11, 11     # HEART: the middle of the pan
CLEAR = 5.0         # nothing stands above the ground this close to HEART (the hunters land 4 out)

ROCK = [(K.STONE, 4), (K.ANDESITE, 3), (K.TUFF, 2), (K.COBBLE, 1)]
SLAB_OF = {K.STONE: K.slab('stone_slab'), K.ANDESITE: K.slab('andesite_slab'), K.TUFF: K.slab('tuff_slab'),
           K.COBBLE: K.slab('cobblestone_slab')}
PAN = [(K.GRAVEL, 4), (K.ANDESITE, 2), (K.TUFF, 2), (K.STONE, 1), (K.COARSE, 1)]


def near_heart(x, z, r=CLEAR):
    return (x - HX) ** 2 + (z - HZ) ** 2 < r * r


def in_gap(x, z):
    """The way in: a five-wide lane from the north edge to the pan."""
    return 9 <= x <= 13 and z <= 6


class Rocks:
    """Boulders as noisy ellipsoids, unioned, in banded rock; slabs round off their tops."""

    def __init__(self, t, seed):
        self.t = t
        self.field = {}   # (x, y, z) -> lowest ellipsoid value seen
        self.mix = K.Mix(ROCK, seed, 2.4, 0.3)
        self.seed = seed

    def boulder(self, cx, cy, cz, rx, ry, rz, seed, rough=0.4, top=None, lean=(0.0, 0.0), ph=2.7, pv=3.6):
        """A rounded block of rock: a superellipsoid, boxy sides (ph) and flattish top and bottom (pv),
        the way weathered tors split. lean: the centre drifts this far in x and z per block up."""
        n = Noise(self.seed * 31 + seed, 1.9)
        for y in range(0, int(cy + ry + 2)):
            if top is not None and y > top:
                continue
            ox, oz = lean[0] * (y - cy), lean[1] * (y - cy)
            for x in range(int(cx - rx - 3 + min(0, ox)), int(cx + rx + 4 + max(0, ox))):
                for z in range(int(cz - rz - 3 + min(0, oz)), int(cz + rz + 4 + max(0, oz))):
                    if not self.t.inside(x, y, z):
                        continue
                    e = abs((x + 0.5 - cx - ox) / rx) ** ph + abs((y + 0.5 - cy) / ry) ** pv + \
                        abs((z + 0.5 - cz - oz) / rz) ** ph
                    e += (n.at(x, y, z) - 0.5) * rough
                    k = (x, y, z)
                    if e < self.field.get(k, 9.0):
                        self.field[k] = e

    def lay(self):
        t = self.t
        for (x, y, z), e in sorted(self.field.items()):
            if e > 1.0:
                continue
            if y >= F and (near_heart(x, z) or in_gap(x, z)):
                continue
            # strata: the bands run level, a little wavy
            t.set(x, y, z, self.mix.at(x * 0.45, y * 1.7, z * 0.45))
        # round the tops: a slab where the field nearly reaches over a rock
        for (x, y, z), e in sorted(self.field.items()):
            if e <= 1.0 or e > 1.32 or y < F:
                continue
            if near_heart(x, z) or in_gap(x, z) or not t.is_air(x, y, z):
                continue
            below = t.get(x, y - 1, z)
            if below in SLAB_OF:
                t.set(x, y, z, SLAB_OF[below])

    def rock(self, x, y, z):
        return self.t.get(x, y, z) in SLAB_OF


def top_of(t, x, z):
    """The highest rock cell of a column (or -1)."""
    for y in range(t.size[1] - 1, -1, -1):
        if t.get(x, y, z) in SLAB_OF:
            return y
    return -1


def scree(t, rng, cells, density=0.35):
    """Loose stones at a rock's foot: slabs and the odd block, the ground under them set."""
    for (x, z) in cells:
        if near_heart(x, z, CLEAR + 0.5) or in_gap(x, z) or not t.inside(x, F, z):
            continue
        if not t.is_air(x, F, z) or t.get(x, F, z) is not None:
            continue
        if rng.random() < density:
            t.set(x, G, z, K.pick(rng, [(K.GRAVEL, 3), (K.ANDESITE, 1), (K.TUFF, 1)]))
            t.set(x, F, z, K.pick(rng, [(K.slab('cobblestone_slab'), 3), (K.slab('andesite_slab'), 2),
                                        (K.slab('tuff_slab'), 2), (K.COBBLE, 1), (K.slab('stone_slab'), 1)]))


def blaster_marks(t, rng, n):
    """Black burns (buttons) on rock faces that look into the pan: old fights and target practice."""
    faces = []
    for (x, y, z), s in sorted(t.cells.items()):
        if s not in SLAB_OF or y < F or y > F + 3:
            continue
        for d in ('north', 'south', 'east', 'west'):
            dx, dz = B.step(d)
            ax, az = x + dx, z + dz
            if not t.inside(ax, y, az) or t.get(ax, y, az) is not None:
                continue
            # the face must look toward HEART
            if (HX - x) * dx + (HZ - z) * dz <= 0:
                continue
            faces.append((ax, y, az, d))
    rng.shuffle(faces)
    for (x, y, z, d) in faces[:n]:
        t.set(x, y, z, S('polished_blackstone_button', face='wall', facing=d, powered=False))


def build():
    t = Template(*SIZE)
    rng = random.Random(9061)
    R = Rocks(t, 9061)

    # --- the tors ------------------------------------------------------------------------------------
    # A. the Perch (south-east): four stones stacked a little askew, the top one flat at y 8
    R.boulder(18.2, 1.0, 18.3, 3.9, 2.1, 3.7, 1)
    R.boulder(18.7, 3.9, 17.9, 3.2, 1.4, 3.0, 2)
    R.boulder(17.9, 6.1, 18.5, 2.8, 1.2, 2.7, 3, rough=0.3)
    R.boulder(18.3, 8.0, 18.1, 2.5, 1.0, 2.4, 4, rough=0.25, top=8)
    # B. the Anvil (north-east): a squat foot and a flat top stone that overhangs the way in
    R.boulder(19.0, 1.0, 4.9, 3.1, 2.0, 2.7, 6)
    R.boulder(17.7, 3.5, 4.4, 3.5, 0.9, 2.5, 7, rough=0.3, pv=4.0)
    # C. the east ridge: long and low, split in two
    R.boulder(21.0, 0.8, 9.0, 1.9, 1.6, 2.2, 8)
    R.boulder(20.6, 0.6, 12.0, 1.6, 1.2, 1.3, 9, ph=2.0, pv=2.0)
    # D. the Leaning Stone (south-west): a fat foot and a tall flat stone leaning out over the cold camp
    R.boulder(4.0, 0.9, 18.4, 3.2, 1.9, 3.2, 10)
    R.boulder(4.0, 5.0, 17.8, 1.2, 4.6, 2.5, 11, rough=0.25, lean=(0.42, 0.06), ph=2.4, pv=2.4)
    # E. the west tor: two stones
    R.boulder(2.6, 1.0, 9.4, 2.6, 2.0, 3.0, 12)
    R.boulder(3.1, 3.5, 9.0, 2.1, 1.0, 2.3, 13, rough=0.3)
    # F. low boulders north-west, and the two stones the trip wire is strung between
    R.boulder(3.2, 0.5, 2.8, 2.4, 1.4, 2.0, 14, ph=2.2, pv=2.4)
    R.boulder(7.2, 0.6, 2.2, 1.6, 1.6, 1.5, 15, rough=0.3, ph=2.0, pv=2.0)
    R.boulder(15.4, 0.6, 2.2, 1.6, 1.7, 1.5, 16, rough=0.3, ph=2.0, pv=2.0)
    # G. the south stone, behind the cold camp
    R.boulder(12.4, 0.6, 21.5, 2.6, 1.4, 1.5, 17)
    R.lay()

    # --- the pan and the way in ------------------------------------------------------------------------
    pan = K.Mix(PAN, 9062, 2.6, 0.4)
    K.ground_blob(t, G, 11.0, 11.5, 6.6, 7.2, pan, 9063, fray=0.3, only_open=True)
    K.trail(t, G, [(11, 0), (11, 4), (11.5, 8)], 4, pan, 9064, fray=0.5)
    # the side gaps: west (between the west tor and the leaning stone), east (the ridge and the perch)
    K.trail(t, G, [(0, 13.5), (6, 13)], 3, pan, 9065, fray=0.6)
    K.trail(t, G, [(22, 13.5), (16, 13.5)], 3, pan, 9066, fray=0.6)
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            if t.get(x, F, z) in SLAB_OF and t.get(x, G, z) is None:
                t.set(x, G, z, R.mix.at(x * 0.45, G * 1.7, z * 0.45))

    # loose stones at the foot of every tor
    feet = set()
    for (x, y, z), s in t.cells.items():
        if y == F and s in SLAB_OF:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    feet.add((x + dx, z + dz))
    scree(t, rng, sorted(feet), 0.22)

    # --- the trap: a trip wire across the way in, mines in the side gaps -----------------------------------
    wz = 2
    xw = max(x for x in range(0, HX) if R.rock(x, F, wz)) + 1      # first air east of the west stone
    xe = min(x for x in range(HX, SIZE[0]) if R.rock(x, F, wz)) - 1  # first air west of the east stone
    t.set(xw, F, wz, S('tripwire_hook', facing='east', attached=True, powered=False))
    t.set(xe, F, wz, S('tripwire_hook', facing='west', attached=True, powered=False))
    for x in range(xw + 1, xe):
        t.set(x, F, wz, S('tripwire', attached=True, disarmed=False, east=True, west=True, north=False, south=False,
                          powered=False))
        K.support(t, x, F, wz, K.GRAVEL)
    for side in (((2, 13), (4, 14), (1, 14), (5, 13), (3, 12)), ((20, 13), (22, 14), (19, 13), (21, 14), (17, 13))):
        laid = 0
        for (x, z) in side:
            if laid < 2 and t.is_air(x, F, z) and t.get(x, F, z) is None and K.standable(t.get(x, G, z)) \
                    and not near_heart(x, z, CLEAR + 1):
                t.set(x, F, z, S('heavy_weighted_pressure_plate', power=0))
                laid += 1

    # --- the Perch: a sniper's nest on the cap ------------------------------------------------------------
    py = max(top_of(t, x, z) for x in range(15, 22) for z in range(15, 22)) + 1   # the feet layer on top
    deck = [(x, z) for x in range(14, 23) for z in range(14, 23) if top_of(t, x, z) == py - 1]
    deck_set = set(deck)
    # sandbags along the rim that looks over the pan (north and west edges of the deck)
    for (x, z) in deck:
        rim_n = (x, z - 1) not in deck_set
        rim_w = (x - 1, z) not in deck_set
        if (rim_n or rim_w) and x + z < 37:
            t.set(x, py, z, K.CANVAS_SLAB if (x * 7 + z * 3) % 5 else K.CANVAS)
    # the rifle laid over the sandbags toward the pan, the scope on a tripod beside it
    nw = min(deck, key=lambda c: c[0] + c[1])
    sx, sz = nw[0] + 1, nw[1] + 1
    if (sx, sz) in deck_set and t.is_air(sx, py, sz):
        t.set(sx, py, sz, K.rod('north'))
    tx, tz = sx + 1, sz
    if (tx, tz) in deck_set and t.is_air(tx, py, tz):
        t.set(tx, py, tz, K.bars())
        t.set(tx, py + 1, tz, K.rod('west'))
    # the sun net over the back half: two posts, canvas slabs a block over head
    back = sorted((x, z) for (x, z) in deck if x + z >= 37)
    if back:
        bx0 = min(x for x, _ in back)
        bz0 = min(z for _, z in back)
        bx1 = max(x for x, _ in back)
        bz1 = max(z for _, z in back)
        for (x, z) in ((bx0, bz1), (bx1, bz0)):
            if (x, z) in deck_set:
                K.column(t, x, py, py + 1, z, K.beam('y'))
        for x in range(bx0 - 1, bx1 + 1):
            for z in range(bz0 - 1, bz1 + 1):
                if t.inside(x, py + 2, z) and (x, z) in deck_set and x + z >= 36:
                    t.set(x, py + 2, z, K.CANVAS_SLAB if (x + 2 * z) % 4 else K.CANVAS_NAVY)
    # the tracker mast at the back corner: a red light that blinks over the rocks at night, a screen
    mx, mz = max(deck, key=lambda c: c[0] * 2 + c[1])
    if t.is_air(mx, py, mz):
        K.column(t, mx, py, py + 1, mz, K.beam('y'))
        t.set(mx, py + 2, mz, K.LIGHT_RED)
    # a bedroll and a ration crate under the net
    for (x, z) in back:
        if t.is_air(x, py, z) and t.is_air(x, py + 1, z) and (x, z) != (mx, mz):
            t.set(x, py, z, K.carpet('gray'))
            break
    # the spotter's rope ladder up the face that looks over the pan (the hunters themselves fly up)
    lx = 18
    fronts = {}
    for y in range(F, py):
        zs = [z for z in range(12, 23) if R.rock(lx, y, z)]
        if zs:
            fronts[y] = zs[0]
    zf = min(fronts.values()) - 1
    for y in range(F, py):
        for z in range(zf + 1, fronts.get(y, zf + 1) + 1):
            if not R.rock(lx, y, z):
                t.set(lx, y, z, R.mix.at(lx * 0.45, y * 1.7, z * 0.45))
        t.set(lx, y, zf, K.ladder('north'))
    K.support(t, lx, F, zf, K.GRAVEL)
    if t.get(lx, py, zf + 1) not in (None, AIR):
        t.set(lx, py, zf + 1, AIR)
    # jet pack burns where they came down on it
    K.scorch_burst(t, py, 18.0, 18.0, 1.6, rng, density=0.4, debris=0.0)

    # --- the Anvil: their lookout over the way in -------------------------------------------------------
    ay = max(top_of(t, x, z) for x in range(15, 22) for z in range(2, 8)) + 1
    atop = [(x, z) for x in range(14, 23) for z in range(0, 9) if top_of(t, x, z) == ay - 1]
    K.scorch_burst(t, ay, sum(x for x, _ in atop) / len(atop), sum(z for _, z in atop) / len(atop), 1.8, rng,
                   density=0.6, debris=0.0)
    ax, az = min(atop, key=lambda c: (c[1], c[0]))      # the north tip
    if t.is_air(ax, ay, az):
        t.set(ax, ay, az, S('heavy_core', waterlogged=False))   # a hunter's drone, set down to watch the road

    # --- the cold camp in the lee of the leaning stone ---------------------------------------------------------
    # the net: canvas slabs on y 3 from the stone to two posts, the floor kept as it is
    cx0, cz0, cx1, cz1 = 7, 18, 10, 21
    for (x, z) in ((cx1, cz0), (cx1 - 1, cz1)):
        if t.is_air(x, F, z):
            K.column(t, x, F, F + 1, z, K.beam('y'))
    for x in range(cx0 - 1, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            if t.is_air(x, F + 2, z) and not near_heart(x, z, CLEAR + 1):
                t.set(x, F + 2, z, K.CANVAS_SLAB if (x + z) % 3 else K.CANVAS)
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            if t.is_air(x, F, z):
                t.set(x, G, z, K.pick(rng, [(K.GRAVEL, 2), (K.COARSE, 2), (K.PACKED_MUD, 1)]))
    K.bedroll(t, 7, F, 19, 'gray', 'z', 2)
    K.bedroll(t, 9, F, 21, 'brown', 'x', 1)
    t.set(8, F, 20, K.campfire(lit=False))     # never lit: they wait cold
    t.set(8, G, 20, K.GRAVEL)
    K.crate_pile(t, [(10, 19, 2), (10, 20, 1)], F, rng, kinds=((K.CRATE, 3), (K.CRATE_IMP, 1)), tarp=0.0)
    t.set(10, F + 1, 20, K.LIGHT_AMBER)            # a glow lamp on the crate, under the net
    t.set(7, F, 21, S('bone_block', axis='x'))     # what they ate
    t.set(6, F, 20, K.skull(4))
    for z in (18, 19):
        if t.is_air(cx0 - 1, F, z):
            t.set(cx0 - 1, F, z, K.bars())          # a rifle rack against the stone

    # --- the pan: the landing burns, a dropped power cell ------------------------------------------------
    for (x, z) in ((HX, HZ - 4), (HX + 4, HZ + 1), (HX - 3, HZ + 3)):
        K.scorch_burst(t, F, x, z, 1.3, rng, density=0.55, debris=0.0)
    if t.is_air(HX + 5, F, HZ - 3):
        t.set(HX + 5, F, HZ - 3, K.rod('east'))   # a spent power cell, kicked aside
    K.litter(t, F, (5, 5, 17, 17), [(K.DEBRIS, 2), (K.slab('cobblestone_slab'), 1)], 0.02, rng)
    blaster_marks(t, rng, 9)

    K.spot(t, 'HEART', HX, F, HZ, K.GRAVEL)
    return t


def verify(t):
    """Nothing stands over the pan within CLEAR of HEART (the hunters come down there)."""
    bad = []
    for (x, y, z), s in sorted(t.cells.items()):
        if y >= F and near_heart(x, z) and s != AIR and not mod.walk_through(s):
            bad.append((x, y, z, s))
    if bad:
        raise SystemExit('ambush_rocks: blocks over the landing ring: %r' % bad[:8])


def main():
    a = K.args()
    if a.only and a.only not in 'mission/ambush_rocks':
        return
    t = build()
    verify(t)
    K.save(t, 'mission/ambush_rocks', ANCHORS, META)


if __name__ == '__main__':
    main()
