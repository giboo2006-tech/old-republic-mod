"""wilds/sunken_freighter: a bulk freighter wrecked in the shallows of a warm sea (docs/MILESTONE_6.md 5.2, O3).

The story. A supply freighter on the frontier run took a raider's broadside over the sea, came down hard
in the shallows and stayed there. Her crew got off in the escape pod (the empty pod bay is on her back);
the cargo did not. Salvagers have been at her ever since: rectangular cuts in her plating where they
took what they could carry, an A-frame and a chain hoist left on her deck, crates dragged out onto the
sand and broken open. The sea has the rest: coral on her plates, kelp through her hatches, fish in the
hold where the red emergency panels still glow. The engineer's locker and the captain's strongbox are
still aboard, for anyone who swims in.

The wreck rests on the sea floor: the structure is projected to OCEAN_FLOOR_WG, so layer B (4) is the
first water layer over the sea bed at the centre, and the template never holds explicit air (the sea, or
the air above it, fills every cell the wreck does not). Waterloggable blocks are stored dry and take the
water where there is water (liquid_settings apply_waterlogging), so a wreck in very shallow water that
breaks the surface is dry above it and wet below.

Two start variants:
  broken_back  53 x 24 x 25  the hull broken in two: the stern half settled level into the sand, the bow
               half reared up on the break with the bridge clear of the bottom, cargo spilled between.
  nose_down    50 x 33 x 25  the whole hull driven nose first into the sea bed, the stern and its two
               engine bells raised high at a slant (out of the water wherever the sea is shallow).

    python3 tools/assets/structures/o3/sunken_freighter.py [--preview DIR] [--only broken_back,nose_down]
"""
import math

from common import (S, B, Template, Noise, mix, pick, slab, report, save, only_names, write_worldgen,
                    loot_table, item, empty, no_air_below)

NAME = 'sunken_freighter'
LOOT = 'chests/wilds/sunken_freighter'
LOOT_LOCKER = 'chests/wilds/sunken_freighter_locker'
BED = 4            # layer B: the first water layer over the sea bed at the start column

# ---------------------------------------------------------------- the hull, as a model in its own frame
# i: station along the hull, 0 at the stern .. L - 1 at the bow; j: height above the keel; k: across.
L, W, H = 46, 11, 9
KC = 5
NOSE = L - 10                 # the bow taper starts here
CAB0, CAB1 = L - 16, L - 9    # the bridge cabin on the deck
CAB_TOP = H - 1 + 3
BULKHEADS = (4, CAB0 - 1)
HATCHES = ((7, 12), (15, 20))
KMIN, KMAX = -5, W + 4        # the model's extent across, nacelles included
N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
SHELL = ('hull', 'keel', 'rib_y', 'rib_z', 'stripe', 'cabin', 'glass', 'bulk', 'ring', 'throat', 'fin', 'light',
         'amber', 'conduit', 'deck', 'hatch', 'nacelle', 'nacelle_top', 'ramp')


def section(i):
    """(keel layer, deck layer, half width) of the hull at station i, None outside it."""
    if i < 2 or i >= L:
        return None
    if i < NOSE:
        return 0, H - 1, 5.0
    f = (i - NOSE + 1) / (L - NOSE + 1)
    return int(round(4.0 * f ** 1.5)), H - 1 - int(round(2.5 * f)), 5.0 - 3.2 * f ** 1.3


def in_hull(i, j, k):
    s = section(i)
    if s is None:
        return False
    j0, j1, half = s
    if j < j0 or j > j1:
        return False
    lim = half - max(0, 2 - (j - j0)) - max(0, 1 - (j1 - j))
    return abs(k - KC) <= lim + 1e-6


def in_cabin(i, j, k):
    if not CAB0 <= i <= CAB1 or j <= H - 1 or j > CAB_TOP:
        return False
    if i == CAB1 and j == CAB_TOP:
        return False
    return abs(k - KC) <= (2 if j == CAB_TOP else 3)


NACELLES = (-2, W + 1)        # the nacelles' axes across the hull (k), at height NAC_J
NAC_J, NAC_LEN = 5, 13


def nac_r(i):
    """The nacelle's radius at station i: full along most of it, tapering into the intake at the front."""
    return {NAC_LEN: 1.6, NAC_LEN - 1: 2.0, NAC_LEN - 2: 2.3}.get(i, 2.5)


def in_nacelle(i, j, k, kc):
    return 0 <= i <= NAC_LEN and math.hypot(j - NAC_J, k - kc) <= nac_r(i)


def fin_height(i):
    return {2: 4, 3: 4, 4: 3, 5: 3, 6: 2, 7: 1}.get(i, 0)


def build_model(salvage_cuts):
    """The freighter as {(i, j, k): part} for its plating and fittings, and the set of cells inside it."""
    m, inner = {}, set()
    for i in range(L):
        for j in range(0, CAB_TOP + 1):
            for k in range(W):
                if in_hull(i, j, k):
                    if any(not in_hull(i + a, j + b, k + c) for (a, b, c) in N6):
                        j0, j1, _ = section(i)
                        if j <= j0 + 1:
                            m[(i, j, k)] = 'keel'
                        elif i % 4 == 2 and 2 < i < NOSE:
                            m[(i, j, k)] = 'rib_z' if j == j1 else 'rib_y'
                        elif j == j1 - 2 and abs(k - KC) >= 4:
                            m[(i, j, k)] = 'stripe'
                        else:
                            m[(i, j, k)] = 'hull'
                    else:
                        inner.add((i, j, k))
                elif in_cabin(i, j, k):
                    if any(not (in_cabin(i + a, j + b, k + c) or in_hull(i + a, j + b, k + c)) for (a, b, c) in N6):
                        front = i == CAB1 or (i == CAB1 - 1 and j == CAB_TOP)
                        m[(i, j, k)] = 'glass' if (j == H + 1 and (front or abs(k - KC) == 3)) or \
                            (front and j == H and abs(k - KC) <= 1) else 'cabin'
                    else:
                        inner.add((i, j, k))
    # the engine room's torn conduits, the stern's closed loading ramp
    for (i, j, k), part in list(m.items()):
        if i == 3 and part == 'hull' and j in (3, 5):
            m[(i, j, k)] = 'conduit'
        if i == 2 and abs(k - KC) <= 3 and 1 <= j <= 6:
            m[(i, j, k)] = 'ramp' if abs(k - KC) <= 2 and j <= 5 else 'bulk'
    # the two engine nacelles along the stern quarters, an engine bell at the back of each, intakes in front
    for kc in NACELLES:
        for i in range(0, NAC_LEN + 1):
            for j in range(0, H + 1):
                for k in range(kc - 3, kc + 4):
                    if not in_nacelle(i, j, k, kc):
                        continue
                    if (i, j, k) in m and m[(i, j, k)] != 'hull':
                        continue
                    d = math.hypot(j - NAC_J, k - kc)
                    if i <= 1:
                        m[(i, j, k)] = 'ring' if d >= nac_r(i) - 1.0 else ('throat' if i == 1 else None)
                        if m[(i, j, k)] is None:
                            del m[(i, j, k)]
                    elif i == NAC_LEN and d < nac_r(i) - 0.6:
                        m[(i, j, k)] = 'throat'
                    elif any(not in_nacelle(i + a, j + b, k + c, kc) for (a, b, c) in N6):
                        if in_hull(i, j, k) and (i, j, k) in inner:
                            continue
                        m[(i, j, k)] = 'nacelle_top' if j >= NAC_J + 2 and abs(k - kc) <= 1 else 'nacelle'
    # bulkheads with a door each, the hold's grating deck, the hold's red emergency panels
    for bi in BULKHEADS:
        for (i, j, k) in [c for c in inner if c[0] == bi]:
            j0 = section(i)[0]
            if not (k == KC and j0 + 2 <= j <= j0 + 3):
                m[(i, j, k)] = 'bulk'
                inner.discard((i, j, k))
    for (i, j, k) in sorted(inner):
        j0 = section(i)[0] if section(i) else None
        if j0 is not None and j == j0 + 1 and 2 <= i <= L - 4:
            m[(i, j, k)] = 'deck'
    for c in [c for c in m if c in inner]:
        inner.discard(c)
    for i in range(7, CAB0 - 1, 6):
        for k in (0, W - 1):
            m[(i, H - 2, k)] = 'light'
    m[(CAB0 + 3, CAB_TOP, KC)] = 'amber'
    # the dorsal fin
    for i in range(2, 8):
        for j in range(H, H + fin_height(i)):
            m[(i, j, KC)] = 'fin'
    # the cargo hatches: the first opened and standing on its hinge, the second gone over the side
    for n, (a, b) in enumerate(HATCHES):
        for i in range(a, b + 1):
            for k in range(KC - 2, KC + 3):
                m.pop((i, H - 1, k), None)
            if n == 0:
                for j in range(H, H + 5):
                    m[(i, j, KC - 3)] = 'hatch'
    # the empty pod bay: a hole in the deck in a raised collar
    for i in range(CAB0 - 6, CAB0 - 1):
        for k in range(KC - 2, KC + 3):
            if CAB0 - 6 < i < CAB0 - 2 and abs(k - KC) <= 1:
                m.pop((i, H - 1, k), None)
            else:
                m[(i, H, k)] = 'bulk'
    # the airlock on the port side, the salvagers' neat cuts
    for i in (CAB0 + 1, CAB0 + 2):
        for j in (2, 3, 4):
            m.pop((i, j, 0), None)
    for (i0, j0, side) in salvage_cuts:
        for i in (i0, i0 + 1):
            for j in (j0, j0 + 1):
                m.pop((i, j, side), None)
    return m, inner


class Skin:
    """The plating's look, fixed to the hull frame so a tilted section keeps its patches."""

    def __init__(self, seed):
        self.plate = mix([('oldrepublic:wreck_plating', 10), ('oldrepublic:durasteel_plating', 3),
                          ('oldrepublic:rusted_plating', 3)], seed, 2.2)
        self.keel = mix([('oldrepublic:durasteel_plating', 5), ('oldrepublic:rusted_plating', 3),
                         ('oldrepublic:wreck_plating', 2)], seed + 1, 2.0)
        self.scorch = Noise(seed + 2, 5.0)
        self.holes = Noise(seed + 3, 3.2)

    def scorched(self, i, j, k):
        return self.scorch.at(i, j * 1.5, k * 2) > 0.66

    def hole(self, i, j, k, part, extra=0.0):
        """A plate torn away (never the keel, a rib or a fitting)."""
        if part not in ('hull', 'stripe', 'cabin', 'glass', 'nacelle'):
            return False
        return self.holes.at(i, j, k * 1.3) > 0.73 - extra

    def block(self, part, i, j, k):
        if part in ('hull', 'cabin', 'fin', 'stripe', 'nacelle', 'nacelle_top'):
            if self.scorched(i, j, k) and part != 'stripe':
                return 'oldrepublic:wreck_plating_scorched'
            if part == 'stripe' or part == 'nacelle_top':
                return 'minecraft:orange_terracotta'
            if part == 'nacelle':
                return self.keel(i, j, k)
            return self.plate(i, j, k)
        return {
            'keel': None, 'rib_y': S('oldrepublic:durasteel_beam', axis='y'),
            'rib_z': S('oldrepublic:durasteel_beam', axis='z'), 'deck': 'oldrepublic:durasteel_grate',
            'bulk': 'oldrepublic:durasteel_plating', 'glass': 'minecraft:gray_stained_glass',
            'light': 'oldrepublic:light_panel_red', 'amber': 'oldrepublic:light_panel_amber',
            'conduit': 'oldrepublic:wreck_conduit', 'ring': 'oldrepublic:wreck_plating_scorched',
            'throat': 'oldrepublic:durasteel_vent', 'hatch': 'oldrepublic:durasteel_plating',
            'ramp': 'oldrepublic:sealed_plating',
        }.get(part) or self.keel(i, j, k)


def rnd(v):
    return int(math.floor(v + 0.5))


class Placed:
    """A section of the model set into the template, pitched by `angle` degrees (nose up positive) about a
    pivot: model (pi, pj) goes to template (px, py); k runs along z from z0."""

    def __init__(self, t, model, inner, skin, stations, pivot, at, angle, z0, extra_holes=None):
        self.t, self.z0 = t, z0
        self.pi, self.pj = pivot
        self.px, self.py = at
        th = math.radians(angle)
        self.c, self.s = math.cos(th), math.sin(th)
        self.cells, self.inner = {}, set()
        lo, hi = stations
        sel = lambda i: lo <= i <= hi   # noqa: E731
        pts = [self.fwd(i, j) for (i, j, k) in list(model) + list(inner) if sel(i)]
        x0, x1 = min(p[0] for p in pts) - 1, max(p[0] for p in pts) + 1
        y0, y1 = min(p[1] for p in pts) - 1, max(p[1] for p in pts) + 1
        for x in range(int(math.floor(x0)), int(math.ceil(x1)) + 1):
            for y in range(int(math.floor(y0)), int(math.ceil(y1)) + 1):
                fi, fj = self.inv(x, y)
                for k in range(KMIN, KMAX + 1):
                    hit = None
                    i, j = rnd(fi), rnd(fj)
                    if sel(i) and (i, j, k) in model:
                        hit = (i, j, k)
                    elif self.s != 0:
                        for (oi, oj) in ((0.35, 0.35), (-0.35, 0.35), (0.35, -0.35), (-0.35, -0.35)):
                            ii, jj = rnd(fi + oi), rnd(fj + oj)
                            if sel(ii) and model.get((ii, jj, k)) in SHELL:
                                hit = (ii, jj, k)
                                break
                    if hit:
                        part = model[hit]
                        if extra_holes and skin.hole(*hit, part, extra_holes(hit[0])):
                            continue
                        if not extra_holes and skin.hole(*hit, part):
                            continue
                        if t.inside(x, y, z0 + k):
                            t.set(x, y, z0 + k, skin.block(part, *hit))
                            self.cells[(x, y, z0 + k)] = (part,) + hit
                    elif sel(i) and (i, j, k) in inner and t.inside(x, y, z0 + k):
                        self.inner.add((x, y, z0 + k))

    def fwd(self, i, j):
        di, dj = i - self.pi, j - self.pj
        return self.px + di * self.c - dj * self.s, self.py + di * self.s + dj * self.c

    def inv(self, x, y):
        dx, dy = x - self.px, y - self.py
        return self.pi + dx * self.c + dy * self.s, self.pj - dx * self.s + dy * self.c

    def at(self, i, j, k):
        x, y = self.fwd(i, j)
        return rnd(x), rnd(y), self.z0 + k

    def rest(self, i, j, k, state, nbt_chest=None, search=False):
        """Put a loose thing at model cell (i, j, k), sliding it down to whatever it rests on inside the hull.
        With search, the nearest free resting place inside the hull is taken when that cell is taken (a chest
        also wants water or air over it, so it can be opened)."""
        t = self.t
        tx, ty, tz = self.at(i, j, k)
        cands = [(tx, ty, tz)]
        if search:
            cands += sorted(self.inner, key=lambda p: (abs(p[0] - tx) + 1.5 * abs(p[1] - ty) + abs(p[2] - tz), p))[:60]
        for (x, y, z) in cands:
            while y > 0 and t.get(x, y - 1, z) is None:
                y -= 1
            if t.get(x, y, z) is not None or t.get(x, y - 1, z) is None:
                continue
            if nbt_chest and t.get(x, y + 1, z) is not None:
                continue
            if nbt_chest:
                t.chest(x, y, z, facing=nbt_chest[0], loot=nbt_chest[1])
            else:
                t.set(x, y, z, state)
            return x, y, z
        return None


# ---------------------------------------------------------------- the sea bed round the wreck

def lowest(t, columns):
    """The lowest set layer of each column."""
    low = {}
    for (x, y, z) in t.cells:
        if (x, z) in columns:
            low[(x, z)] = min(low.get((x, z), 99), y)
    return low


def sand_bed(t, rng, placed, rim=2):
    """The bed the wreck has pushed up: under every column of hull that reaches the sea bed, sand from layer 0
    up to the hull (so the support columns carry it over a dip), and a berm of sand and gravel round it.
    Returns the bed's surface: {(x, z): first layer above it}."""
    hull_cols = {}
    for p in placed:
        for (x, y, z) in p.cells:
            hull_cols[(x, z)] = min(hull_cols.get((x, z), 99), y)
    resting = {c: y for c, y in hull_cols.items() if y <= BED}
    surface = {}
    bed = mix([('minecraft:sand', 8), ('minecraft:gravel', 2), ('minecraft:clay', 1)], 5701, 2.0)
    for (x, z), y_low in sorted(resting.items()):
        for y in range(0, y_low):
            if t.get(x, y, z) is None:
                t.set(x, y, z, bed(x, y, z))
    for (x, z) in sorted(resting):
        for dx in range(-rim, rim + 1):
            for dz in range(-rim, rim + 1):
                c = (x + dx, z + dz)
                if c in hull_cols or c in surface or not t.inside(c[0], 0, c[1]):
                    continue
                near = max(abs(dx), abs(dz))
                top = BED + (1 if near == 1 and rng.random() < 0.55 else 0)
                for y in range(0, top):
                    if t.get(c[0], y, c[1]) is None:
                        t.set(c[0], y, c[1], bed(c[0], y, c[1]))
                surface[c] = top
    return surface, hull_cols


def pad(t, x, z, top=BED):
    """A column of sea bed from layer 0 under a loose thing away from the wreck."""
    for y in range(0, top):
        if t.get(x, y, z) is None:
            t.set(x, y, z, 'minecraft:sand' if y >= top - 2 else 'minecraft:gravel')


def dress(t, rng, surface, density=0.35):
    """Seagrass, kelp, coral and sea pickles on the bed round the wreck (stored dry; the sea waters them)."""
    life = [('seagrass', 8), ('tall', 3), ('kelp', 3), ('coral', 2), ('fan', 2), ('pickle', 1)]
    for (x, z), top in sorted(surface.items()):
        if rng.random() > density or t.get(x, top, z) is not None:
            continue
        what = pick(rng, life)
        if what == 'seagrass':
            t.set(x, top, z, 'minecraft:seagrass')
        elif what == 'tall':
            t.set(x, top, z, S('tall_seagrass', half='lower'))
            t.set(x, top + 1, z, S('tall_seagrass', half='upper'))
        elif what == 'kelp':
            h = rng.randint(2, 4)
            for y in range(top, top + h - 1):
                t.set(x, y, z, 'minecraft:kelp_plant')
            t.set(x, top + h - 1, z, S('kelp', age=rng.randint(10, 20)))
        elif what == 'coral':
            t.set(x, top, z, rng.choice(('minecraft:brain_coral_block', 'minecraft:tube_coral_block',
                                         'minecraft:horn_coral_block', 'minecraft:fire_coral_block')))
            if rng.random() < 0.6:
                t.set(x, top + 1, z, S(rng.choice(('brain_coral_fan', 'tube_coral_fan', 'horn_coral_fan')),
                                       waterlogged=False))
        elif what == 'fan':
            t.set(x, top, z, S(rng.choice(('brain_coral_fan', 'tube_coral_fan', 'bubble_coral_fan')),
                               waterlogged=False))
        else:
            t.set(x, top, z, S('sea_pickle', pickles=rng.randint(1, 3), waterlogged=False))


def overgrow(t, rng, placed, max_layer, density=0.08):
    """Coral and sea pickles on the wreck's own plates low down, where it is surely under water."""
    for p in placed:
        for (x, y, z), info in sorted(p.cells.items()):
            if not BED - 1 <= y <= max_layer or info[0] not in ('hull', 'keel', 'stripe') or t.get(x, y + 1, z) is not None:
                continue
            r = rng.random()
            if r < density * 0.5:
                t.set(x, y, z, rng.choice(('minecraft:tube_coral_block', 'minecraft:brain_coral_block',
                                           'minecraft:horn_coral_block')))
            elif r < density:
                t.set(x, y + 1, z, S('sea_pickle', pickles=rng.randint(1, 4), waterlogged=False)
                      if rng.random() < 0.4 else S(rng.choice(('tube_coral_fan', 'horn_coral_fan')), waterlogged=False))


CRATES = [('oldrepublic:cargo_crate', 5), ('oldrepublic:cargo_crate_republic', 2), ('oldrepublic:durasteel_plating', 1)]


def spill(t, rng, cells, surface):
    """Crates dragged out or spilled onto the bed, some stacked, some half sunk, torn plates among them."""
    for (x, z, h) in cells:
        top = surface.get((x, z))
        if top is None:
            pad(t, x, z)
            top = BED
            surface[(x, z)] = top
        if h == 0:
            t.set(x, top, z, slab('oldrepublic:durasteel_plating_slab', 'bottom'))
            continue
        base = top - 1 if h == 1 and rng.random() < 0.4 else top
        for y in range(base, base + h):
            t.set(x, y, z, pick(rng, CRATES))


def hold_cargo(t, rng, part, stations, density=0.6):
    """Crates along both sides of the hold in a level section, some gone, a few tumbled into the aisle."""
    for i in range(stations[0], stations[1] + 1):
        if i in BULKHEADS:
            continue
        for k in (1, 2, 8, 9):
            for j in (2, 3):
                if rng.random() < density:
                    part.rest(i, j, k, pick(rng, CRATES[:2]))
        if rng.random() < 0.25:
            part.rest(i, 2, rng.choice((4, 5, 6)), pick(rng, CRATES[:2]))


def a_frame(t, x, y, z, span=3, drop=3):
    """The salvagers' gantry and chain hoist standing on the deck at layer y, its beam across the hull (z)."""
    fence = S('dark_oak_fence', waterlogged=False)
    for s in (-span, span):
        for h in range(0, 3):
            t.set(x, y + h, z + s, fence)
    for s in range(-span, span + 1):
        t.set(x, y + 3, z + s, S('dark_oak_log', axis='z'))
    for h in range(1, drop + 1):
        t.set(x, y + 3 - h, z, S('iron_chain', axis='y', waterlogged=False))


# ---------------------------------------------------------------- variant broken_back

def build_broken_back():
    sx, sy, sz = 53, 24, 25
    t = Template(sx, sy, sz, seed=960158, name='broken_back')
    rng = t.rng
    z0 = (sz - W) // 2
    cuts = [(12, 4, W - 1), (17, 3, W - 1), (14, 5, 0)]
    model, inner = build_model(cuts)
    skin = Skin(5710)
    # the break: plates torn away on both sides of it, more the nearer they are
    torn = lambda i: 0.35 * max(0.0, 1.0 - abs(i - 23) / 4.0)   # noqa: E731
    stern = Placed(t, model, inner, skin, (0, 21), (0, 0), (3, BED - 2), 0.0, z0, extra_holes=torn)
    bow = Placed(t, model, inner, skin, (25, L - 1), (25, 0), (29, BED - 1), 14.0, z0, extra_holes=torn)
    parts = [stern, bow]

    # cargo still in the stern half's hold, the engineer's locker in the engine room
    hold_cargo(t, rng, stern, (6, 20), density=0.55)
    stern.rest(3, 2, 2, None, nbt_chest=('east', LOOT_LOCKER), search=True)
    # the captain's strongbox, slid to the back of the bridge in the reared bow
    bow.rest(CAB0 + 1, H, KC + 2, None, nbt_chest=('east', LOOT), search=True)
    for (i, k) in ((CAB1 - 1, KC - 1), (CAB1 - 1, KC + 1)):
        bow.rest(i, H, k, S('oldrepublic:console', facing='west'), search=True)

    # the sea bed: the stern's bed and berm, the bow's heel, spilled cargo in the break and beside it
    surface, cols = sand_bed(t, rng, parts)
    spill(t, rng, [(25, z0 + 3, 2), (26, z0 + 4, 1), (26, z0 + 7, 1), (27, z0 + 2, 1), (25, z0 + 8, 1), (24, z0 - 2, 1),
                   (27, z0 + 6, 0), (22, z0 + 12, 1), (21, z0 + 13, 2), (30, z0 + 13, 1), (31, z0 + 14, 0),
                   (33, z0 - 2, 1), (34, z0 - 3, 0), (16, z0 - 3, 1), (10, z0 + 13, 0), (40, z0 + 14, 1)], surface)
    # the hatch cover that went over the side, lying on the bed
    for i in range(16, 22):
        for k in range(3):
            x, z = i, z0 + W + 1 + k
            if (x, z) not in surface:
                pad(t, x, z)
                surface[(x, z)] = BED
            t.set(x, surface.pop((x, z)), z, slab('oldrepublic:durasteel_plating_slab', 'bottom'))
    # the salvagers' gantry on the stern deck over the hatch whose cover went over the side
    a_frame(t, 3 + 17, BED - 2 + H, z0 + KC, span=3, drop=4)
    dress(t, rng, surface)
    overgrow(t, rng, parts, BED + 3)
    return t


# ---------------------------------------------------------------- variant nose_down

def build_nose_down():
    sx, sy, sz = 50, 33, 25
    t = Template(sx, sy, sz, seed=960159, name='nose_down')
    rng = t.rng
    z0 = (sz - W) // 2
    cuts = [(10, 4, W - 1), (19, 5, 0), (22, 3, 0)]
    model, inner = build_model(cuts)
    skin = Skin(5720)
    hull = Placed(t, model, inner, skin, (0, L - 1), (L - 1, 4), (sx - 5, BED - 1), -24.0, z0)
    parts = [hull]
    # loose cargo in the hold has slid forward against the bulkhead; the lockers came with it
    for i in range(CAB0 - 6, CAB0 - 1):
        for k in (2, 3, 7, 8):
            if rng.random() < 0.7:
                hull.rest(i, 4, k, pick(rng, CRATES[:2]))
    hull.rest(CAB0 - 3, 4, KC, None, nbt_chest=('west', LOOT), search=True)
    hull.rest(CAB0 + 2, H + 1, KC + 1, None, nbt_chest=('west', LOOT_LOCKER), search=True)

    surface, cols = sand_bed(t, rng, parts, rim=3)
    # what fell out of her on the way in, scattered under the stern and past the nose
    spill(t, rng, [(8, z0 + 2, 1), (9, z0 + 3, 2), (11, z0 + 8, 1), (14, z0 - 2, 0), (6, z0 + 12, 1), (5, z0 + 13, 0),
                   (17, z0 + 13, 1), (20, z0 - 3, 1), (21, z0 - 2, 0), (25, z0 + 13, 2), (sx - 2, z0 + 3, 1),
                   (sx - 3, z0 + 8, 0), (sx - 2, z0 + 9, 1), (12, z0 + 5, 1)], surface)
    dress(t, rng, surface, density=0.4)
    overgrow(t, rng, parts, BED + 3)
    return t


# ---------------------------------------------------------------- worldgen, loot

def write_loot():
    loot_table(LOOT, [
        ((3, 5), [item('oldrepublic:durasteel_plate', 8, 1, 2), item('oldrepublic:alloy_plate', 4),
                  item('iron_ingot', 6, 1, 3), item('copper_ingot', 6, 2, 4), item('redstone', 4, 2, 4),
                  item('oldrepublic:medpac', 3), item('gunpowder', 3, 1, 3), item('oldrepublic:credit_chit', 3, 2, 5)]),
    ])
    loot_table(LOOT_LOCKER, [
        ((2, 3), [item('iron_nugget', 6, 2, 6), item('copper_ingot', 4, 1, 3), item('redstone', 3, 1, 3),
                  item('oldrepublic:medpac', 2), item('oldrepublic:credit_chit', 3, 1, 4), item('paper', 2, 1, 3),
                  empty(4)]),
    ])


BUILDERS = [('broken_back', build_broken_back), ('nose_down', build_nose_down)]


def main():
    only = only_names()
    write_loot()
    for name, fn in BUILDERS:
        if only and name not in only:
            continue
        t = fn()
        air = no_air_below(t, t.sy)
        assert not air, '%s: explicit air in a wreck that may lie under water: %s' % (name, air[:5])
        save(t, 'wilds/%s/%s' % (NAME, name), views=('se', 'nw'), ground=BED)
        report(t, 'wilds/%s/%s' % (NAME, name))
    write_worldgen(NAME, [(name, 1) for name, _ in BUILDERS], buried=BED, spacing=64, separation=24, salt=960154,
                   biomes=['minecraft:warm_ocean', 'minecraft:lukewarm_ocean'],
                   adapted=dict(yard_stand=BED, clear_from=40, clear_inset=99, support_depth=6, ramp_rings=0),
                   liquid='apply_waterlogging', heightmap='OCEAN_FLOOR_WG', max_distance=64)


if __name__ == '__main__':
    main()
