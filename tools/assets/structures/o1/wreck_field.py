"""The wreck field (worker O1): a Republic frigate that came down out of the sky, broke its back and drove
its bow into the earth, with the scavengers' den in its stern, the sealed hold with its hyperdrive, the
salvage crew's camp up the track and the furrows it ploughed.

    hull    the fresh wreck: still smoking, streaked by re-entry, the bow nose-down in the mound it pushed up
    hull_2  the old wreck: decades in the field, rusted and overgrown, the engine block stripped to its ribs,
            the bow rolled onto its side in the turf

Both hulls keep the hatch puzzle of the old hull cell for cell (veiled hatch, counterweight, seals, pull
anchor, locker, pedestal), moved as one block by KIT.

    python3 tools/assets/structures/o1/wreck_field.py [--only hull] [--preview DIR]
"""
import math

import common as C
from common import S, mod, Noise, stairs, slab

HS = (71, 26, 31)
BUR = 4          # buried: the stand is local y 4
G = BUR - 1      # ground top
ST = BUR
ZC = 15          # the hull's axis
KIT = (10, 0, 6)  # the old hull's puzzle cells move by this, all together
MECH = ('oldrepublic:veiled_stone', 'oldrepublic:counterweight_stone', 'oldrepublic:puzzle_seal',
        'oldrepublic:pull_anchor', 'oldrepublic:holocron_pedestal')
JIG_PATH = (19, ST, 0)
JIG_WEST = (0, ST, ZC)
JIG_EAST = (70, ST, ZC)
XS, XE = 3, 46   # the stern's end cap, the last bulkhead
DIRS4 = ('north', 'south', 'east', 'west')

ENGINE = (7.4, 8.4, 9.9, 3.2)   # (yc, a, b, p) of the engine block's section
BODY = (6.4, 7.4, 8.6, 2.2)     # and of the body's


def se(dz, dy, b, a, p):
    if a <= 0.05 or b <= 0.05:
        return 9.0
    return (abs(dz) / b) ** p + (abs(dy) / a) ** p


def sect(x):
    """(yc, a, b, p) of the outer section at x: the engine block (x 3..12) is broader and squarer than the
    body (x 16..); they blend over x 13..15."""
    if x <= 12:
        return ENGINE
    if x >= 16:
        return BODY
    f = (x - 12) / 4.0
    return tuple(e + (b - e) * f for e, b in zip(ENGINE, BODY))


def layer(x, y, z):
    """Which layer of the main body a cell is in: None (outside), 'skin', 'wall' or 'in'."""
    yc, a, b, p = sect(x)
    dz, dy = z - ZC, y - yc
    if se(dz, dy, b, a, p) > 1:
        return None
    if se(dz, dy, b - 1, a - 1, p) > 1:
        return 'skin'
    if se(dz, dy, b - 2, a - 2, p) > 1:
        return 'wall'
    return 'in'


def kit_cells():
    """The old hull's puzzle: every cell within one of a veiled stone, the counterweight, a seal, the pull
    anchor or the pedestal (the locker chest is next to its seal), moved by KIT. {cell: (state, nbt)}."""
    old = C.load_base('wreck_field/hull')
    out = {}
    for p in C.neighbourhood(old, MECH, radius=1):
        q = (p[0] + KIT[0], p[1] + KIT[1], p[2] + KIT[2])
        out[q] = (old.cells.get(p), old.nbt.get(p))
    return out


class Wreck:
    def __init__(self, seed, variant):
        self.v = variant
        self.fresh = variant == 1
        self.tid = 'wreck_field/hull' + ('' if variant == 1 else '_2')
        self.t = C.Template(*HS, seed=seed, name=self.tid)
        self.rng = self.t.rng
        self.seed = seed
        self.n1 = Noise(seed + 11, 3.0)
        self.n2 = Noise(seed + 12, 1.6)
        self.n3 = Noise(seed + 13, 6.0)
        self.n4 = Noise(seed + 14, 1.0)
        self.kit = kit_cells()
        self.ground = {}
        self.cells_in = set()
        self.fires = [(2, 9, ZC, 6.0), (33, 13, ZC, 5.0), (48, 9, ZC, 5.5), (64, 5, ZC, 7.0)]

    def doors(self):
        """The puzzle's doors, open (the veiled hatch, the seals): for the route check."""
        return {p for p, (st, nb) in self.kit.items()
                if st and C.name(st) in ('oldrepublic:veiled_stone', 'oldrepublic:puzzle_seal')}

    # ------------------------------------------------------------ materials

    def burn(self, x, y, z):
        """How scorched a skin cell is: re-entry streaks running back from the bow along the belly and the
        flanks, and fire round the engines, the roof breach, the break and the nose."""
        b = 0.0
        for (sx, sy, sz, r) in self.fires:
            d2 = ((x - sx) ** 2 + (y - sy) ** 2 * 1.3 + (z - sz) ** 2) / (r * r)
            b = max(b, math.exp(-d2))
        yc, a, bb, p = sect(min(x, 51))
        th = math.atan2(y - yc, z - ZC)
        lane = self.n4.at(th * 9.0, 0.0, 0.0)
        front = max(0.0, min(1.0, (x - 3) / 60.0))
        low = max(0.0, -(y - yc) / a)
        s = (lane - 0.5) * 2.4 + 0.55 * front + 0.6 * low - 0.45 + 0.35 * (self.n2.at(x * 0.25, y, z) - 0.5)
        b = max(b, s)
        return b + 0.2 * (self.n1.at(x, y, z) - 0.5)

    def panel(self, x, y, z, yc=None):
        """The plate a skin cell belongs to (plates four cells long, about twenty round the hull): mostly the
        white paint in some stretches, mostly bare metal in others."""
        if yc is None:
            yc = sect(min(max(x, XS), 51))[0]
        th = math.atan2(y - yc, z - ZC)
        k0, k1 = x // 4, int((th + math.pi) * 3.3)
        h = self.n1._h(k0, k1, 5)
        pw = 0.25 + 0.6 * self.n3.at(x * 0.7, y * 0.7, z * 0.7)
        if h < pw:
            return 'or:republic_plating'
        h2 = self.n1._h(k0, k1, 6)
        if h2 < 0.62:
            return 'or:durasteel_plating'
        if h2 < 0.92:
            return 'or:wreck_plating'
        return 'or:wreck_plating_scorched' if self.fresh else 'or:rusted_plating'

    def skin(self, x, y, z, band=True):
        """The outer plating: plates of paint and bare metal, the red livery band on the flanks; scorch and
        soot where it burned (a fresh wreck) or rust where the weather got in (an old one)."""
        m = self.n2.at(x, y * 1.5, z)
        side = abs(z - ZC) >= 6
        if self.fresh:
            bb = self.burn(x, y, z)
            if bb > 0.9:
                return 'minecraft:polished_blackstone' if m < 0.5 else 'minecraft:blackstone'
            if bb > 0.62:
                return 'or:wreck_plating_scorched'
        else:
            r = self.n3.at(x, y, z) + 0.3 * (m - 0.5) + (0.12 if y >= 9 else 0.0)
            if r > 0.64:
                return 'or:rusted_plating'
        if band and side and y in (8, 9) and x >= 13 and m > 0.18:
            return 'minecraft:red_terracotta' if (x + y) % 9 else 'minecraft:red_concrete'
        return self.panel(x, y, z)

    def wall(self, x, y, z):
        """The inner wall: durasteel with ribs every fourth cell and conduits."""
        if x % 4 == 0:
            return S('or:durasteel_beam', axis='y') if abs(z - ZC) >= 4 else S('or:durasteel_beam', axis='z')
        r = self.n2.at(x, y, z)
        if r < 0.16:
            return 'or:wreck_conduit'
        if r > 0.84:
            return 'or:durasteel_vent'
        return 'or:durasteel_plating' if r < 0.62 else 'or:wreck_plating'

    def deck(self, x, z):
        if abs(z - ZC) <= 1 and x % 6 == 3:
            return 'or:durasteel_grate'
        r = self.n2.at(x * 2, 3, z * 2)
        if r > 0.86:
            return 'or:wreck_plating'
        return 'or:durasteel_deck'

    def soil(self, x, y, z, top):
        """Turned earth: the crash ploughed it, so the top is dirt and gravel more than grass (fresh), or
        grown over again (old)."""
        r = self.n2.at(x * 1.7, y, z * 1.7)
        if not top:
            return 'minecraft:dirt' if r < 0.62 else ('minecraft:coarse_dirt' if r < 0.85 else 'minecraft:gravel')
        if self.fresh:
            churn = self.churn(x, z)
            if churn > 0.5:
                if r < 0.38:
                    return 'minecraft:coarse_dirt'
                if r < 0.62:
                    return 'minecraft:dirt'
                if r < 0.78:
                    return 'minecraft:gravel'
                if r < 0.88:
                    return 'minecraft:rooted_dirt'
                return 'minecraft:grass_block'
            return 'minecraft:coarse_dirt' if r < 0.12 else 'minecraft:grass_block'
        if r < 0.1:
            return 'minecraft:coarse_dirt'
        if r < 0.18:
            return 'minecraft:moss_block'
        return 'minecraft:grass_block'

    def churn(self, x, z):
        """0..1: how torn up the ground is (the skid along the hull, the mound, the furrow)."""
        d = 0.0
        if 5 <= z <= 25:
            d = 1.0
        else:
            d = max(0.0, 1.0 - (min(abs(z - 5), abs(z - 25)) - 1) / 4.0)
        d += 0.4 * (self.n3.at(x, 9, z) - 0.5)
        if x < 3:
            d = max(d, 1.0 - abs(z - ZC) / 6.0)
        return d

    # ------------------------------------------------------------ ground

    def heights(self):
        """The ground: flat at the stand, a furrow at the stern, berms the skid threw up along both flanks
        (higher on the south), and the mound the bow drove up (fresh) or lies on (old)."""
        h = {}
        for x in range(HS[0]):
            for z in range(HS[2]):
                y = float(G)
                ramp = min(1.0, max(0.0, (x - 1) / 12.0)) * min(1.0, max(0.0, (68 - x) / 6.0))
                y += 4.4 * ramp * math.exp(-((z - 27.0) / 2.2) ** 2) * (0.7 + 0.6 * self.n3.at(x, 0, z))
                gap = 1.0 - math.exp(-((x - 19.0) / 3.2) ** 2)
                y += 2.6 * ramp * gap * math.exp(-((z - 3.2) / 1.7) ** 2) * (0.6 + 0.8 * self.n3.at(x, 5, z))
                if self.fresh:
                    k = math.exp(-((x - 65.0) / 2.9) ** 2) * math.exp(-((z - ZC) / 6.0) ** 2)
                    y += 6.2 * k * (0.85 + 0.3 * self.n2.at(x, 2, z))
                    for zw in (6.5, 23.5):
                        y += 2.6 * math.exp(-((x - 62.0) / 3.0) ** 2) * math.exp(-((z - zw) / 2.2) ** 2)
                else:
                    k = math.exp(-((x - 61.0) / 6.0) ** 2) * math.exp(-((z - 23.0) / 4.0) ** 2)
                    y += 2.6 * k
                if x < 5:
                    y -= 0.9 * math.exp(-((z - ZC) / 4.0) ** 2) * (5 - x) / 5.0
                h[(x, z)] = int(round(y))
        for (x, z) in ((0, ZC), (1, ZC), (70, ZC), (69, ZC), (JIG_PATH[0], 0), (JIG_PATH[0], 1)):
            h[(x, z)] = G
        self.ground = h
        return h

    def lay_ground(self):
        t = self.t
        h = self.heights()
        for (x, z), top in h.items():
            for y in range(0, top + 1):
                t.set(x, y, z, self.soil(x, y, z, y == top))
        C.air_up(t, 0, 0, HS[0] - 1, HS[2] - 1, 1)

    # ------------------------------------------------------------ the main body

    def body(self):
        """The engine block (x 3..12) and the body to the last bulkhead (x 46); its torn end to x 51 with the
        ribs sticking out of it."""
        t = self.t
        for x in range(XS, 52):
            for y in range(0, HS[1]):
                for z in range(HS[2]):
                    L = layer(x, y, z)
                    if L is None:
                        continue
                    if x > XE:   # the break: ragged plating, less of it the further it goes
                        if L == 'in' and y > G:
                            continue
                        keep = self.n2.at(x, y, z) + 0.6 * self.n3.at(x, y * 2, z) - 0.24 * (x - XE)
                        if y > G - 1 and keep < 0.4:
                            if L == 'skin' and (y + z) % 4 == 0 and x <= XE + 3:
                                t.set(x, y, z, S('or:durasteel_beam', axis='x'))
                            continue
                    if x == XS and L in ('in', 'wall'):
                        L = 'skin'
                    if L == 'skin':
                        t.set(x, y, z, self.skin(x, y, z) if y >= G - 1 else 'or:wreck_plating')
                    elif L == 'wall':
                        t.set(x, y, z, self.wall(x, y, z) if y > G else 'or:durasteel_plating')
                    elif y < G:
                        t.set(x, y, z, 'or:wreck_plating' if (x + z) % 3 else 'minecraft:gravel')
                    elif y == G:
                        t.set(x, y, z, self.deck(x, z))
                    else:
                        t.set(x, y, z, C.AIR)
                        self.cells_in.add((x, y, z))

    def bulkhead(self, x, openings=()):
        """A bulkhead across the interior at x: plating framed by beams. openings: (y, z) left open."""
        t = self.t
        for y in range(ST, HS[1]):
            for z in range(HS[2]):
                if (x, y, z) not in self.cells_in or (y, z) in openings:
                    continue
                if abs(z - ZC) >= 5 and z % 3 == 0:
                    t.set(x, y, z, S('or:durasteel_beam', axis='y'))
                elif (y - ST) % 4 == 3:
                    t.set(x, y, z, S('or:durasteel_beam', axis='z'))
                else:
                    t.set(x, y, z, 'or:durasteel_plating' if self.n2.at(x, y, z) < 0.7 else 'or:wreck_plating')
                self.cells_in.discard((x, y, z))

    def engines(self):
        """Three drive bells on the stern: soot round the rims and slag still glowing in the throats (fresh);
        cold and rusted, one cut off and dragged away by salvagers (old)."""
        t = self.t
        bells = [(11.2, ZC, 3.7), (5.6, 8.6, 3.0), (5.6, 21.4, 3.0)]
        if not self.fresh:
            bells = bells[:2]
        for i, (yc, zc, r) in enumerate(bells):
            for x in range(0, 5):
                for y in range(0, HS[1]):
                    for z in range(HS[2]):
                        d = math.hypot(y - yc, z - zc)
                        rb = r + 0.45 * max(0, 3 - x) / 3.0
                        if x <= 2:
                            if rb - 0.95 < d <= rb:      # the bell's outer skin: bare metal
                                if x == 0 and self.n2.at(y * 2, z * 2, i) > 0.72:
                                    continue
                                if self.fresh:
                                    st = 'or:durasteel_plating' if self.n2.at(x, y, z) < 0.55 else 'or:wreck_plating_scorched'
                                else:
                                    st = 'or:rusted_plating' if self.n2.at(x, y, z) < 0.6 else 'or:wreck_plating'
                                t.set(x, y, z, st)
                            elif rb - 1.9 < d <= rb - 0.95:   # its soot-black inside
                                t.set(x, y, z, ('minecraft:polished_blackstone' if (y + z) % 3 else 'minecraft:blackstone')
                                      if self.fresh else 'or:rusted_plating')
                            elif d <= rb - 1.9 and y > G:
                                t.set(x, y, z, C.AIR)
                        elif x == 3 and d <= r - 1.5 and y > G:
                            t.set(x, y, z, C.AIR)
                        elif x == 3 and d <= r - 0.5:
                            t.set(x, y, z, 'minecraft:polished_blackstone' if self.fresh else 'or:rusted_plating')
                        elif x == 4 and d <= r - 0.5:
                            hot = self.fresh and d < r - 1.6
                            t.set(x, y, z, 'minecraft:magma_block' if hot else
                                  ('minecraft:blackstone' if self.fresh else 'or:rusted_plating'))
        if self.fresh:
            # a fire still burning in the furrow under the upper bell: its smoke is the column you see first
            t.set(2, ST, 13, S('campfire', facing='east', lit=True, signal_fire=True, waterlogged=False))
            t.set(2, G, 13, 'minecraft:hay_block[axis=y]')
            t.set(1, ST, 18, 'or:debris')
        else:
            # the third bell, cut free, lies on its side south of the stern
            for x in range(2, 10):
                for y in range(ST, 10):
                    for z in range(22, 30):
                        d = math.hypot(y - (ST + 1.6), z - 26.0)
                        if x in (2, 9) and d <= 2.6:
                            if d > 1.6:
                                t.set(x, y, z, 'or:rusted_plating' if (y + z) % 3 else 'or:wreck_plating')
                        elif 1.6 < d <= 2.7:
                            t.set(x, y, z, 'or:rusted_plating' if (x + y + z) % 4 else 'minecraft:moss_block')
                        elif d <= 1.6:
                            t.set(x, y, z, C.AIR)

    def stripped(self):
        """Old wreck: the salvagers took the engine block's plating above the deck line, leaving the ribs."""
        t = self.t
        for x in range(XS + 1, 14):
            for y in range(8, HS[1]):
                for z in range(HS[2]):
                    L = layer(x, y, z)
                    if L not in ('skin', 'wall'):
                        continue
                    if x % 3 == 0:
                        t.set(x, y, z, S('or:durasteel_beam', axis='y') if abs(z - ZC) > 5 else S('or:durasteel_beam', axis='z'))
                    elif y >= 12 and abs(z - ZC) <= 6 and (x + z) % 7 == 0:
                        t.set(x, y, z, S('or:durasteel_beam', axis='x'))
                    elif self.n2.at(x, y, z) > 0.78 and y < 11:
                        t.set(x, y, z, 'or:rusted_plating')
                    else:
                        t.set(x, y, z, C.AIR)
                        self.cells_in.add((x, y, z))

    def tower(self):
        """The bridge tower on the spine over the stern compartment: three stepped decks with a band of
        viewports, a mast on top (bent over by the impact, or fallen)."""
        t = self.t
        levels = [(14, 13, 22, 4), (15, 13, 22, 4), (16, 14, 22, 3), (17, 15, 22, 3), (18, 15, 21, 3), (19, 16, 20, 2)]
        for (y, x0, x1, hw) in levels:
            for x in range(x0, x1 + 1):
                for z in range(ZC - hw, ZC + hw + 1):
                    corner = (x in (x0, x1)) and abs(z - ZC) == hw
                    if corner:
                        continue
                    edge = x in (x0, x1) or abs(z - ZC) == hw
                    if y == 17 and edge and (x + z) % 3:
                        st = 'minecraft:tinted_glass' if self.fresh else (
                            'minecraft:gray_stained_glass' if (x + z) % 2 else C.AIR)
                    elif y == 19:
                        st = slab('or:durasteel_plating_slab')
                    else:
                        st = self.skin(x, y, z, band=False)
                    t.set(x, y, z, st)
        # the tower's insides are solid plating behind the glass
        for x in range(16, 22):
            for z in range(ZC - 2, ZC + 3):
                if C.is_airish(t, x, 17, z):
                    t.set(x, 17, z, 'or:durasteel_plating')
        if self.fresh:
            for i, (x, y) in enumerate(((18, 20), (18, 21), (18, 22), (19, 23), (20, 23), (21, 23), (22, 22))):
                t.set(x, y, ZC, S('iron_bars', waterlogged=False) if i < 3 else S('or:durasteel_pipes', axis='x'))
            t.set(22, 21, ZC, 'minecraft:lightning_rod[facing=down,powered=false,waterlogged=false]')
        else:
            t.set(18, 20, ZC, S('iron_bars', waterlogged=False))
            for i in range(6):     # the mast lies along the spine
                t.set(23 + i, 15, ZC, S('or:durasteel_pipes', axis='x'))
        # a sensor dish on the tower's back, tipped over
        t.set(13, 16, ZC, 'or:durasteel_plating')
        t.set(12, 16, ZC, S('or:durasteel_plating_stairs', facing='east', half='bottom', shape='straight', waterlogged=False))
        t.set(12, 17, ZC, slab('or:durasteel_plating_slab'))

    def sponsons(self):
        """Gun blisters on the flanks, their barrels bent or gone."""
        t = self.t
        for (xc, zs) in ((29, -1), (37, -1), (33, 1)):
            z_edge = None
            for z in (range(0, ZC) if zs < 0 else range(HS[2] - 1, ZC, -1)):
                if layer(xc, 9, z) is not None:
                    z_edge = z
                    break
            if z_edge is None:
                continue
            for x in range(xc - 2, xc + 3):
                for y in range(7, 12):
                    for dz in range(0, 3):
                        z = z_edge + zs * dz
                        if math.sqrt((x - xc) ** 2 + (y - 9) ** 2 + (dz * 1.4) ** 2) <= 2.3 and C.is_airish(t, x, y, z):
                            t.set(x, y, z, self.skin(x, y, z, band=False))
            zb = z_edge + zs * 3
            if (xc + zs) % 3:
                t.set(xc, 9, zb, S('or:durasteel_pipes', axis='z'))
                t.set(xc, 9, zb + zs, S('or:durasteel_pipes', axis='z'))
            else:
                t.set(xc, 9, zb, S('or:durasteel_pipes', axis='z'))

    # ------------------------------------------------------------ the bow

    def bow(self):
        """The bow, broken off behind the forward bulkhead: a wedge with its decks showing at the break. A fresh
        wreck's stands thirty degrees nose-down, the nose buried in the mound it drove up and blackened by
        re-entry; an old wreck's lies rolled on its side, half sunk in the turf."""
        t = self.t
        if self.fresh:
            A = (51.5, 13.4, ZC)
            pitch, L, roll = math.radians(31), 18.5, 0.0
            dz = 0.0
        else:
            A = (52.5, 5.4, ZC + 4.0)
            pitch, L, roll = math.radians(2), 16.5, math.radians(78)
            dz = 2.0
        Bn = (A[0] + L * math.cos(pitch), A[1] - L * math.sin(pitch), A[2] + dz)
        ex, ey, ez = Bn[0] - A[0], Bn[1] - A[1], Bn[2] - A[2]
        L = math.sqrt(ex * ex + ey * ey + ez * ez)
        ex, ey, ez = ex / L, ey / L, ez / L
        nx, ny = -ey, ex
        cr, sr = math.cos(roll), math.sin(roll)
        p = 2.3
        self.bow_cells = set()
        for x in range(47, HS[0]):
            for y in range(0, HS[1]):
                for z in range(HS[2]):
                    dx, dy, dzz = x - A[0], y - A[1], z - A[2]
                    u = dx * ex + dy * ey + dzz * ez
                    if u < 0 or u > L:
                        continue
                    v0 = dx * nx + dy * ny
                    w0 = dzz - u * ez
                    v = v0 * cr + w0 * sr
                    w = -v0 * sr + w0 * cr
                    f = u / L
                    b = 8.2 * (1 - 0.7 * f ** 1.5)
                    a = 5.8 * (1 - 0.5 * f ** 1.2)
                    vc = -1.0 * f
                    vv = v - vc
                    if se(w, vv, b, a, p) > 1:
                        continue
                    if u < 0.9 + 1.6 * self.n2.at(y, z, 7) - 0.3:   # the broken end
                        continue
                    if not self.fresh and (x, z) in self.ground and y <= self.ground[(x, z)] and vv < -a + 1.5:
                        continue
                    inner = se(w, vv, b - 1.3, a - 1.3, p) <= 1
                    self.bow_cells.add((x, y, z))
                    if not inner:
                        st = self.bow_skin(x, y, z, f, w, vv, a, b)
                        t.set(x, y, z, st)
                    elif vv < -a + 2.3:
                        t.set(x, y, z, 'or:durasteel_deck' if vv > -a + 1.3 else 'or:wreck_plating')
                    elif 0.2 < vv < 1.25 and f < 0.72:
                        t.set(x, y, z, 'or:durasteel_deck' if (x + z) % 5 else 'or:durasteel_grate')
                    elif abs(w) < 0.6 and f < 0.6 and (int(u) % 4 == 0):
                        t.set(x, y, z, S('or:durasteel_beam', axis='y'))
                    else:
                        t.set(x, y, z, C.AIR)

    def bow_skin(self, x, y, z, f, w, vv, a, b):
        """The bow's plating: white paint going grey, then black toward a fresh wreck's nose (it took the heat
        of the fall), the red band along the chines, viewports along the head."""
        side = abs(w) > b - 2.2
        n = self.n2.at(x, y, z)
        if self.fresh:
            heat = f * 1.25 - 0.35 + 0.35 * (n - 0.5) + (0.25 if vv < 0 else 0.0)
            if heat > 0.75:
                return 'minecraft:polished_blackstone' if n < 0.5 else 'minecraft:blackstone'
            if heat > 0.45:
                return 'or:wreck_plating_scorched'
            if side and abs(vv - 1.2) < 0.6 and 0.3 < f < 0.62 and x % 3:
                return 'minecraft:tinted_glass' if (x + y) % 4 else 'minecraft:gray_stained_glass'
        else:
            if self.n3.at(x, y, z) + 0.3 * (n - 0.5) > 0.64:
                return 'or:rusted_plating'
            if vv > a - 1.6 and abs(w) < 2.0 and f > 0.4 and x % 3:
                return 'minecraft:gray_stained_glass'
        if side and abs(vv + 1.0) < 0.55 and n > 0.2:
            return 'minecraft:red_terracotta'
        h = self.n1._h(int(f * 6), int((math.atan2(vv, w) + math.pi) * 3.0), 9)
        return 'or:republic_plating' if h < 0.68 else ('or:durasteel_plating' if h < 0.9 else 'or:wreck_plating')

    # ------------------------------------------------------------ the rooms

    def breach_north(self):
        """The torn north flank of the stern compartment: the way in (the track comes up to it)."""
        t = self.t
        for x in range(13, 25):
            for y in range(ST, 12):
                for z in range(4, 11):
                    if layer(x, y, z) not in ('skin', 'wall'):
                        continue
                    e = ((x - 18.5) / 3.3) ** 2 + ((y - 6.4) / 2.9) ** 2 + 0.5 * (self.n2.at(x, y, z) - 0.5)
                    if e < 1.0:
                        t.set(x, y, z, C.AIR)
        for (x, z) in ((16, 5), (17, 4), (20, 5), (21, 3), (15, 6), (22, 6)):
            y = self.ground[(x, z)] + 1
            if C.is_airish(t, x, y, z):
                t.set(x, y, z, 'or:wreck_salvage' if (x + z) % 2 else 'or:debris')

    def roof_breach(self):
        """The hole torn in the hold's roof: sky over the hyperdrive. A fresh hole's plates curl outward."""
        t = self.t
        rim = []
        for x in range(26, 42):
            for y in range(9, HS[1]):
                for z in range(HS[2]):
                    if layer(x, y, z) not in ('skin', 'wall'):
                        continue
                    e = ((x - 33.5) / 4.8) ** 2 + ((z - 15.5) / 3.7) ** 2 + 0.6 * (self.n2.at(x, y, z) - 0.5)
                    if e < 1.0:
                        t.set(x, y, z, C.AIR)
                    elif e < 1.5 and layer(x, y, z) == 'skin' and C.is_airish(t, x, y + 1, z):
                        rim.append((x, y, z))
        for (x, y, z) in rim:
            if self.rng.random() < 0.3:
                f = 'south' if z < 15.5 else 'north'
                t.set(x, y + 1, z, stairs('or:republic_plating_stairs', f) if self.rng.random() < 0.6 else
                      ('or:wreck_plating_scorched' if self.fresh else 'or:rusted_plating'))

    def stern_rooms(self):
        """The engine room (x 4..13) and the stern compartment (x 15..22), where the scavengers sleep."""
        t = self.t
        self.bulkhead(14, openings={(y, z) for y in range(ST, ST + 3) for z in range(13, 18)})
        # the reactor: a cracked column with its core still glowing (fresh) or dead (old)
        for y in range(ST, 13):
            for (x, z) in C.disk_cells(9.0, ZC, 2.1):
                if (x, y, z) not in self.cells_in:
                    continue
                d = math.hypot(x - 9.0, z - ZC)
                if d > 1.2:
                    t.set(x, y, z, 'or:durasteel_plating' if y % 3 else S('or:durasteel_beam', axis='y'))
                else:
                    t.set(x, y, z, 'or:light_panel_amber' if (self.fresh and y in (6, 7)) else 'or:durasteel_vent')
        for z in (ZC - 4, ZC + 4):
            for x in range(5, 14):
                if (x, 9, z) in self.cells_in:
                    t.set(x, 9, z, S('or:durasteel_pipes', axis='x'))
        for (x, z) in ((6, 11), (12, 19), (11, 11), (6, 19), (5, 14)):
            if (x, ST, z) in self.cells_in:
                t.set(x, ST, z, 'or:wreck_salvage')
        t.set(12, ST, 12, 'or:debris')
        # the den: bedrolls, a stove, a table of stolen parts, lanterns on chains
        for (x, z, c) in ((16, 19, 'brown'), (17, 19, 'brown'), (19, 20, 'gray'), (20, 20, 'gray'), (16, 11, 'light_gray')):
            t.set(x, ST, z, 'minecraft:%s_carpet' % c)
        t.set(15, ST, 19, 'minecraft:white_wool')
        t.set(21, ST, 20, 'minecraft:white_wool')
        t.set(21, ST, 18, S('smoker', facing='west', lit=True))
        t.set(21, ST + 1, 18, S('or:durasteel_pipes', axis='y'))
        t.set(21, ST + 2, 18, S('or:durasteel_pipes', axis='y'))
        t.set(15, ST, 15, 'or:cargo_crate_republic')
        t.set(15, ST, 16, 'or:cargo_crate_republic')
        t.set(15, ST + 1, 16, 'minecraft:lantern[hanging=false,waterlogged=false]')
        t.set(15, ST, 14, 'minecraft:smithing_table')
        t.set(15, ST + 1, 15, S('or:holo_screen', facing='east'))
        t.set(16, ST, 21, S('barrel', facing='up', open=False))
        t.set(15, ST, 21, 'or:wreck_salvage')
        for (x, z) in ((18, 15), (18, 19)):
            for y in range(HS[1] - 2, ST, -1):
                if (x, y, z) in self.cells_in and not C.is_airish(t, x, y + 1, z):
                    t.set(x, y, z, S('iron_chain', axis='y', waterlogged=False))
                    t.set(x, y - 1, z, 'minecraft:lantern[hanging=true,waterlogged=false]')
                    break
        t.set(22, ST + 1, 13, S('or:console', facing='west'))
        t.set(22, ST, 19, 'or:debris')
        mod.spawner(t, 18, ST, 13, 'scavengers', anchor='SCAVENGER_1')
        mod.spawner(t, 20, ST, 17, 'scavengers', anchor='SCAVENGER_2')

    def hold(self):
        """The hold between the bulkheads (x 25..40): the hyperdrive on the south side, a catwalk on the north
        whose west end fell into a ramp, spilled cargo; the HEART in front of the drive, under the torn roof."""
        t = self.t
        for x in range(27, 38):
            for y in range(ST, 11):
                for z in range(16, 22):
                    if (x, y, z) not in self.cells_in:
                        continue
                    d = math.hypot(y - 6.6, z - 19.6)
                    if d <= 2.5:
                        if x in (27, 37):
                            t.set(x, y, z, 'or:durasteel_plating' if d > 1.2 else 'or:durasteel_vent')
                        else:
                            t.set(x, y, z, S('or:durasteel_beam', axis='x') if (x - 27) % 3 == 0 and d > 1.4 else
                                  ('or:republic_plating' if d > 1.4 else 'or:durasteel_plating'))
        for x in (31, 32, 33):
            t.set(x, 6, 17, C.AIR)
            t.set(x, 5, 17, C.AIR)
            t.set(x, 6, 18, 'or:light_panel_amber' if x == 32 else 'or:wreck_conduit')
            t.set(x, 5, 18, 'or:durasteel_grate')
            t.set(x, 4, 17, 'or:debris')
        t.set(30, 7, 17, S('or:holo_screen', facing='north'))
        for x in range(37, 41):
            if (x, 10, 19) in self.cells_in:
                t.set(x, 10, 19, S('or:durasteel_pipes', axis='x'))
        # the catwalk along the north side (floor y 8) from x 32; its west end fell into a ramp of plating
        for x in range(32, 41):
            for z in range(10, 13):
                if (x, 8, z) in self.cells_in:
                    t.set(x, 8, z, slab('or:durasteel_deck_slab', 'top') if z == 12 else 'or:durasteel_grate')
            if (x, 9, 13) in self.cells_in and x % 2 == 0:
                t.set(x, 9, 13, S('iron_bars', waterlogged=False))
        for (x, y) in ((27, 4), (28, 5), (29, 6), (30, 7), (31, 8)):
            t.set(x, y, 12, stairs('or:durasteel_plating_stairs', 'east'))
            for yy in range(ST, y):
                t.set(x, yy, 12, 'or:durasteel_plating')
                t.set(x, yy, 11, 'or:wreck_plating' if (x + yy) % 2 else 'or:durasteel_plating')
            t.set(x, y, 11, slab('or:durasteel_plating_slab'))
        for x in (34, 38):
            for y in range(ST, 8):
                t.set(x, y, 10, S('or:durasteel_beam', axis='y'))
        t.chest(39, 9, 10, facing='south', loot='chests/wreck_field')
        t.anchor('DECK_CACHE', 39, 9, 10)
        t.set(40, 9, 11, 'or:cargo_crate_republic')
        t.set(38, 9, 10, S('barrel', facing='up', open=False))
        for (x, z, h) in ((25, 20, 3), (26, 20, 2), (25, 19, 2), (26, 18, 1), (25, 10, 2), (26, 9, 1),
                          (38, 21, 2), (39, 21, 1), (39, 20, 1)):
            for y in range(ST, ST + h):
                if (x, y, z) in self.cells_in:
                    t.set(x, y, z, 'or:cargo_crate_republic' if (x + y + z) % 3 else 'or:cargo_crate')
        for (x, z) in ((28, 9), (27, 14), (36, 14), (29, 15), (35, 11), (37, 20)):
            if (x, ST, z) in self.cells_in and C.is_airish(t, x, ST, z):
                t.set(x, ST, z, 'or:debris' if (x + z) % 2 else 'or:wreck_salvage')
        for (x, y, z, s) in ((27, 4, 16, slab('or:republic_plating_slab')), (28, 4, 16, stairs('or:republic_plating_stairs', 'west')),
                             (28, 4, 15, stairs('or:republic_plating_stairs', 'west'))):
            t.set(x, y, z, s)
        for x in (27, 33, 39):
            for z in (9, 21):
                for y in (8, 7):
                    if not C.is_airish(t, x, y, z) and (x, y, z) not in self.cells_in:
                        t.set(x, y, z, 'or:light_panel_amber' if self.fresh else 'or:light_panel')
                        break
        mod.spot(t, 'HEART', 32, ST, 15)

    def strongroom(self):
        """Bulkhead B (x 41..42), the strongroom behind it (x 43..45) and the end bulkhead (x 46). The puzzle
        cells are pasted over this afterwards."""
        t = self.t
        self.bulkhead(41)
        self.bulkhead(42)
        self.bulkhead(XE)
        t.set(45, ST, 18, 'or:cargo_crate_republic')
        t.set(45, ST, 19, 'or:cargo_crate_republic')
        t.set(45, ST + 1, 18, 'minecraft:lantern[hanging=false,waterlogged=false]')
        t.set(44, ST, 10, S('barrel', facing='up', open=False))
        t.set(45, ST, 11, 'or:debris')
        # the sealed blast door on the end bulkhead's outer face, seen through the break
        for y in range(ST, ST + 5):
            for z in range(ZC - 2, ZC + 3):
                st = S('or:durasteel_beam', axis='y') if abs(z - ZC) == 2 else (
                    S('or:durasteel_beam', axis='z') if y == ST + 4 else 'or:sealed_plating')
                t.set(XE + 1, y, z, st)
        t.set(XE + 1, ST + 2, ZC + 3, 'or:light_panel_red')

    def overgrow(self):
        """Old wreck: moss on what faces the sky, vines down the flanks, a birch grown up through the hold's
        torn roof, grass in the stripped engine block."""
        t = self.t
        rng = self.rng
        n = Noise(self.seed + 50, 2.5)
        for (x, y, z), s in list(t.cells.items()):
            nm = C.name(s)
            if nm in ('minecraft:air', 'minecraft:jigsaw') or (x, y, z) in self.kit:
                continue
            if y < ST or not C.is_airish(t, x, y + 1, z) or (x, y + 1, z) in self.cells_in:
                continue
            if nm.startswith('oldrepublic:') and ('plating' in nm or 'deck' in nm) and not nm.endswith(('_slab', '_stairs')):
                if n.at(x, y, z) > 0.55 and rng.random() < 0.8:
                    t.set(x, y + 1, z, 'minecraft:moss_carpet')
        for (x, y, z), s in list(t.cells.items()):
            nm = C.name(s)
            if not (nm.startswith('oldrepublic:') and 'plating' in nm) or y < 6:
                continue
            for d, (dx, dz) in (('south', (0, -1)), ('north', (0, 1))):
                q = (x, y, z + dz)
                if q in t.cells and C.name(t.cells[q]) == 'minecraft:air' and q not in self.cells_in and \
                        n.at(x * 1.5, y, z) > 0.6 and rng.random() < 0.5:
                    for k in range(0, 1 + int(rng.random() * 4)):
                        qq = (x, y - k, z + dz)
                        if qq in t.cells and C.name(t.cells[qq]) == 'minecraft:air' and qq not in self.cells_in:
                            t.set(*qq, S('vine', **{d: True, 'up': False, 'east': False, 'west': False,
                                                    ('north' if d == 'south' else 'south'): False}))
                        else:
                            break
        # the birch in the hold
        for (x, z) in ((35, 13), (36, 13), (35, 14)):
            t.set(x, G, z, 'minecraft:rooted_dirt')
        t.set(34, ST, 13, 'minecraft:coarse_dirt')
        t.set(36, ST, 14, 'minecraft:moss_block')
        C.tree(t, 35, ST, 13, kind='birch', height=11, rng=rng, crown=3.0, only_empty=True)
        for (x, z) in ((7, 10), (8, 20), (11, 17), (6, 16)):
            if C.is_airish(t, x, ST, z):
                t.set(x, G, z, 'minecraft:grass_block')
                t.set(x, ST, z, 'minecraft:short_grass' if (x + z) % 3 else 'minecraft:fern')
        C.fix_vines(t)

    def scatter(self):
        """Plates, salvage heaps, scorch (fresh) or grass, ferns and flowers (old) on the ground."""
        t = self.t
        rng = self.rng
        n = Noise(self.seed + 40, 2.2)
        for x in range(HS[0]):
            for z in range(HS[2]):
                y = self.ground[(x, z)]
                if not C.is_airish(t, x, y + 1, z) or C.is_airish(t, x, y, z):
                    continue
                r = rng.random()
                top = C.name(t.get(x, y, z))
                ahead = 1.0 + (x - 35) / 35.0      # more wreckage thrown ahead (east) than left behind
                if self.fresh:
                    heat = 0.0
                    for (sx, sy, sz, rr) in self.fires:
                        heat = max(heat, math.exp(-((x - sx) ** 2 + (z - sz) ** 2) / (1.8 * rr * rr)))
                    if x < 6:
                        heat = max(heat, 0.8 * math.exp(-((z - ZC) / 4.0) ** 2))
                    if n.at(x, 0, z) > 0.42 and r < heat * 0.8:
                        t.set(x, y + 1, z, 'or:scorch_mark')
                    elif r < 0.02 * ahead:
                        t.set(x, y + 1, z, 'or:wreck_salvage')
                    elif r < 0.045 * ahead:
                        t.set(x, y + 1, z, 'or:debris')
                    elif top == 'minecraft:grass_block' and r < 0.4:
                        t.set(x, y + 1, z, 'minecraft:short_grass' if r < 0.33 else 'minecraft:dead_bush')
                else:
                    if top in ('minecraft:grass_block', 'minecraft:moss_block'):
                        if r < 0.32:
                            t.set(x, y + 1, z, 'minecraft:short_grass')
                        elif r < 0.40:
                            t.set(x, y + 1, z, 'minecraft:fern')
                        elif r < 0.43 and C.is_airish(t, x, y + 2, z):
                            t.set(x, y + 1, z, 'minecraft:tall_grass[half=lower]')
                            t.set(x, y + 2, z, 'minecraft:tall_grass[half=upper]')
                        elif r < 0.455:
                            t.set(x, y + 1, z, 'minecraft:wildflowers[facing=east,flower_amount=3]')
                        elif r < 0.475:
                            t.set(x, y + 1, z, 'or:wreck_salvage')
                    elif r < 0.05:
                        t.set(x, y + 1, z, 'or:debris')
        # plates thrown clear, lying flat or bent against the berms (more of them ahead)
        for (x, z) in ((10, 28), (30, 1), (44, 1), (53, 28), (57, 2), (25, 28), (47, 27), (63, 27), (66, 4), (41, 28)):
            for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1), (2, 0)):
                xx, zz = x + dx, z + dz
                if (xx, zz) in self.ground:
                    yy = self.ground[(xx, zz)] + 1
                    if C.is_airish(t, xx, yy, zz):
                        t.set(xx, yy, zz, slab('or:republic_plating_slab') if (dx + dz) % 2 == 0 else
                              ('or:wreck_plating_scorched' if self.fresh else 'or:rusted_plating'))
        # the gun torn off the spine, in the grass north of the hold
        for (x, z, s) in ((37, 3, S('or:durasteel_pipes', axis='x')), (38, 3, S('or:durasteel_pipes', axis='x')),
                          (39, 3, S('or:durasteel_pipes', axis='x')), (35, 3, 'or:durasteel_plating'),
                          (35, 2, 'or:durasteel_plating'), (36, 2, 'or:wreck_plating'), (36, 3, 'or:durasteel_plating')):
            t.set(x, self.ground[(x, z)] + 1, z, s)
        if self.fresh:
            # the fire at the break
            t.set(49, self.ground[(49, 13)] + 1, 13, S('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
            # rock the bow broke out of the ground, thrown up on the mound
            for (x, z, h) in ((62, 9, 2), (63, 21, 1), (67, 11, 1), (66, 20, 2), (60, 5, 1), (69, 17, 1)):
                y = self.ground[(x, z)] + 1
                for k in range(h):
                    if C.is_airish(t, x, y + k, z):
                        t.set(x, y + k, z, 'minecraft:tuff' if (x + k) % 2 else 'minecraft:cobblestone')
                if C.is_airish(t, x + 1, y, z):
                    t.set(x + 1, y, z, 'minecraft:stone')

    # ------------------------------------------------------------ the whole

    def build(self):
        self.lay_ground()
        self.body()
        self.engines()
        if not self.fresh:
            self.stripped()
        self.tower()
        self.sponsons()
        self.bow()
        self.breach_north()
        self.stern_rooms()
        self.bulkhead(23)
        self.bulkhead(24)
        self.hold()
        self.roof_breach()
        self.strongroom()
        self.scatter()
        t = self.t
        C.restore(t, self.kit)
        if not self.fresh:
            self.overgrow()
            C.restore(t, self.kit)
        # the puzzle's anchors move with its cells
        old = C.load_base('wreck_field/hull')
        for name in ('HATCH', 'COUNTERWEIGHT', 'PEDESTAL_WAYFARER', 'ANCHOR_LOCKER'):
            for (x, y, z) in old.anchors[name]:
                t.anchor(name, x + KIT[0], y + KIT[1], z + KIT[2])
        # the surface guard watches the track from the gap in the north berm
        gx = next(x for x in (22, 21, 23, 20, 17) if self.ground[(x, 2)] == G)
        mod.spot(t, 'SURFACE_GUARD', gx, ST, 2)
        self.guard = (gx, ST, 2)
        t.jigsaw(*JIG_PATH, 'north_up', name='hull_path', target='path_in', pool='wreck_field/path')
        t.jigsaw(*JIG_WEST, 'west_up', name='hull_scar', target='scar_entry', pool='wreck_field/side')
        t.jigsaw(*JIG_EAST, 'east_up', name='hull_scar', target='scar_entry', pool='wreck_field/side')
        for (x, y, z) in (JIG_PATH, JIG_WEST, JIG_EAST):
            t.set(x, y + 1, z, C.AIR)
        self.salvage = thin_salvage(t, 4, SALVAGE_KEEP[self.v])
        mod.light_fill(t, region=(4, ST, 8, 45, 12, 22), level=8, strength=11)
        C.restore(t, self.kit)
        return t


# wreck salvage is the field's counted resource (spec 3.2: 6 to 8 per wreck field, as before: four in the
# hull, one in the camp's salvage bay, one in each furrow); the hull keeps these four, spread from the
# engine den to the bow, and every other piece the generator strewed becomes torn debris
SALVAGE_KEEP = {1: [(6, ST, 11), (35, ST, 11), (20, ST, 5), (51, ST, 13)],
                2: [(6, ST, 11), (35, ST, 11), (20, ST, 5), (60, ST, 12)]}


def thin_salvage(t, keep, prefer=()):
    """Keep `keep` wreck salvage (`prefer` first); the rest become debris, or air where nothing is under
    them for the debris to lie on. Returns the cells kept."""
    before = [p for p, st in t.cells.items() if C.name(st) == 'oldrepublic:wreck_salvage']
    kept = C.budget(t, 'or:wreck_salvage', keep, 'or:debris', prefer=prefer)
    for (x, y, z) in before:
        if (x, y, z) not in kept and C.is_airish(t, x, y - 1, z):
            t.set(x, y, z, C.AIR)
    return kept


def salvage_problems(t, start, cells, passable=(), label=''):
    """Each kept salvage must be minable: a reachable stand next to it (beside it, or a step below)."""
    r = C.reach(t, start, passable=passable)
    out = []
    for (x, y, z) in cells:
        near = [(x + dx, y + dy, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) for dy in (0, -1, 1)]
        if not any(q in r for q in near):
            out.append('%s salvage %s has no reachable stand beside it' % (label, [x, y, z]))
    return out


def hull(seed=63001, variant=1):
    w = Wreck(seed, variant)
    return w, w.build()


# ---------------------------------------------------------------- the track, the salvage camp, the furrows

def path_piece(seed=63040):
    """The salvage track from the wreck to the camp (terrain-matching): rutted dirt, the crew's hand-laid rail
    for the salvage sledges, dropped plates and scraps, a lamp post. 7 x 3 x 13; jigsaws at (3, 1, 0) and
    (3, 1, 12)."""
    t = C.Template(7, 3, 13, seed=seed, name='wreck_field/path')
    side = C.mix(seed + 1, [('minecraft:grass_block', 8), ('minecraft:coarse_dirt', 1), ('minecraft:dirt', 1)], 3.0)
    track = C.mix(seed + 2, [('minecraft:coarse_dirt', 5), ('minecraft:dirt_path', 4), ('minecraft:gravel', 2),
                             ('minecraft:dirt', 1)], 1.6)
    for x in range(7):
        for z in range(13):
            wob = int(round(math.sin(z * 0.45) * 0.7))
            on = abs(x - 3 - wob) <= 1
            t.set(x, 0, z, track(x, 0, z) if on else side(x, 0, z))
            t.set(x, 1, z, C.AIR)
            t.set(x, 2, z, C.AIR)
    for z in range(1, 12):
        if z not in (5, 6):
            t.set(5, 1, z, S('rail', shape='north_south', waterlogged=False))
    t.set(5, 1, 6, S('or:durasteel_beam', axis='z'))
    t.set(6, 1, 5, 'or:debris')
    t.set(1, 1, 3, slab('or:republic_plating_slab'))
    t.set(0, 1, 4, slab('or:republic_plating_slab'))
    t.set(0, 1, 9, S('or:durasteel_beam', axis='y'))
    t.set(0, 2, 9, 'or:light_panel_amber')
    t.set(1, 1, 10, 'or:debris')
    for (x, z) in ((6, 1), (0, 12), (6, 11), (1, 7)):
        t.set(x, 1, z, 'minecraft:short_grass')
    t.jigsaw(3, 1, 0, 'north_up', name='path_in', target='hull_path', pool='minecraft:empty')
    t.jigsaw(3, 1, 12, 'south_up', name='path_out', target='camp_entry', pool='wreck_field/side')
    return t


def tent(t, x0, z0, x1, z1, wall, roof='or:canvas', door=None, y=1, h=3):
    """A box tent: canvas walls h high on beams, a pitched roof along x. door: [(x, z)] wall cells left open
    (two high)."""
    door = door or []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            for yy in range(y, y + h):
                if edge and (x, z) not in door:
                    t.set(x, yy, z, wall)
                elif not edge or yy >= y + 2:
                    t.set(x, yy, z, C.AIR if not edge else wall)
            if edge and (x, z) in door:
                t.set(x, y, z, C.AIR)
                t.set(x, y + 1, z, C.AIR)
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for yy in range(y, y + h):
            t.set(x, yy, z, S('or:durasteel_beam', axis='y'))
    zc = (z0 + z1) / 2.0
    half = (z1 - z0) / 2.0
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k = min(z - z0, z1 - z)
            yy = y + h + int(k * 0.5)
            if k * 2 >= z1 - z0 - 1 and (z1 - z0) % 2 == 0 and z == int(zc):
                t.set(x, yy, z, slab(roof + '_slab'))
            elif k % 2 == 0:
                t.set(x, yy, z, stairs(roof + '_stairs', 'south' if z < zc else 'north'))
            else:
                t.set(x, yy, z, roof)
                for y2 in range(y + h, yy):
                    t.set(x, y2, z, roof if x in (x0, x1) else C.AIR)


def salvage_camp(seed=63050):
    """The Republic salvage crew's yard, run as a business: a landing pad with lots waiting for the lift, a
    gantry swinging a hull plate over the sorting bays (plates, conduits, pipe, salvage by kind, each bay
    chalked), a cutting bench, the foreman's tent with the manifest and the strongbox, the medic's tent, the
    cook fire and the floodlights. 29 x 12 x 29, buried 1; the track comes in from the south at (14, 1, 28)."""
    W_, H_, D_ = 29, 12, 29
    t = C.Template(W_, H_, D_, seed=seed, name='wreck_field/salvage_camp')
    rng = t.rng
    yard = C.mix(seed + 3, [('minecraft:grass_block', 9), ('minecraft:coarse_dirt', 3), ('minecraft:dirt', 1)], 4.0)
    trod = C.mix(seed + 4, [('minecraft:coarse_dirt', 4), ('minecraft:dirt_path', 4), ('minecraft:gravel', 2)], 1.8)
    for x in range(W_):
        for z in range(D_):
            t.set(x, 0, z, yard(x, 0, z))
    C.shapes.path(t, [(14, 28), (14, 20), (14, 14), (8, 12)], lambda x, z: 0, 3, trod, rng=rng, jitter=0.4)
    C.shapes.path(t, [(14, 17), (20, 15), (22, 9)], lambda x, z: 0, 2, trod, rng=rng, jitter=0.4)
    # the landing pad (north-east): deck plates, hazard corners, lots strapped for the lift
    for x in range(17, 27):
        for z in range(1, 11):
            edge = x in (17, 26) or z in (1, 10)
            t.set(x, 0, z, ('minecraft:yellow_concrete' if (x + z) % 2 else 'minecraft:black_concrete') if edge and
                  (x in (17, 26) and z in (1, 2, 9, 10) or z in (1, 10) and x in (17, 18, 25, 26)) else
                  ('or:durasteel_deck' if not edge else 'or:durasteel_plating'))
    for (x, z) in ((17, 1), (26, 1), (17, 10), (26, 10)):
        t.set(x, 1, z, 'or:light_panel_amber')
    for (x, z, h) in ((19, 3, 2), (20, 3, 2), (19, 4, 1), (23, 3, 1), (24, 3, 2), (24, 4, 1)):
        for y in range(1, h + 1):
            t.set(x, y, z, 'or:cargo_crate_republic')
    for x in range(19, 25):
        t.set(x, 1, 7, slab('or:republic_plating_slab', 'double') if x % 2 else 'or:republic_plating')
        t.set(x, 2, 7, slab('or:republic_plating_slab'))
    t.set(25, 1, 6, S('or:console', facing='west'))
    t.set(25, 1, 8, 'minecraft:iron_block')
    # the gantry over the sorting bays (west): two legs, a beam, a plate hanging on its chain
    for z in (3, 11):
        for y in range(1, 9):
            t.set(3, y, z, S('or:durasteel_beam', axis='y'))
            t.set(4, y, z, S('or:durasteel_beam', axis='y') if y in (1, 8) else C.AIR)
        t.set(3, 0, z, 'or:durasteel_plating')
    for z in range(3, 12):
        t.set(3, 9, z, S('or:durasteel_beam', axis='z'))
        t.set(4, 9, z, S('or:durasteel_beam', axis='z') if z in (3, 11) else C.AIR)
    for y in (8, 7, 6):
        t.set(3, y, 7, S('iron_chain', axis='y', waterlogged=False))
    for z in (6, 7, 8):
        t.set(3, 5, z, 'or:republic_plating' if z == 7 else stairs('or:republic_plating_stairs', 'north' if z == 6 else 'south', half='top'))
    t.set(3, 9, 2, 'or:durasteel_plating')
    t.set(3, 10, 2, 'or:light_panel_amber')
    # the sorting bays: low plate walls, each bay one kind, a chalked sign board over it
    bays = [(6, 2, 'plates'), (6, 6, 'conduit'), (6, 10, 'pipe'), (10, 2, 'salvage'), (10, 6, 'crates')]
    for (x0, z0, kind) in bays:
        for x in range(x0, x0 + 3):
            for z in range(z0, z0 + 3):
                edge = x == x0 + 2 or z == z0 + 2
                if edge and not (x == x0 + 1 and z == z0 + 2):
                    t.set(x, 1, z, slab('or:durasteel_plating_slab'))
                elif not edge:
                    if kind == 'plates':
                        t.set(x, 1, z, slab('or:republic_plating_slab', 'double' if (x + z) % 2 else 'bottom'))
                    elif kind == 'conduit':
                        t.set(x, 1, z, 'or:wreck_conduit')
                    elif kind == 'pipe':
                        t.set(x, 1, z, S('or:durasteel_pipes', axis='x' if (x + z) % 2 else 'z'))
                    elif kind == 'salvage':
                        # the camp's one piece of salvage (spec 3.2); the bay's scraps round it
                        t.set(x, 1, z, 'or:wreck_salvage' if (x, z) == (x0, z0) else 'or:debris')
                    else:
                        t.set(x, 1, z, 'or:cargo_crate' if (x + z) % 2 else 'or:cargo_crate_republic')
    # the cutting bench: a smithing table, a grindstone, the cutter's power cell
    t.set(12, 1, 11, 'minecraft:smithing_table')
    t.set(13, 1, 11, S('grindstone', face='floor', facing='south'))
    t.set(11, 1, 11, 'or:durasteel_vent')
    t.set(11, 2, 11, S('or:durasteel_pipes', axis='y'))
    t.set(12, 1, 12, 'or:debris')
    # the foreman's tent (south-west): navy canvas, the manifest on the holo table, the strongbox
    tent(t, 2, 16, 9, 22, 'or:canvas_navy', door=[(9, 19)])
    t.set(4, 1, 19, 'or:durasteel_deck')
    t.set(5, 1, 19, 'or:durasteel_deck')
    t.set(4, 2, 19, S('or:holo_screen', facing='east'))
    t.set(5, 2, 19, S('or:console', facing='east'))
    t.chest(3, 1, 21, facing='east', loot='chests/wreck_field')
    t.anchor('CHEST_1', 3, 1, 21)
    t.set(3, 1, 17, 'minecraft:light_blue_carpet')
    t.set(4, 1, 17, 'minecraft:light_blue_carpet')
    t.set(8, 1, 21, S('barrel', facing='up', open=False))
    t.set(8, 1, 17, 'or:cargo_crate_republic')
    t.set(6, 3, 19, 'or:light_panel')
    mod.banner(t, 6, 3, 17, 'south', base='blue')
    # the medic's tent (south-east): white canvas, cots, the supplies
    tent(t, 20, 17, 26, 23, 'or:canvas', door=[(20, 20)])
    for z in (18, 22):
        for x in (22, 23):
            t.set(x, 1, z, 'minecraft:white_carpet')
    t.set(25, 1, 18, 'minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]')
    t.set(25, 1, 22, 'minecraft:cauldron')
    t.set(25, 1, 20, 'or:cargo_crate_republic')
    t.set(23, 3, 20, 'or:light_panel')
    for x in (22, 23, 24):
        t.set(x, 5, 20, 'minecraft:red_wool')
    t.set(23, 5, 19, 'minecraft:red_wool')
    t.set(23, 5, 21, 'minecraft:red_wool')
    # the cook fire, its benches and the water drum
    t.set(17, 1, 25, S('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
    t.set(17, 1, 23, S('stripped_spruce_log', axis='x'))
    t.set(19, 1, 25, S('stripped_spruce_log', axis='z'))
    t.set(17, 1, 27, S('stripped_spruce_log', axis='x'))
    t.set(19, 1, 27, 'minecraft:water_cauldron[level=3]')
    # the sledge rail from the track to the sorting bays, a loaded sledge at its end
    for z in range(17, 29):
        t.set(12, 1, z, S('rail', shape='north_south', waterlogged=False))
    t.set(12, 1, 16, 'or:cargo_crate')
    t.set(11, 1, 17, 'or:wreck_plating_scorched')
    for (x, z) in ((10, 24), (10, 25), (9, 25)):
        t.set(x, 1, z, S('barrel', facing='up', open=False))
    t.set(10, 2, 25, 'or:cargo_crate_republic')
    t.set(18, 1, 15, 'or:cargo_crate_republic')
    t.set(18, 2, 15, 'or:cargo_crate_republic')
    t.set(19, 1, 15, 'or:cargo_crate')
    # the comms mast and the generator
    for y in range(1, 10):
        t.set(15, y, 3, S('iron_bars', waterlogged=False))
    t.set(15, 10, 3, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    t.set(15, 9, 3, 'or:light_panel_red')
    t.set(15, 1, 4, S('or:console', facing='south'))
    t.set(14, 1, 3, 'or:durasteel_vent')
    t.set(14, 2, 3, S('or:durasteel_pipes', axis='y'))
    t.set(16, 1, 3, 'or:cargo_crate_republic')
    # floodlights at the yard's corners
    for (x, z) in ((1, 1), (27, 13), (1, 26), (27, 27), (11, 27)):
        for y in range(1, 5):
            t.set(x, y, z, S('or:durasteel_beam', axis='y'))
        t.set(x, 5, z, 'or:light_panel')
    # sandbags along the north and the sides, a gap where the track comes in
    for (xa, xb, z) in ((5, 15, 0), (1, 9, 28), (19, 27, 28)):
        for x in range(xa, xb + 1):
            t.set(x, 1, z, 'minecraft:mud_brick_wall')
    for z in range(13, 16):
        t.set(0, 1, z, 'minecraft:mud_brick_wall')
        t.set(28, 1, z, 'minecraft:mud_brick_wall')
    for y in (1, 2):
        t.set(14, y, 18, C.AIR)
    mod.spawner(t, 14, 1, 18, 'salvage_crew', anchor='SQUAD')
    t.jigsaw(14, 1, 28, 'south_up', name='camp_entry', target='path_out', pool='minecraft:empty')
    C.air_up(t, 0, 0, W_ - 1, D_ - 1, 1)
    mod.light_fill(t, region=(2, 1, 16, 26, 3, 23), level=8, strength=11)
    return t


def scar(seed=63060):
    """A furrow the frigate ploughed: a trench torn through the turf between two berms of thrown earth,
    narrowing away from the wreck, scorched along its bottom, with plates and torn debris strewn along it, a
    fin stuck in the ground and one piece of salvage worth the cutting (12, 1, 18). Terrain-matching,
    23 x 6 x 25; the jigsaw at (11, 1, 0)."""
    WX, HY, DZ = 23, 6, 25
    t = C.Template(WX, HY, DZ, seed=seed, name='wreck_field/scar')
    rng = t.rng
    n = Noise(seed + 1, 2.5)
    turf = C.mix(seed + 2, [('minecraft:grass_block', 9), ('minecraft:coarse_dirt', 1)], 4.0)
    earth = C.mix(seed + 3, [('minecraft:dirt', 6), ('minecraft:coarse_dirt', 5), ('minecraft:gravel', 2),
                             ('minecraft:rooted_dirt', 1)], 1.8)
    cx = 11
    for z in range(DZ):
        f = z / (DZ - 1.0)
        half = 4.6 * (1 - 0.65 * f) + 0.6 * (n.at(0, 0, z) - 0.5)
        wob = 1.2 * math.sin(z * 0.22)
        for x in range(WX):
            d = abs(x - cx - wob)
            for y in range(1, HY):
                t.set(x, y, z, C.AIR)
            if d <= half and z < DZ - 2:
                # the trench: the turf torn away
                t.set(x, 0, z, C.AIR if (d < half - 0.6 and n.at(x, 1, z) > 0.25) else earth(x, 0, z))
                if d < half - 1.2 and rng.random() < 0.25:
                    t.set(x, 0, z, 'minecraft:gravel')
            else:
                t.set(x, 0, z, turf(x, 0, z))
                # the berms of thrown earth, higher near the wreck
                bd = d - half
                hb = 2.7 * (1 - 0.6 * f) * math.exp(-((bd - 1.6) / 1.4) ** 2) + 1.3 * (n.at(x * 1.5, 3, z * 1.5) - 0.5)
                for y in range(1, int(round(hb)) + 1):
                    t.set(x, y, z, earth(x, y, z) if y < int(round(hb)) else
                          ('minecraft:grass_block' if n.at(x, y, z) > 0.5 else earth(x, y, z)))
    # scorch along the trench floor, plates and debris, a fin driven into the ground
    for z in range(1, DZ - 2):
        for x in range(WX):
            if C.name(t.get(x, 0, z)) == 'minecraft:air' or not C.is_airish(t, x, 1, z):
                continue
            r = rng.random()
            if abs(x - cx - 1.2 * math.sin(z * 0.22)) < 3.5 and r < 0.18:
                t.set(x, 1, z, 'or:scorch_mark')
            elif r < 0.05:
                t.set(x, 1, z, 'or:debris')
    for (x, y, z, st) in ((14, 1, 9, 'or:wreck_plating_scorched'), (14, 2, 9, 'or:wreck_plating_scorched'),
                          (14, 3, 10, stairs('or:republic_plating_stairs', 'south')), (14, 1, 10, 'or:republic_plating'),
                          (14, 2, 10, 'or:republic_plating'), (13, 1, 9, 'or:wreck_plating_scorched'),
                          (14, 4, 10, slab('or:republic_plating_slab')), (15, 1, 10, 'or:debris')):
        t.set(x, y, z, st)
    for (x, z) in ((8, 4), (9, 4), (10, 5)):
        t.set(x, 1, z, slab('or:republic_plating_slab'))
    t.set(7, 1, 15, S('or:durasteel_beam', axis='z'))
    t.set(7, 1, 16, S('or:durasteel_beam', axis='z'))
    t.set(12, 1, 18, 'or:wreck_salvage')
    t.set(11, 1, 21, 'or:wreck_plating_scorched')
    t.set(11, 2, 21, 'or:scorch_mark')
    t.set(cx, 0, 0, 'minecraft:coarse_dirt')
    t.jigsaw(cx, 1, 0, 'north_up', name='scar_entry', target='hull_scar', pool='minecraft:empty')
    return t


# ---------------------------------------------------------------- build

def build(a):
    out = {}
    goals = [(32, ST, 15), (18, ST, 15), (10, ST, 18), (39, 9, 11), (44, ST, 14)]
    if C.wanted(a, 'hull') and not (a.only or '').endswith('_2'):
        w, t = hull(63001, 1)
        for p in C.route_problems(t, (19, ST, 1), goals + [w.guard], passable=w.doors(), label='hull') + \
                salvage_problems(t, (19, ST, 1), w.salvage, passable=w.doors(), label='hull'):
            print('CHECK', p)
        out[w.tid] = C.save(t, w.tid)
    if C.wanted(a, 'hull_2'):
        w, t = hull(63002, 2)
        for p in C.route_problems(t, (19, ST, 1), goals + [w.guard], passable=w.doors(), label='hull_2') + \
                salvage_problems(t, (19, ST, 1), w.salvage, passable=w.doors(), label='hull_2'):
            print('CHECK', p)
        out[w.tid] = C.save(t, w.tid)
    if C.wanted(a, 'path'):
        out['wreck_field/path'] = C.save(path_piece(), 'wreck_field/path')
    if C.wanted(a, 'salvage_camp'):
        t = salvage_camp()
        for p in C.route_problems(t, (14, 1, 27), [(15, 1, 18), (6, 1, 19), (22, 1, 20), (21, 1, 5), (5, 1, 7)], label='camp'):
            print('CHECK', p)
        out['wreck_field/salvage_camp'] = C.save(t, 'wreck_field/salvage_camp')
    if C.wanted(a, 'scar'):
        out['wreck_field/scar'] = C.save(scar(), 'wreck_field/scar')
    for k, t in out.items():
        print(k, t.size, t.count())
    return out


def main():
    build(C.args())


if __name__ == '__main__':
    main()
