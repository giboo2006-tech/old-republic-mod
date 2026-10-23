"""planet/crashed_freighter (worldgen, Ghessar's dunes): an old bulk freighter that came down nose first into the
dune sea, long ago, and has been picked over ever since.

The hull lies along x, engines west, cockpit east, listing to port (north) and sunk to its waist in sand that
has banked against its windward (north) side. A torn breach in the port side lets a sand spill into the hold;
salvagers stripped the dorsal plating over the hold down to the ribs (light falls into the hold through them)
and cut a hole over the engine room, where their gantry still holds the hyperdrive motivator half lifted out.
Their camp, their skiff loaded with plates and the kajidic's claim pole on the hull tell who works this wreck
now: "A ship falls, a crew dies, the sand covers everything. Vessba waits, and buys the sand."

Inside, stern to bow: the engine room (dead reactor, emergency red light, the empty motivator cradle), the
two-storey cargo hold (crate stacks, a crate hanging from the overhead hoist, a catwalk, the cargo cage with
the hold's chest), the crew deck (galley with a meal left half eaten, bunks, a med bay; the officers' deck
above), the forward compartment behind a jammed blast door, and the cockpit under its smashed canopy.

Kept anchors (docs/MILESTONE_6.md 5.6): HEART (the hold), CHEST_1 (chests/crashed_freighter), CRATE (the
recover prop, a barrel in the hold), CREW_1..4 and SURFACE_GUARD (on the sand outside).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gkit as G  # noqa: E402
from gkit import S, AIR, B, mod, W, Noise, Template  # noqa: E402

ID = 'planet/crashed_freighter'
SX, SY, SZ = 70, 26, 31
BURIED = 5            # the outside feet layer
ZC = 15
SEED = 960301

# Hull keyframes: (x, yc, zc, half width, half height, exponent). Nose down: the centre falls toward the bow.
KEYS = [
    (9, 9.0, ZC, 6.4, 5.6, 3.2),
    (12, 8.8, ZC, 7.6, 6.6, 3.0),
    (20, 8.4, ZC, 8.0, 6.9, 2.7),
    (44, 7.0, ZC, 8.0, 6.8, 2.6),
    (54, 6.2, ZC, 7.2, 6.0, 2.4),
    (60, 5.4, ZC, 5.4, 4.8, 2.2),
    (64, 4.8, ZC, 3.0, 3.0, 2.0),
    (66, 4.4, ZC, 1.4, 1.6, 2.0),
]
ROLL = 0.12

# Compartments: (x0, x1, floor y). Bulkheads stand between them.
ENGINE = (10, 17, 4)
HOLD = (19, 38, 3)
CREW = (40, 50, 2)
FORWARD = (52, 56, 2)
COCKPIT = (58, 65, 2)
BULKHEADS = (18, 39, 51, 57)
UPPER_Y = 7            # the officers' deck over the crew deck, and the hold's catwalk
BREACH = (23, 33)      # x range of the torn port side
RAMP = (25, 28)        # x range of the starboard cargo ramp, lowered into the sand
STRIPPED = (25, 37)    # x range of the dorsal plating stripped to the ribs


def build():
    t = Template(SX, SY, SZ, seed=SEED, name=ID)
    hull = G.Hull(KEYS, roll=ROLL)
    shell, inside = hull.voxels(SY, SZ)

    # ------------------------------------------------------------ ground: the dune, the furrow, the berm
    g = G.Ground(t, BURIED, 'dunes', seed=SEED)
    g.noise(amp=2, scale=9.0)
    g.trough([(-2, ZC + 1), (6, ZC), (11, ZC)], width=9, depth2=2, fall=3)        # the furrow behind the stern
    g.mound(66, ZC, 6.5, 11, 9)                                                    # sand ploughed up at the nose
    g.mound(57, 5, 9, 3.5, 4)                                                       # and pushed aside
    g.mound(57, 25, 9, 3.5, 3)
    # drifts banked against the hull at the sand line: high on the windward (north) side, low in the lee
    north_wall, south_wall = set(), set()
    for x in range(hull.x0, hull.x1 + 1):
        zs = [z for z in range(SZ) if (x, BURIED + 1, z) in shell or (x, BURIED + 1, z) in inside]
        if zs:
            north_wall.add((x, min(zs)))
            south_wall.add((x, max(zs)))
    g.drift({c for c in north_wall if not BREACH[0] - 1 <= c[0] <= BREACH[1] + 1}, 'north', 7, 6, seed=1)
    g.drift({c for c in north_wall if BREACH[0] - 1 <= c[0] <= BREACH[1] + 1}, 'north', 3, 3, seed=2, jag=0.0)
    g.drift({c for c in south_wall if not RAMP[0] - 2 <= c[0] <= RAMP[1] + 2}, 'south', 3, 3, seed=3)
    g.taper(slope=2)
    g.feather()
    g.apply(clear_above=4)

    # ------------------------------------------------------------ the hull shell
    rust = Noise(SEED + 5, 2.5)
    patch = Noise(SEED + 6, 5.0)
    scorch = Noise(SEED + 7, 3.0)

    def plating(x, y, z):
        u, v = hull.local(x, y, z)
        yc, zc, w, h, n = hull.at(x)
        # rust streaks run down from the seams on the sides; the top is sand-scoured
        streak = rust.at(x * 2.2, y * 0.35, z * 2.2)
        if x >= 61 and v > -1 and scorch.at(x, y, z) > 0.35:
            return 'or:wreck_plating_scorched'          # the nose burned coming down
        if x <= 11 and scorch.at(x, y, z) > 0.55:
            return 'or:wreck_plating_scorched'          # engine soot
        if 0.15 * h < v < 0.42 * h and abs(u) > 0.55 * w and patch.at(x, 0, z) < 0.62:
            return 'exposed_cut_copper'                 # the owners' faded livery stripe
        if v > 0.7 * h:
            return 'or:durasteel_plating' if patch.at(x, y, z) < 0.66 else 'or:wreck_plating'
        if streak > 0.64 and v < 0.6 * h:
            return 'or:rusted_plating'
        if x <= 14 and v > 0.2 * h and abs(u) > 0.6 * w and (x + y) % 4 == 0:
            return 'or:durasteel_vent'
        return 'or:durasteel_plating' if patch.at(x, y, z) < 0.58 else 'or:wreck_plating'

    for c in shell:
        t.set(*c, plating(*c))
    for c in inside:
        t.set(*c, AIR)

    def floor_of(x):
        for (a, b, fy) in (ENGINE, HOLD, CREW, FORWARD, COCKPIT):
            if a <= x <= b:
                return fy
        return None

    # ------------------------------------------------------------ decks, bilge, bulkheads
    deck = G.mix(SEED + 8, [('or:durasteel_deck', 8), ('or:durasteel_grate', 1)], 3.0)
    for (x, y, z) in inside:
        fy = floor_of(x)
        if x in BULKHEADS:
            continue
        if fy is None:
            continue
        if y < fy:
            t.set(x, y, z, 'or:durasteel_pipes[axis=x]' if (y == fy - 1 and z % 3 == 0) else 'or:durasteel_plating')
        elif y == fy:
            t.set(x, y, z, deck(x, y, z))
    for bx in BULKHEADS:
        for (x, y, z) in inside:
            if x == bx:
                t.set(x, y, z, 'or:wreck_plating' if (y + z) % 5 else 'or:durasteel_beam[axis=y]')

    def cut(bx, z0, z1, y0, y1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                if (bx, y, z) in inside:
                    t.set(bx, y, z, AIR)
        # a frame round the door
        for y in range(y0, y1 + 2):
            for z in (z0 - 1, z1 + 1):
                if (bx, y, z) in inside:
                    t.set(bx, y, z, 'or:durasteel_beam[axis=y]')
        for z in range(z0 - 1, z1 + 2):
            if (bx, y1 + 1, z) in inside:
                t.set(bx, y1 + 1, z, 'or:durasteel_beam[axis=z]')

    # engine room -> hold: a step down (the engine deck is one higher)
    cut(18, 14, 16, 5, 7)
    for z in range(14, 17):
        t.set(18, 4, z, 'or:durasteel_deck')
        t.set(19, 4, z, G.slab('or:durasteel_deck_slab'))
    # hold -> crew deck: a step down
    cut(39, 14, 16, 4, 6)
    for z in range(14, 17):
        t.set(39, 3, z, G.slab('or:durasteel_deck_slab'))
        t.set(39, 2, z, 'or:durasteel_plating')
    # catwalk -> officers' deck
    cut(39, 19, 20, 8, 10)
    for z in (19, 20):
        t.set(39, UPPER_Y, z, 'or:durasteel_deck')
    # crew deck -> forward compartment: the jammed blast door (a gap two wide and two high)
    for z in range(13, 19):
        for y in range(3, 7):
            if (51, y, z) in inside:
                t.set(51, y, z, 'or:durasteel_plating' if z in (13, 18) or y >= 6 else 'or:wreck_plating_scorched')
    for z in (15, 16):
        for y in (3, 4):
            t.set(51, y, z, AIR)
    # (the frame's north corner faces west: its full west face carries the ladder beside the door)
    t.set(51, 5, 14, G.stair('or:durasteel_plating_stairs', 'west', 'top'))
    t.set(51, 5, 17, G.stair('or:durasteel_plating_stairs', 'west', 'top'))
    for y in range(3, 7):
        t.set(51, y, 12, 'or:durasteel_beam[axis=y]')
        t.set(51, y, 19, 'or:durasteel_beam[axis=y]')
    # forward -> cockpit
    cut(57, 14, 15, 3, 5)

    # inner ribs: frames every fourth cell along the hold and the engine room walls
    for (x, y, z) in inside:
        if x % 4 == 2 and (HOLD[0] <= x <= HOLD[1] or ENGINE[0] <= x <= ENGINE[1]):
            fy = floor_of(x)
            if fy is not None and y > fy:
                near_shell = any(q in shell for q in ((x, y, z - 1), (x, y, z + 1), (x, y + 1, z)))
                if near_shell:
                    t.set(x, y, z, 'or:durasteel_beam[axis=y]' if not (x, y + 1, z) in shell else 'or:durasteel_beam[axis=z]')

    # ------------------------------------------------------------ the officers' deck over the crew deck
    for (x, y, z) in inside:
        if CREW[0] <= x <= CREW[1] and y == UPPER_Y:
            t.set(x, y, z, deck(x, y, z))
    # crew deck partitions: rooms north and south of a corridor (z 14..16), split at x 45
    for x in range(CREW[0], CREW[1] + 1):
        for y in range(CREW[2] + 1, UPPER_Y):
            for z in (13, 17):
                if (x, y, z) in inside:
                    t.set(x, y, z, 'or:durasteel_plating' if (x + y) % 6 else 'or:durasteel_vent')
            for z in range(8, 23):
                if x == 45 and (x, y, z) in inside and (z < 13 or z > 17):
                    t.set(x, y, z, 'or:durasteel_plating')
    for (x0, z) in ((42, 13), (47, 13), (42, 17), (47, 17)):
        for x in (x0, x0 + 1):
            for y in (3, 4, 5):
                t.set(x, y, z, AIR)
    # a hatch and ladder from the corridor to the officers' deck
    for y in range(3, UPPER_Y + 1):
        t.set(50, y, 14, G.ladder('west'))
    t.set(50, UPPER_Y + 1, 14, AIR)

    # ------------------------------------------------------------ the catwalk round the hold's south side
    for x in range(20, 39):
        for z in range(19, 23):
            if (x, UPPER_Y, z) in inside:
                t.set(x, UPPER_Y, z, 'or:durasteel_grate')
        if (x, UPPER_Y + 1, 18) in inside and not 21 <= x <= 22:
            t.set(x, UPPER_Y + 1, 18, S('iron_bars'))
        if (x, UPPER_Y, 18) in inside and not 21 <= x <= 22:
            t.set(x, UPPER_Y, 18, G.slab('or:durasteel_plating_slab', 'top'))
    # stairs up to it from the hold floor (west end, climbing south)
    for i, z in enumerate((15, 16, 17, 18)):
        for x in (21, 22):
            t.set(x, HOLD[2] + 1 + i, z, G.stair('or:durasteel_plating_stairs', 'south'))
            for y in range(HOLD[2] + 1, HOLD[2] + 1 + i):
                t.set(x, y, z, 'or:durasteel_plating')
    # supports under the catwalk
    for x in (25, 29):
        for y in range(HOLD[2] + 1, UPPER_Y):
            t.set(x, y, 19, 'or:durasteel_beam[axis=y]')

    # ------------------------------------------------------------ openings: breach, stripped dorsal, salvage cut
    tear = Noise(SEED + 9, 2.0)
    breach_cells = set()
    for c in list(shell):
        x, y, z = c
        if BREACH[0] <= x <= BREACH[1] and z < ZC - 3:
            top = 11 + int((tear.at(x, 0, 0) - 0.5) * 4) - abs(x - 28) // 3
            if BURIED <= y <= top:
                breach_cells.add(c)
    for c in breach_cells:
        t.set(*c, AIR)
    # torn metal curling out round the breach, scorched
    for c in shell:
        x, y, z = c
        if c in breach_cells:
            continue
        near = any((x + dx, y + dy, z + dz) in breach_cells for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1))
        if near:
            t.set(*c, 'or:wreck_plating_scorched' if tear.at(x, y, z) > 0.4 else 'or:rusted_plating')
            if tear.at(x * 3, y, z) > 0.7 and G.free(t, x, y, z - 1):
                t.set(x, y, z - 1, G.stair('or:durasteel_plating_stairs', 'south', 'top' if y > 8 else 'bottom'))

    yc_top = {}
    for x in range(hull.x0, hull.x1 + 1):
        tops = [c[1] for c in shell if c[0] == x]
        yc_top[x] = max(tops) if tops else 0
    stripped = set()
    for c in shell:
        x, y, z = c
        if STRIPPED[0] <= x <= STRIPPED[1] and x % 3 != 0:
            u, v = hull.local(x, y, z)
            yc, zc, w, h, n = hull.at(x)
            edge = 0.62 + 0.12 * tear.at(x, 5, 0)
            if v > edge * h and abs(u) < 0.62 * w:
                stripped.add(c)
    for c in stripped:
        t.set(*c, AIR)
    for c in shell:
        x, y, z = c
        if STRIPPED[0] <= x <= STRIPPED[1] and x % 3 == 0:
            u, v = hull.local(x, y, z)
            yc, zc, w, h, n = hull.at(x)
            if v > 0.55 * h:
                t.set(*c, 'or:durasteel_beam[axis=z]' if abs(u) < 0.4 * w else 'or:durasteel_beam[axis=y]')
    # the salvage cut over the engine room: clean edges, scorched by the cutters
    for c in shell:
        x, y, z = c
        if 13 <= x <= 15 and 13 <= z <= 17 and y >= 12:
            t.set(*c, AIR)
        elif 12 <= x <= 16 and 12 <= z <= 18 and y >= 12:
            t.set(*c, 'or:wreck_plating_scorched')
    # a few corrosion holes high on the hull
    holes = Noise(SEED + 10, 1.6)
    for c in shell:
        x, y, z = c
        if 40 <= x <= 56 and y >= 10 and holes.at(x, y, z) > 0.83:
            t.set(*c, AIR)

    # ------------------------------------------------------------ cockpit canopy (smoked glass, smashed in)
    for c in shell:
        x, y, z = c
        if x >= 59 and c not in stripped:
            u, v = hull.local(x, y, z)
            yc, zc, w, h, n = hull.at(x)
            if v > 0.15 * h and abs(u) < 0.85 * w and y >= 6:
                k = tear.at(x * 2, y, z * 2)
                if z < ZC - 1 and k > 0.5:
                    t.set(*c, AIR)                       # smashed out on the windward side
                elif k > 0.72:
                    t.set(*c, S('glass_pane'))
                else:
                    t.set(*c, 'tinted_glass' if (y + z) % 3 else 'black_stained_glass')
    # canopy frame ribs
    for c in shell:
        x, y, z = c
        if x in (59, 62) and y >= 6 and t.get(*c) in ('minecraft:tinted_glass', 'minecraft:black_stained_glass'):
            t.set(*c, 'or:durasteel_beam[axis=y]')

    # ------------------------------------------------------------ engines, fins, spine
    nozzles = [(8.0, 10, 2.4), (8.0, 20, 2.4), (12.6, ZC, 1.6)]
    for (ny, nz, r) in nozzles:
        for x in range(4, 10):
            for y in range(int(ny - r - 1), int(ny + r + 2)):
                for z in range(int(nz - r - 1), int(nz + r + 2)):
                    d = math.hypot(y - ny, z - nz)
                    flare = r + (0.6 if x <= 5 else 0.0)
                    if d <= flare:
                        if d > flare - 1.1:
                            t.set(x, y, z, 'or:wreck_plating_scorched' if (x + y + z) % 3 else 'or:durasteel_vent')
                        elif x >= 8:
                            t.set(x, y, z, 'blackstone' if x == 9 else 'polished_blackstone')
                        else:
                            t.set(x, y, z, AIR)
        # a sooty rim
        for y in range(int(ny - r), int(ny + r + 1)):
            for z in range(int(nz - r), int(nz + r + 1)):
                if abs(math.hypot(y - ny, z - nz) - r) < 0.5:
                    t.set(4, y, z, 'or:durasteel_beam[axis=x]')
    # the dorsal fin over the stern, swept back, its tip bent
    for i, x in enumerate(range(9, 13)):
        top = yc_top.get(x, 14)
        hgt = (7, 6, 4, 2)[i]
        for k in range(1, hgt + 1):
            z = ZC + (1 if k >= hgt - 1 and hgt > 3 else 0)
            t.set(x, top + k, z, 'or:rusted_plating' if (x + k) % 3 == 0 else 'or:durasteel_plating')
    # wing stubs amidships: the starboard one whole (the salvagers camp in its shade), the port one snapped
    # off (it lies in the furrow)
    for x in range(17, 23):
        span = 5 if x < 22 else 3
        for k in range(1, span + 1):
            z = 23 + k
            if G.free(t, x, 9, z):
                t.set(x, 9, z, G.slab('or:durasteel_plating_slab', 'bottom' if k > 3 else 'double'))
        for k in range(1, 3 if x % 2 else 2):
            if G.free(t, x, 9, 7 - k):
                t.set(x, 9, 7 - k, 'or:wreck_plating_scorched' if k == 2 else 'or:durasteel_plating')
    # the dorsal spine: a beam along the top (it bridges the stripped section like a backbone)
    for x in range(18, 52):
        top = yc_top.get(x, 12)
        zt = int(round(ZC - (top - hull.at(x)[0]) * math.sin(ROLL)))
        if (x, top, zt) in shell or (x, top, zt) in stripped:
            t.set(x, top + 1, zt, 'or:durasteel_beam[axis=x]')
    # a sensor mast near the bow, bent over
    mt = yc_top.get(55, 10)
    for k in range(1, 5):
        t.set(55, mt + k, ZC - 1 if k < 4 else ZC - 2, G.rod('up') if k == 4 else S('iron_bars'))

    # ------------------------------------------------------------ the engine room
    ex0, ex1, efy = ENGINE
    # the reactor housing (south side), cold; the emergency light still burns red in its core
    for y in range(efy + 1, efy + 7):
        for (x, z) in ((13, 17), (14, 17), (15, 17), (13, 19), (15, 19), (13, 18), (15, 18), (14, 19)):
            if (x, y, z) in inside:
                t.set(x, y, z, 'or:durasteel_vent' if (y + x) % 2 else 'or:durasteel_pipes[axis=y]')
        t.set(14, y, 18, 'or:light_panel_red' if y in (efy + 2, efy + 4) else 'or:durasteel_pipes[axis=y]')
    for y in (efy + 2, efy + 3, efy + 4):
        t.set(14, y, 17, S('iron_bars'))
    # the empty motivator cradle under the salvage cut
    for (x, z) in ((13, 13), (15, 13), (13, 15), (15, 15)):
        for y in range(efy + 1, efy + 3):
            t.set(x, y, z, S('iron_bars'))
    t.set(14, efy + 1, 14, 'or:durasteel_deck')
    t.set(14, efy + 2, 14, 'or:scorch_mark')
    # conduits along the ceiling to the nozzles, consoles on the stern bulkhead
    for x in range(ex0, ex1 + 1):
        for z in (10, 11):
            for y in (12, 11):
                if (x, y, z) in inside and (x, y + 1, z) in shell:
                    t.set(x, y, z, 'or:durasteel_pipes[axis=x]')
    for z in (12, 13, 14):
        if (ex0, efy + 1, z) in inside:
            t.set(ex0, efy + 1, z, S('or:console', facing='east'))
    if (ex0, efy + 2, 13) in inside:
        t.set(ex0, efy + 2, 13, S('or:holo_screen', facing='east'))
    t.set(11, efy + 1, 20, G.barrel('up'))
    t.set(12, efy + 1, 20, 'chipped_anvil[facing=north]')
    t.set(16, efy + 1, 11, 'or:debris')
    t.set(12, efy + 1, 16, 'or:debris')
    for (x, y, z) in ((16, 9, 20), (11, 9, 10)):
        if (x, y, z) in inside:
            t.set(x, y, z, 'or:light_panel_red')

    # ------------------------------------------------------------ the hold
    hx0, hx1, hfy = HOLD
    fl = hfy + 1  # feet layer
    # the overhead hoist rail and a crate hanging from it
    for x in range(20, 38):
        if (x, 12, ZC) in inside:
            t.set(x, 12, ZC, 'or:durasteel_beam[axis=x]')
    t.set(31, 11, ZC, 'or:durasteel_plating')
    for y in (10, 9):
        t.set(31, y, ZC, G.chain('y'))
    t.set(31, 8, ZC, 'or:cargo_crate_imperial')
    # crate stacks: west wall, east wall, and a row in the middle; one stack toppled
    rng = t.rng
    stacks = [(20, 11, 3), (20, 12, 2), (20, 10, 2), (21, 10, 1), (20, 13, 1),
              (37, 10, 3), (37, 11, 2), (36, 10, 2), (37, 12, 1), (36, 11, 1),
              (34, 15, 2), (35, 15, 3), (35, 16, 1), (34, 16, 1), (36, 16, 2),
              (37, 19, 2), (37, 20, 1)]
    for (x, z, hgt) in stacks:
        hgt = len([k for k in range(hgt) if (x, fl + k, z) in inside])
        G.crate_stack(t, x, fl, z, hgt, rng, [('or:cargo_crate', 5), ('or:cargo_crate_republic', 2),
                                               ('or:cargo_crate_imperial', 1), (G.barrel(), 2)])
    for (x, y, z) in ((29, fl, 18), (30, fl, 17), (31, fl, 19), (27, fl, 19)):
        t.set(x, y, z, 'or:cargo_crate')                  # a toppled stack, crates strewn
    t.set(28, fl, 18, 'or:debris')
    t.set(32, fl, 18, 'or:debris')
    t.set(30, fl + 1, 17, G.slab('or:durasteel_plating_slab'))
    # the cargo cage under the catwalk, cut open; the hold's chest inside it
    for x in range(32, 37):
        for y in range(fl, fl + 3):
            if (x, y, 18) in inside:
                t.set(x, y, 18, S('iron_bars'))
    for y in range(fl, fl + 3):
        for z in (19, 20, 21):
            if (32, y, z) in inside:
                t.set(32, y, z, S('iron_bars'))
    t.set(33, fl, 18, AIR)
    t.set(33, fl + 1, 18, AIR)
    t.set(34, fl, 18, AIR)
    t.set(34, fl + 1, 18, AIR)
    t.chest(35, fl, 20, 'north', 'chests/crashed_freighter')
    t.anchor('CHEST_1', 35, fl, 20)
    t.set(36, fl, 20, G.barrel('up'))
    t.set(34, fl, 21, 'or:cargo_crate_republic')
    t.set(33, fl, 20, 'or:debris')
    # the recover crate (the old freighter's barrel) among the west stacks
    t.set(21, fl, 12, G.barrel('up'))
    t.anchor('CRATE', 21, fl, 12)
    # amber deck lights on the ribs (a few dead)
    for (x, y, z) in ((26, 9, 9), (34, 9, 9), (26, 10, 21), (30, 6, 22)):
        if (x, y, z) in inside or (x, y, z) in shell:
            t.set(x, y, z, 'or:light_panel_amber')
    # the cargo master's post at the catwalk's east end
    t.set(37, UPPER_Y + 1, 21, S('or:console', facing='west'))
    t.set(36, UPPER_Y + 1, 21, G.stair('or:durasteel_plating_stairs', 'west'))
    if (37, UPPER_Y + 2, 21) in inside:
        t.set(37, UPPER_Y + 2, 21, S('or:holo_screen', facing='west'))
    # the starboard cargo ramp: the crew got out this way; the salvagers haul their plates out down it now
    for x in range(RAMP[0], RAMP[1] + 1):
        for z in (22, 23, 24):
            for y in range(fl, fl + 3):
                if (x, y, z) in shell:
                    t.set(x, y, z, AIR)
        for z in range(23, 27):
            t.set(x, fl, z, G.slab('or:durasteel_plating_slab'))
            for y in range(fl + 1, fl + 3):
                if z >= 25:
                    t.set(x, y, z, AIR)
    for x in (RAMP[0] - 1, RAMP[1] + 1):
        for y in range(fl, fl + 3):
            t.set(x, y, 23, 'or:durasteel_beam[axis=y]')
        t.set(x, fl, 24, 'or:durasteel_beam[axis=z]')
        t.set(x, fl, 25, G.stair('or:durasteel_plating_stairs', 'north'))
        t.set(x, fl + 1, 24, 'or:durasteel_pipes[axis=z]')            # the ramp's rams, still extended
    for x in range(RAMP[0] - 1, RAMP[1] + 2):
        t.set(x, fl + 3, 23, 'or:durasteel_beam[axis=x]')
    for (x, z) in ((RAMP[0] + 1, 26), (RAMP[1], 26), (RAMP[1], 25)):
        t.set(x, fl, z, G.slab('sandstone_slab'))                     # the sand creeping up it
    t.set(RAMP[0] + 2, fl, 22, G.slab('sandstone_slab'))
    t.set(RAMP[0], fl + 3, 22, 'or:light_panel_amber')               # the ramp light, still on
    # tie-down rails across the hold floor, scuffed; scorch and debris where the cargo was dragged out
    for x in range(20, 38):
        for z in (13, 18):
            if (x, hfy, z) in inside and x % 2 == 0:
                t.set(x, hfy, z, 'or:durasteel_grate')
    for (x, z) in ((24, 16), (25, 17), (26, 19), (30, 15), (33, 16), (23, 20), (36, 14)):
        if G.free(t, x, fl, z):
            t.set(x, fl, z, 'or:debris' if (x + z) % 3 else 'or:scorch_mark')
    # a cargo sled half buried by the spill, its load gone
    for x in (23, 24):
        for z in (12, 13):
            t.set(x, fl, z, G.slab('or:durasteel_plating_slab', 'top'))
    t.set(25, fl, 12, 'or:durasteel_pipes[axis=x]')
    t.set(23, fl + 1, 12, 'or:cargo_crate_republic')
    t.set(24, fl + 1, 13, G.trapdoor('spruce_trapdoor', 'south', 'bottom', True))   # a lid pried off
    # cargo straps hanging from the hoist rail and a second, empty hook
    for (x, y0) in ((24, 9), (35, 10)):
        for y in range(y0, 12):
            if (x, y, ZC) in inside:
                t.set(x, y, ZC, G.chain('y'))
        if (x, y0 - 1, ZC) in inside:
            t.set(x, y0 - 1, ZC, G.rod('down'))
    # torn cables dangling from the stripped ribs
    for (x, z, n) in ((27, 12, 2), (30, 18, 3), (33, 13, 2)):
        top = max((y for y in range(SY) if (x, y, z) in inside), default=None)
        if top is not None:
            for k in range(n):
                if (x, top - k, z) in inside and G.free(t, x, top - k, z):
                    t.set(x, top - k, z, G.chain('y'))
    # one of the crew, under the sand by the breach
    t.set(26, fl, 14, S('skeleton_skull', rotation=5, powered=False))
    if G.free(t, 27, fl, 14):
        t.set(27, fl, 14, 'bone_block[axis=x]')
    # sand: the spill through the breach, and sand blown in under the stripped roof
    for x in range(BREACH[0] - 1, BREACH[1] + 2):
        crest = 13 - abs(x - 28) // 2
        for z in range(6, ZC + 4):
            col = [y for y in range(0, SY) if (x, y, z) in inside or (x, y, z) in breach_cells]
            if not col:
                continue
            zw = min(zz for zz in range(SZ) if (x, BURIED + 1, zz) in inside or (x, BURIED + 1, zz) in breach_cells or (x, BURIED + 1, zz) in shell)
            h2 = max(2 * fl, crest - max(0, z - zw))
            if h2 <= 2 * fl:
                continue
            for y in range(hfy, h2 // 2):
                if (x, y, z) in inside or (x, y, z) in breach_cells or (x, y, z) in shell:
                    t.set(x, y, z, 'sand')
            if h2 % 2 and ((x, h2 // 2, z) in inside or (x, h2 // 2, z) in breach_cells):
                t.set(x, h2 // 2, z, G.slab('sandstone_slab'))
    for (x, z) in ((27, 14), (28, 13), (31, 15), (33, 14), (26, 15)):
        if (x, fl, z) in inside and G.free(t, x, fl, z):
            t.set(x, fl, z, G.slab('sandstone_slab'))
    mod.spot(t, 'HEART', 28, fl, 17)

    # ------------------------------------------------------------ the crew deck
    cfy = CREW[2]
    cf = cfy + 1
    # galley (north-west): the meal left on the table, the stove, a cupboard; a stool knocked over
    G.slab_table(t, 41, 10, 43, 10, cf)
    t.set(42, cf + 1, 10, S('cake', bites=3))
    t.set(41, cf + 1, 10, G.candle(1))
    for x in (41, 43):
        G.chair(t, x, cf, 11, 'north', 'or:durasteel_plating_stairs')
    t.set(42, cf, 11, G.stair('or:durasteel_plating_stairs', 'east', 'top'))
    t.set(44, cf, 9, S('smoker', facing='east', lit=False))
    t.set(44, cf, 10, S('water_cauldron', level=1))
    t.set(44, cf + 1, 9, G.trapdoor('iron_trapdoor', 'east', 'top', True))
    t.set(40, cf, 9, G.barrel('east'))
    t.set(40, cf + 1, 9, G.barrel('east'))
    t.set(43, cf + 2, 12, 'or:light_panel')
    # med bay (north-east)
    G.bed(t, 49, cf, 10, 'west', 'white')
    t.set(46, cf, 9, S('brewing_stand', has_bottle_0=False, has_bottle_1=False, has_bottle_2=False))
    t.set(46, cf, 12, G.barrel('up'))
    t.set(47, cf, 9, G.trapdoor('iron_trapdoor', 'south', 'top', True))
    t.set(48, cf + 2, 12, 'or:light_panel')
    # bunks (south-west): two double bunks and the crew's lockers
    for x in (41, 43):
        G.bed(t, x, cf, 19, 'north', 'brown')
        t.set(x, cf + 1, 19, G.slab('or:durasteel_plating_slab', 'top'))
        t.set(x, cf + 1, 20, G.slab('or:durasteel_plating_slab', 'top'))
        G.bed(t, x, cf + 2, 19, 'north', 'gray')
    t.set(42, cf, 21, G.barrel('north'))
    t.set(44, cf, 21, S('potted_dead_bush'))
    t.set(42, cf + 2, 18, 'or:light_panel')
    # lockers (south-east), one burst open
    for z in (19, 20, 21):
        t.set(50, cf, z, G.trapdoor('iron_trapdoor', 'west', 'top', z != 20))
        t.set(50, cf + 1, z, G.trapdoor('iron_trapdoor', 'west', 'bottom', z == 21))
    t.set(48, cf, 20, 'or:debris')
    t.set(47, cf, 21, G.barrel('up'))
    t.set(47, cf + 2, 18, 'or:light_panel')
    # corridor: a light over the hatch
    if (45, cf + 3, 15) in inside:
        t.set(45, cf + 3, 15, 'or:light_panel')

    # ------------------------------------------------------------ the officers' deck
    of = UPPER_Y + 1
    G.bed(t, 41, of, 12, 'north', 'red')
    t.set(42, of, 11, G.barrel('up'))
    t.set(42, of + 1, 11, S('potted_dead_bush'))
    G.slab_table(t, 44, 11, 45, 11, of)
    G.chair(t, 44, of, 12, 'north', 'or:durasteel_plating_stairs')
    t.set(45, of + 1, 11, G.candle(2))
    for z in (12, 13, 14):
        if (49, of, z) in inside:
            t.set(49, of, z, S('or:console', facing='west'))
    if (49, of + 1, 13) in inside:
        t.set(49, of + 1, 13, S('or:holo_screen', facing='west'))
    t.set(48, of, 13, G.stair('or:durasteel_plating_stairs', 'west'))
    t.set(46, of, 19, G.barrel('up'))
    t.set(47, of, 19, 'or:cargo_crate_republic')
    if (44, of + 2, 15) in inside:
        t.set(44, of + 2, 15, 'or:light_panel')

    # ------------------------------------------------------------ the forward compartment
    ff = FORWARD[2] + 1
    for z in (12, 13):
        if (55, ff, z) in inside:
            t.set(55, ff, z, S('or:console', facing='south'))
    if (54, ff, 13) in inside:
        t.set(54, ff, 13, S('or:holo_screen', facing='south'))
    t.set(53, ff, 13, G.stair('or:durasteel_plating_stairs', 'south', 'top'))
    t.set(55, ff, 18, G.barrel('up'))
    t.set(55, ff + 1, 18, S('waxed_weathered_copper_bulb', lit=True, powered=False))
    t.set(53, ff, 18, 'or:cargo_crate')
    t.set(54, ff, 16, 'or:scorch_mark')
    t.set(53, ff, 15, 'or:debris')
    if (56, ff + 2, 15) in inside:
        t.set(56, ff + 2, 15, 'or:light_panel_red')

    # ------------------------------------------------------------ the cockpit
    kf = COCKPIT[2] + 1
    for x in range(59, 62):
        for z in range(10, 21):
            if (x, kf, z) in inside:
                t.set(x, kf, z, G.slab('or:durasteel_deck_slab'))
    for z in (13, 17):
        t.set(60, kf, z, G.stair('or:durasteel_plating_stairs', 'west'))
        t.set(59, kf, z, G.slab('or:durasteel_deck_slab'))
    for z in range(11, 20):
        if (62, kf, z) in inside:
            t.set(62, kf, z, S('or:console', facing='west'))
    for z in (13, 17):
        if (62, kf + 1, z) in inside:
            t.set(62, kf + 1, z, S('or:holo_screen', facing='west'))
    t.set(58, kf, 16, S('skeleton_skull', rotation=12, powered=False))
    # sand blown in through the smashed canopy
    for (x, z) in ((61, 11), (61, 12), (60, 11), (61, 14), (63, 13), (63, 12)):
        if (x, kf, z) in inside:
            t.set(x, kf, z, 'sand' if (x + z) % 2 else G.slab('sandstone_slab'))

    # ------------------------------------------------------------ outside: the salvagers
    def gy(x, z):
        return g.feet(x, z)
    # the gantry over the salvage cut: two portal frames at x 12 and 16, tied along x at the top and the knees
    for gx in (12, 16):
        for (z, yf) in ((4, gy(gx, 4)), (26, gy(gx, 26))):
            t.set(gx, yf, z, 'or:durasteel_plating')
            for y in range(yf + 1, 20):
                t.set(gx, y, z, 'or:durasteel_beam[axis=y]')
            for dz in (-1, 1):
                t.set(gx, yf, z + dz, G.stair('or:durasteel_plating_stairs', 'south' if dz < 0 else 'north'))
        for z in range(4, 27):
            t.set(gx, 20, z, 'or:durasteel_beam[axis=z]')
    for x in range(13, 16):
        t.set(x, 20, ZC, 'or:durasteel_beam[axis=x]')
        for z in (4, 26):
            t.set(x, 11, z, 'or:durasteel_beam[axis=x]')
            t.set(x, 19, z, 'or:durasteel_beam[axis=x]')
    t.set(14, 19, ZC, 'or:durasteel_plating')
    t.set(14, 18, ZC, G.chain('y'))
    t.set(14, 17, ZC, 'weathered_copper_grate[waterlogged=false]')
    t.set(14, 16, ZC, 'or:durasteel_vent')
    t.set(14, 15, ZC, 'or:durasteel_pipes[axis=y]')
    for (dx, dz) in ((-1, 0), (1, 0)):
        t.set(14 + dx, 17, ZC + dz, G.chain('y'))
        t.set(14 + dx, 18, ZC + dz, G.chain('y'))
        t.set(14 + dx, 19, ZC + dz, G.chain('y'))
    # the winch station at the north legs
    wy = gy(10, 2)
    t.set(10, wy, 2, S('or:console', facing='south'))
    t.set(11, wy, 2, G.barrel('up'))
    for x in (8, 9):
        t.set(x, wy, 3, G.chain('x'))
    t.set(10, wy, 4, S('waxed_weathered_copper_bulb', lit=True, powered=False))
    # their camp in the lee (south) of the stern
    cy = gy(21, 27)
    for x in range(19, 24):
        t.set(x, cy, 29, 'or:rusted_plating')
        t.set(x, cy + 1, 29, 'or:rusted_plating')
        t.set(x, cy + 2, 28, G.stair('or:durasteel_plating_stairs', 'south'))
        t.set(x, cy + 2, 27, G.slab('or:canvas_slab', 'top'))
        t.set(x, cy + 2, 26, G.slab('or:canvas_slab', 'top'))
    for x in (19, 23):
        t.set(x, cy, 26, S('spruce_fence'))
        t.set(x, cy + 1, 26, S('spruce_fence'))
    G.bedroll(t, 20, cy, 28, 'x', 'brown')
    t.set(22, cy, 28, G.barrel('up'))
    t.set(21, cy, 24, G.campfire(False))
    for (x, z) in ((20, 24), (22, 24), (21, 23)):
        if G.free(t, x, cy, z):
            t.set(x, cy, z, 'or:scorch_mark')
    t.set(24, cy, 27, S('cauldron'))
    # sorted salvage: plate stacks by the camp
    for (x, z, hgt) in ((25, 28, 2), (26, 28, 3), (25, 29, 1), (17, 28, 2)):
        for k in range(hgt):
            t.set(x, cy + k, z, 'or:rusted_plating' if k % 2 else 'or:durasteel_plating')
    # their skiff, half loaded
    sy0 = gy(34, 27)
    G.skiff(t, 31, sy0, 25, 9, 5, 'x')
    for (x, z) in ((34, 26), (35, 26), (34, 27), (37, 27)):
        t.set(x, sy0 + 2, z, 'or:rusted_plating' if (x + z) % 2 else 'or:durasteel_plating')
    t.set(35, sy0 + 3, 26, G.slab('or:durasteel_plating_slab'))
    t.set(38, sy0 + 2, 26, 'or:cargo_crate')
    # the kajidic's claim pole on the hull's back
    py = yc_top.get(48, 12) + 1
    G.claim_pole(t, 48, py, ZC - 1, height=8, cloth_side='east', banner_side='south')

    # ------------------------------------------------------------ the furrow's debris, westward
    deb = G.mix(SEED + 12, [('or:rusted_plating', 3), ('or:wreck_plating', 2), ('or:wreck_plating_scorched', 2)], 2.0)
    pieces = [(1, 12), (3, 18), (5, 13), (6, 21), (2, 22), (7, 9), (0, 16), (4, 15)]
    for (x, z) in pieces:
        y = gy(x, z)
        t.set(x, y - 1, z, deb(x, y, z))
        r = t.rng.random()
        if r < 0.4:
            t.set(x, y, z, G.stair('or:durasteel_plating_stairs', t.rng.choice(B.DIRS)))
        elif r < 0.7:
            t.set(x, y, z, deb(x, y, z))
            t.set(x + 1, y, z, G.slab('or:durasteel_plating_slab'))
    # the snapped port wing, tilted in the sand
    for x in range(2, 7):
        for z in range(4, 8):
            y = gy(x, z)
            t.set(x, y - (1 if z < 5 else 0), z, G.slab('or:durasteel_plating_slab', 'top' if z >= 6 else 'bottom'))
    # a cracked nozzle ring standing in the sand
    ny0 = gy(3, 26)
    for (dy, dz) in ((0, 0), (1, -1), (2, -1), (3, 0), (3, 1), (2, 2)):
        t.set(3, ny0 + dy, 26 + dz, 'or:wreck_plating_scorched')
    for x in range(0, 12):
        for z in range(8, 23):
            if t.rng.random() < 0.06:
                y = gy(x, z)
                if G.free(t, x, y, z):
                    t.set(x, y, z, 'or:debris' if t.rng.random() < 0.7 else 'or:scorch_mark')
    g.scatter(0.012, avoid={(x, z) for x in range(0, 12) for z in range(8, 23)})

    # ------------------------------------------------------------ anchors outside
    for name, (x, z) in (('CREW_1', (21, 2)), ('CREW_2', (44, 3)), ('CREW_3', (43, 27)), ('CREW_4', (54, 26)),
                         ('SURFACE_GUARD', (28, 3))):
        y = gy(x, z)
        mod.spot(t, name, x, y, z)

    # ------------------------------------------------------------ light the interiors
    allowed = set(inside) | breach_cells
    regions = [((x0, y0, 6, x1, y1, 24), allowed) for (x0, y0, x1, y1) in (
        (ENGINE[0], ENGINE[2] + 1, ENGINE[1], 15), (HOLD[0], HOLD[2] + 1, HOLD[1], 15),
        (CREW[0], CREW[2] + 1, CREW[1], 14), (FORWARD[0], FORWARD[2] + 1, COCKPIT[1], 12))]
    return t, regions


def worldgen():
    """The structure keeps its id, pool and set; the set moves to the clear-of-origin placement (salt kept)."""
    W.pool('crashed_freighter/start', [W.element(ID, adapted=dict(yard_stand=BURIED, clear_from=SY, clear_inset=0,
                                                                     support_depth=8, ramp_rings=2))])
    W.structure('crashed_freighter', start_pool='crashed_freighter/start', biome_tag='has_structure/crashed_freighter',
                size=1, max_distance=48, start_height=1 - BURIED)
    W.structure_set('crashed_freighters', [('crashed_freighter', 1)], spacing=28, separation=10, salt=905201,
                    placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/crashed_freighter', ['oldrepublic:ghessar_dunes'])


def main(preview_dir=None):
    t, regions = build()
    G.save(t, ID, preview_dir, light_regions=regions)
    worldgen()
    G.report_walk(t, ID, ['HEART', 'SURFACE_GUARD', 'CREW_1', 'CREW_2', 'CREW_3', 'CREW_4'])
    return t


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else None)
