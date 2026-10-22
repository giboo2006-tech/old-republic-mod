"""The crystal cave (worker O1): the poachers' camp over the shaft (two layouts), the descent and the heart
(re-skinned in place: the route, the seal, the meditation stone and the hidden chest stay exactly where
they were).

    camp    the dig: a timber headframe over the shaft, the winch, a rail to the grading shed where the
            crystals are sorted by size, the tailings heap, the tents, the boss's tent and the lookout
    camp_2  the sinkhole: the camp around a collapsed hollow whose walls glitter with crystal; a crane over
            the hollow, the shaft at its bottom, a ramp down, the sorting tables on the rim

A poachers' camp you can read as a business: what comes up the shaft goes down the rail to the shed, is
graded on the tables, crated, and waits by the boss's tent for the buyer.

    descent the poachers' stepped tunnel (re-skinned in place: the shaft, the ladders, the stairs and both
            jigsaws stay): the rock deepens from stone to tuff and deepslate, the veins thicken, kyber grows
            in the upper corners as you go down
    heart   the cavern, the seal and the trial chamber (re-skinned in place): crystal pockets, the crowd of
            kyber round the seal's passage, crusted columns, stalactites, glow lichen, the poachers' cutting
            and the keeper's cell behind the veined wall; the trial chamber's floor rings are gone

    python3 tools/assets/structures/o1/crystal_cave.py [--only camp] [--preview DIR]
"""
import math

import common as C
from common import S, mod, Noise, stairs, slab

CS = (37, 16, 37)     # the camp
CC = 18               # its centre: the shaft
YS = 5                # the yard's stand (CampPoolElement.YARD_STAND)
GT = YS - 1           # the yard's ground top
TOP = 12              # nothing inside the inset may stand above this (CampPoolElement clears from 13)
INSET = 2             # CampPoolElement.CLEAR_INSET
DIRS4 = ('north', 'south', 'east', 'west')
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def poachers():
    """The three poachers of the old camp (their NBT, kept as it was)."""
    old = C.load_base('crystal_cave/camp')
    return [e['nbt'] for e in old.entities]


def place_poacher(t, nbt, x, z):
    t.entity(x + 0.5, float(YS), z + 0.5, dict(nbt))


def cluster(t, x, y, z, facing='up'):
    t.set(x, y, z, S('or:kyber_cluster', facing=facing))


class Camp:
    def __init__(self, seed, variant):
        self.v = variant
        self.tid = 'crystal_cave/camp' + ('' if variant == 1 else '_2')
        self.t = C.Template(*CS, seed=seed, name=self.tid)
        self.rng = self.t.rng
        self.seed = seed
        self.n = Noise(seed + 5, 3.0)
        self.yard = C.mix(seed + 1, [('minecraft:coarse_dirt', 5), ('minecraft:dirt_path', 2), ('minecraft:gravel', 2),
                                     ('minecraft:grass_block', 3), ('minecraft:packed_mud', 1)], 3.5)
        self.edge = C.mix(seed + 2, [('minecraft:grass_block', 8), ('minecraft:coarse_dirt', 2)], 4.0)
        self.trod = C.mix(seed + 3, [('minecraft:dirt_path', 4), ('minecraft:coarse_dirt', 3), ('minecraft:gravel', 2)], 1.6)
        self.rock = C.mix(seed + 4, [('minecraft:stone', 6), ('minecraft:tuff', 3), ('minecraft:andesite', 2)], 2.5)
        self.floor = {}    # (x, z) -> ground top

    # ------------------------------------------------------------ ground

    def lay_ground(self, hollow=None):
        """y 0..1 rock, 2..3 dirt, the yard at 4; explicit air from the stand to TOP inside the inset (the camp
        element clears what is above). hollow(x, z) -> a lower ground top, or None."""
        t = self.t
        for x in range(CS[0]):
            for z in range(CS[2]):
                ring = min(x, z, CS[0] - 1 - x, CS[2] - 1 - z)
                top = GT
                if hollow:
                    h = hollow(x, z)
                    if h is not None:
                        top = h
                for y in range(0, top + 1):
                    if y < 2 or (hollow and top < GT and y < top):
                        st = self.rock(x, y, z)
                    elif y < top:
                        st = 'minecraft:dirt'
                    else:
                        st = self.edge(x, y, z) if ring < 4 else self.yard(x, y, z)
                    t.set(x, y, z, st)
                self.floor[(x, z)] = top
                if ring >= INSET:
                    for y in range(top + 1, TOP + 1):
                        t.set(x, y, z, C.AIR)

    def path(self, pts, width=2):
        C.shapes.path(self.t, pts, lambda x, z: self.floor.get((x, z), GT), width, self.trod, rng=self.rng, jitter=0.4)

    # ------------------------------------------------------------ the shaft

    def shaft(self, bottom_top=GT):
        """The plus-shaped shaft at the centre with ladders on all four sides (the descent below can turn any
        way), from the jigsaw at y 0 up to the ground at bottom_top."""
        t = self.t
        c = CC
        ladders = {(c - 1, c): 'east', (c + 1, c): 'west', (c, c - 1): 'south', (c, c + 1): 'north'}
        for y in range(0, bottom_top + 1):
            for dx in (-2, -1, 0, 1, 2):
                for dz in (-2, -1, 0, 1, 2):
                    x, z = c + dx, c + dz
                    if (x, z) in ladders:
                        t.set(x, y, z, S('ladder', facing=ladders[(x, z)], waterlogged=False))
                    elif dx == 0 and dz == 0:
                        t.set(x, y, z, C.AIR)
                    elif y == bottom_top and max(abs(dx), abs(dz)) == 2:
                        t.set(x, y, z, S('stripped_spruce_log', axis='x' if abs(dz) == 2 else 'z'))
                    else:
                        t.set(x, y, z, 'minecraft:spruce_planks' if (y % 3 == 0 and max(abs(dx), abs(dz)) == 1) else self.rock(x, y, z))
        t.jigsaw(c, 0, c, 'down_south', name='camp_bottom', target='descent_top', pool='crystal_cave/descent', joint='rollable')

    def headframe(self, y0, half=2, height=6):
        """A timber headframe over the shaft: four posts, braces, a beam frame on top, a pulley, the chain and
        the bucket hanging over the hole."""
        t = self.t
        c = CC
        top = min(TOP, y0 + height)
        for (dx, dz) in ((-half, -half), (half, -half), (-half, half), (half, half)):
            for y in range(y0, top):
                t.set(c + dx, y, c + dz, S('spruce_log', axis='y'))
        for d in range(-half, half + 1):
            t.set(c + d, top, c - half, S('spruce_log', axis='x'))
            t.set(c + d, top, c + half, S('spruce_log', axis='x'))
            t.set(c - half, top, c + d, S('spruce_log', axis='z'))
            t.set(c + half, top, c + d, S('spruce_log', axis='z'))
        for d in range(-half + 1, half):
            t.set(c, top, c + d, S('stripped_spruce_log', axis='z'))
        # braces on the outside of the legs
        for (dx, dz, f) in ((-half - 1, -half, 'east'), (half + 1, half, 'west'), (-half, half + 1, 'north'), (half, -half - 1, 'south')):
            t.set(c + dx, y0, c + dz, stairs('spruce_stairs', f))
        t.set(c, top - 1, c, S('grindstone', face='ceiling', facing='north'))
        for y in range(y0 + 2, top - 1):
            t.set(c, y, c, S('iron_chain', axis='y', waterlogged=False))
        t.set(c, y0 + 1, c, 'minecraft:cauldron')
        t.set(c - half, top - 1, c, 'minecraft:lantern[hanging=true,waterlogged=false]')
        return top

    # ------------------------------------------------------------ pieces of the business

    def tent(self, x0, x1, zc, open_end='x0', roof='or:canvas', end='minecraft:brown_wool', beds=True):
        t = self.t
        y = YS
        for x in range(x0, x1 + 1):
            t.set(x, y + 2, zc, slab(roof + '_slab'))
            t.set(x, y + 1, zc - 1, stairs(roof + '_stairs', 'south'))
            t.set(x, y + 1, zc + 1, stairs(roof + '_stairs', 'north'))
            t.set(x, y, zc - 2, stairs(roof + '_stairs', 'south'))
            t.set(x, y, zc + 2, stairs(roof + '_stairs', 'north'))
            for z in (zc - 1, zc, zc + 1):
                t.set(x, y, z, C.AIR)
            t.set(x, y + 1, zc, C.AIR)
        back = x1 if open_end == 'x0' else x0
        for z in (zc - 1, zc, zc + 1):
            t.set(back, y, z, end)
        t.set(back, y + 1, zc, end)
        if beds:
            for x in range(x0 + 1, x1):
                if x != back:
                    t.set(x, y, zc - 1, 'minecraft:brown_carpet' if x % 2 else 'minecraft:gray_carpet')
            t.set(back - 1 if back == x1 else back + 1, y, zc + 1, S('barrel', facing='up', open=False))

    def fire(self, x, z):
        t = self.t
        t.set(x, YS, z, S('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
        t.set(x - 2, YS, z, S('stripped_spruce_log', axis='z'))
        t.set(x + 2, YS, z, S('stripped_spruce_log', axis='z'))
        t.set(x, YS, z + 2, S('stripped_spruce_log', axis='x'))
        t.set(x + 1, YS, z - 2, 'minecraft:water_cauldron[level=2]')

    def lamp_post(self, x, z, h=4, y=YS):
        t = self.t
        for k in range(h):
            t.set(x, y + k, z, S('spruce_fence', waterlogged=False))
        t.set(x, y + h, z, 'minecraft:lantern[hanging=false,waterlogged=false]')

    def grading_table(self, x0, z, n=4, facing='north'):
        """A trestle of crates and barrels with crystals laid out on it by size, the grader's lamp at one end."""
        t = self.t
        for i in range(n):
            x = x0 + i
            t.set(x, YS, z, 'or:cargo_crate' if i % 2 else S('barrel', facing='up', open=False))
            if i < n - 1:
                cluster(t, x, YS + 1, z)
        t.set(x0 + n - 1, YS + 1, z, 'minecraft:lantern[hanging=false,waterlogged=false]')

    def crates(self, cells):
        t = self.t
        for (x, z, h) in cells:
            for k in range(h):
                t.set(x, YS + k, z, 'or:cargo_crate' if (x + z + k) % 3 else 'or:cargo_crate_republic')

    def spoil(self, cx, cz, r, h):
        """A heap of tailings: rock and gravel thrown out of the shaft."""
        t = self.t
        mix = C.mix(self.seed + 30, [('minecraft:tuff', 4), ('minecraft:gravel', 4), ('minecraft:cobblestone', 2),
                                     ('minecraft:andesite', 2), ('or:crystal_veined_stone', 1)], 1.5)
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.hypot(x - cx, z - cz)
                hh = h * (1 - (d / r) ** 1.6) + 0.8 * (self.n.at(x, 0, z) - 0.5) if d < r else 0
                top = self.floor.get((x, z), GT)
                for k in range(1, int(round(hh)) + 1):
                    y = top + k
                    if y <= TOP:
                        t.set(x, y, z, mix(x, y, z))

    def lookout(self, x0, z0, h=6):
        """A scaffold lookout: a platform with a rail, a ladder up, a lantern; the watcher's post."""
        t = self.t
        top = YS + h
        for dx in (0, 2):
            for dz in (0, 2):
                for y in range(YS, top):
                    t.set(x0 + dx, y, z0 + dz, S('spruce_fence', waterlogged=False))
        for dx in range(3):
            for dz in range(3):
                t.set(x0 + dx, top, z0 + dz, slab('spruce_slab'))
        for y in range(YS, top + 1):
            t.set(x0 + 1, y, z0 + 3, S('ladder', facing='south', waterlogged=False))
        t.set(x0 + 1, YS, z0 + 2, 'minecraft:spruce_planks')
        for y in range(YS + 1, top):
            t.set(x0 + 1, y, z0 + 2, 'minecraft:spruce_planks')
        for (dx, dz) in ((0, 0), (2, 0), (0, 2), (2, 2)):
            t.set(x0 + dx, top + 1, z0 + dz, S('spruce_fence', waterlogged=False))
        t.set(x0 + 2, top + 2, z0, 'minecraft:lantern[hanging=false,waterlogged=false]')

    def boss_tent(self, x0, z0, x1, z1, door):
        """The boss's tent: crimson canvas, the ledger on its lectern, the strongbox, the buyer's crates."""
        t = self.t
        y = YS
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                t.set(x, GT, z, 'minecraft:spruce_planks' if not edge else self.floor_block(x, z))
                for yy in range(y, y + 3):
                    if edge and (x, z) not in door:
                        t.set(x, yy, z, 'or:canvas_crimson' if yy < y + 2 else 'or:canvas')
                    else:
                        t.set(x, yy, z, C.AIR)
                if edge and (x, z) in door:
                    t.set(x, y + 2, z, 'or:canvas_crimson')
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            for yy in range(y, y + 4):
                t.set(x, yy, z, S('spruce_log', axis='y'))
        zc = (z0 + z1) // 2
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                k = min(z - z0, z1 - z)
                if k == 0:
                    t.set(x, y + 3, z, stairs('or:canvas_stairs', 'south' if z == z0 else 'north'))
                elif z == zc:
                    t.set(x, y + 4, z, slab('or:canvas_slab'))
                    t.set(x, y + 3, z, 'or:canvas' if x in (x0, x1) else C.AIR)
                else:
                    t.set(x, y + 3, z, 'or:canvas' if k == 1 else slab('or:canvas_slab', 'top'))
                    if k > 1:
                        t.set(x, y + 4, z, stairs('or:canvas_stairs', 'south' if z < zc else 'north'))
        return (x0, z0, x1, z1)

    def floor_block(self, x, z):
        return self.yard(x, GT, z)

    def finish(self, guards, light_region):
        t = self.t
        for nbt, (x, z) in zip(poachers(), guards):
            for y in (YS, YS + 1):
                if not C.is_airish(t, x, y, z):
                    raise ValueError('poacher spot %s blocked' % ((x, YS, z),))
            place_poacher(t, nbt, x, z)
        mod.light_fill(t, region=light_region, level=8, strength=11)


# ---------------------------------------------------------------- camp 1: the dig

def camp_v1(seed=64001):
    """The dig: the headframe over the shaft, the winch, the rail to the grading shed, the tailings heap,
    the tents and the cook fire, the boss's tent with the crated crystals, the lookout."""
    k = Camp(seed, 1)
    t = k.t
    k.lay_ground()
    k.path([(18, 36), (18, 22)], 3)
    k.path([(15, 18), (6, 18), (5, 26)], 2)
    k.path([(21, 18), (30, 18)], 2)
    k.path([(18, 14), (18, 6), (9, 5)], 2)
    k.shaft()
    k.headframe(YS, half=2, height=6)
    # the winch beside the headframe, its rope run up to the frame
    for z in (22, 24):
        t.set(13, YS, z, S('spruce_fence', waterlogged=False))
        t.set(13, YS + 1, z, S('spruce_log', axis='y'))
    t.set(13, YS + 2, 22, S('stripped_spruce_log', axis='z'))
    t.set(13, YS + 2, 23, S('stripped_spruce_log', axis='z'))
    t.set(13, YS + 2, 24, S('stripped_spruce_log', axis='z'))
    t.set(14, YS + 2, 23, S('spruce_fence', waterlogged=False))
    t.set(13, YS, 23, 'minecraft:barrel[facing=up,open=false]')
    # the rail from the shaft to the shed, an ore bin at its end
    for x in range(21, 27):
        t.set(x, YS, 18, S('rail', shape='east_west', waterlogged=False))
    t.set(27, YS, 18, 'minecraft:hopper[enabled=true,facing=down]')
    t.set(27, YS, 19, 'minecraft:composter[level=0]')
    # the grading shed (east): posts, a canvas roof, the tables, the cutter, the crates going out
    x0, x1, z0, z1 = 26, 34, 13, 23
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (x0 + 4, z0), (x0 + 4, z1)):
        for y in range(YS, YS + 4):
            t.set(x, y, z, S('spruce_log', axis='y'))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            kk = min(z - z0, z1 - z)
            yy = YS + 4 + min(kk, 4) // 2
            if kk >= 4:
                t.set(x, yy, z, slab('or:canvas_slab'))
            elif kk % 2 == 0:
                t.set(x, yy, z, stairs('or:canvas_stairs', 'south' if z < (z0 + z1) / 2 else 'north'))
            else:
                t.set(x, yy, z, 'or:canvas')
            t.set(x, GT, z, 'minecraft:spruce_planks' if (x + z) % 5 else 'minecraft:coarse_dirt')
    k.grading_table(29, 15, 4)
    k.grading_table(29, 21, 4)
    t.set(33, YS, 17, 'minecraft:stonecutter[facing=west]')
    t.set(33, YS, 19, 'minecraft:chipped_anvil[facing=north]')
    t.set(33, YS, 18, S('grindstone', face='floor', facing='west'))
    mod.lectern(t, 31, YS, 18, facing='west', book=True)
    k.crates([(34, 15, 2), (34, 16, 1), (34, 21, 1), (34, 22, 2)])
    cluster(t, 34, YS + 1, 16)
    # the tailings heap (north-east)
    k.spoil(28.5, 6.5, 5.2, 5.0)
    # the camp: three tents, the cook fire, the drying line
    k.tent(4, 9, 22, open_end='x1')
    k.tent(4, 9, 28, open_end='x1')
    k.tent(11, 15, 30, open_end='x0')
    k.fire(13, 24)
    for x in (3, 8):
        for y in (YS, YS + 1, YS + 2):
            t.set(x, y, 33, S('spruce_fence', waterlogged=False))
    for x in range(4, 8):
        t.set(x, YS + 2, 33, S('iron_chain', axis='x', waterlogged=False))
    # the boss's tent (south-east) and the crates of graded crystal waiting for the buyer
    k.boss_tent(24, 26, 31, 32, door=[(27, 26), (28, 26)])
    mod.lectern(t, 29, YS, 30, facing='north', book=True)
    t.set(25, YS, 31, 'minecraft:iron_block')
    t.set(25, YS + 1, 31, S('iron_chain', axis='x', waterlogged=False))
    t.set(26, YS, 31, 'or:cargo_crate')
    t.set(30, YS, 27, S('barrel', facing='up', open=False))
    t.set(25, YS, 27, 'minecraft:red_carpet')
    t.set(25, YS, 28, 'minecraft:red_carpet')
    t.set(27, YS + 2, 29, 'minecraft:lantern[hanging=true,waterlogged=false]')
    mod.banner(t, 27, YS + 2, 32, 'north', base='black', patterns=[('minecraft:rhombus', 'cyan')])
    k.crates([(21, 28, 2), (22, 28, 1), (21, 29, 1), (33, 26, 2)])
    for (x, z) in ((21, 28), (22, 28), (21, 29)):
        top = YS + (2 if (x, z) == (21, 28) else 1)
        cluster(t, x, top, z)
    # the lookout (north-west)
    k.lookout(4, 4, 5)
    # lamps round the yard
    for (x, z) in ((14, 14), (22, 22), (12, 8), (24, 10), (16, 33), (6, 15)):
        k.lamp_post(x, z, 3)
    # a palisade of stakes on the inner ring, gaps for the ways in
    for i in range(2, CS[0] - 2):
        for (x, z) in ((i, 2), (i, CS[2] - 3), (2, i), (CS[0] - 3, i)):
            if abs(x - CC) <= 2 or abs(z - CC) <= 2:
                continue
            if C.is_airish(t, x, YS, z) and (i * 7 + x) % 5 != 0:
                t.set(x, YS, z, S('spruce_fence', waterlogged=False))
    k.finish([(29, 17), (12, 22), (18, 14)], light_region=(INSET, YS, INSET, CS[0] - 1 - INSET, TOP - 1, CS[2] - 1 - INSET))
    return k


# ---------------------------------------------------------------- camp 2: the sinkhole

def camp_v2(seed=64002):
    """The sinkhole: the camp round a collapsed hollow whose walls glitter with crystal; the shaft at its
    bottom, a ramp down, a crane over the hollow, the sorting tables on the rim, tents and the boss's tent."""
    k = Camp(seed, 2)
    t = k.t
    rr = Noise(seed + 9, 2.0)

    def hollow(x, z):
        d = math.hypot(x - CC, z - CC) + 1.2 * (rr.at(x, 0, z) - 0.5)
        if d < 3.2:
            return 1
        if d < 5.0:
            return 2
        if d < 6.6:
            return 3
        return None
    k.lay_ground(hollow)
    # the hollow's walls: bare rock with crystal veins and clusters catching the light
    for x in range(CC - 8, CC + 9):
        for z in range(CC - 8, CC + 9):
            top = k.floor[(x, z)]
            for (dx, dz, f) in ((1, 0, 'east'), (-1, 0, 'west'), (0, 1, 'south'), (0, -1, 'north')):
                nt = k.floor.get((x + dx, z + dz), GT)
                for y in range(nt + 1, top + 1):
                    # (x, z) is higher: its side face at height y looks into the hollow
                    if rr.at(x * 3, y * 3, z * 3) > 0.62:
                        t.set(x, y, z, 'or:crystal_veined_stone')
                    elif not C.name(t.get(x, y, z)) in ('minecraft:stone', 'minecraft:tuff', 'minecraft:andesite'):
                        t.set(x, y, z, k.rock(x, y, z))
                    if rr.at(x * 5, y * 5 + 50, z * 5) > 0.7 and C.is_airish(t, x + dx, y, z + dz) and \
                            math.hypot(x + dx - CC, z + dz - CC) > 2.3:
                        cluster(t, x + dx, y, z + dz, f)
    k.shaft(bottom_top=1)
    # a ramp of planks down into the hollow from the south
    for (z, y) in ((24, 4), (23, 3), (22, 2)):
        for x in (17, 18, 19):
            t.set(x, y, z, stairs('spruce_stairs', 'south'))
            for yy in range(y + 1, TOP + 1):
                if (x, yy, z) in t.cells and C.name(t.cells[(x, yy, z)]) != 'minecraft:air':
                    t.set(x, yy, z, C.AIR)
            for yy in range(0, y):
                if C.is_airish(t, x, yy, z):
                    t.set(x, yy, z, k.rock(x, yy, z))
    # the crane: a mast on the rim, a jib over the hollow, the chain and the bucket over the shaft
    mx, mz = CC + 7, CC - 1
    for y in range(YS, TOP):
        t.set(mx, y, mz, S('spruce_log', axis='y'))
    for x in range(CC - 1, mx + 2):
        t.set(x, TOP, mz, S('stripped_spruce_log', axis='x'))
    for (x, f) in ((mx - 1, 'east'), (mx + 1, 'west')):
        t.set(x, YS, mz, stairs('spruce_stairs', f))
    for y in range(4, TOP):
        t.set(CC, y, mz, S('iron_chain', axis='y', waterlogged=False))
    t.set(CC, 3, mz, 'minecraft:cauldron')
    t.set(mx + 1, YS, mz + 1, S('stripped_spruce_log', axis='z'))
    t.set(mx + 1, YS + 1, mz + 1, S('stripped_spruce_log', axis='z'))
    t.set(mx + 2, YS, mz, 'minecraft:barrel[facing=up,open=false]')
    # the rim: grading tables under an awning, the cutter, crates
    for x in range(26, 34):
        for z in range(23, 30):
            if z in (23, 29):
                t.set(x, YS + 3, z, stairs('or:canvas_stairs', 'south' if z == 23 else 'north'))
            else:
                t.set(x, YS + 3 + (1 if 25 <= z <= 27 else 0), z, slab('or:canvas_slab') if 25 <= z <= 27 else 'or:canvas')
    for (x, z) in ((26, 23), (33, 23), (26, 29), (33, 29)):
        for y in range(YS, YS + 3):
            t.set(x, y, z, S('spruce_fence', waterlogged=False))
    k.grading_table(27, 25, 5)
    k.grading_table(27, 27, 4)
    t.set(32, YS, 27, 'minecraft:stonecutter[facing=west]')
    mod.lectern(t, 32, YS, 25, facing='west', book=True)
    k.crates([(33, 26, 2), (33, 25, 1), (33, 27, 1)])
    # the tents (west and north) and the fire
    k.tent(3, 8, 9, open_end='x1')
    k.tent(3, 8, 15, open_end='x1')
    k.tent(9, 14, 4, open_end='x1')
    k.fire(10, 25)
    # the boss's tent (north-east), the strongbox, the buyer's crates
    k.boss_tent(24, 4, 31, 10, door=[(27, 10), (28, 10)])
    mod.lectern(t, 29, YS, 6, facing='south', book=True)
    t.set(25, YS, 5, 'minecraft:iron_block')
    t.set(26, YS, 5, 'or:cargo_crate')
    t.set(27, YS + 2, 7, 'minecraft:lantern[hanging=true,waterlogged=false]')
    mod.banner(t, 27, YS + 2, 4, 'south', base='black', patterns=[('minecraft:rhombus', 'cyan')])
    k.crates([(32, 12, 2), (33, 12, 1), (32, 13, 1)])
    cluster(t, 33, YS + 1, 12)
    cluster(t, 32, YS + 1, 13)
    # spoil thrown out of the hollow (west)
    k.spoil(6.5, 30.0, 4.4, 4.0)
    # the lookout (south-east)
    k.lookout(30, 31, 5)
    for (x, z) in ((11, 11), (25, 18), (11, 20), (22, 31), (20, 9)):
        k.lamp_post(x, z, 3)
    # a ring of stakes round the hollow's lip
    for (x, z) in C.ring_cells(CC, CC, 7.6, 1.0):
        if C.is_airish(t, x, YS, z) and k.floor.get((x, z)) == GT and not (17 <= x <= 19 and z > CC) and (x + z) % 3:
            t.set(x, YS, z, S('spruce_fence', waterlogged=False))
    k.finish([(29, 26), (9, 23), (14, 11)], light_region=(INSET, 2, INSET, CS[0] - 1 - INSET, TOP - 1, CS[2] - 1 - INSET))
    return k


# ---------------------------------------------------------------- shared by the descent and the heart

AIRS = ('minecraft:air', 'minecraft:cave_air')
# natural rock the re-skins may swap (never stairs, timber, props, worked stone of the trial chamber)
ROCK = {'minecraft:stone', 'minecraft:tuff', 'minecraft:andesite', 'minecraft:cobblestone', 'minecraft:gravel',
        'oldrepublic:crystal_veined_stone', 'minecraft:calcite', 'minecraft:deepslate', 'minecraft:cobbled_deepslate',
        'minecraft:dripstone_block', 'minecraft:smooth_basalt'}
DEEPSLATE = 'minecraft:deepslate[axis=y]'


def is_air_at(t, p):
    s = t.cells.get(p)
    return s is not None and C.name(s) in AIRS


LICHEN = S('glow_lichen', down=True, up=False, north=False, south=False, east=False, west=False, waterlogged=False)


def solid_rock(t, p):
    s = t.cells.get(p)
    return s is not None and C.name(s) in ROCK


NOT_STURDY = {'oldrepublic:lumen_moss', 'minecraft:glow_lichen', 'minecraft:pointed_dripstone', 'minecraft:grindstone',
              'minecraft:decorated_pot', 'minecraft:light', 'oldrepublic:meditation_stone', 'oldrepublic:resonant_seal'}
FACES = (((0, 1, 0), 'down'), ((0, -1, 0), 'up'), ((1, 0, 0), 'west'), ((-1, 0, 0), 'east'),
         ((0, 0, 1), 'north'), ((0, 0, -1), 'south'))


def attach_facing(t, p, prefer=('down', 'up', 'north', 'south', 'east', 'west'), avoid=()):
    """The facing of a cluster in air cell p grown from a solid neighbour (it points away from it), or None.
    Never from a cell in `avoid` (a seal that opens would drop it)."""
    x, y, z = p
    found = {}
    for (dx, dy, dz), facing in FACES:
        q = (x + dx, y + dy, z + dz)
        s = t.cells.get(q)
        if q in avoid or s is None or mod.walk_through(s):
            continue
        if C.name(s) not in AIRS and not mod.see_through(s) and C.name(s) not in NOT_STURDY:
            found[facing] = q
    for f in prefer:
        if f in found:
            return f
    return None


def dripstone(t, x, y, z, length, down=True):
    """A stalactite hanging from (x, y + 1, z) down `length` cells (or a stalagmite standing on y - 1)."""
    names = {1: ['tip'], 2: ['frustum', 'tip'], 3: ['base', 'frustum', 'tip'], 4: ['base', 'middle', 'frustum', 'tip']}
    parts = names[max(1, min(4, length))]
    for k, th in enumerate(parts):
        yy = y - k if down else y + k
        t.set(x, yy, z, S('pointed_dripstone', thickness=th, vertical_direction='down' if down else 'up', waterlogged=False))


def light_high(t, region, level=8, strength=12):
    """Like mod.light_fill, but the invisible light goes two cells over the dark floor when there is room
    (above a player's head), so the walking cells stay plain air."""
    from collections import deque
    floors = mod.floor_cells(t, region)
    lit = mod.light_map(t)
    added = {}
    for _ in range(4096):
        dark = [p for p in floors if lit.get(p, 0) < level]
        if not dark:
            break
        buckets = {}
        for p in dark:
            buckets.setdefault((p[0] // 5, p[1] // 4, p[2] // 5), []).append(p)
        cells = max(sorted(buckets.items()), key=lambda kv: len(kv[1]))[1]
        mx = sum(p[0] for p in cells) / len(cells)
        mz = sum(p[2] for p in cells) / len(cells)
        x, y, z = min(cells, key=lambda p: ((p[0] - mx) ** 2 + (p[2] - mz) ** 2, p))
        spot = (x, y, z)
        for k in (2, 1):
            col = [(x, y + j, z) for j in range(1, k + 1)]
            if all(is_air_at(t, q) and q not in added for q in col):
                spot = col[-1]
                break
        added[spot] = S('light', level=strength, waterlogged=False)
        if lit.get(spot, 0) < strength:
            lit[spot] = strength
            mod._spread(t, lit, deque([spot]), added)
    for p, s in added.items():
        t.set(*p, s)
    return sorted(added)


# ---------------------------------------------------------------- the descent

DS = (9, 30, 32)


def descent(seed=64101):
    """The poachers' stepped tunnel down to the heart, re-skinned in place: the shaft, the ladders, every
    stair, the timber sets and both jigsaws stay; the rock deepens from stone to tuff and deepslate as it
    goes down, crystal veins thicken, kyber grows in the upper corners and the lanterns follow the sets."""
    t = C.load_base('crystal_cave/descent', seed=seed)
    sx, sy, sz = t.size
    before = dict(t.cells)
    n = Noise(seed + 1, 4.0)
    vein = Noise(seed + 2, 3.0)
    top = C.mix(seed + 3, [('minecraft:stone', 5), ('minecraft:andesite', 3), ('minecraft:tuff', 1),
                           ('minecraft:cobblestone', 1)], 2.5)
    mid = C.mix(seed + 4, [('minecraft:stone', 4), ('minecraft:tuff', 4), ('minecraft:andesite', 1), (DEEPSLATE, 1)], 2.5)
    low = C.mix(seed + 5, [('minecraft:tuff', 4), (DEEPSLATE, 4), ('minecraft:cobbled_deepslate', 1),
                           ('minecraft:calcite', 1)], 2.5)
    # the open cells (feet, heads and the shaft) never change; floors are the stairs
    open_cells = {p for p, s in before.items() if C.name(s) in AIRS}
    for p in sorted(before):
        if not solid_rock(t, p):
            continue
        x, y, z = p
        if not any(is_air_at(t, (x + dx, y + dy, z + dz)) for (dx, dy, dz), _ in FACES):
            continue
        d = 1 - y / (sy - 1) + 0.18 * (n.at(x, y, z) - 0.5)
        v = abs(vein.at(x * 2, y, z * 2) - 0.5)
        if v < 0.035 + 0.09 * d:
            st = 'or:crystal_veined_stone'
        elif d > 0.4 and v < 0.07 + 0.09 * d:
            st = 'minecraft:calcite'
        elif d < 0.3:
            st = top(x, y, z)
        elif d < 0.66:
            st = mid(x, y, z)
        else:
            st = low(x, y, z)
        t.set(x, y, z, st)
    # kyber in the upper corners: the top open cell of a column, against the wall, more of it lower down
    for p in sorted(open_cells):
        x, y, z = p
        if x not in (3, 5) or z < 6 or not t.inside(x, y + 1, z) or is_air_at(t, (x, y + 1, z)):
            continue
        if C.name(t.get(x, y - 1, z)) in AIRS and C.name(t.get(x, y - 2, z)) in AIRS and \
                C.name(t.get(x, y - 3, z)) in AIRS:
            d = 1 - y / (sy - 1)
            if t.rng.random() < 0.1 + 0.85 * d:
                wall = (x - 1 if x == 3 else x + 1, y, z)
                f = 'east' if x == 3 else 'west'
                if t.rng.random() < 0.5 or not solid_rock(t, wall):
                    f = 'down'
                cluster(t, x, y, z, f)
                if solid_rock(t, (x, y + 1, z)):
                    t.set(x, y + 1, z, 'or:crystal_veined_stone')
    # the steps were cut from the rock they stand in: cobblestone above, tuff in the middle, deepslate below
    for p in sorted(before):
        st = t.cells.get(p)
        if C.name(st) != 'minecraft:cobblestone_stairs':
            continue
        x, y, z = p
        d = 1 - y / (sy - 1) + 0.2 * (n.at(x * 2, y, z * 2) - 0.5)
        if d > 0.4:
            name = 'minecraft:cobbled_deepslate_stairs' if d > 0.68 else 'minecraft:tuff_stairs'
            t.set(x, y, z, name + st[len('minecraft:cobblestone_stairs'):])
    # lanterns hang from the timber sets the old tunnel left dark, and over the landing
    for (x, y, z) in ((3, 25, 7), (3, 17, 15), (3, 9, 23), (5, 4, 30)):
        if is_air_at(t, (x, y, z)) and not is_air_at(t, (x, y + 1, z)):
            t.set(x, y, z, 'minecraft:lantern[hanging=true,waterlogged=false]')
    # the landing: where the rail ends, a niche cut into the east wall holds the cut crystal waiting to go
    # up; the west wall opposite is the vein they were working, pick-scarred
    for y in (1, 2):
        for z in (29, 30):
            t.set(6, y, z, C.AIR)
    t.set(6, 1, 29, 'or:cargo_crate')
    t.set(6, 1, 30, S('barrel', facing='up', open=False))
    cluster(t, 6, 2, 29, 'up')
    for (y, z) in ((2, 29), (3, 29), (2, 30), (3, 30), (1, 30)):
        if solid_rock(t, (2, y, z)):
            t.set(2, y, z, 'or:crystal_veined_stone' if (y + z) % 2 else 'minecraft:cobbled_deepslate')
    cluster(t, 3, 4, 29, 'down')
    light_high(t, (2, 1, 1, 6, sy - 2, sz - 1), level=8, strength=11)
    return t


# ---------------------------------------------------------------- the heart

SEAL = [(x, y, 29) for x in (21, 22, 23) for y in (1, 2, 3)]
STONE = (22, 1, 38)
CHEST = (38, 1, 2)
VEIN = [(x, y, 7) for x in (37, 38, 39) for y in (1, 2, 3)]
ENTRY = (22, 1, 0)
UNSTABLE = [(38, 3, 10), (40, 1, 13)]
# the walked lines (PlaythroughTest walks these as straight lines): in, through the seal to the stone; the
# side route round the cavern's east end, up the unstable passage to the veined wall and the chest
MAIN = [(22, 0), (22, 5), (22, 14), (22, 22), (22, 27), (22, 31), (22, 35), (22, 38)]
SIDE = [(22, 26), (26, 18), (31, 18), (38, 18), (38, 9), (38, 3)]
GOALS = [(22, 1, 5), (22, 1, 14), (22, 1, 22), (22, 1, 27), (22, 1, 31), (22, 1, 35), (21, 1, 38), (23, 1, 38),
         (22, 1, 37), (22, 1, 39), (22, 1, 26), (26, 1, 18), (31, 1, 18), (38, 1, 18), (38, 1, 9), (38, 1, 6),
         (38, 1, 4), (38, 1, 3)]
CHAMBER_R = 9.5        # WorldBalance.chamberRadius: the trial's arena round the stone
HIDDEN_BOX = (33, 0, 0, 43, 5, 7)
DIG_BOX = (25, 0, 19, 33, 6, 26)    # the poachers' cutting: no stalagmites or loose crystal on its floor


def seg_dist(px, pz, a, b):
    ax, az = a
    bx, bz = b
    dx, dz = bx - ax, bz - az
    l2 = dx * dx + dz * dz
    u = 0.0 if l2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / l2))
    return math.hypot(px - (ax + u * dx), pz - (az + u * dz))


def route_dist(x, z):
    return min(seg_dist(x, z, a, b) for line in (MAIN, SIDE) for a, b in zip(line, line[1:]))


def in_chamber(x, z, r=CHAMBER_R):
    return z >= 29 and math.hypot(x - STONE[0], z - STONE[2]) <= r


def in_box(p, box):
    x0, y0, z0, x1, y1, z1 = box
    return x0 <= p[0] <= x1 and y0 <= p[1] <= y1 and z0 <= p[2] <= z1


def heart_walk(t, through_wall=True):
    """Feet cells reachable from the entrance tunnel (the seal open; the veined wall mined or not)."""
    return C.reach(t, (22, 1, 1), passable=set(SEAL + (VEIN if through_wall else [])))


def hidden_reachable(t, start=(38, 1, 9), goal=(38, 1, 3)):
    """PlaythroughTest.walkableSteps: can the chest be reached from the side route without mining (steps up
    one block with head room, down three)? Cells without collision are passable."""
    from collections import deque

    def passable(p):
        s = t.cells.get(p)
        if s is None:
            return False      # the rock round the template
        n = C.name(s)
        return n in AIRS or n == 'minecraft:light' or mod.walk_through(s)
    seen = {start}
    q = deque([start])
    while q:
        cur = q.popleft()
        if cur[0] == goal[0] and cur[2] == goal[2] and abs(cur[1] - goal[1]) <= 1:
            return True
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (1, 0, -1, -2, -3):
                nxt = (cur[0] + dx, cur[1] + dy, cur[2] + dz)
                if nxt in seen or not t.inside(*nxt):
                    continue
                head = dy <= 0 or passable((cur[0], cur[1] + 2, cur[2]))
                if head and passable(nxt) and passable((nxt[0], nxt[1] + 1, nxt[2])) and \
                        not passable((nxt[0], nxt[1] - 1, nxt[2])):
                    seen.add(nxt)
                    q.append(nxt)
                    break
                if not passable(nxt):
                    break
    return False


class Heart:
    def __init__(self, seed):
        self.t = C.load_base('crystal_cave/heart', seed=seed)
        self.seed = seed
        self.rng = self.t.rng
        t = self.t
        self.base = dict(t.cells)
        self.base_nbt = dict(t.nbt)
        self.walk = heart_walk(t)
        missing = [g for g in GOALS if g not in self.walk]
        if missing:
            raise ValueError('heart base: goals not walkable %s' % missing)
        self.mech = set(SEAL + VEIN + UNSTABLE + [STONE, CHEST, ENTRY])
        # the hidden room and its shell (two cells deep) stay exactly as they are, apart from its furniture
        self.hidden = {p for p in t.cells if in_box(p, HIDDEN_BOX)}
        # the route: feet, head and the cell above along the walked lines (3 to 5 wide) and in the arena
        self.route = set()
        self.floors = set()
        for (x, y, z) in self.walk:
            if route_dist(x, z) <= 2.0 or in_chamber(x, z):
                self.route.update(((x, y, z), (x, y + 1, z), (x, y + 2, z)))
                self.floors.add((x, y - 1, z))
        self.cave = {p for p, s in t.cells.items() if C.name(s) in AIRS}
        self.placed = {}

    # ------------------------------------------------------------ what may change

    def free(self, p):
        """An open cell off the route where detail may grow."""
        return p in self.cave and p not in self.route and p not in self.mech and p not in self.hidden and \
            is_air_at(self.t, p) and not (in_chamber(p[0], p[2], 8.6) and p[1] <= 2)

    def reskinnable(self, p):
        return solid_rock(self.t, p) and p not in self.mech and p not in self.hidden

    def put(self, p, state):
        self.t.set(*p, state)
        self.placed[p] = state

    def cluster_at(self, p, prefer=('down', 'up', 'north', 'south', 'east', 'west')):
        if not self.free(p):
            return False
        f = attach_facing(self.t, p, prefer, avoid=self.mech)
        if f is None:
            return False
        self.put(p, S('or:kyber_cluster', facing=f))
        return True

    # ------------------------------------------------------------ rock

    def reskin(self):
        """The cavern's skin: tuff and deepslate low, stone and andesite in the walls, dripstone and calcite in
        the roof, thin crystal veins with calcite halos; never the worked stone of the trial chamber."""
        t = self.t
        s = self.seed
        low = C.mix(s + 1, [('minecraft:tuff', 4), ('minecraft:stone', 3), (DEEPSLATE, 2),
                            ('minecraft:cobbled_deepslate', 1)], 3.0)
        wall = C.mix(s + 2, [('minecraft:stone', 5), ('minecraft:andesite', 3), ('minecraft:tuff', 2)], 3.0)
        roof = C.mix(s + 3, [('minecraft:stone', 4), ('minecraft:andesite', 3), ('minecraft:tuff', 2),
                             ('minecraft:calcite', 1)], 3.0)
        n = Noise(s + 4, 3.0)
        vein = Noise(s + 5, 4.0)
        for p in sorted(self.t.cells):
            if not self.reskinnable(p):
                continue
            x, y, z = p
            if not any(is_air_at(t, (x + dx, y + dy, z + dz)) for (dx, dy, dz), _ in FACES):
                continue
            v = abs(vein.at(x * 1.5, y * 2, z * 1.5) - 0.5)
            h = y + 5 * (n.at(x, y, z) - 0.5)
            if v < 0.022:
                st = 'or:crystal_veined_stone'
            elif v < 0.045 and h > 2:
                st = 'minecraft:calcite'
            elif h < 3:
                st = low(x, y, z)
            elif h < 9:
                st = wall(x, y, z)
            else:
                st = roof(x, y, z)
            t.set(x, y, z, st)

    def chamber_floor(self):
        """The trial chamber's floor: worn tiles in patches, the old rings of polished tuff gone (a pattern drawn
        on the floor); the walls, bands and the stone stay."""
        t = self.t
        mix = C.mix(self.seed + 7, [('minecraft:deepslate_tiles', 5), ('minecraft:cracked_deepslate_tiles', 3),
                                    ('minecraft:polished_deepslate', 2), ('minecraft:deepslate_bricks', 2),
                                    ('minecraft:cracked_deepslate_bricks', 1)], 2.2)
        for x in range(t.sx):
            for z in range(29, t.sz):
                p = (x, 0, z)
                if p in self.mech or not in_chamber(x, z, 9.0) or C.name(t.cells.get(p)) in AIRS or p not in t.cells:
                    continue
                above = t.cells.get((x, 1, z))
                if above is None or not (C.name(above) in AIRS or (x, 1, z) in self.mech or
                                         C.name(above) == 'oldrepublic:kyber_cluster'):
                    continue
                t.set(x, 0, z, mix(x, 0, z))

    def chamber_walls(self):
        """Kyber working out of the chamber's joints above head height and out of the dome, one cluster hanging
        over the stone; the old ring of clusters set out on the floor round the stone goes (a ring laid on the
        floor is a pattern, docs/STRUCTURES.md 3)."""
        t = self.t
        for p in sorted(self.cave | set(t.cells)):
            if p[1] == 1 and C.name(t.cells.get(p)) == 'oldrepublic:kyber_cluster' and in_chamber(p[0], p[2], 7.0):
                t.set(*p, 'minecraft:cave_air')
                self.cave.add(p)
        t.set(STONE[0], 8, STONE[2], S('or:kyber_cluster', facing='down'))
        n = Noise(self.seed + 8, 2.0)
        for p in sorted(self.cave):
            x, y, z = p
            if not in_chamber(x, z, 10.5) or y < 3 or not self.free(p):
                continue
            f = attach_facing(t, p, ('down', 'north', 'south', 'east', 'west'))
            if f is None or n.at(x * 3, y * 3, z * 3) < 0.66:
                continue
            self.put(p, S('or:kyber_cluster', facing=f))
            # the joint it grew from turns to crystal
            dx, dy, dz = {'down': (0, 1, 0), 'up': (0, -1, 0), 'west': (1, 0, 0), 'east': (-1, 0, 0),
                          'north': (0, 0, 1), 'south': (0, 0, -1)}[f]
            q = (x + dx, y + dy, z + dz)
            if q not in self.mech and C.name(t.cells.get(q)) in ('minecraft:deepslate_tiles', 'minecraft:polished_deepslate'):
                t.set(*q, 'or:crystal_veined_stone')
        # the deepslate tiles of the walls crack in patches
        crack = Noise(self.seed + 9, 3.0)
        for p in sorted(t.cells):
            x, y, z = p
            if p in self.mech or not in_chamber(x, z, 11.0) or y < 1:
                continue
            if C.name(t.cells[p]) == 'minecraft:deepslate_tiles' and crack.at(x, y, z) > 0.6:
                t.set(x, y, z, 'minecraft:cracked_deepslate_tiles')

    # ------------------------------------------------------------ crystal

    def seal_mouth(self):
        """Kyber crowding round the mouth of the passage to the seal, thickest over it: the way on is the way the
        crystal grows."""
        t = self.t
        n = Noise(self.seed + 14, 1.4)
        for x in range(15, 30):
            for y in range(1, 10):
                for z in range(23, 27):
                    p = (x, y, z)
                    d = math.hypot(x - 22, (y - 3) * 1.3)
                    chance = 0.8 if d < 4.5 else (0.45 if d < 7 else 0.0)
                    if chance and n.at(x * 2, y * 2, z * 2) > 0.3 and self.rng.random() < chance:
                        if self.cluster_at(p, ('north', 'down', 'east', 'west')):
                            q = (x, y, z + 1)
                            if self.reskinnable(q) and self.rng.random() < 0.6:
                                t.set(*q, 'or:crystal_veined_stone')

    def surface_cells(self):
        """Solid cells of the skin facing a free open cell."""
        t = self.t
        out = []
        for p in sorted(t.cells):
            if not self.reskinnable(p):
                continue
            x, y, z = p
            if any(self.free((x + dx, y + dy, z + dz)) for (dx, dy, dz), _ in FACES):
                out.append(p)
        return out

    def pockets(self, count=14, spacing=6.5):
        """Crystal pockets on walls and roof away from the walked lines: an irregular blotch of crystal-veined
        stone mottled with calcite, kyber grown out of it into the cave."""
        t = self.t
        n = Noise(self.seed + 12, 1.6)
        fine = Noise(self.seed + 13, 0.9)
        cands = [p for p in self.surface_cells() if p[1] >= 2 and route_dist(p[0], p[2]) > 3.0 and
                 not in_chamber(p[0], p[2], 11.0) and not in_box(p, (32, 0, 0, 44, 6, 9))]
        self.rng.shuffle(cands)
        centres = []
        for p in cands:
            if all(math.dist(p, c) >= spacing for c in centres):
                centres.append(p)
            if len(centres) >= count:
                break
        for c in centres:
            r = 1.6 + 1.4 * self.rng.random()
            box = [(x, y, z) for x in range(c[0] - 4, c[0] + 5) for y in range(c[1] - 4, c[1] + 5)
                   for z in range(c[2] - 4, c[2] + 5)]
            for p in box:
                d = math.dist(p, c) + 1.8 * (n.at(*p) - 0.5)
                if d < r and self.reskinnable(p):
                    t.set(*p, 'or:crystal_veined_stone' if fine.at(*p) > 0.36 else 'minecraft:calcite')
            for p in box:
                d = math.dist(p, c) + 1.8 * (n.at(*p) - 0.5)
                if d < r + 0.9 and self.rng.random() < 0.7:
                    self.cluster_at(p)
        return centres

    def spires(self, spots):
        """Crystal-crusted columns standing on the floor off the walked lines: veined stone with kyber on its
        sides and top, a calcite foot."""
        t = self.t
        for (x, z, h) in spots:
            y = next((yy for yy in range(1, t.sy - 1) if self.free((x, yy, z)) and
                      not is_air_at(t, (x, yy - 1, z))), None)
            if y is None:
                continue
            if any(not self.free((x, y + k, z)) for k in range(h + 1)):
                continue
            for k in range(h):
                self.put((x, y + k, z), 'or:crystal_veined_stone')
            self.cluster_at((x, y + h, z), ('up',))
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y - 1, z + dz)
                if self.reskinnable(q):
                    t.set(*q, 'minecraft:calcite')
                for k in range(h):
                    if self.rng.random() < 0.45:
                        self.cluster_at((x + dx, y + k, z + dz), ({(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south',
                                                                (0, -1): 'north'}[(dx, dz)],))

    def stalactites(self, density=0.07):
        t = self.t
        for p in sorted(self.cave):
            x, y, z = p
            if not self.free(p) or is_air_at(t, (x, y + 1, z)) or (x, y + 1, z) not in t.cells:
                continue
            if route_dist(x, z) < 2.5 or in_chamber(x, z, 10.5):
                continue
            gap = 0
            while self.free((x, y - gap - 1, z)) and gap < 12:
                gap += 1
            if gap < 5 or self.rng.random() > density:
                continue
            dripstone(t, x, y, z, min(gap - 4, 1 + int(self.rng.random() * 4)))
            if self.reskinnable((x, y + 1, z)):
                t.set(x, y + 1, z, 'minecraft:dripstone_block')

    def stalagmites(self, density=0.035):
        t = self.t
        for p in sorted(self.cave):
            x, y, z = p
            if not self.free(p) or is_air_at(t, (x, y - 1, z)) or (x, y - 1, z) not in t.cells or in_box(p, DIG_BOX):
                continue
            if route_dist(x, z) < 3.0 or C.name(t.get(x, y - 1, z)) == 'minecraft:pointed_dripstone':
                continue
            if self.rng.random() > density:
                continue
            h = 1 + int(self.rng.random() * 3)
            if all(self.free((x, y + k, z)) for k in range(h + 2)):
                dripstone(t, x, y, z, h, down=False)

    def moss(self):
        """Glow lichen in the damp low corners, where the light of the crystals falls (not lumen moss: that is
        the Jedi ruin's counted resource, spec 3.2, and the cave never held any)."""
        t = self.t
        n = Noise(self.seed + 11, 3.0)
        for p in sorted(self.cave):
            x, y, z = p
            if not self.free(p) or route_dist(x, z) < 2.5:
                continue
            below = t.cells.get((x, y - 1, z))
            if below is None or C.name(below) not in ROCK:
                continue
            if n.at(x, y, z) > 0.62:
                self.put(p, LICHEN)

    def scatter(self, density=0.02):
        """Single clusters on the floor and the walls of the open cave."""
        for p in sorted(self.cave):
            if route_dist(p[0], p[2]) < 2.5 or in_box(p, DIG_BOX):
                continue
            if self.rng.random() < density:
                self.cluster_at(p, ('up', 'north', 'south', 'east', 'west', 'down'))

    # ------------------------------------------------------------ people

    def poachers_dig(self):
        """The poachers' cutting at the cavern's east end, under their gantry (the scaffold, the anvil, the
        chain on its bars): the cut crystal crated, a dead fire and a bedroll. They never got past the seal."""
        t = self.t
        for p, st in (((31, 1, 23), 'or:cargo_crate'), ((30, 1, 24), 'or:cargo_crate_republic'),
                      ((27, 1, 24), S('campfire', facing='north', lit=False, signal_fire=False, waterlogged=False)),
                      ((26, 1, 25), 'minecraft:gray_carpet'), ((27, 1, 25), 'minecraft:gray_carpet'),
                      ((28, 1, 23), S('barrel', facing='up', open=False))):
            if not self.free(p):
                raise ValueError('heart: the dig cell %s is taken' % (p,))
            self.put(p, st)
        for p in ((31, 2, 23), (30, 2, 24)):
            self.cluster_at(p, ('up',))

    def keepers_cell(self):
        """The hidden room was someone's cell long before the poachers: the bedroll, the low table, the barrel
        were there; a few kyber laid out on a shelf, candles, a pot."""
        t = self.t
        t.set(42, 1, 3, S('decorated_pot', cracked=True, facing='west', waterlogged=False))
        t.set(41, 1, 2, S('candle', candles=3, lit=True, waterlogged=False))
        t.set(34, 1, 2, S('candle', candles=2, lit=True, waterlogged=False))
        for z in (3, 4):
            t.set(34, 2, z, S('spruce_trapdoor', facing='east', half='top', open=False, powered=False, waterlogged=False))
            t.set(34, 3, z, S('or:kyber_cluster', facing='up'))

    # ------------------------------------------------------------ checks

    def problems(self):
        """Route exact: mechanism cells as they were, the walked cells' feet, head and the cell above unchanged
        (light counts as air), their floors still full blocks; every goal walkable; the chest still hidden."""
        t = self.t
        out = []
        for p in sorted(self.mech):
            if t.cells.get(p) != self.base.get(p) or t.nbt.get(p) != self.base_nbt.get(p):
                out.append('mechanism cell %s changed: %s -> %s' % (p, self.base.get(p), t.cells.get(p)))

        def norm(s):
            return 'minecraft:air' if s is not None and C.name(s) in AIRS + ('minecraft:light',) else s
        for p in sorted(self.route):
            if norm(t.cells.get(p)) != norm(self.base.get(p)):
                out.append('route cell %s changed: %s -> %s' % (p, self.base.get(p), t.cells.get(p)))
        for p in sorted(self.floors):
            if C._kind(t.cells.get(p)) != C._kind(self.base.get(p)):
                out.append('route floor %s changed kind: %s -> %s' % (p, self.base.get(p), t.cells.get(p)))
        walk = heart_walk(t)
        for g in GOALS:
            if g not in walk:
                out.append('goal %s is no longer walkable' % (g,))
        if hidden_reachable(t):
            out.append('the hidden chamber can be reached without mining the veined wall')
        return out


def heart(seed=64201):
    """The heart re-skinned in place: the route, the seal, the meditation stone, the veined wall and the hidden
    chest exactly where they were; crystal, stone and detail only where the route is not."""
    h = Heart(seed)
    h.reskin()
    h.chamber_floor()
    h.chamber_walls()
    h.poachers_dig()
    h.seal_mouth()
    h.pockets()
    h.spires([(15, 21, 3), (30, 11, 4), (17, 25, 2), (27, 13, 2), (34, 25, 3)])
    h.stalactites()
    h.stalagmites()
    h.scatter()
    h.moss()
    h.keepers_cell()
    light_high(h.t, (1, 1, 0, h.t.sx - 2, h.t.sy - 2, h.t.sz - 1), level=8, strength=12)
    return h


# ---------------------------------------------------------------- build

def high_cells(t):
    """Cells inside the clear inset above TOP that are not air: the camp element would clear them."""
    return sorted(p for p, st in t.cells.items() if p[1] > TOP and INSET <= p[0] < CS[0] - INSET and
                  INSET <= p[2] < CS[2] - INSET and C.name(st) != 'minecraft:air')


def descent_problems(t):
    """Both jigsaws as they were, and the tunnel still walks from the shaft's foot to the heart's door."""
    base = C.load_base('crystal_cave/descent')
    out = []
    if t.size != base.size:
        out.append('descent size %s, was %s' % (t.size, base.size))
    for p in ((4, 29, 4), (4, 1, 31)):
        if t.cells.get(p) != base.cells.get(p) or t.nbt.get(p) != base.nbt.get(p):
            out.append('descent jigsaw %s changed' % (p,))
    out += C.route_problems(t, (4, 23, 6), [(4, 1, 30), (3, 1, 30), (5, 1, 31), (4, 12, 17)], label='descent')
    return out


def build(a):
    out = {}
    if C.wanted(a, 'camp') and not (a.only or '').endswith('_2'):
        k = camp_v1()
        for p in C.route_problems(k.t, (18, YS, 35), [(18, YS, 21), (29, YS, 17), (12, YS, 22), (27, YS, 29), (6, YS, 22),
                                                      (16, YS, 18)], label='camp'):
            print('CHECK', p)
        for p in high_cells(k.t):
            print('CHECK camp: above the cleared height', p)
        out[k.tid] = C.save(k.t, k.tid)
    if C.wanted(a, 'camp_2'):
        k = camp_v2()
        for p in C.route_problems(k.t, (18, YS, 35), [(18, 2, 19), (29, YS, 26), (9, YS, 23), (27, YS, 7), (6, YS, 9),
                                                      (16, 2, 18)], label='camp_2'):
            print('CHECK', p)
        for p in high_cells(k.t):
            print('CHECK camp_2: above the cleared height', p)
        out[k.tid] = C.save(k.t, k.tid)
    if C.wanted(a, 'descent'):
        t = descent()
        t.finish()
        for p in descent_problems(t):
            print('CHECK', p)
        out['crystal_cave/descent'] = C.save(t, 'crystal_cave/descent')
    if C.wanted(a, 'heart'):
        h = heart()
        h.t.finish()
        for p in h.problems():
            print('CHECK heart:', p)
        out['crystal_cave/heart'] = C.save(h.t, 'crystal_cave/heart')
    for k_, t in out.items():
        print(k_, t.size, t.count())
    return out


def structure_check():
    """Assemble the cave as worldgen would (every camp, every rotation) and walk it composed: from the top of
    the stepped tunnel down into the heart, through the seal to the stone and round the side route to the
    chest (the seal open and the veined wall mined). The ladders of the shaft are not walked."""
    from structlib import jigsaw as JG
    from structlib.template import _rot_xz
    res = JG.assemble(**JG.structure_args('oldrepublic:crystal_cave'))
    rots = {'none': 0, 'clockwise_90': 1, 'clockwise_180': 2, 'counterclockwise_90': 3}
    problems = []
    for start, rot, pieces in res.runs:
        temps = {p.template.split(':', 1)[1]: C.load_saved(p.template.split(':', 1)[1]) for p in pieces}
        big, off = C.compose(pieces, temps)
        where = {p.template.split(':', 1)[1]: p for p in pieces}

        def w(tid, cell):
            p = where[tid]
            src = temps[tid]
            rx, rz = _rot_xz(cell[0], cell[2], src.sx, src.sz, rots[p.rotation])
            return (p.box[0][0] + off[0] + rx, p.box[0][1] + off[1] + cell[1], p.box[0][2] + off[2] + rz)
        if 'crystal_cave/heart' not in where or 'crystal_cave/descent' not in where:
            problems.append('%s %s: no heart or no descent' % (start, rot))
            continue
        passable = [w('crystal_cave/heart', c) for c in SEAL + VEIN]
        goals = [w('crystal_cave/heart', g) for g in GOALS]
        probs = C.route_problems(big, w('crystal_cave/descent', (4, 23, 6)), goals, passable=passable,
                                 label='%s %s' % (start, rot))
        problems += probs
        print('%s %s: %s' % (start, rot, 'tunnel -> heart -> stone -> chest ok' if not probs else '%d problems' % len(probs)))
    for p in problems:
        print('CHECK', p)
    return problems


def main():
    build(C.args())


if __name__ == '__main__':
    main()
