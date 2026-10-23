#!/usr/bin/env python3
"""Torvin Kaast's hunting camp (docs/MILESTONE_6.md 5.8; MILESTONE_4.md 10.5): boss/hunting_camp,
57 x 16 x 57, buried 4 (ground y 3, feet y 4), built new around the old camp's fight layout. Every anchor
keeps its place relative to the pit's centre (the old 41-wide template's anchors moved 8 east and 8
south), so the ring (radius 20 round the HEART), the nests, the mine spots and the gate fight as before.

  the pit       15 across and 3 deep in three rough terraces, shored with planks and scrap, a ramp of
                steps on each side; the HEART at its foot, stakes and tethers at its rim
  the ring wall salvage: rusted and scorched hull plating, crates and conduit on durasteel posts, 4 high
                with a broken top, firing slits, lightning-rod spikes and trophies; the gate in the south
                wall (GATE x 27..29, y 4..6, z 46, the camp_gate blocks open) under the horned skull of
                something enormous
  four nests    scaffold towers of durasteel at N, E, SSW and W (NEST_1..4, feet at y 10): a decked
                platform, a plating breastwork on the outer side, a mounted gun, the ladder on the pit side
  the arena     trampled earth; cover (crate stacks, barricades, a fallen hull section, barrels) clear of
                the six mine spots (MINE_1..6), the towers and the walk from the gate to the pit; braziers
  outside       Kaast's own camp, orderly: his tent, the forge where he works his iron, frames of drying
                hides, his speeder and supplies, a beast cage, and the ribcage of the biggest thing he ever
                killed; trophy poles along the way to the gate

Usage: python3 tools/assets/structures/d/hunting_camp.py
"""
import math

from common import (S, AIR, B, Noise, DTemplate, mixer, hash01, save, write_meta, check_reach, stairs, slab, fence,
                    bars, chain, lantern, skull, campfire, log, ladder, mod, SIDES, is_air_set)
import props as P
import tomb_kit as K

TID = 'boss/hunting_camp'
SIZE, SY = 57, 16
C = 28                                  # the pit's centre column, x and z
GROUND, FEET = 3, 4
HEART = (28, 1, 28)
NESTS = [(28, 10, 14), (42, 10, 28), (21, 10, 40), (14, 10, 28)]                 # NEST_1..4
MINES = [(38, 4, 34), (28, 4, 40), (18, 4, 34), (18, 4, 22), (28, 4, 16), (38, 4, 22)]   # MINE_1..6
GATE = [(x, y, 46) for x in (27, 28, 29) for y in (4, 5, 6)]                     # the old order
WALL_IN, WALL_OUT = 17.9, 19.6          # the ring wall's radii
DISC = 26.5                             # the trampled ground the camp sets (beyond it the world's own)

ROD = S('lightning_rod', facing='up', powered=False, waterlogged=False)


def r_of(x, z):
    return math.hypot(x - C, z - C)


def angle_of(x, z):
    return math.degrees(math.atan2(z - C, x - C)) % 360


def polar(angle, r):
    a = math.radians(angle)
    return int(round(C + math.cos(a) * r)), int(round(C + math.sin(a) * r))


def beam(axis='y'):
    return S('oldrepublic:durasteel_beam', axis=axis)


def build():
    t = DTemplate(SIZE, SY, SIZE, seed=960101)
    t.name = TID
    fray = Noise(960102, 2.8)
    pitn = Noise(960103, 2.2)
    earth = mixer(960104, [('coarse_dirt', 5), ('packed_mud', 3), ('dirt', 2), ('gravel', 2), ('cobblestone', 1)], 2.6)
    outer = mixer(960105, [('coarse_dirt', 4), ('dirt', 3), ('packed_mud', 1), ('gravel', 1)], 3.2)
    sub = mixer(960106, [('dirt', 4), ('stone', 3), ('cobblestone', 1), ('andesite', 1)], 2.4)
    scrap = mixer(960107, [('oldrepublic:rusted_plating', 5), ('oldrepublic:wreck_plating', 3),
                           ('oldrepublic:wreck_plating_scorched', 2), ('oldrepublic:durasteel_plating', 1),
                           ('oldrepublic:wreck_salvage', 1)], 1.8)
    pit_floor = mixer(960108, [('packed_mud', 4), ('mud', 2), ('coarse_dirt', 3), ('rooted_dirt', 1), ('gravel', 1)], 1.8)

    # ---------------------------------------------------------------- ground and pit
    inside = set()
    for x in range(SIZE):
        for z in range(SIZE):
            r = r_of(x, z)
            if r > DISC + 2.0 or (r > DISC and fray.at(x, 0, z) < (r - DISC) / 2.0):
                continue
            inside.add((x, z))
            for y in range(0, GROUND):
                t.set(x, y, z, sub(x, y, z))
            t.set(x, GROUND, z, earth(x, GROUND, z) if r < WALL_OUT else outer(x, GROUND, z))
            for y in range(FEET, SY):
                t.set(x, y, z, AIR)

    def pit_level(x, z):
        """The pit's floor top at a column (None: the ground)."""
        r = r_of(x, z) + (pitn.at(x, 0, z) - 0.5) * 0.9
        if r <= 5.6:
            return 0
        if r <= 6.6:
            return 1
        if r <= 7.6:
            return 2
        return None
    for (x, z) in list(inside):
        lv = pit_level(x, z)
        if lv is None:
            continue
        for y in range(lv + 1, FEET):
            t.set(x, y, z, AIR)
        t.set(x, lv, z, pit_floor(x, lv, z))
    # the rough riser of each terrace shored with planks, logs and scrap
    shore = mixer(960109, [('spruce_planks', 4), (log('spruce_log', 'y'), 2), ('oldrepublic:rusted_plating', 1),
                           ('cobblestone', 2), ('packed_mud', 2)], 1.5)
    for (x, z) in sorted(inside):
        lv = pit_level(x, z)
        top = GROUND if lv is None else lv
        for _, dx, dz in SIDES:
            q = (x + dx, z + dz)
            if q not in inside:
                continue
            lq = pit_level(*q)
            ltop = GROUND if lq is None else lq
            if ltop < top:
                t.set(x, top, z, shore(x, top, z))
                break

    # four ramps of steps down into the pit, one from each side; the south one meets the walk from the gate
    for (dx, dz, facing) in ((0, 1, 'south'), (0, -1, 'north'), (1, 0, 'east'), (-1, 0, 'west')):
        for across in (-1, 0, 1):
            for k, (r, y) in enumerate(((7, 3), (6, 2), (5, 1))):
                x = C + dx * r + (across if dz else 0)
                z = C + dz * r + (across if dx else 0)
                for yy in range(y, FEET + 1):
                    t.set(x, yy, z, AIR)
                t.set(x, y, z, stairs('spruce_stairs' if across == 0 else 'cobblestone_stairs', facing))
                t.set(x, y - 1, z, 'packed_mud' if y > 1 else pit_floor(x, y - 1, z))
            # the ground at the ramp's head
            for across in (-1, 0, 1):
                hx = C + dx * 8 + (across if dz else 0)
                hz = C + dz * 8 + (across if dx else 0)
                t.set(hx, GROUND, hz, 'packed_mud')
                for yy in range(FEET, FEET + 3):
                    t.set(hx, yy, hz, AIR)

    # ---------------------------------------------------------------- the ring wall
    top_noise = Noise(960110, 2.0)
    posts = set()
    for a in range(0, 360, 14):
        posts.add(polar(a + 7, (WALL_IN + WALL_OUT) / 2))
    gate_xs = (26, 27, 28, 29, 30)
    wall_cells = []
    for (x, z) in sorted(inside):
        r = r_of(x, z)
        if not (WALL_IN <= r <= WALL_OUT):
            continue
        if z >= 44 and 26 <= x <= 30:
            continue                                     # the gate's own frame, below
        wall_cells.append((x, z))
        h = 4 + int(top_noise.at(x, 0, z) * 2.4)
        if hash01(960111, x, z) < 0.08:
            h -= 1                                       # a dent where something hit it
        t.set(x, GROUND, z, 'cobblestone')
        for y in range(FEET, FEET + h):
            t.set(x, y, z, scrap(x, y, z))
        # a band of conduit, firing slits, spikes and the odd crate in the wall
        if hash01(960112, x, z) < 0.35 and h >= 4:
            t.set(x, FEET + 2, z, 'oldrepublic:wreck_conduit')
        if (x, z) in posts:
            for y in range(FEET, FEET + h + 2):
                t.set(x, y, z, beam('y'))
            t.set(x, FEET + h + 2, z, skull(int(hash01(960113, x, z) * 16)) if hash01(960114, x, z) < 0.3 else
                  lantern() if hash01(960115, x, z) < 0.6 else ROD)
        elif hash01(960116, x, z) < 0.18:
            t.set(x, FEET + h, z, ROD)
    for (x, z) in wall_cells:
        r = r_of(x, z)
        if r < (WALL_IN + WALL_OUT) / 2 and hash01(960117, x, z) < 0.12 and (x, z) not in posts:
            # a slit: bars through both rows at eye height
            ox, oz = x + (1 if x > C else -1 if x < C else 0), z + (1 if z > C else -1 if z < C else 0)
            for q in ((x, z), (ox, oz)):
                if q in wall_cells and q not in posts:
                    t.set(q[0], FEET + 1, q[1], bars())
    for (x, z) in wall_cells:
        if hash01(960118, x, z) < 0.05 and r_of(x, z) < WALL_IN + 0.8:
            for y in range(FEET, FEET + 2):
                t.set(x, y, z, 'oldrepublic:cargo_crate' if hash01(960119, x, y) < 0.5 else 'oldrepublic:cargo_crate_imperial')

    # The gate: a durasteel frame, the scrap door's cells (open) and over it the skull.
    for x in gate_xs:
        for z in (46, 47):
            for y in range(FEET, FEET + 5):
                t.set(x, y, z, scrap(x, y, z))
            t.set(x, GROUND, z, 'cobblestone')
    for x in (26, 30):
        for z in (46, 47):
            for y in range(FEET, FEET + 6):
                t.set(x, y, z, beam('y'))
    for x in range(26, 31):
        for z in (46, 47):
            t.set(x, FEET + 3, z, 'oldrepublic:durasteel_plating')
            t.set(x, FEET + 4, z, beam('x'))
    for x in (27, 28, 29):
        for y in (FEET, FEET + 1, FEET + 2):
            t.set(x, y, 46, S('oldrepublic:camp_gate', open=True, top=(y == FEET + 2)))
            t.set(x, y, 47, AIR)
            t.set(x, y, 45, AIR)
            t.set(x, y, 48, AIR)
        t.set(x, GROUND, 46, 'polished_andesite')
        t.set(x, GROUND, 47, 'polished_andesite')
    skull_trophy(t, 28, FEET + 5, 47)

    # ---------------------------------------------------------------- the nests
    for (nx, ny, nz) in NESTS:
        nest_tower(t, nx, ny, nz)

    # ---------------------------------------------------------------- the arena: tracks, cover, braziers
    for z in range(36, 46):
        for x in (27, 28, 29):
            h = hash01(960120, x, z)
            if t.is_air(x, FEET, z) and (x, z) in inside:
                t.set(x, GROUND, z, 'dirt_path' if x == 28 or h < 0.4 else 'coarse_dirt')
    cover(t)
    for a in (45, 135, 225, 315):
        x, z = polar(a, 16.6)
        brazier(t, x, z)

    # ---------------------------------------------------------------- outside: Kaast's camp
    kaast_tent(t, 44, 4)
    forge(t, 51, 26)
    hides(t, 3, 22)
    ribcage(t, 0, 1)
    speeder(t, 44, 49)
    cage(t, 8, 45)
    for (x, z) in ((25, 51), (31, 51), (25, 55), (31, 55)):
        trophy_pole(t, x, z)
    for (x, z) in ((24, 48), (32, 48)):
        brazier(t, x, z)
    for z in range(48, SIZE):
        for x in (27, 28, 29):
            if (x, z) not in inside:
                inside.add((x, z))
                for y in range(0, GROUND):
                    t.set(x, y, z, sub(x, y, z))
                for y in range(FEET, SY):
                    t.set(x, y, z, AIR)
            t.set(x, GROUND, z, 'dirt_path' if x == 28 or hash01(960121, x, z) < 0.5 else 'coarse_dirt')
    scatter(t, inside)

    # ---------------------------------------------------------------- anchors, walks, meta
    mod.spot(t, 'HEART', *HEART)
    for i, p in enumerate(NESTS):
        mod.spot(t, 'NEST_%d' % (i + 1), *p)
    for i, p in enumerate(MINES):
        mod.spot(t, 'MINE_%d' % (i + 1), *p)
    for p in GATE:
        t.anchor('GATE', *p)
    gate_open = set(GATE)
    targets = [(28, FEET, 48), (28, FEET, 44), HEART] + MINES + NESTS
    check_reach(t, [(28, FEET, SIZE - 1)], targets, gate_open, TID)
    # round the outside of the wall: the walk the playthrough takes from wherever it arrives to the gate
    check_reach(t, [(28, FEET, 2)], [(28, FEET, 48), (49, FEET, 28), (7, FEET, 28)], gate_open, TID + ' (round the wall)')
    write_meta(TID, buried=4, max_slope=4, clearance=4, support_depth=4, ramp_rings=2, clear_above=6, terrain='flat')
    return save(t, TID, 'hunting_camp', views=('se', 'nw'), plans=[1, GROUND, FEET, 10])


def skull_trophy(t, cx, y0, z0):
    """The skull over the gate: a long cranium of bone looking south down the way in, fangs hanging from
    its jaw, and horns that sweep out and up over the wall."""
    bone = S('bone_block', axis='y')
    for x in (cx - 1, cx, cx + 1):
        for y in (y0, y0 + 1):
            for z in (z0, z0 + 1):
                t.set(x, y, z, bone)
    t.set(cx, y0 + 2, z0, bone)
    t.set(cx, y0, z0 + 2, S('bone_block', axis='z'))
    t.set(cx, y0 + 1, z0 + 2, S('bone_block', axis='z'))
    t.set(cx, y0, z0 + 3, S('bone_block', axis='z'))
    for x in (cx - 1, cx + 1):
        t.set(x, y0 + 1, z0 + 1, 'blackstone')             # the eyes
    for x in (cx - 1, cx + 1):
        t.set(x, y0 - 1, z0 + 1, S('pointed_dripstone', thickness='tip', vertical_direction='down', waterlogged=False))
    t.set(cx, y0 - 1, z0 + 3, S('pointed_dripstone', thickness='tip', vertical_direction='down', waterlogged=False))
    for side in (-1, 1):
        path = [(cx + side * 2, y0 + 1, z0, 'x'), (cx + side * 3, y0 + 2, z0, 'x'), (cx + side * 4, y0 + 3, z0, 'y'),
                (cx + side * 4, y0 + 4, z0, 'y'), (cx + side * 5, y0 + 5, z0, 'x')]
        for (x, y, z, axis) in path:
            t.set(x, y, z, S('bone_block', axis=axis))
        t.set(cx + side * 5, y0 + 6, z0, S('pointed_dripstone', thickness='tip', vertical_direction='up', waterlogged=False))


def nest_tower(t, nx, ny, nz):
    """A gun nest: durasteel legs and a central post, a braced frame, a deck at ny - 1 with the ladder hole
    on the pit side, a plating breastwork on the outer side and a gun at a pit-side corner."""
    dx, dz = C - nx, C - nz
    n = max(abs(dx), abs(dz))
    ix, iz = (int(round(dx / n)), 0) if abs(dx) >= abs(dz) else (0, int(round(dz / n)))   # inward
    deck = ny - 1
    facing = {(0, 1): 'south', (0, -1): 'north', (1, 0): 'east', (-1, 0): 'west'}[(ix, iz)]
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            x, z = nx + i, nz + j
            t.set(x, GROUND, z, 'cobblestone')
            for y in range(FEET, deck):
                t.set(x, y, z, AIR)
            if i and j:
                for y in range(FEET, deck):
                    t.set(x, y, z, beam('y'))
            t.set(x, deck, z, 'oldrepublic:durasteel_grate' if (i and j) else 'oldrepublic:durasteel_deck')
            for y in range(ny, ny + 4):
                t.set(x, y, z, AIR)
    for y in range(FEET, deck):
        t.set(nx, y, nz, beam('y'))
    # braces at mid height on the three sides without the ladder
    for (bx, bz, axis) in ((nx - ix, nz - iz, 'x' if iz else 'z'), (nx + iz, nz + ix, 'x' if ix else 'z'),
                           (nx - iz, nz - ix, 'x' if ix else 'z')):
        t.set(bx, FEET + 2, bz, beam(axis))
    # the ladder up the pit side, hung on the central post
    lx, lz = nx + ix, nz + iz
    for y in range(FEET, deck + 1):
        t.set(lx, y, lz, ladder(facing))
    # the breastwork: plating on the outer row, higher at its corners; slabs at the sides
    for k in (-1, 0, 1):
        ox, oz = nx - ix + (k if iz else 0), nz - iz + (k if ix else 0)
        t.set(ox, ny, oz, 'oldrepublic:rusted_plating')
        if k:
            t.set(ox, ny + 1, oz, 'oldrepublic:wreck_plating_scorched')
    for s in (-1, 1):
        sx, sz = nx + (s if iz else 0), nz + (s if ix else 0)
        t.set(sx, ny, sz, slab('oldrepublic:durasteel_plating_slab'))
    # the gun at one pit-side corner, its barrel to the pit; a lantern under the deck
    gx, gz = nx + ix + (1 if iz else 0), nz + iz + (1 if ix else 0)
    t.set(gx, ny, gz, S('oldrepublic:durasteel_pipes', axis='x' if ix else 'z'))
    t.set(nx - ix, deck - 1, nz - iz, lantern(hanging=True))


def brazier(t, x, z):
    t.set(x, GROUND, z, 'cobblestone')
    t.set(x, FEET, z, campfire(lit=True))
    for _, dx, dz in SIDES:
        if hash01(960130, x + dx, z + dz) < 0.5 and t.is_air(x + dx, FEET, z + dz):
            t.set(x + dx, FEET, z + dz, S('cobblestone_slab', type='bottom', waterlogged=False))


def free_ground(t, x, z, h=2):
    return all(t.is_air(x, y, z) and (x, y, z) in t.cells for y in range(FEET, FEET + h)) and K.solid(t, (x, GROUND, z))


def clear_of_fight(x, z, room=2.6):
    if r_of(x, z) < 8.6 or r_of(x, z) > 17.0:
        return False
    if any(math.hypot(x - m[0], z - m[2]) < room for m in MINES):
        return False
    if any(max(abs(x - n[0]), abs(z - n[2])) <= 2 for n in NESTS):
        return False
    if 26 <= x <= 30 and z >= 35:
        return False                                      # the walk from the gate to the pit
    return True


def cover(t):
    """Cover in the arena: crate stacks, barricades of plating, a fallen hull section and barrels."""
    def crate(x, z, h):
        for y in range(FEET, FEET + h):
            t.set(x, y, z, ('oldrepublic:cargo_crate', 'oldrepublic:cargo_crate_imperial',
                            'oldrepublic:cargo_crate_republic')[int(hash01(960140, x, y, z) * 3) % 3])
    pieces = [(60, 10.5, 'crates'), (60, 15.8, 'barricade'), (120, 9.6, 'hull'), (172, 9.8, 'barrels'),
              (240, 10.4, 'crates'), (240, 15.8, 'barricade'), (300, 10.4, 'hull'), (300, 15.8, 'crates'),
              (8, 9.8, 'barricade'), (196, 16.0, 'crates'), (150, 16.2, 'barrels'), (330, 16.2, 'barrels')]
    for (a, r, kind) in pieces:
        x, z = polar(a, r)
        tx, tz = -math.sin(math.radians(a)), math.cos(math.radians(a))         # along the ring
        cells = [(int(round(x + tx * k)), int(round(z + tz * k))) for k in (-1, 0, 1)]
        if not all(clear_of_fight(*c) and free_ground(t, *c) for c in cells):
            cells = [c for c in cells if clear_of_fight(*c) and free_ground(t, *c)]
        if not cells:
            continue
        if kind == 'crates':
            for i, (cx, cz) in enumerate(cells[:2]):
                crate(cx, cz, 2 if i == 0 else 1)
        elif kind == 'barricade':
            for (cx, cz) in cells:
                t.set(cx, FEET, cz, 'oldrepublic:rusted_plating')
                t.set(cx, FEET + 1, cz, 'oldrepublic:wreck_plating' if hash01(960141, cx, cz) < 0.6 else
                      slab('oldrepublic:durasteel_plating_slab'))
        elif kind == 'hull':
            # a curved hull section lying on its edge: a low slope of plating stairs against a full row
            ox, oz = int(round(math.cos(math.radians(a)))), int(round(math.sin(math.radians(a))))
            face = {(1, 0): 'west', (-1, 0): 'east', (0, 1): 'north', (0, -1): 'south'}.get((ox, oz), 'north')
            for (cx, cz) in cells:
                t.set(cx, FEET, cz, 'oldrepublic:wreck_plating_scorched')
                t.set(cx, FEET + 1, cz, stairs('oldrepublic:durasteel_plating_stairs', face))
                q = (cx - ox, cz - oz)
                if clear_of_fight(*q) and free_ground(t, *q, h=1):
                    t.set(q[0], FEET, q[1], stairs('oldrepublic:durasteel_plating_stairs', face))
        elif kind == 'barrels':
            for (cx, cz) in cells[:2]:
                t.set(cx, FEET, cz, S('barrel', facing='up', open=False))
            t.set(cells[0][0], FEET + 1, cells[0][1], S('barrel', facing='up', open=False))


def kaast_tent(t, x0, z0):
    """Kaast's tent in the north-east: canvas trimmed in navy, a bedroll, a footlocker, a lantern; a camp
    table before it with his helmet's polish and a chair."""
    ground_pad(t, x0 - 1, z0 - 1, x0 + 6, z0 + 9)
    P.tent(t, x0, z0, 5, 7, FEET, ridge='z', canvas='oldrepublic:canvas_navy', side='oldrepublic:canvas_stairs',
           cap='oldrepublic:canvas_slab', trim=None, door='south', seed=960150)
    cx = x0 + 2
    t.set(cx - 1, FEET, z0 + 2, 'gray_carpet')
    t.set(cx - 1, FEET, z0 + 3, 'gray_carpet')
    t.set(cx + 1, FEET, z0 + 1, S('barrel', facing='up', open=False))
    t.set(cx + 1, FEET, z0 + 2, 'oldrepublic:cargo_crate')
    t.set(cx + 1, FEET + 1, z0 + 2, lantern())
    # the table outside the door
    tz = z0 + 8
    t.set(cx, FEET, tz, fence('spruce_fence'))
    t.set(cx, FEET + 1, tz, slab('spruce_slab'))
    t.set(cx + 1, FEET, tz, fence('spruce_fence'))
    t.set(cx + 1, FEET + 1, tz, slab('spruce_slab'))
    t.set(cx + 1, FEET + 2, tz, lantern())
    t.set(cx - 1, FEET, tz, stairs('spruce_stairs', 'west'))
    t.set(cx, FEET + 2, tz, S('grindstone', face='floor', facing='north'))


def forge(t, x0, z0):
    """The forge east of the wall: a lean-to of spruce and plating over a blast furnace, an anvil, a
    grindstone, a smithing table and a quench tub; a stack of iron plate."""
    ground_pad(t, x0 - 1, z0 - 2, x0 + 5, z0 + 5)
    for (x, z) in ((x0, z0 - 1), (x0 + 4, z0 - 1), (x0, z0 + 4), (x0 + 4, z0 + 4)):
        for y in range(FEET, FEET + 3):
            t.set(x, y, z, log('spruce_log', 'y'))
    for x in range(x0, x0 + 5):
        for z in range(z0 - 1, z0 + 5):
            t.set(x, FEET + 3, z, slab('oldrepublic:durasteel_plating_slab') if x < x0 + 4 else 'oldrepublic:rusted_plating')
    t.set(x0 + 3, FEET, z0, S('blast_furnace', facing='west', lit=True))
    for y in range(FEET + 1, FEET + 5):
        t.set(x0 + 3, y, z0, 'cobblestone' if y < FEET + 4 else S('cobblestone_wall', north='none', east='none',
                                                                     south='none', west='none', up=True,
                                                                     waterlogged=False))
    t.set(x0 + 1, FEET, z0 + 1, S('anvil', facing='north'))
    t.set(x0 + 3, FEET, z0 + 2, 'smithing_table')
    t.set(x0 + 3, FEET, z0 + 3, S('water_cauldron', level=3))
    t.set(x0 + 1, FEET, z0 + 3, S('grindstone', face='floor', facing='east'))
    t.set(x0 + 2, FEET, z0 - 1, 'iron_block')
    t.set(x0 + 3, FEET, z0 - 1, 'coal_block')
    t.set(x0 + 2, FEET + 2, z0 + 1, lantern(hanging=True))


def hides(t, x0, z0):
    """Frames of drying hides west of the wall, and a row of skulls on stakes."""
    ground_pad(t, x0 - 1, z0 - 1, x0 + 3, z0 + 13)
    for k, z in enumerate((z0, z0 + 5, z0 + 10)):
        for y in range(FEET, FEET + 3):
            t.set(x0, y, z, fence('spruce_fence'))
            t.set(x0, y, z + 3, fence('spruce_fence'))
        for zz in range(z, z + 4):
            t.set(x0, FEET + 3, zz, log('spruce_log', 'z'))
        hide = ('brown_wool', 'white_wool', 'black_wool')[k]
        for zz in (z + 1, z + 2):
            for y in (FEET + 1, FEET + 2):
                t.set(x0, y, zz, hide if hash01(960160, zz, y) < 0.85 else AIR)
    for z in range(z0 + 1, z0 + 13, 4):
        t.set(x0 + 2, FEET, z, fence('spruce_fence'))
        t.set(x0 + 2, FEET + 1, z, skull(4))


def ribcage(t, x0, z0):
    """The bones of the biggest thing Kaast ever killed, north-west of the wall: a spine along the ground,
    ribs arching over it (two of them broken), the skull at its head with its horns swept back."""
    sz = z0 + 4
    ground_pad(t, x0, z0 - 1, x0 + 17, z0 + 9)
    for x in range(x0 + 1, x0 + 13):
        t.set(x, FEET, sz, S('bone_block', axis='x'))
    profiles = {4: [(4, 0), (4, 1), (4, 2), (3, 3), (2, 4), (1, 4), (0, 4)],
                3: [(3, 0), (3, 1), (2, 2), (1, 3), (0, 3)],
                2: [(2, 0), (2, 1), (1, 2), (0, 2)]}
    for k, (x, w) in enumerate(zip(range(x0 + 3, x0 + 12, 2), (3, 4, 4, 3, 2))):
        for side in (-1, 1):
            broken = (k, side) in ((1, 1), (3, -1))
            for (dz, dy) in profiles[w]:
                if broken and dy >= 2:
                    continue
                if dz == 0 and side == 1:
                    continue
                t.set(x, FEET + dy, sz + side * dz, S('bone_block', axis='y' if dz == w else 'z'))
            if broken:
                # the rest of the rib lies where it fell
                t.set(x + 1, FEET, sz + side * (w + 1), S('bone_block', axis='z'))
    hx = x0 + 13
    for x in (hx, hx + 1):
        for z in (sz - 1, sz, sz + 1):
            t.set(x, FEET, z, S('bone_block', axis='x'))
            t.set(x, FEET + 1, z, S('bone_block', axis='x'))
    t.set(hx + 2, FEET, sz, S('bone_block', axis='x'))
    for z in (sz - 1, sz + 1):
        t.set(hx + 1, FEET + 1, z, 'blackstone')                 # the eyes
        t.set(hx + 2, FEET, z, S('pointed_dripstone', thickness='tip', vertical_direction='up', waterlogged=False))
    for side in (-1, 1):
        for (dx, dy, dz) in ((0, 2, 1), (-1, 3, 2), (-2, 3, 2), (-3, 4, 3)):
            t.set(hx + dx, FEET + dy, sz + side * dz, S('bone_block', axis='x' if dx < -1 else 'y'))
    for (x, z, st) in ((x0 + 6, sz + 6, skull(3)), (x0 + 15, sz - 4, S('bone_block', axis='z'))):
        if t.inside(x, FEET, z) and t.is_air(x, FEET, z) and (x, FEET, z) in t.cells:
            t.set(x, FEET, z, st)


def speeder(t, x0, z0):
    """Kaast's speeder south-east of the wall, beside fuel drums and his supply crates."""
    ground_pad(t, x0 - 1, z0 - 1, x0 + 8, z0 + 5)
    for x in range(x0, x0 + 4):
        t.set(x, FEET, z0, slab('oldrepublic:durasteel_plating_slab', 'top') if x in (x0, x0 + 3) else
              'oldrepublic:durasteel_plating')
    t.set(x0 + 1, FEET + 1, z0, stairs('oldrepublic:imperial_plating_stairs', 'west'))
    t.set(x0 + 2, FEET + 1, z0, stairs('oldrepublic:durasteel_plating_stairs', 'east'))
    t.set(x0 - 1, FEET, z0, S('oldrepublic:durasteel_pipes', axis='x'))
    t.set(x0 + 4, FEET, z0, slab('oldrepublic:durasteel_plating_slab'))
    for (x, z) in ((x0 + 6, z0 + 1), (x0 + 7, z0 + 1), (x0 + 6, z0 + 2)):
        t.set(x, FEET, z, S('barrel', facing='up', open=False))
    for (x, z, h) in ((x0, z0 + 3, 2), (x0 + 1, z0 + 3, 1), (x0 + 3, z0 + 4, 1)):
        for y in range(FEET, FEET + h):
            t.set(x, y, z, 'oldrepublic:cargo_crate_imperial' if (x + y) % 2 else 'oldrepublic:cargo_crate')


def cage(t, x0, z0):
    """An empty beast cage south-west of the wall, its door torn off; bones inside."""
    ground_pad(t, x0 - 1, z0 - 1, x0 + 5, z0 + 5)
    for x in range(x0, x0 + 4):
        for z in range(z0, z0 + 4):
            edge = x in (x0, x0 + 3) or z in (z0, z0 + 3)
            for y in range(FEET, FEET + 3):
                if edge and not (z == z0 and x in (x0 + 1, x0 + 2) and y < FEET + 2):
                    t.set(x, y, z, bars())
            t.set(x, FEET + 3, z, slab('oldrepublic:durasteel_plating_slab'))
    t.set(x0 + 1, FEET, z0 + 2, S('bone_block', axis='x'))
    t.set(x0 + 2, FEET, z0 + 2, skull(8))
    t.set(x0 + 1, FEET, z0 - 1, bars())


def trophy_pole(t, x, z):
    for y in range(FEET, FEET + 3):
        t.set(x, y, z, fence('spruce_fence'))
    t.set(x, FEET + 3, z, skull(int(hash01(960170, x, z) * 16)))
    t.set(x, GROUND, z, 'coarse_dirt')


def ground_pad(t, x0, z0, x1, z1):
    """Make sure a built area stands on the camp's ground (the corners lie beyond the trampled disc)."""
    for x in range(max(0, x0), min(SIZE - 1, x1) + 1):
        for z in range(max(0, z0), min(SIZE - 1, z1) + 1):
            if (x, GROUND, z) not in t.cells:
                for y in range(0, GROUND):
                    t.set(x, y, z, 'dirt' if y else 'stone')
                t.set(x, GROUND, z, 'coarse_dirt' if hash01(960180, x, z) < 0.6 else 'dirt')
                for y in range(FEET, SY):
                    t.set(x, y, z, AIR)


def scatter(t, inside):
    """Bones, scorch marks and debris in the arena; grass and stones outside."""
    for (x, z) in sorted(inside):
        r = r_of(x, z)
        p = (x, FEET, z)
        if not (t.is_air(*p) and p in t.cells and K.solid(t, (x, GROUND, z))):
            continue
        if not t.is_air(x, FEET + 1, z):
            continue
        h = hash01(960190, x, z)
        if 8.4 < r < WALL_IN - 0.5:
            if any(math.hypot(x - m[0], z - m[2]) < 1.5 for m in MINES) or (26 <= x <= 30 and z >= 35):
                continue
            if h < 0.035:
                t.set(*p, 'oldrepublic:scorch_mark')
            elif h < 0.05:
                t.set(*p, 'oldrepublic:debris')
            elif h < 0.056:
                t.set(*p, S('bone_block', axis='x' if h < 0.053 else 'z'))
        elif r > WALL_OUT + 0.5:
            name = B.name_of(t.cells[(x, GROUND, z)])
            if name not in ('minecraft:dirt', 'minecraft:coarse_dirt'):
                continue
            if h < 0.06:
                t.set(*p, 'short_grass')
            elif h < 0.075:
                t.set(*p, 'dead_bush')
    for (x, z) in sorted(inside):
        lv = None
        r = r_of(x, z)
        if r <= 5.0 and (x, z) != (HEART[0], HEART[2]):
            y = 1
            p = (x, y, z)
            if t.is_air(*p) and p in t.cells and max(abs(x - HEART[0]), abs(z - HEART[2])) > 1:
                h = hash01(960191, x, z)
                if h < 0.05:
                    t.set(*p, S('bone_block', axis='x'))
                elif h < 0.08:
                    t.set(*p, skull(int(h * 300) % 16))
                elif h < 0.12:
                    t.set(*p, 'oldrepublic:scorch_mark')


def main(only=None):
    return [build()]


if __name__ == '__main__':
    import sys
    main(sys.argv[1:])
