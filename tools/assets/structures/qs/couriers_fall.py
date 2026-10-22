#!/usr/bin/env python3
"""story/couriers_fall (docs/MILESTONE_6.md 3.5 and 5.3): Orren Vasch's courier, cracked open on the
hillside. 53 x 14 x 31, buried 2 (layer 1 is the ground, layer 0 the keel and the gouge's floor),
terrain flat. A crash site you can read from west to east:

  * the first touch, a scorched scrape at the west edge; then the gouge, widening and deepening
    eastward, its lips heaped with turned earth;
  * the port wing, torn off at the second impact, lying across the gouge's north lip; the dorsal
    fin, snapped, thrown north;
  * cargo strewn along the way: crates burst open, barrels, a hatch;
  * the hull itself, rolled a little onto its starboard side, the cargo bay torn open to the north
    (toward whoever comes), the engines still smouldering (a small fire behind the port nozzle);
  * the nose driven into a heap of the earth it pushed up, the cockpit canopy cracked.

Kept: PEDESTAL_WAYFARER (the holocron pedestal, always ready, fixed to wayfarers_compass) in the bay;
STRONGBOX, Vasch's strongbox (chest, loot oldrepublic:gameplay/wayfarer_cache) beside it;
SCAVENGER_1 and SCAVENGER_2, the two poachers picking through it (feet anchors). Added: HEART, before
the torn bay. Four cells north of the pedestal stay open floor (the client tests stand there).

Usage: python3 tools/assets/structures/qs/couriers_fall.py [--preview <dir>]
"""
import math
import sys

from common import (AIR, LANTERN_HUNG, S, Noise, Template, barrel, by_noise, campfire, check, mod, pick, preview_dir,
                    slab, spot, stairs, write)

SIZE = (53, 14, 31)
SEED = 6101
G, F = 1, 2                 # the ground layer, the feet layer
ZC = 15                     # the gouge's and the hull's middle line
META = dict(buried=2, terrain='flat', max_slope=4, clearance=10, support_depth=8, ramp_rings=2, clear_above=6,
            spawn_radius=48)

PEDESTAL = (36, F, 14)
STRONGBOX = (38, F, 17)
ANCHORS = {'HEART': (35, F, 5), 'SCAVENGER_1': (21, F, 5), 'SCAVENGER_2': (44, F, 8)}

GRASS = S('grass_block', snowy=False)
SCORCH = [(S('coarse_dirt'), 5), (S('blackstone'), 1), (S('tuff'), 2), (S('rooted_dirt'), 2)]
EARTH = [(S('coarse_dirt'), 4), (S('dirt'), 3), (S('rooted_dirt'), 2), (S('gravel'), 1)]
HULL = [(S('or:republic_plating'), 6), (S('or:wreck_plating'), 3), (S('or:wreck_plating_scorched'), 1)]
BELLY = [(S('or:wreck_plating'), 3), (S('or:wreck_plating_scorched'), 3), (S('or:durasteel_plating'), 1)]
BARS = S('iron_bars', north=False, east=False, south=False, west=False, waterlogged=False)


def hw(x):
    """The gouge's half width at x (it widens toward the wreck)."""
    return 0.8 + 3.0 * max(0, min(x, 28)) / 28.0


# ---------------------------------------------------------------- the gouge

def gouge(t):
    n = Noise(SEED + 1, 2.0)
    m = Noise(SEED + 2, 3.0)
    for x in range(0, 30):
        w = hw(x)
        for z in range(0, SIZE[2]):
            d = abs(z - ZC + (0.4 if x < 10 else 0))
            if x <= 5:
                # the first touch: a scorched scrape, no depth
                if d <= w + 0.3 and (x >= 1 or d <= 0.5):
                    t.set(x, G, z, by_noise(n, SCORCH, x, G, z))
                    if n.at(x, 5, z) > 0.6:
                        t.set(x, F, z, S('or:scorch_mark'))
                continue
            if x <= 7 and d <= w:
                t.set(x, G, z, by_noise(n, SCORCH, x, G, z))
                t.set(x, F, z, AIR)
                continue
            if d <= w:
                t.set(x, 0, z, by_noise(n, SCORCH, x, 0, z))
                t.set(x, G, z, AIR)
                t.set(x, F, z, AIR)
                t.set(x, F + 1, z, AIR)
            elif d <= w + 1.7:
                # the lips: turned earth, two high nearer the wreck
                t.set(x, G, z, by_noise(m, EARTH, x, G, z))
                high = x > 13 and d <= w + 0.9
                t.set(x, F, z, by_noise(m, EARTH, x, F, z) if (d <= w + 1.2 or m.at(x, 3, z) > 0.5) else AIR)
                if high and m.at(x, 4, z) > 0.3:
                    t.set(x, F + 1, z, by_noise(m, EARTH + [(GRASS, 3)], x, F + 1, z))
                top = F + 1 if t.get(x, F + 1, z) not in (None, AIR) else F
                if t.get(x, top, z) not in (None, AIR) and t.rng.random() < 0.18:
                    t.set(x, top, z, GRASS)
                    t.set(x, top + 1, z, pick(t.rng, [(S('short_grass'), 3), (S('dead_bush'), 1), (S('fern'), 1)]))
            elif d <= w + 3.2 and m.at(x, 9, z) > 0.62:
                t.set(x, G, z, by_noise(n, SCORCH, x, G, z))   # soil thrown wide
    # Debris and scorch on the gouge's floor, a plate of hull in it.
    for x in range(8, 29):
        for z in range(ZC - 3, ZC + 4):
            if t.name_at(x, 0, z) and t.is_air(x, G, z):
                r = t.rng.random()
                if r < 0.16:
                    t.set(x, G, z, S('or:debris'))
                elif r < 0.26:
                    t.set(x, G, z, S('or:scorch_mark'))
    for (x, z) in ((12, 15), (17, 14), (24, 16)):
        t.set(x, G, z, by_noise(n, BELLY, x, G, z))


def port_wing(t):
    """The port wing, torn off at the second impact: it lies across the gouge's north lip, its root
    end (east) torn and propped on the earth."""
    for x in range(16, 24):
        for z in range(9, 13):
            if (x, z) in ((16, 9), (16, 12), (17, 12)):
                continue                                   # the wing tapers to its tip (west)
            y = F if x < 21 else F + 1
            t.set(x, y, z, slab('or:durasteel_plating_slab'))
            if y == F + 1 and t.get(x, F, z) in (None, AIR):
                t.set(x, F, z, by_noise(Noise(SEED + 9, 1.5), EARTH, x, F, z))
    for z in range(9, 13):
        t.set(24, F + 1, z, S('or:wreck_plating_scorched') if z % 2 else BARS)
    t.set(19, F + 1, 10, S('or:republic_panel'))           # the wing's marking plate
    t.set(22, F + 2, 11, S('or:scorch_mark'))


def fin(t):
    """The dorsal fin, snapped off and thrown north, lying on its side."""
    for x in range(23, 28):
        t.set(x, F, 4, S('or:wreck_plating') if x != 25 else S('or:republic_plating'))
        if x in (25, 26, 27):
            t.set(x, F, 3, S('or:wreck_plating_scorched'))
        if t.get(x, G, 4) is None:
            t.set(x, G, 4, S('coarse_dirt'))
    t.set(23, F + 1, 4, stairs('or:durasteel_plating_stairs', 'east'))
    t.set(27, F, 5, S('or:debris'))


# ---------------------------------------------------------------- the hull

TOP = {11: 4, 12: 5, 13: 6, 14: 6, 15: 6, 16: 6, 17: 5, 18: 4, 19: 3}   # rolled a little to starboard


def top_of(x, z):
    """The hull's top layer at a cell (the cockpit sits one lower toward the nose)."""
    return TOP[z] - (1 if x >= 45 else 0)


def hull_cells(x):
    """The z cells of the hull's section at x: 11..19, narrowing through the cockpit."""
    nose = x - 43 if x > 43 else 0
    return range(11 + nose, 20 - nose)


def hull(t):
    """The fuselage: plated sides and a rounded back, the keel dug in, the deck inside."""
    n = Noise(SEED + 11, 1.6)
    x0, x1 = 31, 46
    for x in range(x0, x1 + 1):
        zs = hull_cells(x)
        for z in zs:
            top = top_of(x, z)
            side = z in (zs[0], zs[-1])
            if 13 <= z <= 17:
                t.set(x, 0, z, by_noise(n, BELLY, x, 0, z))  # the keel, dug in
            t.set(x, G, z, by_noise(n, BELLY, x, G, z) if side else S('or:durasteel_deck'))
            for y in range(F, top + 1):
                if side or y == top or x in (x0, 43):
                    st = by_noise(n, BELLY if y <= 2 else HULL, x, y, z)
                else:
                    st = AIR
                t.set(x, y, z, st)
        # The back's rounded shoulders: stairs over each step of the section.
        for z in zs:
            top = top_of(x, z)
            if z + 1 in zs and top_of(x, z + 1) > top:
                t.set(x, top + 1, z, stairs('or:republic_plating_stairs', 'south'))
            elif z - 1 in zs and top_of(x, z - 1) > top:
                t.set(x, top + 1, z, stairs('or:republic_plating_stairs', 'north'))
    # A spine along the back, two bands of grey plating, the stub of the fin, a bent antenna.
    for x in range(x0, 44):
        t.set(x, 7, 14, slab('or:republic_plating_slab'))
    for x in (34, 39):
        for z in range(11, 20):
            y = top_of(x, z)
            t.set(x, y, z, S('or:durasteel_plating'))
    for x in (31, 32):
        t.set(x, 7, 15, S('or:wreck_plating_scorched'))
        t.set(x, 8, 15, BARS)
    t.set(41, 7, 13, S('lightning_rod', facing='up', powered=False, waterlogged=False))
    t.set(41, 8, 13, S('lightning_rod', facing='north', powered=False, waterlogged=False))
    # Viewports along the starboard side.
    for x in (35, 38, 41):
        t.set(x, 2, 19, S('black_stained_glass'))
    # The forward bulkhead's hatch into the cockpit.
    for z in (14, 15):
        for y in (F, F + 1):
            t.set(43, y, z, AIR)
    # The cockpit: a cracked dark canopy (its back and upper sides), two seats, the console.
    for x in (44, 45, 46):
        zs = hull_cells(x)
        for z in zs:
            if (x, z) in ((45, 14), (44, 17)):
                t.set(x, top_of(x, z), z, AIR)               # panes gone
            else:
                t.set(x, top_of(x, z), z, S('black_stained_glass'))
            for y in range(top_of(x, z) + 1, top_of(x, z) + 2):
                if t.name_at(x, y, z) == 'oldrepublic:republic_plating_stairs':
                    t.set(x, y, z, AIR)
        for z in (zs[0], zs[-1]):
            t.set(x, top_of(x, z) - 1, z, S('black_stained_glass'))
    t.set(44, F, 14, stairs('polished_blackstone_stairs', 'west'))
    t.set(44, F, 16, stairs('polished_blackstone_stairs', 'west'))
    t.set(45, F, 15, S('or:console', facing='west'))
    t.set(45, F + 1, 15, AIR)


def bay(t):
    """The cargo bay, torn open along its port (north) side: the pedestal, the strongbox, the cargo."""
    # The tear: the north wall gone from x 33 to 40, ragged at its edges, ribs hanging in it.
    for x in range(33, 41):
        for y in range(F, 6):
            if (x, y) in ((33, 4), (33, 5), (40, 4), (40, 5), (34, 5)):
                continue
            t.set(x, y, 11, AIR)
        if x in (35, 36, 37, 38):
            t.set(x, 5, 12, AIR)                             # the tear runs up over the shoulder
    t.set(36, 4, 11, BARS)
    t.set(38, 4, 11, BARS)
    t.set(39, 5, 12, BARS)
    # A hole burst in the roof above the bay, its ribs showing.
    for (x, z) in ((36, 13), (37, 13), (37, 14)):
        t.set(x, 6, z, AIR)
    t.set(36, 6, 14, BARS)
    t.set(38, 6, 13, BARS)
    # The hatch, flung flat on the ground in front of the tear.
    nh = Noise(SEED + 21, 1.0)
    for x in range(34, 40):
        for z in (7, 8, 9):
            if (x, z) in ((34, 7), (39, 9)):
                continue
            t.set(x, G, z, by_noise(nh, [(S('or:wreck_plating'), 3), (S('or:wreck_plating_scorched'), 1)], x, G, z))
    for x in range(33, 41):
        for z in (7, 8, 9, 10):
            for y in (F, F + 1):
                t.set(x, y, z, AIR)
        if t.get(x, G, 10) is None:
            t.set(x, G, 10, by_noise(nh, SCORCH, x, G, 10))
    # The Wayfarer's pedestal at the back of the bay, Vasch's strongbox by it.
    mod.pedestal(t, *PEDESTAL, kind='wayfarer', always_ready=True, anchor='PEDESTAL_WAYFARER',
                 fixed_id='wayfarers_compass')
    t.chest(*STRONGBOX, facing='north', loot='gameplay/wayfarer_cache')
    t.anchor('STRONGBOX', *STRONGBOX)
    # Cargo: crates still strapped at the back, others thrown about, a barrel split.
    for (x, y, z) in ((32, F, 18), (33, F, 18), (32, F + 1, 18), (32, F, 17), (41, F, 18), (42, F, 18),
                      (42, F + 1, 18), (42, F, 17)):
        t.set(x, y, z, S('or:cargo_crate'))
    t.set(40, F, 12, S('or:cargo_crate'))
    t.set(34, F, 13, barrel('north'))
    t.set(39, F, 16, S('or:debris'))
    t.set(33, F, 15, S('or:debris'))
    # Light: the lamp still hanging, an emergency panel in the aft bulkhead, a sparking conduit.
    t.set(36, 5, 15, LANTERN_HUNG)
    t.set(31, 4, 15, S('or:light_panel_amber'))
    t.set(31, 3, 13, S('or:wreck_conduit'))
    t.set(42, 4, 19, S('or:wreck_conduit'))
    # The cells the client tests stand in, north of the pedestal, stay open floor.
    px, py, pz = PEDESTAL
    for z in range(pz - 4, pz):
        t.set(px, py, z, AIR)
        t.set(px, py + 1, z, AIR)


def engines(t):
    """Two engine nacelles at the stern; the port one burned out and still smouldering."""
    n = Noise(SEED + 31, 1.2)
    for zs in ((12, 13), (17, 18)):
        for x in range(27, 31):
            for z in zs:
                for y in (F, F + 1):
                    t.set(x, y, z, by_noise(n, [(S('or:durasteel_plating'), 3), (S('or:wreck_plating_scorched'), 2)],
                                            x, y, z))
                t.set(x, G, z, by_noise(n, SCORCH, x, G, z))
        for z in zs:
            for y in (F, F + 1):
                t.set(27, y, z, S('or:durasteel_vent'))
    t.set(27, F + 1, 12, S('or:wreck_plating_scorched'))
    # The small fire: the port nozzle's fuel still burning on the scorched ground behind it.
    t.set(26, 0, 12, S('blackstone'))
    t.set(26, G, 12, campfire(True, facing='west'))
    t.set(26, F, 12, AIR)
    for (x, z) in ((25, 12), (26, 11), (26, 13), (25, 13), (24, 12), (25, 11)):
        if t.is_air(x, G, z) and t.get(x, 0, z) is not None:
            t.set(x, G, z, S('or:scorch_mark'))              # on the gouge's floor
        elif t.get(x, G, z) is not None and t.get(x, F, z) in (None, AIR):
            t.set(x, F, z, S('or:scorch_mark'))


def starboard_wing(t):
    """The starboard wing, crumpled under the hull as it rolled: a plate bent up at its tip."""
    for x in range(33, 41):
        for z in range(20, 24):
            if (x, z) in ((33, 23), (40, 23)):
                continue
            t.set(x, F, z, slab('or:durasteel_plating_slab'))
            if t.get(x, G, z) is None:
                t.set(x, G, z, S('coarse_dirt'))
        if 34 <= x <= 39:
            t.set(x, F, 24, stairs('or:durasteel_plating_stairs', 'south'))
    t.set(36, F + 1, 24, S('or:wreck_plating_scorched'))


def heap(t):
    """The earth the nose pushed up as it stopped, burying the tip of the cockpit."""
    n = Noise(SEED + 41, 2.0)
    for x in range(44, SIZE[0]):
        for z in range(6, 25):
            h = 6.4 - 0.45 * abs(z - ZC) - 0.7 * max(0, x - 48) + (n.at(x, 0, z) - 0.5) * 1.6
            if x < 47:
                h = min(h, 2.4 + 0.7 * (x - 44))
            top = int(math.floor(h))
            if top < G:
                continue
            for y in range(G, top + 1):
                if x <= 46 and 11 <= z <= 19 and y >= G and t.get(x, y, z) not in (None, AIR):
                    continue                                   # leave the cockpit's visible part
                t.set(x, y, z, by_noise(n, EARTH, x, y, z))
            if top >= F and n.at(x, 7, z) > 0.4:
                t.set(x, top, z, GRASS)
                if n.at(x, 8, z) > 0.75 and top + 1 < SIZE[1]:
                    t.set(x, top + 1, z, S('short_grass'))


def scattered(t):
    """Cargo strewn along the gouge and round the hull, scorch and debris on the grass."""
    crates = [(31, F, 8), (32, F, 7), (32, F + 1, 7), (29, F, 5), (20, F, 7), (13, F, 19), (27, F, 21),
              (42, F, 23), (44, F, 6), (8, F, 12)]
    for (x, y, z) in crates:
        t.set(x, y, z, S('or:cargo_crate'))
        if y == F and t.get(x, G, z) is None:
            t.set(x, G, z, by_noise(Noise(SEED + 51, 1.0), SCORCH, x, G, z))
    for (x, z) in ((30, 6), (21, 8), (14, 19), (28, 21), (43, 6), (10, 12), (33, 6), (45, 22)):
        if t.get(x, F, z) is None:
            t.set(x, F, z, S('or:debris'))
            if t.get(x, G, z) is None:
                t.set(x, G, z, S('coarse_dirt'))
    t.set(30, F, 8, barrel('east'))
    t.set(18, F, 19, barrel('up'))
    t.set(41, F, 7, S('decorated_pot', facing='north', cracked=True, waterlogged=False))
    # Scorched ground right round the hull.
    n = Noise(SEED + 52, 2.0)
    for x in range(25, 48):
        for z in range(4, 27):
            if t.get(x, G, z) is None and n.at(x, 0, z) > 0.42:
                near = min(abs(z - 11), abs(z - 19)) if 31 <= x <= 46 else 3
                if near <= 3 or (25 <= x <= 30 and 10 <= z <= 20):
                    t.set(x, G, z, by_noise(n, SCORCH, x, G, z))
                    if n.at(x, 3, z) > 0.75 and t.get(x, F, z) is None:
                        t.set(x, F, z, S('or:scorch_mark'))


def anchors(t):
    for name, (x, y, z) in ANCHORS.items():
        if t.get(x, y - 1, z) is None or not t.is_solid(x, y - 1, z) or mod.walk_through(t.get(x, y - 1, z)):
            t.set(x, y - 1, z, S('coarse_dirt'))
        spot(t, name, x, y, z)


def couriers_fall():
    t = Template(*SIZE, seed=SEED, name='story/couriers_fall')
    gouge(t)
    port_wing(t)
    fin(t)
    engines(t)
    hull(t)
    starboard_wing(t)
    heap(t)
    bay(t)
    scattered(t)
    anchors(t)
    t.finish()
    check(t, SIZE, feet=('HEART', 'SCAVENGER_1', 'SCAVENGER_2'),
          blocks={'PEDESTAL_WAYFARER': 'oldrepublic:holocron_pedestal', 'STRONGBOX': 'chest'})
    px, py, pz = PEDESTAL
    if not (t.is_air(px, py, pz - 4) and t.is_air(px, py + 1, pz - 4) and t.is_solid(px, py - 1, pz - 4)):
        raise SystemExit('couriers_fall: the stand four cells north of the pedestal is not open floor')
    return t


def main(argv):
    prev = preview_dir(argv)
    t = couriers_fall()
    write(t, 'story/couriers_fall', prev, META, views=('se', 'nw'), plans=[2])


if __name__ == '__main__':
    main(sys.argv[1:])
