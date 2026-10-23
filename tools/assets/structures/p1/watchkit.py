"""P1's Watch building kit: the walls, windows, doors, roofs, arcades, ruin and furnishings of the Watch's
old buildings on Selvaren, shared by the cloister, the ridge tower, the fold and the causeway.

A Watch building is pale watch-stone ashlar on a projecting plinth of field stone, with buttresses that
stand proud, a string course of polished andesite under the eaves, lancet windows with stone sills and
lintels, and a steep roof of deepslate tiles gone green with moss. Ruin removes what weather and time would
(roof first, then the upper courses) and drops it below as rubble in the direction it fell.
"""
import math

from selvaren_lib import (AIR, B, FIELD, Noise, Palette, S, WATCH, nature, slab, stair)

ROOF = Palette(
    tile=[('minecraft:deepslate_tile_stairs', 8), ('minecraft:cobbled_deepslate_stairs', 2),
          ('minecraft:deepslate_brick_stairs', 1)],
    body=[('minecraft:deepslate_tiles', 6), ('minecraft:cobbled_deepslate', 2), ('minecraft:moss_block', 1)],
    ridge='minecraft:deepslate_tile_slab',
)
RUBBLE = [('minecraft:cobblestone', 3), ('minecraft:mossy_cobblestone', 3), ('minecraft:andesite', 1),
          ('minecraft:gravel', 1)]
RUBBLE_LOOSE = [('minecraft:cobblestone_slab[type=bottom,waterlogged=false]', 3),
                ('minecraft:mossy_cobblestone_slab[type=bottom,waterlogged=false]', 3),
                ('minecraft:stone_brick_slab[type=bottom,waterlogged=false]', 2),
                ('oldrepublic:watch_stone_slab[type=bottom,waterlogged=false]', 2),
                ('minecraft:cobblestone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]', 1),
                ('minecraft:mossy_stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]', 1)]
DIRS = ('north', 'east', 'south', 'west')
STEP = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}


def pickw(choices, r):
    total = sum(w for _, w in choices)
    r *= total
    for s, w in choices:
        r -= w
        if r <= 0:
            return s
    return choices[-1][0]


class Kit:
    """The palettes of one template, by noise patches seeded from `seed`."""

    def __init__(self, t, seed):
        self.t = t
        self.seed = seed
        self.rng = t.rng
        self.wall = WATCH.patches('wall', seed + 1, 3.5)
        self.old = WATCH.patches('old', seed + 2, 3.0)
        self.plinth = WATCH.patches('plinth', seed + 3, 2.5)
        self.floor = WATCH.patches('floor', seed + 4, 3.0)
        self.tile = ROOF.patches('tile', seed + 5, 4.0)
        self.roof_body = ROOF.patches('body', seed + 6, 3.0)
        self.field = FIELD.patches('stone', seed + 7, 2.0)
        self.n = Noise(seed + 8, 4.0)
        self.moss_n = Noise(seed + 9, 3.0)

    def roof_tile(self, x, y, z, shade=0.0):
        """A roof tile stair's block: the slate, or moss in patches where the noise and the shade let it grow
        (shade: about 0.1 on a north slope, more on the low courses and under trees)."""
        v = 0.7 * self.moss_n.at(x, y, z) + 0.3 * self.moss_n._h(x, y, z) + shade
        if v > 0.68:
            return 'minecraft:mossy_cobblestone_stairs'
        if v > 0.62:
            return 'minecraft:mossy_stone_brick_stairs'
        return self.tile(x, y, z)

    # ------------------------------------------------------------ walls

    def wall_cell(self, x, y, z, y0, h):
        """The block of a wall cell `y - y0` courses up a wall of h courses."""
        k = y - y0
        if k == 0:
            return self.plinth(x, y, z)
        if k == h - 1:
            return WATCH['trim']
        return self.wall(x, y, z)

    def walls(self, rect, y0, h, sides=DIRS, thick=1, keep=None):
        """The perimeter walls of rect (x0, z0, x1, z1, inclusive) from course y0, h courses high. keep(x, y, z)
        False leaves a cell out (ruin)."""
        x0, z0, x1, z1 = rect
        t = self.t
        for (x, z) in perimeter(rect, sides, thick):
            for y in range(y0, y0 + h):
                if keep and not keep(x, y, z):
                    continue
                t.set(x, y, z, self.wall_cell(x, y, z, y0, h))

    def plinth_skirt(self, rect, y, sides=DIRS):
        """A projecting plinth: stairs at the foot of the walls, sloping away from them."""
        x0, z0, x1, z1 = rect
        t = self.t
        for side in sides:
            dx, dz = STEP[side]
            for (x, z) in side_cells(rect, side):
                px, pz = x + dx, z + dz
                if t.get(px, y, pz) in (None, AIR) or B.name_of(t.get(px, y, pz)) in GROUND_NAMES:
                    t.set(px, y, pz, stair(pickw([('minecraft:cobblestone_stairs', 2),
                                                  ('minecraft:mossy_cobblestone_stairs', 2),
                                                  ('minecraft:stone_brick_stairs', 1)], self.n.at(px, y, pz)),
                                           B.opposite(side)))

    def buttresses(self, rect, y0, h, every=4, sides=DIRS, skip=()):
        """Buttresses standing a cell proud of the walls every `every` cells, stepping back under a sloped cap."""
        t = self.t
        x0, z0, x1, z1 = rect
        for side in sides:
            dx, dz = STEP[side]
            cells = side_cells(rect, side)
            for i, (x, z) in enumerate(cells):
                if i == 0 or i == len(cells) - 1 or i % every:
                    continue
                if (x, z) in skip:
                    continue
                px, pz = x + dx, z + dz
                top = y0 + h - 3
                for y in range(y0, top):
                    t.set(px, y, pz, self.plinth(px, y, pz) if y == y0 else self.wall(px, y, pz))
                t.set(px, top, pz, stair('oldrepublic:watch_stone_stairs', B.opposite(side)))

    def window(self, x, y, z, side, h=2, glass=True):
        """A lancet window in a wall cell facing `side`: open or glazed, a sill below, a pointed head above."""
        t = self.t
        dx, dz = STEP[side]
        for k in range(h):
            if glass:
                t.set(x, y + k, z, S('minecraft:glass_pane', **pane_props(side)))
            else:
                t.set(x, y + k, z, AIR)
        sx, sz = x + dx, z + dz
        if t.get(sx, y - 1, sz) in (None, AIR):
            t.set(sx, y - 1, sz, stair('oldrepublic:watch_stone_stairs', B.opposite(side)))
        t.set(x, y + h, z, 'minecraft:chiseled_stone_bricks')

    def door(self, x, y, z, side, width=2, height=3, arch=True, fill=None):
        """A doorway through a wall (cells from (x, z) along the wall), with a round head of upside-down stairs."""
        t = self.t
        ax, az = along(side)
        cells = [(x + ax * i, z + az * i) for i in range(width)]
        for (cx, cz) in cells:
            for k in range(height):
                t.set(cx, y + k, cz, fill or AIR)
        if arch and width >= 2:
            a, b = cells[0], cells[-1]
            first = 'west' if ax else 'north'
            last = 'east' if ax else 'south'
            t.set(a[0], y + height - 1, a[1], stair('oldrepublic:watch_stone_stairs', first, 'top'))
            t.set(b[0], y + height - 1, b[1], stair('oldrepublic:watch_stone_stairs', last, 'top'))
            for (cx, cz) in cells[1:-1]:
                t.set(cx, y + height - 1, cz, slab('oldrepublic:watch_stone_slab', 'top'))
        for (cx, cz) in cells:
            t.set(cx, y + height, cz, 'minecraft:chiseled_stone_bricks' if (cx, cz) in (cells[0], cells[-1])
                  else WATCH['trim'])

    # ------------------------------------------------------------ roofs

    def gable(self, rect, y, ridge='x', overhang=1, keep=None, gable_ends=True, rafters=True, tiles=None,
              ridge_slab=None, end_wall=None):
        """A steep gable of deepslate tiles over rect (wall lines) with eaves at y; gable ends in the wall stone.
        keep(x, y, z) False is a hole: the tiles there are gone and the rafters below show. tiles(x, y, z) and
        ridge_slab replace the slate (a stair block name, a slab block name); end_wall(x, y, z) the end walls'."""
        t = self.t
        x0, z0, x1, z1 = rect
        if ridge == 'x':
            a0, a1 = z0 - overhang, z1 + overhang
            b0, b1 = x0 - overhang, x1 + overhang
        else:
            a0, a1 = x0 - overhang, x1 + overhang
            b0, b1 = z0 - overhang, z1 + overhang
        w = a1 - a0 + 1
        rows = (w + 1) // 2
        for i in range(rows):
            yy = y + i
            front, back = a0 + i, a1 - i
            for b in range(b0, b1 + 1):
                end = b in (b0, b1) and overhang
                for (a, face) in ((front, 'f'), (back, 'b')):
                    if front > back or (face == 'b' and front == back):
                        continue
                    x, z = (b, a) if ridge == 'x' else (a, b)
                    if keep and not keep(x, yy, z):
                        if rafters and not end and (b - b0) % 3 == 1 and a not in (a0, a1):
                            axis = 'z' if ridge == 'x' else 'x'
                            t.set(x, yy, z, 'minecraft:stripped_dark_oak_log[axis=%s]' % axis)
                        continue
                    if front == back:
                        t.set(x, yy, z, slab(ridge_slab or ROOF['ridge']))
                        continue
                    if ridge == 'x':
                        facing = 'south' if face == 'f' else 'north'
                    else:
                        facing = 'east' if face == 'f' else 'west'
                    # moss grows on the north slope and the low courses
                    shade = (0.06 if facing == 'south' else 0.0) + 0.06 * (1.0 - i / max(1, rows - 1))
                    t.set(x, yy, z, stair(tiles(x, yy, z) if tiles else self.roof_tile(x, yy, z, shade), facing))
                # under the slopes inside the walls: the roof's body, or the gable end walls
                inner_a = range(front + 1, back)
                if gable_ends and b in (b0 + overhang, b1 - overhang):
                    for a in inner_a:
                        x, z = (b, a) if ridge == 'x' else (a, b)
                        if keep and not keep(x, yy, z):
                            continue
                        t.set(x, yy, z, end_wall(x, yy, z) if end_wall else self.wall(x, yy, z))
            if front >= back:
                break
        # the ridge's cap where the width is even: a slab course along the last pair
        return y + rows - 1

    def lean_to(self, cells, y_low, low_side, mat=None):
        """A lean-to roof over cells {(x, z): steps from the low edge}: stairs rising away from `low_side`."""
        t = self.t
        up = B.opposite(low_side)
        for (x, z), k in sorted(cells.items()):
            s = mat or self.roof_tile(x, y_low + k, z, 0.06 if k == 0 else 0.0)
            t.set(x, y_low + k, z, stair(s, up))

    # ------------------------------------------------------------ ruin

    def rubble(self, cells, y_of, density=0.6, seed=0, piles=True):
        """Rubble on the floor cells {(x, z)}: loose slabs and stairs, and blocks in the thick of a pile."""
        t = self.t
        n = Noise(self.seed + seed + 31, 2.0)
        for (x, z) in sorted(cells):
            y = y_of(x, z)
            if y is None:
                continue
            v = n.at(x, z)
            if t.get(x, y, z) not in (None, AIR):
                continue
            if self.rng.random() > density * (0.4 + v):
                continue
            if piles and v > 0.62:
                t.set(x, y, z, pickw(RUBBLE, self.rng.random()))
                if v > 0.72 and t.get(x, y + 1, z) in (None, AIR):
                    t.set(x, y + 1, z, pickw(RUBBLE_LOOSE, self.rng.random()))
            else:
                s = pickw(RUBBLE_LOOSE, self.rng.random())
                if 'facing=' in s:
                    s = B.with_props(s, facing=DIRS[self.rng.randrange(4)])
                t.set(x, y, z, s)

    def break_top(self, cells, y_lo, y_hi, seed=0, depth=3.0):
        """Breaks the tops of walls in cells {(x, z)} unevenly: a noise height between y_lo and y_hi."""
        t = self.t
        n = Noise(self.seed + seed + 41, 3.0)
        for (x, z) in sorted(cells):
            cut = y_lo + int(round((y_hi - y_lo) * n.at(x, z)))
            for y in range(cut, y_hi + int(depth) + 12):
                if t.get(x, y, z) is not None:
                    t.set(x, y, z, None)


GROUND_NAMES = {'minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block', 'minecraft:coarse_dirt',
                'minecraft:dirt', 'minecraft:gravel', 'minecraft:dirt_path', 'minecraft:rooted_dirt'}


def along(side):
    """The direction along a wall that faces `side`."""
    return (1, 0) if side in ('north', 'south') else (0, 1)


def pane_props(side):
    if side in ('north', 'south'):
        return dict(east=True, west=True, north=False, south=False, waterlogged=False)
    return dict(north=True, south=True, east=False, west=False, waterlogged=False)


def side_cells(rect, side):
    x0, z0, x1, z1 = rect
    if side == 'north':
        return [(x, z0) for x in range(x0, x1 + 1)]
    if side == 'south':
        return [(x, z1) for x in range(x0, x1 + 1)]
    if side == 'west':
        return [(x0, z) for z in range(z0, z1 + 1)]
    return [(x1, z) for z in range(z0, z1 + 1)]


def perimeter(rect, sides=DIRS, thick=1):
    x0, z0, x1, z1 = rect
    out = []
    seen = set()
    for k in range(thick):
        r = (x0 + k, z0 + k, x1 - k, z1 - k)
        for side in sides:
            for c in side_cells(r, side):
                if c not in seen:
                    seen.add(c)
                    out.append(c)
    return out


def inside(rect, margin=1):
    x0, z0, x1, z1 = rect
    return [(x, z) for x in range(x0 + margin, x1 - margin + 1) for z in range(z0 + margin, z1 - margin + 1)]


# ---------------------------------------------------------------- furnishings

def bed(t, x, y, z, facing, color='green'):
    """A bed whose head is at (x, y, z), its foot one cell away against `facing`."""
    dx, dz = STEP[facing]
    t.set(x, y, z, S('minecraft:%s_bed' % color, facing=facing, part='head', occupied=False))
    t.set(x - dx, y, z - dz, S('minecraft:%s_bed' % color, facing=facing, part='foot', occupied=False))


def table(t, cells, y, top='minecraft:spruce_slab', leg='minecraft:spruce_fence'):
    """A table: a top of upper slabs on fence legs at the ends."""
    cells = sorted(cells)
    for (x, z) in cells:
        t.set(x, y, z, S(top, type='top', waterlogged=False))
    for (x, z) in (cells[0], cells[-1]):
        pass
    return cells


def candle(t, x, y, z, n=1, color=None, lit=True):
    name = 'minecraft:%s_candle' % color if color else 'minecraft:candle'
    t.set(x, y, z, S(name, candles=n, lit=lit, waterlogged=False))


def lantern(t, x, y, z, hanging=False, soul=False):
    t.set(x, y, z, S('minecraft:soul_lantern' if soul else 'minecraft:lantern', hanging=hanging, waterlogged=False))


def hang_lantern(t, x, y, z, length, soul=False):
    """A lantern on a chain hanging `length` cells below the ceiling cell above (x, y + length, z)."""
    for k in range(1, length):
        t.set(x, y + k, z, 'minecraft:iron_chain[axis=y,waterlogged=false]')
    lantern(t, x, y, z, hanging=True, soul=soul)
