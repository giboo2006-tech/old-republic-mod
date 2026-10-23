"""oldrepublic:wilds/starfall_crater: where a starfall iron meteorite came down (docs/MILESTONE_7.md 4.1).

Run: python3 tools/assets/structures/or/starfall_crater.py [--preview DIR]

Three start variants. In each the crater's centre is the template's centre column, where the jigsaw start
measures the ground, and each is buried 14: the ground's top block at template y 13, feet at y 14. A structure has
one start height, so the three share it, and the shallower craters stand on a deeper plug of rock. Each levels its
ground over the crater, its rim and its ejecta, ragged at the edges so the world's own ground meets it. It digs the
bowl out of that ground and leaves the world as it is beyond. The meteorite came in from the west (template -x):
its streak runs through the crater from west to east, and the ejecta and the thrown fragments lie mostly to the
east, in a fan.

* wilds/starfall_crater/fresh (57 x 22 x 39): a bowl 17 across and 5 deep under a rim of broken rock, the meteorite
  in the middle still hot. Magma glows round its foot and lies under its shell, and smoke rises out of it from a
  signal campfire buried under the magma. Two vents smoke in the floor beside it. The streak to the west is
  scorched black. Fragments of the shell and scorched ground lie thrown out to the east. The trees round the
  crater stand burnt to snags, and those nearest it were blown down, lying outward from the blast.
* wilds/starfall_crater/old (55 x 20 x 41): a crater long cooled and grassed over, 21 across and 4 deep. A pond
  fills the bowl, and the meteorite is a mossy island in it. Mossy fragments lie half buried along the streak to
  the east. Someone prospected here long ago and gave up: a plank walk to the island ends at a notch cut into the
  shell, where the ore shows. On the rim are their fallen lean-to, a dead fire in a ring of stones, a chipped anvil,
  an empty barrel and a rain cauldron.
* wilds/starfall_crater/deep (45 x 22 x 33): a deep crater in rocky ground, 15 across and 6 deep. Its rim is pushed
  up into cliffs of broken strata, deepslate in the floor. Only the top of the meteorite shows. A shaft is sunk
  through its shell, with a ladder and a headframe, its chain and lantern hanging down, to the core: a chamber
  of deepslate studded with deepslate starfall ore. A prospectors' path winds down the wall to it, and their spoil
  lies heaped beside the shaft. On the rim lies an Imperial probe droid that came to survey the fall and crashed,
  its red eye still lit.

Each holds 6 to 14 starfall ore blocks (fresh 10, old 9, deep 12). There is no chest: the ore is the prize. The
worldgen (pool, structure, set, biome tag) is written by worldgen_or.py.
"""
import math

from common import Template, S, AIR, Noise, cli, save, pick, patches

BURIED = 14
SURF = BURIED - 1       # the ground's top block at the crater's centre
G = BURIED              # feet on the original ground
TEMPLATE = 'wilds/starfall_crater/'
SIX = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
FOUR = ((1, 0), (-1, 0), (0, 1), (0, -1))

STARFALL_ROCK = S('or:starfall_rock')
STARFALL_ORE = S('or:starfall_ore')
DEEPSLATE_ORE = S('or:deepslate_starfall_ore')
ORES = ('oldrepublic:starfall_ore', 'oldrepublic:deepslate_starfall_ore')
# The meteorite's shell: its fusion crust, with basalt and blackstone (starfall rock in the middle of the list is
# the commonest pick of patches()).
SHELL = [(S('basalt', axis='y'), 2), (S('blackstone'), 2), (STARFALL_ROCK, 7), (S('smooth_basalt'), 1)]
ROCK = [(S('andesite'), 1), (S('stone'), 6), (S('tuff'), 1)]


def six(p):
    x, y, z = p
    return [(x + a, y + b, z + c) for a, b, c in SIX]


# ---------------------------------------------------------------- the landform

class Crater:
    """The landform of one crater: for every column it touches, the top of its ground (absolute template y) and its
    zone: 'bowl' inside the rim crest, 'rim', 'flank' (the raised outer slope), 'blanket' (the levelled ejecta
    beyond) or 'streak' (the scorched band uprange). The bowl rises from the floor as (r / R) ** power to the rim
    crest, which is higher downrange; the flank falls away over `flank` blocks; the blanket reaches `blanket`
    further, and `fan` more to the east."""

    def __init__(self, t, radius, depth, rim, flank, power, seed, blanket=2.0, fan=8.0, streak=(0, 0.0), rough=0.5):
        self.t = t
        self.cx, self.cz = t.sx // 2, t.sz // 2
        self.R, self.d, self.hr, self.wr, self.p = radius, depth, rim, flank, power
        self.blanket, self.fan = blanket, fan
        self.streak_len, self.streak_w = streak
        self.rough = rough
        self.n_edge = Noise(seed, 3.0)
        self.n_rim = Noise(seed + 1, 2.5)
        self.n_out = Noise(seed + 2, 2.0)
        self.n_fine = Noise(seed + 3, 1.6)
        self.n_bump = Noise(seed + 4, 2.2)
        self.top = {}
        self.zone = {}
        self.u = {}
        self.yard = 0
        for x in range(t.sx):
            for z in range(t.sz):
                self._column(x, z)

    def _ring(self, noise, theta, radius=6.0):
        """Noise sampled round a circle, so it is continuous all the way round."""
        return noise.at(self.cx + radius * math.cos(theta), 0, self.cz + radius * math.sin(theta))

    def polar(self, x, z):
        dx, dz = x - self.cx, z - self.cz
        return math.hypot(dx, dz), math.atan2(dz, dx)

    def rim_height(self, theta):
        return self.hr * (1 + 0.3 * math.cos(theta)) + 1.2 * (self._ring(self.n_rim, theta) - 0.5)

    def crest(self, theta):
        return self.R * (1 + 0.14 * (self._ring(self.n_edge, theta) - 0.5))

    def extent(self, theta):
        fan = self.fan * max(0.0, math.cos(theta)) ** 3
        return self.R + self.wr + self.blanket + fan + 2.4 * (self._ring(self.n_out, theta, 9.0) - 0.5)

    def _column(self, x, z):
        r, th = self.polar(x, z)
        dx, dz = x - self.cx, z - self.cz
        ragged = 1.4 * (self.n_fine.at(x, 0, z) - 0.5)
        Re = self.crest(th)
        inside = r < self.extent(th) + ragged
        streak = False
        if not inside and self.streak_len and dx < 0:
            # the streak runs on west of the blanket's edge, narrowing to nothing
            along = -dx - (self.R + self.wr + self.blanket)
            if along <= self.streak_len:
                w = max(0.7, self.streak_w * (1 - max(0.0, along) / self.streak_len))
                streak = abs(dz) <= w + 0.8 * ragged
        if not inside and not streak:
            return
        hr = self.rim_height(th)
        u = r / Re
        if u < 1:
            s = -self.d + (self.d + hr) * u ** self.p
            zone = 'bowl' if u < 0.93 else 'rim'
        elif r < Re + self.wr:
            s = hr * (1 - (r - Re) / self.wr) ** 1.6
            zone = 'rim' if r < Re + 0.5 * self.wr else 'flank'
        else:
            s = 0.0
            zone = 'blanket' if inside else 'streak'
        if zone != 'bowl':
            # hummocky ejecta: a little higher in patches near the crater
            s += 0.8 * (self.n_bump.at(x, 0, z) - 0.5) + (0.6 if zone == 'blanket' and self.n_bump.at(x, 5, z) > 0.72 else 0)
        else:
            s += self.rough * (self.n_bump.at(x, 0, z) - 0.5) * 2 * min(1.0, 1.4 * u)
        self.top[(x, z)] = SURF + int(math.floor(s + 0.5))
        self.zone[(x, z)] = zone
        self.u[(x, z)] = u

    # ------------------------------------------------------------ building

    def ground(self, surface, under, deep=None):
        """The plug: every column's ground from y 0 to its top. surface(x, z, zone) is the top block,
        under(x, y, z, zone) the two below it, deep (a picker) everything lower."""
        deep = deep or patches(self.t.seed + 11, ROCK, 3.0)
        for (x, z), top in sorted(self.top.items()):
            zone = self.zone[(x, z)]
            for y in range(0, top + 1):
                k = top - y
                if k == 0:
                    st = surface(x, z, zone)
                elif k <= 2:
                    st = under(x, y, z, zone)
                else:
                    st = deep(x, y, z)
                self.t.set(x, y, z, st)

    def set_top(self, x, z, y):
        """Moves a column's top (for a path or a heap); the ground under the new top is filled by the caller."""
        self.top[(x, z)] = y

    def clear_inset(self):
        """The adapted element's clear_inset: the yard (cleared above the template) covers the bowl and its rim."""
        half = (min(self.t.sx, self.t.sz) - 1) // 2
        return max(0, half - int(round(0.8 * (self.R + 1))))

    def air(self, inset):
        """Explicit air over the ground of every column it touches, so a rise is cut away: up to the template's
        top inside the yard (which the fit clears above), else at least three layers and falling from the
        template's top by one a block outside the yard, so a slope is cut back as a bank, never a wall."""
        t = self.t
        x0, x1, z0, z1 = inset, t.sx - 1 - inset, inset, t.sz - 1 - inset
        for (x, z), top in sorted(self.top.items()):
            out = max(x0 - x, x - x1, z0 - z, z - z1, 0)
            ceiling = t.sy - 1 if out == 0 else max(top + 3, t.sy - 1 - out)
            for y in range(top + 1, min(t.sy - 1, ceiling) + 1):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)

    def on_top(self, x, z):
        """The first free layer above a column's ground (None outside the crater)."""
        top = self.top.get((x, z))
        return None if top is None else top + 1

    def free(self, x, y, z):
        s = self.t.get(x, y, z)
        return s is None or s == AIR


# ---------------------------------------------------------------- the meteorite

def meteorite(t, centre, radii, seed, n_core, ore, shell, inner, exposed=0, face=(1.0, 0.6, 0.0)):
    """A lumpy meteorite: the cells inside a noisy ellipsoid round `centre` (a point, cell centres at +0.5). Its
    n_core innermost cells are `ore`, `exposed` more are ore cells in the shell on the side `face` points to (the
    glint a passer-by sees); the outer layer is `shell` (a picker), the rest `inner`. Returns (cells, shell cells,
    ore cells)."""
    cx, cy, cz = centre
    rx, ry, rz = radii
    n = Noise(seed, 1.8)
    q = {}
    for x in range(int(cx - rx) - 2, int(cx + rx) + 3):
        for y in range(int(cy - ry) - 2, int(cy + ry) + 3):
            for z in range(int(cz - rz) - 2, int(cz + rz) + 3):
                v = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 + ((z + 0.5 - cz) / rz) ** 2
                v *= 1 + 0.5 * (n.at(x, y, z) - 0.5)
                if v < 1 and t.inside(x, y, z):
                    q[(x, y, z)] = v
    cells = set(q)
    crust = {p for p in cells if any(nb not in cells for nb in six(p))}
    interior = sorted(cells - crust, key=lambda p: (q[p], p))
    ores = set(interior[:n_core])
    if exposed:
        fx, fy, fz = face
        upper = sorted((p for p in crust if p[1] + 0.5 >= cy - 0.3),
                       key=lambda p: -((p[0] + 0.5 - cx) * fx / rx + (p[1] + 0.5 - cy) * fy / ry + (p[2] + 0.5 - cz) * fz / rz))
        ores |= set(upper[:exposed])
    for p in sorted(cells):
        t.set(*p, ore if p in ores else (shell(*p) if p in crust else inner(*p)))
    return cells, crust, ores


def column_bottoms(cells):
    low = {}
    for (x, y, z) in cells:
        if (x, z) not in low or y < low[(x, z)]:
            low[(x, z)] = y
    return low


def column_tops(cells):
    high = {}
    for (x, y, z) in cells:
        if (x, z) not in high or y > high[(x, z)]:
            high[(x, z)] = y
    return high


# ---------------------------------------------------------------- shared dressing

def fragments(c, rng, n, shell, spread=0.5, streak=0, mossy=False, scorch=False, avoid=()):
    """Pieces of the shell thrown out along the streak: n in a fan downrange (angles about east, spread in
    radians), `streak` more uprange along the scorched band. Each is one to four cells: set into the ground,
    lying on it or both. Returns the cells used."""
    t = c.t
    used = set()
    taken = set(avoid)
    tries = 0
    want = [('fan', i) for i in range(n)] + [('streak', i) for i in range(streak)]
    for kind, _ in want:
        for _ in range(40):
            tries += 1
            if kind == 'fan':
                th = rng.gauss(0.0, spread)
                r = rng.uniform(c.R + 1.5, c.extent(th) - 1.0)
                x, z = int(round(c.cx + r * math.cos(th))), int(round(c.cz + r * math.sin(th)))
            else:
                x = int(round(c.cx - c.R - rng.uniform(2, c.wr + c.blanket + max(1, c.streak_len - 2))))
                z = c.cz + rng.choice((-1, 0, 0, 1))
            if (x, z) not in c.top or c.zone[(x, z)] == 'bowl' or any((x + a, z + b) in taken for a in (-1, 0, 1) for b in (-1, 0, 1)):
                continue
            size = rng.choice((1, 1, 2, 2, 3, 4)) if kind == 'fan' else rng.choice((1, 1, 2))
            shape = [(0, 0)] + [(1, 0), (0, 1), (1, 1)][:max(0, size - 1)]
            cells = []
            for (a, b) in shape:
                if (x + a, z + b) not in c.top:
                    continue
                top = c.top[(x + a, z + b)]
                cells.append((x + a, top, z + b))
                if (a, b) == (0, 0) and size >= 2 or size == 4 and (a, b) == (1, 1):
                    cells.append((x + a, top + 1, z + b))
            for p in cells:
                t.set(*p, shell(*p))
                used.add(p)
                taken.add((p[0], p[2]))
            if mossy:
                for p in cells:
                    above = (p[0], p[1] + 1, p[2])
                    if above not in used and rng.random() < 0.55 and c.free(*above):
                        t.set(*above, S('moss_carpet'))
            if scorch:
                for (a, b) in FOUR:
                    xx, zz = x + a, z + b
                    if (xx, zz) in c.top and (xx, zz) not in taken and rng.random() < 0.6:
                        y = c.top[(xx, zz)] + 1
                        if c.free(xx, y, zz):
                            t.set(xx, y, zz, S('or:scorch_mark'))
            break
    return used


def decals(c, rng, cells, choices, density):
    """Small things (scorch marks, debris, plants) on top of the given columns where the cell above is free."""
    t = c.t
    total = sum(w for _, w in choices)
    for (x, z) in cells:
        if rng.random() >= density:
            continue
        y = c.on_top(x, z)
        if y is None or not t.inside(x, y, z) or not c.free(x, y, z):
            continue
        r = rng.random() * total
        for st, w in choices:
            r -= w
            if r <= 0:
                t.set(x, y, z, st)
                break


def count_ore(t):
    return sum(1 for s in t.cells.values() if s.split('[')[0] in ORES)


# ---------------------------------------------------------------- variant 1: fresh

FRESH_WALL = [(S('cobblestone'), 2), (S('gravel'), 2), (S('stone'), 3), (S('tuff'), 2), (S('andesite'), 1)]
FRESH_FLOOR = [(S('gravel'), 1), (S('basalt', axis='y'), 2), (S('blackstone'), 3), (S('tuff'), 2)]
FRESH_RIM = [(S('cobblestone'), 1), (S('gravel'), 3), (S('tuff'), 2), (S('stone'), 1), (S('blackstone'), 1)]
FRESH_FLANK = [(S('tuff'), 1), (S('gravel'), 3), (S('coarse_dirt'), 2), (S('cobblestone'), 1)]
FRESH_BLANKET = [(S('gravel'), 2), (S('coarse_dirt'), 4), (S('rooted_dirt'), 1), (S('dirt'), 1)]
SCORCHED = [(S('tuff'), 1), (S('coarse_dirt'), 2), (S('blackstone'), 3), (S('basalt', axis='y'), 1), (S('gravel'), 1)]
FRESH_FAN = [(S('coarse_dirt'), 2), (S('tuff'), 1), (S('gravel'), 3), (S('cobblestone'), 1), (S('stone'), 1)]
FRESH_UNDER = [(S('dirt'), 3), (S('coarse_dirt'), 1), (S('gravel'), 1)]


def burnt_trees(c, rng, avoid):
    """Snags round the crater, burnt bare, and the trees nearest it blown down, their trunks lying outward from
    the blast beside their stumps."""
    t = c.t
    log = S('dark_oak_log', axis='y')
    spike = S('dark_oak_fence', waterlogged=False)
    placed = []
    # (angle in degrees, distance beyond the crest, standing height or 0 for one blown down)
    for deg, out, h in ((-160, 5.5, 4), (-118, 4.0, 0), (-72, 6.5, 5), (-28, 4.5, 0), (24, 5.0, 0), (62, 6.0, 3),
                        (104, 4.5, 0), (146, 6.0, 4), (176, 7.0, 2), (-45, 9.0, 3), (40, 9.5, 2)):
        th = math.radians(deg)
        r = c.crest(th) + c.wr * 0.5 + out
        x, z = int(round(c.cx + r * math.cos(th))), int(round(c.cz + r * math.sin(th)))
        if (x, z) not in c.top or c.zone[(x, z)] == 'bowl' or (x, z) in avoid:
            continue
        y = c.top[(x, z)] + 1
        if not c.free(x, y, z):
            continue
        if h:
            for k in range(h):
                t.set(x, y + k, z, log)
            if rng.random() < 0.7:
                t.set(x, y + h, z, spike)
            if h >= 3:
                a, b = rng.choice(FOUR)
                t.set(x + a, y + h - 1, z + b, spike)
        else:
            # the stump, and the trunk lying away from the crater
            t.set(x, y, z, log)
            if abs(math.cos(th)) >= abs(math.sin(th)):
                step, axis = ((1 if math.cos(th) > 0 else -1), 0), 'x'
            else:
                step, axis = (0, (1 if math.sin(th) > 0 else -1)), 'z'
            length = rng.choice((3, 4, 5))
            for k in range(1, length + 1):
                xx, zz = x + step[0] * k, z + step[1] * k
                if (xx, zz) not in c.top or abs(c.top[(xx, zz)] + 1 - y) > 1:
                    break
                yy = c.top[(xx, zz)] + 1
                t.set(xx, yy, zz, S('dark_oak_log', axis=axis) if k < length else S('dark_oak_wood', axis=axis))
                avoid.add((xx, zz))
        avoid.add((x, z))
        placed.append((x, z))
    return placed


def build_fresh():
    t = Template(57, 22, 39, seed=960701, name=TEMPLATE + 'fresh')
    rng = t.rng
    c = Crater(t, radius=8.5, depth=5, rim=2.0, flank=4.0, power=1.5, seed=7701, blanket=4.0, fan=9.0, streak=(10, 3.0))
    wall = patches(7711, FRESH_WALL, 2.0)
    floor = patches(7712, FRESH_FLOOR, 2.0)
    rim = patches(7713, FRESH_RIM, 2.5)
    flank = patches(7714, FRESH_FLANK, 2.5)
    blanket = patches(7715, FRESH_BLANKET, 3.0)
    scorched = patches(7716, SCORCHED, 2.0)
    fan = patches(7719, FRESH_FAN, 2.5)
    under = patches(7717, FRESH_UNDER, 2.0)
    bowl_rock = patches(7718, [(S('stone'), 3), (S('tuff'), 1), (S('cobblestone'), 1)], 2.0)

    def surface(x, z, zone):
        u = c.u[(x, z)]
        if zone == 'bowl':
            return floor(x, SURF, z) if u < 0.5 else wall(x, SURF, z)
        if zone == 'streak' or zone == 'blanket' and x < c.cx - c.R:
            return scorched(x, SURF, z)
        if zone == 'blanket' and c.polar(x, z)[1] ** 2 < 0.5:
            return fan(x, SURF, z)
        return {'rim': rim, 'flank': flank, 'blanket': blanket}[zone](x, SURF, z)

    def below(x, y, z, zone):
        return bowl_rock(x, y, z) if zone in ('bowl', 'rim') else under(x, y, z)

    c.ground(surface, below)

    # The meteorite, still hot: a lump in the floor, half sunk, longer along its flight.
    floor_y = SURF - c.d
    shell = patches(7721, SHELL, 1.6)
    inner = patches(7722, [(S('basalt', axis='y'), 1), (S('blackstone'), 2), (STARFALL_ROCK, 2)], 1.5)
    centre = (c.cx + 0.5, floor_y + 2.6, c.cz + 0.5)
    cells, crust, ores = meteorite(t, centre, (4.0, 3.2, 3.4), 7723, 8, STARFALL_ORE, shell, inner, exposed=2,
                                   face=(-0.6, 1.0, 0.3))
    # Magma under the shell and round its foot; a signal fire under the magma sends its smoke up through it.
    low = column_bottoms(cells)
    for (x, z), y in sorted(low.items()):
        t.set(x, y - 1, z, S('magma_block'))
    foot = set()
    for (x, z), y in sorted(low.items()):
        for (a, b) in FOUR:
            q = (x + a, z + b)
            if q not in low and q in c.top and c.zone[q] == 'bowl':
                foot.add(q)
    for (x, z) in sorted(foot):
        if rng.random() < 0.55:
            t.set(x, c.top[(x, z)], z, S('magma_block'))
    by = low[(c.cx, c.cz)]
    t.set(c.cx, by - 2, c.cz, S('campfire', facing='north', lit=True, signal_fire=True, waterlogged=False))
    t.set(c.cx, by - 3, c.cz, S('hay_block', axis='y'))
    # Two vents in the floor beside it: campfires under a block of rock, smoking out of the ground.
    for (a, b) in ((4, -3), (-3, 4)):
        x, z = c.cx + a, c.cz + b
        if (x, z) in c.top and (x, z) not in low:
            top = c.top[(x, z)]
            t.set(x, top, z, S('tuff'))
            t.set(x, top - 1, z, S('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
    # The scorched streak and the blast.
    taken = {(x, z) for (x, _, z) in cells} | foot
    frags = fragments(c, rng, 13, shell, spread=0.55, streak=3, scorch=True, avoid=taken)
    taken |= {(x, z) for (x, _, z) in frags}
    burnt_trees(c, rng, taken)
    streak_cols = [p for p, zn in sorted(c.zone.items()) if zn == 'streak' or zn == 'blanket' and p[0] < c.cx - c.R]
    decals(c, rng, streak_cols, [(S('or:scorch_mark'), 5), (S('or:debris'), 1)], 0.6)
    bowl_cols = [p for p, zn in sorted(c.zone.items()) if zn == 'bowl' and p not in taken]
    decals(c, rng, bowl_cols, [(S('or:scorch_mark'), 4), (S('or:debris'), 2)], 0.22)
    outer = [p for p, zn in sorted(c.zone.items()) if zn in ('rim', 'flank', 'blanket') and p not in taken and p[0] >= c.cx - c.R]
    decals(c, rng, outer, [(S('or:scorch_mark'), 4), (S('or:debris'), 1), (S('dead_bush'), 1)], 0.12)
    inset = c.clear_inset()
    c.air(inset)
    return t, inset


# ---------------------------------------------------------------- variant 2: old

OLD_TURF = [(S('podzol', snowy=False), 1), (S('grass_block', snowy=False), 8), (S('moss_block'), 2), (S('coarse_dirt'), 1)]
OLD_SHORE = [(S('clay'), 1), (S('mud'), 2), (S('grass_block', snowy=False), 2), (S('gravel'), 1)]
OLD_BED = [(S('sand'), 1), (S('mud'), 2), (S('clay'), 2), (S('gravel'), 1)]
OLD_UNDER = [(S('rooted_dirt'), 1), (S('dirt'), 5), (S('coarse_dirt'), 1)]
CANE_GROUND = ('minecraft:grass_block', 'minecraft:mud', 'minecraft:sand', 'minecraft:dirt')
OLD_PLANTS = [(S('short_grass'), 14), (S('fern'), 4), (S('dandelion'), 1), (S('poppy'), 1), (S('azure_bluet'), 1), (S('bush'), 3)]
OLD_SCARP = [(S('mossy_cobblestone'), 2), (S('stone'), 2), (S('moss_block'), 1), (S('cobblestone'), 1)]


def build_old():
    t = Template(55, 20, 41, seed=960702, name=TEMPLATE + 'old')
    rng = t.rng
    c = Crater(t, radius=10.5, depth=4, rim=1.0, flank=5.0, power=2.0, seed=7801, blanket=2.0, fan=7.0, rough=0.9)
    water_y = SURF - 2
    turf = patches(7811, OLD_TURF, 3.0)
    shore = patches(7812, OLD_SHORE, 2.0)
    bed = patches(7813, OLD_BED, 2.0)
    under = patches(7814, OLD_UNDER, 2.5)
    scarp = patches(7815, OLD_SCARP, 1.5)

    def wet(x, z):
        return c.top[(x, z)] < water_y

    def surface(x, z, zone):
        top = c.top[(x, z)]
        if zone == 'bowl' and top < water_y:
            return bed(x, top, z)
        if zone == 'bowl' and any((x + a, z + b) in c.top and wet(x + a, z + b) for a, b in FOUR):
            return shore(x, top, z)
        if zone in ('bowl', 'rim') and max(c.top.get((x + a, z + b), top) for a, b in FOUR) - top >= 2:
            return scarp(x, top, z)
        return turf(x, top, z)

    c.ground(surface, lambda x, y, z, zone: under(x, y, z))

    # The meteorite: a mossy island in the pond, a little west of the middle.
    floor_y = SURF - c.d
    shell = patches(7821, SHELL, 1.6)
    inner = patches(7822, [(S('basalt', axis='y'), 1), (STARFALL_ROCK, 3), (S('blackstone'), 1)], 1.5)
    centre = (c.cx - 0.5, floor_y + 1.6, c.cz + 0.5)
    cells, crust, ores = meteorite(t, centre, (3.3, 2.5, 2.8), 7823, 6, STARFALL_ORE, shell, inner)
    # The prospector's notch in its east side, a step above the water: three ore blocks show in its walls.
    east = max(x for (x, y, z) in cells if z == c.cz and y == water_y + 1)
    notch = [(east + a, y, z) for (a, y) in ((0, water_y + 1), (0, water_y + 2), (-1, water_y + 1)) for z in (c.cz, c.cz + 1)]
    notch = [p for p in notch if p in cells]
    for p in notch:
        t.set(*p, AIR)
        cells.discard(p)
        ores.discard(p)
    faces = sorted({q for p in notch for q in six(p) if q in cells and q not in ores and q[1] > water_y},
                   key=lambda q: (q[0], q[1], q[2]))
    for q in faces[:3]:
        t.set(*q, STARFALL_ORE)
        ores.add(q)
    tops = column_tops(cells)
    # Moss over the shell's top.
    for (x, z), y in sorted(tops.items()):
        p = (x, y, z)
        if p in ores or p in notch:
            continue
        if rng.random() < 0.55:
            t.set(x, y, z, S('moss_block'))
            if rng.random() < 0.5 and t.get(x, y + 1, z) is None:
                t.set(x, y + 1, z, S('moss_carpet'))
    # The pond: water over every bowl column below the water line, round the island.
    for (x, z), top in sorted(c.top.items()):
        if c.zone[(x, z)] == 'bowl' and top < water_y:
            for y in range(top + 1, water_y + 1):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, S('water', level=0))
    _contain_pond(c, water_y)
    # The plank walk from the east shore to the notch.
    walk = []
    x = east + 1
    while x < t.sx and (x, c.cz) in c.top and c.top[(x, c.cz)] < water_y:
        for z in (c.cz, c.cz + 1):
            if t.name_at(x, water_y, z) == 'minecraft:water':
                t.set(x, water_y, z, S('spruce_slab', type='top', waterlogged=True))
                walk.append((x, z))
        x += 1
    shore_x = x
    # Water plants: lily pads, seagrass where it is deeper, sugar cane on the shore.
    for (x, z), top in sorted(c.top.items()):
        if c.zone[(x, z)] != 'bowl':
            continue
        if top < water_y and (x, z) not in walk:
            if t.name_at(x, water_y, z) == 'minecraft:water' and rng.random() < 0.12 and t.get(x, water_y + 1, z) in (None, AIR):
                t.set(x, water_y + 1, z, S('lily_pad'))
            elif top < water_y - 1 and rng.random() < 0.3 and t.name_at(x, top + 1, z) == 'minecraft:water':
                t.set(x, top + 1, z, S('seagrass'))
        elif top == water_y and t.name_at(x, top, z) in CANE_GROUND and any(t.name_at(x + a, water_y, z + b) == 'minecraft:water'
                                                                              for a, b in FOUR) and rng.random() < 0.3:
            for k in range(rng.choice((1, 2, 2, 3))):
                if t.get(x, top + 1 + k, z) in (None, AIR):
                    t.set(x, top + 1 + k, z, S('sugar_cane', age=0))
    camp = prospector_camp(c, rng, shore_x)
    taken = {(x, z) for (x, _, z) in cells} | set(walk) | camp
    fragments(c, rng, 11, shell, spread=0.6, mossy=True, avoid=taken)
    grass = [p for p, zn in sorted(c.zone.items()) if t.name_at(p[0], c.top[p], p[1]) in ('minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block')]
    decals(c, rng, grass, OLD_PLANTS, 0.2)
    inset = c.clear_inset()
    c.air(inset)
    return t, inset


def _contain_pond(c, water_y):
    """Raises any dry neighbour of a water cell to the water line, so the pond holds."""
    t = c.t
    for _ in range(3):
        changed = False
        for (x, z), top in sorted(c.top.items()):
            if t.name_at(x, water_y, z) != 'minecraft:water':
                continue
            for a, b in FOUR:
                q = (x + a, z + b)
                if not t.inside(q[0], water_y, q[1]):
                    continue
                nb = t.get(q[0], water_y, q[1])
                if nb is None or nb == AIR:
                    for y in range(c.top.get(q, water_y - 3) + 1, water_y + 1):
                        t.set(q[0], y, q[1], S('mud'))
                    if q in c.top:
                        c.top[q] = max(c.top[q], water_y)
                    changed = True
        if not changed:
            break


TROD = ('minecraft:grass_block', 'minecraft:podzol', 'minecraft:coarse_dirt', 'minecraft:moss_block')


def prospector_camp(c, rng, shore_x):
    """The prospector's camp on the east rim: the fallen lean-to, the dead fire in its ring of stones, the chipped
    anvil, the empty barrel and the rain cauldron, and the path down to the plank walk. Returns its columns."""
    t = c.t
    used = set()
    cx = int(round(c.cx + c.crest(0.0) + 2.5))
    cz = c.cz + 3

    def at(x, z):
        return c.on_top(x, z)

    def put(x, z, st, dy=0):
        y = at(x, z)
        if y is None:
            return None
        t.set(x, y + dy, z, st)
        used.add((x, z))
        return y

    # the dead fire and its ring of stones
    put(cx, cz, S('campfire', facing='north', lit=False, signal_fire=False, waterlogged=False))
    for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1)):
        x, z = cx + a, cz + b
        if (x, z) in c.top:
            t.set(x, c.top[(x, z)], z, pick(rng, [(S('cobblestone'), 2), (S('mossy_cobblestone'), 3)]))
            used.add((x, z))
    # the lean-to, fallen: one post still up, the other down, the canvas slumped over the ground
    fence = S('spruce_fence', waterlogged=False)
    y = put(cx + 3, cz + 2, fence)
    if y is not None:
        t.set(cx + 3, y + 1, cz + 2, fence)
    put(cx + 3, cz - 1, S('stripped_spruce_log', axis='z'))
    put(cx + 3, cz - 2, S('stripped_spruce_log', axis='z'))
    for (a, b) in ((4, 0), (4, 1), (5, 0), (5, 1), (4, -1)):
        put(cx + a, cz + b, S('or:canvas_slab', type='bottom', waterlogged=False))
    # the anvil, the barrel, the cauldron
    put(cx - 1, cz + 3, S('chipped_anvil', facing='north'))
    put(cx + 1, cz + 3, S('barrel', facing='up', open=False))
    put(cx + 2, cz + 4, S('water_cauldron', level=1))
    # spoil from the notch, dumped above the shore beside the walk
    for (a, b) in ((0, -2), (1, -2), (1, -3)):
        x, z = shore_x + a, c.cz + b
        if (x, z) in c.top and c.top[(x, z)] >= SURF - 2:
            put(x, z, pick(rng, [(S('cobblestone'), 2), (STARFALL_ROCK, 1), (S('gravel'), 1)]))
    # the path trodden from the fire down to the walk
    x0, z0, x1, z1 = cx - 2, cz - 1, shore_x, c.cz
    n = max(abs(x1 - x0), abs(z1 - z0), 1)
    for k in range(n + 1):
        x, z = int(round(x0 + (x1 - x0) * k / n)), int(round(z0 + (z1 - z0) * k / n))
        if (x, z) in c.top and (x, z) not in used and t.name_at(x, c.top[(x, z)], z) in TROD and c.free(x, c.top[(x, z)] + 1, z):
            t.set(x, c.top[(x, z)], z, S('dirt_path'))
            used.add((x, z))
    return used


# ---------------------------------------------------------------- variant 3: deep

DEEP_SOIL = [(S('grass_block', snowy=False), 2), (S('stone'), 3), (S('coarse_dirt'), 2), (S('andesite'), 1), (S('gravel'), 1)]
DEEP_UPPER = [(S('cobblestone'), 1), (S('andesite'), 2), (S('stone'), 4), (S('gravel'), 1), (S('diorite'), 1)]
DEEP_LOWER = [(S('stone'), 2), (S('tuff'), 2), (S('cobbled_deepslate'), 2), (S('deepslate', axis='y'), 2)]
DEEP_FLOOR = [(S('tuff'), 1), (S('gravel'), 2), (S('cobbled_deepslate'), 3), (S('deepslate', axis='y'), 1)]
DEEP_FLANK = [(S('stone'), 2), (S('coarse_dirt'), 2), (S('grass_block', snowy=False), 4), (S('andesite'), 1), (S('gravel'), 1)]


def build_deep():
    t = Template(45, 22, 33, seed=960703, name=TEMPLATE + 'deep')
    rng = t.rng
    c = Crater(t, radius=7.5, depth=6, rim=3.0, flank=4.0, power=2.0, seed=7901, blanket=2.0, fan=6.0)
    floor_y = SURF - c.d
    path = spiral_path(c)
    soil = patches(7911, DEEP_SOIL, 2.5)
    upper = patches(7912, DEEP_UPPER, 2.0)
    lower = patches(7913, DEEP_LOWER, 2.0)
    bed = patches(7914, DEEP_FLOOR, 2.0)
    flank = patches(7915, DEEP_FLANK, 2.5)
    trod = patches(7916, [(S('gravel'), 1), (S('dirt_path'), 4), (S('coarse_dirt'), 1)], 1.5)

    def strata(x, y, z):
        """The crater's wall shows the ground's broken layers: rock pushed up into the rim, deepslate low down."""
        if y >= SURF - 3:
            return upper(x, y, z)
        return lower(x, y, z)

    def surface(x, z, zone):
        top = c.top[(x, z)]
        if (x, z) in path:
            return trod(x, top, z)
        if zone == 'bowl':
            return bed(x, top, z) if top <= floor_y + 1 else strata(x, top, z)
        if zone == 'rim':
            return soil(x, top, z)
        return flank(x, top, z)

    def below(x, y, z, zone):
        return strata(x, y, z) if zone in ('bowl', 'rim') else (S('dirt') if y >= SURF - 1 else upper(x, y, z))

    c.ground(surface, below)
    # rail posts on the path's open side where it drops away
    for (x, z) in sorted(path):
        y = c.top[(x, z)]
        lowest = min((c.top.get((x + a, z + b), y) for a, b in FOUR))
        if y - lowest >= 2 and path[(x, z)] % 3 == 0:
            t.set(x, y + 1, z, S('spruce_fence', waterlogged=False))

    # The meteorite, mostly buried: a dome of its shell in the floor, the core deep under it.
    shell = patches(7921, SHELL, 1.6)
    core_rock = S('deepslate', axis='y')
    centre = (c.cx + 0.5, floor_y - 1.5, c.cz + 0.5)
    cells, crust, ores = meteorite(t, centre, (4.2, 4.2, 4.0), 7923, 0, DEEPSLATE_ORE, shell, lambda x, y, z: core_rock)
    dome = column_tops(cells)
    top_y = max(dome[(c.cx + a, c.cz + b)] for a in (0, 1) for b in (0, 1))
    # The core: a chamber at the bottom of the shaft, south of the ladder, in deepslate studded with ore.
    chamber = [(c.cx + a, y, c.cz + b) for a in (-1, 0, 1) for b in (0, 1, 2) for y in (floor_y - 4, floor_y - 3)]
    shaft = [(c.cx + a, y, c.cz + b) for a in (0, 1) for b in (0, 1) for y in range(floor_y - 2, top_y + 1)]
    hollow = set(chamber) | set(shaft)
    for p in chamber + shaft:
        t.set(*p, AIR)
    hub = (c.cx + 0.5, c.cz + 1.5)
    walls = sorted({q for p in chamber for q in six(p) if q in cells and q not in hollow and q[1] in (floor_y - 4, floor_y - 3)},
                   key=lambda q: (math.atan2(q[2] + 0.5 - hub[1], q[0] + 0.5 - hub[0]), q[1], q))
    seen = [walls[int(i * len(walls) / 5)] for i in range(5)]
    behind = {q for p in hollow for q in six(p)}
    hidden = sorted((p for p in cells if p not in crust and p not in behind and p not in hollow),
                    key=lambda p: ((p[0] + 0.5 - hub[0]) ** 2 + (p[1] + 0.5 - (floor_y - 3)) ** 2 + (p[2] + 0.5 - hub[1]) ** 2, p))
    for p in seen + hidden[:7]:
        t.set(*p, DEEPSLATE_ORE)
    t.set(c.cx - 1, floor_y - 4, c.cz + 2, S('lantern', hanging=False, waterlogged=False))
    # The ladder up the shaft's north wall, from the chamber floor to the top of the dome.
    for y in range(floor_y - 4, top_y + 1):
        t.set(c.cx, y, c.cz, S('ladder', facing='south', waterlogged=False))
        if t.get(c.cx, y, c.cz - 1) in (None, AIR):
            t.set(c.cx, y, c.cz - 1, STARFALL_ROCK)
    # The headframe over the shaft: two posts on the dome, a beam, the chain down the shaft and its lantern.
    beam = top_y + 4
    fence = S('spruce_fence', waterlogged=False)
    for x in (c.cx - 1, c.cx + 2):
        foot = dome.get((x, c.cz), c.top[(x, c.cz)])
        for y in range(foot + 1, beam):
            t.set(x, y, c.cz, fence)
    for x in range(c.cx - 1, c.cx + 3):
        t.set(x, beam, c.cz, S('spruce_log', axis='x'))
    for y in range(floor_y - 2, beam):
        t.set(c.cx + 1, y, c.cz, S('iron_chain', axis='y', waterlogged=False))
    t.set(c.cx + 1, floor_y - 3, c.cz, S('lantern', hanging=True, waterlogged=False))
    # The spoil heap west of the dome.
    for (a, b, h) in ((-6, 0, 2), (-6, 1, 2), (-7, 0, 1), (-6, -1, 1), (-5, 2, 1), (-7, 1, 1), (-5, -1, 1)):
        x, z = c.cx + a, c.cz + b
        if (x, z) in c.top and (x, z) not in path and (x, z) not in dome:
            y = c.top[(x, z)] + 1
            for k in range(h):
                if c.free(x, y + k, z):
                    t.set(x, y + k, z, pick(rng, [(S('cobbled_deepslate'), 3), (S('gravel'), 2), (STARFALL_ROCK, 1), (S('tuff'), 1)]))
    taken = {(x, z) for (x, _, z) in cells} | set(path)
    taken |= probe_wreck(c, rng)
    fragments(c, rng, 10, shell, spread=0.55, avoid=taken)
    # boulders of the pushed-up strata on the rim
    for (x, z), zn in sorted(c.zone.items()):
        if zn in ('rim', 'flank') and (x, z) not in taken and rng.random() < 0.07:
            y = c.top[(x, z)] + 1
            if c.free(x, y, z):
                t.set(x, y, z, pick(rng, [(S('stone'), 3), (S('andesite'), 2), (S('cobblestone'), 1), (S('mossy_cobblestone'), 1)]))
    plants = [p for p, zn in sorted(c.zone.items()) if zn in ('flank', 'blanket') and t.name_at(p[0], c.top[p], p[1]) == 'minecraft:grass_block']
    decals(c, rng, plants, [(S('short_grass'), 6), (S('fern'), 2), (S('bush'), 1)], 0.35)
    inset = c.clear_inset()
    c.air(inset)
    return t, inset


def spiral_path(c):
    """The prospectors' path: from the rim on the north side it winds clockwise down the wall to the floor beside
    the meteorite, two blocks wide, never more than one block up or down between steps. Moves the columns' tops;
    returns {column: step number}."""
    th0, th1 = math.radians(-100), math.radians(55)
    r0, r1 = c.crest(th0) + 0.6, 4.6
    h0 = c.top.get((int(round(c.cx + r0 * math.cos(th0))), int(round(c.cz + r0 * math.sin(th0)))), SURF + 2)
    h1 = SURF - c.d
    n = 160
    steps = {}
    prev = None
    k = 0
    for i in range(n + 1):
        f = i / n
        th = th0 + (th1 - th0) * f
        r = r0 + (r1 - r0) * f
        h = int(math.floor(h0 + (h1 - h0) * f + 0.5))
        for w in (-0.5, 0.5):
            x = int(round(c.cx + (r + w) * math.cos(th)))
            z = int(round(c.cz + (r + w) * math.sin(th)))
            if (x, z) in steps or (x, z) not in c.top:
                continue
            if prev is not None:
                h = max(min(h, prev + 1), prev - 1)
            steps[(x, z)] = k
            c.set_top(x, z, h)
            prev = h
            k += 1
    return steps


def probe_wreck(c, rng):
    """An Imperial probe droid, crashed on the south rim: its black ball body half sunk in a short skid of scorched
    ground, the red eye still lit, two antennae bent and its legs splayed. Returns its columns."""
    t = c.t
    th = math.radians(110)
    r = c.crest(th) + c.wr * 0.6
    x0, z0 = int(round(c.cx + r * math.cos(th))), int(round(c.cz + r * math.sin(th)))
    if (x0, z0) not in c.top:
        return set()
    y0 = c.top[(x0, z0)]
    body = []
    for a in (-1, 0, 1):
        for b in (0, 1, 2):
            for d in (-1, 0, 1):
                if abs(a) + abs(b - 1) + abs(d) <= 2:
                    body.append((x0 + a, y0 + b, z0 + d))
    for p in body:
        t.set(*p, S('black_concrete') if (p[0] + p[1] + p[2]) % 3 else S('or:imperial_plating'))
    t.set(x0, y0 + 1, z0 - 2, S('or:light_panel_red'))            # the eye, toward the crater
    t.set(x0, y0 + 3, z0, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(x0 + 1, y0 + 2, z0 + 1, S('end_rod', facing='east'))
    t.set(x0 - 2, y0 + 1, z0, S('lightning_rod', facing='west', powered=False, waterlogged=False))
    for (a, d) in ((2, 1), (-2, 2), (1, 2), (2, -1)):
        x, z = x0 + a, z0 + d
        if (x, z) in c.top:
            t.set(x, c.top[(x, z)] + 1, z, S('iron_chain', axis='x' if abs(a) > abs(d) else 'z', waterlogged=False))
    used = {(p[0], p[2]) for p in body}
    # the skid it dug coming in from the east
    for k in range(2, 7):
        x, z = x0 + k, z0 + (1 if k > 4 else 0)
        if (x, z) in c.top and (x, z) not in used:
            top = c.top[(x, z)]
            t.set(x, top, z, pick(rng, [(S('coarse_dirt'), 2), (S('gravel'), 1)]))
            if rng.random() < 0.6 and c.free(x, top + 1, z):
                t.set(x, top + 1, z, pick(rng, [(S('or:scorch_mark'), 3), (S('or:debris'), 1)]))
            used.add((x, z))
    return used


# ---------------------------------------------------------------- main

VARIANTS = (('fresh', build_fresh), ('old', build_old), ('deep', build_deep))


def build_all(preview=None):
    """Builds and saves the three templates; returns {name: (template height, clear inset)} for the worldgen."""
    out = {}
    for name, build in VARIANTS:
        t, inset = build()
        print('%s: %s, %d cells not air, %d starfall ore, clear_inset %d' % (name, t.size, t.count(), count_ore(t), inset))
        save(t, TEMPLATE + name, preview)
        out[name] = (t.sy, inset)
    return out


def main():
    args = cli(__doc__)
    build_all(args.preview)


if __name__ == '__main__':
    main()
